import cn.macmanual.MacRules;
public class MacRulesTest {
 static int count=0;
 static void check(boolean b){count++;if(!b)throw new AssertionError("test "+count);}
 static void invalid(String s){try{MacRules.normalize(s);throw new AssertionError(s);}catch(IllegalArgumentException expected){count++;}}
 public static void main(String[] a){
  check(MacRules.normalize(" 02:AB:11:22:33:44 ").equals("02:ab:11:22:33:44"));
  check(MacRules.normalize("02ab11223344").equals("02:ab:11:22:33:44"));
  check(MacRules.normalize("02-ab-11-22-33-44").equals("02:ab:11:22:33:44"));
  invalid(null);invalid("");invalid("00:00:00:00:00:00");invalid("ff:ff:ff:ff:ff:ff");invalid("01:11:22:33:44:55");invalid("02:11:22:33:44:zz");invalid("02:11:22:33:44:55;reboot");invalid("02:11-22:33:44:55");invalid("02:11:22:33:44");
  check(MacRules.local("02:11:22:33:44:55"));check(!MacRules.local("00:11:22:33:44:55"));
  check(MacRules.selected(false,"wlan0","wlan0","02:11:22:33:44:55")==null);
  check(MacRules.selected(true,"wlan0","wlan1","02:11:22:33:44:55")==null);
  check(MacRules.selected(true,"wlan0","wlan0","invalid")==null);
  check(MacRules.selected(true,"wlan0","wlan0","02:11:22:33:44:55").equals("02:11:22:33:44:55"));
  for(int i=0;i<100;i++){String s=MacRules.random();check(MacRules.normalize(s).equals(s));check(MacRules.local(s));}
  System.out.println("PASS "+count+" MAC validation / selection assertions");
 }
}
