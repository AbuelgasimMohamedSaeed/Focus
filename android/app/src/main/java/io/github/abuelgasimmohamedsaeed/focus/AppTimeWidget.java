package io.github.abuelgasimmohamedsaeed.focus;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Home screen widget: time left today on the apps you limit (up to three, the closest to their limit first),
 * or today's time on watched apps when nothing is limited. Redrawn when you leave an app (at most once a minute)
 * and every 30 minutes, so it costs next to no battery.
 */
public class AppTimeWidget extends AppWidgetProvider {
    private static long lastAt;

    @Override
    public void onUpdate(Context c, AppWidgetManager wm, int[] ids) {
        draw(c, wm, ids);
    }

    static void refreshNow(Context c) {
        lastAt = 0;
        refresh(c);
    }

    /** Redraw any widgets on the home screen; cheap when there are none. */
    static void refresh(Context c) {
        long now = System.currentTimeMillis();
        if (now - lastAt < 60_000) return;
        lastAt = now;
        AppWidgetManager wm = AppWidgetManager.getInstance(c);
        int[] ids = wm.getAppWidgetIds(new ComponentName(c, AppTimeWidget.class));
        if (ids.length > 0) draw(c, wm, ids);
    }

    private static void draw(Context c, AppWidgetManager wm, int[] ids) {
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_apptime);
        v.setTextViewText(R.id.w_title, L.t(c, "App Time", "وقت التطبيقات"));
        if (!AppUsage.allowed(c)) {
            v.setTextViewText(R.id.w_big, "—");
            v.setTextViewText(R.id.w_rows, L.t(c, "Open Focus to set up App Time", "افتح Focus لإعداد وقت التطبيقات"));
        } else {
            JSONArray watched = Rules.get(c).optJSONArray("watched");
            List<long[]> rows = new ArrayList<>(); // {index, used ms, limit ms}
            long total = 0;
            for (int i = 0; watched != null && i < watched.length(); i++) {
                String pkg = watched.optString(i);
                long used = AppUsage.usedToday(c, pkg);
                total += used;
                int lim = Rules.trackOnly(c) ? 0 : Rules.limit(c, pkg);
                if (lim > 0) rows.add(new long[]{i, used, lim * 60_000L});
            }
            rows.sort((a, b) -> Long.compare(a[2] - a[1], b[2] - b[1]));
            StringBuilder sb = new StringBuilder();
            if (rows.isEmpty()) {
                v.setTextViewText(R.id.w_big, Nightly.dur(c, total));
                sb.append(watched == null || watched.length() == 0
                        ? L.t(c, "Watch some apps in Focus", "اختر تطبيقات لمراقبتها في Focus")
                        : L.t(c, "today on watched apps", "اليوم على التطبيقات المراقبة"));
            } else {
                long[] first = rows.get(0);
                long left = Math.max(0, first[2] - first[1]);
                v.setTextViewText(R.id.w_big, left > 0 ? Nightly.dur(c, left) + L.t(c, " left", " متبقية") : L.t(c, "Time's up", "انتهى الوقت"));
                for (int k = 0; k < Math.min(3, rows.size()); k++) {
                    long[] r = rows.get(k);
                    long l = Math.max(0, r[2] - r[1]);
                    if (sb.length() > 0) sb.append('\n');
                    sb.append(AppUsage.name(c, watched.optString((int) r[0]))).append("  ·  ")
                            .append(l > 0 ? Nightly.dur(c, l) + L.t(c, " left", " متبقية") : L.t(c, "time's up", "انتهى الوقت"));
                }
            }
            v.setTextViewText(R.id.w_rows, sb.toString());
        }
        PendingIntent open = PendingIntent.getActivity(c, 72, new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("tab", "apptime"), PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        v.setOnClickPendingIntent(R.id.w_root, open);
        wm.updateAppWidget(ids, v);
    }
}
