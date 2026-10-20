package dev.cihan.gmshwassistant;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.os.Build;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local, read-only Android PackageManager / AccountManager diagnostics. */
public final class DeviceScanner {
    public static final int OK = 0;
    public static final int CAUTION = 1;
    public static final int INFO = 2;

    public static final class Finding {
        public final String title;
        public final String details;
        public final int level;
        public Finding(String title, String details, int level) {
            this.title = title;
            this.details = details;
            this.level = level;
        }
    }

    public static final class Scan {
        public final List<Finding> findings = new ArrayList<>();
        public String report;
        public int installedPackages;
        public int cautions;
    }

    private static final String[] PACKAGE_NAMES = {
        "com.google.android.gms",
        "app.revanced.android.gms",
        "org.microg.gms",
        "com.android.vending",
        "com.google.android.gsf",
        "com.huawei.appmarket"
    };

    private DeviceScanner() {}

    public static Scan run(Context context) {
        PackageManager pm = context.getPackageManager();
        Scan scan = new Scan();
        StringBuilder report = new StringBuilder();
        report.append("GmsCore HW Assistant 1.0 | Yerel tani raporu\n");
        report.append("Salt okunur; e-posta, hesap adi, token, Android ID, ham log yok.\n\n");

        String manufacturer = safe(Build.MANUFACTURER);
        String model = safe(Build.MODEL);
        String android = safe(Build.VERSION.RELEASE);
        String emui = safe(getEmuiVersion());
        add(scan, report, "Cihaz",
                manufacturer + " " + model + "\nAndroid " + android +
                " (API " + Build.VERSION.SDK_INT + ")\nEMUI: " + emui, INFO);

        boolean playStore = false;
        boolean morphe = false;
        boolean huawei = false;
        for (String pkg : PACKAGE_NAMES) {
            PackageInfo info = packageInfo(pm, pkg);
            if (info == null) {
                add(scan, report, pkg, "Kurulu degil veya Android tarafindan gorunmuyor", INFO);
                continue;
            }
            scan.installedPackages++;
            if ("com.android.vending".equals(pkg)) playStore = true;
            if ("app.revanced.android.gms".equals(pkg)) morphe = true;
            if ("com.google.android.gms".equals(pkg)) huawei = true;
            String certificate = certificateSha256(info);
            String signatureNote = "app.revanced.android.gms".equals(pkg)
                    ? "\nReferans: " + DiagnosticRules.signatureLabel(pkg, certificate)
                    : "";
            add(scan, report, pkg,
                    "Surum: " + safe(info.versionName) +
                    " | Kod: " + versionCode(info) +
                    "\nAPK imzasi SHA-256: " + certificate + signatureNote, OK);
        }

        if (morphe && huawei) {
            add(scan, report, "Iki farkli microG/GMS paketi",
                    "com.google.android.gms ve app.revanced.android.gms birlikte goruldu. " +
                    "Farkli kimliklerdir; hesap ve Play Store uyumu dogrulanmadi.", CAUTION);
        } else if (morphe) {
            add(scan, report, "microG-RE paket kimligi",
                    "ReVanced/Morphe GmsCore kullaniliyor. Bu, sistem genelinde " +
                    "Google imzasi taklidinin etkin oldugu anlamina gelmez.", INFO);
        }

        if (playStore) {
            for (String permission : new String[] {
                    "android.permission.INTERACT_ACROSS_USERS",
                    "android.permission.MANAGE_USERS",
                    "android.permission.CREATE_USERS"
            }) {
                boolean granted;
                try {
                    granted = pm.checkPermission(permission, "com.android.vending")
                            == PackageManager.PERMISSION_GRANTED;
                } catch (RuntimeException ignored) {
                    add(scan, report, permission, "Izin sorgusu Android tarafindan engellendi", INFO);
                    continue;
                }
                String shortName = permission.substring(permission.lastIndexOf('.') + 1);
                add(scan, report, "Play Store: " + shortName,
                        DiagnosticRules.protectedPermission(true, granted) +
                        "\nBeyan/izin durumu crash nedenini tek basina kanitlamaz.",
                        granted ? INFO : CAUTION);
            }
        } else {
            add(scan, report, "Play Store izinleri",
                    DiagnosticRules.protectedPermission(false, false), INFO);
        }

        boolean serviceSeen = false;
        try {
            Intent intent = new Intent("com.huawei.signature.diff");
            List<ResolveInfo> services = pm.queryIntentServices(intent, 0);
            serviceSeen = services != null && !services.isEmpty();
        } catch (RuntimeException ignored) {
            // A missing result may indicate OS visibility restrictions, not missing support.
        }
        add(scan, report, "Huawei SignatureService",
                DiagnosticRules.spoofingService(serviceSeen), INFO);

        try {
            boolean googleAuth = false;
            boolean revancedAuth = false;
            AuthenticatorDescription[] authenticators =
                    AccountManager.get(context).getAuthenticatorTypes();
            for (AuthenticatorDescription authenticator : authenticators) {
                if ("com.google".equals(authenticator.type)) googleAuth = true;
                if ("app.revanced".equals(authenticator.type)) revancedAuth = true;
            }
            add(scan, report, "Google hesap dogrulayicisi (com.google)",
                    DiagnosticRules.accountType(googleAuth), INFO);
            add(scan, report, "Morphe hesap dogrulayicisi (app.revanced)",
                    DiagnosticRules.accountType(revancedAuth), INFO);
        } catch (SecurityException e) {
            add(scan, report, "Hesap dogrulayicilari",
                    "Android guvenlik politikasi sorguya izin vermedi. Hesaplar okunmadi.", CAUTION);
        } catch (RuntimeException e) {
            add(scan, report, "Hesap dogrulayicilari",
                    "Bu sistemde dogrulayici turleri sorgulanamadi.", INFO);
        }

        add(scan, report, "Sinirlar",
                "Korunmus Android izinleri verilemez. Play Store'un ham crash kayitlari " +
                "uygulama icinden okunamaz. Imza/servis/hesap dogrulayicisi gorunmesi " +
                "Google Play calisiyor demek degildir.", INFO);
        scan.report = report.toString();
        return scan;
    }

    private static void add(Scan scan, StringBuilder report,
                            String title, String detail, int level) {
        scan.findings.add(new Finding(title, detail, level));
        if (level == CAUTION) scan.cautions++;
        report.append("[").append(level == CAUTION ? "DIKKAT" : level == OK ? "BILGI" : "NOT")
                .append("] ").append(title).append("\n").append(detail).append("\n\n");
    }

    private static PackageInfo packageInfo(PackageManager pm, String pkg) {
        try {
            int flag = Build.VERSION.SDK_INT >= 28
                    ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            return pm.getPackageInfo(pkg, flag);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String certificateSha256(PackageInfo info) {
        try {
            Signature[] signatures = Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                    ? info.signingInfo.getApkContentsSigners() : info.signatures;
            if (signatures == null || signatures.length == 0) return "Dogrulanamadi";
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(signatures[0].toByteArray());
            StringBuilder sb = new StringBuilder(64);
            for (byte b : digest) sb.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return sb.toString();
        } catch (Exception ignored) {
            return "Dogrulanamadi";
        }
    }

    private static long versionCode(PackageInfo info) {
        return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
    }

    private static String getEmuiVersion() {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            java.lang.reflect.Method get = systemProperties.getDeclaredMethod("get", String.class);
            Object value = get.invoke(null, "ro.build.version.emui");
            if (value != null && !value.toString().isEmpty()) return value.toString();
        } catch (Exception ignored) {
            // Reflection on Android's hidden API may be blocked.
        }
        return "Uygulama tarafindan okunamadi";
    }

    private static String safe(String value) {
        if (value == null) return "Bilinmiyor";
        return value.replace('\n', ' ').replace('\r', ' ').trim();
    }
}
