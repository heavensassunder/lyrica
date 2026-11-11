package com.heavens.lyricfloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.SystemClock;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/** Cached sheet layout: no text measurement or per-frame object construction in onDraw. */
public final class GuitarView extends View {
    private static final class Row {
        String text, section, stamp;
        long time;
        String[] names;
        float[] offsets;
    }
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Prefs prefs;
    private final float density, scaled;
    private Row[] rows = new Row[0];
    private long[] times = new long[0], eventTimes = new long[0];
    private String[] eventNames = new String[0];
    private final int[] nowShape = new int[6], nextShape = new int[6];
    private boolean hasNowShape, hasNextShape, playing, noChords, aligned;
    private int currentLine = -1, currentChord = -1, nowColor, otherColor, chordColor, nextColor, sectionColor, shadowColor, outline;
    private float textSize, lineHeight, barHeight, scroll, startScroll, targetScroll, progress;
    private String nowFret = "", nextFret = "";
    private long animationStart, lastPosition = -1;
    private String nowName = "—", nextName = "—", message = "";
    private Typeface font = Typeface.MONOSPACE;
    public GuitarView(Context context, Prefs prefs) {
        super(context); this.prefs = prefs; density = getResources().getDisplayMetrics().density;
        scaled = getResources().getDisplayMetrics().scaledDensity; updateStyle();
    }
    public void updateStyle() {
        nowColor = prefs.color(0); otherColor = prefs.color(1); shadowColor = prefs.color(2); chordColor = prefs.color(4);
        nextColor = prefs.color(5); sectionColor = prefs.color(6); outline = prefs.outline(); textSize = prefs.guitarSize() * scaled;
        lineHeight = Math.max(36 * density, textSize * 2.6f);
        barHeight = (prefs.diagrams() ? Math.max(75, prefs.diagramSize() + 28) : 64) * density;
        switch (prefs.font()) { case 1: font = Typeface.SERIF; break; case 2: font = Typeface.MONOSPACE; break; case 3: font = Typeface.create(Typeface.DEFAULT, Typeface.BOLD_ITALIC); break; default: font = Typeface.SANS_SERIF; }
        invalidate();
    }
    private String shown(String s) { return ChordNames.display(s, prefs.transpose() - prefs.capo(), prefs.flats(), prefs.simplify()); }
    public void setSheet(ChordSheet sheet, boolean noChords) {
        this.noChords = noChords; aligned = sheet != null && sheet.aligned;
        if (sheet == null) sheet = new ChordSheet();
        List<Row> built = new ArrayList<Row>();
        paint.setTypeface(font); paint.setTextSize(textSize);
        for (ChordSheet.ChordLine source : sheet.lines) {
            if (source.section != null && !source.section.isEmpty()) {
                Row label = new Row(); label.text = ""; label.section = source.section; label.time = source.timeMs;
                label.names = new String[0]; label.offsets = new float[0]; label.stamp = ""; built.add(label);
            }
            Row row = new Row(); row.text = source.text == null ? "" : source.text; row.section = ""; row.time = source.timeMs;
            // Instrumental rows show their timestamp so chord-only gaps stay readable.
            row.stamp = row.text.isEmpty() && !source.chords.isEmpty() ? (source.timeMs / 60000) + ":" + String.format(java.util.Locale.ROOT, "%02d", (source.timeMs / 1000) % 60) : "";
            row.names = new String[source.chords.size()]; row.offsets = new float[source.chords.size()];
            for (int i = 0; i < source.chords.size(); i++) {
                ChordSheet.ChordPlacement chord = source.chords.get(i); row.names[i] = shown(chord.name);
                int offset = Math.max(0, Math.min(row.text.length(), chord.offset));
                // Empty instrumental rows retain their spacing rather than collapsing all chords to x=0.
                row.offsets[i] = row.text.isEmpty() ? paint.measureText("M") * chord.offset : paint.measureText(row.text, 0, offset);
            }
            built.add(row);
        }
        rows = built.toArray(new Row[built.size()]); times = new long[rows.length];
        for (int i = 0; i < rows.length; i++) times[i] = rows[i].time;
        List<ChordSheet.Event> events = sheet.events(); eventTimes = new long[events.size()]; eventNames = new String[events.size()];
        for (int i = 0; i < events.size(); i++) { eventTimes[i] = events.get(i).timeMs; eventNames[i] = shown(events.get(i).name); }
        currentLine = -1; currentChord = Integer.MIN_VALUE; lastPosition = -1; scroll = targetScroll = startScroll = 0;
        nowName = nextName = "—"; hasNowShape = hasNextShape = false; invalidate();
    }
    private int index(long[] array, long value) {
        int lo = 0, hi = array.length - 1, found = -1;
        while (lo <= hi) { int mid = (lo + hi) >>> 1; if (array[mid] <= value) { found = mid; lo = mid + 1; } else hi = mid - 1; }
        return found;
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh); lastPosition = -1;
    }
    public void update(long position, boolean isPlaying, String status) {
        String newMessage = status == null ? "" : status;
        boolean redraw = playing != isPlaying || !message.equals(newMessage);
        playing = isPlaying; message = newMessage;
        long lyricPosition = Math.max(0, position + prefs.syncOffsetMs());
        long chordPosition = Math.max(0, lyricPosition + prefs.chordOffset());
        int line = index(times, lyricPosition), chord = index(eventTimes, chordPosition);
        boolean jumped = lastPosition < 0 || Math.abs(position - lastPosition) > 3000; lastPosition = position;
        if (line != currentLine || jumped) {
            redraw = true; currentLine = line; startScroll = scroll; targetScroll = Math.max(0, Math.max(0, line) * lineHeight - Math.max(0, getHeight() - barHeight) * .30f);
            animationStart = SystemClock.uptimeMillis(); if (jumped || !isPlaying) scroll = targetScroll;
        }
        if (chord != currentChord) {
            redraw = true; currentChord = chord; nowName = chord >= 0 ? eventNames[chord] : "—";
            nextName = chord + 1 < eventNames.length ? eventNames[chord + 1] : "—";
            hasNowShape = shape(nowName, nowShape); hasNextShape = shape(nextName, nextShape);
            nowFret = fretLabel(nowShape); nextFret = fretLabel(nextShape);
        }
        float oldProgress = progress;
        if (chord >= 0 && chord + 1 < eventTimes.length && eventTimes[chord + 1] > eventTimes[chord])
            progress = Math.max(0, Math.min(1, (float) (chordPosition - eventTimes[chord]) / (eventTimes[chord + 1] - eventTimes[chord])));
        else progress = 0;
        if ((int) (oldProgress * getWidth()) != (int) (progress * getWidth())) redraw = true;
        if (scroll != targetScroll && isPlaying) {
            redraw = true;
            float p = Math.min(1f, (SystemClock.uptimeMillis() - animationStart) / 220f);
            scroll = startScroll + (targetScroll - startScroll) * (1 - (1 - p) * (1 - p));
        }
        // The progress bar advances between changes, so its pixels also require redraws.
        if (redraw) invalidate();
    }
    private void textStyle(int color, float size, Typeface face) {
        paint.setColor(color); paint.setAlpha(Color.alpha(color)); paint.setTextSize(size); paint.setTypeface(face);
        paint.setStyle(Paint.Style.FILL); paint.clearShadowLayer();
    }
    /** Hardware-accelerated fake outline: one stroke pass behind the fill pass. */
    private void draw(Canvas c, String text, float x, float y) {
        if (text == null || text.length() == 0) return;
        if (outline > 0) {
            int alpha = paint.getAlpha(), color = paint.getColor();
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeJoin(Paint.Join.ROUND); paint.setStrokeWidth(Math.max(1f, outline * density * .55f));
            paint.setColor(shadowColor); paint.setAlpha(alpha); c.drawText(text, x, y, paint);
            paint.setStyle(Paint.Style.FILL); paint.setColor(color); paint.setAlpha(alpha);
        }
        c.drawText(text, x, y, paint);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c); float left = 12 * density, top = barHeight, w = getWidth();
        c.save(); c.clipRect(0, top, getWidth(), getHeight());
        for (int i = 0; i < rows.length; i++) {
            float y = top + i * lineHeight - scroll;
            if (y + lineHeight < top || y > getHeight()) continue;
            Row row = rows[i]; float fade = i < currentLine ? Math.max(.22f, Math.min(.7f, (y - top + lineHeight) / Math.max(1, lineHeight * 2))) : 1f;
            textStyle(i == currentLine ? nowColor : otherColor, textSize * (i == currentLine ? 1.08f : 1f), font);
            paint.setAlpha((int) (Color.alpha(paint.getColor()) * fade));
            draw(c, row.text, left, y + lineHeight * .86f);
            textStyle(chordColor, textSize * .9f, Typeface.DEFAULT_BOLD); paint.setAlpha((int) (Color.alpha(chordColor) * fade));
            for (int j = 0; j < row.names.length; j++) draw(c, row.names[j], left + row.offsets[j] * (i == currentLine ? 1.08f : 1f), y + lineHeight * .46f);
            if (!row.stamp.isEmpty()) { textStyle(otherColor, 9 * scaled, Typeface.DEFAULT); paint.setAlpha((int) (Color.alpha(otherColor) * fade)); draw(c, row.stamp, w - 38 * density, y + lineHeight * .40f); }
            if (!row.section.isEmpty()) { textStyle(sectionColor, textSize * .65f, Typeface.DEFAULT_BOLD); paint.setAlpha((int) (Color.alpha(sectionColor) * fade)); draw(c, row.section, left, y + textSize * .55f); }
        }
        c.restore();
        textStyle(otherColor, 11 * scaled, Typeface.DEFAULT); draw(c, "NOW", left, 15 * density);
        textStyle(chordColor, 24 * scaled, Typeface.DEFAULT_BOLD); draw(c, nowName, left, 43 * density);
        float nextX = w * .48f;
        textStyle(otherColor, 11 * scaled, Typeface.DEFAULT); draw(c, "NEXT", nextX, 15 * density);
        textStyle(nextColor, 17 * scaled, Typeface.DEFAULT_BOLD); draw(c, nextName, nextX, 40 * density);
        paint.clearShadowLayer(); paint.setColor(0x66444444); c.drawRect(left, barHeight - 5 * density, w - left, barHeight - 2 * density, paint);
        paint.setColor(nextColor); c.drawRect(left, barHeight - 5 * density, left + (w - left * 2) * progress, barHeight - 2 * density, paint);
        if (prefs.diagrams()) {
            float size = prefs.diagramSize() * density;
            // Place each diagram at the right edge of its column so long chord names never collide.
            if (hasNowShape) diagram(c, nowShape, nowFret, Math.max(nextX - size - 8 * density, 90 * density), 14 * density, size, chordColor);
            if (prefs.nextDiagram() && hasNextShape) diagram(c, nextShape, nextFret, Math.max(w - size - left, nextX + 60 * density), 14 * density, size, nextColor);
        }
        if (message.length() > 0 || noChords || !aligned || !playing || rows.length == 0) {
            textStyle(otherColor, 11 * scaled, Typeface.DEFAULT);
            // Status messages (searching / not found) take priority over an empty sheet notice.
            String note = message.length() > 0 ? message
                    : noChords ? "No chords for this song — import it in LyricFloat"
                    : rows.length == 0 ? "Waiting for lyrics…"
                    : !aligned ? "Timing estimated — align or record chords" : "Paused";
            draw(c, note, left, getHeight() - 6 * density);
        }
    }
    /** Common movable E/A shapes; unknown and slash chords deliberately have no diagram. */
    private static boolean shape(String name, int[] out) {
        if (!ChordNames.valid(name) || name.contains("/")) return false;
        int rootLength = name.length() > 1 && (name.charAt(1) == '#' || name.charAt(1) == 'b') ? 2 : 1;
        int root = ChordNames.pitch(name.substring(0, rootLength)); String q = name.substring(rootLength);
        if (q.equals("min")) q = "m"; if (q.equals("min7")) q = "m7";
        int eFret = (root - 4 + 12) % 12, aFret = (root - 9 + 12) % 12;
        boolean e = eFret <= aFret; int fret = e ? eFret : aFret; int[] form;
        if (q.isEmpty()) form = e ? new int[]{0,2,2,1,0,0} : new int[]{-1,0,2,2,2,0};
        else if (q.equals("m")) form = e ? new int[]{0,2,2,0,0,0} : new int[]{-1,0,2,2,1,0};
        else if (q.equals("7")) form = e ? new int[]{0,2,0,1,0,0} : new int[]{-1,0,2,0,2,0};
        else if (q.equals("maj7")) form = e ? new int[]{0,2,1,1,0,0} : new int[]{-1,0,2,1,2,0};
        else if (q.equals("m7")) form = e ? new int[]{0,2,0,0,0,0} : new int[]{-1,0,2,0,1,0};
        else if (q.equals("sus2")) form = e ? new int[]{0,2,4,4,0,0} : new int[]{-1,0,2,2,0,0};
        else if (q.equals("sus4")) form = e ? new int[]{0,2,2,2,0,0} : new int[]{-1,0,2,2,3,0};
        else if (q.equals("add9")) form = e ? new int[]{0,2,2,1,0,2} : new int[]{-1,0,2,4,2,0};
        else if (q.equals("dim")) form = e ? new int[]{-1,-1,2,3,5,3} : new int[]{-1,0,1,2,1,-1};
        else return false;
        for (int i = 0; i < 6; i++) out[i] = form[i] < 0 ? -1 : form[i] + fret;
        return true;
    }
    private static String fretLabel(int[] frets) {
        int base = Integer.MAX_VALUE;
        for (int f : frets) if (f > 0) base = Math.min(base, f);
        return base != Integer.MAX_VALUE && base > 1 ? Integer.toString(base) : "";
    }
    private void diagram(Canvas c, int[] frets, String fretLabel, float x, float y, float size, int color) {
        float step = size / 6, height = step * 4; int base = Integer.MAX_VALUE;
        for (int f : frets) if (f > 0) base = Math.min(base, f);
        if (base == Integer.MAX_VALUE || base <= 1) base = 1;
        paint.clearShadowLayer(); paint.setColor(color); paint.setStrokeWidth(Math.max(1, density)); paint.setTextSize(9 * scaled); paint.setTypeface(Typeface.DEFAULT);
        for (int i = 0; i < 6; i++) c.drawLine(x + i * step, y + step, x + i * step, y + step + height, paint);
        for (int i = 0; i <= 4; i++) c.drawLine(x, y + step + i * step, x + step * 5, y + step + i * step, paint);
        if (base > 1) c.drawText(fretLabel, x - step, y + step * 2, paint);
        for (int i = 0; i < 6; i++) {
            if (frets[i] < 0) c.drawText("x", x + i * step - step * .25f, y + step * .8f, paint);
            else if (frets[i] == 0) c.drawCircle(x + i * step, y + step * .45f, step * .2f, paint);
            else if (frets[i] - base < 4) c.drawCircle(x + i * step, y + step * (frets[i] - base + 1.5f), step * .3f, paint);
        }
    }
}
