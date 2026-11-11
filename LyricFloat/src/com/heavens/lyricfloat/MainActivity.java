package com.heavens.lyricfloat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
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
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import org.json.JSONObject;
import java.util.Iterator;
import java.util.ArrayList;
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
    private Switch move, guitarMode;
    private TextView chordSong;
    private final Button[] swatches = new Button[Prefs.COLOR_KEYS.length];
    private Spinner fontSpinner;
    private SeekBar outlineBar;
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
        modeAndThemes(root);
        IntentFilter filter = new IntentFilter(SongState.ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(receiver, filter);
    }
    private void modeAndThemes(LinearLayout root) {
        LinearLayout mode = card(root, "Mode");
        guitarMode = toggle(mode, "Guitar mode (off = lyrics mode)", prefs.guitar(), new Toggle() {
            public void changed(boolean value) { prefs.putBoolean("guitar", value); settings(); }
        });
        button(mode, "Test / exit test guitar mode", new Runnable() { public void run() {
            guitarMode.setChecked(true);
            startOverlay(OverlayService.testing ? OverlayService.ACTION_TEST : OverlayService.ACTION_TEST_GUITAR);
        } });
        LinearLayout theme = card(root, "Theme / colors");
        for (int i = 0; i < Prefs.COLOR_KEYS.length; i++) {
            final int index = i;
            swatches[i] = button(theme, Prefs.COLOR_LABELS[i], new Runnable() { public void run() {
                new ColorPickerDialog(MainActivity.this, prefs.color(index), new ColorPickerDialog.Listener() {
                    public void changed(int color) { prefs.setColor(index, color); refreshSwatches(); settings(); }
                }).show(Prefs.COLOR_LABELS[index]);
            } });
        }
        refreshSwatches();
        theme.addView(text("Font", 14));
        fontSpinner = new Spinner(this);
        ArrayAdapter<String> fonts = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, new String[]{"Sans", "Serif", "Monospace", "Bold-Italic"});
        fonts.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item); fontSpinner.setAdapter(fonts); fontSpinner.setSelection(prefs.font());
        fontSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) { prefs.putInt("font", position); settings(); }
            public void onNothingSelected(AdapterView<?> parent) { }
        }); theme.addView(fontSpinner);
        final TextView outlineLabel = text("Outline thickness: " + prefs.outline(), 14); theme.addView(outlineLabel);
        outlineBar = new SeekBar(this); outlineBar.setMax(8); outlineBar.setProgress(prefs.outline()); theme.addView(outlineBar);
        outlineBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) { outlineLabel.setText("Outline thickness: " + value); if (user) { prefs.putInt("outline", value); settings(); } }
            public void onStartTrackingTouch(SeekBar b) { } public void onStopTrackingTouch(SeekBar b) { }
        });
        theme.addView(text("Presets", 16));
        for (int i = 0; i < Prefs.PRESET_NAMES.length; i++) {
            final int index = i;
            Button preset = button(theme, Prefs.PRESET_NAMES[i], new Runnable() { public void run() { prefs.preset(index); refreshSwatches(); settings(); } });
            setPresetSwatch(preset, i);
        }
        button(theme, "Save current as theme", new Runnable() { public void run() {
            final EditText name = new EditText(MainActivity.this); name.setSingleLine(true); name.setHint("Theme name");
            new AlertDialog.Builder(MainActivity.this).setTitle("Save current theme").setView(name)
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                        if (!prefs.saveTheme(name.getText().toString())) Toast.makeText(MainActivity.this, "Enter a theme name", Toast.LENGTH_SHORT).show();
                    } }).setNegativeButton("Cancel", null).show();
        } });
        button(theme, "Saved themes", new Runnable() { public void run() { savedThemes(); } });
        LinearLayout guitar = card(root, "Guitar settings");
        seek(guitar, "Guitar overlay height", "guitarHeight", 50, 90, 1, prefs.guitarHeight(), "%");
        seek(guitar, "Guitar text size", "guitarSize", 12, 32, 1, prefs.guitarSize(), " sp");
        stepper(guitar, "Transpose", "transpose", -11, 11, prefs.transpose(), " semitones");
        stepper(guitar, "Capo", "capo", 0, 12, prefs.capo(), " fret");
        seek(guitar, "Chord timing offset (positive advances)", "chordOffset", -2000, 2000, 50, prefs.chordOffset(), " ms");
        seek(guitar, "Diagram size", "diagramSize", 40, 100, 2, prefs.diagramSize(), " dp");
        toggle(guitar, "Prefer flats (off = sharps)", prefs.flats(), new Toggle() { public void changed(boolean v) { prefs.putBoolean("flats", v); settings(); } });
        toggle(guitar, "Simplify chords", prefs.simplify(), new Toggle() { public void changed(boolean v) { prefs.putBoolean("simplify", v); settings(); } });
        toggle(guitar, "Show chord diagrams", prefs.diagrams(), new Toggle() { public void changed(boolean v) { prefs.putBoolean("diagrams", v); settings(); } });
        toggle(guitar, "Show next chord diagram", prefs.nextDiagram(), new Toggle() { public void changed(boolean v) { prefs.putBoolean("nextDiagram", v); settings(); } });
        LinearLayout chords = card(root, "Chords"); chordSong = text("", 14); chords.addView(chordSong);
        button(chords, "Import chord sheet", new Runnable() { public void run() { chordScreen(ChordActivity.IMPORT); } });
        button(chords, "Record chords", new Runnable() { public void run() { chordScreen(ChordActivity.RECORD); } });
        button(chords, "Edit chord sheet", new Runnable() { public void run() { chordScreen(ChordActivity.EDIT); } });
        button(chords, "Export / share chord sheet", new Runnable() { public void run() {
            SongState.Snapshot song = SongState.get(); ChordSheet sheet = new ChordStore(MainActivity.this).load(song.title, song.artist);
            if (sheet == null) { Toast.makeText(MainActivity.this, "No chord sheet for this song", Toast.LENGTH_LONG).show(); return; }
            Intent send = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, song.title).putExtra(Intent.EXTRA_TEXT, sheet.toPlainText());
            try { startActivity(Intent.createChooser(send, "Share chord sheet")); } catch (RuntimeException e) { Toast.makeText(MainActivity.this, "No sharing app available", Toast.LENGTH_SHORT).show(); }
        } });
    }
    private void chordScreen(String mode) { startActivity(new Intent(this, ChordActivity.class).putExtra("mode", mode)); }
    private void setSwatch(Button button, int color) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(); d.setColor(color); d.setStroke(dp(1), Color.LTGRAY); d.setCornerRadius(dp(3)); d.setBounds(0, 0, dp(22), dp(22));
        button.setCompoundDrawablePadding(dp(8)); button.setCompoundDrawables(d, null, null, null);
    }
    private void refreshSwatches() { for (int i = 0; i < swatches.length; i++) if (swatches[i] != null) setSwatch(swatches[i], prefs.color(i)); }
    private void savedThemes() {
        final JSONObject all = prefs.themes(); final ArrayList<String> names = new ArrayList<String>();
        Iterator<String> keys = all.keys(); while (keys.hasNext()) names.add(keys.next());
        if (names.isEmpty()) { Toast.makeText(this, "No saved themes yet", Toast.LENGTH_SHORT).show(); return; }
        new AlertDialog.Builder(this).setTitle("Saved themes").setItems(names.toArray(new String[names.size()]), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                final String name = names.get(which);
                new AlertDialog.Builder(MainActivity.this).setTitle(name).setItems(new String[]{"Apply", "Delete"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int action) {
                        if (action == 0) { prefs.applyTheme(name); refreshSwatches(); fontSpinner.setSelection(prefs.font()); outlineBar.setProgress(prefs.outline()); settings(); }
                        else prefs.deleteTheme(name);
                    }
                }).show();
            }
        }).show();
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
    /** Explicit -/+ control, as requested for transpose and capo. */
    private void stepper(LinearLayout parent, final String label, final String key, final int min, final int max, int value, final String unit) {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        final TextView caption = text(label + ": " + value + unit, 14);
        row.addView(caption, new LinearLayout.LayoutParams(0, -2, 1));
        final int[] current = {value};
        Button minus = new Button(this); minus.setText("\u2212"); row.addView(minus);
        Button plus = new Button(this); plus.setText("+"); row.addView(plus);
        minus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            current[0] = Math.max(min, current[0] - 1); prefs.putInt(key, current[0]); caption.setText(label + ": " + current[0] + unit); settings();
        } });
        plus.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            current[0] = Math.min(max, current[0] + 1); prefs.putInt(key, current[0]); caption.setText(label + ": " + current[0] + unit); settings();
        } });
        parent.addView(row);
    }
    private void setPresetSwatch(Button button, int preset) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{prefs.presetColor(preset, 0), prefs.presetColor(preset, 4), prefs.presetColor(preset, 6)});
        d.setStroke(dp(1), Color.LTGRAY); d.setCornerRadius(dp(3)); d.setBounds(0, 0, dp(34), dp(18));
        button.setCompoundDrawablePadding(dp(8)); button.setCompoundDrawables(d, null, null, null);
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
        if (chordSong != null) chordSong.setText("Current song: " + (s.title.isEmpty() ? "Nothing detected" : s.title + (s.artist.isEmpty() ? "" : " — " + s.artist)));
        now.setText((s.title.isEmpty() ? "Nothing detected" : s.title + (s.artist.isEmpty() ? "" : "\n" + s.artist)) + "\n" + OverlayService.lyricStatus + (s.playing ? " · Playing" : " · Paused"));
        start.setText(OverlayService.running ? "Stop overlay" : "Start overlay");
        if (move != null && move.isChecked() != OverlayService.editing) move.setChecked(OverlayService.editing);
    }
    @Override protected void onResume() { super.onResume(); refresh(); }
    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grants) { super.onRequestPermissionsResult(requestCode, permissions, grants); refresh(); }
    @Override protected void onDestroy() { try { unregisterReceiver(receiver); } catch (Exception ignored) { } super.onDestroy(); }
}
