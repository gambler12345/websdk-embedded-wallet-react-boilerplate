package com.ccop.usbcpowermonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.SystemClock;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TelemetryService extends Service {
    public static final String ACTION_TELEMETRY = "com.ccop.usbcpowermonitor.energyv6.TELEMETRY";
    public static final String ACTION_STOP = "com.ccop.usbcpowermonitor.energyv6.STOP";
    private static final String CHANNEL_ID = "ccop_lademonitor_v6";
    private static final int NOTIFY_ID = 6001;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private BatteryManager batteryManager;
    private ProfileStore profileStore;
    private SharedPreferences totals;
    private PowerSnapshot cached = new PowerSnapshot();
    private long lastSystemScan = 0L;
    private long lastTickElapsed = 0L;
    private long lastHistoryWrite = 0L;
    private boolean lastCharging = false;
    private boolean lastPlugged = false;
    private double cycleNetWh = 0.0;
    private double cycleSourceWh = 0.0;
    private double totalNetWh = 0.0;
    private double totalSourceWh = 0.0;
    private int cycleId = 0;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            try { sample(); } catch (Throwable ignored) { }
            handler.postDelayed(this, 1000L);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        profileStore = new ProfileStore(this);
        totals = getSharedPreferences("ccop_telemetry_totals_v6", MODE_PRIVATE);
        totalNetWh = bitsToDouble(totals.getLong("totalNetWh", Double.doubleToLongBits(0.0)));
        totalSourceWh = bitsToDouble(totals.getLong("totalSourceWh", Double.doubleToLongBits(0.0)));
        cycleId = totals.getInt("cycleId", 0);
        createChannel();
        startForeground(NOTIFY_ID, buildNotification("Monitor gestartet", "Warte auf Messdaten"));
        lastTickElapsed = SystemClock.elapsedRealtime();
        handler.post(ticker);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(ticker);
        saveTotals();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void sample() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return;

        long nowWall = System.currentTimeMillis();
        long nowElapsed = SystemClock.elapsedRealtime();
        double dtHours = Math.max(0.0, Math.min(5.0, (nowElapsed - lastTickElapsed) / 1000.0)) / 3600.0;
        lastTickElapsed = nowElapsed;

        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        int battMv = battery.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
        int temp10 = battery.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        int maxCurrentRaw = battery.getIntExtra("max_charging_current", -1);
        int maxVoltageRaw = battery.getIntExtra("max_charging_voltage", -1);

        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        boolean pluggedNow = plugged != 0;
        double phonePct = (level >= 0 && scale > 0) ? 100.0 * level / scale : Double.NaN;
        double battV = battMv > 0 ? battMv / 1000.0 : Double.NaN;
        long nowUa = safeProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        long avgUa = safeProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE);
        long counterUah = safeProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
        boolean hasCurrent = validProperty(nowUa);
        double battA = hasCurrent ? nowUa / 1_000_000.0 : Double.NaN;
        double battMa = hasCurrent ? nowUa / 1000.0 : Double.NaN;
        double battW = (!Double.isNaN(battV) && hasCurrent) ? Math.abs(battV * battA) : Double.NaN;

        if (pluggedNow && !lastPlugged) {
            cycleId++;
            cycleNetWh = 0.0;
            cycleSourceWh = 0.0;
            totals.edit().putInt("cycleId", cycleId).apply();
        }

        if (nowWall - lastSystemScan >= 5000L || lastSystemScan == 0L) {
            cached = scanPowerSupplies();
            lastSystemScan = nowWall;
        }

        Reading srcV = findExternalReading(cached, Metric.VOLTAGE);
        Reading srcA = findExternalReading(cached, Metric.CURRENT);
        Reading srcP = findExternalReading(cached, Metric.POWER);
        Double exactSourceW = srcP != null ? Math.abs(srcP.value) : (srcV != null && srcA != null ? Math.abs(srcV.value * srcA.value) : null);
        Double directSourceSoc = findDirectSourceSoc(cached);
        String sourceType = cached.bestExternal != null ? first(cached.bestExternal.get("usb_type"), cached.bestExternal.get("real_type"), cached.bestExternal.get("type")) : null;

        ProfileStore.Profile active = profileStore.getActive();
        double efficiency = active != null ? clamp(active.efficiency, 0.5, 1.0) : 0.88;
        Double estimatedSourceW = (!Double.isNaN(battW) && charging) ? Math.abs(battW) / efficiency : null;
        Double effectiveSourceW = exactSourceW != null ? exactSourceW : estimatedSourceW;
        boolean sourcePowerEstimated = exactSourceW == null && effectiveSourceW != null;

        if (charging && dtHours > 0 && !Double.isNaN(battW)) {
            double d = Math.abs(battW) * dtHours;
            cycleNetWh += d;
            totalNetWh += d;
        }
        if (charging && dtHours > 0 && effectiveSourceW != null) {
            double d = Math.abs(effectiveSourceW) * dtHours;
            cycleSourceWh += d;
            totalSourceWh += d;
            if (active != null && !"CHARGER".equalsIgnoreCase(active.type)) {
                active.cumulativeSourceWh += d;
                if (directSourceSoc != null) {
                    active.estimatedSoc = clamp(directSourceSoc, 0, 100);
                    active.lastValidatedSoc = active.estimatedSoc;
                    active.lastValidationAt = nowWall;
                } else if (!active.passthrough && active.capacityWh() > 0) {
                    double deltaPct = d / active.capacityWh() * 100.0;
                    active.estimatedSoc = clamp(active.estimatedSoc - deltaPct, 0, 100);
                }
                profileStore.saveProfile(active);
            }
        } else if (active != null && directSourceSoc != null && !"CHARGER".equalsIgnoreCase(active.type)) {
            active.estimatedSoc = clamp(directSourceSoc, 0, 100);
            active.lastValidatedSoc = active.estimatedSoc;
            active.lastValidationAt = nowWall;
            profileStore.saveProfile(active);
        }

        double aggregateSoc = aggregateSoc(phonePct);
        double activeSourceSoc = active != null && !"CHARGER".equalsIgnoreCase(active.type) ? active.estimatedSoc : Double.NaN;

        boolean stateChanged = charging != lastCharging || pluggedNow != lastPlugged;
        long interval = charging ? 5000L : 60000L;
        if (stateChanged || nowWall - lastHistoryWrite >= interval) {
            HistoryStore.Point point = new HistoryStore.Point();
            point.ts = nowWall;
            point.charging = charging;
            point.phonePct = phonePct;
            point.batteryV = battV;
            point.batteryMa = battMa;
            point.batteryW = charging && !Double.isNaN(battW) ? battW : 0.0;
            point.sourceV = srcV != null ? srcV.value : Double.NaN;
            point.sourceA = srcA != null ? srcA.value : Double.NaN;
            point.sourceW = charging && effectiveSourceW != null ? effectiveSourceW : 0.0;
            point.sourceSoc = activeSourceSoc;
            point.aggregateSoc = aggregateSoc;
            point.cycleWh = cycleNetWh;
            point.profileId = active != null ? active.id : "";
            point.profileName = active != null ? active.name : "";
            HistoryStore.append(this, point);
            lastHistoryWrite = nowWall;
        }

        saveTotals();
        broadcast(nowWall, charging, pluggedNow, plugged, phonePct, battV, battA, battMa, battW, avgUa, counterUah,
                temp10, maxVoltageRaw, maxCurrentRaw, srcV, srcA, exactSourceW, effectiveSourceW, sourcePowerEstimated,
                directSourceSoc, sourceType, active, activeSourceSoc, aggregateSoc);
        updateNotification(charging, phonePct, battMa, battW, active, aggregateSoc);

        lastCharging = charging;
        lastPlugged = pluggedNow;
    }

    private void broadcast(long ts, boolean charging, boolean pluggedNow, int plugged, double phonePct, double battV,
                           double battA, double battMa, double battW, long avgUa, long counterUah, int temp10,
                           int maxVRaw, int maxARaw, Reading srcV, Reading srcA, Double exactSourceW, Double effectiveSourceW,
                           boolean sourcePowerEstimated, Double directSourceSoc, String sourceType, ProfileStore.Profile active,
                           double activeSourceSoc, double aggregateSoc) {
        Intent i = new Intent(ACTION_TELEMETRY);
        i.setPackage(getPackageName());
        i.putExtra("ts", ts);
        i.putExtra("charging", charging);
        i.putExtra("plugged", pluggedNow);
        i.putExtra("plugType", plugName(plugged));
        putFinite(i, "phonePct", phonePct);
        putFinite(i, "batteryV", battV);
        putFinite(i, "batteryA", battA);
        putFinite(i, "batteryMa", battMa);
        putFinite(i, "batteryW", battW);
        if (validProperty(avgUa)) i.putExtra("batteryAvgMa", avgUa / 1000.0);
        if (validProperty(counterUah)) i.putExtra("chargeCounterMah", counterUah / 1000.0);
        if (temp10 != Integer.MIN_VALUE) i.putExtra("temperatureC", temp10 / 10.0);
        if (maxVRaw > 0) i.putExtra("profileMaxV", normalizeVoltage(maxVRaw));
        if (maxARaw > 0) i.putExtra("profileMaxA", normalizeCurrent(maxARaw));
        if (srcV != null) { i.putExtra("sourceV", srcV.value); i.putExtra("sourceVPath", srcV.path); }
        if (srcA != null) { i.putExtra("sourceA", srcA.value); i.putExtra("sourceAPath", srcA.path); }
        if (exactSourceW != null) i.putExtra("sourceExactW", exactSourceW);
        if (effectiveSourceW != null) i.putExtra("sourceEffectiveW", effectiveSourceW);
        i.putExtra("sourcePowerEstimated", sourcePowerEstimated);
        if (directSourceSoc != null) i.putExtra("sourceDirectSoc", directSourceSoc);
        if (sourceType != null) i.putExtra("sourceType", sourceType);
        if (active != null) {
            i.putExtra("profileId", active.id);
            i.putExtra("profileName", active.name);
            i.putExtra("profileType", active.type);
            i.putExtra("profilePassthrough", active.passthrough);
            i.putExtra("profileCapacityWh", active.capacityWh());
        }
        putFinite(i, "sourceSoc", activeSourceSoc);
        putFinite(i, "aggregateSoc", aggregateSoc);
        i.putExtra("cycleNetWh", cycleNetWh);
        i.putExtra("cycleSourceWh", cycleSourceWh);
        i.putExtra("totalNetWh", totalNetWh);
        i.putExtra("totalSourceWh", totalSourceWh);
        i.putExtra("cycleId", cycleId);
        i.putExtra("historyCount", HistoryStore.count(this));
        sendBroadcast(i);
    }

    private double aggregateSoc(double phonePct) {
        double fullWh = 0.0;
        double remainingWh = 0.0;
        double phoneWh = profileStore.getPhoneCapacityMah() / 1000.0 * profileStore.getPhoneNominalV();
        if (phoneWh > 0 && !Double.isNaN(phonePct)) {
            fullWh += phoneWh;
            remainingWh += phoneWh * clamp(phonePct, 0, 100) / 100.0;
        }
        for (ProfileStore.Profile p : profileStore.load()) {
            if ("CHARGER".equalsIgnoreCase(p.type)) continue;
            double wh = p.capacityWh();
            if (wh <= 0) continue;
            fullWh += wh;
            remainingWh += wh * clamp(p.estimatedSoc, 0, 100) / 100.0;
        }
        return fullWh > 0 ? clamp(remainingWh / fullWh * 100.0, 0, 100) : Double.NaN;
    }

    private void updateNotification(boolean charging, double phonePct, double battMa, double battW,
                                    ProfileStore.Profile active, double aggregateSoc) {
        String title = charging ? "CCOP LadeMonitor · Laden aktiv" : "CCOP LadeMonitor · Monitoring";
        String speed = !Double.isNaN(battMa) ? String.format(Locale.GERMANY, "%.0f mA", Math.abs(battMa)) : "— mA";
        String watts = !Double.isNaN(battW) ? String.format(Locale.GERMANY, "%.2f W", Math.abs(battW)) : "— W";
        String pct = !Double.isNaN(phonePct) ? String.format(Locale.GERMANY, "%.0f%%", phonePct) : "—%";
        String src = active != null ? active.name : "keine Quelle";
        String agg = !Double.isNaN(aggregateSoc) ? String.format(Locale.GERMANY, " · Gesamt %.0f%%", aggregateSoc) : "";
        Notification n = buildNotification(title, speed + " · " + watts + " · Handy " + pct + agg + " · " + src);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFY_ID, n);
    }

    private Notification buildNotification(String title, String body) {
        Intent open = new Intent(this, MainActivity.class);
        int f = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, f);
        Intent stop = new Intent(this, TelemetryService.class).setAction(ACTION_STOP);
        PendingIntent psi = PendingIntent.getService(this, 1, stop, f);
        Notification.Builder b = Build.VERSION.SDK_INT >= 26 ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        b.setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setOngoing(true)
                .setContentIntent(pi)
                .addAction(new Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Stop", psi).build());
        if (Build.VERSION.SDK_INT >= 21) b.setCategory(Notification.CATEGORY_SERVICE);
        return b.build();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL_ID, "CCOP LadeMonitor", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Lokales Lade- und Energie-Monitoring");
            ((NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private void saveTotals() {
        totals.edit()
                .putLong("totalNetWh", Double.doubleToLongBits(totalNetWh))
                .putLong("totalSourceWh", Double.doubleToLongBits(totalSourceWh))
                .putLong("cycleNetWh", Double.doubleToLongBits(cycleNetWh))
                .putLong("cycleSourceWh", Double.doubleToLongBits(cycleSourceWh))
                .putInt("cycleId", cycleId)
                .apply();
    }

    private double bitsToDouble(long bits) { return Double.longBitsToDouble(bits); }

    private long safeProperty(int id) {
        try { return batteryManager.getIntProperty(id); }
        catch (Throwable t) { return Long.MIN_VALUE; }
    }

    private boolean validProperty(long v) { return v != Long.MIN_VALUE && v != Integer.MIN_VALUE; }

    private PowerSnapshot scanPowerSupplies() {
        PowerSnapshot snap = new PowerSnapshot();
        File root = new File("/sys/class/power_supply");
        try {
            File[] dirs = root.listFiles();
            if (dirs != null) {
                for (File d : dirs) {
                    if (!d.isDirectory()) continue;
                    Supply s = readSupply(d);
                    if (s != null) snap.supplies.add(s);
                }
            }
        } catch (Throwable ignored) { }
        int best = Integer.MIN_VALUE;
        for (Supply s : snap.supplies) {
            int score = externalScore(s);
            if (score > best) {
                best = score;
                snap.bestExternal = score > 0 ? s : null;
            }
        }
        return snap;
    }

    private Supply readSupply(File d) {
        try {
            Supply s = new Supply(d.getName());
            String[] keys = new String[]{
                    "type","usb_type","real_type","online","present","status","capacity","capacity_raw",
                    "voltage_now","voltage_avg","vbus_voltage","vbus_voltage_now","voltage_vbus","usb_voltage","input_voltage","charger_voltage","pd_voltage","pd_voltage_now",
                    "current_now","current_avg","ibus_current","ibus_current_now","current_ibus","usb_current","input_current","charger_current","pd_current","pd_current_now",
                    "power_now","power_avg","pd_active","pd_state","model_name","manufacturer"
            };
            for (String k : keys) {
                String v = readText(new File(d, k));
                if (v != null) s.values.put(k, v);
            }
            return s.values.isEmpty() ? null : s;
        } catch (Throwable t) { return null; }
    }

    private int externalScore(Supply s) {
        String n = s.name.toLowerCase(Locale.ROOT);
        String type = lower(s.get("type"));
        if (n.contains("battery") || n.contains("bms") || type.contains("battery")) return -1000;
        int score = 1;
        if ("1".equals(s.get("online"))) score += 120;
        if ("1".equals(s.get("present"))) score += 15;
        if (n.contains("usb") || n.contains("charger") || n.contains("chg") || n.contains("main") || n.contains("ac") || n.contains("pd") || n.contains("typec")) score += 45;
        if (type.contains("usb") || type.contains("mains") || type.contains("pd")) score += 45;
        return score;
    }

    private Reading findExternalReading(PowerSnapshot snap, Metric metric) {
        String[] keys = metric == Metric.VOLTAGE ? voltageKeys() : (metric == Metric.CURRENT ? currentKeys() : powerKeys());
        Reading best = null;
        int bestScore = Integer.MIN_VALUE;
        for (Supply s : snap.supplies) {
            int ss = externalScore(s);
            if (ss <= 0) continue;
            if ("0".equals(s.get("online")) && "0".equals(s.get("present"))) continue;
            for (int i = 0; i < keys.length; i++) {
                String k = keys[i];
                Double raw = parseDouble(s.get(k));
                if (raw == null || raw == 0.0) continue;
                double v = metric == Metric.VOLTAGE ? normalizeVoltage(raw) : (metric == Metric.CURRENT ? normalizeCurrent(raw) : normalizePower(raw));
                if (!sane(metric, v)) continue;
                int score = ss + (keys.length - i) * 3;
                if (score > bestScore) {
                    bestScore = score;
                    best = new Reading(v, "/sys/class/power_supply/" + s.name + "/" + k);
                }
            }
        }
        return best;
    }

    private Double findDirectSourceSoc(PowerSnapshot snap) {
        Supply s = snap.bestExternal;
        if (s == null) return null;
        Double c = parseDouble(first(s.get("capacity"), s.get("capacity_raw")));
        if (c == null) return null;
        if (c >= 0 && c <= 100) return c;
        if (c > 100 && c <= 10000) return c / 100.0;
        return null;
    }

    private String[] voltageKeys() { return new String[]{"vbus_voltage_now","vbus_voltage","voltage_vbus","usb_voltage","input_voltage","charger_voltage","pd_voltage_now","pd_voltage","voltage_now","voltage_avg"}; }
    private String[] currentKeys() { return new String[]{"ibus_current_now","ibus_current","current_ibus","usb_current","input_current","charger_current","pd_current_now","pd_current","current_now","current_avg"}; }
    private String[] powerKeys() { return new String[]{"power_now","power_avg"}; }

    private boolean sane(Metric m, double v) {
        double a = Math.abs(v);
        if (m == Metric.VOLTAGE) return a >= 1 && a <= 30;
        if (m == Metric.CURRENT) return a >= 0.0005 && a <= 15;
        return a >= 0.001 && a <= 400;
    }

    private String readText(File f) {
        try {
            if (!f.exists() || !f.isFile() || !f.canRead()) return null;
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line = r.readLine();
            r.close();
            return line == null ? null : line.trim();
        } catch (Throwable t) { return null; }
    }

    private Double parseDouble(String s) {
        if (s == null) return null;
        try { return Double.parseDouble(s.trim()); }
        catch (Throwable t) { return null; }
    }

    private double normalizeVoltage(double raw) {
        double a = Math.abs(raw);
        if (a > 100000) return raw / 1_000_000.0;
        if (a > 100) return raw / 1000.0;
        return raw;
    }

    private double normalizeCurrent(double raw) {
        double a = Math.abs(raw);
        if (a > 100000) return raw / 1_000_000.0;
        if (a > 100) return raw / 1000.0;
        return raw;
    }

    private double normalizePower(double raw) {
        double a = Math.abs(raw);
        if (a > 100000) return raw / 1_000_000.0;
        if (a > 1000) return raw / 1000.0;
        return raw;
    }

    private String plugName(int p) {
        if ((p & BatteryManager.BATTERY_PLUGGED_AC) != 0) return "Netzteil / AC";
        if ((p & BatteryManager.BATTERY_PLUGGED_USB) != 0) return "USB";
        if ((p & BatteryManager.BATTERY_PLUGGED_WIRELESS) != 0) return "Wireless";
        return p == 0 ? "keine" : "extern";
    }

    private static void putFinite(Intent i, String key, double v) {
        if (!Double.isNaN(v) && !Double.isInfinite(v)) i.putExtra(key, v);
    }

    private String first(String... vals) {
        if (vals == null) return null;
        for (String s : vals) if (s != null && !s.trim().isEmpty()) return s.trim();
        return null;
    }

    private String lower(String s) { return s == null ? "" : s.toLowerCase(Locale.ROOT); }
    private double clamp(double v, double min, double max) { return Math.max(min, Math.min(max, v)); }

    private enum Metric { VOLTAGE, CURRENT, POWER }
    private static class Reading { final double value; final String path; Reading(double v, String p) { value = v; path = p; } }
    private static class Supply { final String name; final Map<String,String> values = new LinkedHashMap<>(); Supply(String n){name=n;} String get(String k){return values.get(k);} }
    private static class PowerSnapshot { final List<Supply> supplies = new ArrayList<>(); Supply bestExternal; }
}
