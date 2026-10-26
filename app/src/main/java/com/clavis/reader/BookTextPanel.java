package com.clavis.reader;
import android.widget.FrameLayout;
/** Audiobook text always comes from offline speech recognition. */
final class BookTextPanel extends FrameLayout {
 BookTextPanel(MainActivity activity,Book book){
  super(activity);
  setBackground(activity.bg(AppTheme.surface,16));
  setClipToOutline(true);
  InlineTranscriptView transcript=new InlineTranscriptView(activity,book);
  activity.liveText=transcript;
  addView(transcript,new LayoutParams(-1,-1));
 }
}
