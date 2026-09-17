# Çizilen nehirde vadi kotu düzeltmesi

> Bu belge ilk STRONG başarısızlığının tarihsel kaydıdır. Kullanıcının daha derin
> önizleme onayından sonraki ortak ağ çözümü ve başarılı gerçek dünya chunk/Undo
> kabulü için [DRAWN-DEEP-VALLEY-TR.md](DRAWN-DEEP-VALLEY-TR.md) belgesine bakın.

## Doğrulanan neden

Önceki `alongCentreline` bütün hattı aynı miktarda indiriyordu. Kaynak/alçak
kesit de indiği için kanalın su kotu aşağı kayıyor, sırtın göreli yüksekliği
korunuyordu. Aynı hücreye gelen kesitlerin farkları da toplanıyordu.
Uç nokta kotunu bütün hatta uygulayan ek kazı tahmini, normal bir dağ
deresini de gereğinden fazla kazı istiyor gibi sınıflandırıyordu.

## Değişiklik

- Özgün arazi üzerindeki enine kesitlerden aşağı akış kotu çıkarılır;
  düşük kesimler korunur, yalnız bu profilin üstündeki yükseltiler indirilir.
- Seyrek nokta dizisinin arası yarım blok adımla örneklenir. Islak genişlik
  çevresindeki omuz etkisi koridor sınırında sıfıra iner.
- LIGHT/STRONG sınırları ve son kanal doğrulaması korunur. Bu bir tam
  hidroloji/erozyon çözücüsü değildir; aşırı sırtlar hâlâ reddedilebilir.
- Örtüşen kazılar toplanmaz; aynı özgün hücreye yönelik derin hedef bir kez
  alınır. Dolgu önerilerinde en yüksek hedef alınır; kazı/dolgu çakışmasında
  kazı hedefi seçilir ve birleşik plan yeniden kanal doğrulamasına girer.
- Yükseklikler planlamada Tile'ın 1/256 blok gösterimine çevrilir.
- Ana gövde kurtarıldığında daha önce atlanan kollar ortak süre bütçesinde
  yeniden denenir. Kabul edilen kolları bozan yeni öneri kullanılmaz.
- Yalnız kabul edilmiş kollara ait arazi önerileri birleştirilip yeniden
  doğrulanır; başarısız kollara ait turuncu alanlar uygulamaya taşınmaz.
- Kanal uygulaması hata/iptal verirse arazi hazırlığı da geri alınır.
- Çıkış uzatma başarısızlığında çizilen ağın yerine düz kaynak–deniz
  kestirmesi çizilmez; özgün hat ve onun son uzantısı korunur.

## Regresyonlar

`DrawnValleyGradeRegressionTest`: 4 blokluk sırtı güçlü öneri ile gerçekten
geçme, düşük kesitte sıfır kazı, örtüşme idempotansı, normal aşağı eğimde
gereksiz ek kazı olmaması, üç parçalı Y ağı, uygulama/Undo, uygulama sırasında
iptalde araziyi geri yükleme ve sınır dışı sırtın uygulanmaması.

`DrawnRiverExportTest.gradedDrawnRidgeExportsWaterInsteadOfTheBlockingRidge`:
gerçek chunk üretiminde çizim boyunca su ve koridor dışı arazi korunumu.

## Kabul sınırı

Sentetik Java/export testleri kullanıcı dünyasında veya Minecraft sıvı
güncellemelerinde kabul yerine geçmez. Kullanıcının River Path içeren `.world`
dosyası ile aynı çizim henüz doğrulanmadı. Aktif kurulum bu çalışma sırasında
değiştirilmez; paket önce ayrı test çıktısı olarak hazırlanır.

### Gerçek dünya kabulü — 14 Eylül 2026

Kullanıcının paylaştığı Generated World ayrı kopyada incelendi: 25 tile,
Y=-64..512, River Path üzerinde 3068 hücre. Girdi SHA-256:
`975CFEA129C1750F98E358BDFAA57BDE8EC46E322ABE6FC173BDA5851848C491`.

Doğal nehir 5→12 / derinlik 1.1, STRONG araması hâlâ 0 uygulanabilir
parça üretiyor. Geçerli grafikte 7 kaynaklı/13 parçalı ana ağ var; başka bir
çizim grubu birden fazla su çıkışı nedeniyle belirsiz. Önizleme dünya verisini
değiştirmedi. Kaynak dosyanın hash'i test sonunda aynı.

Yerel tangent artık kanalın kullandığı merkezlenmiş yönle hesaplanıyor;
düzenleme sonrası ret nedenleri de günlükte ayrıca gösteriliyor. Bu iki
iyileştirme gerçek dünyadaki başarısızlığı tek başına çözmedi. İteratif alçaltma
ve yerel dolgu denemeleri kabulü geçmediği için bu deneyler tutulmadı.

Sonuç **BAŞARISIZ KABUL**: sentetik testler bu dünya için yeterli değil.
Ortak ağ su/kıyı kotu çözümü tamamlanmadan aktif kurulum güncellenmemeli.
İlk başarısızlığı tekrarlama aracı: `WorldPainter/tools/CheckDrawnWorld.java`,
`strong` modu (yalnız okur). Sonradan eklenen `verify-deep` modu bellekte
uygulama/export/Undo yapar, dünya dosyasını kaydetmez. İlk ayrıntılı rapor:
`dist/acceptance-generated-world-20260914/strong-result.txt`.
