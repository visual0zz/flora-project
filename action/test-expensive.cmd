#!/usr/bin/env bash
@goto :windows || true
# Run expensive tests only: tests hitting real paid external services
# (e.g. LiveStreamingTest streaming against DeepSeek / Anthropic-compatible gateway).
# Network + a valid DEEPSEEK_API_KEY are required; such tests are skipped automatically
# when the key is unset, so an unfunded CI run is free and harmless.
cd "$(dirname "$0")/.." || exit 1
GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

printf '%b\n' "${CYAN}\$ ./mvnw test -Dgroups=expensive -Dsurefire.excludedGroups=${NC}"
if ./mvnw -s addition/config/settings.xml test -Dgroups=expensive -Dsurefire.excludedGroups=; then
  printf '%b\n' "${GREEN}    \xE2\x9C\x93 Maven expensive test success!${NC}"
else
  printf '%b\n' "${RED}    \xE2\x9C\x97 Maven expensive test failed${NC}"
  exit 1
fi
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0.." || exit /b 1

echo %ESC%[36m$ mvnw test -Dgroups=expensive -Dsurefire.excludedGroups=%ESC%[0m
call mvnw -s addition/config/settings.xml test -Dgroups=expensive -Dsurefire.excludedGroups= && (echo %ESC%[32m    OK: Maven expensive test success!%ESC%[0m) || (echo %ESC%[31m    FAILED: Maven expensive test failed%ESC%[0m)
