# Repairs dist\WorldPainter v2\app when a build could not replace dist (exe in use).
# Safe to run while WorldPainter is closed; app/ can usually be updated even if exe was locked during build.

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$AppFolderName = "WorldPainter v2"
$CfgFileName = "$AppFolderName.cfg"

$stagingApp = Join-Path $Root "dist-staging\$AppFolderName"
$distApp = Join-Path $Root "dist\$AppFolderName"

if (-not (Test-Path -LiteralPath (Join-Path $stagingApp "app\$CfgFileName"))) {
    throw "No complete staging build found. Run .\build-v2.ps1 first."
}

New-Item -ItemType Directory -Force -Path (Join-Path $distApp "app") | Out-Null
Copy-Item -Path (Join-Path $stagingApp "app\*") -Destination (Join-Path $distApp "app") -Force -Recurse

$exeSource = Join-Path $stagingApp "$AppFolderName.exe"
$exeDest = Join-Path $distApp "$AppFolderName.exe"
try {
    Copy-Item -LiteralPath $exeSource -Destination $exeDest -Force
} catch {
    Write-Host "Note: could not update exe (close WorldPainter and run again)." -ForegroundColor Yellow
}

if (-not (Test-Path -LiteralPath (Join-Path $distApp "app\$CfgFileName"))) {
    throw "Sync failed: $CfgFileName still missing in dist"
}

# jpackage launcher fails if .cfg has UTF-8 BOM (PowerShell Set-Content adds one)
$RequiredJavaOptions = @(
    "-Djpackage.app-version=1.0",
    "-Dorg.pepsoft.worldpainter.classifier=v2",
    "-XX:MaxRAMPercentage=45",
    "-XX:MinRAMPercentage=10",
    "-XX:+UseStringDeduplication",
    "-XX:+HeapDumpOnOutOfMemoryError"
)
$cfgPath = Join-Path $distApp "app\$CfgFileName"
$out = @(
    "[Application]",
    "app.classpath=`$APPDIR\WorldPainter-v2.jar",
    "app.mainclass=org.pepsoft.worldpainter.Main",
    "",
    "[JavaOptions]"
)
foreach ($option in $RequiredJavaOptions) {
    $out += "java-options=$option"
}
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllLines($cfgPath, $out, $utf8NoBom)

Write-Host "dist updated. Launch: $exeDest" -ForegroundColor Green
