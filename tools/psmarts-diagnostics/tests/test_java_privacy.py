# SPDX-License-Identifier: Apache-2.0
"""Run production form handling against synthetic transport and Android shims.

This is a JVM logging/roundtrip regression, not an Android integration test.
A JDK is required, including in CI; missing javac fails rather than skips tests.
"""
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
TESTS = Path(__file__).resolve().parent


class JavaPrivacyTests(unittest.TestCase):
    def test_form_roundtrip_preserves_credentials_but_never_logs_them(self):
        javac, java = shutil.which("javac"), shutil.which("java")
        self.assertIsNotNone(javac, "JDK required: put javac on PATH")
        self.assertIsNotNone(java, "JDK required: put java on PATH")
        source = ROOT / "play-services-base/core/src/main/java/org/microg/gms/common"
        files = list((TESTS / "java").rglob("*.java")) + [source / "HttpFormClient.java", source / "NotOkayException.java"]
        with tempfile.TemporaryDirectory() as directory:
            result = subprocess.run([javac, "-d", directory, *map(str, files)], capture_output=True, text=True, timeout=60)
            self.assertEqual(result.returncode, 0, result.stderr)
            result = subprocess.run([java, "-cp", directory, "FormPrivacyTest"], capture_output=True, text=True, timeout=30)
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("Form privacy roundtrip: PASS", result.stdout)

    def test_sensitive_auth_and_checkin_log_sites_accept_only_static_messages(self):
        # Regression guard for raw tokens, account Bundles and exception text.
        paths = [
            "play-services-core/src/main/java/org/microg/gms/auth/loginservice/AccountAuthenticator.java",
            "play-services-core/src/main/java/org/microg/gms/auth/AuthManager.java",
            "play-services-core/src/main/java/org/microg/gms/checkin/CheckinClient.java",
            "play-services-core/src/main/java/org/microg/gms/checkin/CheckinService.java",
            "play-services-base/core/src/main/java/org/microg/gms/common/HttpFormClient.java",
        ]
        calls = 0
        for path in paths:
            for statement in re.findall(r'Log\.[dwiev]\(.*?\);', (ROOT / path).read_text(), re.S):
                self.assertRegex(statement, r'^Log\.[dwiev]\(TAG, "[^"\\]*"\);$', path)
                calls += 1
        self.assertGreater(calls, 20)
