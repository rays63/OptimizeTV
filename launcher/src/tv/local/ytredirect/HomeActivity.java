package tv.local.ytredirect;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * tvOS-style home screen: Bing photo of the day (cached for offline), greeting, clock,
 * date, weather and an app row. Hold OK on an app to move it; the order is remembered.
 */
public class HomeActivity extends Activity {
    static final String DEFAULT_NAME = "Ray";
    static final String DEFAULT_CITY = "Kathmandu";
    private static final float DEFAULT_LAT = 27.7172f, DEFAULT_LON = 85.3240f;
    private static final long WEATHER_REFRESH_MS = 30 * 60_000L;
    private static final long WALLPAPER_RETRY_MS = 15 * 60_000L;
    /** Default order before you rearrange anything; other apps follow alphabetically. */
    private static final List<String> PINNED = Arrays.asList(
            "com.netflix.ninja", "org.smarttube.stable", "np.com.geniussystems.android.stb.iptv",
            "com.stremio.one", "com.spotify.tv.android", "com.tcl.browser");

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences prefs;
    private DailyWallpaper wallpaper;
    private SceneView scene;
    private ImageView photo;
    private TextView greeting, weather, caption, hint;
    private HorizontalScrollView scroller;
    private LinearLayout row;
    private long lastWeather, lastWallpaperTry;
    private String weatherCity = "";
    private boolean downloading;
    /** Tile currently being moved, or null. */
    private View moving;
    /** The OK release that ends the hold which started a move must not also end it. */
    private boolean swallowOkUp;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            scene.invalidate();
            greeting.setText(greetingText());
            long now = System.currentTimeMillis();
            if (now - lastWeather > WEATHER_REFRESH_MS) fetchWeather();
            if (wallpaper.isStale() && !downloading && now - lastWallpaperTry > WALLPAPER_RETRY_MS) fetchWallpaper();
            handler.postDelayed(this, 60_000);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        wallpaper = new DailyWallpaper(this);
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);

        // Drawn scene is the fallback until the first photo has downloaded.
        scene = new SceneView(this);
        root.addView(scene);
        photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(photo);
        showWallpaper(false);

        // Soft shading so white text stays readable on any photo.
        View leftShade = new View(this);
        leftShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0x99000000, 0x33000000, 0x00000000}));
        root.addView(leftShade, new FrameLayout.LayoutParams(dp(560), -1));
        View bottomShade = new View(this);
        bottomShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xCC000000, 0x66000000, 0x00000000}));
        root.addView(bottomShade, new FrameLayout.LayoutParams(-1, dp(300), Gravity.BOTTOM));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setPadding(dp(56), dp(40), 0, 0);
        greeting = text("", 30, Typeface.create("sans-serif-light", Typeface.NORMAL), 0xEEFFFFFF);
        info.addView(greeting);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("HH:mm");
        style(clock, 120, Typeface.create("sans-serif-thin", Typeface.NORMAL), Color.WHITE);
        clock.setIncludeFontPadding(false);
        info.addView(clock);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        style(date, 22, Typeface.create("sans-serif", Typeface.NORMAL), 0xEEFFFFFF);
        date.setPadding(0, dp(8), 0, 0);
        info.addView(date);
        weather = text("", 16, Typeface.create("sans-serif", Typeface.NORMAL), 0xBBFFFFFF);
        weather.setPadding(0, dp(4), 0, 0);
        info.addView(weather);
        root.addView(info);

        // Photo location, top right, like the tvOS screensaver caption.
        caption = text("", 13, Typeface.create("sans-serif", Typeface.NORMAL), 0x99FFFFFF);
        caption.setMaxWidth(dp(360));
        caption.setGravity(Gravity.END);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
        clp.setMargins(0, dp(28), dp(48), 0);
        root.addView(caption, clp);
        caption.setText(wallpaper.caption());

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        hint = text("◀  ▶  to move    ·    OK to finish", 13,
                Typeface.create("sans-serif-medium", Typeface.NORMAL), 0xEEFFFFFF);
        hint.setPadding(dp(56), 0, 0, dp(4));
        hint.setVisibility(View.INVISIBLE);
        bottom.addView(hint);
        scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(dp(48), dp(12), dp(48), dp(4));
        row = new LinearLayout(this);
        row.setClipChildren(false);
        scroller.addView(row);
        bottom.addView(scroller);
        bottom.addView(shortcuts());
        root.addView(bottom, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        setContentView(root);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        SceneView.previewHour = intent.getFloatExtra("hour", -1);
        scene.invalidate();
        // Home pressed while already home: back to the first app, like Google TV.
        if (intent.getBooleanExtra("homePressed", false)) {
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
        handler.post(tick);
    }

    /** Re-read what Customize may have changed. */
    private void applyOptions() {
        greeting.setText(greetingText());
        weather.setVisibility(prefs.getBoolean("showWeather", true) ? View.VISIBLE : View.GONE);
        caption.setVisibility(prefs.getBoolean("showCaption", true) && prefs.getBoolean("photo", true)
                ? View.VISIBLE : View.GONE);
        String city = prefs.getString("city", DEFAULT_CITY);
        if (!city.equals(weatherCity)) {
            weatherCity = city;
            weather.setText(city);
            lastWeather = 0;
        }
        if (prefs.getBoolean("photo", true)) {
            if (photo.getDrawable() == null) showWallpaper(false);
        } else {
            photo.setImageDrawable(null);
            scene.setVisibility(View.VISIBLE);
        }
    }

    private String greetingText() {
        int h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY);
        String part = h >= 5 && h < 12 ? "Good morning" : h >= 12 && h < 17 ? "Good afternoon" : "Good evening";
        String name = prefs.getString("name", DEFAULT_NAME);
        return name.isEmpty() ? part : part + ", " + name;
    }

    /** Second row, like Projectivy: all apps, home options and system shortcuts. */
    private View shortcuts() {
        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(56), dp(2), dp(48), dp(20));
        addShortcut(bar, "⊞  All apps", v -> startActivity(new Intent(this, AllAppsActivity.class)));
        addShortcut(bar, "✎  Customize", v -> startActivity(new Intent(this, CustomizeActivity.class)));
        addShortcut(bar, "↻  Next photo", v -> nextPhoto());
        addShortcut(bar, "⇄  Inputs", v -> openInputs());
        addShortcut(bar, "⚙  Settings", v -> startActivity(
                new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
        return bar;
    }

    private void addShortcut(LinearLayout bar, String label, View.OnClickListener click) {
        android.widget.Button b = Ui.button(this, label, 0x40FFFFFF);
        b.setTextSize(14);
        b.setOnClickListener(click);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(36));
        lp.rightMargin = dp(12);
        bar.addView(b, lp);
    }

    private void nextPhoto() {
        if (downloading) return;
        android.widget.Toast.makeText(this, "Getting another photo…", android.widget.Toast.LENGTH_SHORT).show();
        downloading = true;
        new Thread(() -> {
            boolean ok = wallpaper.downloadNext();
            runOnUiThread(() -> {
                downloading = false;
                if (ok) showWallpaper(true);
                else android.widget.Toast.makeText(this, "No internet", android.widget.Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    /** TCL's input picker (what the remote's Input button opens), else Android TV's. */
    private void openInputs() {
        try {
            startActivity(new Intent("com.tcl.suspension.action.main").setPackage("com.tcl.suspension")
                    .putExtra("openInput", "true").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (Exception e) {
            try {
                startActivity(new Intent("com.android.tv.action.VIEW_INPUTS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(tick);
        if (moving != null) stopMoving();
    }

    @Override
    public void onBackPressed() {
        // Home screen: nothing to go back to.
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

    private void buildRow() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(query, 0));
        java.util.Set<String> hidden = AllAppsActivity.hidden(prefs);
        // Other home screens (e.g. the NetTV launcher, kept enabled for Live TV) stay off the row.
        for (ResolveInfo h : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)) {
            hidden.add(h.activityInfo.packageName);
        }
        apps.removeIf(ri -> ri.activityInfo.packageName.equals(getPackageName())
                || ri.activityInfo.packageName.equals("com.android.tv.settings")
                || ri.activityInfo.packageName.equals("com.spocky.projengmenu")
                || hidden.contains(ri.activityInfo.packageName));
        List<String> saved = savedOrder();
        apps.sort((a, b) -> {
            int c = Integer.compare(rank(a.activityInfo.packageName, saved), rank(b.activityInfo.packageName, saved));
            return c != 0 ? c : a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString());
        });

        View focused = getCurrentFocus();
        String keepId = focused != null && focused.getTag() != null ? (String) focused.getTag() : null;
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

    private List<String> savedOrder() {
        String s = prefs.getString("order", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    private void saveOrder() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < row.getChildCount(); i++) ids.add((String) card(row.getChildAt(i)).getTag());
        prefs.edit().putString("order", TextUtils.join(",", ids)).apply();
    }

    private static View card(View tileColumn) {
        return ((ViewGroup) tileColumn).getChildAt(0);
    }

    /**
     * tvOS-like tile: rounded 16:9 card (the app's TV banner, or icon and name) that lifts
     * when focused, with the app name fading in underneath.
     */
    private View tile(String id, Drawable banner, Drawable icon, CharSequence name, Intent launch) {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setGravity(Gravity.CENTER_HORIZONTAL);

        FrameLayout card = new FrameLayout(this);
        card.setTag(id);
        card.setFocusable(true);
        card.setLongClickable(true);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(14));
            }
        });
        card.setElevation(dp(2));
        if (banner != null) {
            ImageView img = new ImageView(this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(banner);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            card.setBackgroundColor(0xFF2A2D3A);
            LinearLayout inner = new LinearLayout(this);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            ImageView img = new ImageView(this);
            img.setImageDrawable(icon);
            inner.addView(img, new LinearLayout.LayoutParams(dp(36), dp(36)));
            TextView t = text(name.toString(), 13, Typeface.DEFAULT, Color.WHITE);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            inner.addView(t);
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        column.addView(card, new LinearLayout.LayoutParams(dp(150), dp(84)));

        TextView label = text(name.toString(), 14, Typeface.create("sans-serif-medium", Typeface.NORMAL), Color.WHITE);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setAlpha(0f);
        label.setPadding(0, dp(12), 0, 0);
        column.addView(label, new LinearLayout.LayoutParams(dp(150), -2));

        card.setOnFocusChangeListener((v, focused) -> {
            v.animate().scaleX(focused ? 1.12f : 1f).scaleY(focused ? 1.12f : 1f)
                    .translationZ(focused ? dp(14) : 0).setDuration(160).start();
            label.animate().alpha(focused ? 1f : 0f).setDuration(160).start();
            v.setForeground(focused ? ring(moving == v ? 0xFF4DA3FF : Color.WHITE) : null);
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

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(18);
        column.setLayoutParams(lp);
        return column;
    }

    // ---- Reordering (hold OK, then ◀ ▶, then OK) ----

    private void startMoving(View card) {
        if (moving != null) return;
        moving = card;
        swallowOkUp = true;
        card.setForeground(ring(0xFF4DA3FF));
        card.animate().scaleX(1.18f).scaleY(1.18f).setDuration(160).start();
        hint.setVisibility(View.VISIBLE);
    }

    private void stopMoving() {
        View card = moving;
        moving = null;
        hint.setVisibility(View.INVISIBLE);
        if (card != null) {
            card.setForeground(card.hasFocus() ? ring(Color.WHITE) : null);
            card.animate().scaleX(card.hasFocus() ? 1.12f : 1f).scaleY(card.hasFocus() ? 1.12f : 1f)
                    .setDuration(160).start();
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
                    if (to >= 0 && to < row.getChildCount()) {
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

    private GradientDrawable ring(int color) {
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(14));
        ring.setStroke(dp(3), color);
        return ring;
    }

    // ---- Weather ----

    private void fetchWeather() {
        lastWeather = System.currentTimeMillis();
        new Thread(() -> {
            try {
                float lat = prefs.getFloat("lat", DEFAULT_LAT), lon = prefs.getFloat("lon", DEFAULT_LON);
                String city = prefs.getString("city", DEFAULT_CITY);
                URL url = new URL("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                        + "&current=temperature_2m,weather_code&timezone=auto");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.setConnectTimeout(8000);
                con.setReadTimeout(8000);
                try (InputStream in = con.getInputStream(); Scanner s = new Scanner(in).useDelimiter("\\A")) {
                    JSONObject cur = new JSONObject(s.next()).getJSONObject("current");
                    String line = city + "   " + Math.round(cur.getDouble("temperature_2m")) + "°  "
                            + describe(cur.getInt("weather_code"));
                    runOnUiThread(() -> weather.setText(line));
                }
            } catch (Exception e) {
                lastWeather = System.currentTimeMillis() - WEATHER_REFRESH_MS + 5 * 60_000L;  // retry in 5 min
            }
        }).start();
    }

    /** WMO weather interpretation codes, as used by Open-Meteo. */
    private static String describe(int code) {
        if (code == 0) return "Clear";
        if (code <= 2) return "Partly cloudy";
        if (code == 3) return "Cloudy";
        if (code <= 48) return "Fog";
        if (code <= 57) return "Drizzle";
        if (code <= 67) return "Rain";
        if (code <= 77) return "Snow";
        if (code <= 82) return "Showers";
        if (code <= 86) return "Snow showers";
        return "Thunderstorm";
    }

    private TextView text(String s, int sp, Typeface tf, int color) {
        TextView t = new TextView(this);
        t.setText(s);
        style(t, sp, tf, color);
        return t;
    }

    private void style(TextView t, int sp, Typeface tf, int color) {
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setShadowLayer(dp(6), 0, dp(1), 0x66000000);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
