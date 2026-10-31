#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if [[ -x /usr/bin/python3 ]]; then python=/usr/bin/python3; else python=python3; fi
if ! "$python" -c 'import sys; raise SystemExit(sys.version_info < (3,10))'; then
  printf '%s\n' 'Python 3.10 or newer is required. / Python 3.10 이상이 필요합니다.' >&2
  exit 1
fi
if ! "$python" -m venv .venv; then
  printf '%s\n' 'Install python3-venv and retry: sudo apt install python3-venv' >&2
  exit 1
fi
if command -v uv >/dev/null 2>&1; then
  uv pip install --python .venv/bin/python -r requirements.txt
else
  .venv/bin/python -m pip install -r requirements.txt
fi
if [[ "${1:-}" != "--no-desktop" ]]; then
  .venv/bin/python install_shortcut.py
fi
printf '%s\n' 'Setup complete. Run ./실행하기.sh or search for Lyrics Float.'
