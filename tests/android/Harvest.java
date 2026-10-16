package cn.macmanual;
import android.app.*;import android.os.*;import java.io.*;
public class Harvest extends Instrumentation {
 public void onCreate(Bundle b){super.onCreate(b);start();}
 public void onStart(){Bundle b=new Bundle();try{
  File source=new File(getTargetContext().getFilesDir(),"session.txt");File dest=new File(getTargetContext().getExternalFilesDir(null),"prior-session.txt");try(FileInputStream in=new FileInputStream(source);FileOutputStream out=new FileOutputStream(dest)){byte[] data=new byte[8192];int n;while((n=in.read(data))!=-1)out.write(data,0,n);}
  b.putString("stream","Saved prior log");finish(-1,b);
 }catch(Exception e){b.putString("stream",e.toString());finish(0,b);}}
}
