package io.github.abuelgasimmohamedsaeed.focus;

import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Locale;

/** Website blocking: which apps are browsers, where their address bar is, and whether its address is blocked. */
final class Sites {
    // browser package -> view id of its address bar
    private static final String[][] BARS = {
            {"com.android.chrome", "url_bar"}, {"com.chrome.beta", "url_bar"}, {"com.chrome.dev", "url_bar"},
            {"com.brave.browser", "url_bar"}, {"com.microsoft.emmx", "url_bar"}, {"com.vivaldi.browser", "url_bar"},
            {"com.sec.android.app.sbrowser", "location_bar_edit_text"},
            {"org.mozilla.firefox", "mozac_browser_toolbar_url_view"}, {"org.mozilla.firefox_beta", "mozac_browser_toolbar_url_view"},
            {"com.opera.browser", "url_field"}, {"com.opera.mini.native", "url_field"},
            {"com.mi.globalbrowser", "url"}, {"com.android.browser", "url"}, {"com.duckduckgo.mobile.android", "omnibarTextInput"},
    };

    private Sites() {}

    static boolean browser(String pkg) {
        for (String[] b : BARS) if (b[0].equals(pkg)) return true;
        return false;
    }

    /** The text in the browser's address bar, or "" when it can't be found. */
    static String address(AccessibilityNodeInfo root, String pkg) {
        if (root == null) return "";
        for (String[] b : BARS) {
            if (!b[0].equals(pkg)) continue;
            List<AccessibilityNodeInfo> found = root.findAccessibilityNodeInfosByViewId(pkg + ":id/" + b[1]);
            if (found != null && !found.isEmpty() && found.get(0).getText() != null) return found.get(0).getText().toString();
        }
        // unknown layout: the first text field near the top of the window
        ArrayDeque<AccessibilityNodeInfo> q = new ArrayDeque<>();
        q.add(root);
        int seen = 0;
        while (!q.isEmpty() && seen++ < 120) {
            AccessibilityNodeInfo n = q.poll();
            if (n == null) continue;
            if (n.getClassName() != null && n.getClassName().toString().endsWith("EditText") && n.getText() != null) return n.getText().toString();
            for (int i = 0; i < n.getChildCount(); i++) q.add(n.getChild(i));
        }
        return "";
    }

    /** The blocked site this address is on (like "instagram.com"), or null. */
    static String blocked(String address, org.json.JSONArray sites) {
        if (sites == null || address == null || address.isEmpty()) return null;
        String a = address.trim().toLowerCase(Locale.US).replaceFirst("^[a-z]+://", "");
        int cut = a.length();
        for (char ch : new char[]{'/', '?', '#', ' ', ':'}) {
            int i = a.indexOf(ch);
            if (i >= 0 && i < cut) cut = i;
        }
        String host = a.substring(0, cut);
        if (!host.contains(".")) return null; // a search, not an address
        for (int i = 0; i < sites.length(); i++) {
            String d = sites.optString(i);
            if (d.isEmpty()) continue;
            if (host.equals(d) || host.endsWith("." + d)) return d;
            if ("facebook.com".equals(d) && (host.equals("fb.com") || host.endsWith(".fb.com") || host.endsWith(".fb.watch") || host.equals("fb.watch"))) return d;
        }
        return null;
    }
}
