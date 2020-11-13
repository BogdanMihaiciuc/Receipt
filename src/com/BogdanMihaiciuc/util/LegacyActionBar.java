package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.style.AbsoluteSizeSpan;
import android.util.Log;
import android.view.ActionMode;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.Spinner;

//import com.BogdanMihaiciuc.receipt.R;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class LegacyActionBar extends Fragment {

    final static String TAG = LegacyActionBar.class.toString();

    final static boolean USE_STRIDE_ANIMATION = true;
    final static boolean USE_STRIDE_ALPHA = false;
    final static boolean USE_ACTION_MODE_BACKED_LABELS = false;
    final static boolean DEBUG_COMMANDED_POPOVER = false;

    final static long AnimationStride = 33l;
    final static long AnimationBaseDelay = 50l;
    final static float AnimationMinScale = 0f;
    final static TimeInterpolator AnimationStrideInterpolator = new Utils.FrictionInterpolator(1.5f);

    public final static int StandardActionBar = 0;
    public final static int ContextualActionBar = 1;
    public final static int ConfirmationActionBar = 2;

    final static float ConfirmationDisplacementDP = 100;

    public interface OnLegacyActionSelectedListener {
        public void onLegacyActionSelected(ActionItem item);
    }

    public interface ContextModeChangedListener {
        public void onContextModeStarted();
        public void onContextModeChanged();
        public void onContextModeFinished();
    }

    public static class ContextModeChangedListenerAdapter implements ContextModeChangedListener {
        public void onContextModeStarted() {}
        public void onContextModeChanged() {}
        public void onContextModeFinished() {}
    }

    public interface OnLegacyNavigationElementSelectedListener {
        public void onLegacyNavigationElementSelected(int index, LegacyNavigationElement element);
    }

    public interface CustomViewProvider {
        public View onCreateCustomView(LayoutInflater inflater, ViewGroup container);
        public void onDestroyCustomView(View customView);
    }

    public static class ViewInflater implements CustomViewProvider {
        int resource; public ViewInflater(int resource) {this.resource = resource;}
        public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {return inflater.inflate(resource, container, false);};
        public void onDestroyCustomView(View customView) {}
    }

    public static class ActionItem {
        private String name;
        private int resource;
        protected Drawable drawable;
        private int id;

        private boolean titleVisible;
        private boolean visibleAsIcon;
        private boolean visible;
        private boolean enabled;

        protected LegacyActionBar listener;
        protected View attachedView;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
            if (listener != null) listener.onItemChanged(this);
        }

        public int getResource() {
            return resource;
        }

        public void setResource(int resource) {
            this.resource = resource;
            if (listener != null) listener.onItemChanged(this);
        }

        public boolean isTitleVisible() {
            return titleVisible;
        }

        public void setTitleVisible(boolean titleVisible) {
            this.titleVisible = titleVisible;
            if (listener != null) listener.onItemChanged(this);
        }

        public boolean isVisible() {
            return visible;
        }

        public void setVisible(boolean visible) {
            this.visible = visible;
            if (listener != null) listener.onItemChanged(this);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;

            if (attachedView != null) {
                attachedView.setEnabled(enabled);
                attachedView.animate().alpha(enabled ? 1f : 0.5f);
                if (enabled) {
                    attachedView.setLayerType(View.LAYER_TYPE_NONE, null);
                }
                else {
                    attachedView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                }
            }
//            if (listener != null) listener.onItemChanged(this);
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
            if (listener != null) listener.onItemChanged(this);
        }

        public boolean isVisibleAsIcon() {
            return visibleAsIcon;
        }

        public boolean isSpinner() {
            return false;
        }

        public void setVisibleAsIcon(boolean visibleAsIcon) {
            this.visibleAsIcon = visibleAsIcon;
        }

        public String toString() { return name; }

        public LegacyActionBar getActionBar() {
            return listener;
        }
    }

    public static interface LegacySpinnerListener {
        public void onItemSelected(int spinnerID, int index, Object object);
        public void onNothingSelected(int spinnerId);
    }

    @SuppressWarnings("unchecked")
    public static class SpinnerItem extends ActionItem {

        private ArrayList dataSet = new ArrayList();
        private int selection;

        private LegacySpinnerListener listener;
        private AdapterView.OnItemSelectedListener proxyListener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                selection = i;
                getListener().onItemSelected(getId(), i, adapterView.getAdapter().getItem(i));
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
                getListener().onNothingSelected(getId());
            }
        };

        protected ArrayList getDataSet() {
            return dataSet;
        }

        public int getResource() {
            return 0;
        }

        public void addObject(Object object) {
            dataSet.add(object);
            if (attachedView != null) {
//                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).add(object);
                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).notifyDataSetChanged();
            }
        }

        public void addObjects(Object ... objects) {
            Collections.addAll(dataSet, objects);
            if (attachedView != null) {
//                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).add(objects);
                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).notifyDataSetChanged();
            }
        }

        public void addObjects(Collection objects) {
            dataSet.addAll(objects);
            if (attachedView != null) {
//                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).addAll(objects);
                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).notifyDataSetChanged();
            }
        }

        public void removeObject(Object object) {
            dataSet.remove(object);
            if (attachedView != null) {
//                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).remove(object);
                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).notifyDataSetChanged();
            }
        }

        public void clearObjects() {
            dataSet.clear();
            if (attachedView != null) {
                ((ArrayAdapter) ((Spinner) attachedView).getAdapter()).notifyDataSetChanged();
            }
        }

        public void setSelection(int selection) {
            this.selection = selection;
            if (attachedView != null) {
                ((Spinner) attachedView).setSelection(selection);
            }
        }

        public int getSelection() {
            return selection;
        }

        public boolean isSpinner() {
            return true;
        }

        public LegacySpinnerListener getListener() {
            return listener;
        }

        public void setListener(LegacySpinnerListener listener) {
            this.listener = listener;
        }

        protected AdapterView.OnItemSelectedListener getProxyListener() {
            return proxyListener;
        }
    }

    public static class LegacyNavigationElement {
        String name;
        int id;
        int resource;

        protected View attachedView;
        protected View text;
        protected View selector;

        private LegacyNavigationElement() {}

        public static LegacyNavigationElement make(int id, String name, int resource) {
            LegacyNavigationElement element = new LegacyNavigationElement();
            element.id = id;
            element.name = name;
            element.resource = resource;
            return element;
        }
    }

    public ActionBarWrapper getWrapper() {
        return actionBarWrapper;
    }

    public class ActionBarWrapper {

        protected int caretResource = Utils.CaretUpLight;
        protected int overflowResource = Utils.OverflowLight;
        protected int doneResource = Utils.Done;
        protected int logoResource = 0;
        protected int backgroundResource = 0;
        protected int backgroundColor = 0;

        protected int selectorColor = Utils.AccentColor;

        protected int spinnerResource = Utils.Spinner;

        protected Drawable logoDrawable;

        protected int titleResource;
        protected CharSequence titleString;

        protected int subtitleResource;
        protected String subtitleString;

        protected int textColor = LegacyActionBarView.DefaultTextColor;

        protected boolean separatorVisible;
        protected boolean doneSeparatorVisible = false;
        protected int backButtonMode;

        protected int navigationMode;
        protected int navigationGravity = Gravity.CENTER;

        protected boolean backButtonEnabled = true;
        protected boolean backButtonVisible = true;

        protected LegacyActionBarView actionBar;

        protected ArrayList<LegacyNavigationElement> navigationElements = new ArrayList<LegacyNavigationElement>();
        protected LegacyNavigationElement selectedNavigationElement;

        protected CustomViewProvider customViewProvider;
        protected int backButtonPosition;

        protected float separatorOpacity = 0.1f;
        protected float innerSeparatorOpacity = 0.1f;
        protected int separatorThicknessDP = 1;

        protected int specialMode = StandardActionBar;

        protected boolean fillContainer;

        protected String tag;

        /**
         * Do not use.
         */
        @Deprecated
        public int forcedMinimumItems = 0;
        protected boolean landscapeUIEnabled = true;

        protected boolean splitZoneEnabled = false;
        protected int splitZoneAlignment = LegacyActionBarView.SplitZoneAlignmentFill;

        protected boolean hasCustomRippleColors = false;
        protected int pressedColor;
        protected int rippleColor;

        // Prevent external sources from instantiating this class
        private ActionBarWrapper() {}

        public void setTag(String tag) {
            this.tag = tag;
        }

        public String getTag() {
            return tag;
        }

        float getRoundRadius() {
            return roundRadius;
        }

        int[] getRoundedCorners() {
            return roundedCorners;
        }

        public void setOverflowEnabled(boolean enabled) {
            // TODO real implementation
            if (enabled) {
                forcedMinimumItems = 0;
            }
            else {
                forcedMinimumItems = 4;
            }
        }

        public void setCustomView(CustomViewProvider customViewProvider) {
            this.customViewProvider = customViewProvider;

            if (actionBar != null) {
                actionBar.onCustomViewChanged();
            }
        }

        public CustomViewProvider getCustomView() {
            return customViewProvider;
        }

        public void setBackButtonPosition(int position) {
            this.backButtonPosition = position;

            if (actionBar != null) {
                actionBar.setBackButtonPosition(position);
            }
        }

        public void setNavigationMode(int mode) {
            navigationMode = mode;

            if (actionBar != null) {
                actionBar.setNavigationMode(mode);
            }
        }

        public void setInlineNavigationGravity(int gravity) {
            navigationGravity = gravity;

            if (actionBar != null) {
                actionBar.setNavigationGravity(gravity);
            }
        }

        public int getInlineNavigationGravity() {
            return navigationGravity;
        }

        public void setCaretResource(int resource) {
            caretResource = resource;

            if (actionBar != null) {
                actionBar.setCaretResource(resource);
            }
        }

        public void setDoneResource(int resource) {
            doneResource = resource;

            if (actionBar != null) {
                actionBar.setDoneResource(resource);
            }
        }

        public void setBackgroundResource(int resource) {
            backgroundResource = resource;
            backgroundColor = 0;

            if (actionBar != null) {
                actionBar.setBackgroundResource(resource);
            }
        }

        public void setBackgroundColor(int color) {
            backgroundResource = 0;
            backgroundColor = color;

            if (actionBar != null) {
                actionBar.setBackgroundColor(color);

                if (commandedPopover != null && Utils.arrayContainsInt(commandedPopoverGravities, commandedPopover.getGravity())) {
                    if (getCurrentActionBarView() == actionBar) {
                        commandedPopover.setIndicatorColor(color);
                    }
                }
            }
        }

        public void setRippleHighlightColors(int background, int ripple) {
            hasCustomRippleColors = true;
            pressedColor = background;
            rippleColor = ripple;

            if (actionBar != null) {
                actionBar.setRippleHighlightColors(background, ripple);
            }
        }

        public void setOverflowResource(int resource) {
            overflowResource = resource;

            if (actionBar != null) {
                actionBar.setOverflowResource(resource);
            }
        }

        public int getOverflowResource() {
            return overflowResource;
        }

        public void setLogoResource(int resource) {
            logoResource = resource;
            logoDrawable = null;

            if (actionBar != null) {
                actionBar.setLogoResource(resource);
            }
        }

        public int getLogoResource() {
            return logoResource;
        }

        public void setLogoDrawable(Drawable drawable) {
            logoDrawable = drawable;
            logoResource = 0;

            if (actionBar != null) {
                actionBar.setLogoDrawable(drawable);
            }
        }

        public void setTitleResource(int resource) {
            titleString = null;
            titleResource = resource;

            if (actionBar != null) {
                actionBar.setTitleResource(resource);
            }
        }

        public void setTitle(CharSequence title) {
            titleString = title;
            titleResource = 0;

            if (actionBar != null) {
                actionBar.setTitle(title);
            }
        }

        public void setSubtitleResource(int resource) {
            titleString = null;
            titleResource = resource;

            if (actionBar != null) {
                actionBar.setSubtitleResource(resource);
            }
        }

        public void setSubtitle(String subtitle) {
            subtitleString = subtitle;
            subtitleResource = 0;

            if (actionBar != null) {
                actionBar.setSubtitle(subtitle);
            }
        }

        public void setTitleAnimated(CharSequence title, int direction) {
            titleString = title;
            titleResource = 0;

            if (actionBar != null) {
                actionBar.setTitleAnimated(title, direction);
            }
        }

        public void setTextColor(int color) {
            textColor = color;

            if (actionBar != null)
                actionBar.setTextColor(color);
        }

        public void setBackMode(int mode) {
            backButtonMode = mode;

            if (actionBar != null)
                actionBar.setBackMode(mode);
        }

        public void setSeparatorVisible(boolean visible) {
            separatorVisible = visible;

            if (actionBar != null) actionBar.setSeparatorVisible(visible);
        }

        public void setDoneSeparatorVisible(boolean visible) {
            doneSeparatorVisible = visible;

            if (actionBar != null) actionBar.setDoneSeparatorVisible(visible);
        }

        public void setSeparatorOpacity(float opacity) {
            separatorOpacity = opacity;

            if (actionBar != null)
                actionBar.setSeparatorOpacity(opacity);
        }

        public void setSeparatorThickness(int thickness) {
            separatorThicknessDP = thickness;

            if (actionBar != null) actionBar.setSeparatorThickness(separatorThicknessDP);
        }

        public void setInnerSeparatorOpacity(float opacity) {
            innerSeparatorOpacity = opacity;

            if (actionBar != null)
                actionBar.setInnerSeparatorOpacity(opacity);
        }

        public int getSeparatorColor() {
            int alpha = (int) (separatorOpacity * 255);
            return Color.argb(alpha, 0, 0, 0);
        }

        public int getInnerSeparatorColor() {
            int alpha = (int) (innerSeparatorOpacity * 255);
            return Color.argb(alpha, 0, 0, 0);
        }

        public void setBackButtonEnabled(boolean enabled) {
            backButtonEnabled = enabled;

            if (actionBar != null) {
                actionBar.setBackButtonEnabled(enabled);
            }
        }

        public void setBackButtonVisible(boolean visible) {
            backButtonVisible = visible;

            if (actionBar != null) {
                actionBar.setBackButtonVisible(visible);
            }
        }

        public void addNavigationElement(LegacyNavigationElement element) {
            if (selectedNavigationElement == null) {
                selectedNavigationElement = element;
            }
            navigationElements.add(element);

            if (actionBar != null) {
                actionBar.addNavigationElement(element);
            }
        }

        public void clearNavigationElements() {
            selectedNavigationElement = null;
            navigationElements.clear();

            if (actionBar != null)
                actionBar.clearNavigationElements();
        }

        public int getNavigationElementCount() {
            return navigationElements.size();
        }

        public void setSelectedNavigationIndex(int index) {
            if (actionBar != null)
                actionBar.deselectNavigationElement();
            selectedNavigationElement = navigationElements.get(index);
            if (actionBar != null)
                actionBar.selectNavigationElement(selectedNavigationElement);

            if (getNavigationListener() != null) {
                getNavigationListener().onLegacyNavigationElementSelected(index, selectedNavigationElement);
            }
        }

        public void setSelectedNavigationElement(LegacyNavigationElement element) {
            if (actionBar != null)
                actionBar.deselectNavigationElement();
            selectedNavigationElement = element;
            if (actionBar != null)
                actionBar.selectNavigationElement(selectedNavigationElement);

            if (getNavigationListener() != null) {
                getNavigationListener().onLegacyNavigationElementSelected(navigationElements.indexOf(element), element);
            }

        }

        public int getNumberOfVisibleActionItems() {
            if (actionBar != null)
                return actionBar.getNumberOfVisibleActionItems();
            return 0;
        }

        public void setFillContainerEnabled(boolean enabled) {
            fillContainer = enabled;

            if (actionBar != null) {
                actionBar.setFillContainerEnabled(enabled);
            }
        }

        public void setLandscapeUIEnabled(boolean enabled) {
            landscapeUIEnabled = enabled;
        }

        public View getBarView() {
            return actionBar;
        }

        public OnLegacyActionSelectedListener getListener() {
            return listener;
        }

        public OnLegacyNavigationElementSelectedListener getNavigationListener() {
            return navigationListener.get();
        }

        public Popover showOverflow() {
            if (actionBar == null) return null;
            ArrayList<ActionItem> overflowItems = actionBar.getOverflowItems();
            if (overflowItems == null) return null;
            Popover.AnchorProvider anchor = new Popover.AnchorProvider() {
                @Override
                public View getAnchor(Popover popover) {
                    if (ViewConfiguration.get(getActivity()).hasPermanentMenuKey()) {
                        return (splitZoneEnabled ? actionBar.splitZone : actionBar);
                    }
                    if (actionBar != null) {
                        if (splitZoneEnabled) {
                            return actionBar.splitZone.findViewById(LegacyActionBarView.OverflowID);
                        }
                        return actionBar.findViewById(LegacyActionBarView.OverflowID);
                    }
                    return null;
                }
            };
            MenuPopover p = (MenuPopover) MenuPopover.objectMenuPopover(anchor, overflowItems).show((Activity) actionBar.getContext());
            if (backgroundColor == 0) {
                p.setBackgroundColor(0xDDFFFFFF);
            }
            else {
                p.setBackgroundColor(Utils.transparentColor(0xDD, backgroundColor));
            }
            p.setTextColor(textColor);
            //p.enableOverflowAnimationStyle();
            p.setHideKeyboardEnabled(false);
            p.setOnMenuItemSelectedListener(new MenuPopover.OnMenuItemSelectedListener() {
                @Override
                public void onMenuItemSelected(Object item, int index) {
                    if (getListener() != null) {
                        getListener().onLegacyActionSelected((ActionItem) item);
                    }
                }
            });
            if (splitZoneEnabled) {
                p.setGravity(Popover.GravityAbove);
            }
            return p;
        }

        public void setHasSplitZone(boolean hasSplitZone) {
            splitZoneEnabled = hasSplitZone;
        }

        public ViewGroup getSplitZone() {
            return splitZone;
        }

        void hintItem(final ActionItem HintedItem) {
            new TooltipPopover(HintedItem.getName(), null, new Popover.AnchorProvider() {
                @Override
                public View getAnchor(Popover popover) {
                    return HintedItem.attachedView;
                }
            }).show((Activity) actionBar.getContext());
        }

        void hintNavigationElement(final LegacyNavigationElement HintedItem) {
            new TooltipPopover(HintedItem.name, null, new Popover.AnchorProvider() {
                @Override
                public View getAnchor(Popover popover) {
                    return HintedItem.attachedView;
                }
            }).show((Activity) actionBar.getContext());
        }

        public int getSplitZoneAlignment() {
            return splitZoneAlignment;
        }

        public void setSplitZoneAlignment(int splitZoneAlignment) {
            this.splitZoneAlignment = splitZoneAlignment;
            if (actionBar != null) {
                actionBar.setSplitZoneAlignment(splitZoneAlignment);
            }
        }
    }

    public static abstract class ContextBarListener implements OnLegacyActionSelectedListener {
        public abstract void onContextBarStarted();
        public abstract void onContextBarDismissed();
        public void onContextBarActivated(ContextBarWrapper wrapper) {}
    }

    public static abstract class ContextBarListenerAdapter extends ContextBarListener {
        public void onContextBarStarted() {}
        public void onContextBarDismissed() {}
        public void onLegacyActionSelected(ActionItem item) {}
    }

    public class ContextBarWrapper extends ActionBarWrapper {
        protected ArrayList<ActionItem> contextItems = new ArrayList<ActionItem>();
        protected OnLegacyNavigationElementSelectedListener navigationListener;

        private ContextBarListener contextBarListener;
        protected ContextBarListener listenerProxy = new ContextBarListener() {
            @Override
            public void onContextBarStarted() {
                contextBarListener.onContextBarStarted();
                contextBarListener.onContextBarActivated(ContextBarWrapper.this);
            }

            @Override
            public void onContextBarDismissed() {
                contextBarListener.onContextBarDismissed();
            }

            @Override
            public void onLegacyActionSelected(ActionItem item) {
                contextBarListener.onLegacyActionSelected(item);
                if (item.getId() == android.R.id.home) {
                    dismiss();
                }
            }
        };

        boolean started;
        int mode;

        public int getCurrentNavigationIndex() {
            return navigationElements.indexOf(selectedNavigationElement);
        }

        public ContextBarWrapper(ContextBarListener listener) {
            contextBarListener = listener;
        }

        public void setAnimationStyle(int style) {
            mode = style;
        }

        public ActionItem addItem(int id, String title, int resource, boolean showTitle, boolean showAsIcon) {
            return addItemToIndex(id, title, resource, showTitle, showAsIcon, contextItems.size());
        }

        public SpinnerItem addSpinner(int id) {
            return addSpinnerToIndex(id, contextItems.size());
        }

        public ActionItem addItemToIndex(int id, String title, int resource, boolean showTitle, boolean showAsIcon, int index) {
            ActionItem item = new ActionItem();

            item.setId(id);
            item.setName(title);
            item.setResource(resource);
            item.setTitleVisible(showTitle);
            item.setVisibleAsIcon(showAsIcon);
            item.setVisible(true);
            item.setEnabled(true);

            item.listener = LegacyActionBar.this;

            contextItems.add(index, item);

            if (actionBar != null)
                actionBar.onItemAdded(item);

            return item;
        }

        public SpinnerItem addSpinnerToIndex(int id, int index) {
            SpinnerItem item = new SpinnerItem();

            item.setId(id);
            item.setVisible(true);
            item.setVisibleAsIcon(true);
            item.setTitleVisible(true);

            contextItems.add(index, item);

            if (actionBar != null)
                actionBar.onItemAdded(item);

            return item;
        }

        public ActionItem removeItemWithId(int id) {
            for (ActionItem item : contextItems) {
                if (item.getId() == id) {
                    contextItems.remove(item);
                    if (actionBar != null) {
                        actionBar.onItemRemoved(item);
                    }
                    return item;
                }
            }

            return null;
        }

        public ActionItem removeItemWithTitle(String title) {
            for (ActionItem item : contextItems) {
                if (item.getName().equals(title)) {
                    contextItems.remove(item);
                    if (actionBar != null) {
                        actionBar.onItemRemoved(item);
                    }
                    return item;
                }
            }

            return null;
        }

        public ActionItem findItemWithId(int id) {
            for (ActionItem item : contextItems) {
                if (item.getId() == id) {
                    return item;
                }
            }

            return null;
        }

        public void setMode(int mode) {
            this.mode = mode;
        }

        public int getMode() {
            return mode;
        }

        public void setOnLegacyNavigationElementSelectedListener(OnLegacyNavigationElementSelectedListener listener) {
            navigationListener = listener;
        }

        public OnLegacyNavigationElementSelectedListener getNavigationListener() {
            return navigationListener;
        }

        public ContextBarWrapper start() {
            if (!started) {
                contextBarListener.onContextBarStarted();
                contextBarListener.onContextBarActivated(this);
            }

            started = true;
            showContext(this);
            return this;
        }

        public void postDismiss() {
            if (actionBar != null) {
                actionBar.requestDisableInteractions();
            }

            new Handler().post(new Runnable() {
                @Override
                public void run() {
                    dismiss();
                }
            });
        }

        public void pop() {
            for (ContextBarWrapper wrapper : contextModes) {
                if (wrapper == this) {
                    dismiss();
                    return;
                }
                if (wrapper.started) {
                    wrapper.dismissInstantly();
                }
            }
        }

        public void dismiss() {
            dismiss(true);
        }

        public void dismiss(boolean animated) {
            if (started)
                contextBarListener.onContextBarDismissed();

            dismissContext(this, animated);
            started = false;
        }

        public void dismissInstantly() {
            dismiss(false);
        }

        public OnLegacyActionSelectedListener getListener() {
            return listenerProxy;
        }

        public void createBar() {
            this.actionBar = new LegacyActionBarView(getActivity(), LegacyActionBar.this, this);
            this.actionBar.setActionItems(contextItems);
        }

        public Popover.AnchorProvider obtainAnchorForItemWithID(final int ID) {
            return new Popover.AnchorProvider() {
                @Override
                public View getAnchor(Popover popover) {
                    return ContextBarWrapper.this.findItemWithId(ID).attachedView;
                }
            };
        }
    }

    private class ActionModeContextBarWrapper extends ContextBarWrapper {

        private ActionMode actionMode;
        private ActionMode.Callback callback;

        public ActionModeContextBarWrapper(ActionMode actionMode, ActionMode.Callback callback) {
            super(null);
            this.actionMode = actionMode;
            this.callback = callback;
            listenerProxy = new ContextBarListener() {
                @Override
                public void onContextBarStarted() {}

                @Override
                public void onContextBarDismissed() {}

                @Override
                public void onLegacyActionSelected(ActionItem item) {
                    if (item.getId() != android.R.id.home)
                        ActionModeContextBarWrapper.this.callback.onActionItemClicked(ActionModeContextBarWrapper.this.actionMode, ActionModeContextBarWrapper.this.actionMode.getMenu().findItem(item.getId()));
                    else
                        ActionModeContextBarWrapper.this.actionMode.finish();
                }
            };
        }

        public void rebuildItems() {
            contextItems.clear();
            Menu menu = actionMode.getMenu();
            for (int i = 0, size = menu.size(); i < size; i++) {
                MenuItem item = menu.getItem(i);
                int drawableID = 0;
                if (item.getTitle().equals(getString(android.R.string.copy))) drawableID = Utils.Copy;
                if (item.getTitle().equals(getString(android.R.string.cut))) drawableID = Utils.Cut;
                if (item.getTitle().equals(getString(android.R.string.paste))) drawableID = Utils.Paste;
                if (item.getTitle().equals(getString(android.R.string.selectAll))) drawableID = Utils.SelectAll;
                addItem(item.getItemId(), item.getTitle().toString(), drawableID, USE_ACTION_MODE_BACKED_LABELS, true);
            }
        }

        public ContextBarWrapper start() {
            started = true;
            showContext(this);
            return this;
        }

        public void dismiss(boolean animated) {
            actionMode.finish();

            dismissContext(this, animated);
            started = false;
        }

    }

    private ArrayList<ActionItem> actionItems = new ArrayList<ActionItem>();

    private boolean initialized;
    private boolean useExternalContainer;

    private int transientState;

    private FrameLayout container;
    private ViewGroup externalContainer;
    private ViewGroup splitZone; // TODO splitZone
    private LegacyActionBarView actionBar;
    private LegacyActionBarView visibleContext;
    protected ActionBarWrapper actionBarWrapper = new ActionBarWrapper();

    private Popover commandedPopover;
    private int[] commandedPopoverGravities;

    private float roundRadius;
    private int[] roundedCorners;

    protected ArrayList<ContextBarWrapper> contextModes = new ArrayList<ContextBarWrapper>();

    private OnLegacyActionSelectedListener listener;
    private WeakReference<OnLegacyNavigationElementSelectedListener> navigationListener = new WeakReference<OnLegacyNavigationElementSelectedListener>(null);
    private ContextModeChangedListener strongContextListener;
    private WeakReference<ContextModeChangedListener> contextListener = new WeakReference<ContextModeChangedListener>(null);

    public void setOnLegacyNavigationElementSelectedListener(OnLegacyNavigationElementSelectedListener listener) {
        navigationListener = new WeakReference<OnLegacyNavigationElementSelectedListener>(listener);
    }

    public void setContainer(ViewGroup container) {
        if (!useExternalContainer) {
            return;
        }

        this.externalContainer = container;
        if (getActivity() != null) externalContainer.addView(createView());
    }

    public ViewGroup getContainer() {
        return this.externalContainer;
    }

    public void setSplitZone(ViewGroup splitZone) {
        if (!useExternalContainer || this.externalContainer != null) {
            throw new IllegalStateException("setSplitZone must be called before setting the main container!");
        }

        this.splitZone = splitZone;

        if (LegacyActionBarView.DEBUG_SPLIT) Log.d(TAG, "The selected split zone is " + splitZone);

        actionBarWrapper.setHasSplitZone(splitZone != null);
        for (ContextBarWrapper wrapper : contextModes) {
            wrapper.setHasSplitZone(splitZone != null);
        }
    }

    public int getSplitZoneAlignment() {
        return actionBarWrapper.getSplitZoneAlignment();
    }

    public void setSplitZoneAlignment(int splitZoneAlignment) {
        actionBarWrapper.setSplitZoneAlignment(splitZoneAlignment);
    }

    public ContextBarWrapper findContextModeWithTag(String tag) {
        for (ContextBarWrapper wrapper : contextModes) {
            if (wrapper.tag.equals(tag)) return wrapper;
        }

        return null;
    }

    public ContextBarWrapper createContextMode(ContextBarListener listener) {
        ContextBarWrapper wrapper = new ContextBarWrapper(listener);

        wrapper.setFillContainerEnabled(actionBarWrapper.fillContainer);
        if (getActivity() != null) {
            wrapper.setBackgroundColor(getResources().getColor(Utils.ActionModeColor));
        }
        else {
            wrapper.setBackgroundResource(Utils.ActionModeColor);
        }
        wrapper.setBackMode(LegacyActionBarView.DoneBackMode);
        wrapper.setSeparatorVisible(true);

        wrapper.mode = ContextualActionBar;
        wrapper.splitZoneEnabled = actionBarWrapper.splitZoneEnabled;
        wrapper.splitZoneAlignment = actionBarWrapper.splitZoneAlignment;

        contextModes.add(wrapper);

        return wrapper;
    }

    public ContextBarWrapper createActionConfirmationContextMode(String title, String action, ContextBarListener listener) {
        return createActionConfirmationContextMode(title, action, 0, listener);
    }

    public ContextBarWrapper createActionConfirmationContextMode(String title, String action, int icon, ContextBarListener listener) {
        ContextBarWrapper wrapper = new ContextBarWrapper(listener);

        wrapper.setBackMode(LegacyActionBarView.DoneBackMode);
        wrapper.setDoneResource(Utils.BackLight);

        wrapper.setFillContainerEnabled(actionBarWrapper.fillContainer);
        wrapper.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_dark));
        SpannableStringBuilder builder = Utils.appendWithSpan(new SpannableStringBuilder(), title, new AbsoluteSizeSpan(20, true));
//        builder.setSpan(new Utils.CustomTypefaceSpan(Receipt.mediumTypeface()), 0, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        wrapper.setTitle(builder);
        wrapper.setSeparatorVisible(true);
        wrapper.setDoneSeparatorVisible(false);

//        wrapper.addItem(R.id.ConfirmCancel, getString(R.string.CancelButtonLabel), 0, true, true);
        wrapper.addItem(Utils.ConfirmOKID, action, icon, icon == 0, true);

        wrapper.mode = ConfirmationActionBar;
        wrapper.splitZoneEnabled = actionBarWrapper.splitZoneEnabled;
        wrapper.splitZoneAlignment = actionBarWrapper.splitZoneAlignment;

        contextModes.add(wrapper);

        return wrapper;
    }

    public ContextBarWrapper createActionModeBackedContextMode(ActionMode actionMode) {

        Field actionModeCallback;
        try {
            actionModeCallback = actionMode.getClass().getDeclaredField("mCallback");
        }
        catch (NoSuchFieldException e) {
            Log.e(TAG, "Unknown ActionMode implementation; swapping to standard behaviour.");
            e.printStackTrace();
            return null;
        }

        actionModeCallback.setAccessible(true);
        ActionMode.Callback callback;

        try {
            callback = (ActionMode.Callback) actionModeCallback.get(actionMode);
        }
        catch (IllegalAccessException e) {
            Log.e(TAG, "Unknown ActionMode implementation; swapping to standard behaviour.");
            e.printStackTrace();
            return null;
        }


        ActionModeContextBarWrapper wrapper = new ActionModeContextBarWrapper(actionMode, callback);
        wrapper.setFillContainerEnabled(actionBarWrapper.fillContainer);

        ActionMode.Callback newCallback = getActionModeCallbackProxy(callback, wrapper);
        try {
            actionModeCallback.set(actionMode, newCallback);
        }
        catch (IllegalAccessException e) {
            Log.e(TAG, "Unknown ActionMode implementation; swapping to standard behaviour.");
            e.printStackTrace();
            return null;
        }

        CharSequence title = null;
        CharSequence subtitle = null;
        title = actionMode.getTitle();
        subtitle = actionMode.getSubtitle();
        if (title != null) wrapper.setTitle(title.toString());
        if (subtitle != null) wrapper.setSubtitle(subtitle.toString());

        wrapper.rebuildItems();
        wrapper.forcedMinimumItems = 4;
        wrapper.splitZoneEnabled = actionBarWrapper.splitZoneEnabled;
        wrapper.splitZoneAlignment = actionBarWrapper.splitZoneAlignment;
        contextModes.add(wrapper);

        return wrapper;
    }

    public ActionMode.Callback getActionModeCallbackProxy(final ActionMode.Callback callback, final ActionModeContextBarWrapper wrapper) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                return callback.onCreateActionMode(actionMode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                boolean callbackReturnValue;
                if (callbackReturnValue = callback.onPrepareActionMode(actionMode, menu)) {
                    wrapper.rebuildItems();
                }
                return callbackReturnValue;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                // This is never called in regular situations
                return callback.onActionItemClicked(actionMode, menuItem);
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                callback.onDestroyActionMode(actionMode);
                wrapper.dismiss();
            }
        };
    }

    public List<ContextBarWrapper> getContextModes() {
        return Collections.unmodifiableList(contextModes);
    }

    public boolean handleBackPress() {
        if (visibleContext == actionBar) return false;

        ((ContextBarWrapper) visibleContext.wrapper).dismiss();

        return true;
    }

    // TODO
    public void setOnContextModeChangedListener(ContextModeChangedListener listener) {
        contextListener = new WeakReference<ContextModeChangedListener>(listener);
        if (!(listener instanceof Context)) {
            strongContextListener = listener; // prevents collection of non-context listeners
        }
        else {
            strongContextListener = null;
        }
    }

    public ContextBarWrapper getCurrentContextMode() {
        ContextBarWrapper currentWrapper = null;
        for (ContextBarWrapper wrapper : contextModes) {
            if (wrapper.started) currentWrapper = wrapper;
        }

        return currentWrapper;
    }

    public void showContext(ContextBarWrapper context) {
        contextModes.remove(context);
        contextModes.add(context);

        if (getActivity() == null) {
            //Already detached
            return;
        }

        if (DEBUG_COMMANDED_POPOVER) {
            if (commandedPopover != null) Log.d(TAG, "Commanded Popover is " + commandedPopover + ", with a gravity of " + commandedPopover.getGravity() + ", which is supported: " + Utils.arrayContainsInt(commandedPopoverGravities, commandedPopover.getGravity()));
        }

        int popoverAnimationDuration;
        TimeInterpolator popoverAnimationInterpolator;

        if (visibleContext == actionBar) {
            if (contextListener.get() != null) contextListener.get().onContextModeStarted();
        }
        else {
            if (contextListener.get() != null) contextListener.get().onContextModeChanged();
        }

        final int StartingColor = commandedPopover != null ? Utils.overlayColors(commandedPopover.getBackgroundColor(), visibleContext.wrapper.backgroundColor) : visibleContext.wrapper.backgroundColor;

        final LegacyActionBarView actionBar = visibleContext;
        ArrayList<ActionItem> actionItems = this.actionItems;
        if (actionBar != this.actionBar) {
            actionItems = ((ContextBarWrapper) actionBar.wrapper).contextItems;
        }

        context.createBar();
        container.addView(context.actionBar);

        visibleContext = context.actionBar;

        final int TargetColor = commandedPopover != null ? Utils.overlayColors(commandedPopover.getBackgroundColor(), context.backgroundColor) : context.backgroundColor;

        context.actionBar.setAlpha(0f);
        ViewGroup contextSplitZone = context.actionBar.splitZone;
        if (contextSplitZone != null) {
            contextSplitZone.setAlpha(0f);
            contextSplitZone.animate().alpha(1f).setDuration(context.mode != ConfirmationActionBar ? 200 : 250);
            if (context.mode == ConfirmationActionBar) contextSplitZone.animate().setInterpolator(new DecelerateInterpolator(2f));
            contextSplitZone.animate().start();
        }

        context.actionBar.animate().alpha(1f).setDuration(context.mode != ConfirmationActionBar ? 200 : 250);
        if (context.mode == ConfirmationActionBar) context.actionBar.animate().setInterpolator(new DecelerateInterpolator(2f));
        context.actionBar.animate().start();

        popoverAnimationDuration = context.mode != ConfirmationActionBar ? 200 : 250;
        popoverAnimationInterpolator = context.mode == ConfirmationActionBar ? new DecelerateInterpolator(2f) : new AccelerateDecelerateInterpolator();

        if (commandedPopover != null && Utils.arrayContainsInt(commandedPopoverGravities, commandedPopover.getGravity())) {
            ValueAnimator popoverAnimator = ValueAnimator.ofFloat(0f, 1f);
            popoverAnimator.setDuration(popoverAnimationDuration);
            popoverAnimator.setInterpolator(popoverAnimationInterpolator);

            popoverAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    // The commanded popover may change mid-animation
                    if (commandedPopover != null && Utils.arrayContainsInt(commandedPopoverGravities, commandedPopover.getGravity())) {
                        if (DEBUG_COMMANDED_POPOVER) {
                            Log.d(TAG, "Commanded popover color has changed: 0x" + Integer.toHexString(Utils.interpolateColors(animation.getAnimatedFraction(), StartingColor, TargetColor)));
                        }
                        commandedPopover.setIndicatorColor(Utils.interpolateColors(animation.getAnimatedFraction(), StartingColor, TargetColor));
                    }
                }
            });

            popoverAnimator.start();
        }

        // Animation for standard context modes
        if (context.mode != ConfirmationActionBar) {

            // Animation for standard back button; this contains the code to run at the end of the animation
            if (context.actionBar.backButton != null) {
                final View ContextActionBarBackButton = context.actionBar.backButton;

                context.actionBar.backButton.animate().cancel();

                context.actionBar.backButton.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int backButtonMeasuredWidth = context.actionBar.backButton.getMeasuredWidth();
                context.actionBar.backButton.setTranslationX(- backButtonMeasuredWidth);
                context.actionBar.backButton.animate().setDuration(200).translationX(0f)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                ContextActionBarBackButton.animate().setListener(null);

                                if (actionBar.wrapper.navigationMode != LegacyActionBarView.TabsNavigationMode) actionBar.setVisibility(View.INVISIBLE);

                                for (int i = 0, size = actionBar.getChildCount(); i < size; i++) {
                                    actionBar.getChildAt(i).animate().cancel();
                                    actionBar.getChildAt(i).setTranslationX(0f);
                                    actionBar.getChildAt(i).setScaleY(1f);
                                    actionBar.getChildAt(i).setScaleX(1f);
                                }

                                if (actionBar.splitZone != null) {
                                    actionBar.splitZone.setVisibility(View.INVISIBLE);

                                    for (int i = 0, size = actionBar.splitZone.getChildCount(); i < size; i++) {
                                        actionBar.splitZone.getChildAt(i).animate().cancel();
                                        actionBar.splitZone.getChildAt(i).setTranslationX(0f);
                                        actionBar.splitZone.getChildAt(i).setScaleY(1f);
                                        actionBar.splitZone.getChildAt(i).setScaleX(1f);
                                    }
                                }
                            }
                        }).start();
            }

            int stride = 0;

            // Standard action items animation
            if (context.actionBar.overflowButton != null) {
                context.actionBar.overflowButton.setScaleY(AnimationMinScale);
                context.actionBar.overflowButton.setScaleX(AnimationMinScale);
                if (USE_STRIDE_ALPHA) {
                    context.actionBar.overflowButton.setAlpha(0f);
                    context.actionBar.overflowButton.animate().alpha(1f);
                }
                context.actionBar.overflowButton.animate().scaleY(1f).scaleX(1f).setStartDelay(AnimationBaseDelay + stride).setDuration(200).setInterpolator(AnimationStrideInterpolator).start();

                stride += AnimationStride;
            }

            for (ActionItem item : context.contextItems) {
                if (item.attachedView != null) {
                    item.attachedView.setScaleY(AnimationMinScale);
                    item.attachedView.setScaleX(AnimationMinScale);
                    if (USE_STRIDE_ALPHA) {
                        item.attachedView.setAlpha(0f);
                        item.attachedView.animate().alpha(1f);
                    }
                    item.attachedView.animate().scaleY(1f).scaleX(1f).setStartDelay(AnimationBaseDelay + stride).setDuration(200).setInterpolator(AnimationStrideInterpolator).start();

                    stride += AnimationStride;
                }
            }
        }
        else {
            // Animation for confirmation context modes
            TimeInterpolator decelerate = new DecelerateInterpolator(2f);
            for (int i = 0, size = context.actionBar.getChildCount(); i < size; i++) {
                context.actionBar.getChildAt(i).setTranslationX(ConfirmationDisplacementDP * getResources().getDisplayMetrics().density);
                context.actionBar.getChildAt(i).animate().setDuration(250).translationX(0f).setInterpolator(decelerate).start();
            }
            if (contextSplitZone != null) {
                for (int i = 0, size = contextSplitZone.getChildCount(); i < size; i++) {
                    contextSplitZone.getChildAt(i).setTranslationX(ConfirmationDisplacementDP * getResources().getDisplayMetrics().density);
                    contextSplitZone.getChildAt(i).animate().setDuration(250).translationX(0f).setInterpolator(decelerate).start();
                }
            }
        }

        // Previous context animation for standard context modes
        // This animation is undone when the back button's animation has completed
        if (context.mode != ConfirmationActionBar) {
            int stride = 0;

            for (ActionItem item : actionItems) {
                if (item.attachedView != null) {
                    if (USE_STRIDE_ALPHA) {
                        item.attachedView.animate().alpha(0f);
                    }
                    item.attachedView.animate().scaleY(AnimationMinScale).scaleX(AnimationMinScale).setStartDelay(stride).setInterpolator(AnimationStrideInterpolator).setDuration(200).start();

                    stride += AnimationStride;
                }
            }

            if (actionBar.overflowButton != null) {
                if (USE_STRIDE_ALPHA) {
                    actionBar.overflowButton.animate().alpha(0f);
                }
                actionBar.overflowButton.animate().scaleY(AnimationMinScale).scaleX(AnimationMinScale).setStartDelay(stride).setInterpolator(AnimationStrideInterpolator).setDuration(200).start();
            }

            stride += AnimationStride;

            if (actionBar.backButton != null) {
                actionBar.backButton.animate().translationX(- context.actionBar.backButton.getMeasuredWidth()).setDuration(200).start();
            }
        }

        // Hiding of tab naviation mode for the previous context; this runs on all types of contexts
        if (actionBar.wrapper.navigationMode == LegacyActionBarView.TabsNavigationMode) {

            if (actionBar.navigationContainer != null) {
                final View NavigationContainer = actionBar.navigationContainer;
                actionBar.navigationContainer.animate().alpha(0f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        NavigationContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    }

                    @Override
                    public void onAnimationEnd(Animator animation) {
                        NavigationContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                }).start();
            }

        }

    }

    public void dismissContext(final ContextBarWrapper context, boolean animated) {
        contextModes.remove(context);
        // Nothing to do if the context wasn't yet started
        if (!context.started) return;

        if (getActivity() == null) {
            //Already detached
            return;
        }

        ActionBarWrapper nextContext = null;
        //Determine which context should become visible next
        for (int i = contextModes.size() - 1; i >= 0; i--) {
            ContextBarWrapper wrapper = contextModes.get(i);
            if (wrapper != context && wrapper.started) {
                nextContext = wrapper;
                break;
            }
        }

        if (nextContext == null) nextContext = actionBarWrapper;

        LegacyActionBarView actionBar = nextContext.actionBar;
        ArrayList<ActionItem> actionItems = this.actionItems;
        if (actionBar != this.actionBar) {
            actionItems = ((ContextBarWrapper) actionBar.wrapper).contextItems;
        }

        final int StartingColor = commandedPopover != null ? Utils.overlayColors(commandedPopover.getBackgroundColor(), context.backgroundColor) : context.backgroundColor;
        final int TargetColor = commandedPopover != null ? Utils.overlayColors(commandedPopover.getBackgroundColor(), nextContext.backgroundColor) : nextContext.backgroundColor;

        if (context.actionBar != visibleContext) {
            actionBar = null;
            nextContext = null;
        }

        if (nextContext == actionBarWrapper)
            { if (contextListener.get() != null) contextListener.get().onContextModeFinished(); }
        else
            { if (contextListener.get() != null) contextListener.get().onContextModeChanged(); }

        if (animated) {
            context.actionBar.requestDisableInteractions();
            ViewGroup contextSplitZone = context.actionBar.splitZone;
            if (contextSplitZone != null) {
                contextSplitZone.animate().alpha(0f).setDuration(context.mode != ConfirmationActionBar ? 200 : 250);
                if (context.mode == ConfirmationActionBar) contextSplitZone.animate().setInterpolator(new AccelerateInterpolator(2f));
                contextSplitZone.animate().start();
            }

            context.actionBar.animate().alpha(0f).setDuration(context.mode != ConfirmationActionBar ? 200 : 250).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (container != null) {
                        container.removeView(context.actionBar);
                    }
                    if (context.actionBar.splitZone != null && LegacyActionBar.this.splitZone != null) {
                        LegacyActionBar.this.splitZone.removeView(context.actionBar.splitZone);
                    }
                }
            });
            if (context.mode == ConfirmationActionBar) context.actionBar.animate().setInterpolator(new AccelerateInterpolator(2f));
            context.actionBar.animate().start();

            int popoverAnimationDuration = context.mode != ConfirmationActionBar ? 200 : 250;
            TimeInterpolator popoverAnimationInterpolator = context.mode == ConfirmationActionBar ? new AccelerateInterpolator(2f) : new AccelerateDecelerateInterpolator();

            if (commandedPopover != null && Utils.arrayContainsInt(commandedPopoverGravities, commandedPopover.getGravity())) {
                ValueAnimator popoverAnimator = ValueAnimator.ofFloat(0f, 1f);
                popoverAnimator.setDuration(popoverAnimationDuration);
                popoverAnimator.setInterpolator(popoverAnimationInterpolator);

                popoverAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator animation) {
                        // The commanded popover may change mid-animation
                        if (commandedPopover != null && Utils.arrayContainsInt(commandedPopoverGravities, commandedPopover.getGravity())) {
                            commandedPopover.setIndicatorColor(Utils.interpolateColors(animation.getAnimatedFraction(), StartingColor, TargetColor));
                        }
                    }
                });

                popoverAnimator.start();
            }

            if (context.mode != ConfirmationActionBar) {
                if (context.actionBar.backButton != null) {
                    // To clear up any possible remaining listener
                    context.actionBar.backButton.animate().cancel();
                    context.actionBar.backButton.animate().setDuration(200).translationX(- context.actionBar.backButton.getWidth()).start();
                }

                int stride = 0;

                if (context.actionBar.overflowButton != null) {
                    if (USE_STRIDE_ALPHA) {
                        context.actionBar.overflowButton.animate().alpha(0f);
                    }
                    context.actionBar.overflowButton.animate().scaleY(AnimationMinScale).scaleX(AnimationMinScale).setStartDelay(stride).setInterpolator(AnimationStrideInterpolator).setDuration(200).start();

                    stride += AnimationStride;
                }

                for (ActionItem item : context.contextItems) {
                    if (item.attachedView != null) {
                        if (USE_STRIDE_ALPHA) {
                            item.attachedView.animate().alpha(0f);
                        }
                        item.attachedView.animate().scaleY(AnimationMinScale).scaleX(AnimationMinScale).setStartDelay(stride).setInterpolator(AnimationStrideInterpolator).setDuration(200).start();

                        stride += AnimationStride;
                    }
                }
            }
            else {
                // Animation for confirmation context modes
                TimeInterpolator accelerate = new AccelerateInterpolator(2f);
                for (int i = 0, size = context.actionBar.getChildCount(); i < size; i++) {
                    context.actionBar.getChildAt(i).animate().setDuration(250).translationX(ConfirmationDisplacementDP * getResources().getDisplayMetrics().density)
                            .setInterpolator(accelerate).start();
                }
                if (contextSplitZone != null) {
                    for (int i = 0, size = contextSplitZone.getChildCount(); i < size; i++) {
                        contextSplitZone.getChildAt(i).animate().setDuration(250).translationX(ConfirmationDisplacementDP * getResources().getDisplayMetrics().density)
                                .setInterpolator(accelerate).start();
                    }
                }
            }

            if (actionBar != null) {
                actionBar.setVisibility(View.VISIBLE);
                if (actionBar.splitZone != null) {
                    actionBar.splitZone.setVisibility(View.VISIBLE);
                }

                // The previous context animation doesn't run for confirmation bars
                if (context.mode != ConfirmationActionBar) {
                    if (actionBar.backButton != null && context.actionBar.backButton != null) {
                        actionBar.backButton.setTranslationX(- context.actionBar.backButton.getWidth());
                        actionBar.backButton.animate().translationX(0f).setDuration(200).start();
                    }

                    int stride = 0;

                    for (ActionItem item : actionItems) {
                        if (item.attachedView != null) {
                            item.attachedView.setScaleY(AnimationMinScale);
                            item.attachedView.setScaleX(AnimationMinScale);
                            if (USE_STRIDE_ALPHA) {
                                item.attachedView.setAlpha(0f);
                                item.attachedView.animate().alpha(1f);
                            }
                            item.attachedView.animate().scaleY(1f).scaleX(1f).setDuration(200).setStartDelay(AnimationBaseDelay + stride).setInterpolator(AnimationStrideInterpolator).start();

                            stride += AnimationStride;
                        }
                    }

                    if (actionBar.overflowButton != null) {
                        actionBar.overflowButton.setScaleY(AnimationMinScale);
                        actionBar.overflowButton.setScaleX(AnimationMinScale);
                        if (USE_STRIDE_ALPHA) {
                            actionBar.overflowButton.setAlpha(0f);
                            actionBar.overflowButton.animate().alpha(1f);
                        }
                        actionBar.overflowButton.animate().scaleY(1f).scaleX(1f).setDuration(200).setStartDelay(AnimationBaseDelay + stride).setInterpolator(AnimationStrideInterpolator).start();

                        stride += AnimationStride;
                    }
                }

                if (actionBar.wrapper.navigationMode == LegacyActionBarView.TabsNavigationMode) {

                    if (actionBar.navigationContainer != null) {
                        final View NavigationContainer = actionBar.navigationContainer;
                        actionBar.navigationContainer.animate().alpha(1f).setDuration(200).setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(Animator animation) {
                                NavigationContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                            }

                            @Override
                            public void onAnimationEnd(Animator animation) {
                                NavigationContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                            }
                        }).start();
                    }

                }
            }
        }
        else {
            if (commandedPopover != null && Utils.arrayContainsInt(commandedPopoverGravities, commandedPopover.getGravity())) {
                commandedPopover.setIndicatorColor(TargetColor);
            }

            container.removeView(context.actionBar);
            if (context.actionBar.splitZone != null) {
                splitZone.removeView(context.actionBar.splitZone);
            }

            if (actionBar != null) {
                actionBar.setVisibility(View.VISIBLE);
                if (actionBar.splitZone != null) {
                    actionBar.splitZone.setVisibility(View.VISIBLE);
                }

                if (actionBar.backButton != null && context.actionBar.backButton != null) {
                    actionBar.backButton.setTranslationX(0f);
                }

                for (ActionItem item : actionItems) {
                    if (item.attachedView != null) {
                        item.attachedView.setScaleY(1f);
                        item.attachedView.setScaleX(1f);
                        if (USE_STRIDE_ALPHA) item.attachedView.setAlpha(1f);
                    }
                }

                if (actionBar.overflowButton != null) {
                    actionBar.overflowButton.setScaleY(1f);
                    actionBar.overflowButton.setScaleX(1f);
                    if (USE_STRIDE_ALPHA) actionBar.overflowButton.setAlpha(1f);
                }

                if (actionBar.wrapper.navigationMode == LegacyActionBarView.TabsNavigationMode) {

                    if (actionBar.navigationContainer != null) {
                        actionBar.navigationContainer.setAlpha(1f);
                    }

                }
            }
        }

        if (nextContext != null) visibleContext = nextContext.actionBar;

    }

    public void setCaretResource(int resource) {
        actionBarWrapper.setCaretResource(resource);
    }

    public void setDoneResource(int resource) {
        actionBarWrapper.setDoneResource(resource);
    }

    public void setBackgroundResource(int resource) {
        actionBarWrapper.setBackgroundResource(resource);
    }

    public void setBackgroundColor(int color) {
        actionBarWrapper.setBackgroundColor(color);
    }

    public int getBackgroundColor() {
        return actionBarWrapper.backgroundColor;
    }

    public void setRoundedCornersWithRadius(int[] corners, float radius) {
        this.roundedCorners = corners;
        this.roundRadius = radius;
    }

    public void setRippleHighlightColors(int background, int ripple) {
        actionBarWrapper.setRippleHighlightColors(background, ripple);
    }

    public void setOverflowResource(int resource) {
        actionBarWrapper.setOverflowResource(resource);
    }

    public int getOverflowResource() {
        return actionBarWrapper.getOverflowResource();
    }

    public void setLogoResource(int resource) {
        actionBarWrapper.setLogoResource(resource);
    }

    public int getLogoResource(int resource) {
        return actionBarWrapper.getLogoResource();
    }

    public void setLogoDrawable(Drawable drawable) {
        actionBarWrapper.setLogoDrawable(drawable);
    }

    public void setTitleResource(int resource) {
        actionBarWrapper.setTitleResource(resource);
    }

    public void setTitle(CharSequence title) {
        actionBarWrapper.setTitle(title);
    }

    public void setTitleAnimated(CharSequence title, int direction) {
        actionBarWrapper.setTitleAnimated(title, direction);
    }

    public void setTextColor(int color) {
        actionBarWrapper.setTextColor(color);
    }

    public void setBackMode(int mode) {
        actionBarWrapper.setBackMode(mode);
    }

    public void setSeparatorVisible(boolean visible) {
        actionBarWrapper.setSeparatorVisible(visible);
    }

    public void setSeparatorOpacity(float opacity) {
        actionBarWrapper.setSeparatorOpacity(opacity);
    }

    public void setSeparatorThickness(int thickness) {
        actionBarWrapper.setSeparatorThickness(thickness);
    }

    public void setInnerSeparatorOpacity(float opacity) {
        actionBarWrapper.setInnerSeparatorOpacity(opacity);
    }

    public void setBackButtonEnabled(boolean enabled) {
        actionBarWrapper.setBackButtonEnabled(enabled);
    }

    public void setBackButtonVisible(boolean visible) {
        actionBarWrapper.setBackButtonVisible(visible);
    }

    public void setCustomView(CustomViewProvider customViewProvider) {
        actionBarWrapper.setCustomView(customViewProvider);
    }

    public CustomViewProvider getCustomView() {
        return actionBarWrapper.getCustomView();
    }

    public void setNavigationMode(int mode) {
        actionBarWrapper.setNavigationMode(mode);
    }

    public LegacyNavigationElement addNavigationElement(int id, String name, int resource) {
        LegacyNavigationElement element = LegacyNavigationElement.make(id, name, resource);
        actionBarWrapper.addNavigationElement(element);
        return element;
    }

    public void clearNavigationElements() {
        actionBarWrapper.clearNavigationElements();
    }

    public void setSelectedNavigationIndex(int index) {
        actionBarWrapper.setSelectedNavigationIndex(index);
    }

    public void setSelectedNavigationElement(LegacyNavigationElement element) {
        actionBarWrapper.setSelectedNavigationElement(element);
    }

    public void setFillContainerEnabled(boolean enabled) {
        actionBarWrapper.setFillContainerEnabled(enabled);
    }

    public void setLandscapeUIEnabled(boolean enabled) {
        actionBarWrapper.setLandscapeUIEnabled(enabled);
    }

    public void setCommandedPopoverIndicatorWithGravities(Popover popover, int ... gravities) {
        commandedPopover = popover;
        commandedPopoverGravities = gravities;

        commandedPopover.setOnGravityChangedListener(new Popover.OnGravityChangedListener() {
            @Override
            public void onPopoverGravityChanged(Popover popover, int gravity) {
                if (!Utils.arrayContainsInt(commandedPopoverGravities, gravity)) {
                    popover.setIndicatorColor(0);
                }
                else {
                    if (getCurrentContextMode() != null) {
                        popover.setIndicatorColor(getCurrentContextMode().backgroundColor);
                    }
                }
            }
        });
        // TODO Update indicator now?
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setRetainInstance(true);
    }

    public int getSelectedNavigationIndex() {
        return actionBarWrapper.navigationElements.indexOf(actionBarWrapper.selectedNavigationElement);
    }

    public int getNavigationElementCount() {
        return actionBarWrapper.getNavigationElementCount();
    }

    public class ActionItemBuilder {
        int id; String title; int resource; boolean showTitle; boolean showAsIcon;

        private ActionItemBuilder() {}

        public ActionItemBuilder setId(int id) { this.id = id; return this; }
        public ActionItemBuilder setTitle(String title) { this.title = title; return this; }
        public ActionItemBuilder setResource(int resource) { this.resource = resource; return this; }
        public ActionItemBuilder setTitleVisible(boolean visible) { showTitle = visible; return this; }
        public ActionItemBuilder setShowAsIcon(boolean visible) { showAsIcon = visible; return this; }
        public ActionItem build() {return addItem(id, title, resource, showTitle, showAsIcon);}
    }

    public ActionItemBuilder buildItem() {
        return new ActionItemBuilder();
    }

    public ActionItem addItem(int id, String title, int resource, boolean showTitle, boolean showAsIcon) {
        return addItemToIndex(id, title, resource, showTitle, showAsIcon, actionItems.size());
    }

    public ActionItem addItemToIndex(int id, String title, int resource, boolean showTitle, boolean showAsIcon, int index) {
        ActionItem item = new ActionItem();

        item.setId(id);
        item.setName(title);
        item.setResource(resource);
        item.setTitleVisible(showTitle);
        item.setVisibleAsIcon(showAsIcon);
        item.setVisible(true);
        item.setEnabled(true);

        item.listener = this;

        actionItems.add(index, item);

        if (actionBar != null)
            actionBar.onItemAdded(item);

        return item;
    }

    public SpinnerItem addSpinner(int id) {
        return addSpinnerToIndex(id, actionItems.size());
    }

    public SpinnerItem addSpinnerToIndex(int id, int index) {
        SpinnerItem item = new SpinnerItem();

        item.setId(id);
        item.setVisible(true);
        item.setVisibleAsIcon(true);
        item.setTitleVisible(true);

        actionItems.add(index, item);

        if (actionBar != null)
            actionBar.onItemAdded(item);

        return item;
    }

    public ActionItem replaceItemWithId(int idPre, int id, String title, int resource, boolean showTitle, boolean showAsIcon) {
        ActionItem item = new ActionItem();

        int index = actionItems.indexOf(findItemWithId(idPre));

        item.setId(id);
        item.setName(title);
        item.setResource(resource);
        item.setTitleVisible(showTitle);
        item.setVisibleAsIcon(showAsIcon);
        item.setVisible(true);
        item.setEnabled(true);

        item.listener = this;

        actionItems.add(index, item);

        if (actionBar != null)
            actionBar.onItemReplaced(id, item);

        actionItems.remove(findItemWithId(idPre));

        return item;
    }

    public ActionItem removeItemWithId(int id) {
        for (ActionItem item : actionItems) {
            if (item.getId() == id) {
                actionItems.remove(item);
                if (actionBar != null) {
                    actionBar.onItemRemoved(item);
                }
                return item;
            }
        }

        return null;
    }

    public ActionItem removeItemWithTitle(String title) {
        for (ActionItem item : actionItems) {
            if (item.getName().equals(title)) {
                actionItems.remove(item);
                if (actionBar != null) {
                    actionBar.onItemRemoved(item);
                }
                return item;
            }
        }

        return null;
    }

    public int getNumberOfVisibleActionItems() {
        return actionBarWrapper.getNumberOfVisibleActionItems();
    }

    public void setOnLegacyActionSeletectedListener(OnLegacyActionSelectedListener listener) {
        this.listener = listener;
    }

    public void onAttach(Activity activity) {
        super.onAttach(activity);

        if (listener == null) listener = (OnLegacyActionSelectedListener) getActivity();
        if (externalContainer != null && useExternalContainer) setContainer(externalContainer);
        if (activity instanceof ContextModeChangedListener && strongContextListener == null) {
            contextListener = new WeakReference<ContextModeChangedListener>((ContextModeChangedListener) activity);
        }
    }


    public View onCreateView(LayoutInflater inflater, ViewGroup fragmentContainer, Bundle savedInstanceState) {
        if (useExternalContainer) return null;
        return createView();
    }

    protected static int obtainHeight(Resources res) {
        Utils.DPTranslator pixels = new Utils.DPTranslator(res.getDisplayMetrics().density);

        Configuration config  = res.getConfiguration();
        if (config.smallestScreenWidthDp < 600) {
            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                return pixels.get(LegacyActionBarView.PhoneLandscapeHeightDP);
            }
            else {
                return pixels.get(LegacyActionBarView.PhonePortraitHeightDP);
            }
        }
        else {
            return pixels.get(LegacyActionBarView.TabletHeightDP);
        }
    }

    protected View createView() {
        Context context = getActivity();

        int height = obtainHeight(getResources());

//        TypedValue tv = new TypedValue();
//        if (getActivity().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true)) {
//            height = TypedValue.complexToDimensionPixelSize(tv.data, getResources().getDisplayMetrics());
//        }

        if (actionBarWrapper.fillContainer) {
            height = ViewGroup.LayoutParams.MATCH_PARENT;
        }

        container = new FrameLayout(context);
        // TODO check for double height in case of portrait phone navigation
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height);
        container.setLayoutParams(params);

        actionBar = new LegacyActionBarView(context, this, actionBarWrapper);
        actionBarWrapper.actionBar = actionBar;
        actionBar.setActionItems(actionItems);

        container.addView(actionBar);

        boolean extraRowCondition = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT && getResources().getConfiguration().smallestScreenWidthDp < 600;

        LegacyActionBarView lastContext = actionBar;
        //Traverse the context list and show started contexts
        for (ContextBarWrapper wrapper : contextModes) {
            if (wrapper.started) {
                if (lastContext.wrapper.navigationMode != LegacyActionBarView.TabsNavigationMode
                        || !extraRowCondition)
                    lastContext.setVisibility(View.INVISIBLE);
                else {
                    if (lastContext.navigationContainer != null) {
                        lastContext.navigationContainer.setAlpha(0f);
                    }
                }
                wrapper.createBar();
                container.addView(wrapper.actionBar);
                lastContext = wrapper.actionBar;

                wrapper.contextBarListener.onContextBarActivated(wrapper);
            }
        }

        visibleContext = lastContext;

        return container;
    }

    public void closeAllContextModesAnimated(boolean animated) {
        while (contextModes.size() > 0) {
            contextModes.get(0).dismiss(animated);
        }
    }

    public void destroyAllCustomViews() {
        if (actionBar != null) {
            ViewGroup container = (ViewGroup) actionBar.getParent();
            for (int i = 0, size = container.getChildCount(); i < size; i++) {
                LegacyActionBarView actionBarView = (LegacyActionBarView) container.getChildAt(i);
                if (actionBarView.customViewContainer != null) {
                    actionBarView.customViewContainer.removeAllViews();
                }
            }
        }
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        initialized = true;
    }

    public boolean isRetainedInstance() {
        return initialized;
    }

    public void onDetach() {
        super.onDetach();
        externalContainer = null;
        splitZone = null;

        if (listener == getActivity()) listener = null;
    }

    public void onDestroyView() {
        super.onDestroyView();

        container = null;
        actionBar = null;

        visibleContext = null;

        actionBarWrapper.actionBar = null;
        actionBarWrapper.logoDrawable = null;

        for (ContextBarWrapper wrapper : contextModes) {
            wrapper.actionBar = null;
            wrapper.logoDrawable = null;
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

    public void showOverflow() {

        if (visibleContext != null)
            visibleContext.showOverflow();
    }

    public LegacyActionBarView getBaseActionBarView() {
        return actionBarWrapper.actionBar;
    }

    public LegacyActionBarView getCurrentActionBarView() {
        return visibleContext == null ? actionBarWrapper.actionBar : visibleContext;
    }

    public void onItemChanged(ActionItem item) {
        if (item.attachedView != null) {
            if (actionBar != null) {
                actionBar.onItemChanged(item);
            }
        }
    }

    public LegacyActionBar() {}

    public static LegacyActionBar getAttachableLegacyActionBar() {
        LegacyActionBar bar = new LegacyActionBar();
        bar.useExternalContainer = true;
        return bar;
    }

    public Popover.AnchorProvider obtainAnchorForItemWithID(final int ID) {
        return new Popover.AnchorProvider() {
            @Override
            public View getAnchor(Popover popover) {
                return findItemWithId(ID).attachedView;
            }
        };
    }

}
