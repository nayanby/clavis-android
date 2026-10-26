package com.clavis.reader;
import java.net.*;
import java.util.*;
final class MagnetLink {
 final String uri,id,name;
 private MagnetLink(String uri,String id,String name){this.uri=uri;this.id=id;this.name=name;}
 static MagnetLink parse(String raw){
  if(raw==null||raw.length()>32768)throw new IllegalArgumentException("Invalid magnet link");
  String value=raw.trim().replace("&amp;","&");URI u=URI.create(value);if(!"magnet".equalsIgnoreCase(u.getScheme()))throw new IllegalArgumentException("Paste a magnet link starting with magnet:?");
  String query=u.getRawQuery(),id="",name="Audiobook download";if(query==null&&value.startsWith("magnet:?"))query=value.substring(8);
  if(query==null)throw new IllegalArgumentException("Magnet link has no information hash");
  for(String part:query.split("&")){String[] pair=part.split("=",2);if(pair.length!=2)continue;String key=decode(pair[0]),v=decode(pair[1]);
   if(key.equals("dn")&&!v.trim().isEmpty())name=v.replaceAll("[\\p{Cntrl}]", " ").trim();
   if(key.equals("xt")){String lower=v.toLowerCase(Locale.ROOT);if(lower.matches("urn:btih:[a-f0-9]{40}"))id=lower.substring(9);else if(lower.matches("urn:btih:[a-z2-7]{32}"))id=base32(lower.substring(9));else if(id.isEmpty()&&lower.matches("urn:btmh:1220[a-f0-9]{64}"))id=lower.substring(9);}
  }
  if(id.isEmpty())throw new IllegalArgumentException("Magnet link needs a valid BitTorrent information hash");return new MagnetLink(value,id,name.length()>160?name.substring(0,160):name);
 }
 private static String decode(String value){try{return URLDecoder.decode(value,"UTF-8");}catch(Exception e){throw new IllegalArgumentException("Invalid magnet encoding");}}
 private static String base32(String value){String alphabet="abcdefghijklmnopqrstuvwxyz234567";StringBuilder hex=new StringBuilder();int bits=0,buffer=0;for(char c:value.toCharArray()){buffer=(buffer<<5)|alphabet.indexOf(c);bits+=5;if(bits>=8){bits-=8;int b=(buffer>>bits)&255;hex.append(Character.forDigit(b>>4,16)).append(Character.forDigit(b&15,16));}}return hex.toString();}
}
