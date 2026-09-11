package com.ccop.usbcpowermonitor;

import android.app.Activity;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MainActivity extends Activity {

    private static final int BG = Color.rgb(9, 13, 17);
    private static final int PANEL = Color.rgb(17, 23, 29);
    private static final int PANEL2 = Color.rgb(13, 19, 25);
    private static final int LINE = Color.rgb(41, 53, 64);
    private static final int TEXT = Color.rgb(246, 248, 251);
    private static final int MUTED = Color.rgb(152, 165, 179);
    private static final int OK = Color.rgb(70, 229, 138);
    private static final int WARN = Color.rgb(255, 209, 102);
    private static final int BLUE = Color.rgb(125, 170, 255);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BatteryManager batteryManager;
    private final Map<String, TextView> values = new HashMap<>();
    private TextView statusText;
    private TextView diagText;
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
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        setContentView(buildUi());
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(14), dp(14), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView title = text("CCOP USB‑C LadeMonitor", 22, TEXT, true);
        root.addView(title);
        TextView subtitle = text("Native Android-Telemetrie · USB-C / Ladegerät / Powerbank", 13, MUTED, false);
        subtitle.setPadding(0, dp(2), 0, dp(14));
        root.addView(subtitle);

        LinearLayout sourceCard = card();
        sourceCard.addView(sectionTitle("EXTERNE ENERGIEQUELLE"));
        statusText = text("PRÜFE…", 34, TEXT, true);
        statusText.setPadding(0, dp(8), 0, dp(8));
        sourceCard.addView(statusText);
        addMetric(sourceCard, "plug", "Anschluss / Quelle");
        addMetric(sourceCard, "charging", "Ladezustand");
        addMetric(sourceCard, "supply", "Power-Supply-Knoten");
        addMetric(sourceCard, "usbType", "USB / PD Typ");
        root.addView(sourceCard, marginBottom());

        LinearLayout inputCard = card();
        inputCard.addView(sectionTitle("USB-C / LADEGERÄT"));
        addMetric(inputCard, "inputVoltage", "Eingangsspannung");
        addMetric(inputCard, "inputCurrent", "Eingangsstrom");
        addMetric(inputCard, "inputPower", "Eingangsleistung");
        addMetric(inputCard, "maxVoltage", "Max. Ladespannung Android");
        addMetric(inputCard, "maxCurrent", "Max. Ladestrom Android");
        addMetric(inputCard, "pdActive", "PD aktiv / Profil");
        root.addView(inputCard, marginBottom());

        LinearLayout phoneCard = card();
        phoneCard.addView(sectionTitle("SMARTPHONE-AKKU"));
        addMetric(phoneCard, "batteryPct", "Ladezustand");
        addMetric(phoneCard, "batteryVoltage", "Akkuspannung");
        addMetric(phoneCard, "batteryCurrent", "Momentaner Akkustrom");
        addMetric(phoneCard, "batteryCurrentAvg", "Mittlerer Akkustrom");
        addMetric(phoneCard, "batteryPower", "Akkuleistung");
        addMetric(phoneCard, "chargeCounter", "Charge Counter");
        addMetric(phoneCard, "energyCounter", "Energy Counter");
        addMetric(phoneCard, "temperature", "Temperatur");
        root.addView(phoneCard, marginBottom());

        LinearLayout pbCard = card();
        pbCard.addView(sectionTitle("EXTERNE POWERBANK"));
        addMetric(pbCard, "pbSoc", "Powerbank-Ladezustand");
        addMetric(pbCard, "pbVoltage", "Powerbank-/Quellspannung");
        addMetric(pbCard, "pbCurrent", "Quellstrom");
        addMetric(pbCard, "pbModel", "Modell / Hersteller");
        TextView pbNote = text("Der Powerbank-SOC wird nur angezeigt, wenn die angeschlossene Quelle ihn tatsächlich als Datenwert bereitstellt. Die APK erfindet keinen Wert.", 12, MUTED, false);
        pbNote.setPadding(0, dp(10), 0, 0);
        pbCard.addView(pbNote);
        root.addView(pbCard, marginBottom());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        Button refresh = button("JETZT PRÜFEN");
        refresh.setOnClickListener(v -> refreshTelemetry());
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(48), 1f));
        Button share = button("DIAGNOSE TEILEN");
        share.setOnClickListener(v -> shareDiagnostics());
        LinearLayout.LayoutParams shareLp = new LinearLayout.LayoutParams(0, dp(48), 1f);
        shareLp.setMargins(dp(8), 0, 0, 0);
        actions.addView(share, shareLp);
        root.addView(actions, marginBottom());

        LinearLayout diagCard = card();
        diagCard.addView(sectionTitle("ANDROID POWER-SUPPLY DIAGNOSE"));
        TextView info = text("Diese Ansicht listet alle lesbaren /sys/class/power_supply-Knoten und Android-Ladewerte auf. Damit werden Motorola-/OEM-spezifische USB-C- und PD-Felder sichtbar, sofern Android sie für Apps freigibt.", 12, MUTED, false);
        info.setPadding(0, dp(4), 0, dp(10));
        diagCard.addView(info);
        diagText = text("Prüfe…", 11, TEXT, false);
        diagText.setTypeface(Typeface.MONOSPACE);
        diagText.setTextIsSelectable(true);
        diagText.setPadding(0, dp(6), 0, 0);
        diagCard.addView(diagText);
        root.addView(diagCard, marginBottom());

        TextView footer = text("Keine Cloud · kein Bluetooth · keine Browser-API · direkte Android-Auswertung", 11, MUTED, false);
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
        int maxCurrentUa = battery.getIntExtra(BatteryManager.EXTRA_MAX_CHARGING_CURRENT, -1);
        int maxVoltageUv = battery.getIntExtra(BatteryManager.EXTRA_MAX_CHARGING_VOLTAGE, -1);

        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        double pct = (level >= 0 && scale > 0) ? (100.0 * level / scale) : Double.NaN;
        double batteryV = battMv > 0 ? battMv / 1000.0 : Double.NaN;

        long currentNowUa = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long currentAvgUa = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        long chargeCounterUah = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long energyCounterNwh = safeLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);

        PowerSnapshot sys = readPowerSupplies();
        Supply ext = sys.bestExternal;

        String plugName = pluggedName(plugged);
        if (charging) {
            statusText.setText("LADEN AKTIV");
            statusText.setTextColor(OK);
        } else if (plugged != 0) {
            statusText.setText("QUELLE VERBUNDEN");
            statusText.setTextColor(WARN);
        } else {
            statusText.setText("NICHT VERBUNDEN");
            statusText.setTextColor(TEXT);
        }

        set("plug", plugged != 0 ? plugName : "keine externe Quelle", plugged != 0 ? OK : MUTED);
        set("charging", charging ? "Ja" : "Nein", charging ? OK : MUTED);
        set("supply", ext != null ? ext.name : "kein lesbarer externer Knoten", ext != null ? TEXT : MUTED);

        String usbType = ext != null ? firstNonEmpty(ext.get("usb_type"), ext.get("real_type"), ext.get("type")) : null;
        set("usbType", usbType != null ? usbType : plugName, usbType != null ? BLUE : MUTED);

        Double inputV = null;
        Double inputA = null;
        Double inputW = null;

        if (ext != null) {
            inputV = voltageFromAny(ext, "voltage_now", "voltage_avg", "voltage_ocv", "voltage_max", "input_voltage_limit", "pd_voltage");
            inputA = currentFromAny(ext, "current_now", "current_avg", "current_max", "input_current_limit", "pd_current", "constant_charge_current_max");
            inputW = powerFromAny(ext, "power_now", "power_avg", "power_max");
            if (inputW == null && inputV != null && inputA != null) inputW = Math.abs(inputV * inputA);
        }

        set("inputVoltage", inputV != null ? fmt(inputV, 3) + " V · direkt" : "nicht vom Kernel freigegeben", inputV != null ? OK : MUTED);
        set("inputCurrent", inputA != null ? fmt(inputA, 3) + " A · direkt" : "nicht vom Kernel freigegeben", inputA != null ? OK : MUTED);
        set("inputPower", inputW != null ? fmt(inputW, 2) + " W · direkt" : "nicht direkt verfügbar", inputW != null ? OK : MUTED);

        set("maxVoltage", maxVoltageUv > 0 ? fmt(maxVoltageUv / 1_000_000.0, 2) + " V" : "nicht gemeldet", maxVoltageUv > 0 ? BLUE : MUTED);
        set("maxCurrent", maxCurrentUa > 0 ? fmt(maxCurrentUa / 1_000_000.0, 2) + " A" : "nicht gemeldet", maxCurrentUa > 0 ? BLUE : MUTED);

        String pd = ext != null ? firstNonEmpty(ext.get("pd_active"), ext.get("pd_state"), ext.get("usb_type"), ext.get("real_type")) : null;
        set("pdActive", pd != null ? pd : "nicht gemeldet", pd != null ? BLUE : MUTED);

        set("batteryPct", Double.isNaN(pct) ? "—" : fmt(pct, 0) + " %", TEXT);
        set("batteryVoltage", Double.isNaN(batteryV) ? "nicht gemeldet" : fmt(batteryV, 3) + " V", batteryV > 0 ? OK : MUTED);
        set("batteryCurrent", validBatteryProperty(currentNowUa) ? fmt(currentNowUa / 1_000_000.0, 3) + " A" : "nicht gemeldet", validBatteryProperty(currentNowUa) ? OK : MUTED);
        set("batteryCurrentAvg", validBatteryProperty(currentAvgUa) ? fmt(currentAvgUa / 1_000_000.0, 3) + " A" : "nicht gemeldet", validBatteryProperty(currentAvgUa) ? TEXT : MUTED);

        if (!Double.isNaN(batteryV) && validBatteryProperty(currentNowUa)) {
            double bw = batteryV * Math.abs(currentNowUa / 1_000_000.0);
            set("batteryPower", fmt(bw, 2) + " W", OK);
        } else {
            set("batteryPower", "nicht berechenbar", MUTED);
        }

        set("chargeCounter", validBatteryProperty(chargeCounterUah) ? fmt(chargeCounterUah / 1000.0, 0) + " mAh" : "nicht gemeldet", validBatteryProperty(chargeCounterUah) ? TEXT : MUTED);
        set("energyCounter", validEnergyProperty(energyCounterNwh) ? fmt(energyCounterNwh / 1_000_000_000.0, 3) + " Wh" : "nicht gemeldet", validEnergyProperty(energyCounterNwh) ? TEXT : MUTED);
        set("temperature", tempTenthC != Integer.MIN_VALUE ? fmt(tempTenthC / 10.0, 1) + " °C" : "nicht gemeldet", TEXT);

        String pbSoc = ext != null ? firstNonEmpty(ext.get("capacity"), ext.get("capacity_raw")) : null;
        set("pbSoc", pbSoc != null ? normalizePercent(pbSoc) : "nicht übertragen", pbSoc != null ? OK : MUTED);
        set("pbVoltage", inputV != null ? fmt(inputV, 3) + " V" : "nicht übertragen", inputV != null ? TEXT : MUTED);
        set("pbCurrent", inputA != null ? fmt(inputA, 3) + " A" : "nicht übertragen", inputA != null ? TEXT : MUTED);
        String pbModel = ext != null ? joinNonEmpty(" · ", ext.get("manufacturer"), ext.get("model_name"), ext.get("serial_number")) : null;
        set("pbModel", pbModel != null ? pbModel : "nicht übertragen", pbModel != null ? TEXT : MUTED);

        latestDiagnostics = buildDiagnostics(battery, sys, currentNowUa, currentAvgUa, chargeCounterUah, energyCounterNwh);
        diagText.setText(latestDiagnostics);
    }

    private long safeIntProperty(int id) {
        try {
            int v = batteryManager.getIntProperty(id);
            return v;
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }

    private long safeLongProperty(int id) {
        try {
            return batteryManager.getLongProperty(id);
        } catch (Throwable t) {
            return Long.MIN_VALUE;
        }
    }

    private boolean validBatteryProperty(long v) {
        return v != Long.MIN_VALUE && v != Integer.MIN_VALUE;
    }

    private boolean validEnergyProperty(long v) {
        return v != Long.MIN_VALUE && v != Long.MIN_VALUE + 1 && v != Integer.MIN_VALUE;
    }

    private PowerSnapshot readPowerSupplies() {
        PowerSnapshot snapshot = new PowerSnapshot();
        File root = new File("/sys/class/power_supply");
        File[] dirs = root.listFiles();
        if (dirs == null) return snapshot;

        for (File dir : dirs) {
            if (!dir.isDirectory() && !dir.exists()) continue;
            Supply s = new Supply(dir.getName());
            readKnown(s, dir, "type", "online", "present", "status", "capacity", "capacity_raw",
                    "voltage_now", "voltage_avg", "voltage_ocv", "voltage_max", "voltage_max_design",
                    "current_now", "current_avg", "current_max", "input_current_limit", "input_voltage_limit",
                    "constant_charge_current_max", "power_now", "power_avg", "power_max", "usb_type", "real_type",
                    "pd_active", "pd_state", "pd_voltage", "pd_current", "charge_counter", "energy_now",
                    "manufacturer", "model_name", "serial_number", "health", "technology");
            readUevent(s, new File(dir, "uevent"));
            snapshot.supplies.add(s);
        }

        Collections.sort(snapshot.supplies, Comparator.comparing(a -> a.name));
        int bestScore = Integer.MIN_VALUE;
        for (Supply s : snapshot.supplies) {
            int score = scoreExternal(s);
            if (score > bestScore) {
                bestScore = score;
                snapshot.bestExternal = score > 0 ? s : null;
            }
        }
        return snapshot;
    }

    private int scoreExternal(Supply s) {
        String type = lower(s.get("type"));
        String name = lower(s.name);
        if (type.contains("battery") || name.contains("battery")) return -100;
        int score = 0;
        if ("1".equals(trim(s.get("online")))) score += 20;
        if ("1".equals(trim(s.get("present")))) score += 4;
        if (type.contains("usb") || type.contains("mains") || type.contains("ac") || type.contains("charger")) score += 8;
        if (name.contains("usb") || name.contains("pd") || name.contains("chg") || name.contains("charger") || name.contains("dc")) score += 6;
        if (s.get("voltage_now") != null || s.get("current_now") != null) score += 3;
        if (s.get("usb_type") != null || s.get("real_type") != null) score += 3;
        return score;
    }

    private void readKnown(Supply s, File dir, String... names) {
        for (String name : names) {
            String v = readFirstLine(new File(dir, name));
            if (v != null && !v.isEmpty()) s.values.put(name, v);
        }
    }

    private void readUevent(Supply s, File file) {
        if (!file.canRead()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                int p = line.indexOf('=');
                if (p <= 0) continue;
                String k = line.substring(0, p).trim();
                String v = line.substring(p + 1).trim();
                if (k.startsWith("POWER_SUPPLY_")) {
                    String normalized = k.substring("POWER_SUPPLY_".length()).toLowerCase(Locale.US);
                    if (!s.values.containsKey(normalized)) s.values.put(normalized, v);
                }
            }
        } catch (Exception ignored) {}
    }

    private String readFirstLine(File f) {
        if (!f.canRead()) return null;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            return br.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private Double voltageFromAny(Supply s, String... keys) {
        for (String k : keys) {
            Double v = normalizeVoltage(s.get(k));
            if (v != null && Math.abs(v) > 0.01) return v;
        }
        return null;
    }

    private Double currentFromAny(Supply s, String... keys) {
        for (String k : keys) {
            Double a = normalizeCurrent(s.get(k));
            if (a != null && Math.abs(a) > 0.001) return a;
        }
        return null;
    }

    private Double powerFromAny(Supply s, String... keys) {
        for (String k : keys) {
            Double p = normalizePower(s.get(k));
            if (p != null && Math.abs(p) > 0.01) return p;
        }
        return null;
    }

    private Double normalizeVoltage(String raw) {
        Double x = parseDouble(raw);
        if (x == null) return null;
        double a = Math.abs(x);
        if (a >= 100000) return x / 1_000_000.0;
        if (a >= 1000) return x / 1000.0;
        return x;
    }

    private Double normalizeCurrent(String raw) {
        Double x = parseDouble(raw);
        if (x == null) return null;
        double a = Math.abs(x);
        if (a >= 100000) return x / 1_000_000.0;
        if (a >= 1000) return x / 1000.0;
        return x;
    }

    private Double normalizePower(String raw) {
        Double x = parseDouble(raw);
        if (x == null) return null;
        double a = Math.abs(x);
        if (a >= 100000) return x / 1_000_000.0;
        if (a >= 1000) return x / 1000.0;
        return x;
    }

    private Double parseDouble(String raw) {
        if (raw == null) return null;
        try { return Double.parseDouble(raw.trim().replace(',', '.')); }
        catch (Exception e) { return null; }
    }

    private String buildDiagnostics(Intent battery, PowerSnapshot sys, long currentNowUa, long currentAvgUa, long chargeCounterUah, long energyCounterNwh) {
        StringBuilder sb = new StringBuilder();
        sb.append("CCOP USB-C LadeMonitor Diagnose\n");
        sb.append("Gerät: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
        sb.append("Android: ").append(android.os.Build.VERSION.RELEASE).append(" (API ").append(android.os.Build.VERSION.SDK_INT).append(")\n\n");

        sb.append("[BatteryManager / ACTION_BATTERY_CHANGED]\n");
        sb.append("plugged=").append(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)).append('\n');
        sb.append("status=").append(battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)).append('\n');
        sb.append("level=").append(battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)).append('\n');
        sb.append("voltage_mV=").append(battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)).append('\n');
        sb.append("temperature_0.1C=").append(battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE)).append('\n');
        sb.append("max_charging_current_uA=").append(battery.getIntExtra(BatteryManager.EXTRA_MAX_CHARGING_CURRENT, -1)).append('\n');
        sb.append("max_charging_voltage_uV=").append(battery.getIntExtra(BatteryManager.EXTRA_MAX_CHARGING_VOLTAGE, -1)).append('\n');
        sb.append("current_now_uA=").append(currentNowUa).append('\n');
        sb.append("current_average_uA=").append(currentAvgUa).append('\n');
        sb.append("charge_counter_uAh=").append(chargeCounterUah).append('\n');
        sb.append("energy_counter_nWh=").append(energyCounterNwh).append("\n\n");

        sb.append("[/sys/class/power_supply]\n");
        if (sys.supplies.isEmpty()) {
            sb.append("Keine lesbaren Knoten.\n");
        } else {
            for (Supply s : sys.supplies) {
                sb.append("\n<").append(s.name).append(">\n");
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

    private String pluggedName(int plugged) {
        List<String> names = new ArrayList<>();
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) names.add("USB / USB-C");
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) names.add("Netzteil / AC");
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) names.add("Wireless");
        if (android.os.Build.VERSION.SDK_INT >= 33 && (plugged & BatteryManager.BATTERY_PLUGGED_DOCK) != 0) names.add("Dock");
        return names.isEmpty() ? "Unbekannt" : join(names, " + ");
    }

    private String normalizePercent(String raw) {
        Double x = parseDouble(raw);
        if (x == null) return raw;
        if (x >= 0 && x <= 100) return fmt(x, 0) + " %";
        return raw;
    }

    private void set(String key, String value, int color) {
        TextView tv = values.get(key);
        if (tv != null) {
            tv.setText(value);
            tv.setTextColor(color);
        }
    }

    private void addMetric(LinearLayout parent, String key, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));

        TextView l = text(label, 13, MUTED, false);
        row.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView v = text("—", 15, TEXT, true);
        v.setGravity(Gravity.END);
        v.setMaxWidth(dp(220));
        row.addView(v, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        values.put(key, v);
        parent.addView(row);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(roundRect(PANEL, 20, LINE));
        c.setElevation(dp(2));
        return c;
    }

    private TextView sectionTitle(String s) {
        TextView t = text(s, 11, MUTED, true);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setLineSpacing(0f, 1.08f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(12);
        b.setTextColor(TEXT);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackground(roundRect(PANEL2, 14, LINE));
        return b;
    }

    private GradientDrawable roundRect(int fill, int radiusDp, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(radiusDp));
        d.setStroke(dp(1), stroke);
        return d;
    }

    private LinearLayout.LayoutParams marginBottom() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String fmt(double v, int digits) {
        return String.format(Locale.GERMANY, "%." + digits + "f", v);
    }

    private String firstNonEmpty(String... xs) {
        for (String x : xs) if (x != null && !x.trim().isEmpty()) return x.trim();
        return null;
    }

    private String joinNonEmpty(String sep, String... xs) {
        List<String> out = new ArrayList<>();
        for (String x : xs) if (x != null && !x.trim().isEmpty()) out.add(x.trim());
        return out.isEmpty() ? null : join(out, sep);
    }

    private String join(List<String> xs, String sep) {
        StringBuilder sb = new StringBuilder();
        for (String x : xs) {
            if (sb.length() > 0) sb.append(sep);
            sb.append(x);
        }
        return sb.toString();
    }

    private String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.US); }
    private String trim(String s) { return s == null ? "" : s.trim(); }

    private static class Supply {
        final String name;
        final LinkedHashMap<String, String> values = new LinkedHashMap<>();
        Supply(String name) { this.name = name; }
        String get(String key) { return values.get(key); }
    }

    private static class PowerSnapshot {
        final List<Supply> supplies = new ArrayList<>();
        Supply bestExternal;
    }
}
