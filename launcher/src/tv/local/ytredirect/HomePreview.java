package tv.local.ytredirect;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * A still, scaled-down copy of the home screen for Settings (Summit's live preview), drawn at
 * full 960x540dp size and scaled to fit. Call {@link #refresh()} after a setting changes.
 * It can't take focus, and it decodes the photo small to stay light on the NetTV box.
 */
final class HomePreview extends FrameLayout {
    private final int W, H, PREVIEW_TILES;  // screen in dp at the chosen layout size
    private final FrameLayout screen;

    HomePreview(Context c) {
        super(c);
        W = Ui.screenWidthDp(c);
        H = Ui.screenHeightDp(c);
        PREVIEW_TILES = (int) Math.ceil(5 / Ui.sizeScale(c));
        setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        setFocusable(false);
        setClipToOutline(true);
        setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), dp(8));
            }
        });
        screen = new FrameLayout(c);
        screen.setPivotX(0);
        screen.setPivotY(0);
        addView(screen, new LayoutParams(dp(W), dp(H)));
        refresh();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float scale = w / (float) dp(W);
        screen.setScaleX(scale);
        screen.setScaleY(scale);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        setMeasuredDimension(w, w * H / W);
        measureChildren(MeasureSpec.makeMeasureSpec(dp(W), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(dp(H), MeasureSpec.EXACTLY));
    }

    void refresh() {
        Context c = getContext();
        screen.removeAllViews();
        screen.setBackgroundColor(0xFF000000);

        Bitmap bmp = Options.bool(c, Options.PHOTO) ? new DailyWallpaper(c).load(480) : null;
        if (bmp != null) {
            ImageView photo = new ImageView(c);
            photo.setScaleType(ImageView.ScaleType.CENTER_CROP);
            photo.setImageBitmap(bmp);
            screen.addView(photo, new LayoutParams(-1, -1));
        } else {
            screen.addView(new SceneView(c), new LayoutParams(-1, -1));
        }
        screen.addView(Ui.gradient(c, GradientDrawable.Orientation.LEFT_RIGHT, 0x8C000000, 0x33000000, 0x00000000),
                new LayoutParams(dp(620), -1));
        screen.addView(Ui.gradient(c, GradientDrawable.Orientation.BOTTOM_TOP, 0xD9000000, 0x80000000, 0x00000000),
                new LayoutParams(-1, dp(380), Gravity.BOTTOM));

        LinearLayout page = new LinearLayout(c);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(0, dp(Ui.SAFE_Y), 0, dp(Ui.SAFE_Y));
        screen.addView(page, new LayoutParams(-1, -1));

        // Top bar: tabs and the weather chip, as pictures (nothing here is focusable).
        LinearLayout bar = new LinearLayout(c);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(Ui.SAFE_X), 0, dp(Ui.SAFE_X), 0);
        TextView tabs = Ui.chip(c, "Home      Apps      Settings");
        bar.addView(tabs, new LinearLayout.LayoutParams(-2, dp(48)));
        bar.addView(new View(c), new LinearLayout.LayoutParams(0, 1, 1));
        Weather w = Weather.cached(c);
        if (Options.bool(c, Options.SHOW_WEATHER)) {
            String chip = w == null ? Options.str(c, Options.CITY)
                    : Weather.temp(c, w.temp) + "  " + Weather.label(w.code, w.isDay) + "  H " + Weather.temp(c, w.hi) + " L " + Weather.temp(c, w.lo);
            bar.addView(Ui.chip(c, chip), new LinearLayout.LayoutParams(-2, dp(44)));
        }
        page.addView(bar);

        // Clock and dates.
        Date now = new Date();
        LinearLayout clockRow = new LinearLayout(c);
        clockRow.setGravity(Gravity.CENTER_VERTICAL);
        clockRow.setPadding(dp(Ui.SAFE_X - 4), dp(4), 0, 0);
        String pattern = DateFormat.is24HourFormat(c) ? "HH:mm" : "h:mm";
        View clock = Ui.onPhoto(c, Ui.text(c, Options.digits(c, new SimpleDateFormat(pattern, Locale.ENGLISH).format(now)),
                112, Typeface.create("sans-serif-thin", Typeface.NORMAL), Ui.INK));
        LinearLayout side = new LinearLayout(c);
        side.setOrientation(LinearLayout.VERTICAL);
        TextView greeting = Ui.onPhoto(c, Ui.text(c, HomeActivity.greeting(c), 24, Ui.MEDIUM, Ui.INK));
        String cal = Options.str(c, Options.CALENDAR);
        String bs = "ad".equals(cal) ? null : BsDate.format(c, Calendar.getInstance());
        TextView b = Ui.onPhoto(c, Ui.text(c, bs, 24, Ui.MEDIUM, Ui.INK));
        b.setVisibility(bs == null ? View.GONE : View.VISIBLE);
        TextView a = Ui.onPhoto(c, Ui.text(c, Options.digits(c, new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.ENGLISH).format(now)),
                15, Ui.REGULAR, Ui.INK_2));
        a.setVisibility(!"bs".equals(cal) || bs == null ? View.VISIBLE : View.GONE);
        Ui.layoutDates(c, side, greeting, b, a);
        Ui.arrangeClock(c, clockRow, clock, side);
        page.addView(clockRow);
        page.addView(new View(c), new LinearLayout.LayoutParams(-1, 0, 1));

        // Favourites header and the first few tiles; the first shows as focused.
        LinearLayout header = new LinearLayout(c);
        header.setPadding(dp(Ui.SAFE_X), 0, dp(Ui.SAFE_X), 0);
        header.addView(Ui.onPhoto(c, Ui.text(c, "Favourites", 15, Ui.MEDIUM, Ui.INK_2)));
        TextView caption = Ui.onPhoto(c, Ui.text(c, Options.bool(c, Options.PHOTO) && Options.bool(c, Options.SHOW_CAPTION)
                ? new DailyWallpaper(c).caption() : "", 13, Ui.REGULAR, Ui.INK_3));
        caption.setGravity(Gravity.END);
        caption.setSingleLine(true);
        caption.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        page.addView(header);

        LinearLayout row = new LinearLayout(c);
        row.setPadding(dp(Ui.SAFE_X), dp(14), 0, 0);
        PackageManager pm = c.getPackageManager();
        List<ResolveInfo> apps = HomeActivity.rowApps(c);
        for (int i = 0; i < Math.min(PREVIEW_TILES, apps.size()); i++) {
            ResolveInfo ri = apps.get(i);
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            FrameLayout card = Ui.card(c, ri.activityInfo.loadBanner(pm), ri.loadIcon(pm), ri.loadLabel(pm));
            if (i == 0) card.setForeground(Ui.ring(c, Ui.ACCENT));
            col.addView(card, new LinearLayout.LayoutParams(dp(176), dp(99)));
            TextView label = Ui.onPhoto(c, Ui.text(c, ri.loadLabel(pm), 15, Ui.MEDIUM, Ui.INK));
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            label.setPadding(0, dp(12), 0, 0);
            label.setAlpha(Ui.showName(c, i == 0) ? 1f : 0f);
            col.addView(label, new LinearLayout.LayoutParams(dp(176), -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.rightMargin = dp(22);
            row.addView(col, lp);
        }
        page.addView(row);
    }

    private int dp(int v) {
        return Ui.dp(getContext(), v);
    }
}
