# Nehirlerde doğal kıvrım, tam genişlikte ağız ve yerel granit detayı

Tarih: 3 Eylül 2026

Bu not, [önceki yönlendirme güvenilirliği paketinden](RIVER-ROUTING-RELIABILITY-TR.md)
sonraki yerel değişiklikleri anlatır. Önceki belgedeki 360 Java / 47 Node testi
ve kurulu JAR özeti bu değişikliklerin test veya kurulum sonucu değildir.

## Güncel durum

**Sonraki dağ nehri paketi kuruldu:** 19:22 uyarlamalı paket kurulmadı; onun
93 blokluk kıyı rotası dağ hedefi için yeterli değildir. Sonraki 19:55 paketi
yüksek kaynak, gerçek alçalma ve ilk suya kadar yeni yatak kontrollerini
içerir. 396 Java/61 Node testi geçti; fakat eski nehirli ayrı yedekte hâlâ rota
bulunamadı. Aşağıdaki test, kurulum ve hash bilgileri eski 17:37 aşamasına aittir.
Güncel dağ nehri ölçütleri ve yapılacaklar: [Dağdan vadiye nehir seçimi](RIVER-MOUNTAIN-COURSES-TR.md).

- **Tamamlandı:** ağız birleşimi, güvenli doğal kıvrım ve yalnız nehir granitini
  etkileyen export detayı; 44 sınıfta 389 Java testi geçti (0 hata, 0 atlanan).
- **Geçti:** 61 Node testi; 12 benzersiz JavaScript dosyasında sözdizimi denetimi.
- **Tamamlandı:** Java 21 derlemesi. 110 Y dağ vadisi, sırtı dolaşan rota,
  sığ kaynak, birleşim genişliği, granit exportu ve damlalık filtresi doğrulandı.
- **Tamamlandı:** Windows app-image ve yedekli aktif kurulum. 354 dosya,
  94 class/script girdisi, 4 plugin ve 14 script menü girdisi doğrulandı.
- **Tamamlandı:** yeni exe başladı; JIDE sonrasında ana editör ve açık nehir
  script penceresi görsel olarak doğrulandı. İlk ekran yakalama hatası, güncel
  pencere tekrar seçilince aşıldı. Script çalıştırılmadı.
- **Bekliyor:** Minecraft içinde görsel inceleme ve su güncellemeleriyle test.

Aktif JAR SHA-256: `3B1AD32C8E061636780A389C19D102F70CA85EAC91CF8D0B679110D2284B7837`.
Exe: `C:\Users\Admin\Documents\WorldPainter-v2-updated\dist\WorldPainter v2\WorldPainter v2.exe`.
Kısayol: `C:\Users\Admin\Desktop\WorldPainter V2.lnk`.
Paket/test kayıtları: `C:\Users\Admin\Documents\WorldPainter-river-natural-20260903-173700`.
Yedek: `C:\Users\Admin\Documents\WorldPainter-river-natural-backup-20260903-173700`.
1009 dosya, 363 brush, 4 `.world` kaydı ve kısayol yedeklendi; dosya hash'leri
doğrulandı. Önbellek dışındaki 628 profil dosyası kurulum sonrası uygulama
açılmadan önce aynıydı. RAM ayarı 6 GB kaldı. Temel commit
`fb893270013f388926d3cf336c1012cc7936cd2f`; değişiklikler GitHub'a gönderilmedi.

## Değişiklikler

### 1. Nehir ağzı tek dar temas yerine bütün kesitiyle bağlanır

Mevcut göl, deniz veya uygun alıcı suya bağlanırken yalnız merkez çizgisinin
suya değmesi yeterli sayılmaz. Islak kanalın tam genişlikte, kesintisiz bir
ağız oluşturması denetlenir. Gerektiğinde ağız sınırlı miktarda uzatılır;
sınırsız kazı, dolgu veya çevreyi düzleştirme yapılmaz.

Birleşimde yeni planın ıslak hücreleri ile mevcut su birlikte değerlendirilir.
Yeni ıslak bir komşu yanlışlıkla kuru kıyı sayılarak kanalın daralmasına neden
olmamalıdır. Aynı seviyedeki derin alıcı su, nehrin sığ yatak hedefinden daha
derin diye tek başına reddedilmez; alıcı suyun yatağı ve su seviyesi korunur.

Uzatılan ağız da korunan alan, eksik arazi, lav ve seçili kaçınma katmanı
denetimlerinden geçer. Kaynak/otomatik scripti, Router'a verdiği aynı kaçınma
maskesiyle **ortak planın son gerçek ıslak alanını** uygulamadan hemen önce
yeniden kontrol eder. Nokta yolu modunda özel kaçınma maskesi yoktur; yerleşik
arazi korumaları yine kontrol edilir. Denetim başarısızsa plan uygulanmaz.

### 2. Otomatik rotaya yalnız güvenli küçük kıvrımlar eklenir

Önce değiştirilmemiş rota bütün güvenlik denetimlerini geçmelidir. Daha doğal
bir çizgi için bundan sonra en fazla **3 blok yanal sapmalı** yumuşak bir aday
denenir (blok koordinatına yuvarlama en fazla 0,71 blok ekleyebilir). Aday;
gerçek kanal genişliği, kıyı, su, kazı ve kaçınma alanı dahil
tam geometri denetiminden geçmeden kabul edilmez.

Doğallaştırma güvenli değilse zaten doğrulanmış ham rotaya dönülür. Estetik
iyileştirme, çalışabilen bir nehri kaybetme veya arazi korumasını gevşetme
gerekçesi değildir. **Kullanıcının Nokta yolu koordinatları bu işlemle
taşınmaz.** Bu değişiklik bir erozyon ya da tam hidroloji simülasyonu değildir.

Kıyı seçiminde rastgele fark artık yalnız eşit maliyetleri sıralar; vadinin
uygun ağzını denemeden aynı bölgedeki yamaçla elemez. Doğrulanmış rota küçük
bir sırtı tırmanıyorsa, en fazla 650.000 ek örnek ve Router'ın ortak çalışma
bütçesi içinde daha düşük maliyetli vadi aranır. Arama tamamlanamazsa güvenli
ilk aday korunur. Vadi ve görünüm düzeltmeleri kaynaktaki doğrulanmış su
seviyesini değiştiremez.

### 2.1. Otomatik arama artık tek kısa turda vazgeçmez

Otomatik mod aynı güvenlik planında en fazla altı tur çalışır. İlk tur geniş
aralıklı kaynakları, ikinci tur daha sık kaynakları, sonraki turlar ise kalan
16 blok aralıklı kaynak bölgelerini dener. Bir Router planının mutlak sınırları
192 tam aday, 360.000 arama düğümü ve 24 milyon kesit örneğidir; her döngüde
iptal denetimi sürer. Bu sınırlar arayüzü sonsuza kadar kilitlemeden eski
48 aday / 8 milyon örnek sınırına göre çok daha fazla farklı rota dener.

Aynı 24×24 kıyı alanındaki farklı su seviyeleri artık tek bir çıkış sayılmaz.
İlk dar veya yanlış seviyeli kıyı örneği reddedilse de aynı bölgedeki başka bir
su seviyesinin güvenli, tam genişlikli ağzı denenebilir. Son karar yine Carver'ın
tam genişlik, su seviyesi, korunan alan ve tek blokluk boğaz kontrollerinindir.

İstenen tam genişlikte hiçbir güvenli plan bulunmazsa yalnız otomatik mod,
çevre araziyi doldurmak veya düzleştirmek yerine ıslak yatağın bitiş genişliğini
önce yaklaşık üçte ikiye, gerekirse kaynak genişliğine kadar indirerek yeniden
arar. Örneğin **Doğal Nehir** önce 5–12, sonra 5–8, son olarak 5–5 blok dener.
Başlangıç genişliği hiçbir zaman azaltılmaz; 1 blokluk kanal üretilmez. Kazı
sınırı 1,85 blok, dolgu 0, korumalar ve mevcut su seviyesi kuralları her geçişte
aynıdır. Uygulanan gerçek genişlik script çıktısında açıkça yazılır.

**Dağ hedefinden önceki tarihsel test:** 3 Eylül gerçek dünya yedeğinde 5–12 plan güvenli rota bulamadı; 5–8 uyarlaması
ikinci turda bir rota buldu. Planlama 35 saniye sürdü. Test boyunca `.world`
dosyasının SHA-256 değeri, dünya/tile değişiklik sayaçları ve örneklenen arazi
özeti değişmedi. Bu, aramanın kabul ve tek uygulama adımından önce salt okunur
olduğunu doğrular. Ancak bu 93 blokluk kıyı sonucu dağ nehri kabulü değildir;
bu testi içeren 19:22 paketi kurulmamıştır.

### 3. Granit detayı artık dünya geneli ayarı açılmadan çalışabilir

Yeni oluşturulan uygun nehir graniti `RiverSurfaceDetail` işareti alır.
Exportta yerel slab/merdiven adayı olmak için işaretin yanında hücrenin
gerçekten ıslak ve terrain'inin granit olması da gerekir. Bu yol, dünya geneli
`SurfaceSmoothing.NONE` olsa da çalışır; genel ayar değiştirilmez.

“2×2×2 detay” her granit bloğun mutlaka merdiven olacağı anlamına gelmez.
Düz yüzeyler, dik uçurumlar ve gerekli taşıyıcı bloklar tam kalabilir. Kuru
kıyı, çimen sınırı ve eski işaretsiz nehirler sırf bu özellik eklendi diye
yumuşatılmaz. Gelişmiş scriptte yerel granit seçeneğinin açıkça kapatılması
korunur; hazır Nehir penceresi bu seçeneği açık gönderir.

İşaret tile verisidir: geri alma ve `.world` kaydetme/yükleme testleri geçti.
Sistem katmanı olarak damlalık ve bilgi panelindeki seçilebilir katmanlardan
çıkarılır; bu filtre de test edildi. Dünya kapsayıcı biçimi
değişmese de **yeni işareti içeren dünyaları açmak için güncel WorldPainter
V2 gerekir**; eski sürümle uyumluluk varsayılmamalıdır. Genel export ayarını
değiştirip bunu tile geri almasının parçasıymış gibi sunan bir geçiş yoktur.

## Kullanıcı için güvenli deneme

Mevcut nehirler kendiliğinden onarılmaz. Daha önce bozulmuş nehrin üzerine
yeniden uygulamak yerine, **nehir yapılmadan önceki temiz dünya kopyasını**
açıp ayrı bir dosya olarak saklayın. Güncel paket doğrulandıktan sonra küçük
bir alanda yeni nehir üretin ve ayrı test exportunda sonucu inceleyin.
Kurulum veya dünya açılışı sırasında otomatik script çalıştırılmaz.

Minecraft su seviyeleri tam blok basamaklıdır. Kıvrımların ve ağızların
iyileştirilmesi bu sınırlamayı kaldırmaz; eğimli nehirlerde kot geçişleri
kalabilir. Amaç etrafta yapay geniş düzlükler oluşturmadan sürekliliği ve
birleşimi iyileştirmektir; her arazide tamamen basamaksız su vaadi yoktur.

## Kabul kontrol listesi

- [x] 61 Node testi ve 12 script sözdizimi denetimi.
- [x] Altı turlu genişletilmiş arama, farklı su seviyeli kıyı adayları ve
  5–12 → 5–8 güvenli genişlik uyarlamasını gerçek yedek dünyada doğrula;
  planlama dünya dosyasını değiştirmesin.
- [x] 110 Y dağ vadisi regresyonunu gider; tam Java test grubunu yeniden geçir.
- [x] Düz/çapraz ağız, tam genişlik, ıslak/kuru birleşim ve aynı seviyeli derin
  alıcı su testlerini doğrula; alıcı su ve kuru çevre değişmesin.
- [x] Doğal kıvrımın güvenli adayda uygulanmasını ve uygunsuz adayda ham rotaya
  geri dönüşünü doğrula; elle konmuş noktalar korunmalı.
- [x] Son ağız alanında kaçınma/koruma ihlali ve iptal dünya değişikliği bırakmasın.
- [x] Genel ayar kapalıyken işaretli ıslak granitte yerel exportu; kuru/işaretsiz
  alanda değişmezliği, destek bloklarını ve waterlogged durumunu doğrula.
- [x] İşaretin geri alma ve kaydetme/yükleme testlerini geçir.
- [x] Java 21 derlemesi, Windows paketi ve yedekli kurulum; son JAR SHA-256 ve
  masaüstü kısayolunun çalıştırdığı paket doğrulansın.
- [ ] Temiz kopyadan yeniden üretilmiş nehirde Minecraft görsel/su testi yap.

Kurulum doğrulaması ile Minecraft içindeki görsel değerlendirme ayrı adımlardır;
oyun içi sonucu henüz doğrulanmış saymayın.
