# Üst su hizası doğal kıyı yumuşatması

Yalnız **yeni** nehirlerde (işaretlenmiş hücreler). Eski haritalara otomatik eklenmez.

## Ne yapar?

Nehirin **en üst su kotunda** (`Y = W`, üstü hava) kalan **kuru kıyı dudağına** waterlogged alt-half stair / alt slab yazar.

- Islak granite yüzeyi → `RiverSurfaceDetail` (önceki davranış)
- Kuru üst dudak → `RiverWaterlineDetail` (bu özellik)

Genel yüzey yumuşatma (`hasAdjacentFullWater` vb.) dünya genelinde **değişmez**; yalnız marker + canlı topoloji doğrulanınca aşılır.

## Kullanıcı seçeneği

River Tools: **“Üst su hizasını doğal yumuşat”** (`waterlineBankDetail`, varsayılan **açık**).

Kapatılabilir. Dirt −1 ve granite detayından bağımsızdır.

## Önizleme

Planlayıcı **planlanan** yükseklik/suyu kullanır (`WaterlineColumns`); dünya henüz yazılmadan da kıyı hücrelerini bulur. Önizleme önbelleği apply marker sayısını sıfırlamaz.

## Güvenlik

Tam blok bırakılır (hata değil): ağız/birleşim guard, yüksek duvar, kaynak, kaçak riski, stale marker, desteksiz malzeme, **açık cardinal yan yüz** (komşu `height < W` ve su `waterLevel < W` — daha alçak su yüzü kapatmaz).

Yasak (v1): üst slab, ters stair, çift slab, su kotu yükseltme, kanal daraltma. Minecraft su tick’inin tam simülasyonu yok; cardinal sızdırmazlık kabul eşiğidir.

## Export

`WorldPainterChunkFactory` → `SurfaceSmoother.dryWaterlineMaterial` (`isDryWaterlineEdge` **ve** `isWaterlineSideSealed`). Stale/kaçak → full blok.

## Undo

`originalWaterlineDetail` ile tek Undo kapsamında geri alınır.
