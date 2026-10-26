package com.clavis.reader;
import android.content.Context;
import org.json.JSONArray;
import java.util.ArrayList;
final class Store {
 static synchronized void opened(Context c,String uri){ArrayList<Book> books=load(c);for(Book b:books)if(b.uri.equals(uri))b.lastReadAt=System.currentTimeMillis();save(c,books);}
 static ArrayList<Book> newestFirst(java.util.List<Book> books){ArrayList<Book> result=new ArrayList<>(books);java.util.Collections.reverse(result);result.sort((a,b)->Long.compare(Math.max(b.lastReadAt,b.addedAt),Math.max(a.lastReadAt,a.addedAt)));return result;}
 static ArrayList<Book> load(Context c){ ArrayList<Book> list=new ArrayList<>();try{JSONArray a=new JSONArray(c.getSharedPreferences("library",0).getString("books","[]"));for(int i=0;i<a.length();i++)list.add(new Book(a.getJSONObject(i)));}catch(Exception ignored){}return list; }
 static void save(Context c,ArrayList<Book> books){JSONArray a=new JSONArray();for(Book b:books)a.put(b.json());c.getSharedPreferences("library",0).edit().putString("books",a.toString()).apply();}
 static void progress(Context c,String uri,long position,long duration){ArrayList<Book> list=load(c);for(Book b:list)if(b.uri.equals(uri)){b.position=position;b.duration=duration;b.lastReadAt=System.currentTimeMillis();}save(c,list);}
 static ArrayList<Book> recent(java.util.List<Book> library){ArrayList<Book> recent=new ArrayList<>();for(Book b:library)if(b.lastReadAt>0||b.position>0||b.chapterIndex>0)recent.add(b);recent.sort((a,b)->Long.compare(b.lastReadAt,a.lastReadAt));return new ArrayList<>(recent.subList(0,Math.min(4,recent.size())));}
 static String kind(Book b){return b.author;}
 static String time(long ms){long s=ms/1000;return s>=3600?String.format(java.util.Locale.US,"%d:%02d:%02d",s/3600,s/60%60,s%60):String.format(java.util.Locale.US,"%d:%02d",s/60,s%60);}
}

