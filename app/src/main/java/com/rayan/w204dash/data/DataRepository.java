package com.rayan.w204dash.data;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.Prefs;

import java.util.ArrayList;

/**
 * Point central des donnees : cree la source choisie dans les Reglages,
 * lit ses valeurs ~6 fois par seconde et les diffuse aux ecrans abonnes.
 */
public final class DataRepository {

    public interface Listener {
        void onVehicleData(VehicleData d);
    }

    private static final int PERIOD_MS = 160;

    private final Context ctx;
    private final Prefs prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ArrayList<Listener> listeners = new ArrayList<Listener>();
    private final VehicleData current = new VehicleData();
    private final int[] lastSourceOpen = new int[VehicleData.OPENABLES];
    private final boolean[] visualOpen = new boolean[VehicleData.OPENABLES];
    private DataSource source;
    private boolean ticking;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            ticking = false;
            if (listeners.isEmpty()) return;
            poll();
            for (Listener l : listeners.toArray(new Listener[listeners.size()])) {
                try {
                    l.onVehicleData(current);
                } catch (Throwable t) {
                    DiagLog.e("Données", "Erreur d'affichage", t);
                }
            }
            schedule();
        }
    };

    public DataRepository(Context ctx, Prefs prefs) {
        this.ctx = ctx.getApplicationContext();
        this.prefs = prefs;
        java.util.Arrays.fill(lastSourceOpen, VehicleData.UNKNOWN);
        restartSource();
    }

    /** Recree la source a partir des reglages (appele apres un changement). */
    public void restartSource() {
        if (source != null) {
            try {
                source.stop();
            } catch (Throwable t) {
                DiagLog.e("Données", "Arrêt de la source", t);
            }
        }
        source = create(prefs.source());
        DiagLog.i("Données", "Source active : " + source.getName());
        java.util.Arrays.fill(lastSourceOpen, VehicleData.UNKNOWN);
        try {
            source.start(ctx);
        } catch (Throwable t) {
            DiagLog.e("Données", "Démarrage de la source", t);
        }
    }

    private DataSource create(String id) {
        boolean auto = Prefs.GEAR_AUTO.equals(prefs.gearbox());
        if (Prefs.SRC_OBD.equals(id)) {
            return new Obd2DataSource(prefs.obdAddress(), prefs.obdName());
        }
        if (Prefs.SRC_CAN.equals(id)) {
            return new HeadUnitDataSource(prefs.customCanActions());
        }
        if (Prefs.SRC_OBD_CAN.equals(id)) {
            return new CompositeDataSource(new Obd2DataSource(prefs.obdAddress(), prefs.obdName()),
                    new HeadUnitDataSource(prefs.customCanActions()));
        }
        return new DemoDataSource(auto);
    }

    public DataSource source() {
        return source;
    }

    public boolean isDemo() {
        return source instanceof DemoDataSource;
    }

    private void poll() {
        current.clear();
        try {
            source.snapshot(current);
        } catch (Throwable t) {
            DiagLog.e("Données", "Lecture de la source", t);
        }
        if (current.gear == null) {
            current.gear = DemoDataSource.estimateGear(current.speed, current.rpm,
                    Prefs.GEAR_AUTO.equals(prefs.gearbox()));
        }
        // l'etat visuel des ouvrants suit les changements reels
        for (int i = 0; i < VehicleData.OPENABLES; i++) {
            int v = current.open[i];
            if (v != VehicleData.UNKNOWN && v != lastSourceOpen[i]) {
                visualOpen[i] = v == 1;
            }
            lastSourceOpen[i] = v;
        }
    }

    /** Etat d'ouverture affiche sur la voiture 3D. */
    public boolean isVisuallyOpen(int i) {
        return visualOpen[i];
    }

    /** Touche sur un ouvrant (voiture 3D ou liste). */
    public void toggleFromUser(int i) {
        if (i < 0 || i >= VehicleData.OPENABLES) return;
        if (source.canToggleOpenables()) {
            source.toggleOpenable(i);
            poll();
        } else {
            visualOpen[i] = !visualOpen[i];
        }
        notifyNow();
    }

    public VehicleData current() {
        return current;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
        schedule();
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void notifyNow() {
        handler.removeCallbacks(tick);
        ticking = false;
        tick.run();
    }

    private void schedule() {
        if (!ticking && !listeners.isEmpty()) {
            ticking = true;
            handler.postDelayed(tick, PERIOD_MS);
        }
    }

    /** Lance le test de connexion de la source active (resultat ligne par ligne). */
    public void runTest(final DataSource.TestReport report) {
        final DataSource s = source;
        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    s.runConnectionTest(ctx, report);
                } catch (Throwable e) {
                    report.line("[X] Erreur pendant le test : " + e);
                }
            }
        }, "test-connexion");
        t.setDaemon(true);
        t.start();
    }
}
