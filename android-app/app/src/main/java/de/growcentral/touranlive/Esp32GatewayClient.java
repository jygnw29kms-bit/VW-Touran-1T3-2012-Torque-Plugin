package de.growcentral.touranlive;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transport client for the dedicated Waveshare ESP32-S3-CAN-2CH-U gateway.
 *
 * The application no longer needs to know ELM327 commands or Bluetooth SPP.
 * Every gateway transport carries the same NDJSON protocol; Wi-Fi/TCP is the
 * first implemented transport and USB CDC / BLE can plug into the same parser.
 */
public final class Esp32GatewayClient {
    public static final String DEFAULT_HOST = "192.168.4.1";
    public static final int DEFAULT_PORT = 13569;

    public interface Listener {
        void onConnected(String board, String firmware, boolean readOnly);
        void onDisconnected(String reason);
        void onCanFrame(int channel, long timestampUs, long canId, boolean extended, byte[] data);
        void onValue(String name, double value, String unit, long timestampUs, String source);
        void onHealth(JSONObject health);
        void onProtocolError(String line, String reason);
    }

    private final Listener listener;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Socket socket;
    private volatile BufferedWriter writer;

    public Esp32GatewayClient(Listener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        Socket s = socket;
        return running.get() && s != null && s.isConnected() && !s.isClosed();
    }

    public void connectWifi() {
        connectWifi(DEFAULT_HOST, DEFAULT_PORT);
    }

    public void connectWifi(String host, int port) {
        if (!running.compareAndSet(false, true)) return;
        io.execute(() -> runTcp(host, port));
    }

    private void runTcp(String host, int port) {
        String disconnectReason = "connection closed";
        try {
            Socket s = new Socket();
            s.setKeepAlive(true);
            s.setTcpNoDelay(true);
            s.connect(new InetSocketAddress(host, port), 2500);
            socket = s;

            BufferedReader reader = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8));
            sendCommand(new JSONObject().put("cmd", "hello"));
            sendCommand(new JSONObject().put("cmd", "capture").put("enable", true));

            String line;
            while (running.get() && (line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                parseLine(line.trim());
            }
        } catch (Exception e) {
            disconnectReason = safe(e.getMessage());
        } finally {
            closeSocket();
            running.set(false);
            listener.onDisconnected(disconnectReason);
        }
    }

    public void requestHealth() {
        sendAsync(new JSONObjectBuilder().put("cmd", "health").build());
    }

    public void setCapture(boolean enabled) {
        sendAsync(new JSONObjectBuilder().put("cmd", "capture").put("enable", enabled).build());
    }

    public void setFilter(int channel, long... ids) {
        try {
            JSONArray a = new JSONArray();
            if (ids != null) for (long id : ids) a.put(id);
            JSONObject o = new JSONObject();
            o.put("cmd", "filter");
            o.put("ch", channel);
            o.put("ids", a);
            sendAsync(o);
        } catch (Exception e) {
            listener.onProtocolError("filter", safe(e.getMessage()));
        }
    }

    private void sendAsync(JSONObject command) {
        io.execute(() -> {
            try {
                sendCommand(command);
            } catch (Exception e) {
                listener.onProtocolError(command.toString(), safe(e.getMessage()));
            }
        });
    }

    private synchronized void sendCommand(JSONObject command) throws Exception {
        BufferedWriter w = writer;
        if (w == null) return;
        w.write(command.toString());
        w.write('\n');
        w.flush();
    }

    private void parseLine(String line) {
        try {
            JSONObject o = new JSONObject(line);
            String type = o.optString("type", "");
            switch (type) {
                case "hello":
                    listener.onConnected(o.optString("board", "ESP32-S3-CAN-2CH-U"),
                            o.optString("fw", "unknown"), o.optBoolean("readonly", true));
                    break;
                case "can":
                    listener.onCanFrame(o.optInt("ch", 0), o.optLong("ts_us", 0L),
                            o.optLong("id", 0L), o.optBoolean("ext", false),
                            decodeHex(o.optString("data", "")));
                    break;
                case "value":
                    listener.onValue(o.optString("name", ""), o.optDouble("value", Double.NaN),
                            o.optString("unit", ""), o.optLong("ts_us", 0L), o.optString("source", ""));
                    break;
                case "health":
                    listener.onHealth(o);
                    break;
                default:
                    listener.onProtocolError(line, "unknown type: " + type);
                    break;
            }
        } catch (Exception e) {
            listener.onProtocolError(line, safe(e.getMessage()));
        }
    }

    public void disconnect() {
        running.set(false);
        closeSocket();
    }

    private synchronized void closeSocket() {
        writer = null;
        Socket s = socket;
        socket = null;
        try { if (s != null) s.close(); } catch (Exception ignored) {}
    }

    private static byte[] decodeHex(String hex) {
        String s = hex == null ? "" : hex.replaceAll("[^0-9A-Fa-f]", "");
        if ((s.length() & 1) != 0) s = "0" + s;
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    private static String safe(String s) {
        return s == null || s.trim().isEmpty() ? "unknown" : s.trim();
    }

    /** Tiny checked-exception-free helper used for fixed commands. */
    private static final class JSONObjectBuilder {
        private final JSONObject o = new JSONObject();
        JSONObjectBuilder put(String key, Object value) {
            try { o.put(key, value); } catch (Exception ignored) {}
            return this;
        }
        JSONObject build() { return o; }
    }
}
