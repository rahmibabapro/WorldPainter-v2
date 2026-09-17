# Nehir script arama duzeltmesi — 2026-09-08

## Kaynakta tamamlanan ve test edilen ilk adim

- RiverWaterProfile ile mevcut kuru su kotu, minimum islak derinlik ve kazi
  toleransi ShallowRiverRouter / ShallowRiverCarver arasinda ortaklastirildi.
- Ekrandaki 68.80078125 arazi / 67 su kotu / 0.65 minimum derinlik vakasi
  2.45078125 blok kazi gerektiriyor; 1.85 sinirini asmasi testle dogrulandi.
- Bu refaktor guvenlik sinirlarini degistirmez ve tek basina rota bulma
  basarisini artirdigi iddia edilmez.
- Java 21: RiverWaterProfileTest (4), RiverSearchSessionTest (13),
  ShallowRiverRouterTest (16), TerrainPreservingRiverCarverTest (10):
  toplam 43 test, sifir hata. Reactor build basarili.

## Henuz tamamlanmayanlar

- ScriptRunner/otomatik script girisini RiverSearchSession onizlemesine
  baglayan adaptor. Scriptin plan.apply() adimi bu yolda calismamali.
- Ayarlarin acik eslenmesi: mevcut session granite detayini true ile
  olusturuyor; scriptteki detail secimi ve ozel oranlar sessizce atlanmamali.
- Tam kaynak kesiti on elemesi ve ayni vadide alternatif kaynak siralama.
- Eski scriptin 6 tur ve farkli genisliklerde yeni router olusturma yolunu
  ortak 120 saniye butcesine tasima; tekrarlanan denemeleri onleme.
- Genel Error yerine sonuc/onizleme arayuzu ve gercek giris entegrasyon testi.
- Kullanici dunya kopyasinda tekrar uretim, Minecraft su guncelleme testi.
- Ayri test paketi ve yedekli dagitim.

## Kurulum durumu

- Aktif exe yolu dist/WorldPainter v2/WorldPainter v2.exe; iki acik surec
  goruldu (3004, 6840). Uygulamalar kapatilmadi.
- Aktif Java paket kimligi henuz dogrulanmadi. Profil/cache script SHA256
  kaynak script ile ayni; bu Java siniflarinin ayni oldugunu kanitlamaz.
- Kullanici dunyasi, profil ve aktif paket degistirilmedi; push yapilmadi.
