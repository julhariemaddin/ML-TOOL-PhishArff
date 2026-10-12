@echo off
setlocal EnableExtensions
cd /d "%~dp0"
title PhishArff

rem ===== 1. Java 17 or newer is required =====
where java >nul 2>nul
if errorlevel 1 goto nojava
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JVER=%%~v"
for /f "delims=. tokens=1" %%m in ("%JVER%") do set "JMAJOR=%%m"
if %JMAJOR% LSS 17 goto oldjava

rem ===== 2. Maven: use the installed one, or download a private copy once =====
where mvn >nul 2>nul
if not errorlevel 1 set "MVN=mvn"
if defined MVN goto run

set "MAVEN_VERSION=3.9.9"
set "MAVEN_DIR=%~dp0.tools\apache-maven-%MAVEN_VERSION%"
if exist "%MAVEN_DIR%\bin\mvn.cmd" goto havemaven

echo First run only: downloading Maven %MAVEN_VERSION% ^(about 9 MB^)...
if not exist ".tools" mkdir ".tools"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ProgressPreference='SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -Uri 'https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip' -OutFile '.tools\maven.zip'"
if errorlevel 1 goto nodownload
powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force -Path '.tools\maven.zip' -DestinationPath '.tools'"
if errorlevel 1 goto nodownload
del ".tools\maven.zip" >nul 2>nul

:havemaven
set "MVN=%MAVEN_DIR%\bin\mvn.cmd"

rem ===== 3. Build and start =====
:run
echo Starting PhishArff... ^(the first start downloads JavaFX and takes a minute^)
call "%MVN%" -q compile javafx:run
if errorlevel 1 goto failed
exit /b 0

:nojava
echo Java was not found. Install JDK 17 or newer from https://adoptium.net and run this file again.
goto end
:oldjava
echo Java %JVER% is too old. PhishArff needs Java 17 or newer: https://adoptium.net
goto end
:nodownload
echo Could not download Maven. Check your internet connection, or install Maven from https://maven.apache.org and run again.
goto end
:failed
echo.
echo PhishArff stopped with an error. Read the message above.
:end
echo.
pause
exit /b 1
