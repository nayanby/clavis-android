package com.clavis.reader;

import android.content.Context;
import android.media.*;
import android.net.Uri;
import java.io.*;
import java.nio.*;
import java.util.function.BooleanSupplier;

/** Decodes the selected audiobook file to 16kHz mono PCM. It never opens a microphone. */
final class PcmSource {
 static void write(Context context,String uri,long fromMs,long untilMs,OutputStream output,BooleanSupplier cancelled)throws Exception{
  MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
  try{
   extractor.setDataSource(context,Uri.parse(uri),null);MediaFormat format=null;
   for(int i=0;i<extractor.getTrackCount();i++){MediaFormat candidate=extractor.getTrackFormat(i);String mime=candidate.getString(MediaFormat.KEY_MIME);if(mime!=null&&mime.startsWith("audio/")){format=candidate;extractor.selectTrack(i);break;}}
   if(format==null)throw new IOException("No audio track");
   int rate=format.getInteger(MediaFormat.KEY_SAMPLE_RATE),channels=format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),encoding=AudioFormat.ENCODING_PCM_16BIT;
   format.setInteger(MediaFormat.KEY_PCM_ENCODING,AudioFormat.ENCODING_PCM_16BIT);
   codec=MediaCodec.createDecoderByType(format.getString(MediaFormat.KEY_MIME));codec.configure(format,null,null,0);codec.start();extractor.seekTo(fromMs*1000,MediaExtractor.SEEK_TO_PREVIOUS_SYNC);
   boolean inputDone=false,outputDone=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();double phase=0;ByteArrayOutputStream pcm=new ByteArrayOutputStream(32768);
   while(!outputDone&&!cancelled.getAsBoolean()){
    if(!inputDone){int index=codec.dequeueInputBuffer(10000);if(index>=0){ByteBuffer buffer=codec.getInputBuffer(index);int size=extractor.readSampleData(buffer,0);long pts=extractor.getSampleTime();if(size<0||pts>=untilMs*1000){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true;}else{codec.queueInputBuffer(index,0,size,pts,0);extractor.advance();}}}
    int index=codec.dequeueOutputBuffer(info,10000);
    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){MediaFormat actual=codec.getOutputFormat();rate=actual.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=actual.getInteger(MediaFormat.KEY_CHANNEL_COUNT);if(actual.containsKey(MediaFormat.KEY_PCM_ENCODING))encoding=actual.getInteger(MediaFormat.KEY_PCM_ENCODING);}
    else if(index>=0){
     ByteBuffer buffer=codec.getOutputBuffer(index);buffer.position(info.offset);buffer.limit(info.offset+info.size);buffer.order(ByteOrder.LITTLE_ENDIAN);int bytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:2;int frames=info.size/(bytes*channels);
     for(int frame=0;frame<frames;frame++){
      long timeUs=info.presentationTimeUs+frame*1000000L/rate;float sample=0;for(int c=0;c<channels;c++)sample+=encoding==AudioFormat.ENCODING_PCM_FLOAT?buffer.getFloat()*32767:buffer.getShort();sample/=channels;
      if(timeUs<fromMs*1000||timeUs>=untilMs*1000)continue;phase+=16000;
      while(phase>=rate){short value=(short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,sample));pcm.write(value&255);pcm.write((value>>>8)&255);phase-=rate;}
      if(pcm.size()>=16384){pcm.writeTo(output);pcm.reset();}
     }
     outputDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(index,false);
    }
   }
   if(!cancelled.getAsBoolean()){pcm.writeTo(output);output.flush();}
  }finally{if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}extractor.release();}
 }
}

