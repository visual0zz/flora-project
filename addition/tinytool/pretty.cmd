#!/usr/bin/env bash
# ====== for humans only, AI agents should NOT run this script ======
@goto :windows || true
GREEN='\033[0;32m'; NC='\033[0m'
SCOPE="${1:---global}"
git config "$SCOPE" alias.logs "log --all --graph --pretty=format:'%Cred%h%Creset -%C(yellow)%d%Creset %s %Cgreen(%cr) %C(bold blue)<%an>%Creset' --abbrev-commit --date=relative"
git config "$SCOPE" core.quotepath false
printf '%b\n' "${GREEN}Git pretty configured ($SCOPE)${NC}"
exit 0

:windows
@echo off
setlocal enabledelayedexpansion
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
if "%~1"=="" (set "SCOPE=--global") else (set "SCOPE=%~1")
git config "%SCOPE%" alias.logs "log --all --graph --pretty=format:'%%Cred%%h%%Creset -%%C(yellow)%%d%%Creset %%s %%Cgreen(%%cr) %%C(bold blue)<%%an>%%Creset' --abbrev-commit --date=relative"
git config "%SCOPE%" core.quotepath false
echo %ESC%[32mGit pretty configured (%SCOPE%)%ESC%[0m
