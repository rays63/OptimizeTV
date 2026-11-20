package tv.local.ytredirect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Line-drawn weather icon for a WMO code, on a 24-unit grid like the designs. No image files. */
final class WeatherIcon extends View {
    private static final int SUN = 0xFFFFB74D, RAIN = 0xFF8EC1FF, CLOUD = 0xFFE3E6EC;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int code;
    private boolean day = true;

    WeatherIcon(Context c) {
        super(c);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
    }

    void set(int code, boolean day) {
        this.code = code;
        this.day = day;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
        c.scale(s, s);
        paint.setStrokeWidth(2f);
        if (code == 0) {
            if (day) sun(c, 12, 12, 1f); else moon(c);
        } else if (code <= 2) {
            sun(c, 9, 9, 0.7f);
            cloud(c, 3, CLOUD);
        } else if (code == 45 || code == 48) {
            paint.setColor(CLOUD);
            for (int y = 8; y <= 18; y += 5) c.drawLine(3, y, 21, y, paint);
        } else if ((code >= 71 && code <= 77) || code == 85 || code == 86) {
            cloud(c, -2, CLOUD);
            paint.setColor(CLOUD);
            paint.setStyle(Paint.Style.FILL);
            for (float x = 8; x <= 16; x += 4) c.drawCircle(x, 20, 1.2f, paint);
            paint.setStyle(Paint.Style.STROKE);
        } else if (code >= 95) {
            cloud(c, -2, CLOUD);
            paint.setColor(SUN);
            Path bolt = new Path();
            bolt.moveTo(13, 15);
            bolt.lineTo(10, 19);
            bolt.lineTo(14, 19);
            bolt.lineTo(11, 23);
            c.drawPath(bolt, paint);
        } else if (code >= 51) {
            cloud(c, -2, RAIN);
            paint.setColor(RAIN);
            for (float x = 8; x <= 16; x += 4) c.drawLine(x, 18, x - 1, 21, paint);
        } else {
            cloud(c, 0, CLOUD);
        }
        c.restore();
    }

    private void sun(Canvas c, float cx, float cy, float k) {
        paint.setColor(SUN);
        c.drawCircle(cx, cy, 4 * k, paint);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI / 4 * i;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine(cx + dx * 7 * k, cy + dy * 7 * k, cx + dx * 9.5f * k, cy + dy * 9.5f * k, paint);
        }
    }

    private void moon(Canvas c) {
        paint.setColor(0xFFFFF6E0);
        Path p = new Path();
        p.addArc(new RectF(4, 4, 20, 20), 60, 260);
        p.quadTo(10, 12, 18.5f, 17);
        c.drawPath(p, paint);
    }

    /** Cloud body; dy shifts it up for icons with something underneath. */
    private void cloud(Canvas c, float dy, int color) {
        paint.setColor(color);
        Path p = new Path();
        p.moveTo(7, 19 + dy);
        p.lineTo(17.5f, 19 + dy);
        p.arcTo(new RectF(13, 10 + dy, 22, 19 + dy), 90, -180);
        p.arcTo(new RectF(6, 6 + dy, 17, 17 + dy), -20, -150);
        p.arcTo(new RectF(2, 12 + dy, 9, 19 + dy), 270, -180);
        p.close();
        c.drawPath(p, paint);
    }
}
