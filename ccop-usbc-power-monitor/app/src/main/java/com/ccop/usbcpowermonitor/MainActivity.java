package com.ccop.usbcpowermonitor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.BatteryManager;
import android.os.Build;
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

    // These ACTION_BATTERY_CHANGED extras exist on many Android/OEM builds but are not
    // public BatteryManager constants, so use their literal keys and fall back to sysfs.
    private static final String EXTRA_MAX_CHARGING_CURRENT = "max_charging_current";
    private static final String EXTRA_MAX_CHARGING_VOLTAGE = "max_charging_voltage";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<String, TextView> values = new HashMap<>();
    private BatteryManager batteryManager;
    private TextView statusText;
    private TextView diagText;
    private String latestDiagnostics = "Noch keine Diagnose.";

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            refreshTelemetry();
            handler.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        setContentView(buildUi());
    }

    @Override protected void onResume() {
        super.onResume();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
    }

    @Override protected void onPause() {
        handler.removeCallbacks(ticker);
        super.onPause();
    }

    private View buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(18), dp(14), dp(34));
        scroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(text("CCOP USB-C LadeMonitor", 23, TEXT, true));
        TextView sub = text("Native Android-Auswertung · ohne Browser · ohne Bluetooth", 13, MUTED, false);
        sub.setPadding(0, dp(3), 0, dp(14));
        root.addView(sub);

        LinearLayout source = card();
        source.addView(sectionTitle("EXTERNE ENERGIEQUELLE"));
        statusText = text("PRÜFE…", 34, TEXT, true);
        statusText.setPadding(0, dp(9), 0, dp(6));
        source.addView(statusText);
        addMetric(source, "plug", "Anschluss / Quelle");
        addMetric(source, "charging", "Aktiver Ladezyklus");
        addMetric(source, "supply", "Android Power-Supply");
        addMetric(source, "usbType", "USB / PD Typ");
        root.addView(source, marginBottom());

        LinearLayout charger = card();
        charger.addView(sectionTitle("USB-C / LADEGERÄT · LIVE"));
        addMetric(charger, "inputVoltage", "Eingangsspannung");
        addMetric(charger, "inputCurrent", "Eingangsstrom");
        addMetric(charger, "inputPower", "Eingangsleistung");
        addMetric(charger, "maxVoltage", "Gemeldete max. Spannung");
        addMetric(charger, "maxCurrent", "Gemeldeter max. Strom");
        addMetric(charger, "pdActive", "USB-PD / Profil");
        root.addView(charger, marginBottom());

        LinearLayout battery = card();
        battery.addView(sectionTitle("SMARTPHONE-AKKU · DIREKT"));
        addMetric(battery, "batteryPct", "Ladezustand");
        addMetric(battery, "batteryVoltage", "Akkuspannung");
        addMetric(battery, "batteryCurrent", "Momentaner Akkustrom");
        addMetric(battery, "batteryCurrentAvg", "Mittlerer Akkustrom");
        addMetric(battery, "batteryPower", "Momentane Akkuleistung");
        addMetric(battery, "chargeCounter", "Charge Counter");
        addMetric(battery, "energyCounter", "Energy Counter");
        addMetric(battery, "temperature", "Temperatur");
        root.addView(battery, marginBottom());

        LinearLayout bank = card();
        bank.addView(sectionTitle("EXTERNE POWERBANK"));
        addMetric(bank, "pbSoc", "Übertragener Ladezustand");
        addMetric(bank, "pbVoltage", "Quellspannung");
        addMetric(bank, "pbCurrent", "Quellstrom");
        addMetric(bank, "pbModel", "Modell / Hersteller");
        TextView bankInfo = text(
                "Der Ladezustand der Powerbank erscheint hier nur, wenn die Quelle ihn als digitalen Wert an Android überträgt. Spannung und Strom werden zusätzlich aus allen lesbaren Android-Power-Supply-Knoten gesucht.",
                12, MUTED, false);
        bankInfo.setPadding(0, dp(10), 0, 0);
        bank.addView(bankInfo);
        root.addView(bank, marginBottom());

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button refresh = button("JETZT PRÜFEN");
        refresh.setOnClickListener(v -> refreshTelemetry());
        actions.addView(refresh, new LinearLayout.LayoutParams(0, dp(50), 1f));
        Button share = button("DIAGNOSE TEILEN");
        share.setOnClickListener(v -> shareDiagnostics());
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(50), 1f);
        slp.setMargins(dp(8), 0, 0, 0);
        actions.addView(share, slp);
        root.addView(actions, marginBottom());

        LinearLayout diag = card();
        diag.addView(sectionTitle("GERÄTE-DIAGNOSE · OEM / MOTOROLA"));
        TextView diagInfo = text(
                "Die APK liest zusätzlich /sys/class/power_supply. Dadurch werden herstellerspezifische USB-C-, Charger-, PD- und BMS-Felder sichtbar, sofern Android sie normalen Apps freigibt.",
                12, MUTED, false);
        diagInfo.setPadding(0, dp(5), 0, dp(10));
        diag.addView(diagInfo);
        diagText = text("Prüfe…", 11, TEXT, false);
        diagText.setTypeface(Typeface.MONOSPACE);
        diagText.setTextIsSelectable(true);
        diag.addView(diagText);
        root.addView(diag, marginBottom());

        TextView footer = text("Lokal · keine Cloud · kein Bluetooth · native Android-APIs", 11, MUTED, false);
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
        int maxCurrentUa = battery.getIntExtra(EXTRA_MAX_CHARGING_CURRENT, -1);
        int maxVoltageUv = battery.getIntExtra(EXTRA_MAX_CHARGING_VOLTAGE, -1);

        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;
        double pct = level >= 0 && scale > 0 ? (100.0 * level / scale) : Double.NaN;
        double batteryV = battMv > 0 ? battMv / 1000.0 : Double.NaN;

        long currentNowUa = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long currentAvgUa = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        long chargeCounterUah = safeIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        long energyCounterNwh = safeLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);

        PowerSnapshot snapshot = readPowerSupplies();
        Supply external = snapshot.bestExternal;
        Supply externalBattery = snapshot.bestExternalBattery;

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
        set("supply", external != null ? external.name : "kein externer Knoten lesbar", external != null ? TEXT : MUTED);

        String usbType = external != null ? firstNonEmpty(
                external.get("usb_type"), external.get("real_type"), external.get("type"),
                external.get("charger_type"), external.get("charge_type")) : null;
        set("usbType", usbType != null ? usbType : plugName, usbType != null ? BLUE : MUTED);

        Double inputV = external != null ? voltageFromAny(external,
                "voltage_now", "voltage_avg", "voltage_ocv", "pd_voltage",
                "input_voltage_now", "vbus_voltage", "voltage_max") : null;
        Double inputA = external != null ? currentFromAny(external,
                "current_now", "current_avg", "pd_current", "input_current_now",
                "ibus_current", "current_max", "input_current_limit") : null;
        Double inputW = external != null ? powerFromAny(external,
                "power_now", "power_avg", "input_power_now", "power_max") : null;
        if (inputW == null && inputV != null && inputA != null) inputW = Math.abs(inputV * inputA);

        // Fall back to max charger values reported in ACTION_BATTERY_CHANGED when OEM exposes them.
        Double maxV = maxVoltageUv > 0 ? maxVoltageUv / 1_000_000.0 : null;
        Double maxA = maxCurrentUa > 0 ? maxCurrentUa / 1_000_000.0 : null;
        if (inputV == null && maxV != null) inputV = maxV;
        if (inputA == null && maxA != null) inputA = maxA;
        if (inputW == null && inputV != null && inputA != null) inputW = Math.abs(inputV * inputA);

        set("inputVoltage", inputV != null ? fmt(inputV, 3) + " V" : "nicht freigegeben", inputV != null ? OK : MUTED);
        set("inputCurrent", inputA != null ? fmt(Math.abs(inputA), 3) + " A" : "nicht freigegeben", inputA != null ? OK : MUTED);
        set("inputPower", inputW != null ? fmt(Math.abs(inputW), 2) + " W" : "nicht direkt verfügbar", inputW != null ? OK : MUTED);
        set("maxVoltage", maxV != null ? fmt(maxV, 2) + " V" : "nicht gemeldet", maxV != null ? BLUE : MUTED);
        set("maxCurrent", maxA != null ? fmt(maxA, 2) + " A" : "nicht gemeldet", maxA != null ? BLUE : MUTED);

        String pd = external != null ? firstNonEmpty(
                external.get("pd_active"), external.get("pd_state"), external.get("pd_type"),
                external.get("usb_type"), external.get("real_type"), external.get("adapter_type")) : null;
        set("pdActive", pd != null ? pd : "nicht gemeldet", pd != null ? BLUE : MUTED);

        set("batteryPct", Double.isNaN(pct) ? "nicht gemeldet" : fmt(pct, 0) + " %", TEXT);
        set("batteryVoltage", Double.isNaN(batteryV) ? "nicht gemeldet" : fmt(batteryV, 3) + " V", !Double.isNaN(batteryV) ? OK : MUTED);

        Double currentNowA = validIntProperty(currentNowUa) ? currentNowUa / 1_000_000.0 : null;
        Double currentAvgA = validIntProperty(currentAvgUa) ? currentAvgUa / 1_000_000.0 : null;
        set("batteryCurrent", currentNowA != null ? fmt(currentNowA, 3) + " A" : "nicht gemeldet", currentNowA != null ? OK : MUTED);
        set("batteryCurrentAvg", currentAvgA != null ? fmt(currentAvgA, 3) + " A" : "nicht gemeldet", currentAvgA != null ? TEXT : MUTED);

        if (!Double.isNaN(batteryV) && currentNowA != null) {
            set("batteryPower", fmt(Math.abs(batteryV * currentNowA), 2) + " W", OK);
        } else {
            set("batteryPower", "nicht berechenbar", MUTED);
        }

        set("chargeCounter", validIntProperty(chargeCounterUah) ? fmt(chargeCounterUah / 1000.0, 0) + " mAh" : "nicht gemeldet",
                validIntProperty(chargeCounterUah) ? TEXT : MUTED);
        set("energyCounter", validLongProperty(energyCounterNwh) ? fmt(energyCounterNwh / 1_000_000_000.0, 3) + " Wh" : "nicht gemeldet",
                validLongProperty(energyCounterNwh) ? TEXT : MUTED);
        set("temperature", tempTenthC != Integer.MIN_VALUE ? fmt(tempTenthC / 10.0, 1) + " °C" : "nicht gemeldet", TEXT);

        String pbSoc = externalBattery != null ? firstNonEmpty(externalBattery.get("capacity"), externalBattery.get("capacity_raw")) : null;
        if (pbSoc == null && external != null && !looksLikePhoneBattery(external)) {
            pbSoc = firstNonEmpty(external.get("capacity"), external.get("capacity_raw"));
        }
        set("pbSoc", pbSoc != null ? normalizePercent(pbSoc) : "nicht übertragen", pbSoc != null ? OK : MUTED);
        set("pbVoltage", inputV != null ? fmt(inputV, 3) + " V" : "nicht übertragen", inputV != null ? TEXT : MUTED);
        set("pbCurrent", inputA != null ? fmt(Math.abs(inputA), 3) + " A" : "nicht übertragen", inputA != null ? TEXT : MUTED);

        Supply modelSupply = externalBattery != null ? externalBattery : external;
        String model = modelSupply != null ? joinNonEmpty(" · ",
                modelSupply.get("manufacturer"), modelSupply.get("model_name"), modelSupply.get("serial_number")) : null;
        set("pbModel", model != null ? model : "nicht übertragen", model != null ? TEXT : MUTED);

        latestDiagnostics = buildDiagnostics(battery, snapshot, currentNowUa, currentAvgUa,
                chargeCounterUah, energyCounterNwh, maxCurrentUa, maxVoltageUv);
        diagText.setText(latestDiagnostics);
    }

    private long safeIntProperty(int id) {
        try { return batteryManager.getIntProperty(id); }
        catch (Throwable t) { return Long.MIN_VALUE; }
    }

    private long safeLongProperty(int id) {
        try { return batteryManager.getLongProperty(id); }
        catch (Throwable t) { return Long.MIN_VALUE; }
    }

    private boolean validIntProperty(long v) {
        return v != Long.MIN_VALUE && v != Integer.MIN_VALUE;
    }

    private boolean validLongProperty(long v) {
        return v != Long.MIN_VALUE && v != Integer.MIN_VALUE;
    }

    private PowerSnapshot readPowerSupplies() {
        PowerSnapshot out = new PowerSnapshot();
        File root = new File("/sys/class/power_supply");
        File[] nodes = root.listFiles();
        if (nodes == null) return out;

        for (File node : nodes) {
            Supply s = new Supply(node.getName());
            readAllSmallTextFiles(s, node);
            readUevent(s, new File(node, "uevent"));
            out.supplies.add(s);
        }
        Collections.sort(out.supplies, Comparator.comparing(a -> a.name));

        int externalScore = Integer.MIN_VALUE;
        int externalBatteryScore = Integer.MIN_VALUE;
        for (Supply s : out.supplies) {
            int score = scoreExternal(s);
            if (score > externalScore) {
                externalScore = score;
                out.bestExternal = score > 0 ? s : null;
            }
            int bscore = scoreExternalBattery(s);
            if (bscore > externalBatteryScore) {
                externalBatteryScore = bscore;
                out.bestExternalBattery = bscore > 0 ? s : null;
            }
        }
        return out;
    }

    private void readAllSmallTextFiles(Supply s, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (!f.isFile() || !f.canRead()) continue;
            if ("uevent".equals(f.getName())) continue;
            long len = f.length();
            if (len > 4096) continue;
            String v = readFirstLine(f);
            if (v != null && !v.trim().isEmpty() && v.length() <= 256) {
                s.values.put(f.getName().toLowerCase(Locale.US), v.trim());
            }
        }
    }

    private void readUevent(Supply s, File file) {
        if (!file.canRead()) return;
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                int p = line.indexOf('=');
                if (p <= 0) continue;
                String key = line.substring(0, p).trim();
                String value = line.substring(p + 1).trim();
                if (key.startsWith("POWER_SUPPLY_")) {
                    key = key.substring("POWER_SUPPLY_".length()).toLowerCase(Locale.US);
                    if (!s.values.containsKey(key)) s.values.put(key, value);
                }
            }
        } catch (Exception ignored) { }
    }

    private String readFirstLine(File file) {
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            return br.readLine();
        } catch (Exception e) {
            return null;
        }
    }

    private int scoreExternal(Supply s) {
        String name = lower(s.name);
        String type = lower(s.get("type"));
        if (looksLikePhoneBattery(s)) return -100;
        int score = 0;
        if ("1".equals(trim(s.get("online")))) score += 30;
        if ("1".equals(trim(s.get("present")))) score += 5;
        if (containsAny(type, "usb", "mains", "ac", "charger", "wireless")) score += 12;
        if (containsAny(name, "usb", "pd", "charger", "chg", "dc", "typec", "main")) score += 9;
        if (firstNonEmpty(s.get("voltage_now"), s.get("current_now"), s.get("usb_type"), s.get("real_type")) != null) score += 6;
        return score;
    }

    private int scoreExternalBattery(Supply s) {
        if (looksLikePhoneBattery(s)) return -100;
        String type = lower(s.get("type"));
        String name = lower(s.name);
        int score = 0;
        if (type.contains("battery")) score += 10;
        if (s.get("capacity") != null) score += 12;
        if (containsAny(name, "dock", "case", "powerbank", "external")) score += 15;
        if ("1".equals(trim(s.get("present")))) score += 3;
        return score;
    }

    private boolean looksLikePhoneBattery(Supply s) {
        String name = lower(s.name);
        if (name.equals("battery") || name.equals("bms") || name.equals("fg") || name.contains("main_battery")) return true;
        String scope = lower(s.get("scope"));
        if (scope.contains("system")) return true;
        return false;
    }

    private Double voltageFromAny(Supply s, String... keys) {
        for (String key : keys) {
            Double v = normalizeElectrical(s.get(key));
            if (v != null && Math.abs(v) >= 0.05 && Math.abs(v) < 1000) return v;
        }
        return null;
    }

    private Double currentFromAny(Supply s, String... keys) {
        for (String key : keys) {
            Double v = normalizeElectrical(s.get(key));
            if (v != null && Math.abs(v) >= 0.001 && Math.abs(v) < 1000) return v;
        }
        return null;
    }

    private Double powerFromAny(Supply s, String... keys) {
        for (String key : keys) {
            Double v = normalizeElectrical(s.get(key));
            if (v != null && Math.abs(v) >= 0.01 && Math.abs(v) < 10000) return v;
        }
        return null;
    }

    private Double normalizeElectrical(String raw) {
        Double x = parseDouble(raw);
        if (x == null) return null;
        double a = Math.abs(x);
        if (a >= 100000.0) return x / 1_000_000.0;
        if (a >= 1000.0) return x / 1000.0;
        return x;
    }

    private Double parseDouble(String raw) {
        if (raw == null) return null;
        try { return Double.parseDouble(raw.trim().replace(',', '.')); }
        catch (Exception e) { return null; }
    }

    private String buildDiagnostics(Intent battery, PowerSnapshot snapshot,
                                    long currentNowUa, long currentAvgUa,
                                    long chargeCounterUah, long energyCounterNwh,
                                    int maxCurrentUa, int maxVoltageUv) {
        StringBuilder sb = new StringBuilder();
        sb.append("CCOP USB-C LadeMonitor Diagnose\n");
        sb.append("device=").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n');
        sb.append("android=").append(Build.VERSION.RELEASE).append(" api=").append(Build.VERSION.SDK_INT).append("\n\n");
        sb.append("[ANDROID BATTERY]\n");
        sb.append("plugged=").append(battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)).append('\n');
        sb.append("status=").append(battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)).append('\n');
        sb.append("level=").append(battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)).append('\n');
        sb.append("voltage_mV=").append(battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)).append('\n');
        sb.append("temperature_0.1C=").append(battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE)).append('\n');
        sb.append("max_charging_current_uA=").append(maxCurrentUa).append('\n');
        sb.append("max_charging_voltage_uV=").append(maxVoltageUv).append('\n');
        sb.append("current_now_uA=").append(currentNowUa).append('\n');
        sb.append("current_average_uA=").append(currentAvgUa).append('\n');
        sb.append("charge_counter_uAh=").append(chargeCounterUah).append('\n');
        sb.append("energy_counter_nWh=").append(energyCounterNwh).append("\n\n");
        sb.append("best_external=").append(snapshot.bestExternal != null ? snapshot.bestExternal.name : "none").append('\n');
        sb.append("best_external_battery=").append(snapshot.bestExternalBattery != null ? snapshot.bestExternalBattery.name : "none").append("\n\n");
        sb.append("[/sys/class/power_supply]\n");
        if (snapshot.supplies.isEmpty()) {
            sb.append("Keine lesbaren Power-Supply-Knoten.\n");
        } else {
            for (Supply s : snapshot.supplies) {
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
        List<String> out = new ArrayList<>();
        if ((plugged & BatteryManager.BATTERY_PLUGGED_USB) != 0) out.add("USB / USB-C");
        if ((plugged & BatteryManager.BATTERY_PLUGGED_AC) != 0) out.add("Netzteil / AC");
        if ((plugged & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) out.add("Wireless");
        if (Build.VERSION.SDK_INT >= 33 && (plugged & BatteryManager.BATTERY_PLUGGED_DOCK) != 0) out.add("Dock");
        return out.isEmpty() ? "Unbekannt" : join(out, " + ");
    }

    private String normalizePercent(String raw) {
        Double n = parseDouble(raw);
        if (n == null) return raw;
        return n >= 0 && n <= 100 ? fmt(n, 0) + " %" : raw;
    }

    private void set(String key, String value, int color) {
        TextView tv = values.get(key);
        if (tv == null) return;
        tv.setText(value);
        tv.setTextColor(color);
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
        v.setMaxWidth(dp(230));
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

    private TextView sectionTitle(String value) {
        TextView t = text(value, 11, MUTED, true);
        t.setLetterSpacing(0.12f);
        return t;
    }

    private TextView text(String value, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        t.setLineSpacing(0f, 1.08f);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private Button button(String value) {
        Button b = new Button(this);
        b.setText(value);
        b.setAllCaps(false);
        b.setTextSize(12);
        b.setTextColor(TEXT);
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
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        return lp;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private String fmt(double value, int digits) {
        return String.format(Locale.GERMANY, "%." + digits + "f", value);
    }

    private String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) return value.trim();
        }
        return null;
    }

    private String joinNonEmpty(String separator, String... values) {
        List<String> found = new ArrayList<>();
        for (String value : values) if (value != null && !value.trim().isEmpty()) found.add(value.trim());
        return found.isEmpty() ? null : join(found, separator);
    }

    private String join(List<String> values, String separator) {
        StringBuilder sb = new StringBuilder();
        for (String value : values) {
            if (sb.length() > 0) sb.append(separator);
            sb.append(value);
        }
        return sb.toString();
    }

    private String lower(String v) { return v == null ? "" : v.toLowerCase(Locale.US); }
    private String trim(String v) { return v == null ? "" : v.trim(); }

    private boolean containsAny(String haystack, String... needles) {
        for (String needle : needles) if (haystack.contains(needle)) return true;
        return false;
    }

    private static class Supply {
        final String name;
        final LinkedHashMap<String, String> values = new LinkedHashMap<>();
        Supply(String name) { this.name = name; }
        String get(String key) { return values.get(key); }
    }

    private static class PowerSnapshot {
        final List<Supply> supplies = new ArrayList<>();
        Supply bestExternal;
        Supply bestExternalBattery;
    }
}
