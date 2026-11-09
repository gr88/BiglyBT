/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2 of the License only.
 */
package com.biglybt.ui.swt.views.table.utils;

import java.util.Map;

import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.downloadcontrolbt.DownloadControlBT;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTDefaults;
import com.biglybt.ui.common.table.TableColumnCore;
import com.biglybt.ui.common.table.impl.TableColumnManager;
import com.biglybt.ui.swt.views.tableitems.mytorrents.DLCTorrentModeItem;

/**
 * One-shot (per Library table) DLCBT layout fix that also applies to
 * existing saved layouts: hide Play Now and the other DLCBT-hidden
 * columns, and on Incomplete tables show the DLC column after the name.
 * Runs once per table id; later user changes persist.
 */
public final class DownloadControlBTLibraryColumns
{
	private static final String FLAG_PREFIX = "dlcbt.library.columns.v2.";

	private DownloadControlBTLibraryColumns()
	{
	}

	public static void
	applyOnce(
		Map				mapTCs,
		Class			dataSourceType,
		String			tableID,
		boolean			incomplete )
	{
		try{
			if ( mapTCs == null || tableID == null || !DownloadControlBT.isProfileEnabled()){
				return;
			}
			String flag = FLAG_PREFIX + tableID;
			if ( COConfigurationManager.getBooleanParameter( flag, false )){
				return;
			}
			int namePos = -1;
			for ( Object o: mapTCs.values()){
				TableColumnCore tc = (TableColumnCore)o;
				String id = tc.getName();
				if ( DownloadControlBTDefaults.isHiddenDefaultLibraryColumn( id )){
					tc.setVisible( false );
				}
				if (( "name".equals( id ) || "name.thumb".equals( id )) && tc.isVisible()){
					namePos = Math.max( namePos, tc.getPosition());
				}
			}
			if ( incomplete ){
				TableColumnCore dlc = (TableColumnCore)mapTCs.get( DLCTorrentModeItem.COLUMN_ID );
				if ( dlc != null && !dlc.isVisible()){
					dlc.setVisible( true );
					if ( namePos >= 0 ){
						dlc.setPositionNoShift( namePos + 1 );
					}
				}
			}
			TableColumnManager.getInstance().saveTableColumns( dataSourceType, tableID );
			COConfigurationManager.setParameter( flag, true );
		}catch( Throwable e ){
			// layout cosmetics must never break table creation
		}
	}
}
