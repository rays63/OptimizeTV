package tv.local.ytredirect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

import java.util.Calendar;
import java.util.Random;

/** Illustrated landscape whose sky, light and colours follow the time of day. */
public class SceneView extends View {
    private static final int NIGHT = 0, DAWN = 1, DAY = 2, DUSK = 3;
    // {skyTop, skyBottom, hillFar, hillMid, hillNear, house}
    private static final int[][] PALETTES = {
            {0xFF070B1F, 0xFF27365F, 0xFF1A2448, 0xFF121A37, 0xFF0B1129, 0xFF070B1C},
            {0xFF2B3768, 0xFFF0A27C, 0xFF5B4E78, 0xFF3F3A62, 0xFF2A284A, 0xFF1D1B35},
            {0xFF3E86D1, 0xFFC4E6F7, 0xFF7DB07A, 0xFF5A9460, 0xFF3F7748, 0xFF2E4A33},
            {0xFF262A59, 0xFFEE8A5E, 0xFF4A3D6B, 0xFF342D55, 0xFF221E40, 0xFF161430},
    };

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[][] stars = new float[140][3];
    /** For previewing other times of day (adb: am start ... --ef hour 21); negative = live. */
    static float previewHour = -1;

    public SceneView(Context context) {
        super(context);
        Random r = new Random(42);
        for (float[] s : stars) {
            s[0] = r.nextFloat();
            s[1] = r.nextFloat() * 0.6f;
            s[2] = 0.3f + r.nextFloat() * 0.7f;
        }
    }

    private static int phase(float hour) {
        if (hour >= 5 && hour < 7) return DAWN;
        if (hour >= 7 && hour < 17) return DAY;
        if (hour >= 17 && hour < 19) return DUSK;
        return NIGHT;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        Calendar now = Calendar.getInstance();
        float hour = now.get(Calendar.HOUR_OF_DAY) + now.get(Calendar.MINUTE) / 60f;
        if (previewHour >= 0) hour = previewHour;
        int phase = phase(hour);
        int[] p = PALETTES[phase];
        boolean dark = phase != DAY;

        paint.setShader(new LinearGradient(0, 0, 0, h * 0.75f, p[0], p[1], Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        if (dark) {
            int alpha = phase == NIGHT ? 255 : 120;
            for (float[] s : stars) {
                paint.setColor(Color.argb((int) (alpha * s[2]), 255, 255, 255));
                c.drawCircle(s[0] * w, s[1] * h, 1.2f + s[2] * 1.6f, paint);
            }
        }

        if (phase == NIGHT) drawMoon(c, w * 0.79f, h * 0.2f, h * 0.075f);
        else drawSun(c, w, h, hour, phase);

        drawHill(c, w, h, 0.60f, 0.05f, 1.3f, p[2]);
        drawHill(c, w, h, 0.68f, 0.04f, 2.1f, p[3]);
        drawCottage(c, w * 0.84f, h * 0.745f, h, p[5], dark);
        drawHill(c, w, h, 0.76f, 0.03f, 3.0f, p[4]);

        // Darken the bottom so the app row reads clearly.
        paint.setShader(new LinearGradient(0, h * 0.7f, 0, h, 0x00000000, 0xB0000000, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.7f, w, h, paint);
        paint.setShader(null);
    }

    private void drawMoon(Canvas c, float x, float y, float r) {
        paint.setShader(new RadialGradient(x, y, r * 3, 0x40FFF4D6, 0x00FFF4D6, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r * 3, paint);
        paint.setShader(null);
        Path moon = new Path();
        moon.addCircle(x, y, r, Path.Direction.CW);
        Path bite = new Path();
        bite.addCircle(x + r * 0.45f, y - r * 0.25f, r * 0.9f, Path.Direction.CW);
        moon.op(bite, Path.Op.DIFFERENCE);
        paint.setColor(0xFFFFF6E0);
        c.drawPath(moon, paint);
    }

    /** Sun arcs from left (sunrise) to right (sunset). */
    private void drawSun(Canvas c, float w, float h, float hour, int phase) {
        float t = Math.max(0, Math.min(1, (hour - 5f) / 14f));
        float x = w * (0.15f + 0.7f * t);
        float y = h * (0.62f - 0.45f * (float) Math.sin(Math.PI * t));
        float r = h * 0.06f;
        int core = phase == DAY ? 0xFFFFF3C4 : 0xFFFFC27A;
        paint.setShader(new RadialGradient(x, y, r * 4, (core & 0x00FFFFFF) | 0x55000000,
                core & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r * 4, paint);
        paint.setShader(null);
        paint.setColor(core);
        c.drawCircle(x, y, r, paint);
    }

    private void drawHill(Canvas c, float w, float h, float base, float amp, float freq, int color) {
        Path path = new Path();
        path.moveTo(0, h);
        for (int i = 0; i <= 48; i++) {
            float x = w * i / 48f;
            float y = h * (base - amp * (float) Math.sin(freq * Math.PI * i / 48f + base * 9));
            path.lineTo(x, y);
        }
        path.lineTo(w, h);
        path.close();
        paint.setColor(color);
        c.drawPath(path, paint);
    }

    private void drawCottage(Canvas c, float x, float ground, float h, int color, boolean lit) {
        float s = h * 0.05f;
        if (lit) {
            paint.setShader(new RadialGradient(x, ground - s * 0.6f, s * 3.2f, 0x66FFC45C, 0x00FFC45C,
                    Shader.TileMode.CLAMP));
            c.drawCircle(x, ground - s * 0.6f, s * 3.2f, paint);
            paint.setShader(null);
        }
        paint.setColor(color);
        c.drawRect(x - s, ground - s * 1.2f, x + s, ground, paint);
        Path roof = new Path();
        roof.moveTo(x - s * 1.3f, ground - s * 1.15f);
        roof.lineTo(x, ground - s * 2.1f);
        roof.lineTo(x + s * 1.3f, ground - s * 1.15f);
        roof.close();
        c.drawPath(roof, paint);
        c.drawRect(x + s * 0.45f, ground - s * 2.0f, x + s * 0.75f, ground - s * 1.4f, paint);
        for (float tx : new float[]{x + s * 2.2f, x + s * 3.0f, x - s * 2.3f}) {
            Path tree = new Path();
            tree.moveTo(tx - s * 0.35f, ground);
            tree.lineTo(tx, ground - s * 1.6f);
            tree.lineTo(tx + s * 0.35f, ground);
            tree.close();
            c.drawPath(tree, paint);
        }
        paint.setColor(lit ? 0xFFFFC85A : 0xFF5C6F80);
        c.drawRect(x - s * 0.65f, ground - s * 0.85f, x - s * 0.2f, ground - s * 0.4f, paint);
        c.drawRect(x + s * 0.2f, ground - s * 0.85f, x + s * 0.65f, ground - s * 0.4f, paint);
        if (lit) {
            paint.setColor(0x55FFFFFF);
            c.drawCircle(x + s * 0.7f, ground - s * 2.4f, s * 0.12f, paint);
            c.drawCircle(x + s * 0.85f, ground - s * 2.8f, s * 0.16f, paint);
            c.drawCircle(x + s * 1.05f, ground - s * 3.3f, s * 0.2f, paint);
        }
    }
}
