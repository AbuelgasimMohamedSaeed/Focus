package io.github.abuelgasimmohamedsaeed.focus;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.provider.Settings;

/**
 * Gray screen: turns the whole phone black and white with Android's own color-correction setting (the one under
 * Accessibility). Changing it needs a permission Android only gives through a one-time command from a computer
 * (see "Gray screen setup.bat"). Focus only turns gray off again if Focus was the one that turned it on.
 */
final class Gray {
    private static final String ON = "accessibility_display_daltonizer_enabled", MODE = "accessibility_display_daltonizer";

    private Gray() {}

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) == PackageManager.PERMISSION_GRANTED;
    }

    static void set(Context c, boolean gray) {
        if (!allowed(c)) return;
        SharedPreferences p = c.getSharedPreferences("focus", Context.MODE_PRIVATE);
        boolean byUs = p.getBoolean("grayByUs", false);
        try {
            boolean isOn = Settings.Secure.getInt(c.getContentResolver(), ON, 0) == 1
                    && Settings.Secure.getInt(c.getContentResolver(), MODE, -1) == 0;
            if (gray && !isOn) {
                Settings.Secure.putInt(c.getContentResolver(), MODE, 0); // 0 = monochrome
                Settings.Secure.putInt(c.getContentResolver(), ON, 1);
                p.edit().putBoolean("grayByUs", true).apply();
            } else if (!gray && isOn && byUs) {
                Settings.Secure.putInt(c.getContentResolver(), ON, 0);
                p.edit().putBoolean("grayByUs", false).apply();
            }
        } catch (Exception ignored) {
        }
    }
}
