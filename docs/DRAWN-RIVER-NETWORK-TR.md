# Çizimden nehir ağı — geliştirme durumu

## Kullanım

Tools → Nehir → Çizim fırçası → Nehir çizim fırçasını aç.
River Path üzerinde ana hattı ve ona değen kolları çiz. Aynı ekranda Ağı önizle'yi aç.
Gri çizimdir, mavi gerçek doğrulanmış yataktır. Uygula'ya kadar dünya değişmez.
Kısmi sonuçta yalnız mavi parçalar uygulanır; retler ve bağımlı üst kollar listelenir.

## Bu değişiklikte

- Kalın çizimleri bağlantıları koruyarak inceltme; bütün bağlı grupları ele alma.
- Tek alıcılı ağaç grafiği; ortak gövdeyi bir kez planlama. Döngüler sessizce kesilmez.
- Mevcut suya bağlı tek uç varsa çıkış olarak kullanma. Birden fazla su ucu belirsiz kabul edilir.
  Su bağlantısı olmayan çizimde en düşük uç bir **öneridir**, doğrulanmış deniz bağlantısı iddiası değildir.
- Kaynak katkısıyla genişlik: kaynak genişliği × katkının karekökü; stil üst sınırı korunur.
  Bu katkı çizilen kaynak sayısıdır, DEM havza alanı veya fiziksel debi değildir.
- Yan kolun son bölümünde yaklaşık dört yerel genişlik boyunca ana kol genişliğine geçiş.
- Ortak birleşim su kotu; nihai ıslak alan birleşimi üzerinde mevcut kesit/kazı/taşma kontrolleri.
- Başarısız kesitte sekiz sabit yerel alternatif; çizgiye en fazla dört blok öteleme.
  Birleşim ve çıkış uçları sabit. Yaprak kaynak için öteleme önizlemede önerilir.
- Dirt düzeltmesi zorunlu; birleşim çevresinde ek alçaltma kapalı.
- 120 saniye ortak planlama bütçesi; en fazla 100.000 çizim hücresi ve 250.000 önbellek arazi hücresi.
  Hücre sınırı gerçek JVM heap ölçümü değildir. Sınırda çözünürlük düşürülmez.
- Önizleme salt okunur; revizyon kontrolü; tek Undo; uygulama sırasında iptal/hata durumunda carver geri yüklemesi.
- Adlandırılmış eski script presetleri yeni önizlemeye yönlenir. Script işlemi bitmeden pencere başlamaz.
  Kaynak/üst genişlik, derinlik, smooth, granit seçimi ve seed aktarılır.
  Eski otomatik yan kol, uzak waypoint birleştirme ve granit oran ayarlarının kullanılmadığı günlükte açıkça yazılır.

## Kalan kapsam / kabul kapısı

- Kısa kopuk uçları kullanıcı kontrollü yaklaştırma, çıkış seçme ve tek tek kol kapatma arayüzü henüz yok.
- Önizleme şu an çizim/kanal şemasıdır; topoğrafik arka plan, oklar ve kazı renk haritası sonraki iştir.
- DEM katkı alanı, daha gelişmiş vadiye oturtma ve eğri düzenleme henüz yok.
- Bütçe/önbellek sınırı aşılırsa bu oturumun uygulanması kapalıdır; önceki tamamlanan kolları kurtarma henüz yok.
- Preset 0 eski script davranışıdır; yeni ağ için adlandırılmış preset veya Tools ekranı kullanılmalı.
- Otomatik test ve export verisi, gerçek Minecraft su güncellemeleri kabulünün yerine geçmez.
- Kullanıcının dağlık çiziminin bulunduğu gerçek `.world` dosyası üzerinde kabul henüz yapılmadı.

## 2026-09-14 doğrulama ve paket

- Java 21 ile WPGUI ve bağımlı modüllerin tam test koşusu başarılı; 3 mevcut test atlandı.
- Ardından eklenen Y ağı export testi dahil `DrawnRiver*Test` koşusu başarılı.
- 53 JavaScript preset regresyon testi başarılı.
- Paket: `dist/drawn-network-test-20260914/WorldPainter V2 Drawn Test/WorldPainter V2 Drawn Test.exe`.
- Ayrı profil: `%APPDATA%/WorldPainter [V2-DRAWN-TEST]`; aktif V2 profili kullanılmaz.
- İlk ayrı paket masaüstündeki aktif kuruluma geçirilmediği için kullanıcı eski scriptin 558. satırındaki hatayı almaya devam ediyordu.
- Aktif JAR ve tüm V2 profili (fırçalar ve autosave dosyaları dahil), yapılandırma ve masaüstü kısayolu
  `dist/backup-before-drawn-network-20260914-1140` altına yedeklendi.
- Gerçek arayüz kontrolünde Maven shaded JAR'da çekirdek plugin kaydının eksik olduğu da bulundu.
  Maven paketlemesi dört temel plugin kaydını birleştirecek şekilde düzeltildi;
  `WorldPainter/tools/PackagedPluginCheck.java` yalnız paketlenmiş JAR üzerinden plugin ve Caves katmanı yüklenmesini doğruladı.
- Java 21 paketleme ve paket kontrolü başarılı. Önceki test koşularından sonra yalnız paketleme değiştirildi;
  tam test koşusu bu paketleme değişikliği sonrasında tekrar edilmedi.
- Masaüstü kısayolunun kullandığı `dist/WorldPainter v2/app/WorldPainter-v2.jar` ve ayrı test paketi güncellendi.
  Aktif `rivers_river_from_line.js` önbelleği de yeni ağ önizlemesine yönlenen sürümle değiştirildi.
- Aktif ve ayrı paket JAR SHA-256 aynı:
  `5070C6263349EB2F8840386864661C44AE345011E88F1DCD693D779961CBD4CC`.
- Güncellenen aktif uygulamada boş 640×640 düz test haritası hatasız açıldı.
  Pencil ile Y çizimi yapıldı: 2 kaynak, 1 birleşim, 3 doğrulanmış parça, 0 ret.
  Önizleme ve Uygula akışı başarılı; tek Ctrl+Z ile nehir uygulaması geri alındı ve çizim kaldı.
  Ctrl+Y ile ağ yeniden geldi. Uygulamada açık kalan harita bu yeni, kaydedilmemiş test haritasıdır.
- Bu düz test haritasında su çıkışı yok; önizleme bunu açıkça bildirdi. Test, gerçek dağlık dünyada
  çıkış veya Minecraft su güncellemeleri kabulü yerine geçmez. Kullanıcının kayıtlı dünyaları değiştirilmedi.

## Referans yaklaşım

Önceki araştırmadaki bağlantı koruyan inceltme, PyFlwDir akış grafiği ve değişken genişlikli
koridor yaklaşımı bağımsız Java koduna uyarlanmıştır. Python/JTS çalışma bağımlılığı eklenmemiştir.

https://scikit-image.org/docs/stable/auto_examples/edges/plot_skeleton.html
https://deltares.github.io/pyflwdir/latest/
https://locationtech.github.io/jts/javadoc/org/locationtech/jts/operation/buffer/VariableBuffer.html
