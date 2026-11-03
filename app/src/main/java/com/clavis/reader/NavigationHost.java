package com.clavis.reader;

import android.graphics.Insets;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.*;

/** One activity window, one navigation pill, and retained section views. */
final class NavigationHost {
 final MainActivity a;
 final FrameLayout window, pages;
 final LinkedHashMap<String,Page> saved=new LinkedHashMap<>();
 final LinkedHashMap<String,Page> feedPages=new LinkedHashMap<>();
 FrameLayout active,outgoing;String activeRoute="Home";
 boolean feedTabMotion;final ArrayList<View> movingContent=new ArrayList<>();
 boolean bottomReady;final java.util.HashMap<View,Runnable> deferred=new java.util.HashMap<>();
 NavigationHost(MainActivity a){
  this.a=a;window=new FrameLayout(a);pages=new FrameLayout(a);
  window.setBackgroundColor(AppTheme.background);window.addView(pages,new FrameLayout.LayoutParams(-1,-1));
  window.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
  a.setContentView(window);
 }
 static String key(String screen,boolean browse){
  if(screen.equals("Home")||screen.equals("Downloads")||screen.equals("News")||screen.equals("Journals"))return screen;
  if(browse&&(screen.equals("Audio")||screen.equals("EPUB")||screen.equals("PDF")))return "Browser";
  if(screen.equals("Audio")||screen.equals("Player"))return "Audio";
  if(screen.equals("PodcastPlayer")||screen.startsWith("Podcasts"))return "Podcasts";
  return "";
 }
 void settle(){for(View content:movingContent){content.animate().cancel();content.setTranslationX(0);}movingContent.clear();for(int n=pages.getChildCount()-1;n>=0;n--){View page=pages.getChildAt(n);page.animate().cancel();page.setTranslationX(0);if(page!=active){if(retains(page))page.setVisibility(View.GONE);else pages.removeViewAt(n);}}outgoing=null;}
 int direction(String route){Page previous=saved.get(route);if(route.equals(activeRoute)&&previous!=null&&previous.sources!=a.feedSources)return a.feedSources?1:-1;List<String> order=Arrays.asList("Home","Audio","Podcasts","News","Journals","Browser","Downloads");int from=order.indexOf(activeRoute),to=order.indexOf(route);return route.equals("Home")||from>=0&&to>=0&&to<from?-1:1;}
 void slide(FrameLayout from,FrameLayout to,int direction){to.setVisibility(View.VISIBLE);to.bringToFront();int width=pages.getWidth();if(feedTabMotion&&from!=null){to.setTranslationX(0);from.setVisibility(View.GONE);if(!retains(from))pages.removeView(from);to.post(()->{if(active!=to)return;View content=feedContent(to);if(content!=null&&android.animation.ValueAnimator.areAnimatorsEnabled()){movingContent.add(content);content.setTranslationX(direction*width);content.animate().translationX(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(()->movingContent.remove(content)).start();}});return;}if(from==null||width<=0||!android.animation.ValueAnimator.areAnimatorsEnabled()){to.setTranslationX(0);if(from!=null){if(retains(from))from.setVisibility(View.GONE);else pages.removeView(from);}return;}outgoing=from;to.setTranslationX(direction*width);to.animate().translationX(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();from.animate().translationX(-direction*width*.22f).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(()->{from.setTranslationX(0);if(from!=active){if(retains(from))from.setVisibility(View.GONE);else pages.removeView(from);}if(outgoing==from)outgoing=null;}).start();}
 static View feedContent(FrameLayout frame){if(frame.getChildCount()>0&&frame.getChildAt(0) instanceof LinearLayout){LinearLayout root=(LinearLayout)frame.getChildAt(0);for(int n=0;n<root.getChildCount();n++){View child=root.getChildAt(n);if(child.getLayoutParams() instanceof LinearLayout.LayoutParams&&((LinearLayout.LayoutParams)child.getLayoutParams()).weight>0)return child;}}return null;}
 void mount(FrameLayout frame){settle();FrameLayout from=active;String route=key(a.screen,a.bookBrowse);int motion=direction(route);frame.setOnApplyWindowInsetsListener(null);active=frame;activeRoute=route;pages.addView(frame,new FrameLayout.LayoutParams(-1,-1));slide(from,frame,motion);
  final String screen=a.screen;final boolean browse=a.bookBrowse;
  frame.post(()->{if(active!=frame)return;String target=key(screen,browse);if(!target.isEmpty()){Page old=saved.put(target,new Page(a));if(old!=null&&old.frame!=frame&&old.frame!=outgoing){if(!retains(old.frame))pages.removeView(old.frame);}}selection();});
 }
 void bottom(){if(!bottomReady){bottomReady=true;UiStyle.bottom(a,window);}selection();}
 void selection(){String route=key(a.screen,a.bookBrowse);for(String tab:new String[]{"Home","Audio","Podcasts","News","Journals"}){View view=window.findViewWithTag("bottom:"+tab);if(view instanceof ImageButton){boolean on=tab.equals(route);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(on?AppTheme.text:AppTheme.surface);bg.setCornerRadius(a.dp(20));view.setBackground(bg);((ImageButton)view).setImageTintList(null);view.setSelected(on);}}}
 boolean show(String route){Page page=saved.get(route);if(page==null)return false;if(active==page.frame)return true;settle();FrameLayout from=active;int motion=route.equals(activeRoute)&&page.sources!=a.feedSources?(page.sources?1:-1):direction(route);if(a.tick!=null)a.handler.removeCallbacks(a.tick);page.restore(a);active=page.frame;activeRoute=route;Runnable render=deferred.remove(active);if(render!=null)render.run();slide(from,active,motion);selection();if(a.tick!=null)a.handler.post(a.tick);a.miniPlayer();return true;}
 void rememberFeed(String kind,boolean sources){feedPages.put(kind+":"+sources,new Page(a));}
 void invalidateFeed(String kind){feedPages.remove(kind+":false");feedPages.remove(kind+":true");}
 boolean showFeed(String kind,boolean sources){Page target=feedPages.get(kind+":"+sources);if(target==null)return false;saved.put(kind,target);return show(kind);}
 boolean retains(View view){for(Page page:feedPages.values())if(page.body==view||page.frame==view)return true;for(Page page:saved.values())if(page.body==view||page.frame==view)return true;return false;}

 void defer(View frame,Runnable render){if(retains(frame))deferred.put(frame,render);}
 void invalidateBooks(){for(String key:new String[]{"Home","Audio","Browser"}){Page page=saved.remove(key);if(page!=null&&page.frame!=active)pages.removeView(page.frame);}}
 void close(){saved.clear();feedPages.clear();deferred.clear();}
 static final class Page {
  final FrameLayout frame;final LinearLayout root,body,bar;final String screen,query;final boolean browse,sources;final Book selected;final ArrayList<Book> books;final FeedStore.Feed feed;
  final TextView play,remaining,chapter,time,speed;final SeekBar seek;final Runnable tick;
  Page(MainActivity a){frame=a.mainFrame;root=a.root;body=a.body;bar=a.bar;screen=a.screen;query=a.query;browse=a.bookBrowse;sources=a.feedSources;selected=a.selected;books=a.books;feed=a.openFeed;play=a.playButton;remaining=a.remaining;chapter=a.chapter;time=a.time;speed=a.speedButton;seek=a.seek;tick=a.tick;}
  void restore(MainActivity a){a.mainFrame=frame;a.root=root;a.body=body;a.bar=bar;a.screen=screen;a.query=query;a.bookBrowse=browse;a.feedSources=sources;a.selected=selected;a.books=books;a.openFeed=feed;a.playButton=play;a.remaining=remaining;a.chapter=chapter;a.time=time;a.speedButton=speed;a.seek=seek;a.tick=tick;a.dragging=false;}
 }
}
