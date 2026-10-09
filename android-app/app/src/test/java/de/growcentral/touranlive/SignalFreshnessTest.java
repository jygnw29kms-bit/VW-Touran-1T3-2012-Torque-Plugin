package de.growcentral.touranlive;
import org.junit.Test;
import static org.junit.Assert.*;
public class SignalFreshnessTest {
    @Test public void receiptTimeControlsExpiryNotGatewayClock() {
        SignalFreshness values = new SignalFreshness();
        assertTrue(values.record("rpm", 9999999999L, 100));
        assertTrue(values.isFresh("rpm", 4599));
        assertFalse(values.isFresh("rpm", 4600));
        assertFalse(values.isFresh("unknown", 100));
    }
    @Test public void duplicateOrOlderFramesDoNotRefreshAge() {
        SignalFreshness values = new SignalFreshness();
        assertTrue(values.record("rpm", 100, 10));
        assertFalse(values.record("rpm", 100, 4000));
        assertFalse(values.record("rpm", 99, 4000));
        assertFalse(values.isFresh("rpm", 4510));
    }
    @Test public void reconnectAllowsRebootedGatewayTimestamps() {
        SignalFreshness values = new SignalFreshness();
        values.record("rpm", 10000, 100); values.clear();
        assertFalse(values.isFresh("rpm", 101));
        assertTrue(values.record("rpm", 1, 200));
        assertTrue(values.isFresh("rpm", 201));
    }
}
