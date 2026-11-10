/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

/**
 * Per-peer incoming DATA (payload) average. Ramp and window accumulate
 * only while the peer is actively sending eligible requested data.
 * Idle, choked, no-outstanding-request, or piece-gate/disk-verify waits
 * are not active time: they pause the clock (neither time nor bytes are
 * counted) so stale zeros cannot become a "below minimum" ban. A pause
 * longer than {@link #STALE_PAUSE_MS} discards the partial state.
 */
public final class DownloadControlBTSpeedSampler
{
	public static final int NOT_ELIGIBLE = 0;
	public static final int RAMPING = 1;
	public static final int OK = 2;
	public static final int BELOW_MIN = 3;
	public static final int ABOVE_MAX = 4;

	public static final long STALE_PAUSE_MS = 5L * 60L * 1000L;

	private long	eligibleMs;
	private long	pausedSinceMs = -1;
	private long	lastEligibleMs = -1;
	private long	windowElapsedMs;
	private long	windowBytes;
	private boolean	windowOpen;

	public void
	reset()
	{
		eligibleMs = 0;
		lastEligibleMs = -1;
		pausedSinceMs = -1;
		windowElapsedMs = 0;
		windowBytes = 0;
		windowOpen = false;
	}

	public int
	sample(
		long		nowMs,
		long		dataBytesDelta,
		boolean		activelySendingEligible,
		long		minBps,
		long		maxBps,
		long		rampMs,
		long		windowMs )
	{
		if ( !activelySendingEligible ){
			if ( pausedSinceMs < 0 ){
				pausedSinceMs = nowMs;
			}else if ( nowMs - pausedSinceMs > STALE_PAUSE_MS ){
				reset();
				pausedSinceMs = nowMs;
			}
			lastEligibleMs = -1;
			return NOT_ELIGIBLE;
		}
		pausedSinceMs = -1;
		if ( dataBytesDelta < 0 ){
			dataBytesDelta = 0;
		}
		if ( lastEligibleMs < 0 ){
			lastEligibleMs = nowMs;
			return eligibleMs >= rampMs ? ( windowOpen ? OK : RAMPING ) : RAMPING;
		}
		long dt = nowMs - lastEligibleMs;
		lastEligibleMs = nowMs;
		if ( dt < 0 ){
			dt = 0;
		}
		if ( eligibleMs < rampMs ){
			eligibleMs += dt;
			return RAMPING;
		}
		if ( !windowOpen ){
			windowOpen = true;
			windowElapsedMs = 0;
			windowBytes = 0;
		}
		windowElapsedMs += dt;
		windowBytes += dataBytesDelta;
		if ( windowElapsedMs < windowMs ){
			return OK;
		}
		long avg = windowBytes * 1000L / Math.max( 1L, windowElapsedMs );
		windowOpen = false;
		windowElapsedMs = 0;
		windowBytes = 0;
		if ( avg < minBps ){
			return BELOW_MIN;
		}
		if ( maxBps > 0 && avg > maxBps ){
			return ABOVE_MAX;
		}
		return OK;
	}

	public long
	getEligibleMs()
	{
		return eligibleMs;
	}
}
