/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Isolated, in-memory temporary bans. Independent of IpFilter / IPBanner
 * so a speed or duplicate ban cannot widen an IPv4 /24 or rewrite
 * persisted bad-data bans.
 */
public final class DownloadControlBTBanRegistry
{
	public static final class Ban
	{
		public final String		canonical;
		public final long		startMs;
		public final long		expiryMs;
		public final String		reason;
		public final String		detail;

		public Ban(
			String		canonical,
			long		startMs,
			long		expiryMs,
			String		reason,
			String		detail )
		{
			this.canonical = canonical;
			this.startMs = startMs;
			this.expiryMs = expiryMs;
			this.reason = reason;
			this.detail = detail;
		}

		public boolean
		activeAt(
			long		nowMs )
		{
			return nowMs < expiryMs;
		}
	}

	private final Map<String, Ban>	bans = new LinkedHashMap<>();

	public Ban
	ban(
		String		rawAddress,
		long		nowMs,
		long		durationMs,
		String		reason,
		String		detail )
	{
		String canonical = DownloadControlBTAddresses.canonicalize( rawAddress );
		if ( canonical == null || DownloadControlBTAddresses.isLoopback( canonical )){
			return null;
		}
		if ( durationMs <= 0 ){
			throw new IllegalArgumentException( "duration" );
		}
		Ban existing = bans.get( canonical );
		if ( existing != null && existing.activeAt( nowMs )){
			return existing;
		}
		Ban created = new Ban( canonical, nowMs, nowMs + durationMs, reason, detail );
		bans.put( canonical, created );
		return created;
	}

	public boolean
	isBanned(
		String		rawAddress,
		long		nowMs )
	{
		String canonical = DownloadControlBTAddresses.canonicalize( rawAddress );
		if ( canonical == null ){
			return false;
		}
		Ban ban = bans.get( canonical );
		if ( ban == null ){
			return false;
		}
		if ( !ban.activeAt( nowMs )){
			bans.remove( canonical );
			return false;
		}
		return true;
	}

	public Ban
	get(
		String		rawAddress,
		long		nowMs )
	{
		String canonical = DownloadControlBTAddresses.canonicalize( rawAddress );
		if ( canonical == null ){
			return null;
		}
		Ban ban = bans.get( canonical );
		if ( ban == null ){
			return null;
		}
		if ( !ban.activeAt( nowMs )){
			bans.remove( canonical );
			return null;
		}
		return ban;
	}

	public boolean
	unban(
		String		rawAddress )
	{
		String canonical = DownloadControlBTAddresses.canonicalize( rawAddress );
		if ( canonical == null ){
			return false;
		}
		return bans.remove( canonical ) != null;
	}

	public int
	unbanAll()
	{
		int n = bans.size();
		bans.clear();
		return n;
	}

	public List<Ban>
	expireDue(
		long		nowMs )
	{
		List<Ban> gone = new ArrayList<>();
		List<String> keys = new ArrayList<>( bans.keySet());
		for ( String key: keys ){
			Ban ban = bans.get( key );
			if ( ban != null && !ban.activeAt( nowMs )){
				bans.remove( key );
				gone.add( ban );
			}
		}
		return gone;
	}

	public List<Ban>
	snapshot(
		long		nowMs )
	{
		expireDue( nowMs );
		return new ArrayList<>( bans.values());
	}

	public static long
	randomDurationMs(
		int			minMinutes,
		int			maxMinutes,
		Random		random )
	{
		if ( minMinutes <= 0 || maxMinutes < minMinutes ){
			throw new IllegalArgumentException( "duration range" );
		}
		int span = maxMinutes - minMinutes;
		int picked = minMinutes + ( span == 0 ? 0 : random.nextInt( span + 1 ));
		return picked * 60L * 1000L;
	}
}
