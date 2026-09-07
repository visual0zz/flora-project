#!/usr/bin/env bash
@goto :windows || true
# ============================================================
#  sanctum.cmd - build and launch flora-sanctum (Swing GUI or CLI)
#
#  Steps:
#    1. Install flora-root, flora-shell, flora-sanctum-core to local Maven repo
#    2. Package flora-sanctum-app (copies deps to target/lib)
#    3. Launch via module-path (no args -> Swing GUI; with args -> CLI)
#
#  Usage (same on every OS):
#      ./sanctum.cmd              # launch Swing GUI
#      ./sanctum.cmd create /path  # run a CLI command (args passed through)
# ============================================================
cd "$(dirname "$0")/../.." || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

# ---- 1. Install dependencies (flora-root, flora-shell, sanctum-core) ----
printf '%b\n' "${CYAN}\$ ./mvnw -pl flora-root,flora-shell,flora-sanctum/flora-sanctum-core -am install -DskipTests${NC}"
if ./mvnw -s addition/config/settings.xml -P !osmetes-check -pl flora-root,flora-shell,flora-sanctum/flora-sanctum-core -am install -DskipTests; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 dependencies installed${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 dependency build FAILED${NC}"
  exit 1
fi

# ---- 2. Package flora-sanctum-app (clean: removes stale version jars left in target/lib) ----
printf '%b\n' "${CYAN}\$ ./mvnw -pl flora-sanctum/flora-sanctum-app clean package -DskipTests${NC}"
if ./mvnw -s addition/config/settings.xml -P !osmetes-check -pl flora-sanctum/flora-sanctum-app clean package -DskipTests; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 flora-sanctum-app packaged${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 flora-sanctum-app build FAILED${NC}"
  exit 1
fi

APP=$(ls flora-sanctum/flora-sanctum-app/target/flora-sanctum-app-*.jar 2>/dev/null | head -1)
LIB="flora-sanctum/flora-sanctum-app/target/lib"
if [ -z "$APP" ] || [ ! -d "$LIB" ]; then
  printf '%b\n' "${RED}    \xe2\x9c\x97 jar or lib not found under target/${NC}"
  exit 1
fi

# ---- 3. Launch via module-path (JPMS: parallel jars) ----
MODPATH="$APP:$LIB"
NATIVE="--enable-native-access=com.formdev.flatlaf"
if [ "$#" -eq 0 ]; then
  printf '%b\n' "${GREEN}Launching flora-sanctum Swing GUI ...${NC}"
  exec java $NATIVE --module-path "$MODPATH" -m com.flora.sanctum.app/com.flora.sanctum.app.Main
else
  printf '%b\n' "${GREEN}Launching flora-sanctum CLI: $*${NC}"
  exec java $NATIVE --module-path "$MODPATH" -m com.flora.sanctum.app/com.flora.sanctum.app.Main "$@"
fi

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0..\.." || exit /b 1

rem ---- 1. Install dependencies ----
echo %ESC%[36m$ mvnw -pl flora-root,flora-shell,flora-sanctum/flora-sanctum-core -am install -DskipTests%ESC%[0m
call mvnw -s addition/config/settings.xml -P !osmetes-check -pl flora-root,flora-shell,flora-sanctum/flora-sanctum-core -am install -DskipTests
if %errorlevel% neq 0 (
  echo %ESC%[31m    FAILED: dependency build%ESC%[0m
  exit /b 1
)
echo %ESC%[32m    OK: dependencies installed%ESC%[0m

rem ---- 2. Package flora-sanctum-app (clean: removes stale version jars left in target/lib) ----
echo %ESC%[36m$ mvnw -pl flora-sanctum/flora-sanctum-app clean package -DskipTests%ESC%[0m
call mvnw -s addition/config/settings.xml -P !osmetes-check -pl flora-sanctum/flora-sanctum-app clean package -DskipTests
if %errorlevel% neq 0 (
  echo %ESC%[31m    FAILED: flora-sanctum-app build%ESC%[0m
  exit /b 1
)
echo %ESC%[32m    OK: flora-sanctum-app packaged%ESC%[0m

set "APP="
for %%f in (flora-sanctum\flora-sanctum-app\target\flora-sanctum-app-*.jar) do set "APP=%%f"
set "LIB=flora-sanctum\flora-sanctum-app\target\lib"
if "%APP%"=="" (
  echo %ESC%[31m    FAILED: jar not found under target\%ESC%[0m
  exit /b 1
)
if not exist "%LIB%" (
  echo %ESC%[31m    FAILED: lib not found: %LIB%%ESC%[0m
  exit /b 1
)

rem ---- 3. Launch via module-path ----
set "MODPATH=%APP%;%LIB%"
if "%~1"=="" (
  echo %ESC%[32mLaunching flora-sanctum Swing GUI ...%ESC%[0m
  java --enable-native-access=com.formdev.flatlaf --module-path "%MODPATH%" -m com.flora.sanctum.app/com.flora.sanctum.app.Main
) else (
  echo %ESC%[32mLaunching flora-sanctum CLI: %*%ESC%[0m
  java --enable-native-access=com.formdev.flatlaf --module-path "%MODPATH%" -m com.flora.sanctum.app/com.flora.sanctum.app.Main %*
)
