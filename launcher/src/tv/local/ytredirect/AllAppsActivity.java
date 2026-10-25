package tv.local.ytredirect;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Every installed app in a grid. OK opens; hold OK to hide or show it on the home row. */
public class AllAppsActivity extends Activity {
    static final String HIDDEN = "hidden";
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("home", MODE_PRIVATE);
        build();
    }

    static Set<String> hidden(SharedPreferences prefs) {
        return new HashSet<>(prefs.getStringSet(HIDDEN, new HashSet<>()));
    }

    private void build() {
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
        Set<String> hidden = hidden(prefs);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF101015);
        root.setPadding(dp(56), dp(32), dp(56), dp(16));
        TextView title = new TextView(this);
        title.setText("All apps");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Hold OK to hide or show an app on the home screen");
        sub.setTextColor(0x99FFFFFF);
        sub.setTextSize(14);
        sub.setPadding(0, 0, 0, dp(16));
        root.addView(sub);

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(6);
        grid.setClipChildren(false);
        scroll.addView(grid);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        for (ResolveInfo ri : apps) {
            String pkg = ri.activityInfo.packageName;
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER);
            cell.setFocusable(true);
            cell.setPadding(dp(8), dp(12), dp(8), dp(12));
            cell.setBackground(focusBg());
            ImageView icon = new ImageView(this);
            icon.setImageDrawable(ri.loadIcon(pm));
            cell.addView(icon, new LinearLayout.LayoutParams(dp(56), dp(56)));
            TextView name = new TextView(this);
            name.setText(ri.loadLabel(pm));
            name.setTextColor(Color.WHITE);
            name.setTextSize(14);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setGravity(Gravity.CENTER);
            name.setPadding(0, dp(8), 0, 0);
            cell.addView(name, new LinearLayout.LayoutParams(-1, -2));
            TextView badge = new TextView(this);
            badge.setTextSize(11);
            badge.setTextColor(0xFFFFB74D);
            badge.setText(hidden.contains(pkg) ? "Hidden from home" : "");
            cell.addView(badge);
            if (hidden.contains(pkg)) icon.setAlpha(0.4f);

            Intent launch = pm.getLeanbackLaunchIntentForPackage(pkg);
            if (launch == null) launch = pm.getLaunchIntentForPackage(pkg);
            Intent open = launch;
            cell.setOnClickListener(v -> {
                if (open != null) startActivity(open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            });
            cell.setOnKeyListener((v, keyCode, event) -> {
                boolean ok = keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER;
                if (ok && event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 1) {
                    v.setPressed(false);
                    Set<String> h = hidden(prefs);
                    boolean nowHidden = !h.remove(pkg);
                    if (nowHidden) h.add(pkg);
                    prefs.edit().putStringSet(HIDDEN, h).apply();
                    badge.setText(nowHidden ? "Hidden from home" : "");
                    icon.setAlpha(nowHidden ? 0.4f : 1f);
                    swallowUp = true;
                    return true;
                }
                if (ok && event.getRepeatCount() > 1) return true;
                if (ok && event.getAction() == KeyEvent.ACTION_UP && swallowUp) {
                    swallowUp = false;
                    return true;
                }
                return false;
            });
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = dp(140);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            grid.addView(cell, lp);
        }
        setContentView(root);
        if (grid.getChildCount() > 0) grid.getChildAt(0).requestFocus();
    }

    private boolean swallowUp;

    private android.graphics.drawable.StateListDrawable focusBg() {
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, Ui.pill(this, 0x33FFFFFF, 16));
        bg.addState(new int[]{}, Ui.pill(this, 0x00000000, 16));
        return bg;
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
