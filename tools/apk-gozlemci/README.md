# Huawei APK Gözlemci

Huawei P Smart S (Android 10 / EMUI 12) için klasörde yeni APK dosyası görünce bildirim çıkaran yardımcı uygulama.

- Orijinal Huawei com.android.packageinstaller APK'sini değiştirmez veya yeniden imzalamaz.
- Kullanıcının seçtiği bir klasörün en üst seviyesini yaklaşık 5 saniyede bir tarar.
- İndirme tamamlanmamışsa dosyanın durumunun sabitlenmesini bekler.
- APK geldiğinde bildirim gösterir; bildirime basınca sistem yükleyici ekranını açar.
- Kurulum için kullanıcı onayı ve bilinmeyen kaynak izni gerekir; sessiz kurulum sağlamaz.
- İmza korumasını veya Android'in güvenlik izinlerini atlatmaz.

## APK edinme

GitHub > Actions > APK Gozlemci Build > son başarılı çalıştırma > Artifacts > huawei-apk-gozlemci-debug.

Bu bir debug derlemesidir ve kendi geliştirme anahtarıyla imzalanır; Huawei'nin orijinal imzası değildir.

## Derleme

GitHub Actions Ubuntu + Java 17 + Gradle 8.9 + AGP 8.7.3 ile Android SDK 35 kullanarak tools/apk-gozlemci dizininde assembleDebug çalıştırır.

## Kullanım

APK Gözlemci'yi kur, klasör seç ve izlemeyi başlat. Yeni APK bulunduğunda bildirime bas. Android 10'daki paket yükleyici kurulum onayını isteyecektir. Mevcut eski APK dosyaları ilk taramada atlanır. EMUI'nin pil ve arka plan kısıtlamaları izlemeyi durdurabilir.

Güvenmediğin APK'leri yükleme. Orijinal Package Installer dosyasının kendisi projede dağıtılmaz.
