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
 * The photo of the day from the source chosen in Settings › Background (Bing, NASA Image of the
 * Day, Wikimedia Commons Picture of the Day or Unsplash via Picsum), downloaded once a day at about
 * 1920 px and cached on disk, so the last photo keeps showing when there is no internet.
 */
class DailyWallpaper {
    private static final String BING = "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=1&mkt=en-US";
    private static final String NASA = "https://www.nasa.gov/feeds/iotd-feed/";
    private static final String WIKI = "https://api.wikimedia.org/feed/v1/wikipedia/en/featured/";
    private static final String UA = "OptimizeTV-launcher/1.0";  // Wikimedia asks for a User-Agent
    private static final int DAYS = 8;  // how far "Next photo" steps back
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

    private String source() {
        return Options.str(context, Options.PHOTO_SOURCE);
    }

    boolean isStale() {
        return !today().equals(prefs.getString("date", "")) || !source().equals(prefs.getString("source", "bing"))
                || !file().exists();
    }

    /** Changes whenever a different photo is saved, so Home can tell its picture is out of date. */
    String key() {
        return prefs.getString("date", "") + "/" + prefs.getString("source", "") + "/" + prefs.getInt("idx", 0);
    }

    /** Where the photo was taken or what it shows, e.g. "Lake Bled, Slovenia"; empty if unknown. */
    String caption() {
        return prefs.getString("caption", "");
    }

    /**
     * The photo's most vivid colour, brightened so it works as a selection colour; white for a
     * grey photo or none. Worked out once per photo from a 64 px copy and cached.
     */
    int accent() {
        if (prefs.contains("accent")) return prefs.getInt("accent", 0xFFFFFFFF);
        Bitmap b = load(64);
        if (b == null) return 0xFFFFFFFF;
        float[] weight = new float[36], sat = new float[36], hsv = new float[3];
        for (int y = 0; y < b.getHeight(); y += 2) {
            for (int x = 0; x < b.getWidth(); x += 2) {
                android.graphics.Color.colorToHSV(b.getPixel(x, y), hsv);
                if (hsv[1] < 0.25f || hsv[2] < 0.3f) continue;  // greys and shadows don't count
                int bucket = Math.min(35, (int) (hsv[0] / 10));
                weight[bucket] += hsv[1] * hsv[2];
                sat[bucket] += hsv[1] * hsv[1] * hsv[2];
            }
        }
        float total = 0;
        int best = 0;
        for (int i = 0; i < 36; i++) {
            total += weight[i];
            if (weight[i] > weight[best]) best = i;
        }
        int accent = 0xFFFFFFFF;
        if (weight[best] > 0 && weight[best] > total * 0.08f) {
            float s = Math.max(0.45f, Math.min(0.8f, sat[best] / weight[best]));
            accent = android.graphics.Color.HSVToColor(new float[]{best * 10 + 5, s, 0.95f});
        }
        prefs.edit().putInt("accent", accent).apply();
        return accent;
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

    /** A different photo: steps back through the source's last few days. */
    boolean downloadNext() {
        int idx = source().equals(prefs.getString("source", "")) ? prefs.getInt("idx", 0) : 0;
        return download((idx + 1) % DAYS);
    }

    private boolean download(int idx) {
        String src = source();
        try {
            String[] got;  // {image url, caption}
            switch (src) {
                case "nasa": got = nasa(idx); break;
                case "wiki": got = wiki(idx); break;
                case "unsplash": got = unsplash(idx); break;
                default: got = bing(idx); break;
            }
            if (got == null) { android.util.Log.w("DailyWallpaper", src + ": no usable photo"); return false; }
            File tmp = new File(context.getFilesDir(), "wallpaper.tmp");
            HttpURLConnection con = open(got[0]);
            try (InputStream in = con.getInputStream(); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buf = new byte[16384];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            }
            if (tmp.length() < 10_000 || !tmp.renameTo(file())) return false;
            prefs.edit().putString("date", today()).putString("source", src)
                    .putString("caption", got[1]).putInt("idx", idx).remove("accent").apply();
            return true;
        } catch (Exception e) {
            android.util.Log.w("DailyWallpaper", src + " photo failed", e);
            return false;
        }
    }

    /** Bing keeps the last 8 days. */
    private static String[] bing(int idx) throws Exception {
        JSONObject img = new JSONObject(get(BING.replace("idx=0", "idx=" + idx))).getJSONArray("images").getJSONObject(0);
        // "Lake Bled, Slovenia (© Photographer/Agency)" -> "Lake Bled, Slovenia"
        String caption = img.optString("copyright", "").replaceAll("\\s*\\(©.*\\)\\s*$", "");
        return new String[]{"https://www.bing.com" + img.getString("urlbase") + "_1920x1080.jpg", caption};
    }

    /** NASA Image of the Day: the RSS feed's idx-th item, resized by NASA's server to 1920 px. */
    private static String[] nasa(int idx) throws Exception {
        String rss = get(NASA);
        String[] items = rss.split("<item>");
        if (items.length < 2) return null;
        String item = items[1 + Math.min(idx, items.length - 2)];
        java.util.regex.Matcher url = java.util.regex.Pattern.compile("<enclosure url=\"([^\"]+)\"").matcher(item);
        java.util.regex.Matcher title = java.util.regex.Pattern.compile("<title>(?:<!\\[CDATA\\[)?(.*?)(?:]]>)?</title>").matcher(item);
        if (!url.find()) return null;
        return new String[]{url.group(1) + "?w=1920", title.find() ? html(title.group(1)) + " \u00b7 NASA" : "NASA Image of the Day"};
    }

    /** Wikimedia Commons Picture of the Day, idx days ago, as a 1920 px thumbnail. */
    private static String[] wiki(int idx) throws Exception {
        java.util.Calendar day = java.util.Calendar.getInstance();
        day.add(java.util.Calendar.DAY_OF_MONTH, -idx);
        JSONObject img = new JSONObject(get(WIKI + new SimpleDateFormat("yyyy/MM/dd", Locale.US).format(day.getTime())))
                .getJSONObject("image");
        JSONObject full = img.getJSONObject("image");
        String src = full.getString("source").replaceAll("\\?.*$", "");
        if (!src.matches("(?i).*\\.(jpe?g|png)$")) return null;  // skip videos, SVGs, GIFs
        String url = src;
        if (full.optInt("width") > 1920) {
            // .../commons/c/cc/Name.jpg -> .../commons/thumb/c/cc/Name.jpg/1920px-Name.jpg
            String name = src.substring(src.lastIndexOf('/') + 1);
            url = src.replace("/commons/", "/commons/thumb/") + "/1920px-" + name;
        }
        String caption = img.has("description") ? img.getJSONObject("description").optString("text", "") : "";
        caption = caption.replaceAll("\\s+", " ").trim();
        int dot = caption.indexOf(". ");
        if (dot > 0) caption = caption.substring(0, dot);
        if (caption.length() > 90) caption = caption.substring(0, 89) + "\u2026";
        return new String[]{url, caption.isEmpty() ? "Wikimedia Commons Picture of the Day" : caption};
    }

    /** Unsplash photos through Picsum (no API key): one photo per day, cropped to 1920x1080. */
    private static String[] unsplash(int idx) throws Exception {
        HttpURLConnection con = open("https://picsum.photos/seed/" + today() + "-" + idx + "/1920/1080");
        con.setInstanceFollowRedirects(false);
        String where = con.getHeaderField("Location");
        con.disconnect();
        if (where == null) return null;
        java.util.regex.Matcher id = java.util.regex.Pattern.compile("/id/(\\d+)/").matcher(where);
        String caption = "Photo from Unsplash";
        if (id.find()) {
            String author = new JSONObject(get("https://picsum.photos/id/" + id.group(1) + "/info")).optString("author", "");
            if (!author.isEmpty()) caption = "Photo by " + author + " on Unsplash";
        }
        return new String[]{where, caption};
    }

    private static String html(String s) {
        return s.replace("&amp;", "&").replace("&#8217;", "\u2019").replace("&#8216;", "\u2018")
                .replace("&quot;", "\"").replace("&#039;", "'").trim();
    }

    private static HttpURLConnection open(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(10_000);
        con.setReadTimeout(20_000);
        con.setRequestProperty("User-Agent", UA);
        return con;
    }

    private static String get(String url) throws Exception {
        try (InputStream in = open(url).getInputStream(); Scanner s = new Scanner(in).useDelimiter("\\A")) {
            return s.next();
        }
    }
}
