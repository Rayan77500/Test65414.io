package com.rayan.w204dash.compat;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import com.rayan.w204dash.App;

/**
 * Service "Acces aux notifications" : n'affiche rien, il permet seulement a
 * l'application de lire le titre / l'artiste du lecteur de musique actif
 * (Android 5+). L'utilisateur l'active dans les reglages Android.
 */
public class MediaListenerService extends NotificationListenerService {

    @Override
    public void onListenerConnected() {
        try {
            App.get().media().refreshSessions();
        } catch (Throwable ignored) {
            // application pas encore prete
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        // rien
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        // rien
    }
}
