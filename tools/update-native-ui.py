from pathlib import Path
import re

path=Path('app/src/main/java/com/clavis/reader/MainActivity.java')
source=path.read_text(encoding='utf-8-sig')
methods={
 'shell':' void shell(String title){UiStyle.shell(this,title);}',
 'home':' void home(){UiStyle.home(this);}',
 'library':' void library(String kind){UiStyle.library(this,kind);}',
 'bookRow':' void bookRow(Book b){UiStyle.bookRow(this,b);}',
 'nowPlaying':' void nowPlaying(Book b){NowPlayingScreen.show(this,b);}',
 'showChapters':' void showChapters(Book b){PlayerDialogs.chapters(this,b);}',
}
for name,value in methods.items():
    source,count=re.subn(r'^ void '+name+r'\([^\n]*$',lambda _:value,source,flags=re.M)
    if count!=1: raise RuntimeError(f'{name}: expected exactly one method, got {count}')
source=source.replace(' void pick(String kind){',' void pickFile(String kind){',1)
source=source.replace('super.onActivityResult(request,result,data);if(request!=20','super.onActivityResult(request,result,data);if(request==21&&result==RESULT_OK&&data!=null){importFolder(data);return;}if(request!=20',1)
source=source.replace('root.addView(m,new LinearLayout.LayoutParams(-1,-2));','LinearLayout.LayoutParams mp=new LinearLayout.LayoutParams(-1,-2);mp.bottomMargin=dp(76);root.addView(m,mp);')
source=source.replace('books=Store.load(this);if(getClass()==MainActivity.class)','books=Store.load(this);if(state!=null)importKind=state.getString("importKind","Audio");if(getClass()==MainActivity.class)',1)
insert='''
 void pick(String kind){if(kind.equals("Audio"))new AlertDialog.Builder(this).setTitle("Import audiobooks").setItems(new String[]{"Select folder (books, chapters & covers)","Select audio files"},(d,n)->{if(n==0)pickFolder();else pickFile(kind);}).setNegativeButton("Cancel",null).show();else pickFile(kind);}
 void pickFolder(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION|Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);startActivityForResult(intent,21);}
 void importFolder(Intent data){Uri tree=data.getData();if(tree==null)return;try{getContentResolver().takePersistableUriPermission(tree,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception e){Toast.makeText(this,"Folder access was not granted",Toast.LENGTH_LONG).show();return;}java.util.concurrent.atomic.AtomicBoolean cancelled=new java.util.concurrent.atomic.AtomicBoolean();ProgressDialog progress=new ProgressDialog(this);progress.setTitle("Import audiobooks");progress.setMessage("Scanning books, chapters and cover images…");progress.setIndeterminate(true);progress.setCancelable(false);progress.setButton(android.content.DialogInterface.BUTTON_NEGATIVE,"Cancel",(d,n)->cancelled.set(true));progress.show();io.execute(()->{try{ArrayList<Book> imported=FolderImporter.scan(getContentResolver(),tree,cancelled);if(cancelled.get())return;runOnUiThread(()->{progress.dismiss();if(isDestroyed())return;ArrayList<Book> existing=Store.load(this);int added=0;for(Book incoming:imported){Book old=null;for(Book b:existing)if(b.uri.equals(incoming.uri)){old=b;break;}if(old==null){existing.add(incoming);added++;}else{String current=old.trackUri(old.chapterIndex);old.chapters.clear();old.chapters.addAll(incoming.chapters);old.coverUri=incoming.coverUri;old.chapterIndex=0;for(int i=0;i<old.chapterCount();i++)if(old.trackUri(i).equals(current))old.chapterIndex=i;}}Store.save(this,existing);library("Audio");Toast.makeText(this,imported.isEmpty()?"No supported audio files found":added+" books imported · "+imported.size()+" folders scanned",Toast.LENGTH_LONG).show();});}catch(java.util.concurrent.CancellationException e){runOnUiThread(progress::dismiss);}catch(Exception e){runOnUiThread(()->{progress.dismiss();if(!isDestroyed())new AlertDialog.Builder(this).setTitle("Folder import").setMessage("Could not read this folder. Select a local audiobook folder and try again.\\n"+e.getMessage()).setPositiveButton("Close",null).show();});}});}
 @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);if(intent.getBooleanExtra("player",false))openActivePlayer();}
 @Override protected void onSaveInstanceState(Bundle state){state.putString("importKind",importKind);super.onSaveInstanceState(state);}
'''
source=source.rsplit('}',1)[0]+insert+'}\n'
path.write_text(source,encoding='utf-8')

path=Path('app/src/main/java/com/clavis/reader/ReaderActivity.java')
source=path.read_text(encoding='utf-8-sig')
source=source.replace('toolbar.setPadding(dp(8),dp(7),dp(8),dp(7));','toolbar.setPadding(dp(12),0,dp(12),0);toolbar.setMinimumHeight(dp(56));')
source=source.replace('titleBlock.setPadding(dp(10),dp(4),dp(10),dp(4));titleBlock.setBackground(getDrawable(R.drawable.bg_reader_toolbar_pill));','titleBlock.setPadding(0,0,0,0);titleBlock.setGravity(Gravity.CENTER_VERTICAL);titleBlock.setMinimumHeight(dp(40));')
source=source.replace('name.setMaxLines(2);','name.setMaxLines(1);name.setTypeface(serif,Typeface.BOLD);')
source=source.replace('titleBlock.addView(folder);','if(!kind.equals("PDF"))titleBlock.addView(folder);')
source=source.replace('toolbar.addView(button("Aa",this::typography))','toolbar.addView(smallAction("Aa",this::typography))')
source=source.replace('toolbar.addView(button("⋮",()->menu(toolbar)))','toolbar.addView(icon(R.drawable.ic_toolbar_more,"Reader options",()->menu(toolbar)))')
source=source.replace('root.addView(toolbar,new LinearLayout.LayoutParams(-1,-2))','root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(56)))')
source=source.replace('nav.setPadding(dp(10),dp(5),dp(10),dp(7));','nav.setPadding(0,0,0,dp(10));')
source=source.replace('pagePill.setTypeface(serif);','pagePill.setTypeface(serif,Typeface.BOLD);pagePill.setMinHeight(0);pagePill.setPadding(dp(8),0,dp(8),0);')
source=source.replace('FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.END|Gravity.CENTER_VERTICAL);','FrameLayout.LayoutParams op=new FrameLayout.LayoutParams(dp(34),dp(34),Gravity.END|Gravity.TOP);op.rightMargin=dp(12);')
source=source.replace('b.setPadding(dp(14),dp(14),dp(14),dp(14));','b.setPadding(dp(6),dp(6),dp(6),dp(6));')
source=source.replace('b.setLayoutParams(new LinearLayout.LayoutParams(dp(48),dp(48)));','b.setLayoutParams(new LinearLayout.LayoutParams(dp(34),dp(34)));')
source=source.rsplit('}',1)[0]+''' TextView smallAction(String label,Runnable action){TextView t=button(label,action);t.setMinHeight(0);t.setTextSize(14);t.setPadding(dp(6),dp(6),dp(6),dp(6));t.setBackgroundResource(R.drawable.bg_toolbar_button);t.setLayoutParams(new LinearLayout.LayoutParams(dp(34),dp(34)));return t;}\n}\n'''
path.write_text(source,encoding='utf-8')
