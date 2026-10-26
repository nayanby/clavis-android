package com.clavis.reader;
import android.content.Context;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/** Horizontal gestures toggle the cover; vertical gestures stay with the transcript scroll view. */
final class SwipeCoverFrame extends FrameLayout {
 private float startX,startY;private final float threshold;private Runnable toggle=()->{};
 SwipeCoverFrame(Context context){super(context);threshold=45*getResources().getDisplayMetrics().density;setClipChildren(false);setClipToPadding(false);}
 void setToggle(Runnable toggle){this.toggle=toggle;}
 @Override public boolean onInterceptTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getX();startY=event.getY();}if(event.getActionMasked()==MotionEvent.ACTION_MOVE){float dx=Math.abs(event.getX()-startX),dy=Math.abs(event.getY()-startY);if(dx>threshold&&dx>dy*1.5f){getParent().requestDisallowInterceptTouchEvent(true);return true;}}return false;}
 @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){startX=event.getX();startY=event.getY();return true;}if(event.getActionMasked()==MotionEvent.ACTION_UP){float dx=Math.abs(event.getX()-startX),dy=Math.abs(event.getY()-startY);if(dx>threshold&&dx>dy*1.5f)toggle.run();performClick();return true;}return true;}
 @Override public boolean performClick(){super.performClick();return true;}
}

