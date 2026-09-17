# river.bp: sığ yatak ve kıyı ölçümü

Kaynak yalnızca okundu: `C:\Users\Admin\Desktop\river.bp`.
SHA-256: `A6C97C3D5E1071003638BA5CED808844B74666EDAA224ABCA30C306034C0B854`.
Başlıktaki ad `river`, yazar `Furkqn`; başlık ve bağımsız sayımda 30.021 hava dışı blok.
Boyut: yatay 63 × 50, düşey 13 blok. Aşağıdaki Z değerleri blueprintin yerel
WorldPainter koordinatlarıdır; gerçek dünya yüksekliği değildir.

## Doğrudan sayılan geometri

Su veya `waterlogged=true` içeren 441 yatay sütun var.

| Sütunda gerçek water bloğu | Sütun sayısı | Açıklama |
|---|---:|---|
| 0 | 101 | Su, kısmi blokların waterlogged hacminde |
| 1 | 332 | Baskın sığ su hücresi |
| 2 | 8 | Az sayıda daha derin geçiş |
| 3 ve üstü | 0 | Kaynakta yok |

Toplam 348 water bloğu: 209 kaynak (`level=0`), 139 diğer seviyelerde akış.
171 adet waterlogged blok var: 102 granite slab, 62 granite stairs, 7 diğer.
Bu iki toplam aynı sütunlarda üst üste bulunabilir; birbirine toplanarak sütun
sayısı elde edilmez.

En üst ıslak hücrenin üst yüzü ile arazinin destek yüzeyi arasındaki düşey fark:

| Hücre-zarfı derinliği | Sütun |
|---|---:|
| 0,5 | 71 |
| 1 | 302 |
| 1,5 | 34 |
| 2 | 8 |
| Merdivenin yüksek basamağı ile aynı üst seviye | 26 |

Bu tablo gerçek akışkan yüzey yüksekliği değildir: water `level` değerinden
akışkan yüzeyi simüle edilmedi. Alt yarım merdivende yüzey blok içinde 0,5–1
arasında değişir; tablo yüksek basamağı kullanır. Bu nedenle son satırın gerçek
su derinliği sıfır demek değildir. Ana bulgu, referansın 0,5–1 blok ağırlıklı çok
sığ bir yatak olmasıdır; 2,5 blok nominal derinlik aynı görünümü vermez.

Yan yana 713 ıslak sütun çiftinin 690'ında (%96,8) üst su hücresi aynı Z'dedir;
23'ünde fark 1'dir, daha büyük basamak yoktur. Aynı çiftlerde destek yüzeyi
farkı: 538 düz, 139 yarım blok, 34 bir blok, 2 bir buçuk blok.

Islak/kuru sınırındaki 325 komşulukta kıyı üstünün su hücresi tavanına göre farkı:
226 aynı düzey, 10 yarım blok yukarı, 5 bir blok yukarı, 84 daha aşağı.
Bir bloktan daha yüksek kıyı çıkıntısı yoktur. Yalnızca su derinliğini sınırlamak,
su çizgisi arazinin çok altında kalırsa bu kriteri sağlamaz; kazı miktarı ve
su-kıyı kotu da birlikte sınırlanmalıdır.

`cross-sections-x.tsv` ile `top-map.txt` yatay dağılımı verir. Ana kolun kuzey
ucundaki ilk 14 satırda yatay kesit 5–7 ıslak hücredir; gerçek akışa dik genişlik
bundan biraz daha az olabilir. Batıdan gelen çok dar yan kol ve aşağıda daha
geniş bir sığ birleşim vardır. Alt satırlardaki geniş X aralığı doğrudan kanalın
akışa dik genişliği değildir; kıvrım/yan kol ve eğik akış bu sayıyı büyütür.

## Malzeme ve yumuşaklık

441 ıslak sütunun üst destek yüzeyi:

| Malzeme | Sütun | Oran |
|---|---:|---:|
| dirt | 235 | %53,3 |
| granite_slab | 102 | %23,1 |
| granite_stairs | 62 | %14,1 |
| andesite | 33 | %7,5 |
| diğer | 9 | %2,0 |

Bütün blueprint hacminde 257 granite slab ve 126 granite stairs var;
**tamamı alt yarım tip**. Ayrıca 118 tam granite bulunuyor. Bunlar sadece
rastgele kaya rengi değildir: kıyı/zemin yüzeyini yarım bloklarla yuvarlatırlar.
Kuru ve ıslak yarım bloklar birlikte kullanılmıştır. Tüm düzlükleri granite
boyamak gerekmez; doğru kapsam, yeni açılan yatak ve kıyı geçişidir.

## Uygulama için ölçüme dayalı hedefler

- Referans benzeri doğal/dere profilde baskın su derinliği 0,5–1 blok,
  geçiş/pool üst sınırı yaklaşık 2 blok. Bu, hard-coded 2,5 blok her noktada
  kazı demek değildir.
- Su çizgisine göre kıyı yüksekliği çoğunlukla 0–0,5, en çok yaklaşık 1 blok;
  sonra keskin duvar yerine dışarı doğru kuru ve yumuşak omuz.
- Merkezi su hattı yanal komşu arazinin çukuruna göre her hücrede ayrı ayrı
  alçalmamalı. Aynı kesitte ortak su düzeyi ve az sayıda tek blok boyuna iniş
  tercih edilmeli. Zor topoğrafyada dar ve derin yarık üretmek yerine rota veya
  uygunluk denetimi gerekli.
- Geometrik smooth, fractional yatak yüksekliği + exportta alttan slab/stair
  dönüşümü + waterlogged state ile doğrulanmalı. Yalnızca merkez hattı görselini
  yumuşatmak Minecraft'taki kesit duvarını yumuşatmaz.
- Sığ ıslak yatak/kenarda yeterli granite aile desteği korunmalı; ölçülen
  %37,2 granite partial desteği, tüm haritaya uygulanacak granite oranı değildir.
- Aynı profile yeniden uygulama önceki kazıyı geri getirmez. Testler temiz
  kaynak arazide yapılmalı; terrain yüksekliği, su kotu ve nihai export blokları
  ayrı ölçülmeli.

## Tekrar üretim ve kapsam

`BlueprintMeasure.java` genel hacim/yüzey sayımlarını oluşturdu;
`RiverMeasure.java` bu klasördeki wet-columns, river-metrics, kesit ve metin
haritasını üretir. Kaynak dosyaya yazılmaz. İkinci araçta zemin malzemeleri
açık bir listeyle belirlenir; ot/yaprak/dekorasyon zemin sayılmaz. Merdivenlerin
tüm voxel yüzeyleri veya Minecraft sıvı simülasyonu yeniden oluşturulmaz.

```powershell
& "$env:USERPROFILE\.jdks\jdk-21.0.12.1+1\bin\java.exe" -Xmx1g `
  -cp '..\dist\WorldPainter v2\app\WorldPainter-v2.jar' `
  'tools\axiom-analysis\river-results\RiverMeasure.java' `
  "$env:USERPROFILE\Desktop\river.bp" 'tools\axiom-analysis\river-results'
```
