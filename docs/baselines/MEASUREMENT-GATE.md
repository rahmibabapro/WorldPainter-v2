# Measurement gate (audit follow-up)

- Created: 2026-08-31
- Label: gate-measured
- Bottleneck: **EXPORT_CPU (Resources & Terrain) + DEFLATE I/O**

## Automated Benchmark Results (ExportBenchmarkHarness)

| Mod / Konfigürasyon | Süre (ms) | Çıktı Boyutu (KB) | Peak Heap (MB) | Tasarruf / Hızlanma |
|---------------------|-----------|-------------------|----------------|---------------------|
| Full Export (Deflate Default) | **2233 ms** | 1032.78 KB | 66 MB | Referans (Baseline) |
| Full Export (Deflate Level 1) | **1146 ms** | 1032.78 KB | 55 MB | **~2.0x Hızlı** (Saving: 506ms → 147ms) |
| Full Export (Deflate Level 3) | **866 ms** | 1032.78 KB | 70 MB | **~2.5x Hızlı** (Saving: 506ms → 51ms) |
| Turbo Export (Level 1 + Deferred) | **380 ms** | 1032.78 KB | 58 MB | **~5.8x Hızlı** |
| Linear Format Export (.linear) | **339 ms** | 1215.71 KB | 88 MB | **~6.5x Hızlı** |

### Stage Breakdown Analizi (Full Export - 3486 ms stage toplamı):
- **Layer: Resources**: 2340 ms (**67.1%**) → En büyük CPU darboğazı!
- **Post processing**: 427 ms (12.3%)
- **Terrain**: 404 ms (11.6%)
- **Block properties**: 166 ms (4.8%)
- **Saving / MCA Flush**: 147 ms (4.2% @ Level 1, 506 ms @ Default)

## Fixes & Test Verification Applied

- **Virtual Threads Error-Injection Test (`WorldRegionVirtualThreadTest`)**: 
  - Fail-fast ve cancellation doğrulandı (hata durumunda anında abort, artık çift yazım veya sahipsiz iş parçacığı yok).
- **Linear slots**: uncompressed NBT + round-trip unit tests (`LinearRegionFileTest`).
- **Deflate**: varsayılan `DEFAULT_COMPRESSION`; Level 1 / 3 sysprop desteği.
- **ExportMemoryBudget**: 8/6/0.72 limitleri korundu.

## Karar ve Sonraki Öncelik:
1. **CPU Optimizasyonu:** `Layer: Resources` (cevher/yapı dağılımı) ve `Terrain` hesaplamalarındaki CPU döngülerini hafifletmek.
2. **Deflate I/O:** `Deflate Level 1` veya `Level 3` kullanarak MCA yazım süresini 506ms'den 51-147ms bandına çekmek.

## After — Resources cluster (2026-08-31)

Evidence: `docs/baselines/resources-cluster-1788168358170.md`

| Mode | Duration | Resources ms | Resources % |
|------|----------|--------------|-------------|
| Full Deflate Default | 1510 ms | **447** | **15.2%** (was 67.1%) |
| Full Deflate L1 | 722 ms | 61 | 5.0% |
| Turbo L1 | 505 ms | 0 (skipped) | — |

Gate **PASS**: Resources share 15.2% < 30%. Cluster is v2 default; `legacyNoise` / `-Dorg.pepsoft.worldpainter.resources.legacyNoise=true` restores Perlin.

## Measurement rules (post Track-1)

- Always run [`ExportBenchmarkHarness`](../../WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/perf/ExportBenchmarkHarness.java) with **3 runs per mode** and report **medians**.
- Accept gate when median Resources share **&lt; 30%** OR median Resources ms **&lt; prior × 0.25** (prior ≈ 2340 ms).
- Do **not** claim full-export **&lt; 400 ms** unless the **median** full duration proves it.
- Golden-chunk determinism: [`ResourcesExporterGoldenChunkTest`](../../WorldPainter/WPCore/src/test/java/org/pepsoft/worldpainter/layers/exporters/ResourcesExporterGoldenChunkTest.java).
