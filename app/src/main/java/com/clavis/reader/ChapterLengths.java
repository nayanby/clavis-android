package com.clavis.reader;
import android.content.Context;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
final class ChapterLengths {
 static long cached(Context c,String uri){return c.getSharedPreferences("chapter_lengths",0).getLong(uri,-1);}
 static long read(Context c,String uri){long value=cached(c,uri);if(value>=0)return value;MediaMetadataRetriever m=new MediaMetadataRetriever();try{m.setDataSource(c,Uri.parse(uri));String raw=m.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);if(raw!=null){value=Long.parseLong(raw);c.getSharedPreferences("chapter_lengths",0).edit().putLong(uri,value).apply();}}catch(Exception ignored){}finally{try{m.release();}catch(Exception ignored){}}return value;}
}
