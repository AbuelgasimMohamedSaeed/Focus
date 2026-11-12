package io.github.abuelgasimmohamedsaeed.focus;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * Focus's Accessibility service. It draws the time line over the status bar, and for App Time it notices
 * which app comes to the front, so an app whose daily time is up gets the block screen.
 * It only reads which app is open, never what is on the screen.
 * Battery: it does nothing until an app is opened, and then sets one timer for when that app's time runs out.
 */
public class FocusAccessibility extends AccessibilityService {
    static volatile FocusAccessibility instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String front;
    private final Runnable recheck = () -> { if (front != null) check(front); };

    @Override
    protected void onServiceConnected() {
        instance = this;
        TopLine.reset();
        Sit s = Sit.load(this);
        if (s != null) Engine.refresh(this, s);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (pkg.equals(home())) { // back on the home screen: no app is open
            front = null;
            main.removeCallbacks(recheck);
            return;
        }
        if (pkg.equals(getPackageName()) || !AppUsage.apps(this).containsKey(pkg)) return; // keyboards, pop-ups, Focus itself
        if (pkg.equals(front)) return;
        front = pkg;
        check(pkg);
    }

    private String homePkg;

    private String home() {
        if (homePkg == null) {
            try {
                homePkg = getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                        android.content.pm.PackageManager.MATCH_DEFAULT_ONLY).activityInfo.packageName;
            } catch (Exception e) {
                homePkg = "";
            }
        }
        return homePkg;
    }

    /** Block the app now if its time is up, or set one timer for the moment it will be. */
    private void check(String pkg) {
        main.removeCallbacks(recheck);
        if (Rules.trackOnly(this)) return;
        int limit = Rules.limit(this, pkg);
        if (limit <= 0) return;
        long used = AppUsage.usedToday(this, pkg), left = limit * 60000L - used;
        if (left > 0) {
            main.postDelayed(recheck, left + 1500);
            return;
        }
        front = null; // so coming back to it checks again
        startActivity(new Intent(this, BlockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
                .putExtra(BlockActivity.EXTRA_USED, used)
                .putExtra(BlockActivity.EXTRA_LIMIT, limit));
    }

    /** The rules changed on the App Time page: check the app that is open right now again. */
    static void rulesChanged() {
        FocusAccessibility s = instance;
        if (s != null) s.main.post(() -> { if (s.front != null) s.check(s.front); });
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        main.removeCallbacks(recheck);
        TopLine.reset();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        instance = null;
        main.removeCallbacks(recheck);
        TopLine.reset();
        super.onDestroy();
    }
}
