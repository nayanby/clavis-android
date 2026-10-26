package com.clavis.reader;
import java.util.ArrayDeque;
/** Rolling payload-byte estimate; gaps and stalls must not retain a stale ETA. */
final class DownloadEstimate {
 static final class Sample {final long time,bytes;Sample(long time,long bytes){this.time=time;this.bytes=bytes;}}
 private final ArrayDeque<Sample> samples=new ArrayDeque<>();
 long remainingSeconds(long now,long bytes,long total,boolean running){
  if(!running||total<=0||bytes<0){samples.clear();return -1;}
  if(bytes>=total)return 0;
  Sample last=samples.peekLast();if(last!=null&&(bytes<last.bytes||now-last.time>10000))samples.clear();
  samples.addLast(new Sample(now,bytes));while(samples.size()>1&&now-samples.peekFirst().time>20000)samples.removeFirst();
  Sample first=samples.peekFirst();if(now-first.time<3000)return -1;
  Sample changed=null;for(Sample sample:samples)if(sample.bytes<bytes)changed=sample;
  if(changed==null||now-changed.time>8000)return -1;
  double rate=(bytes-first.bytes)*1000.0/(now-first.time);return rate<1?-1:(long)Math.ceil((total-bytes)/rate);
 }
 static String label(long seconds){if(seconds<0)return "Estimating time remaining...";if(seconds==0)return "Download complete";if(seconds<60)return "About "+seconds+"s remaining";long minutes=(seconds+59)/60;if(minutes<60)return "About "+minutes+" min remaining";long hours=minutes/60;return "About "+hours+"h "+(minutes%60)+"m remaining";}
}
