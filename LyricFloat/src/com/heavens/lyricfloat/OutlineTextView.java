package com.heavens.lyricfloat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.Layout;
import android.text.TextPaint;
import android.widget.TextView;

/** Draw the existing TextView layout once as a stroke, then normally as a fill. */
public final class OutlineTextView extends TextView {
    private int outlineColor;
    private float width;
    public OutlineTextView(Context context) { super(context); }
    public void setOutline(int color, float pixels) { outlineColor = color; width = pixels; invalidate(); }
    @Override protected void onDraw(Canvas canvas) {
        Layout layout = getLayout();
        if (layout != null && width > 0) {
            TextPaint p = getPaint(); int oldColor = p.getColor(); Paint.Style oldStyle = p.getStyle(); float oldWidth = p.getStrokeWidth();
            p.setStyle(Paint.Style.STROKE); p.setStrokeJoin(Paint.Join.ROUND); p.setStrokeWidth(width); p.setColor(outlineColor);
            canvas.save(); canvas.translate(getCompoundPaddingLeft() - getScrollX(), getExtendedPaddingTop() - getScrollY());
            layout.draw(canvas); canvas.restore();
            p.setColor(oldColor); p.setStyle(oldStyle); p.setStrokeWidth(oldWidth);
        }
        super.onDraw(canvas);
    }
}
