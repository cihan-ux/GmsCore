# SPDX-License-Identifier: Apache-2.0
"""Synthetic offline tests: no ADB, device logs, Google account or network."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

HERE = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location("psmarts_trial", HERE / "trial.py")
trial = importlib.util.module_from_spec(spec)
spec.loader.exec_module(trial)


def before():
    return {
        "schema": 2, "user_id": 0,
        "device": {"model": "AQM-LX1", "android": "10"},
        "packages": {"com.google.android.gms": {"installed": True},
                     "com.android.vending": {"installed": True}},
    }


def after():
    value = before()
    value["huawei_signature_service_running_hint"] = True
    value["huawei_signature_components_in_gms_apk"] = {
        "com.huawei.signature.diff.SignatureService": True
    }
    return value


def diff(new=None, available=True):
    return {"session_comparison_available": available,
            "new_current_vending_session_ids": [] if new is None else new,
            "vending_uid_changed": False}


def events(**overrides):
    counts = dict.fromkeys(trial.KNOWN_EVENTS, 0)
    counts.update(overrides)
    return {"schema": 1, "collector_status": "capture_window_completed",
            "counts": counts,
            "raw_logs_written": False}


class TrialTests(unittest.TestCase):
    def test_account_and_signature_events_not_claimed_as_cause(self):
        data = trial.assessment(before(), after(), diff(),
                                events(gms_signature_rejected=5,
                                       finsky_catalog_hint=20, gms_auth_request=14))
        self.assertEqual(data["observation_stage"],
                         "catalog_auth_or_signature_events_only")
        self.assertEqual(data["signature_check_caused_install_failure"], "unproven")
        self.assertEqual(data["huawei_firmware_integration"], "unknown")

    def test_installer_session_is_strongest_observed_stage(self):
        summary = trial.assessment(before(), after(), diff(new=[9, 10]),
                                   events(finsky_install_hint=2))
        self.assertEqual(summary["observation_stage"],
                         "current_vending_installer_session_observed")
        self.assertEqual(summary["new_current_vending_session_count"], 2)

    def test_installer_event_is_only_a_hint(self):
        summary = trial.assessment(before(), after(), diff(),
                                   events(installer_component_hint=3))
        self.assertEqual(summary["observation_stage"], "installer_component_log_hint_only")
        self.assertEqual(summary["new_current_vending_session_count"], 0)

    def test_download_hint_is_not_download_proof(self):
        summary = trial.assessment(before(), after(), diff(),
                                   events(download_component_hint=1))
        self.assertEqual(summary["observation_stage"], "delivery_or_download_log_hint_only")
        self.assertEqual(summary["playstore_delivery_response"], "unknown")

    def test_unavailable_logcat_keeps_unknown_counts(self):
        summary = trial.assessment(before(), after(), diff(), {"schema": 1,
            "collector_status": "unavailable", "counts": {"gms_signature_rejected": 99}})
        self.assertIsNone(summary["event_counts"]["gms_signature_rejected"])
        self.assertEqual(summary["observation_stage"], "no_discriminating_events_observed")

    def test_unavailable_sessions_are_unknown_not_zero(self):
        summary = trial.assessment(before(), after(), diff(available=False), events())
        self.assertFalse(summary["session_comparison_available"])
        self.assertIsNone(summary["new_current_vending_session_count"])

    def test_malformed_events_dont_leak_strings(self):
        e = events()
        e["counts"]["gms_auth_request"] = "ya29.SECRET_PERSON@example.org"
        summary = trial.assessment(before(), after(), diff(), e)
        self.assertNotIn("ya29.", json.dumps(summary))
        self.assertIsNone(summary["event_counts"]["gms_auth_request"])

    def test_invalid_schema_and_device_rejected(self):
        wrong = after()
        wrong["user_id"] = 10
        with self.assertRaises(ValueError):
            trial.assessment(before(), wrong, diff(), events())
        wrong["user_id"] = 0
        wrong["schema"] = 1
        with self.assertRaises(ValueError):
            trial.assessment(before(), wrong, diff(), events())
        wrong["schema"] = 2
        wrong["device"]["model"] = "different"
        with self.assertRaises(ValueError):
            trial.assessment(before(), wrong, diff(), events())

    def test_trial_writes_only_private_allowlisted_reports(self):
        class FakeRunner:
            def __call__(self, *args):
                self.assertions = args
                return "device\n"
        synthetic = events(gms_signature_rejected=5)
        with tempfile.TemporaryDirectory() as td, \
             patch.object(trial.diagnose, "Adb", return_value=FakeRunner()), \
             patch.object(trial.diagnose, "snapshot", side_effect=[before(), after()]), \
             patch.object(trial.diagnose, "compare", return_value=diff()), \
             patch.object(trial.live_trace, "capture", return_value=synthetic):
            run = trial.run(Path(td), prompt=lambda _: "")
            self.assertEqual(sorted(p.name for p in run.iterdir()),
                             ["after.json", "before.json", "comparison.json",
                              "events.json", "summary.json"])
            self.assertEqual(run.stat().st_mode & 0o777, 0o700)
            for file in run.iterdir():
                self.assertEqual(file.stat().st_mode & 0o777, 0o600)
                self.assertNotIn("ya29.", file.read_text())
            report = json.loads((run / "summary.json").read_text())
            self.assertEqual(report["event_counts"]["gms_signature_rejected"], 5)

    def test_no_live_does_not_read_logcat(self):
        class FakeRunner:
            def __call__(self, *args):
                return "device"
        with tempfile.TemporaryDirectory() as td, \
             patch.object(trial.diagnose, "Adb", return_value=FakeRunner()), \
             patch.object(trial.diagnose, "snapshot", side_effect=[before(), after()]), \
             patch.object(trial.diagnose, "compare", return_value=diff()), \
             patch.object(trial.live_trace, "capture",
                          side_effect=AssertionError("must not collect")):
            run = trial.run(Path(td), no_live=True, prompt=lambda _: "")
            self.assertFalse((run / "events.json").exists())
            summary = json.loads((run / "summary.json").read_text())
            self.assertIsNone(summary["event_counts"]["gms_signature_rejected"])

    def test_logcat_failure_preserves_session_comparison(self):
        class FakeRunner:
            def __call__(self, *args):
                return "device"
        with tempfile.TemporaryDirectory() as td, \
             patch.object(trial.diagnose, "Adb", return_value=FakeRunner()), \
             patch.object(trial.diagnose, "snapshot", side_effect=[before(), after()]), \
             patch.object(trial.diagnose, "compare", return_value=diff(new=[101])), \
             patch.object(trial.live_trace, "capture", side_effect=RuntimeError("token")):
            run = trial.run(Path(td), prompt=lambda _: "")
            summary = json.loads((run / "summary.json").read_text())
            self.assertNotIn("token", json.dumps(summary))
            self.assertEqual(summary["new_current_vending_session_count"], 1)
            self.assertEqual(json.loads((run / "events.json").read_text())["collector_status"],
                             "unavailable")


if __name__ == "__main__":
    unittest.main()
