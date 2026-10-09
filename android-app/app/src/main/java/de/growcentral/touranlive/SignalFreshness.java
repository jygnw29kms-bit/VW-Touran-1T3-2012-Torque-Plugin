package de.growcentral.touranlive;

import java.util.HashMap;
import java.util.Map;

/** Keeps ESP monotonic timestamps separate from Android receipt time. Clear on each connection. */
final class SignalFreshness {
    static final long MAX_AGE_MS = 4500;
    private final Map<String, Long> timestamps = new HashMap<>();
    private final Map<String, Long> received = new HashMap<>();

    boolean record(String name, long timestampUs, long receiptMs) {
        Long previous = timestamps.get(name);
        if (previous != null && timestampUs <= previous) return false;
        timestamps.put(name, timestampUs);
        received.put(name, receiptMs);
        return true;
    }

    boolean isFresh(String name, long nowMs) {
        Long at = received.get(name);
        return at != null && nowMs >= at && nowMs - at < MAX_AGE_MS;
    }

    void clear() { timestamps.clear(); received.clear(); }
}
