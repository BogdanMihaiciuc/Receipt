package com.BogdanMihaiciuc.util;

import android.R;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

public class HelpView extends View {

    final static Paint BackgroundPaint;
    final static Paint CirclePaint;
    final static Paint GradientPaint;
    final static Paint Puncher;

    final static int BackgroundColor = 0xCC000000;

    final static float Sqrt2 = 1.4142135623730950488016887242096980785696f;

    static {
        CirclePaint = new Paint();
        CirclePaint.setStyle(Paint.Style.STROKE);
        CirclePaint.setAntiAlias(true);

        GradientPaint = new Paint();
        GradientPaint.setStyle(Paint.Style.FILL);
        GradientPaint.setAntiAlias(true);

        Puncher = new Paint();
        Puncher.setStyle(Paint.Style.FILL);
        Puncher.setAntiAlias(true);
        Puncher.setColor(BackgroundColor);

        BackgroundPaint = new Paint();
        BackgroundPaint.setStyle(Paint.Style.FILL);
        BackgroundPaint.setColor(BackgroundColor);
        BackgroundPaint.setAntiAlias(true);
    }

    final static int DefaultRadiusDP = 64;
    final static int CircleThicknessDP = 4;
    final static int InnerGradientThicknessDP = 24;
    final static int OuterGradientThicknessDP = 28;

    final static int GradientGapDP = 12;

    final static float CircleAnimationScale = 2;
    final static float ShaderAnimationScale = 0.8f;

    private float density;

    private float circleRadius;
    private float circleThickness;
    private float innerGradientThickness;
    private float outerGradientThickness;
    private float totalSize;
    private float totalRadius;
    private float gradientGap;
    private float centerX, centerY;

    private int color;
    private int innerColor;
    private int outerColor;
    private int transparent;

    private boolean drawn;

    private Path puncher;

    private RadialGradient innerGradient;
    private RadialGradient outerGradient;

    private ValueAnimator positionAnimator;
    private float animationPercent = 0f;
    private float acceleratedAnimationPercent = 0f;

    public void setTargetView(View view) {

        int location[] = new int[2];
        view.getLocationOnScreen(location);

        setPosition(location[0] + view.getWidth() / 2, location[1] + view.getHeight() / 2);

    }

    public void setPosition(int centerX, int centerY) {
        setPosition(centerX, centerY, false);
    }

    public void setPositionAnimated(int centerX, int centerY) {
        setPosition(centerX, centerY, true);
    }

    protected void setPosition(final int centerX, final int centerY, boolean animated) {
        if (drawn) {
            invalidate((int) (centerX - totalRadius - 0.5f), (int) (centerY - totalRadius - 0.5f),
                    (int) (centerX + totalRadius + 0.5f), (int) (centerY + totalRadius + 0.5f));
        }

        if (positionAnimator != null)
            positionAnimator.end();

        this.centerX = centerX;
        this.centerY = centerY;

        float innerRadius = circleRadius + innerGradientThickness;
        float colorStart = circleRadius / innerRadius;
        int colorMidpoint = Color.argb(
                (int) (0.40f * Color.alpha(innerColor)),
                Color.red(innerColor), Color.green(innerColor), Color.blue(innerColor)
        );
        innerGradient = new RadialGradient(centerX, centerY,
                innerRadius,
                new int[] {transparent, transparent, innerColor, colorMidpoint, transparent},
                new float[] {0f, colorStart - 0.005f, colorStart, 0.5f + colorStart / 2f, 1},
                Shader.TileMode.CLAMP
        );

        float outerRadius = circleRadius + innerGradientThickness + gradientGap + outerGradientThickness;
        colorStart = (circleRadius + innerGradientThickness + gradientGap) / outerRadius;
        colorMidpoint = Color.argb(
                (int) (0.40f * Color.alpha(outerColor)),
                Color.red(innerColor), Color.green(innerColor), Color.blue(innerColor)
        );
        outerGradient = new RadialGradient(centerX, centerY,
                outerRadius,
                new int[] {transparent, transparent, outerColor, colorMidpoint, transparent},
                new float[] {0f, colorStart - 0.005f, colorStart, 0.5f + colorStart / 2f, 1},
                Shader.TileMode.CLAMP
        );

        if (puncher != null) {
            puncher.reset();
        }
        else {
            puncher = new Path();
        }

        puncher.addRect((int) (centerX - totalRadius - 0.5f), (int) (centerY - totalRadius - 0.5f), (int) (centerX + totalRadius + 0.5f), (int) (centerY + totalRadius + 0.5f), Path.Direction.CW);
        puncher.setFillType(Path.FillType.EVEN_ODD);
        puncher.addCircle(centerX, centerY, circleRadius, Path.Direction.CW);

        if (animated) {
            positionAnimator = ValueAnimator.ofFloat(0f, 1f);
            final TimeInterpolator Decelerator = new DecelerateInterpolator(1.33f);
            final TimeInterpolator Accelerator = new AccelerateInterpolator(1.33f);
            positionAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    animationPercent = 1 - Decelerator.getInterpolation(valueAnimator.getAnimatedFraction());
                    acceleratedAnimationPercent = 1 - Accelerator.getInterpolation(valueAnimator.getAnimatedFraction());

                    float totalRadius = HelpView.this.totalRadius;
                    totalRadius *= CircleAnimationScale;

                    invalidate((int) (centerX - totalRadius - 0.5f), (int) (centerY - totalRadius - 0.5f),
                            (int) (centerX + totalRadius + 0.5f), (int) (centerY + totalRadius + 0.5f));
                }
            });
            positionAnimator.setInterpolator(new LinearInterpolator());
            positionAnimator.setDuration(400);
            positionAnimator.start();
        }

        if (!drawn)
            invalidate();
        else
            invalidate((int) (centerX - totalRadius - 0.5f), (int) (centerY - totalRadius - 0.5f),
                    (int) (centerX + totalRadius + 0.5f), (int) (centerY + totalRadius + 0.5f));
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            setPositionAnimated((int) event.getRawX(), (int) event.getRawY());
        }
        return true;
    }

    protected void initialize() {
        density = getResources().getDisplayMetrics().density;

        circleRadius = DefaultRadiusDP * density;
        circleThickness = CircleThicknessDP * density;
        innerGradientThickness = InnerGradientThicknessDP * density;
        outerGradientThickness = OuterGradientThicknessDP * density;
        gradientGap = GradientGapDP * density;

        totalRadius = circleRadius + innerGradientThickness + gradientGap + outerGradientThickness;
        totalSize = totalRadius * 2;

        color = getResources().getColor(R.color.holo_blue_light);
        initializeColors();
    }

    public void initializeColors() {
        int color = getResources().getColor(R.color.holo_blue_dark);
        innerColor = Color.argb(
                (int) (0.5f * Color.alpha(color)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
        outerColor = Color.argb(
                (int) (0.5f * Color.alpha(color)),
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
        transparent = Color.argb(
                0,
                Color.red(color),
                Color.green(color),
                Color.blue(color)
        );
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (innerGradient == null || outerGradient == null || puncher == null) {
            canvas.drawColor(BackgroundColor);
            return;
        }
        else {
            drawn = true;
        }

        /******* COMPONENTS********

                 --------------
                |     |   |    |
                |     | 3 |    |
                |     |---|    |
                |  1  | 5 |  2 |
                |     |---|    |
                |     | 4 |    |
                 --------------

         */

        canvas.drawRect(0, 0, (int) (centerX - totalRadius - 0.5f), getHeight(), BackgroundPaint);

        canvas.drawRect((int) (centerX + totalRadius + 0.5f), 0, getWidth(), getHeight(), BackgroundPaint);

        canvas.drawRect((int) (centerX - totalRadius - 0.5f), 0, (int) (centerX + totalRadius + 0.5f), (int) (centerY - totalRadius - 0.5f), BackgroundPaint);

        canvas.drawRect((int) (centerX - totalRadius - 0.5f), (int) (centerY + totalRadius + 0.5f), (int) (centerX + totalRadius + 0.5f), getHeight(), BackgroundPaint);

        // The magic center part


        canvas.drawPath(puncher, BackgroundPaint);

        if (animationPercent > 0f) {
            Puncher.setColor(0);
            Puncher.setAlpha((int) (0xCC * acceleratedAnimationPercent));
            canvas.drawCircle(centerX, centerY, circleRadius + 0.5f, Puncher);
        }

        CirclePaint.setColor(color);
        CirclePaint.setStrokeWidth(circleThickness);
        if (animationPercent > 0f) {
            int color = Color.argb(
                    (int) (0xFF * (1 - animationPercent)),
                    0xFF, 0xFF, 0xFF
            );
            GradientPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            CirclePaint.setAlpha((int) (Color.alpha(color) * (1 - animationPercent)));
            float scale = 1 - animationPercent * (1 - CircleAnimationScale);
            canvas.save();
            canvas.scale(scale, scale, centerX, centerY);
        }
        else {
            GradientPaint.setColorFilter(null);
        }
        canvas.drawCircle(centerX, centerY, circleRadius, CirclePaint);

        GradientPaint.setShader(innerGradient);
        canvas.drawCircle(centerX, centerY, circleRadius + innerGradientThickness, GradientPaint);

        GradientPaint.setShader(outerGradient);
        if (animationPercent > 0f) {
            int color = Color.argb(
                    (int) (0xFF * (1 - animationPercent)),
                    0xFF, 0xFF, 0xFF
            );
            GradientPaint.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.MULTIPLY));
            float scale = 1 - animationPercent * (1 - ShaderAnimationScale);
            canvas.restore();
            canvas.scale(scale, scale, centerX, centerY);
        }
        else {
            GradientPaint.setColorFilter(null);
        }
        canvas.drawCircle(centerX, centerY, circleRadius + innerGradientThickness + gradientGap + outerGradientThickness, GradientPaint);
//        canvas.restore();

    }

    public boolean isOpaque() {
        return false;
    }

    public HelpView(Context context) {
        super(context);
        initialize();
    }

    public HelpView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public HelpView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initialize();
    }
}
