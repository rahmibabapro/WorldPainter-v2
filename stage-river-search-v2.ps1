param([string]$JdkHome = "C:\Users\Admin\.jdks\jdk-21.0.12.1+1")
$ErrorActionPreference = 'Stop'
$root = $PSScriptRoot
$sourceJar = Join-Path $root 'WorldPainter\WPGUI\target\WorldPainter-v2.jar'
$installed = Join-Path $root 'dist\WorldPainter v2'
if (!(Test-Path -LiteralPath $sourceJar)) { throw 'Build the package first.' }
$reports = @(Get-ChildItem (Join-Path $root 'WorldPainter\*\target\surefire-reports\TEST-*.xml'))
if ($reports.Count -eq 0) { throw 'No test reports found.' }
foreach ($report in $reports) {
    [xml]$xml = Get-Content -LiteralPath $report.FullName
    if ([int]$xml.testsuite.failures -or [int]$xml.testsuite.errors) { throw "Failed tests: $report" }
}
# Only extract the existing descriptor repair helper, never execute the build script.
$parseErrors = $null; $tokens = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile((Join-Path $root 'build-v2.ps1'), [ref]$tokens, [ref]$parseErrors)
$helper = $ast.Find({param($node) $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Repair-ShadedPluginDescriptor'}, $true)
if (!$helper) { throw 'Plugin descriptor repair helper is missing.' }
. ([scriptblock]::Create($helper.Extent.Text))
$stage = Join-Path $root ('dist\river-search-v2-' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
if (Test-Path -LiteralPath $stage) { throw 'Staging directory already exists.' }
$inputDir = Join-Path $stage 'input'
New-Item -ItemType Directory -Path $inputDir | Out-Null
$stagedJar = Join-Path $inputDir 'WorldPainter-v2.jar'
Copy-Item -LiteralPath $sourceJar -Destination $stagedJar
Repair-ShadedPluginDescriptor -JarPath $stagedJar
$options = @(Get-Content -LiteralPath (Join-Path $installed 'app\WorldPainter v2.cfg') | Where-Object { $_ -like 'java-options=*' } | ForEach-Object { $_.Substring(13) })
$arguments = @('--input', $inputDir, '--name', 'WorldPainter v2', '--main-jar', 'WorldPainter-v2.jar', '--main-class', 'org.pepsoft.worldpainter.Main', '--type', 'app-image', '--dest', $stage, '--runtime-image', (Join-Path $installed 'runtime'))
foreach ($option in $options) { $arguments += @('--java-options', $option) }
& (Join-Path $JdkHome 'bin\jpackage.exe') @arguments
if ($LASTEXITCODE -ne 0) { throw "jpackage failed: $LASTEXITCODE" }
$image = Join-Path $stage 'WorldPainter v2'
foreach ($relative in @('WorldPainter v2.exe', 'app\WorldPainter-v2.jar', 'app\WorldPainter v2.cfg')) {
    if (!(Test-Path -LiteralPath (Join-Path $image $relative))) { throw "Missing $relative" }
}
if ((Get-FileHash -LiteralPath $stagedJar).Hash -ne (Get-FileHash -LiteralPath (Join-Path $image 'app\WorldPainter-v2.jar')).Hash) { throw 'Packaged jar mismatch.' }
Write-Output "STAGED=$image"
Write-Output 'Installed application, desktop shortcut and user profile were not changed.'
