# Çizilen nehir — tek düğmeli hazırlama

## Akış

Ana işlem **Nehri hazırla** (`DrawnRiverSession.prepare()`). Menü ve çizim scripti aynı pencereyi açar.

1. Araziyi koruyan çözüm (koridor / LIGHT), en fazla **120 sn**.
2. Tamamlanamayan kollar varsa **DrawnRiverJointPlanner** ile ortak vadi: 16 blok ek kazı / 2 dolgu.
3. Tek süre bütçesi **5 dakika**; ikinci aşama kalan süreyi kullanır, sayaç sıfırlanmaz.
4. Koruma bütün uygulanabilir kolları bitirdiyse derin kazı yok.
5. İkinci aşama iptal, süre dolumu **veya beklenmeyen hesap hatasında** önceki tamamen doğrulanmış sonuç korunur. Yeni ağ, önceki geçerli kolların uçlarını kapsıyorsa kabul edilir; yalnız daha fazla parça yeterli değildir.

Eski `search()` / `search(boolean)` / `searchDeepValley()` uyumluluk için durur.

## Önizleme

Yazmaz. Kaynak / birleşim / parça, aşama, azami ek kazı, atlanan gruplar, sürüm kimliği (`VERSION (BUILD) · nehri-hazırla/joint-16`).

İptal, süre dolumu ve hesap hatası ayrı açıklanır. Dünya / çizim / hariç tutulan kenar değişince Uygula kapanır.

## Kabul (örnek dünya kopyası)

Girdi SHA-256: `975CFEA129C1750F98E358BDFAA57BDE8EC46E322ABE6FC173BDA5851848C491`  
Ayar: kaynak 5, ana 12, derinlik 1,1. `prepare` / `verify-prepare`: **8 kaynak, 6 birleşim, 14 parça**, aşama `DEEP_TERRAIN`, azami ek kazı 16, atlanan deniz grubu 1. Önizleme yazmaz; uygulama + 2349 export su örneği + tek Undo doğrulandı. Masaüstü `.world` hash’i değişmedi.

Java 21: 18 sınıfta **137** odaklı test (önceki 135 + hesap hatası geri dönüşü + kol kapsama).

## Teslim

- Yedek: `dist\backup-before-prepare-keep-arms-20260914-163052`
- Aktif JAR: `dist\WorldPainter v2\app\WorldPainter-v2.jar` SHA-256 `218C05ECE160CEB24B04B491C7D58864BA0C02C919E79E7ADEDBAD5452911D38`
- Kısayol: `Desktop\WorldPainter V2.lnk` → `dist\WorldPainter v2\WorldPainter v2.exe`
- Kimlik: `2.0.0-v2-SNAPSHOT (20260914163054) · nehri-hazırla/joint-16`

Masaüstü düğme (Uygula / Undo / Redo) ve Minecraft su güncellemesi hâlâ kullanıcı denemesine bağlıdır. Ayrı `WorldPainter-v2-deep-preview-20260914` klasörü nihai teslim değildir.
