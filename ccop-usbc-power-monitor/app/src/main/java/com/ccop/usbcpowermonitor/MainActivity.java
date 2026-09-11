package com.ccop.usbcpowermonitor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(8,12,16), PANEL = Color.rgb(17,23,29), PANEL2 = Color.rgb(12,18,24);
    private static final int LINE = Color.rgb(43,56,68), TEXT = Color.rgb(247,249,252), MUTED = Color.rgb(151,164,178);
    private static final int OK = Color.rgb(72,229,139), WARN = Color.rgb(255,210,105), BLUE = Color.rgb(117,164,255), RED = Color.rgb(255,114,114);

    private enum FocusMode { SOURCE, BATTERY, PROFILE }
    private enum Metric { VOLTAGE, CURRENT, POWER }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String,TextView> values = new HashMap<>();
    private final List<Button> focusButtons = new ArrayList<>();
    private final List<Double> rollingBatteryMa = new ArrayList<>(), rollingBatteryW = new ArrayList<>(), rollingSourceV = new ArrayList<>();

    private BatteryManager batteryManager;
    private UsbManager usbManager;
    private FocusMode focusMode = FocusMode.SOURCE;
    private TextView heroStatus, heroSub, focusTitle, focusQuality, diagText;
    private boolean wasPlugged = false;
    private int sessionId = 0;
    private long sessionStartedMs = 0L, lastTickMs = 0L;
    private double sessionBatteryWh = 0.0;
    private String latestDiagnostics = "Noch keine Diagnose.";

    private final Runnable ticker = new Runnable() {
        @Override public void run() { refreshTelemetry(); handler.postDelayed(this, 1000); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        lastTickMs = SystemClock.elapsedRealtime();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() { handler.removeCallbacks(ticker); super.onPause(); }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(26), dp(14), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("CCOP USB-C LadeMonitor · VERIFIED", 23, TEXT, true));
        TextView sub = text("Quellenspannung ≠ Akkuspannung ≠ Ladeprofil · keine Ersatzwerte", 12, MUTED, false);
        sub.setPadding(0,dp(3),0,dp(12)); root.addView(sub);

        LinearLayout hero = card();
        hero.addView(sectionTitle("VERBINDUNGSSTATUS"));
        heroStatus = text("PRÜFE…",35,TEXT,true); heroStatus.setPadding(0,dp(8),0,dp(2)); hero.addView(heroStatus);
        heroSub = text("Systemdaten werden geprüft",13,MUTED,false); heroSub.setPadding(0,0,0,dp(10)); hero.addView(heroSub);
        addMetric(hero,"plug","Android meldet Quelle");
        addMetric(hero,"charging","Aktiver Ladezyklus");
        addMetric(hero,"externalNode","Externer Messknoten");
        root.addView(hero,mb());

        LinearLayout selector = card();
        selector.addView(sectionTitle("MESSOBJEKT AUSWÄHLEN"));
        LinearLayout row = horizontalButtons();
        addFocusButton(row,"LADEGERÄT / USB-C",FocusMode.SOURCE);
        addFocusButton(row,"SMARTPHONE-AKKU",FocusMode.BATTERY);
        addFocusButton(row,"PROFIL / MAX",FocusMode.PROFILE);
        selector.addView(wrapHorizontal(row)); root.addView(selector,mb()); refreshButtonStyles();

        LinearLayout focus = card();
        focusTitle = sectionTitle("AUSGEWÄHLTER MESSFOKUS"); focus.addView(focusTitle);
        focusQuality = text("Prüfe Datenherkunft…",12,MUTED,true); focusQuality.setPadding(0,dp(5),0,dp(8)); focus.addView(focusQuality);
        addMetric(focus,"focusVoltage","Spannung"); addMetric(focus,"focusCurrentA","Strom · A");
        addMetric(focus,"focusCurrentMa","Strom · mA"); addMetric(focus,"focusPower","Leistung · W"); addMetric(focus,"focusOrigin","Datenherkunft");
        root.addView(focus,mb());

        LinearLayout source = card();
        source.addView(sectionTitle("LADEGERÄT / USB-C · QUELLENSEITE"));
        TextView strict = text("Nur externe VBUS/USB/PD-Werte. Die 4,x-V-Akkuspannung wird hier niemals eingesetzt.",12,WARN,true); strict.setPadding(0,dp(4),0,dp(8)); source.addView(strict);
        addMetric(source,"sourceVoltage","Quellenspannung · LIVE"); addMetric(source,"sourceCurrentA","Quellenstrom · LIVE");
        addMetric(source,"sourceCurrentMa","Quellenstrom · mA"); addMetric(source,"sourcePower","Quellenleistung");
        addMetric(source,"sourceVoltagePath","Spannungsquelle / Pfad"); addMetric(source,"sourceCurrentPath","Stromquelle / Pfad"); addMetric(source,"usbType","USB / PD Typ");
        root.addView(source,mb());

        LinearLayout battery = card();
        battery.addView(sectionTitle("SMARTPHONE-AKKU · BATTERYMANAGER"));
        addMetric(battery,"batteryPct","Ladezustand"); addMetric(battery,"batteryVoltage","Akkuspannung · LIVE");
        addMetric(battery,"batteryCurrentA","Netto-Akkustrom · LIVE"); addMetric(battery,"batteryCurrentMa","Netto-Akkustrom · mA");
        addMetric(battery,"batteryPower","Netto-Akkuleistung"); addMetric(battery,"batteryAvg","Mittlerer Akkustrom");
        addMetric(battery,"chargeCounter","Charge Counter"); addMetric(battery,"temperature","Temperatur"); root.addView(battery,mb());

        LinearLayout profile = card();
        profile.addView(sectionTitle("LADEPROFIL / LIMITS · NICHT LIVE"));
        TextView pw = text("5 V / 2 A hier sind Profil-/Grenzwerte, keine gemessene Ladegeräte-Spannung.",12,WARN,true); pw.setPadding(0,dp(4),0,dp(8)); profile.addView(pw);
        addMetric(profile,"maxVoltage","Gemeldete max. Spannung"); addMetric(profile,"maxCurrent","Gemeldeter max. Strom");
        addMetric(profile,"maxPower","Max. rechnerische Leistung"); addMetric(profile,"pdProfile","PD / Charger Type"); root.addView(profile,mb());

        LinearLayout compare = card();
        compare.addView(sectionTitle("KABEL-/LADEGERÄT-VERGLEICH"));
        addMetric(compare,"session","Testsitzung"); addMetric(compare,"fingerprint","Verbindungs-Signatur");
        addMetric(compare,"avgCurrent","Ø Akkustrom · letzte 20 s"); addMetric(compare,"avgPower","Ø Akkuleistung · letzte 20 s");
        addMetric(compare,"sourceRange","Quellenspannung · Min–Max"); addMetric(compare,"sessionEnergy","Energie zum Akku · Sitzung");
        TextView cmp = text("Für Vergleich: Akku möglichst 20–70 %, gleicher Bildschirmzustand, je Kabel/Ladegerät 20–30 s warten.",11,MUTED,false); cmp.setPadding(0,dp(8),0,0); compare.addView(cmp);
        root.addView(compare,mb());

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("JETZT PRÜFEN"); refresh.setOnClickListener(v -> refreshTelemetry()); actions.addView(refresh,new LinearLayout.LayoutParams(0,dp(50),1f));
        Button share = button("DIAGNOSE TEILEN"); share.setOnClickListener(v -> shareDiagnostics()); LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(0,dp(50),1f); sp.setMargins(dp(8),0,0,0); actions.addView(share,sp); root.addView(actions,mb());

        LinearLayout diag = card();
        diag.addView(sectionTitle("RAW-DIAGNOSE · POWER_SUPPLY + TYPE-C + USB"));
        TextView dh = text("Fehlt VBUS/IBUS hier, gibt Android/OEM den echten Quellwert der App nicht frei.",11,MUTED,false); dh.setPadding(0,dp(3),0,dp(8)); diag.addView(dh);
        diagText = text("Prüfe…",10,TEXT,false); diagText.setTypeface(Typeface.MONOSPACE); diagText.setTextIsSelectable(true); diag.addView(diagText); root.addView(diag,mb());

        TextView footer = text("Local First · keine Demo-Werte · Quellenseite und Akkuseite strikt getrennt",10,MUTED,false); footer.setGravity(Gravity.CENTER); root.addView(footer);
        return scroll;
    }

    private void refreshTelemetry() {
        Intent battery = registerReceiver(null,new IntentFilter(Intent.ACTION_BATTERY_CHANGED)); if (battery == null) return;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL,-1), scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE,100);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS,BatteryManager.BATTERY_STATUS_UNKNOWN), plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0);
        int battMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE,-1), temp10 = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Integer.MIN_VALUE);
        int maxCurrentUa = battery.getIntExtra("max_charging_current",-1), maxVoltageUv = battery.getIntExtra("max_charging_voltage",-1);
        boolean pluggedNow = plugged != 0, charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        if (pluggedNow && !wasPlugged) startSession(); if (!pluggedNow && wasPlugged) endSession(); wasPlugged = pluggedNow;

        double pct = level >= 0 && scale > 0 ? 100.0*level/scale : Double.NaN, batteryV = battMv > 0 ? battMv/1000.0 : Double.NaN;
        long nowUa = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW), avgUa = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE), chargeUah = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        boolean hasNow = validBatteryProperty(nowUa); double batteryA = hasNow ? nowUa/1_000_000.0 : Double.NaN, batteryMa = hasNow ? nowUa/1000.0 : Double.NaN;
        double batteryW = (!Double.isNaN(batteryV) && hasNow) ? Math.abs(batteryV*batteryA) : Double.NaN;

        PowerSnapshot sys = readPowerSupplies();
        Reading srcV = findReading(sys,Metric.VOLTAGE), srcA = findReading(sys,Metric.CURRENT), srcP = findReading(sys,Metric.POWER);
        Double sourceW = srcP != null ? srcP.value : (srcV != null && srcA != null ? Math.abs(srcV.value*srcA.value) : null);

        if (charging) { heroStatus.setText("LADEN AKTIV"); heroStatus.setTextColor(OK); }
        else if (pluggedNow) { heroStatus.setText("QUELLE VERBUNDEN"); heroStatus.setTextColor(WARN); }
        else { heroStatus.setText("NICHT VERBUNDEN"); heroStatus.setTextColor(TEXT); }
        heroSub.setText(srcV != null ? "Echte Quellenspannung gefunden: "+srcV.path : (pluggedNow ? "Laden erkannt, aber keine direkte Quellenspannung freigegeben." : "Keine externe Quelle aktiv."));
        set("plug",pluggedNow?pluggedName(plugged):"keine externe Quelle",pluggedNow?OK:MUTED); set("charging",charging?"Ja":"Nein",charging?OK:MUTED);
        set("externalNode",sys.bestExternal!=null?sys.bestExternal.name:"kein externer Messknoten",sys.bestExternal!=null?BLUE:MUTED);

        renderSource(srcV,srcA,sourceW,sys,battery); renderBattery(pct,batteryV,batteryA,batteryMa,batteryW,avgUa,chargeUah,temp10); renderProfile(maxVoltageUv,maxCurrentUa,sys,battery);
        updateRolling(charging,batteryMa,batteryW,srcV); updateEnergy(charging,batteryW); renderComparison(maxVoltageUv,maxCurrentUa,sys,battery,srcV);
        renderFocus(srcV,srcA,sourceW,batteryV,batteryA,batteryMa,batteryW,maxVoltageUv,maxCurrentUa);
        latestDiagnostics = buildDiagnostics(battery,sys,nowUa,avgUa,chargeUah,maxVoltageUv,maxCurrentUa,srcV,srcA); diagText.setText(latestDiagnostics);
    }

    private void renderSource(Reading v, Reading a, Double w, PowerSnapshot sys, Intent battery) {
        if (v != null) { set("sourceVoltage",fmt(v.value,3)+" V · LIVE",OK); set("sourceVoltagePath",v.path,BLUE); }
        else { set("sourceVoltage","NICHT AUSLESBAR",RED); set("sourceVoltagePath","kein direkter VBUS-/USB-Spannungswert",MUTED); }
        if (a != null) { set("sourceCurrentA",signedFmt(a.value,3)+" A · LIVE",OK); set("sourceCurrentMa",signedFmt(a.value*1000.0,0)+" mA · LIVE",OK); set("sourceCurrentPath",a.path,BLUE); }
        else { set("sourceCurrentA","NICHT AUSLESBAR",RED); set("sourceCurrentMa","NICHT AUSLESBAR",RED); set("sourceCurrentPath","kein direkter IBUS-/USB-Stromwert",MUTED); }
        set("sourcePower",w!=null?fmt(Math.abs(w),2)+" W":"nicht berechenbar",w!=null?OK:MUTED);
        String type = bestUsbType(sys,battery); set("usbType",type!=null?type:"nicht gemeldet",type!=null?BLUE:MUTED);
    }

    private void renderBattery(double pct,double v,double a,double ma,double w,long avgUa,long chargeUah,int temp10) {
        set("batteryPct",Double.isNaN(pct)?"nicht gemeldet":fmt(pct,0)+" %",TEXT); set("batteryVoltage",Double.isNaN(v)?"nicht gemeldet":fmt(v,3)+" V · AKKU",Double.isNaN(v)?MUTED:OK);
        set("batteryCurrentA",Double.isNaN(a)?"nicht gemeldet":signedFmt(a,3)+" A",Double.isNaN(a)?MUTED:OK); set("batteryCurrentMa",Double.isNaN(ma)?"nicht gemeldet":signedFmt(ma,0)+" mA",Double.isNaN(ma)?MUTED:OK);
        set("batteryPower",Double.isNaN(w)?"nicht berechenbar":fmt(w,2)+" W",Double.isNaN(w)?MUTED:OK); set("batteryAvg",validBatteryProperty(avgUa)?signedFmt(avgUa/1000.0,0)+" mA":"nicht gemeldet",validBatteryProperty(avgUa)?TEXT:MUTED);
        set("chargeCounter",validBatteryProperty(chargeUah)?fmt(chargeUah/1000.0,0)+" mAh":"nicht gemeldet",validBatteryProperty(chargeUah)?TEXT:MUTED); set("temperature",temp10!=Integer.MIN_VALUE?fmt(temp10/10.0,1)+" °C":"nicht gemeldet",TEXT);
    }

    private void renderProfile(int maxVRaw,int maxARaw,PowerSnapshot sys,Intent battery) {
        Double v = maxVRaw>0?normalizeVoltageRaw(maxVRaw):null, a = maxARaw>0?normalizeCurrentRaw(maxARaw):null;
        set("maxVoltage",v!=null?fmt(v,2)+" V · PROFIL/MAX":"nicht gemeldet",v!=null?BLUE:MUTED); set("maxCurrent",a!=null?fmt(a,2)+" A · PROFIL/MAX":"nicht gemeldet",a!=null?BLUE:MUTED);
        set("maxPower",v!=null&&a!=null?fmt(Math.abs(v*a),2)+" W · RECHNERISCH":"nicht berechenbar",v!=null&&a!=null?BLUE:MUTED);
        String p=bestUsbType(sys,battery); set("pdProfile",p!=null?p:"nicht gemeldet",p!=null?BLUE:MUTED);
    }

    private void renderFocus(Reading sv,Reading sa,Double sw,double bv,double ba,double bma,double bw,int maxVRaw,int maxARaw) {
        if (focusMode==FocusMode.SOURCE) {
            focusTitle.setText("AUSGEWÄHLT · LADEGERÄT / USB-C"); boolean exact=sv!=null||sa!=null;
            focusQuality.setText(exact?"DIREKTE EXTERNE SYSTEMTELEMETRIE":"KEIN DIREKTER QUELLENMESSWERT · kein Akku-Ersatzwert"); focusQuality.setTextColor(exact?OK:RED);
            set("focusVoltage",sv!=null?fmt(sv.value,3)+" V":"NICHT AUSLESBAR",sv!=null?OK:RED); set("focusCurrentA",sa!=null?signedFmt(sa.value,3)+" A":"NICHT AUSLESBAR",sa!=null?OK:RED);
            set("focusCurrentMa",sa!=null?signedFmt(sa.value*1000.0,0)+" mA":"NICHT AUSLESBAR",sa!=null?OK:RED); set("focusPower",sw!=null?fmt(Math.abs(sw),2)+" W":"nicht berechenbar",sw!=null?OK:MUTED);
            set("focusOrigin",sv!=null?sv.path:(sa!=null?sa.path:"Android/OEM gibt VBUS/IBUS nicht frei"),BLUE);
        } else if (focusMode==FocusMode.BATTERY) {
            focusTitle.setText("AUSGEWÄHLT · SMARTPHONE-AKKU"); focusQuality.setText("BATTERYMANAGER · AKKUSEITE, NICHT LADEGERÄT"); focusQuality.setTextColor(OK);
            set("focusVoltage",!Double.isNaN(bv)?fmt(bv,3)+" V · AKKU":"nicht gemeldet",!Double.isNaN(bv)?OK:MUTED); set("focusCurrentA",!Double.isNaN(ba)?signedFmt(ba,3)+" A":"nicht gemeldet",!Double.isNaN(ba)?OK:MUTED);
            set("focusCurrentMa",!Double.isNaN(bma)?signedFmt(bma,0)+" mA":"nicht gemeldet",!Double.isNaN(bma)?OK:MUTED); set("focusPower",!Double.isNaN(bw)?fmt(bw,2)+" W":"nicht berechenbar",!Double.isNaN(bw)?OK:MUTED); set("focusOrigin","ACTION_BATTERY_CHANGED + BatteryManager",BLUE);
        } else {
            focusTitle.setText("AUSGEWÄHLT · PROFIL / MAX"); focusQuality.setText("ANDROID-LADEPROFIL · KEINE LIVE-MESSUNG"); focusQuality.setTextColor(WARN);
            Double v=maxVRaw>0?normalizeVoltageRaw(maxVRaw):null,a=maxARaw>0?normalizeCurrentRaw(maxARaw):null;
            set("focusVoltage",v!=null?fmt(v,2)+" V · MAX":"nicht gemeldet",v!=null?BLUE:MUTED); set("focusCurrentA",a!=null?fmt(a,2)+" A · MAX":"nicht gemeldet",a!=null?BLUE:MUTED);
            set("focusCurrentMa",a!=null?fmt(a*1000.0,0)+" mA · MAX":"nicht gemeldet",a!=null?BLUE:MUTED); set("focusPower",v!=null&&a!=null?fmt(v*Math.abs(a),2)+" W · MAX":"nicht berechenbar",v!=null&&a!=null?BLUE:MUTED); set("focusOrigin","Battery broadcast: max_charging_*",BLUE);
        }
    }

    private void startSession(){ sessionId++; sessionStartedMs=System.currentTimeMillis(); sessionBatteryWh=0; rollingBatteryMa.clear(); rollingBatteryW.clear(); rollingSourceV.clear(); }
    private void endSession(){ rollingBatteryMa.clear(); rollingBatteryW.clear(); rollingSourceV.clear(); lastTickMs=SystemClock.elapsedRealtime(); }
    private void updateRolling(boolean charging,double ma,double w,Reading sv){ if(!charging)return; addRolling(rollingBatteryMa,ma); addRolling(rollingBatteryW,w); if(sv!=null)addRolling(rollingSourceV,sv.value); }
    private void addRolling(List<Double> l,double v){ if(Double.isNaN(v)||Double.isInfinite(v))return; l.add(v); while(l.size()>20)l.remove(0); }
    private double average(List<Double> l){ if(l.isEmpty())return Double.NaN; double s=0; for(double v:l)s+=v; return s/l.size(); }
    private void updateEnergy(boolean charging,double w){ long now=SystemClock.elapsedRealtime(); if(lastTickMs==0)lastTickMs=now; double h=Math.max(0,Math.min(5,(now-lastTickMs)/1000.0))/3600.0; lastTickMs=now; if(charging&&!Double.isNaN(w))sessionBatteryWh+=Math.abs(w)*h; }

    private void renderComparison(int maxVRaw,int maxARaw,PowerSnapshot sys,Intent battery,Reading sv){
        String since=sessionStartedMs>0?new SimpleDateFormat("HH:mm:ss",Locale.GERMANY).format(new Date(sessionStartedMs)):"—"; set("session",sessionId>0?"#"+sessionId+" · seit "+since:"noch keine",TEXT);
        Double mv=maxVRaw>0?normalizeVoltageRaw(maxVRaw):null,ma=maxARaw>0?normalizeCurrentRaw(maxARaw):null; String type=bestUsbType(sys,battery);
        String fp=(type!=null?type:pluggedName(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)))+" | Profil "+(mv!=null?fmt(mv,2)+"V":"?")+"×"+(ma!=null?fmt(ma,2)+"A":"?")+" | VBUS "+(sv!=null?fmt(sv.value,3)+"V":"gesperrt"); set("fingerprint",fp,BLUE);
        double ac=average(rollingBatteryMa),ap=average(rollingBatteryW); set("avgCurrent",Double.isNaN(ac)?"warte auf Daten":signedFmt(ac,0)+" mA",Double.isNaN(ac)?MUTED:OK); set("avgPower",Double.isNaN(ap)?"warte auf Daten":fmt(ap,2)+" W",Double.isNaN(ap)?MUTED:OK);
        if(rollingSourceV.isEmpty())set("sourceRange","kein direkter VBUS-Wert",RED); else { double mn=rollingSourceV.get(0),mx=mn; for(double x:rollingSourceV){mn=Math.min(mn,x);mx=Math.max(mx,x);} set("sourceRange",fmt(mn,3)+"–"+fmt(mx,3)+" V",OK); }
        set("sessionEnergy",fmt(sessionBatteryWh,4)+" Wh",TEXT);
    }

    private PowerSnapshot readPowerSupplies(){
        PowerSnapshot snap=new PowerSnapshot(); File root=new File("/sys/class/power_supply");
        try{ File[] dirs=root.listFiles(); if(dirs!=null)for(File d:dirs){Supply s=readSupply(d); if(s!=null)snap.supplies.add(s);} }catch(Throwable ignored){}
        int best=Integer.MIN_VALUE; for(Supply s:snap.supplies){int sc=externalScore(s); if(sc>best){best=sc;snap.bestExternal=sc>0?s:null;}} return snap;
    }

    private Supply readSupply(File dir){
        if(dir==null||!dir.exists()||!dir.isDirectory())return null; Supply s=new Supply(dir.getName());
        String[] known={"type","usb_type","real_type","online","present","status","voltage_now","voltage_avg","voltage_ocv","voltage_max","vbus_voltage","vbus_voltage_now","voltage_vbus","usb_voltage","input_voltage","charger_voltage","pd_voltage","pd_voltage_now","pd_active","pd_state","current_now","current_avg","current_max","ibus_current","ibus_current_now","current_ibus","usb_current","input_current","charger_current","pd_current","pd_current_now","power_now","power_avg","power_max","input_voltage_limit","input_current_limit","constant_charge_current","constant_charge_current_max","capacity","manufacturer","model_name","serial_number"};
        for(String k:known){String v=readText(new File(dir,k)); if(v!=null)s.values.put(k,v);} 
        try{File ue=new File(dir,"uevent"); if(ue.canRead()){BufferedReader br=new BufferedReader(new FileReader(ue));String line;while((line=br.readLine())!=null){int eq=line.indexOf('=');if(eq>0){String k=line.substring(0,eq).toLowerCase(Locale.ROOT).replace("power_supply_","");String v=line.substring(eq+1).trim();if(interestingKey(k)&&!v.isEmpty())s.values.putIfAbsent(k,v);}}br.close();}}catch(Throwable ignored){}
        try{File[] fs=dir.listFiles();if(fs!=null){int c=0;for(File f:fs){if(c++>120)break;String k=f.getName().toLowerCase(Locale.ROOT);if(f.isFile()&&interestingKey(k)&&!s.values.containsKey(k)){String v=readText(f);if(v!=null&&v.length()<=160)s.values.put(k,v);}}}}catch(Throwable ignored){}
        return s.values.isEmpty()?null:s;
    }

    private boolean interestingKey(String k){String x=k.toLowerCase(Locale.ROOT);return x.contains("volt")||x.contains("curr")||x.contains("power")||x.contains("vbus")||x.contains("ibus")||x.contains("usb")||x.contains("pd")||x.contains("charge")||x.contains("online")||x.contains("present")||x.contains("status")||x.contains("type")||x.contains("input")||x.contains("model")||x.contains("manufacturer");}
    private int externalScore(Supply s){String n=s.name.toLowerCase(Locale.ROOT),type=lower(s.get("type"));if(n.contains("battery")||n.contains("bms")||type.contains("battery"))return -1000;int sc=1;if("1".equals(s.get("online")))sc+=120;if("1".equals(s.get("present")))sc+=10;if(n.contains("usb")||n.contains("charger")||n.contains("chg")||n.contains("main")||n.contains("ac")||n.contains("pd")||n.contains("typec")||n.contains("dc"))sc+=35;if(type.contains("usb")||type.contains("mains")||type.contains("usb_pd"))sc+=35;if(hasAny(s,voltageKeys()))sc+=50;if(hasAny(s,currentKeys()))sc+=50;return sc;}

    private Reading findReading(PowerSnapshot snap,Metric metric){
        Reading best=null;int bestScore=Integer.MIN_VALUE;String[] keys=metric==Metric.VOLTAGE?voltageKeys():metric==Metric.CURRENT?currentKeys():powerKeys();
        for(Supply s:snap.supplies){int ss=externalScore(s);if(ss<=0)continue;if("0".equals(s.get("online"))&&"0".equals(s.get("present")))continue;for(int i=0;i<keys.length;i++){String k=keys[i];Double raw=parseDouble(s.get(k));if(raw==null||raw==0)continue;double v=metric==Metric.VOLTAGE?normalizeVoltageRaw(raw):metric==Metric.CURRENT?normalizeCurrentRaw(raw):normalizePowerRaw(raw);if(!sane(metric,v))continue;int score=ss+(keys.length-i)*3;if(score>bestScore){bestScore=score;best=new Reading(v,"/sys/class/power_supply/"+s.name+"/"+k,s.name,k);}}}return best;
    }
    private String[] voltageKeys(){return new String[]{"vbus_voltage_now","vbus_voltage","voltage_vbus","usb_voltage","input_voltage","charger_voltage","pd_voltage_now","voltage_now","voltage_avg"};}
    private String[] currentKeys(){return new String[]{"ibus_current_now","ibus_current","current_ibus","usb_current","input_current","charger_current","pd_current_now","current_now","current_avg"};}
    private String[] powerKeys(){return new String[]{"power_now","power_avg"};}
    private boolean sane(Metric m,double v){double a=Math.abs(v);if(m==Metric.VOLTAGE)return a>=1&&a<=30;if(m==Metric.CURRENT)return a>=0.0005&&a<=15;return a>=0.001&&a<=400;}
    private boolean hasAny(Supply s,String[] ks){for(String k:ks)if(s.get(k)!=null)return true;return false;}

    private String bestUsbType(PowerSnapshot sys,Intent battery){if(sys.bestExternal!=null){String x=firstNonEmpty(sys.bestExternal.get("usb_type"),sys.bestExternal.get("real_type"),sys.bestExternal.get("type"));if(x!=null)return x;}return firstNonEmpty(battery.getStringExtra("charger_type"),battery.getStringExtra("usb_type"));}
    private String readTypecDiagnostics(){StringBuilder sb=new StringBuilder();File root=new File("/sys/class/typec");try{File[] ds=root.listFiles();if(ds==null||ds.length==0)return "keine lesbaren TYPE-C-Knoten\n";for(File d:ds){sb.append('[').append(d.getName()).append("]\n");File[] fs=d.listFiles();if(fs==null)continue;int c=0;for(File f:fs){if(c++>80)break;String k=f.getName().toLowerCase(Locale.ROOT);if(!interestingKey(k)&&!k.contains("role")&&!k.contains("mode")&&!k.contains("orientation"))continue;String v=readText(f);if(v!=null&&v.length()<=160)sb.append(k).append('=').append(v).append('\n');}}}catch(Throwable t){sb.append("TYPE-C nicht lesbar: ").append(t.getClass().getSimpleName()).append('\n');}return sb.toString();}
    private String usbDeviceDiagnostics(){StringBuilder sb=new StringBuilder();try{Map<String,UsbDevice> devs=usbManager!=null?usbManager.getDeviceList():null;if(devs==null||devs.isEmpty())return "keine USB-Host-Geräte sichtbar\n";for(UsbDevice d:devs.values()){sb.append(d.getDeviceName()).append(" vid=").append(d.getVendorId()).append(" pid=").append(d.getProductId());try{sb.append(" manufacturer=").append(d.getManufacturerName()).append(" product=").append(d.getProductName());}catch(Throwable ignored){}sb.append('\n');}}catch(Throwable t){sb.append("USB-Liste nicht lesbar: ").append(t.getClass().getSimpleName()).append('\n');}return sb.toString();}

    private String buildDiagnostics(Intent battery,PowerSnapshot sys,long nowUa,long avgUa,long chargeUah,int maxVRaw,int maxARaw,Reading sv,Reading sa){
        StringBuilder sb=new StringBuilder();sb.append("CCOP USB-C LadeMonitor v3 VERIFIED\nZeit: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.GERMANY).format(new Date())).append('\n');
        sb.append("SOURCE_V=").append(sv!=null?sv.value+" @ "+sv.path:"UNAVAILABLE").append('\n').append("SOURCE_A=").append(sa!=null?sa.value+" @ "+sa.path:"UNAVAILABLE").append('\n');
        sb.append("BATTERY_current_now_uA=").append(nowUa).append('\n').append("BATTERY_current_avg_uA=").append(avgUa).append('\n').append("BATTERY_charge_counter_uAh=").append(chargeUah).append('\n');
        sb.append("PROFILE_max_voltage_raw=").append(maxVRaw).append('\n').append("PROFILE_max_current_raw=").append(maxARaw).append('\n');
        Bundle ex=battery.getExtras();if(ex!=null){sb.append("\nBattery broadcast relevant extras\n");for(String k:ex.keySet()){String x=k.toLowerCase(Locale.ROOT);if(interestingKey(x)||x.contains("plug"))sb.append(k).append('=').append(String.valueOf(ex.get(k))).append('\n');}}
        sb.append("\n/sys/class/power_supply\n");if(sys.supplies.isEmpty())sb.append("keine lesbaren Knoten\n");for(Supply s:sys.supplies){sb.append('[').append(s.name).append(" score=").append(externalScore(s)).append("]\n");for(Map.Entry<String,String> e:s.values.entrySet())sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');}
        sb.append("\n/sys/class/typec\n").append(readTypecDiagnostics()).append("\nUsbManager device list\n").append(usbDeviceDiagnostics());return sb.toString();
    }

    private long safeIntProperty(int id){try{return batteryManager.getIntProperty(id);}catch(Throwable t){return Long.MIN_VALUE;}}
    private boolean validBatteryProperty(long v){return v!=Long.MIN_VALUE&&v!=Integer.MIN_VALUE;}
    private String readText(File f){try{if(!f.exists()||!f.isFile()||!f.canRead())return null;BufferedReader br=new BufferedReader(new FileReader(f));String line=br.readLine();br.close();return line==null?null:line.trim();}catch(Throwable t){return null;}}
    private Double parseDouble(String s){if(s==null)return null;try{return Double.parseDouble(s.trim());}catch(Throwable t){return null;}}
    private double normalizeVoltageRaw(double raw){double a=Math.abs(raw);if(a>100000)return raw/1_000_000.0;if(a>100)return raw/1000.0;return raw;}
    private double normalizeCurrentRaw(double raw){double a=Math.abs(raw);if(a>100000)return raw/1_000_000.0;if(a>100)return raw/1000.0;return raw;}
    private double normalizePowerRaw(double raw){double a=Math.abs(raw);if(a>100000)return raw/1_000_000.0;if(a>1000)return raw/1000.0;return raw;}

    private void shareDiagnostics(){Intent send=new Intent(Intent.ACTION_SEND);send.setType("text/plain");send.putExtra(Intent.EXTRA_SUBJECT,"CCOP USB-C LadeMonitor v3 Diagnose");send.putExtra(Intent.EXTRA_TEXT,latestDiagnostics);startActivity(Intent.createChooser(send,"Diagnose teilen"));}
    private void addFocusButton(LinearLayout row,String label,FocusMode mode){Button b=choiceButton(label);b.setTag(mode);b.setOnClickListener(v->{focusMode=mode;refreshButtonStyles();refreshTelemetry();});focusButtons.add(b);row.addView(b,choiceLp());}
    private void refreshButtonStyles(){for(Button b:focusButtons)styleChoice(b,b.getTag()==focusMode);}
    private void styleChoice(Button b,boolean active){GradientDrawable gd=new GradientDrawable();gd.setCornerRadius(dp(14));gd.setColor(active?Color.rgb(25,87,58):PANEL2);gd.setStroke(dp(1),active?OK:LINE);b.setBackground(gd);b.setTextColor(active?OK:TEXT);}
    private LinearLayout horizontalButtons(){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setPadding(0,dp(8),0,0);return r;}
    private HorizontalScrollView wrapHorizontal(LinearLayout row){HorizontalScrollView h=new HorizontalScrollView(this);h.setHorizontalScrollBarEnabled(false);h.addView(row);return h;}
    private LinearLayout.LayoutParams choiceLp(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,dp(46));lp.setMargins(0,0,dp(8),0);return lp;}
    private Button choiceButton(String label){Button b=new Button(this);b.setText(label);b.setTextSize(12);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setAllCaps(false);b.setPadding(dp(14),0,dp(14),0);return b;}
    private void addMetric(LinearLayout p,String key,String label){LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);r.setGravity(Gravity.CENTER_VERTICAL);r.setPadding(0,dp(8),0,dp(8));TextView l=text(label,13,MUTED,false),v=text("—",14,TEXT,true);v.setGravity(Gravity.END);r.addView(l,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1f));r.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1.2f));values.put(key,v);p.addView(r);}
    private void set(String key,String value,int color){TextView v=values.get(key);if(v!=null){v.setText(value);v.setTextColor(color);}}
    private LinearLayout card(){LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(16),dp(16),dp(16));GradientDrawable gd=new GradientDrawable();gd.setColor(PANEL);gd.setCornerRadius(dp(24));gd.setStroke(dp(1),LINE);c.setBackground(gd);return c;}
    private LinearLayout.LayoutParams mb(){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT);lp.setMargins(0,0,0,dp(12));return lp;}
    private TextView sectionTitle(String s){TextView t=text(s,12,MUTED,true);t.setLetterSpacing(0.11f);return t;}
    private TextView text(String s,int sp,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(sp);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);t.setLineSpacing(0,1.08f);return t;}
    private Button button(String label){Button b=new Button(this);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(TEXT);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);GradientDrawable gd=new GradientDrawable();gd.setColor(Color.rgb(24,32,40));gd.setCornerRadius(dp(15));gd.setStroke(dp(1),LINE);b.setBackground(gd);return b;}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private String pluggedName(int p){if((p&BatteryManager.BATTERY_PLUGGED_AC)!=0)return "Netzteil / AC";if((p&BatteryManager.BATTERY_PLUGGED_USB)!=0)return "USB";if((p&BatteryManager.BATTERY_PLUGGED_WIRELESS)!=0)return "Wireless";return p==0?"keine":"extern";}
    private String fmt(double v,int d){return String.format(Locale.GERMANY,"%."+d+"f",v);} private String signedFmt(double v,int d){return String.format(Locale.GERMANY,"%+."+d+"f",v);} private String lower(String s){return s==null?"":s.toLowerCase(Locale.ROOT);}
    private String firstNonEmpty(String...v){if(v==null)return null;for(String s:v)if(s!=null&&!s.trim().isEmpty())return s.trim();return null;}

    private static class Reading{final double value;final String path,supply,key;Reading(double v,String p,String s,String k){value=v;path=p;supply=s;key=k;}}
    private static class Supply{final String name;final Map<String,String> values=new LinkedHashMap<>();Supply(String n){name=n;}String get(String k){return values.get(k);}}
    private static class PowerSnapshot{final List<Supply> supplies=new ArrayList<>();Supply bestExternal;}
}