/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 */
package com.biglybt.core.downloadcontrolbt;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;

/**
 * Exact-address identity for DLCBT temporary bans. IPv4 /32 and IPv6 /128
 * after folding IPv4-mapped IPv6 onto IPv4 and using Java's host address
 * for a stable IPv6 spelling. No subnet math.
 */
public final class DownloadControlBTAddresses
{
	private DownloadControlBTAddresses()
	{
	}

	public static String
	canonicalize(
		String		raw )
	{
		if ( raw == null ){
			return null;
		}
		String trimmed = raw.trim();
		if ( trimmed.length() == 0 ){
			return null;
		}
		int pct = trimmed.indexOf( '%' );
		if ( pct > 0 ){
			trimmed = trimmed.substring( 0, pct );
		}
		if ( trimmed.startsWith( "[" ) && trimmed.endsWith( "]" ) && trimmed.length() > 2 ){
			trimmed = trimmed.substring( 1, trimmed.length() - 1 );
		}
		try{
			InetAddress ia = InetAddress.getByName( trimmed );
			if ( ia instanceof Inet6Address ){
				byte[] b = ia.getAddress();
				if ( isIpv4Mapped( b )){
					return dotted( b[12], b[13], b[14], b[15] );
				}
			}
			if ( ia instanceof Inet4Address ){
				return ia.getHostAddress();
			}
			return ia.getHostAddress();
		}catch( Throwable t ){
			return null;
		}
	}

	static boolean
	isIpv4Mapped(
		byte[]		b )
	{
		if ( b == null || b.length != 16 ){
			return false;
		}
		for ( int i=0; i<10; i++ ){
			if ( b[i] != 0 ){
				return false;
			}
		}
		return b[10] == (byte)0xff && b[11] == (byte)0xff;
	}

	private static String
	dotted(
		byte	a,
		byte	b,
		byte	c,
		byte	d )
	{
		return (a & 0xff) + "." + (b & 0xff) + "." + (c & 0xff) + "." + (d & 0xff);
	}

	public static boolean
	isLoopback(
		String		canonical )
	{
		if ( canonical == null ){
			return false;
		}
		if ( "127.0.0.1".equals( canonical ) || "0.0.0.0".equals( canonical )){
			return true;
		}
		if ( canonical.startsWith( "127." )){
			return true;
		}
		return "::1".equals( canonical ) || "0:0:0:0:0:0:0:1".equalsIgnoreCase( canonical );
	}

	public static boolean
	same(
		String		a,
		String		b )
	{
		String ca = canonicalize( a );
		String cb = canonicalize( b );
		return ca != null && ca.equals( cb );
	}
}
