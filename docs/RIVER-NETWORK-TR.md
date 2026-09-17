# Havza tabanli birlesen nehir agi

## 2026-09-09 — Test edilmis analiz temeli

- RiverDrainageGraph: degismez alici dizisi, topolojik sira, kok/havza
  kimligi, blok kare cinsinden katk i alani, Strahler ve alan tabanli genislik.
- Ortak govde ve katk i alani tek kez hesaplanir. Girdi dizileri kopyalanir.
- Dongu, gecersiz alici, NaN/negatif alan ve alan tasmasi reddedilir.
- RiverNetworkTopology: kaynak/birlesim/cikis dugumleri, aradaki ortak
  parcalar. Govde her kaynak icin yeniden kopyalanmaz; kol secimi gecici
  gorunumu degistirir, hidrolojik katk i alanini degistirmez.
- Y agi, sekiz kaynakli uc birlesim seviyeli ag, ayri havzalar, tek govde,
  ornekleme cozunurlugunden bagimsiz genislik, iptal ve degismezlik testleri.
- Java 21 reactor test basarili: 6 yeni grafik testi + 13 mevcut arama
  testi + 6 mevcut birlesim testi = 25, sifir hata.

## Sinirlar — ozellik henuz uygulamada kullanilabilir degil

Bu siniflar analiz temelidir; WorldPainter arayuzune veya kaziciya henuz
baglanmadi. Grafik koku fiziksel olarak dogrulanmis deniz cikisi demek
degildir. Topoloji arazi guvenligi ve su geometrisi onayi tasimaz.
RiverNetworkPlan adi ancak bu veriyi dogrulanmis kanal profilleriyle
birlestiren asamada kullanilacak.

## Siradaki uygulama adimlari

1. RiverTerrainSurvey'den gercek temsil edilen blok alanlari; mevcut
   Priority-Flood analizinin paylasilmasi, fiziksel cikis filtreleri.
2. 64 blok kaynak araligi/yan kol uzunlugu; 12/24/48 havza kaynak limiti,
   toplam 128 kaynak ve ortak 120 saniye butcesiyle siralama.
3. Parca boyunca degisken genislik, terminal su kotu ve tek birlesim tabani.
4. Granite/mud-brick ve dirt duzeltmesini ag genelinde tek kez planlama;
   birlesim alanlarini dirt indirmenin disinda tutma.
5. Havza/kol secimli onizleme, ret nedenleri, 5 dakika ayrintilandirma,
   revision dogrulamasi ve tek Undo.
6. 2K/8K, negatif Y, korunmus alan ve Minecraft export kabul testleri.
7. Ayri test paketi; oyun ici kabulden sonra yedekli aktif kurulum.

Bu tur aktif paket/profil/kisayol veya kullanici dunyasi degismedi.
Commit/push yapilmadi. Onceki dirt test paketi yeni ag ozelligini icermez.
