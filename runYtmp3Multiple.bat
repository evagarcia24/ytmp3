@echo off
rem -------------------------------------------------
rem   runYtmp3Multiple.bat – Lanza YouTube MP3 Downloader
rem -------------------------------------------------

cd /d "%~dp0"

echo ========================================
echo   YouTube MP3 Downloader
echo ========================================
echo.

rem Verificar Java
java -version >nul 2>&1
if errorlevel 1 (
    echo [ERROR] Java no está instalado o no está en el PATH
    echo Descárgalo de: https://www.java.com/download/
    pause
    exit /b 1
)

rem Verificar JAR
if not exist "Ytmp3Multiple.jar" (
    echo [ERROR] No se encontró Ytmp3Multiple.jar
    echo Asegúrate de que esté en la misma carpeta que este archivo
    pause
    exit /b 1
)

rem Ejecutar
echo Iniciando aplicación...
echo.
java -jar Ytmp3Multiple.jar

if errorlevel 1 (
    echo.
    echo [ERROR] La aplicación terminó con errores
    pause
)