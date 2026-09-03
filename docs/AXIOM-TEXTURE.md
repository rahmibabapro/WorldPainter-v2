# Axiom blueprint dağ, düzlük dokusu ve katmanlı kar

Bu işlem iki Axiom blueprintini okuyarak araziye dağ dokusu ve doğal düzlük karışımı uygular:

- `Downloads/dag.bp`: dağ/kaya ve beyaz zirve kaynağı;
- `Desktop/terrain.bp`: normal düzlükler için hafif dalgalı çimen–moss kaynağı.

Script Library → Global Operations → **Axiom Blueprint Dağ + Düzlük + Kar** tek
tıklamayla çalışır; texture görseli, renk paleti veya parametre formu gerektirmez.
İleri kullanımda kaynak yolları sırasıyla `worldpainter.v2.axiomTextureBlueprint`
ve `worldpainter.v2.axiomPlainsTextureBlueprint` Java özellikleriyle değiştirilebilir.

## Kaynak ölçümü

Kaynakta 6.052.506 hava dışı blok, 76 blok adı ve 190 block-state bulunuyor.
Bitkiler, yapraklar, sıvılar ve kısmi şekiller ayrıldığında bağımsız analizde
Diamond işaretleri ayıklandıktan sonra 127.672 zemin sütunu, 39 blok adı ve 44
block-state ölçüldü.
Tam analiz, yükseklik/eğim koşullu yüzdeler ve tekrarlanabilir ölçüm araçları:
[Blueprint analizi](../WorldPainter/tools/axiom-analysis/ANALYSIS.md).

Yan yana aynı zemin malzemesi görülme oranı kaynakta yaklaşık %66.
Aynı genel yüzdeler bağımsız rastgele dağıtılsa bu oran yalnızca %9,94 olur.
Dolayısıyla yalnızca blok yüzdelerini tutturmak kaynaktaki deseni üretmez.

`terrain.bp` ayrı ölçüldü: 62×93×33 ve 5.766 yapısal zemin sütunu vardır. Bu
işlem düz arazi için bu blueprintteki tam `grass_block` durumunu alır ve yalnızca
onu kullanır. Pale moss, moss, mossy cobblestone, slab ve diğer dekoratif
örnekler düz araziye bilinçli olarak dahil edilmez; düz alanın sonucu yalnızca
grass block'tur. Su, lava, kar, buz ve beyaz kar maskesi de bu kaynakla
üretilmez.

## Aktarım

- **Dağ:** Kaynaktan yönleri değiştirilmeden gerçek 16×16 yüzey parçaları seçilir.
- Kaynak ve hedefte yükseklik, gerçekten mevcut arazinin en düşük/en yüksek
  noktasına göre normalize edilir; Minecraft yapı yüksekliği kullanılmaz.
- Eğim yönü iki yöndeki merkezi farklardan, yerel çukur/sırt bilgisi komşu
  yüksekliklerden çıkarılır. Her aday parça 25 konumda karşılaştırılır.
- Dört blok örtüşen parça sınırları en düşük malzeme uyuşmazlığı boyunca birleştirilir.
- Kaynakta bulunmayan rastgele bir taş karışımı oluşturulmaz. Aktarılan tam
  blokların kimlikleri ve `axis` gibi özellikleri korunur.
- Otlar, yapraklar, merdivenler ve çitler arazi dolgusu olarak kullanılmaz.
- Kaynaktaki diamond block işaretleri de atlanır; aynı sütundaki ilk normal alt zemin kullanılır.
- Önce `dag.bp` bütün uygun kuru yüzeye uygulanır. Ardından `terrain.bp`, yalnızca
  normal düzlük hedeflerine ikinci geçişle uygulanır: Y=150'nin altında ve 30°'nin
  altında. Bu ikinci geçiş yalnızca grass block boyar; moss, cobblestone veya slab
  eklemez. Eğim 30° veya daha fazlaysa, yükseklikten bağımsız olarak yüzey taş
  olur. Böylece alçak kesimlerdeki dik yamaçlar da toprak olarak kalmaz.
- Dağ aktarımının belleği, kaynak profilinin yanında 16 satırlık bir çıktı
  şerididir; 8192 blok genişlikte 512 KiB'dır. Düzlük grass geçişi kaynak yaması,
  seam veya dünya boyutunda seçim maskesi oluşturmaz; yalnızca tile'ları tarar.
  Bu rakam tüm uygulamanın/dünyanın belleği değildir.

Eşleştirme maliyeti normalize yükseklik, iki eğim bileşeni, eğrilik ve örtüşme
uyuşmazlığını içerir. Farklı şekilli bir dağda aynı yerel koşullara uygun parçalar
seçildiği için bütün haritanın malzeme yüzdelerinin kaynakla eşit olması beklenmez.
İşlem günlüğü kaynak sayımlarını ve hedefte gerçekleşen sayımları ayrı raporlar.

## Beyaz bölgeler → gerçek kar katmanları

Kullanıcı tercihi gereği blueprintte ölçülen beyaz zirve blokları da gerçek yüzey
terrain'i olarak korunur. Kaynaktaki huş odunu, diorit, beyaz beton tozu ve açıkça
beyaz/kar/buz malzemeleri aynı zamanda kar maskesi olarak yorumlanır; uygun
yükseklikte bunların üstüne gerçek snow layer eklenir. Pale oak, pale moss ve gri
yün kaya geçişi olarak korunur.

| Hedef koşul | Davranış |
|---|---|
| Y < 150 | Beyaz yüzey ve yeni kar yok |
| Y ≥ 150, eğim < 30° | Kaynakta beyaz olan uygun yüzeylerde sürekli smooth snow |
| Y = 150–190 | Snow layer derinliği smoothstep ile yumuşak biçimde artar |
| Y ≥ 190 | Maskenin uygun, düz yüzeyleri yoğun karla kaplı |
| Dik yüzey (≥ 30°) | Beyaz yüzey ve kar yok; taş önceliklidir |
| Su/lav ve kıyı | Korunur |

Kar yüksekliği 1–8 katman olarak `Snow Depth` yardımcı verisinde saklanır;
görünürlüğü Frost sağlar. Export tam olarak `minecraft:snow[layers=1..8]` yazar.
Yeni `snow_block`, beyaz beton/diorit dolgusu veya buz icat etmez; yalnızca
blueprintte gerçekten ölçülen bu blokları terrain olarak taşır ve arazi yüksekliğini
değiştirmez. Kar maskesi sınırında kalınlık azaltılır. Açık kar kalınlığı sıfır
olan mevcut Frost alanlarının eski export davranışı korunur.

`terrain.bp` kar maskesi üretmez. Düzlük geçişinin yazdığı her hücrede önceki dağ
geçişinden kalmış Frost ve Snow Depth verisi temizlenir; ardından smooth-snow
yalnızca dağ blueprintinden kalan beyaz maskede çalışır.

## Moss filtresi

Dağ kaynağındaki `moss_block` ve `pale_moss_block` yalnızca Y=80–120 orta-dağ
bandında kalır. 4 blok ölçeğinde eğim 30°'ye ulaştığında moss tamamen reddedilir;
22–30° arasında yumuşakça azalır. Düzlükte moss, pale moss veya cobblestone
kullanılmaz; yalnızca grass block vardır. Böylece dik kaya yamacına düz arazi
karışımı sürülmez.

## İşlem güvenliği ve sınırlar

Dağ kaynağının gerekli block-state'leri ile tek native grass plains terrain'i aynı
Custom Terrain işlemi içinde, herhangi bir dünya değişikliğinden önce denetlenir.
Mevcut malzemeler adla değil tam block-state veya eşdeğer Mixed Material tarifiyle
yeniden kullanılır.
İptalde/hata durumunda hücreler ve yeni terrain tanımları geri yüklenir.
Başarılı işlemde arazi, Frost ve kar kalınlığı tek geri alma adımıdır;
oluşturulan terrain tanımları geri almadan sonra tekrar kullanılabilmek için palettedir.
Başarılı işlemin Frost genel ayarı da normal hücre geri almasına dahil değildir;
iptal/hata geri alması ise önceki Frost ayarını ayrıca geri yükler.

Bu bir **yüzey aktarımıdır**. Blueprintin mağaraları, çıkıntıları, ağaçları veya
her yüksekliğe ayrı boyanmış dik duvarları kopyalanmaz. WorldPainter'in bir
sütunda tek terrain saklaması nedeniyle tam üç boyutlu blok eşitliği iddia edilmez.

## Durum

- [x] Kaynak blok/state, koşullu oran ve mekânsal komşuluk analizi.
- [x] Gerçek parçalardan, eğime/yüksekliğe bağlı yüzey aktarımı.
- [x] Beyaz zirve bölgelerini ayrı kar maskesine dönüştürme.
- [x] Gerçek 1–8 snow layer export yolu, su/lav koruması.
- [x] `terrain.bp` içindeki tam grass block ile yalnızca-grass düz arazi yüzeyi;
  dik yamaçlarda taş önceliği.
- [x] Tek tuşlu menü ve bağımsız script giriş noktası.
- [x] Tam malzeme eşleme, ön kontrol ve iptal geri alma.
- [ ] Kullanıcının kendi 8K dünyasında Minecraft içi görsel kabul kontrolü.
- [ ] Gerekirse ayrı bir export katmanıyla dik duvarların yükseklik boyunca dokulanması.

## Doğrulama — 3 Eylül 2026

Java 21 ile 56 hedefli test geçti: 5 texture aktarımı, 5 smooth snow, 9 snow export,
3 kar-altı sağlam yüzey, 24 mevcut slab/stair yumuşatma, 7 terrain tahsisi ve
3 bütün işlem/geri alma/iptal testi. Karın altında slab oluşması engellenirken
diğer hücrelerin mevcut yumuşatma davranışı korunuyor.

Dağ+düzlük eklemesi için core ve GUI hedefli testler; filtreli 16×16 dağ ribbon
aktarımı, yalnızca-grass plains terrain'in tek referansla boyanması, 30° ve üzeri
eğimlerde taş önceliği, Y=150'den itibaren beyaz zirve/sürekli smooth snow,
Frost/Snow Depth temizliği, tek Undo'yu ve çift-kaynak işlem iptalinde tam geri
almayı kapsar.

Gerçek `dag.bp` ile iki 384×384 bellek içi test yapıldı. Dağ için gerçek yama
aktarımı çalıştı; düzlük için ise kaynak yaması taşımayan yalnızca-grass native
terrain kullanılır.

| Ölçüm | Aynı arazi şekli | Değiştirilmiş arazi şekli |
|---|---:|---:|
| Alt zemin dağılım farkı (total variation) | %1,66 | %4,06 |
| Referans alt zemin komşuluğu | %69,22 | %67,77 |
| Aktarılan alt zemin komşuluğu | %69,74 | %69,44 |
| Rastgele hücre kontrolü komşuluğu | %12,57 | %12,54 |
| Kaynak kar maskesi kesişim/birleşim oranı | %98,08 | %57,52 |
| Beyaz dolgu / bitki dolgusu / geçersiz kar | 0 / 0 / 0 | 0 / 0 / 0 |
| Değişen korunan hücre / arazi yüksekliği | 0 / 0 | 0 / 0 |

Değiştirilmiş arazide kar maskesinin piksel eşleşmesi tam değildir; bu yöntem
aynı malzeme istatistikleri ve bağlantılı yüzey parçalarını başka geometriye
uyarlar. Minecraft içinde görsel kabul kontrolü henüz yapılmadı. Bu küçük test
süreleri 8K uygulama/export süresi olarak yorumlanmamalıdır.

[Kar katmanlı karşılaştırma görseli](../WorldPainter/tools/axiom-analysis/verification-results/changed-relief-comparison.png)
ve [ölçüm çıktıları](../WorldPainter/tools/axiom-analysis/verification-results/changed-relief-metrics.json)
tekrar üretilebilir. Görsel, blok başına bir piksel olan tanılama çizimidir;
Minecraft ekran görüntüsü değildir.
