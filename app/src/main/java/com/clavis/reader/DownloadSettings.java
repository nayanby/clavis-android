package com.clavis.reader;
import android.app.*;import android.content.*;
final class DownloadSettings {
 static int parallel(Context c){return Math.max(1,Math.min(8,c.getSharedPreferences("download_settings",0).getInt("parallel",4)));}
 static void show(MainActivity a){new AppDialog.Builder(a).setTitle("Download settings").setItems(new String[]{"Parallel torrents: "+parallel(a),"Save completed books: Internal storage/Clavis","Move completed books to Clavis"},(d,n)->{if(n==0)parallelDialog(a);else if(n==1||SharedDownloads.folder(a)==null)SharedDownloads.choose(a);else TorrentService.command(a,"publishall","");}).setNegativeButton("Close",null).show();}
 static void parallelDialog(MainActivity a){String[] labels={"1 download","2 downloads","3 downloads","4 downloads","5 downloads","6 downloads","7 downloads","8 downloads"};new AppDialog.Builder(a).setTitle("Parallel torrent downloads").setSingleChoiceItems(labels,parallel(a)-1,(d,n)->{a.getSharedPreferences("download_settings",0).edit().putInt("parallel",n+1).apply();if(TorrentService.live!=null)TorrentService.command(a,"settings","");d.dismiss();}).setNegativeButton("Close",null).show();}
}
