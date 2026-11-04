package com.rayan.w204dash.compat;

import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * Lecture du titre en cours via les MediaSessions (Android 5+). Necessite
 * l'acces aux notifications accorde a l'application.
 */
public final class MediaSessions21 {

    public interface Sink {
        void onMediaState(String title, String artist, String app, int playing, Bitmap art);
    }

    private final Context ctx;
    private final Sink sink;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaSessionManager msm;
    private MediaController controller;
    private MediaSessionManager.OnActiveSessionsChangedListener sessionsListener;
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            publish();
        }

        @Override
        public void onMetadataChanged(MediaMetadata metadata) {
            publish();
        }

        @Override
        public void onSessionDestroyed() {
            refresh();
        }
    };

    public MediaSessions21(Context ctx, Sink sink) {
        this.ctx = ctx.getApplicationContext();
        this.sink = sink;
    }

    private ComponentName component() {
        return new ComponentName(ctx, MediaListenerService.class);
    }

    /** @return faux si l'acces aux notifications n'est pas accorde */
    public boolean start() {
        try {
            msm = (MediaSessionManager) ctx.getSystemService(Context.MEDIA_SESSION_SERVICE);
            if (msm == null) return false;
            if (sessionsListener == null) {
                sessionsListener = new MediaSessionManager.OnActiveSessionsChangedListener() {
                    @Override
                    public void onActiveSessionsChanged(List<MediaController> controllers) {
                        choose(controllers);
                    }
                };
                msm.addOnActiveSessionsChangedListener(sessionsListener, component(), handler);
            }
            choose(msm.getActiveSessions(component()));
            return true;
        } catch (SecurityException e) {
            sessionsListener = null;
            return false;
        } catch (Throwable t) {
            return false;
        }
    }

    public void refresh() {
        if (msm == null) {
            start();
            return;
        }
        try {
            choose(msm.getActiveSessions(component()));
        } catch (Throwable ignored) {
            // acces retire
        }
    }

    private void choose(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) {
                    best = c;
                    break;
                }
                if (best == null) best = c;
            }
        }
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        controller = best;
        if (controller != null) controller.registerCallback(callback, handler);
        publish();
    }

    private void publish() {
        MediaController c = controller;
        if (c == null) {
            sink.onMediaState(null, null, null, -1, null);
            return;
        }
        String title = null;
        String artist = null;
        Bitmap art = null;
        MediaMetadata md = c.getMetadata();
        if (md != null) {
            title = md.getString(MediaMetadata.METADATA_KEY_TITLE);
            artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST);
            if (artist == null) artist = md.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST);
            art = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
            if (art == null) art = md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        }
        PlaybackState st = c.getPlaybackState();
        int playing = st == null ? -1 : (st.getState() == PlaybackState.STATE_PLAYING ? 1 : 0);
        String app = c.getPackageName();
        try {
            app = ctx.getPackageManager().getApplicationLabel(
                    ctx.getPackageManager().getApplicationInfo(app, 0)).toString();
        } catch (Throwable ignored) {
            // nom de paquet
        }
        sink.onMediaState(title, artist, app, playing, art);
    }

    /** @return vrai si la commande a ete envoyee a une session */
    public boolean command(int which) {
        MediaController c = controller;
        if (c == null) return false;
        MediaController.TransportControls tc = c.getTransportControls();
        switch (which) {
            case 0:
                PlaybackState st = c.getPlaybackState();
                if (st != null && st.getState() == PlaybackState.STATE_PLAYING) tc.pause();
                else tc.play();
                return true;
            case 1:
                tc.skipToNext();
                return true;
            case -1:
                tc.skipToPrevious();
                return true;
            default:
                return false;
        }
    }

    public void stop() {
        if (controller != null) {
            try {
                controller.unregisterCallback(callback);
            } catch (Throwable ignored) {
                // ignore
            }
            controller = null;
        }
        if (msm != null && sessionsListener != null) {
            try {
                msm.removeOnActiveSessionsChangedListener(sessionsListener);
            } catch (Throwable ignored) {
                // ignore
            }
        }
        sessionsListener = null;
    }
}
