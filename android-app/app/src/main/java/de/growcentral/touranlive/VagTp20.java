package de.growcentral.touranlive;

import java.io.BufferedReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Minimal read-only VW TP2.0 + KWP2000 measuring-block client for ELM327.
 * It only opens address 01 and reads measuring blocks (service 0x21).
 * No coding, adaptation, basic settings, writes or security access are implemented.
 */
final class VagTp20 {
    interface LogSink { void log(String state, String detail); }

    static final class Cell {
        final int formula;
        final int a;
        final int b;
        final Double value;
        final String unit;
        Cell(int formula, int a, int b, Double value, String unit) {
            this.formula = formula; this.a = a; this.b = b; this.value = value; this.unit = unit;
        }
    }

    static final class Block {
        final int group;
        final Cell[] cells;
        final String raw;
        Block(int group, Cell[] cells, String raw) {
            this.group = group; this.cells = cells; this.raw = raw;
        }
        Double value(int field) {
            return field >= 1 && field <= cells.length ? cells[field - 1].value : null;
        }
        String unit(int field) {
            return field >= 1 && field <= cells.length ? cells[field - 1].unit : "";
        }
    }

    private final BufferedReader reader;
    private final OutputStream writer;
    private final LogSink log;
    private int txId = -1;
    private int rxId = -1;
    private int txSeq = 0;
    private int rxSeq = 0;
    private boolean open = false;

    VagTp20(BufferedReader reader, OutputStream writer, LogSink log) {
        this.reader = reader; this.writer = writer; this.log = log;
    }

    boolean openEngine() {
        try {
            cmd("ATZ", 1800);
            requireOk("ATE0");
            requireOk("ATL1");
            requireOk("ATS1");
            requireOk("ATH1");
            requireOk("ATD1");
            cmd("ATCAF0", 600);
            cmd("ATCFC0", 600);
            requireOk("AT PB C0 01");       // user protocol B: 500 kbit/s, 11-bit CAN
            requireOk("AT SP B");
            cmd("ATST18", 600);             // 96 ms receive timeout

            requireOk("ATSH200");
            requireOk("ATCRA201");
            List<byte[]> setupFrames = frames(cmd("01 C0 00 10 00 03 01", 900));
            byte[] setup = firstData(setupFrames, 7);
            if (setup == null || setup.length < 7 || (setup[1] & 0xFF) != 0xD0) {
                throw new Exception("TP2.0 Kanalaufbau ohne D0-Antwort");
            }
            rxId = ((setup[3] & 0x0F) << 8) | (setup[2] & 0xFF);
            txId = ((setup[5] & 0x0F) << 8) | (setup[4] & 0xFF);
            if (rxId <= 0 || txId <= 0) throw new Exception("TP2.0 Kanal-IDs unplausibel");

            requireOk(String.format(Locale.ROOT, "ATSH%03X", txId));
            requireOk(String.format(Locale.ROOT, "ATCRA%03X", rxId));
            byte[] p = firstData(frames(cmd("A0 0F 8A FF 4A FF", 900)), 6);
            if (p == null || p.length < 6 || (p[0] & 0xFF) != 0xA1) {
                throw new Exception("TP2.0 Parameter-Antwort A1 fehlt");
            }

            txSeq = 0; rxSeq = 0;
            byte[] diag = sendKwp(new byte[]{0x10, (byte)0x89});
            if (diag == null || diag.length < 2 || (diag[0] & 0xFF) != 0x50 || (diag[1] & 0xFF) != 0x89) {
                throw new Exception("KWP2000 Diagnosesitzung 0x89 nicht bestätigt");
            }
            open = true;
            log.log("VAG_TP20_OPEN", String.format(Locale.ROOT, "tx=%03X rx=%03X KWP=0x89", txId, rxId));
            return true;
        } catch (Exception e) {
            log.log("VAG_TP20_FAIL", e.getMessage() == null ? e.toString() : e.getMessage());
            try { close(); } catch (Exception ignored) {}
            return false;
        }
    }

    boolean isOpen() { return open; }

    Block readBlock(int group) throws Exception {
        if (!open) throw new Exception("VAG-Kanal nicht offen");
        if (group < 0 || group > 255) throw new Exception("Messwertblock außerhalb 0..255");
        byte[] payload = sendKwp(new byte[]{0x21, (byte)group});
        if (payload == null || payload.length < 2) throw new Exception("Leere KWP-Antwort");
        if ((payload[0] & 0xFF) == 0x7F) {
            int reason = payload.length > 2 ? payload[2] & 0xFF : -1;
            throw new Exception(String.format(Locale.ROOT, "KWP negative Antwort Gruppe %d, NRC=%02X", group, reason));
        }
        if ((payload[0] & 0xFF) != 0x61 || (payload[1] & 0xFF) != group) {
            throw new Exception("Unerwartete KWP-Antwort für Gruppe " + group);
        }
        int count = Math.min(4, (payload.length - 2) / 3);
        Cell[] cells = new Cell[count];
        StringBuilder raw = new StringBuilder();
        for (byte x : payload) raw.append(String.format(Locale.ROOT, "%02X", x & 0xFF));
        for (int i=0; i<count; i++) {
            int f = payload[2 + i*3] & 0xFF;
            int a = payload[3 + i*3] & 0xFF;
            int b = payload[4 + i*3] & 0xFF;
            cells[i] = decode(f, a, b);
        }
        return new Block(group, cells, raw.toString());
    }

    void keepAlive() {
        if (!open) return;
        try { cmd("A3", 500); } catch (Exception e) { log.log("VAG_KEEPALIVE_FAIL", e.getMessage()); }
    }

    void close() {
        try { if (txId > 0) cmd("A8", 300); } catch (Exception ignored) {}
        open = false; txId = -1; rxId = -1; txSeq = 0; rxSeq = 0;
    }

    private byte[] sendKwp(byte[] payload) throws Exception {
        int len = payload.length;
        if (len > 7) throw new Exception("KWP request zu lang");
        byte[] tp = new byte[len + 3];
        tp[0] = (byte)(0x10 | (txSeq & 0x0F));
        tp[1] = (byte)((len >> 8) & 0xFF);
        tp[2] = (byte)(len & 0xFF);
        System.arraycopy(payload, 0, tp, 3, len);
        txSeq = (txSeq + 1) & 0x0F;

        String raw = cmd(hex(tp), 1100);
        List<byte[]> fs = frames(raw);
        int index = 0;
        if (index < fs.size() && ((fs.get(index)[0] >> 4) & 0x0F) == 0x0B) index++;

        List<Byte> out = new ArrayList<>();
        int expected = -1;
        boolean first = true;
        boolean last = false;
        while (!last) {
            if (index >= fs.size()) throw new Exception("TP2.0 Antwort unvollständig");
            byte[] f = fs.get(index++);
            if (f.length == 0) continue;
            int op = (f[0] >> 4) & 0x0F;
            int seq = f[0] & 0x0F;
            if (op > 3) continue;
            if (seq != (rxSeq & 0x0F)) rxSeq = seq;
            rxSeq = (rxSeq + 1) & 0x0F;
            int pos;
            if (first) {
                if (f.length < 3) throw new Exception("TP2.0 erster Frame zu kurz");
                expected = ((f[1] & 0xFF) << 8) | (f[2] & 0xFF);
                expected &= 0x7FFF;
                pos = 3;
                first = false;
            } else pos = 1;
            for (int i=pos; i<f.length && out.size() < expected; i++) out.add(f[i]);
            last = (op & 0x01) != 0 || (expected >= 0 && out.size() >= expected);

            if ((op & 0x02) == 0) {
                String ackRaw = cmd(String.format(Locale.ROOT, "%02X", 0xB0 | (rxSeq & 0x0F)), last ? 350 : 850);
                if (!last) {
                    fs = frames(ackRaw);
                    index = 0;
                }
            }
        }
        byte[] result = new byte[Math.min(out.size(), Math.max(0, expected))];
        for (int i=0;i<result.length;i++) result[i]=out.get(i);
        return result;
    }

    private Cell decode(int id, int a, int b) {
        Double v = null; String u = "raw";
        switch (id) {
            case 0x01: v = a * b / 5.0; u = "rpm"; break;
            case 0x04: v = Math.abs(b - 127) * 0.01 * a; u = b > 127 ? "° n.OT" : "° v.OT"; break;
            case 0x07: v = 0.01 * a * b; u = "km/h"; break;
            case 0x12: v = a * b / 25.0; u = "mbar"; break;
            case 0x14: v = a * b / 128.0 - 1.0; u = "%"; break;
            case 0x15: v = a * b / 1000.0; u = "V"; break;
            case 0x16: v = 0.001 * a * b; u = "ms"; break;
            case 0x17: v = a * b / 256.0; u = "%"; break;
            case 0x19: v = a == 0 ? null : (100.0 / a) * b; u = "g/s"; break;
            case 0x1A: v = (double)(b - a); u = "°C"; break;
            case 0x21: v = a == 0 ? 100.0 * b : 100.0 * b / a; u = "%"; break;
            case 0x22: v = (b - 128) * 0.01 * a; u = "kW"; break;
            case 0x23: v = a * b / 100.0; u = "l/h"; break;
            case 0x27: v = a * b / 256.0; u = "mg/stk"; break;
            case 0x31: v = a * b / 40.0; u = "mg/stk"; break;
            case 0x33: v = ((b - 128) / 255.0) * a; u = "mg/stk Δ"; break;
            case 0x36: v = (double)((a << 8) | b); u = "count"; break;
            case 0x37: v = a * b / 200.0; u = "s"; break;
            case 0x5E: v = a * (b / 50.0 - 1.0); u = "Nm"; break;
            default: v = (double)((a << 8) | b); u = "raw"; break;
        }
        return new Cell(id, a, b, v, u);
    }

    private void requireOk(String command) throws Exception {
        String r = cmd(command, 700).toUpperCase(Locale.ROOT);
        if (!r.contains("OK")) throw new Exception(command + " -> " + r.replace('\r',' ').replace('\n',' '));
    }

    private String cmd(String command, long timeoutMs) throws Exception {
        while (reader.ready()) reader.read();
        writer.write((command + "\r").getBytes(StandardCharsets.US_ASCII));
        writer.flush();
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (reader.ready()) {
                int c = reader.read();
                if (c < 0) break;
                char ch = (char)c;
                if (ch == '>') break;
                sb.append(ch);
            } else Thread.sleep(2);
        }
        String result = sb.toString();
        log.log("VAG_IO", command + " => " + result.replace('\r',' ').replace('\n',' ').trim());
        return result;
    }

    private static String hex(byte[] data) {
        StringBuilder s = new StringBuilder();
        for (int i=0;i<data.length;i++) {
            if (i>0) s.append(' ');
            s.append(String.format(Locale.ROOT, "%02X", data[i] & 0xFF));
        }
        return s.toString();
    }

    private static List<byte[]> frames(String raw) {
        List<byte[]> out = new ArrayList<>();
        if (raw == null) return out;
        for (String line : raw.split("[\\r\\n]+")) {
            String t = line.trim().toUpperCase(Locale.ROOT);
            if (t.isEmpty() || t.contains("NO DATA") || t.contains("ERROR") || t.contains("OK")) continue;
            String[] parts = t.split("\\s+");
            try {
                int start = 0;
                if (parts.length >= 3 && parts[0].matches("[0-9A-F]{3}")) {
                    start = 1;
                    if (parts[start].matches("[0-9A-F]{1,2}")) start++;
                }
                List<Byte> bytes = new ArrayList<>();
                for (int i=start;i<parts.length;i++) {
                    if (parts[i].matches("[0-9A-F]{2}")) bytes.add((byte)Integer.parseInt(parts[i],16));
                }
                if (bytes.isEmpty()) {
                    String h=t.replaceAll("[^0-9A-F]","");
                    if ((h.length() & 1)==0) for(int i=0;i<h.length();i+=2) bytes.add((byte)Integer.parseInt(h.substring(i,i+2),16));
                }
                if (!bytes.isEmpty()) {
                    byte[] f=new byte[bytes.size()]; for(int i=0;i<f.length;i++) f[i]=bytes.get(i); out.add(f);
                }
            } catch (Exception ignored) {}
        }
        return out;
    }

    private static byte[] firstData(List<byte[]> frames, int minLen) {
        for (byte[] f : frames) if (f.length >= minLen) return f;
        return null;
    }
}
