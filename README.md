# WorldPainter v2

[Captain-Chaos/WorldPainter](https://github.com/Captain-Chaos/WorldPainter) tabanlı fork. Minecraft **26.2** odaklı iyileştirmeler, daha hızlı boyama/export ve pratik düzeltmeler içerir.

Repo: [rahmibabapro/WorldPainter-v2](https://github.com/rahmibabapro/WorldPainter-v2)

## Ne değişti? (özet)

### Minecraft / export
- Export varsayılanı **Minecraft 26.2** (eski “unknown format” sorunu giderildi)
- Varsayılan kayıt klasörü: AstralRinth / Fabulously Optimized saves
- **Chest of goodies** varsayılan kapalı
- Export’a basınca donma: arayüzde `System.gc()` kaldırıldı
- Turbo export ve bellek bütçesi iyileştirmeleri

### Merge
- Minecraft açıkken kilitli dosya → anlaşılır uyarı (map’i kapatın)
- Chunk merge’de `ArrayIndexOutOfBounds` düzeltmesi
- Aynı map’i tekrar merge edince merdivenlerin üst üste binmesi engellendi
- Son seçilen merge klasörü hatırlanıyor

### Yüzey / su / deepslate
- Deepslate stairs & slab malzemeleri eklendi
- Deepslate kenarlarında hava boşluğu oluşması azaltıldı
- Deniz seviyesindeki stair/slab’ler doğru şekilde waterlogged oluyor

### Custom objects (ağaçlar)
- **Axiom `.bp`** dosyaları düzgün yükleniyor (önceden yatık/bozuk geliyordu)
- Yeni eklenen `.bp` ağaçlar: yatayda **auto** ortalama + dikey **Y = -1** (1 blok toprağa gömülür)

### Arayüz / araçlar
- Ctrl+Z geri alma düzeltildi
- Fırça intensity / boyama daha hızlı tepki veriyor
- Custom Layers & Custom Terrain panelleri + **+** butonları düzgün açılıyor
- Script Library sadeleştirildi (şu an “Taş Çimen” / stone-grass slope)

### Performans / bellek
- Turbo / hollow export, export bellek bütçesi (ölçümsüz thread artışı yok)
- MCA: pluggable `ChunkCompressor` (varsayılan JDK `DEFAULT_COMPRESSION`; hızlı yol: `-Dorg.pepsoft.worldpainter.deflateLevel=1`)
- Region flush: `parallelStream` varsayılan; VT opt-in (`virtualThreads=true`) ve hata fail-fast
- Idle memory guard, tile cache, optimize game rules
- Baseline: `docs/PERFORMANCE.md`, `scripts/run-baseline.ps1` (interactive ölçüm kapısı)
- Height blend helper (scalar); **experimental/unwired:** off-heap grid, GPU panel stub, GPU compute
- Opsiyonel Linear `.linear` (slot = ham NBT); Anvil varsayılan
- Ayrı config: `%APPDATA%\WorldPainter [V2]`

## Derleme / çalıştırma

Ayrıntılar: **[BUILDING-V2.md](BUILDING-V2.md)**

```powershell
.\build-v2.ps1
.\launch-WorldPainter-v2.bat
```

Çıktı: `dist\WorldPainter v2\WorldPainter v2.exe`

## Katkı

Collaborator daveti kabul edildikten sonra `worldpainter-v2` dalında çalışabilirsiniz.

## Lisans

GPL v3 (Captain-Chaos/WorldPainter türevi).
