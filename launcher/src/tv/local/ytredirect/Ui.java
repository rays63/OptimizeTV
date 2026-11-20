package tv.local.ytredirect;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Shared design tokens and building blocks, so every screen looks like one product.
 * Everything here is cheap to draw: no blur, and shadows only on the focused tile.
 */
final class Ui {
    private Ui() {}

    // ---- Colour ----
    static final int INK = 0xFFFFFFFF;          // primary text
    static final int INK_2 = 0xD9FFFFFF;        // secondary text
    static final int INK_3 = 0x8CFFFFFF;        // hints, captions
    static final int INK_DARK = 0xFF15161A;     // text on a focused (white) control
    static final int GLASS = 0x29FFFFFF;        // idle control on a photo
    static final int GLASS_2 = 0x1AFFFFFF;      // idle row on a dark backdrop
    static final int SMOKE = 0x59000000;        // idle control that must read on bright photos too
    static final int CHIP = 0xCC1C1E24;         // solid dark chip: readable on any photo (from Summit)
    static final int SURFACE = 0xFF15161A;      // backdrop when there's no photo
    static final int MOVE = 0xFF4DA3FF;         // tile being moved
    static final int WARN = 0xFFFFC266;         // "Hidden" badge

    // ---- Layout: 5% TV-safe area of a 960x540dp screen ----
    // Not final: sized() divides them by the layout size so the margins stay 5% of the screen.
    static int SAFE_X = 48, SAFE_Y = 28;

    /** Layout size setting as a factor on every dp and sp: large is the original design. */
    static float sizeScale(Context c) {
        switch (Options.str(c, Options.LAYOUT_SIZE)) {
            case "small": return 0.72f;
            case "medium": return 0.85f;
            default: return 1f;
        }
    }

    /** Wrap an activity's base context so the whole screen draws at the chosen layout size. */
    static Context sized(Context base) {
        float s = sizeScale(base);
        SAFE_X = Math.round(48 / s);
        SAFE_Y = Math.round(28 / s);
        if (s == 1f) return base;
        android.content.res.Configuration cfg =
                new android.content.res.Configuration(base.getResources().getConfiguration());
        cfg.densityDpi = Math.round(cfg.densityDpi * s);
        cfg.screenWidthDp = Math.round(cfg.screenWidthDp / s);
        cfg.screenHeightDp = Math.round(cfg.screenHeightDp / s);
        cfg.smallestScreenWidthDp = Math.round(cfg.smallestScreenWidthDp / s);
        return base.createConfigurationContext(cfg);
    }
    static final int TILE_RADIUS = 12;

    // ---- Type (Roboto, three weights) ----
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    static int dp(Context c, int v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static int screenWidthDp(Context c) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        return Math.round(m.widthPixels / m.density);
    }

    /**
     * Fill the column beside the clock: the greeting (bold, same size as the primary date), then the
     * primary date big and bold, then the other date small. A hidden (GONE) date is never primary.
     */
    static void layoutDates(Context c, LinearLayout side, TextView greeting, TextView bs, TextView ad) {
        side.removeAllViews();
        greeting.setTextSize(24);
        greeting.setTypeface(MEDIUM);
        greeting.setTextColor(INK);
        side.addView(greeting);
        boolean adFirst = bs.getVisibility() == View.GONE
                || (ad.getVisibility() != View.GONE && "ad".equals(Options.str(c, Options.PRIMARY_DATE)));
        TextView primary = adFirst ? ad : bs, other = adFirst ? bs : ad;
        primary.setTextSize(24);
        primary.setTypeface(MEDIUM);
        primary.setTextColor(INK);
        primary.setPadding(0, dp(c, 8), 0, 0);
        other.setTextSize(15);
        other.setTypeface(REGULAR);
        other.setTextColor(INK_2);
        other.setPadding(0, dp(c, 7), 0, 0);
        side.addView(primary);
        side.addView(other);
    }

    /** Put the greeting and dates (side) right of, above or below the clock, per Settings. */
    static void arrangeClock(Context c, LinearLayout row, View clock, LinearLayout side) {
        row.removeAllViews();
        String pos = Options.str(c, Options.DATE_POS);
        if ("above".equals(pos) || "below".equals(pos)) {
            row.setOrientation(LinearLayout.VERTICAL);
            row.setGravity(Gravity.START);
            boolean above = "above".equals(pos);
            side.setPadding(dp(c, 4), above ? dp(c, 16) : dp(c, 6), 0, above ? dp(c, 10) : 0);
            row.addView(above ? side : clock);
            row.addView(above ? clock : side);
        } else {
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            side.setPadding(dp(c, 28), dp(c, 4), 0, 0);
            row.addView(clock);
            row.addView(side);
        }
    }

    static int screenHeightDp(Context c) {
        android.util.DisplayMetrics m = c.getResources().getDisplayMetrics();
        return Math.round(m.heightPixels / m.density);
    }

    static TextView text(Context c, CharSequence s, float sp, Typeface tf, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTypeface(tf);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        return t;
    }

    /** Soft drop shadow for text that sits directly on a photo. */
    static <T extends TextView> T onPhoto(Context c, T t) {
        t.setShadowLayer(dp(c, 8), 0, dp(c, 1), 0x80000000);
        return t;
    }

    static GradientDrawable pill(Context c, int color, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(c, radiusDp));
        return d;
    }

    static GradientDrawable circle(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.OVAL);
        d.setColor(color);
        return d;
    }

    static GradientDrawable ring(Context c, int color) {
        GradientDrawable r = new GradientDrawable();
        r.setCornerRadius(dp(c, TILE_RADIUS));
        r.setStroke(dp(c, 3), color);
        return r;
    }

    static StateListDrawable focusable(Drawable focused, Drawable idle) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, focused);
        bg.addState(new int[]{}, idle);
        return bg;
    }

    /** White when focused, otherwise {@code idle}. Children can share it with duplicateParentState. */
    static ColorStateList focusText(int idle) {
        return new ColorStateList(new int[][]{{android.R.attr.state_focused}, {}}, new int[]{INK_DARK, idle});
    }

    /** Pill button: glass when idle, white with dark text when focused. */
    static Button button(Context c, String label, int idleColor) {
        Button b = new Button(c);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTypeface(MEDIUM);
        b.setFocusable(true);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(c, 20), 0, dp(c, 20), 0);
        b.setStateListAnimator(null);
        b.setBackground(focusable(pill(c, INK, 20), pill(c, idleColor, 20)));
        b.setTextColor(focusText(INK_2));
        return b;
    }

    /** Round glyph button for the home screen's top bar (a 22dp-radius pill, so it can widen). */
    static Button iconButton(Context c, String glyph) {
        Button b = new Button(c);
        b.setText(glyph);
        b.setTextSize(18);
        b.setGravity(Gravity.CENTER);
        b.setPadding(0, 0, 0, 0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setTypeface(MEDIUM);
        b.setSingleLine(true);
        b.setBackground(focusable(pill(c, INK, 22), pill(c, CHIP, 22)));
        b.setTextColor(focusText(INK));
        return b;
    }

    /**
     * 16:9 rounded tile: the app's TV banner, or its icon and name on a glass card when it
     * has no banner. Used by the home row, All apps and the switcher.
     */
    static FrameLayout card(Context c, Drawable banner, Drawable icon, CharSequence name) {
        FrameLayout card = new FrameLayout(c);
        card.setClipToOutline(true);
        card.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(c, TILE_RADIUS));
            }
        });
        if (banner != null) {
            ImageView img = new ImageView(c);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            img.setImageDrawable(banner);
            card.addView(img, new FrameLayout.LayoutParams(-1, -1));
        } else {
            card.setBackgroundColor(0xFF2B2E38);
            LinearLayout inner = new LinearLayout(c);
            inner.setOrientation(LinearLayout.VERTICAL);
            inner.setGravity(Gravity.CENTER);
            inner.setPadding(dp(c, 8), 0, dp(c, 8), 0);
            ImageView img = new ImageView(c);
            img.setImageDrawable(icon);
            inner.addView(img, new LinearLayout.LayoutParams(dp(c, 40), dp(c, 40)));
            TextView t = text(c, name, 13, MEDIUM, INK);
            t.setGravity(Gravity.CENTER);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            t.setPadding(0, dp(c, 6), 0, 0);
            inner.addView(t, new LinearLayout.LayoutParams(-1, -2));
            card.addView(inner, new FrameLayout.LayoutParams(-1, -1));
        }
        return card;
    }

    /** The tvOS focus lift: scale up and cast a shadow. */
    static void lift(View v, boolean up, float scale) {
        v.animate().scaleX(up ? scale : 1f).scaleY(up ? scale : 1f)
                .translationZ(up ? dp(v.getContext(), 12) : 0).setDuration(150).start();
    }

    /**
     * Full-screen root for secondary screens: today's photo, decoded tiny and stretched so it
     * reads as a soft blur (real blur needs Android 12; the NetTV box runs 10), then darkened.
     */
    static FrameLayout backdrop(Activity a, int dim) {
        FrameLayout root = new FrameLayout(a);
        root.setBackgroundColor(SURFACE);
        SharedPreferences prefs = a.getSharedPreferences("home", Context.MODE_PRIVATE);
        if (prefs.getBoolean("photo", true)) {
            Bitmap bmp = new DailyWallpaper(a).load(240);
            if (bmp != null) {
                ImageView photo = new ImageView(a);
                photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
                photo.setImageBitmap(bmp);
                root.addView(photo, new FrameLayout.LayoutParams(-1, -1));
            }
        }
        View shade = new View(a);
        shade.setBackgroundColor(dim);
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));
        return root;
    }


    // ---- Tabs shared by Home, Apps and Settings ----
    static final int TAB_HOME = 0, TAB_APPS = 1, TAB_SETTINGS = 2;
    private static final String[] TABS = {"Home", "Apps", "Settings"};

    /** Dark chip with Home, Apps and Settings; the current tab stays highlighted. */
    static LinearLayout tabs(Activity a, int current) {
        LinearLayout bar = new LinearLayout(a);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(a, 4), dp(a, 4), dp(a, 4), dp(a, 4));
        bar.setBackground(pill(a, CHIP, 24));
        for (int i = 0; i < TABS.length; i++) {
            int tab = i;
            Button b = new Button(a);
            b.setText(TABS[i]);
            b.setAllCaps(false);
            b.setTextSize(15);
            b.setTypeface(MEDIUM);
            b.setMinWidth(0);
            b.setMinimumWidth(0);
            b.setMinHeight(0);
            b.setMinimumHeight(0);
            b.setPadding(dp(a, 20), 0, dp(a, 20), 0);
            b.setStateListAnimator(null);
            b.setSelected(i == current);
            StateListDrawable bg = new StateListDrawable();
            bg.addState(new int[]{android.R.attr.state_focused}, pill(a, INK, 20));
            bg.addState(new int[]{android.R.attr.state_selected}, pill(a, 0x40FFFFFF, 20));
            bg.addState(new int[]{}, pill(a, 0x00000000, 20));
            b.setBackground(bg);
            b.setTextColor(new ColorStateList(new int[][]{{android.R.attr.state_focused},
                    {android.R.attr.state_selected}, {}}, new int[]{INK_DARK, INK, INK_3}));
            b.setOnClickListener(v -> go(a, current, tab));
            bar.addView(b, new LinearLayout.LayoutParams(-2, dp(a, 40)));
        }
        return bar;
    }

    /** Switch tabs without stacking screens: Home is the base, Apps and Customize sit on it. */
    static void go(Activity a, int from, int to) {
        if (from == to) return;
        if (to == TAB_HOME) {
            a.finish();
        } else {
            a.startActivity(new android.content.Intent(a, to == TAB_APPS ? AllAppsActivity.class : SettingsActivity.class));
            if (from != TAB_HOME) a.finish();
        }
        a.overridePendingTransition(0, 0);
    }

    /** Whether a tile's name shows, per Settings › Icons & tiles › App names. */
    static boolean showName(Context c, boolean focused) {
        String mode = Options.str(c, Options.APP_NAMES);
        return "always".equals(mode) || ("selected".equals(mode) && focused);
    }

    /** Dark rounded chip for short status text, like the weather. */
    static TextView chip(Context c, CharSequence s) {
        TextView t = text(c, s, 15, MEDIUM, INK);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(c, 18), 0, dp(c, 18), 0);
        t.setBackground(pill(c, CHIP, 22));
        t.setSingleLine(true);
        return t;
    }

    static View gradient(Context c, GradientDrawable.Orientation o, int... colors) {
        View v = new View(c);
        v.setBackground(new GradientDrawable(o, colors));
        return v;
    }
}
