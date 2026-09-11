package com.ccop.usbcpowermonitor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
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

    private static final int BG = Color.rgb(8, 12, 16);
    private static final int PANEL = Color.rgb(17, 23, 29);
    private static final int PANEL2 = Color.rgb(12, 18, 24);
    private static final int LINE = Color.rgb(43, 56, 68);
    private static final int TEXT = Color.rgb(247, 249, 252);
    private static final int MUTED = Color.rgb(151, 164, 178);
    private static final int OK = Color.rgb(72, 229, 139);
    private static final int WARN = Color.rgb(255, 210, 105);
    private static final int BLUE = Color.rgb(117, 164, 255);
    private static final int RED = Color.rgb(255, 114, 114);

    private enum SourceMode { AUTO, BATTERY_API, SYSTEM }
    private enum ViewMode { LIVE, PROFILE }

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, TextView> values = new HashMap<>();
    private final List<Button> sourceButtons = new ArrayList<>();
    private final List<Button> viewButtons = new ArrayList<>();

    private BatteryManager batteryManager;
    private SharedPreferences prefs;
    private SourceMode sourceMode = SourceMode.AUTO;
    private ViewMode viewMode = ViewMode.LIVE;

    private TextView heroStatus;
    private TextView heroSub;
    private TextView sourceQuality;
    private TextView flowText;
    private TextView diagText;
    private FlowChartView flowChart;

    private long lastTickMs = 0L;
    private double sessionEnergyWh = 0.0;
    private double todayEnergyWh = 0.0;
    private String currentDayKey = "";
    private String latestDiagnostics = "Noch keine Diagnose.";

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshTelemetry();
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        prefs = getSharedPreferences("live_energy", MODE_PRIVATE);
        loadDayEnergy();
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        lastTickMs = SystemClock.elapsedRealtime();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        saveDayEnergy();
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(12), dp(14), dp(28));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final int baseLeft = dp(14), baseTop = dp(12), baseRight = dp(14), baseBottom = dp(28);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            v.setPadding(baseLeft, baseTop + top, baseRight, baseBottom + bottom);
            return insets;
        });

        TextView title = text("CCOP USB-C LadeMonitor · LIVE", 23, TEXT, true);
        root.addView(title);
        TextView subtitle = text("Keine Demo-Fallbacks · Werte nur aus Android-API oder lesbaren Systemknoten", 12, MUTED, false);
        subtitle.setPadding(0, dp(3), 0, dp(12));
        root.addView(subtitle);

        LinearLayout hero = card();
        hero.addView(sectionTitle("LIVE-STATUS"));
        heroStatus = text("PRÜFE…", 35, TEXT, true);
        heroStatus.setPadding(0, dp(8), 0, dp(2));
        hero.addView(heroStatus);
        heroSub = text("Messquelle wird geprüft", 13, MUTED, false);
        heroSub.setPadding(0, 0, 0, dp(10));
        hero.addView(heroSub);
        addMetric(hero, "plug", "Anschluss / Quelle");
        addMetric(hero, "charging", "Aktiver Ladezyklus");
        addMetric(hero, "apiSource", "Aktuelle Datenquelle");
        root.addView(hero, marginBottom());

        LinearLayout selector = card();
        selector.addView(sectionTitle("1 · MESSQUELLE AUSWÄHLEN"));
        selector.addView(text("AUTO bevorzugt echte USB/System-Livewerte. Wenn Android diese sperrt, wird nichts erfunden.", 12, MUTED, false));
        LinearLayout sourceRow = horizontalButtons();
        addSourceButton(sourceRow, "AUTO LIVE", SourceMode.AUTO);
        addSourceButton(sourceRow, "AKKU-API", SourceMode.BATTERY_API);
        addSourceButton(sourceRow, "USB/PD SYSTEM", SourceMode.SYSTEM);
        selector.addView(wrapHorizontal(sourceRow), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView stage2 = sectionTitle("2 · ANZEIGEART AUSWÄHLEN");
        stage2.setPadding(0, dp(14), 0, dp(2));
        selector.addView(stage2);
        selector.addView(text("LIVE zeigt nur Messwerte. PROFIL/MAX zeigt gemeldete Obergrenzen getrennt davon.", 12, MUTED, false));
        LinearLayout viewRow = horizontalButtons();
        addViewButton(viewRow, "LIVE-MESSUNG", ViewMode.LIVE);
        addViewButton(viewRow, "PROFIL / MAX", ViewMode.PROFILE);
        selector.addView(wrapHorizontal(viewRow), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(selector, marginBottom());
        refreshButtonStyles();

        LinearLayout input = card();
        input.addView(sectionTitle("AUSGEWÄHLTE ENERGIEQUELLE"));
        sourceQuality = text("Quelle wird ausgewertet…", 12, MUTED, true);
        sourceQuality.setPadding(0, dp(4), 0, dp(8));
        input.addView(sourceQuality);
        addMetric(input, "inputVoltage", "Spannung");
        addMetric(input, "inputCurrentA", "Strom · Ampere");
        addMetric(input, "inputCurrentMa", "Strom · Milliampere");
        addMetric(input, "inputPower", "Leistung · Watt");
        addMetric(input, "usbType", "USB / PD / Profil");
        root.addView(input, marginBottom());

        LinearLayout flow = card();
        flow.addView(sectionTitle("STROMFLUSS · 1 SEKUNDE"));
        flowText = text("Warte auf ersten Live-Wert…", 19, TEXT, true);
        flowText.setPadding(0, dp(5), 0, dp(8));
        flow.addView(flowText);
        flowChart = new FlowChartView(this);
        flow.addView(flowChart, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(132)));
        TextView flowHint = text("Kurve = Akkuleistung aus echter Akkuspannung × gemessenem Akku-Strom. Kein Ersatzwert aus 5 V / 2 A.", 11, MUTED, false);
        flowHint.setPadding(0, dp(7), 0, 0);
        flow.addView(flowHint);
        root.addView(flow, marginBottom());

        LinearLayout battery = card();
        battery.addView(sectionTitle("SMARTPHONE-AKKU · DIREKT"));
        addMetric(battery, "batteryPct", "Ladezustand");
        addMetric(battery, "batteryVoltage", "Akkuspannung");
        addMetric(battery, "batteryCurrentA", "Momentaner Akkustrom · A");
        addMetric(battery, "batteryCurrentMa", "Momentaner Akkustrom · mA");
        addMetric(battery, "batteryAvg", "Mittlerer Akkustrom");
        addMetric(battery, "batteryPower", "Momentane Akkuleistung");
        addMetric(battery, "chargeCounter", "Charge Counter");
        addMetric(battery, "temperature", "Temperatur");
        root.addView(battery, marginBottom());

        LinearLayout profile = card();
        profile.addView(sectionTitle("GERÄTEMELDUNG · PROFIL / MAX"));
        TextView profileWarn = text("Diese Werte sind Fähigkeiten/Obergrenzen des Ladepfads und ausdrücklich KEINE Live-Messung.", 12, WARN, true);
        profileWarn.setPadding(0, dp(3), 0, dp(8));
        profile.addView(profileWarn);
        addMetric(profile, "maxVoltage", "Gemeldete max. Spannung");
        addMetric(profile, "maxCurrent", "Gemeldeter max. Strom");
        addMetric(profile, "maxPower", "Rechnerische max. Leistung");
        addMetric(profile, "pdProfile", "PD / USB-Typ");
        root.addView(profile, marginBottom());

        LinearLayout totals = card();
        totals.addView(sectionTitle("ZEIT- UND ENERGIEWERTE"));
        addMetric(totals, "sessionEnergy", "Diese App-Sitzung");
        addMetric(totals, "todayEnergy", "Heute · erfasste App-Messzeit");
        addMetric(totals, "yearEnergy", "Jahreswert · 365 × Tageswert");
        TextView totalsHint = text("Tages-/Jahreswerte zählen nur Energie, solange die App tatsächlich misst. Der Jahreswert ist eine Hochrechnung, kein Netzbetreiber-Messwert.", 11, MUTED, false);
        totalsHint.setPadding(0, dp(8), 0, 0);
        totals.addView(totalsHint);
        root.addView(totals, marginBottom());

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("JETZT NEU PRÜFEN");
        refresh.setOnClickListener(v -> refreshTelemetry());
        actionRow.addView(refresh, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button share = button("DIAGNOSE TEILEN");
        share.setOnClickListener(v -> shareDiagnostics());
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        shareLp.setMargins(dp(8), 0, 0, 0);
        actionRow.addView(share, shareLp);
        root.addView(actionRow, marginBottom());

        LinearLayout diag = card();
        diag.addView(sectionTitle("RAW-DIAGNOSE · API + /sys/class/power_supply"));
        TextView diagHint = text("Hier stehen die tatsächlich gelieferten Rohdaten. Fehlt ein USB-Wert hier, gibt Android ihn dieser App nicht frei.", 11, MUTED, false);
        diagHint.setPadding(0, dp(3), 0, dp(8));
        diag.addView(diagHint);
        diagText = text("Prüfe…", 10, TEXT, false);
        diagText.setTypeface(Typeface.MONOSPACE);
        diagText.setTextIsSelectable(true);
        diag.addView(diagText);
        root.addView(diag, marginBottom());

        TextView footer = text("Local First · keine Cloud · keine Demo-Werte · Android API + OEM-Systemknoten", 10, MUTED, false);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);
        return scroll;
    }

    private void refreshTelemetry() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;

        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int battMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        int tempTenthC = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);

        int maxCurrentUa = battery.getIntExtra("max_charging_current", -1);
        int maxVoltageUv = battery.getIntExtra("max_charging_voltage", -1);

        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        double pct = (level >= 0 && scale > 0) ? (100.0 * level / scale) : Double.NaN;
        double batteryV = battMv > 0 ? battMv / 1000.0 : Double.NaN;

        long currentNowUa = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long currentAvgUa = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        long chargeCounterUah = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);

        boolean hasBatteryCurrent = validBatteryProperty(currentNowUa);
        double batteryA = hasBatteryCurrent ? currentNowUa / 1_000_000.0 : Double.NaN;
        double batteryMa = hasBatteryCurrent ? currentNowUa / 1000.0 : Double.NaN;
        double batteryPowerW = (!Double.isNaN(batteryV) && hasBatteryCurrent) ? Math.abs(batteryV * batteryA) : Double.NaN;

        PowerSnapshot sys = readPowerSupplies();
        Supply ext = sys.bestExternal;
        Double sysV = ext == null ? null : voltageFromAny(ext, "voltage_now", "voltage_avg", "voltage_ocv");
        Double sysA = ext == null ? null : currentFromAny(ext, "current_now", "current_avg");
        Double sysW = ext == null ? null : powerFromAny(ext, "power_now", "power_avg");
        if (sysW == null && sysV != null && sysA != null) sysW = Math.abs(sysV * sysA);

        String plugName = pluggedName(plugged);
        if (charging) {
            heroStatus.setText("LADEN AKTIV");
            heroStatus.setTextColor(OK);
        } else if (plugged != 0) {
            heroStatus.setText("QUELLE VERBUNDEN");
            heroStatus.setTextColor(WARN);
        } else {
            heroStatus.setText("NICHT VERBUNDEN");
            heroStatus.setTextColor(TEXT);
        }

        set("plug", plugged != 0 ? plugName : "keine externe Quelle", plugged != 0 ? OK : MUTED);
        set("charging", charging ? "Ja" : "Nein", charging ? OK : MUTED);

        String usbType = ext != null ? firstNonEmpty(ext.get("usb_type"), ext.get("real_type"), ext.get("type")) : null;
        String pdProfile = firstNonEmpty(
                ext != null ? ext.get("usb_type") : null,
                ext != null ? ext.get("real_type") : null,
                battery.getStringExtra("charger_type")
        );

        if (viewMode == ViewMode.PROFILE) {
            renderProfileSelection(maxVoltageUv, maxCurrentUa, pdProfile);
        } else {
            renderLiveSelection(sysV, sysA, sysW, batteryV, batteryA, batteryMa, batteryPowerW, ext, usbType);
        }

        set("batteryPct", Double.isNaN(pct) ? "nicht gemeldet" : fmt(pct, 0) + " %", TEXT);
        set("batteryVoltage", Double.isNaN(batteryV) ? "nicht gemeldet" : fmt(batteryV, 3) + " V", Double.isNaN(batteryV) ? MUTED : OK);
        set("batteryCurrentA", hasBatteryCurrent ? signedFmt(batteryA, 3) + " A" : "nicht gemeldet", hasBatteryCurrent ? OK : MUTED);
        set("batteryCurrentMa", hasBatteryCurrent ? signedFmt(batteryMa, 0) + " mA" : "nicht gemeldet", hasBatteryCurrent ? OK : MUTED);
        set("batteryAvg", validBatteryProperty(currentAvgUa) ? signedFmt(currentAvgUa / 1000.0, 0) + " mA" : "nicht gemeldet", validBatteryProperty(currentAvgUa) ? TEXT : MUTED);
        set("batteryPower", Double.isNaN(batteryPowerW) ? "nicht berechenbar" : fmt(batteryPowerW, 2) + " W", Double.isNaN(batteryPowerW) ? MUTED : OK);
        set("chargeCounter", validBatteryProperty(chargeCounterUah) ? fmt(chargeCounterUah / 1000.0, 0) + " mAh" : "nicht gemeldet", validBatteryProperty(chargeCounterUah) ? TEXT : MUTED);
        set("temperature", tempTenthC != Integer.MIN_VALUE ? fmt(tempTenthC / 10.0, 1) + " °C" : "nicht gemeldet", TEXT);

        set("maxVoltage", maxVoltageUv > 0 ? fmt(normalizeVoltageRaw(maxVoltageUv), 2) + " V · MAX" : "nicht gemeldet", maxVoltageUv > 0 ? BLUE : MUTED);
        set("maxCurrent", maxCurrentUa > 0 ? fmt(normalizeCurrentRaw(maxCurrentUa), 2) + " A · MAX" : "nicht gemeldet", maxCurrentUa > 0 ? BLUE : MUTED);
        if (maxVoltageUv > 0 && maxCurrentUa > 0) {
            double maxW = normalizeVoltageRaw(maxVoltageUv) * Math.abs(normalizeCurrentRaw(maxCurrentUa));
            set("maxPower", fmt(maxW, 2) + " W · RECHNERISCH", BLUE);
        } else {
            set("maxPower", "nicht berechenbar", MUTED);
        }
        set("pdProfile", pdProfile != null ? pdProfile : "nicht gemeldet", pdProfile != null ? BLUE : MUTED);

        updateFlow(charging, batteryMa, batteryPowerW);
        updateEnergy(charging, batteryPowerW);
        updateTotals();

        String activeSource;
        if (viewMode == ViewMode.PROFILE) {
            activeSource = "Android Ladeprofil / MAX";
        } else if (sourceMode == SourceMode.BATTERY_API) {
            activeSource = "BatteryManager · Akku-Seite";
        } else if (sourceMode == SourceMode.SYSTEM) {
            activeSource = ext != null ? "/sys/class/power_supply/" + ext.name : "Systemknoten nicht lesbar";
        } else {
            activeSource = (ext != null && (sysV != null || sysA != null)) ? "/sys/class/power_supply/" + ext.name : "BatteryManager · Akku-Seite";
        }
        set("apiSource", activeSource, activeSource.contains("nicht") ? WARN : BLUE);
        heroSub.setText(viewMode == ViewMode.PROFILE
                ? "Profilansicht aktiv · keine Livewerte als MAX-Werte tarnen"
                : "Liveansicht aktiv · Aktualisierung jede Sekunde");

        latestDiagnostics = buildDiagnostics(battery, sys, currentNowUa, currentAvgUa, chargeCounterUah, maxVoltageUv, maxCurrentUa);
        diagText.setText(latestDiagnostics);
    }

    private void renderLiveSelection(Double sysV, Double sysA, Double sysW,
                                     double batteryV, double batteryA, double batteryMa, double batteryPowerW,
                                     Supply ext, String usbType) {
        boolean systemLive = ext != null && (sysV != null || sysA != null || sysW != null);

        if (sourceMode == SourceMode.SYSTEM || (sourceMode == SourceMode.AUTO && systemLive)) {
            sourceQuality.setText(systemLive
                    ? "ECHTE SYSTEM-LIVEWERTE · " + ext.name
                    : "USB/PD-Systemwerte sind auf diesem Gerät für normale Apps nicht lesbar.");
            sourceQuality.setTextColor(systemLive ? OK : WARN);
            set("inputVoltage", sysV != null ? fmt(sysV, 3) + " V · LIVE" : "nicht freigegeben", sysV != null ? OK : MUTED);
            set("inputCurrentA", sysA != null ? signedFmt(sysA, 3) + " A · LIVE" : "nicht freigegeben", sysA != null ? OK : MUTED);
            set("inputCurrentMa", sysA != null ? signedFmt(sysA * 1000.0, 0) + " mA · LIVE" : "nicht freigegeben", sysA != null ? OK : MUTED);
            set("inputPower", sysW != null ? fmt(Math.abs(sysW), 2) + " W · LIVE" : "nicht freigegeben", sysW != null ? OK : MUTED);
            set("usbType", usbType != null ? usbType : "nicht gemeldet", usbType != null ? BLUE : MUTED);
            return;
        }

        boolean battLive = !Double.isNaN(batteryV) || !Double.isNaN(batteryA);
        sourceQuality.setText(battLive
                ? (sourceMode == SourceMode.AUTO
                    ? "AUTO: USB-Eingang gesperrt → echte Akku-Seiten-Livewerte werden gezeigt."
                    : "ECHTE BATTERYMANAGER-LIVEWERTE · Akku-Seite")
                : "Keine Live-Telemetrie verfügbar.");
        sourceQuality.setTextColor(battLive ? OK : WARN);
        set("inputVoltage", !Double.isNaN(batteryV) ? fmt(batteryV, 3) + " V · AKKU LIVE" : "nicht gemeldet", !Double.isNaN(batteryV) ? OK : MUTED);
        set("inputCurrentA", !Double.isNaN(batteryA) ? signedFmt(batteryA, 3) + " A · AKKU LIVE" : "nicht gemeldet", !Double.isNaN(batteryA) ? OK : MUTED);
        set("inputCurrentMa", !Double.isNaN(batteryMa) ? signedFmt(batteryMa, 0) + " mA · AKKU LIVE" : "nicht gemeldet", !Double.isNaN(batteryMa) ? OK : MUTED);
        set("inputPower", !Double.isNaN(batteryPowerW) ? fmt(batteryPowerW, 2) + " W · AKKU LIVE" : "nicht berechenbar", !Double.isNaN(batteryPowerW) ? OK : MUTED);
        set("usbType", "Akku-Seite · kein USB-Profil", MUTED);
    }

    private void renderProfileSelection(int maxVoltageUv, int maxCurrentUa, String pdProfile) {
        sourceQuality.setText("PROFIL / MAX · bewusst getrennt von Live-Messung");
        sourceQuality.setTextColor(WARN);
        Double v = maxVoltageUv > 0 ? normalizeVoltageRaw(maxVoltageUv) : null;
        Double a = maxCurrentUa > 0 ? normalizeCurrentRaw(maxCurrentUa) : null;
        Double w = (v != null && a != null) ? Math.abs(v * a) : null;
        set("inputVoltage", v != null ? fmt(v, 2) + " V · MAX" : "nicht gemeldet", v != null ? BLUE : MUTED);
        set("inputCurrentA", a != null ? fmt(a, 2) + " A · MAX" : "nicht gemeldet", a != null ? BLUE : MUTED);
        set("inputCurrentMa", a != null ? fmt(a * 1000.0, 0) + " mA · MAX" : "nicht gemeldet", a != null ? BLUE : MUTED);
        set("inputPower", w != null ? fmt(w, 2) + " W · MAX, RECHNERISCH" : "nicht berechenbar", w != null ? BLUE : MUTED);
        set("usbType", pdProfile != null ? pdProfile : "nicht gemeldet", pdProfile != null ? BLUE : MUTED);
    }

    private void updateFlow(boolean charging, double batteryMa, double batteryPowerW) {
        if (!Double.isNaN(batteryPowerW)) flowChart.addPoint((float) batteryPowerW);
        if (!Double.isNaN(batteryMa) && !Double.isNaN(batteryPowerW)) {
            String arrow = charging ? "→ AKKU" : "← AKKU";
            flowText.setText(arrow + "   " + signedFmt(batteryMa, 0) + " mA   ·   " + fmt(batteryPowerW, 2) + " W");
            flowText.setTextColor(charging ? OK : WARN);
        } else {
            flowText.setText("Kein Live-Stromwert verfügbar");
            flowText.setTextColor(MUTED);
        }
    }

    private void updateEnergy(boolean charging, double batteryPowerW) {
        long now = SystemClock.elapsedRealtime();
        if (lastTickMs == 0L) lastTickMs = now;
        double hours = Math.max(0.0, Math.min(5.0, (now - lastTickMs) / 1000.0)) / 3600.0;
        lastTickMs = now;
        rotateDayIfNeeded();
        if (charging && !Double.isNaN(batteryPowerW) && batteryPowerW >= 0.0) {
            double delta = batteryPowerW * hours;
            sessionEnergyWh += delta;
            todayEnergyWh += delta;
        }
        if (((int) now) % 10000 < 1100) saveDayEnergy();
    }

    private void updateTotals() {
        set("sessionEnergy", formatEnergy(sessionEnergyWh), TEXT);
        set("todayEnergy", formatEnergy(todayEnergyWh), TEXT);
        double yearKwh = todayEnergyWh * 365.0 / 1000.0;
        set("yearEnergy", fmt(yearKwh, 3) + " kWh/Jahr · HOCHRECHNUNG", yearKwh > 0 ? BLUE : MUTED);
    }

    private String formatEnergy(double wh) {
        if (wh < 1.0) return fmt(wh * 1000.0, 1) + " mWh";
        return fmt(wh, 3) + " Wh";
    }

    private void loadDayEnergy() {
        currentDayKey = dayKey();
        String savedDay = prefs.getString("day", "");
        if (currentDayKey.equals(savedDay)) todayEnergyWh = Double.longBitsToDouble(prefs.getLong("energy", Double.doubleToLongBits(0.0)));
        else todayEnergyWh = 0.0;
    }

    private void rotateDayIfNeeded() {
        String k = dayKey();
        if (!k.equals(currentDayKey)) {
            currentDayKey = k;
            todayEnergyWh = 0.0;
            saveDayEnergy();
        }
    }

    private void saveDayEnergy() {
        prefs.edit()
                .putString("day", currentDayKey)
                .putLong("energy", Double.doubleToLongBits(todayEnergyWh))
                .apply();
    }

    private String dayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(new Date());
    }

    private long safeIntProperty(int id) {
        try {
            return batteryManager.getIntProperty(id);
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }

    private boolean validBatteryProperty(long v) {
        return v != Long.MIN_VALUE && v != Integer.MIN_VALUE;
    }

    private PowerSnapshot readPowerSupplies() {
        PowerSnapshot snap = new PowerSnapshot();
        File root = new File("/sys/class/power_supply");
        Map<String, Supply> unique = new LinkedHashMap<>();

        try {
            File[] dirs = root.listFiles();
            if (dirs != null) {
                for (File dir : dirs) {
                    if (dir.isDirectory()) {
                        Supply s = readSupply(dir);
                        if (s != null) unique.put(s.name, s);
                    }
                }
            }
        } catch (Throwable ignored) { }

        String[] candidates = new String[]{
                "usb", "USB", "ac", "AC", "charger", "main", "usbpd", "usb_pd", "dc", "wireless",
                "battery", "bms", "pc_port", "qcom-battery", "mtk-master-charger"
        };
        for (String name : candidates) {
            if (unique.containsKey(name)) continue;
            File dir = new File(root, name);
            Supply s = readSupply(dir);
            if (s != null) unique.put(s.name, s);
        }

        snap.supplies.addAll(unique.values());
        for (Supply s : snap.supplies) {
            if (isExternalSupply(s)) {
                if (snap.bestExternal == null) snap.bestExternal = s;
                String online = s.get("online");
                if ("1".equals(online)) {
                    snap.bestExternal = s;
                    break;
                }
            }
        }
        return snap;
    }

    private Supply readSupply(File dir) {
        try {
            if (!dir.exists() || !dir.isDirectory()) return null;
            Supply s = new Supply(dir.getName());
            String[] keys = new String[]{
                    "type", "usb_type", "real_type", "online", "present", "status",
                    "voltage_now", "voltage_avg", "voltage_ocv", "voltage_max", "voltage_max_design",
                    "current_now", "current_avg", "current_max", "current_max_design",
                    "power_now", "power_avg", "power_max",
                    "input_voltage_limit", "input_current_limit",
                    "constant_charge_current", "constant_charge_current_max",
                    "pd_active", "pd_state", "pd_voltage", "pd_current",
                    "capacity", "capacity_raw", "manufacturer", "model_name", "serial_number"
            };
            int readCount = 0;
            for (String key : keys) {
                String value = readText(new File(dir, key));
                if (value != null) {
                    s.values.put(key, value);
                    readCount++;
                }
            }
            return readCount > 0 ? s : null;
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isExternalSupply(Supply s) {
        String n = s.name.toLowerCase(Locale.ROOT);
        String type = s.get("type");
        if (n.contains("battery") || "battery".equals(n) || "Battery".equalsIgnoreCase(type)) return false;
        return true;
    }

    private String readText(File file) {
        try {
            if (!file.exists() || !file.isFile()) return null;
            BufferedReader br = new BufferedReader(new FileReader(file));
            String line = br.readLine();
            br.close();
            return line == null ? null : line.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    private Double voltageFromAny(Supply s, String... keys) {
        for (String key : keys) {
            Double raw = parseDouble(s.get(key));
            if (raw != null && raw != 0.0) return normalizeVoltageRaw(raw);
        }
        return null;
    }

    private Double currentFromAny(Supply s, String... keys) {
        for (String key : keys) {
            Double raw = parseDouble(s.get(key));
            if (raw != null && raw != 0.0) return normalizeCurrentRaw(raw);
        }
        return null;
    }

    private Double powerFromAny(Supply s, String... keys) {
        for (String key : keys) {
            Double raw = parseDouble(s.get(key));
            if (raw != null && raw != 0.0) {
                double a = Math.abs(raw);
                if (a > 100000.0) return raw / 1_000_000.0;
                if (a > 1000.0) return raw / 1000.0;
                return raw;
            }
        }
        return null;
    }

    private double normalizeVoltageRaw(double raw) {
        double a = Math.abs(raw);
        if (a > 100000.0) return raw / 1_000_000.0;
        if (a > 100.0) return raw / 1000.0;
        return raw;
    }

    private double normalizeCurrentRaw(double raw) {
        double a = Math.abs(raw);
        if (a > 100000.0) return raw / 1_000_000.0;
        if (a > 100.0) return raw / 1000.0;
        return raw;
    }

    private Double parseDouble(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (Throwable t) { return null; }
    }

    private String buildDiagnostics(Intent battery, PowerSnapshot sys, long currentNowUa, long currentAvgUa,
                                    long chargeCounterUah, int maxVoltageUv, int maxCurrentUa) {
        StringBuilder sb = new StringBuilder();
        sb.append("CCOP USB-C LadeMonitor · RAW\n");
        sb.append("Zeit: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.GERMANY).format(new Date())).append('\n');
        sb.append("sourceMode=").append(sourceMode).append("  viewMode=").append(viewMode).append("\n\n");
        sb.append("BatteryManager\n");
        sb.append("current_now_uA=").append(currentNowUa).append('\n');
        sb.append("current_avg_uA=").append(currentAvgUa).append('\n');
        sb.append("charge_counter_uAh=").append(chargeCounterUah).append('\n');
        sb.append("max_charging_voltage_raw=").append(maxVoltageUv).append('\n');
        sb.append("max_charging_current_raw=").append(maxCurrentUa).append('\n');

        Bundle extras = battery.getExtras();
        if (extras != null) {
            sb.append("\nBattery broadcast extras\n");
            for (String key : extras.keySet()) {
                String low = key.toLowerCase(Locale.ROOT);
                if (low.contains("volt") || low.contains("curr") || low.contains("charg") || low.contains("plug") || low.contains("power") || low.contains("usb")) {
                    Object val = extras.get(key);
                    sb.append(key).append('=').append(String.valueOf(val)).append('\n');
                }
            }
        }

        sb.append("\n/sys/class/power_supply\n");
        if (sys.supplies.isEmpty()) {
            sb.append("keine lesbaren Knoten\n");
        } else {
            for (Supply s : sys.supplies) {
                sb.append('[').append(s.name).append("]\n");
                for (Map.Entry<String, String> e : s.values.entrySet()) {
                    sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
                }
            }
        }
        return sb.toString();
    }

    private void shareDiagnostics() {
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT, "CCOP USB-C LadeMonitor Diagnose");
        send.putExtra(Intent.EXTRA_TEXT, latestDiagnostics);
        startActivity(Intent.createChooser(send, "Diagnose teilen"));
    }

    private void addSourceButton(LinearLayout row, String label, SourceMode mode) {
        Button b = choiceButton(label);
        b.setTag(mode);
        b.setOnClickListener(v -> {
            sourceMode = mode;
            viewMode = ViewMode.LIVE;
            refreshButtonStyles();
            refreshTelemetry();
        });
        sourceButtons.add(b);
        row.addView(b, choiceLp());
    }

    private void addViewButton(LinearLayout row, String label, ViewMode mode) {
        Button b = choiceButton(label);
        b.setTag(mode);
        b.setOnClickListener(v -> {
            viewMode = mode;
            refreshButtonStyles();
            refreshTelemetry();
        });
        viewButtons.add(b);
        row.addView(b, choiceLp());
    }

    private void refreshButtonStyles() {
        for (Button b : sourceButtons) {
            boolean active = b.getTag() == sourceMode;
            styleChoice(b, active);
        }
        for (Button b : viewButtons) {
            boolean active = b.getTag() == viewMode;
            styleChoice(b, active);
        }
    }

    private void styleChoice(Button b, boolean active) {
        GradientDrawable gd = new GradientDrawable();
        gd.setCornerRadius(dp(14));
        gd.setColor(active ? Color.rgb(25, 87, 58) : PANEL2);
        gd.setStroke(dp(1), active ? OK : LINE);
        b.setBackground(gd);
        b.setTextColor(active ? OK : TEXT);
    }

    private LinearLayout horizontalButtons() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(8), 0, 0);
        return row;
    }

    private HorizontalScrollView wrapHorizontal(LinearLayout row) {
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        hsv.addView(row);
        return hsv;
    }

    private LinearLayout.LayoutParams choiceLp() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(46));
        lp.setMargins(0, 0, dp(8), 0);
        return lp;
    }

    private Button choiceButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(12);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setAllCaps(false);
        b.setPadding(dp(14), 0, dp(14), 0);
        return b;
    }

    private void addMetric(LinearLayout parent, String key, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView l = text(label, 13, MUTED, false);
        TextView v = text("—", 14, TEXT, true);
        v.setGravity(Gravity.END);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(v, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f));
        values.put(key, v);
        parent.addView(row);
    }

    private void set(String key, String value, int color) {
        TextView tv = values.get(key);
        if (tv != null) {
            tv.setText(value);
            tv.setTextColor(color);
        }
    }

    private LinearLayout card() {
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(dp(16), dp(16), dp(16), dp(16));
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(PANEL);
        gd.setCornerRadius(dp(24));
        gd.setStroke(dp(1), LINE);
        ll.setBackground(gd);
        return ll;
    }

    private LinearLayout.LayoutParams marginBottom() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 12, MUTED, true);
        t.setLetterSpacing(0.11f);
        return t;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(s);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setLineSpacing(0, 1.08f);
        return tv;
    }

    private Button button(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.rgb(24, 32, 40));
        gd.setCornerRadius(dp(15));
        gd.setStroke(dp(1), LINE);
        b.setBackground(gd);
        return b;
    }

    private int dp(int n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    private String pluggedName(int plugged) {
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "Netzteil / AC";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "Wireless";
        return plugged == 0 ? "keine" : "extern";
    }

    private String fmt(double v, int decimals) {
        return String.format(Locale.GERMANY, "%." + decimals + "f", v);
    }

    private String signedFmt(double v, int decimals) {
        return String.format(Locale.GERMANY, "%+." + decimals + "f", v);
    }

    private String firstNonEmpty(String... values) {
        if (values == null) return null;
        for (String s : values) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private static class Supply {
        final String name;
        final Map<String, String> values = new LinkedHashMap<>();
        Supply(String name) { this.name = name; }
        String get(String key) { return values.get(key); }
    }

    private static class PowerSnapshot {
        final List<Supply> supplies = new ArrayList<>();
        Supply bestExternal;
    }

    private static class FlowChartView extends View {
        private final List<Float> points = new ArrayList<>();
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);

        FlowChartView(Context c) {
            super(c);
            grid.setColor(Color.rgb(43, 56, 68));
            grid.setStrokeWidth(1f);
            line.setColor(Color.rgb(72, 229, 139));
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(4f);
            line.setStrokeJoin(Paint.Join.ROUND);
            line.setStrokeCap(Paint.Cap.ROUND);
            fill.setColor(Color.argb(36, 72, 229, 139));
            fill.setStyle(Paint.Style.FILL);
            label.setColor(Color.rgb(151, 164, 178));
            label.setTextSize(28f);
        }

        void addPoint(float p) {
            if (Float.isNaN(p) || Float.isInfinite(p)) return;
            points.add(Math.max(0f, p));
            while (points.size() > 120) points.remove(0);
            invalidate();
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            int w = getWidth();
            int h = getHeight();
            c.drawLine(0, h * 0.25f, w, h * 0.25f, grid);
            c.drawLine(0, h * 0.50f, w, h * 0.50f, grid);
            c.drawLine(0, h * 0.75f, w, h * 0.75f, grid);
            if (points.size() < 2) {
                c.drawText("Live-Kurve startet nach 2 Messpunkten", 12, h / 2f, label);
                return;
            }
            float max = 0.1f;
            for (float p : points) max = Math.max(max, p);
            Path path = new Path();
            Path area = new Path();
            float dx = w / (float) Math.max(1, points.size() - 1);
            for (int i = 0; i < points.size(); i++) {
                float x = i * dx;
                float y = h - (points.get(i) / max) * (h - 16f) - 8f;
                if (i == 0) {
                    path.moveTo(x, y);
                    area.moveTo(x, h);
                    area.lineTo(x, y);
                } else {
                    path.lineTo(x, y);
                    area.lineTo(x, y);
                }
            }
            area.lineTo(w, h);
            area.close();
            c.drawPath(area, fill);
            c.drawPath(path, line);
            c.drawText(String.format(Locale.GERMANY, "max %.2f W", max), 12, 30, label);
        }
    }
}
