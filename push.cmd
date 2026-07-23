#!/usr/bin/env bash
@goto :windows || true
script_path="$(cd "$(dirname "$0")" && pwd)"
cd "$script_path" || exit
eval "$(grep -v '^#' "addition/config/pushConfig.txt" | sed '/^$/d;s/=/="/;s/$/"/')"

[ -z "$LOCAL_BRANCH" ] || [ -z "$REMOTE_BRANCH" ] || [ -z "$DEFAULT_COMMIT_MESSAGE" ] && {
  echo "ERROR: pushConfig.txt missing LOCAL_BRANCH / REMOTE_BRANCH / DEFAULT_COMMIT_MESSAGE"
  exit 1
}

GREEN='\033[0;32m'; RED='\033[0;31m'; CYAN='\033[0;36m'; NC='\033[0m'

if [ $# -eq 0 ]; then
  message="$DEFAULT_COMMIT_MESSAGE"
elif [ $# -eq 1 ]; then
  message="$1"
else
  echo "usage: $(basename "$0") [commit message]"
  exit 1
fi

git add -A
git commit -m "$message"

grep -v '^#' "addition/config/remoteRepoList.txt" | while IFS= read -r repo_url || [ -n "$repo_url" ]; do
  [ -z "$repo_url" ] && continue
  printf '%b\n' "${CYAN}git push $repo_url ${LOCAL_BRANCH}:${REMOTE_BRANCH}${NC}"
  if git push "$repo_url" "${LOCAL_BRANCH}:${REMOTE_BRANCH}"; then
    printf '%b\n' "${GREEN}    \xe2\x9c\x93 OK${NC}"
  else
    printf '%b\n' "${RED}    \xe2\x9c\x97 FAILED${NC}"
  fi
done
exit 0

:windows
@echo off
setlocal
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"

set "script_path=%~dp0"
cd /d "%script_path%" || exit /b 1

for /f "usebackq eol=# tokens=1,* delims==" %%a in ("addition\config\pushConfig.txt") do set "%%a=%%b"

if not defined LOCAL_BRANCH   (echo %ESC%[31mERROR: missing LOCAL_BRANCH%ESC%[0m   & exit /b 1)
if not defined REMOTE_BRANCH  (echo %ESC%[31mERROR: missing REMOTE_BRANCH%ESC%[0m  & exit /b 1)
if not defined DEFAULT_COMMIT_MESSAGE (echo %ESC%[31mERROR: missing DEFAULT_COMMIT_MESSAGE%ESC%[0m & exit /b 1)

if "%~1"=="" (
    set "message=%DEFAULT_COMMIT_MESSAGE%"
) else if "%~2"=="" (
    set "message=%~1"
) else (
    echo usage: %~n0 [commit message]
    exit /b 1
)

git add -A
git commit -m "%message%"

for /f "eol=# tokens=*" %%i in (addition\config\remoteRepoList.txt) do (
    echo %ESC%[36mgit push %%i %LOCAL_BRANCH%:%REMOTE_BRANCH%%ESC%[0m
    git push "%%i" "%LOCAL_BRANCH%:%REMOTE_BRANCH%" && (echo %ESC%[32m    OK%ESC%[0m) || (echo %ESC%[31m    FAILED%ESC%[0m)
)
