package com.clavis.reader;
import android.os.Bundle;
public final class CatalogActivity extends MainActivity {
 @Override public void onCreate(Bundle state){super.onCreate(state);UiStyle.library(this,getIntent().getBooleanExtra("ebook",false)?"EPUB":"Audio",true);bar.getChildAt(0).setOnClickListener(v->finish());}
 @Override public void onBackPressed(){finish();}
 @Override protected void onResume(){super.onResume();if(root!=null&&(screen.equals("EPUB")||screen.equals("Audio")||screen.equals("PDF"))){UiStyle.library(this,screen,bookBrowse);bar.getChildAt(0).setOnClickListener(v->finish());}}
}

