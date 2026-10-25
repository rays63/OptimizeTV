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
 *  - YouTube button  -> SmartTube (TCL and NetTV remotes)
 *  - Prime button    -> Spotify
 *  - TCL Channel     -> recent-apps switcher, whose Close force-stops apps by
 *                       pressing "Force stop" / "OK" in the app's settings page.
 *  - Home button     -> our HomeActivity instead of the Google TV launcher
 *  - Auto-kill: an app left in the background for 5 minutes is killed. Android refuses
 *    to kill anything visible or playing media, so background music keeps going.
 */
public class RedirectService extends AccessibilityService {
    private static final String SMARTTUBE = "org.smarttube.stable";
    private static final String SPOTIFY = "com.spotify.tv.android";
    private static final String TV_SETTINGS = "com.android.tv.settings";
    private static final String GOOGLE_LAUNCHER = "com.google.android.apps.tv.launcherx";
    private static final int KEYCODE_TCL_YOUTUBE = 4065;
    private static final int KEYCODE_TCL_PRIME = 4099;
    private static final int KEYCODE_TCL_CHANNEL = 4020;
    /** NetTV box remote's YouTube button. */
    private static final int KEYCODE_NETTV_YOUTUBE = KeyEvent.KEYCODE_F11;
    private static final long BACKGROUND_KILL_MS = 5 * 60_000;
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

    /** TCL remote's app buttons: open our chosen apps, even if the stock apps are uninstalled. */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        String target;
        switch (event.getKeyCode()) {
            case KEYCODE_TCL_YOUTUBE:
            case KEYCODE_NETTV_YOUTUBE: target = SMARTTUBE; break;
            case KEYCODE_TCL_PRIME: target = SPOTIFY; break;
            case KEYCODE_TCL_CHANNEL:
                if (event.getAction() == KeyEvent.ACTION_UP) openSwitcher();
                return true;
            case KeyEvent.KEYCODE_HOME:
                if (event.getAction() == KeyEvent.ACTION_UP) goHome();
                return true;
            default: return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) launch(target);
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

    private void launch(String pkg) {
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = getPackageManager().getLaunchIntentForPackage(pkg);
        if (i == null) return;
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(i);
    }

    // ---- Force stop via the app's settings page, then go Home ----

    void forceStop(List<String> pkgs) {
        stopQueue.addAll(pkgs);
        if (stopping == null) nextStop();
    }

    private void nextStop() {
        stopping = stopQueue.poll();
        if (stopping == null) {
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
