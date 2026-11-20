package tv.local.ytredirect;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal recent-apps switcher (Google TV has none). Lists recently used apps with
 * Open / Close; Close force-stops via {@link RedirectService}.
 */
public class SwitcherActivity extends Activity {
    static boolean visible;
    private static final long WINDOW_MS = 6 * 60 * 60 * 1000L;
    private static final int MAX_APPS = 6;

    private static final int CARD_W = 192, CARD_H = 108;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        build(getIntent().getStringExtra("current"));
    }

    /**
     * Overlay over the app you were in: recent apps as cards along the bottom, each with
     * its own Close button. Focus starts on Close for the app you just left.
     */
    private void build(String current) {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0x99000000);
        root.addView(Ui.gradient(this, GradientDrawable.Orientation.BOTTOM_TOP, 0xF2000000, 0x99000000, 0x00000000),
                new FrameLayout.LayoutParams(-1, dp(420), Gravity.BOTTOM));

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(0, 0, 0, dp(Ui.SAFE_Y + 12));
        root.addView(panel, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.BOTTOM);
        header.setPadding(dp(Ui.SAFE_X), 0, dp(Ui.SAFE_X), 0);
        LinearLayout titles = new LinearLayout(this);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.addView(Ui.text(this, "Recent apps", 28, Ui.REGULAR, Ui.INK));
        TextView help = Ui.text(this, "Select a card to open the app. Close stops it completely.", 15, Ui.REGULAR, Ui.INK_3);
        help.setPadding(0, dp(8), 0, 0);
        titles.addView(help);
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        Button closeAll = Ui.button(this, "Close all", Ui.GLASS);
        header.addView(closeAll, new LinearLayout.LayoutParams(-2, dp(40)));
        panel.addView(header);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setClipToPadding(false);
        scroller.setClipChildren(false);
        scroller.setPadding(dp(Ui.SAFE_X), dp(28), dp(Ui.SAFE_X), 0);
        LinearLayout row = new LinearLayout(this);
        row.setClipChildren(false);
        scroller.addView(row);
        panel.addView(scroller);

        List<String> apps = recentApps(current);
        Button firstClose = null;
        for (String pkg : apps) {
            Button b = addCard(row, pkg);
            if (firstClose == null) firstClose = b;
        }
        if (apps.isEmpty()) {
            TextView none = Ui.text(this, "Nothing is running. Apps you open will show up here.", 18, Ui.REGULAR, Ui.INK_2);
            none.setPadding(0, dp(8), 0, dp(40));
            row.addView(none);
        }
        closeAll.setOnClickListener(v -> forceStop(apps));
        setContentView(root);
        // One press closes what you were just using.
        View initial = firstClose != null ? firstClose : closeAll;
        root.post(initial::requestFocus);
    }

    private Button addCard(LinearLayout row, String pkg) {
        PackageManager pm = getPackageManager();
        CharSequence name = pkg;
        Drawable icon = null, banner = null;
        try {
            name = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0));
            icon = pm.getApplicationIcon(pkg);
            Intent i = launchIntent(pkg);
            if (i != null && i.getComponent() != null) banner = pm.getActivityBanner(i.getComponent());
            if (banner == null) banner = pm.getApplicationBanner(pkg);
        } catch (PackageManager.NameNotFoundException ignored) {
        }

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setClipChildren(false);

        FrameLayout card = Ui.card(this, banner, icon, name);
        card.setFocusable(true);
        card.setContentDescription("Open " + name);
        column.addView(card, new LinearLayout.LayoutParams(dp(CARD_W), dp(CARD_H)));
        TextView label = Ui.text(this, name, 15, Ui.MEDIUM, Ui.INK_2);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(16), 0, dp(12));
        column.addView(label, new LinearLayout.LayoutParams(dp(CARD_W), -2));
        Button close = Ui.button(this, "Close", Ui.GLASS);
        close.setContentDescription("Close " + name);
        column.addView(close, new LinearLayout.LayoutParams(-2, dp(36)));

        card.setOnFocusChangeListener((v, focused) -> {
            Ui.lift(v, focused, 1.08f);
            v.setForeground(focused ? Ui.ring(this, Color.WHITE) : null);
            label.setTextColor(focused ? Ui.INK : Ui.INK_2);
        });
        card.setOnClickListener(v -> {
            Intent i = launchIntent(pkg);
            if (i != null) startActivity(i);
            finish();
        });
        close.setOnClickListener(v -> forceStop(java.util.Collections.singletonList(pkg)));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(24);
        row.addView(column, lp);
        return close;
    }

    private List<String> recentApps(String current) {
        List<String> out = new ArrayList<>();
        for (Recents.Entry e : Recents.query(this, WINDOW_MS, MAX_APPS, current)) out.add(e.pkg);
        return out;
    }

    private Intent launchIntent(String pkg) {
        return Recents.launchIntent(this, pkg);
    }

    /** Hand off to the accessibility service, which presses Force stop for each app, then goes Home. */
    private void forceStop(List<String> pkgs) {
        RedirectService svc = RedirectService.instance;
        if (svc != null) svc.forceStop(new ArrayList<>(pkgs));
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (intent.getBooleanExtra("dismiss", false)) finish();
        else build(intent.getStringExtra("current"));  // reopened: show the current list, not a stale one
    }

    @Override
    protected void onResume() {
        super.onResume();
        visible = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        visible = false;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
