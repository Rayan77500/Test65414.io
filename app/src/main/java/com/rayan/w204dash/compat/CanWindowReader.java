package com.rayan.w204dash.compat;

import android.accessibilityservice.AccessibilityService;
import android.os.Build;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.data.CanWindowState;
import com.rayan.w204dash.data.HeadUnitProbe;

import java.util.ArrayList;
import java.util.List;

/**
 * Service d'accessibilite (a activer dans les reglages Android) : lit
 * UNIQUEMENT les fenetres affichees par l'appli CAN de l'ecran (ex. la
 * fenetre "porte ouverte") pour en deduire l'etat des ouvrants.
 */
public class CanWindowReader extends AccessibilityService {

    private static final String TAG = "Fenêtres";
    private static final int MAX_NODES = 150;

    private String lastSignature = "";
    private boolean canWindowShown;
    private int dumps;

    @Override
    protected void onServiceConnected() {
        CanWindowState.active = true;
        DiagLog.i(TAG, "Lecture des fenêtres de l'écran activée");
    }

    @Override
    public void onDestroy() {
        CanWindowState.active = false;
        super.onDestroy();
    }

    static boolean isCanPackage(CharSequence pkg) {
        if (pkg == null) return false;
        String p = pkg.toString();
        return !p.startsWith("com.rayan.") && !p.contains("bluetooth") && !p.equals("com.android.systemui")
                && HeadUnitProbe.isVehiclePackage(p);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent e) {
        try {
            handle(e);
        } catch (Throwable t) {
            DiagLog.e(TAG, "Lecture d'une fenêtre", t);
        }
    }

    private String lastWindowPkg = "";
    private int windowLogs;

    private void handle(AccessibilityEvent e) {
        CharSequence pkg = e.getPackageName();
        // quelle appli affiche une fenetre ? (nom seulement, sans contenu)
        if (pkg != null && e.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                && !pkg.toString().equals(lastWindowPkg) && !pkg.toString().startsWith("com.rayan.")
                && windowLogs < 120) {
            lastWindowPkg = pkg.toString();
            windowLogs++;
            DiagLog.i(TAG, "Fenêtre affichée par " + pkg + (e.getClassName() != null ? " (" + e.getClassName() + ")" : ""));
        }
        if (isCanPackage(pkg)) {
            AccessibilityNodeInfo root = rootOf(e.getSource());
            if (root == null && Build.VERSION.SDK_INT >= 21) root = canRoot();
            if (root == null) return;
            List<CanWindowState.Node> nodes = new ArrayList<CanWindowState.Node>();
            StringBuilder dump = new StringBuilder();
            collect(root, 0, nodes, dump);
            canWindowShown = true;
            boolean ok = CanWindowState.apply(pkg.toString(), nodes);
            String sig = dump.toString();
            if (!sig.equals(lastSignature)) {
                lastSignature = sig;
                String head = "Fenêtre de " + pkg + " (" + nodes.size() + " éléments)"
                        + (ok ? " : ouvrants reconnus → " + CanWindowState.summary() : " : non interprétée");
                CanWindowState.setDump(head + "\n" + sig);
                if (dumps++ < 40) DiagLog.i(TAG, head + "\n" + sig);
            }
        } else if (canWindowShown && Build.VERSION.SDK_INT >= 21) {
            // la fenetre CAN a-t-elle disparu ?
            if (!canWindowStillShown()) {
                canWindowShown = false;
                CanWindowState.windowGone();
                DiagLog.i(TAG, "Fenêtre de l'appli CAN fermée");
            }
        }
    }

    private boolean canWindowStillShown() {
        return canRoot() != null;
    }

    /** Racine d'une fenetre affichee par l'appli CAN (Android 5+), ou null. */
    private AccessibilityNodeInfo canRoot() {
        List<AccessibilityWindowInfo> ws = getWindows();
        if (ws == null) return null;
        for (AccessibilityWindowInfo w : ws) {
            AccessibilityNodeInfo r = w.getRoot();
            if (r != null && isCanPackage(r.getPackageName())) return r;
        }
        return null;
    }

    private static AccessibilityNodeInfo rootOf(AccessibilityNodeInfo n) {
        if (n == null) return null;
        AccessibilityNodeInfo cur = n;
        for (int i = 0; i < 30; i++) {
            AccessibilityNodeInfo p = cur.getParent();
            if (p == null) break;
            cur = p;
        }
        return cur;
    }

    private void collect(AccessibilityNodeInfo n, int depth, List<CanWindowState.Node> out, StringBuilder dump) {
        if (n == null || out.size() >= MAX_NODES || depth > 14) return;
        CanWindowState.Node x = new CanWindowState.Node();
        x.id = Build.VERSION.SDK_INT >= 18 && n.getViewIdResourceName() != null ? n.getViewIdResourceName() : "";
        x.cls = n.getClassName() == null ? "" : n.getClassName().toString();
        x.text = n.getText() == null ? "" : n.getText().toString();
        x.desc = n.getContentDescription() == null ? "" : n.getContentDescription().toString();
        x.visible = Build.VERSION.SDK_INT >= 16 && n.isVisibleToUser();
        x.selected = n.isSelected() || n.isChecked();
        out.add(x);
        StringBuilder pad = new StringBuilder();
        for (int i = 0; i < depth; i++) pad.append("  ");
        String id = x.id.contains("/") ? x.id.substring(x.id.indexOf('/') + 1) : x.id;
        dump.append(pad).append(x.cls.substring(x.cls.lastIndexOf('.') + 1))
                .append(id.length() > 0 ? " #" + id : "")
                .append(x.text.length() > 0 ? " \"" + x.text + "\"" : "")
                .append(x.desc.length() > 0 ? " [" + x.desc + "]" : "")
                .append(x.visible ? "" : " (caché)")
                .append(x.selected ? " (sélectionné)" : "")
                .append('\n');
        for (int i = 0; i < n.getChildCount(); i++) {
            collect(n.getChild(i), depth + 1, out, dump);
        }
    }

    @Override
    public void onInterrupt() {
        // rien
    }
}
