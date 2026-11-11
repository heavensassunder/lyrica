package com.heavens.lyricfloat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    public static final class Line {
        public final long timeMs;
        public final String text;
        public Line(long timeMs, String text) { this.timeMs = timeMs; this.text = text; }
    }
    private static final Pattern TIME = Pattern.compile("\\[(\\d+):(\\d{2})(?:[.:](\\d{1,3}))?\\]");
    private final List<Line> lines;
    public LrcParser(String lrc) {
        List<Line> result = new ArrayList<Line>();
        if (lrc != null) for (String row : lrc.split("\\r?\\n")) {
            Matcher m = TIME.matcher(row);
            List<Long> times = new ArrayList<Long>();
            int end = 0;
            while (m.find()) {
                try {
                    long min = Long.parseLong(m.group(1)), sec = Long.parseLong(m.group(2));
                    if (sec >= 60 || min > 1000000) continue;
                    String fraction = m.group(3);
                    int ms = fraction == null ? 0 : Integer.parseInt((fraction + "000").substring(0, 3));
                    times.add(min * 60000 + sec * 1000 + ms); end = m.end();
                } catch (RuntimeException ignored) { }
            }
            if (!times.isEmpty()) {
                String text = row.substring(end).trim();
                for (Long time : times) result.add(new Line(time, text));
            }
        }
        Collections.sort(result, new Comparator<Line>() {
            @Override public int compare(Line a, Line b) { return Long.compare(a.timeMs, b.timeMs); }
        });
        lines = Collections.unmodifiableList(result);
    }
    public List<Line> lines() { return lines; }
    /** Positive offset advances the displayed lyrics. Returns -1 before the first timestamp. */
    public int indexForPosition(long ms, int offsetMs) {
        long target = ms + offsetMs;
        int low = 0, high = lines.size() - 1, answer = -1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (lines.get(mid).timeMs <= target) { answer = mid; low = mid + 1; }
            else high = mid - 1;
        }
        return answer;
    }
}
