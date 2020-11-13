package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.Rect;
import android.os.Handler;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;

public class EventTouchListener implements View.OnTouchListener {

    final static String TAG = EventTouchListener.class.getName();

    final static boolean DEBUG = false;
    final static boolean DEBUG_SWIPE = false;

    public interface EventDelegate {
        public boolean viewShouldPerformClick(EventTouchListener listener, View view);
        public boolean viewShouldPerformLongClick(EventTouchListener listener, View view);

        public boolean viewShouldStartMoving(EventTouchListener listener, View view);

        public void viewDidMove(EventTouchListener listener, View view, float distance);

        public void viewDidBeginSwiping(EventTouchListener listener, View view, float velocity);
        public void viewDidCancelSwiping(EventTouchListener listener, View view);

        public int getSwipeDistanceThreshold();
    }

    public abstract static class EventDelegateAdapter implements EventDelegate {
        public boolean viewShouldPerformClick(EventTouchListener listener, View view) { return true; }
        public boolean viewShouldPerformLongClick(EventTouchListener listener, View view) { return true; }

        public boolean viewShouldStartMoving(EventTouchListener listener, View view) { return true; }

        public void viewDidMove(EventTouchListener listener, View view, float distance) {}

        public void viewDidBeginSwiping(EventTouchListener listener, View view, float velocity) {}
        public void viewDidCancelSwiping(EventTouchListener listener, View view) {}

        public int getSwipeDistanceThreshold() {
            return Integer.MAX_VALUE;
        }
    }

    private Context context;
    private int minimumScrollDistance;
    private int minimumSwipeVelocity;
    private int pressEffectDelay;
    private int longPressDelay;
    private int pressedStateDuration;

    private int scaledTouchSlop;

    private boolean handlesClick = true;
    private boolean handlesLongClick = true;

    private float previousX, previousY, startingX;
    private boolean scrollingTriggered;
    private boolean longPressTriggered;
    private boolean prepressTriggered;
    private boolean pressThresholdReached;

    /**
     * The motion event becomes cancelled if the view cannot move, but the pointer goes out of the view's area
     */
    private boolean cancelled = false;

    private boolean allowSemiLongClick = true;

    private VelocityTracker tracker;

    private View view;

    private Handler handler = new Handler();

    private EventDelegate delegate;

    private EventTouchListener(Context context) {
        this.context = context;

        ViewConfiguration config = ViewConfiguration.get(context);

        minimumScrollDistance = config.getScaledTouchSlop();
        minimumSwipeVelocity = config.getScaledMinimumFlingVelocity();
        pressEffectDelay = ViewConfiguration.getTapTimeout();
        longPressDelay = ViewConfiguration.getLongPressTimeout();
        pressedStateDuration = ViewConfiguration.getPressedStateDuration();

        scaledTouchSlop = config.getScaledTouchSlop();
    }

    public static EventTouchListener listenerInContext(Context context) {
        return new EventTouchListener(context);
    }

    public void setEnforceTapThreshold(boolean enforce) {
        allowSemiLongClick = !enforce;
    }

    public boolean doesEnforceTapThreshold() {
        return !allowSemiLongClick;
    }

    public void setHandlesClick(boolean handlesClick) {
        this.handlesClick = handlesClick;
    }

    public void setHandlesLongClick(boolean handlesLongClick) {
        this.handlesLongClick = handlesLongClick;
    }

    public void setDelegate(EventDelegate delegate) {
        this.delegate = delegate;
    }

    public static int sgn(float number) {
        if (number < 0) {
            return -1;
        }

        return 1;
    }

    private Rect bounds = new Rect();
    private int[] location = new int[2];
    public void updateBounds(View view) {
        view.getLocationOnScreen(location);

        bounds.left = location[0] - scaledTouchSlop;
        bounds.top = location[1] - scaledTouchSlop;
        bounds.right = bounds.left + view.getWidth() + 2 * scaledTouchSlop;
        bounds.bottom = bounds.top + view.getHeight() + 2 * scaledTouchSlop;

    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {

        if (delegate == null) {
            Log.w(TAG, "EventTouchListener has no delegate set!");
            return false;
        }

        final PointF EventOriginalLocation = new PointF(event.getX(), event.getY());

        event.setLocation(event.getRawX(), event.getRawY());

        if (this.view != view) {
            this.view = view;
        }

        if (tracker == null) {
            tracker = VelocityTracker.obtain();
        }

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            previousX = event.getX();
            previousY = event.getY();

            startingX = event.getX();

            scrollingTriggered = false;
            longPressTriggered = false;
            prepressTriggered = false;
            pressThresholdReached = false;

            cancelled = false;

            if (handlesLongClick) {
                handler.postDelayed(LongClickRunnable, longPressDelay);
            }

            if (doesEnforceTapThreshold()) {
                handler.postDelayed(DisableClickRunnable, pressEffectDelay * 2);
            }

            handler.postDelayed(PressedStateRunnable, pressEffectDelay);

            if (DEBUG) Log.d(TAG, "MotionEvent action down has been received!");

        }

        if (cancelled) {
            event.setLocation(EventOriginalLocation.x, EventOriginalLocation.y);
            return true;
        }

        if (event.getAction() == MotionEvent.ACTION_MOVE) {
            float x = event.getX();
            float y = event.getY();
            if (!scrollingTriggered && !longPressTriggered) {

                if (Math.abs(x - previousX) > minimumScrollDistance && Math.abs(x - previousX) > Math.abs(y - previousY)) {
                    if (delegate.viewShouldStartMoving(this, view)) {
                        scrollingTriggered = true;

                        handler.removeCallbacks(LongClickRunnable);
                        handler.removeCallbacks(PressedStateRunnable);

                        view.getParent().requestDisallowInterceptTouchEvent(true);

                        if (view.isPressed()) {
                            view.setPressed(false);
                        }
                    }
                }

                if (!scrollingTriggered) {
                    updateBounds(view);

                    // The pointer has moved outside the view's bounds; stop the current event and do not process any more until the next down event
                    if (!bounds.contains((int) event.getRawX(), (int) event.getRawY())) {

                        if (DEBUG) Log.d(TAG, "This event is now outside the view's bounds; cancelling...\n" +
                                "Point is (" + event.getRawX() + ", " + event.getRawY() + ")\n" +
                                "Bounds are (" + bounds.left + ", " + bounds.top + ") -> (" + bounds.right + ", " + bounds.bottom + ")"
                        );

                        handler.removeCallbacks(LongClickRunnable);
                        handler.removeCallbacks(PressedStateRunnable);

                        if (view.isPressed()) {
                            view.setPressed(false);
                        }

                        cancelled = true;

                        tracker.recycle();
                        tracker = null;
                    }
                }
            }

            // Scrolling may be triggered by the preivous if branch in which case it is handled here
            if (scrollingTriggered) {
                view.getParent().requestDisallowInterceptTouchEvent(true);

                delegate.viewDidMove(this, view, x - previousX);

                previousX = x;
                previousY = y;
            }

            if (!cancelled) tracker.addMovement(event);
        }

        if (event.getAction() == MotionEvent.ACTION_UP) {

            if (DEBUG_SWIPE) Log.d(TAG, "Up event has begun!");

            tracker.addMovement(event);

            handler.removeCallbacks(LongClickRunnable);
            handler.removeCallbacks(PressedStateRunnable);
            handler.removeCallbacks(DisableClickRunnable);
            float x = event.getX();

            if (prepressTriggered) {
                handler.post(ClearPressedStateRunnable);
            }
            else if (!scrollingTriggered) {
                view.setPressed(true);
//                handler.post(PressedStateRunnable);
                handler.postDelayed(ClearPressedStateRunnable, pressedStateDuration);
            }

            if (!scrollingTriggered && !this.longPressTriggered && handlesClick && !pressThresholdReached) {
                if (DEBUG) Log.d(TAG, "Sending click event; scrollingTriggered: " + scrollingTriggered + " longPressTriggered: " + this.longPressTriggered + " handlesClick: " + handlesClick);
                ClickRunnable.run();
            }

            if (!longPressTriggered && scrollingTriggered) {
                tracker.computeCurrentVelocity(1); // px/ms
                float xVelocity = tracker.getXVelocity();
                float yVelocity = tracker.getYVelocity();

                if (sgn(x - startingX) != sgn(xVelocity)) {
                    if (DEBUG_SWIPE) Log.e(TAG, "Displacement(" + (x - startingX) + ": " + sgn(x - startingX) + ") and velocity(" + xVelocity + ":" + sgn(xVelocity) + ") have different directions!");
                }

                if (Math.abs(xVelocity * 1000) > minimumSwipeVelocity && sgn(x - startingX) == sgn(xVelocity)) {
                    if (DEBUG_SWIPE) Log.d(TAG, "Calling delegate.viewDidBeginSwiping");
                    delegate.viewDidBeginSwiping(this, view, xVelocity);
                }
                else if (Math.abs(x - startingX) > delegate.getSwipeDistanceThreshold()) {
                    if (xVelocity != 0) {
                        if (sgn(x - startingX) == sgn(xVelocity)) {
                            if (DEBUG_SWIPE) Log.d(TAG, "Calling delegate.viewDidBeginSwiping");
                            delegate.viewDidBeginSwiping(this, view, xVelocity);
                        }
                        else {
                            if (DEBUG_SWIPE) Log.e(TAG, "Calling delegate.viewDidCancelSwiping. Reason: displacement and velocity have different directions!");
                            delegate.viewDidCancelSwiping(this, view);
                        }
                    }
                    else {
                        if (DEBUG_SWIPE) Log.d(TAG, "Calling delegate.viewDidBeginSwiping");
                        delegate.viewDidBeginSwiping(this, view, xVelocity);
                    }
                }
                else {
                    if (DEBUG_SWIPE) Log.e(TAG, "Calling delegate.viewDidCancelSwiping. Reason: displacement and velocity are too low!");
                    delegate.viewDidCancelSwiping(this, view);
                }
            }

            tracker.recycle();
            tracker = null;
        }

        if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(LongClickRunnable);
            handler.removeCallbacks(PressedStateRunnable);
            if (scrollingTriggered) {
                delegate.viewDidCancelSwiping(this, view);
            }

            if (view.isPressed()) {
                view.setPressed(false);
            }

            tracker.recycle();
            tracker = null;

            if (DEBUG) {
                Log.e(TAG, "MotionEvent has been cancelled!");
                new Throwable().printStackTrace();
            }
        }

        // Reset the original coordinates for whatever else will handle this event afterwards
        event.setLocation(EventOriginalLocation.x, EventOriginalLocation.y);
        // The EventTouchListener always handles all touch events the view receives
        return true;
    }

    final private Runnable PressedStateRunnable = new Runnable() {
        @Override
        public void run() {
            prepressTriggered = true;
            if (view.isClickable() || view.isLongClickable()) {
                view.setPressed(true);
            }
        }
    };

    final private Runnable ClearPressedStateRunnable = new Runnable() {
        @Override
        public void run() {
            view.setPressed(false);
        }
    };

    final private Runnable DisableClickRunnable = new Runnable() {
        @Override
        public void run() {
            pressThresholdReached = true;
        }
    };

    final private Runnable ClickRunnable = new Runnable() {
        @Override
        public void run() {
            if (delegate.viewShouldPerformClick(EventTouchListener.this, view)) {
                view.performClick();
            }
        }
    };

    final private Runnable LongClickRunnable = new Runnable() {
        @Override
        public void run() {
            if (delegate.viewShouldPerformLongClick(EventTouchListener.this, view)) {
                longPressTriggered |= view.performLongClick();

                if (DEBUG) Log.d(TAG, "Sending long click event; longPressTriggered is now: " + longPressTriggered);
            }
        }
    };
}
