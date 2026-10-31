package com.lyricsfloat.android;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** Setup screen: permissions and starting the panel. Appearance lives in the panel's own ⚙ settings, as on the desktop. */
public final class MainActivity extends Activity {
    private static final int BG=0xff1c1d21,CARD=0xff26252c,PINK=0xffff75b4;
    private TextView notifyState,overlayState,spotifyState,toggle;
    private int dp(float n) { return (int)(n * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);scroll.setVerticalScrollBarEnabled(false);
        LinearLayout root=new LinearLayout(this);root.setPadding(dp(22),dp(40),dp(22),dp(28));root.setOrientation(LinearLayout.VERTICAL);scroll.addView(root);setContentView(scroll);
        TextView eyebrow=text("LYRICS FLOAT",11,0xffdca7c9);eyebrow.setLetterSpacing(.18f);root.addView(eyebrow);
        TextView title=text("음악 위에, 가사",30,0xfff4f2f4);title.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));add(root,title,8);
        add(root,text("Spotify를 들으면서 다른 앱 위에 가사를 띄웁니다.",15,0xffb6b1b7),8);
        add(root,preview(),26);
        add(root,section("준비"),28);
        notifyState=step(root,"1","알림 접근","Spotify의 곡 이름과 재생 위치를 읽습니다. 다른 알림 내용은 읽지 않습니다.",()->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)));
        overlayState=step(root,"2","다른 앱 위에 표시","가사 창을 다른 앱 위에 띄웁니다.",()->startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:"+getPackageName()))));
        spotifyState=step(root,"3","Spotify 로그인 (선택)","Spotify 가사를 사용합니다. Spotify 공식 기능이 아니며 약관에 어긋날 수 있습니다. 로그인 정보는 이 휴대폰에만 남습니다.",this::spotifyAccount);
        toggle=text("",16,BG);toggle.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));toggle.setGravity(Gravity.CENTER);toggle.setBackground(round(PINK,16));toggle.setOnClickListener(v->{if(running()){stopService(new Intent(this,OverlayService.class));v.postDelayed(this::refreshState,300);}else startOverlay();});
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,dp(56));tp.topMargin=dp(22);root.addView(toggle,tp);
        add(root,section("사용 방법"),28);
        add(root,text("· 창의 아무 곳이나 끌어서 옮깁니다.\n· 두 손가락으로 창 크기를 조절합니다.\n· 창의 ⚙에서 투명도, 글자 크기, 색, 물결, 타임라인을 바꿉니다.",14,0xffd8d2d9),10);
        TextView info=text("Spotify에 로그인하면 Spotify의 가사를 먼저 사용하고, 없으면 곡 정보를 LRCLIB에 보내 찾습니다. 로그인하지 않으면 LRCLIB만 사용합니다. 창 설정에서 Spotify 가사를 끌 수도 있습니다.",12,0xff8f8a93);add(root,info,24);
        if(getIntent().getBooleanExtra("start",false))startOverlay();
    }
    private TextView text(String value,int sp,int color) { TextView v=new TextView(this);v.setText(value);v.setTextSize(sp);v.setTextColor(color);v.setLineSpacing(0,1.15f);return v; }
    private TextView section(String value){TextView v=text(value,12,0xffbcb6c0);v.setLetterSpacing(.05f);return v;}
    private GradientDrawable round(int color,float radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private void add(LinearLayout root,View view,int top) { LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);root.addView(view,p); }
    private View preview(){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);card.setPadding(dp(18),dp(18),dp(18),dp(20));card.setBackground(round(0xff2c2b33,20));
        TextView a=text("이전 가사",13,0xff99949b),b=text("현재 노래의 가사입니다",21,PINK),c=text("다음 가사",13,0xff99949b);b.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        for(TextView v:new TextView[]{a,b,c}){v.setGravity(Gravity.CENTER);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(6);card.addView(v,p);}
        return card;
    }
    private TextView step(LinearLayout root,String number,String name,String detail,Runnable action){
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(14),dp(14),dp(14));row.setBackground(round(CARD,16));
        TextView badge=text(number,13,0xfff4f2f4);badge.setGravity(Gravity.CENTER);GradientDrawable dot=new GradientDrawable();dot.setShape(GradientDrawable.OVAL);dot.setColor(0x1effffff);badge.setBackground(dot);row.addView(badge,new LinearLayout.LayoutParams(dp(30),dp(30)));
        LinearLayout copy=new LinearLayout(this);copy.setOrientation(LinearLayout.VERTICAL);copy.setPadding(dp(14),0,dp(10),0);TextView n=text(name,15,0xfff4f2f4);n.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));copy.addView(n);TextView d=text(detail,12,0xff9d98a1);copy.addView(d);row.addView(copy,new LinearLayout.LayoutParams(0,-2,1));
        TextView chip=text("",12,0xfff4f2f4);chip.setGravity(Gravity.CENTER);chip.setPadding(dp(12),dp(6),dp(12),dp(6));row.addView(chip);
        row.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(10);root.addView(row,p);return chip;
    }
    private void spotifyAccount(){
        if(!SpotifyLyrics.loggedIn()){new android.app.AlertDialog.Builder(this).setTitle("Spotify 가사 연결").setMessage("Spotify 웹사이트에 직접 로그인합니다. 앱은 비밀번호를 보지 않지만, Spotify가 만든 로그인 정보(쿠키)를 이 휴대폰에 저장하고 가사를 가져올 때 사용합니다.\n\n공식 기능이 아니므로 Spotify 약관에 어긋날 수 있고, Spotify가 바꾸면 작동하지 않을 수 있습니다. 휴대폰을 다른 사람이 쓰면 계정에 접근할 수 있으니 주의하세요.").setPositiveButton("로그인",(d,w)->startActivity(new Intent(this,SpotifyLogin.class))).setNegativeButton("취소",null).show();return;}
        new android.app.AlertDialog.Builder(this).setTitle("Spotify 연결 해제").setMessage("이 휴대폰에 저장된 Spotify 로그인 정보를 지웁니다.").setPositiveButton("로그아웃",(d,w)->{SpotifyLyrics.logout();refreshState();}).setNegativeButton("취소",null).show();
    }
    private void mark(TextView chip,boolean ok){chip.setText(ok?"허용됨":"허용하기");chip.setTextColor(ok?0xffb6b1b7:BG);chip.setBackground(round(ok?0x1effffff:PINK,12));}
    private boolean notificationAccess() {
        ComponentName component=new ComponentName(this,PlaybackListener.class);
        if(Build.VERSION.SDK_INT>=27)return getSystemService(NotificationManager.class).isNotificationListenerAccessGranted(component);
        String enabled=Settings.Secure.getString(getContentResolver(),"enabled_notification_listeners");
        if(enabled==null)return false;
        for(String item:enabled.split(":")){ComponentName parsed=ComponentName.unflattenFromString(item);if(component.equals(parsed))return true;}
        return false;
    }
    @SuppressWarnings("deprecation")
    private boolean running(){for(ActivityManager.RunningServiceInfo s:getSystemService(ActivityManager.class).getRunningServices(20))if(s.service.getClassName().equals(OverlayService.class.getName()))return true;return false;}
    private void refreshState(){mark(notifyState,notificationAccess());mark(overlayState,Settings.canDrawOverlays(this));boolean spotify=SpotifyLyrics.loggedIn();spotifyState.setText(spotify?"연결됨":"로그인");spotifyState.setTextColor(spotify?0xffb6b1b7:0xfff4f2f4);spotifyState.setBackground(round(spotify?0x1effffff:0xff45434d,12));boolean on=running();toggle.setText(on?"가사 창 닫기":"가사 창 띄우기");toggle.setTextColor(on?0xfff4f2f4:BG);toggle.setBackground(round(on?0xff45434d:PINK,16));}
    @Override protected void onResume(){super.onResume();refreshState();}
    private void startOverlay(){
        if(!notificationAccess()){Toast.makeText(this,"먼저 알림 접근을 허용해 주세요.",Toast.LENGTH_LONG).show();return;}
        if(!Settings.canDrawOverlays(this)){Toast.makeText(this,"먼저 다른 앱 위에 표시를 허용해 주세요.",Toast.LENGTH_LONG).show();return;}
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},1);
        startForegroundService(new Intent(this,OverlayService.class));toggle.postDelayed(this::refreshState,400);
    }
}
