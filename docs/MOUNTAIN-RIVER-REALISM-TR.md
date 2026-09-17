# Dağ ve nehir gerçekçiliği — uygulama takibi

## Sabit kararlar

Mevcut araziyi koruma varsayılandır. Yeniden şekillendirme ayrı önizleme ve
uygulama onayı gerektirir: yatak dışı ek kazı en fazla 2 blok, dolgu 0,5 blok;
yan koridor genişliğin iki katı ve her yanda en fazla 32 blok. Mevcut sığ
kanal güvenliği gevşetilmez. İlk taslak 120 saniye; seçili havzada ayrıntılı
arama 5 dakika olarak ayrıca uygulanacaktır. Kullanıcı dünyası otomatik işlenmez.

## Aşamalar

- [x] Kaynak: akışa dik kesit ile boyuna eğimin ayrılması.
- [x] Kaynak: beklenmeyen program hatalarının çıkış bulunamadı diye gizlenmemesi.
- [x] Kaynak: bütün uygun kaynakları tutan primitive öncelik kuyruğu;
  64 rota / 512 kaynak kesmesi kaldırıldı, rota talep edildiğinde üretiliyor.
- [x] Kaynak: genişleyen koridor aynı rotayı tekrar döndürdüğünde son
  başarısız geometrinin yeniden doğrulanmaması.
- [ ] Havzalar arasında çeşitlilik ve bütün başarısız denemelerin sınırlı kayıtları.
- [ ] TerrainAdjustmentPlan, sınırlı aşındırma/tortu önerisi ve tam doğrulama.
- [ ] Akarsu ağı, birleşimde ortak taban/su ve değişken genişlik.
- [ ] Granite/mud-brick, grass sınırı ve Minecraft su güncellemeleri kabulü.
- [ ] Seçili havza ayrıntılandırması, önce/sonra, ayrı kazı/dolgu gösterimi.
- [ ] Eski scriptlerin ayar kaybetmeden ortak plan adaptörü.
- [ ] Gerçek heap/GC, tile sınırında akış ve tortu sürekliliği ölçümü.
- [ ] Ayrı test paketi, Minecraft kabulü ve yedekli masaüstü kurulumu.
- [ ] İlk sürüm kabulünden sonra yeni dağ üretimi; GPU deneyi daha sonra.

## Aday kuyruğunun sınırları

Bir milyon hücreye kadar özet içindeki uygun kaynak kimlikleri tutulur;
bütün tam yollar belleğe alınmaz. Puanlar bir kez hesaplanır. Aynı kaynak
kuyruktan bir kez çıkar. Tam doğrulanmış yeterli uzunlukta istenen rota sayısı
bulununca durulur; küresel en iyi çözüm bulunduğu iddia edilmez. Diğer
durumlarda ortak süre/düğüm/örnek güvenlik sınırları geçerlidir.

Yakın başarısız kaynaklar artık birbirini otomatik elemez. Buna karşın farklı
havzalara adil arama bütçesi dağıtımı bu ilk kuyruk değişikliğinde tamamlanmış
değildir. Yeniden şekillendirme ve yeni dağ üretimi henüz kullanıcı arayüzüne
eklenmedi. Hiçbir deneysel eski erozyon/carver otomatik etkinleştirilmedi.

Kaynak, test ve kurulum durumları ayrıdır. Bu turda mevcut kurulu paket,
profil, brush ve dünyalar değiştirilmedi; GitHub'a otomatik gönderim yapılmadı.

## Doğrulama — 2026-09-07

Java 21 bütün modüller başarılı: 551 test, 549 başarılı, 2 atlanan, sıfır hata.
8K opt-in dar-vadi testi bu çalıştırmada etkin. Primitive kuyruk 1024 adayın
tamamını sıralı çıkarma, giriş sırasından bağımsızlık ve kapasite sınırı ile
test edildi. Bunlar gerçek dağ/Minecraft kabul testlerinin yerine geçmez.
