/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.core.downloadcontrolbt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DownloadControlFileQueueTest
{
	private static DownloadControlFileQueue.FileSnap file(
		int index, long length, int first, int last, int priority, boolean skipped )
	{
		int nb = length <= 0 ? 0 : last - first + 1;
		return new DownloadControlFileQueue.FileSnap(
			index, length, first, last, nb, priority, skipped );
	}

	@Test
	public void emptyScheduleIsSeededWithExactPeerSetLines()
	{
		assertThat( DownloadControlBTDefaults.shouldSeedSchedule( null )).isTrue();
		assertThat( DownloadControlBTDefaults.shouldSeedSchedule( new ArrayList<>())).isTrue();
		List<String> blanks = Arrays.asList( "", "   " );
		assertThat( DownloadControlBTDefaults.shouldSeedSchedule( blanks )).isTrue();
		List<String> seeded = DownloadControlBTDefaults.defaultScheduleLines();
		assertThat( seeded ).containsExactly(
			"peer_set UpPeers=All,peer_up=25.1k",
			"peer_set DownPeers=All,peer_down=251.1k" );
	}

	@Test
	public void nonemptyScheduleIsNotClobbered()
	{
		List<String> existing = Arrays.asList(
			"peer_set Existing=All,peer_up=1k",
			"peer_set Other=All,peer_down=2k" );
		assertThat( DownloadControlBTDefaults.shouldSeedSchedule( existing )).isFalse();
		assertThat( DownloadControlBTDefaults.defaultScheduleLines() )
			.doesNotContain( existing.get( 0 ));
	}

	@Test
	public void hiddenLibraryColumnsAreRemovedButRemainIdentifiable()
	{
		String[] stock = {
			"health", "#", "name", "TorrentStream", "azsubs.ui.column.subs",
			"commenticon", "size", "SeedingRank", "status"
		};
		String[] hidden = DownloadControlBTDefaults.applyHiddenDefaultLibraryColumns( stock );
		assertThat( hidden ).containsExactly( "name", "size", "status" );
		assertThat( DownloadControlBTDefaults.isHiddenDefaultLibraryColumn( "#" )).isTrue();
		assertThat( DownloadControlBTDefaults.isHiddenDefaultLibraryColumn( "name" )).isFalse();
	}

	@Test
	public void defaultStartModePrefersStoppedThenForceStarted()
	{
		assertThat( DownloadControlBTDefaults.resolveDefaultStartMode( true, true ))
			.isEqualTo( DownloadControlBTDefaults.STARTMODE_STOPPED );
		assertThat( DownloadControlBTDefaults.resolveDefaultStartMode( false, true ))
			.isEqualTo( DownloadControlBTDefaults.STARTMODE_FORCESTARTED );
		assertThat( DownloadControlBTDefaults.resolveDefaultStartMode( false, false ))
			.isEqualTo( DownloadControlBTDefaults.STARTMODE_QUEUED );
	}

	@Test
	public void queueOrdersByPriorityThenFileIndexAndSkipsEmpty()
	{
		DownloadControlFileQueue.FileSnap[] files = {
			file( 0, 1000, 0, 2, 0, false ),
			file( 1, 1000, 3, 5, 3, false ),
			file( 2, 0, 6, 6, 5, false ),
			file( 3, 1000, 7, 9, 3, false ),
			file( 4, 1000, 10, 12, 1, true ),
			file( 5, 1000, 13, 15, -2, false ),
		};
		List<DownloadControlFileQueue.FileSnap> order = DownloadControlFileQueue.queueOrder( files );
		assertThat( order ).extracting( f -> f.index ).containsExactly( 1, 3, 0, 5 );
	}

	@Test
	public void pinHoldsThroughPriorityChangeAndAdvancesOnFinishOrSkip()
	{
		DownloadControlFileQueue.FileSnap[] files = {
			file( 0, 1000, 0, 1, 0, false ),
			file( 1, 1000, 2, 3, 5, false ),
			file( 2, 1000, 4, 5, 1, false ),
		};
		Verified verified = new Verified();
		int pinned = DownloadControlFileQueue.pickPinnedOrNext( files, 0, verified );
		assertThat( pinned ).isEqualTo( 0 );

		files[1] = file( 1, 1000, 2, 3, 9, false );
		assertThat( DownloadControlFileQueue.pickPinnedOrNext( files, 0, verified )).isEqualTo( 0 );

		verified.mark( 0 );
		verified.mark( 1 );
		assertThat( DownloadControlFileQueue.pickPinnedOrNext( files, 0, verified )).isEqualTo( 1 );

		files[1] = file( 1, 1000, 2, 3, 9, true );
		assertThat( DownloadControlFileQueue.pickPinnedOrNext( files, 1, verified )).isEqualTo( 2 );
	}

	@Test
	public void unfinishedPinnedFileIsStableAcrossRestartLikeReselection()
	{
		DownloadControlFileQueue.FileSnap[] files = {
			file( 0, 1000, 0, 1, 0, false ),
			file( 1, 1000, 2, 3, 8, false ),
		};
		Verified verified = new Verified();
		verified.mark( 0 );
		assertThat( DownloadControlFileQueue.pickPinnedOrNext( files, 0, verified )).isEqualTo( 0 );
	}

	@Test
	public void stalledHeadDoesNotSkipInsideAFile()
	{
		int[] order = DownloadControlStrictOrder.buildRequestOrder( 10, 14 );
		Verified verified = new Verified();
		DownloadControlStrictOrder.Decision decision =
			DownloadControlStrictOrder.decide( true, false, order, verified );
		assertThat( decision.activePiece ).isEqualTo( 10 );
		assertThat( DownloadControlStrictOrder.isNewRequestAllowed( decision.activePiece, 14 )).isFalse();
		assertThat( DownloadControlStrictOrder.isNewRequestAllowed( decision.activePiece, 11 )).isFalse();
	}

	@Test
	public void hashFailureDoesNotAdvanceAndSharedBoundaryCountsForBothFiles()
	{
		Verified verified = new Verified();
		verified.mark( 10 );
		int[] order = DownloadControlStrictOrder.buildRandomRequestOrder( 10, 12, new Random( 7 ));
		int active = DownloadControlStrictOrder.decide( true, false, order, verified ).activePiece;
		assertThat( active ).isEqualTo( 12 );
		verified.unmark( 12 );
		assertThat( DownloadControlStrictOrder.decide( true, false, order, verified ).activePiece )
			.isEqualTo( 12 );

		DownloadControlFileQueue.FileSnap[] files = {
			file( 0, 1000, 10, 12, 0, false ),
			file( 1, 1000, 12, 14, 0, false ),
		};
		verified.mark( 10 );
		verified.mark( 11 );
		verified.mark( 12 );
		assertThat( DownloadControlFileQueue.isFileFinished( files[0], verified )).isTrue();
		assertThat( DownloadControlFileQueue.pickPinnedOrNext( files, 0, verified )).isEqualTo( 1 );
		assertThat( DownloadControlFileQueue.isFileFinished( files[1], verified )).isFalse();
	}

	@Test
	public void randomPlanHasNoRepeats()
	{
		int[] order = DownloadControlStrictOrder.buildRandomRequestOrder( 100, 118, new Random( 0xD1CB72026L ));
		assertThat( order[0] ).isEqualTo( 100 );
		assertThat( order[1] ).isEqualTo( 118 );
		Set<Integer> seen = new HashSet<>();
		for ( int piece: order ){
			assertThat( seen.add( piece )).isTrue();
		}
		assertThat( seen ).hasSize( 19 );
	}

	@Test
	public void lettersCoverQueuedFilesAndStayBlankForOffOrSkipped()
	{
		assertThat( DownloadControlFileQueue.modeLetterForFile(
			DownloadControlFileQueue.POLICY_OFF, false, DownloadControlStrictOrder.MODE_RANDOM,
			0, 0, DownloadControlStrictOrder.MODE_OFF, false )).isEmpty();
		assertThat( DownloadControlFileQueue.modeLetterForFile(
			DownloadControlFileQueue.POLICY_AUTO, true, DownloadControlStrictOrder.MODE_RANDOM,
			0, 2, DownloadControlStrictOrder.MODE_SEQUENTIAL, false )).isEqualTo( "S" );
		assertThat( DownloadControlFileQueue.modeLetterForFile(
			DownloadControlFileQueue.POLICY_AUTO, true, DownloadControlStrictOrder.MODE_RANDOM,
			0, 3, DownloadControlStrictOrder.MODE_OFF, false )).isEqualTo( "R" );
		assertThat( DownloadControlFileQueue.modeLetterForFile(
			DownloadControlFileQueue.POLICY_AUTO, true, DownloadControlStrictOrder.MODE_RANDOM,
			0, 4, DownloadControlStrictOrder.MODE_RANDOM, true )).isEmpty();
		assertThat( DownloadControlFileQueue.modeLetterForFile(
			DownloadControlFileQueue.POLICY_MANUAL, true, DownloadControlStrictOrder.MODE_SEQUENTIAL,
			1, 2, DownloadControlStrictOrder.MODE_OFF, false )).isEmpty();
		assertThat( DownloadControlFileQueue.modeLetterForFile(
			DownloadControlFileQueue.POLICY_MANUAL, true, DownloadControlStrictOrder.MODE_RANDOM,
			2, 2, DownloadControlStrictOrder.MODE_OFF, false )).isEqualTo( "R" );
	}

	@Test
	public void pendingLettersMatchAddDialogOverridesAndSkip()
	{
		assertThat( DownloadControlFileQueue.modeLetterForPendingFile(
			true, true, DownloadControlStrictOrder.MODE_SEQUENTIAL, false )).isEmpty();
		assertThat( DownloadControlFileQueue.modeLetterForPendingFile(
			false, true, DownloadControlStrictOrder.MODE_OFF, true )).isEmpty();
		assertThat( DownloadControlFileQueue.modeLetterForPendingFile(
			false, false, DownloadControlStrictOrder.MODE_OFF, false )).isEmpty();
		assertThat( DownloadControlFileQueue.modeLetterForPendingFile(
			false, true, DownloadControlStrictOrder.MODE_OFF, false )).isEqualTo( "R" );
		assertThat( DownloadControlFileQueue.modeLetterForPendingFile(
			false, true, DownloadControlStrictOrder.MODE_SEQUENTIAL, false )).isEqualTo( "S" );
		assertThat( DownloadControlFileQueue.modeLetterForPendingFile(
			false, false, DownloadControlStrictOrder.MODE_RANDOM, false )).isEqualTo( "R" );
	}

	@Test
	public void newTorrentDefaultsRespectOffLegacyManualAndMagnet()
	{
		DownloadControlFileQueue.FileSnap[] files = {
			file( 0, 1000, 0, 1, 0, false ),
			file( 1, 1000, 2, 3, 2, false ),
		};
		Map<Integer, Integer> modes = new HashMap<>();
		modes.put( 0, DownloadControlStrictOrder.MODE_SEQUENTIAL );

		DownloadControlFileQueue.NewTorrentDecision off =
			DownloadControlFileQueue.planNewTorrent(
				files, true, false, true, true, modes, new Verified());
		assertThat( off.policy ).isEqualTo( DownloadControlFileQueue.POLICY_OFF );

		DownloadControlFileQueue.NewTorrentDecision magnet =
			DownloadControlFileQueue.planNewTorrent(
				files, true, true, false, true, modes, new Verified());
		assertThat( magnet.isNoOp()).isTrue();

		DownloadControlFileQueue.NewTorrentDecision auto =
			DownloadControlFileQueue.planNewTorrent(
				files, true, false, false, true, modes, new Verified());
		assertThat( auto.policy ).isEqualTo( DownloadControlFileQueue.POLICY_AUTO );
		assertThat( auto.torrentDefaultMode ).isEqualTo( DownloadControlStrictOrder.MODE_RANDOM );
		assertThat( auto.initialFileIndex ).isEqualTo( 1 );
		assertThat( auto.initialFileMode ).isEqualTo( DownloadControlStrictOrder.MODE_RANDOM );

		DownloadControlFileQueue.NewTorrentDecision manual =
			DownloadControlFileQueue.planNewTorrent(
				files, true, false, false, false, modes, new Verified());
		assertThat( manual.policy ).isEqualTo( DownloadControlFileQueue.POLICY_MANUAL );
		assertThat( manual.initialFileIndex ).isEqualTo( 0 );
		assertThat( manual.initialFileMode ).isEqualTo( DownloadControlStrictOrder.MODE_SEQUENTIAL );

		assertThat( DownloadControlFileQueue.resolvePolicy( true, DownloadControlFileQueue.POLICY_UNSET ))
			.isEqualTo( DownloadControlFileQueue.POLICY_MANUAL );
		assertThat( DownloadControlFileQueue.resolvePolicy( false, DownloadControlFileQueue.POLICY_UNSET ))
			.isEqualTo( DownloadControlFileQueue.POLICY_UNSET );
		assertThat( DownloadControlFileQueue.resolvePolicy( false, DownloadControlFileQueue.POLICY_OFF ))
			.isEqualTo( DownloadControlFileQueue.POLICY_OFF );
	}

	@Test
	public void fileModeOverridesRoundTripAndDoNotCreateConcurrentHeads()
	{
		Map<Integer, Integer> modes = new HashMap<>();
		modes.put( 2, DownloadControlStrictOrder.MODE_SEQUENTIAL );
		modes.put( 5, DownloadControlStrictOrder.MODE_RANDOM );
		String[] stored = DownloadControlFileQueue.serializeFileModes( modes );
		Map<Integer, Integer> parsed = DownloadControlFileQueue.parseFileModes( stored );
		assertThat( parsed ).isEqualTo( modes );

		DownloadControlFileQueue.FileSnap[] files = {
			file( 0, 1000, 0, 2, 0, false ),
			file( 1, 1000, 3, 5, 0, false ),
		};
		Verified verified = new Verified();
		assertThat( DownloadControlFileQueue.pickPinnedOrNext( files, 0, verified )).isEqualTo( 0 );
		assertThat( DownloadControlFileQueue.pickPinnedOrNext( files, 0, verified )).isNotEqualTo( 1 );
	}

	private static final class Verified
		implements DownloadControlStrictOrder.HashVerifiedPieces
	{
		private final Set<Integer> done = new HashSet<>();

		void mark( int piece )
		{
			done.add( Integer.valueOf( piece ));
		}

		void unmark( int piece )
		{
			done.remove( Integer.valueOf( piece ));
		}

		@Override
		public boolean isHashVerified( int pieceNumber )
		{
			return done.contains( Integer.valueOf( pieceNumber ));
		}
	}
}
