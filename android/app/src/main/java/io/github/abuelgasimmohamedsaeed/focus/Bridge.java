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
        if (on && !Settings.canDrawOverlays(app)) {
            activity.runOnUiThread(() -> activity.askOverlay(true));
            return false;
        }
        activity.runOnUiThread(() -> {
            if (on) activity.openMini();
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

    @JavascriptInterface
    public String version() {
        return activity.versionName();
    }
}
