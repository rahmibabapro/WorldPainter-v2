# WorldPainter V2 — sistem geliştirme takibi

Başlangıç: 2026-09-04. Kaynak tabanı: `fb893270`; mevcut yerel çalışmalar korunur.
Bir maddenin kaynakta tamamlanması, masaüstü paketine kurulduğu anlamına gelmez.

## 1. Sürüm ve veri güvenliği — devam ediyor

- [x] Tile fingerprint hesabında yükseklik, terrain ve suyun tüm hücrelerini kapsa.
- [x] BIT, NIBBLE ve BYTE layer değişikliklerinde hücre atlama; BIT_PER_CHUNK için her chunk'ı kapsa.
- [x] Manifest sürümünü değiştir; eski örneklemeli hash'leri yeni hesapla karıştırma.
- [x] Başarılı export sonrası uyumsuz manifest yerine yalnız export edilen alanın yeni kayıtlarını yaz.
- [x] Boşta bellek temizliği sırasında Undo/Redo geçmişini koru; yalnız render cache temizle.
- [ ] Delta export için exporter ayarları, custom material tanımları ve harici nesne bağımlılıklarını izle.
- [ ] Silinen tile ve farklı dimension/dünya eşleşmelerini doğrula.
- [ ] Büyük dünyalarda eksiksiz hash hesabının süresini ölç; arayüz dışı çalışma/iptal ekle.
- [ ] Kaynak değişiklik özeti, commit ve paket kimliğini uygulamada göster.
- [ ] Test edilmiş paketi ayrı alanda hazırla; profil/RAM ayarlarını koruyan geri dönüşlü kurulum yap.

**Sınır:** Tam hücre hash'i, global ayar bağımlılıklarının takibinin yerine geçmez.
Delta export'un bütünü henüz doğrulanmış sayılmaz; karşılaştırma referansı normal tam export'tur.

## 2. Nehir motoru — bekliyor

- [ ] Kaba havza analizi + aday koridorlarda ince çözünürlük + blok düzeyinde doğrulama.
- [ ] Kaynak/çıkış puanlamasında akış birikimi, vadi açıklığı, uzunluk ve kazı maliyeti.
- [ ] Merkez hattı, taban, su, genişlik ve kıyı için ortak ve değişmez nehir planı.
- [ ] Dağ deresi, ova nehri ve deniz ağzı davranışlarını ayır.
- [ ] Birleşimleri ağ olarak çöz; minimum genişliği hatta dik kesitlerde doğrula.
- [ ] Kazıyı sınırlı koridorda tut; koridor dışında yükseklik değişimini sıfırla.
- [ ] Arama sınırı ile fiziksel olarak uygun çıkış bulunmamasını ayır; iptal edilebilir genişleyen arama.

## 3. Nehir çizim ve önizleme — devam ediyor

- [x] İki ucu belirgin çizgiyi iç tepe/çukurlarda kesmeden uçtan uca seç.
- [x] Boyanmış dik açılı köşeleri çaprazdan atlama; gerçek çapraz çizgiyi bağlı tut.
- [x] Çizim/kaynak boyama açıldığında ilgili pencere modunu hatırla.

- [ ] Sıralı çizgi, başlangıç/bitiş, kol ve akış yönü bilgisi.
- [ ] Taşınabilir kontrol noktaları, parça silme ve bağlantı düzenleme.
- [ ] Genişlik/kazı/kıyı önizlemesi; Uygula öncesinde dünya değişmesin.
- [ ] Mod/stil korunması ve tek işlemde uygulama/geri alma.

## 4. Smooth ve su — bekliyor

- [ ] Ortak köşe yükseklikleriyle chunk/tile sınırında tutarlı slab/stair üretimi.
- [ ] Granite yatak, mud-brick kıyı, grass sınırı ve waterlogged durumunu birlikte çöz.
- [ ] river.bp ölçümlerini yeniden doğrula; sığ dere profilinde referans olarak kullan.
- [ ] Dört yön, çapraz, iç/dış köşe, deniz ağzı ve su güncellemeleri sonrası oyun testi.

## 5. Texture ve kar — bekliyor

- [ ] Giriş noktalarının ortak profil kullanması; düz grass, dik kaya, Y=150+ ve <30 derece beyaz/smooth snow kuralları.
- [ ] Moss yükseklik/eğim filtresi ve dağ materyallerinde yerel karışım davranışı.
- [ ] Nehir malzemelerini koruma; tekrar çalıştırmada istenmeyen birikim olmaması.
- [ ] Terrain, layer, palette ve ayar değişikliklerini iptalde birlikte geri alma.

## 6. Render/export performansı — bekliyor

- [ ] Gerçek 3B worker sınırı, görünür alan önceliği ve eski sonuçların reddi.
- [ ] Değişmez cache anahtarları; bütçe, istatistik ve disk cache yaşam döngüsü.
- [ ] Gerçek bellek zirvesi/GC ölçümü; ortak CPU ve bellek bütçesi.
- [ ] Büyük brush, overlay ve layer listelerinde arayüz gecikmesi ölçümü.
- [ ] Aynı çıktıyla 2K/4K/8K, yüksek dünya ve yoğun layer testleri; tekrarların ortanca süresi.

## 7. Vulkan — bekliyor

- [ ] CPU ölçümleri sonrası bağımsız, varsayılan kapalı önizleme modülü.
- [ ] Arazi/kamera/su/culling; destek yoksa CPU'ya dönüş.
- [ ] Export hızlandırması olarak sunmama; gerçek GPU süreleri ve farklı cihaz doğrulaması.

## Bu aşamanın doğrulaması

- Odak testleri: TileFingerprinterTest, DeltaExportCalculatorTest, ExportManifestTest — 13/13 başarılı.
- Genel test: Java 21 ile tüm Maven modülleri başarılı. 517 test: 515 başarılı, 2 atlanan, 0 hata.
- Değişen dosyalarda `git diff --check` başarılı. Idle geçmiş koruması kod incelemesi ve GUI modül derlemesiyle doğrulandı; açık uygulamada bekleme testi henüz yapılmadı.
- Kurulu uygulama: bu aşamadaki değişiklikler henüz dağıtılmadı.
- Kullanıcı dünyası/profili/brush dosyalarında işlem yapılmadı.

### Çizim hattı düzeltmesi (2026-09-04)

- Java 21 odak doğrulaması: RiverScriptBridgeTest + RiverToolsDialogTest — 29/29 başarılı.
- Yedi yeni senaryo: iç tepe/çukur, dik köşe, çapraz hat, ters giriş sırası,
  iptal, gerçek carver'a uçtan uca aktarım ve kapalı döngü geri dönüşü.
- İki ucu bulunmayan/çok kollu çizgiler eski seçim politikasını kullanmaya devam eder;
  açık uç/kol düzenleyicisi henüz eklenmedi. Boyama sırası kaydedilmiş değildir.
- Bu değişiklikler kurulu masaüstü paketine henüz dağıtılmadı.

### Çok ölçekli nehir araması V2 (2026-09-04)

- [x] Kaynak: özet vadi analizi, yerel ince arama, ortak süre/iptal bütçesi.
- [x] Kaynak: değişmez sonuç, otomatik modda önizleme ve açık Uygula akışı.
- [x] Test: Java 21, 2K/8K dar vadi dahil 538 test; 536 başarılı, 2 atlanan.
- [ ] Eski otomatik script adaptörü ve tam gerçek dünya kabul matrisi.
- [ ] Minecraft su güncellemeleri ve canlı arayüz kabul testi.
- [ ] Masaüstüne kuruldu (ayrı test paketi kurulmuş sayılmaz).

Ayrıntılı kapsam ve eksikler: [RIVER-SEARCH-V2-TR.md](RIVER-SEARCH-V2-TR.md).
