package com.rayan.w204dash.gl;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Voiture prete a afficher : sommets ramenes dans un repere commun (avant
 * vers +Z, gauche vers +X, sol a y=0, longueur 4,58 m), pieces ouvrantes
 * reconnues par leur nom (door_fl, hood, trunk, porte, capot, coffre...) et
 * charnieres calculees automatiquement. Sans dependance Android.
 */
public final class CarScene {

    public static final int PARTS = 6;
    public static final float TARGET_LENGTH = 4.58f;

    public static final class Item {
        public String name;
        public float[] pos;
        public float[] nor;
        public float[] uv;
        public int[] idx;
        public GlbModel.Material mat;
        public int image = -1;
        public int part = -1;
        public boolean paint;
        public final float[] min = new float[3];
        public final float[] max = new float[3];

        public int triangles() {
            return idx.length / 3;
        }

        void bounds() {
            min[0] = min[1] = min[2] = Float.MAX_VALUE;
            max[0] = max[1] = max[2] = -Float.MAX_VALUE;
            for (int i = 0; i < pos.length; i += 3) {
                for (int k = 0; k < 3; k++) {
                    float v = pos[i + k];
                    if (v < min[k]) min[k] = v;
                    if (v > max[k]) max[k] = v;
                }
            }
        }

        float cx() {
            return (min[0] + max[0]) / 2;
        }

        float cz() {
            return (min[2] + max[2]) / 2;
        }
    }

    public static final class Part {
        public boolean present;
        public final float[] min = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE};
        public final float[] max = {-Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        public final float[] pivot = new float[3];
        public final float[] axis = new float[3];
        public float angle;
    }

    public final List<Item> items = new ArrayList<Item>();
    public List<GlbModel.Image> images = new ArrayList<GlbModel.Image>();
    public final Part[] parts = new Part[PARTS];
    public final float[] min = new float[3];
    public final float[] max = new float[3];
    public boolean generated;
    public int triangles;
    public String info = "";

    private CarScene() {
        for (int i = 0; i < PARTS; i++) parts[i] = new Part();
    }

    // ---------------------------------------------------------------- noms
    static List<String> tokens(String name) {
        List<String> out = new ArrayList<String>();
        if (name == null) return out;
        // separe aussi les mots en "CamelCase"
        String s = name.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.US);
        for (String t : s.split("[^a-z0-9]+")) {
            if (t.length() > 0) out.add(t);
        }
        return out;
    }

    static final int KIND_NONE = 0, KIND_DOOR = 1, KIND_HOOD = 2, KIND_TRUNK = 3;

    static int kindOf(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        if (n.contains("hood") || n.contains("bonnet") || n.contains("capot")) return KIND_HOOD;
        if (n.contains("trunk") || n.contains("coffre") || n.contains("tailgate") || n.contains("decklid")
                || n.contains("bootlid") || n.contains("boot_lid") || n.contains("hayon")) return KIND_TRUNK;
        for (String t : tokens(name)) {
            if (t.equals("boot")) return KIND_TRUNK;
            if (t.startsWith("door") || t.startsWith("porte") || t.startsWith("portiere")) return KIND_DOOR;
        }
        if (n.contains("door")) return KIND_DOOR;
        return KIND_NONE;
    }

    static boolean isPaint(GlbModel.Material m) {
        if (m == null) return false;
        String n = m.name.toLowerCase(Locale.US);
        return n.contains("paint") || n.contains("carpaint") || n.contains("peinture") || n.contains("lack")
                || n.equals("body") || n.contains("body_paint") || n.contains("bodycolor") || n.contains("car_body")
                || n.contains("exterior");
    }

    static boolean isGround(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.US);
        return n.contains("ground") || n.contains("floor") || n.contains("shadow") || n.equals("plane")
                || n.startsWith("plane.") || n.contains("backdrop");
    }

    // ---------------------------------------------------------------- build
    /**
     * @param userRotation rotation supplementaire choisie dans les Reglages (degres, autour de Y)
     */
    public static CarScene build(GlbModel m, int userRotation) {
        CarScene sc = new CarScene();
        sc.generated = m.generator != null && m.generator.contains("W204 Dash");
        sc.images = m.images;
        for (int r : m.roots) {
            sc.visit(m, r, M4.identity(), KIND_NONE, null);
        }
        if (sc.items.isEmpty()) {
            // modele sans scene / noeuds : on prend les maillages tels quels
            for (int i = 0; i < m.meshes.size(); i++) {
                sc.addMesh(m, m.meshes.get(i), M4.identity(), KIND_NONE, m.meshes.get(i).name);
            }
        }
        sc.normalize(userRotation);
        sc.assignDoors();
        sc.computeHinges();
        int nPaint = 0;
        for (Item it : sc.items) {
            if (it.paint) nPaint++;
        }
        if (nPaint == 0) sc.guessPaint();
        StringBuilder sb = new StringBuilder();
        sb.append(sc.items.size()).append(" éléments, ").append(sc.triangles).append(" triangles. Ouvrants : ");
        String[] names = {"porte AVG", "porte AVD", "porte ARG", "porte ARD", "capot", "coffre"};
        int found = 0;
        for (int i = 0; i < PARTS; i++) {
            if (sc.parts[i].present) {
                sb.append(found++ > 0 ? ", " : "").append(names[i]);
            }
        }
        if (found == 0) sb.append("aucun (nommez les pièces door_fl, hood, trunk…)");
        sc.info = sb.toString();
        return sc;
    }

    private void visit(GlbModel m, int ni, float[] parent, int kind, String partName) {
        if (ni < 0 || ni >= m.nodes.size()) return;
        GlbModel.Node n = m.nodes.get(ni);
        float[] world = M4.mul(parent, n.matrix);
        int k = kind;
        String pn = partName;
        if (k == KIND_NONE) {
            k = kindOf(n.name);
            if (k != KIND_NONE) pn = n.name;
        }
        if (n.mesh >= 0 && n.mesh < m.meshes.size()) {
            GlbModel.Mesh mesh = m.meshes.get(n.mesh);
            int mk = k;
            if (mk == KIND_NONE) mk = kindOf(mesh.name);
            addMesh(m, mesh, world, mk, n.name.length() > 0 ? n.name : mesh.name);
        }
        for (int c : n.children) {
            visit(m, c, world, k, pn);
        }
    }

    private void addMesh(GlbModel m, GlbModel.Mesh mesh, float[] world, int kind, String name) {
        if (isGround(name)) return;
        float[] normalMat = M4.invert(world);
        for (GlbModel.Primitive p : mesh.primitives) {
            Item it = new Item();
            it.name = name;
            int n = p.pos.length / 3;
            it.pos = new float[p.pos.length];
            for (int i = 0; i < n; i++) M4.transformPoint(world, p.pos, i * 3, it.pos, i * 3);
            if (p.nor != null) {
                it.nor = new float[p.nor.length];
                for (int i = 0; i < n; i++) {
                    // normale : transposee de l'inverse
                    float x = p.nor[i * 3], y = p.nor[i * 3 + 1], z = p.nor[i * 3 + 2];
                    float[] im = normalMat != null ? normalMat : M4.identity();
                    float nx = im[0] * x + im[1] * y + im[2] * z;
                    float ny = im[4] * x + im[5] * y + im[6] * z;
                    float nz = im[8] * x + im[9] * y + im[10] * z;
                    float l = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
                    if (l < 1e-12f) l = 1;
                    it.nor[i * 3] = nx / l;
                    it.nor[i * 3 + 1] = ny / l;
                    it.nor[i * 3 + 2] = nz / l;
                }
            }
            it.uv = p.uv;
            it.idx = p.idx;
            if (p.material >= 0 && p.material < m.materials.size()) {
                it.mat = m.materials.get(p.material);
                it.image = it.mat.image;
            } else {
                it.mat = new GlbModel.Material();
                it.mat.metallic = 0.2f;
                it.mat.roughness = 0.5f;
                it.mat.color = new float[]{0.7f, 0.7f, 0.7f, 1f};
            }
            it.paint = isPaint(it.mat);
            it.part = kind == KIND_HOOD ? 4 : kind == KIND_TRUNK ? 5 : kind == KIND_DOOR ? 100 : -1;
            if (it.nor == null) computeNormals(it);
            it.bounds();
            items.add(it);
            triangles += it.triangles();
        }
    }

    static void computeNormals(Item it) {
        float[] nor = new float[it.pos.length];
        float[] p = it.pos;
        for (int t = 0; t + 2 < it.idx.length; t += 3) {
            int a = it.idx[t] * 3, b = it.idx[t + 1] * 3, c = it.idx[t + 2] * 3;
            float ux = p[b] - p[a], uy = p[b + 1] - p[a + 1], uz = p[b + 2] - p[a + 2];
            float vx = p[c] - p[a], vy = p[c + 1] - p[a + 1], vz = p[c + 2] - p[a + 2];
            float nx = uy * vz - uz * vy, ny = uz * vx - ux * vz, nz = ux * vy - uy * vx;
            for (int v : new int[]{a, b, c}) {
                nor[v] += nx;
                nor[v + 1] += ny;
                nor[v + 2] += nz;
            }
        }
        for (int i = 0; i < nor.length; i += 3) {
            float l = (float) Math.sqrt(nor[i] * nor[i] + nor[i + 1] * nor[i + 1] + nor[i + 2] * nor[i + 2]);
            if (l < 1e-12f) {
                nor[i + 1] = 1;
            } else {
                nor[i] /= l;
                nor[i + 1] /= l;
                nor[i + 2] /= l;
            }
        }
        it.nor = nor;
    }

    private void bounds(float[] mn, float[] mx) {
        mn[0] = mn[1] = mn[2] = Float.MAX_VALUE;
        mx[0] = mx[1] = mx[2] = -Float.MAX_VALUE;
        for (Item it : items) {
            for (int k = 0; k < 3; k++) {
                mn[k] = Math.min(mn[k], it.min[k]);
                mx[k] = Math.max(mx[k], it.max[k]);
            }
        }
    }

    /** Oriente (longueur selon Z), centre, pose au sol et met a l'echelle. */
    private void normalize(int userRotation) {
        if (items.isEmpty()) return;
        // retire un eventuel sol plat qui englobe tout le reste
        if (items.size() > 1) {
            float[] mn = new float[3], mx = new float[3];
            bounds(mn, mx);
            float size = Math.max(mx[0] - mn[0], mx[2] - mn[2]);
            for (int i = items.size() - 1; i >= 0; i--) {
                Item it = items.get(i);
                float flat = it.max[1] - it.min[1];
                float span = Math.max(it.max[0] - it.min[0], it.max[2] - it.min[2]);
                if (flat < 0.002f * size && span > 0.98f * size && it.min[1] <= mn[1] + 0.01f * size) {
                    triangles -= it.triangles();
                    items.remove(i);
                }
            }
        }
        float[] mn = new float[3], mx = new float[3];
        bounds(mn, mx);
        float rot = userRotation;
        if (mx[0] - mn[0] > (mx[2] - mn[2]) * 1.1f) rot += 90; // longueur selon X -> Z
        float[] r = M4.rotation(rot, 0, 1, 0);
        applyAll(r);
        bounds(mn, mx);
        float len = mx[2] - mn[2];
        float s = len > 1e-6f ? TARGET_LENGTH / len : 1f;
        float[] t = M4.mul(M4.scale(s), M4.translation(-(mn[0] + mx[0]) / 2, -mn[1], -(mn[2] + mx[2]) / 2));
        applyAll(t);
        bounds(min, max);
    }

    private void applyAll(float[] m) {
        for (Item it : items) {
            float[] tmp = new float[3];
            for (int i = 0; i < it.pos.length; i += 3) {
                M4.transformPoint(m, it.pos, i, tmp, 0);
                it.pos[i] = tmp[0];
                it.pos[i + 1] = tmp[1];
                it.pos[i + 2] = tmp[2];
                if (it.nor != null) {
                    M4.transformDir(m, it.nor, i, tmp, 0);
                    float l = (float) Math.sqrt(tmp[0] * tmp[0] + tmp[1] * tmp[1] + tmp[2] * tmp[2]);
                    if (l < 1e-12f) l = 1;
                    it.nor[i] = tmp[0] / l;
                    it.nor[i + 1] = tmp[1] / l;
                    it.nor[i + 2] = tmp[2] / l;
                }
            }
            it.bounds();
        }
    }

    /** Range les portes par position : gauche = +X, avant = +Z. */
    private void assignDoors() {
        float zmin = Float.MAX_VALUE, zmax = -Float.MAX_VALUE;
        boolean any = false;
        for (Item it : items) {
            if (it.part == 100) {
                any = true;
                zmin = Math.min(zmin, it.cz());
                zmax = Math.max(zmax, it.cz());
            }
        }
        if (!any) return;
        boolean onlyFront = zmax - zmin < 0.35f;
        float zmid = (zmin + zmax) / 2;
        for (Item it : items) {
            if (it.part != 100) continue;
            boolean left = it.cx() > 0;
            boolean front = onlyFront || it.cz() > zmid;
            it.part = front ? (left ? 0 : 1) : (left ? 2 : 3);
        }
    }

    private void computeHinges() {
        for (Item it : items) {
            if (it.part < 0) continue;
            Part p = parts[it.part];
            p.present = true;
            for (int k = 0; k < 3; k++) {
                p.min[k] = Math.min(p.min[k], it.min[k]);
                p.max[k] = Math.max(p.max[k], it.max[k]);
            }
        }
        for (int i = 0; i < PARTS; i++) {
            Part p = parts[i];
            if (!p.present) continue;
            if (i < 4) {
                boolean left = (p.min[0] + p.max[0]) > 0;
                // bord exterieur de la porte (le retroviseur, minoritaire, est ignore)
                float outer = outerX(i, left);
                p.pivot[0] = left ? outer - 0.03f : -outer + 0.03f;
                p.pivot[1] = 0;
                p.pivot[2] = p.max[2] - 0.03f;
                p.axis[1] = 1;
                p.angle = left ? -66f : 66f;
            } else if (i == 4) {
                p.pivot[1] = p.max[1];
                p.pivot[2] = p.min[2] + 0.02f;
                p.axis[0] = 1;
                p.angle = -52f;
            } else {
                p.pivot[1] = p.max[1];
                p.pivot[2] = p.max[2] - 0.02f;
                p.axis[0] = 1;
                p.angle = 58f;
            }
        }
    }

    /**
     * Bord exterieur de la porte cote charniere : |x| maxi des sommets de la
     * moitie basse, pres du bord avant (le retroviseur, en haut, est ignore).
     */
    private float outerX(int part, boolean left) {
        Part p = parts[part];
        float midY = (p.min[1] + p.max[1]) / 2;
        float zLim = p.max[2] - 0.3f * (p.max[2] - p.min[2]);
        float best = -Float.MAX_VALUE;
        for (Item it : items) {
            if (it.part != part) continue;
            for (int i = 0; i < it.pos.length; i += 3) {
                if (it.pos[i + 1] < midY && it.pos[i + 2] > zLim) {
                    float x = left ? it.pos[i] : -it.pos[i];
                    if (x > best) best = x;
                }
            }
        }
        if (best > -Float.MAX_VALUE) return best;
        return percentileX(part, left);
    }

    /** 97e centile de |x| des sommets de la piece (cote exterieur). */
    private float percentileX(int part, boolean left) {
        int n = 0;
        for (Item it : items) {
            if (it.part == part) n += it.pos.length / 3;
        }
        if (n == 0) return 0;
        float[] xs = new float[n];
        int k = 0;
        for (Item it : items) {
            if (it.part != part) continue;
            for (int i = 0; i < it.pos.length; i += 3) {
                xs[k++] = left ? it.pos[i] : -it.pos[i];
            }
        }
        java.util.Arrays.sort(xs);
        return xs[Math.min(n - 1, (int) (n * 0.97f))];
    }

    /** Sans materiau "paint" identifie : la couleur la plus utilisee a l'exterieur. */
    private void guessPaint() {
        GlbModel.Material best = null;
        float bestArea = 0;
        java.util.HashMap<GlbModel.Material, Float> area = new java.util.HashMap<GlbModel.Material, Float>();
        for (Item it : items) {
            GlbModel.Material m = it.mat;
            if (m.blend || m.image >= 0) continue;
            float lum = 0.3f * m.color[0] + 0.59f * m.color[1] + 0.11f * m.color[2];
            float sat = Math.max(m.color[0], Math.max(m.color[1], m.color[2]))
                    - Math.min(m.color[0], Math.min(m.color[1], m.color[2]));
            if (lum < 0.08f && sat < 0.05f) continue; // pneus / plastiques noirs
            float a = (it.max[0] - it.min[0]) * (it.max[2] - it.min[2]) + (it.max[1] - it.min[1]) * (it.max[2] - it.min[2]);
            Float prev = area.get(m);
            float tot = (prev == null ? 0 : prev) + a;
            area.put(m, tot);
            if (tot > bestArea) {
                bestArea = tot;
                best = m;
            }
        }
        if (best == null) return;
        for (Item it : items) {
            if (it.mat == best) it.paint = true;
        }
    }

    // ------------------------------------------------------------ animation
    /** Matrice de la piece i ouverte a la fraction t (0 = fermee, 1 = ouverte). */
    public float[] partMatrix(int i, float t) {
        Part p = parts[i];
        if (!p.present || t <= 0f) return M4.identity();
        float e = t * t * (3 - 2 * t);
        float[] m = M4.translation(p.pivot[0], p.pivot[1], p.pivot[2]);
        m = M4.mul(m, M4.rotation(p.angle * e, p.axis[0], p.axis[1], p.axis[2]));
        return M4.mul(m, M4.translation(-p.pivot[0], -p.pivot[1], -p.pivot[2]));
    }

    // ------------------------------------------------------------- selection
    /**
     * Rayon (origine o, direction d) contre la voiture.
     *
     * @return index d'ouvrant touche, -1 si autre partie, -2 si rien
     */
    public int pick(float[] o, float[] d, float[] openT) {
        float best = Float.MAX_VALUE;
        int bestPart = -2;
        float[] lo = new float[3], ld = new float[3];
        for (Item it : items) {
            float[] ro = o, rd = d;
            if (it.part >= 0 && openT != null && openT[it.part] > 0.001f) {
                float[] inv = M4.invert(partMatrix(it.part, openT[it.part]));
                if (inv == null) continue;
                M4.transformPoint(inv, o, 0, lo, 0);
                M4.transformDir(inv, d, 0, ld, 0);
                ro = lo;
                rd = ld;
            }
            if (!rayBox(ro, rd, it.min, it.max, best)) continue;
            float t = rayMesh(ro, rd, it, best);
            if (t < best) {
                best = t;
                bestPart = it.part >= 0 ? it.part : -1;
            }
        }
        return bestPart;
    }

    static boolean rayBox(float[] o, float[] d, float[] mn, float[] mx, float maxT) {
        float t0 = 0, t1 = maxT;
        for (int k = 0; k < 3; k++) {
            float inv = 1f / (Math.abs(d[k]) < 1e-12f ? 1e-12f : d[k]);
            float a = (mn[k] - 0.01f - o[k]) * inv;
            float b = (mx[k] + 0.01f - o[k]) * inv;
            if (a > b) {
                float tmp = a;
                a = b;
                b = tmp;
            }
            t0 = Math.max(t0, a);
            t1 = Math.min(t1, b);
            if (t0 > t1) return false;
        }
        return true;
    }

    static float rayMesh(float[] o, float[] d, Item it, float maxT) {
        float best = maxT;
        float[] p = it.pos;
        int[] idx = it.idx;
        for (int t = 0; t + 2 < idx.length; t += 3) {
            int a = idx[t] * 3, b = idx[t + 1] * 3, c = idx[t + 2] * 3;
            float e1x = p[b] - p[a], e1y = p[b + 1] - p[a + 1], e1z = p[b + 2] - p[a + 2];
            float e2x = p[c] - p[a], e2y = p[c + 1] - p[a + 1], e2z = p[c + 2] - p[a + 2];
            float px = d[1] * e2z - d[2] * e2y, py = d[2] * e2x - d[0] * e2z, pz = d[0] * e2y - d[1] * e2x;
            float det = e1x * px + e1y * py + e1z * pz;
            if (Math.abs(det) < 1e-10f) continue;
            float inv = 1f / det;
            float tx = o[0] - p[a], ty = o[1] - p[a + 1], tz = o[2] - p[a + 2];
            float u = (tx * px + ty * py + tz * pz) * inv;
            if (u < 0 || u > 1) continue;
            float qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
            float v = (d[0] * qx + d[1] * qy + d[2] * qz) * inv;
            if (v < 0 || u + v > 1) continue;
            float dist = (e2x * qx + e2y * qy + e2z * qz) * inv;
            if (dist > 1e-4f && dist < best) best = dist;
        }
        return best;
    }
}
