package cn.family.adguard;
import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
public final class Rules {
 private static final Pattern DOMAIN=Pattern.compile("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");
 private final Set<String> domains=new HashSet<>();
 public String version="未知";
 public static Rules parse(Reader reader)throws IOException {
  Rules rules=new Rules();BufferedReader in=new BufferedReader(reader);String s;int chars=0;
  while((s=in.readLine())!=null){chars+=s.length();if(chars>12000000)throw new IOException("规则文件过大");s=s.trim().toLowerCase(Locale.ROOT);
   if(s.startsWith("#ver=")){rules.version=s.substring(5);continue;}
   if(s.length()<=253 && DOMAIN.matcher(s).matches())rules.domains.add(s);
  }return rules;
 }
 public boolean blocks(String domain){String s=domain.toLowerCase(Locale.ROOT);while(true){if(domains.contains(s))return true;int p=s.indexOf('.');if(p<0)return false;s=s.substring(p+1);}}
 public int size(){return domains.size();}
}
