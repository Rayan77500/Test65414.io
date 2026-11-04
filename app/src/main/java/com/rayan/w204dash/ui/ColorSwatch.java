package com.rayan.w204dash.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

/** Pastille de couleur ronde (selection de la teinte de carrosserie / d'accent). */
public class ColorSwatch extends View {

    private int color;
    private boolean selected2;
    private boolean plus;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

    public ColorSwatch(Context c, int color, boolean plus) {
        super(c);
        this.color = color;
        this.plus = plus;
        setClickable(true);
    }

    public void setColor(int c) {
        color = c;
        invalidate();
    }

    public void setChecked(boolean s) {
        selected2 = s;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float r = Math.min(w, h) / 2f;
        float cx = w / 2, cy = h / 2;
        p.setStyle(Paint.Style.FILL);
        if (plus) {
            p.setColor(0x22FFFFFF);
            c.drawCircle(cx, cy, r * 0.78f, p);
            p.setColor(Theme.text);
            p.setStrokeWidth(r * 0.1f);
            p.setStyle(Paint.Style.STROKE);
            c.drawLine(cx - r * 0.3f, cy, cx + r * 0.3f, cy, p);
            c.drawLine(cx, cy - r * 0.3f, cx, cy + r * 0.3f, p);
        } else {
            p.setColor(color);
            c.drawCircle(cx, cy, r * 0.78f, p);
            // reflet "metallise"
            p.setColor(0x30FFFFFF);
            c.drawCircle(cx - r * 0.2f, cy - r * 0.25f, r * 0.32f, p);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2, r * 0.09f));
        p.setColor(selected2 ? Theme.accent : 0x33FFFFFF);
        c.drawCircle(cx, cy, r * (selected2 ? 0.93f : 0.80f), p);
    }
}
