package com.clavis.reader;

import android.content.Context;
import android.os.*;
import org.json.*;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;

/** Bundled offline file transcription: no microphone, Android speech pack, key or server. */
final class LiveTranscriber {
 interface Listener{void status(String message);void partial(String text);void result(long from,long until,List<TranscriptLine> lines);void failure(String message);}
 private static Model sharedModel;
 private final Context context;private final Listener listener;private final Handler main=new Handler(Looper.getMainLooper());private final ExecutorService worker=Executors.newSingleThreadExecutor();private volatile int generation;private volatile boolean busy,closed;
 LiveTranscriber(Context context,Listener listener){this.context=context.getApplicationContext();this.listener=listener;}
 boolean busy(){return busy;}
 static boolean available(Context context){return true;}
 private static synchronized Model model(Context context)throws Exception{
  if(sharedModel!=null)return sharedModel;
  File base=new File(context.getNoBackupFilesDir(),"speech-en-0.22-lgraph");File folder=new File(base,"vosk-model-en-us-0.22-lgraph");File ready=new File(base,"ready");
  if(!ready.isFile()){
   base.mkdirs();String safeRoot=base.getCanonicalPath()+File.separator;
   try(ZipInputStream zip=new ZipInputStream(context.getAssets().open("speech/english-model.zip"))){ZipEntry entry;byte[] bytes=new byte[65536];while((entry=zip.getNextEntry())!=null){File target=new File(base,entry.getName());if(!target.getCanonicalPath().startsWith(safeRoot))throw new IOException("Invalid model archive path");if(entry.isDirectory())target.mkdirs();else{File parent=target.getParentFile();if(parent!=null)parent.mkdirs();try(OutputStream out=new FileOutputStream(target)){int n;while((n=zip.read(bytes))!=-1)out.write(bytes,0,n);}}zip.closeEntry();}}
   if(!new File(folder,"am/final.mdl").isFile())throw new IOException("Bundled speech model is incomplete");ready.createNewFile();
  }
  sharedModel=new Model(folder.getAbsolutePath());return sharedModel;
 }
 void recognize(String uri,long from,long until){recognize(uri,from,until,0);}
 void recognize(String uri,long from,long until,long offset){
  if(closed||busy)return;busy=true;int token=++generation;listener.status("Preparing offline transcription…");
  worker.execute(()->{try{
   Model loaded=model(context);if(token!=generation||closed)return;
   main.post(()->{if(token==generation&&!closed)listener.status("Offline live text · English");});
   ArrayList<TranscriptLine> combined=new ArrayList<>();
   try(Recognizer recognizer=new Recognizer(loaded,16000f)){
    recognizer.setWords(true);
    OutputStream sink=new OutputStream(){
     public void write(int value)throws IOException{throw new IOException("PCM must be written in blocks");}
     public void write(byte[] bytes,int offset,int length)throws IOException{
      if(token!=generation||closed)throw new InterruptedIOException("Transcription stopped");
      byte[] block=offset==0?bytes:Arrays.copyOfRange(bytes,offset,offset+length);
      if(recognizer.acceptWaveForm(block,length)){try{String json=recognizer.getResult();append(json,combined,from,true);long committed=committedUntil(json,from);if(committed>from)deliver(token,from,Math.min(until,committed),combined);}catch(Exception error){throw new IOException("Invalid speech result",error);}}

     }
    };
    PcmSource.write(context,uri,from+offset,until+offset,sink,()->token!=generation||closed);
    if(token!=generation||closed)return;append(recognizer.getFinalResult(),combined,from,false);deliver(token,from,until,combined);
   }
   main.post(()->{if(token==generation&&!closed){busy=false;listener.status("Offline live text · following playback");}});
  }catch(Exception|LinkageError error){main.post(()->{if(token==generation&&!closed){busy=false;listener.failure("Could not transcribe this passage. Tap Retry. "+error.getMessage());}});}});
 }
 private long committedUntil(String json,long from)throws JSONException{
  JSONArray words=new JSONObject(json).optJSONArray("result");return words==null||words.length()==0?from:from+Math.round(words.getJSONObject(words.length()-1).optDouble("end")*1000);
 }
 private void append(String json,List<TranscriptLine> lines,long from,boolean endpoint)throws Exception{
  JSONObject result=new JSONObject(json);JSONArray words=result.optJSONArray("result");if(words==null||words.length()==0)return;StringBuilder text=new StringBuilder();long start=0;int count=0;
  for(int i=0;i<words.length();i++){JSONObject word=words.getJSONObject(i);if(count==0)start=from+Math.round(word.optDouble("start")*1000);if(text.length()>0)text.append(' ');String token=word.optString("word");if(i==0&&!token.isEmpty())token=Character.toUpperCase(token.charAt(0))+token.substring(1);if(token.equals("i"))token="I";text.append(token);double gap=i+1<words.length()?words.getJSONObject(i+1).optDouble("start")-word.optDouble("end"):0;if(gap>=.9||(endpoint&&i==words.length()-1))text.append('.');else if(gap>=.4)text.append(',');count++;if(count>=8){lines.add(new TranscriptLine(start,text.toString()));text.setLength(0);count=0;}}
  if(text.length()>0)lines.add(new TranscriptLine(start,text.toString()));
 }
 private void deliver(int token,long from,long until,List<TranscriptLine> lines){ArrayList<TranscriptLine> copy=new ArrayList<>(lines);main.post(()->{if(token==generation&&!closed)listener.result(from,until,copy);});}
 void cancelCurrent(){generation++;busy=false;}
 void close(){closed=true;cancelCurrent();main.removeCallbacksAndMessages(null);worker.shutdownNow();}
}

