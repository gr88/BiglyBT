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

public class DownloadControlStrictOrderTest
{
	private static final int FIRST = 3355;
	private static final int LAST = 3582;

	@Test
	public void firstLastThenInteriorAscending()
	{
		int[] order = DownloadControlStrictOrder.buildRequestOrder( FIRST, LAST );

		assertThat( order ).hasSize( LAST - FIRST + 1 );
		assertThat( order[0] ).isEqualTo( FIRST );
		assertThat( order[1] ).isEqualTo( LAST );
		assertThat( order[2] ).isEqualTo( FIRST + 1 );
		assertThat( order[order.length - 1] ).isEqualTo( LAST - 1 );

		for (int i = 2; i < order.length; i++){
			assertThat( order[i] ).isEqualTo( FIRST + ( i - 1 ));
		}
	}

	@Test
	public void singlePieceFileHasNoDuplicate()
	{
		int[] order = DownloadControlStrictOrder.buildRequestOrder( 42, 42 );
		assertThat( order ).containsExactly( 42 );
	}

	@Test
	public void emptyOrInvalidRangeHasNoPieces()
	{
		assertThat( DownloadControlStrictOrder.buildRequestOrder( -1, 10 )).isEmpty();
		assertThat( DownloadControlStrictOrder.buildRequestOrder( 10, 9 )).isEmpty();
		assertThat( DownloadControlStrictOrder.buildRequestOrder( -5, -1 )).isEmpty();
	}

	@Test
	public void nextPieceStartsAtFirstThenLastThenInterior()
	{
		Verified verified = new Verified();

		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified ))
			.isEqualTo( FIRST );

		verified.mark( FIRST );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified ))
			.isEqualTo( LAST );

		verified.mark( LAST );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified ))
			.isEqualTo( FIRST + 1 );

		verified.mark( FIRST + 1 );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified ))
			.isEqualTo( FIRST + 2 );
	}

	@Test
	public void unavailableHeadDoesNotSkipToALaterPiece()
	{
		Verified verified = new Verified();
		// Later pieces being "available" is irrelevant; this helper only
		// inspects hash-verified completion, never swarm availability.
		int next = DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified );
		assertThat( next ).isEqualTo( FIRST );
		assertThat( next ).isNotEqualTo( LAST );
		assertThat( next ).isNotEqualTo( FIRST + 1 );
	}

	@Test
	public void hashFailureDoesNotAdvance()
	{
		Verified verified = new Verified();
		verified.mark( FIRST );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified ))
			.isEqualTo( LAST );

		// A failed hash means the piece is still not verified.
		verified.unmark( LAST );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified ))
			.isEqualTo( LAST );
	}

	@Test
	public void alreadyVerifiedBoundariesResumeAtFirstInterior()
	{
		Verified verified = new Verified();
		verified.mark( FIRST );
		verified.mark( LAST );

		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified ))
			.isEqualTo( 3356 );
	}

	@Test
	public void restoreUsesOnlyVerifiedStateNotTransientBytes()
	{
		Verified verified = new Verified();
		// Simulate restart: some interior pieces were written but never
		// hash-verified. Only FIRST is actually done on disk.
		verified.mark( FIRST );

		int next = DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified );
		assertThat( next ).isEqualTo( LAST );
	}

	@Test
	public void sharedBoundaryPiecesAppearOnceEach()
	{
		// Distinct first/last pieces are treated as shared file-boundary
		// pieces and downloaded in full, without duplicating either.
		int[] order = DownloadControlStrictOrder.buildRequestOrder( 10, 20 );
		assertThat( order[0] ).isEqualTo( 10 );
		assertThat( order[1] ).isEqualTo( 20 );
		long firstCount = java.util.Arrays.stream( order ).filter( p -> p == 10 ).count();
		long lastCount = java.util.Arrays.stream( order ).filter( p -> p == 20 ).count();
		assertThat( firstCount ).isEqualTo( 1 );
		assertThat( lastCount ).isEqualTo( 1 );
	}

	@Test
	public void completeRangeReturnsNoPiece()
	{
		Verified verified = new Verified();
		for (int p = FIRST; p <= LAST; p++){
			verified.mark( p );
		}
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( FIRST, LAST, verified ))
			.isEqualTo( DownloadControlStrictOrder.NO_PIECE );
	}

	@Test
	public void emptyFileDecisionIsComplete()
	{
		DownloadControlStrictOrder.Decision decision =
			DownloadControlStrictOrder.decide( true, true, 0, 0, new Verified() );
		assertThat( decision.status ).isEqualTo( DownloadControlStrictOrder.STATUS_EMPTY );
		assertThat( decision.activePiece ).isEqualTo( DownloadControlStrictOrder.NO_PIECE );
	}

	@Test
	public void disabledDecisionDoesNotSelectAPiece()
	{
		DownloadControlStrictOrder.Decision decision =
			DownloadControlStrictOrder.decide( false, false, FIRST, LAST, new Verified() );
		assertThat( decision.status ).isEqualTo( DownloadControlStrictOrder.STATUS_DISABLED );
		assertThat( decision.activePiece ).isEqualTo( DownloadControlStrictOrder.NO_PIECE );
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
