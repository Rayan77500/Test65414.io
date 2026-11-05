import com.rayan.w204dash.gl.CarScene;
import com.rayan.w204dash.gl.GlbModel;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Exporte la scene telle que l'appli la prepare (CarScene) pour l'apercu
 * WebGL de tools/preview (memes shaders que l'appli).
 * Format : JSON d'en-tete + blocs float32/int32 little-endian.
 */
public class DumpScene {
    public static void main(String[] a) throws Exception {
        CarScene sc = CarScene.build(GlbModel.parse(Files.readAllBytes(Paths.get(a[0]))), 0);
        StringBuilder js = new StringBuilder("{\"items\":[");
        java.io.ByteArrayOutputStream bin = new java.io.ByteArrayOutputStream();
        int off = 0;
        for (int i = 0; i < sc.items.size(); i++) {
            CarScene.Item it = sc.items.get(i);
            GlbModel.Material m = it.mat;
            ByteBuffer b = ByteBuffer.allocate((it.pos.length * 2 + it.idx.length) * 4).order(ByteOrder.LITTLE_ENDIAN);
            for (float f : it.pos) b.putFloat(f);
            for (float f : it.nor) b.putFloat(f);
            for (int v : it.idx) b.putInt(v);
            bin.write(b.array());
            if (i > 0) js.append(',');
            js.append(String.format(java.util.Locale.US,
                "{\"off\":%d,\"nv\":%d,\"ni\":%d,\"part\":%d,\"paint\":%b,\"blend\":%b,\"color\":[%f,%f,%f,%f],\"metal\":%f,\"rough\":%f,\"em\":[%f,%f,%f],\"c\":[%f,%f,%f]}",
                off, it.pos.length / 3, it.idx.length, it.part, it.paint, m.blend, m.color[0], m.color[1], m.color[2], m.color[3],
                m.metallic, m.roughness, m.emissive[0], m.emissive[1], m.emissive[2],
                (it.min[0] + it.max[0]) / 2, (it.min[1] + it.max[1]) / 2, (it.min[2] + it.max[2]) / 2));
            off += b.capacity();
        }
        js.append("],\"min\":[").append(sc.min[0]).append(',').append(sc.min[1]).append(',').append(sc.min[2]).append("],\"max\":[")
          .append(sc.max[0]).append(',').append(sc.max[1]).append(',').append(sc.max[2]).append("],\"open\":[");
        for (int p = 0; p < CarScene.PARTS; p++) {
            float[] m = sc.partMatrix(p, 1f);
            if (p > 0) js.append(',');
            js.append('[');
            for (int k = 0; k < 16; k++) js.append(k > 0 ? "," : "").append(m[k]);
            js.append(']');
        }
        js.append("],\"generated\":").append(sc.generated).append('}');
        Files.write(Paths.get(a[1] + ".json"), js.toString().getBytes("UTF-8"));
        Files.write(Paths.get(a[1] + ".bin"), bin.toByteArray());
        System.out.println("export ok : " + sc.items.size() + " elements");
    }
}
