package com.rayan.w204dash;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Journal de diagnostic partage par toute l'application (affiche dans
 * l'ecran Diagnostic et enregistrable dans un fichier).
 */
public final class DiagLog {

    public interface Listener {
        void onLogLine(String line);
    }

    private static final int MAX = 800;
    private static final ArrayList<String> LINES = new ArrayList<String>();
    private static final ArrayList<Listener> LISTENERS = new ArrayList<Listener>();
    private static Handler main;

    private DiagLog() {
    }

    public static void i(String tag, String msg) {
        add("I", tag, msg);
    }

    public static void w(String tag, String msg) {
        add("W", tag, msg);
    }

    public static void e(String tag, String msg, Throwable t) {
        add("E", tag, msg + (t != null ? " : " + t.getClass().getSimpleName() + " " + t.getMessage() : ""));
    }

    private static void add(String level, String tag, String msg) {
        final String line = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(new Date())
                + " " + level + " [" + tag + "] " + msg;
        try {
            Log.i("W204Dash", line);
        } catch (Throwable ignored) {
            // tests JVM
        }
        synchronized (LINES) {
            LINES.add(line);
            if (LINES.size() > MAX) {
                LINES.subList(0, LINES.size() - MAX).clear();
            }
        }
        dispatch(line);
    }

    private static void dispatch(final String line) {
        final Listener[] ls;
        synchronized (LISTENERS) {
            if (LISTENERS.isEmpty()) {
                return;
            }
            ls = LISTENERS.toArray(new Listener[LISTENERS.size()]);
        }
        try {
            if (main == null) {
                main = new Handler(Looper.getMainLooper());
            }
        } catch (Throwable t) {
            return;
        }
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : ls) {
                    l.onLogLine(line);
                }
            }
        });
    }

    public static List<String> snapshot() {
        synchronized (LINES) {
            return new ArrayList<String>(LINES);
        }
    }

    public static void clear() {
        synchronized (LINES) {
            LINES.clear();
        }
    }

    public static void addListener(Listener l) {
        synchronized (LISTENERS) {
            if (!LISTENERS.contains(l)) {
                LISTENERS.add(l);
            }
        }
    }

    public static void removeListener(Listener l) {
        synchronized (LISTENERS) {
            LISTENERS.remove(l);
        }
    }
}
