"""Privacy and parsing tests using synthetic, nonreal Android 10 dumpsys data."""

import unittest

from chrome_visibility_dumpsys import report, summarize


class ChromeVisibilityDumpTests(unittest.TestCase):
    def test_google_account_visibility_and_redaction(self):
        sensitive = """User UserInfo{0:Owner:13}:
 Accounts: 1
  Account {name=alice@example.invalid, type=com.google}

 Active Sessions: 1
  opaque-session-secret-999

 Account visibility:
  alice@example.invalid
  com.android.chrome, 2
  android:accounts:key_legacy_not_visible, 4
  android:accounts:key_legacy_visible, 1
"""
        audit = summarize(sensitive)
        self.assertEqual(audit.sections, 1)
        self.assertEqual(audit.google_accounts_listed, 1)
        self.assertEqual(audit.matched_google_labels, 1)
        self.assertEqual(audit.counts["com.android.chrome"][2], 1)
        self.assertEqual(
            audit.counts["android:accounts:key_legacy_not_visible"][4], 1
        )
        output = report(sensitive)
        self.assertIn("Chrome (explicit): 2 (USER_MANAGED_VISIBLE) x1", output)
        for secret in ["alice@example.invalid", "opaque-session-secret-999",
                       "Account {", "Active Sessions"]:
            self.assertNotIn(secret, output)

    def test_unrelated_account_type_is_not_attributed_to_google(self):
        raw = """User UserInfo{0:Owner:13}:
 Accounts: 2
  Account {name=g@example.invalid, type=com.google}
  Account {name=other@example.invalid, type=com.example}
 Account visibility:
  g@example.invalid
  android:accounts:key_legacy_visible, 1
  other@example.invalid
  com.android.chrome, 3
"""
        a = summarize(raw)
        self.assertEqual(a.google_accounts_listed, 1)
        self.assertEqual(a.matched_google_labels, 1)
        self.assertEqual(a.counts["com.android.chrome"][3], 0)
        self.assertEqual(a.counts["android:accounts:key_legacy_visible"][1], 1)
        self.assertIn("GOOGLE HESABINA AİT AÇIK KAYIT YOK", report(raw))
        self.assertNotIn("other@example.invalid", report(raw))

    def test_unknown_account_label_stops_attribution(self):
        raw = """User UserInfo{0:Owner:13}:
 Accounts: 1
  Account {name=g@example.invalid, type=com.google}
 Account visibility:
  g@example.invalid
  com.android.chrome, 2
  unlisted@example.invalid
  com.android.chrome, 4
"""
        a = summarize(raw)
        self.assertEqual(a.counts["com.android.chrome"][2], 1)
        self.assertEqual(a.counts["com.android.chrome"][4], 0)

    def test_unrelated_package_entry_does_not_break_account(self):
        raw = """Accounts: 1
 Account {name=g@example.invalid, type=com.google}
Account visibility:
 g@example.invalid
 com.fake.chrome, 1
 com.android.chrome, 2
"""
        self.assertEqual(summarize(raw).counts["com.android.chrome"][2], 1)

    def test_no_visibility_section_inconclusive_and_redacted(self):
        o = report("""User UserInfo{0:Owner:13}:
 Accounts: 1
 Account {name=hidden@example.invalid, type=com.google}
""")
        self.assertIn("BELİRSİZ", o)
        self.assertNotIn("hidden@example.invalid", o)

    def test_multiple_users_separate_identities(self):
        raw = """User UserInfo{0:Owner:13}:
 Accounts: 1
  Account {name=shared@example.invalid, type=com.google}
 Account visibility:
  shared@example.invalid
  com.android.chrome, 2
User UserInfo{10:Profile:13}:
 Accounts: 1
  Account {name=shared@example.invalid, type=com.example}
 Account visibility:
  shared@example.invalid
  com.android.chrome, 4
"""
        a = summarize(raw)
        self.assertEqual(a.sections, 2)
        self.assertEqual(a.google_accounts_listed, 1)
        self.assertEqual(a.counts["com.android.chrome"][2], 1)
        self.assertEqual(a.counts["com.android.chrome"][4], 0)

    def test_duplicate_same_name_different_types_is_ambiguous(self):
        raw = """User UserInfo{0:Owner:13}:
 Accounts: 2
  Account {name=same@example.invalid, type=com.google}
  Account {name=same@example.invalid, type=com.example}
 Account visibility:
  same@example.invalid
  com.android.chrome, 2
"""
        a = summarize(raw)
        self.assertEqual(a.google_accounts_listed, 1)
        self.assertEqual(a.matched_google_labels, 0)
        self.assertEqual(a.ambiguous_labels, 1)
        self.assertEqual(a.counts["com.android.chrome"][2], 0)
        self.assertIn("BELİRSİZ", report(raw))
        self.assertNotIn("same@example.invalid", report(raw))

    def test_google_account_without_stored_entry_not_hidden(self):
        raw = """Accounts: 1
 Account {name=google@example.invalid, type=com.google}
Account visibility:
"""
        o = report(raw)
        self.assertIn("BELİRSİZ", o)
        self.assertIn("Kayıt olmaması Chrome'un hesabı göremediğini kanıtlamaz.", o)

    def test_unknown_dump_format_yields_inconclusive(self):
        raw = """User UserInfo{0:Owner:13}:
 Google Account: secret@example.invalid
 Account visibility:
  secret@example.invalid
  com.android.chrome, 3
"""
        a = summarize(raw)
        self.assertEqual(a.google_accounts_listed, 0)
        self.assertEqual(a.counts["com.android.chrome"][3], 0)
        self.assertIn("BELİRSİZ", report(raw))
        self.assertNotIn("secret@example.invalid", report(raw))

    def test_unrelated_text_and_token_are_never_printed(self):
        raw = """Accounts: 1
 Account {name=me@example.invalid, type=com.google}
Active Sessions: 1
 sensitiveAccessToken=letmein
Account visibility:
 me@example.invalid
 com.android.chrome, 3
"""
        o = report(raw)
        self.assertNotIn("me@example.invalid", o)
        self.assertNotIn("letmein", o)
        self.assertIn("Chrome (explicit): 3 (NOT_VISIBLE) x1", o)


if __name__ == "__main__":
    unittest.main()
