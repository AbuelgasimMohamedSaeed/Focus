package io.github.abuelgasimmohamedsaeed.focus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The full block screen over an app whose time is up today, that is cooling down, or that the clock keeps closed
 * (outside its open hours, or at bedtime). At a prayer time it is only a reminder: "I understand" goes back to the app.
 * Ways out, if you set them up on the App Time page: 5 more minutes after waiting 30 seconds or typing a sentence,
 * or an emergency unlock (a few per week).
 */
public class BlockActivity extends Activity {
    static final String EXTRA_PKG = "pkg", EXTRA_WHY = "why", EXTRA_VALUE = "value", EXTRA_LIMIT = "limit", EXTRA_PRAYER = "prayer";
    static final String LIMIT = "limit", COOLDOWN = "cooldown", WINDOW = "window", BEDTIME = "bedtime", PRAYER = "prayer", SITE = "site";
    private static final int BG = 0xFF09090B, FG = 0xFFECEAE4, MUTED = 0xFF8B8A86;
    private static final java.util.Map<String, String> ARABIC = new java.util.HashMap<>();
    static {
        ARABIC.put("Fajr", "\u0627\u0644\u0641\u062c\u0631");
        ARABIC.put("Dhuhr", "\u0627\u0644\u0638\u0647\u0631");
        ARABIC.put("Asr", "\u0627\u0644\u0639\u0635\u0631");
        ARABIC.put("Maghrib", "\u0627\u0644\u0645\u063a\u0631\u0628");
        ARABIC.put("Isha", "\u0627\u0644\u0639\u0634\u0627\u0621");
    }
    private final Handler main = new Handler(Looper.getMainLooper());
    private String pkg;
    private float d;
    private LinearLayout box;

    @Override
    protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        show(intent);
    }

    private void show(Intent i) {
        main.removeCallbacksAndMessages(null);
        pkg = i.getStringExtra(EXTRA_PKG);
        String why = i.getStringExtra(EXTRA_WHY);
        long value = i.getLongExtra(EXTRA_VALUE, 0);
        int limit = i.getIntExtra(EXTRA_LIMIT, 0);
        String name = AppUsage.name(this, pkg);
        d = getResources().getDisplayMetrics().density;

        box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        int pad = dp(32);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        scroll.addView(box, new ScrollView.LayoutParams(-1, -2));
        scroll.setOnApplyWindowInsetsListener((v, ins) -> {
            android.graphics.Insets b = ins.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.ime());
            box.setPadding(pad + b.left, pad + b.top + dp(48), pad + b.right, pad + b.bottom);
            return WindowInsets.CONSUMED;
        });

        ImageView icon = new ImageView(this);
        try {
            Drawable dr = getPackageManager().getApplicationIcon(pkg);
            icon.setImageDrawable(dr);
        } catch (Exception ignored) {
        }
        icon.setAlpha(.5f);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(72), dp(72));
        ilp.bottomMargin = dp(28);
        box.addView(icon, ilp);

        if (L.ar(this)) box.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        if (COOLDOWN.equals(why)) {
            long min = Math.max(1, (value + 59999) / 60000);
            add(text(t("Take a short break", "خذ استراحة قصيرة"), 26, FG), 0);
            add(text(t(name + " opens again in " + min + " min.", "يفتح " + name + " بعد " + min + " دقيقة."), 16, MUTED), 12);
        } else if (WINDOW.equals(why)) {
            add(text(t(name + " is closed now", name + " مغلق الآن"), 26, FG), 0);
            add(text(t("It opens " + at(value) + ".", "يفتح " + at(value) + "."), 16, MUTED), 12);
        } else if (BEDTIME.equals(why)) {
            add(text(t("Bedtime", "وقت النوم"), 26, FG), 0);
            add(text(t(name + " opens again " + at(value) + ". Sleep well.", "يفتح " + name + " مجددًا " + at(value) + ". نومًا هنيئًا."), 16, MUTED), 12);
        } else if (SITE.equals(why)) {
            String site = i.getStringExtra(EXTRA_PRAYER);
            add(text(t("This website is closed", "هذا الموقع مغلق"), 26, FG), 0);
            add(text(t(site + " is closed in browsers. Use the app, where your limits apply.",
                    site + " مغلق في المتصفحات. استخدم التطبيق حيث تنطبق حدودك."), 16, MUTED), 12);
            add(button(t("Close it", "أغلقه"), true, v -> goHome()), 40);
            setContentView(scroll);
            getWindow().setDecorFitsSystemWindows(false);
            return;
        } else if (PRAYER.equals(why)) {
            String prayer = i.getStringExtra(EXTRA_PRAYER);
            String arTitle = ARABIC.containsKey(prayer) ? "\u062d\u0627\u0646 \u0648\u0642\u062a \u0635\u0644\u0627\u0629 " + ARABIC.get(prayer) : "";
            if (L.ar(this)) {
                add(text(arTitle, 28, FG), 0);
                add(text("أذّن المؤذن. سيبقى " + name + " هنا بعد صلاتك.", 16, MUTED), 12);
            } else {
                add(text(arTitle, 20, MUTED), 0);
                add(text("Time for " + (prayer == null ? "prayer" : prayer), 28, FG), 8);
                add(text("The adhan has come. " + name + " will be here after you pray.", 16, MUTED), 12);
            }
            add(button(t("I understand", "فهمت"), true, v -> {
                Rules.ackPrayer(this);
                reopen();
            }), 40);
            setContentView(scroll);
            getWindow().setDecorFitsSystemWindows(false);
            return;
        } else {
            long usedMin = Math.round(value / 60000.0);
            add(text(t(name + " is done for today", "انتهى وقت " + name + " لليوم"), 26, FG), 0);
            add(text(t("You used " + usedMin + " min of your " + limit + " min. It opens again tomorrow.",
                    "استخدمت " + usedMin + " دقيقة من " + limit + " دقيقة. يفتح غدًا."), 16, MUTED), 12);
        }
        add(button(t("Close it", "أغلقه"), true, v -> goHome()), 40);

        if (!COOLDOWN.equals(why)) {
            if (LIMIT.equals(why)) {
                String friction = Rules.friction(this);
                if ("wait".equals(friction)) addWait();
                else if ("type".equals(friction)) addType(name);
            }
            int left = Rules.emergencyLeft(this);
            if (left > 0) {
                add(link(t("Emergency: " + Rules.EMERGENCY_MIN + " minutes (" + left + " left this week)",
                        "طوارئ: " + Rules.EMERGENCY_MIN + " دقيقة (متبقٍ " + left + " هذا الأسبوع)"), v -> {
                    Rules.useEmergency(this, pkg);
                    reopen();
                }), 18);
            }
        }

        setContentView(scroll);
        getWindow().setDecorFitsSystemWindows(false);
    }

    /** 5 more minutes, after waiting 30 seconds. */
    private void addWait() {
        TextView b = button(t("5 more minutes", "5 دقائق إضافية"), false, null);
        add(b, 12);
        b.setOnClickListener(v -> {
            b.setEnabled(false);
            int[] s = {30};
            Runnable tick = new Runnable() {
                @Override
                public void run() {
                    if (s[0] > 0) {
                        b.setText(t("Wait " + s[0] + " s", "انتظر " + s[0] + " ث"));
                        s[0]--;
                        main.postDelayed(this, 1000);
                    } else {
                        b.setText(t("Unlock 5 minutes", "افتح 5 دقائق"));
                        b.setEnabled(true);
                        b.setOnClickListener(x -> unlock());
                    }
                }
            };
            tick.run();
        });
    }

    /** 5 more minutes, after typing a sentence exactly. */
    private void addType(String name) {
        String sentence = t("I choose to spend 5 more minutes on " + name + " instead of my goals.",
                "أختار أن أقضي 5 دقائق إضافية على " + name + " بدلًا من أهدافي.");
        TextView b = button(t("5 more minutes", "5 دقائق إضافية"), false, null);
        add(b, 12);
        b.setOnClickListener(v -> {
            b.setVisibility(View.GONE);
            add(text(t("Type this to unlock 5 minutes:", "اكتب هذا لفتح 5 دقائق:") + "\n“" + sentence + "”", 14, MUTED), 18);
            EditText in = field(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            add(in, 10);
            TextView go = button(t("Unlock 5 minutes", "افتح 5 دقائق"), false, x -> unlock());
            go.setEnabled(false);
            go.setAlpha(.4f);
            add(go, 10);
            in.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c) {}
                @Override public void onTextChanged(CharSequence s, int a, int b2, int c) {}
                @Override public void afterTextChanged(Editable e) {
                    boolean ok = e.toString().trim().equalsIgnoreCase(sentence);
                    go.setEnabled(ok);
                    go.setAlpha(ok ? 1f : .4f);
                }
            });
            in.requestFocus();
        });
    }

    /** "at 6:00 PM", or "tomorrow at 6:00 AM" (in Arabic: "الساعة 6:00 م"). */
    private String at(long when) {
        java.util.Calendar a = java.util.Calendar.getInstance(), b = java.util.Calendar.getInstance();
        b.setTimeInMillis(when);
        boolean same = a.get(java.util.Calendar.DAY_OF_YEAR) == b.get(java.util.Calendar.DAY_OF_YEAR);
        String clock = new java.text.SimpleDateFormat("h:mm", java.util.Locale.US).format(new java.util.Date(when));
        boolean am = b.get(java.util.Calendar.HOUR_OF_DAY) < 12;
        if (L.ar(this)) return (same ? "" : "غدًا ") + "الساعة " + clock + (am ? " ص" : " م");
        return (same ? "at " : "tomorrow at ") + clock + (am ? " AM" : " PM");
    }

    private String t(String en, String ar) {
        return L.t(this, en, ar);
    }

    private void unlock() {
        Rules.grant(this, pkg, Rules.EXTRA_MIN);
        reopen();
    }

    /** Back to the app, timed again from now. */
    private void reopen() {
        FocusAccessibility.unlocked(pkg);
        finish();
    }

    private void add(View v, int topDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(topDp);
        box.addView(v, lp);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private TextView button(String label, boolean primary, View.OnClickListener click) {
        TextView t = text(label, 16, primary ? BG : FG);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(30));
        if (primary) bg.setColor(FG);
        else bg.setStroke(dp(1), 0xFF3B3B41);
        t.setBackground(bg);
        t.setPadding(dp(20), dp(15), dp(20), dp(15));
        if (click != null) t.setOnClickListener(click);
        return t;
    }

    private TextView link(String label, View.OnClickListener click) {
        TextView t = text(label, 14, MUTED);
        t.setPadding(0, dp(10), 0, dp(10));
        if (click != null) t.setOnClickListener(click);
        return t;
    }

    private EditText field(int type) {
        EditText e = new EditText(this);
        e.setInputType(type);
        e.setTextColor(FG);
        e.setHintTextColor(MUTED);
        e.setTextSize(16);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(14));
        bg.setColor(0xFF141418);
        bg.setStroke(dp(1), 0xFF3B3B41);
        e.setBackground(bg);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        return e;
    }

    private int dp(int v) {
        return Math.round(v * d);
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goHome(); // back never returns to the blocked app
    }
}
