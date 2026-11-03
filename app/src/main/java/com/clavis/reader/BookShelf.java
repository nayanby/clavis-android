package com.clavis.reader;

import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

/** Local book metadata is read off the UI thread; rows are created on demand. */
final class BookShelf extends RecyclerView.Adapter<BookShelf.Holder> {
 static final java.util.concurrent.ExecutorService loader=java.util.concurrent.Executors.newSingleThreadExecutor();
 final MainActivity a;final ArrayList<Book> books;
 BookShelf(MainActivity a,ArrayList<Book> books){this.a=a;this.books=books;}
 static void show(MainActivity a,String kind){
  FrameLayout page=a.mainFrame;a.body.addView(a.text("Loading your books…",14,MainActivity.MUTED,false));
  loader.execute(()->{ArrayList<Book> all=Store.load(a),selected=new ArrayList<>();for(Book book:Store.newestFirst(all))if(book.author.equals(kind))selected.add(book);
   a.runOnUiThread(()->{if(a.isDestroyed()||a.isFinishing())return;Runnable render=()->{if(a.mainFrame!=page)return;a.books=all;a.body.removeAllViews();if(selected.isEmpty()){a.body.addView(a.text("Your shelf is waiting",20,MainActivity.INK,true));a.space(a.body,12);a.body.addView(a.button("Import "+(kind.equals("Audio")?"audiobooks":kind.toLowerCase()+" books"),()->a.pick(kind)));}else{RecyclerView list=new RecyclerView(a);list.setLayoutManager(new LinearLayoutManager(a));list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(a.dp(16),a.dp(8),a.dp(16),a.dp(100));list.setAdapter(new BookShelf(a,selected));View old=a.root.getChildAt(2);a.root.removeView(old);a.root.addView(list,2,new LinearLayout.LayoutParams(-1,0,1));}a.miniPlayer();ScrollChrome.install(a);};if(a.mainFrame==page)render.run();else if(a.navigation!=null)a.navigation.defer(page,render);});
  });
 }
 @Override public int getItemCount(){return books.size();}
 @Override public Holder onCreateViewHolder(ViewGroup parent,int type){FrameLayout frame=new FrameLayout(a);RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,-2);p.bottomMargin=a.dp(10);frame.setLayoutParams(p);return new Holder(frame);}
 @Override public void onBindViewHolder(Holder holder,int position){holder.frame.removeAllViews();holder.frame.addView(UiStyle.bookView(a,books.get(position)),new FrameLayout.LayoutParams(-1,-2));}
 @Override public void onViewRecycled(Holder holder){holder.frame.removeAllViews();}
 static final class Holder extends RecyclerView.ViewHolder{final FrameLayout frame;Holder(FrameLayout frame){super(frame);this.frame=frame;}}
}
