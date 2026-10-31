"""Decorative waves for the playback timeline; no audio capture is used."""
import math
import time
from PyQt6.QtCore import Qt, QRectF, QTimer
from PyQt6.QtGui import QColor, QLinearGradient, QPainter, QPainterPath, QPen
from PyQt6.QtWidgets import QWidget

class MusicBars(QWidget):
    def __init__(self):
        super().__init__();self.setFixedHeight(33)
        self.wave_enabled=False;self.timeline_enabled=False;self.playing=False;self.progress=0.0;self.phase=0.0;self.last_tick=time.monotonic()
        self.setAttribute(Qt.WidgetAttribute.WA_TransparentForMouseEvents)
        self.timer=QTimer(self);self.timer.setInterval(40);self.timer.timeout.connect(self.tick)
    def tick(self):
        now=time.monotonic();self.phase+=(now-self.last_tick)*2.0;self.last_tick=now;self.update()
    def sync_timer(self):
        if self.wave_enabled and self.playing and self.isVisible():
            if not self.timer.isActive():self.last_tick=time.monotonic();self.timer.start()
        else:self.timer.stop()
    def showEvent(self,event):
        super().showEvent(event);self.sync_timer()
    def hideEvent(self,event):
        super().hideEvent(event);self.sync_timer()
    def set_playing(self,playing):
        self.playing=playing;self.sync_timer()
    def set_progress(self,value):
        self.progress=max(0,min(1,value));self.update()
    def set_modes(self,wave,timeline):
        self.wave_enabled=wave;self.timeline_enabled=timeline;self.sync_timer();self.update()
    def paintEvent(self,event):
        painter=QPainter(self);painter.setRenderHint(QPainter.RenderHint.Antialiasing)
        width=max(0,self.width()-8);start=4;baseline=self.height()-6;played=width*self.progress
        if self.wave_enabled:
            wave_width=played if self.timeline_enabled else width
            if wave_width>1:
                for layer in range(3):
                    path=QPainterPath();path.moveTo(start,baseline)
                    amplitude=(self.height()-9)*(.68+.22*math.sin(self.phase*.9+layer*.9))
                    for i in range(1,40):
                        x=i/40;envelope=math.sin(math.pi*x)**1.1
                        variation=.58+.4*math.sin(x*math.pi*(2+layer)+self.phase*(1 if layer!=1 else -1)+layer*1.6)
                        path.lineTo(start+wave_width*x,baseline-amplitude*envelope*variation)
                    path.lineTo(start+wave_width,baseline);path.closeSubpath()
                    gradient=QLinearGradient(start,0,start+wave_width,0)
                    gradient.setColorAt(0,QColor(112,153,255,40+layer*18));gradient.setColorAt(.55,QColor(207,184,255,65+layer*20));gradient.setColorAt(1,QColor(255,232,249,100+layer*20))
                    painter.setPen(Qt.PenStyle.NoPen);painter.setBrush(gradient);painter.drawPath(path)
        if self.timeline_enabled:
            painter.setPen(QPen(QColor('#696671'),4,Qt.PenStyle.SolidLine,Qt.PenCapStyle.RoundCap));painter.drawLine(int(start),baseline,int(start+width),baseline)
            if played>0:
                painter.setPen(QPen(QColor('#d9d7f8'),4,Qt.PenStyle.SolidLine,Qt.PenCapStyle.RoundCap));painter.drawLine(int(start),baseline,int(start+played),baseline)
            painter.setPen(QPen(QColor('#ffffff'),2));painter.setBrush(QColor('#625578'));painter.drawEllipse(QRectF(start+played-4,baseline-4,8,8))
