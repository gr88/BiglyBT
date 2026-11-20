/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Random;

import org.junit.jupiter.api.Test;

public class DownloadControlBTBanPolicyTest
{
	private static final long MIN_BPS = 10L * 1024;
	private static final long MAX_BPS = (long)( 251.1f * 1024 );
	private static final long RAMP = 60_000L;
	private static final long WINDOW = 30_000L;

	@Test
	public void canonicalizesMappedAndSpellingVariants()
	{
		assertThat( DownloadControlBTAddresses.canonicalize( "1.2.3.4" )).isEqualTo( "1.2.3.4" );
		assertThat( DownloadControlBTAddresses.canonicalize( "::ffff:1.2.3.4" )).isEqualTo( "1.2.3.4" );
		assertThat( DownloadControlBTAddresses.canonicalize( "0:0:0:0:0:ffff:0102:0304" )).isEqualTo( "1.2.3.4" );
		String a = DownloadControlBTAddresses.canonicalize( "2001:0db8:0000:0000:0000:0000:0000:0001" );
		String b = DownloadControlBTAddresses.canonicalize( "2001:db8::1" );
		assertThat( a ).isEqualTo( b );
		assertThat( DownloadControlBTAddresses.same( "::ffff:10.0.0.9", "10.0.0.9" )).isTrue();
		assertThat( DownloadControlBTAddresses.isLoopback( "127.0.0.1" )).isTrue();
		assertThat( DownloadControlBTAddresses.isLoopback(
			DownloadControlBTAddresses.canonicalize( "::1" ))).isTrue();
	}

	@Test
	public void parseFractionalKUses1024()
	{
		assertThat( DownloadControlBTRateFormat.parseRate( "10k" )).isEqualTo( 10L * 1024 );
		assertThat( DownloadControlBTRateFormat.parseRate( "251.1k" )).isEqualTo((long)( 251.1f * 1024 ));
		assertThat( DownloadControlBTRateFormat.peerDownToken(
			Arrays.asList( "peer_set UpPeers=All,peer_up=25.1k",
				"peer_set DownPeers=All,peer_down=251.1k" ))).isEqualTo( "251.1k" );
	}

	@Test
	public void registryExactAddressNoSubnetCascade()
	{
		DownloadControlBTBanRegistry registry = new DownloadControlBTBanRegistry();
		long now = 1_000_000L;
		long dur = 54L * 60L * 1000L;
		assertThat( registry.ban( "8.8.8.8", now, dur, "speed-low", "t" ).canonical ).isEqualTo( "8.8.8.8" );
		assertThat( registry.isBanned( "8.8.8.8", now + 1000 )).isTrue();
		assertThat( registry.isBanned( "8.8.8.9", now + 1000 )).isFalse();
		assertThat( registry.isBanned( "::ffff:8.8.8.8", now + 1000 )).isTrue();
		registry.ban( "2001:db8::1", now, dur, "speed-high", "t" );
		assertThat( registry.isBanned( "2001:0db8:0:0:0:0:0:1", now + 1 )).isTrue();
		assertThat( registry.unban( "8.8.8.8" )).isTrue();
		assertThat( registry.isBanned( "8.8.8.8", now + 1 )).isFalse();
		assertThat( registry.isBanned( "2001:db8::1", now + 1 )).isTrue();
		registry.ban( "8.8.4.4", now, dur, "speed-low", "t" );
		assertThat( registry.isBanned( "8.8.4.4", now + dur - 1 )).isTrue();
		assertThat( registry.isBanned( "8.8.4.4", now + dur )).isFalse();
		assertThat( registry.isBanned( "2001:db8::1", now + 1 )).isTrue();
	}

	@Test
	public void loopbackIsNeverStored()
	{
		DownloadControlBTBanRegistry registry = new DownloadControlBTBanRegistry();
		assertThat( registry.ban( "127.0.0.1", 0, 1000, "x", "" )).isNull();
		assertThat( registry.ban( "::1", 0, 1000, "x", "" )).isNull();
	}

	@Test
	public void durationRangeIsInclusive()
	{
		Random fixed = new Random( 1 );
		for ( int i=0; i<40; i++ ){
			long ms = DownloadControlBTBanRegistry.randomDurationMs( 54, 64, fixed );
			long min = 54L * 60L * 1000L;
			long max = 64L * 60L * 1000L;
			assertThat( ms ).isBetween( min, max );
			assertThat( ms % 60_000L ).isZero();
		}
	}

	@Test
	public void existingBanIsNotExtended()
	{
		DownloadControlBTBanRegistry registry = new DownloadControlBTBanRegistry();
		DownloadControlBTBanRegistry.Ban first = registry.ban( "9.9.9.9", 0, 10_000, "speed-low", "a" );
		DownloadControlBTBanRegistry.Ban again = registry.ban( "9.9.9.9", 5_000, 50_000, "speed-high", "b" );
		assertThat( again.expiryMs ).isEqualTo( first.expiryMs );
		assertThat( again.reason ).isEqualTo( "speed-low" );
	}

	@Test
	public void idleZerosNeverViolateMinimum()
	{
		DownloadControlBTSpeedSampler s = new DownloadControlBTSpeedSampler();
		int last = DownloadControlBTSpeedSampler.NOT_ELIGIBLE;
		for ( int t=0; t<=90_000; t += 1000 ){
			last = s.sample( t, 0, false, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		}
		assertThat( last ).isEqualTo( DownloadControlBTSpeedSampler.NOT_ELIGIBLE );
	}

	@Test
	public void rampRequiresSixtySecondsOfEligibleTime()
	{
		DownloadControlBTSpeedSampler s = new DownloadControlBTSpeedSampler();
		assertThat( s.sample( 0, 1000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.RAMPING );
		assertThat( s.sample( 59_000, 59_000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.RAMPING );
		assertThat( s.sample( 60_000, 1000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.RAMPING );
	}

	@Test
	public void inactivityPausesRampWithoutCountingIdleTime()
	{
		DownloadControlBTSpeedSampler s = new DownloadControlBTSpeedSampler();
		s.sample( 0, 1000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		s.sample( 30_000, 30_000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		assertThat( s.sample( 31_000, 0, false, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.NOT_ELIGIBLE );
		// 100s of idle/gate wait must not count toward ramp
		assertThat( s.sample( 131_000, 0, false, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.NOT_ELIGIBLE );
		assertThat( s.sample( 132_000, 1000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.RAMPING );
		assertThat( s.getEligibleMs()).isEqualTo( 30_000 );
		assertThat( s.sample( 162_000, 30_000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.RAMPING );
		assertThat( s.getEligibleMs()).isEqualTo( 60_000 );
	}

	@Test
	public void stalePauseDiscardsPartialState()
	{
		DownloadControlBTSpeedSampler s = new DownloadControlBTSpeedSampler();
		s.sample( 0, 1000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		s.sample( 30_000, 30_000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		s.sample( 31_000, 0, false, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		s.sample( 31_000 + DownloadControlBTSpeedSampler.STALE_PAUSE_MS + 1, 0, false, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		assertThat( s.getEligibleMs()).isEqualTo( 0 );
	}

	@Test
	public void belowMinAfterRampAndWindow()
	{
		DownloadControlBTSpeedSampler s = primed( 4000 ); // 4kB over first window tick pattern
		// After ramp completing sample at 60000, next open window at 60000 return RAMPING.
		// Window starts at subsequent sample.
		assertThat( s.sample( 90_000, 4000, true, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.BELOW_MIN );
	}

	@Test
	public void okBetweenThresholds()
	{
		DownloadControlBTSpeedSampler s = primed( 20L * 1024 * 30 );
		assertThat( s.sample( 90_000, 20L * 1024 * 30, true, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.OK );
	}

	@Test
	public void aboveMaxAfterRampAndWindow()
	{
		DownloadControlBTSpeedSampler s = primed( 300L * 1024 * 30 );
		assertThat( s.sample( 90_000, 300L * 1024 * 30, true, MIN_BPS, MAX_BPS, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.ABOVE_MAX );
	}

	@Test
	public void thresholdUpdateAppliesToNextWindow()
	{
		DownloadControlBTSpeedSampler s = primed( 20L * 1024 * 30 );
		assertThat( s.sample( 90_000, 20L * 1024 * 30, true, MIN_BPS, 15L * 1024, RAMP, WINDOW ))
			.isEqualTo( DownloadControlBTSpeedSampler.ABOVE_MAX );
	}

	@Test
	public void duplicateWindowInclusiveBoundary()
	{
		DownloadControlBTDuplicateTracker tracker = new DownloadControlBTDuplicateTracker();
		String torrent = "hash-a";
		assertThat( tracker.onRejectedIncomingDuplicate( torrent, "5.5.5.5", 0, true, true ).shouldBan ).isFalse();
		assertThat( tracker.onRejectedIncomingDuplicate( torrent, "5.5.5.5", 30_000, true, true ).shouldBan ).isFalse();
		DownloadControlBTDuplicateTracker.Decision third =
			tracker.onRejectedIncomingDuplicate( torrent, "5.5.5.5", 60_000, true, true );
		assertThat( third.shouldBan ).isTrue();
		assertThat( third.countInWindow ).isEqualTo( 3 );
	}

	@Test
	public void duplicateJustOutsideWindowDoesNotBan()
	{
		DownloadControlBTDuplicateTracker tracker = new DownloadControlBTDuplicateTracker();
		tracker.onRejectedIncomingDuplicate( "hash-a", "5.5.5.5", 0, true, true );
		tracker.onRejectedIncomingDuplicate( "hash-a", "5.5.5.5", 30_000, true, true );
		assertThat( tracker.onRejectedIncomingDuplicate( "hash-a", "5.5.5.5", 60_001, true, true ).shouldBan )
			.isFalse();
	}

	@Test
	public void differentTorrentOrOutgoingOrNoLiveDoesNotCount()
	{
		DownloadControlBTDuplicateTracker tracker = new DownloadControlBTDuplicateTracker();
		tracker.onRejectedIncomingDuplicate( "hash-a", "5.5.5.5", 0, true, true );
		tracker.onRejectedIncomingDuplicate( "hash-b", "5.5.5.5", 10, true, true );
		tracker.onRejectedIncomingDuplicate( "hash-a", "5.5.5.5", 20, false, true );
		tracker.onRejectedIncomingDuplicate( "hash-a", "5.5.5.5", 30, true, false );
		assertThat( tracker.onRejectedIncomingDuplicate( "hash-a", "5.5.5.5", 40, true, true ).shouldBan )
			.isFalse();
	}

	@Test
	public void mappedAddressSharesDuplicateBucket()
	{
		DownloadControlBTDuplicateTracker tracker = new DownloadControlBTDuplicateTracker();
		tracker.onRejectedIncomingDuplicate( "hash-a", "9.9.9.9", 0, true, true );
		tracker.onRejectedIncomingDuplicate( "hash-a", "::ffff:9.9.9.9", 10, true, true );
		assertThat( tracker.onRejectedIncomingDuplicate( "hash-a", "9.9.9.9", 20, true, true ).shouldBan )
			.isTrue();
	}

	@Test
	public void torrentDisplayLetterMixed()
	{
		assertThat( DownloadControlFileQueue.torrentModeDisplay( false, DownloadControlFileQueue.POLICY_AUTO,
			Arrays.asList( "S", "R" ))).isEmpty();
		assertThat( DownloadControlFileQueue.torrentModeDisplay( true, DownloadControlFileQueue.POLICY_OFF,
			Arrays.asList( "S" ))).isEmpty();
		assertThat( DownloadControlFileQueue.torrentModeDisplay( true, DownloadControlFileQueue.POLICY_AUTO,
			Arrays.asList( "S", "S" ))).isEqualTo( "S" );
		assertThat( DownloadControlFileQueue.torrentModeDisplay( true, DownloadControlFileQueue.POLICY_AUTO,
			Arrays.asList( "R", "" ))).isEqualTo( "R" );
		assertThat( DownloadControlFileQueue.torrentModeDisplay( true, DownloadControlFileQueue.POLICY_AUTO,
			Arrays.asList( "S", "R" ))).isEqualTo( "Mixed" );
	}

	private static DownloadControlBTSpeedSampler
	primed(
		long		ignoredWindowBytes )
	{
		DownloadControlBTSpeedSampler s = new DownloadControlBTSpeedSampler();
		s.sample( 0, 1024, true, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		s.sample( 60_000, 60 * 1024, true, MIN_BPS, MAX_BPS, RAMP, WINDOW );
		return s;
	}
}
