package com.ccop.usbcpowermonitor;

import android.content.Context;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.List;

public class HistoryStore {
    private static final String FILE_NAME = "ccop_lademonitor_history_v6.jsonl";

    public static class Point {
        public long ts;
        public boolean charging;
        public double phonePct = Double.NaN;
        public double batteryV = Double.NaN;
        public double batteryMa = Double.NaN;
        public double batteryW = Double.NaN;
        public double sourceV = Double.NaN;
        public double sourceA = Double.NaN;
        public double sourceW = Double.NaN;
        public double sourceSoc = Double.NaN;
        public double aggregateSoc = Double.NaN;
        public double cycleWh = 0.0;
        public String profileId = "";
        public String profileName = "";

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("ts", ts);
                o.put("charging", charging);
                putFinite(o, "phonePct", phonePct);
                putFinite(o, "batteryV", batteryV);
                putFinite(o, "batteryMa", batteryMa);
                putFinite(o, "batteryW", batteryW);
                putFinite(o, "sourceV", sourceV);
                putFinite(o, "sourceA", sourceA);
                putFinite(o, "sourceW", sourceW);
                putFinite(o, "sourceSoc", sourceSoc);
                putFinite(o, "aggregateSoc", aggregateSoc);
                o.put("cycleWh", cycleWh);
                o.put("profileId", profileId);
                o.put("profileName", profileName);
            } catch (Exception ignored) { }
            return o;
        }

        public static Point fromJson(JSONObject o) {
            Point p = new Point();
            p.ts = o.optLong("ts", 0L);
            p.charging = o.optBoolean("charging", false);
            p.phonePct = optDouble(o, "phonePct");
            p.batteryV = optDouble(o, "batteryV");
            p.batteryMa = optDouble(o, "batteryMa");
            p.batteryW = optDouble(o, "batteryW");
            p.sourceV = optDouble(o, "sourceV");
            p.sourceA = optDouble(o, "sourceA");
            p.sourceW = optDouble(o, "sourceW");
            p.sourceSoc = optDouble(o, "sourceSoc");
            p.aggregateSoc = optDouble(o, "aggregateSoc");
            p.cycleWh = o.optDouble("cycleWh", 0.0);
            p.profileId = o.optString("profileId", "");
            p.profileName = o.optString("profileName", "");
            return p;
        }

        private static void putFinite(JSONObject o, String k, double v) throws Exception {
            if (!Double.isNaN(v) && !Double.isInfinite(v)) o.put(k, v);
        }

        private static double optDouble(JSONObject o, String k) {
            return o.has(k) ? o.optDouble(k, Double.NaN) : Double.NaN;
        }
    }

    public static synchronized void append(Context c, Point p) {
        try {
            File f = new File(c.getFilesDir(), FILE_NAME);
            BufferedWriter w = new BufferedWriter(new FileWriter(f, true));
            w.write(p.toJson().toString());
            w.newLine();
            w.close();
        } catch (Exception ignored) { }
    }

    public static synchronized List<Point> readSince(Context c, long sinceTs, int maxPoints) {
        ArrayList<Point> all = new ArrayList<>();
        try {
            File f = new File(c.getFilesDir(), FILE_NAME);
            if (!f.exists()) return all;
            BufferedReader r = new BufferedReader(new FileReader(f));
            String line;
            while ((line = r.readLine()) != null) {
                try {
                    Point p = Point.fromJson(new JSONObject(line));
                    if (p.ts >= sinceTs) all.add(p);
                } catch (Exception ignored) { }
            }
            r.close();
        } catch (Exception ignored) { }
        if (maxPoints > 0 && all.size() > maxPoints) {
            int step = (int) Math.ceil(all.size() / (double) maxPoints);
            ArrayList<Point> reduced = new ArrayList<>();
            for (int i = 0; i < all.size(); i += step) reduced.add(all.get(i));
            if (!reduced.isEmpty() && reduced.get(reduced.size() - 1) != all.get(all.size() - 1)) reduced.add(all.get(all.size() - 1));
            return reduced;
        }
        return all;
    }

    public static synchronized long count(Context c) {
        long n = 0;
        try {
            File f = new File(c.getFilesDir(), FILE_NAME);
            if (!f.exists()) return 0;
            BufferedReader r = new BufferedReader(new FileReader(f));
            while (r.readLine() != null) n++;
            r.close();
        } catch (Exception ignored) { }
        return n;
    }

    public static synchronized void clear(Context c) {
        try {
            File f = new File(c.getFilesDir(), FILE_NAME);
            if (f.exists()) f.delete();
        } catch (Exception ignored) { }
    }
}
