package com.rayan.w204dash.store;

import android.content.Context;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.Files;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Carnet d'entretien (saisie manuelle), enregistre en JSON sur l'appareil. */
public final class MaintenanceStore {

    public static final class Entry {
        public String date;   // "AAAA-MM-JJ"
        public int km = -1;
        public String type = "";
        public String notes = "";
        public String cost = "";
    }

    private final File file;
    /** Prochaine revision : date "AAAA-MM-JJ" ou "" et kilometrage ou -1. */
    private String nextDate = "";
    private int nextKm = -1;
    private final List<Entry> entries = new ArrayList<Entry>();

    public MaintenanceStore(Context ctx) {
        file = new File(ctx.getFilesDir(), "entretien.json");
        load();
    }

    private void load() {
        if (!file.exists()) return;
        try {
            JSONObject o = new JSONObject(Files.readText(file));
            nextDate = o.optString("nextDate", "");
            nextKm = o.optInt("nextKm", -1);
            JSONArray a = o.optJSONArray("history");
            if (a != null) {
                for (int i = 0; i < a.length(); i++) {
                    JSONObject e = a.getJSONObject(i);
                    Entry en = new Entry();
                    en.date = e.optString("date", "");
                    en.km = e.optInt("km", -1);
                    en.type = e.optString("type", "");
                    en.notes = e.optString("notes", "");
                    en.cost = e.optString("cost", "");
                    entries.add(en);
                }
            }
            sort();
        } catch (Exception e) {
            DiagLog.e("Entretien", "Lecture du carnet", e);
        }
    }

    private void save() {
        try {
            JSONObject o = new JSONObject();
            o.put("nextDate", nextDate);
            o.put("nextKm", nextKm);
            JSONArray a = new JSONArray();
            for (Entry en : entries) {
                JSONObject e = new JSONObject();
                e.put("date", en.date);
                e.put("km", en.km);
                e.put("type", en.type);
                e.put("notes", en.notes);
                e.put("cost", en.cost);
                a.put(e);
            }
            o.put("history", a);
            Files.write(file, o.toString(2).getBytes("UTF-8"));
        } catch (Exception e) {
            DiagLog.e("Entretien", "Enregistrement du carnet", e);
        }
    }

    private void sort() {
        Collections.sort(entries, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return String.valueOf(b.date).compareTo(String.valueOf(a.date));
            }
        });
    }

    public String nextDate() {
        return nextDate;
    }

    public int nextKm() {
        return nextKm;
    }

    public void setNext(String date, int km) {
        nextDate = date == null ? "" : date;
        nextKm = km;
        save();
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public void add(Entry e) {
        entries.add(e);
        sort();
        save();
    }

    public void remove(Entry e) {
        entries.remove(e);
        save();
    }
}
