package com.rayan.w204dash.ui;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rayan.w204dash.App;
import com.rayan.w204dash.MainActivity;
import com.rayan.w204dash.Prefs;
import com.rayan.w204dash.data.DataRepository;
import com.rayan.w204dash.data.DataSource;
import com.rayan.w204dash.data.VehicleData;
import com.rayan.w204dash.gl.CarGlView;
import com.rayan.w204dash.gl.ModelLoader;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Accueil : voiture 3D a manipuler au doigt + horloge, musique, infos rapides. */
public class HomePage extends Page implements DataRepository.Listener {

    private final CarGlView gl;
    private final TextView loading;
    private final TextView toast;
    private final TextView subtitle;
    private final TextView status;
    private final TextView clock;
    private final TextView date;
    private final TextView outside;
    private final TextView fuel;
    private final TextView battery;
    private final TextView coolant;
    private final MusicWidget music;
    private final boolean[] lastOpen = new boolean[VehicleData.OPENABLES];
    private boolean modelLoaded;

    private final Runnable clockTick = new Runnable() {
        @Override
        public void run() {
            updateClock();
            postDelayed(this, 1000);
        }
    };
    private final Runnable hideToast = new Runnable() {
        @Override
        public void run() {
            toast.animate().alpha(0f).setDuration(250).start();
        }
    };

    public HomePage(MainActivity a) {
        super(a);
        final Prefs prefs = App.get().prefs();
        LinearLayout root = Ui.row(a);
        root.setGravity(Gravity.NO_GRAVITY);

        // ---- zone 3D
        FrameLayout stage = new FrameLayout(a);
        gl = new CarGlView(a);
        gl.renderer().setTheme(Theme.glBg, Theme.accent);
        gl.renderer().setPaint(prefs.carColor());
        gl.renderer().setAutoRotate(prefs.autoRotate());
        gl.setOnPartTapListener(new CarGlView.OnPartTapListener() {
            @Override
            public void onPartTapped(int part) {
                if (part >= 0) {
                    App.get().repository().toggleFromUser(part);
                    boolean open = App.get().repository().isVisuallyOpen(part);
                    showToast(VehicleData.openableName(part) + (open ? " : ouvert" : " : fermé"));
                }
            }
        });
        gl.setVisibility(GONE); // rendu actif seulement quand l'ecran est affiche
        stage.addView(gl, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout head = Ui.column(a);
        head.setPadding(Ui.dp(22), Ui.dp(18), Ui.dp(22), 0);
        head.addView(Ui.text(a, "Classe C 180 CDI", 26, Theme.text, Ui.MEDIUM));
        subtitle = Ui.text(a, "", 15, Theme.text2, Ui.REGULAR);
        LinearLayout.LayoutParams slp = Ui.lp(-2, -2);
        slp.topMargin = Ui.dp(6);
        head.addView(subtitle, slp);
        stage.addView(head, Ui.frame(-2, -2, Gravity.TOP | Gravity.LEFT));

        status = Ui.pill(a, "Démo", Theme.accent);
        FrameLayout.LayoutParams stlp = Ui.frame(-2, -2, Gravity.TOP | Gravity.RIGHT);
        stlp.setMargins(0, Ui.dp(18), Ui.dp(18), 0);
        stage.addView(status, stlp);

        TextView hint = Ui.text(a, "Glissez pour tourner · Pincez pour zoomer · Touchez une porte, le capot ou le coffre",
                14, Theme.text3, Ui.REGULAR);
        hint.setMaxLines(2);
        FrameLayout.LayoutParams hlp = Ui.frame(-1, -2, Gravity.BOTTOM | Gravity.LEFT);
        hlp.setMargins(Ui.dp(22), 0, Ui.dp(90), Ui.dp(18));
        stage.addView(hint, hlp);

        FrameLayout reset = new FrameLayout(a);
        reset.setBackgroundDrawable(Ui.pressable(30, 0));
        IconView ri = new IconView(a, Icons.RESET, Theme.text);
        int rp = Ui.dp(14);
        ri.setPadding(rp, rp, rp, rp);
        reset.addView(ri, new FrameLayout.LayoutParams(-1, -1));
        reset.setClickable(true);
        reset.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                gl.renderer().resetView();
            }
        });
        FrameLayout.LayoutParams rlp = Ui.frame(Ui.dp(56), Ui.dp(56), Gravity.BOTTOM | Gravity.RIGHT);
        rlp.setMargins(0, 0, Ui.dp(16), Ui.dp(12));
        stage.addView(reset, rlp);

        loading = Ui.text(a, "Chargement du modèle 3D…", 18, Theme.text2, Ui.REGULAR);
        stage.addView(loading, Ui.frame(-2, -2, Gravity.CENTER));

        toast = Ui.text(a, "", 17, Theme.text, Ui.MEDIUM);
        toast.setPadding(Ui.dp(18), Ui.dp(10), Ui.dp(18), Ui.dp(10));
        toast.setBackgroundDrawable(Ui.rounded(0xCC10161F, Theme.withAlpha(Theme.accent, 0x80), 30));
        toast.setAlpha(0f);
        FrameLayout.LayoutParams tlp = Ui.frame(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        tlp.bottomMargin = Ui.dp(64);
        stage.addView(toast, tlp);

        root.addView(stage, Ui.weight(1, true));

        // ---- colonne de droite
        LinearLayout side = Ui.column(a);
        side.setPadding(0, Ui.dp(14), Ui.dp(14), Ui.dp(14));

        LinearLayout clockCard = Ui.card(a, null);
        clock = Ui.text(a, "--:--", 64, Theme.text, Ui.LIGHT);
        date = Ui.text(a, "", 16, Theme.text2, Ui.REGULAR);
        LinearLayout outRow = Ui.row(a);
        outRow.addView(new IconView(a, Icons.TEMP, Theme.accent), Ui.lp(Ui.dp(24), Ui.dp(24)));
        outside = Ui.text(a, "--", 20, Theme.text, Ui.REGULAR);
        LinearLayout.LayoutParams olp = Ui.lp(-2, -2);
        olp.leftMargin = Ui.dp(8);
        outRow.addView(outside, olp);
        TextView outLbl = Ui.text(a, "  extérieur", 15, Theme.text2, Ui.REGULAR);
        outRow.addView(outLbl);
        clockCard.addView(clock);
        LinearLayout.LayoutParams dlp = Ui.lp(-2, -2);
        dlp.topMargin = Ui.dp(6);
        clockCard.addView(date, dlp);
        LinearLayout.LayoutParams orl = Ui.lp(-1, -2);
        orl.topMargin = Ui.dp(12);
        clockCard.addView(outRow, orl);
        side.addView(clockCard, Ui.lp(-1, -2));

        music = new MusicWidget(a, App.get().media(), new Runnable() {
            @Override
            public void run() {
                act.openNotificationAccess();
            }
        });
        LinearLayout.LayoutParams mlp = Ui.lp(-1, -2);
        mlp.topMargin = Ui.dp(12);
        side.addView(music, mlp);

        LinearLayout quick = Ui.row(a);
        fuel = quickTile(quick, Icons.FUEL, "Gazole");
        battery = quickTile(quick, Icons.BATTERY, "Batterie");
        coolant = quickTile(quick, Icons.ENGINE, "Moteur");
        LinearLayout.LayoutParams qlp = Ui.weight(1, false);
        qlp.topMargin = Ui.dp(12);
        side.addView(quick, qlp);

        root.addView(side, Ui.lp(Ui.dp(340), -1));
        addView(root, new FrameLayout.LayoutParams(-1, -1));
        loadModel();
    }

    private TextView quickTile(LinearLayout parent, int icon, String label) {
        LinearLayout t = Ui.column(getContext());
        t.setGravity(Gravity.CENTER);
        t.setBackgroundDrawable(Ui.glass(18));
        t.setPadding(Ui.dp(6), Ui.dp(8), Ui.dp(6), Ui.dp(8));
        t.addView(new IconView(getContext(), icon, Theme.accent), Ui.lp(Ui.dp(26), Ui.dp(26)));
        TextView v = Ui.value(getContext(), "--", 22);
        v.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams vlp = Ui.lp(-1, -2);
        vlp.topMargin = Ui.dp(6);
        t.addView(v, vlp);
        TextView l = Ui.label(getContext(), label);
        l.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(13));
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams llp = Ui.lp(-1, -2);
        llp.topMargin = Ui.dp(4);
        t.addView(l, llp);
        LinearLayout.LayoutParams lp = Ui.weight(1, true);
        if (parent.getChildCount() > 0) lp.leftMargin = Ui.dp(10);
        parent.addView(t, lp);
        return v;
    }

    /** (Re)charge le modele 3D en arriere-plan. */
    public void loadModel() {
        modelLoaded = false;
        loading.setText("Chargement du modèle 3D…");
        loading.setVisibility(VISIBLE);
        final int rot = App.get().prefs().modelRotation();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final ModelLoader.Result r = ModelLoader.load(getContext(), rot);
                post(new Runnable() {
                    @Override
                    public void run() {
                        if (r.scene != null) {
                            gl.renderer().setScene(r.scene);
                            loading.setVisibility(GONE);
                            modelLoaded = true;
                        } else {
                            loading.setText("Modèle 3D indisponible");
                        }
                        if (r.error != null) showToast(r.error);
                    }
                });
            }
        }, "chargement-3d").start();
    }

    public void applySettings() {
        Prefs p = App.get().prefs();
        gl.renderer().setPaint(p.carColor());
        gl.renderer().setAutoRotate(p.autoRotate());
        String opts = p.options().trim();
        subtitle.setText("W204 berline · " + p.carColorName()
                + (Prefs.GEAR_AUTO.equals(p.gearbox()) ? " · Automatique" : " · Manuelle")
                + (opts.length() > 0 ? " · " + opts.replace('\n', ' ') : ""));
        subtitle.setMaxLines(2);
        subtitle.setEllipsize(TextUtils.TruncateAt.END);
        subtitle.setMaxWidth(Ui.dp(520));
    }

    private void showToast(String s) {
        toast.setText(s);
        toast.animate().cancel();
        toast.setAlpha(1f);
        removeCallbacks(hideToast);
        postDelayed(hideToast, 2200);
    }

    private void updateClock() {
        Date now = new Date();
        clock.setText(new SimpleDateFormat("HH:mm", Locale.FRANCE).format(now));
        String d = new SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(now);
        date.setText(d.length() > 0 ? Character.toUpperCase(d.charAt(0)) + d.substring(1) : d);
    }

    @Override
    protected void onShow() {
        gl.setVisibility(VISIBLE);
        gl.onResume();
        applySettings();
        App.get().repository().addListener(this);
        removeCallbacks(clockTick);
        clockTick.run();
        music.onResumeCheck();
        onVehicleData(App.get().repository().current());
    }

    @Override
    protected void onHide() {
        App.get().repository().removeListener(this);
        removeCallbacks(clockTick);
        gl.onPause();
        gl.setVisibility(GONE);
    }

    @Override
    public void onActivityPause() {
        if (isShown2()) gl.onPause();
    }

    @Override
    public void onActivityResume() {
        if (isShown2()) {
            gl.onResume();
            music.onResumeCheck();
        }
    }

    @Override
    public void onVehicleData(VehicleData d) {
        DataRepository repo = App.get().repository();
        for (int i = 0; i < VehicleData.OPENABLES; i++) {
            boolean o = repo.isVisuallyOpen(i);
            if (o != lastOpen[i] || !modelLoaded) {
                lastOpen[i] = o;
                gl.renderer().setOpen(i, o);
            }
        }
        outside.setText(Fmt.temp(d.outsideTemp));
        fuel.setText(Fmt.pct(d.fuelPct));
        battery.setText(Fmt.volt(d.battery));
        coolant.setText(Float.isNaN(d.coolant) ? Fmt.NA : Fmt.num(d.coolant, 0) + "°");
        DataSource s = repo.source();
        int st = s.getState();
        int col = st == DataSource.STATE_OK ? (repo.isDemo() ? Theme.accent : Theme.OK)
                : st == DataSource.STATE_CONNECTING ? Theme.WARN : st == DataSource.STATE_ERROR ? Theme.BAD : Theme.UNKNOWN;
        String label = "● " + s.getName() + (st == DataSource.STATE_CONNECTING ? " (connexion…)"
                : st == DataSource.STATE_ERROR ? " (erreur)" : "");
        if (!label.contentEquals(status.getText())) Ui.setPill(status, label, col);
    }
}
