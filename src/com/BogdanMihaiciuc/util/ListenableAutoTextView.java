package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.AutoCompleteTextView;

public class ListenableAutoTextView extends AutoCompleteTextView {
    private ListenableEditText.OnKeyPreImeListener listener;

    public void setOnKeyPreImeListener(ListenableEditText.OnKeyPreImeListener listener) {
        this.listener = listener;
    }

    public ListenableEditText.OnKeyPreImeListener getOnKeyPreImeListener() {
        return listener;
    }

    public boolean onKeyPreIme(int keyCode, KeyEvent event) {
        boolean listenerResult = false;
        if (listener != null) {
            listenerResult = listener.onKeyPreIme(keyCode, event);
        }
        if (!listenerResult) {
            return super.onKeyPreIme(keyCode, event);
        }

        return listenerResult;
    }

    private boolean alwaysEnoughToFilter;

    public void setAlwaysEnoughToFilter(boolean alwaysEnoughToFilter) {
        this.alwaysEnoughToFilter = alwaysEnoughToFilter;
    }

    public boolean enoughToFilter() {
        return alwaysEnoughToFilter || super.enoughToFilter();
    }

    public ListenableAutoTextView(Context context) {
        super(context);
    }

    public ListenableAutoTextView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ListenableAutoTextView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
}
