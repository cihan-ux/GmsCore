# PackageManager Signature Probe (Huawei P Smart S)

Android 10 (API 29) üzerinde Google uygulamalarına görünen imza sertifikalarını kontrol eden, **salt okunur** tanılama uygulaması.

- Google Play Hizmetleri / microG-HW (`com.google.android.gms`)
- Companion Store / Play Store (`com.android.vending`)
- GSF, Gmail, ReVanced microG ve `android` sistemi
- `GET_SIGNATURES`, `GET_SIGNING_CERTIFICATES`, `checkSignatures` karşılaştırması
- Huawei `com.huawei.signature.diff.SignatureService` bileşeninin kayıt/erişim testi

## Sınırlar

Bu uygulama **signature spoofing sağlamaz**. Sadece bu uygulamanın kendi sürecine görünen imzaları okur; Gmail farklı bir cevap görüyor olabilir. Huawei servisinin kayıtlı olması Android PackageManager'ın servisi kullandığını kanıtlamaz.

## GitHub Actions ile derleme

Depoda **Actions → Signature Probe APK → Run workflow** seç. Derleme sonunda `signature-probe-apk-and-source` adlı artifact içinde hem `app-debug.apk` hem de kaynak ZIP dosyası bulunur. Root ve Shizuku gerekmez.

## Yerel derleme

Android SDK 35 ve Java 17 ile proje dizininde `gradle :app:assembleDebug` çalıştır.

Lisans: Apache-2.0. Mevcut GmsCore kaynaklarını değiştirmez.
