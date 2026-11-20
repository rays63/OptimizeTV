package tv.local.ytredirect;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Recently used apps from usage stats, newest first. Shared by the Continue row and the switcher. */
final class Recents {
    private Recents() {}

    static final class Entry {
        final String pkg;
        final long lastUsed;

        Entry(String pkg, long lastUsed) {
            this.pkg = pkg;
            this.lastUsed = lastUsed;
        }
    }

    /**
     * Apps opened within {@code windowMs}, skipping this launcher, home screens, protected system
     * apps and anything without a launch intent. Empty if usage access isn't granted.
     * Can take a moment on slow boxes: call off the main thread when the window is long.
     */
    static List<Entry> query(Context c, long windowMs, int max, String current) {
        Map<String, Long> last = new LinkedHashMap<>();
        UsageStatsManager usm = (UsageStatsManager) c.getSystemService(Context.USAGE_STATS_SERVICE);
        long now = System.currentTimeMillis();
        try {
            UsageEvents events = usm.queryEvents(now - windowMs, now);
            UsageEvents.Event e = new UsageEvents.Event();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                if (e.getEventType() == UsageEvents.Event.ACTIVITY_RESUMED) last.put(e.getPackageName(), e.getTimeStamp());
            }
        } catch (Exception ignored) {
            // No usage access: behave as if nothing was used.
        }
        if (current != null) last.put(current, now);
        List<Map.Entry<String, Long>> sorted = new ArrayList<>(last.entrySet());
        sorted.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));

        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Long> en : sorted) {
            String pkg = en.getKey();
            if (pkg.equals(c.getPackageName()) || RedirectService.PROTECTED.contains(pkg)
                    || isHome(c, pkg) || launchIntent(c, pkg) == null) continue;
            out.add(new Entry(pkg, en.getValue()));
            if (out.size() == max) break;
        }
        return out;
    }

    static boolean isHome(Context c, String pkg) {
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg);
        return !c.getPackageManager().queryIntentActivities(home, 0).isEmpty();
    }

    static Intent launchIntent(Context c, String pkg) {
        PackageManager pm = c.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    /** "Just now", "5 min ago", "3 h ago", "Yesterday", "4 days ago". */
    static String ago(long time) {
        long min = Math.max(0, (System.currentTimeMillis() - time) / 60_000);
        if (min < 1) return "Just now";
        if (min < 60) return min + " min ago";
        long h = min / 60;
        if (h < 24) return h + " h ago";
        long d = h / 24;
        return d == 1 ? "Yesterday" : d + " days ago";
    }
}
