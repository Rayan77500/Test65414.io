package com.rayan.w204dash;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Petites fonctions de lecture / ecriture de fichiers (compatibles Android 4.1). */
public final class Files {

    private Files() {
    }

    public static byte[] readAll(InputStream in) throws IOException {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(Math.max(1024, in.available()));
            byte[] buf = new byte[16384];
            int n;
            while ((n = in.read(buf)) > 0) {
                bos.write(buf, 0, n);
            }
            return bos.toByteArray();
        } finally {
            closeQuietly(in);
        }
    }

    public static byte[] readAll(File f) throws IOException {
        return readAll(new FileInputStream(f));
    }

    public static String readText(File f) throws IOException {
        return new String(readAll(f), "UTF-8");
    }

    public static void write(File f, byte[] data) throws IOException {
        File tmp = new File(f.getPath() + ".tmp");
        OutputStream os = new FileOutputStream(tmp);
        try {
            os.write(data);
        } finally {
            closeQuietly(os);
        }
        if (f.exists() && !f.delete()) {
            throw new IOException("Impossible de remplacer " + f);
        }
        if (!tmp.renameTo(f)) {
            throw new IOException("Impossible d'ecrire " + f);
        }
    }

    public static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // rien
            }
        }
    }
}
