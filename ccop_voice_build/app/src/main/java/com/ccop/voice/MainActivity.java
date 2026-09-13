package com.ccop.voice;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.webkit.*;
import android.webkit.JavascriptInterface;
import android.webkit.MimeTypeMap;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.widget.Toast;
import android.webkit.ValueCallback;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {
  private WebView web;
  private ValueCallback<Uri[]> fileCallback;
  private static final int FILE_REQ=3412;
  private static final String ORIGIN="https://ccop.local";

  @Override public void onCreate(Bundle b){ super.onCreate(b);
    web=new WebView(this); setContentView(web);
    WebSettings s=web.getSettings();
    s.setJavaScriptEnabled(true); s.setDomStorageEnabled(true); s.setDatabaseEnabled(true);
    s.setMediaPlaybackRequiresUserGesture(false); s.setAllowFileAccess(false); s.setAllowContentAccess(true);
    s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW); s.setCacheMode(WebSettings.LOAD_DEFAULT);
    s.setJavaScriptCanOpenWindowsAutomatically(true);
    if(android.os.Build.VERSION.SDK_INT>=26) s.setSafeBrowsingEnabled(true);
    web.addJavascriptInterface(new NativeBridge(),"CcOpAndroid");
    web.setWebViewClient(new WebViewClient(){
      @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req){
        Uri u=req.getUrl(); if("ccop.local".equals(u.getHost())) return assetResponse(u.getPath()); return super.shouldInterceptRequest(view,req);
      }
      @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req){
        Uri u=req.getUrl(); if("ccop.local".equals(u.getHost())) return false;
        try{startActivity(new Intent(Intent.ACTION_VIEW,u));}catch(Exception ignored){} return true;
      }
    });
    web.setWebChromeClient(new WebChromeClient(){
      @Override public void onPermissionRequest(PermissionRequest r){ runOnUiThread(()->r.grant(r.getResources())); }
      @Override public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params){
        if(fileCallback!=null)fileCallback.onReceiveValue(null); fileCallback=cb;
        try{Intent i=params.createIntent();i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,FILE_REQ);}catch(Exception e){fileCallback=null;Toast.makeText(MainActivity.this,"Dateiauswahl nicht verfügbar",Toast.LENGTH_SHORT).show();return false;}return true;
      }
    });
    if(android.os.Build.VERSION.SDK_INT>=23 && checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},10);
    web.loadUrl(ORIGIN+"/index.html");
  }

  private WebResourceResponse assetResponse(String path){
    try{
      if(path==null||path.equals("/")||path.isEmpty())path="/index.html";
      String rel="www"+path; InputStream in=getAssets().open(rel);
      String ext=MimeTypeMap.getFileExtensionFromUrl(path); String mime=MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
      if(mime==null){if(path.endsWith(".wasm"))mime="application/wasm";else if(path.endsWith(".json"))mime="application/json";else mime="application/octet-stream";}
      String enc=(mime.startsWith("text/")||mime.contains("javascript")||mime.contains("json")||mime.contains("svg"))?"UTF-8":null;
      WebResourceResponse resp=new WebResourceResponse(mime,enc,in);
      Map<String,String> headers=new HashMap<>(); headers.put("Access-Control-Allow-Origin",ORIGIN); headers.put("Cache-Control","public, max-age=31536000"); resp.setResponseHeaders(headers);
      return resp;
    }catch(Exception e){return null;}
  }

  @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){super.onActivityResult(requestCode,resultCode,data);if(requestCode==FILE_REQ&&fileCallback!=null){Uri[] result=null;if(resultCode==RESULT_OK&&data!=null&&data.getData()!=null)result=new Uri[]{data.getData()};fileCallback.onReceiveValue(result);fileCallback=null;}}
  @Override public void onBackPressed(){ if(web.canGoBack())web.goBack(); else super.onBackPressed(); }

  public class NativeBridge {
    @JavascriptInterface public String fetchUrl(String url){
      JSONObject out=new JSONObject(); HttpURLConnection c=null;
      try{
        URL current=new URL(url); String proto=current.getProtocol().toLowerCase(Locale.ROOT); if(!proto.equals("http")&&!proto.equals("https"))throw new Exception("Nur HTTP/HTTPS erlaubt");
        c=(HttpURLConnection)current.openConnection(); c.setInstanceFollowRedirects(true); c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent","Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/140 Mobile Safari/537.36 CcOp/1.1"); c.setRequestProperty("Accept-Language","de-DE,de;q=0.9,en;q=0.7"); c.setRequestProperty("Accept","text/html,application/xhtml+xml,text/plain,application/json;q=0.9,*/*;q=0.5");
        int code=c.getResponseCode(); InputStream input=code>=400?c.getErrorStream():c.getInputStream(); if(input==null)throw new Exception("Leere Antwort");
        BufferedReader br=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8)); StringBuilder body=new StringBuilder(); String line; int max=3_000_000;
        while((line=br.readLine())!=null&&body.length()<max)body.append(line).append('\n'); br.close();
        out.put("ok",code>=200&&code<400); out.put("status",code); out.put("body",body.toString()); out.put("finalUrl",c.getURL().toString()); if(code>=400)out.put("error","HTTP "+code);
      }catch(Exception e){try{out.put("ok",false);out.put("status",0);out.put("error",e.getMessage()==null?e.toString():e.getMessage());}catch(Exception ignored){}}
      finally{if(c!=null)c.disconnect();}
      return out.toString();
    }

    @JavascriptInterface public String postJson(String url,String json){
      JSONObject out=new JSONObject(); HttpURLConnection c=null;
      try{
        URL target=new URL(url); String proto=target.getProtocol().toLowerCase(Locale.ROOT); if(!proto.equals("http")&&!proto.equals("https"))throw new Exception("Nur HTTP/HTTPS erlaubt");
        c=(HttpURLConnection)target.openConnection(); c.setRequestMethod("POST"); c.setDoOutput(true); c.setInstanceFollowRedirects(true); c.setConnectTimeout(15000); c.setReadTimeout(120000);
        c.setRequestProperty("Content-Type","application/json; charset=utf-8"); c.setRequestProperty("Accept","application/json");
        try(OutputStream os=c.getOutputStream()){os.write(json.getBytes(StandardCharsets.UTF_8));}
        int code=c.getResponseCode(); InputStream input=code>=400?c.getErrorStream():c.getInputStream(); if(input==null)throw new Exception("Leere Antwort");
        BufferedReader br=new BufferedReader(new InputStreamReader(input,StandardCharsets.UTF_8)); StringBuilder body=new StringBuilder(); String line; int max=5_000_000; while((line=br.readLine())!=null&&body.length()<max)body.append(line).append('\n'); br.close();
        out.put("ok",code>=200&&code<400); out.put("status",code); out.put("body",body.toString()); if(code>=400)out.put("error","HTTP "+code);
      }catch(Exception e){try{out.put("ok",false);out.put("status",0);out.put("error",e.getMessage()==null?e.toString():e.getMessage());}catch(Exception ignored){}} finally{if(c!=null)c.disconnect();}
      return out.toString();
    }
  }
}
