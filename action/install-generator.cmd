#!/usr/bin/env bash
# ====== install code generator (flora-ramet + ramet maven plugin) into local repo ======
# Usage: action/install-generator.cmd
# This makes the `regenerate` profile (template code generation) available on a fresh checkout.
@goto :windows || true
cd "$(dirname "$0")/.." || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

printf '%b\n' "${CYAN}\$ mvnw install generator (flora-ramet + flora-ramet-plugin)${NC}"
if ./mvnw -s addition/config/settings.xml -pl flora-ramet,plugins/maven-plugins/flora-ramet-plugin -am install -DskipTests -P'!osmetes-check'; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 generator installed to local repo${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 generator install FAILED${NC}"
  exit 1
fi

printf '%b\n' "${GREEN}Done! Run 'action/regenerate.cmd' to generate sources.${NC}"
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"

cd /d "%~dp0.." || exit /b 1

rem ---- install generator (flora-ramet + ramet maven plugin) into local repo ----
echo %ESC%[36m$ mvnw install generator (flora-ramet + flora-ramet-plugin)%ESC%[0m
call mvnw -s addition/config/settings.xml -pl flora-ramet,plugins/maven-plugins/flora-ramet-plugin -am install -DskipTests -P"!osmetes-check"
if %errorlevel% equ 0 (
  echo %ESC%[32m    OK: generator installed to local repo%ESC%[0m
) else (
  echo %ESC%[31m    FAILED: generator install%ESC%[0m
  exit /b 1
)

echo %ESC%[32mDone! Run 'action/regenerate.cmd' to generate sources.%ESC%[0m
