# Erken ince arama siniri — 2026-09-09

## Neden

RiverSearchSession toplam 360.000 dugum veya 24.000.000 kenar orneginde
tum aramayi durduruyordu. Bu sayilar onceki koridorlarin birikimli isiydi;
canli bellekte tutulan dugum sayisi degildi. Hizli makinelerde 120 saniye
dolmadan birkac saniyede bu esige ulasilabiliyordu.

## Kaynak duzeltmesi

- Birikimli dugum/ornek sayaclari long telemetri oldu; tum aramayi kesmiyor.
- Her koridorun 12.000 genisletme / 96.000 maliyet kaydi siniri korundu.
- Ortak sure, iptal, dunya revizyonu ve analiz bellek kontrolleri korundu.
- Kazi, derinlik, korunan alan ve minimum genislik degistirilmedi.
- Adaylar biterse arama 120 saniyeden once bitebilir. Bu bir asgari bekleme
  suresi veya her dunyada nehir garantisi degildir.

## Dogrulama

- Yeni test onceki koridorlardan 360.000 dugum ve 24.000.000 ornek
  harcanmis durumu kurar; kalan surede bilinen vadi bulunur, dunya degismez.
- RiverSearchSessionTest, RiverSearchLargeWorldTest, RiverSearchDialogTest
  Java 21 ile basarili.
- Tam Maven package: 571 test, 0 hata/basarisizlik, 3 atlanan (568 gecti).
- Ayri paket: dist/river-search-v2-20260909-121926/WorldPainter v2.
- Kullanici uygulamayi kapattiktan sonra aktif app jar guncellendi.
  Yedek: dist/backup-before-search-limit-20260909-121945.
  Kurulu SHA256: 3C88CC261779ABF135E5A37DEA555B0F8EB2A949783457D5F3D06BDFF9BBBF8D.
- Masaustu kisayolu ve exe dogrulandi; profil, dunyalar ve RAM ayari korunuyor.
- Kullanicinin kendi dunyasinda rota bulma ve Minecraft kabul testi henuz yapilmadi.
