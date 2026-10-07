package de.growcentral.touranlive;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

public final class AppUpdater {
    private static final String META_URL = "https://www.dezender.de/touran/update.json";
    private final Activity activity;
    private final ExecutorService net;
    private final Logger logger;

    public interface Logger { void log(String state, String detail); }

    public AppUpdater(Activity activity, ExecutorService net, Logger logger) {
        this.activity = activity; this.net = net; this.logger = logger;
    }

    public void checkAtStartup() { net.execute(() -> check(false)); }
    public void checkManual() { net.execute(() -> check(true)); }

    private void check(boolean manual) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection)new URL(META_URL).openConnection();
            c.setConnectTimeout(7000); c.setReadTimeout(9000); c.setUseCaches(false);
            if (c.getResponseCode() != 200) throw new Exception("update.json HTTP " + c.getResponseCode());
            String json = readText(c.getInputStream(), 64 * 1024);
            JSONObject o = new JSONObject(json);
            int remoteCode = o.getInt("versionCode");
            String remoteName = o.optString("versionName", String.valueOf(remoteCode));
            String apkUrl = o.getString("apkUrl");
            String sha256 = o.optString("sha256", "").trim().toLowerCase(Locale.ROOT);
            String notes = o.optString("notes", "");
            int localCode = localVersionCode();
            logger.log("UPDATE_CHECK", "local=" + localCode + " remote=" + remoteCode + " name=" + remoteName);
            if (remoteCode <= localCode) {
                if (manual) activity.runOnUiThread(() -> toastDialog("Update", "Version " + remoteName + " ist bereits installiert."));
                return;
            }
            activity.runOnUiThread(() -> new AlertDialog.Builder(activity)
                    .setTitle("Update " + remoteName + " verfügbar")
                    .setMessage(notes.isEmpty() ? "Neue Version herunterladen und installieren?" : notes)
                    .setNegativeButton("Später", null)
                    .setPositiveButton("Installieren", (d,w) -> net.execute(() -> downloadAndInstall(remoteName, apkUrl, sha256)))
                    .show());
        } catch (Exception e) {
            logger.log("UPDATE_CHECK_ERROR", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            if (manual) activity.runOnUiThread(() -> toastDialog("Updateprüfung fehlgeschlagen", String.valueOf(e.getMessage())));
        } finally { if (c != null) c.disconnect(); }
    }

    private void downloadAndInstall(String versionName, String apkUrl, String expectedSha) {
        HttpURLConnection c = null;
        try {
            File dir = new File(activity.getCacheDir(), "updates"); dir.mkdirs();
            File apk = new File(dir, "TouranLive-" + versionName + ".apk");
            c = (HttpURLConnection)new URL(apkUrl).openConnection();
            c.setConnectTimeout(10000); c.setReadTimeout(30000); c.setInstanceFollowRedirects(true);
            if (c.getResponseCode() < 200 || c.getResponseCode() >= 300) throw new Exception("APK HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); FileOutputStream out = new FileOutputStream(apk, false)) {
                byte[] b = new byte[32768]; int n; while ((n=in.read(b))>0) out.write(b,0,n); out.flush();
            }
            if (!expectedSha.isEmpty()) {
                String actual = sha256(apk);
                if (!actual.equalsIgnoreCase(expectedSha)) { apk.delete(); throw new SecurityException("SHA-256 stimmt nicht"); }
            }
            logger.log("UPDATE_DOWNLOAD_OK", apk.getAbsolutePath() + " bytes=" + apk.length());
            activity.runOnUiThread(() -> requestPermissionOrInstall(apk));
        } catch (Exception e) {
            logger.log("UPDATE_DOWNLOAD_ERROR", e.getClass().getSimpleName() + ": " + String.valueOf(e.getMessage()));
            activity.runOnUiThread(() -> toastDialog("Update fehlgeschlagen", String.valueOf(e.getMessage())));
        } finally { if (c != null) c.disconnect(); }
    }

    private void requestPermissionOrInstall(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.getPackageManager().canRequestPackageInstalls()) {
            logger.log("UPDATE_PERMISSION_REQUIRED", "REQUEST_INSTALL_PACKAGES");
            new AlertDialog.Builder(activity)
                    .setTitle("Installation freigeben")
                    .setMessage("Android muss 135er Touran einmal erlauben, APK-Updates zu installieren. Danach Update erneut starten.")
                    .setPositiveButton("Freigeben", (d,w) -> {
                        Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + activity.getPackageName()));
                        activity.startActivity(i);
                    }).setNegativeButton("Abbrechen", null).show();
            return;
        }
        install(apk);
    }

    private void install(File apk) {
        Uri uri = FileProvider.getUriForFile(activity, activity.getPackageName() + ".fileprovider", apk);
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, "application/vnd.android.package-archive");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        logger.log("UPDATE_INSTALLER_OPEN", apk.getName());
        activity.startActivity(i);
    }

    private int localVersionCode() throws Exception {
        android.content.pm.PackageInfo p = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= 28 ? (int)p.getLongVersionCode() : p.versionCode;
    }

    private static String readText(InputStream in, int max) throws Exception {
        StringBuilder s = new StringBuilder(); byte[] b = new byte[4096]; int n;
        while ((n=in.read(b))>0 && s.length()<max) s.append(new String(b,0,n,java.nio.charset.StandardCharsets.UTF_8));
        in.close(); return s.toString();
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
