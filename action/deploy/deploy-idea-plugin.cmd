#!/usr/bin/env bash
@goto :windows || true
# ===========================================================================
# deploy: 发布 ramet-language-support（IDEA 插件）到 JetBrains Marketplace
# ===========================================================================
# 前置条件（必须配置“其中之一”，否则 publishPlugin 会报
#   'token' property must be specified for plugin publishing）：
#
#   方式一（推荐）：配置“系统/用户级”环境变量
#       JETBRAINS_MARKETPLACE_TOKEN=<你的 Marketplace 发布 token>
#       注意：仅在当前终端用 $env:VAR=...（PowerShell）或 set VAR=...（cmd）
#       设置的是“会话级”变量，不会被其他进程（如本脚本/CI）继承；
#       请通过系统属性把该变量设为“用户变量”或“系统变量”，
#       或在 ~/.gradle/gradle.properties 里配置（见方式二）。
#
#   方式二：写入本机全局 Gradle 配置（与仓库无关，不会进版本控制）
#       在 ~/.gradle/gradle.properties 中加入一行：
#       jetbrainsMarketplaceToken=<你的 Marketplace 发布 token>
#       （~ 即当前用户主目录，如 C:\Users\shutie.zhao\.gradle\gradle.properties）
#
# 安全提醒：
#   * token 属于敏感凭据，绝不能写进仓库或提交到 git。
#   * 本脚本不读取、不写入任何密钥文件，只调用 Gradle 的 publishPlugin 任务，
#     实际 token 由 build.gradle.kts 中的 intellijPlatform.publishing.token 读取
#     （环境变量优先，其次 ~/.gradle/gradle.properties）。
#
# 版本说明：
#   * 插件版本号来自 build.gradle.kts 的 `version`，与 Marketplace 上一次
#     上传的版本比较，必须更高；若已存在同名版本，请先 bump 版本再发布。
# ===========================================================================
cd "$(dirname "$0")/.." || exit 1
PLUGIN_DIR="plugins/idea-plugins/ramet-language-support"
cd "$PLUGIN_DIR" || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

if [ -z "$JETBRAINS_MARKETPLACE_TOKEN" ]; then
  printf '%b\n' "${RED}WARN: 未检测到环境变量 JETBRAINS_MARKETPLACE_TOKEN。${NC}"
  printf '%b\n' "${RED}       若已在 ~/.gradle/gradle.properties 配置 jetbrainsMarketplaceToken 仍可发布；否则将失败。${NC}"
fi

printf '%b\n' "${CYAN}\$ ./gradlew publishPlugin (${PLUGIN_DIR})${NC}"
if ./gradlew publishPlugin; then
  printf '%b\n' "${GREEN}    \xE2\x9C\x93 插件发布成功！${NC}"
else
  printf '%b\n' "${RED}    \xE2\x9C\x97 插件发布失败（请检查上方 token / 版本号报错）${NC}"
  exit 1
fi
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0.." || exit /b 1
cd /d "plugins\idea-plugins\ramet-language-support" || exit /b 1

if "%JETBRAINS_MARKETPLACE_TOKEN%"=="" (
  echo %ESC%[31mWARN: 未检测到环境变量 JETBRAINS_MARKETPLACE_TOKEN。%ESC%[0m
  echo %ESC%[31m        若已在 %%USERPROFILE%%\.gradle\gradle.properties 配置 jetbrainsMarketplaceToken 仍可发布；否则将失败。%ESC%[0m
)

echo %ESC%[36m$ gradlew.bat publishPlugin%ESC%[0m
call gradlew.bat publishPlugin && (echo %ESC%[32m    OK: 插件发布成功！%ESC%[0m) || (echo %ESC%[31m    FAILED: 插件发布失败（请检查上方 token / 版本号报错）%ESC%[0m & exit /b 1)
