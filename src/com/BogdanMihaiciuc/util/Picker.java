package com.BogdanMihaiciuc.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

//import com.BogdanMihaiciuc.receipt.Receipt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Picker extends View implements AbstractScroller.ScrollEventListener {

    final static String TAG = Picker.class.getName();
    final static boolean DEBUG = false;

    public interface PickerListener {
        public void onValueChanged(Picker picker, int index, boolean fromUser);
        public void onValueSelected(Picker picker, int index, boolean fromUser);
    }


    final static int RowHeightDP = 48;
    final static int FullOpacityBandDP = 64;
    final static int MinimumVelocityDP = 2;

    final static int TextColor = 0xFFFFFFFF;
    final static int SelectorColor = Resources.getSystem().getColor(android.R.color.holo_blue_light);

    final static Paint TextPaint;
    final static Paint SelectorPaint;
    static {
        TextPaint = new Paint();
        TextPaint.setAntiAlias(true);
        TextPaint.setTypeface(Utils.DefaultTypeface);
        TextPaint.setTextAlign(Paint.Align.CENTER);

        SelectorPaint = new Paint();
        SelectorPaint.setStyle(Paint.Style.FILL);
        SelectorPaint.setColor(SelectorColor);
    }

    private List<Object> values = new ArrayList<Object>();

    private Utils.DPTranslator pixels;
    private int rowHeight;
    private int fullOpacityBand;

    private int value;

    private int textColor = TextColor;

    private AbstractScroller scroller = new AbstractScroller();
    private VelocityTracker tracker = VelocityTracker.obtain();

    private PickerListener listener;

    private int previousY;
    private int distanceY;
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            scroller.cancelScrolling();

            previousY = (int) event.getY();
            tracker.addMovement(event);
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            distanceY = (int) event.getY();
            tracker.addMovement(event);

            scroller.setCurrentDistance(scroller.getCurrentDistance() + (distanceY - previousY));
            previousY = distanceY;

            if (listener != null) {
                listener.onValueChanged(this, indexFromPosition(scroller.getCurrentDistance()), true);
            }
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {
            tracker.addMovement(event);
            tracker.computeCurrentVelocity(1); // 1 returns a velocity expressed in px/ms

            float velocity = tracker.getYVelocity();
            if (Math.abs(velocity) < pixels.get(MinimumVelocityDP)) {
                velocity = pixels.get(MinimumVelocityDP) * Math.signum(velocity);
                int targetPosition = scroller.getCurrentDistance();

                int previousPosition = targetPosition;
                targetPosition = Utils.getClosestMultiple(targetPosition, rowHeight);

                if (DEBUG) Log.d(TAG, "Target Position is " + targetPosition + " with a remainder of " + (targetPosition % rowHeight));
                if (DEBUG) Log.d(TAG, "The closest multiple of " + previousPosition + " is " + targetPosition);
                scroller.prepareScrollingFromInitialSpeedWithTargetDistance(velocity, targetPosition);
                scroller.beginScrolling();

                tracker.clear();
                return true;
            }

            scroller.prepareScrollingFromInitialSpeed(velocity);
            int targetPosition = Utils.getClosestMultiple(scroller.getComputedFinalScrollPosition(), rowHeight);

            scroller.prepareScrollingFromInitialSpeedWithTargetDistance(velocity, targetPosition);
            scroller.beginScrolling();

            tracker.clear();
        }

        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            tracker.computeCurrentVelocity(1); // 1 returns a velocity expressed in px/ms

            float velocity = tracker.getYVelocity();
            velocity = pixels.get(MinimumVelocityDP) * Math.signum(velocity);
            int targetPosition = scroller.getCurrentDistance();

            int previousPosition = targetPosition;
            targetPosition = Utils.getClosestMultiple(targetPosition, rowHeight);

            if (DEBUG) Log.d(TAG, "Target Position is " + targetPosition + " with a remainder of " + (targetPosition % rowHeight));
            if (DEBUG) Log.d(TAG, "The closest multiple of " + previousPosition + " is " + targetPosition);
            scroller.prepareScrollingFromInitialSpeedWithTargetDistance(velocity, targetPosition);
            scroller.beginScrolling();

            tracker.clear();
            return true;
        }
        return true; // The picker will always eat up all motion events
    }

    public void setValues(List values) {
        this.values = values;
        invalidate();
    }

    public List getValues() {
        return Collections.unmodifiableList(values);
    }

    public int getWrapLimit() {
        return values.size() * rowHeight;
    }

    public void setTextColor(int color) {
        textColor = color;
        invalidate();
    }

    public void setCurrentValue(int index) {
        scroller.setCurrentDistance(- index * rowHeight);

        value = index;
    }

    public boolean setCurrentValue(CharSequence value) {
        int index = 0;

        for (Object object : values) {
            if (object.toString().equals(value)) {
                setCurrentValue(index);
                return true;
            }

            index++;
        }

        return false;
    }

    /**
     * Gets the currently selected index.
     * @return The currently selected index.
     */
    public int getValue() {
        return value;
    }

    public void setPickerListener(PickerListener listener) {
        this.listener = listener;
    }

    public PickerListener getPickerListener() {
        return listener;
    }

    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);

        if (!hasFocus) {
            scroller.endScrolling();
        }
    }

    private int wrapPosition(int position) {
        if (values.size() == 0) return 0;
        if (position < 0) {
            position = values.size() + position % values.size();
        }
        return position % values.size();
    }

    private int obtainReversedPosition(int position) {
        return values.size() - position - 1;
    }

    private String obtainLabelForPosition(int position) {
//        position = obtainReversedPosition(position);
        return values.get(wrapPosition(position)).toString();
    }

    private int indexFromPosition(int scrollPosition) {
        return obtainReversedPosition(wrapPosition(scrollPosition / rowHeight - 1));
    }


    private int height;
    private int centerX;
    private int centerY;
    private int transparentBandBottom;
    private int transparentBandTop;
    public void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);

        this.height = height;
        this.centerX = width / 2;
        this.centerY = height / 2;

        transparentBandBottom = centerY - rowHeight / 2;
        transparentBandTop = centerY + rowHeight / 2;

        if (DEBUG) {
            Log.d(TAG, "Height is " + height + " center is " + centerY);
        }
    }

    private AccelerateInterpolator accelerator = new AccelerateInterpolator(1f);
    private DecelerateInterpolator decelerator = new DecelerateInterpolator(1f);

    public void onDraw(Canvas canvas) {
        TextPaint.setTextSize(pixels.get(20));

        int scrollPosition = - scroller.getCurrentDistance();
        int drawPosition = scrollPosition - centerY;
        int displacement = - Utils.distanceFromMultiple(scrollPosition - centerY, rowHeight);
        int startingIndex = (drawPosition + displacement) / rowHeight;
        if (DEBUG) {
            Log.d(TAG, "The displacement is " + displacement + " for the drawing position " + drawPosition + " with a starting index of " + startingIndex);
        }

        int currentPosition = displacement;
        int currentIndex = startingIndex;
        do {
            canvas.save();
            if (currentPosition > transparentBandBottom && currentPosition < transparentBandTop) {
                TextPaint.setColor(textColor);
            }
            else if (currentPosition <= transparentBandBottom) {
                float alpha = currentPosition / (float)transparentBandBottom;
                alpha = accelerator.getInterpolation(alpha < 0f ? 0f : alpha);
                TextPaint.setColor(Utils.interpolatedTransparentColor(alpha, textColor));
            }
            else {
                float alpha = 1f - (currentPosition - transparentBandTop) / (float)transparentBandBottom;
                alpha = accelerator.getInterpolation(alpha < 0f ? 0f : alpha);
                TextPaint.setColor(Utils.interpolatedTransparentColor(alpha, textColor));
            }

            if (currentPosition < centerY) {
                float scale = Utils.interpolateValues(decelerator.getInterpolation(currentPosition / (float) centerY), 0.5f, 1f);
                canvas.scale(scale, scale, centerX, currentPosition);
            }
            else {
                float scale = Utils.interpolateValues(decelerator.getInterpolation(1 - (currentPosition - centerY) / (float) centerY), 0.5f, 1f);
                canvas.scale(scale, scale, centerX, currentPosition);
            }

            String label = obtainLabelForPosition(currentIndex);
            canvas.drawText(label, centerX, currentPosition - (TextPaint.descent() + TextPaint.ascent()) / 2 , TextPaint);
            canvas.restore();

            if (DEBUG) {
                Log.d(TAG, "Current position is " + currentPosition);
            }

            currentIndex++;
            currentPosition += rowHeight;
        } while (currentPosition - rowHeight / 2 < height);

        canvas.drawRect(0, transparentBandBottom, getWidth(), transparentBandBottom + pixels.get(2), SelectorPaint);
        canvas.drawRect(0, transparentBandTop - pixels.get(2), getWidth(), transparentBandTop, SelectorPaint);
    }

    // Instance init block
    {
        pixels = new Utils.DPTranslator(getContext().getResources().getDisplayMetrics().density);
        rowHeight = pixels.get(RowHeightDP);
        fullOpacityBand = pixels.get(FullOpacityBandDP);

        scroller.setFriction(0.20f);
        scroller.setScrollEventListener(this);
    }

    public Picker(Context context) {
        super(context);
    }

    public Picker(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public Picker(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public Picker(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    public void onSrollStarted(AbstractScroller scroller) {
        invalidate();
    }

    @Override
    public void onScrollPositionChanged(AbstractScroller scroller, int newPosition) {
        invalidate();
        if (listener != null) {
            value = indexFromPosition(newPosition);
            listener.onValueChanged(this, value, true);
        }
    }

    public void onScrollCancelled(AbstractScroller scroller) {
        invalidate();
    }

    @Override
    public void onScrollFinished(AbstractScroller scroller) {
        invalidate();
        if (listener != null) {
            value = indexFromPosition(scroller.getCurrentDistance());

            listener.onValueSelected(this, value, true);
        }
    }
}
