#!/usr/bin/env python3
"""Offline fixtures for the sanitized Huawei Play Store log analyzer."""
import unittest

from huawei_playstore_errors import summarize, build_report, classify


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


if __name__ == "__main__":
    unittest.main()
