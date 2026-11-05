package com.rayan.w204dash.compat;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

import java.io.OutputStream;

/**
 * Tous les appels aux API Android posterieures a la 4.1 (API 16) sont
 * regroupes ici et proteges par un test de version. Le reste du code est
 * compile contre l'API 16 pour garantir la compatibilite avec les vieux ecrans.
 */
public final class Compat {

    private Compat() {
    }

    public static boolean hasPermission(Context ctx, String perm) {
        if (Build.VERSION.SDK_INT < 23) return true;
        return ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED;
    }

    /** Android 12+ : autorisation "Appareils a proximite" pour le Bluetooth. */
    public static boolean hasBluetoothPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < 31) return true;
        return hasPermission(ctx, "android.permission.BLUETOOTH_CONNECT");
    }

    public static void requestPermissions(Activity a, String[] perms, int code) {
        if (Build.VERSION.SDK_INT >= 23) {
            a.requestPermissions(perms, code);
        }
    }

    /** Enregistre un recepteur pour des broadcasts emis par d'autres applis. */
    public static Intent registerReceiverExported(Context ctx, BroadcastReceiver r, IntentFilter f) {
        if (Build.VERSION.SDK_INT >= 33) {
            return ctx.registerReceiver(r, f, Context.RECEIVER_EXPORTED);
        }
        return ctx.registerReceiver(r, f);
    }

    /** Envoie une touche multimedia (lecture/pause/suivant) au lecteur actif. */
    public static void sendMediaKey(Context ctx, int keyCode) {
        long now = SystemClock.uptimeMillis();
        KeyEvent down = new KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0);
        KeyEvent up = new KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0);
        if (Build.VERSION.SDK_INT >= 19) {
            AudioManager am = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.dispatchMediaKeyEvent(down);
                am.dispatchMediaKeyEvent(up);
                return;
            }
        }
        Intent i1 = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i1.putExtra(Intent.EXTRA_KEY_EVENT, down);
        ctx.sendOrderedBroadcast(i1, null);
        Intent i2 = new Intent(Intent.ACTION_MEDIA_BUTTON);
        i2.putExtra(Intent.EXTRA_KEY_EVENT, up);
        ctx.sendOrderedBroadcast(i2, null);
    }

    /** Plein ecran immersif (masque les barres systeme) sur Android 4.4+. */
    public static void immersive(Window w) {
        View d = w.getDecorView();
        if (Build.VERSION.SDK_INT >= 19) {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        } else {
            d.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);
        }
    }

    /**
     * Android 10+ : enregistre un fichier dans le dossier Telechargements.
     *
     * @return chemin lisible ou null si non supporte
     */
    public static String saveToDownloads(Context ctx, String name, String mime, byte[] data) throws Exception {
        if (Build.VERSION.SDK_INT < 29) return null;
        ContentResolver cr = ctx.getContentResolver();
        ContentValues v = new ContentValues();
        v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
        v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
        v.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download");
        Uri uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (uri == null) throw new Exception("MediaStore a refusé le fichier");
        OutputStream os = cr.openOutputStream(uri);
        if (os == null) throw new Exception("Écriture impossible");
        try {
            os.write(data);
        } finally {
            os.close();
        }
        return "Téléchargements/" + name;
    }

    /** Ouvre les reglages "Acces aux notifications" (Android 4.3+). */
    public static Intent notificationAccessIntent() {
        return new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
    }

    public static boolean supportsMediaSessions() {
        return Build.VERSION.SDK_INT >= 21;
    }
}
