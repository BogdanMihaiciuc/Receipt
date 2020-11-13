package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

public class GlyphDrawable extends Drawable implements Glyph.GlyphListener {
    private Glyph glyph;
    private int intrinsicWidth = -1, intrinsicHeight = -1;

    public GlyphDrawable(Context context, int glyph) {
        this.glyph = new Glyph(context, this);
        this.glyph.setGlyph(glyph, false);
    }

    @Override
    public void draw(Canvas canvas) {
        glyph.render(canvas);
    }

    public void onBoundsChange(Rect newBounds) {
        super.onBoundsChange(newBounds);

        glyph.setCenter(newBounds.centerX(), newBounds.centerY());
    }

    public GlyphDrawable setIntrinsicSize(int size) {
        intrinsicWidth = size;
        intrinsicHeight = size;
        glyph.setSize(size);
        return this;
    }

    @Override
    public int getIntrinsicWidth() {
        return intrinsicWidth;
    }

    public int getIntrinsicHeight() {
        return intrinsicHeight;
    }

    @Override
    public void setAlpha(int alpha) {

    }

    public GlyphDrawable setColor(int color) {
        glyph.setColor(color);
        return this;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public void invalidate() {
        invalidateSelf();
    }
}
