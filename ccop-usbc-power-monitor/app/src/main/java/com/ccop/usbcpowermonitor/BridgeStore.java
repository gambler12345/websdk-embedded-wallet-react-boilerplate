package com.ccop.usbcpowermonitor;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.*;

public class BridgeStore {
    private static final String PREFS="ccop_bridges_v9";
    private final SharedPreferences prefs;
    public BridgeStore(Context c){prefs=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public static class Bridge {
        public String id=UUID.randomUUID().toString();
        public String name="Neue Bridge";
        public String batteryAId="",batteryBId="",cableId="",sourceId="";
        public String mode="POOL";
        public boolean active=false,archived=false;
        public long updatedAt=System.currentTimeMillis();
        public JSONObject toJson(){JSONObject o=new JSONObject();try{o.put("id",id);o.put("name",name);o.put("batteryAId",batteryAId);o.put("batteryBId",batteryBId);o.put("cableId",cableId);o.put("sourceId",sourceId);o.put("mode",mode);o.put("active",active);o.put("archived",archived);o.put("updatedAt",updatedAt);}catch(Exception ignored){}return o;}
        public static Bridge fromJson(JSONObject o){Bridge b=new Bridge();b.id=o.optString("id",b.id);b.name=o.optString("name",b.name);b.batteryAId=o.optString("batteryAId","");b.batteryBId=o.optString("batteryBId","");b.cableId=o.optString("cableId","");b.sourceId=o.optString("sourceId","");b.mode=o.optString("mode","POOL");b.active=o.optBoolean("active",false);b.archived=o.optBoolean("archived",false);b.updatedAt=o.optLong("updatedAt",System.currentTimeMillis());return b;}
    }
    public synchronized List<Bridge> load(){ArrayList<Bridge> out=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("bridges","[]"));for(int i=0;i<a.length();i++)out.add(Bridge.fromJson(a.getJSONObject(i)));}catch(Exception ignored){}return out;}
    public synchronized void save(Bridge b){List<Bridge> list=load();boolean r=false;for(int i=0;i<list.size();i++)if(list.get(i).id.equals(b.id)){list.set(i,b);r=true;break;}if(!r)list.add(b);JSONArray a=new JSONArray();for(Bridge x:list)a.put(x.toJson());prefs.edit().putString("bridges",a.toString()).apply();}
    public synchronized void activate(String id){List<Bridge> list=load();JSONArray a=new JSONArray();for(Bridge b:list){b.active=b.id.equals(id)&&!b.archived;if(b.active)b.updatedAt=System.currentTimeMillis();a.put(b.toJson());}prefs.edit().putString("bridges",a.toString()).apply();}
    public synchronized Bridge active(){for(Bridge b:load())if(b.active&&!b.archived)return b;return null;}
    public synchronized void archive(String id){for(Bridge b:load())if(b.id.equals(id)){b.archived=true;b.active=false;save(b);break;}}
}
