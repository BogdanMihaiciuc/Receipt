package com.BogdanMihaiciuc.receipt;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

public class DisableableListView extends ListView {

	private boolean enabled;
	private OnTouchListener interceptTouchListener;
	private int preloadSize;
	
	public DisableableListView(Context context) {
		super(context);
		this.enabled = true;
	}

	public DisableableListView(Context context, AttributeSet attrs) {
	    super(context, attrs);
	    this.enabled = true;
	}
	
	public DisableableListView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		this.enabled = true;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
	    if (this.enabled) {
	    	if (interceptTouchListener == null)
	    		return super.onTouchEvent(event);
	    	else {
	    		if (interceptTouchListener.onTouch(this, event)) 
	    			return true;
	    		else
	    			return super.onTouchEvent(event);
	    	}
	    }
	    return false;
	}
	
	public void setInterceptTouchListener(OnTouchListener listener) {
		interceptTouchListener = listener;
	}

	@Override
	public boolean onInterceptTouchEvent(MotionEvent event) {
	    if (this.enabled) {
	    	return super.onInterceptTouchEvent(event);
	    }
	    return false;
	}

	public void setScrollingEnabled(boolean enabled) {
	    this.enabled = enabled;
	}
	
	public void setPreloadSize(int preloadSize) {
		this.preloadSize = preloadSize;
	}
	
	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
	    super.onMeasure(widthMeasureSpec, heightMeasureSpec);
	    setMeasuredDimension(getMeasuredWidth(), getMeasuredHeight() + preloadSize);
	}

}
