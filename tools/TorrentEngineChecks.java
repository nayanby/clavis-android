package com.clavis.reader;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.libtorrent4j.*;
public final class TorrentEngineChecks {
 public static void main(String[] args)throws Exception{
  File base=new File(args[0]),seedDir=new File(base,"seed"),downloadDir=new File(base,"download");seedDir.mkdirs();downloadDir.mkdirs();File source=new File(seedDir,"Reson-test.wav");byte[] data=new byte[262144];new Random(42).nextBytes(data);Files.write(source.toPath(),data);
  TorrentInfo info=new TorrentInfo(new TorrentBuilder().path(source).generate().entry().bencode());
  SessionManager seed=new SessionManager();LibreTorrentSession client=new LibreTorrentSession(new File(base,"state"));
  SettingsPack seedSettings=new SettingsPack().listenInterfaces("127.0.0.1:48991"),clientSettings=LibreTorrentSession.configuration().listenInterfaces("127.0.0.1:48992");seedSettings.setEnableDht(false);seedSettings.setEnableLsd(false);clientSettings.setEnableDht(false);clientSettings.setEnableLsd(false);
  try{seed.start(new SessionParams(seedSettings));client.start(new SessionParams(clientSettings));seed.download(info,seedDir);String magnet="magnet:?xt=urn:btih:"+info.infoHash().toHex()+"&dn=Reson-test";MagnetLink parsed=MagnetLink.parse(magnet);client.add(parsed.uri,downloadDir);
   long deadline=System.currentTimeMillis()+30000;boolean complete=false;while(System.currentTimeMillis()<deadline){TorrentHandle s=seed.find(info.infoHash()),d=client.find(info.infoHash());if(s!=null&&s.isValid()){s.unsetFlags(TorrentFlags.AUTO_MANAGED);s.resume();}if(d!=null&&d.isValid()){d.unsetFlags(TorrentFlags.AUTO_MANAGED);d.resume();d.swig().connect_peer(new TcpEndpoint("127.0.0.1",48991).swig());if(d.status(true).isFinished()){complete=true;break;}}Thread.sleep(250);}
   if(!complete)throw new AssertionError("Local magnet transfer did not complete");File downloaded=new File(downloadDir,"Reson-test.wav");if(!Arrays.equals(data,Files.readAllBytes(downloaded.toPath())))throw new AssertionError("Downloaded bytes differ");TorrentHandle handle=client.find(info.infoHash());handle.pause();handle.resume();client.checkpoint(true);if(!new File(base,"state/session.state").isFile())throw new AssertionError("Session state missing");if(!new File(base,"state/"+info.infoHash().toHex()+".resume").isFile())throw new AssertionError("Resume state missing");client.remove(handle);TorrentHandle restored=client.add(parsed.uri,downloadDir);if(restored==null||!restored.isValid())throw new AssertionError("Resume restore failed");client.remove(restored);System.out.println("Torrent engine: magnet metadata, local peer transfer, byte verification, pause/resume, session checkpoint, fast-resume restoration and remove passed");
  }finally{client.stop();seed.stop();}
 }
}
