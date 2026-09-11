package com.ccop.usbcpowermonitor;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.*;

public class DeviceSocChartView extends View {
    private final Paint grid=new Paint(Paint.ANTI_ALIAS_FLAG),text=new Paint(Paint.ANTI_ALIAS_FLAG),line=new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<HistoryStore.Point> points=new ArrayList<>(); private String profileId=""; private String label="Gerät";
    public DeviceSocChartView(Context c){super(c);grid.setColor(Color.rgb(43,56,68));grid.setStrokeWidth(1f);text.setColor(Color.rgb(151,164,178));text.setTextSize(10f*getResources().getDisplayMetrics().scaledDensity);line.setColor(Color.rgb(72,229,139));line.setStrokeWidth(2.4f*getResources().getDisplayMetrics().density);line.setStyle(Paint.Style.STROKE);}
    public void setData(List<HistoryStore.Point> p,String id,String name){points=p==null?new ArrayList<>():p;profileId=id==null?"":id;label=name==null?"Gerät":name;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);int w=getWidth(),h=getHeight();c.drawColor(Color.rgb(8,13,17));float l=48,r=w-10,t=12,b=h-28;for(int i=0;i<=4;i++){float y=t+(b-t)*i/4f;c.drawLine(l,y,r,y,grid);}c.drawText("100%",4,t+10,text);c.drawText("0%",12,b,text);c.drawText(label,l+8,t+13,line);if(points.size()<2){c.drawText("Noch nicht genug Gerätedaten",l+8,t+36,text);return;}long min=points.get(0).ts,max=points.get(points.size()-1).ts;if(max<=min)max=min+1;Path pth=new Path();boolean started=false;for(HistoryStore.Point p:points){double v=p.socFor(profileId);if(Double.isNaN(v))continue;v=Math.max(0,Math.min(100,v));float x=l+(r-l)*(p.ts-min)/(float)(max-min);float y=b-(b-t)*(float)(v/100.0);if(!started){pth.moveTo(x,y);started=true;}else pth.lineTo(x,y);}if(started)c.drawPath(pth,line);SimpleDateFormat f=new SimpleDateFormat("HH:mm",Locale.GERMANY);c.drawText(f.format(new Date(min)),l,h-7,text);String e=f.format(new Date(max));c.drawText(e,r-text.measureText(e),h-7,text);}
}
