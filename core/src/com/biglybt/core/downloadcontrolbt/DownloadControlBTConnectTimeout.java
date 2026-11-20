/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.core.downloadcontrolbt;

/**
 * DLCBT Nov 18: kick (disconnect, never ban) outgoing peers that stay in
 * "Connecting" for about 30 s.
 *
 * Why upstream peers sit in Connecting longer than TCP's 15 s
 * CONNECT_ATTEMPT_TIMEOUT: that timer only covers the raw socket connect
 * once the request leaves the TCPConnectionManager queue. The peer stays
 * CONNECTION_CONNECTING until connectSuccess, i.e. also through the
 * MSE/PHE crypto negotiation (and a plain-text fallback reconnect when
 * crypto is refused), the uTP/UDP path (connect timeout 20 s, handled
 * outside TCP, "timeouts handled separately"), the TCP+uTP dual attempt,
 * hole-punch / SOCKS / I2P / Tor proxied connects. The only catch-all
 * upstream is a 2 minute safety net in PEPeerTransportProtocol
 * .doTimeoutChecks. DLCBT measures from the first connectStarted
 * (outbound_connection_start) and kicks at 30 s; checks run on the
 * peer-manager five-second cadence, so the kick lands at 30..35 s.
 */
public final class DownloadControlBTConnectTimeout
{
	public static final long KICK_MS = 30 * 1000L;

	private DownloadControlBTConnectTimeout(){}

	public static boolean shouldKick(
		boolean		profileEnabled,
		boolean		incoming,
		boolean		connecting,
		long		connectStartMono,
		long		nowMono )
	{
		if ( !profileEnabled || incoming || !connecting || connectStartMono <= 0 ){
			return false;
		}
		return nowMono - connectStartMono >= KICK_MS;
	}
}
