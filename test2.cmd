@echo off
echo === A: eol=# on PURE-ASCII file ===
for /f "eol=# tokens=*" %%p in (test_prefix.txt) do echo A=%%p
echo === B: eol=# on UTF8+Chinese file ===
for /f "eol=# tokens=*" %%p in (addition\config\tagPrefixes.txt) do echo B=%%p
echo === C: manual first-char check on UTF8 file ===
for /f "tokens=*" %%p in (addition\config\tagPrefixes.txt) do (
    set "L=%%p"
    if not "%%p"=="" if /i not "%%p:~0,1%"=="#" echo C=%%p
)
echo DONE
