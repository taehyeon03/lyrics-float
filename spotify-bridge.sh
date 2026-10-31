#!/usr/bin/env bash
set -euo pipefail
if ! command -v snap >/dev/null || ! snap list spotify >/dev/null 2>&1; then
  echo 'Spotify Snap이 설치되어 있지 않습니다.' >&2; exit 1
fi
if pgrep -x spotify >/dev/null; then
  echo 'Spotify를 완전히 종료한 뒤 다시 실행해 주세요.' >&2; exit 1
fi
exec systemd-run --user --unit=spotify-lyrics-session --collect snap run spotify --remote-debugging-port=19222 --remote-debugging-address=127.0.0.1
