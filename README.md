# Lodos Saat HCI Analizörü 0.1.0

SMART-TIME PRO ve DSMART A8U saatinin Bluetooth trafiğini **Android'in kendi HCI log dosyası üzerinden** inceleyen bağımsız, reklamsız, çevrimdışı Android uygulaması.

**Gerçek zamanlı gizli dinleme YAPMAZ.** Normal Android APK başka uygulamanın BLE oturumunu okuyamaz. Saat SMART-TIME PRO'da bağlı kalabilir.

## Kullanım
1. Android Geliştirici seçeneklerinde Bluetooth HCI izleme günlüğünü aç (gerekiyorsa Bluetooth'u yeniden başlat).
2. Orijinal saat uygulaması üzerinden birkaç güvenli işlem yap ve Android hata raporu al.
3. Analizörde btsnoop_hci.log, gzip veya HCI içeren hata raporu ZIP dosyasını seç.
4. MAC 0C:AF:6F:E2:C4:46 ile eşleşen ATT WRITE/NOTIFY/READ paketlerini incele; raporu TXT olarak kaydet.

Root, BLE bağlantısı, Internet ve saat komutu gönderme izni gerekmez. Hedef MAC bağlantısı kayıtta yoksa sonuç otomatik çıkarılamaz; ek tanı seçeneği diğer BLE paketlerini gösterebilir ve mahrem veriler içerebilir.

## Derleme
GitHub Actions > Lodos Saat HCI APK > başarılı iş > Artifacts > LodosSaat-HCI-APK > app-debug.apk

Bu bir **tanı/analiz** uygulamasıdır; tam saat yönetimi ya da tüm komutların çözümü değildir.