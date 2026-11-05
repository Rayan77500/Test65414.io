package com.rayan.w204dash.data;

import android.content.Context;

/**
 * Valeurs simulees realistes pour une C 180 CDI (OM 651, diesel) :
 * ralenti vers 750 tr/min, 14 V moteur tournant, cycle de conduite de 90 s
 * (arret, ville, route, freinage, manoeuvre en marche arriere).
 */
public class DemoDataSource extends BaseSource {

    /** Rapports approximatifs (boite manuelle 6 / automatique 7G-Tronic). */
    private static final double[] MANUAL = {5.01, 2.61, 1.55, 1.14, 0.87, 0.73};
    private static final double[] AUTO = {4.38, 2.86, 1.92, 1.37, 1.00, 0.82, 0.73};
    private static final double FINAL_DRIVE = 2.65;
    private static final double WHEEL_CIRC = 2 * Math.PI * 0.316; // 205/55 R16

    private final boolean automatic;
    private final boolean[] openState = new boolean[VehicleData.OPENABLES];
    private long startMs;
    private long lastMs;
    private double odometer = 187450.0;
    private double fuel = 62.0;
    private int gearIndex = 0;

    public DemoDataSource(boolean automatic) {
        this.automatic = automatic;
    }

    @Override
    public String getName() {
        return "Démo";
    }

    @Override
    public void start(Context ctx) {
        startMs = System.currentTimeMillis();
        lastMs = startMs;
        setState(STATE_OK, "Mode démo : valeurs simulées");
    }

    @Override
    public void stop() {
        setState(STATE_OFF, "Arrêtée");
    }

    @Override
    public void snapshot(VehicleData out) {
        compute(System.currentTimeMillis());
        super.snapshot(out);
    }

    /** Vitesse du cycle de demonstration (km/h) a l'instant t (s). */
    static double cycleSpeed(double t) {
        double c = t % 90.0;
        if (c < 12) return 0;
        if (c < 24) return 50 * smooth((c - 12) / 12);
        if (c < 40) return 50 + 2.5 * Math.sin(c * 0.9);
        if (c < 50) return 50 + 40 * smooth((c - 40) / 10);
        if (c < 62) return 90 + 1.5 * Math.sin(c * 0.7);
        if (c < 74) return 90 * (1 - smooth((c - 62) / 12));
        if (c < 82) return 0;
        // marche arriere lente
        return 4 * Math.sin(Math.PI * (c - 82) / 8);
    }

    private static double smooth(double x) {
        x = Math.max(0, Math.min(1, x));
        return x * x * (3 - 2 * x);
    }

    synchronized void compute(long now) {
        if (startMs == 0) {
            startMs = now;
            lastMs = now;
        }
        double t = (now - startMs) / 1000.0;
        double dt = Math.max(0, Math.min(2.0, (now - lastMs) / 1000.0));
        lastMs = now;
        double c = t % 90.0;
        boolean rev = c >= 82;
        double speed = Math.abs(cycleSpeed(t));
        double accel = cycleSpeed(t + 0.5) - cycleSpeed(t - 0.5);

        double[] ratios = automatic ? AUTO : MANUAL;
        double wheelRpm = speed / 3.6 / WHEEL_CIRC * 60.0;
        double rpm;
        String gear;
        if (speed < 1.0) {
            gearIndex = 0;
            rpm = 750 + 12 * Math.sin(t * 2.1) + 6 * Math.sin(t * 5.3);
            gear = rev ? "R" : (automatic ? (c < 12 ? "P" : "D") : "N");
        } else if (rev) {
            rpm = 820 + 30 * Math.sin(t);
            gear = "R";
        } else {
            double up = automatic ? 1900 : 2200;
            double down = automatic ? 1150 : 1300;
            gearIndex = Math.max(0, Math.min(ratios.length - 1, gearIndex));
            double r = wheelRpm * ratios[gearIndex] * FINAL_DRIVE;
            while (r > up && gearIndex < ratios.length - 1) {
                gearIndex++;
                r = wheelRpm * ratios[gearIndex] * FINAL_DRIVE;
            }
            while (r < down && gearIndex > 0) {
                gearIndex--;
                r = wheelRpm * ratios[gearIndex] * FINAL_DRIVE;
            }
            rpm = Math.max(780, r + (accel > 0 ? 120 : 0));
            gear = automatic ? "D" + (gearIndex + 1) : String.valueOf(gearIndex + 1);
        }

        odometer += speed * dt / 3600.0;
        fuel = Math.max(5, fuel - dt * (0.0004 + speed * 0.000004));

        synchronized (data) {
            data.speed = (float) speed;
            data.rpm = (float) rpm;
            data.gear = gear;
            data.outsideTemp = (float) (14.5 + 1.5 * Math.sin(t / 300.0));
            data.battery = (float) (14.15 + 0.06 * Math.sin(t / 7.0));
            data.odometer = (float) odometer;
            data.fuelPct = (float) fuel;
            data.coolant = (float) (89 - 44 * Math.exp(-t / 120.0));
            data.beltDriver = 1;
            data.beltPassenger = 0;
            data.washerLow = 0;
            data.reverse = rev ? 1 : 0;
            data.climTemp = 21.5f;
            data.fanLevel = 3;
            data.acOn = 1;
            for (int i = 0; i < VehicleData.OPENABLES; i++) {
                data.open[i] = openState[i] ? 1 : 0;
            }
            // radars : toujours simules en demo pour que l'ecran s'affiche
            double dr = 95 + 70 * Math.sin(t / 3.5);
            data.radarRear[0] = (int) (dr + 30);
            data.radarRear[1] = (int) dr;
            data.radarRear[2] = (int) (dr + 9);
            data.radarRear[3] = (int) (dr + 38);
            double df = 125 + 60 * Math.sin(t / 4.7 + 1.3);
            data.radarFront[0] = (int) (df + 25);
            data.radarFront[1] = (int) (df + 4);
            data.radarFront[2] = (int) df;
            data.radarFront[3] = (int) (df + 33);
        }
    }

    /** Rapport estime a partir de la vitesse et du regime (boite manuelle), ou null. */
    public static String estimateGear(float speed, float rpm, boolean automatic) {
        if (Float.isNaN(speed) || Float.isNaN(rpm) || speed < 5 || rpm < 600) return null;
        if (automatic) return "D";
        double wheelRpm = speed / 3.6 / WHEEL_CIRC * 60.0;
        double ratio = rpm / (wheelRpm * FINAL_DRIVE);
        int best = 0;
        double err = Double.MAX_VALUE;
        for (int i = 0; i < MANUAL.length; i++) {
            double e = Math.abs(Math.log(ratio / MANUAL[i]));
            if (e < err) {
                err = e;
                best = i;
            }
        }
        return err < 0.18 ? String.valueOf(best + 1) : null;
    }

    @Override
    public boolean canToggleOpenables() {
        return true;
    }

    @Override
    public synchronized void toggleOpenable(int index) {
        if (index >= 0 && index < VehicleData.OPENABLES) {
            openState[index] = !openState[index];
        }
    }

    @Override
    public void runConnectionTest(Context ctx, TestReport report) {
        report.line("Source « Démo » : aucune connexion nécessaire.");
        report.line("Les valeurs sont simulées (diesel OM 651 : ralenti ~750 tr/min, 14,1 V).");
        report.line("Résultat : OK");
    }
}
