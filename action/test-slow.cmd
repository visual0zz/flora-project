#!/usr/bin/env bash
@goto :windows || true
# 运行慢测试（Maven 主项目 + IntelliJ 插件在沙箱 IDEA 中的 fixture 测试）
cd "$(dirname "$0")/.." || exit 1
GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'
ANY_FAILED=0

if [ -f "pom.xml" ]; then
  printf '%b\n' "${CYAN}\$ ./mvnw test -Dgroups=slow -Dtest.excluded.groups=${NC}"
  if ./mvnw -s addition/config/settings.xml test -Dgroups=slow -Dtest.excluded.groups=; then
    printf '%b\n' "${GREEN}    \xE2\x9C\x93 Maven slow test success!${NC}"
  else
    printf '%b\n' "${RED}    \xE2\x9C\x97 Maven slow test failed${NC}"
    ANY_FAILED=1
  fi
fi

printf '%b\n' "${CYAN}\$ cd plugins/idea-plugins/ramet-language-support && ./gradlew test${NC}"
if cd plugins/idea-plugins/ramet-language-support && ./gradlew --no-daemon test; then
  printf '%b\n' "${GREEN}    \xE2\x9C\x93 IntelliJ plugin fixture tests success!${NC}"
else
  printf '%b\n' "${RED}    \xE2\x9C\x97 IntelliJ plugin fixture tests failed${NC}"
  ANY_FAILED=1
fi
cd "$(dirname "$0")/.." || exit 1

if [ "$ANY_FAILED" -ne 0 ]; then
  exit 1
fi
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0.." || exit /b 1

echo %ESC%[36m$ mvnw test -Dgroups=slow -Dtest.excluded.groups=%ESC%[0m
call mvnw -s addition/config/settings.xml test -Dgroups=slow -Dtest.excluded.groups= && (echo %ESC%[32m    OK: Maven slow test success!%ESC%[0m) || (echo %ESC%[31m    FAILED: Maven slow test failed%ESC%[0m)

echo %ESC%[36m$ cd plugins\idea-plugins\ramet-language-support ^&^& gradlew test%ESC%[0m
cd /d "%~dp0..\plugins\idea-plugins\ramet-language-support" || exit /b 1
call gradlew.bat --no-daemon test && (echo %ESC%[32m    OK: IntelliJ plugin fixture tests success!%ESC%[0m) || (echo %ESC%[31m    FAILED: IntelliJ plugin fixture tests failed%ESC%[0m)
cd /d "%~dp0.." || exit /b 1
