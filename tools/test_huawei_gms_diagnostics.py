#!/usr/bin/env python3
"""Offline unit tests: never invoke adb or interact with a real device."""
import unittest

from huawei_gms_diagnostics import (
    PROPERTY_KEYS, PACKAGE_NAMES, build_report, clean_value,
    parse_package_list, parse_package_version,
    parse_known_account_types, parse_known_package_uids, build_account_type_report,
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


    def test_account_types_and_authenticators_only(self):
        raw = (
            "Account {name=private.person@example.com, type=app.revanced}\n"
            "Account {name=private2@example.com, type=com.android.email}\n"
            "ServiceInfo: AuthenticatorDescription {type=com.google}, "
            "ComponentInfo{com.google.android.gms/GoogleLoginService}\n"
            "ServiceInfo: AuthenticatorDescription {type=app.revanced}, "
            "ComponentInfo{app.revanced.android.gms/LoginService}\n"
        )
        accounts, authenticators = parse_known_account_types(raw)
        self.assertEqual({"app.revanced"}, accounts)
        self.assertEqual({"app.revanced", "com.google"}, authenticators)
        report = build_account_type_report(accounts, authenticators)
        self.assertNotIn("private.person", report)
        self.assertNotIn("@", report)
        self.assertNotIn("com.android.email", report)
        self.assertIn("com.google: authenticator=REGISTERED | account=NOT SEEN", report)
        self.assertIn("app.revanced: authenticator=REGISTERED | account=PRESENT", report)

    def test_account_dumpsys_unrelated_lines_do_not_match(self):
        raw = (
            "Secret: x@domain.com, type=com.google\n"
            "Something {type=app.revanced}\n"
            "Account {name=a@b.com, type=com.google.evil}\n"
        )
        self.assertEqual((set(), set()), parse_known_account_types(raw))

    def test_uid_parser_only_approved_packages(self):
        raw = (
            "package:com.android.vending uid:10279\n"
            "package:com.google.android.gms uid:10283\n"
            "package:app.revanced.android.gms uid:10260\n"
            "package:private.banking uid:10890\n"
            "package:com.google.android.gms.evil uid:10999\n"
        )
        uids = parse_known_package_uids(raw)
        self.assertEqual({
            "com.android.vending": "10279",
            "com.google.android.gms": "10283",
            "app.revanced.android.gms": "10260",
        }, uids)
        report = build_report({}, set(uids), {}, uids)
        self.assertIn("com.android.vending: INSTALLED | uid=10279", report)
        self.assertNotIn("private.banking", report)
        self.assertNotIn("10299", report)

    def test_account_report_empty_is_not_called_no_account(self):
        report = build_account_type_report(set(), set())
        self.assertIn("not seen in output", report)
        self.assertNotIn("NO ACCOUNTS", report)


if __name__ == "__main__":
    unittest.main()
