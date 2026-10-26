package com.clavis.reader;
import android.app.Activity;
import android.view.*;
import android.widget.FrameLayout;
final class ScreenMotion {
 static void replace(Activity a,View previous,View incoming){
  if(previous==null||previous==incoming||!android.animation.ValueAnimator.areAnimatorsEnabled())return;
  ViewGroup parent=(ViewGroup)previous.getParent();if(parent!=null)parent.removeView(previous);
  FrameLayout host=a.findViewById(android.R.id.content);host.addView(previous,new FrameLayout.LayoutParams(-1,-1));
  incoming.setAlpha(0f);incoming.animate().alpha(1f).setDuration(180).start();
  previous.animate().alpha(0f).setDuration(180).withEndAction(()->host.removeView(previous)).start();
 }
}