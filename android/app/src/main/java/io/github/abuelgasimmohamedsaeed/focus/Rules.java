package io.github.abuelgasimmohamedsaeed.focus;

import android.content.Context;

import org.json.JSONObject;

/** App Time rules, set on the App Time page: daily minutes per app, or "track only" (nothing is blocked). */
final class Rules {
    private static JSONObject cached;

    private Rules() {}

    static synchronized void save(Context c, String json) {
        try {
            cached = new JSONObject(json);
            c.getSharedPreferences("focus", Context.MODE_PRIVATE).edit().putString("appRules", cached.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private static synchronized JSONObject get(Context c) {
        if (cached == null) {
            try {
                cached = new JSONObject(c.getSharedPreferences("focus", Context.MODE_PRIVATE).getString("appRules", "{}"));
            } catch (Exception e) {
                cached = new JSONObject();
            }
        }
        return cached;
    }

    static boolean trackOnly(Context c) {
        return get(c).optBoolean("track", false);
    }

    /** The app's daily limit in minutes, or 0 for none. */
    static int limit(Context c, String pkg) {
        JSONObject limits = get(c).optJSONObject("limits");
        return limits == null ? 0 : Math.max(0, limits.optInt(pkg, 0));
    }
}
