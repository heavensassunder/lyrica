package com.heavens.lyricfloat;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Import, record and edit a sheet for the song captured when this screen opens. */
public class ChordActivity extends Activity {
    public static final String IMPORT = "import", RECORD = "record", EDIT = "edit";
    private String title, artist, mode, initialText;
    private long duration;
    private Prefs prefs;
    private ChordStore store;
    private ChordSheet sheet;
    private List<LrcParser.Line> lyrics = new ArrayList<LrcParser.Line>();
    private LyricsFetcher fetcher;
    private boolean loading, alive, hadSheet;
    private LinearLayout root;
    private EditText input;
    private LinearLayout recordedList;
    private final Set<String> available = new LinkedHashSet<String>();
    private int dp(int n) { return (int) (getResources().getDisplayMetrics().density * n + .5f); }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); alive = true; prefs = new Prefs(this); store = new ChordStore(this);
        SongState.Snapshot song = SongState.get(); title = song.title; artist = song.artist; duration = song.duration;
        Intent intent = getIntent(); mode = intent == null ? IMPORT : intent.getStringExtra("mode");
        if (mode == null) mode = IMPORT;
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())) { mode = IMPORT; CharSequence shared = intent.getCharSequenceExtra(Intent.EXTRA_TEXT); initialText = shared == null ? null : shared.toString(); }
        sheet = store.load(title, artist); hadSheet = sheet != null;
        if (sheet == null) sheet = new ChordSheet();
        collectChords();
        if (!title.trim().isEmpty()) {
            loading = true; fetcher = new LyricsFetcher(this);
            fetcher.fetch(title, artist, duration, new LyricsFetcher.Callback() {
                @Override public void onResult(LyricsFetcher.Result result) {
                    if (!alive) return; loading = false;
                    if (result != null && result.synced) lyrics = new LrcParser(result.text).lines();
                    if (!hadSheet && sheet.lines.isEmpty() && !lyrics.isEmpty()) sheet = ChordSheet.fromLyrics(lyrics);
                    // Do not rebuild the import form while the user is typing.
                    if (!IMPORT.equals(mode)) render();
                }
            });
        }
        render();
    }
    private void collectChords() { for (ChordSheet.ChordLine line : sheet.lines) for (ChordSheet.ChordPlacement c : line.chords) available.add(c.name); }
    private TextView text(String value, int size) { TextView v = new TextView(this); v.setText(value); v.setTextSize(size); return v; }
    private Button button(LinearLayout parent, String value, final Runnable action) {
        Button b = new Button(this); b.setText(value); b.setAllCaps(false); b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { action.run(); } }); parent.addView(b); return b;
    }
    private void message(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }
    private boolean validSong() { if (title == null || title.trim().isEmpty()) { message("Play a song first, then reopen this screen."); return false; } return true; }
    private boolean save() {
        if (!validSong()) return false;
        if (!store.save(title, artist, sheet)) { message("Could not save chord sheet"); return false; }
        hadSheet = true;
        if (OverlayService.running) startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_CHORDS));
        return true;
    }
    private void render() {
        ScrollView scroll = new ScrollView(this); root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root); setContentView(scroll);
        root.addView(text(IMPORT.equals(mode) ? "Import chord sheet" : RECORD.equals(mode) ? "Record chords" : "Edit chord sheet", 24));
        root.addView(text(title.isEmpty() ? "No song detected" : title + (artist.isEmpty() ? "" : " — " + artist), 16));
        if (IMPORT.equals(mode)) importScreen(); else if (RECORD.equals(mode)) recordScreen(); else editScreen();
        button(root, "Back", new Runnable() { public void run() { finish(); } });
    }
    private void importScreen() {
        root.addView(text("Paste chords above lyrics, or ChordPro [Am]inline chords.", 14));
        input = new EditText(this); input.setGravity(android.view.Gravity.TOP); input.setMinLines(10);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        if (initialText != null) input.setText(initialText); root.addView(input, new LinearLayout.LayoutParams(-1, dp(260)));
        button(root, "Paste from clipboard", new Runnable() { public void run() {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            try {
                ClipData clip = clipboard == null ? null : clipboard.getPrimaryClip();
                CharSequence value = clip == null || clip.getItemCount() == 0 ? null : clip.getItemAt(0).coerceToText(ChordActivity.this);
                if (value != null) input.setText(value); else message("Clipboard is empty");
            } catch (RuntimeException e) { message("Cannot read clipboard"); }
        } });
        button(root, "Preview alignment", new Runnable() { public void run() {
            if (!validSong()) return;
            if (loading) { message("Still loading lyrics; please try again shortly."); return; }
            final ChordSheet imported = ChordImporter.parse(input.getText().toString(), lyrics);
            if (imported.lines.isEmpty()) { message("Paste a chord sheet first"); return; }
            String description = "Matched " + imported.matched + " of " + ChordImporter.lyricCount(imported) + " lyric lines.";
            if (lyrics.isEmpty()) description += "\nNo synced lyrics available: times are estimates.";
            else description += "\nUnmatched lines use interpolated times. Saving replaces this song's existing sheet.";
            String preview = imported.toPlainText(); if (preview.length() > 1600) preview = preview.substring(0, 1600) + "\n…";
            new AlertDialog.Builder(ChordActivity.this).setTitle("Preview alignment").setMessage(description + "\n\n" + preview)
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                        sheet = imported; if (save()) { message("Chord sheet saved"); finish(); }
                    } }).setNegativeButton("Cancel", null).show();
        } });
    }
    private interface Chosen { void selected(String chord); }
    private void pickChord(final Chosen chosen) {
        final String[] roots = {"C", "C#", "Db", "D", "D#", "Eb", "E", "F", "F#", "Gb", "G", "G#", "Ab", "A", "A#", "Bb", "B"};
        final String[] qualities = {"Major", "Minor", "7", "maj7", "m7", "sus2", "sus4", "add9", "dim", "aug"};
        final String[] suffix = {"", "m", "7", "maj7", "m7", "sus2", "sus4", "add9", "dim", "aug"};
        new AlertDialog.Builder(this).setTitle("Chord root").setItems(roots, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, final int root) {
                new AlertDialog.Builder(ChordActivity.this).setTitle("Chord quality").setItems(qualities, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) { chosen.selected(roots[root] + suffix[which]); }
                }).show();
            }
        }).show();
    }
    private void recordScreen() {
        root.addView(text("Tap chords while music is playing. Times are saved immediately for the song above.", 14));
        final TextView delay = text("Reaction delay: " + prefs.reactionDelay() + " ms", 14); root.addView(delay);
        SeekBar bar = new SeekBar(this); bar.setMax(1000); bar.setProgress(prefs.reactionDelay()); root.addView(bar);
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int v, boolean user) { delay.setText("Reaction delay: " + v + " ms"); if (user) prefs.putInt("reactionDelay", v); }
            public void onStartTrackingTouch(SeekBar b) { } public void onStopTrackingTouch(SeekBar b) { }
        });
        android.widget.HorizontalScrollView chordScroll = new android.widget.HorizontalScrollView(this);
        LinearLayout chordButtons = new LinearLayout(this); chordButtons.setOrientation(LinearLayout.HORIZONTAL); chordScroll.addView(chordButtons); root.addView(chordScroll);
        for (final String chord : available) button(chordButtons, chord, new Runnable() { public void run() { record(chord); } });
        button(root, "Add chord", new Runnable() { public void run() { pickChord(new Chosen() { public void selected(String chord) { available.add(chord); render(); } }); } });
        root.addView(text("Recorded changes", 19));
        recordedList = new LinearLayout(this); recordedList.setOrientation(LinearLayout.VERTICAL); root.addView(recordedList);
        rebuildRecorded();
    }
    /** Only the recorded list is rebuilt, so repeated taps keep the scroll position and stay fast. */
    private void rebuildRecorded() {
        if (recordedList == null) return;
        recordedList.removeAllViews(); int count = 0;
        for (final ChordSheet.ChordLine line : sheet.lines) for (final ChordSheet.ChordPlacement c : new ArrayList<ChordSheet.ChordPlacement>(line.chords)) {
            if (c.timeMs < 0) continue; count++;
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); recordedList.addView(row);
            TextView name = text(c.name + "  " + String.format(Locale.ROOT, "%.2fs", c.timeMs / 1000.0), 15);
            row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            button(row, "−50", new Runnable() { public void run() { c.timeMs = Math.max(0, c.timeMs - 50); if (save()) rebuildRecorded(); } });
            button(row, "+50", new Runnable() { public void run() { c.timeMs += 50; if (save()) rebuildRecorded(); } });
            button(row, "×", new Runnable() { public void run() { line.chords.remove(c); if (save()) rebuildRecorded(); } });
        }
        if (count == 0) recordedList.addView(text("No recorded changes yet.", 14));
    }
    private void record(String chord) {
        if (!validSong()) return;
        SongState.Snapshot song = SongState.get();
        if (!title.equals(song.title) || !artist.equals(song.artist)) { message("The song changed. Reopen Record chords."); return; }
        if (!song.playing) { message("Play the song before recording."); return; }
        long time = Math.max(0, song.getCurrentPositionMs() - prefs.reactionDelay());
        if (sheet.lines.isEmpty()) { ChordSheet.ChordLine line = new ChordSheet.ChordLine(); line.timeMs = 0; sheet.lines.add(line); }
        int index = 0;
        for (int i = 0; i < sheet.lines.size(); i++) { if (sheet.lines.get(i).timeMs <= time) index = i; else break; }
        ChordSheet.ChordLine line = sheet.lines.get(index);
        long end = index + 1 < sheet.lines.size() ? sheet.lines.get(index + 1).timeMs : Math.max(time + 1, line.timeMs + 4000);
        int offset = (int) (Math.max(0, Math.min(end - line.timeMs, time - line.timeMs)) * line.text.length() / Math.max(1, end - line.timeMs));
        line.chords.add(new ChordSheet.ChordPlacement(chord, offset, time));
        if (save()) { rebuildRecorded(); message(chord + " at " + String.format(Locale.ROOT, "%.1fs", time / 1000.0)); }
    }
    private void editScreen() {
        if (loading && sheet.lines.isEmpty()) root.addView(text("Loading lyrics…", 14));
        if (sheet.lines.isEmpty()) root.addView(text("No synced lyrics yet. Import a sheet first.", 14));
        for (int i = 0; i < sheet.lines.size(); i++) {
            final ChordSheet.ChordLine line = sheet.lines.get(i);
            String label = (line.section.isEmpty() ? "" : "[" + line.section + "] ") + (line.text.isEmpty() ? "(instrumental)" : line.text) + "  (" + line.chords.size() + " chords)";
            button(root, label, new Runnable() { public void run() { editLine(line); } });
        }
    }
    private void editLine(final ChordSheet.ChordLine line) {
        final LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), 0, dp(16), 0);
        box.addView(text("Tap a position in the text, then add a chord.", 14));
        final EditText words = new EditText(this); words.setText(line.text); box.addView(words);
        final LinearLayout chordList = new LinearLayout(this); chordList.setOrientation(LinearLayout.VERTICAL); box.addView(chordList);
        final ChordSheet backup;
        try { backup = ChordSheet.fromJson(sheet.toJson()); } catch (Exception e) { message("Cannot open editor"); return; }
        final Runnable[] updateList = new Runnable[1];
        updateList[0] = new Runnable() { public void run() {
            chordList.removeAllViews();
            for (final ChordSheet.ChordPlacement c : new ArrayList<ChordSheet.ChordPlacement>(line.chords)) {
                button(chordList, c.name + " at character " + c.offset + (c.timeMs >= 0 ? " (recorded)" : ""), new Runnable() {
                    public void run() { editChord(line, c, words, updateList[0]); }
                });
            }
        } };
        button(box, "Add chord at cursor", new Runnable() { public void run() {
            final int offset = Math.max(0, words.getSelectionStart());
            pickChord(new Chosen() { public void selected(String chord) { line.chords.add(new ChordSheet.ChordPlacement(chord, offset, -1)); updateList[0].run(); } });
        } });
        updateList[0].run();
        new AlertDialog.Builder(this).setTitle("Edit lyric line").setView(box).setPositiveButton("Save", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { line.text = words.getText().toString(); if (save()) render(); }
        }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) { sheet = backup; render(); }
        }).show();
    }
    private void editChord(final ChordSheet.ChordLine line, final ChordSheet.ChordPlacement chord, final EditText words, final Runnable refresh) {
        final LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(16), 0, dp(16), 0);
        final EditText name = new EditText(this); name.setSingleLine(true); name.setText(chord.name); name.setHint("Chord, e.g. F#m7 or C/G"); box.addView(name);
        final EditText offset = new EditText(this); offset.setInputType(InputType.TYPE_CLASS_NUMBER); offset.setText(Integer.toString(chord.offset)); offset.setHint("Character offset"); box.addView(offset);
        new AlertDialog.Builder(this).setTitle("Edit / move chord").setView(box)
                .setPositiveButton("Apply", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) {
                    String n = name.getText().toString().trim(); if (!ChordNames.valid(n)) { message("Invalid chord"); return; }
                    try { int newOffset = Math.max(0, Integer.parseInt(offset.getText().toString())); chord.name = n; if (newOffset != chord.offset) chord.timeMs = -1; chord.offset = newOffset; refresh.run(); }
                    catch (NumberFormatException e) { message("Invalid character offset"); }
                } }).setNeutralButton("Use cursor", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) { chord.offset = Math.max(0, words.getSelectionStart()); chord.timeMs = -1; refresh.run(); } })
                .setNegativeButton("Delete", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) { line.chords.remove(chord); refresh.run(); } }).show();
    }
    @Override protected void onDestroy() { alive = false; if (fetcher != null) fetcher.shutdown(); super.onDestroy(); }
}
