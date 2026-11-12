package io.github.abuelgasimmohamedsaeed.focus;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowInsets;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** The full block screen shown over an app whose time is up for today. */
public class BlockActivity extends Activity {
    static final String EXTRA_PKG = "pkg", EXTRA_USED = "used", EXTRA_LIMIT = "limit";

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
        String pkg = i.getStringExtra(EXTRA_PKG);
        int limit = i.getIntExtra(EXTRA_LIMIT, 0);
        long usedMin = Math.round(i.getLongExtra(EXTRA_USED, 0) / 60000.0);
        float d = getResources().getDisplayMetrics().density;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setBackgroundColor(0xFF09090B);
        int pad = Math.round(32 * d);
        box.setPadding(pad, pad, pad, pad);
        box.setOnApplyWindowInsetsListener((v, ins) -> {
            android.graphics.Insets b = ins.getInsets(WindowInsets.Type.systemBars());
            v.setPadding(pad + b.left, pad + b.top, pad + b.right, pad + b.bottom);
            return WindowInsets.CONSUMED;
        });

        ImageView icon = new ImageView(this);
        try {
            Drawable dr = getPackageManager().getApplicationIcon(pkg);
            icon.setImageDrawable(dr);
        } catch (Exception ignored) {
        }
        icon.setAlpha(.5f);
        int is = Math.round(72 * d);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(is, is);
        ilp.bottomMargin = Math.round(28 * d);
        box.addView(icon, ilp);

        TextView title = text(AppUsage.name(this, pkg) + " is done for today", 26, 0xFFECEAE4);
        box.addView(title);
        TextView sub = text("You used " + usedMin + " min of your " + limit + " min. It opens again tomorrow.", 16, 0xFF8B8A86);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-2, -2);
        slp.topMargin = Math.round(12 * d);
        box.addView(sub, slp);

        TextView home = text("Close it", 17, 0xFF09090B);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFECEAE4);
        bg.setCornerRadius(999);
        home.setBackground(bg);
        home.setPadding(Math.round(36 * d), Math.round(16 * d), Math.round(36 * d), Math.round(16 * d));
        home.setOnClickListener(v -> goHome());
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-2, -2);
        hlp.topMargin = Math.round(40 * d);
        box.addView(home, hlp);

        setContentView(box);
        getWindow().setDecorFitsSystemWindows(false);
    }

    private TextView text(String s, int sp, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setGravity(Gravity.CENTER);
        return t;
    }

    private void goHome() {
        startActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goHome(); // back never returns to the blocked app
    }
}
