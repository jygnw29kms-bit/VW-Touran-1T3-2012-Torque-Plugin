package de.growcentral.touranlive;

import android.content.Context;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/** Persistent daily telemetry with crash-safe offline upload queue. */
public final class DebugTelemetry {
    public static final class Result {
        public final int sent, pending;
        public final String lastResponse;
        Result(int sent, int pending, String lastResponse) {
            this.sent = sent; this.pending = pending; this.lastResponse = lastResponse;
        }
    }

    private static final class QueueMeta {
        final String report, session, day;
        QueueMeta(String report, String session, String day) {
            this.report=report; this.session=session; this.day=day;
        }
    }

    private final File root;
    private final File queueDir;
    private final String sessionId = UUID.randomUUID().toString();
    private final Object lock = new Object();
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY);
    private final SimpleDateFormat fileDayFmt = new SimpleDateFormat("yyyyMMdd", Locale.GERMANY);
    private final SimpleDateFormat stampFmt = new SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.GERMANY);

    public DebugTelemetry(Context context) {
        root = new File(context.getFilesDir(), "touran-debug");
        queueDir = new File(root, "upload-queue");
        root.mkdirs(); queueDir.mkdirs();
    }

    public String sessionId() { return sessionId; }

    public void appendCsvLine(String line) {
        if (line == null || line.isEmpty()) return;
        synchronized (lock) {
            try {
                String day = dayFmt.format(new Date());
                File dayDir = new File(root, day);
                dayDir.mkdirs();
                File out = new File(dayDir, "135er_Touran_" + day + ".csv");
                boolean fresh = !out.exists() || out.length() == 0;
                try (FileOutputStream fos = new FileOutputStream(out, true)) {
                    if (fresh) fos.write("timestamp;elapsed_ms;pid;label;raw_response;decoded_value;unit;status\n".getBytes(StandardCharsets.UTF_8));
                    fos.write(line.getBytes(StandardCharsets.UTF_8));
                    if (!line.endsWith("\n")) fos.write('\n');
                    fos.flush();
                    try { fos.getFD().sync(); } catch (Exception ignored) {}
                }
            } catch (Exception ignored) {}
        }
    }

    public File queueSnapshot(String reportType, String payload) throws IOException {
        synchronized (lock) {
            String safeType = clean(reportType == null ? "log" : reportType);
            String stamp = stampFmt.format(new Date());
            File f = new File(queueDir, stamp + "__" + safeType + "__" + sessionId + "__" + UUID.randomUUID() + ".pending");
            try (FileOutputStream out = new FileOutputStream(f, false)) {
                out.write(payload.getBytes(StandardCharsets.UTF_8)); out.flush();
                try { out.getFD().sync(); } catch (Exception ignored) {}
            }
            return f;
        }
    }

    public int pendingCount() {
        File[] fs = queueDir.listFiles((d,n) -> n.endsWith(".pending"));
        return fs == null ? 0 : fs.length;
    }

    public Result flush(String uploadUrl, String installId, String appVersion) {
        int sent = 0; String last = "";
        File[] files = queueDir.listFiles((d,n) -> n.endsWith(".pending"));
        if (files == null) return new Result(0,0,"");
        Arrays.sort(files, Comparator.comparing(File::getName));
        for (File f : files) {
            HttpURLConnection c = null;
            try {
                byte[] body = readAll(f);
                QueueMeta meta = meta(f);
                URL u = new URL(uploadUrl);
                c = (HttpURLConnection)u.openConnection();
                c.setConnectTimeout(8000); c.setReadTimeout(12000);
                c.setRequestMethod("POST"); c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
                c.setRequestProperty("X-Touran-Install", clean(installId));
                c.setRequestProperty("X-Touran-Session", clean(meta.session));
                c.setRequestProperty("X-Touran-App", appVersion == null ? "unknown" : clean(appVersion));
                c.setRequestProperty("X-Touran-Date", meta.day);
                c.setRequestProperty("X-Touran-Report", clean(meta.report));
                c.setRequestProperty("X-Touran-Queue-File", f.getName());
                c.setFixedLengthStreamingMode(body.length);
                try (OutputStream os = c.getOutputStream()) { os.write(body); os.flush(); }
                int code = c.getResponseCode();
                InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
                last = "HTTP " + code + " " + readText(in, 4096);
                if (code >= 200 && code < 300) {
                    if (!f.delete()) last += " delete_failed=" + f.getName();
                    sent++;
                } else break;
            } catch (Exception e) {
                last = e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage());
                break;
            } finally { if (c != null) c.disconnect(); }
        }
        return new Result(sent, pendingCount(), last);
    }

    private QueueMeta meta(File f) {
        String n=f.getName();
        String day=dayFmt.format(new Date());
        try {
            if (n.length() >= 8) {
                Date d=fileDayFmt.parse(n.substring(0,8));
                if (d != null) day=dayFmt.format(d);
            }
        } catch (Exception ignored) {}
        String report="log", session=sessionId;
        String[] p=n.split("__");
        if (p.length >= 4) {
            report=clean(p[1]);
            session=clean(p[2]);
        }
        return new QueueMeta(report,session,day);
    }

    private static String clean(String s) {
        if (s == null) return "unknown";
        String v=s.replaceAll("[^A-Za-z0-9._-]","_");
        return v.length()>96 ? v.substring(0,96) : v;
    }

    private static byte[] readAll(File f) throws IOException {
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n=in.read(b))>0) out.write(b,0,n);
            return out.toByteArray();
        }
    }

    private static String readText(InputStream in, int max) throws IOException {
        if (in == null) return "";
        try (InputStream x=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[1024]; int n;
            while ((n=x.read(b))>0 && out.size()<max) out.write(b,0,Math.min(n,max-out.size()));
            return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
        }
    }
}
