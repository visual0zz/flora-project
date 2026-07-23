#!/usr/bin/env bash
# ====== Strip all comments from Java files in a given subdirectory ======
# Usage: strip-jcomments.cmd <relative-dir>
#   <relative-dir>  relative path from project root (e.g. flora-project/flora-root/src)
@goto :windows || true

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

if [ $# -eq 0 ]; then
  echo "Usage: $(basename "$0") <relative-dir>" >&2
  exit 1
fi

TARGET_DIR="$PROJECT_DIR/$1"
if [ ! -d "$TARGET_DIR" ]; then
  echo "Error: directory not found: $TARGET_DIR" >&2
  exit 1
fi

python3 - "$TARGET_DIR" << 'PYEOF'
import os, sys

def strip_java_comments(text):
    result = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == '"':
            result.append(c)
            i += 1
            while i < n:
                ch = text[i]
                result.append(ch)
                if ch == '\\' and i + 1 < n:
                    i += 1
                    result.append(text[i])
                elif ch == '"':
                    break
                i += 1
            i += 1
            continue
        if c == "'":
            result.append(c)
            i += 1
            while i < n:
                ch = text[i]
                result.append(ch)
                if ch == '\\' and i + 1 < n:
                    i += 1
                    result.append(text[i])
                elif ch == "'":
                    break
                i += 1
            i += 1
            continue
        if c == '/' and i + 1 < n and text[i + 1] == '/':
            i += 2
            while i < n and text[i] != '\n':
                i += 1
            continue
        if c == '/' and i + 1 < n and text[i + 1] == '*':
            i += 2
            while i + 1 < n and not (text[i] == '*' and text[i + 1] == '/'):
                i += 1
            i += 2
            continue
        result.append(c)
        i += 1
    return ''.join(result)

root = sys.argv[1]
count = 0
changed = 0
for dirpath, dirnames, filenames in os.walk(root):
    for fn in filenames:
        if not fn.endswith('.java'):
            continue
        fp = os.path.join(dirpath, fn)
        with open(fp, 'r', encoding='utf-8') as f:
            original = f.read()
        stripped = strip_java_comments(original)
        if stripped != original:
            with open(fp, 'w', encoding='utf-8') as f:
                f.write(stripped)
            changed += 1
        count += 1
print(f'Processed: {count} files, Changed: {changed} files')
PYEOF

exit 0

:windows
@echo off
setlocal enabledelayedexpansion
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"

if "%~1"=="" (
    echo %ESC%[31mUsage: %~n0 ^<relative-dir^>%ESC%[0m
    exit /b 1
)

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%..\.." || exit /b 1
set "PROJECT_DIR=%CD%"
set "TARGET_DIR=%PROJECT_DIR%\%~1"
if not exist "%TARGET_DIR%\" (
    echo %ESC%[31mError: directory not found: %TARGET_DIR%%ESC%[0m
    exit /b 1
)

set "PS_FILE=%TEMP%\strip-jcomments_%RANDOM%.ps1"

> "%PS_FILE%" (
    echo param([string]$dir^)
    echo $count=0; $changed=0
    echo function Strip-JavaComments ^([string]$text^) ^{
    echo     $sb = New-Object System.Text.StringBuilder
    echo     $i = 0; $n = $text.Length
    echo     while ^($i -lt $n^) ^{
    echo         $c = $text[$i]
    echo         if ^($c -eq [char]34^) ^{ [void]$sb.Append($c^); $i++
    echo             while ^($i -lt $n^) ^{ $ch = $text[$i]; [void]$sb.Append($ch^)
    echo                 if ^($ch -eq '\' -and $i+1 -lt $n^) ^{ $i++; [void]$sb.Append($text[$i]^) }
    echo                 elseif ^($ch -eq [char]34^) ^{ break }
    echo                 $i++
    echo             }; $i++; continue }
    echo         if ^($c -eq [char]39^) ^{ [void]$sb.Append($c^); $i++
    echo             while ^($i -lt $n^) ^{ $ch = $text[$i]; [void]$sb.Append($ch^)
    echo                 if ^($ch -eq '\' -and $i+1 -lt $n^) ^{ $i++; [void]$sb.Append($text[$i]^) }
    echo                 elseif ^($ch -eq [char]39^) ^{ break }
    echo                 $i++
    echo             }; $i++; continue }
    echo         if ^($c -eq '/' -and $i+1 -lt $n -and $text[$i+1] -eq '/'^) ^{
    echo             $i += 2
    echo             while ^($i -lt $n -and $text[$i] -ne [char]10^) ^{ $i++ }
    echo             continue }
    echo         if ^($c -eq '/' -and $i+1 -lt $n -and $text[$i+1] -eq '*'^) ^{
    echo             $i += 2
    echo             while ^($i+1 -lt $n -and -not ^($text[$i] -eq '*' -and $text[$i+1] -eq '/'^)^) ^{ $i++ }
    echo             $i += 2; continue }
    echo         [void]$sb.Append($c^); $i++
    echo     }
    echo     return $sb.ToString()
    echo }
    echo Get-ChildItem $dir -Recurse -Filter *.java ^| ForEach-Object ^{
    echo     $path = $_.FullName
    echo     $orig = [System.IO.File]::ReadAllText^($path^)
    echo     $stripped = Strip-JavaComments $orig
    echo     if ^($stripped -ne $orig^) ^{
    echo         [System.IO.File]::WriteAllText^($path, $stripped^)
    echo         $changed++
    echo     }
    echo     $count++
    echo }
    echo Write-Host ^("Processed: $count files, Changed: $changed files"^)
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%PS_FILE%" "%TARGET_DIR%"
del "%PS_FILE%"

exit /b %ERRORLEVEL%
