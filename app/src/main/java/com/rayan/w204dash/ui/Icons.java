package com.rayan.w204dash.ui;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/**
 * Icones dessinees par programme sur une grille 24x24 (aucune image a
 * charger, nettes a toutes les tailles, compatibles Android 4.1).
 */
public final class Icons {

    public static final int HOME = 1, CAR = 2, SETTINGS = 3, DIAG = 4, PLAY = 5, PAUSE = 6, NEXT = 7, PREV = 8,
            DOOR = 9, RADAR = 10, INFO = 11, WRENCH = 12, BLUETOOTH = 13, MUSIC = 14, TEMP = 15, BATTERY = 16,
            ODOMETER = 17, FUEL = 18, SEATBELT = 19, WASHER = 20, ENGINE = 21, FAN = 22, GEARBOX = 23, RESET = 24,
            PLUS = 25, CHECK = 26, CLOSE = 27, FOLDER = 28, PALETTE = 29, LINK = 30, SAVE = 31, TRASH = 32,
            WARNING = 33, SPEED = 34, ROTATE = 35, CUBE = 36, EDIT = 37, CALENDAR = 38;

    private Icons() {
    }

    public static void draw(Canvas c, int icon, float size, int color) {
        Paint s = new Paint(Paint.ANTI_ALIAS_FLAG);
        s.setStyle(Paint.Style.STROKE);
        s.setStrokeWidth(1.9f);
        s.setStrokeCap(Paint.Cap.ROUND);
        s.setStrokeJoin(Paint.Join.ROUND);
        s.setColor(color);
        Paint f = new Paint(Paint.ANTI_ALIAS_FLAG);
        f.setStyle(Paint.Style.FILL);
        f.setColor(color);
        c.save();
        c.scale(size / 24f, size / 24f);
        Path p = new Path();
        switch (icon) {
            case HOME:
                p.moveTo(3, 11);
                p.lineTo(12, 3.5f);
                p.lineTo(21, 11);
                p.moveTo(5.5f, 9.5f);
                p.lineTo(5.5f, 20.5f);
                p.lineTo(18.5f, 20.5f);
                p.lineTo(18.5f, 9.5f);
                p.moveTo(10, 20.5f);
                p.lineTo(10, 14.5f);
                p.lineTo(14, 14.5f);
                p.lineTo(14, 20.5f);
                c.drawPath(p, s);
                break;
            case CAR:
                p.moveTo(2.5f, 15.5f);
                p.lineTo(2.5f, 12.5f);
                p.quadTo(2.8f, 10.6f, 5.5f, 10.2f);
                p.lineTo(8.2f, 6.6f);
                p.quadTo(9, 5.6f, 10.5f, 5.6f);
                p.lineTo(15, 5.6f);
                p.quadTo(16.4f, 5.6f, 17.2f, 6.8f);
                p.lineTo(19.4f, 10.2f);
                p.quadTo(21.5f, 10.7f, 21.5f, 12.8f);
                p.lineTo(21.5f, 15.5f);
                p.close();
                c.drawPath(p, s);
                c.drawLine(5.5f, 10.2f, 19.4f, 10.2f, s);
                c.drawCircle(7, 16, 2.1f, f);
                c.drawCircle(17, 16, 2.1f, f);
                break;
            case SETTINGS: {
                for (int i = 0; i < 8; i++) {
                    double a0 = Math.toRadians(i * 45 - 13), a1 = Math.toRadians(i * 45 + 13);
                    double b0 = Math.toRadians(i * 45 + 22.5 - 10), b1 = Math.toRadians(i * 45 + 22.5 + 10);
                    float[] pts = {
                            (float) (12 + 10 * Math.cos(a0)), (float) (12 + 10 * Math.sin(a0)),
                            (float) (12 + 10 * Math.cos(a1)), (float) (12 + 10 * Math.sin(a1)),
                            (float) (12 + 7.4 * Math.cos(b0)), (float) (12 + 7.4 * Math.sin(b0)),
                            (float) (12 + 7.4 * Math.cos(b1)), (float) (12 + 7.4 * Math.sin(b1))};
                    if (i == 0) p.moveTo(pts[0], pts[1]);
                    else p.lineTo(pts[0], pts[1]);
                    p.lineTo(pts[2], pts[3]);
                    p.lineTo(pts[4], pts[5]);
                    p.lineTo(pts[6], pts[7]);
                }
                p.close();
                c.drawPath(p, s);
                c.drawCircle(12, 12, 3.2f, s);
                break;
            }
            case DIAG:
                p.moveTo(2, 12.5f);
                p.lineTo(6.5f, 12.5f);
                p.lineTo(9, 7);
                p.lineTo(12, 18);
                p.lineTo(15, 5);
                p.lineTo(17.5f, 12.5f);
                p.lineTo(22, 12.5f);
                c.drawPath(p, s);
                break;
            case PLAY:
                p.moveTo(8, 5);
                p.lineTo(19.5f, 12);
                p.lineTo(8, 19);
                p.close();
                c.drawPath(p, f);
                break;
            case PAUSE:
                c.drawRoundRect(new RectF(6.5f, 5, 10.5f, 19), 1, 1, f);
                c.drawRoundRect(new RectF(13.5f, 5, 17.5f, 19), 1, 1, f);
                break;
            case NEXT:
                p.moveTo(5, 5.5f);
                p.lineTo(15, 12);
                p.lineTo(5, 18.5f);
                p.close();
                c.drawPath(p, f);
                c.drawRoundRect(new RectF(16, 5.5f, 19, 18.5f), 1, 1, f);
                break;
            case PREV:
                p.moveTo(19, 5.5f);
                p.lineTo(9, 12);
                p.lineTo(19, 18.5f);
                p.close();
                c.drawPath(p, f);
                c.drawRoundRect(new RectF(5, 5.5f, 8, 18.5f), 1, 1, f);
                break;
            case DOOR:
                p.moveTo(5, 20.5f);
                p.lineTo(5, 10);
                p.lineTo(11, 3.5f);
                p.lineTo(19, 3.5f);
                p.lineTo(19, 20.5f);
                p.close();
                c.drawPath(p, s);
                c.drawLine(5, 10, 19, 10, s);
                c.drawLine(14, 13.5f, 17, 13.5f, s);
                break;
            case RADAR:
                c.drawRoundRect(new RectF(8.5f, 15, 15.5f, 22), 1.5f, 1.5f, s);
                c.drawArc(new RectF(7, 9, 17, 19), 225, 90, false, s);
                c.drawArc(new RectF(4, 6, 20, 22), 225, 90, false, s);
                c.drawArc(new RectF(1, 3, 23, 25), 225, 90, false, s);
                break;
            case INFO:
                c.drawCircle(12, 12, 9.5f, s);
                c.drawLine(12, 11, 12, 17, s);
                c.drawCircle(12, 7.6f, 1.2f, f);
                break;
            case WRENCH: {
                Paint t = new Paint(s);
                t.setStrokeWidth(3.2f);
                c.drawLine(5, 19, 13.5f, 10.5f, t);
                c.drawArc(new RectF(12, 3, 21, 12), 100, 300, false, s);
                break;
            }
            case BLUETOOTH:
                p.moveTo(7, 7.5f);
                p.lineTo(17, 16.5f);
                p.lineTo(12, 21);
                p.lineTo(12, 3);
                p.lineTo(17, 7.5f);
                p.lineTo(7, 16.5f);
                c.drawPath(p, s);
                break;
            case MUSIC:
                c.drawLine(10, 17, 10, 5, s);
                c.drawLine(19, 15, 19, 3.5f, s);
                {
                    Paint t = new Paint(s);
                    t.setStrokeWidth(2.6f);
                    c.drawLine(10, 5, 19, 3.5f, t);
                }
                c.drawCircle(7.5f, 17.2f, 2.6f, f);
                c.drawCircle(16.5f, 15.2f, 2.6f, f);
                break;
            case TEMP:
                c.drawRoundRect(new RectF(9.5f, 2.5f, 14.5f, 15), 2.5f, 2.5f, s);
                c.drawCircle(12, 18, 3.6f, s);
                c.drawCircle(12, 18, 1.6f, f);
                c.drawLine(12, 9, 12, 16, s);
                c.drawLine(16.5f, 6, 19, 6, s);
                c.drawLine(16.5f, 9.5f, 19, 9.5f, s);
                break;
            case BATTERY:
                c.drawRoundRect(new RectF(3, 7.5f, 21, 19.5f), 2, 2, s);
                c.drawLine(6.5f, 5, 9.5f, 5, s);
                c.drawLine(14.5f, 5, 17.5f, 5, s);
                c.drawLine(6.5f, 13.5f, 10, 13.5f, s);
                c.drawLine(8.25f, 11.75f, 8.25f, 15.25f, s);
                c.drawLine(14.5f, 13.5f, 18, 13.5f, s);
                break;
            case ODOMETER:
            case SPEED:
                c.drawArc(new RectF(3, 5, 21, 23), 160, 220, false, s);
                c.drawLine(12, 14, icon == SPEED ? 16.5f : 7.5f, 8.5f, s);
                c.drawCircle(12, 14, 1.6f, f);
                break;
            case FUEL:
                c.drawRoundRect(new RectF(4, 4, 13.5f, 21), 1.5f, 1.5f, s);
                c.drawRect(6.5f, 6.5f, 11, 10.5f, f);
                p.moveTo(13.5f, 8);
                p.lineTo(17, 8);
                p.lineTo(19.5f, 10.5f);
                p.lineTo(19.5f, 17.5f);
                p.quadTo(19.5f, 19, 18, 19);
                p.quadTo(16.5f, 19, 16.5f, 17.5f);
                p.lineTo(16.5f, 13);
                p.lineTo(13.5f, 13);
                c.drawPath(p, s);
                break;
            case SEATBELT:
                c.drawCircle(12, 4.5f, 2.3f, s);
                p.moveTo(6.5f, 21);
                p.lineTo(6.5f, 12);
                p.quadTo(6.5f, 8.5f, 10, 8.5f);
                p.lineTo(14, 8.5f);
                p.quadTo(17.5f, 8.5f, 17.5f, 12);
                p.lineTo(17.5f, 21);
                c.drawPath(p, s);
                {
                    Paint t = new Paint(s);
                    t.setStrokeWidth(2.8f);
                    c.drawLine(8, 9.5f, 16.5f, 19.5f, t);
                }
                break;
            case WASHER:
                p.moveTo(3, 19);
                p.quadTo(12, 11, 21, 19);
                c.drawPath(p, s);
                c.drawLine(12, 15, 12, 19, s);
                c.drawLine(8, 6, 9.2f, 9, s);
                c.drawLine(12, 4, 12, 8, s);
                c.drawLine(16, 6, 14.8f, 9, s);
                break;
            case ENGINE:
                p.moveTo(4, 10);
                p.lineTo(7, 10);
                p.lineTo(7, 7);
                p.lineTo(15, 7);
                p.lineTo(15, 10);
                p.lineTo(18, 10);
                p.lineTo(18, 12.5f);
                p.lineTo(21, 12.5f);
                p.lineTo(21, 16.5f);
                p.lineTo(18, 16.5f);
                p.lineTo(18, 19);
                p.lineTo(8.5f, 19);
                p.lineTo(6.5f, 16.5f);
                p.lineTo(4, 16.5f);
                p.close();
                c.drawPath(p, s);
                c.drawLine(9, 4.5f, 13, 4.5f, s);
                c.drawLine(11, 4.5f, 11, 7, s);
                break;
            case FAN:
                c.drawCircle(12, 12, 1.8f, f);
                for (int i = 0; i < 3; i++) {
                    c.save();
                    c.rotate(i * 120, 12, 12);
                    Path b = new Path();
                    b.moveTo(12, 10);
                    b.quadTo(8, 3, 13, 2.5f);
                    b.quadTo(17, 3, 14, 10.5f);
                    c.drawPath(b, s);
                    c.restore();
                }
                break;
            case GEARBOX:
                c.drawLine(6, 6, 6, 18, s);
                c.drawLine(12, 6, 12, 18, s);
                c.drawLine(18, 6, 18, 12, s);
                c.drawLine(6, 12, 18, 12, s);
                c.drawCircle(6, 5, 1.7f, f);
                c.drawCircle(12, 5, 1.7f, f);
                c.drawCircle(18, 5, 1.7f, f);
                c.drawCircle(6, 19, 1.7f, f);
                c.drawCircle(12, 19, 1.7f, f);
                break;
            case RESET:
            case ROTATE:
                c.drawArc(new RectF(4, 4, 20, 20), icon == RESET ? -60 : 30, 300, false, s);
                if (icon == RESET) {
                    p.moveTo(16.5f, 2.5f);
                    p.lineTo(16.5f, 6.8f);
                    p.lineTo(20.8f, 6.8f);
                } else {
                    p.moveTo(20.5f, 9.5f);
                    p.lineTo(19.2f, 14.2f);
                    p.lineTo(15, 12);
                }
                c.drawPath(p, s);
                break;
            case PLUS:
                c.drawLine(12, 5, 12, 19, s);
                c.drawLine(5, 12, 19, 12, s);
                break;
            case CHECK:
                p.moveTo(5, 12.5f);
                p.lineTo(10, 17.5f);
                p.lineTo(19.5f, 7);
                c.drawPath(p, s);
                break;
            case CLOSE:
                c.drawLine(6, 6, 18, 18, s);
                c.drawLine(18, 6, 6, 18, s);
                break;
            case FOLDER:
                p.moveTo(3, 6.5f);
                p.lineTo(9, 6.5f);
                p.lineTo(11, 8.5f);
                p.lineTo(21, 8.5f);
                p.lineTo(21, 19);
                p.lineTo(3, 19);
                p.close();
                c.drawPath(p, s);
                break;
            case PALETTE:
                c.drawCircle(12, 12, 9.2f, s);
                c.drawCircle(8, 9, 1.5f, f);
                c.drawCircle(12.5f, 7, 1.5f, f);
                c.drawCircle(16.5f, 10, 1.5f, f);
                c.drawCircle(9, 15.5f, 2.3f, s);
                break;
            case LINK:
                c.drawRoundRect(new RectF(2.5f, 8.5f, 12.5f, 15.5f), 3.5f, 3.5f, s);
                c.drawRoundRect(new RectF(11.5f, 8.5f, 21.5f, 15.5f), 3.5f, 3.5f, s);
                break;
            case SAVE:
                c.drawLine(12, 3.5f, 12, 15, s);
                p.moveTo(7.5f, 10.5f);
                p.lineTo(12, 15);
                p.lineTo(16.5f, 10.5f);
                c.drawPath(p, s);
                c.drawLine(4.5f, 20, 19.5f, 20, s);
                break;
            case TRASH:
                c.drawLine(4, 6.5f, 20, 6.5f, s);
                c.drawLine(9.5f, 6.5f, 10, 3.5f, s);
                c.drawLine(10, 3.5f, 14, 3.5f, s);
                c.drawLine(14, 3.5f, 14.5f, 6.5f, s);
                p.moveTo(6, 6.5f);
                p.lineTo(7.2f, 20.5f);
                p.lineTo(16.8f, 20.5f);
                p.lineTo(18, 6.5f);
                c.drawPath(p, s);
                break;
            case WARNING:
                p.moveTo(12, 3);
                p.lineTo(22, 20.5f);
                p.lineTo(2, 20.5f);
                p.close();
                c.drawPath(p, s);
                c.drawLine(12, 9.5f, 12, 14.5f, s);
                c.drawCircle(12, 17.3f, 1.1f, f);
                break;
            case CUBE:
                p.moveTo(12, 2.5f);
                p.lineTo(20.5f, 7);
                p.lineTo(20.5f, 17);
                p.lineTo(12, 21.5f);
                p.lineTo(3.5f, 17);
                p.lineTo(3.5f, 7);
                p.close();
                p.moveTo(3.5f, 7);
                p.lineTo(12, 11.5f);
                p.lineTo(20.5f, 7);
                p.moveTo(12, 11.5f);
                p.lineTo(12, 21.5f);
                c.drawPath(p, s);
                break;
            case EDIT:
                p.moveTo(4, 20);
                p.lineTo(4.8f, 15.8f);
                p.lineTo(15.5f, 5);
                p.lineTo(19, 8.5f);
                p.lineTo(8.2f, 19.2f);
                p.close();
                c.drawPath(p, s);
                break;
            case CALENDAR:
                c.drawRoundRect(new RectF(3.5f, 5, 20.5f, 20.5f), 2, 2, s);
                c.drawLine(3.5f, 9.5f, 20.5f, 9.5f, s);
                c.drawLine(8, 3, 8, 6.5f, s);
                c.drawLine(16, 3, 16, 6.5f, s);
                break;
            default:
                c.drawCircle(12, 12, 8, s);
                break;
        }
        c.restore();
    }
}
