package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.util.DisplayMetrics;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

public class SwipeToDeleteListener implements View.OnTouchListener {

    final static String TAG = "com.BogdanMihaiciuc.util.SwipeToDeleteListener";

    public interface OnMoveListener {
        public void onMove(View view, float distance, boolean initial);
    }

    public interface OnReleaseListener {
        public void onRelease(View view);
    }

    public interface OnDeleteListener {
        public void onDelete(View view, float velocity, float velocityRatio);
    }

    public interface EnabledListener {
        public boolean isEnabled();
    }


    final static int initialSwipeSteps = 2;
    private float minimumSwipeDistance;
    private float minimumSwipeError;
    private float minimumSwipeSpeed;

    class FlingGestureDetector extends GestureDetector.SimpleOnGestureListener {

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean positive = velocityX > 0f;
            if(Math.abs(velocityX) > minimumSwipeSpeed) {
                if (positive && e2.getRawX() - e1.getRawX() >= 0f) {
                    speed = velocityX;
                    return true;
                }
                if (!positive && e2.getRawX() - e1.getRawX() < 0f) {
                    speed = velocityX;
                    return true;
                }
            }
            return false;
        }

    }

    public boolean activated;
    public boolean started;
    public boolean ran;
    private int elapsedSteps;

    private float previousX, previousY;
    private float startX;
    private float x, y;

    private EnabledListener enabledListener = new EnabledListener() {
        @Override
        public boolean isEnabled() {
            return true;
        }
    };
    private OnDeleteListener deleteListener;
    private OnReleaseListener releaseListener;
    private OnMoveListener moveListener;
    private float speed;
    private Context applicationContext;

    private GestureDetector detector;
    private DisplayMetrics metrics;

    private boolean clickable;
    private boolean longClickable;

    public SwipeToDeleteListener(Context applicationContext) {
        detector = new android.view.GestureDetector(applicationContext, new FlingGestureDetector());
        metrics = applicationContext.getResources().getDisplayMetrics();
        this.applicationContext = applicationContext.getApplicationContext();

        minimumSwipeDistance = 2 * metrics.widthPixels/3;
        minimumSwipeSpeed = ViewConfiguration.get(applicationContext).getScaledMinimumFlingVelocity();
        minimumSwipeError = 10 * metrics.density;
    }

    public void setOnDeleteListener(OnDeleteListener listener) {
        deleteListener = listener;
    }

    public void setOnMoveListener(OnMoveListener listener) {
        moveListener = listener;
    }

    public void setOnReleaseListener(OnReleaseListener listener) {
        releaseListener = listener;
    }

    public void setEnabledListener(EnabledListener listener) {
        enabledListener = listener;
    }

    public void setMinimumSwipeDistance(float swipeDistance) {
        minimumSwipeDistance = swipeDistance;
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {

        boolean shouldFling = detector.onTouchEvent(event);

        x = event.getRawX();
        y = event.getRawY();

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            activated = true;
            started = false;
            ran = false;
            previousX = x;
            previousY = y;
            startX = previousX;
            elapsedSteps = 1;

//            clickable = view.isClickable();
//            longClickable = view.isLongClickable();

            if (!enabledListener.isEnabled()) {
                activated = false;
            }
            return false;
        }

        if (elapsedSteps < initialSwipeSteps && event.getAction() != MotionEvent.ACTION_UP) {
            if (Math.abs(y - previousY) > Math.abs(x - previousX) || !enabledListener.isEnabled()) {
                activated = false;
            }
            else {
                previousX = x;
                previousY = y;
            }
            elapsedSteps++;
            return false;
        }

        if (activated && event.getAction() != MotionEvent.ACTION_UP) {
            if (Math.abs(x - startX) >= minimumSwipeError) {
                started = true;
            }
        }

        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {

            view.setPressed(false);
            ((View) view.getParent()).setPressed(false);
            view.getParent().requestDisallowInterceptTouchEvent(false);

            if (activated && ran) {
                if ((shouldFling || Math.abs(x - startX) > minimumSwipeDistance)
                        && event.getAction() == MotionEvent.ACTION_UP) {

                    float speedRatio;
                    if (shouldFling) {
                        speedRatio = 2000/speed;
                    }
                    else {
                        speedRatio = 0.5f;
                    }

                    speedRatio = Math.copySign(speedRatio, Math.signum(x - startX));

                    if (deleteListener != null) {
                        deleteListener.onDelete(view, speed, speedRatio);
                    }

                }
                else {

//                    view.setClickable(clickable);
//                    view.setLongClickable(longClickable);

                    if (releaseListener != null) {
                        releaseListener.onRelease(view);
                    }
                }

                return true;
            }

            return false;
        }

        if (!activated || !started)
            return false;

        if (started && activated && event.getAction() == MotionEvent.ACTION_MOVE) {

            if (!ran) {
                if (moveListener != null) {
                    moveListener.onMove(view, x - previousX, true);
                }
            }
            else {
                if (moveListener != null) {
                    moveListener.onMove(view, x - previousX, false);
                }
            }

//            view.setClickable(false);
//            view.setLongClickable(false);
//            view.setPressed(false);
            view.getParent().requestDisallowInterceptTouchEvent(true);
//            ((View) view.getParent()).setPressed(false);

            ran = true;

            previousX = x;
            previousY = y;

            return false;
        }

        return false;
    }
}
