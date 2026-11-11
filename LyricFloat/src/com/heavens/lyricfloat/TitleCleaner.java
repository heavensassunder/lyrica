package com.heavens.lyricfloat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TitleCleaner {
    public static final class Candidate {
        public final String artist, track;
        Candidate(String artist, String track) { this.artist = artist; this.track = track; }
    }
    private static String clean(String s) {
        if (s == null) return "";
        // Remove common YouTube decorations, including bare resolution labels and emoji.
        return s.replaceAll("(?i)[\\[(]\\s*(?:official(?:\\s+music)?\\s+video|lyrics?|lyric\\s+video|audio|remastered(?:\\s+\\d{4})?|live|hd|4k)\\s*[\\])]", " ")
                .replaceAll("(?i)\\b(?:official\\s+(?:music\\s+)?video|lyric\\s+video|lyrics?|audio|remastered|live|hd|4k)\\b", " ")
                .replaceAll("[\\p{So}\\p{Cs}]", " ").replaceAll("\\s+", " ").trim()
                .replaceAll("^[\\s\\-–—|]+|[\\s\\-–—|]+$", "").trim();
    }
    private static String artist(String s) {
        if (s == null) return "";
        return clean(s.replaceAll("(?i)\\s*-\\s*topic\\s*$", "").replaceAll("(?i)\\s*vevo\\s*$", ""));
    }
    private static String noFeat(String s) {
        return s.replaceAll("(?i)\\s*[\\[(]?\\s*\\b(?:ft\\.?|feat\\.?)\\s+.*$", "").trim();
    }
    private static void add(List<Candidate> out, Set<String> seen, String a, String t) {
        a = artist(a); t = clean(t);
        if (t.isEmpty()) return;
        String key = (a + "\n" + t).toLowerCase(Locale.ROOT);
        if (seen.add(key)) out.add(new Candidate(a, t));
    }
    public static List<Candidate> candidates(String title, String channel) {
        List<Candidate> out = new ArrayList<Candidate>();
        Set<String> seen = new LinkedHashSet<String>();
        String raw = title == null ? "" : title;
        int split = raw.indexOf(" - ");
        String a = split >= 0 ? raw.substring(0, split) : channel;
        String t = split >= 0 ? raw.substring(split + 3) : raw;
        String cleaned = clean(t);
        add(out, seen, noFeat(a == null ? "" : a), noFeat(cleaned));
        add(out, seen, a, cleaned); // Preserve featured artists for fallback.
        if (split >= 0) {
            add(out, seen, channel, noFeat(cleaned));
            add(out, seen, channel, cleaned);
            add(out, seen, channel, clean(raw));
        }
        return out;
    }
    private TitleCleaner() { }
}
