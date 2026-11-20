package tv.local.ytredirect;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Home screen: Bing photo of the day (cached for offline), a big clock with the Nepali and
 * English dates, a weather chip that opens the forecast, and the Favourites row. Hold OK on an
 * app to move it; the order is remembered. Layout modelled on Summit (see NOTICES.md).
 */
public class HomeActivity extends Activity {
    static final String DEFAULT_NAME = "Ray";
    static final String DEFAULT_CITY = "Kathmandu";
    static final float DEFAULT_LAT = 27.7172f, DEFAULT_LON = 85.3240f;
    private static final long WALLPAPER_RETRY_MS = 15 * 60_000L;
    /** Default order before you rearrange anything; other apps follow alphabetically. */
    private static final List<String> PINNED = Arrays.asList(
            "com.netflix.ninja", "org.smarttube.stable", "np.com.geniussystems.android.stb.iptv",
            "com.stremio.one", "com.spotify.tv.android", "com.tcl.browser");

    /** Tag of the fixed All apps tile at the end of the row. */
    private static final String ALL_APPS = "__all_apps";
    private static final int TILE_W = 176, TILE_H = 99, TILE_GAP = 22;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private DailyWallpaper wallpaper;
    private SceneView scene;
    private ImageView photo;
    private FrameLayout root;
    private LinearLayout page;
    private TextView clock, greeting, bsDate, adDate, caption, favLabel, favHint;
    private LinearLayout weatherChip;
    private WeatherIcon weatherIcon;
    private TextView weatherTemp, weatherCond, weatherHiLo;
    private Weather lastWeather;
    private View forecast;
    private boolean fetchingWeather;
    private HorizontalScrollView scroller;
    private LinearLayout row;
    private long lastWallpaperTry;
    private boolean downloading;
    /** Tile currently being moved, or null. */
    private View moving;
    /** The OK release that ends the hold which started a move must not also end it. */
    private boolean swallowOkUp;

    /** Clock and dates, redrawn on each minute boundary. */
    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            long now = System.currentTimeMillis();
            handler.postDelayed(this, 60_000 - now % 60_000 + 50);
        }
    };

    /** Slower housekeeping: scene, weather, wallpaper. */
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            scene.invalidate();
            if (Weather.stale(HomeActivity.this)) loadWeather();
            long now = System.currentTimeMillis();
            if (wallpaper.isStale() && !downloading && now - lastWallpaperTry > WALLPAPER_RETRY_MS) fetchWallpaper();
            handler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = Options.prefs(this);
        wallpaper = new DailyWallpaper(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Drawn scene is the fallback until the first photo has downloaded.
        scene = new SceneView(this);
        root.addView(scene);
        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photo);
        showWallpaper(false);

        // Scrims: keep text readable on bright photos without darkening the whole picture.
        root.addView(Ui.gradient(this, GradientDrawable.Orientation.LEFT_RIGHT,
                0x8C000000, 0x33000000, 0x00000000), new FrameLayout.LayoutParams(dp(620), -1));
        root.addView(Ui.gradient(this, GradientDrawable.Orientation.TOP_BOTTOM,
                0x66000000, 0x00000000), new FrameLayout.LayoutParams(-1, dp(150)));
        root.addView(Ui.gradient(this, GradientDrawable.Orientation.BOTTOM_TOP,
                0xD9000000, 0x80000000, 0x00000000), new FrameLayout.LayoutParams(-1, dp(380), Gravity.BOTTOM));

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        page.setPadding(0, dp(Ui.SAFE_Y), 0, dp(Ui.SAFE_Y));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        page.addView(topBar());

        // The big clock, with the greeting and both dates beside it.
        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clockRow.setPadding(dp(Ui.SAFE_X - 4), dp(4), dp(Ui.SAFE_X), 0);
        clock = Ui.onPhoto(this, Ui.text(this, "", 112, Typeface.create("sans-serif-thin", Typeface.NORMAL), Ui.INK));
        clock.setLetterSpacing(-0.02f);
        clockRow.addView(clock);
        LinearLayout side = new LinearLayout(this);
        side.setOrientation(LinearLayout.VERTICAL);
        side.setPadding(dp(28), dp(4), 0, 0);
        greeting = Ui.onPhoto(this, Ui.text(this, "", 20, Ui.LIGHT, Ui.INK_2));
        side.addView(greeting);
        bsDate = Ui.onPhoto(this, Ui.text(this, "", 24, Ui.MEDIUM, Ui.INK));
        bsDate.setPadding(0, dp(8), 0, 0);
        side.addView(bsDate);
        adDate = Ui.onPhoto(this, Ui.text(this, "", 15, Ui.REGULAR, Ui.INK_2));
        adDate.setPadding(0, dp(7), 0, 0);
        side.addView(adDate);
        clockRow.addView(side);
        page.addView(clockRow);

        // Favourites sit at the bottom, with the photo showing in between.
        page.addView(new View(this), new LinearLayout.LayoutParams(-1, 0, 1));

        // Favourites header: label and hint on the left, photo location on the right.
        LinearLayout favHeader = new LinearLayout(this);
        favHeader.setGravity(Gravity.BOTTOM);
        favHeader.setPadding(dp(Ui.SAFE_X), 0, dp(Ui.SAFE_X), 0);
        favLabel = Ui.onPhoto(this, Ui.text(this, "Favourites", 15, Ui.MEDIUM, Ui.INK_2));
        favHeader.addView(favLabel);
        favHint = Ui.onPhoto(this, Ui.text(this, "   hold OK to move", 14, Ui.REGULAR, Ui.INK_3));
        favHeader.addView(favHint);
        caption = Ui.onPhoto(this, Ui.text(this, "", 13, Ui.REGULAR, Ui.INK_3));
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        caption.setGravity(Gravity.END);
        favHeader.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        caption.setText(wallpaper.caption());
        page.addView(favHeader);

        scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(dp(Ui.SAFE_X - 10), dp(14), dp(Ui.SAFE_X), 0);  // columns are 10dp wider than their cards
        row = new LinearLayout(this);
        row.setClipChildren(false);
        scroller.addView(row);
        page.addView(scroller);

        setContentView(root);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        SceneView.previewHour = intent.getFloatExtra("hour", -1);
        scene.invalidate();
        // Home pressed while already home: close the forecast and go back to the first app, like Google TV.
        if (intent.getBooleanExtra("homePressed", false)) {
            closeForecast();
            if (moving != null) stopMoving();
            scroller.smoothScrollTo(0, 0);
            if (row.getChildCount() > 0) card(row.getChildAt(0)).requestFocus();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        applyOptions();
        if (moving == null) buildRow();
        handler.post(clockTick);
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(clockTick);
        handler.removeCallbacks(tick);
        if (moving != null) stopMoving();
    }

    @Override
    public void onBackPressed() {
        closeForecast();  // otherwise nothing: it's the home screen
    }

    /** Re-read what Settings may have changed. */
    private void applyOptions() {
        updateClock();
        weatherChip.setVisibility(Options.bool(this, Options.SHOW_WEATHER) ? View.VISIBLE : View.GONE);
        caption.setVisibility(Options.bool(this, Options.SHOW_CAPTION) && Options.bool(this, Options.PHOTO)
                ? View.VISIBLE : View.GONE);
        // Show what's cached straight away (fresh or not); the tick downloads when it's stale.
        if (lastWeather == null) lastWeather = Weather.cached(this);
        if (lastWeather != null) showWeather(lastWeather);  // units or digits may have changed
        if (Options.bool(this, Options.PHOTO)) {
            if (photo.getDrawable() == null) showWallpaper(false);
        } else {
            photo.setImageDrawable(null);
            scene.setVisibility(View.VISIBLE);
        }
    }

    // ---- Clock and dates ----

    private void updateClock() {
        Date now = new Date();
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        clock.setText(Options.digits(this, new SimpleDateFormat(pattern, Locale.ENGLISH).format(now)));
        greeting.setText(greetingText());
        String cal = Options.str(this, Options.CALENDAR);
        String bs = "ad".equals(cal) ? null : BsDate.format(this, Calendar.getInstance());
        bsDate.setText(bs);
        bsDate.setVisibility(bs == null ? View.GONE : View.VISIBLE);
        boolean showAd = !"bs".equals(cal) || bs == null;
        adDate.setText(Options.digits(this, new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(now)));
        adDate.setVisibility(showAd ? View.VISIBLE : View.GONE);
        // Without the Nepali line, the English date takes its size.
        adDate.setTextSize(bs == null ? 22 : 15);
        adDate.setTextColor(bs == null ? Ui.INK : Ui.INK_2);
    }

    private String greetingText() {
        return greeting(this);
    }

    /** "Good evening, Ray", or just "Good evening" with no name set. */
    static String greeting(android.content.Context c) {
        int h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String part = h >= 5 && h < 12 ? "Good morning" : h >= 12 && h < 17 ? "Good afternoon" : "Good evening";
        String name = Options.str(c, Options.NAME);
        return name.isEmpty() ? part : part + ", " + name;
    }

    // ---- Top bar ----

    /** Tabs on the left; weather chip, Next photo and TV settings on the right. */
    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(Ui.SAFE_X), 0, dp(Ui.SAFE_X), 0);
        bar.addView(Ui.tabs(this, Ui.TAB_HOME));
        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        bar.addView(weatherChip(), new LinearLayout.LayoutParams(-2, dp(44)));
        addTool(bar, "\u21BB", "Next photo", v -> nextPhoto());
        addTool(bar, "\u2699", "TV settings", v -> openTvSettings(this));
        return bar;
    }

    static void openTvSettings(Activity a) {
        try {
            a.startActivity(new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception ignored) {
        }
    }

    private void addTool(LinearLayout bar, String glyph, String label, View.OnClickListener click) {
        Button b = Ui.iconButton(this, glyph);
        b.setContentDescription(label);
        b.setOnClickListener(click);
        // Expands to show its name while focused, so the bar stays compact.
        b.setOnFocusChangeListener((v, focused) -> {
            b.setText(focused ? glyph + "  " + label : glyph);
            b.setPadding(focused ? dp(16) : 0, 0, focused ? dp(18) : 0, 0);
        });
        b.setMinWidth(dp(44));
        b.setMinimumWidth(dp(44));
        b.setTextSize(16);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.leftMargin = dp(10);
        bar.addView(b, lp);
    }

    // ---- Weather chip and forecast panel (from Summit) ----

    /** Icon, temperature, condition and today's high and low; OK opens the forecast. */
    private View weatherChip() {
        weatherChip = new LinearLayout(this);
        weatherChip.setGravity(Gravity.CENTER_VERTICAL);
        weatherChip.setFocusable(true);
        weatherChip.setClickable(true);
        weatherChip.setPadding(dp(12), 0, dp(18), 0);
        weatherChip.setBackground(Ui.focusable(Ui.pill(this, Ui.INK, 22), Ui.pill(this, Ui.CHIP, 22)));
        weatherChip.setContentDescription("Weather. Open forecast");
        weatherChip.setOnClickListener(v -> openForecast());
        weatherIcon = new WeatherIcon(this);
        weatherChip.addView(weatherIcon, new LinearLayout.LayoutParams(dp(22), dp(22)));
        weatherTemp = Ui.text(this, "", 15, Typeface.create("sans-serif", Typeface.BOLD), Ui.INK);
        weatherTemp.setPadding(dp(8), 0, 0, 0);
        weatherChip.addView(weatherTemp);
        weatherCond = Ui.text(this, Options.str(this, Options.CITY), 15, Ui.REGULAR, Ui.INK);
        weatherCond.setPadding(dp(8), 0, 0, 0);
        weatherChip.addView(weatherCond);
        weatherHiLo = Ui.text(this, "", 15, Ui.REGULAR, Ui.INK_2);
        weatherHiLo.setPadding(dp(8), 0, 0, 0);
        weatherChip.addView(weatherHiLo);
        // Dark text while the chip is focused (white).
        weatherTemp.setTextColor(Ui.focusText(Ui.INK));
        weatherCond.setTextColor(Ui.focusText(Ui.INK));
        weatherHiLo.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0x99000000, Ui.INK_2}));
        for (View v : new View[]{weatherTemp, weatherCond, weatherHiLo}) v.setDuplicateParentStateEnabled(true);
        return weatherChip;
    }

    private void loadWeather() {
        if (fetchingWeather) return;
        fetchingWeather = true;
        new Thread(() -> {
            Weather w = Weather.load(this);
            runOnUiThread(() -> {
                fetchingWeather = false;
                if (w != null && !isFinishing()) showWeather(w);
            });
        }).start();
    }

    private void showWeather(Weather w) {
        lastWeather = w;
        weatherIcon.set(w.code, w.isDay);
        weatherTemp.setText(Weather.temp(this, w.temp));
        weatherCond.setText(Weather.label(w.code, w.isDay));
        weatherHiLo.setText("H " + Weather.temp(this, w.hi) + " L " + Weather.temp(this, w.lo));
    }

    private void openForecast() {
        Weather w = lastWeather;
        if (w == null || forecast != null) {
            if (w == null) android.widget.Toast.makeText(this, "No weather yet. Check the internet connection.",
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        FrameLayout layer = new FrameLayout(this);
        layer.setBackgroundColor(0x9E000000);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(0xF71C1E24);
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setPadding(dp(28), dp(Ui.SAFE_Y + 4), dp(28), dp(Ui.SAFE_Y));
        scroll.addView(p);
        layer.addView(scroll, new FrameLayout.LayoutParams(dp(380), -1, Gravity.END));

        p.addView(Ui.text(this, Options.str(this, Options.CITY) + " \u00b7 Today", 15, Ui.MEDIUM, Ui.INK_2));
        LinearLayout now = new LinearLayout(this);
        now.setGravity(Gravity.CENTER_VERTICAL);
        now.setPadding(0, dp(12), 0, 0);
        WeatherIcon big = new WeatherIcon(this);
        big.set(w.code, w.isDay);
        now.addView(big, new LinearLayout.LayoutParams(dp(52), dp(52)));
        TextView t = Ui.text(this, Weather.temp(this, w.temp), 56, Ui.LIGHT, Ui.INK);
        t.setPadding(dp(14), 0, dp(14), 0);
        now.addView(t);
        LinearLayout cond = new LinearLayout(this);
        cond.setOrientation(LinearLayout.VERTICAL);
        cond.addView(Ui.text(this, Weather.label(w.code, w.isDay), 20, Ui.REGULAR, Ui.INK));
        TextView hl = Ui.text(this, "High " + Weather.temp(this, w.hi) + " \u00b7 Low " + Weather.temp(this, w.lo), 14, Ui.REGULAR, Ui.INK_2);
        hl.setPadding(0, dp(5), 0, 0);
        cond.addView(hl);
        now.addView(cond);
        p.addView(now);
        TextView windRain = Ui.text(this, "Wind " + Weather.wind(this, w.wind) + " \u00b7 Rain today " + Weather.rain(this, w.rainToday),
                14, Ui.REGULAR, Ui.INK_2);
        windRain.setPadding(0, dp(10), 0, 0);
        p.addView(windRain);
        TextView summary = Ui.text(this, w.summary(this), 15, Ui.MEDIUM, Ui.INK);
        summary.setPadding(0, dp(8), 0, 0);
        p.addView(summary);

        LinearLayout parts = new LinearLayout(this);
        parts.setPadding(0, dp(18), 0, 0);
        String[] names = {"Morning", "Afternoon", "Evening", "Night"};
        for (int i = 0; i < 4; i++) {
            Weather.Part part = w.parts[i];
            boolean day = i < 3;
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER_HORIZONTAL);
            card.setPadding(dp(4), dp(10), dp(4), dp(10));
            card.setBackground(Ui.pill(this, 0x14FFFFFF, 12));
            card.addView(Ui.text(this, names[i], 13, Ui.REGULAR, Ui.INK_2));
            WeatherIcon ic = new WeatherIcon(this);
            ic.set(part.code, day);
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(26), dp(26));
            ilp.topMargin = dp(6);
            card.addView(ic, ilp);
            TextView pt = Ui.text(this, Weather.temp(this, part.temp), 18, Ui.MEDIUM, Ui.INK);
            pt.setPadding(0, dp(6), 0, 0);
            card.addView(pt);
            String note = part.rainProb >= 50 ? "Rain " + Weather.pct(this, part.rainProb) : Weather.label(part.code, day);
            TextView pn = Ui.text(this, note, 12, Ui.REGULAR, Ui.INK_2);
            pn.setPadding(0, dp(6), 0, 0);
            pn.setGravity(Gravity.CENTER);
            card.addView(pn);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i < 3) lp.rightMargin = dp(8);
            parts.addView(card, lp);
        }
        p.addView(parts);

        TextView nextLabel = Ui.text(this, "Next days", 13, Ui.MEDIUM, Ui.INK_2);
        nextLabel.setPadding(0, dp(20), 0, dp(4));
        p.addView(nextLabel);
        for (Weather.Day d : w.next) {
            LinearLayout r = new LinearLayout(this);
            r.setGravity(Gravity.CENTER_VERTICAL);
            r.setPadding(0, dp(6), 0, dp(6));
            WeatherIcon ic = new WeatherIcon(this);
            ic.set(d.code, true);
            r.addView(ic, new LinearLayout.LayoutParams(dp(20), dp(20)));
            TextView dn = Ui.text(this, d.name, 15, Ui.MEDIUM, Ui.INK);
            dn.setPadding(dp(10), 0, 0, 0);
            r.addView(dn, new LinearLayout.LayoutParams(dp(95), -2));
            r.addView(Ui.text(this, Weather.label(d.code, true) + " \u00b7 " + Weather.pct(this, d.rainProb),
                    15, Ui.REGULAR, Ui.INK_2), new LinearLayout.LayoutParams(0, -2, 1));
            r.addView(Ui.text(this, Weather.temp(this, d.hi) + " / " + Weather.temp(this, d.lo), 15, Ui.MEDIUM, Ui.INK));
            p.addView(r);
        }

        LinearLayout buttons = new LinearLayout(this);
        buttons.setPadding(0, dp(24), 0, 0);
        Button close = Ui.button(this, "Close", 0x1FFFFFFF);
        close.setOnClickListener(v -> closeForecast());
        buttons.addView(close, new LinearLayout.LayoutParams(-2, dp(44)));
        Button city = Ui.button(this, "Change city", 0x1FFFFFFF);
        city.setOnClickListener(v -> {
            closeForecast();
            startActivity(new Intent(this, SettingsActivity.class).putExtra(SettingsActivity.EXTRA_PAGE, "weather"));
        });
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(-2, dp(44));
        clp.leftMargin = dp(10);
        buttons.addView(city, clp);
        p.addView(buttons);

        // Keep focus inside the panel while it's open.
        page.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        forecast = layer;
        root.addView(layer, new FrameLayout.LayoutParams(-1, -1));
        scroll.setTranslationX(dp(380));
        scroll.animate().translationX(0).setDuration(150).start();
        close.requestFocus();
    }

    private void closeForecast() {
        if (forecast == null) return;
        root.removeView(forecast);
        forecast = null;
        page.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        weatherChip.requestFocus();
    }

    private void nextPhoto() {
        if (downloading) return;
        android.widget.Toast.makeText(this, "Loading another photo\u2026", android.widget.Toast.LENGTH_SHORT).show();
        downloading = true;
        new Thread(() -> {
            boolean ok = wallpaper.downloadNext();
            runOnUiThread(() -> {
                downloading = false;
                if (ok) showWallpaper(true);
                else android.widget.Toast.makeText(this, "No internet. Try again later.", android.widget.Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    // ---- Wallpaper ----

    private void showWallpaper(boolean fade) {
        if (!prefs.getBoolean("photo", true)) return;
        Bitmap bmp = wallpaper.load(getResources().getDisplayMetrics().widthPixels);
        if (bmp == null) return;
        photo.setImageBitmap(bmp);
        scene.setVisibility(View.GONE);
        if (fade) {
            photo.setAlpha(0f);
            photo.animate().alpha(1f).setDuration(1200).start();
        }
        if (caption != null) caption.setText(wallpaper.caption());
    }

    private void fetchWallpaper() {
        downloading = true;
        lastWallpaperTry = System.currentTimeMillis();
        new Thread(() -> {
            boolean ok = wallpaper.download();
            runOnUiThread(() -> {
                downloading = false;
                if (ok) showWallpaper(true);
            });
        }).start();
    }

    // ---- App row ----

    /** The Favourites row's apps in order: saved order, then the defaults, then A to Z. Hidden apps left out. */
    static List<ResolveInfo> rowApps(android.content.Context c) {
        PackageManager pm = c.getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(query, 0));
        java.util.Set<String> hidden = AllAppsActivity.hidden(Options.prefs(c));
        // Other home screens (e.g. the NetTV launcher, kept enabled for Live TV) stay off the row.
        for (ResolveInfo h : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            hidden.add(h.activityInfo.packageName);
        }
        apps.removeIf(ri -> ri.activityInfo.packageName.equals(c.getPackageName())
                || ri.activityInfo.packageName.equals("com.android.tv.settings")
                || ri.activityInfo.packageName.equals("com.spocky.projengmenu")
                || hidden.contains(ri.activityInfo.packageName));
        String order = Options.prefs(c).getString(Options.ORDER, "");
        List<String> saved = order.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(order.split(",")));
        apps.sort((a, b) -> {
            int r = Integer.compare(rank(a.activityInfo.packageName, saved), rank(b.activityInfo.packageName, saved));
            return r != 0 ? r : a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });
        return apps;
    }

    private void buildRow() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = rowApps(this);

        View focused = getCurrentFocus();
        String keepId = focused != null && focused.getTag() instanceof String ? (String) focused.getTag() : null;
        // Focus elsewhere (e.g. a shortcut button) stays where it is after a rebuild.
        boolean focusInRow = focused == null || keepId != null;
        row.removeAllViews();
        for (ResolveInfo ri : apps) {
            Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                    .setClassName(ri.activityInfo.packageName, ri.activityInfo.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
            row.addView(tile(ri.activityInfo.packageName, ri.activityInfo.loadBanner(pm),
                    ri.loadIcon(pm), ri.loadLabel(pm), launch));
        }
        row.addView(allAppsTile());
        View target = row.getChildCount() > 0 ? card(row.getChildAt(0)) : null;
        for (int i = 0; keepId != null && i < row.getChildCount(); i++) {
            if (keepId.equals(card(row.getChildAt(i)).getTag())) target = card(row.getChildAt(i));
        }
        if (target != null && focusInRow) target.requestFocus();
    }

    /** Saved position first, then the default pinned apps, then everything else. */
    private static int rank(String pkg, List<String> saved) {
        int s = saved.indexOf(pkg);
        if (s >= 0) return s;
        int p = PINNED.indexOf(pkg);
        return p >= 0 ? 1000 + p : 2000;
    }


    private void saveOrder() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) {
            Object id = card(row.getChildAt(i)).getTag();
            if (!ALL_APPS.equals(id)) ids.add((String) id);
        }
        prefs.edit().putString(Options.ORDER, TextUtils.join(",", ids)).apply();
    }

    private static View card(View tileColumn) {
        return ((ViewGroup) tileColumn).getChildAt(0);
    }

    /**
     * tvOS-like tile: rounded 16:9 card (the app's TV banner, or icon and name) that lifts
     * when focused, with the app name fading in underneath.
     */
    private View tile(String id, Drawable banner, Drawable icon, CharSequence name, Intent launch) {
        FrameLayout card = Ui.card(this, banner, icon, name);
        View column = column(card, name);
        card.setTag(id);
        card.setFocusable(true);
        card.setLongClickable(true);
        TextView label = (TextView) ((ViewGroup) column).getChildAt(1);
        card.setOnFocusChangeListener((v, focused) -> {
            Ui.lift(v, focused, 1.1f);
            label.animate().alpha(Ui.showName(this, focused) ? 1f : 0f).setDuration(150).start();
            v.setForeground(focused ? Ui.ring(this, moving == v ? Ui.MOVE : Color.WHITE) : null);
        });
        card.setOnClickListener(v -> {
            try {
                startActivity(launch);
            } catch (Exception ignored) {
            }
        });
        card.setOnLongClickListener(v -> {
            startMoving(v);
            return true;
        });
        card.setOnKeyListener((v, keyCode, event) -> {
            if (moving == v) return onMoveKey(v, keyCode, event);
            // Holding OK on the remote sends repeated key-downs: the first repeat starts a move.
            boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
            if (moving == null && ok && event.getAction() == KeyEvent.ACTION_DOWN
                    && (event.getRepeatCount() > 0 || event.isLongPress())) {
                v.setPressed(false);
                startMoving(v);
                return true;
            }
            return false;
        });
        return column;
    }

    /** Fixed last tile, so All apps is reachable straight from the row. */
    private View allAppsTile() {
        FrameLayout card = new FrameLayout(this);
        card.setTag(ALL_APPS);
        card.setFocusable(true);
        card.setBackground(Ui.pill(this, Ui.GLASS, Ui.TILE_RADIUS));
        TextView glyph = Ui.text(this, "\u229E", 30, Ui.LIGHT, Ui.INK);
        card.addView(glyph, new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER));
        View column = column(card, "All apps");
        ((ViewGroup) column).getChildAt(1).setAlpha(0f);  // the tile already says "All apps"
        card.setOnFocusChangeListener((v, focused) -> {
            Ui.lift(v, focused, 1.1f);
            v.setForeground(focused ? Ui.ring(this, Color.WHITE) : null);
        });
        card.setOnClickListener(v -> startActivity(new Intent(this, AllAppsActivity.class)));
        return column;
    }

    /** Card plus the name that appears under it when focused. */
    private View column(View card, CharSequence name) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(card, new LinearLayout.LayoutParams(dp(TILE_W), dp(TILE_H)));
        TextView label = Ui.onPhoto(this, Ui.text(this, name, 15, Ui.MEDIUM, Ui.INK));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(Ui.showName(this, false) ? 1f : 0f);
        label.setPadding(0, dp(16), 0, 0);
        column.addView(label, new LinearLayout.LayoutParams(dp(TILE_W + 20), -2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(TILE_GAP - 20);
        column.setLayoutParams(lp);
        return column;
    }

    // ---- Reordering (hold OK, then ◀ ▶, then OK) ----

    private void startMoving(View card) {
        if (moving != null) return;
        moving = card;
        swallowOkUp = true;
        card.setForeground(Ui.ring(this, Ui.MOVE));
        card.animate().scaleX(1.16f).scaleY(1.16f).setDuration(150).start();
        favLabel.setText("Moving " + ((TextView) ((ViewGroup) card.getParent()).getChildAt(1)).getText());
        favHint.setText("   \u25C0 \u25B6 to move, OK to finish");
        favLabel.setTextColor(Ui.INK);
    }

    private void stopMoving() {
        View card = moving;
        moving = null;
        favLabel.setText("Favourites");
        favHint.setText("   hold OK to move");
        favLabel.setTextColor(Ui.INK_2);
        if (card != null) {
            card.setForeground(card.hasFocus() ? Ui.ring(this, Color.WHITE) : null);
            Ui.lift(card, card.hasFocus(), 1.1f);
        }
        saveOrder();
    }

    private boolean onMoveKey(View card, int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    View column = (View) card.getParent();
                    int from = row.indexOfChild(column);
                    int to = from + (keyCode == KeyEvent.KEYCODE_DPAD_LEFT ? -1 : 1);
                    if (to >= 0 && to < row.getChildCount() - 1) {  // All apps stays last
                        row.removeViewAt(from);
                        row.addView(column, to);
                        card.requestFocus();
                        column.post(() -> column.requestRectangleOnScreen(
                                new android.graphics.Rect(0, 0, column.getWidth(), column.getHeight())));
                    }
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_BACK:
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    if (swallowOkUp && keyCode != KeyEvent.KEYCODE_BACK) swallowOkUp = false;
                    else stopMoving();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return true;
            default:
                return false;
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
