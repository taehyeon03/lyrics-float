package com.lyricsfloat.android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Unofficial: reads lyrics the way Spotify's web player does. A hidden WebView opens the signed-in web player, and the
 * bearer token it sends is reused for Spotify's lyrics endpoint. Device registration is blocked so the hidden player never
 * becomes a Spotify Connect target and takes playback away from the phone. Tokens stay in memory; nothing is sent anywhere but Spotify.
 */
final class SpotifyLyrics {
    private static final String UA="Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Safari/537.36";
    private static final Pattern TRACK=Pattern.compile("spotify:track:([A-Za-z0-9]{22})");
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static volatile String token,clientToken;private static volatile long tokenAt;private static WebView web;private static long loadAt;
    static final class Result{final List<OverlayService.Line> lines;final String status;Result(List<OverlayService.Line> l,String s){lines=l;status=s;}}

    static boolean loggedIn(){String c=CookieManager.getInstance().getCookie("https://open.spotify.com");return c!=null&&c.contains("sp_dc=");}
    static void reset(){token=null;clientToken=null;tokenAt=0;}
    static void logout(){reset();CookieManager.getInstance().removeAllCookies(null);CookieManager.getInstance().flush();main.post(()->{if(web!=null){web.destroy();web=null;}});}

    /** Must run on the main thread. Loads the web player only when a fresh token is needed, then unloads it. */
    @SuppressLint("SetJavaScriptEnabled")
    private static void loadPlayer(Context context){
        if(SystemClock.elapsedRealtime()-loadAt<20000)return;loadAt=SystemClock.elapsedRealtime();
        if(web==null){web=new WebView(context.getApplicationContext());web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);web.getSettings().setUserAgentString(UA);
            web.setWebViewClient(new WebViewClient(){
                @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                    String url=r.getUrl().toString();if(url.contains("track-playback")||url.contains("connect-state")||url.contains("/devices")){android.util.Log.i("LyricsFloat","Blocked web player device request");return new WebResourceResponse("application/json","utf-8",403,"Forbidden",Collections.emptyMap(),new java.io.ByteArrayInputStream(new byte[0]));}
                    String auth=null,client=null;for(Map.Entry<String,String> h:r.getRequestHeaders().entrySet()){String k=h.getKey().toLowerCase(Locale.ROOT);if(k.equals("authorization"))auth=h.getValue();else if(k.equals("client-token"))client=h.getValue();}
                    if(auth!=null&&auth.startsWith("Bearer ")&&r.getUrl().getHost()!=null&&r.getUrl().getHost().endsWith("spotify.com")){if(!auth.substring(7).equals(token))tokenAt=SystemClock.elapsedRealtime();token=auth.substring(7);if(client!=null)clientToken=client;
                        android.util.Log.i("LyricsFloat","Spotify token captured"+(client!=null?" with client token":""));main.post(()->{if(web!=null)web.loadUrl("about:blank");});}
                    return null;}});}
        web.loadUrl("https://open.spotify.com/");
    }
    private static String token(Context context)throws InterruptedException{
        if(token!=null&&SystemClock.elapsedRealtime()-tokenAt<45*60_000)return token;
        reset();main.post(()->loadPlayer(context));
        for(int i=0;i<40&&token==null;i++)Thread.sleep(500);
        return token;
    }
    private static final class Http{final int code;final String body;Http(int c,String b){code=c;body=b;}}
    private static Http get(String url,String bearer)throws IOException{
        HttpURLConnection c=(HttpURLConnection)new URL(url).openConnection();c.setConnectTimeout(10000);c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent",UA);c.setRequestProperty("Authorization","Bearer "+bearer);c.setRequestProperty("app-platform","WebPlayer");c.setRequestProperty("Accept","application/json");if(clientToken!=null)c.setRequestProperty("client-token",clientToken);
        try{int code=c.getResponseCode();if(code!=200)return new Http(code,"");try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){if(out.size()+n>2097152)throw new IOException("response too large");out.write(buf,0,n);}return new Http(code,new String(out.toByteArray(),StandardCharsets.UTF_8));}}finally{c.disconnect();}
    }
    private static String normalized(String s){return s==null?"":s.toLowerCase(Locale.ROOT).replaceAll("\\s*[\\(\\[][^\\)\\]]*[\\)\\]]","").replaceAll("[^\\p{L}\\p{N}]","");}
    /** Spotify's media session usually carries the track URI; otherwise the track is found with Spotify search. */
    private static String trackId(String mediaId,String song,String artist,long ms,String bearer)throws Exception{
        if(mediaId!=null){Matcher m=TRACK.matcher(mediaId);if(m.find())return m.group(1);}
        String q=URLEncoder.encode("track:"+song+" artist:"+artist.split(",")[0].trim(),"UTF-8");Http r=get("https://api.spotify.com/v1/search?type=track&limit=10&q="+q,bearer);
        if(r.code==401)throw new SecurityException();if(r.code!=200){android.util.Log.i("LyricsFloat","Spotify search HTTP "+r.code);return null;}
        JSONArray items=new JSONObject(r.body).getJSONObject("tracks").getJSONArray("items");String want=normalized(song);String best=null;long delta=Long.MAX_VALUE;
        for(int i=0;i<items.length();i++){JSONObject t=items.getJSONObject(i);long d=Math.abs(t.optLong("duration_ms")-ms);if(normalized(t.optString("name")).equals(want)&&d<=3000&&d<delta){best=t.optString("id");delta=d;}}
        return best;
    }
    static Result lookup(Context context,String mediaId,String song,String artist,long ms)throws Exception{
        for(int attempt=0;attempt<2;attempt++){
            String bearer=token(context);if(bearer==null)return new Result(Collections.emptyList(),"Spotify 토큰을 받지 못함");
            try{String id=trackId(mediaId,song,artist,ms,bearer);android.util.Log.i("LyricsFloat","Spotify track "+(id==null?"not found":id));if(id==null)return new Result(Collections.emptyList(),"Spotify에서 곡을 찾지 못함");
                Http r=get("https://spclient.wg.spotify.com/color-lyrics/v2/track/"+id+"?format=json&vocalRemoval=false&market=from_token",bearer);android.util.Log.i("LyricsFloat","Spotify lyrics HTTP "+r.code);
                if(r.code==401){reset();continue;}if(r.code==404)return new Result(Collections.emptyList(),"Spotify에 가사가 없습니다");if(r.code!=200)return new Result(Collections.emptyList(),"Spotify 가사 HTTP "+r.code);
                JSONObject lyrics=new JSONObject(r.body).getJSONObject("lyrics");if(!"LINE_SYNCED".equals(lyrics.optString("syncType")))return new Result(Collections.emptyList(),"동기화된 가사가 없습니다");
                JSONArray rows=lyrics.getJSONArray("lines");List<OverlayService.Line> out=new ArrayList<>();
                for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);String words=row.optString("words").trim();out.add(new OverlayService.Line(Long.parseLong(row.optString("startTimeMs","0")),words.equals("♪")?"":words));}
                return new Result(out,"");
            }catch(SecurityException e){reset();}
        }
        return new Result(Collections.emptyList(),"Spotify 인증 실패 · 다시 로그인해 주세요");
    }
}
