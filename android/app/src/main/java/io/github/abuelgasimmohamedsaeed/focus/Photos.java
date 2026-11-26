package io.github.abuelgasimmohamedsaeed.focus;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Saves a background photo, in full quality, to Pictures › Focus, so it shows in the gallery and can be a wallpaper. */
final class Photos {
    private Photos() {}

    /** Tries each address in turn (the HD copy first, then the original) until one downloads. */
    static void save(Context c, String[] urls, String name) {
        boolean ok = false;
        for (String u : urls) {
            if (u == null || !u.startsWith("https://")) continue;
            if (download(c, u, name)) { ok = true; break; }
        }
        final boolean done = ok;
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(c,
                done ? L.t(c, "Saved to Pictures › Focus", "حُفظت في الصور › Focus")
                     : L.t(c, "Couldn't save the photo. Check your internet.", "تعذّر حفظ الصورة. تحقّق من الإنترنت."),
                Toast.LENGTH_LONG).show());
    }

    private static boolean download(Context c, String address, String name) {
        HttpURLConnection h = null;
        Uri item = null;
        ContentResolver r = c.getContentResolver();
        try {
            h = (HttpURLConnection) new URL(address).openConnection();
            h.setRequestProperty("User-Agent", "Focus/1.0 (https://abuelgasimmohamedsaeed.github.io/Focus/)");
            h.setConnectTimeout(15000);
            h.setReadTimeout(30000);
            h.setInstanceFollowRedirects(true);
            if (h.getResponseCode() != 200) return false;
            String type = h.getContentType();
            if (type == null || !type.startsWith("image/")) return false;
            String ext = type.contains("png") ? ".png" : type.contains("webp") ? ".webp" : ".jpg";
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name + ext);
            v.put(MediaStore.MediaColumns.MIME_TYPE, type.split(";")[0]);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Focus");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            item = r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v);
            if (item == null) return false;
            try (InputStream in = h.getInputStream(); OutputStream out = r.openOutputStream(item)) {
                if (out == null) throw new Exception("no output");
                byte[] b = new byte[65536];
                int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
            }
            ContentValues done = new ContentValues();
            done.put(MediaStore.MediaColumns.IS_PENDING, 0);
            r.update(item, done, null, null);
            return true;
        } catch (Exception e) {
            if (item != null) r.delete(item, null, null);
            return false;
        } finally {
            if (h != null) h.disconnect();
        }
    }
}
