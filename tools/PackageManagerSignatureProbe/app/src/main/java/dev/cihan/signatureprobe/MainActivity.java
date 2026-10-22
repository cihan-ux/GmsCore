package dev.cihan.signatureprobe;

import android.app.Activity;
import android.os.Bundle;
import android.os.Build;
import android.content.Intent;
import android.content.ComponentName;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.content.pm.ServiceInfo;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.security.MessageDigest;
import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Locale;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private static final String[] TARGETS = {
        "com.google.android.gms", "com.android.vending", "com.google.android.gsf",
        "com.google.android.gm", "app.revanced.android.gms", "android"
    };
    private TextView result;
    private String report = "";

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);
        Button scan = new Button(this);
        scan.setText("İmzaları tara");
        Button share = new Button(this);
        share.setText("TXT raporunu paylaş");
        ScrollView scroll = new ScrollView(this);
        result = new TextView(this);
        result.setTextIsSelectable(true);
        scroll.addView(result);
        root.addView(scan);
        root.addView(share);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        scan.setOnClickListener(v -> scan());
        share.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, report);
            startActivity(Intent.createChooser(intent, "Raporu paylaş"));
        });
        scan();
    }

    private void scan() {
        PackageManager pm = getPackageManager();
        StringBuilder sb = new StringBuilder();
        sb.append("PackageManager Signature Probe 1.0\n");
        sb.append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.US).format(new Date())).append("\n");
        sb.append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
          .append(" / Android ").append(Build.VERSION.RELEASE)
          .append(" API ").append(Build.VERSION.SDK_INT).append("\n");
        sb.append("Sorgulayan uygulama: ").append(getPackageName()).append("\n");
        sb.append("Bu uygulama sadece kendi işlemine görünen PackageManager cevaplarını okur.\n\n");

        for (String target : TARGETS) {
            sb.append("=== ").append(target).append(" ===\n");
            try {
                int flags = PackageManager.GET_SIGNATURES | PackageManager.GET_SIGNING_CERTIFICATES;
                PackageInfo p = pm.getPackageInfo(target, flags);
                sb.append("versionName: ").append(p.versionName).append("\n");
                appendSignatures(sb, "GET_SIGNATURES", p.signatures);
                SigningInfo si = p.signingInfo;
                if (si == null) sb.append("SigningInfo: null\n");
                else {
                    appendSignatures(sb, "GET_SIGNING_CERTIFICATES", si.getApkContentsSigners());
                    sb.append("multipleSigners: ").append(si.hasMultipleSigners()).append("\n");
                }
            } catch (PackageManager.NameNotFoundException ex) {
                sb.append("Görünmüyor veya kurulu değil\n");
            } catch (Exception ex) {
                sb.append("Sorgu hatası: ").append(ex.getClass().getSimpleName()).append("\n");
            }
            sb.append("\n");
        }
        sb.append("=== checkSignatures ===\n");
        appendComparison(pm, sb, "com.google.android.gms", "com.android.vending");
        appendComparison(pm, sb, "com.google.android.gms", "com.google.android.gm");
        sb.append("\n=== Huawei service ===\n");
        try {
            ServiceInfo s = pm.getServiceInfo(
               new ComponentName("com.google.android.gms",
                 "com.huawei.signature.diff.SignatureService"), 0);
            sb.append("SignatureService tanımlı: evet\n");
            sb.append("enabled=").append(s.enabled).append(" exported=").append(s.exported).append("\n");
            sb.append("permission=").append(s.permission).append("\n");
        } catch (Exception ex) {
            sb.append("SignatureService görünmüyor: ").append(ex.getClass().getSimpleName()).append("\n");
        }
        sb.append("\nNOT: Servis tanımı, firmware entegrasyonunu veya spoofing'i kanıtlamaz.\n");
        sb.append("Root, Shizuku ve sistem değişikliği gerekmez.\n");
        report = sb.toString();
        result.setText(report);
    }

    private static void appendComparison(PackageManager pm, StringBuilder sb, String a, String b) {
        sb.append(a).append(" vs ").append(b).append(": ")
          .append(pm.checkSignatures(a, b)).append(" (-3 unknown, -1 no-match, 0 match)\n");
    }

    private static void appendSignatures(StringBuilder sb, String label, Signature[] signatures) {
        sb.append(label).append(": ");
        if (signatures == null || signatures.length == 0) {
            sb.append("yok\n");
            return;
        }
        sb.append(signatures.length).append(" imza\n");
        for (Signature signature : signatures) {
            try {
                byte[] bytes = signature.toByteArray();
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                byte[] hash = sha.digest(bytes);
                StringBuilder hex = new StringBuilder();
                for (byte b : hash) hex.append(String.format(Locale.US, "%02X", b & 255));
                sb.append("  SHA-256: ").append(hex).append("\n");
                X509Certificate cert = (X509Certificate) CertificateFactory.getInstance("X.509")
                   .generateCertificate(new ByteArrayInputStream(bytes));
                sb.append("  Subject: ").append(cert.getSubjectX500Principal()).append("\n");
            } catch (Exception ex) {
                sb.append("  Sertifika okunamadı: ").append(ex.getClass().getSimpleName()).append("\n");
            }
        }
    }
}
