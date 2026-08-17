#!/usr/bin/env bash
cd "$(dirname "$0")"
java --module-path lib --module com.flora.sanctum.app/com.flora.sanctum.app.Main "$@"
