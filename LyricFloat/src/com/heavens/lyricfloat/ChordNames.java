package com.heavens.lyricfloat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChordNames {
    private static final Pattern CHORD = Pattern.compile("^([A-G])([#b]?)(?:(m|maj|min|dim|aug|sus[24]?|add[0-9]+|[0-9]+))*((?:/[A-G][#b]?)?)$");
    private static final Pattern PARTS = Pattern.compile("^([A-G][#b]?)([^/]*)(?:/([A-G][#b]?))?$");
    private static final String[] SHARP = {"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};
    private static final String[] FLAT = {"C","Db","D","Eb","E","F","Gb","G","Ab","A","Bb","B"};
    public static boolean valid(String name) { return name != null && CHORD.matcher(name).matches(); }
    public static int pitch(String n) {
        if (n == null || n.isEmpty()) return -1;
        String notes = "C D EF G A B"; int p = notes.indexOf(n.charAt(0));
        if (p < 0 || n.charAt(0) == ' ') return -1;
        if (n.length() > 1) p += n.charAt(1) == '#' ? 1 : n.charAt(1) == 'b' ? -1 : 0;
        return (p + 12) % 12;
    }
    public static String display(String name, int shift, boolean flats, boolean simplify) {
        if (!valid(name)) return name == null ? "" : name;
        Matcher m = PARTS.matcher(name); if (!m.matches()) return name;
        String suffix = m.group(2);
        if (simplify) suffix = suffix.startsWith("min") || (suffix.startsWith("m") && !suffix.startsWith("maj")) ? "m" : "";
        String[] notes = flats ? FLAT : SHARP;
        String result = notes[(pitch(m.group(1)) + shift % 12 + 12) % 12] + suffix;
        if (m.group(3) != null) result += "/" + notes[(pitch(m.group(3)) + shift % 12 + 12) % 12];
        return result;
    }
    private ChordNames() { }
}
