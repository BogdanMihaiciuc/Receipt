package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.LayoutTransition;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.support.v4.view.ViewCompat;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.AbsoluteSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.CollectionPopover;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.EventTouchListener;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.ListenableEditText;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.PrecisionRangeSlider;
import com.BogdanMihaiciuc.util.$;
import com.BogdanMihaiciuc.util.SwipeToDeleteListener;
import com.BogdanMihaiciuc.util.TagView;
import com.BogdanMihaiciuc.util.Utils;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Item;
import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;

public class TagExpander {

    final static String TAG = TagExpander.class.toString();

    final static boolean USE_POPOVER = true;
    final static String DeleteActionBarKey = "TagExpander.deleteActionBar";

    final static Object UncommonTag = new Object();

    public final static int ScrollerID = 0x00CACA00;

    public interface OnCloseListener {
        public void onClose();
    }

    public interface OnTagDeletedListener {
        public void onTagDeleted(Tag tag);
    }

    static class SavedState {
        boolean addButtonActive;
        CharSequence addButtonText;
        boolean secondPhaseAdd;

        CollectionPopover popover;
        TagController popoverController;
    }

    static SavedState staticContext = new SavedState();

    @Deprecated
    static TagExpander fromViewInContainer(TagView compactTags, ViewGroup container) {
        return fromViewInContainerWithTarget(compactTags, container, null);
    }

    static TagExpander fromViewInContainerWithTarget(TagView compactTags, ViewGroup container, Item target) {
        TagExpander expander = new TagExpander();
        expander.context = container.getContext();
        expander.compactTags = compactTags;
        expander.container = container;
        expander.metrics = expander.context.getResources().getDisplayMetrics();
        expander.target = target;
        expander.itemTarget = true;
        expander.collectionViewParent = true;
        return expander;
    }

    static TagExpander fromViewInContainerWithProxyTarget(TagView compactTags, ViewGroup container, Item target) {
        TagExpander expander = fromViewInContainerWithTarget(compactTags, container, target);
        expander.itemTarget = false;
        return expander;
    }

    static TagExpander fromViewInListViewContainerWithTarget(TagView compactTags, ViewGroup container, Item target) {
        TagExpander expander = fromViewInContainerWithTarget(compactTags, container, target);
        expander.collectionViewParent = false;
        expander.listViewParent = true;
        return expander;
    }

    private Context context;
    private ViewGroup container;
    private HorizontalScrollView scroller;
    private View compactor;
    private RelativeLayout tagRoot;
    private DisplayMetrics metrics;
    private ArrayList<LinearLayout> expandedTags = new ArrayList<LinearLayout>();
    private AutoCompleteTextViewWithSwappableAdapters addTextView;
    private ViewGroup colorPanel;
    private OnCloseListener onCloseListener;
    private OnTagDeletedListener onTagDeletedListener;
    private Item target;
    private boolean closed;
    private boolean expanded;

    private boolean inverted;

    private boolean enforceHuePositions = true;
    private boolean readWrite = true;

    private LegacyActionBar deleteActionBar;
    private ValueAnimator deleteAnimator;

    // These may be null in a valid TagExpander
    private TagView compactTags;

    private boolean itemTarget;
    private boolean collectionViewParent;
    private boolean listViewParent;

    CollectionPopover popover;

    public Item getTarget() {
        return target;
    }

    protected LegacyRippleDrawable obtainRipple() {
        LegacyRippleDrawable background = new LegacyRippleDrawable(context);
        background.setShape(LegacyRippleDrawable.ShapeRoundRect);

        if (inverted) {
            background.setColors(LegacyRippleDrawable.DefaultBackgroundColor, LegacyRippleDrawable.DefaultLightPressedColor);
            background.setRippleColor(LegacyRippleDrawable.DefaultLightRippleColor);
        }

        return background;
    }

    protected LinearLayout createExtendedTag(int color, boolean initial) {
        LinearLayout expandedTag = (LinearLayout) ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.tag, tagRoot, false);
        expandedTag.setBackground(obtainRipple());
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setDashedCircleEnabled(false);
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusEnabled(true);
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setColor(color);

        if (target != null) {
            for (Tag tag : target.tags) {
                if (tag.color == color) {
                    ((TextView) expandedTag.findViewById(R.id.TagText)).setText(tag.name);
                    break;
                }
            }
        }

        if (initial) ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusOpacity(0f);
        else expandedTag.findViewById(R.id.TagColor).setRotation(45);
        if (initial) expandedTag.findViewById(R.id.TagText).setAlpha(0f);

        return expandedTag;
    }

    public void setCanEditTags(boolean canEditTags) {
        readWrite = canEditTags;
    }

    public void setEnforceHuePositions(boolean enforceHuePositions) {
        this.enforceHuePositions = enforceHuePositions;
    }

    /**
     * Sets whether the ripple effects and tag borders will be white instead of black. This must be called before the TagExpander has created its layout.
     * @param enabled True if the ripple effects and tag borders will be white, false otherwise.
     */
    public void setInvertedModeEnabled(boolean enabled) {
        inverted = enabled;
    }

    public void expand() {
        expandAnimated(true);
    }

    public void expandAnimated(final boolean Animated) {
        expanded = true;

        scroller = new HorizontalScrollView(context);
        scroller.setId(ScrollerID);
        tagRoot = new RelativeLayout(context);
        tagRoot.setClipChildren(false);

        if (itemTarget) ViewCompat.setHasTransientState(container, true);

        scroller.addView(tagRoot, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));

        final TagView compactTags = this.compactTags == null ? (TagView) container.findViewById(R.id.ItemTags) : this.compactTags;
        final int stride = compactTags.getCircleStride();

        int id = 1000;
        int i = compactTags.getColors().size() - 1;

        for (; i >= 0; i--) {
            final int color = compactTags.getColors().get(i);
            LinearLayout expandedTag = (LinearLayout) ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.tag, tagRoot, false);
            expandedTag.setBackground(obtainRipple());
            ((TagView) expandedTag.findViewById(R.id.TagColor)).setDashedCircleEnabled(false);
            ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusEnabled(true);
            ((TagView) expandedTag.findViewById(R.id.TagColor)).setColor(color);
            if (inverted) ((TagView) expandedTag.findViewById(R.id.TagColor)).setWhiteBordersEnabled(true);

            if (target != null) {
                for (Tag tag : target.tags) {
                    if (tag.color == color) {
                        ((TextView) expandedTag.findViewById(R.id.TagText)).setText(tag.name);
                        break;
                    }
                }
            }

            ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusOpacity(0f);
            expandedTag.findViewById(R.id.TagText).setAlpha(0f);
            if (inverted) ((TextView) expandedTag.findViewById(R.id.TagText)).setTextColor(expandedTag.getResources().getColor(android.R.color.white));
            else ((TextView) expandedTag.findViewById(R.id.TagText)).setTextColor(expandedTag.getResources().getColor(R.color.ItemText));
            expandedTags.add(expandedTag);
            RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            if (id == 1000) {
                params.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            }
            else {
                params.addRule(RelativeLayout.RIGHT_OF, id);
            }
            id++;
            expandedTag.setId(id);
            expandedTag.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    removeColor(color);
                }
            });
            if (color != -1)
                expandedTag.setTag(color);
            else
                expandedTag.setTag(UncommonTag);
            tagRoot.addView(expandedTag, 0, params);
        }
        LinearLayout expandedTag = (LinearLayout) ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.tag, tagRoot, false);
        expandedTag.setBackground(obtainRipple());
        expandedTag.setId(id + 1);
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setDashedCircleEnabled(true);
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusEnabled(true);
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusOpacity(0f);
        if (inverted) ((TagView) expandedTag.findViewById(R.id.TagColor)).setWhiteBordersEnabled(true);
        expandedTag.findViewById(R.id.TagColor).setRotation(-45);
        ((TextView) expandedTag.findViewById(R.id.TagText)).setText("Add Tag");
        if (inverted) ((TextView) expandedTag.findViewById(R.id.TagText)).setTextColor(expandedTag.getResources().getColor(android.R.color.white));
        else ((TextView) expandedTag.findViewById(R.id.TagText)).setTextColor(expandedTag.getResources().getColor(R.color.ItemText));
        expandedTag.findViewById(R.id.TagText).setAlpha(0f);
        ArrayList<Integer> tmp = new ArrayList<Integer>();
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setColors(tmp);
        expandedTags.add(expandedTag);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.RIGHT_OF, id);
        tagRoot.addView(expandedTag, 0, params);

        if (USE_POPOVER) {
            expandedTag.setOnClickListener(popoverAddListener);
        }
        else {
            expandedTag.setOnClickListener(addListener);
        }
        expandedTag.setTag(-1);

        if (!target.canAddTags()) {
             expandedTag.findViewById(R.id.TagText).setAlpha(1f);
            ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusOpacity(1f);
            expandedTag.findViewById(R.id.TagColor).setRotation(0);
            expandedTag.setVisibility(View.GONE);
        }

//        final LinearLayout dashedTag = expandedTag;

        container.addView(scroller);
        if (itemTarget) scroller.setVisibility(View.INVISIBLE);

        tagRoot.setLayoutTransition(new LayoutTransition());
        tagRoot.getLayoutTransition().setAnimateParentHierarchy(true);

        tagRoot.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int j, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                tagRoot.removeOnLayoutChangeListener(this);

                int displacement = tagRoot.getWidth();

                if (itemTarget) {
                    scroller.setVisibility(View.VISIBLE);
                    compactTags.setVisibility(View.INVISIBLE);

                    if (tagRoot.getWidth() > container.getWidth() * 0.8f) {
                        displacement = (int) (container.getWidth() * 0.8f);
                    }

                    final int Displacement = displacement;

                    scroller.post(new Runnable() {
                        @Override
                        public void run() {
                            scroller.getLayoutParams().width = Displacement;
                            scroller.setLayoutParams(scroller.getLayoutParams());
                        }
                    });

                    for (int i = 0; i < container.getChildCount(); i++) {
                        if (container.getChildAt(i) != scroller) {
                            if (Animated) {
                                container.getChildAt(i).animate().xBy(displacement - compactTags.getWidth() + 8 * metrics.density).alpha(0.15f).setStartDelay(0).withLayer();
                            }
                            else {
                                container.getChildAt(i).setX(container.getChildAt(i).getX() + displacement - compactTags.getWidth() + 8 * metrics.density);
                                container.getChildAt(i).setAlpha(0.15f);
                            }
                        }
                    }
                }

                if (itemTarget) {
                    compactor = new View(context);
                    container.addView(compactor, new ViewGroup.LayoutParams(container.getWidth() - displacement, ViewGroup.LayoutParams.MATCH_PARENT));
                    compactor.setX(displacement);
                    compactor.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        compact();
                    }
                });
                }

                boolean skipDashed = !compactTags.isDashedCircleEnabled();
                int modifier = skipDashed ? 2 : 1;

                // (width/2) - (stride * count) - (circleWidth/2) - padding
                int circleStartpoint = compactTags.getWidth() / 2 - (expandedTags.size() - modifier) * (stride / 2) - (int)(8 * metrics.density);// - (8 * metrics.density) - (8 * metrics.density));
                if (expandedTags.size() > 0) {
                    circleStartpoint -= expandedTags.get(0).getChildAt(0).getX();
                }

                int i = 0;
                for (final LinearLayout expandedTag : expandedTags) {
                    if (skipDashed && i == expandedTags.size() - 1) break;
                    i++;
                    if (Animated) {
                        expandedTag.setX(circleStartpoint + (i - 1) * (stride));
                        expandedTag.animate().translationX(0);
                        expandedTag.findViewById(R.id.TagText).animate().alpha(1f);
                        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
//                        expandedTag.findViewById(R.id.TagColor).animate().rotationBy(45);
                        final float RotationStart = expandedTag.findViewById(R.id.TagColor).getRotation();
                        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusOpacity(valueAnimator.getAnimatedFraction());
                                expandedTag.findViewById(R.id.TagColor).setRotation(Utils.interpolateValues(valueAnimator.getAnimatedFraction(), RotationStart, RotationStart + 45));
                            }
                        });
                        animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (!closed) {
                                    if (target.tags.size() == 0 && addTextView == null) {
                                        if (tagRoot.findViewWithTag(-1) != null) {
                                            tagRoot.findViewWithTag(-1).performClick();
                                        }
//                                        addListener.onClick(tagRoot.findViewWithTag(-1));
                                    }
                                }
                            }
                        });
                        animator.start();
                    }
                    else {
                        expandedTag.findViewById(R.id.TagText).setAlpha(1f);
                        expandedTag.findViewById(R.id.TagColor).setRotation(expandedTag.findViewById(R.id.TagColor).getRotation() + 45);
                        ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusOpacity(1f);
                    }

                }
            }
        });
    }

    public void setOnCloseListener(OnCloseListener listener) {
        onCloseListener = listener;
    }

    public void setOnTagDeletedListener(OnTagDeletedListener listener) {
        onTagDeletedListener = listener;
    }

    static class SelectableArrayAdapter<T> extends ArrayAdapter<T> {
        public Tag selection = null;

        public void setSelection(Tag selection) {
            this.selection = selection;
        }

        public SelectableArrayAdapter(Context context, int i, int j) {
            super(context, i, j);
        }
    }

    static class PersistentlyEqualsTag extends Tag {
        public String stringRepresentation;

        public String toString() {
            return stringRepresentation;
        }
    }

    class AutoCompleteTextViewWithSwappableAdapters extends AutoCompleteTextView {
        ListAdapter backgroundAdapter;

        private boolean layoutComplete = false;

        AutoCompleteTextViewWithSwappableAdapters(Context context) { super(context); }

        @Override
        public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (isPopupShowing()) {
                if (keyCode == KeyEvent.KEYCODE_BACK) {
                    super.dismissDropDown();
                    return true;
                }
            }
            return super.onKeyPreIme(keyCode, event);
        }

        @Override
        public boolean enoughToFilter() {
            return true;
        }

        @Override
        public <T extends ListAdapter & Filterable> void setAdapter(T adapter) {
            backgroundAdapter = getAdapter();
            super.setAdapter(adapter);
        }

        public void swapAdapters() {
            if (backgroundAdapter.getClass() == ColorAdapter.class)
                setAdapter((ColorAdapter)backgroundAdapter);
            else
                setAdapter((ArrayAdapter<?>) backgroundAdapter);
        }

        public void onSizeChanged(int w, int h, int oldW, int oldH) {
            if (getWidth() > 0 && getHeight() > 0) {
                layoutComplete = true;
            }
            super.onSizeChanged(w, h, oldW, oldH);
        }

        public void requestLayout() {
            if (!listViewParent || !layoutComplete) {
                // Request layout doesn't play nicely with listView; block them once the initial layout is complete
                super.requestLayout();
            }
        }

        public void superRequestLayout() {
            if (listViewParent) {
                super.requestLayout();
            }
        }

//        public void showDropDown() {
//            post(new Runnable() {
//                @Override
//                public void run() {
//                    AutoCompleteTextViewWithSwappableAdapters.super.showDropDown();
//                }
//            });
//        }

        public void dismissDropDown() {
            if (!listViewParent) super.dismissDropDown();
        }

        public void superDismissDropDown() {
            super.dismissDropDown();
        }

    }

    protected AutoCompleteTextViewWithSwappableAdapters getAddTextView() {
        return addTextView;
    }

    private SwipeToDeleteListener tagDeleter;

    public void dismissDeleteActionBar() {
        dismissDeleteActionBar(true);
    }

    public void dismissDeleteActionBar(boolean animated) {
        dismissDeleteActionBar(animated, true);
    }

    public void dismissDeleteActionBar(boolean animated, boolean detachActionBar) {
        if (deleteActionBar == null) return;

        final ViewGroup previousContainer = deleteActionBar.getContainer();
        if (animated) {
            previousContainer.getChildAt(0).animate().alpha(1f).translationX(0f).setDuration(200);
            if (detachActionBar)
                previousContainer.getChildAt(1).animate().alpha(0f).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        previousContainer.removeViewAt(1);
                        ViewCompat.setHasTransientState(previousContainer, false);
                    }
                });
        }
        else {
            previousContainer.getChildAt(0).setAlpha(1f);
            previousContainer.getChildAt(0).setTranslationX(0f);
            if (detachActionBar) {
                previousContainer.removeViewAt(1);
                ViewCompat.setHasTransientState(previousContainer, false);
            }
        }
        deleteActionBar.getActivity().getFragmentManager().beginTransaction().remove(deleteActionBar).commit();
        deleteActionBar = null;
    }

    private View.OnClickListener addListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {

            LayoutTransition transition = tagRoot.getLayoutTransition();
            tagRoot.setLayoutTransition(null);

            final View.OnClickListener itemClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    addTextView.setText(((Tag) view.getTag()).name);
                    completeAdd();
                }
            };

            if (tagDeleter == null) {
                tagDeleter = new SwipeToDeleteListener(view.getContext());
                tagDeleter.setOnMoveListener(new SwipeToDeleteListener.OnMoveListener() {
                    @Override
                    public void onMove(View view, float distance, boolean initial) {
                        view.setX(view.getX() + distance);
                        if (initial) {
                            ViewCompat.setHasTransientState((View) view.getParent(), true);
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                            view.setBackgroundColor(0);
                            view.setOnClickListener(null);
                            view.setClickable(false);
                        }
                    }
                });
                tagDeleter.setOnReleaseListener(new SwipeToDeleteListener.OnReleaseListener() {
                    @Override
                    public void onRelease(final View view) {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        view.animate().translationX(0f).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                view.animate().setListener(null);

                                ViewCompat.setHasTransientState((View) view.getParent(), false);
                                view.setPressed(false);
                                view.setClickable(true);
                                view.setOnClickListener(itemClickListener);
                                if (view.isSelected()) {
                                    view.setBackgroundResource(R.drawable.selected_scrap);
                                }
                                else {
                                    view.setBackgroundResource(R.drawable.unselected_scrap);
                                }
                            }
                        }).setDuration(200);
                    }
                });
                tagDeleter.setOnDeleteListener(new SwipeToDeleteListener.OnDeleteListener() {
                    @Override
                    public void onDelete(final View view, float velocity, float velocityRatio) {
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        float dr = view.getWidth() - view.getTranslationX();
                        float distanceRatio = dr / view.getWidth();
                        if (distanceRatio < 0) distanceRatio = 0.01f;
                        float timeRatio = Math.signum(velocityRatio) * 300 * distanceRatio * velocityRatio;
                        if (timeRatio > 300f) timeRatio = 300f;
                        if (timeRatio < 100f) timeRatio = 100f;

                        view.animate().translationX(Math.signum(velocityRatio) * view.getWidth())
                                .setInterpolator(new DecelerateInterpolator(1.5f))
                                .setDuration((long)(timeRatio))
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        view.animate().setListener(null);

                                        view.setPressed(false);
                                        view.setClickable(true);
                                        view.setOnClickListener(itemClickListener);
                                        if (view.isSelected()) {
                                            view.setBackgroundResource(R.drawable.selected_scrap);
                                        }
                                        else {
                                            view.setBackgroundResource(R.drawable.unselected_scrap);
                                        }
                                    }
                                });


                        if (deleteActionBar != null) {
                            dismissDeleteActionBar();
                        }

                        Activity context = (Activity) view.getContext();
                        deleteActionBar = LegacyActionBar.getAttachableLegacyActionBar();
//                        deleteActionBar.setBackgroundColor(context.getResources().getColor(R.color.DeleteStripBackground));
//                        deleteActionBar.setTextColor(context.getResources().getColor(android.R.color.white));
                        int color = ((TagView) view.findViewById(R.id.TagColor)).getColors().get(0);
                        deleteActionBar.setBackgroundColor(color);
                        deleteActionBar.setTextColor(TagStorage.getSuggestedTextColor(color));
                        deleteActionBar.setCaretResource(0);
                        deleteActionBar.setBackButtonEnabled(false);
                        deleteActionBar.setFillContainerEnabled(true);

                        deleteActionBar.setTitle(context.getString(R.string.ConfirmTagDelete));
                        deleteActionBar.addItem(R.id.ConfirmCancel, context.getString(R.string.CancelButtonLabel), 0, true, true);
                        deleteActionBar.addItem(R.id.ConfirmOK, context.getString(R.string.ActionDelete), 0, true, true);

                        deleteActionBar.setOnLegacyActionSeletectedListener(new LegacyActionBar.OnLegacyActionSelectedListener() {
                            @Override
                            public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
                                if (item.getId() == R.id.ConfirmOK) {
                                    deleteColor(deleteActionBar.getBackgroundColor());
                                    dismissDeleteActionBar(false, false);
                                }
                                else {
                                    dismissDeleteActionBar();
                                }
                            }
                        });

                        context.getFragmentManager().beginTransaction().add(deleteActionBar, DeleteActionBarKey).commit();
                        deleteActionBar.setContainer((ViewGroup) view.getParent());

                        view.post(new Runnable() {
                            @Override
                            public void run() {
                                ((ViewGroup) view.getParent()).getChildAt(1).setAlpha(0f);
                                ((ViewGroup) view.getParent()).getChildAt(1).animate().alpha(1f);
                            }
                        });

                    }
                });

                // Because the popup is a fixed 250dp
                tagDeleter.setMinimumSwipeDistance(100f * metrics.density);
            }

            addTextView = new AutoCompleteTextViewWithSwappableAdapters(view.getContext());

            final View.OnClickListener createTagClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    setupTag(((Tag) view.getTag()).name);
                }
            };

            final AbsListView.OnScrollListener OnScrollListener = new AbsListView.OnScrollListener() {
                @Override
                public void onScrollStateChanged(AbsListView absListView, int i) {
                    if (i == AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL && deleteActionBar != null) {
                        dismissDeleteActionBar();
                    }
                }

                @Override
                public void onScroll(AbsListView absListView, int i, int i2, int i3) {
                }
            };

            final PersistentlyEqualsTag persistentlyEqualsTag = new PersistentlyEqualsTag();
            persistentlyEqualsTag.color = -1;

            final SelectableArrayAdapter<ItemCollectionFragment.Tag> adapter = new SelectableArrayAdapter<ItemCollectionFragment.Tag>(view.getContext(), R.layout.tag, R.id.TagText) {

                @Override
                public View getView(int position, View convertView, ViewGroup container) {
//                    View view = super.getView(position, convertView, container);
                    FrameLayout viewContainer = convertView != null ? (FrameLayout) convertView : new FrameLayout(container.getContext());
                    View view;
                    if (convertView == null) {
                        view = LayoutInflater.from(container.getContext()).inflate(R.layout.tag, viewContainer, false);
                        viewContainer.addView(view);
                    }
                    else {
                        view = viewContainer.getChildAt(0);
                    }

                    ((TextView) view.findViewById(R.id.TagText)).setText(getItem(position).name);
                    ((TagView) view.findViewById(R.id.TagColor)).setColor(getItem(position).color);

                    if (selection == getItem(position)) {
                        view.setSelected(true);
                        //noinspection deprecation
                        view.setBackgroundDrawable(container.getResources().getDrawable(R.drawable.selected_scrap));
                    }
                    else {
                        view.setSelected(false);
                        //noinspection deprecation
                        view.setBackgroundDrawable(container.getResources().getDrawable(R.drawable.unselected_scrap));
                    }

                    view.setTag(getItem(position));
                    view.setOnClickListener(itemClickListener);

                    if (getItem(position) == persistentlyEqualsTag) {
                        view.setOnClickListener(createTagClickListener);
                        view.setOnTouchListener(null);
                    }
                    else {
                        if (readWrite) view.setOnTouchListener(tagDeleter);
                    }

                    if (target.tags.contains(getItem(position))) {
                        view.setEnabled(false);
                        view.setAlpha(0.5f);
                    }
                    else {
                        view.setEnabled(true);
                        view.setAlpha(1f);
                    }

                    ((AbsListView) container).setOnScrollListener(OnScrollListener);

                    return viewContainer;
                }
            };

            // First set up the colors adapter; this will be replaced immediately after by the search adapter
            final ArrayList<Integer> colors = TagStorage.getAllAvailableColors(addTextView.getResources());

            final ColorAdapter colorAdapter = new ColorAdapter(colors);
            colorAdapter.selection = TagStorage.getNextAvailableColor();
            colorAdapter.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    colorAdapter.selection = ((TagView) view).getColors().get(0);
                    finishCreatingTag();
                }
            });

            addTextView.setAdapter(colorAdapter);

            // replace it with the searchadapter
            addTextView.setAdapter(adapter);

            // the default tags are actually all the available tags
            adapter.addAll(TagStorage.getDefaultTags(view.getResources()));
            for (Tag tag : target.tags) {
                adapter.remove(tag);
                adapter.add(tag);
            }

            RelativeLayout.LayoutParams params;
            if (true) {
                params = new RelativeLayout.LayoutParams(0, view.getHeight());
                params.addRule(RelativeLayout.ALIGN_LEFT, view.getId());
                params.addRule(RelativeLayout.ALIGN_RIGHT, view.getId());
            }
            else {
                // The layoutParams will always mirror those of the addButton; however, can't use the same instance of LayoutParams
                // as the RelativeLayout keeps positioning info inside the LayoutParams
                params = (RelativeLayout.LayoutParams) view.getLayoutParams();
            }

            addTextView.setLayoutParams(params);
            addTextView.setMinWidth(view.getWidth());
            addTextView.setHint("Add Tag"); //TODO;
            addTextView.setThreshold(0);
            addTextView.setMaxLines(1);
            addTextView.setSingleLine();
            addTextView.setInputType(InputType.TYPE_TEXT_FLAG_CAP_WORDS);
            addTextView.setImeOptions(EditorInfo.IME_FLAG_NO_EXTRACT_UI | EditorInfo.IME_ACTION_DONE);
            addTextView.setGravity(Gravity.CENTER_VERTICAL);
            addTextView.setDropDownWidth((int) (250 * metrics.density));
            addTextView.setCompoundDrawablesWithIntrinsicBounds(inverted ? R.drawable.search_hint_light : R.drawable.search_hint, 0, 0, 0);
            addTextView.setPadding((int) (4 * metrics.density), addTextView.getPaddingTop(), addTextView.getPaddingRight(), addTextView.getPaddingBottom());
            addTextView.setCompoundDrawablePadding((int) (4 * metrics.density));
            if (inverted) addTextView.setTextColor(addTextView.getResources().getColor(android.R.color.white));
            else addTextView.setTextColor(addTextView.getResources().getColor(R.color.ItemText));
            if (inverted) addTextView.setHintTextColor(0x88FFFFFF);
            addTextView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (deleteAnimator != null) {
                        deleteAnimator.cancel();
                    }

                    if (i == EditorInfo.IME_ACTION_DONE || i == EditorInfo.IME_ACTION_NEXT) {
                        if (addTextView.getAdapter().getClass() != ColorAdapter.class) {
                            if (adapter.selection != null) {
                                if (adapter.selection == persistentlyEqualsTag) {
                                    setupTag(textView.getText().toString().trim());
                                    return true;
                                }
                                if (!target.tags.contains(adapter.selection)) {
                                    completeAdd();
                                }
                            }
                            else {
                                completeAdd();
                            }
                        }
                        else {
                            // If the popup is dismissed, bring it up again, allowing the user to select their color
                            if (addTextView.isPopupShowing())
                                finishCreatingTag();
                            else
                                addTextView.showDropDown();
                        }
                        return true;
                    }
                    return false;
                }
            });

            addTextView.setTag(view);

            tagRoot.addView(addTextView);
            view.setVisibility(View.INVISIBLE);
            tagRoot.setLayoutTransition(transition);

            if (itemTarget && collectionViewParent) {
                final View collectionContainer = (View) container.getParent();
                ((ViewGroup.MarginLayoutParams) collectionContainer.getLayoutParams()).topMargin = (int) collectionContainer.getY();
                collectionContainer.requestLayout();
                collectionContainer.setTranslationY(0);

                // container -> collectionView wrapper -> collectionView container -> collectionView
                final CollectionView collectionView = (CollectionView) container.getParent().getParent().getParent();
                collectionView.setScrollOnFocus(false);
            }

            addTextView.setText("");

            addTextView.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                    // Make sure searches trigger the search adapter and never happen inside the color adapter
                    if (addTextView.getAdapter().getClass() == ColorAdapter.class) {
                        addTextView.swapAdapters();
                    }
                    dismissDeleteActionBar(false);
                    if (deleteAnimator != null) {
                        deleteAnimator.cancel();
                    }
                }

                @Override
                public void onTextChanged(final CharSequence charSequence, int j, int i2, int i3) {
                    adapter.setSelection(null);
                    adapter.remove(persistentlyEqualsTag);

                    String trimmedString = charSequence.toString().trim();

                    boolean addPersistentlyEquals = true;
                    boolean exactMatchFound = false;

                    ArrayList<Tag> tags = TagStorage.getDefaultTags(null);

                    if (!TextUtils.isEmpty(trimmedString)) for (int i = tags.size() - 1; i >= 0; i--) {
                        Tag tag = tags.get(i);
                        if (!exactMatchFound && tag.name.toLowerCase().startsWith(trimmedString.toLowerCase())) {
                            adapter.setSelection(tag);
                        }
                        if (tag.name.equalsIgnoreCase(trimmedString)) {
                            exactMatchFound = true;
                            adapter.setSelection(tag);
                            addPersistentlyEquals = false;
                        }
                    }

                    if (TagStorage.canCreateTags() && addPersistentlyEquals && !trimmedString.isEmpty() && readWrite) {
                        adapter.add(persistentlyEqualsTag);
                        persistentlyEqualsTag.name = "Create tag: " + trimmedString;
                        persistentlyEqualsTag.stringRepresentation = charSequence.toString();
                        if (adapter.selection == null) {
                            adapter.setSelection(persistentlyEqualsTag);
                            addTextView.setCompoundDrawablesWithIntrinsicBounds(inverted ? R.drawable.add_hint_light : R.drawable.add_hint, 0, 0, 0);
                            addTextView.setPadding((int) (4 * metrics.density), addTextView.getPaddingTop(), addTextView.getPaddingRight(), addTextView.getPaddingBottom());
                            addTextView.setCompoundDrawablePadding((int) (4 * metrics.density));
                        }
                    } else {
                        addTextView.setCompoundDrawablesWithIntrinsicBounds(inverted ? R.drawable.search_hint_light : R.drawable.search_hint, 0, 0, 0);
                        addTextView.setPadding((int) (4 * metrics.density), addTextView.getPaddingTop(), addTextView.getPaddingRight(), addTextView.getPaddingBottom());
                        addTextView.setCompoundDrawablePadding((int) (4 * metrics.density));
                    }

                    adapter.notifyDataSetChanged();
                }

                @Override
                public void afterTextChanged(Editable editable) {
                }
            });

            addTextView.requestFocus();

            if (adapter.getCount() == 0) {
                InputMethodManager imm = (InputMethodManager) addTextView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.showSoftInput(addTextView, InputMethodManager.SHOW_IMPLICIT);
            }

            addTextView.showDropDown();

        }
    };

    private View.OnClickListener popoverAddListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            addTagUsingPopover();
        }
    };

    private Popover.AnchorProvider addAnchor = new Popover.AnchorProvider() {
        @Override
        public View getAnchor(Popover popover) {
            if (tagRoot != null) {
                return tagRoot.findViewWithTag(-1);
            }
            return null;
        }
    };

    final static int ViewTypeTag = 0;
    final static int ViewTypeColor = 1;
    static class TagController extends CollectionViewController {
        private TagExpander expander;

        private View.OnClickListener tagClickListener;
        private View.OnClickListener createTagClickListener;
        private View.OnClickListener finishAddingTagClickListener;
        private View.OnLongClickListener tagLongClickListener;

        private Tag confirmingTag;
        private ArrayList<Tag> selection = new ArrayList<Tag>();

        private LegacyActionBar header;
        private LegacyActionBar.ContextBarWrapper selectionWrapper;
        private LegacyActionBar.ContextBarListener selectionListener = new LegacyActionBar.ContextBarListenerAdapter() {
            public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
                // TODO
            }

            public void onContextBarDismissed() {
                selection.clear();

                selectionWrapper = null;

                getCollectionView().refreshViews();
            }
        };

        public void setHeader(LegacyActionBar header) {
            this.header = header;
        }

        public TagController() {

            tagLongClickListener = new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Tag tag = (Tag) getObjectForView(v);
                    int direction = 0;
                    if (selection.contains(tag)) {
                        selection.remove(tag);

                        direction = -1;
                    }
                    else {
                        if (selection.size() == 0) {
                            selectionWrapper = header.createContextMode(selectionListener);
                            selectionWrapper.setBackgroundColor(getCollectionView().getResources().getColor(R.color.SelectionBar));
                            selectionWrapper.setTextColor(0xFFFFFFFF);
                            selectionWrapper.setDoneResource(R.drawable.ic_action_done);

                            selectionWrapper.addItem(R.id.menu_delete, getCollectionView().getResources().getString(R.string.DeleteLabel), R.drawable.ic_action_delete, false, true);

                            selectionWrapper.start();
                        }

                        selection.add(tag);

                        direction = 1;
                    }

                    if (selection.size() == 0) {
                        selectionWrapper.dismiss();
                    }

                    if (selectionWrapper != null) selectionWrapper.setTitleAnimated(Utils.appendWithSpan(new SpannableStringBuilder(), selection.size() + " selected", new AbsoluteSizeSpan(20, true)), direction);

                    requestConfigureView(v, tag, ViewTypeTag);

                    return true;
                }
            };

        }

        public View createEmptyView(ViewGroup container, LayoutInflater inflater) {
            View view = inflater.inflate(R.layout.layout_empty, container, false);
            ((TextView) view.findViewById(R.id.EmptyText)).setTypeface(Receipt.condensedTypeface());
            ((TextView) view.findViewById(R.id.EmptyText)).setText("No Tags");
            ((RelativeLayout.LayoutParams) view.findViewById(R.id.EmptyText).getLayoutParams()).addRule(RelativeLayout.CENTER_IN_PARENT);
            view.findViewById(R.id.EmptyImage).setVisibility(ViewGroup.GONE);
            view.getLayoutParams().height = (int) (container.getResources().getDisplayMetrics().density * 128 + 0.5f);
            return view;
        }

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            if (viewType == ViewTypeTag) {
                View view = inflater.inflate(R.layout.tag, container, false);
                view.findViewById(R.id.TagColor).getLayoutParams().width = container.getResources().getDimensionPixelSize(R.dimen.KeylineIcon); //(int) (48 * container.getResources().getDisplayMetrics().density + 0.5f);
                ((ViewGroup.MarginLayoutParams) view.findViewById(R.id.TagColor).getLayoutParams()).leftMargin /*-*/= container.getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline);//(int) (4 * container.getResources().getDisplayMetrics().density + 0.5f);
                ((ViewGroup.MarginLayoutParams) view.findViewById(R.id.TagColor).getLayoutParams()).rightMargin = container.getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline);
                ((TextView) view.findViewById(R.id.TagText)).setTextColor(container.getResources().getColor(R.color.DashboardText));
                ((ViewGroup.MarginLayoutParams) view.findViewById(R.id.TagText).getLayoutParams()).leftMargin = 0;

                view.setBackground(Utils.getDeselectedColors(view.getContext()));
                if (true) {
                    EventTouchListener listener = EventTouchListener.listenerInContext(container.getContext());

                    listener.setDelegate(new EventTouchListener.EventDelegate() {
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
                            if (selection.size() == 0) {
                                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                getCollectionView().requestDisableInteractions();
                                return true;
                            }

                            return false;
                        }

                        @Override
                        public void viewDidMove(EventTouchListener listener, View view, float distance) {
                            view.setTranslationX(view.getTranslationX() + distance);

                            float distanceRatio = Math.abs(view.getTranslationX() / getSwipeDistanceThreshold());

                            if (distanceRatio > 1) distanceRatio = 1;
                            distanceRatio = Utils.interpolateValues(distanceRatio, 1, 0.2f);

                            view.setAlpha(distanceRatio);
                        }

                        @Override
                        public void viewDidBeginSwiping(EventTouchListener listener, final View view, float velocity) {
                            Tag tag = (Tag) getCollectionView().getObjectForView(view);

                            if (tag == null) {
                                viewDidCancelSwiping(listener, view);
                                return;
                            }
                            getCollectionView().requestEnableInteractions();

                            // The view will continue to move with constant speed
                            if (velocity == 0) {
                                velocity = EventTouchListener.sgn(view.getTranslationX());
                            }

                            float totalDistance =  getCollectionView().getWidth() - Math.abs(view.getTranslationX());
                            long timeRequired = (long) (totalDistance / Math.abs(velocity));
                            if (timeRequired > 300) {
                                timeRequired = 300;
                            }
                            if (timeRequired < 100) {
                                timeRequired = 100;
                            }

                            final float StartingAlpha = view.getAlpha();
                            final float StartingTranslation = view.getTranslationX();
                            final float Velocity = velocity;
                            final float TotalDistance = totalDistance;


                            final Tag PreviousConfirmingTag = confirmingTag;
                            confirmingTag = tag;
                            getCollectionView().retainView(view);

                            view.animate().alpha(0f).translationXBy(EventTouchListener.sgn(Velocity) * TotalDistance)
                                    .setDuration(timeRequired)
                                    .setInterpolator(new LinearInterpolator())
                                    .withEndAction(new Runnable() {
                                        @Override
                                        public void run() {
                                            view.setLayerType(View.LAYER_TYPE_NONE, null);
                                            // TODO
//                                        view.setAlpha(1f);
                                            view.animate().setInterpolator(new AccelerateDecelerateInterpolator());
                                            view.setTranslationX(0f);
                                            if (getCollectionView() != null) {
                                                if (PreviousConfirmingTag != null) {
                                                    removeSetupConfirmator((ViewGroup) getCollectionView().getViewForObject(PreviousConfirmingTag), true);
                                                }

                                                setupConfirmator((ViewGroup) view, true);
                                            }
                                        }
                                    });
                        }

                        @Override
                        public void viewDidCancelSwiping(EventTouchListener listener, final View View) {
                            getCollectionView().requestEnableInteractions();
                            View.animate().alpha(1f).translationX(0f).withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    View.setLayerType(android.view.View.LAYER_TYPE_NONE, null);
                                }
                            });
                        }

                        @Override
                        public int getSwipeDistanceThreshold() {
                            return (getCollectionView().getWidth() * 2) / 3; // TODO
                        }
                    });

                    ((LegacyRippleDrawable) view.getBackground()).setForwardListener(listener);
                }

                return view;
            }
            else if (viewType == ViewTypeColor) {
                final ViewGroup ColorLayout = (ViewGroup) inflater.inflate(R.layout.tag_color_chooser, container, false);
                final Resources Res = ColorLayout.getResources();

                $.find(ColorLayout, R.id.LightTag, R.id.MediumTag, R.id.DarkTag).each((view, index) -> view.setBackground(new LegacyRippleDrawable(view.getContext())));

                PrecisionRangeSlider hueSlider = ((PrecisionRangeSlider) ColorLayout.findViewById(R.id.HueSlider));
                hueSlider.setColorIntervals(
                        Res.getColor(R.color.TagRed),
                        Res.getColor(R.color.TagOrange),
                        Res.getColor(R.color.TagGreen),
                        Res.getColor(R.color.TagBlue),
                        Res.getColor(R.color.TagPurple),
                        Res.getColor(R.color.TagBlack)
                );

                final String[] ColorNames = new String[] {
                        Res.getString(R.string.Red),
                        Res.getString(R.string.Yellow),
                        Res.getString(R.string.Green),
                        Res.getString(R.string.Blue),
                        Res.getString(R.string.Purple),
                        Res.getString(R.string.Gray)
                };

                final int[] ColorTable[] = new int[][] {
                        new int[] {Res.getColor(R.color.TagLightRed), Res.getColor(R.color.TagRed), Res.getColor(R.color.TagDarkRed)},
                        new int[] {Res.getColor(R.color.TagLightOrange), Res.getColor(R.color.TagOrange), Res.getColor(R.color.TagDarkOrange)},
                        new int[] {Res.getColor(R.color.TagLightGreen), Res.getColor(R.color.TagGreen), Res.getColor(R.color.TagDarkGreen)},
                        new int[] {Res.getColor(R.color.TagLightBlue), Res.getColor(R.color.TagBlue), Res.getColor(R.color.TagDarkBlue)},
                        new int[] {Res.getColor(R.color.TagLightPurple), Res.getColor(R.color.TagPurple), Res.getColor(R.color.TagDarkPurple)},
                        new int[] {Res.getColor(R.color.TagWhite), Res.getColor(R.color.TagGray), Res.getColor(R.color.TagBlack)}
                };

                hueSlider.setOnRangeChangeListener(new PrecisionRangeSlider.OnRangeChangedListener() {
                    @Override
                    public void onRangeChange(float fromRange, float toRange, boolean fromUser) {
                        final int Index = (int) Utils.constrain(toRange, 0, 5);

                        ((TagView) ColorLayout.findViewById(R.id.LightTag)).setColor(ColorTable[Index][0]);
                        ((TagView) ColorLayout.findViewById(R.id.MediumTag)).setColor(ColorTable[Index][1]);
                        ((TagView) ColorLayout.findViewById(R.id.DarkTag)).setColor(ColorTable[Index][2]);

                        if (TagStorage.isColorAvailable(ColorTable[Index][0])) {
                            ((TagView) ColorLayout.findViewById(R.id.LightTag)).setColorMode(TagView.ColorModeFill);
                            ColorLayout.findViewById(R.id.LightTag).setEnabled(true);
                        }
                        else {
                            ((TagView) ColorLayout.findViewById(R.id.LightTag)).setColorMode(TagView.ColorModeDashed);
                            ColorLayout.findViewById(R.id.LightTag).setEnabled(false);
                        }

                        if (TagStorage.isColorAvailable(ColorTable[Index][1])) {
                            ((TagView) ColorLayout.findViewById(R.id.MediumTag)).setColorMode(TagView.ColorModeFill);
                            ColorLayout.findViewById(R.id.MediumTag).setEnabled(true);
                        }
                        else {
                            ((TagView) ColorLayout.findViewById(R.id.MediumTag)).setColorMode(TagView.ColorModeDashed);
                            ColorLayout.findViewById(R.id.MediumTag).setEnabled(false);
                        }

                        if (TagStorage.isColorAvailable(ColorTable[Index][2])) {
                            ((TagView) ColorLayout.findViewById(R.id.DarkTag)).setColorMode(TagView.ColorModeFill);
                            ColorLayout.findViewById(R.id.DarkTag).setEnabled(true);
                        }
                        else {
                            ((TagView) ColorLayout.findViewById(R.id.DarkTag)).setColorMode(TagView.ColorModeDashed);
                            ColorLayout.findViewById(R.id.DarkTag).setEnabled(false);
                        }
                    }

                    @Override
                    public void onRangeSelected(float fromRange, float toRange, boolean fromUser) {
                        onRangeChange(fromRange, toRange, fromUser);
                    }
                });

                hueSlider.setPopupListener(new PrecisionRangeSlider.PopupListener() {
                    @Override
                    public CharSequence getPopupLabel(float percent) {
                        int value = (int) Utils.constrain(percent, 0, 5);
                        return ColorNames[value];
                    }
                });
                hueSlider.setEndPosition(0.33f, false);

                ((TagView) ColorLayout.findViewById(R.id.LightTag)).setRadius(16);
                ((TagView) ColorLayout.findViewById(R.id.MediumTag)).setRadius(16);
                ((TagView) ColorLayout.findViewById(R.id.DarkTag)).setRadius(16);

                ColorLayout.findViewById(R.id.LightTag).setOnClickListener(finishAddingTagClickListener);
                ColorLayout.findViewById(R.id.MediumTag).setOnClickListener(finishAddingTagClickListener);
                ColorLayout.findViewById(R.id.DarkTag).setOnClickListener(finishAddingTagClickListener);

                return ColorLayout;
            }
            return null;
        }

        @Override
        public void configureView(final View view, final Object item, int viewType) {
            if (viewType == ViewTypeTag) {
                final Tag tag = (Tag) item;
                view.setTag(tag);

                if (tag == confirmingTag) {
                    if (view.findViewById(R.id.ConfirmOK) == null) {
                        getCollectionView().post(new Runnable() {
                            @Override
                            public void run() {
                                if (getCollectionView() != null)
                                    getCollectionView().retainView(view);
                            }
                        });
                        setupConfirmator((ViewGroup) view, false);
                    }
                }

                ((TagView) view.findViewById(R.id.TagColor)).setColor(tag.color);
                ((TextView) view.findViewById(R.id.TagText)).setText(tag.name);

                if (tag.color != -1) {
                    view.setOnClickListener(tagClickListener);
                    if (expander.readWrite) {
                        view.setOnLongClickListener(tagLongClickListener);
                    }
                }
                else {
                    view.setOnClickListener(createTagClickListener);
                    view.setOnLongClickListener(null);
                }

                if (expander.target.tags.contains(item)) {
                    view.setEnabled(false);
                    ((TagView) view.findViewById(R.id.TagColor)).setColorMode(TagView.ColorModeDashed);
                    ((TextView) view.findViewById(R.id.TagText)).setTextColor(view.getResources().getColor(R.color.DashboardTitle));
                }
                else {
                    view.setEnabled(true);
                    ((TagView) view.findViewById(R.id.TagColor)).setColorMode(TagView.ColorModeFill);
                    ((TextView) view.findViewById(R.id.TagText)).setTextColor(view.getResources().getColor(R.color.DashboardText));
                }

                if (item == selectedTag) {
                    if (!isRefreshingViews() && !view.isSelected()) {
                        ((LegacyRippleDrawable) view.getBackground()).dismissPendingAnimation();
                    }
                    view.setSelected(true);
                }
                else {
                    if (!isRefreshingViews() && view.isSelected()) {
                        ((LegacyRippleDrawable) view.getBackground()).dismissPendingAnimation();
                    }
                    view.setSelected(false);
                }

                // Technically contains can be a lengthy operation, but the tag list is limited to 16 objects max
                if (view.isSelected() != selection.contains(item)) {
                    if (!isRefreshingViews()) {
                        ((LegacyRippleDrawable) view.getBackground()).dismissPendingAnimation();
                    }
                    view.setSelected(selection.contains(item));
                }

                SwipeToDeleteListener tagDeleter = new SwipeToDeleteListener(view.getContext().getApplicationContext());
                tagDeleter.setEnabledListener(new SwipeToDeleteListener.EnabledListener() {
                    @Override
                    public boolean isEnabled() {
                        return !expander.target.tags.contains(item) && tag.color != -1;
                    }
                });
                tagDeleter.setMinimumSwipeDistance(view.getResources().getDisplayMetrics().density * 128);
                tagDeleter.setOnMoveListener(new SwipeToDeleteListener.OnMoveListener() {
                    @Override
                    public void onMove(View view, float distance, boolean initial) {
                        if (initial) {
                            view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                            view.setPressed(false);
                        }

                        view.setAlpha(1 - Utils.constrain(
                                2 * Utils.getIntervalPercentage(Math.abs(view.getTranslationX()), 0, view.getWidth()),
                                0f, 0.8f));
                        view.setTranslationX(view.getTranslationX() + distance);
                    }
                });
                tagDeleter.setOnReleaseListener(new SwipeToDeleteListener.OnReleaseListener() {
                    @Override
                    public void onRelease(final View view) {
                        getCollectionView().retainView(view);
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        view.setPressed(false);
                        view.animate().alpha(1f).translationX(0f).setDuration(200)
                                .setInterpolator(new DecelerateInterpolator(1.5f))
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        view.setPressed(false);
                                        view.setLayerType(View.LAYER_TYPE_NONE, null);
                                        if (getCollectionView() != null)
                                            getCollectionView().releaseView(view);
                                    }
                                });
                    }
                });
                tagDeleter.setOnDeleteListener(new SwipeToDeleteListener.OnDeleteListener() {
                    @Override
                    public void onDelete(final View view, float velocity, float velocityRatio) {
                        final Tag PreviousConfirmingTag = confirmingTag;
                        confirmingTag = tag;
                        getCollectionView().retainView(view);
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        view.setPressed(false);
                        float factor = velocity > 0 ? 1f : -1f;
                        view.animate().alpha(0f).translationX(view.getWidth() * factor)
                                .setDuration((long) Utils.constrain(600 / velocity, 150, 300))
                                .setInterpolator(new DecelerateInterpolator(1.5f))
                                .withEndAction(new Runnable() {
                                    @Override
                                    public void run() {
                                        view.setLayerType(View.LAYER_TYPE_NONE, null);
                                        // TODO
//                                        view.setAlpha(1f);
                                        view.setTranslationX(0f);
                                        if (getCollectionView() != null) {
                                            if (PreviousConfirmingTag != null) {
                                                removeSetupConfirmator((ViewGroup) getCollectionView().getViewForObject(PreviousConfirmingTag), true);
                                            }

                                            setupConfirmator((ViewGroup) view, true);
                                        }
                                    }
                                });
                    }
                });
                if (expander.readWrite && false) {
                    ((LegacyRippleDrawable) view.getBackground()).setForwardListener(tagDeleter);
                }
            }
            // Nothing to do here
        }

        public void setupConfirmator(final ViewGroup TagLayout, boolean animated) {
            Tag tag = (Tag) TagLayout.getTag();

            TagLayout.findViewById(R.id.TagColor).setVisibility(View.GONE);
            TagLayout.findViewById(R.id.TagText).setVisibility(View.GONE);

            ViewGroup confirmator = (ViewGroup) LayoutInflater.from(TagLayout.getContext()).inflate(R.layout.layout_actionbar_generic_confirmation, TagLayout, false);
            confirmator.setBackgroundColor(tag.color);
            ((TextView) confirmator.findViewById(R.id.ConfirmBack)).setTextColor(TagStorage.getSuggestedTextColor(tag.color));
            ((TextView) confirmator.findViewById(R.id.ConfirmOK)).setTextColor(TagStorage.getSuggestedTextColor(tag.color));
            ((TextView) confirmator.findViewById(R.id.ConfirmCancel)).setTextColor(TagStorage.getSuggestedTextColor(tag.color));
            TagLayout.addView(confirmator);

            confirmator.findViewById(R.id.ConfirmCancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    confirmingTag = null;

                    view.setClickable(false);
                    TagLayout.findViewById(R.id.ConfirmOK).setClickable(false);
                    removeSetupConfirmator(TagLayout, true);
                }
            });

            confirmator.findViewById(R.id.ConfirmOK).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (selectedTag == confirmingTag) selectedTag = null;
                    confirmingTag = null;

                    view.setClickable(false);
                    TagLayout.findViewById(R.id.ConfirmOK).setClickable(false);

                    if (expander != null) {
                        requestBeginTransaction();
                        getSectionAtIndex(0).removeObject(TagLayout.getTag());
                        expander.deleteTag((Tag) TagLayout.getTag());
                        requestCompleteTransaction();
                    }
                }
            });

            confirmator.findViewById(R.id.ConfirmCancel).setBackground(new LegacyRippleDrawable(confirmator.getContext(), LegacyRippleDrawable.ShapeCircle));
            confirmator.findViewById(R.id.ConfirmOK).setBackground(new LegacyRippleDrawable(confirmator.getContext(), LegacyRippleDrawable.ShapeCircle));

            confirmator.setClipChildren(false);

            if (animated) {
                TagLayout.animate().alpha(1f).withLayer().withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        TagLayout.setPressed(false);
                        TagLayout.setClickable(true);
                    }
                });
            }
            else {
                TagLayout.setAlpha(1f);
            }
        }

        public void removeSetupConfirmator(final ViewGroup view, boolean animated) {
            if (view == null) return;

            final View Confirmator = view.findViewById(R.id.Confirmator);
            if (animated) {
                Confirmator.animate().alpha(0f).withLayer().setDuration(200).withEndAction(new Runnable() {
                    @Override
                    public void run() {
                        ((ViewGroup) Confirmator.getParent()).removeView(Confirmator);
                        view.setTranslationX(view.getWidth());
                        view.setAlpha(0f);
                        view.findViewById(R.id.TagColor).setVisibility(View.VISIBLE);
                        view.findViewById(R.id.TagText).setVisibility(View.VISIBLE);
                        view.animate().alpha(1f).translationX(0f).withLayer().setDuration(200).withEndAction(new Runnable() {
                            @Override
                            public void run() {
                                if (getCollectionView() != null) {
                                    getCollectionView().releaseView(view);
                                }
                            }
                        });
                    }
                });
            }
            else {
                ((ViewGroup) Confirmator.getParent()).removeView(Confirmator);
                view.setAlpha(1f);
                view.setTranslationX(0f);
                getCollectionView().releaseView(view);
            }
        }

        protected void onAttachedToCollectionView(final CollectionView collectionView) {
            collectionView.setOnScrollListener(new CollectionView.OnScrollListener() {
                @Override
                public void onScroll(CollectionView collectionView, int top, int amount) {
                    if (confirmingTag != null) {
                        removeSetupConfirmator((ViewGroup) collectionView.getViewForObject(confirmingTag), true);
                        confirmingTag = null;
                    }
                }
            });
            collectionView.addTransactionListener(new CollectionView.TransactionListener() {
                @Override
                public void onTransactionStart() {
                    if (confirmingTag != null) {
                        removeSetupConfirmator((ViewGroup) collectionView.getViewForObject(confirmingTag), true);
                        confirmingTag = null;
                    }
                }

                @Override
                public void onTransactionEnd() {

                }
            });
        }

        public void setTagClickListener(final View.OnClickListener TagClickListener) {
            this.tagClickListener = new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (selection.size() > 0) {
                        tagLongClickListener.onLongClick(v);
                    }
                    else {
                        TagClickListener.onClick(v);
                    }
                }
            };
        }

        public void setCreateTagClickListener(View.OnClickListener createTagClickListener) {
            this.createTagClickListener = createTagClickListener;
        }

        public void setFinishAddingTagClickListener(View.OnClickListener finishAddingTagClickListener) {
            this.finishAddingTagClickListener = finishAddingTagClickListener;
        }

        public void setExpander(TagExpander expander) {
            this.expander = expander;
        }

    }

    class FinishAddingTagClickListener implements View.OnClickListener{

        @Override
        public void onClick(View view) {
            Tag tag = finishCreatingTag(searchString, ((TagView) view).getColors().get(0));
            addTag(tag);
            if (popover != null) {
                popover.dismiss();
            }
        }
    }

    final static int SearchDelayMS = 200;
    final static Handler SearchHandler = new Handler();

    final static int DashboardTitle = Receipt.getStaticContext().getResources().getColor(R.color.DashboardTitle);

    static Tag selectedTag;

    static String searchString;
    //noinspection unused
    static class AddInflater extends LegacyActionBar.ContextBarListenerAdapter implements LegacyActionBar.CustomViewProvider {
        TagExpander expander;
        LegacyActionBar actionBar;
        String localSearchString = "";
        WeakReference<ListenableEditText> addBox;
        boolean readWrite;
        int currentResource = R.drawable.ic_search_dark;
        Tag createTag; {
            createTag = new Tag();
            createTag.color = -1;
        }

        public AddInflater(TagExpander expander, LegacyActionBar actionBar, boolean readWrite) {
            this.expander = expander;
            this.actionBar = null;
            this.readWrite = readWrite;
        }

        public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
            View view = inflater.inflate(R.layout.tag_add_box, container, false);

            final ImageView HintImage = ((ImageView) view.findViewById(R.id.SearchIcon));
            final ListenableEditText AddBox = ((ListenableEditText) view.findViewById(R.id.AddBox));
            AddBox.setTextSize(20);
            addBox = new WeakReference<ListenableEditText>(AddBox);
            AddBox.setText(localSearchString);
            if (readWrite && TagStorage.canCreateTags()) {
                if (TagStorage.getDefaultTags(null).size() == 0) {
                    AddBox.setHint(Utils.echoText(AddBox.getResources().getString(R.string.SearchTagsWriteonly), DashboardTitle));
                    HintImage.setImageDrawable(HintImage.getResources().getDrawable(R.drawable.add_hint));
                    AddBox.requestFocus();
                    AddBox.post(new Runnable() {
                        @Override
                        public void run() {
                            InputMethodManager imm = (InputMethodManager) AddBox.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                            imm.showSoftInput(AddBox, InputMethodManager.SHOW_FORCED);
                        }
                    });
                }
                else {
                    AddBox.setHint(Utils.echoText(AddBox.getResources().getString(R.string.SearchTags), DashboardTitle));
                }
            }
            else {
                AddBox.setHint(Utils.echoText(AddBox.getResources().getString(R.string.SearchTagsReadonly), DashboardTitle));
            }
            AddBox.addTextChangedListener(new Utils.OnTextChangedListener() {

                @Override
                public void onTextChanged(final CharSequence Text, int i, int i2, int i3) {
                    TagExpander.searchString = Text.toString().trim();
                    localSearchString = searchString;
                    final String SearchString = TagExpander.searchString.toLowerCase();
                    createTag.name = AddBox.getResources().getString(R.string.CreateTag, Text);

                    int icon = R.drawable.ic_search_dark;
                    if (TextUtils.isEmpty(searchString)) {
                        selectedTag = null;
                    }
                    else {
                        selectedTag = TagStorage.findTag(SearchString);
                        if (selectedTag == null) {
                            icon = R.drawable.add_hint;
                            selectedTag = createTag;
                        }
                        else if (selectedTag.name.equalsIgnoreCase(searchString)) createTag.name = "";
                    }

                    if (currentResource != icon) {
                        currentResource = icon;
                        if (!expander.readWrite) icon = R.drawable.ic_search_dark;
                        HintImage.setImageDrawable(HintImage.getResources().getDrawable(icon));
                    }

                    // TODO possible crash
                    if (expander == null) return;
                    if (expander.popover == null) return;
                    expander.popover.getController().getCollectionView().refreshViews();

                    SearchHandler.removeCallbacksAndMessages(null);
                    SearchHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (expander != null && expander.popover != null) {
                                expander.popover.getController().requestBeginTransaction();

                                ArrayList<Tag> results = new ArrayList<Tag>();
                                int exactMatch = TagStorage.getFilteredTags(SearchString, results);

                                for (Tag tag : expander.target.tags) {
                                    if (results.contains(tag)) {
                                        results.remove(tag);
                                        results.add(tag);
                                    }
                                }

                                if (exactMatch == -1 && !TextUtils.isEmpty(SearchString) && readWrite && TagStorage.canCreateTags()) {
                                    results.add(createTag);
                                }

                                expander.popover.getController().getCollectionView().setMoveWithLayersEnabled(false);
                                expander.popover.getController().getSectionAtIndex(0).clear().addAllObjects(results);
                                expander.popover.getController().requestCompleteTransaction();
                            }
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
                    if (currentResource == R.drawable.add_hint) {
                        if (expander != null && expander.readWrite) {
                            expander.createTagClickListener.onClick(AddBox);
                        }
                    }
                    else {
                        if (expander != null) {
                            if (selectedTag == null) return;
                            for (Tag tag : expander.target.tags) {
                                if (selectedTag == tag) return;
                            }
                            if (expander.popover != null) expander.popover.dismiss();
                            expander.addTag(selectedTag);
                        }
                    }
                }
            });

            return view;
        }

        public void dismissCreateTagScreen() {

        }

        public void onDestroyCustomView(View customView) {
            actionBar.setCustomView(null);
        }

        public void onContextBarDismissed() {
            expander.popover.getController().requestBeginTransaction();

            if (addBox.get() != null) {
                // controller is locked in a transaction so this will not cause a transaction animation to run
                addBox.get().setText("");
                SearchHandler.removeCallbacksAndMessages(null);
            }
            expander.popover.getController().getSectionAtIndex(0).clear().addAllObjects(TagStorage.getDefaultTags(null));
            CollectionViewController tagController = expander.popover.getController();
            for (Tag tag : expander.target.tags) {
                if (tagController.getSectionAtIndex(0).containsObject(tag)) {
                    tagController.getSectionAtIndex(0).removeObject(tag);
                    tagController.getSectionAtIndex(0).addObject(tag);
                }
            }
            expander.popover.getController().getSectionAtIndex(1).clear();

            final CollectionView collectionView = expander.popover.getController().getCollectionView();
            collectionView.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNavigate);
            collectionView.setNavigationTransactionDirection(CollectionView.NavigationTransactionLeftToRight);
            collectionView.setMoveAnimationDuration(300);

            expander.popover.getController().requestCompleteTransaction();

            // TransactionScrollingModeNavigate are always instant and never scheduled
            // setting these parameters after the transaction has ended will have no effect on the
            // current transaction
            collectionView.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
            collectionView.setMoveAnimationDuration(200);
            collectionView.setDeleteAnimationDuration(150);
        }
    }

    static CollectionView.ReversibleAnimation insertAnimation = new CollectionView.ReversibleAnimation() {
        public void playAnimation(View view, Object object, int viewType) {
            view.setAlpha(0f);
            view.setScaleY(0.95f);
            view.animate().alpha(1f).scaleY(1f);
        }

        public void resetState(View view, Object object, int viewType) {}
    };

    static CollectionView.ReversibleAnimation deleteAnimation = new CollectionView.ReversibleAnimation() {
        public void playAnimation(View view, Object object, int viewType) {
            view.animate().alpha(0f);
        }

        public void resetState(View view, Object object, int viewType) {
            view.setAlpha(1f);
        }
    };

    private View.OnClickListener tagClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (popover != null) popover.dismiss();
            addTag((Tag) view.getTag());
        }
    };

    // TODO: Selection mode for renaming and changing colors
    public void addTagUsingPopover() {
        if (popover != null) return; // already up

        selectedTag = null;

        TagController tagController = new TagController();
        tagController.setRefreshViewsOnTransactionEnabled(true);
        tagController.addSectionForViewTypeWithTag(ViewTypeTag, null);
        // TagStorage will have been initialized at this point, so don't need to pass resources
        tagController.getSectionAtIndex(0).addAllObjects(TagStorage.getDefaultTags(null));
        for (Tag tag : target.tags) {
            if (tagController.getSectionAtIndex(0).containsObject(tag)) {
                tagController.getSectionAtIndex(0).removeObject(tag);
                tagController.getSectionAtIndex(0).addObject(tag);
            }
        }
        boolean phoneUI = context.getResources().getConfiguration().smallestScreenWidthDp < 600;
        tagController.addSectionForViewTypeWithTag(ViewTypeColor, null);
        tagController.setTagClickListener(tagClickListener);
        tagController.setCreateTagClickListener(createTagClickListener);
        tagController.setFinishAddingTagClickListener(new FinishAddingTagClickListener());
        tagController.setExpander(this);
        popover = new CollectionPopover(addAnchor, tagController);
        tagController.setHeader(popover.getHeader());
        popover.setPackedAnimationsEnabled(true);
        popover.setAutoGravity(CollectionPopover.AutoGravityStandard);
        popover.setWidth((int) (metrics.density * (phoneUI ? 256 : 320) + 0.5f));
        popover.getHeader().setBackButtonEnabled(false);
        popover.getHeader().setCustomView(new AddInflater(this, popover.getHeader(), readWrite));
        popover.setInsertAnimation(insertAnimation);
        popover.setOnDismissListener(new Popover.OnDismissListener() {
            @Override
            public void onDismiss() {
                popover = null;
            }
        });
        popover.setOnCreatedLayoutListener(CollectionPopover.FastAnimationLayoutListener);
        popover.show((Activity) context);
    }

    public View.OnClickListener createTagClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            if (TextUtils.isEmpty(searchString.trim()) || TagStorage.findExactTag(searchString.trim()) != null) {
                return;
            }

            SearchHandler.removeCallbacksAndMessages(null);
            TagController controller = (TagController) popover.getController();
            controller.requestBeginTransaction();
            controller.getSectionAtIndex(0).clear();

            // Dummy placeholder for the viewtype
            controller.getSectionAtIndex(1).addObject(new Object());

            final CollectionView Collection = controller.getCollectionView();

            // end all current animations
            Collection.setAnimationsEnabled(false);
            Collection.setAnimationsEnabled(true);

            Collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNavigate);
            Collection.setNavigationTransactionDirection(CollectionView.NavigationTransactionRightToLeft);
            Collection.setMoveAnimationDuration(300);

            Collection.addTransactionListener(new CollectionView.TransactionListener() {
                @Override
                public void onTransactionStart() {
                }

                @Override
                public void onTransactionEnd() {
                    InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(Collection.getWindowToken(), 0);
                    Collection.removeTransactionListener(this);
                }
            });

            popover.takeFocus();

            controller.requestCompleteTransaction();

            LegacyActionBar.ContextBarWrapper wrapper = popover.getHeader().createContextMode((AddInflater) popover.getHeader().getCustomView());

            wrapper.setBackgroundResource(R.drawable.popover_action_mode_white);
            wrapper.setSeparatorVisible(true);
            wrapper.setAnimationStyle(LegacyActionBar.ConfirmationActionBar);
            wrapper.setBackButtonEnabled(true);
            wrapper.setBackMode(LegacyActionBarView.DoneBackMode);
            wrapper.setDoneResource(R.drawable.back_dark_centered);
            wrapper.setDoneSeparatorVisible(false);
            wrapper.setTextColor(view.getResources().getColor(R.color.DashboardText));
            SpannableStringBuilder title = Utils.appendWithSpan(new SpannableStringBuilder(), TagExpander.searchString, new AbsoluteSizeSpan(24, true));
            title.setSpan(new Utils.CustomTypefaceSpan(Receipt.condensedTypeface()), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            wrapper.setTitle(title);

            wrapper.start();
        }
    };

    public void onDestroyView() {
        listViewParent = false;
    }

    public static class ColorAdapter extends BaseAdapter implements Filterable {

        private ArrayList<Integer> colors;
        public int selection = -1;
        public boolean checkAvailability = true;

        public ColorAdapter(ArrayList<Integer> colors) {
            this.colors = colors;
        }

        @Override
        public int getCount() {
            return 6;
        }

        @Override
        public Object getItem(int i) {
            return null;
        }

        @Override
        public long getItemId(int i) {
            return 0;
        }

        private View.OnClickListener onClickListener;

        public void setOnClickListener(View.OnClickListener listener) { onClickListener = listener; }

        public boolean areAllItemsEnabled() { return false; }
        public boolean isEnabled(int position) { return false; }

        @Override
        public View getView(int i, View view, ViewGroup container) {
            LinearLayout row = new LinearLayout(container.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setLayoutParams(new AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            if (i < 3) {
                i = i * 2;
            }
            else {
                i = 2 * i - 10 + 5;
            }
            for (int j = i * 3; j < (i + 1) * 3; j++) {
                TagView tagView = new TagView(container.getContext());
                tagView.setColor(colors.get(j));
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, (int) (48 * container.getResources().getDisplayMetrics().density));
                params.weight = 1;
                row.addView(tagView, params);

                final int Color = colors.get(j);

                tagView.setOnClickListener(onClickListener);

                if (colors.get(j) == selection) {
                    tagView.setBackgroundResource(R.drawable.selected_scrap);
                }
                else {
                    tagView.setBackgroundResource(R.drawable.unselected_scrap);
                }

                if (checkAvailability) {
                    if (TagStorage.isColorAvailable(colors.get(j))) {
                        tagView.setColorMode(TagView.ColorModeFill);
                        tagView.setEnabled(true);
                    }
                    else {
                        tagView.setColorMode(TagView.ColorModeDashed);
                        tagView.setEnabled(false);
                    }
                }
            }
            return row;
        }

        // This dummy filter should always return true
        @Override
        public Filter getFilter() {
            return new Filter() {
                @Override
                protected FilterResults performFiltering(CharSequence charSequence) {
                    return null;
                }

                @Override
                protected void publishResults(CharSequence charSequence, FilterResults filterResults) {

                }
            };
        }
    }

    // This can only be called from within the addTextView, so it's not null
    protected void setupTag(String name) {
//        InputMethodManager imm = (InputMethodManager) addTextView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
//        imm.hideSoftInputFromWindow(addTextView.getWindowToken(), 0);

        if (!TagStorage.canCreateTags() || !readWrite) {
            return;
        }

        if (addTextView.getAdapter().getClass() != ColorAdapter.class) {
            addTextView.swapAdapters();
        }
        addTextView.showDropDown();
    }

    protected void finishCreatingTag() {
        String name = addTextView.getText().toString().trim();
        int color = ((ColorAdapter) addTextView.getAdapter()).selection;

        Tag tag = Tag.make(name, color, TagStorage.generateTagUID());

        TagStorage.addTag(tag);

        completeAdd();
    }

    protected Tag finishCreatingTag(String name, int color) {
        Tag tag = Tag.make(name, color, TagStorage.generateTagUID());
        TagStorage.addTag(tag);
        return tag;
    }

    protected void cancelSetupTag() {
        if (colorPanel != null) {
            colorPanel.setOnTouchListener(null);
            colorPanel.animate().alpha(0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                ((ViewGroup) ((Activity) colorPanel.getContext()).getWindow().getDecorView()).removeView(colorPanel);
                colorPanel = null;
                }
            });
        }
    }

    protected void completeAdd() {
        // TODO
        LayoutTransition transition = tagRoot.getLayoutTransition();
        tagRoot.setLayoutTransition(null);

        InputMethodManager imm = (InputMethodManager) addTextView.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(addTextView.getWindowToken(), 0);

        String addTextViewText = addTextView.getText().toString().trim();

        Tag adapterSelection;
        if (addTextView.getAdapter() instanceof SelectableArrayAdapter) {
            adapterSelection = ((SelectableArrayAdapter) addTextView.getAdapter()).selection;
        }
        else {
            Log.d(TAG, "Adapter class is of the wrong type!");
            adapterSelection = null;
        }

        tagRoot.removeView(addTextView);
        addTextView.superDismissDropDown();
        ((View) addTextView.getTag()).setVisibility(View.VISIBLE);
        addTextView = null;

        tagRoot.setLayoutTransition(transition);

        if (!addTextViewText.isEmpty()) {
            for (Tag tag : TagStorage.getDefaultTags(tagRoot.getResources())) {
                if (tag.name.equalsIgnoreCase(addTextViewText) || tag.equals(adapterSelection)) {
                    addTag(tag);
                    break;
                }
            }
        }
    }

    protected void addTag(Tag tag) {
        final View add = tagRoot.findViewWithTag(-1);

        // Generate a tagView and place it into the RelativeLayout
        final int color = tag.color;
        LinearLayout expandedTag = (LinearLayout) ((LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.tag, tagRoot, false);
        expandedTag.setBackground(obtainRipple());
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setDashedCircleEnabled(false);
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusEnabled(true);
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusOpacity(1f);
        expandedTag.findViewById(R.id.TagColor).setRotation(45);
        ((TextView) expandedTag.findViewById(R.id.TagText)).setText(tag.name);
        ((TagView) expandedTag.findViewById(R.id.TagColor)).setColor(color);
        if (inverted) { ((TagView) expandedTag.findViewById(R.id.TagColor)).setWhiteBordersEnabled(true);
                        ((TextView) expandedTag.findViewById(R.id.TagText)).setTextColor(expandedTag.getResources().getColor(android.R.color.white)); }
        else ((TextView) expandedTag.findViewById(R.id.TagText)).setTextColor(expandedTag.getResources().getColor(R.color.ItemText));
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
        int id = add.getId();
        expandedTag.setId(id);
        id++;
        add.setId(id);
        expandedTag.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                removeColor(color);
            }
        });
        expandedTag.setTag(color);

        // Determine the position of this new tag based on its color's strength
        int strength = TagStorage.getColorStrength(tag.color);
        int position = -1;
        if (enforceHuePositions) {
            for (int i = 0; i < target.tags.size(); i++) {
                if (strength < TagStorage.getColorStrength(target.tags.get(i).color)) {
                    position = i;
                    break;
                }
            }
        }

        if (position == -1) position = target.tags.size();

//        Log.d(TAG, "Position is " + position + ", layout position is " + (tagRoot.getChildCount() - position));
        // The 1 accounts for the "Add Tag" button
        tagRoot.addView(expandedTag, position + 1, params);
        expandedTags.add(expandedTags.size() - position - 1, expandedTag);
        if (target != null) {
//            target.tags.add(position, tag);
            target.addTagToIndex(tag, position);
            compactTags.setTags(target.tags);
        }

        // If there are no more uncommon tags after this call, remove the associated button
        if (target.canHaveUncommonTags() && !target.hasUncommonTags()) {
            View uncommonView = tagRoot.findViewWithTag(UncommonTag);
            if (uncommonView != null) {
                tagRoot.removeView(uncommonView);
                //noinspection SuspiciousMethodCalls
                expandedTags.remove(uncommonView);
            }
        }

        if (!target.canAddTags()) {
            //noinspection SuspiciousMethodCalls
//            expandedTags.remove(tagRoot.findViewWithTag(-1));
            tagRoot.findViewWithTag(-1).setVisibility(View.GONE);
        }

        // Reorder the views within the tagRoot
        for (int i = 0; i < tagRoot.getChildCount(); i++) {
            int index = tagRoot.getChildCount() - i - 1;
            if (i != 0) {
                // The layout is already scheduled to run after removeView, so no need to call RequestLayout or SetLayoutParams
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    RelativeLayout.LayoutParams tagParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    tagParams.addRule(RelativeLayout.RIGHT_OF, tagRoot.getChildAt(index + 1).getId());
                    tagRoot.getChildAt(index).setLayoutParams(tagParams);
                }
                else {
                    ((RelativeLayout.LayoutParams) tagRoot.getChildAt(index).getLayoutParams()).addRule(RelativeLayout.RIGHT_OF, tagRoot.getChildAt(index + 1).getId());
                    ((RelativeLayout.LayoutParams) tagRoot.getChildAt(index).getLayoutParams()).removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                }
            }
            else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    RelativeLayout.LayoutParams tagParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    tagParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    tagRoot.getChildAt(index).setLayoutParams(tagParams);
                }
                else {
                    ((RelativeLayout.LayoutParams) tagRoot.getChildAt(index).getLayoutParams()).removeRule(RelativeLayout.RIGHT_OF);
                    ((RelativeLayout.LayoutParams) tagRoot.getChildAt(index).getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                }
            }
        }

        tagRoot.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        tagRoot.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int j, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {

                tagRoot.removeOnLayoutChangeListener(this);

                if (closed) return;

                int displacement = tagRoot.getWidth();
                if (itemTarget) {
                    if (tagRoot.getWidth() > container.getWidth() * 0.8f) {
                        displacement = (int) (container.getWidth() * 0.8f);
                    }

                    scroller.getLayoutParams().width = displacement;
                    scroller.setLayoutParams(scroller.getLayoutParams());
                }

                final int Displacement = displacement;

                long startDelay = tagRoot.getLayoutTransition().getDuration(LayoutTransition.DISAPPEARING);
                TimeInterpolator interpolator = tagRoot.getLayoutTransition().getInterpolator(LayoutTransition.CHANGE_DISAPPEARING);

                if (itemTarget) {
                    for (int i = 0; i < container.getChildCount(); i++) {
                        if (container.getChildAt(i) != scroller) {
                            container.getChildAt(i).animate().translationX(displacement - compactTags.getWidth() + 8 * metrics.density).setStartDelay(0).setInterpolator(interpolator);
                        }
                    }
                }

                if (itemTarget) {
                    compactor.post(new Runnable() {
                        public void run() {

                            if (closed) return;

                            container.removeView(compactor);

                            compactor = new View(context);
                            container.addView(compactor, new ViewGroup.LayoutParams(container.getWidth() - Displacement, ViewGroup.LayoutParams.MATCH_PARENT));
                            compactor.setX(Displacement);
                            compactor.setClickable(true);
                            compactor.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    compact();
                                }
                            });

                            compactor.bringToFront();

                        }
                    });
                }
            }
        });
    }

    protected void removeColor(int color) {
        if (addTextView != null) completeAdd();

        if (color != -1) {
            //noinspection SuspiciousMethodCalls
            expandedTags.remove(tagRoot.findViewWithTag(color));
            tagRoot.removeView(tagRoot.findViewWithTag(color));
        }
        else {
            //noinspection SuspiciousMethodCalls
            expandedTags.remove(tagRoot.findViewWithTag(UncommonTag));
            tagRoot.removeView(tagRoot.findViewWithTag(UncommonTag));
        }

        // Reorder the views within the tagRoot
        for (int i = 0; i < tagRoot.getChildCount(); i++) {
            int index = tagRoot.getChildCount() - i - 1;
            if (i != 0) {
                // The layout is already scheduled to run after removeView, so no need to call RequestLayout or SetLayoutParams
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    RelativeLayout.LayoutParams tagParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    tagParams.addRule(RelativeLayout.RIGHT_OF, tagRoot.getChildAt(index + 1).getId());
                    tagRoot.getChildAt(index).setLayoutParams(tagParams);
                }
                else {
                    ((RelativeLayout.LayoutParams) tagRoot.getChildAt(index).getLayoutParams()).addRule(RelativeLayout.RIGHT_OF, tagRoot.getChildAt(index + 1).getId());
                    ((RelativeLayout.LayoutParams) tagRoot.getChildAt(index).getLayoutParams()).removeRule(RelativeLayout.ALIGN_PARENT_LEFT);
                }
            }
            else {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                    RelativeLayout.LayoutParams tagParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
                    tagParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                    tagRoot.getChildAt(index).setLayoutParams(tagParams);
                }
                else {
                    ((RelativeLayout.LayoutParams) tagRoot.getChildAt(index).getLayoutParams()).removeRule(RelativeLayout.RIGHT_OF);
                    ((RelativeLayout.LayoutParams) tagRoot.getChildAt(index).getLayoutParams()).addRule(RelativeLayout.ALIGN_PARENT_LEFT);
                }
            }
        }
        if (target != null) {
            for (int i = 0; i < target.tags.size(); i++) {
                if (target.tags.get(i).color == color) {
//                    target.tags.remove(i);
                    target.removeTagAtIndex(i);
                    break;
                }
            }
            compactTags.setTags(target.tags);
        }

        if (target.canAddTags()) {
            tagRoot.findViewWithTag(-1).setVisibility(View.VISIBLE);
        }

        tagRoot.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));

        tagRoot.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int j, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {

                tagRoot.removeOnLayoutChangeListener(this);

                if (closed) return;

                int displacement = tagRoot.getWidth();
                if (itemTarget) {
                    if (tagRoot.getWidth() > container.getWidth() * 0.8f) {
                        displacement = (int) (container.getWidth() * 0.8f);
                    }

                    scroller.getLayoutParams().width = displacement;
                    scroller.setLayoutParams(scroller.getLayoutParams());
                }

                final int Displacement = displacement;

                long startDelay = tagRoot.getLayoutTransition().getDuration(LayoutTransition.DISAPPEARING);
                TimeInterpolator interpolator = tagRoot.getLayoutTransition().getInterpolator(LayoutTransition.CHANGE_DISAPPEARING);

                if (itemTarget) {
                    for (int i = 0; i < container.getChildCount(); i++) {
                        if (container.getChildAt(i) != scroller) {
                            container.getChildAt(i).animate().translationX(displacement - compactTags.getWidth() + 8 * metrics.density).setStartDelay(startDelay).setInterpolator(interpolator);
                        }
                    }

                    compactor.animate().x(Displacement).setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {

                            if (closed) return;

                            container.removeView(compactor);

                            compactor = new View(context);
                            container.addView(compactor, new ViewGroup.LayoutParams(container.getWidth() - Displacement, ViewGroup.LayoutParams.MATCH_PARENT));
                            compactor.setX(Displacement);
                            compactor.setClickable(true);
                            compactor.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View view) {
                                    compact();
                                }
                            });

                            compactor.bringToFront();

                        }
                    });
                }
            }
        });
    }

    public void deleteTag(Tag tag) {
        // Ensure this tag represents the root-level tag associated with its color
        final Tag deletedTag = TagStorage.findTagWithColor(tag.color);

        if (deletedTag != null) {
            if (onTagDeletedListener != null) {
                onTagDeletedListener.onTagDeleted(deletedTag);
            }
            TagStorage.removeTag(deletedTag);
        }
    }

    public void deleteColor(int color) {
        final Tag tag = TagStorage.findTagWithColor(color);
        if (tag == null) {
            if (deleteActionBar != null) {
                deleteActionBar.getContainer().removeViewAt(1);
            }
            return;
        }

        //noinspection unchecked
        final SelectableArrayAdapter<ItemCollectionFragment.Tag> TagAdapter = ((SelectableArrayAdapter<ItemCollectionFragment.Tag>) addTextView.getAdapter());
        if (TagAdapter.selection == tag) {
            TagAdapter.selection = null;
        }

        if (deleteActionBar != null) {
            final ViewGroup Container = deleteActionBar.getContainer();
            final int ContainerHeight = Container.getHeight();
            final ValueAnimator Deleter = ValueAnimator.ofInt(ContainerHeight, 1);
            ((LegacyActionBarView) ((ViewGroup) Container.getChildAt(1)).getChildAt(0)).requestDisableInteractions();
            ViewCompat.setHasTransientState(Container, true);
            Deleter.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                int previousHeight = ContainerHeight;
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    Container.getLayoutParams().height = (Integer) valueAnimator.getAnimatedValue();
                    Container.setLayoutParams(Container.getLayoutParams());
                    Container.setAlpha(1 - valueAnimator.getAnimatedFraction());
                }
            });
            Deleter.addListener(new AnimatorListenerAdapter() {
                boolean canceled = false;
                public void onAnimationCancel(Animator animation) {
                    canceled = true;
                }
                public void onAnimationEnd(Animator animation) {
                    deleteAnimator = null;
                    ViewCompat.setHasTransientState(Container, false);
                    Container.getLayoutParams().height = ContainerHeight;
                    Container.setLayoutParams(Container.getLayoutParams());
                    Container.setAlpha(1f);
                    Container.removeViewAt(1);
                    TagAdapter.remove(tag);
                    TagAdapter.notifyDataSetChanged();
                    TagAdapter.getFilter().filter(addTextView.getText());
                    addTextView.setText(addTextView.getText());
                }
            });
            Deleter.start();
            deleteAnimator = Deleter;
        }
        else {
            TagAdapter.remove(tag);
            TagAdapter.notifyDataSetChanged();
            TagAdapter.getFilter().filter(addTextView.getText());
            addTextView.setText(addTextView.getText());
        }

        if (onTagDeletedListener != null) {
            onTagDeletedListener.onTagDeleted(tag);
        }
        TagStorage.removeTag(tag);

        if (addTextView.backgroundAdapter.getClass() == ColorAdapter.class) {
            ((ColorAdapter) addTextView.backgroundAdapter).selection = TagStorage.getNextAvailableColor();
        }

    }

    public void compact() {
        if (!expanded) return;

        if (popover != null) {
            popover.dismiss();
        }

        closed = true;
        if (deleteActionBar != null) {
            dismissDeleteActionBar(false);
        }

        if (addTextView != null) {
            completeAdd();
        }

        cancelSetupTag();

        if (itemTarget) ViewCompat.setHasTransientState(container, false);

        if (onCloseListener != null) {
            onCloseListener.onClose();
        }

        TimeInterpolator interpolator = new AccelerateDecelerateInterpolator();

        if (itemTarget) {
            for (int i = 0; i < container.getChildCount(); i++) {
                if (container.getChildAt(i) != scroller && container.getChildAt(i) != compactor) {
                    container.getChildAt(i).animate().translationX(0).alpha(1f).setStartDelay(0).setInterpolator(interpolator).withLayer();
                }
            }

            if (compactor != null) compactor.setOnClickListener(null);
        }

        final int stride = compactTags.getCircleStride();

        boolean skipDashed = !compactTags.isDashedCircleEnabled();
        int modifier = skipDashed ? 2 : 1;

        // (width/2) - (stride * count) - (circleWidth/2) - padding
        int circleStartpoint = compactTags.getWidth() / 2 - (expandedTags.size() - modifier) * (stride / 2) - (int)(8 * metrics.density);// - (8 * metrics.density) - (8 * metrics.density));
        if (expandedTags.size() > 0) {
            circleStartpoint -= expandedTags.get(0).getChildAt(0).getX();
        }
        if (scroller.getScrollX() != 0)
            scroller.smoothScrollTo(0, 0);

        int i = 0;

        for (final LinearLayout expandedTag : expandedTags) {
            if (skipDashed && i == expandedTags.size()) break;
            i++;
            expandedTag.animate().x(circleStartpoint + (i - 1) * (stride));
            expandedTag.findViewById(R.id.TagText).animate().alpha(0f).start();
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
//            expandedTag.findViewById(R.id.TagColor).animate().rotationBy(-45).start();
            final float RotationStart = expandedTag.findViewById(R.id.TagColor).getRotation();
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    expandedTag.findViewById(R.id.TagColor).setRotation(Utils.interpolateValues(valueAnimator.getAnimatedFraction(), RotationStart, RotationStart - 45));
                    ((TagView) expandedTag.findViewById(R.id.TagColor)).setPlusOpacity(1f - valueAnimator.getAnimatedFraction());
                }
            });
            animator.start();

            if (skipDashed && i == expandedTags.size() - 1) i++;

            if (i == expandedTags.size()) {
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        compactTags.setVisibility(View.VISIBLE);
                        container.removeView(scroller);
                        if (itemTarget) container.removeView(compactor);
                    }
                });
            }
        }
    }

    public void destroy() {
        destroy(true);
    }

    public void destroy(boolean commitPendingChanges) {
        if (!expanded) return;

        if (deleteActionBar != null) {
            dismissDeleteActionBar(false);
        }

        if (addTextView != null && commitPendingChanges) {
            completeAdd();
        }

        if (addTextView != null) addTextView.superDismissDropDown();

        if (colorPanel != null) {
            ((ViewGroup) colorPanel.getParent()).removeView(colorPanel);
            colorPanel = null;
        }

        closed = true;

        if (itemTarget) ViewCompat.setHasTransientState(container, false);

        if (itemTarget) {
            for (int i = 0; i < container.getChildCount(); i++) {
                if (container.getChildAt(i) != scroller && container.getChildAt(i) != compactor) {
                    container.getChildAt(i).animate().setListener(null).cancel();
                    container.getChildAt(i).setTranslationX(0);
                    container.getChildAt(i).setAlpha(1f);
                }
            }
        }

        compactTags.setVisibility(View.VISIBLE);
        container.removeView(scroller);
        if (itemTarget) container.removeView(compactor);

        if (onCloseListener != null) {
            onCloseListener.onClose();
        }
    }

    public void destroyView() {
        if (scroller != null) container.removeView(scroller);
        if (compactor != null) container.removeView(compactor);
    }

   public void dismissPopover() {
       if (popover != null) {
           popover.dismiss();
       }
   }

    public void saveStaticContext() {
        if (USE_POPOVER) {
            if (popover != null) {
                staticContext.popover = popover;
                popover.setAnchor(Popover.NoAnchor);
                popover.setOnDismissListener(null);
                ((TagController) popover.getController()).setTagClickListener(null);
                staticContext.popoverController = (TagController) popover.getController();
                staticContext.popoverController.setCreateTagClickListener(null);
                staticContext.popoverController.setFinishAddingTagClickListener(null);
                staticContext.popoverController.setExpander(null);
            }
        }
        else {
            if (addTextView != null) {
                staticContext.addButtonActive = true;
                staticContext.addButtonText = addTextView.getText();
                if (addTextView.getAdapter().getClass() == ColorAdapter.class) {
                    staticContext.secondPhaseAdd = true;
                }
            } else {
                staticContext.addButtonActive = false;
                staticContext.addButtonText = null;
            }
        }
    }

    public void restoreStaticContext() {
        if (USE_POPOVER) {
            if (staticContext.popover != null) {
                this.popover = staticContext.popover;
                popover.setAnchor(new Popover.AnchorProvider() {
                    @Override
                    public View getAnchor(Popover popover) {
                        if (tagRoot != null) {
                            return tagRoot.findViewWithTag(-1);
                        }
                        return null;
                    }
                });
                popover.setOnDismissListener(new Popover.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        popover = null;
                    }
                });
                staticContext.popoverController.setTagClickListener(tagClickListener);
                staticContext.popoverController.setCreateTagClickListener(createTagClickListener);
                staticContext.popoverController.setFinishAddingTagClickListener(new FinishAddingTagClickListener());
                staticContext.popoverController.setExpander(this);
            }
        }
        else {
            if (staticContext.addButtonActive) {
                addListener.onClick(tagRoot.findViewWithTag(-1));
                addTextView.setText(staticContext.addButtonText);
                addTextView.setSelection(staticContext.addButtonText.length());
                if (staticContext.secondPhaseAdd) {
                    addTextView.swapAdapters();
                }
            }

            staticContext.addButtonActive = false;
            staticContext.addButtonText = null;
        }
    }

}
