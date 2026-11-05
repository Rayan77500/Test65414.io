package com.rayan.w204dash.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rayan.w204dash.App;
import com.rayan.w204dash.MainActivity;
import com.rayan.w204dash.data.VehicleData;

/** Onglet Ouvrants : 4 portes, capot, coffre (ouvert / ferme). */
public class OpenablesTab extends LinearLayout implements VehiclePage.Tab {

    private final CarTopView top;
    private final TextView[] pills = new TextView[VehicleData.OPENABLES];
    private final TextView note;
    private final TextView summary;

    public OpenablesTab(MainActivity a) {
        super(a);
        setOrientation(HORIZONTAL);
        LinearLayout left = Ui.card(a, "Vue de dessus");
        top = new CarTopView(a, false);
        top.setOnOpenableTap(new CarTopView.OnOpenableTap() {
            @Override
            public void onTap(int index) {
                toggle(index);
            }
        });
        left.addView(top, Ui.weight(1, false));
        summary = Ui.text(a, "", 18, Theme.text, Ui.MEDIUM);
        summary.setGravity(Gravity.CENTER);
        left.addView(summary, Ui.lp(-1, -2));
        addView(left, Ui.weight(1, true));

        LinearLayout right = Ui.card(a, "État des ouvrants");
        int[] order = {VehicleData.DOOR_FL, VehicleData.DOOR_FR, VehicleData.DOOR_RL, VehicleData.DOOR_RR,
                VehicleData.HOOD, VehicleData.TRUNK};
        for (final int i : order) {
            LinearLayout row = Ui.row(a);
            row.setBackgroundDrawable(Ui.pressable(14, 0x08FFFFFF));
            row.setPadding(Ui.dp(14), 0, Ui.dp(10), 0);
            row.setClickable(true);
            row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    toggle(i);
                }
            });
            IconView ic = new IconView(a, i >= VehicleData.HOOD ? Icons.CAR : Icons.DOOR, Theme.text2);
            row.addView(ic, Ui.lp(Ui.dp(26), Ui.dp(26)));
            TextView name = Ui.text(a, VehicleData.openableName(i), 19, Theme.text, Ui.REGULAR);
            LinearLayout.LayoutParams nlp = Ui.lp(0, -2);
            nlp.weight = 1;
            nlp.leftMargin = Ui.dp(12);
            row.addView(name, nlp);
            pills[i] = Ui.pill(a, Fmt.NA, Theme.UNKNOWN);
            pills[i].setMinWidth(Ui.dp(96));
            row.addView(pills[i], Ui.lp(-2, -2));
            LinearLayout.LayoutParams rlp = Ui.weight(1, false);
            rlp.bottomMargin = Ui.dp(6);
            right.addView(row, rlp);
        }
        note = Ui.text(a, "", 13, Theme.text2, Ui.REGULAR);
        note.setPadding(0, Ui.dp(6), 0, 0);
        right.addView(note, Ui.lp(-1, -2));
        LinearLayout.LayoutParams rp = Ui.weight(1.15f, true);
        rp.leftMargin = Ui.dp(14);
        addView(right, rp);
    }

    private void toggle(int i) {
        App.get().repository().toggleFromUser(i);
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public void onData(VehicleData d) {
        boolean demo = App.get().repository().isDemo();
        VehicleData shown = new VehicleData();
        shown.copyFrom(d);
        int openCount = 0, known = 0;
        for (int i = 0; i < VehicleData.OPENABLES; i++) {
            int v = d.open[i];
            if (v == VehicleData.UNKNOWN) {
                Ui.setPill(pills[i], Fmt.NA, Theme.UNKNOWN);
            } else {
                known++;
                if (v == 1) openCount++;
                Ui.setPill(pills[i], v == 1 ? "OUVERT" : "FERMÉ", v == 1 ? Theme.WARN : Theme.OK);
            }
        }
        top.setPaintColor(App.get().prefs().carColor());
        top.update(shown);
        if (known == 0) {
            summary.setText("État des ouvrants non disponible");
            summary.setTextColor(Theme.text2);
        } else if (openCount == 0) {
            summary.setText("Tout est fermé");
            summary.setTextColor(Theme.OK);
        } else {
            summary.setText(openCount + (openCount > 1 ? " ouvrants ouverts" : " ouvrant ouvert"));
            summary.setTextColor(Theme.WARN);
        }
        note.setText(demo ? "Mode démo : touchez une ligne ou la voiture pour simuler une ouverture."
                : "Les états viennent de la source « " + App.get().repository().source().getName()
                + " ». « -- » = information non fournie par la voiture.");
    }

    @Override
    public void onShown() {
    }

    @Override
    public void onHidden() {
    }
}
