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

/**
 * Pure ordering rules for the DownLoadControlBT first-phase prototype.
 * <p>
 * Selected-file piece order is: the first containing piece, then the last
 * containing piece, then interior pieces in ascending order. Already
 * hash-verified pieces are skipped. The next unfinished piece in that order
 * stays active until it is hash-verified; this helper never falls forward
 * because a later piece looks more available.
 * <p>
 * When the first and last containing pieces are the same, that piece appears
 * once. Shared file-boundary pieces are included in full as ordinary members
 * of the range.
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
	 * Builds the full request order for a zero-based inclusive piece range.
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
	 * Returns the next piece that is not hash-verified, in strict order.
	 * Does not consult availability and therefore never skips an unfinished
	 * head/active piece because later pieces are present in the swarm.
	 *
	 * @return piece number, or {@link #NO_PIECE} if the range is empty,
	 *         invalid, or fully verified
	 */
	public static int
	nextUnverifiedPiece(
		int						firstPiece,
		int						lastPiece,
		HashVerifiedPieces		verified )
	{
		if ( verified == null ){
			return NO_PIECE;
		}

		int[] order = buildRequestOrder( firstPiece, lastPiece );
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
		if ( !enabled ){
			return new Decision( STATUS_DISABLED, NO_PIECE );
		}

		if ( emptyFile ){
			return new Decision( STATUS_EMPTY, NO_PIECE );
		}

		if ( firstPiece < 0 || lastPiece < 0 || firstPiece > lastPiece ){
			return new Decision( STATUS_INVALID, NO_PIECE );
		}

		int next = nextUnverifiedPiece( firstPiece, lastPiece, verified );
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
