package com.ccop.flybrain;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {
  private WebView webView;
  @Override public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(Color.rgb(8,12,18));getWindow().setNavigationBarColor(Color.rgb(8,12,18));webView=new WebView(this);WebSettings s=webView.getSettings();s.setJavaScriptEnabled(true);s.setDomStorageEnabled(true);s.setDatabaseEnabled(true);s.setMediaPlaybackRequiresUserGesture(false);s.setAllowFileAccess(true);s.setAllowContentAccess(true);s.setLoadWithOverviewMode(true);s.setUseWideViewPort(true);webView.setWebChromeClient(new WebChromeClient());webView.setWebViewClient(new WebViewClient(){@Override public boolean shouldOverrideUrlLoading(WebView v,String u){if(u.startsWith("file:///android_asset/"))return false;try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));}catch(Exception ignored){}return true;}});webView.addJavascriptInterface(new NativeBridge(),"Native");webView.loadUrl("file:///android_asset/index.html");setContentView(webView);}
  public class NativeBridge{
    @JavascriptInterface public void shareText(String text){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_TEXT,text);startActivity(Intent.createChooser(i,"Session teilen"));});}
    @JavascriptInterface public void openUsageAccess(){runOnUiThread(()->{try{startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));}catch(Exception e){Toast.makeText(MainActivity.this,"Usage Access konnte nicht geöffnet werden.",Toast.LENGTH_SHORT).show();}});}
    @JavascriptInterface public void toast(String text){runOnUiThread(()->Toast.makeText(MainActivity.this,text,Toast.LENGTH_SHORT).show());}
  }
  @Override public void onBackPressed(){if(webView!=null&&webView.canGoBack())webView.goBack();else super.onBackPressed();}
  @Override protected void onPause(){super.onPause();if(webView!=null)webView.onPause();}
  @Override protected void onResume(){super.onResume();if(webView!=null)webView.onResume();}
}
