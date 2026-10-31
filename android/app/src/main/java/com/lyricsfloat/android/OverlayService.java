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
    static final int[] LYRIC_COLORS={0xffff75b4,0xfff4f2f4,0xff8fceff,0xffc7a6ff,0xffffd989,0xff7fe0c0,0xffff8a7a,0xffffb26b,0xffc8f07a,0xffffb3d1,0xff6fe3ff,0xffb6b1b7};
    static final int[] BG_COLORS={0xff1c1d21,0xff000000,0xff141a2e,0xff241a33,0xff2e1520,0xff13251f,0xff2a2622,0xff1f2b33,0xff3a3d46,0xfff4f2f4};
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private WindowManager windows;private WindowManager.LayoutParams params;private FrameLayout root;private LinearLayout panel,lyricsBox,timelineRow,controls,lyricSwatches,bgSwatches;private ScrollView settingsBox;
    private TextView title,previous,current,next,elapsed,total,fontValue;private IconButton play;private WaveView wave;private SharedPreferences prefs;
    private MediaController controller;private boolean lookupInFlight=false;private String trackKey="",source="",message="Spotify에서 음악을 재생하세요";private long duration=0,retryAt=0;private int generation=0;private List<Line> lines=Collections.emptyList();
    private final BroadcastReceiver refresh=new BroadcastReceiver(){@Override public void onReceive(Context c,Intent i){applyAppearance();}};
    private int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    @Override public void onCreate(){super.onCreate();
        NotificationManager nm=getSystemService(NotificationManager.class);nm.createNotificationChannel(new NotificationChannel("overlay","가사 창",NotificationManager.IMPORTANCE_LOW));
        PendingIntent pi=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification=new Notification.Builder(this,"overlay").setSmallIcon(R.drawable.ic_notification).setContentTitle("가사 플로팅 실행 중").setContentText("눌러서 설정을 엽니다").setContentIntent(pi).setOngoing(true).build();startForeground(101,notification);
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
            final int slop=ViewConfiguration.get(OverlayService.this).getScaledTouchSlop();float downX,downY,anchorX,anchorY,ratioX,ratioY;int startX,startY;boolean dragging,pinched,inSettings;
            final ScaleGestureDetector pinch=new ScaleGestureDetector(OverlayService.this,new ScaleGestureDetector.SimpleOnScaleGestureListener(){float scale;int startWidth,startFont;
                @Override public boolean onScaleBegin(ScaleGestureDetector d){pinched=true;dragging=false;scale=1;startWidth=params.width;startFont=prefs.getInt("font",22);anchorX=params.x+d.getFocusX();anchorY=params.y+d.getFocusY();ratioX=d.getFocusX()/Math.max(1,getWidth());ratioY=d.getFocusY()/Math.max(1,getHeight());return true;}
                @Override public boolean onScale(ScaleGestureDetector d){scale*=d.getScaleFactor();int screen=getResources().getDisplayMetrics().widthPixels;params.width=Math.max(dp(240),Math.min(screen,(int)(startWidth*scale)));params.x=Math.round(anchorX-ratioX*params.width);keepOnScreen();windows.updateViewLayout(root,params);current.setTextSize(Math.max(15,Math.min(34,Math.round(startFont*scale))));return true;}
                @Override public void onScaleEnd(ScaleGestureDetector d){prefs.edit().putInt("width",Math.round(params.width*100f/getResources().getDisplayMetrics().widthPixels)).putInt("font",Math.max(15,Math.min(34,Math.round(startFont*scale)))).putInt("x",params.x).putInt("y",params.y).apply();applyAppearance();}});
            {addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob)->{if(pinch.isInProgress()&&b-t!=ob-ot){params.y=Math.round(anchorY-ratioY*(b-t));keepOnScreen();windows.updateViewLayout(this,params);}});}
            private boolean moved(MotionEvent e){return Math.hypot(e.getRawX()-downX,e.getRawY()-downY)>slop;}
            @Override public boolean dispatchTouchEvent(MotionEvent e){pinch.onTouchEvent(e);if(e.getActionMasked()==MotionEvent.ACTION_DOWN){downX=e.getRawX();downY=e.getRawY();startX=params.x;startY=params.y;dragging=false;pinched=false;inSettings=settingsBox.getVisibility()==View.VISIBLE&&e.getY()>=panel.getTop()+settingsBox.getTop();}return super.dispatchTouchEvent(e);}
            @Override public boolean onInterceptTouchEvent(MotionEvent e){if(e.getPointerCount()>1)return true;if(e.getActionMasked()==MotionEvent.ACTION_MOVE&&!pinched&&!inSettings&&moved(e)){dragging=true;return true;}return false;}
            @Override public boolean onTouchEvent(MotionEvent e){switch(e.getActionMasked()){
                case MotionEvent.ACTION_MOVE:if(!pinched&&!inSettings&&e.getPointerCount()==1&&(dragging||moved(e))){dragging=true;params.x=startX+(int)(e.getRawX()-downX);params.y=startY+(int)(e.getRawY()-downY);keepOnScreen();windows.updateViewLayout(this,params);}break;
                case MotionEvent.ACTION_UP:if(dragging)prefs.edit().putInt("x",params.x).putInt("y",params.y).apply();dragging=false;break;}
                return true;}};panel=new LinearLayout(this);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(16),dp(10),dp(12),dp(12));root.addView(panel,new FrameLayout.LayoutParams(-1,-2));
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
        play=icon(controls,IconButton.PLAY,46,18,v->{if(controller!=null){PlaybackState p=controller.getPlaybackState();if(p!=null&&p.getState()==PlaybackState.STATE_PLAYING)controller.getTransportControls().pause();else controller.getTransportControls().play();}else startSpotify();});play.setStrong(true);
        icon(controls,IconButton.NEXT,38,18,v->{if(controller!=null)controller.getTransportControls().skipToNext();});lyricsBox.addView(controls,lp(-1,-2,4));
        buildSettings();
        params=new WindowManager.LayoutParams(-1,-2,WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);params.gravity=Gravity.TOP|Gravity.START;params.x=prefs.getInt("x",dp(12));params.y=prefs.getInt("y",dp(110));
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
        lyricSwatches=palette(box,"현재 가사 색","lyric_color",LYRIC_COLORS,.55f,1f);bgSwatches=palette(box,"배경 색","bg_color",BG_COLORS,.45f,.2f);
        section(box,"표시");toggleRow(box,"움직이는 물결","wave",true);toggleRow(box,"노래 타임라인","timeline",true);toggleRow(box,"재생 제어 버튼","controls",true);toggleRow(box,"Spotify 가사 먼저 찾기","spotify",true);
        TextView note=label("Spotify에 로그인하면 Spotify 가사를 먼저 쓰고, 없으면 LRCLIB에서 찾습니다. 끄면 LRCLIB만 사용합니다.",11,0xff99949b);note.setGravity(Gravity.START);box.addView(note,lp(-1,-2,8));
        LinearLayout buttons=new LinearLayout(this);box.addView(buttons,lp(-1,dp(42),16));
        TextView more=label("권한 · 앱 설정",13,0xfff5f4f7);more.setBackground(round(0xff45434d,12));buttons.addView(more,new LinearLayout.LayoutParams(0,-1,1));more.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        TextView done=label("설정 닫기",13,0xff1c1d21);done.setBackground(round(0xfff5f4f7,12));LinearLayout.LayoutParams dl=new LinearLayout.LayoutParams(0,-1,1);dl.leftMargin=dp(8);buttons.addView(done,dl);done.setOnClickListener(v->toggleSettings());
    }
    /** A scrollable row of preset swatches plus a hue slider for any other color; saturation and brightness suit the target. */
    private LinearLayout palette(LinearLayout box,String title,String key,int[] colors,float saturation,float value){
        section(box,title);HorizontalScrollView scroll=new HorizontalScrollView(this);scroll.setHorizontalScrollBarEnabled(false);LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(2),dp(2),dp(2),dp(2));scroll.addView(row);box.addView(scroll,lp(-1,-2,8));
        for(int color:colors){View v=new View(this);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(30),dp(30));p.rightMargin=dp(10);row.addView(v,p);v.setTag(color);v.setOnClickListener(x->{prefs.edit().putInt(key,(int)x.getTag()).apply();refreshAll();});}
        int[] hues=new int[13];for(int i=0;i<hues.length;i++)hues[i]=Color.HSVToColor(new float[]{i*30f,saturation,value});
        GradientDrawable track=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,hues);track.setCornerRadius(dp(5));
        SeekBar hue=new SeekBar(this);hue.setMax(359);hue.setProgressDrawable(track);hue.setThumbTintList(ColorStateList.valueOf(0xfff5f4f7));if(Build.VERSION.SDK_INT>=29){hue.setMinHeight(dp(10));hue.setMaxHeight(dp(10));}
        float[] hsv=new float[3];Color.colorToHSV(prefs.getInt(key,colors[0]),hsv);hue.setProgress((int)hsv[0]);box.addView(hue,lp(-1,dp(30),8));
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int h,boolean user){if(user){prefs.edit().putInt(key,Color.HSVToColor(new float[]{h,saturation,value})).apply();refreshAll();}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});
        return row;
    }
    private void ring(LinearLayout row,int selected){for(int i=0;i<row.getChildCount();i++){View v=row.getChildAt(i);GradientDrawable d=new GradientDrawable();d.setShape(GradientDrawable.OVAL);d.setColor((int)v.getTag());d.setStroke(dp((int)v.getTag()==selected?3:1),(int)v.getTag()==selected?0xffff75b4:0x40ffffff);v.setBackground(d);}}
    /** With no Spotify session, wake Spotify with a play key; if it still has none shortly after, open the app and press play again. */
    private void startSpotify(){
        Intent key=new Intent(Intent.ACTION_MEDIA_BUTTON).setPackage("com.spotify.music");
        for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP})sendBroadcast(new Intent(key).putExtra(Intent.EXTRA_KEY_EVENT,new KeyEvent(action,KeyEvent.KEYCODE_MEDIA_PLAY)));
        main.postDelayed(()->{if(controller!=null)return;Intent open=getPackageManager().getLaunchIntentForPackage("com.spotify.music");if(open==null){current.setText("Spotify가 설치되어 있지 않습니다");return;}
            startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));main.postDelayed(()->{if(controller!=null&&controller.getPlaybackState()!=null&&controller.getPlaybackState().getState()==PlaybackState.STATE_PLAYING)return;android.media.AudioManager audio=getSystemService(android.media.AudioManager.class);for(int action:new int[]{KeyEvent.ACTION_DOWN,KeyEvent.ACTION_UP})audio.dispatchMediaKeyEvent(new KeyEvent(action,KeyEvent.KEYCODE_MEDIA_PLAY));},2500);},1500);
    }
    private void fontStep(int delta){prefs.edit().putInt("font",Math.max(15,Math.min(34,prefs.getInt("font",22)+delta))).apply();refreshAll();}
    private void toggleSettings(){boolean open=settingsBox.getVisibility()!=View.VISIBLE;settingsBox.setVisibility(open?View.VISIBLE:View.GONE);lyricsBox.setVisibility(open?View.GONE:View.VISIBLE);
        if(open){int max=getResources().getDisplayMetrics().heightPixels/2;settingsBox.getLayoutParams().height=-2;settingsBox.measure(View.MeasureSpec.makeMeasureSpec(params.width>0?params.width:getResources().getDisplayMetrics().widthPixels,View.MeasureSpec.AT_MOST),View.MeasureSpec.UNSPECIFIED);if(settingsBox.getMeasuredHeight()>max)settingsBox.getLayoutParams().height=max;settingsBox.requestLayout();}
        wave.updatePlayback(false,0);}
    private void applyAppearance(){if(root==null)return;int opacity=prefs.getInt("opacity",85),font=prefs.getInt("font",22),color=prefs.getInt("lyric_color",LYRIC_COLORS[0]);int bg=prefs.getInt("bg_color",BG_COLORS[0]);panel.setBackground(round(Color.argb(opacity*255/100,Color.red(bg),Color.green(bg),Color.blue(bg)),20));
        current.setTextSize(font);current.setTextColor(color);previous.setTextSize(Math.max(12,font*.58f));next.setTextSize(Math.max(12,font*.58f));if(fontValue!=null)fontValue.setText(String.valueOf(font));
        ring(lyricSwatches,color);ring(bgSwatches,bg);
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
        if(!key.equals(trackKey)){trackKey=key;generation++;source="";lines=Collections.emptyList();lookupInFlight=false;message="가사를 찾고 있어요…";retryAt=0;lookup(id,song,artist,album,length,generation);}
        if(lines.isEmpty()&&!lookupInFlight&&retryAt>0&&SystemClock.elapsedRealtime()>=retryAt)lookup(id,song,artist,album,length,generation);
        if(lines.isEmpty()){current.setText(message);previous.setText("");next.setText("");return;}
        int selected=-1;for(int i=0;i<lines.size();i++){if(lines.get(i).ms<=pos)selected=i;else break;}current.setText(selected<0||lines.get(selected).text.isEmpty()?"♪":lines.get(selected).text);previous.setText(selected>0?lines.get(selected-1).text:"");next.setText(selected+1<lines.size()?lines.get(selected+1).text:"");
    }catch(SecurityException e){current.setText("알림 접근을 허용해 주세요");}}
    static final class Line{final long ms;final String text;Line(long ms,String text){this.ms=ms;this.text=text;}}
    private static final Pattern STAMP=Pattern.compile("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\]");
    static List<Line> parse(String lrc){List<Line> out=new ArrayList<>();if(lrc==null)return out;for(String line:lrc.split("\\r?\\n")){Matcher m=STAMP.matcher(line);String text=STAMP.matcher(line).replaceAll("").trim();while(m.find())try{out.add(new Line((long)((Integer.parseInt(m.group(1))*60+Double.parseDouble(m.group(2)))*1000),text));}catch(NumberFormatException ignored){}}out.sort(Comparator.comparingLong(x->x.ms));return out;}
    private static String enc(String s){try{return URLEncoder.encode(s==null?"":s,"UTF-8");}catch(UnsupportedEncodingException e){throw new IllegalStateException(e);}}
    private static final class HttpResult{int code;String body;HttpResult(int c,String b){code=c;body=b;}}
    private static HttpResult request(String url)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);c.setRequestProperty("User-Agent","LyricsFloatAndroid/0.1 (https://github.com/taehyeon03/lyrics-float)");
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
    private void lookup(String mediaId,String song,String artist,String album,long ms,int requested){lookupInFlight=true;final boolean wantSpotify=prefs.getBoolean("spotify",true),useSpotify=wantSpotify&&SpotifyLyrics.loggedIn();io.execute(()->{List<Line> found=Collections.emptyList();String result="가사를 찾지 못했습니다";String from="LRCLIB";boolean transientError=false;String spotifyStatus=null;
        if(useSpotify){
            try{SpotifyLyrics.Result r=SpotifyLyrics.lookup(this,mediaId,song,artist,ms);if(!r.lines.isEmpty()){found=r.lines;from="Spotify";}else spotifyStatus=r.status;}
            catch(Exception e){android.util.Log.w("LyricsFloat","Spotify request failed: "+e);spotifyStatus="Spotify 오류 ("+e.getClass().getSimpleName()+")";}
        }else if(wantSpotify){spotifyStatus="Spotify 로그인 안 됨";
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
        if(found.isEmpty()&&spotifyStatus!=null)result=spotifyStatus+" · LRCLIB "+(transientError?"연결 오류":"없음");
        final List<Line> fetched=found;final String status=result;final String provider=found.isEmpty()?"":from;final boolean retry=transientError;main.post(()->{if(requested!=generation)return;lookupInFlight=false;lines=fetched;source=provider;message=status;retryAt=retry?SystemClock.elapsedRealtime()+15000:0;});});}
    @Override public int onStartCommand(Intent intent,int flags,int id){return START_NOT_STICKY;}
    @Override public void onDestroy(){main.removeCallbacksAndMessages(null);io.shutdownNow();try{unregisterReceiver(refresh);}catch(IllegalArgumentException ignored){}if(root!=null&&root.isAttachedToWindow())windows.removeView(root);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
