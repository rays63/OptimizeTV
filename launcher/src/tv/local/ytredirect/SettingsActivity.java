package tv.local.ytredirect;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Settings in Summit's layout: categories on the left, each opening a page of options, with a
 * live preview of Home on the right. Changes apply as soon as they're made.
 */
public class SettingsActivity extends Activity {
    /** Open straight to a category: "weather", "date", ... */
    static final String EXTRA_PAGE = "page";

    private SharedPreferences prefs;
    private LinearLayout list;
    private ScrollView listScroll;
    private HomePreview preview;
    /** Open pages, the category list first; each remembers which row was focused. */
    private final List<Page> stack = new ArrayList<>();

    private static final class Page {
        final String id;
        int focus;

        Page(String id) {
            this.id = id;
        }
    }

    /** One row: a name, its current value (may be empty) and what OK does. */
    private static final class Row {
        final String label;
        final String value;
        final int icon;
        final int trailing;
        final Runnable click;

        Row(String label, String value, int icon, int trailing, Runnable click) {
            this.label = label;
            this.value = value;
            this.icon = icon;
            this.trailing = trailing;
            this.click = click;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = Options.prefs(this);

        FrameLayout root = Ui.backdrop(this, 0x9E0C0D12);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(Ui.SAFE_X), dp(Ui.SAFE_Y), dp(Ui.SAFE_X), dp(Ui.SAFE_Y));
        root.addView(page, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.addView(Ui.tabs(this, Ui.TAB_SETTINGS));
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        String pattern = DateFormat.is24HourFormat(this) ? "HH:mm" : "h:mm";
        top.addView(Ui.text(this, Options.digits(this, new SimpleDateFormat(pattern, Locale.ENGLISH).format(new Date())),
                20, Ui.MEDIUM, Ui.INK_2));
        page.addView(top);

        LinearLayout panes = new LinearLayout(this);
        panes.setPadding(0, dp(24), 0, 0);
        page.addView(panes, new LinearLayout.LayoutParams(-1, 0, 1));

        listScroll = new ScrollView(this);
        listScroll.setVerticalScrollBarEnabled(false);
        listScroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        listScroll.addView(list);
        panes.addView(listScroll, new LinearLayout.LayoutParams(dp(390), -1));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(dp(32), 0, 0, 0);
        right.addView(Ui.text(this, "Preview", 14, Ui.REGULAR, Ui.INK_2));
        preview = new HomePreview(this);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(-1, -2);
        plp.topMargin = dp(10);
        right.addView(preview, plp);
        TextView hint = Ui.text(this, "\u25B2 \u25BC move \u00b7 OK open or choose \u00b7 Back goes back. Changes apply instantly.",
                14, Ui.REGULAR, Ui.INK_2);
        hint.setPadding(0, dp(14), 0, 0);
        right.addView(hint);
        panes.addView(right, new LinearLayout.LayoutParams(0, -2, 1));

        setContentView(root);
        stack.add(new Page("root"));
        String start = getIntent().getStringExtra(EXTRA_PAGE);
        if (start != null) stack.add(new Page(start));
        render();
    }

    @Override
    public void onBackPressed() {
        if (stack.size() > 1) {
            stack.remove(stack.size() - 1);
            render();
        } else {
            super.onBackPressed();
        }
    }

    private void open(String id) {
        stack.get(stack.size() - 1).focus = focusedIndex();
        stack.add(new Page(id));
        render();
    }

    private void back() {
        onBackPressed();
    }

    /** A setting changed: redraw this page (values) and the preview. */
    private void changed() {
        stack.get(stack.size() - 1).focus = focusedIndex();
        render();
        preview.refresh();
    }

    private int focusedIndex() {
        View f = getCurrentFocus();
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i) == f) return i;
        return -1;
    }

    // ---- Pages ----

    private void render() {
        Page page = stack.get(stack.size() - 1);
        list.removeAllViews();
        String id = page.id;
        List<Row> rows = new ArrayList<>();
        String title, crumb = "Settings", note = null;
        boolean reset = true;

        switch (id) {
            case "root":
                title = "Settings";
                crumb = null;
                reset = false;
                rows.add(new Row("Background", "", LineIcon.IMAGE, LineIcon.CHEVRON, () -> open("background")));
                rows.add(new Row("Weather", "", -1, LineIcon.CHEVRON, () -> open("weather")));
                rows.add(new Row("Date", "", LineIcon.CALENDAR, LineIcon.CHEVRON, () -> open("date")));
                rows.add(new Row("Home screen", "", LineIcon.HOME, LineIcon.CHEVRON, () -> open("home")));
                rows.add(new Row("Icons & tiles", "", LineIcon.TILES, LineIcon.CHEVRON, () -> open("tiles")));
                rows.add(new Row("Remote buttons", "", LineIcon.REMOTE, LineIcon.CHEVRON, () -> open("remote")));
                rows.add(new Row("TV settings", "", LineIcon.TV, LineIcon.EXTERNAL, () -> HomeActivity.openTvSettings(this)));
                break;
            case "background":
                title = "Background";
                rows.add(choice("Background", Options.PHOTO, null, new String[]{"Daily photo", "Drawn scene"}));
                rows.add(choice("Photo location", Options.SHOW_CAPTION, null, new String[]{"Shown", "Hidden"}));
                break;
            case "weather":
                title = "Weather";
                rows.add(new Row("City", Options.str(this, Options.CITY), -1, LineIcon.CHEVRON,
                        () -> edit("Weather city", Options.str(this, Options.CITY), this::setCity)));
                rows.add(choice("Temperature", Options.TEMP_UNIT, new String[]{"c", "f"}, new String[]{"\u00b0C", "\u00b0F"}));
                rows.add(choice("Wind speed", Options.WIND_UNIT, new String[]{"kmh", "ms", "mph"}, new String[]{"km/h", "m/s", "mph"}));
                rows.add(choice("Rainfall", Options.RAIN_UNIT, new String[]{"mm", "in"}, new String[]{"mm", "inches"}));
                rows.add(choice("Show on home", Options.SHOW_WEATHER, null, new String[]{"Shown", "Hidden"}));
                break;
            case "date":
                title = "Date";
                rows.add(choice("Calendar", Options.CALENDAR, new String[]{"both", "bs", "ad"},
                        new String[]{"Both (Bikram Sambat and Gregorian)", "Bikram Sambat", "Gregorian"}));
                rows.add(choice("Devanagari digits", Options.DIGITS, null, new String[]{"On: clock, dates and weather", "Off"}));
                break;
            case "home": {
                title = "Home screen";
                String name = Options.str(this, Options.NAME);
                rows.add(new Row("Your name", name.isEmpty() ? "Not set" : name, -1, LineIcon.CHEVRON,
                        () -> edit("Your name", Options.str(this, Options.NAME), s -> {
                            prefs.edit().putString(Options.NAME, s).apply();
                            changed();
                        })));
                boolean custom = !prefs.getString(Options.ORDER, "").isEmpty();
                rows.add(new Row("App order", custom ? "Your order. OK puts it back to default" : "Default", -1, -1, () -> {
                    if (!prefs.getString(Options.ORDER, "").isEmpty()) {
                        prefs.edit().remove(Options.ORDER).apply();
                        toast("App order is back to default");
                        changed();
                    }
                }));
                int hidden = AllAppsActivity.hidden(prefs).size();
                rows.add(new Row("Hidden apps", hidden == 0 ? "None. Hide apps from the Apps tab"
                        : Options.digits(this, String.valueOf(hidden)) + " hidden. OK shows them all", -1, -1, () -> {
                    if (!AllAppsActivity.hidden(prefs).isEmpty()) {
                        prefs.edit().putStringSet(AllAppsActivity.HIDDEN, new HashSet<>()).apply();
                        toast("All apps are back on the home screen");
                        changed();
                    }
                }));
                break;
            }
            case "tiles":
                title = "Icons & tiles";
                rows.add(choice("App names", Options.APP_NAMES, new String[]{"selected", "always", "off"},
                        new String[]{"When selected", "Always", "Off"}));
                break;
            case "remote":
                title = "Remote buttons";
                rows.add(button("YouTube button", Options.BTN_YOUTUBE));
                rows.add(button("Prime Video button", Options.BTN_PRIME));
                rows.add(button("TCL Channel button", Options.BTN_CHANNEL));
                note = "The Home button always opens this home screen. On the NetTV box, only the YouTube button can be changed.";
                break;
            default:
                if (id.startsWith("choice:")) {
                    reset = false;
                    title = renderChoice(id.substring(7), rows);
                    crumb = "Settings \u203a " + categoryTitle(stack.get(stack.size() - 2).id);
                } else if (id.startsWith("button:")) {
                    reset = false;
                    title = renderButtonPicker(id.substring(7), rows);
                    crumb = "Settings \u203a Remote buttons";
                } else {
                    title = "Settings";
                }
        }

        if (crumb != null) {
            TextView c = Ui.text(this, crumb, 14, Ui.REGULAR, Ui.INK_2);
            c.setPadding(dp(20), dp(8), 0, dp(4));
            list.addView(c);
        }
        TextView t = Ui.text(this, title, 28, Ui.MEDIUM, Ui.INK);
        t.setPadding(dp(20), crumb == null ? dp(8) : 0, 0, dp(16));
        list.addView(t);

        int firstRow = list.getChildCount();
        for (Row r : rows) list.addView(rowView(r, id.equals("root")));
        if (note != null) {
            TextView n = Ui.text(this, note, 14, Ui.REGULAR, Ui.INK_2);
            n.setLineSpacing(dp(3), 1f);
            n.setPadding(dp(20), dp(12), dp(20), 0);
            list.addView(n);
        }
        if (reset) {
            String cat = id;
            View r = rowView(new Row("Reset to default", "", LineIcon.RESET, -1, () -> resetCategory(cat)), true);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) r.getLayoutParams();
            lp.topMargin = dp(24);
            list.addView(r);
        }

        // Focus: the row we came back to, else the current choice, else the first row.
        int target = page.focus >= 0 ? page.focus : firstRow + Math.max(0, selectedIndex(rows));
        if (target >= list.getChildCount() || !list.getChildAt(target).isFocusable()) target = firstRow;
        View f = list.getChildAt(target);
        if (f != null) {
            f.requestFocus();
            listScroll.post(() -> listScroll.requestChildFocus(f, f));
        }
    }

    private int selectedIndex(List<Row> rows) {
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).trailing == LineIcon.CHECK) return i;
        return 0;
    }

    private String categoryTitle(String id) {
        switch (id) {
            case "background": return "Background";
            case "weather": return "Weather";
            case "date": return "Date";
            case "tiles": return "Icons & tiles";
            default: return "Home screen";
        }
    }

    // ---- Choices ----

    /** A row showing the current choice; OK opens the list of choices. values == null: on/off. */
    private Row choice(String label, String key, String[] values, String[] labels) {
        int i = indexOf(key, values);
        String id = "choice:" + key;
        choiceSpecs.put(key, new Object[]{label, values, labels});
        return new Row(label, labels[Math.max(0, i)], -1, LineIcon.CHEVRON, () -> open(id));
    }

    private final java.util.Map<String, Object[]> choiceSpecs = new java.util.HashMap<>();

    private int indexOf(String key, String[] values) {
        if (values == null) return Options.bool(this, key) ? 0 : 1;
        String v = Options.str(this, key);
        for (int i = 0; i < values.length; i++) if (values[i].equals(v)) return i;
        return 0;
    }

    private String renderChoice(String key, List<Row> rows) {
        Object[] spec = choiceSpecs.get(key);
        String label = (String) spec[0];
        String[] values = (String[]) spec[1], labels = (String[]) spec[2];
        int current = indexOf(key, values);
        for (int i = 0; i < labels.length; i++) {
            int pick = i;
            rows.add(new Row(labels[i], "", -1, i == current ? LineIcon.CHECK : -1, () -> {
                if (values == null) prefs.edit().putBoolean(key, pick == 0).apply();
                else prefs.edit().putString(key, values[pick]).apply();
                preview.refresh();
                back();
            }));
        }
        return label;
    }

    // ---- Remote buttons ----

    private Row button(String label, String key) {
        buttonLabels.put(key, label);
        return new Row(label, targetName(Options.str(this, key)), -1, LineIcon.CHEVRON, () -> open("button:" + key));
    }

    private final java.util.Map<String, String> buttonLabels = new java.util.HashMap<>();

    private String targetName(String target) {
        if (Options.SWITCHER.equals(target)) return "Recent apps switcher";
        if (Options.TV_DEFAULT.equals(target)) return "TV default";
        try {
            PackageManager pm = getPackageManager();
            return pm.getApplicationLabel(pm.getApplicationInfo(target, 0)).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return "TV default (app not installed)";
        }
    }

    /** Recent apps switcher, TV default, then every launchable app A to Z. */
    private String renderButtonPicker(String key, List<Row> rows) {
        String current = Options.str(this, key);
        rows.add(pick(key, Options.SWITCHER, "Recent apps switcher", "Built in", current));
        rows.add(pick(key, Options.TV_DEFAULT, "TV default", "What the button did originally", current));
        PackageManager pm = getPackageManager();
        List<ResolveInfo> apps = new ArrayList<>(pm.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER), 0));
        Set<String> seen = new HashSet<>();
        for (ResolveInfo ri : apps) seen.add(ri.activityInfo.packageName);
        for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)) {
            if (seen.add(ri.activityInfo.packageName)) apps.add(ri);
        }
        apps.removeIf(ri -> ri.activityInfo.packageName.equals(getPackageName()));
        apps.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));
        for (ResolveInfo ri : apps) {
            rows.add(pick(key, ri.activityInfo.packageName, ri.loadLabel(pm).toString(), "", current));
        }
        return buttonLabels.containsKey(key) ? buttonLabels.get(key) : "Button";
    }

    private Row pick(String key, String target, String label, String value, String current) {
        return new Row(label, value, -1, target.equals(current) ? LineIcon.CHECK : -1, () -> {
            prefs.edit().putString(key, target).apply();
            back();
        });
    }

    // ---- Resets ----

    private void resetCategory(String id) {
        SharedPreferences.Editor e = prefs.edit();
        switch (id) {
            case "background": e.remove(Options.PHOTO).remove(Options.SHOW_CAPTION); break;
            case "weather":
                e.remove(Options.CITY).remove("lat").remove("lon").remove(Options.TEMP_UNIT)
                        .remove(Options.WIND_UNIT).remove(Options.RAIN_UNIT).remove(Options.SHOW_WEATHER);
                break;
            case "date": e.remove(Options.CALENDAR).remove(Options.DIGITS); break;
            case "home": e.remove(Options.NAME).remove(Options.ORDER).remove(AllAppsActivity.HIDDEN); break;
            case "tiles": e.remove(Options.APP_NAMES); break;
            case "remote": e.remove(Options.BTN_YOUTUBE).remove(Options.BTN_PRIME).remove(Options.BTN_CHANNEL); break;
            default: break;
        }
        e.apply();
        toast(categoryTitleFor(id) + " settings are back to default");
        changed();
    }

    private String categoryTitleFor(String id) {
        return "remote".equals(id) ? "Remote button" : categoryTitle(id);
    }

    // ---- Row view ----

    /** Focusable row: icon, name and value, then a chevron, check or arrow. White when focused. */
    private View rowView(Row r, boolean compact) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setClickable(true);
        row.setPadding(dp(20), 0, dp(16), 0);
        row.setBackground(Ui.focusable(Ui.pill(this, Ui.INK, 12), Ui.pill(this, 0x00000000, 12)));
        if (r.icon >= 0) {
            row.addView(new LineIcon(this, r.icon), new LinearLayout.LayoutParams(dp(24), dp(24)));
        } else if (compact && r.label.equals("Weather")) {
            WeatherIcon w = new WeatherIcon(this);
            w.set(2, true);
            w.setDuplicateParentStateEnabled(true);
            row.addView(w, new LinearLayout.LayoutParams(dp(24), dp(24)));
        }
        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setDuplicateParentStateEnabled(true);
        texts.setPadding(row.getChildCount() > 0 ? dp(16) : 0, 0, dp(8), 0);
        TextView l = Ui.text(this, r.label, 18, Ui.REGULAR, Ui.INK);
        l.setTextColor(Ui.focusText(Ui.INK));
        l.setDuplicateParentStateEnabled(true);
        texts.addView(l);
        if (!r.value.isEmpty()) {
            TextView v = Ui.text(this, r.value, 14, Ui.REGULAR, Ui.INK_2);
            v.setTextColor(new android.content.res.ColorStateList(
                    new int[][]{{android.R.attr.state_focused}, {}}, new int[]{0xB3000000, Ui.INK_2}));
            v.setDuplicateParentStateEnabled(true);
            v.setPadding(0, dp(5), 0, 0);
            texts.addView(v);
        }
        row.addView(texts, new LinearLayout.LayoutParams(0, -2, 1));
        if (r.trailing >= 0) row.addView(new LineIcon(this, r.trailing), new LinearLayout.LayoutParams(dp(20), dp(20)));
        row.setOnClickListener(v -> r.click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(r.value.isEmpty() ? (compact ? 46 : 48) : 60));
        lp.bottomMargin = dp(4);
        row.setLayoutParams(lp);
        return row;
    }

    // ---- Text entry ----

    /** Text entry in a dialog, so the on-screen keyboard only appears when asked for. */
    private void edit(String title, String current, Consumer<String> save) {
        EditText e = new EditText(this);
        e.setText(current);
        e.setSelection(current.length());
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(e);
        AlertDialog d = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Save", (dialog, which) -> save.accept(e.getText().toString().trim()))
                .setNegativeButton("Cancel", null)
                .create();
        e.setOnEditorActionListener((v, id, ev) -> {
            save.accept(e.getText().toString().trim());
            d.dismiss();
            return true;
        });
        d.show();
    }

    /** Looks the city up with Open-Meteo's free geocoder and stores its coordinates. */
    private void setCity(String query) {
        if (query.isEmpty()) return;
        toast("Looking up " + query + "\u2026");
        new Thread(() -> {
            try {
                URL url = new URL("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                        + URLEncoder.encode(query, "UTF-8"));
                try (InputStream in = url.openStream(); Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A")) {
                    JSONArray results = new JSONObject(s.next()).optJSONArray("results");
                    if (results == null || results.length() == 0) {
                        runOnUiThread(() -> toast("No city called \u201C" + query + "\u201D. Check the spelling and try again."));
                        return;
                    }
                    JSONObject r = results.getJSONObject(0);
                    prefs.edit().putString(Options.CITY, r.getString("name"))
                            .putFloat("lat", (float) r.getDouble("latitude"))
                            .putFloat("lon", (float) r.getDouble("longitude")).apply();
                    runOnUiThread(() -> {
                        toast("Weather city set to " + r.optString("name") + ", " + r.optString("country"));
                        if (!isFinishing()) changed();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> toast("No internet. The weather city wasn\u2019t changed."));
            }
        }).start();
    }

    private void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
    }

    private int dp(int v) {
        return Ui.dp(this, v);
    }
}
