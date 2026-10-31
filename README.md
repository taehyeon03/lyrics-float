# Lyrics Float · 가사 플로팅

A small desktop window that follows Spotify lyrics on Ubuntu.  
Ubuntu에서 Spotify 가사를 따라가는 작은 데스크톱 창입니다.

[English](#english) · [한국어](#한국어)

## English

Lyrics Float reads the song and playback position from the Spotify desktop app through MPRIS. It looks up time-synced lyrics on LRCLIB and highlights the current line. It does not need a Spotify password or developer account.

### Requirements

- Ubuntu Linux with a graphical desktop and a user systemd session.
- The Spotify desktop app. The browser player is not supported.
- Python 3.10 or newer, `venv`, and an internet connection.
- This has been tested on Ubuntu 24.04 x86-64. Other Ubuntu releases and architectures have not yet been tested.
- For Qt's X11 platform on Ubuntu, install `libxcb-cursor0` if it is missing.

```bash
sudo apt install git python3-venv libxcb-cursor0
git clone https://github.com/taehyeon03/lyrics-float.git
cd lyrics-float
./setup.sh
./실행하기.sh
```

Setup creates a user-local application shortcut. Search for **Lyrics Float** in the app menu. If you move the folder, run `./setup.sh` again. Setup uses `uv` when it is available and otherwise uses `pip`. The launcher runs the app in your desktop user session. This allows the app to read the MPRIS interface of Spotify's Snap package without changing its security policy.

### Controls

1. Open Spotify and play a song.
2. Drag the panel to move it. Drag an edge or corner to resize it. The panel can shrink to 240 × 110 pixels.
3. Select **고정** to keep the window above ordinary windows.
4. Select **⚙** for settings. **배경** adjusts background opacity. **글자 색** changes the current line's color. **배경 색** changes the background color. **글꼴** chooses the font. **가 −** and **가 +** change the text size. Enable **움직이는 물결** to show a decorative wave. Enable **노래 타임라인 표시** for playback progress and **재생 제어 버튼 표시** for previous, play/pause, and next controls. The progress bar is display-only.
5. Select **×** to close the app. Settings are saved locally.

Lyrics depend on LRCLIB coverage. Korean lyrics work when the service has a matching record with timestamps. Matching and timing can be inaccurate. Fullscreen applications may cover the window. This version has been developed for Ubuntu; Android is not yet supported.

### Privacy

The app sends the title, artist, album, and duration of the current track to LRCLIB to find lyrics. It stores appearance settings under `~/.config/lyrics-float/`. It does not ask for Spotify credentials. The optional wave is decorative. The app does not capture or analyze audio.

## 한국어

가사 플로팅은 Spotify 데스크톱 앱에서 곡 정보와 재생 위치를 읽습니다. LRCLIB에서 시간 정보가 있는 가사를 찾고 현재 줄을 강조합니다. Spotify 비밀번호나 개발자 계정은 필요하지 않습니다.

### 준비와 실행

Ubuntu의 그래픽 환경과 Spotify 데스크톱 앱이 필요합니다. 브라우저에서 재생하는 Spotify는 아직 지원하지 않습니다. 인터넷과 Python 3.10 이상도 필요합니다. 현재 Ubuntu 24.04의 x86-64 컴퓨터에서 확인했습니다. 다른 Ubuntu 버전과 CPU에서는 아직 검증하지 못했습니다.

터미널에서 아래 명령을 실행하세요.

```bash
sudo apt install git python3-venv libxcb-cursor0
git clone https://github.com/taehyeon03/lyrics-float.git
cd lyrics-float
./setup.sh
./실행하기.sh
```

설치가 끝나면 앱 목록에서 **가사 플로팅**을 검색해 실행할 수 있습니다. 폴더를 옮기면 새 위치에서 `./setup.sh`를 다시 실행하세요. 설치할 때 `uv`가 있으면 이를 사용하고, 없으면 `pip`를 사용합니다.

실행 스크립트는 사용자 세션에서 앱을 실행합니다. 따라서 Spotify의 Snap 패키지가 제공하는 재생 정보에 접근할 수 있습니다. Spotify의 보안 정책은 변경하지 않습니다.

### 사용 방법

1. Spotify에서 노래를 재생합니다.
2. 창을 끌어서 위치를 옮깁니다. 창의 가장자리나 모서리를 끌어서 크기를 바꿉니다. 최소 크기는 240 × 110픽셀입니다.
3. **고정**을 선택하면 일반 창 위에 표시됩니다.
4. **⚙**를 눌러 설정을 엽니다. **배경**으로 투명도를 바꿉니다. **글자 색**으로 현재 가사의 색을 바꿉니다. **배경 색**으로 배경의 색을 바꿉니다. **글꼴**로 글꼴을 선택합니다. **가 −**, **가 +**로 글자 크기를 바꿉니다. **움직이는 물결**을 켜면 부드럽게 움직이는 장식이 나타납니다. **노래 타임라인 표시**와 **재생 제어 버튼 표시**는 각각 켜고 끌 수 있습니다. 재생 제어 버튼을 켜면 이전 곡, 재생·일시 정지, 다음 곡 버튼이 함께 표시됩니다. 타임라인은 현재 위치를 표시하며 이동 기능은 없습니다.
5. **×**를 눌러 종료합니다. 설정은 컴퓨터에 저장됩니다.

한국어 곡도 LRCLIB에 일치하는 가사와 시간 정보가 있으면 표시됩니다. 곡에 따라 가사가 없거나 시간이 어긋날 수 있습니다. 전체 화면 앱은 가사 창을 가릴 수 있습니다. 현재 버전은 Ubuntu용입니다. 안드로이드 버전은 아직 없습니다.

### 개인정보

가사를 찾을 때 현재 곡의 제목, 가수, 앨범, 길이를 LRCLIB에 보냅니다. 화면 설정은 `~/.config/lyrics-float/`에 저장합니다. Spotify의 로그인 정보는 요청하지 않습니다. 물결은 장식용이며 오디오를 읽거나 분석하지 않습니다.
