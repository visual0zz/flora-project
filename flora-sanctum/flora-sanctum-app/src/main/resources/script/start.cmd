@echo off
cd /d "%~dp0"
java --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main %*
