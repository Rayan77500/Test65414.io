package com.rayan.w204dash;

import android.app.Application;

import com.rayan.w204dash.data.DataRepository;
import com.rayan.w204dash.media.MediaHub;
import com.rayan.w204dash.store.MaintenanceStore;

/**
 * Point d'entree de l'application : cree les objets partages (reglages,
 * donnees vehicule, musique, entretien) une seule fois.
 */
public class App extends Application {

    private static App instance;

    private Prefs prefs;
    private DataRepository repository;
    private MediaHub media;
    private MaintenanceStore maintenance;

    public static App get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        CrashLog.install(this);
        prefs = new Prefs(this);
        DiagLog.i("App", "Démarrage W204 Dash " + BuildInfo.VERSION + " sur " + BuildInfo.androidVersion());
        repository = new DataRepository(this, prefs);
        media = new MediaHub(this);
        maintenance = new MaintenanceStore(this);
    }

    public Prefs prefs() {
        return prefs;
    }

    public DataRepository repository() {
        return repository;
    }

    public MediaHub media() {
        return media;
    }

    public MaintenanceStore maintenance() {
        return maintenance;
    }
}
