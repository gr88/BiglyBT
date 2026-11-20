/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Counts rejected *incoming* duplicate-connection attempts from the same
 * exact normalized address on the same torrent. Outgoing dials, loopback,
 * and different torrents do not increment the same bucket.
 */
public final class DownloadControlBTDuplicateTracker
{
	public static final class Decision
	{
		public final int		countInWindow;
		public final boolean	shouldBan;

		public Decision(
			int			countInWindow,
			boolean		shouldBan )
		{
			this.countInWindow = countInWindow;
			this.shouldBan = shouldBan;
		}
	}

	private static final class Key
	{
		final String	torrentId;
		final String	canonical;

		Key(
			String		torrentId,
			String		canonical )
		{
			this.torrentId = torrentId;
			this.canonical = canonical;
		}

		@Override
		public boolean equals(
			Object		o )
		{
			if ( this == o ){
				return true;
			}
			if ( !( o instanceof Key )){
				return false;
			}
			Key other = (Key)o;
			return torrentId.equals( other.torrentId ) && canonical.equals( other.canonical );
		}

		@Override
		public int hashCode()
		{
			return torrentId.hashCode() * 31 + canonical.hashCode();
		}
	}

	private final Map<Key, List<Long>>	attempts = new HashMap<>();
	private final int	threshold;
	private final long	windowMs;

	public DownloadControlBTDuplicateTracker()
	{
		this( 3, 60L * 1000L );
	}

	public DownloadControlBTDuplicateTracker(
		int		threshold,
		long	windowMs )
	{
		this.threshold = threshold;
		this.windowMs = windowMs;
	}

	public Decision
	onRejectedIncomingDuplicate(
		String		torrentId,
		String		rawAddress,
		long		nowMs,
		boolean		incoming,
		boolean		hadLiveSameAddressOnTorrent )
	{
		if ( !incoming || !hadLiveSameAddressOnTorrent || torrentId == null ){
			return new Decision( 0, false );
		}
		String canonical = DownloadControlBTAddresses.canonicalize( rawAddress );
		if ( canonical == null || DownloadControlBTAddresses.isLoopback( canonical )){
			return new Decision( 0, false );
		}
		Key key = new Key( torrentId, canonical );
		List<Long> times = attempts.get( key );
		if ( times == null ){
			times = new ArrayList<Long>();
			attempts.put( key, times );
		}
		long oldest = nowMs - windowMs;
		Iterator<Long> it = times.iterator();
		while ( it.hasNext()){
			if ( it.next().longValue() < oldest ){
				it.remove();
			}
		}
		times.add( Long.valueOf( nowMs ));
		boolean ban = times.size() >= threshold;
		if ( ban ){
			attempts.remove( key );
		}
		return new Decision( times.size(), ban );
	}
}
