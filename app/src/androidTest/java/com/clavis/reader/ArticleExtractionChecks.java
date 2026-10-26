package com.clavis.reader;
import android.app.Instrumentation;
final class ArticleExtractionChecks {
 static void run(Instrumentation test)throws Exception{
  FeedPostChecks.check(java.time.Instant.parse("2026-10-25T04:30:00Z").equals(FeedDate.instant("Sun, 25 Oct 2026 10:00:00 +0530")),"RSS publication timezone parsed correctly");
  FeedPostChecks.check(java.time.Instant.parse("2026-10-25T04:30:00Z").equals(FeedDate.instant("2026-10-25T10:00:00+05:30")),"Atom publication timezone parsed correctly");
  FeedPostChecks.check(FeedDate.display("",java.util.Locale.US).isEmpty(),"Missing publication date is not fabricated");
  FeedPostChecks.check(java.time.Instant.parse("2026-10-17T13:45:00Z").equals(FeedDate.instant("Sat, 17 Oct 2026 14:45:00 BST")),"Sky Sports British Summer Time parsed");
  String datedFeed="<rss xmlns:dc='http://purl.org/dc/elements/1.1/'><channel><title>Dates</title><item><title>Story</title><dc:date>2026-10-25T10:00:00Z</dc:date></item></channel></rss>";FeedStore.Feed parsed=FeedStore.parse(new java.io.ByteArrayInputStream(datedFeed.getBytes(java.nio.charset.StandardCharsets.UTF_8)),"https://example.com/feed");FeedPostChecks.check(parsed.items.get(0).date.equals("2026-10-25T10:00:00Z"),"Dublin Core publication dates retained");
  FeedPostChecks.check(ArticleContent.extract("<meta property='article:published_time' content='2026-10-25T10:00:00Z'><article><p>Story</p></article>","https://example.com/news",false).published.equals("2026-10-25T10:00:00Z"),"Publisher page date fallback extracted");
  FeedStore.Feed logoFeed=new FeedStore.Feed();logoFeed.image="https://www.nature.com/uploads/product/nm/rss.gif";FeedPostChecks.check(!PostArtwork.usable(logoFeed.image,logoFeed),"Nature RSS logo is not enlarged into story artwork");ArticleContent artwork=ArticleContent.extract("<p>Story</p><img src='/uploads/product/nm/rss.gif'><img src='/article/figure1.jpg'>","https://www.nature.com/",true);FeedPostChecks.check(PostArtwork.choose(artwork,logoFeed).endsWith("figure1.jpg"),"Article figure preferred over publisher logo");
  android.content.Context context=test.getTargetContext();float density=context.getResources().getDisplayMetrics().density;int gap=Math.round(16*density),extra=Math.round(3*density);android.text.TextPaint paint=new android.text.TextPaint();paint.setTextSize(30*density);
  CharSequence spaced=StoryPages.spaced("First paragraph\n\nSecond paragraph",gap,extra,1.16f);android.text.StaticLayout layout=android.text.StaticLayout.Builder.obtain(spaced,0,spaced.length(),paint,Math.round(600*density)).setIncludePad(false).setLineSpacing(extra,1.16f).build();
  FeedPostChecks.check(Math.abs(layout.getLineTop(2)-layout.getLineTop(1)-gap)<=1,"Paragraph gap is 16dp independent of font size");
  String story="The local council approved a new public transport plan after residents requested more reliable services. The first services will begin next month and connect the hospital with the town centre.";
  String html="<main><p>Subscribe now</p><p>Unrelated front page material</p><article><p>Published - today</p><div class='articlebodycontent'><p>"+story+"</p><div class='audio-player'>Listen to this article</div><p>कॉपी लिंक</p><a href='#save'><span>Save</span></a><div role='button'><span>Save for later</span></div><p>Saved</p><p>.</p><p>"+story+"</p></div></article></main>";
  FeedPostChecks.check(!ArticleContent.noiseText("The council plans to save the historic library."),"Save inside story prose is preserved");String text=StoryPages.text(ArticleContent.extract(html,"https://example.com/news",false));
  FeedPostChecks.check(text.equals(story),"Story container excludes chrome, punctuation and duplicate paragraphs");
  String captionHtml="<article><div class='articlebodycontent'><p>"+story+"</p><figure><img src='/scene.jpg'><figcaption>Scene at the council meeting. Photograph: Reporter/AP</figcaption></figure><div class='imageCaption'><img src='/second.jpg'><span>A second caption with a nested image</span></div><p><span class='photo-credit'>Reporter/Getty Images</span></p><p>A description of the meeting. (Photo: Agency)</p><p>A meeting photograph. Agency/Getty Images hide caption</p><p>Photo: Agency</p><p>फोटो: एजेंसी</p><p>Text size</p><p>Open in app</p><p>The photographer explains how tax credits support local businesses.</p></div></article>";
  ArticleContent clean=ArticleContent.extract(captionHtml,"https://example.com/news",false);text=StoryPages.text(clean);
  FeedPostChecks.check(text.equals(story+"\n\nThe photographer explains how tax credits support local businesses."),"Captions, credits and image controls excluded without deleting story prose");
  FeedPostChecks.check(clean.blocks.stream().filter(b->b.kind.equals("img")).count()==2,"Artwork inside caption wrappers preserved");
  FeedPostChecks.check(clean.blocks.stream().noneMatch(b->b.kind.equals("caption")),"Native reader and slides both exclude captions");
  clean=ArticleContent.extract("<p>"+story+"</p><div class='caption'>An RSS image caption</div>","https://example.com/news",true);
  FeedPostChecks.check(StoryPages.text(clean).equals(story),"RSS fallback captions cleaned too");
  String footer="<article><p>"+story+"</p><div class='author-bio'>A reporter covering billionaires and their impact on the world.</div><div>Image</div><div>A version of this article appears in print on <span>October 25</span>, Section <span>A</span>, Page <span>15</span> of the New York edition with the headline:</div><div>Print headline</div></article>";
  FeedPostChecks.check(StoryPages.text(ArticleContent.extract(footer,"https://example.com/news",false)).equals(story),"Observed NYT author biography, Image label and fragmented print footer excluded");
  FeedPostChecks.check(!ArticleContent.noiseText("The image shows residents celebrating the new transport plan."),"Image mentioned within story prose preserved");
  String access="You have a preview view of this article while we are checking your access. When we have confirmed access, the full article content will load.";
  ArticleContent preview=ArticleContent.extract("<article><p>"+access+"</p><p>"+story+"</p></article>","https://example.com/news",false);
  FeedPostChecks.check(preview.preview&&StoryPages.text(preview).equals(story),"Observed NYT access notice removed while publisher preview remains explicit");
  java.io.File previewCache=java.io.File.createTempFile("preview-check-",".json",test.getTargetContext().getCacheDir());try{ArticleActivity.write(previewCache,preview);FeedPostChecks.check(ArticleActivity.read(previewCache).preview,"Publisher preview flag survives cache reload");}finally{previewCache.delete();}
  org.json.JSONObject schema=new org.json.JSONObject().put("@type","NewsArticle").put("articleBody",story+"\n"+story.replace("local council","regional council"));
  text=StoryPages.text(ArticleContent.extract("<script type='application/ld+json'>"+schema+"</script><main><p>Loading article</p></main>","https://example.com/news",false));
  FeedPostChecks.check(text.contains("regional council")&&text.contains("\n\n"),"Structured story fallback preserves paragraphs");
  String bbc;try(java.io.InputStream input=test.getContext().getAssets().open("articles/bbc-video.html")){bbc=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  String bbcText=StoryPages.text(ArticleContent.extract(bbc,"https://www.bbc.co.uk/news/videos/c6vg08xlr8g3o",false));
  FeedPostChecks.check(bbcText.contains("Hundreds of protests")&&bbcText.contains("Adina Campbell"),"Affected BBC video story keeps its actual reporting");
  FeedPostChecks.check(!bbcText.contains("Subsection")&&!bbcText.contains("Published")&&!bbcText.contains("Related topics"),"BBC metadata labels and topic footer do not enter story slides");
  FeedPostChecks.check(!ArticleContent.noiseText("Published research shows that related topics matter in Europe."),"Publication words within ordinary story prose survive");
  String bbcFragment="<p>"+story+"</p><ul class='ssrcss-9tw7r1-MetadataStripContainer'><li><div class='visually-hidden'>Subsection</div><span>Europe</span></li><li><div>Published</div><time>17 October</time></li></ul><h2>Related topics</h2><p>Unrelated recommendation text.</p>";
  FeedPostChecks.check(StoryPages.text(ArticleContent.extract(bbcFragment,"https://www.bbc.co.uk/news/",true)).equals(story),"BBC metadata and recommendation boundary also cleaned from RSS fallback");
  String bbcNews;try(java.io.InputStream input=test.getContext().getAssets().open("articles/bbc-news.html")){bbcNews=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  ArticleContent bbcReport=ArticleContent.extract(bbcNews,"https://www.bbc.co.uk/news/",false);String reportText=StoryPages.text(bbcReport);
  FeedPostChecks.check(!reportText.contains("Getty Images")&&reportText.contains("Lord Ricketts")&&reportText.contains("key infrastructure"),"Observed BBC figure copyright excluded while reporting survives");
  FeedPostChecks.check(bbcReport.blocks.stream().anyMatch(b->b.kind.equals("img")),"BBC artwork survives image copyright cleanup");
  FeedPostChecks.check(!ArticleContent.noiseText("Getty Images announced a new partnership today."),"Agency names in genuine reporting are not globally blacklisted");
  if(test instanceof ReaderChecks&&((ReaderChecks)test).feedNetwork)for(String source:new String[]{"https://www.bbc.co.uk/news/videos/c6vg08xlr8g3o?at_medium=RSS&at_campaign=rss","https://www.bbc.co.uk/news/articles/cmze4y9rjepro"}){ArticleContent live=ArticleRepository.load(test.getTargetContext(),source,new ArticleContent());String liveText=StoryPages.text(live);FeedPostChecks.check(live.characters>120&&!liveText.contains("Getty Images")&&!liveText.contains("Subsection")&&!liveText.contains("Related topics"),"Live BBC cache excludes observed metadata and image-credit artifacts");}
  String refreshUrl="https://refresh-check.invalid/"+System.nanoTime(),untouchedUrl=refreshUrl+"-untouched";java.io.File refreshCache=ArticleRepository.cacheFile(context,refreshUrl),untouchedCache=ArticleRepository.cacheFile(context,untouchedUrl);try{ArticleContent cached=ArticleContent.extract("<p>"+story+"</p>",refreshUrl,true);ArticleActivity.write(refreshCache,cached);ArticleActivity.write(untouchedCache,cached);long previousTime=untouchedCache.lastModified();FeedStore.Feed refreshFeed=new FeedStore.Feed();refreshFeed.kind="News";FeedStore.Item refreshItem=new FeedStore.Item();refreshItem.link=refreshUrl;refreshFeed.items.add(refreshItem);ArticleRepository.invalidate(context,java.util.Collections.singletonList(refreshFeed));FeedPostChecks.check(refreshCache.isFile()&&refreshCache.lastModified()==0,"Refreshing a feed expires its article text while retaining an offline copy");FeedPostChecks.check(untouchedCache.lastModified()==previousTime,"Refreshing one feed leaves other article caches intact");FeedPostChecks.check(StoryPages.text(ArticleRepository.load(context,refreshUrl,new ArticleContent())).equals(story),"Unavailable refreshed article retains its last readable body");}finally{refreshCache.delete();untouchedCache.delete();}
  String real;try(java.io.InputStream input=test.getContext().getAssets().open("articles/hindu.html")){real=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  text=StoryPages.text(ArticleContent.extract(real,"https://www.thehindu.com/",false));
  FeedPostChecks.check(text.contains("Cyberabad")&&text.contains("latest detention"),"Actual publisher page preserves story beginning and ending");
  FeedPostChecks.check(!text.contains("Published -")&&!text.contains("feedback")&&!text.contains("Sign in"),"Actual publisher story excludes timestamps and widgets");
  String sports;try(java.io.InputStream input=test.getContext().getAssets().open("articles/skysports.html")){sports=new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);}
  text=StoryPages.text(ArticleContent.extract(sports,"https://www.skysports.com/",false));
  FeedPostChecks.check(!text.contains("This content is provided by")&&!text.contains("we need your permission to use cookies")&&!text.contains("Unfortunately we have been unable")&&!text.contains("Privacy Options"),"Actual Sky Sports page excludes complete consent roadblocks and fragments");
  FeedPostChecks.check(!text.contains("Monday 19 October 2026")&&!text.startsWith("Saturday"),"Sky Sports header metadata excluded from story body");FeedPostChecks.check(text.startsWith("Spain"),"Actual story opening retained");FeedPostChecks.check(text.contains("McIlroy")&&text.contains("Chacarra")&&text.length()>1000,"Sky Sports story remains after removing embedded-content notices");
 }
}







