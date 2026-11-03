package com.clavis.reader;

import android.content.*;
import java.util.*;

/** Uses isolated preferences, leaving the user's sort and tag choices untouched. */
final class FeedTagChecks {
 static void run(Context app){
  String name="feed_tag_checks_"+System.nanoTime();
  Context isolated=new ContextWrapper(app){@Override public SharedPreferences getSharedPreferences(String ignored,int mode){return app.getSharedPreferences(name,mode);}};
  try{
   FeedTopics.prefs(isolated).edit().putBoolean("latest_News",true).commit();
   require(!FeedTopics.latest(isolated,"News"),"Popular replaces the legacy default");
   FeedTopics.prefs(isolated).edit().putBoolean("latest_News",true).commit();
   require(FeedTopics.latest(isolated,"News"),"An explicit Latest choice is retained");
   ArrayList<String> recent=new ArrayList<>();FeedTopics.remember(recent,"Medical");FeedTopics.remember(recent,"Technology");FeedTopics.remember(recent,"Physics");
   Set<String> selected=new HashSet<>(Arrays.asList("Medical","Technology","Physics"));FeedTopics.save(isolated,"News",selected,recent);
   require(FeedTopics.selected(isolated,"News").equals(selected),"More than two selected tags are retained");
   require(FeedTopics.quickTags(isolated,"News").equals(Arrays.asList("Physics","Technology")),"The last two choices appear first");
   ArrayList<String> fitted=FeedTopics.fitTags(isolated,"News",35,tag->10);require(fitted.size()==3,"Tag row is not limited to two choices");Set<String> five=new HashSet<>(Arrays.asList("Medical","Technology","Physics","Science","India"));require(FeedTopics.overflowLabel(five,Arrays.asList("Medical","Technology")).equals("+3 more"),"Overflow counts selected tags outside the visible row");require(FeedTopics.overflowLabel(five,Arrays.asList("Medical","Technology","Physics")).equals("+2 more"),"Overflow adjusts when more tags fit");
   FeedTopics.save(isolated,"News",Collections.emptySet(),recent);
   require(FeedTopics.selected(isolated,"News").isEmpty(),"All clears the filter");
   require(FeedTopics.quickTags(isolated,"News").equals(Arrays.asList("Physics","Technology")),"All retains recent tag shortcuts");
   FeedStore.Feed alpha=new FeedStore.Feed(),beta=new FeedStore.Feed();alpha.title="Alpha";alpha.url="alpha";alpha.kind="News";alpha.added=1;beta.title="beta";beta.url="beta";beta.kind="News";beta.added=2;ArrayList<FeedStore.Feed> feeds=new ArrayList<>(Arrays.asList(beta,alpha));FeedHighlights.sortSources(isolated,"News",feeds);require(feeds.get(0)==alpha,"My list sorts alphabetically");FeedTopics.prefs(isolated).edit().putInt("source_sort_News",1).commit();FeedHighlights.sortSources(isolated,"News",feeds);require(feeds.get(0)==beta,"My list sorts by date added newest first");FeedStore.save(isolated,feeds);FeedStore.Feed refreshed=new FeedStore.Feed();refreshed.url="alpha";refreshed.kind="News";refreshed.updated=999;FeedStore.replaceIfSubscribed(isolated,refreshed);for(FeedStore.Feed feed:FeedStore.load(isolated))if(feed.url.equals("alpha"))require(feed.added==1,"Refreshing preserves the original date added");require(FeedTopics.latest(isolated,"News"),"My list sort leaves Popular sort independent");
  }finally{app.deleteSharedPreferences(name);}
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
}
