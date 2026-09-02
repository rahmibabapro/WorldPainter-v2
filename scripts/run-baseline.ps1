param(
    [switch]$Jfr,
    [string]$OutDir = "docs/baselines"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
Set-Location $Root

$Mvn = "$env:USERPROFILE\.maven\maven-3.9.12\bin\mvn.cmd"
if (-not (Test-Path $Mvn)) {
    $Mvn = "mvn"
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$report = Join-Path $OutDir "baseline-$stamp.md"

Write-Host "Running WPCore unit tests (includes Phase-0 classifier + compressor benchmarks scaffolding)..." -ForegroundColor Cyan

$jfrArgs = ""
if ($Jfr) {
    $jfrFile = Join-Path $OutDir "baseline-$stamp.jfr"
    $jfrArgs = "-XX:StartFlightRecording=filename=$jfrFile,settings=profile,duration=60s"
    Write-Host "JFR enabled -> $jfrFile"
}

& $Mvn -f WorldPainter/pom.xml -pl WPCore -am test "-Dtest=BottleneckClassifierTest,ChunkCompressorTest,HeightBlendKernelsTest,LinearRegionFileTest,OffHeapShortGridTest,CpuHydraulicErosionTest,GpuTileTextureAtlasTest" "-Dsurefire.argLine=$jfrArgs -Duser.home=$Root/WorldPainter/WPCore/target"
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

@"
# WorldPainter v2 performance baseline

- Created: $(Get-Date -Format o)
- Label: automated-unit+$stamp
- Bottleneck: **UNKNOWN** (fill after interactive export/paint run)

## Timings (ms)

- unitSuite: see Maven surefire logs
- paintBrush: (manual)
- panZoomFrame: (manual)
- exportTurbo: (manual)
- exportFull: (manual)

## Memory (MB)

- peakHeap: (manual / JFR)

## Notes

Phase-0 gate: run interactive export on a fixed 2K and 8K world, then replace UNKNOWN via BottleneckClassifier metrics.
See docs/PERFORMANCE.md.
"@ | Set-Content -Path $report -Encoding UTF8

Write-Host "Wrote $report" -ForegroundColor Green
