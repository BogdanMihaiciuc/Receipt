package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.Fragment;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.style.RelativeSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.TextView;

public class HeadsUpFragment extends Fragment {

    final static String TAG = "HeadsUpFragment";

    final static String MessageKey = "HeadsUp.message";
    final static String TargetKey = "HeadsUp.target";

    private int targetID;
    private CharSequence message;
    private boolean created;

    private View headsUp;
    private View target;
    private ViewGroup root;

    public HeadsUpFragment() {
        Log.w(TAG, "Default constructor has been called!");
    }

    public HeadsUpFragment(View target, CharSequence message) {
        this.target = target;
        this.message = message;
        this.targetID = target.getId();
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);


    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        final Activity activity = getActivity();
        final DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        headsUp = activity.getLayoutInflater().inflate(R.layout.heads_up_display, (ViewGroup) activity.getWindow().getDecorView(), false);
        root = (ViewGroup) activity.getWindow().getDecorView();
        root.addView(headsUp);
        final TextView text = (TextView) headsUp.findViewById(R.id.HeadsUpContent);
        final ViewGroup textRoot = (ViewGroup) text.getParent();

        text.setText(message);
        text.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        float length = text.getMeasuredWidth();
        float maxLength = metrics.widthPixels - 2 * textRoot.getPaddingLeft() - 2 * activity.getResources().getDimensionPixelSize(R.dimen.EdgePadding);
        float scaleFactor;
        if (length > maxLength) {
            scaleFactor = maxLength/length;
            Log.d("", "scaleFactor is: " + scaleFactor + "; maxWidth is: " + maxLength);
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.setSpan(new RelativeSizeSpan(scaleFactor), 0, 0, SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            builder.append(message);
            text.setText(builder);
        }
        if (!created) {
            textRoot.setAlpha(0f);
            textRoot.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                    textRoot.removeOnLayoutChangeListener(this);

                    final float widthRatio = ((float) target.getWidth())/((float) textRoot.getWidth());
                    final float heightRatio = ((float) target.getHeight())/((float) textRoot.getHeight());

                    final Rect rct = new Rect();
                    target.getGlobalVisibleRect(rct);

                    textRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    textRoot.setScaleX(widthRatio);
                    textRoot.setScaleY(heightRatio);
                    textRoot.setX(rct.left - (textRoot.getWidth() - textRoot.getWidth()*widthRatio)/2f);
                    textRoot.setY(rct.top - (textRoot.getHeight() - textRoot.getHeight()*heightRatio)/2f);

                    textRoot.animate()
                            .scaleX(1).scaleY(1).translationX(0).translationY(0).alpha(1)
                            .setDuration(400)
                            .setInterpolator(new OvershootInterpolator());
                }
            });
            created = true;
        }

        headsUp.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    dismiss();
                    return (motionEvent.getX() >= headsUp.getX() && motionEvent.getX() >= headsUp.getX() + headsUp.getWidth() &&
                            motionEvent.getY() >= headsUp.getY() && motionEvent.getY() >= headsUp.getY() + headsUp.getHeight());
                }
                return false;
            }
        });
    }

    public void onDetach() {
        super.onDetach();
        headsUp = null;
        target = null;
        root = null;
    }

    public void dismiss() {
        final TextView text = (TextView) headsUp.findViewById(R.id.HeadsUpContent);
        final ViewGroup textRoot = (ViewGroup) text.getParent();
        final View HeadsUp = headsUp;
        final ViewGroup Root = root;

        HeadsUp.setOnTouchListener(null);

        if (target == null) {
            target = getActivity().findViewById(targetID);
        }

        if (target != null) {
            final float widthRatio = ((float) target.getWidth())/((float) textRoot.getWidth());
            final float heightRatio = ((float) target.getHeight())/((float) textRoot.getHeight());

            final Rect rct = new Rect();
            target.getGlobalVisibleRect(rct);

            textRoot.animate()
                    .scaleX(widthRatio).scaleY(heightRatio)
                    .x(rct.left - (textRoot.getWidth() - textRoot.getWidth() * widthRatio) / 2f)
                    .y(rct.top - (textRoot.getHeight() - textRoot.getHeight() * heightRatio) / 2f).
                    alpha(0)
                    .setInterpolator(new AccelerateInterpolator())
                    .setDuration(300)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            Root.removeView(HeadsUp);
                        }
                    });
        }
        else {
            textRoot.animate()
                    .alpha(0)
                    .setInterpolator(new AccelerateInterpolator())
                    .setDuration(300)
                    .setStartDelay(500)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            root.removeView(headsUp);
                        }
                    });
        }

        getActivity().getFragmentManager().beginTransaction().remove(this).commit();
    }

}
