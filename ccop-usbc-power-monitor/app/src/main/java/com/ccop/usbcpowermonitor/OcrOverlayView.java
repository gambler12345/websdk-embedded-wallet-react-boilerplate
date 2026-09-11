package com.ccop.usbcpowermonitor;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.*;

/** Shows the original label photo with OCR element boxes. A selected OCR row is highlighted. */
public class OcrOverlayView extends View {
    public static class Item { public Rect box; public String text; public String id; public Item(Rect b,String t,String i){box=b;text=t;id=i;} }
    private Bitmap bitmap; private List<Item> items=new ArrayList<>(); private int selected=-1;
    private final Paint box=new Paint(Paint.ANTI_ALIAS_FLAG), selectedPaint=new Paint(Paint.ANTI_ALIAS_FLAG), label=new Paint(Paint.ANTI_ALIAS_FLAG);
    public OcrOverlayView(Context c){super(c);box.setStyle(Paint.Style.STROKE);box.setStrokeWidth(2f*c.getResources().getDisplayMetrics().density);box.setColor(Color.rgb(34,157,255));selectedPaint.setStyle(Paint.Style.STROKE);selectedPaint.setStrokeWidth(4f*c.getResources().getDisplayMetrics().density);selectedPaint.setColor(Color.rgb(255,82,82));label.setColor(Color.WHITE);label.setTextSize(10f*c.getResources().getDisplayMetrics().scaledDensity);label.setStyle(Paint.Style.FILL);setBackgroundColor(Color.rgb(5,10,14));}
    public void setData(Bitmap b,List<Item> i){bitmap=b;items=i==null?new ArrayList<>():i;selected=-1;invalidate();}
    public void select(int index){selected=index;invalidate();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);if(bitmap==null){c.drawText("Noch kein Bild",20,35,label);return;}float sx=getWidth()/(float)bitmap.getWidth(), sy=getHeight()/(float)bitmap.getHeight(), s=Math.min(sx,sy);float dw=bitmap.getWidth()*s,dh=bitmap.getHeight()*s,ox=(getWidth()-dw)/2f,oy=(getHeight()-dh)/2f;c.drawBitmap(bitmap,null,new RectF(ox,oy,ox+dw,oy+dh),null);for(int n=0;n<items.size();n++){Rect r=items.get(n).box;if(r==null)continue;RectF q=new RectF(ox+r.left*s,oy+r.top*s,ox+r.right*s,oy+r.bottom*s);c.drawRect(q,n==selected?selectedPaint:box);if(n==selected){String id=items.get(n).id;c.drawText(id,q.left,Math.max(14,q.top-4),label);}}}
}
