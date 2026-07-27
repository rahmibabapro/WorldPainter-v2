@echo off
rem Masaustu kisayolu ile ayni; projeden de calistirilabilir.
setlocal
set "WP_DIR=%~dp0dist\WorldPainter v2"
set "WP_EXE=%WP_DIR%\WorldPainter v2.exe"

if not exist "%WP_EXE%" (
    echo WorldPainter v2 bulunamadi:
    echo %WP_EXE%
    echo.
    echo Once build-v2.ps1 ile derleyin.
    pause
    exit /b 1
)

cd /d "%WP_DIR%"
start "" "%WP_EXE%"
