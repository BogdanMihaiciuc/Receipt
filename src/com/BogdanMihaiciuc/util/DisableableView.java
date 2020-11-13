package com.BogdanMihaiciuc.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

/**
 * Created by Bogdan on 2/15/15.
 */
public class DisableableView extends View {

    public boolean drawingSuspended;

    public void invalidate() {
        if (!drawingSuspended) {
            super.invalidate();
        }
    }

    public void suspendDrawing() {
        drawingSuspended = true;
        setWillNotDraw(true);
    }

    public void resumeDrawing() {
        drawingSuspended = false;
        setWillNotDraw(false);
    }


    public DisableableView(Context context) {
        super(context);
    }

    public DisableableView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public DisableableView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public DisableableView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }
}
