# Çizilen ağ için ortak derin vadi önerisi

## Yetki ve sınır

14 Eylül 2026: Kullanıcı, kendi çiziminde yaklaşık 13 blok ek kazı gerektiği açıklandıktan sonra **daha derin vadi önerisi** istedi. Ayrı DEEP önizleme eklendi: en fazla 16 blok ek arazi kazısı / 2 blok dolgu, 5 dakika ortak bütçe. LIGHT (2/0,5) ve STRONG (6/2) değişmedi. `.world` biçimi ve aktif kurulum değiştirilmedi.

## Kök neden ve çözüm

Tek tek kol planlamak, önce alçalan kolu daha yüksek bir birleşime bağlamaya çalışıyordu. Arazi düzenlemesinden tekrar su kotu hesaplamak yatağı yeniden düşürüyor; örtüşen koridorların omuzları başka kolların ıslak kesitlerine karışıyordu.

- `DrawnRiverJointPlanner` gerçek mevcut deniz/göl çıkışından bağlantılı havzayı çözer. Kaynak, birleşim ve çıkış kotları ortak kısıtlardır; birleşim kotu düşerse bütün bağlı profillere taşınır.
- `RiverWaterProfile`: aynı kesit derinliği, blok yuvarlama ve fiziksel mesafede su basamağı dağılımı arazi hazırlığında ve kanalda kullanılır. Deniz tabanı yüksekliği deniz su kotu yerine kullanılmaz.
- `TerrainAdjustmentPlan`: dış koridor ve ıslak kanal için ayrı yakınlık sahipliği. Ortak ıslak hücrelerin ve kuru su tutan sınırın hedefleri bütün havza tamamlandıktan sonra çözülür. Aynı yerde kazılar toplanmaz.
- Kanal, ana gövdeden yukarı kollara değişken genişlikle kurulur. Birleşime son dört yerel genişlik boyunca yumuşak genişleme uygulanır. Genişlik katkı sayısının görsel modelidir; fiziksel debi hesabı değildir.
- Birleşmiş ağ tamamlanmadan uygulanamaz. Son doğrulama su tutma, kazı/derinlik, yönlü komşu su basamakları, gerçek merkez hattı boyunca aşağı-akış ve kuru boşluk denetimini yapar. Reddedilen havzanın arazi önerisi de atılır.
- DEEP önizlemesinde en fazla dört blokluk kopuk uç yalnız doğrulanmış çıkışlı başka çizim grubuna bağlanabilir. Yalnız geçici maske değişir; hariç tutulan kenar varken otomatik onarım yapılmaz.
- Tamamı ana kanalın blok düzeyindeki ıslak kesiminde kalan, en fazla iki kaynak genişliği uzunluğundaki kısa çizgi ayrı bir ters akışlı kaynak sayılmaz. Kullanıcının çiziminde `(18,29)→(18,21)` bu durumdaydı. Islak kenar hesabı kanalın gerçek slab/tam-blok kesitiyle aynıdır, sadece iç yüzde 60 çekirdekle sınırlı değildir.
- Zorunlu dirt −1 ve üst kıyı detayları nihai ortak planda kalır; kıyı güvenliği gevşetilmez.

## Gerçek dünya ölçümü

Girdi: `Generated World.world`, 25 tile, Y=-64..512, 3068 River Path hücresi.
SHA-256: `975CFEA129C1750F98E358BDFAA57BDE8EC46E322ABE6FC173BDA5851848C491`.

Ayar: kaynak 5, ana kol üst genişliği 12, derinlik 1,1, smooth açık, DEEP.

- İki çıkışlı havza: toplam **8 kaynak, 6 birleşim, 14 parça**. Ana ağ 7 kaynak/6 birleşim/13 parça; diğer bağımsız dere 1 parça.
- Çizim boşluğunda iki geçici hücre tamamlandı. Bir kaplı kısa çizgi ayrı kaynak sayılmadı.
- Deniz içindeki `(261,258)` civarı 34×3 belirsiz çok-çıkışlı çizim grubu uygulanmadı. Sonuç bütün çizim hücrelerinin koşulsuz kabulü değildir.
- Arazi önerisi: **57.493 hücre**, azami ek kazı **16**, azami dolgu **0,80078125**. Kanal kazısı ve dirt istisnası bu sayıdan ayrıdır.
- Dirt −1: **3284 hücre**. Üst kıyı önerisi: **860 hücre**; güvenli olmayan kıyı noktaları tam blok kalır.
- Son yerel çalıştırmada yaklaşık **8,6 saniye**; bu genel performans garantisi değildir.

## Doğrulama

Java 21 ile 18 test sınıfında **131 test**, hata/başarısızlık yok. Drawn graph/session/valley/export, terrain adjustment, dirt, waterline, eski script köprüleri ve nehir diyalogları dahil. Eski script köprüsü testinde eksik `params` bağlamı gerçek ScriptRunner gibi sağlandı; beklentiler silinmedi veya gevşetilmedi.

Paketlenmiş JAR ile kullanıcı dünyasının kopyası bellekte uygulandı:

- Önizleme dünya revizyonunu değiştirmedi.
- 200 gerçek export chunk'ında 2349 merkez hattı örneğinde su (veya su içeren blok) doğrulandı.
- Merkez hattında aşağı akışta yükselen su kotu yok.
- Mevcut deniz/göl su kotları aynı; önizleme değişim alanı dışında yükseklik, terrain, su ve detay marker'ları aynı.
- Tek Undo, bütün yükseklik/su/terrain/detay marker hash'ini eski hâline getirdi.
- Asıl masaüstü dosyasının SHA-256 değeri aynı kaldı. Test aracı dünyayı diske kaydetmez.

Tekrarlama: `WorldPainter/tools/CheckDrawnWorld.java`, mod `verify-deep`, argümanlar `input.world verify-deep 5 12 1.1 yeni-rapor.txt`. Java classpath yalnız paket JAR'ı olmalıdır. Son rapor `dist/acceptance-generated-world-20260914/deep-final-acceptance.txt`.

## Dağıtım ve kalan kabul

Ayrı paket: `dist/WorldPainter-v2-deep-preview-20260914/WorldPainter v2.exe`. Ayrı profil classifier'ı `v2-deep-preview-20260914`; mevcut V2 profiline yazmaz. Değiştirilmemiş dünya kopyası paket yanında verilir.

Kaynak **uygulandı**; odaklı testler ve bellekte gerçek chunk export'u **geçti**; aktif kurulum **değiştirilmedi**. Bütün proje testleri çalıştırılmış değildir. Minecraft oyun içi su güncellemeleri, görsel arazi kabulü ve masaüstü düğme akışının canlı denemesi bekliyor. Her çizim/dünya için sonuç garantisi yok.
