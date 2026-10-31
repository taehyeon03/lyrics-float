#!/usr/bin/env bash
cd "$(dirname "$0")"
if systemctl --user is-active --quiet lyrics-float.service; then exit 0; fi
exec systemd-run --user --unit=lyrics-float --collect --setenv=QT_QPA_PLATFORM=xcb --working-directory="$PWD" "$PWD/.venv/bin/python" "$PWD/app.py"
