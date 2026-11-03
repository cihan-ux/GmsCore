/*
 * SPDX-License-Identifier: Apache-2.0
 * Test-only user-approved Android PackageInstaller session.
 * This code does not patch, impersonate, or update Google Play Store.
 */
package org.psmarts.rootlesslab;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;

public final class MainActivity extends Activity {
    private static final String TAG = "RootlessInstallerLab";
    private static final int PICK_APK = 501;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Rootless Installer Lab");
        title.setTextSize(23);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView warning = new TextView(this);
        warning.setText("\nAndroid 10 smoke test only. Only a NEW, permissionless Installer Smoke Test APK is accepted. Existing apps, Google services and updates are blocked. Android must ask for confirmation.\n\nThis does not repair Aurora or Play Store downloads.\n");
        warning.setTextSize(15);
        warning.setTextColor(Color.DKGRAY);
        root.addView(warning);

        Button select = new Button(this);
        select.setText("Choose disposable Smoke Test APK");
        root.addView(select);
        select.setOnClickListener(v -> pick());

        status = new TextView(this);
        status.setText("No APK selected. No changes made.");
        status.setTextSize(14);
        status.setTextIsSelectable(true);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(status);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);
    }

    private void show(String message) {
        Log.i(TAG, message);
        runOnUiThread(() -> status.setText(message));
    }

    private void pick() {
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            show("Allow APK installs for this lab in Android settings first. Return and tap Choose again. Do not grant this permission to unrelated apps.");
            Intent settings = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + getPackageName()));
            startActivity(settings);
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/vnd.android.package-archive");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/vnd.android.package-archive", "application/octet-stream"});
        startActivityForResult(intent, PICK_APK);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_APK || resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        show("Checking the disposable APK before creating any install session.");
        new Thread(() -> submitInstall(uri), "install-lab-worker").start();
    }

    private void submitInstall(Uri uri) {
        File checkedApk = null;
        PackageInstaller installer = getPackageManager().getPackageInstaller();
        PackageInstaller.Session session = null;
        int sessionId = -1;
        boolean committed = false;
        try {
            // Strictly inspect a private copy BEFORE creating a session.
            checkedApk = InstallSafetyGate.prepare(this, uri);
            PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                    PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            params.setAppPackageName(SmokePolicy.TEST_PACKAGE);
            sessionId = installer.createSession(params);
            session = installer.openSession(sessionId);
            try (InputStream input = new FileInputStream(checkedApk);
                 OutputStream output = session.openWrite("base.apk", 0, checkedApk.length())) {
                byte[] buffer = new byte[65536];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                }
                session.fsync(output);
            }
            Intent callback = new Intent(this, InstallStatusReceiver.class);
            callback.setAction("org.psmarts.rootlesslab.INSTALL_RESULT");
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_MUTABLE;
            PendingIntent pendingIntent = PendingIntent.getBroadcast(this, sessionId, callback, flags);
            session.commit(pendingIntent.getIntentSender());
            committed = true;
            show("Smoke APK submitted. Approve it in Android's own confirmation UI.");
        } catch (SecurityException rejected) {
            // Every rejection originates in our fixed, non-sensitive policy strings.
            show(rejected.getMessage());
            Log.w(TAG, "Installer smoke test blocked by policy");
        } catch (Exception e) {
            show("Test failed: " + e.getClass().getSimpleName() + ". No account changes.");
            Log.e(TAG, "Installer smoke test failed (" + e.getClass().getSimpleName() + ")");
        } finally {
            if (!committed) {
                if (session != null) {
                    try { session.abandon(); } catch (Exception ignored) {}
                } else if (sessionId >= 0) {
                    try { installer.abandonSession(sessionId); } catch (Exception ignored) {}
                }
            }
            if (session != null) {
                try { session.close(); } catch (Exception ignored) {}
            }
            if (checkedApk != null && checkedApk.exists() && !checkedApk.delete()) {
                Log.w(TAG, "Temporary smoke APK could not be deleted");
            }
        }
    }
}
