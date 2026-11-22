/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.core.downloadcontrolbt;

import com.biglybt.core.config.COConfigurationManager;

/**
 * DLCBT Nov 18: kick (disconnect, never ban) outgoing peers that stay in
 * "Connecting" for about 30 s.
 */
public final class DownloadControlBTConnectTimeout
{
	public static final long KICK_MS = 30 * 1000L;
	public static final String CFG_ENABLED = "dlcbt.kick.connecting.enabled";
	public static final String CFG_KICK_SECONDS = "dlcbt.kick.connecting.seconds";
	public static final int DEFAULT_KICK_SECONDS = 30;

	private DownloadControlBTConnectTimeout(){}

	public static boolean isEnabled()
	{
		try{
			return DownloadControlBT.isProfileEnabled()
				&& COConfigurationManager.getBooleanParameter( CFG_ENABLED, true );
		}catch( Throwable t ){
			return false;
		}
	}

	public static long kickMs()
	{
		try{
			int sec = COConfigurationManager.getIntParameter( CFG_KICK_SECONDS, DEFAULT_KICK_SECONDS );
			return Math.max( 1, sec ) * 1000L;
		}catch( Throwable t ){
			return KICK_MS;
		}
	}

	public static boolean shouldKick(
		boolean		profileEnabled,
		boolean		incoming,
		boolean		connecting,
		long		connectStartMono,
		long		nowMono )
	{
		return shouldKick( profileEnabled, incoming, connecting, connectStartMono, nowMono, KICK_MS );
	}

	public static boolean shouldKick(
		boolean		profileEnabled,
		boolean		incoming,
		boolean		connecting,
		long		connectStartMono,
		long		nowMono,
		long		kickMs )
	{
		if ( !profileEnabled || incoming || !connecting || connectStartMono <= 0 || kickMs <= 0 ){
			return false;
		}
		return nowMono - connectStartMono >= kickMs;
	}
}
