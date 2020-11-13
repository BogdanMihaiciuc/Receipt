package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.ActionBar;
import android.app.Activity;
import android.app.Dialog;
import android.app.Fragment;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Rect;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.ScrollView;

//import com.BogdanMihaiciuc.receipt.R;

import java.util.ArrayList;

public class Popover extends Fragment {
    final static String TAG = Popover.class.toString();

    final static boolean DEBUG = false;
    final static boolean DEBUG_FOCUS = true;
    final static boolean DEBUG_PUNCH = false;
    final static boolean DEBUG_DRAGON = false;

    public final static String PopoverKey = "com.BogdanMihaiciuc.util.Popover";

    public final static int GravityBelow = 0;
    public final static int GravityAbove = 1;
    public final static int GravityLeftOf = 2;
    public final static int GravityRightOf = 3;
    public final static int GravityCenter = 4;

    final static int ShadowRadiusDP = 16;
    final static int ShadowDisplacementDP = 8;

    final static int CornerRoundnessDP = 8;

    final static int TouchBlockerID = LegacyActionBarView.generateViewId();

    public interface AnchorProvider {
        public View getAnchor(Popover popover);
    }

    public interface OnDismissListener {
        public void onDismiss();
    }

    public interface OnGravityChangedListener {
        public void onPopoverGravityChanged(Popover popover, int gravity);
    }

    public final static AnchorProvider NoAnchor = new AnchorProvider() {
        @Override
        public View getAnchor(Popover popover) {
            return null;
        }
    };

    private int layout;
    private LegacyActionBar.CustomViewProvider layoutListener;

    private AnchorProvider anchor;
    private Boolean anchorIsInScrollContainer;
    private ScrollView scrollContainer;

    private OnDismissListener onDismissListener;
    private OnGravityChangedListener onGravityChangedListener;

    private int gravity = GravityBelow;
    private int backgroundColor = 0xFFFFFFFF;
    private int indicatorColor = 0;
    private float indicatorScale = 1f;

    float roundness;

    private boolean closed = false;
    private boolean active = false;
    private boolean animated = false;
    private boolean showAsWindow = false;
    private boolean dismissIME = true;

    private boolean modal = false;

    private boolean consumesMotionEvents = true;
    private boolean transientMode = false;

    private boolean hasDragons = false;

    private boolean overflowAnimationStyle;

    private Activity activity;
    private Utils.BackStack backStack;
    protected DisplayMetrics metrics;
    private ViewGroup root;

    private Utils.DisableableRelativeLayout layoutRoot;
    private View background;
    private FrameLayout viewContainer;

    private View touchBlocker;

    private Utils.DPTranslator pixels;

    private ArrayList<Animator> animations = new ArrayList<Animator>();

    private Dialog container;

    private Runnable backStackEntry;

    private PopoverDrawable backgroundDrawable;

    protected Popover(AnchorProvider anchor) {
        this.anchor = anchor;
    }

    protected void setLayoutListener(LegacyActionBar.CustomViewProvider listener) {
        layoutListener = listener;
    }

    public Popover(int layout, AnchorProvider anchor) {
        this.layout = layout;
        this.anchor = anchor;
    }

    public Popover(LegacyActionBar.CustomViewProvider layoutListener, AnchorProvider anchor) {
        this.layoutListener = layoutListener;
        this.anchor = anchor;
    }

    public void setShowAsWindowEnabled(boolean enabled) {
        if (active) {
            throw new IllegalStateException("setShowAsWindowEnabled must be called before the popover is shown.");
        }
        showAsWindow = enabled;
    }

    public void setShowAsModalEnabled(boolean enabled) {
        if (active) {
            throw new IllegalStateException("setShowAsModal must be called before the popover is shown.");
        }
        modal = enabled;
    }

    public void setHasDragons(boolean hasDragons) {
        if (active) {
            throw new IllegalStateException("setHasDragons must be called before the popover is shown");
        }
        this.hasDragons = hasDragons;
    }

    protected boolean hasDragons() {
        return hasDragons;
    }

    public void setHideKeyboardEnabled(boolean enabled) {
        dismissIME = enabled;
    }

    public Popover show(Activity context) {
        active = true;
        context.getFragmentManager().beginTransaction().add(this, PopoverKey).commit();
        return this;
    }

    /* instanceInit */ {
        backStackEntry = onCreateBackStackEntry();
    }

    protected Runnable onCreateBackStackEntry() {
        return new Runnable() {
            @Override
            public void run() {
                dismiss();

                if (transientMode) {
                    backStack.popBackStack();
                }
            }
        };
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    private boolean initial = true;
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (closed) return;

        activity = getActivity();
        if (!(activity instanceof Utils.BackStack)) {
            throw new IllegalArgumentException("Popover must be shown in an activity that implements the BackStack!");
        }
        backStack = ((Utils.BackStack) activity).persistentBackStack();
        metrics = getResources().getDisplayMetrics();

        if (showAsWindow) {
            container = new Dialog(getActivity());
//            container.getWindow().setWindowAnimations(0);
            if (!modal) {
                container.getWindow().getAttributes().windowAnimations = gravity == GravityBelow ? Utils.PopoverDialogAnimation : Utils.PopoverDialogAnimationLand;
            }
            else {
                if (hasDragons) container.getWindow().getAttributes().windowAnimations = Utils.ModalDialogAnimation;
            }

            if (!dismissIME) {
                container.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
            }
            container.requestWindowFeature(Window.FEATURE_NO_TITLE);
            if (!modal) {
                container.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            }
            else {
                container.setCanceledOnTouchOutside(false);
            }
            container.getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            container.getWindow().setBackgroundDrawable(new ColorDrawable(0));

            WindowManager.LayoutParams params = container.getWindow().getAttributes();
            params.width = getResources().getDisplayMetrics().widthPixels;
            container.getWindow().setAttributes(params);

            container.show();
            container.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialogInterface) {
                    close();
                }
            });
            root = (ViewGroup) container.getWindow().getDecorView();
        }
        else {
            root = (ViewGroup) activity.getWindow().getDecorView();

            if (dismissIME) {
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(root.getWindowToken(), 0);
            }
        }

        pixels = new Utils.DPTranslator(activity.getResources().getDisplayMetrics().density);

        if (!showAsWindow) {
            touchBlocker = new View(activity);
            touchBlocker.setId(TouchBlockerID);
            if (!DEBUG_PUNCH) {
                touchBlocker.setOnTouchListener(new View.OnTouchListener() {
                    boolean fired = false;

                    @Override
                    public boolean onTouch(View view, MotionEvent event) {
                        if (modal) return true;

                        if (!fired) {
                            fired = true;
                            close();
                        }
                        return consumesMotionEvents;
                    }
                });
            }
            root.addView(touchBlocker);
            touchBlocker.requestFocus();
        }

        createLayout();
        if (initial) {
            initial = false;
            backStack.pushToBackStack(backStackEntry);
        }

        if (!animated && !showAsWindow) {
            layoutRoot.setAlpha(0f);
        }

        root.getViewTreeObserver().addOnGlobalLayoutListener(globalLayoutListener);
        root.getViewTreeObserver().addOnPreDrawListener(globalPreDrawListener);
    }

    public void onResume() {
        super.onResume();

        if (closed) {
            // Cleanup has already been run
            getActivity().getFragmentManager().beginTransaction().remove(this).commit();
        }
    }

    protected Utils.BackStack getBackStack() {
        return backStack;
    }

    public void onPause() {
        super.onPause();

        flushAnimations();
    }

    public void onDetach() {

        animations.clear();

        if (showAsWindow) {
            container.setOnDismissListener(null);
            container.dismiss();
            container = null;
        }

        if (layoutListener != null && viewContainer != null) {
            layoutListener.onDestroyCustomView(viewContainer.getChildAt(0));
        }

        super.onDetach();
        root.getViewTreeObserver().removeGlobalOnLayoutListener(globalLayoutListener);
        root.getViewTreeObserver().removeOnPreDrawListener(globalPreDrawListener);

        if (activity.isChangingConfigurations()) {
            ((PopoverDrawable) background.getBackground()).dealloc();
            background.setBackground(null);
        }

        activity = null;

        root = null;

        layoutRoot = null;
        viewContainer = null;
        background = null;

        touchBlocker = null;

        anchorIsInScrollContainer = null;
        scrollContainer = null;

        backgroundDrawable = null;

    }

    protected boolean handleBackPressed() {
        return false;
    }

    public void setAnchor(AnchorProvider anchor) {
        this.anchor = anchor;
    }

    protected void enableOverflowAnimationStyle() {
        overflowAnimationStyle = true;
    }

    public void flushAnimations() {
        while (animations.size() > 0) {
            animations.get(0).end();
        }
    }

    private Rect anchorRect = new Rect();
    private Rect previousRootFrame;
    private Rect rootFrame;
    boolean animatedPositioning = false;

    protected Rect getCurrentRootFrame() {
        return rootFrame;
    }

    protected ViewGroup getRoot() { return root; }

    float previousX, previousY;
    float previousScroll;
    Rect anchorFrame = new Rect();
    private ViewTreeObserver.OnPreDrawListener globalPreDrawListener = new ViewTreeObserver.OnPreDrawListener() {
        @Override
        public boolean onPreDraw() {
            View anchorView = anchor.getAnchor(Popover.this);
            if (anchorView == null) return true;
            if (anchorIsInScrollContainer == null) {
                anchorIsInScrollContainer = Boolean.FALSE;
                ViewParent parent = anchorView.getParent();
                if (parent != null) do {
                    if (parent instanceof ScrollView) {
                        anchorIsInScrollContainer = Boolean.TRUE;
                        scrollContainer = (ScrollView) parent;
                        previousScroll = scrollContainer.getScrollY();
                        break;
                    }
                } while ((parent = parent.getParent()) != null);
            }

            if (DEBUG_PUNCH) {
                anchorView.getGlobalVisibleRect(anchorFrame);
                if (anchorFrame.left != previousX || anchorFrame.top != previousY) {
                    previousX = anchorFrame.left;
                    previousY = anchorFrame.top;
                    if (layoutRoot.getHeight() > 0) {
                        adjustPosition(true);
                    }
                }
            }
            else {
                boolean needsAdjustment = anchorView.getX() != previousX || anchorView.getY() != previousY;
                if (anchorIsInScrollContainer) {
                    needsAdjustment = needsAdjustment || scrollContainer.getScrollY() != previousScroll;
                    previousScroll = scrollContainer.getScrollY();
                }
                if (needsAdjustment) {
                    previousX = anchorView.getX();
                    previousY = anchorView.getY();
                    if (layoutRoot.getHeight() > 0) {
                        adjustPosition(true);
                    }
                }
            }

            return true;
        }
    };

    private ViewTreeObserver.OnGlobalLayoutListener globalLayoutListener  = new ViewTreeObserver.OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            adjustPosition(true);
        }
    };

    /**
     * Updates and returns the current rect in which the anchor view is contained.
     * @return The visible rect of the current anchor view or null if the anchor view is null. The visible rect must be treated as read-only.
     */
    protected Rect getAnchorFrame() {
        if (anchor.getAnchor(this) == null) return null;
        anchor.getAnchor(this).getGlobalVisibleRect(anchorRect);
        return anchorRect;
    }

    public void adjustPosition(boolean translateBalloon) {
        if (gravity == GravityCenter && showAsWindow) return; // handled automatically for windows

        onPositionWillChange();

        if (closed) return;

        if (anchor.getAnchor(this) == null) {
            if (DEBUG_FOCUS) Log.d(TAG, "Invalid anchor!");
            return;
        }
        anchor.getAnchor(this).getGlobalVisibleRect(anchorRect);

        // This is called also when the IME appears;
        // try to detect here

        // When the IME appears, the rootFrame bounds change
        // when the bounds do change, the next positioning will be animated
        if (rootFrame != null) {
            root.getWindowVisibleDisplayFrame(rootFrame);
            if (!rootFrame.equals(previousRootFrame)) {
                animatedPositioning = true;
            }
            previousRootFrame.set(rootFrame);
        }
        else {
            rootFrame = new Rect();
            previousRootFrame = new Rect();

            root.getWindowVisibleDisplayFrame(rootFrame);
            previousRootFrame.set(rootFrame);
        }

        float translation = 0;

        if (!showAsWindow) {

            if (!animatedPositioning) {
                layoutRoot.animate().cancel();
            }

            int y = (int) layoutRoot.getY();
            int x = (int) layoutRoot.getX();

            if (gravity == GravityBelow || gravity == GravityAbove) {
                if (gravity == GravityBelow) {
                    layoutRoot.setY(anchorRect.bottom - anchorRect.height() / 2);
                }
                else {
                    layoutRoot.setY(anchorRect.top + anchorRect.height() / 2 - layoutRoot.getHeight());
                    if (anchorRect.top + anchorRect.height() / 2 - pixels.get(32) > rootFrame.bottom) {
                        int outOfBoundsPixels = (int) (layoutRoot.getY() + layoutRoot.getHeight()) - rootFrame.bottom;
                        layoutRoot.setY(layoutRoot.getY() - outOfBoundsPixels + pixels.get(32)); // TODO
                    }
                }
                layoutRoot.setTranslationX((int) (-rootFrame.width() / 2 + anchorRect.exactCenterX() + 0.5f));

                if (layoutRoot.getX() + layoutRoot.getWidth() > rootFrame.width()) {
                    translation = layoutRoot.getX() + layoutRoot.getWidth() - rootFrame.width();
                    if (DEBUG) Log.d(TAG, "Translation is " + translation + ", max translation is: " + (viewContainer.getChildAt(0).getWidth() / 2 - pixels.get(16) - pixels.get(CornerRoundnessDP)));
                    if (translation > viewContainer.getChildAt(0).getWidth() / 2 - pixels.get(16) - pixels.get(CornerRoundnessDP)) {
                        if (DEBUG) Log.d(TAG, "Preventing translation from exceeding the balloon bounds.");
                        translation = viewContainer.getChildAt(0).getWidth() / 2 - pixels.get(16) - pixels.get(CornerRoundnessDP);
                    }
                    layoutRoot.setTranslationX((int) (layoutRoot.getTranslationX() - translation + 0.5f));
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonTranslation(translation);
                    }
                }
                if (layoutRoot.getX() < 0) {
                    translation = layoutRoot.getX();
                    layoutRoot.setTranslationX((int) (layoutRoot.getTranslationX() - translation + 0.5f));
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonTranslation(translation);
                    }
                }
            }
            else if (gravity == GravityCenter) {
                layoutRoot.setTranslationX(0);
                layoutRoot.setTranslationY(0);
            }
            else {
                if (gravity == GravityRightOf) {
                    layoutRoot.setX(anchorRect.right - anchorRect.width() / 2);
                }
                else {
                    layoutRoot.setX(anchorRect.left + anchorRect.width() / 2 - layoutRoot.getWidth());
                }

                float baseTranslation = root.getHeight() - rootFrame.bottom;
                layoutRoot.setTranslationY(- rootFrame.bottom / 2f + anchorRect.exactCenterY() - baseTranslation);

                if (layoutRoot.getY() + layoutRoot.getHeight() > rootFrame.bottom) {
                    translation = layoutRoot.getY() + layoutRoot.getHeight() - rootFrame.bottom;
                    if (translation > viewContainer.getChildAt(0).getHeight() / 2 - pixels.get(16) - pixels.get(CornerRoundnessDP)) {
                        translation = viewContainer.getChildAt(0).getHeight() / 2 - pixels.get(16) - pixels.get(CornerRoundnessDP);
                    }
                    layoutRoot.setTranslationY(layoutRoot.getTranslationY() - translation);
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonTranslation(translation + baseTranslation / 2);
                    }
                }
                if (layoutRoot.getY() < rootFrame.top) {
                    if (translation != 0) {
                        layoutRoot.setTranslationY(layoutRoot.getTranslationY() + translation);
                    }
                    translation = layoutRoot.getY() - rootFrame.top;
                    layoutRoot.setTranslationY(layoutRoot.getTranslationY() - translation);
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonTranslation(translation + baseTranslation / 2);
                    }
                }

                if (anchorRect.top > rootFrame.bottom) {
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonVisible(false);
                    }
                }
                else {
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonVisible(true);
                    }
                }
            }

            if (animatedPositioning) {
                animatedPositioning = false;
                float targetX = layoutRoot.getX();
                float targetY = layoutRoot.getY();

                layoutRoot.setX(x);
                layoutRoot.setY(y);

                layoutRoot.animate().x(targetX).y(targetY).setInterpolator(new Utils.FrictionInterpolator(1.5f)).setDuration(300).withLayer();
            }
        }
        else {
            container.getWindow().getAttributes().gravity = Gravity.TOP | Gravity.LEFT;
            int targetX = 0, targetY = 0;
            if (gravity == GravityBelow) {
                targetX = anchorRect.left + anchorRect.width() / 2;
                targetX = targetX - layoutRoot.getWidth() / 2;

                targetY = anchorRect.bottom - anchorRect.height() / 2;

                if (targetX + layoutRoot.getWidth() > metrics.widthPixels) {
                    translation = targetX + layoutRoot.getWidth() - metrics.widthPixels;
                    targetX = (int) (targetX - translation);
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonTranslation(translation);
                    }
                }
                if (targetX < 0) {
                    translation = targetX;
                    targetX = (int) (targetX - translation);
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonTranslation(translation);
                    }
                }
            }
            else if (gravity ==GravityRightOf) {
                targetX = anchorRect.right - anchorRect.width() / 2;
                targetY = anchorRect.centerY() - layoutRoot.getHeight() / 2;

                if (targetY + layoutRoot.getHeight() > metrics.heightPixels) {
                    translation = targetY + layoutRoot.getHeight() - metrics.heightPixels;
                    targetY = (int) (targetY - translation);
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonTranslation(translation);
                    }
                }
                if (targetY < 0) {
                    translation = targetY;
                    targetY = (int) (targetY - translation);
                    if (translateBalloon) {
                        ((PopoverDrawable) background.getBackground()).setBalloonTranslation(translation);
                    }
                }
            }

            if (container.getWindow().getAttributes().x != targetX || container.getWindow().getAttributes().y != targetY
                    || container.getWindow().getAttributes().gravity != (Gravity.TOP | Gravity.LEFT)) {
                // Infinite message loop otherwise
                container.getWindow().getAttributes().x = targetX;
                container.getWindow().getAttributes().y = targetY;

                container.getWindow().setAttributes(container.getWindow().getAttributes());
            }
        }

        if (gravity == GravityBelow || gravity == GravityAbove) {
            layoutRoot.setPivotX(layoutRoot.getWidth() / 2f + translation);
            layoutRoot.setPivotY(gravity == GravityBelow ? 0f : layoutRoot.getHeight());

            background.setPivotY(layoutRoot.getPivotY());
            background.setPivotX(layoutRoot.getPivotX());
            viewContainer.setPivotY(layoutRoot.getPivotY());
            viewContainer.setPivotX(layoutRoot.getPivotX());
        }
        else if (gravity != GravityCenter) {
            layoutRoot.setPivotX(gravity == GravityLeftOf ? layoutRoot.getWidth() : 0f);
            layoutRoot.setPivotY(layoutRoot.getHeight() / 2f + translation);

            background.setPivotY(layoutRoot.getPivotY());
            background.setPivotX(layoutRoot.getPivotX());
            viewContainer.setPivotY(layoutRoot.getPivotY());
            viewContainer.setPivotX(layoutRoot.getPivotX());
        }

        onPositionDidChange();

        if (!animated && !showAsWindow) {
//            background.setPivotY(layoutRoot.getPivotY());
//            background.setPivotX(layoutRoot.getPivotX());
//            viewContainer.setPivotY(layoutRoot.getPivotY());
//            viewContainer.setPivotX(layoutRoot.getPivotX());

            animated = true;
            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            final TimeInterpolator Overshoot = overflowAnimationStyle ? new DecelerateInterpolator(1.5f) : new OvershootInterpolator(2f);
            final float StartScale = overflowAnimationStyle ? 0.9f : 0.6f;
            layoutRoot.setScaleX(StartScale);
            layoutRoot.setScaleY(StartScale);
            layoutRoot.setAlpha(1f);

            viewContainer.setAlpha(0f);
            viewContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            if (modal) {
                touchBlocker.setBackgroundColor(0);
            }

            background.setAlpha(0f);
            final TimeInterpolator Friction = new Utils.FrictionInterpolator(1.5f);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    if (layoutRoot == null) {
                        animator.cancel();
                        return;
                    }

                    viewContainer.setAlpha(Friction.getInterpolation(fraction));

                    layoutRoot.setScaleX(Utils.interpolateValues(Overshoot.getInterpolation(fraction), StartScale, 1f));
                    layoutRoot.setScaleY(Utils.interpolateValues(Overshoot.getInterpolation(fraction), StartScale, 1f));

                    background.setAlpha(Friction.getInterpolation(fraction));

                    if (modal) {
                        touchBlocker.setBackgroundColor(Utils.interpolateColors(Friction.getInterpolation(fraction), 0, Utils.transparentColor(0.66f, 0)));
                    }
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                public void onAnimationStart(Animator animation) {
                    if (layoutRoot == null) {
                        layoutRoot.setAlpha(1f);
                        layoutRoot.setTag(animator);
                    }
                    onAnimationBegin();
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);
                    if (layoutRoot == null) {
                        return;
                    }
//                    layoutRoot.setLayerType(View.LAYER_TYPE_NONE, null);
                    viewContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                    layoutRoot.setTag(null);
                    Popover.this.onAnimationEnd();
                }
            });
            animations.add(animator);
            animator.setDuration(overflowAnimationStyle ? 200 : 300);
            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    animator.start();
                }
            });
//            layoutRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//                layoutRoot.animate().alpha(1f).scaleX(1f).scaleY(1f).withLayer().setInterpolator(new OvershootInterpolator(2f));
        }
    }

    /**
     * Called when something changes and the popover will check to see if its position must change. This may be called repeatedly during animations or scrolling. The default implementation does nothing.
     */
    protected void onPositionWillChange() {

    }

    /**
     * Called after the popover's position has changed. This may be called repeatedly during animations or scrolling. The default implementation does nothing.
     */
    protected void onPositionDidChange() {

    }

    /**
     * Called when the initial animation starts. The default implementation does nothing.
     */
    protected void onAnimationBegin() {

    }

    /**
     * Called when the initial animation ends. The default implementation does nothing.
     */
    protected void onAnimationEnd() {

    }


    /**
     * Returns true if the popover is visible on screen
     * @return true if the popover is visible on screen, false if it has been dismissed or not shown yet
     */
    public boolean isPopoverVisible() {
        return active && !closed;
    }

    public void setOnDismissListener(OnDismissListener listener) {
        onDismissListener = listener;
    }

    public void setOnGravityChangedListener(OnGravityChangedListener listener) {
        onGravityChangedListener = listener;
    }

    public OnGravityChangedListener getOnGravityChangedListener() {
        return onGravityChangedListener;
    }

    protected View getAnchor() {
        return anchor.getAnchor(this);
    }

    public void setIndicatorColor(int color) {
        this.indicatorColor = color;

        if (backgroundDrawable != null) {
            backgroundDrawable.setIndicatorColor(color);
        }
        else if (LegacyActionBar.DEBUG_COMMANDED_POPOVER) {
            Log.d(TAG, "Unable to change popover color; the background drawable is: " + backgroundDrawable);
        }
    }

    public int getIndicatorColor() {
        return indicatorColor;
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
    }
    public int getBackgroundColor() {return  backgroundColor; }

    public void setConsumesMotionEvents(boolean consumesMotionEvents) {
        this.consumesMotionEvents = consumesMotionEvents;
    }

    public void setTransientEnabled(boolean enabled) {
        this.transientMode = enabled;
    }

    public void takeFocus() {
        if (touchBlocker != null) {
            touchBlocker.setFocusable(true);
            touchBlocker.setFocusableInTouchMode(true);
            touchBlocker.requestFocus();
        }
    }

    public void requestGravity(int gravity) {
        setGravity(gravity);
    }

    protected void setGravity(int gravity) {
        setGravity(gravity, true);
    }

    protected void setGravity(int gravity, boolean animated) {
        if (this.gravity != gravity) {
            if (showAsWindow && container != null) {
                container.getWindow().getAttributes().windowAnimations = gravity == GravityBelow ? Utils.PopoverDialogAnimation : Utils.PopoverDialogAnimationLand;
            }

            animatedPositioning = animated;
            this.gravity = gravity;
            if (layoutRoot != null) {
                onGravityChanged();
            }

            if (onGravityChangedListener != null) {
                onGravityChangedListener.onPopoverGravityChanged(this, gravity);
            }
        }
    }

    protected void resetBalloonTranslation() {
        if (background != null) {
            ((PopoverDrawable) background.getBackground()).setBalloonTranslation(0f);
        }
    }

    private void onGravityChanged() {
        PopoverDrawable drawable = (PopoverDrawable) background.getBackground();
//        viewContainer.setPadding(gravity == GravityRightOf ? pixels.get(ShadowRadiusDP) + drawable.getIndicatorHeight() : pixels.get(ShadowRadiusDP),
//                gravity == GravityBelow ? pixels.get(ShadowRadiusDP) + drawable.getIndicatorHeight()  : pixels.get(ShadowRadiusDP),
//                gravity == GravityLeftOf ? pixels.get(ShadowRadiusDP) + drawable.getIndicatorHeight()  : pixels.get(ShadowRadiusDP),
//                gravity == GravityAbove ? pixels.get(ShadowRadiusDP + ShadowDisplacementDP) + drawable.getIndicatorHeight() : pixels.get(ShadowRadiusDP + ShadowDisplacementDP));
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) layoutRoot.getLayoutParams();
        boolean changed = false;
        if (gravity == GravityBelow || gravity == GravityAbove) {
            if (params.gravity != Gravity.CENTER_HORIZONTAL) {
                params.gravity = Gravity.CENTER_HORIZONTAL;
                changed = true;
            }
        }
        else if (gravity == GravityCenter) {
            if (params.gravity != Gravity.CENTER) {
                params.gravity = Gravity.CENTER;
                changed = true;
            }
        }
        else {
            if (params.gravity != Gravity.CENTER_VERTICAL) {
                params.gravity = Gravity.CENTER_VERTICAL;
                changed = true;
            }
        }
        if (changed) layoutRoot.setLayoutParams(params);
        ((PopoverDrawable) background.getBackground()).setGravity(gravity);

        if (!hasDragons) viewContainer.setPadding(drawable.getLeftPadding(), drawable.getTopPadding(), drawable.getRightPadding(), drawable.getBottomPadding());

        // This triggers a relayout, which in turn will call the positioning listener
    }

    protected int getGravity() {
        return gravity;
    }

    protected void setIndicatorScale(float scale) {
        indicatorScale = scale;
    }

    private void createLayout() {

        viewContainer = new FrameLayout(activity);

        $.bind(activity);

        if (hasDragons) {
            if (DEBUG_DRAGON) {
                Log.d(TAG, "Dragon layout detected! Applying the ViewContainer background...");
            }

            // The viewContainer will have some additional vertical padding if the popover has dragons
//            int padding = $.drawable(Utils.Dragon, activity).getIntrinsicHeight();
//            viewContainer.setPadding(0, padding, 0, padding);

            viewContainer.setBackground($.drawable(Utils.DragonWindow, activity));
            viewContainer.setPadding($.dp(67), $.dp(224), $.dp(67), $.dp(32));
        }

        $.unbind();

        viewContainer.addView(obtainView());

        layoutRoot = new Utils.DisableableRelativeLayout(activity) {

            // Intercept back presses to prevent views with focus from consuming it when they don't need it
            public boolean dispatchKeyEvent(KeyEvent event) {
                boolean returnValue = super.dispatchKeyEvent(event);
                if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
                    if (touchBlocker != null) {
                        touchBlocker.requestFocus();
                    }
                }

                return returnValue;
            }

            // Whenever a child of this popover loses focus, the layoutRoot transfer focus to the touchBlocker, which allows it to intercept back button presses.
            public void clearChildFocus(View child) {
                super.clearChildFocus(child);
                if (DEBUG_FOCUS) Log.d(TAG, "Child has lost focus!");

                if (!showAsWindow && !touchBlocker.hasFocus()) {
                    if (DEBUG_FOCUS) Log.d(TAG, "Transferring lost focus to touchBlocker");
                    touchBlocker.requestFocus();
                }
            }

        };
        layoutRoot.setNextFocusDownId(TouchBlockerID);
        layoutRoot.setNextFocusForwardId(TouchBlockerID);
        layoutRoot.setNextFocusLeftId(TouchBlockerID);
        layoutRoot.setNextFocusRightId(TouchBlockerID);
        layoutRoot.setNextFocusUpId(TouchBlockerID);
        layoutRoot.setAddStatesFromChildren(true);
        layoutRoot.setTransientEnabled(transientMode);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (gravity == GravityBelow || gravity == GravityAbove) {
            params.gravity = Gravity.CENTER_HORIZONTAL;
        }
        else {
            params.gravity = Gravity.CENTER_VERTICAL;
        }

        layoutRoot.setLayoutParams(params);

        background = new View(activity);
        PopoverDrawable drawable = new PopoverDrawable(activity);
        backgroundDrawable = drawable;
        drawable.setIndicatorSize((int) (drawable.getIndicatorWidth() * indicatorScale + 0.5f), (int) (drawable.getIndicatorHeight() * indicatorScale + 0.5f));
//        Log.e(TAG, "Creating the popover drawable with a fill color of " + Integer.toHexString(backgroundColor));
        drawable.setFillColor(backgroundColor);
        drawable.prepareIndicatorColor(indicatorColor);
        drawable.setEdgeColor(0x20000000);
        drawable.setShadowAlpha(0.25f);
        drawable.setShadowRadius(ShadowRadiusDP, ShadowDisplacementDP, true);
        drawable.setRoundness(CornerRoundnessDP, true);

        // Has Dragons draws a different, bitmap-based background
        if (hasDragons) {
            if (DEBUG_DRAGON) {
                Log.d(TAG, "Dragon layout detected! Making the popover drawable invisible...");
            }

            drawable.setEdgeColor(0);
            drawable.setShadowAlpha(0);
            drawable.setFillColor(0);
            drawable.prepareIndicatorColor(0);

            drawable.setShadowRadius(1, 0, false);
            drawable.setIndicatorSize(0, 0);
        }

        background.setBackground(drawable);

        RelativeLayout.LayoutParams backGroundParams = new RelativeLayout.LayoutParams(0, 0);
        backGroundParams.addRule(RelativeLayout.ALIGN_LEFT, 1);
        backGroundParams.addRule(RelativeLayout.ALIGN_RIGHT, 1);
        backGroundParams.addRule(RelativeLayout.ALIGN_TOP, 1);
        backGroundParams.addRule(RelativeLayout.ALIGN_BOTTOM, 1);
        layoutRoot.addView(background, backGroundParams);

        layoutRoot.setClickable(true);
        // TODO Yikes! Use generated viewId instead
        viewContainer.setId(1);
        params = new FrameLayout.LayoutParams(
                hasDragons ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        viewContainer.setLayoutParams(params);
        onGravityChanged();
        layoutRoot.addView(viewContainer);

        if (hasDragons) {
            // If the popover has dragons, the layoutRoot gains a few additional views
            if (DEBUG_DRAGON) {
                Log.d(TAG, "Dragon layout detected! Creating the drogon head...");
            }

            // ---------------------------------------------------------

            ImageView dragon = new ImageView(activity);

            dragon.setImageDrawable($.drawable(Utils.Dragon, activity));

            layoutRoot.addView(dragon, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Everything gets packed within the layout root
            layoutRoot.setPadding(0, 0, 0, 0);
        }

        if (hasDragons) {
            root.addView(layoutRoot, new ViewGroup.LayoutParams(
                            hasDragons ? Math.min(getResources().getDisplayMetrics().widthPixels + $.dp(48, activity), $.dp(480, activity)) : ViewGroup.LayoutParams.WRAP_CONTENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT)
            );
        }
        else {
            root.addView(layoutRoot);
        }

        if (hasDragons) {
            if (showAsWindow && getResources().getDisplayMetrics().widthPixels + $.dp(48, activity) < $.dp(480, activity)) {
                layoutRoot.setTranslationX(-$.dp(24, activity));
            }

            if (showAsWindow) {
                layoutRoot.setTranslationY(-$.dp(24, activity));
            }
        }
    }

    private View obtainView() {
        if (layoutListener != null) {
            return layoutListener.onCreateCustomView(activity.getLayoutInflater(), viewContainer);
        }
        else {
            return activity.getLayoutInflater().inflate(layout, viewContainer, false);
        }
    }

    public void dismiss() {
        close();
    }

    protected void close() {
        if (closed) return;
        closed = true;

        flushAnimations();

        if (layoutRoot != null) {
            layoutRoot.disable();
        }

        backStack.popBackStackFrom(backStackEntry);

        root.getViewTreeObserver().removeGlobalOnLayoutListener(globalLayoutListener);
        root.getViewTreeObserver().removeOnPreDrawListener(globalPreDrawListener);

        final ViewGroup Root = root;
        final ViewGroup Layout = layoutRoot;
        final View Background = background;

        Root.removeView(touchBlocker);

        if (showAsWindow) {
            if (container != null) {
                if (container.isShowing()) {
                    container.dismiss();
                }
            }
        }
        else {
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            Layout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            final float EndScale = overflowAnimationStyle ? 0.95f : 0.9f;
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    Layout.setScaleX(Utils.interpolateValues(fraction, 1f, EndScale));
                    Layout.setScaleY(Utils.interpolateValues(fraction, 1f, EndScale));
                    Layout.setAlpha(1 - fraction);
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ((PopoverDrawable) Background.getBackground()).dealloc();
                    Background.setBackground(null);
                    Root.removeView(Layout);
                }
            });
            animator.setDuration(overflowAnimationStyle ? 150 : 200);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            animator.start();
        }

        try {
            activity.getFragmentManager().beginTransaction().remove(this).commit();
        }
        catch (IllegalStateException exception) {
            // The fragment will be detached on the next resume
        }

//        if (layoutListener != null && viewContainer != null) {
//            layoutListener.onDestroyCustomView(viewContainer.getChildAt(0));
//        }

        if (onDismissListener != null) {
            onDismissListener.onDismiss();
        }
    }

    public static AnchorProvider anchorWithID(final int ID) {
        return new AnchorProvider() {
            @Override
            public View getAnchor(Popover popover) {
                if (popover.getActivity() != null) {
                    return popover.getActivity().findViewById(ID);
                }
                return null;
            }
        };
    }

}
