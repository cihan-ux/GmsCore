# SPDX-License-Identifier: Apache-2.0
"""Offline tests: no device, Google credentials or network required."""
import importlib.util
from pathlib import Path
import tempfile
import unittest
import json

MODULE = Path(__file__).resolve().parents[1] / "diagnose.py"
spec = importlib.util.spec_from_file_location("psmarts_diag", MODULE)
diag = importlib.util.module_from_spec(spec)
spec.loader.exec_module(diag)

class DiagnosticTests(unittest.TestCase):
    def test_parse_user_package_and_manifest(self):
        sample = """Packages:
    codePath=/data/app/com.android.vending-ABC
    versionCode=81951300 minSdk=16 targetSdk=29
    versionName=19.5.13
    privateFlags=[ HAS_DOMAIN_URLS ]
    requested permissions:
      android.permission.INSTALL_PACKAGES
      android.permission.DELETE_PACKAGES
    install permissions:
      android.permission.INSTALL_PACKAGES: granted=false
"""
        parsed = diag.parse_package(sample)
        self.assertEqual(parsed["location_class"], "user_app")
        self.assertEqual(parsed["version"], "19.5.13")
        self.assertTrue(parsed["requested_install_permissions"]["android.permission.INSTALL_PACKAGES"])
        self.assertFalse(parsed["requested_install_permissions"]["android.permission.REQUEST_INSTALL_PACKAGES"])
        self.assertFalse(parsed["privileged_flag"])

    def test_installer_sessions_and_uid(self):
        sample = """Active install sessions:
  Session 73517775:
    userId=0 mOriginalInstallerUid=10250 mInstallerPackageName=com.android.vending mInstallerUid=10250
    mClientProgress=0.0 mProgress=0.0 mCommitted=true mSealed=true
  Session 999:
    userId=0 mOriginalInstallerUid=10136 mInstallerPackageName=com.android.vending mInstallerUid=10136
    mClientProgress=0.2 mProgress=0.2 mCommitted=false mSealed=false
  Session 333:
    mInstallerPackageName=dev.example.other mInstallerUid=444
"""
        out = diag.parse_sessions(sample)
        self.assertEqual([s["id"] for s in out], [999, 73517775])
        self.assertEqual(out[1]["installer_uid"], 10250)
        self.assertTrue(out[1]["committed"])

    def test_log_summarizer_does_not_leak_tokens(self):
        sensitive = ("D GmsAuthenticator: getAuthToken: "
                     "ya29.SYNTHETIC_TOKEN_DO_NOT_SHARE\n"
                     "W GooglePlayServicesUtil: com.android.vending requires Google Play "
                     "services, but their signature is invalid.\n"
                     "user@example.org https://private.example/?token=SECRET\n")
        summary = diag.summarize_logcat(sensitive)
        self.assertEqual(summary["gms_signature_invalid"], 1)
        self.assertNotIn("SYNTHETIC_TOKEN", json.dumps(summary))
        self.assertNotIn("user@example.org", json.dumps(summary))

    def test_compare_distinguishes_old_uid(self):
        before = {"vending_installer_sessions": [{"id": 7, "installer_uid": 10250,
                                                  "progress": 0.0}]}
        after = {"vending_installer_sessions": [
            {"id": 7, "installer_uid": 10250, "progress": 0.0},
            {"id": 10, "installer_uid": 10136, "progress": 0.0}],
            "packages": {"com.android.vending": {"uid": 10136}}}
        result = diag.compare(before, after)
        self.assertEqual(result["new_session_ids"], [10])
        self.assertEqual(result["old_uid_session_ids"], [7])

    def test_permission_status(self):
        def fake_runner(*args):
            return "-1\n"
        self.assertEqual(diag._get_permission(fake_runner,
                         "com.android.vending", "android.permission.INSTALL_PACKAGES"),
                         "denied")

    def test_json_private_mode(self):
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "snapshot.json"
            diag.private_json(path, {"log_event_counts": {"install_failed": 0}})
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            self.assertEqual(json.loads(path.read_text())["log_event_counts"]["install_failed"], 0)

    def test_snapshot_with_fake_adb_and_no_raw_tokens(self):
        versions = {
            "com.google.android.gms": "versionName=0.3.17.252432-hw\n",
            "com.android.vending": """versionName=19.5.13
codePath=/data/app/com.android.vending-XYZ
requested permissions:
  android.permission.INSTALL_PACKAGES
install permissions:
""",
            "com.google.android.gsf": "versionName=v0.1.0\n",
        }
        def runner(*args):
            if args[:3] == ("shell", "getprop", args[2] if len(args) > 2 else None):
                return "AQM-L21A\n"
            if args == ("shell", "pm", "list", "packages", "-U"):
                return "package:com.android.vending uid:10136\n"
            if args == ("shell", "pm", "list", "packages"):
                return "".join("package:" + p + "\n" for p in diag.PACKAGES)
            if args == ("shell", "pm", "list", "packages", "-d"):
                return ""
            if len(args) == 4 and args[:3] == ("shell", "dumpsys", "package"):
                return versions.get(args[3], "")
            if len(args) == 7 and args[1:4] == ("dumpsys", "package", "check-permission"):
                return "-1\n"
            if args[:3] == ("shell", "cmd", "appops"):
                return "No operations.\nDefault mode: default\n"
            if args == ("shell", "dumpsys", "package", "installs"):
                return "Active install sessions:\n"
            if args[0] == "logcat":
                return "D/GmsAuthenticator: getAuthToken: ya29.SYNTHETIC"
            raise AssertionError(args)
        result = diag.snapshot(runner=runner)
        self.assertEqual(result["packages"]["com.android.vending"]["uid"], 10136)
        self.assertNotIn("ya29.", json.dumps(result))
        self.assertFalse(result["huawei_signature_components_in_gms_apk"]
                         ["com.huawei.signature.diff.SignatureService"])

if __name__ == "__main__":
    unittest.main()
