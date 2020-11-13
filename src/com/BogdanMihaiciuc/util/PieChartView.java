package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;


public class PieChartView extends View {

    final static String TAG = PieChartView.class.toString();

    final static Object DeleteTarget = new Object();

    final static boolean DEBUG_TEST = true;

    final static Paint DashedPaint;
    final static Paint SelectionPaint;
    final static Paint FillPaint;
    final static Paint LineEraser;
    final static Paint Burner;

    final static PorterDuffXfermode ClearXfermode = new PorterDuffXfermode(PorterDuff.Mode.CLEAR);

    final static float EdgeThicknessDP = 38;
    final static float SelectionPaddingDP = 16;
    final static float SelectionSizeDP = 4;
    final static float BorderOpacity = 0.15f;
    final static float ShadowOpacity = 0.07f;
    final static float DegreesStart = -90;
    final static float DegreesStartCartesian = 90;
    final static float DegreesCompletionOffset = 135;
    final static float GapDP = 4;

    final static long TransactionAnimationDuration = 600;

    final static int MinimumSlicePercentage = 5;

    static {
        FillPaint = new Paint();
        FillPaint.setAntiAlias(true);
        FillPaint.setStyle(Paint.Style.STROKE);

        SelectionPaint = new Paint();
        SelectionPaint.setAntiAlias(true);
        SelectionPaint.setStyle(Paint.Style.STROKE);

        DashedPaint = new Paint();
        DashedPaint.setARGB((int) (255 * BorderOpacity), 0, 0, 0);
        DashedPaint.setStyle(Paint.Style.STROKE);
        DashedPaint.setAntiAlias(true);

        LineEraser = new Paint();
        LineEraser.setARGB(255, 255, 255, 255);
        LineEraser.setStyle(Paint.Style.STROKE);
        LineEraser.setAntiAlias(true);

        Burner = new Paint();
        Burner.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.OVERLAY));
        Burner.setARGB((int) (255 * ShadowOpacity), 0, 0, 0);
        Burner.setStyle(Paint.Style.STROKE);
        Burner.setAntiAlias(true);
    }

    public static void setMultiplyModeEnabled(boolean enabled) {
        if (enabled) {
            FillPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            SelectionPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            DashedPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
            LineEraser.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
        }
        else {
            FillPaint.setXfermode(null);
            SelectionPaint.setXfermode(null);
            DashedPaint.setXfermode(null);
            LineEraser.setXfermode(null);
        }
    }

    public boolean areSlicesSelectable() {
        return slicesSelectable;
    }

    public void setSlicesSelectable(boolean slicesSelectable) {
        this.slicesSelectable = slicesSelectable;

        if (!slicesSelectable) {
            for (Slice slice : slices) {
                slice.selectionPercentage = 0f;
                slice.selected = false;
            }
        }
    }

    static class Slice implements Comparable<Slice> {
        long amount;
        int color;

        float percentage;

        float animatedPercentage;
        float animatedAlpha;
        boolean deleted;
        ValueAnimator attachedAnimator;

        boolean selected;
        float selectionPercentage;
        ValueAnimator attachedSelectionAnimator;

        @Override
        public int compareTo(Slice o) {
            if (o.color == -1 && color != -1)
                return 1;
            if (color == -1 && o.color != -1)
                return -1;
            return (int) (o.amount - amount);
        }
    }

    private DashPathEffect dashPathEffect;
    private DisplayMetrics metrics;
    private float density;

    private int eraserColor = 0xFFFFFFFF;
    private int gap;
    private float sideGap;
    private int thickness;
    private float halfThickness;

    private float selectionPadding;
    private float selectionSize;

    private float canvasRadius;
    private float innerRadius;

    private float completion = 1f;

    private long total;
    private long animatedTotal;
    private ValueAnimator attachedTotalAnimator;

    private ArrayList<Slice> slices = new ArrayList<Slice>();
    private ArrayList<Slice> pendingSlices;

    private ArrayList<Animator> runningAnimations = new ArrayList<Animator>();

    private RectF drawingBounds = new RectF();
    private RectF reducedDrawingBounds = new RectF();
    private RectF selectionBounds = new RectF();
    private PointF centerPoint = new PointF();

    private boolean clearModeEnabled;

    private boolean slicesSelectable = true;

    public PieChartView(Context context) {
        super(context);
        init();
    }

    public PieChartView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PieChartView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    void init() {
        metrics = getContext().getResources().getDisplayMetrics();
        density = metrics.density;

        gap = (int) (GapDP * density);
        sideGap = ((float) gap) /2f;

        dashPathEffect = new DashPathEffect(new float[]{8f * density, 4f * density}, 1f);

        selectionPadding = SelectionPaddingDP * density;
        selectionSize = SelectionSizeDP * density;

        thickness = (int) (EdgeThicknessDP * density);
        thickness =  2 * thickness / 3;
        halfThickness = (float) thickness / 2f;
    }

    public void setClearModeEnabled(boolean enabled) {
        clearModeEnabled = enabled;
        invalidate();
    }

    public void setThickness(float thickness) {
        this.thickness = (int) thickness;
        halfThickness = thickness / 2f;
    }

    public void setCompletion(float completion) {
        this.completion = completion;

        invalidate();
    }

    public float getCompletion() {
        return completion;
    }

    public void clear() {
        if (pendingSlices != null) {
            pendingSlices.clear();
            return;
        }

        slices.clear();
        total = 0;

        invalidate();

    }

    public void startTransaction() {
        pendingSlices = new ArrayList<Slice>(slices);
    }

    public void endTransaction() {
        endTransactionAnimated(true);
    }

    public void endTransactionAnimated(boolean animated) {
        if (pendingSlices == null) return;

        total = 0;
        for (Slice slice : pendingSlices) {
            total += slice.amount;
        }

        if (!animated) {
            endTransactionInstantly();
            return;
        }

        for (final Slice slice : slices) {
            int pendingIndex = -1;
            for (int i = 0, size = pendingSlices.size(); i < size; i++) {
                if (slice.color == pendingSlices.get(i).color) {
                    pendingIndex = i;
                    break;
                }
            }

            if (pendingIndex != -1) {
                slice.amount = pendingSlices.get(pendingIndex).amount;
                pendingSlices.remove(pendingIndex);
                slice.animatedPercentage = slice.percentage;
                slice.percentage = (float) (((double) slice.amount) / ((double) total)) * 360f;

                if (slice.deleted) {
                    if (slice.attachedAnimator != null) {
                        slice.attachedAnimator.removeAllListeners();
                        runningAnimations.remove(slice.attachedAnimator);
                        slice.attachedAnimator.cancel();
                    }
                    slice.deleted = false;
                    slice.animatedAlpha = 0f;
                }
                else {
                    if (slice.attachedAnimator != null) {
                        slice.attachedAnimator.cancel();
                    }
                }

                final float AnimatedPercentage = slice.animatedPercentage;

                slice.attachedAnimator = ValueAnimator.ofFloat(0, 1);
                slice.attachedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        slice.animatedPercentage = AnimatedPercentage + (slice.percentage - AnimatedPercentage) * valueAnimator.getAnimatedFraction();

                        invalidate();
                    }
                });

                slice.attachedAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        //                        slice.animatedPercentage = slice.percentage;
                        runningAnimations.remove(animation);
                        slice.attachedAnimator = null;
                    }
                });

                slice.attachedAnimator.setDuration(TransactionAnimationDuration);
                slice.attachedAnimator.setInterpolator(new Utils.FrictionInterpolator(1f));
                slice.attachedAnimator.start();

                runningAnimations.add(slice.attachedAnimator);
            }
            else {
                if (slice.deleted) {
                    if (slice.attachedAnimator != null) {
                        slice.attachedAnimator.removeAllListeners();
                        runningAnimations.remove(slice.attachedAnimator);
                        slice.attachedAnimator.cancel();
                    }
                    slice.animatedAlpha = 0f;
                }
                else {
                    if (slice.attachedAnimator != null) {
                        slice.attachedAnimator.cancel();
                    }
                }

                // This slice is about to be removed
                slice.percentage = slice.animatedPercentage;
                slice.deleted = true;

                slice.attachedAnimator = ValueAnimator.ofFloat(0, 1);
                slice.attachedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        slice.animatedPercentage = slice.percentage * (1 - valueAnimator.getAnimatedFraction());
                        slice.animatedAlpha = valueAnimator.getAnimatedFraction();

                        invalidate();
                    }
                });

                slice.attachedAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        //                        slice.animatedPercentage = slice.percentage;
                        slices.remove(slice);
                        runningAnimations.remove(animation);
                        slice.attachedAnimator = null;
                    }
                });

                slice.attachedAnimator.setDuration(TransactionAnimationDuration);
                slice.attachedAnimator.setInterpolator(new Utils.FrictionInterpolator(1f));
                slice.attachedAnimator.start();

                runningAnimations.add(slice.attachedAnimator);
            }

        }

        // The remaining slices have been added during the transaction
        for (final Slice slice : pendingSlices) {
            slices.add(slice);

            // This slice is about to be removed
            slice.animatedPercentage = 0f;
            slice.percentage = (float) (((double) slice.amount) / ((double) total)) * 360f;
            slice.deleted = true;

            slice.attachedAnimator = ValueAnimator.ofFloat(0, 1);
            slice.attachedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    slice.animatedPercentage = slice.percentage * (valueAnimator.getAnimatedFraction());
                    slice.animatedAlpha = 1 - valueAnimator.getAnimatedFraction();

                    invalidate();
                }
            });

            slice.attachedAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    //                        slice.animatedPercentage = slice.percentage;
                    runningAnimations.remove(animation);
                    slice.attachedAnimator = null;
                }
            });

            slice.attachedAnimator.setDuration(TransactionAnimationDuration);
            slice.attachedAnimator.setInterpolator(new Utils.FrictionInterpolator(1f));
            slice.attachedAnimator.start();

            runningAnimations.add(slice.attachedAnimator);

        }

        pendingSlices = null;
    }

    protected void endTransactionInstantly() {
        slices = new ArrayList<Slice>(pendingSlices);
        pendingSlices = null;
        for (Slice slice : slices) {
            slice.animatedAlpha = 0f;
            slice.percentage = (float) (((double) slice.amount) / ((double) total)) * 360f;
            slice.animatedPercentage = slice.percentage;
        }
        invalidate();
    }

    public void removeColor(int color, boolean animated) {
        if (pendingSlices != null) {
            for (Slice slice : pendingSlices) {
                if (slice.color == color) {
                    pendingSlices.remove(slice);
                    return;
                }
            }

            return;
        }

        Slice targetSlice = null;
        for (Slice slice : slices) {
            if (slice.color == color) {
                targetSlice = slice;
                break;
            }
        }

        if (targetSlice == null) return;

        if (!animated) {
            total -= targetSlice.amount;
            animatedTotal = total;

            if (targetSlice.attachedAnimator != null) {
                targetSlice.attachedAnimator.cancel();
            }
            slices.remove(targetSlice);

            for (Slice slice : slices) {
                slice.percentage = (float) (((double) slice.amount) / ((double) total)) * 360f;
                slice.animatedPercentage = slice.percentage;
                if (slice.attachedAnimator != null) {
                    slice.attachedAnimator.cancel();
                }
            }

            invalidate();
        }
        else {

            if (attachedTotalAnimator != null)
                attachedTotalAnimator.cancel();

            total -= targetSlice.amount;
            animatedTotal = total;

            for (final Slice slice : slices) {
                slice.animatedPercentage = slice.percentage;
                slice.percentage = (float) (((double) slice.amount) / ((double) total)) * 360f;
                if (slice.attachedAnimator != null && !slice.deleted) {
                    slice.attachedAnimator.cancel();
                }

                if (slice != targetSlice) {
                    final float AnimatedPercentage = slice.animatedPercentage;

                    slice.attachedAnimator = ValueAnimator.ofFloat(0, 1);
                    slice.attachedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator valueAnimator) {
                            slice.animatedPercentage = AnimatedPercentage + (slice.percentage - AnimatedPercentage) * valueAnimator.getAnimatedFraction();

                            invalidate();
                        }
                    });

                    slice.attachedAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            //                        slice.animatedPercentage = slice.percentage;
                            runningAnimations.remove(animation);
                            slice.attachedAnimator = null;
                        }
                    });

                    slice.attachedAnimator.setInterpolator(new Utils.FrictionInterpolator(1f));
                    slice.attachedAnimator.start();

                    runningAnimations.add(slice.attachedAnimator);
                }
                else {
                    slice.percentage = slice.animatedPercentage;
                    slice.deleted = true;

                    slice.attachedAnimator = ValueAnimator.ofFloat(0, 1);
                    slice.attachedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator valueAnimator) {
                            slice.animatedPercentage = slice.percentage * (1 - valueAnimator.getAnimatedFraction());
                            slice.animatedAlpha = valueAnimator.getAnimatedFraction();

                            invalidate();
                        }
                    });

                    slice.attachedAnimator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            //                        slice.animatedPercentage = slice.percentage;
                            slices.remove(slice);
                            runningAnimations.remove(animation);
                            slice.attachedAnimator = null;
                        }
                    });

                    slice.attachedAnimator.setInterpolator(new Utils.FrictionInterpolator(1f));
                    slice.attachedAnimator.start();

                    runningAnimations.add(slice.attachedAnimator);
                }
            }

        }

    }

    public void addColorWithAmount(int color, long amount) {
        if (pendingSlices != null) {
            boolean alreadyContains = false;
            for (Slice slice : pendingSlices) {
                if (slice.color == color) {
                    alreadyContains = true;
                    slice.amount = amount;
                    break;
                }
            }

            if (!alreadyContains) {
                Slice newSlice = new Slice();
                total += amount;

                newSlice.amount = amount;
                newSlice.color = color;

                pendingSlices.add(newSlice);
            }

            return;
        }

        boolean alreadyContains = false;
        for (Slice slice : slices) {
            if (slice.color == color) {
                alreadyContains = true;
                slice.amount = amount;
                break;
            }
        }

        if (!alreadyContains) {
            Slice newSlice = new Slice();
            total += amount;

            newSlice.amount = amount;
            newSlice.color = color;

            slices.add(newSlice);
        }

        for (final Slice slice : slices) {
            slice.animatedPercentage = slice.percentage;
            slice.percentage = (float) (((double) slice.amount) / ((double) total)) * 360f;
            final float AnimatedPercentage = slice.animatedPercentage;

            if (slice.attachedAnimator != null) {
                if (slice.deleted) {
                    if (slice.color == color) {
                        slice.attachedAnimator.removeAllListeners();
                        slice.attachedAnimator.cancel();
                        slice.deleted = false;
                        slice.animatedAlpha = 0f;
                    }
                }
                else {
                    slice.attachedAnimator.cancel();
                }
            }

            if (!slice.deleted) {
                slice.attachedAnimator = ValueAnimator.ofFloat(0, 1);
                slice.attachedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        slice.animatedPercentage = AnimatedPercentage + (slice.percentage - AnimatedPercentage) * valueAnimator.getAnimatedFraction();

                        invalidate();
                    }
                });

                slice.attachedAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        //                        slice.animatedPercentage = slice.percentage;
                        runningAnimations.remove(animation);
                        slice.attachedAnimator = null;
                    }
                });

                slice.attachedAnimator.setInterpolator(new Utils.FrictionInterpolator(1f));
                slice.attachedAnimator.start();

                runningAnimations.add(slice.attachedAnimator);
            }
        }

        Collections.sort(slices);

        invalidate();
    }

    public void setSelection(int color) {
        for (Slice slice : slices) {
            if (slice.color == color) {

                slice.selected = true;
                slice.selectionPercentage = 1f;
                invalidate();

                return;
            }
        }
    }

    public void setEraserColor(int color) {
        eraserColor = color;

        invalidate();
    }

    private int centerX, centerY;
    private int rawCenterX, rawCenterY;
    private int[] locationOnScreen = new int[2];

    public void onSizeChanged (int width, int height, int oldWidth, int oldHeight) {
        getLocationOnScreen(locationOnScreen);

        centerX = width / 2;
        centerY = height / 2;

        rawCenterX = locationOnScreen[0] + centerX;
        rawCenterY = locationOnScreen[1] + centerY;
    }

    public float getAngleOfRawPoint(float x, float y) {

        double displacedX = x - rawCenterX;
        double displacedY = y - rawCenterY;

        double angle = Math.atan2(displacedY, displacedX);

        return (float) Math.toDegrees(angle);
    }

    static boolean isValueBetween(float value, float min, float max) {
        if (min < -1) min = 360 + min;
        if (min > 361) min = min - 360;
        if (max < -1) max = 360 + max;
        if (max > 361) max = max - 360;
        return value > min && value < max;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!super.onTouchEvent(event) && slicesSelectable) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                float angle = getAngleOfRawPoint(event.getRawX(), event.getRawY());

                // Get the slice corresponding to this angle
                float angleDisplacement = DegreesStart + completion * DegreesCompletionOffset;

                angle = angle - angleDisplacement;

                if (angle < 0) angle = 360 + angle;
                if (angle > 360) angle = angle - 360;

                float currentAngle = 0;

                // Find the selected slice and see if it needs to be unselected
                for (final Slice slice : slices) {
                    if (slice.selected) {
                        if (isValueBetween(angle, currentAngle, currentAngle + slice.percentage)) {
                            // Nothing needs to be done
                            return true;
                        }
                        else {
                            // The slice needs to be deselected
                            slice.selected = false;

                            if (slice.attachedSelectionAnimator != null)
                                slice.attachedSelectionAnimator.cancel();

                            ValueAnimator deselectAnimator = ValueAnimator.ofFloat(1f, 0f);
                            slice.attachedSelectionAnimator = deselectAnimator;

                            deselectAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                                @Override
                                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                    slice.selectionPercentage = 1 - valueAnimator.getAnimatedFraction();
                                    invalidate();
                                }
                            });
                            deselectAnimator.addListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    runningAnimations.remove(slice.attachedSelectionAnimator);
                                    slice.attachedSelectionAnimator = null;
                                }
                            });
                            deselectAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));

                            deselectAnimator.start();
                            runningAnimations.add(deselectAnimator);

                            break;
                        }
                    }
                    currentAngle += slice.percentage;
                }

                currentAngle = 0;

                for (final Slice slice : slices) {
                    if (isValueBetween(angle, currentAngle, currentAngle + slice.percentage) && !slice.selected) {

                        slice.selected = true;

                        if (slice.attachedSelectionAnimator != null)
                            slice.attachedSelectionAnimator.cancel();

                        ValueAnimator selectAnimator = ValueAnimator.ofFloat(1f, 0f);
                        slice.attachedSelectionAnimator = selectAnimator;

                        selectAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                slice.selectionPercentage = valueAnimator.getAnimatedFraction();
                                invalidate();
                            }
                        });
                        selectAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                runningAnimations.remove(slice.attachedSelectionAnimator);
                                slice.attachedSelectionAnimator = null;
                            }
                        });
                        selectAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));

                        selectAnimator.start();
                        runningAnimations.add(selectAnimator);

                        return true;

                    }

                    currentAngle += slice.percentage;
                }

            }
        }
        return true;
    }

    private void setSelectionOval(Canvas canvas, float percentage, boolean fromOutside) {

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        float selectionStart = fromOutside ? percentage * selectionPadding - selectionSize + (1 - percentage) * selectionSize
                : (1 - percentage) * selectionPadding - selectionSize + selectionPadding;

        if (width > height) {
            float left = (width - height) / 2f;

            selectionBounds.set(left + selectionStart,
                    selectionStart,
                    left + height - selectionStart,
                    height - selectionStart);

        }
        else {
            float top = (height - width) / 2f;

            selectionBounds.set(selectionStart,
                    top + selectionStart,
                    width - selectionStart,
                    top + width - selectionStart);
        }

    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = canvas.getWidth();
        int height = canvas.getHeight();

        float padding = sideGap + selectionPadding;

        if (width > height) {
            float left = (width - height) / 2f;
            drawingBounds.set(left + padding, padding, left + height - padding, height - padding);

            reducedDrawingBounds.set(left + padding + halfThickness, padding + halfThickness,
                    left + height - padding - halfThickness, height - padding - halfThickness);

            canvasRadius = drawingBounds.height() / 2f;
        }
        else {
            float top = (height - width) / 2f;
            drawingBounds.set(padding, top + padding, width - padding, top + width - padding);

            reducedDrawingBounds.set(padding + halfThickness, top + padding + halfThickness,
                    width - padding - halfThickness, top + width - padding - halfThickness);

            canvasRadius = drawingBounds.height() / 2f;
        }

        centerPoint.set(drawingBounds.centerX(), drawingBounds.centerY());

        innerRadius = canvasRadius - thickness;

        float degreesOffset = DegreesCompletionOffset * completion;

        float currentDegrees = DegreesStart + degreesOffset;

        Paint usedPaint;

        FillPaint.setStrokeWidth(thickness);
        SelectionPaint.setStrokeWidth(selectionSize);
        DashedPaint.setStrokeWidth(sideGap);
        DashedPaint.setPathEffect(dashPathEffect);

        for (Slice slice : slices) {
            if (slice.color == -1) {
//                usedPaint = DashedPaint;

//                // Start drawing an arc
//                canvas.drawArc(drawingBounds, currentDegrees, slice.animatedPercentage * completion, false, usedPaint);
//                canvas.drawArc(selectionBounds, currentDegrees, slice.animatedPercentage * completion, false, usedPaint);
            }
            else {
                usedPaint = FillPaint;
                usedPaint.setColor(slice.color);
                if (slice.animatedAlpha != 0f) {
                    usedPaint.setAlpha((int) (255 * (1 - slice.animatedAlpha)));
                }

                // Start drawing an arc
                canvas.drawArc(reducedDrawingBounds, currentDegrees, slice.animatedPercentage * completion, false, usedPaint);

            }

            if (slice.selectionPercentage > 0) {
                SelectionPaint.setColor(slice.color == -1 ? 0xFFBABEBE : slice.color);
                SelectionPaint.setAlpha((int) (255 * slice.selectionPercentage));

                setSelectionOval(canvas, slice.selectionPercentage, !slice.selected);

                // Start drawing an arc
                canvas.drawArc(selectionBounds, currentDegrees + density, slice.animatedPercentage * completion - 2f * density, false, SelectionPaint);

                if (slice.selected && slice.selectionPercentage < 1) {

                    SelectionPaint.setAlpha((int) (64 * slice.selectionPercentage));

                    setSelectionOval(canvas, 0.5f + slice.selectionPercentage / 2f, true);

                    // Start drawing an arc
                    canvas.drawArc(selectionBounds, currentDegrees + density, slice.animatedPercentage * completion - 2f * density, false, SelectionPaint);
                }

            }

            // Move the next arc by gap amount
            currentDegrees += slice.animatedPercentage * completion;
        }

        DashedPaint.setStrokeWidth(density);
        DashedPaint.setPathEffect(null);
        Burner.setStrokeWidth(2.3f * thickness / 3);

        canvas.drawCircle(centerPoint.x, centerPoint.y, canvasRadius - density / 2, DashedPaint);
        canvas.drawCircle(centerPoint.x, centerPoint.y, innerRadius + density / 2, DashedPaint);
//        canvas.drawCircle(centerPoint.x, centerPoint.y, innerRadius + (2.3f * thickness / 3) / 2, Burner);

        currentDegrees = DegreesStartCartesian + degreesOffset;
        LineEraser.setStrokeWidth(gap);

        if (!clearModeEnabled) {
            LineEraser.setColor(eraserColor);
            LineEraser.setXfermode(null);
        }
        else {
            LineEraser.setColor(0);
            LineEraser.setXfermode(ClearXfermode);
            LineEraser.setAntiAlias(true);
        }

        DashedPaint.setStrokeWidth(gap + 2 * density);

        canvasRadius = canvasRadius + 1;

        for (Slice slice : slices) {

            canvas.drawLine(centerPoint.x - innerRadius * (float) Math.cos(Math.toRadians(currentDegrees)),
                    centerPoint.y + innerRadius * (float) Math.sin(- Math.toRadians(currentDegrees)),
                    centerPoint.x - (canvasRadius - density) * (float) Math.cos(Math.toRadians(currentDegrees)),
                    centerPoint.y + (canvasRadius - density) * (float) Math.sin(- Math.toRadians(currentDegrees)),
                    DashedPaint);

            // The eraser line moves from the start degrees by slice.percentage amount
            canvas.drawLine(centerPoint.x, centerPoint.y,
                    centerPoint.x - canvasRadius * (float) Math.cos(Math.toRadians(currentDegrees)),
                    centerPoint.y + canvasRadius * (float) Math.sin(- Math.toRadians(currentDegrees)),
                    LineEraser);

            // Move the next arc by gap amount
            currentDegrees += slice.animatedPercentage * completion;
        }

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        while (runningAnimations.size() > 0) {
            // Animations remove themselves from the running animations array when completed automatically
            runningAnimations.get(0).end();
        }
    }

}
