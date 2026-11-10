package com.biglybt.core.downloadcontrolbt;

/** Per-controller cadence for the active piece only; monotonic timestamps. */
public final class DownloadControlBTRerequest {
    private int lastPiece = -1;
    private long lastRequest = Long.MIN_VALUE;

    public static boolean fullyCovered(boolean[] received, boolean[] requested) {
        if (received == null || requested == null || received.length == 0 || received.length != requested.length) return false;
        boolean missing = false;
        for (int i=0; i<received.length; i++) {
            if (!received[i]) { missing = true; if (!requested[i]) return false; }
        }
        return missing; // all data arrived: wait for hash verification, don't rerequest
    }
    public static final class Coverage {
        public final boolean covered, allArrived;
        Coverage(boolean covered, boolean allArrived) { this.covered=covered; this.allArrived=allArrived; }
    }
    /** Include actual peer queues: request marks may already have been cleared by a rerequest. */
    public static Coverage coverage(com.biglybt.core.peer.impl.PEPeerControl control,
            com.biglybt.core.disk.DiskManager disk, com.biglybt.core.peer.PEPiece piece) {
        if (piece == null || piece.getNbBlocks() == 0) return new Coverage(false,false);
        boolean all=true, covered=true;
        java.util.List<com.biglybt.core.peer.PEPeer> peers=null;
        for (int b=0;b<piece.getNbBlocks();b++) {
            if (piece.isDownloaded(b) || piece.isWritten(b)) continue;
            all=false;
            if (piece.isRequested(b)) continue; // includes the picker's block promises
            boolean outstanding=false;
            if (control != null && disk != null) {
                if (peers == null) peers=control.getPeers();
                com.biglybt.core.disk.DiskManagerReadRequest request=disk.createReadRequest(
                    piece.getPieceNumber(),b*com.biglybt.core.disk.DiskManager.BLOCK_SIZE,piece.getBlockSize(b));
                if (peers != null) for (com.biglybt.core.peer.PEPeer peer:peers) {
                    if (peer instanceof com.biglybt.core.peer.impl.PEPeerTransport
                        && ((com.biglybt.core.peer.impl.PEPeerTransport)peer).getRequestIndex(request)>=0) {
                        outstanding=true; break;
                    }
                }
            }
            if (!outstanding) { covered=false; break; }
        }
        return new Coverage(covered && !all,all);
    }

    public boolean due(int piece, long now, boolean covered) {
        if (piece != lastPiece) { lastPiece = piece; lastRequest = Long.MIN_VALUE; }
        if (piece < 0 || !covered) return false;
        if (lastRequest != Long.MIN_VALUE && now-lastRequest < 2000) return false;
        lastRequest = now;
        return true;
    }
}
