package io.github.abuelgasimmohamedsaeed.focus;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
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

/** Focus for Android: the online Focus app in a full-screen window, plus what only a real app can do. */
public class MainActivity extends Activity {
    static final String URL = "https://abuelgasimmohamedsaeed.github.io/Focus/";
    private static final String HOST = "abuelgasimmohamedsaeed.github.io";
    private static final int BG = 0xFF09090B;
    private static final int PICK_FILE = 2;

    /** True while Focus is on screen; the web app rings the bells itself then. */
    static volatile boolean visible;
    static MainActivity current;

    private FrameLayout root;
    private WebView web;
    private View custom;
    private WebChromeClient.CustomViewCallback customDone;
    private ValueCallback<Uri[]> fileDone;
    private long loadedAt;
    private String pendingUpdate;

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
        web.loadUrl(URL);
        loadedAt = System.currentTimeMillis();

        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        keepScreenOn(Sit.load(this) != null);
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
        current = this;
        web.onResume();
        web.resumeTimers();
        stopService(new Intent(this, BellService.class)); // opening Focus quiets a ringing bell
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.cancel(Engine.ID_LIVE);
        nm.cancel(Engine.ID_ALERT);
        Sit sit = Sit.load(this);
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
        Sit sit = Sit.load(this);
        if (sit != null) Engine.refresh(this, sit); // the live line takes over
        web.onPause();
        web.pauseTimers(); // Android rings the bells now, so the page doesn't ring them twice
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (current == this) current = null;
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
        root.setBackgroundColor(color);
        WindowInsetsController bars = getWindow().getInsetsController();
        if (bars != null) {
            int dark = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            bars.setSystemBarsAppearance(Color.luminance(color) > .5f ? dark : 0, dark); // dark icons on a light background
        }
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
