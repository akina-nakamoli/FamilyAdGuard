package cn.family.adguard;
import java.util.*;
import java.nio.charset.StandardCharsets;
public final class Dns {
    public static boolean matches(String name, String rule) { return name.equals(rule) || name.endsWith("."+rule); }
    public static String name(byte[] q) {
        if(q.length<17 || (q[2]&128)!=0 || q[4]!=0 || q[5]!=1) return null;
        StringBuilder s=new StringBuilder(); int p=12;
        while(p<q.length) {
            int n=q[p++]&255;
            if(n==0) return p+4<=q.length && s.length()>0 ? s.toString().toLowerCase(Locale.ROOT) : null;
            if(n>63 || p+n>q.length || s.length()+n>253) return null;
            if(s.length()>0)s.append('.');
            s.append(new String(q,p,n,StandardCharsets.US_ASCII)); p+=n;
        }
        return null;
    }
    public static byte[] reply(byte[] q,int code) {
        byte[] r=q.clone(); r[2]=(byte)0x81; r[3]=(byte)(0x80|code);
        Arrays.fill(r,6,12,(byte)0); return r;
    }
    static int u16(byte[] b,int p){return ((b[p]&255)<<8)|(b[p+1]&255);}
    static void put(byte[] b,int p,int v){b[p]=(byte)(v>>8);b[p+1]=(byte)v;}
    public static byte[] packet(byte[] request,byte[] dns) {
        byte[] r=new byte[28+dns.length]; r[0]=0x45; put(r,2,r.length); r[8]=64;r[9]=17;
        System.arraycopy(request,16,r,12,4); System.arraycopy(request,12,r,16,4);
        int ihl=(request[0]&15)*4; put(r,20,53);put(r,22,u16(request,ihl));put(r,24,8+dns.length);
        System.arraycopy(dns,0,r,28,dns.length);
        int sum=0;for(int i=0;i<20;i+=2)sum+=u16(r,i);while((sum>>16)!=0)sum=(sum&65535)+(sum>>16);put(r,10,~sum);
        return r;
    }
}
