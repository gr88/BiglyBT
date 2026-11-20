/*
 * Copyright (C) 2026 DownLoadControlBT experiment contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *
 */

package com.biglybt.ui.swt.views.tableitems.files;

import com.biglybt.core.disk.DiskManagerFileInfo;
import com.biglybt.core.downloadcontrolbt.DownloadControlBT;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 * Files-view letter for the DownLoadControlBT experiment only:
 * S sequential, R random, blank if the file is skipped or the
 * experiment is off. In auto file queue the letter is shown for
 * each controlled queued file; in manual mode it is shown only
 * on the selected file. This is not BiglyBT's ordinary
 * sequential-from-file picker flag.
 */
public class DLCModeItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public DLCModeItem(String tableID) {
		super("dlcbt", ALIGN_CENTER, POSITION_LAST, 36, tableID);
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
		DiskManagerFileInfo fileInfo = (DiskManagerFileInfo)cell.getDataSource();
		String text = "";
		int sort = 0;
		if ( fileInfo != null && fileInfo.getDownloadManager() != null ){
			text = DownloadControlBT.getModeLetterForFile(
				fileInfo.getDownloadManager(), fileInfo.getIndex());
			if ( "S".equals( text )){
				sort = 1;
			}else if ( "R".equals( text )){
				sort = 2;
			}
		}
		if ( !cell.setSortValue( sort ) && cell.isValid() ){
			return;
		}
		cell.setText( text );
	}
}
