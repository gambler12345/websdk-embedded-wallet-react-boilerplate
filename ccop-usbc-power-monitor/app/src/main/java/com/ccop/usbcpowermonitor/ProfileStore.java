package com.ccop.usbcpowermonitor;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProfileStore {
    private static final String PREFS = "ccop_energy_profiles_v6";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE = "active_profile";
    private static final String KEY_PHONE_MAH = "phone_capacity_mah";
    private static final String KEY_PHONE_V = "phone_nominal_v";

    public static class Profile {
        public String id = UUID.randomUUID().toString();
        public String name = "Powerbank";
        public String type = "POWERBANK";
        public double capacityMah = 10000.0;
        public double nominalV = 3.70;
        public double estimatedSoc = 100.0;
        public double lastValidatedSoc = 100.0;
        public long lastValidationAt = System.currentTimeMillis();
        public double efficiency = 0.88;
        public boolean passthrough = false;
        public String note = "";
        public String photoUri = "";
        public String ocrText = "";
        public double profileMaxVoltage = 0.0;
        public double profileMaxCurrent = 0.0;
        public double cumulativeSourceWh = 0.0;
        public double lastValidationDelta = 0.0;

        public double capacityWh() {
            if (capacityMah <= 0 || nominalV <= 0) return 0.0;
            return capacityMah / 1000.0 * nominalV;
        }

        public JSONObject toJson() {
            JSONObject o = new JSONObject();
            try {
                o.put("id", id);
                o.put("name", name);
                o.put("type", type);
                o.put("capacityMah", capacityMah);
                o.put("nominalV", nominalV);
                o.put("estimatedSoc", estimatedSoc);
                o.put("lastValidatedSoc", lastValidatedSoc);
                o.put("lastValidationAt", lastValidationAt);
                o.put("efficiency", efficiency);
                o.put("passthrough", passthrough);
                o.put("note", note);
                o.put("photoUri", photoUri);
                o.put("ocrText", ocrText);
                o.put("profileMaxVoltage", profileMaxVoltage);
                o.put("profileMaxCurrent", profileMaxCurrent);
                o.put("cumulativeSourceWh", cumulativeSourceWh);
                o.put("lastValidationDelta", lastValidationDelta);
            } catch (Exception ignored) { }
            return o;
        }

        public static Profile fromJson(JSONObject o) {
            Profile p = new Profile();
            p.id = o.optString("id", p.id);
            p.name = o.optString("name", p.name);
            p.type = o.optString("type", p.type);
            p.capacityMah = o.optDouble("capacityMah", p.capacityMah);
            p.nominalV = o.optDouble("nominalV", p.nominalV);
            p.estimatedSoc = o.optDouble("estimatedSoc", p.estimatedSoc);
            p.lastValidatedSoc = o.optDouble("lastValidatedSoc", p.lastValidatedSoc);
            p.lastValidationAt = o.optLong("lastValidationAt", p.lastValidationAt);
            p.efficiency = o.optDouble("efficiency", p.efficiency);
            p.passthrough = o.optBoolean("passthrough", p.passthrough);
            p.note = o.optString("note", p.note);
            p.photoUri = o.optString("photoUri", p.photoUri);
            p.ocrText = o.optString("ocrText", p.ocrText);
            p.profileMaxVoltage = o.optDouble("profileMaxVoltage", p.profileMaxVoltage);
            p.profileMaxCurrent = o.optDouble("profileMaxCurrent", p.profileMaxCurrent);
            p.cumulativeSourceWh = o.optDouble("cumulativeSourceWh", p.cumulativeSourceWh);
            p.lastValidationDelta = o.optDouble("lastValidationDelta", p.lastValidationDelta);
            return p;
        }
    }

    private final SharedPreferences prefs;

    public ProfileStore(Context c) {
        prefs = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (load().isEmpty()) {
            Profile p = new Profile();
            p.name = "Powerbank 10.000 mAh";
            saveProfile(p);
            setActiveId(p.id);
        }
    }

    public synchronized List<Profile> load() {
        ArrayList<Profile> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString(KEY_PROFILES, "[]"));
            for (int i = 0; i < a.length(); i++) list.add(Profile.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) { }
        return list;
    }

    public synchronized void saveAll(List<Profile> profiles) {
        JSONArray a = new JSONArray();
        for (Profile p : profiles) a.put(p.toJson());
        prefs.edit().putString(KEY_PROFILES, a.toString()).apply();
    }

    public synchronized void saveProfile(Profile profile) {
        List<Profile> list = load();
        boolean replaced = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(profile.id)) {
                list.set(i, profile);
                replaced = true;
                break;
            }
        }
        if (!replaced) list.add(profile);
        saveAll(list);
    }

    public synchronized void deleteProfile(String id) {
        List<Profile> list = load();
        ArrayList<Profile> next = new ArrayList<>();
        for (Profile p : list) if (!p.id.equals(id)) next.add(p);
        saveAll(next);
        if (id.equals(getActiveId())) setActiveId(next.isEmpty() ? "" : next.get(0).id);
    }

    public synchronized Profile getById(String id) {
        for (Profile p : load()) if (p.id.equals(id)) return p;
        return null;
    }

    public synchronized String getActiveId() {
        return prefs.getString(KEY_ACTIVE, "");
    }

    public synchronized void setActiveId(String id) {
        prefs.edit().putString(KEY_ACTIVE, id == null ? "" : id).apply();
    }

    public synchronized Profile getActive() {
        String id = getActiveId();
        Profile p = getById(id);
        if (p != null) return p;
        List<Profile> list = load();
        return list.isEmpty() ? null : list.get(0);
    }

    public double getPhoneCapacityMah() {
        return Double.longBitsToDouble(prefs.getLong(KEY_PHONE_MAH, Double.doubleToLongBits(5000.0)));
    }

    public void setPhoneCapacityMah(double v) {
        prefs.edit().putLong(KEY_PHONE_MAH, Double.doubleToLongBits(v)).apply();
    }

    public double getPhoneNominalV() {
        return Double.longBitsToDouble(prefs.getLong(KEY_PHONE_V, Double.doubleToLongBits(3.85)));
    }

    public void setPhoneNominalV(double v) {
        prefs.edit().putLong(KEY_PHONE_V, Double.doubleToLongBits(v)).apply();
    }
}
