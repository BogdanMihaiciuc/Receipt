package com.BogdanMihaiciuc.util;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.FrameLayout;

//import com.BogdanMihaiciuc.receipt.R;

public class CollectionPopover extends Popover {
    final static boolean DEBUG = false;

    public interface OnCreatedLayoutListener {
        public void onCreatedLayout(Popover popover, View root);
    }

    public final static CollectionPopover.OnCreatedLayoutListener FastAnimationLayoutListener = new CollectionPopover.OnCreatedLayoutListener() {

        @Override
        public void onCreatedLayout(Popover popover, View root) {
            CollectionView c = (CollectionView) root.findViewById(CollectionPopover.CollectionID);
            c.setDeleteAnimationDuration(150);
            c.setMoveAnimationDuration(200);
            c.setMoveInterpolator(new AccelerateDecelerateInterpolator());
        }
    };

    public final static int AutoGravityStandard = 0;
    public final static int AutoGravityTopDown = 1;
    public final static int AutoGravityDisabled = 2;
    public final static int AutoGravityCenter = 3;

    public final static int CollectionID = LegacyActionBarView.generateViewId();

    // 8 (* 48) items + 1/2 item + 7 spacings
    final static int MaxHeightTabletDP = 416;
    final static int MaxHeightPhoneDP = 320;
    final static int MinWidthDP = 160;
    final static int EndPaddingDP = 48;

    private CollectionView collection;
    private CollectionViewController controller;

    private FrameLayout layout;
    private FrameLayout headerContainer;
    private LegacyActionBar header;

    private Activity activity;

    private Utils.DPTranslator pixels;

    private CollectionView.ReversibleAnimation insertAnimation, deleteAnimation;
    private OnCreatedLayoutListener layoutListener;

    private int width = 0;
    private int autoGravity = AutoGravityTopDown;

    private boolean phoneUI, landscape;
    private boolean closed;

    private Boolean packedAnimations;

    private int maxHeightDP;

    public CollectionPopover(AnchorProvider anchor, CollectionViewController controller) {
        super(anchor);
        super.setLayoutListener(listCreator);
        header = LegacyActionBar.getAttachableLegacyActionBar();
        header.setCommandedPopoverIndicatorWithGravities(this, GravityBelow);
        header.setBackgroundColor(0);
        header.setSeparatorVisible(true);
        header.setBackButtonEnabled(false);
        header.setSeparatorOpacity(0.15f);
        header.setFillContainerEnabled(true);
        header.setOnContextModeChangedListener(new LegacyActionBar.ContextModeChangedListenerAdapter());
        setController(controller);
    }

    protected Runnable onCreateBackStackEntry() {
        return new Runnable() {
            @Override
            public void run() {
                if (header.handleBackPress()) {
                    getBackStack().pushToBackStack(this);
                    return;
                }
                dismiss();
            }
        };
    }

    public void setController(CollectionViewController controller) {
        this.controller = controller;
    }

    public CollectionViewController getController() { return controller; }

    public CollectionView getCollectionView() { return collection; }

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
        phoneUI = getResources().getConfiguration().smallestScreenWidthDp < 600;
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        maxHeightDP = phoneUI ? MaxHeightPhoneDP : MaxHeightTabletDP;

        super.onActivityCreated(savedInstanceState);

        if (closed) return;

        activity = getActivity();
    }

    public void onResume() {
        super.onResume();

        if (closed) getActivity().getFragmentManager().beginTransaction().remove(header).commit();
    }

    public LegacyActionBar getHeader() {
        return header;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setAutoGravity(int autoGravity) {
        this.autoGravity = autoGravity;
    }

    public Popover show(Activity activity) {
        activity.getFragmentManager().beginTransaction().add(header, null).commit();
        return super.show(activity);
    }

    public void setInsertAnimation(CollectionView.ReversibleAnimation insertAnimation) {
        this.insertAnimation = insertAnimation;
    }

    public void setDeleteAnimation(CollectionView.ReversibleAnimation deleteAnimation) {
        this.deleteAnimation = deleteAnimation;
    }

    public void setPackedAnimationsEnabled(boolean enabled) {
        packedAnimations = enabled;
    }

    public void setOnCreatedLayoutListener(OnCreatedLayoutListener layoutListener) {
        this.layoutListener = layoutListener;
    }

    public void requestGravity(int gravity) {
        // NOP
    }

    protected void onPositionDidChange() {
        // Try to determine the maximum height and the gravity
        int maxHeight = -1;
        Rect rootFrame = getCurrentRootFrame();
        Rect anchorPosition = new Rect();
        if (getAnchor() != null) {
            View anchor = getAnchor();
            anchor.getGlobalVisibleRect(anchorPosition);
            if (anchorPosition.height() != 0) {
                boolean topDownGravity = !landscape || !phoneUI || autoGravity != AutoGravityStandard ||
                        anchorPosition.bottom < rootFrame.top + 56 * metrics.density + 0.5f ||
                        anchorPosition.top > getRoot().getHeight() - 56 * metrics.density + 0.5f;

                if (topDownGravity) {
                    if (anchorPosition.top > rootFrame.bottom - anchorPosition.bottom + pixels.get(ShadowRadiusDP) + pixels.get(ShadowDisplacementDP)) {
                        if (getGravity() != GravityAbove) {
                            if (getGravity() == GravityRightOf || getGravity() == GravityLeftOf) {
                                resetBalloonTranslation();
                            }

                            if (autoGravity == AutoGravityCenter) {
                                setGravity(GravityCenter);
                            }
                            else {
                                setGravity(GravityAbove);
                            }
                        }

                        maxHeight = anchorPosition.top - pixels.get(2 * ShadowRadiusDP + 16) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);
                        // additionally, the max height may not become larger than the screen size, which is possible if the anchor object is below the screen bounds
                        maxHeight = Math.min(maxHeight, rootFrame.bottom - pixels.get(2 * ShadowRadiusDP + 16) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight));
                    }
                    else {
                        if (getGravity() != GravityBelow) {
                            if (getGravity() == GravityRightOf || getGravity() == GravityLeftOf) {
                                resetBalloonTranslation();
                            }

                            if (autoGravity == AutoGravityCenter) {
                                setGravity(GravityCenter);
                            }
                            else {
                                setGravity(GravityBelow);
                            }
                        }
                        maxHeight = (rootFrame.bottom - anchorPosition.bottom) - pixels.get(ShadowRadiusDP) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);
                    }
                }
                else {
                    if (anchorPosition.left > rootFrame.right - anchorPosition.right) {
                        if (getGravity() != GravityLeftOf) {
                            setGravity(GravityLeftOf, false);
                        }

                        int heightBase = rootFrame.height();
                        if (rootFrame.bottom < getRoot().getHeight()) {
                            heightBase = Math.min(rootFrame.height() + pixels.get(ShadowRadiusDP + ShadowDisplacementDP), getRoot().getHeight() - rootFrame.top);
                        }
                        maxHeight = heightBase - pixels.get(2 * ShadowRadiusDP + ShadowDisplacementDP) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);
                    }
                    else {
                        if (getGravity() != GravityRightOf) {
                            setGravity(GravityRightOf, false);
                        }

                        int heightBase = rootFrame.height();
                        if (rootFrame.bottom < getRoot().getHeight()) {
                            heightBase = Math.min(rootFrame.height() + pixels.get(ShadowRadiusDP + ShadowDisplacementDP), getRoot().getHeight() - rootFrame.top);
                        }
                        maxHeight = heightBase - pixels.get(2 * ShadowRadiusDP + ShadowDisplacementDP) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);
                    }
                }
                maxHeight = Math.min(maxHeight, pixels.get(maxHeightDP));
                collection.setAnimateLayoutEnabled(topDownGravity);
                if (collection.getMaxHeight() != maxHeight) {

                    if (DEBUG) Log.d(TAG, "Post-layout maxHeight changed to " + maxHeight);
                    collection.setMaxHeight(maxHeight);
                }
            }
        }

    }

    protected View createLayout (final Context context) {
        if (width == 0) {
            width = getResources().getConfiguration().smallestScreenWidthDp < 600 ?
                (int) (256 * metrics.density + 0.5f) : (int) (360 * metrics.density + 0.5f);
        }

        header.setTextColor(context.getResources().getColor(Utils.DashboardText));

        // Try to determine the maximum height and the gravity
        int maxHeight = -1;
        Rect anchorPosition = new Rect();
        Rect rootRect = new Rect();
        boolean topDownGravity = true;
        if (getAnchor() != null) {
            View anchor = getAnchor();
            anchor.getGlobalVisibleRect(anchorPosition);
            getRoot().getWindowVisibleDisplayFrame(rootRect);
            if (anchorPosition.height() != 0) {
                topDownGravity = !landscape || !phoneUI || autoGravity != AutoGravityStandard ||
                        anchorPosition.bottom < rootRect.top + 56 * metrics.density + 0.5f ||
                        anchorPosition.top > getRoot().getHeight() - 56 * metrics.density + 0.5f;
                if (topDownGravity) {
                    if (anchorPosition.top > metrics.heightPixels - anchorPosition.bottom) {
                        setGravity(GravityAbove, false);

                        maxHeight = anchorPosition.top - pixels.get(2 * ShadowRadiusDP + 16) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);
                    }
                    else {

                        maxHeight = (metrics.heightPixels - anchorPosition.bottom) - pixels.get(ShadowRadiusDP) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);
                    }

                    if (autoGravity == AutoGravityCenter) {
                        setGravity(GravityCenter, false);
                    }
                }
                else {
                    if (anchorPosition.left > metrics.widthPixels - anchorPosition.right) {
                        setGravity(GravityLeftOf, false);

                        maxHeight = metrics.heightPixels - pixels.get(ShadowRadiusDP) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);
                    }
                    else {
                        setGravity(GravityRightOf, false);

                        maxHeight = metrics.heightPixels - pixels.get(ShadowRadiusDP) - getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);
                    }
                }
            }
        }
        maxHeight = Math.min(maxHeight, pixels.get(maxHeightDP));
        if (DEBUG) Log.d(TAG, "Pre-layout maxHeight changed to " + maxHeight);

        layout = new FrameLayout(context);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        layout.setLayoutParams(params);

        int topMargin = 0;

        headerContainer = new FrameLayout(context);
        params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(Utils.GenericHeaderHeight));
        topMargin += getResources().getDimensionPixelSize(Utils.GenericHeaderHeight);

        header.setRoundedCornersWithRadius(Utils.TopSide, pixels.get(CornerRoundnessDP) - 1);

        header.setContainer(headerContainer);
        layout.addView(headerContainer, params);

        collection = new CollectionView(context);
        collection.setAnimateLayoutEnabled(topDownGravity);
        collection.setMoveInterpolator(new Utils.FrictionInterpolator(1.5f));
        collection.setId(CollectionID);
        if (packedAnimations != null) {
            collection.setPackedAnimationsEnabled(packedAnimations);
        }
        if (insertAnimation != null) {
            collection.setInsertAnimator(insertAnimation);
        }
        if (deleteAnimation != null) {
            collection.setDeleteAnimator(deleteAnimation);
        }
        if (maxHeight != -1) {
            collection.setMaxHeight(maxHeight);
        }
        else {
            collection.setMaxHeight(3 * metrics.heightPixels / 4);
        }
        params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = topMargin;
        layout.addView(collection, params);
        collection.setController(controller);

        if (layoutListener != null) {
            layoutListener.onCreatedLayout(this, layout);
        }

        return layout;
    }

    protected void onAnimationBegin() {
        if (collection != null) {
            collection.freeze();
        }
    }

    protected void onAnimationEnd() {
        if (collection != null) {
            collection.thaw();
        }
    }

    protected boolean handleBackPressed() {
        return header.handleBackPress();
    }

    public void onDetach() {
        super.onDetach();

        activity = null;

        layout = null;

        headerContainer = null;
        collection = null;
    }

    protected void close() {
        closed = true;
        if (activity == null) return;

        collection.freeze();

        super.close();

        try {
            activity.getFragmentManager().beginTransaction().remove(header).commit();
        }
        catch (IllegalStateException exception) {
            // Handled on next resume
        }
    }

}
