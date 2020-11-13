package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.widget.EditText;

public class ListenableEditText extends EditText {
    public interface OnKeyPreImeListener {
        public boolean onKeyPreIme(int keyCode, KeyEvent event);
    }

    private OnKeyPreImeListener listener;

    public void setOnKeyPreImeListener(OnKeyPreImeListener listener) {
        this.listener = listener;
    }

    public OnKeyPreImeListener getOnKeyPreImeListener() {
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

    public ListenableEditText(Context context) {
        super(context);
    }

    public ListenableEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ListenableEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
}
