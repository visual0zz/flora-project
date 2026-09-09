#!/usr/bin/env bash
@goto :windows || true
# Cross-platform launcher: run flora-sanctum via JPMS module path (lib/) from this script's directory.
cd "$(dirname "$0")" || exit 1
exec java --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main "$@" || exit 1

:windows
@echo off
cd /d "%~dp0"
java --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main %*
