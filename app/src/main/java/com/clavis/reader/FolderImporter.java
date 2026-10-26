package com.clavis.reader;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Reads only the user-selected SAF tree; original audio and cover files stay in place. */
final class FolderImporter {
 static final class Folder {
  String name,uri,cover="";int coverRank=99;
  final ArrayList<String> epubs=new ArrayList<>();
  final ArrayList<Book.Chapter> tracks=new ArrayList<>();
  final ArrayList<Folder> children=new ArrayList<>();
 }
 static ArrayList<Book> scan(ContentResolver resolver,Uri tree,AtomicBoolean cancelled)throws Exception{
  Folder root=read(resolver,tree,DocumentsContract.getTreeDocumentId(tree),cancelled,0);
  ArrayList<Book> books=new ArrayList<>();group(root,"",books);ChapterDetection.enrich(resolver,books);return books;
 }
 static ArrayList<Book> scanAt(ContentResolver resolver,Uri tree,String id)throws Exception{ArrayList<Book> books=new ArrayList<>();group(read(resolver,tree,id,new AtomicBoolean(),0),"",books);ChapterDetection.enrich(resolver,books);return books;}
 private static Folder read(ContentResolver resolver,Uri tree,String id,AtomicBoolean cancel,int depth)throws Exception{
  if(cancel.get())throw new java.util.concurrent.CancellationException();
  if(depth>40)throw new java.io.IOException("Folder nesting is too deep");
  Folder f=new Folder();Uri doc=DocumentsContract.buildDocumentUriUsingTree(tree,id);f.uri=doc.toString();f.name=id.substring(id.lastIndexOf('/')+1);if(f.name.contains(":"))f.name=f.name.substring(f.name.lastIndexOf(':')+1);
  try(Cursor meta=resolver.query(doc,new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME},null,null,null)){if(meta!=null&&meta.moveToFirst())f.name=meta.getString(0);}
  ArrayList<String> dirs=new ArrayList<>();
  Uri children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);
  try(Cursor c=resolver.query(children,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)){
   if(c==null)throw new java.io.IOException("The selected folder is not readable");
   while(c.moveToNext()){
    if(cancel.get())throw new java.util.concurrent.CancellationException();
    String childId=c.getString(0),name=c.getString(1),mime=c.getString(2),lower=name.toLowerCase(Locale.ROOT);String uri=DocumentsContract.buildDocumentUriUsingTree(tree,childId).toString();
    if(DocumentsContract.Document.MIME_TYPE_DIR.equals(mime))dirs.add(childId);
    else if((mime!=null&&mime.startsWith("audio/"))||lower.matches(".*\\.(mp3|m4a|m4b|aac|flac|ogg|opus|wav|wma)$"))f.tracks.add(new Book.Chapter(uri,name.replaceFirst("(?i)\\.[a-z0-9]+$","")));
    else if(lower.endsWith(".epub"))f.epubs.add(uri);
    else if(lower.matches(".*\\.(jpg|jpeg|png|webp)$")){int rank=lower.matches("(cover|folder|front)\\..*")?0:lower.contains("cover")?1:2;if(rank<f.coverRank){f.cover=uri;f.coverRank=rank;}}
   }
  }
  for(String child:dirs)f.children.add(read(resolver,tree,child,cancel,depth+1));
  f.tracks.sort((a,b)->naturalCompare(a.title,b.title));f.children.sort((a,b)->naturalCompare(a.name,b.name));return f;
 }
 static ArrayList<Book> scanLocal(java.io.File directory)throws Exception{ArrayList<Book> books=new ArrayList<>();group(readLocal(directory,directory.getCanonicalPath()+java.io.File.separator,0),"",books);ChapterDetection.enrich(null,books);return books;}
 private static Folder readLocal(java.io.File directory,String root,int depth)throws Exception{
  if(depth>40)throw new java.io.IOException("Folder nesting is too deep");Folder f=new Folder();f.name=directory.getName();f.uri=Uri.fromFile(directory).toString();java.io.File[] children=directory.listFiles();if(children==null)throw new java.io.IOException("Downloaded folder is unreadable");
  for(java.io.File file:children){if(!file.getCanonicalPath().startsWith(root))continue;String name=file.getName(),lower=name.toLowerCase(Locale.ROOT);if(file.isDirectory())f.children.add(readLocal(file,root,depth+1));else if(lower.matches(".*\\.(mp3|m4a|m4b|aac|flac|ogg|opus|wav|wma)$"))f.tracks.add(new Book.Chapter(Uri.fromFile(file).toString(),name.replaceFirst("(?i)\\.[a-z0-9]+$","")));else if(lower.endsWith(".epub"))f.epubs.add(Uri.fromFile(file).toString());else if(lower.matches(".*\\.(jpg|jpeg|png|webp)$")){int rank=lower.matches("(cover|folder|front)\\..*")?0:lower.contains("cover")?1:2;if(rank<f.coverRank){f.cover=Uri.fromFile(file).toString();f.coverRank=rank;}}}
  f.tracks.sort((a,b)->naturalCompare(a.title,b.title));f.children.sort((a,b)->naturalCompare(a.name,b.name));return f;
 }
 private static void gatherEpubs(Folder f,ArrayList<String> out){out.addAll(f.epubs);for(Folder child:f.children)if(child.tracks.isEmpty())gatherEpubs(child,out);}
 private static boolean disc(Folder f){return f.name.toLowerCase(Locale.ROOT).matches("(?:cd|disc|disk|part|volume|vol|chapters?)[ ._-]*\\d*");}
 private static void gather(Folder f,ArrayList<Book.Chapter> tracks){tracks.addAll(f.tracks);for(Folder c:f.children)gather(c,tracks);}
 private static void group(Folder f,String inherited,ArrayList<Book> books){
  String cover=f.cover.isEmpty()?inherited:f.cover;ArrayList<Book.Chapter> tracks=new ArrayList<>(f.tracks);
  for(Folder child:f.children)if(disc(child)){gather(child,tracks);if(cover.isEmpty())cover=child.cover;}
  if(!tracks.isEmpty()){Book book=new Book(f.uri,f.name.isEmpty()?"Audiobook":f.name,"Audio");book.coverUri=cover;ArrayList<String> epubs=new ArrayList<>();gatherEpubs(f,epubs);if(epubs.size()==1)book.epubUri=epubs.get(0);book.chapters.addAll(tracks);books.add(book);}
  for(Folder child:f.children)if(!disc(child))group(child,cover,books);
 }
 static int naturalCompare(String a,String b){
  int i=0,j=0;while(i<a.length()&&j<b.length()){
   char x=Character.toLowerCase(a.charAt(i)),y=Character.toLowerCase(b.charAt(j));
   if(Character.isDigit(x)&&Character.isDigit(y)){int si=i,sj=j;while(i<a.length()&&Character.isDigit(a.charAt(i)))i++;while(j<b.length()&&Character.isDigit(b.charAt(j)))j++;java.math.BigInteger na=new java.math.BigInteger(a.substring(si,i)),nb=new java.math.BigInteger(b.substring(sj,j));int c=na.compareTo(nb);if(c!=0)return c;}
   else{if(x!=y)return Character.compare(x,y);i++;j++;}
  }return Integer.compare(a.length(),b.length());
 }
}

