package de.growcentral.touranlive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

/** In-app updater for the private TouranLive deployment on dezender.de. */
public final class AppUpdater {
    private static final String META_URL = "https://www.dezender.de/touran/update.json";
    private static final String PREF = "touranlive_updater";
    private static final String PENDING_APK = "pending_apk";
    private static final String PENDING_SHA = "pending_sha";
    private final Activity activity;
    private final ExecutorService net;
    private final Logger logger;

    public interface Logger { void log(String state, String detail); }

    public AppUpdater(Activity activity, ExecutorService net, Logger logger) {
        this.activity = activity; this.net = net; this.logger = logger;
    }

    public void checkAtStartup() { net.execute(() -> check(false)); }
    public void checkManual() { net.execute(() -> check(true)); }

    public void resumePendingInstallIfPermitted() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) return;
        SharedPreferences p = activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE);
        String path = p.getString(PENDING_APK, "");
        if (path == null || path.isEmpty()) return;
        String expected = p.getString(PENDING_SHA, "");
        p.edit().remove(PENDING_APK).remove(PENDING_SHA).apply();
        net.execute(() -> {
            try {
                File apk = new File(path);
                File expectedFile = new File(activity.getCacheDir(), "updates/TouranLive-update.apk");
                if (!apk.getCanonicalFile().equals(expectedFile.getCanonicalFile())) throw new SecurityException("Unzulässiger Update-Pfad");
                if (!sha256(apk).equals(UpdatePolicy.checksum(expected))) throw new SecurityException("SHA-256 stimmt nicht");
                validateArchive(apk, -1);
                logger.log("UPDATE_RESUME_INSTALL", apk.getName());
                onUi(() -> install(apk));
            } catch (Exception e) { logger.log("UPDATE_PENDING_INVALID", String.valueOf(e.getMessage())); }
        });
    }

    private void check(boolean manual) {
        HttpURLConnection c = null;
        try {
            c = openVerified(META_URL, 7000, 9000);
            int http = c.getResponseCode();
            if (http != 200) throw new Exception("update.json HTTP " + http);
            String json = readText(c.getInputStream(), 64 * 1024);
            JSONObject o = new JSONObject(json);
            int remoteCode = o.getInt("versionCode");
            String remoteName = o.optString("versionName", String.valueOf(remoteCode));
            String apkUrl = o.getString("apkUrl");
            String sha256 = UpdatePolicy.checksum(o.optString("sha256", "").trim());
            UpdatePolicy.url(apkUrl);
            String notes = o.optString("notes", "");
            int localCode = localVersionCode();
            logger.log("UPDATE_CHECK", "local=" + localCode + " remote=" + remoteCode + " name=" + remoteName);
            if (remoteCode <= localCode) {
                if (manual) onUi(() -> toastDialog("Update", "Keine neuere Version verfügbar."));
                return;
            }
            onUi(() -> new AlertDialog.Builder(activity)
                    .setTitle("Update " + remoteName + " verfügbar")
                    .setMessage(notes.isEmpty() ? "Neue Version herunterladen und installieren?" : notes)
                    .setNegativeButton("Später", null)
                    .setPositiveButton("Installieren", (d,w) -> net.execute(() -> downloadAndInstall(remoteCode, apkUrl, sha256)))
                    .show());
        } catch (Exception e) {
            logger.log("UPDATE_CHECK_ERROR", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            if (manual) onUi(() -> toastDialog("Updateprüfung fehlgeschlagen", String.valueOf(e.getMessage())));
        } finally { if (c != null) c.disconnect(); }
    }

    private synchronized void downloadAndInstall(int versionCode, String apkUrl, String expectedSha) {
        HttpURLConnection c = null;
        File partial = null;
        try {
            File dir = new File(activity.getCacheDir(), "updates"); dir.mkdirs();
            File apk = new File(dir, "TouranLive-update.apk");
            partial = new File(dir, "TouranLive-update.part");
            expectedSha = UpdatePolicy.checksum(expectedSha);
            c = openVerified(apkUrl, 10000, 30000);
            int http = c.getResponseCode();
            if (http < 200 || http >= 300) throw new Exception("APK HTTP " + http);
            long declared = c.getContentLengthLong();
            if (declared > 150L * 1024L * 1024L) throw new SecurityException("APK unerwartet groß");
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(partial, false)) {
                byte[] b = new byte[32768]; int n; long total=0;
                while ((n=in.read(b))>0) {
                    total += n;
                    if (total > 150L * 1024L * 1024L) throw new SecurityException("APK Download zu groß");
                    out.write(b,0,n);
                }
                out.flush(); try { out.getFD().sync(); } catch (Exception ignored) {}
            }
            if (partial.length() < 1024) throw new Exception("APK Download ist leer/ungültig");
            if (declared >= 0 && partial.length() != declared) throw new IOException("APK Download unvollständig");
            String actual = sha256(partial);
            if (!actual.equals(expectedSha)) throw new SecurityException("SHA-256 stimmt nicht");
            validateArchive(partial, versionCode);
            if (!partial.renameTo(apk)) throw new IOException("Geprüfte APK konnte nicht gespeichert werden");
            logger.log("UPDATE_SHA256_OK", actual);
            logger.log("UPDATE_DOWNLOAD_OK", apk.getAbsolutePath() + " bytes=" + apk.length());
            onUi(() -> requestPermissionOrInstall(apk, actual));
        } catch (Exception e) {
            logger.log("UPDATE_DOWNLOAD_ERROR", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            onUi(() -> toastDialog("Update fehlgeschlagen", String.valueOf(e.getMessage())));
        } finally { if (c != null) c.disconnect(); if (partial != null && partial.exists()) partial.delete(); }
    }

    private void requestPermissionOrInstall(File apk, String checksum) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            activity.getSharedPreferences(PREF, Activity.MODE_PRIVATE).edit().putString(PENDING_APK, apk.getAbsolutePath()).putString(PENDING_SHA, checksum).apply();
            logger.log("UPDATE_PERMISSION_REQUIRED", "REQUEST_INSTALL_PACKAGES pending=" + apk.getName());
            new AlertDialog.Builder(activity)
                    .setTitle("Installation freigeben")
                    .setMessage("Android muss 135er Touran einmal erlauben, APK-Updates zu installieren. Nach der Freigabe wird dieses Update automatisch fortgesetzt.")
                    .setPositiveButton("Freigeben", (d,w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(i);
                    }).setNegativeButton("Abbrechen", null).show();
            return;
        }
        install(apk);
    }

    private void install(File apk) {
        try {
            Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            logger.log("UPDATE_INSTALLER_OPEN", apk.getName());
            activity.startActivity(i);
        } catch (Exception e) {
            logger.log("UPDATE_INSTALLER_ERROR", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            toastDialog("Installer konnte nicht geöffnet werden", String.valueOf(e.getMessage()));
        }
    }

    private int localVersionCode() throws Exception {
        android.content.pm.PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? (int)p.getLongVersionCode() : p.versionCode;
    }

    private static String readText(InputStream in, int max) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[4096]; int n;
            while ((n = input.read(b)) != -1) {
                if (out.size() + n > max) throw new IOException("Update-Metadaten zu groß");
                out.write(b, 0, n);
            }
            return new String(out.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static HttpURLConnection openVerified(String address, int connectMs, int readMs) throws Exception {
        URL url = UpdatePolicy.url(address);
        for (int redirects = 0; redirects <= 3; redirects++) {
            HttpURLConnection c = (HttpURLConnection) url.openConnection();
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(connectMs); c.setReadTimeout(readMs);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json,application/vnd.android.package-archive,application/octet-stream");
            int code;
            try { code = c.getResponseCode(); } catch (Exception e) { c.disconnect(); throw e; }
            if (code == 301 || code == 302 || code == 303 || code == 307 || code == 308) {
                String location = c.getHeaderField("Location"); c.disconnect();
                if (location == null) throw new IOException("Update-Redirect ohne Ziel");
                url = UpdatePolicy.url(new URL(url, location).toString());
            } else return c;
        }
        throw new IOException("Zu viele Update-Weiterleitungen");
    }

    @SuppressWarnings("deprecation")
    private void validateArchive(File apk, int expectedCode) throws Exception {
        PackageManager pm = activity.getPackageManager();
        int flags = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo remote = pm.getPackageArchiveInfo(apk.getAbsolutePath(), flags);
        PackageInfo local = pm.getPackageInfo(activity.getPackageName(), flags);
        if (remote == null || !activity.getPackageName().equals(remote.packageName)) throw new SecurityException("Falsches APK-Paket");
        long code = Build.VERSION.SDK_INT >= 28 ? remote.getLongVersionCode() : remote.versionCode;
        if (code <= localVersionCode() || (expectedCode >= 0 && code != expectedCode)) throw new SecurityException("APK-Version passt nicht");
        Signature[] theirs = Build.VERSION.SDK_INT >= 28 && remote.signingInfo != null ? remote.signingInfo.getApkContentsSigners() : remote.signatures;
        Signature[] ours = Build.VERSION.SDK_INT >= 28 && local.signingInfo != null ? local.signingInfo.getApkContentsSigners() : local.signatures;
        if (ours == null || theirs == null || ours.length == 0 || ours.length != theirs.length) throw new SecurityException("APK-Signatur fehlt");
        for (Signature signature : ours) if (!Arrays.asList(theirs).contains(signature)) throw new SecurityException("APK-Signatur passt nicht zur installierten App");
    }

    private void onUi(Runnable action) {
        activity.runOnUiThread(() -> { if (!activity.isFinishing() && !activity.isDestroyed()) action.run(); });
    }

    private static String sha256(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = new java.io.FileInputStream(f)) { byte[] b=new byte[32768]; int n; while((n=in.read(b))>0) md.update(b,0,n); }
        StringBuilder s=new StringBuilder(); for(byte x:md.digest()) s.append(String.format(Locale.ROOT,"%02x",x)); return s.toString();
    }

    private void toastDialog(String title, String msg) {
        new AlertDialog.Builder(activity).setTitle(title).setMessage(msg).setPositiveButton("OK", null).show();
    }
}
