# GmsCore HW Assistant v1.0

Android 10 / EMUI 12 için **rootsuz, çevrimdışı, salt okunur** GMS tanılama uygulaması.

**Paket:** `dev.cihan.gmshwassistant` (Morphe, Play Store ve GmsCore'dan ayrı kurulur).

## İşlevler

- Cihaz modeli, Android/EMUI sürümü ve belirlenmiş GMS paketlerinin sürümleri
- Yüklü paketlerin SHA-256 APK imza sertifikaları; bilinen Morphe ve Wallet A debug referansları
- Play Store'un `INTERACT_ACROSS_USERS`, `MANAGE_USERS` ve `CREATE_USERS` izinlerinin **verilmiş olup olmadığı**
- Huawei `com.huawei.signature.diff` servisinin görünürlük kontrolü (gerçek imza taklidini doğrulamaz)
- Google / ReVanced **hesap doğrulayıcı türlerinin** kaydı (hiçbir hesap adı veya e-posta okunmaz)
- Kişisel veriler içermemesi amaçlanan yerel metin raporu ve Android paylaşım menüsü

Uygulama **root, ADB, internet, depolama veya hesap erişimi izni istemez**. Telefon ayarlarını, APK'ları ve hesapları değiştirmez. Standart Android uygulamaları başka süreçlerin crash/logcat verilerini okuyamaz: Play Store çökme analizi için mevcut `tools/huawei_playstore_errors.py` bilgisayarda ADB ile çalıştırılmalıdır.

## Önemli sınırlar

- Android 10'da hesap doğrulayıcı türünün görünmesi, Play Store'un hesabı okuyabildiğini veya Google hesabının mevcut olduğunu **göstermez**.
- Paket / servis varlığı veya APK imzası tek başına Huawei signature spoofing desteğini kanıtlamaz.
- `INTERACT_ACROSS_USERS` veya `MANAGE_USERS` gibi korumalı izinleri başka uygulamalara veremez.
- APK'ya dokunmaz; hiçbir uygulamayı kaldırmayın, hesabı silmeyin.
- Aygıtta ilgili bilgi Android güvenlik kısıtı nedeniyle görünmüyorsa raporda belirsizlik korunur.
- Debug APK, GitHub Actions koşularında farklı imzalanabilir. Bu **yalnızca yardımcı uygulama** için geçerlidir; orijinal GmsCore ile paket adını paylaşmaz.

## Yerel geliştirme

Java 17, Android SDK platform 35, Gradle 8.9:

```sh
gradle -p hw-assistant :app:testDebugUnitTest :app:assembleDebug --no-daemon
```

Çıktı: `hw-assistant/app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions: **GmsCore HW Assistant - APK** iş akışındaki **Artifacts** bölümünden doğrulanmış debug APK'yı indirin.

## Güvenli tasarım

Uygulama ham log, hesap adı, e-posta, token, Android ID veya cihaz seri numarası toplamaz. Rapor metnini cihazda oluşturur ve sadece kullanıcı paylaşım menüsünü açarsa seçilen hedefe verir.
