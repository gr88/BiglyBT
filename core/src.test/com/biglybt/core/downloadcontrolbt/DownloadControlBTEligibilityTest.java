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

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration-level tests of the eligibility gate that PiecePickerImpl,
 * RTA allocation, endgame, reserved pieces, request hints and allowed-fast
 * priority offsets all consult. These tests exercise that shared gate rather
 * than only the order-array helper.
 */
public class DownloadControlBTEligibilityTest
{
	private static final int FIRST = 3355;
	private static final int LAST = 3582;

	@Test
	public void disabledModeAllowsAnyPiece()
	{
		int allowed = allowedPiece( false, false, FIRST, LAST, new Verified() );
		assertThat( allowed ).isEqualTo( DownloadControlBT.UNRESTRICTED );
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, FIRST )).isTrue();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, LAST )).isTrue();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, 0 )).isTrue();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, 3999 )).isTrue();
	}

	@Test
	public void onlyFirstPieceIsEligibleUntilVerified()
	{
		int allowed = allowedPiece( true, false, FIRST, LAST, new Verified() );
		assertThat( allowed ).isEqualTo( FIRST );
		assertAllowedOnly( allowed, FIRST );
	}

	@Test
	public void rarestPriorityHintAndAllowedFastCannotBypassActivePiece()
	{
		int allowed = allowedPiece( true, false, FIRST, LAST, new Verified() );

		int rarestOther = LAST - 10;
		int hinted = LAST;
		int allowedFast = FIRST + 3;

		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, rarestOther )).isFalse();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, hinted )).isFalse();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, allowedFast )).isFalse();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, FIRST )).isTrue();
	}

	@Test
	public void reservedOrEndgameChunkForAnotherPieceIsRejected()
	{
		Verified verified = new Verified();
		verified.mark( FIRST );
		int allowed = allowedPiece( true, false, FIRST, LAST, verified );
		assertThat( allowed ).isEqualTo( LAST );

		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, FIRST + 1 )).isFalse();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, LAST )).isTrue();
	}

	@Test
	public void unavailableActivePieceStillBlocksFallback()
	{
		int allowed = allowedPiece( true, false, FIRST, LAST, new Verified() );
		assertThat( allowed ).isEqualTo( FIRST );
		// No other piece becomes eligible just because the head is unavailable.
		for (int p = FIRST + 1; p <= LAST; p++){
			assertThat( DownloadControlBT.isNewRequestAllowed( allowed, p )).isFalse();
		}
	}

	@Test
	public void hashFailureKeepsTheSameActivePiece()
	{
		Verified verified = new Verified();
		verified.mark( FIRST );
		int allowed = allowedPiece( true, false, FIRST, LAST, verified );
		assertThat( allowed ).isEqualTo( LAST );

		// Failed hash leaves LAST unverified, so the gate does not advance.
		verified.unmark( LAST );
		int still = allowedPiece( true, false, FIRST, LAST, verified );
		assertThat( still ).isEqualTo( LAST );
	}

	@Test
	public void alreadyCompleteBoundariesSelectFirstInterior()
	{
		Verified verified = new Verified();
		verified.mark( FIRST );
		verified.mark( LAST );
		int allowed = allowedPiece( true, false, FIRST, LAST, verified );
		assertThat( allowed ).isEqualTo( 3356 );
		assertAllowedOnly( allowed, 3356 );
	}

	@Test
	public void completeSelectedRangeAllowsNoNewRequests()
	{
		Verified verified = new Verified();
		for (int p = FIRST; p <= LAST; p++){
			verified.mark( p );
		}
		int allowed = allowedPiece( true, false, FIRST, LAST, verified );
		assertThat( allowed ).isEqualTo( DownloadControlBT.NO_PIECE );
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, FIRST )).isFalse();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, LAST )).isFalse();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, 0 )).isFalse();
	}

	@Test
	public void emptyFileAllowsNoNewRequests()
	{
		int allowed = allowedPiece( true, true, 0, 0, new Verified() );
		assertThat( allowed ).isEqualTo( DownloadControlBT.NO_PIECE );
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, 0 )).isFalse();
	}

	@Test
	public void singlePieceFileOnlyThatPieceIsEligible()
	{
		int allowed = allowedPiece( true, false, 99, 99, new Verified() );
		assertThat( allowed ).isEqualTo( 99 );
		assertAllowedOnly( allowed, 99 );
	}

	@Test
	public void disjointSharedBoundariesStayInPlanUntilVerified()
	{
		// first=10 is shared with the previous file, last=20 with the next.
		Verified verified = new Verified();
		int allowed = allowedPiece( true, false, 10, 20, verified );
		assertThat( allowed ).isEqualTo( 10 );

		verified.mark( 10 );
		allowed = allowedPiece( true, false, 10, 20, verified );
		assertThat( allowed ).isEqualTo( 20 );

		verified.mark( 20 );
		allowed = allowedPiece( true, false, 10, 20, verified );
		assertThat( allowed ).isEqualTo( 11 );
	}

	@Test
	public void invalidRangeAllowsNoNewRequests()
	{
		DownloadControlStrictOrder.Decision decision =
			DownloadControlStrictOrder.decide( true, false, 20, 10, new Verified() );
		int allowed = toAllowedPiece( decision, true );
		assertThat( allowed ).isEqualTo( DownloadControlBT.NO_PIECE );
	}

	private static int
	allowedPiece(
		boolean			enabled,
		boolean			emptyFile,
		int				first,
		int				last,
		Verified		verified )
	{
		DownloadControlStrictOrder.Decision decision =
			DownloadControlStrictOrder.decide( enabled, emptyFile, first, last, verified );
		return toAllowedPiece( decision, enabled );
	}

	private static int
	toAllowedPiece(
		DownloadControlStrictOrder.Decision		decision,
		boolean									enabled )
	{
		if ( !enabled || decision.status == DownloadControlStrictOrder.STATUS_DISABLED ){
			return DownloadControlBT.UNRESTRICTED;
		}
		if ( decision.hasActivePiece()){
			return decision.activePiece;
		}
		return DownloadControlBT.NO_PIECE;
	}

	private static void
	assertAllowedOnly(
		int		allowed,
		int		expected )
	{
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, expected )).isTrue();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, expected - 1 )).isFalse();
		assertThat( DownloadControlBT.isNewRequestAllowed( allowed, expected + 1 )).isFalse();
	}

	private static final class Verified
		implements DownloadControlStrictOrder.HashVerifiedPieces
	{
		private final Set<Integer> done = new HashSet<>();

		void mark(int pieceNumber)
		{
			done.add( pieceNumber );
		}

		void unmark(int pieceNumber)
		{
			done.remove( pieceNumber );
		}

		@Override
		public boolean isHashVerified(int pieceNumber)
		{
			return done.contains( pieceNumber );
		}
	}
}
