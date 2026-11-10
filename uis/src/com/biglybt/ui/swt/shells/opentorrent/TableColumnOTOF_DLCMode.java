/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package com.biglybt.ui.swt.shells.opentorrent;

import com.biglybt.core.downloadcontrolbt.DownloadControlFileQueue;
import com.biglybt.core.downloadcontrolbt.DownloadControlStrictOrder;
import com.biglybt.core.internat.MessageText;
import com.biglybt.core.torrent.impl.TorrentOpenFileOptions;
import com.biglybt.core.torrent.impl.TorrentOpenOptions;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumn;
import com.biglybt.pif.ui.tables.TableColumnExtraInfoListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;

/**
 * Open Torrent Options file-table letter: S, R, or blank, reflecting the
 * pending Off / Auto queue / per-file override, even when unchecked.
 */
public class TableColumnOTOF_DLCMode
	implements TableCellRefreshListener, TableColumnExtraInfoListener
{
	public static final String COLUMN_ID = "dlcbt";

	public TableColumnOTOF_DLCMode(TableColumn column) {
		column.initialize(TableColumn.ALIGN_CENTER, TableColumn.POSITION_LAST, 36);
		column.addListeners(this);
		column.setRefreshInterval(TableColumn.INTERVAL_LIVE);
	}

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] {
			TableColumn.CAT_CONTENT,
		});
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	@Override
	public void refresh(TableCell cell) {
		Object ds = cell.getDataSource();
		if ( !(ds instanceof TorrentOpenFileOptions) ){
			return;
		}
		TorrentOpenFileOptions file = (TorrentOpenFileOptions) ds;
		TorrentOpenOptions opts = file.getTorrentOptions();
		boolean emptyFile = file == null || file.getSize() <= 0;
		boolean off = opts != null && opts.isDlcbtExplicitOff();
		boolean auto = opts != null && opts.isDlcbtAutoQueue();
		int override = opts == null ? DownloadControlStrictOrder.MODE_OFF : opts.getDlcbtFileMode( file.getIndex());
		String text = DownloadControlFileQueue.modeLetterForPendingFile(
			off, auto, override, emptyFile );
		int sort = 0;
		if ( "S".equals( text )){
			sort = 1;
		}else if ( "R".equals( text )){
			sort = 2;
		}
		if ( !cell.setSortValue( sort ) && cell.isValid() ){
			return;
		}
		cell.setText( text );
		try{
			cell.setToolTip( MessageText.getString( "TableColumn.header.dlcbt.tooltip" ));
		}catch( Throwable ignored ){
		}
	}
}
