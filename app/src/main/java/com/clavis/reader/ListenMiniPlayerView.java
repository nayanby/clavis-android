package com.clavis.reader;
import android.view.*;import android.widget.*;
/** Narration survives reader Back; explicit Close stops and dismisses it. */
final class ListenMiniPlayerView extends LinearLayout {
 final TextView title,subtitle;final PlayerButton play;final MainActivity a;
 final Runnable refresh=new Runnable(){public void run(){update();postDelayed(this,500);}};
 ListenMiniPlayerView(MainActivity a){super(a);this.a=a;setContentDescription("Narration mini player");setOrientation(HORIZONTAL);setGravity(Gravity.CENTER_VERTICAL);setPadding(a.dp(12),a.dp(8),a.dp(8),a.dp(8));setBackground(a.bg(AppTheme.surface,12));
  ImageView icon=new ImageView(a);icon.setImageResource(R.drawable.ic_listen_audio);icon.setImageTintList(android.content.res.ColorStateList.valueOf(AppTheme.accent));icon.setPadding(a.dp(10),a.dp(10),a.dp(10),a.dp(10));icon.setBackground(a.bg(AppTheme.raised,8));addView(icon,new LayoutParams(a.dp(46),a.dp(50)));
  LinearLayout labels=a.column();labels.setPadding(a.dp(8),0,a.dp(4),0);title=a.text("",14,MainActivity.INK,false);title.setTypeface(null,android.graphics.Typeface.BOLD);title.setSingleLine();title.setEllipsize(android.text.TextUtils.TruncateAt.END);subtitle=a.text("",12,MainActivity.MUTED,false);subtitle.setSingleLine();subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);labels.addView(title);labels.addView(subtitle);addView(labels,new LayoutParams(0,-2,1));
  play=new PlayerButton(a,"play",()->{ListenService s=ListenService.live;if(s!=null){if(s.playing)s.pause();else s.play();update();}});addView(play,new LayoutParams(a.dp(46),a.dp(46)));
  addView(UiStyle.icon(a,R.drawable.cortex_ic_close,"Close narration",()->{ListenService s=ListenService.live;if(s!=null)s.stop();setVisibility(GONE);},40));
  setOnClickListener(v->{ListenService s=ListenService.live;if(s!=null&&s.file!=null)Listen.open(a,s.file);});update();
 }
 void update(){ListenService s=ListenService.live;if(s==null||s.story==null){setVisibility(GONE);return;}title.setText(s.story.title);subtitle.setText(s.story.label()+" · "+s.speed+"×");play.setPlaying(s.playing);play.setContentDescription(s.playing?"Pause narration":"Play narration");}
 @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(refresh);}
 @Override protected void onDetachedFromWindow(){removeCallbacks(refresh);super.onDetachedFromWindow();}
}
