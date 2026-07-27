param(
    [switch]$SkipExe,
    [string]$JdkHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot",
    [string]$MavenHome = "$env:USERPROFILE\.maven\maven-3.9.12"
)

$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $MyInvocation.MyCommand.Path
$WorldPainterDir = Join-Path $Root "WorldPainter"
$Mvn = Join-Path $MavenHome "bin\mvn.cmd"
$Toolchains = Join-Path $Root "toolchains.xml"
$AppFolderName = "WorldPainter v2"
$CfgFileName = "$AppFolderName.cfg"
$ExeFileName = "$AppFolderName.exe"

$RequiredJavaOptions = @(
    "-Djpackage.app-version=1.0",
    "-Dorg.pepsoft.worldpainter.classifier=v2",
    "-XX:MaxRAMPercentage=70",
    "-XX:MinRAMPercentage=15",
    "-XX:+UseStringDeduplication",
    "-XX:+HeapDumpOnOutOfMemoryError",
    "-XX:G1HeapRegionSize=16m"
)

function Write-Utf8NoBomLines {
    param([string]$Path, [string[]]$Lines)
    $utf8NoBom = New-Object System.Text.UTF8Encoding $false
    [System.IO.File]::WriteAllLines($Path, $Lines, $utf8NoBom)
}

function Merge-JavaOptionsIntoCfg {
    param([string]$CfgPath)
    if (-not (Test-Path -LiteralPath $CfgPath)) {
        throw "Config file not found: $CfgPath"
    }
    $lines = Get-Content -LiteralPath $CfgPath
    $out = New-Object System.Collections.Generic.List[string]
    $inJavaOptions = $false
    $sawJavaOptionsSection = $false
    foreach ($line in $lines) {
        if ($line -match '^\[JavaOptions\]\s*$') {
            $inJavaOptions = $true
            $sawJavaOptionsSection = $true
            $out.Add($line)
            continue
        }
        if ($inJavaOptions -and ($line -match '^\[')) {
            foreach ($option in $RequiredJavaOptions) {
                $out.Add("java-options=$option")
            }
            $inJavaOptions = $false
        }
        if ($inJavaOptions -and ($line -match '^\s*java-options=')) {
            continue
        }
        $out.Add($line)
    }
    if ($inJavaOptions) {
        foreach ($option in $RequiredJavaOptions) {
            $out.Add("java-options=$option")
        }
    } elseif (-not $sawJavaOptionsSection) {
        $out.Add("")
        $out.Add("[JavaOptions]")
        foreach ($option in $RequiredJavaOptions) {
            $out.Add("java-options=$option")
        }
    }
    Write-Utf8NoBomLines -Path $CfgPath -Lines $out.ToArray()
}

function Test-AppImageComplete {
    param([string]$AppImageRoot)
    $cfg = Join-Path $AppImageRoot "app\$CfgFileName"
    $jar = Join-Path $AppImageRoot "app\WorldPainter-v2.jar"
    $exe = Join-Path $AppImageRoot $ExeFileName
    return ((Test-Path -LiteralPath $cfg) -and (Test-Path -LiteralPath $jar) -and (Test-Path -LiteralPath $exe))
}

function Repair-ShadedPluginDescriptor {
    param([string]$JarPath)
    # Shade collapses WPCore+WPGUI plugin descriptors to one file. Restore a merged descriptor
    # so JavaPlatformProvider / DefaultPlugin / custom objects / layer editors all load.
    Add-Type -AssemblyName System.IO.Compression
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $merged = @"
{
  "name": "WorldPainter",
  "version": "2.0.0-v2-SNAPSHOT",
  "classes": [
    "org.pepsoft.worldpainter.DefaultPlugin",
    "org.pepsoft.worldpainter.platforms.JavaPlatformProvider",
    "org.pepsoft.worldpainter.DefaultCustomObjectProvider",
    "org.pepsoft.worldpainter.layers.DefaultLayerEditorProvider"
  ]
}
"@
    $entryName = "org.pepsoft.worldpainter.plugins"
    $zip = [System.IO.Compression.ZipFile]::Open($JarPath, [System.IO.Compression.ZipArchiveMode]::Update)
    try {
        $existing = $zip.GetEntry($entryName)
        if ($null -ne $existing) {
            $existing.Delete()
        }
        $entry = $zip.CreateEntry($entryName, [System.IO.Compression.CompressionLevel]::Optimal)
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($merged.Trim() + "`n")
        $stream = $entry.Open()
        try {
            $stream.Write($bytes, 0, $bytes.Length)
        } finally {
            $stream.Dispose()
        }
    } finally {
        $zip.Dispose()
    }
    Write-Host "Merged plugin descriptor into: $JarPath" -ForegroundColor Green
}

function Sync-AppImage {
    param(
        [string]$SourceRoot,
        [string]$DestRoot
    )
    if (-not (Test-Path -LiteralPath $SourceRoot)) {
        throw "Source app image not found: $SourceRoot"
    }
    if (-not (Test-AppImageComplete $SourceRoot)) {
        throw "Source app image is incomplete: $SourceRoot"
    }

    New-Item -ItemType Directory -Force -Path (Join-Path $DestRoot "app") | Out-Null

    # app/ must always be updated — missing .cfg breaks the launcher even when exe is locked
    Copy-Item -Path (Join-Path $SourceRoot "app\*") -Destination (Join-Path $DestRoot "app") -Force -Recurse

    # runtime rarely changes between builds; update when possible
    $runtimeSource = Join-Path $SourceRoot "runtime"
    $runtimeDest = Join-Path $DestRoot "runtime"
    if (Test-Path -LiteralPath $runtimeSource) {
        if (Test-Path -LiteralPath $runtimeDest) {
            try {
                Remove-Item -LiteralPath $runtimeDest -Recurse -Force -ErrorAction Stop
            } catch {
                Write-Host "Could not replace runtime (may be in use); keeping existing runtime." -ForegroundColor Yellow
            }
        }
        if (-not (Test-Path -LiteralPath $runtimeDest)) {
            Copy-Item -LiteralPath $runtimeSource -Destination $runtimeDest -Recurse -Force
        }
    }

    $exeSource = Join-Path $SourceRoot $ExeFileName
    $exeDest = Join-Path $DestRoot $ExeFileName
    try {
        Copy-Item -LiteralPath $exeSource -Destination $exeDest -Force -ErrorAction Stop
    } catch {
        Write-Host "Could not replace $ExeFileName (WorldPainter may be running). app/ was still updated." -ForegroundColor Yellow
    }

    if (-not (Test-Path -LiteralPath (Join-Path $DestRoot "app\$CfgFileName"))) {
        throw "Publish failed: $(Join-Path $DestRoot "app\$CfgFileName") is still missing"
    }
}

function Publish-AppImage {
    param(
        [string]$StagingDir,
        [string]$DistDir
    )
    $stagedApp = Join-Path $StagingDir $AppFolderName
    $distApp = Join-Path $DistDir $AppFolderName

    if (-not (Test-AppImageComplete $stagedApp)) {
        throw "jpackage output is incomplete: $stagedApp"
    }

    New-Item -ItemType Directory -Force -Path $DistDir | Out-Null

    $distEmpty = -not (Test-Path -LiteralPath $distApp)
    $canReplaceWholeTree = $distEmpty

    if (-not $distEmpty) {
        try {
            Remove-Item -LiteralPath $distApp -Recurse -Force -ErrorAction Stop
            $canReplaceWholeTree = $true
        } catch {
            $canReplaceWholeTree = $false
        }
    }

    if ($canReplaceWholeTree) {
        if (Test-Path -LiteralPath $DistDir) {
            try {
                Remove-Item -LiteralPath $DistDir -Recurse -Force -ErrorAction Stop
            } catch {
                $canReplaceWholeTree = $false
            }
        }
    }

    if ($canReplaceWholeTree) {
        Rename-Item -LiteralPath $StagingDir -NewName (Split-Path -Leaf $DistDir)
        return Join-Path $DistDir $AppFolderName
    }

    Write-Host "dist is in use; merging fresh build into existing folder..." -ForegroundColor Yellow
    New-Item -ItemType Directory -Force -Path $distApp | Out-Null
    Sync-AppImage -SourceRoot $stagedApp -DestRoot $distApp
    return $distApp
}

if (-not (Test-Path $Mvn)) {
    throw "Maven not found at $Mvn. Install Maven or set -MavenHome."
}
if (-not (Test-Path $JdkHome)) {
    throw "JDK not found at $JdkHome. Install JDK 17+ or set -JdkHome."
}

# Keep toolchains.xml in sync with selected JDK
$toolchainsContent = @"
<?xml version="1.0" encoding="UTF-8"?>
<toolchains xmlns="http://maven.apache.org/TOOLCHAINS/1.1.0"
            xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://maven.apache.org/TOOLCHAINS/1.1.0 https://maven.apache.org/xsd/toolchains-1.1.0.xsd">
    <toolchain>
        <type>jdk</type>
        <provides>
            <version>17</version>
        </provides>
        <configuration>
            <jdkHome>$JdkHome</jdkHome>
        </configuration>
    </toolchain>
</toolchains>
"@
Set-Content -Path $Toolchains -Value $toolchainsContent -Encoding UTF8

Write-Host "==> Building WorldPainter v2 (fat JAR)..." -ForegroundColor Cyan
& $Mvn -f (Join-Path $WorldPainterDir "pom.xml") clean package "-Dmaven.test.skip=true" "-t" $Toolchains
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$Jar = Join-Path $WorldPainterDir "WPGUI\target\WorldPainter-v2.jar"
if (-not (Test-Path $Jar)) {
    throw "Expected jar not found: $Jar"
}
Write-Host "Built: $Jar" -ForegroundColor Green
Repair-ShadedPluginDescriptor -JarPath $Jar

if ($SkipExe) {
    Write-Host "Skipping jpackage (-SkipExe)." -ForegroundColor Yellow
    $DistApp = Join-Path $Root "dist\$AppFolderName\app"
    $DistJar = Join-Path $DistApp "WorldPainter-v2.jar"
    $DistCfg = Join-Path $DistApp $CfgFileName
    if (Test-Path -LiteralPath $DistApp) {
        Copy-Item -LiteralPath $Jar -Destination $DistJar -Force
        Write-Host "Updated app JAR in: $DistJar" -ForegroundColor Green
    } else {
        Write-Host "dist app folder not found ($DistApp); run a full build without -SkipExe once." -ForegroundColor Yellow
    }
    if (Test-Path -LiteralPath $DistCfg) {
        Merge-JavaOptionsIntoCfg -CfgPath $DistCfg
        Write-Host "Updated JVM options in: $DistCfg" -ForegroundColor Green
    }
    exit 0
}

$Jpackage = Join-Path $JdkHome "bin\jpackage.exe"
if (-not (Test-Path $Jpackage)) {
    throw "jpackage not found at $Jpackage"
}

$JpackageInputDir = Join-Path $Root "jpackage-input"
$DistDir = Join-Path $Root "dist"
$DistStagingDir = Join-Path $Root "dist-staging"
$Icon = Join-Path $WorldPainterDir "WPGUI\src\main\resources\org\pepsoft\worldpainter\icons\shovel-icon.png"

Write-Host "==> Packaging Windows exe with jpackage..." -ForegroundColor Cyan
if (Test-Path $JpackageInputDir) {
    Remove-Item -Recurse -Force $JpackageInputDir
}
New-Item -ItemType Directory -Path $JpackageInputDir | Out-Null
Copy-Item -Path $Jar -Destination (Join-Path $JpackageInputDir "WorldPainter-v2.jar")

if (Test-Path $DistStagingDir) {
    Remove-Item -Recurse -Force $DistStagingDir
}

$jpackageArgs = @(
    "--input", $JpackageInputDir,
    "--name", $AppFolderName,
    "--main-jar", "WorldPainter-v2.jar",
    "--main-class", "org.pepsoft.worldpainter.Main",
    "--type", "app-image",
    "--dest", $DistStagingDir
)
foreach ($option in $RequiredJavaOptions) {
    $jpackageArgs += @("--java-options", $option)
}
if (Test-Path $Icon) {
    $jpackageArgs += @("--icon", $Icon)
}

& $Jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

$StagedCfg = Join-Path $DistStagingDir "$AppFolderName\app\$CfgFileName"
Merge-JavaOptionsIntoCfg -CfgPath $StagedCfg

$PublishedApp = Publish-AppImage -StagingDir $DistStagingDir -DistDir $DistDir
$PublishedCfg = Join-Path $PublishedApp "app\$CfgFileName"
Merge-JavaOptionsIntoCfg -CfgPath $PublishedCfg
$Exe = Join-Path $PublishedApp $ExeFileName

if (-not (Test-Path -LiteralPath (Join-Path $PublishedApp "app\$CfgFileName"))) {
    throw "Published app image is missing $CfgFileName"
}

Write-Host "Done. Launch: $Exe" -ForegroundColor Green
