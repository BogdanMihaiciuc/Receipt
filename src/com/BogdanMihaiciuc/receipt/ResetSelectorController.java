package com.BogdanMihaiciuc.receipt;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.Date;
import com.BogdanMihaiciuc.util.EventTouchListener;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.LegacySnackbar;
import com.BogdanMihaiciuc.util.Picker;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.PopoverDrawable;
import com.BogdanMihaiciuc.util.SwipeToDeleteListener;
import com.BogdanMihaiciuc.util.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class ResetSelectorController implements LegacyActionBar.CustomViewProvider, LegacyActionBar.OnLegacyActionSelectedListener {

    final static String TAG = ResetSelectorController.class.getName();

//    static class Rule {
//        int ruleType;
//        int ruleValue;
//
//        public static Rule make(int type, int value) {
//            Rule rule = new Rule();
//
//            rule.ruleType = type;
//            rule.ruleValue = value;
//
//            return rule;
//        }
//    }

    final static String ActionBarKey = "ResetSelectorActionBar";

    final static int StateList = 0;
    final static int StateRule = 1;

    final static int TabMonthly = 0;
    final static int TabWeekly = 1;

    private int state = StateList;
    private int tab = TabMonthly;
    private List<RepeatDateStorage.RepeatDate> rules;
    private RepeatDateStorage storage;

    private LegacyActionBar actionBar;

    private FrameLayout content;
    private CollectionView rulesList;
    private DisableableViewPager rulePager;

    private int DashboardTextColor;

    private Popover popover;

    public ResetSelectorController(Context context) {
        actionBar = LegacyActionBar.getAttachableLegacyActionBar();

        storage = RepeatDateStorage.sharedStorage(context);
        rules = storage.getRepeatDates();

        ruleController.addSection().addAllObjects(rules);
    }

    public void attach(Activity activity, Popover popover) {
        DashboardTextColor = activity.getResources().getColor(R.color.DashboardText);

        actionBar.setBackButtonEnabled(false);
        actionBar.setBackMode(LegacyActionBarView.CaretBackMode);
        actionBar.setSeparatorVisible(true);
        actionBar.setTitle(ReceiptActivity.titleFormattedString(activity.getString(R.string.ResetDate)));
        actionBar.setTextColor(DashboardTextColor);
        actionBar.setOnLegacyActionSeletectedListener(this);

        actionBar.setFillContainerEnabled(true);

        actionBar.setCommandedPopoverIndicatorWithGravities(popover, PopoverDrawable.GravityBelow);
        actionBar.setRoundedCornersWithRadius(new int[] {Utils.TopLeftCorner, Utils.TopRightCorner}, activity.getResources().getDisplayMetrics().density * 7);

        LegacyActionBar.ActionItem addButton = actionBar.buildItem().setId(R.id.MenuAddList).setResource(R.drawable.content_new_mini_dark).setTitle("Add Rule").setShowAsIcon(true).build();
        addButton.setEnabled(storage.canAddRepeatDates());

        this.popover = popover;

        activity.getFragmentManager().beginTransaction().add(actionBar, ActionBarKey).commit();
    }

    private CollectionViewController ruleController = new CollectionViewController() {
        int suffixResources[] = new int[] {R.string.SuffixST, R.string.SuffixND, R.string.SuffixRD, R.string.SuffixTH};

        private final Runnable BackStackEntry = new Runnable() {
            @Override
            public void run() {
                if (selectionWrapper != null) selectionWrapper.dismiss();
            }
        };

        private ArrayList<RepeatDateStorage.RepeatDate> selection = new ArrayList<RepeatDateStorage.RepeatDate>();
        private LegacyActionBar.ContextBarWrapper selectionWrapper;
        private LegacyActionBar.ContextBarListenerAdapter selectListener = new LegacyActionBar.ContextBarListenerAdapter() {

            public void onContextBarStarted() {
                ((Utils.BackStack) getCollectionView().getContext()).persistentBackStack().pushToBackStack(BackStackEntry);
            }

            @Override
            public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
                super.onLegacyActionSelected(item);

                // TODO
//                Log.d(TAG, "" + ((Utils.BackStack) getCollectionView().getContext()).persistentBackStack().backStackSize());
            }

            @Override
            public void onContextBarDismissed() {
                selectionWrapper = null;

                ((Utils.BackStack) getCollectionView().getContext()).persistentBackStack().swipeFromBackStack(BackStackEntry);

                deselect();
            }
        };

        private void deselect() {
            selection.clear();
            getCollectionView().refreshViews();

            if (selectionWrapper != null) selectionWrapper.dismiss();
        }

        private void onRuleClicked(View rule) {
            RepeatDateStorage.RepeatDate item = (RepeatDateStorage.RepeatDate) getObjectForView(rule);

            int oldSize = selection.size();

            if (selection.contains(item)) {
                selection.remove(item);
                rule.setSelected(false);
            }
            else {
                selection.add(item);
                rule.setSelected(true);
            }

            Context context = getCollectionView().getContext();

            if (selectionWrapper == null) {
                selectionWrapper = actionBar.createContextMode(selectListener);

//                selectionWrapper.setBackgroundColor(context.getResources().getColor(R.color.SelectionBar));
                selectionWrapper.addItem(R.id.menu_delete, context.getString(R.string.DeleteLabel), R.drawable.ic_action_delete, false, true);

                selectionWrapper.start();
            }

            if (selection.size() == 0 && selectionWrapper != null) {
                selectionWrapper.dismiss();
            }

            if (selectionWrapper != null) selectionWrapper.setTitleAnimated(
                    Utils.appendWithSpan(
                            new SpannableStringBuilder(), context.getString(R.string.SelectionTitle, selection.size()), new AbsoluteSizeSpan(20, true)
                    ), selection.size() - oldSize
            );
        }

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            Context context = container.getContext();
            final int LineHeight = context.getResources().getDimensionPixelSize(R.dimen.LineHeight);
            final int PrimaryKeyline = context.getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline);

            FrameLayout rule = new FrameLayout(context);

            TextView ruleValue = new TextView(context);
            ruleValue.setTextSize(16);
            ruleValue.setTextColor(DashboardTextColor);
            ruleValue.setPadding(0, 0, 0, 0);
            ruleValue.setGravity(Gravity.CENTER);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, LineHeight);
            params.leftMargin = PrimaryKeyline;
            rule.addView(ruleValue, params);

            TextView ruleType = new TextView(context);
            ruleType.setTextSize(16);
            ruleType.setTextColor(context.getResources().getColor(R.color.DashboardTitle));
            ruleType.setPadding(0, 0, 0, 0);
            ruleType.setGravity(Gravity.CENTER);

            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, LineHeight);
            params.rightMargin = PrimaryKeyline;
            params.gravity = Gravity.CENTER_VERTICAL | Gravity.RIGHT;
            rule.addView(ruleType, params);

            rule.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, LineHeight));
            rule.setBackground(Utils.getDeselectedColors(getCollectionView().getContext()));

            EventTouchListener listener = EventTouchListener.listenerInContext(container.getContext());
            listener.setDelegate(new EventTouchListener.EventDelegateAdapter() {
                @Override
                public boolean viewShouldPerformClick(EventTouchListener listener, View view) {
                    return true;
                }

                @Override
                public boolean viewShouldPerformLongClick(EventTouchListener listener, View view) {
                    return true;
                }

                @Override
                public boolean viewShouldStartMoving(EventTouchListener listener, View view) {
                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    ((LegacyRippleDrawable) view.getBackground()).flushRipple();

                    return selection.size() == 0;
                }

                @Override
                public void viewDidMove(EventTouchListener listener, View view, float distance) {
                    Utils.ViewUtils.displaceView(view, distance, 0);
                    float alpha = Utils.constrain(Math.abs(view.getTranslationX()) / view.getWidth(), 0, 1);
                    alpha = Utils.interpolateValues(alpha, 1f, 0.2f);
                    view.setAlpha(alpha);
                }

                @Override
                public void viewDidBeginSwiping(EventTouchListener listener, View view, float velocity) {
                    RepeatDateStorage.RepeatDate target;
                    try {
                        target = (RepeatDateStorage.RepeatDate) ruleController.getCollectionView().getObjectForView(view);
                    } catch (Exception e) {
                        target = null;
                    }

                    if (target == null) {
                        viewDidCancelSwiping(listener, view);
                        return;
                    }

                    final int Index = ruleController.getSectionAtIndex(0).indexOfObject(target);

                    // The view will continue to move with constant speed
                    if (velocity == 0) {
                        velocity = EventTouchListener.sgn(view.getTranslationX());
                    }

                    float totalDistance = ruleController.getCollectionView().getWidth() - Math.abs(view.getTranslationX());
                    long timeRequired = (long) (totalDistance / Math.abs(velocity));
                    if (timeRequired > 300) {
                        timeRequired = 300;
                    }
                    if (timeRequired < 100) {
                        timeRequired = 100;
                    }

//                            view.animate().alpha(0f).translationXBy(EventTouchListener.sgn(velocity) * totalDistance)
//                                    .setInterpolator(new AccelerateInterpolator(1.5f))
//                                    .setDuration(timeRequired)
//                                    .withEndAction(new Runnable() {
//                                        @Override
//                                        public void run() {
//                                            view.setLayerType(View.LAYER_TYPE_NONE, null);
//                                            view.setAlpha(1f);
//                                            view.setTranslationX(0f);
//
//                                            view.animate().setInterpolator(new AccelerateDecelerateInterpolator());
//                                        }
//                                    });

                    ruleController.requestBeginTransaction();
//                    confirmingReceipt = target;
                    ruleController.getSectionAtIndex(0).removeObject(target);
//                    if (confirmingReceipt.header != null) { //enable deletion of corrupt receipts
//                        usedGlobalBudget = usedGlobalBudget.subtract(confirmingReceipt.header.total.multiply(new BigDecimal(10000 + confirmingReceipt.header.tax).movePointLeft(4)));
//                    }
//                    updateBalanceDisplay();
//
//                    showDeleteConfirmator(true);

                    final float StartingAlpha = view.getAlpha();
                    final float StartingTranslation = view.getTranslationX();
                    final float Velocity = velocity;
                    final float TotalDistance = totalDistance;
                    final View InnerView = view;

                    final CollectionView RuleCollection = ruleController.getCollectionView();

                    RuleCollection.setAnimationsEnabled(true);
                    RuleCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
                    RuleCollection.setAnchorCondition(null);
                    RuleCollection.setDeleteAnimationStride(0);
                    RuleCollection.setDeleteAnimationDuration(timeRequired);
                    RuleCollection.setDeleteInterpolator(new LinearInterpolator());
//                            RuleCollection.setMoveWithLayersEnabled(true);
                    RuleCollection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
                        @Override
                        public void playAnimation(View view, Object object, int viewType) {
                            InnerView.setAlpha(1f);
                            InnerView.setTranslationX(0f);

                            InnerView.setLayerType(View.LAYER_TYPE_NONE, null);
                            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                            view.setAlpha(StartingAlpha);
                            view.setTranslationX(view.getTranslationX() + StartingTranslation);
                            view.setScaleY(0.99f);
                            view.animate().alpha(0f).translationXBy(EventTouchListener.sgn(Velocity) * TotalDistance);
                        }

                        @Override
                        public void resetState(View view, Object object, int viewType) {
                            view.setAlpha(1f);
                            view.setLayerType(View.LAYER_TYPE_NONE, null);
                            RuleCollection.setDeleteInterpolator(CollectionView.StandardDeleteInterpolator);
                        }
                    });

                    ruleController.requestCompleteTransaction();

                    final RepeatDateStorage.RepeatDate Target = target;

                    LegacySnackbar.showSnackbarWithMessage(view.getResources().getString(R.string.DeleteOverlayTitle), new LegacySnackbar.SnackbarListener() {
                        @Override
                        public void onActionConfirmed(LegacySnackbar snackbar) {
                            storage.removeRepeatDate(Target);
                        }

                        @Override
                        public void onActionUndone(LegacySnackbar snackbar) {
                            actionBar.findItemWithId(R.id.MenuAddList).setEnabled(storage.canAddRepeatDates());

                            ruleController.requestBeginTransaction();

                            ruleController.getSectionAtIndex(0).addObjectToIndex(Target, Index);
                            CollectionView collectionView = ruleController.getCollectionView();

                            if (collectionView != null) {
                                collectionView.setInsertAnimator(new CollectionView.ReversibleAnimation() {
                                    @Override
                                    public void playAnimation(View view, Object object, int viewType) {
                                        view.setTranslationX(EventTouchListener.sgn(Velocity) * view.getWidth());
                                        view.setAlpha(0f);

                                        view.animate().alpha(1f);
                                    }

                                    @Override
                                    public void resetState(View view, Object object, int viewType) {
                                    }
                                });

                                collectionView.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
                                collectionView.setAnchorCondition(new CollectionView.AnchorInspector() {
                                    @Override
                                    public boolean isAnchor(Object object, int viewType) {
                                        return object == Target;
                                    }
                                });

                                collectionView.setMoveWithLayersEnabled(true);
                                collectionView.setAnimationsEnabled(true);
                            }

                            ruleController.requestCompleteTransaction();
                        }
                    }, (Activity) view.getContext());

                    actionBar.findItemWithId(R.id.MenuAddList).setEnabled(true);
                }

                @Override
                public void viewDidCancelSwiping(EventTouchListener listener, final View view) {
                    view.animate()
                            .alpha(1f).translationX(0f).withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            view.setLayerType(View.LAYER_TYPE_NONE, null);
                        }
                    });
                }

                @Override
                public int getSwipeDistanceThreshold() {
                    return 2 * ruleController.getCollectionView().getWidth() / 3;
                }
            });

            rule.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onRuleClicked(v);
                }
            });
            rule.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    onRuleClicked(v);
                    return true;
                }
            });
            ((LegacyRippleDrawable) rule.getBackground()).setForwardListener(listener);

            return rule;
        }

        @Override
        public void configureView(View view, Object item, int viewType) {
            ViewGroup ruleView = (ViewGroup) view;
            TextView ruleValue = (TextView) ruleView.getChildAt(0);
            TextView ruleType = (TextView) ruleView.getChildAt(1);
            Context context = view.getContext();

//            Log.e(TAG, "The OnTouchController has been determined; it is : " + view.getOn);

            RepeatDateStorage.RepeatDate rule = (RepeatDateStorage.RepeatDate) item;
            if (rule.type == TabMonthly) {
                ruleType.setText(view.getContext().getString(R.string.ResetTypeMonthly));

                int suffixResource = suffixResources[3];
                if (rule.value / 10 != 1) {
                    if (rule.value % 10 < 4 && rule.value % 10 > 0) suffixResource = suffixResources[rule.value % 10 - 1];
                }

                SpannableStringBuilder builder = new SpannableStringBuilder(context.getString(R.string.ResetDisplay, rule.value, context.getString(suffixResource)));
                builder.setSpan(new SuperscriptSpan(), builder.length() - 2, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                builder.setSpan(new RelativeSizeSpan(0.66f), builder.length() - 2, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                ruleValue.setText(builder);

//                Log.e(TAG, ruleType.getText() + " " + ruleValue.getText());
            }
            else {
                ruleType.setText(view.getContext().getString(R.string.ResetTypeWeekly));

                Calendar calendar = Calendar.getInstance();
                calendar.set(Calendar.DAY_OF_WEEK, rule.value);

                ruleValue.setText(calendar.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()));

//                Log.e(TAG, ruleType.getText() + " " + ruleValue.getText());
            }


            if (view.isSelected() != selection.contains(item)) {
                if (!isRefreshingViews()) {
                    ((LegacyRippleDrawable) view.getBackground()).dismissPendingAnimation();
                }
                view.setSelected(selection.contains(item));
            }


        }
    };

    private View createListView(Context context) {
        rulesList = new CollectionView(context);

        rulesList.setController(ruleController);

        return rulesList;
    }

    private int currentPickerValue = 0;

    private View createRuleView(final Context context) {
        rulePager = new DisableableViewPager(context);

//        rulePager.addView(new NumberPicker(context));
//        rulePager.addView(new NumberPicker(context));

        rulePager.setAdapter(new PagerAdapter() {
            @Override
            public int getCount() {
                return 2;
            }

            @Override
            public boolean isViewFromObject(View view, Object o) {
                return view == o;
            }

            public Object instantiateItem(ViewGroup container, int position) {
                View layout = LayoutInflater.from(container.getContext()).inflate(R.layout.popover_dashboard_reset_picker, container, false);

                Picker picker = (Picker) layout.findViewById(R.id.Picker);
                picker.setTextColor(context.getResources().getColor(R.color.DashboardText));
                if (position == 0) {
                    ArrayList<String> strings = new ArrayList<String>(31);
                    for (int i = 1; i < 32; i++) {
                        strings.add("" + i);
                    }
                    picker.setValues(strings);
                }
                else {
                    List strings = Arrays.asList("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday");
                    picker.setValues(strings);
                }

                picker.setPickerListener(new Picker.PickerListener() {
                    @Override
                    public void onValueChanged(Picker picker, int index, boolean fromUser) {

                    }

                    @Override
                    public void onValueSelected(Picker picker, int index, boolean fromUser) {
                        currentPickerValue = index;
                    }
                });

                picker.setCurrentValue(currentPickerValue);

                layout.findViewById(R.id.AddResetButton).setBackground(new LegacyRippleDrawable(container.getContext()));
                layout.findViewById(R.id.AddResetButton).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        addRepeatDate();
                    }
                });

                container.addView(layout);
                return layout;
            }

            public void destroyItem(ViewGroup container, int index, Object object) {
                container.removeView((View) object);
            }
        });

        rulePager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int i, float v, int i2) {

            }

            @Override
            public void onPageSelected(int i) {
                if (insertWrapper != null) {
                    insertWrapper.setSelectedNavigationIndex(i);
                }
            }

            @Override
            public void onPageScrollStateChanged(int i) {

            }
        });

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        rulePager.setLayoutParams(params);

        rulePager.setCurrentItem(actionBar.getCurrentContextMode().getCurrentNavigationIndex());

        return rulePager;
    }

    public void addRepeatDate() {
        int type = rulePager.getCurrentItem();
        int value = currentPickerValue + 1;

        ruleController.getSectionAtIndex(0).addObject(storage.addRepeatDate(type, value));

        actionBar.getCurrentContextMode().dismiss();
    }

    @Override
    public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
        View layout = inflater.inflate(R.layout.popover_dashboard_reset, container, false);

        actionBar.setContainer((ViewGroup) layout.findViewById(R.id.ResetHeader));
        content = (FrameLayout) layout.findViewById(R.id.ResetContent);

        if (state == StateList) {
            content.addView(createListView(container.getContext()));
        }
        else {
            content.addView(createRuleView(container.getContext()));
        }

        if (container.getContext().getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            popover.requestGravity(Popover.GravityRightOf);
        }
        else {
            popover.requestGravity(Popover.GravityBelow);
        }

        return layout;
    }

    @Override
    public void onDestroyCustomView(View customView) {
        content = null;

        rulesList = null;

        rulePager = null;
    }

    public void detach() {
        actionBar.getActivity().getFragmentManager().beginTransaction().remove(actionBar).commit();
    }

    private LegacyActionBar.ContextBarWrapper insertWrapper;
    private final Runnable DismissInsertWrapperRunnable = new Runnable() {
        @Override
        public void run() {
            if (insertWrapper != null) {
                insertWrapper.dismiss();
            }
        }
    };
    private LegacyActionBar.ContextBarListener insertListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {
            state = StateRule;
            ((Utils.BackStack) actionBar.getActivity()).pushToBackStack(DismissInsertWrapperRunnable);
        }

        @Override
        public void onContextBarDismissed() {
            cancelAddRule();
            state = StateList;
            insertWrapper = null;
            ((Utils.BackStack) actionBar.getActivity()).popBackStackFrom(DismissInsertWrapperRunnable);

            if (!storage.canAddRepeatDates()) {
                actionBar.findItemWithId(R.id.MenuAddList).setEnabled(false);
            }
            else {
                actionBar.findItemWithId(R.id.MenuAddList).setEnabled(true);
            }
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {

        }
    };

    @Override
    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        if (item.getId() == R.id.MenuAddList) {
            addRule();
        }
    }

    public void addRule() {
        if (insertWrapper != null) return;

        insertWrapper = actionBar.createContextMode(insertListener);
        insertWrapper.setBackgroundResource(R.drawable.actionbar_white_round);
        insertWrapper.setDoneResource(R.drawable.back_dark);
        insertWrapper.setTextColor(DashboardTextColor);

        insertWrapper.setMode(LegacyActionBar.ConfirmationActionBar);

        insertWrapper.setNavigationMode(LegacyActionBarView.InlineTabsNavigationMode);
        insertWrapper.addNavigationElement(LegacyActionBar.LegacyNavigationElement.make(0, actionBar.getString(R.string.ResetTypeMonthly), 0));
        insertWrapper.addNavigationElement(LegacyActionBar.LegacyNavigationElement.make(1, actionBar.getString(R.string.ResetTypeWeekly), 0));
        insertWrapper.setInlineNavigationGravity(Gravity.RIGHT);

        insertWrapper.setOnLegacyNavigationElementSelectedListener(new LegacyActionBar.OnLegacyNavigationElementSelectedListener() {
            @Override
            public void onLegacyNavigationElementSelected(int index, LegacyActionBar.LegacyNavigationElement element) {
                if (rulePager != null) {
                    rulePager.setCurrentItem(index);
                }
            }
        });

        insertWrapper.start();

        currentPickerValue = 0;

        View pager = createRuleView(actionBar.getActivity());

        content.addView(pager);
        pager.setTranslationX(content.getWidth());

        rulesList.requestDisableInteractions();
        final View RulesList = rulesList;
        rulesList.animate().translationX(-content.getWidth()).setDuration(300).setInterpolator(new Utils.FrictionInterpolator(1.5f)).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (content != null) {
                    content.removeView(RulesList);
                }
            }
        }).start();

        rulesList = null;

        pager.animate().translationX(0).setDuration(300).setInterpolator(new Utils.FrictionInterpolator(1.5f)).start();
    }

    public void cancelAddRule() {
        View list = createListView(actionBar.getActivity());

        content.addView(list);
        list.setTranslationX(- content.getWidth());

        rulePager.freeze();
        final View RulePager = rulePager;
        rulePager.animate().translationX(content.getWidth()).setDuration(300).setInterpolator(new Utils.FrictionInterpolator(1.5f)).withEndAction(new Runnable() {
            @Override
            public void run() {
                if (content != null) {
                    content.removeView(RulePager);
                }
            }
        }).start();

        rulePager = null;

        list.animate().translationX(0).setDuration(300).setInterpolator(new Utils.FrictionInterpolator(1.5f)).start();
    }

}
