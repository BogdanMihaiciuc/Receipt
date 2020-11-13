package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import com.BogdanMihaiciuc.util.Utils;

/**
 * Created by Bogdan on 11/27/14.
 */
public class GraphStackingDrawable extends Drawable {

    final static float DarkPointsStandardDP[] = {5.5f, 23.5f,
                                                 13f, 12.5f,
                                                 21f, 20.5f,
                                                 26.5f, 8f};
    final static float LightPointsStandardDP[] = {5.5f, 11f,
                                                  13f, 20.5f,
                                                  21f, 13.5f,
                                                  26.5f, 23.5f};
    final static float DarkPointsStackedDP[] = {5.5f, 26.5f,
                                                13f, 15.5f,
                                                21f, 23.5f,
                                                26.5f, 11f};
    final static float LightPointsStackedDP[] = {5.5f, 14f,
                                                13f, 6.5f,
                                                21f, 13.5f,
                                                26.5f, 5.5f};

    final static float PointSizeDP = 2f;
    final static float LineThicknessDP = 1.5f;
    final static int IntrinsicSizeDP = 32;

    final static int DarkColor = Utils.overlayColors(0xFFFFFFFF, Utils.transparentColor(0.75f, 0));
    final static int LightColor = Utils.overlayColors(0xFFFFFFFF, Utils.transparentColor(0.33f, 0));

    final static Paint LinePaint;

    static {
        LinePaint = new Paint();
        LinePaint.setAntiAlias(true);
        LinePaint.setStyle(Paint.Style.FILL);
    }

    public final static int ModeStandard = 0;
    public final static int ModeStacked = 1;

    private Utils.DPTranslator pixels;

    private int mode = ModeStandard;

    private float pointSize;
    private float lineThickness;
    private int intrinsicSize;
    private float[] darkPointMap;
    private float[] lightPointMap;
    private int startPointX, startPointY;

    private ValueAnimator modeAnimator;

    public GraphStackingDrawable(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        pixels = new Utils.DPTranslator(density);

        pointSize = PointSizeDP * density;
        lineThickness = LineThicknessDP * density;
        intrinsicSize = pixels.get(IntrinsicSizeDP);

        darkPointMap = Utils.obtainScaledArray(DarkPointsStandardDP, density);
        lightPointMap = Utils.obtainScaledArray(LightPointsStandardDP, density);
    }

    public void setMode(int mode, final boolean animated) {
        if (this.mode == mode) return;
        this.mode = mode;

        if (!animated) {
            if (mode == ModeStandard) {
                darkPointMap = Utils.obtainScaledArray(DarkPointsStandardDP, pixels.getDensity());
                lightPointMap = Utils.obtainScaledArray(LightPointsStandardDP, pixels.getDensity());
            }
            else {
                darkPointMap = Utils.obtainScaledArray(DarkPointsStandardDP, pixels.getDensity());
                lightPointMap = Utils.obtainScaledArray(LightPointsStandardDP, pixels.getDensity());
            }

            invalidateSelf();
        }
        else {

            final float[] StartingDarkPointMap = new float[darkPointMap.length];
            System.arraycopy(darkPointMap, 0, StartingDarkPointMap, 0, darkPointMap.length);
            final float[] StartingLightPointMap = new float[lightPointMap.length];
            System.arraycopy(lightPointMap, 0, StartingLightPointMap, 0, lightPointMap.length);

            final float[] TargetDarkPointMap = mode == ModeStandard ? DarkPointsStandardDP : DarkPointsStackedDP;
            final float[] TargetLightPointMap = mode == ModeStandard ? LightPointsStandardDP : LightPointsStackedDP;

            if (modeAnimator != null) modeAnimator.cancel();

            modeAnimator = ValueAnimator.ofFloat(0f, 1f);
            modeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float fraction = animation.getAnimatedFraction();
                    int length = darkPointMap.length;

                    for (int i = 0; i < length; i++) {
                        darkPointMap[i] = Utils.interpolateValues(fraction, StartingDarkPointMap[i], TargetDarkPointMap[i] * pixels.getDensity());
                        lightPointMap[i] = Utils.interpolateValues(fraction, StartingLightPointMap[i], TargetLightPointMap[i] * pixels.getDensity());
                    }

                    invalidateSelf();
                }
            });
            modeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    modeAnimator = null;
                }
            });
            modeAnimator.setDuration(300).setInterpolator(new Utils.FrictionInterpolator(1.5f));
            modeAnimator.start();

        }
    }

    public void onBoundsChange(Rect bounds) {
        startPointX = bounds.width() / 2 - intrinsicSize / 2;
        startPointY = bounds.height() / 2 - intrinsicSize / 2;
    }

    @Override
    public void draw(Canvas canvas) {

        int length = darkPointMap.length / 2;

        LinePaint.setStrokeWidth(lineThickness);

        LinePaint.setColor(LightColor);

        for (int i = 0; i < length; i++) {
            canvas.drawCircle(startPointX + lightPointMap[i * 2], startPointY + lightPointMap[i * 2 + 1], pointSize, LinePaint);

            if (i != 0) {
                canvas.drawLine(startPointX + lightPointMap[(i - 1) * 2], startPointY + lightPointMap[(i - 1) * 2 + 1],
                        startPointX + lightPointMap[i * 2], startPointY + lightPointMap[i * 2 + 1], LinePaint);
            }
        }

        LinePaint.setColor(DarkColor);

        for (int i = 0; i < length; i++) {
            canvas.drawCircle(startPointX + darkPointMap[i * 2], startPointY + darkPointMap[i * 2 + 1], pointSize, LinePaint);

            if (i != 0) {
                canvas.drawLine(startPointX + darkPointMap[(i - 1) * 2], startPointY + darkPointMap[(i - 1) * 2 + 1],
                        startPointX + darkPointMap[i * 2], startPointY + darkPointMap[i * 2 + 1], LinePaint);
            }
        }

    }

    public int getIntrinsicWidth() {
        return intrinsicSize;
    }

    public int getIntrinsicHeight() {
        return intrinsicSize;
    }

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter cf) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
