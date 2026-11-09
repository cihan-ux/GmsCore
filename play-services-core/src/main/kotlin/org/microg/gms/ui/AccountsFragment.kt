/*
 * SPDX-FileCopyrightText: 2024 microG Project Team
 * SPDX-License-Identifier: Apache-2.0
 */

package org.microg.gms.ui

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.TwoStatePreference
import com.google.android.gms.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.microg.gms.auth.AuthConstants
import org.microg.gms.common.Constants
import org.microg.gms.gcm.ACTION_GCM_CONNECTED
import org.microg.gms.gcm.ACTION_GCM_REGISTER_ALL_ACCOUNTS
import org.microg.gms.people.DatabaseHelper
import org.microg.gms.people.PeopleManager
import org.microg.gms.settings.SettingsContract
import org.microg.gms.settings.SettingsContract.Auth

const val PREF_ACCOUNTS_NONE = "pref_current_accounts_none"
const val PREF_ACCOUNTS_ADD = "pref_current_accounts_add"
const val PREFCAT_ACCOUNTS = "prefcat_current_accounts"
val TWO_STATE_SETTINGS = listOf(
    Auth.TRUST_GOOGLE,
    Auth.VISIBLE,
    Auth.INCLUDE_ANDROID_ID,
    Auth.STRIP_DEVICE_NAME,
    Auth.TWO_STEP_VERIFICATION,
    Auth.FIND_DEVICES,
)

class AccountsFragment : PreferenceFragmentCompat() {

    // Android 8+ retains per-account visibility independently from the microG preference.
    // Accounts created before the setting was enabled need their visibility updated here.
    private fun updateExistingAccountVisibility(visible: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = AccountManager.get(requireContext())
        val visibility = if (visible) {
            AccountManager.VISIBILITY_USER_MANAGED_VISIBLE
        } else {
            AccountManager.VISIBILITY_UNDEFINED
        }

        try {
            for (account in manager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)) {
                // Existing pre-Android-O clients rely on the legacy visibility key.
                setVisibilitySafely(
                    manager,
                    account,
                    AccountManager.PACKAGE_NAME_KEY_LEGACY_NOT_VISIBLE,
                    visibility,
                    "legacy clients",
                )

                // Modern Chrome targets Android O+ and may not consult the legacy key.
                // Only update its explicit account visibility when the user changes the
                // existing "Allow apps to find accounts" setting; do not grant all apps access.
                setVisibilitySafely(
                    manager,
                    account,
                    CHROME_PACKAGE_NAME,
                    visibility,
                    "Chrome",
                )
            }
        } catch (e: RuntimeException) {
            // A vendor-modified AccountManager must not crash the settings screen.
            Log.w(TAG, "Unable to enumerate Google accounts for visibility update", e)
        }
    }

    private fun setVisibilitySafely(
        manager: AccountManager,
        account: Account,
        packageName: String,
        visibility: Int,
        target: String,
    ) {
        try {
            val before = manager.getAccountVisibility(account, packageName)
            val updated = manager.setAccountVisibility(account, packageName, visibility)
            val after = manager.getAccountVisibility(account, packageName)
            // Do not log the account name, email, or auth tokens.
            Log.i(TAG, "$target visibility: before=$before requested=$visibility after=$after applied=$updated")
            if (!updated || (visibility != AccountManager.VISIBILITY_UNDEFINED && after != visibility)) {
                Log.w(TAG, "Google account visibility was not confirmed for $target")
            }
        } catch (e: RuntimeException) {
            // A missing client package or vendor-specific AccountManager behavior
            // should not prevent visibility updates for other accounts.
            Log.w(TAG, "Unable to update account visibility for $target", e)
        }
    }

    // TODO: This should use some better means of accessing the database
    private fun getDisplayName(account: Account): String? {
        val databaseHelper = DatabaseHelper(context)
        val cursor = databaseHelper.getOwner(account.name)
        return try {
            if (cursor.moveToNext()) {
                cursor.getColumnIndex("display_name").takeIf { it >= 0 }?.let { cursor.getString(it) }.takeIf { !it.isNullOrBlank() }
            } else null
        } finally {
            cursor.close()
            databaseHelper.close()
        }
    }

    private fun getCircleBitmapDrawable(bitmap: Bitmap?) =
        if (bitmap != null) RoundedBitmapDrawableFactory.create(resources, bitmap.let {
            Bitmap.createScaledBitmap(bitmap, 100, 100, true)
        }).also { it.isCircular = true } else null

    private fun registerGcmInGms() {
        Intent(ACTION_GCM_REGISTER_ALL_ACCOUNTS).apply {
            `package` = Constants.GMS_PACKAGE_NAME
        }.let { requireContext().sendBroadcast(it) }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences_accounts)
        updateSettings()
        for (setting in TWO_STATE_SETTINGS) {
            findPreference<TwoStatePreference>(setting)?.setOnPreferenceChangeListener { preference, newValue ->
                if (newValue is Boolean && preference.key in TWO_STATE_SETTINGS) {
                    SettingsContract.setSettings(requireContext(), Auth.getContentUri(requireContext())) { put(preference.key, newValue) }
                    updateSettings()
                    if (preference.key == Auth.VISIBLE) updateExistingAccountVisibility(newValue)
                    if (preference.key == Auth.TWO_STEP_VERIFICATION && newValue) registerGcmInGms()
                    if (preference.key == Auth.FIND_DEVICES && newValue) registerGcmInGms()
                    true
                } else false
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateSettings()
    }

    private fun updateSettings() {
        val context = requireContext()

        val accountManager = AccountManager.get(context)
        val accounts = accountManager.getAccountsByType(AuthConstants.DEFAULT_ACCOUNT_TYPE)

        findPreference<Preference>(PREF_ACCOUNTS_NONE)?.isVisible = accounts.isEmpty()
        val preferenceCategory = findPreference<PreferenceCategory>(PREFCAT_ACCOUNTS) ?: return
        // Keep the add and none
        while (preferenceCategory.preferenceCount > 2) {
            preferenceCategory.removePreference(preferenceCategory.getPreference(0))
        }
        accounts.map { account ->
            val photo = PeopleManager.getOwnerAvatarBitmap(context, account.name, false)
            val preference = Preference(context).apply {
                title = getDisplayName(account)
                summary = account.name
                icon = getCircleBitmapDrawable(photo)
                key = "account:${account.name}"
                order = 0
                if (photo == null) {
                    lifecycleScope.launchWhenStarted {
                        withContext(Dispatchers.IO) {
                            PeopleManager.getOwnerAvatarBitmap(context, account.name, true)
                        }?.let { icon = getCircleBitmapDrawable(it) }
                    }
                }
                setOnPreferenceClickListener {
                    startActivity(Intent(Settings.ACTION_SYNC_SETTINGS).apply {
                        putExtra(Settings.EXTRA_ACCOUNT_TYPES, arrayOf(AuthConstants.DEFAULT_ACCOUNT_TYPE))
                    })
                    false
                }
            }
            preference
        }.sorted().forEach { preferenceCategory.addPreference(it) }

        for (setting in TWO_STATE_SETTINGS) {
            findPreference<TwoStatePreference>(setting)?.isChecked =
                SettingsContract.getSettings(context, Auth.getContentUri(context), arrayOf(setting)) { c -> c.getInt(0) != 0 }
        }
    }

    init {
        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, MENU_GAMES_MANAGED, 0, org.microg.gms.base.core.R.string.menu_game_managed)
        menu.add(0, MENU_PASSKEY_MANAGER, 1, R.string.pref_passkey_manager_title)
        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_GAMES_MANAGED -> {
                findNavController().navigate(requireContext(), R.id.openGameManagerSettings)
                true
            }

            MENU_PASSKEY_MANAGER -> {
                findNavController().navigate(requireContext(), R.id.openPasskeyManagerSettings)
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val TAG = "GmsAccountSettings"
        private const val CHROME_PACKAGE_NAME = "com.android.chrome"
        private const val MENU_GAMES_MANAGED = Menu.FIRST
        private const val MENU_PASSKEY_MANAGER = Menu.FIRST + 1
    }
}
