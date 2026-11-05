package com.rayan.w204dash.data;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Build;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.compat.Compat;

import java.io.IOException;
import java.util.Locale;

/**
 * Source OBD2 via un adaptateur ELM327 Bluetooth (appaire au prealable dans
 * les reglages Bluetooth d'Android). PIDs standards : vitesse (0D), regime
 * (0C), temperature exterieure (46), tension (ATRV / 42), carburant (2F),
 * liquide de refroidissement (05), kilometrage (A6, rare).
 */
public class Obd2DataSource extends BaseSource implements Runnable {

    private static final String TAG = "OBD2";

    private final String address;
    private final String deviceName;
    private volatile boolean running;
    private Thread thread;
    private Elm327Link link;
    private final boolean[] supported = new boolean[256];

    public Obd2DataSource(String address, String deviceName) {
        this.address = address == null ? "" : address;
        this.deviceName = deviceName == null || deviceName.length() == 0 ? this.address : deviceName;
    }

    @Override
    public String getName() {
        return "OBD2";
    }

    @Override
    public void start(Context ctx) {
        if (address.length() == 0) {
            setState(STATE_ERROR, "Aucun adaptateur OBD2 choisi (Réglages)");
            DiagLog.w(TAG, "Aucun adaptateur choisi");
            return;
        }
        if (!Compat.hasBluetoothPermission(ctx)) {
            setState(STATE_ERROR, "Autorisation Bluetooth requise (Réglages)");
            DiagLog.w(TAG, "Autorisation BLUETOOTH_CONNECT manquante");
            return;
        }
        running = true;
        thread = new Thread(this, "obd2");
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void stop() {
        running = false;
        Elm327Link l = link;
        if (l != null) l.close();
        if (thread != null) thread.interrupt();
        setState(STATE_OFF, "Arrêtée");
    }

    @Override
    public void run() {
        int backoff = 3000;
        while (running) {
            link = new Elm327Link();
            try {
                setState(STATE_CONNECTING, "Connexion à " + deviceName + "…");
                link.connect(address);
                DiagLog.i(TAG, "Bluetooth connecté à " + deviceName);
                initElm(link, null);
                setState(STATE_OK, "Connecté à " + deviceName);
                backoff = 3000;
                pollLoop(link);
            } catch (Exception e) {
                if (running) {
                    DiagLog.e(TAG, "Liaison perdue / échec", e);
                    setState(STATE_ERROR, "Erreur OBD2 : " + e.getMessage() + " (nouvel essai…)");
                }
            } finally {
                link.close();
                synchronized (data) {
                    data.clear();
                }
            }
            if (!running) break;
            try {
                Thread.sleep(backoff);
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(15000, backoff * 2);
        }
    }

    /** Initialise l'ELM327 et detecte les PIDs supportes. */
    private void initElm(Elm327Link l, TestReport report) throws IOException {
        String id = l.send("ATZ", 2500);
        log(report, "ATZ → " + oneLine(id));
        l.send("ATE0", 1000);
        l.send("ATL0", 1000);
        l.send("ATS0", 1000);
        l.send("ATH0", 1000);
        l.send("ATAT1", 1000);
        l.send("ATSP0", 1000);
        String r = l.send("0100", 12000);
        log(report, "0100 → " + oneLine(r));
        java.util.Arrays.fill(supported, false);
        if (!ObdParser.parseSupported(r, 0x00, supported)) {
            throw new IOException("Le véhicule ne répond pas (contact mis ?) : " + oneLine(r));
        }
        int base = 0x00;
        while (base < 0xA0 && supported[base + 0x20]) {
            base += 0x20;
            String rr = l.send("01" + ObdParser.hex2(base), 3000);
            if (!ObdParser.parseSupported(rr, base, supported)) break;
        }
        String proto = l.send("ATDPN", 1000);
        log(report, "Protocole : " + ObdParser.protocolName(proto));
        StringBuilder sb = new StringBuilder("PIDs utiles supportés :");
        int[] pids = {ObdParser.PID_SPEED, ObdParser.PID_RPM, ObdParser.PID_COOLANT, ObdParser.PID_AMBIENT_TEMP,
                ObdParser.PID_FUEL_LEVEL, ObdParser.PID_MODULE_VOLTAGE, ObdParser.PID_ODOMETER};
        String[] names = {"vitesse", "régime", "liquide refroid.", "temp. ext.", "carburant", "tension", "kilométrage"};
        for (int i = 0; i < pids.length; i++) {
            sb.append(' ').append(names[i]).append(supported[pids[i]] ? "=oui" : "=non");
        }
        log(report, sb.toString());
    }

    private void log(TestReport report, String s) {
        DiagLog.i(TAG, s);
        if (report != null) report.line(s);
    }

    private static String oneLine(String s) {
        return s == null ? "(rien)" : s.replace('\r', ' ').replace('\n', ' ').trim();
    }

    private void pollLoop(Elm327Link l) throws IOException {
        long tick = 0;
        while (running) {
            int[] b;
            b = query(l, ObdParser.PID_SPEED);
            float speed = ObdParser.speed(b);
            b = query(l, ObdParser.PID_RPM);
            float rpm = ObdParser.rpm(b);
            synchronized (data) {
                data.speed = speed;
                data.rpm = rpm;
            }
            if (tick % 10 == 0) {
                float coolant = supported[ObdParser.PID_COOLANT]
                        ? ObdParser.temperature(query(l, ObdParser.PID_COOLANT)) : Float.NaN;
                float v = ObdParser.voltage(l.send("ATRV", 1500));
                if (Float.isNaN(v) && supported[ObdParser.PID_MODULE_VOLTAGE]) {
                    v = ObdParser.moduleVoltage(query(l, ObdParser.PID_MODULE_VOLTAGE));
                }
                synchronized (data) {
                    data.coolant = coolant;
                    data.battery = v;
                }
            }
            if (tick % 40 == 0) {
                float amb = supported[ObdParser.PID_AMBIENT_TEMP]
                        ? ObdParser.temperature(query(l, ObdParser.PID_AMBIENT_TEMP)) : Float.NaN;
                float fuel = supported[ObdParser.PID_FUEL_LEVEL]
                        ? ObdParser.fuelPercent(query(l, ObdParser.PID_FUEL_LEVEL)) : Float.NaN;
                float odo = supported[ObdParser.PID_ODOMETER]
                        ? ObdParser.odometer(query(l, ObdParser.PID_ODOMETER)) : Float.NaN;
                synchronized (data) {
                    data.outsideTemp = amb;
                    data.fuelPct = fuel;
                    data.odometer = odo;
                }
            }
            tick++;
            try {
                Thread.sleep(60);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private int[] query(Elm327Link l, int pid) throws IOException {
        String r = l.send("01" + ObdParser.hex2(pid), 1500);
        if (r.toUpperCase(Locale.US).contains("UNABLE TO CONNECT")) {
            throw new IOException("UNABLE TO CONNECT (contact coupé ?)");
        }
        return ObdParser.isError(r) ? null : ObdParser.parseMode01(r, pid);
    }

    @Override
    public void runConnectionTest(Context ctx, TestReport report) {
        report.line("Test OBD2 (ELM327 Bluetooth)");
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) {
            report.line("[X] Cet appareil n'a pas de Bluetooth.");
            return;
        }
        if (!Compat.hasBluetoothPermission(ctx)) {
            report.line("[X] Autorisation Bluetooth refusée (Android " + Build.VERSION.RELEASE + ").");
            return;
        }
        report.line(ad.isEnabled() ? "[OK] Bluetooth activé" : "[X] Bluetooth désactivé : activez-le.");
        if (!ad.isEnabled()) return;
        if (address.length() == 0) {
            report.line("[X] Aucun adaptateur choisi : Réglages > Source > Choisir l'adaptateur.");
            return;
        }
        report.line("Adaptateur : " + deviceName + " (" + address + ")");
        boolean wasRunning = running;
        if (wasRunning) {
            report.line("(la source OBD2 active est mise en pause pendant le test)");
            stop();
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {
                // ignore
            }
        }
        Elm327Link l = new Elm327Link();
        try {
            l.connect(address);
            report.line("[OK] Liaison Bluetooth établie");
            initElm(l, report);
            report.line("Tension batterie : " + oneLine(l.send("ATRV", 1500)));
            int[] b = ObdParser.parseMode01(l.send("010C", 2000), ObdParser.PID_RPM);
            report.line("Régime moteur : " + (b != null ? Math.round(ObdParser.rpm(b)) + " tr/min" : "--"));
            report.line("Résultat : OK");
        } catch (Exception e) {
            report.line("[X] Échec : " + e.getMessage());
            report.line("Conseils : contact mis, adaptateur bien branché (prise OBD sous le volant), "
                    + "appairé dans Android (code 1234 ou 0000).");
        } finally {
            l.close();
            if (wasRunning) start(ctx);
        }
    }
}
