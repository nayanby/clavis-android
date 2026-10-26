package com.clavis.reader;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.ArrayList;

final class Book {
    String uri, title, author, coverUri="",epubUri=""; long duration, position, lastReadAt,addedAt; boolean saved,chaptersChecked;
    int chapterIndex=0; float speed=1f;
    final ArrayList<Chapter> chapters=new ArrayList<>();
    static final class Chapter {
        String uri,title; long startMs=0,endMs=-1;
        Chapter(String uri,String title){this.uri=uri;this.title=title;}
        Chapter(String uri,String title,long start,long end){this(uri,title);startMs=start;endMs=end;}
        JSONObject json() throws Exception{return new JSONObject().put("uri",uri).put("title",title).put("startMs",startMs).put("endMs",endMs);}
    }
    Book(String uri, String title, String author) { this.uri=uri; this.title=title; this.author=author;this.addedAt=System.currentTimeMillis(); }
    Book(JSONObject j) { uri=j.optString("uri"); title=j.optString("title"); author=j.optString("author"); lastReadAt=j.optLong("lastReadAt");addedAt=j.optLong("addedAt",0); duration=j.optLong("duration"); position=j.optLong("position"); saved=j.optBoolean("saved");chaptersChecked=j.optBoolean("chaptersChecked");coverUri=j.optString("coverUri","");epubUri=j.optString("epubUri","");chapterIndex=j.optInt("chapterIndex");speed=(float)j.optDouble("speed",1);JSONArray tracks=j.optJSONArray("chapters");if(tracks!=null)for(int i=0;i<tracks.length();i++){JSONObject t=tracks.optJSONObject(i);if(t!=null)chapters.add(new Chapter(t.optString("uri"),t.optString("title"),t.optLong("startMs",0),t.optLong("endMs",-1)));}chapterIndex=Math.max(0,Math.min(chapterIndex,chapterCount()-1)); }
    boolean isAudio(){return author.equals("Audio")||author.equals("Podcast");}
    long trackStart(int i){return chapters.isEmpty()?0:chapters.get(i).startMs;}
    long trackEnd(int i){return chapters.isEmpty()?-1:chapters.get(i).endMs;}
    long trackLength(int i,long total){return Math.max(0,(trackEnd(i)<0?total:Math.min(total,trackEnd(i)))-trackStart(i));}
    long totalAudioLength(java.util.function.ToLongFunction<String> lengths){long total=0;for(int i=0;i<chapterCount();i++){long end=trackEnd(i);long length=end>=0?end-trackStart(i):lengths.applyAsLong(trackUri(i));if(length<0)return -1;if(end<0)length=Math.max(0,length-trackStart(i));total+=length;}return total;}
    int chapterCount(){return Math.max(1,chapters.size());}
    String trackUri(int index){return chapters.isEmpty()?uri:chapters.get(index).uri;}
    String trackTitle(int index){return chapters.isEmpty()?title:chapters.get(index).title;}
    JSONObject json() { JSONObject j=new JSONObject(); try { JSONArray tracks=new JSONArray();for(Chapter c:chapters)tracks.put(c.json());j.put("addedAt",addedAt).put("lastReadAt",lastReadAt).put("uri",uri).put("title",title).put("author",author).put("duration",duration).put("position",position).put("saved",saved).put("chaptersChecked",chaptersChecked).put("coverUri",coverUri).put("epubUri",epubUri).put("chapterIndex",chapterIndex).put("speed",speed).put("chapters",tracks); } catch(Exception ignored) {} return j; }
}


