package com.clavis.reader;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import androidx.recyclerview.widget.RecyclerView;

/** Covers the cover-to-story pager callback without injecting input into other apps. */
final class StoryPillChecks {
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static String run(Instrumentation test)throws Exception{
  MainActivity a=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{
   for(String kind:new String[]{"News","Journals"}){
    FeedStore.Feed feed=new FeedStore.Feed();feed.kind=kind;feed.title="Story pill check";feed.url="https://example.invalid/story-pill-check";feed.fullText=true;
    FeedStore.Item item=new FeedStore.Item();item.id="pill-check-"+kind;item.title="Story opening check";item.description=("<p>A long story keeps its text and reading controls while the reader opens the article from its cover. The floating navigation shortcut should leave the story clear.</p>").repeat(50);feed.items.add(item);
    test.runOnMainSync(()->{FeedRefresh.forget(a);FeedScreens.items(a,feed);});test.waitForIdleSync();SystemClock.sleep(400);
    final ScrollChrome.Controller[] controller={null};
    test.runOnMainSync(()->{
     RecyclerView posts=FeedPostChecks.posts(a.mainFrame);require(posts!=null,"Story list exists for "+kind);
     ScrollChrome.install(a);controller[0]=ScrollChrome.active.get(posts);require(controller[0]!=null,"Scroll controller exists");
     FeedPosts.PostHolder holder=(FeedPosts.PostHolder)posts.findViewHolderForAdapterPosition(0);require(holder!=null&&holder.slides.getItemCount()>1,"Story has a text page");
     controller[0].top.show(true);controller[0].userDirection=-1;require(controller[0].top.visible,"Shortcut is initially visible");
     holder.pager.setCurrentItem(1,true);require(!controller[0].top.visible,"Opening a story dismisses the shortcut");
     controller[0].moved(-50);require(!controller[0].top.visible,"Story centering cannot show the shortcut again");
    });
    SystemClock.sleep(450);test.waitForIdleSync();
    test.runOnMainSync(()->require(controller[0].top.pill.getVisibility()==View.GONE,"Shortcut stays hidden after story transition"));
   }
   return "14 News and Articles story-opening pill checks passed\n";
  }finally{test.runOnMainSync(a::finish);}
 }
}
