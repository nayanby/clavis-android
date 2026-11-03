package com.clavis.reader;

import android.app.Instrumentation;
import android.content.Intent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/** Render audiobook and podcast controls without starting playback or changing the library. */
final class PlayerCoverChecks {
 static View described(View view,String label){
  if(view.getContentDescription()!=null&&label.contentEquals(view.getContentDescription()))return view;
  if(view instanceof ViewGroup)for(int n=0;n<((ViewGroup)view).getChildCount();n++){View found=described(((ViewGroup)view).getChildAt(n),label);if(found!=null)return found;}
  return null;
 }
 static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
 static String run(Instrumentation test)throws Exception{
  MainActivity a=(MainActivity)test.startActivitySync(new Intent(test.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{
   for(String kind:new String[]{"Audio","Podcast"}){
    test.runOnMainSync(()->NowPlayingScreen.show(a,new Book("content://clavis-player-check/"+kind,"Player cover check",kind)));
    test.waitForIdleSync();
    test.runOnMainSync(()->{
     View artwork=described(a.mainFrame,"Cover artwork");
     require(artwork instanceof FrameLayout,"Player cover remains visible for "+kind);
     require(((FrameLayout)artwork).getChildCount()==1&&((FrameLayout)artwork).getChildAt(0) instanceof CoverArtView,"Cover contains artwork only");
     require(a.playButton!=null&&a.seek!=null,"Play and seek controls remain");
     require(described(a.mainFrame,"Skip backward")!=null&&described(a.mainFrame,"Skip forward")!=null,"Skip controls remain");
     require(a.speedButton!=null,"Playback speed remains");
     require(FeedTabChecks.text(a.mainFrame,"Bookmark")!=null,"Bookmarks remain");
    });
   }
   return "12 audiobook and podcast player cover/control checks passed\n";
  }finally{test.runOnMainSync(a::finish);}
 }
}
