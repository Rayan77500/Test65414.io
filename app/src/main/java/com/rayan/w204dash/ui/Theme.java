package com.rayan.w204dash.ui;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;

import com.rayan.w204dash.Prefs;

/** Couleurs du theme courant (sombre, effet verre depoli). */
public final class Theme {

    public static int bgTop, bgBottom, glBg;
    public static int panel, panelPressed, stroke, divider;
    public static int text = 0xFFF2F5F8;
    public static int text2 = 0xFFA4AFBD;
    public static int text3 = 0xFF6C7686;
    public static int accent = Prefs.DEFAULT_ACCENT;
    public static final int OK = 0xFF46D38A;
    public static final int WARN = 0xFFFFB547;
    public static final int BAD = 0xFFFF5A5F;
    public static final int UNKNOWN = 0xFF6C7686;

    /** Couleurs d'accent proposees (eclairage d'ambiance). */
    public static final int[] ACCENTS = {0xFF4FC3F7, 0xFF64FFDA, 0xFFFFB74D, 0xFFFF5252, 0xFFB388FF, 0xFFECEFF1};
    public static final String[] ACCENT_NAMES = {"Bleu glacier", "Turquoise", "Ambre", "Rouge", "Violet", "Blanc"};

    private Theme() {
    }

    public static void load(Prefs p) {
        accent = p.accent();
        String t = p.theme();
        if (Prefs.THEME_BLACK.equals(t)) {
            bgTop = 0xFF070708;
            bgBottom = 0xFF000000;
            glBg = 0xFF040405;
            panel = 0x14FFFFFF;
        } else if (Prefs.THEME_GRAPHITE.equals(t)) {
            bgTop = 0xFF24272C;
            bgBottom = 0xFF111214;
            glBg = 0xFF1A1C20;
            panel = 0x17FFFFFF;
        } else {
            bgTop = 0xFF111A2B;
            bgBottom = 0xFF05080E;
            glBg = 0xFF0A1019;
            panel = 0x16FFFFFF;
        }
        panelPressed = 0x30FFFFFF;
        stroke = 0x24FFFFFF;
        divider = 0x18FFFFFF;
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    /** Fond d'ecran : degrade + deux halos de la couleur d'accent (dessine une fois). */
    public static Drawable background() {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[]{bgTop, bgBottom});
        GradientDrawable glow1 = new GradientDrawable();
        glow1.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow1.setColors(new int[]{withAlpha(accent, 0x26), withAlpha(accent, 0)});
        glow1.setGradientCenter(0.15f, 0.0f);
        glow1.setGradientRadius(Ui.dp(520));
        GradientDrawable glow2 = new GradientDrawable();
        glow2.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow2.setColors(new int[]{withAlpha(accent, 0x14), withAlpha(accent, 0)});
        glow2.setGradientCenter(0.95f, 1.0f);
        glow2.setGradientRadius(Ui.dp(420));
        return new LayerDrawable(new Drawable[]{base, glow1, glow2});
    }
}
