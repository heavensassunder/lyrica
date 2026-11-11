package com.heavens.lyricfloat;

import android.content.ComponentName;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import java.util.ArrayList;
import java.util.List;

/**
 * Watches media sessions through notification-listener access. Session callbacks are
 * supplemented by a slow reconciliation pass, because several music apps stop sending
 * metadata callbacks while they keep playing (or after a process restart).
 */
public class MediaListenerService extends NotificationListenerService {
    /** Shown in MainActivity to make "Waiting for music" diagnosable. */
    public static volatile String status = "Listener not connected";
    private static volatile MediaListenerService instance;
    private MediaSessionManager manager;
    private MediaController active;
    private final List<MediaController> observed = new ArrayList<MediaController>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean connected;
    private int rebindAttempts;
    private final ComponentName component = new ComponentName("com.heavens.lyricfloat", "com.heavens.lyricfloat.MediaListenerService");
    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) { chooseSession(); }
        @Override public void onMetadataChanged(MediaMetadata metadata) { chooseSession(); }
        @Override public void onSessionDestroyed() { chooseSession(); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions =
            new MediaSessionManager.OnActiveSessionsChangedListener() {
                @Override public void onActiveSessionsChanged(List<MediaController> controllers) { select(controllers); }
            };
    private final Runnable reconcile = new Runnable() {
        @Override public void run() {
            if (!connected) return;
            chooseSession();
            main.postDelayed(this, 3000);
        }
    };
    private final Runnable rebind = new Runnable() {
        @Override public void run() {
            if (connected || rebindAttempts >= 6) return;
            rebindAttempts++;
            try {
                NotificationListenerService.requestRebind(component);
                status = "Reconnecting to notification access…";
            } catch (RuntimeException e) { status = "Reconnect failed: " + e.getClass().getSimpleName(); }
            main.postDelayed(this, 3000);
        }
    };
    private final Runnable refresh = new Runnable() {
        @Override public void run() { chooseSession(); }
    };
    @Override public void onCreate() {
        super.onCreate(); instance = this;
    }
    @Override public void onListenerConnected() {
        super.onListenerConnected();
        instance = this; connected = true; rebindAttempts = 0;
        main.removeCallbacks(rebind);
        manager = (MediaSessionManager) getSystemService(MEDIA_SESSION_SERVICE);
        if (manager == null) { status = "No media session service on this device"; return; }
        try {
            manager.removeOnActiveSessionsChangedListener(sessions);
            manager.addOnActiveSessionsChangedListener(sessions, new ComponentName(this, MediaListenerService.class), main);
            status = "Listener connected";
            chooseSession();
            main.removeCallbacks(reconcile); main.postDelayed(reconcile, 3000);
        } catch (SecurityException e) { status = "Notification access rejected — re-enable it"; clear(); }
    }
    @Override public void onListenerDisconnected() {
        connected = false;
        main.removeCallbacks(reconcile); main.removeCallbacks(refresh);
        clear();
        status = "Listener disconnected — trying to reconnect";
        rebindAttempts = 0;
        main.removeCallbacks(rebind); main.postDelayed(rebind, 2000);
        super.onListenerDisconnected();
    }
    @Override public void onNotificationPosted(StatusBarNotification sbn) { debounced(); }
    @Override public void onNotificationRemoved(StatusBarNotification sbn) { debounced(); }
    private void debounced() {
        // Bursty notification updates are collapsed into one session refresh.
        main.removeCallbacks(refresh); main.postDelayed(refresh, 300);
    }
    private void chooseSession() {
        if (manager == null) return;
        try { select(manager.getActiveSessions(new ComponentName(this, MediaListenerService.class))); }
        catch (SecurityException e) {
            status = "Notification access missing — enable it again";
            clear();
        } catch (RuntimeException e) {
            status = "Media session query failed: " + e.getClass().getSimpleName();
        }
    }
    private void select(List<MediaController> controllers) {
        if (controllers == null) controllers = new ArrayList<MediaController>();
        // Watch every session, not just the selected one: a paused competing session
        // can become playing without changing the active-session list.
        List<MediaController> wanted = new ArrayList<MediaController>();
        List<String> tokens = new ArrayList<String>();
        for (MediaController c : controllers) {
            if (c == null) continue;
            try {
                String token = c.getSessionToken().toString();
                if (!getPackageName().equals(c.getPackageName()) && !tokens.contains(token)) { tokens.add(token); wanted.add(c); }
            } catch (RuntimeException ignored) { }
        }
        for (MediaController old : observed) {
            boolean present = false;
            for (MediaController c : wanted) {
                try { if (old.getSessionToken().equals(c.getSessionToken())) present = true; }
                catch (RuntimeException ignored) { }
            }
            if (!present) { try { old.unregisterCallback(callback); } catch (RuntimeException ignored) { } }
        }
        List<MediaController> next = new ArrayList<MediaController>();
        for (MediaController c : wanted) {
            MediaController existing = null;
            for (MediaController old : observed) {
                try { if (old.getSessionToken().equals(c.getSessionToken())) existing = old; }
                catch (RuntimeException ignored) { }
            }
            if (existing == null) {
                existing = c;
                try { existing.registerCallback(callback, main); } catch (RuntimeException ignored) { }
            }
            next.add(existing);
        }
        observed.clear(); observed.addAll(next);
        publish();
    }
    private String titleOf(MediaController c) {
        try {
            MediaMetadata m = c.getMetadata();
            String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
            return title == null ? "" : title.trim();
        } catch (RuntimeException e) { return ""; }
    }
    private boolean playing(MediaController c) {
        try {
            PlaybackState s = c.getPlaybackState();
            return s != null && s.getState() == PlaybackState.STATE_PLAYING;
        } catch (RuntimeException e) { return false; }
    }
    /** Prefer a playing session with a title, then any titled session, then anything. */
    private MediaController best() {
        MediaController titled = null, any = null;
        for (MediaController c : observed) {
            boolean hasTitle = !titleOf(c).isEmpty();
            if (playing(c) && hasTitle) return c;
            if (hasTitle && titled == null) titled = c;
            if (any == null) any = c;
        }
        return titled != null ? titled : any;
    }
    private void publish() {
        active = best();
        if (active == null) {
            status = "No active media sessions";
            SongState.update(this, "", "", 0, null);
            return;
        }
        for (int attempt = 0; attempt < observed.size() && active != null; attempt++) {
            try {
                MediaMetadata m = active.getMetadata();
                String title = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_TITLE);
                String artist = m == null ? null : m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                long duration = m == null ? 0 : m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                String pkg;
                try { pkg = active.getPackageName(); } catch (RuntimeException e) { pkg = "unknown"; }
                status = (title == null || title.trim().isEmpty() ? "Session without title" : "Playing: " + title) + " · " + pkg;
                SongState.update(this, title, artist, duration, active.getPlaybackState());
                return;
            } catch (RuntimeException e) {
                // A dead controller must not discard the other working sessions.
                MediaController failed = active;
                observed.remove(failed);
                try { failed.unregisterCallback(callback); } catch (RuntimeException ignored) { }
                active = best();
            }
        }
        status = observed.isEmpty() ? "No active media sessions" : "Media session unreadable";
        SongState.update(this, "", "", 0, null);
    }
    private void clear() {
        for (MediaController c : observed) { try { c.unregisterCallback(callback); } catch (RuntimeException ignored) { } }
        observed.clear(); active = null;
        SongState.update(this, "", "", 0, null);
    }
    /** Used by MainActivity's Refresh button. */
    public static void refreshNow() {
        MediaListenerService service = instance;
        if (service == null) { status = "Listener service not running — toggle Notification access"; return; }
        if (!service.connected) {
            status = "Listener disconnected — reconnect requested";
            service.rebindAttempts = 0;
            service.main.removeCallbacks(service.rebind);
            service.main.post(service.rebind);
        }
        service.main.post(new Runnable() { public void run() { service.chooseSession(); } });
    }
    @Override public void onDestroy() {
        connected = false; instance = null;
        main.removeCallbacks(reconcile); main.removeCallbacks(rebind); main.removeCallbacks(refresh);
        if (manager != null) { try { manager.removeOnActiveSessionsChangedListener(sessions); } catch (RuntimeException ignored) { } }
        clear(); status = "Listener stopped"; super.onDestroy();
    }
}
