@echo off
setlocal
set ROOT=%~dp0
set JAR=%ROOT%WorldPainter\WPGUI\target\WorldPainter-v2.jar
if not exist "%JAR%" (
  echo Jar not found. Run build-v2.ps1 first.
  exit /b 1
)
java -Dorg.pepsoft.worldpainter.classifier=v2 -jar "%JAR%" %*
