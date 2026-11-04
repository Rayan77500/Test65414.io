package com.rayan.w204dash.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import com.rayan.w204dash.data.VehicleData;

/**
 * Voiture vue de dessus (avant en haut). Mode "ouvrants" : portes, capot et
 * coffre dessines ouverts/fermes. Mode "radars" : arcs de distance avant et
 * arriere colores selon la proximite.
 */
public class CarTopView extends View {

    public interface OnOpenableTap {
        void onTap(int index);
    }

    private final boolean radarMode;
    private final int[] open = new int[VehicleData.OPENABLES];
    private final int[] rear = {-1, -1, -1, -1};
    private final int[] front = {-1, -1, -1, -1};
    private int paintColor = 0xFF8C9196;
    private OnOpenableTap tapListener;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rf = new RectF();
    // geometrie calculee a chaque dessin
    private float cx, cy, cw, cl;

    public CarTopView(Context c, boolean radarMode) {
        super(c);
        this.radarMode = radarMode;
        java.util.Arrays.fill(open, -1);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeJoin(Paint.Join.ROUND);
        line.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
    }

    public void setPaintColor(int c) {
        if (paintColor != c) {
            paintColor = c;
            invalidate();
        }
    }

    public void setOnOpenableTap(OnOpenableTap l) {
        tapListener = l;
        setClickable(l != null);
    }

    public void update(VehicleData d) {
        boolean changed = false;
        for (int i = 0; i < VehicleData.OPENABLES; i++) {
            if (open[i] != d.open[i]) {
                open[i] = d.open[i];
                changed = true;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (rear[i] != d.radarRear[i] || front[i] != d.radarFront[i]) {
                rear[i] = d.radarRear[i];
                front[i] = d.radarFront[i];
                changed = true;
            }
        }
        if (changed) invalidate();
    }

    /** Couleur selon la distance (cm). */
    public static int distanceColor(int cm) {
        if (cm < 0) return Theme.UNKNOWN;
        if (cm < 40) return Theme.BAD;
        if (cm < 80) return Theme.WARN;
        if (cm < 120) return 0xFFE6E35A;
        return Theme.OK;
    }

    private float X(float u) {
        return cx + u * cw;
    }

    private float Y(float v) {
        return cy + v * cl;
    }

    @Override
    protected void onDraw(Canvas c) {
        float w = getWidth(), h = getHeight();
        float lenRatio = 4.58f / 1.77f;
        float availH = h * (radarMode ? 0.50f : 0.84f);
        float availW = w * (radarMode ? 0.5f : 0.42f);
        cl = Math.min(availH, availW * lenRatio);
        cw = cl / lenRatio;
        cx = w / 2;
        cy = h / 2;
        float sw = Math.max(1.5f, cw * 0.025f);
        line.setStrokeWidth(sw);

        if (radarMode) drawRadars(c);

        // ombre
        fill.setColor(0x55000000);
        rf.set(X(-0.56f), Y(-0.51f), X(0.56f), Y(0.53f));
        c.drawRoundRect(rf, cw * 0.3f, cw * 0.3f, fill);

        // carrosserie
        int body = blend(paintColor, 0xFF000000, 0.25f);
        bodyPath();
        fill.setColor(body);
        c.drawPath(path, fill);
        line.setColor(blend(paintColor, 0xFFFFFFFF, 0.35f));
        c.drawPath(path, line);

        // vitres
        fill.setColor(0xCC0C1118);
        path.reset();
        path.moveTo(X(-0.40f), Y(-0.165f));
        path.lineTo(X(0.40f), Y(-0.165f));
        path.lineTo(X(0.34f), Y(-0.05f));
        path.lineTo(X(-0.34f), Y(-0.05f));
        path.close();
        c.drawPath(path, fill);
        path.reset();
        path.moveTo(X(-0.35f), Y(0.20f));
        path.lineTo(X(0.35f), Y(0.20f));
        path.lineTo(X(0.40f), Y(0.29f));
        path.lineTo(X(-0.40f), Y(0.29f));
        path.close();
        c.drawPath(path, fill);
        // toit
        fill.setColor(blend(body, 0xFFFFFFFF, 0.06f));
        rf.set(X(-0.34f), Y(-0.05f), X(0.34f), Y(0.20f));
        c.drawRoundRect(rf, cw * 0.06f, cw * 0.06f, fill);

        if (!radarMode) {
            drawHood(c, VehicleData.HOOD);
            drawHood(c, VehicleData.TRUNK);
            drawDoor(c, VehicleData.DOOR_FL, true, -0.15f, 0.07f);
            drawDoor(c, VehicleData.DOOR_RL, true, 0.07f, 0.27f);
            drawDoor(c, VehicleData.DOOR_FR, false, -0.15f, 0.07f);
            drawDoor(c, VehicleData.DOOR_RR, false, 0.07f, 0.27f);
        }
        // retroviseurs
        fill.setColor(body);
        rf.set(X(-0.60f), Y(-0.15f), X(-0.47f), Y(-0.11f));
        c.drawRoundRect(rf, cw * 0.03f, cw * 0.03f, fill);
        rf.set(X(0.47f), Y(-0.15f), X(0.60f), Y(-0.11f));
        c.drawRoundRect(rf, cw * 0.03f, cw * 0.03f, fill);
        // phares / feux
        fill.setColor(0xFFE8F0FF);
        rf.set(X(-0.44f), Y(-0.495f), X(-0.22f), Y(-0.47f));
        c.drawRoundRect(rf, 4, 4, fill);
        rf.set(X(0.22f), Y(-0.495f), X(0.44f), Y(-0.47f));
        c.drawRoundRect(rf, 4, 4, fill);
        fill.setColor(0xFFE0303A);
        rf.set(X(-0.46f), Y(0.475f), X(-0.24f), Y(0.497f));
        c.drawRoundRect(rf, 4, 4, fill);
        rf.set(X(0.24f), Y(0.475f), X(0.46f), Y(0.497f));
        c.drawRoundRect(rf, 4, 4, fill);
    }

    private void bodyPath() {
        path.reset();
        path.moveTo(X(-0.30f), Y(-0.5f));
        path.quadTo(X(0f), Y(-0.515f), X(0.30f), Y(-0.5f));
        path.quadTo(X(0.48f), Y(-0.49f), X(0.49f), Y(-0.40f));
        path.lineTo(X(0.5f), Y(0.40f));
        path.quadTo(X(0.49f), Y(0.49f), X(0.32f), Y(0.5f));
        path.quadTo(X(0f), Y(0.51f), X(-0.32f), Y(0.5f));
        path.quadTo(X(-0.49f), Y(0.49f), X(-0.5f), Y(0.40f));
        path.lineTo(X(-0.49f), Y(-0.40f));
        path.quadTo(X(-0.48f), Y(-0.49f), X(-0.30f), Y(-0.5f));
        path.close();
    }

    private int stateColor(int i) {
        return open[i] == 1 ? Theme.WARN : open[i] == 0 ? Theme.OK : Theme.UNKNOWN;
    }

    private void drawHood(Canvas c, int i) {
        boolean hood = i == VehicleData.HOOD;
        float y0 = hood ? -0.47f : 0.31f, y1 = hood ? -0.19f : 0.47f;
        rf.set(X(-0.42f), Y(y0), X(0.42f), Y(y1));
        if (open[i] == 1) {
            fill.setColor(Theme.withAlpha(Theme.WARN, 0x55));
            c.drawRoundRect(rf, cw * 0.08f, cw * 0.08f, fill);
        }
        line.setColor(stateColor(i));
        if (open[i] < 0) line.setPathEffect(new DashPathEffect(new float[]{cw * 0.05f, cw * 0.04f}, 0));
        c.drawRoundRect(rf, cw * 0.08f, cw * 0.08f, line);
        line.setPathEffect(null);
    }

    private void drawDoor(Canvas c, int i, boolean left, float y0, float y1) {
        float s = left ? -1 : 1;
        float hingeX = X(s * 0.5f), hingeY = Y(y0);
        float len = (y1 - y0) * cl;
        float thick = cw * 0.075f;
        c.save();
        if (open[i] == 1) c.rotate(left ? 50 : -50, hingeX, hingeY);
        rf.set(left ? hingeX - thick * 0.5f : hingeX - thick * 0.5f, hingeY, hingeX + thick * 0.5f, hingeY + len);
        fill.setColor(open[i] == 1 ? Theme.withAlpha(Theme.WARN, 0xD0) : blend(paintColor, 0xFF000000, 0.15f));
        c.drawRoundRect(rf, thick * 0.4f, thick * 0.4f, fill);
        line.setColor(stateColor(i));
        if (open[i] < 0) line.setPathEffect(new DashPathEffect(new float[]{cw * 0.05f, cw * 0.04f}, 0));
        c.drawRoundRect(rf, thick * 0.4f, thick * 0.4f, line);
        line.setPathEffect(null);
        c.restore();
    }

    private void drawRadars(Canvas c) {
        float rx = cx, ryRear = Y(0.5f), ryFront = Y(-0.5f);
        float[] radii = {cw * 0.42f, cw * 0.72f, cw * 1.02f};
        float band = cw * 0.17f;
        Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(band);
        arc.setStrokeCap(Paint.Cap.BUTT);
        for (int side = 0; side < 2; side++) {
            int[] d = side == 0 ? rear : front;
            float y = side == 0 ? ryRear : ryFront;
            for (int i = 0; i < 4; i++) {
                float a0 = side == 0 ? 130 - (i + 1) * 20 : 230 + i * 20;
                int dist = d[i];
                for (int b = 0; b < 3; b++) {
                    float r = radii[b];
                    rf.set(rx - r, y - r, rx + r, y + r);
                    // bande b allumee si l'obstacle est a moins de 45 / 90 / 150 cm
                    float limit = b == 0 ? 45 : b == 1 ? 90 : 150;
                    boolean lit = dist >= 0 && dist < limit;
                    int col;
                    if (dist < 0) col = 0x1FFFFFFF;
                    else if (lit) col = distanceColor(dist);
                    else col = 0x26FFFFFF;
                    arc.setColor(col);
                    c.drawArc(rf, a0 + 1.5f, 17f, false, arc);
                }
            }
        }
    }

    static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * t) << 16) | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (tapListener == null || radarMode) return super.onTouchEvent(e);
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float u = (e.getX() - cx) / cw;
            float v = (e.getY() - cy) / cl;
            int hit = -1;
            if (Math.abs(u) <= 0.95f && v >= -0.55f && v <= 0.55f) {
                if (v < -0.19f) hit = VehicleData.HOOD;
                else if (v > 0.30f) hit = VehicleData.TRUNK;
                else if (u < 0) hit = v < 0.07f ? VehicleData.DOOR_FL : VehicleData.DOOR_RL;
                else hit = v < 0.07f ? VehicleData.DOOR_FR : VehicleData.DOOR_RR;
            }
            if (hit >= 0) {
                playSoundEffect(android.view.SoundEffectConstants.CLICK);
                tapListener.onTap(hit);
            }
        }
        return true;
    }
}
