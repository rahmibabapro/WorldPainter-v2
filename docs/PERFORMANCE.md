# WorldPainter v2 — Performance baseline (Phase 0)

**Status:** measurement gate open. Unit-test green ≠ product readiness. Interactive 2K/8K export + JFR numbers must fill `docs/baselines/` before raising memory/deflate/GPU work.

## How to capture

```powershell
.\scripts\run-baseline.ps1
```

Or with JFR:

```powershell
.\scripts\run-baseline.ps1 -Jfr
```

Manual in-app: export a fixed world with turbo and full modes; timings appear in the log via `ExportTimingReporter`. Write a markdown report with:

```java
ExportTimingReporter.writeBaselineReport("2k-turbo", stats, Path.of("docs/baselines/latest.md"), extraUiMs);
```

Required interactive metrics before changing defaults: `paintBrush`, `panZoomFrame`, `exportTurbo`, `exportFull`, `.mca` byte size, peak heap (JFR).

## Bottleneck labels

| Label | Meaning | Next step (only after measured) |
|-------|---------|----------------------------------|
| `UI` | paint / pan-zoom | experimental GPU viewport (not wired) |
| `DEFLATE` | zlib MCA write | try `-Dorg.pepsoft.worldpainter.deflateLevel=1` A/B |
| `EXPORT_CPU` | chunk factory | turbo / budget (do not raise caps without heap proof) |
| `HEAP` | GC / footprint | experimental off-heap (not wired to Tile) |
| `MIXED` | several | prioritize highest ms share |

## Feature flags (honest defaults)

| Property | Default | Effect |
|----------|---------|--------|
| `org.pepsoft.worldpainter.deflateLevel` | unset (= JDK `DEFAULT_COMPRESSION`) | Set `1` for fast/larger MCA. **Turbo export sets this automatically.** |
| `org.pepsoft.worldpainter.resources.legacyNoise` | unset (= v2 cluster default) | `true` restores per-block 3D Perlin Resources path |
| `org.pepsoft.worldpainter.libdeflate.compressor` | unset | Optional `ChunkCompressor` SPI class (opt-in; not default — JDK L1 wins measured A/B) |
| `org.pepsoft.worldpainter.export.virtualThreads` | `false` | Region flush VT; CPU-bound NBT+deflate — keep off unless proven |
| `org.pepsoft.worldpainter.simd` | `true` | Scalar unrolled height blend helper (not Vector API) |
| `org.pepsoft.worldpainter.offHeapTiles` | `false` | **Experimental / unwired** — `OffHeapShortGrid` exists; Tile does not use it |
| `org.pepsoft.worldpainter.gpuViewport` | `false` | **Experimental / unwired** — panel is Java2D blit stub; no GL draw |
| `org.pepsoft.worldpainter.gpuCompute` | `false` | **Experimental / unwired** — CPU erosion fallback only unless backend SPI set |

## Linear export

Slot payload = **uncompressed NBT** (LinearRegionFileFormatTools). Anvil `.mca` remains primary. UI checkbox writes sibling `.linear` after export. Requires LinearPaper/compatible fork smoke before relying on it in production.

## Red list (until measured)

- Vulkan / LWJGL viewport rewrite
- Native libdeflate packaging
- Off-heap Tile rewrite
- Raising `ExportMemoryBudget` caps
- Claiming speed % without baseline files
