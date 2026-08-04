#!/usr/bin/env bash
# ====== install osmetes checker (library + maven plugin) into local repo ======
# Usage: action/install-checker.cmd
# This makes the `osmetes-check` profile (whole-repo scan) available on a fresh checkout.
@goto :windows || true
cd "$(dirname "$0")/.." || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

printf '%b\n' "${CYAN}\$ mvnw install checker (flora-osmetes + flora-osmetes-plugin)${NC}"
if ./mvnw -s addition/config/settings.xml -pl flora-osmetes,plugins/maven-plugins/flora-osmetes-plugin -am install -DskipTests -P'!osmetes-check'; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 checker installed to local repo${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 checker install FAILED${NC}"
  exit 1
fi

printf '%b\n' "${GREEN}Done! Run 'mvn validate -pl flora-root' to scan the repo.${NC}"
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"

cd /d "%~dp0.." || exit /b 1

rem ---- install checker (library + maven plugin) into local repo ----
echo %ESC%[36m$ mvnw install checker (flora-osmetes + flora-osmetes-plugin)%ESC%[0m
call mvnw -s addition/config/settings.xml -pl flora-osmetes,plugins/maven-plugins/flora-osmetes-plugin -am install -DskipTests -P"!osmetes-check"
if %errorlevel% equ 0 (
  echo %ESC%[32m    OK: checker installed to local repo%ESC%[0m
) else (
  echo %ESC%[31m    FAILED: checker install%ESC%[0m
  exit /b 1
)

echo %ESC%[32mDone! Run 'mvn validate -pl flora-root' to scan the repo.%ESC%[0m
