package com.clavis.reader;
import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
/** Shared extracted-article cache for the reader and feed post slides. */
final class ArticleRepository {
 private static final java.util.concurrent.ExecutorService workers=new java.util.concurrent.ThreadPoolExecutor(3,3,30,java.util.concurrent.TimeUnit.SECONDS,new java.util.concurrent.ArrayBlockingQueue<>(48));
 private static final java.util.HashMap<String,java.util.concurrent.CompletableFuture<ArticleContent>> pending=new java.util.HashMap<>();
 static synchronized java.util.concurrent.CompletableFuture<ArticleContent> request(Context c,String source,ArticleContent fallback){
  java.util.concurrent.CompletableFuture<ArticleContent> existing=pending.get(source);if(existing!=null)return existing;
  java.util.concurrent.CompletableFuture<ArticleContent> result=new java.util.concurrent.CompletableFuture<>();pending.put(source,result);Context app=c.getApplicationContext();
  try{workers.execute(()->{try{result.complete(load(app,source,fallback));}catch(Exception e){result.completeExceptionally(e);}finally{synchronized(ArticleRepository.class){pending.remove(source,result);}}});}catch(java.util.concurrent.RejectedExecutionException e){pending.remove(source);result.completeExceptionally(e);}return result;
 }
 private static long cacheEpoch;
 static synchronized void invalidate(Context c,java.util.Collection<FeedStore.Feed> feeds){if(feeds.isEmpty())return;java.util.HashSet<String> seen=new java.util.HashSet<>();for(FeedStore.Feed feed:feeds)if(!feed.kind.equals("Podcasts"))for(FeedStore.Item item:feed.items)if(!item.link.isEmpty()&&seen.add(item.link)){if(seen.size()==1)cacheEpoch++;pending.remove(item.link);try{File cache=cacheFile(c,item.link);if(cache.isFile()&&!cache.setLastModified(0))cache.delete();}catch(Exception ignored){}}}
 // Start with the newest story from each source, then warm more in round-robin order.
 static void prefetch(Context c,String kind,java.util.List<FeedStore.Feed> feeds){
  if(kind.equals("Podcasts"))return;int count=0;java.util.HashSet<String> seen=new java.util.HashSet<>();
  for(int n=0;n<5&&count<20;n++)for(FeedStore.Feed feed:feeds)if(feed.kind.equals(kind)&&!feed.fullText&&n<feed.items.size()){
   FeedStore.Item item=feed.items.get(n);if(item.link.isEmpty()||!FeedTopics.item(c,feed,item)||!seen.add(item.link))continue;
   request(c,item.link,ArticleContent.extract(item.description,item.link,true)).whenComplete((article,error)->{if(article!=null){String artwork=PostArtwork.choose(article,feed);FeedArtwork.main.post(()->FeedArtwork.prefetch(c,artwork));}});if(++count==20)break;
  }
 }
 static File cacheFile(Context c,String source)throws Exception{
  StringBuilder hash=new StringBuilder();for(byte b:MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)))hash.append(String.format(Locale.US,"%02x",b&255));
  File folder=new File(c.getCacheDir(),"article-text-v17");folder.mkdirs();return new File(folder,hash+".json");
 }
 static ArticleContent load(Context c,String source,ArticleContent fallback)throws Exception{
  if(FeedStore.web(source).isEmpty())throw new IOException("No article link");File cache=cacheFile(c,source);long epoch; synchronized(ArticleRepository.class){epoch=cacheEpoch;}
  ArticleContent saved=null;if(cache.isFile())try{ArticleContent previous=ArticleActivity.read(cache);if(previous.characters>=fallback.characters&&previous.characters>=120){saved=previous;if(System.currentTimeMillis()-cache.lastModified()<6*60*60*1000L)return saved;}}catch(Exception ignored){}
  try{
  byte[] bytes=FeedStore.fetch(source,8*1024*1024);org.jsoup.nodes.Document parsed=org.jsoup.Jsoup.parse(new ByteArrayInputStream(bytes),null,source);
  ArticleContent content=ArticleContent.extract(parsed.outerHtml(),source,false);
  if(content.characters<120||content.characters<fallback.characters){
   // A feed may contain the full story while the publisher page exposes only
   // image/date metadata. Keep that story and still recover its artwork.
   ArticleContent merged=new ArticleContent();merged.title=content.title;merged.published=content.published;merged.preview=content.preview;
   for(ArticleContent.Block block:content.blocks)if(block.kind.equals("img"))merged.add(block.kind,block.value);
   for(ArticleContent.Block block:fallback.blocks)merged.add(block.kind,block.value);
   if(merged.characters<120)throw new IOException("Only a preview was available");content=merged;
  }
  synchronized(ArticleRepository.class){if(epoch!=cacheEpoch)return content;File pending=File.createTempFile("article-",".json",cache.getParentFile());try{ArticleActivity.write(pending,content);if(!pending.renameTo(cache)&&!cache.exists())throw new IOException("Could not cache article");}finally{pending.delete();}return content;}
  }catch(Exception error){if(saved!=null)return saved;throw error;}
 }
}
