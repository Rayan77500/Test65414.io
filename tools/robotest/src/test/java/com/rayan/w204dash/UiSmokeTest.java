package com.rayan.w204dash;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import com.rayan.w204dash.ui.VehiclePage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.annotation.LooperMode;

import java.io.File;
import java.io.FileOutputStream;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/** Lance l'appli dans Android simule, ouvre chaque ecran / onglet et fait des captures. */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class UiSmokeTest {

    private static void idle(long ms) {
        for (int i = 0; i < Math.max(1, ms / 100); i++) {
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {
            }
        }
    }

    private static void shot(MainActivity a, String name) throws Exception {
        // captures seulement avec le moteur graphique natif de Robolectric (option)
        if (System.getProperty("screens.native") == null) return;
        View root = a.getWindow().getDecorView();
        int w = 1024, h = 600;
        root.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        root.layout(0, 0, w, h);
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        root.draw(new Canvas(b));
        File dir = new File(System.getProperty("screens.dir", "build/screens"));
        dir.mkdirs();
        FileOutputStream fos = new FileOutputStream(new File(dir, name + ".png"));
        b.compress(Bitmap.CompressFormat.PNG, 100, fos);
        fos.close();
    }

    private static int count(View v) {
        if (!(v instanceof ViewGroup)) return 1;
        int n = 1;
        ViewGroup g = (ViewGroup) v;
        for (int i = 0; i < g.getChildCount(); i++) n += count(g.getChildAt(i));
        return n;
    }

    private static <T> T find(View v, Class<T> c) {
        if (c.isInstance(v)) return c.cast(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T r = find(g.getChildAt(i), c);
                if (r != null) return r;
            }
        }
        return null;
    }

    /**
     * Le simulateur n'execute pas OpenGL : pour la capture d'accueil, on place sous
     * les commandes l'image produite par les shaders de l'appli (tools/preview).
     */
    private static void insert3dPreview(MainActivity a) {
        String p = System.getProperty("preview3d");
        if (p == null || !new File(p).exists()) return;
        com.rayan.w204dash.gl.CarGlView gl = find(a.getWindow().getDecorView(), com.rayan.w204dash.gl.CarGlView.class);
        if (gl == null) return;
        android.widget.ImageView iv = new android.widget.ImageView(a);
        iv.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
        iv.setImageBitmap(android.graphics.BitmapFactory.decodeFile(p));
        ((ViewGroup) gl.getParent()).addView(iv, 1, new android.widget.FrameLayout.LayoutParams(-1, -1));
    }

    @Test
    @Config(sdk = 34, qualifiers = "w1024dp-h600dp-land-mdpi")
    public void allScreens() throws Exception {
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        idle(2500);
        insert3dPreview(a);
        assertTrue("vues creees", count(a.getWindow().getDecorView()) > 30);
        shot(a, "1_accueil");
        a.showPage(1);
        idle(600);
        VehiclePage vp = find(a.getWindow().getDecorView(), VehiclePage.class);
        assertNotNull(vp);
        String[] tabs = {"2a_ouvrants", "2b_radars", "2c_infos", "2d_entretien"};
        for (int i = 0; i < 4; i++) {
            vp.select(i);
            idle(800);
            shot(a, tabs[i]);
        }
        // ouvrir une porte en demo puis revoir l'onglet Ouvrants
        App.get().repository().toggleFromUser(0);
        App.get().repository().toggleFromUser(5);
        vp.select(0);
        idle(500);
        shot(a, "2e_ouvrants_portes_ouvertes");
        a.showPage(2);
        idle(800);
        shot(a, "3_reglages");
        a.showPage(3);
        idle(3000);
        shot(a, "4_diagnostic");
        a.showPage(0);
        idle(500);
        c.pause().stop().destroy();
    }

    @Test
    @Config(sdk = 34, qualifiers = "w683dp-h400dp-land-mdpi")
    public void smallScreen() throws Exception {
        // ecran 7" 1024x600 en densite hdpi (le cas le plus etroit)
        ActivityController<MainActivity> c = Robolectric.buildActivity(MainActivity.class).setup();
        MainActivity a = c.get();
        idle(1500);
        for (int p = 1; p < 4; p++) {
            a.showPage(p);
            idle(600);
        }
        a.showPage(1);
        VehiclePage vp = find(a.getWindow().getDecorView(), VehiclePage.class);
        for (int i = 0; i < 4; i++) {
            vp.select(i);
            idle(300);
        }
        c.pause().stop().destroy();
    }
}
