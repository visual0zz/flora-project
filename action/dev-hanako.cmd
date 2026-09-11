#!/usr/bin/env bash
@goto :windows || true
# ============================================================
#  devrun.cmd - one-click build and launch Flora Hanako (Java openhanako)
#
#  Steps:
#    1. Install flora-root to local Maven repo (flora-hanako depends on it)
#    2. Package flora-hanako (skip tests for fast dev start)
#    3. Launch the Javalin server; open http://localhost:<port>
#
#  Usage (same on every OS):
#      ./devrun.cmd            # default port 4567
#      ./devrun.cmd 8080      # custom port
# ============================================================
cd "$(dirname "$0")/../.." || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
PORT="${1:-4567}"

# ---- 1. Install flora-root (dependency of flora-hanako) ----
printf '%b\n' "${CYAN}\$ ./mvnw -pl flora-root -am install -DskipTests${NC}"
if ./mvnw -s addition/config/settings.xml -P !osmetes-check -pl flora-root -am install -DskipTests; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 flora-root installed${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 flora-root build FAILED${NC}"
  exit 1
fi

# ---- 2. Package flora-hanako ----
printf '%b\n' "${CYAN}\$ ./mvnw -pl cultivating/flora-hanako -am package -DskipTests${NC}"
if ./mvnw -s addition/config/settings.xml -P !osmetes-check -pl cultivating/flora-hanako -am package -DskipTests; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 flora-hanako packaged${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 flora-hanako build FAILED${NC}"
  exit 1
fi

JAR="cultivating/flora-hanako/target/flora-hanako-0.1.jar"
if [ ! -f "$JAR" ]; then
  printf '%b\n' "${RED}    \xe2\x9c\x97 jar not found: $JAR${NC}"
  exit 1
fi

# ---- 3. Launch (exec:java resolves the full classpath: flora-root + javalin + slf4j) ----
printf '%b\n' "${GREEN}Launching Flora Hanako on port $PORT ...${NC}"
printf '%b\n' "${CYAN}    Open http://localhost:$PORT in your browser${NC}"
exec ./mvnw -s addition/config/settings.xml -P !osmetes-check -pl cultivating/flora-hanako exec:java -Dexec.mainClass=com.flora.hanako.Main -Dexec.args="$PORT"

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0..\.." || exit /b 1

set "PORT=%~1"
if "%PORT%"=="" set "PORT=4567"

rem ---- 1. Install flora-root (dependency of flora-hanako) ----
echo %ESC%[36m$ mvnw -pl flora-root -am install -DskipTests%ESC%[0m
call mvnw -s addition/config/settings.xml -P !osmetes-check -pl flora-root -am install -DskipTests
if %errorlevel% neq 0 (
  echo %ESC%[31m    FAILED: flora-root build%ESC%[0m
  exit /b 1
)
echo %ESC%[32m    OK: flora-root installed%ESC%[0m

rem ---- 2. Package flora-hanako ----
echo %ESC%[36m$ mvnw -pl cultivating/flora-hanako -am package -DskipTests%ESC%[0m
call mvnw -s addition/config/settings.xml -P !osmetes-check -pl cultivating/flora-hanako -am package -DskipTests
if %errorlevel% neq 0 (
  echo %ESC%[31m    FAILED: flora-hanako build%ESC%[0m
  exit /b 1
)
echo %ESC%[32m    OK: flora-hanako packaged%ESC%[0m

set "JAR=cultivating\flora-hanako\target\flora-hanako-0.1.jar"
if not exist "%JAR%" (
  echo %ESC%[31m    FAILED: jar not found: %JAR%%ESC%[0m
  exit /b 1
)

rem ---- 3. Launch (exec:java resolves the full classpath: flora-root + javalin + slf4j) ----
echo %ESC%[32mLaunching Flora Hanako on port %PORT% ...%ESC%[0m
echo %ESC%[36m    Open http://localhost:%PORT% in your browser%ESC%[0m
call mvnw -s addition/config/settings.xml -P !osmetes-check -pl cultivating/flora-hanako exec:java -Dexec.mainClass=com.flora.hanako.Main -Dexec.args=%PORT%
exit /b %errorlevel%
