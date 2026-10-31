#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ ! -x .venv/bin/python ]]; then
  printf '%s\n' 'Run ./setup.sh first. / 먼저 ./setup.sh를 실행하세요.' >&2
  exit 1
fi
if [[ -z "${DISPLAY:-}" && -z "${WAYLAND_DISPLAY:-}" ]]; then
  printf '%s\n' 'Start this application from a graphical desktop session.' >&2
  exit 1
fi
export QT_QPA_PLATFORM="${QT_QPA_PLATFORM:-xcb}"
if command -v systemd-run >/dev/null && systemctl --user show-environment >/dev/null 2>&1; then
  if systemctl --user is-active --quiet lyrics-float.service; then exit 0; fi
  env_args=("--setenv=QT_QPA_PLATFORM=$QT_QPA_PLATFORM")
  for name in DISPLAY WAYLAND_DISPLAY XAUTHORITY XDG_CURRENT_DESKTOP XDG_SESSION_TYPE DBUS_SESSION_BUS_ADDRESS; do
    if [[ -n "${!name:-}" ]]; then env_args+=("--setenv=$name=${!name}"); fi
  done
  exec systemd-run --user --unit=lyrics-float --collect "${env_args[@]}" --working-directory="$PWD" "$PWD/.venv/bin/python" "$PWD/app.py"
fi
printf '%s\n' 'User systemd unavailable; starting directly. Spotify Snap may restrict access.' >&2
exec "$PWD/.venv/bin/python" "$PWD/app.py"
