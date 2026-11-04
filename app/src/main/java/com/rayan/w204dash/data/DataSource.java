package com.rayan.w204dash.data;

import android.content.Context;

/**
 * Une source de donnees vehicule. Trois implementations :
 * {@link DemoDataSource} (valeurs simulees), {@link Obd2DataSource}
 * (adaptateur ELM327 Bluetooth) et {@link HeadUnitDataSource} (boitier CAN
 * de l'ecran). {@link CompositeDataSource} combine OBD2 + ecran.
 */
public interface DataSource {

    int STATE_OFF = 0;
    int STATE_CONNECTING = 1;
    int STATE_OK = 2;
    int STATE_ERROR = 3;

    /** Nom affiche (ex. "Démo"). */
    String getName();

    void start(Context ctx);

    void stop();

    /** Copie les valeurs connues dans out (les autres restent inconnues). */
    void snapshot(VehicleData out);

    int getState();

    /** Texte d'etat en francais (ex. "Connecté à OBDII"). */
    String getStatus();

    /** Vrai si la source accepte de simuler l'ouverture des ouvrants (mode demo). */
    boolean canToggleOpenables();

    void toggleOpenable(int index);

    /**
     * Test de connexion (appele hors du fil principal). Ecrit le deroulement
     * ligne par ligne dans report.
     */
    void runConnectionTest(Context ctx, TestReport report);

    /** Reception des lignes d'un test de connexion. */
    interface TestReport {
        void line(String text);
    }
}
