package cn.macmanual;
import android.app.*;import android.content.*;import android.os.*;import android.graphics.Bitmap;import java.io.*;import java.nio.charset.StandardCharsets;
/** Explicit real-device regression: caller supplies both addresses, and final state is restored. */
public class ApplyCheck extends Instrumentation {
 MainActivity a;String target;StringBuilder log=new StringBuilder();
 public void onCreate(Bundle b){super.onCreate(b);target=b.getString("test_mac");start();}
 void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 void idle()throws Exception{for(int i=0;i<150;i++){final boolean[] b={false};runOnMainSync(()->b[0]=a.busy);if(!b[0])return;Thread.sleep(300);}throw new AssertionError("timeout");}
 void stage(String s){log.append(s).append('\n');Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(1,b);}
 MainActivity.Result action(String op,MainActivity.Iface n,String mac)throws Exception{return a.rootCommand(a.script("action.sh",op,n.name,mac,n.index,n.path,n.mac),35);}
 MainActivity.Iface current()throws Exception{String real=a.snapshot();runOnMainSync(()->a.parse(real));return a.selected();}
 public void onStart(){Bundle result=new Bundle();int resultCode=0;MainActivity.Iface original=null;boolean changed=false;try{
  ActivityMonitor monitor=addMonitor("cn.macmanual.MainActivity",null,false);getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).setClassName("cn.macmanual","cn.macmanual.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));a=(MainActivity)waitForMonitorWithTimeout(monitor,15000);check(a!=null,"launch");Thread.sleep(500);idle();original=current();
  check(original!=null&&original.kind.equals("WIFI")&&a.hookLoaded(),"requires active Wi-Fi hook");check("1".equals(a.config.get("wifi_enabled"))&&original.mac.equals(a.config.get("wifi_mac")),"requires existing fixed profile matching actual MAC to restore");String mac=MacRules.normalize(target);check(!mac.equals(original.mac),"test target must differ");
  stage("Verify combined operation with fixed mode initially OFF");changed=true;MainActivity.Result off=a.rootCommand(a.script("action.sh","profile-off"),10);check(off.code==0,off.text);MainActivity.Result apply=action("smart-apply",current(),mac);stage(apply.text);check(apply.code==0,"apply failed "+apply.code);Thread.sleep(4000);MainActivity.Iface after=current();check(after.mac.equals(mac)&&"1".equals(a.config.get("wifi_enabled"))&&mac.equals(a.config.get("wifi_mac")),"target not retained after four seconds");
  stage("Restore the original user-selected address through combined operation");MainActivity.Result restore=action("smart-apply",after,original.mac);stage(restore.text);check(restore.code==0,"restore failed");Thread.sleep(4000);check(current().mac.equals(original.mac),"restored MAC mismatch");changed=false;
  for(int i=0;i<4;i++){final int page=i;runOnMainSync(()->a.showPage(page));Thread.sleep(400);Bitmap bmp=getUiAutomation().takeScreenshot();if(bmp!=null){try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"page-"+i+".png"))){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}bmp.recycle();}}
  runOnMainSync(()->{a.showPage(0);a.event("v1.1 实机验证通过：固定模式关闭时一键修改成功，已恢复测试前地址");});stage("PASS: actual Wi-Fi MAC change, automatic profile enable/update, delayed readback, original address restored, four page captures.");result.putString("stream",log.toString());resultCode=-1;
 }catch(Throwable t){stage("FAIL: "+t);if(changed&&original!=null)try{stage("Recovery: "+action("smart-apply",current(),original.mac).text);}catch(Throwable recovery){stage("Recovery failed: "+recovery);}result.putString("stream",log.toString());resultCode=0;
 }finally{try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"apply-check.txt"))){out.write(log.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}finish(resultCode,result);}
}
