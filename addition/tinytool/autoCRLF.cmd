#!/usr/bin/env bash
# ====== for humans only, AI agents should NOT run this script ======
@goto :windows || true
GREEN='\033[0;32m'; NC='\033[0m'
SCOPE="${1:---global}"
git config "$SCOPE" core.autocrlf input
git config "$SCOPE" core.eol lf
printf '%b\n' "${GREEN}Git CRLF configured for Unix/macOS ($SCOPE)${NC}"
exit 0

:windows
@echo off
setlocal enabledelayedexpansion
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
if "%~1"=="" (set "SCOPE=--global") else (set "SCOPE=%~1")
git config "%SCOPE%" core.autocrlf true
git config "%SCOPE%" core.eol crlf
echo %ESC%[32mGit CRLF configured for Windows (%SCOPE%)%ESC%[0m
