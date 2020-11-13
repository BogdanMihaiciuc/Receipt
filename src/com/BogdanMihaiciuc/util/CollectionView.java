package com.BogdanMihaiciuc.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings({"UnusedDeclaration", "deprecation"})
public class CollectionView extends ScrollView {

    final static String TAG = CollectionView.class.toString();

    final static boolean DEBUG = false;
    final static boolean DEBUG_SCROLL = false;
    //    final static boolean DEBUG_GINGERBREAD = false;
    final static boolean DEBUG_DELETE = false;
    final static boolean DEBUG_SMOOTHNESS = false;
    final static boolean DEBUG_ATTACHMENT = false;
    final static boolean DEBUG_DATASETCHANGE = false;
    final static boolean DEBUG_BACKENDUNDO = false;
    final static boolean DEBUG_ANIMATED_CONTAINER = false;
    final static boolean DEBUG_UNNECESSARY_ANIMATIONS = false;
    final static boolean DEBUG_GRAVITY = false;
    final static boolean DEBUG_METADATA = false;
    final static boolean DEBUG_VARIABLE_COLUMN_COUNT = false;
    final static boolean DEBUG_COLLECTION_EVENT_DELEGATE = false;

    // The "Mystery" bug is a previously undocumented framework bug
    // When something calls requestLayout during a layout pass, that request isn't honored
    // and as such some views are not laid out correctly
    final static boolean DEBUG_MYSTERY = false;

    final static String ItemIndexKey = "itemIndex";
    final static String SectionIndexKey = "sectionIndex";

    final static int NoRow = -1;

    final static int BindToStart = 0;
    final static int BindToEnd = 1;

    final static int StandardBackburnerLimit = 15;

    public final static int TransactionScrollingModeTop = 0;
    public final static int TransactionScrollingModeNoScroll = 1;
    public final static int TransactionScrollingModeAnchor = 2;
    public final static int TransactionScrollingModeNavigate = 3;

    public final static int NavigationTransactionRightToLeft = 1;
    public final static int NavigationTransactionLeftToRight = -1;

    public final static TimeInterpolator StandardDeleteInterpolator = new AccelerateInterpolator(1.33f);
    public final static TimeInterpolator StandardMoveInterpolator = new AccelerateDecelerateInterpolator();
    public final static TimeInterpolator StandardInsertInterpolator = new DecelerateInterpolator(1.33f);

    static int lastItemUID = Integer.MIN_VALUE;
    static int lastSectionUID = Integer.MIN_VALUE;

    public boolean isRefreshViewsOnTransactionEnabled() {
        return refreshViewsOnTransactionEnabled;
    }

    public void setRefreshViewsOnTransactionEnabled(boolean refreshViewsOnTransactionEnabled) {
        this.refreshViewsOnTransactionEnabled = refreshViewsOnTransactionEnabled;
    }

    public interface ViewRunnable {
        public void runForView(View view, Object object, int viewType);
    }

    public interface AnchorInspector {
        public boolean isAnchor(Object object, int viewType);
    }

    public interface OnViewCollectedListener {
        public void onViewCollected(CollectionView collectionView, View view, int viewType);
    }

    public static class Section {

        final int sectionUID;
        int columnCount = 1;
        int viewType;

        public final String Tag;
        Object metadata;

        ArrayList<Item> content = new ArrayList<Item>();
        ArrayList<Item> historicContent;

        public Section(int viewType) {
            this.viewType = viewType;
            Tag = null;
            sectionUID = lastSectionUID++;
        }

        public Section(int viewType, String tag) {
            this.viewType = viewType;
            Tag = tag;
            sectionUID = lastSectionUID++;
        }

        Section(Section section) {
            sectionUID = section.sectionUID;
            columnCount = section.columnCount;
            viewType = section.viewType;
            Tag = section.Tag;
            metadata = section.metadata;

            content = new ArrayList<Item>(section.content);
        }

        Section createHistoricContent() {
            historicContent = new ArrayList<Item>(content);
            return this;
        }

        public Section setMetadata(Object metadata) {
            this.metadata = metadata;
            if (DEBUG_METADATA) Log.d(TAG, "Metadata has been set to: " + metadata);
            return this;
        }

        public Object getMetadata() {
            if (DEBUG_METADATA) Log.d(TAG, "Returning metadata: " + metadata);
            return metadata;
        }

        public int getViewType() { return viewType; }

        public void addAllObjects(List objects) {
            for (Object object : objects) {
                addObject(object);
            }
        }

        public void addObjects(Object ... objects) {
            for (Object object : objects) {
                addObject(object);
            }
        }

        public void addObject(Object object) {
            addObjectToIndex(object, content.size());
        }

        public void addObjectToIndex(Object object, int index) {
            Item wrapper = new Item();
            wrapper.item = object;
            wrapper.itemUID = lastItemUID++;
            wrapper.viewSubtype = 0;
            content.add(index, wrapper);
        }

        public Section clear() {
            content.clear();
            return this;
        }

        public boolean containsObject(Object object) {
            return containsObjectWithComparator(object, CollectionViewController.StandardComparator);
        }

        public boolean containsObjectWithComparator(Object object, CollectionViewController.ObjectComparator comparator) {
            int size = content.size();
            for (int i = 0; i < size; i++) {
                if (comparator.areObjectsEqual(content.get(i).item, object)) {
                    return true;
                }
            }

            return false;
        }

        public int indexOfObject(Object object) {
            return indexOfObjectWithComparator(object, CollectionViewController.StandardComparator);
        }

        public int indexOfObjectWithComparator(Object object, CollectionViewController.ObjectComparator comparator) {
            int size = content.size();
            for (int i = 0; i < size; i++) {
                if (comparator.areObjectsEqual(content.get(i).item, object)) {
                    return i;
                }
            }

            return -1;
        }

        int historicIndexOfObjectWithComparator(Object object, CollectionViewController.ObjectComparator comparator) {
            int size = historicContent.size();
            for (int i = 0; i < size; i++) {
                if (comparator.areObjectsEqual(historicContent.get(i).item, object)) {
                    return i;
                }
            }

            return -1;
        }

        public void sortWithComparator(final Comparator comparator) {
            Collections.sort(content, new Comparator<Item>() {
                @Override
                public int compare(Item item, Item item2) {
                    return comparator.compare(item.item, item2.item);
                }
            });
        }

        public Object removeObjectAtIndex(int index) {
            return content.remove(index).item;
        }

        public void removeObject(Object object) {
            removeObjectWithComparator(object, CollectionViewController.StandardComparator);
        }

        void removeObjectWithComparator(Object object, CollectionViewController.ObjectComparator comparator) {
            int size = content.size();
            for (int i = 0; i < size; i++) {
                if (comparator.areObjectsEqual(content.get(i).item, object)) {
                    content.remove(i);
                    return;
                }
            }
        }

        public Object getObjectAtIndex(int index) {
            return content.get(index).item;
        }

        public void setColumnCount(int columnCount) {
            this.columnCount = columnCount;
        }

        int getRowCount() {
            int rowCount = 0;
            rowCount = content.size() / columnCount;
            if (content.size() % columnCount != 0) {
                rowCount += 1;
            }
            return rowCount;
        }

        public int getSize() {
            return content.size();
        }
    }

    static class Item {
        Object item;
        int itemUID;
        int viewSubtype;
        VisibleView boundView;
    }

    class VisibleView {
        FrameLayout view;
        Item target;
        Section section;
        int retainCount;

        int row, column;

        private boolean initialized;
        private boolean unbound = false;

        VisibleView retain() {
            if (retainCount == 0) {
                Log.e(TAG, "Retaining a dead view!");
                new Throwable().printStackTrace();
            }
            if (unbound) return this;
            retainCount++;

            if (DEBUG_DELETE) {
                if (retainCount > 1) {
                    Log.e(TAG, "RetainCount for view of " + target.item + " was increased past 1!");
                }
            }

            return this;
        }

        void release() {
            if (unbound) return;
            retainCount--;
            if (retainCount == 0) {
                flushRipplesOnView(view);

                if (viewCollectedListener != null) {

                    viewCollectedListener.onViewCollected(CollectionView.this, view.getChildAt(0), section.viewType);
                }
                unbind();
            }
        }

        void unbind() {
            target.boundView = null;
            boundViews.remove(this);

            // "Hacky", but much faster than removing from the layout or changing visibility
//            view.setTop((int)(- 1000f * Density));
//            view.setY(- 1000f * Density);
            view.setVisibility(View.INVISIBLE);
//            container.removeView(view);

            // If the view still had a retainCount when it was unbound
            // it is dirty and should not be added to the backburner
            if (retainCount == 0) {
                if (backburner.get(section.viewType).size() < backburnerLimit /*|| !enforceBackburnerLimit*/) {
                    backburner.get(section.viewType).add(this);
                }
                else {
                    container.removeView(view);
                    if (DEBUG_DATASETCHANGE) Log.d(TAG, "The visibleView has been removed from the container because the backburner is full!");
                }
            }
            else {
                container.removeView(view);
                unbound = true;
                if (DEBUG_DATASETCHANGE) Log.d(TAG, "The visibleView has been removed from the container because it was dirty!");
            }
        }

        protected boolean isInitialized() {
            return initialized;
        }

        protected void setInitialized(boolean initialized) {
            if (DEBUG_MYSTERY) Log.d(TAG, "Setting initialized for " + (target == null ? target : target.item));
            if (DEBUG_MYSTERY) if (view.getLayoutParams().width == 0) {
                Log.e(TAG, "Bad call to setInitialized detected!");
                new Throwable().printStackTrace();
            }
            this.initialized = initialized;
        }
    }

    public static interface ReversibleAnimation {
        public void playAnimation(View view, Object object, int viewType);
        public void resetState(View view, Object object, int viewType);
    }

    // The Row class identifies a row within the CollectionView
    // One row class is created for each row, even if off-screen
    static class Row {
        int start;
        int end;
    }

    private final float Density;

    // The number of view types within the fluidCollectionView
    // This number is maintained automatically, and can increase during the lifetime of the CollectionView
    private int viewTypeCount;


    // The controller inflates and decorates views
    // This is created by the developer and supplied to the CollectionView
    private CollectionViewController controller;

    // This arrayList represents the main content displayed by the FluidCollecionView
    private ArrayList<Section> sections;

    // The heights list holds the height for each section item.
    // Changes in height must be handled by the CollectionView
    private ArrayList<Integer> heights = new ArrayList<Integer>();

    // The backburner has recycled views for each view type
    private ArrayList<ArrayList<VisibleView>> backburner = new ArrayList<ArrayList<VisibleView>>();
    private int backburnerLimit = StandardBackburnerLimit;

    // The container which holds all views
    private FrameLayout container;

    private int containerHeight;
    // Gravity specifies where views will be placed if the total height is less than the collection view height
    private int gravity = Gravity.TOP;

    // The Layout contains all the currently visible views
    private ArrayList<VisibleView> boundViews = new ArrayList<VisibleView>();
    private ArrayList<VisibleView> layout = new ArrayList<VisibleView>();

    // The EmptyView is shown when after a transaction, there are no rows
    private View emptyView;

    // This array contains all the "logical" rows
    // That is, its count is the same as the row count
    // and each object holds the startpoint and endpoint of its respective row
    private ArrayList<Row> logicalRows = new ArrayList<Row>();

    private Handler handler = new Handler();

    // The number of times disableViewCollection has been requested; If this is different than 0
    // views will not be bound or released during scroll
    private int disableViewCollectionRequests = 0;

    // The number of times disableInteractions has been requested; If this is different than 0
    // touch events received by the CollectionView will be intercepted and discarded
    private int disableInteractionRequests = 0;

    // If animationsEnabled is false, no animations will be played and all changes will happen instantly
    private boolean animationsEnabled = true;

    // When refreshViewsOnTransactionEnabled is true, refreshViews() will be called automatically during the dataset change
    private boolean refreshViewsOnTransactionEnabled = false;

    // When packedAnimations is true, the delete and insert animations run in parallel
    private boolean packedAnimations = false;

    // If enforceBackburnerLimit is true, new views will not be added to the backburner if it already
    // contains a certain amount of views
//    private boolean enforceBackburnerLimit = true;

    private TimeInterpolator deleteInterpolator = StandardDeleteInterpolator;
    private TimeInterpolator moveInterpolator = StandardMoveInterpolator;
    private TimeInterpolator insertInterpolator = StandardInsertInterpolator;

    private long deleteDuration = 200;
    private long moveDuration = 300;
    private long insertDuration = 200;

    private long deleteStride = 0;

    private ArrayList<Animator> activeAnimators = new ArrayList<Animator>();

    private AnchorInspector anchorInspector = null;

    // The viewCollectedListener is notified whenever a view object is collected in order to be reused
    private OnViewCollectedListener viewCollectedListener = null;

    // Whenever a view is moved to the backburner, the collection view will run through all the subviews with the ids
    // in this array, and cancel all the LegacyRippleDrawable animations currently running
    private int[] legacyRippleDrawableIDs;

    public final ReversibleAnimation StandardDeleteAnimator = new ReversibleAnimation() {
        @Override
        public void playAnimation(View view, Object object, int viewType) {
            playDeleteAnimation(view);
        }

        @Override
        public void resetState(View view, Object object, int viewType) {
            reverseDeleteAnimation(view);
        }
    };

    private ReversibleAnimation deleteAnimator = StandardDeleteAnimator;

    public final ReversibleAnimation StandardInsertAnimator = new ReversibleAnimation() {
        @Override
        public void playAnimation(View view, Object object, int viewType) {
            playInsertAnimation(view);
        }

        @Override
        public void resetState(View view, Object object, int viewType) {
        }
    };
    private ReversibleAnimation insertAnimator = StandardInsertAnimator;

    // The onScrollListener can be used by the developer to listen to scroll events
    public interface OnScrollListener {
        public void onScroll(CollectionView collectionView, int top, int amount);
    }

    public final static int OverScrollTop = 0;
    public final static int OverScrollBottom = 1;

    public interface OnOverScrollListener {
        public void onOverScroll(CollectionView collectionView, int amount, int direction);
        public void onOverScrollStopped(CollectionView collectionView);
    }

    private OnScrollListener onScrollListener;
    private OnOverScrollListener onOverScrollListener;
    private boolean overScrollEnabled = true;

    public interface TransactionListener {
        public void onTransactionStart();
        public void onTransactionEnd();
    }

    public static class TransactionListenerAdapter implements TransactionListener {
        public void onTransactionStart(){}
        public void onTransactionEnd(){}
    }

    private ArrayList<TransactionListener> transactionListeners = new ArrayList<TransactionListener>();

     // ************ METHODS *************
    private void generateContainerAndRows() {

        int pixelOffset = 0;

        for (Section section : sections) {
            int rowCount = section.content.size() / section.columnCount;
            if (section.content.size() % section.columnCount != 0) {
                rowCount += 1;
            }

            containerHeight += rowCount * heights.get(section.viewType);

            for (int j = 0; j < rowCount; j++) {
                Row row = new Row();
                row.start = pixelOffset;
                row.end = pixelOffset + heights.get(section.viewType);
                logicalRows.add(row);

                pixelOffset = pixelOffset + heights.get(section.viewType) + 1;
            }

        }

        if (gravity == Gravity.CENTER_VERTICAL || gravity == Gravity.CENTER) {
            if (logicalRows.size() > 0) {
                if (logicalRows.get(logicalRows.size() - 1).end < getHeight()) {
                    if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) Log.d(TAG, "Displacing rows by: " + (getHeight() / 2 - logicalRows.get(logicalRows.size() - 1).end / 2) + " from generateContainerAndRows");
                    displaceRows(getHeight() / 2 - logicalRows.get(logicalRows.size() - 1).end / 2);
                }
            }
        }

        if (container != null) {
            this.removeView(container);
        }

        if (logicalRows.size() > 0) {
            containerHeight = logicalRows.get(logicalRows.size() - 1).end;
        }
        else {
            View emptyView = controller != null ? controller.createEmptyView(this, LayoutInflater.from(getContext())) : null;
            int height = 1;
            if (emptyView != null) {
                if (emptyView.getLayoutParams() != null) height = emptyView.getLayoutParams().height;
                if (height < 1) height = 1;
                if (DEBUG_ANIMATED_CONTAINER) Log.d(TAG, "Measured height of emptyView has been determined: " + height);
            }
            containerHeight = height;
        }

        container = new CollectionViewContainer(this.getContext()) {

            int measuredHeight;

            public void setLayoutParams(ViewGroup.LayoutParams params) {
                super.setLayoutParams(params);
                measuredHeight = params.height;
            }

            protected void onMeasure(int i, int j) {
                super.onMeasure(i, j);
                setMeasuredDimension(getMeasuredWidth(), Math.max(CollectionView.this.getHeight(), measuredHeight));
            }
        };
        container.setMotionEventSplittingEnabled(false);

        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, containerHeight);
        container.setLayoutParams(params);

        if (DEBUG) {
            Log.d(TAG, "Created rows: ");
            for (Row row : logicalRows) {
                Log.d(TAG, "(" + row.start + ", " + row.end + ")");
            }
            Log.d(TAG, "Created container with height " + containerHeight);
        }

        this.addView(container);
        container.requestLayout();

    }

    private void regenerateRows() {
        logicalRows = new ArrayList<Row>();

        int pixelOffset = 0;

        containerHeight = 0;

        for (Section section : sections) {
            int rowCount = section.content.size() / section.columnCount;
            if (section.content.size() % section.columnCount != 0) {
                rowCount += 1;
            }
            containerHeight += rowCount * heights.get(section.viewType);

            for (int j = 0; j < rowCount; j++) {
                Row row = new Row();
                row.start = pixelOffset;
                row.end = pixelOffset + heights.get(section.viewType);
                logicalRows.add(row);

                pixelOffset = pixelOffset + heights.get(section.viewType) + 1;
            }

        }

        if (gravity == Gravity.CENTER_VERTICAL || gravity == Gravity.CENTER) {
            if (logicalRows.size() > 0) {
                if (logicalRows.get(logicalRows.size() - 1).end < getHeight()) {
                    if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) Log.d(TAG, "Displacing rows by: " + (getHeight() / 2 - logicalRows.get(logicalRows.size() - 1).end / 2) + " from regenerateRows()");
                    displaceRows(getHeight() / 2 - logicalRows.get(logicalRows.size() - 1).end / 2);
                }
            }
        }

    }

    private void displaceRows(int amount) {
        int i = 0;
        for (Row row : logicalRows) {
            row.start += amount;
            row.end += amount;
            if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) {
                Log.d(TAG, "Row " + i + " displaced to " + row.start);
            }
            i++;
        }
    }


    private FrameLayout wrapView(View instance, final int viewType) {

        FrameLayout instanceContainer = new CollectionViewContainer(getContext()) {
            public void onLayout(boolean changed, int left, int top, int right, int bottom) {
                super.onLayout(changed, left, top, right, bottom);
                if (getTag() != null) {
                    Object target;
                    target = ((VisibleView) getTag()).target;
                    if (target != null) {
                        target = ((Item) target).item;
                    }
                    else
                        target = getTag();
                    if (DEBUG_MYSTERY) Log.d(TAG, "Instance container for " + target + " laid out to " + (right - left) + ", " + (bottom - top));
                    if (DEBUG_MYSTERY) Log.d(TAG, "The visibile view initialization status is: " + ((VisibleView) getTag()).isInitialized());
                }
                else {
                    if (DEBUG_MYSTERY) Log.d(TAG, "Instance container for null laid out to " + (right - left) + ", " + (bottom - top));
                }
                if (DEBUG_MYSTERY) Log.d(TAG, "Measured dimensions for container are: " + (getMeasuredWidth()) + ", " + (getMeasuredHeight()));
                if (DEBUG_MYSTERY) Log.d(TAG, "but the params specify the following dimensions: " + getLayoutParams().width + ", " + getLayoutParams().height);
            }

            public void setLayoutParams(ViewGroup.LayoutParams params) {
                super.setLayoutParams(params);
                if (DEBUG_MYSTERY) if (params.width == 0) {
                    Log.e(TAG, "Bad call to setLayoutParams trapped!");
                    new Throwable().printStackTrace();
                }
            }
        };
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, heights.get(viewType));
//        instanceContainer.setLayoutParams();
        instanceContainer.addView(instance);

        ((LayoutParams) instance.getLayoutParams()).gravity = Gravity.CENTER;
//        instanceContainer.setTop((int)(- 1000f * Density));
//        instanceContainer.setY(- 1000f * Density);
        instanceContainer.setVisibility(INVISIBLE);

        container.addView(instanceContainer, params);

        return instanceContainer;

    }

    private void getHeightsForViewTypes() {
        heights = new ArrayList<Integer>();

        for (int i = 0; i < viewTypeCount; i++) {
            heights.add(0);
            getMeasuredHeightOfViewType(i);
        }

        if (DEBUG) {
            Log.d(TAG, "ViewType heights are: ");
            for (Integer i : heights) {
                Log.d(TAG, String.valueOf(i));
            }
        }
    }

    private void getMeasuredHeightOfViewType(int viewType) {
        // This always called before any views are created to be added to the layout
        if (backburner.get(viewType).size() > 0) {
            FrameLayout view = backburner.get(viewType).get(0).view;
            view.getChildAt(0).measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int height = view.getMeasuredHeight();
            if (height < view.getLayoutParams().height) {
                height = view.getLayoutParams().height;
            }
            ViewGroup.LayoutParams params = view.getChildAt(0).getLayoutParams();
            if (params instanceof MarginLayoutParams) {
                height += ((MarginLayoutParams) params).topMargin;
                height += ((MarginLayoutParams) params).bottomMargin;
            }
            heights.set(viewType, height);
            if (DEBUG_SMOOTHNESS) {
                Log.d(TAG, "Measured height of viewType " + viewType + " is " + heights.get(viewType));
            }
        }
        else {
            FrameLayout measureContainer;
            if (container == null)
                measureContainer = new FrameLayout(getContext());
            else
                measureContainer = container;

            View instance = controller.createView(viewType, measureContainer, LayoutInflater.from(getContext()));
            instance.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            heights.set(viewType, instance.getMeasuredHeight());
            int height = instance.getMeasuredHeight();
            if (height < instance.getLayoutParams().height) {
                height = instance.getLayoutParams().height;
            }
            ViewGroup.LayoutParams params = instance.getLayoutParams();
            if (params instanceof MarginLayoutParams) {
                height += ((MarginLayoutParams) params).topMargin;
                height += ((MarginLayoutParams) params).bottomMargin;
            }
            heights.set(viewType, height);

            if (DEBUG_SMOOTHNESS) {
                Log.d(TAG, "Measured height of viewType " + viewType + " is " + heights.get(viewType));
            }

            //Add this view to the backburner
//            VisibleView visibleView = new VisibleView();
//            visibleView.retainCount = 0;
//            visibleView.view = wrapView(instance, viewType);
//            backburner.get(viewType).add(visibleView);
        }
    }

    private VisibleView getViewForItem(final Item item, Section section) {
        // Run through the bound views. If there is already a view bound to this item, return that
        if (item.boundView != null) {
            return item.boundView;
        }

        FrameLayout measureContainer;
        if (container == null)
            measureContainer = new FrameLayout(getContext());
        else
            measureContainer = container;

        // If control exits the loop, the view wasn't already bound and must be created
        VisibleView visibleView;
        FrameLayout view;
        if (backburner.get(section.viewType).size() > 0) {
            visibleView = backburner.get(section.viewType).remove(backburner.get(section.viewType).size() - 1);
        }
        else {
            visibleView = new VisibleView();
            if (DEBUG_ATTACHMENT) Log.d(TAG, "Created a new view for " + item.item);
            view = wrapView(controller.createView(section.viewType, measureContainer, LayoutInflater.from(getContext())), section.viewType);
            view.setTag(visibleView);
            visibleView.view = view;
        }

        visibleView.target = item;
        visibleView.section = section;
        controller.configureView(visibleView.view.getChildAt(0), item.item, section.viewType);

        return visibleView;
    }

    // Gets the index of the logicalRow that should contain this item
    private int getRowOfItem(Item item) {
        int row = 0;
        Section section = null;
        int sectionCount = sections.size();
        int itemIndex = -1;
        for (int i = 0; i < sectionCount; i++){
            section = sections.get(i);
            itemIndex = section.content.indexOf(item);
            if (itemIndex == -1) {
                row += section.getRowCount();
            }
            else break;
        }

        if (itemIndex == -1) return NoRow;

        // At the end of the for-loop, section is the item's section
        row += itemIndex / section.columnCount;

        if (DEBUG) {
            Log.d(TAG, "Row for object with index " + itemIndex + ", within section with UID " + section.sectionUID +
                    " is " + row);
        }

        return row;
    }

    private int getHistoricRowOfItem(Item item, Section historicSection, ArrayList<Section> historicSections, ArrayList<Row> historicRows) {

        int row = 0;
        Section section;
        int sectionCount = historicSections.size();
        int itemIndex = -1;
        for (int i = 0; i < sectionCount; i++){
            section = sections.get(i);
            if (section == historicSection) {
                itemIndex = section.historicContent.indexOf(item);
                break;
            }
            else {
                row += section.historicContent.size() / section.columnCount + (section.historicContent.size() % section.columnCount == 0 ? 0 : 1);
            }
        }

        if (itemIndex == -1) return NoRow;

        // At the end of the for-loop, section is the item's section
        row += itemIndex / historicSection.columnCount;

        return row;
    }

    private void bindView(VisibleView visibleView, int sectionUID, Item item, int bindTarget) {
        // Get the position of the item
        int row = 0;
        Section section = null;
        int sectionCount = sections.size();
        for (int i = 0; i < sectionCount; i++){
            section = sections.get(i);
            if (section.sectionUID != sectionUID) {
                row += section.getRowCount();
            }
            else break;
        }

        // At the end of the for-loop, section is the item's section
        int itemIndex = section.content.indexOf(item);
        row += itemIndex / section.columnCount;

        int column = itemIndex % section.columnCount;

        bindViewToPosition(visibleView, section, item, bindTarget, row, column);
    }

    private void bindViewToPosition(final VisibleView visibleView, Section section, Item item, int bindTarget, int row, int column) {

        int containerWidth = container.getWidth();
        final boolean SchedulePosition = containerWidth == 0;
        if (containerWidth == 0) {
            containerWidth = container.getMeasuredWidth();
        }
        int columnWidth = containerWidth / section.columnCount;
        if (DEBUG_VARIABLE_COLUMN_COUNT) {
            Log.d(TAG, "Column width has been determined: " + columnWidth);
        }

        // Create a VisibleView object for the current view
        visibleView.target = item;
        item.boundView = visibleView;
        // The visibleView is implicitly retained once by the layout
        visibleView.retainCount = 1;
        visibleView.column = column;
        visibleView.section = section;
        visibleView.row = row;

        // And add it to the boundViews array
        boundViews.add(visibleView);

        // Add it to the container and the layout as well
        View view = visibleView.view;
        if (!visibleView.isInitialized()) {
            // if initialization can't be performed instantly, defer it until layout has run
            if (columnWidth > 0) {
                visibleView.setInitialized(true);
            }
            else {
                if (DEBUG) Log.w(TAG, "Initialization will be postponed until the layout has run.");
                final Section Section = section;
                getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (container.getWidth() > 0) {
                            getViewTreeObserver().removeGlobalOnLayoutListener(this);

                            visibleView.setInitialized(true);
                            int columnWidth = container.getWidth() / Section.columnCount;

                            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(columnWidth, heights.get(Section.viewType));
                            visibleView.view.setLayoutParams(params);
                        }
                    }
                });
            }
            if (DEBUG_MYSTERY) Log.d(TAG, "Initializing view for " + visibleView.target.item + " with width " + columnWidth);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(columnWidth, heights.get(section.viewType));
            view.setLayoutParams(params);
            if (DEBUG_SMOOTHNESS) {
                Log.w(TAG, "Uninitialized view had to be initialized!");
            }
        }
        view.setTag(visibleView);

        if (bindTarget == BindToEnd) {
            layout.add(visibleView);
        }
        else {
            layout.add(0, visibleView);
        }

//        if (DEBUG_GINGERBREAD) {
//            params.topMargin = logicalRows.get(row).start;
//            params.leftMargin = columnWidth * column;
//            view.setLayoutParams(params);
//        }
//        else {
        view.setY(logicalRows.get(row).start);
        view.setX(columnWidth * column);
//        view.setTop(logicalRows.get(row).start);
//        view.setLeft(columnWidth * column);
//        view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
//        view.layout(columnWidth * column, logicalRows.get(row).start, (columnWidth + 1) * column, logicalRows.get(row).end);
//        }

//        container.addView(view);

        view.setVisibility(VISIBLE);

        // KIT KAT fix
        // Force the container to invalidate itself to prevent views being invisible
        // TODO Highlighted bugfix
        if (Build.VERSION.SDK_INT >= 19) {
            container.postInvalidate();
        }

        if (SchedulePosition) {
            final Section Section = section;
            final float Column = column;
            final View View = view;
            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (container.getWidth() > 0) {
                        getViewTreeObserver().removeGlobalOnLayoutListener(this);


                        View.setX((container.getWidth() / Section.columnCount) * Column);
                    }
                }
            });
        }

        if (DEBUG) {
            Log.d(TAG, "Bound view for item " + section.content.indexOf(item) + " within section UID " + section.sectionUID + " to row " + row);
        }

        if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) Log.d(TAG, "Bound " + item.item + " to: " + logicalRows.get(row).start);

    }


    private void bindViewToHistoricPosition(VisibleView visibleView, Section section, Item item, int bindTarget, int row, int column, ArrayList<Row> historicRows) {

        int containerWidth = container.getWidth();
        int columnWidth = containerWidth / section.columnCount;

        // Create a VisibleView object for the current view
        visibleView.target = item;
        item.boundView = visibleView;
        // The visibleView is implicitly retained once by the layout
        visibleView.retainCount = 1;
        visibleView.column = column;
        visibleView.section = section;
        visibleView.row = row;

        // And add it to the boundViews array
        boundViews.add(visibleView);

        // Add it to the container and the layout as well
        View view = visibleView.view;
        if (!visibleView.isInitialized()) {
            visibleView.setInitialized(true);
            if (DEBUG_MYSTERY) Log.d(TAG, "Initializing view for " + visibleView.target.item + " with width " + columnWidth);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(columnWidth, heights.get(section.viewType));
            view.setLayoutParams(params);
            if (DEBUG_SMOOTHNESS) {
                Log.w(TAG, "Uninitialized view had to be initialized!");
            }
        }
        view.setTag(visibleView);

        if (bindTarget == BindToEnd)
            layout.add(visibleView);
        else
            layout.add(0, visibleView);

        view.setY(historicRows.get(row).start);
        view.setX(columnWidth * column);
//        view.setTop(historicRows.get(row).start);
//        view.setLeft(columnWidth * column);
//        view.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
//        view.layout(columnWidth * column, historicRows.get(row).start, (columnWidth + 1) * column, historicRows.get(row).end);

//        container.addView(view);
        view.setVisibility(VISIBLE);

        // KIT KAT fix
        // Force the container to invalidate itself to prevent views being invisible
        // TODO Highlighted bugfix
        if (Build.VERSION.SDK_INT >= 19) {
            container.postInvalidate();
        }

        if (DEBUG) {
            Log.d(TAG, "Bound view for item " + section.content.indexOf(item) + " within section UID " + section.sectionUID + " to row " + row);
        }

    }

    long nanoTime;

    // This is where it all happens
    // in onScrollChanged, it is verified which views have gone off-screen and which new rows have appeared which need views set up
    @Override
    public void onScrollChanged(int left, int top, int oldLeft, int oldTop) {

        // KIT KAT FIX
        boolean invalidationNeeded = true; // becomes true if any view is bound or unbound as a result of this scroll

        if (DEBUG_SMOOTHNESS) {
            nanoTime = System.nanoTime();
        }

        if (top != getScrollY()) {
            Log.w(TAG, "Mismatch between reported top(" + top + ") and scrollY(" + getScrollY() + ")");
            top = getScrollY();
        }

        if (layout.size() == 0 && logicalRows.size() > 0) {
            preloadViewsBetweenRows(getFirstVisibleRowIndex(), getLastVisibleRowIndex() - getFirstVisibleRowIndex() + 1);
        }

        if (disableViewCollectionRequests == 0 && layout.size() > 0) {

//            long nanoTime = System.nanoTime();

            VisibleView visibleView;
            int scrollViewHeight = getHeight();

            if (top - oldTop > 0) {
                // When scrolling down, only need to remove the views that get scrolled up
                // and add views to the bottom

                // These can be calculated before changing the layout since they don't change
                // However, the last visible view can be removed if the scroll is distance is more than
                // "one screen" long
                int currentRow = layout.get(layout.size() - 1).row; // the last visible row
                Item currentItem = layout.get(layout.size() - 1).target; // the last visible item
                Section currentSection = layout.get(layout.size() - 1).section; // the last visible section

                // First release views which have gone off-screen
                // this is so they are added to the backburner to be available if any views have to be added

                // Check for views scrolled up
                for (int i = 0; i < layout.size(); i++) {
                    visibleView = layout.get(i);
                    if (logicalRows.get(visibleView.row).end < top) {
                        visibleView.release();
                        layout.remove(visibleView);
                        i--;
                        invalidationNeeded = true;
                    }
                    else {
                        break;
                    }
                }

                // This will rebuild the whole hierarchy if there no views in the layout. This is very slow and it shouldn't happen in normal conditions
                if (layout.size() == 0) {
                    preloadViewsBetweenRows(getFirstVisibleRowIndex(), getLastVisibleRowIndex() - getFirstVisibleRowIndex() + 1);
                }
                else {
                    // Add the view from the bottom down, until reaching an invisible item

                    // Only need to add views if the last visible row's end is higher than the visible area's bottom margin
                    int bottomMargin = top + scrollViewHeight;
                    if (logicalRows.get(currentRow).end < bottomMargin) {
                        int sectionIndex = sections.indexOf(currentSection);
                        int itemIndex = currentSection.content.indexOf(currentItem) + 1;

                        int startingSize = layout.size();

                        // The last visible section doesn't start from 0 and is treated separately
                        int sectionItemCount = currentSection.content.size();
                        int sectionCount = sections.size();

                        currentRow++;
                        int rowCount = logicalRows.size();
                        if (currentRow < rowCount) do {
                            // If the current item index is over the section item count, it's time to move on to the next section
                            if (itemIndex >= sectionItemCount) {
                                sectionIndex += 1;
                                if (sectionIndex >= sectionCount) break;
                                currentSection = sections.get(sectionIndex);
                                sectionItemCount = currentSection.content.size();
                                itemIndex = 0;
                            }
                            if (DEBUG_VARIABLE_COLUMN_COUNT) {
                                Log.d(TAG, "Current section has " + currentSection.columnCount + " columns for layout.");
                            }
                            // Bind an entire row
                            for (int i = 0; i < currentSection.columnCount && itemIndex < sectionItemCount; i++) {
                                bindItemToPositionIfVisible(currentSection.content.get(itemIndex), currentSection, BindToEnd, currentRow, i);
                                itemIndex++;
                                invalidationNeeded = true;
                            }
                            // Move on to the next row.
                            // If guards against zero-size sections with no rows incorrectly advancing the current row
                            if (currentSection.content.size() > 0) currentRow++;
                            if (currentRow >= rowCount) break;
                        } while (logicalRows.get(currentRow).start < bottomMargin);

                        if (DEBUG_SCROLL) {
                            Log.d(TAG, "Layout size is: " + layout.size() + " after binding " + (startingSize - layout.size()) + " views from the bottom from the current section.");
                        }

                        if (DEBUG_SCROLL) {
                            Log.d(TAG, "Layout size is: " + layout.size() + " after binding " + (startingSize - layout.size()) + " views from the bottom (total).");
                        }
                    }
                }
            }
            else {
                // When scrolling up, views on the bottom need to be removed, and views are added to the top
                int currentRow = layout.get(0).row;
                Item currentItem = layout.get(0).target;
                Section currentSection = layout.get(0).section;

                // First release views which have gone off-screen
                // this is so they are added to the backburner to be available if any views have to be added

                // Check for views scrolled down
                for (int i = layout.size() - 1; i >= 0; i--) {
                    visibleView = layout.get(i);
                    if (logicalRows.get(visibleView.row).start > top + scrollViewHeight) {
                        visibleView.release();
                        layout.remove(visibleView);
                        if (DEBUG_GRAVITY) {
                            if (gravity != Gravity.TOP) {
                                Log.d(TAG, visibleView.target.item + " unbound; out of view! the bottom margin is: " + (top + scrollViewHeight) + "; the top is: " + top);
                            }
                        }
                        invalidationNeeded = true;
                    }
                    else {
                        break;
                    }
                }

                // This will rebuild the whole hierarchy if there no views in the layout. This is very slow and it shouldn't happen in normal conditions
                if (layout.size() == 0) {
                    preloadViewsBetweenRows(getFirstVisibleRowIndex(), getLastVisibleRowIndex() - getFirstVisibleRowIndex() + 1);
                }
                else {

                    // Add views to the top first, starting from the first visible item and then moving up
                    // until reaching an item that is out of the visible area

                    // Only need to add views if the first visible row's start point is lower than the visible start point
                    if (logicalRows.get(currentRow).start > top) {
                        int sectionIndex = sections.indexOf(currentSection);
                        int itemIndex = currentSection.content.indexOf(currentItem) - 1;

                        int startingSize = layout.size();
                        int startingColumn;

                        // The last visible section doesn't start from 0 and is treated separately
                        int sectionItemCount;

                        currentRow--;
                        if (currentRow >= 0) do {
                            // If the current item index is over the section item count, it's time to move on to the next section
                            if (itemIndex < 0) {
                                sectionIndex -= 1;
                                if (sectionIndex < 0) break;
                                currentSection = sections.get(sectionIndex);
                                sectionItemCount = currentSection.content.size();
                                itemIndex = sectionItemCount - 1;
                            }
                            if (DEBUG_VARIABLE_COLUMN_COUNT) {
                                Log.d(TAG, "Current section has " + currentSection.columnCount + " columns for layout.");
                            }
                            startingColumn = itemIndex % currentSection.columnCount;
                            // Bind an entire row
                            for (int i = startingColumn; i >= 0 && itemIndex >= 0; i--) {
                                bindItemToPositionIfVisible(currentSection.content.get(itemIndex), currentSection, BindToStart, currentRow, i);
                                itemIndex--;
                                invalidationNeeded = true;
                            }
                            // Move on to the next row.
                            // If guards against zero-size sections with no rows incorrectly advancing the current row
                            if (currentSection.content.size() > 0) currentRow--;
                            if (currentRow < 0) break;
                        } while (logicalRows.get(currentRow).end > top);

                        if (DEBUG_SCROLL) {
                            Log.d(TAG, "Layout size is: " + layout.size() + " after binding " + (startingSize - layout.size()) + " views from the bottom from the current section.");
                        }

                        if (DEBUG_SCROLL) {
                            Log.d(TAG, "Layout size is: " + layout.size() + " after binding " + (startingSize - layout.size()) + " views from the bottom (total).");
                        }
                    }
                }

            }

            if (DEBUG_SMOOTHNESS) Log.d(TAG, "OnScrollChanged took " + (System.nanoTime() - nanoTime)/1000000 + " ms.");

        }

        super.onScrollChanged(left, top, oldLeft, oldTop);

        // Kit Kat bugfix
        if (Build.VERSION.SDK_INT >= 19) {
            if (container != null && invalidationNeeded) {
                container.invalidate();
            }
        }

        // Finally, if there is onScrollListener set, notify it
        if (onScrollListener != null) {
            onScrollListener.onScroll(this, top, top - oldTop);
        }
    }

    @Override
    public void computeScroll() {
        if (DEBUG_SMOOTHNESS) nanoTime = System.nanoTime();
        super.computeScroll();
        if (DEBUG_SMOOTHNESS) Log.d(TAG, "OnScrollChanged took " + (System.nanoTime() - nanoTime)/1000000 + " ms.");
    }

    private void requestDisableViewCollection() {
        disableViewCollectionRequests++;
    }

    private void requestEnableViewCollection() {
        disableViewCollectionRequests--;
    }

    public void requestDisableInteractions() {
        disableInteractionRequests++;
    }

    public void requestEnableInteractions() {
        disableInteractionRequests--;
    }

    public boolean areInteractionsEnabled() {
        return disableInteractionRequests == 0;
    }

    public void endAllAnimations() {
        int sizePre;
        for (int i = 0; i < boundViews.size(); i++) {
            sizePre = boundViews.size();
            boundViews.get(i).view.animate().cancel();
            if (boundViews.size() != sizePre)
                i--;
        }
        while (activeAnimators.size() > 0) {
            activeAnimators.get(0).cancel();
        }
    }

    public void setAnimationsEnabled(boolean animationsEnabled) {
        this.animationsEnabled = animationsEnabled;

        // If animations are disabled, immediately end all currently playing animations
        if (!animationsEnabled) {
            int sizePre;
            for (int i = 0; i < boundViews.size(); i++) {
                sizePre = boundViews.size();
                boundViews.get(i).view.animate().cancel();
                if (boundViews.size() != sizePre)
                    i--;
            }
            while (activeAnimators.size() > 0) {
                activeAnimators.get(0).cancel();
            }
        }
    }

    public boolean areAnimationsEnabled() { return animationsEnabled; }

    private boolean frozen;

    public void invalidate() {
        if (!frozen) {
            super.invalidate();

            //KIT KAT FIX TODO
            if (Build.VERSION.SDK_INT >= 19) {
                if (container != null) container.invalidate();
            }
        }
    }


    private boolean verticalScrollBarEnabled = true;

    public void setVerticalScrollBarEnabled(boolean enabled) {
        verticalScrollBarEnabled = enabled;
        super.setVerticalScrollBarEnabled(enabled);
    }

    private int frozenScrollBarFadeDuration;
    private int frozenScrollBarFadeDelay;

    public void freeze() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
            if (frozen) {
                frozenScrollBarFadeDelay = getScrollBarDefaultDelayBeforeFade();
                frozenScrollBarFadeDuration = getScrollBarFadeDuration();

                setScrollBarFadeDuration(0);
                setScrollBarDefaultDelayBeforeFade(0);
            }
        }

        setWillNotDraw(true);

        frozen = true;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        requestDisableInteractions();
    }

    public void thaw() {
        frozen = false;
        setVerticalScrollBarEnabled(true);
        setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        requestEnableInteractions();
        invalidate();

        setWillNotDraw(false);

        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.JELLY_BEAN) {
            setScrollBarFadeDuration(frozenScrollBarFadeDuration);
            setScrollBarDefaultDelayBeforeFade(frozenScrollBarFadeDelay);
        }
    }

    @Override
    protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
//        if (containerHeight <= getHeight()) return;

        if (disableInteractionRequests == 0)
            super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
    }

//    public void disableBackburnerLimit() {
//        enforceBackburnerLimit = false;
//    }

//    public void enforceBackburnerLimit() {
//        enforceBackburnerLimit = true;
//
//        for (ArrayList<VisibleView> burner : backburner) {
//            if (burner.size() > 0) {
//                while (burner.size() > burner.get(0).section.columnCount * MaximumCollectedRowCount)
//                    burner.remove(burner.size() - 1);
//            }
//        }
//    }

    private void bindInitialViews(CollectionViewController.SavedPosition savedState) {

        // Prevent view collection, as there might be scrolling involved
        requestDisableViewCollection();

        int row;
        try {
            row = getRowOfItem(sections.get(savedState.sectionIndex).content.get(savedState.itemIndex));

            scrollTo(0, logicalRows.get(row).start);
        }
        catch (Exception e) {
            row = 0;
            savedState = new CollectionViewController.SavedPosition();
            savedState.itemIndex = 0;
            savedState.sectionIndex = 0;

            scrollTo(0, 0);
        }

        // Add the view from the bottom down, until reaching an invisible item
        boolean passComplete = false;

        int sectionItemCount;
        int sectionCount = sections.size();
        int itemIndex = savedState.itemIndex;
        // Make sure to start with the first column
        if (sections.size() > 0) {
            if (sections.get(savedState.sectionIndex).content.size() > itemIndex) {
                itemIndex -= itemIndex % sections.get(savedState.sectionIndex).columnCount;
            }
        }

        // Run through all the sections and their items and bind views until reaching a non-visible item
        for (int i = savedState.sectionIndex; i < sectionCount && !passComplete; i++) {
            Section section = sections.get(i);
            sectionItemCount = section.content.size();
            for (int j = itemIndex; j < sectionItemCount && !passComplete; j++) {
                Item item = section.content.get(j);
                passComplete = !bindItemIfVisible(item, section, BindToEnd);

                if (passComplete) {
                    if (DEBUG) Log.d(TAG, "Initial binding finished with object at index " + j + " from section with UID " + section.sectionUID);
                }
            }
            itemIndex = 0;
        }

        // Re-enable view collection
        requestEnableViewCollection();

    }

    private void bindInitialViewsFromSavedState(CollectionViewController.SavedPosition savedState, int height) {

        int row;
        try {
            row = getRowOfItem(sections.get(savedState.sectionIndex).content.get(savedState.itemIndex));
        }
        catch (Exception e) {
            row = 0;
            savedState = new CollectionViewController.SavedPosition();
            savedState.itemIndex = 0;
            savedState.sectionIndex = 0;
        }

        // Add the view from the bottom down, until reaching an invisible item
        boolean passComplete = false;

        int sectionItemCount;
        int sectionCount = sections.size();
        int itemIndex = savedState.itemIndex;
        // Make sure to start with the first column
        if (sections.size() > 0) {
            if (sections.get(savedState.sectionIndex).content.size() > itemIndex) {
                itemIndex -= itemIndex % sections.get(savedState.sectionIndex).columnCount;
            }
        }



        // Run through all the sections and their items and bind views until completing the visible height
        for (int i = savedState.sectionIndex; i < sectionCount && !passComplete; i++) {
            Section section = sections.get(i);
            sectionItemCount = section.content.size();
            for (int j = itemIndex; j < sectionItemCount && !passComplete; j++) {
                Item item = section.content.get(j);
                int currentRow = bindItem(item, section, BindToEnd);
                if (logicalRows.get(currentRow).end > logicalRows.get(row).start + height) passComplete = true;

                if (passComplete) {
                    if (DEBUG) Log.d(TAG, "Initial binding finished with object at index " + j + " from section with UID " + section.sectionUID);
                }
            }
            itemIndex = 0;
        }

    }

    private void clearState() {

        layout.clear();
        backburner.clear();
        heights.clear();
        removeView(container);
        while (boundViews.size() > 0) {
            boundViews.get(0).unbind();
        }

    }

    // This will check if the item is visible, if it is, it will bind the appropriate view and return true
    // If the item already had a bound view, this will retain that view and return true
    // If the item is not visible, it will return false
    private boolean bindItemIfVisible(Item item, Section section, int bindTarget) {
        int rowIndex = getRowOfItem(item); // WARNING - SLOWWWWW
        int itemIndex = section.content.indexOf(item);
        int column = itemIndex % section.columnCount;
        return bindItemToPositionIfVisible(item, section, bindTarget, rowIndex, column);
    }

    // This returns the row index of the item
    private int bindItem(Item item, Section section, int bindTarget) {
        int rowIndex = getRowOfItem(item); // WARNING - SLOWWWWW
        int itemIndex = section.content.indexOf(item);
        int column = itemIndex % section.columnCount;
        bindItemToPosition(item, section, bindTarget, rowIndex, column);

        return rowIndex;
    }

    private boolean bindItemToPositionIfVisible(Item item, Section section, int bindTarget, int rowIndex, int column) {
        Row row = logicalRows.get(rowIndex);
        if (row.start < getScrollY() + getHeight() && row.end > getScrollY()) {
            bindItemToPosition(item, section, bindTarget, rowIndex, column);
            return true;
        }
        else {
            if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) Log.d(TAG, "Failed to bind " + item.item + "; out of view!");

            if (DEBUG) {
                Log.d(TAG, "Trying to bind an item to an invisible position!");
                Log.d(TAG, "ActionItem area is (" + row.start + ", " + row.end + "), visible area is (" + getScrollY() + ", " + (getScrollY() + getHeight()) + ")");
            }

            return false;
        }

    }

    private VisibleView bindItemToPosition(Item item, Section section, int bindTarget, int rowIndex, int column) {

        if (item.boundView != null) {
            if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) Log.d(TAG, "Item " + item.item + " already had a bound view; retaining.");

            item.boundView.retain();
            if (bindTarget == BindToEnd) layout.add(item.boundView);
            else layout.add(0, item.boundView);

            return item.boundView;
        }
        else {
            if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) Log.d(TAG, "Bound a new view to " + item.item + ".");
            VisibleView viewForObject = getViewForItem(item, section);
            bindViewToPosition(viewForObject, section, item, bindTarget, rowIndex, column);

            return viewForObject;
        }

    }

    private boolean forceBindItemToHistoricPosition(Item item, Section section, int bindTarget, ArrayList<Section> historicSections, ArrayList<Row> historicRows) {

        int row = getHistoricRowOfItem(item, section, historicSections, historicRows);
        if (row == NoRow) return false;
        int column = section.historicContent.indexOf(item) % section.columnCount;

        if (item.boundView != null) {
            item.boundView.retain();
            if (DEBUG_DELETE) {
                Log.e(TAG, "Trying to force bind a view for item " + item.item + ", but it already had a view bound to it.");
            }
            if (bindTarget == BindToEnd)
                layout.add(item.boundView);
            else
                layout.add(0, item.boundView);
        }
        else {
            VisibleView viewForObject = getViewForItem(item, section);
            bindViewToHistoricPosition(viewForObject, section, item, bindTarget, row, column, historicRows);
        }

        return true;

    }

    private boolean forceBindObjectToHistoricPosition(Object object, int bindTarget, ArrayList<Section> historicSections, ArrayList<Row> historicRows) {

        // Get the historic row, section and index associated with this object
        int row = 0;
        Section section = null;
        int sectionCount = historicSections.size();
        int itemIndex = -1;
        for (int i = 0; i < sectionCount; i++){
            section = historicSections.get(i);
            itemIndex = section.historicIndexOfObjectWithComparator(object, controller.comparator);
            if (itemIndex != -1) {
                break;
            }
            else {
                row += section.historicContent.size() / section.columnCount + (section.historicContent.size() % section.columnCount == 0 ? 0 : 1);
            }
        }

        if (itemIndex == -1) return false;

        // At the end of the for-loop, section is the item's section
        row += itemIndex / section.columnCount;

        if (row == NoRow) return false;
        int column = itemIndex % section.columnCount;

        Item item = section.historicContent.get(itemIndex);

        if (item.boundView != null) {
            item.boundView.retain();
            if (DEBUG_DELETE) {
                Log.e(TAG, "Trying to force bind a view for item " + item.item + ", but it already had a view bound to it.");
            }
            if (bindTarget == BindToEnd)
                layout.add(item.boundView);
            else
                layout.add(0, item.boundView);
        }
        else {
            VisibleView viewForObject = getViewForItem(item, section);
            bindViewToHistoricPosition(viewForObject, section, item, bindTarget, row, column, historicRows);
        }

        return true;

    }

    private void getViewTypeCount() {

        viewTypeCount = 0;
        for (Section section : sections) {
            if (section.viewType > viewTypeCount)
                viewTypeCount = section.viewType;
        }

        viewTypeCount += 1;

    }

    public int getViewTypeOfView(View view) {
        final VisibleView VisibleView;
        final FrameLayout ViewContainer;

        try {
            ViewContainer = (FrameLayout) view.getParent();
            VisibleView = (VisibleView) ViewContainer.getTag();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }
        catch (NullPointerException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }

        return VisibleView.section.viewType;
    }

    public Object getObjectForView(View view) {
        final VisibleView VisibleView;
        final FrameLayout ViewContainer;

        try {
            ViewContainer = (FrameLayout) view.getParent();
            VisibleView = (VisibleView) ViewContainer.getTag();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }
        catch (NullPointerException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }

        return VisibleView.target.item;
    }

    public View retainView(View view) {
        final VisibleView VisibleView;
        final FrameLayout ViewContainer;

        try {
            ViewContainer = (FrameLayout) view.getParent();
            VisibleView = (VisibleView) ViewContainer.getTag();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }
        catch (NullPointerException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }

        VisibleView.retain();

        return VisibleView.view;
    }

    public void releaseView(View view) {
        final VisibleView VisibleView;
        final FrameLayout ViewContainer;

        try {
            ViewContainer = (FrameLayout) view.getParent();
            VisibleView = (VisibleView) ViewContainer.getTag();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }
        catch (NullPointerException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }

        VisibleView.release();
    }

    public View getViewForObject(Object object) {
        for (VisibleView visibleView : boundViews) {
            if (controller.comparator.areObjectsEqual(object, visibleView.target.item)) {
                return visibleView.view.getChildAt(0);
            }
        }

        return null;
    }

    public View retainViewForObject(Object object) {
        // Check to see first, if there is already a view bound to this object
        for (VisibleView visibleView : boundViews) {
            if (controller.comparator.areObjectsEqual(object, visibleView.target.item)) {
                visibleView.retain();
                return visibleView.view.getChildAt(0);
            }
        }

        // If there isn't one, create one and return it
        int row = 0, column = 0;
        int index = -1;
        Item item = null;
        Section section = null;
        int sectionCount = sections.size();

        // Determine the section, associated item and position of this object's view
        //noinspection ForLoopReplaceableByForEach
        for (int i = 0; i < sectionCount; i++) {
            section = sections.get(i);
            if ((index = section.indexOfObjectWithComparator(object, controller.comparator)) != -1) {
                column = index % section.columnCount;
                row += index / section.columnCount;
                item = section.content.get(index);
                break;
            }
            else {
                row += section.getRowCount();
            }
        }

        // If the item index wasn't found, the object is not part of the data set and a view can't be created for it
        if (index == -1) return null;

        bindItemToPosition(item, section, BindToEnd, row, column);
        // The newly bound view is not part of the layout, but is implicitly retained
        VisibleView boundView = layout.remove(layout.size() - 1);

        return boundView.view.getChildAt(0);
    }

    private int getObjectRow(Object object) {
        int objectRow = 0;
        int objectIndex;
        for (Section section : sections) {
            if ((objectIndex = section.indexOfObject(object)) != -1) {
                objectRow += objectIndex / section.columnCount;
                break;
            }
            else {
                objectRow += section.getRowCount();
            }
        }

        return objectRow;
    }

    public int getObjectTopPosition(Object object) {
        int row = getObjectRow(object);
        if (row != -1) {
            return logicalRows.get(row).start;
        }
        else return -1;
    }

    public int getObjectBottomPosition(Object object) {
        int row = getObjectRow(object);
        if (row != -1) {
            return logicalRows.get(row).end;
        }
        else return -1;
    }

    public void smoothScrollToObject(Object object) {
        int row = getObjectRow(object);
        if (row != -1) {
            int minimumRequiredScroll = logicalRows.get(row).start;
            int maximumRequiredScroll = logicalRows.get(row).end - getHeight();
            if (maximumRequiredScroll > getScrollY()) {
                smoothScrollTo(0, maximumRequiredScroll);
            }
            else {
                smoothScrollTo(0, minimumRequiredScroll);
            }
        }
    }

    public int getObjectMinimumScrollPosition(Object object) {
        int objectRow = getObjectRow(object);

        if (objectRow < logicalRows.size()) {
            int scrollPosition = logicalRows.get(objectRow).end - getHeight();
            if (scrollPosition < 0) return 0; else return scrollPosition;
        }

        return -1;
    }

    public void deleteObjectForView(View view) {

        final VisibleView VisibleView;
        final FrameLayout ViewContainer;

        try {
            ViewContainer = (FrameLayout) view.getParent();
            VisibleView = (VisibleView) ViewContainer.getTag();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }
        catch (NullPointerException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }

        ArrayList<Selection> sel = new ArrayList<Selection>();
        sel.add(Selection.make(VisibleView.section, VisibleView.target));
        deleteItemsAnchored(sel, true);


        // Special code is nicer and faster, but harder to maintain with the older code
//        ViewContainer.animate()
//                .alpha(0f)
//                .setDuration(deleteDuration)
//                .setStartDelay(0)
//                .setInterpolator(deleteInterpolator)
//                .setListener(new AnimatorListenerAdapter() {
//                    @Override
//                    public void onAnimationStart(Animator animation) {
//                        ViewContainer.setLayerType(LAYER_TYPE_HARDWARE, null);
//                        requestDisableInteractions();
//                    }
//
//                    @Override
//                    public void onAnimationEnd(Animator animation) {
//                        ViewContainer.setLayerType(LAYER_TYPE_NONE, null);
//                        ViewContainer.animate().setListener(null);
//
//                        final ArrayList<Section> HistoricSections = new ArrayList<Section>(sections);
//                        for (Section section : HistoricSections) {
//                            section.historicContent = new ArrayList<ActionItem>(section.content);
//                        }
//                        final ArrayList<Row> HistoricRows = new ArrayList<Row>(logicalRows);
//
//                        Section section = VisibleView.section;
//                        int preRowCount = section.getRowCount();
//                        section.content.remove(VisibleView.target);
//
//                        layout.remove(VisibleView);
//                        VisibleView.release();
//                        if (VisibleView.retainCount != 0) {
//                            VisibleView.unbind();
//                        }
//
//                        VisibleView.view.setAlpha(1f);
//
//                        if (preRowCount != section.getRowCount()) {
//                            regenerateRows();
//                            resizeContainerTo(logicalRows.get(logicalRows.size() - 1).end);
//                        }
//
//                        preloadViewsInImageOf(HistoricSections, HistoricRows);
//
//                        moveViewsAndCleanup();
//                    }
//                });
//

    }

    private int getFirstVisibleRowIndex() {
        int top = getScrollY();
        return getFirstVisibleRowIndex(top);
    }

    private int getFirstVisibleRowIndex(int top) {
        int i = 0;
        for (Row row : logicalRows) {
            if (row.end > top) return i;
            i++;
        }
        return NoRow;
    }

    public View getFirstVisibleView() {
        if (layout.size() > 0) {
            return layout.get(0).view;
        }

        return null;
    }

    public View getLastVisibleView() {
        if (layout.size() > 0) {
            return layout.get(layout.size() - 1).view;
        }

        return null;
    }

    public Object getFirstVisibleObject() {
        if (layout.size() > 0) {
            return layout.get(0).target.item;
        }

        return null;
    }

    public Object getLastVisibleObject() {
        if (layout.size() > 0) {
            return layout.get(layout.size() - 1).target.item;
        }

        return null;
    }

    public Section getFirstVisibleSection() {
        if (layout.size() > 0) {
            return layout.get(0).section;
        }

        return null;
    }

    public Section getLastVisibleSection() {
        if (layout.size() > 0) {
            return layout.get(layout.size() - 1).section;
        }

        return null;
    }

    private int getLastVisibleRowIndex() {
        int bottomMargin = getScrollY() + getHeight();
        if (getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT) {
            // TODO
            int extraHeight = Math.abs(container.getHeight() - containerHeight);
            if (extraHeight > maxHeight - getHeight()) extraHeight = maxHeight - getHeight();
            bottomMargin = getScrollY() + getHeight() + extraHeight;
        }
        return getLastVisibleRowIndex(bottomMargin);
    }

    private int getLastVisibleRowIndex(int bottomMargin) {
        int i = logicalRows.size() - 1;
        Row row;
        for (; i >= 0; i--) {
            row = logicalRows.get(i);
            if (row.start < bottomMargin) {
                if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) if (gravity != Gravity.TOP) Log.d(TAG, "The last visible row index has been determined: " + i);
                return i;
            }
        }
        return NoRow;
    }

    // This will refresh the content area for the specified row range, but placed as if it were part of the old content
    private void preloadViewsBetweenRows(int rowStart, int rowCount) {

        if (rowStart > logicalRows.size()) return;

        int sectionIndex = 0;
        int itemIndex;

        int currentRow = 0;

        //Find the item to start with
        for (Section section : sections) {
            if (currentRow + section.getRowCount() > rowStart) break;
            currentRow += section.getRowCount();
            sectionIndex++;
        }

        Section section = sections.get(sectionIndex);
        itemIndex = (rowStart - currentRow) * section.columnCount;
        int sectionItemCount = section.content.size();
        int sectionCount = sections.size();
        int rowsDone = 0;

        currentRow = rowStart;

        do {
            // If the current item index is over the section item count, it's time to move on to the next section
            if (itemIndex >= sectionItemCount) {
                sectionIndex += 1;
                if (sectionIndex >= sectionCount) break;
                section = sections.get(sectionIndex);
                sectionItemCount = section.content.size();
                itemIndex = 0;
            }
            // Bind an entire row and reorder the layout
            // At the end of any eventual animations, all other views will be removed from the layout
            // and only the ordered views will remain within the layout
            for (int j = 0; j < section.columnCount && itemIndex < sectionItemCount; j++) {
                VisibleView view = section.content.get(itemIndex).boundView;
                if (view == null) {
                    if (DEBUG_VARIABLE_COLUMN_COUNT) {
                        Log.d(TAG, "Binding a view as part of layout preload");
                    }
                    bindItemIfVisible(section.content.get(itemIndex), section, BindToEnd);
                }
                else {
                    if (layout.contains(view)) {
                        layout.remove(view);
                        layout.add(view);
                    }
                    else {
                        view.retain();
                        layout.add(view);
                    }
                }
                itemIndex++;
            }
            // Move on to the next row.
            if (section.content.size() > 0) {
                currentRow++;
                rowsDone++;
            }
            if (currentRow >= logicalRows.size()) break;
        } while (rowsDone < rowCount);

    }

    private ArrayList<VisibleView> preloadViewsBetweenRowsInImageOf(int rowStart, int rowCount, final ArrayList<Section> HistoricSection, final ArrayList<Row> HistoricRows) {
        return preloadObjectsBetweenRowsInImageOf(rowStart, rowCount, HistoricSection, HistoricRows, false);
    }

    // This will refresh the content area for the specified row range, but placed as if it were part of the old content
    // It returns views which should be part of the specified rows but whose associated objects were not found in the historic content
    private ArrayList<VisibleView> preloadObjectsBetweenRowsInImageOf(int rowStart, int rowCount, final ArrayList<Section> HistoricSection, final ArrayList<Row> HistoricRows, boolean useComparator) {

        ArrayList<VisibleView> newContent = new ArrayList<VisibleView>();

        if (sections.size() == 0) return newContent;

        int sectionIndex = 0;
        int itemIndex;

        int currentRow = 0;

        //Find the item to start with
        for (Section section : sections) {
            if (currentRow + section.getRowCount() > rowStart) break;
            currentRow += section.getRowCount();
            sectionIndex++;
        }

        Section section = sections.get(sectionIndex);
        itemIndex = (rowStart - currentRow) * section.columnCount;
        int sectionItemCount = section.content.size();
        int sectionCount = sections.size();
        int rowsDone = 0;

        currentRow = rowStart;

        do {
            // If the current item index is over the section item count, it's time to move on to the next section
            if (itemIndex >= sectionItemCount) {
                sectionIndex += 1;
                if (sectionIndex >= sectionCount) break;
                section = sections.get(sectionIndex);
                sectionItemCount = section.content.size();
                itemIndex = 0;
            }
            // Bind an entire row and reorder the layout
            // At the end of any eventual animations, all other views will be removed from the layout
            // and only the ordered views will remain within the layout
            for (int j = 0; j < section.columnCount && itemIndex < sectionItemCount; j++) {
                VisibleView view;
                try {
                    view = section.content.get(itemIndex).boundView;
                }
                catch (ArrayIndexOutOfBoundsException e) { // TODO
                    itemIndex++;
                    continue;
                }
                if (view == null) {
                    boolean bound = useComparator ?
                            forceBindObjectToHistoricPosition(section.content.get(itemIndex).item, BindToEnd, HistoricSection, HistoricRows) :
                            forceBindItemToHistoricPosition(section.content.get(itemIndex), section, BindToEnd, HistoricSection, HistoricRows);
                    if (!bound) {
                        newContent.add(bindItemToPosition(section.content.get(itemIndex), section, BindToEnd, currentRow, itemIndex % section.columnCount));
                    }
                }
                else {
                    if (layout.contains(view)) {
                        layout.remove(view);
                        layout.add(view);
                    }
                    else {
                        view.retain();
                        layout.add(view);
                    }
                }
                if (useComparator) {
                    view = layout.get(layout.size() - 1);
                    view.section = section;
                    view.target = section.content.get(itemIndex);
                }
                itemIndex++;
            }
            // Move on to the next row.
            if (section.content.size() > 0) {
                currentRow++;
                rowsDone++;
            }
            if (currentRow >= logicalRows.size()) break;
        } while (rowsDone < rowCount);


        return newContent;
    }

    // This will refresh the content area for the current content, but placed as if it were part of the old content
    // This assumes that nothing new has to be placed between the currently visible views and will only place view
    // before and after the current layout
    private void preloadViewsInImageOf(final ArrayList<Section> HistoricSection, final ArrayList<Row> HistoricRows) {

        // Load views towards the bottom
        int bottomMargin = getScrollY() + getHeight();

        // Load the remainder of the column for the current item, if applicable
        int itemIndex;
        int layoutIndex = layout.size() - 1;
        Item item;
        Section section;
        do {

            item = layout.get(layoutIndex).target;
            section = layout.get(layoutIndex).section;

            itemIndex = section.content.indexOf(item);

            layoutIndex -= 1;

        } while (itemIndex == -1);

        int currentRow = getRowOfItem(item);

        int i;
        int columnsPassed = 0;
        for (i = itemIndex % section.columnCount + 1; i < section.columnCount; i++) {
            columnsPassed++;
            if (itemIndex + columnsPassed >= section.content.size()) break;
            forceBindItemToHistoricPosition(section.content.get(itemIndex + columnsPassed), section, BindToEnd, HistoricSection, HistoricRows);
        }

        if (DEBUG_DELETE) {
            Log.d(TAG, "Completed final row with " + columnsPassed + " columns.");
        }

        itemIndex += columnsPassed + 1;
        int sectionIndex = sections.indexOf(section);

        // The last visible section doesn't start from 0 and is treated separately
        int sectionItemCount = section.content.size();
        int sectionCount = sections.size();

        currentRow++;
        int rowCount = logicalRows.size();
        do {
            // If the current item index is over the section item count, it's time to move on to the next section
            if (itemIndex >= sectionItemCount) {
                sectionIndex += 1;
                if (sectionIndex >= sectionCount) break;
                section = sections.get(sectionIndex);
                sectionItemCount = section.content.size();
                itemIndex = 0;
            }
            // Bind an entire row
            for (int j = 0; j < section.columnCount && itemIndex < sectionItemCount; j++) {
                forceBindItemToHistoricPosition(section.content.get(itemIndex), section, BindToEnd, HistoricSection, HistoricRows);
                itemIndex++;
            }
            // Move on to the next row.
            if (section.content.size() > 0) currentRow++;
            if (currentRow >= rowCount) break;
        } while (logicalRows.get(currentRow).start < bottomMargin);

        // Load views towards the top
        int top = getScrollY();

        // Load the remainder of the column for the current item, if applicable
        layoutIndex = 0;
        do {

            item = layout.get(layoutIndex).target;
            section = layout.get(layoutIndex).section;

            itemIndex = section.content.indexOf(item);

            layoutIndex += 1;

        } while (itemIndex == -1);

        currentRow = getRowOfItem(item);

        columnsPassed = 0;
        for (i = itemIndex % section.columnCount - 1; i >= 0; i--) {
            columnsPassed++;
            if (itemIndex - columnsPassed < 0) break;
            forceBindItemToHistoricPosition(section.content.get(itemIndex - columnsPassed), section, BindToStart, HistoricSection, HistoricRows);
        }

        if (DEBUG_DELETE) {
            Log.d(TAG, "Completed final row with " + columnsPassed + " columns.");
        }

        itemIndex -= columnsPassed + 1;
        sectionIndex = sections.indexOf(section);

        currentRow--;
        do {
            // If the current item index is over the section item count, it's time to move on to the next section
            if (itemIndex < 0) {
                sectionIndex -= 1;
                if (sectionIndex < 0) break;
                section = sections.get(sectionIndex);
                itemIndex = section.content.size() - 1;
            }
            // Bind an entire row
            for (int j = 0; j < section.columnCount && itemIndex >= 0; j++) {
                forceBindItemToHistoricPosition(section.content.get(itemIndex), section, BindToStart, HistoricSection, HistoricRows);
                itemIndex--;
            }
            // Move on to the next row.
            if (section.content.size() > 0) currentRow--;
            if (currentRow < 0) break;
        } while (logicalRows.get(currentRow).end > top);

    }

    private void moveViewsAndCleanup() {
        moveViewsAndCleanup(true, false);
    }

    private boolean moveWithLayers = false;

    public void setMoveWithLayersEnabled(boolean enabled) {
        moveWithLayers = enabled;
    }
    public void setMoveInterpolator(TimeInterpolator interpolator) {
        moveInterpolator = interpolator;
    }

    public TimeInterpolator getMoveInterpolator() {
        return moveInterpolator;
    }

    private void moveViewsAndCleanup(final boolean EnableInteraction, final boolean EnableViewCollection) {
        moveViewsAndCleanup(EnableInteraction, EnableViewCollection, true);
    }

    // moveViewsAndCleanup is the final transaction step; this animates views into their new positions and enables
    // touch events, view recycling and binding on scroll and if necessary notifies the scroll listener of scroll-like behavior
    private void moveViewsAndCleanup(final boolean EnableInteractions, final boolean EnableViewCollection, final boolean NotifyScrollListener) {

        if (refreshViewsOnTransactionEnabled) refreshViews();

        if (Build.VERSION.SDK_INT >= 19) {
            // KIT KAT FIX
            // force an invalidate to prevent views appearing transparent until scrolling
            // this must occur after a layout pass

            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (container != null) container.invalidate();
                    getViewTreeObserver().removeGlobalOnLayoutListener(this);
                }
            });
        }

        final int LayoutSize = boundViews.size();
        int i = 0;

        if (animationsEnabled && NotifyScrollListener) {
            ValueAnimator animator = ValueAnimator.ofInt(0, 1000);
            animator.setDuration(moveDuration);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    if (onScrollListener != null) {
                        onScrollListener.onScroll(CollectionView.this, getScrollY(), 0);
                    }
                }
            });
//            activeAnimators.add(animator);
        }

        for (final VisibleView visibleView : boundViews) {
            if (packedAnimations) {
                if (visibleView.view.getId() != 0) continue;
            }
            i++;
            int row = getRowOfItem(visibleView.target);
            int column = visibleView.section.content.indexOf(visibleView.target) % visibleView.section.columnCount;

            if (row != - 1) {
                visibleView.row = row;
                visibleView.column = column;

                if (animationsEnabled) {

                    visibleView.view.animate()
                            .y(logicalRows.get(row).start)
                            .x(column * (container.getWidth() / visibleView.section.columnCount))
                            .alpha(1f)
                            .setStartDelay(0)
                            .setDuration(moveDuration)
                            .setInterpolator(moveInterpolator)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(Animator animation) {
                                    if (moveWithLayers) {
                                        visibleView.view.setLayerType(LAYER_TYPE_HARDWARE, null);
                                    }
                                }

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    visibleView.view.animate().setListener(null);

                                    // Cleanup the layertype even if moveWithLayers is disabled
                                    // Because this takes over the listener for the insert animation
                                    visibleView.view.setLayerType(LAYER_TYPE_NONE, null);
                                }
                            }).start();

                }
                else {
                    visibleView.view.setY(logicalRows.get(row).start);
                    visibleView.view.setX(column * (container.getWidth() / visibleView.section.columnCount));
                }
            }
            else {
            }
        }

        // If nothing was processed, need to cleanup instantly
        if (i == 0 || !animationsEnabled) {
            if (!cleanupHeldOff) {
                cleanup(EnableInteractions, EnableViewCollection);
                if (containerAnimated && containerAnimator != null) {
                    containerAnimator.end();
                }
            }
        }
        else {
            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(moveDuration);
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    cleanup(EnableInteractions, EnableViewCollection);
                    activeAnimators.remove(animator);
                }
            });
            animator.start();
            activeAnimators.add(animator);
            if (containerAnimated && containerAnimator != null) {
                containerAnimator.setDuration(moveDuration);
                containerAnimator.setInterpolator(moveInterpolator);
                containerAnimator.start();
            }
        }
    }

    private void cleanup(final boolean EnableInteractions, final boolean EnableViewCollection) {
        // Release views which the transaction animation has pushed off-screen
        for (int j = 0; j < layout.size(); j++) {
            if (layout.get(j).row >= logicalRows.size() || logicalRows.get(layout.get(j).row).end < getScrollY()
                    || logicalRows.get(layout.get(j).row).start > getScrollY() + getHeight()) {
                layout.get(j).release();
                layout.remove(j);
                j--;
            }
        }

        if (DEBUG_DATASETCHANGE) {
            Log.d(TAG, "Layout views: ");
            for (final VisibleView visibleView : layout) {
                Log.d(TAG, "" + visibleView.target.item + " at Y position " + visibleView.view.getY());
                if (visibleView.view.getAlpha() < 1f) {
                    Log.e(TAG, "This view's alpha is less than one!");
                }
                if ((int) visibleView.view.getY() != logicalRows.get(visibleView.row).start) {
                    Log.e(TAG, "This view's position isn't what it should be!");
                }
                if (visibleView.view.getVisibility() != VISIBLE) {
                    Log.e(TAG, "This view is invisible!");
                }
            }
        }
        if (!controller.isInTransaction()) {
            for (Section section : sections) {
                section.historicContent = null;
            }
        }
        if (EnableViewCollection) requestEnableViewCollection();

        if (containerAnimator != null) {
            containerAnimator.end();
        }

        setWillNotDraw(false);

        CollectionView.super.setVerticalScrollBarEnabled(verticalScrollBarEnabled);

        if (getLayoutParams().height == LayoutParams.WRAP_CONTENT) {
            requestLayout();
        }

        if (EnableInteractions) {
            requestEnableInteractions();
            controller.requestEndInternalTransaction();
        }

        inTransaction = false;

        if (!controller.isInTransaction()) {
            int listenerCount = transactionListeners.size();
            for (int i = 0; i < listenerCount; i++) {
                TransactionListener listener = transactionListeners.get(i);
                listener.onTransactionEnd();

                // Allow listeners to remove themselves after being fired
                if (transactionListeners.size() < listenerCount) {
                    i--;
                    listenerCount--;
                }
            }

            transactionListeners.remove(PostLayoutTransactionListener);
        }
    }

    private ValueAnimator containerAnimator;
    private boolean containerAnimated;

    public void setAnimateLayoutEnabled(boolean enabled) {
        containerAnimated = enabled;
    }

    private void resizeContainerTo(final int size, final long delay) {
        if (containerAnimator != null) {
            // container is about to be resized, so resizing listener is useless
            containerAnimator.removeAllListeners();
            containerAnimator.cancel();
        }

        if ((containerHeight > size || containerAnimated) && animationsEnabled) {
            if (DEBUG_BACKENDUNDO) Log.d(TAG, "Container resize is delayed; resizing from: " + containerHeight + " to: " + size);
            ValueAnimator animator = ValueAnimator.ofInt(containerHeight, size);
            containerAnimator = animator;
            containerHeight = size;
            animator.setInterpolator(moveInterpolator);
            animator.setDuration(moveDuration + deleteDuration + deleteStride * layout.size());
            if (containerAnimated) {
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        if (DEBUG_BACKENDUNDO) Log.d(TAG, "container height is now: " + valueAnimator.getAnimatedValue());
                        container.getLayoutParams().height = (Integer) valueAnimator.getAnimatedValue();
                        container.setLayoutParams(container.getLayoutParams());
                    }
                });
            }
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    container.getLayoutParams().height = size;
                    container.setLayoutParams(container.getLayoutParams());
                    containerAnimator = null;

                    if (DEBUG_BACKENDUNDO) Log.d(TAG, "Container has resized successfully to: " + size);
                }
            });
            container.setTag(animator);
            if (!containerAnimated) {
                animator.start();
            }
        }
        else {
            if (DEBUG_BACKENDUNDO) Log.d(TAG, "Container resize is instant; resizing from: " + containerHeight + " to: " + size);
            containerHeight = size;
            container.getLayoutParams().height = size;
            container.setLayoutParams(container.getLayoutParams());
        }
    }



    // ********************* SELECTION ************************

    static class Selection {
        Section section;
        Item item;

        @Override
        public boolean equals(Object selection) {
            if (selection instanceof  Selection) {
                if (section == ((Selection) selection).section && item == ((Selection) selection).item) return true;
            }
            return false;
        }

        public static Selection make(Section section, Item item) {
            Selection selection = new Selection();
            selection.section = section;
            selection.item = item;
            return selection;
        }
    }

    private ArrayList<Selection> selection = new ArrayList<Selection>();

    public void clearSelection() {
        selection.clear();
        refreshViews();
    }

    public void refreshViews() {
        // no controller = no views to refresh
        if (controller == null) return;

        controller.setIsRefreshingViews(true);

        //Reconfigure views
        for (VisibleView visibleView : boundViews) {
            controller.configureView(visibleView.view.getChildAt(0), visibleView.target.item, visibleView.section.viewType);
        }

        controller.setIsRefreshingViews(false);
    }

    public void refreshViewForObject(Object object) {
        // no controller = no views to refresh
        if (controller == null) return;

        controller.setIsRefreshingViews(true);

        for (VisibleView visibleView : boundViews) {
            if (controller.comparator.areObjectsEqual(visibleView.target.item, object)) {
                controller.configureView(visibleView.view.getChildAt(0), visibleView.target.item, visibleView.section.viewType);
                controller.setIsRefreshingViews(false);
                return;
            }
        }

        controller.setIsRefreshingViews(false);
    }

    final TransactionListener PostLayoutTransactionListener = new TransactionListener() {
        @Override
        public void onTransactionStart() {}

        @Override
        public void onTransactionEnd() {
            post(new Runnable() {
                @Override
                public void run() {
                    onScrollChanged(getScrollX(), getScrollY(), getScrollX(), getScrollY() - 1);
                    onScrollChanged(getScrollX(), getScrollY(), getScrollX(), getScrollY() + 1);
                }
            });
        }
    };

    @Override
    public void onSizeChanged(int newWidth, int newHeight, int oldWidth, int oldHeight) {
        super.onSizeChanged(newWidth, newHeight, oldWidth, oldHeight);

        if (newHeight > oldHeight && oldHeight > 0) {
            if (DEBUG) Log.w(TAG, "Re-running layout due to post-layout size increase.");
            if (inTransaction) {
                addTransactionListener(PostLayoutTransactionListener);
            }
            else{
                post(new Runnable() {
                    @Override
                    public void run() {
                        onScrollChanged(getScrollX(), getScrollY(), getScrollX(), getScrollY() - 1);
                        onScrollChanged(getScrollX(), getScrollY(), getScrollX(), getScrollY() + 1);
                    }
                });
            }
        }

        if (oldHeight == 0) {
            if (gravity == Gravity.CENTER_VERTICAL || gravity == Gravity.CENTER) {
                if (logicalRows.size() > 0) {
                    if (logicalRows.get(logicalRows.size() - 1).end < getHeight()) {
                        if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) Log.d(TAG, "Displacing rows by: " + (getHeight() / 2 - logicalRows.get(logicalRows.size() - 1).end / 2) + " from onSizeChanged()");
                        displaceRows(getHeight() / 2 - logicalRows.get(logicalRows.size() - 1).end / 2);
                    }
                }
            }
        }
    }

    public int selectionSize() {
        return selection.size();
    }

    public boolean isViewSelected(View view) {
        final VisibleView VisibleView;
        final FrameLayout ViewContainer;

        try {
            ViewContainer = (FrameLayout) view.getParent();
            VisibleView = (VisibleView) ViewContainer.getTag();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }
        catch (NullPointerException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }

        Selection sel = new Selection();
        sel.section = VisibleView.section;
        sel.item = VisibleView.target;

        return selection.contains(sel);
    }

    public boolean toggleSelectionForView(View view) {
        final VisibleView VisibleView;
        final FrameLayout ViewContainer;

        try {
            ViewContainer = (FrameLayout) view.getParent();
            VisibleView = (VisibleView) ViewContainer.getTag();
        }
        catch (ClassCastException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }
        catch (NullPointerException e) {
            throw new IllegalArgumentException("View " + view + " is not a view created by the " + this.getClass().toString());
        }

        Selection sel = new Selection();
        sel.section = VisibleView.section;
        sel.item = VisibleView.target;

        if (selection.contains(sel)) {
            selection.remove(sel);
            return false;
        }
        else {
            selection.add(sel);
            return true;
        }
    }

    public void deleteSelection() {
        deleteSelectionAnchored(true);
    }

    public void deleteSelectionAnchored(final boolean anchored) {
        deleteItemsAnchored(selection, anchored);
    }

    private Selection createSelectionForObject(Object object) {
        for (Section section : sections) {
            for (Item item : section.content) {
                if (controller.comparator.areObjectsEqual(item.item, object)) {
                    return Selection.make(section, item);
                }
            }
        }

        return null;
    }

    public void deleteObjects(Collection<Object> objects) {
        ArrayList<Selection> selections = new ArrayList<Selection>(objects.size());
        for (Object object : objects) {
            Selection selection = createSelectionForObject(object);
            if (selection != null)
                selections.add(selection);
        }

        deleteItemsAnchored(selection, true);
    }

    protected ViewPropertyAnimator playDeleteAnimation(View view) {
        view.animate().alpha(0f)
                .setDuration(deleteDuration)
                .setInterpolator(deleteInterpolator)
                .setStartDelay(0);
        return view.animate();
    }

    protected void reverseDeleteAnimation(View view) {
        view.setAlpha(1f);
    }

    private void deleteItemsAnchored(final ArrayList<Selection> deletedItems, final boolean anchored) {

        final ArrayList<Section> HistoricSections = new ArrayList<Section>(sections);
        for (Section section : HistoricSections) {
            section.historicContent = new ArrayList<Item>(section.content);
        }
        final ArrayList<Row> HistoricRows = new ArrayList<Row>(logicalRows);

        Selection sel;
        for (int i = 0; i < deletedItems.size(); i++) {
            sel = deletedItems.get(i);
            if (sel.item.boundView == null) {
                sel.section.content.remove(sel.item);
                deletedItems.remove(sel);
                i--;
            }
        }

        int i = 0;

        if (deletedItems.size() > 0) {
            for (Selection sel1 : deletedItems) {
                i++;

                final VisibleView VisibleView = sel1.item.boundView;
                final FrameLayout ViewContainer = VisibleView.view;

//                disableBackburnerLimit();

                if (animationsEnabled) {
                    ViewContainer.setLayerType(LAYER_TYPE_HARDWARE, null);
                    ViewContainer.buildLayer();

                    deleteAnimator.playAnimation(ViewContainer, VisibleView.target.item, VisibleView.section.viewType);
                    ViewContainer.animate()
                            .setDuration(deleteDuration)
                            .setInterpolator(deleteInterpolator)
                            .setStartDelay(0)
                            .setListener(new AnimatorListenerAdapter() {

                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    ViewContainer.setLayerType(LAYER_TYPE_NONE, null);
                                    ViewContainer.animate().setListener(null);

                                    VisibleView.section.content.remove(VisibleView.target);

                                    layout.remove(VisibleView);
                                    VisibleView.release();
                                    if (VisibleView.retainCount != 0) {
                                        VisibleView.unbind();
                                    }

                                    deleteAnimator.resetState(VisibleView.view, VisibleView.target.item, VisibleView.section.viewType);
                                    //                                reverseDeleteAnimation(VisibleView.view);
                                }
                            });
                }
                else {
                    VisibleView.section.content.remove(VisibleView.target);

                    layout.remove(VisibleView);
                    VisibleView.release();
                    if (VisibleView.retainCount != 0) {
                        VisibleView.unbind();
                    }
                }

                if (i == deletedItems.size()) {
                    final Runnable endCallback = new Runnable() {
                        @Override
                        public void run() {

                            // The rows need to be recalculated and the container resized
                            regenerateRows();
                            if (logicalRows.size() > 0)
                                resizeContainerTo(logicalRows.get(logicalRows.size() - 1).end, moveDuration);
                            else resizeContainerTo(1, moveDuration);

                            // If every visible view has been deleted, just reset the layout, since there's nothing to anchor to
                            if (layout.size() == 0 && logicalRows.size() > 0) {

                                // If the current scroll position is larger than the new container size, adjust the scrolling appropriately
                                // There is no need to translate any views, because the layout is empty
                                if (getScrollY() + getHeight() > logicalRows.get(logicalRows.size() - 1).end) {
                                    requestDisableViewCollection();

                                    scrollTo(0, logicalRows.get(logicalRows.size() - 1).end);
                                    scrollBy(0, -getHeight());

                                    requestEnableViewCollection();

                                }

                                preloadViewsBetweenRowsInImageOf(getFirstVisibleRowIndex(), getLastVisibleRowIndex() - getFirstVisibleRowIndex() + 1, HistoricSections, HistoricRows);
                                moveViewsAndCleanup();

                                return;
                            }

                            // Anchoring can only happen if there are views to anchor to; if everything gets removed, there is nothing to anchor to
                            // Anchoring is implicitly enabled if deleting causes the current scroll position to be larger than the maximum scroll after layout
                            boolean anchor = logicalRows.size() > 0 ? getScrollY() + getHeight() > logicalRows.get(logicalRows.size() - 1).end || anchored : false;

                            if (anchor && logicalRows.size() > 0) {

                                // If anchored, the list will "stick" to the first visible item
                                // After everything has moved into position, this item will be on the first visible row
                                VisibleView firstVisibleView = layout.get(0);

                                if (firstVisibleView == null) {
                                    // unable to get first view, just play it normally
                                    preloadViewsInImageOf(HistoricSections, HistoricRows);

                                    //moveViewsAndCleanup() will handle the rest of the magic
                                    moveViewsAndCleanup(true, true);
                                }
                                else {
                                    // Get top difference if negative, so view doesn't scroll down
                                    int topDifference = (int) (firstVisibleView.view.getY() - getScrollY());

                                    // The overScroll is the distance between the top of the first visible item to the current scroll position
                                    int overScroll = topDifference = topDifference > 0 ? topDifference : 0;
                                    int contentSize = getHeight();
                                    int translationAmount;

                                    // Get new row of view
                                    int row = getRowOfItem(firstVisibleView.target);

                                    // Get first visible row from bottom
                                    int lastRow = logicalRows.size() - 1;
                                    if (getScrollY() + getHeight() > logicalRows.get(lastRow).end) {
                                        int projectedTop = logicalRows.get(lastRow).end - getHeight();
                                        while (logicalRows.get(lastRow).start > projectedTop) {
                                            lastRow--;
                                            if (lastRow < 0) {
                                                lastRow = 0;
                                                break;
                                            }
                                        }
                                    }

                                    // If this view's row can't be the topmost row for any scroll position, the anchor will be the topmost row
                                    // for when the scroll position is at maximum
                                    row = lastRow > row ? row : lastRow;

                                    translationAmount = (int) (logicalRows.get(row).start - firstVisibleView.view.getY() + overScroll);

                                    // Compute the amount of rows that will be visible onscreen
                                    int completedArea = -Math.abs(topDifference);
                                    int rowCount = 0;
                                    while (completedArea < getScrollY() + contentSize) {
                                        completedArea = logicalRows.get(row + rowCount).end - Math.abs(topDifference);
                                        rowCount++;
                                        if (rowCount + row >= logicalRows.size()) break;
                                    }

                                    preloadViewsBetweenRowsInImageOf(row, rowCount, HistoricSections, HistoricRows);

                                    // Translate everything
                                    for (VisibleView visibleView : layout) {
                                        visibleView.view.setY(visibleView.view.getY() + translationAmount);// + topDifference);
                                    }

                                    // Scroll to new position instantly
                                    requestDisableViewCollection();
                                    scrollBy(0, translationAmount);// + topDifference);

                                    //moveViewsAndCleanup() will handle the rest of the magic
                                    moveViewsAndCleanup(true, true);
                                }
                            }
                            else if (logicalRows.size() > 0) {
                                preloadViewsInImageOf(HistoricSections, HistoricRows);

                                moveViewsAndCleanup();
                            }
                            else {
                                requestEnableInteractions();
                                controller.requestEndInternalTransaction();
                            }

//                                    enforceBackburnerLimit();
                        }
                    };


                    AnimatorListenerAdapter deleteListener = new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationStart(Animator animation) {
                            requestDisableInteractions();
                            controller.requestBeginInternalTransaction();
                        }

                        @Override
                        public void onAnimationEnd(Animator animation) {
                            ViewContainer.setLayerType(LAYER_TYPE_NONE, null);
                            ViewContainer.animate().setListener(null);

                            VisibleView.section.content.remove(VisibleView.target);

                            layout.remove(VisibleView);
                            VisibleView.release();
                            if (VisibleView.retainCount != 0) {
                                VisibleView.unbind();
                            }

                            deleteAnimator.resetState(VisibleView.view, VisibleView.target.item, VisibleView.section.viewType);
//                                    reverseDeleteAnimation(VisibleView.view);

                            endCallback.run();
                        }
                    };

                    if (animationsEnabled) {
                        ViewContainer.animate()
                                .setListener(deleteListener);
                    }
                    else {
                        requestDisableInteractions();
                        controller.requestBeginInternalTransaction();
                        Log.d(TAG, "Running the end runnables because the animations are disabled!");
                        endCallback.run();
                    }
                }

                if (animationsEnabled)
                    ViewContainer.animate().start();
            }
            deletedItems.clear();
        }
        else {

//            disableBackburnerLimit();

            requestDisableInteractions();
            controller.requestBeginInternalTransaction();
            regenerateRows();
            if (logicalRows.size() > 0)
                resizeContainerTo(logicalRows.get(logicalRows.size() - 1).end, moveDuration);
            else resizeContainerTo(1, moveDuration);

            if (anchored && logicalRows.size() > 0) {
                // If anchored, will need to get the first visible item after reorder
                VisibleView firstVisibleView = null;
                for (VisibleView visibleView : layout) {
                    if (visibleView.column <= visibleView.section.content.indexOf(visibleView.target)) {
                        firstVisibleView = visibleView;
                        break;
                    }
                }

                if (firstVisibleView == null) {
                    // whatever, just play it normally
                    preloadViewsInImageOf(HistoricSections, HistoricRows);
                }
                else {
                    // Get top difference if negative, so view doesn't scroll down
                    int topDifference = (int) (firstVisibleView.view.getY() - getScrollY());
                    topDifference = topDifference < 0 ? -topDifference : 0;
                    int contentSize = getHeight();
                    int translationAmount;

                    // Get new row of view
                    int row = getRowOfItem(firstVisibleView.target);
                    translationAmount = (int) (logicalRows.get(row).start - firstVisibleView.view.getY());

                    int completedArea = -topDifference;
                    int rowCount = 0;
                    while (completedArea < getScrollY() + contentSize) {
                        completedArea = logicalRows.get(row + rowCount).end - topDifference;
                        rowCount++;
                        if (rowCount + row >= logicalRows.size()) break;
                    }

                    preloadViewsBetweenRowsInImageOf(row, rowCount, HistoricSections, HistoricRows);

                    // Translate everything
                    for (VisibleView visibleView : layout) {
                        visibleView.view.setY(visibleView.view.getY() + translationAmount);
                    }

                    // Scroll to new position instantly
                    requestDisableViewCollection();
                    scrollBy(0, translationAmount);

                    //moveViewsAndCleanup() will handle the rest of the magic
                    moveViewsAndCleanup(true, true);
                }
            }
            else if (logicalRows.size() > 0) {
                preloadViewsInImageOf(HistoricSections, HistoricRows);

                moveViewsAndCleanup();
            }
            else {
                requestEnableInteractions();
                controller.requestEndInternalTransaction();
            }

//            enforceBackburnerLimit();
        }
    }

    private boolean containsObject(Object object) {
        for (Section section : sections) {
            if (section.containsObjectWithComparator(object, controller.comparator))
                return true;
        }
        return false;
    }

    // When the data set changes, this method looks for the item with the corresponding visibleView's object within the data set
    // If it is found, it updates the Section and ActionItem fields of the VisibleView to it's correct values and returns true
    // It will also set the boundView field of the item
    // Otherwise, it does nothing and returns false
    private boolean updateVisibleViewToCurrentDataSet(VisibleView visibleView) {

        int index;

        for (Section section : sections) {
            // Optimization; views coming from one view type are not checked against objects of a different viewtype
            if (section.viewType == visibleView.section.viewType) {
                index = section.indexOfObjectWithComparator(visibleView.target.item, controller.comparator);
            }
            else {
                index = -1;
            }

            if (index != -1) {
                visibleView.section = section;
                visibleView.target = section.content.get(index);
                section.content.get(index).boundView = visibleView;
                return true;
            }
        }
        if (DEBUG_DATASETCHANGE) {
            Log.d(TAG, "Object " + visibleView.target.item + " was NOT found in the new dataset.");
        }
        return false;

    }

    protected ViewPropertyAnimator playInsertAnimation(View view) {
        view.setAlpha(0f);
        view.setScaleY(2f);
        view.setScaleX(2f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(moveDuration)
                .setInterpolator(insertInterpolator)
                .setStartDelay(0);
        return view.animate();
    }

    public void setPackedAnimationsEnabled(boolean enabled) {
        // TODO: NOP
        if (true) return;
        packedAnimations = enabled;
    }

    private int transactionScrollingMode = TransactionScrollingModeNoScroll;
    private int navigationTransactionDirection = NavigationTransactionRightToLeft;

    public int getTransactionScrollingMode() {
        return this.transactionScrollingMode;
    }

    public void setTransactionScrollingMode(int transactionScrollingMode) {
        this.transactionScrollingMode = transactionScrollingMode;
    }

    public void setNavigationTransactionDirection(int direction) {
        navigationTransactionDirection = direction;
    }

    private long containerResizeDelay = moveDuration + deleteDuration;

    public void setContainerResizeDelay(long delay) {
        containerResizeDelay = delay;
    }

    public void setAnchorCondition(AnchorInspector inspector) {
        anchorInspector = inspector;
    }

    private boolean inTransaction = false;

    void onTransactionComplete(ArrayList<Section> newDataSet) {
        inTransaction = true;

        if (DEBUG_BACKENDUNDO) Log.d(TAG, "onTransactionComplete has started!");

        super.setVerticalScrollBarEnabled(false);
        requestDisableViewCollection();
        smoothScrollTo(0, getScrollY());

        final ArrayList<Section> historicSections = sections;
        for (Section section : sections) {
            section.historicContent = new ArrayList<Item>(section.content);
        }
        sections = newDataSet;

        if (controller.viewTypeCount > viewTypeCount) {
            for (int i = viewTypeCount; i < controller.viewTypeCount; i++) {
                backburner.add(new ArrayList<VisibleView>());
            }
            viewTypeCount = controller.viewTypeCount;
            getHeightsForViewTypes();
        }

        final int ContainerHeight = containerHeight;
        final ArrayList<Row> historicRows = logicalRows;
        regenerateRows();
        containerHeight = ContainerHeight;
        if (logicalRows.size() > 0) {
            if (DEBUG_BACKENDUNDO)
                for (Row row : logicalRows) {
                    Log.d(TAG, "Row(" + row.start + ", " + row.end + ")");
                }
            resizeContainerTo(logicalRows.get(logicalRows.size() - 1).end, containerResizeDelay);
        }
        else {
            View emptyView = controller.createEmptyView(container, LayoutInflater.from(getContext()));
            int height = 1;
            if (emptyView != null) {
                height = emptyView.getLayoutParams().height;
                if (height < 1) height = 1;
                if (DEBUG_ANIMATED_CONTAINER) Log.d(TAG, "Measured height of emptyView has been determined: " + height);
            }
            resizeContainerTo(height, containerResizeDelay);
        }

        // if the container width is still 0, the global layout has NOT run
        if (container.getWidth() == 0 ||
                // if the transaction is anchored, the container needs to grow to support the new virtual scroll position, but not if the container resize is animated
                (transactionScrollingMode == TransactionScrollingModeAnchor && ContainerHeight < containerHeight && computeVerticalScrollRange() < containerHeight && !containerAnimated)
           ) {
            if (DEBUG_BACKENDUNDO) Log.d(TAG, "About to delay refreshLayoutMaintainingScroll: ContainerHeight (pre) is: " + ContainerHeight + ", containerHeight is: " + containerHeight);
            // if the transaction is anchored and the container's height increases
            // need to wait for layout in case scroll pushes past the current container height

            // TODO Improve this by using a correct translation
            requestLayout();

            if (DEBUG_COLLECTION_EVENT_DELEGATE) Log.d(TAG, "Refresh layout maintainging scroll DELAYED for LAYOUT!");

            getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (container.getWidth() > 0 && computeVerticalScrollRange() >= containerHeight && container.getHeight() == containerHeight) {
                        getViewTreeObserver().removeGlobalOnLayoutListener(this);

                        if (DEBUG_COLLECTION_EVENT_DELEGATE) Log.d(TAG, "LAYOUT has arrived!");

                        refreshLayoutMaintainingScroll(historicSections, historicRows, transactionScrollingMode, ContainerHeight);
                    }
                    else {
                        if (DEBUG_COLLECTION_EVENT_DELEGATE) Log.d(TAG, "LAYOUT has arrived but did not apply the correct dimensions: \n" +
                                "computeVerticalScrollRange(" + computeVerticalScrollRange() + ") >= containerHeight(" + containerHeight + ")\n" +
                                "container.getHeight(" + container.getHeight() + ") == containerHeight(" + containerHeight + ")\n" +
                                "container.getLayoutParams.height(" + container.getLayoutParams().height + ")"
                        );
                    }
                }
            });
        }
        else {

            if (DEBUG_COLLECTION_EVENT_DELEGATE) Log.d(TAG, "Refresh layout maintainging scroll!");

            refreshLayoutMaintainingScroll(historicSections, historicRows, transactionScrollingMode, ContainerHeight);
        }
    }

    boolean inLayout;

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        inLayout = false;
    }

    public void requestLayout() {
        if (inLayout) Log.w(TAG, "requestLayout() called during the layout phase!");
        super.requestLayout();
    }

    public void showEmptyView() {
        if (emptyView != null) {
            ((ViewGroup) emptyView.getParent()).removeView(emptyView);
        }

        emptyView = controller.createEmptyView(container, LayoutInflater.from(getContext()));
        if (emptyView != null) {
            container.addView(emptyView);
            FrameLayout.LayoutParams emptyParams = (LayoutParams) emptyView.getLayoutParams();
            emptyParams.gravity = Gravity.CENTER;
            if (animationsEnabled) {
                final View EmptyView = emptyView;
                emptyView.setAlpha(0f);
                emptyView.setLayerType(LAYER_TYPE_HARDWARE, null);
                emptyView.buildLayer();
                emptyView.animate().alpha(1f).setDuration(moveDuration).setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        EmptyView.animate().setListener(null);
                        EmptyView.setLayerType(LAYER_TYPE_NONE, null);
                    }
                });
            }
            // If animations are not enabled, nothing else needs to be done
        }
    }

    public void removeEmptyView() {
        final View EmptyView = emptyView;
        emptyView.animate().cancel();
        emptyView = null;
        if (animationsEnabled) {
            EmptyView.setLayerType(LAYER_TYPE_HARDWARE, null);
            EmptyView.buildLayer();
            EmptyView.animate().alpha(0f).setDuration(deleteDuration).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    ((ViewGroup) EmptyView.getParent()).removeView(EmptyView);
                }
            });
        }
        else {
            ((ViewGroup) EmptyView.getParent()).removeView(EmptyView);
        }
    }

    private boolean cleanupHeldOff;

    void refreshLayoutMaintainingScroll(final ArrayList<Section> HistoricContent, final ArrayList<Row> HistoricRows, final int TransactionScrollingMode, final int ContainerHeight) {

        if (DEBUG_UNNECESSARY_ANIMATIONS) {
            Log.e(TAG, "RefreshLayoutMaintainingScroll has started; height is: " + getHeight());
            new Throwable().printStackTrace();
        }

        for (TransactionListener listener : transactionListeners) {
            listener.onTransactionStart();
        }

        setWillNotDraw(true);

        int translationAmount = getScrollY();
        ViewPropertyAnimator lastAnimator = null;
        VisibleView lastVisibleView = null;

        if (DEBUG_BACKENDUNDO) Log.d(TAG, "refreshLayoutMaintainingScroll has started!");

        final boolean AnimationsEnabled = animationsEnabled;
        final View Screenshot;
        // if animations are disabled, transactionscrollingmodenavigate is basically transactionscrollingmodetop
        if (TransactionScrollingMode == TransactionScrollingModeNavigate && animationsEnabled) {
            animationsEnabled = false;
            cleanupHeldOff = true;
            Screenshot = render();
            ((ViewGroup) getParent()).addView(Screenshot, getWidth(), getHeight());
            Screenshot.setX(getX());
            Screenshot.setY(getY());
            // Navigate transaction scrolling mode is very different compared to regular transaction modes
            // In this case, a screenshot view of the collection (the old view) will slide out to make room
            // for the collection view (the new view)
        }
        else {
            Screenshot = null;
        }

        requestDisableInteractions();
        controller.requestBeginInternalTransaction();

        if (emptyView != null && logicalRows.size() > 0) {
            removeEmptyView();
        }

        if (layout.size() > 0) {
            //Translate the whole content into the top row and visually remove deleted items
            int stride = 0;
            for (int i = 0; i < boundViews.size(); i++) {
                final VisibleView visibleView = boundViews.get(i);
                if (!updateVisibleViewToCurrentDataSet(visibleView)) {
                    if (animationsEnabled) {
                        visibleView.view.setLayerType(LAYER_TYPE_HARDWARE, null);
                        visibleView.view.buildLayer();
                        if (packedAnimations) visibleView.view.setId(1);
                        deleteAnimator.playAnimation(visibleView.view, visibleView.target.item, visibleView.section.viewType);
                        visibleView.view.animate()
                                .setDuration(deleteDuration)
                                .setInterpolator(deleteInterpolator)
                                .setStartDelay(stride * deleteStride)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        visibleView.view.setLayerType(View.LAYER_TYPE_NONE, null);
                                        visibleView.view.animate().setListener(null);

                                        if (layout.remove(visibleView)) {
                                            visibleView.release();
                                        }
                                        if (visibleView.retainCount != 0) {
                                            visibleView.unbind();
                                        }
                                        else {
                                            visibleView.view.setId(0);
                                        }

                                        deleteAnimator.resetState(visibleView.view, visibleView.target.item, visibleView.section.viewType);
                                    }
                                }).start();
                        lastAnimator = visibleView.view.animate();
                        lastVisibleView = visibleView;
                        stride++;
                    }
                    else {
                        if (layout.remove(visibleView)) {
                            visibleView.release();
                        }
                        if (visibleView.retainCount != 0)
                            visibleView.unbind();

                        i--;
                    }
                }
            }
        }

        if (TransactionScrollingMode == TransactionScrollingModeTop || TransactionScrollingMode == TransactionScrollingModeNavigate) {
            scrollTo(0, 0);
            smoothScrollTo(0, 0);
        }
        final int TranslationAmount = translationAmount;

        final Runnable EndRunnableUnanchored = new Runnable() {
            @Override
            public void run() {

                if (logicalRows.size() == 0) {
                    showEmptyView();
                }

                int firstVisibleRowIndex = getFirstVisibleRowIndex();

                for (final VisibleView visibleView : boundViews) {
                    visibleView.view.setY(visibleView.view.getY() + TranslationAmount);
                }

                ArrayList<VisibleView> insertedViews = preloadObjectsBetweenRowsInImageOf(firstVisibleRowIndex, getLastVisibleRowIndex() - firstVisibleRowIndex + 1,
                        HistoricContent, HistoricRows, true);

                if (DEBUG_DATASETCHANGE) {
                    Log.d(TAG, insertedViews.size() + " newly inserted views.");
                }

                for (final VisibleView visibleView : insertedViews) {
                    if (animationsEnabled) {
                        visibleView.view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        visibleView.view.buildLayer();
                        insertAnimator.playAnimation(visibleView.view, visibleView.target.item, visibleView.section.viewType);
                        visibleView.view.animate()
                                .setDuration(moveDuration)
                                .setInterpolator(insertInterpolator)
                                .setStartDelay(0)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationStart(Animator animation) {
                                    }

                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        visibleView.view.setLayerType(View.LAYER_TYPE_NONE, null);
                                        visibleView.view.animate().setListener(null);
                                    }
                                }).start();
                    }
                }

                for (final VisibleView visibleView : boundViews) {
                    visibleView.view.setY(visibleView.view.getY() - TranslationAmount);
                }

                moveViewsAndCleanup(true, true);
            }
        };

        final Runnable EndRunnableRetainedScroll = new Runnable() {

            @Override
            public void run() {

                if (logicalRows.size() == 0) {
                    showEmptyView();
                }

                int translationAmount = 0;

                if (getScrollY() + getHeight() > containerHeight) {
                    translationAmount = containerHeight - getScrollY() - getHeight();
                }

                if (DEBUG_DATASETCHANGE) Log.d(TAG, "Translation amount is " + translationAmount);

                int scrollY = getScrollY() + translationAmount;
                if (scrollY < 0) {
                    translationAmount = translationAmount - scrollY;
                    scrollY = 0;
                }

                final int ScrollY = getScrollY();

                scrollBy(0, - getScrollY() + scrollY);
                smoothScrollBy(0, 0);

                if (onScrollListener != null && animationsEnabled) {
                    onScrollListener.onScroll(CollectionView.this, ScrollY, 0);
                }

                int firstVisibleRowIndex = getFirstVisibleRowIndex(scrollY);
                if (DEBUG_DATASETCHANGE) Log.d(TAG, "First visible row is " + firstVisibleRowIndex);

                for (final VisibleView visibleView : boundViews) {
                    visibleView.view.setY(visibleView.view.getY() + translationAmount);
                }

                int size;
                if (getHeight() <= maxHeight) {
                    size = Math.min(maxHeight, containerHeight);
                }
                else {
                    size = getHeight();
                }

                ArrayList<VisibleView> insertedViews = preloadObjectsBetweenRowsInImageOf(firstVisibleRowIndex, getLastVisibleRowIndex(scrollY + size) - firstVisibleRowIndex + 1,
                        HistoricContent, HistoricRows, true);

                if (DEBUG_DATASETCHANGE) {
                    Log.d(TAG, insertedViews.size() + " newly inserted views.");
                }

                for (final VisibleView visibleView : insertedViews) {
                    visibleView.view.setY(visibleView.view.getY() + translationAmount);
                    if (animationsEnabled) {
                        visibleView.view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        visibleView.view.buildLayer();
                        insertAnimator.playAnimation(visibleView.view, visibleView.target.item, visibleView.section.viewType);
                        visibleView.view.animate()
                                .setDuration(moveDuration)
                                .setInterpolator(insertInterpolator)
                                .setStartDelay(0)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationStart(Animator animation) {
                                    }

                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        visibleView.view.setLayerType(View.LAYER_TYPE_NONE, null);
                                        visibleView.view.animate().setListener(null);
                                    }
                                }).start();
                    }
                }

                // Virtual onScrollChangedListener
                if (translationAmount != 0 && animationsEnabled) {
                    ValueAnimator scrollUpdater = ValueAnimator.ofInt(ScrollY, getScrollY());
                    scrollUpdater.setDuration(moveDuration);
                    scrollUpdater.setInterpolator(moveInterpolator);
                    scrollUpdater.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator valueAnimator) {
                            if (onScrollListener != null) {
                                onScrollListener.onScroll(CollectionView.this, (Integer) valueAnimator.getAnimatedValue(), 0);
                            }
                        }
                    });
                    scrollUpdater.start();

                    moveViewsAndCleanup(true, true, false);
                }
                else {
                    moveViewsAndCleanup(true, true, true);
                }
            }
        };

        final Runnable EndRunnableAnchored = new Runnable() {
            @Override
            public void run() {

                if (DEBUG_BACKENDUNDO) Log.d(TAG, "EndRunnableAnchored.run() has started!");

                if (layout.size() == 0 || logicalRows.size() == 0) {
                    EndRunnableRetainedScroll.run();
                    return;
                }

                VisibleView anchor = null;
                if (anchorInspector == null) {
                    anchor = layout.get(0);
                }
                else {
                    for (VisibleView visibleView : layout) {
                        if (anchorInspector.isAnchor(visibleView.target.item, visibleView.section.viewType)) {
                            anchor = visibleView;
                            break;
                        }
                    }
                }

                if (anchor == null) {
                    EndRunnableRetainedScroll.run();
                    return;
                }

                // The overscroll is the amount by which the topmostview is off-screen, if any
                int overScroll = anchor.view.getY() < getScrollY() ? (int) (getScrollY() - anchor.view.getY()) : 0;

                // The topMargin is the distance between the top of the view and the top of the visible area, if positive
                int topMargin = anchor.view.getY() > getScrollY() ? (int) (getScrollY() - anchor.view.getY()) : 0;

                // The container has to scroll and displace by the difference between the topmost view's current position
                // and it's position in the new dataset
                int newRow = getRowOfItem(anchor.target);
                if (newRow == -1) {
                    // If the row could not be found, run the retained scroll sequence
                    Log.d(TAG, "Retaining scroll because the new row could not be found.");
                    EndRunnableRetainedScroll.run();
                    return;
                }

                Row row = logicalRows.get(newRow);
                int scrollY = getScrollY();

                final int ScrollY = getScrollY();
//                Log.d(TAG, "PreScrollY is " + scrollY);

                // The translation amount accounts for the overscroll
//                int translationAmount = (int) (anchor.view.getY() - logicalRows.get(newRow).start - topMargin);
                // The translation amount is the difference between the final scroll position and the current scroll position
                int translationAmount = row.start + overScroll - scrollY;

                scrollY += translationAmount;

                if (scrollY + getHeight() > containerHeight) {
                    translationAmount = containerHeight - getScrollY() - getHeight();
                    scrollY = getScrollY() + translationAmount;
                }

                if (DEBUG_DATASETCHANGE) Log.d(TAG, "Anchored to " + anchor.target.item);

                if (DEBUG_DATASETCHANGE) Log.d(TAG, "Anchor translation amount is " + translationAmount);

                if (scrollY < 0) {
                    translationAmount = translationAmount - scrollY;
                    scrollY = 0;
                }

//                if (onScrollListener != null && animationsEnabled) {
//                    onScrollListener.onScroll(CollectionView.this, ScrollY, 0);
//                }

                final OnScrollListener OnScrollListener = onScrollListener;
                onScrollListener = null;

                scrollBy(0, - getScrollY() + scrollY);
                smoothScrollBy(0, 0);

                onScrollListener = OnScrollListener;

                int firstVisibleRowIndex = getFirstVisibleRowIndex(scrollY);
                if (DEBUG_DATASETCHANGE) Log.d(TAG, "First visible row is " + firstVisibleRowIndex);

                int rowCount = getLastVisibleRowIndex(scrollY + getHeight()) - firstVisibleRowIndex + 1;
                if (DEBUG_DATASETCHANGE) Log.d(TAG, "Row count is " + rowCount);

                final ArrayList<VisibleView> insertedViews = preloadObjectsBetweenRowsInImageOf(firstVisibleRowIndex, rowCount,
                        HistoricContent, HistoricRows, true);

                if (DEBUG_DATASETCHANGE) {
                    Log.d(TAG, insertedViews.size() + " newly inserted views.");

                    Log.d(TAG, "Layout views: ");
                    for (final VisibleView visibleView : layout) {
                        Log.d(TAG, "" + visibleView.target.item);
                    }

                    Log.d(TAG, "Layout size is: " + layout.size() + "; boundViews size is " + boundViews.size());
                }

                for (final VisibleView visibleView : boundViews) {
                    visibleView.view.setY(visibleView.view.getY() + translationAmount);
                }

                for (final VisibleView visibleView : insertedViews) {
                    visibleView.view.setY(visibleView.view.getY() - translationAmount);
                    if (animationsEnabled) {
                        visibleView.view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        visibleView.view.buildLayer();
                        insertAnimator.playAnimation(visibleView.view, visibleView.target.item, visibleView.section.viewType);
                        visibleView.view.animate()
                                .setDuration(moveDuration)
                                .setInterpolator(insertInterpolator)
                                .setStartDelay(0)
                                .setListener(new AnimatorListenerAdapter() {
                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        visibleView.view.setLayerType(View.LAYER_TYPE_NONE, null);
                                        visibleView.view.animate().setListener(null);
                                    }
                                }).start();
                    }
                }

                if (animationsEnabled) {
                    getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                        @Override
                        public boolean onPreDraw() {
                            getViewTreeObserver().removeOnPreDrawListener(this);
                            // Skip the next drawing phase to prevent flicker
                            return false;
                        }
                    });
                }

                // Virtual onScrollChangedListener
                if (translationAmount != 0 && animationsEnabled) {
                    ValueAnimator scrollUpdater = ValueAnimator.ofInt(ScrollY, scrollY);
                    scrollUpdater.setDuration(moveDuration);
                    scrollUpdater.setInterpolator(moveInterpolator);
                    scrollUpdater.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator valueAnimator) {
                            if (onScrollListener != null) {
                                onScrollListener.onScroll(CollectionView.this, (Integer) valueAnimator.getAnimatedValue(), 0);
                            }
//                            Log.d(TAG, "Current virtual ScrollY is " + (Integer) valueAnimator.getAnimatedValue());
                        }
                    });
                    scrollUpdater.start();

                    moveViewsAndCleanup(true, true, false);
                }
                else {
                    moveViewsAndCleanup(true, true, true);
                }

            }
        };

        if (lastAnimator != null) {
            if (TransactionScrollingMode == TransactionScrollingModeTop) {
                // if last animator is not null, animations are enabled
                for (final VisibleView visibleView : boundViews) {
                    visibleView.view.setY(visibleView.view.getY() - translationAmount);
                }
            }

            final VisibleView LastVisibleView = lastVisibleView;
            LastVisibleView.view.setLayerType(LAYER_TYPE_HARDWARE, null);
            lastVisibleView.view.buildLayer();
            lastVisibleView.view.animate().setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (DEBUG_DATASETCHANGE) {
                        Log.d(TAG, "Last delete animation has now reached its end!");
                    }
                    LastVisibleView.view.setLayerType(View.LAYER_TYPE_NONE, null);
                    LastVisibleView.view.animate().setListener(null);

                    if (layout.remove(LastVisibleView)) {
                        LastVisibleView.release();
                    }
                    if (LastVisibleView.retainCount != 0)
                        LastVisibleView.unbind();

                    deleteAnimator.resetState(LastVisibleView.view, LastVisibleView.target.item, LastVisibleView.section.viewType);

                    if (TransactionScrollingMode == TransactionScrollingModeTop)
                        EndRunnableUnanchored.run();
                    else if (TransactionScrollingMode == TransactionScrollingModeNoScroll)
                        EndRunnableRetainedScroll.run();
                    else
                        EndRunnableAnchored.run();
                }
            }).start();
        }

        if (lastAnimator == null || packedAnimations) {
            if (TransactionScrollingMode == TransactionScrollingModeTop || TransactionScrollingMode == TransactionScrollingModeNavigate) {
                if (logicalRows.size() == 0) {
                    showEmptyView();
                }

                int firstVisibleRowIndex = getFirstVisibleRowIndex();

                ArrayList<VisibleView> insertedViews = preloadObjectsBetweenRowsInImageOf(firstVisibleRowIndex, getLastVisibleRowIndex() - firstVisibleRowIndex + 1,
                        HistoricContent, HistoricRows, true);

                if (DEBUG_DATASETCHANGE || DEBUG_UNNECESSARY_ANIMATIONS) {
                    Log.d(TAG, insertedViews.size() + " newly inserted views.");
                }

                for (final VisibleView visibleView : insertedViews) {
                    if (animationsEnabled) {
                        visibleView.view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        visibleView.view.buildLayer();
                        visibleView.view.setY(visibleView.view.getY() + translationAmount);
                        insertAnimator.playAnimation(visibleView.view, visibleView.target.item, visibleView.section.viewType);
                        visibleView.view.animate()
                                .setDuration(moveDuration)
                                .setInterpolator(insertInterpolator)
                                .setStartDelay(0)
                                .setListener(new AnimatorListenerAdapter() {

                                    @Override
                                    public void onAnimationEnd(Animator animation) {
                                        visibleView.view.setLayerType(View.LAYER_TYPE_NONE, null);
                                        visibleView.view.animate().setListener(null);
                                    }
                                }).start();
                    }
                    else {
                        visibleView.view.setY(visibleView.view.getY() + translationAmount);
                    }
                }

                for (final VisibleView visibleView : boundViews) {
                    visibleView.view.setY(visibleView.view.getY() - translationAmount);
                }

                moveViewsAndCleanup(true, true);
            }
            else  if (TransactionScrollingMode == TransactionScrollingModeNoScroll) {
                EndRunnableRetainedScroll.run();
            }
            else {
                // If this does not run during post, unexplainable things can happen
                EndRunnableAnchored.run();
            }
        }

        // if transactionscrollingmode is set to navigation mode, by the time control reaches this point, the transaction will have been completed
        if (TransactionScrollingMode == TransactionScrollingModeNavigate) {
            animationsEnabled = AnimationsEnabled;
            cleanupHeldOff = false;

            Screenshot.setLayerType(LAYER_TYPE_HARDWARE, null);
            Screenshot.buildLayer();

            // Alpha animations are slow on this because the hardware layer needs frequent updates
            // from the container requesting layout on each frame
            setTranslationX(navigationTransactionDirection * getWidth());

            animate().translationX(0f).setDuration(moveDuration).setInterpolator(moveInterpolator)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            cleanup(true, true);
                        }
                    });

            Screenshot.animate().alpha(0f).translationX(- navigationTransactionDirection * getWidth()).setDuration(moveDuration).setInterpolator(moveInterpolator)
                    .withEndAction(new Runnable() {
                        @Override
                        public void run() {
                            ((ViewGroup) Screenshot.getParent()).removeView(Screenshot);
                        }
                    });

            if (containerAnimated && containerAnimator != null) {
                containerAnimator.setDuration(moveDuration);
                containerAnimator.setInterpolator(moveInterpolator);
                containerAnimator.start();
            }
        }

    }



    private View render() {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0) {
            Log.e("", "Trying to create bitmap with width = 0");
            width = getLayoutParams().width;
        }
        if (width <= 0) width = 1;
        if (height <= 0) {
            Log.e("", "Trying to create bitmap with height = 0");
            height = getLayoutParams().height;
        }
        if (height <= 0) height = 1;
        View screenshotView = new View(getContext());

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        for (VisibleView view : layout) {
            canvas.translate(0, view.view.getY() - getScrollY());
            view.view.draw(canvas);
            canvas.setMatrix(null);
        }
//        source.draw(new Canvas(bitmap));
        screenshotView.setBackgroundDrawable(new BitmapDrawable(bitmap));

        return screenshotView;
    }

    void shuffleTest() {

        final ArrayList<Section> HistoricSections = new ArrayList<Section>(sections);
        for (Section section : HistoricSections) {
            section.historicContent = new ArrayList<Item>(section.content);
        }
        final ArrayList<Row> HistoricRows = new ArrayList<Row>(logicalRows);

        for (Section section : sections) {
            for (int i = 0; i < section.content.size() / 2; i++) {
                int newPos = (int)(Math.random() * ((section.content.size())));
                section.content.add(newPos, section.content.remove(i));
            }
        }

        requestDisableInteractions();
        controller.requestBeginInternalTransaction();

        int rowStart = getFirstVisibleRowIndex();
        int rowEnd = getLastVisibleRowIndex();
        preloadViewsBetweenRowsInImageOf(rowStart, rowEnd - rowStart + 1, HistoricSections, HistoricRows);

        moveViewsAndCleanup();
    }

    public void ensureMinimumSupplyForViewType(final int supply, final int viewType) {

        final Runnable SupplyRunnable = new Runnable() {
            @Override
            public void run() {

                FrameLayout measureContainer = container;
                Section section = null;
                for (int i = 0; i < sections.size(); i++) {
                    if (sections.get(i).viewType == viewType) {
                        section = sections.get(i);
                        break;
                    }
                }

                if (section == null) return;

                int columnWidth = container.getWidth() / section.columnCount;

                for (int i = 0; i < supply && backburner.get(viewType).size() < backburnerLimit; i++) {
                    VisibleView visibleView = new VisibleView();
                    FrameLayout view = wrapView(controller.createView(section.viewType, measureContainer, LayoutInflater.from(getContext())), section.viewType);
                    view.setTag(visibleView);

                    if (DEBUG_MYSTERY) Log.d(TAG, "Initializing view with width " + columnWidth);
                    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(columnWidth, heights.get(viewType));
                    view.setLayoutParams(params);

                    visibleView.view = view;
                    visibleView.setInitialized(true);

                    backburner.get(viewType).add(visibleView);
                }

            }
        };

        if (container == null || getWidth() == 0) {
            addOnLayoutChangeListener(new OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                    if (getWidth() != 0 && container != null) {
                        removeOnLayoutChangeListener(this);
                        post(SupplyRunnable);
                    }
                }
            });
            return;
        }

        post(SupplyRunnable);
    }

    public void setCollectedViewLimit(int limit) {
        backburnerLimit = limit;
    }

    public CollectionViewController getController() {
        return controller;
    }

    private Runnable onMeasureHook;


    private int maxHeight = -1;
    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;

        requestLayout();
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        inLayout = true;

        int targetHeight = maxHeight == -1 ? containerHeight : Math.min(maxHeight, containerHeight);
        if (containerAnimated && containerAnimator != null) {
            targetHeight = Math.min(maxHeight, container.getLayoutParams().height);
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (getLayoutParams().height == LayoutParams.WRAP_CONTENT) {
            int heightSize = MeasureSpec.getSize(heightMeasureSpec);
            int heightMode = MeasureSpec.getMode(heightMeasureSpec);

            if (heightMode == MeasureSpec.AT_MOST) {
                setMeasuredDimension(getMeasuredWidth(), Math.min(heightSize, targetHeight));
            }
            if (heightMode == MeasureSpec.UNSPECIFIED) {
                setMeasuredDimension(getMeasuredWidth(), targetHeight);
            }
        }


        if (onMeasureHook != null) {
            onMeasureHook.run();
            onMeasureHook = null;
        }
    }

    public void setController(CollectionViewController controller) {
        if (this.controller != null) {
            for (VisibleView view : boundViews) {
                view.target.boundView = null;
            }
            this.controller.detach();
        }
        this.controller = controller;
        controller.attachTo(this);
        clearState();
        if (this.controller != null) {
            sections = controller.dataSet;

            getViewTypeCount();

            backburner = new ArrayList<ArrayList<VisibleView>>();
            for (int i = 0; i < viewTypeCount; i++) {
                backburner.add(new ArrayList<VisibleView>());
            }

            getHeightsForViewTypes();

            generateContainerAndRows();

//            scrollTo(0, 0);
//            if (DEBUG_GINGERBREAD) {
//                getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//                    @Override
//                    public void onGlobalLayout() {
//                        if (getHeight() > 0) {
//                            getViewTreeObserver().removeGlobalOnLayoutListener(this);
//                            bindInitialViews();
//                        }
//                    }
//                });
//            }
//            else {
            if (getHeight() == 0 || getWidth() == 0 || container.getWidth() == 0) {
                if (DEBUG_VARIABLE_COLUMN_COUNT) {
                    Log.d(TAG, "Height or width are 0, or the container width is 0, posting layout listener!");
                }

                getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (getHeight() != 0 && getWidth() != 0 && container.getWidth() != 0) {
                            getViewTreeObserver().removeGlobalOnLayoutListener(this);
                            initialSetupRunnable.run();
                        }
                    }
                });

//                post(new Runnable() {
//                    @Override
//                    public void run() {
//                        if (getHeight() == 0) {
//                            post(this);
//                        }
//                        else {
//                            bindInitialViews(CollectionView.this.controller.getSavedPosition());
//                        }
//                    }
//                });
            }
            else {
                initialSetupRunnable.run();
//                bindInitialViews(controller.getSavedPosition());
            }
//            }
        }
        else {
            sections = new ArrayList<Section>();
        }
    }

    private Runnable initialSetupRunnable = new Runnable() {
        @Override
        public void run() {
            if (DEBUG_VARIABLE_COLUMN_COUNT) {
                Log.d(TAG, "Initial setup has begun!");
            }
            CollectionViewController.SavedPosition position = CollectionView.this.controller.getSavedPosition();
            int row = 0;
            Row logicalRow = null;
            try {
                row = getRowOfItem(sections.get(position.sectionIndex).content.get(position.itemIndex));

                logicalRow = logicalRows.get(row);
            }
            catch (Exception e) {
                logicalRow = new Row();
                logicalRow.start = 0;
            }

            if (logicalRow.start != 0) {
                scrollTo(0, logicalRows.get(row).start);
                onScrollChanged(0, Math.min(logicalRow.start, containerHeight - getHeight()), 0, 0);
            }
            else {
                scrollTo(0, 0);
                onScrollChanged(0, 0, 0, 0);
            }

            if (logicalRows.size() == 0) {
                boolean animationsEnabled = CollectionView.this.animationsEnabled;
                CollectionView.this.animationsEnabled = false;
                showEmptyView();
                CollectionView.this.animationsEnabled = animationsEnabled;
            }

            // KIT KAT fix
            // Force the container to invalidate itself to prevent views being invisible
            // TODO Highlighted bugfix
            if (Build.VERSION.SDK_INT >= 19) {
                if (container != null) container.postInvalidate();
            }

            if (DEBUG_VARIABLE_COLUMN_COUNT) {
                Log.d(TAG, "Initial setup has ended.");
            }

//                            bindInitialViews(CollectionView.this.controller.getSavedPosition());
        }
    };

    @Override
    public void onWindowVisibilityChanged(int visible) {
        super.onWindowVisibilityChanged(visible);
        if (visible != VISIBLE) {
            smoothScrollTo(0, getScrollY());
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (!hasFocus) {
            smoothScrollTo(0, getScrollY());
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!animationsEnabled) {
            animationsEnabled = true;
        }

    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        smoothScrollTo(0, getScrollY());

        // Immediately end all current animations, and prevent any new animations from starting
        animationsEnabled = false;
        int sizePre;
        for (int i = 0; i < boundViews.size(); i++) {
            sizePre = boundViews.size();
            boundViews.get(i).view.animate().cancel();
            if (boundViews.size() != sizePre)
                i--;
        }
        while (activeAnimators.size() > 0) {
            activeAnimators.get(0).cancel();
        }

        if (controller != null) {
            if (layout.size() > 0) {
                controller.savePosition(sections.indexOf(layout.get(0).section), layout.get(0).section.content.indexOf(layout.get(0).target));
            }
            else {
                controller.savePosition(0, 0);
            }
            for (VisibleView view : boundViews) {
                view.target.boundView = null;
            }
            controller.detach();
        }
    }

    private float lastTouchY;
    private float lastTouchX;
    private float accumulatedOverScroll;
    private float accumulatedXScroll;
    private boolean overScrollActive;
    private boolean initializedOverScroll;

    @Override
    public boolean onInterceptTouchEvent(MotionEvent event) {
        if (disableInteractionRequests != 0) {
            return true;
        }

        if (onOverScrollListener == null || !overScrollEnabled) return super.onInterceptTouchEvent(event);

        if (getScrollY() <= 0) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || !initializedOverScroll) {
                // don't intercept the down event; some child might want to
                initializedOverScroll = true;
                lastTouchY = event.getY();
                lastTouchX = event.getX();
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                accumulatedOverScroll += event.getY() - lastTouchY;
                accumulatedXScroll += event.getX() - lastTouchX;

                lastTouchY = event.getY();
                lastTouchX = event.getX();

                // intercept overscroll
                if (accumulatedOverScroll > ViewConfiguration.get(getContext()).getScaledTouchSlop() && //user is dragging finger from top down
                        Math.abs(accumulatedXScroll) < Math.abs(accumulatedOverScroll)) { // and Y scroll must be larger than X scroll

                    try {
                        super.onInterceptTouchEvent(event); //allow super to process this touch event before returning true
                    }
                    catch (IllegalArgumentException e) {/* Nothing to do here */}
                    return true;
                }
            }
            if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                // when the touch event ends, clear the overscroll counter
                accumulatedOverScroll = 0;
                accumulatedXScroll = 0;
                initializedOverScroll = false;
            }
        }
        else {
            accumulatedOverScroll = 0;
            accumulatedXScroll = 0;
            initializedOverScroll = false;
        }

        try {
            return super.onInterceptTouchEvent(event);
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (disableInteractionRequests != 0) {
            return false;
        }

        if (onOverScrollListener == null || !overScrollEnabled) return super.onTouchEvent(event);

        if (getScrollY() <= 0) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || !initializedOverScroll) {
                initializedOverScroll = true;
                lastTouchY = event.getY();
            }

            if (event.getAction() == MotionEvent.ACTION_MOVE) {
                accumulatedOverScroll += event.getY() - lastTouchY;
                lastTouchY = event.getY();
            }

            if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
                if (accumulatedOverScroll > 0) {
                    if (overScrollActive) overScrollActive = false;
                    if (onOverScrollListener != null) {
                        onOverScrollListener.onOverScrollStopped(this);
                    }
                }

                accumulatedOverScroll = 0;
                accumulatedXScroll = 0;
            }

            if (accumulatedOverScroll > 0) {
                if (!overScrollActive) overScrollActive = true;
                // if there is some overscroll, notify the listener
                if (onOverScrollListener != null) {
                    onOverScrollListener.onOverScroll(this, (int) accumulatedOverScroll, OverScrollTop);
                }
                return true;
            }
            else {
                if (overScrollActive) {
                    overScrollActive = false;
                    if (onOverScrollListener != null) {
                        onOverScrollListener.onOverScrollStopped(this);
                    }
                }
                try {
                    return super.onTouchEvent(event);
                }
                catch (IllegalArgumentException e) {
                    return false;
                }
            }
        }
        else {
            accumulatedOverScroll = 0;
            accumulatedXScroll = 0;
            initializedOverScroll = false;
        }

        try {
            return super.onTouchEvent(event);
        }
        catch (IllegalArgumentException e) {
            return false;
        }
    }

    private boolean playInitialAnimation = false;

    public void playInitialAnimation() {
        playInitialAnimation = true;
    }

    public void setOnViewCollectedListener(OnViewCollectedListener listener) {
        viewCollectedListener = listener;
    }

    public OnViewCollectedListener getOnViewCollectedListener() {
        return viewCollectedListener;
    }

    /**
     * Sets the ids of the views that contain legacy ripple drawables.
     * Whenever the CollectionView collects a view, all subviews with those ids
     * will have their ripple animations instantly cancelled, in order to prevent them
     * from running when those views are reused for other objects.
     * @param ids A list of the ids for views that have a LegacyRippleDrawable as their background.
     */
    public void setLegacyRippleDrawableIDs(int ... ids) {
        legacyRippleDrawableIDs = new int[ids.length];

        System.arraycopy(ids, 0, legacyRippleDrawableIDs, 0, ids.length);
    }

    /**
     * Returns the previously set ids of views that have a LegacyRippleDrawable background.
     * @return The previously set ids of views that have a LegacyRippleDrawable background.
     */
    public int[] getLegacyRippleDrawableIDs() {
        int [] ids = new int[legacyRippleDrawableIDs.length];

        System.arraycopy(legacyRippleDrawableIDs, 0, ids, 0, legacyRippleDrawableIDs.length);

        return ids;
    }

    /**
     * Uses the previously set LegacyRippleDrawable IDs to cancel all ripple animations on the relevant subviews.
     * @param view The root view in which to search for subviews with ripple animations.
     */
    public void flushRipplesOnView(View view) {
        // Cancel all current LegacyRippleDrawable animations
        if (legacyRippleDrawableIDs != null) {
            for (int id : legacyRippleDrawableIDs) {
                View drawableHost = view.findViewById(id);

                if (drawableHost != null) {
                    ((LegacyRippleDrawable) drawableHost.getBackground()).flushRipple();
                }
            }
        }
    }

    public ReversibleAnimation getDeleteAnimator() {
        return deleteAnimator;
    }

    public ReversibleAnimation getInsertAnimator() {
        return insertAnimator;
    }

    public void setDeleteAnimationDuration(long duration) {
        deleteDuration = duration;
    }

    public void setDeleteAnimationStride(long stride) {
        deleteStride = stride;
    }

    public long getDeleteAnimationDuration() {
        return deleteDuration;
    }

    public void setMoveAnimationDuration(long duration) {
        moveDuration = duration;
    }

    public long getMoveAnimationDuration() {
        return moveDuration;
    }

    public void setDeleteInterpolator(TimeInterpolator interpolator) {
        deleteInterpolator = interpolator;
    }

    public TimeInterpolator getDeleteInterpolator() {
        return deleteInterpolator;
    }

    public void setDeleteAnimator(ReversibleAnimation animator) {
        deleteAnimator = animator;
        if (animator == null) deleteAnimator = new ReversibleAnimation() {
            @Override
            public void playAnimation(View view, Object object, int viewType) {
                playDeleteAnimation(view);
            }

            @Override
            public void resetState(View view, Object object, int viewType) {
                reverseDeleteAnimation(view);
            }
        };
    }

    public void setInsertAnimator(ReversibleAnimation animator) {
        insertAnimator = animator;
        if (animator == null) insertAnimator = new ReversibleAnimation() {
            @Override
            public void playAnimation(View view, Object object, int viewType) {
                playInsertAnimation(view);
            }

            @Override
            public void resetState(View view, Object object, int viewType) {
            }
        };
    }

    public void setOnScrollListener(OnScrollListener listener) {
        onScrollListener = listener;
    }

    public OnScrollListener getOnScrollListener() {
        return onScrollListener;
    }

    public void setOnOverScrollListener(OnOverScrollListener listener) {
        onOverScrollListener = listener;
    }

    public OnOverScrollListener getOnOverScrollListener() { return onOverScrollListener; }

    public void setOverScrollEnabled(boolean enabled) {
        overScrollEnabled = enabled;
    }

    public void addTransactionListener(TransactionListener listener) {
        transactionListeners.add(listener);
    }

    public boolean removeTransactionListener(TransactionListener listener) {
        return transactionListeners.remove(listener);
    }

    public void setGravity(int gravity) {
        this.gravity = gravity;

        if (gravity == Gravity.CENTER_VERTICAL || gravity == Gravity.CENTER && getHeight() > 0) {
            if (logicalRows.size() > 0) {
                if (logicalRows.get(logicalRows.size() - 1).end < getHeight()) {
                    if (DEBUG_GRAVITY) if (gravity != Gravity.TOP) Log.d(TAG, "Displacing rows by: " + (getHeight() / 2 - logicalRows.get(logicalRows.size() - 1).end / 2) + " from setGravity()");
                    displaceRows(getHeight() / 2 - logicalRows.get(logicalRows.size() - 1).end / 2);
                }
            }
        }
    }

    public int getGravity() {
        return gravity;
    }

    private boolean scrollOnFocus = true;

    public void runForEachView(ViewRunnable runnable) {
        for (VisibleView visibleView : layout) {
            runnable.runForView(visibleView.view.getChildAt(0), visibleView.target.item, visibleView.section.viewType);
        }
    }

    public void runForEachVisibleView(ViewRunnable runnable) {
        for (VisibleView visibleView : boundViews) {
            runnable.runForView(visibleView.view.getChildAt(0), visibleView.target.item, visibleView.section.viewType);
        }
    }

    public void runForEachCollectedView(ViewRunnable runnable) {
        for (int viewType = 0; viewType < backburner.size(); viewType++) {
            for (VisibleView visibleView : backburner.get(viewType)) {
                runnable.runForView(visibleView.view.getChildAt(0), null, viewType);
            }
        }
    }

    /**
     * Creates a new ViewProxy wrapper around all the currently visible views.
     * The views will be ordered in the wrapper as they appear on screen from left to right then top to bottom.
     * @return A new ViewProxy wrapper around all the currently visible views.
     */
    public $ visibleViews() {
        View [] visibleViews = new View[layout.size()];
        int i = 0;
        for (VisibleView view : layout) {
            visibleViews[i] = view.view.getChildAt(0);
            i++;
        }

        return visibleViews.length > 0 ? $.wrap(visibleViews) : $.emptySet((Activity) getContext());
    }

    @Override
    public boolean requestChildRectangleOnScreen(View view, Rect rect, boolean immediate) {
        if (scrollOnFocus)
            return super.requestChildRectangleOnScreen(view, rect, immediate);
        return false;
    }

    public void setScrollOnFocus(boolean scrollOnFocus) {
        this.scrollOnFocus = scrollOnFocus;
    }

    @Override
    public ArrayList<View> getFocusables(int direction) {
        if (scrollOnFocus)
            return super.getFocusables(direction);
        return new ArrayList<View>();
    }

    @Override
    public boolean onRequestFocusInDescendants(int direction, Rect previouslyFocusedRect) {
        if (scrollOnFocus)
            return super.onRequestFocusInDescendants(direction, previouslyFocusedRect);
        return true;
    }

    public CollectionView(Context context) {
        super(context);
        Density = context.getResources().getDisplayMetrics().density;
        init();
    }

    public CollectionView(Context context, AttributeSet attrs) {
        super(context, attrs);
        Density = context.getResources().getDisplayMetrics().density;
        init();
    }

    public CollectionView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        Density = context.getResources().getDisplayMetrics().density;
        init();
    }

    public void init() {
        setFillViewport(true);
        setDuplicateParentStateEnabled(false);
    }

}
