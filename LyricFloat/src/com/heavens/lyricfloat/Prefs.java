package com.heavens.lyricfloat;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONObject;

/** Central defaults and persisted overlay settings. */
public final class Prefs {
    private final SharedPreferences p;
    public Prefs(Context context) { p = context.getApplicationContext().getSharedPreferences("settings", Context.MODE_PRIVATE); }
    public int x() { return p.getInt("x", 0); }
    public int y() { return p.getInt("y", 100); }
    public int width() { return p.getInt("width", 300); } // dp
    public int overlayAlpha() { return p.getInt("overlayAlpha", 100); }
    public int panelAlpha() { return p.getInt("panelAlpha", 65); }
    public int textSizeSp() { return p.getInt("textSizeSp", 22); }
    public int syncOffsetMs() { return p.getInt("syncOffsetMs", 0); }
    public boolean showContext() { return p.getBoolean("showContext", true); }
    public boolean autoHide() { return p.getBoolean("autoHide", false); }
    public static final String[] COLOR_KEYS = {"currentColor", "otherColor", "shadowColor", "panelColor", "chordColor", "nextChordColor", "sectionColor"};
    public static final String[] COLOR_LABELS = {"Current lyric", "Other lyrics", "Outline / shadow", "Panel background", "Chord", "Next chord", "Section label"};
    public static final String[] PRESET_NAMES = {"Classic White", "Neon Green", "Sunset", "Ocean Blue", "Karaoke Gold", "Hacker Terminal", "Midnight Purple"};
    private static final int[][] PRESETS = {
        {0xffffffff,0xffffffff,0xff000000,0xff0c0c10,0xffffdf6e,0xffb1d7ff,0xffc7bfff},
        {0xffbcffad,0xff71d979,0xff001d03,0xff06170b,0xffd1ff37,0xff5bffd8,0xffa2ffa6},
        {0xffffdfbd,0xffffa5a5,0xff42101f,0xff29101c,0xffffa045,0xffff80b7,0xffffd596},
        {0xffe2f7ff,0xff8cccec,0xff00192e,0xff061a29,0xff6edfff,0xff97a8ff,0xffa3efff},
        {0xfffff5cd,0xffdeca83,0xff261900,0xff201704,0xffffca36,0xffffee99,0xffffd860},
        {0xff54ff69,0xff22b83e,0xff001000,0xff000000,0xffb5ff54,0xff39e5aa,0xff70d75a},
        {0xfff1e4ff,0xffbba2dc,0xff150624,0xff150d25,0xffd597ff,0xff8aafff,0xfff4a0ea}
    };
    public int color(int index) { return p.getInt(COLOR_KEYS[index], PRESETS[0][index]); }
    public void setColor(int index, int color) { p.edit().putInt(COLOR_KEYS[index], color).apply(); }
    public void preset(int index) {
        if (index < 0 || index >= PRESETS.length) return;
        SharedPreferences.Editor e = p.edit();
        for (int i = 0; i < COLOR_KEYS.length; i++) e.putInt(COLOR_KEYS[i], PRESETS[index][i]);
        e.apply();
    }
    public int presetColor(int preset, int index) { return PRESETS[preset][index]; }
    public int font() { return p.getInt("font", 0); }
    public int outline() { return p.getInt("outline", 2); }
    public boolean guitar() { return p.getBoolean("guitar", false); }
    public int guitarHeight() { return p.getInt("guitarHeight", 75); }
    public int guitarSize() { return p.getInt("guitarSize", 18); }
    public int transpose() { return p.getInt("transpose", 0); }
    public int capo() { return p.getInt("capo", 0); }
    public int chordOffset() { return p.getInt("chordOffset", 0); }
    public int reactionDelay() { return p.getInt("reactionDelay", 150); }
    public int diagramSize() { return p.getInt("diagramSize", 64); }
    public boolean flats() { return p.getBoolean("flats", false); }
    public boolean simplify() { return p.getBoolean("simplify", false); }
    public boolean diagrams() { return p.getBoolean("diagrams", false); }
    public boolean nextDiagram() { return p.getBoolean("nextDiagram", false); }
    public JSONObject themes() {
        try { return new JSONObject(p.getString("themes", "{}")); }
        catch (Exception e) { return new JSONObject(); }
    }
    public boolean saveTheme(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        try {
            JSONObject all = themes(), theme = new JSONObject();
            for (int i = 0; i < COLOR_KEYS.length; i++) theme.put(COLOR_KEYS[i], color(i));
            theme.put("font", font()); theme.put("outline", outline());
            all.put(name.trim(), theme); p.edit().putString("themes", all.toString()).apply(); return true;
        } catch (Exception e) { return false; }
    }
    public boolean applyTheme(String name) {
        try {
            JSONObject theme = themes().getJSONObject(name); SharedPreferences.Editor e = p.edit();
            for (int i = 0; i < COLOR_KEYS.length; i++) e.putInt(COLOR_KEYS[i], theme.optInt(COLOR_KEYS[i], color(i)));
            e.putInt("font", theme.optInt("font", font())); e.putInt("outline", theme.optInt("outline", outline()));
            e.apply(); return true;
        } catch (Exception e) { return false; }
    }
    public void deleteTheme(String name) {
        JSONObject all = themes(); all.remove(name); p.edit().putString("themes", all.toString()).apply();
    }
    public void position(int x, int y) { p.edit().putInt("x", x).putInt("y", y).apply(); }
    public void putInt(String key, int value) { p.edit().putInt(key, value).apply(); }
    public void putBoolean(String key, boolean value) { p.edit().putBoolean(key, value).apply(); }
}
