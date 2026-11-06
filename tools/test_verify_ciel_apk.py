"""Signer-output compatibility checks for the APK release gate."""

import unittest

from verify_ciel_apk import signer_certificates


class SignerCertificatesTest(unittest.TestCase):
    fingerprint = "1e11584f8c081fc18f0ddaf6467a8268f59bf5a37c2b0bd55105bb7af0a17bac"

    def test_build_tools_34_numbered_signer(self):
        output = "Signer #1 certificate SHA-256 digest: " + self.fingerprint + "\n"
        self.assertEqual(signer_certificates(output), [self.fingerprint])

    def test_sdk_range_signer_and_duplicate_rotation_record(self):
        output = "\n".join(
            name + " certificate SHA-256 digest: " + self.fingerprint
            for name in ("Signer (minSdkVersion=28, maxSdkVersion=2147483647)",
                         "Signer (minSdkVersion=33 (dev release=true), maxSdkVersion=2147483647)")
        )
        self.assertEqual(signer_certificates(output), [self.fingerprint])

    def test_other_digests_cannot_supply_the_application_signer(self):
        output = "\n".join([
            "Signer #1 public key SHA-256 digest: " + self.fingerprint,
            "Source Stamp Signer certificate SHA-256 digest: " + self.fingerprint,
            "Signer #1 certificate SHA-256 digest: 1234",
        ])
        self.assertEqual(signer_certificates(output), [])

    def test_distinct_identities_remain_distinct_for_gate_rejection(self):
        output = "Signer #1 certificate SHA-256 digest: " + self.fingerprint + "\n"
        output += "Signer #2 certificate SHA-256 digest: " + "a" * 64 + "\n"
        self.assertEqual(len(signer_certificates(output)), 2)


if __name__ == "__main__":
    unittest.main()
