package com.biglybt.core.downloadcontrolbt;
import java.io.*;
import java.nio.file.*;
import java.net.*;
import java.util.*;
import com.biglybt.core.*;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.*;
import com.biglybt.core.global.*;
import com.biglybt.core.torrent.*;
public class DownloadControlBTDiskProbe {
 public static void main(String[] args) throws Exception {
  File root = new File(args[0]).getCanonicalFile(); root.mkdirs();
  boolean reopen = args.length > 1;
  System.setProperty("azureus.config.path",new File(root,"config").getPath());
  System.setProperty("azureus.install.path",root.getPath());
  System.setProperty("azureus.loadplugins","0");
  System.setProperty("azureus.platform.manager.disable","true");
  System.setProperty("azureus.security.manager.permitexit","true");
  System.setProperty("MULTI_INSTANCE","true");
  COConfigurationManager.initialise();
  COConfigurationManager.setParameter("dlcbt.profile.enabled",true);
  COConfigurationManager.setParameter("DefaultDir.AutoSave.AutoRename",false);
  COConfigurationManager.setParameter("Rename Incomplete Files",true);
  COConfigurationManager.setParameter("Rename Incomplete Files Extension",".incomplete");
  COConfigurationManager.setParameter("Bind IP","127.0.0.1");
  COConfigurationManager.setParameter("Enforce Bind IP",true);
  COConfigurationManager.setParameter("TCP.Listen.Port.Enable",false);
  COConfigurationManager.setParameter("UDP.Listen.Port.Enable",false);
  COConfigurationManager.setParameter("UDP.NonData.Listen.Port.Enable",false);
  COConfigurationManager.setParameter("Send Version Info",false);
  COConfigurationManager.setParameter("versioncheck.test.address.false","127.0.0.1");
  COConfigurationManager.setParameter("versioncheck.test.address.true","::1");
  COConfigurationManager.setParameter("update.start",false);
  COConfigurationManager.setParameter("update.periodic",false);
  COConfigurationManager.setParameter("Plugin.DHT.dht.enabled",false);
  COConfigurationManager.setParameter("Save Torrent Files",false);
  Core core = CoreFactory.create(); core.start();
  try {
   for(int c=0;c<4;c++) {
    byte[] bytes=new byte[65536]; new Random(555+c).nextBytes(bytes);
    File srcDir=new File(root,"original-"+c); srcDir.mkdirs();
    File source=new File(srcDir,"synthetic-"+c+".bin"); Files.write(source.toPath(),bytes);
    TOTorrent torrent=TOTorrentFactory.createFromFileOrDirWithFixedPieceLength(source,new URL("http://127.0.0.1:1/announce"),16384).create();
    torrent.setPrivate(true);
    File tf=new File(root,"synthetic-"+c+".torrent"); torrent.serialiseToBEncodedFile(tf);
    File destination=new File(root,"moved-"+c); destination.mkdirs();
    boolean partial=c%2==0; boolean suffix=c>=2;
    File target=new File(destination,source.getName()+(suffix?".incomplete":""));
    if (!reopen) Files.write(target.toPath(),Arrays.copyOf(bytes,partial?32768:65536));
    DownloadManager dm=core.getGlobalManager().addDownloadManager(tf.getPath(),null,destination.getPath(),DownloadManager.STATE_STOPPED,true);
    // Simulate a legacy DLCBT-enabled download, without resetting data or queue.
    dm.getDownloadState().setBooleanAttribute(DownloadManagerState.AT_DLCBT_ENABLED,true);
    dm.getDownloadState().setIntAttribute(DownloadManagerState.AT_DLCBT_FILE_INDEX,0);
    dm.getDownloadState().setIntAttribute(DownloadManagerState.AT_DLCBT_MODE,1);
    dm.forceRecheck();
    long deadline=System.currentTimeMillis()+25000;
    do { Thread.sleep(50); } while ((dm.isForceRechecking() || dm.getState()!=DownloadManager.STATE_STOPPED) && System.currentTimeMillis()<deadline);
    long have=dm.getDiskManagerFileInfoSet().getFiles()[0].getDownloaded();
    long expected=partial?32768:65536;
    if(have!=expected) throw new AssertionError("case="+c+" state="+dm.getState()+" have="+have+" expected="+expected+" error="+dm.getErrorDetails());
    System.out.println("RECHECK_OK case="+c+" partial="+partial+" suffix="+suffix+" bytes="+have);
   }
  } finally { core.stop(); }
  System.out.println("ALL_RECHECK_OK"); System.exit(0);
 }
}
