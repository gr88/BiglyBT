/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import java.util.List;
import java.util.Locale;

/**
 * Same conventional units as SpeedLimitHandler.parseRate: 251.1k is
 * 251.1 * 1024 bytes/sec. Used only for isolated DLCBT ban thresholds.
 */
public final class DownloadControlBTRateFormat
{
	public static final String DEFAULT_MIN = "10k";
	public static final String DEFAULT_MAX = "251.1k";

	private DownloadControlBTRateFormat()
	{
	}

	public static long
	parseRate(
		String		str )
	{
		if ( str == null ){
			throw new IllegalArgumentException( "rate" );
		}
		str = str.trim().toLowerCase( Locale.US );
		int slash = str.indexOf( '/' );
		if ( slash != -1 ){
			str = str.substring( 0, slash ).trim();
		}
		String num = "";
		long mult = 1;
		for ( int i=0; i<str.length(); i++ ){
			char c = str.charAt( i );
			if ( Character.isDigit( c ) || c == '.' ){
				num += c;
			}else{
				if ( c == 'k' ){
					mult = 1024;
				}else if ( c == 'm' ){
					mult = 1024L * 1024;
				}else if ( c == 'g' ){
					mult = 1024L * 1024 * 1024;
				}
				break;
			}
		}
		if ( num.length() == 0 ){
			throw new IllegalArgumentException( "rate" );
		}
		if ( num.contains( "." )){
			return (long)( Float.parseFloat( num ) * mult );
		}
		return Long.parseLong( num ) * mult;
	}

	/**
	 * Reads the first peer_down= token from schedule lines such as
	 * {@code peer_set DownPeers=All,peer_down=251.1k}.
	 */
	public static String
	peerDownToken(
		List<?>		lines )
	{
		if ( lines == null ){
			return null;
		}
		for ( Object raw: lines ){
			if ( raw == null ){
				continue;
			}
			String line = raw instanceof byte[]
				? new String((byte[])raw, java.nio.charset.StandardCharsets.UTF_8 )
				: String.valueOf( raw );
			String lc = line.toLowerCase( Locale.US );
			int idx = lc.indexOf( "peer_down=" );
			if ( idx < 0 ){
				continue;
			}
			int start = idx + "peer_down=".length();
			int end = start;
			while ( end < lc.length() ){
				char c = lc.charAt( end );
				if ( c == ',' || c == ' ' || c == '\t' ){
					break;
				}
				end++;
			}
			if ( end > start ){
				return line.substring( start, end ).trim();
			}
		}
		return null;
	}
}
