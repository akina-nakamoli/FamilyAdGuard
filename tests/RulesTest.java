package cn.family.adguard;
import java.io.*;
public class RulesTest {
 public static void main(String[] args)throws Exception{
  Rules r=Rules.parse(new StringReader("#VER=test\nads.example\nads.example\nhttps://bad.test/x\ncom\n"));
  if(r.size()!=1||!r.blocks("a.ads.example")||r.blocks("badads.example"))throw new AssertionError();
  Rules shipped=Rules.parse(new InputStreamReader(new FileInputStream("app/assets/anti-ad-domains.txt"),"UTF-8"));
  if(shipped.size()<10000||!shipped.blocks("log-api.pangolin-sdk-toutiao.com")||shipped.blocks("example.org"))throw new AssertionError();
  System.out.println("PASS: parsed, deduplicated, bounded domain matching; bundled rules="+shipped.size());
 }
}
