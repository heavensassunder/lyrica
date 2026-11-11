package com.heavens.lyricfloat;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses common two-line and ChordPro sheets, then matches lyrics in forward order. */
public final class ChordImporter {
    private static final Pattern TOKEN = Pattern.compile("\\S+");
    private static final Pattern INLINE = Pattern.compile("\\[([^\\]]+)\\]");
    private static boolean section(String row) {
        String s = row.trim();
        return s.matches("(?i)\\[(?:verse|chorus|bridge|intro|outro|instrumental|pre-chorus|refrain|solo)(?:[ 0-9:-]*)\\]") ||
                s.matches("(?i)(?:verse|chorus|bridge|intro|outro|instrumental|pre-chorus|refrain|solo)(?:[ 0-9-]*):");
    }
    private static String sectionName(String row) { return row.trim().replaceAll("^[\\[]|[\\]:]$", "").trim(); }
    private static boolean chordRow(String row) {
        Matcher m = TOKEN.matcher(row); int all = 0, chords = 0;
        while (m.find()) { all++; if (ChordNames.valid(m.group())) chords++; }
        return all > 0 && chords > 0 && chords * 4 >= all * 3;
    }
    private static void addChordRow(ChordSheet.ChordLine line, String row) {
        Matcher m = TOKEN.matcher(row);
        while (m.find()) if (ChordNames.valid(m.group())) line.chords.add(new ChordSheet.ChordPlacement(m.group(), m.start(), -1));
    }
    private static ChordSheet.ChordLine inline(String row) {
        ChordSheet.ChordLine line = new ChordSheet.ChordLine(); Matcher m = INLINE.matcher(row); StringBuilder text = new StringBuilder(); int last = 0;
        while (m.find()) {
            if (!ChordNames.valid(m.group(1))) continue;
            text.append(row, last, m.start()); line.chords.add(new ChordSheet.ChordPlacement(m.group(1), text.length(), -1)); last = m.end();
        }
        text.append(row.substring(last)); line.text = text.toString(); return line;
    }
    private static String normalized(String s) { return s.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim(); }
    private static double similarity(String a, String b) {
        a = normalized(a); b = normalized(b);
        if (a.isEmpty() || b.isEmpty()) return 0;
        if (a.equals(b)) return 1;
        // Cheap overlap test first: most candidate pairs are rejected without the O(n*m) table.
        String[] at = a.split(" "), bt = b.split(" ");
        int common = 0;
        for (String token : at) for (String other : bt) if (token.equals(other)) { common++; break; }
        double overlap = at.length == 0 ? 0 : (double) common / Math.max(at.length, bt.length);
        if (overlap < .45) return overlap * .8;
        if (a.contains(b) || b.contains(a)) return .92;
        // Bound work for unusually long pasted text on low-memory phones.
        if (a.length() > 160) a = a.substring(0, 160);
        if (b.length() > 160) b = b.substring(0, 160);
        int[] prev = new int[b.length() + 1], next = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            next[0] = i;
            for (int j = 1; j <= b.length(); j++) next[j] = Math.min(Math.min(next[j - 1] + 1, prev[j] + 1), prev[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1));
            int[] t = prev; prev = next; next = t;
        }
        return 1.0 - (double) prev[b.length()] / Math.max(a.length(), b.length());
    }
    public static ChordSheet parse(String input, List<LrcParser.Line> lyrics) {
        ChordSheet sheet = new ChordSheet();
        if (input == null) return sheet;
        if (input.length() > 500000) input = input.substring(0, 500000);
        String[] rows = input.replace("\r", "").split("\n", -1); String pendingSection = "";
        for (int i = 0; i < rows.length && sheet.lines.size() < 10000; i++) {
            String row = rows[i]; String trimmed = row.trim(); if (trimmed.isEmpty()) continue;
            // ChordPro metadata ({title: ...}) and comment directives are not lyric lines.
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) continue;
            if (trimmed.startsWith("#")) continue;
            if (section(row)) { pendingSection = sectionName(row); continue; }
            ChordSheet.ChordLine line;
            if (chordRow(row)) {
                line = new ChordSheet.ChordLine(); addChordRow(line, row);
                // A blank line, another chord row, or a section marks an instrumental row.
                if (i + 1 < rows.length && !rows[i + 1].trim().isEmpty() && !section(rows[i + 1]) && !chordRow(rows[i + 1])) {
                    ChordSheet.ChordLine next = inline(rows[++i]); line.text = next.text; line.chords.addAll(next.chords);
                }
            } else line = inline(row);
            line.section = pendingSection; pendingSection = ""; line.timeMs = -1; sheet.lines.add(line);
        }
        if (!pendingSection.isEmpty()) { ChordSheet.ChordLine line = new ChordSheet.ChordLine(); line.section = pendingSection; line.timeMs = -1; sheet.lines.add(line); }
        int cursor = 0;
        if (lyrics != null) for (ChordSheet.ChordLine line : sheet.lines) {
            if (line.text.trim().isEmpty()) continue;
            int best = -1; double score = .59;
            // Bounded forward search keeps repeated choruses ordered and limits CPU work.
            for (int j = cursor; j < lyrics.size() && j < cursor + 40; j++) {
                double candidate = similarity(line.text, lyrics.get(j).text);
                if (candidate > score) { score = candidate; best = j; if (candidate == 1.0) break; }
            }
            if (best >= 0) { line.timeMs = lyrics.get(best).timeMs; cursor = best + 1; sheet.matched++; }
        }
        sheet.aligned = sheet.matched > 0;
        // Interpolate unmatched rows (including instrumentals) between matched neighbors.
        int i = 0; long previous = 0;
        while (i < sheet.lines.size()) {
            if (sheet.lines.get(i).timeMs >= 0) { previous = sheet.lines.get(i).timeMs; i++; continue; }
            int first = i; while (i < sheet.lines.size() && sheet.lines.get(i).timeMs < 0) i++;
            int count = i - first;
            long next = i < sheet.lines.size() ? sheet.lines.get(i).timeMs : previous + (long) Math.max(1, count) * 4000;
            for (int n = 0; n < count; n++) {
                long t = first == 0 ? next * n / Math.max(1, count) : previous + Math.max(0, next - previous) * (n + 1) / (count + 1);
                sheet.lines.get(first + n).timeMs = Math.max(0, t);
            }
        }
        return sheet;
    }
    public static int lyricCount(ChordSheet sheet) {
        int count = 0; for (ChordSheet.ChordLine line : sheet.lines) if (!line.text.trim().isEmpty()) count++; return count;
    }
    private ChordImporter() { }
}
