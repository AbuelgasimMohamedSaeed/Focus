package io.github.abuelgasimmohamedsaeed.focus;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Bells, alarms and notifications for a running sit. */
final class Engine {
    static final String CH_LIVE = "live", CH_ALERT = "alert";
    static final int ID_LIVE = 1, ID_ALERT = 2;
    static final String START = "start", INTERVAL = "interval", END = "end";
    static final String ACTION_ALARM = "io.github.abuelgasimmohamedsaeed.focus.ALARM";

    private Engine() {}

    static void channels(Context c) {
        NotificationChannel live = new NotificationChannel(CH_LIVE, "Running timer", NotificationManager.IMPORTANCE_LOW);
        live.setDescription("Time left while a sit is running");
        live.setShowBadge(false);
        live.setSound(null, null);
        NotificationChannel alert = new NotificationChannel(CH_ALERT, "Time's up", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("When a sit finishes");
        alert.setSound(null, null); // Focus plays the bell you chose itself
        alert.enableVibration(true);
        alert.setVibrationPattern(new long[]{0, 400, 200, 400});
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.createNotificationChannel(live);
        nm.createNotificationChannel(alert);
    }

    /** The web app started, changed or finished the sit. */
    static void update(Context c, String json) {
        Sit s = Sit.parse(json);
        Sit.store(c, s);
        schedule(c, s);
        if (s == null) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.cancel(ID_LIVE);
            nm.cancel(ID_ALERT);
            TopLine.update(c, null);
        } else {
            refresh(c, s);
        }
    }

    /** Shows the live notification and the top line while Focus is out of sight, and hides them while Focus is on screen. */
    static void refresh(Context c, Sit s) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        boolean away = s != null && !MainActivity.onScreen();
        if (away) nm.notify(ID_LIVE, live(c, s));
        else nm.cancel(ID_LIVE);
        TopLine.update(c, away ? s : null);
    }

    /** An alarm went off: ring the bell if Focus is closed, then set the next one. */
    static void fire(Context c, String kind, long endAt) {
        Sit s = Sit.load(c);
        if (s == null) return;
        if (s.endAt != endAt || kind == null) { // an old alarm: the sit has changed since
            schedule(c, s);
            return;
        }
        boolean away = !MainActivity.onScreen(), sound = !"off".equals(s.sound);
        if (END.equals(kind)) {
            s.over = true;
            Sit.store(c, s);
            if (away) {
                if (sound) BellService.play(c, END);
                else c.getSystemService(NotificationManager.class).notify(ID_ALERT, alert(c, s));
            }
        } else if (away && sound) {
            BellService.play(c, kind);
        }
        refresh(c, s);
        schedule(c, s);
    }

    /** Sets one alarm for the next moment that needs a bell: the start, an interval bell, or the end. */
    static void schedule(Context c, Sit s) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (s == null || s.over) {
            am.cancel(alarm(c, null, 0));
            return;
        }
        long now = System.currentTimeMillis(), at;
        String kind;
        if (now < s.startAt - 500) {
            kind = START;
            at = s.startAt;
        } else {
            kind = END;
            at = Math.max(s.endAt, now);
            long iv = s.interval * 60000L;
            if (iv > 0) {
                long next = s.startAt + ((now - s.startAt) / iv + 1) * iv;
                if (next < s.endAt - 20000) {
                    kind = INTERVAL;
                    at = next;
                }
            }
        }
        PendingIntent p = alarm(c, kind, s.endAt);
        try {
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setAlarmClock(new AlarmManager.AlarmClockInfo(at, open(c)), p);
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
            }
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p);
        }
    }

    private static PendingIntent alarm(Context c, String kind, long endAt) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(ACTION_ALARM)
                .putExtra("kind", kind).putExtra("endAt", endAt);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    /** The live line: a countdown in the notification shade, and on Android 16 a chip in the status bar. */
    static Notification live(Context c, Sit s) {
        String name = s.label.isEmpty() ? "Focus" : s.label;
        Notification.Builder b = new Notification.Builder(c, CH_LIVE)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(true)
                .setUsesChronometer(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setContentIntent(open(c));
        int col = color(s.color);
        if (col != 0) b.setColor(col);
        if (s.over) {
            b.setContentTitle(name + L.t(c, " · time's up", " · انتهى الوقت"))
                    .setContentText(L.t(c, "Extra time counts every 5 min · open Focus and tap Done", "الوقت الإضافي يُحسب · افتح Focus واضغط تم"))
                    .setWhen(s.endAt); // counts up from the end
        } else if (s.up) {
            b.setContentTitle(name)
                    .setContentText(L.t(c, "Counting up to ", "العدّ حتى ") + duration(s.planned) + L.t(c, " · ends at ", " · ينتهي ") + clock(s.endAt))
                    .setWhen(s.startAt);
        } else {
            b.setContentTitle(name)
                    .setContentText(duration(s.planned) + L.t(c, " · ends at ", " · ينتهي ") + clock(s.endAt))
                    .setWhen(s.endAt)
                    .setChronometerCountDown(true);
        }
        Bundle extras = new Bundle();
        extras.putBoolean("android.requestPromotedOngoing", true); // Android 16 Live Update: the time shows in the status bar
        b.addExtras(extras);
        return b.build();
    }

    /** The "time's up" alert. */
    static Notification alert(Context c, Sit s) {
        String name = s.label.isEmpty() ? L.t(c, "Your sit", "جلستك") : s.label;
        Notification.Builder b = new Notification.Builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat_focus)
                .setContentTitle(L.t(c, "Time's up", "انتهى الوقت"))
                .setContentText(name + " · " + duration(s.planned) + L.t(c, " done. Extra time counts until you tap Done.", " اكتملت. الوقت الإضافي يُحسب حتى تضغط تم."))
                .setCategory(Notification.CATEGORY_REMINDER)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setContentIntent(open(c));
        int col = color(s.color);
        if (col != 0) b.setColor(col);
        return b.build();
    }

    /** A strong vibration through Android itself, felt even when touch vibration is turned down. */
    static void buzz(Context c, String kind) {
        Vibrator v = Build.VERSION.SDK_INT >= 31
                ? c.getSystemService(VibratorManager.class).getDefaultVibrator()
                : c.getSystemService(Vibrator.class);
        if (v == null || !v.hasVibrator()) return;
        VibrationEffect e;
        if ("goal".equals(kind)) e = VibrationEffect.createWaveform(new long[]{0, 300, 120, 300}, -1);
        else if ("end".equals(kind)) e = VibrationEffect.createWaveform(new long[]{0, 400, 200, 400}, -1);
        else e = VibrationEffect.createOneShot(55, v.hasAmplitudeControl() ? 255 : VibrationEffect.DEFAULT_AMPLITUDE);
        v.vibrate(e, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build());
    }

    /** The recorded bell for a sound name: the single strike, or the full ending with its repeats. */
    static int sound(String name, boolean end) {
        switch (name == null ? "ring" : name) {
            case "temple": return end ? R.raw.end_temple : R.raw.bell_temple;
            case "bowl": return end ? R.raw.end_bowl : R.raw.bell_bowl;
            case "tingsha": return end ? R.raw.end_tingsha : R.raw.bell_tingsha;
            case "hand": return end ? R.raw.end_hand : R.raw.bell_hand;
            default: return end ? R.raw.end_ring : R.raw.bell_ring;
        }
    }

    private static int color(String hex) {
        try {
            return hex == null || hex.isEmpty() ? 0 : Color.parseColor(hex);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String clock(long t) {
        return new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date(t));
    }

    private static String duration(int min) {
        if (min < 60) return min + " min";
        return (min / 60) + " h" + (min % 60 == 0 ? "" : " " + (min % 60) + " min");
    }
}
