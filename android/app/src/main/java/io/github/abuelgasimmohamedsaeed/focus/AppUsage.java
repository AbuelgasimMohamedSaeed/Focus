package io.github.abuelgasimmohamedsaeed.focus;

import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** App Time: how long and how often each app was used today, read from Android's own usage records. */
final class AppUsage {
    private static final Map<String, String> icons = new HashMap<>();

    private AppUsage() {}

    /** Has the user turned on Usage access for Focus? */
    static boolean allowed(Context c) {
        AppOpsManager ops = c.getSystemService(AppOpsManager.class);
        int mode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.getPackageName());
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return c.checkSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED;
        }
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    static void openSettings(Context c) {
        Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            c.startActivity(new Intent(i).setData(Uri.parse("package:" + c.getPackageName()))); // straight to Focus where supported
        } catch (Exception e) {
            try { c.startActivity(i); } catch (Exception ignored) {}
        }
    }

    /** Today since midnight: [{pkg, name, ms, opens}] for every app with a home-screen icon that was used. */
    static String today(Context c) {
        JSONArray out = new JSONArray();
        if (!allowed(c)) return out.toString();
        Map<String, long[]> use = usage(c);
        Map<String, String> apps = launchable(c);
        List<Map.Entry<String, long[]>> rows = new ArrayList<>(use.entrySet());
        rows.sort((a, b) -> Long.compare(b.getValue()[0], a.getValue()[0]));
        for (Map.Entry<String, long[]> r : rows) {
            String name = apps.get(r.getKey());
            if (name == null || (r.getValue()[0] < 1000 && r.getValue()[1] == 0)) continue;
            try {
                out.put(new JSONObject().put("pkg", r.getKey()).put("name", name)
                        .put("ms", r.getValue()[0]).put("opens", r.getValue()[1]));
            } catch (Exception ignored) {
            }
        }
        return out.toString();
    }

    /** Milliseconds this app has been on screen today. */
    static long usedToday(Context c, String pkg) {
        if (!allowed(c)) return 0;
        long[] v = usage(c).get(pkg);
        return v == null ? 0 : v[0];
    }

    /** package -> {milliseconds on screen today, opens today} */
    private static Map<String, long[]> usage(Context c) {
        long now = System.currentTimeMillis();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long start = cal.getTimeInMillis();

        Map<String, long[]> use = new HashMap<>(); // package -> {milliseconds, opens}
        UsageStatsManager um = c.getSystemService(UsageStatsManager.class);
        UsageEvents events = um.queryEvents(start - 3 * 3600_000L, now); // a little before midnight, to catch an app already open then
        UsageEvents.Event e = new UsageEvents.Event();
        String current = null, lastOpened = null;
        long since = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            String p = e.getPackageName();
            long t = e.getTimeStamp();
            switch (e.getEventType()) {
                case UsageEvents.Event.ACTIVITY_RESUMED:
                    if (current != null && !current.equals(p)) add(use, current, since, t, start);
                    if (!p.equals(current)) since = t;
                    current = p;
                    if (!p.equals(lastOpened)) { // came from another app or the home screen: one open
                        if (t >= start) entry(use, p)[1]++;
                        lastOpened = p;
                    }
                    break;
                case UsageEvents.Event.ACTIVITY_PAUSED:
                case UsageEvents.Event.ACTIVITY_STOPPED:
                    if (p.equals(current)) {
                        add(use, current, since, t, start);
                        current = null;
                    }
                    break;
                case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                    if (current != null) add(use, current, since, t, start);
                    current = null;
                    break;
                default:
                    break;
            }
        }
        if (current != null) add(use, current, since, now, start);
        return use;
    }

    /**
     * The last n days, today included: [{d: "2026-11-13", apps: {package: milliseconds}}], oldest first.
     * Android keeps about a week or so of these records; older days come back empty.
     */
    static String history(Context c, int n) {
        JSONArray out = new JSONArray();
        if (!allowed(c) || n < 1) return out.toString();
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.DAY_OF_YEAR, -(n - 1));
        long[] bounds = new long[n + 1];
        String[] keys = new String[n];
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US);
        for (int i = 0; i <= n; i++) {
            bounds[i] = cal.getTimeInMillis();
            if (i < n) keys[i] = f.format(new java.util.Date(bounds[i]));
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }
        @SuppressWarnings("unchecked")
        Map<String, Long>[] per = new Map[n];
        for (int i = 0; i < n; i++) per[i] = new HashMap<>();
        long now = System.currentTimeMillis();
        UsageEvents events = c.getSystemService(UsageStatsManager.class).queryEvents(bounds[0] - 3 * 3600_000L, now);
        UsageEvents.Event e = new UsageEvents.Event();
        String current = null;
        long since = 0;
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            String p = e.getPackageName();
            long t = e.getTimeStamp();
            switch (e.getEventType()) {
                case UsageEvents.Event.ACTIVITY_RESUMED:
                    if (current != null && !current.equals(p)) split(per, bounds, current, since, t);
                    if (!p.equals(current)) since = t;
                    current = p;
                    break;
                case UsageEvents.Event.ACTIVITY_PAUSED:
                case UsageEvents.Event.ACTIVITY_STOPPED:
                    if (p.equals(current)) {
                        split(per, bounds, current, since, t);
                        current = null;
                    }
                    break;
                case UsageEvents.Event.SCREEN_NON_INTERACTIVE:
                    if (current != null) split(per, bounds, current, since, t);
                    current = null;
                    break;
                default:
                    break;
            }
        }
        if (current != null) split(per, bounds, current, since, now);
        Map<String, String> apps = apps(c);
        for (int i = 0; i < n; i++) {
            JSONObject a = new JSONObject();
            for (Map.Entry<String, Long> r : per[i].entrySet()) {
                if (r.getValue() < 30_000 || !apps.containsKey(r.getKey()) || r.getKey().equals(c.getPackageName())) continue;
                try { a.put(r.getKey(), (long) r.getValue()); } catch (Exception ignored) {}
            }
            try { out.put(new JSONObject().put("d", keys[i]).put("apps", a)); } catch (Exception ignored) {}
        }
        return out.toString();
    }

    /** Add [from, to) to each day it falls in. */
    private static void split(Map<String, Long>[] per, long[] bounds, String pkg, long from, long to) {
        for (int i = 0; i < per.length; i++) {
            long lo = Math.max(from, bounds[i]), hi = Math.min(to, bounds[i + 1]);
            if (hi > lo) per[i].put(pkg, per[i].getOrDefault(pkg, 0L) + (hi - lo));
        }
    }

    private static Map<String, String> appsCache;
    private static long appsAt;

    /** Apps with a home-screen icon (package -> name), refreshed every 10 minutes. */
    static synchronized Map<String, String> apps(Context c) {
        long now = System.currentTimeMillis();
        if (appsCache == null || now - appsAt > 600_000L) {
            appsCache = launchable(c);
            appsAt = now;
        }
        return appsCache;
    }

    static String name(Context c, String pkg) {
        String n = apps(c).get(pkg);
        return n == null ? pkg : n;
    }

    /** Every app with a home-screen icon: [{pkg, name}], A to Z. */
    static String installed(Context c) {
        Map<String, String> apps = launchable(c);
        List<Map.Entry<String, String>> rows = new ArrayList<>(apps.entrySet());
        Collator az = Collator.getInstance();
        rows.sort((a, b) -> az.compare(a.getValue(), b.getValue()));
        JSONArray out = new JSONArray();
        for (Map.Entry<String, String> r : rows) {
            if (r.getKey().equals(c.getPackageName())) continue;
            try { out.put(new JSONObject().put("pkg", r.getKey()).put("name", r.getValue())); } catch (Exception ignored) {}
        }
        return out.toString();
    }

    /** The app's icon as a small PNG data URL, or "" when there is none. */
    static synchronized String icon(Context c, String pkg) {
        String cached = icons.get(pkg);
        if (cached != null) return cached;
        String url = "";
        try {
            Drawable d = c.getPackageManager().getApplicationIcon(pkg);
            int px = 96;
            Bitmap b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(b);
            d.setBounds(0, 0, px, px);
            d.draw(canvas);
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            b.compress(Bitmap.CompressFormat.PNG, 100, png);
            url = "data:image/png;base64," + Base64.encodeToString(png.toByteArray(), Base64.NO_WRAP);
        } catch (Exception ignored) {
        }
        icons.put(pkg, url);
        return url;
    }

    private static Map<String, String> launchable(Context c) {
        PackageManager pm = c.getPackageManager();
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        Map<String, String> apps = new HashMap<>();
        for (ResolveInfo r : pm.queryIntentActivities(home, 0)) {
            String pkg = r.activityInfo.packageName;
            if (!apps.containsKey(pkg)) apps.put(pkg, String.valueOf(r.activityInfo.applicationInfo.loadLabel(pm)));
        }
        return apps;
    }

    private static long[] entry(Map<String, long[]> use, String pkg) {
        long[] v = use.get(pkg);
        if (v == null) {
            v = new long[2];
            use.put(pkg, v);
        }
        return v;
    }

    private static void add(Map<String, long[]> use, String pkg, long from, long to, long dayStart) {
        from = Math.max(from, dayStart);
        if (to > from) entry(use, pkg)[0] += to - from;
    }
}
