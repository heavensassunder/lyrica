package com.heavens.lyricfloat;

import android.content.Context;
import android.content.Intent;
import android.media.session.PlaybackState;
import android.os.SystemClock;

/** All components run in the same app process; snapshots are immutable. */
public final class SongState {
    public static final String ACTION = "com.heavens.lyricfloat.STATE_CHANGED";
    public static final class Snapshot {
        public final String title, artist;
        public final long duration, position, updated;
        public final float speed;
        public final boolean playing;
        Snapshot(String title, String artist, long duration, long position, long updated, float speed, boolean playing) {
            this.title = title; this.artist = artist; this.duration = duration; this.position = position;
            this.updated = updated; this.speed = speed; this.playing = playing;
        }
        public long getCurrentPositionMs() {
            // PlaybackState timestamps use elapsedRealtime, not wall clock time.
            long result = position;
            if (playing && updated > 0) result += (long) (Math.max(0, SystemClock.elapsedRealtime() - updated) * speed);
            result = Math.max(0, result);
            return duration > 0 ? Math.min(result, duration) : result;
        }
    }
    private static volatile Snapshot current = new Snapshot("", "", 0, 0, 0, 0, false);
    public static Snapshot get() { return current; }
    public static void update(Context context, String title, String artist, long duration, PlaybackState state) {
        boolean playing = state != null && state.getState() == PlaybackState.STATE_PLAYING;
        long pos = state == null ? 0 : Math.max(0, state.getPosition());
        current = new Snapshot(title == null ? "" : title, artist == null ? "" : artist,
                Math.max(0, duration), pos, state == null ? 0 : state.getLastPositionUpdateTime(),
                state == null ? 0 : state.getPlaybackSpeed(), playing);
        notifyChanged(context);
    }
    public static void notifyChanged(Context context) {
        context.sendBroadcast(new Intent(ACTION).setPackage(context.getPackageName()));
    }
    private SongState() { }
}
