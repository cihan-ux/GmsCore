package org.psmarts.signatureinspector;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.security.MessageDigest;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String[] PACKAGES = {"com.google.android.gms", "com.android.vending", "com.google.android.gsf"};
    private static final String GOOGLE_SHA1 = "38918a453d07199354f8b19af05ec6562ced5788";
    private String report = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this);
        title.setText("P Smart S Signature Inspector\nAndroid PackageManager görünümü");
        title.setTextSize(20);
        root.addView(title);
        Button refresh = new Button(this); refresh.setText("Yeniden kontrol et"); root.addView(refresh);
        Button copy = new Button(this); copy.setText("Raporu kopyala"); root.addView(copy);
        ScrollView scroll = new ScrollView(this);
        TextView result = new TextView(this);
        result.setTextIsSelectable(true); result.setTextSize(14); result.setMovementMethod(new ScrollingMovementMethod());
        scroll.addView(result); root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        refresh.setOnClickListener(v -> inspect(result));
        copy.setOnClickListener(v -> { ((ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Signature Inspector", report)); Toast.makeText(this,"Rapor kopyalandı",Toast.LENGTH_SHORT).show(); });
        inspect(result);
    }

    private void inspect(TextView view) {
        StringBuilder out = new StringBuilder();
        out.append("P Smart S Signature Inspector v1.0\n");
        out.append("Device: ").append(Build.MODEL).append("; Android ").append(Build.VERSION.RELEASE).append("; API ").append(Build.VERSION.SDK_INT).append("\n");
        out.append("Scope: this app's PackageManager view only. Not proof of Play Store's internal checks or Huawei firmware integration.\n\n");
        boolean gmsMatched = false; boolean gmsReadable = false;
        PackageManager pm = getPackageManager();
        for (String name : PACKAGES) {
            out.append("PACKAGE: ").append(name).append("\n");
            try {
                PackageInfo info = pm.getPackageInfo(name, Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES);
                out.append("Version: ").append(info.versionName).append("\n");
                Signature[] certs;
                if (Build.VERSION.SDK_INT >= 28) {
                    SigningInfo signing = info.signingInfo;
                    certs = signing == null ? null : (signing.hasMultipleSigners() ? signing.getApkContentsSigners() : signing.getSigningCertificateHistory());
                } else certs = info.signatures;
                if (certs == null || certs.length == 0) { out.append("Result: no certificate returned\n"); continue; }
                for (Signature cert : certs) {
                    String sha1 = digest(cert.toByteArray(), "SHA-1");
                    String sha256 = digest(cert.toByteArray(), "SHA-256");
                    out.append("PackageManager SHA-1: ").append(sha1).append("\nPackageManager SHA-256: ").append(sha256).append("\n");
                    if (name.equals("com.google.android.gms")) { gmsReadable = true; if (sha1.equals(GOOGLE_SHA1)) gmsMatched = true; }
                }
                if (name.equals("com.google.android.gms")) {
                    try { pm.getServiceInfo(new android.content.ComponentName(name,"com.huawei.signature.diff.SignatureService"), 0); out.append("Huawei service: declared\n"); }
                    catch (PackageManager.NameNotFoundException e) { out.append("Huawei service: not declared or not visible\n"); }
                }
            } catch (PackageManager.NameNotFoundException e) { out.append("Result: package unavailable or not visible\n"); }
            catch (Exception e) { out.append("Result: read error (not proof of absence)\n"); }
            out.append("\n");
        }
        out.append("EXPECTED GOOGLE SHA-1: ").append(GOOGLE_SHA1).append("\n");
        out.append("RESULT: ").append(!gmsReadable ? "INCONCLUSIVE" : gmsMatched ? "GOOGLE SIGNATURE VISIBLE TO INSPECTOR" : "GOOGLE SIGNATURE NOT VISIBLE TO INSPECTOR").append("\n");
        out.append("Important: APK on-disk signer is NOT measured here; use the existing opt-in apksigner diagnostics for that.\n");
        out.append("No root, internet, accounts, or device identifiers accessed.\n");
        report = out.toString();
        view.setText(report);
    }
    private static String digest(byte[] bytes, String algorithm) throws Exception {
        byte[] hash = MessageDigest.getInstance(algorithm).digest(bytes);
        StringBuilder s = new StringBuilder(); for (byte b : hash) s.append(String.format(Locale.ROOT,"%02x",b & 0xff)); return s.toString();
    }
}
