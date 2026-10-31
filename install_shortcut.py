"""Create a user-local desktop launcher for this checkout."""
import os
from pathlib import Path

root = Path(__file__).resolve().parent
launcher = str(root / '실행하기.sh')
# Desktop Entry Exec fields use their own escaping rules and percent codes.
quoted = '"' + launcher.replace('\\', '\\\\').replace('"', '\\"').replace('`', '\\`').replace('$', '\\$').replace('%', '%%') + '"'
data_home = Path(os.environ.get('XDG_DATA_HOME') or Path.home() / '.local' / 'share')
apps = data_home / 'applications'
apps.mkdir(parents=True, exist_ok=True)
(apps / 'lyrics-float.desktop').write_text(
    '[Desktop Entry]\nType=Application\nName=Lyrics Float\n'
    'Name[ko]=가사 플로팅\nComment=Floating Spotify lyrics\n'
    f'Exec={quoted}\nIcon=audio-x-generic\nTerminal=false\n'
    'Categories=AudioVideo;Audio;\nStartupNotify=false\n', encoding='utf-8')
print('Application shortcut installed / 앱 바로가기를 만들었습니다.')
