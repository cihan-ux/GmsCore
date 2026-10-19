/*
 * SPDX-License-Identifier: Apache-2.0
 * Disposable installation smoke-test app with no permissions.
 */
package org.psmarts.rootlesslab.smoke;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

public final class SmokeActivity extends Activity {
    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        TextView text = new TextView(this);
        text.setText("Rootless install smoke test: OK\nNo account access, network, or Google services.");
        text.setTextSize(19);
        text.setGravity(Gravity.CENTER);
        setContentView(text);
    }
}
