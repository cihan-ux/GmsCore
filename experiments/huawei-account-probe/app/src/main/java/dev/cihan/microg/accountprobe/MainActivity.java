package dev.cihan.microg.accountprobe;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.net.Uri;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.LinkedHashSet;
import java.util.List;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Standalone, read-only visibility probe.
 *
 * This app only sees accounts visible to ITS OWN package, not Chrome.
 * Browser package presence and web-intent handlers are local, non-privileged queries.
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
        output.append("microG Account Probe v0.4 — iki cihaz karşılaştırması\n")
              .append("Yalnızca okuma / İnternet izni yok\n\n")
              .append("Cihaz: ").append(Build.MANUFACTURER)
              .append(" ").append(Build.MODEL)
              .append(" | Android ").append(Build.VERSION.RELEASE)
              .append(" (API ").append(Build.VERSION.SDK_INT).append(")")
              .append(" | build type ").append(Build.TYPE).append("\n")
              .append("Bu uygulamanın paketi: ").append(getPackageName()).append("\n\n");

        PackageManager pm = getPackageManager();
        appendInstalledPackage(output, pm, "com.google.android.gms", "microG/GMS");
        appendInstalledPackage(output, pm, "com.android.chrome", "Chrome (kararlı)");
        appendInstalledPackage(output, pm, "com.chrome.beta", "Chrome Beta");
        appendInstalledPackage(output, pm, "com.chrome.dev", "Chrome Dev");
        appendInstalledPackage(output, pm, "com.chrome.canary", "Chrome Canary");
        appendInstalledPackage(output, pm, "org.chromium.chrome", "Chromium");
        appendInstalledPackage(output, pm, "com.android.vending", "Play Store");
        appendInstalledPackage(output, pm, "com.google.android.gsf", "Google Services Framework");
        output.append("\n");
        appendChromeAccountPermissions(output, pm);
        output.append("\n");
        appendPackageSigning(output, pm, "com.google.android.gms", "microG/GMS");
        appendPackageSigning(output, pm, "com.android.chrome", "Chrome");
        appendPackageSigning(output, pm, "com.android.vending", "Play/Companion");
        appendSpoofPermissionClue(output, pm);
        output.append("\n");
        appendWebHandlers(output, pm);
        output.append("\n");

        AccountManager accounts = AccountManager.get(this);
        try {
            boolean googleAuthenticatorPresent = false;
            for (AuthenticatorDescription authenticator : accounts.getAuthenticatorTypes()) {
                if (GOOGLE_ACCOUNT_TYPE.equals(authenticator.type)) {
                    googleAuthenticatorPresent = true;
                    output.append("Google hesap sağlayıcı paketi: ")
                          .append(authenticator.packageName).append("\n");
                    break;
                }
            }
            output.append("Google AccountManager sağlayıcısı: ")
                  .append(googleAuthenticatorPresent ? "GÖRÜLÜYOR" : "GÖRÜNMÜYOR")
                  .append("\n");
        } catch (RuntimeException error) {
            output.append("Google hesap sağlayıcısı: belirsiz (")
                  .append(error.getClass().getSimpleName()).append(")\n");
        }

        boolean permissionGranted = checkSelfPermission(Manifest.permission.GET_ACCOUNTS)
                == PackageManager.PERMISSION_GRANTED;
        output.append("GET_ACCOUNTS izni: ")
              .append(permissionGranted ? "VERİLMİŞ" : "VERİLMEMİŞ")
              .append("\n");

        if (permissionGranted) {
            try {
                Account[] visible = accounts.getAccountsByType(GOOGLE_ACCOUNT_TYPE);
                output.append("BU UYGULAMAYA GÖRÜNEN Google hesap SAYISI: ")
                      .append(visible.length).append("\n");
            } catch (SecurityException error) {
                output.append("Google hesabı sorgusu reddedildi (SecurityException)\n");
            } catch (RuntimeException error) {
                output.append("Google hesabı sorgusu başarısız (")
                      .append(error.getClass().getSimpleName()).append(")\n");
            }
        } else {
            output.append("Hesap sayısı izinsiz sorgulanmadı.\n");
        }

        output.append("\nCihazları karşılaştır: SAMSUNG A7 (Chrome girişi çalışıyor) "
            + "ve HUAWEI AQM-LX1 (Chrome girişi çalışmıyor).\n")
              .append("İmza taklidi için microG Self-Check ayrıca kontrol edilmelidir.\n")
              .append("\nÖNEMLİ: Bu APK kendi hesap görünürlüğünü ölçer; ")
              .append("Chrome'un hesabı gördüğünü veya imza taklidinin çalıştığını ")
              .append("kanıtlamaz. Hesap adı, e-posta, token veya şifre okumaz. ")
              .append("Google hesabını kaldırmaz, microG'yi değiştirmez.");
        report.setText(output.toString());
    }

    private static void appendInstalledPackage(
            StringBuilder out, PackageManager pm, String packageName, String label) {
        try {
            PackageInfo info = pm.getPackageInfo(packageName, 0);
            ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
            out.append(label).append(appInfo.enabled ? ": yüklü / etkin" : ": yüklü / DEVRE DIŞI");
            if (info.versionName != null) out.append(" (").append(info.versionName).append(")");
            out.append("\n");
        } catch (PackageManager.NameNotFoundException ignored) {
            out.append(label).append(": bulunamadı\n");
        } catch (RuntimeException error) {
            out.append(label).append(": sorgulanamadı (")
               .append(error.getClass().getSimpleName()).append(")\n");
        }
    }
    // Read-only comparison of PUBLIC APK signing certificates (no account data).
    // A cert mismatch between Chrome and microG is expected and not an error.
    private static void appendPackageSigning(
            StringBuilder out, PackageManager pm, String pkg, String label) {
        out.append(label).append(" imza sertifikası SHA-256: ");
        try {
            PackageInfo info = pm.getPackageInfo(
                pkg, PackageManager.GET_SIGNING_CERTIFICATES);
            SigningInfo signing = info.signingInfo;
            Signature[] signatures = signing == null
                    ? null : signing.getApkContentsSigners();
            if (signatures == null || signatures.length == 0) {
                out.append("BELİRSİZ (sertifika yok)\n");
                return;
            }
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signatures[0].toByteArray());
            StringBuilder hex = new StringBuilder(64);
            for (byte item : digest) {
                hex.append(String.format(Locale.ROOT, "%02x", item & 255));
            }
            out.append(hex).append("\n");
        } catch (PackageManager.NameNotFoundException e) {
            out.append("bulunamadı\n");
        } catch (Exception e) {
            out.append("sorgulanamadı (")
               .append(e.getClass().getSimpleName()).append(")\n");
        }
    }

    // A historical ROM permission is only a clue, NOT a functioning spoofing
    // test. ROMs can spoof signatures without exposing this permission.
    private static void appendSpoofPermissionClue(
            StringBuilder out, PackageManager pm) {
        String permission = "android.permission.FAKE_PACKAGE_SIGNATURE";
        out.append("microG imza taklidi izni ipucu: ");
        try {
            pm.getPermissionInfo(permission, 0);
            int granted = pm.checkPermission(permission, "com.google.android.gms");
            out.append(granted == PackageManager.PERMISSION_GRANTED
                    ? "tanımlı / microG'ye verilmiş"
                    : "tanımlı / microG'ye verilmemiş");
        } catch (PackageManager.NameNotFoundException e) {
            out.append("ROM'da bu isimle tanımlı değil");
        } catch (RuntimeException e) {
            out.append("sorgulanamadı (")
               .append(e.getClass().getSimpleName()).append(")");
        }
        out.append("\n");
        out.append("NOT: Bu izin kontrolü imza taklidinin çalıştığını kanıtlamaz "
            + "ve destek yok sonucunu da vermez.\n");
        out.append("İmzalar Google hesabına erişim veya Chrome girişi ölçümü değildir.\n");
    }

    // These are package-declared permissions, NOT the accounts exposed to Chrome.
    // This independent app cannot impersonate Chrome or inspect its private account picker.
    private static void appendChromeAccountPermissions(StringBuilder out, PackageManager pm) {
        out.append("Chrome hesap erişimi izin tanısı:\n");
        final String packageName = "com.android.chrome";
        try {
            PackageInfo chrome = pm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
            appendPermissionStatus(out, pm, chrome, packageName, Manifest.permission.GET_ACCOUNTS);
            appendPermissionStatus(
                out, pm, chrome, packageName, "android.permission.GET_ACCOUNTS_PRIVILEGED");
        } catch (PackageManager.NameNotFoundException error) {
            out.append("  Chrome paketi bulunamadı; izinler incelenemiyor.\n");
        } catch (RuntimeException error) {
            out.append("  İzin sorgusu başarısız (")
               .append(error.getClass().getSimpleName()).append(")\n");
        }
        out.append("NOT: İzin verilmiş olsa bile Chrome'un Google hesabını ")
           .append("veya oturum açma hizmetlerini kullanabildiği kanıtlanmaz.\n");
    }

    private static void appendPermissionStatus(
            StringBuilder out, PackageManager pm, PackageInfo info, String pkg, String permission) {
        boolean requested = false;
        boolean grantedByFlags = false;
        if (info.requestedPermissions != null) {
            for (int i = 0; i < info.requestedPermissions.length; i++) {
                if (permission.equals(info.requestedPermissions[i])) {
                    requested = true;
                    grantedByFlags = info.requestedPermissionsFlags != null
                        && i < info.requestedPermissionsFlags.length
                        && (info.requestedPermissionsFlags[i]
                           & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                    break;
                }
            }
        }

        String shortName = permission.substring("android.permission.".length());
        out.append("  ").append(shortName).append(": ");
        if (!requested) {
            out.append("manifestte istenmemiş\n");
        } else {
            boolean granted = pm.checkPermission(permission, pkg) ==
                PackageManager.PERMISSION_GRANTED;
            out.append(granted ? "İZİN VAR" : "İZİN YOK")
               .append(" (manifest grant=")
               .append(grantedByFlags ? "evet" : "hayır")
               .append(")\n");
        }
    }

    private static void appendWebHandlers(StringBuilder out, PackageManager pm) {
        out.append("HTTPS bağlantısı açabilen paketler (en fazla 12):\n");
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
            intent.addCategory(Intent.CATEGORY_BROWSABLE);
            List<ResolveInfo> handlers =
                pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
            LinkedHashSet<String> packages = new LinkedHashSet<>();
            for (ResolveInfo handler : handlers) {
                if (handler.activityInfo != null && handler.activityInfo.packageName != null) {
                    packages.add(handler.activityInfo.packageName);
                }
            }
            if (packages.isEmpty()) {
                out.append("  Uygun uygulama görünmedi.\n");
            } else {
                int count = 0;
                for (String pkg : packages) {
                    if (count++ >= 12) {
                        out.append("  ... daha fazlası olabilir\n");
                        break;
                    }
                    out.append("  ").append(pkg).append("\n");
                }
            }
            out.append("Bu liste Google hesap görünürlüğünü veya Chrome girişini ölçmez.\n");
        } catch (RuntimeException error) {
            out.append("  Sorgulanamadı (")
               .append(error.getClass().getSimpleName()).append(")\n");
        }
    }

}
