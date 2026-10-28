package com.clavis.reader;
import android.graphics.Typeface;import android.widget.TextView;
/** User-supplied Noto Serif Devanagari; retain complete vowel/conjunct metrics. */
final class HindiTypography {
 private static Typeface regular,bold;
 static boolean contains(String text){if(text==null)return false;for(int n=0;n<text.length();n++){char c=text.charAt(n);if(c>='\u0900'&&c<='\u097f'||c>='\ua8e0'&&c<='\ua8ff')return true;}return false;}
 static synchronized Typeface face(android.content.Context context,boolean heavy){if(regular==null)regular=Typeface.createFromAsset(context.getAssets(),"fonts/NotoSerifDevanagari-Regular.ttf");if(bold==null)bold=Typeface.createFromAsset(context.getAssets(),"fonts/NotoSerifDevanagari-Bold.ttf");return heavy?bold:regular;}
 static void apply(TextView view,String text){if(contains(text)){view.setTypeface(face(view.getContext(),view.getTypeface()!=null&&view.getTypeface().isBold()));view.setIncludeFontPadding(true);if(android.os.Build.VERSION.SDK_INT>=28)view.setFallbackLineSpacing(true);}}
 static void apply(TextView view,String text,Typeface fallback){view.setTypeface(fallback);apply(view,text);}
}
