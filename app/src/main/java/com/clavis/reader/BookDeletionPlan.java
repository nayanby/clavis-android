package com.clavis.reader;
import java.util.*;import java.net.URI;
final class BookDeletionPlan {
 static ArrayList<String> files(Book book,List<Book> library){Set<String> shared=new HashSet<>();for(Book other:library)if(!other.uri.equals(book.uri))for(String uri:resources(other))shared.add(key(uri));LinkedHashMap<String,String> unique=new LinkedHashMap<>();for(String uri:resources(book))if(!shared.contains(key(uri)))unique.put(key(uri),uri);return new ArrayList<>(unique.values());}
 static ArrayList<String> resources(Book book){ArrayList<String> out=new ArrayList<>();if(book.chapters.isEmpty())out.add(book.uri);else for(Book.Chapter chapter:book.chapters)out.add(chapter.uri);if(book.coverUri!=null&&!book.coverUri.isEmpty())out.add(book.coverUri);if(book.epubUri!=null&&!book.epubUri.isEmpty())out.add(book.epubUri);return out;}
 static String key(String value){try{URI uri=URI.create(value);if("file".equalsIgnoreCase(uri.getScheme()))return "file:"+new java.io.File(uri).getCanonicalPath();String path=uri.getPath();int at=path==null?-1:path.indexOf("/document/");if(at>=0)return uri.getAuthority()+":"+path.substring(at+10);return uri.getScheme()+":"+uri.getAuthority()+":"+path;}catch(Exception e){return value;}}
}
