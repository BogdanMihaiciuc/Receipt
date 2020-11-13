package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnticipateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

//import com.BogdanMihaiciuc.receipt.R;
//import com.BogdanMihaiciuc.receipt.Receipt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PrecisionRangeSlider extends View {

    final static String TAG = PrecisionRangeSlider.class.getName();

    final static boolean DEBUG = true;

    public interface PopupListener {
        public CharSequence getPopupLabel(float percent);
    }

    public interface PopupColorListener {
        public int getPopupColor(float percent);
    }

    public interface OnRangeChangedListener {
        public void onRangeChange(float fromRange, float toRange, boolean fromUser);
        public void onRangeSelected(float fromRange, float toRange, boolean fromUser);
    }

    final static int SliderHeightDP = 48;
    final static int LabelHeightDP = 48;

    final static int TrackHeightDP = 8;
    final static int HandleGlowRadiusDP = 8;

    final static int PrecisionTrackHeightDP = 16;

    final static int LabelTextSizeSP = 12;

    final static int DefaultTrackEdgeColor = 0x25000000;
    final static int DefaultTrackFillColor = Utils.transparentColor(0x88, Resources.getSystem().getColor(android.R.color.holo_blue_light));
    final static int DefaultHandleColor = Resources.getSystem().getColor(android.R.color.holo_blue_dark);
    final static int DefaultHandleGlowColor = DefaultTrackFillColor;

    final static Paint EdgePaint;
    final static Paint FillPaint;
    final static Paint LabelPaint;

    final static int HandleNone = 0;
    final static int HandleStart = 1;
    final static int HandleEnd = 2;

    public final static int SliderTypeRange = 0;
    public final static int SliderTypeAmount = 1;
    public final static int SliderTypeColor = 2;

    static {
        EdgePaint = new Paint();
        EdgePaint.setAntiAlias(true);
        EdgePaint.setStyle(Paint.Style.STROKE);

        FillPaint = new Paint();
        FillPaint.setAntiAlias(true);
        FillPaint.setStyle(Paint.Style.FILL);

        LabelPaint = new Paint();
        LabelPaint.setAntiAlias(true);
        LabelPaint.setTextAlign(Paint.Align.CENTER);
    }

    private int sliderHeight;
    private int labelHeight;

    private int labelColor;
    private int sublabelColor;
    private int trackEdgeColor;
    private int trackFillColor;
    private int handleColor;
    private int handleGlowColor;

    private int trackHeight;
    private int handleGlowRadius;
    private int precisionTrackHeight;

    private int margin;

    private DisplayMetrics metrics;
    private float density;

    private Utils.DPTranslator pixels;

    private float startPosition = 0f;
    private float endPosition = 0.5f;

    private float minimumRange = 0f;
    private float maximumRange = Float.MAX_VALUE;

    private boolean labelsEnabled = false;
    private int selectedLabel = -1;
    private ArrayList<CharSequence> labels = new ArrayList<CharSequence>();
    private ArrayList<Float> labelRanges = new ArrayList<Float>();

    private ArrayList<Integer> colors;

    private float rangeStart = 0f;
    private float rangeEnd = 100f;

    private int sliderType = SliderTypeAmount;

    private PopupListener popupListener;
    private PopupColorListener popupColorListener;
    private OnRangeChangedListener rangeListener;

    private ValueAnimator rangeAnimator;

    public void init() {
        metrics = getResources().getDisplayMetrics();
        density = metrics.density;

        pixels = new Utils.DPTranslator(density);

        sliderHeight = pixels.get(SliderHeightDP);
        labelHeight = pixels.get(LabelHeightDP);

        trackHeight = pixels.get(TrackHeightDP);
        handleGlowRadius = pixels.get(HandleGlowRadiusDP);

        precisionTrackHeight = pixels.get(PrecisionTrackHeightDP);

        labelColor = getResources().getColor(Utils.DashboardTitle);
        sublabelColor = Utils.transparentColor(Color.alpha(labelColor) / 2, labelColor);

        trackEdgeColor = DefaultTrackEdgeColor;
        trackFillColor = DefaultTrackFillColor;
        handleColor = DefaultHandleColor;
        handleGlowColor = DefaultHandleGlowColor;

        LabelPaint.setTextSize(pixels.get(12));
    }

    public void setRange(float start, float end) {
        rangeEnd = end;
        rangeStart = start;
    }

    public void setOnRangeChangeListener(OnRangeChangedListener listener) {
        rangeListener = listener;
    }

    public OnRangeChangedListener getOnRangeChangeListener() {
        return rangeListener;
    }

    public void addLabels(CharSequence ... labels) {
        if (!labelsEnabled) labelsEnabled = true;

        Collections.addAll(this.labels, labels);
        onSizeChanged(getWidth(), getHeight(), 0, 0);
        updateLabelRanges();
    }

    public void setSliderType(int sliderType) {
        this.sliderType = sliderType;
        if (sliderType == SliderTypeAmount) {
            maximumRange = Float.MAX_VALUE;
        }

        updateStartInteractRect();
        invalidate();
    }

    public void setColorIntervals(Integer ... colors) {
        setColorIntervals(Arrays.asList(colors));
    }

    public void setColorIntervals(List<Integer> colors) {
        this.colors = new ArrayList<Integer>(colors);

        sliderType = SliderTypeColor;
        trackFillColor = 0;
        trackHeight = pixels.get(TrackHeightDP * 2);
        handleColor = 0xFFFFFFFF;

        setRange(0, colors.size());

        // Automatically calls invalidate()
        onSizeChanged(getWidth(), getHeight(), 0, 0);
    }

    public void setMinimumRange(float range) {
        minimumRange = Utils.getConstrainedIntervalPercentage(range, rangeStart, rangeEnd);
        if (startPosition + minimumRange > endPosition) {
            endPosition = startPosition + minimumRange;
            if (endPosition > 1) {
                endPosition = 1;
                startPosition = endPosition - minimumRange;
                if (startPosition < 0) startPosition = 0;
            }

            invalidate();
            updateInteractRects();
        }
    }

    public void setMaximumRange(float range) {
        maximumRange = Utils.getConstrainedIntervalPercentage(range, rangeStart, rangeEnd);
        if (startPosition + maximumRange < endPosition) {
            endPosition = startPosition + maximumRange;
            if (endPosition > 1) {
                endPosition = 1;
                startPosition = endPosition - maximumRange;
                if (startPosition < 0) startPosition = 0;
            }

            invalidate();
            updateInteractRects();
        }
    }

    public void setStartPosition(float position) {
        if (sliderType == SliderTypeRange) {
            startPosition = Utils.getIntervalPercentage(position, rangeStart, rangeEnd);
            if (rangeListener != null) {
                rangeListener.onRangeSelected(getUserVisibleRange(startPosition), getUserVisibleRange(endPosition), false);
            }
            invalidate();
        }
    }

    public void setEndPosition(float position) {
        setEndPosition(position, true);
    }

    public void setEndPosition(float position, boolean animated) {
        final float EndPosition = Utils.constrain(endPosition, 0f, 1f);
        final float EndPositionPost = Utils.constrain(Utils.getIntervalPercentage(position, rangeStart, rangeEnd), 0f, 1f);
        if (animated) {
            if (rangeAnimator != null) rangeAnimator.end();
            rangeAnimator = ValueAnimator.ofFloat(EndPosition, EndPositionPost);
            rangeAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    endPosition = (Float) valueAnimator.getAnimatedValue();
                    if (endPosition - startPosition > maximumRange) {
                        startPosition = endPosition - maximumRange;
                    }
                    if (rangeListener != null) {
                        rangeListener.onRangeChange(getUserVisibleRange(startPosition), getUserVisibleRange(endPosition), false);
                    }

                    invalidate();
                }
            });
            rangeAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    rangeAnimator = null;
                    if (rangeListener != null) {
                        rangeListener.onRangeSelected(getUserVisibleRange(startPosition), getUserVisibleRange(endPosition), false);
                    }
                }
            });
            rangeAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
            rangeAnimator.setDuration(200);
            rangeAnimator.start();
        }
        else {
            endPosition = EndPositionPost;
            if (rangeListener != null) {
                rangeListener.onRangeSelected(getUserVisibleRange(startPosition), getUserVisibleRange(endPosition), false);
            }
        }
        invalidate();
    }

    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);

        int height = MeasureSpec.getSize(heightMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int measuredHeight;

        if (heightMode == MeasureSpec.UNSPECIFIED) {
            if (labelsEnabled) measuredHeight = labelHeight + sliderHeight;
            else measuredHeight = trackHeight;
        }
        else if (heightMode == MeasureSpec.AT_MOST) {
            if (labelsEnabled) measuredHeight = Math.min(labelHeight + sliderHeight, height);
            else measuredHeight = Math.min(sliderHeight, height);
        }
        else {
            measuredHeight = height;
        }

        setMeasuredDimension(width, measuredHeight);
    }

    private RectF fullFillRect = new RectF();
    private RectF trackRect = new RectF();
    private float trackRoundRadius;

    private Rect startRect = new Rect();
    private Rect endRect = new Rect();

    public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        if (labelsEnabled) margin = (height - (sliderHeight + labelHeight)) / 2;
        else margin = (height - sliderHeight) / 2;

        fullFillRect.left = handleGlowRadius;
        fullFillRect.right = width - handleGlowRadius;
        fullFillRect.top = (int) (margin + (sliderHeight - trackHeight) / 2 + 0.5f);
        fullFillRect.bottom = fullFillRect.top + trackHeight;

        trackRect.left = fullFillRect.left + pixels.get(1) / 2f;
        trackRect.top = fullFillRect.top + pixels.get(1) / 2f;
        trackRect.right = fullFillRect.right - pixels.get(1) / 2f;
        trackRect.bottom = fullFillRect.bottom - pixels.get(1) / 2f;

        startRect.top = margin;
        startRect.bottom = margin + sliderHeight;
        updateStartInteractRect();

        endRect.top = startRect.top;
        endRect.bottom = startRect.bottom;
        updateEndInteractRect();

        trackRoundRadius = trackHeight / 2f;
        updateLabelRanges();
    }

    private float getUserVisibleRange(float position) {
        return Utils.interpolateValues(position, rangeStart, rangeEnd);
    }

    private void onPositionsChanged() {
        updateStartInteractRect();
        updateEndInteractRect();
    }

    private void updateLabelRanges() {
        float position = fullFillRect.left;
        labelRanges.clear();
        labelRanges = new ArrayList<Float>();

        // Resolve width per label
        float padding = fullFillRect.width();
        for (CharSequence label : labels) {
            padding -= LabelPaint.measureText(label, 0, label.length());
        }
        padding = padding / (labels.size() - 1); // There are labels - 1 spaces in total

        labelRanges.add(padding);

        for (CharSequence label : labels) {
            labelRanges.add(position + LabelPaint.measureText(label, 0, label.length()) / 2);
            position += LabelPaint.measureText(label, 0, label.length()) + padding;
        }

        invalidate();
    }

    public int getLabelForPercentage(float percentage) {
        percentage = Utils.getIntervalPercentage(percentage, rangeStart, rangeEnd);

        float position = percentage * fullFillRect.width();

        float minRange = - labelRanges.get(0) / 2;
        float maxRange = minRange;

        int i = 0;
        for (CharSequence label : labels) {
            maxRange += LabelPaint.measureText(label, 0, label.length()) + labelRanges.get(0);
            if (position >= minRange && position <= maxRange) return i;

            minRange = maxRange;
            i++;
        }

        return -1;
    }

    public float getLabelExactCenter(int labelIndex) {
        float minRange = - labelRanges.get(0) / 2;
        float maxRange = minRange;

        int i = 0;
        for (CharSequence label : labels) {
            maxRange += LabelPaint.measureText(label, 0, label.length()) + labelRanges.get(0);
            if (i == labelIndex) {
                float pixelRange = minRange + (maxRange - minRange) / 2f;
                return getUserVisibleRange(pixelRange / fullFillRect.width());
            }

            minRange = maxRange;
            i++;
        }

        return -1;
    }

    public CharSequence getLabelAtIndex(int index) {
        return labels.get(index);
    }

    public void setSelectedLabel(int label) {
        selectedLabel = label;
        invalidate();
    }

    public int getSelectedLabel() {
        return selectedLabel;
    }

    private void updateInteractRects() {
        updateStartInteractRect();
        updateEndInteractRect();
    }

    private void updateStartInteractRect() {
        if (sliderType == SliderTypeRange) {
            startRect.left = (int) (trackRect.left + trackRect.width() * startPosition - pixels.get(24));
            startRect.right = (int) (trackRect.left + trackRect.width() * startPosition + pixels.get(24));
        }
        else {
            startRect.left = Integer.MIN_VALUE;
            startRect.right = Integer.MIN_VALUE;
        }
    }

    private void updateEndInteractRect() {
        endRect.left = (int) (trackRect.left + trackRect.width() * endPosition - pixels.get(24));
        endRect.right = (int) (trackRect.left + trackRect.width() * endPosition + pixels.get(24));
    }

    public void setPopupListener(PopupListener listener) {
        popupListener = listener;
    }

    public PopupListener getPopupListener() {
        return popupListener;
    }

    private int trackedHandle = HandleNone;
    private float lastX;
    private float baseBalloonDisplacement;
    private float balloonYBase;

    private TextView balloonPopup;
    private Rect globalVisibleRect = new Rect();

    protected void createBalloonPopup() {

        balloonPopup = new TextView(getContext()) {
            @Override
            public void setTranslationX(float translationX) {
                if (getX() + getWidth() > metrics.widthPixels) {
                    float translation = getTranslationX();
                    super.setTranslationX(getTranslationX() -
                            (getX() + getWidth() - metrics.widthPixels));

                    ((PopoverDrawable) getBackground()).setBalloonTranslation(translation - getTranslationX());
                }
                else if (getX() + getWidth() < metrics.widthPixels) { // strictly lesser prevents recursion
                    ((PopoverDrawable) getBackground()).setBalloonTranslation(0);
                    super.setTranslationX(translationX);
                }
                else super.setTranslationX(translationX);
            }
        };
        balloonPopup.setTextSize(18);
        balloonPopup.setTypeface(Utils.CondesedTypeface);
        balloonPopup.setTextColor(0xFFFFFFFF);
        balloonPopup.setBackgroundDrawable(new PopoverDrawable(getContext()).setShadowRadius(8, 3, true));
        if (sliderType == SliderTypeColor) {
            ((PopoverDrawable) balloonPopup.getBackground()).setEdgeColor(trackEdgeColor);
//            ((PopoverDrawable) balloonPopup.getBackground()));
            balloonPopup.setShadowLayer(pixels.get(1), 0, 0, Utils.transparentColor(128, 0));
        }
        balloonPopup.addOnLayoutChangeListener(new OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                if (view.getX() + view.getWidth() > metrics.widthPixels) {
                    float translation = view.getTranslationX();
                    view.setTranslationX(view.getTranslationX() -
                        view.getX() + view.getWidth() - metrics.widthPixels);

                    ((PopoverDrawable) view.getBackground()).setBalloonTranslation(translation - view.getTranslationX());
                }
            }
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;

        ((ViewGroup) getRootView()).addView(balloonPopup, params);

    }

    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {

            if (rangeAnimator != null) rangeAnimator.end();

            getParent().requestDisallowInterceptTouchEvent(true);
            if (startRect.contains((int) event.getX(), (int) event.getY())) {
                trackedHandle = HandleStart;
            }
            else if (endRect.contains((int) event.getX(), (int) event.getY())) {
                trackedHandle = HandleEnd;
            }
            else {
                if (sliderType == SliderTypeRange) {
                    trackedHandle = HandleNone;
                }
                else {
                    trackedHandle = HandleEnd;
                    float position = (event.getX() - trackRect.left) / trackRect.width();
                    endPosition = Utils.constrain(position, startPosition + minimumRange, 1f);
                    if (rangeListener != null) {
                        rangeListener.onRangeChange(getUserVisibleRange(startPosition), getUserVisibleRange(endPosition), true);
                    }
                }
            }

            invalidate();

            lastX = event.getX();

            if (trackedHandle != HandleNone && popupListener != null) {
                getGlobalVisibleRect(globalVisibleRect);
                balloonYBase = globalVisibleRect.centerY();
                if (labelsEnabled) balloonYBase -= labelHeight / 2;
                baseBalloonDisplacement = - getRootView().getWidth() / 2 + globalVisibleRect.exactCenterX() - globalVisibleRect.width() / 2f + trackRect.left;

                createBalloonPopup();
                final float Position = trackedHandle == HandleStart ? startPosition : endPosition;

                balloonPopup.setText(popupListener.getPopupLabel(getUserVisibleRange(Position)));
                if (sliderType == SliderTypeColor) {
                    ((PopoverDrawable) balloonPopup.getBackground()).setFillColor(colors.get(
                            (int) (Utils.constrain(endPosition * colors.size(), 0, colors.size() - 1))
                    ));
                }
                balloonPopup.setAlpha(0f);

                final View BalloonPopup = balloonPopup;
                balloonPopup.post(new Runnable() {
                    @Override
                    public void run() {
//                        Utils.ViewUtils.centerViewOnPoint(BalloonPopup, globalVisibleRect.exactCenterX(), globalVisibleRect.centerY() - BalloonPopup.getHeight() / 2);
                        BalloonPopup.setY(balloonYBase - BalloonPopup.getHeight());
                        BalloonPopup.setTranslationX(baseBalloonDisplacement + Position * trackRect.width());

                        BalloonPopup.setPivotX(BalloonPopup.getWidth() / 2 + ((PopoverDrawable) BalloonPopup.getBackground()).getBalloonTranslation());
                        BalloonPopup.setPivotY(BalloonPopup.getHeight());
                        BalloonPopup.setScaleY(0.8f);
                        BalloonPopup.setScaleX(0.8f);
                        BalloonPopup.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).setInterpolator(new OvershootInterpolator(3f));
                    }
                });
            }
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            lastX = event.getX();

            float position = (event.getX() - trackRect.left) / trackRect.width();
            float currentPosition = 0f;
            if (trackedHandle == HandleStart) {
                startPosition = Utils.constrain(position, 0f, endPosition - minimumRange);
                if (endPosition - startPosition > maximumRange) {
                    endPosition = startPosition + maximumRange;
                }
                if (position > endPosition) {
                    trackedHandle = HandleEnd;
                }
                if (rangeListener != null) {
                    rangeListener.onRangeChange(getUserVisibleRange(startPosition), getUserVisibleRange(endPosition), true);
                }

                currentPosition = startPosition;
            }
            else if (trackedHandle == HandleEnd) {
                endPosition = Utils.constrain(position, startPosition + minimumRange, 1f);
                if (sliderType == SliderTypeRange) {
                    if (endPosition - startPosition > maximumRange) {
                        startPosition = endPosition - maximumRange;
                    }
                    if (position < startPosition) {
                        trackedHandle = HandleStart;
                    }

                    // Amount sliders cannot enforce maximum range or swap handles
                }

                if (rangeListener != null) {
                    rangeListener.onRangeChange(getUserVisibleRange(startPosition), getUserVisibleRange(endPosition), true);
                }

                currentPosition = endPosition;
            }

            if (balloonPopup != null && popupListener != null) {
                if (sliderType == SliderTypeColor) {
                    ((PopoverDrawable) balloonPopup.getBackground()).setFillColor(colors.get(
                            (int) (Utils.constrain(endPosition * colors.size(), 0, colors.size() - 1))
                    ));
                }
                balloonPopup.setText(popupListener.getPopupLabel(getUserVisibleRange(currentPosition)));
                balloonPopup.setY(balloonYBase - balloonPopup.getHeight());
                balloonPopup.setTranslationX(baseBalloonDisplacement + currentPosition * (fullFillRect.width() - trackHeight) + trackHeight / 2);
                balloonPopup.setPivotX(balloonPopup.getWidth() / 2 + ((PopoverDrawable) balloonPopup.getBackground()).getBalloonTranslation());
                balloonPopup.setPivotY(balloonPopup.getHeight());
            }

            invalidate();
        }

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            onPositionsChanged();
            getParent().requestDisallowInterceptTouchEvent(false);
            invalidate();
            trackedHandle = HandleNone;

            if (rangeListener != null) {
                rangeListener.onRangeSelected(getUserVisibleRange(startPosition), getUserVisibleRange(endPosition), true);
            }

            if (balloonPopup != null) {
                final View BalloonPopup = balloonPopup;
                BalloonPopup.setPivotX(balloonPopup.getWidth() / 2 + ((PopoverDrawable) BalloonPopup.getBackground()).getBalloonTranslation());
                BalloonPopup.setPivotY(balloonPopup.getHeight());
                BalloonPopup.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f).setDuration(250).setInterpolator(new AnticipateInterpolator(3f)).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        ((ViewGroup) getRootView()).removeView(BalloonPopup);
                    }
                });
                balloonPopup = null;
            }
        }

        return true; // Always handle incoming events
    }

    private RectF fillRect = new RectF();
    private RectF colorRect = new RectF();
    private Rect bounds = new Rect();

    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        EdgePaint.setColor(trackEdgeColor);
        EdgePaint.setStrokeWidth(pixels.get(1));

        FillPaint.setColor(handleGlowColor);

        fillRect.left = fullFillRect.left + (fullFillRect.width() - trackHeight) * startPosition;
        fillRect.right = fullFillRect.left + (fullFillRect.width() - trackHeight) * endPosition + trackHeight / 2f;
//        if (fillRect.right < fillRect.left + trackHeight) {
//            fillRect.right = fillRect.left + trackHeight;
//        }

        fillRect.top = fullFillRect.top;
        fillRect.bottom = fullFillRect.bottom;

        // Draw fill first
        if (sliderType != SliderTypeColor) {
            canvas.drawRoundRect(fillRect, trackRoundRadius, trackRoundRadius, FillPaint);
        }
        else {
            colorRect.top = fullFillRect.top;
            colorRect.bottom = fullFillRect.bottom;
            if (colors.size() == 1) {
                FillPaint.setColor(colors.get(0));
                canvas.drawRoundRect(fullFillRect, trackRoundRadius, trackRoundRadius, FillPaint);
            }
            else for (int i = 0; i < colors.size(); i++) {
                FillPaint.setColor(colors.get(i));
                colorRect.left = fullFillRect.left + (int) (fullFillRect.width() / colors.size() * i + 0.5f);
                if (i < colors.size() - 1) {
                    colorRect.right = fullFillRect.left + (int) (fullFillRect.width() / colors.size() * (i + 1) + 0.5f);
                }
                else {
                    colorRect.right = fullFillRect.right;
                }
                if (i == 0) {
                    drawOneSideRoundRect(canvas, colorRect, trackRoundRadius, true, FillPaint);
                }
                else if (i == colors.size() - 1) {
                    drawOneSideRoundRect(canvas, colorRect, trackRoundRadius, false, FillPaint);
                }
                else {
                    canvas.drawRect(colorRect, FillPaint);
                }
            }

        }

        // Then the borders
        canvas.drawRoundRect(trackRect, trackRoundRadius, trackRoundRadius, EdgePaint);

        if (sliderType == SliderTypeColor) {
            FillPaint.setColor(Utils.transparentColor(51, colors.get(
                    (int) (Utils.constrain(endPosition * colors.size(), 0, colors.size() - 1)) // prevents outofbounds at exactly 1
            )));
        }

        // Draw the handle glows
        if (sliderType == SliderTypeRange) {
            canvas.drawCircle(fillRect.left + trackHeight / 2f,
                    fillRect.top + trackHeight / 2f,
                    trackHeight / 2f + handleGlowRadius,
                    FillPaint);
        }
        canvas.drawCircle(fillRect.left - startPosition * (fullFillRect.width() - trackHeight) + endPosition * (fullFillRect.width() - trackHeight) + trackHeight / 2f,
                fillRect.top + trackHeight / 2f,
                trackHeight / 2f + handleGlowRadius,
                FillPaint);

        if (trackedHandle != HandleNone) {
            float dp2 = pixels.get(2);
            if (trackedHandle == HandleStart) {
                EdgePaint.setColor(handleColor);
                EdgePaint.setStrokeWidth(dp2);
                canvas.drawCircle(fillRect.left + trackHeight / 2f,
                        fillRect.top + trackHeight / 2f,
                        trackHeight / 2f + handleGlowRadius - dp2 / 2f,
                        EdgePaint);
            }
            else if (trackedHandle == HandleEnd) {
                if (sliderType == SliderTypeColor) {
                    EdgePaint.setColor(trackEdgeColor);
                }
                else {
                    EdgePaint.setColor(handleColor);
                }
                EdgePaint.setStrokeWidth(dp2);
                canvas.drawCircle(fillRect.left - startPosition * (fullFillRect.width() - trackHeight) + endPosition * (fullFillRect.width() - trackHeight) + trackHeight / 2f,
                        fillRect.top + trackHeight / 2f,
                        trackHeight / 2f + handleGlowRadius - dp2 / 2f,
                        EdgePaint);
            }
        }

        // Draw the handles
        FillPaint.setColor(handleColor);

        if (sliderType == SliderTypeRange) {
            canvas.drawCircle(fillRect.left + trackHeight / 2f,
                    fillRect.top + trackHeight / 2f,
                    trackHeight / 2f,
                    FillPaint);
        }
        if (sliderType == SliderTypeColor) {
            EdgePaint.setStrokeWidth(pixels.get(2));
            // Color sliders need an outer stroke for the handle
            canvas.drawCircle(fillRect.left - startPosition * (fullFillRect.width() - trackHeight) + endPosition * (fullFillRect.width() - trackHeight) + trackHeight / 2f,
                    fillRect.top + trackHeight / 2f,
                    trackHeight / 2f, // Radius
                    EdgePaint);
        }
        canvas.drawCircle(fillRect.left - startPosition * (fullFillRect.width() - trackHeight) + endPosition * (fullFillRect.width() - trackHeight) + trackHeight / 2f,
                fillRect.top + trackHeight / 2f,
                trackHeight / 2f,
                FillPaint);

        // TODO: labels
        LabelPaint.getTextBounds("A", 0, 1, bounds);
        float y = margin + sliderHeight + bounds.height() / 2;

        int i = 1;
        for (CharSequence label : labels) {
            if (i - 1 == selectedLabel || selectedLabel == -1) {
                LabelPaint.setColor(getResources().getColor(Utils.DashboardText));
            }
            else {
                LabelPaint.setColor(Utils.transparentColor(0x33, getResources().getColor(Utils.DashboardTitle)));
            }
            canvas.drawText(label, 0, label.length(), labelRanges.get(i), y, LabelPaint);
            i++;
        }
    }

    RectF oneSideRoundRectBounds = new RectF();
    protected void drawOneSideRoundRect(Canvas c, RectF bounds, float radius, boolean leftSide, Paint paint) {
        oneSideRoundRectBounds.set(bounds);
        if (leftSide) {
            oneSideRoundRectBounds.right = oneSideRoundRectBounds.left + bounds.width() / 2 + radius;
        }
        else {
            oneSideRoundRectBounds.left = oneSideRoundRectBounds.right - bounds.width() / 2 - radius;
        }

        c.drawRoundRect(oneSideRoundRectBounds, radius, radius, paint);
        if (leftSide) {
            c.drawRect(bounds.left + bounds.width() / 2, bounds.top, bounds.right, bounds.bottom, paint);
        }
        else {
            c.drawRect(bounds.left, bounds.top, bounds.right - bounds.width() / 2, bounds.bottom, paint);
        }
    }

    public PrecisionRangeSlider(Context context) {
        super(context);
        init();
    }

    public PrecisionRangeSlider(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PrecisionRangeSlider(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }
}
