package com.rayan.w204dash.gl;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;

/**
 * Vue 3D : glisser = tourner autour de la voiture, pincer = zoom,
 * double appui = vue par defaut, appui simple = ouvrir/fermer l'ouvrant touche.
 */
public final class CarGlView extends GLSurfaceView {

    public interface OnPartTapListener {
        /** part : 0..5 (portes, capot, coffre), -1 autre partie, -2 a cote. */
        void onPartTapped(int part);
    }

    private final CarRenderer renderer;
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaler;
    private final float density;
    private OnPartTapListener tapListener;

    public CarGlView(Context ctx) {
        super(ctx);
        density = ctx.getResources().getDisplayMetrics().density;
        setEGLContextClientVersion(2);
        setEGLConfigChooser(new ConfigChooser());
        setPreserveEGLContextOnPause(true);
        renderer = new CarRenderer(ctx);
        renderer.attach(this);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_WHEN_DIRTY);

        gestures = new GestureDetector(ctx, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                renderer.stopInertia();
                return true;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float dx, float dy) {
                if (scaler.isInProgress()) return true;
                float k = 0.32f / density;
                renderer.rotateBy(-dx * k, -dy * k * 0.6f);
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float vx, float vy) {
                if (scaler.isInProgress()) return true;
                renderer.fling(-vx * 0.25f / density);
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                final float x = e.getX(), y = e.getY();
                // calcul du rayon hors du fil d'interface (gros modeles)
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final int part = renderer.pick(x, y);
                        post(new Runnable() {
                            @Override
                            public void run() {
                                if (tapListener != null) tapListener.onPartTapped(part);
                            }
                        });
                    }
                }, "selection-3d").start();
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                renderer.resetView();
                return true;
            }
        });
        scaler = new ScaleGestureDetector(ctx, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                renderer.zoomBy(d.getScaleFactor());
                return true;
            }
        });
    }

    public CarRenderer renderer() {
        return renderer;
    }

    public void setOnPartTapListener(OnPartTapListener l) {
        tapListener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        gestures.onTouchEvent(e);
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    /** Choisit une configuration OpenGL ES 2 : anticrenelage 4x si possible. */
    private static final class ConfigChooser implements GLSurfaceView.EGLConfigChooser {
        private static final int EGL_RENDERABLE_TYPE = 0x3040;
        private static final int EGL_OPENGL_ES2_BIT = 4;

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[][] lists = {
                    {EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16,
                            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_SAMPLE_BUFFERS, 1, EGL10.EGL_SAMPLES, 4,
                            EGL10.EGL_NONE},
                    {EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_DEPTH_SIZE, 16,
                            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE},
                    {EGL10.EGL_RED_SIZE, 5, EGL10.EGL_GREEN_SIZE, 6, EGL10.EGL_BLUE_SIZE, 5, EGL10.EGL_DEPTH_SIZE, 16,
                            EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE},
                    {EGL10.EGL_DEPTH_SIZE, 16, EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT, EGL10.EGL_NONE},
            };
            for (int[] attribs : lists) {
                EGLConfig[] configs = new EGLConfig[1];
                int[] num = new int[1];
                if (egl.eglChooseConfig(display, attribs, configs, 1, num) && num[0] > 0 && configs[0] != null) {
                    return configs[0];
                }
            }
            throw new IllegalArgumentException("Aucune configuration OpenGL ES 2.0 disponible");
        }
    }
}
