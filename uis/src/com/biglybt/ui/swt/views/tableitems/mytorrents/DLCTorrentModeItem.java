/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 2 of the License only.
 */
package com.biglybt.ui.swt.views.tableitems.mytorrents;

import com.biglybt.core.download.DownloadManager;
import com.biglybt.core.downloadcontrolbt.DownloadControlBT;
import com.biglybt.pif.download.Download;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 * Library torrent-level DownLoadControlBT mode: S, R, Mixed, or blank (Off).
 */
public class DLCTorrentModeItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final Class DATASOURCE_TYPE = Download.class;
	public static final String COLUMN_ID = "dlcbt.torrent";

	public DLCTorrentModeItem(String sTableID) {
		super(DATASOURCE_TYPE, COLUMN_ID, ALIGN_CENTER, 48, sTableID);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void refresh(TableCell cell) {
		DownloadManager dm = (DownloadManager)cell.getDataSource();
		String text = "";
		try{
			text = dm == null ? "" : DownloadControlBT.getTorrentModeDisplay(dm);
		}catch( Throwable e ){
		}
		if ( text == null ){
			text = "";
		}
		int sort = "S".equals(text) ? 1 : "R".equals(text) ? 2 : text.length() > 0 ? 3 : 0;
		if ( !cell.setSortValue( sort ) && cell.isValid() ){
			return;
		}
		cell.setText( text );
	}
}
