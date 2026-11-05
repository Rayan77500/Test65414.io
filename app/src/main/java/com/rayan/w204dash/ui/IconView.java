package com.rayan.w204dash.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** Affiche une icone de {@link Icons}. */
public class IconView extends View {

    private int icon;
    private int color;

    public IconView(Context c, int icon, int color) {
        super(c);
        this.icon = icon;
        this.color = color;
    }

    public void setIcon(int icon) {
        if (this.icon != icon) {
            this.icon = icon;
            invalidate();
        }
    }

    public void setColor(int color) {
        if (this.color != color) {
            this.color = color;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float s = Math.min(getWidth() - getPaddingLeft() - getPaddingRight(),
                getHeight() - getPaddingTop() - getPaddingBottom());
        if (s <= 0) return;
        canvas.save();
        canvas.translate(getPaddingLeft() + (getWidth() - getPaddingLeft() - getPaddingRight() - s) / 2f,
                getPaddingTop() + (getHeight() - getPaddingTop() - getPaddingBottom() - s) / 2f);
        Icons.draw(canvas, icon, s, color);
        canvas.restore();
    }
}
