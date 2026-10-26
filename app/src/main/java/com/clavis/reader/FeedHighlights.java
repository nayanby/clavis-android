package com.clavis.reader;
import android.content.Context;
import java.util.*;
/** Local popularity only: subscriptions do not expose comparable audience metrics. */
final class FeedHighlights {
 static String key(FeedStore.Item item){return !item.audio.isEmpty()?item.audio:!item.link.isEmpty()?item.link:item.id;}
 static void opened(Context c,FeedStore.Item item){String key=key(item);if(key.isEmpty())return;android.content.SharedPreferences p=c.getSharedPreferences("feed_activity",0);p.edit().putInt(key,Math.min(100000,p.getInt(key,0)+1)).apply();}
 static final class Entry {final FeedStore.Feed feed;final FeedStore.Item item;final int position,opens;Entry(FeedStore.Feed feed,FeedStore.Item item,int position,int opens){this.feed=feed;this.item=item;this.position=position;this.opens=opens;}}
 static ArrayList<Entry> ranked(Context c,String kind,List<FeedStore.Feed> feeds){ArrayList<Entry> result=new ArrayList<>();HashSet<String> seen=new HashSet<>();android.content.SharedPreferences activity=c.getSharedPreferences("feed_activity",0);
  for(FeedStore.Feed feed:feeds)if(feed.kind.equals(kind)){int position=0;for(FeedStore.Item item:feed.items){if(kind.equals("Podcasts")&&item.audio.isEmpty())continue;String key=key(item);if(key.isEmpty())key=feed.url+item.title;if(!seen.add(key))continue;result.add(new Entry(feed,item,position++,activity.getInt(key,0)));}}
  // Feeds usually publish newest first; interleave their positions instead of flooding
  // the first screen with a single source when there is no local activity yet.
  result.sort(Comparator.comparingInt((Entry e)->e.opens).reversed().thenComparingInt(e->e.position));return result;
 }
 static FeedStore.Feed combined(Context c,String kind,List<FeedStore.Feed> feeds){FeedStore.Feed combined=new FeedStore.Feed();combined.kind=kind;combined.title=kind.equals("Journals")?"Articles":kind;for(Entry e:ranked(c,kind,feeds)){combined.items.add(e.item);combined.origins.put(e.item,e.feed);if(combined.items.size()==150)break;}return combined;}
}
