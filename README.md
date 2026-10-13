# LodosKentWatch • DSMART A8U • v0.1.0 BLE

Daniel Klein **DSMART A8U** (MAC `0C:AF:6F:E2:C4:46`) için kendi yerel Bluetooth istemcimiz.

**Bu sürüm HCI kayıt okuyucusu DEĞİLDİR.** Saatin BLE reklamını tarar ve doğrudan GATT bağlantısı kurar. Nordic UART `6e400001` ve AE00 servislerini keşfeder, NUS TX / AE02 bildirimlerine abone olur ve gelen HEX paketleri görüntüler. Kullanıcı düğmesiyle yalnızca araştırma amaçlı `POWER READ` sorgusu gönderebilir. Veriler TXT olarak dışarı aktarılabilir.

**Önemli teknik sınır:** SMART-TIME PRO APK'sinin `classes3.dex` dosyasındaki Smable paket biçimi ve CRC yeniden uygulanmıştır. Bu SDK çok saat modelini destekler; A8U için henüz canlı cihaz yanıtı görülmemiştir. Pil yüzdesi, uyku, nabız, adım, kadran, bildirim gönderme, saat bulma ve arama gibi işlevlerin çalıştığı **iddia edilmez**. Bu özellikler cihaz yanıtı/protokol kesinleştirildikçe eklenir.

## Kurulum ve kullanım
- Android 8+ gerektirir. İnternet, hesap, reklam SDK'sı gerekmez.
- **SMART-TIME PRO veya nRF Connect saati kullanıyorsa bağlantısını kesin.** BLE bağlantısı eşzamanlı kullanılamayabilir.
- Aç → "Bluetooth ile saati bul ve bağlan" → izinleri ver → bağlantı/servis sonuçlarını gör.
- Bildirimler ve pil sorgusu isteğe bağlıdır, firmware / DFU / sıfırlama gönderilmez.
- Cihaz adı sabit: DSMART A8U. Diğer cihazlara rastgele komut gönderilmez.

## GitHub Actions
Actions → "LodosKentWatch Android APK" → Artifacts → "LodosKentWatch-A8U-APK" → `app-debug.apk`.
Android'in "Waiting for Debugger" uyarısı cihazın Geliştirici seçeneklerindeki "Hata ayıklayıcıyı bekle" ayarına da bağlıdır. Bu projede debug variant `debuggable false` olarak ayarlandı ve paket ID önceki analizörden farklıdır.

## Kaynak kanıtları
- Kullanıcının saat ekranı: `DSMART A8U`, MAC `0C:AF:6F:E2:C4:46`, sürüm `V0.0.4`.
- Saat nRF GATT: Nordic UART RX:6e400002 TX:6e400003 ve AE01/AE02.
- SMART-TIME PRO 2.1.3.4 `base.apk` — `MessageFactory.create()`, Smable POWER=0x0203 READ=0x10, CRC-16/ARC başlangıç=0.
