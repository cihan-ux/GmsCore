#!/usr/bin/env python3
"""Offline fixtures for the sanitized Huawei Play Store log analyzer."""
import unittest

from huawei_playstore_errors import (
    summarize, build_report, classify, scan_coverage, build_coverage_report,
)


class PlayStoreErrorSummaryTests(unittest.TestCase):
    def test_android_getuserdata_denial_matches(self):
        log = ("10-19 22:16:13.218 E/Finsky: java.lang.SecurityException: "
               "uid 10279 cannot get user data for accounts of type: app.revanced")
        counts, dates = summarize(log)
        self.assertEqual(1, counts["private_account"])
        self.assertEqual("10-19 22:16:13", dates["private_account"])

    def test_account_fetch_failure(self):
        counts, _ = summarize(
            "10-19 22:16:13.218 E/Finsky: Failed to fetch user data from account manager.")
        self.assertEqual(1, counts["account_fetch"])

    def test_privileged_operation_denial(self):
        counts, _ = summarize(
            "10-19 22:20:01.000 E/Finsky: "
            "java.lang.SecurityException: MANAGE_USERS or CREATE_USERS permission")
        self.assertEqual(1, counts["user_privilege"])

    def test_store_self_update_is_different_from_app_install(self):
        counts, dates = summarize(
            "10-18 15:36:26.441 E/Finsky: "
            "SU: Install failed for com.android.vending with an 976")
        self.assertEqual(1, counts["self_update"])
        self.assertEqual(0, counts["package_install"])
        self.assertEqual("10-18 15:36:26", dates["self_update"])

    def test_session_callback_and_install_failed_token(self):
        counts, _ = summarize(
            "10-18 15:36:26.440 I/PackageInstaller: notifySessionFinished again failed\n"
            "10-18 15:36:26.444 E/PackageManager: "
            "INSTALL_FAILED_UPDATE_INCOMPATIBLE")
        self.assertEqual(1, counts["installer_session"])
        self.assertEqual(1, counts["package_install"])

    def test_finsky_ui_warnings_are_not_install_failures(self):
        counts, _ = summarize(
            "10-19 22:23:02.254 E/Finsky: AutoOpen setup failed. Existing AutoOpen session may exist.\n"
            "10-19 22:23:02.365 E/Finsky: ComposeVisualElement Error: already linked\n"
            "10-19 22:16:13.218 E/Finsky: no logging context")
        self.assertEqual(0, sum(counts.values()))

    def test_finsky_download_failures(self):
        counts, _ = summarize("10-19 22:18:13.223 E/Finsky: download failed")
        self.assertEqual(1, counts["download"])

    def test_no_private_values_leave_report(self):
        private = (
            "10-19 22:16:13.218 E/Finsky: java.lang.SecurityException: "
            "uid 10279 cannot get user data for accounts of type: app.revanced "
            "Account {name=private.user@example.com, type=app.revanced} "
            "Authorization: Bearer ya29.example_private_token\n"
            "10-19 22:18:13.223 E/Finsky: download failed for "
            "private.app.package /data/user/0/secret"
        )
        out = build_report(*summarize(private))
        for secret in ("private.user", "@example.com", "Authorization", "ya29",
                       "private.app.package", "/data/user/0/", "10279"):
            self.assertNotIn(secret, out)
        self.assertIn("PLAY_STORE_ACCOUNT_PRIVATE_DATA_DENIED", out)
        self.assertIn("FINSKY_DOWNLOAD_FAILURE", out)

    def test_account_name_without_error_is_not_reported(self):
        counts, _ = summarize(
            "10-19 22:10:00 D/GmsAuthenticator: getAuthToken: "
            "Account {name=somebody@example.com, type=app.revanced}, "
            "Bearer ya29.SECRET")
        self.assertEqual(0, sum(counts.values()))

    def test_no_matches_is_not_success_claim(self):
        out = build_report(*summarize(""))
        self.assertIn("does not by itself prove", out)
        self.assertIn("PLAY_STORE_ACCOUNT_PRIVATE_DATA_DENIED: 0 event(s)", out)


    def test_coverage_reports_when_no_finsky_logs_are_present(self):
        raw = (
            "10-19 22:31:10.100 I/ActivityManager( 1549): some event\n"
            "10-19 22:31:12.200 D/OtherApp( 1200): benign event\n"
        )
        counts, _ = summarize(raw)
        self.assertEqual(0, sum(counts.values()))
        coverage, first, last = scan_coverage(raw)
        self.assertEqual(2, coverage["all_lines"])
        self.assertEqual(0, coverage["finsky_lines"])
        report = build_coverage_report(coverage, first, last)
        self.assertIn("NO FINSKY LOGS", report)
        self.assertIn("10-19 22:31:10", report)
        self.assertIn("10-19 22:31:12", report)

    def test_coverage_with_finsky_but_no_known_errors(self):
        raw = (
            "10-19 22:32:00.001 I/Finsky  (1234): Finsky started\n"
            "10-19 22:32:01.002 I/Finsky:background(998): download running\n"
            "10-19 22:32:01.003 I/PackageInstaller(1549): session created\n"
            "10-19 22:32:02.004 I/Finsky: package com.android.vending loaded\n"
        )
        coverage, first, last = scan_coverage(raw)
        self.assertEqual(3, coverage["finsky_lines"])
        self.assertEqual(1, coverage["installer_lines"])
        self.assertEqual(1, coverage["vending_mentions"])
        self.assertIn("Finsky logs are present", build_coverage_report(coverage, first, last))
        self.assertEqual(0, sum(summarize(raw)[0].values()))

    def test_coverage_empty_and_private_content_is_filtered(self):
        empty = build_coverage_report(*scan_coverage(""))
        self.assertIn("EMPTY", empty)
        secret = (
            "10-19 22:32:00.123 E/Finsky  (9999): "
            "my.name@example.com token=ya29.private_user_token "
            "/data/user/0/com.android.vending/secret"
        )
        sanitized = build_coverage_report(*scan_coverage(secret))
        self.assertIn("Finsky-tagged lines: 1", sanitized)
        for data in ("my.name", "example.com", "ya29", "token=", "/data/user/"):
            self.assertNotIn(data, sanitized)



if __name__ == "__main__":
    unittest.main()
