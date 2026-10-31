#!/usr/bin/env bash
set -euo pipefail
if python3 -c 'import json,urllib.request; x=json.load(urllib.request.urlopen("http://127.0.0.1:19222/json/list",timeout=1)); raise SystemExit(not any(v.get("url", "").startswith("https://xpui.app.spotify.com/") for v in x))' 2>/dev/null; then
  echo 'Spotify 연결 모드가 이미 실행 중입니다.'; exit 0
fi
if pgrep -x spotify >/dev/null; then
  echo 'Spotify가 실행 중입니다. Spotify를 완전히 종료한 뒤 다시 눌러 주세요.' >&2; exit 1
fi
if command -v snap >/dev/null && snap list spotify >/dev/null 2>&1; then
  spotify_cmd=(snap run spotify)
elif command -v spotify >/dev/null 2>&1; then
  spotify_cmd=("$(command -v spotify)")
else
  echo 'Spotify 데스크톱 앱을 찾지 못했습니다. Snap 또는 일반 패키지를 설치해 주세요.' >&2; exit 1
fi
if ! systemctl --user show-environment >/dev/null 2>&1; then
  echo '사용자 systemd 세션에 연결할 수 없습니다.' >&2; exit 1
fi
env_args=()
for name in DISPLAY WAYLAND_DISPLAY XAUTHORITY XDG_CURRENT_DESKTOP XDG_SESSION_TYPE DBUS_SESSION_BUS_ADDRESS; do
  if [[ -n "${!name:-}" ]]; then env_args+=("--setenv=$name=${!name}"); fi
done
exec systemd-run --user --unit=spotify-lyrics-session --collect "${env_args[@]}" "${spotify_cmd[@]}" --remote-debugging-port=19222 --remote-debugging-address=127.0.0.1
