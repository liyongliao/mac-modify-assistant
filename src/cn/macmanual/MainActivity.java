package cn.macmanual;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 static final int BG=0xff0b1420,CARD=0xff152333,INK=0xffedf4f8,MUTED=0xff9aafc3,GREEN=0xff6fe3c4,AMBER=0xffffca7c;
 final Handler main=new Handler(Looper.getMainLooper());
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 final ArrayList<Iface> ifaces=new ArrayList<>(); final ArrayList<Button> buttons=new ArrayList<>();
 final HashMap<String,String> config=new HashMap<>();
 Spinner selector; EditText address; TextView rootText,hookText,currentText,profileText,logs; LinearLayout body;
 AddressBook book; LinearLayout savedBody; FrameLayout pages; final ArrayList<View> pageViews=new ArrayList<>(); final ArrayList<NavItem> navButtons=new ArrayList<>(); int activePage=0; TextView activityStatus;
 boolean root=false,busy=false,dead=false,binding=false;String su="su",boot="",lastSnapshot="",report="",displayedId="";
 final StringBuilder history=new StringBuilder();
 static class Iface {
  String kind,name,mac,index,path,state,flags,driver,label;
  Iface(String[] p){kind=p[1];name=p[2];mac=p[3];index=p[4];path=p[5];state=p[6];flags=p[7];driver=p[8];label=p.length>9?p[9]:"";}
  String id(){return index+"|"+path;} String title(){return (kind.equals("WIFI")?"Wi-Fi":"USB 网卡")+" · "+name;}
 }
 static class Result {int code;String text;Result(int c,String t){code=c;text=t;}}
 interface Job {String run()throws Exception;}
 @Override public void onCreate(Bundle state){super.onCreate(state);root=getPreferences(0).getBoolean("root",false);su=getPreferences(0).getString("su","su");try{copyAsset("scan.sh");copyAsset("action.sh");}catch(Exception e){Toast.makeText(this,e.toString(),1).show();}book=new AddressBook(getPreferences(0));build();event("启动 · Android "+Build.VERSION.RELEASE);if(root)refresh();}
 @Override public void onDestroy(){dead=true;worker.shutdown();super.onDestroy();}
 int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 GradientDrawable bg(int c,int r){GradientDrawable g=new GradientDrawable();g.setColor(c);g.setCornerRadius(dp(r));return g;}
 TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
 void gap(LinearLayout p,int h){p.addView(new View(this),new LinearLayout.LayoutParams(1,dp(h)));}
 LinearLayout card(String title){LinearLayout p=new LinearLayout(this);p.setOrientation(1);p.setPadding(dp(18),dp(18),dp(18),dp(18));p.setBackground(bg(CARD,18));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);body.addView(p,lp);TextView t=text(title,13,GREEN);t.setTypeface(null,Typeface.BOLD);p.addView(t);gap(p,12);return p;}
 void button(LinearLayout p,String title,boolean primary,Runnable click){Button b=new Button(this);b.setAllCaps(false);b.setText(title);b.setTextSize(14);b.setTextColor(primary?BG:INK);b.setBackground(bg(primary?GREEN:0xff24384b,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.topMargin=dp(10);p.addView(b,lp);b.setOnClickListener(v->click.run());buttons.add(b);}
 LinearLayout newPage(String title,String subtitle){
  ScrollView sc=new ScrollView(this);sc.setFillViewport(true);LinearLayout p=new LinearLayout(this);p.setOrientation(1);p.setPadding(dp(20),dp(18),dp(20),dp(24));sc.addView(p);pages.addView(sc,new FrameLayout.LayoutParams(-1,-1));pageViews.add(sc);body=p;
  TextView h=text(title,28,INK);h.setTypeface(null,Typeface.BOLD);p.addView(h);gap(p,5);p.addView(text(subtitle,13,MUTED));gap(p,20);return p;
 }
 /** Compact icon-and-label navigation with a selected icon capsule and native ripple. */
 final class NavItem extends LinearLayout {
  final ImageView icon; final TextView label; final FrameLayout indicator;
  NavItem(String name,int resource){super(MainActivity.this);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setClickable(true);setFocusable(true);setContentDescription(name);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
   setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x226fe3c4),null,bg(0xffffffff,20)));
   indicator=new FrameLayout(MainActivity.this);addView(indicator,new LinearLayout.LayoutParams(dp(54),dp(30)));
   icon=new ImageView(MainActivity.this);icon.setImageResource(resource);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams iconParams=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);indicator.addView(icon,iconParams);
   label=text(name,11,MUTED);label.setIncludeFontPadding(false);label.setGravity(Gravity.CENTER);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(-1,-2);textParams.topMargin=dp(5);addView(label,textParams);
  }
  void activate(boolean selected){setSelected(selected);indicator.setBackground(bg(selected?0xff28483f:0x00000000,15));icon.setColorFilter(selected?GREEN:MUTED);label.setTextColor(selected?GREEN:MUTED);label.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);}
  @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.RadioButton");info.setCheckable(true);info.setChecked(isSelected());}
 }
 void showPage(int index){activePage=index;for(int i=0;i<pageViews.size();i++){pageViews.get(i).setVisibility(i==index?View.VISIBLE:View.GONE);navButtons.get(i).activate(i==index);}if(index==1)renderSaved();}
 void build(){
  LinearLayout shell=new LinearLayout(this);shell.setOrientation(1);shell.setBackgroundColor(BG);setContentView(shell);shell.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
  LinearLayout bar=new LinearLayout(this);bar.setPadding(dp(22),dp(10),dp(22),dp(6));bar.setGravity(Gravity.CENTER_VERTICAL);shell.addView(bar);TextView brand=text("MAC修改助手",14,GREEN);brand.setTypeface(null,Typeface.BOLD);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));activityStatus=text("准备就绪",12,MUTED);bar.addView(activityStatus);
  pages=new FrameLayout(this);shell.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
  newPage("修改地址","选择接口，填写地址，一次完成应用与回读验证");
  LinearLayout edit=card("网络接口");selector=new Spinner(this);selector.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"等待读取 Wi-Fi / USB 接口"}));edit.addView(selector,new LinearLayout.LayoutParams(-1,dp(48)));selector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onItemSelected(AdapterView<?> p,View v,int pos,long id){if(!binding){Iface chosen=selected();if(chosen!=null&&!chosen.id().equals(displayedId))showSelected(true);}}public void onNothingSelected(AdapterView<?> p){}});
  currentText=text("授权 Root 后读取真实网络接口。",14,INK);currentText.setTextIsSelectable(true);edit.addView(currentText);button(edit,"刷新接口",false,()->refresh());
  LinearLayout target=card("目标 MAC");address=new EditText(this);address.setTextSize(20);address.setTextColor(INK);address.setHintTextColor(MUTED);address.setSingleLine(true);address.setHint("02:11:22:33:44:55");address.setTypeface(Typeface.MONOSPACE);address.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);target.addView(address,new LinearLayout.LayoutParams(-1,dp(52)));
  button(target,"立即修改",true,()->apply(false));button(target,"保存到地址库",false,()->editSaved(null));button(target,"生成随机地址",false,()->address.setText(MacRules.random()));gap(target,12);target.addView(text("Wi-Fi 自动先更新固定配置，再修改接口。USB 直接修改。操作会短暂断网。",12,MUTED));button(target,"恢复修改前地址",false,()->restore());
  newPage("地址库","保存常用 MAC，点击应用即可使用");button(body,"添加地址",true,()->editSaved(null));gap(body,16);savedBody=new LinearLayout(this);savedBody.setOrientation(1);body.addView(savedBody);
  newPage("模块与设置","Wi-Fi 系统 Hook · Root 权限 · 固定配置");
  LinearLayout state=card("运行状态");rootText=text("Root 尚未验证",14,AMBER);state.addView(rootText);gap(state,12);hookText=text("Wi-Fi Hook：等待检测",14,MUTED);state.addView(hookText);button(state,"申请 / 检查 Root",true,()->checkRoot());button(state,"刷新状态",false,()->refresh());
  LinearLayout setup=card("首次启用 Wi-Fi 模块");setup.addView(text("在 LSPosed 中启用 MAC修改助手，作用域选择“系统框架”。停用旧 MACsposed Fork 后重启手机一次。USB 修改只需要 Root。",14,INK));
  try{getPackageManager().getPackageInfo("com.samg381.macsposedfork",0);gap(setup,10);setup.addView(text("检测到旧 MACsposed Fork，请避免两个模块同时启用。",13,AMBER));}catch(Exception ignored){}
  LinearLayout wifi=card("Wi-Fi 固定配置");profileText=text("尚未读取固定配置",13,INK);profileText.setTextIsSelectable(true);wifi.addView(profileText);gap(wifi,12);wifi.addView(text("“立即修改”和地址库“应用”会自动维护此配置，无需单独保存。关闭后，下次连接交还系统管理。",13,MUTED));button(wifi,"关闭 Wi-Fi 固定模式",false,()->profileOff());button(wifi,"使用说明与来源",false,()->help());
  newPage("操作记录","记录实际结果，便于排查驱动与模块问题");LinearLayout log=card("诊断日志");button(log,"导出诊断记录 TXT",false,()->export());gap(log,14);logs=text("",12,MUTED);logs.setTypeface(Typeface.MONOSPACE);logs.setTextIsSelectable(true);log.addView(logs);body.addView(text("v1.2.0 · 无联网权限 · AGPL-3.0",12,MUTED));
  LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);nav.setPadding(dp(6),dp(7),dp(6),dp(7));GradientDrawable dock=bg(0xff142231,26);dock.setStroke(dp(1),0xff243444);nav.setBackground(dock);nav.setElevation(dp(6));
  LinearLayout.LayoutParams dockParams=new LinearLayout.LayoutParams(-1,dp(76));dockParams.setMargins(dp(18),dp(6),dp(18),dp(10));shell.addView(nav,dockParams);
  String[] names={"修改","地址库","模块","记录"};int[] icons={R.drawable.nav_edit,R.drawable.nav_book,R.drawable.nav_module,R.drawable.nav_log};for(int i=0;i<names.length;i++){final int pos=i;NavItem item=new NavItem(names[i],icons[i]);nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));navButtons.add(item);item.setOnClickListener(v->showPage(pos));}
  updateStatus();showPage(0);
 }
 void renderSaved(){
  // Release detached buttons so task-state updates only address current views.
  for(int i=buttons.size()-1;i>=0;i--){View v=buttons.get(i);while(v.getParent() instanceof View && v.getParent()!=savedBody)v=(View)v.getParent();if(v.getParent()==savedBody)buttons.remove(i);}
  savedBody.removeAllViews();ArrayList<AddressBook.Entry> entries=book.all();if(entries.isEmpty()){TextView empty=text("还没有保存的地址\n\n在“修改”中输入 MAC 后保存，或点击上方“添加地址”。",15,MUTED);empty.setPadding(dp(12),dp(24),dp(12),dp(24));savedBody.addView(empty);return;}
  LinearLayout previous=body;body=savedBody;for(AddressBook.Entry e:entries){LinearLayout c=card(e.name);TextView m=text(e.mac,20,INK);m.setTypeface(Typeface.MONOSPACE);c.addView(m);gap(c,7);c.addView(text((e.kind.equals("WIFI")?"Wi-Fi":"USB 网卡")+(e.iface.isEmpty()?"":" · 首选 "+e.iface),12,MUTED));button(c,"应用",true,()->applySaved(e));button(c,"编辑",false,()->editSaved(e));button(c,"删除",false,()->confirm("删除已保存地址",e.name+"\n仅删除此条收藏。",()->{book.delete(e.id);renderSaved();}));}body=previous;for(Button b:buttons)b.setEnabled(!busy);
 }
 void editSaved(AddressBook.Entry existing){
  if(busy)return;Iface n=selected();LinearLayout form=new LinearLayout(this);form.setOrientation(1);form.setPadding(dp(22),dp(6),dp(22),dp(4));
  EditText name=new EditText(this);name.setHint("名称，例如：家庭 Wi-Fi");name.setSingleLine(true);form.addView(name);
  EditText mac=new EditText(this);mac.setHint("02:11:22:33:44:55");mac.setSingleLine(true);mac.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);form.addView(mac);
  Spinner kind=new Spinner(this);kind.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"Wi-Fi","USB 网卡"}));form.addView(kind);
  name.setText(existing==null?"":existing.name);mac.setText(existing==null?address.getText().toString():existing.mac);kind.setSelection((existing!=null?existing.kind:n==null?"WIFI":n.kind).equals("WIFI")?0:1);
  AlertDialog d=new AlertDialog.Builder(this).setTitle(existing==null?"保存常用地址":"编辑常用地址").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",null).create();d.setOnShowListener(x->d.getButton(-1).setOnClickListener(v->{try{String k=kind.getSelectedItemPosition()==0?"WIFI":"USB";book.save(existing==null?null:existing.id,name.getText().toString(),mac.getText().toString(),k,existing!=null&&existing.kind.equals(k)?existing.iface:n!=null&&n.kind.equals(k)?n.name:"");d.dismiss();renderSaved();event("地址已保存到本机地址库");Toast.makeText(this,"已保存",0).show();}catch(Exception e){mac.setError(e.getMessage());}}));d.show();
 }
 void applySaved(AddressBook.Entry e){
  if(busy||!needRoot())return;ArrayList<Iface> matches=new ArrayList<>();for(Iface n:ifaces)if(n.kind.equals(e.kind))matches.add(n);
  if(matches.isEmpty()){dialog("未找到对应接口","请连接 "+(e.kind.equals("WIFI")?"Wi-Fi":"USB 网卡")+" 并刷新接口后重试。");return;}
  if(matches.size()==1){applyTarget(matches.get(0),e.mac);return;}
  String[] names=new String[matches.size()];for(int i=0;i<names.length;i++)names[i]=matches.get(i).title()+" · "+matches.get(i).mac;
  new AlertDialog.Builder(this).setTitle("选择要应用的接口").setItems(names,(d,p)->applyTarget(matches.get(p),e.mac)).setNegativeButton("取消",null).show();
 }
 void updateStatus(){if(activityStatus!=null)activityStatus.setText(busy?"正在执行…":root?"Root 已就绪":"需要 Root");rootText.setText(root?"Root 已验证":"Root 未授权 · 请在 SukiSU 中允许本应用");rootText.setTextColor(root?GREEN:AMBER);boolean loaded=hookLoaded();hookText.setText(loaded?"Wi-Fi Hook 已在本次开机加载\n"+config.get("hook_info"):"Wi-Fi Hook 尚未确认加载 · 需启用并重启");hookText.setTextColor(loaded?GREEN:AMBER);boolean enabled="1".equals(config.get("wifi_enabled"));profileText.setText(enabled?"固定模式：已开启\n"+config.get("wifi_iface")+" → "+config.get("wifi_mac")+"\n最近驱动返回："+value(config.get("last_result")):"固定模式：关闭 / 尚未保存");}
 boolean hookLoaded(){return !boot.isEmpty() && !boot.equals("unavailable") && boot.equals(config.get("hook_boot"));}
 String value(String s){return s==null||s.trim().equals("null")?"暂无":s.trim();}
 Iface selected(){int p=selector.getSelectedItemPosition();return p>=0 && p<ifaces.size()?ifaces.get(p):null;}
 void showSelected(boolean fill){Iface n=selected();if(n==null){currentText.setText("尚未找到 Wi-Fi 或已识别的 USB 网卡。USB 设备须先完成枚举并创建网络接口。");return;}displayedId=n.id();currentText.setText(n.title()+"\n当前 MAC  "+n.mac+"\n链路 "+n.state+" · 驱动 "+(n.driver.isEmpty()?"未知":n.driver)+(n.label.isEmpty()?"":"\n"+n.label));if(fill)address.setText(n.mac);}
 synchronized void append(String s){try{File f=new File(getFilesDir(),"session.txt");if(f.length()>1024*1024){File old=new File(getFilesDir(),"previous.txt");old.delete();f.renameTo(old);}try(FileOutputStream out=new FileOutputStream(f,true)){out.write(s.getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}}
 void event(String s){String l=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.ROOT).format(new Date())+"  "+s+"\n";history.append(l);if(history.length()>15000)history.delete(0,5000);append(l);if(logs!=null)logs.setText(history.substring(Math.max(0,history.length()-3500)));}
 void task(String label,Job job,java.util.function.Consumer<String> done){if(busy)return;busy=true;for(Button b:buttons)b.setEnabled(false);rootText.setText(label);activityStatus.setText(label);worker.execute(()->{String out;try{out=job.run();}catch(Exception e){out="执行失败："+e;}final String result=out;main.post(()->{if(dead)return;busy=false;for(Button b:buttons)b.setEnabled(true);updateStatus();done.accept(result);});});}
 static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
 void copyAsset(String n)throws Exception{try(InputStream in=getAssets().open(n);FileOutputStream o=new FileOutputStream(new File(getFilesDir(),n))){byte[] b=new byte[8192];int c;while((c=in.read(b))!=-1)o.write(b,0,c);}}
 String script(String n,String... args){String c="/system/bin/sh "+quote(new File(getFilesDir(),n).getAbsolutePath());for(String a:args)c+=" "+quote(a);return c;}
 Result process(String[] args,int seconds)throws Exception{java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();p.getOutputStream().close();ByteArrayOutputStream out=new ByteArrayOutputStream();Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){synchronized(out){if(out.size()<300000)out.write(b,0,Math.min(n,300000-out.size()));}}}catch(Exception ignored){}});reader.setDaemon(true);reader.start();boolean ok=p.waitFor(seconds,TimeUnit.SECONDS);if(!ok)p.destroyForcibly();reader.join(1500);String data;synchronized(out){data=out.toString("UTF-8");}return new Result(ok?p.exitValue():124,data+(ok?"":"\n操作超时，请检查 Root 授权。"));}
 Result rootCommand(String command,int seconds)throws Exception{return process(new String[]{su,"-c",command},seconds);}
 void checkRoot(){task("等待 Root 授权（最长 45 秒）…",()->{StringBuilder failures=new StringBuilder();for(String c:new String[]{"su","/debug_ramdisk/su","/sbin/su","/system/xbin/su","/system/bin/su"})try{Result r=process(new String[]{c,"-c","id -u"},45);if(r.code==0 && r.text.trim().equals("0")){su=c;return "OK";}failures.append(r.text);break;}catch(IOException e){failures.append(c).append(" 不可见\n");}return failures+"\n请在 SukiSU / KernelSU / Magisk 中允许 MAC修改助手（cn.macmanual）。";},out->{root=out.equals("OK");getPreferences(0).edit().putBoolean("root",root).putString("su",su).apply();updateStatus();event(root?"Root 验证通过":out);if(root)refresh();else dialog("尚未取得 Root",out);});}
 boolean needRoot(){if(root)return true;dialog("需要 Root","请先点击“申请 / 检查 Root”，并在 Root 管理器中允许 MAC修改助手（cn.macmanual）。");return false;}
 String snapshot()throws Exception{Result r=rootCommand(script("scan.sh"),15);if(r.code!=0||!r.text.contains("ROOT|yes"))throw new IOException("Root 诊断未完成，请重新检查授权。\n"+r.text);return r.text;}
 void refresh(){if(!needRoot())return;task("读取真实网络接口…",()->snapshot(),out->{parse(out);event("接口状态已刷新");});}
 void parse(String raw){if(!raw.contains("ROOT|yes")){event(raw);return;}Iface old=selected();String previous=old==null?"":old.name;String entered=address.getText().toString();ifaces.clear();config.clear();boot="";for(String l:raw.split("\n")){String[] p=l.split("\\|",-1);if(p[0].equals("IFACE")&&p.length>=9)ifaces.add(new Iface(p));if(p[0].equals("BOOT")&&p.length>1)boot=p[1];if(p[0].equals("CFG")&&p.length>2)config.put(p[1],p[2].trim());}binding=true;ArrayList<String> names=new ArrayList<>();int pos=0;for(int i=0;i<ifaces.size();i++){names.add(ifaces.get(i).title());if(ifaces.get(i).name.equals(previous))pos=i;}if(names.isEmpty())names.add("未发现可用接口");ArrayAdapter<String> a=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,names);selector.setAdapter(a);selector.setSelection(pos);binding=false;showSelected(old==null);if(old!=null)address.setText(entered);lastSnapshot=raw;updateStatus();}
 String desired(){try{return MacRules.normalize(address.getText().toString());}catch(IllegalArgumentException e){address.setError(e.getMessage());return null;}}
 void confirm(String title,String message,Runnable run){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",null).setPositiveButton("执行",(d,w)->run.run()).show();}
 void dialog(String title,String msg){new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("知道了",null).show();}
 void apply(boolean unused){if(!needRoot()||busy)return;Iface n=selected();if(n==null){dialog("没有可操作接口","请连接网卡并刷新。未枚举的 USB 网卡无法改址。");return;}String mac=desired();if(mac!=null)applyTarget(n,mac);}
 void applyTarget(Iface n,String mac){
  if(busy||!needRoot())return;
  if(n.kind.equals("WIFI")&&!hookLoaded()){dialog("Wi-Fi 模块尚未加载","请在“模块”栏目完成首次启用。模块加载后，立即修改会自动保存固定配置并改址，无需分步操作。");showPage(2);return;}
  String note=MacRules.local(mac)?"":"\n请确认此地址没有与同一网络中的设备重复。";
  confirm("应用到 "+n.name,"目标地址："+mac+(n.kind.equals("WIFI")?"\n将自动更新 Wi-Fi 固定配置，再修改接口并核对结果。":"\n将修改 USB 接口并核对结果。")+"\n此接口的网络会短暂中断。"+note,()->runAction("smart-apply",n,mac,true));
 }
 void profileOff(){if(!needRoot())return;confirm("关闭固定模式","停止替换系统 Wi-Fi MAC 设置。当前地址不会立即恢复；重连后由系统网络设置决定地址。",()->runAction("profile-off",null,"",false));}
 void restore(){if(!needRoot())return;Iface n=selected();if(n==null)return;String saved=getPreferences(0).getString("backup_"+n.name,"");String[] p=saved.split("\\|",-1);if(p.length!=4||!p[0].equals(boot)||!p[1].equals(n.index)||!p[2].equals(n.path)){dialog("没有当前接口的备份","只有本应用成功修改过、且仍是同一次开机和同一个接口实例时，才能恢复。拔插或重启后不会猜测恢复地址。");return;}if(n.kind.equals("WIFI")&&!hookLoaded()){dialog("模块尚未加载","请先在模块栏目完成首次启用。");return;}confirm("恢复修改前地址",n.name+" → "+p[3]+"\n此地址是本次修改前读取的地址，不一定是出厂地址。",()->runAction("smart-apply",n,p[3],false));}
 void runAction(String op,Iface n,String mac,boolean backup){task("执行操作并回读状态…",()->{String command=n==null?script("action.sh",op):script("action.sh",op,n.name,mac,n.index,n.path,n.mac);Result r=rootCommand(command,35);String after;try{after=snapshot();}catch(Exception e){after="刷新失败："+e;}return "EXIT="+r.code+"\n"+r.text+"\n===SNAPSHOT===\n"+after;},out->{int at=out.indexOf("===SNAPSHOT===\n");String msg=at<0?out:out.substring(0,at);if(backup&&msg.startsWith("EXIT=0\n")&&n!=null){String key="backup_"+n.name;String old=getPreferences(0).getString(key,"");if(!old.startsWith(boot+"|"+n.index+"|"+n.path+"|"))getPreferences(0).edit().putString(key,boot+"|"+n.index+"|"+n.path+"|"+n.mac).apply();}if(at>=0)parse(out.substring(at+15));event(msg);append("\n"+out+"\n");dialog(msg.startsWith("EXIT=0\n")?"操作已完成":"操作未完成",msg);});}
 String read(File f)throws Exception{if(!f.exists())return "";try(InputStream in=new FileInputStream(f);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toString("UTF-8");}}
 void export(){try{report="MAC修改助手 v1.2.0\n"+read(new File(getFilesDir(),"previous.txt"))+read(new File(getFilesDir(),"session.txt"))+"\n最新接口状态\n"+lastSnapshot;}catch(Exception e){report=history+"\n"+lastSnapshot;}Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"MAC-Manual-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".txt");try{startActivityForResult(i,5);}catch(Exception e){dialog("导出失败",e.toString());}}
 @Override protected void onActivityResult(int r,int c,Intent d){super.onActivityResult(r,c,d);if(r==5&&c==RESULT_OK&&d!=null&&d.getData()!=null)try(OutputStream o=getContentResolver().openOutputStream(d.getData())){if(o==null)throw new IOException("无法打开文件");o.write(report.getBytes(StandardCharsets.UTF_8));event("诊断记录已导出");}catch(Exception e){dialog("保存失败",e.toString());}}
 void help(){dialog("使用说明与来源","Wi-Fi：先启用本 LSPosed 模块（系统框架作用域），停用旧 MACsposed Fork，重启一次。在“修改”选择 wlan0、输入 MAC 后点“立即修改”。应用会自动先保存固定配置，再修改实际接口并回读验证；失败时尝试恢复原配置与地址。\n\n地址库：保存名称、MAC 和网卡类型，点击“应用”并确认即可。地址保存在本机，升级保留；卸载清除。多个同类接口时会让你选择。\n\nUSB：网卡先完成枚举，刷新后选择其接口，输入地址并立即修改。拔插后可能恢复设备默认值；本版本不做开机或插入自动改址。\n\n立即修改使用 Root 修改内核接口；Wi-Fi 固定模式改写驱动设置调用的参数，不伪造驱动成功。驱动是否支持仍以实际返回与读回结果为准。\n\n生成地址为本地单播地址。MAC 修改会影响 DHCP 和网络会话；不修改固件和出厂存储。\n\n参考 MACsposed-Fork 的 Wi-Fi Hook 思路；本项目使用 AGPL-3.0，源码随 APK 一并提供。原项目：github.com/Samg381/MACsposed-Fork。\n\n本应用读取原模块信息但不覆盖其安装，也不会自动重启手机。");}
}
