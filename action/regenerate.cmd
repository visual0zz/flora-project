#!/usr/bin/env bash
@goto :windows || true
cd "$(dirname "$0")/.." || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

printf '%b\n' "${CYAN}[1/2] Building code generator, plugin, and osmetes lib ...${NC}"
if ./mvnw -s addition/config/settings.xml install -DskipTests -pl flora-ramet,plugins/maven-plugins/flora-ramet-maven-plugin,flora-osmetes -am -q; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 done${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 failed${NC}"
  exit 1
fi

printf '%b\n' "${CYAN}[2/2] Generating sources from templates ...${NC}"
if ./mvnw -s addition/config/settings.xml generate-sources -Pregenerate -pl '!flora-ramet,!plugins/maven-plugins/flora-ramet-maven-plugin' -q; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 All files generated successfully!${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 Some code generation tasks failed${NC}"
  exit 1
fi
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0.." || exit /b 1
echo %ESC%[36m[1/2] Building code generator, plugin, and osmetes lib ...%ESC%[0m
call mvnw -s addition/config/settings.xml install -DskipTests -pl flora-ramet,plugins/maven-plugins/flora-ramet-maven-plugin,flora-osmetes -am -q || (echo %ESC%[31m    FAILED: failed%ESC%[0m & exit /b 1)
echo %ESC%[32m    OK: done%ESC%[0m
echo %ESC%[36m[2/2] Generating sources from templates ...%ESC%[0m
call mvnw -s addition/config/settings.xml generate-sources -Pregenerate -pl "!flora-ramet,!plugins/maven-plugins/flora-ramet-maven-plugin" -q || (echo %ESC%[31m    FAILED: Some code generation tasks failed%ESC%[0m & exit /b 1)
echo %ESC%[32m    OK: All files generated successfully!%ESC%[0m
