package com.rayan.w204dash.ui;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import com.rayan.w204dash.App;
import com.rayan.w204dash.BuildInfo;
import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.Files;
import com.rayan.w204dash.MainActivity;
import com.rayan.w204dash.Prefs;
import com.rayan.w204dash.R;
import com.rayan.w204dash.compat.Compat;
import com.rayan.w204dash.data.DataRepository;
import com.rayan.w204dash.data.DataSource;
import com.rayan.w204dash.data.VehicleData;
import com.rayan.w204dash.gl.CarScene;
import com.rayan.w204dash.gl.GlbModel;
import com.rayan.w204dash.gl.ModelLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Reglages : couleur de la voiture, options, boite de vitesses, modele 3D,
 * source de donnees (+ test de connexion), theme, musique, a propos.
 */
public class SettingsPage extends Page implements DataRepository.Listener {

    static final int[] CAR_COLORS = {0xFF8C9196, 0xFFB9BEC3, 0xFF55595E, 0xFF1C1E21, 0xFFE9EAE7, 0xFF1E2C4A,
            0xFF6A1420, 0xFF4B3A2E};
    static final String[] CAR_COLOR_NAMES = {"Gris palladium métallisé", "Argent iridium métallisé",
            "Gris ténorite métallisé", "Noir obsidienne métallisé", "Blanc calcite", "Bleu cavansite métallisé",
            "Rouge hyacinthe métallisé", "Marron dolomite métallisé"};
    static final String[] SOURCES = {Prefs.SRC_DEMO, Prefs.SRC_OBD, Prefs.SRC_CAN, Prefs.SRC_OBD_CAN};

    private final Prefs prefs;
    private final List<ColorSwatch> carSwatches = new ArrayList<ColorSwatch>();
    private final List<ColorSwatch> accentSwatches = new ArrayList<ColorSwatch>();
    private final TextView colorName;
    private final TextView modelInfo;
    private final TextView obdDevice;
    private final TextView sourceStatus;
    private final TextView testOutput;
    private final TextView musicStatus;
    private final LinearLayout obdRow;

    public SettingsPage(MainActivity a) {
        super(a);
        prefs = App.get().prefs();
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        LinearLayout cols = Ui.row(a);
        cols.setGravity(Gravity.TOP);
        cols.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));
        LinearLayout c1 = Ui.column(a);
        LinearLayout c2 = Ui.column(a);
        cols.addView(c1, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams c2lp = new LinearLayout.LayoutParams(0, -2, 1);
        c2lp.leftMargin = Ui.dp(14);
        cols.addView(c2, c2lp);
        sv.addView(cols, new ScrollView.LayoutParams(-1, -2));
        addView(sv, new FrameLayout.LayoutParams(-1, -1));

        // ===================== Voiture
        LinearLayout car = Ui.card(a, "Voiture");
        car.addView(Ui.text(a, "Couleur de la carrosserie", 17, Theme.text, Ui.REGULAR));
        LinearLayout sw = Ui.row(a);
        for (int i = 0; i < CAR_COLORS.length; i++) {
            final int idx = i;
            ColorSwatch s = new ColorSwatch(a, CAR_COLORS[i], false);
            s.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    setCarColor(CAR_COLORS[idx], CAR_COLOR_NAMES[idx]);
                }
            });
            sw.addView(s, Ui.lp(Ui.dp(50), Ui.dp(50)));
            carSwatches.add(s);
        }
        ColorSwatch custom = new ColorSwatch(a, 0, true);
        custom.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                customColor();
            }
        });
        sw.addView(custom, Ui.lp(Ui.dp(50), Ui.dp(50)));
        LinearLayout.LayoutParams swl = Ui.lp(-1, -2);
        swl.topMargin = Ui.dp(8);
        car.addView(sw, swl);
        colorName = Ui.text(a, "", 15, Theme.text2, Ui.REGULAR);
        colorName.setPadding(0, Ui.dp(6), 0, 0);
        car.addView(colorName);
        car.addView(Ui.divider(a));
        car.addView(Ui.text(a, "Boîte de vitesses", 17, Theme.text, Ui.REGULAR));
        Segmented gb = new Segmented(a, new String[]{"Manuelle", "Automatique"},
                Prefs.GEAR_AUTO.equals(prefs.gearbox()) ? 1 : 0, new Segmented.OnSelect() {
            @Override
            public void onSelect(int index) {
                prefs.setGearbox(index == 1 ? Prefs.GEAR_AUTO : Prefs.GEAR_MANUAL);
                if (App.get().repository().isDemo()) App.get().repository().restartSource();
            }
        });
        LinearLayout.LayoutParams gbl = Ui.lp(-1, -2);
        gbl.topMargin = Ui.dp(8);
        car.addView(gb, gbl);
        car.addView(Ui.divider(a));
        car.addView(Ui.text(a, "Options du véhicule (texte libre)", 17, Theme.text, Ui.REGULAR));
        final EditText opts = new EditText(a);
        opts.setText(prefs.options());
        opts.setHint("Ex. : pack Avantgarde, jantes 17\", toit ouvrant, Parktronic, attelage…");
        opts.setTextColor(Theme.text);
        opts.setHintTextColor(Theme.text3);
        opts.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(17));
        opts.setMinLines(2);
        opts.setMaxLines(5);
        opts.setGravity(Gravity.TOP | Gravity.LEFT);
        opts.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                prefs.setOptions(e.toString());
            }
        });
        car.addView(opts, Ui.lp(-1, -2));
        car.addView(Ui.text(a, BuildInfo.CAR, 13, Theme.text3, Ui.REGULAR));
        c1.addView(car, Ui.lp(-1, -2));

        // ===================== Modele 3D
        LinearLayout model = Ui.card(a, "Modèle 3D");
        modelInfo = Ui.text(a, "", 14, Theme.text2, Ui.REGULAR);
        model.addView(modelInfo, Ui.lp(-1, -2));
        LinearLayout mb = Ui.row(a);
        mb.addView(Ui.button(a, Icons.FOLDER, "Importer un .glb", new OnClickListener() {
            @Override
            public void onClick(View v) {
                act.pickModelFile();
            }
        }), Ui.weight(1, true));
        LinearLayout.LayoutParams mb2 = Ui.weight(1, true);
        mb2.leftMargin = Ui.dp(10);
        mb.addView(Ui.button(a, Icons.CUBE, "Modèle intégré", new OnClickListener() {
            @Override
            public void onClick(View v) {
                java.io.File f = ModelLoader.customFile(getContext());
                if (f.exists() && f.delete()) {
                    act.toast("Retour au modèle intégré");
                    act.home().loadModel();
                } else {
                    act.toast("Le modèle intégré est déjà utilisé");
                }
                refreshModelInfo();
            }
        }), mb2);
        LinearLayout.LayoutParams mbl = Ui.lp(-1, Ui.dp(60));
        mbl.topMargin = Ui.dp(10);
        model.addView(mb, mbl);
        LinearLayout mb3 = Ui.row(a);
        mb3.addView(Ui.button(a, Icons.ROTATE, "Tourner de 90°", new OnClickListener() {
            @Override
            public void onClick(View v) {
                prefs.setModelRotation(prefs.modelRotation() + 90);
                act.home().loadModel();
                act.toast("Modèle tourné : " + prefs.modelRotation() + "° (l'avant doit regarder vers la droite au départ)");
            }
        }), Ui.weight(1, true));
        LinearLayout.LayoutParams m3l = Ui.lp(-1, Ui.dp(60));
        m3l.topMargin = Ui.dp(10);
        model.addView(mb3, m3l);
        model.addView(toggleRow("Rotation automatique (vitrine)", prefs.autoRotate(), new ToggleView.OnToggle() {
            @Override
            public void onToggle(boolean on) {
                prefs.setAutoRotate(on);
            }
        }));
        TextView mh = Ui.text(a, "Pour que les ouvrants s'animent, nommez les pièces du .glb : door_fl, door_fr, door_rl, "
                + "door_rr, hood, trunk (ou porte…, capot, coffre). Évitez la compression Draco.", 13, Theme.text3, Ui.REGULAR);
        mh.setPadding(0, Ui.dp(8), 0, 0);
        model.addView(mh);
        LinearLayout.LayoutParams ml = Ui.lp(-1, -2);
        ml.topMargin = Ui.dp(14);
        c1.addView(model, ml);

        // ===================== Apparence
        LinearLayout look = Ui.card(a, "Apparence");
        look.addView(Ui.text(a, "Thème", 17, Theme.text, Ui.REGULAR));
        String t = prefs.theme();
        Segmented th = new Segmented(a, new String[]{"Nuit", "Noir", "Graphite"},
                Prefs.THEME_BLACK.equals(t) ? 1 : Prefs.THEME_GRAPHITE.equals(t) ? 2 : 0, new Segmented.OnSelect() {
            @Override
            public void onSelect(int index) {
                prefs.setTheme(index == 1 ? Prefs.THEME_BLACK : index == 2 ? Prefs.THEME_GRAPHITE : Prefs.THEME_NIGHT);
                act.restartForTheme();
            }
        });
        LinearLayout.LayoutParams thl = Ui.lp(-1, -2);
        thl.topMargin = Ui.dp(8);
        look.addView(th, thl);
        look.addView(Ui.divider(a));
        look.addView(Ui.text(a, "Couleur d'accent (éclairage d'ambiance)", 17, Theme.text, Ui.REGULAR));
        LinearLayout acc = Ui.row(a);
        for (int i = 0; i < Theme.ACCENTS.length; i++) {
            final int col = Theme.ACCENTS[i];
            ColorSwatch s = new ColorSwatch(a, col, false);
            s.setChecked(col == prefs.accent());
            s.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    prefs.setAccent(col);
                    act.restartForTheme();
                }
            });
            acc.addView(s, Ui.lp(Ui.dp(50), Ui.dp(50)));
            accentSwatches.add(s);
        }
        LinearLayout.LayoutParams accl = Ui.lp(-1, -2);
        accl.topMargin = Ui.dp(8);
        look.addView(acc, accl);
        LinearLayout.LayoutParams ll = Ui.lp(-1, -2);
        ll.topMargin = Ui.dp(14);
        c1.addView(look, ll);

        // ===================== Source de donnees
        LinearLayout src = Ui.card(a, "Source de données");
        int si = 0;
        for (int i = 0; i < SOURCES.length; i++) if (SOURCES[i].equals(prefs.source())) si = i;
        Segmented ss = new Segmented(a, new String[]{"Démo", "OBD2", "Écran", "Les deux"}, si, new Segmented.OnSelect() {
            @Override
            public void onSelect(int index) {
                chooseSource(SOURCES[index]);
            }
        });
        src.addView(ss, Ui.lp(-1, -2));
        TextView srcHelp = Ui.text(a, "Démo : valeurs simulées. OBD2 : adaptateur ELM327 Bluetooth branché sur la prise "
                + "OBD (sous le volant). Écran : boîtier CAN de l'autoradio (portes, ceintures, clim…). "
                + "Les deux : OBD2 pour le moteur + écran pour le reste.", 13, Theme.text3,
                Ui.REGULAR);
        srcHelp.setPadding(0, Ui.dp(8), 0, 0);
        src.addView(srcHelp);
        obdRow = Ui.row(a);
        LinearLayout.LayoutParams orl = Ui.lp(-1, Ui.dp(60));
        orl.topMargin = Ui.dp(10);
        obdRow.addView(new IconView(a, Icons.BLUETOOTH, Theme.accent), Ui.lp(Ui.dp(26), Ui.dp(26)));
        obdDevice = Ui.text(a, "", 16, Theme.text, Ui.REGULAR);
        obdDevice.setMaxLines(2);
        LinearLayout.LayoutParams odl = Ui.weight(1, true);
        odl.leftMargin = Ui.dp(10);
        obdRow.addView(obdDevice, odl);
        obdRow.addView(Ui.button(a, 0, "Choisir", new OnClickListener() {
            @Override
            public void onClick(View v) {
                pickObdDevice();
            }
        }), Ui.lp(-2, Ui.dp(56)));
        src.addView(obdRow, orl);
        sourceStatus = Ui.text(a, "", 15, Theme.text2, Ui.REGULAR);
        sourceStatus.setPadding(0, Ui.dp(10), 0, 0);
        src.addView(sourceStatus, Ui.lp(-1, -2));
        LinearLayout tb = Ui.row(a);
        tb.addView(Ui.button(a, Icons.LINK, "Tester la connexion", new OnClickListener() {
            @Override
            public void onClick(View v) {
                runTest();
            }
        }), Ui.weight(1, true));
        LinearLayout.LayoutParams tb2 = Ui.weight(1, true);
        tb2.leftMargin = Ui.dp(10);
        tb.addView(Ui.button(a, Icons.DIAG, "Diagnostic", new OnClickListener() {
            @Override
            public void onClick(View v) {
                act.showPage(3);
            }
        }), tb2);
        LinearLayout.LayoutParams tbl = Ui.lp(-1, Ui.dp(60));
        tbl.topMargin = Ui.dp(10);
        src.addView(tb, tbl);
        testOutput = Ui.text(a, "", 14, Theme.text, Typeface.MONOSPACE);
        testOutput.setPadding(Ui.dp(12), Ui.dp(10), Ui.dp(12), Ui.dp(10));
        testOutput.setBackgroundDrawable(Ui.rounded(0x33000000, 0, 12));
        testOutput.setVisibility(GONE);
        LinearLayout.LayoutParams tol = Ui.lp(-1, -2);
        tol.topMargin = Ui.dp(10);
        src.addView(testOutput, tol);
        c2.addView(src, Ui.lp(-1, -2));

        // ===================== Musique
        LinearLayout mus = Ui.card(a, "Musique");
        musicStatus = Ui.text(a, "", 15, Theme.text2, Ui.REGULAR);
        mus.addView(musicStatus, Ui.lp(-1, -2));
        LinearLayout.LayoutParams mbtn = Ui.lp(-1, Ui.dp(60));
        mbtn.topMargin = Ui.dp(10);
        mus.addView(Ui.button(a, Icons.MUSIC, "Autoriser l'affichage des titres", new OnClickListener() {
            @Override
            public void onClick(View v) {
                act.openNotificationAccess();
            }
        }), mbtn);
        LinearLayout.LayoutParams musl = Ui.lp(-1, -2);
        musl.topMargin = Ui.dp(14);
        c2.addView(mus, musl);

        // ===================== A propos
        LinearLayout about = Ui.card(a, "À propos");
        about.addView(Ui.text(a, "W204 Dash " + BuildInfo.VERSION + " — tableau de bord hors ligne, sans services Google.",
                15, Theme.text2, Ui.REGULAR));
        TextView ver = Ui.text(a, BuildInfo.androidVersion() + " · écran "
                + getResources().getDisplayMetrics().widthPixels + "×" + getResources().getDisplayMetrics().heightPixels
                + " · échelle " + Math.round(Ui.scale * 100) + " %", 13, Theme.text3, Ui.REGULAR);
        ver.setPadding(0, Ui.dp(6), 0, 0);
        about.addView(ver);
        LinearLayout.LayoutParams abl = Ui.lp(-1, -2);
        abl.topMargin = Ui.dp(14);
        c2.addView(about, abl);
        refreshAll();
    }

    private LinearLayout toggleRow(String label, boolean on, ToggleView.OnToggle l) {
        LinearLayout r = Ui.row(getContext());
        r.addView(Ui.text(getContext(), label, 17, Theme.text, Ui.REGULAR), Ui.weight(1, true));
        r.addView(new ToggleView(getContext(), on, l));
        r.setMinimumHeight(Ui.dp(56));
        LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
        lp.topMargin = Ui.dp(6);
        r.setLayoutParams(lp);
        return r;
    }

    // ------------------------------------------------------------- couleur
    private void setCarColor(int color, String name) {
        prefs.setCarColor(color, name);
        refreshAll();
    }

    private void customColor() {
        final float[] hsv = new float[3];
        Color.colorToHSV(prefs.carColor(), hsv);
        LinearLayout f = Ui.column(getContext());
        f.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), Ui.dp(10));
        final View preview = new View(getContext());
        preview.setBackgroundDrawable(Ui.rounded(prefs.carColor(), 0, 14));
        f.addView(preview, Ui.lp(-1, Ui.dp(60)));
        String[] labels = {"Teinte", "Saturation", "Luminosité"};
        final SeekBar[] bars = new SeekBar[3];
        for (int i = 0; i < 3; i++) {
            TextView l = Ui.text(getContext(), labels[i], 15, Theme.text2, Ui.REGULAR);
            l.setPadding(0, Ui.dp(12), 0, Ui.dp(4));
            f.addView(l);
            SeekBar b = new SeekBar(getContext());
            b.setMax(i == 0 ? 360 : 100);
            b.setProgress(i == 0 ? Math.round(hsv[0]) : Math.round(hsv[i] * 100));
            final int k = i;
            b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar s, int p, boolean u) {
                    hsv[k] = k == 0 ? p : p / 100f;
                    preview.setBackgroundDrawable(Ui.rounded(Color.HSVToColor(hsv), 0, 14));
                }

                @Override
                public void onStartTrackingTouch(SeekBar s) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar s) {
                }
            });
            f.addView(b, Ui.lp(-1, Ui.dp(48)));
            bars[i] = b;
        }
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle("Couleur personnalisée")
                .setView(f)
                .setPositiveButton("Appliquer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        setCarColor(Color.HSVToColor(hsv), "Couleur personnalisée");
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ------------------------------------------------------------- source
    private void chooseSource(final String id) {
        boolean needsBt = Prefs.SRC_OBD.equals(id) || Prefs.SRC_OBD_CAN.equals(id);
        prefs.setSource(id);
        if (needsBt && !Compat.hasBluetoothPermission(getContext())) {
            act.requestPermission("android.permission.BLUETOOTH_CONNECT", new Runnable() {
                @Override
                public void run() {
                    App.get().repository().restartSource();
                    if (prefs.obdAddress().length() == 0) pickObdDevice();
                    refreshAll();
                }
            });
        } else {
            App.get().repository().restartSource();
            if (needsBt && prefs.obdAddress().length() == 0) pickObdDevice();
        }
        testOutput.setVisibility(GONE);
        refreshAll();
    }

    private void pickObdDevice() {
        if (!Compat.hasBluetoothPermission(getContext())) {
            act.requestPermission("android.permission.BLUETOOTH_CONNECT", new Runnable() {
                @Override
                public void run() {
                    pickObdDevice();
                }
            });
            return;
        }
        final BluetoothAdapter ad = BluetoothAdapter.getDefaultAdapter();
        if (ad == null) {
            act.toast("Cet écran n'a pas de Bluetooth");
            return;
        }
        if (!ad.isEnabled()) {
            try {
                act.startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            } catch (Throwable e) {
                act.toast("Activez le Bluetooth dans les réglages Android");
            }
            return;
        }
        final List<BluetoothDevice> devs = new ArrayList<BluetoothDevice>();
        List<String> labels = new ArrayList<String>();
        try {
            Set<BluetoothDevice> bonded = ad.getBondedDevices();
            if (bonded != null) {
                for (BluetoothDevice d : bonded) {
                    devs.add(d);
                    String n = d.getName();
                    boolean likely = n != null && n.toUpperCase(java.util.Locale.US).matches(".*(OBD|ELM|V-LINK|VLINK|KONNWEI|VGATE|ICAR).*");
                    labels.add((likely ? "» " : "") + (n == null ? "(sans nom)" : n) + "\n" + d.getAddress());
                }
            }
        } catch (SecurityException e) {
            act.toast("Autorisation Bluetooth refusée");
            return;
        }
        labels.add("Ouvrir les réglages Bluetooth (appairer l'adaptateur, code 1234 ou 0000)…");
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle(devs.isEmpty() ? "Aucun appareil appairé" : "Adaptateur OBD2 (ELM327)")
                .setItems(labels.toArray(new String[labels.size()]), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        if (which >= devs.size()) {
                            try {
                                act.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS));
                            } catch (Throwable e) {
                                act.toast("Réglages Bluetooth introuvables");
                            }
                            return;
                        }
                        BluetoothDevice dev = devs.get(which);
                        String name;
                        try {
                            name = dev.getName();
                        } catch (SecurityException e) {
                            name = null;
                        }
                        prefs.setObdDevice(dev.getAddress(), name == null ? dev.getAddress() : name);
                        DiagLog.i("OBD2", "Adaptateur choisi : " + name + " (" + dev.getAddress() + ")");
                        String s = prefs.source();
                        if (Prefs.SRC_OBD.equals(s) || Prefs.SRC_OBD_CAN.equals(s)) App.get().repository().restartSource();
                        refreshAll();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void runTest() {
        testOutput.setVisibility(VISIBLE);
        testOutput.setText("Test en cours…\n");
        App.get().repository().runTest(new DataSource.TestReport() {
            @Override
            public void line(final String text) {
                post(new Runnable() {
                    @Override
                    public void run() {
                        CharSequence cur = testOutput.getText();
                        if (cur.toString().startsWith("Test en cours…")) testOutput.setText("");
                        testOutput.append(text + "\n");
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------- modele
    /** Appele par l'activite apres le choix d'un fichier .glb. */
    public void onModelPicked(final Uri uri) {
        act.toast("Import du modèle…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String msg;
                try {
                    byte[] data = Files.readAll(getContext().getContentResolver().openInputStream(uri));
                    GlbModel m = GlbModel.parse(data);
                    CarScene sc = CarScene.build(m, prefs.modelRotation());
                    if (sc.items.isEmpty()) throw new Exception("le fichier ne contient aucun maillage");
                    Files.write(ModelLoader.customFile(getContext()), data);
                    msg = "Modèle importé : " + sc.info;
                    DiagLog.i("3D", msg);
                    post(new Runnable() {
                        @Override
                        public void run() {
                            act.home().loadModel();
                            refreshModelInfo();
                        }
                    });
                } catch (Throwable e) {
                    msg = "Import impossible : " + e.getMessage();
                    DiagLog.e("3D", "Import du modèle", e);
                }
                final String m2 = msg;
                post(new Runnable() {
                    @Override
                    public void run() {
                        act.toast(m2);
                    }
                });
            }
        }, "import-glb").start();
    }

    private void refreshModelInfo() {
        boolean custom = ModelLoader.customFile(getContext()).exists();
        modelInfo.setText((custom ? "Modèle importé utilisé." : "Modèle intégré (provisoire) : berline W204 simplifiée.")
                + " Rotation : " + prefs.modelRotation() + "°");
    }

    private void refreshAll() {
        int cc = prefs.carColor();
        for (int i = 0; i < carSwatches.size(); i++) carSwatches.get(i).setChecked(CAR_COLORS[i] == cc);
        colorName.setText(prefs.carColorName());
        String s = prefs.source();
        boolean obd = Prefs.SRC_OBD.equals(s) || Prefs.SRC_OBD_CAN.equals(s);
        obdRow.setVisibility(obd ? VISIBLE : GONE);
        obdDevice.setText(prefs.obdAddress().length() == 0 ? "Aucun adaptateur choisi"
                : "Adaptateur : " + prefs.obdName() + "\n" + prefs.obdAddress());
        refreshModelInfo();
        App.get().media();
        boolean titles = App.get().media().canShowTitles();
        musicStatus.setText(!titles ? "Lecture / pause / suivant fonctionnent. L'affichage des titres demande Android 5 ou plus."
                : App.get().media().hasNotificationAccess() ? "Affichage des titres : autorisé"
                : "Lecture / pause / suivant fonctionnent. Pour voir le titre et l'artiste, autorisez l'accès aux "
                + "notifications pour « W204 Dash ».");
        onVehicleData(App.get().repository().current());
    }

    @Override
    protected void onShow() {
        refreshAll();
        App.get().repository().addListener(this);
    }

    @Override
    protected void onHide() {
        App.get().repository().removeListener(this);
    }

    @Override
    public void onActivityResume() {
        if (isShown2()) refreshAll();
    }

    @Override
    public void onVehicleData(VehicleData d) {
        DataSource src = App.get().repository().source();
        String st = "État : " + src.getStatus();
        if (!st.contentEquals(sourceStatus.getText())) sourceStatus.setText(st);
        int state = src.getState();
        sourceStatus.setTextColor(state == DataSource.STATE_OK ? Theme.OK : state == DataSource.STATE_ERROR ? Theme.BAD
                : state == DataSource.STATE_CONNECTING ? Theme.WARN : Theme.text2);
    }
}
