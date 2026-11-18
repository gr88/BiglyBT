/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.biglybt.core.config.COConfigurationManager;

public class DownloadControlBTNov18Test
{
	private static final int S = DownloadControlStrictOrder.MODE_SEQUENTIAL;
	private static final int R = DownloadControlStrictOrder.MODE_RANDOM;
	private static final int OFF = DownloadControlStrictOrder.MODE_OFF;

	@Test
	public void unsetFileModeDefaultsToSequentialButStoredModesStay()
	{
		assertThat( DownloadControlFileQueue.effectiveFileMode( OFF, OFF )).isEqualTo( S );
		// an auto-written torrent default R no longer forces R on unset files
		assertThat( DownloadControlFileQueue.effectiveFileMode( R, OFF )).isEqualTo( S );
		assertThat( DownloadControlFileQueue.effectiveFileMode( S, R )).isEqualTo( R );
		assertThat( DownloadControlFileQueue.effectiveFileMode( R, S )).isEqualTo( S );
		assertThat( DownloadControlFileQueue.modeLetterForPendingFile( false, true, OFF, false )).isEqualTo( "S" );
		assertThat( DownloadControlFileQueue.modeLetterForPendingFile( false, true, R, false )).isEqualTo( "R" );
	}

	@Test
	public void newTorrentPlanDefaultsToSequential()
	{
		DownloadControlFileQueue.FileSnap[] files = {
			new DownloadControlFileQueue.FileSnap( 0, 1000, 0, 1, 2, 0, false ),
		};
		DownloadControlFileQueue.NewTorrentDecision d = DownloadControlFileQueue.planNewTorrent(
			files, true, false, false, true, new HashMap<>(), p -> false );
		assertThat( d.torrentDefaultMode ).isEqualTo( S );
		assertThat( d.initialFileMode ).isEqualTo( S );
	}

	@Test
	public void addDialogMultiSelectEnablesModes()
	{
		assertThat( TablePointerHit.addDialogModeDisableReason( 3, 3 )).isNull();
		assertThat( TablePointerHit.addDialogModeDisableReason( 3, 1 )).isNull();
		assertThat( TablePointerHit.addDialogModeDisableReason( 1, 1 )).isNull();
		assertThat( TablePointerHit.addDialogModeDisableReason( 2, 0 )).isEqualTo( TablePointerHit.DISABLE_EMPTY );
		assertThat( TablePointerHit.addDialogModeDisableReason( 0, 0 )).isEqualTo( TablePointerHit.DISABLE_EMPTY );
		assertThat( TablePointerHit.commonMode( new int[]{ S, S, S })).isEqualTo( S );
		assertThat( TablePointerHit.commonMode( new int[]{ S, R })).isEqualTo( 0 );
		assertThat( TablePointerHit.commonMode( new int[0] )).isEqualTo( 0 );
	}

	@Test
	public void versionStringFromVersionTxt() throws Exception
	{
		assertThat( DownloadControlBTVersion.format( "202611101559\n" )).isEqualTo( "DownLoadControlBT202611101559" );
		assertThat( DownloadControlBTVersion.format( "4.1.0.1_B07" )).isNull();
		assertThat( DownloadControlBTVersion.format( "20261110155" )).isNull();
		assertThat( DownloadControlBTVersion.format( null )).isNull();
		File dir = Files.createTempDirectory( "dlcbt-ver" ).toFile();
		try{
			assertThat( DownloadControlBTVersion.readFrom( dir.getPath())).isNull();
			File v = new File( dir, "version.txt" );
			Files.write( v.toPath(), "202611181700\n".getBytes( StandardCharsets.UTF_8 ));
			assertThat( DownloadControlBTVersion.readFrom( dir.getPath())).isEqualTo( "DownLoadControlBT202611181700" );
			v.delete();
		}finally{
			dir.delete();
		}
		// protocol / plugin / peer-id version is untouched
		assertThat( com.biglybt.core.util.Constants.BIGLYBT_VERSION ).doesNotContain( "DownLoadControlBT" );
	}

	@Test
	public void hashFailBanDurationIs210To270Seconds()
	{
		Random rnd = new Random( 7 );
		long min = Long.MAX_VALUE, max = 0;
		for ( int i = 0; i < 5000; i++ ){
			long ms = DownloadControlBTBanService.hashFailDurationMs( rnd );
			assertThat( ms ).isBetween( 210_000L, 270_000L );
			min = Math.min( min, ms );
			max = Math.max( max, ms );
		}
		assertThat( min ).isLessThan( 215_000L );
		assertThat( max ).isGreaterThan( 265_000L );
		assertThat( DownloadControlBTBanRegistry.randomDurationRangeMs( 5, 5, rnd )).isEqualTo( 5 );
	}

	@Test
	public void hashFailBanUsesSeparateReasonAndRespectsBanEnabled()
	{
		System.setProperty( com.biglybt.core.util.SystemProperties.SYSPROP_SECURITY_MANAGER_PERMITEXIT, "true" );
		COConfigurationManager.setParameter( DownloadControlBTDefaults.CFG_PROFILE, true );
		DownloadControlBTBanService service = DownloadControlBTBanService.get();
		try{
			COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_ENABLED, false );
			assertThat( service.onHashFail( "192.0.2.90", "kind=piece" )).isNull();
			assertThat( service.isTemporarilyBanned( "192.0.2.90" )).isFalse();

			COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_ENABLED, true );
			DownloadControlBTBanRegistry.Ban ban = service.onHashFail( "192.0.2.90", "kind=piece" );
			assertThat( ban ).isNotNull();
			assertThat( ban.reason ).isEqualTo( "hash-fail" );
			assertThat( ban.expiryMs - ban.startMs ).isBetween( 210_000L, 270_000L );
			assertThat( service.isTemporarilyBanned( "192.0.2.90" )).isTrue();
			assertThat( service.isTemporarilyBanned( "192.0.2.91" )).isFalse();
			// already banned: keeps the existing ban, no second one
			assertThat( service.onHashFail( "192.0.2.90", "kind=block" )).isNull();
		}finally{
			service.unbanOwn( "192.0.2.90" );
		}
	}

	@Test
	public void badGoodRatioFormatting()
	{
		assertThat( DownloadControlBTPeerBadData.formatRatio( 0, 0 )).isEmpty();
		assertThat( DownloadControlBTPeerBadData.formatRatio( 0, 1000 )).isEqualTo( "0%" );
		assertThat( DownloadControlBTPeerBadData.formatRatio( 125, 1000 )).isEqualTo( "12.5%" );
		assertThat( DownloadControlBTPeerBadData.formatRatio( 2000, 1000 )).isEqualTo( "200.0%" );
		assertThat( DownloadControlBTPeerBadData.formatRatio( 16384, 0 )).isEqualTo( "\u221E" );
		assertThat( DownloadControlBTPeerBadData.goodBytes( 1000, 300 )).isEqualTo( 700 );
		assertThat( DownloadControlBTPeerBadData.goodBytes( 100, 300 )).isEqualTo( 0 );
		assertThat( DownloadControlBTPeerBadData.sortValue( 0, 0 )).isEqualTo( -1 );
		assertThat( DownloadControlBTPeerBadData.sortValue( 1, 0 )).isEqualTo( Long.MAX_VALUE );
		Object peer = new Object();
		DownloadControlBTPeerBadData.addBad( peer, 16384 );
		DownloadControlBTPeerBadData.addBad( peer, 16384 );
		DownloadControlBTPeerBadData.addBad( peer, -5 );
		assertThat( DownloadControlBTPeerBadData.getBad( peer )).isEqualTo( 32768 );
		assertThat( DownloadControlBTPeerBadData.getBad( new Object())).isEqualTo( 0 );
	}

	@Test
	public void connectingKickAfterThirtySecondsOnlyForOutgoingDlcbt()
	{
		long start = 1_000_000L;
		assertThat( DownloadControlBTConnectTimeout.shouldKick( true, false, true, start, start + 29_999 )).isFalse();
		assertThat( DownloadControlBTConnectTimeout.shouldKick( true, false, true, start, start + 30_000 )).isTrue();
		assertThat( DownloadControlBTConnectTimeout.shouldKick( false, false, true, start, start + 60_000 )).isFalse();
		assertThat( DownloadControlBTConnectTimeout.shouldKick( true, true, true, start, start + 60_000 )).isFalse();
		assertThat( DownloadControlBTConnectTimeout.shouldKick( true, false, false, start, start + 60_000 )).isFalse();
		assertThat( DownloadControlBTConnectTimeout.shouldKick( true, false, true, 0, start )).isFalse();
	}
}
