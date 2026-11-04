package com.rayan.w204dash.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Choix exclusif sous forme de gros boutons cote a cote. */
public class Segmented extends LinearLayout {

    public interface OnSelect {
        void onSelect(int index);
    }

    private final TextView[] items;
    private int selected = -1;
    private OnSelect listener;

    public Segmented(Context c, String[] labels, int initial, OnSelect l) {
        super(c);
        setOrientation(HORIZONTAL);
        setBaselineAligned(false);
        listener = null;
        items = new TextView[labels.length];
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            TextView t = Ui.text(c, labels[i], 16, Theme.text, Ui.MEDIUM);
            t.setGravity(Gravity.CENTER);
            t.setMaxLines(2);
            t.setMinHeight(Ui.dp(54));
            t.setPadding(Ui.dp(10), Ui.dp(6), Ui.dp(10), Ui.dp(6));
            t.setBackgroundDrawable(Ui.pressable(14, 0));
            t.setClickable(true);
            t.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx, true);
                }
            });
            LayoutParams lp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.leftMargin = Ui.dp(8);
            addView(t, lp);
            items[i] = t;
        }
        select(initial, false);
        listener = l;
    }

    public void select(int i, boolean notify) {
        if (i < 0 || i >= items.length) return;
        boolean changed = i != selected;
        selected = i;
        for (int k = 0; k < items.length; k++) {
            items[k].setSelected(k == i);
            items[k].setTextColor(k == i ? Theme.text : Theme.text2);
        }
        if (notify && changed && listener != null) listener.onSelect(i);
    }

    public int selected() {
        return selected;
    }
}
