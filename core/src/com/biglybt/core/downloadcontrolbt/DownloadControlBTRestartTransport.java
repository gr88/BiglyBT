/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package com.biglybt.core.downloadcontrolbt;

import java.io.File;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;

/**
 * Isolated restart spawn: detaches the child from the dying parent's pipes
 * (so launcher banners cannot SIGPIPE) and waits for the old JVM pid to
 * exit instead of sleeping a fixed two seconds. No live BiglyBT Main.
 */
public final class DownloadControlBTRestartTransport
{
	public static final int PARENT_WAIT_STEPS = 50;
	public static final String PARENT_WAIT_SLEEP = "0.2";

	private DownloadControlBTRestartTransport()
	{
	}

	/**
	 * Waiter snippet executed by {@code bash -c}. {@code $1} is the parent
	 * pid (may be empty), {@code $2} is the launcher script to exec.
	 */
	public static String waiterScript()
	{
		return "oldpid=\"$1\"; script=\"$2\"; "
			+ "i=0; while [ \"$i\" -lt " + PARENT_WAIT_STEPS + " ]; do "
			+ "if [ -n \"$oldpid\" ] && kill -0 \"$oldpid\" 2>/dev/null; then "
			+ "i=$((i+1)); sleep " + PARENT_WAIT_SLEEP + "; "
			+ "else break; fi; "
			+ "done; "
			+ "exec \"$script\"";
	}

	public static ProcessBuilder buildLauncher(
		File		script,
		File		childLog,
		long		parentPid )
	{
		if ( script == null ){
			throw new IllegalArgumentException( "script" );
		}
		if ( childLog == null ){
			throw new IllegalArgumentException( "childLog" );
		}
		File logParent = childLog.getParentFile();
		if ( logParent != null ){
			logParent.mkdirs();
		}
		ProcessBuilder pb = new ProcessBuilder(
			"bash", "-c", waiterScript(),
			"dlcbt-restart",
			parentPid > 0 ? Long.toString( parentPid ) : "",
			script.getAbsolutePath());
		if ( script.getParentFile() != null ){
			pb.directory( script.getParentFile());
		}
		pb.redirectInput( Redirect.from( new File( "/dev/null" )));
		pb.redirectOutput( Redirect.appendTo( childLog ));
		pb.redirectErrorStream( true );
		return pb;
	}

	public static Process startLauncher(
		File		script,
		File		childLog,
		long		parentPid )
		throws IOException
	{
		return buildLauncher( script, childLog, parentPid ).start();
	}
}
