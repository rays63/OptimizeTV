package tv.local.ytredirect;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Every setting in one place: its key, default and choices. Settings shows these; the home
 * screen, Apps and the remote helper read them. All live in the "home" preferences file.
 */
final class Options {
    private Options() {}

    static final String FILE = "home";

    // Background
    static final String PHOTO = "photo";                 // true: daily photo, false: drawn scene
    static final String SHOW_CAPTION = "showCaption";
    static final String PHOTO_SOURCE = "photoSource";    // bing, nasa, wiki, unsplash
    static final String ACCENT = "accent";               // photo: selection colour from the photo; white
    // Weather
    static final String CITY = "city";
    static final String TEMP_UNIT = "tempUnit";          // c, f
    static final String WIND_UNIT = "windUnit";          // kmh, ms, mph
    static final String RAIN_UNIT = "rainUnit";          // mm, in
    static final String SHOW_WEATHER = "showWeather";
    // Date
    static final String CALENDAR = "calendar";           // both, bs, ad
    static final String DIGITS = "digits";               // true: Devanagari digits
    static final String PRIMARY_DATE = "primaryDate";    // bs, ad: which date is big and bold
    static final String DATE_POS = "datePos";            // right, above, below (greeting and dates vs the clock)
    // Home screen
    static final String NAME = "name";
    static final String ORDER = "order";
    // Icons & tiles
    static final String APP_NAMES = "appNames";          // selected, always, off
    static final String LAYOUT_SIZE = "layoutSize";      // large, medium, small
    // Remote buttons: a package name, SWITCHER or TV_DEFAULT
    static final String BTN_YOUTUBE = "btnYoutube";
    static final String BTN_PRIME = "btnPrime";
    static final String BTN_CHANNEL = "btnChannel";
    static final String SWITCHER = "@switcher";
    static final String TV_DEFAULT = "@default";

    static final String SMARTTUBE = "org.smarttube.stable";
    static final String SPOTIFY = "com.spotify.tv.android";

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static String str(Context c, String key) {
        return prefs(c).getString(key, defaultStr(key));
    }

    static boolean bool(Context c, String key) {
        return prefs(c).getBoolean(key, defaultBool(key));
    }

    static String defaultStr(String key) {
        switch (key) {
            case CITY: return HomeActivity.DEFAULT_CITY;
            case NAME: return HomeActivity.DEFAULT_NAME;
            case TEMP_UNIT: return "c";
            case WIND_UNIT: return "kmh";
            case RAIN_UNIT: return "mm";
            case CALENDAR: return "both";
            case PHOTO_SOURCE: return "bing";
            case ACCENT: return "photo";
            case DATE_POS: return "right";
            case PRIMARY_DATE: return "bs";
            case APP_NAMES: return "selected";
            case LAYOUT_SIZE: return "large";
            case BTN_YOUTUBE: return SMARTTUBE;
            case BTN_PRIME: return SPOTIFY;
            case BTN_CHANNEL: return SWITCHER;
            default: return "";
        }
    }

    static boolean defaultBool(String key) {
        return true;  // photo, captions, weather and Devanagari digits all start on
    }

    /** 0-9 to Devanagari digits when that setting is on. */
    static String digits(Context c, String s) {
        if (s == null || !bool(c, DIGITS)) return s;
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            b.append(ch >= '0' && ch <= '9' ? (char) ('\u0966' + (ch - '0')) : ch);
        }
        return b.toString();
    }
}
