package com.rayan.w204dash;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.rayan.w204dash.compat.Compat;
import com.rayan.w204dash.ui.DiagnosticPage;
import com.rayan.w204dash.ui.HomePage;
import com.rayan.w204dash.ui.IconView;
import com.rayan.w204dash.ui.Icons;
import com.rayan.w204dash.ui.Page;
import com.rayan.w204dash.ui.SettingsPage;
import com.rayan.w204dash.ui.Theme;
import com.rayan.w204dash.ui.Ui;
import com.rayan.w204dash.ui.VehiclePage;

/**
 * Activite unique : barre de navigation a gauche (Accueil, Vehicule,
 * Reglages, Diagnostic) et ecrans a droite. Plein ecran, ecran toujours allume.
 */
public class MainActivity extends Activity {

    private static final int REQ_MODEL = 41;
    private static final int REQ_PERM = 42;
    private static final String[] NAV_LABELS = {"Accueil", "Véhicule", "Réglages", "Diagnostic"};
    private static final int[] NAV_ICONS = {Icons.HOME, Icons.CAR, Icons.SETTINGS, Icons.DIAG};

    private FrameLayout container;
    private final Page[] pages = new Page[4];
    private final LinearLayout[] navItems = new LinearLayout[4];
    private int current = -1;
    private Runnable pendingPermission;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);
        Theme.load(App.get().prefs());
        Ui.init(this);
        buildUi();
        int start = state != null ? state.getInt("page", 0) : 0;
        showPage(start);
        if (CrashLog.read() != null) {
            toast("L'application s'est fermée anormalement la dernière fois : détails dans Diagnostic");
        }
    }

    private void buildUi() {
        LinearLayout root = Ui.row(this);
        root.setGravity(Gravity.NO_GRAVITY);
        root.setBackgroundDrawable(Theme.background());

        LinearLayout nav = Ui.column(this);
        nav.setGravity(Gravity.CENTER_HORIZONTAL);
        nav.setPadding(Ui.dp(10), Ui.dp(14), Ui.dp(4), Ui.dp(14));
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout item = Ui.column(this);
            item.setGravity(Gravity.CENTER);
            item.setBackgroundDrawable(Ui.pressable(20, 0x00000000));
            item.setClickable(true);
            item.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showPage(idx);
                }
            });
            IconView iv = new IconView(this, NAV_ICONS[i], Theme.text);
            item.addView(iv, Ui.lp(Ui.dp(32), Ui.dp(32)));
            TextView t = Ui.text(this, NAV_LABELS[i], 13, Theme.text2, Ui.MEDIUM);
            t.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams tl = Ui.lp(-2, -2);
            tl.topMargin = Ui.dp(6);
            item.addView(t, tl);
            LinearLayout.LayoutParams lp = i == 3 ? Ui.lp(-1, Ui.dp(84)) : Ui.weight(1, false);
            if (i > 0) lp.topMargin = Ui.dp(8);
            if (i == 3) {
                View spacer = new View(this);
                nav.addView(spacer, Ui.weight(0.6f, false));
            }
            nav.addView(item, lp);
            navItems[i] = item;
        }
        root.addView(nav, Ui.lp(Ui.dp(104), -1));

        container = new FrameLayout(this);
        root.addView(container, Ui.weight(1, true));
        setContentView(root);
    }

    private Page create(int i) {
        switch (i) {
            case 0: return new HomePage(this);
            case 1: return new VehiclePage(this);
            case 2: return new SettingsPage(this);
            default: return new DiagnosticPage(this);
        }
    }

    public void showPage(int i) {
        if (i < 0 || i >= 4) i = 0;
        if (i == current) return;
        if (current >= 0 && pages[current] != null) {
            pages[current].hide();
            pages[current].setVisibility(View.GONE);
        }
        current = i;
        if (pages[i] == null) {
            try {
                pages[i] = create(i);
            } catch (Throwable t) {
                DiagLog.e("Interface", "Ouverture de l'écran " + NAV_LABELS[i], t);
                toast("Erreur à l'ouverture de « " + NAV_LABELS[i] + " » : voir Diagnostic");
                if (i != 3) showPage(3);
                return;
            }
            container.addView(pages[i], new FrameLayout.LayoutParams(-1, -1));
        }
        pages[i].setVisibility(View.VISIBLE);
        pages[i].setAlpha(0f);
        pages[i].animate().alpha(1f).setDuration(160).start();
        pages[i].show();
        for (int k = 0; k < 4; k++) {
            navItems[k].setSelected(k == i);
            ((IconView) navItems[k].getChildAt(0)).setColor(k == i ? Theme.accent : Theme.text);
        }
    }

    public HomePage home() {
        if (pages[0] == null) {
            pages[0] = create(0);
            pages[0].setVisibility(View.GONE);
            container.addView(pages[0], 0, new FrameLayout.LayoutParams(-1, -1));
        }
        return (HomePage) pages[0];
    }

    public void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    /** Reglages Android "Acces aux notifications" (titres de la musique). */
    public void openNotificationAccess() {
        try {
            startActivity(Compat.notificationAccessIntent());
            toast("Activez « W204 Dash » dans la liste, puis revenez");
        } catch (Throwable e) {
            toast("Ce réglage n'existe pas sur cet écran : les titres ne pourront pas s'afficher");
        }
    }

    public void pickModelFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            startActivityForResult(Intent.createChooser(i, "Choisir un modèle 3D (.glb)"), REQ_MODEL);
        } catch (ActivityNotFoundException e) {
            toast("Aucun gestionnaire de fichiers installé sur cet écran");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_MODEL && res == RESULT_OK && data != null && data.getData() != null) {
            if (pages[2] == null) {
                pages[2] = create(2);
                pages[2].setVisibility(View.GONE);
                container.addView(pages[2], new FrameLayout.LayoutParams(-1, -1));
            }
            ((SettingsPage) pages[2]).onModelPicked(data.getData());
        }
    }

    /** Demande une autorisation Android (6+) puis execute onGranted si accordee. */
    public void requestPermission(String perm, Runnable onGranted) {
        if (Compat.hasPermission(this, perm)) {
            onGranted.run();
            return;
        }
        pendingPermission = onGranted;
        Compat.requestPermissions(this, new String[]{perm}, REQ_PERM);
    }

    // pas d'@Override : la methode n'existe qu'a partir d'Android 6
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        if (req != REQ_PERM) return;
        Runnable r = pendingPermission;
        pendingPermission = null;
        if (results != null && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            if (r != null) r.run();
        } else {
            toast("Autorisation refusée : cette fonction ne pourra pas marcher");
        }
    }

    /** Applique un nouveau theme en recreant l'interface. */
    public void restartForTheme() {
        recreate();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        out.putInt("page", current < 0 ? 0 : current);
    }

    @Override
    public void onBackPressed() {
        if (current != 0) {
            showPage(0);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            try {
                Compat.immersive(getWindow());
            } catch (Throwable ignored) {
                // ecran sans barre systeme
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (Page p : pages) {
            if (p != null) p.onActivityPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (Page p : pages) {
            if (p != null) p.onActivityResume();
        }
    }

    @Override
    protected void onDestroy() {
        for (Page p : pages) {
            if (p != null) p.hide();
        }
        super.onDestroy();
    }
}
