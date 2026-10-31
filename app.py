import asyncio, bisect, json, re, sys, time, urllib.parse, urllib.request, urllib.error, difflib
from pathlib import Path
from PyQt6.QtCore import Qt, QTimer, QObject, pyqtSignal
from PyQt6.QtGui import QColor, QFont
from PyQt6.QtWidgets import QApplication, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QSlider, QCheckBox, QFrame, QSizeGrip, QColorDialog, QFontDialog
from dbus_fast.aio import MessageBus
from dbus_fast import Message, MessageType

CONFIG = Path.home()/'.config'/'lyrics-float'/'settings.json'

def parse_lrc(text):
    lines=[]
    for line in (text or '').splitlines():
        stamps=list(re.finditer(r'\[(\d+):(\d+(?:\.\d+)?)\]',line))
        lyric=re.sub(r'\[\d+:\d+(?:\.\d+)?\]', '',line).strip()
        for stamp in stamps:
            lines.append((int(stamp.group(1))*60+float(stamp.group(2)),lyric))
    return sorted(lines)

async def read_player():
    bus=await MessageBus().connect()
    try:
        names=await bus.call(Message(destination='org.freedesktop.DBus',path='/org/freedesktop/DBus',interface='org.freedesktop.DBus',member='ListNames'))
        services=[n for n in names.body[0] if n.startswith('org.mpris.MediaPlayer2.spotify')]
        if not services: return None
        reply=await bus.call(Message(destination=services[0],path='/org/mpris/MediaPlayer2',interface='org.freedesktop.DBus.Properties',member='GetAll',signature='s',body=['org.mpris.MediaPlayer2.Player']))
        if reply.message_type==MessageType.ERROR: raise RuntimeError(reply.error_name)
        d={k:v.value for k,v in reply.body[0].items()}; m={k:v.value for k,v in d.get('Metadata',{}).items()}
        return {'id':str(m.get('mpris:trackid','')), 'title':m.get('xesam:title',''), 'artist':', '.join(m.get('xesam:artist',[])), 'album':m.get('xesam:album',''), 'duration':m.get('mpris:length',0)/1000000, 'position':d.get('Position',0)/1000000, 'playing':d.get('PlaybackStatus')=='Playing'}
    finally: bus.disconnect()

def get_lyrics(track):
    headers={'User-Agent':'LyricsFloat/1.0 (desktop personal lyrics viewer)'}
    def request(endpoint, params):
        req=urllib.request.Request('https://lrclib.net/api/'+endpoint+'?'+urllib.parse.urlencode(params),headers=headers)
        with urllib.request.urlopen(req,timeout=12) as response: return json.load(response)
    params={'track_name':track['title'],'artist_name':track['artist'],'album_name':track['album'],'duration':round(track['duration'])}
    try:
        data=request('get',params)
    except urllib.error.HTTPError as error:
        if error.code != 404: raise
        candidates=request('search',{'track_name':track['title'],'artist_name':track['artist'].split(',')[0]})
        def normalized(value): return re.sub(r'[^\w]+','',value.casefold())
        def score(item):
            title=difflib.SequenceMatcher(None,normalized(track['title']),normalized(item.get('trackName',''))).ratio()
            artist=difflib.SequenceMatcher(None,normalized(track['artist'].split(',')[0]),normalized(item.get('artistName',''))).ratio()
            duration=abs(track['duration']-float(item.get('duration') or 0))
            return (title,artist,duration)
        matches=[item for item in candidates if score(item)[0]>=.80 and score(item)[1]>=.65 and (not track['duration'] or score(item)[2]<=10)]
        if not matches: return None,None,False
        data=max(matches,key=lambda item:(bool(item.get('syncedLyrics')), score(item)[0]+score(item)[1],-score(item)[2]))
    return data.get('syncedLyrics'),data.get('plainLyrics'),data.get('instrumental')

class Bridge(QObject):
    player=pyqtSignal(object)
    lyrics=pyqtSignal(str,object,object,object)
    error=pyqtSignal(str)

class Window(QWidget):
    def __init__(self):
        super().__init__(); self.setWindowTitle('가사 플로팅'); self.resize(560,230); self.setMinimumSize(320,180)
        self.setWindowFlags(Qt.WindowType.Window | Qt.WindowType.FramelessWindowHint | Qt.WindowType.WindowStaysOnTopHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.track=None; self.lines=[]; self.base=0; self.at=time.monotonic(); self.playing=False; self.busy=False; self.plain=None; self.bridge=Bridge(); self.fontsize=23; self.lyric_color="#ff75b4"; self.background_color="#1c1d21"; self.font_family="sans-serif"
        self.bridge.player.connect(self.player_done); self.bridge.lyrics.connect(self.lyrics_done); self.bridge.error.connect(self.player_error)
        self.setStyleSheet("""QLabel {background: transparent; color:#f4f2f4; font-family:sans-serif}
            QLabel#title {color:#b6b1b7;font-size:11px} QLabel#previous, QLabel#next {color:#99949b}
            QLabel#lyric {color:#ff75b4}
            QPushButton {background:rgba(255,255,255,28);color:#eae6e9;border:0;border-radius:12px;padding:4px 10px}
            QPushButton:hover {background:rgba(255,255,255,55)} QCheckBox {color:#e5dce2;background:transparent}
        """)
        outer=QVBoxLayout(self);outer.setContentsMargins(0,0,0,0)
        self.panel=QFrame();self.panel.setObjectName('panel');outer.addWidget(self.panel)
        layout=QVBoxLayout(self.panel);layout.setContentsMargins(20,12,20,10);layout.setSpacing(7)
        top=QHBoxLayout();self.title=QLabel('Spotify를 기다리는 중');self.title.setObjectName('title');top.addWidget(self.title,1)
        self.pin=QCheckBox('고정');self.pin.setChecked(True);self.pin.toggled.connect(self.pin_change);top.addWidget(self.pin)
        self.settings_button=QPushButton('⚙');self.settings_button.setToolTip('설정');top.addWidget(self.settings_button)
        close=QPushButton('×');close.setToolTip('닫기');close.clicked.connect(self.close);top.addWidget(close);layout.addLayout(top)
        layout.addStretch(); self.previous=QLabel('');self.previous.setObjectName('previous');self.previous.setAlignment(Qt.AlignmentFlag.AlignCenter);self.previous.setWordWrap(True);layout.addWidget(self.previous)
        self.current=QLabel('음악을 기다리는 중');self.current.setObjectName('lyric');self.current.setWordWrap(True);self.current.setAlignment(Qt.AlignmentFlag.AlignCenter);layout.addWidget(self.current)
        self.next=QLabel('');self.next.setObjectName('next');self.next.setWordWrap(True);self.next.setAlignment(Qt.AlignmentFlag.AlignCenter);layout.addWidget(self.next);layout.addStretch()
        self.options=QWidget();self.options.setStyleSheet('background:transparent');bottom=QHBoxLayout(self.options);bottom.setContentsMargins(0,0,0,0)
        bottom.addWidget(QLabel('배경'));self.opacity=QSlider(Qt.Orientation.Horizontal);self.opacity.setRange(20,100);self.opacity.setValue(85);self.opacity.valueChanged.connect(self.update_background);bottom.addWidget(self.opacity,1)
        self.color_button=QPushButton('글자 색');self.color_button.clicked.connect(self.choose_color);bottom.addWidget(self.color_button)
        self.bg_button=QPushButton('배경 색');self.bg_button.clicked.connect(self.choose_background);bottom.addWidget(self.bg_button)
        self.font_button=QPushButton('글꼴');self.font_button.clicked.connect(self.choose_font);bottom.addWidget(self.font_button)
        self.smaller=QPushButton('가 −');self.larger=QPushButton('가 +');self.smaller.clicked.connect(lambda:self.font_adjust(-2));self.larger.clicked.connect(lambda:self.font_adjust(2));bottom.addWidget(self.smaller);bottom.addWidget(self.larger);layout.addWidget(self.options)
        self.options.hide();self.settings_button.clicked.connect(lambda:self.options.setVisible(not self.options.isVisible()))
        griprow=QHBoxLayout();griprow.addStretch();grip=QSizeGrip(self);grip.setStyleSheet('background:transparent;width:14px;height:14px');griprow.addWidget(grip);layout.addLayout(griprow)
        self.load_settings();self.apply_typography();self.update_background(self.opacity.value());self.pin_change(self.pin.isChecked())
        self.timer=QTimer(self); self.timer.timeout.connect(self.render_lyrics); self.timer.start(100)
        self.poll=QTimer(self); self.poll.timeout.connect(self.poll_player); self.poll.start(2000); self.poll_player()
    def update_background(self,n):
        color=QColor(self.background_color)
        self.panel.setStyleSheet(f"QFrame#panel {{background:rgba({color.red()}, {color.green()}, {color.blue()}, {round(n*2.55)});border-radius:20px;}}")
    def mousePressEvent(self,event):
        if event.button()==Qt.MouseButton.LeftButton:
            self.windowHandle().startSystemMove()
    def apply_typography(self):
        for label in (self.previous, self.current, self.next):
            font=QFont(self.font_family)
            font.setPixelSize(self.fontsize if label is self.current else max(10,round(self.fontsize*0.65)))
            font.setBold(label is self.current)
            label.setFont(font)
        self.current.setStyleSheet(f'color:{self.lyric_color}; background:transparent')
    def choose_color(self):
        color=QColorDialog.getColor(QColor(self.lyric_color),self,'현재 가사의 색')
        if color.isValid():
            self.lyric_color=color.name();self.apply_typography();self.save_settings()
    def choose_background(self):
        color=QColorDialog.getColor(QColor(self.background_color),self,'배경 색')
        if color.isValid():
            self.background_color=color.name();self.update_background(self.opacity.value());self.save_settings()
    def choose_font(self):
        font,ok=QFontDialog.getFont(QFont(self.font_family),self,'가사 글꼴')
        if ok:
            self.font_family=font.family();self.apply_typography();self.save_settings()
    def font_adjust(self,n):
        self.fontsize=max(12,min(60,self.fontsize+n));self.apply_typography()
        if n:self.save_settings()
    def pin_change(self,on):
        self.setWindowFlag(Qt.WindowType.WindowStaysOnTopHint,on); self.show()
    def poll_player(self):
        if self.busy: return
        import threading
        self.busy=True
        def worker():
            try: self.bridge.player.emit(asyncio.run(read_player()))
            except Exception as e: self.bridge.error.emit(str(e))
        threading.Thread(target=worker,daemon=True).start()
    def player_error(self,e): self.busy=False; self.title.setText('Spotify 연결 오류: '+e[:100])
    def player_done(self,t):
        self.busy=False
        if not t or not t['title']:
            self.track=None; self.playing=False; self.lines=[]; self.current.setText('음악을 기다리는 중');self.previous.setText('');self.next.setText('');self.title.setText('Spotify 데스크톱 앱에서 음악을 재생하세요');return
        changed=not self.track or (t['id'],t['title'],t['artist']) != (self.track['id'],self.track['title'],self.track['artist'])
        self.track=t; self.base=t['position'];self.at=time.monotonic();self.playing=t['playing']; self.title.setText(t['title']+'  ·  '+t['artist']+('  ⏸' if not self.playing else ''))
        if changed:
            self.lines=[];self.plain=None;self.current.setText('가사를 찾고 있어요…');self.previous.setText('');self.next.setText('')
            import threading
            def fetch():
                try:
                    synced,plain,instrumental=get_lyrics(t);self.bridge.lyrics.emit(t['id'],synced,plain,instrumental)
                except Exception: self.bridge.lyrics.emit(t['id'],None,None,False)
            threading.Thread(target=fetch,daemon=True).start()
    def lyrics_done(self,track_id,synced,plain,instrumental):
        if not self.track or self.track['id']!=track_id:return
        self.lines=parse_lrc(synced);self.plain=plain
        if not self.lines:
            self.current.setText('연주곡입니다' if instrumental else ('동기화된 가사가 없습니다' if plain else '가사를 찾지 못했습니다'))
            self.next.setText('가사 출처: LRCLIB')
    def render_lyrics(self):
        if not self.lines:return
        pos=self.base+(time.monotonic()-self.at if self.playing else 0)
        i=bisect.bisect_right([x[0] for x in self.lines],pos)-1
        self.previous.setText(self.lines[i-1][1] if i>0 else '')
        self.current.setText(self.lines[i][1] if i>=0 and self.lines[i][1] else '♪')
        self.next.setText(self.lines[i+1][1] if i+1<len(self.lines) else '')
    def load_settings(self):
        try:
            x=json.loads(CONFIG.read_text());self.resize(x.get('width',540),x.get('height',240));self.opacity.setValue(x.get('opacity',90));self.pin.setChecked(x.get('pin',True));self.fontsize=x.get('font',23);self.lyric_color=x.get('lyric_color','#ff75b4');self.background_color=x.get('background_color','#1c1d21');self.font_family=x.get('font_family','sans-serif');self.font_adjust(0)
        except (OSError,ValueError,TypeError):pass
    def save_settings(self):
        CONFIG.parent.mkdir(parents=True,exist_ok=True);CONFIG.write_text(json.dumps({'width':self.width(),'height':self.height(),'opacity':self.opacity.value(),'pin':self.pin.isChecked(),'font':self.fontsize,'lyric_color':self.lyric_color,'background_color':self.background_color,'font_family':self.font_family},ensure_ascii=False))
    def closeEvent(self,event):
        self.save_settings();super().closeEvent(event)

if __name__=='__main__':
    app=QApplication(sys.argv); w=Window(); w.show();sys.exit(app.exec())
