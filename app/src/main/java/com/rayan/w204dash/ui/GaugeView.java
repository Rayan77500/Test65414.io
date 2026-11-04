package com.rayan.w204dash.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Compteur circulaire (vitesse, regime) : arc 240 degres, valeur au centre. */
public class GaugeView extends View {

    private final String label;
    private final String unit;
    private final float max;
    private final float redFrom;
    private final int majorStep;
    private float value = Float.NaN;
    private float shown = 0f;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    public GaugeView(Context c, String label, String unit, float max, float redFrom, int majorStep) {
        super(c);
        this.label = label;
        this.unit = unit;
        this.max = max;
        this.redFrom = redFrom;
        this.majorStep = majorStep;
    }

    public void setValue(float v) {
        if (Float.isNaN(v) && Float.isNaN(value)) return;
        if (!Float.isNaN(v) && Math.abs(v - value) < 0.5f) return;
        value = v;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float size = Math.min(w, h * 1.08f);
        float cx = w / 2, cy = h * 0.52f;
        float r = size * 0.42f;
        float sw = size * 0.045f;
        oval.set(cx - r, cy - r, cx + r, cy + r);
        float start = 150, sweep = 240;

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(sw);
        p.setColor(0x22FFFFFF);
        c.drawArc(oval, start, sweep, false, p);
        if (redFrom > 0 && redFrom < max) {
            p.setColor(Theme.withAlpha(Theme.BAD, 0x70));
            float a0 = start + sweep * redFrom / max;
            c.drawArc(oval, a0, start + sweep - a0, false, p);
        }
        // graduations
        p.setStrokeWidth(Math.max(1, sw * 0.22f));
        p.setColor(0x66FFFFFF);
        int steps = (int) (max / majorStep);
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(start + sweep * i * majorStep / max);
            float r1 = r - sw * 1.3f, r2 = r - sw * 2.3f;
            c.drawLine(cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1,
                    cx + (float) Math.cos(a) * r2, cy + (float) Math.sin(a) * r2, p);
        }
        boolean known = !Float.isNaN(value);
        float target = known ? Math.max(0, Math.min(max, value)) : 0;
        // animation legere vers la valeur
        shown += (target - shown) * 0.35f;
        if (Math.abs(target - shown) > max * 0.002f) postInvalidateDelayed(33);
        else shown = target;
        if (known) {
            p.setStrokeWidth(sw);
            p.setColor(value >= redFrom && redFrom > 0 ? Theme.BAD : Theme.accent);
            c.drawArc(oval, start, Math.max(0.5f, sweep * shown / max), false, p);
        }
        // textes
        p.setStyle(Paint.Style.FILL);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Ui.LIGHT);
        p.setColor(known ? Theme.text : Theme.text3);
        p.setTextSize(size * 0.26f);
        c.drawText(known ? String.valueOf(Math.round(value)) : "--", cx, cy + size * 0.08f, p);
        p.setTypeface(Ui.REGULAR);
        p.setTextSize(size * 0.075f);
        p.setColor(Theme.text2);
        c.drawText(unit, cx, cy + size * 0.20f, p);
        p.setTypeface(Ui.MEDIUM);
        p.setTextSize(size * 0.07f);
        c.drawText(label.toUpperCase(java.util.Locale.FRANCE), cx, cy + r * 0.98f, p);
    }
}
