package com.biglybt.core.downloadcontrolbt;

import java.util.function.IntSupplier;
import com.biglybt.pif.network.RateLimiter;

/** One instance per HTTP peer: each gets its own token bucket in the network manager. */
public final class DownloadControlBTPeerRateLimiter implements RateLimiter {
    private final IntSupplier rate;
    public DownloadControlBTPeerRateLimiter() { this(DownloadControlBTRateFormat::schedulePeerDown); }
    DownloadControlBTPeerRateLimiter(IntSupplier rate) { this.rate = rate; }
    public String getName() { return "DLCBT webseed peer_down"; }
    public int getRateLimitBytesPerSecond() { return rate.getAsInt(); }
    public void setRateLimitBytesPerSecond(int value) { /* schedule owns this limit */ }
    public long getRateLimitTotalByteCount() { return 0; }
}
