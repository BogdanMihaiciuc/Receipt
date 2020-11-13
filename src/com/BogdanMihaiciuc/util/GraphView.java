package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.os.Handler;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;

//import com.BogdanMihaiciuc.receipt.ListenableHorizontalScrollView;
//import com.BogdanMihaiciuc.receipt.R;
//import com.BogdanMihaiciuc.receipt.Receipt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GraphView extends View {

    final static String TAG = GraphView.class.getName();

    // When offscreen draw is enabled, the graphview will always draw the whole graph, even if it's not visible
    // This results in fewer invalidations but more time spent on each redraw
    final static boolean ENABLE_OFFSCREEN_DRAW = true;
    final static boolean ALLOW_OVERLAY_FILL = false;
    final static boolean ALTERNATE_POINTS = true;

    final static boolean BITMAP_BACKED_DRAW = false;

    public interface OnSelectionChangedListener {
        public void onSelectionChanged(Object object, int index);
    }

    final static Paint LinePaint;
    final static Paint GradientPaint;
    final static Paint TickPaint;
    final static Paint LabelPaint;
    final static Paint SelectionPaint;

    final static int LineColor = Resources.getSystem().getColor(android.R.color.holo_blue_dark);
    final static int HoloBlue = Resources.getSystem().getColor(android.R.color.holo_blue_light);
    final static int LabelColor = Color.argb(48, 0, 0, 0);

    final static int GradientStart = Color.argb(32, Color.red(HoloBlue), Color.green(HoloBlue), Color.blue(HoloBlue)); //25%
    final static int GradientStop = Color.argb(13, Color.red(HoloBlue), Color.green(HoloBlue), Color.blue(HoloBlue)); //5%

    final static int SelectorBackground = Color.argb(10, 0, 0, 0);
    final static int ClickBackground = Color.argb(25, 0, 0, 0);

    final static int BorderColor = Color.argb(64, 0, 0, 0);
    final static int TickColor = Color.argb(48, 0, 0, 0);

    final static int LineWidthDP = 2;
    final static int TickWidthDP = 1;
    final static int TickLengthDP = 5;

    final static int PointRadiusDP = 5;
    final static int RequiredTopPaddingDP = 24;

    final static int ItemWidthTabletDP = 64;
    final static int ItemWidthPhoneDP = 56;
    final static int LabelHeightDP = 32;

    public final static int OverlayModeRegular = 0;
    public final static int OverlayModeStacked = 1;

    static {
        LinePaint = new Paint();
        LinePaint.setStyle(Paint.Style.STROKE);
        LinePaint.setAntiAlias(true);

        GradientPaint = new Paint();
        GradientPaint.setStyle(Paint.Style.FILL);

        TickPaint = new Paint();
        TickPaint.setStyle(Paint.Style.STROKE);

        LabelPaint = new Paint();
        LabelPaint.setTextAlign(Paint.Align.CENTER);
        LabelPaint.setAntiAlias(true);

        SelectionPaint = new Paint();
    }

    public class Point {
        long value;
        String label;
        Object tag; // TODO

        float percentage;

        long accumulatedOverlayValue;

        public String getLabel() {
            return label;
        }

        public Object getTag() { return tag; }

        public void setValue(long value) {
            this.value = value;

            if (value > highest) {
                highest = value;
                refreshPercentages();
            }
            else {
                percentage = value / (float) highest;
            }

            // TODO change max if no other point is equal to it

            invalidate();
        }

        public long getValue() {
            return value;
        }

        Point zeroClone() {
            Point point = new Point();
            point.value = 0;
            point.label = label;
            point.tag = tag;
            return point;
        }

    }

    public class Overlay {
        int color;
        Object tag;
        ArrayList<Point> points = new ArrayList<Point>(); //Points must be at most equal to max
        boolean pointsVisible;

        boolean visible;
        boolean removed;
        float completion;

        ValueAnimator attachedAnimator;

        public Overlay setTag(Object tag) {
            this.tag = tag;
            return this;
        }

        public Object getTag() {
            return tag;
        }

        public int getColor() {
            return color;
        }

        public List<Point> getPoints() {
            return Collections.unmodifiableList(points);
        }

        public void setPointsVisible(boolean visible) {
            pointsVisible = visible;
        }

        public boolean arePointsVisible() {
            return pointsVisible;
        }

        public void show() {show(true);}

        public void show(boolean animated) {
            visible = true;

            if (attachedAnimator != null) {
                attachedAnimator.end();
            }
            if (animated) {
                attachedAnimator = ValueAnimator.ofFloat(0f, 1f);
                attachedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        completion = valueAnimator.getAnimatedFraction();
                        invalidate();
                    }
                });
                attachedAnimator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        attachedAnimator = null;
                    }
                });
                attachedAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
                attachedAnimator.start();
            }
            else {
                completion = 1f;
                invalidate();
            }
        }

    }

    private float density;

    private int lineWidth;
    private int tickWidth;
    private int tickLength;

    private int itemWidth;
    private int labelHeight;

    private int pointRadius;
    private int requiredTopPadding;

    private int minimumClickDistance;
    private int tapDelay;
    private int pressedStateDuration;

    private int totalWidth;

    private int maxTextureWidth;

    private int lineColor = LineColor;
    private int gradientStart = GradientStart;
    private int gradientStop = GradientStop;
    private int labelColor;

    private int borderColor, tickColor;
    private int selectorColor, clickColor;

    private boolean fillEnabled = true;

    private ArrayList<Point> points = new ArrayList<Point>();
    private long highest;
    private float completion = 1f;

    private ArrayList<Overlay> overlays = new ArrayList<Overlay>();
    private int overlayMode = OverlayModeRegular;
    private float modeTransitionCompletion = 1f;

    private Point selectedPoint;

    private Handler clickDelayHandler = new Handler();
    private OnSelectionChangedListener listener;

    private boolean scrollViewParentHint;
    private HorizontalScrollView parent;
    private int parentLeftPadding;
    private int parentRightPadding;

    private Bitmap softwareBackground;
    private int softwareSurfaceWidth;

    private View rippleView;
    private LegacyRippleDrawable ripple;

    protected void init() {
        density = getResources().getDisplayMetrics().density;

        lineWidth = (int) (LineWidthDP * density + 0.5f);
        tickWidth = (int) (TickWidthDP * density + 0.5f);
        tickLength = (int) (TickLengthDP * density + 0.5f);

        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            itemWidth = (int) (ItemWidthPhoneDP * density + 0.5f);
        }
        else {
            itemWidth = (int) (ItemWidthTabletDP * density + 0.5f);
        }
        labelHeight = (int) (LabelHeightDP * density + 0.5f);

        pointRadius = (int) (PointRadiusDP * density + 0.5f);
        requiredTopPadding = (int) (RequiredTopPaddingDP * density + 0.5f);

        tapDelay = ViewConfiguration.getTapTimeout();
        pressedStateDuration = ViewConfiguration.getPressedStateDuration();
        minimumClickDistance = ViewConfiguration.get(getContext()).getScaledTouchSlop();

        maxTextureWidth = (int) (120 * density + 0.5f);

        selectorColor = SelectorBackground;
        clickColor = ClickBackground;

        tickColor = getResources().getColor(Utils.DashboardSeparatorTransparent); //getResources().getColor(R.color.HeaderSeparator);
        borderColor = getResources().getColor(Utils.DashboardSeparatorTransparent);
        labelColor = getResources().getColor(Utils.DashboardTitle);
        lineColor = Utils.overlayColors(0xFFFFFFFF, getResources().getColor(Utils.DashboardText));

        LinePaint.setStrokeWidth(lineWidth);
        LinePaint.setColor(lineColor);

        TickPaint.setStrokeWidth(tickWidth);
        LabelPaint.setTextSize(14 * density);
        LabelPaint.setTypeface(Utils.DefaultTypeface);
    }

    private int canvasWidth;
    private int canvasHeight;

    private Path pointPath = new Path();

    public void setRippleView(View ripple) {
        this.rippleView = ripple;
        this.ripple = (LegacyRippleDrawable) ripple.getBackground();
    }

    public View getRippleView() {
        return rippleView;
    }

    public void setBaseColor(int color) {
        lineColor = color;
//        gradientStart = Utils.transparentColor(13, color);
//        gradientStop = Utils.transparentColor(13, color);

//        GradientPaint.setShader(new LinearGradient(0, requiredTopPadding, 0, graphHeight, gradientStart, gradientStop, Shader.TileMode.CLAMP));
//        GradientPaint.setColor(gradientStart);
        invalidate();
    }

    public void setFillColor(int color) {
        fillEnabled = true;
        gradientStart = color;
        GradientPaint.setColor(gradientStart);
        invalidate();
    }

    public void setSelectorColor(int color) {
        selectorColor = color;
        clickColor = Utils.transparentColor(2 * Color.alpha(color), color);
        invalidate();
    }

    public void setFillEnabled(boolean enabled) {
        fillEnabled = enabled;
    }

    private int width;

    protected void onSizeChanged(final int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);

        width = w;

        int borderPosition = h - labelHeight;
        int graphHeight = borderPosition - tickWidth / 2 - requiredTopPadding;

//        GradientPaint.setShader(new LinearGradient(0, requiredTopPadding, 0, graphHeight, gradientStart, gradientStop, Shader.TileMode.CLAMP));
        GradientPaint.setColor(gradientStart);

        if (getParent() instanceof HorizontalScrollView && !ENABLE_OFFSCREEN_DRAW) {
//            scrollViewParentHint = true;
//            parent = (ListenableHorizontalScrollView) getParent();
//            parent.setOnScrollListener(new ListenableHorizontalScrollView.OnScrollListener() {
//                @Override
//                public void onScrollChanged(ListenableHorizontalScrollView view, int left, int top, int oldleft, int oldtop) {
//                    onParentScrollChanged(left);
//                }
//            });
//            parentLeftPadding = parent.getPaddingLeft();
//            parentRightPadding = parent.getPaddingRight();
//            visibleWidth = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
//
//            int surfacePointCount = parent.getWidth() / itemWidth + 2;
//            softwareSurfaceWidth = surfacePointCount * itemWidth;
//            softwareBackground = Bitmap.createBitmap(parent.getWidth(), h, Bitmap.Config.ARGB_8888);
//            previousPoints = new float[surfacePointCount];

        }
        else {
            scrollViewParentHint = false;
            parent = null;
        }
    }

    public void clear() {
        points.clear();

        for (Overlay overlay : overlays) {
            overlay.points.clear();
        }

        totalWidth = 0;
        highest = 0;

        requestLayout();
    }

    public void addPoint(long value, String label) {
        addPointWithTag(null, value, label);
    }

    public void addPointWithTag(Object tag, long value, String label) {
        points.add(new Point());
        points.get(points.size() - 1).label = label;
        points.get(points.size() - 1).value = value;
        points.get(points.size() - 1).tag = tag;

        if (value > highest) {
            highest = value;
            refreshPercentages();
        }

        points.get(points.size() - 1).percentage = value / (float) highest;
        totalWidth += itemWidth;

        for (Overlay overlay : overlays) {
            overlay.points.add(points.get(points.size() - 1).zeroClone());
        }

        // TODO: refresh highest

        requestLayout();
    }

    public void refreshHighest() {
        long highest = Long.MIN_VALUE;
        if (overlayMode == OverlayModeRegular) {
            for (Point point : points) {
                if (point.value > highest) highest = point.value;
            }

            for (Overlay overlay : overlays) {

                for (Point point : overlay.points) {
                    if (point.value > highest) highest = point.value;
                }

            }
        }
        else {
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).value > highest) highest = points.get(i).value;
                points.get(i).accumulatedOverlayValue = 0;

                for (Overlay overlay : overlays) {
                    points.get(i).accumulatedOverlayValue += overlay.points.get(i).value;
                }
                if (points.get(i).accumulatedOverlayValue > highest) highest = points.get(i).accumulatedOverlayValue;
            }
        }
        this.highest = highest;

        refreshPercentages();
    }

    public void refreshPercentages() {
        for (Point point : points) {
            point.percentage = point.value / (float) highest;
        }

        for (Overlay overlay : overlays) {

            for (Point point : overlay.points) {
                point.percentage = point.value / (float) highest;
            }

        }
    }

    public void removeOverlayWithColor(int color, boolean animated) {
        Overlay deletedOverlay = null;
        for (Overlay overlay : overlays) {
            if (overlay.color == color && !overlay.removed) {
                deletedOverlay = overlay;
                if (!animated) {
                    overlays.remove(overlay);
                    refreshHighest();
                    invalidate();
                    return;
                }
            }
        }

        final Overlay DeletedOverlay = deletedOverlay;
        if (deletedOverlay != null) {
            deletedOverlay.removed = true;

            if (deletedOverlay.attachedAnimator != null) {
                deletedOverlay.attachedAnimator.end();
            }

            deletedOverlay.attachedAnimator = ValueAnimator.ofFloat(0f, 1f);
            deletedOverlay.attachedAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    DeletedOverlay.completion = 1 - valueAnimator.getAnimatedFraction();
                    invalidate();
                }
            });
            deletedOverlay.attachedAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    overlays.remove(DeletedOverlay);
                }
            });
            deletedOverlay.attachedAnimator.setInterpolator(new AccelerateInterpolator(1.5f));
            deletedOverlay.attachedAnimator.start();
        }
    }

    public Overlay createOverlayWithColor(int color) {
        return createOverlayWithColorAtIndex(color, overlays.size());
    }

    public Overlay createOverlayWithColorAtIndex(int color, int index) {
        Overlay overlay = new Overlay();
        overlays.add(index, overlay);
        overlay.color = color;

        for (Point point : points) {
            overlay.points.add(point.zeroClone());
        }

        return overlay;
    }

    public Overlay findOverlayWithTag(Object tag) {
        for (Overlay overlay : overlays) {
            if (overlay.tag == tag) {
                return overlay;
            }
        }

        return null;
    }

    public void clearOverlays() {
        overlays.clear();
        invalidate();
    }

    public void setOverlayDisplayMode(int mode, boolean animated) {
        overlayMode = mode;

        refreshHighest();

        if (animated) {
            modeTransitionCompletion = 0f;

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    modeTransitionCompletion = valueAnimator.getAnimatedFraction();
                    invalidate();
                }
            });
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            animator.start();
        }
        else {
            modeTransitionCompletion = 1f;
            invalidate();
        }
    }

    public int getOverlayDispalyMode() {
        return overlayMode;
    }

    private boolean trackingPress = false;
    private int clickedPosition = -1;
    private float eventStartX, eventStartY;

    public int getEventItemIndex(MotionEvent event) {
        int startPoint = canvasWidth / 2 - totalWidth / 2;
        int index = (int) ((event.getX() - startPoint) / itemWidth);
        if (index >= 0 && index < points.size()) {
            return index;
        }
        return -1;
    }

    public void setItemWidth(int width) {
        if (width != itemWidth) {
            itemWidth = width;
            totalWidth = points.size() * width;
            requestLayout();
        }
    }

    public List<Point> getPoints() {
        return Collections.unmodifiableList(points);
    }

    public int getItemWidth() {
        return itemWidth;
    }

    private void dispatchRipplePressedState() {
        if (rippleView != null) {
            ripple.onStateChange(new int[] {LegacyRippleDrawable.StatePressed, LegacyRippleDrawable.StateEnabled});
        }
    }

    private Runnable clickRunnable = new Runnable() {
        @Override
        public void run() {
            invalidate();
            dispatchRipplePressedState();
        }
    };

    private void dispatchRippleRestedState() {
        if (rippleView != null) {
            ripple.onStateChange(new int[] {LegacyRippleDrawable.StateEnabled});
        }
    }

    private Runnable clearClickRunnable = new Runnable() {
        @Override
        public void run() {
            clickedPosition = -1;
            invalidate();
            dispatchRippleRestedState();
        }
    };

    private CollectionViewController.ObjectComparator comparator = CollectionViewController.StandardComparator;

    public void setComparator(CollectionViewController.ObjectComparator comparator) {
        if (comparator != null) {
            this.comparator = comparator;
        }
        else {
            this.comparator = CollectionViewController.StandardComparator;
        }
    }

    public void selectPointWithTag(Object tag) {
        Point previousSelectedPoint = selectedPoint;

        for (Point point : points) {
            if (comparator.areObjectsEqual(tag, point.tag)) {
                selectedPoint = point;
                break;
            }
        }

        if (previousSelectedPoint != selectedPoint) {
            invalidate();
            if (listener != null) {
                listener.onSelectionChanged(selectedPoint.tag, points.indexOf(selectedPoint));
            }
        }
    }

    public void selectPointWithTagSilently(Object tag) {
        Point previousSelectedPoint = selectedPoint;

        for (Point point : points) {
            if (comparator.areObjectsEqual(tag, point.tag)) {
                selectedPoint = point;
                break;
            }
        }

        if (previousSelectedPoint != selectedPoint) {
            invalidate();
        }
    }

    public void setOnSelectionChangedListener(OnSelectionChangedListener listener) {
        this.listener = listener;
    }

    public OnSelectionChangedListener getOnSelectionChangedListener() {
        return listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            trackingPress = true;
            eventStartX = event.getX();
            eventStartY = event.getY();
            clickedPosition = getEventItemIndex(event);

            if (rippleView != null) {
                rippleView.setX(getLeft() + clickedPosition * itemWidth);
                ripple.setRippleSource(eventStartX - clickedPosition * itemWidth, eventStartY);
            }

            clickDelayHandler.postDelayed(clickRunnable, tapDelay);
            return true;
        }
        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            if (trackingPress) {
                if (Math.abs(event.getX() - eventStartX) > minimumClickDistance ||
                        Math.abs(event.getY() - eventStartY) > minimumClickDistance) {
                    clickDelayHandler.removeCallbacks(clickRunnable);
                    clickedPosition = -1;
                    dispatchRippleRestedState();
                    invalidate();
                    trackingPress = false;
                    return false;
                }
                return true;
            }
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            if (trackingPress) {
                clickDelayHandler.removeCallbacks(clickRunnable);
                clickedPosition = -1;
                dispatchRippleRestedState();
                invalidate();
            }
            trackingPress = false;
            return false;
        }
        if (event.getAction() == MotionEvent.ACTION_UP && trackingPress) {
            clickDelayHandler.removeCallbacks(clickRunnable);
            dispatchRipplePressedState();
            invalidate();
            clickDelayHandler.postDelayed(clearClickRunnable, pressedStateDuration);
            int index = getEventItemIndex(event);
            if (index != -1) {
                if (index != points.indexOf(selectedPoint)) {
                    selectedPoint = points.get(index);
                    if (listener != null) {
                        listener.onSelectionChanged(points.get(index).tag, index);
                    }
                }
            }
            invalidate();
            return true;
        }

//        if (rippleView != null) {
//            rippleView.dispatchTouchEvent(event);
//        }

        return trackingPress;
    }

    public void setCompletion(float completion) {
        this.completion = completion;
        invalidate();
    }

    public float getCompletion() {
        return completion;
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        int widthValue = MeasureSpec.getSize(widthMeasureSpec);

        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.AT_MOST) {
            if (widthValue < totalWidth)
                setMeasuredDimension(widthValue, getMeasuredHeight());
            else
                setMeasuredDimension(totalWidth, getMeasuredHeight());
        }

        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.EXACTLY) {
            setMeasuredDimension(widthValue, getMeasuredHeight());
        }

        if (MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED) {
            setMeasuredDimension(totalWidth, getMeasuredHeight());
        }

    }

    public int getRequiredWidth() {
        return totalWidth;
    }

    private int startIndex;
    private int endIndex;

    private int parentLeft;
    private int visibleWidth;

    protected void onParentScrollChanged(int left) {
        parentLeft = left;

        int startIndex, endIndex, overlayStartIndex, overlayEndIndex;
        startIndex = parentLeft / itemWidth - 1;
        if (startIndex < 0) {
            startIndex = 0;
        }
        endIndex = startIndex + (int) (visibleWidth / (float) itemWidth + 0.99f) + 2;
        if (endIndex > points.size()) {
            endIndex = points.size();
        }

        overlayStartIndex = startIndex;
        overlayEndIndex = endIndex;

        if (!ENABLE_OFFSCREEN_DRAW) {
            if (startIndex != this.startIndex || endIndex != this.endIndex) invalidate();
        }
    }

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (BITMAP_BACKED_DRAW) {
            newOnDraw(canvas);
            return;
        }

        canvasWidth = canvas.getWidth();
        canvasHeight = canvas.getHeight();

        GradientPaint.setColor(gradientStart);

        int borderPosition = canvasHeight - labelHeight;
        int labelPosition = borderPosition + 2 * labelHeight / 3;
        int graphHeight = borderPosition - tickWidth / 2 - requiredTopPadding;

        int tickStartPoint = canvasWidth / 2 - totalWidth / 2;

        if (points.size() > 1) {
            // Fill up the bottom area
            canvas.drawRect(0, borderPosition - 1, canvasWidth, canvas.getHeight(), GradientPaint);
        }

        if (points.size() > 0) {

            // Draw each tick; additionally, start building the labels and the line path
            TickPaint.setColor(Utils.transparentColor((int) (completion * Color.alpha(tickColor)), tickColor));

            // Optimization begin
            startIndex = 0;
            endIndex = points.size();
            if (scrollViewParentHint) {
                startIndex = parentLeft / itemWidth - 1;
                if (startIndex < 0) {
                    startIndex = 0;
                }
                // startIndex draws one item before the visible border
                // endIndex draw one item after the visible border
                // Always consider fractional items as a full index
                endIndex = startIndex + (int) (visibleWidth / (float) itemWidth + 0.99f) + 2;
                if (endIndex > points.size()) {
                    endIndex = points.size();
                }
            }
            // Optimization end

            // The first and last ticks are missing
            int currentPosition = tickStartPoint + itemWidth + itemWidth * startIndex;

            int accumulatedPathWidth = 0;
//            int lastItemIndex = 0;
            int lastPathStartPoint = tickStartPoint;

            if (fillEnabled) {
                pointPath.rewind();
                pointPath.moveTo(0, requiredTopPadding + graphHeight - (points.get(startIndex).percentage / 2) * completion * graphHeight);
                pointPath.lineTo(tickStartPoint + itemWidth / 2 + itemWidth * startIndex, requiredTopPadding + graphHeight - points.get(startIndex).percentage * completion * graphHeight);
                lastPathStartPoint = - itemWidth / 2;
            }

            int selectedLabelColor = Utils.transparentColor((int) (completion * Color.alpha(lineColor)), lineColor);
            int deselectedLabelColor = Utils.transparentColor((int) (completion * Color.alpha(labelColor)), labelColor);

            if (points.get(startIndex) == selectedPoint)  LabelPaint.setColor(selectedLabelColor);
            else LabelPaint.setColor(deselectedLabelColor);

            LabelPaint.setAlpha((int) (LabelPaint.getAlpha() * completion));
            canvas.drawText(points.get(startIndex).label, tickStartPoint + itemWidth / 2, labelPosition, LabelPaint);
            int pointsSize = points.size();

            for (int i = startIndex + 1; i < endIndex; i++) {

                // Tick
//                canvas.drawLine(currentPosition, borderPosition + tickWidth, currentPosition, borderPosition + tickWidth + tickLength, TickPaint);
                canvas.drawLine(currentPosition, canvasHeight - tickLength, currentPosition, canvasHeight, TickPaint);

                if (fillEnabled) {
                    // Path
                    pointPath.lineTo(currentPosition + itemWidth / 2, requiredTopPadding + graphHeight - points.get(i).percentage * completion * graphHeight);
                }

                // Label
                if (points.get(i) == selectedPoint)  LabelPaint.setColor(lineColor);
                else LabelPaint.setColor(labelColor);

                LabelPaint.setAlpha((int) (LabelPaint.getAlpha() * completion));

                canvas.drawText(points.get(i).label, currentPosition + itemWidth / 2, labelPosition, LabelPaint);

                accumulatedPathWidth += itemWidth;
                // If the path exceeds the screen width, it will not be rendered into view, so it has to be drawn in steps if too long
                if (accumulatedPathWidth > maxTextureWidth && fillEnabled) {
                    accumulatedPathWidth = 0;
                    pointPath.lineTo(currentPosition + itemWidth / 2, requiredTopPadding + graphHeight);
                    pointPath.lineTo(lastPathStartPoint + itemWidth / 2, requiredTopPadding + graphHeight);
                    pointPath.close();

                    canvas.drawPath(pointPath, GradientPaint);

                    pointPath.rewind();
                    pointPath.moveTo(currentPosition + itemWidth / 2, requiredTopPadding + graphHeight - points.get(i).percentage * completion * graphHeight);

                    lastPathStartPoint = currentPosition;
//                    lastItemIndex = i;
                }

                currentPosition += itemWidth;
            }

            if (fillEnabled) {
                pointPath.lineTo(currentPosition + itemWidth / 2, requiredTopPadding + graphHeight - points.get(points.size() - 1).percentage / 2f * completion * graphHeight);

                // The line path is now complete, but the gradient must be drawn first
                pointPath.lineTo(currentPosition + itemWidth / 2, requiredTopPadding + graphHeight);
//            pointPath.lineTo(tickStartPoint + itemWidth / 2, requiredTopPadding + graphHeight);
                pointPath.lineTo(lastPathStartPoint + itemWidth / 2, requiredTopPadding + graphHeight);
                pointPath.close();

                canvas.drawPath(pointPath, GradientPaint);
            }

            currentPosition = tickStartPoint + itemWidth + itemWidth * startIndex;
            LinePaint.setAlpha((int) (255 * completion));
            // draw the starting and ending lines
            canvas.drawLine(- itemWidth / 2, requiredTopPadding + graphHeight - points.get(0).percentage / 2f * completion * graphHeight,
                    currentPosition - itemWidth / 2, requiredTopPadding + graphHeight - points.get(0).percentage * completion * graphHeight, LinePaint);

            for (int i = startIndex + 1; i < endIndex; i++) {
                // Line; using drawLine is much faster than drawPath and causes far less overdraw
                // as drawPath is first rendered into a rectangular bitmap
                canvas.drawLine(currentPosition - itemWidth / 2, requiredTopPadding + graphHeight - points.get(i - 1).percentage * completion * graphHeight,
                        currentPosition + itemWidth / 2, requiredTopPadding + graphHeight - points.get(i).percentage * completion * graphHeight, LinePaint);

                currentPosition += itemWidth;
            }

            canvas.drawLine(currentPosition - itemWidth / 2, requiredTopPadding + graphHeight - points.get(points.size() - 1).percentage * completion * graphHeight,
                    currentPosition + itemWidth / 2, requiredTopPadding + graphHeight - points.get(points.size() - 1).percentage / 2f * completion * graphHeight, LinePaint);

            // The line can now be drawn
//            canvas.drawPath(pointPath, LinePaint);

            currentPosition = tickStartPoint + itemWidth / 2 + itemWidth * startIndex;

            // The points may now be drawn
            for (int i = startIndex; i < endIndex; i++) {
                points.get(i).accumulatedOverlayValue = 0;

                if (ALTERNATE_POINTS) {
                    LinePaint.setStyle(Paint.Style.FILL);
                    LinePaint.setColor(lineColor);
                    LinePaint.setAlpha((int) (255 * completion));
                    canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - points.get(i).percentage * completion * graphHeight, pointRadius, LinePaint);
                }
                else {
                    // outer line
                    LinePaint.setColor(0xFFFFFFFF);
                    LinePaint.setAlpha((int) (255 * completion));
                    LinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - points.get(i).percentage * completion * graphHeight, pointRadius + lineWidth, LinePaint);
                    // inner circle
                    LinePaint.setColor(lineColor);
                    if (points.get(i) == selectedPoint)
                        LinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    else LinePaint.setStyle(Paint.Style.STROKE);
                    LinePaint.setAlpha((int) (255 * completion));
                    canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - points.get(i).percentage * completion * graphHeight, pointRadius, LinePaint);
                }

                currentPosition += itemWidth;
            }

            // Drawing overlays, if any
            for (Overlay overlay : overlays) {
                if (!overlay.visible) continue;
                float compoundCompletion = completion * overlay.completion;

                LinePaint.setColor(overlay.color);

                currentPosition = tickStartPoint + itemWidth + itemWidth * startIndex;
                LinePaint.setAlpha((int) (255 * compoundCompletion));

                float previousHeight;
                points.get(startIndex).accumulatedOverlayValue += overlay.points.get(startIndex).value * overlay.completion;
                if (overlayMode == OverlayModeStacked) {
                    previousHeight = points.get(startIndex).accumulatedOverlayValue / (float) highest;
                    if (modeTransitionCompletion < 1) {
                        previousHeight = Utils.interpolateValues(modeTransitionCompletion, overlay.points.get(startIndex).percentage, previousHeight);
                    }
                }
                else {
                    previousHeight = overlay.points.get(startIndex).percentage;
                    if (modeTransitionCompletion < 1) {
                        previousHeight = Utils.interpolateValues(modeTransitionCompletion, points.get(startIndex).accumulatedOverlayValue / (float) highest, previousHeight);
                    }
                }
                previousHeight = previousHeight * compoundCompletion * graphHeight;

                for (int i = startIndex + 1; i < endIndex; i++) {

                    float currentHeight;
                    points.get(i).accumulatedOverlayValue += overlay.points.get(i).value * overlay.completion;
                    if (overlayMode == OverlayModeStacked) {
                        currentHeight = points.get(i).accumulatedOverlayValue / (float) highest;
                        if (modeTransitionCompletion < 1) {
                            currentHeight = Utils.interpolateValues(modeTransitionCompletion, overlay.points.get(i).percentage, currentHeight);
                        }
                    }
                    else {
                        currentHeight = overlay.points.get(i).percentage;
                        if (modeTransitionCompletion < 1) {
                            currentHeight = Utils.interpolateValues(modeTransitionCompletion, points.get(i).accumulatedOverlayValue / (float) highest, currentHeight);
                        }
                    }
                    currentHeight = currentHeight  * compoundCompletion * graphHeight;

                    // Line; using drawLine is much faster than drawPath and causes far less overdraw
                    // as drawPath is first rendered into a rectangular bitmap
                    canvas.drawLine(currentPosition - itemWidth / 2, requiredTopPadding + graphHeight - previousHeight,
                            currentPosition + itemWidth / 2, requiredTopPadding + graphHeight - currentHeight, LinePaint);

                    currentPosition += itemWidth;

                    previousHeight = currentHeight;
                }

                // The line can now be drawn
//            canvas.drawPath(pointPath, LinePaint);

                currentPosition = tickStartPoint + itemWidth / 2 + itemWidth * startIndex;

                // The points may now be drawn
                if (overlay.arePointsVisible()) {
                    for (int i = startIndex; i < endIndex; i++) {
                        float height;
                        if (overlayMode == OverlayModeStacked) {
                            height = points.get(i).accumulatedOverlayValue / (float) highest;
                            if (modeTransitionCompletion < 1) {
                                height = Utils.interpolateValues(modeTransitionCompletion, overlay.points.get(i).percentage, height);
                            }
                        }
                        else {
                            height = overlay.points.get(i).percentage;
                            if (modeTransitionCompletion < 1) {
                                height = Utils.interpolateValues(modeTransitionCompletion, points.get(i).accumulatedOverlayValue / (float) highest, height);
                            }
                        }
                        height = height  * compoundCompletion * graphHeight;

                        // outer line
                        LinePaint.setColor(0xFFFFFFFF);
                        LinePaint.setAlpha((int) (255 * compoundCompletion));
                        LinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
                        canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - height, pointRadius + lineWidth, LinePaint);
                        // inner circle
                        LinePaint.setColor(overlay.color);
                        if (points.get(i) == selectedPoint)
                            LinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
                        else LinePaint.setStyle(Paint.Style.STROKE);
                        LinePaint.setAlpha((int) (255 * compoundCompletion));
                        canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - height, pointRadius, LinePaint);

                        currentPosition += itemWidth;
                    }
                }
                else {
                    LinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    for (int i = startIndex; i < endIndex; i++) {
                        float height;
                        if (overlayMode == OverlayModeStacked) {
                            height = points.get(i).accumulatedOverlayValue / (float) highest;
                            if (modeTransitionCompletion < 1) {
                                height = Utils.interpolateValues(modeTransitionCompletion, overlay.points.get(i).percentage, height);
                            }
                        }
                        else {
                            height = overlay.points.get(i).percentage;
                            if (modeTransitionCompletion < 1) {
                                height = Utils.interpolateValues(modeTransitionCompletion, points.get(i).accumulatedOverlayValue / (float) highest, height);
                            }
                        }
                        height = height  * compoundCompletion * graphHeight;
                        // inner circle
                        LinePaint.setColor(overlay.color);
                        LinePaint.setAlpha((int) (255 * compoundCompletion));
                        canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - height, pointRadius / 2, LinePaint);

                        currentPosition += itemWidth;
                    }
                }
            }

            LinePaint.setColor(lineColor);

        }

        // Draw the bottom line @deprecated
//        TickPaint.setColor(borderColor);
//        canvas.drawLine(0, borderPosition, canvasWidth, borderPosition, TickPaint);

        // Draw the selection, if it exists
        int index = points.indexOf(selectedPoint);
        SelectionPaint.setColor(Utils.transparentColor((int) (Color.alpha(selectorColor) * completion), selectorColor));
        canvas.drawRect(tickStartPoint + index * itemWidth, 0, tickStartPoint + (index + 1) * itemWidth, canvasHeight, SelectionPaint);

        // Draw the click background, if it exists and there's no ripple view handling press events
        if (clickedPosition != -1 && rippleView == null) {
            SelectionPaint.setColor(clickColor);
            canvas.drawRect(tickStartPoint + clickedPosition * itemWidth, 0, tickStartPoint + (clickedPosition + 1) * itemWidth, canvasHeight, SelectionPaint);
        }
    }

    protected void newOnDraw(Canvas canvas) {

        if (true) {
            startIndex = 0;
            endIndex = points.size();
        }

        canvasWidth = canvas.getWidth();
        canvasHeight = canvas.getHeight();

        GradientPaint.setColor(gradientStart);

        int borderPosition = canvasHeight - labelHeight;
        int labelPosition = borderPosition + 2 * labelHeight / 3;
        int graphHeight = borderPosition - tickWidth / 2 - requiredTopPadding;
        int bottom = requiredTopPadding + graphHeight;
        float completedHeight = completion * graphHeight;

        int tickStartPoint = canvasWidth / 2 - totalWidth / 2; // this is 0 except when the view's width is less than the graph's width

        drawFills(canvas, graphHeight, bottom);

        // start and end index

        if (points.size() > 0) {
            // Draw each tick; additionally, start building the labels and the line path
            TickPaint.setColor(tickColor);

            // The first and last ticks are missing
            int currentPosition = tickStartPoint + itemWidth + itemWidth * startIndex;

            if (points.get(startIndex) == selectedPoint)  LabelPaint.setColor(lineColor);
            else LabelPaint.setColor(labelColor);

            LabelPaint.setAlpha((int) (LabelPaint.getAlpha() * completion));
            canvas.drawText(points.get(startIndex).label, tickStartPoint + itemWidth / 2, labelPosition, LabelPaint);

            LinePaint.setAlpha((int) (255 * completion));

            for (int i = startIndex + 1; i < endIndex; i++) {
                // Tick
                canvas.drawLine(currentPosition, borderPosition + tickWidth, currentPosition, borderPosition + tickWidth + tickLength, TickPaint);

                // Label
                if (points.get(i) == selectedPoint)  LabelPaint.setColor(lineColor);
                else LabelPaint.setColor(labelColor);

                LabelPaint.setAlpha((int) (LabelPaint.getAlpha() * completion));

                canvas.drawText(points.get(i).label, currentPosition + itemWidth / 2, labelPosition, LabelPaint);

                // Line; using drawLine is much faster than drawPath and causes far less overdraw
                // as drawPath is first rendered into a rectangular bitmap
                canvas.drawLine(currentPosition - itemWidth / 2, bottom - points.get(i - 1).percentage * completedHeight,
                        currentPosition + itemWidth / 2, bottom - points.get(i).percentage * completedHeight, LinePaint);

                // Advance caret to the next point
                currentPosition += itemWidth;
            }

            currentPosition = tickStartPoint + itemWidth / 2 + itemWidth * startIndex;

            // The points may now be drawn
            for (int i = startIndex; i < endIndex; i++) {
                points.get(i).accumulatedOverlayValue = 0;

                LinePaint.setStyle(Paint.Style.FILL);
                LinePaint.setColor(lineColor);
                LinePaint.setAlpha((int) (255 * completion));
                canvas.drawCircle(currentPosition, bottom - points.get(i).percentage * completedHeight, pointRadius, LinePaint);

                // Advance caret to the next point
                currentPosition += itemWidth;
            }

            // Drawing overlays, if any
            for (Overlay overlay : overlays) {
                if (!overlay.visible) continue;
                float compoundCompletion = completion * overlay.completion;

                LinePaint.setColor(overlay.color);

                currentPosition = tickStartPoint + itemWidth + itemWidth * startIndex;
                LinePaint.setAlpha((int) (255 * compoundCompletion));

                float previousHeight = increaseAndObtainHeight(overlay, startIndex);
                previousHeight = previousHeight * compoundCompletion * graphHeight;

                for (int i = startIndex + 1; i < endIndex; i++) {

                    float currentHeight = increaseAndObtainHeight(overlay, i);
                    currentHeight = currentHeight  * compoundCompletion * graphHeight;

                    // Line
                    canvas.drawLine(currentPosition - itemWidth / 2, requiredTopPadding + graphHeight - previousHeight,
                            currentPosition + itemWidth / 2, requiredTopPadding + graphHeight - currentHeight, LinePaint);

                    currentPosition += itemWidth;

                    previousHeight = currentHeight;
                }

                currentPosition = tickStartPoint + itemWidth / 2 + itemWidth * startIndex;

                // The points may now be drawn
                if (overlay.arePointsVisible()) {
                    for (int i = startIndex; i < endIndex; i++) {
                        float height = obtainHeight(overlay, i);
                        height = height  * compoundCompletion * graphHeight;

                        // outer line
                        LinePaint.setColor(0xFFFFFFFF);
                        LinePaint.setAlpha((int) (255 * compoundCompletion));
                        LinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
                        canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - height, pointRadius + lineWidth, LinePaint);
                        // inner circle
                        LinePaint.setColor(overlay.color);
                        if (points.get(i) == selectedPoint)
                            LinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
                        else LinePaint.setStyle(Paint.Style.STROKE);
                        LinePaint.setAlpha((int) (255 * compoundCompletion));
                        canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - height, pointRadius, LinePaint);

                        currentPosition += itemWidth;
                    }
                }
                else {
                    LinePaint.setStyle(Paint.Style.FILL_AND_STROKE);
                    for (int i = startIndex; i < endIndex; i++) {
                        float height = obtainHeight(overlay, i);
                        height = height  * compoundCompletion * graphHeight;
                        // inner circle
                        LinePaint.setColor(overlay.color);
                        LinePaint.setAlpha((int) (255 * compoundCompletion));
                        canvas.drawCircle(currentPosition, requiredTopPadding + graphHeight - height, pointRadius / 2, LinePaint);

                        currentPosition += itemWidth;
                    }
                }
            }

            LinePaint.setColor(lineColor);

        }

        // Draw the bottom line @deprecated
//        TickPaint.setColor(borderColor);
//        canvas.drawLine(0, borderPosition, canvasWidth, borderPosition, TickPaint);

        // Fill up the bottom area
        canvas.drawRect(0, borderPosition, canvasWidth, canvas.getHeight(), GradientPaint);

        // Draw the selection, if it exists
        int index = points.indexOf(selectedPoint);
        SelectionPaint.setColor(selectorColor);
        canvas.drawRect(tickStartPoint + index * itemWidth, 0, tickStartPoint + (index + 1) * itemWidth, canvasHeight, SelectionPaint);

        // Draw the click background, if it exists and if it's not already handled by a ripple view
        if (clickedPosition != -1 && rippleView == null) {
            SelectionPaint.setColor(ClickBackground);
            canvas.drawRect(tickStartPoint + clickedPosition * itemWidth, 0, tickStartPoint + (clickedPosition + 1) * itemWidth, canvasHeight, SelectionPaint);
        }
    }

    protected float obtainHeight(Overlay overlay, int index) {

        float previousHeight;
        if (overlayMode == OverlayModeStacked) {
            previousHeight = points.get(index).accumulatedOverlayValue / (float) highest;
            if (modeTransitionCompletion < 1) {
                previousHeight = Utils.interpolateValues(modeTransitionCompletion, overlay.points.get(index).percentage, previousHeight);
            }
        }
        else {
            previousHeight = overlay.points.get(index).percentage;
            if (modeTransitionCompletion < 1) {
                previousHeight = Utils.interpolateValues(modeTransitionCompletion, points.get(index).accumulatedOverlayValue / (float) highest, previousHeight);
            }
        }
        return previousHeight;
    }

    protected float increaseAndObtainHeight(Overlay overlay, int index) {

        points.get(index).accumulatedOverlayValue += overlay.points.get(index).value * overlay.completion;
        return obtainHeight(overlay, index);

    }

    Path fillTop = new Path();
    Path fillBottom = new Path();
    float[] previousPoints; // initialized in onSizeChanged according to the softwareSurfaceWidth
    Canvas softwareCanvas = new Canvas();

    protected void drawFills(Canvas canvas, int graphHeight, float bottom) {
//        Log.d(TAG, "DRAWWWW FILLSSSSSSS");

        // ORDER IS: OVERLAY 0 > ... > OVERLAY N > GRAPH

        boolean initializeAccumulatedOverlayValue = true;
        int displacement = parent.getScrollX();

        int localStartIndex = displacement / itemWidth - 1;
        if (localStartIndex < 0) localStartIndex = 0;

        Bitmap bitmap = softwareBackground;
        softwareCanvas.setBitmap(bitmap);
        softwareCanvas.drawColor(0, PorterDuff.Mode.CLEAR);

        int localEndIndex = (displacement + parent.getWidth()) / itemWidth + 1;
        if (localEndIndex > points.size()) localEndIndex = points.size();

        Overlay lastOverlay;

        boolean drawOverlays = overlayMode == OverlayModeStacked || modeTransitionCompletion != 0;
        if (drawOverlays) for (Overlay overlay : overlays) {
            if (!overlay.visible) continue;
            float compoundCompletion = completion * overlay.completion;
            float compoundCompletedHeight = compoundCompletion * graphHeight;

            lastOverlay = overlay;

            // caret represents the current drawing point; it is relative to the bitmap
            int caret = localStartIndex * itemWidth + itemWidth / 2 - displacement;
            int backwardsCaret = (localEndIndex - localStartIndex - 1) * itemWidth;
            int i = localStartIndex;

            fillTop.rewind();

            if (initializeAccumulatedOverlayValue) {
                points.get(i).accumulatedOverlayValue = 0;

                // during the first pass, the fillBottom path must be created
                // in subsequent passes it's already built; it's identical to the previous fillTop
                fillTop.moveTo(caret, bottom);
                fillBottom.moveTo(backwardsCaret, bottom);
                previousPoints[0] = 0;
            }
            else {
                fillTop.moveTo(caret, bottom - previousPoints[0]);
                fillBottom.moveTo(backwardsCaret, previousPoints[localEndIndex - localStartIndex - 1]);
            }

            float topMargin = increaseAndObtainHeight(overlay, i) * compoundCompletedHeight;
            fillTop.lineTo(caret, topMargin);

            caret += itemWidth;
            i++;

            for (; i < localEndIndex - 1; i++) {
                if (initializeAccumulatedOverlayValue) points.get(i).accumulatedOverlayValue = 0;

                topMargin = increaseAndObtainHeight(overlay, i);
                fillTop.lineTo(caret, bottom - topMargin);
                if (initializeAccumulatedOverlayValue) {
                    fillBottom.lineTo(backwardsCaret, bottom);
                }
                else {
                    fillBottom.lineTo(backwardsCaret, bottom - previousPoints[localEndIndex - i - 1]);
                }

                previousPoints[localEndIndex - i - 1] = topMargin;

                caret += itemWidth;
                backwardsCaret -= itemWidth;
            }

            fillTop.addPath(fillBottom);
            fillTop.close();

            GradientPaint.setColor(Utils.transparentColor((int) (compoundCompletion * modeTransitionCompletion * 64), overlay.color));

            softwareCanvas.drawPath(fillTop, GradientPaint);

            initializeAccumulatedOverlayValue = false;

        }

        // TODO MAINNNNN GRAPHHHH FILLLLLL

        canvas.drawBitmap(bitmap, displacement, 0, null);
    }

    public GraphView(Context context) {
        super(context);

        init();
    }

    public GraphView(Context context, AttributeSet attrs) {
        super(context, attrs);

        init();
    }

    public GraphView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);

        init();
    }

}
