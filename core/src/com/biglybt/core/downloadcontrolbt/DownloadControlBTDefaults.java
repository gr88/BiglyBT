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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Isolated-profile defaults and parsers that do not require a live
 * DownloadManager. Regular BiglyBT config is unaffected unless the
 * isolated profile flag is turned on.
 */
public final class DownloadControlBTDefaults
{
	public static final String CFG_PROFILE = "dlcbt.profile.enabled";
	public static final String CFG_AUTO_QUEUE_DEFAULT = "dlcbt.auto.queue.default";
	public static final String CFG_DEFAULT_FORCE_START = "dlcbt.default.force.start";
	public static final String CFG_ALWAYS_SHOW_FORCE_START = "Always Show Force Start";
	public static final String CFG_SCHEDULE_LINES = "speed.limit.handler.schedule.lines";
	public static final String CFG_ADD_OPENOPTIONS = "ui.addtorrent.openoptions";
	public static final String CFG_ADD_OPENOPTIONS_ALWAYS = "always";
	public static final String CFG_DEFAULT_STOPPED = "Default Start Torrents Stopped";

	public static final String SCHEDULE_UP = "peer_set UpPeers=All,peer_up=25.1k";
	public static final String SCHEDULE_DOWN = "peer_set DownPeers=All,peer_down=251.1k";

	public static final String COL_ORDER = "#";
	public static final String COL_PLAY_NOW = "TorrentStream";
	public static final String COL_SUBSCRIBE = "azsubs.ui.column.subs";
	public static final String COL_HEALTH = "health";
	public static final String COL_COMMENT_ICON = "commenticon";
	public static final String COL_SEEDING_RANK = "SeedingRank";

	public static final int STARTMODE_QUEUED = 0;
	public static final int STARTMODE_STOPPED = 1;
	public static final int STARTMODE_FORCESTARTED = 3;

	private static final Set<String> HIDDEN_LIBRARY_COLUMNS = new HashSet<>(Arrays.asList(
		COL_ORDER,
		COL_PLAY_NOW,
		COL_SUBSCRIBE,
		COL_HEALTH,
		COL_COMMENT_ICON,
		COL_SEEDING_RANK
	));

	private DownloadControlBTDefaults()
	{
	}

	public static boolean
	isHiddenDefaultLibraryColumn(
		String		id )
	{
		return id != null && HIDDEN_LIBRARY_COLUMNS.contains( id );
	}

	public static String[]
	applyHiddenDefaultLibraryColumns(
		String[]		defaults )
	{
		if ( defaults == null ){
			return new String[0];
		}
		List<String> out = new ArrayList<>( defaults.length );
		for ( String id: defaults ){
			if ( !isHiddenDefaultLibraryColumn( id )){
				out.add( id );
			}
		}
		return out.toArray( new String[0] );
	}

	public static boolean
	shouldSeedSchedule(
		List<?>		existing )
	{
		if ( existing == null || existing.isEmpty()){
			return true;
		}
		for ( Object raw: existing ){
			if ( raw == null ){
				continue;
			}
			String line;
			if ( raw instanceof byte[] ){
				line = new String((byte[])raw, StandardCharsets.UTF_8 );
			}else{
				line = String.valueOf( raw );
			}
			if ( line.trim().length() > 0 ){
				return false;
			}
		}
		return true;
	}

	public static List<String>
	defaultScheduleLines()
	{
		return Arrays.asList( SCHEDULE_UP, SCHEDULE_DOWN );
	}

	/**
	 * Isolated add-mode default: Force Started, unless the user explicitly
	 * chose "start torrents stopped". Does not change the meaning of Force
	 * Start itself.
	 */
	public static int
	resolveDefaultStartMode(
		boolean		startStopped,
		boolean		forceStartDefault )
	{
		if ( startStopped ){
			return STARTMODE_STOPPED;
		}
		if ( forceStartDefault ){
			return STARTMODE_FORCESTARTED;
		}
		return STARTMODE_QUEUED;
	}
}
