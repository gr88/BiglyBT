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

import java.io.File;

import com.biglybt.core.Core;
import com.biglybt.core.CoreException;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;
import com.biglybt.core.update.ClientRestarter;

/**
 * Package-owned restart: re-executes the isolated start-dlcbt.sh rather than
 * launching {@code com.biglybt.ui.Main} or the official updater.
 */
public class DownloadControlBTRestarter
	implements ClientRestarter
{
	private static final LogIDs LOGID = LogIDs.CORE;

	public DownloadControlBTRestarter(Core core)
	{
	}

	@Override
	public void
	restart(
		boolean		update_only )
	{
		if ( update_only ){
			if ( Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID,
					"DownLoadControlBT: isolated restarter ignores update-only restart"));
			}
			return;
		}

		String cmd = System.getProperty( "azureus.restart.cmd" );
		if ( cmd == null || cmd.length() == 0 ){
			if ( Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID,
					"DownLoadControlBT: azureus.restart.cmd is not set; not restarting"));
			}
			return;
		}

		File script = new File( cmd );
		if ( !script.isFile() || !script.canExecute()){
			if ( Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID,
					"DownLoadControlBT: restart script is missing or not executable: " + cmd));
			}
			return;
		}

		try{
			ProcessBuilder pb = new ProcessBuilder(
				"bash", "-c", "sleep 2; exec \"$1\"",
				"dlcbt-restart",
				script.getAbsolutePath());
			if ( script.getParentFile() != null ){
				pb.directory( script.getParentFile());
			}
			pb.redirectErrorStream( true );
			pb.start();
			if ( Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID,
					"DownLoadControlBT: spawned isolated restart via " + script.getAbsolutePath()));
			}
		}catch( Throwable t ){
			if ( Logger.isEnabled()){
				Logger.log(new LogEvent(LOGID,
					"DownLoadControlBT: failed to spawn isolated restart: " + t));
			}
		}
	}

	@Override
	public void
	updateNow()
		throws CoreException
	{
		throw( new CoreException( "isolated DownLoadControlBT package does not apply updates" ));
	}
}
