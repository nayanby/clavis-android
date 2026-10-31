package com.clavis.reader;
public final class MagnetLinkChecks {
 static int count;static void check(boolean value){count++;if(!value)throw new AssertionError("Check "+count);}
 static void invalid(String value){try{MagnetLink.parse(value);throw new AssertionError("Accepted invalid link");}catch(IllegalArgumentException expected){count++;}}
 public static void main(String[] args){String hash="0123456789abcdef0123456789abcdef01234567";MagnetLink link=MagnetLink.parse("magnet:?xt=urn:btih:"+hash+"&dn=A+Book&tr=udp%3A%2F%2Ftracker.test%3A80");check(link.id.equals(hash));check(link.name.equals("A Book"));check(MagnetLink.parse("magnet:?xt=urn:btih:AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA").id.equals("0000000000000000000000000000000000000000"));check(MagnetLink.parse("magnet:?xt=urn:btmh:1220"+hash+"0123456789abcdef01234567").id.length()==68);invalid("https://example.com/book");invalid("magnet:?dn=Missing+hash");invalid("magnet:?xt=urn:btih:../bad");invalid("magnet:?xt=urn:btih:1234");invalid("magnet:?xt=%ZZ");check(MagnetLink.parse("magnet:?xt=urn:btih:"+hash.toUpperCase()).id.equals(hash));System.out.println(count+" magnet link checks passed");}
}
