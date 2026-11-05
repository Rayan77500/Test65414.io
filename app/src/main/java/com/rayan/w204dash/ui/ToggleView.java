package com.rayan.w204dash.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Interrupteur on/off dessine (identique sur toutes les versions d'Android). */
public class ToggleView extends View {

    public interface OnToggle {
        void onToggle(boolean on);
    }

    private boolean on;
    private OnToggle listener;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public ToggleView(Context c, boolean initial, OnToggle l) {
        super(c);
        on = initial;
        listener = l;
        setClickable(true);
        setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                on = !on;
                invalidate();
                if (listener != null) listener.onToggle(on);
            }
        });
    }

    public boolean isOn() {
        return on;
    }

    @Override
    protected void onMeasure(int wm, int hm) {
        setMeasuredDimension(Ui.dp(68), Ui.dp(40));
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float th = h * 0.62f;
        r.set(h * 0.1f, (h - th) / 2, w - h * 0.1f, (h + th) / 2);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(on ? Theme.withAlpha(Theme.accent, 0x90) : 0x33FFFFFF);
        c.drawRoundRect(r, th / 2, th / 2, paint);
        float cx = on ? w - h / 2 : h / 2;
        paint.setColor(on ? Theme.accent : 0xFFB0B8C4);
        c.drawCircle(cx, h / 2, h * 0.38f, paint);
    }
}
