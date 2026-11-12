package io.github.abuelgasimmohamedsaeed.focus;

import android.content.Context;
import android.provider.Settings;
import android.webkit.JavascriptInterface;

/** What the web app can ask of Android. Seen in the page as window.FocusAndroid. */
final class Bridge {
    private final MainActivity activity;
    private final Context app;

    Bridge(MainActivity activity) {
        this.activity = activity;
        this.app = activity.getApplicationContext();
    }

    /** The running sit changed (or ended, as "null"). */
    @JavascriptInterface
    public void sitState(String json) {
        Engine.update(app, json);
        boolean running = Sit.parse(json) != null;
        activity.runOnUiThread(() -> activity.sitChanged(running));
    }

    /** Open (true) or close (false) the floating mini window. False when Android first needs the user's permission. */
    @JavascriptInterface
    public boolean mini(boolean on) {
        activity.runOnUiThread(() -> {
            if (on) activity.openFloating();
            else activity.closeMini(true);
        });
        return true;
    }

    /** Automatic backup to Download › Focus backups. */
    @JavascriptInterface
    public void saveBackup(String json) {
        new Thread(() -> Backups.save(app, json)).start();
    }

    /** The newest backup, or "" when there is none. */
    @JavascriptInterface
    public String findBackup() {
        return Backups.newest(app);
    }

    /** The page's background color, so the status-bar area matches it (white when a Dhikr goal is reached). */
    @JavascriptInterface
    public void barColor(String hex) {
        activity.runOnUiThread(() -> activity.barColor(hex));
    }

    /** A vibration made by Android itself: "tap" (short and strong), "goal" or "end". */
    @JavascriptInterface
    public void vibrate(String kind) {
        Engine.buzz(app, kind);
    }

    /** True when the line can be drawn over the status bar (Focus's Accessibility service is on). */
    @JavascriptInterface
    public boolean lineReady() {
        return FocusAccessibility.instance != null;
    }

    @JavascriptInterface
    public void setupLine() {
        activity.runOnUiThread(activity::askLine);
    }

    // ---- App Time

    /** True once Usage access is on for Focus. */
    @JavascriptInterface
    public boolean usageAccess() {
        return AppUsage.allowed(app);
    }

    @JavascriptInterface
    public void openUsageAccess() {
        AppUsage.openSettings(activity);
    }

    /** Today's minutes and opens per app. */
    @JavascriptInterface
    public String appUsage() {
        return AppUsage.today(app);
    }

    /** All apps with a home-screen icon, for picking which to watch. */
    @JavascriptInterface
    public String installedApps() {
        return AppUsage.installed(app);
    }

    @JavascriptInterface
    public String appIcon(String pkg) {
        return AppUsage.icon(app, pkg);
    }

    @JavascriptInterface
    public String version() {
        return activity.versionName();
    }
}
