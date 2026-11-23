package cn.family.adguard;
import android.app.*;
import android.content.*;
import android.net.VpnService;
import android.os.*;
import java.io.*;
import java.net.*;
import java.util.*;

public class GuardService extends VpnService {
    public static volatile boolean running=false;
    private ParcelFileDescriptor tunnel;
    private Thread worker;
    private volatile DatagramSocket upstream;
    private volatile boolean stopping;
    static final String[] RULES={"doubleclick.net","googlesyndication.com","googleadservices.com","adservice.google.com","ads.unity3d.com","unityads.unity3d.com","applovin.com","adcolony.com","vungle.com","pangolin-sdk-toutiao.com","pglstatp-toutiao.com"};
    public int onStartCommand(Intent intent,int flags,int id) {
        if(intent!=null && "stop".equals(intent.getAction())){stopSelf();return START_NOT_STICKY;}
        if(worker!=null)return START_NOT_STICKY;
        stopping=false;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("guard","广告防护",NotificationManager.IMPORTANCE_LOW));
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE);
        PendingIntent stop=PendingIntent.getService(this,1,new Intent(this,GuardService.class).setAction("stop"),PendingIntent.FLAG_IMMUTABLE);
        startForeground(1,new Notification.Builder(this,"guard").setSmallIcon(android.R.drawable.ic_lock_lock).setContentTitle("安心广告管家正在防护").setContentText("仅过滤 DNS · 点此查看").setContentIntent(open).addAction(new Notification.Action.Builder(null,"停止防护",stop).build()).build());
        worker=new Thread(this::loop,"dns-guard");worker.start();return START_NOT_STICKY;
    }
    private void loop() {
        try {
            tunnel=new Builder().setSession("安心广告管家").addAddress("10.83.0.1",32).addDnsServer("10.83.0.2").addRoute("10.83.0.2",32).setMtu(1500).setBlocking(true).establish();
            if(tunnel==null)throw new IOException("VPN 未获授权");
            running=true;
            FileInputStream input=new FileInputStream(tunnel.getFileDescriptor());
            FileOutputStream output=new FileOutputStream(tunnel.getFileDescriptor());
            byte[] buffer=new byte[32767];
            while(!stopping) {
                int n=input.read(buffer);if(n<0)break;
                if(n<28 || (buffer[0]>>4)!=4 || buffer[9]!=17)continue;
                int h=(buffer[0]&15)*4;
                if(h<20 || n<h+8 || (Dns.u16(buffer,6)&0x3fff)!=0 || Dns.u16(buffer,h+2)!=53)continue;
                int len=Dns.u16(buffer,h+4)-8;
                if(len<12 || h+8+len>n)continue;
                byte[] q=Arrays.copyOfRange(buffer,h+8,h+8+len);
                String domain=Dns.name(q);if(domain==null)continue;
                boolean blocked=RuleStore.get(this).blocks(domain);
                for(String rule:RULES)if(Dns.matches(domain,rule)){blocked=true;break;}
                String custom=getSharedPreferences("guard",0).getString("rules","");
                for(String rule:custom.split("\\s+"))if(!rule.isEmpty()&&Dns.matches(domain,rule)){blocked=true;break;}
                String allowed=getSharedPreferences("guard",0).getString("allow","");
                for(String rule:allowed.split("\\s+"))if(!rule.isEmpty()&&Dns.matches(domain,rule)){blocked=false;break;}
                Trace.add(domain,blocked);
                byte[] answer;
                if(blocked){answer=Dns.reply(q,3);android.content.SharedPreferences prefs=getSharedPreferences("guard",0);prefs.edit().putLong("count",prefs.getLong("count",0)+1).apply();}
                else answer=resolve(q);
                if(!stopping)output.write(Dns.packet(buffer,answer));
            }
        }catch(Exception e){if(!stopping)getSharedPreferences("guard",0).edit().putString("error","防护已停止："+e.getClass().getSimpleName()).apply();}
        finally{running=false;closeTunnel();stopSelf();}
    }
    private byte[] resolve(byte[] q) {
        try(DatagramSocket socket=new DatagramSocket()) {
            upstream=socket;if(!protect(socket))return Dns.reply(q,2);
            socket.connect(InetAddress.getByName("223.5.5.5"),53);socket.setSoTimeout(1800);
            socket.send(new DatagramPacket(q,q.length));
            byte[] b=new byte[4096];DatagramPacket p=new DatagramPacket(b,b.length);socket.receive(p);
            if(p.getLength()<12 || b[0]!=q[0] || b[1]!=q[1] || (b[2]&128)==0)return Dns.reply(q,2);
            return Arrays.copyOf(b,p.getLength());
        }catch(IOException e){return Dns.reply(q,2);}finally{upstream=null;}
    }
    private synchronized void closeTunnel(){if(tunnel!=null){try{tunnel.close();}catch(IOException ignored){}tunnel=null;}}
    public void onRevoke(){stopSelf();}
    public void onDestroy(){stopping=true;running=false;if(upstream!=null)upstream.close();closeTunnel();if(worker!=null)worker.interrupt();super.onDestroy();}
}
