# SPDX-License-Identifier: Apache-2.0
"""Synthetic fixtures only. No phone, real credentials or network required."""
import contextlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / "diagnose.py"
spec = importlib.util.spec_from_file_location("psmarts_diag", MODULE)
diag = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diag)

PACKAGE = """Packages:
  Package [com.android.vending]:
    codePath=/data/app/com.android.vending-RANDOM/base.apk
    versionCode=81951300 minSdk=16 targetSdk=29
    versionName=19.5.13
    flags=[ HAS_CODE ]
    privateFlags=[ HAS_DOMAIN_URLS ]
    requested permissions:
      android.permission.INSTALL_PACKAGES
    install permissions:
      android.permission.INSTALL_PACKAGES: granted=false
"""
SESSIONS = """Active install sessions:
  Session 999:
    userId=0 mInstallerPackageName=com.android.vending mInstallerUid=10136
    mProgress=0.2 mCommitted=false mSealed=false
  Session 123:
    userId=10 mInstallerPackageName=com.android.vending mInstallerUid=1010136
  Session 333:
    userId=0 mInstallerPackageName=com.android.vending.evil mInstallerUid=444
Historical install sessions:
  Session 73517775:
    userId=0 mInstallerPackageName=com.android.vending mInstallerUid=10250
    mProgress=0.0 mCommitted=true mSealed=true mFinalStatus=-1
  Session 888:
    userId=0 mInstallerPackageName=com.android.vending
"""
SECRET_LOG = """D/GmsAuthenticator(123): getAuthToken: ya29.SYNTHETIC_TOKEN
W/GooglePlayServicesUtil(123): com.android.vending requires Google Play services, but their signature is invalid.
D/GmsHttpFormClient: Email=person@example.org&Token=UNRECOGNIZED_SECRET
D/GmsCheckinSvc: Checked in as 123456789abcdef
D/SignatureService: onStartCommand
Authorization: Bearer SYNTHETIC_BEARER
Cookie: oauth_token=SYNTHETIC_COOKIE
https://private.example/path?auth=TOKEN
android_id=123456789 secret=SYNTHETIC
"""


class FakeAdb:
    def __init__(self, failures=()):
        self.calls = []
        self.failures = set(failures)

    def __call__(self, *args):
        self.calls.append(args)
        if args in self.failures:
            raise RuntimeError("SENSITIVE_REMOTE_ERROR person@example.org")
        if args[:2] == ("shell", "getprop"):
            return "AQM-L21A\n"
        if args[:4] == ("shell", "pm", "list", "packages"):
            if "-U" in args:
                return "package:com.android.vending uid:10136\n"
            if "-d" in args:
                return ""
            return "".join("package:" + p + "\n" for p in diag.PACKAGES + diag.SYSTEM_PACKAGES)
        if args == ("shell", "dumpsys", "package", "installs"):
            return SESSIONS
        if args[:4] == ("shell", "dumpsys", "package", "check-permission"):
            return "-1\n"
        if args[:3] == ("shell", "dumpsys", "package"):
            return PACKAGE + "\n" + "\n".join(diag.HUAWEI_COMPONENTS)
        if args[:3] == ("shell", "dumpsys", "activity"):
            return "ServiceRecord com.google.android.gms/com.huawei.signature.diff.SignatureService"
        if args[:3] == ("shell", "cmd", "appops"):
            return "No operations.\nDefault mode: default\n"
        if args[0] == "logcat":
            return SECRET_LOG
        if args == ("get-state",):
            return "device\n"
        raise AssertionError(args)


class DiagnosticTests(unittest.TestCase):
    def test_package_permissions_and_data_path_do_not_invent_privilege(self):
        parsed = diag.parse_package(PACKAGE)
        self.assertEqual(parsed["location_class"], "data_app")
        self.assertEqual(parsed["version"], "19.5.13")
        self.assertEqual(parsed["target_sdk"], "29")
        self.assertTrue(parsed["requested_install_permissions"][diag.INSTALL_PERMS[0]])
        self.assertFalse(parsed["requested_install_permissions"][diag.INSTALL_PERMS[1]])
        self.assertFalse(parsed["privileged_flag"])

    def test_updated_system_app_is_not_classified_as_unprivileged(self):
        raw = PACKAGE.replace("flags=[ HAS_CODE ]", "flags=[ SYSTEM UPDATED_SYSTEM_APP ]").replace("privateFlags=[ HAS_DOMAIN_URLS ]", "privateFlags=[ PRIVILEGED ]")
        parsed = diag.parse_package(raw)
        self.assertEqual(parsed["location_class"], "data_app")
        self.assertTrue(parsed["system_flag"])
        self.assertTrue(parsed["privileged_flag"])

    def test_hidden_factory_package_does_not_override_installed_metadata(self):
        raw = PACKAGE + "Hidden system packages:\nversionName=factory\nrequested permissions:\nandroid.permission.REQUEST_INSTALL_PACKAGES\n"
        parsed = diag.parse_package(raw)
        self.assertEqual(parsed["version"], "19.5.13")
        self.assertFalse(parsed["requested_install_permissions"][diag.INSTALL_PERMS[1]])

    def test_missing_fields_are_unknown_not_false(self):
        parsed = diag.parse_package("")
        self.assertIsNone(parsed["privileged_flag"])
        self.assertIsNone(parsed["requested_install_permissions"][diag.INSTALL_PERMS[1]])
        session = diag.parse_sessions(SESSIONS)[1]
        self.assertIsNone(session["committed"])

    def test_sessions_preserve_history_user_ownership_and_unknown_fields(self):
        sessions = {s["id"]: s for s in diag.parse_sessions(SESSIONS)}
        self.assertNotIn(333, sessions)
        self.assertEqual(sessions[999]["bucket"], "active")
        self.assertEqual(sessions[73517775]["bucket"], "historical")
        self.assertEqual(sessions[123]["user_id"], 10)
        self.assertIsNone(sessions[888]["installer_uid"])
        self.assertEqual(sessions[73517775]["final_status"], -1)

    def test_log_counters_discard_every_raw_value(self):
        counts = diag.summarize_logcat(SECRET_LOG)
        self.assertEqual(counts["gms_signature_invalid"], 1)
        self.assertEqual(counts["checkin_success_hint"], 1)
        self.assertTrue(all(isinstance(n, int) for n in counts.values()))
        self.assert_private(counts)

    def assert_private(self, value):
        data = json.dumps(value)
        for marker in ("ya29.", "person@", "SYNTHETIC", "private.example", "123456789abcdef", "UNRECOGNIZED_SECRET", "SENSITIVE_REMOTE_ERROR"):
            self.assertNotIn(marker, data)

    def test_full_snapshot_reads_only_and_never_claims_spoofing(self):
        runner = FakeAdb()
        report = diag.snapshot(runner)
        self.assertEqual(report["packages"][diag.PACKAGES[1]]["uid"], 10136)
        self.assertEqual(report["huawei_firmware_integration"], "unknown")
        self.assertEqual(report["caller_visible_gms_signature"], "unknown")
        self.assertTrue(report["huawei_signature_service_running_hint"])
        self.assertEqual(report["vending_request_install_appop"], "not_recorded")
        self.assert_private(report)
        allowed = {("shell", "getprop"), ("shell", "pm"), ("shell", "dumpsys"), ("shell", "cmd")}
        for command in runner.calls:
            if command[0] == "logcat":
                self.assertIn("-d", command)
                self.assertNotIn("-c", command)
            else:
                self.assertIn(command[:2], allowed)
                self.assertNotIn("grant", command)
                self.assertNotIn("set", command)
                self.assertNotIn("install", command)
                if command[:2] == ("shell", "cmd"):
                    self.assertEqual(command[2:4], ("appops", "get"))
                if command[:2] == ("shell", "pm"):
                    self.assertEqual(command[2:4], ("list", "packages"))

    def test_unavailable_reads_do_not_mean_absent_packages_or_sessions(self):
        failures = [("shell", "pm", "list", "packages", "--user", "0"),
                    ("shell", "dumpsys", "package", "installs"),
                    ("shell", "cmd", "appops", "get", "--user", "0", diag.PACKAGES[1], "REQUEST_INSTALL_PACKAGES")]
        report = diag.snapshot(FakeAdb(failures))
        self.assertIsNone(report["packages"][diag.PACKAGES[0]]["installed"])
        self.assertEqual(report["installer_session_visibility"], "unknown")
        self.assertEqual(report["vending_request_install_appop"], "unknown")
        self.assertIsNone(diag.compare(report, report)["new_session_ids"])
        self.assert_private(report)

    def test_unrecognized_dumpsys_format_is_not_empty_session_evidence(self):
        def runner(*args):
            if args == ("shell", "dumpsys", "package", "installs"):
                return "OEM output changed\n"
            return FakeAdb()(*args)
        self.assertEqual(diag.snapshot(runner)["installer_session_visibility"], "unknown")

    def test_compare_history_transition_is_not_new_install(self):
        before = diag.snapshot(FakeAdb(), include_logcat=False)
        after = json.loads(json.dumps(before))
        next(s for s in after["vending_installer_sessions"] if s["id"] == 999)["bucket"] = "historical"
        compared = diag.compare(before, after)
        self.assertEqual(compared["new_session_ids"], [])
        self.assertEqual(compared["old_uid_session_ids"], [73517775])
        self.assertEqual(compared["other_user_session_ids"], [123])
        self.assertEqual(compared["changed_sessions"][0]["id"], 999)

    def test_compare_never_copies_unknown_session_fields_or_secret_ids(self):
        before = diag.snapshot(FakeAdb(), include_logcat=False)
        after = json.loads(json.dumps(before))
        after["vending_installer_sessions"][0]["raw_log"] = SECRET_LOG
        after["vending_installer_sessions"][0]["committed"] = True
        self.assert_private(diag.compare(before, after))
        after["vending_installer_sessions"][0]["installer_uid"] = "SYNTHETIC"
        with self.assertRaises(ValueError):
            diag.compare(before, after)

    def test_compare_rejects_different_users_and_schema(self):
        before = diag.snapshot(FakeAdb(), include_logcat=False)
        for change in ({"user_id": 10}, {"schema": 1}, {"device": {"model": "different"}}):
            with self.assertRaises(ValueError):
                diag.compare(before, dict(before, **change))

    def test_no_logcat_option_does_not_read_logs(self):
        runner = FakeAdb()
        self.assertIsNone(diag.snapshot(runner, include_logcat=False)["log_event_counts"])
        self.assertFalse(any(c[0] == "logcat" for c in runner.calls))

    def test_permission_denied_unsupported_and_errors_are_distinct(self):
        for raw, expected in (("-1\n", "denied"), ("0\n", "granted"), ("Permission Denial", "unknown")):
            self.assertEqual(diag._get_permission(lambda *args: raw, diag.PACKAGES[1], diag.INSTALL_PERMS[0]), expected)

    def test_private_output_refuses_overwrite_symlink_and_hardlink(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "report.json"
            diag.private_json(path, {"safe": True})
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            for other in (path, Path(directory) / "link", Path(directory) / "hardlink"):
                if other.name == "link":
                    other.symlink_to(path)
                elif other.name == "hardlink":
                    os.link(path, other)
                with self.assertRaises(FileExistsError):
                    diag.private_json(other, {"overwrite": True})
            self.assertEqual(json.loads(path.read_text()), {"safe": True})

    def test_apk_verifier_drops_dn_and_pulls_only_to_temporary_directory(self):
        destinations = []
        def runner(*args):
            if args[:3] == ("shell", "pm", "path"):
                return "package:/data/app/example/base.apk\npackage:/data/app/example/split.apk\n"
            if args[0] == "pull":
                destinations.append(Path(args[2]))
                destinations[-1].write_bytes(b"synthetic APK")
                return "person@example.org copied"
            self.fail(args)
        result = subprocess.CompletedProcess([], 0, "Signer #1 certificate DN: person@example.org\nSigner #1 certificate SHA-256 digest: " + "a" * 64 + "\n", "SYNTHETIC")
        with patch.object(diag.subprocess, "run", return_value=result):
            report = diag.inspect_apks(runner, diag.PACKAGES[1], "/trusted/apksigner")
        self.assertTrue(all(a["signature_verified"] for a in report["artifacts"]))
        self.assertEqual(len(report["artifacts"]), 2)
        self.assertFalse(any(p.exists() for p in destinations))
        self.assert_private(report)

    def test_apk_failure_is_not_success_and_bad_paths_are_never_pulled(self):
        runner = lambda *args: "package:/data/app/bad;command.apk\n"
        self.assertEqual(diag.inspect_apks(runner, diag.PACKAGES[1], "/trusted/apksigner")["status"], "unavailable_or_unsupported_path")
        self.assertEqual(diag.inspect_apks(runner, diag.PACKAGES[1], None)["status"], "apksigner_unavailable")

    def test_cli_compare_and_no_sensitive_exception_output(self):
        with tempfile.TemporaryDirectory() as directory:
            p = Path(directory)
            snapshot = diag.snapshot(FakeAdb(), include_logcat=False)
            (p / "before.json").write_text(json.dumps(snapshot))
            (p / "after.json").write_text(json.dumps(snapshot))
            self.assertEqual(diag.main(["compare", "--before", str(p / "before.json"), "--after", str(p / "after.json"), "--output", str(p / "diff.json")]), 0)
            err = io.StringIO()
            with contextlib.redirect_stderr(err), patch.object(diag, "Adb", side_effect=RuntimeError(SECRET_LOG)):
                self.assertEqual(diag.main(["snapshot", "--output", str(p / "fail.json")]), 1)
            self.assert_private(err.getvalue())
            self.assertFalse((p / "fail.json").exists())

    def test_serial_selection_is_a_local_option_and_errors_are_generic(self):
        error = subprocess.CompletedProcess([], 1, "", "SYNTHETIC serial and account")
        with patch.object(diag.subprocess, "run", return_value=error) as run:
            with self.assertRaisesRegex(RuntimeError, "^ADB read failed$"):
                diag.Adb("synthetic-serial")("get-state")
            self.assertEqual(run.call_args.args[0], ["adb", "-s", "synthetic-serial", "get-state"])


if __name__ == "__main__":
    unittest.main()
