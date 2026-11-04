package com.rayan.w204dash.ui;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rayan.w204dash.App;
import com.rayan.w204dash.MainActivity;
import com.rayan.w204dash.Prefs;
import com.rayan.w204dash.data.VehicleData;

/**
 * Onglet Infos : vitesse, regime, temperature exterieure, batterie,
 * kilometrage, gazole, ceinture conducteur, lave-glace (+ moteur, clim, rapport).
 */
public class InfoTab extends LinearLayout implements VehiclePage.Tab {

    private final GaugeView speed;
    private final GaugeView rpm;
    private final Tile outTemp, battery, odo, fuel, belt, washer, coolant, clim, gear;

    private static final class Tile {
        final LinearLayout box;
        final IconView icon;
        final TextView value;
        final TextView sub;

        Tile(LinearLayout box, IconView icon, TextView value, TextView sub) {
            this.box = box;
            this.icon = icon;
            this.value = value;
            this.sub = sub;
        }

        void set(String v, int color, String s) {
            if (!v.contentEquals(value.getText())) value.setText(v);
            value.setTextColor(v.equals(Fmt.NA) ? Theme.text3 : color);
            if (s != null && !s.contentEquals(sub.getText())) sub.setText(s);
        }
    }

    public InfoTab(MainActivity a) {
        super(a);
        setOrientation(HORIZONTAL);
        LinearLayout gauges = Ui.card(a, null);
        gauges.setOrientation(VERTICAL);
        gauges.setPadding(Ui.dp(6), Ui.dp(6), Ui.dp(6), Ui.dp(6));
        speed = new GaugeView(a, "Vitesse", "km/h", 240, 0, 20);
        rpm = new GaugeView(a, "Régime", "tr/min", 5000, 4200, 500);
        gauges.addView(speed, Ui.weight(1, false));
        gauges.addView(rpm, Ui.weight(1, false));
        addView(gauges, Ui.weight(0.62f, true));

        LinearLayout grid = Ui.column(a);
        LinearLayout r1 = Ui.row(a), r2 = Ui.row(a), r3 = Ui.row(a);
        outTemp = tile(r1, Icons.TEMP, "Température ext.");
        battery = tile(r1, Icons.BATTERY, "Tension batterie");
        odo = tile(r1, Icons.ODOMETER, "Kilométrage");
        fuel = tile(r2, Icons.FUEL, "Niveau de gazole");
        belt = tile(r2, Icons.SEATBELT, "Ceinture");
        washer = tile(r2, Icons.WASHER, "Lave-glace");
        coolant = tile(r3, Icons.ENGINE, "Temp. moteur");
        clim = tile(r3, Icons.FAN, "Climatisation");
        gear = tile(r3, Icons.GEARBOX, "Rapport");
        grid.addView(r1, Ui.weight(1, false));
        LinearLayout.LayoutParams l2 = Ui.weight(1, false);
        l2.topMargin = Ui.dp(10);
        grid.addView(r2, l2);
        LinearLayout.LayoutParams l3 = Ui.weight(1, false);
        l3.topMargin = Ui.dp(10);
        grid.addView(r3, l3);
        LinearLayout.LayoutParams gp = Ui.weight(1.6f, true);
        gp.leftMargin = Ui.dp(14);
        addView(grid, gp);
    }

    private Tile tile(LinearLayout row, int icon, String label) {
        LinearLayout b = Ui.column(getContext());
        b.setBackgroundDrawable(Ui.glass(18));
        b.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(10), Ui.dp(10));
        LinearLayout head = Ui.row(getContext());
        IconView iv = new IconView(getContext(), icon, Theme.accent);
        head.addView(iv, Ui.lp(Ui.dp(22), Ui.dp(22)));
        TextView l = Ui.text(getContext(), label, 13, Theme.text2, Ui.REGULAR);
        l.setSingleLine(true);
        LinearLayout.LayoutParams llp = Ui.lp(0, -2);
        llp.weight = 1;
        llp.leftMargin = Ui.dp(8);
        head.addView(l, llp);
        b.addView(head, Ui.lp(-1, -2));
        View fill = new View(getContext());
        b.addView(fill, Ui.weight(1, false));
        TextView v = Ui.value(getContext(), Fmt.NA, 26);
        b.addView(v, Ui.lp(-1, -2));
        TextView s = Ui.text(getContext(), "", 12, Theme.text3, Ui.REGULAR);
        s.setSingleLine(true);
        LinearLayout.LayoutParams slp = Ui.lp(-1, -2);
        slp.topMargin = Ui.dp(3);
        b.addView(s, slp);
        LinearLayout.LayoutParams lp = Ui.weight(1, true);
        if (row.getChildCount() > 0) lp.leftMargin = Ui.dp(10);
        row.addView(b, lp);
        return new Tile(b, iv, v, s);
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public void onData(VehicleData d) {
        speed.setValue(d.speed);
        rpm.setValue(d.rpm);
        outTemp.set(Fmt.temp(d.outsideTemp), Float.isNaN(d.outsideTemp) || d.outsideTemp > 3 ? Theme.text : 0xFF8FD3FF,
                !Float.isNaN(d.outsideTemp) && d.outsideTemp <= 3 ? "Risque de verglas" : "");
        int bc = Float.isNaN(d.battery) ? Theme.text : d.battery < 11.8f ? Theme.BAD : d.battery < 12.3f ? Theme.WARN : Theme.text;
        battery.set(Fmt.volt(d.battery), bc, Float.isNaN(d.battery) ? "" : d.battery > 13.2f ? "Moteur tournant (charge)"
                : d.battery < 11.8f ? "Batterie faible" : "Moteur arrêté");
        odo.set(Fmt.km(d.odometer), Theme.text, "");
        fuel.set(Fmt.pct(d.fuelPct), !Float.isNaN(d.fuelPct) && d.fuelPct < 12 ? Theme.WARN : Theme.text,
                Fmt.liters(d.fuelPct) + (Float.isNaN(d.fuelPct) ? "" : " sur 66 L"));
        belt.set(Fmt.state(d.beltDriver, "Bouclée", "Non bouclée"), d.beltDriver == 0 ? Theme.BAD : Theme.OK, "Conducteur");
        washer.set(Fmt.state(d.washerLow, "Niveau bas", "OK"), d.washerLow == 1 ? Theme.WARN : Theme.OK, "");
        coolant.set(Fmt.temp(d.coolant), !Float.isNaN(d.coolant) && d.coolant > 105 ? Theme.BAD : Theme.text,
                !Float.isNaN(d.coolant) && d.coolant < 60 ? "Moteur froid" : "");
        String climTxt = d.acOn == VehicleData.UNKNOWN && Float.isNaN(d.climTemp) && d.fanLevel < 0 ? Fmt.NA
                : (Float.isNaN(d.climTemp) ? "" : Fmt.num(d.climTemp, 1) + " °C ")
                + (d.acOn == 1 ? "A/C" : d.acOn == 0 ? "A/C off" : "");
        clim.set(climTxt.trim().length() == 0 ? Fmt.NA : climTxt.trim(), Theme.text,
                d.fanLevel >= 0 ? "Ventilation : " + d.fanLevel : "");
        boolean auto = Prefs.GEAR_AUTO.equals(App.get().prefs().gearbox());
        gear.set(d.gear == null ? Fmt.NA : d.gear, Theme.text, auto ? "Boîte automatique" : "Boîte manuelle (estimé)");
    }

    @Override
    public void onShown() {
    }

    @Override
    public void onHidden() {
    }
}
