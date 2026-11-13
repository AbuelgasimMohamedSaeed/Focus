package io.github.abuelgasimmohamedsaeed.focus;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;

/**
 * Focus's Accessibility service. It draws the time line over the status bar, and for App Time it notices
 * which app comes to the front, so an app whose daily time is up gets the block screen.
 * It only reads which app is open. The one exception is website blocking, if you turn it on: while a browser is open,
 * it reads that browser's address bar, and nothing else.
 * Battery: it does nothing until an app is opened, and then sets one timer for when that app's time runs out.
 * Address bar changes are only listened to while a browser is in front and some sites are blocked.
 */
public class FocusAccessibility extends AccessibilityService {
    static volatile FocusAccessibility instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private String front;
    private long dueAt = Long.MAX_VALUE; // when the open app should be looked at again
    private final Runnable recheck = () -> { if (front != null) check(front); gray(); };
    private final Runnable readBar = this::checkSite;
    private boolean listening; // to address bar changes

    @Override
    protected void onServiceConnected() {
        instance = this;
        TopLine.reset();
        Sit s = Sit.load(this);
        if (s != null) Engine.refresh(this, s);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getPackageName() == null) return;
        String pkg = event.getPackageName().toString();
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) { // only while a browser is in front
            if (pkg.equals(front)) {
                main.removeCallbacks(readBar);
                main.postDelayed(readBar, 700);
            }
            return;
        }
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        if (front != null && System.currentTimeMillis() >= dueAt) check(front); // the timer slept with the phone
        if (pkg.equals(home())) { // back on the home screen: no app is open
            leave();
            main.removeCallbacks(recheck);
            dueAt = Long.MAX_VALUE;
            gray();
            return;
        }
        if (pkg.equals(getPackageName()) || !AppUsage.apps(this).containsKey(pkg)) { // keyboards, pop-ups, the lock screen, Focus
            gray();
            return;
        }
        if (pkg.equals(front)) return;
        leave();
        front = pkg;
        check(pkg);
        listen(Sites.browser(pkg) && !Rules.trackOnly(this) && Rules.sites(this) != null);
        if (listening) {
            main.removeCallbacks(readBar);
            main.postDelayed(readBar, 700);
        }
        gray();
    }

    /** Hear address bar changes only while a browser is open and some sites are blocked. */
    private void listen(boolean on) {
        if (on == listening) return;
        listening = on;
        try {
            AccessibilityServiceInfo info = getServiceInfo();
            info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED | (on ? AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED : 0);
            setServiceInfo(info);
        } catch (Exception ignored) {
        }
        if (!on) main.removeCallbacks(readBar);
    }

    /** A blocked website in the browser in front: go back from it and show the block screen. */
    private void checkSite() {
        String pkg = front;
        if (pkg == null || !Sites.browser(pkg) || Rules.trackOnly(this)) return;
        String site = Sites.blocked(Sites.address(getRootInActiveWindow(), pkg), Rules.sites(this));
        if (site == null) return;
        performGlobalAction(GLOBAL_ACTION_BACK);
        startActivity(new Intent(this, BlockActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(BlockActivity.EXTRA_PKG, pkg)
                .putExtra(BlockActivity.EXTRA_WHY, BlockActivity.SITE)
                .putExtra(BlockActivity.EXTRA_PRAYER, site));
        front = null;
        listen(false);
    }

    /**
     * Gray screen: on at bedtime (if you chose it), and while a watched app is past half its daily limit
     * (if you chose that). Off otherwise, but only if Focus turned it on.
     */
    private void gray() {
        if (!Gray.allowed(this)) return;
        long now = System.currentTimeMillis();
        boolean want = Rules.grayBedtime(this, now);
        if (!want && front != null && Rules.grayHalf(this) && !Rules.trackOnly(this)) {
            int lim = Rules.limit(this, front);
            want = lim > 0 && AppUsage.usedToday(this, front) >= lim * 30_000L;
        }
        Gray.set(this, want);
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
        if (front != null) AppTimeWidget.refresh(this);
        front = null;
        listen(false);
    }

    /**
     * Show the prayer reminder if an adhan has come; block the app if the clock keeps it closed (its hours, bedtime),
     * it is cooling down or its time is up;
     * otherwise set one timer for the first moment one of those will happen.
     */
    private void check(String pkg) {
        main.removeCallbacks(recheck);
        dueAt = Long.MAX_VALUE;
        long now = System.currentTimeMillis();
        String prayer = Rules.prayerDue(this, now);
        if (prayer != null) { // the adhan has come: a reminder over any app, until "I understand"
            block(pkg, BlockActivity.PRAYER, now, 0, prayer);
            return;
        }
        long next = Rules.nextPrayer(this, now);
        if (Rules.trackOnly(this)) {
            schedule(now, next);
            return;
        }
        Rules.Shut shut = Rules.shut(this, pkg, now);
        if (shut != null) {
            block(pkg, shut.why, shut.until, 0, shut.prayer);
            return;
        }
        next = Math.min(next, Rules.nextShut(this, pkg, now));
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
            long toHalf = limit * 30_000L - used; // gray screen at half the limit
            if (toHalf > 0 && Rules.grayHalf(this) && Gray.allowed(this)) next = Math.min(next, toHalf);
        }
        long bed = Rules.nextGrayEdge(this, now); // gray screen starting or ending at bedtime
        if (bed > 0) next = Math.min(next, bed);
        schedule(now, next);
    }

    private void schedule(long now, long next) {
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
        if (s != null) s.main.post(() -> { if (s.front != null) s.check(s.front); s.gray(); });
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        main.removeCallbacks(readBar);
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
