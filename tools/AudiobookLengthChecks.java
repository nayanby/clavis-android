package com.clavis.reader;
public final class AudiobookLengthChecks {
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){Book b=new Book("single","Book","Audio");check(b.totalAudioLength(u->9000)==9000);check(b.totalAudioLength(u->-1)==-1);b.chapters.add(new Book.Chapter("a","A"));b.chapters.add(new Book.Chapter("b","B"));check(b.totalAudioLength(u->u.equals("a")?1000:2000)==3000);check(b.totalAudioLength(u->u.equals("a")?1000:-1)==-1);b.chapters.clear();b.chapters.add(new Book.Chapter("same","One",0,1000));b.chapters.add(new Book.Chapter("same","Two",1000,3000));check(b.totalAudioLength(u->9999)==3000);b.chapters.get(1).endMs=-1;check(b.totalAudioLength(u->4000)==4000);System.out.println("6 audiobook length checks passed");}
}
