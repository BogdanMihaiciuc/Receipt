package com.BogdanMihaiciuc.util;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;

import static com.BogdanMihaiciuc.util.CollectionView.Item;
import static com.BogdanMihaiciuc.util.CollectionView.Section;
import static com.BogdanMihaiciuc.util.CollectionView.TAG;

public abstract class CollectionViewController {

    final static boolean DEBUG = false;

    public interface ObjectComparator {
        public boolean areObjectsEqual(Object object1, Object object2);
    }

    final static ObjectComparator StandardComparator = new ObjectComparator() {
        @Override
        public boolean areObjectsEqual(Object object1, Object object2) {
            return object1 == object2;
        }
    };

    static class SavedPosition {
        int sectionIndex;
        int itemIndex;
    }

    int viewTypeCount = 0;

    ArrayList<CollectionView.Section> dataSet = new ArrayList<Section>();
    ArrayList<Section> pendingDataSet;

    private boolean isRefreshingViews = false;

    public Section addSection() {
        return addSectionForViewTypeWithTag(viewTypeCount, null);
    }

    public Section addSectionWithTag(String tag) {
        return addSectionForViewTypeWithTag(viewTypeCount, tag);
    }

    public Section addSectionForViewTypeWithTag(int viewType, String tag) {
        Section section = new Section(viewType, tag);

        if (internalTransactionRequestCount + externalTransactionRequestCount > 0) {
            if (!dirty) {
                dirty = true;
                generatePendingDataSet();
            }
            pendingDataSet.add(section);
            section.historicContent = new ArrayList<Item>();
        }
        else {
            dataSet.add(section);
        }

        if (viewType >= viewTypeCount) {
            viewTypeCount = viewType + 1;
        }

        // TODO notify CollectionView if attached
        return section;
    }

    public Section findSectionWithTag(String tag) {
        ArrayList<Section> dataset = dataSet;

        if (internalTransactionRequestCount + externalTransactionRequestCount > 0) {
            if (!dirty) {
                dirty = true;
                generatePendingDataSet();
            }
            dataset = pendingDataSet;
        }

        for (Section section : dataset) {
            if (section.Tag != null) {
                if (section.Tag.equals(tag)) return section;
            }
        }

        return null;
    }

    public Object findMetadata(String tag) {
        ArrayList<Section> dataset = dataSet;

        if (internalTransactionRequestCount + externalTransactionRequestCount > 0) {
            if (dirty) {
                dataset = pendingDataSet;
            }
        }

        for (Section section : dataset) {
            if (section.Tag != null) {
                if (section.Tag.equals(tag)) return section.metadata;
            }
        }

        return null;
    }

    public Section getSectionAtIndex(int index) {
        if (internalTransactionRequestCount + externalTransactionRequestCount > 0) {
            if (!dirty) {
                dirty = true;
                generatePendingDataSet();
            }
            return pendingDataSet.get(index);
        }
        else {
            return dataSet.get(index);
        }
    }

    public int getIndexOfSection(Section section) {
        if (internalTransactionRequestCount + externalTransactionRequestCount > 0) {
            if (!dirty) {
                dirty = true;
                generatePendingDataSet();
            }
            return pendingDataSet.indexOf(section);
        }
        else {
            return dataSet.indexOf(section);
        }
    }

    public int getSectionCount() {
        if (internalTransactionRequestCount + externalTransactionRequestCount > 0) {
            if (!dirty) {
                dirty = true;
                generatePendingDataSet();
            }
            return pendingDataSet.size();
        }
        else {
            return dataSet.size();
        }
    }

    public void clear() {
        if (internalTransactionRequestCount + externalTransactionRequestCount > 0) {
            if (!dirty) {
                dirty = true;
                generatePendingDataSet();
            }
            pendingDataSet.clear();
        }
        else {
            dataSet.clear();
        }
    }

    ObjectComparator comparator = StandardComparator;
    CollectionView collectionView;

    public void setComparator(ObjectComparator comparator) {
        if (comparator == null) {
            this.comparator = StandardComparator;
        }
        else {
            this.comparator = comparator;
        }
    }

    private Boolean refreshViewsOnTransaction;

    public void setRefreshViewsOnTransactionEnabled(Boolean enabled) {
        this.refreshViewsOnTransaction = enabled;
    }

    void detach() {
        onDetachedFromCollectionView(collectionView);

        collectionView = null;
//        for (Section section : dataSet) {
//            for (Item item : section.content) {
//                item.boundView = null;
//            }
//        }
    }

    protected void onDetachedFromCollectionView(CollectionView collectionView) {

    }

    void attachTo(CollectionView collectionView) {
        this.collectionView = collectionView;
        if (refreshViewsOnTransaction != null) {
            collectionView.setRefreshViewsOnTransactionEnabled(refreshViewsOnTransaction);
        }

        onAttachedToCollectionView(collectionView);
    }

    protected void onAttachedToCollectionView(CollectionView collectionView) {

    }

    // A controller becomes "dirty" if its data set changes during an internal transaction state or if an external
    // transaction state is requested
    private boolean dirty;
    private int externalTransactionRequestCount;
    private int internalTransactionRequestCount;

    public void purgeSavedState() {
        savedPosition.itemIndex = 0;
        savedPosition.sectionIndex = 0;
    }

    void generatePendingDataSet() {
        savedPosition.itemIndex = 0;
        savedPosition.sectionIndex = 0;
        pendingDataSet = new ArrayList<Section>(dataSet.size());

        for (Section section : dataSet) {
            // During a transaction, the field normally used for historic content is instead used for pending content
            pendingDataSet.add(new Section(section));
        }
    }

    void requestBeginInternalTransaction() {
        internalTransactionRequestCount++;
        if (DEBUG) Log.d(TAG, "Internal begin; Internal transaction count: " + internalTransactionRequestCount + ", external " + externalTransactionRequestCount);
    }

    void requestEndInternalTransaction() {
        internalTransactionRequestCount--;
        if (DEBUG) Log.d(TAG, "Internal end; Internal transaction count: " + internalTransactionRequestCount + ", external " + externalTransactionRequestCount);

        if (internalTransactionRequestCount + externalTransactionRequestCount == 0 && dirty) {
            dirty = false;
            collectionView.onTransactionComplete(pendingDataSet);
            dataSet = pendingDataSet;
        }
    }

    public CollectionViewController requestBeginTransaction() {

        if (!dirty) {
            generatePendingDataSet();
        }

        dirty = true;

        externalTransactionRequestCount++;
        if (DEBUG) Log.d(TAG, "External begin; Internal transaction count: " + internalTransactionRequestCount + ", external " + externalTransactionRequestCount);

        return this;
    }

    public CollectionViewController holdTransaction() {

        externalTransactionRequestCount++;

        return this;
    }

    public void releaseTransaction() {

        externalTransactionRequestCount--;

        if (internalTransactionRequestCount + externalTransactionRequestCount == 0 && dirty) {
            dirty = false;
            collectionView.onTransactionComplete(pendingDataSet);
            dataSet = pendingDataSet;
        }

    }

    public CollectionViewController requestBeginNewDataSetTransaction() {

        savedPosition.itemIndex = 0;
        savedPosition.sectionIndex = 0;
        pendingDataSet = new ArrayList<Section>();

        dirty = true;

        externalTransactionRequestCount++;
        if (DEBUG) Log.d(TAG, "External begin; Internal transaction count: " + internalTransactionRequestCount + ", external " + externalTransactionRequestCount);

        return this;
    }

    public void requestCompleteTransaction() {
        externalTransactionRequestCount--;
        if (DEBUG) Log.d(TAG, "External end; Internal transaction count: " + internalTransactionRequestCount + ", external " + externalTransactionRequestCount);

        if (internalTransactionRequestCount + externalTransactionRequestCount == 0) {
            dirty = false;
            dataSet = pendingDataSet;
            if (collectionView != null) {
                if (CollectionView.DEBUG_COLLECTION_EVENT_DELEGATE) Log.d(TAG, "collection view will complete transaction!");
                collectionView.onTransactionComplete(pendingDataSet);
            }
        }

    }

    public final boolean isInTransaction() {
        return internalTransactionRequestCount + externalTransactionRequestCount > 0;
    }

    int transactionCount() {
       return internalTransactionRequestCount + externalTransactionRequestCount;
    }

    public void cancelTransaction() {
        pendingDataSet = null;
        for (Section section : dataSet) {
            section.historicContent = null;
        }
    }

    public CollectionView getCollectionView() {
        return collectionView;
    }

    private SavedPosition savedPosition = new SavedPosition();

    void savePosition(int section, int item) {
        savedPosition.itemIndex = item;
        savedPosition.sectionIndex = section;
    }

    SavedPosition getSavedPosition() {
        return savedPosition;
    }

    public View retainView(View view) {
        return collectionView != null ? collectionView.retainView(view) : null;
    }

    public void releaseView(View view) {
        if (collectionView != null) collectionView.releaseView(view);
    }

    public Object getObjectForView(View view) {
        return collectionView != null ? collectionView.getObjectForView(view) : null;
    }

    public View getViewForObject(Object object) {
        return collectionView != null ? collectionView.getViewForObject(object) : null;
    }

    public View retainViewForObject(Object object) {
        return collectionView != null ? collectionView.retainViewForObject(object) : null;
    }

    public void setColumnCountForViewType(int columnCount, int viewType) {
        for (Section section : dataSet) {
            if (section.viewType == viewType) {
                section.columnCount = columnCount;
            }
        }
    }

    public int getViewTypeCount() {
        int maxViewType = 0;
        for (Section section : dataSet) {
            if (section.viewType > maxViewType) {
                maxViewType = section.viewType;
            }
        }
        return maxViewType + 1;
    }

    void setIsRefreshingViews(boolean isRefreshingViews) {
        this.isRefreshingViews = isRefreshingViews;
    }

    public boolean isRefreshingViews() {
        return isRefreshingViews;
    }

    public void requestConfigureView(View view, Object item, int viewType) {
        boolean isRefreshingViews = this.isRefreshingViews;
        setIsRefreshingViews(true);
        configureView(view, item, viewType);
        setIsRefreshingViews(isRefreshingViews);
    }

    public abstract View createView(int viewType, ViewGroup container, LayoutInflater inflater);
    public abstract void configureView(View view, Object item, int viewType);
    public View createEmptyView(ViewGroup container, LayoutInflater inflater) { return null; }

}
