/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.core.downloadcontrolbt;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peermanager.piecepicker.PiecePicker;

/**
 * DLCBT Nov 20: per-file rarest-only upload. A piece may be sent only if it
 * is at the current lowest availability among the pieces of that file that
 * we have, or it is the file's first or last piece (which also covers
 * pieces that span two files). Availability includes our own copy.
 */
public final class DownloadControlBTRarestUpload
{
	public static final String REASON_ONLY_NON_RAREST = "only-non-rarest-requests";
	public static final int DISCONNECT_AFTER_REJECTS = 8;
	public static final String CFG_DISCONNECT_ENABLED = "dlcbt.upload.rarestonly.disconnect.enabled";
	public static final String CFG_DISCONNECT_REJECTS = "dlcbt.upload.rarestonly.disconnect.rejects";

	private DownloadControlBTRarestUpload(){}

	public static boolean isEnabled()
	{
		try{
			return DownloadControlBT.isProfileEnabled()
				&& COConfigurationManager.getBooleanParameter(
					DownloadControlBTDefaults.CFG_RAREST_ONLY, true );
		}catch( Throwable t ){
			return false;
		}
	}

	public static boolean isDisconnectEnabled()
	{
		try{
			return isEnabled()
				&& COConfigurationManager.getBooleanParameter( CFG_DISCONNECT_ENABLED, true );
		}catch( Throwable t ){
			return false;
		}
	}

	public static int disconnectAfterRejects()
	{
		try{
			int n = COConfigurationManager.getIntParameter( CFG_DISCONNECT_REJECTS, DISCONNECT_AFTER_REJECTS );
			return Math.max( 1, n );
		}catch( Throwable t ){
			return DISCONNECT_AFTER_REJECTS;
		}
	}

	/** First/last piece of any file is always allowed (covers spanning pieces). */
	public static boolean isBoundaryPiece( DiskManagerFileInfo[] files, int piece )
	{
		if ( files == null ){
			return false;
		}
		for ( DiskManagerFileInfo f : files ){
			if ( f == null || f.getLength() <= 0 ){
				continue;
			}
			if ( piece == f.getFirstPieceNumber() || piece == f.getLastPieceNumber()){
				return true;
			}
		}
		return false;
	}

	/**
	 * Lowest availability among pieces of [first, last] that we have.
	 * Returns -1 if we have none of them.
	 */
	public static int minHaveAvailability( int first, int last, boolean[] have, int[] avail )
	{
		if ( have == null || avail == null || first < 0 || last < first ){
			return -1;
		}
		int end = Math.min( last, Math.min( have.length, avail.length ) - 1 );
		int min = Integer.MAX_VALUE;
		for ( int p = first; p <= end; p++ ){
			if ( have[p] ){
				int a = avail[p];
				if ( a < min ){
					min = a;
				}
			}
		}
		return min == Integer.MAX_VALUE ? -1 : min;
	}

	/**
	 * Pure decision: may we send {@code piece} given have[] and avail[]
	 * (avail includes us) and the file ranges.
	 */
	public static boolean maySend(
		int			piece,
		int[]		fileFirst,
		int[]		fileLast,
		boolean[]	have,
		int[]		avail )
	{
		if ( have == null || avail == null || piece < 0 || piece >= have.length || !have[piece] ){
			return false;
		}
		if ( fileFirst == null ){
			return true;
		}
		boolean inFile = false;
		for ( int i = 0; i < fileFirst.length; i++ ){
			int first = fileFirst[i];
			int last = fileLast[i];
			if ( piece == first || piece == last ){
				return true;
			}
			if ( piece >= first && piece <= last ){
				inFile = true;
				int min = minHaveAvailability( first, last, have, avail );
				if ( min >= 0 && avail[piece] == min ){
					return true;
				}
			}
		}
		return !inFile;
	}

	public static boolean maySend( PEPeerManager control, int piece )
	{
		if ( !isEnabled() || control == null ){
			return true;
		}
		try{
			DiskManager dm = control.getDiskManager();
			PiecePicker picker = control.getPiecePicker();
			if ( dm == null || picker == null ){
				return true;
			}
			DiskManagerPiece[] pieces = dm.getPieces();
			if ( pieces == null || piece < 0 || piece >= pieces.length || !pieces[piece].isDone()){
				return false;
			}
			int[] avail = picker.getAvailability();
			if ( avail == null ){
				return true;
			}
			boolean[] have = new boolean[pieces.length];
			for ( int i = 0; i < pieces.length; i++ ){
				have[i] = pieces[i].isDone();
			}
			DiskManagerFileInfo[] files = dm.getFiles();
			int n = files == null ? 0 : files.length;
			int[] first = new int[n];
			int[] last = new int[n];
			for ( int i = 0; i < n; i++ ){
				first[i] = files[i].getFirstPieceNumber();
				last[i] = files[i].getLastPieceNumber();
			}
			return maySend( piece, first, last, have, avail );
		}catch( Throwable t ){
			return true;
		}
	}

	public static boolean mayAnnounce( PEPeerManager control, int piece )
	{
		return maySend( control, piece );
	}
}
