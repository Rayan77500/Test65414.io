package com.rayan.w204dash;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import com.rayan.w204dash.data.McuDoorLink;
import com.rayan.w204dash.data.VehicleData;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * Dialogue avec un faux service du microcontroleur (meme protocole que
 * com.carocean.mcuserver d'apres l'appli CAN de l'ecran).
 */
@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.PAUSED)
@Config(sdk = 34)
public class McuLinkTest {

    @Test
    public void doorsFromMcu() throws Exception {
        final Messenger[] client = new Messenger[1];
        final int[][] codes = new int[1][];
        final int[] what = {0};
        Messenger fake = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message m) {
                what[0] = m.what;
                client[0] = m.replyTo;
                codes[0] = m.getData().getIntArray("cmdcode");
            }
        });
        Intent it = new Intent(McuDoorLink.SERVICE_ACTION).setPackage(McuDoorLink.SERVICE_PACKAGE);
        shadowOf(RuntimeEnvironment.getApplication()).setComponentNameAndServiceForBindServiceForIntent(it,
                new ComponentName(McuDoorLink.SERVICE_PACKAGE, "com.carocean.mcuserver.McuService"), fake.getBinder());

        VehicleData d = new VehicleData();
        McuDoorLink link = new McuDoorLink(RuntimeEnvironment.getApplication(), d);
        assertTrue("liaison demandee", link.start());
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("message d'abonnement", 0x100, what[0]);
        assertArrayEquals("abonnement au canal portes", new int[]{0x12}, codes[0]);
        assertNotNull("adresse de retour", client[0]);

        Message m = Message.obtain(null, 0x102);
        Bundle b = new Bundle();
        b.putInt("cmdcode", 0x12);
        b.putByteArray("data", new byte[]{0x00, 0x01, 0x00, (byte) 0x88});
        m.setData(b);
        client[0].send(m);
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("porte AVG ouverte", 1, d.open[VehicleData.DOOR_FL]);
        assertEquals("coffre ouvert", 1, d.open[VehicleData.TRUNK]);
        assertEquals("porte AVD fermee", 0, d.open[VehicleData.DOOR_FR]);
        assertTrue(link.state(), link.state().startsWith("OK"));

        link.stop();
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals("desabonnement", 0x101, what[0]);
    }

    @Test
    public void scanAllChannels() throws Exception {
        final Messenger[] client = new Messenger[1];
        final int[][] codes = new int[1][];
        final int[] what = {0};
        Messenger fake = new Messenger(new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message m) {
                what[0] = m.what;
                client[0] = m.replyTo;
                codes[0] = m.getData().getIntArray("cmdcode");
            }
        });
        Intent it = new Intent(McuDoorLink.SERVICE_ACTION).setPackage(McuDoorLink.SERVICE_PACKAGE);
        shadowOf(RuntimeEnvironment.getApplication()).setComponentNameAndServiceForBindServiceForIntent(it,
                new ComponentName(McuDoorLink.SERVICE_PACKAGE, "com.carocean.mcuserver.McuService"), fake.getBinder());
        final String[] summary = {null};
        com.rayan.w204dash.data.McuScanner sc = new com.rayan.w204dash.data.McuScanner(RuntimeEnvironment.getApplication());
        assertTrue(sc.start(60, new com.rayan.w204dash.data.McuScanner.Done() {
            @Override
            public void onDone(String s) {
                summary[0] = s;
            }
        }));
        shadowOf(Looper.getMainLooper()).idle();
        assertEquals(0x100, what[0]);
        assertEquals("255 canaux", 255, codes[0].length);
        for (int code : new int[]{0x21, 0x21, 0x30}) {
            Message m = Message.obtain(null, 0x102);
            Bundle b = new Bundle();
            b.putInt("cmdcode", code);
            b.putByteArray("data", new byte[]{1, 2, (byte) code});
            m.setData(b);
            client[0].send(m);
        }
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(61));
        assertEquals("desabonnement apres 60 s", 0x101, what[0]);
        assertNotNull(summary[0]);
        assertTrue(summary[0], summary[0].contains("0x21=2") && summary[0].contains("0x30=1"));
    }
}
