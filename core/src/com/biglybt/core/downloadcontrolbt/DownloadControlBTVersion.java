/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.core.downloadcontrolbt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Display-only DLCBT version string ("DownLoadControlBT202611101559"), read
 * from the package's version.txt (beside LICENSE in azureus.install.path).
 * Never alters Constants.BIGLYBT_VERSION, which still drives protocol,
 * plugin checks and the peer client id.
 */
public final class DownloadControlBTVersion
{
	public static final String PREFIX = "DownLoadControlBT";

	private static volatile boolean	loaded;
	private static volatile String	cached;

	private DownloadControlBTVersion(){}

	/** "DownLoadControlBT" + stamp, or null when raw is not a 12-digit YYYYMMDDHHMM stamp. */
	public static String format( String raw )
	{
		if ( raw == null ){
			return null;
		}
		String v = raw.trim();
		if ( v.length() != 12 ){
			return null;
		}
		for ( int i = 0; i < v.length(); i++ ){
			char c = v.charAt( i );
			if ( c < '0' || c > '9' ){
				return null;
			}
		}
		return PREFIX + v;
	}

	/** Display string for the running package, or null outside a DLCBT package. */
	public static String displayVersion()
	{
		if ( !loaded ){
			synchronized( DownloadControlBTVersion.class ){
				if ( !loaded ){
					cached = readFrom( System.getProperty( "azureus.install.path" ));
					loaded = true;
				}
			}
		}
		return cached;
	}

	/** Prefers the DLCBT string, else the given upstream fallback. */
	public static String displayVersionOr( String fallback )
	{
		String v = displayVersion();
		return v == null ? fallback : v;
	}

	static String readFrom( String dir )
	{
		if ( dir == null || dir.isEmpty()){
			return null;
		}
		File f = new File( dir, "version.txt" );
		if ( !f.isFile() || f.length() > 256 ){
			return null;
		}
		try ( BufferedReader r = new BufferedReader( new InputStreamReader(
				new FileInputStream( f ), StandardCharsets.UTF_8 ))){
			return format( r.readLine());
		}catch( Throwable t ){
			return null;
		}
	}
}
