#!/usr/bin/env bash
@goto :windows || true
# ============================================================
#  setupRemotes.cmd - Setup git remotes from remoteRepoList.txt
#
#  Reads addition/config/remoteRepoList.txt and adds each URL
#  as a git remote. The remote name is the host segment after
#  '@' (e.g. git@gitee.com:... -> "gitee").
#
#  Usage:
#      ./setupRemotes.cmd
# ============================================================
script_path="$(cd "$(dirname "$0")" && pwd)"
cd "$script_path" || exit

REMOTE_LIST="../config/remoteRepoList.txt"
[ -f "$REMOTE_LIST" ] || { echo "ERROR: $REMOTE_LIST not found"; exit 1; }

grep -v '^#' "$REMOTE_LIST" | while IFS= read -r url || [ -n "$url" ]; do
    [ -z "$url" ] && continue
    # Extract the word after '@' (before the first dot or colon)
    host="${url#*@}"
    key="${host%%.*}"
    if git remote add "$key" "$url" 2>/dev/null; then
        echo "Added remote: $key -> $url"
    else
        echo "Remote '$key' already exists, skipping."
    fi
done
exit 0

:windows
@echo off
setlocal enabledelayedexpansion
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"

set "script_path=%~dp0"
cd /d "%script_path%" || exit /b 1

set "REMOTE_LIST=..\config\remoteRepoList.txt"
if not exist "%REMOTE_LIST%" (
    echo %ESC%[31mERROR: %REMOTE_LIST% not found%ESC%[0m
    exit /b 1
)

for /f "tokens=*" %%i in ('findstr /b /v "#" "%REMOTE_LIST%"') do (
    REM Extract the word after @ (before the first dot)
    for /f "tokens=2 delims=@." %%k in ("%%i") do (
        git remote add %%k "%%i" >nul 2>&1 && (
            echo %ESC%[32mAdded remote: %%k -^> %%i%ESC%[0m
        ) || (
            echo %ESC%[33mRemote '%%k' already exists, skipping.%ESC%[0m
        )
    )
)
endlocal
exit /b 0
