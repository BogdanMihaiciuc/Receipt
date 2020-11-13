package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.receipt.R;

public class LegacySnackbar extends Fragment {

    final static int UndoActionID = LegacyActionBarView.generateViewId();

    public interface SnackbarListener {
        public void onActionConfirmed(LegacySnackbar snackbar);
        public void onActionUndone(LegacySnackbar snackbar);
    }

    final static int BackgroundColor = 0x88000000;

    private CharSequence message;
    private View confirmator;
    private boolean active;
    private boolean animated;

    private SnackbarListener listener;

    public void setTitle(CharSequence title) {
        message = title;
    }

    public LegacySnackbar() {
        setRetainInstance(true);
    }

    public static LegacySnackbar showSnackbarWithMessage(CharSequence message, SnackbarListener listener, Activity activity) {
        LegacySnackbar snackbar = new LegacySnackbar();

        snackbar.setTitle(message);
        snackbar.setListener(listener);

        snackbar.show(true, activity);
        return snackbar;
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (active) {
            build();
            animated = false;
        }
    }

    public void onDetach() {
        confirmator = null;

        super.onDetach();
    }

    public void setListener(SnackbarListener listener) {
        this.listener = listener;
    }

    public SnackbarListener getListener() {
        return listener;
    }

    private final Runnable BackstackEntry = new Runnable() {
        @Override
        public void run() {
            if (active) {
                confirmAction();
                ((Utils.BackStack) getActivity()).popBackStack();
            }
        }
    };

    public void show(boolean animated, Activity activity) {
        if (active) return;

        this.animated = animated;
        active = true;

        activity.getFragmentManager().beginTransaction().add(this, hashCode() + "").commit();
        ((Utils.BackStack) activity).pushToBackStack(BackstackEntry);
    }

    private void build() {
        Activity activity = getActivity();
        ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
        final DisplayMetrics Metrics = activity.getResources().getDisplayMetrics();

        confirmator = activity.getLayoutInflater().inflate(Utils.ConfirmatorLayout, root, false);
        final View UndoButton = confirmator.findViewById(Utils.UndoID);

        ((TextView) confirmator.findViewById(R.id.DeleteTitle)).setText(message);

        LegacyRippleDrawable background = new LegacyRippleDrawable(activity, LegacyRippleDrawable.ShapeRoundRect);
        background.setColors(0x00FFFFFF, 0x22FFFFFF);
        background.setRippleColor(0x40FFFFFF);
        UndoButton.setBackground(background);

        root.addView(confirmator);
        UndoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmator.setOnTouchListener(null);
                UndoButton.setOnClickListener(null);
                UndoButton.setClickable(false);
                undoPressed();
            }
        });

        confirmator.animate().setDuration(100);

        if (animated) {
            confirmator.setAlpha(0);
            confirmator.animate()
                    .alpha(1).withLayer();
        }

        confirmator.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (event.getY() < ((ViewGroup) confirmator).getChildAt(0).getTop() - Metrics.density * 16) {
                        confirmator.setOnTouchListener(null);
                        UndoButton.setOnClickListener(null);
                        UndoButton.setClickable(false);
                        confirmAction();
                        return false;
                    }
                }
                return true;
            }
        });
    }

    public void undoPressed() {
        ((Utils.BackStack) getActivity()).swipeFromBackStack(BackstackEntry);

        final Activity Activity = getActivity();
        final ViewGroup Root = (ViewGroup) Activity.getWindow().getDecorView();

        if (active) {
            final View Confirmator = confirmator;
            confirmator.animate().alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator a) {
                    if (Activity == null) return;
                    Root.removeView(Confirmator);
                }
            });
            confirmator = null;
            active = false;
        }

        if (listener != null) listener.onActionUndone(this);

    }

    public void confirmAction() {
        confirmAction(true);
    }

    public void confirmAction(boolean animated) {
        ((Utils.BackStack) getActivity()).swipeFromBackStack(BackstackEntry);

        final Activity Activity = getActivity();
        final ViewGroup Root = (ViewGroup) Activity.getWindow().getDecorView();

        if (active) {
            final View Confirmator = confirmator;
            if (animated)
                confirmator.animate().alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        if (Activity == null) return;
                        Root.removeView(Confirmator);
                    }
                });
            else {
                Root.removeView(confirmator);
            }
            confirmator = null;
            active = false;
        }

        if (listener != null) listener.onActionConfirmed(this);
    }


}
