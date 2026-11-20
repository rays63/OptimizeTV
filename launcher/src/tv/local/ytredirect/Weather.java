package tv.local.ytredirect;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;

/**
 * Current weather, today's parts of the day and the next three days, from Open-Meteo.
 * Always fetched in metric and converted for display, so changing units needs no refetch.
 * The last response is cached, so the chip still shows something when offline.
 * Summary and day-part logic follow Summit's (MIT).
 */
final class Weather {
    static final long MAX_AGE_MS = 30 * 60_000L;
    private static final String CACHE = "weatherJson", CACHE_AT = "weatherAt", CACHE_LOC = "weatherLoc";

    double temp, wind, hi, lo, rainToday;
    int code;
    boolean isDay = true;
    final Part[] parts = {new Part(), new Part(), new Part(), new Part()};  // morning, afternoon, evening, night
    final List<Day> next = new ArrayList<>();

    static final class Part {
        int code, rainProb, rainFromHour = -1;
        double temp;
    }

    static final class Day {
        String name;
        int code, rainProb;
        double hi, lo;
    }

    /** Cached weather if fresh, else downloads. Blocking: call off the main thread. Null if none. */
    static Weather load(Context c) {
        SharedPreferences p = Options.prefs(c);
        float lat = p.getFloat("lat", HomeActivity.DEFAULT_LAT), lon = p.getFloat("lon", HomeActivity.DEFAULT_LON);
        String loc = lat + "," + lon;
        String json = p.getString(CACHE, null);
        boolean sameLoc = loc.equals(p.getString(CACHE_LOC, ""));
        if (!sameLoc || System.currentTimeMillis() - p.getLong(CACHE_AT, 0) > MAX_AGE_MS) {
            try {
                json = get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code,wind_speed_10m,is_day"
                        + "&hourly=temperature_2m,precipitation_probability,weather_code"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,precipitation_sum"
                        + "&timezone=auto&forecast_days=4");
                p.edit().putString(CACHE, json).putLong(CACHE_AT, System.currentTimeMillis()).putString(CACHE_LOC, loc).apply();
            } catch (Exception e) {
                if (!sameLoc) return null;  // never show another city's weather
            }
        }
        if (json == null) return null;
        try {
            return parse(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** The cached weather only, never downloading. Null if none or for another place. */
    static Weather cached(Context c) {
        SharedPreferences p = Options.prefs(c);
        String loc = p.getFloat("lat", HomeActivity.DEFAULT_LAT) + "," + p.getFloat("lon", HomeActivity.DEFAULT_LON);
        String json = p.getString(CACHE, null);
        if (json == null || !loc.equals(p.getString(CACHE_LOC, ""))) return null;
        try {
            return parse(json);
        } catch (Exception e) {
            return null;
        }
    }

    /** True when the cache is missing, stale or for another place. */
    static boolean stale(Context c) {
        SharedPreferences p = Options.prefs(c);
        String loc = p.getFloat("lat", HomeActivity.DEFAULT_LAT) + "," + p.getFloat("lon", HomeActivity.DEFAULT_LON);
        return !loc.equals(p.getString(CACHE_LOC, "")) || System.currentTimeMillis() - p.getLong(CACHE_AT, 0) > MAX_AGE_MS;
    }

    static Weather parse(String json) throws Exception {
        Weather w = new Weather();
        JSONObject o = new JSONObject(json);
        JSONObject cur = o.getJSONObject("current");
        w.temp = cur.getDouble("temperature_2m");
        w.code = cur.getInt("weather_code");
        w.wind = cur.optDouble("wind_speed_10m", 0);
        w.isDay = cur.optInt("is_day", 1) == 1;

        JSONObject d = o.getJSONObject("daily");
        w.hi = d.getJSONArray("temperature_2m_max").getDouble(0);
        w.lo = d.getJSONArray("temperature_2m_min").getDouble(0);
        w.rainToday = d.getJSONArray("precipitation_sum").optDouble(0, 0);
        JSONArray times = d.getJSONArray("time");
        SimpleDateFormat in = new SimpleDateFormat("yyyy-MM-dd", Locale.US), out = new SimpleDateFormat("EEEE", Locale.ENGLISH);
        for (int i = 1; i < Math.min(4, times.length()); i++) {
            Day day = new Day();
            day.name = out.format(in.parse(times.getString(i)));
            day.code = d.getJSONArray("weather_code").getInt(i);
            day.hi = d.getJSONArray("temperature_2m_max").getDouble(i);
            day.lo = d.getJSONArray("temperature_2m_min").getDouble(i);
            day.rainProb = d.getJSONArray("precipitation_probability_max").optInt(i, 0);
            w.next.add(day);
        }

        // Hourly data starts at today 00:00 local time; night runs into tomorrow 06:00.
        JSONObject h = o.getJSONObject("hourly");
        int[][] ranges = {{6, 12}, {12, 17}, {17, 21}, {21, 30}};
        for (int i = 0; i < 4; i++) {
            w.parts[i] = part(h.getJSONArray("weather_code"), h.getJSONArray("temperature_2m"),
                    h.getJSONArray("precipitation_probability"), ranges[i][0], ranges[i][1]);
        }
        return w;
    }

    /** Most common code in the range; an hour with 50%+ rain chance marks the part as wet. */
    private static Part part(JSONArray codes, JSONArray temps, JSONArray probs, int from, int to) {
        Part p = new Part();
        Map<Integer, Integer> count = new HashMap<>();
        double sum = 0;
        int n = 0, wet = -1;
        for (int i = from; i < to && i < codes.length(); i++) {
            int code = codes.optInt(i);
            count.merge(code, 1, Integer::sum);
            sum += temps.optDouble(i, 0);
            n++;
            int prob = probs.optInt(i, 0);
            p.rainProb = Math.max(p.rainProb, prob);
            if (prob >= 50 && isWet(code)) {
                wet = Math.max(wet, code);
                if (p.rainFromHour < 0) p.rainFromHour = i % 24;
            }
        }
        int best = 0, bestN = -1;
        for (Map.Entry<Integer, Integer> e : count.entrySet()) {
            if (e.getValue() > bestN) {
                best = e.getKey();
                bestN = e.getValue();
            }
        }
        p.code = wet >= 0 ? wet : best;
        p.temp = n == 0 ? 0 : sum / n;
        return p;
    }

    static boolean isWet(int code) {
        return code >= 51;
    }

    // ---- Text ----

    /** WMO weather codes, as used by Open-Meteo. */
    static String label(int code, boolean day) {
        if (code == 0) return day ? "Sunny" : "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code == 45 || code == 48) return "Fog";
        if ((code >= 71 && code <= 77) || code == 85 || code == 86) return "Snow";
        if (code >= 95) return "Thunderstorm";
        if (code >= 51) return "Rain";
        return "Cloudy";
    }

    /** e.g. "Sunny morning, rain from 2 PM, clear tonight". */
    String summary(Context c) {
        String m = label(parts[0].code, true), a = label(parts[1].code, true), n = label(parts[3].code, false);
        if (parts[0].code == parts[1].code && parts[1].code == parts[3].code && !isWet(parts[1].code)) {
            return m + " all day";
        }
        List<String> out = new ArrayList<>();
        out.add(m + " morning");
        if (parts[1].rainFromHour >= 0) {
            int hr = parts[1].rainFromHour;
            out.add("rain from " + Options.digits(c, String.valueOf(hr % 12 == 0 ? 12 : hr % 12)) + (hr < 12 ? " AM" : " PM"));
        } else if (parts[1].code != parts[0].code) {
            out.add(a.toLowerCase(Locale.ENGLISH) + " afternoon");
        }
        out.add(n.toLowerCase(Locale.ENGLISH) + " tonight");
        return String.join(", ", out);
    }

    static String temp(Context c, double celsius) {
        long v = "f".equals(Options.str(c, Options.TEMP_UNIT)) ? Math.round(celsius * 9 / 5 + 32) : Math.round(celsius);
        return Options.digits(c, v + "°");
    }

    static String wind(Context c, double kmh) {
        switch (Options.str(c, Options.WIND_UNIT)) {
            case "ms": return Options.digits(c, (Math.round(kmh / 3.6 * 10) / 10.0) + " m/s");
            case "mph": return Options.digits(c, Math.round(kmh / 1.609) + " mph");
            default: return Options.digits(c, Math.round(kmh) + " km/h");
        }
    }

    static String rain(Context c, double mm) {
        return "in".equals(Options.str(c, Options.RAIN_UNIT))
                ? Options.digits(c, (Math.round(mm / 25.4 * 100) / 100.0) + " in")
                : Options.digits(c, Math.round(mm) + " mm");
    }

    static String pct(Context c, int p) {
        return Options.digits(c, p + "%");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(8000);
        con.setReadTimeout(8000);
        try (InputStream in = con.getInputStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
            return s.next();
        } finally {
            con.disconnect();
        }
    }
}
