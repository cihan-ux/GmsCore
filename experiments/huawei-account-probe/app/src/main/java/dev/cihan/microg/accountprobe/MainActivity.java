package dev.cihan.microg.accountprobe;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Standalone, read-only visibility probe.
 *
 * This app only sees accounts visible to ITS OWN package, not Chrome.
 * It neither invokes getAuthToken nor reads account names/email addresses.
 * No network, file, or account-modification code is present.
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_ACCOUNTS = 42;
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private TextView report;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 24, 24, 24);

        Button permissionButton = new Button(this);
        permissionButton.setText("Hesap iznini iste (isteğe bağlı)");
        permissionButton.setOnClickListener(view -> {
            if (Build.VERSION.SDK_INT >= 23
                    && checkSelfPermission(Manifest.permission.GET_ACCOUNTS)
                        != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    new String[] {Manifest.permission.GET_ACCOUNTS}, REQUEST_ACCOUNTS);
            } else {
                refreshReport();
            }
        });
        content.addView(permissionButton);

        Button refreshButton = new Button(this);
        refreshButton.setText("Yeniden kontrol et");
        refreshButton.setOnClickListener(view -> refreshReport());
        content.addView(refreshButton);

        report = new TextView(this);
        report.setTextSize(14f);
        report.setTextIsSelectable(true);
        report.setPadding(0, 16, 0, 16);
        content.addView(report);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll);
        refreshReport();
    }

    @Override public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_ACCOUNTS) refreshReport();
    }

    private void refreshReport() {
        StringBuilder output = new StringBuilder();
        output.append("microG Account Probe v0.1\\n")
              .append("Yalnızca okuma / İnternet izni yok\\n\\n")
              .append("Cihaz: ").append(Build.MANUFACTURER)
              .append(" ").append(Build.MODEL)
              .append(" | Android API ").append(Build.VERSION.SDK_INT).append("\\n")
              .append("Bu uygulamanın paketi: ").append(getPackageName()).append("\\n\\n");

        PackageManager pm = getPackageManager();
        appendInstalledPackage(output, pm, "com.google.android.gms", "microG/GMS");
        appendInstalledPackage(output, pm, "com.android.chrome", "Chrome");
        appendInstalledPackage(output, pm, "com.android.vending", "Play Store");
        output.append("\\n");

        AccountManager accounts = AccountManager.get(this);
        try {
            boolean googleAuthenticatorPresent = false;
            for (AuthenticatorDescription authenticator : accounts.getAuthenticatorTypes()) {
                if (GOOGLE_ACCOUNT_TYPE.equals(authenticator.type)) {
                    googleAuthenticatorPresent = true;
                    break;
                }
            }
            output.append("Google AccountManager sağlayıcısı: ")
                  .append(googleAuthenticatorPresent ? "GÖRÜLÜYOR" : "GÖRÜNMÜYOR")
                  .append("\\n");
        } catch (RuntimeException error) {
            output.append("Google hesap sağlayıcısı: belirsiz (")
                  .append(error.getClass().getSimpleName()).append(")\\n");
        }

        boolean permissionGranted = checkSelfPermission(Manifest.permission.GET_ACCOUNTS)
                == PackageManager.PERMISSION_GRANTED;
        output.append("GET_ACCOUNTS izni: ")
              .append(permissionGranted ? "VERİLMİŞ" : "VERİLMEMİŞ")
              .append("\\n");

        if (permissionGranted) {
            try {
                Account[] visible = accounts.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
                output.append("BU UYGULAMAYA GÖRÜNEN Google hesap SAYISI: ")
                      .append(visible.length).append("\\n");
            } catch (SecurityException error) {
                output.append("Google hesabı sorgusu reddedildi (SecurityException)\\n");
            } catch (RuntimeException error) {
                output.append("Google hesabı sorgusu başarısız (")
                      .append(error.getClass().getSimpleName()).append(")\\n");
            }
        } else {
            output.append("Hesap sayısı izinsiz sorgulanmadı.\\n");
        }

        output.append("\\nÖNEMLİ: Bu APK kendi hesap görünürlüğünü ölçer; ")
              .append("Chrome'un hesabı gördüğünü veya imza taklidinin çalıştığını ")
              .append("kanıtlamaz. Hesap adı, e-posta, token veya şifre okumaz. ")
              .append("Google hesabını kaldırmaz, microG'yi değiştirmez.");
        report.setText(output.toString());
    }

    private static void appendInstalledPackage(
            StringBuilder out, PackageManager pm, String packageName, String label) {
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            out.append(label).append(": yüklü");
            if (info.versionName != null) out.append(" (").append(info.versionName).append(")");
            out.append("\\n");
        } catch (PackageManager.NameNotFoundException ignored) {
            out.append(label).append(": bulunamadı\\n");
        } catch (RuntimeException error) {
            out.append(label).append(": sorgulanamadı (")
               .append(error.getClass().getSimpleName()).append(")\\n");
        }
    }
}
