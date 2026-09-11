package com.ccop.usbcpowermonitor;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Mobile-first history chart. POWER keeps the sign: charge above zero, discharge below zero. */
public class HistoryChartView extends View {
    public enum Mode { POWER, SOC }
    private final Paint grid=new Paint(Paint.ANTI_ALIAS_FLAG), text=new Paint(Paint.ANTI_ALIAS_FLAG),
            green=new Paint(Paint.ANTI_ALIAS_FLAG), blue=new Paint(Paint.ANTI_ALIAS_FLAG), yellow=new Paint(Paint.ANTI_ALIAS_FLAG), red=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<HistoryStore.Point> points=new ArrayList<>(); private Mode mode;

    public HistoryChartView(Context c,Mode m){super(c);mode=m;float d=getResources().getDisplayMetrics().density,sd=getResources().getDisplayMetrics().scaledDensity;
        grid.setColor(Color.rgb(39,55,68));grid.setStrokeWidth(d);text.setColor(Color.rgb(170,184,198));text.setTextSize(11f*sd);
        setup(green,Color.rgb(49,216,134),2.6f*d);setup(blue,Color.rgb(34,157,255),2.4f*d);setup(yellow,Color.rgb(255,202,72),2.2f*d);setup(red,Color.rgb(255,91,91),1.2f*d);}
    private void setup(Paint p,int color,float width){p.setColor(color);p.setStrokeWidth(width);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);}
    public void setPoints(List<HistoryStore.Point> p){points=p==null?new ArrayList<>():p;invalidate();}

    @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();c.drawColor(Color.rgb(7,13,18));float L=58,R=w-12,T=38,B=h-36;if(R<=L||B<=T)return;
        for(int i=0;i<=4;i++){float y=T+(B-T)*i/4f;c.drawLine(L,y,R,y,grid);}for(int i=0;i<=4;i++){float x=L+(R-L)*i/4f;c.drawLine(x,T,x,B,grid);}
        if(points.size()<2){c.drawText("Noch nicht genug Messpunkte",L+10,T+28,text);return;}long min=points.get(0).ts,max=points.get(points.size()-1).ts;if(max<=min)max=min+1;
        if(mode==Mode.POWER)drawPower(c,L,R,T,B,min,max);else drawSoc(c,L,R,T,B,min,max);
        SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.GERMANY);for(int i=0;i<=4;i++){long t=min+(max-min)*i/4;String s=f.format(new Date(t));float x=L+(R-L)*i/4f;c.drawText(s,Math.max(L,Math.min(R-text.measureText(s),x-text.measureText(s)/2)),h-10,text);}
    }

    private void drawPower(Canvas c,float L,float R,float T,float B,long minT,long maxT){double lo=0,hi=0,sum=0;int n=0;for(HistoryStore.Point p:points){if(finite(p.batteryW)){lo=Math.min(lo,p.batteryW);hi=Math.max(hi,p.batteryW);sum+=p.batteryW;n++;}if(finite(p.sourceW))hi=Math.max(hi,p.sourceW);}if(hi<.5)hi=.5;if(lo>-.5)lo=-.5;double span=hi-lo;hi+=span*.08;lo-=span*.08;
        float zero=yFor(0,lo,hi,T,B);c.drawLine(L,zero,R,zero,grid);c.drawText(String.format(Locale.GERMANY,"+%.1f W",hi),4,T+10,text);c.drawText("0 W",12,zero-4,text);c.drawText(String.format(Locale.GERMANY,"%.1f W",lo),4,B,text);
        Path battery=new Path(),source=new Path();boolean bs=false,ss=false;for(HistoryStore.Point p:points){float x=xFor(p.ts,minT,maxT,L,R);if(finite(p.batteryW)){float y=yFor(p.batteryW,lo,hi,T,B);if(!bs){battery.moveTo(x,y);bs=true;}else battery.lineTo(x,y);}if(finite(p.sourceW)){float y=yFor(p.sourceW,lo,hi,T,B);if(!ss){source.moveTo(x,y);ss=true;}else source.lineTo(x,y);}}
        if(bs)c.drawPath(battery,green);if(ss)c.drawPath(source,blue);drawEvents(c,L,R,T,B,minT,maxT);
        double avg=n>0?sum/n:0;c.drawText("Akku netto",L,T-17,green);c.drawText("Quelle",L+100,T-17,blue);String stat=String.format(Locale.GERMANY,"min %.1f · Ø %.1f · max %.1f W",lo,avg,hi);c.drawText(stat,Math.max(L,R-text.measureText(stat)),T-17,text);
    }
    private void drawSoc(Canvas c,float L,float R,float T,float B,long minT,long maxT){c.drawText("100 %",4,T+10,text);c.drawText("50 %",10,(T+B)/2,text);c.drawText("0 %",18,B,text);drawSocSeries(c,0,green,L,R,T,B,minT,maxT);drawSocSeries(c,1,blue,L,R,T,B,minT,maxT);drawSocSeries(c,2,yellow,L,R,T,B,minT,maxT);drawEvents(c,L,R,T,B,minT,maxT);c.drawText("Handy",L,T-17,green);c.drawText("Quelle",L+70,T-17,blue);c.drawText("Gesamt",L+145,T-17,yellow);}
    private void drawSocSeries(Canvas c,int which,Paint paint,float L,float R,float T,float B,long minT,long maxT){Path path=new Path();boolean started=false;for(HistoryStore.Point p:points){double v=which==0?p.phonePct:(which==1?p.sourceSoc:p.aggregateSoc);if(!finite(v))continue;v=Math.max(0,Math.min(100,v));float x=xFor(p.ts,minT,maxT,L,R),y=B-(B-T)*(float)(v/100.0);if(!started){path.moveTo(x,y);started=true;}else path.lineTo(x,y);}if(started)c.drawPath(path,paint);}
    private void drawEvents(Canvas c,float L,float R,float T,float B,long minT,long maxT){boolean last=points.get(0).plugged;for(int i=1;i<points.size();i++){HistoryStore.Point p=points.get(i);if(p.plugged!=last||!(p.event==null||p.event.isEmpty())){float x=xFor(p.ts,minT,maxT,L,R);c.drawLine(x,T,x,B,red);}last=p.plugged;}}
    private float xFor(long ts,long min,long max,float L,float R){return L+(R-L)*(ts-min)/(float)(max-min);}private float yFor(double v,double lo,double hi,float T,float B){return B-(B-T)*(float)((v-lo)/(hi-lo));}private boolean finite(double v){return !Double.isNaN(v)&&!Double.isInfinite(v);}
}
