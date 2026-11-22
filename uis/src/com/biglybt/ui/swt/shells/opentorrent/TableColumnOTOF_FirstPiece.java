package com.biglybt.ui.swt.shells.opentorrent;
import com.biglybt.core.torrent.impl.TorrentOpenFileOptions;
import com.biglybt.pif.ui.tables.*;
/** Torrent-global, zero-based containing-piece number (same numbering as Pieces). */
public class TableColumnOTOF_FirstPiece implements TableCellRefreshListener {
    public static final String COLUMN_ID = "dlcbt.firstpiece";
    public TableColumnOTOF_FirstPiece(TableColumn column) {
        column.initialize(TableColumn.ALIGN_TRAIL, TableColumn.POSITION_LAST, 72);
        column.addListeners(this);
        column.setRefreshInterval(TableColumn.INTERVAL_INVALID_ONLY);
    }
    public void refresh(TableCell cell) {
        TorrentOpenFileOptions file = (TorrentOpenFileOptions)cell.getDataSource();
        int piece = file == null || file.getSize() <= 0 ? -1 : file.getFirstPieceNumber();
        if (!cell.setSortValue(piece) && cell.isValid()) return;
        cell.setText(piece < 0 ? "" : String.valueOf(piece));
    }
}
