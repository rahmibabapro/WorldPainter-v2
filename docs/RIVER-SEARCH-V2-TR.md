# Nehir Araması V2 — uygulama ve doğrulama durumu

## Kaynakta uygulandı (2026-09-04)

- `RiverTerrainSurvey`: gerçek minimum/koordinat örnekleri, 8 blok başlangıç,
  en fazla 1.048.576 özet hücresi ve 128 tile LRU. Gerçek yükseklikler değiştirilmez.
- `RiverSearchSession`: analiz dizilerinde Priority-Flood, akış birikimi/vadi
  sıralaması, bağımsız adaylar, 2 blok yerel arama ve genişleyen koridorlar.
  Ortak 120 saniye bütçesi, iptal, arama iş sınırları ve en fazla 10 Hz bildirim.
- Bütün adaylarda mevcut arazi-koruyucu `ShallowRiverCarver` ve engel kontrolleri
  son karar merciidir. Analiz dolgu yüksekliği araziye yazılmaz.
- Değişmez `RiverSearchResult`; altı ayrı sonuç durumu, yatak/su/merkez hattı
  ve tahmini bellek/süre bilgileri. Uygulama anında dünya revizyonu denetlenir.
- Nehir penceresinin Otomatik modu: Rota ara → renkli önizleme → açıkça Uygula.
  Önizleme dünya/layer yazmaz; uygulama tek geri alma çerçevesindedir.
- Eski çizim ve kaynak girişleri korunmuştur; mevcut eski script motorunu kullanır.

## Test edildi

- Java 21 tüm modüller: 538 test, 536 başarılı, 2 atlanan, 0 hata.
- 2K ve 8K sabit dar-vadi testleri çalıştırıldı (8K opt-in bayrağı dahil).
  Bunlar sentetik regresyon dünyalarıdır; gerçek dağ/ova performans garantisi değildir.
- Önizlemenin değişiklik yapmaması, iptal, eski sonuç reddi, tek adım geri alma,
  tekrar edilebilirlik ve korunan hücre testleri geçti.
- 256 MB bilgisi muhafazakâr veri-yapısı tahminidir; JVM gerçek tepe bellek
  ölçümü değildir. Dünya nesnelerinin mevcut belleği bu arama tahminine dahil değildir.

## Henüz tamamlanmadı

- Eski otomatik JavaScript girişinin yeni önizleme oturumuna adaptörü.
- Gerçek kıvrımlı dağ–ova–deniz, kapalı havza, kopuk tile ve birleşim için
  genişletilmiş uçtan uca kabul matrisi; kesite dik minimum genişlik ölçümü.
- Minecraft'ta export sonrası su güncellemeleri ve kaçak/daralma doğrulaması.
- Canlı pencerede uzun arama/iptal/kapatma testi; gerçek heap/GC ölçümü.
- Kurulu masaüstü paketine dağıtım. Ayrı hazırlanan paket kurulu uygulamayı,
  profilini, brush'ları ve 6 GB heap ayarını değiştirmez.

## Paketleme

Testleri geçirdikten sonra Maven `package -DskipTests`, ardından
`stage-river-search-v2.ps1` ayrı tarihli `dist` altına app-image hazırlar.
Script mevcut Java seçeneklerini korur; hiçbir eski paketi silmez.
Raporların varlığını kontrol etmek testleri yeniden çalıştırmanın yerine geçmez.

Hazırlanan paket: `dist/river-search-v2-20260904-231304/WorldPainter v2/`.
EXE/config/JAR varlığı ve paketlenen JAR'ın SHA-256 eşleşmesi doğrulandı.
Bu paket henüz canlı UI/Minecraft kabul testinden geçmedi ve masaüstü kısayoluna
bağlanmadı. Mevcut kurulu uygulama ve kullanıcı dosyaları değişmedi.

## Araştırma

- Priority-Flood: https://arxiv.org/abs/1511.04463
- Akış birikimi: https://richdem.readthedocs.io/en/latest/flow_accumulation.html
- Analiz/gerçek arazi ayrımı: https://richdem.readthedocs.io/en/latest/depression_breaching.html

Bağımsız Java uygulamasıdır; RichDEM kodu aktarılmamıştır. Her dünyada fiziksel
olarak uygulanabilir nehir garantisi verilmez; arama sınırı ayrı raporlanır.
