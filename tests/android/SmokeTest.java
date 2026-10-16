package cn.macmanual;
import android.app.*;import android.widget.ArrayAdapter;import android.content.*;import android.os.*;import java.io.*;import java.nio.charset.StandardCharsets;
public class SmokeTest extends Instrumentation {
 MainActivity a;String real="";boolean hadRoot=false;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void stage(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(1,b);}
 void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 void idle()throws Exception{for(int i=0;i<100;i++){final boolean[] busy={false};runOnMainSync(()->busy[0]=a.busy);if(!busy[0])return;Thread.sleep(400);}throw new AssertionError("busy timeout");}
 public void onStart(){Bundle result=new Bundle();try{
  stage("launch");ActivityMonitor monitor=addMonitor("cn.macmanual.MainActivity",null,false);getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).setClassName("cn.macmanual","cn.macmanual.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));a=(MainActivity)waitForMonitorWithTimeout(monitor,15000);check(a!=null,"activity launch timeout");Thread.sleep(500);stage("launched");idle();stage("idle");hadRoot=a.root;if(hadRoot)real=a.snapshot();
  stage("fixtures");final String fixture="ROOT|yes\nBOOT|test-boot\nCFG|wifi_enabled|1\nCFG|wifi_iface|wlan0\nCFG|wifi_mac|02:11:22:33:44:55\nCFG|hook_boot|test-boot\nIFACE|WIFI|wlan0|00:11:22:33:44:55|8|/sys/devices/wifi|up|0x1003|qca|\nIFACE|USB|eth0|02:aa:bb:cc:dd:ee|9|/sys/devices/usb1/1-1|up|0x1003|r8152|0bda:8156 RTL8156\n";
  runOnMainSync(()->{a.parse(fixture);check(a.ifaces.size()==2,"Wi-Fi and USB parsed");check(a.hookLoaded(),"current boot hook detection");check(a.currentText.getText().toString().contains("wlan0"),"interface view");a.address.setText("02:11:22:33:44:55");a.parse(fixture);});
  Thread.sleep(400);runOnMainSync(()->{check(a.address.getText().toString().equals("02:11:22:33:44:55"),"refresh erased manual entry");a.selector.setSelection(1);});
  Thread.sleep(400);runOnMainSync(()->{check(a.selected().name.equals("eth0"),"USB selection");check(a.currentText.getText().toString().contains("RTL8156"),"USB label");check(a.address.getText().toString().equals("02:aa:bb:cc:dd:ee"),"selected address");a.parse(fixture.replace("CFG|hook_boot|test-boot","CFG|hook_boot|old-boot"));check(!a.hookLoaded(),"stale hook detection");});
  stage("navigation and persistent address book");
  final android.content.SharedPreferences testPrefs=a.getSharedPreferences("addressbook_instrumentation",0);testPrefs.edit().clear().commit();
  AddressBook book=new AddressBook(testPrefs);book.save(null,"家庭 Wi-Fi","02-11-22-33-44-55","WIFI","wlan0");String id=book.all().get(0).id;
  check(new AddressBook(testPrefs).all().get(0).mac.equals("02:11:22:33:44:55"),"address survives storage reload and normalizes");
  book.save(id,"已修改","02:22:33:44:55:66","USB","eth0");check(book.all().size()==1&&book.all().get(0).kind.equals("USB"),"edit keeps stable ID");
  boolean rejected=false;try{book.save(null,"invalid","ff:ff:ff:ff:ff:ff","WIFI","");}catch(IllegalArgumentException e){rejected=true;}check(rejected&&book.all().size()==1,"invalid entry must not persist");
  book.delete(id);check(new AddressBook(testPrefs).all().isEmpty(),"delete persists");testPrefs.edit().clear().commit();
  runOnMainSync(()->{check(a.pageViews.size()==4&&a.navButtons.size()==4,"four navigation pages");for(int i=0;i<4;i++){a.navButtons.get(i).performClick();for(int j=0;j<4;j++)check(a.pageViews.get(j).getVisibility()==(i==j?android.view.View.VISIBLE:android.view.View.GONE),"only selected page is visible");}a.showPage(0);});
  stage("shell guard");MainActivity.Result invalid=a.process(new String[]{"/system/bin/sh",a.getFilesDir()+"/action.sh","apply","../../invalid","02:11:22:33:44:55","0","/","00:11:22:33:44:55"},10);
  check(invalid.code==10,"unprivileged app must reject root operation");
  String report="PASS: four navigation pages, saved address persistence/edit/delete/validation, interface parsing, Wi-Fi/USB selection, manual entry retained across refresh, hook boot freshness, ordinary-shell Root guard.\n";
  if(hadRoot){MainActivity.Result guard=a.rootCommand(a.script("action.sh","apply","../../invalid","02:11:22:33:44:55","0","/","00:11:22:33:44:55"),10);check(guard.code==13,"root path guard");check(real.contains("IFACE|WIFI|wlan0|"),"actual Wi-Fi sysfs");report+="PASS: Root uid and actual Wi-Fi scan; root-shell path guard.\n"+real;}
  else report+="Root not authorized: actual root changes and active Xposed hook not tested.\n";
  File f=new File(getTargetContext().getExternalFilesDir(null),"device-test.txt");try(FileOutputStream o=new FileOutputStream(f)){o.write(report.getBytes(StandardCharsets.UTF_8));}
  final String restore=real;runOnMainSync(()->{a.ifaces.clear();a.config.clear();a.boot="";a.displayedId="";a.selector.setAdapter(new ArrayAdapter<String>(a,android.R.layout.simple_spinner_dropdown_item,new String[]{"等待读取 Wi-Fi / USB 接口"}));if(hadRoot)a.parse(restore);else{a.currentText.setText("授权 Root 后读取真实 Wi-Fi 与 USB 网络接口。");a.address.setText("");a.updateStatus();}a.event("实机界面与保护测试通过；未修改任何真实网卡 MAC");});
  result.putString("stream",report.split("\nIFACE")[0]);finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("stream","FAIL: "+t);finish(Activity.RESULT_CANCELED,result);}}
}
