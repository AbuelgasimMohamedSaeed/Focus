package io.github.abuelgasimmohamedsaeed.focus;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
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
 *   watched    the apps on the watch list: bedtime and prayer times close these
 *   windows    {package: [{from, to}]} minutes after midnight: the app opens only in these hours
 *   bedtime    {from, to} or null: watched apps closed in these hours
 *   prayer     {lat, lng, fajr, isha, ishaMin, after} or null: watched apps closed from each adhan for `after` minutes
 * Tightening applies at once; loosening waits for the delay, so turning limits off in a weak moment doesn't work instantly.
 */
final class Rules {
    static final int EXTRA_MIN = 5, EMERGENCY_MIN = 15;
    private static final long DAY = 86400000L;
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
                || b.optInt("delay", 0) < a.optInt("delay", 0)
                || changed(a.optJSONObject("bedtime"), b.optJSONObject("bedtime"))
                || changed(a.optJSONObject("prayer"), b.optJSONObject("prayer"))
                || windowsLooser(a, b) || unwatched(a, b);
    }

    /** A schedule that was on was turned off or changed. */
    private static boolean changed(JSONObject before, JSONObject after) {
        return before != null && (after == null || !before.toString().equals(after.toString()));
    }

    private static boolean windowsLooser(JSONObject a, JSONObject b) {
        JSONObject wa = a.optJSONObject("windows"), wb = b.optJSONObject("windows");
        if (wa == null) return false;
        for (Iterator<String> it = wa.keys(); it.hasNext(); ) {
            String k = it.next();
            JSONArray x = wa.optJSONArray(k), y = wb == null ? null : wb.optJSONArray(k);
            if (x != null && x.length() > 0 && (y == null || !x.toString().equals(y.toString()))) return true;
        }
        return false;
    }

    /** An app left the watch list while bedtime or prayer times were closing it. */
    private static boolean unwatched(JSONObject a, JSONObject b) {
        if (a.optJSONObject("bedtime") == null && a.optJSONObject("prayer") == null) return false;
        JSONArray wa = a.optJSONArray("watched");
        if (wa == null) return false;
        for (int i = 0; i < wa.length(); i++) if (!watched(b, wa.optString(i))) return true;
        return false;
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
        prefs(c).edit().putString("emergencies", kept.toString())
                .putLong("free:" + pkg, System.currentTimeMillis() + EMERGENCY_MIN * 60000L).apply(); // past any schedule too
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

    // ---- the clock: open hours per app, bedtime, prayer times

    /** Why the clock keeps this app closed right now. */
    static final class Shut {
        final String why, prayer;
        final long until;

        Shut(String why, long until, String prayer) {
            this.why = why;
            this.until = until;
            this.prayer = prayer;
        }
    }

    private static boolean watched(JSONObject r, String pkg) {
        JSONArray w = r.optJSONArray("watched");
        if (w != null) for (int i = 0; i < w.length(); i++) if (pkg.equals(w.optString(i))) return true;
        return false;
    }

    private static long msOfDay(long now) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(now);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return now - cal.getTimeInMillis();
    }

    /** Is t inside [from, to)? Ranges past midnight (11 PM to 6 AM) wrap. */
    private static boolean inside(long t, long from, long to) {
        if (from == to) return false;
        return from < to ? t >= from && t < to : t >= from || t < to;
    }

    /** Milliseconds from t until the clock next shows x. */
    private static long ahead(long t, long x) {
        return ((x - t) % DAY + DAY) % DAY;
    }

    private static String prayerKey;
    private static int[] prayerTimes;

    /** Today's adhan times in minutes after midnight, worked out once a day. */
    static synchronized int[] prayers(JSONObject p) {
        String key = today() + p.toString();
        if (!key.equals(prayerKey)) {
            prayerTimes = Prayer.today(p.optDouble("lat", 3.139), p.optDouble("lng", 101.687),
                    p.optDouble("fajr", 20), p.optDouble("isha", 18), p.optInt("ishaMin", 0));
            prayerKey = key;
        }
        return prayerTimes;
    }

    /** The app is closed by the clock now (outside its hours, bedtime, prayer), or null. */
    static Shut shut(Context c, String pkg, long now) {
        if (now < prefs(c).getLong("free:" + pkg, 0)) return null; // an emergency unlock is running
        JSONObject r = get(c);
        long t = msOfDay(now);
        JSONObject wins = r.optJSONObject("windows");
        JSONArray w = wins == null ? null : wins.optJSONArray(pkg);
        if (w != null && w.length() > 0) {
            boolean open = false;
            long next = DAY;
            for (int i = 0; i < w.length(); i++) {
                JSONObject x = w.optJSONObject(i);
                if (x == null) continue;
                long from = x.optInt("from") * 60000L, to = x.optInt("to") * 60000L;
                if (inside(t, from, to)) open = true;
                else next = Math.min(next, ahead(t, from));
            }
            if (!open) return new Shut(BlockActivity.WINDOW, now + next, "");
        }
        if (!watched(r, pkg)) return null;
        JSONObject b = r.optJSONObject("bedtime");
        if (b != null) {
            long from = b.optInt("from") * 60000L, to = b.optInt("to") * 60000L;
            if (inside(t, from, to)) return new Shut(BlockActivity.BEDTIME, now + ahead(t, to), "");
        }
        JSONObject p = r.optJSONObject("prayer");
        if (p != null) {
            int[] times = prayers(p);
            long after = p.optInt("after", 20) * 60000L;
            for (int i = 0; i < times.length; i++) {
                long s = times[i] * 60000L;
                if (t >= s && t < s + after) return new Shut(BlockActivity.PRAYER, now + (s + after - t), Prayer.NAMES[i]);
            }
        }
        return null;
    }

    /** Milliseconds until the clock next closes this app (or an emergency unlock ends), or Long.MAX_VALUE. */
    static long nextShut(Context c, String pkg, long now) {
        long free = prefs(c).getLong("free:" + pkg, 0) - now, next = free > 0 ? free : Long.MAX_VALUE;
        JSONObject r = get(c);
        long t = msOfDay(now);
        JSONObject wins = r.optJSONObject("windows");
        JSONArray w = wins == null ? null : wins.optJSONArray(pkg);
        if (w != null) {
            for (int i = 0; i < w.length(); i++) {
                JSONObject x = w.optJSONObject(i);
                if (x == null) continue;
                long from = x.optInt("from") * 60000L, to = x.optInt("to") * 60000L;
                if (inside(t, from, to)) next = Math.min(next, ahead(t, to));
            }
        }
        if (!watched(r, pkg)) return next;
        JSONObject b = r.optJSONObject("bedtime");
        if (b != null) next = Math.min(next, ahead(t, b.optInt("from") * 60000L));
        JSONObject p = r.optJSONObject("prayer");
        if (p != null) {
            int[] times = prayers(p);
            long soonest = times[0] * 60000L + DAY - t; // tomorrow's Fajr, about
            for (int m : times) if (m * 60000L > t) soonest = Math.min(soonest, m * 60000L - t);
            next = Math.min(next, soonest);
        }
        return next;
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
