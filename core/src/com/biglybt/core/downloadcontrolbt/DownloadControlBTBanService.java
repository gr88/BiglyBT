/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.logging.LogEvent;
import com.biglybt.core.logging.LogIDs;
import com.biglybt.core.logging.Logger;

/**
 * Profile-gated facade used by connection hooks. Keeps temporary DLCBT bans
 * out of {@code IPBannerImpl} so they cannot persist as bad-data bans or
 * trigger the upstream IPv4 /24 cascade.
 */
public final class DownloadControlBTBanService
{
	public static final String REASON_SPEED_LOW = "speed-low";
	public static final String REASON_SPEED_HIGH = "speed-high";
	public static final String REASON_DUPLICATE = "duplicate-incoming";

	public static final String CFG_ENABLED = "dlcbt.ban.enabled";
	public static final String CFG_MIN_MINUTES = "dlcbt.ban.duration.min.minutes";
	public static final String CFG_MAX_MINUTES = "dlcbt.ban.duration.max.minutes";
	public static final String CFG_MIN_RATE = "dlcbt.ban.speed.min";
	public static final String CFG_MAX_RATE = "dlcbt.ban.speed.max";
	public static final String CFG_RAMP_SECONDS = "dlcbt.ban.ramp.seconds";
	public static final String CFG_WINDOW_SECONDS = "dlcbt.ban.window.seconds";
	public static final String CFG_DUP_WINDOW_SECONDS = "dlcbt.ban.duplicate.window.seconds";
	public static final String CFG_DUP_THRESHOLD = "dlcbt.ban.duplicate.threshold";

	public static final int DEFAULT_MIN_MINUTES = 54;
	public static final int DEFAULT_MAX_MINUTES = 64;
	public static final int DEFAULT_RAMP_SECONDS = 60;
	public static final int DEFAULT_WINDOW_SECONDS = 30;
	public static final int DEFAULT_DUP_WINDOW_SECONDS = 60;
	public static final int DEFAULT_DUP_THRESHOLD = 3;

	private static final DownloadControlBTBanService	instance = new DownloadControlBTBanService();

	public interface BanListener
	{
		/** Close any connected peer whose canonical address equals {@code canonical}. */
		public void
		dlcbtTemporaryBan(
			DownloadControlBTBanRegistry.Ban	ban );
	}

	private final DownloadControlBTBanRegistry			registry = new DownloadControlBTBanRegistry();
	private DownloadControlBTDuplicateTracker			duplicates;
	private final Random	random = new Random();
	private final List<BanListener>	listeners = new CopyOnWriteArrayList<>();

	public void
	addListener(
		BanListener		l )
	{
		listeners.add( l );
	}

	public void
	removeListener(
		BanListener		l )
	{
		listeners.remove( l );
	}

	public static DownloadControlBTBanService
	get()
	{
		return instance;
	}

	public boolean
	isPolicyEnabled()
	{
		try{
			return DownloadControlBT.isProfileEnabled()
				&& COConfigurationManager.getBooleanParameter( CFG_ENABLED, true );
		}catch( Throwable t ){
			return false;
		}
	}

	public boolean
	isTemporarilyBanned(
		String		address )
	{
		if ( !isPolicyEnabled()){
			return false;
		}
		synchronized( this ){
			return registry.isBanned( address, System.currentTimeMillis());
		}
	}

	public DownloadControlBTBanRegistry
	registry()
	{
		return registry;
	}

	public DownloadControlBTBanRegistry.Ban
	onRejectedIncomingDuplicate(
		String		torrentId,
		String		address,
		boolean		incoming,
		boolean		hadLiveSameAddressOnTorrent )
	{
		if ( !isPolicyEnabled()){
			return null;
		}
		long now = System.currentTimeMillis();
		DownloadControlBTBanRegistry.Ban ban;
		synchronized( this ){
			if ( duplicates == null ){
				duplicates = new DownloadControlBTDuplicateTracker(
					Math.max( 1, intParam( CFG_DUP_THRESHOLD, DEFAULT_DUP_THRESHOLD )),
					Math.max( 1, intParam( CFG_DUP_WINDOW_SECONDS, DEFAULT_DUP_WINDOW_SECONDS )) * 1000L );
			}
			DownloadControlBTDuplicateTracker.Decision decision = duplicates.onRejectedIncomingDuplicate(
				torrentId, address, now, incoming, hadLiveSameAddressOnTorrent );
			if ( !decision.shouldBan ){
				return null;
			}
			ban = applyBanLocked( address, now, REASON_DUPLICATE,
				"rejectedIncomingDuplicates=" + decision.countInWindow +
				" withinSeconds=" + intParam( CFG_DUP_WINDOW_SECONDS, DEFAULT_DUP_WINDOW_SECONDS ) +
				" torrent=" + safeId( torrentId ));
		}
		fire( ban );
		return ban;
	}

	/**
	 * @param sampler per-peer state owned by the caller (one per connection)
	 * @param dataBytesDelta incoming payload bytes since the previous sample (no protocol overhead)
	 * @param activelySendingEligible unchoked, we have outstanding requests to it,
	 *        and not waiting on the DLCBT piece gate or disk verification
	 */
	public DownloadControlBTBanRegistry.Ban
	onDataSample(
		DownloadControlBTSpeedSampler	sampler,
		String							address,
		long							dataBytesDelta,
		boolean							activelySendingEligible,
		String							detail )
	{
		if ( sampler == null || !isPolicyEnabled()){
			return null;
		}
		long now = System.currentTimeMillis();
		DownloadControlBTBanRegistry.Ban ban = null;
		synchronized( this ){
			if ( registry.isBanned( address, now )){
				return registry.get( address, now );
			}
			long min = minBps();
			long max = maxBps();
			int result = sampler.sample(
				now, dataBytesDelta, activelySendingEligible,
				min, max, rampMs(), windowMs());
			if ( result == DownloadControlBTSpeedSampler.BELOW_MIN ){
				ban = applyBanLocked( address, now, REASON_SPEED_LOW,
					"minBps=" + min + " windowSeconds=" + ( windowMs() / 1000 ) + " " + detail );
			}else if ( result == DownloadControlBTSpeedSampler.ABOVE_MAX ){
				ban = applyBanLocked( address, now, REASON_SPEED_HIGH,
					"maxBps=" + max + " windowSeconds=" + ( windowMs() / 1000 ) + " " + detail );
			}
		}
		if ( ban != null ){
			sampler.reset();
			fire( ban );
		}
		return ban;
	}

	/** Called only for the same connection state displayed as Waiting for Handshake. */
	public DownloadControlBTBanRegistry.Ban onHandshakeTimeout(String address, int state, long establishedMono, long nowMono) {
		if (!isPolicyEnabled() || !handshakeOverdue(state, establishedMono, nowMono)) return null;
		DownloadControlBTBanRegistry.Ban ban;
		synchronized (this) {
			if (registry.isBanned(address, System.currentTimeMillis())) return null;
			ban = applyBanLocked(address, System.currentTimeMillis(), "handshake-timeout", "waitingForHandshakeMs=" + (nowMono-establishedMono));
		}
		fire(ban);
		return ban;
	}

	public static boolean handshakeOverdue(int state, long establishedMono, long nowMono) {
		return state == com.biglybt.core.peer.impl.PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE
			&& nowMono - establishedMono > 3000;
	}

	public synchronized int
	unbanOwn(
		String		address )
	{
		return registry.unban( address ) ? 1 : 0;
	}

	public synchronized int
	unbanAllOwn()
	{
		return registry.unbanAll();
	}

	private void
	fire(
		DownloadControlBTBanRegistry.Ban	ban )
	{
		if ( ban == null ){
			return;
		}
		for ( BanListener l: listeners ){
			try{
				l.dlcbtTemporaryBan( ban );
			}catch( Throwable t ){
				// a listener failure must not stop other torrents closing the peer
			}
		}
	}

	DownloadControlBTBanRegistry.Ban
	applyBanLocked(
		String		address,
		long		nowMs,
		String		reason,
		String		detail )
	{
		long duration = DownloadControlBTBanRegistry.randomDurationMs(
			minMinutes(), maxMinutes(), random );
		DownloadControlBTBanRegistry.Ban ban = registry.ban( address, nowMs, duration, reason, detail );
		if ( ban != null ){
			logBan( ban );
		}
		return ban;
	}

	private static void
	logBan(
		DownloadControlBTBanRegistry.Ban	ban )
	{
		String msg = "DLCBT temporary ban reason=" + ban.reason +
			" addr=" + ban.canonical +
			" startMs=" + ban.startMs +
			" expiryMs=" + ban.expiryMs +
			" " + ( ban.detail == null ? "" : ban.detail );
		try{
			if ( Logger.isEnabled()){
				Logger.log( new LogEvent( LogIDs.CORE, LogEvent.LT_INFORMATION, msg ));
			}
		}catch( Throwable t ){
			// tests or early init
		}
	}

	private static String
	safeId(
		String		torrentId )
	{
		if ( torrentId == null ){
			return "-";
		}
		if ( torrentId.length() <= 16 ){
			return torrentId;
		}
		return torrentId.substring( 0, 16 );
	}

	private int
	minMinutes()
	{
		return intParam( CFG_MIN_MINUTES, DEFAULT_MIN_MINUTES );
	}

	private int
	maxMinutes()
	{
		int max = intParam( CFG_MAX_MINUTES, DEFAULT_MAX_MINUTES );
		int min = minMinutes();
		return Math.max( max, min );
	}

	private long
	rampMs()
	{
		return intParam( CFG_RAMP_SECONDS, DEFAULT_RAMP_SECONDS ) * 1000L;
	}

	private long
	windowMs()
	{
		return intParam( CFG_WINDOW_SECONDS, DEFAULT_WINDOW_SECONDS ) * 1000L;
	}

	private long
	minBps()
	{
		try{
			return DownloadControlBTRateFormat.parseRate(
				COConfigurationManager.getStringParameter( CFG_MIN_RATE, DownloadControlBTRateFormat.DEFAULT_MIN ));
		}catch( Throwable t ){
			return DownloadControlBTRateFormat.parseRate( DownloadControlBTRateFormat.DEFAULT_MIN );
		}
	}

	/**
	 * Explicit {@code dlcbt.ban.speed.max} wins; blank means "use the
	 * per-peer peer_down limit from the speed schedule" (251.1k by default).
	 */
	private long
	maxBps()
	{
		try{
			String explicit = COConfigurationManager.getStringParameter( CFG_MAX_RATE, "" );
			if ( explicit != null && explicit.trim().length() > 0 ){
				return DownloadControlBTRateFormat.parseRate( explicit );
			}
			String token = DownloadControlBTRateFormat.peerDownToken(
				COConfigurationManager.getListParameter(
					DownloadControlBTDefaults.CFG_SCHEDULE_LINES, new java.util.ArrayList()));
			if ( token != null ){
				return DownloadControlBTRateFormat.parseRate( token );
			}
		}catch( Throwable t ){
		}
		return DownloadControlBTRateFormat.parseRate( DownloadControlBTRateFormat.DEFAULT_MAX );
	}

	private static int
	intParam(
		String		key,
		int			fallback )
	{
		try{
			return COConfigurationManager.getIntParameter( key, fallback );
		}catch( Throwable t ){
			return fallback;
		}
	}
}
