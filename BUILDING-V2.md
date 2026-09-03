# WorldPainter v2 — Windows build

Fork of [WorldPainter](https://github.com/Captain-Chaos/WorldPainter) with performance and memory improvements, surface smoothing, River Designer, and Axiom terrain/smooth-snow tools. See [other-PC update instructions](docs/UPDATE-OTHER-PC-TR.md) and [Axiom texture documentation](docs/AXIOM-TEXTURE.md).

## What’s included

- Turbo / hollow export + `ExportMemoryBudget` thread planning
- MCA zlib via `ChunkCompressor` (default JDK compression; opt-in level 1)
- Optional Linear `.linear` (uncompressed NBT slots + Zstd) for AgeOfMC forks
- Phase-0 baseline tooling (`docs/PERFORMANCE.md`, `scripts/run-baseline.ps1`) — fill interactive metrics before tuning
- Experimental stubs (unwired): off-heap grid, GPU viewport Java2D panel, GPU compute SPI
- Export height snapshots / chunk height cache / interior hollower
- Idle memory guard + 3D tile render cache
- Optimized Minecraft game rules on export
- Separate branding/config: `%APPDATA%\WorldPainter [V2]`
- Windows packaging (`build-v2.ps1` → `dist\WorldPainter v2\`)

## Prerequisites

Same as upstream ([BUILDING.md](BUILDING.md)):

1. **JDK 17+** (JDK 21 preferred). Update [toolchains.xml](toolchains.xml) if needed (Adoptium path must exist).
2. **Maven 3.9+** (default: `%USERPROFILE%\.maven\maven-3.9.12`)
3. **JIDE** jars via `.\install-jide.ps1 -JideDir "C:\path\to\jide\lib"`

## Performance baseline

```powershell
.\scripts\run-baseline.ps1
```

See [docs/PERFORMANCE.md](docs/PERFORMANCE.md).

## Build

```powershell
.\build-v2.ps1 -SkipExe   # fat JAR only
.\build-v2.ps1            # JAR + Windows exe under dist\
```

Output: `dist\WorldPainter v2\WorldPainter v2.exe`

Launch: Desktop shortcut / `WorldPainter v2.bat`, or `.\launch-WorldPainter-v2.bat`

## License

GPL v3 — derived from Captain-Chaos/WorldPainter.
