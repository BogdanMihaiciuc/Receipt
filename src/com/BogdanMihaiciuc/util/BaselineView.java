package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;


// The BaselineView provides a precise baseline on which to align other views

public class BaselineView extends View {
    public BaselineView(Context context) {
        super(context);
    }

    public BaselineView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public BaselineView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    public int getBaseline() {
        return 0;
    }

}
