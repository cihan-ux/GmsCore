/* SPDX-License-Identifier: Apache-2.0 */
package org.psmarts.rootlesslab;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Copy the user-selected document to an app-private temporary APK, inspect it
 * before opening any PackageInstaller session, and fail closed on uncertainty.
 */
final class InstallSafetyGate {
    private static final long MAX_APK_BYTES = 20L * 1024L * 1024L;
    private InstallSafetyGate() {}

    static File prepare(Context context, Uri document) throws IOException {
        File apk = File.createTempFile("smoke-", ".apk", context.getCacheDir());
        boolean accepted = false;
        try {
            try (InputStream input = context.getContentResolver().openInputStream(document)) {
                if (input == null) throw new IOException("APK document could not be opened");
                try (FileOutputStream output = new FileOutputStream(apk)) {
                    long total = 0;
                    byte[] buffer = new byte[16384];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_APK_BYTES) {
                            throw new SecurityException("Blocked: test APK exceeds the 20 MiB size limit.");
                        }
                        output.write(buffer, 0, count);
                    }
                }
            }

            PackageManager pm = context.getPackageManager();
            PackageInfo archive = pm.getPackageArchiveInfo(
                    apk.getAbsolutePath(), PackageManager.GET_PERMISSIONS);
            if (archive == null) {
                throw new SecurityException("Blocked: selected file is not a readable APK.");
            }

            boolean installed = false;
            try {
                pm.getPackageInfo(SmokePolicy.TEST_PACKAGE, 0);
                installed = true;
            } catch (PackageManager.NameNotFoundException expected) {
                // Only a not-installed disposable test app may be installed.
            }

            String reason = SmokePolicy.rejectionReason(
                    archive.packageName, archive.requestedPermissions, installed);
            if (reason != null) throw new SecurityException(reason);

            accepted = true;
            return apk;
        } finally {
            if (!accepted) {
                // Fail closed and remove partially copied, untrusted APK bytes.
                apk.delete();
            }
        }
    }
}
