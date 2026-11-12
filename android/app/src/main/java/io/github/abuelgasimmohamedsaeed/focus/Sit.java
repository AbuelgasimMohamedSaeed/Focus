package io.github.abuelgasimmohamedsaeed.focus;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

/** The running sit, as the web app reports it. Kept on the device so the bells and the live line work with Focus closed. */
final class Sit {
    long startAt, endAt;
    int planned, interval;
    String label = "", color = "", sound = "ring";
    boolean up, over;

    /** Reads the web app's sit, or returns null when no sit is running. */
    static Sit parse(String json) {
        if (json == null) return null;
        try {
            JSONObject o = new JSONObject(json);
            Sit s = new Sit();
            s.endAt = o.getLong("endAt");
            s.planned = o.optInt("planned", 0);
            s.startAt = o.optLong("startAt", s.endAt - s.planned * 60000L);
            s.interval = o.optInt("interval", 0);
            s.label = o.optString("label", "");
            s.color = o.optString("color", "");
            s.sound = o.optString("sound", "ring");
            s.up = o.optBoolean("up", false);
            s.over = o.optBoolean("over", false);
            return s;
        } catch (Exception e) {
            return null; // "null": no sit
        }
    }

    String toJson() {
        try {
            return new JSONObject()
                    .put("startAt", startAt).put("endAt", endAt).put("planned", planned).put("interval", interval)
                    .put("label", label).put("color", color).put("sound", sound).put("up", up).put("over", over)
                    .toString();
        } catch (Exception e) {
            return null;
        }
    }

    static Sit load(Context c) {
        return parse(prefs(c).getString("sit", null));
    }

    static void store(Context c, Sit s) {
        prefs(c).edit().putString("sit", s == null ? null : s.toJson()).commit();
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("focus", Context.MODE_PRIVATE);
    }
}
