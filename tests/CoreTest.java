package cn.family.adguard;
public class CoreTest {
    public static void main(String[] args) throws Exception {
        check(Dns.matches("a.ads.example", "ads.example"));
        check(!Dns.matches("badads.example", "ads.example"));
        byte[] q = new byte[]{1,2,1,0,0,1,0,0,0,0,0,0,3,97,100,115,7,101,120,97,109,112,108,101,0,0,1,0,1};
        check("ads.example".equals(Dns.name(q)));
        byte[] r = Dns.reply(q, 3);
        check((r[3]&15)==3 && (r[2]&128)!=0 && r[0]==1);
        check(Dns.name(new byte[2])==null);
        byte[] request = new byte[28+q.length]; request[0]=0x45; request[9]=17;
        request[12]=10; request[15]=1; request[16]=10; request[19]=2;
        Dns.put(request,20,45678); Dns.put(request,22,53);
        byte[] packet=Dns.packet(request,r);
        check(Dns.u16(packet,2)==28+r.length && Dns.u16(packet,22)==45678);
        check(packet[15]==2 && packet[19]==1 && packet[28]==1);
        int sum=0; for(int i=0;i<20;i+=2)sum+=Dns.u16(packet,i);
        while((sum>>16)!=0)sum=(sum&65535)+(sum>>16); check(sum==65535);
        q[12]=(byte)192; check(Dns.name(q)==null);
        System.out.println("PASS: domain boundaries, query parsing, NXDOMAIN, malformed packets");
    }
    static void check(boolean value) { if(!value) throw new AssertionError(); }
}
