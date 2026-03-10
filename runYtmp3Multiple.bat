@echo off
rem -------------------------------------------------
rem   runYtmp3Multiple.bat – lanza la aplicación
rem -------------------------------------------------
set "JAR=%~dp0Ytmp3Multiple.jar"

if not exist "%JAR%" (
    echo [ERROR] No se encontro el archivo "%JAR%".
    pause
    exit /b 1
)

rem ----- Verificar que Java está en el PATH -----
where java >nul 2>&1
if %errorlevel% neq 0 (
    echo -------------------------------------------------
    echo  Java no esta instalado o no esta en el PATH.
    echo  Descarga la JRE (Java Runtime) aqui:
    echo  https://adoptium.net/  (elige la version LTS, p.ej. 17)
    echo -------------------------------------------------
    pause
    exit /b 1
)

java -jar "%JAR%" %*
pause