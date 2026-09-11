package com.ccop.usbcpowermonitor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG=Color.rgb(8,12,16), PANEL=Color.rgb(17,23,29), LINE=Color.rgb(43,56,68);
    private static final int TEXT=Color.rgb(247,249,252), MUTED=Color.rgb(151,164,178), OK=Color.rgb(72,229,139);
    private static final int WARN=Color.rgb(255,210,105), BLUE=Color.rgb(117,164,255), RED=Color.rgb(255,114,114);

    private final Handler handler=new Handler(Looper.getMainLooper());
    private final Map<String,TextView> fields=new LinkedHashMap<>();
    private final List<Double> speedSamplesMa=new ArrayList<>();
    private BatteryManager batteryManager;
    private TextView hero, sub, speedValue, speedSub, diag;
    private PowerSnapshot cachedSnapshot=new PowerSnapshot();
    private long lastSystemScan=0L;
    private String latestDiagnostics="Noch keine Diagnose.";

    private final Runnable ticker=new Runnable(){
        @Override public void run(){
            refreshSafe();
            handler.postDelayed(this,1000);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        try{
            getWindow().setStatusBarColor(BG);
            getWindow().setNavigationBarColor(BG);
            batteryManager=(BatteryManager)getSystemService(Context.BATTERY_SERVICE);
            setContentView(buildUi());
            hero.setText("APP BEREIT");
            hero.setTextColor(OK);
            sub.setText("Live-Ladegeschwindigkeit wird geladen");
        }catch(Throwable t){
            setContentView(buildEmergencyUi(t));
        }
    }

    @Override protected void onResume(){
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker,300);
    }

    @Override protected void onPause(){
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private View buildUi(){
        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14),dp(34),dp(14),dp(32));
        scroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("CCOP LadeMonitor · v5 SPEED",23,TEXT,true));
        TextView intro=text("Eine Kennzahl im Mittelpunkt: echte momentane Ladegeschwindigkeit zum Smartphone-Akku",12,MUTED,false);
        intro.setPadding(0,dp(4),0,dp(12)); root.addView(intro);

        LinearLayout speed=card();
        speed.addView(title("LADEGESCHWINDIGKEIT · LIVE"));
        speedValue=text("PRÜFE…",46,TEXT,true); speedValue.setPadding(0,dp(10),0,dp(2)); speed.addView(speedValue);
        speedSub=text("BatteryManager wird gelesen",14,MUTED,true); speedSub.setPadding(0,0,0,dp(10)); speed.addView(speedSub);
        metric(speed,"speedA","Live-Strom");
        metric(speed,"speedW","Leistung zum Akku");
        metric(speed,"speedPctH","Geschätzter Zuwachs");
        metric(speed,"timeFull","Zeit bis 100 % bei aktueller Rate");
        metric(speed,"speedAvg","Ø Ladegeschwindigkeit · 15 s");
        metric(speed,"batteryPct","Akkustand");
        root.addView(speed,mb());

        LinearLayout status=card();
        status.addView(title("STATUS"));
        hero=text("STARTET…",31,TEXT,true); hero.setPadding(0,dp(8),0,dp(2)); status.addView(hero);
        sub=text("Oberfläche wird initialisiert",13,MUTED,false); sub.setPadding(0,0,0,dp(10)); status.addView(sub);
        metric(status,"plug","Android meldet Quelle");
        metric(status,"charging","Aktiver Ladezyklus");
        metric(status,"chargeCounter","Charge Counter");
        metric(status,"capacityEstimate","Geschätzte Vollkapazität");
        root.addView(status,mb());

        LinearLayout source=card();
        source.addView(title("LADEQUELLE / POWERBANK / NETZTEIL"));
        TextView sourceHint=text("LIVE-Quellwerte werden nur gezeigt, wenn Android/OEM sie wirklich freigibt. Profilwerte werden separat als MAX gekennzeichnet.",12,WARN,true);
        sourceHint.setPadding(0,dp(4),0,dp(9)); source.addView(sourceHint);
        metric(source,"sourceVoltage","Quellenspannung · LIVE");
        metric(source,"sourceCurrent","Quellenstrom · LIVE");
        metric(source,"sourcePower","Quellenleistung · LIVE");
        metric(source,"sourceSoc","Powerbank-/Quellen-Akkustand");
        metric(source,"sourceModel","Quelle / Modell");
        metric(source,"sourcePath","Live-Datenpfad");
        root.addView(source,mb());

        LinearLayout profile=card();
        profile.addView(title("ANDROID LADEPROFIL · MAXIMALWERTE"));
        metric(profile,"maxVoltage","Max. gemeldete Spannung");
        metric(profile,"maxCurrent","Max. gemeldeter Strom");
        metric(profile,"maxPower","Max. rechnerische Leistung");
        TextView profileNote=text("Beispiel: 5 V × 2 A = 10 W bedeutet nur das gemeldete Ladeprofil. Es ist nicht automatisch die aktuelle Live-Leistung.",11,MUTED,false);
        profileNote.setPadding(0,dp(8),0,0); profile.addView(profileNote);
        root.addView(profile,mb());

        LinearLayout battery=card();
        battery.addView(title("SMARTPHONE-AKKU · DIREKTE MESSWERTE"));
        metric(battery,"batteryVoltage","Akkuspannung");
        metric(battery,"batteryCurrentRaw","Momentaner Netto-Strom");
        metric(battery,"batteryPower","Momentane Netto-Leistung");
        metric(battery,"temperature","Temperatur");
        root.addView(battery,mb());

        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh=button("JETZT PRÜFEN"); refresh.setOnClickListener(v->{lastSystemScan=0;refreshSafe();});
        actions.addView(refresh,new LinearLayout.LayoutParams(0,dp(50),1f));
        Button share=button("DIAGNOSE TEILEN"); share.setOnClickListener(v->shareDiagnostics());
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(50),1f); slp.setMargins(dp(8),0,0,0); actions.addView(share,slp);
        root.addView(actions,mb());

        LinearLayout d=card();
        d.addView(title("RAW-DIAGNOSE"));
        TextView hint=text("SOURCE_V/A = echte externe Quellenseite. BATTERY_* = Smartphone-Akkuseite. PROFILE_* = nur gemeldete Ladegrenzen.",11,MUTED,false);
        hint.setPadding(0,dp(4),0,dp(8)); d.addView(hint);
        diag=text("Noch keine Diagnose",10,TEXT,false); diag.setTypeface(Typeface.MONOSPACE); diag.setTextIsSelectable(true); d.addView(diag);
        root.addView(d,mb());

        TextView footer=text("SAFE START · Live-KPI aus BatteryManager · keine Demo-Werte · keine Cloud",10,MUTED,false);
        footer.setGravity(Gravity.CENTER); root.addView(footer);
        return scroll;
    }

    private View buildEmergencyUi(Throwable t){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(52),dp(24),dp(24)); root.setBackgroundColor(BG);
        root.addView(text("CCOP LadeMonitor · SAFE MODE",24,OK,true));
        TextView msg=text("Die App bleibt geöffnet.\n\nFehler: "+t.getClass().getSimpleName()+"\n"+String.valueOf(t.getMessage()),14,TEXT,false);
        msg.setPadding(0,dp(16),0,0); root.addView(msg);
        return root;
    }

    private void refreshSafe(){
        try{ refreshTelemetry(); }
        catch(Throwable t){
            if(hero!=null){ hero.setText("APP AKTIV · TELEMETRIEFEHLER"); hero.setTextColor(WARN); }
            if(sub!=null) sub.setText(t.getClass().getSimpleName()+": "+String.valueOf(t.getMessage()));
            latestDiagnostics="Telemetry exception\n"+t.getClass().getName()+"\n"+String.valueOf(t.getMessage());
            if(diag!=null)diag.setText(latestDiagnostics);
        }
    }

    private void refreshTelemetry(){
        Intent battery=registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if(battery==null){ sub.setText("Android liefert keinen Battery-Broadcast"); return; }

        int level=battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);
        int scale=battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        int status=battery.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN);
        int plugged=battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
        int battMv=battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1);
        int temp10=battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
        int maxCurrentRaw=battery.getIntExtra("max_charging_current",-1);
        int maxVoltageRaw=battery.getIntExtra("max_charging_voltage",-1);
        boolean charging=status==BatteryManager.BATTERY_STATUS_CHARGING||status==BatteryManager.BATTERY_STATUS_FULL;
        boolean pluggedNow=plugged!=0;

        double pct=(level>=0&&scale>0)?100.0*level/scale:Double.NaN;
        double battV=battMv>0?battMv/1000.0:Double.NaN;
        long nowUa=safeProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long counterUah=safeProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        boolean hasCurrent=validProperty(nowUa);
        boolean hasCounter=validProperty(counterUah)&&counterUah>0;
        double rawA=hasCurrent?nowUa/1_000_000.0:Double.NaN;
        double rawMa=hasCurrent?nowUa/1000.0:Double.NaN;
        double speedMa=(charging&&hasCurrent)?Math.abs(rawMa):Double.NaN;
        double speedA=Double.isNaN(speedMa)?Double.NaN:speedMa/1000.0;
        double battW=(!Double.isNaN(battV)&&!Double.isNaN(speedA))?battV*speedA:Double.NaN;

        if(charging&&!Double.isNaN(speedMa)){
            speedSamplesMa.add(speedMa);
            while(speedSamplesMa.size()>15)speedSamplesMa.remove(0);
        }else if(!charging){
            speedSamplesMa.clear();
        }
        double avgMa=average(speedSamplesMa);

        double fullCapacityMah=Double.NaN;
        double chargeCounterMah=hasCounter?counterUah/1000.0:Double.NaN;
        if(hasCounter&&!Double.isNaN(pct)&&pct>=5.0&&pct<=100.0){
            double est=chargeCounterMah/(pct/100.0);
            if(est>=500.0&&est<=30000.0)fullCapacityMah=est;
        }
        double ratePctH=(!Double.isNaN(avgMa)&&!Double.isNaN(fullCapacityMah)&&fullCapacityMah>0)?avgMa/fullCapacityMah*100.0:Double.NaN;
        double minutesToFull=(!Double.isNaN(ratePctH)&&ratePctH>0.05&&!Double.isNaN(pct))?Math.max(0.0,(100.0-pct)/ratePctH*60.0):Double.NaN;

        long now=System.currentTimeMillis();
        if(now-lastSystemScan>5000||lastSystemScan==0){ cachedSnapshot=scanPowerSupplies(); lastSystemScan=now; }
        Reading srcV=findExternalReading(cachedSnapshot,true);
        Reading srcA=findExternalReading(cachedSnapshot,false);
        Double srcW=(srcV!=null&&srcA!=null)?Math.abs(srcV.value*srcA.value):null;

        if(charging&&!Double.isNaN(speedMa)){
            hero.setText("LÄDT · "+fmt(speedMa,0)+" mA"); hero.setTextColor(OK);
            speedValue.setText(fmt(speedMa,0)+" mA"); speedValue.setTextColor(OK);
            speedSub.setText(fmt(speedA,3)+" A · "+(!Double.isNaN(battW)?"≈ "+fmt(battW,2)+" W zum Akku":"Watt nicht berechenbar")+" · LIVE");
            speedSub.setTextColor(OK);
        }else if(pluggedNow){
            hero.setText("QUELLE VERBUNDEN"); hero.setTextColor(WARN);
            speedValue.setText("0 mA / wartet"); speedValue.setTextColor(WARN);
            speedSub.setText("Quelle erkannt, aber aktuell kein positiver Netto-Ladestrom"); speedSub.setTextColor(WARN);
        }else{
            hero.setText("NICHT VERBUNDEN"); hero.setTextColor(TEXT);
            speedValue.setText("—"); speedValue.setTextColor(TEXT);
            speedSub.setText("Keine aktive Ladequelle"); speedSub.setTextColor(MUTED);
        }

        sub.setText(srcV!=null?"Externe Quellenspannung verifiziert: "+srcV.path:(pluggedNow?"Laden erkannt · VBUS selbst wird vom Gerät nicht freigegeben":"Keine externe Quelle aktiv"));
        set("plug",pluggedNow?plugName(plugged):"keine",pluggedNow?OK:MUTED);
        set("charging",charging?"Ja":"Nein",charging?OK:MUTED);

        set("speedA",!Double.isNaN(speedA)?fmt(speedA,3)+" A · LIVE":"nicht verfügbar",!Double.isNaN(speedA)?OK:MUTED);
        set("speedW",!Double.isNaN(battW)?fmt(battW,2)+" W · AKKUSEITE":"nicht berechenbar",!Double.isNaN(battW)?OK:MUTED);
        set("speedPctH",!Double.isNaN(ratePctH)?"≈ "+fmt(ratePctH,1)+" %/h":"noch nicht berechenbar",!Double.isNaN(ratePctH)?BLUE:MUTED);
        set("timeFull",!Double.isNaN(minutesToFull)?"≈ "+formatMinutes(minutesToFull)+" · Momentaufnahme":"noch nicht berechenbar",!Double.isNaN(minutesToFull)?BLUE:MUTED);
        set("speedAvg",!Double.isNaN(avgMa)?fmt(avgMa,0)+" mA":"warte auf Messwerte",!Double.isNaN(avgMa)?TEXT:MUTED);
        set("batteryPct",!Double.isNaN(pct)?fmt(pct,0)+" %":"nicht gemeldet",TEXT);
        set("chargeCounter",hasCounter?fmt(chargeCounterMah,0)+" mAh":"nicht gemeldet",hasCounter?TEXT:MUTED);
        set("capacityEstimate",!Double.isNaN(fullCapacityMah)?"≈ "+fmt(fullCapacityMah,0)+" mAh":"nicht berechenbar",!Double.isNaN(fullCapacityMah)?BLUE:MUTED);

        if(srcV!=null){set("sourceVoltage",fmt(srcV.value,3)+" V · LIVE",OK);set("sourcePath",srcV.path,BLUE);}else{set("sourceVoltage","nicht freigegeben",RED);set("sourcePath",srcA!=null?srcA.path:"kein echter VBUS-/IBUS-Pfad",MUTED);}
        if(srcA!=null){set("sourceCurrent",fmt(Math.abs(srcA.value),3)+" A · LIVE",OK);}else{set("sourceCurrent","nicht freigegeben",RED);}
        set("sourcePower",srcW!=null?fmt(srcW,2)+" W · LIVE":"nicht direkt messbar",srcW!=null?OK:MUTED);
        String sourceSoc=findExternalText(cachedSnapshot,"capacity");
        if(sourceSoc!=null&&isPercent(sourceSoc))set("sourceSoc",sourceSoc.trim()+" % · DIREKT",OK); else set("sourceSoc","nicht übertragen",MUTED);
        String model=first(findExternalText(cachedSnapshot,"manufacturer"),findExternalText(cachedSnapshot,"model_name"));
        set("sourceModel",model!=null?model:(pluggedNow?plugName(plugged):"keine Quelle"),model!=null?BLUE:MUTED);

        Double maxV=maxVoltageRaw>0?normalizeVoltage(maxVoltageRaw):null;
        Double maxA=maxCurrentRaw>0?normalizeCurrent(maxCurrentRaw):null;
        set("maxVoltage",maxV!=null?fmt(maxV,2)+" V · MAX":"nicht gemeldet",maxV!=null?BLUE:MUTED);
        set("maxCurrent",maxA!=null?fmt(maxA,2)+" A · MAX":"nicht gemeldet",maxA!=null?BLUE:MUTED);
        set("maxPower",maxV!=null&&maxA!=null?fmt(Math.abs(maxV*maxA),2)+" W · MAX":"nicht berechenbar",maxV!=null&&maxA!=null?BLUE:MUTED);

        set("batteryVoltage",!Double.isNaN(battV)?fmt(battV,3)+" V · LIVE":"nicht gemeldet",!Double.isNaN(battV)?OK:MUTED);
        set("batteryCurrentRaw",!Double.isNaN(rawMa)?signed(rawMa,0)+" mA · RAW":"nicht gemeldet",!Double.isNaN(rawMa)?OK:MUTED);
        set("batteryPower",!Double.isNaN(battW)?fmt(battW,2)+" W · Netto zum Akku":"nicht berechenbar",!Double.isNaN(battW)?OK:MUTED);
        set("temperature",temp10!=Integer.MIN_VALUE?fmt(temp10/10.0,1)+" °C":"nicht gemeldet",TEXT);

        latestDiagnostics=diagnostics(battery,cachedSnapshot,srcV,srcA,nowUa,counterUah,maxVoltageRaw,maxCurrentRaw,ratePctH,minutesToFull);
        diag.setText(latestDiagnostics);
    }

    private PowerSnapshot scanPowerSupplies(){
        PowerSnapshot snap=new PowerSnapshot();
        File root=new File("/sys/class/power_supply");
        try{
            File[] dirs=root.listFiles();
            if(dirs!=null){
                for(File d:dirs){
                    if(!d.isDirectory())continue;
                    Supply s=readSupply(d);
                    if(s!=null){snap.supplies.add(s);if(isExternal(s)&&snap.bestExternal==null)snap.bestExternal=s;if(isExternal(s)&&"1".equals(s.get("online")))snap.bestExternal=s;}
                }
            }
        }catch(Throwable ignored){}
        return snap;
    }

    private Supply readSupply(File d){
        try{
            Supply s=new Supply(d.getName());
            String[] keys={"type","usb_type","real_type","online","present","status","capacity","manufacturer","model_name","voltage_now","voltage_avg","vbus_voltage","vbus_voltage_now","voltage_vbus","usb_voltage","input_voltage","charger_voltage","pd_voltage","pd_voltage_now","current_now","current_avg","ibus_current","ibus_current_now","current_ibus","usb_current","input_current","charger_current","pd_current","pd_current_now","power_now"};
            for(String k:keys){String v=readText(new File(d,k));if(v!=null)s.values.put(k,v);}
            return s.values.isEmpty()?null:s;
        }catch(Throwable t){return null;}
    }

    private boolean isExternal(Supply s){
        String n=s.name.toLowerCase(Locale.ROOT), type=lower(s.get("type"));
        if(n.contains("battery")||n.contains("bms")||type.contains("battery"))return false;
        return n.contains("usb")||n.contains("charger")||n.contains("chg")||n.contains("main")||n.contains("ac")||n.contains("pd")||n.contains("typec")||n.contains("dc")||type.contains("usb")||type.contains("mains");
    }

    private Reading findExternalReading(PowerSnapshot snap,boolean voltage){
        String[] keys=voltage?new String[]{"vbus_voltage_now","vbus_voltage","voltage_vbus","usb_voltage","input_voltage","charger_voltage","pd_voltage_now","pd_voltage","voltage_now","voltage_avg"}:new String[]{"ibus_current_now","ibus_current","current_ibus","usb_current","input_current","charger_current","pd_current_now","pd_current","current_now","current_avg"};
        Reading best=null; int bestScore=-1;
        for(Supply s:snap.supplies){
            if(!isExternal(s))continue;
            int base="1".equals(s.get("online"))?100:10;
            for(int i=0;i<keys.length;i++){
                Double raw=parseDouble(s.get(keys[i])); if(raw==null||raw==0)continue;
                double val=voltage?normalizeVoltage(raw):normalizeCurrent(raw);
                if(voltage&&(Math.abs(val)<1||Math.abs(val)>30))continue;
                if(!voltage&&(Math.abs(val)<0.0001||Math.abs(val)>15))continue;
                int score=base+(keys.length-i);
                if(score>bestScore){bestScore=score;best=new Reading(val,"/sys/class/power_supply/"+s.name+"/"+keys[i]);}
            }
        }
        return best;
    }

    private String findExternalText(PowerSnapshot snap,String key){
        if(snap.bestExternal!=null){String x=snap.bestExternal.get(key);if(x!=null&&!x.trim().isEmpty())return x.trim();}
        for(Supply s:snap.supplies){if(isExternal(s)){String x=s.get(key);if(x!=null&&!x.trim().isEmpty())return x.trim();}}
        return null;
    }

    private String diagnostics(Intent battery,PowerSnapshot snap,Reading srcV,Reading srcA,long nowUa,long counterUah,int maxV,int maxA,double rate,double mins){
        StringBuilder sb=new StringBuilder();
        sb.append("CCOP LadeMonitor v5 SPEED\nZeit: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.GERMANY).format(new Date())).append('\n');
        sb.append("SOURCE_V=").append(srcV!=null?srcV.value+" @ "+srcV.path:"UNAVAILABLE").append('\n');
        sb.append("SOURCE_A=").append(srcA!=null?srcA.value+" @ "+srcA.path:"UNAVAILABLE").append('\n');
        sb.append("BATTERY_CURRENT_NOW_uA=").append(nowUa).append('\n');
        sb.append("BATTERY_CHARGE_COUNTER_uAh=").append(counterUah).append('\n');
        sb.append("PROFILE_MAX_V_RAW=").append(maxV).append('\n');
        sb.append("PROFILE_MAX_A_RAW=").append(maxA).append('\n');
        sb.append("EST_RATE_PERCENT_H=").append(Double.isNaN(rate)?"UNAVAILABLE":fmt(rate,2)).append('\n');
        sb.append("EST_MIN_TO_FULL=").append(Double.isNaN(mins)?"UNAVAILABLE":fmt(mins,1)).append('\n');
        sb.append("\nPOWER_SUPPLY\n");
        for(Supply s:snap.supplies){sb.append('[').append(s.name).append("]\n");for(Map.Entry<String,String> e:s.values.entrySet())sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');}
        return sb.toString();
    }

    private long safeProperty(int id){try{return batteryManager.getIntProperty(id);}catch(Throwable t){return Long.MIN_VALUE;}}
    private boolean validProperty(long v){return v!=Long.MIN_VALUE&&v!=Integer.MIN_VALUE;}
    private String readText(File f){try{if(!f.exists()||!f.isFile()||!f.canRead())return null;BufferedReader br=new BufferedReader(new FileReader(f));String line=br.readLine();br.close();return line==null?null:line.trim();}catch(Throwable t){return null;}}
    private Double parseDouble(String s){if(s==null)return null;try{return Double.parseDouble(s.trim());}catch(Throwable t){return null;}}
    private double normalizeVoltage(double raw){double a=Math.abs(raw);if(a>100000)return raw/1_000_000.0;if(a>100)return raw/1000.0;return raw;}
    private double normalizeCurrent(double raw){double a=Math.abs(raw);if(a>100000)return raw/1_000_000.0;if(a>100)return raw/1000.0;return raw;}
    private double average(List<Double> l){if(l.isEmpty())return Double.NaN;double s=0;for(double v:l)s+=v;return s/l.size();}
    private boolean isPercent(String s){try{double v=Double.parseDouble(s.trim());return v>=0&&v<=100;}catch(Throwable t){return false;}}
    private String formatMinutes(double mins){int m=(int)Math.round(mins);if(m<60)return m+" min";return (m/60)+" h "+(m%60)+" min";}

    private void shareDiagnostics(){
        Intent send=new Intent(Intent.ACTION_SEND); send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,"CCOP LadeMonitor v5 Diagnose"); send.putExtra(Intent.EXTRA_TEXT,latestDiagnostics);
        startActivity(Intent.createChooser(send,"Diagnose teilen"));
    }

    private void metric(LinearLayout p,String key,String label){
        LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(8),0,dp(8));
        TextView l=text(label,13,MUTED,false),v=text("—",14,TEXT,true);v.setGravity(Gravity.END);
        r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));r.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1.25f));
        fields.put(key,v);p.addView(r);
    }
    private void set(String key,String value,int color){TextView v=fields.get(key);if(v!=null){v.setText(value);v.setTextColor(color);}}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable gd=new GradientDrawable();gd.setColor(PANEL);gd.setCornerRadius(dp(24));gd.setStroke(dp(1),LINE);c.setBackground(gd);return c;}
    private LinearLayout.LayoutParams mb(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,dp(12));return lp;}
    private TextView title(String s){TextView t=text(s,12,MUTED,true);t.setLetterSpacing(0.10f);return t;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(0,1.08f);return t;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable gd=new GradientDrawable();gd.setColor(Color.rgb(24,32,40));gd.setCornerRadius(dp(15));gd.setStroke(dp(1),LINE);b.setBackground(gd);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String plugName(int p){if((p&BatteryManager.BATTERY_PLUGGED_AC)!=0)return "Netzteil / AC";if((p&BatteryManager.BATTERY_PLUGGED_USB)!=0)return "USB";if((p&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0)return "Wireless";return p==0?"keine":"extern";}
    private String fmt(double v,int d){return String.format(Locale.GERMANY,"%."+d+"f",v);}
    private String signed(double v,int d){return String.format(Locale.GERMANY,"%+."+d+"f",v);}
    private String lower(String s){return s==null?"":s.toLowerCase(Locale.ROOT);}
    private String first(String... values){if(values==null)return null;for(String s:values)if(s!=null&&!s.trim().isEmpty())return s.trim();return null;}

    private static class Reading{final double value;final String path;Reading(double v,String p){value=v;path=p;}}
    private static class Supply{final String name;final Map<String,String> values=new LinkedHashMap<>();Supply(String n){name=n;}String get(String k){return values.get(k);}}
    private static class PowerSnapshot{final List<Supply> supplies=new ArrayList<>();Supply bestExternal;}
}
