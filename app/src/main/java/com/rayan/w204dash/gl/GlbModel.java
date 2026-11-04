package com.rayan.w204dash.gl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

/**
 * Lecteur minimal de fichiers glTF 2.0 binaires (.glb) : noeuds, maillages,
 * materiaux PBR (couleur, metal, rugosite, emissif, transparence) et
 * textures de couleur integrees. Sans dependance Android (teste sur PC).
 */
public final class GlbModel {

    public static final class Material {
        public String name = "";
        public float[] color = {1, 1, 1, 1};
        public float metallic = 1f;
        public float roughness = 1f;
        public float[] emissive = {0, 0, 0};
        public boolean blend;
        public float alphaCutoff = -1f;
        public int image = -1;
    }

    public static final class Primitive {
        public float[] pos;
        public float[] nor;
        public float[] uv;
        public int[] idx;
        public int material = -1;
    }

    public static final class Mesh {
        public String name = "";
        public final List<Primitive> primitives = new ArrayList<Primitive>();
    }

    public static final class Node {
        public String name = "";
        public int mesh = -1;
        public int[] children = new int[0];
        public float[] matrix = M4.identity();
        public int parent = -1;
    }

    public static final class Image {
        public byte[] data;
        public String mime;
    }

    public final List<Node> nodes = new ArrayList<Node>();
    public final List<Mesh> meshes = new ArrayList<Mesh>();
    public final List<Material> materials = new ArrayList<Material>();
    public final List<Image> images = new ArrayList<Image>();
    public int[] roots = new int[0];
    public String generator = "";

    private JSONObject json;
    private final List<ByteBuffer> buffers = new ArrayList<ByteBuffer>();

    public static GlbModel parse(byte[] glb) throws IOException {
        GlbModel m = new GlbModel();
        try {
            m.read(glb);
        } catch (JSONException e) {
            throw new IOException("JSON glTF invalide : " + e.getMessage());
        } catch (RuntimeException e) {
            throw new IOException("Fichier glTF illisible : " + e);
        }
        m.json = null;
        m.buffers.clear();
        return m;
    }

    private void read(byte[] glb) throws IOException, JSONException {
        if (glb == null || glb.length < 20) throw new IOException("Fichier vide");
        ByteBuffer bb = ByteBuffer.wrap(glb).order(ByteOrder.LITTLE_ENDIAN);
        int magic = bb.getInt(0);
        if (magic != 0x46546C67) {
            throw new IOException("Ce n'est pas un fichier .glb (glTF binaire)");
        }
        int version = bb.getInt(4);
        if (version != 2) throw new IOException("Version glTF " + version + " non supportée (2 attendue)");
        int off = 12;
        byte[] bin = null;
        String js = null;
        while (off + 8 <= glb.length) {
            int len = bb.getInt(off);
            int type = bb.getInt(off + 4);
            off += 8;
            if (len < 0 || off + len > glb.length) throw new IOException("Fichier .glb tronqué");
            if (type == 0x4E4F534A) {
                js = new String(glb, off, len, "UTF-8");
            } else if (type == 0x004E4942 && bin == null) {
                bin = new byte[len];
                System.arraycopy(glb, off, bin, 0, len);
            }
            off += (len + 3) & ~3;
        }
        if (js == null) throw new IOException("Bloc JSON absent");
        json = new JSONObject(js);
        JSONObject asset = json.optJSONObject("asset");
        if (asset != null) generator = asset.optString("generator", "");
        JSONArray req = json.optJSONArray("extensionsRequired");
        if (req != null) {
            for (int i = 0; i < req.length(); i++) {
                String e = req.getString(i);
                if (e.contains("draco") || e.contains("meshopt")) {
                    throw new IOException("Modèle compressé (" + e + ") non supporté : "
                            + "ré-exportez le .glb sans compression Draco/Meshopt");
                }
            }
        }
        JSONArray bufs = json.optJSONArray("buffers");
        if (bufs != null) {
            for (int i = 0; i < bufs.length(); i++) {
                JSONObject b = bufs.getJSONObject(i);
                String uri = b.optString("uri", null);
                byte[] data;
                if (uri == null) {
                    data = bin;
                } else if (uri.startsWith("data:")) {
                    data = Base64Lite.decode(uri.substring(uri.indexOf(',') + 1));
                } else {
                    throw new IOException("Fichier externe « " + uri + " » non supporté : utilisez un .glb autonome");
                }
                buffers.add(data == null ? null : ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN));
            }
        }
        readImages();
        readMaterials();
        readMeshes();
        readNodes();
    }

    private void readImages() throws JSONException {
        JSONArray imgs = json.optJSONArray("images");
        if (imgs == null) return;
        for (int i = 0; i < imgs.length(); i++) {
            JSONObject o = imgs.getJSONObject(i);
            Image im = new Image();
            im.mime = o.optString("mimeType", "");
            try {
                if (o.has("bufferView")) {
                    im.data = viewBytes(o.getInt("bufferView"));
                } else {
                    String uri = o.optString("uri", "");
                    if (uri.startsWith("data:")) im.data = Base64Lite.decode(uri.substring(uri.indexOf(',') + 1));
                }
            } catch (Exception e) {
                im.data = null;
            }
            images.add(im);
        }
    }

    private void readMaterials() throws JSONException {
        JSONArray mats = json.optJSONArray("materials");
        JSONArray texs = json.optJSONArray("textures");
        if (mats == null) return;
        for (int i = 0; i < mats.length(); i++) {
            JSONObject o = mats.getJSONObject(i);
            Material m = new Material();
            m.name = o.optString("name", "materiau" + i);
            JSONObject pbr = o.optJSONObject("pbrMetallicRoughness");
            if (pbr != null) {
                JSONArray c = pbr.optJSONArray("baseColorFactor");
                if (c != null && c.length() >= 4) {
                    for (int k = 0; k < 4; k++) m.color[k] = (float) c.getDouble(k);
                }
                m.metallic = (float) pbr.optDouble("metallicFactor", 1.0);
                m.roughness = (float) pbr.optDouble("roughnessFactor", 1.0);
                JSONObject bct = pbr.optJSONObject("baseColorTexture");
                if (bct != null && texs != null) {
                    int ti = bct.optInt("index", -1);
                    if (ti >= 0 && ti < texs.length()) {
                        m.image = texs.getJSONObject(ti).optInt("source", -1);
                    }
                }
            }
            JSONArray e = o.optJSONArray("emissiveFactor");
            if (e != null && e.length() >= 3) {
                for (int k = 0; k < 3; k++) m.emissive[k] = (float) e.getDouble(k);
            }
            String am = o.optString("alphaMode", "OPAQUE");
            m.blend = "BLEND".equals(am);
            if ("MASK".equals(am)) m.alphaCutoff = (float) o.optDouble("alphaCutoff", 0.5);
            materials.add(m);
        }
    }

    private void readMeshes() throws JSONException, IOException {
        JSONArray ms = json.optJSONArray("meshes");
        if (ms == null) return;
        for (int i = 0; i < ms.length(); i++) {
            JSONObject o = ms.getJSONObject(i);
            Mesh mesh = new Mesh();
            mesh.name = o.optString("name", "");
            JSONArray prims = o.optJSONArray("primitives");
            if (prims != null) {
                for (int p = 0; p < prims.length(); p++) {
                    Primitive pr = readPrimitive(prims.getJSONObject(p));
                    if (pr != null) mesh.primitives.add(pr);
                }
            }
            meshes.add(mesh);
        }
    }

    private Primitive readPrimitive(JSONObject o) throws JSONException, IOException {
        int mode = o.optInt("mode", 4);
        if (mode < 4) return null; // points / lignes ignores
        JSONObject at = o.getJSONObject("attributes");
        if (!at.has("POSITION")) return null;
        Primitive p = new Primitive();
        p.pos = readFloats(at.getInt("POSITION"), 3);
        if (at.has("NORMAL")) p.nor = readFloats(at.getInt("NORMAL"), 3);
        if (at.has("TEXCOORD_0")) p.uv = readFloats(at.getInt("TEXCOORD_0"), 2);
        p.material = o.optInt("material", -1);
        int n = p.pos.length / 3;
        int[] idx;
        if (o.has("indices")) {
            idx = readInts(o.getInt("indices"));
        } else {
            idx = new int[n];
            for (int i = 0; i < n; i++) idx[i] = i;
        }
        if (mode == 5) idx = stripToTriangles(idx);
        else if (mode == 6) idx = fanToTriangles(idx);
        for (int v : idx) {
            if (v < 0 || v >= n) throw new IOException("Indice de sommet hors limites");
        }
        p.idx = idx;
        if (p.nor != null && p.nor.length != p.pos.length) p.nor = null;
        if (p.uv != null && p.uv.length / 2 != n) p.uv = null;
        return p;
    }

    static int[] stripToTriangles(int[] s) {
        if (s.length < 3) return new int[0];
        int[] t = new int[(s.length - 2) * 3];
        for (int i = 0; i < s.length - 2; i++) {
            boolean even = (i & 1) == 0;
            t[i * 3] = s[i];
            t[i * 3 + 1] = even ? s[i + 1] : s[i + 2];
            t[i * 3 + 2] = even ? s[i + 2] : s[i + 1];
        }
        return t;
    }

    static int[] fanToTriangles(int[] s) {
        if (s.length < 3) return new int[0];
        int[] t = new int[(s.length - 2) * 3];
        for (int i = 0; i < s.length - 2; i++) {
            t[i * 3] = s[0];
            t[i * 3 + 1] = s[i + 1];
            t[i * 3 + 2] = s[i + 2];
        }
        return t;
    }

    private void readNodes() throws JSONException {
        JSONArray ns = json.optJSONArray("nodes");
        if (ns == null) return;
        for (int i = 0; i < ns.length(); i++) {
            JSONObject o = ns.getJSONObject(i);
            Node n = new Node();
            n.name = o.optString("name", "");
            n.mesh = o.optInt("mesh", -1);
            JSONArray ch = o.optJSONArray("children");
            if (ch != null) {
                n.children = new int[ch.length()];
                for (int k = 0; k < ch.length(); k++) n.children[k] = ch.getInt(k);
            }
            JSONArray mat = o.optJSONArray("matrix");
            if (mat != null && mat.length() == 16) {
                for (int k = 0; k < 16; k++) n.matrix[k] = (float) mat.getDouble(k);
            } else {
                float[] t = {0, 0, 0};
                float[] r = {0, 0, 0, 1};
                float[] s = {1, 1, 1};
                JSONArray a = o.optJSONArray("translation");
                if (a != null) for (int k = 0; k < 3; k++) t[k] = (float) a.getDouble(k);
                a = o.optJSONArray("rotation");
                if (a != null) for (int k = 0; k < 4; k++) r[k] = (float) a.getDouble(k);
                a = o.optJSONArray("scale");
                if (a != null) for (int k = 0; k < 3; k++) s[k] = (float) a.getDouble(k);
                n.matrix = M4.trs(t, r, s);
            }
            nodes.add(n);
        }
        for (int i = 0; i < nodes.size(); i++) {
            for (int c : nodes.get(i).children) {
                if (c >= 0 && c < nodes.size()) nodes.get(c).parent = i;
            }
        }
        JSONArray scenes = json.optJSONArray("scenes");
        int si = json.optInt("scene", 0);
        if (scenes != null && si < scenes.length()) {
            JSONArray rn = scenes.getJSONObject(si).optJSONArray("nodes");
            if (rn != null) {
                roots = new int[rn.length()];
                for (int k = 0; k < rn.length(); k++) roots[k] = rn.getInt(k);
                return;
            }
        }
        // pas de scene : toutes les racines
        List<Integer> r = new ArrayList<Integer>();
        for (int i = 0; i < nodes.size(); i++) {
            if (nodes.get(i).parent < 0) r.add(i);
        }
        roots = new int[r.size()];
        for (int i = 0; i < roots.length; i++) roots[i] = r.get(i);
    }

    // ------------------------------------------------------------ accessors
    private byte[] viewBytes(int vi) throws JSONException, IOException {
        JSONObject v = json.getJSONArray("bufferViews").getJSONObject(vi);
        ByteBuffer b = buffers.get(v.optInt("buffer", 0));
        if (b == null) throw new IOException("Tampon binaire manquant");
        int off = v.optInt("byteOffset", 0);
        int len = v.getInt("byteLength");
        byte[] out = new byte[len];
        for (int i = 0; i < len; i++) out[i] = b.get(off + i);
        return out;
    }

    private static int comps(String type) {
        if ("SCALAR".equals(type)) return 1;
        if ("VEC2".equals(type)) return 2;
        if ("VEC3".equals(type)) return 3;
        if ("VEC4".equals(type)) return 4;
        if ("MAT4".equals(type)) return 16;
        return 1;
    }

    private static int compSize(int ct) {
        switch (ct) {
            case 5120:
            case 5121:
                return 1;
            case 5122:
            case 5123:
                return 2;
            default:
                return 4;
        }
    }

    private float[] readFloats(int ai, int want) throws JSONException, IOException {
        JSONObject a = json.getJSONArray("accessors").getJSONObject(ai);
        int count = a.getInt("count");
        int nc = comps(a.getString("type"));
        int ct = a.getInt("componentType");
        boolean norm = a.optBoolean("normalized", false);
        float[] out = new float[count * want];
        if (!a.has("bufferView")) return out;
        JSONObject v = json.getJSONArray("bufferViews").getJSONObject(a.getInt("bufferView"));
        ByteBuffer b = buffers.get(v.optInt("buffer", 0));
        if (b == null) throw new IOException("Tampon binaire manquant");
        int base = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0);
        int cs = compSize(ct);
        int stride = v.optInt("byteStride", 0);
        if (stride == 0) stride = cs * nc;
        int take = Math.min(nc, want);
        for (int i = 0; i < count; i++) {
            int p = base + i * stride;
            for (int c = 0; c < take; c++) {
                int q = p + c * cs;
                float f;
                switch (ct) {
                    case 5126: f = b.getFloat(q); break;
                    case 5120: f = norm ? Math.max(b.get(q) / 127f, -1f) : b.get(q); break;
                    case 5121: f = norm ? (b.get(q) & 0xFF) / 255f : (b.get(q) & 0xFF); break;
                    case 5122: f = norm ? Math.max(b.getShort(q) / 32767f, -1f) : b.getShort(q); break;
                    case 5123: f = norm ? (b.getShort(q) & 0xFFFF) / 65535f : (b.getShort(q) & 0xFFFF); break;
                    default: f = b.getInt(q); break;
                }
                out[i * want + c] = f;
            }
        }
        return out;
    }

    private int[] readInts(int ai) throws JSONException, IOException {
        JSONObject a = json.getJSONArray("accessors").getJSONObject(ai);
        int count = a.getInt("count");
        int ct = a.getInt("componentType");
        int[] out = new int[count];
        if (!a.has("bufferView")) return out;
        JSONObject v = json.getJSONArray("bufferViews").getJSONObject(a.getInt("bufferView"));
        ByteBuffer b = buffers.get(v.optInt("buffer", 0));
        if (b == null) throw new IOException("Tampon binaire manquant");
        int base = v.optInt("byteOffset", 0) + a.optInt("byteOffset", 0);
        int cs = compSize(ct);
        int stride = v.optInt("byteStride", 0);
        if (stride == 0) stride = cs;
        for (int i = 0; i < count; i++) {
            int q = base + i * stride;
            switch (ct) {
                case 5121: out[i] = b.get(q) & 0xFF; break;
                case 5123: out[i] = b.getShort(q) & 0xFFFF; break;
                default: out[i] = b.getInt(q); break;
            }
        }
        return out;
    }

    /** Decodeur base64 minimal (images / tampons integres en "data:"). */
    static final class Base64Lite {
        static byte[] decode(String s) {
            int[] map = new int[128];
            java.util.Arrays.fill(map, -1);
            String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
            for (int i = 0; i < 64; i++) map[abc.charAt(i)] = i;
            map['-'] = 62;
            map['_'] = 63;
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream(s.length() * 3 / 4);
            int buf = 0, bits = 0;
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= 128 || map[c] < 0) continue;
                buf = (buf << 6) | map[c];
                bits += 6;
                if (bits >= 8) {
                    bits -= 8;
                    out.write((buf >> bits) & 0xFF);
                }
            }
            return out.toByteArray();
        }
    }
}
