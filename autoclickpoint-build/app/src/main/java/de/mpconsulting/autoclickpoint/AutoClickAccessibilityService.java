package de.mpconsulting.autoclickpoint;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class AutoClickAccessibilityService extends AccessibilityService {
    private static final String PREFS="autoclick_prefs", KEY_X="x", KEY_Y="y", KEY_I="interval";
    private static final long[] INTERVALS={0,10,25,50,100,250,500,1000};
    private final Handler handler=new Handler(Looper.getMainLooper());
    private WindowManager wm;
    private View target;
    private LinearLayout controls;
    private WindowManager.LayoutParams targetLp;
    private Button startPause;
    private TextView intervalLabel;
    private SharedPreferences prefs;
    private boolean running=false, gestureInFlight=false;
    private int intervalIndex=2;
    private float xFraction=.5f,yFraction=.45f;

    @Override protected void onServiceConnected(){
        prefs=getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        xFraction=prefs.getFloat(KEY_X,.5f); yFraction=prefs.getFloat(KEY_Y,.45f);
        intervalIndex=Math.max(0,Math.min(INTERVALS.length-1,prefs.getInt(KEY_I,2)));
        wm=(WindowManager)getSystemService(WINDOW_SERVICE);
        showTarget(); showControls();
    }

    private int dp(float v){ return Math.round(v*getResources().getDisplayMetrics().density); }
    private GradientDrawable bg(int color,float radius){ GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d; }
    private int sw(){ return getResources().getDisplayMetrics().widthPixels; }
    private int sh(){ return getResources().getDisplayMetrics().heightPixels; }
    private int clamp(int v,int min,int max){ return Math.max(min,Math.min(max,v)); }

    private void showTarget(){
        TextView m=new TextView(this); m.setText("+"); m.setTextColor(Color.WHITE); m.setTextSize(27); m.setGravity(Gravity.CENTER);
        GradientDrawable targetBg=bg(Color.rgb(215,25,32),100); targetBg.setStroke(dp(2),Color.WHITE); m.setBackground(targetBg); m.setElevation(dp(8));
        int size=dp(58);
        targetLp=new WindowManager.LayoutParams(size,size,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        targetLp.gravity=Gravity.TOP|Gravity.START;
        targetLp.x=clamp(Math.round(xFraction*sw()-size/2f),0,Math.max(0,sw()-size));
        targetLp.y=clamp(Math.round(yFraction*sh()-size/2f),0,Math.max(0,sh()-size));
        m.setOnTouchListener(new View.OnTouchListener(){ float dx,dy; int sx,sy; public boolean onTouch(View v,MotionEvent e){ if(running)return false; switch(e.getActionMasked()){
            case MotionEvent.ACTION_DOWN: dx=e.getRawX();dy=e.getRawY();sx=targetLp.x;sy=targetLp.y;return true;
            case MotionEvent.ACTION_MOVE: targetLp.x=clamp(sx+Math.round(e.getRawX()-dx),0,Math.max(0,sw()-targetLp.width)); targetLp.y=clamp(sy+Math.round(e.getRawY()-dy),0,Math.max(0,sh()-targetLp.height)); wm.updateViewLayout(target,targetLp);return true;
            case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL: savePosition();return true; default:return false; } }});
        target=m; wm.addView(target,targetLp);
    }

    private Button small(String text){ Button b=new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setTextSize(18); b.setAllCaps(false); b.setPadding(0,0,0,0); b.setBackground(bg(Color.rgb(45,47,53),18)); return b; }

    private void showControls(){
        LinearLayout p=new LinearLayout(this); p.setOrientation(LinearLayout.HORIZONTAL); p.setGravity(Gravity.CENTER_VERTICAL); p.setPadding(dp(8),dp(6),dp(8),dp(6)); p.setBackground(bg(Color.argb(238,16,17,20),22)); p.setElevation(dp(12));
        Button minus=small("−"); minus.setOnClickListener(v->changeInterval(-1)); p.addView(minus,new LinearLayout.LayoutParams(dp(44),dp(44)));
        intervalLabel=new TextView(this); intervalLabel.setTextColor(Color.WHITE); intervalLabel.setTextSize(13); intervalLabel.setGravity(Gravity.CENTER); updateInterval(); p.addView(intervalLabel,new LinearLayout.LayoutParams(dp(88),dp(44)));
        Button plus=small("+"); plus.setOnClickListener(v->changeInterval(1)); p.addView(plus,new LinearLayout.LayoutParams(dp(44),dp(44)));
        startPause=new Button(this); startPause.setText("START"); startPause.setTextColor(Color.WHITE); startPause.setTextSize(13); startPause.setAllCaps(false); startPause.setBackground(bg(Color.rgb(36,135,74),18)); startPause.setOnClickListener(v->{ if(running)pause(); else start(); });
        LinearLayout.LayoutParams sLp=new LinearLayout.LayoutParams(dp(100),dp(44)); sLp.setMargins(dp(8),0,0,0); p.addView(startPause,sLp);
        WindowManager.LayoutParams lp=new WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.WRAP_CONTENT,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,PixelFormat.TRANSLUCENT);
        lp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL; lp.y=dp(28); controls=p; wm.addView(controls,lp);
    }

    private void changeInterval(int d){ if(running)return; intervalIndex=Math.max(0,Math.min(INTERVALS.length-1,intervalIndex+d)); prefs.edit().putInt(KEY_I,intervalIndex).apply(); updateInterval(); }
    private void updateInterval(){ if(intervalLabel!=null){ long ms=INTERVALS[intervalIndex]; intervalLabel.setText(ms==0?"MAX":ms+" ms"); } }

    private void start(){ if(running)return; savePosition(); running=true; startPause.setText("PAUSE"); startPause.setBackground(bg(Color.rgb(215,25,32),18)); setTargetTouchable(false); ((TextView)target).setText("•"); target.setAlpha(.6f); schedule(0); }
    private void pause(){ running=false; handler.removeCallbacksAndMessages(null); gestureInFlight=false; if(startPause!=null){ startPause.setText("START"); startPause.setBackground(bg(Color.rgb(36,135,74),18)); } if(target!=null){ setTargetTouchable(true); ((TextView)target).setText("+"); target.setAlpha(1f); } }
    private void setTargetTouchable(boolean touch){ if(target==null)return; int flags=WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS; if(!touch)flags|=WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE; targetLp.flags=flags; wm.updateViewLayout(target,targetLp); }
    private void schedule(long delay){ if(running)handler.postDelayed(()->{ if(running&&!gestureInFlight)tap(); },Math.max(0,delay)); }

    private void tap(){
        if(!running||gestureInFlight)return;
        float x=targetLp.x+targetLp.width/2f,y=targetLp.y+targetLp.height/2f;
        Path path=new Path(); path.moveTo(x,y);
        GestureDescription g=new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,1)).build();
        gestureInFlight=true;
        boolean accepted=dispatchGesture(g,new GestureResultCallback(){
            @Override public void onCompleted(GestureDescription d){ gestureInFlight=false; if(running)schedule(INTERVALS[intervalIndex]); }
            @Override public void onCancelled(GestureDescription d){ gestureInFlight=false; if(running)schedule(Math.max(10,INTERVALS[intervalIndex])); }
        },handler);
        if(!accepted){ gestureInFlight=false; schedule(Math.max(25,INTERVALS[intervalIndex])); }
    }

    private void savePosition(){ if(targetLp==null||prefs==null)return; xFraction=Math.max(0f,Math.min(1f,(targetLp.x+targetLp.width/2f)/Math.max(1f,sw()))); yFraction=Math.max(0f,Math.min(1f,(targetLp.y+targetLp.height/2f)/Math.max(1f,sh()))); prefs.edit().putFloat(KEY_X,xFraction).putFloat(KEY_Y,yFraction).apply(); }
    @Override public void onConfigurationChanged(Configuration c){ super.onConfigurationChanged(c); if(target==null)return; targetLp.x=clamp(Math.round(xFraction*sw()-targetLp.width/2f),0,Math.max(0,sw()-targetLp.width)); targetLp.y=clamp(Math.round(yFraction*sh()-targetLp.height/2f),0,Math.max(0,sh()-targetLp.height)); wm.updateViewLayout(target,targetLp); }
    @Override public void onAccessibilityEvent(AccessibilityEvent event){}
    @Override public void onInterrupt(){ pause(); }
    @Override public void onDestroy(){ running=false; handler.removeCallbacksAndMessages(null); if(wm!=null){ if(target!=null)try{wm.removeView(target);}catch(Exception ignored){} if(controls!=null)try{wm.removeView(controls);}catch(Exception ignored){} } super.onDestroy(); }
}
