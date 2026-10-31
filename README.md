# Lyrics Float · 가사 플로팅

A small desktop window that follows Spotify lyrics on Ubuntu.  
Ubuntu에서 Spotify 가사를 따라가는 작은 데스크톱 창입니다.

[English](#english) · [한국어](#한국어)

## English

Lyrics Float reads the song and playback position from the Spotify desktop app through MPRIS. It looks up time-synced lyrics on LRCLIB and highlights the current line. It does not need a Spotify password or developer account.

### Requirements

- Ubuntu Linux with a graphical desktop and a user systemd session.
- The Spotify desktop app. The browser player is not supported.
- Python 3.11 or newer, `venv`, and an internet connection.
- For Qt's X11 platform on Ubuntu, install `libxcb-cursor0` if it is missing.

```bash
sudo apt install python3-venv libxcb-cursor0
./setup.sh
./실행하기.sh
```

Run the commands from this repository's directory. The launcher runs the app in your desktop user session. This allows the app to read the MPRIS interface of Spotify's Snap package without changing its security policy.

### Controls

1. Open Spotify and play a song.
2. Drag the panel to move it. Drag an edge or corner to resize it. The panel can shrink to 240 × 110 pixels.
3. Select **고정** to keep the window above ordinary windows.
4. Select **⚙** for settings. **배경** adjusts background opacity. **글자 색** changes the current line's color. **배경 색** changes the background color. **글꼴** chooses the font. **가 −** and **가 +** change the text size.
5. Select **×** to close the app. Settings are saved locally.

Lyrics depend on LRCLIB coverage. Korean lyrics work when the service has a matching record with timestamps. Matching and timing can be inaccurate. Fullscreen applications may cover the window. This version has been developed for Ubuntu; Android is not yet supported.

### Privacy

The app sends the title, artist, album, and duration of the current track to LRCLIB to find lyrics. It stores appearance settings under `~/.config/lyrics-float/`. It does not ask for Spotify credentials.

## 한국어

가사 플로팅은 Spotify 데스크톱 앱에서 곡 정보와 재생 위치를 읽습니다. LRCLIB에서 시간 정보가 있는 가사를 찾고 현재 줄을 강조합니다. Spotify 비밀번호나 개발자 계정은 필요하지 않습니다.

### 준비와 실행

Ubuntu의 그래픽 환경과 Spotify 데스크톱 앱이 필요합니다. 브라우저에서 재생하는 Spotify는 아직 지원하지 않습니다. 인터넷과 Python 3.11 이상도 필요합니다.

저장소 폴더에서 아래 명령을 실행하세요.

```bash
sudo apt install python3-venv libxcb-cursor0
./setup.sh
./실행하기.sh
```

실행 스크립트는 사용자 세션에서 앱을 실행합니다. 따라서 Spotify의 Snap 패키지가 제공하는 재생 정보에 접근할 수 있습니다. Spotify의 보안 정책은 변경하지 않습니다.

### 사용 방법

1. Spotify에서 노래를 재생합니다.
2. 창을 끌어서 위치를 옮깁니다. 창의 가장자리나 모서리를 끌어서 크기를 바꿉니다. 최소 크기는 240 × 110픽셀입니다.
3. **고정**을 선택하면 일반 창 위에 표시됩니다.
4. **⚙**를 눌러 설정을 엽니다. **배경**으로 투명도를 바꿉니다. **글자 색**으로 현재 가사의 색을 바꿉니다. **배경 색**으로 배경의 색을 바꿉니다. **글꼴**로 글꼴을 선택합니다. **가 −**, **가 +**로 글자 크기를 바꿉니다.
5. **×**를 눌러 종료합니다. 설정은 컴퓨터에 저장됩니다.

한국어 곡도 LRCLIB에 일치하는 가사와 시간 정보가 있으면 표시됩니다. 곡에 따라 가사가 없거나 시간이 어긋날 수 있습니다. 전체 화면 앱은 가사 창을 가릴 수 있습니다. 현재 버전은 Ubuntu용입니다. 안드로이드 버전은 아직 없습니다.

### 개인정보

가사를 찾을 때 현재 곡의 제목, 가수, 앨범, 길이를 LRCLIB에 보냅니다. 화면 설정은 `~/.config/lyrics-float/`에 저장합니다. Spotify의 로그인 정보는 요청하지 않습니다.
