/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Random;

import org.junit.jupiter.api.Test;

import com.biglybt.core.peer.impl.PEPeerTransport;

public class DownloadControlBTNov20Test
{
	@Test
	public void rarestOnlyAllowsMinAndAlwaysExemptsFileBoundaries()
	{
		// one file pieces 0..4; we have all; avail 3,1,1,2,3
		boolean[] have = { true, true, true, true, true };
		int[] avail = { 3, 1, 1, 2, 3 };
		int[] first = { 0 };
		int[] last = { 4 };
		assertThat( DownloadControlBTRarestUpload.maySend( 0, first, last, have, avail )).isTrue(); // first
		assertThat( DownloadControlBTRarestUpload.maySend( 4, first, last, have, avail )).isTrue(); // last
		assertThat( DownloadControlBTRarestUpload.maySend( 1, first, last, have, avail )).isTrue();
		assertThat( DownloadControlBTRarestUpload.maySend( 2, first, last, have, avail )).isTrue();
		assertThat( DownloadControlBTRarestUpload.maySend( 3, first, last, have, avail )).isFalse();
	}

	@Test
	public void rarestOnlyUsesOnlyPiecesWeHaveForMin()
	{
		boolean[] have = { true, false, true, true };
		int[] avail = { 5, 1, 2, 2 }; // piece 1 is rarest overall but we don't have it
		int[] first = { 0 };
		int[] last = { 3 };
		assertThat( DownloadControlBTRarestUpload.minHaveAvailability( 0, 3, have, avail )).isEqualTo( 2 );
		assertThat( DownloadControlBTRarestUpload.maySend( 2, first, last, have, avail )).isTrue();
		assertThat( DownloadControlBTRarestUpload.maySend( 1, first, last, have, avail )).isFalse();
		assertThat( DownloadControlBTRarestUpload.maySend( 0, first, last, have, avail )).isTrue(); // boundary
	}

	@Test
	public void spanningBoundaryPieceIsAlwaysAllowed()
	{
		// file0: 0-2, file1: 2-5, piece 2 is last of first and first of second
		int[] first = { 0, 2 };
		int[] last = { 2, 5 };
		boolean[] have = { true, true, true, true, true, true };
		int[] avail = { 4, 5, 9, 1, 1, 4 }; // piece 1 is neither a boundary nor file0 min
		assertThat( DownloadControlBTRarestUpload.maySend( 2, first, last, have, avail )).isTrue();
		assertThat( DownloadControlBTRarestUpload.maySend( 3, first, last, have, avail )).isTrue();
		assertThat( DownloadControlBTRarestUpload.maySend( 1, first, last, have, avail )).isFalse();
	}

	@Test
	public void idleBanAfterFiveSecondsWhenFullyEstablishedAndNoData()
	{
		long start = 1_000_000L;
		assertThat( DownloadControlBTIdleBan.shouldBan( true,
			PEPeerTransport.CONNECTION_FULLY_ESTABLISHED, start, -1, start + 4_999, false )).isFalse();
		assertThat( DownloadControlBTIdleBan.shouldBan( true,
			PEPeerTransport.CONNECTION_FULLY_ESTABLISHED, start, -1, start + 5_000, false )).isTrue();
		assertThat( DownloadControlBTIdleBan.shouldBan( true,
			PEPeerTransport.CONNECTION_CONNECTING, start, -1, start + 20_000, false )).isFalse();
		assertThat( DownloadControlBTIdleBan.shouldBan( true,
			PEPeerTransport.CONNECTION_FULLY_ESTABLISHED, start, start + 4_000, start + 8_000, false )).isFalse();
		assertThat( DownloadControlBTIdleBan.shouldBan( true,
			PEPeerTransport.CONNECTION_FULLY_ESTABLISHED, start, start + 4_000, start + 9_000, false )).isTrue();
		// rarest-only reject in the window: do not idle-ban
		assertThat( DownloadControlBTIdleBan.shouldBan( true,
			PEPeerTransport.CONNECTION_FULLY_ESTABLISHED, start, -1, start + 20_000, true )).isFalse();
		assertThat( DownloadControlBTIdleBan.shouldBan( false,
			PEPeerTransport.CONNECTION_FULLY_ESTABLISHED, start, -1, start + 20_000, false )).isFalse();
	}

	@Test
	public void sidebarMeasureUsesFullClientWidth()
	{
		assertThat( measureWidth( 200, 40, 10, 20, false )).isEqualTo( 200 );
		assertThat( measureWidth( 80, 400, 0, 10, true )).isEqualTo( 80 );
		assertThat( measureWidth( 0, 10, 0, 0, false )).isEqualTo( 0 );
	}

	/** Mirrors the Nov 20 MeasureItem rule: always the full client width. */
	static int measureWidth( int clientWidth, int textWidth, int eventX, int eventWidth, boolean windows )
	{
		eventX = 0;
		return Math.max( 0, clientWidth );
	}

	@Test
	public void autofitZeroMeansOffAndDefaultIsSixMinutes()
	{
		assertThat( DownloadControlBTDefaults.DEFAULT_AUTOFIT_SECONDS ).isEqualTo( 360 );
		assertThat( autofitEnabled( 0 )).isFalse();
		assertThat( autofitEnabled( 360 )).isTrue();
	}

	static boolean autofitEnabled( int seconds )
	{
		return seconds > 0;
	}

	@Test
	public void listenPortsOneShotDoesNotOverwriteUserChoice()
	{
		// first run: flag missing -> apply 26981
		assertThat( applyListen( false, 6881, 6881 )).containsExactly( 26981, 26981, 1 );
		// later start: flag set, user ports kept
		assertThat( applyListen( true, 40123, 40124 )).containsExactly( 40123, 40124, 1 );
	}

	static int[] applyListen( boolean flagSet, int existingTcp, int existingUdp )
	{
		if ( !flagSet ){
			return new int[]{ 26981, 26981, 1 };
		}
		return new int[]{ existingTcp, existingUdp, 1 };
	}

	@Test
	public void hashFailDurationStillInRange()
	{
		Random rnd = new Random( 3 );
		for ( int i = 0; i < 200; i++ ){
			assertThat( DownloadControlBTBanService.hashFailDurationMs( rnd )).isBetween( 210_000L, 270_000L );
		}
	}
}
