"""Optional local audio visualizer for PulseAudio and PipeWire's Pulse server."""
import math
import shutil
import struct
import subprocess
import threading
from PyQt6.QtCore import QObject, Qt, pyqtSignal, QRectF
from PyQt6.QtGui import QColor, QLinearGradient, QPainter, QPainterPath, QPen
from PyQt6.QtWidgets import QWidget

RATE = 16000
FRAME = 512
FREQUENCIES = (80, 110, 150, 205, 280, 380, 515, 700, 950, 1290, 1750, 2380, 3230, 4380, 5940)
COEFFICIENTS = tuple(2 * math.cos(2 * math.pi * f / RATE) for f in FREQUENCIES)

def spectrum(samples):
    """Measure frequency energy without writing audio data to disk."""
    if not samples: return [0.0] * len(FREQUENCIES)
    levels=[]
    for coefficient in COEFFICIENTS:
        prev=prev2=0.0
        for value in samples:
            current=value/32768 + coefficient*prev-prev2
            prev2,prev=prev,current
        energy=math.sqrt(max(0,prev*prev+prev2*prev2-coefficient*prev*prev2)) / len(samples)
        levels.append(min(1.0, math.sqrt(energy * 8)))
    return levels

class AudioWorker(QObject):
    levels=pyqtSignal(int,object)
    failed=pyqtSignal(int,str)
    def __init__(self):
        super().__init__();self.process=None;self.generation=0
    def start(self):
        self.stop();self.generation+=1;generation=self.generation
        def capture():
            try:
                if not shutil.which('pactl') or not shutil.which('parec'):
                    raise RuntimeError('음악 바를 사용하려면 pulseaudio-utils가 필요합니다.')
                sink=subprocess.run(['pactl','get-default-sink'],capture_output=True,text=True,timeout=4,check=True).stdout.strip()
                if not sink:raise RuntimeError('기본 오디오 출력을 찾지 못했습니다.')
                process=subprocess.Popen(['parec','--device='+sink+'.monitor','--format=s16le','--rate=16000','--channels=1','--latency-msec=60'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
                if generation!=self.generation:
                    process.terminate();process.wait(timeout=2);return
                self.process=process
                while generation==self.generation:
                    chunk=process.stdout.read(FRAME*2)
                    if len(chunk)!=FRAME*2: break
                    self.levels.emit(generation,spectrum(struct.unpack('<'+str(FRAME)+'h',chunk)))
                if generation==self.generation:raise RuntimeError('오디오 출력을 읽을 수 없습니다.')
            except (OSError,subprocess.SubprocessError,RuntimeError) as exc:
                if generation==self.generation:self.failed.emit(generation,str(exc))
            finally:
                if 'process' in locals() and process.poll() is None:
                    process.terminate()
                    try:process.wait(timeout=2)
                    except subprocess.TimeoutExpired:process.kill();process.wait()
        threading.Thread(target=capture,daemon=True).start()
    def stop(self):
        self.generation+=1
        process=self.process;self.process=None
        if process and process.poll() is None:process.terminate()

class MusicBars(QWidget):
    """Draw live audio as smooth waves above the played part of the timeline."""
    def __init__(self):
        super().__init__();self.values=[0.0]*len(FREQUENCIES);self.setFixedHeight(33)
        self.wave_enabled=False;self.timeline_enabled=False;self.progress=0.0
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
    def set_levels(self,levels):
        self.values=[old*.65+new*.35 for old,new in zip(self.values,levels)];self.update()
    def clear(self):
        self.values=[0.0]*len(FREQUENCIES);self.update()
    def set_progress(self,value):
        self.progress=max(0,min(1,value));self.update()
    def set_modes(self,wave,timeline):
        self.wave_enabled=wave;self.timeline_enabled=timeline;self.update()
    def paintEvent(self,event):
        painter=QPainter(self);painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        width=max(0,self.width()-8);start=4;baseline=self.height()-6;played=width*self.progress
        if self.wave_enabled:
            wave_width=played if self.timeline_enabled else width
            energy=sum(self.values)/len(self.values)
            if wave_width>1 and energy>0.001:
                for layer in range(3):
                    path=QPainterPath();path.moveTo(start,baseline)
                    amplitude=min(1.0,energy*(1.3+layer*.35))*(self.height()-9)
                    points=24
                    for i in range(1,points):
                        x=i/points;envelope=math.sin(math.pi*x)**1.1
                        variation=.55+.35*math.sin(x*math.pi*(2+layer)+layer*1.6)
                        height=amplitude*envelope*variation
                        path.lineTo(start+wave_width*x,baseline-height)
                    path.lineTo(start+wave_width,baseline);path.closeSubpath()
                    gradient=QLinearGradient(start,0,start+wave_width,0)
                    gradient.setColorAt(0,QColor(112,153,255,40+layer*18));gradient.setColorAt(.55,QColor(207,184,255,65+layer*20));gradient.setColorAt(1,QColor(255,232,249,100+layer*20))
                    painter.setPen(Qt.PenStyle.NoPen);painter.setBrush(gradient);painter.drawPath(path)
        if self.timeline_enabled:
            painter.setPen(QPen(QColor('#696671'),4,Qt.PenStyle.SolidLine,Qt.PenCapStyle.RoundCap));painter.drawLine(int(start),baseline,int(start+width),baseline)
            if played>0:
                painter.setPen(QPen(QColor('#d9d7f8'),4,Qt.PenStyle.SolidLine,Qt.PenCapStyle.RoundCap));painter.drawLine(int(start),baseline,int(start+played),baseline)
            painter.setPen(QPen(QColor('#ffffff'),2));painter.setBrush(QColor('#625578'));painter.drawEllipse(QRectF(start+played-4,baseline-4,8,8))
