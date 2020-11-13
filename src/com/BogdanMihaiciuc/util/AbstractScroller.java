package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.util.Log;
import android.view.animation.DecelerateInterpolator;

public class AbstractScroller {

    final static String TAG = AbstractScroller.class.getName();
    final static boolean DEBUG = false;

    public interface ScrollEventListener {
        public void onSrollStarted(AbstractScroller scroller);
        public void onScrollPositionChanged(AbstractScroller scroller, int newPosition);
        public void onScrollFinished(AbstractScroller scroller);
        public void onScrollCancelled(AbstractScroller scroller);
        public int getWrapLimit();
    }

    public abstract class ScrollEventListenerAdapter implements ScrollEventListener {
        public void onSrollStarted(AbstractScroller scroller) {}
        public void onScrollPositionChanged(AbstractScroller scroller, int newPosition) {}
        public void onScrollFinished(AbstractScroller scroller) {}
        public void onScrollCancelled(AbstractScroller scroller) {}
        public abstract int getWrapLimit();
    }

    // Acceleration (friction) is measured in px / ms ^ 2
    private float friction;
    private float activeFriction;

    // Velocity is measured in px/ms
    private float currentSpeed;
    private float initialSpeed;

    // Distance is measured in px
    private int currentDistance = 0;

    // Time is measured in ms
    private float time;
    private int distance;

    private boolean scrollingPrepared;

    private ValueAnimator scroller;

    private ScrollEventListener scrollEventListener;

    public void setScrollEventListener(ScrollEventListener listener) {
        this.scrollEventListener = listener;
    }

    public ScrollEventListener getScrollEventListener() {
        return scrollEventListener;
    }

    public void setFriction(float friction) {
        this.friction = - friction * friction;
    }

    public float getFriction() {
        return (float) Math.sqrt(- friction);
    }

    public void setCurrentDistance(int currentDistance) {
        this.currentDistance = currentDistance;

        if (scrollEventListener != null) {
            scrollEventListener.onScrollPositionChanged(this, currentDistance);
        }
    }

    public int getCurrentDistance() {
        return currentDistance;
    }

    public void prepareScrollingFromInitialSpeed(float initialSpeed) {
        if (scroller != null) {
            scroller.cancel();
        }

        this.activeFriction = friction;
        this.initialSpeed = initialSpeed;
        this.time = (long) (- initialSpeed / friction );
        if (time < 0) time = - time;
        if (time < 60) {
            time = 60;
        }
        this.distance = (int) (initialSpeed * time + friction * time * time / 2f) / 2;

        scrollingPrepared = true;

        if (DEBUG) {
            Log.d(TAG, "Scrolling for initial speed " + initialSpeed + "px/ms will cover a distance of " + distance + "px over " + time + "ms");
        }
    }

    public void prepareScrollingFromInitialSpeedWithTargetDistance(float initialSpeed, int targetDistance) {
        if (scroller != null) {
            scroller.cancel();
        }

        this.initialSpeed = initialSpeed;
        this.time = (long) (- initialSpeed / friction);
        if (time < 0) time = - time;
        if (time < 60) {
            time = 60;
        }
        this.distance = targetDistance - currentDistance;

        this.activeFriction = - (2 * initialSpeed * time) / (time * time);

        scrollingPrepared = true;

        if (DEBUG) {
            Log.d(TAG, "Scrolling for initial speed " + initialSpeed + "px/ms with a distance of " + distance + "px will happen over " + time + "ms");
        }
    }

    public int getComputedFinalScrollPosition() {
        if (!scrollingPrepared) {
            throw new IllegalStateException("prepareScrolling must be called first to compute the final scroll position.");
        }

        return currentDistance + distance;
    }

    public void beginScrolling() {
        if (!scrollingPrepared) {
            throw new IllegalStateException("prepareScrolling must be called at least once before each beginScrolling call.");
        }

        scrollingPrepared = false;

        final int StartingDistance = currentDistance;
        final int TargetDistance = currentDistance + distance;
        scroller = ValueAnimator.ofFloat(0f, 1f);
        scroller.setDuration((long) time);
        scroller.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float time = AbstractScroller.this.time * animation.getAnimatedFraction();
                if (DEBUG) {
                    Log.d(TAG, "Speed for fraction " + animation.getAnimatedFraction() + " is " + velocity(time));
                    Log.d(TAG, "Displacement for fraction " + animation.getAnimatedFraction() + " is " + displacement(time));
                }

                currentDistance = (int) Utils.interpolateValues(animation.getAnimatedFraction(), StartingDistance, TargetDistance);
                if (time == AbstractScroller.this.time) {
                    currentDistance = TargetDistance;
                }
                if (scrollEventListener != null) {
                    scrollEventListener.onScrollPositionChanged(AbstractScroller.this, currentDistance);
                }
            }
        });
        scroller.addListener(new AnimatorListenerAdapter() {
            boolean cancelled = false;
            public void onAnimationCancel(Animator animator) {
                this.cancelled = true;
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                wrapDistance();
                scroller = null;
                if (scrollEventListener != null) {
                    if (cancelled) {
                        scrollEventListener.onScrollCancelled(AbstractScroller.this);
                    }
                    else {
                        scrollEventListener.onScrollFinished(AbstractScroller.this);
                    }
                }
            }
        });
        scroller.setInterpolator(new DecelerateInterpolator(1.5f));
        scroller.start();
        if (scrollEventListener != null) {
            scrollEventListener.onSrollStarted(this);
        }
    }

    private void wrapDistance() {
        if (scrollEventListener == null) return;

        final int PreviousDistance = currentDistance;

        int wrappingLimit = scrollEventListener.getWrapLimit();

        if (currentDistance > wrappingLimit) {
            currentDistance = currentDistance % wrappingLimit;
        }
        if (currentDistance < - wrappingLimit) {
            currentDistance = currentDistance % (- wrappingLimit);
        }

    }

    public void cancelScrolling() {
        if (scroller != null) {
            scroller.cancel();
        }
    }

    public void endScrolling() {
        if (scroller != null) {
            scroller.end();
        }
    }

    private float velocity(float time) {
        time = time / 2;
        return initialSpeed + activeFriction * time;
    }

    private float displacement(float time) {
        time = time / 2;
        return initialSpeed * time + activeFriction * time * time;
    }

}
