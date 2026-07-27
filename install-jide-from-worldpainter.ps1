param(
    [string]$WorldPainterLib = "C:\Program Files\WorldPainter\lib"
)

$ErrorActionPreference = "Stop"
$Mvn = "$env:USERPROFILE\.maven\maven-3.9.12\bin\mvn.cmd"
if (-not (Test-Path $Mvn)) {
    throw "Maven not found at $Mvn"
}
if (-not (Test-Path $WorldPainterLib)) {
    throw "WorldPainter lib folder not found: $WorldPainterLib"
}

$Version = "3.8.1"
$Required = @(
    @{ File = "jide-common.jar"; Id = "jide-common" },
    @{ File = "jide-dock.jar";   Id = "jide-dock" }
)

foreach ($artifact in $Required) {
    $path = Join-Path $WorldPainterLib $artifact.File
    if (-not (Test-Path $path)) {
        throw "Missing $($artifact.File) in $WorldPainterLib. Install WorldPainter from https://www.worldpainter.net first."
    }
    Write-Host "Installing $($artifact.Id) from installed WorldPainter..." -ForegroundColor Cyan
    & $Mvn install:install-file `
        "-Dfile=$path" `
        "-DgroupId=com.jidesoft" `
        "-DartifactId=$($artifact.Id)" `
        "-Dversion=$Version" `
        "-Dpackaging=jar"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$PlafPath = Join-Path $WorldPainterLib "jide-plaf-jdk7.jar"
if (Test-Path $PlafPath) {
    Write-Host "Installing jide-plaf-jdk7..." -ForegroundColor Cyan
    & $Mvn install:install-file `
        "-Dfile=$PlafPath" `
        "-DgroupId=com.jidesoft" `
        "-DartifactId=jide-plaf-jdk7" `
        "-Dversion=$Version" `
        "-Dpackaging=jar"
} else {
    Write-Host "jide-plaf-jdk7.jar not found; installing jide-common.jar alias (Windows runtime only)..." -ForegroundColor Yellow
    & $Mvn install:install-file `
        "-Dfile=$(Join-Path $WorldPainterLib 'jide-common.jar')" `
        "-DgroupId=com.jidesoft" `
        "-DartifactId=jide-plaf-jdk7" `
        "-Dversion=$Version" `
        "-Dpackaging=jar"
}
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

Write-Host "JIDE $Version installed from $WorldPainterLib" -ForegroundColor Green
Write-Host "Now run: .\build-v2.ps1"
