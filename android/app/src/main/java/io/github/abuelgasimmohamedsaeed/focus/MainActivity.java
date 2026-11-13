package io.github.abuelgasimmohamedsaeed.focus;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityOptions;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Focus for Android: the online Focus app in a full-screen window, plus what only a real app can do. */
public class MainActivity extends Activity {
    static final String URL = "https://abuelgasimmohamedsaeed.github.io/Focus/";
    private static final String HOST = "abuelgasimmohamedsaeed.github.io";
    private static final int BG = 0xFF09090B;
    private static final int PICK_FILE = 2;

    /** True while Focus is on screen; the web app rings the bells itself then. */
    static volatile boolean visible;
    /** True while the floating mini window shows Focus over other apps. */
    static volatile boolean miniShown;
    static MainActivity current;

    static boolean onScreen() {
        return visible || miniShown;
    }

    private FrameLayout root;
    private WebView web;
    private View custom;
    private WebChromeClient.CustomViewCallback customDone;
    private ValueCallback<Uri[]> fileDone;
    private long loadedAt;
    private String pendingUpdate;
    private LinearLayout miniBox;
    private GradientDrawable miniBg;
    private View miniHandle;
    private int pageColor = BG;
    private WindowManager.LayoutParams miniLp;
    private float dragX, dragY;
    private int startW;
    /** Shape of the mini window's page area: 210 wide by 176 high. */
    private static final float RATIO = 210f / 176f;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        Engine.channels(this);

        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setNavigationBarContrastEnforced(false);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        web = new WebView(this);
        web.setBackgroundColor(BG);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsets.CONSUMED;
        });
        setContentView(root);
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars != null) {
            bars.setSystemBarsAppearance(0, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setSupportZoom(false);
        s.setAllowFileAccess(false);
        web.addJavascriptInterface(new Bridge(this), "FocusAndroid");
        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.loadUrl(URL + ("apptime".equals(getIntent().getStringExtra("tab")) ? "#apptime" : ""));
        loadedAt = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        keepScreenOn(Sit.load(this) != null);
    }

    /** Opened again from a notification or the widget that points at a page (App Time). */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String tab = intent.getStringExtra("tab");
        if (web != null && "apptime".equals(tab)) web.evaluateJavascript("window.focusOpenTab && focusOpenTab('apptime')", null);
    }

    private static final int ASK_LOCATION = 7;

    /** Prayer times for where you are: ask for rough location, or open Focus's settings if it was refused for good. */
    void askLocation() {
        if (Place.allowed(this)) {
            Place.refresh(this, true, this::placeChanged);
        } else if (getSharedPreferences("focus", MODE_PRIVATE).getBoolean("askedLocation", false)
                && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        } else {
            getSharedPreferences("focus", MODE_PRIVATE).edit().putBoolean("askedLocation", true).apply();
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, ASK_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(code, permissions, results);
        if (code == ASK_LOCATION) {
            if (Place.allowed(this)) Place.refresh(this, true, this::placeChanged);
            else placeChanged();
        }
    }

    private void placeChanged() {
        if (web != null) web.evaluateJavascript("window.focusPlaceChanged && focusPlaceChanged()", null);
        FocusAccessibility.rulesChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        current = this;
        if (miniShown) closeMini(false); // Focus opened from the home screen: take the page back from the mini window
        web.onResume();
        web.resumeTimers();
        stopService(new Intent(this, BellService.class)); // opening Focus quiets a ringing bell
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.cancel(Engine.ID_ALERT);
        Sit sit = Sit.load(this);
        Engine.refresh(this, sit); // Focus is on screen: no notification and no top line
        Place.refresh(this, false, this::placeChanged); // prayer times follow you, checked at most every 6 hours
        keepScreenOn(sit != null);
        long now = System.currentTimeMillis();
        if (sit == null && now - loadedAt > 6 * 3600_000L) { // been open a long time: pick up any new version of the app
            web.reload();
            loadedAt = now;
        }
        if (pendingUpdate != null && getPackageManager().canRequestPackageInstalls()) {
            String url = pendingUpdate;
            pendingUpdate = null;
            Updater.install(this, url);
        } else {
            Updater.check(this);
        }
    }

    @Override
    protected void onPause() {
        visible = false;
        if (!miniShown) { // in the mini window the page keeps running and ringing itself
            Sit sit = Sit.load(this);
            if (sit != null) Engine.refresh(this, sit); // the live notification and the top line take over
            web.onPause();
            web.pauseTimers(); // Android rings the bells now, so the page doesn't ring them twice
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
        if (miniShown) closeMini(false);
        web.destroy();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (custom != null) exitFullScreen();
        else moveTaskToBack(true); // keep Focus as it is, like Home
    }

    void barColor(String hex) {
        int color;
        try {
            color = Color.parseColor(hex);
        } catch (Exception e) {
            return;
        }
        pageColor = color;
        root.setBackgroundColor(color);
        paintMini();
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars != null) {
            int dark = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            bars.setSystemBarsAppearance(Color.luminance(color) > .5f ? dark : 0, dark); // dark icons on a light background
        }
    }

    /** The page started or ended a sit. */
    void sitChanged(boolean running) {
        keepScreenOn(running);
        if (running && FocusAccessibility.instance == null && !prefs().getBoolean("askedLine", false)) {
            prefs().edit().putBoolean("askedLine", true).apply();
            askLine();
        }
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("focus", MODE_PRIVATE);
    }

    /** Accessibility lets Focus draw the line over the status bar, at the very top of the screen. */
    void askLine() {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(L.t(this, "Turn on Accessibility for Focus", "شغّل إمكانية الوصول لـ Focus"))
                .setMessage(L.t(this, "Focus uses it for the line along the very top of the screen that shows your time left, "
                        + "App Time's block screen, and, if you turn it on, closing blocked websites in your browser.\n\n"
                        + "1. Tap Open settings.\n"
                        + "2. Find Focus (on Xiaomi: under Downloaded apps) and turn it on.\n\n"
                        + "If Android says it is a restricted setting: open Focus's App info, tap \u22ee at the top right, "
                        + "choose Allow restricted settings, and try again.\n\n"
                        + "Focus only sees which app is open, plus the browser's address bar when website blocking is on.",
                        "يستخدمها Focus للخط أعلى الشاشة الذي يُظهر الوقت المتبقي، ولشاشة الحظر في وقت التطبيقات، ولإغلاق المواقع المحظورة في المتصفح إن شغّلت ذلك.\n\n"
                        + "1. اضغط فتح الإعدادات.\n"
                        + "2. ابحث عن Focus (في شاومي: ضمن التطبيقات المنزّلة) وشغّله.\n\n"
                        + "إذا قال أندرويد إنه إعداد مقيّد: افتح معلومات تطبيق Focus، اضغط \u22ee في الأعلى، "
                        + "اختر السماح بالإعدادات المقيدة، ثم حاول مجددًا.\n\n"
                        + "يرى Focus فقط التطبيق المفتوح، وشريط عنوان المتصفح عند تشغيل حظر المواقع."))
                .setPositiveButton(L.t(this, "Open settings", "فتح الإعدادات"), (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                    } catch (Exception ignored) {
                    }
                })
                .setNegativeButton(L.t(this, "Not now", "ليس الآن"), null)
                .show();
    }

    /** "Display over other apps" lets Focus show the mini window. */
    void askOverlay(boolean forMini) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle(forMini ? "Mini window" : "Line at the top of the screen")
                .setMessage((forMini
                        ? "To float over your other apps, Focus needs \"Display over other apps\"."
                        : "While you sit with Focus closed, a thin line along the top of the screen can show the time left. "
                        + "For that, Focus needs \"Display over other apps\".")
                        + "\n\nTurn it on for Focus, then come back. On Xiaomi phones, also allow "
                        + "\"Display pop-up windows while running in the background\".")
                .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Not now", null)
                .show();
    }

    /**
     * The mini button: first ask Android for its own floating window (Xiaomi, Samsung and others draw it,
     * with their own handle, resize corner and full-screen gesture). If the phone does not allow that,
     * Focus's own mini window opens instead.
     */
    void openFloating() {
        if (isInMultiWindowMode() || !startFloating()) {
            openOwnMini();
            return;
        }
        web.postDelayed(() -> {
            if (isInMultiWindowMode()) { // it worked: show the whole app in the floating window
                web.evaluateJavascript("window.focusMiniClosed && focusMiniClosed()", null);
            } else {
                openOwnMini();
            }
        }, 1200);
    }

    private void openOwnMini() {
        if (Settings.canDrawOverlays(this)) {
            openMini();
        } else { // needs "Display over other apps" first
            web.evaluateJavascript("window.focusMiniClosed && focusMiniClosed()", null);
            askOverlay(true);
        }
    }

    private boolean startFloating() {
        try {
            ActivityOptions o = ActivityOptions.makeBasic();
            ActivityOptions.class.getMethod("setLaunchWindowingMode", int.class).invoke(o, 5); // 5 = freeform window
            DisplayMetrics dm = getResources().getDisplayMetrics();
            int w = Math.round(dm.widthPixels * .5f), h = Math.round(dm.heightPixels * .42f), m = Math.round(16 * dm.density);
            o.setLaunchBounds(new Rect(dm.widthPixels - w - m, m * 8, dm.widthPixels - m, m * 8 + h));
            startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), o.toBundle());
            return true;
        } catch (Throwable t) {
            return false; // this phone does not let apps ask for it
        }
    }

    /** Moves the page into a small window that floats over other apps; drag it by its top bar. */
    void openMini() {
        if (miniShown || !Settings.canDrawOverlays(this)) return;
        if (custom != null) exitFullScreen();
        float d = getResources().getDisplayMetrics().density;
        int screenW = getResources().getDisplayMetrics().widthPixels, screenH = getResources().getDisplayMetrics().heightPixels;
        int bar = Math.round(24 * d);
        // starts small; drag the corner to make it bigger, and it remembers your size
        // the page inside is drawn for 210 x 176 and zooms with the window, so the window keeps that shape
        int minW = Math.round(150 * d), maxW = Math.min(screenW - Math.round(16 * d), Math.round((screenH * .7f - bar) * RATIO));
        int w = clamp(prefs().getInt("miniW", Math.round(210 * d)), minW, maxW);
        int h = Math.round(w / RATIO) + bar;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(); // one surface in the page's own color, like Xiaomi's floating windows
        bg.setCornerRadius(22 * d);
        box.setBackground(bg);
        box.setClipToOutline(true);
        box.setElevation(10 * d);

        FrameLayout handle = new FrameLayout(this);
        View dots = new View(this) { // three small dots to grab and move the window
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas c) {
                p.setColor(Color.luminance(pageColor) > .5f ? 0xFF9A9A96 : 0xFF5A5A62);
                float cy = getHeight() / 2f, cx = getWidth() / 2f, r = 2.4f * d, gap = 9 * d;
                for (int i = -1; i <= 1; i++) c.drawCircle(cx + i * gap, cy, r, p);
            }
        };
        handle.addView(dots, new FrameLayout.LayoutParams(-1, -1));
        TextView close = new TextView(this);
        close.setText("\u2715");
        close.setTextColor(0xFF8B8A86);
        close.setTextSize(11);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Close mini window");
        close.setOnClickListener(v -> closeMini(false));
        handle.addView(close, new FrameLayout.LayoutParams(bar + Math.round(8 * d), bar, Gravity.END | Gravity.CENTER_VERTICAL));
        box.addView(handle, new LinearLayout.LayoutParams(-1, bar));

        WindowManager wm = getApplicationContext().getSystemService(WindowManager.class);
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(w, h,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | (Sit.load(this) != null ? WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON : 0),
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = prefs().getInt("miniX", screenW - w - Math.round(12 * d));
        lp.y = prefs().getInt("miniY", Math.round(90 * d));
        handle.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragX = lp.x - e.getRawX();
                    dragY = lp.y - e.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    lp.x = Math.round(e.getRawX() + dragX);
                    lp.y = Math.round(e.getRawY() + dragY);
                    try { wm.updateViewLayout(box, lp); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    prefs().edit().putInt("miniX", lp.x).putInt("miniY", lp.y).apply();
                    return true;
                default:
                    return false;
            }
        });

        FrameLayout content = new FrameLayout(this);
        View resize = new View(this) { // resize handle: two short diagonal lines in the bottom-right corner
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas c) {
                p.setColor(Color.luminance(pageColor) > .5f ? 0xFF9A9A96 : 0xFF5A5A62);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(3 * d);
                p.setStrokeCap(Paint.Cap.ROUND);
                float s = getWidth(), m = 5 * d, r = 18 * d;
                c.drawArc(new RectF(s - m - 2 * r, s - m - 2 * r, s - m, s - m), 10, 70, false, p);
            }
        };
        resize.setContentDescription("Resize mini window");
        resize.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    dragX = e.getRawX();
                    dragY = e.getRawY();
                    startW = lp.width;
                    return true;
                case MotionEvent.ACTION_MOVE: // grows or shrinks in both directions together, keeping its shape
                    float grow = ((e.getRawX() - dragX) + (e.getRawY() - dragY) * RATIO) / 2f;
                    lp.width = clamp(Math.round(startW + grow), minW, maxW);
                    lp.height = Math.round(lp.width / RATIO) + bar;
                    try { wm.updateViewLayout(box, lp); } catch (Exception ignored) {}
                    return true;
                case MotionEvent.ACTION_UP:
                    prefs().edit().putInt("miniW", lp.width).apply();
                    web.postDelayed(() -> { // make sure the page lays itself out for the new size
                        web.requestLayout();
                        web.evaluateJavascript("window.dispatchEvent(new Event('resize'))", null);
                    }, 120);
                    return true;
                default:
                    return false;
            }
        });
        root.removeView(web);
        content.addView(web, new FrameLayout.LayoutParams(-1, -1));
        int gs = Math.round(32 * d);
        content.addView(resize, new FrameLayout.LayoutParams(gs, gs, Gravity.END | Gravity.BOTTOM));
        View home = new View(this) { // the line at the bottom: pull it down (or up) to open the full app
            private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override
            protected void onDraw(Canvas c) {
                p.setColor(Color.luminance(pageColor) > .5f ? 0xFF8A8A86 : 0xFF6A6A72);
                float w = 44 * d, t = 4 * d, cx = getWidth() / 2f, cy = getHeight() - 9 * d;
                c.drawRoundRect(new RectF(cx - w / 2, cy - t / 2, cx + w / 2, cy + t / 2), t / 2, t / 2, p);
            }
        };
        home.setContentDescription("Open the full app");
        home.setOnTouchListener((v, e) -> {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                dragY = e.getRawY();
                return true;
            }
            if (e.getActionMasked() == MotionEvent.ACTION_UP) {
                if (Math.abs(e.getRawY() - dragY) > 24 * d) closeMini(true); // pulled down or up: full screen
                return true;
            }
            return e.getActionMasked() == MotionEvent.ACTION_MOVE;
        });
        content.addView(home, new FrameLayout.LayoutParams(Math.round(96 * d), Math.round(26 * d), Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM));
        box.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
        try {
            wm.addView(box, lp);
        } catch (Exception e) { // the phone refused: put the page back
            content.removeView(web);
            root.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
            web.evaluateJavascript("window.focusMiniClosed && focusMiniClosed()", null);
            return;
        }
        miniBox = box;
        miniBg = bg;
        miniHandle = handle;
        miniLp = lp;
        paintMini();
        miniShown = true;
        Sit sit = Sit.load(this);
        Engine.refresh(this, sit); // the mini window shows the time, so no notification or top line
        moveTaskToBack(true);
    }

    /** Puts the page back into the full app; opens the app too when openApp is true. */
    void closeMini(boolean openApp) {
        if (!miniShown) return;
        if (openApp) startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT));
        miniShown = false;
        try {
            getApplicationContext().getSystemService(WindowManager.class).removeView(miniBox);
        } catch (Exception ignored) {
        }
        if (web.getParent() instanceof ViewGroup) ((ViewGroup) web.getParent()).removeView(web);
        root.addView(web, 0, new FrameLayout.LayoutParams(-1, -1));
        miniBox = null;
        miniBg = null;
        miniHandle = null;
        miniLp = null;
        web.evaluateJavascript("window.focusMiniClosed && focusMiniClosed()", null);
        if (!visible && !openApp) { // closed with the ✕: Focus stays in the background
            Sit sit = Sit.load(this);
            if (sit != null) Engine.refresh(this, sit);
            web.onPause();
            web.pauseTimers();
        }
    }

    /** The mini window wears the page's color: dark, or white when a Dhikr goal is reached. */
    private void paintMini() {
        if (miniBox == null) return;
        miniBg.setColor(pageColor);
        miniHandle.setBackgroundColor(pageColor);
        miniBox.invalidate();
        for (int i = 0; i < miniBox.getChildCount(); i++) invalidateAll(miniBox.getChildAt(i));
    }

    private static void invalidateAll(View v) {
        v.invalidate();
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) invalidateAll(g.getChildAt(i));
        }
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, Math.max(lo, v)));
    }

    void keepScreenOn(boolean on) {
        if (on) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    long versionCode() {
        PackageInfo p = info();
        return p == null ? 0 : p.getLongVersionCode();
    }

    String versionName() {
        PackageInfo p = info();
        return p == null ? "" : p.versionName;
    }

    private PackageInfo info() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    void offerUpdate(String title, String notes, String url) {
        if (isFinishing() || isDestroyed()) return;
        new AlertDialog.Builder(this)
                .setTitle("Update available")
                .setMessage(title + (notes.isEmpty() ? "" : "\n\n" + notes))
                .setPositiveButton("Update", (d, w) -> startUpdate(url))
                .setNegativeButton("Later", null)
                .show();
    }

    private void startUpdate(String url) {
        if (getPackageManager().canRequestPackageInstalls()) {
            Updater.install(this, url);
            return;
        }
        pendingUpdate = url;
        new AlertDialog.Builder(this)
                .setTitle("Allow updates")
                .setMessage("One time only: let Focus install its own updates. Turn on \"Allow from this source\", then come back.")
                .setPositiveButton("Open settings", (d, w) -> startActivity(new Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))))
                .setNegativeButton("Later", (d, w) -> pendingUpdate = null)
                .show();
    }

    private void exitFullScreen() {
        if (custom == null) return;
        WebChromeClient.CustomViewCallback cb = customDone;
        root.removeView(custom);
        custom = null;
        customDone = null;
        web.setVisibility(View.VISIBLE);
        WindowInsetsController c = getWindow().getInsetsController();
        if (c != null) c.show(WindowInsets.Type.systemBars());
        if (cb != null) cb.onCustomViewHidden();
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        if (request == PICK_FILE && fileDone != null) {
            fileDone.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, data));
            fileDone = null;
            return;
        }
        super.onActivityResult(request, result, data);
    }

    /** Keeps Focus inside the app; anything else opens in the browser. Shows a friendly page on the first offline start. */
    private final class Client extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (HOST.equals(u.getHost()) && u.getPath() != null && u.getPath().startsWith("/Focus")) return false;
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception ignored) {
            }
            return true;
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (!request.isForMainFrame()) return;
            view.loadDataWithBaseURL(null, OFFLINE, "text/html", "utf-8", null);
        }
    }

    /** Full screen and the "Restore from file" picker. */
    private final class Chrome extends WebChromeClient {
        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            if (custom != null) {
                callback.onCustomViewHidden();
                return;
            }
            custom = view;
            customDone = callback;
            root.addView(view, new FrameLayout.LayoutParams(-1, -1));
            web.setVisibility(View.GONE);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }

        @Override
        public void onHideCustomView() {
            exitFullScreen();
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileDone != null) fileDone.onReceiveValue(null);
            fileDone = callback;
            try {
                startActivityForResult(params.createIntent(), PICK_FILE);
            } catch (Exception e) {
                fileDone = null;
                callback.onReceiveValue(null);
            }
            return true;
        }
    }

    private static final String OFFLINE = "<!doctype html><meta name=viewport content='width=device-width,initial-scale=1'>"
            + "<body style='margin:0;min-height:100vh;display:grid;place-items:center;background:#09090b;color:#eceae4;"
            + "font:16px system-ui,sans-serif;text-align:center'><div style='padding:24px'>"
            + "<p style='font-size:22px;margin:0 0 10px'>Focus needs the internet once</p>"
            + "<p style='color:#8b8a86;margin:0 0 24px;line-height:1.5'>Connect to Wi-Fi or mobile data to set it up.<br>"
            + "After that it works offline.</p>"
            + "<button onclick=\"location.replace('" + URL + "')\" style='background:#eceae4;color:#09090b;border:0;"
            + "border-radius:999px;padding:12px 26px;font:inherit'>Try again</button></div>";
}
