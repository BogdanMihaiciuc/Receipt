package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.BogdanMihaiciuc.receipt.R;
import com.BogdanMihaiciuc.receipt.Receipt;

public class BetaUtilities {

    public static void showUnderConstructionFromView(final View Source) {
        final Activity Context = (Activity) Source.getContext();

        final float density = Context.getResources().getDisplayMetrics().density;

        final FrameLayout ConstructionLayout = new FrameLayout(Context);
        final ImageView Logo = new ImageView(Context);
        Logo.setImageDrawable(Context.getResources().getDrawable(R.drawable.logo));

        ConstructionLayout.setClickable(true);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.bottomMargin = (int) (48 * density + 0.5f);

        ConstructionLayout.addView(Logo, params);

        final TextView Text = new TextView(Context);
        Text.setTextSize(30);
        Text.setTypeface(Receipt.condensedTypeface());
        Text.setTextColor(Context.getResources().getColor(R.color.white));
        Text.setText("Under Construction");

        params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER;
        params.topMargin = (int) (48 * density + 0.5f);

        ConstructionLayout.addView(Text, params);

        final ViewGroup Root = (ViewGroup) Context.getWindow().getDecorView();
        Root.addView(ConstructionLayout);

        Source.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                ConstructionLayout.setBackgroundColor(Utils.interpolateColors(fraction, 0x00000000, 0xCC000000));
                Logo.setAlpha(fraction);
                Text.setAlpha(fraction);

                Logo.setTranslationY(Utils.interpolateValues(fraction, 48 * density, 0f));
                Text.setTranslationY(Utils.interpolateValues(fraction, -48 * density, 0f));

            }
        });

        animator.setDuration(400);
        animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        animator.start();

        ConstructionLayout.setOnClickListener(new View.OnClickListener() {
            boolean clicked;
            @Override
            public void onClick(View view) {
                if (clicked) return;

                clicked = true;

                animator.end();
                animator.reverse();

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        Root.removeView(ConstructionLayout);
                        Source.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                });
            }
        });
    }

}
