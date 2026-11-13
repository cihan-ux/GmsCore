"""Offline tests for the local privacy-preserving AccountManager dump parser."""

import unittest

from chrome_visibility_dumpsys import report, summarize


class ChromeVisibilityDumpTests(unittest.TestCase):
    def test_parses_visibility_without_printing_identifiers(self):
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
        sections, counts = summarize(sensitive)
        self.assertEqual(sections, 1)
        self.assertEqual(counts["com.android.chrome"][2], 1)
        self.assertEqual(counts["android:accounts:key_legacy_not_visible"][4], 1)
        output = report(sensitive)
        self.assertIn("Chrome (explicit): 2 (USER_MANAGED_VISIBLE) x1", output)
        self.assertNotIn("alice@example.invalid", output)
        self.assertNotIn("opaque-session-secret-999", output)
        self.assertNotIn("Account {", output)

    def test_nonmatching_package_and_text_do_not_leak(self):
        raw = """Account visibility:
  user@sample.invalid
  com.fake.chrome, 1
  com.android.chrome, 3
  dangerous-access-token=abcdef
"""
        output = report(raw)
        self.assertIn("Chrome (explicit): 3 (NOT_VISIBLE) x1", output)
        self.assertNotIn("com.fake.chrome", output)
        self.assertNotIn("abcdef", output)
        self.assertNotIn("user@sample.invalid", output)

    def test_no_visibility_section_is_inconclusive(self):
        output = report("User UserInfo{0:Owner:13}:\n Accounts: 1\n Account hidden@example.invalid")
        self.assertIn("BELİRSİZ", output)
        self.assertNotIn("hidden@example.invalid", output)

    def test_multiple_users_count_stored_entries_only(self):
        raw = """User UserInfo{0:Owner:13}:
Account visibility:
 alice@sample.invalid
 com.android.chrome, 2
User UserInfo{10:Profile:13}:
Account visibility:
 bob@sample.invalid
 com.android.chrome, 4
"""
        sections, entries = summarize(raw)
        self.assertEqual(sections, 2)
        self.assertEqual(entries["com.android.chrome"][2], 1)
        self.assertEqual(entries["com.android.chrome"][4], 1)

    def test_absent_chrome_entry_does_not_claim_hidden(self):
        output = report("Account visibility:\n me@example.invalid\n android:accounts:key_legacy_not_visible, 2")
        self.assertIn("KAYIT BULUNAMADI (varsayılan erişim yine mümkün)", output)
        self.assertNotIn("me@example.invalid", output)
        self.assertIn("erişim olmadığı anlamına GELMEZ", output)


if __name__ == "__main__":
    unittest.main()
