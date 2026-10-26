package com.clavis.reader;
/** Matches the reader footer's pagination-based percentage. */
final class BookProgress {
 static int percent(Book b){if(!b.isAudio())return b.duration>0?clamp(Math.round((b.position+1)*100f/b.duration)):0;long total=b.duration,done=b.position;if(!b.chapters.isEmpty()){String track=b.trackUri(0);boolean single=true;for(int i=1;i<b.chapterCount();i++)if(!track.equals(b.trackUri(i)))single=false;if(single&&b.trackEnd(b.chapterCount()-1)>0){total=b.trackEnd(b.chapterCount()-1);done=b.trackStart(b.chapterIndex)+b.position;}else if(b.chapterCount()>1)return -1;}return total>0?clamp(Math.round(done*100f/total)):0;}
 static int clamp(int value){return Math.max(0,Math.min(100,value));}
 static String label(Book b){int value=percent(b);return value<0?"":value+"% "+(b.isAudio()?"listened":"read");}
}
