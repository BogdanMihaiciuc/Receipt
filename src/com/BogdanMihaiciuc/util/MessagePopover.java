package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.content.res.Resources;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

//import com.BogdanMihaiciuc.receipt.R;
//import com.BogdanMihaiciuc.receipt.Receipt;

/**
 * The message popover delivers a one-shot modal message. The user must acknowledge the message by tapping the OK button in order to dismiss it.
 */
public class MessagePopover extends Popover {

    private CharSequence title;
    private CharSequence description;
    private String okButtonLabel;

    public MessagePopover(CharSequence title, CharSequence description) {
        super(new AnchorProvider() {
            @Override
            public View getAnchor(Popover popover) {
                return null;
            }
        });

        setHasDragons(true);

        setLayoutListener(messageLayout);
        setGravity(GravityCenter);
        setShowAsWindowEnabled(true);
        setShowAsModalEnabled(true);

        this.title = title;
        this.description = description;
    }

    /**
     * Sets the label that will be used for the OK button.
     * @param label The label.
     * @return This MessagePopover.
     */
    public MessagePopover setOKButtonLabel(String label) {
        okButtonLabel = label;

        return this;
    }

    protected LegacyActionBar.CustomViewProvider messageLayout = new LegacyActionBar.CustomViewProvider() {
        @Override
        public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
            Context context = container.getContext();
            Resources res = context.getResources();

            $.bind(context);

            final float Density = context.getResources().getDisplayMetrics().density;
            final Utils.DPTranslator pixels = new Utils.DPTranslator(Density);

            FrameLayout layout = new FrameLayout(context);

            TextView title = new TextView(context);
            title.setTextSize(24);
            title.setGravity(Gravity.CENTER_VERTICAL);
            if (!hasDragons()) {
                title.setTextColor(0xFFFFFFFF);
            }
            else {
                title.setTextColor($.color(Utils.DashboardText));
            }
            title.setTypeface(Utils.CondesedTypeface);
            title.setPadding($.dimen(Utils.PrimaryKeyline), 0, 0, 0);
            if (!hasDragons()) title.setBackground(res.getDrawable(Utils.ConfirmationTitlebar));
            title.setText(MessagePopover.this.title);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, res.getDimensionPixelSize(Utils.GenericHeaderHeight));
            title.setLayoutParams(params);

            View separator = new View(context);
            if (!hasDragons())  separator.setBackgroundColor(res.getColor(Utils.DashboardSeparatorTransparent));
            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pixels.get(1));
            params.topMargin = title.getLayoutParams().height - pixels.get(1);
            separator.setLayoutParams(params);

            TextView description = new TextView(context);
            description.setTextSize(16);
            description.setLineSpacing(pixels.get(8), 1);
            description.setPadding($.dimen(Utils.PrimaryKeyline), $.dimen(Utils.PrimaryKeyline), $.dimen(Utils.PrimaryKeyline), $.dimen(Utils.PrimaryKeyline));
            description.setTextColor(res.getColor(Utils.DashboardText));
            description.setText(MessagePopover.this.description);

            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = title.getLayoutParams().height;
            params.bottomMargin = pixels.get(48);
            description.setLayoutParams(params);


            Button button = new Button(context, null, android.R.attr.borderlessButtonStyle);
            button.setTextSize(16);
            button.setAllCaps(true);
            button.setTypeface(Utils.MediumTypeface);
            button.setTextColor(res.getColor(Utils.DashboardText));
            button.setText(okButtonLabel == null ? res.getString(Utils.OKLabel) : okButtonLabel);
            button.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    dismiss();
                }
            });

            button.setBackground(Utils.getDeselectedColors(context));
            button.setPadding($.dp(16), 0, $.dp(16), 0);

            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, pixels.get(48));
            params.rightMargin = $.dimen(Utils.PrimaryKeyline);
            params.bottomMargin = $.dp(8);
            params.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            button.setLayoutParams(params);

            View buttonSeparator = new View(context);
//            buttonSeparator.setBackgroundColor(res.getColor(Utils.DashboardSeparatorTransparent));
            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pixels.get(1));
            params.bottomMargin = pixels.get(48) - pixels.get(1);
            params.gravity = Gravity.BOTTOM;
            buttonSeparator.setLayoutParams(params);

            layout.addView(title);
            layout.addView(separator);
            layout.addView(description);
            layout.addView(button);
            layout.addView(buttonSeparator);

            params = new FrameLayout.LayoutParams(hasDragons() ? ViewGroup.LayoutParams.MATCH_PARENT : pixels.get(280), ViewGroup.LayoutParams.WRAP_CONTENT);
            layout.setLayoutParams(params);

            $.unbind();

            return layout;
        }

        @Override
        public void onDestroyCustomView(View customView) {

        }
    };

}
