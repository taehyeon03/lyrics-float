import asyncio, bisect, json, re, sys, time, urllib.parse, urllib.request, urllib.error, difflib
from pathlib import Path
from PyQt6.QtCore import Qt, QTimer, QObject, pyqtSignal, QEvent
from PyQt6.QtGui import QColor, QFont
from PyQt6.QtWidgets import QApplication, QWidget, QVBoxLayout, QHBoxLayout, QLabel, QPushButton, QSlider, QCheckBox, QFrame, QSizeGrip, QColorDialog, QFontDialog, QSizePolicy, QGridLayout, QDialog, QProgressBar, QScrollArea
from dbus_fast.aio import MessageBus
from dbus_fast import Message, MessageType
from visualizer import AudioWorker, MusicBars

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
        return {'id':str(m.get('mpris:trackid','')), 'title':m.get('xesam:title',''), 'artist':', '.join(m.get('xesam:artist',[])), 'album':m.get('xesam:album',''), 'duration':m.get('mpris:length',0)/1000000, 'position':d.get('Position',0)/1000000, 'playing':d.get('PlaybackStatus')=='Playing', 'can_next':d.get('CanGoNext',False)}
    finally: bus.disconnect()

async def next_track():
    bus=await MessageBus().connect()
    try:
        names=await bus.call(Message(destination='org.freedesktop.DBus',path='/org/freedesktop/DBus',interface='org.freedesktop.DBus',member='ListNames'))
        services=[n for n in names.body[0] if n.startswith('org.mpris.MediaPlayer2.spotify')]
        if not services: raise RuntimeError('Spotify에 연결할 수 없습니다')
        reply=await bus.call(Message(destination=services[0],path='/org/mpris/MediaPlayer2',interface='org.mpris.MediaPlayer2.Player',member='Next'))
        if reply.message_type==MessageType.ERROR:raise RuntimeError('Spotify에서 다음 곡을 재생할 수 없습니다')
    finally:bus.disconnect()

def format_time(seconds):
    seconds=max(0,int(seconds))
    return f'{seconds//60}:{seconds%60:02d}'

def get_lyrics(track):
    headers={'User-Agent':'LyricsFloat/1.0 (desktop personal lyrics viewer)'}
    def request(endpoint, params):
        req=urllib.request.Request('https://lrclib.net/api/'+endpoint+'?'+urllib.parse.urlencode(params),headers=headers)
        with urllib.request.urlopen(req,timeout=12) as response: return json.load(response)
    params={'track_name':track['title'],'artist_name':track['artist'],'album_name':track['album'],'duration':round(track['duration'])}
    try:
        data=request('get',params)
    except urllib.error.HTTPError as error:
        if error.code not in (404, 429, 500, 502, 503, 504): raise
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
    command_error=pyqtSignal(str)

class Window(QWidget):
    def __init__(self):
        super().__init__(); self.setWindowTitle('가사 플로팅'); self.resize(560,230); self.setMinimumSize(240,110)
        self.setWindowFlags(Qt.WindowType.Window | Qt.WindowType.FramelessWindowHint | Qt.WindowType.WindowStaysOnTopHint)
        self.setAttribute(Qt.WidgetAttribute.WA_TranslucentBackground)
        self.loading_settings=True;self.track=None; self.lines=[]; self.base=0; self.at=time.monotonic(); self.playing=False; self.busy=False; self.plain=None; self.bridge=Bridge(); self.fontsize=23; self.lyric_color="#ff75b4"; self.background_color="#1c1d21"; self.font_family="sans-serif"
        self.bridge.player.connect(self.player_done); self.bridge.lyrics.connect(self.lyrics_done); self.bridge.error.connect(self.player_error); self.bridge.command_error.connect(self.player_error)
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
        self.settings_scroll=QScrollArea();self.settings_scroll.setWidgetResizable(True);self.settings_scroll.setFrameShape(QFrame.Shape.NoFrame)
        self.settings_scroll.setStyleSheet("QScrollArea {background:transparent;border:0} QWidget#settingsContent {background:transparent} QLabel,QCheckBox {color:#f5f4f7;background:transparent} QPushButton {background:#45434d;color:#fff;padding:8px;border-radius:6px}")
        layout.addWidget(self.settings_scroll,1);self.settings_scroll.hide();scroll=self.settings_scroll
        self.options=QWidget();self.options.setObjectName('settingsContent');scroll.setWidget(self.options);bottom=QVBoxLayout(self.options);bottom.setSpacing(10)
        bottom.addWidget(QLabel('배경 투명도'))
        self.opacity=QSlider(Qt.Orientation.Horizontal);self.opacity.setRange(20,100);self.opacity.setValue(85);self.opacity.valueChanged.connect(self.update_background);bottom.addWidget(self.opacity)
        self.color_button=QPushButton('현재 가사 색');self.color_button.clicked.connect(self.choose_color);bottom.addWidget(self.color_button)
        self.bg_button=QPushButton('배경 색');self.bg_button.clicked.connect(self.choose_background);bottom.addWidget(self.bg_button)
        self.font_button=QPushButton('글꼴');self.font_button.clicked.connect(self.choose_font);bottom.addWidget(self.font_button)
        fontrow=QHBoxLayout();self.smaller=QPushButton('글자 작게 −');self.larger=QPushButton('글자 크게 +');self.smaller.clicked.connect(lambda:self.font_adjust(-2));self.larger.clicked.connect(lambda:self.font_adjust(2));fontrow.addWidget(self.smaller);fontrow.addWidget(self.larger);bottom.addLayout(fontrow)
        self.bars_check=QCheckBox('소리에 반응하는 음악 바');self.bars_check.setToolTip('컴퓨터의 기본 출력 소리에 반응합니다');bottom.addWidget(self.bars_check)
        self.timeline_check=QCheckBox('노래 타임라인 표시');bottom.addWidget(self.timeline_check)
        self.next_check=QCheckBox('다음 곡 버튼 표시');bottom.addWidget(self.next_check)
        self.bars_status=QLabel('');self.bars_status.setWordWrap(True);bottom.addWidget(self.bars_status);bottom.addStretch()
        done=QPushButton('설정 닫기');done.clicked.connect(self.open_settings);bottom.addWidget(done)
        self.settings_button.clicked.connect(self.open_settings)
        self.bars=MusicBars();self.bars.hide();layout.addWidget(self.bars)
        self.audio=AudioWorker();self.audio.levels.connect(self.audio_levels);self.audio.failed.connect(self.audio_failed)
        self.bars_check.toggled.connect(self.toggle_bars)
        self.timeline=QWidget(); timeline_layout=QHBoxLayout(self.timeline);timeline_layout.setContentsMargins(0,0,0,0);timeline_layout.setSpacing(7)
        self.elapsed=QLabel('0:00');self.elapsed.setStyleSheet('font-size:10px;color:#bcb6c0');timeline_layout.addWidget(self.elapsed)
        self.progress=QProgressBar();self.progress.setRange(0,1000);self.progress.setValue(0);self.progress.setTextVisible(False);self.progress.setFixedHeight(5)
        self.progress.setStyleSheet('QProgressBar {background:#53515a;border:0;border-radius:2px} QProgressBar::chunk {background:#fb80b8;border-radius:2px}')
        timeline_layout.addWidget(self.progress,1);self.duration=QLabel('0:00');self.duration.setStyleSheet('font-size:10px;color:#bcb6c0');timeline_layout.addWidget(self.duration);layout.addWidget(self.timeline);self.timeline.hide()
        self.skip_button=QPushButton('다음 곡  ⏭');self.skip_button.setToolTip('Spotify에서 다음 곡 재생');self.skip_button.clicked.connect(self.skip_next);top.insertWidget(1,self.skip_button);self.skip_button.hide()
        self.timeline_check.toggled.connect(self.toggle_timeline);self.next_check.toggled.connect(self.toggle_next)
        griprow=QHBoxLayout();griprow.addStretch();grip=QSizeGrip(self);grip.setStyleSheet('background:transparent;width:14px;height:14px');griprow.addWidget(grip);layout.addLayout(griprow)
        for label in (self.title,self.previous,self.current,self.next):
            label.setSizePolicy(QSizePolicy.Policy.Ignored,QSizePolicy.Policy.Ignored if label is not self.title else QSizePolicy.Policy.Preferred)
        self.setMouseTracking(True)
        for widget in (self.panel,self.title,self.previous,self.current,self.next):
            widget.setMouseTracking(True);widget.installEventFilter(self)
        self.save_timer=QTimer(self);self.save_timer.setSingleShot(True);self.save_timer.setInterval(800);self.save_timer.timeout.connect(self.save_settings)
        self.load_settings();self.loading_settings=False;self.apply_typography();self.update_background(self.opacity.value());self.pin_change(self.pin.isChecked())
        self.timer=QTimer(self); self.timer.timeout.connect(self.render_lyrics); self.timer.start(100)
        self.poll=QTimer(self); self.poll.timeout.connect(self.poll_player); self.poll.start(2000); self.poll_player()
    def open_settings(self):
        showing=not self.settings_scroll.isVisible()
        self.settings_scroll.setVisible(showing)
        self.current.setVisible(not showing)
        self.previous.setVisible(not showing and self.height()>=170)
        self.next.setVisible(not showing and self.height()>=145)
        self.bars.setVisible(not showing and self.bars_check.isChecked())
        self.timeline.setVisible(not showing and self.timeline_check.isChecked())
        if showing:self.settings_scroll.verticalScrollBar().setValue(0)
    def toggle_timeline(self,on):
        self.timeline.setVisible(on and not self.settings_scroll.isVisible())
        if hasattr(self,'save_timer') and not self.loading_settings:self.save_settings()
    def toggle_next(self,on):
        self.skip_button.setVisible(on)
        if hasattr(self,'save_timer') and not self.loading_settings:self.save_settings()
    def skip_next(self):
        import threading
        def worker():
            try:asyncio.run(next_track())
            except Exception as exc:self.bridge.command_error.emit(str(exc))
        threading.Thread(target=worker,daemon=True).start()
    def update_background(self,n):
        color=QColor(self.background_color)
        self.panel.setStyleSheet(f"QFrame#panel {{background:rgba({color.red()}, {color.green()}, {color.blue()}, {round(n*2.55)});border-radius:20px;}}")
    def toggle_bars(self,on):
        self.bars.setVisible(on and not self.settings_scroll.isVisible());self.bars_status.setText('')
        if on:self.audio.start()
        else:self.audio.stop();self.bars.clear()
        if hasattr(self,'save_timer') and not self.loading_settings:self.save_settings()
    def audio_levels(self,generation,levels):
        if generation==self.audio.generation and self.bars_check.isChecked():self.bars.set_levels(levels)
    def audio_failed(self,generation,message):
        if generation==self.audio.generation:
            self.bars_status.setText(message);self.bars.clear()
    def resize_edges(self, pos):
        margin=10; edges=Qt.Edge(0)
        if pos.x()<margin: edges|=Qt.Edge.LeftEdge
        elif pos.x()>=self.width()-margin: edges|=Qt.Edge.RightEdge
        if pos.y()<margin: edges|=Qt.Edge.TopEdge
        elif pos.y()>=self.height()-margin: edges|=Qt.Edge.BottomEdge
        return edges
    def update_resize_cursor(self,widget,pos):
        edges=self.resize_edges(pos)
        horizontal=bool(edges & (Qt.Edge.LeftEdge | Qt.Edge.RightEdge))
        vertical=bool(edges & (Qt.Edge.TopEdge | Qt.Edge.BottomEdge))
        if horizontal and vertical:
            diagonal=(bool(edges & Qt.Edge.LeftEdge) == bool(edges & Qt.Edge.TopEdge))
            cursor=Qt.CursorShape.SizeFDiagCursor if diagonal else Qt.CursorShape.SizeBDiagCursor
        elif horizontal: cursor=Qt.CursorShape.SizeHorCursor
        elif vertical: cursor=Qt.CursorShape.SizeVerCursor
        else:
            widget.unsetCursor();return
        widget.setCursor(cursor)
    def eventFilter(self,watched,event):
        if event.type()==QEvent.Type.MouseMove:
            self.update_resize_cursor(watched,watched.mapTo(self,event.position().toPoint()))
        elif event.type()==QEvent.Type.Leave:
            watched.unsetCursor()
        elif event.type()==QEvent.Type.MouseButtonPress and event.button()==Qt.MouseButton.LeftButton:
            pos=watched.mapTo(self,event.position().toPoint())
            edges=self.resize_edges(pos)
            if edges and self.windowHandle():
                self.windowHandle().startSystemResize(edges)
                return True
        return super().eventFilter(watched,event)
    def mouseMoveEvent(self,event):
        self.update_resize_cursor(self,event.position().toPoint())
    def mousePressEvent(self,event):
        if event.button()==Qt.MouseButton.LeftButton:
            edges=self.resize_edges(event.position().toPoint())
            if edges:self.windowHandle().startSystemResize(edges)
            else:self.windowHandle().startSystemMove()
    def resizeEvent(self,event):
        super().resizeEvent(event)
        if hasattr(self,'previous'):
            self.previous.setVisible(not self.settings_scroll.isVisible() and self.height()>=170)
            self.next.setVisible(not self.settings_scroll.isVisible() and self.height()>=145)
        if hasattr(self,'save_timer'): self.save_timer.start()
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
        self.skip_button.setEnabled(t.get('can_next',False));self.track=t; self.base=t['position'];self.at=time.monotonic();self.playing=t['playing']; self.title.setText(t['title']+'  ·  '+t['artist']+('  ⏸' if not self.playing else ''))
        if changed:
            self.lines=[];self.plain=None;self.current.setText('가사를 찾고 있어요…');self.previous.setText('');self.next.setText('')
            self.fetch_lyrics(t)
    def fetch_lyrics(self,t):
        import threading
        def fetch():
            try:
                synced,plain,instrumental=get_lyrics(t);self.bridge.lyrics.emit(t['id'],synced,plain,instrumental)
            except Exception:
                self.bridge.lyrics.emit(t['id'],None,None,'temporary_error')
        threading.Thread(target=fetch,daemon=True).start()
    def lyrics_done(self,track_id,synced,plain,instrumental):
        if not self.track or self.track['id']!=track_id:return
        self.lines=parse_lrc(synced);self.plain=plain
        if instrumental == 'temporary_error':
            self.current.setText('가사 서비스에 연결할 수 없습니다')
            self.next.setText('잠시 후 다시 시도합니다')
            QTimer.singleShot(15000,lambda:self.retry_lyrics(track_id))
        elif not self.lines:
            self.current.setText('연주곡입니다' if instrumental else ('동기화된 가사가 없습니다' if plain else '가사를 찾지 못했습니다'))
            self.next.setText('가사 출처: LRCLIB')
    def retry_lyrics(self,track_id):
        if self.track and self.track['id']==track_id and not self.lines:
            self.current.setText('가사를 다시 찾고 있어요…')
            self.fetch_lyrics(self.track.copy())
    def render_lyrics(self):
        pos=self.base+(time.monotonic()-self.at if self.playing else 0)
        duration=self.track.get('duration',0) if self.track else 0
        pos=max(0,min(pos,duration)) if duration else 0
        self.elapsed.setText(format_time(pos));self.duration.setText(format_time(duration));self.progress.setValue(round(1000*pos/duration) if duration else 0)
        if not self.lines:return
        i=bisect.bisect_right([x[0] for x in self.lines],pos)-1
        self.previous.setText(self.lines[i-1][1] if i>0 else '')
        self.current.setText(self.lines[i][1] if i>=0 and self.lines[i][1] else '♪')
        self.next.setText(self.lines[i+1][1] if i+1<len(self.lines) else '')
    def load_settings(self):
        try:
            x=json.loads(CONFIG.read_text());self.resize(x.get('width',540),x.get('height',240));self.opacity.setValue(x.get('opacity',90));self.pin.setChecked(x.get('pin',True));self.bars_check.setChecked(bool(x.get('music_bars',False)));self.timeline_check.setChecked(bool(x.get('timeline',False)));self.next_check.setChecked(bool(x.get('next_button',False)));self.fontsize=x.get('font',23);self.lyric_color=x.get('lyric_color','#ff75b4');self.background_color=x.get('background_color','#1c1d21');self.font_family=x.get('font_family','sans-serif');self.font_adjust(0)
        except (OSError,ValueError,TypeError):pass
    def save_settings(self):
        CONFIG.parent.mkdir(parents=True,exist_ok=True);CONFIG.write_text(json.dumps({'width':self.width(),'height':self.height(),'opacity':self.opacity.value(),'pin':self.pin.isChecked(),'font':self.fontsize,'lyric_color':self.lyric_color,'background_color':self.background_color,'music_bars':self.bars_check.isChecked(),'timeline':self.timeline_check.isChecked(),'next_button':self.next_check.isChecked(),'font_family':self.font_family},ensure_ascii=False))
    def closeEvent(self,event):
        self.audio.stop();self.save_settings();super().closeEvent(event)

if __name__=='__main__':
    app=QApplication(sys.argv); w=Window(); w.show();sys.exit(app.exec())
