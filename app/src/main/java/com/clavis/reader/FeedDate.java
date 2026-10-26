package com.clavis.reader;
import java.time.*;
import java.time.format.*;
import java.util.Locale;
/** Publication metadata, displayed in the device's local timezone. */
final class FeedDate {
 private static final java.util.concurrent.ExecutorService repairs=java.util.concurrent.Executors.newFixedThreadPool(2);
 static void repair(MainActivity a,String kind,java.util.List<FeedStore.Feed> feeds){android.content.Context app=a.getApplicationContext();android.content.SharedPreferences attempts=app.getSharedPreferences("feed_date_repairs",0);for(FeedStore.Feed feed:feeds){if(!feed.kind.equals(kind)||feed.items.stream().noneMatch(i->i.date.isEmpty()))continue;String key=kind+feed.url;long now=System.currentTimeMillis();if(now-attempts.getLong(key,0)<6*60*60*1000L)continue;attempts.edit().putLong(key,now).apply();repairs.execute(()->{try{FeedStore.Feed updated=FeedStore.download(feed.url,kind);java.util.HashMap<String,String> dates=new java.util.HashMap<>();for(FeedStore.Item item:updated.items)if(!item.date.isEmpty())dates.put(FeedHighlights.key(item),item.date);
   synchronized(FeedStore.class){java.util.ArrayList<FeedStore.Feed> stored=FeedStore.load(app);for(FeedStore.Feed current:stored)if(current.url.equals(feed.url)&&current.kind.equals(kind)){boolean changed=false;for(FeedStore.Item item:current.items)if(item.date.isEmpty()&&dates.containsKey(FeedHighlights.key(item))){item.date=dates.get(FeedHighlights.key(item));changed=true;}if(changed)FeedStore.save(app,stored);break;}}
   a.runOnUiThread(()->{if(a.isDestroyed())return;for(FeedStore.Item item:feed.items)if(item.date.isEmpty()&&dates.containsKey(FeedHighlights.key(item)))item.date=dates.get(FeedHighlights.key(item));updateHeaders(a.mainFrame);});
  }catch(Exception ignored){}});}}
 static void updateHeaders(android.view.View root){if(root instanceof android.widget.TextView&&root.getTag() instanceof FeedStore.Item)bind((android.widget.TextView)root,(FeedStore.Item)root.getTag());if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int n=0;n<group.getChildCount();n++)updateHeaders(group.getChildAt(n));}}
 static void bind(android.widget.TextView view,FeedStore.Item item){String value=display(item.date,view.getResources().getConfiguration().getLocales().get(0));view.setText(value);view.setVisibility(value.isEmpty()?android.view.View.GONE:android.view.View.VISIBLE);view.setContentDescription(value.isEmpty()?null:"Published "+value.replace('\n',' '));}
 static Instant instant(String value){value=value.replaceAll("\\s+BST$"," +0100");
  try{return Instant.parse(value);}catch(Exception ignored){}
  try{return OffsetDateTime.parse(value).toInstant();}catch(Exception ignored){}
  try{return ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();}catch(Exception ignored){}
  for(String pattern:new String[]{"EEE, d MMM yyyy HH:mm:ss Z","EEE, d MMM yyyy HH:mm Z","d MMM yyyy HH:mm:ss Z"})try{java.text.SimpleDateFormat parser=new java.text.SimpleDateFormat(pattern,Locale.US);parser.setLenient(false);java.text.ParsePosition position=new java.text.ParsePosition(0);java.util.Date date=parser.parse(value,position);if(date!=null&&position.getIndex()==value.length())return date.toInstant();}catch(Exception ignored){}
  return null;
 }
 static String display(String raw,Locale locale){String value=raw==null?"":raw.trim();Instant time=instant(value);if(time!=null){ZonedDateTime local=time.atZone(ZoneId.systemDefault());return DateTimeFormatter.ofPattern("d MMM yyyy",locale).format(local)+" · "+DateTimeFormatter.ofPattern("h:mm a",locale).format(local);}
  try{return LocalDate.parse(value).format(DateTimeFormatter.ofPattern("d MMM yyyy",locale));}catch(Exception ignored){}return "";
 }
}



