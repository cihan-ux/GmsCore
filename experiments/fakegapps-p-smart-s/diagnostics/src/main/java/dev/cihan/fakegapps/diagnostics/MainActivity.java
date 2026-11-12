package dev.cihan.fakegapps.diagnostics;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

import java.security.MessageDigest;
import java.util.Locale;

/**
 * Purely read-only Android 10 signature diagnostics.
 * No LSPatch hooks, APK modifications, token/network access, or root commands.
 */
public final class MainActivity extends Activity {
    private static final String GMS = "com.google.android.gms";
    private static final String PLAY = "com.android.vending";
    private static final String CHROME = "com.android.chrome";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        PackageManager pm = getPackageManager();
        StringBuilder report = new StringBuilder();
        report.append("FakeGApps P Smart S v0.5 — READ ONLY\n")
            .append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
            .append(" | Android API ").append(Build.VERSION.SDK_INT).append("\n")
            .append("This independent APK is NOT patched by LSPatch.\n\n");

        report.append(inspect(pm, GMS)).append("\n\n")
            .append(inspect(pm, PLAY)).append("\n\n")
            .append(inspect(pm, CHROME)).append("\n\n");

        int code = pm.checkSignatures(GMS, PLAY);
        report.append("SYSTEM PackageManager.checkSignatures(GMS, Play): ")
            .append(describeSignatureCode(code)).append(" (").append(code).append(")\n");

        appendPairSignatureCheck(report, pm, GMS, CHROME, "GMS, Chrome");
        appendPairSignatureCheck(report, pm, PLAY, CHROME, "Play, Chrome");

        try {
            PackageInfo playInfo = pm.getPackageInfo(PLAY, PackageManager.GET_SIGNING_CERTIFICATES);
            if (playInfo.signingInfo == null) {
                report.append("hasSigningCertificate: INCONCLUSIVE (Play SigningInfo null)\n");
            } else {
                Signature[] signatures = playInfo.signingInfo.getApkContentsSigners();
                if (signatures == null || signatures.length == 0) {
                    report.append("hasSigningCertificate: INCONCLUSIVE (no Play signers)\n");
                } else {
                    byte[] playDigest = MessageDigest.getInstance("SHA-256")
                        .digest(signatures[0].toByteArray());
                    report.append("SYSTEM hasSigningCertificate(Play, Play cert): ")
                        .append(pm.hasSigningCertificate(PLAY, playDigest, PackageManager.CERT_INPUT_SHA256))
                        .append(" [control]\n");
                    report.append("SYSTEM hasSigningCertificate(GMS, Play cert): ")
                        .append(pm.hasSigningCertificate(GMS, playDigest, PackageManager.CERT_INPUT_SHA256))
                        .append("\n");
                }
            }
        } catch (PackageManager.NameNotFoundException e) {
            report.append("hasSigningCertificate: INCONCLUSIVE (Play not installed)\n");
        } catch (Throwable t) {
            report.append("hasSigningCertificate: INCONCLUSIVE (")
                .append(t.getClass().getSimpleName()).append(")\n");
        }


        // The Chrome certificate is only a local comparison reference.
        // This check does not establish whether Chrome is Google-authentic.
        try {
            PackageInfo chromeInfo = pm.getPackageInfo(
                CHROME, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo chromeSigning = chromeInfo.signingInfo;
            Signature[] chromeSigners = chromeSigning == null
                ? null : chromeSigning.getApkContentsSigners();
            if (chromeSigners != null && chromeSigners.length > 0) {
                byte[] chromeDigest = MessageDigest.getInstance("SHA-256")
                    .digest(chromeSigners[0].toByteArray());
                report.append("SYSTEM hasSigningCertificate(Chrome, Chrome cert): ")
                    .append(pm.hasSigningCertificate(
                        CHROME, chromeDigest, PackageManager.CERT_INPUT_SHA256))
                    .append(" [control]\\n");
                report.append("SYSTEM hasSigningCertificate(GMS, Chrome cert): ")
                    .append(pm.hasSigningCertificate(
                        GMS, chromeDigest, PackageManager.CERT_INPUT_SHA256))
                    .append("\\n");
                report.append("SYSTEM hasSigningCertificate(Play, Chrome cert): ")
                    .append(pm.hasSigningCertificate(
                        PLAY, chromeDigest, PackageManager.CERT_INPUT_SHA256))
                    .append("\\n");
            } else {
                report.append("Chrome certificate comparison: INCONCLUSIVE (no signer)\\n");
            }
        } catch (PackageManager.NameNotFoundException e) {
            report.append("Chrome certificate comparison: INCONCLUSIVE (Chrome not installed)\\n");
        } catch (Exception e) {
            report.append("Chrome certificate comparison: INCONCLUSIVE (")
                .append(e.getClass().getSimpleName()).append(")\\n");
        }

        report.append("\\nINTERPRETATION: Matching GMS/Play package signatures may be the result ")
            .append("of the same actual APK signing key. This is not proof of spoofing.\\n")
            .append("Chrome certificate comparison does not establish Chrome authenticity, ")
            .append("account access, or why Chrome sign-in fails.\\n");

        report.append("\nLIMITS: Read-only local diagnostics. These results do not")
            .append(" reveal the exact Play Store call path, demonstrate download failure")
            .append(" causality, or change any stored signature.\n")
            .append("No Google account data or tokens are collected.");
        TextView tv = new TextView(this);
        tv.setTextSize(13f);
        tv.setTextIsSelectable(true);
        tv.setPadding(24, 24, 24, 24);
        tv.setText(report.toString());
        ScrollView scroll = new ScrollView(this);
        scroll.addView(tv);
        setContentView(scroll);
    }

    private static void appendPairSignatureCheck(
            StringBuilder report, PackageManager pm, String first,
            String second, String label) {
        try {
            int code = pm.checkSignatures(first, second);
            report.append("SYSTEM PackageManager.checkSignatures(")
                .append(label).append("): ")
                .append(describeSignatureCode(code))
                .append(" (").append(code).append(")\\n");
        } catch (RuntimeException e) {
            report.append("Signature comparison ").append(label)
                .append(": INCONCLUSIVE (")
                .append(e.getClass().getSimpleName()).append(")\\n");
        }
    }

    private static String inspect(PackageManager pm, String pkg) {
        StringBuilder sb = new StringBuilder(pkg);
        try {
            PackageInfo legacy = pm.getPackageInfo(pkg, PackageManager.GET_SIGNATURES);
            sb.append("\nGET_SIGNATURES: ").append(fingerprint(legacy.signatures));
            PackageInfo modern = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signing = modern.signingInfo;
            sb.append("\nGET_SIGNING_CERTIFICATES: ")
                .append(fingerprint(signing == null ? null : signing.getApkContentsSigners()));
            // On-disk parsing is a second, unhooked observation (may be inaccessible).
            try {
                String path = pm.getApplicationInfo(pkg, 0).sourceDir;
                PackageInfo archive = pm.getPackageArchiveInfo(path, PackageManager.GET_SIGNATURES);
                sb.append("\nAPK archive certificate: ")
                    .append(archive == null ? "<unavailable>" : fingerprint(archive.signatures));
            } catch (Throwable t) {
                sb.append("\nAPK archive certificate: unavailable (")
                    .append(t.getClass().getSimpleName()).append(")");
            }
        } catch (PackageManager.NameNotFoundException e) {
            sb.append("\nNOT INSTALLED for current Android user");
        } catch (Throwable t) {
            sb.append("\nINCONCLUSIVE: ").append(t.getClass().getSimpleName());
        }
        return sb.toString();
    }

    private static String describeSignatureCode(int code) {
        switch (code) {
            case PackageManager.SIGNATURE_MATCH: return "MATCH";
            case PackageManager.SIGNATURE_NO_MATCH: return "NO_MATCH";
            case PackageManager.SIGNATURE_FIRST_NOT_SIGNED: return "FIRST_NOT_SIGNED";
            case PackageManager.SIGNATURE_SECOND_NOT_SIGNED: return "SECOND_NOT_SIGNED";
            case PackageManager.SIGNATURE_NEITHER_SIGNED: return "NEITHER_SIGNED";
            case PackageManager.SIGNATURE_UNKNOWN_PACKAGE: return "UNKNOWN_PACKAGE";
            default: return "OTHER";
        }
    }

    private static String fingerprint(Signature[] signatures) {
        if (signatures == null || signatures.length == 0) return "<none>";
        StringBuilder b = new StringBuilder();
        for (Signature signature : signatures) {
            try {
                byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray());
                if (b.length() > 0) b.append(" ");
                for (byte item : digest) b.append(String.format(Locale.ROOT, "%02x", item & 255));
            } catch (Exception e) {
                return "<digest-error>";
            }
        }
        return b.toString();
    }
}
