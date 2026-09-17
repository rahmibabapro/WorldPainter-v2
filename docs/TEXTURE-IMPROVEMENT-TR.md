# Otomatik texture geliştirme

## 2026-09-07 — Nehir yüzeylerini koruma

Kaynak incelemesi: AxiomTextureTransfer'ın ortak uygunluk filtresi ve
SmoothSnow kuru hücrelerde nehir işaretlerini kontrol etmiyordu. Su yüksekliği
koruması tek başına kuru kalan veya sonradan suyu indirilen işaretli yatakları
korumuyordu. AxiomMountainStyleOp yüzey geçişinde de aynı açık vardı.

- Kaynakta tamamlandı: River ve RiverSurfaceDetail işaretli hücreler bu üç
  yolda yeniden texture/kar uygulamasından çıkarıldı. Mevcut terrain, snow
  ve nehir işaretleri silinmiyor. İşaretsiz normal arazi işlenmeye devam ediyor.
- Test edildi: Java 21 odak grubu 42/42 başarılı, sıfır hata. İki yeni test,
  Y=210 kuru nehir hücrelerinin yüzey+kar ve mixed-terrain geçişlerinde
  korunmasını, yanındaki normal arazinin yine işlenmesini doğruluyor.
- Tam genel test takımı ve gerçek 8K texture benchmarkı bu turda çalıştırılmadı.
- Kurulmadı/paketlenmedi; kullanıcı dünyası, profil ve brush dosyaları
  değiştirilmedi. Commit/push yapılmadı.

## Sıradaki sınırlı işler

1. Gerçek giriş yolu ScriptLibraryActions → AxiomBlueprintTextureOp;
   AxiomMountainStyleOp şu an kütüphaneden doğrudan çağrılmıyor. Ortak
   texture politikasını bu gerçek giriş üzerinden test ederek geliştirmek.
2. Y=150 ve <30 derece kar/beyaz şartlarını Axiom ve genel snow yollarında
   açıkça karşılaştırmak; global Snowify varsayılanlarını habersiz değiştirmemek.
3. Karıştırılmış terrain/blob dağılımlarında eğim/yükseklik geçişi ve
   tekrar desenlerini ölçmek. Kullanıcı custom terrain tanımlarını korumak.
4. Taş-çimen ve kalan JavaScript girişlerinde nehir korumasını denetlemek;
   bütün sistem korunmuş iddiası bu üç yolla sınırlı testten çıkarılmamalı.

Araştırma bu turda mevcut kaynak incelemesiyle sınırlıydı; dış kod alınmadı.

## 2026-09-07 17:39 — Taş/çimen girişinde nehir koruması

- ScriptLibraryActions gerçekten GlobalSlopeTerrainOp'u çağırıyor. Bu işlem
  MapQuickPresetExecutor.applyStoneMixGrassSlopeSplit üzerinden işaretli kuru
  nehir yataklarını yeniden boyayabiliyordu.
- Kaynakta düzeltildi: River/RiverSurfaceDetail hücreleri bu ortak geçişte
  atlanıyor. İşaretsiz eğim/grass davranışı ve su yüksekliği değiştirilmedi.
- Yeni test granite/mud malzemesinin ve nehir işaretinin korunmasını,
  yan normal hücrenin boyanmasını ve tek adım undo'yu doğruluyor.
- Java 21 GlobalSlopeTerrainOpTest grubu başarılı; genel test takımı yeniden
  çalıştırılmadı. Aktif Maven/Surefire işlemi kalmadığı kontrol edildi.
- Kurulum, kullanıcı dünyası, profil, commit ve push işlemi yapılmadı.
- Sıradaki öncelik: gerçek Axiom blueprint girişindeki Y=150 / <30 derece
  sınır testleri ve snow/texture kurallarının birlikte doğrulanması.

## 2026-09-07 17:52 — Birleşik texture/kar sınır regresyonu

- Gerçek AxiomBlueprintTextureOp.applyProfiles yolu üzerinden Y=149,
  tam Y=150 düzlüğü ve 31 derece eğimli yüksek arazi aynı testte çalıştırıldı.
- Y=149'da beyaz/Frost/SnowDepth yok; tam Y=150'de beyaz source yüzeyin
  smooth snow ile birlikte oluştuğu; 31 derecede stone ve karsız yüzey
  doğrulandı. Geçiş bölgeleri eğim örnekleme yarıçapı nedeniyle ayrı tutuldu.
- Test geçti; bu senaryoda üretim hatası bulunmadığı için algoritma gereksiz
  değiştirilmedi. Java 21 AxiomBlueprintTextureOpTest grubu başarılı.
- Tam 30 derece nicemleme sınırı ve <30 derece eğimli beyaz yüzeyler sonraki
  testlerdir; bu tur bunlar doğrulandı diye sunulmamalı.
- Paket/kurulum, kullanıcı dünyası, profil, commit/push yok.

## 2026-09-07 18:04 — Beyaz yüzey / kar eğimi tutarlılığı

- Yeni gerçek birleşik işlem testi: Y≈190, 8 blok periyotlu küçük yüzey
  dalgalanmaları. Önce `Missing snow over white at 12,8` ile başarısız oldu.
- Neden: transfer 4 blok komşuluk eğimini kullanırken snow 1 blok komşuluk
  kullanıyordu. Uzak örnekler yerel dikliği gizleyerek beyaz terrain bırakıyordu.
- Düzeltme: transfer eğim sınıflandırması uzak ve yerel eğimin büyüğünü
  kullanıyor. Böylece karın reddedeceği yerel dik yüzey, beyaz terrain yerine
  mevcut dik-kaya yoluna giriyor. Genel snow varsayılanları değiştirilmedi.
- Etki yalnız beyazda değil, aynı filtreyi kullanan plains/steep-rock
  sınıflandırmasında da yerel dikliğin hesaba katılmasıdır; küçük çıkıntılarda
  daha çok kaya olabilir. Görsel gerçekçilik henüz Minecraft'ta ölçülmedi.
- Java 21: AxiomBlueprintTextureOpTest, AxiomTextureTransferTest ve
  SmoothSnowReliabilityTest grupları başarılı (35 test). Regresyon artık geçiyor.
- Kurulum veya kullanıcı dünya değişikliği yok. Sonraki adım: eğim
  politikası için düzlem yönleri, tile sınırı ve 30 derece çevresi testleri;
  ardından blob malzeme geçişlerini ölçmek.
