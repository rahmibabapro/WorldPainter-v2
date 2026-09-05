# Script güvenilirliği denetimi — 2026-09-03

## Son durum — 17:08 güncellemesi

Bu denetimin düzeltmeleri nehir yönlendirme düzeltmeleriyle birlikte aktif
masaüstü kurulumuna aktarıldı. Son doğrulama: 360 Java + 47 Node testi,
14 profil script girdisi ve 354 paket dosyası. Profil/brush/kayıt yedekleri
korundu. JIDE açılış uyarısı nedeniyle ana ekranın açıldığı henüz doğrulanmadı.
[Güncel kurulum ve yönlendirme raporu](RIVER-ROUTING-RELIABILITY-TR.md).
Aşağıdaki 16:19 paket/kurulum bilgileri önceki aşamanın tarihsel kaydıdır.

## Kapsam ve yöntem

Paket içindeki 12 benzersiz JavaScript dosyası; nehir/yol, Snowify/kar,
Axiom/doku, taş-çimen ve su işlemleri incelendi. Aynı menülerin çağırdığı
Java motorları ve ortak Run Script çalıştırıcısı da kapsama alındı.
Görünüm profilleri, terrain karışım oranları ve mevcut kar yükseklikleri
yeniden tasarlanmadı. Kullanıcı dünyasında script çalıştırılmadı.

## Araştırmanın uygulamaya etkisi

- WorldPainter'ın resmî API'si, döngülerde açık iptal kontrolü gerektiğini ve
  standart çalıştırıcının yarım değişiklikleri otomatik geri almadığını belirtir.
  Bu nedenle iptal kontrolleri eklendi; otomatik geri alma yalnız kaynak metni
  paket içeriğiyle birebir eşleşen, işlem kapsamı bilinen scriptlere açıldı.
  [WorldPainter Scripting API](https://www.worldpainter.net/trac/wiki/Scripting/API)
- Java'nın script API'si, aynı motorun önceki çalıştırmadan değişken/fonksiyon
  durumu taşıyabileceğini açıklar. Paylaşılan motor/derlenmiş-script önbelleği
  kaldırıldı; her çalıştırma yeni motor kullanır.
  [Java 21 ScriptEngine](https://docs.oracle.com/en/java/javase/21/docs/api/java.scripting/javax/script/ScriptEngine.html)
- Swing bileşenlerine ana arayüz iş parçacığından erişilmeli; iptal eden işlemin
  kendisi de iptale yanıt vermelidir. Parametreler başlamadan arayüzde alınır,
  metin çıktısı arayüz kuyruğuna sınırlı ve birleştirilmiş şekilde aktarılır.
  [Java 21 SwingWorker](https://docs.oracle.com/en/java/javase/21/docs/api/java.desktop/javax/swing/SwingWorker.html)
- Yerel `Dimension`/UndoManager uygulaması ve upstream çalıştırıcı incelendi.
  Event inhibition burada referans sayacı değil booleandır; iç içe çağrılar
  yalnız kendi açtıkları kilidi bırakır. İç geri alma sonrasında önceki kullanıcı
  adımını geri almamak için `Dimension.undoChanges()` kirli kontrolü kullanılır.
  [WorldPainter Dimension API](https://www.worldpainter.net/javadoc/org/pepsoft/worldpainter/Dimension.html),
  [upstream ScriptRunner](https://github.com/Captain-Chaos/WorldPainter/blob/master/WorldPainter/WPGUI/src/main/java/org/pepsoft/worldpainter/tools/scripts/ScriptRunner.java)

## Uygulanan düzeltmeler

- [x] Doku scriptinde opsiyonel filtrelerin adının, değer yerine okunması düzeltildi.
  Sınır satırları/negatif koordinatlar dahil yalnız mevcut tile'lar taranır.
- [x] Axiom, kar, genel işlemler ve yol akışlarına korumalı/eksik arazi ve
  geçersiz sayılar için kontroller eklendi. İlgili su/lav alanları korunur.
- [x] Kar menüsünün eski parametre isimleri güncel Snowify arayüzüne bağlandı.
  Boş/NaN/sonsuz ve hatalı tam sayı değerleri Java'ya dönüştürülmeden reddedilir.
- [x] Kar tekrar uygulanırken maskenin tekrar incelmesi, SnowDepth kalıntıları,
  aynı taramada değişmiş yükseklikten eğim ölçümü ve dünya tavanında opsiyonel
  kar yükseltme sorunları düzeltildi.
- [x] Nehir çizgisi kabul edilmeden marker katmanına yapılan yazı kaldırıldı.
  Çizgi taraması iptal edilebilir ve sınırlı; koordinat taşması, aşırı örnekleme
  ve sessizce eski moda düşme engellendi. Arazi koruma geometrisi korunur.
- [x] Eski Y=0 tabanlı dünyalarda `-1` su kotunun unsigned depolamada
  255/65535'e dönüşmesi su kaldırma scriptlerinde ve native dry-world yolunda
  giderildi. Kaldırma eşiği ile depolanabilir minimum kot ayrı tutulur.
- [x] Yol scriptinin kendi `.js` dosyasını yeniden yazması kaldırıldı.
  Yanlış dimension seçimi, boş opsiyonel maskeler ve sınırsız örtüşme listeleri
  giderildi; önce toplam/sayı ile sınırlı plan, sonra uygulama kullanılır.
- [x] Native Axiom ve taş/çimen işlemlerinde hata/iptal geri dönüşü,
  Undo ön koşulu ve dış event inhibition'ın korunması eklendi.
- [x] Ortak çalıştırıcı artık önceki dünya/parametre/global değişkeni tutmaz.
  Hata/iptalde bilinen dahili scriptlerin mevcut dimension tile değişiklikleri
  ve kar ayarları geri alınır. Native blueprint kendi palette işlemini yönetir.
- [x] Çıktı kuyruğu 65.536, görünür çıktı 524.288 karakter ile sınırlanır;
  aşırı çıktı üreten script tüm RAM'i arayüz kuyruğunda tüketmez.
- [x] Form editörünü yeniden almak kullanıcının seçtiği değeri varsayılana
  sıfırlamaz. Boş opsiyonel sayı alanı eski sayıyı gizlice göndermez.
- [x] Çalışma sürerken parametre değiştirmek Run düğmesini yeniden açamaz;
  aynı pencerede ikinci eşzamanlı işlem engellenir. Host yönetimli işlemlerde
  son iptal kontrolü kayıt kararından hemen önce de yapılır.
- [x] Script önbelleği zaman damgasıyla değil içerikle yenilenir. Değişen eski
  kopya önce benzersiz `bundled-cache/backups` klasörüne yedeklenir; sonra
  atomik değiştirilir. Birebir aynı içerik tekrar yazılmaz.

## Doğrulama / teslim

- [x] 12 scriptin JavaScript sözdizimi kontrolü geçti.
- [x] 41 Node regresyon testi geçti; hata/atlanan test yok.
- [x] İlk çekirdek test grubu geçti; gerçek dünya yedeğinde salt-okunur denize
  bağlı 69,5 blokluk rota hâlâ bulunuyor. Veri dosyası/sayaçları değişmedi.
- [x] 35 sınıfta 337 Java/Nashorn/gerçek UndoManager/Swing testi geçti;
  hata, atlanan test ve test hatası yok. İlk koşudaki beş fixture sorunu
  başlangıç temasını açıkça tanımlayarak giderildi; assertion'lar gevşetilmedi.
- [x] Java 21 derlemesi ve Windows app-image üretimi başarılı. 354 paket
  dosyası, 83 class/script girdisi (12 script dahil) ve dört plugin sağlayıcısı
  doğrulandı. Mevcut `-Xms512m -Xmx6g` korundu.
- [x] Yedekli aktif kurulum, yeni nehir paketiyle 17:08'de tamamlandı; yukarıdaki güncel rapora bakın.
- [ ] Kullanıcının nehir uygulanmamış dünya kopyasında Minecraft görsel kabulü.

Raporlar: `C:\Users\Admin\Documents\WorldPainter-script-reliability-20260903-160939\verification`.

Hazır paket: `C:\Users\Admin\Documents\WorldPainter-script-reliability-20260903-160939\package\WorldPainter v2`.
JAR SHA-256: `C447C6341BA8880193B2C8B9983593D9F8DF5FFEFFA96811E25C3152E0373326`.
16:19 kontrolünde eski uygulama açıktı; aktif uygulamaya/profil önbelleğine
bu paketin dosyaları yazılmadı. Kurulum için kullanıcıdan normal kapatma istendi.

## Sınırlar

Sıfır hata veya her dünya için uygun rota garantisi verilmez. Otomatik geri
alma, özel/değiştirilmiş üçüncü taraf scriptlerin dosya yazmasını veya başka
dünyalara yaptığı işlemleri kapsamaz. Büyük yol/nehir maskesi güvenli bellek/
çalışma bütçesini aşarsa dünya değiştirilmeden açık hata verir; daha küçük
işaretli alan gerekir. Başarılı işlemlerin tek adımlı geri alma testleri tile
verisini kapsar; dünya çapındaki bütün metadata ayarlarına genel bir işlem
garantisi verilmez. Tam 8K×8K Minecraft export/görsel test bu denetimde yapılmadı.
