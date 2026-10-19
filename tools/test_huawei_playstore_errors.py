#!/usr/bin/env python3
"""Offline fixtures for the sanitized Huawei Play Store log analyzer."""
import unittest
from unittest.mock import patch
import subprocess

from huawei_playstore_errors import (
    summarize, build_report, classify, scan_coverage, build_coverage_report,
    build_logcat_command, read_adb_log,
    summarize_playstore_crashes, summarize_playstore_crash_causes,
    build_crash_report, read_crash_buffer,
    summarize_playstore_crash_security_context,
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


    def test_focused_command_filters_tags_before_tail_count(self):
        command = build_logcat_command(3000, focused=True)
        self.assertEqual(command[:3], ("adb", "logcat", "-d"))
        self.assertIn("-s", command)
        self.assertIn("Finsky:V", command)
        self.assertIn("PackageInstaller:V", command)
        self.assertIn("PackageManager:W", command)
        self.assertEqual(command[command.index("-t") + 1], "3000")
        self.assertNotIn("-c", command)
        self.assertNotIn("clear", command)
        self.assertNotIn("--pid", command)

    def test_standard_command_does_not_filter_tags(self):
        command = build_logcat_command(3000, focused=False)
        self.assertNotIn("-s", command)
        self.assertEqual(command[command.index("-t") + 1], "3000")

    @patch("huawei_playstore_errors.subprocess.run")
    def test_focused_query_stays_redacted_and_read_only(self, fake_run):
        fake_run.return_value = subprocess.CompletedProcess(
            args=(), returncode=0,
            stdout="10-19 23:25:01.000 E/Finsky: Failed to fetch user data from account manager.\n"
                   "Account {name=private.user@example.com, type=app.revanced}\n",
            stderr="",
        )
        raw, mode = read_adb_log(3000, focused=True)
        self.assertIn("FOCUSED", mode)
        command = fake_run.call_args.args[0]
        self.assertIn("Finsky:V", command)
        self.assertEqual(fake_run.call_args.kwargs["capture_output"], True)
        report = build_report(*summarize(raw))
        self.assertNotIn("private.user", report)
        self.assertIn("PLAY_STORE_ACCOUNT_METADATA_FETCH_FAILED: 1", report)

    @patch("huawei_playstore_errors.subprocess.run")
    def test_focused_fallback_preserves_tag_filter(self, fake_run):
        fake_run.side_effect = [
            subprocess.TimeoutExpired(cmd="adb", timeout=55),
            subprocess.CompletedProcess(args=(), returncode=0, stdout="", stderr=""),
        ]
        raw, mode = read_adb_log(3000, focused=True)
        self.assertEqual(raw, "")
        self.assertIn("FOCUSED FALLBACK", mode)
        self.assertEqual(fake_run.call_count, 2)
        for call in fake_run.call_args_list:
            self.assertIn("Finsky:V", call.args[0])
            self.assertNotIn("-c", call.args[0])
        fallback_command = fake_run.call_args_list[1].args[0]
        self.assertEqual(fallback_command[fallback_command.index("-t")+1], "300")



    def test_real_play_store_crash_only(self):
        raw = (
            "10-19 23:35:10.000 E/AndroidRuntime(1540): FATAL EXCEPTION: main\n"
            "10-19 23:35:10.001 E/AndroidRuntime(1540): Process: com.android.vending, PID: 1540\n"
            "10-19 23:35:10.002 E/AndroidRuntime(1540): java.lang.IllegalStateException: "
            "name=private.person@example.com token=ya29.secret\n"
            "10-19 23:36:10.000 E/AndroidRuntime(1800): FATAL EXCEPTION: main\n"
            "10-19 23:36:10.001 E/AndroidRuntime(1800): Process: other.app, PID: 1800\n"
            "10-19 23:36:10.002 E/AndroidRuntime(1800): java.lang.NullPointerException\n"
        )
        events = summarize_playstore_crashes(raw)
        self.assertEqual([("10-19 23:35:10", "IllegalStateException")], events)
        report = build_crash_report(raw, "STANDARD")
        self.assertIn("com.android.vending: 1", report)
        self.assertNotIn("private.person", report)
        self.assertNotIn("ya29", report)
        self.assertNotIn("1540", report)

    def test_unrelated_crashes_not_counted(self):
        raw = (
            "10-19 23:35:10.000 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:35:10.001 E/AndroidRuntime: Process: com.google.android.gms, PID: 33\n"
            "10-19 23:35:10.002 E/AndroidRuntime: com.android.vending was mentioned\n"
        )
        self.assertEqual([], summarize_playstore_crashes(raw))

    def test_nonfatal_play_store_error_not_called_crash(self):
        raw = "10-19 23:35:00.000 E/Finsky: failed to fetch account metadata\n"
        self.assertEqual([], summarize_playstore_crashes(raw))
        report = build_crash_report(raw, "STANDARD")
        self.assertIn("FATAL EXCEPTION events belonging to com.android.vending: 0", report)
        self.assertIn("does not rule out", report)

    @patch("huawei_playstore_errors.subprocess.run")
    def test_crash_buffer_adb_read_is_privacy_limited(self, fake_run):
        fake_run.return_value = subprocess.CompletedProcess(
            args=(), returncode=0,
            stdout="10-19 23:35:00.000 E/AndroidRuntime: FATAL EXCEPTION: main\n"
                   "10-19 23:35:00.001 E/AndroidRuntime: Process: com.android.vending, PID: 19\n"
                   "10-19 23:35:00.002 E/AndroidRuntime: java.lang.SecurityException: "
                   "email=private@example.com\n",
            stderr="",
        )
        data, mode = read_crash_buffer()
        args = fake_run.call_args.args[0]
        self.assertEqual(args[:3], ("adb", "logcat", "-d"))
        self.assertEqual(args[args.index("-b") + 1], "crash")
        self.assertNotIn("-c", args)
        self.assertEqual(fake_run.call_args.kwargs["capture_output"], True)
        report = build_crash_report(data, mode)
        self.assertIn("SecurityException", report)
        self.assertNotIn("private@example.com", report)



    def test_nested_runtime_error_identifies_account_denial_indicator(self):
        raw = (
            "10-19 23:36:16.100 E/AndroidRuntime(20): FATAL EXCEPTION: main\n"
            "10-19 23:36:16.101 E/AndroidRuntime(20): Process: com.android.vending, PID: 20\n"
            "10-19 23:36:16.102 E/AndroidRuntime(20): java.lang.RuntimeException: private@example.com\n"
            "10-19 23:36:16.103 E/AndroidRuntime(20):   at x.y.z(Unknown:12)\n"
            "10-19 23:36:16.104 E/AndroidRuntime(20): Caused by: java.lang.SecurityException: "
            "uid 10279 cannot get user data for accounts of type: app.revanced "
            "token=ya29.secret\n"
        )
        events = summarize_playstore_crash_causes(raw)
        self.assertEqual([("10-19 23:36:16", "ACCOUNT_PRIVATE_USERDATA_DENIED")], events)
        report = build_crash_report(raw, "STANDARD")
        self.assertIn("ACCOUNT_PRIVATE_USERDATA_DENIED", report)
        for secret in ("private@", "ya29", "10279"):
            self.assertNotIn(secret, report)

    def test_protected_user_management_different_from_account_data(self):
        raw = (
            "10-19 23:36:16.100 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:36:16.101 E/AndroidRuntime: Process: com.android.vending, PID: 55\n"
            "10-19 23:36:16.102 E/AndroidRuntime: java.lang.SecurityException: "
            "MANAGE_USERS or CREATE_USERS permission\n"
        )
        self.assertEqual(
            [("10-19 23:36:16", "PROTECTED_USER_MANAGEMENT_PERMISSION")],
            summarize_playstore_crash_causes(raw)
        )

    def test_nearby_other_process_cause_not_misattributed(self):
        raw = (
            "10-19 23:32:01.001 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:32:01.002 E/AndroidRuntime: Process: com.android.vending, PID: 4\n"
            "10-19 23:32:01.003 E/AndroidRuntime: java.lang.RuntimeException\n"
            "10-19 23:32:02.001 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:32:02.002 E/AndroidRuntime: Process: another.app, PID: 5\n"
            "10-19 23:32:02.003 E/AndroidRuntime: java.lang.SecurityException: "
            "uid 5 cannot get user data for accounts of type: app.revanced\n"
        )
        self.assertEqual(
            [("10-19 23:32:01", "OTHER_RUNTIME_EXCEPTION")],
            summarize_playstore_crash_causes(raw)
        )

    def test_unknown_crash_indicator_no_raw_text(self):
        raw = (
            "10-19 23:32:01.001 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:32:01.002 E/AndroidRuntime: Process: com.android.vending, PID: 4\n"
            "10-19 23:32:01.003 E/AndroidRuntime: vendor.custom.Fatal: "
            "private.person@example.com and ya29.SECRET\n"
        )
        report = build_crash_report(raw, "STANDARD")
        self.assertIn("indicator: UNKNOWN", report)
        self.assertNotIn("private.person", report)
        self.assertNotIn("ya29", report)



    def test_security_permission_markers_are_known_only(self):
        raw = (
            "10-19 23:53:20.001 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:53:20.002 E/AndroidRuntime: Process: com.android.vending, PID: 10279\n"
            "10-19 23:53:20.003 E/AndroidRuntime: java.lang.RuntimeException\n"
            "10-19 23:53:20.004 E/AndroidRuntime: Caused by: java.lang.SecurityException: "
            "requires android.permission.INTERACT_ACROSS_USERS "
            "and random.private.email@example.com ya29.some_auth_token\n"
            "10-19 23:53:20.005 E/AndroidRuntime: at android.os.UserManager.getUsers\n"
        )
        items = summarize_playstore_crash_security_context(raw)
        self.assertEqual(
            [("10-19 23:53:20", ("INTERACT_ACROSS_USERS",), ("USER_MANAGER",))],
            items,
        )
        rendered = build_crash_report(raw, "STANDARD")
        self.assertIn("permissions: INTERACT_ACROSS_USERS", rendered)
        self.assertIn("components: USER_MANAGER", rendered)
        for secret in ("random.private", "example.com", "ya29", "10279"):
            self.assertNotIn(secret, rendered)

    def test_unrecognized_security_message_not_echoed(self):
        raw = (
            "10-19 23:53:26.001 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:53:26.002 E/AndroidRuntime: Process: com.android.vending, PID: 10279\n"
            "10-19 23:53:26.003 E/AndroidRuntime: java.lang.SecurityException: "
            "secret custom vendor operation with token=ya29.KEEP_PRIVATE\n"
        )
        report = build_crash_report(raw, "STANDARD")
        self.assertIn("permissions: NO_KNOWN_MARKER | components: UNKNOWN", report)
        self.assertNotIn("ya29", report)
        self.assertNotIn("secret custom", report)

    def test_unrelated_app_permissions_are_never_reported(self):
        raw = (
            "10-19 23:53:20.001 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:53:20.002 E/AndroidRuntime: Process: com.android.vending, PID: 20\n"
            "10-19 23:53:20.003 E/AndroidRuntime: java.lang.RuntimeException\n"
            "10-19 23:53:21.001 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:53:21.002 E/AndroidRuntime: Process: vendor.other, PID: 21\n"
            "10-19 23:53:21.003 E/AndroidRuntime: java.lang.SecurityException: "
            "MANAGE_USERS permission required in AccountManager\n"
        )
        self.assertEqual([], summarize_playstore_crash_security_context(raw))
        self.assertNotIn("MANAGE_USERS", build_crash_report(raw, "STANDARD"))

    def test_account_manager_context_from_google_login_crash(self):
        raw = (
            "10-19 23:53:30.001 E/AndroidRuntime: FATAL EXCEPTION: main\n"
            "10-19 23:53:30.002 E/AndroidRuntime: Process: com.android.vending, PID: 999\n"
            "10-19 23:53:30.003 E/AndroidRuntime: java.lang.SecurityException: "
            "uid cannot get user data for accounts of type: app.revanced\n"
            "10-19 23:53:30.004 E/AndroidRuntime: at android.accounts.AccountManager.getUserData\n"
        )
        r = build_crash_report(raw, "STANDARD")
        self.assertIn("ACCOUNT_PRIVATE_USERDATA_DENIED", r)
        self.assertIn("components: ACCOUNT_MANAGER", r)



if __name__ == "__main__":
    unittest.main()
