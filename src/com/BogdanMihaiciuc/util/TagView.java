package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import com.BogdanMihaiciuc.receipt.ItemCollectionFragment;

import java.util.ArrayList;

public class TagView extends View {

    public final static int ColorModeFill = 0;
    public final static int ColorModeDashed = 1;

    final static Paint FillPaint;
    final static Paint EdgePaint;
    final static Paint DashedPaint;

    final static Paint DashedWhitePaint;
    final static Paint EdgeWhitePaint;

    final static int CircleRadiusDP = 8;
    final static int BorderSizeDP = 1;
    final static int StrideDP = 8;
    final static int PackedStrideDP = 6;
    final static float BorderOpacity = 0.33f;
    final static float WhiteBorderOpacity = 0.45f;
    final static int PlusThicknessDP = 2;
    final static int PlusLengthDP = 12;

    final static int DefaultMaximumTags = 4;

    final static int WhiteFill = Color.argb((int) (255 * 0.15f), 255, 255, 255);
    final static int WhiteBorderColor = Color.argb((int) (255 * WhiteBorderOpacity), 255, 255, 255);

    static {
        FillPaint = new Paint();
        FillPaint.setAntiAlias(true);
        EdgePaint = new Paint();
        EdgePaint.setARGB((int) (255 * BorderOpacity), 0, 0, 0);
        EdgePaint.setStyle(Paint.Style.STROKE);
        EdgePaint.setAntiAlias(true);
        DashedPaint = new Paint();
        DashedPaint.setARGB((int) (255 * BorderOpacity), 0, 0, 0);
        DashedPaint.setStyle(Paint.Style.STROKE);
        DashedPaint.setAntiAlias(true);

        DashedWhitePaint = new Paint(DashedPaint);
        DashedWhitePaint.setARGB((int) (255 * WhiteBorderOpacity), 255, 255, 255);

        EdgeWhitePaint = new Paint(EdgePaint);
        EdgeWhitePaint.setARGB((int) (255 * BorderOpacity), 255, 255, 255);
    }

    @SuppressWarnings("unused")
    public TagView(Context context){
        super(context);
        init();
    }

    @SuppressWarnings("unused")
    public TagView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        init();
    }

    @SuppressWarnings("unused")
    public TagView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    private float density;
    private int circleRadius;
    private float edgeRadius;
    private float borderSize;
    private int stride;
    private int plusThickness;
    private int plusLength;

    private DashPathEffect dashPathEffect;

    public void init() {
        density = getResources().getDisplayMetrics().density;
        circleRadius = (int) (density * CircleRadiusDP);
        borderSize = density * BorderSizeDP;
        if (borderSize < 1f) borderSize = 1f;
        edgeRadius = circleRadius - borderSize / 2f;
        stride = (int) (density * StrideDP);
        plusThickness = (int) (density * PlusThicknessDP);
        plusLength = (int) (density * PlusLengthDP);
        dashPathEffect = new DashPathEffect(new float[]{4f * density, 2f * density}, 1f);

        setColors(colors);
    }

    private ArrayList<Integer> colors = new ArrayList<Integer>();
    private boolean dashedCircleEnabled = true;
    private boolean plusEnabled = false;
    private int plusOpacity = Color.argb((int) (255 * BorderOpacity), 0, 0, 0);
    private int plusWhiteOpacity = Color.argb((int) (255 * BorderOpacity), 255, 255, 255);
    private float rotation = 0;
    private Path plusPath = new Path();
    private boolean whiteBordersEnabled = false;
    private int colorMode = ColorModeFill;

    private int maximumTags = DefaultMaximumTags;

    public void setRadius(float radius) {
        circleRadius = (int) (density * radius);
        edgeRadius = circleRadius - borderSize / 2f;
        invalidate();
    }

    public void setMaximumTags(int maximumTags) {
        this.maximumTags = maximumTags;
        if (colors.size() > maximumTags - 1) setDashedCircleEnabled(false);
        else setDashedCircleEnabled(true);
    }

    public void setColors(ArrayList<Integer> colors) {
        if (colors != null) {
            this.colors = colors;
            int colorsSize = colors.size();
            if (dashedCircleEnabled) colorsSize++;
            int compoundPadding = getPaddingLeft() + getPaddingRight();
            if (colorsSize > maximumTags - 1 && compoundPadding < 16 * density) {
                stride = (int) (density * PackedStrideDP);
            }
            else {
                stride = (int) (density * StrideDP);
            }
            invalidate();
        }
    }

    public void setTags(ArrayList<com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag> tags) {
        colors.clear();
        for (ItemCollectionFragment.Tag tag : tags) {
            colors.add(tag.color);
        }
        if (colors.size() > maximumTags - 1) setDashedCircleEnabled(false);
        else setDashedCircleEnabled(true);
        setColors(colors);
    }

    public void setColor(int color) {
        if (color != -1) {
            colors.clear();
            colors.add(color);
            setColors(colors);
            setDashedCircleEnabled(false);
        }
        else {
            colors.clear();
            setDashedCircleEnabled(true);
            setColors(colors);
        }
    }

    public void setRotation(float rotation) {
        if (Build.VERSION.SDK_INT >= 19) {
            this.rotation = rotation;
            invalidate();
        }
        else {
            super.setRotation(rotation);
        }
    }

    public float getRotation() {
        if (Build.VERSION.SDK_INT >= 19) {
            return rotation;
        }
        else {
            return super.getRotation();
        }
    }

    public ArrayList<Integer> getColors() {
        return colors;
    }

    public void setPlusEnabled(boolean enabled) {
        plusEnabled = enabled;
        invalidate();
    }

    public void setPlusOpacity(float opacity) {
        plusOpacity = Color.argb((int) (opacity * 255 * BorderOpacity), 0, 0, 0);
        plusWhiteOpacity = Color.argb((int) (opacity * 255 * BorderOpacity), 255, 255, 255);
        invalidate();
    }

    public void setDashedCircleEnabled(boolean enabled) {
        dashedCircleEnabled = enabled;
        int colorsSize = colors.size();
        if (dashedCircleEnabled) colorsSize++;
        int compoundPadding = getPaddingLeft() + getPaddingRight();
        if (colorsSize > 3 && compoundPadding < 16 * density) {
            stride = (int) (density * PackedStrideDP);
        }
        else {
            stride = (int) (density * StrideDP);
        }
        invalidate();
    }

    public void setColorMode(int mode) {
        colorMode = mode;
    }

    public boolean isDashedCircleEnabled() {
        return dashedCircleEnabled;
    }

    public boolean isWhiteBordersEnabled() {
        return whiteBordersEnabled;
    }

    public void setWhiteBordersEnabled(boolean whiteBordersEnabled) {
        this.whiteBordersEnabled = whiteBordersEnabled;
        invalidate();
    }

    public int getCircleStride() {
        return stride;
    }

    Path dashedCirclePath = new Path();
    Matrix dashedCircleMatrix = new Matrix();

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int centerY = getHeight() / 2;
        int centerX = getWidth() / 2;

        int colorCount = colors.size();
        colorCount = dashedCircleEnabled ? colorCount + 1 : colorCount;

        int x = centerX + (stride / 2)*(colorCount - 1);

        if (whiteBordersEnabled) {
            if (dashedCircleEnabled) {
                DashedWhitePaint.setStrokeWidth(borderSize);
                DashedWhitePaint.setPathEffect(dashPathEffect);
                dashedCirclePath.rewind();
                dashedCirclePath.addCircle(x, centerY, edgeRadius, Path.Direction.CW);
                dashedCircleMatrix.setRotate(rotation, x, centerY);
                dashedCirclePath.transform(dashedCircleMatrix);
                canvas.drawPath(dashedCirclePath, DashedWhitePaint);
//                canvas.drawCircle(x, centerY, edgeRadius, DashedWhitePaint);
                x -= stride;
            }
            for (Integer color : colors) {
                FillPaint.setColor(color);
                canvas.drawCircle(x, centerY, circleRadius, FillPaint);
                EdgeWhitePaint.setStrokeWidth(borderSize);
                canvas.drawCircle(x, centerY, edgeRadius, EdgeWhitePaint);
                x -= stride;
            }

            if (plusEnabled) {
                FillPaint.setColor(dashedCircleEnabled ? WhiteBorderColor : plusOpacity);
                plusPath.rewind();
                dashedCircleMatrix.setRotate(rotation, centerX, centerY);
                plusPath.addRect(centerX - plusThickness / 2, centerY - plusLength / 2, centerX + plusThickness / 2, centerY + plusLength / 2, Path.Direction.CW);
                plusPath.addRect(centerX - plusLength / 2, centerY - plusThickness / 2, centerX + plusLength / 2, centerY + plusThickness / 2, Path.Direction.CW);
                if (Build.VERSION.SDK_INT >= 19) plusPath.transform(dashedCircleMatrix);
                canvas.drawPath(plusPath, FillPaint);
            }
        }
        else {
            if (dashedCircleEnabled) {
                DashedPaint.setStrokeWidth(borderSize);
                DashedPaint.setPathEffect(dashPathEffect);
                dashedCirclePath.rewind();
                dashedCirclePath.addCircle(x, centerY, edgeRadius, Path.Direction.CW);
                dashedCircleMatrix.setRotate(rotation, x, centerY);
                if (Build.VERSION.SDK_INT >= 19) dashedCirclePath.transform(dashedCircleMatrix);
                canvas.drawPath(dashedCirclePath, DashedPaint);
//                canvas.drawCircle(x, centerY, edgeRadius, DashedPaint);
                x -= stride;
            }
            for (Integer color : colors) {
                if (colorMode == ColorModeFill) {
                    FillPaint.setColor(color);
                    canvas.drawCircle(x, centerY, circleRadius, FillPaint);
                    EdgePaint.setStrokeWidth(borderSize);
                    EdgePaint.setARGB((int) (255 * BorderOpacity), 0, 0, 0);
                    canvas.drawCircle(x, centerY, edgeRadius, EdgePaint);
                }
                else {
                    EdgePaint.setStrokeWidth(borderSize);
                    EdgePaint.setColor(color);
                    canvas.drawCircle(x, centerY, edgeRadius, EdgePaint);
                }
                x -= stride;
            }

            if (plusEnabled) {
                FillPaint.setColor(plusOpacity);
                plusPath.rewind();
                dashedCircleMatrix.setRotate(rotation, centerX, centerY);
                plusPath.addRect(centerX - plusThickness / 2, centerY - plusLength / 2, centerX + plusThickness / 2, centerY + plusLength / 2, Path.Direction.CW);
                plusPath.addRect(centerX - plusLength / 2, centerY - plusThickness / 2, centerX + plusLength / 2, centerY + plusThickness / 2, Path.Direction.CW);
                if (Build.VERSION.SDK_INT >= 19) plusPath.transform(dashedCircleMatrix);
                canvas.drawPath(plusPath, FillPaint);
            }
        }
//        invalidate();
    }
}
