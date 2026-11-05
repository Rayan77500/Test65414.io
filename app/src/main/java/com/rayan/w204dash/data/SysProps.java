package com.rayan.w204dash.data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Proprietes systeme Android (commande "getprop") : lecture, filtrage des
 * proprietes liees au vehicule et analyse de l'etat du boitier CAN.
 */
public final class SysProps {

    private static final Pattern LINE = Pattern.compile("^\\[([^\\]]+)\\]: \\[(.*)\\]$");
    private static final Pattern VEHICLE = Pattern.compile(
            "(?i).*(can|car|door|backcar|reverse|radar|belt|air|mcu|acc|park|brake|gear|light|ill|wheel|steer|key).*");

    private SysProps() {
    }

    /** Toutes les proprietes lisibles (vide si getprop est indisponible). */
    public static Map<String, String> readAll() {
        Map<String, String> out = new LinkedHashMap<String, String>();
        Process p = null;
        try {
            p = Runtime.getRuntime().exec("getprop");
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            int total = 0;
            while ((line = br.readLine()) != null && total < 5000) {
                total++;
                parseLine(line, out);
            }
            br.close();
        } catch (Exception ignored) {
            // getprop absent
        } finally {
            if (p != null) p.destroy();
        }
        return out;
    }

    public static void parseLine(String line, Map<String, String> out) {
        Matcher m = LINE.matcher(line.trim());
        if (m.matches()) out.put(m.group(1), m.group(2));
    }

    /** Proprietes susceptibles de changer avec l'etat de la voiture. */
    public static boolean isVehicleKey(String key) {
        if (key.startsWith("ro.") || key.startsWith("dalvik.") || key.contains("ril.") || key.startsWith("init.svc.")
                || key.contains("boottime") || key.contains("carrier") || key.contains("vold")) {
            return false;
        }
        return VEHICLE.matcher(key).matches();
    }

    public static Map<String, String> vehicleOnly(Map<String, String> all) {
        Map<String, String> out = new LinkedHashMap<String, String>();
        for (Map.Entry<String, String> e : all.entrySet()) {
            if (isVehicleKey(e.getKey())) out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    /**
     * Conclusions lisibles sur le boitier CAN a partir des proprietes.
     * Classe sans dependance Android (testee sur PC).
     */
    public static java.util.List<String> verdict(Map<String, String> p) {
        java.util.List<String> v = new java.util.ArrayList<String>();
        String fun = p.get("persist.sys.fun.canbus");
        String box = p.get("persist.sys.can_box");
        String type = p.get("persist.sys.can_car_type");
        if (fun != null || box != null) {
            boolean off = "0".equals(fun) || ("0".equals(box) && (type == null || "0".equals(type)));
            if (off) {
                v.add("Boîtier CAN : réglages internes à zéro (fun.canbus=" + fun + ", can_box=" + box
                        + ", can_car_type=" + type + "). Selon les écrans, cela veut dire « désactivé » ou seulement"
                        + " « premier type de boîtier » : ce n'est pas une preuve.");
                v.add("  Indice fiable : si l'écran affiche lui-même une fenêtre quand on ouvre une porte ou qu'on"
                        + " règle la clim, il reçoit bien les infos de la voiture.");
            } else {
                v.add("Boîtier CAN : configuré (fun.canbus=" + fun + ", can_box=" + box + ", can_car_type=" + type + ").");
            }
        }
        String pwd = p.get("persist.sys.factory_password");
        if (pwd != null && pwd.length() > 0) v.add("Code des réglages d'usine de l'écran : " + pwd);
        String plat = p.get("ro.board.platform");
        if (plat != null) {
            String low = plat.toLowerCase(Locale.US);
            if (low.contains("9863") || low.contains("ums512") || low.contains("ums312")) {
                v.add("Processeur : Unisoc/Spreadtrum (" + plat + ").");
            }
        }
        return v;
    }
}
