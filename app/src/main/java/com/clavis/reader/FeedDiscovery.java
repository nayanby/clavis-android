package com.clavis.reader;
import org.json.*;import java.nio.charset.StandardCharsets;import java.net.URI;import java.util.HashSet;
/** Online RSS source discovery; feed validation still occurs before subscribing. */
final class FeedDiscovery {
 static JSONArray search(String query,String kind)throws Exception{
  String clean=query.trim();if(clean.isEmpty())throw new IllegalArgumentException("Enter a publisher, website or topic");
  String url="https://cloud.feedly.com/v3/search/feeds?count=25&locale=en&query="+android.net.Uri.encode(clean);
  return sources(new JSONObject(new String(FeedStore.fetch(url,2*1024*1024),StandardCharsets.UTF_8)),kind);
 }
 static JSONArray sources(JSONObject response,String kind)throws Exception{
  if(!kind.equals("News")&&!kind.equals("Journals"))throw new IllegalArgumentException("Unsupported feed category");
  JSONArray results=response.optJSONArray("results");if(results==null||!response.optBoolean("success",true))throw new java.io.IOException("Feed search is unavailable");
  JSONArray sources=new JSONArray();HashSet<String> seen=new HashSet<>();
  for(int n=0;n<results.length()&&sources.length()<25;n++){
   JSONObject result=results.optJSONObject(n);if(result==null||!result.optBoolean("valid",true))continue;
   String raw=result.optString("feedId");if(raw.isEmpty())raw=result.optString("id");if(raw.startsWith("feed/"))raw=raw.substring(5);
   String feed=FeedStore.web(raw);if(feed.isEmpty())continue;
   if(feed.startsWith("http://"))feed="https://"+feed.substring(7);
   URI uri=new URI(feed).normalize();if(uri.getRawUserInfo()!=null)continue;feed="https://"+uri.getHost().toLowerCase(java.util.Locale.ROOT)+(uri.getPort()<0||uri.getPort()==443?"":":"+uri.getPort())+(uri.getRawPath()==null?"":uri.getRawPath())+(uri.getRawQuery()==null?"":"?"+uri.getRawQuery());
   if(!seen.add(feed))continue;
   String title=FeedScreens.plain(result.optString("title")).trim();if(title.isEmpty())title=uri.getHost();
   sources.put(new JSONObject().put("kind",kind).put("title",title).put("url",feed));
  }return sources;
 }
}