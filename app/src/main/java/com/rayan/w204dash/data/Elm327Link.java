package com.rayan.w204dash.data;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.Files;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;

/** Liaison serie Bluetooth (SPP) avec un adaptateur ELM327. */
public class Elm327Link {

    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String TAG = "OBD2";

    private BluetoothSocket socket;
    private InputStream in;
    private OutputStream out;

    public boolean isOpen() {
        return socket != null;
    }

    public void connect(String address) throws IOException {
        BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) throw new IOException("Pas de Bluetooth sur cet appareil");
        if (!ad.isEnabled()) throw new IOException("Bluetooth désactivé");
        BluetoothDevice dev = ad.getRemoteDevice(address);
        try {
            ad.cancelDiscovery();
        } catch (Exception ignored) {
            // permission de scan absente : sans importance
        }
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            BluetoothSocket s = null;
            try {
                if (attempt == 0) {
                    s = dev.createRfcommSocketToServiceRecord(SPP);
                } else if (attempt == 1) {
                    s = dev.createInsecureRfcommSocketToServiceRecord(SPP);
                } else {
                    // astuce connue pour certains clones ELM327
                    Method m = dev.getClass().getMethod("createRfcommSocket", int.class);
                    s = (BluetoothSocket) m.invoke(dev, 1);
                }
                DiagLog.i(TAG, "Connexion (méthode " + (attempt + 1) + ") à " + address);
                s.connect();
                socket = s;
                in = s.getInputStream();
                out = s.getOutputStream();
                return;
            } catch (IOException e) {
                last = e;
                Files.closeQuietly(s);
            } catch (Exception e) {
                last = new IOException(e.toString());
                Files.closeQuietly(s);
            }
        }
        throw last != null ? last : new IOException("Connexion impossible");
    }

    /**
     * Envoie une commande et lit la reponse jusqu'a l'invite '>'.
     */
    public String send(String cmd, long timeoutMs) throws IOException {
        if (socket == null) throw new IOException("Non connecté");
        // vide ce qui reste dans le tampon
        while (in.available() > 0) {
            //noinspection ResultOfMethodCallIgnored
            in.read();
        }
        out.write((cmd + "\r").getBytes("US-ASCII"));
        out.flush();
        StringBuilder sb = new StringBuilder();
        long end = System.currentTimeMillis() + timeoutMs;
        byte[] buf = new byte[256];
        while (System.currentTimeMillis() < end) {
            int n = in.available();
            if (n > 0) {
                int r = in.read(buf, 0, Math.min(buf.length, n));
                for (int i = 0; i < r; i++) {
                    char c = (char) (buf[i] & 0xFF);
                    if (c == '>') {
                        return sb.toString().trim();
                    }
                    if (c != 0) sb.append(c);
                }
            } else {
                try {
                    Thread.sleep(8);
                } catch (InterruptedException e) {
                    throw new IOException("Interrompu");
                }
            }
        }
        if (sb.length() == 0) throw new IOException("Pas de réponse à " + cmd);
        return sb.toString().trim();
    }

    public void close() {
        Files.closeQuietly(in);
        Files.closeQuietly(out);
        Files.closeQuietly(socket);
        socket = null;
        in = null;
        out = null;
    }
}
