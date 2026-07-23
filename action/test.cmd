#!/usr/bin/env bash
@goto :windows || true
# 运行 Maven 主项目的快测试（IntelliJ 插件测试由 test-slow.cmd 单独跑）
cd "$(dirname "$0")/.." || exit 1
GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
ANY_FAILED=0

printf '%b\n' "${CYAN}\$ ./mvnw test${NC}"
if ./mvnw -s addition/config/settings.xml test; then
  printf '%b\n' "${GREEN}    \xE2\x9C\x93 Maven test success!${NC}"
else
  printf '%b\n' "${RED}    \xE2\x9C\x97 Maven test failed${NC}"
  ANY_FAILED=1
fi

if [ "$ANY_FAILED" -ne 0 ]; then
  exit 1
fi
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0.." || exit /b 1

echo %ESC%[36m$ mvnw test%ESC%[0m
call mvnw -s addition/config/settings.xml test && (echo %ESC%[32m    OK: Maven test success!%ESC%[0m) || (echo %ESC%[31m    FAILED: Maven test failed%ESC%[0m)
