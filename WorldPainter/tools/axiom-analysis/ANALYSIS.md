# dag.bp: doğrudan blok ölçümü

Kaynak: `C:\Users\Admin\Downloads\dag.bp`. SHA-256:
`AA293C53116FECE10647CB0A721503D5D979887909B3299D2C9D8F643D753FBA`.
Blueprint başlığındaki `BlockCount=6052506` ile bağımsız sayılan dolu blok sayısı aynıdır.
Ölçüm `AxiomBlueprint` okuyucusunu kullanır; kaynak blueprint/dünya değiştirilmez.

## Doğrulanmış sayılar

| Ölçüm | Değer |
|---|---:|
| Yatay boyut | 350 × 380 blok |
| Düşey boyut | 140 blok |
| Hava dışındaki blok | 6.052.506 |
| Hacimde farklı blok adı / tam block-state | 76 / 190 |
| Dolu yatay sütun | 129.234 |
| Boş yatay sütun | 3.766 |
| Birden çok ayrı düşey dolu bölüm içeren sütun | 28.919 |
| Gerçek en üst görünen yüzeyde farklı blok adı | 62 |
| Tam blok zemin yüzeyi bulunan sütun | 127.696 |
| Tam blok zemin yüzeyinde farklı ad / tam block-state | 38 / 43 |

“Zemin” sayımı, her yatay konumda bitki, yaprak, sıvı, ince kar, çit/kapı/duvar,
merdiven/slab, bayrak ve sivri damlataş gibi kısmi/geometrik detayları atlayarak ilk
tam zemin bloğunu alır. Ahşap, yün ve beton gibi kaynakta gerçekten kullanılan tam
bloklar korunur. Bu ayrım olmadan çimler veya çitler arazi dolgusu olarak üretilir.
Snow block ve buz tam zemin paletindedir; ince snow katmanı ayrı örtüdür.

Önceki 69 malzeme/31.653 stone benzeri “üst yüzey” rakamları en üstteki tek hücreyi
temsil etmez. Sütun başına üst yüzey, mağara/çıkıntı içindeki bütün yukarı bakan
yüzler ve bütün dolu hacim farklı örneklemlerdir; oranları karıştırılmamalıdır.

## Tam blok üst zemin paleti

Oranların paydası 127.696 zemin sütunudur. Bütün 38 ad ve 43 state
`dag-results/counts.tsv` içinde bulunur.

| Blok | Sayı | Yüzde |
|---|---:|---:|
| stone | 22.818 | 17,869 |
| grass_block | 17.918 | 14,032 |
| pale_moss_block | 17.131 | 13,415 |
| acacia_wood | 12.336 | 9,660 |
| moss_block | 10.966 | 8,588 |
| cyan_terracotta | 8.307 | 6,505 |
| cobblestone | 5.891 | 4,613 |
| mud | 5.563 | 4,356 |
| snow_block | 4.793 | 3,753 |
| birch_wood | 4.213 | 3,299 |
| white_concrete_powder | 3.323 | 2,602 |
| diorite | 3.006 | 2,354 |
| pale_oak_wood | 2.587 | 2,026 |
| basalt | 2.177 | 1,705 |
| andesite | 1.445 | 1,132 |

State örnekleri: acacia wood 12.333 `axis=y`, 3 `axis=z`; birch wood 4.209
`axis=y`, 4 `axis=x`; pale oak wood 2.568 `axis=y`, 13 `axis=z`, 6 `axis=x`.
Yalnızca blok adını saklamak, bu yönlü texture bilgisini kaybettirir.

## Yükseklik ve eğim belirleyici

Sadece bütün-dünya oranı rastgele dağıtılarak kaynak görünümü elde edilemez.
Kaynak zemin yüksekliği, blueprintin kendi tabanına göre 0–138 aralığındadır.
Bu değerler Minecraft dünyasındaki mutlak yükseklik veya hedef platformun build
limitleri değildir. Zemin yüksekliği yüzdelikleri yaklaşık: %5=3, %10=7,
%25=17, %50=42, %75=72, %90=99, %95=115, %99=133.

Eğim, her yönde 4 blok uzaklıktaki komşu yüksekliklerinden merkezi farkla ölçüldü:
`atan(hypot((h[x+4]-h[x-4])/8, (h[y+4]-h[y-4])/8))`.

| Kaynak göreli yükseklik | Eğim | Öne çıkan gerçek oranlar |
|---|---|---|
| 0–34 | 0–10° | grass %73,8; moss %19,8 |
| 0–34 | 55°+ | stone %20,9; acacia %16,1; cyan terracotta %13,8; pale moss %11,9 |
| 35–68 | 25–40° | stone %28,9; pale moss %22,7; acacia %12,5; cobble %7,4 |
| 69–103 | 0–10° | snow block %63,7; birch wood %10,4; packed ice %8,8 |
| 104–138 | 10–25° | diorite %36,2; birch wood %33,5; white powder %19,0; snow block %7,8 |
| 104–138 | 25–40° | birch wood %29,6; white powder %22,4; diorite %19,1; snow block %10,2 |
| 104–138 | 55°+ | stone %22,7; pale moss %16,0; cobble %14,6; acacia %14,4 |

Zirvenin beyazlığı ağırlıklı olarak snow block değildir. Diorite, birch wood ve
white concrete powder ana bileşenlerdir. **Güncel uygulamada bu ölçülen beyaz zirve
blokları terrain olarak aynen taşınır; konumları ayrıca gerçek ince kar katmanı için
maske olur.** Y=160 başlangıcı ve Y=190 tam örtü bu maskeye uygulanır. Böylece ham
blueprint beyaz blok oranları, taşınan terrain ve üstündeki kar katmanı ayrı ayrı
ölçülür.

16 göreli yükseklik bandı × 5 eğim bandı × her blok sayıları, ayrıca 1/4/8 blok
eğim ölçekleri ve yönleri `conditional.tsv` içindedir. Komşusu olmayan kenarda
merkezi yükseklik kullanılır; sınırlarda bu eğim ölçümü yaklaşık değerdir.

## Mekânsal desen: en büyük önceki hata

| Hücre uzaklığı | X yönü aynı malzeme | Y yönü aynı malzeme |
|---|---:|---:|
| 1 | %64,99 | %66,97 |
| 2 | %50,87 | %53,46 |
| 4 | %36,59 | %38,29 |
| 8 | %27,21 | %27,35 |

Aynı bütün-dünya malzeme oranları bağımsız rastgele hücrelere dağıtılsa komşuların
aynı malzeme olma olasılığı `sum(p_i²) = %9,9417` olur. Kaynağın gerçek değeri
yaklaşık %65,98'dir. Dolayısıyla oranları doğru tutan bir rastgele boya bile aynı
texture olamaz: 2–8 blok ölçeğindeki bitişik yamalar ve daha geniş yükselti
bölgeleri korunmalıdır.

Harita görselinde dağ sırtlarına göre uzanan desenler bulunur. Bununla birlikte
25°+ eğimlerde “akış yönünde” ve “kontur yönünde” korelasyon farkı kuvvetli değildir
(4 blokta %31,42 / %31,30; 8 blokta %22,38 / %21,30). Kaynaktan örnek parçaları
aktarmak uygundur; ölçüm, her yamayı keyfî biçimde uzun çizgilere dönüştürmeyi
desteklemiyor.

## Yanal yüzler ve kaynak detayları

Kutu sınırına bakan 27.848 yapay kesim yüzü dışlandı. Komşu dolu zemin sütununun
üzerinde kalan tam-blok dış yan yüzler: kuzey 38.443, doğu 48.938, güney 43.575,
batı 36.015. Düzensiz seçim/boşluk sınırlarının tümü otomatik olarak doğal yüzey
diye yorumlanmamalıdır. Bütün hava-komşusu yüzler `side_internal_*`, yükseklik
haritasından görünen dış yüz alt kümesi `side_above_neighbor_*` adlarıyla ayrıdır.

Kuzey/doğu yüzlerinde acacia yaklaşık %16, cyan terracotta %12, stone %17 iken
güney/batıda acacia %8, cyan %5, stone %26 ve cobble %10–11'dir. Tek bir küresel
palet dört cephenin farkını da kaybeder. WorldPainter tek sütun terrain ile her
düşey bloğa/yüze ayrı material atayamaz; tam 3B birebir aktarım object/özel export
gerektirir. Texture scripti yalnızca yükseklik haritasıyla temsil edilebilen
yüzeyi ve malzeme alanını yeniden oluşturabilir.

Diamond block hacimde 33.620, tam üst zeminde 420 hücredir. Üstteki örnekler tabana
yakın (yükseklik 0–27, ortalama 10,61), çoğunlukla güney eteklerindedir. Tek ve açık
bir işaret bloğu değildir; otomatik olarak “hata” sayılıp silinmesi kanıtlanmış
değildir. Kaynak paletinde tutulur, bu bağlam ayrıca raporlanır.

## Çıktılar ve tekrar üretim

- `BlueprintMeasure.java`: ölçüm; kaynak .bp sadece okunur.
- `BlueprintHeader.java`: özgün dosya başlığı ve içerideki PNG önizlemeyi çıkarır.
- `dag-results/summary.json`: temel doğrulanmış sayılar.
- `counts.tsv`: hacim, görünür üst, tam zemin, state ve dört yan yüz ayrı paydalarla.
- `conditional.tsv`: göreli yükseklik/eğim/yön koşullu sayımlar.
- `surface-samples.tsv`: 127.696 hücrenin koordinat/yükseklik/eğim/tam state verisi.
- `correlation.tsv`, `patches.tsv`: komşuluk ve bitişik yama geometrisi.
- `material-spatial-summary.tsv`, `height-percentiles.tsv`: konum/yükseklik bilgileri.
- `source-surface-maps.png`: hücre başına bir piksellik bilimsel üst görünüş; renkler
  WorldPainter'ın material preview renkleridir, Minecraft resource pack renderı değildir.
- `blueprint-original-thumbnail.png`: blueprint içinde zaten bulunan özgün küçük PNG.

Repo içindeki WorldPainter klasöründen, kurulu JDK ile örnek çağrı:

```powershell
& "$env:USERPROFILE\.jdks\jdk-21.0.12.1+1\bin\java.exe" -Xmx4g `
  -cp '..\dist\WorldPainter v2\app\WorldPainter-v2.jar' `
  'tools\axiom-analysis\BlueprintMeasure.java' `
  "$env:USERPROFILE\Downloads\dag.bp" 'tools\axiom-analysis\dag-results'
```

Bir referans ölçümü yaklaşık 8,5 saniye sürdü; eşzamanlı sistem yükünde bu süre
değişir. İşlem süresi bir üretim scriptinin 8K dünya performansı ölçümü değildir.
