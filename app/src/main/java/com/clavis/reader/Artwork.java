package com.clavis.reader;
import android.content.Context;
import android.graphics.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import java.io.*;
final class Artwork {
 static Bitmap load(Context context,Book book){
  try{if(!book.coverUri.isEmpty()){String cover=book.coverUri;if(cover.startsWith("https://")||cover.startsWith("http://"))cover=Uri.fromFile(FeedStore.image(context,cover)).toString();BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(cover))){BitmapFactory.decodeStream(in,null,size);}BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(size.outWidth,size.outHeight)/900);try(InputStream in=context.getContentResolver().openInputStream(Uri.parse(cover))){return BitmapFactory.decodeStream(in,null,options);}}}catch(Exception ignored){}
  if(book.author.equals("EPUB")||book.author.equals("PDF"))return documentCover(context,book);
  if(book.author.equals("Podcast"))return null;
  MediaMetadataRetriever meta=new MediaMetadataRetriever();try{meta.setDataSource(context,Uri.parse(book.trackUri(book.chapterIndex)));byte[] bytes=meta.getEmbeddedPicture();if(bytes!=null){BitmapFactory.Options size=new BitmapFactory.Options();size.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,size);BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=Math.max(1,Math.max(size.outWidth,size.outHeight)/900);return BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);}}catch(Exception ignored){}finally{try{meta.release();}catch(Exception ignored){}}return null;
 }
 static Bitmap documentCover(Context c,Book b){File temporary=null;com.artifex.mupdf.fitz.Document doc=null;try{File covers=new File(c.getCacheDir(),"book-covers");covers.mkdirs();String id=Integer.toHexString(b.uri.hashCode());File image=new File(covers,id+".png");if(image.isFile())return BitmapFactory.decodeFile(image.getAbsolutePath());File original=new File(new File(c.getCacheDir(),"reading"),id+(b.author.equals("PDF")?".pdf":".epub"));if(!original.isFile()){temporary=File.createTempFile("book-cover-",b.author.equals("PDF")?".pdf":".epub",c.getCacheDir());try(InputStream in=c.getContentResolver().openInputStream(Uri.parse(b.uri));OutputStream out=new FileOutputStream(temporary)){if(in==null)return null;byte[] buffer=new byte[65536];long total=0;int n;while((n=in.read(buffer))!=-1){total+=n;if(total>256L*1024*1024)return null;out.write(buffer,0,n);}}original=temporary;}doc=com.artifex.mupdf.fitz.Document.openDocument(original.getAbsolutePath());if(doc.needsPassword())return null;if(doc.isReflowable())doc.layout(420,620,12);if(doc.countPages()==0)return null;com.artifex.mupdf.fitz.Page page=doc.loadPage(0);Bitmap bitmap;try{com.artifex.mupdf.fitz.Rect bounds=page.getBounds();if((bounds.y1-bounds.y0)/Math.max(1,bounds.x1-bounds.x0)>10)return null;bitmap=com.artifex.mupdf.fitz.android.AndroidDrawDevice.drawPageFitWidth(page,180);}finally{page.destroy();}try(OutputStream out=new FileOutputStream(image)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}return bitmap;}catch(Exception|LinkageError ignored){return null;}finally{if(doc!=null)doc.destroy();if(temporary!=null)temporary.delete();}}

}

