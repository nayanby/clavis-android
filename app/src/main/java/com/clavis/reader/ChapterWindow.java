package com.clavis.reader;



import android.app.Dialog;

import android.graphics.*;

import android.graphics.drawable.ColorDrawable;

import android.view.*;

import android.widget.*;



/** Mirrors Cortex's 290dp white, 16dp-radius outline panel. */

final class ChapterWindow {

 static void show(MainActivity a,Book book){

  Dialog dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

  LinearLayout panel=a.column();panel.setBackground(a.bg(AppTheme.surface,16));panel.setClipToOutline(true);panel.setElevation(a.dp(10));

  LinearLayout header=a.row();header.setPadding(a.dp(12),a.dp(8),a.dp(12),a.dp(4));TextView title=a.text("CHAPTERS",12,MainActivity.MUTED,false);title.setLetterSpacing(.09f);title.setTypeface(null,Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));ImageButton close=UiStyle.icon(a,R.drawable.ic_outline,"Close chapters",dialog::dismiss,34);header.addView(close);panel.addView(header);

  ScrollView scroll=new ScrollView(a);scroll.setFillViewport(false);LinearLayout rows=a.column();rows.setPadding(0,0,0,a.dp(20));scroll.addView(rows);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));

  PlaybackService current=PlaybackService.live;int selected=current!=null&&current.uri.equals(book.uri)?current.chapterIndex:book.chapterIndex;

  View selectedRow=null;

  for(int i=0;i<book.chapterCount();i++){

   int index=i;String label=book.trackTitle(i).replaceFirst("^\\s*\\d+[\\s._-]+", "");TextView row=a.text(label,14,MainActivity.INK,true);row.setPadding(a.dp(12),a.dp(8),a.dp(12),a.dp(8));row.setMinHeight(a.dp(40));row.setGravity(Gravity.CENTER_VERTICAL);row.setMaxLines(2);row.setEllipsize(android.text.TextUtils.TruncateAt.END);row.setContentDescription(label);row.setFocusable(true);

   LinearLayout entry=a.row();entry.addView(row,new LinearLayout.LayoutParams(0,-2,1));TextView length=a.text("…",13,MainActivity.MUTED,false);length.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);length.setPadding(0,0,a.dp(12),0);entry.addView(length,new LinearLayout.LayoutParams(a.dp(66),-1));String track=book.trackUri(i);long cached=ChapterLengths.cached(a,track);if(cached>=0)length.setText(Store.time(book.trackLength(index,cached)));a.io.execute(()->{long ms=ChapterLengths.read(a,track);a.runOnUiThread(()->{if(dialog.isShowing())length.setText(ms>=0?Store.time(book.trackLength(index,ms)):"—");});});

   if(i==selected){entry.setBackgroundColor(AppTheme.accentSoft);row.setTypeface(a.serif,Typeface.BOLD);selectedRow=entry;}

   row.setOnClickListener(v->{PlaybackService s=PlaybackService.live;if(s!=null&&s.uri.equals(book.uri))s.jumpChapter(index,0);dialog.dismiss();});entry.setOnClickListener(v->row.performClick());rows.addView(entry,new LinearLayout.LayoutParams(-1,-2));

  }

  dialog.setContentView(panel);dialog.setCanceledOnTouchOutside(true);Window window=dialog.getWindow();if(window!=null){window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams p=window.getAttributes();p.dimAmount=.18f;p.gravity=Gravity.CENTER;window.setAttributes(p);}

  dialog.show();if(window!=null)window.setLayout(Math.min(a.dp(290),a.getResources().getDisplayMetrics().widthPixels-a.dp(32)),Math.round(a.getResources().getDisplayMetrics().heightPixels*.7f));

  View active=selectedRow;if(active!=null)scroll.post(()->scroll.scrollTo(0,Math.max(0,active.getTop()-a.dp(90))));

 }

}





