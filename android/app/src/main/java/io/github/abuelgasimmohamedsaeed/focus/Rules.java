package io.github.abuelgasimmohamedsaeed.focus;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;

/**
 * App Time rules, set on the App Time page:
 *   track      true = just count, block nothing
 *   limits     {package: daily minutes}
 *   friction   "none" | "wait" (30 seconds) | "type" (a sentence): what it takes to unlock 5 more minutes
 *   emergency  emergency unlocks allowed per 7 days (15 minutes each, no friction)
 *   cooldown   minutes an app stays closed after you leave it
 *   delay      minutes before a change that loosens the rules takes effect
 * Tightening applies at once; loosening waits for the delay, so turning limits off in a weak moment doesn't work instantly.
 */
final class Rules {
    static final int EXTRA_MIN = 5, EMERGENCY_MIN = 15;
    private static JSONObject current;

    private Rules() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("focus", Context.MODE_PRIVATE);
    }

    private static JSONObject read(Context c, String key) {
        try {
            return new JSONObject(prefs(c).getString(key, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** The rules in force now (a waiting loosened version replaces them once its time comes). */
    static synchronized JSONObject get(Context c) {
        if (current == null) current = read(c, "appRules");
        long at = prefs(c).getLong("pendingAt", 0);
        if (at > 0 && System.currentTimeMillis() >= at) {
            current = read(c, "pendingRules");
            prefs(c).edit().putString("appRules", current.toString()).remove("pendingRules").remove("pendingAt").apply();
        }
        return current;
    }

    /** New rules from the page: stricter ones apply now, looser ones after the delay. */
    static synchronized void save(Context c, String json) {
        JSONObject next;
        try {
            next = new JSONObject(json);
        } catch (Exception e) {
            return;
        }
        JSONObject now = get(c);
        int delay = now.optInt("delay", 0);
        if (delay > 0 && looser(now, next)) {
            prefs(c).edit().putString("pendingRules", next.toString())
                    .putLong("pendingAt", System.currentTimeMillis() + delay * 60000L).apply();
        } else {
            current = next;
            prefs(c).edit().putString("appRules", next.toString()).remove("pendingRules").remove("pendingAt").apply();
        }
    }

    private static int frictionLevel(String f) {
        return "type".equals(f) ? 2 : "wait".equals(f) ? 1 : 0;
    }

    static boolean looser(JSONObject a, JSONObject b) {
        if (b.optBoolean("track") && !a.optBoolean("track")) return true;
        JSONObject la = a.optJSONObject("limits"), lb = b.optJSONObject("limits");
        if (la != null) {
            for (Iterator<String> it = la.keys(); it.hasNext(); ) {
                String k = it.next();
                int before = la.optInt(k, 0), after = lb == null ? 0 : lb.optInt(k, 0);
                if (before > 0 && (after == 0 || after > before)) return true;
            }
        }
        return frictionLevel(b.optString("friction")) < frictionLevel(a.optString("friction"))
                || b.optInt("emergency", 0) > a.optInt("emergency", 0)
                || b.optInt("cooldown", 0) < a.optInt("cooldown", 0)
                || b.optInt("delay", 0) < a.optInt("delay", 0);
    }

    static long pendingAt(Context c) {
        get(c);
        return prefs(c).getLong("pendingAt", 0);
    }

    static boolean trackOnly(Context c) {
        return get(c).optBoolean("track", false);
    }

    static String friction(Context c) {
        return get(c).optString("friction", "none");
    }

    static int cooldown(Context c) {
        return get(c).optInt("cooldown", 0);
    }

    /** The daily limit in minutes for today, including unlocked extra time; 0 = no limit. */
    static int limit(Context c, String pkg) {
        JSONObject limits = get(c).optJSONObject("limits");
        int base = limits == null ? 0 : Math.max(0, limits.optInt(pkg, 0));
        return base == 0 ? 0 : base + extraToday(c, pkg);
    }

    static int baseLimit(Context c, String pkg) {
        JSONObject limits = get(c).optJSONObject("limits");
        return limits == null ? 0 : Math.max(0, limits.optInt(pkg, 0));
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    static int extraToday(Context c, String pkg) {
        JSONObject g = read(c, "grants");
        return today().equals(g.optString("day")) ? g.optInt(pkg, 0) : 0;
    }

    static synchronized void grant(Context c, String pkg, int minutes) {
        JSONObject g = read(c, "grants");
        try {
            if (!today().equals(g.optString("day"))) g = new JSONObject().put("day", today());
            g.put(pkg, g.optInt(pkg, 0) + minutes);
        } catch (Exception ignored) {
        }
        prefs(c).edit().putString("grants", g.toString()).apply();
    }

    /** Emergency unlocks still allowed in the last 7 days. */
    static int emergencyLeft(Context c) {
        int allowed = get(c).optInt("emergency", 0), used = 0;
        long since = System.currentTimeMillis() - 7 * 86400000L;
        try {
            JSONArray a = new JSONArray(prefs(c).getString("emergencies", "[]"));
            for (int i = 0; i < a.length(); i++) if (a.getLong(i) > since) used++;
        } catch (Exception ignored) {
        }
        return Math.max(0, allowed - used);
    }

    static synchronized void useEmergency(Context c, String pkg) {
        JSONArray kept = new JSONArray();
        long since = System.currentTimeMillis() - 7 * 86400000L;
        try {
            JSONArray a = new JSONArray(prefs(c).getString("emergencies", "[]"));
            for (int i = 0; i < a.length(); i++) if (a.getLong(i) > since) kept.put(a.getLong(i));
        } catch (Exception ignored) {
        }
        kept.put(System.currentTimeMillis());
        prefs(c).edit().putString("emergencies", kept.toString()).apply();
        grant(c, pkg, EMERGENCY_MIN);
    }

    /** When you last left each app, for the cooldown. */
    static void left(Context c, String pkg) {
        prefs(c).edit().putLong("left:" + pkg, System.currentTimeMillis()).apply();
    }

    /** Milliseconds until this app may open again, or 0. */
    static long cooldownLeft(Context c, String pkg) {
        int cd = cooldown(c);
        if (cd <= 0 || baseLimit(c, pkg) <= 0) return 0;
        long at = prefs(c).getLong("left:" + pkg, 0) + cd * 60000L - System.currentTimeMillis();
        return Math.max(0, at);
    }

    /** For the page: {pendingAt, emergencyLeft}. */
    static String status(Context c) {
        try {
            return new JSONObject().put("pendingAt", pendingAt(c)).put("emergencyLeft", emergencyLeft(c)).toString();
        } catch (Exception e) {
            return "{}";
        }
    }
}
