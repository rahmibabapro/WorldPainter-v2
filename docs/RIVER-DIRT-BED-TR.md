# Nehir dirt tabani — 2026-09-14

## Kaynakta uygulanan

- Bagli dirt bolgeleri dort yonle, **ayni su kotunda** toplanir. Rota numarasi
  tek basina bolgeyi gecersiz kilmaz veya zehirlemez.
- Dirt muafiyeti artik `atOutletLevel` (butun nehirde ayni su kotu) degil;
  **konumsal** agiz diski ve birlesim yaricapidir. Kaynak kapagi, mevcut su,
  korunan hucre ve dunya alt siniri korunur.
- Farkli su kotu, plan disi veya korunan baglanti **yumusak sinirdir**; tek
  komşu butun bolgeyi atlatmaz.
- Hucre rolleri: `INTERIOR_DIRT`, `INTERIOR_GRANITE`, `WET_SHORE`,
  `PROTECTED_LINK`. Onizleme ve uygulama ayni plani kullanir.
- Islak kiyi malzemesi banka dayali kalir; ic granite slab/stair yeni tabana
  uyum saglayabilir. Dirt −1 yalniz isaretli ic dirt hucrelerinde.
- Skip nedenleri: `NO_DIRT`, `WORLD_FLOOR`, `NO_SUPPORT`, `GEOMETRY_FAILED`,
  `PROTECTED`. Onizlemede indirilen dirt ayri renk; ozet aday/indirilen/neden
  yazar. Kalici yeni `.world` tipi yoktur.
- Yalniz yeni olusturulan nehirler; eski WP/MC haritalari otomatik onarilmaz.

## Dogrulama

- Birim: uzun mud-brick kenarli serit, farkli rota/ayni su, farkli su soft
  sinir, korunan baglanti, tile siniri, idempotent preview.
- Export: dirt Y tam −1, malzeme dirt, bosalan voxel su; deniz kotundaki uzun
  yatak ve birlesim yani kol.
- Preview dunya revizyonunu degistirmez.

## Dagitim

- `build-v2.ps1 -SkipExe` ile dist JAR; aktif kurulumdan once yedek.
- Minecraft su tick / kacak ayri kabul adimidir.
