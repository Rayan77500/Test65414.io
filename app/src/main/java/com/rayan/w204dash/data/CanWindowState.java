package com.rayan.w204dash.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Etat des ouvrants lu dans la fenetre que l'appli CAN de l'ecran affiche
 * quand une porte s'ouvre (via le service d'accessibilite CanWindowReader).
 * L'analyse des noms de vues est sans dependance Android (testee sur PC).
 */
public final class CanWindowState {

    /** Element d'une fenetre, simplifie. */
    public static final class Node {
        public String id = "";
        public String cls = "";
        public String text = "";
        public String desc = "";
        public boolean visible;
        public boolean selected;
    }

    /** Vrai quand le service d'accessibilite est connecte. */
    public static volatile boolean active;
    private static final int[] OPEN = new int[VehicleData.OPENABLES];
    private static long updated;
    private static boolean mappingFound;
    private static String lastDump = "";
    private static String lastPackage = "";

    static {
        Arrays.fill(OPEN, VehicleData.UNKNOWN);
    }

    private CanWindowState() {
    }

    // ------------------------------------------------------------ analyse
    static Set<String> tokens(String s) {
        Set<String> out = new HashSet<String>();
        if (s == null) return out;
        String x = s;
        int slash = x.lastIndexOf('/');
        if (slash >= 0) x = x.substring(slash + 1);
        x = x.replaceAll("([a-z])([A-Z])", "$1 $2").replaceAll("([a-zA-Z])([0-9])", "$1 $2");
        for (String t : x.toLowerCase(Locale.FRANCE).split("[^a-z0-9àâçéèêëîïôûùüÿ]+")) {
            if (t.length() > 0) out.add(t);
        }
        return out;
    }

    private static boolean any(Set<String> t, String... w) {
        for (String s : w) {
            if (t.contains(s)) return true;
        }
        return false;
    }

    /** Ouvrant designe par un nom de vue ou un texte (ex. "iv_door_lf", "Porte avant gauche"), ou -1. */
    public static int openableOf(Set<String> t) {
        boolean door = any(t, "door", "doors", "porte", "portes", "dr");
        if (any(t, "hood", "bonnet", "capot") || (t.contains("engine") && !any(t, "temp", "water", "oil"))) {
            return VehicleData.HOOD;
        }
        boolean left = any(t, "left", "l", "gauche", "g", "lh");
        boolean right = any(t, "right", "r", "droite", "d", "rh");
        if (any(t, "trunk", "boot", "tail", "tailgate", "coffre", "hatch", "hayon")
                || (door && t.contains("back") && !left && !right)) {
            return VehicleData.TRUNK;
        }
        boolean front = any(t, "front", "f", "avant", "av", "head");
        boolean rear = any(t, "rear", "back", "b", "arriere", "arrière", "ar");
        if (any(t, "fl", "lf") || (front && left)) return VehicleData.DOOR_FL;
        if (any(t, "fr", "rf") || (front && right)) return VehicleData.DOOR_FR;
        if (any(t, "rl", "lr", "bl", "lb") || (rear && left)) return VehicleData.DOOR_RL;
        if (any(t, "rr", "br", "rb") || (rear && right)) return VehicleData.DOOR_RR;
        return -1;
    }

    /**
     * Deduit l'etat des ouvrants des elements visibles de la fenetre.
     *
     * @return tableau d'etats (-1 inconnu) ou null si la fenetre n'est pas interpretable
     */
    public static int[] parse(List<Node> nodes) {
        int[] st = new int[VehicleData.OPENABLES];
        Arrays.fill(st, VehicleData.UNKNOWN);
        int mapped = 0, visible = 0, selected = 0;
        List<int[]> hits = new ArrayList<int[]>(); // {index, visible, selected}
        for (Node n : nodes) {
            int idx = openableOf(tokens(n.id));
            boolean fromText = false;
            if (idx < 0) {
                idx = openableOf(tokens(n.text + " " + n.desc));
                fromText = idx >= 0;
            }
            if (idx < 0) continue;
            if (fromText) {
                // texte explicite : "ouverte" / "fermée"
                String t = (n.text + " " + n.desc).toLowerCase(Locale.FRANCE);
                if (n.visible) {
                    st[idx] = t.contains("ferm") || t.contains("close") ? 0 : 1;
                }
                continue;
            }
            mapped++;
            if (n.visible) visible++;
            if (n.selected) selected++;
            hits.add(new int[]{idx, n.visible ? 1 : 0, n.selected ? 1 : 0});
        }
        if (mapped > 0) {
            // si toutes les images sont visibles, c'est leur contenu (non lisible) qui change :
            // on ne peut conclure qu'avec l'etat "selectionne"
            boolean useSelected = visible == mapped && mapped >= 3;
            if (useSelected && selected == 0) return hasAny(st) ? st : null;
            for (int[] h : hits) {
                int v = useSelected ? h[2] : h[1];
                if (st[h[0]] != 1) st[h[0]] = v;
            }
        }
        return hasAny(st) ? st : null;
    }

    private static boolean hasAny(int[] st) {
        for (int v : st) {
            if (v != VehicleData.UNKNOWN) return true;
        }
        return false;
    }

    // ------------------------------------------------------------ etat partage
    /** Nouvelle lecture d'une fenetre de l'appli CAN. @return vrai si des ouvrants ont ete reconnus */
    public static synchronized boolean apply(String pkg, List<Node> nodes) {
        lastPackage = pkg == null ? "" : pkg;
        int[] st = parse(nodes);
        if (st == null) return false;
        mappingFound = true;
        for (int i = 0; i < st.length; i++) {
            if (st[i] != VehicleData.UNKNOWN) OPEN[i] = st[i];
        }
        updated = System.currentTimeMillis();
        return true;
    }

    /** La fenetre de l'appli CAN a disparu : tout est considere ferme (si on savait la lire). */
    public static synchronized void windowGone() {
        if (!mappingFound) return;
        Arrays.fill(OPEN, 0);
        updated = System.currentTimeMillis();
    }

    /** Complete les ouvrants inconnus avec ceux lus a l'ecran. */
    public static synchronized void fill(VehicleData d) {
        if (updated == 0) return;
        for (int i = 0; i < VehicleData.OPENABLES; i++) {
            if (d.open[i] == VehicleData.UNKNOWN) d.open[i] = OPEN[i];
        }
    }

    public static synchronized void setDump(String s) {
        lastDump = s;
    }

    public static synchronized String dump() {
        return lastDump;
    }

    public static synchronized String summary() {
        if (!active) return "désactivée";
        if (!mappingFound) {
            return "activée, en attente d'une fenêtre de l'appli CAN" + (lastPackage.length() > 0
                    ? " (dernière vue : " + lastPackage + ", non interprétée)" : "");
        }
        StringBuilder sb = new StringBuilder("activée, ouvrants lus :");
        for (int i = 0; i < VehicleData.OPENABLES; i++) {
            sb.append(' ').append(VehicleData.openableName(i)).append('=')
                    .append(OPEN[i] == 1 ? "ouvert" : OPEN[i] == 0 ? "fermé" : "?");
        }
        return sb.toString();
    }
}
