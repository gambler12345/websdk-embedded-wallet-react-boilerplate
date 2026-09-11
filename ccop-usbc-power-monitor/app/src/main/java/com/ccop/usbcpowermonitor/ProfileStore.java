package com.ccop.usbcpowermonitor;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

/** Local-first inventory. No demo profiles or demo SOC values are created. */
public class ProfileStore {
    private static final String PREFS = "ccop_energy_profiles_v10_live";
    private static final String KEY_PROFILES = "profiles";
    private static final String KEY_ACTIVE = "active_profile";
    private static final String KEY_ACTIVE_CABLE = "active_cable";
    private static final String KEY_PHONE_MAH = "phone_capacity_mah";
    private static final String KEY_PHONE_V = "phone_nominal_v";

    public static class Profile {
        public String id = UUID.randomUUID().toString();
        public String name = "Neues Gerät";
        public String type = "POWERBANK"; // POWERBANK / EXTERNAL_BATTERY / CHARGER / CABLE
        public double capacityMah = 0.0;
        public double nominalV = 0.0;
        public double estimatedSoc = Double.NaN;
        public boolean socKnown = false;
        public String socOrigin = "UNBEKANNT"; // DIRECT / MANUAL / ESTIMATED
        public double lastValidatedSoc = Double.NaN;
        public long lastValidationAt = 0L;
        public double efficiency = 0.88;
        public boolean passthrough = false;
        public boolean archived = false;
        public String note = "";
        public ArrayList<String> photoUris = new ArrayList<>();
        public String ocrText = "";
        public double profileMaxVoltage = 0.0;
        public double profileMaxCurrent = 0.0;
        public double cumulativeSourceWh = 0.0;
        public long createdAt = System.currentTimeMillis();
        public long lastSeenAt = 0L;
        public String deviceHash = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        public String connectionSignature = "";
        public String lastPlugType = "";
        public boolean autoConfigured = false;
        // Cable inventory / quality
        public double cableMaxW = 0.0;
        public double cableLengthM = 0.0;
        public boolean cableEMarked = false;
        public double cableQuality = Double.NaN;
        public double cableVoltageDrop = Double.NaN;
        public double cableResistanceOhm = Double.NaN;

        public double capacityWh(){ return capacityMah>0 && nominalV>0 ? capacityMah/1000.0*nominalV : 0.0; }
        public boolean isEnergyStore(){ return "POWERBANK".equalsIgnoreCase(type)||"EXTERNAL_BATTERY".equalsIgnoreCase(type); }
        public boolean isCable(){ return "CABLE".equalsIgnoreCase(type); }

        public JSONObject toJson(){
            JSONObject o=new JSONObject();
            try{
                o.put("id",id);o.put("name",name);o.put("type",type);o.put("capacityMah",capacityMah);o.put("nominalV",nominalV);
                if(!Double.isNaN(estimatedSoc))o.put("estimatedSoc",estimatedSoc);o.put("socKnown",socKnown);o.put("socOrigin",socOrigin);
                if(!Double.isNaN(lastValidatedSoc))o.put("lastValidatedSoc",lastValidatedSoc);o.put("lastValidationAt",lastValidationAt);
                o.put("efficiency",efficiency);o.put("passthrough",passthrough);o.put("archived",archived);o.put("note",note);
                JSONArray ph=new JSONArray();for(String u:photoUris)ph.put(u);o.put("photoUris",ph);o.put("ocrText",ocrText);
                o.put("profileMaxVoltage",profileMaxVoltage);o.put("profileMaxCurrent",profileMaxCurrent);o.put("cumulativeSourceWh",cumulativeSourceWh);
                o.put("createdAt",createdAt);o.put("lastSeenAt",lastSeenAt);o.put("deviceHash",deviceHash);o.put("connectionSignature",connectionSignature);
                o.put("lastPlugType",lastPlugType);o.put("autoConfigured",autoConfigured);o.put("cableMaxW",cableMaxW);o.put("cableLengthM",cableLengthM);
                o.put("cableEMarked",cableEMarked);if(!Double.isNaN(cableQuality))o.put("cableQuality",cableQuality);
                if(!Double.isNaN(cableVoltageDrop))o.put("cableVoltageDrop",cableVoltageDrop);if(!Double.isNaN(cableResistanceOhm))o.put("cableResistanceOhm",cableResistanceOhm);
            }catch(Exception ignored){}
            return o;
        }
        public static Profile fromJson(JSONObject o){
            Profile p=new Profile();p.id=o.optString("id",p.id);p.name=o.optString("name",p.name);p.type=o.optString("type",p.type);
            p.capacityMah=o.optDouble("capacityMah",0);p.nominalV=o.optDouble("nominalV",0);p.estimatedSoc=o.has("estimatedSoc")?o.optDouble("estimatedSoc",Double.NaN):Double.NaN;
            p.socKnown=o.optBoolean("socKnown",!Double.isNaN(p.estimatedSoc));p.socOrigin=o.optString("socOrigin",p.socKnown?"MANUAL":"UNBEKANNT");
            p.lastValidatedSoc=o.has("lastValidatedSoc")?o.optDouble("lastValidatedSoc",Double.NaN):Double.NaN;p.lastValidationAt=o.optLong("lastValidationAt",0);
            p.efficiency=o.optDouble("efficiency",.88);p.passthrough=o.optBoolean("passthrough",false);p.archived=o.optBoolean("archived",false);p.note=o.optString("note","");
            p.photoUris.clear();JSONArray a=o.optJSONArray("photoUris");if(a!=null)for(int i=0;i<a.length();i++)p.photoUris.add(a.optString(i));p.ocrText=o.optString("ocrText","");
            p.profileMaxVoltage=o.optDouble("profileMaxVoltage",0);p.profileMaxCurrent=o.optDouble("profileMaxCurrent",0);p.cumulativeSourceWh=o.optDouble("cumulativeSourceWh",0);
            p.createdAt=o.optLong("createdAt",System.currentTimeMillis());p.lastSeenAt=o.optLong("lastSeenAt",0);p.deviceHash=o.optString("deviceHash",p.deviceHash);
            p.connectionSignature=o.optString("connectionSignature","");p.lastPlugType=o.optString("lastPlugType","");p.autoConfigured=o.optBoolean("autoConfigured",false);
            p.cableMaxW=o.optDouble("cableMaxW",0);p.cableLengthM=o.optDouble("cableLengthM",0);p.cableEMarked=o.optBoolean("cableEMarked",false);
            p.cableQuality=o.has("cableQuality")?o.optDouble("cableQuality",Double.NaN):Double.NaN;p.cableVoltageDrop=o.has("cableVoltageDrop")?o.optDouble("cableVoltageDrop",Double.NaN):Double.NaN;
            p.cableResistanceOhm=o.has("cableResistanceOhm")?o.optDouble("cableResistanceOhm",Double.NaN):Double.NaN;return p;
        }
    }

    private final SharedPreferences prefs;
    public ProfileStore(Context c){prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public synchronized List<Profile> load(){ArrayList<Profile> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString(KEY_PROFILES,"[]"));for(int i=0;i<a.length();i++)out.add(Profile.fromJson(a.getJSONObject(i)));}catch(Exception ignored){}return out;}
    public synchronized List<Profile> loadActiveInventory(){ArrayList<Profile> out=new ArrayList<>();for(Profile p:load())if(!p.archived)out.add(p);return out;}
    public synchronized void saveAll(List<Profile> list){JSONArray a=new JSONArray();for(Profile p:list)a.put(p.toJson());prefs.edit().putString(KEY_PROFILES,a.toString()).apply();}
    public synchronized void saveProfile(Profile p){List<Profile> list=load();boolean found=false;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(p.id)){list.set(i,p);found=true;break;}if(!found)list.add(p);saveAll(list);}
    public synchronized void archiveProfile(String id,boolean archived){Profile p=getById(id);if(p!=null){p.archived=archived;saveProfile(p);}if(archived&&id.equals(getActiveId()))setActiveId("");if(archived&&id.equals(getActiveCableId()))setActiveCableId("");}
    public synchronized Profile getById(String id){for(Profile p:load())if(p.id.equals(id))return p;return null;}
    public synchronized Profile getActive(){Profile p=getById(getActiveId());return p!=null&&!p.archived?p:null;}
    public synchronized String getActiveId(){return prefs.getString(KEY_ACTIVE,"");}public synchronized void setActiveId(String id){prefs.edit().putString(KEY_ACTIVE,id==null?"":id).apply();}
    public synchronized String getActiveCableId(){return prefs.getString(KEY_ACTIVE_CABLE,"");}public synchronized void setActiveCableId(String id){prefs.edit().putString(KEY_ACTIVE_CABLE,id==null?"":id).apply();}
    public synchronized Profile getActiveCable(){Profile p=getById(getActiveCableId());return p!=null&&!p.archived&&p.isCable()?p:null;}
    public synchronized List<Profile> matchingSignature(String sig){ArrayList<Profile> out=new ArrayList<>();if(sig==null||sig.isEmpty())return out;for(Profile p:loadActiveInventory())if(sig.equals(p.connectionSignature))out.add(p);return out;}
    public double getPhoneCapacityMah(){return Double.longBitsToDouble(prefs.getLong(KEY_PHONE_MAH,Double.doubleToLongBits(5000.0)));}
    public void setPhoneCapacityMah(double v){prefs.edit().putLong(KEY_PHONE_MAH,Double.doubleToLongBits(v)).apply();}
    public double getPhoneNominalV(){return Double.longBitsToDouble(prefs.getLong(KEY_PHONE_V,Double.doubleToLongBits(3.87)));}
    public void setPhoneNominalV(double v){prefs.edit().putLong(KEY_PHONE_V,Double.doubleToLongBits(v)).apply();}
}
