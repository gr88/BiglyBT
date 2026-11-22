/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.core.downloadcontrolbt;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.peer.impl.PEPeerTransport;

/**
 * DLCBT Nov 20: after Fully Established, a peer with no data in either
 * direction for more than about 5 seconds gets a 54-64 minute temporary ban
 * (reason idle-peer). Applies while downloading and seeding.
 *
 * Peers we have just refused (rarest-only) are not idle-banned; they are
 * disconnected without a ban if they keep asking only for non-rarest pieces.
 */
public final class DownloadControlBTIdleBan
{
	public static final String REASON = "idle-peer";
	public static final long IDLE_MS = 5 * 1000L;
	public static final String CFG_ENABLED = "dlcbt.ban.idle.enabled";
	public static final String CFG_IDLE_SECONDS = "dlcbt.ban.idle.seconds";
	public static final int DEFAULT_IDLE_SECONDS = 5;

	private DownloadControlBTIdleBan(){}

	public static boolean isEnabled()
	{
		try{
			return DownloadControlBTBanService.get().isPolicyEnabled()
				&& COConfigurationManager.getBooleanParameter( CFG_ENABLED, true );
		}catch( Throwable t ){
			return false;
		}
	}

	public static long idleMs()
	{
		try{
			int sec = COConfigurationManager.getIntParameter( CFG_IDLE_SECONDS, DEFAULT_IDLE_SECONDS );
			return Math.max( 1, sec ) * 1000L;
		}catch( Throwable t ){
			return IDLE_MS;
		}
	}

	/**
	 * @param establishedMono when the peer reached Fully Established, or &lt;=0 if not yet
	 * @param lastDataMono last time any payload was received from or sent to the peer
	 *                     (use establishedMono if no payload yet)
	 * @param rarestRejectRecent true if a rarest-only reject happened in this idle window
	 */
	public static boolean shouldBan(
		boolean		policyEnabled,
		int			connectionState,
		long		establishedMono,
		long		lastDataMono,
		long		nowMono,
		boolean		rarestRejectRecent )
	{
		return shouldBan(
			policyEnabled, connectionState, establishedMono, lastDataMono,
			nowMono, rarestRejectRecent, IDLE_MS );
	}

	public static boolean shouldBan(
		boolean		policyEnabled,
		int			connectionState,
		long		establishedMono,
		long		lastDataMono,
		long		nowMono,
		boolean		rarestRejectRecent,
		long		idleMs )
	{
		if ( !policyEnabled || rarestRejectRecent || idleMs <= 0 ){
			return false;
		}
		if ( connectionState != PEPeerTransport.CONNECTION_FULLY_ESTABLISHED ){
			return false;
		}
		if ( establishedMono <= 0 ){
			return false;
		}
		long mark = lastDataMono > 0 ? lastDataMono : establishedMono;
		if ( nowMono - establishedMono < idleMs ){
			return false;
		}
		return nowMono - mark >= idleMs;
	}
}
