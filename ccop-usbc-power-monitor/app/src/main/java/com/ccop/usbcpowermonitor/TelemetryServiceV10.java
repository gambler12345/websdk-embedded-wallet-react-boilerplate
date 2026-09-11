package com.ccop.usbcpowermonitor;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.JSONObject;
import java.io.*;
import java.util.*;

/**
 * v10 telemetry service. Writes every sample to SharedPreferences as well as a broadcast.
 * The Activity can therefore recover live data even when a dynamic broadcast is missed.
 */
public class TelemetryServiceV10 extends Service {
    public static final String ACTION="com.ccop.lademonitor.v10.TELEMETRY";
    public static final String PREFS="ccop_live_telemetry_v10";
    public static final String KEY_LATEST="latest";
    private static final String CHANNEL="ccop_live_v10";
    private final Handler h=new Handler(Looper.getMainLooper());
    private BatteryManager bm; private ProfileStore profiles; private SharedPreferences live,totals;
    private long lastElapsed,lastHistory,lastScan; private int plugEventId=0,cycleId=0; private boolean lastPlugged=false;
    private double cycleWh=0,totalWh=0; private Snapshot cached=new Snapshot();
    private final Runnable tick=new Runnable(){public void run(){try{sample();}catch(Throwable ignored){}h.postDelayed(this,1000);}};

    @Override public void onCreate(){super.onCreate();bm=(BatteryManager)getSystemService(BATTERY_SERVICE);profiles=new ProfileStore(this);live=getSharedPreferences(PREFS,MODE_PRIVATE);totals=getSharedPreferences("ccop_totals_v10",MODE_PRIVATE);plugEventId=totals.getInt("plugEventId",0);cycleId=totals.getInt("cycleId",0);cycleWh=bits(totals.getLong("cycleWh",Double.doubleToLongBits(0)));totalWh=bits(totals.getLong("totalWh",Double.doubleToLongBits(0)));createChannel();startForeground(1010,notification("Monitor aktiv","Live-Telemetrie startet"));lastElapsed=SystemClock.elapsedRealtime();h.post(tick);}
    @Override public int onStartCommand(Intent i,int flags,int id){return START_STICKY;}
    @Override public void onDestroy(){h.removeCallbacks(tick);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}

    private void sample() throws Exception{
        Intent bat=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));if(bat==null)return;
        long now=System.currentTimeMillis(),el=SystemClock.elapsedRealtime();double dt=Math.min(5,Math.max(0,(el-lastElapsed)/1000.0))/3600.0;lastElapsed=el;
        int level=bat.getIntExtra(BatteryManager.EXTRA_LEVEL,-1),scale=bat.getIntExtra(BatteryManager.EXTRA_SCALE,100),status=bat.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN),plugRaw=bat.getIntExtra(BatteryManager.EXTRA_PLUGGED,0),mv=bat.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1),temp=bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
        boolean plugged=plugRaw!=0;boolean charging=plugged&&(status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL);
        double pct=level>=0&&scale>0?100.0*level/scale:Double.NaN,v=mv>0?mv/1000.0:Double.NaN;long ua=prop(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW),counter=prop(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);double ma=valid(ua)?ua/1000.0:Double.NaN,a=valid(ua)?ua/1_000_000.0:Double.NaN;double battW=finite(v)&&finite(a)?v*a:Double.NaN;if(finite(battW))battW=charging?Math.abs(battW):-Math.abs(battW);
        int maxARaw=bat.getIntExtra("max_charging_current",-1),maxVRaw=bat.getIntExtra("max_charging_voltage",-1);double maxA=maxARaw>0?normA(maxARaw):Double.NaN,maxV=maxVRaw>0?normV(maxVRaw):Double.NaN;
        if(plugged&&!lastPlugged){plugEventId++;cycleId++;cycleWh=0;}if(now-lastScan>4000||lastScan==0){cached=scan();lastScan=now;}
        Reading srcV=find(cached,true),srcA=find(cached,false);double exactW=srcV!=null&&srcA!=null?Math.abs(srcV.value*srcA.value):Double.NaN;Double directSoc=directSoc(cached);String srcType=cached.best==null?"":first(cached.best.get("usb_type"),cached.best.get("real_type"),cached.best.get("type"));
        String plugType=plugName(plugRaw);String signature=signature(plugType,srcType,maxV,maxA,srcV,srcA);

        // Infer a better phone capacity from Android charge counter when possible.
        double chargeMah=valid(counter)?counter/1000.0:Double.NaN;if(finite(chargeMah)&&finite(pct)&&pct>=10&&pct<=100){double inferred=chargeMah/(pct/100.0);if(inferred>=1000&&inferred<=20000){double old=profiles.getPhoneCapacityMah();profiles.setPhoneCapacityMah(old*.8+inferred*.2);}}

        ProfileStore.Profile active=profiles.getActive();double eff=active==null?.88:clamp(active.efficiency,.5,1);double modeledW=charging&&finite(battW)?Math.abs(battW)/eff:Double.NaN;double sourceW=finite(exactW)?exactW:modeledW;
        if(charging&&finite(battW)&&dt>0){double d=Math.abs(battW)*dt;cycleWh+=d;totalWh+=d;}
        if(plugged&&active!=null){active.lastSeenAt=now;active.lastPlugType=plugType;if(active.connectionSignature.isEmpty())active.connectionSignature=signature;if(active.isEnergyStore()){
            if(directSoc!=null){active.estimatedSoc=clamp(directSoc,0,100);active.socKnown=true;active.socOrigin="DIRECT";active.lastValidatedSoc=active.estimatedSoc;active.lastValidationAt=now;}
            else if(charging&&active.socKnown&&!active.passthrough&&finite(sourceW)&&dt>0&&active.capacityWh()>0){double d=sourceW*dt;active.cumulativeSourceWh+=d;active.estimatedSoc=clamp(active.estimatedSoc-d/active.capacityWh()*100,0,100);active.socOrigin="ESTIMATED";}
        }profiles.saveProfile(active);}

        Aggregate ag=aggregate(pct);JSONObject j=new JSONObject();j.put("ts",now);j.put("plugged",plugged);j.put("charging",charging);j.put("plugType",plugType);put(j,"phonePct",pct);put(j,"batteryV",v);put(j,"batteryMa",ma);put(j,"batteryW",battW);if(temp!=Integer.MIN_VALUE)j.put("temperatureC",temp/10.0);put(j,"chargeCounterMah",chargeMah);put(j,"profileMaxV",maxV);put(j,"profileMaxA",maxA);if(srcV!=null){j.put("sourceV",srcV.value);j.put("sourceVPath",srcV.path);}if(srcA!=null){j.put("sourceA",srcA.value);j.put("sourceAPath",srcA.path);}put(j,"sourceExactW",exactW);put(j,"sourceEffectiveW",sourceW);j.put("sourcePowerEstimated",!finite(exactW)&&finite(sourceW));if(directSoc!=null)j.put("sourceDirectSoc",directSoc);j.put("sourceType",srcType);j.put("signature",signature);j.put("plugEventId",plugEventId);j.put("cycleId",cycleId);j.put("cycleWh",cycleWh);j.put("totalWh",totalWh);put(j,"aggregateSoc",ag.soc);j.put("aggregateRemainingWh",ag.remaining);j.put("aggregateFullWh",ag.full);j.put("aggregateKnownCount",ag.known);j.put("aggregateUnknownCount",ag.unknown);if(active!=null){j.put("profileId",active.id);j.put("profileName",active.name);if(active.isEnergyStore()&&active.socKnown)j.put("sourceSoc",active.estimatedSoc);}
        live.edit().putString(KEY_LATEST,j.toString()).putLong("updatedAt",now).apply();Intent out=new Intent(ACTION).setPackage(getPackageName());out.putExtra("json",j.toString());sendBroadcast(out);

        long histInterval=plugged?5000:15000;if(now-lastHistory>=histInterval||plugged!=lastPlugged){HistoryStore.Point p=new HistoryStore.Point();p.ts=now;p.plugged=plugged;p.charging=charging;p.phonePct=pct;p.batteryV=v;p.batteryMa=ma;p.batteryW=battW;p.sourceV=srcV==null?Double.NaN:srcV.value;p.sourceA=srcA==null?Double.NaN:srcA.value;p.sourceW=plugged&&finite(sourceW)?sourceW:0;p.sourceSoc=active!=null&&active.isEnergyStore()&&active.socKnown?active.estimatedSoc:Double.NaN;p.aggregateSoc=ag.soc;p.cycleWh=cycleWh;p.cycleId=cycleId;p.profileId=active==null?"":active.id;p.profileName=active==null?"":active.name;p.event=plugged!=lastPlugged?(plugged?"CONNECTED":"DISCONNECTED"):"";JSONObject socs=new JSONObject();for(ProfileStore.Profile q:profiles.loadActiveInventory())if(q.isEnergyStore()&&q.socKnown)socs.put(q.id,q.estimatedSoc);p.profileSocs=socs.toString();HistoryStore.append(this,p);lastHistory=now;}
        totals.edit().putInt("plugEventId",plugEventId).putInt("cycleId",cycleId).putLong("cycleWh",Double.doubleToLongBits(cycleWh)).putLong("totalWh",Double.doubleToLongBits(totalWh)).apply();lastPlugged=plugged;
        String body=(plugged?(charging?"Laden aktiv":"Quelle verbunden"):"Akkubetrieb")+" · "+(finite(pct)?String.format(Locale.GERMANY,"%.0f%%",pct):"—")+(finite(battW)?" · "+String.format(Locale.GERMANY,"%.2f W",Math.abs(battW)):"");((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).notify(1010,notification("CCOP LadeMonitor",body));
    }

    private Aggregate aggregate(double phonePct){Aggregate r=new Aggregate();double phoneWh=profiles.getPhoneCapacityMah()/1000.0*profiles.getPhoneNominalV();if(phoneWh>0&&finite(phonePct)){r.full+=phoneWh;r.remaining+=phoneWh*clamp(phonePct,0,100)/100;r.known++;}for(ProfileStore.Profile p:profiles.loadActiveInventory())if(p.isEnergyStore()&&p.capacityWh()>0){if(p.socKnown){r.full+=p.capacityWh();r.remaining+=p.capacityWh()*clamp(p.estimatedSoc,0,100)/100;r.known++;}else r.unknown++;}r.soc=r.full>0?r.remaining/r.full*100:Double.NaN;return r;}

    private Snapshot scan(){Snapshot s=new Snapshot();File root=new File("/sys/class/power_supply");try{File[] ds=root.listFiles();if(ds!=null)for(File d:ds)if(d.isDirectory()){Supply x=new Supply(d.getName());for(String k:new String[]{"type","usb_type","real_type","online","present","capacity","voltage_now","vbus_voltage","vbus_voltage_now","usb_voltage","input_voltage","pd_voltage_now","current_now","ibus_current","ibus_current_now","usb_current","input_current","pd_current_now"}){String z=read(new File(d,k));if(z!=null)x.v.put(k,z);}if(!x.v.isEmpty())s.all.add(x);}}catch(Throwable ignored){}int best=-9999;for(Supply x:s.all){int sc=score(x);if(sc>best){best=sc;s.best=sc>0?x:null;}}return s;}
    private int score(Supply s){String n=s.name.toLowerCase(Locale.ROOT),t=low(s.get("type"));if(n.contains("battery")||n.contains("bms")||t.contains("battery"))return-1000;int x=1;if("1".equals(s.get("online")))x+=100;if("1".equals(s.get("present")))x+=20;if(n.contains("usb")||n.contains("pd")||n.contains("charger")||n.contains("ac")||n.contains("typec"))x+=40;if(t.contains("usb")||t.contains("mains")||t.contains("pd"))x+=40;return x;}
    private Reading find(Snapshot s,boolean voltage){String[] keys=voltage?new String[]{"vbus_voltage_now","vbus_voltage","usb_voltage","input_voltage","pd_voltage_now","voltage_now"}:new String[]{"ibus_current_now","ibus_current","usb_current","input_current","pd_current_now","current_now"};Reading best=null;int bs=-9999;for(Supply x:s.all){int ss=score(x);if(ss<=0)continue;if("0".equals(x.get("online"))&&"0".equals(x.get("present")))continue;for(int i=0;i<keys.length;i++){Double raw=parse(x.get(keys[i]));if(raw==null||raw==0)continue;double val=voltage?normV(raw):normA(raw);if((voltage&&val>=1&&val<=30)||(!voltage&&Math.abs(val)>=.0005&&Math.abs(val)<=15)){int q=ss+(keys.length-i)*3;if(q>bs){bs=q;best=new Reading(val,"/sys/class/power_supply/"+x.name+"/"+keys[i]);}}}}return best;}
    private Double directSoc(Snapshot s){if(s.best==null)return null;Double x=parse(s.best.get("capacity"));if(x==null)return null;if(x>=0&&x<=100)return x;if(x>100&&x<=10000)return x/100;return null;}
    private String signature(String plug,String type,double maxV,double maxA,Reading v,Reading a){return (plug+"|"+non(type)+"|MAX:"+(finite(maxV)?String.format(Locale.US,"%.1f",maxV):"?")+"V/"+(finite(maxA)?String.format(Locale.US,"%.1f",maxA):"?")+"A|VBUS:"+(v==null?"?":String.format(Locale.US,"%.2f",v.value))+"|IBUS:"+(a==null?"?":String.format(Locale.US,"%.2f",a.value))).toUpperCase(Locale.ROOT);}
    private long prop(int id){try{return bm.getIntProperty(id);}catch(Throwable t){return Long.MIN_VALUE;}}private boolean valid(long x){return x!=Long.MIN_VALUE&&x!=Integer.MIN_VALUE;}private double normV(double r){double a=Math.abs(r);return a>100000?r/1e6:(a>100?r/1000:r);}private double normA(double r){double a=Math.abs(r);return a>100000?r/1e6:(a>100?r/1000:r);}private boolean finite(double x){return!Double.isNaN(x)&&!Double.isInfinite(x);}private double clamp(double x,double a,double b){return Math.max(a,Math.min(b,x));}private double bits(long x){return Double.longBitsToDouble(x);}private void put(JSONObject j,String k,double x)throws Exception{if(finite(x))j.put(k,x);}private String read(File f){try{if(!f.exists()||!f.canRead())return null;BufferedReader r=new BufferedReader(new FileReader(f));String s=r.readLine();r.close();return s==null?null:s.trim();}catch(Throwable t){return null;}}private Double parse(String s){try{return s==null?null:Double.parseDouble(s.trim());}catch(Throwable t){return null;}}private String low(String s){return s==null?"":s.toLowerCase(Locale.ROOT);}private String non(String s){return s==null?"":s;}private String first(String...a){for(String s:a)if(s!=null&&!s.trim().isEmpty())return s.trim();return"";}
    private String plugName(int p){if((p&BatteryManager.BATTERY_PLUGGED_AC)!=0)return"AC";if((p&BatteryManager.BATTERY_PLUGGED_USB)!=0)return"USB";if((p&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0)return"WIRELESS";return p==0?"NONE":"EXTERNAL";}
    private Notification notification(String title,String body){Intent o=new Intent(this,MainActivityV10.class);PendingIntent pi=PendingIntent.getActivity(this,0,o,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);Notification.Builder b=Build.VERSION.SDK_INT>=26?new Notification.Builder(this,CHANNEL):new Notification.Builder(this);b.setContentTitle(title).setContentText(body).setSmallIcon(android.R.drawable.ic_lock_idle_charging).setOngoing(true).setContentIntent(pi);return b.build();}
    private void createChannel(){if(Build.VERSION.SDK_INT>=26)((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(new NotificationChannel(CHANNEL,"CCOP LadeMonitor Live",NotificationManager.IMPORTANCE_LOW));}
    static class Supply{String name;Map<String,String>v=new LinkedHashMap<>();Supply(String n){name=n;}String get(String k){return v.get(k);}}static class Snapshot{List<Supply>all=new ArrayList<>();Supply best;}static class Reading{double value;String path;Reading(double v,String p){value=v;path=p;}}static class Aggregate{double full=0,remaining=0,soc=Double.NaN;int known=0,unknown=0;}
}
