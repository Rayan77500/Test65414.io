package com.rayan.w204dash.ui;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.rayan.w204dash.App;
import com.rayan.w204dash.BuildInfo;
import com.rayan.w204dash.CrashLog;
import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.Files;
import com.rayan.w204dash.MainActivity;
import com.rayan.w204dash.Prefs;
import com.rayan.w204dash.R;
import com.rayan.w204dash.compat.Compat;
import com.rayan.w204dash.data.HeadUnitProbe;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Ecran Diagnostic : tout ce qui est detecte sur l'ecran (plateforme,
 * applis du boitier CAN, broadcasts, ports...) + journal en direct.
 */
public class DiagnosticPage extends Page implements DiagLog.Listener {

    private final TextView report;
    private final TextView log;
    private final ScrollView logScroll;
    private boolean probing;

    public DiagnosticPage(MainActivity a) {
        super(a);
        LinearLayout root = Ui.column(a);
        root.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));

        LinearLayout bar = Ui.row(a);
        addBtn(bar, Icons.RESET, "Relancer la détection", new OnClickListener() {
            @Override
            public void onClick(View v) {
                runProbe();
            }
        });
        addBtn(bar, Icons.SAVE, "Enregistrer", new OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        addBtn(bar, Icons.PLUS, "Écouter un broadcast", new OnClickListener() {
            @Override
            public void onClick(View v) {
                addAction();
            }
        });
        addBtn(bar, Icons.FOLDER, "Exporter l'appli CAN", new OnClickListener() {
            @Override
            public void onClick(View v) {
                exportCanApp();
            }
        });
        addBtn(bar, Icons.TRASH, "Effacer le journal", new OnClickListener() {
            @Override
            public void onClick(View v) {
                DiagLog.clear();
                log.setText("");
            }
        });
        root.addView(bar, Ui.lp(-1, Ui.dp(60)));

        LinearLayout cols = Ui.row(a);
        cols.setGravity(android.view.Gravity.TOP);
        LinearLayout left = Ui.card(a, "Détecté sur cet écran");
        ScrollView rs = new ScrollView(a);
        report = Ui.text(a, "", 13, Theme.text, Typeface.MONOSPACE);
        report.setLineSpacing(0, 1.15f);
        rs.addView(report);
        left.addView(rs, Ui.weight(1, false));
        cols.addView(left, Ui.weight(1.1f, true));

        LinearLayout right = Ui.card(a, "Journal en direct");
        logScroll = new ScrollView(a);
        log = Ui.text(a, "", 12, Theme.text2, Typeface.MONOSPACE);
        log.setLineSpacing(0, 1.12f);
        logScroll.addView(log);
        right.addView(logScroll, Ui.weight(1, false));
        LinearLayout.LayoutParams rp = Ui.weight(1, true);
        rp.leftMargin = Ui.dp(14);
        cols.addView(right, rp);

        LinearLayout.LayoutParams cl = Ui.weight(1, false);
        cl.topMargin = Ui.dp(14);
        root.addView(cols, cl);
        addView(root, new FrameLayout.LayoutParams(-1, -1));
    }

    private void addBtn(LinearLayout bar, int icon, String label, OnClickListener l) {
        LinearLayout.LayoutParams lp = Ui.weight(1, true);
        if (bar.getChildCount() > 0) lp.leftMargin = Ui.dp(10);
        bar.addView(Ui.button(getContext(), icon, label, l), lp);
    }

    private String header() {
        StringBuilder sb = new StringBuilder();
        sb.append("W204 Dash ").append(BuildInfo.VERSION).append(" — ")
                .append(new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.FRANCE).format(new Date())).append('\n');
        sb.append("Source active : ").append(App.get().repository().source().getName()).append('\n');
        sb.append("État : ").append(App.get().repository().source().getStatus()).append('\n');
        Prefs p = App.get().prefs();
        if (p.obdAddress().length() > 0) sb.append("Adaptateur OBD2 : ").append(p.obdName()).append(" (")
                .append(p.obdAddress()).append(")\n");
        if (p.customCanActions().trim().length() > 0) {
            sb.append("Broadcasts ajoutés à la main :\n  ").append(p.customCanActions().trim().replace("\n", "\n  ")).append('\n');
        }
        sb.append("Lecture des fenêtres (accessibilité) : ").append(com.rayan.w204dash.data.CanWindowState.summary())
                .append('\n');
        String win = com.rayan.w204dash.data.CanWindowState.dump();
        if (win.length() > 0) {
            sb.append("\n== Dernière fenêtre de l'appli CAN ==\n")
                    .append(win.length() > 3000 ? win.substring(0, 3000) + "…" : win).append('\n');
        }
        String crash = CrashLog.read();
        if (crash != null) {
            sb.append("\n== Dernier plantage ==\n").append(crash.length() > 2500 ? crash.substring(0, 2500) + "…" : crash)
                    .append('\n');
        }
        return sb.toString();
    }

    private void showReport() {
        StringBuilder sb = new StringBuilder(header());
        HeadUnitProbe.Report r = HeadUnitProbe.last();
        sb.append('\n');
        if (r == null) {
            sb.append(probing ? "Détection en cours (jusqu'à 30 s)…" : "Touchez « Relancer la détection ».");
        } else {
            for (String s : r.lines) sb.append(s).append('\n');
        }
        report.setText(sb.toString());
    }

    private void runProbe() {
        if (probing) return;
        probing = true;
        showReport();
        new Thread(new Runnable() {
            @Override
            public void run() {
                HeadUnitProbe.run(getContext().getApplicationContext());
                post(new Runnable() {
                    @Override
                    public void run() {
                        probing = false;
                        showReport();
                        String s = App.get().prefs().source();
                        if (Prefs.SRC_CAN.equals(s) || Prefs.SRC_OBD_CAN.equals(s)) App.get().repository().restartSource();
                    }
                });
            }
        }, "diagnostic").start();
    }

    private void addAction() {
        final EditText e = new EditText(getContext());
        e.setHint("ex. com.exemple.canbus.ACTION_DOOR");
        e.setTextColor(Theme.text);
        e.setHintTextColor(Theme.text3);
        e.setSingleLine(true);
        LinearLayout f = Ui.column(getContext());
        f.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), 0);
        TextView help = Ui.text(getContext(), "Action d'un broadcast à écouter (trouvée sur un forum ou dans la liste "
                + "ci-contre). Elle sera ajoutée à la source « Écran voiture ».", 14, Theme.text2, Ui.REGULAR);
        f.addView(help);
        f.addView(e, Ui.lp(-1, -2));
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle("Écouter un broadcast")
                .setView(f)
                .setPositiveButton("Ajouter", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        String a = e.getText().toString().trim();
                        if (a.length() < 4) return;
                        Prefs p = App.get().prefs();
                        String cur = p.customCanActions().trim();
                        p.setCustomCanActions(cur.length() == 0 ? a : cur + "\n" + a);
                        DiagLog.i("CAN", "Broadcast ajouté : " + a);
                        String s = p.source();
                        if (Prefs.SRC_CAN.equals(s) || Prefs.SRC_OBD_CAN.equals(s)) {
                            App.get().repository().restartSource();
                        } else {
                            act.toast("Choisissez la source « Écran » dans Réglages pour l'écouter");
                        }
                        showReport();
                    }
                })
                .setNeutralButton("Tout effacer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        App.get().prefs().setCustomCanActions("");
                        showReport();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void save() {
        StringBuilder sb = new StringBuilder(report.getText());
        sb.append("\n== Journal ==\n");
        for (String s : DiagLog.snapshot()) sb.append(s).append('\n');
        final String name = "w204dash_diagnostic_" + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(new Date())
                + ".txt";
        final byte[] data;
        try {
            data = sb.toString().getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            return;
        }
        saveBytes(name, "text/plain", data, new Runnable() {
            @Override
            public void run() {
                save();
            }
        });
    }

    /** Enregistre un fichier dans Telechargements (ou, a defaut, le dossier de l'appli). */
    private void saveBytes(String name, String mime, byte[] data, Runnable retryAfterPermission) {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                String where = Compat.saveToDownloads(getContext(), name, mime, data);
                act.toast("Enregistré dans " + where);
                return;
            } catch (Exception e) {
                DiagLog.e("Diag", "Enregistrement dans Téléchargements", e);
            }
        } else if (Compat.hasPermission(getContext(), "android.permission.WRITE_EXTERNAL_STORAGE")) {
            try {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                //noinspection ResultOfMethodCallIgnored
                dir.mkdirs();
                File f = new File(dir, name);
                Files.write(f, data);
                act.toast("Enregistré dans " + f.getAbsolutePath());
                return;
            } catch (Exception e) {
                DiagLog.e("Diag", "Enregistrement dans Download", e);
            }
        } else {
            act.requestPermission("android.permission.WRITE_EXTERNAL_STORAGE", retryAfterPermission);
            return;
        }
        try {
            File dir = getContext().getExternalFilesDir(null);
            if (dir == null) dir = getContext().getFilesDir();
            File f = new File(dir, name);
            Files.write(f, data);
            act.toast("Enregistré dans " + f.getAbsolutePath());
        } catch (Exception e) {
            act.toast("Impossible d'enregistrer : " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ export appli CAN
    private static final long EXPORT_MAX = 70L * 1024 * 1024;
    private static final long PART_SIZE = 24L * 1024 * 1024;

    /**
     * Copie l'appli CAN de l'ecran (APK, code precompile odex/vdex, bibliotheques
     * natives) dans un .zip du dossier Telechargements, pour l'analyser sur PC
     * et comprendre comment elle recoit les infos de la voiture.
     */
    private void exportCanApp() {
        act.toast("Export de l'appli CAN en cours…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                byte[] zip = null;
                String err = null;
                try {
                    zip = buildExport();
                } catch (Throwable t) {
                    err = t.getMessage();
                    DiagLog.e("Diag", "Export de l'appli CAN", t);
                }
                final byte[] z = zip;
                final String e = err;
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (z == null) {
                            act.toast("Export impossible : " + e);
                            return;
                        }
                        String name = "w204dash_appli_can_"
                                + new SimpleDateFormat("yyyyMMdd_HHmm", Locale.FRANCE).format(new Date()) + ".zip";
                        DiagLog.i("Diag", "Export de l'appli CAN : " + (z.length / 1024) + " Ko");
                        Runnable retry = new Runnable() {
                            @Override
                            public void run() {
                                exportCanApp();
                            }
                        };
                        if (z.length <= PART_SIZE) {
                            saveBytes(name, "application/zip", z, retry);
                        } else {
                            // trop gros pour un envoi : decoupe en morceaux de 24 Mo (.001, .002...)
                            int parts = (int) ((z.length + PART_SIZE - 1) / PART_SIZE);
                            for (int i = 0; i < parts; i++) {
                                int from = (int) (i * PART_SIZE);
                                int len = (int) Math.min(PART_SIZE, z.length - from);
                                byte[] part = new byte[len];
                                System.arraycopy(z, from, part, 0, len);
                                saveBytes(name + String.format(Locale.US, ".%03d", i + 1), "application/octet-stream",
                                        part, retry);
                            }
                            act.toast("Export découpé en " + parts + " morceaux (.001, .002…) : envoyez-les tous");
                        }
                    }
                });
            }
        }, "export-can").start();
    }

    private byte[] buildExport() throws Exception {
        HeadUnitProbe.Report r = HeadUnitProbe.last();
        if (r == null) r = HeadUnitProbe.run(getContext().getApplicationContext());
        android.content.pm.PackageManager pm = getContext().getPackageManager();
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(bos);
        StringBuilder info = new StringBuilder();
        info.append("Export W204 Dash ").append(BuildInfo.VERSION).append(" - ").append(BuildInfo.androidVersion())
                .append(" - ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
                .append("Plateforme : ").append(r.platform).append("\n\n");
        long[] total = {0};
        int files = 0;
        for (String pkg : r.packages) {
            if (pkg.contains("bluetooth")) continue;
            android.content.pm.ApplicationInfo ai;
            android.content.pm.PackageInfo pi;
            try {
                ai = pm.getApplicationInfo(pkg, 0);
                pi = pm.getPackageInfo(pkg, 0);
            } catch (Exception e) {
                continue;
            }
            info.append(pkg).append(" v").append(pi.versionName).append(" (code ").append(pi.versionCode).append(")\n")
                    .append("  APK : ").append(ai.sourceDir).append('\n')
                    .append("  bibliothèques : ").append(ai.nativeLibraryDir).append('\n');
            File apk = new File(ai.sourceDir);
            // seulement le code et les mises en page (pas les images, trop lourdes)
            files += addApkCode(zos, apk, pkg + "/apk", total);
            File dir = apk.getParentFile();
            if (dir != null) {
                files += addTree(zos, new File(dir, "oat"), pkg + "/oat", total, 0);
                files += addTree(zos, new File(dir, "lib"), pkg + "/lib", total, 0);
            }
            if (ai.nativeLibraryDir != null) {
                files += addTree(zos, new File(ai.nativeLibraryDir), pkg + "/nativeLib", total, 0);
            }
        }
        info.append("\n").append(files).append(" fichier(s), ").append(total[0] / 1024).append(" Ko\n");
        zos.putNextEntry(new java.util.zip.ZipEntry("infos.txt"));
        zos.write(info.toString().getBytes("UTF-8"));
        zos.closeEntry();
        zos.close();
        if (files == 0) throw new Exception("aucun fichier lisible (appli CAN introuvable ou protégée)");
        return bos.toByteArray();
    }

    private static final long ONE_MB = 1024L * 1024;

    /** Fichiers utiles a l'analyse : code (dex/odex/vdex), bibliotheques natives moyennes, petits fichiers. */
    static boolean keepForAnalysis(String name, long size) {
        String n = name.toLowerCase(Locale.US);
        if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".webp") || n.endsWith(".gif")
                || n.endsWith(".mp3") || n.endsWith(".mp4") || n.endsWith(".ogg") || n.endsWith(".wav") || n.endsWith(".ttf")
                || n.endsWith(".otf") || n.endsWith(".art") || n.endsWith(".9.png")) return false;
        if (n.endsWith(".dex") || n.endsWith(".odex") || n.endsWith(".vdex")) return size < 40 * ONE_MB;
        if (n.endsWith(".so")) return size < 4 * ONE_MB;
        if (n.equals("androidmanifest.xml") || n.equals("resources.arsc")) return size < 12 * ONE_MB;
        if (n.startsWith("res/layout") || n.startsWith("res/xml")) return true;
        if (n.startsWith("res/")) return false;
        return size < ONE_MB;
    }

    private static int addApkCode(java.util.zip.ZipOutputStream zos, File apk, String prefix, long[] total) {
        int n = 0;
        java.util.zip.ZipFile zf = null;
        try {
            zf = new java.util.zip.ZipFile(apk);
            java.util.Enumeration<? extends java.util.zip.ZipEntry> en = zf.entries();
            byte[] buf = new byte[65536];
            while (en.hasMoreElements()) {
                java.util.zip.ZipEntry ze = en.nextElement();
                if (ze.isDirectory() || !keepForAnalysis(ze.getName(), ze.getSize())) continue;
                if (total[0] + ze.getSize() > EXPORT_MAX) continue;
                zos.putNextEntry(new java.util.zip.ZipEntry(prefix + "/" + ze.getName()));
                java.io.InputStream in = zf.getInputStream(ze);
                try {
                    int k;
                    while ((k = in.read(buf)) > 0) zos.write(buf, 0, k);
                } finally {
                    Files.closeQuietly(in);
                }
                zos.closeEntry();
                total[0] += Math.max(0, ze.getSize());
                n++;
            }
        } catch (Exception e) {
            DiagLog.e("Diag", "Lecture de " + apk, e);
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
        return n;
    }

    private static int addTree(java.util.zip.ZipOutputStream zos, File dir, String prefix, long[] total, int depth)
            throws java.io.IOException {
        File[] fs = dir.listFiles();
        if (fs == null || depth > 3) return 0;
        int n = 0;
        for (File f : fs) {
            if (f.isDirectory()) n += addTree(zos, f, prefix + "/" + f.getName(), total, depth + 1);
            else if (keepForAnalysis(f.getName(), f.length())) n += addFile(zos, f, prefix + "/" + f.getName(), total);
        }
        return n;
    }

    private static int addFile(java.util.zip.ZipOutputStream zos, File f, String entry, long[] total)
            throws java.io.IOException {
        if (!f.isFile() || !f.canRead() || total[0] + f.length() > EXPORT_MAX) return 0;
        zos.putNextEntry(new java.util.zip.ZipEntry(entry));
        java.io.InputStream in = new java.io.FileInputStream(f);
        try {
            byte[] buf = new byte[65536];
            int k;
            while ((k = in.read(buf)) > 0) zos.write(buf, 0, k);
        } finally {
            Files.closeQuietly(in);
        }
        zos.closeEntry();
        total[0] += f.length();
        return 1;
    }

    @Override
    protected void onShow() {
        List<String> lines = DiagLog.snapshot();
        StringBuilder sb = new StringBuilder();
        int from = Math.max(0, lines.size() - 300);
        for (int i = from; i < lines.size(); i++) sb.append(lines.get(i)).append('\n');
        log.setText(sb.toString());
        scrollLog();
        DiagLog.addListener(this);
        showReport();
        if (HeadUnitProbe.last() == null) runProbe();
    }

    @Override
    protected void onHide() {
        DiagLog.removeListener(this);
    }

    private void scrollLog() {
        logScroll.post(new Runnable() {
            @Override
            public void run() {
                logScroll.fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    @Override
    public void onLogLine(String line) {
        log.append(line + "\n");
        if (log.getLineCount() > 600) {
            CharSequence t = log.getText();
            log.setText(t.subSequence(t.length() / 2, t.length()));
        }
        scrollLog();
    }
}
