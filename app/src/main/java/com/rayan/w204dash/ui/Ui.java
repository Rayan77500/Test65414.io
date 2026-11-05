package com.rayan.w204dash.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fabrique de composants d'interface : tailles adaptees a l'ecran (lisibles
 * de loin), panneaux "verre depoli", gros boutons.
 */
public final class Ui {

    /** Facteur d'echelle global (ecrans 7" 1024x600 jusqu'aux 10" 1280x720). */
    public static float scale = 1f;
    private static float density = 1f;

    public static Typeface LIGHT = Typeface.DEFAULT;
    public static Typeface REGULAR = Typeface.DEFAULT;
    public static Typeface MEDIUM = Typeface.DEFAULT_BOLD;
    public static Typeface CONDENSED = Typeface.DEFAULT;

    private Ui() {
    }

    public static void init(Activity a) {
        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        density = dm.density;
        float wDp = Math.max(dm.widthPixels, dm.heightPixels) / dm.density;
        float hDp = Math.min(dm.widthPixels, dm.heightPixels) / dm.density;
        scale = Math.max(0.70f, Math.min(1.45f, Math.min(wDp / 1000f, hDp / 560f)));
        LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
        REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
        Typeface med = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        MEDIUM = med == null || med.equals(Typeface.DEFAULT) ? Typeface.DEFAULT_BOLD : med;
        CONDENSED = Typeface.create("sans-serif-condensed", Typeface.NORMAL);
    }

    public static int dp(float v) {
        return Math.round(v * density * scale);
    }

    public static float px(float v) {
        return v * density * scale;
    }

    // ------------------------------------------------------------ textes
    public static TextView text(Context c, String s, float sizeDp, int color, Typeface tf) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(sizeDp));
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setIncludeFontPadding(false);
        return t;
    }

    public static TextView title(Context c, String s) {
        return text(c, s, 22, Theme.text, MEDIUM);
    }

    public static TextView label(Context c, String s) {
        return text(c, s, 16, Theme.text2, REGULAR);
    }

    public static TextView value(Context c, String s, float size) {
        TextView t = text(c, s, size, Theme.text, LIGHT);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    // ------------------------------------------------------------ fonds
    public static GradientDrawable rounded(int fill, int strokeColor, float radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(px(radiusDp));
        if (strokeColor != 0) d.setStroke(Math.max(1, Math.round(density)), strokeColor);
        return d;
    }

    /** Panneau "verre depoli" : voile clair translucide + liseré. */
    public static Drawable glass(float radiusDp) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{Theme.withAlpha(0xFFFFFFFF, (Theme.panel >>> 24) + 10), Theme.panel});
        d.setCornerRadius(px(radiusDp));
        d.setStroke(Math.max(1, Math.round(density)), Theme.stroke);
        return d;
    }

    /** Fond cliquable (s'eclaircit a l'appui). */
    public static Drawable pressable(float radiusDp, int normalFill) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, rounded(Theme.panelPressed, Theme.stroke, radiusDp));
        s.addState(new int[]{android.R.attr.state_selected}, rounded(Theme.withAlpha(Theme.accent, 0x40),
                Theme.withAlpha(Theme.accent, 0xA0), radiusDp));
        s.addState(new int[]{}, normalFill == 0 ? glass(radiusDp) : rounded(normalFill, Theme.stroke, radiusDp));
        return s;
    }

    // ------------------------------------------------------------ composants
    public static LinearLayout card(Context c, String title) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundDrawable(glass(20));
        int p = dp(16);
        l.setPadding(p, p, p, p);
        if (title != null) {
            TextView t = text(c, title.toUpperCase(java.util.Locale.FRANCE), 13, Theme.text2, MEDIUM);
            l.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            View gap = new View(c);
            l.addView(gap, new LinearLayout.LayoutParams(1, dp(10)));
        }
        return l;
    }

    /** Gros bouton avec icone optionnelle (zone tactile >= 56 dp). */
    public static LinearLayout button(Context c, int icon, String label, View.OnClickListener cl) {
        LinearLayout b = new LinearLayout(c);
        b.setOrientation(LinearLayout.HORIZONTAL);
        b.setGravity(Gravity.CENTER);
        b.setMinimumHeight(dp(56));
        b.setPadding(dp(18), dp(8), dp(18), dp(8));
        b.setBackgroundDrawable(pressable(16, 0));
        b.setClickable(true);
        b.setOnClickListener(cl);
        if (icon != 0) {
            IconView iv = new IconView(c, icon, Theme.accent);
            b.addView(iv, new LinearLayout.LayoutParams(dp(26), dp(26)));
        }
        if (label != null) {
            TextView t = text(c, label, 17, Theme.text, MEDIUM);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (icon != 0) lp.leftMargin = dp(10);
            b.addView(t, lp);
        }
        return b;
    }

    /** Pastille d'etat (ex. "OUVERT" en ambre). */
    public static TextView pill(Context c, String s, int color) {
        TextView t = text(c, s, 14, color, MEDIUM);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(6), dp(12), dp(6));
        t.setBackgroundDrawable(rounded(Theme.withAlpha(color, 0x2A), Theme.withAlpha(color, 0x70), 40));
        return t;
    }

    public static void setPill(TextView t, String s, int color) {
        t.setText(s);
        t.setTextColor(color);
        t.setBackgroundDrawable(rounded(Theme.withAlpha(color, 0x2A), Theme.withAlpha(color, 0x70), 40));
    }

    public static View space(Context c, int wDp, int hDp) {
        View v = new View(c);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));
        return v;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(Theme.divider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.max(1, Math.round(density)));
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(8);
        v.setLayoutParams(lp);
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams weight(float w, boolean horizontal) {
        return horizontal ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, w)
                : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, w);
    }

    public static LinearLayout.LayoutParams margins(LinearLayout.LayoutParams lp, int l, int t, int r, int b) {
        lp.setMargins(dp(l), dp(t), dp(r), dp(b));
        return lp;
    }

    public static FrameLayout.LayoutParams frame(int w, int h, int gravity) {
        return new FrameLayout.LayoutParams(w, h, gravity);
    }

    public static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setBaselineAligned(false);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
}
