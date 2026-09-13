package com.ccop.flybrain;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
    private WebView webView;
    private SharedPreferences prefs;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("flybrain", MODE_PRIVATE);
        applyImmersive();
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(5, 8, 14));
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false); s.setAllowFileAccess(true); s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(false); s.setUseWideViewPort(true); s.setBuiltInZoomControls(false); s.setDisplayZoomControls(false);
        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient(){ @Override public boolean shouldOverrideUrlLoading(WebView view, String url){
            if(url.startsWith("file:///android_asset/")) return false;
            try{ startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }catch(Exception ignored){}
            return true;
        }});
        webView.addJavascriptInterface(new NativeBridge(), "Native");
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    private void applyImmersive(){
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private boolean accessibilityEnabled(){
        ComponentName expected = new ComponentName(this, InstagramAccessibilityService.class);
        String enabled = Settings.Secure.getString(getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if(enabled==null) return false;
        TextUtils.SimpleStringSplitter splitter = new TextUtils.SimpleStringSplitter(':'); splitter.setString(enabled);
        while(splitter.hasNext()){ ComponentName c=ComponentName.unflattenFromString(splitter.next()); if(c!=null&&c.equals(expected)) return true; }
        return false;
    }

    public class NativeBridge {
        @JavascriptInterface public void shareText(String text){ runOnUiThread(()->{ Intent send=new Intent(Intent.ACTION_SEND); send.setType("text/plain"); send.putExtra(Intent.EXTRA_TEXT,text); startActivity(Intent.createChooser(send,"Session teilen")); }); }
        @JavascriptInterface public void openUsageAccess(){ runOnUiThread(()->{ try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(Exception e){toast("Usage Access konnte nicht geöffnet werden.");} }); }
        @JavascriptInterface public void openAccessibilitySettings(){ runOnUiThread(()->{ try{startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));}catch(Exception e){toast("Bedienungshilfen konnten nicht geöffnet werden.");} }); }
        @JavascriptInterface public boolean isInstagramAccessibilityEnabled(){ return accessibilityEnabled(); }
        @JavascriptInterface public void configureInstagram(boolean autoScroll,int intervalMs,boolean overlay){ int safe=Math.max(1200,Math.min(15000,intervalMs)); prefs.edit().putBoolean("ig_auto",autoScroll).putInt("ig_interval",safe).putBoolean("ig_overlay",overlay).apply(); }
        @JavascriptInterface public String getInstagramStats(){ return "{\"posts\":"+prefs.getInt("ig_posts",0)+",\"likes\":"+prefs.getInt("ig_likes",0)+",\"score\":"+prefs.getInt("ig_score",35)+",\"auto\":"+prefs.getBoolean("ig_auto",true)+",\"interval\":"+prefs.getInt("ig_interval",3500)+"}"; }
        @JavascriptInterface public void resetInstagramStats(){ prefs.edit().putInt("ig_posts",0).putInt("ig_likes",0).putInt("ig_score",35).apply(); }
        @JavascriptInterface public void openInstagram(){ runOnUiThread(()->{
            if(!accessibilityEnabled()) Toast.makeText(MainActivity.this,"Für die Steuerung zuerst den FlyBrain Instagram-Assistenten unter Bedienungshilfen aktivieren.",Toast.LENGTH_LONG).show();
            Intent launch=getPackageManager().getLaunchIntentForPackage("com.instagram.android");
            if(launch!=null){launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);}else{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id=com.instagram.android")));}catch(Exception e){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.instagram.com/")));}}
        }); }
        @JavascriptInterface public void setFullscreen(boolean full){ runOnUiThread(()->{ if(full)applyImmersive(); else getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE); }); }
        @JavascriptInterface public void toast(String text){ runOnUiThread(()->toast(text)); }
    }

    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_SHORT).show();}
    @Override public void onWindowFocusChanged(boolean hasFocus){super.onWindowFocusChanged(hasFocus);if(hasFocus)applyImmersive();}
    @Override protected void onResume(){super.onResume();applyImmersive();if(webView!=null){webView.onResume();webView.postDelayed(()->webView.evaluateJavascript("window.onNativeResume&&window.onNativeResume()",null),250);}}
    @Override protected void onPause(){super.onPause();if(webView!=null)webView.onPause();}
    @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
}
