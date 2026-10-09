package de.growcentral.touranlive;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class DebugTelemetryTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    @Test public void atomicQueueAndServerLimits() throws Exception {
        DebugTelemetry telemetry = new DebugTelemetry(temporary.newFolder());
        File queued = telemetry.queueSnapshot("raw-can", "CAN fixture");
        assertTrue(queued.isFile()); assertEquals(1, telemetry.pendingCount());
        String oversized = new String(new char[5 * 1024 * 1024 + 1]).replace('\0', 'x');
        assertThrows(IOException.class, () -> telemetry.queueSnapshot("log", oversized));
        assertEquals(1, telemetry.pendingCount());
    }
    @Test public void httpSuccessWithoutAcknowledgementKeepsReport() throws Exception {
        DebugTelemetry telemetry = new DebugTelemetry(temporary.newFolder());
        telemetry.queueSnapshot("log", "test");
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(200).setBody("<html>not an upload acknowledgement</html>"));
            server.start();
            DebugTelemetry.Result result = telemetry.flush(server.url("/").toString(), "test", "test");
            assertEquals(0, result.sent); assertEquals(1, result.pending);
        }
    }
    @Test public void failedRequestRemainsAvailableForRetry() throws Exception {
        DebugTelemetry telemetry = new DebugTelemetry(temporary.newFolder());
        telemetry.queueSnapshot("raw-can", "test capture");
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(503).setBody("unavailable"));
            server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"ok\":true}"));
            server.start();
            assertEquals(1, telemetry.flush(server.url("/").toString(), "test", "test").pending);
            assertEquals(1, telemetry.flush(server.url("/").toString(), "test", "test").sent);
            assertEquals(0, telemetry.pendingCount());
            assertEquals("raw-can", server.takeRequest().getHeader("X-Touran-Report"));
        }
    }
    @Test public void concurrentFlushesSendEachReportOnlyOnce() throws Exception {
        DebugTelemetry telemetry = new DebugTelemetry(temporary.newFolder());
        telemetry.queueSnapshot("log", "test");
        try (MockWebServer server = new MockWebServer()) {
            server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"ok\" : true}").setBodyDelay(100, TimeUnit.MILLISECONDS));
            server.start(); ExecutorService pool = Executors.newFixedThreadPool(2);
            try {
                String url = server.url("/").toString();
                Future<?> a = pool.submit(() -> telemetry.flush(url, "test", "test"));
                Future<?> b = pool.submit(() -> telemetry.flush(url, "test", "test"));
                a.get(5, TimeUnit.SECONDS); b.get(5, TimeUnit.SECONDS);
                assertEquals(1, server.getRequestCount()); assertEquals(0, telemetry.pendingCount());
            } finally { pool.shutdownNow(); }
        }
    }
}
