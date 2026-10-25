package tv.local.ytredirect;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.Scanner;

/** Home screen options: name, weather city, wallpaper style, what to show, and resets. */
public class CustomizeActivity extends Activity {
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        prefs = getSharedPreferences("home", MODE_PRIVATE);

        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(56), dp(32), dp(56), dp(32));
        TextView title = new TextView(this);
        title.setText("Customize home");
        title.setTextColor(Color.WHITE);
        title.setTextSize(28);
        title.setPadding(0, 0, 0, dp(16));
        list.addView(title);

        EditText name = field(list, "Your name", prefs.getString("name", HomeActivity.DEFAULT_NAME));
        name.setOnEditorActionListener((v, id, e) -> {
            prefs.edit().putString("name", v.getText().toString().trim()).apply();
            toast("Name saved");
            return false;
        });
        EditText city = field(list, "Weather city", prefs.getString("city", HomeActivity.DEFAULT_CITY));
        city.setOnEditorActionListener((v, id, e) -> {
            setCity(v.getText().toString().trim());
            return false;
        });

        Button background = toggle(list, "Background", "photo", true, "Daily photo", "Drawn scene");
        toggle(list, "Weather", "showWeather", true, "Shown", "Hidden");
        toggle(list, "Photo location", "showCaption", true, "Shown", "Hidden");

        Button resetOrder = row(list, "App order", "Reset to default");
        resetOrder.setOnClickListener(v -> {
            prefs.edit().remove("order").apply();
            toast("App order reset");
        });
        Button unhide = row(list, "Hidden apps", "Show all on home");
        unhide.setOnClickListener(v -> {
            prefs.edit().putStringSet(AllAppsActivity.HIDDEN, new HashSet<>()).apply();
            toast("All apps are back on the home screen");
        });

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF101015);
        scroll.addView(list);
        setContentView(scroll);
        background.requestFocus();
    }

    private EditText field(LinearLayout list, String label, String value) {
        LinearLayout r = rowLayout(list, label);
        EditText e = new EditText(this);
        e.setText(value);
        e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        e.setTextColor(Color.WHITE);
        e.setTextSize(18);
        e.setBackground(Ui.pill(this, 0x22FFFFFF, 12));
        e.setPadding(dp(16), dp(10), dp(16), dp(10));
        r.addView(e, new LinearLayout.LayoutParams(dp(320), -2));
        return e;
    }

    /** Two-state option stored as a boolean; the button shows the current state. */
    private Button toggle(LinearLayout list, String label, String key, boolean def, String on, String off) {
        Button b = row(list, label, prefs.getBoolean(key, def) ? on : off);
        b.setOnClickListener(v -> {
            boolean now = !prefs.getBoolean(key, def);
            prefs.edit().putBoolean(key, now).apply();
            b.setText(now ? on : off);
        });
        return b;
    }

    private Button row(LinearLayout list, String label, String action) {
        LinearLayout r = rowLayout(list, label);
        Button b = Ui.button(this, action, 0x22FFFFFF);
        r.addView(b, new LinearLayout.LayoutParams(-2, dp(42)));
        return b;
    }

    private LinearLayout rowLayout(LinearLayout list, String label) {
        LinearLayout r = new LinearLayout(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(8), 0, dp(8));
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xDDFFFFFF);
        t.setTextSize(18);
        r.addView(t, new LinearLayout.LayoutParams(dp(220), -2));
        list.addView(r);
        return r;
    }

    /** Looks the city up with Open-Meteo's free geocoder and stores its coordinates. */
    private void setCity(String query) {
        if (query.isEmpty()) return;
        new Thread(() -> {
            try {
                URL url = new URL("https://geocoding-api.open-meteo.com/v1/search?count=1&name="
                        + URLEncoder.encode(query, "UTF-8"));
                try (InputStream in = url.openStream(); Scanner s = new Scanner(in).useDelimiter("\\A")) {
                    JSONArray results = new JSONObject(s.next()).optJSONArray("results");
                    if (results == null || results.length() == 0) {
                        runOnUiThread(() -> toast("Couldn't find " + query));
                        return;
                    }
                    JSONObject r = results.getJSONObject(0);
                    prefs.edit().putString("city", r.getString("name"))
                            .putFloat("lat", (float) r.getDouble("latitude"))
                            .putFloat("lon", (float) r.getDouble("longitude"))
                            .putLong("weatherAt", 0).apply();
                    runOnUiThread(() -> toast("Weather set to " + r.optString("name") + ", " + r.optString("country")));
                }
            } catch (Exception e) {
                runOnUiThread(() -> toast("No internet, city not changed"));
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
