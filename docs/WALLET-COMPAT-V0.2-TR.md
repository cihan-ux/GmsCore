# microG Wallet uyumluluğu v0.2 — deneysel geliştirme

Hedef: Huawei P Smart S (Android 10 / EMUI 12) üzerinde Play Store 53.5.48
"Yükle -> Bir sorun oluştu" akışındaki Wallet istemci belirteci hatasını
yanıltıcı bir başarı yanıtı üretmeden araştırmak.

## Kod değişiklikleri

- OwServiceImpl.getClientToken() gerçek Google Wallet token sağlayıcısı içermiyor.
  Mevcut Status.INTERNAL_ERROR yanıtı korunuyor. Yalnızca isteğin geldiğini
  bildiriyor; token, seçenek, hesap, ödeme verisi ya da istisna metni loglamıyor.
- isReadyToPay() isteği ve JSON içeriği loglanmıyor. Eski
  Status.SUCCESS / false yanıtı korunuyor.
- Tanınmayan Binder işlemi yalnızca işlem sayısıyla loglanıyor;
  Parcel içeriği yazılmıyor.
- wallet_1p_initialize_buyflow özelliğinin destekleniyormuş gibi
  ilan edilmesi kaldırılıyor. Diğer Wallet özelliklerinin eksiksiz
  çalıştığı anlamına gelmez.

## Çevrimdışı test

Android SDK gerektirmez:

    python3 -m unittest discover -s tools -p 'test_wallet_compat.py' -v

Eski Play Store log dosyasının gizli verileri yansıtmayan özeti:

    python3 tools/wallet_trace_report.py --from-file ~/playstore-53-install.log

Özetleyici yalnızca sabit kategori sayıları ve gözlemlenen zamanları basar.
Ham kayıt, OAuth token, e-posta, cihaz kimliği ve seri numarası basmaz,
telefon üzerinde işlem yapmaz. Ham log dosyasını GitHub'a yüklemeyin.

## Kritik sınırlar

- Bu değişiklik Google Play Store uygulama indirmeyi sağlamaz.
  Geçerli Google Wallet istemci token'ı ve sunucu edinme akışı uygulanmadı.
- Bu repo Huawei hmsHuawei hedefinde com.google.android.gms üretir.
  Morphe/ReVanced app.revanced.android.gms farklı paket ve imzaya sahiptir.
- Signature spoofing / manifest tek başına MANAGE_USERS, INTERACT_ACROSS_USERS
  veya AccountManager özel kullanıcı verisi yetkisi vermez.
- Mevcut Morphe microG'yi, Google hesaplarını ve Play Store verisini
  silmeyin. İmza karşılaştırması olmadan APK kurmayın.
- Kaynak/test doğrulaması Android derlemesi ve telefon entegrasyon testi değildir.

Sonraki gerçek iş: meşru token kaynağını, veri protokolünü ve istemci
yetkilendirmesini doğrulamak. Bu mümkün değilse sahte SUCCESS dönülmemeli.
