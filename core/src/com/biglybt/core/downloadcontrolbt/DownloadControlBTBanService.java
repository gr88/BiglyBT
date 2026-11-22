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
	public static final String REASON_HASH_FAIL = "hash-fail";
	public static final String REASON_IDLE = DownloadControlBTIdleBan.REASON;
	/** Hash-fail ban: random 3.5 .. 4.5 minutes, separate from the 54-64 min policy. */
	public static final long HASH_FAIL_MIN_MS = 210 * 1000L;
	public static final long HASH_FAIL_MAX_MS = 270 * 1000L;

	public static final String CFG_ENABLED = "dlcbt.ban.enabled";
	public static final String CFG_MIN_MINUTES = "dlcbt.ban.duration.min.minutes";
	public static final String CFG_MAX_MINUTES = "dlcbt.ban.duration.max.minutes";
	public static final String CFG_MIN_RATE = "dlcbt.ban.speed.min";
	public static final String CFG_MAX_RATE = "dlcbt.ban.speed.max";
	public static final String CFG_RAMP_SECONDS = "dlcbt.ban.ramp.seconds";
	public static final String CFG_WINDOW_SECONDS = "dlcbt.ban.window.seconds";
	public static final String CFG_DUP_WINDOW_SECONDS = "dlcbt.ban.duplicate.window.seconds";
	public static final String CFG_DUP_THRESHOLD = "dlcbt.ban.duplicate.threshold";
	public static final String CFG_SPEED_ENABLED = "dlcbt.ban.speed.enabled";
	public static final String CFG_HANDSHAKE_ENABLED = "dlcbt.ban.handshake.enabled";
	public static final String CFG_HANDSHAKE_SECONDS = "dlcbt.ban.handshake.seconds";
	public static final String CFG_DUP_ENABLED = "dlcbt.ban.duplicate.enabled";
	public static final String CFG_HASH_FAIL_ENABLED = "dlcbt.ban.hashfail.enabled";
	public static final String CFG_HASH_FAIL_MIN_SECONDS = "dlcbt.ban.hashfail.min.seconds";
	public static final String CFG_HASH_FAIL_MAX_SECONDS = "dlcbt.ban.hashfail.max.seconds";

	public static final int DEFAULT_MIN_MINUTES = 54;
	public static final int DEFAULT_MAX_MINUTES = 64;
	public static final int DEFAULT_RAMP_SECONDS = 60;
	public static final int DEFAULT_WINDOW_SECONDS = 30;
	public static final int DEFAULT_HANDSHAKE_SECONDS = 3;
	public static final int DEFAULT_DUP_WINDOW_SECONDS = 60;
	public static final int DEFAULT_DUP_THRESHOLD = 3;
	public static final int DEFAULT_HASH_FAIL_MIN_SECONDS = 210;
	public static final int DEFAULT_HASH_FAIL_MAX_SECONDS = 270;

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
	private int											lastDupThreshold = -1;
	private long										lastDupWindowMs = -1;
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

	public boolean isSpeedBanEnabled()
	{
		return isPolicyEnabled() && boolParam( CFG_SPEED_ENABLED, true );
	}

	public boolean isHandshakeBanEnabled()
	{
		return isPolicyEnabled() && boolParam( CFG_HANDSHAKE_ENABLED, true );
	}

	public boolean isDuplicateBanEnabled()
	{
		return isPolicyEnabled() && boolParam( CFG_DUP_ENABLED, true );
	}

	public boolean isHashFailBanEnabled()
	{
		return isPolicyEnabled() && boolParam( CFG_HASH_FAIL_ENABLED, true );
	}

	public boolean isIdleBanEnabled()
	{
		return isPolicyEnabled() && boolParam( DownloadControlBTIdleBan.CFG_ENABLED, true );
	}

	public DownloadControlBTBanRegistry.Ban
	onRejectedIncomingDuplicate(
		String		torrentId,
		String		address,
		boolean		incoming,
		boolean		hadLiveSameAddressOnTorrent )
	{
		if ( !isDuplicateBanEnabled()){
			return null;
		}
		long now = System.currentTimeMillis();
		DownloadControlBTBanRegistry.Ban ban;
		synchronized( this ){
			int thresh = Math.max( 1, intParam( CFG_DUP_THRESHOLD, DEFAULT_DUP_THRESHOLD ));
			long winMs = Math.max( 1, intParam( CFG_DUP_WINDOW_SECONDS, DEFAULT_DUP_WINDOW_SECONDS )) * 1000L;
			if ( duplicates == null || thresh != lastDupThreshold || winMs != lastDupWindowMs ){
				duplicates = new DownloadControlBTDuplicateTracker( thresh, winMs );
				lastDupThreshold = thresh;
				lastDupWindowMs = winMs;
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
		if ( sampler == null || !isSpeedBanEnabled()){
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
		if (!isHandshakeBanEnabled() || !handshakeOverdue(state, establishedMono, nowMono, handshakeMs())) return null;
		DownloadControlBTBanRegistry.Ban ban;
		synchronized(this) {
			if (registry.isBanned(address, System.currentTimeMillis())) return null;
			ban = applyBanLocked(address, System.currentTimeMillis(), "handshake-timeout", "waitingForHandshakeMs=" + (nowMono-establishedMono));
		}
		fire(ban);
		return ban;
	}

	/**
	 * DLCBT Nov 18: any hash fail attributed to a peer gives an exact-address,
	 * in-memory, all-torrent temporary ban of 3.5 .. 4.5 minutes. BiglyBT's own
	 * hash-fail warnings / IP filter ban and discard-ratio disconnect are
	 * untouched. Disabled with dlcbt.ban.enabled=false (or DLCBT profile off).
	 * An address already banned keeps its existing (possibly longer) ban.
	 */
	public DownloadControlBTBanRegistry.Ban onHashFail(String address, String detail) {
		if (!isHashFailBanEnabled() || address == null || address.isEmpty()) return null;
		DownloadControlBTBanRegistry.Ban ban;
		synchronized(this) {
			long now = System.currentTimeMillis();
			if (registry.isBanned(address, now)) return null;
			long minMs = hashFailMinSeconds() * 1000L;
			long maxMs = Math.max(minMs, hashFailMaxSeconds() * 1000L);
			long dur = DownloadControlBTBanRegistry.randomDurationRangeMs(minMs, maxMs, random);
			ban = registry.ban(address, now, dur, REASON_HASH_FAIL, detail);
			if (ban != null) logBan(ban);
		}
		fire(ban);
		return ban;
	}

	public static long hashFailDurationMs(Random random) {
		return DownloadControlBTBanRegistry.randomDurationRangeMs(HASH_FAIL_MIN_MS, HASH_FAIL_MAX_MS, random);
	}

	/** Fully-established peer idle in both directions for ~5 s. */
	public DownloadControlBTBanRegistry.Ban onIdlePeer(String address, String detail) {
		if (!isIdleBanEnabled() || address == null || address.isEmpty()) return null;
		DownloadControlBTBanRegistry.Ban ban;
		synchronized(this) {
			long now = System.currentTimeMillis();
			if (registry.isBanned(address, now)) return null;
			ban = applyBanLocked(address, now, REASON_IDLE, detail);
		}
		fire(ban);
		return ban;
	}

	public static boolean handshakeOverdue(int state, long establishedMono, long nowMono) {
		return handshakeOverdue(state, establishedMono, nowMono, 3000L);
	}

	public static boolean handshakeOverdue(int state, long establishedMono, long nowMono, long thresholdMs) {
		return state == com.biglybt.core.peer.impl.PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE
			&& nowMono - establishedMono > thresholdMs;
	}

	public String formatActiveBansSummary(long nowMs) {
		List<DownloadControlBTBanRegistry.Ban> active;
		synchronized (this) {
			active = registry.snapshot(nowMs);
		}
		if (active.isEmpty()) {
			return "Active DLCBT temporary bans: 0";
		}
		StringBuilder sb = new StringBuilder();
		sb.append("Active DLCBT temporary bans: ").append(active.size());
		int limit = Math.min(active.size(), 50);
		for (int i = 0; i < limit; i++) {
			DownloadControlBTBanRegistry.Ban b = active.get(i);
			long remSec = Math.max(0L, (b.expiryMs - nowMs) / 1000L);
			sb.append('\n')
			  .append(b.canonical)
			  .append("  [").append(b.reason).append("]  ")
			  .append(remSec / 60).append('m').append(String.format("%02ds", remSec % 60));
			if (b.detail != null && !b.detail.isEmpty()) {
				sb.append("  ").append(b.detail);
			}
		}
		if (active.size() > limit) {
			sb.append("\n... and ").append(active.size() - limit).append(" more");
		}
		return sb.toString();
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

	private long
	handshakeMs()
	{
		return Math.max( 1, intParam( CFG_HANDSHAKE_SECONDS, DEFAULT_HANDSHAKE_SECONDS )) * 1000L;
	}

	private int
	hashFailMinSeconds()
	{
		return Math.max( 1, intParam( CFG_HASH_FAIL_MIN_SECONDS, DEFAULT_HASH_FAIL_MIN_SECONDS ));
	}

	private int
	hashFailMaxSeconds()
	{
		return Math.max( hashFailMinSeconds(), intParam( CFG_HASH_FAIL_MAX_SECONDS, DEFAULT_HASH_FAIL_MAX_SECONDS ));
	}

	private static boolean
	boolParam(
		String		key,
		boolean		fallback )
	{
		try{
			return COConfigurationManager.getBooleanParameter( key, fallback );
		}catch( Throwable t ){
			return fallback;
		}
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
