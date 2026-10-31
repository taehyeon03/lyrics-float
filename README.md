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
4. Select **⚙** for settings. **배경** adjusts background opacity. **글자 색** changes the current line's color. **배경 색** changes the background color. **글꼴** chooses the font. **가 −** and **가 +** change the text size. Enable **움직이는 물결** to show a decorative wave. Enable **노래 타임라인 표시** for playback progress and **재생 제어 버튼 표시** for previous, play/pause, and next controls. Click or drag the timeline to seek when Spotify allows it.
5. Select **×** to close the app. Settings are saved locally.

Lyrics depend on LRCLIB coverage. Korean lyrics work when the service has a matching record with timestamps. Matching and timing can be inaccurate. Fullscreen applications may cover the window. The desktop version has been developed for Ubuntu. An Android version is described below.

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
4. **⚙**를 눌러 설정을 엽니다. **배경**으로 투명도를 바꿉니다. **글자 색**으로 현재 가사의 색을 바꿉니다. **배경 색**으로 배경의 색을 바꿉니다. **글꼴**로 글꼴을 선택합니다. **가 −**, **가 +**로 글자 크기를 바꿉니다. **움직이는 물결**을 켜면 부드럽게 움직이는 장식이 나타납니다. **노래 타임라인 표시**와 **재생 제어 버튼 표시**는 각각 켜고 끌 수 있습니다. 재생 제어 버튼을 켜면 이전 곡, 재생·일시 정지, 다음 곡 버튼이 함께 표시됩니다. Spotify가 이동을 허용하는 곡에서는 타임라인을 누르거나 끌어서 재생 위치를 바꿀 수 있습니다.
5. **×**를 눌러 종료합니다. 설정은 컴퓨터에 저장됩니다.

한국어 곡도 LRCLIB에 일치하는 가사와 시간 정보가 있으면 표시됩니다. 곡에 따라 가사가 없거나 시간이 어긋날 수 있습니다. 전체 화면 앱은 가사 창을 가릴 수 있습니다. 컴퓨터 버전은 Ubuntu용입니다. 안드로이드 버전은 아래를 참고하세요.

### 개인정보

가사를 찾을 때 현재 곡의 제목, 가수, 앨범, 길이를 LRCLIB에 보냅니다. 화면 설정은 `~/.config/lyrics-float/`에 저장합니다. Spotify의 로그인 정보는 요청하지 않습니다. 물결은 장식용이며 오디오를 읽거나 분석하지 않습니다.

## License / 라이선스

This project is licensed under the [GNU General Public License v3.0 only](LICENSE). It uses PyQt6, which is offered under GPL v3 or a separate commercial license. Third-party components and lyric services have their own terms.

이 프로젝트는 [GNU 일반 공중 사용 허가서 버전 3.0만](LICENSE) 적용합니다. 사용하는 PyQt6에는 GPL v3 또는 별도의 상용 라이선스가 적용됩니다. 다른 외부 구성 요소와 가사 서비스에도 각각의 이용 조건이 있습니다.

### Experimental Spotify fallback / Spotify 보조 가사 (실험 기능)

If LRCLIB cannot provide synced lyrics, the app can use lyrics that the Spotify desktop client loads. On Ubuntu, close Spotify completely, select **Spotify 가사 연결 실행** in the app settings, then open Spotify's lyrics view while listening. Snap and standard package launchers are detected; other installation types have not been tested. Keep that view open as tracks change. The app also checks the currently open lyrics view when it connects. The lyrics are kept only in memory and matched by track ID. This is an unofficial method and may stop working after Spotify updates. Re-run `./setup.sh` after updating to install the additional dependency.

**Security:** this mode opens Spotify's debugging interface on `127.0.0.1:19222`. Other local processes can potentially control the Spotify session while it is open. Close Spotify completely and reopen it normally to turn the interface off. It is not reachable directly from other computers through this listening address.

LRCLIB에서 동기화된 가사를 찾지 못하면 Spotify가 직접 불러온 가사를 보조로 사용할 수 있습니다. Ubuntu에서 Spotify를 완전히 종료한 다음 앱의 설정에서 **Spotify 가사 연결 실행**을 누르세요. Snap과 일반 패키지 실행 경로를 감지합니다. 다른 설치 방식은 검증하지 않았습니다. 노래를 재생하면서 Spotify의 가사 창을 열고 유지하세요. 연결할 때 이미 열려 있는 가사도 확인합니다. 가사는 곡 ID로 구분하며 메모리에만 보관합니다. 비공식 방식이라 Spotify 업데이트 후 작동하지 않을 수 있습니다. 업데이트한 뒤에는 `./setup.sh`를 다시 실행해 필요한 라이브러리를 설치하세요.

**보안:** 이 모드에서는 Spotify의 디버그 인터페이스가 `127.0.0.1:19222`에서 열립니다. 이 컴퓨터의 다른 프로세스가 Spotify 세션을 제어할 가능성이 있습니다. 인터페이스를 닫으려면 Spotify를 완전히 종료한 뒤 평소 방식으로 다시 실행하세요. 이 주소는 다른 컴퓨터에서 직접 접근할 수 없습니다.

## Android · 안드로이드

### English

The Android app shows a floating lyrics panel above other apps while Spotify plays on the phone. It follows the current line in real time and uses the same design as the desktop version.

**Tested device**

| | |
|---|---|
| Phone | Samsung Galaxy S25 Ultra (SM-S938N) |
| OS | Android 17 (API 37), One UI 9.0 |
| CPU | arm64-v8a |
| Spotify | 9.1.94 |

The app targets Android 8.0 (API 26) or later. It has only been tested on the device above. Other phones and Android versions may behave differently.

**Install**

1. Download `lyrics-float-android.apk` from [Releases](https://github.com/taehyeon03/lyrics-float/releases).
2. Open the file on the phone and allow installation from that source when Android asks. The APK is a debug build signed with a development key, so Play Protect may show a warning.
3. Open **가사 플로팅** (Lyrics Float).
4. Select **알림 접근** (notification access) and turn it on for 가사 플로팅. If Android says the setting is restricted, open the app's info page, select ⋮ → **Allow restricted settings**, and try again.
5. Select **다른 앱 위에 표시** (display over other apps) and allow it.
6. Optional: select **Spotify 로그인** (Spotify sign-in) and sign in on Spotify's own page to use Spotify's lyrics. Read the warning below first.
7. Play a song in Spotify, then select **가사 창 띄우기** (show lyrics panel).

**Using the panel**

- Drag anywhere on the panel to move it. Pinch with two fingers to resize the panel and the lyric text together around the point between your fingers.
- Use the timeline to see and change the playback position. Use the buttons for previous, play/pause, and next. If Spotify is closed, the play button starts it.
- Select ⚙ to change opacity, text size, lyric color, and background color. Pick a preset or use the rainbow slider for any color. You can also turn the wave, the timeline, the playback buttons, and Spotify lyrics on or off.
- Select × to close the panel.

**How lyrics are found**

The app reads the title, artist, album, duration, and position of the Spotify track from Android's media session. Notification access is needed only for this; the app does not read other notifications. If you are signed in to Spotify, the app uses Spotify's own time-synced lyrics first. Otherwise, or when Spotify has none, it looks them up on LRCLIB. Turn off **Spotify 가사 먼저 찾기** in the panel settings to use LRCLIB only.

**Spotify sign-in (unofficial, optional)**

Spotify has no public lyrics API. When you sign in, the app keeps the session cookie that Spotify's website sets on the phone. To get lyrics, it briefly opens Spotify's web player in a hidden view, reuses the access token that the player sends, and closes the player. The hidden player is blocked from registering as a Spotify Connect device, so it does not take over playback. The app never sees your password, and the token stays in memory.

- This is not an official Spotify feature. It may break the Spotify Terms of Use and may stop working whenever Spotify changes its web player.
- Anyone who can use your unlocked phone could use the stored session. Select the Spotify card again to sign out and delete the cookie.
- Signing in with Google may be blocked inside the app; signing in with email and password works.

**Privacy**

When signed in, requests go only to Spotify. When Spotify has no lyrics, or you are not signed in, track metadata is sent to LRCLIB. Settings and the Spotify session cookie are stored only on the phone. The wave is decorative and does not record or analyze audio.

**Build from source**

Install the Android SDK (API 36) and a JDK 17 or later, then run:

```bash
cd android
./gradlew assembleDebug
```

The APK is written to `android/app/build/outputs/apk/debug/app-debug.apk`.

### 한국어

안드로이드 앱은 휴대폰에서 Spotify를 들을 때 다른 앱 위에 가사 창을 띄웁니다. 현재 부르는 줄을 실시간으로 따라가며, 컴퓨터 버전과 같은 디자인을 사용합니다.

**테스트한 기기**

| | |
|---|---|
| 휴대폰 | Samsung Galaxy S25 Ultra (SM-S938N) |
| 운영체제 | Android 17 (API 37), One UI 9.0 |
| CPU | arm64-v8a |
| Spotify | 9.1.94 |

Android 8.0(API 26) 이상을 대상으로 합니다. 위 기기에서만 확인했습니다. 다른 휴대폰이나 Android 버전에서는 다르게 동작할 수 있습니다.

**설치 방법**

1. [Releases](https://github.com/taehyeon03/lyrics-float/releases)에서 `lyrics-float-android.apk`를 내려받습니다.
2. 휴대폰에서 파일을 열고, Android가 물으면 해당 앱의 설치를 허용합니다. 개발용 키로 서명한 디버그 빌드라서 Play 프로텍트 경고가 나올 수 있습니다.
3. **가사 플로팅** 앱을 엽니다.
4. **알림 접근**을 눌러 가사 플로팅을 켭니다. "제한된 설정"이라는 안내가 나오면 앱 정보 화면에서 ⋮ → **제한된 설정 허용**을 누른 뒤 다시 시도합니다.
5. **다른 앱 위에 표시**를 눌러 허용합니다.
6. 선택 사항: Spotify 가사를 쓰려면 **Spotify 로그인**을 누르고 Spotify 로그인 페이지에서 직접 로그인합니다. 먼저 아래 안내를 읽어 주세요.
7. Spotify에서 노래를 재생하고 **가사 창 띄우기**를 누릅니다.

**가사 창 사용법**

- 창의 아무 곳이나 끌어서 옮깁니다. 두 손가락으로 벌리거나 오므리면 두 손가락 사이를 중심으로 창과 가사 글자 크기가 함께 바뀝니다.
- 타임라인에서 재생 위치를 보고 바꿀 수 있습니다. 버튼으로 이전 곡, 재생·일시 정지, 다음 곡을 조작합니다. Spotify가 꺼져 있으면 재생 버튼으로 Spotify를 켜서 재생합니다.
- ⚙를 눌러 투명도, 글자 크기, 가사 색, 배경 색을 바꿉니다. 준비된 색을 고르거나 무지개 슬라이더로 원하는 색을 고를 수 있습니다. 물결, 타임라인, 재생 버튼, Spotify 가사도 각각 켜고 끌 수 있습니다.
- ×를 눌러 창을 닫습니다.

**가사를 찾는 방식**

Android의 미디어 세션에서 Spotify 곡의 제목, 가수, 앨범, 길이, 재생 위치를 읽습니다. 알림 접근 권한은 이 정보를 읽는 데만 쓰며, 다른 알림은 읽지 않습니다. Spotify에 로그인했다면 Spotify의 시간 정보가 있는 가사를 먼저 사용합니다. 로그인하지 않았거나 Spotify에 가사가 없으면 LRCLIB에서 찾습니다. 창 설정에서 **Spotify 가사 먼저 찾기**를 끄면 LRCLIB만 사용합니다.

**Spotify 로그인 (비공식, 선택 사항)**

Spotify는 가사를 위한 공개 API를 제공하지 않습니다. 로그인하면 Spotify 웹사이트가 만든 로그인 쿠키를 휴대폰에 보관합니다. 가사를 가져올 때 보이지 않는 화면에서 Spotify 웹 플레이어를 잠깐 열어 플레이어가 보내는 접근 토큰을 사용하고, 플레이어를 바로 닫습니다. 숨긴 플레이어가 Spotify Connect 기기로 등록되지 않게 막아서 재생을 가져가지 않습니다. 앱은 비밀번호를 보지 않으며 토큰은 메모리에만 둡니다.

- Spotify 공식 기능이 아닙니다. Spotify 이용약관에 어긋날 수 있고, Spotify가 웹 플레이어를 바꾸면 작동하지 않을 수 있습니다.
- 잠금이 풀린 휴대폰을 다른 사람이 쓰면 저장된 로그인으로 계정에 접근할 수 있습니다. Spotify 카드를 다시 누르면 로그아웃하고 쿠키를 지웁니다.
- 앱 안에서는 Google 로그인이 막힐 수 있습니다. 이메일과 비밀번호로 로그인하면 됩니다.

**개인정보**

로그인한 경우 요청은 Spotify로만 갑니다. Spotify에 가사가 없거나 로그인하지 않았으면 곡 정보가 LRCLIB로 전송됩니다. 설정과 Spotify 로그인 쿠키는 휴대폰에만 저장됩니다. 물결은 장식이며 오디오를 녹음하거나 분석하지 않습니다.

**직접 빌드하기**

Android SDK(API 36)와 JDK 17 이상을 설치한 뒤 실행합니다.

```bash
cd android
./gradlew assembleDebug
```

APK는 `android/app/build/outputs/apk/debug/app-debug.apk`에 만들어집니다.
