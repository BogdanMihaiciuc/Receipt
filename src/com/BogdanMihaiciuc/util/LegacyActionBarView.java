package com.BogdanMihaiciuc.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.TextView;

//import com.BogdanMihaiciuc.receipt.R;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.BogdanMihaiciuc.util.LegacyActionBar.ActionItem;
import static com.BogdanMihaiciuc.util.LegacyActionBar.LegacyNavigationElement;

public class LegacyActionBarView extends RelativeLayout {

    final static String TAG = LegacyActionBarView.class.getName();

    final static boolean USE_POPOVER_MENUS = true;

    final static boolean DEBUG_DISABLE_OVERFLOW = false;
    final static boolean DEBUG_SPLIT = false;

    final static boolean USE_EXPERIMENTAL_BUTTON_SIZES = true;

    public final static String ActionItemMetadataKey = "LegacyActionBarView$ActionItem";

    final static int ButtonWidthDP = 56;
    final static int ButtonWidthTabletDP = 64;

    final static int SelectorThicknessDP = 6;

    final static int SeparatorPaddingDP = 8;

    final static int CaretLeftPaddingDP = 0;
    final static int CaretRightPaddingDP = 1;
    final static int BackButtonRightPaddingDP = 2;

    final static int DonePaddingDP = 8;

    final static float DefaultTextSize = 16;
    final static float DefaultSubtitleSize = 14;
    final static float DefaultActionTextSize = 12;
    final static float DefaultDoneTextSize = 12;
    final static int DefaultTextColor = 0xFFFFFFFF;

    final static int[] MinimumWidthForButtonCount = {
            320, //0
            320, //1
            320, //2
            USE_EXPERIMENTAL_BUTTON_SIZES ? 320 : 360, //3
            424, //4
            560  //5
    };

    final static int PhonePortraitHeightDP = 56;
    final static int PhoneLandscapeHeightDP = 48;
    final static int TabletHeightDP = 64;

    public final static int CaretBackMode = 0;
    public final static int DoneBackMode = 1;

    public final static int NoNavigationMode = 0;
    public final static int TabsNavigationMode = 1;
    public final static int SpinnerNavigationMode = 2;
    public final static int InlineTabsNavigationMode = 3;

    public final static int BackButtonPositionLeft = 0;
    public final static int BackButtonPositionRight = 1;

    public final static int SplitZoneAlignmentFill = 0;
    public final static int SplitZoneAlignmentCenter = 1;
    public final static int SplitZoneAlignmentLeft = 2;
    public final static int SplitZoneAlignmentRight = 3;

    public final static int CaretID = Integer.MAX_VALUE;
    public final static int DoneID = Integer.MAX_VALUE - 6;

    public final static int DoneTitleID = Integer.MAX_VALUE - 8;
    public final static int TitleID = Integer.MAX_VALUE - 3;

    public final static int LogoID = Integer.MAX_VALUE - 1;

    public final static int BackID = Integer.MAX_VALUE - 2;
    public final static int OverflowID = Integer.MAX_VALUE - 4;

    public final static int ActionBarID = Integer.MAX_VALUE - 5;

    final static int DoneSeparatorID = Integer.MAX_VALUE - 9;

    final static Paint TextMeasure;

    static {
        TextMeasure = new Paint();
        TextMeasure.setTypeface(Typeface.DEFAULT_BOLD);
    }

    private int height;
    private int buttonWidth;
    private int caretLeftPadding;
    private int caretRightPadding;
    private int backButtonRightPadding;
    private int donePadding;
    private int selectorThickness;
    private int primaryKeyline;
    private int secondaryKeyline;

    private boolean tablet;
    private boolean landscape;
    private boolean titlesAllowed;
    private boolean showOverflow;

    private int maxActionButtons;
    private int usedActionButtons;
    private int suggestedActionButtons;

    private DisplayMetrics metrics;

    private ArrayList<ActionItem> actionItems;

    protected LegacyActionBar.ActionBarWrapper wrapper;

    RelativeLayout container;
        ViewGroup backButton;
            ImageView caret;
            ImageView logo;

            ImageView done;
        TextView doneTitle;

            TextView title;

        RelativeLayout titleContainer;
            TextView subtitle;
            TextView backupTitle;
            TextView backupSubtitle;

        Spinner navigationSpinner;

        ImageView overflowButton;

        ArrayList<View> separators;

        View separator;

        FrameLayout customViewContainer;

        LinearLayout navigationContainer;
        ArrayList<View> navigationSeparators = new ArrayList<View>();
    LinearLayout splitZone;

    private PopupMenu overflowMenu;

    private LegacyActionBar listener;

    public void setCaretResource(int resource) {
        if (caret != null) {
            caret.setImageDrawable(resource == 0 ? null : getResources().getDrawable(resource));
        }
    }

    public void setDoneResource(int resource) {
        if (done != null) {
            done.setImageDrawable(resource == 0 ? null : getResources().getDrawable(resource));
        }
    }

    public void setBackMode(int mode) {
        prepareBackButton();
        prepareTitle();
    }

    public void setOverflowResource(int resource) {
        if (overflowButton != null) {
            overflowButton.setImageDrawable(resource == 0 ? null : getResources().getDrawable(resource));
        }
    }

    public int getOverflowResource(int resource) {
        return resource;
    }

    public void setLogoResource(int resource) {
        if (logo != null) {
            logo.setImageDrawable(resource == 0 ? null : getResources().getDrawable(resource));
        }
    }

    public void setLogoDrawable(Drawable drawable) {
        if (logo != null) {
            logo.setImageDrawable(drawable);
        }
    }

    public void setTitleResource(int resource) {

        if (wrapper.backButtonMode == DoneBackMode) {
            if (title != null) applyVerboseTitleTo(title);
            return;
        }

        if (title != null) {
            if (resource != 0)
                title.setText(getResources().getString(resource));
            else
                title.setText("");
        }
    }

    public void setTitle(CharSequence title) {

        if (wrapper.backButtonMode == DoneBackMode) {
            if (this.title != null) applyVerboseTitleTo(this.title);
            return;
        }

        if (this.title != null) {
            this.title.setText(title);
        }
    }

    public int getActionBarHeight() {
        return height;
    }

    public void setTitleAnimated(CharSequence title, int direction) {

        if (backupTitle == null || this.title == null) {
            Log.e(TAG, "Unable to make the title change animated! Reverting to default behaviour. BackupTitle: " + backupTitle);
            setTitle(title);
            return;
        }

        backupTitle.setText(this.title.getText());
        backupTitle.setTranslationY(0f);
        backupTitle.setAlpha(1f);

        this.title.setTranslationY(direction * 24 * metrics.density);
        this.title.setAlpha(0f);
        applyVerboseTitleTo(this.title);

        backupTitle.animate().cancel();
        this.title.animate().cancel();

        backupTitle.animate().alpha(0f).translationY(-direction * 24 * metrics.density).setDuration(200);
        this.title.animate().alpha(1f).translationY(0f).setDuration(200);

    }

    public void setSubtitleResource(int resource) {

        if (wrapper.backButtonMode == DoneBackMode) {
            if (title != null) applyVerboseTitleTo(title);
            return;
        }

        if (subtitle != null) {
            if (resource != 0)
                subtitle.setText(getResources().getString(resource));
            else
                subtitle.setText("");
        }
    }

    public void setSubtitle(String subtitle) {

        if (wrapper.backButtonMode == DoneBackMode) {
            if (title != null) applyVerboseTitleTo(title);
            return;
        }

        if (this.subtitle != null) {
            this.subtitle.setText(subtitle);
        }
    }

    public void setTextColor(int color) {
        if (title != null) {
            title.setTextColor(color);
        }

        if (doneTitle != null) {
            doneTitle.setTextColor(color);
        }

        if (titlesAllowed) {
            for (ActionItem item : actionItems) {
                if (item.attachedView != null && item.attachedView instanceof TextView) {
                    ((TextView) item.attachedView).setTextColor(color);
                }
            }
        }
    }

    public void setBackgroundColor(int color) {
        if (splitZone != null) {
            splitZone.setBackgroundColor(color);
        }

        if (wrapper.getRoundedCorners() != null && wrapper.getRoundRadius() != 0) {
            super.setBackground(Utils.RoundedCornerDrawable.roundedCornersWithRadiusAndColor(wrapper.getRoundedCorners(), wrapper.getRoundRadius(), color));
        }
        else {
            super.setBackgroundColor(color);
        }
    }

    public void setDoneSeparatorVisible(boolean visible) {
        if (wrapper.backButtonMode == DoneBackMode) {
            if (findViewById(DoneSeparatorID) != null) {
                findViewById(DoneSeparatorID).setVisibility(visible ? VISIBLE : INVISIBLE);
            }
        }
    }

    public void setSeparatorVisible(boolean visible) {
        if (separator != null) {
            if (visible)
                separator.setVisibility(VISIBLE);
            else
                separator.setVisibility(INVISIBLE);
        }
    }

    public void setSeparatorOpacity(float opacity) {
        if (separator != null) {
            separator.setBackgroundColor(wrapper.getSeparatorColor());
        }
    }

    public void setSeparatorThickness(int thickness) {
        if (separator != null) {
            separator.getLayoutParams().height = (int) (thickness * metrics.density);
            if (separator.getHeight() != 0) separator.requestLayout();
        }
    }

    public void setInnerSeparatorOpacity(float opacity) {
        if (separators != null) {
            for (View separator : separators) {
                separator.setBackgroundColor(wrapper.getInnerSeparatorColor());
            }
        }
    }

    public void setBackButtonEnabled(boolean enabled) {
        if (!enabled) {
            if (wrapper.backButtonMode == CaretBackMode) {
                if (backButton != null) backButton.setEnabled(false);
                if (caret != null) caret.setVisibility(INVISIBLE);
            }
            else {
                if (backButton != null) {
                    backButton.setEnabled(false);
                    backButton.setAlpha(0.5f);
                }
            }
        }
        else {
            if (wrapper.backButtonMode == CaretBackMode) {
                if (backButton != null) backButton.setEnabled(true);
                if (caret != null) caret.setVisibility(VISIBLE);
            }
            else {
                if (backButton != null) {
                    backButton.setEnabled(true);
                    backButton.setAlpha(1f);
                }
            }
        }
    }

    /**
     * Controls whether the back button is visible on the actionBar or not.
     * This only works with the "Done" back button mode.
     * @param visible True if the back butotn is visible, false otherwise.
     */
    public void setBackButtonVisible(boolean visible) {
        if (wrapper.backButtonMode == DoneBackMode) {
            $.bind(getContext());

            if (visible) {
                backButton.setEnabled(true);
                $.wrap(titleContainer, backButton)
                        .stop("visibleQueue")
                        .animate()
                            .property($.TranslateX, 0)
                            .duration(300)
                        .start("visibleQueue");
            }
            else {
                backButton.setEnabled(false);
                $.wrap(titleContainer, backButton)
                        .stop("visibleQueue")
                        .animate()
                            .property($.TranslateX, -$.dimen(Utils.SecondaryKeyline) + $.dimen(Utils.PrimaryKeyline))
                            .duration(300)
                        .start("visibleQueue");
            }

            $.unbind();
        }
    }

    public void setNavigationGravity(int gravity) {
        if (wrapper.navigationMode == InlineTabsNavigationMode) {
            if (navigationContainer != null) {
                throw new IllegalStateException("setNavigationGravity() may not be called after the navigation elements have been created.");
//                ((FrameLayout.LayoutParams) navigationContainer.getLayoutParams()).gravity = gravity;
            }
        }
    }

    public void setNavigationMode(int mode) {
        if (mode == TabsNavigationMode || mode == InlineTabsNavigationMode) {
            if (navigationContainer != null) return;
            else {
                if (navigationSpinner != null) removeView(navigationSpinner);
                navigationSpinner = null;
                createNavigationElements();

                if (!landscape && !tablet && mode == TabsNavigationMode) {
                    getLayoutParams().height *= 2;
                    // if height is 0, the layout hasn't run yet
                    if (getHeight() != 0) setLayoutParams(getLayoutParams());
                }
            }
        }
        else {
            if (navigationContainer != null) removeView(navigationContainer);
            navigationContainer = null;
            if (mode == SpinnerNavigationMode) {
                if (navigationSpinner != null) return;
                createNavigationElements();

            }
            else {
                if (navigationSpinner != null) removeView(navigationSpinner);
                navigationSpinner = null;
            }
        }
    }

    public void deselectNavigationElement() {
        if (wrapper.selectedNavigationElement != null) {
            if (wrapper.selectedNavigationElement.selector != null) {
                wrapper.selectedNavigationElement.selector.setVisibility(INVISIBLE);
            }
        }
    }

    public void selectNavigationElement(LegacyNavigationElement element) {
        if (element.selector != null) {
            element.selector.setVisibility(VISIBLE);
        }
        if (navigationSpinner != null) {
            navigationSpinner.setSelection(wrapper.navigationElements.indexOf(element));
        }
    }

    public void onCustomViewChanged() {
        if (wrapper.customViewProvider != null) {
            if (customViewContainer != null) {
                removeView(customViewContainer);
            }
            else {
                removeButtons();
                if (titleContainer != null) removeView(titleContainer);
                titleContainer = null;
            }

            customViewContainer = new FrameLayout(getContext());
            addView(customViewContainer);
            View customView = wrapper.customViewProvider.onCreateCustomView(LayoutInflater.from(getContext()), customViewContainer);
            if (customView != null) customViewContainer.addView(customView);
            setBackButtonPosition(wrapper.backButtonPosition);
        }
        else {
            if (customViewContainer != null) {
                removeView(customViewContainer);
                createButtons();
                prepareTitle();
            }
        }
    }

    public void setBackButtonPosition(int position) {
        if (backButton == null || customViewContainer == null) return;

        RelativeLayout.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
        if (position == BackButtonPositionRight)
            params.addRule(ALIGN_PARENT_RIGHT);
        else
            params.addRule(ALIGN_PARENT_LEFT);

        backButton.setLayoutParams(params);

        int customViewAnchor;

        if (wrapper.backButtonMode == DoneBackMode) {
            View doneSeparator = findViewById(DoneSeparatorID);
            if (doneSeparator != null)
                removeView(doneSeparator);

            doneSeparator = createSeparator();
            doneSeparator.setId(DoneSeparatorID);
            doneSeparator.setVisibility(wrapper.doneSeparatorVisible ? VISIBLE : INVISIBLE);
            params = (LayoutParams) doneSeparator.getLayoutParams();
            if (position == BackButtonPositionRight)
                params.addRule(LEFT_OF, BackID);
            else
                params.addRule(RIGHT_OF, BackID);
            addView(doneSeparator);

            customViewAnchor = DoneSeparatorID;
        }
        else {
            customViewAnchor = BackID;
        }


        if (!wrapper.backButtonEnabled && wrapper.logoResource == 0 && TextUtils.isEmpty(wrapper.titleString) && wrapper.titleResource == 0) {
            params = new LayoutParams(0, height);
            params.addRule(ALIGN_PARENT_LEFT);
            params.addRule(ALIGN_PARENT_RIGHT);
        }
        else {
            params = new LayoutParams(0, height);
            if (position == BackButtonPositionRight) {
                params.addRule(ALIGN_PARENT_LEFT);
                params.addRule(LEFT_OF, customViewAnchor);
            } else {
                params.addRule(ALIGN_PARENT_RIGHT);
                params.addRule(RIGHT_OF, customViewAnchor);
            }
        }

        customViewContainer.setLayoutParams(params);
    }

    public void setFillContainerEnabled(boolean enabled) {
        throw new UnsupportedOperationException("setFillContainerEnabled must be called before the legacyActionBar has attached to its container.");
    }

    public LegacyRippleDrawable obtainRipple() {
        LegacyRippleDrawable background = new LegacyRippleDrawable(getContext());
        background.setShape(LegacyRippleDrawable.ShapeCircle);
        if (wrapper.hasCustomRippleColors) {
            background.setColors(Utils.transparentColor(0, wrapper.pressedColor), wrapper.pressedColor);
            background.setRippleColor(wrapper.rippleColor);
        }
        return background;
    }

    public void setRippleHighlightColors(int background, int ripple) {
        // Must enumerate children and adjust the ripple drawable accordingly where appropriate
        applyRippleColorsToLayout(background, ripple, this);

        if (splitZone != null) {
            applyRippleColorsToLayout(background, ripple, splitZone);
        }
    }

    private void applyRippleColorsToLayout(int background, int ripple, ViewGroup layout) {
        for (int i = 0; i < layout.getChildCount(); i++) {
            View child = layout.getChildAt(i);
            if (child.getBackground() instanceof LegacyRippleDrawable) {
                ((LegacyRippleDrawable) child.getBackground()).setColors(Utils.transparentColor(0, background), background);
                ((LegacyRippleDrawable) child.getBackground()).setRippleColor(ripple);
            }
        }
    }

    private class NavigationAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            return wrapper.navigationElements.size();
        }

        @Override
        public LegacyNavigationElement getItem(int i) {
            return wrapper.navigationElements.get(i);
        }

        @Override
        public long getItemId(int i) {
            return wrapper.navigationElements.get(i).id;
        }

        public View getView(int position, View convertView, ViewGroup container) {
            if (convertView == null) {
                convertView = new TextView(getContext());
                TextView textConvertView = (TextView) convertView;
                textConvertView.setTextSize(DefaultTextSize);
                textConvertView.setGravity(Gravity.CENTER_VERTICAL);
                if (!tablet && !landscape)
                    textConvertView.setPadding(donePadding, 0, donePadding, 0);
                else {
                    textConvertView.setPadding(donePadding, 0, 2 * donePadding, 0);
                }
                AbsListView.LayoutParams params = new AbsListView.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (int) (48 * metrics.density + 0.5f));
                convertView.setLayoutParams(params);
            }

            ((TextView) convertView).setText(getItem(position).name);
            return convertView;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup container) {
            convertView = getView(position, convertView, container);
            convertView.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;

            if (wrapper.selectedNavigationElement == getItem(position)) {
                convertView.setBackground(Utils.getSelectedColors(getContext()));
            }
            else {
                convertView.setBackground(Utils.getDeselectedColors(getContext()));
            }

            return convertView;
        }
    }

    public ActionItem addItem(int id, String title, int resource, boolean showTitle, boolean showAsIcon) {
        ActionItem item = new ActionItem();

        item.setId(id);
        item.setName(title);
        item.setResource(resource);
        item.setTitleVisible(showTitle);
        item.setVisibleAsIcon(showAsIcon);
        item.setVisible(true);
        item.setEnabled(true);

        item.listener = listener;

        actionItems.add(item);

        onItemAdded(item);

        return item;
    }

    public void addNavigationElement(LegacyNavigationElement element) {
        if (navigationSpinner != null) {
            ((BaseAdapter) navigationSpinner.getAdapter()).notifyDataSetChanged();
        }
    }

    public void clearNavigationElements() {
        if (navigationSpinner != null) {
            ((BaseAdapter) navigationSpinner.getAdapter()).notifyDataSetChanged();
        }
    }

    public void onItemReplaced(int idPre, ActionItem item) {
        ActionItem actionItem = listener.findItemWithId(idPre);
        if (actionItem.attachedView != null) {
            actionItem.attachedView.setId(item.getId());
            ((ImageView) actionItem.attachedView).setImageDrawable(item.getResource() == 0 ? null : getResources().getDrawable(item.getResource()));
            actionItem.attachedView.setTag(item);
            item.attachedView = actionItem.attachedView;

        }
    }

    public void onAttach(Activity activity) {

        metrics = getResources().getDisplayMetrics();

        int swdp = getResources().getConfiguration().smallestScreenWidthDp;
        tablet = swdp >= 600;

        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && wrapper.landscapeUIEnabled;

        titlesAllowed = tablet || landscape;

        showOverflow = !ViewConfiguration.get(activity).hasPermanentMenuKey();

        if (DEBUG_DISABLE_OVERFLOW) showOverflow = false;

        int wdp = getResources().getConfiguration().screenWidthDp;
        for (int i = 0; i < MinimumWidthForButtonCount.length; i++) {
            if (wdp >= MinimumWidthForButtonCount[i]) {
                maxActionButtons = i;
            }
            else break;
        }
        suggestedActionButtons = maxActionButtons;
        if (maxActionButtons < wrapper.forcedMinimumItems) {
            maxActionButtons = wrapper.forcedMinimumItems;
        }
//        Log.d("", "Max action buttons is " + maxActionButtons);

//        TypedValue tv = new TypedValue();
//        if (getContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
//        {
//            height = TypedValue.complexToDimensionPixelSize(tv.data, metrics);
//        }

        height = LegacyActionBar.obtainHeight(getResources());

        if (wrapper.fillContainer) {
            height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        caretLeftPadding = (int) ((CaretLeftPaddingDP * metrics.density) + 0.5);
        caretRightPadding = (int) ((CaretRightPaddingDP * metrics.density) + 0.5);

        backButtonRightPadding = (int) ((BackButtonRightPaddingDP * metrics.density) + 0.5);

        donePadding = (int) ((DonePaddingDP * metrics.density) + 0.5);

        selectorThickness = (int) ((SelectorThicknessDP * metrics.density) + 0.5);

        Utils.DPTranslator pixels = new Utils.DPTranslator(getResources().getDisplayMetrics().density);
        if (tablet) {
            buttonWidth = (int) ((ButtonWidthTabletDP * metrics.density) + 0.5);

            primaryKeyline = pixels.get(Dimensions.PrimaryKeylineTabletDP);
            secondaryKeyline = pixels.get(Dimensions.SecondaryKeylineTabletDP);
        }
        else {
            buttonWidth = (int) ((ButtonWidthDP * metrics.density) + 0.5);

            primaryKeyline = pixels.get(Dimensions.PrimaryKeylinePhoneDP);
            secondaryKeyline = pixels.get(Dimensions.SecondaryKeylinePhoneDP);
        }

    }

    private ActionItem backItem = new ActionItem();

    public void onCreateView() {
        Context context = getContext();

        backItem.setId(android.R.id.home);

//        container = new RelativeLayout(context);
//        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
//        container.setLayoutParams(params);

        int totalHeight = height;

        if (!landscape && !tablet && wrapper.navigationMode == TabsNavigationMode)
            totalHeight *= 2;

        setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, totalHeight));

        addView(createBackButton());
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                wrapper.getListener().onLegacyActionSelected(backItem);
            }
        });

        setId(ActionBarID);

        if (wrapper.customViewProvider == null) {
            prepareTitle();

            usedActionButtons = 0;

            if (wrapper.splitZoneEnabled) {
                createSplitZone();
            }
            createButtons();
        }
        else {
            onCustomViewChanged();
        }

        RelativeLayout.LayoutParams buttonParams;

        if (wrapper.backgroundResource != 0) {
            setBackgroundResource(wrapper.backgroundResource);
        }
        else {
            setBackgroundColor(wrapper.backgroundColor);
        }

        separator = new View (context);
        separator.setBackgroundColor(wrapper.getSeparatorColor());
        buttonParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) (wrapper.separatorThicknessDP * metrics.density));
        buttonParams.addRule(ALIGN_PARENT_BOTTOM);
        separator.setLayoutParams(buttonParams);

        addView(separator);
        if (!wrapper.separatorVisible) separator.setVisibility(INVISIBLE);

//        super.addView(container);

        createNavigationElements();

//        return container;
    }

    protected void createSplitZone() {
        splitZone = new LinearLayout(getContext());
        splitZone.setClipChildren(false);

        if (wrapper.backgroundResource == 0) {
            splitZone.setBackgroundColor(wrapper.backgroundColor);
        }
        else {
            splitZone.setBackgroundResource(wrapper.backgroundResource);
        }

        if (wrapper.getSplitZoneAlignment() == SplitZoneAlignmentCenter) {
            splitZone.setGravity(Gravity.CENTER);
        }
        else if (wrapper.getSplitZoneAlignment() == SplitZoneAlignmentRight) {
            splitZone.setGravity(Gravity.RIGHT);
        }
        else if (wrapper.getSplitZoneAlignment() == SplitZoneAlignmentLeft) {
            splitZone.setGravity(Gravity.LEFT);
        }


        wrapper.getSplitZone().addView(splitZone, LayoutParams.MATCH_PARENT, height);
    }

    public ViewGroup getSplitZone() {
        return splitZone;
    }

    protected void setSplitZoneAlignment(int alignment) {
        if (wrapper.getSplitZone() == null || splitZone == null) return;

        if (wrapper.getSplitZoneAlignment() == SplitZoneAlignmentCenter) {
            splitZone.setGravity(Gravity.CENTER);
        }
        else if (wrapper.getSplitZoneAlignment() == SplitZoneAlignmentRight) {
            splitZone.setGravity(Gravity.RIGHT);
        }
        else if (wrapper.getSplitZoneAlignment() == SplitZoneAlignmentLeft) {
            splitZone.setGravity(Gravity.LEFT);
        }

        for (int i = 0; i < splitZone.getChildCount(); i++) {
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) splitZone.getChildAt(i).getLayoutParams();
            if (alignment == SplitZoneAlignmentFill) {
                params.weight = 1;
            }
            else {
                params.width = buttonWidth;
                params.weight = 0;
            }
        }

        splitZone.requestLayout();
    }

    protected void createButtons() {
        createButtons(false);
    }

    protected void createButtons(boolean soft) {
        if (DEBUG_SPLIT) Log.d(TAG, "Split zone is " + wrapper.splitZoneEnabled);

        int itemsCreated = 0;

        ActionItem lastCreatedItem = null;

        for (int i = 0, size = actionItems.size(); i < size && usedActionButtons < maxActionButtons; i++) {
            ActionItem item = actionItems.get(i);

            if (item.isVisibleAsIcon()) {

                if (soft && item.attachedView != null) {
                    if ($.DEBUG_LAYOUT) Log.d(TAG, "Removed deleted metadata from item " + item.getName());
                    $.removeMetadata(item.attachedView, "Deleted");
                }
                else {
                    item.attachedView = createItemView(item);

                    $.metadata(item.attachedView, ActionItemMetadataKey, item.getId());

                    item.attachedView.setTag(item);

                    lastCreatedItem = item;

                    if (wrapper.splitZoneEnabled) {
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height);
                        if (wrapper.getSplitZoneAlignment() == SplitZoneAlignmentFill) {
                            params.weight = 1;
                        }
                        else {
                            params.width = buttonWidth;
                        }
                        splitZone.addView(item.attachedView, params);
                    }
                    else {
                        addView(item.attachedView);
                    }

                }
                usedActionButtons++;
                itemsCreated++;
            }
        }

        if (itemsCreated < actionItems.size()) {
            if (showOverflow) {

                createOverflowView();

                if (wrapper.splitZoneEnabled) {
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, height);
                    if (wrapper.getSplitZoneAlignment() == SplitZoneAlignmentFill) {
                        params.weight = 1;
                    }
                    else {
                        params.width = buttonWidth;
                    }
                    splitZone.addView(overflowButton, params);
                }
                else {
                    addView(overflowButton);
                }

                usedActionButtons++;

                // If adding the overflow caused the visible buttons to increase past the allowed limit, removed the most recent button
                if (usedActionButtons > maxActionButtons && lastCreatedItem != null) {
                    removeView(lastCreatedItem.attachedView);
                    lastCreatedItem.attachedView = null;
                    usedActionButtons--;
                }
            }
//            else Log.d("", "Skipping overflow because the device has a hardware menu key.");
        }
//        else Log.d("", "Skipping overflow because all the items fit into the actionbar.");

        orderButtonsAndResetParams(false);

        int visibility = usedActionButtons > suggestedActionButtons && !wrapper.splitZoneEnabled ? INVISIBLE : VISIBLE;
        if (titleContainer != null) {
            titleContainer.setVisibility(visibility);
        }
        if (title != null) {
            title.setVisibility(visibility);
        }

    }

    protected void removeButtons() {
        usedActionButtons = 0;

        for (ActionItem item : actionItems) {
            if (item.attachedView != null) {
                if (wrapper.splitZoneEnabled) {
                    splitZone.removeView(item.attachedView);
                }
                else {
                    removeView(item.attachedView);
                }
                item.attachedView = null;
            }
        }

        if (overflowButton != null) {
            if (wrapper.splitZoneEnabled) {
                splitZone.removeView(overflowButton);
            }
            else {
                removeView(overflowButton);
            }
        }

        overflowButton = null;
    }

    protected void orderButtons() {
        orderButtonsAndResetParams(true);
    }

    protected void orderButtonsAndResetParams(boolean resetParams) {
        orderButtonsAndResetParamsInContext(resetParams, overflowButton, actionItems);
    }

    public int getNumberOfVisibleActionItems() {
        return usedActionButtons;
    }

    private static class SeparatorInsertionPoint {
        View viewLeft;
        View viewRight;

        static SeparatorInsertionPoint make(View viewLeft, View viewRight) {
            SeparatorInsertionPoint point = new SeparatorInsertionPoint();
            point.viewLeft = viewLeft;
            point.viewRight = viewRight;
            return point;
        }
    }

    private static final AtomicInteger sNextGeneratedId = new AtomicInteger(1);

    public static int generateViewId() {
        for (;;) {
            final int result = sNextGeneratedId.get();
            // aapt-generated IDs have the high byte nonzero; clamp to the range under that.
            int newValue = result + 1;
            if (newValue > 0x00FFFFFF) newValue = 1; // Roll over to 1, not 0.
            if (sNextGeneratedId.compareAndSet(result, newValue)) {
                return result;
            }
        }
    }

    protected void orderButtonsAndResetParamsInContext(boolean resetParams, View overflowButton, ArrayList<ActionItem> actionItems) {
        if (wrapper.splitZoneEnabled) return; // TODO

        RelativeLayout.LayoutParams buttonParams;
        ActionItem lastDrawnItem = null;

        ArrayList<View> oldSeparators = separators;
        if (oldSeparators == null) oldSeparators = new ArrayList<View>();
        separators = new ArrayList<View>();

        ArrayList<SeparatorInsertionPoint> separatorInsertionPoints = new ArrayList<SeparatorInsertionPoint>();

        if (overflowButton != null) {
            lastDrawnItem = new ActionItem();
            lastDrawnItem.setId(OverflowID);
            lastDrawnItem.setResource(-1);
            lastDrawnItem.attachedView = overflowButton;
        }

        for (int i = actionItems.size() - 1; i >=0; i--) {
            ActionItem item = actionItems.get(i);

            if (item.attachedView != null) {

                buttonParams = (RelativeLayout.LayoutParams) item.attachedView.getLayoutParams();
                buttonParams = new LayoutParams(buttonParams.width, buttonParams.height);

                if (lastDrawnItem == null) {
                    buttonParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                }
                else {
                    boolean shouldInsertSeparator = titlesAllowed && item.isTitleVisible();
                    shouldInsertSeparator = shouldInsertSeparator || titlesAllowed && lastDrawnItem.isTitleVisible();
                    shouldInsertSeparator = shouldInsertSeparator || lastDrawnItem.getResource() == 0;
                    shouldInsertSeparator = shouldInsertSeparator || item.getResource() == 0;

                    if (shouldInsertSeparator) {
                        View separator;
                        LayoutParams params;
                        if (oldSeparators != null && oldSeparators.size() != 0) {
                            separator = oldSeparators.remove(0);
                            params = createSeparatorLayoutParams();
                            separator.setLayoutParams(params);
                        }
                        else {
                            separator = createSeparator();
                            params = (LayoutParams) separator.getLayoutParams();

                            addView(separator);
                        }

                        params.addRule(RelativeLayout.LEFT_OF, lastDrawnItem.getId());
                        params.rightMargin = caretLeftPadding;
                        int id = generateViewId();
                        separator.setId(id);
                        separators.add(separator);

                        buttonParams.addRule(RelativeLayout.LEFT_OF, id);

                    }
                    else {
                        buttonParams.addRule(RelativeLayout.LEFT_OF, lastDrawnItem.getId());
                    }
                }

                if (titlesAllowed && item.isTitleVisible() && lastDrawnItem != null) {
                    separatorInsertionPoints.add(SeparatorInsertionPoint.make(lastDrawnItem.attachedView, item.attachedView));
                }

                lastDrawnItem = item;

                item.attachedView.setLayoutParams(buttonParams);
            }
        }

        //Cleanup the remaining unused separators
        for (View separator : oldSeparators) {
            removeView(separator);
        }
    }

    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        container = null;
        backButton = null;
        caret = null;
        logo = null;
        title = null;
        done = null;
        doneTitle = null;
        overflowButton = null;

        if (overflowMenu != null) {
            overflowMenu.dismiss();
        }

        for (ActionItem item : actionItems) {
            item.attachedView = null;
            item.drawable = null;
        }

    }

    public ActionItem findItemWithId(int id) {
        for (ActionItem item : actionItems) {
            if (item.getId() == id) {
                return item;
            }
        }

        return null;
    }

    public ArrayList<ActionItem> getOverflowItems() {
        ArrayList<ActionItem> overflowItems = new ArrayList<ActionItem>();

        int processedItems = 0;
        for (ActionItem item : actionItems) {
            if (item.attachedView == null && item.isVisible()) {
                processedItems++;
                overflowItems.add(item);
            }
        }
        if (processedItems == 0) return null;

        return overflowItems;
    }

    public void showOverflow() {
        if (USE_POPOVER_MENUS) {
            wrapper.showOverflow();
            return;
        }

        Context context = getContext();

        View anchor = overflowButton;
        // TODO
        if (anchor == null || !showOverflow) anchor = this;

        overflowMenu = new PopupMenu(context, anchor);
        Menu menu = overflowMenu.getMenu();

        int processedItems = 0;
        for (ActionItem item : actionItems) {
            if (item.attachedView == null && item.isVisible()) {
                processedItems++;
                MenuItem menuItem = menu.add(Menu.NONE, item.getId(), Menu.NONE, item.getName());
                menuItem.setEnabled(item.isEnabled());
            }
        }

        if (processedItems == 0) {
            overflowMenu.dismiss();
            overflowMenu = null;
            return;
        }

        overflowMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                wrapper.getListener().onLegacyActionSelected(findItemWithId(menuItem.getItemId()));
                return true;
            }
        });

        overflowMenu.setOnDismissListener(new PopupMenu.OnDismissListener() {
            @Override
            public void onDismiss(PopupMenu popupMenu) {
                overflowMenu = null;
            }
        });

        overflowMenu.show();
    }

    public ViewGroup createBackButton() {
        Context context = getContext();

        backButton = new LinearLayout(context, null, android.R.attr.borderlessButtonStyle);
        backButton.setId(BackID);
        backButton.setPadding(0, 0, 0, 0);

        prepareBackButton();

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
        backButton.setLayoutParams(params);

        backButton.setBackground(obtainRipple());

        return backButton;
    }

    public void prepareBackButton() {
        Context context = getContext();

        backButton.removeAllViews();

        if (wrapper.backButtonMode == CaretBackMode) {

            caret = new ImageView(context);
            caret.setId(CaretID);
            caret.setImageDrawable(wrapper.caretResource == 0 ? null : getResources().getDrawable(wrapper.caretResource));
            caret.setScaleType(ImageView.ScaleType.CENTER);
            caret.setPadding(caretLeftPadding, caret.getPaddingTop(), caretRightPadding, caret.getPaddingBottom());
            caret.setMinimumWidth(primaryKeyline);
            backButton.addView(caret, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);

            logo = new ImageView(context);
            logo.setId(LogoID);
            if (wrapper.logoDrawable == null) logo.setImageDrawable(wrapper.logoResource == 0 ? null : getResources().getDrawable(wrapper.logoResource));
            else logo.setImageDrawable(wrapper.logoDrawable);
            logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            backButton.addView(logo, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);

            title = new TextView(context);
            title.setTextColor(wrapper.textColor);
            title.setTextSize(DefaultTextSize);
            title.setId(TitleID);
            title.setGravity(Gravity.CENTER);
            if (wrapper.titleString == null && wrapper.titleResource != 0) title.setText(getResources().getString(wrapper.titleResource));
            else title.setText(wrapper.titleString);
            backButton.addView(title, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);

            if (!wrapper.backButtonEnabled) {
                caret.setVisibility(INVISIBLE);
                backButton.setEnabled(false);
            }

            done = null;
        }
        else {
            if (!wrapper.backButtonEnabled && wrapper.logoResource == 0 && TextUtils.isEmpty(wrapper.titleString) && wrapper.titleResource == 0) {
                backButton.setPadding(0, 0, 0, 0);
                return;
            }
            caret = null;
            logo = null;

            done = new ImageView(context);
            done.setId(DoneID);
            done.setImageDrawable(wrapper.doneResource == 0 ? null : getResources().getDrawable(wrapper.doneResource));
            done.setScaleType(ImageView.ScaleType.CENTER);
            done.setPadding(0, 0, 0, 0);
            backButton.addView(done, secondaryKeyline - donePadding, ViewGroup.LayoutParams.MATCH_PARENT);

            if (getResources().getConfiguration().smallestScreenWidthDp >= 600 && wrapper.doneSeparatorVisible) {
                doneTitle = new TextView(context);
                doneTitle.setTextColor(wrapper.textColor);
                doneTitle.setTextSize(DefaultDoneTextSize);
                doneTitle.setAllCaps(true);
                doneTitle.setGravity(Gravity.CENTER);
                doneTitle.setText(Utils.DoneLabel);
                doneTitle.setId(DoneTitleID);
                doneTitle.setPadding(caretRightPadding, 0, 2 * donePadding, 0);
                backButton.addView(doneTitle, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            }
            else {
                doneTitle = null;
            }

            if (!wrapper.backButtonEnabled) {
                backButton.setAlpha(0.5f);
                backButton.setEnabled(false);
            }
        }

    }

    public void applyVerboseTitleTo(TextView target) {
        CharSequence titleText = wrapper.titleResource != 0 ? getResources().getString(wrapper.titleResource) : wrapper.titleString;
        String subtitleText = wrapper.subtitleResource != 0 ? getResources().getString(wrapper.subtitleResource) : wrapper.subtitleString;

        if (titleText == null) titleText = "";
        if (subtitleText == null) subtitleText = "";

        SpannableStringBuilder span = new SpannableStringBuilder();
        span.append(titleText);
        if (!TextUtils.isEmpty(subtitleText)) {
            span.append("\n");
            span.append(subtitleText);
            span.setSpan(new AbsoluteSizeSpan((int) DefaultSubtitleSize, true), titleText.length(), span.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
            span.setSpan(new ForegroundColorSpan(
                    Color.argb(Color.alpha(wrapper.textColor) / 2,
                            Color.red(wrapper.textColor),
                            Color.green(wrapper.textColor),
                            Color.blue(wrapper.textColor))),
                    titleText.length(), span.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }

        target.setText(span);
    }

    protected LayoutParams createSeparatorLayoutParams() {
        int separatorWidth = (int) metrics.density;
        if (separatorWidth < 1) separatorWidth = 1;
        if (!wrapper.fillContainer)
            return new LayoutParams(separatorWidth, height - (int) (SeparatorPaddingDP * 2 * metrics.density));
        else {
            LayoutParams params = new LayoutParams(separatorWidth, ViewGroup.LayoutParams.MATCH_PARENT);
            params.topMargin = (int) (SeparatorPaddingDP * metrics.density);
            params.bottomMargin = params.topMargin;
            return params;
        }
    }

    protected LinearLayout.LayoutParams createSeparatorLinearLayoutParams() {
        int separatorWidth = (int) metrics.density;
        if (separatorWidth < 1) separatorWidth = 1;
        return new LinearLayout.LayoutParams(separatorWidth, height - (int) (SeparatorPaddingDP * 2 * metrics.density));
    }

    final static int SeparatorModeRelative = 0;
    final static int SeparatorModeLinear = 0;

    protected View createSeparator() {
        return createSeparator(SeparatorModeRelative);
    }

    protected View createSeparator(int mode) {
        View separator = new View(getContext());
        if (mode == SeparatorModeRelative) {
            LayoutParams params = createSeparatorLayoutParams();
            separator.setBackgroundColor(wrapper.getInnerSeparatorColor());
            //        params.addRule(CENTER_VERTICAL);
            params.topMargin = (int) (SeparatorPaddingDP * metrics.density);
            separator.setLayoutParams(params);
        }
        else {
            LinearLayout.LayoutParams params = createSeparatorLinearLayoutParams();
            separator.setBackgroundColor(wrapper.getInnerSeparatorColor());
            //        params.addRule(CENTER_VERTICAL);
            params.topMargin = (int) (SeparatorPaddingDP * metrics.density);
            separator.setLayoutParams(params);
        }
        return separator;
    }

    public void prepareTitle() {
        if (wrapper.backButtonMode == CaretBackMode) {
            if (titleContainer != null) removeView(titleContainer);
            backupTitle = null;
            titleContainer = null;
            return;
        }

        if (titleContainer == null) {
            titleContainer = new RelativeLayout(getContext());
            RelativeLayout.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
            params.addRule(RIGHT_OF, BackID);
            titleContainer.setLayoutParams(params);

            View doneSeparator = createSeparator();
            params = (LayoutParams) doneSeparator.getLayoutParams();
            params.addRule(ALIGN_PARENT_LEFT);
            params.rightMargin = donePadding;
            doneSeparator.setId(DoneSeparatorID);
            titleContainer.addView(doneSeparator);

            if (!wrapper.doneSeparatorVisible) doneSeparator.setVisibility(INVISIBLE);

            addView(titleContainer);
        }

        if (title != null) {
            if (title.getParent() != null) ((ViewGroup) title.getParent()).removeView(title);
        }

        title = new TextView(getContext());
        title.setTextSize(DefaultTextSize);
        title.setTextColor(wrapper.textColor);
        title.setId(TitleID);
        title.setGravity(Gravity.CENTER_VERTICAL);

        RelativeLayout.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
        params.addRule(RIGHT_OF, DoneSeparatorID);
        params.addRule(CENTER_VERTICAL);

        title.setLayoutParams(params);

        titleContainer.addView(title);

        backupTitle = new TextView(getContext());
        backupTitle.setTextSize(DefaultTextSize);
        backupTitle.setTextColor(wrapper.textColor);
        backupTitle.setGravity(Gravity.CENTER_VERTICAL);

        params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
        params.addRule(RIGHT_OF, DoneSeparatorID);
        params.addRule(CENTER_VERTICAL);

        backupTitle.setLayoutParams(params);

        titleContainer.addView(backupTitle);
        backupTitle.setAlpha(0f);

        applyVerboseTitleTo(title);

    }

    public void prepareActionTextView(TextView actionTextView) {
        actionTextView.setTextColor(wrapper.textColor);
        actionTextView.setTextSize(DefaultActionTextSize);
        actionTextView.setTypeface(Typeface.DEFAULT_BOLD);
        actionTextView.setAllCaps(true);
        actionTextView.setGravity(Gravity.CENTER);
    }

    public View createOverflowView() {
        Context context = getContext();

        overflowButton = new ImageView(context, null, android.R.attr.borderlessButtonStyle);
        overflowButton.setId(OverflowID);
        overflowButton.setScaleType(ImageView.ScaleType.CENTER);
        overflowButton.setImageDrawable(wrapper.overflowResource == 0 ? null : getResources().getDrawable(wrapper.overflowResource));

        $.metadata(overflowButton, ActionItemMetadataKey, OverflowID);
        overflowButton.setBackground(Utils.getDeselectedColors(getContext()).setShape(LegacyRippleDrawable.ShapeCircle));

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(buttonWidth, height);
        params.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
        overflowButton.setLayoutParams(params);

        overflowButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showOverflow();
            }
        });

        return overflowButton;
    }

    private View.OnLongClickListener hintLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            wrapper.hintItem(((ActionItem) v.getTag()));
//            Rect rect = new Rect();
//            v.getGlobalVisibleRect(rect);
//            Toast toast = Toast.makeText(getContext(), ((ActionItem) v.getTag()).getName(), Toast.LENGTH_SHORT);
//            toast.setGravity(Gravity.TOP|Gravity.LEFT, rect.left, rect.top + rect.height()/2);
//            toast.show();
            return true;
        }
    };

    private View.OnLongClickListener tabHintLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View v) {
            wrapper.hintNavigationElement((LegacyNavigationElement) v.getTag());
//            Rect rect = new Rect();
//            v.getGlobalVisibleRect(rect);
//            Toast toast = Toast.makeText(getContext(), ((LegacyNavigationElement) v.getTag()).name, Toast.LENGTH_SHORT);
//            toast.setGravity(Gravity.TOP|Gravity.LEFT, rect.left, rect.top + rect.height()/2);
//            toast.show();
            return true;
        }
    };

    public View createItemView(ActionItem item) {
        Context context = getContext();

        if (item.isSpinner()) {
            final LegacyActionBar.SpinnerItem Item = (LegacyActionBar.SpinnerItem) item;

            Spinner itemView = new Spinner(context);
            itemView.setBackgroundResource(Utils.Spinner);
            if (Build.VERSION.SDK_INT >= 16) {
                itemView.setPopupBackgroundResource(wrapper.spinnerResource);
            }

            //noinspection unchecked
            itemView.setAdapter(new ArrayAdapter(context, Utils.SpinnerItemLayout, ((LegacyActionBar.SpinnerItem) item).getDataSet()) {
                public TextView getView(int position, View convertView, ViewGroup parent) {
                    TextView v = (TextView) super.getView(position, convertView, parent);
                    v.setTextSize(12);
                    v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
                    v.setAllCaps(true);
                    v.setTextColor(wrapper.textColor);
                    return v;
                }

                public TextView getDropDownView(int position, View convertView, ViewGroup parent) {
                    TextView v = (TextView) super.getView(position, convertView, parent);
                    if (position == Item.getSelection())
                        v.setBackground(Utils.getSelectedColors(getContext()));
                    else
                        v.setBackground(Utils.getDeselectedColors(getContext()));
                    return v;
                }
            });
            itemView.setSelection(Item.getSelection());

            itemView.setOnItemSelectedListener(Item.getProxyListener());

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
            itemView.setPadding(itemView.getPaddingLeft(), itemView.getPaddingTop(), (int) (itemView.getPaddingRight() - 16 * metrics.density), itemView.getPaddingBottom());
            params.rightMargin = (int) (4 * metrics.density);
            params.leftMargin = (int) (4 * metrics.density);
            itemView.setLayoutParams(params);

            itemView.setEnabled(item.isEnabled());
            itemView.setAlpha(item.isEnabled() ? 1f : 0.5f);
            if (item.isEnabled()) {
                itemView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            else {
                itemView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            return itemView;
        }

        if (item.getResource() == 0 || (titlesAllowed && item.isTitleVisible())) {
            TextView itemView = new TextView(context, null, android.R.attr.borderlessButtonStyle);
            itemView.setId(item.getId());
            prepareActionTextView(itemView);
            itemView.setText(item.getName());
            itemView.setMinWidth(buttonWidth);
            if (item.drawable == null)
                itemView.setCompoundDrawablesWithIntrinsicBounds(item.getResource(), 0, 0, 0);
            else
                itemView.setCompoundDrawablesWithIntrinsicBounds(item.drawable, null, null, null);
            itemView.setPadding(item.getResource() == 0 ? donePadding : donePadding / 2, 0,
                    donePadding, 0);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, height);
            itemView.setLayoutParams(params);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    wrapper.getListener().onLegacyActionSelected((ActionItem) view.getTag());
                }
            });

            itemView.setBackground(obtainRipple());

            itemView.setEnabled(item.isEnabled());
            itemView.setAlpha(item.isEnabled() ? 1f : 0.5f);
            if (item.isEnabled()) {
                itemView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            else {
                itemView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            return itemView;
        }
        else {
            ImageView itemView = new ImageView(context, null, android.R.attr.borderlessButtonStyle);
            itemView.setId(item.getId());
            itemView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            itemView.setImageDrawable(item.getResource() == 0 ? item.drawable : getResources().getDrawable(item.getResource()));

            itemView.setOnLongClickListener(hintLongClickListener);

            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(buttonWidth, height);
            itemView.setLayoutParams(params);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    wrapper.getListener().onLegacyActionSelected((ActionItem) view.getTag());
                }
            });

            itemView.setBackground(obtainRipple());

            itemView.setEnabled(item.isEnabled());
            itemView.setAlpha(item.isEnabled() ? 1f : 0.5f);
            if (item.isEnabled()) {
                itemView.setLayerType(View.LAYER_TYPE_NONE, null);
            }
            else {
                itemView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            }

            return itemView;
        }
    }

    public View findViewWithId(int id, boolean searchInSplitZone) {
        if (searchInSplitZone) {
            View view = findViewById(id);

            if (view == null && splitZone != null) return splitZone.findViewById(id);

            return view;
        }
        return findViewById(id);
    }

    public View createNavigationTab(final LegacyNavigationElement element) {
        FrameLayout tab = new FrameLayout(getContext(), null, android.R.attr.borderlessButtonStyle);
        tab.setPadding(0, 0, 0, 0);

        TextView tabText = new TextView(getContext());
        prepareActionTextView(tabText);
        tabText.setTextColor(wrapper.textColor);
        if (element.resource == 0) tabText.setText(element.name);
        else tabText.setCompoundDrawablesWithIntrinsicBounds(element.resource, 0, 0, 0); // TODO

        if (((landscape || tablet) && wrapper.navigationMode != InlineTabsNavigationMode) && element.resource != 0) {
            tabText.setText(element.name);
        }

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.gravity = Gravity.CENTER;

        tab.addView(tabText, params);

        View selector = new View(getContext());
        selector.setBackgroundColor(getResources().getColor(wrapper.selectorColor));
        params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, selectorThickness);
        params.gravity = Gravity.BOTTOM;

        if (element != wrapper.selectedNavigationElement) selector.setVisibility(INVISIBLE);

        tab.addView(selector, params);

        element.attachedView = tab;
        element.text = tabText;
        element.selector = selector;

        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        tabParams.weight = 1;
        tab.setLayoutParams(tabParams);

        tab.setId(element.id);
        tab.setTag(element);
        tab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                wrapper.setSelectedNavigationElement(element);
            }
        });
        if (TextUtils.isEmpty(tabText.getText())) { // TODO
            tab.setOnLongClickListener(tabHintLongClickListener);
        }

        tab.setBackground(obtainRipple());

        return tab;
    }

    public void createNavigationElements() {

        if (wrapper.navigationMode == NoNavigationMode) return;

        if (wrapper.navigationMode == TabsNavigationMode || wrapper.navigationMode == InlineTabsNavigationMode) {
            navigationContainer = new LinearLayout(getContext());
            navigationContainer.setClipChildren(false);
            navigationContainer.setOrientation(LinearLayout.HORIZONTAL);

            if (!landscape && !tablet && wrapper.navigationMode == TabsNavigationMode) {
                LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
                params.addRule(ALIGN_PARENT_BOTTOM);

                navigationContainer.setLayoutParams(params);
            }
            else {
                LayoutParams params = new LayoutParams(wrapper.navigationElements.size() * ((int) getMaximumTextSize() + donePadding * 4), height);
                if (wrapper.navigationMode == TabsNavigationMode) params.addRule(RIGHT_OF, BackID);
                if (wrapper.navigationMode == InlineTabsNavigationMode) {
                    if (wrapper.navigationGravity == Gravity.RIGHT) {
                        params.addRule(ALIGN_PARENT_RIGHT);
                    }
                    else {
                        params.addRule(CENTER_HORIZONTAL);
                    }
                }

                if (wrapper.navigationMode == TabsNavigationMode) navigationContainer.setPadding(donePadding, 0, 0, 0);

                navigationContainer.setLayoutParams(params);
            }

            ArrayList<LegacyNavigationElement> navigationElements = wrapper.navigationElements;
            for (int i = 0, navigationElementsSize = navigationElements.size(); i < navigationElementsSize; i++) {
                LegacyNavigationElement element = navigationElements.get(i);

                navigationContainer.addView(createNavigationTab(element));
//                if (i != navigationElementsSize - 1) {
//                    View separator = createSeparator(SeparatorModeLinear);
//                    navigationSeparators.add(separator);
//                    navigationContainer.addView(separator);
//                }
            }

            super.addView(navigationContainer);
        }

        if (wrapper.navigationMode == SpinnerNavigationMode) {
            navigationSpinner = new Spinner(getContext());

            RelativeLayout.LayoutParams params = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (wrapper.backButtonMode == CaretBackMode) {
                params.addRule(RIGHT_OF, BackID);
            }
            else {
                params.addRule(RIGHT_OF, DoneSeparatorID);
            }
            navigationSpinner.setLayoutParams(params);
            navigationSpinner.setAdapter(new NavigationAdapter());
            int index = wrapper.navigationElements.indexOf(wrapper.selectedNavigationElement);
            navigationSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                    wrapper.setSelectedNavigationElement((LegacyNavigationElement) adapterView.getAdapter().getItem(i));
                    ((BaseAdapter) adapterView.getAdapter()).notifyDataSetChanged();
                }

                @Override
                public void onNothingSelected(AdapterView<?> adapterView) {
                }
            });
            if (index != -1) navigationSpinner.setSelection(index);

            addView(navigationSpinner);
        }

    }

    protected float getMaximumTextSize() {
        float maximumTextSize = 0;
        float currentMeasure;

        TextMeasure.setTextSize(DefaultActionTextSize * metrics.scaledDensity);

        for (LegacyNavigationElement element : wrapper.navigationElements) {
            if (wrapper.navigationMode == TabsNavigationMode) {
                if (element.resource == 0) currentMeasure = TextMeasure.measureText(element.name.toUpperCase(Locale.getDefault()));
                else currentMeasure = buttonWidth - donePadding * 4;
            }
            else {
                currentMeasure = 0;
                if (element.resource == 0 || landscape || tablet) currentMeasure += TextMeasure.measureText(element.name.toUpperCase(Locale.getDefault()));
                if (element.resource != 0) {
                    currentMeasure += buttonWidth - donePadding * 4;
                    if (landscape || tablet) currentMeasure -= donePadding * 2;
                }
            }
            if (currentMeasure > maximumTextSize) {
                maximumTextSize = currentMeasure;
            }
        }

        return maximumTextSize;
    }

    // TODO
    public void onItemChanged(ActionItem item) {
        if (item.attachedView != null) {
            if (item.isSpinner()) {
                Spinner itemView = (Spinner) item.attachedView;
                ((ArrayAdapter) itemView.getAdapter()).notifyDataSetChanged();
                return;
            }
            if (item.getResource() == 0 || (titlesAllowed && item.isTitleVisible())) {
                TextView itemView = (TextView) item.attachedView;
                itemView.setId(item.getId());
                prepareActionTextView(itemView);
                itemView.setText(item.getName());
                itemView.setMinWidth(buttonWidth);
                itemView.setCompoundDrawablesWithIntrinsicBounds(item.getResource(), 0, 0, 0);
                itemView.setPadding(item.getResource() == 0 ? donePadding / 2 : 0, 0,
                        donePadding, 0);
            }
            else {
                ImageView itemView = (ImageView) item.attachedView;
                itemView.setId(item.getId());
                itemView.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                itemView.setImageDrawable(item.getResource() == 0 ? null : getResources().getDrawable(item.getResource()));
            }

        }
    }

    public void onItemAdded(ActionItem item) {

        if (getWidth() == 0) {
            // called pre-layout
            removeButtons();
            createButtons();

            return;
        }

        final SparseArray<Map> ItemMetadata = new SparseArray<Map>();

        $ actionItems = $.find(this, "." + ActionItemMetadataKey).finish("LayoutQueue").storeProperties($.X).each(new $.Each() {
            @Override
            public void run(View view, int index) {
                if ($.DEBUG_QUERY) Log.d(TAG, "View className: " + view.getClass().getName() + " has ActionItemMetadataKey metadata: " + $.metadata(view, ActionItemMetadataKey).toString());
                ItemMetadata.put((Integer) $.metadata(view, ActionItemMetadataKey), $.dumpMetadata(view));
            }
        });

        if ($.DEBUG_QUERY) Log.d(TAG, actionItems.length() + "");

        removeButtons();
        createButtons();

        $.find(this, "." + ActionItemMetadataKey).each(new $.Each() {
            @Override
            public void run(View view, int index) {
                Map metadata = ItemMetadata.get((Integer) $.metadata(view, ActionItemMetadataKey));

                if (metadata != null) {
                    $.loadMetadata(view, metadata);
                    $.metadata(view, "Pending", "true");

                    if ($.DEBUG_QUERY) Log.d(TAG, "Metadata Loaded!");
                } else {
                    view.setAlpha(0f);
                    view.setScaleX(0f);
                    view.setScaleY(0f);

                    view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                            .withLayer()
                            .setInterpolator(new Utils.FrictionInterpolator(1.5f))
                            .setDuration(200).setStartDelay(0).start();
                }
            }
        }).filter(".Pending").removeMetadata("Pending")
                .delayForLayout("LayoutQueue")
                .animate()
                    .property($.TranslateX, new $.Getter<Float>() {
                        @Override
                        public Float get(View view) {
                            return (Float) $.metadata(view, $.X) - view.getLeft();
                        }
                    }, new $.Getter<Float>() {
                        @Override
                        public Float get(View view) {
                            return 0f;
                        }
                    })
                    .duration(200)
                .start("LayoutQueue");

    }

    public void onItemRemoved(ActionItem item) {
//        if (item.attachedView != null) {
//            removeView(item.attachedView);
            item.attachedView = null;
//        }

        // Remove and recreate buttons
        // TODO inefficient

        $.find(this, "." + ActionItemMetadataKey).finish("LayoutQueue").storeProperties($.X).metadata("Deleted", "true").metadata("Old", "true");
        if ($.DEBUG_LAYOUT) Log.d(TAG, "Deleted has " + $.find(this, ".Deleted").length());

//        removeButtons();
        createButtons(true);


        $.find(this, ".Deleted").remove();

        $.find(this, "." + ActionItemMetadataKey).not(".Deleted").filter(".Old").removeMetadata("Old")
                .delayForLayout("LayoutQueue")
                .animate()
                    .property($.TranslateX, new $.Getter<Float>() {
                        @Override
                        public Float get(View view) {
                            return (Float) $.metadata(view, $.X) - view.getLeft();
                        }
                    }, new $.Getter<Float>() {
                        @Override
                        public Float get(View view) {
                            return 0f;
                        }
                    })
                    .duration(200)
                .start("LayoutQueue");

    }

    protected void setActionItems(ArrayList<ActionItem> items) {
        actionItems = items;

        onAttach((Activity) getContext());
        onCreateView();
    }

    private int disableInteractionRequests = 0;

    public void requestDisableInteractions() {
        disableInteractionRequests++;
    }

    public void requestEnableInteractions() {
        disableInteractionRequests--;
    }

    public void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);

        View firstActionItem = null;
        for (ActionItem item : actionItems) {
            if (item.attachedView != null) {
                firstActionItem = item.attachedView;
                break;
            }
        }
        if (firstActionItem == null) firstActionItem = overflowButton;

        if (titleContainer != null && firstActionItem != null && splitZone == null) {
            if (titleContainer.getRight() > firstActionItem.getLeft()) {
                titleContainer.setVisibility(INVISIBLE);
            }
        }
    }

    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (disableInteractionRequests == 0) {
            return super.onInterceptTouchEvent(event);
        }
        else {
            return true;
        }
    }

    public LegacyActionBarView(Context context, LegacyActionBar listener, LegacyActionBar.ActionBarWrapper wrapper) {
        super(context);
        this.listener = listener;
        this.wrapper = wrapper;
        setClickable(true);

        setClipChildren(false);
    }

    public LegacyActionBarView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LegacyActionBarView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }
}
