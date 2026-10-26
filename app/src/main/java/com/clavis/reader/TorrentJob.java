package com.clavis.reader;
import org.json.*;
final class TorrentJob {
 final String id,magnet;volatile String name,state="Waiting for peers",error="";volatile boolean paused,done,imported,queued;volatile float progress;volatile long bytes,total;final DownloadEstimate estimate=new DownloadEstimate();volatile long etaSeconds=-1;volatile int rate,peers,seeds,knownSeeds;volatile long dhtNodes;
 TorrentJob(MagnetLink link){id=link.id;magnet=link.uri;name=link.name;}
 TorrentJob(JSONObject j){id=j.optString("id");magnet=j.optString("magnet");name=j.optString("name");paused=j.optBoolean("paused");done=j.optBoolean("done");imported=j.optBoolean("imported");progress=(float)j.optDouble("progress");bytes=j.optLong("bytes");total=j.optLong("total");error=j.optString("error");peers=j.optInt("peers");seeds=j.optInt("seeds");knownSeeds=j.optInt("knownSeeds");dhtNodes=j.optLong("dhtNodes");state=done?"Completed":paused?"Paused":"Ready to resume";}
 JSONObject json(){JSONObject j=new JSONObject();try{j.put("id",id).put("magnet",magnet).put("name",name).put("paused",paused).put("done",done).put("imported",imported).put("progress",progress).put("bytes",bytes).put("total",total).put("error",error).put("peers",peers).put("seeds",seeds).put("knownSeeds",knownSeeds).put("dhtNodes",dhtNodes).put("etaSeconds",etaSeconds).put("rate",rate).put("state",state);}catch(Exception ignored){}return j;}
}
