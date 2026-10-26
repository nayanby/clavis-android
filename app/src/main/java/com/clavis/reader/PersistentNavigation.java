package com.clavis.reader;
import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.widget.*;
/** Shared navigation for the notebook activity, which uses its own content shell. */
public final class PersistentNavigation {
 public static void attach(Activity a,FrameLayout host){
  float density=a.getResources().getDisplayMetrics().density;
  LinearLayout pill=new LinearLayout(a);pill.setGravity(Gravity.CENTER);pill.setPadding((int)(16*density),0,(int)(16*density),0);pill.setBackgroundResource(R.drawable.bg_bottom_navigation_pill);pill.setElevation(6*density);
  FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams((int)(320*density),(int)(56*density),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);lp.bottomMargin=(int)(10*density);host.addView(pill,lp);
  String[] routes={"Home","Audio","Podcasts","Browser","Downloads"};String[] labels={"Home","Audiobooks","Podcasts","Browser","Downloads"};int[] icons={R.drawable.ic_home,R.drawable.ic_listen_audio,R.drawable.ic_podcast,R.drawable.ic_browser_globe,R.drawable.ic_download_books};
  for(int i=0;i<routes.length;i++){String route=routes[i];ImageButton button=new ImageButton(a);button.setImageResource(icons[i]);button.setImageTintList(ColorStateList.valueOf(AppTheme.muted));button.setBackgroundColor(android.graphics.Color.TRANSPARENT);button.setContentDescription(labels[i]);int pad=(int)(12*density);button.setPadding(pad,pad,pad,pad);pill.addView(button,new LinearLayout.LayoutParams(0,-1,1));button.setOnClickListener(v->{Intent intent=route.equals("Downloads")?new Intent(a,DownloadsActivity.class):new Intent(a,MainActivity.class).putExtra("navigationRoute",route).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);a.startActivity(intent);});}
 }
}