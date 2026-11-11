package com.heavens.lyricfloat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** Chord names are stored as concert chords; -1 means derive timing from text position. */
public final class ChordSheet {
    public static final class ChordPlacement {
        public String name;
        public int offset;
        public long timeMs;
        public ChordPlacement(String name, int offset, long timeMs) {
            this.name = name == null ? "" : name; this.offset = Math.max(0, offset); this.timeMs = timeMs;
        }
    }
    public static final class ChordLine {
        public String text = "", section = "";
        public long timeMs;
        public final List<ChordPlacement> chords = new ArrayList<ChordPlacement>();
    }
    public static final class Event {
        public final long timeMs;
        public final String name;
        public Event(long timeMs, String name) { this.timeMs = timeMs; this.name = name; }
    }
    public final List<ChordLine> lines = new ArrayList<ChordLine>();
    public boolean aligned;
    public int matched;
    public static ChordSheet fromLyrics(List<LrcParser.Line> lyrics) {
        ChordSheet sheet = new ChordSheet();
        if (lyrics != null) for (LrcParser.Line lyric : lyrics) {
            ChordLine line = new ChordLine(); line.text = lyric.text; line.timeMs = lyric.timeMs; sheet.lines.add(line);
        }
        sheet.aligned = lyrics != null && !lyrics.isEmpty(); return sheet;
    }
    public List<Event> events() {
        List<Event> events = new ArrayList<Event>();
        for (int i = 0; i < lines.size(); i++) {
            ChordLine line = lines.get(i);
            long end = i + 1 < lines.size() ? lines.get(i + 1).timeMs : line.timeMs + 4000;
            long duration = Math.max(0, end - line.timeMs);
            for (int index = 0; index < line.chords.size(); index++) {
                ChordPlacement chord = line.chords.get(index);
                long t;
                if (chord.timeMs >= 0) t = chord.timeMs;
                else if (line.text.isEmpty()) t = line.timeMs + duration * index / Math.max(1, line.chords.size());
                else t = line.timeMs + duration * Math.min(chord.offset, line.text.length()) / Math.max(1, line.text.length());
                if (!chord.name.isEmpty()) events.add(new Event(Math.max(0, t), chord.name));
            }
        }
        Collections.sort(events, new Comparator<Event>() { public int compare(Event a, Event b) { return Long.compare(a.timeMs, b.timeMs); } });
        return events;
    }
    public JSONObject toJson() throws Exception {
        JSONObject root = new JSONObject(); root.put("version", 1); root.put("aligned", aligned); root.put("matched", matched);
        JSONArray rows = new JSONArray();
        for (ChordLine line : lines) {
            JSONObject row = new JSONObject(); row.put("text", line.text); row.put("section", line.section); row.put("time", line.timeMs);
            JSONArray chords = new JSONArray();
            for (ChordPlacement c : line.chords) {
                JSONObject item = new JSONObject(); item.put("name", c.name); item.put("offset", c.offset); item.put("time", c.timeMs); chords.put(item);
            }
            row.put("chords", chords); rows.put(row);
        }
        root.put("lines", rows); return root;
    }
    public static ChordSheet fromJson(JSONObject root) throws Exception {
        ChordSheet sheet = new ChordSheet(); sheet.aligned = root.optBoolean("aligned", false); sheet.matched = root.optInt("matched", 0);
        JSONArray rows = root.optJSONArray("lines");
        if (rows == null || rows.length() > 10000) return sheet;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.optJSONObject(i); if (row == null) continue;
            ChordLine line = new ChordLine(); line.text = row.optString("text", ""); line.section = row.optString("section", ""); line.timeMs = Math.max(0, row.optLong("time", 0));
            JSONArray chords = row.optJSONArray("chords");
            if (chords != null) for (int j = 0; j < chords.length() && j < 1000; j++) {
                JSONObject c = chords.optJSONObject(j);
                if (c != null && ChordNames.valid(c.optString("name", ""))) line.chords.add(new ChordPlacement(c.optString("name"), c.optInt("offset", 0), c.optLong("time", -1)));
            }
            sheet.lines.add(line);
        }
        return sheet;
    }
    public String toPlainText() {
        StringBuilder out = new StringBuilder();
        for (ChordLine line : lines) {
            if (!line.section.isEmpty()) out.append('[').append(line.section).append("]\n");
            if (!line.chords.isEmpty()) {
                List<ChordPlacement> sorted = new ArrayList<ChordPlacement>(line.chords);
                Collections.sort(sorted, new Comparator<ChordPlacement>() { public int compare(ChordPlacement a, ChordPlacement b) { return Integer.compare(a.offset, b.offset); } });
                StringBuilder row = new StringBuilder();
                for (ChordPlacement c : sorted) { while (row.length() < c.offset) row.append(' '); if (row.length() > c.offset) row.append(' '); row.append(c.name); }
                out.append(row).append('\n');
            }
            if (!line.text.isEmpty()) out.append(line.text).append('\n');
            else if (!line.chords.isEmpty()) out.append('\n');
        }
        return out.toString();
    }
}
