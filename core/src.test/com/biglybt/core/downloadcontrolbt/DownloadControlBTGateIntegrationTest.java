package com.biglybt.core.downloadcontrolbt;

import static org.assertj.core.api.Assertions.assertThat;
import java.lang.reflect.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.biglybt.core.config.COConfigurationManager;
import com.biglybt.core.download.*;
import com.biglybt.core.disk.*;

/** Exercises the real facade/gate/queue together; fake disk exposes only hash-verification state. */
public class DownloadControlBTGateIntegrationTest {
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, InvocationHandler h) {
        return (T)Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (p,m,a) -> {
            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
            if (m.getName().equals("equals")) return p == a[0];
            Object r = h.invoke(p,m,a);
            if (r != null) return r;
            Class<?> rt=m.getReturnType();
            if (rt == boolean.class) return false;
            if (rt == long.class) return 0L;
            if (rt == int.class) return 0;
            return null;
        });
    }
    static class Fixture {
        final Map<String,Object> attrs=new HashMap<>();
        final Map<Object,Object> userData=new IdentityHashMap<>();
        final boolean[] done=new boolean[6];
        final boolean[] skipped=new boolean[2];
        final int[] priorities=new int[2];
        final DiskManagerFileInfo[] files=new DiskManagerFileInfo[2];
        final DiskManagerPiece[] pieces=new DiskManagerPiece[6];
        final DownloadManagerState state;
        final DiskManager disk;
        final DownloadManager dm;
        Fixture() {
            System.setProperty(com.biglybt.core.util.SystemProperties.SYSPROP_SECURITY_MANAGER_PERMITEXIT,"true");
            COConfigurationManager.setParameter(DownloadControlBTDefaults.CFG_PROFILE,true);
            attrs.put(DownloadManagerState.AT_DLCBT_ENABLED,true);
            attrs.put(DownloadManagerState.AT_DLCBT_POLICY,DownloadControlBT.POLICY_AUTO);
            attrs.put(DownloadManagerState.AT_DLCBT_MODE,DownloadControlBT.MODE_SEQUENTIAL);
            attrs.put(DownloadManagerState.AT_DLCBT_FILE_INDEX,0);
            state=proxy(DownloadManagerState.class,(p,m,a) -> {
                if (m.getName().startsWith("get") && a != null) return attrs.get(a[0]);
                if (m.getName().startsWith("set") && a != null && a.length==2) attrs.put((String)a[0],a[1]);
                return null;
            });
            for(int i=0;i<files.length;i++) {
                final int f=i;
                files[i]=proxy(DiskManagerFileInfo.class,(p,m,a) -> {
                    switch(m.getName()) {
                    case "getIndex": return f;
                    case "getLength": return 49152L;
                    case "getFirstPieceNumber": return f*3;
                    case "getLastPieceNumber": return f*3+2;
                    case "getNbPieces": return 3;
                    case "getPriority": return priorities[f];
                    case "isSkipped": return skipped[f];
                    case "setSkipped": skipped[f]=(Boolean)a[0]; return null;
                    }
                    return null;
                });
            }
            for(int i=0;i<pieces.length;i++) {
                final int piece=i;
                pieces[i]=proxy(DiskManagerPiece.class,(p,m,a) -> m.getName().equals("isDone") ? done[piece] : null);
            }
            disk=proxy(DiskManager.class,(p,m,a) -> m.getName().equals("getFiles") ? files : m.getName().equals("getPieces") ? pieces : null);
            dm=proxy(DownloadManager.class,(p,m,a) -> {
                switch(m.getName()) {
                case "getDownloadState": return state;
                case "getDiskManager": return disk;
                case "getUserData": return userData.get(a[0]);
                case "setUserData": userData.put(a[0],a[1]); return null;
                }
                return null;
            });
        }
        int allowed() { return DownloadControlBT.getAllowedPieceNumber(dm,disk); }
    }
    @Test public void priorityChangeInRealGateFinishesCurrentPieceThenSwitches() {
        Fixture f=new Fixture();
        assertThat(f.allowed()).isZero();
        f.priorities[1]=5;
        assertThat(f.allowed()).isZero();
        f.done[0]=true;
        assertThat(f.allowed()).isEqualTo(3);
        f.done[3]=f.done[4]=f.done[5]=true;
        assertThat(f.allowed()).isEqualTo(2); // resume first file: legacy sequential first,last,interiors
    }
    @Test public void realGateForwardSuffixIsSequentialAndThenQueuesNextFile() {
        Fixture f=new Fixture();
        f.skipped[0]=true;
        assertThat(DownloadControlBT.startForward(f.dm,0,1)).isTrue();
        assertThat(f.skipped[0]).isFalse();
        assertThat(f.allowed()).isEqualTo(1);
        f.done[1]=true;
        assertThat(f.allowed()).isEqualTo(2);
        f.done[2]=true;
        assertThat(f.done[0]).isFalse();
        assertThat(f.allowed()).isEqualTo(3);
        f.userData.clear(); // restored disk/download attributes, no in-memory progress
        assertThat(f.allowed()).isEqualTo(3);
        assertThat(DownloadControlBT.snapshotFiles(f.dm,f.disk)[0].firstPiece).isEqualTo(1);
        assertThat(DownloadControlBT.chooseFileMode(f.dm,0,DownloadControlBT.MODE_SEQUENTIAL)).isTrue();
        assertThat(DownloadControlBT.snapshotFiles(f.dm,f.disk)[0].firstPiece).isZero();
    }
    @Test public void uncheckedFileKeepsItsStoredModeLetter() {
        Fixture f=new Fixture();
        f.skipped[0]=true;
        assertThat(DownloadControlBT.getModeLetterForFile(f.dm,0)).isEqualTo("S");
        DownloadControlBT.setFileModeOverride(f.dm,0,DownloadControlBT.MODE_RANDOM);
        assertThat(DownloadControlBT.getModeLetterForFile(f.dm,0)).isEqualTo("R");
    }
    @Test public void rerequestCoverageIncludesRealPeerQueueAfterMarksWereCleared() {
        com.biglybt.core.peer.PEPiece piece=proxy(com.biglybt.core.peer.PEPiece.class,(p,m,a) -> {
            switch(m.getName()) {
            case "getNbBlocks": return 2;
            case "getPieceNumber": return 5;
            case "getBlockSize": return 16384;
            case "isDownloaded": return ((Integer)a[0])==0;
            }
            return null;
        });
        com.biglybt.core.peer.impl.PEPeerTransport peer=proxy(com.biglybt.core.peer.impl.PEPeerTransport.class,(p,m,a) -> {
            if(m.getName().equals("getRequestIndex")) {
                DiskManagerReadRequest r=(DiskManagerReadRequest)a[0];
                return r.getPieceNumber()==5 && r.getOffset()==16384 ? 0 : -1;
            }
            return null;
        });
        com.biglybt.core.peer.impl.PEPeerControl control=proxy(com.biglybt.core.peer.impl.PEPeerControl.class,(p,m,a) ->
            m.getName().equals("getPeers") ? java.util.Collections.singletonList(peer) : null);
        DiskManager disk=proxy(DiskManager.class,(p,m,a) -> m.getName().equals("createReadRequest")
            ? new com.biglybt.core.disk.impl.access.impl.DiskManagerReadRequestImpl((Integer)a[0],(Integer)a[1],(Integer)a[2]) : null);
        DownloadControlBTRerequest.Coverage c=DownloadControlBTRerequest.coverage(control,disk,piece);
        assertThat(c.covered).isTrue();
        assertThat(c.allArrived).isFalse();
        assertThat(DownloadControlBTRerequest.coverage(null,disk,piece).covered).isFalse();
    }
}
