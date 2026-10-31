package com.clavis.reader;
import java.util.*;
public final class RecentBooksChecks {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
 ArrayList<Book> books=new ArrayList<>();
 for(int i=1;i<=6;i++){Book b=new Book("book"+i,"Book "+i,"EPUB");b.lastReadAt=i;books.add(b);}
 Book unread=new Book("unread","Unread","EPUB");books.add(unread);
 ArrayList<Book> recent=Store.recent(books);
 check(recent.size()==4);for(int i=0;i<4;i++)check(recent.get(i).lastReadAt==6-i);
 check(books.size()==7&&books.get(0).lastReadAt==1);
 Book legacy=new Book("legacy","Legacy","PDF");legacy.position=3;
 check(Store.recent(Arrays.asList(unread,legacy)).equals(Arrays.asList(legacy)));
 check(Store.recent(Collections.emptyList()).isEmpty());
 System.out.println("8 recent books checks passed");
 }
}
