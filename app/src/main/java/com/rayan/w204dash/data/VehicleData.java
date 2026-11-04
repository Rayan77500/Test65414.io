package com.rayan.w204dash.data;

import java.util.Arrays;

/**
 * Instantane des donnees du vehicule. Une valeur inconnue vaut NaN (nombres),
 * -1 (etats) ou null (texte) : l'interface affiche alors "--".
 */
public final class VehicleData {

    public static final int UNKNOWN = -1;

    /** Index des ouvrants. */
    public static final int DOOR_FL = 0, DOOR_FR = 1, DOOR_RL = 2, DOOR_RR = 3, HOOD = 4, TRUNK = 5;
    public static final int OPENABLES = 6;

    public float speed = Float.NaN;        // km/h
    public float rpm = Float.NaN;          // tr/min
    public float outsideTemp = Float.NaN;  // degres C
    public float battery = Float.NaN;      // volts
    public float odometer = Float.NaN;     // km
    public float fuelPct = Float.NaN;      // % du reservoir
    public float coolant = Float.NaN;      // degres C
    public float climTemp = Float.NaN;     // consigne climatisation, degres C

    public int beltDriver = UNKNOWN;       // 1 = bouclee, 0 = non bouclee
    public int beltPassenger = UNKNOWN;
    public int washerLow = UNKNOWN;        // 1 = niveau lave-glace bas, 0 = OK
    public int reverse = UNKNOWN;          // 1 = marche arriere
    public int fanLevel = UNKNOWN;         // ventilation 0..7
    public int acOn = UNKNOWN;             // climatisation
    public String gear = null;             // rapport engage

    /** -1 inconnu, 0 ferme, 1 ouvert. */
    public final int[] open = new int[OPENABLES];
    /** Distances radars en cm (-1 = inconnu). Ordre : gauche -> droite. */
    public final int[] radarRear = new int[4];
    public final int[] radarFront = new int[4];

    public VehicleData() {
        clear();
    }

    public void clear() {
        speed = rpm = outsideTemp = battery = odometer = fuelPct = coolant = climTemp = Float.NaN;
        beltDriver = beltPassenger = washerLow = reverse = fanLevel = acOn = UNKNOWN;
        gear = null;
        Arrays.fill(open, UNKNOWN);
        Arrays.fill(radarRear, UNKNOWN);
        Arrays.fill(radarFront, UNKNOWN);
    }

    public void copyFrom(VehicleData o) {
        speed = o.speed;
        rpm = o.rpm;
        outsideTemp = o.outsideTemp;
        battery = o.battery;
        odometer = o.odometer;
        fuelPct = o.fuelPct;
        coolant = o.coolant;
        climTemp = o.climTemp;
        beltDriver = o.beltDriver;
        beltPassenger = o.beltPassenger;
        washerLow = o.washerLow;
        reverse = o.reverse;
        fanLevel = o.fanLevel;
        acOn = o.acOn;
        gear = o.gear;
        System.arraycopy(o.open, 0, open, 0, OPENABLES);
        System.arraycopy(o.radarRear, 0, radarRear, 0, 4);
        System.arraycopy(o.radarFront, 0, radarFront, 0, 4);
    }

    /** Complete les valeurs inconnues de cet objet avec celles de o. */
    public void fillMissingFrom(VehicleData o) {
        if (Float.isNaN(speed)) speed = o.speed;
        if (Float.isNaN(rpm)) rpm = o.rpm;
        if (Float.isNaN(outsideTemp)) outsideTemp = o.outsideTemp;
        if (Float.isNaN(battery)) battery = o.battery;
        if (Float.isNaN(odometer)) odometer = o.odometer;
        if (Float.isNaN(fuelPct)) fuelPct = o.fuelPct;
        if (Float.isNaN(coolant)) coolant = o.coolant;
        if (Float.isNaN(climTemp)) climTemp = o.climTemp;
        if (beltDriver == UNKNOWN) beltDriver = o.beltDriver;
        if (beltPassenger == UNKNOWN) beltPassenger = o.beltPassenger;
        if (washerLow == UNKNOWN) washerLow = o.washerLow;
        if (reverse == UNKNOWN) reverse = o.reverse;
        if (fanLevel == UNKNOWN) fanLevel = o.fanLevel;
        if (acOn == UNKNOWN) acOn = o.acOn;
        if (gear == null) gear = o.gear;
        for (int i = 0; i < OPENABLES; i++) {
            if (open[i] == UNKNOWN) open[i] = o.open[i];
        }
        for (int i = 0; i < 4; i++) {
            if (radarRear[i] == UNKNOWN) radarRear[i] = o.radarRear[i];
            if (radarFront[i] == UNKNOWN) radarFront[i] = o.radarFront[i];
        }
    }

    public boolean hasRadar() {
        for (int i = 0; i < 4; i++) {
            if (radarRear[i] >= 0 || radarFront[i] >= 0) return true;
        }
        return false;
    }

    public static int minDistance(int[] r) {
        int m = Integer.MAX_VALUE;
        for (int v : r) {
            if (v >= 0 && v < m) m = v;
        }
        return m == Integer.MAX_VALUE ? UNKNOWN : m;
    }

    public static String openableName(int i) {
        switch (i) {
            case DOOR_FL: return "Porte avant gauche";
            case DOOR_FR: return "Porte avant droite";
            case DOOR_RL: return "Porte arrière gauche";
            case DOOR_RR: return "Porte arrière droite";
            case HOOD: return "Capot";
            case TRUNK: return "Coffre";
            default: return "?";
        }
    }
}
