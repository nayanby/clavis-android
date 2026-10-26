package com.clavis.reader;
import java.util.Locale;
/** Publisher logos are source identity, not full-size story photographs. */
final class PostArtwork {
 static boolean usable(String url,FeedStore.Feed feed){if(url==null||url.isEmpty())return false;if(!feed.image.isEmpty()&&ArticleContent.imageKey(url).equals(ArticleContent.imageKey(feed.image)))return false;String path=url.toLowerCase(Locale.ROOT).split("\\?",2)[0];return !path.matches(".*(?:/rss\\.(?:gif|png)|favicon[^/]*|(?:^|[/_-])logo[^/]*|/og-(?:sections|image)\\.png)$");}
 static String choose(ArticleContent article,FeedStore.Feed feed){for(ArticleContent.Block block:article.blocks)if(block.kind.equals("img")&&usable(block.value,feed))return block.value;return "";}
}
