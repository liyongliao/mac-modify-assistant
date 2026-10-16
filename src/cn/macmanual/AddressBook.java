package cn.macmanual;
import android.content.SharedPreferences;
import org.json.*;
import java.util.*;
/** Private, persistent named addresses. Invalid entries never reach root commands. */
final class AddressBook {
 static class Entry {
  final String id,name,mac,kind,iface;
  Entry(String id,String name,String mac,String kind,String iface){this.id=id;this.name=name;this.mac=MacRules.normalize(mac);this.kind=kind;this.iface=iface;}
 }
 final SharedPreferences prefs;
 AddressBook(SharedPreferences p){prefs=p;}
 ArrayList<Entry> all(){ArrayList<Entry> result=new ArrayList<>();try{JSONArray a=new JSONArray(prefs.getString("addresses_v1","[]"));for(int i=0;i<a.length();i++)try{JSONObject o=a.getJSONObject(i);String kind=o.getString("kind");if(!kind.equals("WIFI")&&!kind.equals("USB"))continue;result.add(new Entry(o.getString("id"),o.getString("name"),o.getString("mac"),kind,o.optString("iface")));}catch(Exception ignored){}}catch(Exception ignored){}return result;}
 void save(String id,String name,String mac,String kind,String iface){
  name=name.trim();if(name.isEmpty()||name.length()>40)throw new IllegalArgumentException("名称请输入 1–40 个字符");
  if(!kind.equals("WIFI")&&!kind.equals("USB"))throw new IllegalArgumentException("请选择 Wi-Fi 或 USB");
  Entry entry=new Entry(id==null?UUID.randomUUID().toString():id,name,mac,kind,iface);
  ArrayList<Entry> entries=all();boolean found=false;for(int i=0;i<entries.size();i++)if(entries.get(i).id.equals(entry.id)){entries.set(i,entry);found=true;break;}if(!found)entries.add(entry);write(entries);
 }
 void delete(String id){ArrayList<Entry> e=all();e.removeIf(x->x.id.equals(id));write(e);}
 void write(ArrayList<Entry> entries){JSONArray a=new JSONArray();try{for(Entry e:entries){JSONObject o=new JSONObject();o.put("id",e.id);o.put("name",e.name);o.put("mac",e.mac);o.put("kind",e.kind);o.put("iface",e.iface);a.put(o);}}catch(JSONException ex){throw new IllegalStateException(ex);}if(!prefs.edit().putString("addresses_v1",a.toString()).commit())throw new IllegalStateException("地址库保存失败，请检查存储空间");}
}
