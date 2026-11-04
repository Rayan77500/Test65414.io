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
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                String where = Compat.saveToDownloads(getContext(), name, "text/plain", data);
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
            act.requestPermission("android.permission.WRITE_EXTERNAL_STORAGE", new Runnable() {
                @Override
                public void run() {
                    save();
                }
            });
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
