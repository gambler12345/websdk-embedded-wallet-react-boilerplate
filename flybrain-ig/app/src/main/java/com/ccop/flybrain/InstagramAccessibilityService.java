package com.ccop.flybrain;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class InstagramAccessibilityService extends AccessibilityService {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private SharedPreferences prefs; private WindowManager wm; private LinearLayout overlay; private TextView status;
    private boolean instagramActive=false, paused=false; private long lastScrollEvent=0;
    private final Runnable loop=new Runnable(){@Override public void run(){
        if(prefs!=null){boolean auto=prefs.getBoolean("ig_auto",true);int interval=Math.max(1200,Math.min(15000,prefs.getInt("ig_interval",3500)));if(instagramActive&&auto&&!paused)swipeNext();updateOverlay();handler.postDelayed(this,interval);}else handler.postDelayed(this,2500);
    }};

    @Override protected void onServiceConnected(){super.onServiceConnected();prefs=getSharedPreferences("flybrain",MODE_PRIVATE);wm=(WindowManager)getSystemService(WINDOW_SERVICE);createOverlay();handler.removeCallbacks(loop);handler.postDelayed(loop,1200);}
    @Override public void onAccessibilityEvent(AccessibilityEvent event){
        if(event==null||event.getPackageName()==null)return;instagramActive="com.instagram.android".contentEquals(event.getPackageName());
        if(overlay!=null)overlay.setVisibility(instagramActive&&prefs.getBoolean("ig_overlay",true)?View.VISIBLE:View.GONE);if(!instagramActive)return;
        if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_SCROLLED){long now=System.currentTimeMillis();if(now-lastScrollEvent>800){lastScrollEvent=now;int posts=prefs.getInt("ig_posts",0)+1;int score=Math.min(100,prefs.getInt("ig_score",35)+4);prefs.edit().putInt("ig_posts",posts).putInt("ig_score",score).apply();}}
        else if(event.getEventType()==AccessibilityEvent.TYPE_VIEW_CLICKED){CharSequence d=event.getContentDescription();String s=(d!=null?d.toString():"").toLowerCase();if((s.contains("like")||s.contains("gefällt mir"))&&!s.contains("unlike")&&!s.contains("nicht mehr"))registerLike();}
        updateOverlay();
    }
    @Override public void onInterrupt(){}
    private void swipeNext(){if(!instagramActive)return;int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;Path p=new Path();p.moveTo(w*.52f,h*.76f);p.lineTo(w*.52f,h*.24f);dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,0,340)).build(),null,null);}
    private void likeCurrent(){AccessibilityNodeInfo root=getRootInActiveWindow();if(root!=null&&clickLikeNode(root)){registerLike();return;}int w=getResources().getDisplayMetrics().widthPixels,h=getResources().getDisplayMetrics().heightPixels;tap(w*.5f,h*.47f,0);tap(w*.5f,h*.47f,170);registerLike();}
    private boolean clickLikeNode(AccessibilityNodeInfo node){if(node==null)return false;CharSequence d=node.getContentDescription(),t=node.getText();String s=((d!=null?d.toString():"")+" "+(t!=null?t.toString():"")).trim().toLowerCase();boolean like=s.equals("like")||s.equals("gefällt mir")||s.startsWith("like ")||s.startsWith("gefällt mir ");boolean unlike=s.contains("unlike")||s.contains("nicht mehr")||s.contains("remove like");if(like&&!unlike&&node.isClickable())return node.performAction(AccessibilityNodeInfo.ACTION_CLICK);for(int i=0;i<node.getChildCount();i++)if(clickLikeNode(node.getChild(i)))return true;return false;}
    private void tap(float x,float y,long delay){Path p=new Path();p.moveTo(x,y);dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(p,delay,60)).build(),null,null);}
    private void registerLike(){int likes=prefs.getInt("ig_likes",0)+1,score=Math.min(100,prefs.getInt("ig_score",35)+8);prefs.edit().putInt("ig_likes",likes).putInt("ig_score",score).apply();updateOverlay();}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setTextColor(Color.WHITE);b.setTextSize(12);b.setAllCaps(false);b.setBackgroundColor(Color.argb(220,18,24,34));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,46,1f);lp.setMargins(3,0,3,0);b.setLayoutParams(lp);return b;}
    private void createOverlay(){if(overlay!=null||wm==null)return;overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.HORIZONTAL);overlay.setGravity(Gravity.CENTER_VERTICAL);overlay.setPadding(6,4,6,4);overlay.setBackgroundColor(Color.argb(205,5,9,15));status=new TextView(this);status.setTextColor(Color.WHITE);status.setTextSize(11);status.setPadding(7,0,7,0);overlay.addView(status,new LinearLayout.LayoutParams(0,46,1.65f));Button pause=button("⏯"),next=button("↑"),like=button("♥"),back=button("FlyBrain");overlay.addView(pause);overlay.addView(next);overlay.addView(like);overlay.addView(back,new LinearLayout.LayoutParams(0,46,1.35f));pause.setOnClickListener(v->{paused=!paused;updateOverlay();});next.setOnClickListener(v->swipeNext());like.setOnClickListener(v->likeCurrent());back.setOnClickListener(v->{Intent i=getPackageManager().getLaunchIntentForPackage(getPackageName());if(i!=null){i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);startActivity(i);}});WindowManager.LayoutParams p=new WindowManager.LayoutParams(WindowManager.LayoutParams.MATCH_PARENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,PixelFormat.TRANSLUCENT);p.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL;wm.addView(overlay,p);overlay.setVisibility(View.GONE);updateOverlay();}
    private void updateOverlay(){if(status==null||prefs==null)return;int posts=prefs.getInt("ig_posts",0),likes=prefs.getInt("ig_likes",0),score=prefs.getInt("ig_score",35);boolean auto=prefs.getBoolean("ig_auto",true);status.setText("🪰 IG  "+score+"% · "+posts+" Posts · "+likes+" ♥"+(paused?" · PAUSE":auto?" · AUTO":" · MANUELL"));if(overlay!=null&&instagramActive)overlay.setVisibility(prefs.getBoolean("ig_overlay",true)?View.VISIBLE:View.GONE);}
    @Override public void onDestroy(){handler.removeCallbacks(loop);if(overlay!=null&&wm!=null){try{wm.removeView(overlay);}catch(Exception ignored){}}overlay=null;super.onDestroy();}
}
