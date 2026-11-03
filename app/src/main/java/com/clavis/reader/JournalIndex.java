package com.clavis.reader;

import org.json.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Citation and abstract fallback; never filters journals by open-access status. */
final class JournalIndex {
 static final HashMap<String,String[]> sources=new HashMap<>();
 static synchronized void configure(android.content.Context c){if(!sources.isEmpty())return;try(java.io.InputStream in=c.getAssets().open("rss/journal-index.json")){JSONArray entries=new JSONArray(new String(FeedCatalog.readBytes(in),StandardCharsets.UTF_8));for(int n=0;n<entries.length();n++){JSONObject entry=entries.getJSONObject(n);sources.put(entry.getString("url"),new String[]{entry.getString("title"),entry.getString("issn")});}}catch(Exception e){android.util.Log.w("ClavisRSS","Journal index unavailable",e);}}
 static FeedStore.Feed download(String url)throws Exception{
  String[] source;synchronized(JournalIndex.class){source=sources.get(url);}if(source==null)throw new java.io.IOException("No journal index mapping");
  String query=URLEncoder.encode("ISSN:"+source[1]+" sort_date:y","UTF-8");JSONObject data=new JSONObject(new String(FeedStore.fetch("https://www.ebi.ac.uk/europepmc/webservices/rest/search?query="+query+"&format=json&resultType=core&pageSize=20",4*1024*1024),StandardCharsets.UTF_8));
  FeedStore.Feed feed=new FeedStore.Feed();feed.url=url;feed.kind="Journals";feed.title=source[0];feed.updated=System.currentTimeMillis();feed.access="abstracts";feed.policy="https://europepmc.org/About";
  JSONArray records=data.getJSONObject("resultList").getJSONArray("result");for(int n=0;n<records.length();n++){JSONObject record=records.getJSONObject(n);FeedStore.Item item=new FeedStore.Item();String id=record.optString("id"),provider=record.optString("source","MED"),doi=record.optString("doi");item.id="europepmc:"+provider+":"+id;item.title=record.optString("title");item.link=doi.isEmpty()?"https://europepmc.org/article/"+provider+"/"+id:"https://doi.org/"+doi;item.date=record.optString("firstPublicationDate");item.author=record.optString("authorString");item.description=record.optString("abstractText");if(item.description.isEmpty())item.description="Publication record indexed by Europe PMC. Open the publisher website for the article.";item.description+="<p>Citation and abstract: Europe PMC. Publisher access terms apply.</p>";if(!item.title.isEmpty())feed.items.add(item);}
  if(feed.items.isEmpty())throw new java.io.IOException("No indexed journal entries");return feed;
 }
}
