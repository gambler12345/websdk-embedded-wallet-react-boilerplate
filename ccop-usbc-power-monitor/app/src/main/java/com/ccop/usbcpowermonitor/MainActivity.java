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
    private BatteryManager batteryManager;
    private TextView hero, sub, diag;
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
            sub.setText("Safe-Start abgeschlossen · Live-Daten werden geladen");
        }catch(Throwable t){
            setContentView(buildEmergencyUi(t));
        }
    }

    @Override protected void onResume(){
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.postDelayed(ticker,250);
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
        root.setPadding(dp(14),dp(28),dp(14),dp(30));
        scroll.addView(root,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("CCOP USB-C LadeMonitor · v4 SAFE",23,TEXT,true));
        TextView intro=text("Startstabil · keine Demo-Werte · Ladegerät, Akku und Profil strikt getrennt",12,MUTED,false);
        intro.setPadding(0,dp(4),0,dp(12)); root.addView(intro);

        LinearLayout status=card();
        status.addView(title("STATUS"));
        hero=text("STARTET…",35,TEXT,true); hero.setPadding(0,dp(8),0,dp(2)); status.addView(hero);
        sub=text("Oberfläche wird initialisiert",13,MUTED,false); sub.setPadding(0,0,0,dp(10)); status.addView(sub);
        metric(status,"plug","Android meldet Quelle");
        metric(status,"charging","Aktiver Ladezyklus");
        metric(status,"systemNode","Externer Messknoten");
        root.addView(status,mb());

        LinearLayout source=card();
        source.addView(title("LADEGERÄT / USB-C · ECHTE QUELLENSEITE"));
        TextView strict=text("Nur VBUS/IBUS/USB/PD-Werte aus externen Systemknoten. Die 4,x-V-Akkuspannung wird hier niemals als Ladegeräte-Spannung verwendet.",12,WARN,true);
        strict.setPadding(0,dp(4),0,dp(9)); source.addView(strict);
        metric(source,"sourceVoltage","Quellenspannung");
        metric(source,"sourceCurrentA","Quellenstrom · A");
        metric(source,"sourceCurrentMa","Quellenstrom · mA");
        metric(source,"sourcePower","Quellenleistung · W");
        metric(source,"sourcePath","Datenpfad");
        metric(source,"sourceType","USB / PD Typ");
        root.addView(source,mb());

        LinearLayout battery=card();
        battery.addView(title("SMARTPHONE-AKKU · LIVE"));
        metric(battery,"batteryPct","Ladezustand");
        metric(battery,"batteryVoltage","Akkuspannung");
        metric(battery,"batteryCurrentA","Netto-Akkustrom · A");
        metric(battery,"batteryCurrentMa","Netto-Akkustrom · mA");
        metric(battery,"batteryPower","Netto-Akkuleistung · W");
        metric(battery,"chargeCounter","Charge Counter");
        metric(battery,"temperature","Temperatur");
        root.addView(battery,mb());

        LinearLayout profile=card();
        profile.addView(title("ANDROID LADEPROFIL / MAX · NICHT LIVE"));
        TextView note=text("Diese Werte sind Profil-/Grenzwerte. Gleiche 5 V / 2 A bei mehreren Geräten bedeuten nicht, dass live immer 5 V / 2 A fließen.",12,WARN,true);
        note.setPadding(0,dp(4),0,dp(9)); profile.addView(note);
        metric(profile,"maxVoltage","Gemeldete max. Spannung");
        metric(profile,"maxCurrent","Gemeldeter max. Strom");
        metric(profile,"maxPower","Rechnerische max. Leistung");
        root.addView(profile,mb());

        LinearLayout actions=new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh=button("JETZT PRÜFEN"); refresh.setOnClickListener(v->{lastSystemScan=0;refreshSafe();});
        actions.addView(refresh,new LinearLayout.LayoutParams(0,dp(50),1f));
        Button share=button("DIAGNOSE TEILEN"); share.setOnClickListener(v->shareDiagnostics());
        LinearLayout.LayoutParams slp=new LinearLayout.LayoutParams(0,dp(50),1f); slp.setMargins(dp(8),0,0,0); actions.addView(share,slp);
        root.addView(actions,mb());

        LinearLayout d=card();
        d.addView(title("RAW-DIAGNOSE"));
        TextView hint=text("Wenn Quellenspannung hier NICHT AUSLESBAR ist, wird bewusst kein Akku- oder Profilwert als Ersatz eingesetzt.",11,MUTED,false);
        hint.setPadding(0,dp(4),0,dp(8)); d.addView(hint);
        diag=text("Noch keine Diagnose",10,TEXT,false); diag.setTypeface(Typeface.MONOSPACE); diag.setTextIsSelectable(true); d.addView(diag);
        root.addView(d,mb());

        TextView footer=text("SAFE START · Android BatteryManager + /sys/class/power_supply · keine Cloud",10,MUTED,false);
        footer.setGravity(Gravity.CENTER); root.addView(footer);
        return scroll;
    }

    private View buildEmergencyUi(Throwable t){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(24),dp(48),dp(24),dp(24)); root.setBackgroundColor(BG);
        root.addView(text("CCOP LadeMonitor · SAFE MODE",24,OK,true));
        TextView msg=text("Die App konnte die Hauptansicht nicht vollständig laden, bleibt aber geöffnet.\n\nFehler: "+t.getClass().getSimpleName()+"\n"+String.valueOf(t.getMessage()),14,TEXT,false);
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
        double battA=hasCurrent?nowUa/1_000_000.0:Double.NaN;
        double battMa=hasCurrent?nowUa/1000.0:Double.NaN;
        double battW=(!Double.isNaN(battV)&&hasCurrent)?Math.abs(battV*battA):Double.NaN;

        long now=System.currentTimeMillis();
        if(now-lastSystemScan>5000||lastSystemScan==0){ cachedSnapshot=scanPowerSupplies(); lastSystemScan=now; }
        Reading srcV=findExternalReading(cachedSnapshot,true);
        Reading srcA=findExternalReading(cachedSnapshot,false);
        Double srcW=(srcV!=null&&srcA!=null)?Math.abs(srcV.value*srcA.value):null;

        if(charging){hero.setText("LADEN AKTIV");hero.setTextColor(OK);}
        else if(pluggedNow){hero.setText("QUELLE VERBUNDEN");hero.setTextColor(WARN);}
        else{hero.setText("NICHT VERBUNDEN");hero.setTextColor(TEXT);}
        sub.setText(srcV!=null?"Verifizierte externe Quellenspannung: "+srcV.path:(pluggedNow?"Laden erkannt · echte VBUS-Spannung derzeit nicht freigegeben":"Keine externe Quelle aktiv"));
        set("plug",pluggedNow?plugName(plugged):"keine",pluggedNow?OK:MUTED);
        set("charging",charging?"Ja":"Nein",charging?OK:MUTED);
        set("systemNode",cachedSnapshot.bestExternal!=null?cachedSnapshot.bestExternal.name:"kein externer Knoten",cachedSnapshot.bestExternal!=null?BLUE:MUTED);

        if(srcV!=null){set("sourceVoltage",fmt(srcV.value,3)+" V · LIVE",OK);set("sourcePath",srcV.path,BLUE);}else{set("sourceVoltage","NICHT AUSLESBAR",RED);set("sourcePath","kein echter VBUS-/USB-Spannungspfad",MUTED);}
        if(srcA!=null){set("sourceCurrentA",signed(srcA.value,3)+" A · LIVE",OK);set("sourceCurrentMa",signed(srcA.value*1000,0)+" mA · LIVE",OK);if(srcV==null)set("sourcePath",srcA.path,BLUE);}else{set("sourceCurrentA","NICHT AUSLESBAR",RED);set("sourceCurrentMa","NICHT AUSLESBAR",RED);}
        set("sourcePower",srcW!=null?fmt(srcW,2)+" W · LIVE":"nicht berechenbar",srcW!=null?OK:MUTED);
        String type=cachedSnapshot.bestExternal!=null?first(cachedSnapshot.bestExternal.get("usb_type"),cachedSnapshot.bestExternal.get("real_type"),cachedSnapshot.bestExternal.get("type")):null;
        set("sourceType",type!=null?type:"nicht gemeldet",type!=null?BLUE:MUTED);

        set("batteryPct",Double.isNaN(pct)?"nicht gemeldet":fmt(pct,0)+" %",TEXT);
        set("batteryVoltage",Double.isNaN(battV)?"nicht gemeldet":fmt(battV,3)+" V · AKKU",Double.isNaN(battV)?MUTED:OK);
        set("batteryCurrentA",Double.isNaN(battA)?"nicht gemeldet":signed(battA,3)+" A",Double.isNaN(battA)?MUTED:OK);
        set("batteryCurrentMa",Double.isNaN(battMa)?"nicht gemeldet":signed(battMa,0)+" mA",Double.isNaN(battMa)?MUTED:OK);
        set("batteryPower",Double.isNaN(battW)?"nicht berechenbar":fmt(battW,2)+" W",Double.isNaN(battW)?MUTED:OK);
        set("chargeCounter",validProperty(counterUah)?fmt(counterUah/1000.0,0)+" mAh":"nicht gemeldet",validProperty(counterUah)?TEXT:MUTED);
        set("temperature",temp10!=Integer.MIN_VALUE?fmt(temp10/10.0,1)+" °C":"nicht gemeldet",TEXT);

        Double maxV=maxVoltageRaw>0?normalizeVoltage(maxVoltageRaw):null;
        Double maxA=maxCurrentRaw>0?normalizeCurrent(maxCurrentRaw):null;
        set("maxVoltage",maxV!=null?fmt(maxV,2)+" V · PROFIL":"nicht gemeldet",maxV!=null?BLUE:MUTED);
        set("maxCurrent",maxA!=null?fmt(maxA,2)+" A · PROFIL":"nicht gemeldet",maxA!=null?BLUE:MUTED);
        set("maxPower",maxV!=null&&maxA!=null?fmt(Math.abs(maxV*maxA),2)+" W · RECHNERISCH":"nicht berechenbar",maxV!=null&&maxA!=null?BLUE:MUTED);

        latestDiagnostics=diagnostics(battery,cachedSnapshot,srcV,srcA,nowUa,maxVoltageRaw,maxCurrentRaw);
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
            String[] keys={"type","usb_type","real_type","online","present","status","voltage_now","voltage_avg","vbus_voltage","vbus_voltage_now","voltage_vbus","usb_voltage","input_voltage","charger_voltage","pd_voltage","pd_voltage_now","current_now","current_avg","ibus_current","ibus_current_now","current_ibus","usb_current","input_current","charger_current","pd_current","pd_current_now","power_now"};
            for(String k:keys){String v=readText(new File(d,k));if(v!=null)s.values.put(k,v);}
            return s.values.isEmpty()?null:s;
        }catch(Throwable t){return null;}
    }

    private boolean isExternal(Supply s){
        String n=s.name.toLowerCase(Locale.ROOT);String type=String.valueOf(s.get("type")).toLowerCase(Locale.ROOT);
        return !(n.contains("battery")||n.contains("bms")||type.contains("battery"));
    }

    private Reading findExternalReading(PowerSnapshot snap,boolean voltage){
        String[] keys=voltage?new String[]{"vbus_voltage_now","vbus_voltage","voltage_vbus","usb_voltage","input_voltage","charger_voltage","pd_voltage_now","pd_voltage","voltage_now","voltage_avg"}:new String[]{"ibus_current_now","ibus_current","current_ibus","usb_current","input_current","charger_current","pd_current_now","pd_current","current_now","current_avg"};
        Reading best=null;int bestScore=-9999;
        for(Supply s:snap.supplies){
            if(!isExternal(s))continue;
            int base="1".equals(s.get("online"))?100:10;
            String name=s.name.toLowerCase(Locale.ROOT);if(name.contains("usb")||name.contains("pd")||name.contains("charger")||name.contains("main"))base+=30;
            for(int i=0;i<keys.length;i++){
                Double raw=parse(s.get(keys[i]));if(raw==null||raw==0)continue;
                double val=voltage?normalizeVoltage(raw):normalizeCurrent(raw);
                if(voltage&&(Math.abs(val)<1||Math.abs(val)>30))continue;
                if(!voltage&&(Math.abs(val)<0.0005||Math.abs(val)>15))continue;
                int score=base+(keys.length-i)*3;
                if(score>bestScore){bestScore=score;best=new Reading(val,"/sys/class/power_supply/"+s.name+"/"+keys[i]);}
            }
        }
        return best;
    }

    private String diagnostics(Intent battery,PowerSnapshot snap,Reading v,Reading a,long nowUa,int maxV,int maxA){
        StringBuilder sb=new StringBuilder();
        sb.append("CCOP LadeMonitor v4 SAFE\n");
        sb.append("Zeit: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.GERMANY).format(new Date())).append('\n');
        sb.append("SOURCE_V=").append(v==null?"UNAVAILABLE":v.value+" @ "+v.path).append('\n');
        sb.append("SOURCE_A=").append(a==null?"UNAVAILABLE":a.value+" @ "+a.path).append('\n');
        sb.append("BATTERY_CURRENT_NOW_uA=").append(nowUa).append('\n');
        sb.append("PROFILE_MAX_V_RAW=").append(maxV).append('\n');
        sb.append("PROFILE_MAX_A_RAW=").append(maxA).append("\n\n");
        for(Supply s:snap.supplies){sb.append('[').append(s.name).append("]\n");for(Map.Entry<String,String> e:s.values.entrySet())sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');}
        return sb.toString();
    }

    private long safeProperty(int id){try{return batteryManager==null?Long.MIN_VALUE:batteryManager.getIntProperty(id);}catch(Throwable t){return Long.MIN_VALUE;}}
    private boolean validProperty(long v){return v!=Long.MIN_VALUE&&v!=Integer.MIN_VALUE;}
    private String readText(File f){try{if(!f.exists()||!f.isFile()||!f.canRead())return null;BufferedReader br=new BufferedReader(new FileReader(f));String x=br.readLine();br.close();return x==null?null:x.trim();}catch(Throwable t){return null;}}
    private Double parse(String s){try{return s==null?null:Double.parseDouble(s.trim());}catch(Throwable t){return null;}}
    private double normalizeVoltage(double raw){double a=Math.abs(raw);if(a>100000)return raw/1_000_000.0;if(a>100)return raw/1000.0;return raw;}
    private double normalizeCurrent(double raw){double a=Math.abs(raw);if(a>100000)return raw/1_000_000.0;if(a>100)return raw/1000.0;return raw;}

    private void shareDiagnostics(){try{Intent i=new Intent(Intent.ACTION_SEND);i.setType("text/plain");i.putExtra(Intent.EXTRA_SUBJECT,"CCOP LadeMonitor v4 Diagnose");i.putExtra(Intent.EXTRA_TEXT,latestDiagnostics);startActivity(Intent.createChooser(i,"Diagnose teilen"));}catch(Throwable ignored){}}
    private void metric(LinearLayout parent,String key,String label){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(8),0,dp(8));TextView l=text(label,13,MUTED,false),v=text("—",14,TEXT,true);v.setGravity(Gravity.END);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));r.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1.2f));fields.put(key,v);parent.addView(r);}
    private void set(String key,String value,int color){TextView t=fields.get(key);if(t!=null){t.setText(value);t.setTextColor(color);}}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable g=new GradientDrawable();g.setColor(PANEL);g.setCornerRadius(dp(24));g.setStroke(dp(1),LINE);c.setBackground(g);return c;}
    private TextView title(String s){TextView t=text(s,12,MUTED,true);t.setLetterSpacing(.1f);return t;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable g=new GradientDrawable();g.setColor(Color.rgb(24,32,40));g.setCornerRadius(dp(15));g.setStroke(dp(1),LINE);b.setBackground(g);return b;}
    private LinearLayout.LayoutParams mb(){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);p.setMargins(0,0,0,dp(12));return p;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String plugName(int p){if((p&BatteryManager.BATTERY_PLUGGED_AC)!=0)return "Netzteil / AC";if((p&BatteryManager.BATTERY_PLUGGED_USB)!=0)return "USB";if((p&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0)return "Wireless";return p==0?"keine":"extern";}
    private String fmt(double v,int d){return String.format(Locale.GERMANY,"%."+d+"f",v);} private String signed(double v,int d){return String.format(Locale.GERMANY,"%+."+d+"f",v);} private String first(String... xs){for(String x:xs)if(x!=null&&!x.trim().isEmpty())return x.trim();return null;}

    private static class Supply{final String name;final Map<String,String> values=new LinkedHashMap<>();Supply(String n){name=n;}String get(String k){return values.get(k);}}
    private static class PowerSnapshot{final List<Supply> supplies=new ArrayList<>();Supply bestExternal;}
    private static class Reading{final double value;final String path;Reading(double v,String p){value=v;path=p;}}
}
