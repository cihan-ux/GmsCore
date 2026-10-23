package dev.cihan.gmailhwbridge;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.ArraySet;
import android.util.Base64;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Locale;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Experimental, Gmail-only signature compatibility shim for microG-HW.
 *
 * This does NOT alter the real Android PackageManager or microG. It adjusts only
 * signature data returned to a Gmail process patched with LSPatch.
 *
 * Safety gate: the original APK certificate must match the known microG-HW
 * NOGAPPS certificate. Other packages or a replacement GMS are not trusted.
 */
public final class BridgeHook implements IXposedHookLoadPackage {
    private static final String TAG = "GmailMicroGHW";
    private static final String GMAIL = "com.google.android.gm";
    private static final String GMS = "com.google.android.gms";
    private static final String VENDING = "com.android.vending";
    private static final String NOGAPPS_SHA256 = "9BD06727E62796C0130EB6DAB39B73157451582CBD138E86C468ACC395D14165";
    private static final String GOOGLE_CERT_SHA256 = "F0FD6C5B410F25CB25C3B53346C8972FAE30F8EE7411DF910480AD6B2D60DB83";
    private static final String GOOGLE_CERT_BASE64 = "MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVKZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOjgdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXikdjB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29vZ2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjwyxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KRkEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK";
    private final ThreadLocal<Boolean> unmodifiedQuery = new ThreadLocal<>();
    private Signature googleSignature;
    private PublicKey googlePublicKey;

    @Override
    public void handleLoadPackage(LoadPackageParam loaded) {
        if (!GMAIL.equals(loaded.packageName)) return;
        if (Build.VERSION.SDK_INT != 29) {
            log("Inactive: designed for API 29 only; found " + Build.VERSION.SDK_INT);
            return;
        }
        try {
            byte[] cert = Base64.decode(GOOGLE_CERT_BASE64, Base64.DEFAULT);
            if (!GOOGLE_CERT_SHA256.equals(sha256(cert))) {
                log("Inactive: Google reference certificate integrity check failed");
                return;
            }
            googleSignature = new Signature(cert);
            googlePublicKey = ((X509Certificate) CertificateFactory.getInstance("X.509")
                .generateCertificate(new ByteArrayInputStream(cert))).getPublicKey();
            Class<?> clazz = XposedHelpers.findClass(
                "android.app.ApplicationPackageManager", loaded.classLoader);

            XC_MethodHook pkgHook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Boolean.TRUE.equals(unmodifiedQuery.get())) return;
                    if (p.hasThrowable() || p.args.length == 0 || !(p.args[0] instanceof String)) return;
                    String name = (String) p.args[0];
                    if (!tracked(name)) return;
                    if (!(p.getResult() instanceof PackageInfo)) return;
                    PackageInfo info = (PackageInfo) p.getResult();
                    if (!originalIsExpectedMicrog(info)) return;
                    info.signatures = new Signature[] {googleSignature};
                    // SigningInfo is a hidden-API-backed object on Android 10.
                    // If creation fails, the legacy signature still changes.
                    SigningInfo replacement = buildSigningInfo();
                    if (replacement != null) info.signingInfo = replacement;
                    p.setResult(info);
                    log("Scoped signature override: " + name +
                        ", SigningInfo=" + (replacement != null));
                }
            };
            XposedBridge.hookAllMethods(clazz, "getPackageInfo", pkgHook);
            XposedBridge.hookAllMethods(clazz, "getPackageInfoAsUser", pkgHook);

            XC_MethodHook certHook = new XC_MethodHook() {
                @Override protected void afterHookedMethod(MethodHookParam p) {
                    if (Boolean.TRUE.equals(unmodifiedQuery.get())) return;
                    if (p.hasThrowable() || p.args.length < 3 ||
                        !(p.args[0] instanceof String) ||
                        !(p.args[1] instanceof byte[]) ||
                        !(p.args[2] instanceof Integer)) return;
                    String name = (String) p.args[0];
                    if (!tracked(name)) return;
                    if (!matchesGoogleCertificate((byte[])p.args[1], (Integer)p.args[2])) return;
                    if (!verifiedFromOriginalPackage((PackageManager)p.thisObject, name)) return;
                    p.setResult(true);
                    log("Scoped hasSigningCertificate override: " + name);
                }
            };
            XposedBridge.hookAllMethods(clazz, "hasSigningCertificate", certHook);
            log("Module active only in Gmail; target=microG-HW; SDK=29");
        } catch (Throwable error) {
            log("Module initialization failure: " + error);
            XposedBridge.log(error);
        }
    }

    private boolean verifiedFromOriginalPackage(PackageManager pm, String name) {
        if (pm == null || !tracked(name)) return false;
        unmodifiedQuery.set(Boolean.TRUE);
        try {
            PackageInfo info = pm.getPackageInfo(name,
                PackageManager.GET_SIGNATURES | PackageManager.GET_SIGNING_CERTIFICATES);
            return originalIsExpectedMicrog(info);
        } catch (Throwable t) {
            log("Original microG verification failed: " + t);
            return false;
        } finally {
            unmodifiedQuery.remove();
        }
    }

    private boolean originalIsExpectedMicrog(PackageInfo info) {
        if (info == null || !tracked(info.packageName)) return false;
        String version = info.versionName == null ? "" : info.versionName;
        if (!version.endsWith("-hw")) return false;
        Signature[] signatures = info.signatures;
        if ((signatures == null || signatures.length == 0) && info.signingInfo != null)
            signatures = info.signingInfo.getApkContentsSigners();
        return signatures != null && signatures.length == 1 &&
            NOGAPPS_SHA256.equals(sha256(signatures[0].toByteArray()));
    }

    private boolean matchesGoogleCertificate(byte[] candidate, int type) {
        if (candidate == null) return false;
        if (type == PackageManager.CERT_INPUT_RAW_X509)
            return Arrays.equals(candidate, googleSignature.toByteArray());
        if (type == PackageManager.CERT_INPUT_SHA256)
            return GOOGLE_CERT_SHA256.equals(upperHex(candidate));
        return false;
    }

    private SigningInfo buildSigningInfo() {
        try {
            Class<?> cls;
            try { cls = Class.forName("android.content.pm.SigningDetails"); }
            catch (ClassNotFoundException e) {
                cls = Class.forName("android.content.pm.PackageParser$SigningDetails");
            }
            ArraySet<PublicKey> keys = new ArraySet<>();
            keys.add(googlePublicKey);
            Object details = null;
            for (Constructor<?> c : cls.getDeclaredConstructors()) {
                Class<?>[] types = c.getParameterTypes();
                boolean v4 = types.length == 4 &&
                    types[0] == Signature[].class && types[1] == int.class &&
                    types[2] == ArraySet.class && types[3] == Signature[].class;
                boolean v5 = types.length == 5 &&
                    types[0] == Signature[].class && types[1] == int.class &&
                    types[2] == ArraySet.class && types[3] == Signature[].class &&
                    types[4] == int[].class;
                if (!v4 && !v5) continue;
                c.setAccessible(true);
                Object[] args = v4
                    ? new Object[] {new Signature[]{googleSignature}, 3, keys, null}
                    : new Object[] {new Signature[]{googleSignature}, 3, keys, null, null};
                details = c.newInstance(args);
                break;
            }
            if (details == null) return null;
            Constructor<SigningInfo> factory = SigningInfo.class.getDeclaredConstructor(cls);
            factory.setAccessible(true);
            return factory.newInstance(details);
        } catch (Throwable err) {
            log("SigningInfo reflection blocked: " + err.getClass().getSimpleName());
            return null;
        }
    }

    private static boolean tracked(String name) {
        return GMS.equals(name) || VENDING.equals(name);
    }

    private static String sha256(byte[] input) {
        try {
            return upperHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (Exception e) { return ""; }
    }
    private static String upperHex(byte[] bytes) {
        char[] hex = "0123456789ABCDEF".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            result[i * 2] = hex[(bytes[i] >>> 4) & 15];
            result[i * 2 + 1] = hex[bytes[i] & 15];
        }
        return new String(result);
    }
    private static void log(String message) {
        XposedBridge.log(TAG + ": " + message);
    }
}
