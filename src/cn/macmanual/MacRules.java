package cn.macmanual;
import java.util.Locale;
import java.security.SecureRandom;
/** Validates actual Ethernet addresses, not display-only spoof values. */
public final class MacRules {
 public static String normalize(String raw) {
  if(raw==null)throw new IllegalArgumentException("请输入 MAC 地址");
  String s=raw.trim().toLowerCase(Locale.ROOT);
  if(s.matches("[0-9a-f]{12}"))s=s.replaceAll("(..)(?!$)","$1:");
  else if(s.matches("[0-9a-f]{2}(-[0-9a-f]{2}){5}"))s=s.replace('-',':');
  if(!s.matches("[0-9a-f]{2}(:[0-9a-f]{2}){5}"))throw new IllegalArgumentException("格式应为 02:11:22:33:44:55");
  if(s.equals("00:00:00:00:00:00"))throw new IllegalArgumentException("不能使用全零地址");
  if((Integer.parseInt(s.substring(0,2),16)&1)!=0)throw new IllegalArgumentException("不能使用组播或广播地址（首字节必须为偶数）");
  return s;
 }
 public static boolean local(String s){return (Integer.parseInt(normalize(s).substring(0,2),16)&2)!=0;}
 public static String random(){byte[] b=new byte[6];new SecureRandom().nextBytes(b);b[0]=(byte)((b[0]&0xfc)|2);StringBuilder s=new StringBuilder();for(byte v:b){if(s.length()>0)s.append(':');s.append(String.format(Locale.ROOT,"%02x",v&255));}return s.toString();}
 public static String selected(boolean enabled,String targetIface,String actualIface,String mac){if(!enabled || targetIface==null || !targetIface.equals(actualIface))return null;try{return normalize(mac);}catch(IllegalArgumentException e){return null;}}
}
