"""Offline tests: no phone, credentials or ADB needed."""
import unittest
from unittest.mock import patch
from tools.huawei_google_login_preflight import parse_package, classify_gsf_crashes, main


class GsfPreflightTest(unittest.TestCase):
    def test_privileged_false(self):
        d = " userId=10334\n versionCode=29 minSdk=29\n privateFlags=[ HAS_CODE ]\n sharedUser=SharedUserSetting{abc com.google.uid.shared/10334}\n"
        p = parse_package(d, "package:/data/app/gsf/base.apk\n")
        self.assertEqual(p["uid"], "10334")
        self.assertEqual(p["shared_user"], "com.google.uid.shared")
        self.assertEqual(p["privileged"], False)

    def test_missing_package(self):
        self.assertFalse(parse_package(None, "")["installed"])

    def test_crash_redacts_secrets(self):
        raw = ("10-20 16:13:16 FATAL EXCEPTION: main\n"
               "10-20 16:13:16 Process: com.google.process.gapps, PID: 1234\n"
               "10-20 16:13:16 java.lang.SecurityException: secret ya29.NOT_FOR_OUTPUT\n"
               "10-20 16:13:16 at com.google.android.gsf.settings.GoogleSettingsProvider.onCreate(X)\n"
               "10-20 16:13:16 at android.os.UserManager.getProfileParent(Y)\n"
               "10-20 16:13:16 account=private@example.com")
        out = classify_gsf_crashes(raw)
        self.assertEqual(out, [("10-20 16:13:16", "GSF_PROFILE_PARENT_PERMISSION_FAILURE")])
        self.assertNotIn("ya29.", str(out))
        self.assertNotIn("private@example.com", str(out))

    def test_other_crash_classification(self):
        raw = "10-20 16:13:16 FATAL EXCEPTION: main\nProcess: com.google.process.gapps, PID: 8\nSecurityException"
        self.assertEqual(classify_gsf_crashes(raw)[0][1], "GSF_OTHER_SECURITY_FAILURE")


if __name__ == "__main__":
    unittest.main()
