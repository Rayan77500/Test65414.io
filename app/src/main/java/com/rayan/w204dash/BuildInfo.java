package com.rayan.w204dash;

/** Informations de version affichees dans Reglages et Diagnostic. */
public final class BuildInfo {
    public static final String VERSION = "1.2";
    public static final String CAR = "Mercedes-Benz Classe C berline W204 - C 180 CDI (OM 651.913, 120 ch)";

    private BuildInfo() {
    }

    private static final String[] NAMES = {"", "1.0", "1.1", "1.5", "1.6", "2.0", "2.0.1", "2.1", "2.2", "2.3",
            "2.3.3", "3.0", "3.1", "3.2", "4.0", "4.0.3", "4.1", "4.2", "4.3", "4.4", "4.4W", "5.0", "5.1", "6",
            "7.0", "7.1", "8.0", "8.1", "9", "10", "11", "12", "12L", "13", "14", "15", "16"};

    /** Version Android reelle d'apres l'API (certains ecrans affichent une version "maquillee"). */
    public static String androidVersion(int sdk, String release) {
        String real = sdk > 0 && sdk < NAMES.length ? NAMES[sdk] : null;
        if (real == null) return "Android " + release + " (API " + sdk + ")";
        String s = "Android " + real + " (API " + sdk + ")";
        String r = release == null ? "" : release.trim();
        if (r.length() > 0 && !real.startsWith(r) && !r.startsWith(real)) {
            s += " — l'écran annonce « " + r + " »";
        }
        return s;
    }

    public static String androidVersion() {
        return androidVersion(android.os.Build.VERSION.SDK_INT, android.os.Build.VERSION.RELEASE);
    }
}
