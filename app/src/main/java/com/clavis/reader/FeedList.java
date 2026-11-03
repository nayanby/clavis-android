package com.clavis.reader;

import android.view.*;
import android.widget.*;
import androidx.recyclerview.widget.*;
import java.util.*;

/** Inflate only visible podcast episodes or subscribed sources. */
final class FeedList extends RecyclerView.Adapter<FeedList.Holder> {
 final MainActivity a;final String kind;final ArrayList<FeedStore.Feed> feeds;final FeedStore.Feed combined;
 FeedList(MainActivity a,String kind,ArrayList<FeedStore.Feed> feeds,FeedStore.Feed combined){this.a=a;this.kind=kind;this.feeds=feeds;this.combined=combined;}
 static void attach(MainActivity a,String kind,ArrayList<FeedStore.Feed> feeds,FeedStore.Feed combined){
  RecyclerView list=new RecyclerView(a);list.setLayoutManager(new LinearLayoutManager(a));list.setItemAnimator(null);list.setClipToPadding(false);list.setPadding(a.dp(16),a.dp(12),a.dp(16),a.dp(100));list.setAdapter(new FeedList(a,kind,feeds,combined));
  int index=FeedScreens.contentIndex(a);View old=a.root.getChildAt(index);a.root.removeView(old);a.root.addView(FeedRefresh.wrap(a,list,kind,null),index,new LinearLayout.LayoutParams(-1,0,1));
 }
 @Override public int getItemCount(){return combined==null?feeds.size():combined.items.size();}
 @Override public Holder onCreateViewHolder(ViewGroup parent,int type){FrameLayout frame=new FrameLayout(a);RecyclerView.LayoutParams p=new RecyclerView.LayoutParams(-1,-2);p.bottomMargin=a.dp(10);frame.setLayoutParams(p);return new Holder(frame);}
 @Override public void onBindViewHolder(Holder holder,int position){holder.frame.removeAllViews();View row;if(combined==null)row=FeedScreens.sourceView(a,feeds.get(position));else{FeedStore.Item item=combined.items.get(position);row=FeedScreens.entryView(a,combined.origins.getOrDefault(item,combined),item);}holder.frame.addView(row,new FrameLayout.LayoutParams(-1,-2));}
 @Override public void onViewRecycled(Holder holder){holder.frame.removeAllViews();}
 static final class Holder extends RecyclerView.ViewHolder{final FrameLayout frame;Holder(FrameLayout frame){super(frame);this.frame=frame;}}
}
