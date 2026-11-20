package tv.local.ytredirect;

import android.accessibilityservice.AccessibilityService;
import android.app.ActivityManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TCL remote helper (also used on the NetTV box):
 *  - YouTube, Prime and TCL Channel buttons -> whatever Settings › Remote buttons says.
 *    Defaults: SmartTube, Spotify and the recent-apps switcher, whose Close force-stops
 *    apps by pressing "Force stop" / "OK" in the app's settings page.
 *  - Home button     -> our HomeActivity instead of the Google TV launcher
 *  - Auto-close: an app not used for 10 minutes is closed. Every 2 minutes a sweep reads the
 *    usage history (so it also catches apps from before a reboot, or that started themselves)
 *    and kills their background processes. That can't touch apps holding a foreground service
 *    (Stremio's server, a paused Spotify), and apps can't see which other apps are running, so
 *    at most every 30 minutes the idle apps are also force-stopped through their settings pages
 *    (a greyed-out Force stop means not running: skipped). Only while Home is showing, nothing
 *    is playing and the remote has been idle for 2 minutes; any key press cuts it short.
 */
public class RedirectService extends AccessibilityService {
    /**
     * Turn this service back on if something switched it off (the TCL resets accessibility_enabled
     * on reboot). Needs WRITE_SECURE_SETTINGS, granted over adb; without it this does nothing.
     */
    static void ensureEnabled(android.content.Context c) {
        if (c.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.content.ContentResolver r = c.getContentResolver();
        String me = c.getPackageName() + "/" + RedirectService.class.getName();
        try {
            // Already running? Then leave the settings alone.
            android.view.accessibility.AccessibilityManager am =
                    (android.view.accessibility.AccessibilityManager) c.getSystemService(ACCESSIBILITY_SERVICE);
            for (android.accessibilityservice.AccessibilityServiceInfo i : am.getEnabledAccessibilityServiceList(
                    android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)) {
                if (me.equals(i.getId())) return;
            }
            String list = android.provider.Settings.Secure.getString(r, "enabled_accessibility_services");
            String others = list == null ? "" : (":" + list + ":").replace(":" + me + ":", ":").replaceAll("^:+|:+$", "");
            android.provider.Settings.Secure.putInt(r, "accessibility_enabled", 1);
            // Android only (re)binds services when this list changes, so write it without us, then with us.
            android.provider.Settings.Secure.putString(r, "enabled_accessibility_services", others);
            android.provider.Settings.Secure.putString(r, "enabled_accessibility_services",
                    others.isEmpty() ? me : others + ":" + me);
            // After a reboot the TCL's accessibility manager ignores settings changes and only
            // re-reads them on a package change: flip our own hidden component to cause one.
            android.content.pm.PackageManager pm = c.getPackageManager();
            android.content.ComponentName nudge = new android.content.ComponentName(c, c.getPackageName() + ".Nudge");
            int now = pm.getComponentEnabledSetting(nudge);
            pm.setComponentEnabledSetting(nudge,
                    now == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
                            : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
        } catch (RuntimeException e) {
            android.util.Log.w("RedirectService", "could not re-enable the remote helper", e);
        }
    }

    private static final String TV_SETTINGS = "com.android.tv.settings";
    private static final String GOOGLE_LAUNCHER = "com.google.android.apps.tv.launcherx";
    private static final int KEYCODE_TCL_YOUTUBE = 4065;
    private static final int KEYCODE_TCL_PRIME = 4099;
    private static final int KEYCODE_TCL_CHANNEL = 4020;
    /** NetTV box remote's YouTube button. */
    private static final int KEYCODE_NETTV_YOUTUBE = KeyEvent.KEYCODE_F11;
    private static final long BACKGROUND_KILL_MS = 10 * 60_000;
    private static final long SWEEP_MS = 2 * 60_000;
    private static final long REMOTE_IDLE_MS = 2 * 60_000;
    /** Never force-stopped: interrupting these breaks app updates and Google services. */
    private static final Set<String> NEVER_FORCE_STOP = new HashSet<>(Arrays.asList(
            "com.android.vending", "com.google.android.gms", "com.google.android.katniss"));
    private static final long FORCE_PASS_MS = 30 * 60_000;
    private static final boolean FORCE_PASS_ON = false;  // off until tested on the TV
    private long lastKeyAt, lastForcePass;
    private boolean autoPass;  // the current force-stop run is the idle sweep's, not the switcher's
    /** System apps that should never be killed or offered in the switcher. */
    static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
            "com.tcl.tv", TV_SETTINGS, "com.android.systemui"));

    static RedirectService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String lastPkg = "";
    private String fgPkg;
    private final Map<String, Runnable> pendingKills = new HashMap<>();

    private final ArrayDeque<String> stopQueue = new ArrayDeque<>();
    private String stopping;
    private boolean confirming;
    private int tries;

    @Override
    protected void onServiceConnected() {
        instance = this;
        handler.removeCallbacks(sweep);
        handler.postDelayed(sweep, SWEEP_MS);
    }

    /** Close apps that haven't been used for BACKGROUND_KILL_MS (see the class comment). */
    private final Runnable sweep = new Runnable() {
        @Override
        public void run() {
            handler.postDelayed(this, SWEEP_MS);
            try {
                closeIdleApps();
            } catch (RuntimeException e) {
                android.util.Log.w("RedirectService", "sweep failed", e);
            }
        }
    };

    private void closeIdleApps() {
        if (stopping != null) return;
        long now = System.currentTimeMillis();
        android.app.usage.UsageStatsManager usm =
                (android.app.usage.UsageStatsManager) getSystemService(USAGE_STATS_SERVICE);
        Map<String, Long> lastUsed = new HashMap<>();
        android.app.usage.UsageEvents events = usm.queryEvents(now - 24 * 3_600_000L, now);
        android.app.usage.UsageEvents.Event e = new android.app.usage.UsageEvents.Event();
        while (events.hasNextEvent()) {
            events.getNextEvent(e);
            String pkg = e.getPackageName();
            switch (e.getEventType()) {
                case android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED:
                case android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED:
                    lastUsed.put(pkg, e.getTimeStamp());
                    break;
                default: break;
            }
        }
        // What's on screen now (fgPkg can be stale: at boot Home appears before this service starts).
        AccessibilityNodeInfo root = getRootInActiveWindow();
        String onScreen = root != null && root.getPackageName() != null ? root.getPackageName().toString() : fgPkg;
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<String> stubborn = new java.util.ArrayList<>();
        for (android.content.pm.ApplicationInfo app : getPackageManager().getInstalledApplications(0)) {
            String pkg = app.packageName;
            if (pkg.equals(onScreen) || pkg.equals(fgPkg) || pkg.equals(getPackageName()) || !isKillable(pkg)) continue;
            Long used = lastUsed.get(pkg);
            if (used != null && now - used < BACKGROUND_KILL_MS) continue;
            am.killBackgroundProcesses(pkg);  // no-op if it isn't running
            if (!NEVER_FORCE_STOP.contains(pkg)) stubborn.add(pkg);
        }
        // Force stop needs TV Settings on screen for a moment: only while nobody is using the TV.
        android.media.AudioManager audio = (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
        boolean idleOnHome = getPackageName().equals(onScreen) && now - lastKeyAt > REMOTE_IDLE_MS;
        if (FORCE_PASS_ON && !stubborn.isEmpty() && idleOnHome && !audio.isMusicActive() && now - lastForcePass > FORCE_PASS_MS) {
            lastForcePass = now;
            autoPass = true;
            android.util.Log.i("RedirectService", "force-stopping idle apps " + stubborn);
            forceStop(stubborn);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence cs = event.getPackageName();
        if (cs == null) return;
        String pkg = cs.toString();
        boolean ownHome = pkg.equals(getPackageName()) && event.getClassName() != null
                && event.getClassName().toString().endsWith("HomeActivity");
        if (pkg.equals("com.android.systemui") || isKeyboard(pkg)) return;
        if (pkg.equals(getPackageName()) && !ownHome) return;
        if (stopping != null) return;
        // Home button lands on Google TV's home page; swap in our home screen. Its other
        // windows (the quick-settings panel from the remote's gear button) are left alone.
        if (pkg.equals(GOOGLE_LAUNCHER)) {
            CharSequence cls = event.getClassName();
            if (cls != null && cls.toString().endsWith(".home.HomeActivity")) {
                goHome();
            }
            return;
        }
        if (!pkg.equals(fgPkg)) {
            if (fgPkg != null) scheduleKill(fgPkg);
            Runnable pending = pendingKills.remove(pkg);
            if (pending != null) handler.removeCallbacks(pending);
            fgPkg = pkg;
        }
        lastPkg = pkg;
    }

    private void scheduleKill(String pkg) {
        if (!isKillable(pkg) || pendingKills.containsKey(pkg)) return;
        Runnable kill = () -> {
            pendingKills.remove(pkg);
            ((ActivityManager) getSystemService(ACTIVITY_SERVICE)).killBackgroundProcesses(pkg);
        };
        pendingKills.put(pkg, kill);
        handler.postDelayed(kill, BACKGROUND_KILL_MS);
    }

    /** Only normal launchable apps; never home screens or protected system apps. */
    private boolean isKillable(String pkg) {
        if (PROTECTED.contains(pkg)) return false;
        Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).setPackage(pkg);
        if (!getPackageManager().queryIntentActivities(home, 0).isEmpty()) return false;
        return getPackageManager().getLeanbackLaunchIntentForPackage(pkg) != null
                || getPackageManager().getLaunchIntentForPackage(pkg) != null;
    }

    private boolean isKeyboard(String pkg) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        for (InputMethodInfo info : imm.getInputMethodList()) {
            if (info.getPackageName().equals(pkg)) return true;
        }
        return false;
    }

    /**
     * Remote app buttons do what Settings › Remote buttons says: open an app, open the recent-apps
     * switcher, or keep the TV's own behaviour. A chosen app that's been uninstalled falls back to
     * the TV's behaviour. Down and up are decided the same way, so the system never gets half a press.
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        lastKeyAt = System.currentTimeMillis();
        if (autoPass && stopping != null) stopQueue.clear();  // someone's using the TV: stop after this app
        String key;
        switch (event.getKeyCode()) {
            case KEYCODE_TCL_YOUTUBE:
            case KEYCODE_NETTV_YOUTUBE: key = Options.BTN_YOUTUBE; break;
            case KEYCODE_TCL_PRIME: key = Options.BTN_PRIME; break;
            case KEYCODE_TCL_CHANNEL: key = Options.BTN_CHANNEL; break;
            case KeyEvent.KEYCODE_HOME:
                if (event.getAction() == KeyEvent.ACTION_UP) goHome();
                return true;
            default: return false;
        }
        String target = Options.str(this, key);
        if (Options.TV_DEFAULT.equals(target)) return false;
        if (Options.SWITCHER.equals(target)) {
            if (event.getAction() == KeyEvent.ACTION_UP) openSwitcher();
            return true;
        }
        Intent launch = launchIntent(target);
        if (launch == null) return false;  // uninstalled: let the TV handle the button
        if (event.getAction() == KeyEvent.ACTION_UP) startActivity(launch);
        return true;
    }

    /** Straight to our home screen, without Google TV's launcher flashing first. */
    private void goHome() {
        startActivity(new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("homePressed", true));
    }

    /** Pressing the button again while the switcher is showing closes it. */
    private void openSwitcher() {
        Intent i = new Intent(this, SwitcherActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra("current", lastPkg)
                .putExtra("dismiss", SwitcherActivity.visible);
        startActivity(i);
    }

    private Intent launchIntent(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    // ---- Force stop via the app's settings page, then go Home ----

    void forceStop(List<String> pkgs) {
        stopQueue.addAll(pkgs);
        if (stopping == null) nextStop();
    }

    private void nextStop() {
        stopping = stopQueue.poll();
        if (stopping == null) {
            autoPass = false;
            performGlobalAction(GLOBAL_ACTION_HOME);
            return;
        }
        confirming = false;
        tries = 0;
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + stopping))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK
                        | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS));
        handler.postDelayed(this::stopStep, 400);
    }

    private void stopStep() {
        if (++tries > 20) { nextStop(); return; }  // ~5s: give up on this app
        AccessibilityNodeInfo button = findButton(confirming ? "OK" : "Force stop");
        if (button == null) { handler.postDelayed(this::stopStep, 250); return; }
        if (!button.isEnabled()) { nextStop(); return; }  // already stopped
        button.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        if (confirming) {
            handler.postDelayed(this::nextStop, 300);
        } else {
            confirming = true;
            tries = 0;
            handler.postDelayed(this::stopStep, 300);
        }
    }

    /** Clickable ancestor of the node whose text is exactly {@code text}, in TV Settings only. */
    private AccessibilityNodeInfo findButton(String text) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null || root.getPackageName() == null
                || !TV_SETTINGS.contentEquals(root.getPackageName())) return null;
        for (AccessibilityNodeInfo n : root.findAccessibilityNodeInfosByText(text)) {
            if (n.getText() == null || !text.contentEquals(n.getText())) continue;
            for (AccessibilityNodeInfo p = n; p != null; p = p.getParent()) {
                if (p.isClickable()) return p;
            }
        }
        return null;
    }

    @Override
    public void onInterrupt() {}
}
