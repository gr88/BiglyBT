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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import com.biglybt.core.config.COConfigurationManager;
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
 * Facade for the DownLoadControlBT experiment: persist control policy,
 * an optional strict file queue, and the sole active piece from
 * disk-verified state, and expose the eligibility gate used by core
 * allocation paths.
 * <p>
 * Sequential and random selected-file plans are supported. Auto file
 * queue downloads exactly one unskipped nonempty file at a time, ordered
 * by numeric priority descending then file index. This phase does not
 * implement bans, speed policy, short retry timers, queue demotion, or
 * block-level control.
 */
public final class DownloadControlBT
{
	public static final int UNRESTRICTED = DownloadControlStrictOrder.UNRESTRICTED;
	public static final int NO_PIECE = DownloadControlStrictOrder.NO_PIECE;
	public static final int MODE_OFF = DownloadControlStrictOrder.MODE_OFF;
	public static final int MODE_SEQUENTIAL = DownloadControlStrictOrder.MODE_SEQUENTIAL;
	public static final int MODE_RANDOM = DownloadControlStrictOrder.MODE_RANDOM;
	public static final int POLICY_UNSET = DownloadControlFileQueue.POLICY_UNSET;
	public static final int POLICY_AUTO = DownloadControlFileQueue.POLICY_AUTO;
	public static final int POLICY_MANUAL = DownloadControlFileQueue.POLICY_MANUAL;
	public static final int POLICY_OFF = DownloadControlFileQueue.POLICY_OFF;

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

	public static int
	getStoredMode(
		DownloadManager		dm )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return MODE_OFF;
		}
		return dm.getDownloadState().getIntAttribute(
			DownloadManagerState.AT_DLCBT_MODE );
	}

	/**
	 * Resolved experiment mode: {@link #MODE_OFF} when disabled;
	 * {@link #MODE_SEQUENTIAL} when enabled with no stored mode
	 * (legacy {@code dlcbt.enabled=true}); otherwise the stored mode.
	 */
	public static int
	getMode(
		DownloadManager		dm )
	{
		return DownloadControlStrictOrder.resolveMode( isEnabled( dm ), getStoredMode( dm ));
	}

	/**
	 * Files-view letter for this row: {@code S}, {@code R}, or blank.
	 */
	public static String
	getModeLetterForFile(
		DownloadManager		dm,
		int					fileIndex )
	{
		DiskManagerFileInfo file = getFile( dm, fileIndex );
		boolean blank = file == null || file.getLength() <= 0;
		return DownloadControlFileQueue.modeLetterForFile(
			getPolicy( dm ),
			isEnabled( dm ),
			getStoredMode( dm ),
			getSelectedFileIndex( dm ),
			fileIndex,
			getFileModeOverride( dm, fileIndex ),
			blank );
	}

	public static boolean
	isProfileEnabled()
	{
		try{
			return COConfigurationManager.getBooleanParameter(
				DownloadControlBTDefaults.CFG_PROFILE, false );
		}catch( Throwable t ){
			return false;
		}
	}

	public static boolean
	isAutoQueueDefault()
	{
		if ( !isProfileEnabled()){
			return false;
		}
		try{
			return COConfigurationManager.getBooleanParameter(
				DownloadControlBTDefaults.CFG_AUTO_QUEUE_DEFAULT, true );
		}catch( Throwable t ){
			return true;
		}
	}

	public static boolean
	isDefaultForceStart()
	{
		try{
			return COConfigurationManager.getBooleanParameter(
				DownloadControlBTDefaults.CFG_DEFAULT_FORCE_START, false );
		}catch( Throwable t ){
			return false;
		}
	}

	public static int
	getPolicy(
		DownloadManager		dm )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return POLICY_UNSET;
		}
		return DownloadControlFileQueue.resolvePolicy(
			isEnabled( dm ),
			dm.getDownloadState().getIntAttribute( DownloadManagerState.AT_DLCBT_POLICY ));
	}

	public static boolean
	isAutoQueue(
		DownloadManager		dm )
	{
		return getPolicy( dm ) == POLICY_AUTO;
	}

	/**
	 * The Auto file queue menu switch was removed. Downloads that were left
	 * in POLICY_MANUAL keep their selected file and mode as a per-file
	 * override and then join the automatic one-file-at-a-time engine.
	 * POLICY_OFF is unchanged.
	 */
	public static void
	migrateManualPolicyToAutoQueue(
		DownloadManager		dm )
	{
		if ( dm == null || !isProfileEnabled()){
			return;
		}
		if ( getPolicy( dm ) != POLICY_MANUAL ){
			return;
		}
		setAutoQueueEnabled( dm, true );
		log( dm,
			"migrated stored POLICY_MANUAL to auto file queue; retained the previous file mode as an override rather than dropping it" );
	}

	public static String
	getTorrentModeDisplay(
		DownloadManager		dm )
	{
		if ( dm == null ){
			return "";
		}
		migrateManualPolicyToAutoQueue( dm );
		if ( !isEnabled( dm ) || getPolicy( dm ) == POLICY_OFF ){
			return "";
		}
		java.util.List<String> letters = new java.util.ArrayList<>();
		DiskManagerFileInfo[] files = dm.getDiskManagerFileInfoSet().getFiles();
		if ( files != null ){
			for ( DiskManagerFileInfo file: files ){
				letters.add( getModeLetterForFile( dm, file.getIndex()));
			}
		}
		return DownloadControlFileQueue.torrentModeDisplay(
			true, getPolicy( dm ), letters );
	}

	public static int
	getFileModeOverride(
		DownloadManager		dm,
		int					fileIndex )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return MODE_OFF;
		}
		return DownloadControlFileQueue.fileModeOverride(
			readFileModes( dm.getDownloadState()), fileIndex );
	}

	public static int
	getEffectiveFileMode(
		DownloadManager		dm,
		int					fileIndex )
	{
		return DownloadControlFileQueue.effectiveFileMode(
			getStoredMode( dm ), getFileModeOverride( dm, fileIndex ));
	}

	public static boolean
	enableForFile(
		DownloadManager		dm,
		int					fileIndex )
	{
		return enableForFile( dm, fileIndex, MODE_SEQUENTIAL );
	}

	public static boolean
	enableForFile(
		DownloadManager		dm,
		int					fileIndex,
		int					mode )
	{
		if ( dm == null ){
			return false;
		}
		if ( mode != MODE_SEQUENTIAL && mode != MODE_RANDOM ){
			disable( dm );
			return false;
		}

		if ( getPolicy( dm ) == POLICY_AUTO ){
			setFileModeOverride( dm, fileIndex, mode );
			if ( getSelectedFileIndex( dm ) == fileIndex ){
				return activateFile( dm, fileIndex, mode, POLICY_AUTO );
			}
			log( dm,
				"stored per-file mode override " +
				DownloadControlStrictOrder.modeLetter( mode ) +
				" for fileIndex=" + fileIndex +
				" ; auto file queue stays pinned to fileIndex=" +
				getSelectedFileIndex( dm ));
			return true;
		}

		return activateFile( dm, fileIndex, mode, POLICY_MANUAL );
	}

	/**
	 * Files-view S / R choice with the auto file queue as the only engine:
	 * an unchecked (skipped) file is checked for download first, the torrent
	 * is put on the strict one-file-at-a-time priority queue, and the letter
	 * is stored as that file's override.
	 */
	public static boolean
	chooseFileMode(
		DownloadManager		dm,
		int					fileIndex,
		int					mode )
	{
		if ( dm == null || ( mode != MODE_SEQUENTIAL && mode != MODE_RANDOM )){
			return false;
		}
		DiskManagerFileInfo file = getFile( dm, fileIndex );
		if ( file == null || file.getLength() <= 0 ){
			return false;
		}
		setForwardStart(dm, fileIndex, -1);
		if ( file.isSkipped()){
			file.setSkipped( false );
			log( dm, "fileIndex=" + fileIndex + " checked for download by S/R choice" );
		}
		if ( getPolicy( dm ) != POLICY_AUTO ){
			setAutoQueueEnabled( dm, true );
		}
		return enableForFile( dm, fileIndex, mode );
	}

	private static final Object ACTIVE_BOUNDARY_KEY = new Object();

	private static int forwardStart(DownloadManager dm, int fileIndex) {
		if (dm == null || dm.getDownloadState() == null) return -1;
		String[] values = dm.getDownloadState().getListAttribute(DownloadManagerState.AT_DLCBT_FORWARD_STARTS);
		if (values != null) for (String s : values) {
			try {
				String[] parts = s.split("=");
				if (parts.length == 2 && Integer.parseInt(parts[0]) == fileIndex) return Integer.parseInt(parts[1]);
			} catch (RuntimeException e) { }
		}
		return -1;
	}

	private static void setForwardStart(DownloadManager dm, int fileIndex, int piece) {
		DownloadManagerState state = dm.getDownloadState();
		synchronized (state) {
			java.util.List<String> out = new java.util.ArrayList<>();
			String[] old = state.getListAttribute(DownloadManagerState.AT_DLCBT_FORWARD_STARTS);
			String prefix = fileIndex + "=";
			if (old != null) for (String s:old) if (!s.startsWith(prefix)) out.add(s);
			if (piece >= 0) out.add(prefix + piece);
			state.setListAttribute(DownloadManagerState.AT_DLCBT_FORWARD_STARTS, out.toArray(new String[0]));
		}
	}

	/** Explicit action; the suffix is persisted and earlier pieces are not selected. */
	public static boolean startForward(DownloadManager dm, int fileIndex, int piece) {
		DiskManagerFileInfo file = getFile(dm, fileIndex);
		if (!isProfileEnabled() || file == null || file.getLength() <= 0 || piece < file.getFirstPieceNumber() || piece > file.getLastPieceNumber()) return false;
		file.setSkipped(false);
		setForwardStart(dm, fileIndex, piece);
		setFileModeOverride(dm, fileIndex, MODE_SEQUENTIAL);
		dm.setUserData(ACTIVE_BOUNDARY_KEY, null);
		return activateFile(dm, fileIndex, MODE_SEQUENTIAL, POLICY_AUTO);
	}

	static boolean
	activateFile(
		DownloadManager		dm,
		int					fileIndex,
		int					mode,
		int					policy )
	{
		if ( dm == null ){
			return false;
		}
		if ( mode != MODE_SEQUENTIAL && mode != MODE_RANDOM ){
			return false;
		}

		DiskManagerFileInfo file = getFile( dm, fileIndex );
		if ( file == null ){
			log( dm, "refused enable: invalid or missing file index " + fileIndex );
			return false;
		}

		int forward = forwardStart(dm, fileIndex);
		int first = Math.max(file.getFirstPieceNumber(), forward);
		int last = file.getLastPieceNumber();
		boolean sameFileAndMode =
			isEnabledForFile( dm, fileIndex ) && getEffectiveFileMode( dm, fileIndex ) == mode;

		DownloadManagerState state = dm.getDownloadState();
		synchronized( state ){
			state.setIntAttribute( DownloadManagerState.AT_DLCBT_FILE_INDEX, fileIndex );
			state.setBooleanAttribute( DownloadManagerState.AT_DLCBT_ENABLED, true );
			if ( policy == POLICY_AUTO ){
				int stored = getStoredMode( dm );
				if ( stored != MODE_SEQUENTIAL && stored != MODE_RANDOM ){
					state.setIntAttribute( DownloadManagerState.AT_DLCBT_MODE, MODE_RANDOM );
				}
				setFileModeOverride( dm, fileIndex, mode );
			}else{
				state.setIntAttribute( DownloadManagerState.AT_DLCBT_MODE, mode );
			}
			state.setIntAttribute( DownloadManagerState.AT_DLCBT_POLICY, policy );

		int[] order;
		long seed = 0;
		if ( mode == MODE_RANDOM ){
			int[] existing = readStoredPlan( state );
			if ( sameFileAndMode && DownloadControlStrictOrder.isValidPlan( existing, first, last )){
				order = existing;
				seed = state.getLongAttribute( DownloadManagerState.AT_DLCBT_PLAN_SEED );
				log( dm,
					"reusing persisted random plan for fileIndex=" + fileIndex +
					" pieces=[" + first + ".." + last +
					"] seed=" + seed +
					" ver=" + DownloadControlStrictOrder.PLAN_VERSION +
					" order=" + DownloadControlStrictOrder.formatPlan( order ) +
					"; one hash-verified piece at a time; wait if unavailable" );
			}else{
				seed = newRandomSeed( dm, fileIndex );
				order = DownloadControlStrictOrder.buildRandomRequestOrder(
					first, last, new Random( seed ));
				storePlan( state, order, seed );
				log( dm,
					"enabled experimental random order for fileIndex=" + fileIndex +
					" length=" + file.getLength() +
					" pieces=[" + first + ".." + last +
					"] seed=" + seed +
					" ver=" + DownloadControlStrictOrder.PLAN_VERSION +
					" order=" + DownloadControlStrictOrder.formatPlan( order ) +
					"; first,last,then shuffled interiors; " +
					"one hash-verified piece at a time; wait if unavailable" );
			}
		}else{
			clearPlan( state );
			order = forward >= file.getFirstPieceNumber()
				? DownloadControlFileQueue.forwardOrder(first, last)
				: DownloadControlStrictOrder.buildRequestOrder( first, last );
			log( dm,
				"enabled experimental sequential order for fileIndex=" + fileIndex +
				" length=" + file.getLength() +
				" pieces=[" + first + ".." + last +
				"] order=" + DownloadControlStrictOrder.formatPlan( order ) +
				(forward >= file.getFirstPieceNumber() ? "; sequential forward from chosen piece; " : "; first,last,then interior ascending; ") +
				"one hash-verified piece at a time; wait if unavailable" );
		}

		}
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
		DownloadManagerState state = dm.getDownloadState();
		synchronized( state ){
			state.setBooleanAttribute( DownloadManagerState.AT_DLCBT_ENABLED, false );
			state.setIntAttribute( DownloadManagerState.AT_DLCBT_MODE, MODE_OFF );
			state.setIntAttribute( DownloadManagerState.AT_DLCBT_POLICY, POLICY_OFF );
			clearPlan( state );
		}
		log( dm, "disabled; experimental gates off; normal piece picking restored" );
	}

	public static void
	setAutoQueueEnabled(
		DownloadManager		dm,
		boolean				auto )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return;
		}
		if ( auto ){
			int previousPolicy = getPolicy( dm );
			int previousFile = getSelectedFileIndex( dm );
			int previousMode = getMode( dm );
			DownloadManagerState state = dm.getDownloadState();
			synchronized( state ){
				if ( previousPolicy == POLICY_MANUAL && previousFile >= 0
					&& ( previousMode == MODE_SEQUENTIAL || previousMode == MODE_RANDOM )){
					setFileModeOverride( dm, previousFile, previousMode );
				}
				if ( getStoredMode( dm ) != MODE_SEQUENTIAL && getStoredMode( dm ) != MODE_RANDOM ){
					state.setIntAttribute( DownloadManagerState.AT_DLCBT_MODE, MODE_RANDOM );
				}
				state.setIntAttribute( DownloadManagerState.AT_DLCBT_POLICY, POLICY_AUTO );
				state.setBooleanAttribute( DownloadManagerState.AT_DLCBT_ENABLED, true );
			}
			refreshAutoActiveFile( dm, dm.getDiskManager());
			log( dm, "auto file queue enabled; default piece mode Random unless a file override is set" );
			return;
		}
		if ( getPolicy( dm ) == POLICY_AUTO ){
			DownloadManagerState state = dm.getDownloadState();
			synchronized( state ){
				state.setIntAttribute( DownloadManagerState.AT_DLCBT_POLICY, POLICY_MANUAL );
			}
			log( dm, "auto file queue disabled; keeping one-file manual control if a file is active" );
		}
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
		return getAllowedPieceNumber( dm, diskManager, true );
	}

	static int
	getAllowedPieceNumber(
		DownloadManager		dm,
		DiskManager			diskManager,
		boolean				allowAdvance )
	{
		if ( !isEnabled( dm )){
			return UNRESTRICTED;
		}

		if ( allowAdvance ){
			migrateManualPolicyToAutoQueue( dm );
		}

		if ( allowAdvance && isAutoQueue( dm )){
			refreshAutoActiveFile( dm, diskManager );
			if ( !isEnabled( dm )){
				return UNRESTRICTED;
			}
			if ( DownloadControlFileQueue.pickPinnedOrNext(
					snapshotFiles( dm, diskManager ),
					getSelectedFileIndex( dm ),
					createVerifiedView( diskManager )) < 0 ){
				logOnce( dm, "queue-complete",
					"auto file queue has no remaining unskipped nonempty unfinished file; requesting nothing" );
				return NO_PIECE;
			}
		}

		int fileIndex = getSelectedFileIndex( dm );
		DiskManagerFileInfo file = getFile( dm, diskManager, fileIndex );
		if ( file == null ){
			logOnce( dm, "invalid-file",
				"enabled but fileIndex=" + fileIndex + " is not available; requesting nothing" );
			return NO_PIECE;
		}

		if ( file.isSkipped()){
			logOnce( dm, "skipped-" + fileIndex,
				"selected fileIndex=" + fileIndex + " is skipped; requesting nothing" );
			return NO_PIECE;
		}

		boolean empty = file.getLength() <= 0 || file.getNbPieces() <= 0;
		int start = forwardStart(dm, fileIndex);
		int first = Math.max(file.getFirstPieceNumber(), start);
		int last = file.getLastPieceNumber();
		int mode = getEffectiveFileMode( dm, fileIndex );

		int[] order;
		if ( mode == MODE_RANDOM ){
			order = readStoredPlan( dm.getDownloadState() );
			if ( !DownloadControlStrictOrder.isValidPlan( order, first, last )){
				logOnce( dm, "invalid-plan",
					"enabled random mode for fileIndex=" + fileIndex +
					" but stored plan is missing or invalid for pieces=[" +
					first + ".." + last + "]; requesting nothing until Random is selected again" );
				return NO_PIECE;
			}
		}else{
			order = start >= file.getFirstPieceNumber()
				? DownloadControlFileQueue.forwardOrder(first, last)
				: DownloadControlStrictOrder.buildRequestOrder( first, last );
		}

		DownloadControlStrictOrder.HashVerifiedPieces verified =
			createVerifiedView( diskManager );

		DownloadControlStrictOrder.Decision decision =
			DownloadControlStrictOrder.decide( true, empty, order, verified );

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
			logOnce( dm, "complete-" + fileIndex,
				"selected fileIndex=" + fileIndex +
				" pieces [" + first + ".." + last +
				"] are hash-verified; " +
				( isAutoQueue( dm )
					? "auto file queue will move to the next unfinished file"
					: "requesting nothing until mode is disabled" ));
			return NO_PIECE;
		}

		int active = decision.activePiece;
		if (allowAdvance) dm.setUserData(ACTIVE_BOUNDARY_KEY, Integer.valueOf(active));
		logOnce( dm, "active-" + mode + "-" + active,
			"active piece " + active +
			" of fileIndex=" + fileIndex +
			" range=[" + first + ".." + last +
			"] mode=" + DownloadControlStrictOrder.modeLetter( mode ) +
			"; will wait indefinitely if this piece is unavailable" );
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
		com.biglybt.core.peer.PEPiece p = peerControl.getPiece(allowedPiece);
		DownloadControlBTRerequest.Coverage coverage = DownloadControlBTRerequest.coverage(peerControl, diskManager, p);
		boolean covered = coverage.covered;
		boolean arrived = coverage.allArrived;
		String status = arrived ? "all block data arrived; awaiting disk write/hash verification" : covered
			? "fully covered by received/requested blocks; automatic rerequest cadence active"
			: "not currently available/requestable; no fallback to a later piece";
		logOnce(dm, "wait-" + allowedPiece + "-" + status, "active piece " + allowedPiece + ": " + status);
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
		int allowed = getAllowedPieceNumber( dm, diskManager, false );
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

	public static void
	applyNewTorrentDefaults(
		DownloadManager				dm,
		boolean						explicitOff,
		boolean						autoQueue,
		Map<Integer, Integer>		fileModes )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return;
		}
		if ( dm.getDownloadState().getFlag( DownloadManagerState.FLAG_METADATA_DOWNLOAD )){
			return;
		}
		if ( getPolicy( dm ) != POLICY_UNSET ){
			return;
		}

		if ( fileModes == null ){
			fileModes = new HashMap<>();
		}
		storeFileModes( dm, fileModes );

		DownloadControlFileQueue.FileSnap[] files = snapshotFiles( dm, dm.getDiskManager());
		DownloadControlFileQueue.NewTorrentDecision decision =
			DownloadControlFileQueue.planNewTorrent(
				files,
				isProfileEnabled(),
				false,
				explicitOff,
				autoQueue,
				fileModes,
				createVerifiedView( dm.getDiskManager()));

		if ( decision.policy == POLICY_OFF ){
			disable( dm );
			return;
		}
		if ( decision.isNoOp()){
			return;
		}

		if ( decision.policy == POLICY_AUTO ){
			DownloadManagerState state = dm.getDownloadState();
			synchronized( state ){
				state.setIntAttribute( DownloadManagerState.AT_DLCBT_POLICY, POLICY_AUTO );
				state.setBooleanAttribute( DownloadManagerState.AT_DLCBT_ENABLED, true );
				state.setIntAttribute( DownloadManagerState.AT_DLCBT_MODE, MODE_RANDOM );
			}
			if ( decision.initialFileIndex >= 0 ){
				activateFile(
					dm,
					decision.initialFileIndex,
					decision.initialFileMode,
					POLICY_AUTO );
			}else{
				log( dm, "auto file queue enabled on add but no eligible file is ready yet" );
			}
			return;
		}

		if ( decision.policy == POLICY_MANUAL && decision.initialFileIndex >= 0 ){
			activateFile(
				dm,
				decision.initialFileIndex,
				decision.initialFileMode,
				POLICY_MANUAL );
		}
	}

	static void
	refreshAutoActiveFile(
		DownloadManager		dm,
		DiskManager			diskManager )
	{
		if ( dm == null || dm.getDownloadState() == null || !isAutoQueue( dm ) || !isEnabled( dm )){
			return;
		}
		DownloadManagerState state = dm.getDownloadState();
		synchronized( state ){
			DownloadControlFileQueue.FileSnap[] files = snapshotFiles( dm, diskManager );
			int pinned = getSelectedFileIndex( dm );
			Object lastPiece = dm.getUserData(ACTIVE_BOUNDARY_KEY);
			int next = DownloadControlFileQueue.pickAtPieceBoundary(
				files, pinned, createVerifiedView( diskManager ), lastPiece instanceof Integer ? ((Integer)lastPiece).intValue() : -1);
			if ( next < 0 ){
				return;
			}
			int mode = getEffectiveFileMode( dm, next );
			boolean need = next != pinned || !isEnabledForFile( dm, next );
			if ( mode == MODE_RANDOM ){
				DownloadControlFileQueue.FileSnap file = DownloadControlFileQueue.fileAt( files, next );
				if ( file != null
					&& !DownloadControlStrictOrder.isValidPlan(
						readStoredPlan( state ), file.firstPiece, file.lastPiece )){
					need = true;
				}
			}
			if ( need ){
				log( dm,
					"auto file queue selecting fileIndex=" + next +
					" mode=" + DownloadControlStrictOrder.modeLetter( mode ) +
					" (previous pinned fileIndex=" + pinned + ")" );
				dm.setUserData(ACTIVE_BOUNDARY_KEY, null);
				activateFile( dm, next, mode, POLICY_AUTO );
			}
		}
	}

	static DownloadControlFileQueue.FileSnap[]
	snapshotFiles(
		DownloadManager		dm,
		DiskManager			diskManager )
	{
		DiskManagerFileInfo[] infos = null;
		if ( diskManager != null ){
			infos = diskManager.getFiles();
		}
		if (( infos == null || infos.length == 0 )
			&& dm != null && dm.getDiskManagerFileInfoSet() != null ){
			infos = dm.getDiskManagerFileInfoSet().getFiles();
		}
		if ( infos == null ){
			return new DownloadControlFileQueue.FileSnap[0];
		}
		DownloadControlFileQueue.FileSnap[] snaps = new DownloadControlFileQueue.FileSnap[infos.length];
		for (int i = 0; i < infos.length; i++){
			DiskManagerFileInfo file = infos[i];
			if ( file == null ){
				snaps[i] = new DownloadControlFileQueue.FileSnap( i, 0, -1, -1, 0, 0, true );
				continue;
			}
			snaps[i] = new DownloadControlFileQueue.FileSnap(
				file.getIndex(),
				file.getLength(),
				Math.max(file.getFirstPieceNumber(), forwardStart(dm, file.getIndex())),
				file.getLastPieceNumber(),
				file.getNbPieces(),
				file.getPriority(),
				file.isSkipped());
		}
		return snaps;
	}

	static Map<Integer, Integer>
	readFileModes(
		DownloadManagerState		state )
	{
		if ( state == null ){
			return new HashMap<>();
		}
		return DownloadControlFileQueue.parseFileModes(
			state.getListAttribute( DownloadManagerState.AT_DLCBT_FILE_MODES ));
	}

	static void
	storeFileModes(
		DownloadManager				dm,
		Map<Integer, Integer>		modes )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return;
		}
		dm.getDownloadState().setListAttribute(
			DownloadManagerState.AT_DLCBT_FILE_MODES,
			DownloadControlFileQueue.serializeFileModes( modes ));
	}

	static void
	setFileModeOverride(
		DownloadManager		dm,
		int					fileIndex,
		int					mode )
	{
		if ( dm == null || dm.getDownloadState() == null ){
			return;
		}
		Map<Integer, Integer> modes = readFileModes( dm.getDownloadState());
		if ( mode == MODE_SEQUENTIAL || mode == MODE_RANDOM ){
			modes.put( Integer.valueOf( fileIndex ), Integer.valueOf( mode ));
		}else{
			modes.remove( Integer.valueOf( fileIndex ));
		}
		storeFileModes( dm, modes );
	}

		static int[]
	readStoredPlan(
		DownloadManagerState		state )
	{
		if ( state == null ){
			return new int[0];
		}
		return DownloadControlStrictOrder.parsePlan(
			state.getListAttribute( DownloadManagerState.AT_DLCBT_PLAN ));
	}

	static void
	storePlan(
		DownloadManagerState		state,
		int[]						order,
		long						seed )
	{
		if ( state == null ){
			return;
		}
		state.setListAttribute(
			DownloadManagerState.AT_DLCBT_PLAN,
			DownloadControlStrictOrder.serializePlan( order ));
		state.setLongAttribute( DownloadManagerState.AT_DLCBT_PLAN_SEED, seed );
		state.setIntAttribute(
			DownloadManagerState.AT_DLCBT_PLAN_VER,
			DownloadControlStrictOrder.PLAN_VERSION );
	}

	static void
	clearPlan(
		DownloadManagerState		state )
	{
		if ( state == null ){
			return;
		}
		state.setListAttribute( DownloadManagerState.AT_DLCBT_PLAN, new String[0] );
		state.setLongAttribute( DownloadManagerState.AT_DLCBT_PLAN_SEED, 0 );
		state.setIntAttribute( DownloadManagerState.AT_DLCBT_PLAN_VER, 0 );
	}

	static long
	newRandomSeed(
		DownloadManager		dm,
		int					fileIndex )
	{
		long seed = System.nanoTime() ^ ((long)fileIndex * 0x9E3779B97F4A7C15L);
		try{
			if ( dm != null && dm.getTorrent() != null ){
				byte[] hash = dm.getTorrent().getHash();
				if ( hash != null ){
					for (int i = 0; i < hash.length && i < 8; i++){
						seed ^= ((long)(hash[i] & 0xff)) << (8 * (i % 8));
					}
				}
			}
		}catch( Throwable ignore ){
		}
		if ( seed == 0 ){
			seed = 1L;
		}
		return seed;
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
