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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Pure ordering rules for the DownLoadControlBT experiment.
 * <p>
 * Sequential selected-file piece order is: the first containing piece, then
 * the last containing piece, then interior pieces in ascending order.
 * Random selected-file piece order keeps that same first and last, then uses
 * a persisted shuffle of the interiors. Already hash-verified pieces are
 * skipped. The next unfinished piece in the plan stays active until it is
 * hash-verified; this helper never falls forward because a later piece looks
 * more available.
 * <p>
 * When the first and last containing pieces are the same, that piece appears
 * once. Shared file-boundary pieces are included in full as ordinary members
 * of the range. A two-piece file is first then last, with no interiors.
 */
public final class DownloadControlStrictOrder
{
	/** Mode is off; any piece may be requested (normal client behaviour). */
	public static final int UNRESTRICTED = Integer.MIN_VALUE;

	/** No remaining unfinished piece in the selected-file plan. */
	public static final int NO_PIECE = -1;

	public static final int STATUS_DISABLED = 0;
	public static final int STATUS_ACTIVE = 1;
	public static final int STATUS_WAITING = 2;
	public static final int STATUS_COMPLETE = 3;
	public static final int STATUS_EMPTY = 4;
	public static final int STATUS_INVALID = 5;

	/** Experiment off, or stored mode unset while the feature is disabled. */
	public static final int MODE_OFF = 0;
	/** First, last, then interiors ascending. Legacy enabled-without-mode. */
	public static final int MODE_SEQUENTIAL = 1;
	/** First, last distinct, then shuffled interiors. */
	public static final int MODE_RANDOM = 2;

	/** Version of the persisted random-plan format. */
	public static final int PLAN_VERSION = 1;

	public interface HashVerifiedPieces
	{
		/**
		 * @return true only if the piece has passed its hash check.
		 * Transient received/written bytes must not count.
		 */
		boolean isHashVerified(int pieceNumber);
	}

	public static final class Decision
	{
		public final int status;
		public final int activePiece;

		public Decision(int status, int activePiece)
		{
			this.status = status;
			this.activePiece = activePiece;
		}

		public boolean hasActivePiece()
		{
			return activePiece >= 0;
		}
	}

	private DownloadControlStrictOrder()
	{
	}

	/**
	 * Maps persisted flags onto a mode. An older download that only has
	 * {@code enabled=true} (stored mode 0 / missing) becomes sequential so
	 * existing experiments keep working after upgrade.
	 */
	public static int
	resolveMode(
		boolean		enabled,
		int			storedMode )
	{
		if ( !enabled ){
			return MODE_OFF;
		}
		if ( storedMode == MODE_RANDOM ){
			return MODE_RANDOM;
		}
		return MODE_SEQUENTIAL;
	}

	public static String
	modeLetter(
		int		mode )
	{
		if ( mode == MODE_SEQUENTIAL ){
			return "S";
		}
		if ( mode == MODE_RANDOM ){
			return "R";
		}
		return "";
	}

	/**
	 * Display letter for one file row: S or R only on the selected
	 * experimental file while a mode is active; otherwise blank.
	 */
	public static String
	modeLetterForFile(
		boolean		enabled,
		int			storedMode,
		int			selectedFileIndex,
		int			fileIndex )
	{
		if ( fileIndex < 0 || selectedFileIndex != fileIndex ){
			return "";
		}
		return modeLetter( resolveMode( enabled, storedMode ));
	}

	/**
	 * Builds the sequential request order for a zero-based inclusive piece range.
	 * Invalid or empty ranges produce a zero-length array.
	 */
	public static int[]
	buildRequestOrder(
		int		firstPiece,
		int		lastPiece )
	{
		if ( firstPiece < 0 || lastPiece < 0 || firstPiece > lastPiece ){
			return new int[0];
		}

		if ( firstPiece == lastPiece ){
			return new int[]{ firstPiece };
		}

		int count = lastPiece - firstPiece + 1;
		int[] order = new int[count];
		order[0] = firstPiece;
		order[1] = lastPiece;
		int pos = 2;
		for (int p = firstPiece + 1; p < lastPiece; p++){
			order[pos++] = p;
		}
		return order;
	}

	/**
	 * Builds a random request order: first containing piece, then the last
	 * distinct containing piece, then a shuffle of interiors. The supplied
	 * Random is the only source of shuffle; the same seed therefore yields
	 * the same plan. Does not reshuffle on its own.
	 */
	public static int[]
	buildRandomRequestOrder(
		int		firstPiece,
		int		lastPiece,
		Random	rng )
	{
		if ( rng == null ){
			return new int[0];
		}
		if ( firstPiece < 0 || lastPiece < 0 || firstPiece > lastPiece ){
			return new int[0];
		}
		if ( firstPiece == lastPiece ){
			return new int[]{ firstPiece };
		}
		if ( lastPiece == firstPiece + 1 ){
			return new int[]{ firstPiece, lastPiece };
		}

		List<Integer> interiors = new ArrayList<>();
		for (int p = firstPiece + 1; p < lastPiece; p++){
			interiors.add( p );
		}
		Collections.shuffle( interiors, rng );

		int[] order = new int[interiors.size() + 2];
		order[0] = firstPiece;
		order[1] = lastPiece;
		for (int i = 0; i < interiors.size(); i++){
			order[i + 2] = interiors.get( i );
		}
		return order;
	}

	/**
	 * True if {@code order} is a complete, duplicate-free plan for
	 * {@code [firstPiece..lastPiece]} with first then last (when distinct)
	 * occupying the first slots and the remainder exactly the interiors.
	 */
	public static boolean
	isValidPlan(
		int[]	order,
		int		firstPiece,
		int		lastPiece )
	{
		if ( order == null ){
			return false;
		}
		if ( firstPiece < 0 || lastPiece < 0 || firstPiece > lastPiece ){
			return order.length == 0;
		}
		int expected = lastPiece - firstPiece + 1;
		if ( order.length != expected ){
			return false;
		}
		if ( expected == 0 ){
			return true;
		}
		if ( order[0] != firstPiece ){
			return false;
		}
		if ( expected >= 2 && order[1] != lastPiece ){
			return false;
		}
		Set<Integer> seen = new LinkedHashSet<>();
		for ( int piece: order ){
			if ( piece < firstPiece || piece > lastPiece ){
				return false;
			}
			if ( !seen.add( piece )){
				return false;
			}
		}
		return seen.size() == expected;
	}

	public static String[]
	serializePlan(
		int[]	order )
	{
		if ( order == null || order.length == 0 ){
			return new String[0];
		}
		String[] out = new String[order.length];
		for (int i = 0; i < order.length; i++){
			out[i] = Integer.toString( order[i] );
		}
		return out;
	}

	public static int[]
	parsePlan(
		String[]	values )
	{
		if ( values == null || values.length == 0 ){
			return new int[0];
		}
		int[] order = new int[values.length];
		for (int i = 0; i < values.length; i++){
			if ( values[i] == null ){
				return new int[0];
			}
			try{
				order[i] = Integer.parseInt( values[i].trim() );
			}catch( NumberFormatException e ){
				return new int[0];
			}
		}
		return order;
	}

	public static String
	formatPlan(
		int[]	order )
	{
		if ( order == null || order.length == 0 ){
			return "";
		}
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < order.length; i++){
			if ( i > 0 ){
				sb.append( ',' );
			}
			sb.append( order[i] );
		}
		return sb.toString();
	}

	/**
	 * Returns the next piece that is not hash-verified, in sequential order.
	 */
	public static int
	nextUnverifiedPiece(
		int						firstPiece,
		int						lastPiece,
		HashVerifiedPieces		verified )
	{
		return nextUnverifiedPiece( buildRequestOrder( firstPiece, lastPiece ), verified );
	}

	/**
	 * Returns the next piece that is not hash-verified, walking {@code order}.
	 * Does not consult availability and therefore never skips an unfinished
	 * head/active piece because later pieces are present in the swarm.
	 *
	 * @return piece number, or {@link #NO_PIECE} if the plan is empty
	 *         or fully verified
	 */
	public static int
	nextUnverifiedPiece(
		int[]					order,
		HashVerifiedPieces		verified )
	{
		if ( verified == null || order == null || order.length == 0 ){
			return NO_PIECE;
		}
		for ( int pieceNumber: order ){
			if ( !verified.isHashVerified( pieceNumber )){
				return pieceNumber;
			}
		}
		return NO_PIECE;
	}

	public static Decision
	decide(
		boolean					enabled,
		boolean					emptyFile,
		int						firstPiece,
		int						lastPiece,
		HashVerifiedPieces		verified )
	{
		return decide( enabled, emptyFile, buildRequestOrder( firstPiece, lastPiece ), verified );
	}

	public static Decision
	decide(
		boolean					enabled,
		boolean					emptyFile,
		int[]					order,
		HashVerifiedPieces		verified )
	{
		if ( !enabled ){
			return new Decision( STATUS_DISABLED, NO_PIECE );
		}

		if ( emptyFile ){
			return new Decision( STATUS_EMPTY, NO_PIECE );
		}

		if ( order == null || order.length == 0 ){
			return new Decision( STATUS_INVALID, NO_PIECE );
		}

		int next = nextUnverifiedPiece( order, verified );
		if ( next < 0 ){
			return new Decision( STATUS_COMPLETE, NO_PIECE );
		}

		// Availability is applied by the caller: if no peer has this piece,
		// the picker must wait rather than choose another piece.
		return new Decision( STATUS_ACTIVE, next );
	}

	/**
	 * Eligibility gate used by every new-request allocation path.
	 *
	 * @param allowedPiece {@link #UNRESTRICTED},
	 *        {@link #NO_PIECE}, or the sole active piece number
	 */
	public static boolean
	isNewRequestAllowed(
		int		allowedPiece,
		int		pieceNumber )
	{
		if ( allowedPiece == UNRESTRICTED ){
			return true;
		}
		if ( allowedPiece < 0 || pieceNumber < 0 ){
			return false;
		}
		return allowedPiece == pieceNumber;
	}
}
