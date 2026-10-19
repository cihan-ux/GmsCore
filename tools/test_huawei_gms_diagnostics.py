#!/usr/bin/env python3
"""Offline unit tests: never invoke adb or interact with a real device."""
import unittest

from huawei_gms_diagnostics import (
    PROPERTY_KEYS, PACKAGE_NAMES, build_report, clean_value,
    parse_package_list, parse_package_version,
)


class HuaweiGmsDiagnosticsTests(unittest.TestCase):
    def test_exact_package_matches_only(self):
        got = parse_package_list(
            "package:com.google.android.gms\n"
            "package:app.revanced.android.gms\n"
            "package:com.google.android.gms.fake\n"
            "ignored\n")
        self.assertIn("com.google.android.gms", got)
        self.assertIn("app.revanced.android.gms", got)
        self.assertNotIn("com.android.vending", got)
        self.assertNotIn("ignored", got)

    def test_untrusted_lines_rejected(self):
        self.assertNotIn("bad;name", parse_package_list("package:bad;name\n"))
        self.assertNotIn("foo bar", parse_package_list("package:foo bar\n"))

    def test_version_metadata_only(self):
        raw = """Packages:
          Package [com.google.android.gms] (abc):
            versionCode=252432000 minSdk=19 targetSdk=29
            versionName=0.0.0.252432-hw
            userId=10200
        """
        self.assertEqual(
            ("0.0.0.252432-hw", "252432000"),
            parse_package_version(raw)
        )

    def test_unavailable_versions_are_nonfatal(self):
        self.assertEqual(("unavailable", "unavailable"), parse_package_version(""))

    def test_control_characters_removed_and_length_bounded(self):
        self.assertEqual("hello world", clean_value("hello\n world\r"))
        self.assertLessEqual(len(clean_value("X" * 200)), 150)

    def test_report_has_only_allowlisted_properties_and_packages(self):
        properties = {key: "test" for key in PROPERTY_KEYS}
        properties["ro.serialno"] = "PRIVATE_SERIAL_DO_NOT_PRINT"
        installed = set(PACKAGE_NAMES[:2])
        report = build_report(properties, installed, {
            "com.google.android.gms": ("1.0", "1")
        })
        self.assertNotIn("PRIVATE_SERIAL_DO_NOT_PRINT", report)
        self.assertNotIn("ro.serialno", report)
        self.assertIn("app.revanced.android.gms: INSTALLED", report)
        self.assertIn("com.android.vending: NOT INSTALLED", report)
        self.assertIn("No account names", report)
        self.assertIn("MANAGE_USERS", report)


if __name__ == "__main__":
    unittest.main()
