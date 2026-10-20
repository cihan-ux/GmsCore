package dev.cihan.fakegapps.probe;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.os.Build;
import android.widget.ScrollView;
import android.widget.TextView;
import java.security.MessageDigest;
import java.util.Locale;

public final class MainActivity extends Activity {
    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView text = new TextView(this);
        text.setTextSize(13f);
        text.setPadding(24, 24, 24, 24);
        text.setText("FakeGApps P Smart S — PROBE ONLY\n"
            + "Android API: " + Build.VERSION.SDK_INT + "\n"
            + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n\n"
            + inspect("com.google.android.gms")
            + "\n\n" + inspect("com.android.vending")
            + "\n\nThis screen shows ONLY what this app receives via PackageManager. "
            + "It cannot prove system-wide signature spoofing.");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        setContentView(scroll);
    }

    private String inspect(String target) {
        StringBuilder sb = new StringBuilder(target);
        PackageManager pm = getPackageManager();
        try {
            PackageInfo legacy = pm.getPackageInfo(target, PackageManager.GET_SIGNATURES);
            sb.append("\nPackageInfo.signatures SHA-256: ")
              .append(fingerprints(legacy.signatures));
            if (Build.VERSION.SDK_INT >= 28) {
                PackageInfo modern = pm.getPackageInfo(
                    target, PackageManager.GET_SIGNING_CERTIFICATES);
                Signature[] signers = modern.signingInfo == null
                    ? null : modern.signingInfo.getApkContentsSigners();
                sb.append("\nSigningInfo SHA-256: ").append(fingerprints(signers));
            }
            sb.append("\nNOTE: Compare before/after applying LSPatch to THIS probe.");
        } catch (PackageManager.NameNotFoundException e) {
            sb.append("\nNOT INSTALLED for this Android user");
        } catch (Throwable t) {
            sb.append("\nQuery failed: ").append(t.getClass().getSimpleName());
        }
        return sb.toString();
    }

    private String fingerprints(Signature[] signatures) {
        if (signatures == null || signatures.length == 0) return "<none>";
        StringBuilder result = new StringBuilder();
        for (Signature signature : signatures) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray());
                for (byte item : digest) {
                    result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
                }
                result.append(" ");
            } catch (Exception e) {
                return "<digest-error>";
            }
        }
        return result.toString().trim();
    }
}
