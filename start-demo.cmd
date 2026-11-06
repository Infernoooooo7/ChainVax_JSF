@echo off
rem ChainVax demo launcher (Windows). Needs only a JDK 17+ on PATH.
rem   start-demo.cmd                 PostgreSQL 17 + Redis 7 in Docker if Docker is running, else the in-process database
rem   start-demo.cmd docker          force Docker mode (fails if Docker is not running); data kept between runs
rem   start-demo.cmd docker-reset    Docker mode with a wiped database (fresh demo data)
rem   start-demo.cmd embedded        in-process PostgreSQL + in-memory cache (zero install, fresh data each start)
rem   start-demo.cmd rebuild [mode]  rebuild the jar first, then start in the given mode
rem First run builds backend\target\chainvax.jar (internet needed once for Maven downloads).
setlocal
cd /d "%~dp0backend"

where java >nul 2>nul
if errorlevel 1 (
  echo [ChainVax] Java 17 or newer is required on PATH. Install a JDK, e.g. https://adoptium.net
  pause
  exit /b 1
)

set MODE=%~1
if /i "%MODE%"=="rebuild" (
  if exist target\chainvax.jar del /q target\chainvax.jar
  set MODE=%~2
)

if not exist target\chainvax.jar (
  echo [ChainVax] Building the application jar - first run takes a few minutes...
  call mvnw.cmd -B -DskipTests package
  if errorlevel 1 (
    echo [ChainVax] Build failed - see the output above.
    pause
    exit /b 1
  )
)

set PROFILES=
if "%MODE%"=="" (
  where docker >nul 2>nul || set "PATH=%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin;%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
  docker info >nul 2>nul && set MODE=docker
)
if "%MODE%"=="" echo [ChainVax] Docker is not running - using the in-process demo database instead.
if /i "%MODE%"=="docker" set USE_DOCKER=1
if /i "%MODE%"=="docker-reset" set USE_DOCKER=1
if defined USE_DOCKER (
  where docker >nul 2>nul || set "PATH=%LOCALAPPDATA%\Programs\DockerDesktop\resources\bin;%ProgramFiles%\Docker\Docker\resources\bin;%PATH%"
  docker info >nul 2>nul
  if errorlevel 1 (
    echo [ChainVax] Docker is not running. Start Docker Desktop, wait until it says "Engine running", then try again.
    pause
    exit /b 1
  )
  pushd "%~dp0"
  if /i "%MODE%"=="docker-reset" docker compose down -v
  echo [ChainVax] Starting PostgreSQL and Redis containers...
  docker compose up -d --wait
  if errorlevel 1 (
    popd
    echo [ChainVax] Could not start the containers - is something else using port 5432 or 6379?
    pause
    exit /b 1
  )
  popd
  set PROFILES=--spring.profiles.active=postgres,redis
)

if "%CHAINVAX_PORT%"=="" set CHAINVAX_PORT=8080
echo.
echo [ChainVax] Starting - ready in about 40 seconds at  http://localhost:%CHAINVAX_PORT%
echo [ChainVax] API docs: http://localhost:%CHAINVAX_PORT%/swagger-ui.html
if defined USE_DOCKER (
  echo [ChainVax] Database: PostgreSQL in Docker, cache: Redis in Docker. Data is kept between runs ^(docker-reset wipes it^).
) else (
  echo [ChainVax] Every start uses a fresh in-process demo database.
)
echo [ChainVax] Press Ctrl+C to stop.
echo.
java -jar target\chainvax.jar %PROFILES%
