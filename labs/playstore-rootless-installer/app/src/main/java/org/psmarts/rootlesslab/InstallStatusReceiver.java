/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.psmarts.rootlesslab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

public final class InstallStatusReceiver extends BroadcastReceiver {
    private static final String TAG = "RootlessInstallerLab";

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent confirmation = intent.getParcelableExtra(Intent.EXTRA_INTENT);
            if (confirmation != null) {
                confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    context.startActivity(confirmation);
                    Log.i(TAG, "Requested Android confirmation UI");
                } catch (Exception e) {
                    Log.e(TAG, "Could not launch Android confirmation UI", e);
                    Toast.makeText(context, "Could not open confirmation UI", Toast.LENGTH_LONG).show();
                }
            } else {
                Log.e(TAG, "Android confirmation intent missing");
            }
        } else {
            String outcome = (status == PackageInstaller.STATUS_SUCCESS ? "Installed successfully" : "Install result " + status);
            Log.i(TAG, outcome + ": " + message);
            Toast.makeText(context, outcome, Toast.LENGTH_LONG).show();
        }
    }
}
