#!/usr/bin/env python3
"""Check the actual signed Ciel deliverable without launching it.

This verifies packaging, not runtime behavior or the trustworthiness of a signer.
Only metadata and finding locations are reported; credential values are never printed.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile


def android_tool(name):
    executable = shutil.which(name)
    if executable:
        return executable
    for sdk in (os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT")):
        if not sdk:
            continue
        for candidate in sorted((Path(sdk) / "build-tools").glob(f"*/{name}"), reverse=True):
            if candidate.is_file():
                return str(candidate)
    raise RuntimeError(f"{name} is unavailable; set ANDROID_HOME to the installed SDK")


def run_tool(command, include_stderr=False):
    result = subprocess.run(command, capture_output=True, text=True, timeout=180)
    if result.returncode:
        raise RuntimeError(f"{Path(command[0]).name} failed with exit {result.returncode}")
    return result.stdout + ("\n" + result.stderr if include_stderr else "")


def signer_certificates(output):
    # Build Tools 36+ names v3 signers by SDK range; older releases use #1.
    # Match certificate digests only, never a public key or SourceStamp digest.
    return sorted(set(re.findall(
        r"(?m)^Signer(?: #\d+| \(minSdkVersion=[^\r\n]*\)) certificate SHA-256 digest: "
        r"([a-fA-F0-9]{64})[ \t]*\r?$", output,
    )))


def inspect(apk, package):
    failures = []
    with apk.open("rb") as stream:
        digest = hashlib.file_digest(stream, "sha256").hexdigest()
    badging = run_tool([android_tool("aapt"), "dump", "badging", str(apk)])
    identity = re.search(r"package: name='([^']+)' versionCode='([^']+)' versionName='([^']+)'", badging)
    if not identity:
        raise RuntimeError("Android package identity could not be read")
    app_id, version_code, version_name = identity.groups()
    if app_id != package:
        failures.append("unexpected application id")
    if "application-debuggable" in badging:
        failures.append("APK is debuggable")
    activity = re.search(r"launchable-activity: name='([^']+)'", badging)
    if not activity or not activity.group(1).endswith(".ui.CielHomeActivity"):
        failures.append("Ciel home is not the launcher")
    sdk = re.search(r"sdkVersion:'(\d+)'", badging)
    abis = re.search(r"native-code: (.+)", badging)
    native_abis = re.findall(r"'([^']+)'", abis.group(1)) if abis else []
    if "arm64-v8a" not in native_abis:
        failures.append("ARM64 native libraries are missing")

    signing_tool = android_tool("apksigner")
    signature = run_tool([signing_tool, "verify", "--verbose", "--print-certs", str(apk)], include_stderr=True)
    fingerprints = signer_certificates(signature)
    if len(fingerprints) != 1:
        failures.append("expected exactly one signer certificate identity")
    run_tool([android_tool("zipalign"), "-c", "4", str(apk)])

    # Provider key formats are checked inside compressed DEX/assets, not just
    # in the outer ZIP bytes. Findings name the member and type, never the key.
    credential_patterns = {
        "Google API key": re.compile(rb"AIza[0-9A-Za-z_-]{35}"),
        "private key PEM": re.compile(rb"-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"),
    }
    suspicious = []
    with zipfile.ZipFile(apk) as archive:
        for notice in ("LICENSE", "THIRD_PARTY_NOTICES.md", "CIEL_NOTICE.md"):
            if f"assets/licenses/{notice}" not in archive.namelist():
                failures.append(f"packaged notice missing: {notice}")
        for member in archive.infolist():
            name = member.filename
            if name.endswith((".jks", ".keystore", ".p12", "/.env")):
                suspicious.append({"member": name, "kind": "signing/configuration material"})
            if not (name.endswith(".dex") or name.startswith("assets/")):
                continue
            if member.file_size > 64 * 1024 * 1024:
                failures.append(f"credential scan limit exceeded: {name}")
                continue
            data = archive.read(member)
            if name == "assets/andee_key.txt" and data.strip():
                suspicious.append({"member": name, "kind": "nonempty bundled provider key"})
            for kind, pattern in credential_patterns.items():
                if pattern.search(data):
                    suspicious.append({"member": name, "kind": kind})
    if suspicious:
        failures.append("possible credentials or signing material are packaged")
    return {
        "apk": str(apk.resolve()), "sha256": digest, "bytes": apk.stat().st_size,
        "package": app_id, "version_code": int(version_code), "version_name": version_name,
        "min_sdk": int(sdk.group(1)) if sdk else None, "native_abis": native_abis,
        "launcher": activity.group(1) if activity else None,
        "certificate_sha256": fingerprints[0].lower() if len(fingerprints) == 1 else None,
        "certificate_sha256_all": [value.lower() for value in fingerprints],
        "signing_tool": signing_tool,
        # apksigner's report contains public certificate metadata, not a key.
        **({"signing_report": signature} if len(fingerprints) != 1 else {}),
        "signature_verified": True, "zip_alignment_verified": True,
        "credential_findings": suspicious, "failures": failures,
        "packaging_passed": not failures, "runtime_verified_by_this_script": False,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    parser.add_argument("--package", default="ai.ciel.companion")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    try:
        result = inspect(args.apk, args.package)
    except (OSError, RuntimeError, subprocess.TimeoutExpired, zipfile.BadZipFile) as error:
        result = {"apk": str(args.apk), "packaging_passed": False, "error": str(error)}
    output = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.write_text(output)
    print(output, end="")
    return 0 if result["packaging_passed"] else 1


if __name__ == "__main__":
    sys.exit(main())
