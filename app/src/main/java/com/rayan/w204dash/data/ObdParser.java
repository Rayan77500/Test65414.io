package com.rayan.w204dash.data;

import java.util.Locale;

/**
 * Decodage des reponses ELM327 (mode 01, PIDs OBD2 standards SAE J1979).
 * Classe sans dependance Android (testee sur PC).
 */
public final class ObdParser {

    public static final int PID_COOLANT = 0x05;
    public static final int PID_RPM = 0x0C;
    public static final int PID_SPEED = 0x0D;
    public static final int PID_FUEL_LEVEL = 0x2F;
    public static final int PID_MODULE_VOLTAGE = 0x42;
    public static final int PID_AMBIENT_TEMP = 0x46;
    public static final int PID_ODOMETER = 0xA6;

    private ObdParser() {
    }

    /** Vrai si la reponse est une erreur ELM327 ou ne contient rien d'exploitable. */
    public static boolean isError(String raw) {
        if (raw == null) return true;
        String s = raw.toUpperCase(Locale.US).replace(" ", "");
        return s.contains("NODATA") || s.contains("UNABLETOCONNECT") || s.contains("CANERROR")
                || s.contains("BUSERROR") || s.contains("BUSBUSY") || s.contains("STOPPED")
                || s.contains("ERROR") || s.trim().equals("?") || s.trim().isEmpty();
    }

    /**
     * Extrait les octets de donnees d'une reponse au PID donne (mode 01).
     * Accepte l'echo, les espaces, "SEARCHING...", plusieurs calculateurs.
     *
     * @return octets A, B, C... ou null si absent
     */
    public static int[] parseMode01(String raw, int pid) {
        if (raw == null) return null;
        String s = raw.toUpperCase(Locale.US)
                .replace("SEARCHING...", "\n")
                .replace("BUS INIT...", "\n")
                .replace("OK", "\n");
        String key = "41" + hex2(pid);
        String[] lines = s.split("[\\r\\n>]+");
        for (String line : lines) {
            String h = line.replaceAll("[^0-9A-F]", "");
            // format avec numero de trame "0:" etc. ignore
            int idx = h.indexOf(key);
            if (idx < 0) continue;
            String rest = h.substring(idx + key.length());
            int n = rest.length() / 2;
            if (n == 0) continue;
            n = Math.min(n, 8);
            int[] out = new int[n];
            for (int i = 0; i < n; i++) {
                out[i] = Integer.parseInt(rest.substring(i * 2, i * 2 + 2), 16);
            }
            return out;
        }
        return null;
    }

    static String hex2(int v) {
        String h = Integer.toHexString(v & 0xFF).toUpperCase(Locale.US);
        return h.length() == 1 ? "0" + h : h;
    }

    public static float rpm(int[] b) {
        return b != null && b.length >= 2 ? ((b[0] * 256) + b[1]) / 4f : Float.NaN;
    }

    public static float speed(int[] b) {
        return b != null && b.length >= 1 ? b[0] : Float.NaN;
    }

    /** PIDs 05 (liquide de refroidissement) et 46 (temperature exterieure). */
    public static float temperature(int[] b) {
        return b != null && b.length >= 1 ? b[0] - 40 : Float.NaN;
    }

    public static float fuelPercent(int[] b) {
        return b != null && b.length >= 1 ? b[0] * 100f / 255f : Float.NaN;
    }

    public static float moduleVoltage(int[] b) {
        return b != null && b.length >= 2 ? ((b[0] * 256) + b[1]) / 1000f : Float.NaN;
    }

    public static float odometer(int[] b) {
        if (b == null || b.length < 4) return Float.NaN;
        long v = ((long) b[0] << 24) | ((long) b[1] << 16) | ((long) b[2] << 8) | b[3];
        return v / 10f;
    }

    /** Reponse a "ATRV" (ex. "12.6V") en volts. */
    public static float voltage(String raw) {
        if (raw == null) return Float.NaN;
        String s = raw.toUpperCase(Locale.US).replace(',', '.');
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{1,2}(\\.\\d{1,2})?)\\s*V").matcher(s);
        if (m.find()) {
            try {
                float v = Float.parseFloat(m.group(1));
                if (v > 3 && v < 30) return v;
            } catch (NumberFormatException ignored) {
                // ignore
            }
        }
        return Float.NaN;
    }

    /**
     * Decode la reponse a 0100 / 0120 / 0140... (PIDs supportes).
     *
     * @param base 0x00, 0x20, 0x40...
     * @param supported tableau de 256 cases rempli (index = PID)
     * @return vrai si la reponse etait valide
     */
    public static boolean parseSupported(String raw, int base, boolean[] supported) {
        int[] b = parseMode01(raw, base);
        if (b == null || b.length < 4) return false;
        for (int i = 0; i < 32; i++) {
            int byteIdx = i / 8;
            int bit = 7 - (i % 8);
            if (((b[byteIdx] >> bit) & 1) != 0) {
                int pid = base + i + 1;
                if (pid < supported.length) supported[pid] = true;
            }
        }
        return true;
    }

    /** Nom lisible d'un protocole ELM327 (reponse a ATDPN). */
    public static String protocolName(String raw) {
        if (raw == null) return "?";
        String s = raw.toUpperCase(Locale.US).replaceAll("[^0-9A-C]", "");
        if (s.isEmpty()) return raw.trim();
        char c = s.charAt(s.length() - 1);
        switch (c) {
            case '1': return "SAE J1850 PWM";
            case '2': return "SAE J1850 VPW";
            case '3': return "ISO 9141-2";
            case '4': return "ISO 14230-4 KWP (5 baud)";
            case '5': return "ISO 14230-4 KWP (rapide)";
            case '6': return "ISO 15765-4 CAN 11 bits 500 kb";
            case '7': return "ISO 15765-4 CAN 29 bits 500 kb";
            case '8': return "ISO 15765-4 CAN 11 bits 250 kb";
            case '9': return "ISO 15765-4 CAN 29 bits 250 kb";
            case 'A': return "SAE J1939 CAN";
            default: return raw.trim();
        }
    }
}
