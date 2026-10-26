package com.clavis.reader;

import android.app.AlertDialog;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;
import java.util.ArrayList;

/** Dimensions and typography taken directly from Cortex's native layout resources. */
final class UiStyle {
 static ImageButton icon(MainActivity a,int resource,String description,Runnable action,int size){
  ImageButton view=new ImageButton(a);view.setImageResource(resource);view.setImageTintList(android.content.res.ColorStateList.valueOf(MainActivity.INK));if(resource==R.drawable.home_articles||resource==R.drawable.home_journals)view.setImageTintList(null);view.setBackgroundColor(Color.TRANSPARENT);view.setPadding(a.dp(6),a.dp(6),a.dp(6),a.dp(6));view.setContentDescription(description);view.setOnClickListener(v->action.run());view.setLayoutParams(new LinearLayout.LayoutParams(a.dp(size),a.dp(size)));return view;
 }
 static void shell(MainActivity a,String title){
  View previous=a.mainFrame;
  if(a.tick!=null)a.handler.removeCallbacks(a.tick);if(a.liveText!=null){a.liveText.close();a.liveText=null;}
  if(Build.VERSION.SDK_INT>=30)a.getWindow().setDecorFitsSystemWindows(false);else a.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  a.getWindow().setStatusBarColor(Color.TRANSPARENT);a.getWindow().setNavigationBarColor(AppTheme.background);
  FrameLayout frame=new FrameLayout(a);a.mainFrame=frame;frame.setBackgroundColor(AppTheme.background);
  frame.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){Insets i=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(i.left,i.top,i.right,i.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
  a.root=a.column();a.root.setBackgroundColor(AppTheme.background);frame.addView(a.root,new FrameLayout.LayoutParams(-1,-1));a.setContentView(frame);
  if(Build.VERSION.SDK_INT>=30){WindowInsetsController controller=a.getWindow().getInsetsController();if(controller!=null)controller.setSystemBarsAppearance(AppTheme.dark?0:WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);}else if(Build.VERSION.SDK_INT>=27)a.getWindow().getDecorView().setSystemUiVisibility(AppTheme.dark?a.getWindow().getDecorView().getSystemUiVisibility()&~(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR):a.getWindow().getDecorView().getSystemUiVisibility()|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR|View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
  a.bar=a.row();a.bar.setPadding(a.dp(12),0,a.dp(12),0);a.bar.setBackgroundColor(AppTheme.background);
  boolean home=a.screen.equals("Home");ImageButton nav=icon(a,home?R.drawable.ic_menu:R.drawable.ic_arrow_back,home?"Open menu":"Back",()->{if(home)menu(a);else a.home();},34);nav.setPadding(a.dp(5),a.dp(8),a.dp(5),a.dp(8));a.bar.addView(nav,new LinearLayout.LayoutParams(a.dp(34),a.dp(40)));
  TextView heading=a.text(title,20,MainActivity.INK,true);heading.setTypeface(a.serif,Typeface.BOLD);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setPadding(a.dp(16),0,0,0);heading.setSingleLine();heading.setEllipsize(android.text.TextUtils.TruncateAt.END);a.bar.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
  a.bar.addView(icon(a,R.drawable.ic_search,"Search books",()->search(a),48));a.root.addView(a.bar,new LinearLayout.LayoutParams(-1,a.dp(56)));
  a.body=a.column();a.body.setPadding(a.dp(16),a.dp(14),a.dp(16),a.dp(90));ScrollView scroll=new ScrollView(a);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.addView(a.body);a.root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
  if(!a.screen.equals("Reader")&&!a.screen.equals("Player")&&!a.screen.equals("PodcastPlayer"))bottom(a,frame);
  ScreenMotion.replace(a,previous,frame);
 }
 static void bottom(MainActivity a,FrameLayout frame){
  FrameLayout host=new FrameLayout(a);host.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,new int[]{AppTheme.background,AppTheme.background,AppTheme.background & 0x00ffffff}));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(-1,a.dp(98),Gravity.BOTTOM);frame.addView(host,hp);
  LinearLayout pill=a.row();pill.setPadding(a.dp(16),0,a.dp(16),0);pill.setBackgroundResource(R.drawable.bg_bottom_navigation_pill);pill.setElevation(a.dp(2));pill.setClipToOutline(true);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(a.dp(320),a.dp(56),Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);p.bottomMargin=a.dp(10);host.addView(pill,p);
  String[] tabs={"Home","Audio","Podcasts","Browser","Downloads"};int[] icons={R.drawable.ic_home,R.drawable.ic_listen_audio,R.drawable.ic_podcast,R.drawable.ic_browser_globe,R.drawable.ic_download_books};String[] labels={"Home","Audiobooks","Podcasts","Browser","Downloads"};
  for(int i=0;i<tabs.length;i++){String tab=tabs[i];FrameLayout slot=new FrameLayout(a);pill.addView(slot,new LinearLayout.LayoutParams(0,-1,1));ImageButton item=icon(a,icons[i],labels[i],()->{if(tab.equals("Home"))a.home();else if(tab.equals("Audio"))a.openAudioPlayer();else if(tab.equals("Podcasts"))a.openPodcastPlayer();else if(tab.equals("Browser"))new AppDialog.Builder(a).setTitle("Browse books").setItems(new String[]{"Audiobooks","Ebooks"},(d,n)->a.startActivity(new android.content.Intent(a,CatalogActivity.class).putExtra("ebook",n==1))).show();else if(tab.equals("Downloads"))a.startActivity(new android.content.Intent(a,DownloadsActivity.class));else a.library(tab);},40);GradientDrawable badge=new GradientDrawable();boolean active=a.screen.equals(tab)||(tab.equals("Audio")&&a.screen.equals("Player"))||(tab.equals("Podcasts")&&a.screen.equals("PodcastPlayer"));badge.setColor(active?AppTheme.text:AppTheme.surface);badge.setCornerRadius(a.dp(20));item.setBackground(badge);item.setImageTintList(android.content.res.ColorStateList.valueOf(active?AppTheme.surface:AppTheme.muted));item.setPadding(a.dp(8),a.dp(8),a.dp(8),a.dp(8));slot.addView(item,new FrameLayout.LayoutParams(a.dp(40),a.dp(40),Gravity.CENTER));}

 }
 static int pastel(String kind){boolean sage=kind.equals("PDF")||kind.equals("Podcasts"),peach=kind.equals("Audio")||kind.equals("Journals");return AppTheme.dark?(sage?0xff3c4436:peach?0xff514038:0xff47404d):(sage?0xffe1ead8:peach?0xffe9e0ef:0xffdbeaf0);}
 static GradientDrawable surface(MainActivity a,String kind,int radius){GradientDrawable d=new GradientDrawable();d.setColor(AppTheme.surface);d.setCornerRadius(a.dp(radius));return d;}
 static void home(MainActivity a){
  a.screen="Home";a.books=Store.load(a);shell(a,"Clavis");a.body.setPadding(a.dp(16),a.dp(8),a.dp(16),a.dp(90));
  String[] titles={"Ebooks","PDF Books","Audio Books","News","Podcasts","Articles","Journalling"};
  String[] descriptions={"Read at your own pace","Read and annotate PDFs","Listen to your library","Your latest RSS stories","Listen to your shows","Follow publications","Type or write your thoughts"};
  String[] kinds={"EPUB","PDF","Audio","News","Podcasts","Journals","Journalling"};
  int[] resources={R.drawable.home_epub,R.drawable.home_pdf,R.drawable.home_audiobooks,R.drawable.home_news,R.drawable.home_podcasts,R.drawable.home_articles,R.drawable.home_journals};
  int columns=a.getResources().getConfiguration().screenWidthDp>=600?3:2;for(int i=0;i<titles.length;i+=columns){LinearLayout row=a.row();for(int j=i;j<Math.min(i+columns,titles.length);j++){LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-2,1);if(j>i)cell.leftMargin=a.dp(8);row.addView(homeCard(a,titles[j],descriptions[j],kinds[j],resources[j]),cell);}a.body.addView(row);a.space(a.body,8);}
  a.space(a.body,6);TextView h=a.text("Continue reading",18,MainActivity.INK,true);h.setTypeface(a.serif,Typeface.BOLD);a.body.addView(h);a.space(a.body,14);boolean any=false;for(Book b:Store.recent(a.books)){a.bookRow(b);any=true;}if(!any)a.body.addView(a.text("Import a book to start your next chapter.",13,MainActivity.MUTED,true));a.miniPlayer();
 }
 static View homeCard(MainActivity a,String title,String sub,String kind,int resource){
  LinearLayout card=a.row();card.setPadding(a.dp(12),a.dp(14),a.dp(12),a.dp(14));card.setMinimumHeight(a.dp(72));card.setBackground(surface(a,kind,18));
  ImageView icon=new ImageView(a);icon.setImageResource(resource);icon.setImageTintList(null);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);card.addView(icon,new LinearLayout.LayoutParams(a.dp(36),a.dp(36)));
  TextView name=a.text(title,16,AppTheme.text,false);name.setTypeface(a.serif,Typeface.NORMAL);name.setMaxLines(3);name.setGravity(Gravity.CENTER_VERTICAL);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=a.dp(10);card.addView(name,np);
  card.setOnClickListener(v->a.library(kind));card.setContentDescription(title+". "+sub);card.setFocusable(true);return card;
 }
 static void menu(MainActivity a){SideDrawer.show(a);}
 static void library(MainActivity a,String kind){library(a,kind,false);}
 static void library(MainActivity a,String kind,boolean browse){a.bookBrowse=browse;
  a.screen=kind;a.books=Store.load(a);shell(a,kind.equals("Audio")?"Audio Books":kind.equals("EPUB")?"Ebooks":kind+" Books");
  if(browse)a.bar.addView(icon(a,R.drawable.ic_toolbar_more,"Catalog information",()->CatalogScreens.info(a),40));else a.bar.addView(icon(a,R.drawable.ic_add,"Import "+kind+" books",()->a.pick(kind),34));
  LinearLayout tabs=a.row();tabs.setPadding(a.dp(16),a.dp(6),a.dp(16),a.dp(10));for(boolean target:new boolean[]{false,true}){TextView tab=a.button(target?"Browse":"My books",()->library(a,kind,target));tab.setSelected(target==browse);tab.setBackground(target==browse?surface(a,kind,14):null);tabs.addView(tab,new LinearLayout.LayoutParams(0,a.dp(48),1));}a.root.addView(tabs,1,new LinearLayout.LayoutParams(-1,-2));
  a.body.setPadding(a.dp(16),a.dp(8),a.dp(16),a.dp(90));if(browse){CatalogScreens.render(a,kind,"");a.miniPlayer();return;}int count=0;for(Book b:Store.newestFirst(a.books))if(b.author.equals(kind)){a.bookRow(b);count++;}
  if(count==0){a.space(a.body,50);TextView empty=a.text("Your shelf is waiting",20,MainActivity.INK,true);empty.setGravity(Gravity.CENTER);a.body.addView(empty);a.space(a.body,12);TextView help=a.text(kind.equals("Audio")?"Import a book folder, a whole library folder, or audio files.":"Import a "+kind.toLowerCase()+" book from your device.",13,MainActivity.MUTED,true);help.setGravity(Gravity.CENTER);a.body.addView(help);a.space(a.body,22);a.body.addView(a.button(kind.equals("Audio")?"＋  Import audiobook folder":"＋  Import "+kind+" book",()->{if(kind.equals("Audio"))a.pickFolder();else a.pick(kind);}));}
  a.miniPlayer();
 }
 static void bookRow(MainActivity a,Book b){
  LinearLayout r=a.row();r.setPadding(a.dp(14),a.dp(14),a.dp(14),a.dp(14));r.setBackground(surface(a,"EPUB",18));FrameLayout tile=new FrameLayout(a);tile.setBackgroundResource(R.drawable.bg_subject_tile);ImageView symbol=new ImageView(a);symbol.setPadding(a.dp(10),a.dp(10),a.dp(10),a.dp(10));symbol.setImageResource(b.isAudio()?R.drawable.ic_listen_audio:b.author.equals("PDF")?R.drawable.ic_pdf:R.drawable.ic_book);symbol.setImageTintList(android.content.res.ColorStateList.valueOf(MainActivity.MUTED));tile.addView(symbol,new FrameLayout.LayoutParams(-1,-1));r.addView(tile,new LinearLayout.LayoutParams(a.dp(40),a.dp(40)));a.io.execute(()->{Bitmap art=Artwork.load(a,b);if(art!=null)a.runOnUiThread(()->{if(a.isDestroyed()){art.recycle();return;}symbol.setImageTintList(null);tile.setBackground(a.bg(Color.WHITE,8));tile.setClipToOutline(true);tile.setElevation(a.dp(3));symbol.setPadding(a.dp(3),a.dp(3),a.dp(3),a.dp(3));symbol.setScaleType(ImageView.ScaleType.FIT_CENTER);symbol.setImageBitmap(art);});});
  LinearLayout copy=a.column();TextView heading=a.text(b.title,14,MainActivity.INK,true);heading.setTypeface(a.serif,Typeface.BOLD);heading.setMaxLines(2);heading.setEllipsize(android.text.TextUtils.TruncateAt.END);copy.addView(heading);String detail=b.isAudio()?b.chapterCount()+" chapter"+(b.chapterCount()==1?"":"s")+" · Chapter "+(b.chapterIndex+1):b.author+(b.position>0?" · Page "+(b.position+1):"");String progress=BookProgress.label(b);if(!progress.isEmpty())detail+=" · "+progress;copy.addView(a.text(detail,12,MainActivity.MUTED,true));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,-2,1);cp.leftMargin=a.dp(14);r.addView(copy,cp);if(b.isAudio()){TextView length=a.text("...",12,MainActivity.MUTED,true);length.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);length.setSingleLine();length.setPadding(a.dp(10),0,0,0);r.addView(length,new LinearLayout.LayoutParams(-2,-2));long cached=b.totalAudioLength(uri->ChapterLengths.cached(a,uri));if(cached>=0){length.setText(Store.time(cached));length.setContentDescription("Audiobook length "+Store.time(cached));}else a.io.execute(()->{long total=b.totalAudioLength(uri->ChapterLengths.read(a,uri));a.runOnUiThread(()->{if(!a.isDestroyed()){length.setText(total>=0?Store.time(total):"--");length.setContentDescription(total>=0?"Audiobook length "+Store.time(total):"Audiobook length unavailable");}});});}r.setOnClickListener(v->a.open(b));r.setOnLongClickListener(v->{BookDeletion.confirm(a,b);return true;});a.body.addView(r);a.space(a.body,10);
 }
 static void search(MainActivity a){EditText input=new EditText(a);input.setSingleLine();input.setHint("Book title");new AppDialog.Builder(a).setTitle("Search books").setView(input).setPositiveButton("Search",(d,n)->{ArrayList<Book> found=new ArrayList<>();for(Book b:Store.load(a))if(b.title.toLowerCase(java.util.Locale.ROOT).contains(input.getText().toString().toLowerCase(java.util.Locale.ROOT)))found.add(b);new AppDialog.Builder(a).setTitle(found.size()+" books").setItems(found.stream().map(b->b.title).toArray(String[]::new),(dd,i)->a.open(found.get(i))).setNegativeButton("Close",null).show();}).setNegativeButton("Cancel",null).show();}
}









