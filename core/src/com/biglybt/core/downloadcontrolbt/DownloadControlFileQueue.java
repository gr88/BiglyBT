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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure helpers for the per-torrent FILE queue: priority then file-index
 * ordering, pin-until-finished (or explicit skip), per-file piece-mode
 * overrides, and new-torrent policy planning. No live network or GUI.
 */
public final class DownloadControlFileQueue
{
	public static final int POLICY_UNSET = 0;
	public static final int POLICY_AUTO = 1;
	public static final int POLICY_MANUAL = 2;
	public static final int POLICY_OFF = 3;

	private DownloadControlFileQueue()
	{
	}

	public static final class FileSnap
	{
		public final int index;
		public final long length;
		public final int firstPiece;
		public final int lastPiece;
		public final int nbPieces;
		public final int priority;
		public final boolean skipped;

		public FileSnap(
			int			index,
			long		length,
			int			firstPiece,
			int			lastPiece,
			int			nbPieces,
			int			priority,
			boolean		skipped )
		{
			this.index = index;
			this.length = length;
			this.firstPiece = firstPiece;
			this.lastPiece = lastPiece;
			this.nbPieces = nbPieces;
			this.priority = priority;
			this.skipped = skipped;
		}
	}

	public static final class NewTorrentDecision
	{
		public final int policy;
		public final int torrentDefaultMode;
		public final int initialFileIndex;
		public final int initialFileMode;

		public NewTorrentDecision(
			int		policy,
			int		torrentDefaultMode,
			int		initialFileIndex,
			int		initialFileMode )
		{
			this.policy = policy;
			this.torrentDefaultMode = torrentDefaultMode;
			this.initialFileIndex = initialFileIndex;
			this.initialFileMode = initialFileMode;
		}

		public boolean
		isNoOp()
		{
			return policy == POLICY_UNSET;
		}
	}

	public static int
	resolvePolicy(
		boolean		enabled,
		int			storedPolicy )
	{
		if ( storedPolicy == POLICY_AUTO || storedPolicy == POLICY_MANUAL || storedPolicy == POLICY_OFF ){
			return storedPolicy;
		}
		if ( enabled ){
			return POLICY_MANUAL;
		}
		return POLICY_UNSET;
	}

	public static boolean
	isEligible(
		FileSnap		file )
	{
		return file != null
			&& !file.skipped
			&& file.length > 0
			&& file.nbPieces > 0
			&& file.firstPiece >= 0
			&& file.lastPiece >= file.firstPiece;
	}

	public static boolean
	isFileFinished(
		FileSnap										file,
		DownloadControlStrictOrder.HashVerifiedPieces	verified )
	{
		if ( !isEligible( file )){
			return true;
		}
		if ( verified == null ){
			return false;
		}
		for (int p = file.firstPiece; p <= file.lastPiece; p++){
			if ( !verified.isHashVerified( p )){
				return false;
			}
		}
		return true;
	}

	public static List<FileSnap>
	queueOrder(
		FileSnap[]		files )
	{
		List<FileSnap> out = new ArrayList<>();
		if ( files == null ){
			return out;
		}
		for ( FileSnap file: files ){
			if ( isEligible( file )){
				out.add( file );
			}
		}
		Collections.sort( out, new Comparator<FileSnap>()
		{
			@Override
			public int compare( FileSnap a, FileSnap b )
			{
				if ( a.priority != b.priority ){
					return Integer.compare( b.priority, a.priority );
				}
				return Integer.compare( a.index, b.index );
			}
		});
		return out;
	}

	public static FileSnap
	fileAt(
		FileSnap[]		files,
		int				index )
	{
		if ( files == null || index < 0 ){
			return null;
		}
		if ( index < files.length && files[index] != null && files[index].index == index ){
			return files[index];
		}
		for ( FileSnap file: files ){
			if ( file != null && file.index == index ){
				return file;
			}
		}
		return null;
	}

	/**
	 * Keep the pinned file while it is still unfinished and eligible.
	 * Otherwise pick the first unfinished file in priority-then-index order.
	 */
	public static int
	pickPinnedOrNext(
		FileSnap[]										files,
		int												pinnedIndex,
		DownloadControlStrictOrder.HashVerifiedPieces	verified )
	{
		FileSnap pinned = fileAt( files, pinnedIndex );
		if ( isEligible( pinned ) && !isFileFinished( pinned, verified )){
			return pinned.index;
		}
		for ( FileSnap file: queueOrder( files )){
			if ( !isFileFinished( file, verified )){
				return file.index;
			}
		}
		return -1;
	}

	public static int
	effectiveFileMode(
		int		torrentDefaultMode,
		int		fileOverrideMode )
	{
		if ( fileOverrideMode == DownloadControlStrictOrder.MODE_SEQUENTIAL
			|| fileOverrideMode == DownloadControlStrictOrder.MODE_RANDOM ){
			return fileOverrideMode;
		}
		if ( torrentDefaultMode == DownloadControlStrictOrder.MODE_SEQUENTIAL ){
			return DownloadControlStrictOrder.MODE_SEQUENTIAL;
		}
		return DownloadControlStrictOrder.MODE_RANDOM;
	}

	public static String
	modeLetterForFile(
		int			policy,
		boolean		enabled,
		int			storedMode,
		int			selectedFileIndex,
		int			fileIndex,
		int			fileOverrideMode,
		boolean		blankFile )
	{
		int resolved = resolvePolicy( enabled, policy );
		if ( resolved == POLICY_OFF || !enabled || blankFile || fileIndex < 0 ){
			return "";
		}
		if ( resolved == POLICY_AUTO ){
			return DownloadControlStrictOrder.modeLetter(
				effectiveFileMode( storedMode, fileOverrideMode ));
		}
		return DownloadControlStrictOrder.modeLetterForFile(
			enabled, storedMode, selectedFileIndex, fileIndex );
	}

	/**
	 * Add-dialog letter for a pending file: S/R from a stored override,
	 * R for every unskipped nonempty file when auto queue is on (default
	 * Random), blank when Off, skipped, or empty. Mirrors
	 * {@link #modeLetterForFile} for not-yet-added torrents.
	 */
	public static String
	modeLetterForPendingFile(
		boolean		explicitOff,
		boolean		autoQueue,
		int			fileOverrideMode,
		boolean		skippedOrEmpty )
	{
		if ( explicitOff || skippedOrEmpty ){
			return "";
		}
		if ( fileOverrideMode == DownloadControlStrictOrder.MODE_SEQUENTIAL ){
			return "S";
		}
		if ( fileOverrideMode == DownloadControlStrictOrder.MODE_RANDOM ){
			return "R";
		}
		if ( autoQueue ){
			return DownloadControlStrictOrder.modeLetter( DownloadControlStrictOrder.MODE_RANDOM );
		}
		return "";
	}

	public static Map<Integer, Integer>
	parseFileModes(
		String[]		stored )
	{
		Map<Integer, Integer> out = new HashMap<>();
		if ( stored == null ){
			return out;
		}
		for ( String raw: stored ){
			if ( raw == null ){
				continue;
			}
			int eq = raw.indexOf( '=' );
			if ( eq <= 0 || eq == raw.length() - 1 ){
				continue;
			}
			try{
				int index = Integer.parseInt( raw.substring( 0, eq ).trim());
				int mode = Integer.parseInt( raw.substring( eq + 1 ).trim());
				if ( index >= 0
					&& ( mode == DownloadControlStrictOrder.MODE_SEQUENTIAL
						|| mode == DownloadControlStrictOrder.MODE_RANDOM )){
					out.put( Integer.valueOf( index ), Integer.valueOf( mode ));
				}
			}catch( NumberFormatException ignore ){
			}
		}
		return out;
	}

	public static String[]
	serializeFileModes(
		Map<Integer, Integer>		modes )
	{
		if ( modes == null || modes.isEmpty()){
			return new String[0];
		}
		Integer[] keys = modes.keySet().toArray( new Integer[0] );
		Arrays.sort( keys );
		List<String> out = new ArrayList<>( keys.length );
		for ( Integer key: keys ){
			Integer mode = modes.get( key );
			if ( key == null || mode == null ){
				continue;
			}
			if ( mode.intValue() == DownloadControlStrictOrder.MODE_SEQUENTIAL
				|| mode.intValue() == DownloadControlStrictOrder.MODE_RANDOM ){
				out.add( key.intValue() + "=" + mode.intValue());
			}
		}
		return out.toArray( new String[0] );
	}

	public static int
	fileModeOverride(
		Map<Integer, Integer>		modes,
		int							fileIndex )
	{
		if ( modes == null ){
			return DownloadControlStrictOrder.MODE_OFF;
		}
		Integer mode = modes.get( Integer.valueOf( fileIndex ));
		return mode == null ? DownloadControlStrictOrder.MODE_OFF : mode.intValue();
	}

	public static NewTorrentDecision
	planNewTorrent(
		FileSnap[]										files,
		boolean											profile,
		boolean											metadata,
		boolean											explicitOff,
		boolean											autoQueue,
		Map<Integer, Integer>							fileModes,
		DownloadControlStrictOrder.HashVerifiedPieces	verified )
	{
		if ( metadata ){
			return new NewTorrentDecision(
				POLICY_UNSET,
				DownloadControlStrictOrder.MODE_OFF,
				-1,
				DownloadControlStrictOrder.MODE_OFF );
		}
		if ( explicitOff ){
			return new NewTorrentDecision(
				POLICY_OFF,
				DownloadControlStrictOrder.MODE_OFF,
				-1,
				DownloadControlStrictOrder.MODE_OFF );
		}
		if ( !profile ){
			return planManualOverrides( files, fileModes );
		}
		if ( autoQueue ){
			int pick = pickPinnedOrNext( files, -1, verified );
			int mode = pick < 0
				? DownloadControlStrictOrder.MODE_RANDOM
				: effectiveFileMode(
					DownloadControlStrictOrder.MODE_RANDOM,
					fileModeOverride( fileModes, pick ));
			return new NewTorrentDecision(
				POLICY_AUTO,
				DownloadControlStrictOrder.MODE_RANDOM,
				pick,
				mode );
		}
		return planManualOverrides( files, fileModes );
	}

	private static NewTorrentDecision
	planManualOverrides(
		FileSnap[]					files,
		Map<Integer, Integer>		fileModes )
	{
		if ( files == null || fileModes == null || fileModes.isEmpty()){
			return new NewTorrentDecision(
				POLICY_UNSET,
				DownloadControlStrictOrder.MODE_OFF,
				-1,
				DownloadControlStrictOrder.MODE_OFF );
		}
		FileSnap[] byIndex = files.clone();
		Arrays.sort( byIndex, new Comparator<FileSnap>()
		{
			@Override
			public int compare( FileSnap a, FileSnap b )
			{
				return Integer.compare( a.index, b.index );
			}
		});
		for ( FileSnap file: byIndex ){
			int mode = fileModeOverride( fileModes, file.index );
			if (( mode == DownloadControlStrictOrder.MODE_SEQUENTIAL
				|| mode == DownloadControlStrictOrder.MODE_RANDOM )
				&& isEligible( file )){
				return new NewTorrentDecision( POLICY_MANUAL, mode, file.index, mode );
			}
		}
		return new NewTorrentDecision(
			POLICY_UNSET,
			DownloadControlStrictOrder.MODE_OFF,
			-1,
			DownloadControlStrictOrder.MODE_OFF );
	}
}
