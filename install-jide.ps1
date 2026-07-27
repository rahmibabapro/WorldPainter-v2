param(
    [Parameter(Mandatory = $true)]
    [string]$JideDir
)

$ErrorActionPreference = "Stop"
$Mvn = "$env:USERPROFILE\.maven\maven-3.9.12\bin\mvn.cmd"
if (-not (Test-Path $Mvn)) {
    throw "Maven not found at $Mvn"
}

$Version = "3.8.1"
$Artifacts = @(
    @{ File = "jide-common.jar";    Id = "jide-common" },
    @{ File = "jide-dock.jar";      Id = "jide-dock" },
    @{ File = "jide-plaf-jdk7.jar";  Id = "jide-plaf-jdk7" }
)

foreach ($artifact in $Artifacts) {
    $path = Join-Path $JideDir $artifact.File
    if (-not (Test-Path $path)) {
        throw "Missing $($artifact.File) in $JideDir"
    }
    Write-Host "Installing $($artifact.Id)..." -ForegroundColor Cyan
    & $Mvn install:install-file `
        "-Dfile=$path" `
        "-DgroupId=com.jidesoft" `
        "-DartifactId=$($artifact.Id)" `
        "-Dversion=$Version" `
        "-Dpackaging=jar"
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

Write-Host "JIDE $Version installed to local Maven repository." -ForegroundColor Green
Write-Host "Now run: .\build-v2.ps1"
