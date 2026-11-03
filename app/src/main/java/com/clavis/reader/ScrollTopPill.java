package com.clavis.reader;

import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Brief, unobtrusive shortcut while moving toward earlier content. */
final class ScrollTopPill {
 final MainActivity a;final View content;final FrameLayout host;final LinearLayout pill;final java.util.function.IntSupplier headerHeight;
 boolean visible,closed,returning;
 final Runnable dismiss=()->show(false);
 ScrollTopPill(MainActivity a,View content,java.util.function.IntSupplier headerHeight){
  this.a=a;this.content=content;this.headerHeight=headerHeight;host=a.mainFrame;
  pill=a.row();pill.setTag("floating-go-to-top");pill.setGravity(Gravity.CENTER);pill.setPadding(a.dp(12),a.dp(6),a.dp(12),a.dp(6));pill.setBackground(a.bg(AppTheme.surface,18));pill.setElevation(a.dp(5));pill.setVisibility(View.GONE);pill.setContentDescription("Go to top");
  ImageView arrow=new ImageView(a);arrow.setImageResource(R.drawable.ic_episode_expand);arrow.setRotation(180);arrow.setImageTintList(ColorStateList.valueOf(AppTheme.muted));pill.addView(arrow,new LinearLayout.LayoutParams(a.dp(18),a.dp(18)));
  TextView label=a.text("Go to top",12,AppTheme.muted,true);LinearLayout.LayoutParams text=new LinearLayout.LayoutParams(-2,-2);text.leftMargin=a.dp(6);pill.addView(label,text);
  host.addView(pill,new FrameLayout.LayoutParams(-2,a.dp(36),Gravity.TOP|Gravity.CENTER_HORIZONTAL));
  pill.setOnClickListener(v->{if(a.mainFrame!=host)return;returning=true;FeedScreens.goToTop(a);show(false);});
 }
 void position(){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)pill.getLayoutParams();int top=headerHeight.getAsInt()+a.dp(8);if(p.topMargin!=top){p.topMargin=top;pill.setLayoutParams(p);}}
 boolean pastFirstTwoPages(){if(content instanceof androidx.recyclerview.widget.RecyclerView){androidx.recyclerview.widget.RecyclerView list=(androidx.recyclerview.widget.RecyclerView)content;int extent=list.computeVerticalScrollExtent();return extent>0&&(long)list.computeVerticalScrollOffset()>=2L*extent;}int viewport=content.getHeight()-content.getPaddingTop()-content.getPaddingBottom();return viewport>0&&(long)content.getScrollY()>=2L*viewport;}
 void scrolled(int dy){if(closed)return;if(!content.canScrollVertically(-1)){returning=false;show(false);return;}if(!pastFirstTwoPages()){show(false);return;}if(returning)return;if(dy<0&&a.mainFrame==host&&host.isShown())show(true);else if(dy>0)show(false);}
 void show(boolean value){if(closed)return;a.handler.removeCallbacks(dismiss);if(value)a.handler.postDelayed(dismiss,2200);position();if(visible==value)return;visible=value;pill.animate().cancel();if(value){pill.setVisibility(View.VISIBLE);pill.setTranslationY(-a.dp(12));pill.animate().translationY(0).setDuration(160).start();}else pill.animate().translationY(-a.dp(12)).setDuration(140).withEndAction(()->{if(!visible)pill.setVisibility(View.GONE);}).start();}
 void close(){closed=true;a.handler.removeCallbacks(dismiss);pill.animate().cancel();host.post(()->host.removeView(pill));}
}
