package com.rayan.w204dash.ui;

import android.widget.FrameLayout;

import com.rayan.w204dash.MainActivity;

/** Un ecran de l'application (Accueil, Vehicule, Reglages, Diagnostic). */
public abstract class Page extends FrameLayout {

    protected final MainActivity act;
    private boolean shown;

    protected Page(MainActivity a) {
        super(a);
        act = a;
    }

    public final void show() {
        if (shown) return;
        shown = true;
        onShow();
    }

    public final void hide() {
        if (!shown) return;
        shown = false;
        onHide();
    }

    public boolean isShown2() {
        return shown;
    }

    protected void onShow() {
    }

    protected void onHide() {
    }

    /** Appele quand l'activite est mise en pause / reprise. */
    public void onActivityPause() {
    }

    public void onActivityResume() {
    }
}
