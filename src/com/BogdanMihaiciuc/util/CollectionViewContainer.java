package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

public class CollectionViewContainer extends FrameLayout {

    private boolean inLayout;

    public CollectionViewContainer(Context context) {
        super(context);
    }

    public CollectionViewContainer(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public CollectionViewContainer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @SuppressWarnings("NullableProblems")
    public void addView(View child) {
        if (inLayout) {
            Log.d("", "Trapped view trying to be added during the layout phase.");
            ViewGroup.LayoutParams params = generateLayoutParams(child.getLayoutParams());
            addViewInLayout(child, getChildCount(), params);
            child.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
        else {
            super.addView(child);
        }
    }

    @SuppressWarnings("NullableProblems")
    public void addView(View child, ViewGroup.LayoutParams params) {
        if (inLayout) {
            Log.d("", "Trapped view trying to be added during the layout phase.");
            addViewInLayout(child, getChildCount(), params);
            child.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth(), MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(getMeasuredHeight(), MeasureSpec.AT_MOST));
            child.layout(0, 0, child.getMeasuredWidth(), child.getMeasuredHeight());
        }
        else {
            super.addView(child, params);
        }
    }

    public void requestLayout() {
        if (inLayout)
            Log.e("CollectionViewContainer", "Trapped defective call to requestLayout during the layout!");
        super.requestLayout();
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        inLayout = true;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        inLayout = false;
    }

    private int interactionDisableRequests = 0;

    protected void requestDisableInteractions() {
        interactionDisableRequests++;
    }

    protected void requestEnableInteractions() {
        interactionDisableRequests--;
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (interactionDisableRequests == 0) return super.dispatchTouchEvent(event);
        return false;
    }

}
