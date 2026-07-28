#!/usr/bin/env bash
# ====== for humans only, AI agents should NOT run this script ======
@goto :windows || true
# ====== Strip UTF-8 BOM (EF BB BF) from all text files in the project ======
GREEN='\033[0;32m'; NC='\033[0m'

PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
echo "Scanning $PROJECT_DIR for text files with BOM ..."
echo ""

is_text_file() {
    local file="$1"
    if command -v file &>/dev/null; then
        local mime
        mime=$(file -b --mime-encoding "$file" 2>/dev/null)
        [ "$mime" != "binary" ] && [ "$mime" != "unknown" ] && [ -n "$mime" ] && return 0
    fi
    local suspicious
    suspicious=$(head -c 512 "$file" 2>/dev/null | od -An -tu1 -v 2>/dev/null | awk '
    { for (i=1; i<=NF; i++) {
        v = $i + 0
        if (v == 0 || (v >= 1 && v <= 8) || (v >= 11 && v <= 12) || (v >= 14 && v <= 31) || v == 127) s++
        t++
    }}
    END { if (t > 0 && s/t > 0.15) print "binary"; else print "text" }')
    [ "$suspicious" != "binary" ]
}

count=0
while IFS= read -r -d '' f; do
    case "${f##*.}" in
        class|jar|war|png|jpg|jpeg|gif|ico|bmp|tif|tiff|webp|avif|psd|exe|dll|so|o|obj|lib|a|dylib|pyc|pyo|pyd|zip|gz|tar|7z|rar|bz2|xz|lz|lzma|z|cab|arj|lzh|iso|img|woff|woff2|ttf|eot|otf|pdf|doc|docx|xls|xlsx|ppt|pptx|pps|ppsx|odp|odt|ods|pub|vsd|vsdx|mp3|wav|ogg|flac|aac|wma|m4a|mid|midi|ape|aiff|opus|mp4|avi|mkv|mov|wmv|flv|webm|mpg|mpeg|3gp|ogv|ts|swf|db|mdb|accdb|dbf|sqlite|sqlitedb|dex|jmod|unity3d|asset)
            continue ;;
    esac
    bom=$(head -c 3 "$f" | od -An -tx1 | tr -d ' \n')
    [ "$bom" != "efbbbf" ] && continue
    is_text_file "$f" || continue
    tail -c +4 "$f" > "$f.tmp" 2>/dev/null && mv "$f.tmp" "$f" && echo "  stripped: ${f#$PROJECT_DIR/}" && count=$((count + 1))
done < <(find "$PROJECT_DIR" -type f ! -path "*/target/*" ! -path "*/.git/*" ! -path "*/.idea/*" -print0)

echo ""
printf '%b\n' "${GREEN}Done. $count file(s) processed.${NC}"
exit 0

:windows
@echo off
setlocal enabledelayedexpansion
for /F %%a in ('echo prompt $E ^| cmd') do set "ESC=%%a"
cd /d "%~dp0..\.." || exit /b 1
echo Scanning %CD% for text files with BOM ...
echo.

powershell -NoProfile -ExecutionPolicy Bypass -Command "$root=(Get-Location).Path; $count=0; $binaryExts=@('.3gp','.a','.aac','.accdb','.aiff','.ape','.arj','.asset','.avi','.bmp','.bz2','.cab','.class','.dbf','.dex','.dll','.doc','.docx','.dylib','.eot','.exe','.flac','.flv','.gif','.gz','.ico','.img','.iso','.jar','.jmod','.jpeg','.jpg','.lz','.lzma','.m4a','.mdb','.mid','.midi','.mkv','.mov','.mp3','.mp4','.mpg','.mpeg','.o','.obj','.odp','.ods','.odt','.ogg','.ogv','.opus','.otf','.pdf','.png','.pps','.ppsx','.ppt','.pptx','.psd','.pub','.pyc','.pyd','.pyo','.rar','.so','.sqlite','.sqlitedb','.swf','.tar','.tbz2','.tgz','.tif','.tiff','.ts','.ttf','.txz','.unity3d','.vsd','.vsdx','.war','.wav','.webm','.webp','.wma','.wmv','.woff','.woff2','.xls','.xlsx','.xz','.z','.zip','.zst'); function Is-TextFile($b){$len=[Math]::Min(512,$b.Length);$s=0;for($i=0;$i -lt $len;$i++){$v=$b[$i];if($v -eq 0 -or ($v -ge 1 -and $v -le 8) -or ($v -ge 11 -and $v -le 12) -or ($v -ge 14 -and $v -le 31) -or $v -eq 127){$s++}};return ($len -eq 0 -or $s -le $len * 0.15)}; Get-ChildItem -Path $root -Recurse -File | ForEach-Object {$path=$_.FullName; if($binaryExts -contains $_.Extension.ToLower()){return}; if($path -match '[/\\]target[/\\]' -or $path -match '[/\\]\.git[/\\]' -or $path -match '[/\\]\.idea[/\\]'){return}; try{$bytes=[System.IO.File]::ReadAllBytes($path); if($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF){if($bytes.Length -eq 3){$newBytes=@()}else{$newBytes=$bytes[3..($bytes.Length-1)]}; if(-not (Is-TextFile $newBytes)){return}; [System.IO.File]::WriteAllBytes($path,$newBytes); $rel=$path.Substring($root.Length+1); Write-Host ('  stripped: '+$rel); $count++}}catch{}}; Write-Host ''; Write-Host %ESC%[32m('Done. '+$count+' file(s) processed.')%ESC%[0m"

exit /b %ERRORLEVEL%
