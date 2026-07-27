#!/usr/bin/env bash
@goto :windows || true
# ============================================================
#  push.cmd - cross-platform push helper (bash section / Windows cmd section)
#
#  What it does:
#   1. Reads addition/config/pushConfig.txt for LOCAL_BRANCH /
#      REMOTE_BRANCH / DEFAULT_COMMIT_MESSAGE.
#   2. git add -A and commit (skips if there is nothing to commit).
#   3. Pushes the current branch to every remote listed in
#      addition/config/remoteRepoList.txt.
#   4. Additionally pushes every local git tag whose prefix is listed
#      in addition/config/tagPrefixes.txt. Tags are prefixed per product
#      to keep multiple version lines separate in one monorepo
#      (e.g. ramet-idea-plugin-v0.8.2). Pushing a tag is idempotent;
#      tags already on the remote are skipped.
#
#  Usage (same on every OS):
#      ./push.cmd                # use the default commit message
#      ./push.cmd "commit msg"   # use a custom commit message
#
#  Before running, make sure you have:
#      - maintained the tag prefixes in addition/config/tagPrefixes.txt
#        (one prefix per line, lines starting with # are comments).
#      - created the matching git tag(s), e.g.
#        git tag ramet-idea-plugin-v0.8.2
# ============================================================
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
git commit -m "$message" || echo "(nothing to commit)"

# Push every local tag whose prefix is listed in tagPrefixes.txt.
# Use a refspec wildcard to push the whole prefix group in one go.
push_matching_tags() {
  local repo="$1"
  local pf="addition/config/tagPrefixes.txt"
  [ -f "$pf" ] || return 0
  grep -v '^#' "$pf" | while IFS= read -r prefix || [ -n "$prefix" ]; do
    prefix="$(echo "$prefix" | xargs)"   # trim whitespace
    [ -z "$prefix" ] && continue
    if git tag -l "${prefix}*" | grep -q .; then
      printf '%b\n' "${CYAN}git push $repo tags ${prefix}*${NC}"
      if git push "$repo" "refs/tags/${prefix}*:refs/tags/${prefix}*"; then
        printf '%b\n' "${GREEN}    \xe2\x9c\x93 OK${NC}"
      else
        printf '%b\n' "${RED}    \xe2\x9c\x97 FAILED${NC}"
      fi
    fi
  done
}

grep -v '^#' "addition/config/remoteRepoList.txt" | while IFS= read -r repo_url || [ -n "$repo_url" ]; do
  [ -z "$repo_url" ] && continue
  printf '%b\n' "${CYAN}git push $repo_url ${LOCAL_BRANCH}:${REMOTE_BRANCH}${NC}"
  if git push "$repo_url" "${LOCAL_BRANCH}:${REMOTE_BRANCH}"; then
    printf '%b\n' "${GREEN}    \xe2\x9c\x93 OK${NC}"
  else
    printf '%b\n' "${RED}    \xe2\x9c\x97 FAILED${NC}"
  fi
  push_matching_tags "$repo_url"
done
exit 0

:windows
@echo off
setlocal
REM ============================================================
REM  push.cmd - Windows cmd section (executed by cmd.exe)
REM  See the bash section above for the full description.
REM  Tag pushing uses a refspec wildcard:
REM      git push <remote> "refs/tags/<prefix>*:refs/tags/<prefix>*"
REM  This needs only a single for /f to read the prefix file and
REM  avoids the fragile nested command-style for /f entirely.
REM ============================================================
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
git commit -m "%message%" || echo %ESC%[33m(nothing to commit)%ESC%[0m

for /f "eol=# tokens=*" %%i in (addition\config\remoteRepoList.txt) do (
    echo %ESC%[36mgit push %%i %LOCAL_BRANCH%:%REMOTE_BRANCH%%ESC%[0m
    git push "%%i" "%LOCAL_BRANCH%:%REMOTE_BRANCH%" && (echo %ESC%[32m    OK%ESC%[0m) || (echo %ESC%[31m    FAILED%ESC%[0m)
    if exist addition\config\tagPrefixes.txt (
        for /f "eol=# tokens=*" %%p in (addition\config\tagPrefixes.txt) do (
            call :push_tag "%%i" "%%p"
        )
    )
)
endlocal
exit /b 0

:push_tag
set "R=%~1"
set "P=%~2"
echo [tag push] %R%  refs/tags/%P%*
git push "%R%" "refs/tags/%P%*:refs/tags/%P%*" && echo     OK || echo     FAILED
goto :eof
