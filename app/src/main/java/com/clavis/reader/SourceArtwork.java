package com.clavis.reader;
import android.content.Context;import android.graphics.*;import java.io.*;import java.net.*;import java.nio.*;import java.util.*;import org.jsoup.nodes.*;
/** Resolve source identity from its feed artwork, then publisher-owned icons. */
final class SourceArtwork {
 static String origin(String url){try{URI u=new URI(url);return u.getHost()==null?"":"https://"+u.getHost();}catch(Exception ignored){return "";}}
 static Bitmap attempt(Context c,String url){if(url.isEmpty())return null;try{return FeedArtwork.decode(c,url);}catch(Exception ignored){return null;}}
 static Bitmap load(Context c,FeedStore.Feed feed){
  android.content.SharedPreferences saved=c.getSharedPreferences("source_artwork",0);Bitmap known=attempt(c,saved.getString(feed.url,""));if(known!=null)return known;
  Bitmap bitmap=feed.image.contains("atypon_logo")?null:attempt(c,feed.image);if(bitmap!=null)return bitmap;
  String site=origin(feed.url);if(!feed.items.isEmpty()&&!feed.items.get(0).link.isEmpty())site=origin(feed.items.get(0).link);
  if(site.isEmpty())return null;for(String suffix:new String[]{"/favicon.ico","/apple-touch-icon.png"}){bitmap=attempt(c,site+suffix);if(bitmap!=null){saved.edit().putString(feed.url,site+suffix).apply();return bitmap;}}
  try{HttpURLConnection connection=FeedStore.connect(site,4000,5000);byte[] html;try(InputStream in=connection.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1){if(out.size()+n>1024*1024)break;out.write(buffer,0,n);}html=out.toByteArray();}finally{connection.disconnect();}
   Document document=org.jsoup.Jsoup.parse(new ByteArrayInputStream(html),null,site);int tried=0;for(Element icon:document.select("link[rel=apple-touch-icon],link[rel=icon],link[rel='shortcut icon']")){String url=FeedStore.resolve(site,icon.attr("href"));if(url.isEmpty()||url.toLowerCase(Locale.ROOT).contains(".svg"))continue;bitmap=attempt(c,url);if(bitmap!=null){saved.edit().putString(feed.url,url).apply();return bitmap;}if(++tried>=4)break;}
  }catch(Exception ignored){}return null;
 }
 // BitmapFactory doesn't understand ICO containers. Decode their PNG or DIB image.
 static Bitmap ico(File file){try{if(file.length()>1024*1024)return null;byte[] data;try(InputStream in=new FileInputStream(file);ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);data=out.toByteArray();}if(data.length<22)return null;ByteBuffer b=ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);if(b.getShort(0)!=0||b.getShort(2)!=1)return null;int count=b.getShort(4)&65535;if(count>64||6+count*16>data.length)return null;
  Bitmap best=null;for(int n=0;n<count;n++){int entry=6+n*16,size=b.getInt(entry+8),offset=b.getInt(entry+12);if(offset<0||size<40||offset>data.length-size)continue;Bitmap image=BitmapFactory.decodeByteArray(data,offset,size);if(image==null&&b.getInt(offset)>=40){int w=b.getInt(offset+4),h=b.getInt(offset+8)/2,bits=b.getShort(offset+14)&65535,compression=b.getInt(offset+16);if(w<=0||h<=0||w>256||h>256||(bits!=24&&bits!=32)||compression!=0)continue;int stride=((w*bits+31)/32)*4,start=offset+b.getInt(offset);if(start<offset||start+(long)stride*h>offset+(long)size)continue;int[] pixels=new int[w*h];boolean alpha=false;for(int y=0;y<h;y++)for(int x=0;x<w;x++){int p=start+(h-1-y)*stride+x*(bits/8),a=bits==32?data[p+3]&255:255;alpha|=a!=0;pixels[y*w+x]=Color.argb(a,data[p+2]&255,data[p+1]&255,data[p]&255);}if(!alpha)for(int p=0;p<pixels.length;p++)pixels[p]|=0xff000000;image=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);}if(image!=null){if(best==null||image.getWidth()>best.getWidth()){if(best!=null)best.recycle();best=image;}else image.recycle();}}
  return best;}catch(Exception ignored){return null;}}
}

