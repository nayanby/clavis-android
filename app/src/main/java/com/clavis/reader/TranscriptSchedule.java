package com.clavis.reader;
/** Maintain a minute of wall-clock text ahead; never decode past chapter end. */
final class TranscriptSchedule {
 static boolean needsMore(long position,long duration,long from,long until,float speed){
  if(duration<=0)return false;
  if(from<0||position<from||position>until)return true;
  return until<duration&&until-position<Math.max(60000,Math.round(60000*speed));
 }
 static long nextStart(long position,long from,long until){return from>=0&&position>=from&&position<=until?until:Math.max(0,position-1500);}
}
