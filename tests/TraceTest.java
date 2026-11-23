package cn.family.adguard;
public class TraceTest {
 public static void main(String[] args) {
  Trace.add("hidden.example",false); if(!Trace.snapshot().isEmpty())throw new AssertionError();
  Trace.enable(); for(int i=0;i<150;i++)Trace.add("host"+i+".example",i%2==0);
  if(Trace.snapshot().size()!=100)throw new AssertionError();
  Trace.disable(); if(!Trace.snapshot().isEmpty())throw new AssertionError();
  System.out.println("PASS: opt-in trace, bounded memory, deletion on stop");
 }
}
