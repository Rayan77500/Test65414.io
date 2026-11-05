package com.rayan.w204dash.data;

import java.util.Locale;
import java.util.Map;

/**
 * Interprete "a l'aveugle" les extras des broadcasts emis par le boitier CAN
 * d'un ecran Android : on cherche des noms de cles parlants (door, belt,
 * temp, fan...). Classe sans dependance Android (testee sur PC).
 */
public final class CanExtrasParser {

    private CanExtrasParser() {
    }

    static String norm(String k) {
        return k == null ? "" : k.toLowerCase(Locale.US).replaceAll("[^a-z0-9]", "");
    }

    /** Convertit une valeur en nombre (booleens -> 1/0, textes "open"/"close"...). */
    static Double num(Object v) {
        if (v == null) return null;
        if (v instanceof Boolean) return ((Boolean) v) ? 1.0 : 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof String) {
            String s = ((String) v).trim().toLowerCase(Locale.US);
            if (s.equals("open") || s.equals("opened") || s.equals("on") || s.equals("true") || s.equals("ouvert"))
                return 1.0;
            if (s.equals("close") || s.equals("closed") || s.equals("off") || s.equals("false") || s.equals("ferme"))
                return 0.0;
            try {
                return Double.parseDouble(s.replace(',', '.').replaceAll("[^0-9.\\-]", ""));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static boolean any(String k, String... words) {
        for (String w : words) {
            if (k.contains(w)) return true;
        }
        return false;
    }

    /** Index d'ouvrant designe par une cle normalisee, ou -1. */
    static int openableFromKey(String k, boolean doorContext) {
        boolean door = k.contains("door") || doorContext;
        if (any(k, "hood", "bonnet", "enginecover", "frontcover", "engineroom")) return VehicleData.HOOD;
        if (any(k, "trunk", "boot", "tailgate", "backdoor", "liftgate", "rearcover")) return VehicleData.TRUNK;
        if (!door) return -1;
        if (any(k, "frontleft", "leftfront", "doorfl", "fldoor", "lfdoor", "doorlf", "driver")) return VehicleData.DOOR_FL;
        if (any(k, "frontright", "rightfront", "doorfr", "frdoor", "rfdoor", "doorrf", "passenger", "copilot"))
            return VehicleData.DOOR_FR;
        if (any(k, "rearleft", "leftrear", "backleft", "leftback", "doorrl", "rldoor", "lrdoor", "doorlr"))
            return VehicleData.DOOR_RL;
        if (any(k, "rearright", "rightrear", "backright", "rightback", "doorrr", "rrdoor")) return VehicleData.DOOR_RR;
        if (doorContext) {
            if (k.equals("fl") || k.equals("lf")) return VehicleData.DOOR_FL;
            if (k.equals("fr") || k.equals("rf")) return VehicleData.DOOR_FR;
            if (k.equals("rl") || k.equals("lr")) return VehicleData.DOOR_RL;
            if (k.equals("rr")) return VehicleData.DOOR_RR;
        }
        return -1;
    }

    private static float scaleTemp(double v) {
        if (v > 85 || v < -50) v = v / 10.0;
        return (v >= -50 && v <= 85) ? (float) v : Float.NaN;
    }

    private static float scaleVolt(double v) {
        if (v > 3000) v = v / 1000.0;
        else if (v > 300) v = v / 100.0;
        else if (v > 30) v = v / 10.0;
        return (v > 5 && v < 32) ? (float) v : Float.NaN;
    }

    /**
     * Analyse un broadcast.
     *
     * @return nombre de valeurs reconnues et ecrites dans d
     */
    public static int parse(String action, Map<String, Object> extras, VehicleData d) {
        String act = norm(action);
        int found = 0;
        if (act.contains("backcar") || act.contains("reverse") || act.contains("rgear")) {
            if (act.endsWith("start") || act.endsWith("on") || act.endsWith("enter")) {
                d.reverse = 1;
                found++;
            } else if (act.endsWith("stop") || act.endsWith("off") || act.endsWith("exit")) {
                d.reverse = 0;
                found++;
            }
        }
        if (extras == null) return found;
        boolean doorCtx = act.contains("door");
        for (Map.Entry<String, Object> e : extras.entrySet()) {
            String k = norm(e.getKey());
            Object v = e.getValue();
            // tableaux : portes dans l'ordre AVG, AVD, ARG, ARD, capot, coffre
            if ((k.contains("door") || doorCtx) && (v instanceof int[] || v instanceof boolean[] || v instanceof byte[])) {
                int n = v instanceof int[] ? ((int[]) v).length : v instanceof boolean[] ? ((boolean[]) v).length : ((byte[]) v).length;
                for (int i = 0; i < Math.min(n, VehicleData.OPENABLES); i++) {
                    int x;
                    if (v instanceof int[]) x = ((int[]) v)[i];
                    else if (v instanceof boolean[]) x = ((boolean[]) v)[i] ? 1 : 0;
                    else x = ((byte[]) v)[i];
                    d.open[i] = x != 0 ? 1 : 0;
                    found++;
                }
                continue;
            }
            Double n = num(v);
            if (n == null) continue;
            double x = n;
            int op = openableFromKey(k, doorCtx);
            if (op >= 0) {
                d.open[op] = x != 0 ? 1 : 0;
                found++;
            } else if (k.contains("belt")) {
                if (any(k, "passenger", "copilot", "right", "fr")) d.beltPassenger = x != 0 ? 1 : 0;
                else d.beltDriver = x != 0 ? 1 : 0;
                found++;
            } else if (any(k, "washer", "washing", "wiperfluid", "glasswater", "washliquid")) {
                d.washerLow = x != 0 ? 1 : 0;
                found++;
            } else if (any(k, "outtemp", "outsidetemp", "outdoortemp", "ambienttemp", "exttemp", "externaltemp",
                    "temperatureout", "tempout", "outtemperature")) {
                d.outsideTemp = scaleTemp(x);
                found++;
            } else if (any(k, "rpm", "enginespeed", "revs", "rotatespeed")) {
                if (x >= 0 && x < 9000) {
                    d.rpm = (float) x;
                    found++;
                }
            } else if (k.endsWith("speed") && !any(k, "fan", "wind", "blow", "engine", "wiper")) {
                if (x >= 0 && x < 300) {
                    d.speed = (float) x;
                    found++;
                }
            } else if (any(k, "mileage", "odometer", "odo", "totalmile", "totaldistance")) {
                if (x >= 0 && x < 2000000) {
                    d.odometer = (float) x;
                    found++;
                }
            } else if (any(k, "fuel", "oillevel", "remainoil") || k.equals("oil")) {
                if (x >= 0 && x <= 100) {
                    d.fuelPct = (float) x;
                    found++;
                }
            } else if (any(k, "battery", "voltage", "batvolt", "batt")) {
                d.battery = scaleVolt(x);
                found++;
            } else if (any(k, "coolant", "watertemp", "enginetemp")) {
                d.coolant = scaleTemp(x);
                found++;
            } else if (any(k, "fanlevel", "windlevel", "blower", "fanspeed", "windspeed") || k.equals("fan") || k.equals("wind")) {
                if (x >= 0 && x <= 10) {
                    d.fanLevel = (int) x;
                    found++;
                }
            } else if (k.equals("ac") || k.equals("acon") || k.equals("acstate") || k.equals("aircon") || k.equals("acswitch")) {
                d.acOn = x != 0 ? 1 : 0;
                found++;
            } else if (any(k, "lefttemp", "templeft", "settemp", "drivertemp", "actemp")) {
                float t = scaleTemp(x);
                if (!Float.isNaN(t) && t >= 10 && t <= 35) {
                    d.climTemp = t;
                    found++;
                }
            } else if (any(k, "reverse", "backcar", "reversing", "rgear")) {
                d.reverse = x != 0 ? 1 : 0;
                found++;
            } else if (k.contains("radar")) {
                found += radar(k, x, d);
            }
        }
        return found;
    }

    private static int radar(String k, double x, VehicleData d) {
        boolean front = any(k, "front", "head");
        int idx = -1;
        char last = k.charAt(k.length() - 1);
        if (last >= '0' && last <= '4') {
            // radar0 -> 0 ; radar1..radar4 (numerotation la plus courante) -> 0..3
            idx = last == '0' ? 0 : last - '1';
        } else if (any(k, "leftmid", "midleft", "middleleft", "leftcenter", "lm")) {
            idx = 1;
        } else if (any(k, "rightmid", "midright", "middleright", "rightcenter", "rm")) {
            idx = 2;
        } else if (k.contains("left")) {
            idx = 0;
        } else if (k.contains("right")) {
            idx = 3;
        }
        if (idx < 0 || idx > 3 || x < 0 || x > 600) return 0;
        if (front) d.radarFront[idx] = (int) x;
        else d.radarRear[idx] = (int) x;
        return 1;
    }
}
