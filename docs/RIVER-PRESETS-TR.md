# Nehir düzeltmesi — blueprint benzeri sığ yatak

## Son güncelleme — 3 Eylül 17:08

Genel yönlendirme, gerçek voxel yüksekliği ve kuru kıyı desteği düzeltildi;
360 Java + 47 script testi geçti ve masaüstü kurulumu yenilendi.
[Güncel rapor ve test/kurulum durumu](RIVER-ROUTING-RELIABILITY-TR.md).
Su tavanı artık exporttaki `round(height)` desteğiyle ve gerçek kuru komşularla
hesaplanır. Aşağıdaki `floor`/69,5 blok/183 test/önceki JAR bilgileri eski
aşamanın tarihsel kaydıdır; güncel davranış ve paket için üstteki raporu kullanın.

## Güncel çalışma: araziyi koruyan nehir

Kullanıcı, 12 blokluk kıyı uyarlamasının oyunda yapay teraslar oluşturduğunu
gösterdi. Bu son istek, önceki kazı/dolgu izninin yerine geçer. Yeni adlandırılmış
profillerde `enableTerrainPreservation()` kullanılır:

- [x] Dolgu sıfır; yalnız gerçek ıslak kanal hücrelerinde sınırlı kazı yapılır.
  Kuru hücrelerin yüksekliği, terrain'i, su düzeyi ve biome'u değiştirilmez.
- [x] 12 blokluk düzleştirme kuşağı kaldırıldı. Su altı kesit yumuşatma seçeneği
  yalnız kanalın iç biçimini değiştirir; yamaçlara teras çizmez.
- [x] Rota maliyeti fazla kazı, yanal eğim, dışbükey sırt ve keskin dönüşleri
  hesaba katar; mevcut vadiyi tercih eder. Kaynak/nokta yolu yerleri korunur.
- [x] Su düzeyi doğal zeminin üstüne yuvarlanmaz. Profil derinliği bir üst
  sınırdır; doğal bir sığlıkta yatak 0,65 blok asgari derinliğe kadar sığlaşabilir.
  Kazı bütçesi büyütülmez. Gerçek deniz/göl kotu bağlantıda aynen korunur.
- [x] Taş kıyıyı yükseltmek yerine, tam suya komşu kuru yüzey bloğunun exportta
  slab/stair ile delinmesi önlenir. Su altı granit ayrıntılar korunur.
- [x] Küçük testte bütün kuru hücreler değişmeden kalıyor; zorlu sırt/yanal
  uçurum zorla kazılmıyor. Eski adaptasyon API'si yalnız geriye uyum/regresyon
  için durur; yeni arayüz/scriptler onu çağırmaz.
- [x] Gerçek dünya yedeğinde salt-okunur koruyucu rota: 69,5 blok,
  gerçek denize bağlantı (su kotu 0), dolgu sınırı 0, kazı sınırı 1,85.
  Son tam testte planlama 0,785 saniye; dosya SHA-256, dünya sayaçları ve
  arazi örnekleri değişmedi. Bu, her dünyada rota garantisi veya küresel
  olarak en iyi rotanın bulunduğu iddiası değildir.
- [x] 183 Java testi ve 19 script testi geçti; hata ve atlanan test yok.
  Aynı pürüzlü kıyıda eski yöntem 1.838 kuru hücreyi değiştirirken yeni yöntem
  0 kuru hücreyi değiştirdi. Sonuçlar:
  `C:\Users\Admin\Documents\WorldPainter-river-preserve-20260903-150326\verification`.
- [x] Java 21 derlemesi ve ayrı Windows app-image hazır (354 dosya):
  `C:\Users\Admin\Documents\WorldPainter-river-preserve-20260903-150326\package\WorldPainter v2`.
  JAR SHA-256: `656E6FC81C73C8B99332870695F85F12478B295749FFE2241F0D7CE607C4AD3B`.
  20 derlenmiş class/script girdisi ve dört plugin sağlayıcısı doğrulandı;
  mevcut `-Xms512m -Xmx6g` ayarı korundu.
- [x] 2026-09-03 15:40:15: kullanıcı uygulamayı normal kapattıktan sonra paket
  `dist\WorldPainter v2` konumuna kuruldu. 354 dosya ve JAR SHA-256 doğrulandı;
  iki nehir scriptinin profil önbelleği kaynakla eşleşiyor. Masaüstü kısayolu
  doğru exe'ye bağlı ve 6 GB sınırı korundu.
- [x] Kurulum öncesi profil + uygulama: 994 dosya / 2.339.913.898 bayt
  SHA-256 ile doğrulandı; 363 brush korundu. Tarihli yedek:
  `C:\Users\Admin\Documents\WorldPainter-river-backup-20260903-153806`.
  Eski aktif uygulama silinmedi, aynı yedekte `retired-active` içine taşındı.
- [x] Yeni exe 15:40:26'da başlatıldı. JIDE açılış penceresi listelendi;
  ekran yakalama iki denemede `window crop is outside captured monitor`
  hatası verdi. Kullanıcıdan uyarıyı normal kapatması istendi. Ana ekran ve
  yeni ayar penceresinin görsel kontrolü bu kurulumda tamamlanmadı.
  Kullanıcı dünyasında otomatik script çalıştırılmadı.
- [ ] Nehir uygulanmamış kopyadan yeni Minecraft exportuyla görsel kabul.

Azami toplam kazı yeniden profil derinliği + 0,75 bloktur: Dere 1,60;
Doğal nehir 1,85; Geniş nehir 2,15; Kanyon 2,75; Dağ deresi 1,75.
Önceden yapılmış teraslar bu güncellemeyle geri alınmaz; karşılaştırma için
nehirden önceki `.world` kopyası veya kullanıcı denetimindeki geri alma gerekir.

## Önceki sürüm: alternatif rota ve yerel arazi uyarlama

Kullanıcının “uygun rota yok deyip bırakmasın; gerekirse araziyi düzenlesin”
isteği üzerine yeni adlandırılmış profiller iki aşamalı çalışır:

- `ShallowRiverRouter` farklı yükseklik bantlarından, birbirinden uzak kaynaklar
  ve çıkışlar dener. Eski 300 blok minimum uzunluk, birikim ≥8 ve deniz+8 kaynak
  şartları yeni otomatik yola taşınmaz. Mevcut deniz/göl önceliklidir.
- Önceki kaynak doğruysa konumu sessizce değiştirilmez; o kaynaktan alternatif
  koridor aranır. Boyanmış kaynak yaması tek piksel başına ayrı nehir üretmez.
- Yeni otomatik yol eski JavaScript hidrolojisini iki kez hesaplamaz. Eski
  `presetId=0` akışı ayrı kalır. Nokta yolunda çizilen rota korunur, yatağın yerel
  arazi uyarlaması kullanılabilir; noktalar gizlice başka çıkışa yönlendirilmez.
- Kullanıcı onayıyla yerel grading açılır: eski toplam kazı bütçesine en fazla
  3 blok ilave, en fazla 2 blok dolgu; su derinliği profil değeriyle aynı kalır.
  Kıyı etkisi kanalın dışındaki 12 blokta sıfıra iner. Bütün dünya düzleştirilmez.
- Rotalar geçici salt-okunur planlarda doğrulanır; yalnız kabul edilenler tek
  uygulamada yazılır. Sonuç gerçek azami kazı/dolgu ve doldurulan hücre sayısını
  bildirir. İptalde kazı ve dolgu birlikte geri alınır.
- Normal bir blokluk aşağı-akış ile kuru kıyı deliği ayrılır. Gerçek kuru açıklık
  mevcut koridor içinde fiziksel dolgu ile kapatılır; dolgu etkisi dört blokta
  sıfıra iner. Taş/custom terrain'de slab/stair exportu için su kotundaki destek
  bloğu tam tutulur. Uç kapakları kuru kalır; uç dışına yeni su yazılmaz.
- Birden fazla yol geçici birleşmiş planda tekrar doğrulanır. Farklı kotlu
  kesişme önceki yolu bozmaz; yeni kuru uç kapağı eski ıslak kanalı kapatmaz.
- Arama ve önbellek sınırlıdır; koruma, lav, eksik tile, kaçınma katmanı ve deniz
  güvenliği zorla aşılmaz. Gerçekten mümkün olmayan/bütçesi tükenen durumda kırmızı
  JavaScript hatası yerine açıklamalı, dünyayı değiştirmeyen sonuç verilir.

Yeni adaptif profillerin azami kazısı: Dere 4,60; Doğal 4,85; Geniş 5,15;
Kanyon 5,75; Dağ deresi 4,75 blok. Aşağıdaki eski kazı tablosu düşük seviyeli
motorun adaptasyon **kapalı** sözleşmesidir.

### Bu ekin doğrulama durumu

- [x] 11 alternatif-router, 10 adaptif kazı/dolgu, 4 eğim/kıyı, 6 birleşim testi;
  gerçek Nashorn üzerinden boş otomatik aday / elle kaynak / yeniden rota.
- [x] 155 Java testi ve 18 hızlı script testi başarılı. 17 gerçek chunk export
  testi içinde adaptif kazı/dolgu ve hem çimen hem granit çapraz eğimli kıyı var.
- [x] Yedek gerçek dünyada salt-okunur ölçüm: 25 tile, 640×640 alan; yükleme ve
  rota denemesi dosya SHA-256, world/dimension değişim sayaçları ve arazi örnekleri
  üzerinde değişiklik yapmadı.
- [x] Gerçek dünyada başarılı alternatif rota: son tam test koşusunda 22 aday
  içinde 1 kabul, 70,9 blok uzunluk, 1,445 saniye planlama. 25.600 arazi örneği,
  6.806 arama düğümü; kaynak (14,326), çıkış (62,370). Bu örnekte güvenli mevcut
  su bağlantısı bulunamadığı için dünya kenarından içeride kuru çıkış seçildi.
  Bu ölçüm küresel olarak en iyi rotanın bulunduğu veya her dünyada aynı sürenin
  alınacağı iddiası değildir. Veri dosyası ve dünya sayaçları değişmedi.
- [x] Güncel tam test tekrarı; çıktı raporları
  `C:\Users\Admin\Documents\WorldPainter-river-adaptive-20260903-144228\verification`.
- [x] Java 21 ile derleme ve ayrı Windows app-image hazır:
  `C:\Users\Admin\Documents\WorldPainter-river-adaptive-20260903-144228\package\WorldPainter v2\WorldPainter v2.exe`.
  Paket JAR SHA-256: `DBF9024925D6B9B08BB57144ABE46760DC616BD109AD642EE380A74CC130CE97`.
  Dört ana class ve iki nehir scripti kaynak/derlenmiş hedef ile eşleşiyor;
  dört plugin girdisi ve mevcut `-Xms512m -Xmx6g` ayarı korundu.
- [x] 2026-09-03 14:54–14:56: kullanıcı uygulamayı normal kapattıktan sonra yeni
  paket `dist\WorldPainter v2` konumuna kuruldu. 354 paket dosyası ve aktif JAR
  SHA-256 doğrulandı; iki nehir scriptinin profil önbelleği kaynakla eşleştirildi.
  Masaüstü `WorldPainter V2.lnk` doğru exe'ye bağlı; uygulama 14:54:58'de açıldı.
  Ana ekran ve Run Script penceresi görüntülendi; kullanıcı dünyasında hiçbir
  otomatik script başlatılmadı. Nehir ayarları, açık script işlemine müdahale
  etmemek için ayrıca tıklanmadı.
- [x] Güncel kurulum öncesi yedek:
  `C:\Users\Admin\Documents\WorldPainter-river-backup-20260903-145249`.
  Profilin 640 dosyası / 2.148.132.062 baytı SHA-256 ile doğrulandı; 363 brush
  korundu. Eski uygulama `application` ve `retired-active` altında korunuyor.
- [ ] Minecraft içinde yeni export ile görsel kabul.

Alttaki 14:06 kurulum kaydı **önceki** 116 testlik sürümdür; bu ekin kurulduğunu
göstermez. Güncel paket ve ölçüm sonucu bu bölümde ayrıca kaydedilecektir.

## Sorun ve uygulanan değişiklik

Önceki profiller su derinliğini sınırlıyordu; ancak eski kaynak scriptinin yerçekimi
ve eğim düzeltmeleri daha önce araziyi kazabiliyordu. Böylece toplam kazı sınırsız
kalıyor, su dar ve derin bir toprak yarığının dibinde görünüyordu. Granit detayı da
arayüzden kapalı gönderildiği için toprağa slab/merdiven uygulanamıyordu.

Yeni `ShallowRiverCarver`, Otomatik / Nokta yolu / Kaynak modlarındaki adlandırılmış
profillerin ortak yatak motorudur. Bu profiller eski kazı, eğim, su genişletme ve
tekrar düzeltme aşamalarına girmez. `presetId=0` ile doğrudan çalıştırılan eski
script davranışı korunur.

- Bütün kazı planı değiştirilmemiş arazi üzerinden hesaplanır; üst üste gelen
  kesitler derinliği tekrar tekrar çıkarmaz.
- Merkez hattındaki küçük zikzaklar arazi elverdiğinde yumuşatılır; araları sürekli
  kesitlerle doldurulur. Yatak ve kuru dış kıyı `smoothstep` ile bağlanır.
- Aynı kesitte ortak, aşağı akış boyunca yükselmeyen tam sayı su düzeyi kullanılır.
- Hem su altı yatak derinliği hem orijinal araziden toplam kazı sınırlıdır.
  Sığ sınırı aşmadan geçilemeyen sırt/yamaç rotası reddedilir; derin yarıkla zorlanmaz.
- Islak yatak ağırlıklı toprak, kıyı geçişleri granit destekli küçük deterministik
  yamalardır. Slab/merdiven yapabilen granit yalnızca yatak/kenar geçişinde kullanılır;
  çevredeki düzlükler cobblestone veya granitle boyanmaz.
- Mevcut su/göl sütunları, korunan alanlar ve lav korunur. Eski yerleşik `River`
  katmanı ile çakışan rota reddedilir: o katmanın exportta ikinci kez kazması önlenir.
  `River Path` işaretleme katmanı bu eski `River` katmanı değildir.
- Planlama salt okunurdur. Plan sonrası arazi değişimi uygulanmadan yakalanır;
  yeni motorun uygulaması iptal olursa kendi yükseklik/su/terrain/biome yazıları
  geri alınır. Başarılı işlem mevcut script geri alma sınırını kullanır.

### Tek blok daralma, kıyıdaki parçalı su ve yerel flatten düzeltmesi

- Minimum ıslak iç yarıçap 1,5 blok; iç kenar derinliği en az 0,65 blok.
  Bu, WorldPainter'ın yükseklik yuvarlamasından SONRA en az üç blok gerçek su
  koridoru sağlar. Genişleyen nehirde iç koridor da genişler. Kazı bütçesi yüzünden
  bu koridorun kapanacağı rota baştan reddedilir; sessizce tek bloğa düşürülmez.
- Kıyının kendi arazi yüksekliğinden ayrı bir su düzeyi türetilmez. Yalnız gerçek
  ıslak yatağa ortak kesit su düzeyi yazılır; kuru kıyı hücrelerinin su düzlemi
  korunur. Böylece rastgele granite slab/stair'ler ayrı waterlogged kaynaklarına
  dönüşmez. Su içindeki granit detaylar waterlogged kalır.
- Kesite en yakın gerçek merkez hattı parçası seçilir; mesafe/genişlik oranı
  kullanılmaz. Genişleyen aşağı akış parçasının komşu kesitin su kotunu çalması
  ve farklı yanal seviyeler üretmesi önlenir.
- Uçlar en yakın parça seçildikten sonra kırpılır. Son noktayı geçen eski yuvarlak
  damgalar geride kalıp çıkışın aşağısına yüksek su taşımaz.
- “Kıyıları yumuşat ve çevreyi doğal düzleştir” açıksa yatağın dışına 6–8 blokluk
  kuru bir geçiş kuşağı eklenir. Flatten etkisi dışa doğru sıfırlanır; kazı sınırı,
  mevcut göller, korunan alanlar ve çevredeki terrain materyalleri korunur.
  Bu ayar bütün dünyayı düzleştirmez, yüksek dağ duvarını sınırsız kazmaz.

### Deniz ağzı ve taş–çimen birleşimleri

- Mevcut deniz/göl seviyesi çıkışın sınır koşuludur. Nehir, kıyıdaki küçük bir
  çukuru izleyerek bu seviyenin altına düşmez; mevcut deniz tabanı ve su sütunları
  değiştirilmez. Yükselen suyun sığ kalması ve son kıyı geometrisiyle tutulabilmesi
  doğrulanır; açık yamaca taşacak veya aşırı derinleşecek rota reddedilir.
- Son noktanın en fazla 2 blok önündeki veya yanındaki güvenli suya kısa bağlantı
  kurulabilir; geriye U dönüşü yapılmaz. Yolun başka kısmında farklı seviyeli
  mevcut suya uyumsuz yan temas varsa rota veri değiştirmeden reddedilir.
- Taşın 2×2×2 yüzey hesabı artık dört yönde simetrik 3×3 komşuluk kullanır.
  Yalnız doğu/güney komşuluğunu örneklemenin oluşturduğu yön farkı giderildi.
- Aynı yuvarlanmış yükseklikteki kuru grass/dirt gibi tam blok yüzeyle birleşen
  taş tam blok kalır; çimenin yanında yarım blokluk oluk oluşturulmaz. Normal
  custom terrain karışımlarında da gerçek komşu yüzey materyali örneklenir.
- Tam sayı yüksekliğinde düz plato, eksik dünya sınırı ve tek blok yüzeyiyle
  ifade edilemeyen gerçek uçurumlar tam blok kalır. Her taşı merdivene çevirmek
  amaç değildir. Sualtı granit slab/stair ve waterlogging desteği korunur.
- Bu sınır koruması terrain yüzeyi içindir; sonradan çalışan object/layer
  exporter'larının eklediği her blok için bir garanti değildir.

## Blueprint ölçümünün dayanağı

Kullanıcının `river.bp` dosyası değiştirilmeden okundu. Ayrıntılı sayımlar ve tekrar
üretim aracı: `WorldPainter/tools/axiom-analysis/river-results/README.md`.

- 441 ıslak sütunun 332'sinde 1 tam su bloğu, 8'inde 2 su bloğu, 101'inde yalnızca
  waterlogged kısmi blok var. 2'den fazla su bloğu bulunan sütun yok.
- Komşu ıslak sütun çiftlerinin %96,8'inde üst su hücresi aynı yükseklikte.
- Islak zeminin %53,3'ü dirt; %23,1'i granite slab, %14,1'i granite stairs.
- Islak/kuru sınırında su hücresi tavanından 1 bloktan yüksek kıyı yok.

Amaç bu sığ kesit ve yarım blok karakterine yaklaşmaktır; aynı blueprinti
tekrarlamak veya her arazi üzerinde aynı blok oranını garanti etmek değildir.
Ölçülen su hücresi yüksekliği gerçek akışkan yüzeyi simülasyonu değildir.

## Hazır ayarlar

| Profil | Kaynak → son yatak genişliği | Merkez yatak derinliği | Azami toplam kazı |
|---|---:|---:|---:|
| Dere | 3 → 6 | 0,85 | 1,60 |
| Doğal nehir | 5 → 12 | 1,10 | 1,85 |
| Geniş nehir | 8 → 20 | 1,40 | 2,15 |
| Kanyon | 4 → 10 | 2,00 | 2,75 |
| Dağ deresi | 3 → 8 | 1,00 | 1,75 |

Birim WorldPainter/Minecraft bloğudur. Genişlik tüm yatak koridorudur; gerçek ıslak
genişlik daha dardır, kuru kıyı omuzu daha dışa uzanır. Tam sayı su ve blok
yuvarlaması nedeniyle 1,10 yatak derinliği exportta yer yer 2 su hücresi içerebilir.
2×2×2, bir Minecraft bloğunun yüzeyini örnekleme yöntemidir; dünya ölçeğini iki
katına çıkarmaz. Ek şelale havuzu kazısı ve ek dallanma bu profillerde kapalıdır.

## Kullanım ve sınırlar

1. **Nehir öncesi dünya kopyasını açın.** Bu düzeltme eski derin yarığın kaybolan
   yüksekliğini bilemez ve kendiliğinden onaramaz. Aynı yere tekrar uygulama test
   yöntemi değildir; mevcut su sütunları özellikle korunur.
2. Tools → Nehir → Doğal nehir, 1 ana nehir,
   “Kıyıları yumuşat ve çevreyi doğal düzleştir” açık ile başlayın.
3. Granit yarım bloklar için Dimension Properties içindeki
   **smooth surface with slabs and stairs** seçeneği açık olmalıdır. Nehir penceresi
   bu ayarın durumunu gösterir, tüm dünyanın export ayarını sessizce değiştirmez.
4. Rota reddedilirse vadi tabanına yakın kaynak/noktalar seçin. Motor, sığ görünüm
   uğruna mevcut yüksek sırtı derin bir kanalla kesmez.
5. Önce küçük bir test export'u yapın. Kullanıcı dünyasında otomatik script
   çalıştırılmaz; başarılı işlemi geri almak için uygulamanın Undo işlemini kullanın.

Mevcut havza/rota arama algoritması korunmuştur. Gerçek keskin uçurum veya dik
inişte doğal su basamakları hâlâ olabilir; her topoğrafyada kesintisiz düz su
garantisi yoktur. Normal eğimlerde üst yüzey yumuşatması dört yönde test edilir;
yüksek uçurumlarda tam bloklar bilinçli olarak korunur.
Nokta yolunun hazırlık aşaması `River Path` işaretlerini birleştirebilir; motorun
iptal geri alması kendi yatak yazıları içindir, bütün eski script aşamaları için
genel bir işlem sistemi değildir.

## Doğrulama / TODO

- [x] Blueprintin doğrudan blok ve yarım blok ölçümü.
- [x] Ortak sığ motor; eski çift kazı yolunu ayırma; üç modun parametreleri.
- [x] 44 gerçek Dimension testi: toplam kazı, yuvarlama, sığ su, kıyı, tile sınırı,
  negatif koordinatlar, kesişim, aynı seed, göl/koruma, stale plan ve iptal geri alma.
- [x] Yatay, dikey, diagonal ve hafif eğimli arazide en az üç blok ıslak koridor;
  aynı kesitte tek su Y; kuru kıyıda kopuk su cepleri olmaması; iki kıyıda local
  flatten ve 16 blok dışındaki test arazisinin değişmemesi.
- [x] 14 gerçek chunk export testi: granit slab/stair yönleri, waterlogged, grass/dirt
  korunumu; yeni motorla 24 chunk'lık uçtan uca export. Testte su sütunu en fazla 2.
  Deniz ağzında Y=100 gerçek su sütunları, değişmeyen kum tabanı ve 3 blok genişlik.
- [x] Son export ölçümü: minimum gerçek su genişliği 3; kuru kıyıda fluid 0;
  düz/eğimli kesitte yanal AIR açıklığı 0; 204 waterlogged granit detayında
  desteksiz parça 0 ve ana sudan kopuk parça 0. Bunlar voxel destek/bağlantı
  kontrolleridir; Minecraft akış güncellemelerini simüle etmez.
- [x] S kıvrımı/90° dönüşte tek bağlı su; uç nokta dışına eski segment kapağından
  su/kazı sızmaması; 8192×128 test şeridinde planlama/uygulama/iptal/ilerleme testi.
  Bu PC'deki tek koşu: planlama 1216,3 ms, uygulama 94,6 ms, 65.650 değişen hücre,
  azami gerçek kazı 1,0977 blok. Tam 8K×8K dünya veya export benchmarkı değildir.
- [x] 10 Java profil testi ve 8 gerçek Nashorn üretici/script-köprü testi.
- [x] 29 surface-smoothing birim testi, 8 gerçek taş–çimen/yön/chunk sınırı export
  testi ve 3 smooth-snow yüzey desteği testi. Son toplam **116 Java testi**, sıfır hata.
- [x] Otomatik hydro, otomatik fallback, kaynak ve düz yolda rota yönü regresyonları;
  eski profilin yön sözleşmesi korunuyor.
- [x] 16 hızlı JavaScript yönlendirme/parametre regresyonu ve iki scriptin sözdizimi.
- [x] Kullanıcı dünyasını kaydedip uygulamayı normal kapattı; çalışan süreç kalmadı.
  Profil, 363 özel fırça, mevcut app-image ve masaüstü kısayolu yedeklendi:
  `C:\Users\Admin\Documents\WorldPainter-river-backup-20260903-135245`.
- [x] Son eklerle tam Java 21 build/jpackage başarılı (03.09.2026 14:06).
  Aktif kurulum: `C:\Users\Admin\Documents\WorldPainter-v2-updated\dist\WorldPainter v2\WorldPainter v2.exe`.
  `C:\Users\Admin\Desktop\WorldPainter V2.lnk` bu exe'ye bağlı.
  512 MB başlangıç / 6 GB azami RAM ayarları yedekten geri kondu.
- [x] Aktif JAR, build çıktısıyla SHA-256 eşit; motor, renderer, pencere sınıfları
  ve iki river scripti derlenmiş sınıflar/kaynakla birebir karşılaştırıldı.
  JAR SHA-256: `75DEA523A78DF3FA5CB7514E5E470674658A87937206EB51D5BFBA45587B97CA`.
  Kaynak tabanı `fb893270013f388926d3cf336c1012cc7936cd2f` + yerel düzeltmeler;
  GitHub'a push yapılmadı. Test raporları ve build.log tarihli yedeğin
  `verification` klasöründe korundu. jpackage PNG ikon uyarısında varsayılan
  ikonu kullandı; paketleme başarıyla tamamlandı.
- [x] Güncel aktif exe başlatıldı; WorldPainter ana ekranı ve özel fırçalar görüldü.
- [x] Açılıştan sonra profildeki iki `bundled-cache/rivers_*.js` dosyası da yeni
  kaynakla SHA-256 eşit. 363 fırçanın göreli yolları ve boyutları yedekle aynı.
- [ ] Nehir penceresinin son canlı kontrolü: Axiom doku işlemi ekranda belirdiği
  için kullanıcı kontrolüne müdahale etmemek amacıyla UI tıklamaları durduruldu.
- [ ] Kullanıcının gerçek dünyasının ayrı kopyasında Minecraft görsel kabulü.

Sentetik testlerin geçmesi gerçek oyun içi görsel kabulün tamamlandığı anlamına gelmez.

### Testleri tekrar çalıştırma

`WorldPainter` klasöründe, bu bilgisayara ait JDK toolchain tanımıyla:

```powershell
mvn -pl WPGUI -am '-Dtest=RiverScriptBridgeTest,RiverPresetTest,ShallowRiverCarverTest,ShallowRiverSurfaceExportTest,SurfaceSmootherTest,SurfaceSmoothingSeamExportTest,ExplicitSnowSurfaceSupportTest' '-Dsurefire.failIfNoSpecifiedTests=false' test -t '..\toolchains.xml'
node --test tools/tests/river-presets.test.cjs
```
