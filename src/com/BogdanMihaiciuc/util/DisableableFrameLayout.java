package com.BogdanMihaiciuc.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.FrameLayout;

public class DisableableFrameLayout extends FrameLayout {

    private boolean pressedStateForwardingEnabled = true;
    private int disableInteractionRequests = 0;

    public void setForwardPressedStateEnabled(boolean enabled) {
        this.pressedStateForwardingEnabled = enabled;
    }

    public void setPressed(boolean pressed) {
        if (pressedStateForwardingEnabled) {
            super.setPressed(pressed);
        }
    }

    public DisableableFrameLayout(Context context) {
        super(context);
    }

    public DisableableFrameLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DisableableFrameLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public DisableableFrameLayout(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void requestDisableInteractions() {
        disableInteractionRequests++;
    }

    public void requestEnableInteractions() {
        disableInteractionRequests--;
    }

    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (disableInteractionRequests != 0) {
            return true;
        }
        return super.onInterceptTouchEvent(event);
    }

    public boolean onTouchEvent(MotionEvent event) {
        if (disableInteractionRequests != 0) {
            return false;
        }
        return super.onTouchEvent(event);
    }

}
