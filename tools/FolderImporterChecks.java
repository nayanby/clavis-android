package com.clavis.reader;
import java.util.*;
import java.lang.reflect.Method;

/** Pure grouping tests; Android file-picker access is tested separately on device. */
public final class FolderImporterChecks {
 private static int checks;
 private static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
 private static FolderImporter.Folder folder(String name,String uri){FolderImporter.Folder f=new FolderImporter.Folder();f.name=name;f.uri=uri;return f;}
 private static ArrayList<Book> group(FolderImporter.Folder root)throws Exception{Method m=FolderImporter.class.getDeclaredMethod("group",FolderImporter.Folder.class,String.class,ArrayList.class);m.setAccessible(true);ArrayList<Book> result=new ArrayList<>();m.invoke(null,root,"",result);return result;}
 public static void main(String[] args)throws Exception{
  check(FolderImporter.naturalCompare("Chapter 2","Chapter 10")<0,"Natural chapter ordering");
  check(FolderImporter.naturalCompare("Chapter 11","Chapter 2")>0,"Two-digit chapter ordering");
  check(FolderImporter.naturalCompare("chapter 2","Chapter 2")==0,"Case-independent chapter ordering");
  check(FolderImporter.naturalCompare("Chapter 0002","Chapter 10")<0,"Zero-padded chapter ordering");
  check(FolderImporter.naturalCompare("Disc 2 Track 9","Disc 2 Track 10")<0,"Multiple numeric segments");
  FolderImporter.Folder root=folder("Library","library");
  FolderImporter.Folder first=folder("First book","book1");first.cover="cover1";first.epubs.add("first.epub");
  for(String n:new String[]{"Chapter 10","Chapter 2","Chapter 1"})first.tracks.add(new Book.Chapter(n,n));
  first.tracks.sort((a,b)->FolderImporter.naturalCompare(a.title,b.title));root.children.add(first);
  FolderImporter.Folder second=folder("Second book","book2");second.cover="cover2";
  FolderImporter.Folder cd1=folder("CD 1","book2/cd1"),cd2=folder("CD 2","book2/cd2");
  cd1.tracks.add(new Book.Chapter("cd1-track","Opening"));cd2.tracks.add(new Book.Chapter("cd2-track","Ending"));second.children.add(cd1);second.children.add(cd2);root.children.add(second);
  ArrayList<Book> books=group(root);
  check(books.get(0).epubUri.equals("first.epub"),"Companion EPUB associated with audiobook");check(books.get(1).epubUri.isEmpty(),"EPUB not inherited by unrelated books");
  check(books.size()==2,"Library subfolders produce two books");
  check(books.get(0).chapterCount()==3,"Chapter files grouped into a single book");
  check(books.get(0).trackTitle(0).equals("Chapter 1"),"First chapter naturally ordered");
  check(books.get(0).trackTitle(2).equals("Chapter 10"),"Tenth chapter follows chapter two");
  check(books.get(0).coverUri.equals("cover1"),"Book-specific cover kept");
  check(books.get(1).chapterCount()==2,"CD folders merge into one book");
  check(books.get(1).trackUri(1).equals("cd2-track"),"Disc order retained");
  check(books.get(1).coverUri.equals("cover2"),"Disc chapters inherit parent cover");
  check(group(folder("Empty","empty")).isEmpty(),"Empty folders ignored");
  Book single=new Book("file","Title","Audio");
  check(single.chapterCount()==1&&single.trackUri(0).equals("file"),"Existing single-file imports remain compatible");
  System.out.println("PASS: "+checks+" folder, chapter-ordering, cover and compatibility checks");
 }
}
