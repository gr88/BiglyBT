/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DownloadControlBTRestartTransportTest
{
	@TempDir
	File tmp;

	@Test
	public void detachedChildWritesMarkerAfterWatchedPidExits()
		throws Exception
	{
		File marker = new File( tmp, "marker.txt" );
		File childLog = new File( tmp, "restart-child.log" );
		File script = new File( tmp, "dummy-restart.sh" );
		String scriptBody = "#!/usr/bin/env bash\n"
			+ "set -euo pipefail\n"
			+ "echo \"CHILD_BANNER stdout\"\n"
			+ "echo \"CHILD_OK\" > \"" + marker.getAbsolutePath() + "\"\n"
			+ "echo \"CHILD_OK\" >&2\n";
		Files.write( script.toPath(), scriptBody.getBytes( StandardCharsets.UTF_8 ));
		script.setExecutable( true );

		File pidFile = new File( tmp, "sleeper.pid" );
		Process sleeper = new ProcessBuilder( "bash", "-c",
			"echo $$ > \"" + pidFile.getAbsolutePath() + "\"; sleep 0.4" ).start();
		long sleeperPid = 0;
		for ( int i = 0; i < 50 && sleeperPid <= 0; i++ ){
			if ( pidFile.isFile()){
				String raw = new String( java.nio.file.Files.readAllBytes( pidFile.toPath() ), StandardCharsets.UTF_8 ).trim();
				if ( !raw.isEmpty()){
					sleeperPid = Long.parseLong( raw );
				}
			}
			if ( sleeperPid <= 0 ){
				Thread.sleep( 20 );
			}
		}
		assertThat( sleeperPid ).isGreaterThan( 0 );

		Process child = DownloadControlBTRestartTransport.startLauncher(
			script, childLog, sleeperPid );
		assertThat( sleeper.waitFor( 5, TimeUnit.SECONDS )).isTrue();
		assertThat( child.waitFor( 8, TimeUnit.SECONDS )).isTrue();
		assertThat( child.exitValue() ).isZero();

		assertThat( marker ).exists();
		assertThat( Files.readString( marker.toPath() )).contains( "CHILD_OK" );
		assertThat( childLog ).exists();
		String log = Files.readString( childLog.toPath());
		assertThat( log ).contains( "CHILD_BANNER" );
		assertThat( child.isAlive() ).isFalse();
	}

	@Test
	public void alreadyDeadParentPidStartsImmediately()
		throws Exception
	{
		File marker = new File( tmp, "quick.txt" );
		File childLog = new File( tmp, "quick.log" );
		File script = new File( tmp, "quick.sh" );
		Files.write( script.toPath(), (
			"#!/usr/bin/env bash\necho QUICK > \"" + marker.getAbsolutePath() + "\"\n"
		).getBytes( StandardCharsets.UTF_8 ));
		script.setExecutable( true );

		// Pid 0 is not a live process that kill -0 will succeed on as a user JVM.
		Process child = DownloadControlBTRestartTransport.startLauncher( script, childLog, 0 );
		assertThat( child.waitFor( 5, TimeUnit.SECONDS )).isTrue();
		assertThat( marker ).exists();
	}

}
