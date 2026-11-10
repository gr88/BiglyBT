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

/**
 * One-shot (per Library table) DLCBT layout fix that also applies to
 * existing saved layouts: hide Play Now and the other DLCBT-hidden
 * columns, and remove the old torrent-level DLC column.
 * Runs once per table id; later user changes persist.
 */
public final class DownloadControlBTLibraryColumns
{
	private static final String FLAG_PREFIX = "dlcbt.library.columns.v3.";

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
			for ( Object o: mapTCs.values()){
				TableColumnCore tc = (TableColumnCore)o;
				String id = tc.getName();
				if ( DownloadControlBTDefaults.isHiddenDefaultLibraryColumn( id ) || "dlcbt.torrent".equals(id)){
					tc.setVisible( false );
				}

			}
			TableColumnManager.getInstance().saveTableColumns( dataSourceType, tableID );
			COConfigurationManager.setParameter( flag, true );
		}catch( Throwable e ){
			// layout cosmetics must never break table creation
		}
	}
}
