package com.aquaquenchers.classifier;
import java.util.*;
import java.util.function.Supplier;
/** Measurement harness; callers declare actual device/network/provenance. No fabricated results. */
public final class InferenceBenchmark {
 private InferenceBenchmark(){}
 public static Map<String,Object> measure(Supplier<ExpenseCategoryClassifier> factory,List<String> remarks,String device){
  if(remarks==null||remarks.isEmpty()||device==null||device.trim().isEmpty())throw new IllegalArgumentException("Declare device and benchmark inputs.");
  long baseline=used();ExpenseCategoryClassifier classifier=factory.get();
  int known=0;String version=null;for(String text:remarks){ExpenseCategoryClassifier.Result r=classifier.suggest(text);if(r.status==ExpenseCategoryClassifier.Status.UNAVAILABLE)throw new IllegalArgumentException("Approved model unavailable; device benchmark NOT RUN.");if(r.reason!=ExpenseCategoryClassifier.Reason.NO_KNOWN_TERMS&&r.reason!=ExpenseCategoryClassifier.Reason.TOO_MANY_TOKENS)known++;version=r.modelVersion;}if(known*2<remarks.size())throw new IllegalArgumentException("Benchmark needs at least half known feature inputs; review the workload.");
  for(int i=0;i<100;i++)classifier.suggest(remarks.get(i%remarks.size()));
  long[] samples=new long[1000];long peak=used();
  for(int i=0;i<1000;i++){long start=System.nanoTime();classifier.suggest(remarks.get(i%remarks.size()));samples[i]=System.nanoTime()-start;peak=Math.max(peak,used());}
  Arrays.sort(samples);Map<String,Object> result=new LinkedHashMap<>();result.put("device",device);result.put("model_version",version);result.put("known_feature_inputs",known);result.put("input_count",remarks.size());result.put("warmups",100);result.put("timed_calls",1000);result.put("median_ms",(samples[499]/2.0+samples[500]/2.0)/1e6);result.put("p95_ms",samples[949]/1e6);result.put("incremental_java_heap_bytes",Math.max(0,peak-baseline));result.put("memory_method","Runtime used-heap peak including model load; not process RSS/PSS");result.put("latency_gate_pass",samples[949]<=100000000L);result.put("heap_gate_pass",peak-baseline<=32L*1024*1024);result.put("quality_evaluation","NOT RUN; repeated timings are not independent labeled examples");return result;
 }
 private static long used(){Runtime r=Runtime.getRuntime();return r.totalMemory()-r.freeMemory();}
}
