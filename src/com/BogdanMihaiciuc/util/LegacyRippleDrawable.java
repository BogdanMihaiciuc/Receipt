package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.ViewAnimator;

import org.xmlpull.v1.XmlPullParser;

import java.lang.ref.WeakReference;

public class LegacyRippleDrawable extends Drawable {

    final static String TAG = LegacyRippleDrawable.class.getName();

    final static boolean USE_FADING_RIPPLE = true;
    final static boolean DEBUG_STATES = false;
    final static boolean DEBUG_DISAPPEARING_RIPPLE = false;

    /**
     * The ViewProxy animation queue on which the outward ripple animation runs.
     */
    public final static String RippleQueue = "LRD$RQ";
    /**
     * The ViewProxy animation queue on which TBD animation runs // TODO
     */
    public final static String BackgroundQueue = "LRD$BQ";
    /**
     * The ViewProxy animation queue on which the background color change animations run.
     */
    public final static String ColorQueue = "LRD$CQ";
    /**
     * The ViewProxy animation queue on which color flash animations run.
     */
    public final static String FlashQueue = "LRD$FQ";

    public final static int ShapeRect = 0;
    public final static int ShapeRoundRect = 1;
    public final static int ShapeCircle = 2;
    final static int ShapeCustom = 3;

    public final static int RadiusTypeInset = 0;
    public final static int RadiusTypeTallEdge = 1;
    public final static int RadiusTypeWideEdge = 2;
    public final static int RadiusTypeCustom = 3;

    final static int DefaultRoundnessDP = 4;

    public final static int DefaultBackgroundColor = 0x00000000;
    public final static int DefaultPressedColor = 0x10001020;
    public final static int DefaultRippleColor = 0x25001020;
    public final static int DefaultLightPressedColor = Utils.transparentColor(0.07f, 0xDDEEFF);
    public final static int DefaultLightRippleColor = Utils.transparentColor(0.13f, 0xDDEEFF);
    public final static int DefaultSelectedColor = Utils.transparentColor(0.5f, Resources.getSystem().getColor(android.R.color.holo_blue_light));
    public final static int DefaultNotificationColor = Utils.transparentColor(0.5f, Resources.getSystem().getColor(android.R.color.holo_orange_dark));

    final static int StatePressed = android.R.attr.state_pressed;
    final static int StateSelected = android.R.attr.state_selected;
    final static int StateNotification = android.R.attr.state_activated;
    final static int StateEnabled = android.R.attr.state_enabled;

    final static Paint RipplePaint;
    public final static TimeInterpolator FlagAnimationCancelled = new LinearInterpolator();

    static {
        RipplePaint = new Paint();
        RipplePaint.setAntiAlias(true);
        RipplePaint.setStyle(Paint.Style.FILL);
    }

    private Utils.DPTranslator pixels;
    private Context context;
    private Utils.RippleAnimationStack stack;
    private View callback;

    private int shape = ShapeRoundRect;

    // RoundRect Specific
    private float roundness;

    // Circle Specific
    private float radius;
    private int radiusType = RadiusTypeWideEdge;

    private int backgroundColor = DefaultBackgroundColor;
    private int pressedColor = DefaultPressedColor;
    private int selectedColor = DefaultSelectedColor;
    private int selectedPressedColor = Utils.overlayColors(DefaultSelectedColor, DefaultPressedColor);
    private int notificationColor = DefaultNotificationColor;
    private int selectedNotificationColor = Utils.overlayColors(DefaultNotificationColor, DefaultPressedColor);
    private int rippleColor = DefaultRippleColor;

    private int flashColor;
    private float flashCompletion;
    private ValueAnimator flashAnimator;

    private float rippleX, rippleY, rippleCompletion, rippleSize;
    private boolean rippleActive = false;
    private View.OnTouchListener forwardListener;

    private boolean prepressed = false;

    private int currentColor = backgroundColor;
    private int workingBackgroundColor = backgroundColor;
    private int workingPressedColor = pressedColor;
    private int[] previousState = new int[0];

    private ValueAnimator colorAnimator;
    private ValueAnimator rippleAnimator;
    private ValueAnimator backgroundRipple;

    private Rect bounds;
    private RectF boundsF;

    private WeakReference<RippleDrawableDelegate> delegate;

    public boolean debug = false;
    private boolean pendingAnimationDismissed;

    public LegacyRippleDrawable(Context context) {
        super();
        this.context = context;
        stack = (Utils.RippleAnimationStack) context;

        pixels = new Utils.DPTranslator(context.getResources().getDisplayMetrics().density);
        roundness = pixels.get(2);
    }

    public LegacyRippleDrawable(Context context, int shape) {
        this(context);

        setShape(shape);
    }

    private Handler handler = new Handler();
    private Runnable dismissPrepressRunnable = new Runnable() {
        @Override
        public void run() {
            prepressed = false;
        }
    };

    private View.OnTouchListener listener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {

            rippleX = Utils.constrain(event.getX(), 0, v.getWidth());
            rippleY = Utils.constrain(event.getY(), 0, v.getHeight());

            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                handler.removeCallbacks(dismissPrepressRunnable);
                prepressed = true;
                handler.postDelayed(dismissPrepressRunnable, ViewConfiguration.getTapTimeout());

            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                int slop = ViewConfiguration.get(v.getContext()).getScaledTouchSlop();
                if (event.getX() < - slop || event.getY() < - slop || event.getX() > v.getWidth() + slop || event.getY() > v.getHeight() + slop) {
                    prepressed = false;
                    handler.removeCallbacks(dismissPrepressRunnable);
                }
            }
            if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                prepressed = false;
                handler.removeCallbacks(dismissPrepressRunnable);
            }
            if (event.getAction() == MotionEvent.ACTION_UP) {
                handler.removeCallbacks(dismissPrepressRunnable);
                if (hasState(previousState, StateEnabled) && prepressed && !hasState(previousState, StateSelected)) {
                    // only do this if the ripple isn't currently running or being dismissed
                    if (backgroundRipple == null && rippleAnimator == null) {

                        if (colorAnimator != null) {
                            colorAnimator.end();
                        }

                        currentColor = workingPressedColor;
                        invalidateSelf();
                    }
                }
            }
            if (forwardListener != null) {
                return forwardListener.onTouch(v, event);
            }
            return false;
        }
    };

    public void setForwardListener(View.OnTouchListener listener) {
        forwardListener = listener;
    }

    public View.OnTouchListener getForwardListener() {
        return forwardListener;
    }

    public void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        this.callback = (View) getCallback();

        callback.setOnTouchListener(listener);

        bounds = getBounds();
        boundsF = new RectF(bounds);
    }

    public boolean isStateful() {
        return true;
    }

    public LegacyRippleDrawable setShape(int shape) {
        if (shape == ShapeCircle && Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN) {
            shape = ShapeRoundRect;
        }
        this.shape = shape;
        return this;
    }

    protected boolean hasState(int[] states, int checkedState) {
        for (int state : states) {
            if (state == checkedState) return true;
        }

        return false;
    }

    @Override
    public boolean onStateChange(int[] states) {

        final boolean AnimationDisabled = pendingAnimationDismissed;
        pendingAnimationDismissed = false;

        if (!hasState(states, StateEnabled)) {
            if (hasState(previousState, StateEnabled)) {
                dismissRipple();
                transitionToColor(backgroundColor, AnimationDisabled ? 0 : 100);
                return true;
            }
            return false;
        }

        boolean stateChanged = false;

        if (hasState(states, StatePressed) && !hasState(previousState, StatePressed)) {
            // Transitioning into pressed state; the ripple must become enabled
            beginRipple();
            prepressed = false;
            transitionToColor(workingPressedColor, AnimationDisabled ? 0 : 100);
            if (DEBUG_STATES) Log.e(TAG, "State transition detected! from not-pressed into pressed");
            stateChanged = true;
        }
        else if (!hasState(states, StatePressed) && hasState(previousState, StatePressed)) {
            // Transitioning away from pressed state; the ripple must end
            dismissRipple();
            if (DEBUG_STATES) Log.e(TAG, "State transition detected! from pressed into not-pressed");
            stateChanged = true;
        }

        if (hasState(states, StateNotification)) {
            if (!hasState(previousState, StateNotification) || (hasState(previousState, StateSelected) != hasState(states, StateSelected))) {
                if (hasState(states, StateSelected)) {
                    setWorkingColors(notificationColor, Utils.overlayColors(notificationColor, pressedColor), !AnimationDisabled);
                    stateChanged = true;
                }
                else {
                    setWorkingColors(backgroundColor, pressedColor, !AnimationDisabled);
                    stateChanged = true;
                }
            }
        }
        else {
            if (hasState(previousState, StateNotification)) {
                if (hasState(states, StateSelected)) {
                    setWorkingColors(selectedColor, Utils.overlayColors(selectedColor, pressedColor), !AnimationDisabled);
                    stateChanged = true;
                }
                else {
                    setWorkingColors(backgroundColor, pressedColor);
                    stateChanged = true;
                }
            }
            else {
                if (hasState(states, StateSelected) && !hasState(previousState, StateSelected)) {
                    setWorkingColors(selectedColor, Utils.overlayColors(selectedColor, pressedColor), !AnimationDisabled);
                    stateChanged = true;
                }
                else if (!hasState(states, StateSelected) && hasState(previousState, StateSelected)) {
                    setWorkingColors(backgroundColor, pressedColor, !AnimationDisabled);
                    stateChanged = true;
                }
            }
        }

        previousState = new int[states.length];
        System.arraycopy(states, 0, previousState, 0, states.length);

        return stateChanged;
    }

    public void dismissPendingAnimation() {
        pendingAnimationDismissed = true;
    }

    public void dismissPendingFlushRequest() {
        if (rippleAnimator != null) {
            stack.removeRipple(rippleAnimator);
        }
        if (colorAnimator != null) {
            stack.removeRipple(colorAnimator);
        }
        if (backgroundRipple != null) {
            stack.removeRipple(backgroundRipple);
        }
        if (flashAnimator != null) {
            stack.removeRipple(flashAnimator);
        }
    }

    public void flushRipple() {
        // TODO maybe inspect rippleActive, in case this is called when there is no active ripple

        handler.removeCallbacks(dismissPrepressRunnable);
        prepressed = false;

        // Prevent the ending of the color animation from applying the pressed color by dismissing the current first part of the ripple
        dismissRipple();

        if (rippleAnimator != null) {
            rippleAnimator.setInterpolator(FlagAnimationCancelled);
            rippleAnimator.end();
        }
        if (colorAnimator != null) {
            colorAnimator.setInterpolator(FlagAnimationCancelled);
            colorAnimator.end();
        }
        if (backgroundRipple != null) {
            backgroundRipple.setInterpolator(FlagAnimationCancelled);
            backgroundRipple.end();
        }
        if (flashAnimator != null) {
            flashAnimator.setInterpolator(FlagAnimationCancelled);
            flashAnimator.end();
        }
    }

    public void beginRipple() {
        stack.flushRipples();
        if (rippleAnimator != null) {
            rippleAnimator.cancel();
        }

        rippleActive = true;

        rippleAnimator = ValueAnimator.ofFloat(0f, 1f);
        rippleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                rippleSize = animation.getAnimatedFraction();

                rippleCompletion = rippleSize / 2f + 0.5f;
                invalidateSelf();
            }
        });
        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationCancel(Animator animator) {
                for (int i = 0; i < previousState.length; i++) {
                    if (previousState[i] == StatePressed) {
                        previousState[i] = Integer.MIN_VALUE;
                    }
                }
            }

            public void onAnimationEnd(Animator animation) {
                stack.removeRipple(animation);

                if (((ValueAnimator) animation).getInterpolator() == FlagAnimationCancelled) {
                    rippleCompletion = 0;
                    rippleSize = 0;
                }

                if (rippleAnimator == animation) {
                    rippleAnimator = null;
                }
            }
        });
        rippleAnimator.setInterpolator(new DecelerateInterpolator(1.5f));
        rippleAnimator.setDuration(800);
        rippleAnimator.start();
        stack.addRipple(rippleAnimator);
    }

//    public float getCompletion() {
//        return completion;
//    }
//
//    public void setCompletion(float completion) {
//        this.completion = completion;
//
//        if (rippleAnimator != null) {
//            rippleAnimator.setCurrentPlayTime((long) (rippleAnimator.getDuration() * completion));
//        }
//    }

    public void dismissRipple() {
        if (rippleAnimator != null && rippleAnimator.getDuration() == 800) {
            long currentPlayTime = rippleAnimator.getCurrentPlayTime();
            rippleAnimator.setDuration(200);
            rippleAnimator.setCurrentPlayTime((long) (200 * currentPlayTime / 800f));
            rippleAnimator.addListener(new AnimatorListenerAdapter() {
                boolean cancelled = false;

                @Override
                public void onAnimationCancel(Animator animation) {
                    cancelled = true;
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    if (!cancelled && !USE_FADING_RIPPLE) {
                        endRipple();
                    }

                    if (backgroundRipple == animation) {
                        backgroundRipple = null;
                    }
                }
            });

            if (USE_FADING_RIPPLE) {
                if (rippleAnimator.getInterpolator() != FlagAnimationCancelled) {
                    endRipple((long) Utils.constrain(200 + (200 - (long) (200 * currentPlayTime / 800f)), 200, 300), false);
                }
            }
        }
        else {
            if (rippleAnimator != null) {
                if (rippleAnimator.getInterpolator() != FlagAnimationCancelled) {
                    endRipple();
                }
                else {
                    endRipple(0, false);
                }
            }
            else {
                // if the ripple animator is null, the ripple must be fully visible or there is no reason to run this
                if (rippleCompletion == 1 && currentColor == workingPressedColor) {
                    endRipple();
                }
                else {
                    endRipple(0, false);
                }
            }
        }
    }


    public void endRipple() {
        endRipple(200, true);
    }

    public void endRipple(long duration, final boolean UpdateRippleState) {
        if (DEBUG_DISAPPEARING_RIPPLE) new Throwable().printStackTrace(System.err);

        if (hasState(previousState, StatePressed)) {
            currentColor = workingPressedColor;
        }
        transitionToColor(workingBackgroundColor, duration);

        if (!rippleActive) return;
        if (rippleAnimator != null) {
            if (UpdateRippleState) {
                rippleAnimator.cancel();
            }
            else {
                backgroundRipple = rippleAnimator;
            }
        }

        if (UpdateRippleState) {
            rippleSize = 1;
        }
        else {
            for (int i = 0; i < previousState.length; i++) {
                if (previousState[i] == StatePressed) {
                    previousState[i] = Integer.MIN_VALUE;
                }
            }
        }

        if (DEBUG_DISAPPEARING_RIPPLE) Log.e(TAG, "Ripple is being ended; UpdateRippleState is: " + UpdateRippleState + ", targetColor is 0x" + Integer.toHexString(workingBackgroundColor));

        rippleAnimator = ValueAnimator.ofFloat(0f, 1f);

        rippleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                rippleCompletion = 1 - valueAnimator.getAnimatedFraction();
                invalidateSelf();
            }
        });

        rippleAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationCancel(Animator animator) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                stack.removeRipple(animation);
                if (backgroundRipple != null) {
                    backgroundRipple.end();
                }
                if (rippleAnimator == animation) {
                    rippleAnimator = null;
                    rippleActive = false;
                }
            }
        });

        rippleAnimator.setDuration(duration);
        rippleAnimator.setInterpolator(new AccelerateInterpolator(1.5f));
        rippleAnimator.start();
        stack.addRipple(rippleAnimator);
    }

    public void flashColor(int color) {
        flashColor(color, 300, 2f);
    }

    public void flashColor(int color, long duration) {
        flashColor(color, duration, 2f);
    }

    /**
     * Flash the specified color.
     * @param color The color to flash.
     * @param duration The duration in milliseconds over which the flashing will take place.
     * @param cycles The amount of times the color will be flashed.
     */
    public void flashColor(int color, long duration, float cycles) {
        if (flashAnimator != null) {
            flashAnimator.end();
        }

        flashColor = color;

        flashAnimator = ValueAnimator.ofFloat(0f, 1f);
        flashAnimator.setDuration(duration);
        flashAnimator.setInterpolator(new CycleInterpolator(cycles));

        flashAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                flashCompletion = animation.getAnimatedFraction();
                invalidateSelf();
            }
        });

        flashAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation == flashAnimator) {
                    flashAnimator = null;
                }

                flashColor = 0;
                flashCompletion = 0;
            }
        });

        flashAnimator.start();
    }

    /**
     * Set the background and pressed color for the default state.
     * @param background The background color.
     * @param pressed The pressed state background color.
     */
    public void setColors(int background, int pressed) {
        backgroundColor = background;
        pressedColor = pressed;

        setWorkingColors(background, pressed, false);
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public int getPressedColor() {
        return pressedColor;
    }

    /**
     * Sets the ripple color. This color is used for all the view's states.
     * @param color The ripple color.
     */
    public void setRippleColor(int color) {
        rippleColor = color;
        invalidateSelf();
    }

    /**
     * Retrieves the current ripple color.
     * @return The ripple color.
     */
    public int getRippleColor() {
        return rippleColor;
    }

    public void setSelectedColors(int background, int pressed) {
        selectedColor = background;
        selectedPressedColor = pressed;
        invalidateSelf();
    }

    public int getSelectedColor() {
        return selectedColor;
    }

    public int getSelectedPressedColor() {
        return selectedPressedColor;
    }

    public void setNotificationColors(int background, int pressed) {
        notificationColor = background;
        selectedNotificationColor = pressed;
    }

    private void setWorkingColors(int background, int pressed) {
        setWorkingColors(background, pressed, true);
    }

    private void setWorkingColors(int background, int pressed, boolean animated) {
        workingBackgroundColor = background;
        workingPressedColor = pressed;

        if (!hasState(previousState, StatePressed)) {
            transitionToColor(background, animated ? 300 : 0);
        }
        else {
            transitionToColor(pressed, animated ? 300 : 0);
        }
    }



    public void transitionToColor(final int TargetColor) {
        transitionToColor(TargetColor, 100);
    }

    public void transitionToColor(final int TargetColor, final long Duration) {


        if (DEBUG_DISAPPEARING_RIPPLE) Log.e(TAG, "Transitioning to color 0x" + Integer.toHexString(TargetColor) + " over " + Duration + " milliseconds!");

        final int StartingColor = currentColor;
        if (colorAnimator != null) {
            colorAnimator.end();
        }

        colorAnimator = ValueAnimator.ofFloat(0f, 1f);
        colorAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                currentColor = Utils.interpolateColors(animation.getAnimatedFraction(), StartingColor, TargetColor);
                if (DEBUG_DISAPPEARING_RIPPLE) Log.e(TAG, "Current color is now 0x" + Integer.toHexString(currentColor));
                invalidateSelf();
            }
        });
        colorAnimator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationCancel(Animator animation) {
                if (DEBUG_DISAPPEARING_RIPPLE) Log.e(TAG, "Color animator was cancelled!");
                if (DEBUG_DISAPPEARING_RIPPLE) new Throwable().printStackTrace(System.err);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                if (DEBUG_DISAPPEARING_RIPPLE) Log.e(TAG, "Color animator was ended!");
                if (DEBUG_DISAPPEARING_RIPPLE) new Throwable().printStackTrace(System.err);
                stack.removeRipple(animation);
                colorAnimator = null;
            }
        });
        colorAnimator.setDuration(Duration);
        colorAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        colorAnimator.start();
        stack.addRipple(colorAnimator);

    }

    public void setBackgroundColor(int color, boolean animated) {
        if (colorAnimator != null) colorAnimator.cancel();
        if (!animated) {
            currentColor = color;
            invalidateSelf();
        }
    }

    public int getCurrentColor() {
        return currentColor;
    }

    public float lastXCoordinate() {
        return rippleX;
    }

    public float lastYCoordinate() {
        return rippleY;
    }

    public void setRippleSource(float x, float y) {
        rippleX = x;
        rippleY = y;
    }

    @Override
    public void draw(Canvas canvas) {
        int width = getBounds().width();
        int height = getBounds().height();

        if (shape == ShapeRect) {
            if (Color.alpha(currentColor) > 0) {
                RipplePaint.setColor(currentColor);
                canvas.drawRect(getBounds(), RipplePaint);
            }

            if (rippleCompletion > 0) {
                RipplePaint.setColor(Utils.transparentColor((int) (rippleCompletion * Color.alpha(rippleColor)), rippleColor));

                canvas.drawCircle(
                        Utils.interpolateValues(rippleSize, rippleX, getBounds().width() / 2f),
                        Utils.interpolateValues(rippleSize, rippleY, getBounds().height() / 2f),
                        Utils.interpolateValues(rippleSize, 0, (float) Math.sqrt(width * width + height * height) / 2f),
                        RipplePaint
                );
            }

            if (flashCompletion > 0) {
                RipplePaint.setColor(Utils.transparentColor((int) (flashCompletion * Color.alpha(flashColor)), flashColor));
                canvas.drawRect(boundsF, RipplePaint);
            }
        }
        if (shape == ShapeRoundRect) {
            if (Color.alpha(currentColor) > 0) {
                RipplePaint.setColor(currentColor);
                if (boundsF == null) {
                    boundsF = new RectF(getBounds());
                }
                canvas.drawRoundRect(boundsF, roundness, roundness, RipplePaint);
            }

            if (rippleCompletion > 0) {
                RipplePaint.setColor(Utils.transparentColor((int) (rippleCompletion * Color.alpha(rippleColor)), rippleColor));

                canvas.clipRect(getBounds());
                canvas.drawCircle(
                        Utils.interpolateValues(rippleSize, rippleX, getBounds().width() / 2f),
                        Utils.interpolateValues(rippleSize, rippleY, getBounds().height()/ 2f),
                        Utils.interpolateValues(rippleSize, 0, (float) Math.sqrt(width * width + height * height) / 2f),
                        RipplePaint
                );
            }

            if (flashCompletion > 0) {
                RipplePaint.setColor(Utils.transparentColor((int) (flashCompletion * Color.alpha(flashColor)), flashColor));
                canvas.drawRoundRect(boundsF, roundness, roundness, RipplePaint);
            }
        }
        if (shape == ShapeCircle) {
            float radius = this.radius;
            if (radiusType == RadiusTypeWideEdge) {
                // TODO the rest
                radius = getBounds().width() / 2f + pixels.get(8);
            }

            if (Color.alpha(currentColor) > 0) {
                RipplePaint.setColor(currentColor);
                canvas.drawCircle(getBounds().width() / 2f, getBounds().height() / 2f, radius, RipplePaint);
            }

            if (rippleCompletion > 0) {
                RipplePaint.setColor(Utils.transparentColor((int) (rippleCompletion * Color.alpha(rippleColor)), rippleColor));
                canvas.drawCircle(
                        Utils.interpolateValues(rippleSize, rippleX, getBounds().width() / 2f),
                        Utils.interpolateValues(rippleSize, rippleY, getBounds().height() / 2f),
                        Utils.interpolateValues(rippleSize, 0, radius),
                        RipplePaint
                );
            }

            if (flashCompletion > 0) {
                RipplePaint.setColor(Utils.transparentColor((int) (flashCompletion * Color.alpha(flashColor)), flashColor));
                canvas.drawCircle(getBounds().width() / 2f, getBounds().height() / 2f, radius, RipplePaint);
            }
        }
    }

    public void invalidateSelf() {
        super.invalidateSelf();

        if (delegate != null && delegate.get() != null) {
            delegate.get().invalidateSelf();
        }
    }

    /**
     * The delegate drawable is a new drawable instance whose states mirrors that of this ripple drawable.
     * This drawable may have at most one delegate. When this method is called a second time, the first delegate drawable will stop functioning.
     * @return A new drawable instance whose states mirrors that of this ripple drawable.
     */
    public Drawable createDelegateDrawable() {
        RippleDrawableDelegate delegateDrawable = new RippleDrawableDelegate();

        delegate = new WeakReference<RippleDrawableDelegate>(delegateDrawable);

        return delegateDrawable;
    }

    private class RippleDrawableDelegate extends Drawable {

        @Override
        public void draw(Canvas canvas) {
            LegacyRippleDrawable.this.draw(canvas);
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

    @Override
    public void setAlpha(int alpha) {

    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void inflate (Resources r, XmlPullParser parser, AttributeSet attrs) {
//        return this;
    }

}
