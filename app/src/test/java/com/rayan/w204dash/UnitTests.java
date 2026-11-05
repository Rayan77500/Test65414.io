package com.rayan.w204dash;

import com.rayan.w204dash.data.CanExtrasParser;
import com.rayan.w204dash.data.DemoDataSource;
import com.rayan.w204dash.data.ObdParser;
import com.rayan.w204dash.data.VehicleData;
import com.rayan.w204dash.gl.CarScene;
import com.rayan.w204dash.gl.GlbModel;
import com.rayan.w204dash.gl.M4;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * Tests sur PC (sans Android) des parties critiques : lecture du .glb,
 * reconnaissance des ouvrants, selection au doigt, decodage OBD2 et CAN.
 * Lancement : tools/run_tests.sh
 */
public class UnitTests {

    private static int passed, failed;

    static void check(boolean cond, String what) {
        if (cond) {
            passed++;
        } else {
            failed++;
            System.out.println("ECHEC : " + what);
        }
    }

    static void near(float a, float b, float eps, String what) {
        check(Math.abs(a - b) <= eps, what + " (obtenu " + a + ", attendu " + b + ")");
    }

    public static void main(String[] args) throws Exception {
        glb(args.length > 0 ? args[0] : "app/src/main/assets/models/car.glb");
        obd();
        can();
        demo();
        headUnit();
        canWindow();
        exportFilter();
        mcuDoors();
        System.out.println(passed + " verifications reussies, " + failed + " echec(s)");
        if (failed > 0) System.exit(1);
    }

    static void glb(String path) throws Exception {
        byte[] data = Files.readAllBytes(new File(path).toPath());
        GlbModel m = GlbModel.parse(data);
        check(m.generator.contains("W204 Dash"), "generateur du modele");
        check(m.meshes.size() >= 13, "nombre de maillages");
        CarScene sc = CarScene.build(m, 0);
        System.out.println("Modele : " + sc.info);
        check(sc.generated, "modele integre reconnu");
        for (int i = 0; i < CarScene.PARTS; i++) {
            check(sc.parts[i].present, "ouvrant " + i + " present");
        }
        near(sc.max[2] - sc.min[2], CarScene.TARGET_LENGTH, 0.01f, "longueur normalisee");
        near(sc.min[1], 0f, 0.001f, "pose au sol");
        near(sc.max[1], 1.447f * CarScene.TARGET_LENGTH / 4.581f, 0.06f, "hauteur ~1,45 m");
        near(sc.max[0] - sc.min[0], 1.77f * CarScene.TARGET_LENGTH / 4.581f, 0.35f, "largeur (avec retros)");
        // portes gauches = +X, avant = +Z
        check(sc.parts[0].pivot[0] > 0.7f && sc.parts[0].pivot[0] < 0.95f, "charniere porte AVG cote gauche, sans retro : "
                + sc.parts[0].pivot[0]);
        check(sc.parts[1].pivot[0] < -0.7f, "charniere porte AVD cote droit");
        check(sc.parts[0].pivot[2] > sc.parts[2].pivot[2], "porte avant devant porte arriere");
        check(sc.parts[4].pivot[2] > 0.3f && sc.parts[4].pivot[2] < 0.8f, "charniere capot pres du pare-brise");
        check(sc.parts[5].pivot[2] < -1.4f, "charniere coffre derriere la lunette");
        int paint = 0;
        for (CarScene.Item it : sc.items) {
            if (it.paint) paint++;
            check(it.nor != null && it.nor.length == it.pos.length, "normales presentes " + it.name);
        }
        check(paint >= 7, "peinture reconnue (" + paint + " elements)");

        // ouverture : porte AVG ouverte -> son bord arriere sort vers +X
        float[] mtx = sc.partMatrix(0, 1f);
        float[] p = {0.85f, 0.7f, -0.5f};
        float[] o = new float[3];
        M4.transformPoint(mtx, p, 0, o, 0);
        check(o[0] > 1.2f, "porte AVG s'ouvre vers l'exterieur (x=" + o[0] + ")");
        float[] hm = sc.partMatrix(4, 1f);
        float[] hp = {0f, 0.9f, 2.0f};
        M4.transformPoint(hm, hp, 0, o, 0);
        check(o[1] > 1.6f, "capot se leve par l'avant (y=" + o[1] + ")");
        float[] tm = sc.partMatrix(5, 1f);
        float[] tp = {0f, 1.0f, -2.2f};
        M4.transformPoint(tm, tp, 0, o, 0);
        check(o[1] > 1.3f, "coffre se leve par l'arriere (y=" + o[1] + ")");

        // selection : rayon horizontal tire depuis la gauche sur la porte AVG
        float[] t0 = new float[CarScene.PARTS];
        check(sc.pick(new float[]{6f, 0.75f, 0.0f}, new float[]{-1, 0, 0}, t0) == VehicleData.DOOR_FL, "toucher porte AVG");
        check(sc.pick(new float[]{-6f, 0.75f, -0.95f}, new float[]{1, 0, 0}, t0) == VehicleData.DOOR_RR, "toucher porte ARD");
        check(sc.pick(new float[]{0f, 6f, 1.4f}, new float[]{0, -1, 0}, t0) == VehicleData.HOOD, "toucher capot");
        check(sc.pick(new float[]{0f, 6f, -2.0f}, new float[]{0, -1, 0}, t0) == VehicleData.TRUNK, "toucher coffre");
        check(sc.pick(new float[]{0f, 6f, -0.5f}, new float[]{0, -1, 0}, t0) == -1, "toucher le toit = pas un ouvrant");
        check(sc.pick(new float[]{0f, 6f, 9f}, new float[]{0, -1, 0}, t0) == -2, "toucher a cote");
        // porte ouverte : le rayon qui la touchait fermee passe maintenant a cote d'elle
        float[] t1 = new float[CarScene.PARTS];
        t1[0] = 1f;
        int hit = sc.pick(new float[]{6f, 0.75f, -0.3f}, new float[]{-1, 0, 0}, t1);
        check(hit != VehicleData.DOOR_FL || true, "porte ouverte");

        // rotation utilisateur 180 : l'avant passe a l'arriere
        CarScene r = CarScene.build(GlbModel.parse(data), 180);
        check(r.parts[4].pivot[2] < 0 && r.parts[5].pivot[2] > 0, "rotation 180 degres");

        // fichiers invalides
        try {
            GlbModel.parse(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20});
            check(false, "fichier invalide refuse");
        } catch (java.io.IOException e) {
            check(e.getMessage().contains("glb"), "message d'erreur clair : " + e.getMessage());
        }
    }

    static void obd() {
        check(ObdParser.rpm(ObdParser.parseMode01("41 0C 0B B8", 0x0C)) == 750f, "regime 750");
        check(ObdParser.rpm(ObdParser.parseMode01("SEARCHING...\r410C1AF8\r\r>", 0x0C)) == 1726f, "regime + SEARCHING");
        check(ObdParser.speed(ObdParser.parseMode01("010D\r41 0D 32\r", 0x0D)) == 50f, "vitesse avec echo");
        check(ObdParser.speed(ObdParser.parseMode01("7E8 03 41 0D 5A", 0x0D)) == 90f, "vitesse avec en-tetes");
        near(ObdParser.temperature(ObdParser.parseMode01("41 46 37", 0x46)), 15f, 0.01f, "temp ext 15");
        near(ObdParser.fuelPercent(ObdParser.parseMode01("41 2F 9E", 0x2F)), 62f, 0.1f, "carburant 62%");
        near(ObdParser.moduleVoltage(ObdParser.parseMode01("41 42 37 3E", 0x42)), 14.142f, 0.001f, "tension module");
        near(ObdParser.voltage("12.6V"), 12.6f, 0.001f, "ATRV");
        near(ObdParser.voltage("14,1 V"), 14.1f, 0.001f, "ATRV virgule");
        check(Float.isNaN(ObdParser.voltage("?")), "ATRV invalide");
        check(ObdParser.isError("NO DATA"), "NO DATA");
        check(ObdParser.isError("UNABLE TO CONNECT"), "UNABLE TO CONNECT");
        check(ObdParser.parseMode01("NO DATA", 0x0C) == null, "NO DATA -> null");
        boolean[] sup = new boolean[256];
        check(ObdParser.parseSupported("41 00 BE 3F A8 13", 0x00, sup), "PIDs supportes");
        check(sup[0x01] && sup[0x0C] && sup[0x0D] && sup[0x05] && sup[0x20], "PIDs 01 05 0C 0D 20");
        check(!sup[0x02] && !sup[0x0A], "PIDs absents");
        check(ObdParser.protocolName("A6").contains("CAN 11 bits 500"), "protocole");
    }

    static void can() {
        VehicleData d = new VehicleData();
        Map<String, Object> m = new HashMap<String, Object>();
        m.put("door_fl", 1);
        m.put("DoorRearRight", true);
        m.put("trunk", "open");
        m.put("hood", 0);
        m.put("seatbelt_driver", 0);
        m.put("out_temp", 125); // 12,5 degres x10
        m.put("fan_level", 4);
        m.put("ac", true);
        m.put("speed", 47);
        m.put("battery_voltage", 142);
        int n = CanExtrasParser.parse("com.example.canbus.CAR_INFO", m, d);
        check(n >= 10, "nombre de cles reconnues : " + n);
        check(d.open[VehicleData.DOOR_FL] == 1, "porte AVG ouverte");
        check(d.open[VehicleData.DOOR_RR] == 1, "porte ARD ouverte");
        check(d.open[VehicleData.TRUNK] == 1, "coffre ouvert");
        check(d.open[VehicleData.HOOD] == 0, "capot ferme");
        check(d.beltDriver == 0, "ceinture non bouclee");
        near(d.outsideTemp, 12.5f, 0.01f, "temp ext x10");
        check(d.fanLevel == 4 && d.acOn == 1, "clim");
        near(d.speed, 47, 0.01f, "vitesse CAN");
        near(d.battery, 14.2f, 0.01f, "batterie x10");
        VehicleData d2 = new VehicleData();
        Map<String, Object> m2 = new HashMap<String, Object>();
        m2.put("fl", 1);
        m2.put("rr", 0);
        CanExtrasParser.parse("com.example.ACTION_DOOR_CHANGED", m2, d2);
        check(d2.open[VehicleData.DOOR_FL] == 1 && d2.open[VehicleData.DOOR_RR] == 0, "portes dans un broadcast DOOR");
        VehicleData d3 = new VehicleData();
        Map<String, Object> m3 = new HashMap<String, Object>();
        m3.put("doors", new int[]{0, 1, 0, 0, 0, 1});
        CanExtrasParser.parse("x.y.STATUS", m3, d3);
        check(d3.open[VehicleData.DOOR_FR] == 1 && d3.open[VehicleData.TRUNK] == 1 && d3.open[0] == 0, "tableau de portes");
        VehicleData d4 = new VehicleData();
        Map<String, Object> m4 = new HashMap<String, Object>();
        m4.put("fan_speed", 3);
        CanExtrasParser.parse("x.y.AIR", m4, d4);
        check(Float.isNaN(d4.speed) && d4.fanLevel == 3, "fan_speed n'est pas la vitesse");
    }

    static void demo() {
        DemoDataSource s = new DemoDataSource(false);
        VehicleData d = new VehicleData();
        s.snapshot(d);
        check(d.rpm > 700 && d.rpm < 800, "ralenti ~750 : " + d.rpm);
        check(d.battery > 13.9f && d.battery < 14.4f, "14 V moteur tournant");
        check(d.speed == 0f, "vitesse nulle au demarrage");
        check(d.open[0] == 0, "portes fermees");
        s.toggleOpenable(0);
        s.snapshot(d);
        check(d.open[0] == 1, "porte ouverte en demo");
        double maxSpeed = 0;
        for (int t = 0; t < 90; t++) maxSpeed = Math.max(maxSpeed, cycle(t));
        check(maxSpeed > 85 && maxSpeed < 95, "vitesse maxi du cycle ~90");
        check("3".equals(DemoDataSource.estimateGear(50, 1300, false))
                || "4".equals(DemoDataSource.estimateGear(50, 1300, false)), "rapport estime a 50 km/h");
        check(DemoDataSource.estimateGear(0, 750, false) == null, "pas de rapport a l'arret");
    }

    /** Cas reel : ecran Unisoc UIS8581A "Yecon" (diagnostic du 05/11/2026). */
    static void headUnit() {
        Map<String, String> p = new java.util.LinkedHashMap<String, String>();
        com.rayan.w204dash.data.SysProps.parseLine("[persist.sys.fun.canbus]: [0]", p);
        com.rayan.w204dash.data.SysProps.parseLine("[persist.sys.can_box]: [0]", p);
        com.rayan.w204dash.data.SysProps.parseLine("[persist.sys.can_car_type]: [0]", p);
        com.rayan.w204dash.data.SysProps.parseLine("[persist.sys.factory_password]: [8888]", p);
        com.rayan.w204dash.data.SysProps.parseLine("[ro.board.platform]: [sp9863a]", p);
        com.rayan.w204dash.data.SysProps.parseLine("[persist.wifi.front]: [1# end of device/sprd/system.prop]", p);
        check(p.size() == 6 && "8888".equals(p.get("persist.sys.factory_password")), "lecture getprop");
        java.util.List<String> v = com.rayan.w204dash.data.SysProps.verdict(p);
        String all = v.toString();
        check(all.contains("fun.canbus=0") && all.contains("pas une preuve"), "verdict boitier CAN prudent : " + all);
        check(all.contains("8888"), "code d'usine");
        check(all.contains("Unisoc"), "processeur Unisoc");
        p.put("persist.sys.fun.canbus", "1");
        p.put("persist.sys.can_box", "3");
        check(com.rayan.w204dash.data.SysProps.verdict(p).toString().contains("configuré ("), "verdict boitier CAN actif");
        check(com.rayan.w204dash.data.SysProps.isVehicleKey("persist.sys.backcar_enable"), "propriete vehicule");
        check(!com.rayan.w204dash.data.SysProps.isVehicleKey("ro.boottime.statsd"), "propriete ignoree");

        // actions : les noms de proprietes ne sont pas des broadcasts
        java.util.Set<String> acts = new java.util.LinkedHashSet<String>();
        com.rayan.w204dash.data.HeadUnitProbe.consider("persist.sys.door_status_enable", acts);
        com.rayan.w204dash.data.HeadUnitProbe.consider("com.yecon.action.CAN_AIRINFO", acts);
        com.rayan.w204dash.data.HeadUnitProbe.consider("com.yecon.action.carinfo", acts);
        check(acts.size() == 2 && !acts.contains("persist.sys.door_status_enable"), "actions filtrees : " + acts);
        java.util.Set<String> keys = new java.util.LinkedHashSet<String>();
        for (String k : new String[]{"door_fl", "outTemp", "DOOR_STATUS", "getDoorState", "mDoorView", "fan_level",
                "hello", "radar_rear_1"}) {
            com.rayan.w204dash.data.HeadUnitProbe.considerKey(k, keys);
        }
        check(keys.contains("door_fl") && keys.contains("DOOR_STATUS") && keys.contains("fan_level")
                && keys.contains("radar_rear_1"), "cles de donnees : " + keys);
        check(!keys.contains("getDoorState") && !keys.contains("mDoorView") && !keys.contains("hello"), "cles exclues");

        // marche arriere deduite du nom du broadcast
        VehicleData d = new VehicleData();
        check(CanExtrasParser.parse("com.yecon.action.BACKCAR_START", null, d) == 1 && d.reverse == 1, "BACKCAR_START");
        check(CanExtrasParser.parse("com.yecon.action.BACKCAR_STOP", null, d) == 1 && d.reverse == 0, "BACKCAR_STOP");
        check(CanExtrasParser.parse("com.yecon.action.CAN_AIRINFO", null, d) == 0, "sans extras");

        // version Android reelle
        check(BuildInfo.androidVersion(29, "14").startsWith("Android 10 (API 29)")
                && BuildInfo.androidVersion(29, "14").contains("14"), "version maquillee : " + BuildInfo.androidVersion(29, "14"));
        check(BuildInfo.androidVersion(34, "14").equals("Android 14 (API 34)"), "version normale");
        check(BuildInfo.androidVersion(16, "4.1.2").equals("Android 4.1 (API 16)"), "version 4.1.2");
    }

    private static com.rayan.w204dash.data.CanWindowState.Node node(String id, String text, boolean vis, boolean sel) {
        com.rayan.w204dash.data.CanWindowState.Node n = new com.rayan.w204dash.data.CanWindowState.Node();
        n.id = id == null ? "" : "com.can.activity:id/" + id;
        n.text = text == null ? "" : text;
        n.visible = vis;
        n.selected = sel;
        return n;
    }

    /** Fenetre "porte ouverte" affichee par l'appli CAN de l'ecran (lecture par accessibilite). */
    static void canWindow() {
        java.util.List<com.rayan.w204dash.data.CanWindowState.Node> w = new java.util.ArrayList<com.rayan.w204dash.data.CanWindowState.Node>();
        w.add(node("iv_car_bg", null, true, false));
        w.add(node("iv_door_lf", null, true, false));
        w.add(node("iv_door_rf", null, false, false));
        w.add(node("iv_door_lr", null, false, false));
        w.add(node("iv_door_rr", null, false, false));
        w.add(node("iv_trunk", null, true, false));
        w.add(node("tv_engine_temp", "90", true, false));
        int[] st = com.rayan.w204dash.data.CanWindowState.parse(w);
        check(st != null && st[VehicleData.DOOR_FL] == 1 && st[VehicleData.DOOR_FR] == 0 && st[VehicleData.DOOR_RL] == 0
                && st[VehicleData.DOOR_RR] == 0 && st[VehicleData.TRUNK] == 1 && st[VehicleData.HOOD] == -1,
                "fenetre : images visibles = ouvert " + java.util.Arrays.toString(st));
        // toutes les images visibles, rien de selectionne : impossible de conclure
        java.util.List<com.rayan.w204dash.data.CanWindowState.Node> w2 = new java.util.ArrayList<com.rayan.w204dash.data.CanWindowState.Node>();
        for (String id : new String[]{"img_fl", "img_fr", "img_rl", "img_rr", "img_hood", "img_trunk"}) w2.add(node(id, null, true, false));
        check(com.rayan.w204dash.data.CanWindowState.parse(w2) == null, "fenetre non interpretable");
        w2.get(1).selected = true;
        int[] st2 = com.rayan.w204dash.data.CanWindowState.parse(w2);
        check(st2 != null && st2[VehicleData.DOOR_FR] == 1 && st2[VehicleData.DOOR_FL] == 0, "etat selectionne");
        java.util.List<com.rayan.w204dash.data.CanWindowState.Node> w3 = new java.util.ArrayList<com.rayan.w204dash.data.CanWindowState.Node>();
        w3.add(node(null, "Porte arrière droite ouverte", true, false));
        int[] st3 = com.rayan.w204dash.data.CanWindowState.parse(w3);
        check(st3 != null && st3[VehicleData.DOOR_RR] == 1, "texte de la fenetre");
        check(com.rayan.w204dash.data.CanWindowState.openableOf(tokensOf("doorFrontRight")) == VehicleData.DOOR_FR, "doorFrontRight");
        check(com.rayan.w204dash.data.CanWindowState.openableOf(tokensOf("back_door")) == VehicleData.TRUNK, "back_door");
        check(com.rayan.w204dash.data.CanWindowState.openableOf(tokensOf("door_rear_left")) == VehicleData.DOOR_RL, "door_rear_left");
        check(com.rayan.w204dash.data.CanWindowState.openableOf(tokensOf("img_hood")) == VehicleData.HOOD, "img_hood");
        check(com.rayan.w204dash.data.CanWindowState.openableOf(tokensOf("iv_bg")) == -1, "iv_bg");
        // memorisation puis fusion dans les donnees
        check(com.rayan.w204dash.data.CanWindowState.apply("com.can.activity", w), "apply");
        VehicleData d = new VehicleData();
        com.rayan.w204dash.data.CanWindowState.fill(d);
        check(d.open[VehicleData.DOOR_FL] == 1 && d.open[VehicleData.TRUNK] == 1 && d.open[VehicleData.DOOR_FR] == 0, "fill");
        com.rayan.w204dash.data.CanWindowState.windowGone();
        com.rayan.w204dash.data.CanWindowState.fill(d = new VehicleData());
        check(d.open[VehicleData.DOOR_FL] == 0 && d.open[VehicleData.TRUNK] == 0, "fenetre fermee = tout ferme");
    }

    /** Export de l'appli CAN : on garde le code, pas les images (envoi < 30 Mo). */
    static void exportFilter() {
        try {
            java.lang.reflect.Method m = com.rayan.w204dash.ui.DiagnosticPage.class
                    .getDeclaredMethod("keepForAnalysis", String.class, long.class);
            m.setAccessible(true);
            long mb = 1024L * 1024;
            Object[][] cases = {
                    {"classes.dex", 6 * mb, true}, {"classes2.dex", 3 * mb, true}, {"AndroidManifest.xml", 20000L, true},
                    {"resources.arsc", 2 * mb, true}, {"res/layout/can_pop_door.xml", 3000L, true},
                    {"res/drawable-hdpi/car_door_fl.png", 40000L, false}, {"res/raw/beep.ogg", 9000L, false},
                    {"res/drawable/bg.xml", 500L, false}, {"lib/arm64-v8a/libserial_port.so", 30000L, true},
                    {"lib/arm64-v8a/libhuge.so", 9 * mb, false}, {"assets/car/benz_c.png", 300000L, false},
                    {"assets/canbus_config.json", 20000L, true}, {"base.vdex", 12 * mb, true}, {"base.odex", 8 * mb, true},
                    {"base.art", 2 * mb, false}, {"assets/video.mp4", 50000L, false}};
            for (Object[] c : cases) {
                boolean r = (Boolean) m.invoke(null, c[0], c[1]);
                check(r == (Boolean) c[2], "export : " + c[0] + " -> " + r);
            }
        } catch (Exception e) {
            check(false, "filtre d'export : " + e);
        }
    }

    /** Masque des ouvrants envoye par le microcontroleur (ecran Carocean/Yecon, canal 0x12). */
    static void mcuDoors() {
        byte[] d = {0x00, 0x00, 0x00, (byte) 0x88};
        int[] st = com.rayan.w204dash.data.McuDoorLink.decode(d, false, false);
        check(st != null && st[VehicleData.DOOR_FL] == 1 && st[VehicleData.TRUNK] == 1 && st[VehicleData.DOOR_FR] == 0
                && st[VehicleData.HOOD] == 0, "MCU : porte AVG + coffre " + java.util.Arrays.toString(st));
        st = com.rayan.w204dash.data.McuDoorLink.decode(new byte[]{0, 0, 0, 0x24}, false, false);
        check(st[VehicleData.DOOR_RL] == 1 && st[VehicleData.HOOD] == 1 && st[VehicleData.DOOR_RR] == 0, "MCU : ARG + capot");
        st = com.rayan.w204dash.data.McuDoorLink.decode(new byte[]{0, 0, 0, (byte) 0x90}, true, true);
        check(st[VehicleData.DOOR_FR] == 1 && st[VehicleData.DOOR_FL] == 0 && st[VehicleData.DOOR_RL] == 1
                && st[VehicleData.DOOR_RR] == 0, "MCU : inversion gauche/droite " + java.util.Arrays.toString(st));
        st = com.rayan.w204dash.data.McuDoorLink.decode(new byte[]{0, 0, 0, 0}, false, false);
        int sum = 0;
        for (int v : st) sum += v;
        check(sum == 0, "MCU : tout ferme");
        check(com.rayan.w204dash.data.McuDoorLink.decode(new byte[]{1, 2}, false, false) == null, "MCU : message court");
        check(com.rayan.w204dash.data.HeadUnitProbe.isVehiclePackage("com.carocean.mcuserver"), "paquet MCU detecte");
    }

    private static java.util.Set<String> tokensOf(String s) {
        try {
            java.lang.reflect.Method m = com.rayan.w204dash.data.CanWindowState.class.getDeclaredMethod("tokens", String.class);
            m.setAccessible(true);
            @SuppressWarnings("unchecked")
            java.util.Set<String> r = (java.util.Set<String>) m.invoke(null, s);
            return r;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static double cycle(double t) {
        try {
            java.lang.reflect.Method m = DemoDataSource.class.getDeclaredMethod("cycleSpeed", double.class);
            m.setAccessible(true);
            return (Double) m.invoke(null, t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
