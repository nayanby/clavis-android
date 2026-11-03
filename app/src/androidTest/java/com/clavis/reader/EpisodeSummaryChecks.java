package com.clavis.reader;
import android.app.*;import android.content.*;import android.view.*;import android.widget.*;

/** Episode descriptions expand in place from a lower-right arrow without starting playback. */
final class EpisodeSummaryChecks {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static View described(View root,String label){if(label.contentEquals(root.getContentDescription()==null?"":root.getContentDescription()))return root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){View found=described(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
 static TextView text(View root,String label){if(root instanceof TextView&&((TextView)root).getText().toString().equals(label))return (TextView)root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){TextView found=text(((ViewGroup)root).getChildAt(n),label);if(found!=null)return found;}return null;}
 static TextView containing(View root,String part){if(root instanceof TextView&&((TextView)root).getText().toString().contains(part))return (TextView)root;if(root instanceof ViewGroup)for(int n=0;n<((ViewGroup)root).getChildCount();n++){TextView found=containing(((ViewGroup)root).getChildAt(n),part);if(found!=null)return found;}return null;}
 static void run(Instrumentation test)throws Exception{
  Context c=test.getTargetContext();FeedStore.Feed feed=new FeedStore.Feed();feed.kind="Podcasts";feed.url="https://episode-summary.invalid/feed";feed.title="Summary check";
  FeedStore.Item longItem=new FeedStore.Item();longItem.id="episode-summary-long";longItem.title="Long episode";longItem.audio="https://episode-summary.invalid/long.mp3";longItem.description="<p>A long episode description. "+String.join(" ",java.util.Collections.nCopies(90,"word"))+"</p><p>Follow us on Instagram: https://social.example/show</p><p>Learn more about your ad choices.</p>";
  FeedStore.Item shortItem=new FeedStore.Item();shortItem.id="episode-summary-short";shortItem.title="Short episode";shortItem.audio="https://episode-summary.invalid/short.mp3";shortItem.description="Short note.";
  feed.items.add(longItem);feed.items.add(shortItem);
  MainActivity a=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  try{test.runOnMainSync(()->{a.screen="Summary check";a.shell("Episode descriptions");FeedScreens.entry(a,feed,longItem);FeedScreens.entry(a,feed,shortItem);});test.waitForIdleSync();Thread.sleep(100);
   test.runOnMainSync(()->{View longRow=described(a.mainFrame,"Long episode"),shortRow=described(a.mainFrame,"Short episode");check(longRow!=null&&shortRow!=null,"Episode tiles are present");View more=described(longRow,"Expand episode description");check(more instanceof ImageButton,"Description control is an arrow button");TextView summary=containing(longRow,"A long episode description.");check(summary!=null&&summary.getMaxLines()==2,"Description starts collapsed");check(!summary.getText().toString().contains("Instagram")&&!summary.getText().toString().contains("ad choices"),"Episode descriptions exclude promotional footer content");int[] rowAt=new int[2],arrowAt=new int[2];longRow.getLocationOnScreen(rowAt);more.getLocationOnScreen(arrowAt);check(arrowAt[0]+more.getWidth()/2>rowAt[0]+longRow.getWidth()*3/4,"Arrow is at the right edge");check(arrowAt[1]+more.getHeight()>=rowAt[1]+longRow.getHeight()-a.dp(20),"Arrow is at the lower edge");more.performClick();check(summary.getMaxLines()==Integer.MAX_VALUE&&more.isSelected(),"Arrow expands the full description");check(described(longRow,"Collapse episode description")==more,"Expanded arrow offers collapse");more.performClick();check(summary.getMaxLines()==2&&!more.isSelected(),"Arrow collapses the description");check(described(shortRow,"Expand episode description") instanceof ImageButton,"Short descriptions retain the arrow control");});
  }finally{test.runOnMainSync(a::finish);}

 }
}
