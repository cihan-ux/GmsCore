# Bootloader Checker TR

Huawei P Smart S (AQM-L21A, Android 10 / EMUI 12) için Türkçe, root gerektirmeyen
ve yalnızca cihaz üzerinde okuma yapan bağımsız Android uygulaması.
Android 10 ve üzerini destekler; gerçek cihaz bilgilerini gösterir.
Model veya hedef EMUI sürümü değerlendirme sonucu yerine kullanılmaz.

Proje `bootloader-checker/` içinden derlenir. GmsCore kaynaklarına, modüllerine ve
mevcut workflow dosyalarına bağımlı değildir. Çalışma dalı
`bootloader-checker-tr`; yeni APK iş akışı `.github/workflows/bootloader-checker-tr.yml`.

## İki ayrı sonuç

**Kilit durumu:** Kilitli / Kilit açık / Bilinmiyor / Çelişkili.
`ro.boot.flash.locked` ve `ro.boot.vbmeta.device_state` doğrudan kilit sinyalleridir.
Doğrudan sinyallerin çatışması çelişkili sonuç üretir. Verified Boot rengi ve
verity modu ayrıca açıklanır; tek başlarına kesin kilit kararı oluşturmazlar.

**Açılabilirlik:** Açılabilirlik desteğine ilişkin kanıt var /
Desteklenmediğine ilişkin kanıt var / Bilinmiyor / Çelişkili.
`ro.oem_unlock_supported` firmware'in bildirdiği destek sinyalidir.
`sys.oem_unlock_allowed` izin bağlamıdır: izin verilmesi gerçek kilit açma
garantisi değildir; izin verilmemesi de kalıcı destek yokluğu anlamına gelmez.
Kilit açık olması, yeniden kilit açılabileceğini kanıtlamaz.

Ham değer, okuma durumu, yorum ve hangi değerlendirmeye katkıda bulunduğu
her özellik için gösterilir. Okunamayan veya eksik değerler uydurulmaz.
Güven düzeyi, yerel yazılım sinyallerinin tutarlılığını ifade eder; donanım
doğrulaması veya üretici onayı değildir.

Huawei 2018'de resmî bootloader kilit açma kodu hizmetini sonlandırdı.
Root olmayan bir uygulama üretici kodu alınabileceğini, üretici yetkisini veya
fastboot'un bir kilit açma isteğini kabul edeceğini doğrulayamaz. AQM-L21A için
üçüncü taraf yöntemlerle kesin kilit açılabilirlik iddiası yoktur.

## v1.1: İsteğe bağlı Fastboot çıktısı karşılaştırması

Cihazdaki salt okunur sistem özelliği kontrolüne ek olarak ayrı bir **manuel
Fastboot metni** alanı bulunur. PC'de daha önce `fastboot oem get-bootinfo`
ile aldığınız çıktıyı (ör. `(bootloader) locked`) yapıştırıp
**Yapıştırılan Çıktıyı Değerlendir** düğmesine basabilirsiniz.

- `(bootloader) locked`, `(bootloader) unlocked`, `Lock State: ...`,
  `FB LockState: ...` ve `unlocked: yes/no` gibi **açık durum satırları**
  değerlendirilir; `FRP LockState`, `FAILED`, `Command not allowed`,
  OEM izin sinyali ve genel serbest metinler kilit kanıtı olarak alınmaz.
- Manuel sonuç **cihazdan okunan sonuçtan ayrı** tutulur. Çatışırlarsa
  uyuşmazlık bildirilir, otomatik kilit durumu değiştirilmez.
- Girdi sınırlıdır (12.000 karakter); ham yapıştırma metni cihaz kimliği
  içerebileceğinden **kopyalanan rapora alınmaz**. Yalnızca sınıflandırma
  ve açıklaması raporda yer alır.
- Metin düzenlendiğinde önceki sınıflandırma geçersizleşir; yeniden
  değerlendirmek gerekir. Yeni otomatik tarama yapıldığında manuel
  karşılaştırma da yeniden onay gerektirir.
- Fastboot çıktısının gerçek cihazdan geldiği doğrulanamaz. Bu özellik
  **bootloader açılabilirliğini** tespit etmez, kilit açma işlemi yapmaz,
  root veya yeni bir sistem izni istemez.
- **Yalnızca kilit durumu içindir:** `get_unlock_ability` sinyali,
  üretici kodu / yetkilendirme veya başarı garantisi olarak kullanılmaz.

## Gizlilik ve güvenlik

- Root, internet veya ayrıcalıklı Android izni istemez; Google servisleri gerekmez.
- Yalnızca sabit isimli sistem özelliklerini salt okunur olarak sorgular.
- Sistem özelliği okuması kısıtlanırsa erişilemediğini veya boş döndüğünü raporlar;
  boş değer ile firmware tarafından gizlenmiş değeri her zaman ayıramaz.
- Shell üzerinden kullanıcı komutu, `su`, kilit açma, fastboot, ayar yazma veya
  yeniden başlatma işlemi çalıştırmaz.
- Verileri otomatik dışarı göndermez. Rapor ancak kullanıcı düğmeye bastığında
  panoya kopyalanır. Android panosu uygulama dışındaki bir sistem özelliğidir.
- Her yeniden kontrol yeni okumalarla değerlendirilir; önceki sonuçlar yeni
  kontrolün kanıtı olarak kullanılmaz.
- Değiştirilmiş firmware yanıltıcı özellik bildirebilir. Bu uygulama bir
  attestation veya bootloader kilit açma aracı değildir.

## Derleme

Gerekenler: JDK 17, Android SDK platform `android-35`, build tools `35.0.0`.
`ANDROID_HOME` veya `local.properties` dosyasındaki `sdk.dir` ile SDK yolunu belirtin.

```sh
cd bootloader-checker
./gradlew --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug
```

Windows'ta `gradlew.bat` kullanın. Çıktı:
`app/build/outputs/apk/debug/app-debug.apk`.

Projeye Gradle 8.9'un resmî Wrapper JAR'ı ve başlatma betikleri eklenmiştir.
Dağıtım indirilirken `gradle-wrapper.properties` içindeki SHA-256 doğrulanır.
Wrapper kaynağı: [Gradle v8.9.0](https://github.com/gradle/gradle/tree/v8.9.0/gradle/wrapper).
[Resmî dağıtım ve Wrapper özetleri](https://github.com/gradle/gradle-distributions/releases/tag/v8.9.0):

- Wrapper JAR: `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`
- Gradle 8.9 bin ZIP: `d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`

## Testler ve doğrulama

Karar motoru ve Türkçe rapor Android arayüzünden bağımsız, saf Java sınıflarıdır.
JUnit 4 testleri kilitli/açık, eksik değer, güçlü kanıt çatışması, Verified Boot'un
tek başına yetersizliği, destek/izin ayrımı, erişim hatası, yenilemede eski verinin
atılması ve Huawei uyarısı senaryolarını kapsar.

Gerçek test, lint ve APK derleme durumu ilgili commit'in GitHub Actions
çalıştırmasından kontrol edilmelidir. Kaynak dosyalarının varlığı veya Wrapper
bütünlüğü, Android derlemesinin başarılı olduğu anlamına gelmez.
**Fiziksel Huawei P Smart S AQM-L21A üzerinde cihaz testi henüz yapılmadı.**

## GitHub Actions ve APK indirme

`Bootloader Checker TR Debug APK` iş akışı yalnızca `bootloader-checker-tr`
dalında proje veya kendi workflow dosyası değiştiğinde otomatik çalışır.
`workflow_dispatch` de tanımlıdır ve job, başka dal seçilirse çalışmaz.
GitHub'ın manuel çalıştırma arayüzünde görünmesi için workflow'un varsayılan dalda
bulunması gerekebilir; bu nedenle ilk derleme dal push'u ile tetiklenir.
Ana dalı değiştirmek gerekmez.

İş akışı JDK 17 ve Android SDK 35 kurar, resmî Wrapper JAR özetini doğrular,
`:app:testDebugUnitTest`, `:app:lintDebug` ve `:app:assembleDebug` görevlerini sırayla
çalıştırır. Başarısız test veya lint sonrasında APK teslim adımı çalışmaz.

Başarılı çalıştırmada **Artifacts → Bootloader-Checker-TR-debug** ZIP'i şunları içerir:

- `Bootloader-Checker-TR-debug.apk`
- `SHA256SUMS.txt`

Test ve lint raporları `Bootloader-Checker-TR-reports` artifact'ına yüklenir.
Artifact'lar 30 gün saklanır; indirmek için GitHub oturumu gerekebilir.
APK'nin SHA-256 özeti ayrıca çalıştırma özetinde bulunur. ZIP'i açtıktan sonra:

```sh
sha256sum --check SHA256SUMS.txt
adb install -r Bootloader-Checker-TR-debug.apk
```

ADB ile kurulum için bilgisayarda Android Platform Tools, telefonda USB hata
ayıklama açık olmalı ve bağlantı telefonda onaylanmalıdır. Bu ayar uygulamanın
çalışması için gerekmez. APK'yı doğrudan telefona aktarıp dosya yöneticisinden de
kurabilirsiniz; gerekirse yalnızca kullandığınız dosya yöneticisine bu kaynaktan
uygulama yükleme izni verin.

APK debug anahtarıyla imzalanır. Farklı CI çalıştırmaları farklı debug imzası
üretebilir; imza çakışırsa eski debug uygulamasını kaldırarak tekrar kurmak gerekir.
Bu bir Play Store veya üretim imzalı dağıtım değildir.

## Cihazda kontrol

- Uçak modunda açın; root veya uygulama izin penceresi çıkmamalı.
- Model, Android, derleme ve erişilebiliyorsa EMUI bilgilerini karşılaştırın.
- İki sonucun ayrı açıklamalarını ve her ham özelliğin okuma durumunu inceleyin.
- Yeniden kontrolde eski değerlendirmenin yeni sonuç gibi görünmediğini doğrulayın.
- Raporu yalnızca düğmeyle kopyalayın; büyük yazı ve TalkBack ile okunabilirliği kontrol edin.

Sistem özelliği erişimi firmware ve SELinux politikasına bağlıdır. “Bilinmiyor”
geçerli bir sonuçtur; Huawei hizmetinin kapanması da cihazın yerel kilit sinyalini
tek başına değiştirmez.
