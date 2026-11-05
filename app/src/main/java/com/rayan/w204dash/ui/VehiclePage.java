package com.rayan.w204dash.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.rayan.w204dash.App;
import com.rayan.w204dash.MainActivity;
import com.rayan.w204dash.data.DataRepository;
import com.rayan.w204dash.data.DataSource;
import com.rayan.w204dash.data.VehicleData;

/** Ecran Vehicule : onglets Ouvrants, Radars, Infos, Entretien. */
public class VehiclePage extends Page implements DataRepository.Listener {

    public interface Tab {
        View view();

        void onData(VehicleData d);

        void onShown();

        void onHidden();
    }

    private static final String[] NAMES = {"Ouvrants", "Radars", "Infos", "Entretien"};
    private static final int[] ICONS = {Icons.DOOR, Icons.RADAR, Icons.INFO, Icons.WRENCH};

    private final FrameLayout content;
    private final Tab[] tabs = new Tab[4];
    private final LinearLayout[] tabButtons = new LinearLayout[4];
    private final TextView source;
    private int current = -1;

    public VehiclePage(MainActivity a) {
        super(a);
        LinearLayout root = Ui.column(a);
        root.setPadding(Ui.dp(14), Ui.dp(14), Ui.dp(14), Ui.dp(14));

        LinearLayout bar = Ui.row(a);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            LinearLayout b = Ui.button(a, ICONS[i], NAMES[i], new OnClickListener() {
                @Override
                public void onClick(View v) {
                    select(idx);
                }
            });
            LinearLayout.LayoutParams lp = Ui.lp(0, Ui.dp(60));
            lp.weight = 1;
            if (i > 0) lp.leftMargin = Ui.dp(10);
            bar.addView(b, lp);
            tabButtons[i] = b;
        }
        source = Ui.pill(a, "", Theme.accent);
        source.setSingleLine(true);
        LinearLayout.LayoutParams slp = Ui.lp(-2, -2);
        slp.leftMargin = Ui.dp(12);
        slp.gravity = Gravity.CENTER_VERTICAL;
        bar.addView(source, slp);
        root.addView(bar, Ui.lp(-1, Ui.dp(60)));

        content = new FrameLayout(a);
        LinearLayout.LayoutParams clp = Ui.weight(1, false);
        clp.topMargin = Ui.dp(14);
        root.addView(content, clp);
        addView(root, new FrameLayout.LayoutParams(-1, -1));
    }

    private Tab create(int i) {
        switch (i) {
            case 0: return new OpenablesTab(act);
            case 1: return new RadarTab(act);
            case 2: return new InfoTab(act);
            default: return new MaintenanceTab(act);
        }
    }

    public void select(int i) {
        if (i == current) return;
        if (current >= 0 && tabs[current] != null) {
            tabs[current].onHidden();
            tabs[current].view().setVisibility(GONE);
        }
        current = i;
        if (tabs[i] == null) {
            tabs[i] = create(i);
            content.addView(tabs[i].view(), new FrameLayout.LayoutParams(-1, -1));
        }
        tabs[i].view().setVisibility(VISIBLE);
        tabs[i].onShown();
        tabs[i].onData(App.get().repository().current());
        for (int k = 0; k < 4; k++) tabButtons[k].setSelected(k == i);
    }

    @Override
    protected void onShow() {
        if (current < 0) select(0);
        else if (tabs[current] != null) tabs[current].onShown();
        App.get().repository().addListener(this);
        onVehicleData(App.get().repository().current());
    }

    @Override
    protected void onHide() {
        App.get().repository().removeListener(this);
        if (current >= 0 && tabs[current] != null) tabs[current].onHidden();
    }

    @Override
    public void onVehicleData(VehicleData d) {
        DataSource s = App.get().repository().source();
        String txt = s.getName();
        if (!txt.contentEquals(source.getText())) source.setText(txt);
        if (current >= 0 && tabs[current] != null) tabs[current].onData(d);
    }
}
