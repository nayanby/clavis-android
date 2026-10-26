/*
 * Portions adapted from LibreTorrent SessionSettings and TorrentSessionImpl.
 * Copyright (C) 2016-2025 Yaroslav Pronin <proninyaroslav@mail.ru>
 * Clavis adaptation, 2026. SPDX-License-Identifier: GPL-3.0-or-later
 * This program is free software under GNU GPL version 3 or later, without warranty.
 * See licenses/LibreTorrent-GPL-3.0.txt and LibreTorrent-NOTICE.md.
 */
package com.clavis.reader;
import org.libtorrent4j.*;
import org.libtorrent4j.alerts.*;
import org.libtorrent4j.swig.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
/** LibreTorrent's Android session policy and persistent resume/DHT state, adapted for Clavis. */
final class LibreTorrentSession extends SessionManager {
 private final File stateDir;private final Set<String> pending=ConcurrentHashMap.newKeySet();private volatile boolean saving;
 LibreTorrentSession(File stateDir){this.stateDir=stateDir;stateDir.mkdirs();addListener(new AlertListener(){public int[] types(){return new int[]{AlertType.METADATA_RECEIVED.swig(),AlertType.SAVE_RESUME_DATA.swig(),AlertType.SAVE_RESUME_DATA_FAILED.swig()};}public void alert(Alert<?> alert){try{TorrentHandle h=((TorrentAlert<?>)alert).handle();String hash=h.infoHash().toHex();if(alert instanceof MetadataReceivedAlert)h.saveResumeData(TorrentHandle.SAVE_INFO_DICT);else if(alert instanceof SaveResumeDataAlert){write(new File(stateDir,hash+".resume"),AddTorrentParams.writeResumeDataBuf(((SaveResumeDataAlert)alert).params()));pending.remove(hash);}else pending.remove(hash);}catch(Exception e){System.err.println("Clavis resume state: "+e.getMessage());}}});}
 static SettingsPack configuration(){SettingsPack sp=new SettingsPack();sp.activeDownloads(4);sp.activeSeeds(4);sp.activeLimit(6);sp.maxPeerlistSize(200);sp.tickInterval(1000);sp.inactivityTimeout(60);sp.connectionsLimit(200);sp.downloadRateLimit(0);sp.uploadRateLimit(0);sp.seedingOutgoingConnections(true);int port=37000+new Random().nextInt(20011);sp.listenInterfaces("0.0.0.0:"+port+",[::]:"+port);sp.setInteger(settings_pack.int_types.max_retry_port_bind.swigValue(),10);sp.setEnableDht(true);sp.setEnableLsd(true);
  sp.setBoolean(settings_pack.bool_types.enable_incoming_utp.swigValue(),true);sp.setBoolean(settings_pack.bool_types.enable_outgoing_utp.swigValue(),true);sp.setBoolean(settings_pack.bool_types.enable_upnp.swigValue(),true);sp.setBoolean(settings_pack.bool_types.enable_natpmp.swigValue(),true);sp.setBoolean(settings_pack.bool_types.enable_ip_notifier.swigValue(),false);sp.setBoolean(settings_pack.bool_types.announce_to_all_trackers.swigValue(),true);sp.setBoolean(settings_pack.bool_types.announce_to_all_tiers.swigValue(),true);sp.setBoolean(settings_pack.bool_types.validate_https_trackers.swigValue(),true);sp.alertQueueSize(5000);
  sp.setInteger(settings_pack.int_types.in_enc_policy.swigValue(),settings_pack.enc_policy.pe_enabled.swigValue());sp.setInteger(settings_pack.int_types.out_enc_policy.swigValue(),settings_pack.enc_policy.pe_enabled.swigValue());sp.setInteger(settings_pack.int_types.allowed_enc_level.swigValue(),settings_pack.enc_level.pe_both.swigValue());sp.setString(settings_pack.string_types.user_agent.swigValue(),"Clavis/0.6 (LibreTorrent-derived session)");sp.setDhtBootstrapNodes("dht.libtorrent.org:25401,router.bittorrent.com:6881,dht.transmissionbt.com:6881,outer.silotis.us:6881");return sp;
 }
 @Override public void start(){SessionParams params;try{params=new SessionParams(new File(stateDir,"session.state"));}catch(Exception e){params=new SessionParams();}params.setSettings(configuration());params.setPosixDiskIO();super.start(params);}
 TorrentHandle add(String magnet,File folder){AddTorrentParams original=AddTorrentParams.parseMagnetUri(magnet),params=original;File resume=new File(stateDir,original.getInfoHashes().getBest().toHex()+".resume");if(resume.isFile()&&folder.isDirectory()&&folder.list()!=null&&folder.list().length>0)try{error_code ec=new error_code();add_torrent_params nativeParams=libtorrent.read_resume_data_ex(Vectors.bytes2byte_vector(Files.readAllBytes(resume.toPath())),ec);if(ec.value()==0){AddTorrentParams saved=new AddTorrentParams(nativeParams);if(saved.getInfoHashes().getBest().equals(original.getInfoHashes().getBest()))params=saved;}}catch(Exception ignored){}
  java.util.LinkedHashSet<String> trackers=new java.util.LinkedHashSet<>(params.getTrackers());trackers.addAll(original.getTrackers());params.setTrackers(new java.util.ArrayList<>(trackers));params.setSavePath(folder.getAbsolutePath());if(params.getName().isEmpty())params.setName(original.getName());params.setMaxConnections(40);params.setMaxUploads(4);params.setFlags(params.getFlags().and_(TorrentFlags.AUTO_MANAGED.or_(TorrentFlags.PAUSED).or_(TorrentFlags.STOP_WHEN_READY).inv()));ErrorCode ec=new ErrorCode(new error_code());TorrentHandle h=new SessionHandle(swig()).addTorrent(params,ec);if(ec.getValue()!=0)throw new IllegalStateException(ec.getMessage());h.unsetFlags(TorrentFlags.AUTO_MANAGED);h.resume();return h;
 }
 void checkpoint(boolean flush){if(!isRunning()||saving)return;saving=true;try{write(new File(stateDir,"session.state"),saveState());for(TorrentHandle h:new SessionHandle(swig()).torrents()){if(!h.isValid())continue;pending.add(h.infoHash().toHex());h.saveResumeData(flush?TorrentHandle.SAVE_INFO_DICT.or_(TorrentHandle.FLUSH_DISK_CACHE):TorrentHandle.SAVE_INFO_DICT);}if(flush){long until=System.currentTimeMillis()+1500;while(!pending.isEmpty()&&System.currentTimeMillis()<until)Thread.sleep(20);}}catch(Exception e){System.err.println("Clavis checkpoint: "+e.getMessage());}finally{saving=false;}}
 private static void write(File file,byte[] data)throws IOException{File temp=new File(file.getPath()+".tmp");Files.write(temp.toPath(),data);Files.move(temp.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
}
