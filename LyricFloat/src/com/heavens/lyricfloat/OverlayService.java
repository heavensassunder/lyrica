package com.heavens.lyricfloat;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

public class OverlayService extends Service {
    public static final String ACTION_SETTINGS = "com.heavens.lyricfloat.SETTINGS";
    public static final String ACTION_TEST = "com.heavens.lyricfloat.TEST";
    public static final String ACTION_TEST_GUITAR = "com.heavens.lyricfloat.TEST_GUITAR";
    public static final String ACTION_CHORDS = "com.heavens.lyricfloat.CHORDS";
    public static final String ACTION_STOP = "com.heavens.lyricfloat.STOP";
    public static volatile boolean running, editing, testing;
    public static volatile String lyricStatus = "Waiting for music";
    private WindowManager wm;
    private WindowManager.LayoutParams params;
    private LinearLayout panel;
    private final TextView[] views = new TextView[5];
    private TextView status;
    private GuitarView guitar;
    private ChordStore chordStore;
    private ChordSheet storedSheet;
    private String guitarStyleKey = "";
    private Prefs prefs;
    private LyricsFetcher fetcher;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LrcParser parser;
    private List<String> plain = new ArrayList<String>();
    private String songKey = "";
    private int generation, lastIndex = Integer.MIN_VALUE;
    private boolean visible = true, test;
    private long testStart;
    private float downX, downY;
    private int startX, startY;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { if (!test) songChanged(); else render(); }
    };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            render();
            if (visible && (test || SongState.get().playing)) handler.postDelayed(this, prefs.guitar() ? 60 : 100);
        }
    };
    @Override public void onCreate() {
        super.onCreate();
        running = true; prefs = new Prefs(this); fetcher = new LyricsFetcher(this); chordStore = new ChordStore(this);
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.createNotificationChannel(new NotificationChannel("overlay", "Lyric overlay", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, "overlay")
                .setSmallIcon(android.R.drawable.ic_media_play).setContentTitle("LyricFloat is running")
                .setContentText("Tap to open settings").setContentIntent(open).setOngoing(true).build();
        startForeground(1, notification);
        IntentFilter filter = new IntentFilter(SongState.ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        createPanel();
        try { wm.addView(panel, params); } catch (RuntimeException e) { panel = null; stopSelf(); return; }
        songChanged();
    }
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + 0.5f); }
    private void createPanel() {
        panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER); panel.setPadding(dp(12), dp(8), dp(12), dp(8));
        guitar = new GuitarView(this, prefs); guitar.setVisibility(View.GONE);
        panel.addView(guitar, new LinearLayout.LayoutParams(-1, 0, 1));
        for (int i = 0; i < 5; i++) {
            TextView v = new OutlineTextView(this); v.setTextColor(Color.WHITE); v.setGravity(Gravity.CENTER);
            v.setMaxLines(2); v.setShadowLayer(dp(3), 0, dp(1), Color.BLACK);
            views[i] = v; panel.addView(v, new LinearLayout.LayoutParams(-1, -2));
        }
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(12);
        status.setGravity(Gravity.CENTER); panel.addView(status, new LinearLayout.LayoutParams(-1, -2));
        // NOT_TOUCHABLE makes the normal overlay click through. Edit mode removes it.
        params = new WindowManager.LayoutParams(dp(prefs.width()), -2, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.LEFT; params.x = prefs.x(); params.y = prefs.y();
        panel.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                if (!editing) return false;
                if (e.getAction() == MotionEvent.ACTION_DOWN) {
                    downX = e.getRawX(); downY = e.getRawY(); startX = params.x; startY = params.y; return true;
                }
                if (e.getAction() == MotionEvent.ACTION_MOVE) {
                    params.x = startX + (int) (e.getRawX() - downX); params.y = startY + (int) (e.getRawY() - downY);
                    updateWindow(); return true;
                }
                return true;
            }
        });
        applySettings();
    }
    private void updateWindow() {
        if (wm != null && panel != null && panel.isAttachedToWindow()) {
            try { wm.updateViewLayout(panel, params); } catch (RuntimeException ignored) { }
        }
    }
    private void applySettings() {
        if (panel == null) return;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!editing) params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        params.alpha = Math.max(.2f, Math.min(1f, prefs.overlayAlpha() / 100f));
        // Android 12+ blocks touches through an untrusted overlay above 0.8 opacity.
        if (Build.VERSION.SDK_INT >= 31 && !editing) params.alpha = Math.min(.8f, params.alpha);
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        params.width = prefs.guitar() ? (int) (metrics.widthPixels * .95f) : dp(prefs.width());
        params.height = prefs.guitar() ? (int) (metrics.heightPixels * prefs.guitarHeight() / 100f) : WindowManager.LayoutParams.WRAP_CONTENT;
        GradientDrawable bg = new GradientDrawable();
        int panelColor = prefs.color(3);
        bg.setColor(Color.argb(Color.alpha(panelColor) * Math.max(0, Math.min(100, prefs.panelAlpha())) / 100,
                Color.red(panelColor), Color.green(panelColor), Color.blue(panelColor)));
        bg.setCornerRadius(dp(16));
        if (editing) bg.setStroke(dp(2), Color.CYAN);
        panel.setBackground(bg);
        for (int i = 0; i < 5; i++) {
            views[i].setTextSize(i == 2 ? prefs.textSizeSp() : Math.max(10, prefs.textSizeSp() - 4));
            Typeface face = prefs.font() == 1 ? Typeface.SERIF : prefs.font() == 2 ? Typeface.MONOSPACE : Typeface.SANS_SERIF;
            int style = prefs.font() == 3 ? Typeface.BOLD_ITALIC : i == 2 ? Typeface.BOLD : Typeface.NORMAL;
            views[i].setTypeface(face, style);
            views[i].setTextColor(prefs.color(i == 2 ? 0 : 1));
            ((OutlineTextView) views[i]).setOutline(prefs.color(2), dp(prefs.outline()));
            if (prefs.outline() > 0) views[i].setShadowLayer(dp(prefs.outline()), 0, dp(1), prefs.color(2));
            else views[i].setShadowLayer(0, 0, 0, Color.TRANSPARENT);
            views[i].setAlpha(i == 2 ? 1f : (i == 1 || i == 3 ? .55f : .3f));
        }
        status.setTextColor(prefs.color(1));
        guitar.setVisibility(prefs.guitar() ? View.VISIBLE : View.GONE);
        guitar.updateStyle();
        // Only re-measure rows when a setting that affects layout or chord spelling changes.
        String style = prefs.guitarSize() + "|" + prefs.font() + "|" + prefs.transpose() + "|" + prefs.capo() + "|" + prefs.flats() + "|" + prefs.simplify();
        if (!style.equals(guitarStyleKey)) { guitarStyleKey = style; refreshGuitar(); }
        updateWindow(); lastIndex = Integer.MIN_VALUE; render();
    }
    private void setStatus(String text) { lyricStatus = text; SongState.notifyChanged(this); }
    private void songChanged() {
        SongState.Snapshot s = SongState.get();
        String key = s.title + "\n" + s.artist + "\n" + s.duration;
        if (!key.equals(songKey)) {
            songKey = key; final int request = ++generation;
            parser = null; plain.clear(); lastIndex = Integer.MIN_VALUE;
            storedSheet = s.title.isEmpty() ? null : chordStore.load(s.title, s.artist);
            refreshGuitar();
            if (s.title.trim().isEmpty()) { setStatus("Waiting for music"); }
            else {
                setStatus("Searching lyrics...");
                fetcher.fetch(s.title, s.artist, s.duration, new LyricsFetcher.Callback() {
                    @Override public void onResult(LyricsFetcher.Result r) {
                        if (request != generation || test || !running) return;
                        if (r == null) setStatus("Lyrics not found");
                        else if (r.synced) { parser = new LrcParser(r.text); setStatus("Synced lyrics"); }
                        else {
                            for (String line : r.text.split("\\r?\\n")) if (!line.trim().isEmpty()) plain.add(line.trim());
                            setStatus("No synced lyrics for this song.");
                        }
                        refreshGuitar(); lastIndex = Integer.MIN_VALUE; updateVisibility(); restartLoop(); render();
                    }
                });
            }
        }
        updateVisibility(); restartLoop(); render();
    }
    private void refreshGuitar() {
        if (guitar == null) return;
        ChordSheet shown = storedSheet;
        if (shown == null && parser != null) shown = ChordSheet.fromLyrics(parser.lines());
        if (shown == null && !plain.isEmpty()) {
            shown = new ChordSheet();
            // Unsynchronised lyrics have no real timing: use a gentle 4 s cadence for the guitar layout only.
            for (String text : plain) {
                ChordSheet.ChordLine line = new ChordSheet.ChordLine(); line.text = text;
                line.timeMs = (long) shown.lines.size() * 4000; shown.lines.add(line);
            }
        }
        guitarStyleKey = prefs.guitarSize() + "|" + prefs.font() + "|" + prefs.transpose() + "|" + prefs.capo() + "|" + prefs.flats() + "|" + prefs.simplify();
        guitar.setSheet(shown, storedSheet == null);
    }
    @Override public void onConfigurationChanged(android.content.res.Configuration config) {
        super.onConfigurationChanged(config); applySettings(); updateVisibility(); restartLoop();
    }
    private void updateVisibility() {
        if (panel == null) return;
        visible = editing || test || !prefs.autoHide() || SongState.get().playing;
        panel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }
    private void restartLoop() {
        handler.removeCallbacks(tick);
        if (visible && (test || SongState.get().playing)) handler.post(tick);
    }
    private void render() {
        if (panel == null) return;
        SongState.Snapshot s = SongState.get();
        if (prefs.guitar()) {
            for (TextView v : views) v.setVisibility(View.GONE);
            status.setVisibility(View.GONE);
            guitar.update(test ? (android.os.SystemClock.elapsedRealtime() - testStart) % 25000 : s.getCurrentPositionMs(), test || s.playing, lyricStatus);
            return;
        }
        status.setVisibility(View.VISIBLE);
        int index = parser == null ? -1 : parser.indexForPosition(test ? (android.os.SystemClock.elapsedRealtime() - testStart) % 25000 : s.getCurrentPositionMs(), prefs.syncOffsetMs());
        boolean changed = index != lastIndex;
        if (parser != null) {
            List<LrcParser.Line> lines = parser.lines();
            for (int i = 0; i < 5; i++) {
                int n = index + i - 2;
                views[i].setText(n >= 0 && n < lines.size() ? lines.get(n).text : "");
                views[i].setVisibility(i == 2 || prefs.showContext() ? View.VISIBLE : View.GONE);
            }
            status.setText(!test && !s.playing ? "Paused" : (index < 0 ? "Waiting for lyrics..." : ""));
            if (changed && index >= 0) {
                views[2].animate().cancel(); views[2].setAlpha(0f); views[2].setTranslationY(dp(6));
                views[2].animate().alpha(1f).translationY(0).setDuration(200).start();
            }
        } else if (!plain.isEmpty()) {
            // Plain lyrics are deliberately static, with the complete text visible rather than pretending to sync.
            for (int i = 0; i < 5; i++) { views[i].setVisibility(i == 2 ? View.VISIBLE : View.GONE); views[i].setText(""); }
            views[2].setMaxLines(Integer.MAX_VALUE);
            StringBuilder all = new StringBuilder();
            for (String line : plain) { if (all.length() > 0) all.append('\n'); all.append(line); }
            views[2].setText(all.toString());
            status.setText("No synced lyrics for this song." + (!s.playing ? " Paused" : ""));
        } else {
            for (TextView v : views) { v.setText(""); v.setVisibility(View.GONE); }
            status.setText(!s.playing && !test && !s.title.isEmpty() ? lyricStatus + " · Paused" : lyricStatus);
        }
        if (parser != null) views[2].setMaxLines(2);
        lastIndex = index;
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (panel == null) return START_NOT_STICKY;
        if (intent != null && ACTION_SETTINGS.equals(intent.getAction())) {
            boolean newEdit = intent.getBooleanExtra("edit", editing);
            if (editing && !newEdit) prefs.position(params.x, params.y);
            editing = newEdit; applySettings(); updateVisibility(); restartLoop();
        } else if (intent != null && ACTION_CHORDS.equals(intent.getAction())) {
            if (!test) { SongState.Snapshot s = SongState.get(); storedSheet = chordStore.load(s.title, s.artist); refreshGuitar(); render(); }
        } else if (intent != null && ACTION_TEST.equals(intent.getAction()) && test) {
            test = false; testing = false; songKey = ""; songChanged();
        } else if (intent != null && (ACTION_TEST.equals(intent.getAction()) || ACTION_TEST_GUITAR.equals(intent.getAction()))) {
            test = true; testing = true; ++generation; plain.clear();
            parser = new LrcParser("[00:00.00]This is LyricFloat\n[00:05.00]Drag me into place\n[00:10.00]Your music keeps playing\n[00:15.00]Lyrics follow along\n[00:20.00]Enjoy the song");
            storedSheet = null;
            if (ACTION_TEST_GUITAR.equals(intent.getAction())) {
                storedSheet = ChordSheet.fromLyrics(parser.lines());
                String[] chords = {"C", "G", "Am", "F", "C"};
                for (int i = 0; i < storedSheet.lines.size() && i < chords.length; i++) {
                    storedSheet.lines.get(i).chords.add(new ChordSheet.ChordPlacement(chords[i], 0, -1));
                }
                storedSheet.lines.get(0).section = "Verse";
            }
            refreshGuitar(); testStart = android.os.SystemClock.elapsedRealtime(); lastIndex = Integer.MIN_VALUE;
            setStatus("Test lyrics"); updateVisibility(); restartLoop();
        } else if (test) {
            test = false; testing = false; songKey = ""; songChanged();
        }
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() {
        if (editing && params != null && prefs != null) prefs.position(params.x, params.y);
        running = false; editing = false; testing = false; generation++; handler.removeCallbacks(tick);
        if (fetcher != null) fetcher.shutdown();
        try { unregisterReceiver(receiver); } catch (Exception ignored) { }
        if (wm != null && panel != null) { try { wm.removeView(panel); } catch (RuntimeException ignored) { } }
        lyricStatus = "Overlay stopped"; SongState.notifyChanged(this); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
