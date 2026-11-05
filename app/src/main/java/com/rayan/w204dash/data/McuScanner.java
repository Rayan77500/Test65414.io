package com.rayan.w204dash.data;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import com.rayan.w204dash.DiagLog;

import java.util.Arrays;
import java.util.Map;
import java.util.TreeMap;

/**
 * Diagnostic : ecoute TOUS les canaux du microcontroleur de l'ecran
 * (Carocean/Yecon) pendant un temps limite et note chaque message dans le
 * journal, pour trouver ou passent la clim, le kilometrage, le carburant...
 * Ne fait que s'abonner (aucune commande n'est envoyee a la voiture).
 */
public final class McuScanner {

    private static final String TAG = "MCU-scan";
    private static final int MAX_PER_CODE = 12;
    private static final int MAX_LINES = 500;

    public interface Done {
        void onDone(String summary);
    }

    private final Context ctx;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Map<Integer, Integer> counts = new TreeMap<Integer, Integer>();
    private final Map<Integer, byte[]> last = new TreeMap<Integer, byte[]>();
    private final Map<Integer, Integer> logged = new TreeMap<Integer, Integer>();
    private Messenger service;
    private Messenger me;
    private boolean bound;
    private int lines;
    private Done done;
    private static volatile boolean running;

    public McuScanner(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    public static boolean isRunning() {
        return running;
    }

    static int[] allCodes() {
        int[] c = new int[255];
        for (int i = 0; i < c.length; i++) c[i] = i + 1;
        return c;
    }

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = new Messenger(binder);
            send(McuDoorLink.MSG_REGISTER);
            DiagLog.i(TAG, "Écoute de tous les canaux du microcontrôleur (1 à 255)…");
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    /** @return faux si le service du microcontroleur est absent */
    public boolean start(int seconds, Done d) {
        if (running) return false;
        done = d;
        me = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                onMessage(msg);
            }
        });
        Intent it = new Intent(McuDoorLink.SERVICE_ACTION);
        it.setPackage(McuDoorLink.SERVICE_PACKAGE);
        try {
            bound = ctx.bindService(it, conn, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            bound = false;
            DiagLog.e(TAG, "Liaison refusée", t);
        }
        if (!bound) {
            DiagLog.w(TAG, "Service du microcontrôleur introuvable : écoute impossible");
            return false;
        }
        running = true;
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                stop();
            }
        }, seconds * 1000L);
        return true;
    }

    public void stop() {
        if (!running) return;
        running = false;
        if (service != null) send(McuDoorLink.MSG_UNREGISTER);
        if (bound) {
            try {
                ctx.unbindService(conn);
            } catch (Throwable ignored) {
                // deja deliee
            }
        }
        bound = false;
        service = null;
        StringBuilder sb = new StringBuilder("Écoute terminée. Messages reçus par canal :");
        if (counts.isEmpty()) sb.append(" aucun");
        for (Map.Entry<Integer, Integer> e : counts.entrySet()) {
            sb.append(' ').append(String.format("0x%02X", e.getKey())).append('=').append(e.getValue());
        }
        String s = sb.toString();
        DiagLog.i(TAG, s);
        if (done != null) done.onDone(s);
    }

    private void send(int what) {
        Messenger s = service;
        if (s == null) return;
        Message m = Message.obtain(null, what);
        Bundle b = new Bundle();
        b.putIntArray("cmdcode", allCodes());
        m.setData(b);
        m.replyTo = me;
        try {
            s.send(m);
        } catch (Throwable t) {
            DiagLog.e(TAG, "Envoi au microcontrôleur", t);
        }
    }

    void onMessage(Message msg) {
        if (!running || msg.what != McuDoorLink.MSG_DATA) return;
        Bundle b = msg.getData();
        if (b == null) return;
        int code = b.getInt("cmdcode", -1);
        byte[] data = b.getByteArray("data");
        Integer c = counts.get(code);
        counts.put(code, c == null ? 1 : c + 1);
        byte[] prev = last.get(code);
        if (data != null && prev != null && Arrays.equals(prev, data)) return; // inchange
        last.put(code, data);
        Integer n = logged.get(code);
        int nn = n == null ? 0 : n;
        if (nn >= MAX_PER_CODE || lines >= MAX_LINES) return;
        logged.put(code, nn + 1);
        lines++;
        String keys = b.keySet().toString();
        DiagLog.i(TAG, String.format("canal 0x%02X (%d) : ", code, code)
                + (data == null ? "(pas d'octets) clés=" + keys : McuDoorLink.hex(data) + " [" + data.length + " octets]"));
    }
}
