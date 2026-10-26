package com.clavis.reader;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
/** Editable journal text lives beside its notebook metadata, and follows rename/delete. */
final class TextEntryStore {
 static File file(File folder){return new File(folder,"entry.txt");}
 static boolean isText(File folder){return file(folder).isFile()||new File(file(folder).getPath()+".bak").isFile();}
 static String read(File folder)throws IOException{AtomicFile atomic=new AtomicFile(file(folder));if(!isText(folder))return "";return new String(atomic.readFully(),StandardCharsets.UTF_8);}
 static void write(File folder,String text)throws IOException{if(!folder.isDirectory()&&!folder.mkdirs())throw new IOException("Cannot open entry folder");AtomicFile atomic=new AtomicFile(file(folder));FileOutputStream out=null;try{out=atomic.startWrite();out.write(text.getBytes(StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(IOException e){if(out!=null)atomic.failWrite(out);throw e;}}
}