// Modified 2026-10-20 for P Smart S app-process proof-of-concept; GPL-3.0-or-later.
// Upstream: https://github.com/whew-inc/FakeGApps (tag 6.6)
package inc.whew.android.fakegapps;

import android.annotation.TargetApi;
import android.content.pm.PackageInfo;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;
import android.util.ArraySet;
import android.util.Base64;

import java.io.ByteArrayInputStream;
import java.lang.ReflectiveOperationException;
import java.lang.reflect.Constructor;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.PublicKey;
import java.util.AbstractMap.SimpleEntry;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class FakeSignatures implements IXposedHookLoadPackage {
    private static final String TAG = "FakeGApps";
    private static final String _x509cert = "MIIEQzCCAyugAwIBAgIJAMLgh0ZkSjCNMA0GCSqGSIb3DQEBBAUAMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDAeFw0wODA4MjEyMzEzMzRaFw0zNjAxMDcyMzEzMzRaMHQxCzAJBgNVBAYTAlVTMRMwEQYDVQQIEwpDYWxpZm9ybmlhMRYwFAYDVQQHEw1Nb3VudGFpbiBWaWV3MRQwEgYDVQQKEwtHb29nbGUgSW5jLjEQMA4GA1UECxMHQW5kcm9pZDEQMA4GA1UEAxMHQW5kcm9pZDCCASAwDQYJKoZIhvcNAQEBBQADggENADCCAQgCggEBAKtWLgDYO6IIrgqWbxJOKdoR8qtW0I9Y4sypEwPpt1TTcvZApxsdyxMJZ2JORland2qSGT2y5b+3JKkedxiLDmpHpDsz2WCbdxgxRczfey5YZnTJ4VZbH0xqWVW/8lGmPav5xVwnIiJS6HXk+BVKZF+JcWjAsb/GEuq/eFdpuzSqeYTcfi6idkyugwfYwXFU1+5fZKUaRKYCwkkFQVfcAs1fXA5V+++FGfvjJ/CxURaSxaBvGdGDhfXE28LWuT9ozCl5xw4Yq5OGazvV24mZVSoOO0yZ31j7kYvtwYK6NeADwbSxDdJEqO4k//0zOHKrUiGYXtqw/A0LFFtqoZKFjnkCAQOjgdkwgdYwHQYDVR0OBBYEFMd9jMIhF1Ylmn/Tgt9r45jk14alMIGmBgNVHSMEgZ4wgZuAFMd9jMIhF1Ylmn/Tgt9r45jk14aloXikdjB0MQswCQYDVQQGEwJVUzETMBEGA1UECBMKQ2FsaWZvcm5pYTEWMBQGA1UEBxMNTW91bnRhaW4gVmlldzEUMBIGA1UEChMLR29vZ2xlIEluYy4xEDAOBgNVBAsTB0FuZHJvaWQxEDAOBgNVBAMTB0FuZHJvaWSCCQDC4IdGZEowjTAMBgNVHRMEBTADAQH/MA0GCSqGSIb3DQEBBAUAA4IBAQBt0lLO74UwLDYKqs6Tm8/yzKkEu116FmH4rkaymUIE0P9KaMftGlMexFlaYjzmB2OxZyl6euNXEsQH8gjwyxCUKRJNexBiGcCEyj6z+a1fuHHvkiaai+KL8W1EyNmgjmyy8AW7P+LLlkR+ho5zEHatRbM/YAnqGcFh5iZBqpknHf1SKMXFh4dd239FJ1jWYfbMDMy3NS5CTMQ2XFI1MvcyUTdZPErjQfTbQe3aDQsQcafEQPD+nqActifKZ0Np0IS9L9kR/wbNvyz6ENwPiTrjV2KRkEjH78ZMcUQXg0L3BYHJ3lc69Vs5Ddf9uUGGMYldX3WfMBEmh/9iFBDAaTCK";

    @Override
    public void handleLoadPackage(LoadPackageParam loadedPackage) throws CertificateException {
        if (!loadedPackage.packageName.equals("dev.cihan.fakegapps.probe"))
            return;

        final byte[] certBytes = Base64.decode(_x509cert, Base64.DEFAULT);
        final CertificateFactory certFactory = CertificateFactory.getInstance("X.509");
        final Certificate cert = certFactory.generateCertificate(new ByteArrayInputStream(certBytes));

        XC_MethodHook hook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                PackageInfo pi = (PackageInfo) param.getResult();
                if (pi != null) {
                    String packageName = pi.packageName;
                    // Observable canary: modifies only the PROBE's local package-info result.
                    // It does NOT change Android's stored package version or package signatures.
                    if (packageName.equals("dev.cihan.fakegapps.probe")) {
                        pi.versionName = (pi.versionName == null ? "" : pi.versionName)
                            + "-hook-active";
                        // Positive control: only replace the PROBE's own locally returned
                        // signature, never the actual APK certificate or system state.
                        // Unmodified getPackageArchiveInfo below allows a disk comparison.
                        if (pi.signatures != null && pi.signatures.length > 0) {
                            pi.signatures = new Signature[]{new Signature(certBytes)};
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && pi.signingInfo != null) {
                            SigningInfo signingInfo = createSigningInfo(
                                new Signature(certBytes), cert.getPublicKey());
                            if (signingInfo != null) {
                                pi.signingInfo = signingInfo;
                            }
                        }
                        param.setResult(pi);
                        return;
                    }
                    if (packageName.equals("com.google.android.gms") || packageName.equals("com.android.vending")) {
                        pi.signatures = new Signature[]{new Signature(certBytes)};

                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                            SigningInfo signingInfo = createSigningInfo(new Signature(certBytes), cert.getPublicKey());
                            if (signingInfo != null) {
                                pi.signingInfo = signingInfo;
                            }
                        }

                        param.setResult(pi);
                    }
                }
            }
        };


        // LSPatch has no hook into Android's system_server.
        // This is intentionally limited to an independent disposable PROBE APK.
        // Hook only the local PackageManager client result, never system state.
        if (Build.VERSION.SDK_INT != Build.VERSION_CODES.Q) {
            XposedBridge.log(TAG + " PSmartS: Android 10 only; hook skipped");
            return;
        }
        try {
            final Class<?> hookedClass = XposedHelpers.findClass(
                "android.app.ApplicationPackageManager", loadedPackage.classLoader);
            XposedBridge.hookAllMethods(hookedClass, "getPackageInfo", hook);
            XposedBridge.log(TAG + " PSmartS: probe-only PackageInfo hook registered");
        } catch (Throwable t) {
            XposedBridge.log(TAG + " PSmartS: hook registration failed");
            XposedBridge.log(t);
        }
    }

    private static Class<?> findFirstLoadableClass(String... candidates) throws ClassNotFoundException {
        ClassNotFoundException exc = new ClassNotFoundException();
        for (String candidate : candidates) {
            try {
                return Class.forName(candidate);
            } catch (ClassNotFoundException e) {
                exc = e;
            }
        }
        throw exc;
    }

    private static <T> T invokeFirstConstructor(
        Class<T> cls,
        SimpleEntry<Class<?>[], Object[]>... candidates
    ) throws ReflectiveOperationException {
        NoSuchMethodException exc = new NoSuchMethodException();
        for (SimpleEntry<Class<?>[], Object[]> candidate : candidates) {
            Constructor<T> constructor;
            try {
                constructor = cls.getDeclaredConstructor(candidate.getKey());
            } catch (NoSuchMethodException e) {
                exc = e;
                continue;
            }

            constructor.setAccessible(true);
            return constructor.newInstance(candidate.getValue());
        }
        throw exc;
    }

    @TargetApi(android.os.Build.VERSION_CODES.P)
    private SigningInfo createSigningInfo(Signature sig, PublicKey publicKey) {
        final int SIGNING_BLOCK_V3 = 3;
        final Signature[] sigs = new Signature[]{sig};
        final ArraySet<PublicKey> pks = new ArraySet<>();
        pks.add(publicKey);

        // Unfortunately, SigningDetails is not exported in SDK, so we have to rely on reflection.
        // Also, public SigningInfo constructor is only available from API 35, so we can't use it.
        try {
            Class<?> signingDetailsClass = findFirstLoadableClass(
                "android.content.pm.SigningDetails",
                // Android 9 to 12 have SigningDetails embedded in the PackageParser class
                "android.content.pm.PackageParser$SigningDetails"
            );
            Object signingDetails = invokeFirstConstructor(
                signingDetailsClass,
                // https://cs.android.com/android/platform/superproject/+/android-15.0.0_r17:frameworks/base/core/java/android/content/pm/SigningDetails.java;l=146
                new SimpleEntry<Class<?>[], Object[]>(
                    new Class<?>[]{
                        Signature[].class, // signatures
                        int.class, // signatureSchemeVersion
                        ArraySet.class, // keys
                        Signature[].class // pastSigningCertificates
                    },
                    new Object[]{sigs, SIGNING_BLOCK_V3, pks, null}
                ),
                // Android 9 had an extra "pastSigningCertificatesFlags" argument
                // https://cs.android.com/android/platform/superproject/+/android-9.0.0_r60:frameworks/base/core/java/android/content/pm/PackageParser.java;l=5739
                new SimpleEntry<Class<?>[], Object[]>(
                    new Class<?>[]{
                        Signature[].class, // signatures
                        int.class, // signatureSchemeVersion
                        ArraySet.class, // keys
                        Signature[].class, // pastSigningCertificates
                        int[].class // pastSigningCertificatesFlags
                    },
                    new Object[]{sigs, SIGNING_BLOCK_V3, pks, null, null}
                )
            );

            Constructor<SigningInfo> signingInfoConstructor = SigningInfo.class.getDeclaredConstructor(signingDetailsClass);
            signingInfoConstructor.setAccessible(true);
            return signingInfoConstructor.newInstance(signingDetails);
        } catch (Exception e) {
            XposedBridge.log(String.format("%s failed to create signingInfo", TAG));
            XposedBridge.log(e);
        }

        return null;
    }
}
