package com.ccop.usbcpowermonitor;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

public class HistoryStore {
    private static final String FILE_NAME = "ccop_lademonitor_history_v9.jsonl";

    public static class Point {
        public long ts;
        public boolean charging;
        public boolean plugged;
        public double phonePct=Double.NaN,batteryV=Double.NaN,batteryMa=Double.NaN,batteryW=Double.NaN;
        public double sourceV=Double.NaN,sourceA=Double.NaN,sourceW=Double.NaN,sourceSoc=Double.NaN,aggregateSoc=Double.NaN;
        public double cycleWh=0.0;
        public int cycleId=0;
        public String profileId="",profileName="",event="";
        public String profileSocs="{}";
        public JSONObject toJson(){JSONObject o=new JSONObject();try{o.put("ts",ts);o.put("charging",charging);o.put("plugged",plugged);put(o,"phonePct",phonePct);put(o,"batteryV",batteryV);put(o,"batteryMa",batteryMa);put(o,"batteryW",batteryW);put(o,"sourceV",sourceV);put(o,"sourceA",sourceA);put(o,"sourceW",sourceW);put(o,"sourceSoc",sourceSoc);put(o,"aggregateSoc",aggregateSoc);o.put("cycleWh",cycleWh);o.put("cycleId",cycleId);o.put("profileId",profileId);o.put("profileName",profileName);o.put("event",event);o.put("profileSocs",profileSocs);}catch(Exception ignored){}return o;}
        public static Point fromJson(JSONObject o){Point p=new Point();p.ts=o.optLong("ts",0);p.charging=o.optBoolean("charging",false);p.plugged=o.optBoolean("plugged",false);p.phonePct=opt(o,"phonePct");p.batteryV=opt(o,"batteryV");p.batteryMa=opt(o,"batteryMa");p.batteryW=opt(o,"batteryW");p.sourceV=opt(o,"sourceV");p.sourceA=opt(o,"sourceA");p.sourceW=opt(o,"sourceW");p.sourceSoc=opt(o,"sourceSoc");p.aggregateSoc=opt(o,"aggregateSoc");p.cycleWh=o.optDouble("cycleWh",0);p.cycleId=o.optInt("cycleId",0);p.profileId=o.optString("profileId","");p.profileName=o.optString("profileName","");p.event=o.optString("event","");p.profileSocs=o.optString("profileSocs","{}");return p;}
        private static void put(JSONObject o,String k,double v)throws Exception{if(!Double.isNaN(v)&&!Double.isInfinite(v))o.put(k,v);} private static double opt(JSONObject o,String k){return o.has(k)?o.optDouble(k,Double.NaN):Double.NaN;}
        public double socFor(String id){try{JSONObject x=new JSONObject(profileSocs);return x.has(id)?x.optDouble(id,Double.NaN):Double.NaN;}catch(Exception e){return Double.NaN;}}
    }
    public static synchronized void append(Context c,Point p){try{BufferedWriter w=new BufferedWriter(new FileWriter(new File(c.getFilesDir(),FILE_NAME),true));w.write(p.toJson().toString());w.newLine();w.close();}catch(Exception ignored){}}
    public static synchronized List<Point> readSince(Context c,long sinceTs,int maxPoints){ArrayList<Point> all=new ArrayList<>();try{File f=new File(c.getFilesDir(),FILE_NAME);if(!f.exists())return all;BufferedReader r=new BufferedReader(new FileReader(f));String line;while((line=r.readLine())!=null){try{Point p=Point.fromJson(new JSONObject(line));if(p.ts>=sinceTs)all.add(p);}catch(Exception ignored){}}r.close();}catch(Exception ignored){}if(maxPoints>0&&all.size()>maxPoints){int step=(int)Math.ceil(all.size()/(double)maxPoints);ArrayList<Point> reduced=new ArrayList<>();for(int i=0;i<all.size();i+=step)reduced.add(all.get(i));if(!reduced.isEmpty()&&reduced.get(reduced.size()-1)!=all.get(all.size()-1))reduced.add(all.get(all.size()-1));return reduced;}return all;}
    public static synchronized List<Point> readForProfile(Context c,String profileId,long sinceTs,int maxPoints){ArrayList<Point> out=new ArrayList<>();for(Point p:readSince(c,sinceTs,0)){if(!Double.isNaN(p.socFor(profileId)))out.add(p);}if(maxPoints>0&&out.size()>maxPoints){int step=(int)Math.ceil(out.size()/(double)maxPoints);ArrayList<Point> r=new ArrayList<>();for(int i=0;i<out.size();i+=step)r.add(out.get(i));return r;}return out;}
    public static synchronized long count(Context c){long n=0;try{File f=new File(c.getFilesDir(),FILE_NAME);if(!f.exists())return 0;BufferedReader r=new BufferedReader(new FileReader(f));while(r.readLine()!=null)n++;r.close();}catch(Exception ignored){}return n;}
    public static synchronized void clear(Context c){try{File f=new File(c.getFilesDir(),FILE_NAME);if(f.exists())f.delete();}catch(Exception ignored){}}
}
