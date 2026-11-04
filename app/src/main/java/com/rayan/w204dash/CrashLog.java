package com.rayan.w204dash;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Enregistre le dernier plantage dans un fichier pour l'afficher dans
 * l'ecran Diagnostic au lancement suivant.
 */
public final class CrashLog {

    private static File file;

    private CrashLog() {
    }

    public static void install(Context ctx) {
        file = new File(ctx.getFilesDir(), "dernier_plantage.txt");
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    StringWriter sw = new StringWriter();
                    PrintWriter pw = new PrintWriter(sw);
                    pw.println("Plantage le " + new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.FRANCE).format(new Date())
                            + " (thread " + t.getName() + ")");
                    pw.println("Android " + android.os.Build.VERSION.RELEASE + " API " + android.os.Build.VERSION.SDK_INT
                            + " - " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
                    e.printStackTrace(pw);
                    pw.flush();
                    FileOutputStream fos = new FileOutputStream(file);
                    fos.write(sw.toString().getBytes("UTF-8"));
                    fos.close();
                } catch (Throwable ignored) {
                    // rien a faire
                }
                if (previous != null) {
                    previous.uncaughtException(t, e);
                }
            }
        });
    }

    /** Contenu du dernier plantage, ou null. */
    public static String read() {
        if (file == null || !file.exists()) {
            return null;
        }
        try {
            return Files.readText(file);
        } catch (Exception e) {
            return null;
        }
    }

    public static void clear() {
        if (file != null && file.exists()) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }
}
