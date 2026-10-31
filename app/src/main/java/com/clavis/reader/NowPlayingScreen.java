package com.clavis.reader;

import android.app.*;

import android.graphics.*;

import android.view.*;

import android.widget.*;

final class NowPlayingScreen {

 static void show(MainActivity a,Book b){

  boolean podcast=b.author.equals("Podcast");a.screen=podcast?"PodcastPlayer":"Player";a.selected=b;a.shell(podcast?"Podcast player":"Now playing");a.bar.addView(UiStyle.icon(a,R.drawable.ic_toolbar_more,"Player menu",()->new AppDialog.Builder(a).setTitle("Playback").setItems(new String[]{"Bookmarks","Export bookmarks","Sleep timer","Playback settings","Undo last seek","Next book"},(d,n)->{PlaybackService s=PlaybackService.live;if(n==0)PlayerDialogs.bookmarks(a,b);else if(n==1)PlayerDialogs.exportBookmarks(a,b);else if(n==2)PlayerDialogs.sleep(a);else if(n==3)PlayerDialogs.settings(a);else if(n==4&&s!=null)s.undoSeek();else if(n==5&&s!=null){s.nextBook();a.openActivePlayer();}}).show(),34));

  a.body.setPadding(a.dp(16),a.dp(8),a.dp(16),a.dp(12));a.body.setGravity(Gravity.CENTER_HORIZONTAL);
  android.content.res.Configuration config=a.getResources().getConfiguration();boolean wide=config.screenWidthDp>config.screenHeightDp;
  int artHeight=Math.max(100,Math.min(220,config.screenHeightDp-104-Math.round(300*config.fontScale)));
  if(wide)artHeight=Math.max(100,Math.min(200,config.screenHeightDp-190));
  final int coverHeight=artHeight;
  LinearLayout layout=wide?new LinearLayout(a):a.body;if(wide){layout.setOrientation(LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);a.body.setGravity(Gravity.CENTER);a.body.addView(layout,new LinearLayout.LayoutParams(-1,-2));}
  LinearLayout media=a.column(),playback=a.column();media.setGravity(Gravity.CENTER);playback.setGravity(Gravity.CENTER_HORIZONTAL);
  layout.addView(media,new LinearLayout.LayoutParams(wide?0:-1,-2,wide?0.38f:1));LinearLayout.LayoutParams playbackParams=new LinearLayout.LayoutParams(wide?0:-1,-2,wide?0.62f:0);if(wide)playbackParams.leftMargin=a.dp(20);layout.addView(playback,playbackParams);
  TextView bookTitle=a.text(b.title,18,MainActivity.INK,true);bookTitle.setGravity(Gravity.CENTER);bookTitle.setMaxLines(2);bookTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);media.addView(bookTitle);a.space(media,4);

  a.remaining=a.text(podcast?"Loading podcast…":"Loading audiobook…",14,MainActivity.INK,true);

  a.seek=new SeekBar(a);AppTheme.tintProgress(a.seek);a.seek.setMax(1000);

  a.chapter=a.text(podcast?b.title:"Chapter "+(b.chapterIndex+1)+" of "+b.chapterCount(),13,MainActivity.MUTED,true);a.chapter.setGravity(Gravity.CENTER);a.chapter.setMaxLines(2);a.chapter.setEllipsize(android.text.TextUtils.TruncateAt.END);a.chapter.setOnClickListener(v->{if(podcast)a.library("Podcasts");else PlayerDialogs.chapters(a,b);});

  media.addView(a.chapter);a.space(media,8);CoverArtView cover=new CoverArtView(a);a.body.setClipChildren(false);a.body.setClipToPadding(false);TextView placeholder=a.text("♫",42,MainActivity.INK,true);placeholder.setGravity(Gravity.CENTER);placeholder.setPadding(a.dp(16),a.dp(16),a.dp(16),a.dp(16));placeholder.setContentDescription(b.title+". No cover artwork");cover.setBackground(a.bg(AppTheme.surface,16));cover.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));int side=Math.min(coverHeight,(int)(a.getResources().getDisplayMetrics().widthPixels/a.getResources().getDisplayMetrics().density)-80);FrameLayout artSlot=new FrameLayout(a);artSlot.addView(cover,new FrameLayout.LayoutParams(a.dp(side),a.dp(side),Gravity.CENTER));artSlot.setContentDescription("Artwork");media.addView(artSlot,new LinearLayout.LayoutParams(-1,a.dp(coverHeight)));

  a.io.execute(()->{Bitmap bitmap=Artwork.load(a,b);if(bitmap!=null)a.runOnUiThread(()->{if(a.isDestroyed()||!(a.screen.equals("Player")||a.screen.equals("PodcastPlayer"))||a.selected!=b){bitmap.recycle();return;}cover.setArtwork(bitmap,side,coverHeight);});});

  a.space(playback,8);playback.addView(a.remaining);playback.addView(a.seek,new LinearLayout.LayoutParams(-1,a.dp(48)));LinearLayout controls=a.row();controls.setGravity(Gravity.CENTER);

  PlayerButton back=new PlayerButton(a,"back",()->{PlaybackService s=PlaybackService.live;if(s!=null)s.skip(-s.skipSeconds());});back.setContentDescription("Skip backward");

  a.playButton=new PlayerButton(a,"play",()->{PlaybackService s=PlaybackService.live;if(s!=null){if(s.playing())s.pause();else s.play();}});

  PlayerButton forward=new PlayerButton(a,"forward",()->{PlaybackService s=PlaybackService.live;if(s!=null)s.skip(s.skipSeconds());});forward.setContentDescription("Skip forward");

  PlayerButton previousChapter=new PlayerButton(a,"previous",()->{if(podcast){FeedScreens.nextEpisode(a,b,-1);return;}PlaybackService s=PlaybackService.live;if(s!=null&&s.chapterIndex>0)s.jumpChapter(s.chapterIndex-1,0);});previousChapter.setContentDescription(podcast?"Previous episode":"Previous chapter");PlayerButton nextChapter=new PlayerButton(a,"next",()->{if(podcast){FeedScreens.nextEpisode(a,b,1);return;}PlaybackService s=PlaybackService.live;if(s!=null&&s.chapterIndex+1<s.chapterCount())s.jumpChapter(s.chapterIndex+1,0);});nextChapter.setContentDescription(podcast?"Next episode":"Next chapter");controls.addView(previousChapter,new LinearLayout.LayoutParams(0,a.dp(48),1));controls.addView(back,new LinearLayout.LayoutParams(0,a.dp(64),1.2f));controls.addView(a.playButton,new LinearLayout.LayoutParams(a.dp(76),a.dp(76)));controls.addView(forward,new LinearLayout.LayoutParams(0,a.dp(64),1.2f));controls.addView(nextChapter,new LinearLayout.LayoutParams(0,a.dp(48),1));a.time=a.text("0:00 / 0:00",12,MainActivity.MUTED,false);playback.addView(a.time);a.space(playback,4);playback.addView(controls,new LinearLayout.LayoutParams(-1,-2));a.space(playback,6);

  LinearLayout tools=a.row();a.speedButton=a.button("Speed: "+b.speed+"x",()->new AppDialog.Builder(a).setTitle("Playback speed").setItems(new String[]{"0.5x","0.75x","1x","1.25x","1.5x","1.75x","2x","2.5x","3x"},(d,n)->{if(PlaybackService.live!=null)PlaybackService.live.setSpeed(new float[]{.5f,.75f,1,1.25f,1.5f,1.75f,2,2.5f,3}[n]);}).show());TextView chapters=a.button(podcast?"Episodes":"Chapters",()->{if(podcast)a.library("Podcasts");else PlayerDialogs.chapters(a,b);});TextView bookmark=a.button("Bookmark",()->PlayerDialogs.addBookmark(a,b));

  for(TextView t:new TextView[]{a.speedButton,chapters,bookmark}){t.setTextSize(14);t.setMaxLines(2);t.setBackgroundColor(Color.TRANSPARENT);t.setPaintFlags(t.getPaintFlags()|Paint.UNDERLINE_TEXT_FLAG);tools.addView(t,new LinearLayout.LayoutParams(0,-2,1));}playback.addView(tools,new LinearLayout.LayoutParams(-1,-2));a.space(playback,4);

  LinearLayout volumeRow=a.row();volumeRow.setPadding(a.dp(4),a.dp(6),a.dp(4),a.dp(6));ImageView volumeIcon=new ImageView(a);volumeIcon.setImageResource(R.drawable.ic_listen_audio);volumeIcon.setImageTintList(android.content.res.ColorStateList.valueOf(MainActivity.INK));volumeIcon.setContentDescription("Volume");volumeRow.addView(volumeIcon,new LinearLayout.LayoutParams(a.dp(24),a.dp(24)));SeekBar volume=new SeekBar(a);AppTheme.tintProgress(volume);volume.setMax(100);volume.setProgress(PlayerDialogs.prefs(a).getInt("volume",80));volume.setContentDescription("Volume");volumeRow.addView(volume,new LinearLayout.LayoutParams(0,a.dp(48),1));TextView percent=a.text(volume.getProgress()+"%",12,MainActivity.MUTED,false);percent.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);volumeRow.addView(percent,new LinearLayout.LayoutParams(a.dp(40),a.dp(40)));volume.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar bar,int p,boolean user){PlaybackService s=PlaybackService.live;if(s!=null&&s.player!=null&&s.ready)s.player.setVolume(p/100f,p/100f);PlayerDialogs.prefs(a).edit().putInt("volume",p).apply();percent.setText(p+"%");}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){}});playback.addView(volumeRow,new LinearLayout.LayoutParams(-1,-2));

  a.seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int p,boolean user){}public void onStartTrackingTouch(SeekBar v){a.dragging=true;}public void onStopTrackingTouch(SeekBar v){PlaybackService s=PlaybackService.live;if(s!=null)s.seek(s.duration()*v.getProgress()/1000);a.dragging=false;}});

  final boolean podcastPrevious=podcast&&FeedScreens.adjacent(a,b,-1)!=null,podcastNext=podcast&&FeedScreens.adjacent(a,b,1)!=null;
  a.tick=new Runnable(){public void run(){PlaybackService s=PlaybackService.live;if(!PlaybackService.playbackError.isEmpty())a.remaining.setText(PlaybackService.playbackError);if(s!=null&&s.uri.equals(b.uri)&&s.ready){long minutes=(long)Math.ceil((s.duration()-s.position())/60000.0);a.remaining.setText(minutes+" minute"+(minutes==1?"":"s")+(podcast?" left in episode":" left in chapter"));a.chapter.setText(podcast?b.title:"Chapter "+(s.chapterIndex+1)+" of "+s.chapterCount()+" · "+s.chapterTitle());((PlayerButton)a.playButton).setPlaying(s.playing());a.playButton.setContentDescription(s.playing()?"Pause":"Play");a.speedButton.setText("Speed: "+s.speed+"x");a.time.setText(Store.time(s.position())+" / "+Store.time(s.duration()));previousChapter.setEnabled(podcast?podcastPrevious:s.chapterIndex>0);previousChapter.setAlpha(previousChapter.isEnabled()?1f:.55f);nextChapter.setEnabled(podcast?podcastNext:s.chapterIndex+1<s.chapterCount());nextChapter.setAlpha(nextChapter.isEnabled()?1f:.55f);back.setText(""+s.skipSeconds());forward.setText(""+s.skipSeconds());back.invalidate();forward.invalidate();a.playButton.invalidate();if(!a.dragging)a.seek.setProgress((int)(s.duration()>0?s.position()*1000/s.duration():0));}a.handler.postDelayed(this,500);}};a.handler.post(a.tick);

 }

}
