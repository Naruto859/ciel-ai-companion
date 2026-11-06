#!/usr/bin/env python3
r"""Exercise a fresh Ciel APK on an explicitly named Android emulator.

Python standard library + adb only; no Gradle, instrumentation APK or API key.
Example:
  python3 tools/smoke_ciel_android.py --serial emulator-5554 \
    --apk /tmp/ciel.apk --package ai.ciel.companion.preview \
    --output /tmp/ciel-ui-smoke

The target package must be absent, including retained uninstall data. Nothing is
uninstalled or cleared. The exact supplied APK is installed and remains installed.
Font/rotation/animation settings are restored, and the test app is stopped on exit.
Only Ciel UI controls are tapped. Voice, phone permissions, external links and
device tools are never opened. A missing-key precondition precedes the setup test.

PNG/XML files are actual ADB captures. Setup uses FLAG_SECURE: record its UI
hierarchy only, without requesting a screenshot. Screen captures need visual review;
this script does not certify contrast, clipping, avatar beauty or audio latency.
Use a disposable, booted emulator with no PIN. Each ADB command, search and the
whole run has a deadline. Exit 0 means all automated checks passed, 1 a smoke
failure, and 2 an invalid invocation. The output directory must be new or empty.
"""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import shutil
import struct
import subprocess
import sys
import tempfile
import time
import uuid
import xml.etree.ElementTree as ET
import zipfile


ACTIVITY = "net.kuafuai.andee.ui.CielHomeActivity"
LABELS = {
    "Home": ("Home", "主页"),
    "Companion": ("Avatar", "形象"),
    "Setup": ("Setup", "设置"),
    "missing_key": ("Add your Gemini key to begin", "添加 Gemini 密钥即可开始"),
    "setup_voice": ("Set up voice", "设置语音"),
    "key_field": ("Gemini API key", "Gemini API 密钥"),
    "save_key": ("Save and use Gemini Live", "保存并使用 Gemini Live"),
    "key_error": ("Paste your Gemini API key to continue.", "请粘贴 Gemini API 密钥后继续。"),
}
CURRENT = (" · Current", " · 正在使用")
PACKAGE = re.compile(r"[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+")
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")


class SmokeError(RuntimeError):
    pass


def require(condition, message):
    if not condition:
        raise SmokeError(message)


def serial_arg(value):
    if not re.fullmatch(r"emulator-[0-9]+", value):
        raise argparse.ArgumentTypeError("Only an explicit emulator-NNNN serial is allowed")
    return value


def digest(path):
    result = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            result.update(block)
    return result.hexdigest()


def manifest_package(apk):
    """Read just the manifest package from standard binary AXML, without aapt."""
    with zipfile.ZipFile(apk) as archive:
        info = archive.getinfo("AndroidManifest.xml")
        require(info.file_size <= 4 * 1024 * 1024, "Manifest exceeds the 4 MiB limit")
        data = archive.read(info)
    if data.lstrip().startswith(b"<"):
        return ET.fromstring(data).attrib.get("package", "")
    require(len(data) >= 8 and struct.unpack_from("<H", data)[0] == 3, "Not an Android XML manifest")
    strings = []
    offset = struct.unpack_from("<H", data, 2)[0]
    while offset + 8 <= len(data):
        kind, header, size = struct.unpack_from("<HHI", data, offset)
        require(size >= header >= 8 and offset + size <= len(data), "Invalid manifest chunk")
        if kind == 1:  # ResStringPool
            count, _, flags, start, _ = struct.unpack_from("<IIIII", data, offset + 8)
            require(count <= 100000 and header + count * 4 <= size, "Invalid manifest string pool")
            strings = []
            for index in range(count):
                pos = offset + start + struct.unpack_from("<I", data, offset + header + index * 4)[0]
                if flags & 0x100:
                    def length8(at):
                        first = data[at]
                        return (((first & 127) << 8) | data[at + 1], at + 2) if first & 128 else (first, at + 1)
                    _, pos = length8(pos)  # UTF-16 character count, then byte count.
                    length, pos = length8(pos)
                    strings.append(data[pos:pos + length].decode("utf-8"))
                else:
                    length = struct.unpack_from("<H", data, pos)[0]
                    pos += 2
                    if length & 0x8000:
                        length = ((length & 0x7fff) << 16) | struct.unpack_from("<H", data, pos)[0]
                        pos += 2
                    strings.append(data[pos:pos + length * 2].decode("utf-16le"))
        elif kind == 0x102:  # ResXMLTree_attrExt begins after the node header.
            ext = offset + header
            name = struct.unpack_from("<I", data, ext + 4)[0]
            if strings[name] == "manifest":
                start, width, count = struct.unpack_from("<HHH", data, ext + 8)
                require(width >= 20 and ext + start + width * count <= offset + size, "Invalid manifest attributes")
                for index in range(count):
                    at = ext + start + index * width
                    name, raw = struct.unpack_from("<II", data, at + 4)
                    if strings[name] == "package":
                        if raw != 0xffffffff:
                            return strings[raw]
                        require(data[at + 15] == 3, "Package is not a manifest string")
                        return strings[struct.unpack_from("<I", data, at + 16)[0]]
        offset += size
    raise SmokeError("Manifest package was not found")


def node_labels(node):
    return (node.get("text", "").strip(), node.get("content-desc", "").strip())


def label_match(node, choices):
    return any(value in choices for value in node_labels(node))


def bounds(node):
    match = BOUNDS.fullmatch(node.get("bounds", ""))
    return tuple(map(int, match.groups())) if match else (0, 0, 0, 0)


def png_size(data):
    require(len(data) > 32 and data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR", "ADB did not return a PNG")
    width, height = struct.unpack_from(">II", data, 16)
    require(width > 0 and height > 0 and b"IEND" in data[-16:], "Incomplete screenshot")
    return width, height


def redact(value):
    value = re.sub(r"AIza[0-9A-Za-z_-]{20,}", "[redacted-key]", value)
    return re.sub(r"(?i)((?:api[_-]?key|authorization|access[_-]?token)\s*[=:]\s*)\S+", r"\1[redacted]", value)


def app_log_findings(lines, package, pids):
    """Attribute Java/native crashes and ANRs to this app, not unrelated services."""
    selected, failures = [], []
    package_re = re.compile(r"(?<![\w.])" + re.escape(package) + r"(?=[:\s,}/)\]]|$)")
    for index, line in enumerate(lines):
        pid = re.match(r"\d\d-\d\d\s+[\d:.]+\s+(\d+)\s+\d+\s+", line)
        ours = (pid and pid.group(1) in pids) or package_re.search(line)
        process_line = next((item for item in lines[index + 1:index + 7] if "Process:" in item and
                             (not pid or re.match(r"\d\d-\d\d\s+[\d:.]+\s+" + pid.group(1) + r"\s", item))), "")
        java_crash = "FATAL EXCEPTION" in line and (ours or package_re.search(process_line))
        native_crash = "Fatal signal" in line and ours
        native_dump = "Cmdline:" in line and package_re.search(line)
        anr = ("ANR in " in line or "am_anr" in line) and package_re.search(line)
        if ours or java_crash:
            selected.append(redact(line))
        if java_crash or native_crash or native_dump or anr:
            failures.append(redact(line))
    return selected, failures


class Smoke:
    def __init__(self, args, adb):
        self.args, self.adb = args, adb
        self.started = time.monotonic()
        self.deadline = self.started + args.timeout
        self.saved_settings = []
        self.saved_rotation = None
        self.saved_fixed_rotation = None
        self.emulator_verified = False
        self.installed = False
        self.pids = set()
        self.remote_xml = "/data/local/tmp/ciel-smoke-" + uuid.uuid4().hex + ".xml"
        self.log_start = None
        self.report = {
            "status": "running", "serial": args.serial, "package": args.package,
            "apk": str(args.apk.resolve()), "apk_sha256": digest(args.apk),
            "checks": [], "captures": [], "actions": [], "cleanup_errors": [],
            "visual_review_required": True, "audio_or_phone_control_verified": False,
            "limitations": ["Screenshots require visual review for rendering and clipped text.",
                            "Setup records UI hierarchy only; no screenshot is requested from the secure credential window.",
                            "Landscape layout uses an explicit emulator window-manager rotation, not a physical sensor test.",
                            "No Gemini calls, microphone tests, phone tools or physical devices are exercised."],
        }

    def checkpoint(self):
        (self.args.output / "smoke-report.json").write_text(json.dumps(self.report, indent=2, ensure_ascii=False) + "\n")

    def command(self, *parts, timeout=30, cleanup=False):
        remaining = self.deadline - time.monotonic()
        require(cleanup or remaining > 0, "Overall smoke deadline exceeded")
        timeout = timeout if cleanup else min(timeout, max(0.1, remaining))
        started = time.monotonic()
        result = subprocess.run([self.adb, "-s", self.args.serial, *parts], capture_output=True, timeout=timeout)
        self.report["actions"].append({"adb": list(parts), "exit": result.returncode,
                                       "seconds": round(time.monotonic() - started, 2)})
        require(result.returncode == 0, "ADB command failed: " + " ".join(parts[:2]) + ": " + redact(result.stderr.decode(errors="replace"))[:400])
        return result.stdout

    def shell(self, *parts, timeout=30, cleanup=False):
        return self.command("shell", shlex.join(parts), timeout=timeout, cleanup=cleanup).decode(errors="replace").strip()

    def wait(self, predicate, description, seconds=30):
        end = min(time.monotonic() + seconds, self.deadline)
        while time.monotonic() < end:
            if predicate():
                return
            time.sleep(0.4)
        raise SmokeError("Timed out: " + description)

    def check(self, name, action, fatal=False):
        started = time.monotonic()
        check = {"name": name, "status": "running"}
        self.report["checks"].append(check)
        self.checkpoint()
        print(name, flush=True)
        try:
            action()
            check["status"] = "passed"
        except (SmokeError, OSError, subprocess.TimeoutExpired, ET.ParseError) as error:
            check.update(status="failed", error=redact(str(error))[:800])
            if fatal:
                raise
        finally:
            check["seconds"] = round(time.monotonic() - started, 2)
            self.checkpoint()

    def prepare(self):
        require(self.command("get-state").strip() == b"device", "Emulator is not online")
        props = [self.shell("getprop", name) for name in ("ro.kernel.qemu", "ro.boot.qemu")]
        require("1" in props, "Serial has an emulator name but the device does not identify as QEMU; refusing")
        self.emulator_verified = True
        self.wait(lambda: self.shell("getprop", "sys.boot_completed") == "1", "emulator boot", seconds=90)
        require(self.shell("am", "get-current-user") == "0", "Use an emulator on its primary user (0)")
        packages = self.shell("pm", "list", "packages", "-u", self.args.package).splitlines()
        require("package:" + self.args.package not in packages, "Target package already exists or retains data. Use a fresh emulator; nothing was cleared.")
        self.report["device"] = {name: self.shell("getprop", name) for name in
                                 ("ro.build.version.sdk", "ro.product.model", "ro.product.cpu.abi", "ro.build.fingerprint")}
        result = self.command("install", "--no-streaming", str(self.args.apk.resolve()), timeout=150).decode(errors="replace")
        require("Success" in result, "APK install did not report success: " + redact(result)[:300])
        self.installed = True
        paths = self.shell("pm", "path", self.args.package).splitlines()
        require(len(paths) == 1 and paths[0].startswith("package:/"), "Expected one installed base APK for the supplied package")
        with tempfile.TemporaryDirectory(prefix="ciel-smoke-apk-") as directory:
            pulled = Path(directory) / "installed.apk"
            self.command("pull", paths[0][8:], str(pulled), timeout=150)
            self.report["installed_apk_sha256"] = digest(pulled)
        require(self.report["installed_apk_sha256"] == self.report["apk_sha256"], "Installed APK differs from the supplied APK")
        rotation = self.shell("wm", "user-rotation")
        fixed = self.shell("wm", "fixed-to-user-rotation")
        require(re.fullmatch(r"free|lock [0-3]", rotation), "Unknown emulator user-rotation state: " + rotation)
        require(fixed in ("default", "disabled", "enabled"), "Unknown fixed rotation state: " + fixed)
        self.saved_rotation, self.saved_fixed_rotation = rotation, fixed
        self.shell("wm", "fixed-to-user-rotation", "enabled")
        self.shell("wm", "user-rotation", "lock", "0")
        for namespace, key, value in (("system", "font_scale", "1.0"),
                                      ("global", "window_animation_scale", "0"),
                                      ("global", "transition_animation_scale", "0"),
                                      ("global", "animator_duration_scale", "0")):
            self.saved_settings.append((namespace, key, self.shell("settings", "get", namespace, key)))
            self.shell("settings", "put", namespace, key, value)
        self.log_start = self.shell("date", "+%m-%d %H:%M:%S.000")
        require(re.fullmatch(r"\d\d-\d\d \d\d:\d\d:\d\d\.000", self.log_start), "Could not establish logcat start time")
        self.shell("input", "keyevent", "KEYCODE_WAKEUP")
        self.shell("wm", "dismiss-keyguard")

    def resumed(self):
        dump = self.shell("dumpsys", "activity", "activities")
        lines = [line.strip() for line in dump.splitlines() if
                 ("mResumedActivity" in line or "topResumedActivity" in line) and
                 self.args.package + "/" + ACTIVITY in line]
        pid = self.shell("pidof", self.args.package) if lines else ""
        if not lines or not pid:
            return False
        self.pids.update(pid.split())
        self.report["last_resumed_activity"] = lines
        self.report["observed_app_pids"] = sorted(self.pids)
        return True

    def launch(self):
        self.shell("am", "force-stop", self.args.package)
        result = self.shell("am", "start", "-W", "-n", self.args.package + "/" + ACTIVITY, timeout=45)
        require("Error:" not in result and "Exception" not in result, "Home launch failed: " + result[:300])
        self.wait(self.resumed, "CielHomeActivity resumed with a PID", seconds=45)
        self.find(LABELS["Home"], scroll=False)

    def tree(self):
        require(self.resumed(), "CielHomeActivity is not resumed; refusing UI input into another app")
        for _ in range(3):
            self.shell("rm", "-f", self.remote_xml)
            result = self.shell("uiautomator", "dump", "--compressed", self.remote_xml, timeout=25)
            if "ERROR" not in result and "dumped to" in result.lower():
                raw = self.command("exec-out", shlex.join(("cat", self.remote_xml)))
                tree = ET.fromstring(raw)
                require(any(node.get("package") == self.args.package for node in tree.iter("node")), "UI hierarchy is outside Ciel")
                return tree, raw
            time.sleep(0.4)
        raise SmokeError("UIAutomator could not dump a live Ciel hierarchy after three attempts (possibly no idle window)")

    def find(self, choices=None, *, predicate=None, scroll=True):
        match = predicate or (lambda node: label_match(node, choices))
        previous = None
        for _ in range(11 if scroll else 3):
            tree, raw = self.tree()
            for node in tree.iter("node"):
                x1, y1, x2, y2 = bounds(node)
                if node.get("package") == self.args.package and x2 > x1 and y2 > y1 and match(node):
                    return node
            if not scroll:
                time.sleep(0.4)
            elif raw == previous or not self.swipe(tree, up=True):
                break
            previous = raw
        raise SmokeError("Visible UI control/text not found: " + (" / ".join(choices) if choices else "avatar state"))

    def tap(self, node):
        require(node.get("package") == self.args.package and node.get("enabled") == "true" and node.get("clickable") == "true", "Refusing a disabled or non-Ciel UI target")
        require(self.resumed(), "Ciel lost the foreground before the tap")
        x1, y1, x2, y2 = bounds(node)
        require(x2 - x1 >= 8 and y2 - y1 >= 8, "Control has no usable hit area")
        self.shell("input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2))
        time.sleep(0.4)

    def click(self, choices, scroll=True):
        self.tap(self.find(predicate=lambda n: label_match(n, choices) and n.get("clickable") == "true", scroll=scroll))

    def swipe(self, tree, up):
        areas = [node for node in tree.iter("node") if node.get("package") == self.args.package and
                 node.get("class") == "android.widget.ScrollView"]
        if not areas:
            return False
        x1, y1, x2, y2 = bounds(max(areas, key=lambda n: (bounds(n)[2] - bounds(n)[0]) * (bounds(n)[3] - bounds(n)[1])))
        require(x2 > x1 and y2 - y1 > 80, "Scroll area is too small")
        low, high = y1 + (y2 - y1) // 4, y1 + (y2 - y1) * 3 // 4
        self.shell("input", "swipe", str((x1 + x2) // 2), str(high if up else low),
                   str((x1 + x2) // 2), str(low if up else high), "350")
        time.sleep(0.4)
        return True

    def top(self):
        previous = None
        for _ in range(14):
            tree, raw = self.tree()
            if raw == previous or not self.swipe(tree, up=False):
                return
            previous = raw
        raise SmokeError("Could not reach the top within 14 UI-driven swipes")

    def capture(self, name, secure=False):
        tree, raw = self.tree()
        (self.args.output / (name + ".xml")).write_bytes(raw)
        if secure:
            # Some emulator versions refuse screencap entirely for FLAG_SECURE,
            # instead of returning a black PNG. Validate labels through the UI
            # hierarchy while respecting the credential window's capture policy.
            self.report["captures"].append({"name": name, "capture_kind": "hierarchy_only",
                "xml_sha256": hashlib.sha256(raw).hexdigest(), "secure_window_expected": True,
                "screenshot_omitted": "secure credential window"})
            self.checkpoint()
            x1, y1, x2, y2 = bounds(next(tree.iter("node")))
            return x2 - x1, y2 - y1
        # Let the newly attached GL surface draw; its pixels are reviewed from
        # the actual capture, never synthesized from source-side expectations.
        time.sleep(1.0)
        png = self.command("exec-out", "screencap -p")
        width, height = png_size(png)
        (self.args.output / (name + ".png")).write_bytes(png)
        self.report["captures"].append({"name": name, "width": width, "height": height,
                                        "png_sha256": hashlib.sha256(png).hexdigest(),
                                        "xml_sha256": hashlib.sha256(raw).hexdigest(),
                                        "secure_window_expected": secure})
        self.checkpoint()
        return width, height

    def tab(self, name):
        self.click(LABELS[name], scroll=False)
        node = self.find(LABELS[name], scroll=False)
        require(node.get("selected") == "true", name + " tab did not become selected")

    def initial_home(self):
        self.launch()
        self.capture("01-home")
        self.find(LABELS["missing_key"])
        self.report["missing_key_precondition"] = True

    def missing_key_setup(self):
        require(self.report.get("missing_key_precondition"), "Missing-key precondition was not verified; no conversation control was tapped")
        self.launch()
        self.click(LABELS["setup_voice"])
        self.find(LABELS["key_field"])
        self.capture("02-setup-empty", secure=True)
        self.click(LABELS["save_key"])
        self.find(predicate=lambda n: any(expected in value for value in node_labels(n) for expected in LABELS["key_error"]), scroll=False)
        self.capture("03-setup-key-error", secure=True)

    def avatar(self, name, current=None, selected=None):
        def match(node):
            title = node.get("text", "").split("\n", 1)[0]
            right_name = title == name or title in (name + suffix for suffix in CURRENT)
            right_current = current is None or any(title.endswith(suffix) for suffix in CURRENT) == current
            right_selected = selected is None or (node.get("selected") == "true") == selected
            return node.get("clickable") == "true" and right_name and right_current and right_selected
        return self.find(predicate=match)

    def companion(self):
        self.launch()
        self.tab("Companion")

    def avatar_flow(self, name, previous):
        self.companion()
        self.avatar(previous, current=True, selected=True)
        self.tap(self.avatar(name))
        self.avatar(name, selected=True)
        self.top()
        self.avatar(previous, current=True)
        self.capture(name.lower() + "-preview-controls")
        self.top()
        self.capture(name.lower() + "-preview")
        self.companion()  # A preview must not persist after a process restart.
        self.avatar(previous, current=True, selected=True)
        self.tap(self.avatar(name))
        self.click(("Use " + name, "使用 " + name))
        self.top()
        self.avatar(name, current=True, selected=True)
        self.capture(name.lower() + "-saved-controls")
        self.companion()
        self.avatar(name, current=True, selected=True)
        self.top()
        self.capture(name.lower() + "-after-restart")

    def original_companion(self):
        self.companion()
        self.avatar("Ciel", current=True, selected=True)
        self.top()
        self.capture("04-companion-original")

    def layout(self, name, font, rotation):
        self.shell("settings", "put", "system", "font_scale", font)
        self.shell("wm", "user-rotation", "lock", rotation)
        self.launch()
        def oriented():
            width, height = png_size(self.command("exec-out", "screencap -p"))
            return width > height if rotation == "1" else height > width
        self.wait(oriented, "emulator display orientation " + rotation, seconds=20)
        self.top()
        size = self.capture(name + "-home")
        if rotation == "1":
            require(size[0] > size[1], "Emulator did not rotate to landscape")
        for page in ("Companion", "Setup", "Home"):
            self.tab(page)
            if page != "Home":
                self.top()
                self.capture(name + "-" + page.lower(), secure=page == "Setup")
        self.require_navigation()

    def require_navigation(self):
        tree, _ = self.tree()
        width, height = png_size(self.command("exec-out", "screencap -p"))
        for page in ("Home", "Companion", "Setup"):
            matches = [node for node in tree.iter("node") if node.get("package") == self.args.package and
                       label_match(node, LABELS[page]) and node.get("clickable") == "true"]
            require(len(matches) == 1, "Navigation control is missing or ambiguous: " + page)
            x1, y1, x2, y2 = bounds(matches[0])
            require(0 <= x1 < x2 <= width and 0 <= y1 < y2 <= height, "Navigation control is outside the screen: " + page)
        self.report["navigation_bounds_checked"] = True

    def logs(self, require_alive=True):
        require(self.log_start is not None, "No logcat start marker is available")
        raw = self.command("logcat", "-d", "-b", "all", "-v", "threadtime", "-T", self.log_start)
        lines = raw.decode(errors="replace").splitlines()
        selected, failures = app_log_findings(lines, self.args.package, self.pids)
        (self.args.output / "app-logcat.txt").write_text("\n".join(selected) + "\n")
        self.report["app_fatal_or_anr"] = failures
        self.report["logcat_lines_examined"] = len(lines)
        require(not failures, "Ciel FATAL/native crash/ANR detected; see app-logcat.txt")
        if require_alive:
            require(self.resumed(), "Ciel is no longer resumed/alive at the end of the smoke")

    def cleanup(self):
        if not self.emulator_verified:
            return
        commands = []
        if self.installed:
            commands.append(("am", "force-stop", self.args.package))
        for namespace, key, value in reversed(self.saved_settings):
            commands.append(("settings", "delete", namespace, key) if value == "null" else
                            ("settings", "put", namespace, key, value))
        if self.saved_fixed_rotation is not None:
            commands.append(("wm", "fixed-to-user-rotation", self.saved_fixed_rotation))
        if self.saved_rotation is not None:
            commands.append(("wm", "user-rotation", *self.saved_rotation.split()))
        commands.append(("rm", "-f", self.remote_xml))
        for command in commands:
            try:
                self.shell(*command, timeout=8, cleanup=True)
            except (SmokeError, OSError, subprocess.TimeoutExpired) as error:
                self.report["cleanup_errors"].append(redact(str(error))[:300])

    def run(self):
        try:
            self.check("fresh emulator and exact APK provenance", self.prepare, fatal=True)
            self.check("Home launch, resumed PID and missing-key state", self.initial_home, fatal=True)
            self.check("missing-key setup route and visible validation error", self.missing_key_setup)
            self.check("original companion", self.original_companion)
            self.check("Moss preview isolation and saved persistence", lambda: self.avatar_flow("Moss", "Ciel"))
            self.check("Nova preview isolation and saved persistence", lambda: self.avatar_flow("Nova", "Moss"))
            self.check("large-font portrait tabs", lambda: self.layout("large-font", "1.5", "0"))
            self.check("landscape tabs", lambda: self.layout("landscape", "1.0", "1"))
            self.check("app remains resumed without app FATAL/ANR", self.logs)
        except (SmokeError, OSError, subprocess.TimeoutExpired, ET.ParseError) as error:
            self.report["fatal_error"] = redact(str(error))[:800]
        except KeyboardInterrupt:
            self.report["fatal_error"] = "Interrupted by operator"
        finally:
            if self.log_start is not None and "app_fatal_or_anr" not in self.report:
                try:
                    self.logs(require_alive=False)
                except (SmokeError, OSError, subprocess.TimeoutExpired) as error:
                    self.report["log_collection_error"] = redact(str(error))[:500]
            self.cleanup()
            self.report["status"] = "passed" if (not self.report.get("fatal_error") and
                not self.report["cleanup_errors"] and all(check["status"] == "passed" for check in self.report["checks"])) else "failed"
            self.report["seconds"] = round(time.monotonic() - self.started, 2)
            self.checkpoint()
        print(json.dumps({"status": self.report["status"], "report": str(self.args.output / "smoke-report.json")}), flush=True)
        return 0 if self.report["status"] == "passed" else 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--serial", required=True, type=serial_arg)
    parser.add_argument("--apk", required=True, type=Path)
    parser.add_argument("--package", required=True)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--adb", help="ADB executable; otherwise PATH or the installed Android SDK is used")
    parser.add_argument("--timeout", type=int, default=1200, help="Overall deadline in seconds (1200 by default)")
    args = parser.parse_args(argv)
    try:
        require(PACKAGE.fullmatch(args.package), "Invalid package ID")
        require(60 <= args.timeout <= 3600, "--timeout must be between 60 and 3600 seconds")
        require(args.apk.is_file(), "APK file does not exist")
        require(manifest_package(args.apk) == args.package, "Supplied package ID does not match the APK manifest; nothing was installed")
        adb = args.adb or shutil.which("adb")
        if not adb:
            for name in ("ANDROID_HOME", "ANDROID_SDK_ROOT"):
                if os.environ.get(name):
                    candidate = Path(os.environ[name]) / "platform-tools" / "adb"
                    if candidate.is_file():
                        adb = str(candidate)
                        break
        require(adb is not None, "adb is unavailable; use --adb or configure the Android SDK")
        require(not args.output.exists() or (args.output.is_dir() and not any(args.output.iterdir())), "Output directory must be new or empty")
        args.output.mkdir(parents=True, exist_ok=True)
    except (SmokeError, OSError, ValueError, IndexError, KeyError, struct.error, ET.ParseError, zipfile.BadZipFile) as error:
        parser.error(str(error))
    return Smoke(args, adb).run()


if __name__ == "__main__":
    sys.exit(main())
