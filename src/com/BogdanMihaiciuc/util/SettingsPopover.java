package com.BogdanMihaiciuc.util;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.receipt.R;
import com.BogdanMihaiciuc.receipt.Receipt;

import java.util.ArrayList;
import java.util.List;

public class SettingsPopover extends CollectionPopover {

    final static String TAG = SettingsPopover.class.getName();
    final static boolean DEBUG_CONTROLLER = false;
    final static boolean DEBUG_COLLECTION = true;

    final static String ExpandedListMetadataKey = "ExpandedList";

    public class Setting<T> {
        final String key;
        private int titleResource;
        private int descriptionResource;
        private String title;
        private String description;
        private T defaultValue;

        protected Setting(String key) {
            this.key = key;
        }

        public void setTitle(int title) {
            this.titleResource = title;
        }

        public void setTitle(String title) {
            this.title = title;
            titleResource = 0;
        }

        public void setDescription(int description) {
            this.descriptionResource = description;
        }

        public void setDescription(String description) {
            this.description = description;
            descriptionResource = 0;
        }

        public String getTitle() {
            return titleResource != 0 ? getString(titleResource) : title;
        }

        public String getDescription() {
            return descriptionResource != 0 ? getString(descriptionResource) : description;
        }

        public T getValue() {
            return getValue(null);
        }

        public T getValue(T defaultValue) {
            throw new UnsupportedOperationException("Unable to get preference of this type.");
        }

        public void setValue(T value) {
            throw new UnsupportedOperationException("Unable to set preference of this type.");
        }

        public Setting setDefaultValue(T defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public T getDefaultValue() {
            return defaultValue;
        }
    }

    public class InfoSetting extends Setting<Void> {
        protected InfoSetting(String key) {
            super(key);
        }
    }

    public class HeaderSetting extends Setting<Void> {
        protected HeaderSetting(String key) {
            super(key);
        }
    }

    public class IntSetting extends Setting<Integer> {

        protected IntSetting(String key) {
            super(key);
        }

        public Integer getValue(Integer defaultValue) {
            defaultValue = defaultValue == null ? 0 : defaultValue;
            return prefs.getInt(key, defaultValue);
        }

        public void setValue(Integer value) {
            prefs.edit().putInt(key, value).apply();
        }
    }

    public class BooleanSetting extends Setting<Boolean> {

        protected BooleanSetting(String key) {
            super(key);
        }

        public Boolean getValue(Boolean defaultValue) {
            defaultValue = defaultValue == null ? false : defaultValue;
            return prefs.getBoolean(key, defaultValue);
        }

        public void setValue(Boolean value) {
            prefs.edit().putBoolean(key, value).apply();
        }
    }

    public class StringSetting extends Setting<String> {

        protected StringSetting(String key) {
            super(key);
        }

        public String getValue(String defaultValue) {
            return prefs.getString(key, defaultValue);
        }

        public void setValue(String value) {
            prefs.edit().putString(key, value).apply();
        }
    }

    public class ListSetting extends StringSetting {

        List<String> values = new ArrayList<String>();
        List<String> descriptions = new ArrayList<String>();
        List<String> readableValues = new ArrayList<String>();

        private String value;
        private String description;
        private String preselection;

        public void setValue(String value) {
            int index = -1;
            if ((index = values.indexOf(value)) != -1) {
                super.setValue(value);
                super.setTitle(readableValues.get(index));
                super.setDescription(descriptions.get(index));
            }
        }

        protected ListSetting(String key) {
            super(key);
        }
    }

    final static int DescriptionType = 0;
    final static int CheckboxType = 1;
    final static int EditTextType = 2; // TODO
    final static int SearchableListType = 3;
    final static int HeaderType = 4;
    final static int ListEntryType = 5;

    final static int TitleID = LegacyActionBarView.generateViewId();
    final static int CheckboxID = LegacyActionBarView.generateViewId();
    final static int IconID = LegacyActionBarView.generateViewId();
    final static int ValueID = LegacyActionBarView.generateViewId();

    private ArrayList<Setting> settings = new ArrayList<Setting>();
    private SettingsController controller = new SettingsController();

    private LegacyActionBar.ContextBarWrapper searchWrapper;

    private FilterTask currentFilter;

    public SettingsPopover(AnchorProvider anchor, SharedPreferences prefs) {
        super(anchor, null);
        super.setController(controller);
        this.prefs = prefs;
    }

    private class SettingsController extends CollectionViewController {

        protected void onAttachedToCollectionView(CollectionView view) {
            view.setInsertAnimator(new CollectionView.ReversibleAnimation() {
                @Override
                public void playAnimation(View view, Object object, int viewType) {
                    view.setAlpha(0f);
                    view.setScaleX(0.99f);
                    view.animate().alpha(1f).scaleX(1f);
                }

                @Override
                public void resetState(View view, Object object, int viewType) {

                }
            });

        }

        public View createEmptyView(ViewGroup container, LayoutInflater inflater) {
            View view = inflater.inflate(R.layout.layout_empty, container, false);
            ((TextView) view.findViewById(R.id.EmptyText)).setTypeface(Receipt.condensedTypeface());
            ((TextView) view.findViewById(R.id.EmptyText)).setText(getResources().getString(R.string.NoResults));
            ((RelativeLayout.LayoutParams) view.findViewById(R.id.EmptyText).getLayoutParams()).addRule(RelativeLayout.CENTER_IN_PARENT);
            view.findViewById(R.id.EmptyImage).setVisibility(ViewGroup.GONE);
            view.getLayoutParams().height = (int) (container.getResources().getDisplayMetrics().density * 128 + 0.5f);
            return view;
        }

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            Context context = container.getContext();

            // Specialization
            int primaryKeyline = context.getResources().getDimensionPixelSize(Utils.PrimaryKeyline);
            int secondaryKeyline = context.getResources().getDimensionPixelSize(Utils.SecondaryKeyline);
            int lineHeight = context.getResources().getDimensionPixelSize(Utils.LineHeight);


            FrameLayout settingRoot = new FrameLayout(context);
            ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, lineHeight);

            settingRoot.setLayoutParams(params);

            if (viewType != EditTextType) {
                TextView label = new TextView(context);
                label.setTextSize(16);
                label.setTextColor(getResources().getColor(Utils.DashboardText));
                label.setGravity(Gravity.CENTER_VERTICAL);
                label.setId(TitleID);

                FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, lineHeight);
                labelParams.leftMargin = secondaryKeyline;

                if (viewType == HeaderType || viewType == DescriptionType) {
                    labelParams.leftMargin = primaryKeyline;
                }
                else {
                    settingRoot.setBackground(Utils.getDeselectedColors(container.getContext()));
                }

                settingRoot.addView(label, labelParams);

                if (viewType == HeaderType) {
                    label.setTextColor(getResources().getColor(Utils.DashboardTitle));
                }
            }

            if (viewType == CheckboxType) {
                CheckBox check = new CheckBox(context) {
                    // This checkbox is unable to receive touch events
                    public boolean onTouchEvent(MotionEvent event) {
                        return false;
                    }
                };
                check.setId(CheckboxID);

                FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(secondaryKeyline, lineHeight);
                labelParams.leftMargin = primaryKeyline;
                settingRoot.addView(check, labelParams);

            }

            if (viewType == SearchableListType || viewType == ListEntryType) {
                TextView text = new TextView(context);
                text.setTextSize(14);
                text.setAllCaps(true);
                text.setTextColor(getResources().getColor(Utils.DashboardTitle));
                text.setGravity(Gravity.CENTER);
                text.setId(ValueID);

                FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(secondaryKeyline, lineHeight);
                settingRoot.addView(text, labelParams);
            }

            if (viewType == HeaderType) {

                View separator = new View(context);
                separator.setBackgroundColor(container.getResources().getColor(Utils.DashboardSeparator));

                settingRoot.addView(separator, ViewGroup.LayoutParams.MATCH_PARENT, (int) (context.getResources().getDisplayMetrics().density + 0.5f));
            }

            return settingRoot;
        }

        @Override
        public void configureView(View view, final Object Setting, int viewType) {
            TextView title = (TextView) view.findViewById(TitleID);

            if (viewType == ListEntryType) {
                TextView value = (TextView) view.findViewById(ValueID);
                ListSetting setting = (ListSetting) findMetadata(ExpandedListMetadataKey);
                int index = setting.values.indexOf(Setting);
                value.setText(setting.readableValues.get(index));
                title.setText(setting.descriptions.get(index));
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        collapseSetting(view);
                    }
                });

                if (Setting == ((ListSetting) findMetadata(ExpandedListMetadataKey)).getValue() || Setting == ((ListSetting) findMetadata(ExpandedListMetadataKey)).preselection) {
                    view.setBackground(Utils.getSelectedColors(view.getContext()));
                }
                else {
                    view.setBackground(Utils.getDeselectedColors(view.getContext()));
                }

                return;
            }

            if (viewType == CheckboxType) {
                CheckBox check = (CheckBox) view.findViewById(CheckboxID);

                check.setChecked(((BooleanSetting) Setting).getValue());
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        BooleanSetting setting = (BooleanSetting) Setting;
                        boolean value = !setting.getValue(setting.getDefaultValue());
                        setting.setValue(value);
                        ((CheckBox) view.findViewById(CheckboxID)).setChecked(value);
                    }
                });

                view.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        final TooltipPopover popover = new TooltipPopover(((BooleanSetting) Setting).getTitle(), ((BooleanSetting) Setting).getDescription(), new AnchorProvider() {
                            @Override
                            public View getAnchor(Popover popover) {
                                return collectionView.getViewForObject(Setting);
                            }
                        });
                        popover.show(getActivity(), TooltipPopover.TimeoutDefault * 2);
//                        Rect rect = new Rect();
//                        view.getGlobalVisibleRect(rect);
//                        Toast toast = Toast.makeText(view.getContext(), ((BooleanSetting) Setting).getDescription(), Toast.LENGTH_SHORT);
//                        toast.setGravity(Gravity.TOP|Gravity.LEFT, rect.left, rect.top + rect.height()/2);
//                        toast.show();
                        return true;
                    }
                });
            }

            if (viewType == SearchableListType) {
                TextView value = (TextView) view.findViewById(ValueID);
                value.setText(((ListSetting) Setting).getTitle());
                title.setText(((ListSetting) Setting).getDescription());
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        expandSetting((ListSetting) Setting);
                    }
                });
            }
            else {
                title.setText(((Setting) Setting).getTitle());
            }
        }

        int count = 0;
        public CollectionViewController requestBeginTransaction() {
            super.requestBeginTransaction();
            count ++;
            if (DEBUG_CONTROLLER) Log.e(TAG, "Transaction " + count + " has begun!");

            return this;
        }
        public CollectionViewController requestBeginNewDataSetTransaction() {
            super.requestBeginNewDataSetTransaction();
            count ++;
            if (DEBUG_CONTROLLER) Log.e(TAG, "Transaction " + count + " has begun!");

            return this;
        }
        public void requestCompleteTransaction() {
            super.requestCompleteTransaction();
            if (DEBUG_CONTROLLER) Log.e(TAG, "Transaction " + count + " has ended!");
            count --;
        }

    }

    final static int SearchDelayMS = 200;
    private final Handler SearchHandler = new Handler();

    private class FilterTask extends AsyncTask<String, Void, ArrayList<String>> {

        ListSetting setting;
        String keyword;
        public FilterTask() {
            setting = (ListSetting) controller.findMetadata(ExpandedListMetadataKey);
        }

        @Override
        protected ArrayList<String> doInBackground(String... strings) {
            String keyword = strings[0].toLowerCase().trim();
            this.keyword = keyword;
            ArrayList<String> results = new ArrayList<String>();

            for (int i = 0; i < setting.values.size(); i++) {
                if (isCancelled()) return null;
                if (setting.descriptions.get(i).toLowerCase().startsWith(keyword) || setting.readableValues.get(i).toLowerCase().startsWith(keyword)) {
                    results.add(setting.values.get(i));
                }
            }

            return results;
        }

        protected void onPostExecute(ArrayList<String> result) {
            if (controller.findMetadata(ExpandedListMetadataKey) == setting) {
                if (DEBUG_CONTROLLER) Log.e(TAG, "Applying result set for: " + keyword);
                if (controller.findSectionWithTag(ExpandedListMetadataKey).getSize() == result.size() && result.size() == 0) return;
                controller.requestBeginTransaction();

                controller.findSectionWithTag(ExpandedListMetadataKey).clear().addAllObjects(result);
                String previousPreselection = setting.preselection;
                if (result.size() > 0) {
                    setting.preselection = result.get(0);
                }
                else {
                    setting.preselection = null;
                }
                CollectionView collection = controller.getCollectionView();
                if (collection != null) {
                    collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeTop);
                    if (previousPreselection != setting.preselection) {
                        View view = collection.getViewForObject(setting.preselection);
                        if (view != null) view.setBackground(Utils.getSelectedColors(view.getContext()));
                        view = collection.getViewForObject(previousPreselection);
                        if (view != null) view.setBackground(Utils.getDeselectedColors(view.getContext()));
                    }
                }

                controller.requestCompleteTransaction();
            }
            else {
                if (DEBUG_CONTROLLER) Log.e(TAG, "Unable to apply result set because the controller structure has changed!");
            }

            currentFilter = null;
        }

    }

    private class SearchInflater extends LegacyActionBar.ContextBarListenerAdapter implements LegacyActionBar.CustomViewProvider {

        private boolean active;

        public SearchInflater() {
            active = true;
        }

        public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
            View view = inflater.inflate(R.layout.tag_add_box, container, false);

            final ImageView HintImage = ((ImageView) view.findViewById(R.id.SearchIcon));
            ViewGroup.MarginLayoutParams params = (ViewGroup.MarginLayoutParams) HintImage.getLayoutParams();
            params.leftMargin = 0;
            params.rightMargin = (int) (8 * getResources().getDisplayMetrics().density);
            params.width = container.getContext().getResources().getDimensionPixelSize(Utils.SecondaryKeyline) - params.rightMargin;
            params.height = ViewGroup.LayoutParams.MATCH_PARENT;
            HintImage.setImageDrawable(getResources().getDrawable(R.drawable.back_dark));
            HintImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (searchWrapper != null) searchWrapper.dismiss();
                }
            });
            LegacyRippleDrawable background = new LegacyRippleDrawable(getActivity());
            background.setShape(LegacyRippleDrawable.ShapeCircle);
            HintImage.setBackground(background);
            final ListenableEditText AddBox = ((ListenableEditText) view.findViewById(R.id.AddBox));
            AddBox.setTextSize(20);
            AddBox.setHint(AddBox.getResources().getString(R.string.SearchCurrenciesHint));
    //        AddBox.setText(localSearchString);
    //        if (readWrite && TagStorage.canCreateTags()) {
    //            if (TagStorage.getDefaultTags(null).size() == 0) {
    //                AddBox.setHint(Utils.echoText(AddBox.getResources().getString(R.string.SearchTagsWriteonly), DashboardTitle));
    //                HintImage.setImageDrawable(HintImage.getResources().getDrawable(R.drawable.add_hint));
    //                AddBox.requestFocus();
    //                AddBox.post(new Runnable() {
    //                    @Override
    //                    public void run() {
    //                        InputMethodManager imm = (InputMethodManager) AddBox.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
    //                        imm.showSoftInput(AddBox, InputMethodManager.SHOW_FORCED);
    //                    }
    //                });
    //            }
    //            else {
    //                AddBox.setHint(Utils.echoText(AddBox.getResources().getString(R.string.SearchTags), DashboardTitle));
    //            }
    //        }
    //        else {
    //            AddBox.setHint(Utils.echoText(AddBox.getResources().getString(R.string.SearchTagsReadonly), DashboardTitle));
    //        }
            AddBox.addTextChangedListener(new Utils.OnTextChangedListener() {

                @Override
                public void onTextChanged(final CharSequence Text, int i, int i2, int i3) {
                    SearchHandler.removeCallbacksAndMessages(null);
                    SearchHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (!active) return;
                            if (DEBUG_CONTROLLER) Log.e(TAG, "Text change detected! Text is: " + Text);

                            if (TextUtils.isEmpty(Text.toString().trim())) {
                                controller.requestBeginTransaction();

                                ListSetting setting = (ListSetting) controller.findMetadata(ExpandedListMetadataKey);
                                controller.findSectionWithTag(ExpandedListMetadataKey).clear().addAllObjects(((ListSetting) controller.findMetadata(ExpandedListMetadataKey)).values);
                                CollectionView collection = controller.getCollectionView();
                                if (collection != null) {
                                    collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeTop);
                                    View view = collection.getViewForObject(setting.preselection);
                                    if (view != null) view.setBackground(Utils.getDeselectedColors(view.getContext()));
                                    setting.preselection = null;
                                }

                                controller.requestCompleteTransaction();
                                return;
                            }

                            if (currentFilter != null) {
                                currentFilter.cancel(true);
                            }
                            currentFilter = new FilterTask();
                            currentFilter.execute(Text.toString());
                        }
                    }, SearchDelayMS);
                }
            });

            AddBox.setOnKeyPreImeListener(new ListenableEditText.OnKeyPreImeListener() {
                @Override
                public boolean onKeyPreIme(int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                        AddBox.setText("");
                    }
                    return false;
                }
            });

            AddBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int keyCode, KeyEvent keyEvent) {
                    if (!active) return false;
                    if (keyEvent == null) {
                        InputMethodManager imm = (InputMethodManager) textView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                        handleDonePressed();
                        return true;
                    }
                    if (keyEvent.getAction() == EditorInfo.IME_ACTION_DONE) {
                        InputMethodManager imm = (InputMethodManager) textView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                        handleDonePressed();
                        return true;
                    }
                    return false;
                }

                public void handleDonePressed() {
                    ListSetting setting = ((ListSetting) controller.findMetadata(ExpandedListMetadataKey));
                    if (setting.preselection != null) {
                        setting.setValue(setting.preselection);
                        setting.preselection = null;
                        searchWrapper.pop();
                    }
                }
            });

            return view;
        }

        @Override
        public void onDestroyCustomView(View customView) {

        }

        public void onContextBarStarted() {
            active = true;
        }

        public void onContextBarDismissed() {
            active = false;
            SearchHandler.removeCallbacksAndMessages(null);
            if (currentFilter != null) {
                currentFilter.cancel(true);
            }
            currentFilter = null;

            boolean animationsEnabled = controller.getCollectionView().areAnimationsEnabled();
            controller.getCollectionView().setAnimationsEnabled(false);
            controller.getCollectionView().setAnimationsEnabled(animationsEnabled);

            controller.getCollectionView().setTransactionScrollingMode(CollectionView.TransactionScrollingModeNavigate);
            controller.getCollectionView().setNavigationTransactionDirection(CollectionView.NavigationTransactionLeftToRight);
            controller.getCollectionView().setMoveAnimationDuration(300);
            controller.getCollectionView().setMoveInterpolator(new Utils.FrictionInterpolator(1.5f));

            controller.requestBeginNewDataSetTransaction();

            for (Setting setting : settings) {
                if (setting instanceof BooleanSetting) {
                    controller.addSectionForViewTypeWithTag(CheckboxType, null).addObject(setting);
                }
                if (setting instanceof InfoSetting) {
                    controller.addSectionForViewTypeWithTag(DescriptionType, null).addObject(setting);
                }
                if (setting instanceof HeaderSetting) {
                    controller.addSectionForViewTypeWithTag(HeaderType, null).addObject(setting);
                }
                if (setting instanceof ListSetting) {
                    controller.addSectionForViewTypeWithTag(SearchableListType, null).addObject(setting);
                }
            }

            controller.requestCompleteTransaction();
            searchWrapper = null;

            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getActivity().getWindow().getDecorView().getWindowToken(), 0);
        }

    }

    public void expandSetting(ListSetting setting) {
        controller.getCollectionView().setTransactionScrollingMode(CollectionView.TransactionScrollingModeNavigate);
        controller.getCollectionView().setNavigationTransactionDirection(CollectionView.NavigationTransactionRightToLeft);
        controller.getCollectionView().setMoveAnimationDuration(300);
        controller.getCollectionView().setMoveInterpolator(new Utils.FrictionInterpolator(1.5f));

        controller.requestBeginNewDataSetTransaction();

        controller.addSectionForViewTypeWithTag(ListEntryType, ExpandedListMetadataKey).setMetadata(setting).addAllObjects(setting.values);
        SearchInflater inflater = new SearchInflater();
        searchWrapper = getHeader().createContextMode(inflater);
        searchWrapper.setAnimationStyle(LegacyActionBar.ConfirmationActionBar);
        searchWrapper.setBackgroundResource(R.drawable.popover_action_mode_white);
        searchWrapper.setCustomView(inflater);
        searchWrapper.setBackMode(LegacyActionBarView.CaretBackMode);
        searchWrapper.setBackButtonEnabled(false);
        searchWrapper.start();

        controller.requestCompleteTransaction();
    }

    public void collapseSetting(View view) {
        String value = (String) controller.getCollectionView().getObjectForView(view);
        ((ListSetting) controller.findMetadata(ExpandedListMetadataKey)).setValue(value);
        ((ListSetting) controller.findMetadata(ExpandedListMetadataKey)).preselection = null;
        searchWrapper.pop();
    }

    public BooleanSetting addCheckboxSetting(String key, String title, String description) {
        BooleanSetting setting = new BooleanSetting(key);
        setting.setTitle(title);
        setting.setDescription(description);
        setting.setValue(prefs.getBoolean(key, false));
        settings.add(setting);
        controller.addSectionForViewTypeWithTag(CheckboxType, null).addObject(setting);
        return setting;
    }

    public BooleanSetting addCheckboxSetting(String key, int title, int description) {
        BooleanSetting setting = new BooleanSetting(key);
        setting.setTitle(title);
        setting.setDescription(description);
        setting.setValue(prefs.getBoolean(key, false));
        settings.add(setting);
        controller.addSectionForViewTypeWithTag(CheckboxType, null).addObject(setting);
        return setting;
    }

    public ListSetting addListSetting(String key, List<String> values, List<String> descriptions, List<String> titles) {
        ListSetting setting = new ListSetting(key);
        setting.values = values;
        setting.descriptions = descriptions;
        setting.readableValues = titles;
        setting.setValue(prefs.getString(key, null));
        settings.add(setting);
        controller.addSectionForViewTypeWithTag(SearchableListType, null).addObject(setting);
        return setting;
    }

    public InfoSetting addInfoSetting(String key, String title, String description) {
        InfoSetting setting = new InfoSetting(key);
        setting.setTitle(title);
        setting.setDescription(description);
        settings.add(setting);
        controller.addSectionForViewTypeWithTag(DescriptionType, null).addObject(setting);
        return setting;
    }

    public HeaderSetting addHeaderSetting(String key, String title, String description) {
        HeaderSetting setting = new HeaderSetting(key);
        setting.setTitle(title);
        setting.setDescription(description);
        settings.add(setting);
        controller.addSectionForViewTypeWithTag(HeaderType, null).addObject(setting);
        return setting;
    }

    public HeaderSetting addHeaderSetting(String key, int title, int description) {
        HeaderSetting setting = new HeaderSetting(key);
        setting.setTitle(title);
        setting.setDescription(description);
        settings.add(setting);
        controller.addSectionForViewTypeWithTag(HeaderType, null).addObject(setting);
        return setting;
    }

    private SharedPreferences prefs;

}
