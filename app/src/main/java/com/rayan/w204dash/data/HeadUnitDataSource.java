package com.rayan.w204dash.data;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.compat.Compat;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Source "Ecran voiture" : tente de lire les infos du boitier CAN de l'ecran
 * Android (portes, ventilation, ceintures...) en ecoutant les broadcasts
 * detectes par {@link HeadUnitProbe} et ceux ajoutes a la main.
 */
public class HeadUnitDataSource extends BaseSource {

    private static final String TAG = "CAN";

    /** Actions generiques tentees en plus de celles detectees (non garanties). */
    static final String[] GENERIC_ACTIONS = {
            "android.intent.action.CANBUS",
            "android.intent.action.CAN_DATA",
            "android.intent.action.CAR_DOOR",
            "android.intent.action.DOOR_STATE",
            "android.intent.action.REVERSE",
            "com.android.canbus",
            "com.android.canbus.DATA",
            "com.android.canbus.DOOR",
    };

    private final String customActions;
    private Context ctx;
    private BroadcastReceiver receiver;
    private volatile boolean running;
    private final Map<String, Long> lastLogged = new HashMap<String, Long>();
    private int received;
    private int listening;
    private volatile int propChanges;
    private String platform = "?";
    private Thread watcher;

    public HeadUnitDataSource(String customActions) {
        this.customActions = customActions == null ? "" : customActions;
    }

    @Override
    public String getName() {
        return "Écran voiture";
    }

    @Override
    public void start(final Context c) {
        ctx = c.getApplicationContext();
        running = true;
        setState(STATE_CONNECTING, "Recherche du boîtier CAN de l'écran…");
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                final HeadUnitProbe.Report rep = HeadUnitProbe.run(ctx);
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        if (running) listen(rep);
                    }
                });
            }
        }, "can-probe");
        t.setDaemon(true);
        t.start();
    }

    private void listen(HeadUnitProbe.Report rep) {
        platform = rep.platform;
        Set<String> actions = new LinkedHashSet<String>(rep.actions);
        actions.addAll(Arrays.asList(GENERIC_ACTIONS));
        for (String a : customActions.split("[\\s,;]+")) {
            if (a.trim().length() > 3) actions.add(a.trim());
        }
        IntentFilter f = new IntentFilter();
        for (String a : actions) {
            f.addAction(a);
        }
        listening = actions.size();
        startPropertyWatcher();
        receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                handle(intent);
            }
        };
        try {
            Intent sticky = Compat.registerReceiverExported(ctx, receiver, f);
            if (sticky != null) handle(sticky);
            DiagLog.i(TAG, "Écoute de " + listening + " broadcast(s) (dont " + rep.actions.size() + " détectés)");
        } catch (Throwable t) {
            DiagLog.e(TAG, "Impossible d'écouter les broadcasts", t);
        }
        updateStatus();
    }

    /**
     * Surveille les proprietes systeme liees au vehicule (toutes les 3 s) et note
     * chaque changement dans le journal : ouvrir une porte ou passer la marche
     * arriere permet de voir ce que l'ecran expose.
     */
    private void startPropertyWatcher() {
        if (watcher != null) return;
        watcher = new Thread(new Runnable() {
            @Override
            public void run() {
                java.util.Map<String, String> prev = SysProps.vehicleOnly(SysProps.readAll());
                DiagLog.i(TAG, "Surveillance de " + prev.size() + " propriété(s) système liée(s) au véhicule");
                while (running) {
                    try {
                        Thread.sleep(3000);
                    } catch (InterruptedException e) {
                        return;
                    }
                    java.util.Map<String, String> cur = SysProps.vehicleOnly(SysProps.readAll());
                    int logged = 0;
                    for (java.util.Map.Entry<String, String> e : cur.entrySet()) {
                        String old = prev.get(e.getKey());
                        if (!e.getValue().equals(old) && logged < 15) {
                            DiagLog.i(TAG, "Propriété modifiée : " + e.getKey() + " = " + e.getValue()
                                    + (old == null ? " (nouvelle)" : " (avant : " + old + ")"));
                            logged++;
                            propChanges++;
                        }
                    }
                    prev = cur;
                    if (logged > 0) updateStatus();
                }
            }
        }, "can-proprietes");
        watcher.setDaemon(true);
        watcher.start();
    }

    private void updateStatus() {
        boolean known = platform != null && !platform.startsWith("inconnue");
        String s = "Plateforme : " + platform + " — " + listening + " broadcast(s) écouté(s), "
                + received + " reçu(s)" + (propChanges > 0 ? ", " + propChanges + " propriété(s) modifiée(s)" : "");
        setState(received > 0 ? STATE_OK : (known ? STATE_CONNECTING : STATE_ERROR), s);
    }

    void handle(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        Map<String, Object> map = new HashMap<String, Object>();
        Bundle b = null;
        try {
            b = intent.getExtras();
        } catch (Throwable ignored) {
            // extras non lisibles (classe inconnue)
        }
        StringBuilder dump = new StringBuilder();
        if (b != null) {
            for (String k : b.keySet()) {
                Object v;
                try {
                    v = b.get(k);
                } catch (Throwable t) {
                    v = "(illisible)";
                }
                map.put(k, v);
                if (dump.length() < 400) dump.append(' ').append(k).append('=').append(describe(v));
            }
        }
        int n;
        synchronized (data) {
            n = CanExtrasParser.parse(action, map, data);
        }
        received++;
        long now = System.currentTimeMillis();
        Long prev = lastLogged.get(action);
        if (prev == null || now - prev > 3000) {
            lastLogged.put(action, now);
            DiagLog.i(TAG, "Reçu " + action + " :" + (dump.length() > 0 ? dump : " (sans extras)")
                    + (n > 0 ? "  → " + n + " valeur(s) reconnue(s)" : "  → rien de reconnu"));
        }
        updateStatus();
    }

    static String describe(Object v) {
        if (v == null) return "null";
        if (v instanceof int[]) return Arrays.toString((int[]) v);
        if (v instanceof byte[]) {
            byte[] bb = (byte[]) v;
            StringBuilder sb = new StringBuilder("hex:");
            for (int i = 0; i < Math.min(32, bb.length); i++) {
                sb.append(String.format("%02X", bb[i] & 0xFF));
            }
            return sb.toString();
        }
        if (v instanceof boolean[]) return Arrays.toString((boolean[]) v);
        if (v instanceof float[]) return Arrays.toString((float[]) v);
        if (v instanceof String[]) return Arrays.toString((String[]) v);
        String s = String.valueOf(v);
        return s.length() > 60 ? s.substring(0, 60) + "…" : s;
    }

    @Override
    public void stop() {
        running = false;
        if (watcher != null) watcher.interrupt();
        watcher = null;
        if (receiver != null && ctx != null) {
            try {
                ctx.unregisterReceiver(receiver);
            } catch (Throwable ignored) {
                // deja retire
            }
        }
        receiver = null;
        setState(STATE_OFF, "Arrêtée");
    }

    @Override
    public void runConnectionTest(Context c, TestReport report) {
        report.line("Test « Écran voiture » (boîtier CAN de l'écran)");
        HeadUnitProbe.Report r = HeadUnitProbe.run(c.getApplicationContext());
        report.line("Plateforme : " + r.platform);
        report.line("Applis liées au véhicule : " + r.packages.size());
        for (String p : r.packages) {
            report.line("  • " + p);
        }
        report.line("Broadcasts candidats trouvés : " + r.actions.size());
        for (String v : r.verdict) report.line(v);
        if (running) report.line("Broadcasts reçus depuis le démarrage : " + received);
        if (r.packages.isEmpty()) {
            report.line("[X] Aucun boîtier CAN reconnu. Les données véhicule ne seront pas disponibles par l'écran.");
        } else {
            report.line("-> Ouvrez/fermez une porte, passez la marche arrière, puis regardez le journal dans"
                    + " Diagnostic : chaque broadcast reçu et chaque propriété modifiée y apparaissent.");
        }
        report.line("Le détail complet est dans l'écran Diagnostic.");
    }
}
