package cn.family.adguard;
import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import javax.net.ssl.HttpsURLConnection;
public final class RuleStore {
 public static final String SOURCE="https://raw.githubusercontent.com/privacy-protection-tools/anti-AD/master/anti-ad-domains.txt";
 private static volatile Rules current;
 private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"downloaded-rules.txt"));}
 private static Rules read(InputStream in)throws IOException{try(Reader r=new InputStreamReader(in,"UTF-8")){return Rules.parse(r);}}
 public static Rules get(Context c)throws IOException{
  Rules cached=current;if(cached!=null)return cached;
  synchronized(RuleStore.class){
  if(current==null){try{current=read(file(c).openRead());if(current.size()<10000)throw new IOException("下载规则不完整");}catch(IOException e){current=read(c.getAssets().open("anti-ad-domains.txt"));}}
  return current;}
 }
 public static synchronized void reset(Context c)throws IOException{Rules built=read(c.getAssets().open("anti-ad-domains.txt"));file(c).delete();current=built;}
 public static synchronized int update(Context c)throws IOException {
  byte[] data;
  try{data=download(SOURCE);}catch(IOException e){data=download("https://cdn.jsdelivr.net/gh/privacy-protection-tools/anti-AD@master/anti-ad-domains.txt");}
  Rules candidate=read(new ByteArrayInputStream(data));if(candidate.size()<10000)throw new IOException("规则不完整，已保留原规则");
  AtomicFile destination=file(c);FileOutputStream out=null;
  try{out=destination.startWrite();out.write(data);destination.finishWrite(out);}catch(IOException e){if(out!=null)destination.failWrite(out);throw e;}
  current=candidate;return candidate.size();
 }
 private static byte[] download(String url)throws IOException{
  HttpsURLConnection connection=(HttpsURLConnection)new URL(url).openConnection();connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
  try{if(connection.getResponseCode()!=200)throw new IOException("下载服务器返回 "+connection.getResponseCode());
   try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
    byte[] buffer=new byte[8192];int n;long deadline=System.nanoTime()+45000000000L;while((n=in.read(buffer))!=-1){if(System.nanoTime()>deadline)throw new IOException("下载超时");if(out.size()+n>12000000)throw new IOException("规则文件过大");out.write(buffer,0,n);}return out.toByteArray();}
  }finally{connection.disconnect();}
 }
}
