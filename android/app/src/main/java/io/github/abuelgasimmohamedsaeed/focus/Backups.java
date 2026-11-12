package io.github.abuelgasimmohamedsaeed.focus;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** One backup file a day in Download › Focus backups; the last 30 days are kept. */
final class Backups {
    private static final Uri FILES = MediaStore.Downloads.EXTERNAL_CONTENT_URI;
    private static final String DIR = Environment.DIRECTORY_DOWNLOADS + "/Focus backups/";
    private static final String WHERE = MediaStore.MediaColumns.RELATIVE_PATH + "=?";

    private Backups() {}

    static synchronized void save(Context c, String json) {
        try {
            JSONObject data = new JSONObject(json).getJSONObject("data");
            JSONArray sessions = data.optJSONArray("sessions");
            JSONObject dhikr = data.optJSONObject("dhikr");
            JSONArray log = dhikr == null ? null : dhikr.optJSONArray("log");
            if ((sessions == null || sessions.length() == 0) && (log == null || log.length() == 0)) {
                return; // never replace a backup with nothing
            }
            String name = "focus-backup-" + new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date()) + ".json";
            ContentResolver r = c.getContentResolver();
            Uri today = null;
            long cutoff = System.currentTimeMillis() / 1000 - 30L * 86400;
            String[] cols = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.DATE_MODIFIED};
            try (Cursor q = r.query(FILES, cols, WHERE, new String[]{DIR}, null)) {
                while (q != null && q.moveToNext()) {
                    Uri u = ContentUris.withAppendedId(FILES, q.getLong(0));
                    if (name.equals(q.getString(1))) today = u;
                    else if (q.getLong(2) < cutoff) r.delete(u, null, null);
                }
            }
            if (today == null) {
                ContentValues v = new ContentValues();
                v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                v.put(MediaStore.MediaColumns.MIME_TYPE, "application/json");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH, DIR);
                today = r.insert(FILES, v);
            }
            if (today == null) return;
            try (OutputStream out = r.openOutputStream(today, "wt")) {
                if (out != null) out.write(json.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception ignored) {
            // a missed backup is retried on the next change
        }
    }

    static String newest(Context c) {
        ContentResolver r = c.getContentResolver();
        String[] cols = {MediaStore.MediaColumns._ID};
        try (Cursor q = r.query(FILES, cols, WHERE, new String[]{DIR}, MediaStore.MediaColumns.DATE_MODIFIED + " DESC")) {
            while (q != null && q.moveToNext()) {
                Uri u = ContentUris.withAppendedId(FILES, q.getLong(0));
                try (InputStream in = r.openInputStream(u)) {
                    if (in == null) continue;
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    byte[] b = new byte[16384];
                    int n;
                    while ((n = in.read(b)) > 0) buf.write(b, 0, n);
                    String s = buf.toString("UTF-8");
                    if (new JSONObject(s).has("data")) return s;
                } catch (Exception ignored) {
                    // unreadable file: try the next one
                }
            }
        } catch (Exception ignored) {
        }
        return "";
    }
}
