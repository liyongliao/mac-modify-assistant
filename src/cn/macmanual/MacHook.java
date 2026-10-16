package cn.macmanual;

import android.app.AndroidAppHelper;
import android.app.Application;
import android.content.ContentResolver;
import android.net.MacAddress;
import android.os.Binder;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Inspired by MACsposed-Fork, AGPL-3.0. Never fabricates setter success. */
public final class MacHook implements IXposedHookLoadPackage {
 static final String PREFIX="macmanual_v1_";
 final Set<Class<?>> installed=Collections.newSetFromMap(new IdentityHashMap<Class<?>,Boolean>());
 int setterCount=0, getterCount=0;
 @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) {
  if(!"android".equals(p.packageName))return;
  try {
   Class<?> manager=XposedHelpers.findClass("com.android.server.SystemServiceManager",p.classLoader);
   XposedBridge.hookAllMethods(manager,"loadClassFromLoader",new XC_MethodHook(){
    @Override protected void afterHookedMethod(MethodHookParam m){
     if(m.args.length==0 || !"com.android.server.wifi.WifiService".equals(m.args[0]))return;
     for(Object a:m.args)if(a instanceof ClassLoader)install((ClassLoader)a);
     if(m.getResult() instanceof Class)install(((Class<?>)m.getResult()).getClassLoader());
    }});
   // Covers ROMs that use a different service loading helper.
   XposedBridge.hookAllMethods(manager,"startService",new XC_MethodHook(){
    @Override protected void afterHookedMethod(MethodHookParam m){Object service=m.getResult();if(service!=null && service.getClass().getName().equals("com.android.server.wifi.WifiService"))install(service.getClass().getClassLoader());}});
   install(p.classLoader);
  }catch(Throwable t){XposedBridge.log("[MACManual] registration error: "+t);}
 }
 synchronized void install(ClassLoader loader){
  if(loader==null)return;
  try {
   Class<?> vendor=XposedHelpers.findClass("com.android.server.wifi.WifiVendorHal",loader);
   if(!installed.contains(vendor)){
    for(Method method:vendor.getDeclaredMethods())if(method.getName().equals("setStaMacAddress") && method.getReturnType()==boolean.class){
     Class<?>[] types=method.getParameterTypes();
     if(types.length!=2 || types[0]!=String.class || !types[1].getName().equals("android.net.MacAddress"))continue;
     XposedBridge.hookMethod(method,new XC_MethodHook(){
      @Override protected void beforeHookedMethod(MethodHookParam m){
       String chosen=choice((String)m.args[0]);if(chosen==null)return;
       try{m.args[1]=MacAddress.fromString(chosen);m.setObjectExtra("macmanual",chosen);}catch(Throwable t){XposedBridge.log("[MACManual] invalid address, pass through: "+t);}
      }
      @Override protected void afterHookedMethod(MethodHookParam m){
       Object requested=m.getObjectExtra("macmanual");if(requested!=null)status("last_result",m.args[0]+" / "+requested+" / driver="+(m.hasThrowable()?m.getThrowable().getClass().getSimpleName():String.valueOf(m.getResult())));
      }});setterCount++;
    }
    installed.add(vendor);
   }
   Class<?> nativeWifi=XposedHelpers.findClass("com.android.server.wifi.WifiNative",loader);
   if(!installed.contains(nativeWifi)){
    for(Method method:nativeWifi.getDeclaredMethods())if(method.getName().equals("getFactoryMacAddress") && method.getReturnType()==String.class && Arrays.equals(method.getParameterTypes(),new Class<?>[]{String.class})){
     XposedBridge.hookMethod(method,new XC_MethodHook(){
      @Override protected void afterHookedMethod(MethodHookParam m){String chosen=choice((String)m.args[0]);if(chosen!=null && !m.hasThrowable())m.setResult(chosen);}
     });getterCount++;
    }
    installed.add(nativeWifi);
   }
   publish(0);
  }catch(XposedHelpers.ClassNotFoundError ignored){ /* Wi-Fi APEX has not loaded yet. */ }
  catch(Throwable t){XposedBridge.log("[MACManual] Wi-Fi installation error: "+t);}
 }
 String choice(String iface){
  long token=Binder.clearCallingIdentity();
  try{Application app=AndroidAppHelper.currentApplication();if(app==null)return null;ContentResolver r=app.getContentResolver();return MacRules.selected("1".equals(Settings.Global.getString(r,PREFIX+"wifi_enabled")),Settings.Global.getString(r,PREFIX+"wifi_iface"),iface,Settings.Global.getString(r,PREFIX+"wifi_mac"));}
  catch(Throwable t){return null;}finally{Binder.restoreCallingIdentity(token);}
 }
 void publish(int retry){
  if(setterCount==0)return;
  if(AndroidAppHelper.currentApplication()==null){if(retry<30)new Handler(Looper.getMainLooper()).postDelayed(()->publish(retry+1),1000);return;}
  status("hook_boot",boot());status("hook_info","1.0.0 / setter="+setterCount+" / factoryDecision="+getterCount);
  XposedBridge.log("[MACManual] Wi-Fi hooks installed; setters="+setterCount+", factory decision="+getterCount);
 }
 void status(String key,String value){long token=Binder.clearCallingIdentity();try{Application app=AndroidAppHelper.currentApplication();if(app!=null)Settings.Global.putString(app.getContentResolver(),PREFIX+key,value);}catch(Throwable ignored){}finally{Binder.restoreCallingIdentity(token);}}
 static String boot(){try(BufferedReader r=new BufferedReader(new FileReader("/proc/sys/kernel/random/boot_id"))){return r.readLine();}catch(Exception e){return "unavailable";}}
}
