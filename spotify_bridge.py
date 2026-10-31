"""Read lyric responses produced by the local Spotify client, without storing tokens."""
import asyncio
import base64
import json
import logging
import re
import threading
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
            await ws.send(json.dumps({'id':1,'method':'Network.enable'}));requests={};pending={};counter=2
            self.connected.emit(True);logging.info('Spotify lyric bridge connected')
            while not self.stopped.is_set():
                try:message=json.loads(await asyncio.wait_for(ws.recv(),timeout=2))
                except asyncio.TimeoutError:continue
                method=message.get('method');params=message.get('params',{})
                if method=='Network.responseReceived':
                    response=params.get('response',{});match=PATTERN.match(response.get('url',''))
                    if match and response.get('status')==200:requests[params['requestId']]=match.group(1)
                elif method=='Network.loadingFinished':
                    request_id=params.get('requestId');track=requests.pop(request_id,None)
                    if track:
                        pending[counter]=track
                        await ws.send(json.dumps({'id':counter,'method':'Network.getResponseBody','params':{'requestId':request_id}}));counter+=1
                elif message.get('id') in pending:
                    track=pending.pop(message['id']);result=message.get('result',{})
                    try:
                        body=result['body'];body=base64.b64decode(body).decode('utf-8') if result.get('base64Encoded') else body
                        lines=parse_response(body)
                        if lines:self.lyrics.emit(track,lines)
                    except (ValueError,TypeError,KeyError,UnicodeError):pass
                elif method=='Network.loadingFailed':requests.pop(params.get('requestId'),None)
