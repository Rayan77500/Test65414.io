package com.rayan.w204dash.gl;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.Files;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Rendu OpenGL ES 2.0 de la voiture. L'image n'est recalculee que lorsque
 * quelque chose bouge (rotation au doigt, ouverture d'une porte...), pour
 * menager les ecrans peu puissants.
 */
public final class CarRenderer implements GLSurfaceView.Renderer {

    private static final String TAG = "3D";
    private static final int GL_UNSIGNED_INT = 0x1405;
    private static final float FOV = 30f;
    private static final float DEFAULT_YAW = 38f, DEFAULT_PITCH = 13f, DEFAULT_ZOOM = 1.0f;

    private final Context ctx;
    private GLSurfaceView view;

    // --- etat partage (fil interface <-> fil OpenGL), protege par "this"
    private CarScene pendingScene;
    private CarScene scene;
    private float yaw = DEFAULT_YAW, pitch = DEFAULT_PITCH, zoom = DEFAULT_ZOOM, yawVel;
    private final float[] openTarget = new float[CarScene.PARTS];
    private final float[] openT = new float[CarScene.PARTS];
    private float[] paint = {0.55f, 0.565f, 0.58f};
    private float[] clear = {0.03f, 0.04f, 0.06f};
    private float[] accent = {0.31f, 0.76f, 0.97f};
    private boolean autoRotate;
    private long lastTouchMs;
    private float[] lastInvViewProj;
    private float[] lastEye = new float[3];

    // --- ressources OpenGL (fil OpenGL uniquement)
    private Program prog;
    private Program progMask;
    private int gProg, gPos, gViewProj, gHalf, gCenter, gColor, gInner, gVbo;
    private final List<GpuItem> gpu = new ArrayList<GpuItem>();
    private boolean uintSupported;
    private boolean needUpload;
    private int width = 1, height = 1;
    private long lastNs;

    private static final class GpuItem {
        CarScene.Item item;
        int vbo, ibo, tex;
        int count;
        int type;     // GL_UNSIGNED_SHORT, GL_UNSIGNED_INT, ou 0 (glDrawArrays)
        float[] center = new float[3];
    }

    private static final class Program {
        int id, aPos, aNor, aUv, uViewProj, uModel, uCam, uColor, uEmissive, uMetal, uRough, uClear, uUseTex,
                uDarkBack, uCutoff, uTex;
    }

    public CarRenderer(Context ctx) {
        this.ctx = ctx.getApplicationContext();
    }

    void attach(GLSurfaceView v) {
        view = v;
    }

    private void redraw() {
        GLSurfaceView v = view;
        if (v != null) v.requestRender();
    }

    // ------------------------------------------------------------ API (UI)
    public synchronized void setScene(CarScene s) {
        pendingScene = s;
        redraw();
    }

    public synchronized CarScene getScene() {
        return pendingScene != null ? pendingScene : scene;
    }

    public synchronized void setPaint(int argb) {
        paint = new float[]{((argb >> 16) & 0xFF) / 255f, ((argb >> 8) & 0xFF) / 255f, (argb & 0xFF) / 255f};
        redraw();
    }

    public synchronized void setTheme(int bgArgb, int accentArgb) {
        clear = new float[]{((bgArgb >> 16) & 0xFF) / 255f, ((bgArgb >> 8) & 0xFF) / 255f, (bgArgb & 0xFF) / 255f};
        accent = new float[]{((accentArgb >> 16) & 0xFF) / 255f, ((accentArgb >> 8) & 0xFF) / 255f,
                (accentArgb & 0xFF) / 255f};
        redraw();
    }

    public synchronized void setAutoRotate(boolean b) {
        autoRotate = b;
        redraw();
    }

    public synchronized void setOpen(int part, boolean open) {
        if (part >= 0 && part < CarScene.PARTS) {
            float t = open ? 1f : 0f;
            if (openTarget[part] != t) {
                openTarget[part] = t;
                redraw();
            }
        }
    }

    public synchronized void rotateBy(float dYaw, float dPitch) {
        yaw += dYaw;
        pitch = Math.max(2f, Math.min(68f, pitch + dPitch));
        yawVel = 0;
        lastTouchMs = System.currentTimeMillis();
        redraw();
    }

    public synchronized void fling(float vYaw) {
        yawVel = Math.max(-400f, Math.min(400f, vYaw));
        lastTouchMs = System.currentTimeMillis();
        redraw();
    }

    public synchronized void stopInertia() {
        yawVel = 0;
        lastTouchMs = System.currentTimeMillis();
    }

    public synchronized void zoomBy(float f) {
        zoom = Math.max(0.6f, Math.min(2.2f, zoom * f));
        lastTouchMs = System.currentTimeMillis();
        redraw();
    }

    public synchronized void resetView() {
        yaw = DEFAULT_YAW;
        pitch = DEFAULT_PITCH;
        zoom = DEFAULT_ZOOM;
        yawVel = 0;
        redraw();
    }

    /** Ouvrant touche au point (x, y) de la vue : 0..5, -1 autre partie, -2 rien. */
    public int pick(float x, float y) {
        float[] inv;
        CarScene sc;
        float[] t;
        synchronized (this) {
            inv = lastInvViewProj;
            sc = scene;
            t = openT.clone();
        }
        if (inv == null || sc == null) return -2;
        float nx = 2f * x / width - 1f;
        float ny = 1f - 2f * y / height;
        float[] near = unproject(inv, nx, ny, -1f);
        float[] far = unproject(inv, nx, ny, 1f);
        float[] d = {far[0] - near[0], far[1] - near[1], far[2] - near[2]};
        float l = (float) Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        d[0] /= l;
        d[1] /= l;
        d[2] /= l;
        return sc.pick(near, d, t);
    }

    private static float[] unproject(float[] inv, float x, float y, float z) {
        float[] r = new float[3];
        float w = inv[3] * x + inv[7] * y + inv[11] * z + inv[15];
        r[0] = (inv[0] * x + inv[4] * y + inv[8] * z + inv[12]) / w;
        r[1] = (inv[1] * x + inv[5] * y + inv[9] * z + inv[13]) / w;
        r[2] = (inv[2] * x + inv[6] * y + inv[10] * z + inv[14]) / w;
        return r;
    }

    // ---------------------------------------------------------- GL thread
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // contexte neuf : toutes les ressources sont a recreer
        gpu.clear();
        prog = null;
        progMask = null;
        String ext = GLES20.glGetString(GLES20.GL_EXTENSIONS);
        uintSupported = ext != null && ext.contains("GL_OES_element_index_uint");
        DiagLog.i(TAG, "OpenGL ES : " + GLES20.glGetString(GLES20.GL_RENDERER) + " / "
                + GLES20.glGetString(GLES20.GL_VERSION));
        try {
            String vs = asset("shaders/car.vert");
            String fs = asset("shaders/car.frag");
            prog = buildCarProgram(vs, fs, "");
            progMask = buildCarProgram(vs, fs, "#define MASK\n");
        } catch (Exception e) {
            DiagLog.e(TAG, "Shaders principaux refusés, passage au mode simple", e);
            prog = buildCarProgram(FALLBACK_VS, FALLBACK_FS, "");
            progMask = prog;
        }
        try {
            gProg = link(asset("shaders/ground.vert"), asset("shaders/ground.frag"));
        } catch (Exception e) {
            DiagLog.e(TAG, "Shader du sol refusé", e);
            gProg = 0;
        }
        if (gProg != 0) {
            gPos = GLES20.glGetAttribLocation(gProg, "aXZ");
            gViewProj = GLES20.glGetUniformLocation(gProg, "uViewProj");
            gHalf = GLES20.glGetUniformLocation(gProg, "uHalf");
            gCenter = GLES20.glGetUniformLocation(gProg, "uCenter");
            gColor = GLES20.glGetUniformLocation(gProg, "uColor");
            gInner = GLES20.glGetUniformLocation(gProg, "uInner");
            float[] quad = {-1, -1, 1, -1, -1, 1, 1, 1};
            int[] b = new int[1];
            GLES20.glGenBuffers(1, b, 0);
            gVbo = b[0];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gVbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, 32, floatBuffer(quad), GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        }
        synchronized (this) {
            if (pendingScene == null && scene != null) pendingScene = scene;
        }
        needUpload = true;
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glDepthFunc(GLES20.GL_LEQUAL);
        lastNs = 0;
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int w, int h) {
        width = Math.max(1, w);
        height = Math.max(1, h);
        GLES20.glViewport(0, 0, width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        long now = System.nanoTime();
        float dt = lastNs == 0 ? 0.016f : Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;

        CarScene sc;
        float cy, cp, cz;
        float[] pc, cc, ac;
        boolean animating = false;
        float[] tCopy;
        synchronized (this) {
            if (pendingScene != null) {
                scene = pendingScene;
                pendingScene = null;
                needUpload = true;
            }
            sc = scene;
            if (Math.abs(yawVel) > 0.5f) {
                yaw += yawVel * dt;
                yawVel *= (float) Math.exp(-dt * 3.2f);
                animating = true;
            } else {
                yawVel = 0;
            }
            if (autoRotate && System.currentTimeMillis() - lastTouchMs > 4000) {
                yaw += 7f * dt;
                animating = true;
            }
            for (int i = 0; i < CarScene.PARTS; i++) {
                float tgt = openTarget[i];
                if (openT[i] != tgt) {
                    float step = dt / 0.65f;
                    openT[i] = tgt > openT[i] ? Math.min(tgt, openT[i] + step) : Math.max(tgt, openT[i] - step);
                    animating = true;
                }
            }
            cy = yaw;
            cp = pitch;
            cz = zoom;
            pc = paint;
            cc = clear;
            ac = accent;
            tCopy = openT.clone();
        }
        if (needUpload && sc != null) {
            upload(sc);
            needUpload = false;
        }

        GLES20.glClearColor(cc[0], cc[1], cc[2], 1f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        if (sc == null || prog == null) {
            if (animating) redraw();
            return;
        }

        // --- camera
        float h = sc.max[1] - sc.min[1];
        float radius = 0.5f * (float) Math.sqrt(sq(sc.max[0] - sc.min[0]) + sq(h) + sq(sc.max[2] - sc.min[2]));
        float aspect = (float) width / height;
        float halfV = (float) Math.toRadians(FOV / 2);
        float halfH = (float) Math.atan(Math.tan(halfV) * aspect);
        // cadrage : plus serre sur un ecran large (la sphere englobante est prudente)
        float fit = Math.max(0.95f, Math.min(1.35f, 0.95f + (aspect - 0.9f) * 0.9f));
        float dist = (float) (radius / Math.sin(Math.min(halfV, halfH))) / (cz * fit);
        float ty = h * 0.40f;
        double yr = Math.toRadians(cy), pr = Math.toRadians(cp);
        float ex = (float) (dist * Math.cos(pr) * Math.sin(yr));
        float ey = ty + (float) (dist * Math.sin(pr));
        float ez = (float) (dist * Math.cos(pr) * Math.cos(yr));
        float[] viewM = M4.lookAt(ex, ey, ez, 0, ty, 0, 0, 1, 0);
        float[] proj = M4.perspective(FOV, aspect, Math.max(0.05f, dist - radius * 2.2f), dist + radius * 2.5f);
        float[] vp = M4.mul(proj, viewM);
        synchronized (this) {
            lastInvViewProj = M4.invert(vp);
            lastEye = new float[]{ex, ey, ez};
        }

        // --- sol : halo + ombre douce
        float len = sc.max[2] - sc.min[2];
        float wid = sc.max[0] - sc.min[0];
        if (gProg != 0) {
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthMask(false);
            GLES20.glUseProgram(gProg);
            GLES20.glUniformMatrix4fv(gViewProj, 1, false, vp, 0);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, gVbo);
            GLES20.glEnableVertexAttribArray(gPos);
            GLES20.glVertexAttribPointer(gPos, 2, GLES20.GL_FLOAT, false, 8, 0);
            ground(0, 0, wid * 2.3f, len * 0.95f, ac[0], ac[1], ac[2], 0.13f, 0.0f);
            ground(0, 0, wid * 0.68f, len * 0.58f, 0, 0, 0, 0.62f, 0.45f);
            ground(0, 0, wid * 0.80f, len * 0.66f, 0, 0, 0, 0.35f, 0.2f);
            GLES20.glDisableVertexAttribArray(gPos);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
        }

        // --- voiture : opaque puis transparent (du plus loin au plus proche)
        float[][] partM = new float[CarScene.PARTS][];
        for (int i = 0; i < CarScene.PARTS; i++) partM[i] = sc.partMatrix(i, tCopy[i]);
        List<GpuItem> blended = new ArrayList<GpuItem>();
        for (GpuItem g : gpu) {
            if (g.item.mat.blend) {
                blended.add(g);
            } else {
                drawItem(g, g.item.mat.alphaCutoff >= 0 ? progMask : prog, vp, partM, pc, ex, ey, ez, sc.generated);
            }
        }
        if (!blended.isEmpty()) {
            final float fx = ex, fy = ey, fz = ez;
            final float[][] pm = partM;
            Collections.sort(blended, new Comparator<GpuItem>() {
                @Override
                public int compare(GpuItem a, GpuItem b) {
                    return Float.compare(dist2(b, pm, fx, fy, fz), dist2(a, pm, fx, fy, fz));
                }
            });
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthMask(false);
            for (GpuItem g : blended) {
                drawItem(g, prog, vp, partM, pc, ex, ey, ez, sc.generated);
            }
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
        }
        if (animating) redraw();
    }

    private static float sq(float v) {
        return v * v;
    }

    private static float dist2(GpuItem g, float[][] pm, float x, float y, float z) {
        float[] c = g.center;
        if (g.item.part >= 0) {
            float[] o = new float[3];
            M4.transformPoint(pm[g.item.part], c, 0, o, 0);
            c = o;
        }
        return sq(c[0] - x) + sq(c[1] - y) + sq(c[2] - z);
    }

    private void ground(float cx, float cz, float hx, float hz, float r, float g, float b, float a, float inner) {
        GLES20.glUniform2f(gHalf, hx, hz);
        GLES20.glUniform2f(gCenter, cx, cz);
        GLES20.glUniform4f(gColor, r, g, b, a);
        GLES20.glUniform1f(gInner, inner);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private void drawItem(GpuItem g, Program p, float[] vp, float[][] partM, float[] pc, float ex, float ey, float ez,
                          boolean generated) {
        GlbModel.Material m = g.item.mat;
        GLES20.glUseProgram(p.id);
        GLES20.glUniformMatrix4fv(p.uViewProj, 1, false, vp, 0);
        float[] model = g.item.part >= 0 ? partM[g.item.part] : IDENTITY;
        GLES20.glUniformMatrix4fv(p.uModel, 1, false, model, 0);
        GLES20.glUniform3f(p.uCam, ex, ey, ez);
        if (g.item.paint) {
            GLES20.glUniform4f(p.uColor, pc[0], pc[1], pc[2], m.color[3]);
            GLES20.glUniform1f(p.uClear, 1f);
            GLES20.glUniform1f(p.uMetal, Math.min(0.85f, Math.max(0.55f, m.metallic)));
            GLES20.glUniform1f(p.uRough, Math.min(0.45f, m.roughness));
        } else {
            GLES20.glUniform4f(p.uColor, m.color[0], m.color[1], m.color[2], m.color[3]);
            GLES20.glUniform1f(p.uClear, 0f);
            GLES20.glUniform1f(p.uMetal, m.metallic);
            GLES20.glUniform1f(p.uRough, m.roughness);
        }
        GLES20.glUniform3f(p.uEmissive, m.emissive[0], m.emissive[1], m.emissive[2]);
        GLES20.glUniform1f(p.uDarkBack, generated ? 1f : 0f);
        GLES20.glUniform1f(p.uCutoff, m.alphaCutoff);
        if (g.tex != 0) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, g.tex);
            GLES20.glUniform1i(p.uTex, 0);
            GLES20.glUniform1f(p.uUseTex, 1f);
        } else {
            GLES20.glUniform1f(p.uUseTex, 0f);
        }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, g.vbo);
        GLES20.glEnableVertexAttribArray(p.aPos);
        GLES20.glVertexAttribPointer(p.aPos, 3, GLES20.GL_FLOAT, false, 32, 0);
        if (p.aNor >= 0) {
            GLES20.glEnableVertexAttribArray(p.aNor);
            GLES20.glVertexAttribPointer(p.aNor, 3, GLES20.GL_FLOAT, false, 32, 12);
        }
        if (p.aUv >= 0) {
            GLES20.glEnableVertexAttribArray(p.aUv);
            GLES20.glVertexAttribPointer(p.aUv, 2, GLES20.GL_FLOAT, false, 32, 24);
        }
        if (g.type == 0) {
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, g.count);
        } else {
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, g.ibo);
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, g.count, g.type, 0);
            GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        }
        GLES20.glDisableVertexAttribArray(p.aPos);
        if (p.aNor >= 0) GLES20.glDisableVertexAttribArray(p.aNor);
        if (p.aUv >= 0) GLES20.glDisableVertexAttribArray(p.aUv);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    }

    private static final float[] IDENTITY = M4.identity();

    // ------------------------------------------------------------ upload
    private void upload(CarScene sc) {
        for (GpuItem g : gpu) {
            GLES20.glDeleteBuffers(2, new int[]{g.vbo, g.ibo}, 0);
            if (g.tex != 0) GLES20.glDeleteTextures(1, new int[]{g.tex}, 0);
        }
        gpu.clear();
        int[] texCache = new int[64];
        Arrays.fill(texCache, -1);
        long t0 = System.currentTimeMillis();
        for (CarScene.Item it : sc.items) {
            GpuItem g = new GpuItem();
            g.item = it;
            int n = it.pos.length / 3;
            boolean expand = n > 65535 && !uintSupported;
            int vcount = expand ? it.idx.length : n;
            float[] inter = new float[vcount * 8];
            for (int k = 0; k < vcount; k++) {
                int v = expand ? it.idx[k] : k;
                inter[k * 8] = it.pos[v * 3];
                inter[k * 8 + 1] = it.pos[v * 3 + 1];
                inter[k * 8 + 2] = it.pos[v * 3 + 2];
                inter[k * 8 + 3] = it.nor[v * 3];
                inter[k * 8 + 4] = it.nor[v * 3 + 1];
                inter[k * 8 + 5] = it.nor[v * 3 + 2];
                if (it.uv != null) {
                    inter[k * 8 + 6] = it.uv[v * 2];
                    inter[k * 8 + 7] = it.uv[v * 2 + 1];
                }
            }
            int[] b = new int[2];
            GLES20.glGenBuffers(2, b, 0);
            g.vbo = b[0];
            g.ibo = b[1];
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, g.vbo);
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, inter.length * 4, floatBuffer(inter), GLES20.GL_STATIC_DRAW);
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
            if (expand) {
                g.type = 0;
                g.count = vcount;
            } else {
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, g.ibo);
                if (n <= 65535) {
                    short[] s = new short[it.idx.length];
                    for (int k = 0; k < s.length; k++) s[k] = (short) it.idx[k];
                    ShortBuffer sb = ByteBuffer.allocateDirect(s.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
                    sb.put(s).position(0);
                    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, s.length * 2, sb, GLES20.GL_STATIC_DRAW);
                    g.type = GLES20.GL_UNSIGNED_SHORT;
                } else {
                    IntBuffer ib = ByteBuffer.allocateDirect(it.idx.length * 4).order(ByteOrder.nativeOrder()).asIntBuffer();
                    ib.put(it.idx).position(0);
                    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, it.idx.length * 4, ib, GLES20.GL_STATIC_DRAW);
                    g.type = GL_UNSIGNED_INT;
                }
                GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
                g.count = it.idx.length;
            }
            g.center[0] = (it.min[0] + it.max[0]) / 2;
            g.center[1] = (it.min[1] + it.max[1]) / 2;
            g.center[2] = (it.min[2] + it.max[2]) / 2;
            if (it.image >= 0 && it.uv != null && it.image < texCache.length) {
                if (texCache[it.image] == -1) texCache[it.image] = loadTexture(sc, it.image);
                g.tex = Math.max(0, texCache[it.image]);
            }
            gpu.add(g);
        }
        DiagLog.i(TAG, "Modèle envoyé à la carte graphique en " + (System.currentTimeMillis() - t0) + " ms ("
                + sc.triangles + " triangles)");
    }

    private int loadTexture(CarScene sc, int image) {
        GlbModel.Image im = sc.images != null && image < sc.images.size() ? sc.images.get(image) : null;
        if (im == null || im.data == null) return 0;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(im.data, 0, im.data.length, o);
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / sample > 1024) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            Bitmap bmp = BitmapFactory.decodeByteArray(im.data, 0, im.data.length, o);
            if (bmp == null) return 0;
            int w = pot(bmp.getWidth()), h = pot(bmp.getHeight());
            if (w != bmp.getWidth() || h != bmp.getHeight()) {
                Bitmap s = Bitmap.createScaledBitmap(bmp, w, h, true);
                if (s != bmp) bmp.recycle();
                bmp = s;
            }
            int[] t = new int[1];
            GLES20.glGenTextures(1, t, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t[0]);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            bmp.recycle();
            return t[0];
        } catch (Throwable e) {
            DiagLog.e(TAG, "Texture illisible", e);
            return 0;
        }
    }

    private static int pot(int v) {
        int p = 1;
        while (p < v && p < 2048) p <<= 1;
        if (p > v && p / 2 >= 16 && (p - v) > v - p / 2) p >>= 1;
        return p;
    }

    private static FloatBuffer floatBuffer(float[] a) {
        FloatBuffer fb = ByteBuffer.allocateDirect(a.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(a).position(0);
        return fb;
    }

    // ------------------------------------------------------------ shaders
    private String asset(String name) throws java.io.IOException {
        return new String(Files.readAll(ctx.getAssets().open(name)), "UTF-8");
    }

    private Program buildCarProgram(String vs, String fs, String defines) {
        int id = link(vs, defines + fs);
        Program p = new Program();
        p.id = id;
        p.aPos = GLES20.glGetAttribLocation(id, "aPos");
        p.aNor = GLES20.glGetAttribLocation(id, "aNor");
        p.aUv = GLES20.glGetAttribLocation(id, "aUv");
        p.uViewProj = GLES20.glGetUniformLocation(id, "uViewProj");
        p.uModel = GLES20.glGetUniformLocation(id, "uModel");
        p.uCam = GLES20.glGetUniformLocation(id, "uCam");
        p.uColor = GLES20.glGetUniformLocation(id, "uColor");
        p.uEmissive = GLES20.glGetUniformLocation(id, "uEmissive");
        p.uMetal = GLES20.glGetUniformLocation(id, "uMetal");
        p.uRough = GLES20.glGetUniformLocation(id, "uRough");
        p.uClear = GLES20.glGetUniformLocation(id, "uClear");
        p.uUseTex = GLES20.glGetUniformLocation(id, "uUseTex");
        p.uDarkBack = GLES20.glGetUniformLocation(id, "uDarkBack");
        p.uCutoff = GLES20.glGetUniformLocation(id, "uCutoff");
        p.uTex = GLES20.glGetUniformLocation(id, "uTex");
        return p;
    }

    private static int compile(int type, String src) {
        int s = GLES20.glCreateShader(type);
        GLES20.glShaderSource(s, src);
        GLES20.glCompileShader(s);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(s);
            GLES20.glDeleteShader(s);
            throw new RuntimeException("Compilation shader : " + log);
        }
        return s;
    }

    private static int link(String vs, String fs) {
        int v = compile(GLES20.GL_VERTEX_SHADER, vs);
        int f = compile(GLES20.GL_FRAGMENT_SHADER, fs);
        int p = GLES20.glCreateProgram();
        GLES20.glAttachShader(p, v);
        GLES20.glAttachShader(p, f);
        GLES20.glLinkProgram(p);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(p);
            GLES20.glDeleteProgram(p);
            throw new RuntimeException("Liaison shader : " + log);
        }
        return p;
    }

    /** Shaders de secours tres simples si le pilote refuse les principaux. */
    private static final String FALLBACK_VS =
            "attribute vec3 aPos; attribute vec3 aNor; attribute vec2 aUv;\n"
                    + "uniform mat4 uViewProj; uniform mat4 uModel; varying float vL;\n"
                    + "void main(){ vec3 n = normalize((uModel*vec4(aNor,0.0)).xyz);\n"
                    + " vL = 0.35 + 0.65*abs(dot(n, normalize(vec3(0.45,0.85,0.35))));\n"
                    + " gl_Position = uViewProj * uModel * vec4(aPos,1.0); }\n";
    private static final String FALLBACK_FS =
            "precision mediump float; varying float vL; uniform vec4 uColor;\n"
                    + "void main(){ gl_FragColor = vec4(uColor.rgb*vL, uColor.a); }\n";
}
