# Dağdan vadiye nehir seçimi — 3 Eylül 2026

## Hedef ve durum

**20:04 kullanıcı denemesi:** Kurulu sürüm yeni, henüz dosyaya kaydedilmemiş
640×640 engebeli dünyada uzun aramadan sonra sağ sınırda kısa bir nehir
üretti. Teknik rota kabulü gerçekleşmiş olsa da kullanıcının dağdan vadiye
doğal ana nehir hedefi karşılanmadı. Bu özellik görsel olarak tamamlanmış
sayılmamalıdır. Bu haritanın `.world` dosyası bekleniyor; eski yedekler aynı
arazi değildir. Son kullanıcı geri bildirimi nedeniyle aşağıdaki yeni aşama
uygulandı: salt okunur drenaj ön taraması ve doğrulanmış aday karşılaştırması
eklendi. Son kaynakla 417 Java ve 61 Node testi geçti. Yeni paket yedekli
kuruldu ve başlatıldı; ana ekranın görsel kontrolü ve Minecraft kabulü tamamlanmadı.

Hedef yalnızca herhangi bir sığ kanal bulmak değil; dağın uygun üst vadi
kesiminden başlayıp alçalan, mevcut araziyi takip eden yeni bir nehir bulmaktır.
19:22 paketinin 93 blokluk kıyı rotası bu hedef için yeterli değildir ve o paket
kurulmamıştır. Bu değişiklik önceki kazı/koruma kurallarını gevşetmez.

- [x] Otomatik modda dünyaya göre yüksek kaynak, alçalma ve yeni yatak ölçütleri.
- [x] Altı turda Q99 → Q95 → Q90 kaynak aralığı; alçalma/uzunluk şartları sabit.
- [x] Profil hesabında yalnız gerçekten kaynak olabilecek örnekleri kullanma.
- [x] İlk mevcut suya çapraz temasın yarım blok aralıklarla doğru ölçülmesi.
- [x] Üstteki gölü, son denizin değil yerel nehir kesitinin su seviyesiyle kontrol.
- [x] Son kaynakla 45 sınıfta 396 Java testi, 61 Node testi, 12 JS sözdizimi kontrolü.
- [x] Gerçek denizli yedekte planlama; kaynak/yatak/dosya değişmeden güvenli rota.
- [x] Aynı henüz uygulanmamış plandaki önceki nehre katılınca yeni yatak sayımını durdurma.
- [x] Java 21 derlemesi, yeni Windows paketi ve yedekli aktif kurulum.
- [ ] Kullanıcının son hatayı aldığı tam `.world` dosyasında doğrulama.
- [ ] Minecraft içinde görsel/su güncellemesi testi.

## Seçim kuralları

Her bağlı arazi bölgesinin kuru, korunmayan, sınırdan yeterince uzak kaynak
örnekleri taranır. Yerel olarak daha düz veya çukur örneklerden vadi yükseklik
dağılımı ölçülür. Zirve sırtları tek başına bütün kaynak kotunu belirlemez.

İlk iki tur üst vadi Q99 kotunu, sonraki iki tur Q95'i, son iki tur Q90'ı
dener. Her biri arazi Q90 kotuyla sınırlandırılır. Kaynak alt sınırı ayrıca
vadi tabanı + ilk hesaplanan gerekli alçalmanın altına inmez. Sonraki turda
yalnız kaynak aday havuzu genişler; minimum gerçek su düşüşü veya yeni yatak
uzunluğu azaltılmaz. Haritanın dikey rölyefi çok azsa dağ şartı uydurulmaz,
düz arazideki sığ kanal davranışı korunur.

Dağlık arazide gerekli yeni yatak uzunluğu bölgenin uzun kenarının %20'sidir
(en az mevcut 8–64 blok sınırı, en fazla 1024 blok). İlk suya kadar kuş uçuşu
uzaklık bunun en az %60'ı olmalıdır. Gerekli su kotu düşüşü ilk ölçülen vadi
rölyefinin %35'i, en az 4 bloktur. Bunlar bir fizik simülasyonu değil, çok kısa
kıyı parçalarının dağ nehri olarak kabulünü önleyen seçim ölçütleridir.

Yeni yatak uzunluğu ilk mevcut suya kadar ölçülür; mevcut nehrin devamını
takip etmek sonucu yapay biçimde uzatmamalıdır. Küçük kıvrım ve vadi
iyileştirmeleri de aynı kontrollerden geçer. Elle seçilmiş kaynak ve nokta yolu
bu otomatik kaynak seçimiyle başka yere taşınmaz.

## Korunan sınırlar

Sınırsız deneme veya her haritada başarı garantisi yoktur. Altı turlu Router
192 tam aday, 360.000 düğüm ve 24 milyon kesit örneğiyle sınırlıdır; iptal
kontrolü sürer. Otomatik mod hiçbir aday bulamazsa bitiş genişliğini kademeli
azaltabilir (Doğal Nehir: 5–12 → 5–8 → 5–5); başlangıç genişliği, azami sığ
kazı, sıfır dolgu, kıyı ve koruma kontrolleri değişmez.

Planlama kullanıcı dünyasını değiştirmez. Uygulama yalnız bütün adaylar ve
gerçek ıslak alan doğrulandıktan sonra tek işlemde yapılır. Kurulum/test
sırasında kullanıcının dünyasında otomatik script çalıştırılmaz. Daha önce
bozulmuş nehirler bu güncellemeyle kendiliğinden onarılmaz.

## Örneklerin ayrımı

17:37 yedeğindeki `autosave.1.world` eski, çok seviyeli bir nehir içerir.
`autosave.2.world` yalnız deniz seviyesinde su içeren **farklı bir arazidir**;
birincinin nehir öncesi sürümü değildir. İkinci dosyada başarılı sonuç almak,
birinci dosyanın veya kullanıcının son ekranındaki haritanın düzeldiğini
kanıtlamaz. Son ekranın tam `.world` dosyası ayrıca istenmiştir.

## Önceki doğrulama — 19:55 kaynak sürümü

`WorldPainter-river-mountain-20260903-195500/verification` altında test
raporları korunur. 396 Java testinde hata/atlama yoktur. Bu grup ayrıca
Y1050–1160 vadi, negatif Y gölü, 8192×128 uzun şerit, kar/doku scriptleri,
granit exportu, iptal ve geri alma kontrollerini kapsar; **tam 8K×8K Minecraft
görsel kabul testi değildir**.

Denizli `autosave.2.world` üzerinde 5–12 blok genişliğinde ilk turda rota
bulundu: 518,9 blok toplam yol; ilk mevcut suya kadar 517,4 blok yeni yatak;
43 blok su kotu düşüşü. Planlama 0,449 saniye sürdü (yalnız bu küçük örnek,
yükleme hariç). Dosya SHA-256'sı, dünya/tile değişiklik sayaçları ve örneklenen
arazi özeti değişmedi. S vadisi testinde 750,3 blok yeni yatak ve 48 blok
alçalma bulundu; daha kısa uygun kıyı parçası seçilmedi.

**Ayrı, başarısız gerçek dünya testi:** Eski nehirli `autosave.1.world` üzerinde
5–12, 5–8 ve 5–5 geçişleri toplam 67,982 saniyede yeni uygun dağ nehri bulamadı.
Son Router 537 yol araması ve 116 kaynak denedi. Son adaylar dar kesit veya
mevcut farklı su seviyelerinin birleşim koşullarını geçemedi. Bu dosyada
başarı bekleyen opt-in smoke testi başarısızdır; 396 testlik ayrı başarılı
regresyon sonucuna dahil edilmez. Dosya hash'i ve dünya değişiklik sayaçları
yine değişmedi. Bu sınırlama giderilmiş veya her dünyada başarı garanti
edilmiş gibi sunulmamalıdır.

Orijinal su hücrelerini tamamen kaçınılacak alan yaparak yalnız kuru
çıkışları zorlayan ayrı, salt okunur deney de aynı yedekte yaklaşık 69 saniyede
rota bulamadı. Bu deneme üretim koduna taşınmadı; eski suyu engellemek bu
haritanın çözümü olarak gösterilmemelidir.

## Önceki kurulum kaydı — 19:55

- Kaynak: `C:\Users\Admin\Documents\WorldPainter-v2-updated`
- Exe: `dist\WorldPainter v2\WorldPainter v2.exe`
- Masaüstü kısayolu: `C:\Users\Admin\Desktop\WorldPainter V2.lnk`
- Paket/test kaydı: `C:\Users\Admin\Documents\WorldPainter-river-mountain-20260903-195500`
- Yedek: `C:\Users\Admin\Documents\WorldPainter-river-mountain-backup-20260903-195500`
- Aktif JAR SHA-256: `D49FF84CFD223DD85D72BBAEEC22D22BA617CC829DF5462B7AE3F4ECB97A310D`

354 paket dosyası, 97 class/script girdisi, 4 plugin ve 14 materialise edilmiş
script menü girdisi doğrulandı. 1009 dosyalık yedekte 363 brush ve 3 `.world`
dosyası var. Uygulamayı açmadan önce önbellek dışındaki 626 profil dosyasının
hash'leri değişmedi. RAM 6 GB kaldı. Önceki aktif uygulama yedekte
`retired-active` olarak da korunur; veri silinmedi. Değişiklikler yereldir,
GitHub'a gönderilmemiştir.

Güncel exe ve ana editör görsel olarak açıldı. Kullanıcı etkileşimi algılanınca
UI kontrolü bırakıldı; kullanıcı kendi script çalıştırmasını başlattı. Kurulum
ajanı dünyada script çalıştırmadı. Oyun içi görsel kabul hâlâ ayrı bir adımdır.

## Yeni düzeltme: önce drenaj hattı, sonra güvenli kanal

### Önceki sürümde saptanan yapısal sorun

640 blokluk haritada mevcut minimum yeni yatak 128 blok ve kuş uçuşu uzaklık
76,8 bloktur. 320 bloktan kısa adaylar ertelenir, ama her turun sonunda
yeniden değerlendirilip kabul edilir. Kaynak ile çıkış birbirinden bağımsız
seçilir ve ilk uygun aday aramayı bitirir. Bu düzen, bir kanalın yapılabilir
olduğunu ölçer; uzun ve anlamlı bir dağ vadisini bulduğunu kanıtlamaz.

### Araştırma ve uygulanacak sıra

[RichDEM](https://github.com/r-barnes/richdem) ve
[pysheds](https://github.com/pysheds/pysheds) akış yönü, birikim ve havza
analizini kanal çıkarmadan önce hesaplayan örneklerdir.
[Priority-Flood makalesi](https://richard.science/sci/2014_depressions.pdf)
çukurları doldurma/yarma varsayımlarını ayrıca ele alır. Drenaj çizgisi elde
etmek, bu projede gerçek dünyayı doldurma veya derin kazma izni değildir.

- [ ] Kullanıcının aynı `.world` dosyasını al; değiştirilmeyen kopyada süre,
  su kotu, yeni yatak uzunluğu ve bütün kesit kazısını ölç.
- [x] Mevcut en fazla 65.536 hücreli grid üzerinde salt okunur
  `RiverDrainageCandidates` ön taraması: D8 aşağı-akış ebeveyni, gerçek su/kuru
  çıkış terminali, kalan yol uzunluğu, alçalma ve yukarı havza alanı.
- [x] Korunan hücrelerden çapraz geçişi engelle. Eşit kotlu düzlükleri gerçek
  alçak çıkışa göre çöz; kapalı çukurları doldurulmuş gibi varsayma.
- [x] Kaynak–çıkış çiftleri yerine bütün vadi hatlarını sırala. Ortak alt
  yatakları ayır; tek bir vadi bütün aday listesini doldurmasın.
- [x] En fazla 24 aday tanımı sakla; önce 8 farklı vadiyi tam genişlikte Carver
  kontrolüne gönder. A* gerekiyorsa bu vadinin sınırlı koridorunu iyileştirsin.
- [x] İlk kısa uygun rotayı hemen uygulama: doğrulanmış adayları yeni yatak,
  alçalma ve uzanım açısından karşılaştır. Kısa sınır rotası, henüz denenmemiş
  uzun drenaj hattını devre dışı bırakmasın.
- [x] Kazı/dolgu, mevcut su, kıyı, minimum genişlik, iptal ve tek uygulama
  kontrollerini aynen koru; grafik geçişlerine de iptal kontrolü ekle.
- [x] Kapalı çukur, düz plato, denizsiz dünya ve dağ vadisi için yapay dünya testleri.
- [ ] Kullanıcının aynı hata dünyası ve mevcut çok seviyeli nehirli yedekte başarı.
- [x] Son kaynakla 48 sınıfta 417 Java testi, 61 Node testi, 12 JS sözdizimi kontrolü.
- [x] Yeni paketi doğrula, yedekli kur ve uygulamayı başlat.
- [ ] Yeni kurulumun ana ekranını ve Minecraft görünümünü görsel olarak doğrula.

### Uygulama ayrıntıları ve sınırlar

Akış yönü gerçek yükseklikten D8 ile çıkarılır; mevcut veya plandaki su bir
terminaldir ve su kotuyla ölçülür. Düzlükte gerçek aşağı çıkış yoksa çukur
kapalı kalır. D8 sığ bir engelde durduğunda önceki sınırlı A* yolu korunur;
drenaj bulamamak otomatik olarak bütün nehirlerin imkânsız olduğu anlamına gelmez.

En az dört üst havza örneği olan başsu adaylarına öncelik verilir, fakat
bu zorunlu eşik değildir: 24 adayda altıya kadar düşük birikimli alternatife
yer ayrılır. Böylece örnekleme çözünürlüğü gerçek başsuları tamamen elemez.
Ortak ana yatağın tekrarları ve birbirine çok yakın kaynaklar ayıklanır.

Her geçişte en fazla sekiz yeni drenaj hattı değerlendirilir. Gerekirse
hat çevresinde sınırlı A* araması yapılır; koridor oluşturma her grid
hücresini en fazla bir kez ziyaret eder. En fazla 12 doğrulanmış adayın
koordinatları/ölçüleri tutulur; her adayın büyük düzenleme planı saklanmaz.
Arama 192 tam doğrulama sınırının son 12 hakkını nihai yeniden doğrulamaya
ayırır. Kazı/dolgu/koruma kontrolleri gevşetilmez.

Sıralama, görünüş düzeltmesinden **önceki doğrulanmış** yeni yatak uzunluğu,
su düşüşü ve uzanımı üzerindendir. Pahalı vadi/görünüş iyileştirmesi yalnız
seçim aşamasında çalışır, tekrar güvenlik ve minimum uzunluk kontrolünden
geçer. Bütün olası rafine yolların küresel optimumunu bulduğu iddia edilmez.
Kısa kıyı adayı sonraki turlara bekletilir; son tur veya arama bütçesi sonunda
uzun alternatif yoksa kullanılabilir. Bağlı bölgeler de mevcut ortak arama
bütçesi içinde karşılaştırılır; sınırsız arama yoktur.

### Yeni kaynaktaki bilinen başarısız örnek

`WorldPainter-river-drainage-20260903-202000/verification/existing-river-world.log`
altında ayrı çalıştırılan eski
çok seviyeli nehirli `autosave.1.world` testi 68,452 saniyede yine yeni rota
bulamadı (538 yol araması). Dosya/dünya özeti değişmedi. Bu başarısız opt-in
test başarılı regresyon sayısına dahil edilmez; bu harita henüz çözülmüş değildir.

Son paketle yeniden çalıştırılan bağımsız test de başarısızdır:
`verification/existing-river-final.log`, 70,830 saniye, 538 yol araması,
sıfır kabul. Dünya sayaçları, arazi özeti ve dosya hash'i değişmedi. Paketleme
işiyle kısmen eşzamanlı koşulduğu için süre doğrudan hız karşılaştırması değildir.

### Son regresyon sonuçları — drenaj sürümü

Raporlar `C:\Users\Admin\Documents\WorldPainter-river-drainage-20260903-202000\verification`
altındadır. 48 sınıfta 417 Java testi, 61 Node testi ve 12 JS sözdizimi
kontrolü geçti; başarılı grupta hata veya atlama yoktur. Ayrı başarısız gerçek
dünya testi yukarıda açıkça belirtilmiştir.

- Gerçek denizli `autosave.2.world`: 622,3 blok toplam, 620,8 blok yeni yatak,
  43 blok düşüş; ilk turda 1,223 saniye planlama. Dosya hash'i, dünya sayaçları
  ve arazi özeti değişmedi. Önceki sürüm aynı dosyada daha kısa 517,4 blok
  yatağı 0,449 saniyede bulmuştu; **her örnekte daha hızlı olduğu iddia edilmez**.
- Yapay S vadisi: 959,0 blok yeni yatak ve 197 blok su düşüşü, 0,742 saniye.
  Sekiz drenaj önerisi karşılaştırıldı. Fiziksel kazı/dolgu ve bağlantı
  testleri geçer; doğal görünüm için Minecraft içinde değerlendirme ayrıca gerekir.
- İki uygun vadi testi: 212 blokluk kısa kenar kanalı yerine daha uzun
  dağ–deniz rotası seçildi; karşılaştırma sırasında dünya değişmedi.
- 0,75 blok kapalı dipte güvenli A* alternatifi bulundu; 6 blok derin kapalı
  çukur doldurulmadı. Altı tekrar aynı drenaj ön taramasını kullandı.
- En geniş 64 blok ayarında koridor 65.536 hücreyle sınırlı kaldı. Son 12
  yeniden doğrulama hakkı ve manuel modun eski 192 sınırı ayrıca test edildi.

### Aktif kurulum — drenaj sürümü

- Kaynak: `C:\Users\Admin\Documents\WorldPainter-v2-updated`
- Exe: `C:\Users\Admin\Documents\WorldPainter-v2-updated\dist\WorldPainter v2\WorldPainter v2.exe`
- Masaüstü kısayolu: `C:\Users\Admin\Desktop\WorldPainter V2.lnk`
- Paket/testler: `C:\Users\Admin\Documents\WorldPainter-river-drainage-20260903-202000`
- Yedek: `C:\Users\Admin\Documents\WorldPainter-river-drainage-backup-20260903-202000`
- Aktif JAR SHA-256: `4D6942BD1F75D2BB68193BEABD7710AECA7D2765FF2798EDC65109CC061FEE59`
- Git tabanı: `fb893270013f388926d3cf336c1012cc7936cd2f`; yerel değişiklikler
  korunmuştur, bu çalışma GitHub'a gönderilmemiştir.

Java 21 paketlemesi başarılı. 354 kurulum dosyası, 100 derlenmiş class/script
girdisi, 4 plugin ve 14 materialise edilmiş script girdisi doğrulandı.
Yedekte 1010 dosya, 363 brush ve 3 `.world` bulunur. Açılıştan önce önbellek
dışındaki 626 profil dosyasının hash'i değişmedi. RAM 6 GB kaldı.
Önceki uygulama `retired-active` altında korunur; kullanıcı verisi silinmedi.

Güncel exe başlatıldı; ilk gözlemde `JIDE Software, Inc.` penceresi vardı.
Ekran yakalama iki denemede `window crop is outside captured monitor` hatası
verdiği için kör tıklama yapılmadı. Açılış günlüğü yeni uygulamanın dünya
yüklemeden başladığını gösteriyor. Kullanıcı dünyasında script çalıştırılmadı.

## Su altı 2x2x2 kontur düzeltmesi — 3 Eylül 2026

Kullanıcı oyun görüntüsünde geniş nehir tabanında tam blok yüksekliğinde yatay
slab çizgileri ve bazı üçgen köşe izleri bildirdi. Sorun waterlogging değildi:
iki kesirli yükseklik `round(height)` sınırının farklı tarafında kalınca iki
bottom slab ardışık Minecraft Y katmanlarına yerleşebiliyordu.

- [x] Alt kontur hücresini yüksek, aynı su altı yüzeyine bakan upright
  straight/inner/outer stair ile yarım blokta bağla.
- [x] Su altı tabanını kuru bankaya doğru ramp yapma; snapshot yüksekliğiyle
  birlikte `Dimension` su bağlamını da koru.
- [x] Tek stair ile ifade edilemeyen karşılıklı çapraz köşelerde sabit sıra
  yanlılığıyla rastgele üçgen üretme; mevcut simetrik slabı koru.
- [x] Nehirdeki rastgele dirt/granite karışımının bir blokluk ıslak kot
  geçişini kesmesini önle; geçişin iki ıslak tarafını granite/detail yap.
- [x] Dört yön, diagonal köşe, chunk 15/16 ve tile 127/128 sınırı,
  waterlogged state, solid support ve gerçek Carver→chunk export testleri.
- [x] İlgili odaklı birim ve gerçek export test grubu hatasız geçti.
- [x] Son kaynakla bütün regresyonları çalıştır ve Java 21 uygulama paketini üret.
- [x] Profil/dünya/brush yedeğini hash ile doğrula ve paketi aktif kuruluma geçir.
- [x] Güncel exe açılışını ve boş dünya başlangıcını logdan doğrula.
- [ ] Kullanıcının aynı Minecraft exportunda görsel A/B kabul testi.

Vanilla stair/slab geometrisi en fazla yarım blok çözünürlük sunar; gerçek bir
eğri oluşturamaz. Bu düzeltme tam blokluk kontur dudaklarını yarım basamağa
indirir ve rastgele üçgenleri kaldırır. Su seviyesinin kendi tam blok kot
değişimleri taban geometri düzeltmesinden ayrı bir konudur.

### Regresyon ve aktif kurulum — su altı kontur sürümü

- 48 sınıfta **421 Java testi**, **61 Node testi** ve **12 JavaScript
  sözdizimi kontrolü** geçti; hata, atlama veya başarısızlık yoktur.
- 354 dosyalık Java 21 uygulama paketi doğrulandı ve aktif kuruldu.
- Paket/test kayıtları:
  `C:\Users\Admin\Documents\WorldPainter-river-smoothbed-20260903-205000`
- Kurulum öncesi yedek:
  `C:\Users\Admin\Documents\WorldPainter-river-smoothbed-backup-20260903-205000`
- Aktif JAR SHA-256:
  `DED000C46AD0563C8B1DB6DC342E01E068D5D8237AC0E2A951C4D4F701F8227D`
- Yedekte 1011 dosya, 363 custom brush ve 3 `.world` vardır. Kurulumdan
  sonra 657 profil dosyasının hash'i yeniden karşılaştırıldı; eksik, yeni veya
  değişmiş kullanıcı dosyası yoktur. RAM ayarı 6 GB olarak korunmuştur.
- Masaüstü `WorldPainter V2` kısayolu güncel exe'yi göstermektedir.
- Güncel exe başlatıldı; `20260903213911` sürüm kaydıyla dünya yüklemeden
  açıldığı doğrulandı. JIDE bilgi penceresi monitör yakalama sınırının dışında
  kaldığı için otomasyonla kör tıklanmadı; uygulama kullanıcıya açık bırakıldı.

## Mud-brick su seviyesi kıyı şeridi — 4 Eylül 2026

- [x] Yalnız gerçek kuru banka komşu olan en dış ıslak nehir hücresini seç.
- [x] Kuru grass/dirt bankının yüksekliğini, materyalini ve su seviyesini değiştirme.
- [x] Düz kıyıyı waterlogged bottom `mud_brick_slab`, eğimli kıyıyı aynı
  yön/köşe geometrisini koruyan waterlogged `mud_brick_stairs` olarak export et.
- [x] Önce doğrulanmış granite 2x2x2 geometrisini hesapla, sonra yalnız blok
  ailesini değiştir; rounded-contour stair köprüsünü tekrar slab'a dönüştürme.
- [x] Bir blokluk ıslak kot köprüsü full/stair gerektiriyorsa zorla slab yapma.
- [x] 1,5 bloktan yüksek gerçek kayalık duvarların dibinde mud-brick şeridi üretme.
- [x] Kuru, lava, iç yatak ve eski/stale detail işaretlerini güvenli biçimde yok say.
- [x] Dört yön, chunk 15/16, tile 127/128, gerçek carver ve global smoothing
  kapalı/açık export testlerini çalıştır.
- [x] 48 sınıfta **426 Java testi**, **61 Node testi** ve **12 JavaScript
  sözdizimi kontrolü** geçti; hata veya atlama yoktur.
- [x] Java 21 paketi doğrulandı, yedekli kuruldu; 354 dosya ve 100 değişen
  class/script girdisi pakette doğrulandı.
- [x] Güncel exe başlatıldı; JIDE bilgi penceresi normal kapatıldı, WorldPainter
  ana penceresi doğrulandı ve hiçbir dünya veya script otomatik yüklenmedi.
- [ ] Kullanıcının yeni oluşturulmuş nehri Minecraft içinde görsel olarak kabul etmesi.

Aktif kurulum:

- Exe: `C:\Users\Admin\Documents\WorldPainter-v2-updated\dist\WorldPainter v2\WorldPainter v2.exe`
- Paket/testler: `C:\Users\Admin\Documents\WorldPainter-river-mudshore-20260903-220800`
- Yedek: `C:\Users\Admin\Documents\WorldPainter-river-mudshore-backup-20260903-220800`
- Aktif JAR SHA-256:
  `88A8869ACBD50E0B1C88E78945F3706836534E73111EF05D99639EFFB380DD05`
- Yedekte 1011 dosya, 363 custom brush ve 3 `.world` vardır. Kurulumdan
  sonra 657 profil dosyasının hash'i yeniden karşılaştırıldı; eksik, yeni veya
  değişmiş kullanıcı dosyası yoktur. RAM 6 GB ve masaüstü kısayolu korunmuştur.

## Nehir çizim fırçası — 4 Eylül 2026

- [x] İkinci modu kullanıcıya `Çizim fırçası` olarak göster; eski kayıtlı mod
  numarası ve `river_from_line` script sözleşmesi geriye uyumlu kalsın.
- [x] `Nehir çizim fırçasını aç` düğmesiyle `River Path` BIT katmanını oluştur
  veya yeniden kullan.
- [x] Katman seçimiyle birlikte Pencil aracını ve radius 0 tek blokluk merkez
  hattını otomatik etkinleştir; son nehir genişliğini hazır stil belirlesin.
- [x] Serbest sürükleme, Shift ile düz parça, sağ tuşla silme ve çizimden sonra
  yeniden `Nehir → Uygula` akışını arayüz içinde açıkça anlat.
- [x] Çizilmiş hattı mevcut sığ, araziyi koruyan carver ve granite/mud-brick
  export zincirine bağla; otomatik ve kaynak modlarını değiştirme.
- [x] Java 21 ile 22 odaklı nehir/arayüz testi ve tüm reaktörde 506 test
  çalıştırıldı: 504 geçti, 2 mevcut koşullu test atlandı; hata yoktur.
- [ ] Güncel exe paketini aktif kuruluma geçirip çizim fırçasını arayüzde açılış
  testiyle doğrula.

Depodaki `D8FlowEngine` birikim döndürür fakat gerekli ebeveyn/terminal,
korunan alan ve mevcut su davranışlarını sağlamaz; doğrudan bağlanmamalıdır.
Araştırma kodu veya bağımlılığı kopyalanmadı. Değişiklik yalnız eşik artırımı
değil, bu karşılaştırılabilir drenaj adayları üzerinden değerlendirilir.
