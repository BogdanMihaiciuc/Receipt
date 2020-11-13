package com.BogdanMihaiciuc.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

//import com.BogdanMihaiciuc.receipt.Receipt;

public class TooltipPopover extends Popover {

    final static boolean USE_CLASSIC_TOOLTIP = false;

    static TooltipPopover currentTooltip;

    final static Paint TextMeasurePaint = new Paint();
    static {
        TextMeasurePaint.setTypeface(Utils.DefaultTypeface);
    }

    final static int MaxWidthDP = 240;

    final static int TimeoutDefault = 2000;
    final static int TimeoutInfinite = -1;

    final static int TitleID = LegacyActionBarView.generateViewId();
    final static int MessageID = LegacyActionBarView.generateViewId();

    final static int AutoGravity = - 1;

    private LegacyActionBar.CustomViewProvider layout;

    private String title;
    private int titleResource;

    private String message;
    private int messageResource;

    private int textColor = 0xFFFFFFFF;

    private int requestedGravity = AutoGravity;

    private long timeout;

    private boolean phone, landscape;
    private boolean isInitialGravity = true;

    public TooltipPopover(final String Title, final String Message, AnchorProvider anchor) {
        super(anchor);

        if (USE_CLASSIC_TOOLTIP) {
            super.setBackgroundColor(0xFFFFFFBB);
        }
        else {
            super.setBackgroundColor(0xAA000000);
        }
        super.enableOverflowAnimationStyle();
        super.setConsumesMotionEvents(false);
        super.setTransientEnabled(true);
        super.setHideKeyboardEnabled(false);
        super.setIndicatorScale(0.5f);

        this.title = Title;
        this.titleResource = 0;

        this.message = Message;
        this.messageResource = 0;

        layout = new LegacyActionBar.CustomViewProvider() {
            @Override
            public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
                Context context = inflater.getContext();
                Utils.DPTranslator pixels = new Utils.DPTranslator(context.getResources().getDisplayMetrics().density);

                FrameLayout tooltip = new FrameLayout(context);

                int standardPadding = context.getResources().getDimensionPixelSize(Utils.PrimaryKeyline);

                if (Title != null) {
                    TextView title = new TextView(context);
                    title.setTextSize(24);
                    title.setTypeface(Utils.CondesedTypeface);
                    if (USE_CLASSIC_TOOLTIP) {
                        title.setTextColor(0xFF000000);
                    }
                    else {
                        title.setTextColor(textColor);
                    }
                    title.setGravity(Gravity.CENTER_VERTICAL);

                    title.setPadding(standardPadding, 0, standardPadding, 0);

                    title.setText(Title);
                    title.setMaxLines(1);

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (context.getResources().getDisplayMetrics().scaledDensity * 48 + 0.5f));
                    tooltip.addView(title, params);
                }

                ViewGroup.LayoutParams layoutParams = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

                if (Message != null) {
                    TextView message = new TextView(context);
                    message.setTextSize(16);
                    message.setTypeface(Utils.DefaultTypeface);
                    if (USE_CLASSIC_TOOLTIP) {
                        message.setTextColor(0xFF000000);
                    }
                    else {
                        message.setTextColor(textColor);
                    }

                    message.setPadding(standardPadding, Title == null ? standardPadding : 0, standardPadding, Title == null ? standardPadding : pixels.get(8));

                    message.setText(Message);

                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                    if (title != null) {
                        params.topMargin = (int) (context.getResources().getDisplayMetrics().scaledDensity * 48 + 0.5f);
                    }
                    tooltip.addView(message, params);

                    TextMeasurePaint.setTextSize(message.getTextSize());
                    TextMeasurePaint.setTypeface(message.getTypeface());

                    if (TextMeasurePaint.measureText(Message) + (standardPadding * 2) + .5f > pixels.get(MaxWidthDP)) {
                        layoutParams.width = pixels.get(MaxWidthDP);
                    }
                    else {
//                        layoutParams.width = (int) (TextMeasurePaint.measureText(Message) + (standardPadding * 2) + .5f);
                        layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT;

//                        final int Measure = layoutParams.width;
//                        final View Message = message;
//
//                        message.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//                            @Override
//                            public void onGlobalLayout() {
//                                Log.d(TAG, "Measured size is " + Measure + ", real size is " + Message.getWidth());
//                            }
//                        });
                    }
                }

                tooltip.setLayoutParams(layoutParams);

                return tooltip;
            }

            @Override
            public void onDestroyCustomView(View customView) {

            }
        };


        super.setLayoutListener(layout);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        phone = getActivity().getResources().getConfiguration().smallestScreenWidthDp < 600;
        landscape = getActivity().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        isInitialGravity = true;
    }

    Rect anchorFrame = new Rect();
    Rect globalVisibleFrame = new Rect();

    private boolean visible = false;

    protected final void onAnimationBegin() {
        visible = true;
    }

//    protected void onPositionWillChange() {
//        if (visible) {
//            if (getAnchor() == null) {
//                dismiss();
//            }
//            else {
//                Rect anchorRect = getAnchorFrame();
//                Log.e(TAG, "The anchor frame is: " + anchorRect);
//                if (anchorRect.width() == 0 || anchorRect.height() == 0) {
//                    dismiss();
//                }
//            }
//        }
//    }

    protected void onPositionDidChange() {

        if (requestedGravity != AutoGravity) return;

        final boolean IsInitialGravity = isInitialGravity;

        View anchor = getAnchor();
        globalVisibleFrame = getCurrentRootFrame();
        if (anchor != null) {
            anchor.getGlobalVisibleRect(anchorFrame);
            boolean fitsDown = anchorFrame.centerY() <= globalVisibleFrame.centerY();

            if (!fitsDown) {
                if ((!phone || landscape) && message != null) {
                    if (anchorFrame.centerX() < globalVisibleFrame.centerX()) {
                        setGravity(GravityRightOf, !IsInitialGravity);
                    }
                    else {
                        setGravity(GravityLeftOf, !IsInitialGravity);
                    }
                }
                else {
                    setGravity(GravityAbove, !IsInitialGravity);
                }
            }
            else {
                setGravity(GravityBelow, !IsInitialGravity);
            }

            isInitialGravity = false;
        }
    }

    public void setTextColor(int color) {
        textColor = color;
    }

    public int getTextColor() {
        return textColor;
    }

    public void requestGravity(int gravity) {
        requestedGravity = gravity;
        super.setGravity(gravity, false);
//        throw new UnsupportedOperationException("The TooltipPopover manages its own gravity.");
    }

    public Popover show(Activity activity) {
        return show(activity, TimeoutDefault);
    }

    public TooltipPopover show(Activity activity, long timeout) {
        super.show(activity);

        if (currentTooltip != null) {
            currentTooltip.dismiss();
        }
        currentTooltip = this;

        if (timeout != TimeoutInfinite) {
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    dismiss();
                }
            }, timeout);
        }

        return this;
    }

    protected void close() {
        currentTooltip = null;

        super.close();
    }

}
