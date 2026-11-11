package com.heavens.lyricfloat;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LyricsFetcher {
    public static final class Result {
        public final String text;
        public final boolean synced;
        public Result(String text, boolean synced) { this.text = text; this.synced = synced; }
    }
    public interface Callback { void onResult(Result result); }
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LyricsCache cache;
    private Future<?> pending;
    public LyricsFetcher(Context context) { cache = new LyricsCache(context); }
    public void shutdown() { executor.shutdownNow(); }
    public void fetch(final String title, final String artist, final long duration, final Callback callback) {
        if (pending != null) pending.cancel(true);
        pending = executor.submit(new Runnable() {
            @Override public void run() {
                Result found = null;
                try {
                    List<TitleCleaner.Candidate> candidates = TitleCleaner.candidates(title, artist);
                    // Check every candidate before using the network, so cached fallback results work offline.
                    for (TitleCleaner.Candidate c : candidates) {
                        found = cache.get(c.artist, c.track);
                        if (found != null) break;
                    }
                    if (found == null) for (TitleCleaner.Candidate c : candidates) {
                        if (Thread.currentThread().isInterrupted()) break;
                        found = lookup(c, duration);
                        if (found != null) { cache.put(c.artist, c.track, found); break; }
                    }
                } catch (Exception ignored) { }
                final Result result = found;
                main.post(new Runnable() { @Override public void run() { callback.onResult(result); } });
            }
        });
    }
    private static String enc(String s) throws Exception { return URLEncoder.encode(s, "UTF-8"); }
    private static String request(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "LyricFloat/1.0");
        try {
            if (c.getResponseCode() != 200) return null;
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[4096]; int n, total = 0;
                while ((n = in.read(b)) != -1) {
                    total += n; if (total > 2 * 1024 * 1024) return null;
                    out.write(b, 0, n);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally { c.disconnect(); }
    }
    private static Result fromJson(JSONObject j) {
        if (j == null) return null;
        String synced = j.isNull("syncedLyrics") ? "" : j.optString("syncedLyrics", "");
        if (!synced.trim().isEmpty() && !new LrcParser(synced).lines().isEmpty()) return new Result(synced, true);
        String plain = j.isNull("plainLyrics") ? "" : j.optString("plainLyrics", "");
        return plain.trim().isEmpty() ? null : new Result(plain, false);
    }
    private static Result lookup(TitleCleaner.Candidate c, long duration) {
        try {
            String url = "https://lrclib.net/api/get?artist_name=" + enc(c.artist) + "&track_name=" + enc(c.track);
            if (duration > 0) url += "&duration=" + enc(Long.toString(Math.round(duration / 1000.0)));
            String json = request(url);
            if (json != null) {
                Result exact = fromJson(new JSONObject(json));
                if (exact != null) return exact;
            }
        } catch (Exception ignored) { }
        try {
            String json = request("https://lrclib.net/api/search?q=" + enc((c.artist + " " + c.track).trim()));
            if (json == null) return null;
            JSONArray arr = new JSONArray(json);
            Result best = null; double bestDiff = Double.MAX_VALUE;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject item = arr.optJSONObject(i);
                Result r = fromJson(item);
                if (r == null) continue;
                double d = item.optDouble("duration", -1);
                double diff = duration > 0 ? (d >= 0 ? Math.abs(d - duration / 1000.0) : Double.MAX_VALUE) : 0;
                if (duration > 0 && diff > 5) continue;
                if (best == null || diff < bestDiff || (diff == bestDiff && r.synced && !best.synced)) {
                    best = r; bestDiff = diff;
                }
            }
            return best;
        } catch (Exception ignored) { return null; }
    }
}
