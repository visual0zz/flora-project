#!/usr/bin/env bash
@goto :windows || true
# ===========================================================================
# deploy: publish ramet-language-support (IDEA plugin) to JetBrains Marketplace
# ===========================================================================
# Prerequisites:
#   1) Publish token: configure ONE of the following; if NEITHER is set the
#      script aborts with an error before invoking Gradle (see "Method 1 /
#      Method 2" below). publishPlugin itself would otherwise fail later with
#      'token' property must be specified for plugin publishing'.
#   2) Version comes from a git tag of the form ramet-idea-plugin-vX.Y.Z:
#      - add the matching [X.Y.Z] section to CHANGELOG.md first;
#      - then run `git tag ramet-idea-plugin-vX.Y.Z`;
#      - the script derives the version from that tag and injects it into the
#        plugin bundle; without a matching tag the version stays unchanged.
#
#   Method 1 (recommended): set a "user/system level" environment variable
#       JETBRAINS_MARKETPLACE_TOKEN=<your Marketplace publish token>
#       Note: $env:VAR=... (PowerShell) or set VAR=... (cmd) only sets a
#       session-level variable that other processes (e.g. this script / CI)
#       do not inherit; set it as a user or system variable via the system
#       properties UI, or configure it in ~/.gradle/gradle.properties
#       (see Method 2).
#
#   Method 2: write it into the local global Gradle config (not tied to the
#       repo, never enters version control)
#       add a line to ~/.gradle/gradle.properties:
#       jetbrainsMarketplaceToken=<your Marketplace publish token>
#       (~ is the current user's home, e.g. C:\Users\you\.gradle\gradle.properties)
#
# Security note:
#   * The token is sensitive; never write it into the repo or commit it to git.
#   * This script reads or writes no key file; it only invokes the Gradle
#     publishPlugin task. The actual token is read by
#     intellijPlatform.publishing.token in build.gradle.kts
#     (environment variable first, then ~/.gradle/gradle.properties).
#
# Version note:
#   * The plugin version comes from build.gradle.kts `version`; it must be
#     higher than the last uploaded version on the Marketplace. If the same
#     version already exists, bump the version first.
# ===========================================================================
cd "$(dirname "$0")/../.." || exit 1
PLUGIN_DIR="plugins/idea-plugins/ramet-language-support"
cd "$PLUGIN_DIR" || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

# Token check: one of the two sources must be present, else abort.
GRADLE_PROPS="$HOME/.gradle/gradle.properties"
HAS_GRADLE_TOKEN=0
if [ -f "$GRADLE_PROPS" ] && grep -q '^jetbrainsMarketplaceToken=.' "$GRADLE_PROPS" 2>/dev/null; then
  HAS_GRADLE_TOKEN=1
fi
if [ -z "$JETBRAINS_MARKETPLACE_TOKEN" ] && [ "$HAS_GRADLE_TOKEN" -eq 0 ]; then
  printf '%b\n' "${RED}ERROR: no JetBrains Marketplace publish token configured.${NC}"
  printf '%b\n' "${RED}      Set JETBRAINS_MARKETPLACE_TOKEN, or add jetbrainsMarketplaceToken=<token>${NC}"
  printf '%b\n' "${RED}      to $GRADLE_PROPS. Aborting before publish.${NC}"
  exit 1
fi

printf '%b\n' "${CYAN}\$ ./gradlew publishPlugin (${PLUGIN_DIR})${NC}"
if ./gradlew publishPlugin; then
  printf '%b\n' "${GREEN}    \xE2\x9C\x93 Plugin published successfully!${NC}"
else
  printf '%b\n' "${RED}    \xE2\x9C\x97 Plugin publish failed (check the token / version errors above)${NC}"
  exit 1
fi
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0..\.." || exit /b 1
cd /d "plugins\idea-plugins\ramet-language-support" || exit /b 1

set "HAS_GRADLE_TOKEN=0"
if exist "%USERPROFILE%\.gradle\gradle.properties" (
  findstr /R "^jetbrainsMarketplaceToken=." "%USERPROFILE%\.gradle\gradle.properties" >nul 2>&1 && set "HAS_GRADLE_TOKEN=1"
)
if not "%JETBRAINS_MARKETPLACE_TOKEN%"=="" goto :have_token
if "%HAS_GRADLE_TOKEN%"=="0" goto :no_token
goto :have_token

:no_token
echo %ESC%[31mERROR: no JetBrains Marketplace publish token configured.%ESC%[0m
echo %ESC%[31m      Set JETBRAINS_MARKETPLACE_TOKEN, or add jetbrainsMarketplaceToken=^<token^>%ESC%[0m
echo %ESC%[31m      to %%USERPROFILE%%\.gradle\gradle.properties. Aborting before publish.%ESC%[0m
exit /b 1

:have_token

echo %ESC%[36m$ gradlew.bat publishPlugin%ESC%[0m
call gradlew.bat publishPlugin
if errorlevel 1 goto :publish_failed
echo %ESC%[32m    OK: Plugin published successfully%ESC%[0m
exit /b 0

:publish_failed
echo %ESC%[31m    FAILED: Plugin publish failed (check the token / version errors above)%ESC%[0m
exit /b 1
