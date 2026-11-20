package tv.local.ytredirect;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.util.Calendar;
import java.util.Scanner;
import java.util.TimeZone;

/**
 * Bikram Sambat (Nepali) dates. BS month lengths follow no formula, so they come from a table
 * (res/raw/bs_calendar.json, from Summit and nepali-date-converter, both MIT: see NOTICES.md).
 */
final class BsDate {
    private static final String[] MONTHS = {"बैशाख", "जेठ", "असार", "श्रावण", "भदौ", "आश्विन",
            "कार्तिक", "मंसिर", "पुष", "माघ", "फाल्गुन", "चैत्र"};
    private static final String[] DAYS = {"आइतबार", "सोमबार", "मंगलबार", "बुधबार", "बिहिबार", "शुक्रबार", "शनिबार"};

    private static int startYear;
    private static long anchorDay;  // days since 1970-01-01 of BS startYear-01-01
    private static int[][] months;

    private BsDate() {}

    /**
     * e.g. "शनिबार, कार्तिक ७, २०८३" for the given local date; null if the table can't be read
     * or the date is outside its range. Always in Devanagari, like a printed Nepali calendar.
     */
    static String format(Context c, Calendar date) {
        if (!load(c)) return null;
        long days = epochDay(date) - anchorDay;
        if (days < 0) return null;
        for (int y = 0; y < months.length; y++) {
            for (int m = 0; m < 12; m++) {
                if (days < months[y][m]) {
                    String s = DAYS[date.get(Calendar.DAY_OF_WEEK) - 1] + ", " + MONTHS[m] + " "
                            + (days + 1) + ", " + (startYear + y);
                    return devanagari(s);
                }
                days -= months[y][m];
            }
        }
        return null;
    }

    private static synchronized boolean load(Context c) {
        if (months != null) return true;
        int id = c.getResources().getIdentifier("bs_calendar", "raw", c.getPackageName());
        if (id == 0) return false;
        try (InputStream in = c.getResources().openRawResource(id); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
            JSONObject o = new JSONObject(s.next());
            String[] ymd = o.getString("anchorAd").split("-");
            Calendar a = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            a.clear();
            a.set(Integer.parseInt(ymd[0]), Integer.parseInt(ymd[1]) - 1, Integer.parseInt(ymd[2]));
            anchorDay = Math.floorDiv(a.getTimeInMillis(), 86_400_000L);
            JSONArray rows = o.getJSONArray("months");
            int[][] m = new int[rows.length()][12];
            for (int y = 0; y < rows.length(); y++) {
                JSONArray r = rows.getJSONArray(y);
                for (int i = 0; i < 12; i++) m[y][i] = r.getInt(i);
            }
            startYear = o.getInt("startYear");
            months = m;
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Local calendar date as days since 1970-01-01, ignoring the time zone offset. */
    private static long epochDay(Calendar local) {
        Calendar u = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        u.clear();
        u.set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH));
        return Math.floorDiv(u.getTimeInMillis(), 86_400_000L);
    }

    static String devanagari(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append(ch >= '0' && ch <= '9' ? (char) ('\u0966' + (ch - '0')) : ch);
        }
        return b.toString();
    }
}
