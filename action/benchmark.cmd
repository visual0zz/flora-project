#!/usr/bin/env bash
@goto :windows || true
cd "$(dirname "$0")/.." || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

printf '%b\n' "${CYAN}\$ ./mvnw clean package -pl flora-project/flora-benchmark -am -DskipTests${NC}"
if ./mvnw -s addition/config/settings.xml clean package -pl flora-project/flora-benchmark -am -DskipTests; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 Build success!${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 Build failed${NC}"
  exit 1
fi

mkdir -p absent/benchmark
printf '%b\n' "${CYAN}\$ java -jar flora-project/flora-benchmark/target/flora-benchmark-0.1-shaded.jar${NC}"
java -jar flora-project/flora-benchmark/target/flora-benchmark-0.1-shaded.jar
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0.." || exit /b 1
echo %ESC%[36m$ mvnw clean package -pl flora-project\flora-benchmark -am -DskipTests%ESC%[0m
call mvnw -s addition/config/settings.xml clean package -pl flora-project\flora-benchmark -am -DskipTests || (echo %ESC%[31m    FAILED: Build failed%ESC%[0m & exit /b 1)
echo %ESC%[32m    OK: Build success!%ESC%[0m
if not exist absent\benchmark mkdir absent\benchmark
echo %ESC%[36m$ java -jar flora-project\flora-benchmark\target\flora-benchmark-0.1-shaded.jar%ESC%[0m
java -jar flora-project\flora-benchmark\target\flora-benchmark-0.1-shaded.jar
