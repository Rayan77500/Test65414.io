package com.rayan.w204dash;

import android.content.Context;
import android.content.SharedPreferences;

/** Reglages de l'application (memorises sur l'appareil). */
public final class Prefs {

    public static final String SRC_DEMO = "demo";
    public static final String SRC_OBD = "obd";
    public static final String SRC_CAN = "can";
    public static final String SRC_OBD_CAN = "obd_can";

    public static final String GEAR_MANUAL = "manual";
    public static final String GEAR_AUTO = "auto";

    public static final String THEME_NIGHT = "night";
    public static final String THEME_BLACK = "black";
    public static final String THEME_GRAPHITE = "graphite";

    /** Gris palladium metallise (couleur par defaut). */
    public static final int DEFAULT_CAR_COLOR = 0xFF8C9196;
    public static final int DEFAULT_ACCENT = 0xFF4FC3F7;

    private final SharedPreferences sp;

    public Prefs(Context ctx) {
        sp = ctx.getSharedPreferences("w204dash", Context.MODE_PRIVATE);
    }

    public int carColor() {
        return sp.getInt("car_color", DEFAULT_CAR_COLOR);
    }

    public String carColorName() {
        return sp.getString("car_color_name", "Gris palladium métallisé");
    }

    public void setCarColor(int color, String name) {
        sp.edit().putInt("car_color", color).putString("car_color_name", name).apply();
    }

    public String options() {
        return sp.getString("options", "");
    }

    public void setOptions(String s) {
        sp.edit().putString("options", s).apply();
    }

    public String gearbox() {
        return sp.getString("gearbox", GEAR_MANUAL);
    }

    public void setGearbox(String g) {
        sp.edit().putString("gearbox", g).apply();
    }

    public String source() {
        return sp.getString("source", SRC_DEMO);
    }

    public void setSource(String s) {
        sp.edit().putString("source", s).apply();
    }

    public String obdAddress() {
        return sp.getString("obd_addr", "");
    }

    public String obdName() {
        return sp.getString("obd_name", "");
    }

    public void setObdDevice(String addr, String name) {
        sp.edit().putString("obd_addr", addr).putString("obd_name", name).apply();
    }

    public String theme() {
        return sp.getString("theme", THEME_NIGHT);
    }

    public void setTheme(String t) {
        sp.edit().putString("theme", t).apply();
    }

    public int accent() {
        return sp.getInt("accent", DEFAULT_ACCENT);
    }

    public void setAccent(int c) {
        sp.edit().putInt("accent", c).apply();
    }

    public boolean autoRotate() {
        return sp.getBoolean("auto_rotate", false);
    }

    public void setAutoRotate(boolean b) {
        sp.edit().putBoolean("auto_rotate", b).apply();
    }

    public boolean radarBeep() {
        return sp.getBoolean("radar_beep", false);
    }

    public void setRadarBeep(boolean b) {
        sp.edit().putBoolean("radar_beep", b).apply();
    }

    /** Actions de broadcast supplementaires a ecouter (une par ligne). */
    public String customCanActions() {
        return sp.getString("can_actions", "");
    }

    public void setCustomCanActions(String s) {
        sp.edit().putString("can_actions", s).apply();
    }

    /** Rotation manuelle du modele 3D (0, 90, 180, 270 degres). */
    public int modelRotation() {
        return sp.getInt("model_rot", 0);
    }

    public void setModelRotation(int deg) {
        sp.edit().putInt("model_rot", ((deg % 360) + 360) % 360).apply();
    }

    /** Kilometrage saisi a la main (utilise si la source ne le fournit pas). */
    public int manualKm() {
        return sp.getInt("manual_km", -1);
    }

    public void setManualKm(int km) {
        sp.edit().putInt("manual_km", km).apply();
    }
}
