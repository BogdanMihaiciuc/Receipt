package com.BogdanMihaiciuc.receipt;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.HorizontalScrollView;

public class ListenableHorizontalScrollView extends HorizontalScrollView {

	public static interface OnScrollListener {
		public void onScrollChanged (ListenableHorizontalScrollView view, int left, int top, int oldleft, int oldtop);
	}
	
	private OnScrollListener scrollListener;

	public ListenableHorizontalScrollView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
	}

	public ListenableHorizontalScrollView(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public ListenableHorizontalScrollView(Context context) {
		super(context);
	}
	
	public void setOnScrollListener(OnScrollListener scrollListener) {
		this.scrollListener = scrollListener;
	}
	
	protected void onScrollChanged (int l, int t, int oldl, int oldt) {
		super.onScrollChanged(l, t, oldl, oldt);
		if (scrollListener != null)
			scrollListener.onScrollChanged(this, l, t, oldl, oldt);
	}

}
