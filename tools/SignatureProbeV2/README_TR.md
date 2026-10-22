# PackageManager Signature Probe v2.0
Huawei P Smart S / EMUI 12 / Android 10 için root gerektirmeyen, salt okunur tanılama uygulaması.

- GET_SIGNATURES ve GET_SIGNING_CERTIFICATES sonuçlarını karşılaştırır.
- PackageManager.hasSigningCertificate ile önceki Google Play Store sertifikası SHA-256 parmak izini sorgular.
- Gmail görünürlüğünü ve MATCH_UNINSTALLED_PACKAGES kaydını kontrol eder.
- checkSignatures sonuçlarını görüntüler.
- İsteğe bağlı olarak microG-HW SignatureService'e bindService yaparak Binder descriptor okur; querySignature çağırmaz.
- İsteğe bağlı aynı UID içinde ikinci Android işleminde imza sorgusu yapar. Bu Gmail işlemi değildir.
- TXT dışa aktarır.

**Sınır:** Başarılı Binder bağlantısı Huawei firmware'inin PackageManager'dan bu servise başvurduğunu kanıtlamaz. Uygulama hiçbir imzayı değiştirmez. İnternet, root ve Shizuku izni istemez.

Paket: dev.cihan.signatureprobe.v2. v1 ile yan yana kurulabilir.
APK: GitHub Actions / Signature Probe v2 APK / signature-probe-v2-apk.
