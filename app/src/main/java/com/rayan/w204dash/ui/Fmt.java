package com.rayan.w204dash.ui;

import com.rayan.w204dash.data.VehicleData;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Mise en forme des valeurs ("--" si inconnue). */
public final class Fmt {

    public static final String NA = "--";
    private static final DecimalFormatSymbols FR = new DecimalFormatSymbols(Locale.FRANCE);

    private Fmt() {
    }

    public static String num(float v, int decimals) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return NA;
        StringBuilder p = new StringBuilder("#,##0");
        if (decimals > 0) {
            p.append('.');
            for (int i = 0; i < decimals; i++) p.append('0');
        }
        DecimalFormat df = new DecimalFormat(p.toString(), FR);
        // espace insecable fine -> espace normal (polices anciennes)
        return df.format(v).replace(' ', ' ').replace(' ', ' ');
    }

    public static String temp(float v) {
        return Float.isNaN(v) ? NA : num(v, 1) + " °C";
    }

    public static String volt(float v) {
        return Float.isNaN(v) ? NA : num(v, 1) + " V";
    }

    public static String km(float v) {
        return Float.isNaN(v) ? NA : num(v, 0) + " km";
    }

    public static String pct(float v) {
        return Float.isNaN(v) ? NA : num(v, 0) + " %";
    }

    /** Litres restants (reservoir W204 : 66 L). */
    public static String liters(float pct) {
        return Float.isNaN(pct) ? NA : "≈ " + num(pct * 66f / 100f, 0) + " L";
    }

    public static String distance(int cm) {
        if (cm < 0) return NA;
        if (cm >= 100) return num(cm / 100f, 2) + " m";
        return cm + " cm";
    }

    public static String state(int v, String yes, String no) {
        return v == VehicleData.UNKNOWN ? NA : (v == 1 ? yes : no);
    }
}
