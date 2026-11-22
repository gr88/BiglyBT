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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DownloadControlRandomOrderTest
{
	private static final int FIRST = 3355;
	private static final int LAST = 3582;
	private static final long SEED = 0xD1CB72026L;

	@Test
	public void randomKeepsFirstAndLastFixedThenPermutesInteriors()
	{
		int[] order = DownloadControlStrictOrder.buildRandomRequestOrder(
			FIRST, LAST, new Random( SEED ));

		assertThat( order ).hasSize( LAST - FIRST + 1 );
		assertThat( order[0] ).isEqualTo( FIRST );
		assertThat( order[1] ).isEqualTo( LAST );
		assertThat( DownloadControlStrictOrder.isValidPlan( order, FIRST, LAST )).isTrue();

		int[] interiors = Arrays.copyOfRange( order, 2, order.length );
		int[] expectedInteriors = new int[LAST - FIRST - 1];
		for (int i = 0; i < expectedInteriors.length; i++){
			expectedInteriors[i] = FIRST + 1 + i;
		}
		assertThat( interiors ).containsExactlyInAnyOrder( expectedInteriors );
		assertThat( interiors ).isNotEqualTo( expectedInteriors );
	}

	@Test
	public void randomPlanHasNoRepeats()
	{
		int[] order = DownloadControlStrictOrder.buildRandomRequestOrder(
			10, 20, new Random( SEED ));
		Set<Integer> unique = new HashSet<>();
		for ( int piece: order ){
			assertThat( unique.add( piece )).isTrue();
		}
		assertThat( unique ).hasSize( order.length );
	}

	@Test
	public void sameSeedIsDeterministicAcrossRestart()
	{
		int[] first = DownloadControlStrictOrder.buildRandomRequestOrder(
			FIRST, LAST, new Random( SEED ));
		int[] again = DownloadControlStrictOrder.buildRandomRequestOrder(
			FIRST, LAST, new Random( SEED ));
		assertThat( again ).containsExactly( first );

		String[] stored = DownloadControlStrictOrder.serializePlan( first );
		int[] restored = DownloadControlStrictOrder.parsePlan( stored );
		assertThat( restored ).containsExactly( first );

		Verified verified = new Verified();
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( restored, verified ))
			.isEqualTo( FIRST );
		verified.mark( FIRST );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( restored, verified ))
			.isEqualTo( LAST );
		verified.mark( LAST );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( restored, verified ))
			.isEqualTo( first[2] );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( restored, verified ))
			.isEqualTo( DownloadControlStrictOrder.nextUnverifiedPiece( first, verified ));
	}

	@Test
	public void unavailableRandomHeadDoesNotSkip()
	{
		int[] order = DownloadControlStrictOrder.buildRandomRequestOrder(
			FIRST, LAST, new Random( SEED ));
		Verified verified = new Verified();
		int next = DownloadControlStrictOrder.nextUnverifiedPiece( order, verified );
		assertThat( next ).isEqualTo( FIRST );
		assertThat( next ).isNotEqualTo( LAST );
		assertThat( next ).isNotEqualTo( order[2] );
	}

	@Test
	public void hashFailureDoesNotAdvanceRandomPlan()
	{
		int[] order = DownloadControlStrictOrder.buildRandomRequestOrder(
			FIRST, LAST, new Random( SEED ));
		Verified verified = new Verified();
		verified.mark( FIRST );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( order, verified ))
			.isEqualTo( LAST );
		verified.unmark( LAST );
		assertThat( DownloadControlStrictOrder.nextUnverifiedPiece( order, verified ))
			.isEqualTo( LAST );
	}

	@Test
	public void tinyFilesHaveCorrectPlans()
	{
		assertThat( DownloadControlStrictOrder.buildRandomRequestOrder( 7, 7, new Random( SEED )))
			.containsExactly( 7 );
		assertThat( DownloadControlStrictOrder.buildRandomRequestOrder( 7, 8, new Random( SEED )))
			.containsExactly( 7, 8 );
		assertThat( DownloadControlStrictOrder.buildRequestOrder( 7, 7 )).containsExactly( 7 );
		assertThat( DownloadControlStrictOrder.buildRequestOrder( 7, 8 )).containsExactly( 7, 8 );
	}

	@Test
	public void legacyEnabledWithoutModeMapsToSequential()
	{
		assertThat( DownloadControlStrictOrder.resolveMode( false, 0 ))
			.isEqualTo( DownloadControlStrictOrder.MODE_OFF );
		assertThat( DownloadControlStrictOrder.resolveMode( true, 0 ))
			.isEqualTo( DownloadControlStrictOrder.MODE_SEQUENTIAL );
		assertThat( DownloadControlStrictOrder.resolveMode( true, DownloadControlStrictOrder.MODE_SEQUENTIAL ))
			.isEqualTo( DownloadControlStrictOrder.MODE_SEQUENTIAL );
		assertThat( DownloadControlStrictOrder.resolveMode( true, DownloadControlStrictOrder.MODE_RANDOM ))
			.isEqualTo( DownloadControlStrictOrder.MODE_RANDOM );
		assertThat( DownloadControlStrictOrder.resolveMode( false, DownloadControlStrictOrder.MODE_RANDOM ))
			.isEqualTo( DownloadControlStrictOrder.MODE_OFF );
	}

	@Test
	public void modeLetterIsBlankExceptSelectedFile()
	{
		assertThat( DownloadControlStrictOrder.modeLetterForFile( true, 0, 16, 16 )).isEqualTo( "S" );
		assertThat( DownloadControlStrictOrder.modeLetterForFile(
			true, DownloadControlStrictOrder.MODE_RANDOM, 7, 7 )).isEqualTo( "R" );
		assertThat( DownloadControlStrictOrder.modeLetterForFile(
			true, DownloadControlStrictOrder.MODE_RANDOM, 7, 8 )).isEqualTo( "" );
		assertThat( DownloadControlStrictOrder.modeLetterForFile( false, 0, 16, 16 )).isEqualTo( "" );
		assertThat( DownloadControlStrictOrder.modeLetter( DownloadControlStrictOrder.MODE_OFF )).isEmpty();
	}

	@Test
	public void storedPlanRejectedWhenRangeOrContentsChange()
	{
		int[] order = DownloadControlStrictOrder.buildRandomRequestOrder(
			10, 20, new Random( SEED ));
		assertThat( DownloadControlStrictOrder.isValidPlan( order, 10, 20 )).isTrue();
		assertThat( DownloadControlStrictOrder.isValidPlan( order, 10, 21 )).isFalse();
		order[2] = 10;
		assertThat( DownloadControlStrictOrder.isValidPlan( order, 10, 20 )).isFalse();
	}

	@Test
	public void sequentialAndRandomPlansDifferForSameRange()
	{
		int[] sequential = DownloadControlStrictOrder.buildRequestOrder( FIRST, LAST );
		int[] random = DownloadControlStrictOrder.buildRandomRequestOrder(
			FIRST, LAST, new Random( SEED ));
		assertThat( sequential[0] ).isEqualTo( random[0] );
		assertThat( sequential[1] ).isEqualTo( random[1] );
		assertThat( random ).isNotEqualTo( sequential );
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
