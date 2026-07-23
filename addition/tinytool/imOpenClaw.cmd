#!/usr/bin/env bash
@goto :windows || true
GREEN='\033[0;32m'; NC='\033[0m'
git config --local user.name "OpenClaw"
git config --local user.email "openclaw@pleroma.com"
printf '%b\n' "${GREEN}Git user configured as OpenClaw (local)${NC}"
exit 0

:windows
@echo off
setlocal enabledelayedexpansion
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
git config --local user.name "OpenClaw"
git config --local user.email "openclaw@pleroma.com"
echo %ESC%[32mGit user configured as OpenClaw (local)%ESC%[0m
