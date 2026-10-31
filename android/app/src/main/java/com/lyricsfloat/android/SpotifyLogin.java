package com.lyricsfloat.android;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/** The user signs in to Spotify's own web page; the app never sees the password, only the session cookie Spotify sets. */
public final class SpotifyLogin extends Activity {
    private WebView web;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);getWindow().setStatusBarColor(Color.rgb(28,29,33));
        CookieManager.getInstance().setAcceptCookie(true);
        web=new WebView(this);web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);setContentView(web);
        web.setWebViewClient(new WebViewClient(){@Override public void onPageFinished(WebView v,String url){if(SpotifyLyrics.loggedIn()){CookieManager.getInstance().flush();SpotifyLyrics.reset();Toast.makeText(SpotifyLogin.this,"Spotify에 연결했습니다",Toast.LENGTH_SHORT).show();finish();}}});
        web.loadUrl("https://accounts.spotify.com/login?continue=https%3A%2F%2Fopen.spotify.com%2F");
    }
    @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
    @Override protected void onDestroy(){web.destroy();super.onDestroy();}
}
