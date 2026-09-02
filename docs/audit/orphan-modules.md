# Orphan modules audit (WorldPainter v2)

Date: 2026-08-31. Goal: separate **file + unit test** from **production wiring**.

| Module | Tests | Production call site | Status |
|--------|-------|----------------------|--------|
| `OreClusterPlacer` / ResourcesExporter | density + golden | Yes (`ResourcesExporter`) | **Wired** |
| `ExportPlan` / FileInUse Retry | — | Save + load; promote-only if `*.wp-exporting` has `level.dat` | **Wired** |
| HeightMap tiled TIFF / `HeightMapSizeCheck` | yes | Yes (`HeightMapExporter`, `App`) | **Wired** |
| `WorldHealthInspector` | yes | Export success path (`ExportProgressDialog`, checkbox) | **Wired** — MCA header/sector only (no NBT); scans region + DIM-1 + DIM1 |
| `DeltaExportCalculator` / `ExportManifest` / `TileFingerprinter` | yes | Export dialog delta + `JavaWorldExporter` manifest | **Wired (experimental)** — surface + subsample + water/seeds |
| `TilePyramidCache` | yes | `WPTileProvider` zoomed paint | **Wired** — LRU + clear on zoom/dirty |
| `buildgraph.*` (`BuildKey`, `CellWorkset`, `PlacementPoint`, `ExplainLocationInspector`) | yes | No | Scaffolding — keep, demote claims |
| `spatial.geometry` / `analysis` / `road` | yes | No | Scaffolding — keep |
| Hydrology / river carvers | yes | No GUI apply path | Out of this sprint |
| MCA Selector bridge, AppCDS, jqwik/Jazzer, Recipe, World Modules, Poisson | — | Absent | Out of scope |

Rule: do not claim “completed product feature” until there is a call site in `App`, `ExportWorldDialog` / `ExportProgressDialog`, `JavaWorldExporter`, or `WPTileProvider`.
