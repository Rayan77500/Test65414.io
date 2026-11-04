package com.rayan.w204dash.data;

import android.content.Context;

/**
 * Combine deux sources : la premiere est prioritaire, la seconde complete
 * les valeurs manquantes (ex. OBD2 pour le moteur + ecran pour les portes).
 */
public class CompositeDataSource implements DataSource {

    private final DataSource first;
    private final DataSource second;
    private final VehicleData tmp = new VehicleData();

    public CompositeDataSource(DataSource first, DataSource second) {
        this.first = first;
        this.second = second;
    }

    @Override
    public String getName() {
        return first.getName() + " + " + second.getName();
    }

    @Override
    public void start(Context ctx) {
        first.start(ctx);
        second.start(ctx);
    }

    @Override
    public void stop() {
        first.stop();
        second.stop();
    }

    @Override
    public void snapshot(VehicleData out) {
        first.snapshot(out);
        synchronized (tmp) {
            tmp.clear();
            second.snapshot(tmp);
            out.fillMissingFrom(tmp);
        }
    }

    @Override
    public int getState() {
        int a = first.getState();
        int b = second.getState();
        if (a == STATE_OK || b == STATE_OK) return STATE_OK;
        if (a == STATE_CONNECTING || b == STATE_CONNECTING) return STATE_CONNECTING;
        return Math.max(a, b);
    }

    @Override
    public String getStatus() {
        return first.getName() + " : " + first.getStatus() + "\n" + second.getName() + " : " + second.getStatus();
    }

    @Override
    public boolean canToggleOpenables() {
        return false;
    }

    @Override
    public void toggleOpenable(int index) {
        // non supporte
    }

    @Override
    public void runConnectionTest(Context ctx, TestReport report) {
        first.runConnectionTest(ctx, report);
        report.line("");
        second.runConnectionTest(ctx, report);
    }
}
