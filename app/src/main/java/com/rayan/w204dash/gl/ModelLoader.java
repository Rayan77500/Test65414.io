package com.rayan.w204dash.gl;

import android.content.Context;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.Files;

import java.io.File;

/**
 * Charge le modele 3D : d'abord le fichier importe par l'utilisateur
 * (Reglages > Modele 3D), sinon le modele integre assets/models/car.glb.
 */
public final class ModelLoader {

    public static final String BUILTIN = "models/car.glb";

    public static final class Result {
        public CarScene scene;
        public boolean custom;
        public String error;
    }

    private ModelLoader() {
    }

    public static File customFile(Context ctx) {
        return new File(ctx.getFilesDir(), "modele_perso.glb");
    }

    public static Result load(Context ctx, int rotation) {
        Result r = new Result();
        File f = customFile(ctx);
        if (f.exists()) {
            try {
                long t0 = System.currentTimeMillis();
                GlbModel m = GlbModel.parse(Files.readAll(f));
                r.scene = CarScene.build(m, rotation);
                r.custom = true;
                DiagLog.i("3D", "Modèle importé chargé en " + (System.currentTimeMillis() - t0) + " ms : " + r.scene.info);
                return r;
            } catch (Throwable e) {
                r.error = "Modèle importé illisible (" + e.getMessage() + "), retour au modèle intégré";
                DiagLog.e("3D", r.error, null);
            }
        }
        try {
            long t0 = System.currentTimeMillis();
            GlbModel m = GlbModel.parse(Files.readAll(ctx.getAssets().open(BUILTIN)));
            r.scene = CarScene.build(m, rotation);
            DiagLog.i("3D", "Modèle intégré chargé en " + (System.currentTimeMillis() - t0) + " ms : " + r.scene.info);
        } catch (Throwable e) {
            r.error = "Modèle 3D introuvable : " + e.getMessage();
            DiagLog.e("3D", "Chargement du modèle intégré", e);
        }
        return r;
    }
}
