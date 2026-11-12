package io.github.abuelgasimmohamedsaeed.focus;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONObject;

import java.util.TimeZone;

/**
 * Where prayer times are worked out for. With location allowed, the phone's own position (one rough network fix,
 * refreshed at most every 6 hours when Focus opens, so almost no battery). Without it, the city that matches the
 * phone's time zone. Either way the calculation method is the one used in the nearest city on the list.
 */
final class Place {
    // method: Fajr angle, Isha angle, Isha minutes after Maghrib (0 = use the angle)
    private static final double[] JAKIM = {20, 18, 0}, EGYPT = {19.5, 17.5, 0}, UMM_AL_QURA = {18.5, 0, 90},
            GULF = {18.2, 18.2, 0}, QATAR = {18, 0, 90}, KUWAIT = {18, 17.5, 0}, TURKEY = {18, 17, 0},
            KARACHI = {18, 18, 0}, MWL = {18, 17, 0}, ISNA = {15, 15, 0};

    private static final Object[][] CITIES = { // name, lat, lng, method, time zone
            {"Kuala Lumpur", 3.139, 101.687, JAKIM, "Asia/Kuala_Lumpur"},
            {"Penang", 5.414, 100.329, JAKIM, ""},
            {"Johor Bahru", 1.4927, 103.7414, JAKIM, ""},
            {"Kota Kinabalu", 5.9804, 116.0735, JAKIM, ""},
            {"Kuching", 1.5535, 110.3593, JAKIM, "Asia/Kuching"},
            {"Singapore", 1.3521, 103.8198, JAKIM, "Asia/Singapore"},
            {"Jakarta", -6.2088, 106.8456, JAKIM, "Asia/Jakarta"},
            {"Bandar Seri Begawan", 4.9031, 114.9398, JAKIM, "Asia/Brunei"},
            {"Bangkok", 13.7563, 100.5018, MWL, "Asia/Bangkok"},
            {"Khartoum", 15.5007, 32.5599, EGYPT, "Africa/Khartoum"},
            {"Port Sudan", 19.6158, 37.2164, EGYPT, ""},
            {"Cairo", 30.0444, 31.2357, EGYPT, "Africa/Cairo"},
            {"Makkah", 21.4225, 39.8262, UMM_AL_QURA, "Asia/Riyadh"},
            {"Madinah", 24.5247, 39.5692, UMM_AL_QURA, ""},
            {"Riyadh", 24.7136, 46.6753, UMM_AL_QURA, ""},
            {"Dubai", 25.2048, 55.2708, GULF, "Asia/Dubai"},
            {"Doha", 25.2854, 51.531, QATAR, "Asia/Qatar"},
            {"Kuwait City", 29.3759, 47.9774, KUWAIT, "Asia/Kuwait"},
            {"Istanbul", 41.0082, 28.9784, TURKEY, "Europe/Istanbul"},
            {"Karachi", 24.8607, 67.0011, KARACHI, "Asia/Karachi"},
            {"Delhi", 28.6139, 77.209, KARACHI, "Asia/Kolkata"},
            {"Dhaka", 23.8103, 90.4125, KARACHI, "Asia/Dhaka"},
            {"Addis Ababa", 9.03, 38.74, MWL, "Africa/Addis_Ababa"},
            {"Nairobi", -1.2921, 36.8219, MWL, "Africa/Nairobi"},
            {"London", 51.5074, -0.1278, MWL, "Europe/London"},
            {"New York", 40.7128, -74.006, ISNA, "America/New_York"},
    };

    private Place() {}

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("focus", Context.MODE_PRIVATE);
    }

    static boolean allowed(Context c) {
        return c.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /** {name, lat, lng, fajr, isha, ishaMin, gps}: where prayer times are worked out for right now. */
    static JSONObject get(Context c) {
        SharedPreferences p = prefs(c);
        boolean gps = allowed(c) && p.contains("placeLat");
        double lat, lng;
        Object[] city;
        if (gps) {
            lat = Double.longBitsToDouble(p.getLong("placeLat", 0));
            lng = Double.longBitsToDouble(p.getLong("placeLng", 0));
            city = nearest(lat, lng);
        } else {
            city = byTimeZone();
            lat = (double) city[1];
            lng = (double) city[2];
        }
        double[] m = (double[]) city[3];
        try {
            return new JSONObject().put("name", gps ? "Your location, near " + city[0] : (String) city[0])
                    .put("lat", lat).put("lng", lng).put("fajr", m[0]).put("isha", m[1]).put("ishaMin", (int) m[2]).put("gps", gps);
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static Object[] nearest(double lat, double lng) {
        Object[] best = CITIES[0];
        double bestD = Double.MAX_VALUE;
        for (Object[] c : CITIES) {
            double dLat = lat - (double) c[1], dLng = (lng - (double) c[2]) * Math.cos(Math.toRadians(lat));
            double d = dLat * dLat + dLng * dLng;
            if (d < bestD) {
                bestD = d;
                best = c;
            }
        }
        return best;
    }

    /** No location: the listed city in the phone's time zone, else one with the same UTC offset, else Makkah. */
    private static Object[] byTimeZone() {
        TimeZone tz = TimeZone.getDefault();
        for (Object[] c : CITIES) if (tz.getID().equals(c[4])) return c;
        for (Object[] c : CITIES) {
            String id = (String) c[4];
            if (!id.isEmpty() && TimeZone.getTimeZone(id).getRawOffset() == tz.getRawOffset()) return c;
        }
        return CITIES[12];
    }

    /** Take a fresh rough position if the last one is older than 6 hours (or force), then call done on the main thread. */
    static void refresh(Context c, boolean force, Runnable done) {
        if (!allowed(c)) return;
        SharedPreferences p = prefs(c);
        if (!force && System.currentTimeMillis() - p.getLong("placeAt", 0) < 6 * 3600_000L) return;
        LocationManager lm = c.getSystemService(LocationManager.class);
        if (lm == null) return;
        Location last = null;
        try {
            for (String provider : lm.getProviders(true)) {
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (last == null || l.getTime() > last.getTime())) last = l;
            }
        } catch (SecurityException ignored) {
        }
        if (last != null) store(c, last);
        try {
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.getCurrentLocation(LocationManager.NETWORK_PROVIDER, null, c.getMainExecutor(), l -> {
                    if (l != null) store(c, l);
                    if (done != null) done.run();
                });
                return;
            }
        } catch (Exception ignored) {
        }
        if (done != null) c.getMainExecutor().execute(done);
    }

    private static void store(Context c, Location l) {
        prefs(c).edit().putLong("placeLat", Double.doubleToRawLongBits(l.getLatitude()))
                .putLong("placeLng", Double.doubleToRawLongBits(l.getLongitude()))
                .putLong("placeAt", System.currentTimeMillis()).apply();
    }
}
