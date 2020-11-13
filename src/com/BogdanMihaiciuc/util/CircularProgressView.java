package com.BogdanMihaiciuc.util;

import android.R;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;


public class CircularProgressView extends View {

    final static int ThicknessDP = 4;

    final static float StartAngle = -45;

    final static long AnimationDuration = 400;

    final static Paint DrawPaint;

    static {
        DrawPaint = new Paint();
        DrawPaint.setStyle(Paint.Style.STROKE);
        DrawPaint.setAntiAlias(true);
    }

    private void initialize() {
        density = getResources().getDisplayMetrics().density;

        color = getResources().getColor(R.color.holo_blue_light);
        thickness = ThicknessDP * density;
    }

    private float density;

    private int color;
    private int animatedColor;
    private ValueAnimator colorAnimator;
    private float thickness;

    private int width;
    private int height;
    private RectF drawRect;

    private float completion;
    private float animatedCompletion;
    private ValueAnimator completionAnimator;

    private TextView linkedTextView;

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        if (colorAnimator != null) colorAnimator.cancel();
        this.color = color;
        animatedColor = color;
        if (linkedTextView != null) {
            linkedTextView.setTextColor(animatedColor);
        }
        invalidate();
    }

    public void setColor(final int ColorStop, boolean animated) {
        if (!animated) {
            setColor(ColorStop);
            return;
        }

        if (colorAnimator != null) colorAnimator.cancel();
        animatedColor = this.color;
        this.color = ColorStop;

        final int AStart = Color.alpha(animatedColor);
        final int RStart = Color.red(animatedColor);
        final int GStart = Color.green(animatedColor);
        final int BStart = Color.blue(animatedColor);

        final int AStop = Color.alpha(ColorStop);
        final int RStop = Color.red(ColorStop);
        final int GStop = Color.green(ColorStop);
        final int BStop = Color.blue(ColorStop);

        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();
                animatedColor = Color.argb(
                        AStart + (int) ((AStop - AStart) * fraction),
                        RStart + (int) ((RStop - RStart) * fraction),
                        GStart + (int) ((GStop - GStart) * fraction),
                        BStart + (int) ((BStop - BStart) * fraction)
                );
                if (linkedTextView != null) {
                    linkedTextView.setTextColor(animatedColor);
                }
                invalidate();
            }
        });
        colorAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                colorAnimator = null;
            }
        });
        colorAnimator.setDuration(AnimationDuration);
        colorAnimator.start();
    }

    public float getCompletion() {
        return completion;
    }

    public void setCompletion(float completion) {
        if (completionAnimator != null) completionAnimator.cancel();
        this.completion = completion;
        this.animatedCompletion = completion;
        invalidate();
    }

    public void setCompletion(final float completion, boolean animated) {
        if (!animated) {
            setCompletion(completion);
            return;
        }

        if (completionAnimator != null) completionAnimator.cancel();
        this.animatedCompletion = this.completion;
        this.completion = completion;

        completionAnimator = ValueAnimator.ofFloat(this.animatedCompletion, completion);
        completionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                animatedCompletion = (Float) valueAnimator.getAnimatedValue();
                invalidate();
            }
        });
        completionAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                completionAnimator = null;
            }
        });
        completionAnimator.setDuration(AnimationDuration);
        completionAnimator.start();
    }

    public float getThickness() {
        return thickness;
    }

    public void setThickness(float thickness) {
        this.thickness = thickness;
        invalidate();
    }

    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        int usableWidth = width - getPaddingLeft() - getPaddingRight();
        int usableHeight = height - getPaddingTop() - getPaddingBottom();

        int widthDisplacement = usableWidth > usableHeight ? (usableWidth - usableHeight) / 2 : 0;
        int heightDisplacement = usableHeight > usableWidth ? (usableHeight - usableWidth) / 2 : 0;

        drawRect = new RectF(
                getPaddingLeft() + widthDisplacement + thickness / 2f,
                getPaddingTop() + heightDisplacement + thickness / 2f,
                width - getPaddingRight() - widthDisplacement - thickness / 2f,
                height - getPaddingBottom() - heightDisplacement - thickness / 2f
        );

    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (drawRect == null) {
            onSizeChanged(getWidth(), getHeight(), 0, 0);
        }

        DrawPaint.setColor(animatedColor);
        DrawPaint.setStrokeWidth(thickness);

        canvas.drawArc(drawRect, StartAngle, animatedCompletion * 360f, false, DrawPaint);
    }

    public CircularProgressView(Context context) {
        super(context);
        initialize();
    }

    public CircularProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public CircularProgressView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }

    public TextView getLinkedTextView() {
        return linkedTextView;
    }

    public void setLinkedTextView(TextView linkedTextView) {
        this.linkedTextView = linkedTextView;
    }
}
