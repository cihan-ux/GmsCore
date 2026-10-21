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
            + "Device: " + Build.MANUFACTURER + " " + Build.MODEL + "\n"
            + probeHookStatus() + "\n\n"
            + inspectLocalPositiveControl() + "\n\n"
            + inspect("com.google.android.gms")
            + "\n\n" + inspect("com.android.vending")
            + "\n\nThis screen shows ONLY what this app receives via PackageManager. "
            + "It cannot prove system-wide signature spoofing.");
        ScrollView scroll = new ScrollView(this);
        scroll.addView(text);
        setContentView(scroll);
    }

    private String probeHookStatus() {
        try {
            String version = getPackageManager()
                .getPackageInfo(getPackageName(), 0).versionName;
            if (version != null && version.endsWith("-hook-active")) {
                return "LOCAL XPOSED HOOK: ACTIVE (PROBE PROCESS ONLY)";
            }
            return "LOCAL XPOSED HOOK: NOT DETECTED";
        } catch (Exception e) {
            return "LOCAL XPOSED HOOK: UNKNOWN (" + e.getClass().getSimpleName() + ")";
        }
    }

    // getPackageInfo is intercepted locally by our probe-only hook.
    // getPackageArchiveInfo reads the actual on-disk patched APK certificate.
    // A difference demonstrates local returned-data modification, not ROM spoofing.
    private String inspectLocalPositiveControl() {
        PackageManager pm = getPackageManager();
        try {
            PackageInfo local = pm.getPackageInfo(getPackageName(), PackageManager.GET_SIGNATURES);
            PackageInfo archive = pm.getPackageArchiveInfo(
                getApplicationInfo().sourceDir, PackageManager.GET_SIGNATURES);
            if (archive == null) return "LOCAL SIGNATURE CONTROL: APK archive unavailable";
            String localHash = fingerprints(local.signatures);
            String archiveHash = fingerprints(archive.signatures);
            boolean comparable = !"<none>".equals(localHash) && !"<none>".equals(archiveHash);
            String verdict = !comparable ? "INCONCLUSIVE (missing signature)"
                : localHash.equals(archiveHash) ? "NO LOCAL OVERRIDE DETECTED"
                : "DIFFERENT (LOCAL OVERRIDE OBSERVED)";
            return "LOCAL SIGNATURE CONTROL: " + verdict
                + "\nPackageManager SHA-256: " + localHash
                + "\nOn-disk APK SHA-256: " + archiveHash
                + "\nComparison only concerns this probe process.";
        } catch (Throwable t) {
            return "LOCAL SIGNATURE CONTROL: UNKNOWN (" + t.getClass().getSimpleName() + ")";
        }
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
