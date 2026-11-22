/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.ui.swt.views.table.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.config.ParameterListener;
import com.biglybt.core.downloadcontrolbt.DownloadControlBT;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTDefaults;
import com.biglybt.core.util.SimpleTimer;
import com.biglybt.core.util.TimerEvent;
import com.biglybt.core.util.TimerEventPerformer;
import com.biglybt.core.util.TimerEventPeriodic;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableManager;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.TableGroupRowRunner;
import com.biglybt.ui.common.table.TableRowCore;
import com.biglybt.ui.common.table.TableStructureEventDispatcher;
import com.biglybt.ui.swt.UIFunctionsManagerSWT;
import com.biglybt.ui.swt.UIFunctionsSWT;
import com.biglybt.ui.swt.Utils;
import com.biglybt.ui.swt.views.table.TableViewSWT;
import com.biglybt.ui.swt.views.tableitems.mytorrents.ColumnDoneWithDND;
import com.biglybt.ui.swt.views.tableitems.mytorrents.NameItem;

/**
 * Periodic "Size Column to Fit" for every open table, plus a one-shot
 * widen of the completed Library table.
 */
public final class DownloadControlBTTableAutoFit
{
	private static final int GRAPHIC_PCT_TARGET = 15;
	private static final int GRAPHIC_PCT_MIN = 10;
	private static final int GRAPHIC_PCT_MAX = 20;
	private static final int GRAPHIC_MIN_PX = 120;
	private static final int GRAPHIC_MAX_PX = 480;

	private static final Set<String> PIECES_GRAPHIC_COLUMNS = new HashSet<>(Arrays.asList(
		"pieces",
		"blocks",
		"completion",
		"ProgressETA",
		"fileavailability"
	));

	private static final WeakHashMap<TableViewSWT<?>, Boolean> tables = new WeakHashMap<>();
	private static TimerEventPeriodic timer;
	private static final ParameterListener listener = name -> reschedule();

	private DownloadControlBTTableAutoFit(){}

	public static void
	register( TableViewSWT<?> tv )
	{
		if ( tv == null || !DownloadControlBT.isProfileEnabled()){
			return;
		}
		synchronized( tables ){
			tables.put( tv, Boolean.TRUE );
			ensureTimer();
		}
		Utils.execSWTThreadLater( 1500, () -> maybeWidenCompleteTable( tv ));
	}

	public static void
	unregister( TableViewSWT<?> tv )
	{
		synchronized( tables ){
			tables.remove( tv );
		}
	}

	private static void
	ensureTimer()
	{
		if ( timer != null ){
			return;
		}
		try{
			COConfigurationManager.addParameterListener(
				DownloadControlBTDefaults.CFG_AUTOFIT_SECONDS, listener );
		}catch( Throwable ignored ){
		}
		reschedule();
	}

	private static void
	reschedule()
	{
		int seconds = 0;
		try{
			seconds = COConfigurationManager.getIntParameter(
				DownloadControlBTDefaults.CFG_AUTOFIT_SECONDS,
				DownloadControlBTDefaults.DEFAULT_AUTOFIT_SECONDS );
		}catch( Throwable ignored ){
		}
		if ( timer != null ){
			timer.cancel();
			timer = null;
		}
		if ( seconds <= 0 || !DownloadControlBT.isProfileEnabled()){
			return;
		}
		long ms = seconds * 1000L;
		timer = SimpleTimer.addPeriodicEvent( "dlcbt:autofit", ms, new TimerEventPerformer()
		{
			@Override
			public void perform( TimerEvent event )
			{
				Utils.execSWTThread( DownloadControlBTTableAutoFit::fitAll );
			}
		});
	}

	static void
	fitAll()
	{
		List<TableViewSWT<?>> list;
		synchronized( tables ){
			list = new ArrayList<>( tables.keySet());
		}
		for ( TableViewSWT<?> tv : list ){
			try{
				if ( tv.isDisposed()){
					continue;
				}
				fit( tv, false );
			}catch( Throwable ignored ){
			}
		}
	}

	public static boolean
	isPiecesTypeGraphicColumn( TableColumn column )
	{
		return column != null
			&& column.getType() == TableColumn.TYPE_GRAPHIC
			&& DownloadControlBTDefaults.isPiecesTypeGraphicColumnName( column.getName());
	}

	public static int
	computeGraphicalColumnWidth( int appWidth, int minWidth, int headerWidth )
	{
		return DownloadControlBTDefaults.computeGraphicalColumnWidth( appWidth, minWidth, headerWidth );
	}

	private static int
	resolveAppWindowWidth( TableViewSWT<?> tv )
	{
		try{
			UIFunctionsSWT uif = UIFunctionsManagerSWT.getUIFunctionsSWT();
			if ( uif != null ){
				Shell mainShell = uif.getMainShell();
				if ( mainShell != null && !mainShell.isDisposed()){
					int w = mainShell.getClientArea().width;
					if ( w > 0 ){
						return w;
					}
					w = mainShell.getSize().x;
					if ( w > 0 ){
						return w;
					}
				}
			}
		}catch( Throwable ignored ){
		}
		try{
			Composite c = tv.getComposite();
			if ( c != null && !c.isDisposed()){
				Shell s = c.getShell();
				if ( s != null && !s.isDisposed() && s.getClientArea().width > 0 ){
					return s.getClientArea().width;
				}
				if ( c.getClientArea().width > 0 ){
					return c.getClientArea().width;
				}
			}
		}catch( Throwable ignored ){
		}
		return 1000;
	}

	/**
	 * @param fillWindow if true, leftover client width goes to the Name column
	 */
	public static void
	fit( TableViewSWT<?> tv, boolean fillWindow )
	{
		if ( tv == null || tv.isDisposed()){
			return;
		}
		TableColumnCore[] cols = tv.getVisibleColumns();
		if ( cols == null || cols.length == 0 ){
			return;
		}
		int appWidth = resolveAppWindowWidth( tv );
		int total = 0;
		for ( TableColumnCore column : cols ){
			int pref;
			if ( isPiecesTypeGraphicColumn( column )){
				pref = computeGraphicalColumnWidth(
					appWidth, column.getMinWidth(), column.getPreferredHeaderWidth());
				column.setPreferredWidth( pref );
			}else{
				column.setPreferredWidth( -1 );
				int done = tv.runForAllRows( new TableGroupRowRunner()
				{
					@Override
					public void run( TableRowCore row )
					{
						row.fakeRedraw( column.getName());
					}
				});
				pref = done == 0
					? Math.max( column.getMinWidth(), column.getPreferredHeaderWidth())
					: Math.max( column.getPreferredWidth(), column.getPreferredHeaderWidth());
			}
			if ( pref > 0 ){
				column.setWidth( pref );
				total += pref;
			}else{
				total += Math.max( 0, column.getWidth());
			}
		}
		if ( fillWindow ){
			Composite c = tv.getComposite();
			if ( c != null && !c.isDisposed()){
				int client = c.getClientArea().width;
				int leftover = client - total;
				if ( leftover > 20 ){
					for ( TableColumnCore column : cols ){
						if ( NameItem.COLUMN_ID.equals( column.getName()) || "name".equals( column.getName())){
							column.setWidth( column.getWidth() + leftover );
							break;
						}
					}
				}
			}
		}
		TableStructureEventDispatcher.getInstance( tv.getTableID()).tableStructureChanged( false, null );
	}

	private static void
	maybeWidenCompleteTable( TableViewSWT<?> tv )
	{
		try{
			if ( !DownloadControlBT.isProfileEnabled() || tv == null || tv.isDisposed()){
				return;
			}
			String id = tv.getTableID();
			if ( !TableManager.TABLE_MYTORRENTS_COMPLETE.equals( id )
				&& !TableManager.TABLE_MYTORRENTS_COMPLETE_BIG.equals( id )){
				return;
			}
			if ( COConfigurationManager.getBooleanParameter(
					DownloadControlBTDefaults.CFG_LIBRARY_COMPLETE_COLS_V1, false )){
				return;
			}
			for ( TableColumnCore c : tv.getAllColumns()){
				if ( ColumnDoneWithDND.COLUMN_ID.equals( c.getName())){
					c.setVisible( true );
				}
			}
			fit( tv, true );
			COConfigurationManager.setParameter(
				DownloadControlBTDefaults.CFG_LIBRARY_COMPLETE_COLS_V1, true );
		}catch( Throwable ignored ){
		}
	}
}
