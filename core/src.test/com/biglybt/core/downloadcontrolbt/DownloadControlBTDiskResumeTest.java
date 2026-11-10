package com.biglybt.core.downloadcontrolbt;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*;
import java.io.File;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real disk-manager hashing, with local synthetic data and all downloads stopped. */
public class DownloadControlBTDiskResumeTest {
 @TempDir Path root;
 @Test public void movedCompletePartialAndSuffixedFilesRecheckAndSurviveCoreRestart() throws Exception {
  for (int pass=0;pass<2;pass++) {
   File log=root.resolve("probe-"+pass+".log").toFile();
   ProcessBuilder pb=new ProcessBuilder(new File(System.getProperty("java.home"),"bin/java").getPath(),
     "--add-opens=java.base/java.net=ALL-UNNAMED", "-cp",System.getProperty("java.class.path"),
     DownloadControlBTDiskProbe.class.getName(),root.toString(),pass==0?"":"reopen");
   // An empty trailing arg isn't a reopen flag for the initial pass.
   if(pass==0) pb.command().remove(pb.command().size()-1);
   pb.redirectErrorStream(true).redirectOutput(log);
   Process process=pb.start();
   boolean ended=process.waitFor(90,TimeUnit.SECONDS);
   if(!ended) process.destroyForcibly();
   String output=new String(Files.readAllBytes(log.toPath()),java.nio.charset.StandardCharsets.UTF_8);
   assertThat(ended).as("isolated recheck fixture finished: %s",output).isTrue();
   assertThat(process.exitValue()).as("isolated recheck output: %s",output).isZero();
   assertThat(output).contains("ALL_RECHECK_OK");
   for(int c=0;c<4;c++) assertThat(output).contains("RECHECK_OK case="+c);
  }
 }
}
