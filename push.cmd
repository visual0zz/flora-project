#!/usr/bin/env bash
@goto :windows || true
# ============================================================
#  push.cmd — 跨平台推送脚本（bash 段 / Windows cmd 段）
#
#  功能：
#   1. 读取 addition/config/pushConfig.txt 拿到 LOCAL_BRANCH /
#      REMOTE_BRANCH / DEFAULT_COMMIT_MESSAGE。
#   2. git add -A 并提交（若无可提交内容则跳过）。
#   3. 把当前分支推送到 addition/config/remoteRepoList.txt 中
#      列出的所有远程仓库。
#   4. 额外推送 addition/config/tagPrefixes.txt 中列出的「前缀」
#      对应的所有本地 git tag（按前缀隔离多产物的版本线，例如
#      ramet-idea-plugin-v0.8.2）。tag 是幂等的，已推送过的会被
#      远程跳过。
#
#  用法（任一操作系统通用）：
#      ./push.cmd                 # 使用默认提交信息
#      ./push.cmd "提交信息"       # 使用指定提交信息
#
#  注意：运行此脚本前，请确保你已：
#      - 在 addition/config/tagPrefixes.txt 维护了需要附带推送的
#        tag 前缀（每行一个，# 开头为注释）。
#      - 打好了对应前缀的 git tag（如 git tag ramet-idea-plugin-v0.8.2）。
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

# 推送匹配前缀清单的 tag（addition/config/tagPrefixes.txt）
# 用 refspec 通配一次性推送该前缀下的所有本地 tag。
push_matching_tags() {
  local repo="$1"
  local pf="addition/config/tagPrefixes.txt"
  [ -f "$pf" ] || return 0
  grep -v '^#' "$pf" | while IFS= read -r prefix || [ -n "$prefix" ]; do
    prefix="$(echo "$prefix" | xargs)"   # 去掉首尾空白
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
REM  push.cmd Windows 段（由 cmd.exe 执行）
REM  说明见上方 bash 段注释。tag 推送使用 refspec 通配：
REM      git push <remote> "refs/tags/<prefix>*:refs/tags/<prefix>*"
REM  该写法只需单层 for /f 读取前缀文件，避免命令型嵌套 for /f
REM  在部分 cmd 环境下的解析问题。
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
    REM 推送匹配前缀清单的 tag（addition\config\tagPrefixes.txt）
    REM 用 refspec 通配一次性推送整组前缀 tag，避免命令型嵌套 for /f 的坑
    if exist addition\config\tagPrefixes.txt (
        for /f "eol=# tokens=* delims=" %%p in (addition\config\tagPrefixes.txt) do (
            echo %ESC%[36mgit push %%i tags %%p*%ESC%[0m
            git push "%%i" "refs/tags/%%p*:refs/tags/%%p*" && (echo %ESC%[32m    OK%ESC%[0m) || (echo %ESC%[31m    FAILED%ESC%[0m)
        )
    )
)
endlocal
exit /b 0
