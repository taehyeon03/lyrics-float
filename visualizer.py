"""Optional local audio visualizer for PulseAudio and PipeWire's Pulse server."""
import math
import shutil
import struct
import subprocess
import threading
from PyQt6.QtCore import QObject, Qt, pyqtSignal
from PyQt6.QtGui import QColor, QLinearGradient, QPainter
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
    def __init__(self):
        super().__init__();self.values=[0.0]*len(FREQUENCIES);self.setFixedHeight(32)
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
    def set_levels(self,levels):
        self.values=[old*.55+new*.45 for old,new in zip(self.values,levels)];self.update()
    def clear(self):
        self.values=[0.0]*len(FREQUENCIES);self.update()
    def paintEvent(self,event):
        painter=QPainter(self);painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        gradient=QLinearGradient(0,0,self.width(),0)
        for pos,color in [(0,'#79b8ff'),(.5,'#bb8cff'),(1,'#ff74b5')]:gradient.setColorAt(pos,QColor(color))
        painter.setPen(Qt.PenStyle.NoPen);painter.setBrush(gradient)
        count=len(self.values);gap=3;bar=max(2,min(8,(self.width()-gap*(count-1))/count));total=count*bar+(count-1)*gap;left=(self.width()-total)/2
        from PyQt6.QtCore import QRectF
        for i,level in enumerate(self.values):
            height=3+level*(self.height()-5)
            painter.drawRoundedRect(QRectF(left+i*(bar+gap),self.height()-height,bar,height),bar/2,bar/2)
