package com.heavens.lyricfloat;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private Prefs prefs;
    private TextView notificationStatus, overlayStatus, postStatus, now;
    private Button start;
    private Switch move;
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refresh(); }
    };
    private int dp(int n) { return (int) (n * getResources().getDisplayMetrics().density + .5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); prefs = new Prefs(this);
        ScrollView scroll = new ScrollView(this); LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(20), dp(20), dp(20), dp(30));
        scroll.addView(root); setContentView(scroll);
        TextView title = text("LyricFloat", 28); title.setTypeface(null, Typeface.BOLD); root.addView(title);
        root.addView(text("Live lyrics over your music", 15));
        LinearLayout permissions = card(root, "Permissions");
        notificationStatus = text("", 14); permissions.addView(notificationStatus);
        button(permissions, "Notification access", new Runnable() { public void run() { open(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)); } });
        overlayStatus = text("", 14); permissions.addView(overlayStatus);
        button(permissions, "Draw over other apps", new Runnable() { public void run() { open(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()))); } });
        postStatus = text("", 14); permissions.addView(postStatus);
        button(permissions, "Allow notifications (Android 13+)", new Runnable() { public void run() {
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            else Toast.makeText(MainActivity.this, "Not required on this Android version", Toast.LENGTH_SHORT).show();
        } });
        LinearLayout playback = card(root, "Now playing"); now = text("", 15); playback.addView(now);
        start = button(root, "Start overlay", new Runnable() { public void run() {
            if (OverlayService.running) { stopService(new Intent(MainActivity.this, OverlayService.class)); OverlayService.editing = false; refresh(); }
            else startOverlay(null);
        } });
        button(root, "Test / exit test overlay", new Runnable() { public void run() { startOverlay(OverlayService.ACTION_TEST); } });
        LinearLayout look = card(root, "Appearance and timing");
        seek(look, "Overlay opacity", "overlayAlpha", 20, 100, 1, prefs.overlayAlpha(), "%");
        seek(look, "Text size", "textSizeSp", 12, 32, 1, prefs.textSizeSp(), " sp");
        seek(look, "Panel opacity", "panelAlpha", 0, 100, 1, prefs.panelAlpha(), "%");
        seek(look, "Sync offset (positive advances lyrics)", "syncOffsetMs", -3000, 3000, 50, prefs.syncOffsetMs(), " ms");
        toggle(look, "Show previous/next lines", prefs.showContext(), new Toggle() { public void changed(boolean value) { prefs.putBoolean("showContext", value); settings(); } });
        move = toggle(look, "Move overlay", OverlayService.editing, new Toggle() { public void changed(boolean value) {
            if (!OverlayService.running) {
                if (value) { Toast.makeText(MainActivity.this, "Start the overlay first", Toast.LENGTH_SHORT).show(); move.setChecked(false); }
                return;
            }
            Intent i = new Intent(MainActivity.this, OverlayService.class).setAction(OverlayService.ACTION_SETTINGS).putExtra("edit", value);
            startService(i);
        } });
        toggle(look, "Auto-hide when nothing is playing", prefs.autoHide(), new Toggle() { public void changed(boolean value) { prefs.putBoolean("autoHide", value); settings(); } });
        IntentFilter filter = new IntentFilter(SongState.ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }
    private interface Toggle { void changed(boolean value); }
    private Switch toggle(LinearLayout parent, String label, boolean checked, final Toggle action) {
        Switch s = new Switch(this); s.setText(label); s.setPadding(0, dp(10), 0, dp(10)); s.setChecked(checked);
        s.setOnCheckedChangeListener(new android.widget.CompoundButton.OnCheckedChangeListener() {
            @Override public void onCheckedChanged(android.widget.CompoundButton b, boolean checked) { action.changed(checked); }
        }); parent.addView(s); return s;
    }
    private void seek(LinearLayout parent, final String label, final String key, final int min, int max, final int step, int value, final String unit) {
        final TextView caption = text(label + ": " + value + unit, 14); parent.addView(caption);
        SeekBar bar = new SeekBar(this); bar.setMax((max - min) / step); bar.setProgress((value - min) / step); parent.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int progress, boolean user) {
                int v = min + progress * step; caption.setText(label + ": " + v + unit);
                if (user) { prefs.putInt(key, v); settings(); }
            }
            public void onStartTrackingTouch(SeekBar b) { }
            public void onStopTrackingTouch(SeekBar b) { }
        });
    }
    private void settings() {
        if (OverlayService.running) startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_SETTINGS));
    }
    private void startOverlay(String action) {
        if (!Settings.canDrawOverlays(this)) { Toast.makeText(this, "Grant draw over other apps first", Toast.LENGTH_LONG).show(); return; }
        try {
            Intent i = new Intent(this, OverlayService.class); if (action != null) i.setAction(action);
            if (OverlayService.running) startService(i); else startForegroundService(i);
        } catch (RuntimeException e) { Toast.makeText(this, "Cannot start overlay: " + e.getMessage(), Toast.LENGTH_LONG).show(); }
    }
    private TextView text(String s, int size) { TextView t = new TextView(this); t.setText(s); t.setTextSize(size); return t; }
    private LinearLayout card(LinearLayout root, String heading) {
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(12), dp(12), dp(12), dp(12));
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable(); bg.setColor(Color.rgb(45, 45, 50)); bg.setCornerRadius(dp(12)); box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(14), 0, dp(4)); root.addView(box, lp);
        TextView h = text(heading, 19); h.setTypeface(null, Typeface.BOLD); box.addView(h); return box;
    }
    private Button button(LinearLayout parent, String label, final Runnable action) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } }); parent.addView(b); return b;
    }
    private void open(Intent i) { try { startActivity(i); } catch (RuntimeException e) { Toast.makeText(this, "Settings screen unavailable", Toast.LENGTH_SHORT).show(); } }
    private boolean listenerEnabled() {
        String enabled = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (enabled == null) return false;
        ComponentName target = new ComponentName(this, MediaListenerService.class);
        for (String part : enabled.split(":")) { ComponentName c = ComponentName.unflattenFromString(part); if (target.equals(c)) return true; }
        return false;
    }
    private void refresh() {
        notificationStatus.setText("Notification access: " + (listenerEnabled() ? "Granted" : "Not granted"));
        overlayStatus.setText("Overlay permission: " + (Settings.canDrawOverlays(this) ? "Granted" : "Not granted"));
        postStatus.setText("Post notifications: " + (Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ? "Granted / not required" : "Not granted"));
        SongState.Snapshot s = SongState.get();
        now.setText((s.title.isEmpty() ? "Nothing detected" : s.title + (s.artist.isEmpty() ? "" : "\n" + s.artist)) + "\n" + OverlayService.lyricStatus + (s.playing ? " · Playing" : " · Paused"));
        start.setText(OverlayService.running ? "Stop overlay" : "Start overlay");
        if (move != null && move.isChecked() != OverlayService.editing) move.setChecked(OverlayService.editing);
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) { super.onRequestPermissionsResult(requestCode, permissions, grants); refresh(); }
    @Override protected void onDestroy() { try { unregisterReceiver(receiver); } catch (Exception ignored) { } super.onDestroy(); }
}
