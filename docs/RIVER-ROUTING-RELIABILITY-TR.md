# Nehir yönlendirme güvenilirliği

> **Önceki paket kaydıdır.** Bu belgedeki 360 Java / 47 Node testi,
> kurulum bilgileri ve `5A65E3E68160FA12D34C02B768BEA20F76DE2A8CC305AD099AD4B1E803601BA6`
> JAR SHA-256 özeti önceki pakete aittir; yeni değişikliklerin güncel sonucu değildir.
> Doğal kıvrım, tam genişlikte ağız ve yerel granit detayı için
> [güncel değişiklik ve doğrulama notunu](RIVER-NATURAL-JUNCTIONS-TR.md) okuyun.

Tarih: 3 Eylül 2026

Bu çalışma, araziyi koruyan nehir modunda uygun bir yol bulunmasına rağmen
arama sınırı, kaba örnekleme veya su seviyesi hesabı nedeniyle verilen hatalı
“uygun rota bulunamadı” sonuçlarını azaltır. Amaç tek bir örnek dünyaya özel
çözüm değil; düz, eğimli, kıvrımlı, yüksek ve parçalı dünyalarda aynı güvenlik
kurallarıyla çalışabilen bir yönlendirmedir.

## Önceki paketin durumu

- **Tamamlandı:** 40 sınıfta 360 Java testi ve 47 Node script testi geçti;
  hata ve atlanan test yok. 12 benzersiz scriptin sözdizimi de doğrulandı.
  Yeni testlerin 15'i yönlendirme/seçim, 6'sı yuvarlama/kıyı/export desteğidir.
- **Ölçüldü:** Sentetik S-vadisinde elle kaynak seçimi ve otomatik seçim;
  ayrıca 8192 × 128 blokluk şeritte uzun rota planlama.
- **Tamamlandı:** Java 21 derlemesi, Windows app-image ve yedekli aktif kurulum.
  354 dosya, 87 class/script girdisi ve 4 plugin sağlayıcısı doğrulandı.
  Profildeki 14 script menü girdisi paketle eşleştirildi; hiçbir script çalıştırılmadı.
- **Bekliyor:** Uygulama 17:08:42'de başladı; JIDE açılış uyarısı nedeniyle
  ana pencere doğrulaması kullanıcı tarafından uyarının kapatılmasını bekliyor.
- **Bekliyor:** Minecraft içinde görsel değerlendirme ve su güncellemeleriyle
  oynanış testi. Chunk üretimi testleri bunun yerine geçmez.

Önceki kurulumdaki JAR SHA-256: `5A65E3E68160FA12D34C02B768BEA20F76DE2A8CC305AD099AD4B1E803601BA6`.
Exe: `C:\Users\Admin\Documents\WorldPainter-v2-updated\dist\WorldPainter v2\WorldPainter v2.exe`.
Masaüstü: `C:\Users\Admin\Desktop\WorldPainter V2.lnk`.
Temel commit: `fb893270013f388926d3cf336c1012cc7936cd2f`; düzeltmeler yereldir, GitHub'a gönderilmedi.
RAM ayarı `-Xms512m -Xmx6g` korundu.

Yedek: `C:\Users\Admin\Documents\WorldPainter-river-routing-backup-20260903-170350`.
994 dosya (uygulama + profil), 363 brush ve 3 `.world` otomatik kayıt yedeği
SHA-256 ile doğrulandı. Script önbelleği dışındaki 626 profil dosyası
kurulumdan sonra, uygulama açılmadan önce birebir aynıydı.
Paket/test/kurulum kayıtları:
`C:\Users\Admin\Documents\WorldPainter-river-routing-20260903-170350`.

## Araştırmadan alınan yaklaşım

GRASS `r.watershed`, drenaj yönü ve akış birikimi gibi çıktılar üretirken
en düşük maliyetli arama yaklaşımından yararlanır. Bunun önemli ayrımı,
arazi üzerinde bir akış yolu hesaplamanın yükseklik haritasındaki bütün
çukurları doldurmakla aynı işlem olmamasıdır. Düz ve düşük eğimli bölgeler
yalnız “her adımda kesin daha alçak komşu” kuralıyla ele alınmamalıdır.
Sıfır ve negatif yükseklikler de eksik veri sayılmaz.
[Resmî r.watershed belgesi](https://grass.osgeo.org/grass85/manuals/r.watershed.html).

GRASS `r.drain`, yükseklik yüzeyinde yerel inişi; maliyet yüzeyinde ise buna
eşlik eden hareket yönlerini kullanarak yol izlemeyi ayırır. Bu ayrım,
“aynı koordinata ulaşıldı” bilgisinin tek başına yeterli olmayabileceğini
ve seçilen yolun hareket koşullarının da saklanması gerektiğini anlamak
için yararlıdır. Sınır veya eksik veri, serbestçe geçilebilecek arazi değildir.
[Resmî r.drain belgesi](https://grass.osgeo.org/grass85/manuals/r.drain.html).

WorldPainter uygulaması bağımsızdır. Bu kaynaklardan kod aktarılmadı;
GRASS bağımlılığı eklenmedi. Buradaki yönlendirme, GRASS'ın hidroloji
modelinin bir uyarlaması veya tam bir yağış/erozyon simülasyonu değildir.
Araştırma; araziye göre maliyet, yön bilgisi, alternatif arama ve eksik
veriyi doğru ele alma konusunda mimari dayanak sağladı.

## Değişen yönlendirme

Uygulama ağırlıklı olarak
[ShallowRiverRouter](../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/tools/scripts/ShallowRiverRouter.java)
ile
[ShallowRiverCarver](../WorldPainter/WPCore/src/main/java/org/pepsoft/worldpainter/tools/scripts/ShallowRiverCarver.java)
arasında ayrılır: router yolu arar, carver tam geometrinin güvenliğini
doğrular. Router dünya verisini yazmaz ve `apply()` çağırmaz.

1. **Gerçekte bağlı alanın sınırlarını kullanır.** Birbirinden uzak tile
   grupları tek bir dev dikdörtgen gibi örneklenmez. Elle seçilen kaynak
   kendi bağlı alanında aranır; kaynak sessizce başka yere taşınmaz.
   Otomatik seçim kullanılabilir diğer bağlı alanları da değerlendirebilir.
   Aradaki eksik tile'lar oluşturulmaz veya üzerinden köprü varsayılmaz.

2. **Kaynak ve çıkış alternatiflerini çeşitlendirir.** Otomatik kaynaklar
   farklı yükseklik bantlarından ve mekânsal olarak ayrı yerlerden seçilir;
   başarısız kaynaklar da bu ayrımda hesaba katılır. Böylece bütün denemeler
   aynı uygunsuz tepeye harcanmaz. Mevcut göl/deniz çıkışı önceliklidir.
   Güvenli kısa kıyı adayları hemen uygulanmak yerine geçici tutulur; daha
   anlamlı uzun rota bulunamazsa yeniden doğrulanarak son seçenek olarak alınır.
   Su çıkışı uygun değilse dünya kenarına güvenli mesafedeki kuru çıkışlar
   da denenebilir. Küçük/düz dünyaları dışlayan sabit 300 blok uzunluk veya
   denizden en az 8 blok yüksek olma şartı kullanılmaz.

3. **Doğrudan aday yalnız güvenli bir hızlı yoldur.** Vadinin yönüne uyan
   bir doğrudan bağlantı önce denenebilir. Bu, düz dünyada aynı maliyetli
   binlerce hücreyi arama zorunluluğunu azaltır. Doğrudan bağlantı da gerçek
   genişliği ve tüm carver denetimlerini geçmek zorundadır; doğal sırtı
   kesen bir kestirme zorla kabul edilmez.

4. **Arama durumu su seviyesi ve geliş yönünü içerir.** Araziyi koruyan
   aramada etiket `(hücre, su seviyesi, geliş yönü)` olarak tutulur.
   Aynı hücreye farklı su seviyesinde veya farklı yönden gelen alternatif
   yol, yalnız koordinatı aynı diye kaybolmaz. Değiştirilemez önceki-etiket
   bağlantıları, daha sonra bulunan bir durumun mevcut yol zincirini
   bozmasını önler. Arama maliyeti arazi, yanal eğim ve yön değişimini
   dikkate alır; güvenlik koşulları maliyet cezasıyla geçersiz kılınamaz.

5. **Tam aday ve gerçek genişlik tekrar değerlendirilir.** İlk aramada
   genişliğin kuş uçuşu kalan mesafeden erken büyütülmesi, kıvrımlı vadide
   gerçekte geçebilen bir nehri reddedebiliyordu. Tam aday elde edildiğinde
   kendi yol uzunluğu üzerinden genişleme değerlendirilir; gerekli durumda
   bu bilgiyle sınırlı bir iyileştirme araması yapılır. Son karar her zaman
   carver'ın gerçek yol ve genişlik profiliyle yaptığı doğrulamadır.

6. **Reddedilen adayın geçici verisi ortak plana taşınmaz.** Her aday önce
   aynı mod ve geometri ayarlarına sahip ayrı bir carver üzerinde, dünyaya
   uygulanmadan denenir. Yalnız kabul edilen yol ortak plana eklenir.
   Böylece başarısız denemelerin hücre kopyaları ortak planda birikmez.

## Arazi ve su güvenliği

Bu çalışma **araziyi koruma** modunu sürdürür; eski, ayrı arazi uyarlama
modunu kendiliğinden etkinleştirmez.

- Yalnız gerçek ıslak kanal hücreleri kazılabilir. Kuru kıyılar, dış
  yamaçlar ve uç noktaların çevresi düzleştirilmez veya yükseltilmez.
- Dolgu sınırı sıfırdır. Toplam kazı sınırı seçilen derinlik + 0,75 bloktur;
  testlerdeki 1,10 blok hedef derinlik için sınır 1,85 bloktur.
- Hedef derinlik her hücreyi mutlaka aynı miktarda kazma zorunluluğu
  değildir. Araziye uymak için yatak sığlaşabilir; garanti edilen ıslak iç
  kesitte 0,65 blok alt sınırı, yükseklik saklama hassasiyetiyle denetlenir.
- En az üç sütunluk sürekli ıslak iç koridor kontrolü korunur. Sonuç,
  yalnız diyagonal temas eden su noktalarından oluşan bir çizgi sayılamaz.
- Mevcut göl/denizin gerçek su seviyesi ağız koşuludur; mevcut su
  sütunlarının yüksekliği veya tabanı nehre uydurulmak için değiştirilmez.
- Korunan alan, seçili kaçınma katmanı, lav, eksik arazi ve uyumsuz eski
  nehir katmanı kontrolleri atlanmaz. Planlama sırasında kullanıcı dünyası
  değişmez; başarısız arama yarım kazılmış arazi bırakmaz.
- Uygulama ve geri alma çağıran işlem katmanında yapılır. Router'ın
  iptal/arama sonucu, kendi başına bir dünya kaydetme veya export işlemi
  başlatmaz.

### Kesirli yükseklik ve gerçek export bloğu

Yüksekliği aşağı yuvarlamak, henüz export edilmemiş kesirli bir yüzeyde
su seviyesini gereksiz yere bir blok düşürüp fazladan kazı gerektiriyordu.
Örneğin 99,9 yükseklikteki kuru kıyı, export yüzeyinde 100 seviyesindeki
tam bloğu sağlayabilir. Bunu doğrudan 99 kabul etmek güvenli bir kanalı
yanlışlıkla reddedebilir.

Su tavanı hesabı artık export yüzey yüksekliğiyle uyumlu `Math.round`
hesabını kullanır. Bu değişiklik **kıyı kontrolünün yerine geçmez**:
gerçekten alçak kıyı hâlâ reddedilir; suya bakan kuru granit bloğunun tam
blok olması, ıslak slab/stair desteği ve waterlogged durumu gerçek chunk
üretimiyle ayrıca denetlenir. Dört odaklı test kesirli kesiti, negatif
yükseklik/koordinatı, yetersiz kıyının reddini ve export desteğini kapsar.

Kesirli eğimde bir sonraki kuru komşu suyu desteklemiyorsa yalnız o kesitin
su tavanı indirilir; kuru komşu yükseltilmez. Bütün sorunlar tur başına topluca
hesaplanır, aşağı-akış profiline yayılır ve gerçek yatak tekrar hesaplanır.
En çok dört turdan sonra aynı minimum genişlik/derinlik ve kıyı kontrolleri
yeniden geçmelidir. Önceden kabul edilen başka rota ve deniz seviyesi değişmez.

## Sınırlı iş ve tanılama

Arama sınırsız değildir. Bu sürümdeki başlıca üst sınırlar:

| Kaynak | Sınır |
| --- | ---: |
| Bir bağlı alanın kaba arama ızgarası | 65.536 hücre |
| Arazi örneği önbelleği | 65.536 kayıt |
| Tek aramada genişletilen düğüm | 12.000 |
| Toplam genişletilen düğüm | 80.000 |
| Kenar/kesit örnekleme bütçesi | 2.000.000 |
| Tam aday denemesi | 24 |
| Bir bağlı alandaki otomatik kaynak denemesi | 32 |
| Kaynak başına çıkış denemesi | 8 |
| Router yol noktası | 20.000 |
| Bir adayın kaçınma-katmanı alan denetimi | 350.000 hücre |

Bir sınır diğerinden önce aramayı durdurabilir; bunlar toplam bellek
kullanımını veya bütün bir dünyanın işlem süresini tek başına ifade etmez.
Otomatik aramada iş bütçesi alternatif kaynaklar arasında paylaştırılır.
Su/yön etiketlerinin getirdiği ek arama için örnekleme tavanı eski 500.000'den
2.000.000'a çıkarıldı; tek kaynağa bütçenin sekizde birini vermek yerine
32 kaynak arasında paylaştırıldı. Yalnız bütçeyi artırmak yeterli değildi:
aynı gerçek yedekte eşit kaynak paylaşımı olmadan yine rota bulunamıyordu.
Doğrudan yol taraması da aynı toplam bütçeyi tüketir. Bellek önbelleği ve
düğüm sınırları büyütülmedi.
İptal kontrolleri örnekleme, bağlı alan keşfi ve arama sırasında sürer.

Özet; kabul ve aday sayısını, yol araması sayısını, incelenen arazi
örneklerini/düğümleri, son yol uzunluğunu, hedef derinliği, red nedeni
dağılımını ve son adayın koordinatlarıyla son somut red nedenini içerir.
`isSearchLimited()` ve `getFailureReason()` şu iki sonucu ayırır:

- Denenen adaylar kazı, genişlik, su veya koruma koşullarını karşılamadı.
- Arama sınırına ulaşıldı; kaynak/çıkış alternatiflerinin tamamı incelenemedi.

İkinci sonuç “bu dünyada nehir yapılamaz” kanıtı değildir. Bütün dünyalar
için tek bir başarı garantisi vermek yerine hangi denetimin veya bütçenin
durmaya yol açtığı gösterilir.

## Doğrulanan dünya türleri

Pozitif uzun-rota testlerinde önce bilinen yolun gerçek carver tarafından
kabul edildiği doğrulanır. Böylece fiziksel olarak olanaksız bir fixture'ın
router başarısı gibi istenmesi önlenir. Sonra router bağımsız olarak
çalıştırılır. Sentetik test dünyalarında uygulama sonrası kaynak–çıkış
bağlantısı, anlamlı uzunluk/yükseklik kaybı, kazı sınırı, sıfır dolgu,
değişmeyen kuru alanlar ve mevcut deniz kontrol edilir.

| Test grubu | Geçen test | Kapsam |
| --- | ---: | --- |
| [RouterReliabilityTest](../WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/tools/scripts/RouterReliabilityTest.java) | 7 | Denizsiz düz dünya; 256/512/1024 blokluk vadilerde 12/36/110 Y iniş; çapraz vadi; negatif koordinat ve uzak kopuk tile; korunan çıkışın güvenli reddi |
| [RouterWindingWorldTest](../WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/tools/scripts/RouterWindingWorldTest.java) | 5 | Doğal sırt nedeniyle doğrudan kestirmenin uygun olmadığı S-vadisi; aynı dünyada otomatik anlamlı rota; 8192 × 128 şerit; negatif su seviyesi; 1000 Y üzerindeki yüksek dünya |
| [RouteSelectionSafetyTest](../WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/tools/scripts/RouteSelectionSafetyTest.java) | 3 | Uzak bağlı alandaki elle seçilmiş kaynağı koruma; korunan büyük alan yerine uygun küçük alan; tile eklenme sırasından bağımsızlık; bağlı alan keşfinde iptal |
| [PreservingRiverQuantizationTest](../WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/exporting/PreservingRiverQuantizationTest.java) | 4 | Kesirli kıyıdaki yanlış reddi giderme; negatif koordinat/yükseklik; gerçek alçak kıyıyı reddetme; tam kuru granit kıyı ve destekli ıslak slab/stair exportu |
| [PreservingRiverBankSupportTest](../WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/exporting/PreservingRiverBankSupportTest.java) | 2 | Çapraz inişte kuru komşu desteği ve gerçek granit exportu; 100'den fazla Y basamağında yerel su tavanı düzeltmesi |

Önceki sürümde bilinen carver-güvenli yol bulunmasına rağmen denizsiz düz
dünya ve 1024 blokta 110 Y inen vadi örnekleri arama bütçesine takılıyordu.
Bu odaklı testlerin tamamı yeni yönlendirmeyle geçti.

### Yerel planlama ölçümleri

Aşağıdaki değerler aynı bilgisayardaki bir test çalıştırmasının
örnekleridir. Süre yalnız router'ın arama/planlama çağrısını ölçer; dünya
oluşturma, bilinen yolun ön doğrulaması, uygulama ve export dahil değildir.
Testler belirli bir milisaniye değerine bağlı kırılgan hız şartı koymaz.

| Dünya ve seçim | Bulunan yol | Y inişi | Planlama | En büyük kazı |
| --- | ---: | ---: | ---: | ---: |
| S-vadisi, elle kaynak | 738,3 blok | 48 | 0,159 sn | 1,746 blok |
| S-vadisi, otomatik kaynak | 749,5 blok | 48 | 0,275 sn | 1,750 blok |
| 8192 × 128 şerit, elle kaynak | 8130 blok | 160 | 0,924 sn | 1,5938 blok |

Bu örneklerde dolgu sıfırdır. 8192 × 128 şerit 64 tile içerir;
**8192 × 8192 dünya testi değildir**. Bu ölçümlerden tam 8K² dünya süresi,
tepe RAM kullanımı, render FPS'i veya export hızı çıkarılamaz.

Son 360-test koşusunda ayrıca kişisel dünyanın tarihli yedeği yalnız okunarak
test edildi: 116,0 blok, kaynak Y=11,57 → deniz Y=0, 2,031 saniye planlama.
Bu, daha uzun bir nehir bulunamadığında doğrulanmış kısa adaya dönülen bir
örnektir; büyük dağ nehri başarısı gibi sunulmaz. Dünya/dimension sayaçları,
7.225 arazi örneğinin özeti ve dosya SHA-256'sı değişmedi; plan uygulanmadı.

## Sınırlar ve kalan doğrulama

- Tam 8K² dünyada farklı kaynak yoğunluklarıyla süre ve tepe bellek
  ölçümü ayrıca yapılmalıdır.
- Çok dar vadide seçilen genişlik, aşırı yanal eğim, aşılması gereken
  yüksek sırt, korunan geçit veya uygunsuz su seviyesi güvenli bir nehri
  gerçekten engelleyebilir. Bu sürüm çözüm bulmak için kazı sınırını
  büyütmez, dolgu açmaz veya çevrede basamaklı düzlükler oluşturmaz.
- Kaba örnekleme ve sınırlı arama her olası güzergâhı incelemez. Geçen
  sentetik testler genel kapsama katkıdır; her dünya için başarı kanıtı
  veya küresel olarak en iyi güzergâh garantisi değildir.
- Chunk testleri blok desteğini ve su/kıyı ilişkisini denetler. Minecraft
  içindeki su güncellemeleri, kıvrım görünümü, materyal dağılımı ve
  kullanıcıya göre doğallık ayrıca değerlendirilmelidir.
- Ana uygulama/JIDE uyarısı ve Minecraft oyun içi değerlendirmesi tamamlanınca
  durum bölümü güncellenmelidir.
