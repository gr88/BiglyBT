/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.ui.config.ConfigSectionDownloadControlBT;
import com.biglybt.ui.config.ConfigSectionDownloadControlBTRules;

public class DownloadControlBTNov21Test
{
	@AfterEach
	public void resetBanState()
	{
		DownloadControlBTBanService.get().unbanAllOwn();
		COConfigurationManager.removeParameter( DownloadControlBTDefaults.CFG_PROFILE );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_ENABLED );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_SPEED_ENABLED );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_HANDSHAKE_ENABLED );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_HANDSHAKE_SECONDS );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_DUP_ENABLED );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_DUP_THRESHOLD );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_DUP_WINDOW_SECONDS );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_HASH_FAIL_ENABLED );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_HASH_FAIL_MIN_SECONDS );
		COConfigurationManager.removeParameter( DownloadControlBTBanService.CFG_HASH_FAIL_MAX_SECONDS );
		COConfigurationManager.removeParameter( DownloadControlBTIdleBan.CFG_ENABLED );
		COConfigurationManager.removeParameter( DownloadControlBTIdleBan.CFG_IDLE_SECONDS );
		COConfigurationManager.removeParameter( DownloadControlBTConnectTimeout.CFG_ENABLED );
		COConfigurationManager.removeParameter( DownloadControlBTConnectTimeout.CFG_KICK_SECONDS );
		COConfigurationManager.removeParameter( DownloadControlBTDefaults.CFG_RAREST_ONLY );
		COConfigurationManager.removeParameter( DownloadControlBTRarestUpload.CFG_DISCONNECT_ENABLED );
		COConfigurationManager.removeParameter( DownloadControlBTRarestUpload.CFG_DISCONNECT_REJECTS );
		COConfigurationManager.removeParameter( "Default save path" );
		COConfigurationManager.removeParameter( "General_sDefaultTorrent_Directory" );
		COConfigurationManager.removeParameter( "previous.filter.dir.data" );
		COConfigurationManager.removeParameter( "previous.filter.dir.torrent" );
	}

	@Test
	public void rulesSectionBuildsUnderDlcbtParentAndSummarizesActiveBans()
	{
		COConfigurationManager.setParameter( DownloadControlBTDefaults.CFG_PROFILE, true );
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_ENABLED, true );
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_HASH_FAIL_ENABLED, true );

		ConfigSectionDownloadControlBTRules section = new ConfigSectionDownloadControlBTRules();
		assertThat( section.getParentSectionID()).isEqualTo( ConfigSectionDownloadControlBT.SECTION_ID );
		section.build();
		assertThat( section.getParamArray().length).isGreaterThan( 15 );

		assertThat( DownloadControlBTBanService.get().formatActiveBansSummary( System.currentTimeMillis()))
			.contains( "Active DLCBT temporary bans: 0" );

		DownloadControlBTBanRegistry.Ban ban = DownloadControlBTBanService.get().onHashFail( "203.0.113.42", "piece=7" );
		assertThat( ban ).isNotNull();
		String summary = DownloadControlBTBanService.get().formatActiveBansSummary( ban.startMs + 1000L );
		assertThat( summary )
			.contains( "Active DLCBT temporary bans: 1" )
			.contains( "203.0.113.42" )
			.contains( "hash-fail" );
	}

	@Test
	public void perRuleTogglesAndThresholdsControlBansAndKicks()
	{
		COConfigurationManager.setParameter( DownloadControlBTDefaults.CFG_PROFILE, true );
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_ENABLED, true );

		// Handshake toggle and custom threshold
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_HANDSHAKE_ENABLED, false );
		assertThat( DownloadControlBTBanService.get().onHandshakeTimeout(
			"198.51.100.1", PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE, 1_000L, 10_000L )).isNull();
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_HANDSHAKE_ENABLED, true );
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_HANDSHAKE_SECONDS, 8 );
		assertThat( DownloadControlBTBanService.get().onHandshakeTimeout(
			"198.51.100.1", PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE, 1_000L, 8_500L )).isNull();
		assertThat( DownloadControlBTBanService.get().onHandshakeTimeout(
			"198.51.100.1", PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE, 1_000L, 9_500L )).isNotNull();

		// Duplicate threshold updates at runtime without restart
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_DUP_THRESHOLD, 2 );
		assertThat( DownloadControlBTBanService.get().onRejectedIncomingDuplicate(
			"t1", "198.51.100.2", true, true )).isNull();
		assertThat( DownloadControlBTBanService.get().onRejectedIncomingDuplicate(
			"t1", "198.51.100.2", true, true )).isNotNull();

		// Hash-fail custom seconds and toggle
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_HASH_FAIL_MIN_SECONDS, 120 );
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_HASH_FAIL_MAX_SECONDS, 120 );
		DownloadControlBTBanRegistry.Ban hf = DownloadControlBTBanService.get().onHashFail( "198.51.100.3", "piece=1" );
		assertThat( hf ).isNotNull();
		assertThat( hf.expiryMs - hf.startMs ).isEqualTo( 120_000L );
		COConfigurationManager.setParameter( DownloadControlBTBanService.CFG_HASH_FAIL_ENABLED, false );
		assertThat( DownloadControlBTBanService.get().onHashFail( "198.51.100.4", "piece=2" )).isNull();

		// Idle, ConnectTimeout, Rarest disconnect toggles & thresholds
		COConfigurationManager.setParameter( DownloadControlBTIdleBan.CFG_IDLE_SECONDS, 12 );
		assertThat( DownloadControlBTIdleBan.idleMs()).isEqualTo( 12_000L );
		COConfigurationManager.setParameter( DownloadControlBTIdleBan.CFG_ENABLED, false );
		assertThat( DownloadControlBTIdleBan.isEnabled()).isFalse();

		COConfigurationManager.setParameter( DownloadControlBTConnectTimeout.CFG_KICK_SECONDS, 45 );
		assertThat( DownloadControlBTConnectTimeout.kickMs()).isEqualTo( 45_000L );
		COConfigurationManager.setParameter( DownloadControlBTConnectTimeout.CFG_ENABLED, false );
		assertThat( DownloadControlBTConnectTimeout.isEnabled()).isFalse();

		COConfigurationManager.setParameter( DownloadControlBTDefaults.CFG_RAREST_ONLY, true );
		COConfigurationManager.setParameter( DownloadControlBTRarestUpload.CFG_DISCONNECT_REJECTS, 12 );
		assertThat( DownloadControlBTRarestUpload.disconnectAfterRejects()).isEqualTo( 12 );
		COConfigurationManager.setParameter( DownloadControlBTRarestUpload.CFG_DISCONNECT_ENABLED, false );
		assertThat( DownloadControlBTRarestUpload.isDisconnectEnabled()).isFalse();
	}

	@Test
	public void stickyDirectoryListenersClearStaleFilterDirsWhenSavedDirsChange()
	{
		COConfigurationManager.setParameter( DownloadControlBTDefaults.CFG_PROFILE, true );
		DownloadControlBTDefaults.registerStickyDirectoryListeners();

		COConfigurationManager.setParameter( "previous.filter.dir.data", "/old/downloads" );
		COConfigurationManager.setParameter( "previous.filter.dir.torrent", "/old/torrents" );

		COConfigurationManager.setParameter( "Default save path", "/new/downloads" );
		COConfigurationManager.setParameter( "General_sDefaultTorrent_Directory", "/new/torrents" );

		assertThat( COConfigurationManager.getStringParameter( "previous.filter.dir.data" )).isEmpty();
		assertThat( COConfigurationManager.getStringParameter( "previous.filter.dir.torrent" )).isEmpty();
	}

	@Test
	public void initIsolatedConfigUsesSetIfUnsetForSaveAndTorrentDirs()
		throws Exception
	{
		String initSource = Files.readString( Path.of( "..", "packaging", "dlcbt", "InitIsolatedConfig.java" ).normalize());
		if ( !Files.exists( Path.of( "..", "packaging", "dlcbt", "InitIsolatedConfig.java" )) ){
			initSource = Files.readString( Path.of( "packaging", "dlcbt", "InitIsolatedConfig.java" ));
		}
		assertThat( initSource )
			.contains( "setIfUnset(\"Default save path\", downloads.getPath());" )
			.contains( "setIfUnset(\"Save Torrent Files\", true);" )
			.contains( "setIfUnset(\"General_sDefaultTorrent_Directory\", torrents.getPath());" )
			.doesNotContain( "COConfigurationManager.setParameter(\"Default save path\"" )
			.doesNotContain( "COConfigurationManager.setParameter(\"General_sDefaultTorrent_Directory\"" );
	}

	@Test
	public void graphicalAutofitTargetsFifteenPercentInTenToTwentyPercentBand()
	{
		// 1920px window -> 15% = 288px (within 10-20%: 192..384)
		assertThat( DownloadControlBTDefaults.computeGraphicalColumnWidth( 1920, 40, 60 )).isEqualTo( 288 );
		// 1200px window -> 15% = 180px (within 10-20%: 120..240)
		assertThat( DownloadControlBTDefaults.computeGraphicalColumnWidth( 1200, 40, 60 )).isEqualTo( 180 );
		// Narrow 700px window -> clamped at 120px minimum
		assertThat( DownloadControlBTDefaults.computeGraphicalColumnWidth( 700, 40, 60 )).isEqualTo( 120 );
		// Respects column minWidth/headerWidth if larger
		assertThat( DownloadControlBTDefaults.computeGraphicalColumnWidth( 1200, 220, 60 )).isEqualTo( 220 );

		// Recognizes Pieces-type graphical columns, ignores non-bar columns
		assertThat( DownloadControlBTDefaults.isPiecesTypeGraphicColumnName( "pieces" )).isTrue();
		assertThat( DownloadControlBTDefaults.isPiecesTypeGraphicColumnName( "blocks" )).isTrue();
		assertThat( DownloadControlBTDefaults.isPiecesTypeGraphicColumnName( "completion" )).isTrue();
		assertThat( DownloadControlBTDefaults.isPiecesTypeGraphicColumnName( "ProgressETA" )).isTrue();
		assertThat( DownloadControlBTDefaults.isPiecesTypeGraphicColumnName( "health" )).isFalse();
		assertThat( DownloadControlBTDefaults.isPiecesTypeGraphicColumnName( "download" )).isFalse();
	}
}
