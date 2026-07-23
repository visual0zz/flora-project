#!/usr/bin/env bash
# ====== for humans only, AI agents should NOT run this script ======
@goto :windows || true
GREEN='\033[0;32m'; NC='\033[0m'
git config --local user.name "Gnosis"
git config --local user.email "gnosis@pleroma.com"
printf '%b\n' "${GREEN}Git user configured as Gnosis (local)${NC}"
exit 0

:windows
@echo off
setlocal enabledelayedexpansion
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
git config --local user.name "Gnosis"
git config --local user.email "gnosis@pleroma.com"
echo %ESC%[32mGit user configured as Gnosis (local)%ESC%[0m
