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
        "com.google.android.gmt",
        "com.huawei.appmarket",
        "com.huawei.signature.diff"
    };

    private DeviceScanner() {}

    public static Scan run(Context context) {
        PackageManager pm = context.getPackageManager();
        Scan scan = new Scan();
        StringBuilder report = new StringBuilder();
        report.append("Huawei GMS Diagnostics 2.0 | Yerel tani raporu\n");
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
        boolean googleServices = false;
        for (String pkg : PACKAGE_NAMES) {
            PackageInfo info = packageInfo(pm, pkg);
            if (info == null) {
                add(scan, report, pkg, "Kurulu degil veya Android tarafindan gorunmuyor", INFO);
                continue;
            }
            scan.installedPackages++;
            if ("com.android.vending".equals(pkg)) playStore = true;
            if ("app.revanced.android.gms".equals(pkg)) morphe = true;
            if ("com.google.android.gms".equals(pkg)) googleServices = true;
            String certificate = certificateSha256(info);
            String signatureNote = "app.revanced.android.gms".equals(pkg)
                    ? "\nReferans: " + DiagnosticRules.signatureLabel(pkg, certificate)
                    : "";
            add(scan, report, pkg,
                    "Surum: " + safe(info.versionName) +
                    " | Kod: " + versionCode(info) +
                    "\nAPK imzasi SHA-256: " + certificate + signatureNote, OK);
        }

        if (morphe && googleServices) {
            add(scan, report, "Iki farkli microG/GMS paketi",
                    "com.google.android.gms ve app.revanced.android.gms birlikte goruldu. " +
                    "Farkli kimliklerdir; hesap ve Play Store uyumu dogrulanmadi.", CAUTION);
        } else if (morphe) {
            add(scan, report, "microG-RE paket kimligi",
                    "ReVanced/Morphe GmsCore kullaniliyor. Bu, sistem genelinde " +
                    "Google imzasi taklidinin etkin oldugu anlamina gelmez.", INFO);
        }


        // Permission state is a static capability check, not an inference from logcat.
        // Report each relevant APK independently: GSF, GMS and Play Store differ.
        final String[][] relevantPermissions = {
            {"com.google.android.gsf", "android.permission.MANAGE_USERS"},
            {"com.google.android.gsf", "android.permission.INTERACT_ACROSS_USERS"},
            {"com.google.android.gms", "android.permission.MANAGE_USERS"},
            {"com.google.android.gms", "android.permission.INTERACT_ACROSS_USERS"},
            {"com.android.vending", "com.google.android.providers.gsf.permission.READ_GSERVICES"},
            {"com.android.vending", "com.google.android.providers.gsf.permission.WRITE_GSERVICES"},
            {"com.android.vending", "android.permission.INTERACT_ACROSS_USERS"},
            {"com.android.vending", "android.permission.MANAGE_USERS"}
        };
        for (String[] item : relevantPermissions) {
            PackageInfo targetInfo = packageInfo(pm, item[0]);
            if (targetInfo == null) continue;
            String shortName = item[1].substring(item[1].lastIndexOf('.') + 1);
            boolean requested = hasPermissionRequest(targetInfo, item[1]);
            boolean granted;
            try {
                granted = pm.checkPermission(item[1], item[0]) == PackageManager.PERMISSION_GRANTED;
            } catch (RuntimeException ignored) {
                add(scan, report, item[0] + ": " + shortName,
                        "Android izin durumu sorgusunu engelledi; kesin sonuc yok.", INFO);
                continue;
            }
            String conclusion = DiagnosticRules.permissionState(requested, granted, item[1]);
            add(scan, report, item[0] + ": " + shortName, conclusion,
                    requested && !granted ? CAUTION : INFO);
        }

        if (playStore && packageInfo(pm, "com.google.android.gsf") == null) {
            add(scan, report, "Play Store / GSF",
                    "Play Store gorunuyor ama GSF paketi gorunmuyor. Kurulum veya gorunurluk kisitlarini arastirin.", CAUTION);
        }
        PackageInfo currentGsf = packageInfo(pm, "com.google.android.gsf");
        if (currentGsf != null && !hasPermissionRequest(currentGsf, "android.permission.MANAGE_USERS")) {
            add(scan, report, "Mevcut GSF / eski crash farki",
                    "Mevcut GSF APK'si MANAGE_USERS iznini ISTEMIYOR. "
                    + "Eski GSF surumunun MANAGE_USERS crash kaydi bu kurulumda "
                    + "otomatik olarak gecerli sayilamaz. Yeni crash icin ADB logcat kullanin.", INFO);
        }

        // Huawei action visibility alone is not proof of firmware integration.
        // microG-HW itself declares com.huawei.signature.diff.SignatureService.
        boolean serviceSeen = false;
        String serviceOwner = null;
        try {
            Intent intent = new Intent("com.huawei.signature.diff");
            List<ResolveInfo> services = pm.queryIntentServices(intent, 0);
            if (services != null) {
                for (ResolveInfo service : services) {
                    if (service.serviceInfo != null) {
                        serviceSeen = true;
                        serviceOwner = service.serviceInfo.packageName;
                        break;
                    }
                }
            }
        } catch (RuntimeException ignored) {
            // A missing result may indicate Android visibility restrictions.
        }
        add(scan, report, "Huawei SignatureService / saglayici",
                DiagnosticRules.signatureServiceOrigin(serviceSeen ? serviceOwner : null), INFO);

        try {
            boolean googleAuth = false;
            boolean revancedAuth = false;
            String googleProvider = "Gorunmedi";
            AuthenticatorDescription[] authenticators =
                    AccountManager.get(context).getAuthenticatorTypes();
            for (AuthenticatorDescription authenticator : authenticators) {
                if ("com.google".equals(authenticator.type)) {
                    googleAuth = true;
                    googleProvider = safe(authenticator.packageName);
                }
                if ("app.revanced".equals(authenticator.type)) revancedAuth = true;
            }
            add(scan, report, "Google hesap dogrulayicisi (com.google)",
                    DiagnosticRules.accountType(googleAuth), INFO);
            add(scan, report, "Google hesap saglayici paketi",
                    "Gorunen kayit: " + googleProvider + "\nKayit bulunmasi girisin calistigini kanitlamaz.", INFO);
            add(scan, report, "Morphe hesap dogrulayicisi (app.revanced)",
                    DiagnosticRules.accountType(revancedAuth), INFO);
        } catch (SecurityException e) {
            add(scan, report, "Hesap dogrulayicilari",
                    "Android guvenlik politikasi sorguya izin vermedi. Hesaplar okunmadi.", CAUTION);
        } catch (RuntimeException e) {
            add(scan, report, "Hesap dogrulayicilari",
                    "Bu sistemde dogrulayici turleri sorgulanamadi.", INFO);
        }

        add(scan, report, "ADB ile ek tani",
                "Ayrintili GSF/GMS crash analizi telefon uzerinden yapilamaz. " +
                "Linux Mint bilgisayarinizda GmsCore deposundaki " +
                "tools/huawei_playstore_errors.py ve tools/huawei_gms_diagnostics.py araclarini " +
                "yalnizca kendi cihazinizda calistirin. Loglari paylasmadan once " +
                "e-posta, hesap kimlikleri ve tokenlari ayiklayin.", INFO);
        add(scan, report, "Sinirlar",
                "Uygulama sistem izinlerini degistirmez, spoofing'i acmaz veya "
                + "Google hesabina erisemez. Ucuncu taraf logcat, cihazdan okunamaz. "
                + "Gorunen paket, izin ve hesap kaydi Play Store'un calistigini kanitlamaz.", INFO);
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

    private static boolean hasPermissionRequest(PackageInfo info, String name) {
        if (info.requestedPermissions == null) return false;
        for (String requested : info.requestedPermissions) {
            if (name.equals(requested)) return true;
        }
        return false;
    }

    private static PackageInfo packageInfo(PackageManager pm, String pkg) {
        try {
            int flag = Build.VERSION.SDK_INT >= 28
                    ? (PackageManager.GET_SIGNING_CERTIFICATES | PackageManager.GET_PERMISSIONS)
                    : (PackageManager.GET_SIGNATURES | PackageManager.GET_PERMISSIONS);
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
