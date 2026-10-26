package com.clavis.reader;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Short feedback uses the same surface, outline and 16dp corners as app windows. */
final class AppNotice {
 static Toast makeText(Context context,CharSequence message,int duration){
  Toast toast=Toast.makeText(context,message,duration);float density=context.getResources().getDisplayMetrics().density;TextView text=new TextView(context);text.setText(message);text.setTextColor(AppTheme.text);text.setTextSize(14);text.setTypeface(context.getResources().getFont(R.font.averia_serif_libre),Typeface.NORMAL);text.setPadding(Math.round(18*density),Math.round(14*density),Math.round(18*density),Math.round(14*density));text.setGravity(Gravity.CENTER);GradientDrawable background=new GradientDrawable();background.setColor(AppTheme.surface);background.setCornerRadius(16*density);background.setStroke(Math.max(1,Math.round(density)),AppTheme.line);text.setBackground(background);text.setElevation(8*density);toast.setView(text);toast.setGravity(Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL,0,Math.round(90*density));return toast;
 }
}
