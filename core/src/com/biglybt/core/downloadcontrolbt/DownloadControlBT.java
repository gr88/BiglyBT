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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.biglybt.core.disk.DiskManager;
import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.disk.DiskManagerPiece;
import com.biglybt.core.disk.DiskManagerReadRequest;
import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.download.DownloadManagerState;
import com.biglybt.core.download.impl.DownloadManagerController;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.peer.PEPeerManager;
import com.biglybt.core.peer.PEPeerManagerAdapter;
import com.biglybt.core.peer.impl.PEPeerControl;
import com.biglybt.core.peer.impl.PEPeerTransport;
import com.biglybt.core.torrent.TOTorrent;
import com.biglybt.core.util.ByteFormatter;

/**
 * Facade for the DownLoadControlBT first-phase experiment: persist one
 * selected file per torrent, compute the sole active piece from
 * disk-verified state, and expose the eligibility gate used by core
 * allocation paths.
 * <p>
 * This phase does not implement bans, randomised order, speed policy,
 * short retry timers, or queue demotion.
 */
public final class DownloadControlBT
{
	public static final int UNRESTRICTED = DownloadControlStrictOrder.UNRESTRICTED;
	public static final int NO_PIECE = DownloadControlStrictOrder.NO_PIECE;

	private static final LogIDs LOGID = LogIDs.PIECES;
	private static final String LOG_PREFIX = "DownLoadControlBT: ";
	private static final int ONCE_LOG_LIMIT = 256;

	private static final Map<String, Boolean> once_keys =
		Collections.synchronizedMap(
			new LinkedHashMap<String, Boolean>( 64, 0.75f, true )
			{
				@Override
				protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest)
				{
					return size() > ONCE_LOG_LIMIT;
				}
			});

	private DownloadControlBT()
	{
	}

	public static boolean
	isEnabled(
		DownloadManager		dm )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return false;
		}
		return dm.getDownloadState().getBooleanAttribute(
			DownloadManagerState.AT_DLCBT_ENABLED );
	}

	public static int
	getSelectedFileIndex(
		DownloadManager		dm )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return -1;
		}
		return dm.getDownloadState().getIntAttribute(
			DownloadManagerState.AT_DLCBT_FILE_INDEX );
	}

	public static boolean
	isEnabledForFile(
		DownloadManager		dm,
		int					fileIndex )
	{
		return isEnabled( dm ) && getSelectedFileIndex( dm ) == fileIndex;
	}

	public static boolean
	enableForFile(
		DownloadManager		dm,
		int					fileIndex )
	{
		if ( dm == null ){
			return false;
		}

		DiskManagerFileInfo file = getFile( dm, fileIndex );
		if ( file == null ){
			log( dm, "refused enable: invalid or missing file index " + fileIndex );
			return false;
		}

		DownloadManagerState state = dm.getDownloadState();
		state.setIntAttribute( DownloadManagerState.AT_DLCBT_FILE_INDEX, fileIndex );
		state.setBooleanAttribute( DownloadManagerState.AT_DLCBT_ENABLED, true );

		log( dm,
			"enabled experimental strict order for fileIndex=" + fileIndex +
			" length=" + file.getLength() +
			" pieces=[" + file.getFirstPieceNumber() +
			".." + file.getLastPieceNumber() +
			"] order=first,last,then interior ascending; " +
			"one hash-verified piece at a time; wait if unavailable" );

		cancelOffTargetRequests( dm );
		return true;
	}

	public static void
	disable(
		DownloadManager		dm )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return;
		}
		dm.getDownloadState().setBooleanAttribute(
			DownloadManagerState.AT_DLCBT_ENABLED, false );
		log( dm, "disabled; normal piece picking restored" );
	}

	/**
	 * @return {@link #UNRESTRICTED} when the mode is off,
	 *         {@link #NO_PIECE} when it is on but nothing may be requested,
	 *         otherwise the sole piece that may receive new requests
	 */
	public static int
	getAllowedPieceNumber(
		PEPeerControl		peerControl,
		DiskManager			diskManager )
	{
		DownloadManager dm = resolveDownloadManager( peerControl, diskManager );
		return getAllowedPieceNumber( dm, diskManager );
	}

	public static int
	getAllowedPieceNumber(
		DownloadManager		dm,
		DiskManager			diskManager )
	{
		if ( !isEnabled( dm )){
			return UNRESTRICTED;
		}

		int fileIndex = getSelectedFileIndex( dm );
		DiskManagerFileInfo file = getFile( dm, diskManager, fileIndex );
		if ( file == null ){
			logOnce( dm, "invalid-file",
				"enabled but fileIndex=" + fileIndex + " is not available; requesting nothing" );
			return NO_PIECE;
		}

		boolean empty = file.getLength() <= 0 || file.getNbPieces() <= 0;
		int first = file.getFirstPieceNumber();
		int last = file.getLastPieceNumber();

		DownloadControlStrictOrder.HashVerifiedPieces verified =
			createVerifiedView( diskManager );

		DownloadControlStrictOrder.Decision decision =
			DownloadControlStrictOrder.decide( true, empty, first, last, verified );

		if ( decision.status == DownloadControlStrictOrder.STATUS_EMPTY ){
			logOnce( dm, "empty",
				"selected fileIndex=" + fileIndex + " is empty; nothing to request" );
			return NO_PIECE;
		}

		if ( decision.status == DownloadControlStrictOrder.STATUS_INVALID ){
			logOnce( dm, "invalid-range",
				"selected fileIndex=" + fileIndex +
				" has invalid piece range [" + first + ".." + last + "]; requesting nothing" );
			return NO_PIECE;
		}

		if ( decision.status == DownloadControlStrictOrder.STATUS_COMPLETE ){
			logOnce( dm, "complete",
				"selected fileIndex=" + fileIndex +
				" pieces [" + first + ".." + last +
				"] are hash-verified; requesting nothing until mode is disabled" );
			return NO_PIECE;
		}

		int active = decision.activePiece;
		logOnce( dm, "active-" + active,
			"active piece " + active +
			" of fileIndex=" + fileIndex +
			" range=[" + first + ".." + last +
			"]; will wait indefinitely if this piece is unavailable" );
		return active;
	}

	public static boolean
	isRestrictingNewRequests(
		PEPeerControl		peerControl,
		DiskManager			diskManager )
	{
		return getAllowedPieceNumber( peerControl, diskManager ) != UNRESTRICTED;
	}

	public static boolean
	isNewRequestAllowed(
		int		allowedPiece,
		int		pieceNumber )
	{
		return DownloadControlStrictOrder.isNewRequestAllowed( allowedPiece, pieceNumber );
	}

	public static void
	noteNoCandidate(
		PEPeerControl		peerControl,
		DiskManager			diskManager,
		int					allowedPiece )
	{
		if ( allowedPiece < 0 ){
			return;
		}
		DownloadManager dm = resolveDownloadManager( peerControl, diskManager );
		logOnce( dm, "wait-" + allowedPiece,
			"waiting for piece " + allowedPiece +
			" (unavailable or not requestable); not falling back to a later piece" );
	}

	public static void
	noteRequestsAllocated(
		PEPeerControl		peerControl,
		DiskManager			diskManager,
		int					pieceNumber,
		int					blockCount )
	{
		if ( pieceNumber < 0 || blockCount <= 0 ){
			return;
		}
		if ( !isRestrictingNewRequests( peerControl, diskManager )){
			return;
		}
		DownloadManager dm = resolveDownloadManager( peerControl, diskManager );
		logOnce( dm, "req-" + pieceNumber,
			"issued " + blockCount +
			" new block request(s) for active piece " + pieceNumber );
	}

	public static void
	notePieceDoneChanged(
		DiskManager			diskManager,
		DiskManagerPiece	dmPiece )
	{
		if ( diskManager == null || dmPiece == null ){
			return;
		}
		DownloadManager dm = resolveDownloadManager( null, diskManager );
		if ( !isEnabled( dm )){
			return;
		}
		int pieceNumber = dmPiece.getPieceNumber();
		if ( dmPiece.isDone()){
			log( dm, "piece " + pieceNumber + " hash-verified complete" );
		}else{
			log( dm, "piece " + pieceNumber +
				" is not hash-verified (check failed or unfinished); not advancing" );
		}
	}

	public static void
	noteIncomingBlock(
		PEPeerManager		peerManager,
		int					pieceNumber,
		int					offset )
	{
		if ( peerManager == null ){
			return;
		}
		DiskManager diskManager = peerManager.getDiskManager();
		PEPeerControl peerControl =
			(peerManager instanceof PEPeerControl) ? (PEPeerControl)peerManager : null;
		int allowed = getAllowedPieceNumber( peerControl, diskManager );
		if ( allowed == UNRESTRICTED ){
			return;
		}
		if ( allowed == pieceNumber ){
			return;
		}
		DownloadManager dm = resolveDownloadManager( peerControl, diskManager );
		logOnce( dm, "late-" + pieceNumber,
			"late or off-target block arrived for piece " + pieceNumber +
			" offset=" + offset +
			" (active=" + allowed +
			"); not classified malicious; in-flight data may still be written, " +
			"but unverified bytes do not count as completion" );
	}

	public static void
	cancelOffTargetRequests(
		DownloadManager		dm )
	{
		if ( dm == null ){
			return;
		}
		PEPeerManager pm = dm.getPeerManager();
		if ( pm == null ){
			return;
		}
		DiskManager diskManager = pm.getDiskManager();
		PEPeerControl peerControl =
			(pm instanceof PEPeerControl) ? (PEPeerControl)pm : null;
		int allowed = getAllowedPieceNumber( peerControl, diskManager );
		if ( allowed == UNRESTRICTED ){
			return;
		}
		cancelOffTargetRequests( pm, diskManager, allowed );
	}

	public static int
	cancelOffTargetRequests(
		PEPeerManager		pm,
		DiskManager			diskManager,
		int					allowedPiece )
	{
		if ( pm == null || diskManager == null ){
			return 0;
		}

		DiskManagerPiece[] pieces = diskManager.getPieces();
		if ( pieces == null ){
			return 0;
		}

		int cancelledPieces = 0;
		for ( Object rawPeer: pm.getPeers() ){
			if ( !( rawPeer instanceof PEPeerTransport )){
				continue;
			}
			PEPeerTransport pt = (PEPeerTransport)rawPeer;
			int[] requested = pt.getOutgoingRequestedPieceNumbers();
			if ( requested == null ){
				continue;
			}
			for ( int pieceNumber: requested ){
				if ( pieceNumber < 0 || pieceNumber >= pieces.length ){
					continue;
				}
				if ( DownloadControlStrictOrder.isNewRequestAllowed( allowedPiece, pieceNumber )){
					continue;
				}
				DiskManagerPiece dmPiece = pieces[pieceNumber];
				if ( dmPiece == null ){
					continue;
				}
				int nb = dmPiece.getNbBlocks();
				for ( int b = 0; b < nb; b++ ){
					DiskManagerReadRequest request = pm.createDiskManagerRequest(
						pieceNumber,
						b * DiskManager.BLOCK_SIZE,
						dmPiece.getBlockSize( b ));
					pt.sendCancel( request );
				}
				cancelledPieces++;
			}
		}

		if ( cancelledPieces > 0 ){
			DownloadManager dm = resolveDownloadManager(
				(pm instanceof PEPeerControl) ? (PEPeerControl)pm : null,
				diskManager );
			log( dm,
				"cancelled outstanding requests for " + cancelledPieces +
				" off-target piece(s); active=" + allowedPiece +
				". Already-sent data may still arrive on the network." );
		}
		return cancelledPieces;
	}

	public static DownloadManager
	resolveDownloadManager(
		PEPeerControl		peerControl,
		DiskManager			diskManager )
	{
		if ( peerControl != null ){
			PEPeerManagerAdapter adapter = peerControl.getAdapter();
			if ( adapter instanceof DownloadManagerController ){
				DownloadManager dm = ((DownloadManagerController)adapter).getDownloadManager();
				if ( dm != null ){
					return dm;
				}
			}
		}

		if ( diskManager != null ){
			DiskManagerFileInfo[] files = diskManager.getFiles();
			if ( files != null ){
				for ( DiskManagerFileInfo file: files ){
					if ( file != null && file.getDownloadManager() != null ){
						return file.getDownloadManager();
					}
				}
			}
		}
		return null;
	}

	static DownloadControlStrictOrder.HashVerifiedPieces
	createVerifiedView(
		final DiskManager		diskManager )
	{
		return new DownloadControlStrictOrder.HashVerifiedPieces()
		{
			@Override
			public boolean
			isHashVerified(
				int		pieceNumber )
			{
				if ( diskManager == null ){
					return false;
				}
				DiskManagerPiece[] pieces = diskManager.getPieces();
				if ( pieces == null || pieceNumber < 0 || pieceNumber >= pieces.length ){
					return false;
				}
				DiskManagerPiece piece = pieces[pieceNumber];
				return piece != null && piece.isDone();
			}
		};
	}

	private static DiskManagerFileInfo
	getFile(
		DownloadManager		dm,
		int					fileIndex )
	{
		return getFile( dm, dm == null ? null : dm.getDiskManager(), fileIndex );
	}

	private static DiskManagerFileInfo
	getFile(
		DownloadManager		dm,
		DiskManager			diskManager,
		int					fileIndex )
	{
		if ( fileIndex < 0 ){
			return null;
		}

		if ( diskManager != null ){
			DiskManagerFileInfo[] files = diskManager.getFiles();
			if ( files != null && fileIndex < files.length ){
				return files[fileIndex];
			}
		}

		if ( dm != null && dm.getDiskManagerFileInfoSet() != null ){
			DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
			if ( files != null && fileIndex < files.length ){
				return files[fileIndex];
			}
		}
		return null;
	}

	private static void
	logOnce(
		DownloadManager		dm,
		String				keySuffix,
		String				message )
	{
		String key = idOf( dm ) + ":" + keySuffix;
		synchronized( once_keys ){
			if ( once_keys.containsKey( key )){
				return;
			}
			once_keys.put( key, Boolean.TRUE );
		}
		log( dm, message );
	}

	private static String
	idOf(
		DownloadManager		dm )
	{
		if ( dm == null ){
			return "unknown";
		}
		try{
			TOTorrent torrent = dm.getTorrent();
			if ( torrent != null ){
				return ByteFormatter.encodeString( torrent.getHash() );
			}
		}catch( Throwable ignore ){
		}
		return String.valueOf( System.identityHashCode( dm ));
	}

	static void
	log(
		DownloadManager		dm,
		String				message )
	{
		String text = LOG_PREFIX + message;
		TOTorrent torrent = null;
		try{
			if ( dm != null ){
				torrent = dm.getTorrent();
			}
		}catch( Throwable ignore ){
		}

		if ( Logger.isEnabled()){
			if ( torrent != null ){
				Logger.log( new LogEvent( torrent, LOGID, text ));
			}else{
				Logger.log( new LogEvent( LOGID, text ));
			}
		}
	}
}
