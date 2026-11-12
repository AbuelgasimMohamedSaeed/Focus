package io.github.abuelgasimmohamedsaeed.focus;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * The line along the very top of the screen while a sit runs and Focus is closed:
 * full at the start, shrinking to nothing at the end, then a full amber line for extra time.
 * Needs "Display over other apps". Redrawn every 2 seconds, and not at all while the screen is off.
 */
final class TopLine {
    private static final int OVER = 0xFFF0A440;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable tick = TopLine::paint;
    private static View bar;
    private static Sit sit;

    private TopLine() {}

    /** Show the line for this sit, or remove it when s is null. Safe from any thread. */
    static void update(Context c, Sit s) {
        Context app = c.getApplicationContext();
        main.post(() -> apply(app, s));
    }

    private static void apply(Context c, Sit s) {
        sit = s;
        if (s == null || !Settings.canDrawOverlays(c)) {
            remove(c);
            return;
        }
        if (bar == null) {
            float d = c.getResources().getDisplayMetrics().density;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, Math.max(3, Math.round(3 * d)),
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            View v = new View(c);
            v.setPivotX(0);
            try {
                c.getSystemService(WindowManager.class).addView(v, lp);
                bar = v;
            } catch (Exception e) {
                return; // the phone refused the overlay
            }
        }
        paint();
    }

    private static void paint() {
        main.removeCallbacks(tick);
        if (bar == null || sit == null) return;
        long now = System.currentTimeMillis(), span = Math.max(1, sit.endAt - sit.startAt);
        boolean over = sit.over || now >= sit.endAt;
        float f;
        if (over || now < sit.startAt) f = 1f;
        else if (sit.up) f = (now - sit.startAt) / (float) span; // counting up: the line grows
        else f = (sit.endAt - now) / (float) span;                // counting down: the line shrinks
        bar.setBackgroundColor(over ? OVER : color(sit.color));
        bar.setScaleX(Math.max(.01f, Math.min(1f, f)));
        if (!over) main.postDelayed(tick, 2000);
    }

    private static void remove(Context c) {
        main.removeCallbacks(tick);
        if (bar == null) return;
        try {
            c.getSystemService(WindowManager.class).removeView(bar);
        } catch (Exception ignored) {
        }
        bar = null;
    }

    private static int color(String hex) {
        try {
            return hex == null || hex.isEmpty() ? 0xFF3987E5 : Color.parseColor(hex);
        } catch (Exception e) {
            return 0xFF3987E5;
        }
    }
}
