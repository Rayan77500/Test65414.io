package com.rayan.w204dash.gl;

/** Matrices 4x4 en colonnes (comme OpenGL / glTF), sans dependance Android. */
public final class M4 {

    private M4() {
    }

    public static float[] identity() {
        float[] m = new float[16];
        m[0] = m[5] = m[10] = m[15] = 1f;
        return m;
    }

    /** r = a * b */
    public static float[] mul(float[] a, float[] b) {
        float[] r = new float[16];
        for (int c = 0; c < 4; c++) {
            for (int row = 0; row < 4; row++) {
                float s = 0;
                for (int k = 0; k < 4; k++) {
                    s += a[k * 4 + row] * b[c * 4 + k];
                }
                r[c * 4 + row] = s;
            }
        }
        return r;
    }

    public static float[] translation(float x, float y, float z) {
        float[] m = identity();
        m[12] = x;
        m[13] = y;
        m[14] = z;
        return m;
    }

    public static float[] scale(float s) {
        float[] m = identity();
        m[0] = m[5] = m[10] = s;
        return m;
    }

    /** Rotation d'angle deg (degres) autour de l'axe (x,y,z) normalise. */
    public static float[] rotation(float deg, float x, float y, float z) {
        double a = Math.toRadians(deg);
        float c = (float) Math.cos(a), s = (float) Math.sin(a), t = 1 - c;
        float l = (float) Math.sqrt(x * x + y * y + z * z);
        x /= l;
        y /= l;
        z /= l;
        float[] m = identity();
        m[0] = t * x * x + c;
        m[1] = t * x * y + s * z;
        m[2] = t * x * z - s * y;
        m[4] = t * x * y - s * z;
        m[5] = t * y * y + c;
        m[6] = t * y * z + s * x;
        m[8] = t * x * z + s * y;
        m[9] = t * y * z - s * x;
        m[10] = t * z * z + c;
        return m;
    }

    /** Matrice depuis translation / rotation (quaternion x,y,z,w) / echelle (glTF). */
    public static float[] trs(float[] t, float[] q, float[] s) {
        float x = q[0], y = q[1], z = q[2], w = q[3];
        float[] m = identity();
        m[0] = (1 - 2 * (y * y + z * z)) * s[0];
        m[1] = (2 * (x * y + z * w)) * s[0];
        m[2] = (2 * (x * z - y * w)) * s[0];
        m[4] = (2 * (x * y - z * w)) * s[1];
        m[5] = (1 - 2 * (x * x + z * z)) * s[1];
        m[6] = (2 * (y * z + x * w)) * s[1];
        m[8] = (2 * (x * z + y * w)) * s[2];
        m[9] = (2 * (y * z - x * w)) * s[2];
        m[10] = (1 - 2 * (x * x + y * y)) * s[2];
        m[12] = t[0];
        m[13] = t[1];
        m[14] = t[2];
        return m;
    }

    public static void transformPoint(float[] m, float[] p, int off, float[] out, int oo) {
        float x = p[off], y = p[off + 1], z = p[off + 2];
        out[oo] = m[0] * x + m[4] * y + m[8] * z + m[12];
        out[oo + 1] = m[1] * x + m[5] * y + m[9] * z + m[13];
        out[oo + 2] = m[2] * x + m[6] * y + m[10] * z + m[14];
    }

    public static void transformDir(float[] m, float[] p, int off, float[] out, int oo) {
        float x = p[off], y = p[off + 1], z = p[off + 2];
        out[oo] = m[0] * x + m[4] * y + m[8] * z;
        out[oo + 1] = m[1] * x + m[5] * y + m[9] * z;
        out[oo + 2] = m[2] * x + m[6] * y + m[10] * z;
    }

    /** Inverse generale (null si non inversible). */
    public static float[] invert(float[] m) {
        float[] inv = new float[16];
        inv[0] = m[5] * m[10] * m[15] - m[5] * m[11] * m[14] - m[9] * m[6] * m[15] + m[9] * m[7] * m[14] + m[13] * m[6] * m[11] - m[13] * m[7] * m[10];
        inv[4] = -m[4] * m[10] * m[15] + m[4] * m[11] * m[14] + m[8] * m[6] * m[15] - m[8] * m[7] * m[14] - m[12] * m[6] * m[11] + m[12] * m[7] * m[10];
        inv[8] = m[4] * m[9] * m[15] - m[4] * m[11] * m[13] - m[8] * m[5] * m[15] + m[8] * m[7] * m[13] + m[12] * m[5] * m[11] - m[12] * m[7] * m[9];
        inv[12] = -m[4] * m[9] * m[14] + m[4] * m[10] * m[13] + m[8] * m[5] * m[14] - m[8] * m[6] * m[13] - m[12] * m[5] * m[10] + m[12] * m[6] * m[9];
        inv[1] = -m[1] * m[10] * m[15] + m[1] * m[11] * m[14] + m[9] * m[2] * m[15] - m[9] * m[3] * m[14] - m[13] * m[2] * m[11] + m[13] * m[3] * m[10];
        inv[5] = m[0] * m[10] * m[15] - m[0] * m[11] * m[14] - m[8] * m[2] * m[15] + m[8] * m[3] * m[14] + m[12] * m[2] * m[11] - m[12] * m[3] * m[10];
        inv[9] = -m[0] * m[9] * m[15] + m[0] * m[11] * m[13] + m[8] * m[1] * m[15] - m[8] * m[3] * m[13] - m[12] * m[1] * m[11] + m[12] * m[3] * m[9];
        inv[13] = m[0] * m[9] * m[14] - m[0] * m[10] * m[13] - m[8] * m[1] * m[14] + m[8] * m[2] * m[13] + m[12] * m[1] * m[10] - m[12] * m[2] * m[9];
        inv[2] = m[1] * m[6] * m[15] - m[1] * m[7] * m[14] - m[5] * m[2] * m[15] + m[5] * m[3] * m[14] + m[13] * m[2] * m[7] - m[13] * m[3] * m[6];
        inv[6] = -m[0] * m[6] * m[15] + m[0] * m[7] * m[14] + m[4] * m[2] * m[15] - m[4] * m[3] * m[14] - m[12] * m[2] * m[7] + m[12] * m[3] * m[6];
        inv[10] = m[0] * m[5] * m[15] - m[0] * m[7] * m[13] - m[4] * m[1] * m[15] + m[4] * m[3] * m[13] + m[12] * m[1] * m[7] - m[12] * m[3] * m[5];
        inv[14] = -m[0] * m[5] * m[14] + m[0] * m[6] * m[13] + m[4] * m[1] * m[14] - m[4] * m[2] * m[13] - m[12] * m[1] * m[6] + m[12] * m[2] * m[5];
        inv[3] = -m[1] * m[6] * m[11] + m[1] * m[7] * m[10] + m[5] * m[2] * m[11] - m[5] * m[3] * m[10] - m[9] * m[2] * m[7] + m[9] * m[3] * m[6];
        inv[7] = m[0] * m[6] * m[11] - m[0] * m[7] * m[10] - m[4] * m[2] * m[11] + m[4] * m[3] * m[10] + m[8] * m[2] * m[7] - m[8] * m[3] * m[6];
        inv[11] = -m[0] * m[5] * m[11] + m[0] * m[7] * m[9] + m[4] * m[1] * m[11] - m[4] * m[3] * m[9] - m[8] * m[1] * m[7] + m[8] * m[3] * m[5];
        inv[15] = m[0] * m[5] * m[10] - m[0] * m[6] * m[9] - m[4] * m[1] * m[10] + m[4] * m[2] * m[9] + m[8] * m[1] * m[6] - m[8] * m[2] * m[5];
        float det = m[0] * inv[0] + m[1] * inv[4] + m[2] * inv[8] + m[3] * inv[12];
        if (Math.abs(det) < 1e-12) return null;
        det = 1f / det;
        for (int i = 0; i < 16; i++) {
            inv[i] *= det;
        }
        return inv;
    }

    /** Projection perspective (fovy en degres). */
    public static float[] perspective(float fovy, float aspect, float near, float far) {
        float f = (float) (1.0 / Math.tan(Math.toRadians(fovy) / 2));
        float[] m = new float[16];
        m[0] = f / aspect;
        m[5] = f;
        m[10] = (far + near) / (near - far);
        m[11] = -1;
        m[14] = 2 * far * near / (near - far);
        return m;
    }

    public static float[] lookAt(float ex, float ey, float ez, float cx, float cy, float cz, float ux, float uy, float uz) {
        float fx = cx - ex, fy = cy - ey, fz = cz - ez;
        float rlf = (float) (1.0 / Math.sqrt(fx * fx + fy * fy + fz * fz));
        fx *= rlf;
        fy *= rlf;
        fz *= rlf;
        float sx = fy * uz - fz * uy, sy = fz * ux - fx * uz, sz = fx * uy - fy * ux;
        float rls = (float) (1.0 / Math.sqrt(sx * sx + sy * sy + sz * sz));
        sx *= rls;
        sy *= rls;
        sz *= rls;
        float ux2 = sy * fz - sz * fy, uy2 = sz * fx - sx * fz, uz2 = sx * fy - sy * fx;
        float[] m = identity();
        m[0] = sx;
        m[4] = sy;
        m[8] = sz;
        m[1] = ux2;
        m[5] = uy2;
        m[9] = uz2;
        m[2] = -fx;
        m[6] = -fy;
        m[10] = -fz;
        m[12] = -(sx * ex + sy * ey + sz * ez);
        m[13] = -(ux2 * ex + uy2 * ey + uz2 * ez);
        m[14] = fx * ex + fy * ey + fz * ez;
        return m;
    }
}
