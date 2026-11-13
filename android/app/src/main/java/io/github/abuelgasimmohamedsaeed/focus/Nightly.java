package io.github.abuelgasimmohamedsaeed.focus;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;

/**
 * The nightly summary: one quiet notification at the time you chose (10 PM by default) with today's time on your
 * watched apps, which limits you kept, and the time saved against how much you used them before.
 * One inexact alarm a day, so it costs nothing in battery.
 */
public class Nightly extends BroadcastReceiver {
    private static final String CH = "summary";
    private static final int ID = 7;

    @Override
    public void onReceive(Context c, Intent i) {
        try {
            post(c);
        } catch (Exception ignored) {
        }
        schedule(c);
    }

    /** Set (or clear) the alarm for the next summary, from the current rules. */
    static void schedule(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 70, new Intent(c, Nightly.class), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        int at = Rules.get(c).optInt("nightly", -1);
        if (at < 0) return;
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, at / 60);
        cal.set(Calendar.MINUTE, at % 60);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis() + 60_000) cal.add(Calendar.DAY_OF_YEAR, 1);
        am.setWindow(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), 10 * 60_000L, pi);
    }

    private static void post(Context c) {
        if (!AppUsage.allowed(c)) return;
        JSONObject r = Rules.get(c);
        JSONArray watched = r.optJSONArray("watched");
        if (watched == null || watched.length() == 0) return;
        JSONObject base = r.optJSONObject("base");
        long total = 0, saved = 0;
        StringBuilder lines = new StringBuilder();
        int kept = 0, limited = 0;
        for (int k = 0; k < watched.length(); k++) {
            String pkg = watched.optString(k);
            long used = AppUsage.usedToday(c, pkg);
            total += used;
            if (base != null && base.optLong(pkg) > 0) saved += Math.max(0, base.optLong(pkg) - used);
            int lim = Rules.limit(c, pkg);
            if (lim > 0) {
                limited++;
                if (used <= lim * 60_000L + 30_000) kept++;
            }
            if (used < 60_000 && lim == 0) continue;
            if (lines.length() > 0) lines.append('\n');
            lines.append(AppUsage.name(c, pkg)).append("  ").append(dur(c, used));
            if (lim > 0) lines.append(L.t(c, " of ", " من ")).append(dur(c, lim * 60_000L));
        }
        String title = L.t(c, "Today: ", "اليوم: ") + dur(c, total) + L.t(c, " on watched apps", " على التطبيقات المراقبة");
        StringBuilder sub = new StringBuilder();
        if (limited > 0) sub.append(kept == limited ? L.t(c, "Every limit kept", "التزمت بكل الحدود")
                : L.t(c, "Limits kept: ", "الحدود الملتزم بها: ") + kept + "/" + limited);
        if (saved >= 60_000) {
            if (sub.length() > 0) sub.append(" · ");
            sub.append(L.t(c, "Saved ", "وفّرت ")).append(dur(c, saved));
        }
        String text = sub.length() > 0 ? sub.toString() : lines.toString();
        String big = sub.length() > 0 ? sub + (lines.length() > 0 ? "\n" + lines : "") : lines.toString();

        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = new NotificationChannel(CH, L.t(c, "Nightly summary", "الملخص الليلي"), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
        PendingIntent open = PendingIntent.getActivity(c, 71, new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("tab", "apptime"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CH)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(big))
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        try {
            nm.notify(ID, n);
        } catch (SecurityException ignored) {
        }
    }

    static String dur(Context c, long ms) {
        long m = Math.round(ms / 60000.0);
        if (m < 60) return m + L.t(c, "m", " د");
        return (m / 60) + L.t(c, "h ", " س ") + (m % 60) + L.t(c, "m", " د");
    }
}
