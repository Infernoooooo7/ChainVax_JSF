@echo off
rem ChainVax demo launcher (Windows). Needs only a JDK 17+ on PATH.
rem First run builds backend\target\chainvax.jar (internet needed once for Maven downloads).
setlocal
cd /d "%~dp0backend"

where java >nul 2>nul
if errorlevel 1 (
  echo [ChainVax] Java 17 or newer is required on PATH. Install a JDK, e.g. https://adoptium.net
  pause
  exit /b 1
)

if /i "%~1"=="rebuild" if exist target\chainvax.jar del /q target\chainvax.jar

if not exist target\chainvax.jar (
  echo [ChainVax] Building the application jar - first run takes a few minutes...
  call mvnw.cmd -B -DskipTests package
  if errorlevel 1 (
    echo [ChainVax] Build failed - see the output above.
    pause
    exit /b 1
  )
)

if "%CHAINVAX_PORT%"=="" set CHAINVAX_PORT=8080
echo.
echo [ChainVax] Starting - ready in about 40 seconds at  http://localhost:%CHAINVAX_PORT%
echo [ChainVax] API docs: http://localhost:%CHAINVAX_PORT%/swagger-ui.html
echo [ChainVax] Every start uses a fresh demo database. Press Ctrl+C to stop.
echo.
java -jar target\chainvax.jar
