package tv.local.ytredirect;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/**
 * Small line icons for Settings, on a 24-unit grid like the designs. Dark when its row is
 * focused (white), light otherwise: add it with duplicateParentState.
 */
final class LineIcon extends View {
    static final int IMAGE = 0, CALENDAR = 1, HOME = 2, TILES = 3, REMOTE = 4, TV = 5,
            CHEVRON = 6, CHECK = 7, EXTERNAL = 8, RESET = 9;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int kind;

    LineIcon(Context c, int kind) {
        super(c);
        this.kind = kind;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        setDuplicateParentStateEnabled(true);
    }

    @Override
    protected void drawableStateChanged() {
        super.drawableStateChanged();
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        boolean focused = false;
        for (int s : getDrawableState()) if (s == android.R.attr.state_focused) focused = true;
        paint.setColor(focused ? Ui.INK_DARK : Ui.INK);
        float s = Math.min(getWidth(), getHeight()) / 24f;
        c.save();
        c.translate((getWidth() - 24 * s) / 2, (getHeight() - 24 * s) / 2);
        c.scale(s, s);
        paint.setStrokeWidth(kind == CHEVRON || kind == CHECK ? 2.4f : 1.8f);
        Path p = new Path();
        switch (kind) {
            case IMAGE:
                c.drawRoundRect(new RectF(3, 3, 21, 21), 2, 2, paint);
                c.drawCircle(9, 9, 2, paint);
                p.moveTo(21, 15);
                p.lineTo(16, 10);
                p.lineTo(6, 21);
                break;
            case CALENDAR:
                c.drawRoundRect(new RectF(3, 4, 21, 22), 2, 2, paint);
                c.drawLine(16, 2, 16, 6, paint);
                c.drawLine(8, 2, 8, 6, paint);
                c.drawLine(3, 10, 21, 10, paint);
                break;
            case HOME:
                p.moveTo(3, 10);
                p.lineTo(12, 3);
                p.lineTo(21, 10);
                p.lineTo(21, 21);
                p.lineTo(15, 21);
                p.lineTo(15, 15);
                p.lineTo(9, 15);
                p.lineTo(9, 21);
                p.lineTo(3, 21);
                p.close();
                break;
            case TILES:
                for (int x = 3; x <= 14; x += 11)
                    for (int y = 3; y <= 14; y += 11) c.drawRoundRect(new RectF(x, y, x + 7, y + 7), 1.5f, 1.5f, paint);
                break;
            case REMOTE:
                c.drawRoundRect(new RectF(7, 2, 17, 22), 3, 3, paint);
                c.drawCircle(12, 7.5f, 2, paint);
                paint.setStyle(Paint.Style.FILL);
                for (float x = 10; x <= 14; x += 4)
                    for (float y = 13; y <= 16.5f; y += 3.5f) c.drawCircle(x, y, 0.9f, paint);
                paint.setStyle(Paint.Style.STROKE);
                break;
            case TV:
                c.drawRoundRect(new RectF(2, 5, 22, 18), 2, 2, paint);
                c.drawLine(8, 22, 16, 22, paint);
                c.drawLine(12, 18, 12, 22, paint);
                break;
            case CHEVRON:
                p.moveTo(9, 18);
                p.lineTo(15, 12);
                p.lineTo(9, 6);
                break;
            case CHECK:
                p.moveTo(20, 6);
                p.lineTo(9, 17);
                p.lineTo(4, 12);
                break;
            case EXTERNAL:
                p.moveTo(15, 3);
                p.lineTo(21, 3);
                p.lineTo(21, 9);
                p.moveTo(10, 14);
                p.lineTo(21, 3);
                p.moveTo(18, 13);
                p.lineTo(18, 19);
                p.quadTo(18, 21, 16, 21);
                p.lineTo(5, 21);
                p.quadTo(3, 21, 3, 19);
                p.lineTo(3, 8);
                p.quadTo(3, 6, 5, 6);
                p.lineTo(11, 6);
                break;
            case RESET:
                p.addArc(new RectF(3, 3, 21, 21), 215, 285);
                p.moveTo(3, 3);
                p.lineTo(3, 8);
                p.lineTo(8, 8);
                break;
            default:
                break;
        }
        c.drawPath(p, paint);
        c.restore();
    }
}
