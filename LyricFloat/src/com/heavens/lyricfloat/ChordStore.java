package com.heavens.lyricfloat;

import android.content.Context;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONObject;

public final class ChordStore {
    private final File directory;
    public ChordStore(Context context) { directory = new File(context.getFilesDir(), "chords"); }
    private List<TitleCleaner.Candidate> candidates(String title, String artist) { return TitleCleaner.candidates(title, artist); }
    private File file(TitleCleaner.Candidate c) throws Exception {
        return new File(directory, LyricsCache.songHash(c.artist, c.track) + ".json");
    }
    public ChordSheet load(String title, String artist) {
        // Lyrics may have been cached under a fallback title candidate, so try them all.
        for (TitleCleaner.Candidate c : candidates(title, artist)) {
            try {
                File f = file(c);
                if (!f.isFile() || f.length() > 2 * 1024 * 1024) continue;
                return ChordSheet.fromJson(new JSONObject(new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)));
            } catch (Exception ignored) { }
        }
        return null;
    }
    public boolean save(String title, String artist, ChordSheet sheet) {
        try {
            List<TitleCleaner.Candidate> all = candidates(title, artist);
            if (all.isEmpty() || sheet == null) return false;
            File f = file(all.get(0));
            if (!directory.isDirectory() && !directory.mkdirs()) return false;
            // Write to a temporary file before replacing the old sheet.
            File temp = new File(directory, f.getName() + ".tmp");
            Files.write(temp.toPath(), sheet.toJson().toString().getBytes(StandardCharsets.UTF_8));
            if (!temp.renameTo(f)) { temp.delete(); return false; }
            return true;
        } catch (Exception e) { return false; }
    }
}
