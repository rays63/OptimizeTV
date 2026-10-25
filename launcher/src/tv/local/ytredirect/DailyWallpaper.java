package tv.local.ytredirect;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Scanner;

/**
 * Bing's photo of the day (the same feed behind Windows Spotlight), downloaded once a day
 * and cached on disk, so the last photo keeps showing when there is no internet.
 */
class DailyWallpaper {
    private static final String FEED = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US";
    private static final String PREFS = "wallpaper";

    private final Context context;
    private final SharedPreferences prefs;

    DailyWallpaper(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private File file() {
        return new File(context.getFilesDir(), "wallpaper.jpg");
    }

    private static String today() {
        return new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date());
    }

    boolean isStale() {
        return !today().equals(prefs.getString("date", "")) || !file().exists();
    }

    /** Where the photo was taken, e.g. "Lake Bled, Slovenia"; empty if unknown. */
    String caption() {
        return prefs.getString("caption", "");
    }

    /** Cached photo scaled to about screen size (RGB_565 keeps it near 4 MB), or null. */
    Bitmap load(int maxWidth) {
        File f = file();
        if (!f.exists()) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inJustDecodeBounds = false;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= maxWidth) o.inSampleSize *= 2;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** Today's photo. Blocking: call off the main thread. Returns true on success. */
    boolean download() {
        return download(0);
    }

    /** Bing keeps the last 8 days; step back through them for a different photo today. */
    boolean downloadNext() {
        return download((prefs.getInt("idx", 0) + 1) % 8);
    }

    private boolean download(int idx) {
        try {
            JSONObject img = new JSONObject(get(FEED.replace("idx=0", "idx=" + idx))).getJSONArray("images").getJSONObject(0);
            String url = "https://www.bing.com" + img.getString("urlbase") + "_1920x1080.jpg";
            File tmp = new File(context.getFilesDir(), "wallpaper.tmp");
            HttpURLConnection con = open(url);
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            if (tmp.length() < 10_000 || !tmp.renameTo(file())) return false;
            // "Lake Bled, Slovenia (© Photographer/Agency)" -> "Lake Bled, Slovenia"
            String caption = img.optString("copyright", "").replaceAll("\\s*\\(©.*\\)\\s*$", "");
            prefs.edit().putString("date", today()).putString("caption", caption).putInt("idx", idx).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(20_000);
        return con;
    }

    private static String get(String url) throws Exception {
        try (InputStream in = open(url).getInputStream(); Scanner s = new Scanner(in).useDelimiter("\\A")) {
            return s.next();
        }
    }
}
