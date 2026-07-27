@echo off
echo === test eol=# tokens=* ===
for /f "eol=# tokens=*" %%p in (addition\config\tagPrefixes.txt) do echo LINE_A=%%p
echo === test default (no eol) ===
for /f "tokens=*" %%p in (addition\config\tagPrefixes.txt) do echo LINE_B=%%p
echo === test usebackq eol=# ===
for /f "usebackq eol=# tokens=*" %%p in ("addition\config\tagPrefixes.txt") do echo LINE_C=%%p
echo DONE
