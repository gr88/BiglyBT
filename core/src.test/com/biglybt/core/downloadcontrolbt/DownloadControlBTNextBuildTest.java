package com.biglybt.core.downloadcontrolbt;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import com.biglybt.core.peer.impl.PEPeerTransport;

public class DownloadControlBTNextBuildTest {
    private DownloadControlFileQueue.FileSnap file(int index, int priority) {
        return new DownloadControlFileQueue.FileSnap(index, 100, index*10, index*10+9, 10, priority, false);
    }
    @Test public void higherPriorityWaitsUntilCurrentPieceIsHashVerified() {
        DownloadControlFileQueue.FileSnap[] files = {file(0, 0), file(1, 5)};
        assertThat(DownloadControlFileQueue.pickAtPieceBoundary(files, 0, p -> false, 3)).isEqualTo(0);
        // Received bytes alone are not enough. Only the verified view opens the boundary.
        assertThat(DownloadControlFileQueue.pickAtPieceBoundary(files, 0, p -> p==3, 3)).isEqualTo(1);
    }
    @Test public void equalPriorityKeepsPinnedFileEvenAtPieceBoundary() {
        DownloadControlFileQueue.FileSnap[] files = {file(0, 0), file(1, 0)};
        assertThat(DownloadControlFileQueue.pickAtPieceBoundary(files, 1, p -> p==14, 14)).isEqualTo(1);
    }
    @Test public void preemptionReturnsToUnfinishedFileInPriorityOrder() {
        DownloadControlFileQueue.FileSnap[] files = {file(0, 0), file(1, 5)};
        assertThat(DownloadControlFileQueue.pickAtPieceBoundary(files, 1, p -> p>=10, 19)).isEqualTo(0);
    }
    @Test public void forwardOrderDoesNotVisitLastOrEarlierPiecesFirst() {
        assertThat(DownloadControlFileQueue.forwardOrder(7, 10)).containsExactly(7,8,9,10);
        assertThat(DownloadControlFileQueue.forwardOrder(10, 10)).containsExactly(10);
        assertThat(DownloadControlFileQueue.forwardOrder(11, 10)).isEmpty();
    }
    @Test public void forwardSuffixCompletionAllowsNextFileDespiteMissingEarlierPieces() {
        DownloadControlFileQueue.FileSnap[] files = {
            new DownloadControlFileQueue.FileSnap(0,100,7,9,10,0,false), file(1,0)};
        assertThat(DownloadControlFileQueue.pickAtPieceBoundary(files,0,p -> p>=7 && p<=9,9)).isEqualTo(1);
    }
    @Test public void handshakeTimeoutUsesExactlyPeersViewStateAndStrictThreeSeconds() {
        assertThat(DownloadControlBTBanService.handshakeOverdue(PEPeerTransport.CONNECTION_PENDING,0,10000)).isFalse();
        assertThat(DownloadControlBTBanService.handshakeOverdue(PEPeerTransport.CONNECTION_CONNECTING,0,10000)).isFalse();
        assertThat(DownloadControlBTBanService.handshakeOverdue(PEPeerTransport.CONNECTION_FULLY_ESTABLISHED,0,10000)).isFalse();
        assertThat(DownloadControlBTBanService.handshakeOverdue(PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE,1000,4000)).isFalse();
        assertThat(DownloadControlBTBanService.handshakeOverdue(PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE,1000,4001)).isTrue();
    }
    @Test public void receivedPlusRequestedMustCoverEveryBlock() {
        assertThat(DownloadControlBTRerequest.fullyCovered(new boolean[]{true,false}, new boolean[]{false,true})).isTrue();
        assertThat(DownloadControlBTRerequest.fullyCovered(new boolean[]{true,false}, new boolean[]{false,false})).isFalse();
        assertThat(DownloadControlBTRerequest.fullyCovered(new boolean[]{true,true}, new boolean[]{true,true})).isFalse();
        assertThat(DownloadControlBTRerequest.fullyCovered(new boolean[0], new boolean[0])).isFalse();
    }
    @Test public void rerequestImmediatelyThenEveryTwoSecondsOnlyOnSameCoveredPiece() {
        DownloadControlBTRerequest s = new DownloadControlBTRerequest();
        assertThat(s.due(1,0,false)).isFalse();
        assertThat(s.due(1,100,true)).isTrue();
        assertThat(s.due(1,2099,true)).isFalse();
        assertThat(s.due(1,2100,true)).isTrue();
        assertThat(s.due(1,4100,false)).isFalse();
        assertThat(s.due(1,4101,true)).isTrue();
        assertThat(s.due(2,4200,true)).isTrue();
        assertThat(s.due(-1,7000,true)).isFalse();
    }
    @Test public void webseedLimitIsPerPeerAndFollowsScheduleUpdates() {
        java.util.concurrent.atomic.AtomicInteger limit = new java.util.concurrent.atomic.AtomicInteger(257126);
        DownloadControlBTPeerRateLimiter a = new DownloadControlBTPeerRateLimiter(limit::get);
        DownloadControlBTPeerRateLimiter b = new DownloadControlBTPeerRateLimiter(limit::get);
        assertThat(a).isNotSameAs(b); // separate identities -> separate network buckets
        assertThat(a.getRateLimitBytesPerSecond()).isEqualTo(257126);
        assertThat(b.getRateLimitBytesPerSecond()).isEqualTo(257126);
        limit.set(102400);
        assertThat(a.getRateLimitBytesPerSecond()).isEqualTo(102400);
        assertThat(b.getRateLimitBytesPerSecond()).isEqualTo(102400);
        a.setRateLimitBytesPerSecond(5);
        assertThat(a.getRateLimitBytesPerSecond()).isEqualTo(102400);
    }
    @Test public void scheduleIgnoresCommentedExampleRates() {
        assertThat(DownloadControlBTRateFormat.peerDownToken(java.util.Arrays.asList(
            "# peer_set Example=All,peer_down=1k", "  // peer_down=2k", "peer_set DownPeers=All,peer_down=251.1k"))).isEqualTo("251.1k");
    }
    @Test public void handshakeTriggerCreatesSameTimedExactAddressBanAndNotifiesAllTorrents() {
        System.setProperty(com.biglybt.core.util.SystemProperties.SYSPROP_SECURITY_MANAGER_PERMITEXIT,"true");
        com.biglybt.core.config.COConfigurationManager.setParameter(DownloadControlBTDefaults.CFG_PROFILE,true);
        com.biglybt.core.config.COConfigurationManager.setParameter(DownloadControlBTBanService.CFG_ENABLED,true);
        DownloadControlBTBanService service=DownloadControlBTBanService.get();
        java.util.concurrent.atomic.AtomicInteger closedA=new java.util.concurrent.atomic.AtomicInteger();
        java.util.concurrent.atomic.AtomicInteger closedB=new java.util.concurrent.atomic.AtomicInteger();
        DownloadControlBTBanService.BanListener a=ban -> closedA.incrementAndGet();
        DownloadControlBTBanService.BanListener b=ban -> closedB.incrementAndGet();
        service.addListener(a); service.addListener(b);
        try {
            DownloadControlBTBanRegistry.Ban ban=service.onHandshakeTimeout("192.0.2.80",PEPeerTransport.CONNECTION_WAITING_FOR_HANDSHAKE,100,3101);
            assertThat(ban).isNotNull();
            assertThat(ban.reason).isEqualTo("handshake-timeout");
            assertThat(ban.expiryMs-ban.startMs).isBetween(54L*60000,64L*60000);
            assertThat(service.isTemporarilyBanned("::ffff:192.0.2.80")).isTrue();
            assertThat(service.isTemporarilyBanned("192.0.2.81")).isFalse();
            assertThat(closedA.get()).isEqualTo(1);
            assertThat(closedB.get()).isEqualTo(1);
        } finally { service.removeListener(a); service.removeListener(b); service.unbanOwn("192.0.2.80"); }
    }
}
