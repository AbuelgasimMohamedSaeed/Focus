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
    private long dueAt = Long.MAX_VALUE; // when the open app should be looked at again
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
        if (front != null && System.currentTimeMillis() >= dueAt) check(front); // the timer slept with the phone
        if (pkg.equals(home())) { // back on the home screen: no app is open
            leave();
            main.removeCallbacks(recheck);
            dueAt = Long.MAX_VALUE;
            return;
        }
        if (pkg.equals(getPackageName()) || !AppUsage.apps(this).containsKey(pkg)) return; // keyboards, pop-ups, Focus itself
        if (pkg.equals(front)) return;
        leave();
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

    /** Leaving an app starts its cooldown. */
    private void leave() {
        if (front != null && Rules.baseLimit(this, front) > 0) Rules.left(this, front);
        front = null;
    }

    /**
     * Block the app now if the clock keeps it closed (its hours, bedtime, prayer), it is cooling down or its time is up;
     * otherwise set one timer for the first moment one of those will happen.
     */
    private void check(String pkg) {
        main.removeCallbacks(recheck);
        dueAt = Long.MAX_VALUE;
        if (Rules.trackOnly(this)) return;
        long now = System.currentTimeMillis();
        Rules.Shut shut = Rules.shut(this, pkg, now);
        if (shut != null) {
            block(pkg, shut.why, shut.until, 0, shut.prayer);
            return;
        }
        long next = Rules.nextShut(this, pkg, now);
        int limit = Rules.limit(this, pkg);
        if (limit > 0) {
            long cool = Rules.cooldownLeft(this, pkg);
            if (cool > 0) {
                block(pkg, BlockActivity.COOLDOWN, cool, limit, "");
                return;
            }
            long used = AppUsage.usedToday(this, pkg), left = limit * 60000L - used;
            if (left <= 0) {
                block(pkg, BlockActivity.LIMIT, used, limit, "");
                return;
            }
            next = Math.min(next, left);
        }
        if (next == Long.MAX_VALUE) return;
        dueAt = now + next + 1500;
        main.postDelayed(recheck, next + 1500);
    }

    private void block(String pkg, String why, long value, int limit, String prayer) {
        front = null; // so coming back to it checks again
        dueAt = Long.MAX_VALUE;
        startActivity(new Intent(this, BlockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
                .putExtra(BlockActivity.EXTRA_WHY, why)
                .putExtra(BlockActivity.EXTRA_VALUE, value)
                .putExtra(BlockActivity.EXTRA_LIMIT, limit)
                .putExtra(BlockActivity.EXTRA_PRAYER, prayer));
    }

    /** After an unlock: the app is open again, so time it from now. */
    static void unlocked(String pkg) {
        FocusAccessibility s = instance;
        if (s != null) s.main.post(() -> { s.front = pkg; s.check(pkg); });
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
