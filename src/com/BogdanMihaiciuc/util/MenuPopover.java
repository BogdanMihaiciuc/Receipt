package com.BogdanMihaiciuc.util;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

//import com.BogdanMihaiciuc.receipt.R;
//import com.BogdanMihaiciuc.receipt.Receipt;

import java.util.ArrayList;

public class MenuPopover extends Popover {

    public interface OnMenuItemSelectedListener {
        public void onMenuItemSelected(Object item, int index);
    }

    final static int MinWidthDP = 160;
    final static int EndPaddingDP = 48;

    private CharSequence title;
    private ArrayList<CharSequence> items;
    private ArrayList<?> objectItems;
    private int selection = -1;
    private boolean initializedText = false;
    private int textColor = -1;

    private LinearLayout list;
    private TextView header;

    private Activity activity;

    private Utils.DPTranslator pixels;

    private OnMenuItemSelectedListener listener;

    private int suggestedWidth = 0;

    private LegacyActionBar.CustomViewProvider listCreator = new LegacyActionBar.CustomViewProvider() {
        public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
            return createLayout(container.getContext());
        }

        public void onDestroyCustomView(View customView) {}
    };

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pixels = new Utils.DPTranslator(getResources().getDisplayMetrics().density);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        if (!initializedText) {
            initializedText = true;
            textColor = getResources().getColor(Utils.DashboardText);
        }


        super.onActivityCreated(savedInstanceState);

        activity = getActivity();
    }

    public void onDetach() {
        super.onDetach();

        activity = null;

        list = null;
        header = null;
    }

    public void setOnMenuItemSelectedListener(OnMenuItemSelectedListener listener) {
        this.listener = listener;
    }

    public MenuPopover(AnchorProvider anchor, ArrayList<CharSequence> entries) {
        super(anchor);
        super.setLayoutListener(listCreator);
        items = new ArrayList<CharSequence>(entries);
    }

    private MenuPopover(AnchorProvider anchor) {
        super(anchor);
    }

    public static MenuPopover objectMenuPopover(AnchorProvider anchor, ArrayList<?> entries) {
        MenuPopover p = new MenuPopover(anchor);
        p.objectItems = entries;
        p.setLayoutListener(p.listCreator);
        return p;
    }

    public void setTextColor(int color) {
        initializedText = true;
        textColor = color;
    }

    public void setSelection(int selection) {
        if (list != null) {
            if (this.selection != -1) {
                list.getChildAt(this.selection + 2).setSelected(false);
            }
            list.getChildAt(selection + 2).setSelected(true);
        }
        this.selection = selection;
    }

    public void setTitle(CharSequence title) {
        this.title = title;
        if (header != null) {
            header.setText(title);

            header.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(getResources().getDimensionPixelSize(Utils.LineHeight), View.MeasureSpec.EXACTLY));
            if (suggestedWidth < header.getMeasuredWidth() + pixels.get(32 + EndPaddingDP)) {
                suggestedWidth = header.getMeasuredWidth() + pixels.get(32 + EndPaddingDP);
            }

            if (suggestedWidth > pixels.get(MinWidthDP)) {
                list.getLayoutParams().width = suggestedWidth;
            }
        }
    }

    protected View createLayout (final Context activity) {
        suggestedWidth = 0;

        if (list == null) {
            list = new LinearLayout(activity);
            list.setOrientation(LinearLayout.VERTICAL);
            list.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            list.setMinimumWidth(pixels.get(MinWidthDP));
        }
        else {
            list.removeAllViews();
        }

        if (title != null) {
            header = new TextView(activity);
            header.setTextSize(24);
            header.setTextColor(textColor);
            header.setPadding(getResources().getDimensionPixelSize(Utils.PrimaryKeyline), 0, getResources().getDimensionPixelSize(Utils.PrimaryKeyline), 0);
            header.setGravity(Gravity.CENTER_VERTICAL);
            header.setText(title);
            header.setTypeface(Utils.CondesedTypeface);
            list.addView(header, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, getResources().getDimensionPixelSize(Utils.GenericHeaderHeight)));

            header.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(getResources().getDimensionPixelSize(Utils.LineHeight), View.MeasureSpec.EXACTLY));
            if (suggestedWidth < header.getMeasuredWidth() + pixels.get(32 + EndPaddingDP)) {
                suggestedWidth = header.getMeasuredWidth() + pixels.get(32 + EndPaddingDP);
            }

            View separator = new View(activity);
            separator.setBackgroundColor(getResources().getColor(Utils.HeaderSeparator));
            list.addView(separator, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pixels.get(1)));
        }

        final ArrayList<?> itemArray;
        if (items != null) {
            itemArray = items;
        }
        else {
            itemArray = objectItems;
        }
        int i = 0;
        for (Object item : itemArray) {
            final TextView Entry = new TextView(activity);
            Entry.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(Utils.TextSize));
            Entry.setTextColor(textColor);
            Entry.setPadding(getResources().getDimensionPixelSize(Utils.PrimaryKeyline), 0, getResources().getDimensionPixelSize(Utils.PrimaryKeyline), 0);
            Entry.setGravity(Gravity.CENTER_VERTICAL);
            if (item instanceof CharSequence) {
                Entry.setText((CharSequence) item);
            }
            else {
                Entry.setText(item.toString());
            }
            Entry.setTag(item);
            list.addView(Entry, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, pixels.get(48)));
            Entry.setBackground(new LegacyRippleDrawable(getActivity()));
            Entry.setSelected(i == selection);
            Entry.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (MenuPopover.this.activity == null) return;
                    if (listener != null) listener.onMenuItemSelected(Entry.getTag(), itemArray.indexOf(Entry.getText()));
                    selection = itemArray.indexOf(Entry.getText());
                    Entry.setSelected(true);
                    int selectionOffset = title == null ? 0 : 2;
                    if (selection != -1) {
                        list.getChildAt(selection + selectionOffset).setSelected(false);
                    }
                    close();
                }
            });
            Entry.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(pixels.get(48), View.MeasureSpec.EXACTLY));
            if (suggestedWidth < Entry.getMeasuredWidth() + pixels.get(32 + EndPaddingDP)) {
                suggestedWidth = Entry.getMeasuredWidth() + pixels.get(32 + EndPaddingDP);
            }

            i++;
        }

        if (suggestedWidth > pixels.get(MinWidthDP)) {
            list.getLayoutParams().width = suggestedWidth;
        }

        return list;
    }

}
