#!/usr/bin/env bash
# ====== for humans only, AI agents should NOT run this script ======
@goto :windows || true
cd "$(dirname "$0")/.." || exit 1

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

printf '%b\n' "${CYAN}Running pretty ...${NC}"
if ./addition/tinytool/pretty.cmd --local; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 done${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 failed${NC}"
  exit 1
fi

printf '%b\n' "${CYAN}Running imGnosis ...${NC}"
if ./addition/tinytool/imGnosis.cmd --local; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 done${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 failed${NC}"
  exit 1
fi

printf '%b\n' "${CYAN}Running autoCRLF ...${NC}"
if ./addition/tinytool/autoCRLF.cmd --local; then
  printf '%b\n' "${GREEN}    \xe2\x9c\x93 done${NC}"
else
  printf '%b\n' "${RED}    \xe2\x9c\x97 failed${NC}"
  exit 1
fi

printf '%b\n' "${GREEN}Setup complete!${NC}"
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0.." || exit /b 1
echo %ESC%[36mRunning pretty ...%ESC%[0m
call addition\tinytool\pretty.cmd --local || (echo %ESC%[31m    FAILED: failed%ESC%[0m & exit /b 1)
echo %ESC%[32m    OK: done%ESC%[0m
echo %ESC%[36mRunning imGnosis ...%ESC%[0m
call addition\tinytool\imGnosis.cmd --local || (echo %ESC%[31m    FAILED: failed%ESC%[0m & exit /b 1)
echo %ESC%[32m    OK: done%ESC%[0m
echo %ESC%[36mRunning autoCRLF ...%ESC%[0m
call addition\tinytool\autoCRLF.cmd --local || (echo %ESC%[31m    FAILED: failed%ESC%[0m & exit /b 1)
echo %ESC%[32m    OK: done%ESC%[0m
echo %ESC%[32mSetup complete!%ESC%[0m
