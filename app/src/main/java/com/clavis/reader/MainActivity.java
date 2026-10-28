package com.clavis.reader;



import android.app.*;

import android.content.*;

import android.database.Cursor;

import android.graphics.*;

import android.graphics.drawable.GradientDrawable;

import android.media.MediaMetadataRetriever;

import android.net.Uri;

import android.os.*;

import android.provider.OpenableColumns;

import android.text.*;

import android.view.*;

import android.widget.*;

import java.io.*;

import java.util.*;

import java.util.concurrent.*;



public class MainActivity extends Activity {


 static int INK=0xff292664,MUTED=0xff667395,PAPER=0xffFAF9FD,LINE=0xffDDE1EF;

 InlineTranscriptView liveText; FrameLayout mainFrame; LinearLayout root,body,bar;String screen="Home",importKind="Audio",query="";Book selected;ArrayList<Book> books;Typeface serif;Handler handler=new Handler(Looper.getMainLooper());ExecutorService io=Executors.newSingleThreadExecutor();TextView playButton,remaining,chapter,time,speedButton;SeekBar seek;boolean dragging,bookBrowse,feedSources;Runnable tick;

 int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}

 GradientDrawable bg(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),LINE);return d;}

 TextView text(String s,int size,int color,boolean heading){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setTypeface(serif);t.setLineSpacing(dp(2),1.05f);HindiTypography.apply(t,s);if(heading&&HindiTypography.contains(s))t.setTypeface(HindiTypography.face(this,true));return t;}

 LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}

 LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setGravity(Gravity.CENTER_VERTICAL);return l;}

 TextView button(String label,Runnable action){TextView t=text(label,15,INK,false);t.setGravity(Gravity.CENTER);t.setMinHeight(dp(48));t.setPadding(dp(12),dp(8),dp(12),dp(8));t.setBackground(UiStyle.surface(this,"EPUB",12));t.setOnClickListener(v->action.run());t.setFocusable(true);return t;}

 void space(LinearLayout l,int height){l.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}

 FeedStore.Feed openFeed;private boolean resumedOnce;
 @Override protected void onResume(){super.onResume();if(resumedOnce&&getClass()==MainActivity.class){if(screen.equals("Home"))home();else if(screen.equals("EPUB")||screen.equals("PDF")||screen.equals("Audio"))UiStyle.library(this,screen,bookBrowse);else if(FeedStore.category(screen))FeedScreens.show(this,screen,feedSources);}if(getClass()==MainActivity.class){FeedRefresh.observe(this);miniPlayer();}resumedOnce=true;}
 @Override protected void onPause(){FeedRefresh.forget(this);super.onPause();}
 @Override public void onCreate(Bundle state){AppTheme.configure(this);super.onCreate(state);serif=getResources().getFont(R.font.averia_serif_libre);books=Store.load(this);if(state!=null)importKind=state.getString("importKind","Audio");if(getClass()==MainActivity.class){FeedCatalog.seed(this);FeedRefresh.schedule(this);home();ArrayList<FeedStore.Feed> initialFeeds=FeedStore.load(this);FeedArtwork.prefetchSources(this,"News",initialFeeds,8);FeedArtwork.prefetchSources(this,"Journals",initialFeeds,8);FeedRefresh.maybe(this,"News");FeedRefresh.maybe(this,"Journals");openNavigation(getIntent());if(getIntent().getBooleanExtra("audioLibrary",false))library("Audio");if(getIntent().getBooleanExtra("player",false)&&PlaybackService.live!=null)openActivePlayer();if(Build.VERSION.SDK_INT>=33&&checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},7);}}

 void shell(String title){UiStyle.shell(this,title);}

 void home(){UiStyle.home(this);}

 void homeCard(String title,String subtitle,String kind,int from,int to,String symbol){LinearLayout c=row();c.setPadding(dp(20),dp(23),dp(15),dp(23));GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,new int[]{from,to});gradient.setCornerRadius(dp(16));c.setBackground(gradient);TextView icon=text(symbol,32,INK,false);c.addView(icon,new LinearLayout.LayoutParams(dp(50),dp(50)));LinearLayout copy=column();copy.addView(text(title,23,INK,true));space(copy,4);long count=books.stream().filter(b->b.author.equals(kind)).count();copy.addView(text(count==0?subtitle:count+" books · "+subtitle,12,MUTED,false));c.addView(copy,new LinearLayout.LayoutParams(0,-2,1));c.addView(text("›",25,INK,false));c.setOnClickListener(v->library(kind));c.setFocusable(true);c.setContentDescription(title+", open library");body.addView(c,new LinearLayout.LayoutParams(-1,-2));space(body,15);}

 void library(String kind){if(kind.equals("Journalling")){startActivity(new Intent(this,JournallingActivity.class));return;}if(FeedStore.category(kind))FeedScreens.show(this,kind);else UiStyle.library(this,kind);}

 void bookRow(Book b){UiStyle.bookRow(this,b);}

 void miniPlayer(){for(int n=root.getChildCount()-1;n>=0;n--)if(root.getChildAt(n) instanceof MiniPlayerView||root.getChildAt(n) instanceof ListenMiniPlayerView)root.removeViewAt(n);PlaybackService s=PlaybackService.live;ListenService narration=ListenService.live;boolean listening=narration!=null&&narration.story!=null&&(s==null||!s.playing());if(!listening&&(s==null||s.uri.isEmpty()))return;LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.bottomMargin=dp(76);root.addView(listening?new ListenMiniPlayerView(this):new MiniPlayerView(this),mp);}

 void pickFile(String kind){importKind=kind;Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType(kind.equals("Audio")?"audio/*":kind.equals("PDF")?"application/pdf":"application/epub+zip");intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,20);}

 @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request==24&&result==RESULT_OK&&data!=null){BookDeletion.granted(this,data);return;}if(request==22&&result==RESULT_OK&&data!=null){SharedDownloads.accept(this,data);return;}if(request==21&&result==RESULT_OK&&data!=null){importFolder(data);return;}if(request!=20||result!=RESULT_OK||data==null)return;ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null)for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());else if(data.getData()!=null)uris.add(data.getData());books=Store.load(this);for(Uri uri:uris){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}String title="Untitled book";try(Cursor c=getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(c!=null&&c.moveToFirst())title=c.getString(0);}catch(Exception ignored){}String id=uri.toString();if(books.stream().noneMatch(b->b.uri.equals(id)))books.add(new Book(id,title.replaceFirst("(?i)\\.(pdf|epub|mp3|m4a|m4b|ogg|wav|flac)$",""),importKind));}Store.save(this,books);library(importKind);if(importKind.equals("Audio")){ArrayList<Book> pending=new ArrayList<>(books);io.execute(()->{for(Book b:pending)if(ChapterDetection.needs(b))try{ChapterDetection.Discovery found=ChapterDetection.read(getContentResolver(),b);runOnUiThread(()->{PlaybackService active=PlaybackService.live;if(active==null||!active.uri.equals(b.uri))ChapterDetection.save(this,b,found);});}catch(Exception ignored){}});}}

 void open(Book b){Store.opened(this,b.uri);if(b.isAudio()){selected=b;Intent i=new Intent(this,PlaybackService.class).putExtra("uri",b.uri).putExtra("title",b.title).putExtra("position",b.position);startForegroundService(i);nowPlaying(b);}else startActivity(new Intent(this,ReaderActivity.class).putExtra("uri",b.uri).putExtra("title",b.title).putExtra("kind",b.author));}

 void openNavigation(Intent intent){String route=intent.getStringExtra("navigationRoute");if(route==null)return;if(route.equals("Audio"))openAudioPlayer();else if(route.equals("Podcasts"))openPodcastPlayer();else if(route.equals("Browser"))new AppDialog.Builder(this).setTitle("Browse books").setItems(new String[]{"Audiobooks","Ebooks"},(d,n)->startActivity(new Intent(this,CatalogActivity.class).putExtra("ebook",n==1))).show();else home();}
 void openAudioPlayer(){openRememberedPlayer("Audio");}
 void openPodcastPlayer(){openRememberedPlayer("Podcast");}
 void openRememberedPlayer(String kind){ArrayList<Book> library=Store.load(this);PlaybackService active=PlaybackService.live;if(active!=null&&active.book!=null&&active.book.author.equals(kind)&&!active.uri.isEmpty())for(Book book:library)if(book.uri.equals(active.uri)&&book.author.equals(kind)){nowPlaying(book);return;}String last=getSharedPreferences("navigation",0).getString(kind.equals("Podcast")?"lastPodcastUri":"lastAudioUri","");Book target=null;for(Book book:library)if(book.author.equals(kind)){if(target==null)target=book;if(book.uri.equals(last)){target=book;break;}}if(target==null){library(kind.equals("Podcast")?"Podcasts":"Audio");return;}startForegroundService(new Intent(this,PlaybackService.class).putExtra("uri",target.uri).putExtra("autoplay",false));nowPlaying(target);}
 void openActivePlayer(){PlaybackService s=PlaybackService.live;if(s==null)return;for(Book b:Store.load(this))if(b.uri.equals(s.uri)){selected=b;nowPlaying(b);break;}}

 void nowPlaying(Book b){NowPlayingScreen.show(this,b);}
 void showChapters(Book b){PlayerDialogs.chapters(this,b);}

 @Override public void onBackPressed(){if(SideDrawer.close(this))return;if(screen.equals("Home"))super.onBackPressed();else if(screen.equals("Player"))library("Audio");else if(screen.equals("PodcastPlayer"))library("Podcasts");else if(screen.endsWith(" feed"))library(screen.substring(0,screen.length()-5));else if(screen.endsWith(" article"))library(screen.substring(0,screen.length()-8));else home();}

 @Override protected void onDestroy(){if(liveText!=null)liveText.close();if(tick!=null)handler.removeCallbacks(tick);io.shutdown();super.onDestroy();}



 void pick(String kind){if(kind.equals("Audio"))new AppDialog.Builder(this).setTitle("Import audiobooks").setItems(new String[]{"Select folder (books, chapters & covers)","Select audio files"},(d,n)->{if(n==0)pickFolder();else pickFile(kind);}).setNegativeButton("Cancel",null).show();else pickFile(kind);}

 void pickFolder(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(intent,21);}

 void importFolder(Intent data){Uri tree=data.getData();if(tree==null)return;try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){AppNotice.makeText(this,"Folder access was not granted",Toast.LENGTH_LONG).show();return;}java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();ProgressDialog progress=new ProgressDialog(this);progress.setTitle("Import audiobooks");progress.setMessage("Scanning books, chapters and cover images…");progress.setIndeterminate(true);progress.setCancelable(false);progress.setButton(android.content.DialogInterface.BUTTON_NEGATIVE,"Cancel",(d,n)->cancelled.set(true));progress.show();io.execute(()->{try{ArrayList<Book> imported=FolderImporter.scan(getContentResolver(),tree,cancelled);if(cancelled.get())return;runOnUiThread(()->{progress.dismiss();if(isDestroyed())return;ArrayList<Book> existing=Store.load(this);int added=0;for(Book incoming:imported){Book old=null;for(Book b:existing)if(b.uri.equals(incoming.uri)){old=b;break;}if(old==null){existing.add(incoming);added++;}else{String current=old.trackUri(old.chapterIndex);long absolute=old.trackStart(old.chapterIndex)+old.position;old.chapters.clear();old.chapters.addAll(incoming.chapters);old.coverUri=incoming.coverUri;old.epubUri=incoming.epubUri;old.chaptersChecked=incoming.chaptersChecked;old.chapterIndex=0;for(int i=0;i<old.chapterCount();i++)if(old.trackUri(i).equals(current)&&absolute>=old.trackStart(i)&&(old.trackEnd(i)<0||absolute<old.trackEnd(i))){old.chapterIndex=i;old.position=Math.max(0,absolute-old.trackStart(i));if(old.trackEnd(i)>=0)old.duration=old.trackEnd(i)-old.trackStart(i);break;}}}Store.save(this,existing);library("Audio");AppNotice.makeText(this,imported.isEmpty()?"No supported audio files found":added+" books imported · "+imported.size()+" folders scanned",Toast.LENGTH_LONG).show();});}catch(java.util.concurrent.CancellationException e){runOnUiThread(progress::dismiss);}catch(Exception e){runOnUiThread(()->{progress.dismiss();if(!isDestroyed())new AppDialog.Builder(this).setTitle("Folder import").setMessage("Could not read this folder. Select a local audiobook folder and try again.\n"+e.getMessage()).setPositiveButton("Close",null).show();});}});}

 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);openNavigation(intent);if(intent.getBooleanExtra("audioLibrary",false))library("Audio");if(intent.getBooleanExtra("player",false))openActivePlayer();}

 @Override protected void onSaveInstanceState(Bundle state){state.putString("importKind",importKind);super.onSaveInstanceState(state);}

 @Override protected void attachBaseContext(Context base){super.attachBaseContext(AppTheme.themedContext(base));}}
