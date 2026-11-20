/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.core.downloadcontrolbt;

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

	private DownloadControlBTIdleBan(){}

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
		if ( !policyEnabled || rarestRejectRecent ){
			return false;
		}
		if ( connectionState != PEPeerTransport.CONNECTION_FULLY_ESTABLISHED ){
			return false;
		}
		if ( establishedMono <= 0 ){
			return false;
		}
		long mark = lastDataMono > 0 ? lastDataMono : establishedMono;
		if ( nowMono - establishedMono < IDLE_MS ){
			return false;
		}
		return nowMono - mark >= IDLE_MS;
	}
}
