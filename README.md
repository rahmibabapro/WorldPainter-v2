# WorldPainter v2

Clean fork of [Captain-Chaos/WorldPainter](https://github.com/Captain-Chaos/WorldPainter) based on **upstream 2.27.x**, with **performance and memory optimizations only**.

Feature forks (surface smoothing, FlatLaf UI, biome packs, script library, etc.) are **not** included. Full previous fork tip is kept locally as branch `worldpainter-v2-legacy`.

## Optimizations

- Turbo / hollow export + heap-aware `ExportMemoryBudget`
- Export height snapshots / chunk height cache / interior hollower
- Compartmentalised / incremental `.world` save
- Idle memory guard + 3D tile render cache
- Optimized Minecraft game rules on export
- Separate config: `%APPDATA%\WorldPainter [V2]`

## Build & run

See **[BUILDING-V2.md](BUILDING-V2.md)**.

```powershell
.\build-v2.ps1
.\launch-WorldPainter-v2.bat
```

Desktop launcher: `WorldPainter v2.bat` → `dist\WorldPainter v2\WorldPainter v2.exe`

## License

GPL v3 (derived work).
