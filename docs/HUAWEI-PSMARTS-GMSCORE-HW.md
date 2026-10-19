# Huawei P Smart S (AQM-L21A / AQM-LX1) — GmsCore-hw

## Hedef ve sınırlar

- EMUI 12.0.0.226 / Android 10, **root ve downgrade yok**.
- Paketleri **kaldırmadan**, mevcut hesapları/uygulama verisini **silmeden** tanılama.
- hmsHuawei uygulama varyantının `versionName` son eki `-hw`'dir.
- hmsHuawei paket kimliği **com.google.android.gms**. ReVanced microG
  (`app.revanced.android.gms`) ile aynı paket değildir.
- microG paketinin kurulması, gerçek Google Play Store'un çalışmasını veya
  Android imza taklit yetkisinin mevcut olmasını **garanti etmez**.

## GitHub Actions dosyaları

`.github/workflows/p-smart-s-build.yml` iki ayrı paket hazırlar:

1. **PSmartS-GmsCore-hw-SIGNED-DEBUG**: test için **imzalı debug**
   APK ve `SHA256SUMS.txt`; CI, paket kimliğini, `-hw` varyantını ve
   APK imzasını doğrular.
2. **PSmartS-GmsCore-hw-UNSIGNED-RELEASE**: **imzasız release**
   APK ve `SHA256SUMS.txt`; bu sürüm telefona **doğrudan kurulamaz**.

Debug APK her Actions çalıştırmasında farklı otomatik debug anahtarı ile
imzalanabilir. **Başka APK üstüne güncelleme olarak yüklemeyin.**
Aynı paket adındaki mevcut bir uygulamaya farklı imza ile güncelleme
yapılamaz. Paket silinmesi hesap/uygulama verilerini kaybettirebilir:
sorunu çözmek için silme ya da `adb uninstall` önermiyoruz.

Gerçek dağıtım imzası için aynı özel imza anahtarının güvenli şekilde
saklanması gerekir. GitHub depoya özel anahtar eklemeyin. Bu workflow
kalıcı üretim imzası üretmez.

## Önce telefondaki paketleri kontrol edin (Linux Mint)

Linux Mint terminali ve Android Platform Tools:

~~~sh
python3 tools/huawei_gms_diagnostics.py
~~~

Windows'ta Python yüklüyse:

~~~powershell
py -3 tools/huawei_gms_diagnostics.py
~~~

Telefon açıkken USB hata ayıklama iznini onaylayın. Bu kod yalnızca
sabit sistem özelliklerini ve yüklü olduğu bilinen paketlerin sürüm
bilgilerini **okur**. Google hesaplarının e-posta adresini, Android ID'yi,
seri numarasını, logcat'i veya kimlik doğrulama tokenlarını toplamaz.
Verileri internete göndermez. İsterseniz yerel metin dosyası:

~~~sh
python3 tools/huawei_gms_diagnostics.py --output gmscore-diagnostic.txt
~~~

Dosyayı göndermeden önce inceleyin. Bu script uygulama/hesapları silmez,
root kullanmaz, sertifika veya Android güvenlik yetkilerini değiştirmez.
Bağlı cihaz için cihaz izinleri gerektiğinden **ADB üzerinden** çalışır.

Testler (cihaz gerekmez):

~~~sh
python3 -m unittest discover -s tools -p 'test_huawei_gms_diagnostics.py'
~~~

## Play Store hatasının yorumu

Önceki Huawei loglarında `app.revanced` hesap tipine erişim reddi ve
`MANAGE_USERS`/`CREATE_USERS` izin hataları görüldü. Android 10,
farklı UID'ler ve hesap türleri arasındaki erişimi kısıtlayabilir;
bu değerleri yalnızca manifest'e eklemek ayrıcalık kazandırmaz.

- `SecurityException: uid ... cannot get user data for accounts of type: app.revanced`
  hatası, Google hesabının belirli bir uygulama tarafından erişilememesi
  ile ilgilidir. Bu, tek başına bozuk hesabı veya yanlış parolayı kanıtlamaz.
- `MANAGE_USERS or CREATE_USERS permission` hatası, uygulamanın
  Android'in korumalı sistem işlemini gerçekleştiremediğini gösterir.
  Root'suz, imzasız bir APK ile ayrıcalık otomatik kazanılmaz.
- Yeni `com.google.android.gms` paketi, mevcut
  `app.revanced.android.gms` kurulumu ve Play Store paketleri ile farklı
  etkileşebilir. **Telefonun paket durumu incelenmeden yeni APK kurmayın.**

Öncelik sırası: (1) tanılama raporu, (2) paket/imza çakışması analizi,
(3) kontrollü test planı. Bu depo şu anda gerçek Google Play Store'un
işlevsel olacağını ispatlamaz.
