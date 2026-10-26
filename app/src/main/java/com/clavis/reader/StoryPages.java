package com.clavis.reader;
import android.text.*;
import java.util.ArrayList;
/** Paginates at rendered line boundaries, keeping every character in order. */
final class StoryPages {
 static final class Page {final int start,end;final String text;Page(String body,int start,int end){this.start=start;this.end=end;text=body.substring(start,end);}}
 static String text(ArticleContent article){StringBuilder out=new StringBuilder();for(ArticleContent.Block block:article.blocks){if(block.kind.equals("img")||block.kind.equals("caption"))continue;if(out.length()>0)out.append("\n\n");if(block.kind.equals("li"))out.append("• ");out.append(block.value);}return out.toString();}
 static CharSequence spaced(String text,int gap,int extra,float multiplier){SpannableString out=new SpannableString(text);for(int n=text.indexOf("\n\n");n>=0;n=text.indexOf("\n\n",n+2)){final int height=Math.max(1,Math.round((gap-extra)/multiplier));out.setSpan((android.text.style.LineHeightSpan)(value,start,end,spanStart,v,fm)->{fm.ascent=-height;fm.descent=0;fm.top=fm.ascent;fm.bottom=0;},n+1,n+2,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}return out;}
 static ArrayList<Page> paginate(String text,TextPaint paint,int width,int height,int extra,float multiplier,int gap){
  ArrayList<Page> pages=new ArrayList<>();if(text.isEmpty()||width<=0||height<=0)return pages;
  StaticLayout layout=StaticLayout.Builder.obtain(spaced(text,gap,extra,multiplier),0,text.length(),paint,width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).setLineSpacing(extra,multiplier).setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE).build();
  int first=0;while(first<layout.getLineCount()){
   int last=first,top=layout.getLineTop(first);while(last+1<layout.getLineCount()&&layout.getLineBottom(last+1)-top<=height)last++;
   // A standalone slide may wrap differently at its first line (especially bidi text).
   // Measure that exact substring rather than relying only on the full-story layout.
   while(last>first){int from=layout.getLineStart(first),to=last+1==layout.getLineCount()?text.length():layout.getLineEnd(last);StaticLayout page=StaticLayout.Builder.obtain(spaced(text.substring(from,to),gap,extra,multiplier),0,to-from,paint,width).setIncludePad(false).setLineSpacing(extra,multiplier).setBreakStrategy(android.graphics.text.LineBreaker.BREAK_STRATEGY_SIMPLE).build();if(page.getHeight()<=height)break;last--;}
   int start=layout.getLineStart(first),end=last+1==layout.getLineCount()?text.length():layout.getLineEnd(last);
   if(end>start)pages.add(new Page(text,start,end));first=last+1;
  }return pages;
 }
}

