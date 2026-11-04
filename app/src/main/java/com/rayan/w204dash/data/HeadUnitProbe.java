package com.rayan.w204dash.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ApplicationInfo;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.Files;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Detection du boitier CAN de l'ecran : plateforme (FYT, MTC, TopWay...),
 * applications systeme liees au vehicule, services, fournisseurs de contenu,
 * ports serie, et chaines d'actions de broadcast trouvees dans le code des
 * applications du boitier (pour les ecouter ensuite).
 */
public final class HeadUnitProbe {

    private static final String TAG = "CAN";

    /** Mots qui, dans un nom de paquet, signalent une appli liee au vehicule. */
    private static final Set<String> PKG_TOKENS = new HashSet<String>(Arrays.asList(
            "can", "canbus", "canbox", "syu", "microntek", "hct", "mcu", "car", "carinfo", "carsetting",
            "carsettings", "carservice", "vehicle", "autochips", "nwd", "tw", "ts", "fyt", "topway", "zxw",
            "radar", "door", "climate", "hvac", "aircon", "bonovo", "joying", "kswin", "carlink", "decoder",
            "canframe", "carstatus", "carstate", "obd"));

    private static final String[] ACTION_WORDS = {"door", "canbus", ".can.", "_can_", "can_", "radar", "belt",
            "aircon", "air_con", "climate", "hvac", "outtemp", "out_temp", "mileage", "odometer", "carinfo",
            "car_info", "vehicle", "canbox", "mcu", "reverse", "backcar", "wash"};

    private static final Pattern ACTION_RE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+){2,}");
    private static final Pattern CLASS_LIKE = Pattern.compile(".*\\.[A-Z][a-z0-9]+[A-Za-z0-9]*$");

    /** Resultat de la derniere detection (pour l'ecran Diagnostic). */
    public static final class Report {
        public final List<String> lines = new ArrayList<String>();
        public final LinkedHashSet<String> actions = new LinkedHashSet<String>();
        public final List<String> packages = new ArrayList<String>();
        public String platform = "inconnue";
        public long durationMs;

        void add(String s) {
            lines.add(s);
        }
    }

    private static volatile Report last;

    private HeadUnitProbe() {
    }

    public static Report last() {
        return last;
    }

    /** Lance la detection complete (a appeler hors du fil principal, ~2 a 20 s). */
    public static Report run(Context ctx) {
        long t0 = System.currentTimeMillis();
        Report r = new Report();
        DiagLog.i(TAG, "Détection du boîtier CAN de l'écran…");
        try {
            device(r);
        } catch (Throwable t) {
            r.add("Erreur infos appareil : " + t);
        }
        try {
            props(r);
        } catch (Throwable t) {
            r.add("Erreur propriétés : " + t);
        }
        List<PackageInfo> pkgs = new ArrayList<PackageInfo>();
        try {
            pkgs = packages(ctx, r);
        } catch (Throwable t) {
            r.add("Erreur liste des applis : " + t);
        }
        try {
            platform(ctx, r);
        } catch (Throwable t) {
            r.add("Erreur plateforme : " + t);
        }
        try {
            scanActions(pkgs, r, t0);
        } catch (Throwable t) {
            r.add("Erreur analyse des broadcasts : " + t);
        }
        try {
            settings(ctx, r);
        } catch (Throwable t) {
            r.add("Réglages système illisibles : " + t.getClass().getSimpleName());
        }
        try {
            serial(r);
        } catch (Throwable t) {
            r.add("Erreur ports série : " + t);
        }
        try {
            bindKnown(ctx, r);
        } catch (Throwable t) {
            r.add("Erreur services : " + t);
        }
        r.durationMs = System.currentTimeMillis() - t0;
        r.add("");
        r.add("Détection terminée en " + (r.durationMs / 100) / 10.0 + " s : plateforme " + r.platform + ", "
                + r.packages.size() + " appli(s) liée(s) au véhicule, " + r.actions.size()
                + " broadcast(s) candidat(s) mis sur écoute.");
        DiagLog.i(TAG, "Plateforme : " + r.platform + " ; " + r.actions.size() + " broadcast(s) candidat(s)");
        last = r;
        return r;
    }

    private static void device(Report r) {
        r.add("== Appareil ==");
        r.add("Modèle : " + Build.MANUFACTURER + " " + Build.MODEL + " (" + Build.BRAND + " / " + Build.DEVICE + ")");
        r.add("Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        r.add("Carte : " + Build.BOARD + " / " + Build.HARDWARE);
        r.add("Firmware : " + Build.DISPLAY);
        r.add("Empreinte : " + Build.FINGERPRINT);
    }

    private static void props(Report r) {
        r.add("");
        r.add("== Propriétés système intéressantes ==");
        Pattern kw = Pattern.compile("(?i).*(can|car|mcu|fyt|syu|mtc|hct|microntek|ts10|ts18|uis7862|vehicle|platform|"
                + "customer|factory|ac8227|sp9863|px[56]|rk3|8581|8227).*");
        Process p = null;
        int n = 0;
        try {
            p = Runtime.getRuntime().exec("getprop");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            int total = 0;
            while ((line = br.readLine()) != null && total < 3000) {
                total++;
                if (kw.matcher(line).matches() && !line.contains("ril.") && !line.contains("dalvik.")) {
                    if (n < 60) r.add(line.trim());
                    n++;
                }
            }
            br.close();
        } catch (Exception e) {
            r.add("getprop indisponible : " + e.getMessage());
        } finally {
            if (p != null) p.destroy();
        }
        if (n == 0) r.add("(aucune)");
        else if (n > 60) r.add("… (" + (n - 60) + " autres)");
    }

    static boolean isVehiclePackage(String name) {
        String lower = name.toLowerCase(Locale.US);
        if (lower.contains("canbus") || lower.contains("carinfo") || lower.contains("microntek")) return true;
        if (lower.startsWith("com.google.") || lower.contains("carrier") || lower.contains("cards")) return false;
        String[] toks = lower.split("[._]");
        for (String t : toks) {
            if (PKG_TOKENS.contains(t)) return true;
        }
        return false;
    }

    private static List<PackageInfo> packages(Context ctx, Report r) {
        r.add("");
        r.add("== Applications liées au véhicule ==");
        PackageManager pm = ctx.getPackageManager();
        List<PackageInfo> all = pm.getInstalledPackages(0);
        r.add(all.size() + " applications installées au total");
        List<PackageInfo> out = new ArrayList<PackageInfo>();
        int flags = PackageManager.GET_SERVICES | PackageManager.GET_RECEIVERS | PackageManager.GET_PROVIDERS;
        for (PackageInfo pi : all) {
            if (pi.packageName.equals(ctx.getPackageName()) || !isVehiclePackage(pi.packageName)) continue;
            if (out.size() >= 40) break;
            PackageInfo full;
            try {
                full = pm.getPackageInfo(pi.packageName, flags);
            } catch (Throwable t) {
                full = pi;
            }
            out.add(full);
            r.packages.add(full.packageName);
            boolean sys = full.applicationInfo != null
                    && (full.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            int ns = full.services == null ? 0 : full.services.length;
            int nr = full.receivers == null ? 0 : full.receivers.length;
            int np = full.providers == null ? 0 : full.providers.length;
            r.add("• " + full.packageName + (sys ? " (système)" : "") + " v" + full.versionName
                    + " : " + ns + " service(s), " + nr + " récepteur(s), " + np + " fournisseur(s)");
            list(r, "   service", full.services);
            list(r, "   récepteur", full.receivers);
            if (full.providers != null) {
                for (int i = 0; i < Math.min(6, full.providers.length); i++) {
                    ProviderInfo p = full.providers[i];
                    r.add("   fournisseur : content://" + p.authority + (p.exported ? " (public)" : ""));
                }
            }
        }
        if (out.isEmpty()) r.add("(aucune trouvée)");
        return out;
    }

    private static void list(Report r, String label, ComponentInfo[] items) {
        if (items == null) return;
        int shown = 0;
        for (ComponentInfo ci : items) {
            if (shown >= 8) {
                r.add("   … " + (items.length - shown) + " autre(s)");
                break;
            }
            r.add(label + " : " + ci.name + (ci.exported ? " (public)" : ""));
            shown++;
        }
    }

    private static boolean hasPkgPrefix(List<String> pkgs, String prefix) {
        for (String p : pkgs) {
            if (p.startsWith(prefix)) return true;
        }
        return false;
    }

    private static void platform(Context ctx, Report r) {
        r.add("");
        r.add("== Plateforme ==");
        List<String> p = r.packages;
        String plat;
        if (hasPkgPrefix(p, "com.syu.")) plat = "FYT (SYU) - com.syu.ms / com.syu.canbus";
        else if (hasPkgPrefix(p, "com.microntek.") || hasPkgPrefix(p, "android.microntek.")) plat = "MTC/MTCD/MTCE (Microntek)";
        else if (hasPkgPrefix(p, "com.hct.")) plat = "HCT";
        else if (hasPkgPrefix(p, "com.tw.") || hasPkgPrefix(p, "com.ts.")) plat = "TopWay (TS10/TS18…)";
        else if (hasPkgPrefix(p, "com.nwd.")) plat = "NWD";
        else if (hasPkgPrefix(p, "com.zxw.")) plat = "ZXW";
        else if (hasPkgPrefix(p, "com.autochips.")) plat = "Autochips (AC8227…)";
        else plat = p.isEmpty() ? "inconnue (aucune appli CAN reconnue)" : "non identifiée";
        boolean aaos = ctx.getPackageManager().hasSystemFeature("android.hardware.type.automotive");
        if (aaos) plat += " + Android Automotive";
        try {
            Class.forName("android.car.Car");
            r.add("Bibliothèque android.car présente");
        } catch (Throwable ignored) {
            // absente
        }
        r.platform = plat;
        r.add("Plateforme probable : " + plat);
    }

    private static void scanActions(List<PackageInfo> pkgs, Report r, long t0) {
        r.add("");
        r.add("== Broadcasts candidats (trouvés dans le code des applis du véhicule) ==");
        int before = r.actions.size();
        for (PackageInfo pi : pkgs) {
            if (System.currentTimeMillis() - t0 > 25000) {
                r.add("(analyse interrompue : temps limite atteint)");
                break;
            }
            ApplicationInfo ai = pi.applicationInfo;
            if (ai == null || ai.sourceDir == null) continue;
            Set<String> found = new LinkedHashSet<String>();
            File apk = new File(ai.sourceDir);
            scanApk(apk, found);
            // code pre-compile (odex/vdex) a cote de l'APK
            File oat = new File(apk.getParentFile(), "oat");
            File[] archs = oat.listFiles();
            if (archs != null) {
                for (File a : archs) {
                    File[] fs = a.listFiles();
                    if (fs == null) continue;
                    for (File f : fs) {
                        String n = f.getName();
                        if (n.endsWith(".odex") || n.endsWith(".vdex")) scanFile(f, found);
                    }
                }
            }
            if (!found.isEmpty()) {
                r.add("• " + pi.packageName + " : " + found.size() + " action(s)");
                int shown = 0;
                for (String s : found) {
                    if (shown++ < 25) r.add("   " + s);
                }
                if (found.size() > 25) r.add("   … " + (found.size() - 25) + " autre(s) (toutes écoutées)");
                for (String s : found) {
                    if (r.actions.size() < 400) r.actions.add(s);
                }
            }
        }
        if (r.actions.size() == before) r.add("(aucune chaîne d'action trouvée)");
    }

    private static void scanApk(File apk, Set<String> found) {
        if (!apk.canRead()) return;
        ZipFile zf = null;
        try {
            zf = new ZipFile(apk);
            Enumeration<? extends ZipEntry> en = zf.entries();
            while (en.hasMoreElements()) {
                ZipEntry ze = en.nextElement();
                String n = ze.getName();
                if (n.startsWith("classes") && n.endsWith(".dex") && ze.getSize() < 40L * 1024 * 1024) {
                    InputStream in = zf.getInputStream(ze);
                    scanStream(in, found);
                    in.close();
                }
            }
        } catch (Throwable ignored) {
            // APK illisible
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }

    private static void scanFile(File f, Set<String> found) {
        if (!f.canRead() || f.length() > 60L * 1024 * 1024) return;
        try {
            scanStream(new java.io.FileInputStream(f), found);
        } catch (Throwable ignored) {
            // ignore
        }
    }

    /** Equivalent simplifie de la commande "strings" + filtre sur les actions. */
    static void scanStream(InputStream in, Set<String> found) throws java.io.IOException {
        byte[] buf = new byte[65536];
        StringBuilder sb = new StringBuilder();
        int n;
        try {
            while ((n = in.read(buf)) > 0) {
                for (int i = 0; i < n; i++) {
                    int c = buf[i] & 0xFF;
                    boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                            || c == '.' || c == '_';
                    if (ok && sb.length() < 160) {
                        sb.append((char) c);
                    } else {
                        if (sb.length() >= 10) consider(sb.toString(), found);
                        sb.setLength(0);
                    }
                }
            }
            if (sb.length() >= 10) consider(sb.toString(), found);
        } finally {
            Files.closeQuietly(in);
        }
    }

    static void consider(String s, Set<String> found) {
        if (found.size() >= 200 || s.length() > 100) return;
        if (!ACTION_RE.matcher(s).matches()) return;
        String low = s.toLowerCase(Locale.US);
        boolean kw = false;
        for (String w : ACTION_WORDS) {
            if (low.contains(w)) {
                kw = true;
                break;
            }
        }
        if (!kw) return;
        if (CLASS_LIKE.matcher(s).matches()) return;
        if (low.endsWith(".java") || low.endsWith(".xml") || low.endsWith(".png") || low.endsWith(".so")
                || low.endsWith(".jpg") || low.endsWith(".json") || low.endsWith(".txt") || low.startsWith("android.support")
                || low.startsWith("androidx.") || low.startsWith("java.") || low.startsWith("kotlin")) return;
        // les noms d'actions contiennent en general un segment en MAJUSCULES ou "action"
        boolean actionish = low.contains("action") || s.matches(".*\\.[A-Z0-9_]{3,}$") || low.contains("broadcast")
                || low.contains("notify") || low.contains("change") || low.contains("update") || low.contains("status")
                || low.contains("state") || low.contains("info");
        if (actionish) found.add(s);
    }

    private static void settings(Context ctx, Report r) {
        r.add("");
        r.add("== Réglages système liés au véhicule ==");
        Cursor c = null;
        int n = 0;
        try {
            c = ctx.getContentResolver().query(Uri.parse("content://settings/system"),
                    new String[]{"name", "value"}, null, null, null);
            Pattern kw = Pattern.compile("(?i).*(can|door|radar|belt|car_|mcu|vehicle|reverse|backcar|temp).*");
            while (c != null && c.moveToNext() && n < 40) {
                String name = c.getString(0);
                if (name != null && kw.matcher(name).matches()) {
                    r.add(name + " = " + c.getString(1));
                    n++;
                }
            }
        } finally {
            if (c != null) c.close();
        }
        if (n == 0) r.add("(aucun)");
    }

    private static void serial(Report r) {
        r.add("");
        r.add("== Ports série (liaison écran ↔ boîtier CAN) ==");
        File dev = new File("/dev");
        String[] names = dev.list();
        int n = 0;
        if (names != null) {
            Arrays.sort(names);
            for (String s : names) {
                if (s.matches("tty(S|MT|HS|USB|ACM|GS|SAC|mxc|AMA|FIQ)\\d+")) {
                    File f = new File(dev, s);
                    r.add("/dev/" + s + " : lecture " + (f.canRead() ? "OUI" : "non") + ", écriture "
                            + (f.canWrite() ? "OUI" : "non"));
                    n++;
                }
            }
        }
        if (n == 0) r.add("(aucun port visible)");
        else r.add("Note : ces ports sont réservés au système ; l'appli ne les lit pas pour ne pas gêner l'écran.");
    }

    private static void bindKnown(final Context ctx, Report r) {
        r.add("");
        r.add("== Services connus ==");
        String[][] known = {
                {"com.syu.ms", "com.syu.ms.toolkit"},
                {"com.syu.canbus", "com.syu.canbus.toolkit"},
        };
        int tried = 0;
        for (String[] k : known) {
            if (!r.packages.contains(k[0])) continue;
            tried++;
            final String[] desc = {null};
            final CountDownLatch latch = new CountDownLatch(1);
            ServiceConnection conn = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName name, IBinder service) {
                    try {
                        desc[0] = service.getInterfaceDescriptor();
                    } catch (Exception e) {
                        desc[0] = "(descripteur illisible)";
                    }
                    latch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName name) {
                    // rien
                }
            };
            Intent it = new Intent(k[1]);
            it.setPackage(k[0]);
            boolean ok;
            try {
                ok = ctx.getApplicationContext().bindService(it, conn, 0);
            } catch (Throwable t) {
                ok = false;
                r.add(k[1] + " : refusé (" + t.getClass().getSimpleName() + ")");
            }
            if (ok) {
                try {
                    latch.await(2500, TimeUnit.MILLISECONDS);
                } catch (InterruptedException ignored) {
                    // ignore
                }
                r.add(k[1] + " : " + (desc[0] != null ? "joignable, interface " + desc[0] : "déclaré mais ne répond pas"));
                try {
                    ctx.getApplicationContext().unbindService(conn);
                } catch (Throwable ignored) {
                    // ignore
                }
            } else {
                r.add(k[1] + " : non joignable");
            }
        }
        if (tried == 0) r.add("(aucun service connu présent)");
    }
}
