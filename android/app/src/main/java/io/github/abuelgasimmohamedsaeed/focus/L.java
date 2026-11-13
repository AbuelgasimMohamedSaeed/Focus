package io.github.abuelgasimmohamedsaeed.focus;

import android.content.Context;

/** The language you picked in Focus (English or Arabic), for the few screens Android draws itself. */
final class L {
    private L() {}

    static boolean ar(Context c) {
        return "ar".equals(c.getSharedPreferences("focus", Context.MODE_PRIVATE).getString("lang", "en"));
    }

    static String t(Context c, String en, String ar) {
        return ar(c) ? ar : en;
    }

    static void set(Context c, String lang) {
        c.getSharedPreferences("focus", Context.MODE_PRIVATE).edit().putString("lang", "ar".equals(lang) ? "ar" : "en").apply();
    }
}
