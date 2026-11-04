package com.rayan.w204dash.data;

/** Base commune : donnees protegees par un verrou + etat. */
public abstract class BaseSource implements DataSource {

    protected final VehicleData data = new VehicleData();
    private volatile int state = STATE_OFF;
    private volatile String status = "Arrêtée";

    @Override
    public void snapshot(VehicleData out) {
        synchronized (data) {
            out.copyFrom(data);
        }
    }

    protected void setState(int s, String text) {
        state = s;
        status = text;
    }

    @Override
    public int getState() {
        return state;
    }

    @Override
    public String getStatus() {
        return status;
    }

    @Override
    public boolean canToggleOpenables() {
        return false;
    }

    @Override
    public void toggleOpenable(int index) {
        // non supporte par defaut
    }
}
