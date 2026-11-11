package com.heavens.lyricfloat;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import org.json.JSONObject;

public final class LyricsCache {
    private final File dir;
    public LyricsCache(Context c) { dir = new File(c.getFilesDir(), "lyrics"); }
    private File file(String artist, String track) throws Exception {
        String key = (artist.trim() + "\n" + track.trim()).toLowerCase(Locale.ROOT);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
        StringBuilder name = new StringBuilder();
        for (byte b : hash) name.append(String.format(Locale.ROOT, "%02x", b & 255));
        return new File(dir, name.toString() + ".json");
    }
    public synchronized LyricsFetcher.Result get(String artist, String track) {
        try {
            File f = file(artist, track);
            if (!f.isFile() || f.length() > 1024 * 1024) return null;
            JSONObject j = new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8));
            String text = j.optString("text", "");
            if (text.trim().isEmpty()) return null;
            f.setLastModified(System.currentTimeMillis());
            return new LyricsFetcher.Result(text, j.optBoolean("synced", false));
        } catch (Exception e) { return null; }
    }
    public synchronized void put(String artist, String track, LyricsFetcher.Result result) {
        try {
            if (!dir.isDirectory() && !dir.mkdirs()) return;
            File f = file(artist, track);
            JSONObject j = new JSONObject(); j.put("text", result.text); j.put("synced", result.synced);
            Files.write(f.toPath(), j.toString().getBytes(StandardCharsets.UTF_8));
            File[] files = dir.listFiles();
            if (files == null || files.length <= 200) return;
            Arrays.sort(files, new Comparator<File>() {
                @Override public int compare(File a, File b) { return Long.compare(a.lastModified(), b.lastModified()); }
            });
            for (int i = 0; i < files.length - 200; i++) files[i].delete();
        } catch (Exception ignored) { }
    }
}
