package com.lyricsfloat.android;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.*;
import android.media.session.MediaController;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public final class OverlayService extends Service {
    static final String ACTION_REFRESH="com.lyricsfloat.android.REFRESH";
    static final int[] LYRIC_COLORS={0xffff75b4,0xfff4f2f4,0xff8fceff,0xffc7a6ff,0xffffd989};
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private WindowManager windows;private WindowManager.LayoutParams params;private FrameLayout root;private LinearLayout panel,lyricsBox,timelineRow,controls,swatches;private ScrollView settingsBox;
    private TextView title,previous,current,next,elapsed,total,fontValue;private IconButton play;private WaveView wave;private SharedPreferences prefs;
    private MediaController controller;private boolean lookupInFlight=false;private String trackKey="",source="",message="Spotify에서 음악을 재생하세요";private long duration=0,retryAt=0;private int generation=0;private List<Line> lines=Collections.emptyList();
    private final BroadcastReceiver refresh=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){applyAppearance();}};
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(){super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("overlay","가사 창",NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"overlay").setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("가사 플로팅 실행 중").setContentText("눌러서 설정을 엽니다").setContentIntent(pi).setOngoing(true).build();startForeground(101,notification);
        if(!Settings.canDrawOverlays(this)){stopSelf();return;}
        prefs=getSharedPreferences("display",MODE_PRIVATE);windows=(WindowManager)getSystemService(WINDOW_SERVICE);buildPanel();IntentFilter filter=new IntentFilter(ACTION_REFRESH);
        if(Build.VERSION.SDK_INT>=33)registerReceiver(refresh,filter,Context.RECEIVER_NOT_EXPORTED);else registerReceiver(refresh,filter);main.post(tick);
    }
    private TextView label(String value,int size,int color){TextView v=new TextView(this);v.setText(value);v.setTextColor(color);v.setTextSize(size);v.setGravity(Gravity.CENTER);v.setIncludeFontPadding(false);return v;}
    private LinearLayout.LayoutParams lp(int w,int h,int top){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(w,h);p.topMargin=dp(top);return p;}
    private IconButton icon(LinearLayout row,int glyph,int size,int gap,View.OnClickListener click){IconButton b=new IconButton(this,glyph);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(size),dp(size));p.leftMargin=dp(gap);row.addView(b,p);b.setOnClickListener(click);return b;}
    private void refreshAll(){applyAppearance();sendBroadcast(new Intent(ACTION_REFRESH).setPackage(getPackageName()));}
    private void buildPanel(){
        root=new FrameLayout(this){
            final ScaleGestureDetector pinch=new ScaleGestureDetector(OverlayService.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){float scale;int startWidth,startFont;
                @Override public boolean onScaleBegin(ScaleGestureDetector d){scale=1;startWidth=params.width;startFont=prefs.getInt("font",22);return true;}
                @Override public boolean onScale(ScaleGestureDetector d){scale*=d.getScaleFactor();int screen=getResources().getDisplayMetrics().widthPixels;params.width=Math.max(dp(240),Math.min(screen,(int)(startWidth*scale)));keepOnScreen();windows.updateViewLayout(root,params);current.setTextSize(Math.max(15,Math.min(34,Math.round(startFont*scale))));return true;}
                @Override public void onScaleEnd(ScaleGestureDetector d){prefs.edit().putInt("width",Math.round(params.width*100f/getResources().getDisplayMetrics().widthPixels)).putInt("font",Math.max(15,Math.min(34,Math.round(startFont*scale)))).putInt("x",params.x).apply();applyAppearance();}});
            @Override public boolean dispatchTouchEvent(MotionEvent e){pinch.onTouchEvent(e);return super.dispatchTouchEvent(e);}
            @Override public boolean onInterceptTouchEvent(MotionEvent e){return e.getPointerCount()>1||super.onInterceptTouchEvent(e);}
            @Override public boolean onTouchEvent(MotionEvent e){return true;}};panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(10),dp(12),dp(12));root.addView(panel,new FrameLayout.LayoutParams(-1,-2));
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);title=label("Spotify를 기다리는 중",11,0xffb6b1b7);title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);title.setSingleLine(true);title.setEllipsize(TextUtils.TruncateAt.END);header.addView(title,new LinearLayout.LayoutParams(0,dp(32),1));
        icon(header,IconButton.SETTINGS,30,8,v->toggleSettings());icon(header,IconButton.CLOSE,30,8,v->stopSelf());panel.addView(header);
        lyricsBox=new LinearLayout(this);lyricsBox.setOrientation(LinearLayout.VERTICAL);lyricsBox.setGravity(Gravity.CENTER);panel.addView(lyricsBox,lp(-1,-2,4));
        previous=label("",13,0xff99949b);current=label(message,22,0xffff75b4);next=label("",13,0xff99949b);current.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));current.setMaxLines(2);current.setEllipsize(TextUtils.TruncateAt.END);current.setLineSpacing(0,1.08f);
        for(TextView v:new TextView[]{previous,next}){v.setSingleLine(true);v.setEllipsize(TextUtils.TruncateAt.END);}
        lyricsBox.addView(previous,lp(-1,-2,6));lyricsBox.addView(current,lp(-1,-2,8));lyricsBox.addView(next,lp(-1,-2,8));
        timelineRow=new LinearLayout(this);timelineRow.setGravity(Gravity.CENTER_VERTICAL);elapsed=label("0:00",10,0xffbcb6c0);total=label("0:00",10,0xffbcb6c0);wave=new WaveView(this);
        timelineRow.addView(elapsed,new LinearLayout.LayoutParams(dp(34),-2));timelineRow.addView(wave,new LinearLayout.LayoutParams(0,dp(46),1));timelineRow.addView(total,new LinearLayout.LayoutParams(dp(34),-2));lyricsBox.addView(timelineRow,lp(-1,-2,10));
        wave.setSeekListener(new WaveView.SeekListener(){public void onScrub(float r){elapsed.setText(format((long)(duration*r)));}public void onSeek(float r){if(controller!=null&&duration>0)controller.getTransportControls().seekTo((long)(duration*r));}});
        controls=new LinearLayout(this);controls.setGravity(Gravity.CENTER);
        icon(controls,IconButton.PREVIOUS,38,0,v->{if(controller!=null)controller.getTransportControls().skipToPrevious();});
        play=icon(controls,IconButton.PLAY,46,18,v->{if(controller!=null){PlaybackState p=controller.getPlaybackState();if(p!=null&&p.getState()==PlaybackState.STATE_PLAYING)controller.getTransportControls().pause();else controller.getTransportControls().play();}});play.setStrong(true);
        icon(controls,IconButton.NEXT,38,18,v->{if(controller!=null)controller.getTransportControls().skipToNext();});lyricsBox.addView(controls,lp(-1,-2,4));
        buildSettings();
        params=new WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;params.x=prefs.getInt("x",dp(12));params.y=prefs.getInt("y",dp(110));
        header.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int ox,oy;public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){case MotionEvent.ACTION_DOWN:sx=e.getRawX();sy=e.getRawY();ox=params.x;oy=params.y;return true;case MotionEvent.ACTION_MOVE:params.x=ox+(int)(e.getRawX()-sx);params.y=oy+(int)(e.getRawY()-sy);keepOnScreen();windows.updateViewLayout(root,params);return true;case MotionEvent.ACTION_UP:prefs.edit().putInt("x",params.x).putInt("y",params.y).apply();v.performClick();return true;default:return false;}}});
        applyAppearance();try{windows.addView(root,params);}catch(RuntimeException e){stopSelf();}
    }
    private GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private TextView section(LinearLayout box,String text){TextView v=label(text,12,0xffbcb6c0);v.setGravity(Gravity.START);box.addView(v,lp(-1,-2,16));return v;}
    private void toggleRow(LinearLayout box,String text,String key,boolean def){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView name=label(text,14,0xfff5f4f7);name.setGravity(Gravity.START);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        View pill=new View(this){final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);@Override protected void onDraw(Canvas c){boolean on=prefs.getBoolean(key,def);float w=getWidth(),h=getHeight();p.setColor(on?0xffff75b4:0xff45434d);c.drawRoundRect(0,0,w,h,h/2,h/2,p);p.setColor(0xfff5f4f7);c.drawCircle(on?w-h/2:h/2,h/2,h/2-dp(3),p);}};
        row.addView(pill,new LinearLayout.LayoutParams(dp(40),dp(22)));row.setPadding(0,dp(9),0,dp(9));
        row.setOnClickListener(v->{prefs.edit().putBoolean(key,!prefs.getBoolean(key,def)).apply();pill.invalidate();refreshAll();});box.addView(row,lp(-1,-2,2));
    }
    private void buildSettings(){
        settingsBox=new ScrollView(this);settingsBox.setVerticalScrollBarEnabled(false);settingsBox.setVisibility(View.GONE);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(2),0,dp(4),dp(4));settingsBox.addView(box);panel.addView(settingsBox,lp(-1,-2,2));
        section(box,"배경 투명도");SeekBar opacity=new SeekBar(this);opacity.setMax(75);opacity.setProgress(prefs.getInt("opacity",85)-25);opacity.setProgressTintList(ColorStateList.valueOf(0xffff75b4));opacity.setThumbTintList(ColorStateList.valueOf(0xfff5f4f7));opacity.setProgressBackgroundTintList(ColorStateList.valueOf(0x66ffffff));box.addView(opacity,lp(-1,dp(32),4));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){if(user){prefs.edit().putInt("opacity",v+25).apply();refreshAll();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        LinearLayout size=new LinearLayout(this);size.setGravity(Gravity.CENTER_VERTICAL);TextView sizeName=label("글자 크기",12,0xffbcb6c0);sizeName.setGravity(Gravity.START);size.addView(sizeName,new LinearLayout.LayoutParams(0,-2,1));
        icon(size,IconButton.MINUS,32,0,v->fontStep(-2));fontValue=label("",14,0xfff5f4f7);size.addView(fontValue,new LinearLayout.LayoutParams(dp(40),-2));icon(size,IconButton.PLUS,32,0,v->fontStep(2));box.addView(size,lp(-1,-2,14));
        section(box,"현재 가사 색");swatches=new LinearLayout(this);swatches.setGravity(Gravity.CENTER_VERTICAL);box.addView(swatches,lp(-1,-2,8));
        for(int color:LYRIC_COLORS){View s=new View(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(30),dp(30));p.rightMargin=dp(12);swatches.addView(s,p);s.setTag(color);s.setOnClickListener(v->{prefs.edit().putInt("lyric_color",(int)v.getTag()).apply();refreshAll();});}
        section(box,"표시");toggleRow(box,"움직이는 물결","wave",true);toggleRow(box,"노래 타임라인","timeline",true);toggleRow(box,"재생 제어 버튼","controls",true);toggleRow(box,"NetEase 가사 먼저 찾기","netease",true);
        TextView note=label("가사를 찾을 때 곡 정보가 NetEase에 먼저, 없으면 LRCLIB에 전송됩니다. 끄면 LRCLIB만 사용합니다.",11,0xff99949b);note.setGravity(Gravity.START);box.addView(note,lp(-1,-2,8));
        LinearLayout buttons=new LinearLayout(this);box.addView(buttons,lp(-1,dp(42),16));
        TextView more=label("권한 · 앱 설정",13,0xfff5f4f7);more.setBackground(round(0xff45434d,12));buttons.addView(more,new LinearLayout.LayoutParams(0,-1,1));more.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        TextView done=label("설정 닫기",13,0xff1c1d21);done.setBackground(round(0xfff5f4f7,12));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-1,1);dl.leftMargin=dp(8);buttons.addView(done,dl);done.setOnClickListener(v->toggleSettings());
    }
    private void fontStep(int delta){prefs.edit().putInt("font",Math.max(15,Math.min(34,prefs.getInt("font",22)+delta))).apply();refreshAll();}
    private void toggleSettings(){boolean open=settingsBox.getVisibility()!=View.VISIBLE;settingsBox.setVisibility(open?View.VISIBLE:View.GONE);lyricsBox.setVisibility(open?View.GONE:View.VISIBLE);
        if(open){int max=getResources().getDisplayMetrics().heightPixels/2;settingsBox.getLayoutParams().height=-2;settingsBox.measure(View.MeasureSpec.makeMeasureSpec(params.width>0?params.width:getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.AT_MOST),View.MeasureSpec.UNSPECIFIED);if(settingsBox.getMeasuredHeight()>max)settingsBox.getLayoutParams().height=max;settingsBox.requestLayout();}
        wave.updatePlayback(false,0);}
    private void applyAppearance(){if(root==null)return;int opacity=prefs.getInt("opacity",85),font=prefs.getInt("font",22),color=prefs.getInt("lyric_color",LYRIC_COLORS[0]);panel.setBackground(round(Color.argb(opacity*255/100,28,29,33),20));
        current.setTextSize(font);current.setTextColor(color);previous.setTextSize(Math.max(12,font*.58f));next.setTextSize(Math.max(12,font*.58f));if(fontValue!=null)fontValue.setText(String.valueOf(font));
        for(int i=0;i<swatches.getChildCount();i++){View s=swatches.getChildAt(i);GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor((int)s.getTag());if((int)s.getTag()==color)d.setStroke(dp(3),0xfff5f4f7);s.setBackground(d);}
        timelineRow.setVisibility(prefs.getBoolean("timeline",true)?View.VISIBLE:View.GONE);controls.setVisibility(prefs.getBoolean("controls",true)?View.VISIBLE:View.GONE);wave.setWave(prefs.getBoolean("wave",true));
        int screen=getResources().getDisplayMetrics().widthPixels;params.width=Math.max(dp(240),Math.min(screen,screen*prefs.getInt("width",94)/100));keepOnScreen();if(root.isAttachedToWindow())try{windows.updateViewLayout(root,params);}catch(RuntimeException ignored){}}
    private void keepOnScreen(){android.util.DisplayMetrics m=getResources().getDisplayMetrics();params.x=Math.max(0,Math.min(m.widthPixels-params.width,params.x));params.y=Math.max(0,Math.min(m.heightPixels-dp(120),params.y));}
    private static String format(long ms){long s=Math.max(0,ms/1000);return String.format(Locale.ROOT,"%d:%02d",s/60,s%60);}
    private final Runnable tick=new Runnable(){public void run(){updatePlayback();main.postDelayed(this,300);}};
    private void updatePlayback(){if(root==null)return;try{
        MediaSessionManager sm=(MediaSessionManager)getSystemService(MEDIA_SESSION_SERVICE);List<MediaController> active=sm.getActiveSessions(new ComponentName(this,PlaybackListener.class));controller=null;for(MediaController c:active)if("com.spotify.music".equals(c.getPackageName())){controller=c;break;}
        if(controller==null){title.setText("Spotify를 기다리는 중");current.setText("Spotify에서 음악을 재생하세요");previous.setText("");next.setText("");wave.updatePlayback(false,0);if(!trackKey.isEmpty()){trackKey="";lines=Collections.emptyList();generation++;}return;}
        MediaMetadata m=controller.getMetadata();PlaybackState state=controller.getPlaybackState();if(m==null||state==null)return;
        String song=String.valueOf(m.getString(MediaMetadata.METADATA_KEY_TITLE));String artist=String.valueOf(m.getString(MediaMetadata.METADATA_KEY_ARTIST));String album=m.getString(MediaMetadata.METADATA_KEY_ALBUM);long length=m.getLong(MediaMetadata.METADATA_KEY_DURATION);String id=m.getString(MediaMetadata.METADATA_KEY_MEDIA_ID);String key=String.valueOf(id)+"|"+song+"|"+artist+"|"+length;
        title.setText(song+" · "+artist+(source.isEmpty()?"":" · "+source));duration=length;boolean playing=state.getState()==PlaybackState.STATE_PLAYING;play.setGlyph(playing?IconButton.PAUSE:IconButton.PLAY);long pos=Math.max(0,state.getPosition());if(playing&&state.getLastPositionUpdateTime()>0)pos+=(long)((SystemClock.elapsedRealtime()-state.getLastPositionUpdateTime())*state.getPlaybackSpeed());if(duration>0)pos=Math.min(duration,pos);
        if(lyricsBox.getVisibility()==View.VISIBLE){wave.updatePlayback(playing,duration>0?(float)pos/duration:0);if(!wave.isDragging())elapsed.setText(format(pos));total.setText(format(duration));}wave.setSeekable((state.getActions()&PlaybackState.ACTION_SEEK_TO)!=0&&duration>0);
        if(!key.equals(trackKey)){trackKey=key;generation++;source="";lines=Collections.emptyList();lookupInFlight=false;message="가사를 찾고 있어요…";retryAt=0;lookup(song,artist,album,length,generation);}
        if(lines.isEmpty()&&!lookupInFlight&&retryAt>0&&SystemClock.elapsedRealtime()>=retryAt)lookup(song,artist,album,length,generation);
        if(lines.isEmpty()){current.setText(message);previous.setText("");next.setText("");return;}
        int selected=-1;for(int i=0;i<lines.size();i++){if(lines.get(i).ms<=pos)selected=i;else break;}current.setText(selected<0||lines.get(selected).text.isEmpty()?"♪":lines.get(selected).text);previous.setText(selected>0?lines.get(selected-1).text:"");next.setText(selected+1<lines.size()?lines.get(selected+1).text:"");
    }catch(SecurityException e){current.setText("알림 접근을 허용해 주세요");}}
    private static final class Line{final long ms;final String text;Line(long ms,String text){this.ms=ms;this.text=text;}}
    private static final Pattern STAMP=Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]");
    static List<Line> parse(String lrc){List<Line> out=new ArrayList<>();if(lrc==null)return out;for(String line:lrc.split("\\r?\\n")){Matcher m=STAMP.matcher(line);String text=STAMP.matcher(line).replaceAll("").trim();while(m.find())try{out.add(new Line((long)((Integer.parseInt(m.group(1))*60+Double.parseDouble(m.group(2)))*1000),text));}catch(NumberFormatException ignored){}}out.sort(Comparator.comparingLong(x->x.ms));return out;}
    private static String enc(String s){try{return URLEncoder.encode(s==null?"":s,"UTF-8");}catch(UnsupportedEncodingException e){throw new IllegalStateException(e);}}
    private static final class HttpResult{int code;String body;HttpResult(int c,String b){code=c;body=b;}}
    private static HttpResult request(String url)throws IOException{return request(url,false);}
    private static HttpResult request(String url,boolean netease)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent",netease?"Mozilla/5.0":"LyricsFloatAndroid/0.1 (https://github.com/taehyeon03/lyrics-float)");if(netease)c.setRequestProperty("Referer","https://music.163.com/");
        try{int code=c.getResponseCode();if(code!=200)return new HttpResult(code,"");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>1048576)throw new IOException("response too large");out.write(buf,0,n);}return new HttpResult(code,new String(out.toByteArray(),StandardCharsets.UTF_8));}}finally{c.disconnect();}
    }
    private static String normalized(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]","");}
    private static JSONObject matchSearch(JSONArray entries,String song,String artist,long ms){
        String expectedTitle=normalized(song),expectedArtist=normalized(artist.split(",")[0]);JSONObject best=null;double delta=Double.MAX_VALUE;
        for(int i=0;i<entries.length();i++){JSONObject x=entries.optJSONObject(i);if(x==null||x.isNull("syncedLyrics"))continue;
            String title=normalized(x.optString("trackName")),author=normalized(x.optString("artistName"));double diff=Math.abs(x.optDouble("duration",0)-ms/1000.0);
            if(expectedTitle.equals(title)&&!expectedArtist.isEmpty()&&(author.equals(expectedArtist)||author.contains(expectedArtist))&&(ms<=0||diff<=10)&&diff<delta){best=x;delta=diff;}}
        return best;
    }
    private static String baseTitle(String text){return normalized(text==null?"":text.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?|featuring)[^\\)\\]]*[\\)\\]]", ""));}
    private static String queryTitle(String text){return text==null?"":text.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?|featuring)[^\\)\\]]*[\\)\\]]", "").trim();}
    private static final Pattern HANGUL=Pattern.compile("[\\p{IsHangul}]"),HAN_TAIL=Pattern.compile("\\s*\\p{IsHan}[\\p{IsHan}\\p{P}\\s]*$"),CREDIT=Pattern.compile("^(作词|作曲|编曲|制作人|演唱|和声|混音|母带|录音|监制|出品|吉他|贝斯|鼓|键盘|弦乐)[^:：]*[:：]");
    /** NetEase often appends a Chinese translation to each Korean line; keep only the original when the song is Korean. */
    static List<Line> stripTranslation(List<Line> in){boolean korean=false;for(Line l:in)if(HANGUL.matcher(l.text).find()){korean=true;break;}if(!korean)return in;
        List<Line> out=new ArrayList<>();for(Line l:in){if(CREDIT.matcher(l.text).find())continue;String t=HAN_TAIL.matcher(l.text).replaceAll("").trim();if(t.isEmpty()&&!l.text.isEmpty())continue;out.add(new Line(l.ms,t));}return out;}
    private static List<Line> neteaseLookup(String song,String artist,String album,long ms)throws Exception{
        String query=queryTitle(song)+" "+artist.split(",")[0].trim();
        HttpResult search=request("https://music.163.com/api/search/get?type=1&limit=20&s="+enc(query),true);
        if(search.code!=200)throw new IOException("NetEase search unavailable");
        JSONArray songs=new JSONObject(search.body).optJSONObject("result")==null?new JSONArray():new JSONObject(search.body).getJSONObject("result").optJSONArray("songs");
        if(songs==null)return Collections.emptyList();
        JSONObject best=null;double bestDelta=Double.MAX_VALUE;String wantedTitle=baseTitle(song),wantedAlbum=normalized(album),wantedArtist=normalized(artist.split(",")[0]);
        for(int i=0;i<songs.length();i++){
            JSONObject item=songs.optJSONObject(i);if(item==null||!wantedTitle.equals(baseTitle(item.optString("name"))))continue;
            double delta=Math.abs(item.optLong("duration")-ms);if(ms<=0||delta>3000)continue;
            JSONObject al=item.optJSONObject("album");boolean albumMatch=al!=null&&!wantedAlbum.isEmpty()&&wantedAlbum.equals(normalized(al.optString("name")));
            boolean artistMatch=false;JSONArray artists=item.optJSONArray("artists");if(artists!=null)for(int j=0;j<artists.length();j++){JSONObject a=artists.optJSONObject(j);if(a!=null&&!wantedArtist.isEmpty()&&wantedArtist.equals(normalized(a.optString("name"))))artistMatch=true;}
            if((albumMatch||artistMatch)&&delta<bestDelta){best=item;bestDelta=delta;}
        }
        if(best==null)return Collections.emptyList();
        HttpResult response=request("https://music.163.com/api/song/lyric?lv=1&tv=-1&id="+best.optLong("id"),true);
        if(response.code!=200)throw new IOException("NetEase lyrics unavailable");
        JSONObject lrc=new JSONObject(response.body).optJSONObject("lrc");return lrc==null?Collections.emptyList():stripTranslation(parse(lrc.optString("lyric",null)));
    }
    private void lookup(String song,String artist,String album,long ms,int requested){lookupInFlight=true;final boolean useNetease=getSharedPreferences("display",MODE_PRIVATE).getBoolean("netease",true);io.execute(()->{List<Line> found=Collections.emptyList();String result="가사를 찾지 못했습니다";String from="LRCLIB";boolean transientError=false;
        if(useNetease){
            try{List<Line> alt=neteaseLookup(song,artist,album,ms);if(!alt.isEmpty()){found=alt;from="NetEase";transientError=false;android.util.Log.i("LyricsFloat","NetEase synced lyrics found");}}
            catch(Exception e){android.util.Log.w("LyricsFloat","NetEase request failed: "+e.getClass().getSimpleName());}
        }
        if(found.isEmpty())try{
          String url="https://lrclib.net/api/get?track_name="+enc(song)+"&artist_name="+enc(artist)+"&album_name="+enc(album)+"&duration="+(ms/1000);
          HttpResult exact=request(url);JSONObject data=null;
          android.util.Log.i("LyricsFloat","LRCLIB get HTTP "+exact.code);if(exact.code==200)data=new JSONObject(exact.body);
          else if(exact.code==404||exact.code==429||exact.code>=500){
            HttpResult search=request("https://lrclib.net/api/search?track_name="+enc(song)+"&artist_name="+enc(artist.split(",")[0]));
            android.util.Log.i("LyricsFloat","LRCLIB search HTTP "+search.code);if(search.code==200)data=matchSearch(new JSONArray(search.body),song,artist,ms);
            else if(search.code!=404){result="가사 서비스 연결 오류 · 다시 시도합니다";transientError=true;}
            if(data==null&&exact.code!=404){result="가사 서비스 연결 오류 · 다시 시도합니다";transientError=true;}
          }else{result="가사 서비스 연결 오류 · 다시 시도합니다";transientError=true;}
          if(data!=null){found=parse(data.optString("syncedLyrics",null));result=data.optBoolean("instrumental",false)?"연주곡입니다":(data.isNull("plainLyrics")?"가사를 찾지 못했습니다":"동기화된 가사가 없습니다");transientError=false;}
        }catch(Exception e){android.util.Log.w("LyricsFloat","Lyrics request failed: "+e.getClass().getSimpleName());result="가사 서비스 연결 오류 · 다시 시도합니다";transientError=true;}
        final List<Line> fetched=found;final String status=result;final String provider=found.isEmpty()?"":from;final boolean retry=transientError;main.post(()->{if(requested!=generation)return;lookupInFlight=false;lines=fetched;source=provider;message=status;retryAt=retry?SystemClock.elapsedRealtime()+15000:0;});});}
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);io.shutdownNow();try{unregisterReceiver(refresh);}catch(IllegalArgumentException ignored){}if(root!=null&&root.isAttachedToWindow())windows.removeView(root);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
