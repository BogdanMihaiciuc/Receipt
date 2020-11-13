package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

public class LabelDrawable extends Drawable {

    final static int DefaultHeightDP = 32;
    final static int DefaultColor = 0x44000000;
    final static Paint LabelPaint;

    static {
        LabelPaint = new Paint();
        LabelPaint.setStyle(Paint.Style.FILL);
        LabelPaint.setAntiAlias(true);
    }

    private ColorFilter cf;
    private int alpha;

    private Context context;
    private Utils.DPTranslator pixels;

    private int height;
    private int color = DefaultColor;
    private ValueAnimator colorAnimator;
    private RectF labelBounds = new RectF();

    private boolean animationsEnabled = true;

    public LabelDrawable(Context context) {
        this.context = context;
        pixels = new Utils.DPTranslator(context.getResources().getDisplayMetrics().density);

        height = pixels.get(DefaultHeightDP);
    }

    @Override
    public void draw(Canvas canvas) {
        LabelPaint.setColor(color);
        canvas.drawRoundRect(labelBounds, height / 2, height / 2, LabelPaint);
    }

    public void onBoundsChange(Rect newBounds) {
        labelBounds.left = newBounds.left;
        labelBounds.right = newBounds.right;
        labelBounds.top = newBounds.centerY() - height / 2;
        labelBounds.bottom = labelBounds.top + height;
    }

    public void setAnimationsEnabled(boolean enabled) {
        if (!enabled) {
            if (colorAnimator != null) colorAnimator.end();
        }

        animationsEnabled = enabled;
    }

    public void setColor(int color, boolean animated) {
        animated = animated & animationsEnabled;

        if (colorAnimator != null) {
            colorAnimator.end();
        }

        if (animated) {
            final int StartingColor = this.color;
            final int EndingColor = color;
            colorAnimator = ValueAnimator.ofFloat(0f, 1f);
            colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    LabelDrawable.this.color = Utils.interpolateColors(animation.getAnimatedFraction(), StartingColor, EndingColor);
                    invalidateSelf();
                }
            });
            colorAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    colorAnimator = null;
                    ((Utils.RippleAnimationStack) context).removeRipple(animation);
                }
            });
            colorAnimator.setDuration(200);
            colorAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            colorAnimator.start();
            ((Utils.RippleAnimationStack) context).addRipple(colorAnimator);
        }
        else {
            this.color = color;
            invalidateSelf();
        }
    }

    public boolean getPadding(Rect padding) {
        padding.left += height / 2;
        padding.right += height / 2;

        return true;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = alpha;
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        this.cf = cf;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
