# Çizimden nehir — birleşim boşluğu / küçük döngü normalleştirme

## Özet

`DrawnRiverGraph` artık ince iskelet çıkarmadan önce geçici bir maske kopyası üzerinde `DrawnRiverNormalizer` çalıştırır. Kullanıcının River Path katmanı yazılmaz.

## Ne düzelir

Birleşim merkezinde **1 hücre boşluk** (veya ≤4 hücre / ≤3×3 bbox, ≥3 kol) topolojik halkaya dönüp tüm çizim grubunun “Kapalı döngü” ile reddedilmesini engeller. Boşluk geçici maskede doldurulur, yeniden `thin` edilir; dış uçlar kaybolursa geri alınır.

Kalın birleşimlerde kısa halka (çap ≤8, ≥3 dış kol) **yerel spanning tree** ile açılır; büyük gerçek döngüler sessizce kesilmez → `REAL_LOOP` + gerçek bbox/highlight.

## Tanılama

`DrawnRiverGraph.Result` alanları:

- `diagnostics` — `IssueKind`: `SMALL_GAP_REPAIRED`, `JUNCTION_REGION`, `REAL_LOOP`, `MULTI_OUTLET`, `UNRESOLVED`
- `junctionRegions` — temsilci piksel + hücreler
- `repairs` — doldurulan hücreler (önizlemede mor)
- `skeleton` — normalize + thin sonrası

UI sınıfları (`DrawnRiverSession.UiClass`): çizim çözümlenemedi / ağ çözüldü arazi uygun değil / çıkış belirsiz. String `contains("Kapalı döngü")` yok.

## Kenar hariç tutma (v1)

Önizlemede iki bitişik hücreye tıklayarak `Set<Edge>` → yeniden `build`. Katmana yazılmaz. “Kenar hariç tutmayı temizle” ile sıfırlanır.

`DrawnRiverGraph.neighbours` dışlanan kenarı **komşulukta yok sayar** (piksel silinmez). Büyük halka böylece yola açılır.

## Boşluk araması

Yalnız çizime 4-komşu boş hücreler; ≤4 / ≤3×3 adayda durur. Çizimin sınır dikdörtgeni taranmaz.

Kol sayımı: deliğin 1 hücrelik halkasından **dışarı uzanan** maske bileşenleri. Kapalı 3×3 (dış kol yok) doldurulmaz → `REAL_LOOP`.

## Bilerek değişmeyenler

- `maxCut` / süre bütçesi yükseltmesi yok (ortak `check()` + deadline aynı)
- Vadi/kanal planlayıcısı (koridor / LIGHT / STRONG) aynı
- Dirt −1 / waterline / `waterLevel >= W` seal / tek Undo
- Örgülü nehir üretimi yok

## Test

Birincil: `DrawnRiverGraphTest.oneCellJunctionGapMatchesSolidCentreTopology` — 1 hücre boşluk + 3 kol ≡ dolu merkez.

Ek: büyük döngü `REAL_LOOP`; geçersiz grup ayrı geçerli havzayı engellemez; yakın paralel nehirler birleşmez.

## Kabul

1. Çizim doğru kaynak/birleşim/gövde ile çözülüyor (sentetik fixture yeşil).
2. Çözülen ağ mevcut vadi/kanal ile güvenli export.

**Kullanıcı dünyası:** aynı çizimin ayrı kopyada yeniden denenmesi — dosya yolu henüz verilmedi → **bekleyen kabul**. Sentetik fixture ile geliştirme tamam; kullanıcı `.world` verene kadar “tamam” denmez.

## Dosyalar

- `DrawnRiverNormalizer.java`
- `DrawnRiverGraph.java` / `DrawnRiverGraphTest.java`
- `DrawnRiverSession.java` / `DrawnRiverDialog.java`
