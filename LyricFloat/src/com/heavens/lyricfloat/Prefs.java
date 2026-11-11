package com.heavens.lyricfloat;

import android.content.Context;
import android.content.SharedPreferences;

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
    public void position(int x, int y) { p.edit().putInt("x", x).putInt("y", y).apply(); }
    public void putInt(String key, int value) { p.edit().putInt(key, value).apply(); }
    public void putBoolean(String key, boolean value) { p.edit().putBoolean(key, value).apply(); }
}
