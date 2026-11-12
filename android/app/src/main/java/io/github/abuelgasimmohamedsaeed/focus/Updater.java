package io.github.abuelgasimmohamedsaeed.focus;

import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Checks GitHub for a newer Focus app and installs it when you say so. */
final class Updater {
    private static final String LATEST = "https://api.github.com/repos/AbuelgasimMohamedSaeed/Focus/releases/latest";
    private static long lastCheck;
    private static volatile boolean busy;

    private Updater() {}

    /** At most every 3 hours, quietly; offers the update only if there is one. */
    static void check(MainActivity a) {
        long now = System.currentTimeMillis();
        if (busy || now - lastCheck < 3 * 3600_000L) return;
        lastCheck = now;
        busy = true;
        new Thread(() -> {
            try {
                JSONObject rel = new JSONObject(read(LATEST));
                String digits = rel.getString("tag_name").replaceAll("\\D+", "");
                if (digits.isEmpty() || Long.parseLong(digits) <= a.versionCode()) return;
                String url = null;
                JSONArray assets = rel.getJSONArray("assets");
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject as = assets.getJSONObject(i);
                    if (as.getString("name").endsWith(".apk")) url = as.getString("browser_download_url");
                }
                if (url == null) return;
                String title = rel.optString("name", "A new version of Focus");
                String notes = rel.optString("body", "").trim();
                String apk = url;
                a.runOnUiThread(() -> a.offerUpdate(title, notes, apk));
            } catch (Exception ignored) {
                // offline or GitHub unreachable: try again later
            } finally {
                busy = false;
            }
        }).start();
    }

    /** Downloads the new app straight into Android's installer, which then asks you to confirm. */
    static void install(MainActivity a, String url) {
        AlertDialog progress = new AlertDialog.Builder(a)
                .setTitle("Updating Focus")
                .setMessage("Downloading…")
                .setCancelable(false)
                .show();
        new Thread(() -> {
            try {
                HttpURLConnection http = connect(url);
                long size = http.getContentLengthLong();
                PackageInstaller installer = a.getPackageManager().getPackageInstaller();
                PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
                params.setAppPackageName(a.getPackageName());
                if (size > 0) params.setSize(size);
                int id = installer.createSession(params);
                try (PackageInstaller.Session session = installer.openSession(id)) {
                    try (InputStream in = http.getInputStream(); OutputStream out = session.openWrite("focus.apk", 0, size)) {
                        byte[] buf = new byte[65536];
                        long done = 0;
                        int n, shown = -1;
                        while ((n = in.read(buf)) > 0) {
                            out.write(buf, 0, n);
                            done += n;
                            if (size > 0) {
                                int pct = (int) (done * 100 / size);
                                if (pct != shown) {
                                    shown = pct;
                                    a.runOnUiThread(() -> progress.setMessage("Downloading… " + pct + "%"));
                                }
                            }
                        }
                        session.fsync(out);
                    }
                    Intent status = new Intent(a, InstallReceiver.class);
                    PendingIntent pi = PendingIntent.getBroadcast(a, 3, status,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
                    session.commit(pi.getIntentSender());
                }
                a.runOnUiThread(progress::dismiss);
            } catch (Exception e) {
                a.runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(a, "Update failed. Check your internet and try again.", Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    private static HttpURLConnection connect(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Focus-Android");
        c.setRequestProperty("Accept", "application/vnd.github+json, application/octet-stream");
        if (c.getResponseCode() >= 400) throw new Exception("HTTP " + c.getResponseCode());
        return c;
    }

    private static String read(String url) throws Exception {
        HttpURLConnection c = connect(url);
        try (InputStream in = c.getInputStream()) {
            ByteArrayOutputStream buf = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) buf.write(b, 0, n);
            return buf.toString("UTF-8");
        }
    }
}
