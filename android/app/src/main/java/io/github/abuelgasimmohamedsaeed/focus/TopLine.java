package io.github.abuelgasimmohamedsaeed.focus;

import android.content.Context;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;

/**
 * The line along the very top of the screen while a sit runs and Focus is closed:
 * full at the start, shrinking to nothing at the end, then a full amber line for extra time.
 *
 * With Focus's Accessibility service on, it is drawn over the status bar (the strip with the clock);
 * without it, Android only allows it below. Battery: it is redrawn only when it would move by about a pixel
 * (every 2 to 15 seconds), never while the screen is off, and not at all once time is up.
 */
final class TopLine {
    private static final int OVER = 0xFFF0A440;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static final Runnable tick = TopLine::paint;
    private static Context app;
    private static View bar;
    private static Context barHost;
    private static Sit sit;

    private TopLine() {}

    /** Show the line for this sit, or remove it when s is null. Safe from any thread. */
    static void update(Context c, Sit s) {
        Context a = c.getApplicationContext();
        main.post(() -> apply(a, s));
    }

    /** The Accessibility service started or stopped: draw the line again with whatever is allowed now. */
    static void reset() {
        main.post(() -> {
            remove();
            if (app != null && sit != null) apply(app, sit);
        });
    }

    private static void apply(Context c, Sit s) {
        app = c;
        sit = s;
        FocusAccessibility svc = FocusAccessibility.instance;
        Context host = svc != null ? svc : c;
        if (s == null || (svc == null && !Settings.canDrawOverlays(c))) {
            remove();
            return;
        }
        if (bar != null && barHost != host) remove(); // the Accessibility service just came or went
        if (bar == null) {
            float d = c.getResources().getDisplayMetrics().density;
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, Math.round(5 * d), // thick enough to see at a glance
                    svc != null ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                            : WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.setFitInsetsTypes(0); // the very top edge, not below the status bar
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            View v = new View(host);
            v.setPivotX(0);
            try {
                host.getSystemService(WindowManager.class).addView(v, lp);
                bar = v;
                barHost = host;
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
        if (over) return; // a still line from here on: nothing more to do
        int width = Math.max(1, bar.getResources().getDisplayMetrics().widthPixels);
        long perPixel = span / width;
        long next = now < sit.startAt ? sit.startAt - now : Math.min(Math.min(15000, Math.max(2000, perPixel)), sit.endAt - now);
        PowerManager pm = bar.getContext().getSystemService(PowerManager.class);
        if (pm != null && !pm.isInteractive()) next = Math.max(next, 15000); // screen off: nobody can see it
        main.postDelayed(tick, Math.max(500, next));
    }

    private static void remove() {
        main.removeCallbacks(tick);
        if (bar == null) return;
        try {
            barHost.getSystemService(WindowManager.class).removeView(bar);
        } catch (Exception ignored) {
        }
        bar = null;
        barHost = null;
    }

    private static int color(String hex) {
        try {
            return hex == null || hex.isEmpty() ? 0xFF3987E5 : Color.parseColor(hex);
        } catch (Exception e) {
            return 0xFF3987E5;
        }
    }
}
