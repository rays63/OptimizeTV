package tv.local.ytredirect;

import android.content.Context;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Every installed app as banner tiles. OK opens; hold OK (or Menu) opens a side panel to
 * hide or show the app on the home row, see its info, or uninstall it.
 */
public class AllAppsActivity extends Activity {
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.sized(base));
    }

    static final String HIDDEN = "hidden";
    private static final int GAP = 24;
    private int COLS = 5;  // 5 at large, 6 at medium, 7 at small
    private float sizeAtCreate;
    private int accentAtCreate;

    private SharedPreferences prefs;
    private FrameLayout root;
    private LinearLayout content;
    private GridLayout grid;
    private View menu;
    /** Package to focus after a rebuild (e.g. back from App info). */
    private String focusPkg;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        sizeAtCreate = Ui.sizeScale(this);
        accentAtCreate = Ui.ACCENT;
        COLS = Math.round(5 / sizeAtCreate);
        prefs = Options.prefs(this);
        build();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        Ui.loadAccent(this);
        if (Ui.sizeScale(this) != sizeAtCreate || Ui.ACCENT != accentAtCreate) { recreate(); return; }
        build();  // an app may have been uninstalled
    }

    static Set<String> hidden(SharedPreferences prefs) {
        return new HashSet<>(prefs.getStringSet(HIDDEN, new HashSet<>()));
    }

    private List<ResolveInfo> apps() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : apps) seen.add(ri.activityInfo.packageName);
        // Phone-style apps without a TV entry still deserve a place here.
        for (ResolveInfo ri : pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if (seen.add(ri.activityInfo.packageName)) apps.add(ri);
        }
        apps.removeIf(ri -> ri.activityInfo.packageName.equals(getPackageName()));
        apps.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        return apps;
    }

    private void build() {
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = apps();
        Set<String> hidden = hidden(prefs);
        menu = null;

        root = Ui.backdrop(this, 0xD9101116);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setClipChildren(false);
        content.setClipToPadding(false);  // a focused first-column tile grows into the safe margin
        content.setPadding(dp(Ui.SAFE_X), dp(Ui.SAFE_Y), dp(Ui.SAFE_X), 0);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.tabs(this, Ui.TAB_APPS));
        TextView count = Ui.text(this, Options.digits(this, String.valueOf(apps.size())) + " apps", 15, Ui.REGULAR, Ui.INK_3);
        count.setPadding(dp(20), 0, 0, 0);
        top.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        String pattern = android.text.format.DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        top.addView(Ui.text(this, Options.digits(this, new java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH)
                .format(new java.util.Date())), 20, Ui.MEDIUM, Ui.INK_2));
        content.addView(top);

        int tileW = (Ui.screenWidthDp(this) - 2 * Ui.SAFE_X - GAP * (COLS - 1)) / COLS;
        int tileH = tileW * 9 / 16;
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        scroll.setClipChildren(false);
        scroll.setPadding(0, dp(28), 0, dp(16));
        grid = new GridLayout(this);
        grid.setColumnCount(COLS);
        grid.setClipChildren(false);
        grid.setClipToPadding(false);
        scroll.addView(grid);
        content.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        TextView footer = Ui.text(this, "OK opens the app.   Hold OK or press Menu for options.   Back returns home.",
                14, Ui.REGULAR, Ui.INK_3);
        footer.setPadding(0, dp(8), 0, dp(Ui.SAFE_Y));
        content.addView(footer);

        View first = null, refocus = null;
        int i = 0;
        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            CharSequence label = ri.loadLabel(pm);
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setClipChildren(false);

            FrameLayout card = Ui.card(this, ri.activityInfo.loadBanner(pm), ri.loadIcon(pm), label);
            card.setFocusable(true);
            card.setTag(pkg);
            TextView badge = Ui.text(this, "Hidden", 12, Ui.MEDIUM, Ui.WARN);
            badge.setBackground(Ui.pill(this, 0xCC000000, 10));
            badge.setPadding(dp(8), dp(4), dp(8), dp(4));
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.END);
            blp.setMargins(0, dp(6), dp(6), 0);
            card.addView(badge, blp);
            cell.addView(card, new LinearLayout.LayoutParams(dp(tileW), dp(tileH)));

            TextView name = Ui.text(this, label, 15, Ui.REGULAR, Ui.INK_2);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setPadding(dp(2), dp(14), 0, 0);
            cell.addView(name, new LinearLayout.LayoutParams(dp(tileW), -2));
            showHidden(card, badge, hidden.contains(pkg));
            name.setAlpha(Ui.showName(this, false) ? 1f : 0f);  // Settings › Icons & tiles › App names

            card.setOnFocusChangeListener((v, focused) -> {
                Ui.lift(v, focused, 1.08f);
                v.setForeground(focused ? Ui.ring(this, Ui.ACCENT) : null);
                name.setTextColor(focused ? Ui.INK : Ui.INK_2);
                name.setTypeface(focused ? Ui.MEDIUM : Ui.REGULAR);
                name.setAlpha(Ui.showName(this, focused) ? 1f : 0f);
            });
            card.setOnClickListener(v -> open(pkg));
            card.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode == KeyEvent.KEYCODE_MENU) {
                    if (event.getAction() == KeyEvent.ACTION_UP) openMenu(card, badge, pkg, label);
                    return true;
                }
                boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
                if (ok && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 1) {
                    v.setPressed(false);
                    // Focus moves into the panel, so the release lands there (repeats never click).
                    openMenu(card, badge, pkg, label);
                    return true;
                }
                if (ok && event.getRepeatCount() > 1) return true;
                return false;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.setMargins(0, 0, i % COLS == COLS - 1 ? 0 : dp(GAP), dp(20));
            grid.addView(cell, lp);
            if (first == null) first = card;
            if (pkg.equals(focusPkg)) refocus = card;
            i++;
        }
        setContentView(root);
        View target = refocus != null ? refocus : first;
        if (target != null) target.requestFocus();
    }

    /** Hidden apps stay listed, dimmed, with a badge. */
    private static void showHidden(View card, View badge, boolean hidden) {
        ((ViewGroup) card).getChildAt(0).setAlpha(hidden ? 0.4f : 1f);
        badge.setVisibility(hidden ? View.VISIBLE : View.GONE);
    }

    private void open(String pkg) {
        Intent i = Recents.launchIntent(this, pkg);
        if (i != null) startActivity(i);
    }

    // ---- App options panel (from Summit) ----

    private void openMenu(View card, View badge, String pkg, CharSequence label) {
        if (menu != null) return;
        focusPkg = pkg;
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32), dp(Ui.SAFE_Y + 8), dp(32), dp(Ui.SAFE_Y));
        panel.setBackgroundColor(0xF21C1E24);
        TextView title = Ui.text(this, label, 24, Ui.REGULAR, Ui.INK);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        panel.addView(title);
        TextView sub = Ui.text(this, pkg, 13, Ui.REGULAR, Ui.INK_3);
        sub.setPadding(0, dp(8), 0, dp(24));
        panel.addView(sub);

        boolean isHidden = hidden(prefs).contains(pkg);
        Button openB = menuButton(panel, "Open");
        openB.setOnClickListener(v -> {
            closeMenu(card);
            open(pkg);
        });
        menuButton(panel, isHidden ? "Show on home row" : "Hide from home row").setOnClickListener(v -> {
            Set<String> h = hidden(prefs);
            boolean nowHidden = !h.remove(pkg);
            if (nowHidden) h.add(pkg);
            prefs.edit().putStringSet(HIDDEN, h).apply();
            showHidden(card, badge, nowHidden);
            closeMenu(card);
        });
        menuButton(panel, "App info").setOnClickListener(v -> {
            closeMenu(card);
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg)));
            } catch (Exception ignored) {
            }
        });
        menuButton(panel, "Uninstall").setOnClickListener(v -> {
            closeMenu(card);
            try {
                startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)));
            } catch (Exception ignored) {
            }
        });

        // Keep focus inside the panel while it's open.
        content.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        menu = panel;
        root.addView(panel, new FrameLayout.LayoutParams(dp(340), -1, Gravity.END));
        panel.setTranslationX(dp(340));
        panel.animate().translationX(0).setDuration(150).start();
        openB.requestFocus();
    }

    private Button menuButton(LinearLayout panel, String text) {
        Button b = Ui.button(this, text, Ui.GLASS_2);
        b.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        b.setTextSize(17);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
        lp.bottomMargin = dp(10);
        panel.addView(b, lp);
        return b;
    }

    private void closeMenu(View card) {
        if (menu == null) return;
        root.removeView(menu);
        menu = null;
        content.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        card.requestFocus();
    }

    @Override
    public void onBackPressed() {
        if (menu != null) {
            View card = grid.findViewWithTag(focusPkg);
            closeMenu(card != null ? card : grid);
            return;
        }
        super.onBackPressed();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
