/*
 * DownLoadControlBT (unofficial fork experiment). GPL like BiglyBT.
 */
package com.biglybt.ui.swt.views.tableitems.peers;

import com.biglybt.core.downloadcontrolbt.DownloadControlBT;
import com.biglybt.core.downloadcontrolbt.DownloadControlBTPeerBadData;
import com.biglybt.core.peer.PEPeer;
import com.biglybt.core.peer.PEPeerStats;
import com.biglybt.pif.ui.tables.TableCell;
import com.biglybt.pif.ui.tables.TableCellRefreshListener;
import com.biglybt.pif.ui.tables.TableColumnInfo;
import com.biglybt.ui.swt.views.table.CoreTableColumnSWT;

/**
 * DLCBT Nov 18: "%Bad/Good" = hash-failed bytes from this peer divided by
 * good bytes received from it. Visible by default when the DLCBT profile is on.
 */
public class DownloadControlBTBadGoodItem
	extends CoreTableColumnSWT
	implements TableCellRefreshListener
{
	public static final String COLUMN_ID = "dlcbt.badgood";

	@Override
	public void fillTableColumnInfo(TableColumnInfo info) {
		info.addCategories(new String[] { CAT_BYTES, CAT_PROTOCOL });
		info.setProficiency(TableColumnInfo.PROFICIENCY_BEGINNER);
	}

	public DownloadControlBTBadGoodItem(String table_id) {
		super(COLUMN_ID, ALIGN_TRAIL,
			DownloadControlBT.isProfileEnabled() ? POSITION_LAST : POSITION_INVISIBLE,
			70, table_id);
		setRefreshInterval(INTERVAL_LIVE);
	}

	@Override
	public void refresh(TableCell cell) {
		PEPeer peer = (PEPeer) cell.getDataSource();
		long bad = 0;
		long good = 0;
		if (peer != null) {
			bad = DownloadControlBTPeerBadData.getBad(peer);
			PEPeerStats stats = peer.getStats();
			long total = stats == null ? 0 : stats.getTotalDataBytesReceived();
			good = DownloadControlBTPeerBadData.goodBytes(total, bad);
		}
		long sort = DownloadControlBTPeerBadData.sortValue(bad, good);
		if (!cell.setSortValue(sort) && cell.isValid()) {
			return;
		}
		cell.setText(DownloadControlBTPeerBadData.formatRatio(bad, good));
	}
}
