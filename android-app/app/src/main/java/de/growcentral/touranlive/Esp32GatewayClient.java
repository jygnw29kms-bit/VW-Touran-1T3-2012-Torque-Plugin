package de.growcentral.touranlive;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import javax.net.SocketFactory;

/** Read-only NDJSON client. Reading and command writes have separate, session-scoped queues. */
public final class Esp32GatewayClient implements AutoCloseable {
    public static final String DEFAULT_HOST = "192.168.4.1";
    public static final int DEFAULT_PORT = 13569;
    private static final long HELLO_TIMEOUT_MS = 5000;
    private static final long IDLE_TIMEOUT_MS = 15000;
    private static final long HEALTH_INTERVAL_MS = 5000;

    public interface Listener {
        void onConnected(String board, String firmware, boolean readOnly);
        void onDisconnected(String reason);
        void onCanFrame(int channel, long timestampUs, long canId, boolean extended, byte[] data);
        void onValue(String name, double value, String unit, long timestampUs, String source);
        void onHealth(JSONObject health);
        void onProtocolError(String line, String reason);
    }

    private static final class Session {
        final SocketFactory factory;
        volatile boolean running = true, ready;
        volatile Socket socket;
        BufferedWriter writer;
        Session(SocketFactory factory) { this.factory = factory; }
        void stop() {
            running = false;
            ready = false;
            try { if (socket != null) socket.close(); } catch (IOException ignored) { }
            synchronized (this) { writer = null; }
        }
        synchronized void send(JSONObject command) throws IOException {
            if (!running || writer == null) return;
            writer.write(command.toString()); writer.write('\n'); writer.flush();
        }
    }

    private final Listener listener;
    private final ExecutorService reads = Executors.newSingleThreadExecutor();
    private final ExecutorService commands = Executors.newSingleThreadExecutor();
    private volatile Session active;
    private boolean closed;

    public Esp32GatewayClient(Listener listener) { this.listener = listener; }
    public boolean isConnected() { Session s = active; return s != null && s.running && s.ready; }
    public void connectWifi() { connectWifi(DEFAULT_HOST, DEFAULT_PORT); }
    public void connectWifi(String host, int port) { connectWifi(host, port, SocketFactory.getDefault()); }

    /** Allows Android to bind only this socket to the local Wi-Fi network, leaving Internet jobs alone. */
    public synchronized void connectWifi(String host, int port, SocketFactory factory) {
        if (closed || active != null) return;
        if (host == null || host.trim().isEmpty() || port < 1 || port > 65535 || factory == null) {
            listener.onDisconnected("invalid gateway endpoint"); return;
        }
        Session s = new Session(factory);
        active = s;
        reads.execute(() -> runTcp(s, host, port));
    }

    private static long nowMs() { return System.nanoTime() / 1000000L; }

    private void runTcp(Session s, String host, int port) {
        String reason = "connection closed";
        try {
            // Publish the socket before connect so cancellation also interrupts connection attempts.
            synchronized (s) {
                if (!s.running) return;
                s.socket = s.factory.createSocket();
            }
            Socket socket = s.socket;
            socket.setKeepAlive(true); socket.setTcpNoDelay(true); socket.setSoTimeout(1000);
            socket.connect(new InetSocketAddress(host, port), 2500);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                    StandardCharsets.UTF_8.newDecoder()))) {
                synchronized (s) {
                    if (!s.running) return;
                    s.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8));
                }
                s.send(new JSONObject().put("cmd", "hello"));
                long started = nowMs(), lastMessage = started, lastHealth = started;
                StringBuilder line = new StringBuilder();
                boolean oversized = false;
                while (s.running) {
                    long now = nowMs();
                    if (!s.ready && now - started >= HELLO_TIMEOUT_MS) throw new IOException("gateway hello timeout");
                    if (now - lastMessage >= IDLE_TIMEOUT_MS) throw new IOException("gateway response timeout");
                    if (s.ready && now - lastHealth >= HEALTH_INTERVAL_MS) {
                        s.send(new JSONObject().put("cmd", "health")); lastHealth = now;
                    }
                    int c;
                    try { c = reader.read(); } catch (SocketTimeoutException timeout) { continue; }
                    if (c < 0) break;
                    if (c == '\n') {
                        if (!oversized && line.length() > 0 && parseLine(s, line.toString())) lastMessage = nowMs();
                        line.setLength(0); oversized = false;
                    } else if (c != '\r' && !oversized) {
                        if (line.length() >= GatewayProtocol.MAX_LINE_CHARS) {
                            listener.onProtocolError("", "gateway line too long"); line.setLength(0); oversized = true;
                        } else line.append((char)c);
                    }
                }
            }
        } catch (Exception e) {
            reason = s.running ? safe(e.getMessage()) : "disconnected";
        } finally {
            s.stop();
            synchronized (this) { if (active == s) active = null; }
            listener.onDisconnected(reason);
        }
    }

    private boolean parseLine(Session s, String line) throws Exception {
        JSONObject o;
        try { o = GatewayProtocol.parse(line); }
        catch (Exception e) {
            listener.onProtocolError(line, safe(e.getMessage()));
            // A peer announcing incompatible identity/mode cannot establish a trusted session.
            String type = "";
            try { type = new JSONObject(line).optString("type"); } catch (Exception ignored) { }
            if ("hello".equals(type) || "health".equals(type)) throw new IOException("incompatible gateway: " + e.getMessage());
            return false;
        }
        String type = o.getString("type");
        if (!s.ready && !"hello".equals(type)) {
            listener.onProtocolError(line, "message before gateway hello"); return false;
        }
        if (!s.running) return false;
        switch (type) {
            case "hello":
                if (!s.ready) {
                    s.ready = true;
                    s.send(new JSONObject().put("cmd", "capture").put("enable", true));
                    listener.onConnected(o.getString("board"), o.getString("fw"), true);
                }
                break;
            case "can":
                listener.onCanFrame(o.getInt("ch"), o.getLong("ts_us"), o.getLong("id"),
                        o.getBoolean("ext"), GatewayProtocol.decodeHex(o.getString("data")));
                break;
            case "value":
                listener.onValue(o.getString("name"), o.getDouble("value"), o.getString("unit"),
                        o.getLong("ts_us"), o.getString("source"));
                break;
            case "health": listener.onHealth(o); break;
            case "error": listener.onProtocolError(line, o.getString("code") + ": " + o.getString("message")); break;
            default: break;
        }
        return true;
    }

    public void requestHealth() { sendAsync(command("health")); }
    public void setCapture(boolean enabled) {
        try { sendAsync(command("capture").put("enable", enabled)); }
        catch (Exception e) { listener.onProtocolError("capture", safe(e.getMessage())); }
    }
    public void setFilter(int channel, long... ids) {
        try {
            if (channel != 1 && channel != 2) throw new IllegalArgumentException("invalid CAN channel");
            JSONArray a = new JSONArray();
            if (ids != null) for (long id : ids) {
                if (id < 0 || id > 0x1fffffffL) throw new IllegalArgumentException("invalid CAN identifier");
                a.put(id);
            }
            sendAsync(command("filter").put("ch", channel).put("ids", a));
        } catch (Exception e) { listener.onProtocolError("filter", safe(e.getMessage())); }
    }
    private static JSONObject command(String name) {
        JSONObject o = new JSONObject();
        try { o.put("cmd", name); } catch (Exception e) { throw new IllegalStateException(e); }
        return o;
    }
    private void sendAsync(JSONObject command) {
        Session s = active;
        if (s == null || !s.ready || !s.running) return;
        try {
            commands.execute(() -> {
                if (active != s || !s.running || !s.ready) return;
                try { s.send(command); }
                catch (IOException e) { listener.onProtocolError(command.toString(), safe(e.getMessage())); s.stop(); }
            });
        } catch (RejectedExecutionException ignored) { /* client closed */ }
    }
    public void disconnect() { Session s = active; if (s != null) s.stop(); }
    @Override public synchronized void close() {
        closed = true; disconnect(); reads.shutdownNow(); commands.shutdownNow();
    }
    private static String safe(String s) { return s == null || s.trim().isEmpty() ? "unknown" : s.trim(); }
}
