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

import java.util.Map;

/**
 * Lecture directe de l'etat des ouvrants aupres du microcontroleur (MCU) des
 * ecrans "Carocean / Yecon" (UIS8581...), comme le fait l'appli CAN d'origine
 * (com.can.activity) :
 * <ul>
 * <li>liaison au service "com.carocean.mcuservice" de l'appli com.carocean.mcuserver
 * (echange de messages Android "Messenger") ;</li>
 * <li>abonnement (message 0x100, Bundle "cmdcode" = {0x12}) ;</li>
 * <li>reception de messages 0x102 : Bundle "cmdcode" (0x12) + "data" (octets).
 * L'octet n°3 est un masque : 0x80 porte AVG, 0x40 AVD, 0x20 ARG, 0x10 ARD,
 * 0x08 coffre, 0x04 capot (1 = ouvert).</li>
 * </ul>
 * Les proprietes persist.sys.front.door / rear.door inversent gauche et droite
 * (meme regle que l'appli d'origine).
 */
public class McuDoorLink {

    public static final String SERVICE_ACTION = "com.carocean.mcuservice";
    public static final String SERVICE_PACKAGE = "com.carocean.mcuserver";
    static final int MSG_REGISTER = 0x100;
    static final int MSG_UNREGISTER = 0x101;
    static final int MSG_DATA = 0x102;
    static final int CMD_DOORS = 0x12;
    private static final String TAG = "MCU";

    private final Context ctx;
    private final VehicleData target;
    private final Messenger me;
    private Messenger service;
    private boolean bound;
    private boolean swapFront;
    private boolean swapRear;
    private volatile String state = "non démarrée";
    private int received;

    private final ServiceConnection conn = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            service = new Messenger(binder);
            state = "connectée au microcontrôleur, en attente d'un mouvement de porte";
            DiagLog.i(TAG, "Service " + SERVICE_ACTION + " connecté : abonnement à l'état des portes (0x12)");
            send(MSG_REGISTER);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            state = "liaison perdue (le système la rétablira)";
            DiagLog.w(TAG, "Service du microcontrôleur déconnecté");
        }
    };

    /** @param target donnees a mettre a jour (protegees par synchronized(target)) */
    public McuDoorLink(Context ctx, VehicleData target) {
        this.ctx = ctx.getApplicationContext();
        this.target = target;
        me = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                onMessage(msg);
            }
        });
    }

    /** @return vrai si la liaison a pu etre demandee */
    public boolean start() {
        Map<String, String> p = SysProps.readAll();
        swapFront = isOn(p.get("persist.sys.front.door"));
        swapRear = isOn(p.get("persist.sys.rear.door"));
        Intent it = new Intent(SERVICE_ACTION);
        it.setPackage(SERVICE_PACKAGE);
        try {
            bound = ctx.bindService(it, conn, Context.BIND_AUTO_CREATE);
        } catch (Throwable t) {
            bound = false;
            state = "refusée par le système (" + t.getClass().getSimpleName() + ")";
            DiagLog.e(TAG, "Liaison au microcontrôleur refusée", t);
            return false;
        }
        if (!bound) {
            state = "service du microcontrôleur absent (écran non Carocean/Yecon)";
            DiagLog.i(TAG, "Pas de service " + SERVICE_ACTION + " sur cet écran");
        } else {
            state = "connexion au microcontrôleur…";
        }
        return bound;
    }

    public void stop() {
        if (service != null) send(MSG_UNREGISTER);
        if (bound) {
            try {
                ctx.unbindService(conn);
            } catch (Throwable ignored) {
                // deja deliee
            }
        }
        bound = false;
        service = null;
        state = "arrêtée";
    }

    public String state() {
        return state;
    }

    private static boolean isOn(String v) {
        if (v == null) return false;
        try {
            return Integer.parseInt(v.trim()) != 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private void send(int what) {
        Messenger s = service;
        if (s == null) return;
        Message m = Message.obtain(null, what);
        Bundle b = new Bundle();
        b.putIntArray("cmdcode", new int[]{CMD_DOORS});
        m.setData(b);
        m.replyTo = me;
        try {
            s.send(m);
        } catch (Throwable t) {
            DiagLog.e(TAG, "Envoi au microcontrôleur", t);
        }
    }

    void onMessage(Message msg) {
        if (msg.what != MSG_DATA) return;
        Bundle b = msg.getData();
        if (b == null) return;
        int cmd = b.getInt("cmdcode", -1);
        byte[] data = b.getByteArray("data");
        if (cmd != CMD_DOORS || data == null) return;
        received++;
        if (received <= 30) DiagLog.i(TAG, "Portes (0x12) reçu : " + hex(data));
        int[] st = decode(data, swapFront, swapRear);
        if (st == null) return;
        synchronized (target) {
            System.arraycopy(st, 0, target.open, 0, VehicleData.OPENABLES);
        }
        state = "OK, état des portes reçu du microcontrôleur (" + received + " message(s))";
    }

    static String hex(byte[] d) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(d.length, 32); i++) {
            int v = d[i] & 0xFF;
            sb.append(Character.forDigit(v >> 4, 16)).append(Character.forDigit(v & 15, 16)).append(' ');
        }
        return sb.toString().trim().toUpperCase(java.util.Locale.US);
    }

    /**
     * Decode un message 0x12 du microcontroleur (meme regle que l'appli CAN d'origine).
     *
     * @return etats des 6 ouvrants (1 ouvert, 0 ferme) ou null si message trop court
     */
    public static int[] decode(byte[] data, boolean swapFront, boolean swapRear) {
        if (data == null || data.length < 4) return null;
        int raw = data[3] & 0xFF;
        int v = raw;
        if (swapFront) {
            v = (raw & 0x80) != 0 ? v | 0x40 : v & ~0x40;
            v = (raw & 0x40) != 0 ? v | 0x80 : v & ~0x80;
        }
        if (swapRear) {
            v = (raw & 0x20) != 0 ? v | 0x10 : v & ~0x10;
            v = (raw & 0x10) != 0 ? v | 0x20 : v & ~0x20;
        }
        int[] st = new int[VehicleData.OPENABLES];
        st[VehicleData.DOOR_FL] = (v >> 7) & 1;
        st[VehicleData.DOOR_FR] = (v >> 6) & 1;
        st[VehicleData.DOOR_RL] = (v >> 5) & 1;
        st[VehicleData.DOOR_RR] = (v >> 4) & 1;
        st[VehicleData.TRUNK] = (v >> 3) & 1;
        st[VehicleData.HOOD] = (v >> 2) & 1;
        return st;
    }
}
