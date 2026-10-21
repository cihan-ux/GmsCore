# SPDX-License-Identifier: Apache-2.0
"""Offline privacy/causality guardrails for the read-only live event probe."""
import importlib.util
import json
import pathlib
import tempfile
import unittest

HERE = pathlib.Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location("psmarts_live_trace", HERE / "live_trace.py")
probe = importlib.util.module_from_spec(spec)
spec.loader.exec_module(probe)

class LiveTraceTests(unittest.TestCase):
    def test_signature_warning(self):
        line = ("W/GooglePlayServicesUtil(18224): com.android.vending requires "
                "Google Play services, but their signature is invalid.")
        self.assertEqual(probe.classify_log_line(line), "gms_signature_rejected")

    def test_huawei_service_start_is_just_hint(self):
        self.assertEqual(
            probe.classify_log_line("I/SignatureService(10646): onStartCommand"),
            "huawei_service_start")
        self.assertEqual(
            probe.classify_log_line("W/SignatureService(10646): Illegal access from app"),
            "huawei_binder_denied")

    def test_token_never_leaks(self):
        raw = ("D/GmsAuthenticator(10646): getAuthToken: ya29.SYNTHETIC_TOKEN_ABC123 "
               "Account {name=very_private@example.org}\n")
        timeline = probe.SafeTimeline()
        timeline.add(raw, 2.23)
        serialized = json.dumps(timeline.result())
        self.assertNotIn("ya29.", serialized)
        self.assertNotIn("very_private", serialized)
        self.assertEqual(timeline.result()["counts"]["gms_auth_request"], 1)

    def test_finsky_catalog_not_install(self):
        timeline = probe.SafeTimeline()
        timeline.add("I/Finsky  (18224): Page [class=ipf] loaded in [150 ms]", 0.4)
        self.assertEqual(timeline.result()["counts"]["finsky_catalog_hint"], 1)
        self.assertEqual(timeline.result()["counts"]["finsky_install_hint"], 0)

    def test_finsky_install_is_not_success(self):
        self.assertEqual(
            probe.classify_log_line("I/Finsky(1): Starting download transaction"),
            "finsky_install_hint")

    def test_component_attribution_prevents_random_logs(self):
        self.assertIsNone(
            probe.classify_log_line("E/Keyboard(77): PackageInstaller failed: ya29.TOKEN"))
        self.assertIsNone(
            probe.classify_log_line("E/UnknownApp(44): com.android.vending requires Google Play services, but their signature is invalid."))

    def test_installer_event(self):
        self.assertEqual(
            probe.classify_log_line("I/PackageInstaller(44): create install session"),
            "installer_component_hint")

    def test_max_samples_bounded_without_losing_counts(self):
        timeline = probe.SafeTimeline()
        for i in range(500):
            timeline.add("D/GmsAuthenticator(123): getAuthToken: secret", i / 10)
        result = timeline.result()
        self.assertEqual(result["counts"]["gms_auth_request"], 500)
        self.assertLessEqual(len(result["event_samples"]), probe.MAX_SAMPLES_PER_KIND)
        self.assertGreater(len(result["five_second_buckets"]), 1)

    def test_unknown_log_is_dropped(self):
        self.assertIsNone(probe.classify_log_line(
            "D/GmsAuthenticator(123): secret token returned, do not share"))
        self.assertIsNone(probe.classify_log_line("arbitrary newline without log tag"))

    def test_private_report_no_overwrite(self):
        with tempfile.TemporaryDirectory() as directory:
            path = pathlib.Path(directory) / "trace.json"
            probe.private_output(path, {"counts": {"gms_auth_request": 2}})
            self.assertEqual(path.stat().st_mode & 0o777, 0o600)
            with self.assertRaises(FileExistsError):
                probe.private_output(path, {"overwritten": True})
            self.assertNotIn("overwritten", path.read_text())

    def test_bad_capture_window_rejected_before_spawn(self):
        with self.assertRaises(ValueError):
            probe.capture(seconds=9, popen=lambda *_args, **_kwargs:
                          self.fail("ADB must not be started"))
        with self.assertRaises(ValueError):
            probe.capture(seconds=181, popen=lambda *_args, **_kwargs:
                          self.fail("ADB must not be started"))

if __name__ == "__main__":
    unittest.main()
