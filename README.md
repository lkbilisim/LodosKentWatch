# LodosKentWatch 2.0.0-alpha2

Daniel Klein / DSMART A8U için **deneysel** Android BLE bağlantı uygulaması.

## Durum

Bu proje, SMART-TIME PRO'nun birebir kopyası değildir. Orijinal uygulamanın üreticiye özgü veri protokolü saat üzerinde henüz doğrulanmadı. Özellikle **adım, uyku, SpO2, tansiyon, çağrılar, bildirimler ve kadran aktarımı henüz yoktur**. Yazılım; BLE bağlantısı, kopma-sonrası yeniden deneme, servis keşfi, uygun cihazlarda standart pil/nabız okuma ve tanılama için hazırlanmıştır. Bağlantı kararlılığı gerçek DSMART A8U ile ölçülmeden garanti edilemez.

### Arayüz ve eşleştirme (alpha2)
- Sağlık, Spor, Cihaz, Yerel Özet ve Ben sekmeleri geri eklendi. Bu, eski APK'nın görselinin birebir kopyası değil, yeniden tasarlanmış bir önizlemedir.
- Saat seçildiğinde iki farklı seçenek var: **Telefonla eşleştir** (`BluetoothDevice.createBond`) ve **Yalnız BLE bağlan** (`connectGatt`).
- Android sistem onay penceresi cihazın daha önce eşleştirilmiş olmasına, Bluetooth türüne ve saat yazılımına bağlıdır; her durumda açılacağı garanti edilmez.
- Eşleştirme, GATT ve üretici protokolü **ayrı durumlar** olarak gösterilir.
- Tanılama raporuna GATT karakteristik UUID ve property değerleri eklendi.

### Teknik kapsam

- Android 8+; Android 12+ Bluetooth runtime izinleri
- Android 14+ connectedDevice foreground service
- Yakındaki DSMART/A8U cihazlarını tara ve seç; kayıtlı saati hatırla
- GATT bağlantı/servis keşfi zaman aşımı, artan yeniden deneme aralığı
- GATT işlemleri sıraya alınır; eski oturum callback'leri elenir
- BLE Battery Service 0x2A19 ve Heart Rate Measurement 0x2A37, saat bunları sunuyorsa
- Nordic UART bildirimleri yalnız tanılama amacıyla ham HEX olarak kaydedilir
- Reklam, analitik, ağ izni ve internet sunucusu yok

### GitHub'da APK üretimi

1. **Actions > Android APK** bölümüne git.
2. **Run workflow** düğmesine bas (veya main'e push yap).
3. Yeşil başarılı işlem altında **Artifacts > LodosKentWatch-debug-apk** ZIP'ini indir.
4. ZIP içindeki **app-debug.apk** dosyasını Android telefonuna kur.

Debug APK test içindir, Play Store release imzası içermez.

### İlk saat testi

1. SMART-TIME PRO uygulamasından saat bağlantısını kaldır ve uygulamayı tamamen kapat.
2. Android Bluetooth ve uygulama izinlerini aç.
3. LodosKentWatch > **Bağlantı servisini başlat** > **Saatleri tara** > **DSMART A8U** seç.
4. GATT HAZIR görünmesini doğrula; bu ifade üretici veri protokolünün çalıştığı anlamına **gelmez**.
5. Telefon kilitliyken ve saat menzil dışına çıkıp geri döndüğünde davranışı gözlemle.
6. **Tanılama kaydını paylaş** ile UUID ve hata kayıtlarını geliştiriciyle paylaş. Paylaşmadan önce kişisel bilgi içerip içermediğini incele.

### Araştırma notu

Orijinal uygulamanın APK paketinde çoklu BLE protokol altyapısı var. Eski LodoskentWatch 1.0.3, Nordic UART türü UUID'leri kullanıyor; bu UUID'lerin saat üzerinde gerçek veri protokolü olduğunu henüz ispatlamadık. Varsayıma dayalı sağlık ölçümü göstermiyoruz.
