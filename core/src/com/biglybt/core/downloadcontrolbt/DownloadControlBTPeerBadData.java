/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.core.downloadcontrolbt;

import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * DLCBT Nov 18: per-peer (per connection) hash-failed byte counter for the
 * Peers "%Bad/Good" column. In memory only, weakly keyed by the peer object
 * so closed peers are dropped. A failed piece charges each writer with the
 * bytes of the blocks it wrote.
 */
public final class DownloadControlBTPeerBadData
{
	private static final Map<Object, long[]> bad = new WeakHashMap<>();

	private DownloadControlBTPeerBadData(){}

	public static void addBad( Object peer, long bytes )
	{
		if ( peer == null || bytes <= 0 ){
			return;
		}
		synchronized( bad ){
			long[] v = bad.get( peer );
			if ( v == null ){
				v = new long[1];
				bad.put( peer, v );
			}
			v[0] += bytes;
		}
	}

	public static long getBad( Object peer )
	{
		if ( peer == null ){
			return 0;
		}
		synchronized( bad ){
			long[] v = bad.get( peer );
			return v == null ? 0 : v[0];
		}
	}

	/** Good bytes = total data received minus hash-failed bytes, never negative. */
	public static long goodBytes( long totalDataReceived, long badBytes )
	{
		return Math.max( 0, totalDataReceived - Math.max( 0, badBytes ));
	}

	/**
	 * bad / good as a percentage: "" when nothing received, "0%" with no bad
	 * data, "∞" with bad data but no good data, else one decimal ("12.5%").
	 */
	public static String formatRatio( long badBytes, long goodBytes )
	{
		if ( badBytes <= 0 ){
			return goodBytes > 0 ? "0%" : "";
		}
		if ( goodBytes <= 0 ){
			return "\u221E";
		}
		double pct = badBytes * 100.0 / goodBytes;
		return String.format( Locale.ROOT, "%.1f%%", pct );
	}

	/** Sort value: ratio in thousandths of a percent; MAX for infinite, -1 when blank. */
	public static long sortValue( long badBytes, long goodBytes )
	{
		if ( badBytes <= 0 ){
			return goodBytes > 0 ? 0 : -1;
		}
		if ( goodBytes <= 0 ){
			return Long.MAX_VALUE;
		}
		return (long)( badBytes * 100000.0 / goodBytes );
	}
}
