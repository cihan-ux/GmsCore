#!/usr/bin/env python3
"""Offline Wallet guardrail tests; no Android SDK, network, or device."""
from pathlib import Path
import unittest

from wallet_trace_report import PATTERNS, render, summarize

ROOT = Path(__file__).resolve().parents[1]
WALLET = ROOT / "play-services-core/src/main/java/org/microg/gms/wallet"

class WalletCompatibilityTests(unittest.TestCase):
    def test_unsupported_first_party_buyflow_not_advertised(self):
        text = (WALLET / "PaymentService.java").read_text(encoding="utf-8")
        self.assertNotIn('new Feature("wallet_1p_initialize_buyflow"', text)

    def test_client_token_fails_closed(self):
        text = (WALLET / "OwServiceImpl.java").read_text(encoding="utf-8")
        self.assertIn("callbacks.onClientTokenReceived(Status.INTERNAL_ERROR,", text)
        self.assertNotIn("callbacks.onClientTokenReceived(Status.SUCCESS,", text)
        self.assertIn("returning INTERNAL_ERROR (unsupported)", text)

    def test_no_sensitive_wallet_request_logging(self):
        text = (WALLET / "OwServiceImpl.java").read_text(encoding="utf-8")
        for forbidden in (
            'request.toJson()', '"getClientToken: " + options',
            'Log.w(TAG, e)', '" + data + "',
        ):
            self.assertNotIn(forbidden, text)

    def test_sanitizer_emits_only_fixed_labels_and_timestamps(self):
        secret = "ya29.super_secret_token private@example.com AndroidID=abc123"
        log = (
            "10-19 00:10:09.200 E/Finsky (22): Failed to load token: 8: Internal error " + secret + "\n"
            "10-19 00:10:09.102 E/Finsky (22): uid 10279 cannot get user data for accounts "
            "of type: app.revanced " + secret + "\n"
            "10-19 00:10:08.500 D/GmsWalletOwSvc: getClientToken requested; "
            "returning INTERNAL_ERROR (unsupported) " + secret
        )
        counts, last_seen = summarize(log)
        self.assertEqual(1, counts["MICROG_WALLET_CLIENT_TOKEN_UNSUPPORTED"])
        self.assertEqual(1, counts["PLAY_STORE_CLIENT_TOKEN_INTERNAL_ERROR"])
        self.assertEqual(1, counts["PLAY_STORE_ACCOUNT_PRIVATE_DATA_DENIED"])
        out = render(counts, last_seen)
        for sensitive in ("ya29", "private@", "abc123", "10279", "super_secret"):
            self.assertNotIn(sensitive, out)
        self.assertIn("00:10:09", out)

    def test_no_arbitrary_log_text_reflected(self):
        counts, last_seen = summarize("E/Other: password=DO_NOT_ECHO auth=ya29.SECRET")
        self.assertEqual(0, sum(counts.values()))
        report = render(counts, last_seen)
        self.assertNotIn("DO_NOT_ECHO", report)
        self.assertNotIn("ya29", report)

    def test_all_event_ids_unique(self):
        self.assertEqual(len(PATTERNS), len(set(event for event, _ in PATTERNS)))

if __name__ == "__main__":
    unittest.main()
