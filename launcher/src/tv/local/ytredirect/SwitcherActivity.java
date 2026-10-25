package tv.local.ytredirect;

import android.app.Activity;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal recent-apps switcher (Google TV has none). Lists recently used apps with
 * Open / Close; Close force-stops via {@link RedirectService}.
 */
public class SwitcherActivity extends Activity {
    static boolean visible;
    private static final long WINDOW_MS = 6 * 60 * 60 * 1000L;
    private static final int MAX_APPS = 6;

    private LinearLayout list;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0E0E12);

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32), dp(24), dp(32), dp(24));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1C1C22);
        bg.setCornerRadius(dp(20));
        panel.setBackground(bg);
        root.addView(panel, new LinearLayout.LayoutParams(dp(640), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("Recent apps");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button closeAll = button("Close all");
        header.addView(closeAll);
        panel.addView(header);

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(0, dp(12), 0, 0);
        panel.addView(list);

        List<String> apps = recentApps(getIntent().getStringExtra("current"));
        Button firstClose = null;
        for (String pkg : apps) {
            Button b = addRow(pkg);
            if (firstClose == null) firstClose = b;
        }
        if (apps.isEmpty()) {
            TextView none = new TextView(this);
            none.setText("No recent apps");
            none.setTextColor(0xFFAAAAAA);
            none.setTextSize(18);
            list.addView(none);
        }
        closeAll.setOnClickListener(v -> {
            forceStop(apps);
        });
        setContentView(root);
        // Most recent app first, with Close focused: one press closes what you were using.
        View initial = firstClose != null ? firstClose : closeAll;
        root.post(initial::requestFocus);
    }

    private Button addRow(String pkg) {
        PackageManager pm = getPackageManager();
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(4), 0, dp(4));

        ImageView icon = new ImageView(this);
        TextView label = new TextView(this);
        try {
            icon.setImageDrawable(pm.getApplicationIcon(pkg));
            label.setText(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (PackageManager.NameNotFoundException e) {
            label.setText(pkg);
        }
        row.addView(icon, new LinearLayout.LayoutParams(dp(36), dp(36)));
        label.setTextColor(Color.WHITE);
        label.setTextSize(20);
        label.setPadding(dp(16), 0, 0, 0);
        row.addView(label, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button open = button("Open");
        open.setOnClickListener(v -> {
            Intent i = launchIntent(pkg);
            if (i != null) startActivity(i);
            finish();
        });
        Button close = button("Close");
        close.setOnClickListener(v -> {
            forceStop(java.util.Collections.singletonList(pkg));
        });
        row.addView(open);
        row.addView(close);
        list.addView(row);
        return close;
    }

    private List<String> recentApps(String current) {
        Map<String, Long> last = new LinkedHashMap<>();
        UsageStatsManager usm = (UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        UsageEvents events = usm.queryEvents(now - WINDOW_MS, now);
        UsageEvents.Event e = new UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) last.put(e.getPackageName(), e.getTimeStamp());
        }
        if (current != null) last.put(current, Long.MAX_VALUE);
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(last.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<String> out = new ArrayList<>();
        for (Map.Entry<String, Long> en : sorted) {
            String pkg = en.getKey();
            if (pkg.equals(getPackageName()) || RedirectService.PROTECTED.contains(pkg) || isHome(pkg) || launchIntent(pkg) == null) continue;
            out.add(pkg);
            if (out.size() == MAX_APPS) break;
        }
        return out;
    }

    private boolean isHome(String pkg) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg);
        return !getPackageManager().queryIntentActivities(home, 0).isEmpty();
    }

    private Intent launchIntent(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
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

    /** Pill button with an obvious focused state (white fill, dark text) for the remote. */
    private Button button(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(18);
        b.setFocusable(true);
        b.setMinWidth(dp(110));
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setStateListAnimator(null);
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        bg.addState(new int[]{android.R.attr.state_focused}, pill(Color.WHITE));
        bg.addState(new int[]{}, pill(0xFF33333B));
        b.setBackground(bg);
        b.setTextColor(new android.content.res.ColorStateList(
                new int[][]{{android.R.attr.state_focused}, {}},
                new int[]{0xFF111111, 0xFFDDDDDD}));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44));
        lp.leftMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable pill(int color) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(22));
        return d;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
