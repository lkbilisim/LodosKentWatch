# LodosKentWatch — Teknik Şartname ve Doğrulama Kriterleri

Durum: Kullanıcının 13 Ekim 2026 tarihinde verdiği altı maddelik proje talebi. Bu dosya projenin kalıcı kaynak-kod şartnamesidir. **Tamamlandı** anlamına gelmez.

## 1. Hedef donanım ve araştırma
- Daniel Klein A8 Ultra / Bluetooth reklam adı `DSMART A8U`.
- Modelin donanımı, BLE ve varsa klasik Bluetooth ses profilleri, üretici protokolü, özellikleri ve sınırlamaları doğrulanacak.
- Servis keşfi (13.10.2026 gerçek cihaz günlükleri):
  - Generic Access `00001800-0000-1000-8000-00805f9b34fb`; `2A00` props=10.
  - UART `6e400001-b5a3-f393-e0a9-e50e24dcca9e`; TX `6e400002-...` props=12, RX `6e400003-...` props=16.
  - Özel `0000ae00-0000-1000-8000-00805f9b34fb`; `AE01` props=4, `AE02` props=16.
  - Android bond zaten var; GATT bağlandı; UART ve AE02 CCCD bildirim etkinleştirmesi başarılı. **Bunlar üretici protokolü veya sağlık verisi çözümlemesini kanıtlamaz.**

## 2. SMART-TIME PRO davranış analizi
- Kullanıcının sağladığı referans uygulama/APK, önceki LodosKentWatch APK ve ekran görüntüleri analiz edilerek davranış envanteri çıkarılacak.
- Yalnızca ekran kopyası değil; eşleştirme, bağlantı, oturum başlatma, saat senkronizasyonu, veri aktarımı, bildirimler, komut/yanıt, hata ve kopma davranışları incelenecek.
- Kapalı protokol için belirsizlikler ve test kanıtları ayrı ayrı kaydedilecek. Üçüncü taraf kaynak kodu, logoları, reklamları, hesap/URL altyapısı ve özel varlıkları aynen kopyalanmayacak.

## 3. Bağlantı güvenilirliği
- Bağlı GATT oturumunu tarama, tekrar bond sorgusu veya UI eylemi yüzünden gereksiz yere kapatma.
- Foreground servis ve Android sürümüne uygun izinler; kontrollü yeniden bağlanma/backoff, tek GATT oturumu, GATT işlem kuyruğu, CCCD sıraya alma, oturum sağlık kontrolü, bond ve çağrı-ses profillerinin ayrımı.
- Telefon kilidi, uygulama arka planı, Bluetooth kapat/aç, menzil dışı/geri dönüş, saat kapat/aç, telefon yeniden başlatma ve manuel durdurma testleri.
- Her zaman sıfır kopma garantisi verilmez; kullanıcı müdahalesi gerektirmeyen geri kazanım hedeflenir.

## 4. Gerçek işlevler
- Sağlık: adım, mesafe, kalori, nabız, uyku, SpO2 ve **donanımın gerçekten desteklediği** diğer ölçümler.
- Spor: etkinlikler, geçmiş ve senkronizasyon.
- Bildirimler, arama/çağrı (gerekirse ayrı klasik Bluetooth bağlantısı), kişi senkronizasyonu, kamera uzaktan kontrol, alarmlar, dünya saati, tarih/saat, ekran/kadran, titreşim, hatırlatıcılar ve doğrulanan diğer ayarlar.
- SMART-TIME PRO'ya ait hesaplar, URL'ler, reklamlar veya uygulamaya özel gereksiz modüller dahil edilmeyecek; mümkün olduğunca yerel ve gizlilik odaklı çalışma.

## 5. Doğruluk ve teslim koşulları
- Görsel gösterim ile çalışan özellik birbirine karıştırılmayacak. Uydurma nabız, pil, adım veya bağlantı durumu gösterilmeyecek.
- Her özellik için durum: `doğrulandı / kısmen çalışıyor / araştırılıyor / desteklenmiyor`; kanıt: ham paket, test sonucu ve cihaz koşulları.
- GATT HAZIR, 'üretici protokolü doğrulandı' değildir. Sadece derlenmiş APK, cihazda uçtan uca test edildi anlamına gelmez.
- Eksik/kapalı üretici işlevleri için 'tamamlandı' denmeyecek; çözülmesi teknik olarak mümkün olmayabilecek özellikler açıkça belirtilecek.
- APK yalnızca gerçek GitHub Actions başarılı derlemesinden teslim edilecek; doğrudan indirilebilir APK bağlantısı tercih edilecek; olmayan dosya bağlantısı uydurulmayacak.

## 6. Tasarım ve simgeler
- Öncelik işlev, stabilite, erişilebilirlik ve gerçek veriler. 'Görsel şölen', sahte ekran, placeholder işlev yok.
- Uygulama simgesi gerçek kullanım amacına uygun olacak; kullanıcı görsel sağladıysa uygun haklarla o görsel kullanılacak. Rastgele ikon veya uydurma simge kullanılmayacak.

## Öncelikli araştırma ve geliştirme sırası
1. Referans APK ve eski APK'nın manifest, servis, izin, BLE çağrı sırası, protokol izlerini analiz et.
2. Gerçek saatle zararsız/pasif BLE bildirim yakalama ve karakteristik haritası; gerekirse kullanıcının rızasıyla Android Bluetooth HCI snoop kaydı.
3. Üretici handshake/oturum çerçeveleme, komut/yanıt ve ölçüm çözümleyicisini gerçek paketlerle doğrula.
4. Android GATT yaşam döngüsü, reconnect, arka plan güvenilirliği, ses profili eşleştirmesi için tekrarlanabilir testler.
5. Her özellik için uçtan uca uygulama + cihaz testi, rapor ve GitHub APK sürümü.

Referans repo: https://github.com/lkbilisim/LodosKentWatch
