package cn.macmanual;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.nio.charset.StandardCharsets;
public class RootCheck extends Instrumentation {
 MainActivity a;
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean b,String s){if(!b)throw new AssertionError(s);}
 void idle()throws Exception{for(int i=0;i<150;i++){final boolean[] b={false};runOnMainSync(()->b[0]=a.busy);if(!b[0])return;Thread.sleep(400);}throw new AssertionError("timeout");}
 void stage(String s){Bundle b=new Bundle();b.putString("stream",s+"\n");sendStatus(1,b);}
 public void onStart(){Bundle result=new Bundle();try{
  stage("Checking newly granted Root");ActivityMonitor monitor=addMonitor("cn.macmanual.MainActivity",null,false);getTargetContext().startActivity(new Intent(Intent.ACTION_MAIN).setClassName("cn.macmanual","cn.macmanual.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));a=(MainActivity)waitForMonitorWithTimeout(monitor,15000);check(a!=null,"activity launch timeout");Thread.sleep(500);idle();runOnMainSync(()->a.checkRoot());idle();check(a.root,"Root authorization did not become available to cn.macmanual");
  MainActivity.Result uid=a.rootCommand("id -u",10);check(uid.code==0&&uid.text.trim().equals("0"),"uid check");
  String real=a.snapshot();check(real.contains("IFACE|WIFI|wlan0|"),"No real wlan0 found");
  MainActivity.Result guard=a.rootCommand(a.script("action.sh","apply","../../invalid","02:11:22:33:44:55","0","/","00:11:22:33:44:55"),10);check(guard.code==13,"Invalid interface must be rejected before modification");
  MainActivity.Result framework=a.rootCommand("for f in /data/adb/modules/*/module.prop; do [ -f \"$f\" ] || continue; if grep -qiE 'lsposed|lsplant|xposed' \"$f\"; then cat \"$f\"; fi; done",10);
  String report="PASS: Root uid=0, real wlan0 scan, invalid-interface rejection. No actual MAC changed.\n"+real+"\nFramework:\n"+framework.text+"\nPrior app log:\n"+a.read(new File(a.getFilesDir(),"session.txt"));
  File f=new File(getTargetContext().getExternalFilesDir(null),"root-check.txt");try(FileOutputStream out=new FileOutputStream(f)){out.write(report.getBytes(StandardCharsets.UTF_8));}
  runOnMainSync(()->{a.parse(real);a.event("Root 实机验证通过：uid=0 / 真实 wlan0 / 非法接口拒绝");});
  result.putString("stream",report);finish(Activity.RESULT_OK,result);
 }catch(Throwable t){result.putString("stream","FAIL: "+t);finish(Activity.RESULT_CANCELED,result);}}
}
