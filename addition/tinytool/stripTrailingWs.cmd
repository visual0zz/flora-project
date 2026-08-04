#!/usr/bin/env bash
# ====== for humans only, AI agents should NOT run this script ======
@goto :windows || true
# ====== Strip trailing whitespace from all code/config files ======
GREEN='\033[0;32m'; NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
echo "Scanning $PROJECT_DIR for trailing whitespace ..."
echo ""

count=0
while IFS= read -r -d '' f; do
    case "${f##*.}" in
        java|xml|json|yaml|yml|properties|gradle|kts|kt|kts\
        |cmd|bat|sh|ps1|md|txt|cfg|ini|toml|conf|config\
        |css|js|ts|html|htm|sql|c|h|cpp|hpp|py|rb|go|rs\
        |bnd|gitignore|gitattributes|editorconfig)
            ;;
        *) continue ;;
    esac
    # Skip files that also match known binary extensions
    case "$f" in
        *.class|*.jar|*.war|*.png|*.jpg|*.jpeg|*.gif|*.ico|*.bmp|*.exe|*.dll|*.so|*.o|*.zip|*.gz|*.tar|*.7z|*.rar|*.pdf|*.woff|*.woff2|*.ttf|*.eot)
            continue ;;
    esac
    # Use sed to strip trailing whitespace in-place; skip if no change
    if sed -i 's/[[:blank:]]*$//' "$f" 2>/dev/null; then
        echo "  cleaned: ${f#$PROJECT_DIR/}"
        count=$((count + 1))
    fi
done < <(find "$PROJECT_DIR" -type f ! -path "*/.git/*" ! -path "*/target/*" ! -path "*/.idea/*" ! -path "*/node_modules/*" -print0)

echo ""
printf '%b\n' "${GREEN}Done. $count file(s) cleaned.${NC}"
exit 0

:windows
@echo off
setlocal enabledelayedexpansion
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0..\.." || exit /b 1
echo Scanning %CD% for trailing whitespace ...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "$root=(Get-Location).Path; $count=0; $exts=@('.java','.xml','.json','.yaml','.yml','.properties','.gradle','.kts','.kt','.cmd','.bat','.sh','.ps1','.md','.txt','.cfg','.ini','.toml','.conf','.config','.css','.js','.ts','.html','.htm','.sql','.c','.h','.cpp','.hpp','.py','.rb','.go','.rs','.bnd','.gitignore','.gitattributes','.editorconfig'); Get-ChildItem -Path $root -Recurse -File | Where-Object {$exts -contains $_.Extension.ToLower()} | Where-Object {$_.FullName -notmatch '[/\\]\.git[/\\]' -and $_.FullName -notmatch '[/\\]target[/\\]' -and $_.FullName -notmatch '[/\\]\.idea[/\\]' -and $_.FullName -notmatch '[/\\]node_modules[/\\]'} | ForEach-Object {try{$content=[System.IO.File]::ReadAllText($_.FullName); $lines=$content -split '\r?\n'; $cleaned=($lines | ForEach-Object {$_.TrimEnd()}) -join \"`n\"; if($content.EndsWith(\"`r`n\") -or $content.EndsWith(\"`n\")){$cleaned+=\"`n\"}; if($content -ne $cleaned){[System.IO.File]::WriteAllText($_.FullName,$cleaned,[System.Text.UTF8Encoding]::new($false)); $rel=$_.FullName.Substring($root.Length+1); Write-Host ('  cleaned: '+$rel); $count++}}catch{}}; Write-Host ''; Write-Host %ESC%[32m('Done. '+$count+' file(s) cleaned.')%ESC%[0m"

exit /b %ERRORLEVEL%
