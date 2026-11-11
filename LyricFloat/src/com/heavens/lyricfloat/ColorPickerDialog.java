package com.heavens.lyricfloat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import java.util.Locale;

/** Live HSV/alpha picker; Cancel restores the original color. */
public final class ColorPickerDialog {
    public interface Listener { void changed(int color); }
    private final Context context;
    private final Listener listener;
    private final int original;
    private final float[] hsv = new float[3];
    private int alpha;
    private boolean updating, accepted;
    private SeekBar hue, opacity;
    private EditText hex;
    private TextView preview;
    private Square square;
    public ColorPickerDialog(Context context, int initial, Listener listener) {
        this.context = context; this.listener = listener; original = initial;
        Color.colorToHSV(initial, hsv); alpha = Color.alpha(initial);
    }
    private int dp(int n) { return (int) (context.getResources().getDisplayMetrics().density * n + .5f); }
    private int color() { return Color.HSVToColor(alpha, hsv); }
    public void show(String title) {
        LinearLayout box = new LinearLayout(context); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20), dp(8), dp(20), dp(8));
        square = new Square(context); box.addView(square, new LinearLayout.LayoutParams(-1, dp(180)));
        TextView h = new TextView(context); h.setText("Hue"); box.addView(h);
        hue = new SeekBar(context); hue.setMax(360);
        GradientDrawable rainbow = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED});
        rainbow.setCornerRadius(dp(4)); rainbow.setSize(1, dp(8)); hue.setProgressDrawable(rainbow); box.addView(hue);
        TextView a = new TextView(context); a.setText("Alpha"); box.addView(a);
        opacity = new SeekBar(context); opacity.setMax(255); box.addView(opacity);
        hex = new EditText(context); hex.setSingleLine(true); hex.setHint("#RRGGBB or #AARRGGBB"); box.addView(hex);
        preview = new TextView(context); preview.setText("Sample lyric text  ♫"); preview.setTextSize(22); preview.setPadding(dp(10), dp(12), dp(10), dp(12)); preview.setBackgroundColor(0xff202027); box.addView(preview);
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) { if (user) { hsv[0] = value; sync(true); } }
            public void onStartTrackingTouch(SeekBar b) { } public void onStopTrackingTouch(SeekBar b) { }
        });
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int value, boolean user) { if (user) { alpha = value; sync(true); } }
            public void onStartTrackingTouch(SeekBar b) { } public void onStopTrackingTouch(SeekBar b) { }
        });
        hex.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (updating) return;
                String value = s.toString().trim();
                if (!value.matches("#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?")) return;
                try {
                    String digits = value.substring(1);
                    int rgb = Integer.parseInt(digits.length() == 8 ? digits.substring(2) : digits, 16);
                    // A six digit value keeps the alpha slider's current transparency.
                    if (digits.length() == 8) alpha = Integer.parseInt(digits.substring(0, 2), 16);
                    int c = Color.rgb((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255);
                    Color.colorToHSV(c, hsv); sync(false);
                } catch (NumberFormatException ignored) { }
            }
            public void afterTextChanged(Editable e) { }
        });
        sync(false);
        AlertDialog dialog = new AlertDialog.Builder(context).setTitle(title).setView(box)
                .setPositiveButton("OK", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) { accepted = true; } })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() { public void onClick(DialogInterface d, int which) { listener.changed(original); } }).create();
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() { public void onCancel(DialogInterface d) { if (!accepted) listener.changed(original); } });
        dialog.show();
    }
    private void sync(boolean writeHex) {
        updating = true; hue.setProgress(Math.round(hsv[0])); opacity.setProgress(alpha);
        if (writeHex || hex.length() == 0) hex.setText(String.format(Locale.ROOT, "#%08X", color()));
        preview.setTextColor(color()); square.invalidate(); updating = false; listener.changed(color());
    }
    private final class Square extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Square(Context c) { super(c); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas); int w = getWidth(), h = getHeight(); if (w <= 0 || h <= 0) return;
            paint.setShader(new LinearGradient(0, 0, w, 0, Color.WHITE, Color.HSVToColor(new float[]{hsv[0], 1, 1}), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, 0, h, Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)); canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null); paint.setColor(Color.WHITE); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(dp(2)); canvas.drawCircle(hsv[1] * w, (1 - hsv[2]) * h, dp(6), paint); paint.setStyle(Paint.Style.FILL);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN || e.getAction() == MotionEvent.ACTION_MOVE) {
                hsv[1] = Math.max(0, Math.min(1, e.getX() / Math.max(1, getWidth())));
                hsv[2] = 1 - Math.max(0, Math.min(1, e.getY() / Math.max(1, getHeight()))); sync(true); return true;
            }
            return true;
        }
    }
}
