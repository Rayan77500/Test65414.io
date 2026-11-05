package com.rayan.w204dash.media;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;

import com.rayan.w204dash.DiagLog;
import com.rayan.w204dash.compat.Compat;
import com.rayan.w204dash.compat.MediaSessions21;

import java.util.ArrayList;

/**
 * Widget musique : commandes lecture / pause / suivant / precedent envoyees
 * au lecteur actif (touches multimedia standard) et, si possible, titre et
 * artiste en cours (MediaSessions ou broadcasts des lecteurs courants).
 */
public final class MediaHub {

    public interface Listener {
        void onMediaChanged(MediaHub hub);
    }

    private static final String[] META_ACTIONS = {
            "com.android.music.metachanged", "com.android.music.playstatechanged",
            "com.spotify.music.metadatachanged", "com.spotify.music.playbackstatechanged",
            "com.htc.music.metachanged", "com.sec.android.app.music.metachanged",
            "com.miui.player.metachanged", "com.real.IMP.metachanged", "com.sonyericsson.music.metachanged",
            "com.amazon.mp3.metachanged", "com.andrew.apollo.metachanged", "fm.last.android.metachanged",
            "com.rdio.android.metachanged", "com.samsung.sec.android.MusicPlayer.metachanged",
            "com.nullsoft.winamp.metachanged", "com.maxmpz.audioplayer.TRACK_CHANGED",
            "com.maxmpz.audioplayer.STATUS_CHANGED", "com.microntek.music.report", "com.android.music.musicservicecommand",
    };

    private final Context ctx;
    private final ArrayList<Listener> listeners = new ArrayList<Listener>();
    private MediaSessions21 sessions;
    private boolean sessionsActive;

    private String title;
    private String artist;
    private String app;
    private int playing = -1;
    private Bitmap art;

    public MediaHub(Context c) {
        ctx = c.getApplicationContext();
        IntentFilter f = new IntentFilter();
        for (String a : META_ACTIONS) {
            f.addAction(a);
        }
        try {
            Compat.registerReceiverExported(ctx, new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    onBroadcast(intent);
                }
            }, f);
        } catch (Throwable t) {
            DiagLog.e("Musique", "Écoute des lecteurs impossible", t);
        }
        refreshSessions();
    }

    /** (Re)connexion aux MediaSessions si l'acces aux notifications est accorde. */
    public void refreshSessions() {
        if (!Compat.supportsMediaSessions()) return;
        try {
            if (sessions == null) {
                sessions = new MediaSessions21(ctx, new MediaSessions21.Sink() {
                    @Override
                    public void onMediaState(String t, String a, String appName, int play, Bitmap bmp) {
                        if (t != null || appName != null) {
                            title = t;
                            artist = a;
                            app = appName;
                            art = bmp;
                        }
                        if (play >= 0) playing = play;
                        notifyListeners();
                    }
                });
            }
            sessionsActive = sessions.start();
            if (sessionsActive) DiagLog.i("Musique", "Lecture des titres via les sessions multimédia : OK");
        } catch (Throwable t) {
            sessionsActive = false;
        }
    }

    private void onBroadcast(Intent intent) {
        if (sessionsActive && title != null) return; // les sessions sont plus fiables
        Bundle b = intent.getExtras();
        if (b == null) return;
        String t = str(b, "track", "title", "name");
        String a = str(b, "artist", "singer");
        if (t != null) {
            title = t;
            artist = a;
            app = appFromAction(intent.getAction());
            art = null;
        }
        if (b.containsKey("playing")) playing = b.getBoolean("playing") ? 1 : 0;
        else if (b.containsKey("isPlaying")) playing = b.getBoolean("isPlaying") ? 1 : 0;
        notifyListeners();
    }

    private static String str(Bundle b, String... keys) {
        for (String k : keys) {
            Object o = b.get(k);
            if (o != null && o.toString().trim().length() > 0) return o.toString();
        }
        return null;
    }

    private static String appFromAction(String action) {
        if (action == null) return null;
        if (action.startsWith("com.spotify")) return "Spotify";
        if (action.startsWith("com.maxmpz")) return "Poweramp";
        if (action.startsWith("com.microntek")) return "Musique (écran)";
        return "Lecteur";
    }

    public void playPause() {
        if (sessionsActive && sessions != null && sessions.command(0)) return;
        Compat.sendMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
        if (playing >= 0) {
            playing = playing == 1 ? 0 : 1;
            notifyListeners();
        }
    }

    public void next() {
        if (sessionsActive && sessions != null && sessions.command(1)) return;
        Compat.sendMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_NEXT);
    }

    public void previous() {
        if (sessionsActive && sessions != null && sessions.command(-1)) return;
        Compat.sendMediaKey(ctx, KeyEvent.KEYCODE_MEDIA_PREVIOUS);
    }

    /** Vrai si l'acces aux notifications a ete accorde (Android 4.3+). */
    public boolean hasNotificationAccess() {
        if (Build.VERSION.SDK_INT < 18) return false;
        String s = Settings.Secure.getString(ctx.getContentResolver(), "enabled_notification_listeners");
        return s != null && s.contains(ctx.getPackageName());
    }

    public boolean canShowTitles() {
        return Compat.supportsMediaSessions();
    }

    public String title() {
        return title;
    }

    public String artist() {
        return artist;
    }

    public String app() {
        return app;
    }

    /** 1 = en lecture, 0 = en pause, -1 = inconnu. */
    public int playing() {
        return playing;
    }

    public Bitmap art() {
        return art;
    }

    public void addListener(Listener l) {
        if (!listeners.contains(l)) listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyListeners() {
        for (Listener l : listeners.toArray(new Listener[listeners.size()])) {
            l.onMediaChanged(this);
        }
    }
}
