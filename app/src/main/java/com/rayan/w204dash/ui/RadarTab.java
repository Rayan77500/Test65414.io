package com.rayan.w204dash.ui;

import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rayan.w204dash.App;
import com.rayan.w204dash.MainActivity;
import com.rayan.w204dash.data.VehicleData;

/** Onglet Radars : distances de recul (et avant si disponible), bip optionnel. */
public class RadarTab extends LinearLayout implements VehiclePage.Tab {

    private final CarTopView view;
    private final TextView rearValue;
    private final TextView frontValue;
    private final TextView reverse;
    private final TextView info;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private ToneGenerator tone;
    private int minRear = -1;
    private boolean visible;
    private boolean beepOn;

    private final Runnable beep = new Runnable() {
        @Override
        public void run() {
            if (!visible || !beepOn || minRear < 0 || minRear >= 150) return;
            try {
                if (tone == null) tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 70);
                tone.startTone(ToneGenerator.TONE_PROP_BEEP, minRear < 30 ? 400 : 90);
            } catch (Throwable ignored) {
                // pas de son disponible
            }
            int delay = minRear < 30 ? 420 : 150 + minRear * 5;
            handler.postDelayed(this, delay);
        }
    };

    public RadarTab(MainActivity a) {
        super(a);
        setOrientation(HORIZONTAL);
        LinearLayout left = Ui.card(a, "Aide au stationnement");
        view = new CarTopView(a, true);
        left.addView(view, Ui.weight(1, false));
        addView(left, Ui.weight(1.25f, true));

        LinearLayout right = Ui.card(a, "Distances");
        right.addView(Ui.label(a, "Arrière (obstacle le plus proche)"));
        rearValue = Ui.value(a, Fmt.NA, 54);
        right.addView(rearValue, Ui.lp(-1, -2));
        right.addView(Ui.divider(a));
        right.addView(Ui.label(a, "Avant"));
        frontValue = Ui.value(a, Fmt.NA, 36);
        right.addView(frontValue, Ui.lp(-1, -2));
        right.addView(Ui.divider(a));
        reverse = Ui.text(a, "Marche arrière : --", 17, Theme.text, Ui.REGULAR);
        right.addView(reverse, Ui.lp(-1, -2));

        LinearLayout beepRow = Ui.row(a);
        TextView bl = Ui.text(a, "Bip sonore", 18, Theme.text, Ui.REGULAR);
        beepRow.addView(bl, Ui.weight(1, true));
        beepOn = App.get().prefs().radarBeep();
        beepRow.addView(new ToggleView(a, beepOn, new ToggleView.OnToggle() {
            @Override
            public void onToggle(boolean on) {
                beepOn = on;
                App.get().prefs().setRadarBeep(on);
                handler.removeCallbacks(beep);
                if (on) handler.post(beep);
            }
        }));
        LinearLayout.LayoutParams brl = Ui.lp(-1, Ui.dp(56));
        brl.topMargin = Ui.dp(10);
        right.addView(beepRow, brl);
        View filler = new View(a);
        right.addView(filler, Ui.weight(1, false));
        info = Ui.text(a, "", 13, Theme.text2, Ui.REGULAR);
        right.addView(info, Ui.lp(-1, -2));
        LinearLayout.LayoutParams rp = Ui.weight(1, true);
        rp.leftMargin = Ui.dp(14);
        addView(right, rp);
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public void onData(VehicleData d) {
        view.update(d);
        int r = VehicleData.minDistance(d.radarRear);
        int f = VehicleData.minDistance(d.radarFront);
        boolean startBeep = minRear < 0 && r >= 0;
        minRear = r;
        rearValue.setText(Fmt.distance(r));
        rearValue.setTextColor(r < 0 ? Theme.text3 : CarTopView.distanceColor(r));
        frontValue.setText(Fmt.distance(f));
        frontValue.setTextColor(f < 0 ? Theme.text3 : CarTopView.distanceColor(f));
        reverse.setText("Marche arrière : " + Fmt.state(d.reverse, "engagée", "non engagée"));
        info.setText(d.hasRadar()
                ? (App.get().repository().isDemo() ? "Mode démo : distances simulées." : "")
                : "Distances non fournies par la source actuelle. Elles ne sont lisibles que via le boîtier CAN "
                + "de l'écran (si la voiture a le Parktronic).");
        if (startBeep && beepOn && visible) {
            handler.removeCallbacks(beep);
            handler.post(beep);
        }
    }

    @Override
    public void onShown() {
        visible = true;
        handler.removeCallbacks(beep);
        if (beepOn) handler.post(beep);
    }

    @Override
    public void onHidden() {
        visible = false;
        handler.removeCallbacks(beep);
        if (tone != null) {
            tone.release();
            tone = null;
        }
    }
}
