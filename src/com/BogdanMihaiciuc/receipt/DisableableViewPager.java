package com.BogdanMihaiciuc.receipt;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.MotionEvent;

public class DisableableViewPager extends ViewPager {

    private boolean enabled;
    private boolean frozen;

    public DisableableViewPager(Context context) {
        super(context);
        this.enabled = true;
    }

    public DisableableViewPager(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.enabled = true;
    }

    public void freeze() {
        frozen = true;
        setWillNotDraw(true);
    }

    public void thaw() {
        frozen = false;
        setWillNotDraw(false);
    }

    public void invalidate() {
        if (!frozen) super.invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (this.enabled) {
            try {
                return super.onTouchEvent(event);
            }
            catch (IllegalArgumentException e) {
                return false;
            }
        }

        return false;
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (frozen) {
            // Don't give this ViewPager a change to forward this motionevent to one of its descendands
            return true;
        }

        if (this.enabled) {
            try {
                return super.onInterceptTouchEvent(event);
            }
            catch (IllegalArgumentException e) {
                return false;
            }
        }

        return false;
    }

    public void setPagingEnabled(boolean enabled) {
        this.enabled = enabled;
    }

}
