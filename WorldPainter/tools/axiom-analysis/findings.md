# Ölçüm bulguları: beyaz terrain + kar ve moss filtresi

`ANALYSIS.md` kaynak blueprintin gerçek ham materyal oranlarını verir. Güncel
uygulamada zirvedeki beyaz bloklar hem ölçülen terrain olarak taşınır hem de üstlerine
`minecraft:snow[layers=1..8]` ekleyen kar maskesini belirler. Böylece kar layer
seyrek kaldığı alanlarda da blueprintin açık zirve dokusu görünür.

## Beyaz adayların gerçek yüksekliği

Yükseklik blueprintin tabanından başlar; Minecraft dünya Y değeri değildir.

| Kaynak tam blok yüzeyi | Sayı | Min–maks yükseklik | Ortalama |
|---|---:|---:|---:|
| birch_wood | 4.213 | 42–138 | 104,58 |
| diorite | 3.006 | 40–137 | 106,63 |
| white_concrete_powder | 3.323 | 64–138 | 103,55 |
| snow_block | 4.793 | 43–138 | 82,12 |
| packed_ice | 136 | 71–100 | 80,10 |
| ice | 12 | 83–102 | 89,75 |
| powder_snow | 6 | 63–86 | 70,83 |
| pale_oak_wood | 2.587 | 0–130 | 49,79 |
| calcite | 29 | 0–14 | 7,66 |
| white_concrete | 127 | 8–72 | 32,44 |
| white_wool | 82 | 54–60 | 57,13 |
| light_gray_wool | 1.321 | 0–136 | 70,30 |
| pale_moss_block | 17.131 | 0–136 | 49,97 |

İlk yedi malzeme açık zirve/kar paletidir. Pale oak, calcite, pale moss ve açık gri
blokların tümünü adında “pale/light/white” geçtiği için körlemesine kara çevirmek
ölçümle desteklenmez: örneğin calcite yalnızca tabandadır, pale oak dağın çoğuna
yayılmıştır. Karın kaynağa dayanan maskesi açık bir malzeme listesiyle tanımlanır;
otomatik genel ad eşleştirmesi kullanılmamalıdır.

Son uygulamanın açık maskesi snow/ice/powder snow, birch wood, diorite, white
concrete powder, white concrete, white wool, calcite ve varsa quartz/smooth quartz
malzemelerini kapsar. Saf beyaz beton/yün/calcite genişlemesi bilinçli bir ürün
seçimidir; kaynakta bunların hepsi zirve malzemesi değildir. Pale oak, pale moss ve
light gray wool korunur. Kaynakta zeminin üzerinde duran ince snow katmanları da
maskeye katılır; ağacın üstündeki kar zemine düşürülmez. Mutlak Y=160 başlangıcı ve
Y=190 tam kar örtüsü ayrıca uygulanır.

## Hangi sayılar doğruluk ölçütüdür?

- Güncel aktarım profili: 39 blok adı, 44 tam block-state, 127.672 zemin sütunu
  (diamond işaretleri ayıklandıktan sonra).
- Kaynakta komşu aynı materyal oranı yaklaşık %65,98. Aynı oranlarla bağımsız
  hücre seçimi yalnızca %9,94 verir. Texture aktarımı yama bağlantılarını korumalıdır.
- Terrain paleti, kaynakta ölçülen beyaz blokları da içerir. Ham kaynak ile çıktı
  histogramı, farklı hedef reliefte yine tek başına başarı/başarısızlık ölçüsü değildir.
- Test referansı, aynı kaynak terrain'in ve aynı maskenin hedef yükseklikte kar
  kurallarına uygulanmış halidir. Bu “beklenen dönüşüm” ile gerçek aktarım karşılaştırılır.
- Farklı şekilli dağda tüm malzeme yüzdelerinin tıpatıp aynı olması beklenmez;
  yükseklik/eğim koşullu oranlar ve komşuluk, çıplak küresel orandan daha anlamlıdır.
- Kaynaktan gelmeyen beyaz terrain doldurulmaması; maskenin dışında kar doğmaması;
  Y<160'da layer kar olmaması; 1–8 katman; su/korunan alanların değişmemesi somut
  doğrulama koşullarıdır. Moss/pale moss ayrıca yalnız Y=80–120 ve 30° altı
  hedef yüzeylerde kalır.

`VerifyTransfer.java`, kullanıcının .world dosyalarına dokunmadan geçici JVM
belleğinde iki 384×384 dünya üretir: kaynak yüksekliğiyle aynı ama tile sınırları
nedeniyle yama aktarımını gerçekten çalıştıran bir dünya, bir de yatay/düşey olarak
bozulmuş başka bir yükseklik haritası. Dört görüntü üretir: ham blueprint texture,
beklenen beyaz terrain + kar, gerçek aktarım, bağımsız rastgele hücre teşhis örneği.
Tam terrain oranları, kar maskesi/son kar sayıları ve yükseklik/eğim koşullu tablolar
ayrı tutulur. Bu yardımcı bir Minecraft görsel export testi değildir.

## Önceki gerçek-blueprint doğrulaması

Son derlenmiş sınıflarla `dag.bp` yeniden yüklenerek iki test tamamlandı. Profilin
127.696 zemin sütunu, 38 blok adı ve 43 state sayısı bağımsız ham ölçümle aynıdır.
Kaynak beyaz/ince-kar maskesi 16.298 sütundur. Önceki pilotta yakalanan saksılı
fidanın zemin sayılması sorunu düzeltildi; son çıktıda bitki/kısmi blok dolgusu yok.

| Ölçüm | Aynı relief, gerçek yama aktarımı | Değiştirilmiş relief |
|---|---:|---:|
| Boyut | 384×384 | 384×384 |
| Boyanan uygun hücre | 127.696 | 125.115 |
| İşlenen yama | 953 | 936 |
| Aktarım aşaması | 0,183 s | 0,106 s |
| Yama çalışma şeridi belleği | 24.576 bayt | 24.576 bayt |
| Referans alt zeminde aynı komşu oranı | %69,221 | %67,771 |
| Aktarılan alt zeminde aynı komşu oranı | %69,736 | %69,439 |
| Bağımsız rastgele teşhis örneğinde aynı komşu | %12,571 | %12,539 |
| Alt zemin histogramı total-variation farkı | %1,658 | %4,059 |
| Beyaz maskesi konumsal örtüşmesi, IoU | %98,083 | %57,516 |
| Beklenen/gerçek kar hücresi | 4.577 / 4.568 | 4.286 / 3.942 |

Bu ölçüm, beyaz blokların yalnız maskeye dönüştürüldüğü önceki davranışın
referansıdır; güncel beyaz-terrain ve moss-filtre sürümü için yeniden çalıştırılmalıdır.
Önceki testte sıfır ihlal: boyanmayan uygun hücre, korunan alanda değişiklik,
yükseklik değişikliği, saksı/yaprak/merdiven/slab dolgusu, kaynak maskesi dışında
kar ve 1–8 dışı kar kalınlığı. Değiştirilmiş relief çıktısında 1–8 katmanın tamamı
gerçekten oluştu; sayılar sırasıyla
743, 739, 584, 526, 475, 376, 323, 176.

Aktarım süresi blueprintin ilk yüklenmesini, dünya kurulumunu veya Minecraft
exportunu içermez. İkinci test JVM ısınmasından da yararlanır. Bu, 8K dünya için
ölçülmüş bir süre ya da çökmezlik garantisi değildir. Profil belleği şerit belleğine
eklenir; 24.576 bayt bütün JVM'nin belleği değildir.

Konumları değiştirilmiş bir dağda maske örtüşmesi %57,5'tir: sonuç piksel piksel
aynı değildir. Ancak doğru koşullu malzeme ailesi, yama sürekliliği ve kar davranışı
korunur. Aynı reliefte bile yama dikişleri/kenarları nedeniyle %100 kopya iddiası
yapılmamalıdır. Ölçümün güçlü sonucu, rasgele nokta dokusundan gerçek komşuluk
ve yükselti ilişkilerine dayalı bir aktarım elde edilmesidir.

## Güncel beyaz-terrain ve moss filtre doğrulaması — 3 Eylül 2026

Güncel sınıflarla `dag.bp`, kullanıcı dünyasına yazmadan iki geçici 384×384
hedefte yeniden işlendi. Diamond işaretleri atlandıktan sonra profil 127.672
zemin hücresi, 39 blok adı ve 44 tam block-state içerdi. Aynı reliefte 15.605,
değiştirilmiş reliefte 15.392 ölçülen beyaz terrain hücresi taşındı. Her iki
çalışmada da sıfır boyanmamış uygun hücre, sıfır korunan alan/yükseklik değişimi,
sıfır geçersiz snow layer ve sıfır bitki/kısmi-blok terrain dolgusu görüldü.

Moss filtresi, belirtilen Y=80–120 ve 30° kuralı nedeniyle sırasıyla 22.108 ve
23.531 moss/pale-moss hücresini yakın gerçek kaya/toprak/çimen kaynak dokusuna
yönlendirdi. Bu yüksek sayı beklenen davranıştır: blueprintte dekoratif moss dik
veya yüksek yüzlerde de bulunuyordu; yeni kural bunları aynen kopyalamaz.

Son görseller (gözle de kontrol edildi):

- `verification-results/same-relief-quilt-comparison.png`
- `verification-results/changed-relief-comparison.png`

Her görsel soldan sağa ham kaynak, beklenen beyaz terrain + kar, gerçek sonuç ve
rastgele teşhis örneğini gösterir. Son JSON metrikleri ve tam materyal/koşullu
TSV tabloları aynı klasördedir.
