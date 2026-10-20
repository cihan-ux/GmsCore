package dev.cihan.gmshwassistant;

import java.util.Locale;

/** Pure, deterministic classifications. Never treats package presence as proof of GMS compatibility. */
public final class DiagnosticRules {
    private DiagnosticRules() {}

    public static final String MORPHE_CERT = "0b6c9515afb195fac59601696ba0a7907a0b217ccf720b43148427ccf64343e7";
    public static final String WALLET_A_DEBUG_CERT = "05fb84ad3ff95e10f4f095433948400097330111fff1d058ac5fe1c6e0451fd1";

    public static String signatureLabel(String packageName, String sha256) {
        if (!"app.revanced.android.gms".equals(packageName) || sha256 == null) {
            return "Imza referansi tanimli degil";
        }
        String value = sha256.toLowerCase(Locale.ROOT);
        if (MORPHE_CERT.equals(value)) return "Onceki Morphe microG-RE imzasi";
        if (WALLET_A_DEBUG_CERT.equals(value)) return "Wallet A deneyindeki debug imzasi";
        return "Diger / tanimlanmamis sertifika";
    }

    /** Permission request, OS grant and system restriction are separate facts. */
    public static String permissionState(boolean requested, boolean granted, String permission) {
        if (!requested) return "APK bu izni talep etmiyor; eksik izin olarak yorumlanamaz.";
        if (granted) return "Izin verilmis; calisma ve uyumluluk kaniti degil.";
        if ("android.permission.MANAGE_USERS".equals(permission))
            return "Izin istenmis ama VERILMEMIS. signature|privileged; normal ADB ile verilemez.";
        if ("android.permission.INTERACT_ACROSS_USERS".equals(permission))
            return "Izin istenmis ama VERILMEMIS. Bazi Android surumlerinde development grant denenebilir; burada degistirilmez.";
        return "Izin istenmis ama VERILMEMIS. GMS/GSF ve APK uyumlulugunu inceleyin.";
    }

    public static String protectedPermission(boolean installed, boolean granted) {
        if (!installed) return "Play Store bulunamadi";
        return granted ? "Izin verilmis (islevsellik kaniti degil)" :
                "Izin verilmemis (root'suz uygulama bu izni veremez)";
    }

    /** The resolving service may be bundled in microG-HW itself, not Huawei firmware. */
    public static String signatureServiceOrigin(String owner) {
        if (owner == null || owner.isEmpty())
            return "Servis sahibi gorunmedi; bu, firmware desteginin yoklugunu kanitlamaz.";
        if ("com.google.android.gms".equals(owner))
            return "Saglayici: " + owner + " (microG-HW/GMS paketi). "
                    + "microG'nin servisi gorunuyor; EMUI'nin bunu kullandigi veya "
                    + "imza taklidinin CALISTIGI dogrulanmadi.";
        return "Saglayici: " + owner + ". Servisin varligi tek basina "
                + "EMUI imza taklidi destegini kanitlamaz.";
    }

    public static String spoofingService(boolean visible) {
        return visible
                ? "Huawei SignatureService gorunuyor; gercek spoofing destegi DOGRULANMADI"
                : "Huawei SignatureService gorunmedi; yoklugu kesin degil";
    }

    public static String accountType(boolean seen) {
        return seen ? "Dogrulayici kayitli (hesap varligini kanitlamaz)"
                : "Dogrulayici gorulmedi (hesap yoklugu kanitlanmaz)";
    }
}
