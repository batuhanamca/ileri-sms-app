# İleri SMS

[www.ilerisms.com](https://www.ilerisms.com) için Android uygulaması.

## Özellikler
- Siteyi tam ekran, uygulama içinde açar (`https://www.ilerisms.com`)
- Çekerek yenileme ve üstte yükleme çubuğu
- İnternet yokken "Tekrar dene" ekranı
- Koyu/açık tema desteği, adaptif ikon
- `tel:`, `mailto:`, `sms:`, WhatsApp ve harici bağlantılar ilgili uygulamada açılır
- Dosya yükleme ve dosya indirme desteği
- Çıkmak için çift geri tuşu, ilerisms.com bağlantılarını doğrudan uygulamada açma
- Sadece HTTPS; çerezler ve oturum kalıcı

## Derleme
Android Studio ile açıp çalıştırın, ya da:

    gradle :app:assembleDebug

Her push'ta GitHub Actions debug APK üretir (Actions → artifact).
Site adresini değiştirmek için `app/build.gradle` içindeki `SITE_URL` / `SITE_HOST` değerlerini düzenleyin.
