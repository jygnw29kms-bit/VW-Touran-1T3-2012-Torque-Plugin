package de.growcentral.touranlive;

import org.json.JSONObject;
import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class Esp32GatewayClientTest {
    private static class Events implements Esp32GatewayClient.Listener {
        final CountDownLatch connected = new CountDownLatch(1), disconnected = new CountDownLatch(1), frame = new CountDownLatch(1);
        final BlockingQueue<String> errors = new LinkedBlockingQueue<>();
        final AtomicInteger connects = new AtomicInteger();
        public void onConnected(String board, String fw, boolean readOnly) { assertTrue(readOnly); connects.incrementAndGet(); connected.countDown(); }
        public void onDisconnected(String reason) { disconnected.countDown(); }
        public void onCanFrame(int ch, long ts, long id, boolean ext, byte[] bytes) { assertEquals(8, bytes.length); frame.countDown(); }
        public void onValue(String name, double value, String unit, long ts, String source) { }
        public void onHealth(JSONObject health) { }
        public void onProtocolError(String line, String reason) { errors.add(reason); }
    }
    private static void send(BufferedWriter writer, String line) throws IOException { writer.write(line); writer.write('\n'); writer.flush(); }

    @Test public void commandsRunWhileReaderWaitsAndDisconnectClosesSocket() throws Exception {
        ExecutorService serverThread = Executors.newSingleThreadExecutor();
        try (ServerSocket server = new ServerSocket(0); Esp32GatewayClient client = new Esp32GatewayClient(new Events())) {
            CountDownLatch paused = new CountDownLatch(1), health = new CountDownLatch(1);
            Future<Boolean> peer = serverThread.submit(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(4000);
                    BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    send(out, GatewayProtocolTest.HELLO);
                    String line;
                    while ((line = in.readLine()) != null) {
                        JSONObject command = new JSONObject(line);
                        if ("health".equals(command.optString("cmd"))) { health.countDown(); send(out, GatewayProtocolTest.HEALTH); }
                        if ("capture".equals(command.optString("cmd")) && !command.getBoolean("enable")) paused.countDown();
                    }
                    return true;
                }
            });
            client.connectWifi("127.0.0.1", server.getLocalPort());
            long limit = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (!client.isConnected() && System.nanoTime() < limit) Thread.sleep(5);
            assertTrue(client.isConnected());
            client.requestHealth(); client.setCapture(false);
            assertTrue("Health command was stuck behind reader", health.await(2, TimeUnit.SECONDS));
            assertTrue("Capture command was stuck behind reader", paused.await(2, TimeUnit.SECONDS));
            client.disconnect();
            assertTrue(peer.get(2, TimeUnit.SECONDS));
            assertFalse(client.isConnected());
        } finally { serverThread.shutdownNow(); }
    }

    @Test public void oversizedLineIsDiscardedAndNextFrameRemainsIntact() throws Exception {
        ExecutorService thread = Executors.newSingleThreadExecutor(); Events events = new Events();
        try (ServerSocket server = new ServerSocket(0); Esp32GatewayClient client = new Esp32GatewayClient(events)) {
            Future<?> peer = thread.submit(() -> {
                try (Socket socket = server.accept()) {
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    send(out, GatewayProtocolTest.HELLO);
                    send(out, new String(new char[9000]).replace('\0', 'x'));
                    send(out, GatewayProtocolTest.CAN);
                    while (socket.getInputStream().read() != -1) { }
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            client.connectWifi("127.0.0.1", server.getLocalPort());
            assertTrue(events.frame.await(3, TimeUnit.SECONDS));
            assertEquals("gateway line too long", events.errors.poll(1, TimeUnit.SECONDS));
            client.disconnect(); peer.get(2, TimeUnit.SECONDS);
        } finally { thread.shutdownNow(); }
    }

    @Test public void incompatiblePeerCannotEstablishConnectedSession() throws Exception {
        ExecutorService thread = Executors.newSingleThreadExecutor(); Events events = new Events();
        try (ServerSocket server = new ServerSocket(0); Esp32GatewayClient client = new Esp32GatewayClient(events)) {
            Future<?> peer = thread.submit(() -> {
                try (Socket socket = server.accept()) {
                    BufferedWriter out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                    send(out, GatewayProtocolTest.HELLO.replace("readonly\":true", "readonly\":false"));
                } catch (IOException e) { throw new UncheckedIOException(e); }
            });
            client.connectWifi("127.0.0.1", server.getLocalPort());
            assertTrue(events.disconnected.await(3, TimeUnit.SECONDS));
            assertEquals(0, events.connects.get()); assertFalse(client.isConnected()); peer.get();
        } finally { thread.shutdownNow(); }
    }

    @Test public void silentTcpPeerIsNotConnectedAndHandshakeTimesOut() throws Exception {
        ExecutorService thread = Executors.newSingleThreadExecutor(); Events events = new Events();
        try (ServerSocket server = new ServerSocket(0); Esp32GatewayClient client = new Esp32GatewayClient(events)) {
            Future<?> peer = thread.submit(() -> {
                try (Socket socket = server.accept()) { while (socket.getInputStream().read() != -1) { } }
                catch (IOException e) { throw new UncheckedIOException(e); }
            });
            client.connectWifi("127.0.0.1", server.getLocalPort());
            assertTrue(events.disconnected.await(7, TimeUnit.SECONDS));
            assertEquals(0, events.connects.get()); assertFalse(client.isConnected()); peer.get(2, TimeUnit.SECONDS);
        } finally { thread.shutdownNow(); }
    }
}
