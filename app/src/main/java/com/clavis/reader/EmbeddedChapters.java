package com.clavis.reader;
import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;
/** Reads ID3 CHAP and Nero MP4 chpl markers without loading audio into memory. */
final class EmbeddedChapters {
 static final class Marker {final long time;final String title;Marker(long t,String s){time=t;title=s;}}
 static byte[] read(FileChannel c,long at,int n)throws IOException{if(n<0||n>32*1024*1024)throw new IOException("Chapter metadata too large");ByteBuffer b=ByteBuffer.allocate(n);while(b.hasRemaining()){int k=c.read(b,at+b.position());if(k<=0)throw new EOFException();}return b.array();}
 static int sync(byte[] b,int o){return (b[o]&127)<<21|(b[o+1]&127)<<14|(b[o+2]&127)<<7|b[o+3]&127;}
 static long u32(byte[] b,int o){return ByteBuffer.wrap(b,o,4).getInt()&0xffffffffL;}
 static List<Marker> read(FileChannel c)throws IOException{ArrayList<Marker> out=new ArrayList<>();if(c.size()<10)return out;byte[] h=read(c,0,10);if(new String(h,0,3,StandardCharsets.ISO_8859_1).equals("ID3")){int version=h[3];if((version==3||version==4)&&(h[5]&0xc0)==0){byte[] tag=read(c,10,sync(h,6));frames(tag,version,out);} }else {atoms(c,0,c.size(),0,out);if(out.isEmpty())out.addAll(QuickTimeChapters.read(c));}out.sort(Comparator.comparingLong(m->m.time));return out;}
 static void frames(byte[] b,int version,List<Marker> out){for(int i=0;i+10<=b.length;){String id=new String(b,i,4,StandardCharsets.ISO_8859_1);int n=version==4?sync(b,i+4):(int)u32(b,i+4);int from=i+10;if(n<=0||n>b.length-from)break;int end=from+n;if(id.equals("CHAP")&&b[i+8]==0&&b[i+9]==0){int p=from;while(p<end&&b[p]!=0)p++;p++;if(p+16<=end){long start=u32(b,p);String title="Chapter";for(int q=p+16;q+10<=end;){int len=version==4?sync(b,q+4):(int)u32(b,q+4);if(len<=0||len>end-q-10)break;if(new String(b,q,4,StandardCharsets.ISO_8859_1).equals("TIT2")&&len>1){int enc=b[q+10]&255;java.nio.charset.Charset charset=enc==1?StandardCharsets.UTF_16:enc==2?StandardCharsets.UTF_16BE:enc==3?StandardCharsets.UTF_8:StandardCharsets.ISO_8859_1;title=new String(b,q+11,len-1,charset).replace("\u0000","").trim();}q+=10+len;}out.add(new Marker(start,title));}}i=end;}}
 static void atoms(FileChannel c,long from,long end,int depth,List<Marker> out)throws IOException{if(depth>5)return;for(long p=from;p+8<=end;){byte[] h=read(c,p,8);long size=u32(h,0);String type=new String(h,4,4,StandardCharsets.ISO_8859_1);int header=8;if(size==1){if(p+16>end)return;size=ByteBuffer.wrap(read(c,p+8,8)).getLong();header=16;}if(size==0)size=end-p;if(size<header||size>end-p)return;if(type.equals("moov")||type.equals("udta"))atoms(c,p+header,p+size,depth+1,out);else if(type.equals("chpl")&&size-header<=1024*1024){byte[] b=read(c,p+header,(int)size-header);int pos=b.length>0&&b[0]==1?8:4;if(pos<b.length){int count=b[pos++]&255;for(int i=0;i<count&&pos+9<=b.length;i++){long time=ByteBuffer.wrap(b,pos,8).getLong()/10000;pos+=8;int n=b[pos++]&255;if(n>b.length-pos)break;out.add(new Marker(time,new String(b,pos,n,StandardCharsets.UTF_8)));pos+=n;}}}p+=size;}}
}
