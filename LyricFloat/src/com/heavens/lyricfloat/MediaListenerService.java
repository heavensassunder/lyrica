package com.heavens.lyricfloat;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import java.util.List;
import java.util.ArrayList;

public class MediaListenerService extends NotificationListenerService {
    private MediaSessionManager manager;
    private MediaController active;
    private final List<MediaController> observed = new ArrayList<MediaController>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { chooseSession(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { chooseSession(); }
        @Override public void onSessionDestroyed() { chooseSession(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override public void onActiveSessionsChanged(List<MediaController> controllers) { select(controllers); }
            };
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (manager == null) return;
        try {
            ComponentName name = new ComponentName(this, MediaListenerService.class);
            manager.removeOnActiveSessionsChangedListener(sessions);
            manager.addOnActiveSessionsChangedListener(sessions, name, main);
            chooseSession();
        } catch (SecurityException e) { clear(); }
    }
    private void chooseSession() {
        if (manager == null) return;
        try { select(manager.getActiveSessions(new ComponentName(this, MediaListenerService.class))); }
        catch (SecurityException e) { clear(); }
    }
    private void select(List<MediaController> controllers) {
        // Watch every session, not just the selected one: a paused competing session
        // can become playing without changing the active-session list.
        List<MediaController> wanted = new ArrayList<MediaController>();
        if (controllers != null) for (MediaController c : controllers)
            if (c != null && !getPackageName().equals(c.getPackageName())) wanted.add(c);
        for (MediaController old : observed) {
            boolean present = false;
            for (MediaController c : wanted) if (old.getSessionToken().equals(c.getSessionToken())) present = true;
            if (!present) old.unregisterCallback(callback);
        }
        List<MediaController> next = new ArrayList<MediaController>();
        for (MediaController c : wanted) {
            MediaController existing = null;
            for (MediaController old : observed) if (old.getSessionToken().equals(c.getSessionToken())) existing = old;
            if (existing == null) { existing = c; existing.registerCallback(callback, main); }
            next.add(existing);
        }
        observed.clear(); observed.addAll(next);
        MediaController best = null;
        for (MediaController c : observed) {
            PlaybackState s = c.getPlaybackState();
            if (best == null) best = c;
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
        }
        active = best;
        publish();
    }
    private void publish() {
        if (active == null) { SongState.update(this, "", "", 0, null); return; }
        try {
            MediaMetadata m = active.getMetadata();
            String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
            String artist = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
            long duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
            SongState.update(this, title, artist, duration, active.getPlaybackState());
        } catch (RuntimeException e) { clear(); }
    }
    private void clear() {
        for (MediaController c : observed) c.unregisterCallback(callback);
        observed.clear(); active = null;
        SongState.update(this, "", "", 0, null);
    }
    @Override public void onListenerDisconnected() { clear(); super.onListenerDisconnected(); }
    @Override public void onDestroy() {
        if (manager != null) manager.removeOnActiveSessionsChangedListener(sessions);
        clear(); super.onDestroy();
    }
}
