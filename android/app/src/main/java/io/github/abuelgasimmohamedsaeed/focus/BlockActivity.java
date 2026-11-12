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
 * The full block screen over an app whose time is up today, or that is cooling down.
 * Ways out, if you set them up on the App Time page: 5 more minutes after waiting 30 seconds or typing a sentence,
 * an emergency unlock (a few per week), or a pause with your PIN.
 */
public class BlockActivity extends Activity {
    static final String EXTRA_PKG = "pkg", EXTRA_WHY = "why", EXTRA_VALUE = "value", EXTRA_LIMIT = "limit";
    static final String LIMIT = "limit", COOLDOWN = "cooldown";
    private static final int BG = 0xFF09090B, FG = 0xFFECEAE4, MUTED = 0xFF8B8A86;
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

        if (COOLDOWN.equals(why)) {
            long min = Math.max(1, (value + 59999) / 60000);
            add(text("Take a short break", 26, FG), 0);
            add(text(name + " opens again in " + min + " min.", 16, MUTED), 12);
        } else {
            long usedMin = Math.round(value / 60000.0);
            add(text(name + " is done for today", 26, FG), 0);
            add(text("You used " + usedMin + " min of your " + limit + " min. It opens again tomorrow.", 16, MUTED), 12);
        }
        add(button("Close it", true, v -> goHome()), 40);

        if (LIMIT.equals(why)) {
            String friction = Rules.friction(this);
            if ("wait".equals(friction)) addWait();
            else if ("type".equals(friction)) addType(name);
            int left = Rules.emergencyLeft(this);
            if (left > 0) {
                add(link("Emergency: " + Rules.EMERGENCY_MIN + " minutes (" + left + " left this week)", v -> {
                    Rules.useEmergency(this, pkg);
                    reopen();
                }), 18);
            }
        }
        if (Rules.hasPin(this)) addPin();

        setContentView(scroll);
        getWindow().setDecorFitsSystemWindows(false);
    }

    /** 5 more minutes, after waiting 30 seconds. */
    private void addWait() {
        TextView b = button("5 more minutes", false, null);
        add(b, 12);
        b.setOnClickListener(v -> {
            b.setEnabled(false);
            int[] s = {30};
            Runnable tick = new Runnable() {
                @Override
                public void run() {
                    if (s[0] > 0) {
                        b.setText("Wait " + s[0]-- + " s");
                        main.postDelayed(this, 1000);
                    } else {
                        b.setText("Unlock 5 minutes");
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
        String sentence = "I choose to spend 5 more minutes on " + name + " instead of my goals.";
        TextView b = button("5 more minutes", false, null);
        add(b, 12);
        b.setOnClickListener(v -> {
            b.setVisibility(View.GONE);
            add(text("Type this to unlock 5 minutes:\n“" + sentence + "”", 14, MUTED), 18);
            EditText in = field(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            add(in, 10);
            TextView go = button("Unlock 5 minutes", false, x -> unlock());
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

    /** A friend can pause all limits for an hour with the PIN. */
    private void addPin() {
        TextView l = link("Pause limits with PIN", null);
        add(l, 18);
        l.setOnClickListener(v -> {
            l.setVisibility(View.GONE);
            EditText in = field(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
            in.setHint("PIN");
            add(in, 10);
            TextView msg = text("", 13, MUTED);
            add(button("Pause for 1 hour", false, x -> {
                if (Rules.pinMatches(this, in.getText().toString())) {
                    Rules.pause(this, Rules.PAUSE_MIN);
                    reopen();
                } else {
                    msg.setText("That PIN isn't right.");
                    in.setText("");
                }
            }), 10);
            add(msg, 8);
            in.requestFocus();
        });
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
