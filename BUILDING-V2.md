# WorldPainter v2 — Windows build

Clean fork of [WorldPainter](https://github.com/Captain-Chaos/WorldPainter) (`upstream/master`, 2.27.x) with **performance / memory optimizations only** (no surface-smoothing or UI feature forks).

## What’s included

- Turbo / hollow export + `ExportMemoryBudget` thread planning
- Export height snapshots / chunk height cache / interior hollower
- Compartmentalised / incremental world save
- Idle memory guard + 3D tile render cache
- Optimized Minecraft game rules on export
- Separate branding/config: `%APPDATA%\WorldPainter [V2]`
- Windows packaging (`build-v2.ps1` → `dist\WorldPainter v2\`)

## Prerequisites

Same as upstream ([BUILDING.md](BUILDING.md)):

1. **JDK 17+** (JDK 21 preferred). Update [toolchains.xml](toolchains.xml) if needed.
2. **Maven 3.9+**
3. **JIDE** jars via `.\install-jide.ps1 -JideDir "C:\path\to\jide\lib"`

## Build

```powershell
.\build-v2.ps1 -SkipExe   # fat JAR only
.\build-v2.ps1            # JAR + Windows exe under dist\
```

Output: `dist\WorldPainter v2\WorldPainter v2.exe`

Launch: Desktop shortcut / `WorldPainter v2.bat`, or `.\launch-WorldPainter-v2.bat`

## License

GPL v3 — derived from Captain-Chaos/WorldPainter.
