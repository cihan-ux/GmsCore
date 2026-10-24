# DURUM: AÇILIŞTA ÇÖKME DOĞRULANDI (2026-10-24)

**Bu deneysel Gmail microG-HW Bridge modülünü çalışan bir çözüm olarak KULLANMAYIN.**
LSPatch ile yeniden imzalanmış Gmail'de gerçek cihaz (HUAWEI AQM-LX1,
Android 10/EMUI 12) günlüklerinde en az iki çökme gözlendi:

- `java.lang.SecurityException: Access denied, missing google package permission for OWNER`
- `java.lang.NullPointerException: Context.isRestricted()` from Android `FontsContract.fetchFonts`.

**Kök neden / kuvvetli açıklama:** microG, `OWNER` gibi özel Google
yetkilerini *çağıran uygulamanın sistemde kayıtlı gerçek imzasına* göre
`ExtendedPackageInfo.hasGooglePackagePermission` ile sınar. LSPatch,
Gmail APK'sını yeniden imzaladığı için özgün Google sertifikası kaybolur.
Bu modül yalnızca Gmail'in görebildiği GMS imza verisini değiştirir;
microG'nin Gmail imzasına yönelik ayrı kontrolünü değiştirmez.

İlgili upstream dosyalar:
- https://github.com/microg/GmsCore/blob/master/play-services-base/core/src/main/java/org/microg/gms/common/PackageUtils.java
- https://github.com/microg/GmsCore/blob/master/play-services-base/core/src/main/kotlin/org/microg/gms/common/KnownGooglePackages.kt
- https://github.com/microg/GmsCore/blob/master/play-services-base/core/src/main/kotlin/org/microg/gms/utils/ExtendedPackageInfo.kt

**Sonuç:** APK'nın GitHub Actions'ta başarıyla derlenmesi çalışma zamanı uyumluluğu
anlamına gelmez. Gmail verilerini/hesapları yedeklemeden kaldırma veya temizleme
yapmayın. Deneysel yamayı Gmail için çözüm gibi önermeyin.

---

# Gmail microG-HW Bridge — Deneysel modül

Bu proje **Gmail APK'sı değildir**. LSPatch ile yalnızca Gmail'in işlemine yüklenmek üzere tasarlanmış klasik Xposed API modülüdür.

- Test hedefi: HUAWEI AQM-LX1, EMUI 12, Android 10 / API 29.
- microG-HW GMS: com.google.android.gms / 0.3.17.252432-hw.
- microG Companion: com.android.vending / 0.3.17.40226-hw.
- Güvenlik: Yalnızca NOGAPPS SHA-256 sertifikası (9BD06727...) doğrulanan -hw paketlerinin Gmail tarafından görülen imzalarını değiştirir.
- Etki: Gmail içindeki getPackageInfo/getPackageInfoAsUser/hasSigningCertificate çağrıları. Diğer uygulamalara ve sistem PackageManager'a etkisi yoktur.
- Sınırlar: Google hesap yetkilendirmesi, push/senkronizasyon, Google'ın başka bütünlük doğrulamaları ve Gmail'in kendi imzası yine sorun yaratabilir. Başarılı çalışacağı henüz test edilmedi.
- Android 10 SigningInfo üretimi gizli API'ye bağlıdır ve EMUI üzerinde engellenebilir; log uyarısı çıkar.

## Kullanım
1. GitHub Actions derlemesindeki `gmail-microg-hw-module` artefaktından `app-debug.apk` indirin ve modülü kurun.
2. LSPatch v1.2'de Gmail'i (orijinal APK/split seti) patch'leyin. Gmail kurulu değilse önce orijinal Gmail'i kurmanız gerekebilir. LSPatch Manager modu ve **yalnızca bu modülü** seçin.
3. Gmail'in orijinal APK'sı Google tarafından imzalanmıştır. LSPatch değiştirilen Gmail APK'sını yeniden imzalar; bu nedenle mevcut orijinal Gmail üzerine direkt güncelleme kurulamaz. Oturum verisini yedeklemeden uygulamayı kaldırmayın.
4. Gmail'i açıp Android loglarından `GmailMicroGHW` satırlarını inceleyin. İmza dönüşümü görüldüğü halde hata devam ederse yeni log ile değerlendirin.
5. Gmail'in otomatik güncellemelerini kapatın; uygulama güncellenirse LSPatch'i yeniden uygulamak gerekir.

### Notlar
- APK'ya Google hesabı şifresi girmeyin; modülün kendi ekranı ve ağ bağlantısı yok.
- **Bu, orijinal Gmail imzasını koruyan bir yama değildir.** Gmail APK'sını modifiye etmek imzasını zorunlu olarak değiştirir.
- Eğer modül yalnızca imza doğrulamasını aşar, fakat Gmail sunucu/hesap tarafında başarısız olursa sistemde daha fazla değişiklik yapmayın.
- Proje, Xposed API sınıflarını yalnızca derleme sırasında kullanılan yerel `xposed-stubs` modülünden alır; bu sınıflar uygulamanın içine paketlenmez. Çalışma zamanı LSPatch sağlar.
