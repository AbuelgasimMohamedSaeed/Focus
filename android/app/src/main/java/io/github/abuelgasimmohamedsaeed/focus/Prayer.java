package io.github.abuelgasimmohamedsaeed.focus;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * The five daily prayer times, worked out on the phone (no internet), with the standard astronomical method
 * (as in PrayTimes.org): Fajr and Isha by the sun's angle below the horizon, Asr by shadow length (Shafi'i).
 */
final class Prayer {
    static final String[] NAMES = {"Fajr", "Dhuhr", "Asr", "Maghrib", "Isha"};

    private Prayer() {}

    /** Today's times as minutes after local midnight: {fajr, dhuhr, asr, maghrib, isha}. */
    static int[] today(double lat, double lng, double fajrAngle, double ishaAngle, int ishaMinutesAfterMaghrib) {
        Calendar c = Calendar.getInstance();
        int y = c.get(Calendar.YEAR), m = c.get(Calendar.MONTH) + 1, d = c.get(Calendar.DAY_OF_MONTH);
        double tz = TimeZone.getDefault().getOffset(c.getTimeInMillis()) / 3600000.0;
        double jd = julian(y, m, d) - lng / (15 * 24.0);
        double fajr = sunAngleTime(jd, lat, fajrAngle, 5 / 24.0, true);
        double dhuhr = midDay(jd, 12 / 24.0);
        double asr = asrTime(jd, lat, 1, 13 / 24.0);
        double maghrib = sunAngleTime(jd, lat, 0.833, 18 / 24.0, false);
        double isha = ishaMinutesAfterMaghrib > 0 ? maghrib + ishaMinutesAfterMaghrib / 60.0 : sunAngleTime(jd, lat, ishaAngle, 18 / 24.0, false);
        double[] t = {fajr, dhuhr + 1 / 60.0, asr, maghrib + 1 / 60.0, isha};
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) out[i] = (int) Math.round(((t[i] + tz - lng / 15.0) % 24 + 24) % 24 * 60);
        return out;
    }

    private static double julian(int y, int m, int d) {
        if (m <= 2) { y -= 1; m += 12; }
        double a = Math.floor(y / 100.0), b = 2 - a + Math.floor(a / 4);
        return Math.floor(365.25 * (y + 4716)) + Math.floor(30.6001 * (m + 1)) + d + b - 1524.5;
    }

    /** {declination, equation of time} of the sun. */
    private static double[] sun(double jd) {
        double dd = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * dd, 360), q = fix(280.459 + 0.98564736 * dd, 360);
        double l = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360), e = 23.439 - 0.00000036 * dd;
        double ra = Math.toDegrees(Math.atan2(cos(e) * sin(l), cos(l))) / 15;
        return new double[]{Math.toDegrees(Math.asin(sin(e) * sin(l))), q / 15 - fix(ra, 24)};
    }

    private static double midDay(double jd, double t) {
        return fix(12 - sun(jd + t)[1], 24);
    }

    private static double sunAngleTime(double jd, double lat, double angle, double t, boolean beforeNoon) {
        double decl = sun(jd + t)[0], noon = midDay(jd, t);
        double x = (-sin(angle) - sin(decl) * sin(lat)) / (cos(decl) * cos(lat));
        double h = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, x)))) / 15;
        return noon + (beforeNoon ? -h : h);
    }

    private static double asrTime(double jd, double lat, double factor, double t) {
        double decl = sun(jd + t)[0];
        double angle = -Math.toDegrees(Math.atan(1 / (factor + Math.tan(Math.toRadians(Math.abs(lat - decl))))));
        return sunAngleTime(jd, lat, angle, t, false);
    }

    private static double sin(double deg) { return Math.sin(Math.toRadians(deg)); }
    private static double cos(double deg) { return Math.cos(Math.toRadians(deg)); }
    private static double fix(double a, double b) { a = a - b * Math.floor(a / b); return a < 0 ? a + b : a; }
}
