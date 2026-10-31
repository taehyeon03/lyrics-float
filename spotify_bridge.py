"""Read lyric responses produced by the local Spotify client, without storing tokens."""
import asyncio
import base64
import json
import logging
import re
import threading
import time
import urllib.request
from PyQt6.QtCore import QObject, pyqtSignal
import websockets

DEBUG_URL='http://127.0.0.1:19222/json/list'
PATTERN=re.compile(r'^https://spclient\.wg\.spotify\.com/color-lyrics/v\d+/track/([A-Za-z0-9]+)(?:/|\?|$)')

def parse_response(body):
    data=json.loads(body);lyrics=data.get('lyrics') or {}
    if lyrics.get('syncType') not in ('LINE_SYNCED','SYLLABLE_SYNCED'):return []
    result=[]
    for line in lyrics.get('lines',[]):
        try:
            position=int(line['startTimeMs'])/1000
            words=str(line.get('words','')).strip()
            if position>=0:result.append((position,words))
        except (ValueError,TypeError,KeyError):continue
    return sorted(result)

# The view's internal data is used only while Spotify's lyrics page is open.
# Its structure is private and can change with a Spotify update.
VIEW_EXPRESSION = r"""(()=>{
  const element=document.querySelector('[data-testid="lyrics-line"]');
  const key=element&&Object.keys(element).find(k=>k.startsWith('__reactFiber'));
  let fiber=key&&element[key];
  for(let i=0;fiber&&i<25;i++,fiber=fiber.return){
    const p=fiber.memoizedProps||{}, d=p.data;
    if(typeof p.trackUri==='string' && p.trackUri.startsWith('spotify:track:') && d && d.isTimeSynced && Array.isArray(d.lyrics)){
      return JSON.stringify({track:p.trackUri.slice(14),lines:d.lyrics.map(l=>({start:l.start,text:l.text}))});
    }
  }
  return null;
})()"""

def parse_view(body):
    data=json.loads(body);track=data.get('track','')
    if not re.fullmatch(r'[A-Za-z0-9]+',track):return None,[]
    result=[]
    for line in data.get('lines',[]):
        try:
            position=float(line['start'])/1000
            text=line.get('text','')
            if position>=0 and isinstance(text,str):result.append((position,text.strip()))
        except (TypeError,ValueError,KeyError):continue
    return track,sorted(result)

class SpotifyBridge(QObject):
    lyrics=pyqtSignal(str,object)
    connected=pyqtSignal(bool)
    def __init__(self):
        super().__init__();self.stopped=threading.Event();self.thread=None
    def start(self):
        if self.thread and self.thread.is_alive():return
        self.stopped.clear();self.thread=threading.Thread(target=self.run,daemon=True);self.thread.start()
    def stop(self):self.stopped.set()
    def run(self):
        while not self.stopped.is_set():
            try:asyncio.run(self.listen())
            except (OSError,ValueError,KeyError,websockets.exceptions.WebSocketException,TimeoutError):pass
            finally:self.connected.emit(False)
            self.stopped.wait(5)
    async def listen(self):
        with urllib.request.urlopen(DEBUG_URL,timeout=2) as response:targets=json.load(response)
        target=next((x for x in targets if x.get('type')=='page' and x.get('url','').startswith('https://xpui.app.spotify.com/')),None)
        if not target:return
        socket=target['webSocketDebuggerUrl']
        if not socket.startswith('ws://127.0.0.1:19222/'):return
        async with websockets.connect(socket,open_timeout=3,max_size=8*1024*1024,proxy=None) as ws:
            await ws.send(json.dumps({'id':1,'method':'Network.enable'}));requests={};pending={};counter=2;next_view=0;last_view=None
            self.connected.emit(True);logging.info('Spotify lyric bridge connected')
            while not self.stopped.is_set():
                if time.monotonic()>=next_view:
                    pending[counter]=('view',None)
                    await ws.send(json.dumps({'id':counter,'method':'Runtime.evaluate','params':{'expression':VIEW_EXPRESSION,'returnByValue':True}}));counter+=1;next_view=time.monotonic()+2
                try:message=json.loads(await asyncio.wait_for(ws.recv(),timeout=1))
                except asyncio.TimeoutError:continue
                method=message.get('method');params=message.get('params',{})
                if method=='Network.responseReceived':
                    response=params.get('response',{});match=PATTERN.match(response.get('url',''))
                    if match and response.get('status')==200:requests[params['requestId']]=match.group(1)
                elif method=='Network.loadingFinished':
                    request_id=params.get('requestId');track=requests.pop(request_id,None)
                    if track:
                        pending[counter]=('network',track)
                        await ws.send(json.dumps({'id':counter,'method':'Network.getResponseBody','params':{'requestId':request_id}}));counter+=1
                elif message.get('id') in pending:
                    kind,track=pending.pop(message['id']);result=message.get('result',{})
                    try:
                        if kind=='view':
                            body=result.get('result',{}).get('value')
                            if not body:continue
                            track,lines=parse_view(body)
                            fingerprint=(track,len(lines),lines[0][0] if lines else None,lines[-1][0] if lines else None)
                            if not track or not lines or fingerprint==last_view:continue
                            last_view=fingerprint
                        else:
                            body=result['body'];body=base64.b64decode(body).decode('utf-8') if result.get('base64Encoded') else body
                            lines=parse_response(body)
                        if lines:self.lyrics.emit(track,lines)
                    except (ValueError,TypeError,KeyError,UnicodeError):pass
                elif method=='Network.loadingFailed':requests.pop(params.get('requestId'),None)
