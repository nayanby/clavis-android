package com.clavis.reader;
import android.app.Activity;
import android.content.Context;

final class AppTheme {
 static boolean dark=true;
 static int background=0xff20272a,surface=0xff2b3336,raised=0xff354044,line=0xff465256,text=0xffedf2f2,muted=0xffa9b6bb,accent=0xff8a72ad,accentSoft=0xff40545b;
 static Context themedContext(Context base){android.content.res.Configuration config=new android.content.res.Configuration(base.getResources().getConfiguration());config.uiMode=(config.uiMode & ~android.content.res.Configuration.UI_MODE_NIGHT_MASK)|(isDark(base)?android.content.res.Configuration.UI_MODE_NIGHT_YES:android.content.res.Configuration.UI_MODE_NIGHT_NO);return base.createConfigurationContext(config);}
 static boolean isDark(Context c){return c.getSharedPreferences("appearance",0).getBoolean("dark",true);}
 static void configureColors(Context context){dark=isDark(context);if(dark){background=0xff20272a;surface=0xff2b3336;raised=0xff354044;line=0xff465256;text=0xffedf2f2;muted=0xffa9b6bb;accentSoft=0xff40545b;}else{background=0xffedf2f3;surface=0xffffffff;raised=0xffe5edef;line=0xffe3e9eb;text=0xff292b30;muted=0xff5f6c73;accentSoft=0xffdce9ed;}accent=0xff8a72ad;MainActivity.INK=text;MainActivity.MUTED=muted;MainActivity.PAPER=background;MainActivity.LINE=line;}
 static void configure(Activity a){configureColors(a);a.setTheme(dark?R.style.Theme_Clavis:R.style.Theme_Clavis_Light);}
 static void tintProgress(android.widget.ProgressBar bar){
  android.content.res.ColorStateList color=new android.content.res.ColorStateList(new int[][]{new int[]{-android.R.attr.state_enabled},new int[]{}},new int[]{isDark(bar.getContext())?0xff78858b:0xff89959b,bar.getContext().getColor(R.color.control_accent)});
  bar.setProgressTintList(color);bar.setSecondaryProgressTintList(color);bar.setIndeterminateTintList(color);
  bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(isDark(bar.getContext())?0xff59676d:0xffdbe2e6));
  if(bar instanceof android.widget.SeekBar)((android.widget.SeekBar)bar).setThumbTintList(color);
 }
 static void setDark(Context c,boolean enabled){c.getSharedPreferences("appearance",0).edit().putBoolean("dark",enabled).apply();}
}
