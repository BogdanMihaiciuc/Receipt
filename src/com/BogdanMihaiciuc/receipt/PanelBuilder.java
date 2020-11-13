package com.BogdanMihaiciuc.receipt;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.PrecisionRangeSlider;

import java.util.ArrayList;

public class PanelBuilder {

    final static String TAG = PanelBuilder.class.getName();

    public final static int TypeText = 0;
    public final static int TypeEditText = 1;
    public final static int TypeSpinner = 2;
    public final static int TypeSlider = 3;
    public final static int TypeButton = 4;

    private static class PanelItem {
        String name;
        int type;
        int id;

        private PanelItem(String name, int type, int id) {
            this.name = name;
            this.type = type;
            this.id = id;
        }
    }

    private ArrayList<PanelItem> settings = new ArrayList<PanelItem>();
    private PanelItem titleSetting;
    private PanelItem headerSetting;

    private Context context;

    private DisplayMetrics metrics;
    private float density;
    private int alignmentWidth;
    private int alignmentPadding;
    private int fieldMinWidth;
    private int lineHeight;
    private int textSize;

    public PanelBuilder(Context context) {
        this.context = context;
        metrics = context.getResources().getDisplayMetrics();
        density = metrics.density;
        alignmentWidth = context.getResources().getDimensionPixelSize(R.dimen.AlignmentWidth);
        lineHeight = (int) (48 * density + 0.5f);
        alignmentPadding = (int) (48 * density + 0.5f);
        fieldMinWidth = (int) (100 * density + 0.5f);
        textSize = context.getResources().getDimensionPixelSize(R.dimen.TextSize);
    }

    public PanelBuilder setTitleSetting(String name, int type, int id) {
        titleSetting = new PanelItem(name, type, id);
        return this;
    }

    public PanelBuilder removeTitleSetting() {
        titleSetting = null;
        return this;
    }

    public PanelBuilder setHeaderSetting(String name, int type, int id) {
        if (type != TypeText) throw new IllegalArgumentException("The header can only be of 'Text' type!");
        headerSetting = new PanelItem(name, type, id);
        return this;
    }

    public PanelBuilder removeHeaderSetting() {
        headerSetting = null;
        return this;
    }

    public PanelBuilder addSetting(String name, int type, int id) {
        settings.add(new PanelItem(name, type, id));
        return this;
    }

    public PanelBuilder removeSetting(int id) {
        for (PanelItem setting : settings) {
            if (setting.id == id) {
                settings.remove(id);
                break;
            }
        }
        return this;
    }

    public PanelBuilder setTitleWidth(int width) {
        alignmentWidth = width;
        return this;
    }

    public PanelBuilder setSettingMargin(int margin) {
        alignmentPadding = margin;
        return this;
    }

    private void buildLine(FrameLayout panel, PanelItem settingItem, int marginTop) {
        TextView title = new TextView(context);
        title.setTextColor(context.getResources().getColor(R.color.DashboardTitle));
        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);
        title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        title.setText(settingItem.name);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(alignmentWidth, lineHeight);
        params.topMargin = marginTop;
        title.setLayoutParams(params);
        panel.addView(title);

        if (settingItem.type == TypeText || settingItem.type == TypeEditText || settingItem.type == TypeButton) {
            TextView setting;
            if (settingItem.type == TypeEditText) {
                setting = new EditText(context);
                EditText editSetting = (EditText) setting;

                editSetting.setMinWidth(fieldMinWidth);
                editSetting.setSelectAllOnFocus(true);
                editSetting.setBackground(context.getResources().getDrawable(R.drawable.textbox_mini_states));
            }
            else {
                int defStyle;
                if (settingItem.type == TypeButton) {
                    defStyle = android.R.attr.borderlessButtonStyle;
                }
                else {
                    defStyle = android.R.attr.textViewStyle;
                }
                setting = new TextView(context, null, defStyle);
            }

            setting.setTextColor(context.getResources().getColor(R.color.DashboardText));
            setting.setTextSize(TypedValue.COMPLEX_UNIT_PX, textSize);

            if (settingItem.type == TypeButton) {
                setting.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.spinner_dark, 0);
                setting.setCompoundDrawablePadding((int) (8 * metrics.density + 0.5f));
                setting.setBackground(com.BogdanMihaiciuc.util.Utils.getDeselectedColors(context));
            }

            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, lineHeight);
            params.leftMargin = alignmentWidth + alignmentPadding;
            params.topMargin = marginTop;
            setting.setLayoutParams(params);
            setting.setId(settingItem.id);
            setting.setGravity(Gravity.CENTER_VERTICAL);
            panel.addView(setting);
        }
        else if (settingItem.type == TypeSpinner) {
            Spinner setting = new Spinner(context);
            setting.setMinimumWidth(fieldMinWidth);
            setting.setPopupBackgroundDrawable(context.getResources().getDrawable(R.drawable.suggestion_menu));

            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, lineHeight);
            params.leftMargin = alignmentWidth + alignmentPadding;
            params.topMargin = marginTop;
            setting.setLayoutParams(params);
            setting.setId(settingItem.id);
            panel.addView(setting);
        }
        else if (settingItem.type == TypeSlider) {
            PrecisionRangeSlider setting = new PrecisionRangeSlider(context);
            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, lineHeight * 2);
            params.leftMargin = alignmentWidth + alignmentPadding;
            params.topMargin = marginTop;
            params.rightMargin = alignmentPadding;
            setting.setLayoutParams(params);
            setting.setId(settingItem.id);
            panel.addView(setting);
        }
    }

    public FrameLayout build() {
        FrameLayout panel = new FrameLayout(context);
        int height = 0;

        if (headerSetting != null) {
            height += 2 * lineHeight;

            TextView header = new TextView(context);
            header.setTypeface(Receipt.condensedLightTypeface());
            header.setTextSize(TypedValue.COMPLEX_UNIT_PX, context.getResources().getDimensionPixelSize(R.dimen.BalanceTextSize));
            header.setTextColor(context.getResources().getColor(R.color.DashboardText));
            header.setGravity(Gravity.CENTER);
            header.setId(headerSetting.id);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, 2 * lineHeight);
            params.gravity = Gravity.CENTER_HORIZONTAL;
            header.setLayoutParams(params);
            panel.addView(header);

            View separator = new View(context);
            separator.setBackgroundColor(context.getResources().getColor(R.color.DashboardSeparatorTransparent));
            params = new FrameLayout.LayoutParams(2 * alignmentWidth + alignmentPadding, (int) (1 * density));
            params.gravity = Gravity.CENTER_HORIZONTAL;
            params.topMargin = 2 * lineHeight - (int) (1 * density);
            separator.setLayoutParams(params);
            panel.addView(separator);
        }

        if (titleSetting != null) {
            int margin = height;
            height += lineHeight;
            TextView title = new TextView(context);
            title.setTypeface(Receipt.condensedTypeface());
            title.setTextColor(context.getResources().getColor(R.color.DashboardText));
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
            title.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            title.setText(titleSetting.name);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(alignmentWidth, lineHeight);
            params.topMargin = margin;
            title.setLayoutParams(params);
            panel.addView(title);

            if (titleSetting.type == TypeText || titleSetting.type == TypeEditText || titleSetting.type == TypeButton) {
                TextView setting;
                if (titleSetting.type == TypeEditText) {
                    setting = new EditText(context);
                    EditText editSetting = (EditText) setting;

                    editSetting.setMinWidth(fieldMinWidth);
                    editSetting.setSelectAllOnFocus(true);
                    editSetting.setBackground(context.getResources().getDrawable(R.drawable.textbox_mini_states));
                }
                else {
                    int defStyle;
                    if (titleSetting.type == TypeButton) {
                        defStyle = android.R.attr.borderlessButtonStyle;
                    }
                    else {
                        defStyle = android.R.attr.textViewStyle;
                    }
                    setting = new TextView(context, null, defStyle);
                }

                setting.setTypeface(Receipt.condensedTypeface());
                setting.setTextColor(context.getResources().getColor(R.color.DashboardText));
                setting.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
                setting.setGravity(Gravity.CENTER_VERTICAL);

                if (titleSetting.type == TypeButton) {
                    setting.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.spinner_dark, 0);
                    setting.setCompoundDrawablePadding((int) (8 * metrics.density + 0.5f));
                    setting.setBackground(com.BogdanMihaiciuc.util.Utils.getDeselectedColors(context));
                }

                params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, lineHeight);
                params.leftMargin = alignmentWidth + alignmentPadding;
                params.topMargin = margin;
                setting.setLayoutParams(params);
                setting.setId(titleSetting.id);
                panel.addView(setting);
            }
            else if (titleSetting.type == TypeSpinner) {
                Spinner setting = new Spinner(context);
                setting.setMinimumWidth(fieldMinWidth);

                params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, lineHeight);
                params.leftMargin = alignmentWidth + alignmentPadding;
                params.topMargin = margin;
                setting.setLayoutParams(params);
                setting.setId(titleSetting.id);
                panel.addView(setting);
            }
        }

        for (PanelItem setting : settings) {
            buildLine(panel, setting, height);
            if (setting.type == TypeSlider) {
                height += 2 * lineHeight;
            }
            else {
                height += lineHeight;
            }
        }

        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        panel.setLayoutParams(params);

        return panel;
    }

    /**
     * Gets the label associated with the setting view, if it is part of a panel created by the PanelBuilder.
     * @param setting A setting view that has been created by the PanelBuilder.
     * @return The view representing the label associated with the setting view.
     */
    public static TextView labelOfSettingFromPanel(View setting) {
        ViewGroup panel = (ViewGroup) setting.getParent();
        return (TextView) panel.getChildAt(panel.indexOfChild(setting) - 1);
    }

    /**
     * Gets the header setting view from a panel created by the PanelBuilder.
     * @param panel A panel created by the PanelBuilder.
     * @return The header view associated with the panel. If the panel has no header, the return value is undefined.
     */
    public static View headerSettingOfPanel(ViewGroup panel) {
        return panel.getChildAt(0);
    }

    /**
     * Gets the header separator view from a panel created by the PanelBuilder.
     * @param panel A panel created by the PanelBuilder.
     * @return The header separator view associated with the panel. If the panel has no header, the return value is undefined.
     */
    public static View headerSeparatorOfPanel(ViewGroup panel) {
        return panel.getChildAt(1);
    }

}
