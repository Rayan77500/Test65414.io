package com.rayan.w204dash.ui;

import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.content.DialogInterface;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.rayan.w204dash.App;
import com.rayan.w204dash.MainActivity;
import com.rayan.w204dash.R;
import com.rayan.w204dash.data.VehicleData;
import com.rayan.w204dash.store.MaintenanceStore;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Onglet Entretien : prochaine revision + historique saisi a la main. */
public class MaintenanceTab extends LinearLayout implements VehiclePage.Tab {

    private static final String[] TYPES = {"Révision (ASSYST)", "Service A", "Service B", "Vidange", "Pneus", "Freins",
            "Contrôle technique", "Batterie", "Filtre à gazole", "Autre"};

    private final MainActivity act;
    private final MaintenanceStore store;
    private final TextView nextDate, nextDateSub, nextKm, nextKmSub, currentKm;
    private final LinearLayout list;
    private float odometer = Float.NaN;

    public MaintenanceTab(MainActivity a) {
        super(a);
        act = a;
        store = App.get().maintenance();
        setOrientation(HORIZONTAL);

        LinearLayout left = Ui.card(a, "Prochaine révision");
        left.addView(Ui.label(a, "Date"));
        nextDate = Ui.value(a, Fmt.NA, 40);
        left.addView(nextDate, Ui.lp(-1, -2));
        nextDateSub = Ui.text(a, "", 15, Theme.text2, Ui.REGULAR);
        left.addView(nextDateSub, Ui.lp(-1, -2));
        left.addView(Ui.divider(a));
        left.addView(Ui.label(a, "Kilométrage"));
        nextKm = Ui.value(a, Fmt.NA, 40);
        left.addView(nextKm, Ui.lp(-1, -2));
        nextKmSub = Ui.text(a, "", 15, Theme.text2, Ui.REGULAR);
        left.addView(nextKmSub, Ui.lp(-1, -2));
        View fill = new View(a);
        left.addView(fill, Ui.weight(1, false));
        currentKm = Ui.text(a, "", 13, Theme.text3, Ui.REGULAR);
        left.addView(currentKm, Ui.lp(-1, -2));
        LinearLayout btns = Ui.row(a);
        btns.addView(Ui.button(a, Icons.EDIT, "Modifier", new OnClickListener() {
            @Override
            public void onClick(View v) {
                editNext();
            }
        }), Ui.weight(1, true));
        LinearLayout.LayoutParams b2 = Ui.weight(1, true);
        b2.leftMargin = Ui.dp(10);
        btns.addView(Ui.button(a, Icons.CHECK, "Révision faite", new OnClickListener() {
            @Override
            public void onClick(View v) {
                addEntry(true);
            }
        }), b2);
        LinearLayout.LayoutParams bl = Ui.lp(-1, Ui.dp(60));
        bl.topMargin = Ui.dp(10);
        left.addView(btns, bl);
        addView(left, Ui.weight(1, true));

        LinearLayout right = Ui.card(a, null);
        LinearLayout head = Ui.row(a);
        TextView ht = Ui.text(a, "HISTORIQUE", 13, Theme.text2, Ui.MEDIUM);
        head.addView(ht, Ui.weight(1, true));
        head.addView(Ui.button(a, Icons.PLUS, "Ajouter", new OnClickListener() {
            @Override
            public void onClick(View v) {
                addEntry(false);
            }
        }), Ui.lp(-2, Ui.dp(52)));
        right.addView(head, Ui.lp(-1, -2));
        ScrollView sv = new ScrollView(a);
        sv.setFillViewport(true);
        list = Ui.column(a);
        sv.addView(list, new ScrollView.LayoutParams(-1, -2));
        LinearLayout.LayoutParams slp = Ui.weight(1, false);
        slp.topMargin = Ui.dp(10);
        right.addView(sv, slp);
        LinearLayout.LayoutParams rp = Ui.weight(1.2f, true);
        rp.leftMargin = Ui.dp(14);
        addView(right, rp);
        refresh();
    }

    // ------------------------------------------------------------- dates
    static String toDisplay(String iso) {
        try {
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(iso);
            return new SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(d);
        } catch (Exception e) {
            return iso == null || iso.length() == 0 ? Fmt.NA : iso;
        }
    }

    static String iso(Calendar c) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(c.getTime());
    }

    static Calendar parse(String iso) {
        Calendar c = Calendar.getInstance();
        try {
            if (iso != null && iso.length() > 0) c.setTime(new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(iso));
        } catch (ParseException ignored) {
            // aujourd'hui
        }
        return c;
    }

    private int currentKmValue() {
        if (!Float.isNaN(odometer)) return Math.round(odometer);
        return App.get().prefs().manualKm();
    }

    private void refresh() {
        String nd = store.nextDate();
        nextDate.setText(toDisplay(nd));
        if (nd.length() > 0) {
            long days = (parse(nd).getTimeInMillis() - System.currentTimeMillis()) / 86400000L;
            nextDateSub.setText(days >= 0 ? "dans " + days + " jour" + (days > 1 ? "s" : "") : "dépassée de " + (-days) + " jours");
            nextDateSub.setTextColor(days < 0 ? Theme.BAD : days < 30 ? Theme.WARN : Theme.text2);
        } else {
            nextDateSub.setText("Touchez « Modifier » pour la saisir");
            nextDateSub.setTextColor(Theme.text2);
        }
        int nk = store.nextKm();
        nextKm.setText(nk >= 0 ? Fmt.km(nk) : Fmt.NA);
        int cur = currentKmValue();
        if (nk >= 0 && cur >= 0) {
            int left = nk - cur;
            nextKmSub.setText(left >= 0 ? "dans " + Fmt.num(left, 0) + " km" : "dépassé de " + Fmt.num(-left, 0) + " km");
            nextKmSub.setTextColor(left < 0 ? Theme.BAD : left < 1500 ? Theme.WARN : Theme.text2);
        } else {
            nextKmSub.setText(cur < 0 ? "Kilométrage actuel inconnu (saisissez-le dans « Modifier »)" : "");
            nextKmSub.setTextColor(Theme.text2);
        }
        currentKm.setText("Kilométrage actuel : " + (cur >= 0 ? Fmt.km(cur) : Fmt.NA)
                + (Float.isNaN(odometer) ? (cur >= 0 ? " (saisi à la main)" : "") : " (lu sur la voiture)"));

        list.removeAllViews();
        if (store.entries().isEmpty()) {
            TextView e = Ui.text(getContext(), "Aucune intervention enregistrée.\nTouchez « Ajouter » pour noter une vidange, "
                    + "une révision, des pneus…", 16, Theme.text2, Ui.REGULAR);
            e.setPadding(0, Ui.dp(20), 0, 0);
            list.addView(e);
        }
        for (final MaintenanceStore.Entry en : store.entries()) {
            LinearLayout row = Ui.column(getContext());
            row.setBackgroundDrawable(Ui.pressable(14, 0x0AFFFFFF));
            row.setPadding(Ui.dp(14), Ui.dp(10), Ui.dp(14), Ui.dp(10));
            LinearLayout l1 = Ui.row(getContext());
            TextView t = Ui.text(getContext(), en.type, 18, Theme.text, Ui.MEDIUM);
            l1.addView(t, Ui.weight(1, true));
            l1.addView(Ui.text(getContext(), toDisplay(en.date), 15, Theme.text2, Ui.REGULAR));
            row.addView(l1, Ui.lp(-1, -2));
            String sub = (en.km >= 0 ? Fmt.km(en.km) : "") + (en.cost.length() > 0 ? "  ·  " + en.cost + " €" : "")
                    + (en.notes.length() > 0 ? "  ·  " + en.notes : "");
            if (sub.length() > 0) {
                TextView s = Ui.text(getContext(), sub, 14, Theme.text2, Ui.REGULAR);
                s.setPadding(0, Ui.dp(4), 0, 0);
                row.addView(s, Ui.lp(-1, -2));
            }
            row.setLongClickable(true);
            row.setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    confirmDelete(en);
                    return true;
                }
            });
            row.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    act.toast("Appui long pour supprimer");
                }
            });
            LinearLayout.LayoutParams lp = Ui.lp(-1, -2);
            lp.bottomMargin = Ui.dp(8);
            list.addView(row, lp);
        }
    }

    private void confirmDelete(final MaintenanceStore.Entry en) {
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle("Supprimer ?")
                .setMessage(en.type + " du " + toDisplay(en.date))
                .setPositiveButton("Supprimer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        store.remove(en);
                        refresh();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    // ------------------------------------------------------------- dialogues
    private EditText field(LinearLayout form, String label, String value, boolean number) {
        TextView l = Ui.text(getContext(), label, 15, Theme.text2, Ui.REGULAR);
        l.setPadding(0, Ui.dp(10), 0, Ui.dp(2));
        form.addView(l);
        EditText e = new EditText(getContext());
        e.setText(value);
        e.setTextColor(Theme.text);
        e.setHintTextColor(Theme.text3);
        e.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(19));
        e.setSingleLine(true);
        if (number) e.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(e, Ui.lp(-1, -2));
        return e;
    }

    private Button dateButton(LinearLayout form, String label, final Calendar cal) {
        TextView l = Ui.text(getContext(), label, 15, Theme.text2, Ui.REGULAR);
        l.setPadding(0, Ui.dp(10), 0, Ui.dp(2));
        form.addView(l);
        final Button b = new Button(getContext());
        b.setText(toDisplay(iso(cal)));
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.px(19));
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                new DatePickerDialog(getContext(), new DatePickerDialog.OnDateSetListener() {
                    @Override
                    public void onDateSet(DatePicker view, int y, int m, int d) {
                        cal.set(y, m, d);
                        b.setText(toDisplay(iso(cal)));
                    }
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show();
            }
        });
        form.addView(b, Ui.lp(-1, -2));
        return b;
    }

    private LinearLayout form() {
        LinearLayout f = Ui.column(getContext());
        f.setPadding(Ui.dp(20), Ui.dp(6), Ui.dp(20), Ui.dp(6));
        return f;
    }

    private static int toInt(EditText e, int def) {
        try {
            return Integer.parseInt(e.getText().toString().replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    private void editNext() {
        LinearLayout f = form();
        final Calendar cal = parse(store.nextDate());
        if (store.nextDate().length() == 0) cal.add(Calendar.YEAR, 1);
        dateButton(f, "Date de la prochaine révision", cal);
        final EditText km = field(f, "Kilométrage de la prochaine révision",
                store.nextKm() >= 0 ? String.valueOf(store.nextKm()) : "", true);
        final EditText cur = Float.isNaN(odometer) ? field(f, "Kilométrage actuel du compteur",
                App.get().prefs().manualKm() >= 0 ? String.valueOf(App.get().prefs().manualKm()) : "", true) : null;
        ScrollView sv = new ScrollView(getContext());
        sv.addView(f);
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle("Prochaine révision")
                .setView(sv)
                .setPositiveButton("Enregistrer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        store.setNext(iso(cal), toInt(km, -1));
                        if (cur != null) App.get().prefs().setManualKm(toInt(cur, -1));
                        refresh();
                    }
                })
                .setNeutralButton("Effacer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        store.setNext("", -1);
                        refresh();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    private void addEntry(final boolean serviceDone) {
        LinearLayout f = form();
        final Calendar cal = Calendar.getInstance();
        dateButton(f, "Date", cal);
        int cur = currentKmValue();
        final EditText km = field(f, "Kilométrage", cur >= 0 ? String.valueOf(cur) : "", true);
        final EditText type = field(f, "Intervention", serviceDone ? TYPES[0] : "", false);
        HorizontalScrollView hs = new HorizontalScrollView(getContext());
        LinearLayout chips = Ui.row(getContext());
        for (final String t : TYPES) {
            TextView c = Ui.pill(getContext(), t, Theme.accent);
            c.setClickable(true);
            c.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    type.setText(t);
                }
            });
            LinearLayout.LayoutParams lp = Ui.lp(-2, -2);
            lp.rightMargin = Ui.dp(8);
            chips.addView(c, lp);
        }
        hs.addView(chips);
        LinearLayout.LayoutParams hl = Ui.lp(-1, -2);
        hl.topMargin = Ui.dp(6);
        f.addView(hs, hl);
        final EditText cost = field(f, "Coût (€, facultatif)", "", false);
        final EditText notes = field(f, "Remarques (facultatif)", "", false);
        notes.setSingleLine(false);
        notes.setMaxLines(3);
        ScrollView sv = new ScrollView(getContext());
        sv.addView(f);
        new AlertDialog.Builder(getContext(), R.style.DialogTheme)
                .setTitle(serviceDone ? "Révision effectuée" : "Nouvelle intervention")
                .setView(sv)
                .setPositiveButton("Enregistrer", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int w) {
                        MaintenanceStore.Entry e = new MaintenanceStore.Entry();
                        e.date = iso(cal);
                        e.km = toInt(km, -1);
                        e.type = type.getText().toString().trim().length() > 0 ? type.getText().toString().trim() : "Intervention";
                        e.cost = cost.getText().toString().trim();
                        e.notes = notes.getText().toString().trim();
                        store.add(e);
                        if (serviceDone) {
                            Calendar next = (Calendar) cal.clone();
                            next.add(Calendar.YEAR, 1);
                            store.setNext(iso(next), e.km >= 0 ? e.km + 25000 : -1);
                            act.toast("Prochaine révision proposée : dans 1 an ou 25 000 km (modifiable)");
                        }
                        if (Float.isNaN(odometer) && e.km > App.get().prefs().manualKm()) {
                            App.get().prefs().setManualKm(e.km);
                        }
                        refresh();
                    }
                })
                .setNegativeButton("Annuler", null)
                .show();
    }

    @Override
    public View view() {
        return this;
    }

    @Override
    public void onData(VehicleData d) {
        boolean changed = Float.isNaN(odometer) != Float.isNaN(d.odometer)
                || (!Float.isNaN(d.odometer) && Math.abs(d.odometer - odometer) >= 1);
        odometer = d.odometer;
        if (changed) refresh();
    }

    @Override
    public void onShown() {
        refresh();
    }

    @Override
    public void onHidden() {
    }
}
