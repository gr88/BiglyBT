/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.core.downloadcontrolbt;

import java.util.concurrent.atomic.AtomicBoolean;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.util.AEThread2;
import com.biglybt.pif.ui.UIInputReceiver;
import com.biglybt.ui.mdi.MultipleDocumentInterface;
import com.biglybt.ui.swt.SimpleTextEntryWindow;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.nat.NATTestHelpers;

/**
 * Post-sidebar startup: open All Peers once if unset, and run the NAT test.
 */
public final class DownloadControlBTUiStartup
{
	private static final AtomicBoolean started = new AtomicBoolean( false );

	private DownloadControlBTUiStartup(){}

	public static void
	afterSideBar( MultipleDocumentInterface mdi )
	{
		if ( !DownloadControlBT.isProfileEnabled()){
			return;
		}
		DownloadControlBTDefaults.registerStickyDirectoryListeners();
		if ( !started.compareAndSet( false, true )){
			return;
		}
		try{
			if ( !COConfigurationManager.getBooleanParameter( DownloadControlBTDefaults.CFG_ALLPEERS_V1, false )){
				if ( mdi != null ){
					mdi.loadEntryByID( MultipleDocumentInterface.SIDEBAR_SECTION_ALLPEERS, false );
				}
				COConfigurationManager.setParameter( DownloadControlBTDefaults.CFG_ALLPEERS_V1, true );
			}
		}catch( Throwable t ){
		}
		new AEThread2( "dlcbt:nat-test", true )
		{
			@Override
			public void
			run()
			{
				runNatTest();
			}
		}.start();
	}

	static void
	runNatTest()
	{
		try{
			int tcp = COConfigurationManager.getIntParameter( "TCP.Listen.Port" );
			int udp = COConfigurationManager.getIntParameter( "UDP.Listen.Port" );
			final AtomicBoolean tcpOk = new AtomicBoolean( false );
			final AtomicBoolean udpOk = new AtomicBoolean( false );
			final AtomicBoolean tcpFail = new AtomicBoolean( false );
			final AtomicBoolean udpFail = new AtomicBoolean( false );
			NATTestHelpers.runTCP( tcp, line -> classify( line, tcpOk, tcpFail ));
			try{
				NATTestHelpers.runUDP( com.biglybt.core.CoreFactory.getSingleton(), udp,
					line -> classify( line, udpOk, udpFail ));
			}catch( Throwable t ){
				udpFail.set( true );
			}
			if ( tcpOk.get() || udpOk.get()){
				return;
			}
			if ( !( tcpFail.get() && udpFail.get())){
				return;
			}
			Utils.execSWTThread( () -> promptForPort( tcp ));
		}catch( Throwable t ){
			// never fail startup
		}
	}

	static void
	classify( String line, AtomicBoolean ok, AtomicBoolean fail )
	{
		if ( line == null ){
			return;
		}
		try{
			if ( line.contains( MessageText.getString( "configureWizard.nat.ok" ))){
				ok.set( true );
			}
			if ( line.contains( MessageText.getString( "configureWizard.nat.ko" ))){
				fail.set( true );
			}
		}catch( Throwable ignored ){
		}
	}

	private static void
	promptForPort( int current )
	{
		try{
			SimpleTextEntryWindow entry = new SimpleTextEntryWindow(
				"ConfigView.section.dlcbt.nat.popup.title",
				"ConfigView.section.dlcbt.nat.popup.text" );
			entry.setPreenteredText( String.valueOf( current ), false );
			entry.prompt( (UIInputReceiver receiver) -> {
				if ( receiver == null || !receiver.hasSubmittedInput()){
					return;
				}
				try{
					int port = Integer.parseInt( receiver.getSubmittedInput().trim());
					if ( port < 1 || port > 65535 ){
						return;
					}
					COConfigurationManager.setParameter( "TCP.Listen.Port", port );
					COConfigurationManager.setParameter( "UDP.Listen.Port", port );
					COConfigurationManager.setParameter( "UDP.NonData.Listen.Port", port );
					COConfigurationManager.setParameter( DownloadControlBTDefaults.CFG_LISTEN_PORTS_V1, true );
					COConfigurationManager.save();
				}catch( Throwable ignored ){
				}
			});
		}catch( Throwable ignored ){
		}
	}
}
