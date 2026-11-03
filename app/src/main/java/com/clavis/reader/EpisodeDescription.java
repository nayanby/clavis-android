package com.clavis.reader;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import java.util.regex.Pattern;

/** Keep episode prose and chapter notes, without publisher promotional footers. */
final class EpisodeDescription {
 private static final Pattern PROMOTION=Pattern.compile("(?iu)^(?:follow (?:us|the show|this podcast)|subscribe(?: to| for| now|$)|support (?:us|the show|this podcast|the podcast)|join (?:our|the) (?:newsletter|patreon|community)|sign up|advertis(?:e|ing)|sponsors?\\b|sponsored by|this episode is (?:sponsored|brought to you)|learn more about your ad choices|see (?:omnystudio|acast).*privacy|privacy policy|hosted on (?:acast|megaphone)|visit (?:our|the show) (?:website|store)|get (?:ad.free|bonus)|leave (?:us )?(?:a )?review|rate (?:and|&) review|connect with us|listen ad.free|donate|(?:instagram|twitter|facebook|tiktok|patreon|youtube):)");
 private static final Pattern URL=Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
 static String clean(String html){
  if(html==null||html.trim().isEmpty())return "";
  Document doc=Jsoup.parseBodyFragment(html);
  if(doc.body().text().matches("(?s).*<(?:p|div|br|a)\\b.*"))doc=Jsoup.parseBodyFragment(doc.body().text());
  doc.select("script,style,noscript,iframe,svg,form,nav,footer,header,aside,img").remove();
  for(Element block:doc.select("p,li,div")){
   boolean nested=false;for(Element child:block.children())if(child.tagName().matches("p|li|div|ul|ol")){nested=true;break;}
   if(!nested&&PROMOTION.matcher(block.text().trim()).find())block.remove();
  }
  for(Element anchor:doc.select("a"))if(URL.matcher(anchor.text()).find())anchor.remove();
  for(Element block:doc.select("p,li,div,br,h1,h2,h3,h4")){block.before("\n");block.after("\n");}
  StringBuilder result=new StringBuilder();
  for(String raw:doc.body().wholeText().replace('\u00a0',' ').split("\\R")){
   String line=raw.replaceAll("[\\t ]+"," ").trim();
   if(PROMOTION.matcher(line).find())continue;
   line=line.replaceFirst("(?iu)\\s+(?:Learn more about your ad choices|This episode is sponsored by|See omnystudio\\.com/listener for privacy).*","");
   line=URL.matcher(line).replaceAll("").replaceAll("[\\t ]+"," ").trim();
   if(line.isEmpty())continue;
   if(result.length()>0)result.append("\n\n");result.append(line);
  }
  return result.toString();
 }
}
