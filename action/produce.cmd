#!/usr/bin/env bash
@goto :windows || true
cd "$(dirname "$0")/.." || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

# ---- Maven 主构建（硬性门槛） ----
printf '%b\n' "${CYAN}\$ ./mvnw clean package -DskipTests${NC}"
if ./mvnw -s addition/config/settings.xml clean package -DskipTests; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 Maven build success!${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 Maven build FAILED${NC}"
  exit 1
fi

# ---- IDEA 插件 ----
printf '%b\n' "${CYAN}\$ (cd plugins/idea-plugins/ramet-language-support && ./gradlew clean buildPlugin -x test)${NC}"
cd "$(dirname "$0")/.." || exit 1
if ( cd plugins/idea-plugins/ramet-language-support && ./gradlew clean buildPlugin -x test ); then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 IDEA plugin built: plugins/idea-plugins/ramet-language-support/build/distributions/*.zip${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 IDEA plugin build FAILED${NC}"
fi

# ---- VSCode 插件（需要 Node.js / npm / vsce 环境） ----
if command -v npm >/dev/null 2>&1; then
  printf '%b\n' "${CYAN}\$ (cd plugins/vscode-extensions/ramet-language-support && npm install && npm run package)${NC}"
  cd "$(dirname "$0")/.." || exit 1
  if ( cd plugins/vscode-extensions/ramet-language-support && npm install && npm run package ); then
    printf '%b\n' "${GREEN}    \xe2\x9c\x93 VSCode plugin packaged: plugins/vscode-extensions/ramet-language-support/*.vsix${NC}"
  else
    printf '%b\n' "${RED}    \xe2\x9c\x97 VSCode plugin build FAILED${NC}"
  fi
else
  printf '%b\n' "${GREEN}Skip VSCode plugin build: npm not found${NC}"
fi

printf '%b\n' "${GREEN}All success!${NC}"
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"

cd /d "%~dp0.." || exit /b 1

rem ---- Maven 主构建（硬性门槛） ----
echo %ESC%[36m$ mvnw clean package -DskipTests%ESC%[0m
call mvnw -s addition/config/settings.xml clean package -DskipTests || (echo %ESC%[31m    FAILED: Maven build%ESC%[0m & exit /b 1)
echo %ESC%[32m    OK: Maven build success!%ESC%[0m

rem ---- IDEA 插件 ----
echo %ESC%[36m$ gradlew clean buildPlugin -x test%ESC%[0m
cd /d "%~dp0.."
pushd plugins\idea-plugins\ramet-language-support
call gradlew clean buildPlugin -x test
if %errorlevel% equ 0 (
  echo %ESC%[32m    OK: IDEA plugin built%ESC%[0m
) else (
  echo %ESC%[31m    FAILED: IDEA plugin build%ESC%[0m
)
popd

rem ---- VSCode 插件（需要 Node.js / npm / vsce 环境） ----
where npm >nul 2>nul
if %errorlevel% equ 0 (
  echo %ESC%[36m$ npm install && npm run package%ESC%[0m
  cd /d "%~dp0.."
  pushd plugins\vscode-extensions\ramet-language-support
  call npm install
  if %errorlevel% equ 0 (
    call npm run package
    if %errorlevel% equ 0 (
      echo %ESC%[32m    OK: VSCode plugin packaged%ESC%[0m
    ) else (
      echo %ESC%[31m    FAILED: VSCode plugin package%ESC%[0m
    )
  ) else (
    echo %ESC%[31m    FAILED: npm install%ESC%[0m
  )
  popd
) else (
  echo %ESC%[32mSkip VSCode plugin build: npm not found%ESC%[0m
)

echo %ESC%[32mAll success!%ESC%[0m
