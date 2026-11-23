package cn.family.adguard;
import java.util.*;
public final class Trace {
 private static final LinkedList<String> entries=new LinkedList<>();
 private static long until;
 public static synchronized void enable(){entries.clear();until=System.nanoTime()+180000000000L;}
 public static synchronized void disable(){until=0;entries.clear();}
 public static synchronized boolean active(){if(until==0)return false;if(System.nanoTime()>until){disable();return false;}return true;}
 public static synchronized void add(String domain,boolean blocked){if(!active())return;entries.addFirst((blocked?"拦截  ":"放行  ")+domain);while(entries.size()>100)entries.removeLast();}
 public static synchronized List<String> snapshot(){active();return new ArrayList<>(entries);}
}
