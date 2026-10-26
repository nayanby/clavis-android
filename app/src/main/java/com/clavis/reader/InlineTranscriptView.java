package com.clavis.reader;

import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Live lyrics-style text in the cover slot, sharing the player's controls below. */
final class InlineTranscriptView extends LinearLayout {
 private final MainActivity a;private final Book book;private final Handler handler=new Handler(Looper.getMainLooper());private final ArrayList<TranscriptLine> lines=new ArrayList<>();private final ArrayList<TextView> views=new ArrayList<>();private final TextView status,partial;private final ScrollView scroll;private final LinearLayout rows;private LiveTranscriber engine;
 private int chapter=-1,renderStart,lastActive=-1;private boolean closed,failed;private long coveredFrom=-1,coveredUntil,requestedFrom,requestedUntil,lastPosition=-1,lastTickTime;
 InlineTranscriptView(MainActivity a,Book book){
  super(a);this.a=a;this.book=book;setOrientation(VERTICAL);setBackground(a.bg(AppTheme.surface,16));setClipToOutline(true);setPadding(a.dp(14),a.dp(10),a.dp(14),a.dp(8));
  LinearLayout header=a.row();TextView title=a.text("LIVE TEXT",10,MainActivity.MUTED,false);title.setLetterSpacing(.1f);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));TextView offline=a.text("OFFLINE",9,AppTheme.accent,false);header.addView(offline);addView(header);
  status=a.text("Preparing English speech recognition…",10,MainActivity.MUTED,false);status.setPadding(0,a.dp(5),0,a.dp(5));addView(status);scroll=new ScrollView(a);rows=a.column();rows.setPadding(0,a.dp(6),0,a.dp(35));scroll.addView(rows);addView(scroll,new LinearLayout.LayoutParams(-1,0,1));partial=a.text("",14,MainActivity.INK,false);partial.setMaxLines(2);partial.setVisibility(View.GONE);addView(partial);TextView retry=a.text("Retry  ·  swipe either way for artwork",10,AppTheme.accent,false);retry.setPadding(0,a.dp(6),0,a.dp(2));retry.setOnClickListener(v->{failed=false;coveredFrom=-1;coveredUntil=0;engine.cancelCurrent();});addView(retry);
  engine=new LiveTranscriber(a,new LiveTranscriber.Listener(){public void status(String message){if(lines.isEmpty()){status.setText(message);status.setVisibility(View.VISIBLE);}else status.setVisibility(View.GONE);}public void partial(String text){/* Draft words are not synchronized to playback. Keep them off the lyrics panel. */}public void result(long from,long until,List<TranscriptLine> result){failed=false;lines.removeIf(line->line.time>=from&&line.time<until);lines.addAll(result);lines.sort(Comparator.comparingLong(line->line.time));coveredFrom=coveredFrom>=0&&from<=coveredUntil?Math.min(coveredFrom,from):from;coveredUntil=until;partial.setText("");status.setVisibility(lines.isEmpty()?View.VISIBLE:View.GONE);save();render();follow();}public void failure(String message){failed=true;status.setText(message);status.setVisibility(View.VISIBLE);}});handler.post(tick);
 }
 private Book currentBook(){PlaybackService s=PlaybackService.live;return s!=null&&s.uri.equals(book.uri)&&s.book!=null?s.book:book;}
 private String key(){return "offline-en-022-punct2:"+Integer.toHexString(book.uri.hashCode())+":"+chapter+":"+currentBook().trackStart(chapter);}
 private void load(){lines.clear();coveredFrom=-1;coveredUntil=0;try{JSONObject cache=new JSONObject(a.getSharedPreferences("transcripts",0).getString(key(),"{}"));coveredFrom=cache.optLong("from",-1);coveredUntil=cache.optLong("until");JSONArray data=cache.optJSONArray("lines");if(data!=null)for(int i=0;i<data.length();i++){JSONObject line=data.getJSONObject(i);lines.add(new TranscriptLine(line.getLong("time"),line.getString("text")));}}catch(Exception ignored){}render();}
 private void save(){if(chapter<0)return;try{JSONArray data=new JSONArray();for(TranscriptLine line:lines)data.put(new JSONObject().put("time",line.time).put("text",line.text));a.getSharedPreferences("transcripts",0).edit().putString(key(),new JSONObject().put("from",coveredFrom).put("until",coveredUntil).put("lines",data).toString()).apply();}catch(Exception ignored){}}
 private int active(){PlaybackService s=PlaybackService.live;if(s==null)return -1;int found=-1;for(int i=0;i<lines.size();i++){if(lines.get(i).time<=s.position())found=i;else break;}return found;}
 private void render(){
  status.setVisibility(lines.isEmpty()||failed?View.VISIBLE:View.GONE);lastActive=-1;
  int first=Math.max(0,active()-25),count=Math.min(100,lines.size()-first);
  if(views.isEmpty())rows.removeAllViews();
  while(views.size()>count)rows.removeView(views.remove(views.size()-1));
  renderStart=first;
  for(int i=0;i<count;i++){
   TranscriptLine line=lines.get(first+i);TextView text;
   if(i<views.size())text=views.get(i);else{text=a.text("",18,MainActivity.MUTED,false);text.setTypeface(null,Typeface.BOLD);text.setPadding(0,a.dp(7),0,a.dp(7));rows.addView(text);views.add(text);}
   if(!text.getText().toString().equals(line.text))text.setText(line.text);
   text.setOnClickListener(v->{PlaybackService s=PlaybackService.live;if(s!=null&&s.uri.equals(book.uri))s.seek(line.time);});
  }
  if(lines.isEmpty())rows.addView(a.text("Spoken words will appear here as the audiobook is transcribed.",17,MainActivity.MUTED,false));
 }
 private void follow(){int current=active();if(current<0||current==lastActive)return;if(current<renderStart||current>=renderStart+views.size()-5)render();for(int i=0;i<views.size();i++){boolean selected=renderStart+i==current;views.get(i).setTextColor(selected?MainActivity.INK:MainActivity.MUTED);views.get(i).setAlpha(selected?1f:.5f);}int local=current-renderStart;if(local>=0&&local<views.size()){View row=views.get(local);scroll.post(()->scroll.smoothScrollTo(0,Math.max(0,row.getTop()-scroll.getHeight()/3)));}lastActive=current;}
 private final Runnable tick=new Runnable(){public void run(){if(closed)return;PlaybackService s=PlaybackService.live;if(s==null||!s.ready||!s.uri.equals(book.uri)){status.setText("Start this audiobook to transcribe it.");handler.postDelayed(this,500);return;}if(chapter!=s.chapterIndex){engine.cancelCurrent();save();chapter=s.chapterIndex;lastPosition=-1;failed=false;load();}long position=s.position(),now=SystemClock.elapsedRealtime();
  long expected=lastPosition<0?position:lastPosition+(s.playing()?Math.round((now-lastTickTime)*s.speed):0);
  if(engine.busy()&&lastPosition>=0&&Math.abs(position-expected)>2500)engine.cancelCurrent();
  lastPosition=position;lastTickTime=now;
  if(!engine.busy()&&!failed&&TranscriptSchedule.needsMore(position,s.duration(),coveredFrom,coveredUntil,s.speed)){
   requestedFrom=TranscriptSchedule.nextStart(position,coveredFrom,coveredUntil);
   requestedUntil=Math.min(s.duration(),requestedFrom+(coveredFrom<0?12000:30000));
   if(requestedUntil>requestedFrom+300)engine.recognize(currentBook().trackUri(chapter),requestedFrom,requestedUntil,currentBook().trackStart(chapter));
  }follow();handler.postDelayed(this,500);}};
 void close(){if(closed)return;closed=true;save();handler.removeCallbacksAndMessages(null);engine.close();}
 @Override protected void onDetachedFromWindow(){close();super.onDetachedFromWindow();}
}

