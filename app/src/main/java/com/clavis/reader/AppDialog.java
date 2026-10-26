package com.clavis.reader;
import android.app.AlertDialog;
import android.content.Context;
import android.view.View;
import android.widget.*;
/** Shared content insets for platform dialogs, including search and bookmark inputs. */
public final class AppDialog {
 public static final class Builder extends AlertDialog.Builder {
  public Builder(Context context){super(context);}
  @Override public AlertDialog.Builder setView(View view){
   Context c=getContext();float d=c.getResources().getDisplayMetrics().density;
   if(view instanceof EditText){EditText input=(EditText)view;input.setMinHeight(Math.round(48*d));input.setTextColor(AppTheme.text);input.setHintTextColor(AppTheme.muted);input.setPadding(Math.round(12*d),Math.round(10*d),Math.round(12*d),Math.round(10*d));}
   FrameLayout inset=new FrameLayout(c);
   int horizontal=Math.round(24*d),top=Math.round(12*d),bottom=Math.round(12*d);
   // Existing form panels already provide their own content gutter.
   boolean padded=view instanceof android.view.ViewGroup&&view.getPaddingLeft()>=Math.round(16*d)&&view.getPaddingRight()>=Math.round(16*d);
   inset.setPadding(padded?0:horizontal,padded?0:top,padded?0:horizontal,bottom);
   inset.addView(view,new FrameLayout.LayoutParams(-1,-2));
   return super.setView(inset);
  }
  @Override public AlertDialog show(){AlertDialog dialog=super.show();ListView list=dialog.getListView();if(list!=null){float d=getContext().getResources().getDisplayMetrics().density;list.setPadding(Math.round(8*d),Math.round(8*d),Math.round(8*d),Math.round(12*d));list.setClipToPadding(false);}return dialog;}
 }
}