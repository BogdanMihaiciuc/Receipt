package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.ContentValues;
import android.content.Context;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.$;
import com.BogdanMihaiciuc.util.CalendarPicker;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.DisableableFrameLayout;
import com.BogdanMihaiciuc.util.ExtendedFragment;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.PopoverDrawable;
import com.BogdanMihaiciuc.util.TagView;
import com.BogdanMihaiciuc.util.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class HistoryViewerFragment extends ExtendedFragment implements LegacyActionBar.OnLegacyActionSelectedListener, TagExpander.OnTagDeletedListener {

    final static String TAG = HistoryViewerFragment.class.getName();

    final static boolean DEBUG = true;
    final static boolean DEBUG_SLOW_LOADER = false;
    final static boolean DELAY_SLOW_TRANSACTIONS = true;
    // The amount of milliseconds the loader is allowed to take before it is considered slow
    // When a loader exceeds this limit, view drawing will be delayed until after animations have ended
    // to ensure there will be no dropped frames
    final static long SlowLoaderThreshold = 120;

    final static String ActionBarKey = "historyViewerFragment.actionBar";

    class HistoryItem extends ItemCollectionFragment.Item {
        long databaseUID;

        protected void addTagToDatabase(final ItemCollectionFragment.Tag tag) {
            new Thread() {
                public void run() {
                    synchronized (Receipt.DatabaseLock) {
                        SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();

                        ContentValues values = new ContentValues();
                        values.put(Receipt.DBTagConnectionUIDKey, tag.tagUID);
                        values.put(Receipt.DBItemConnectionUIDKey, databaseUID);

                        db.insert(Receipt.DBTagConnectionsTable, null, values);

                        db.close();
                    }
                }
            }.start();

            tagsInvalidated = true;
        }

        @Override
        public void addTag(final ItemCollectionFragment.Tag tag) {
            if (tags.contains(tag)) return;
            super.addTag(tag);
            // automagically calls addTagToIndex()
//            addTagToDatabase(tag);
        }

        @Override
        public void addTagToIndex(final ItemCollectionFragment.Tag tag, int index) {
            if (tags.contains(tag)) return;
            super.addTagToIndex(tag, index);
            addTagToDatabase(tag);
        }

        public void removeTagAtIndex(int index) {
            final ItemCollectionFragment.Tag tag = tags.get(index);
            super.removeTagAtIndex(index);
            new Thread() {
                public void run() {
                    synchronized (Receipt.DatabaseLock) {
                        SQLiteDatabase database = Receipt.DBHelper.getWritableDatabase();
                        database.delete(Receipt.DBTagConnectionsTable,
                                Receipt.DBItemConnectionUIDKey + " = " + databaseUID + " and " +
                                        Receipt.DBTagConnectionUIDKey + " = " + tag.tagUID,
                                null);
                        database.close();
                    }
                }
            }.start();

            tagsInvalidated = true;
            // No need to notify the adapter; the TagExpander takes care of updating the relevant view
        }

        public void removeTag(ItemCollectionFragment.Tag tag) {
            int index = tags.indexOf(tag);
            if (index != -1) {
                removeTagAtIndex(index);
            }
        }

        public void removeTags(final ArrayList<ItemCollectionFragment.Tag> tags) {
            this.tags.removeAll(tags);
            new Thread() {
                public void run() {
                    synchronized (Receipt.DatabaseLock) {
                        SQLiteDatabase database = Receipt.DBHelper.getWritableDatabase();
                        for (ItemCollectionFragment.Tag tag : tags) {
                            database.delete(Receipt.DBTagConnectionsTable,
                                    Receipt.DBItemConnectionUIDKey + " = " + databaseUID + " and " +
                                            Receipt.DBTagConnectionUIDKey + " = " + tag.tagUID,
                                    null);
                        }
                        database.close();
                    }
                }
            }.start();

            tagsInvalidated = true;
        }
    }

    static interface SearchResolver {
        public abstract boolean matchesSearch(String string);
    }

    final static int DetailCheckoutTime = 0;
    final static int DetailItemCount = 1;
    final static int DetailBudget = 2;
    final static int DetailRemainingBudget = 3;
    final static int DetailTax = 4;
    final static int DetailSubtotal = 5;

    final static int DetailDisplacement = 1;
    final static int DetailCount = 7;
    final static String DetailLabels[];
    final static int DetailIDs[] = {
            R.id.CheckoutDate,
            R.id.CheckoutTime,
            R.id.ItemCount,
            R.id.BudgetAssigned,
            R.id.BudgetRemaining,
            R.id.Tax,
            R.id.Subtotal
    };
    final static int DetailLabelTypes[] = {
            PanelBuilder.TypeButton,
            PanelBuilder.TypeButton,
            PanelBuilder.TypeText,
            PanelBuilder.TypeText,
            PanelBuilder.TypeText,
            PanelBuilder.TypeText,
            PanelBuilder.TypeText
    };

    static {
        DetailLabels = new String[DetailCount];

        DetailLabels[0] =                                             Receipt.getStaticContext().getString(R.string.CheckoutDate);
        DetailLabels[DetailCheckoutTime + DetailDisplacement] =       Receipt.getStaticContext().getString(R.string.CheckoutTime);
        DetailLabels[DetailItemCount + DetailDisplacement] =          Receipt.getStaticContext().getString(R.string.ItemCount);
        DetailLabels[DetailBudget + DetailDisplacement] =             Receipt.getStaticContext().getString(R.string.BudgetAssigned);
        DetailLabels[DetailRemainingBudget + DetailDisplacement] =    Receipt.getStaticContext().getString(R.string.BudgetRemaining);
        DetailLabels[DetailTax + DetailDisplacement] =                Receipt.getStaticContext().getString(R.string.Tax);
        DetailLabels[DetailSubtotal + DetailDisplacement] =           Receipt.getStaticContext().getString(R.string.Subtotal);
//        DetailLabels[DetailCount - 1] =                               Receipt.getStaticContext().getString(R.string.Total);

    }

    static class DataSet {
        ArrayList<HistoryItem> items;

        long details[];
        long price;
        Calendar date;
        String name;
    }

    class ItemLoaderTask extends AsyncTask<Long, Void, DataSet> {

        boolean finished;
        final Handler UIHandler = new Handler();
        final Runnable UIRunnable = new Runnable() {
            @Override
            public void run() {
                if (!finished) {
                    artificialTransaction = true;
                    controller.requestBeginTransaction();
                }
            }
        };

        protected void onPreExecute() {
            if (DELAY_SLOW_TRANSACTIONS) UIHandler.postDelayed(UIRunnable, SlowLoaderThreshold);
        }

        @Override
        protected DataSet doInBackground(Long ... params) {
            long target = params[0];

            SQLiteDatabase database = Receipt.DBHelper.getReadableDatabase();
            DataSet result = new DataSet();
            result.items = new ArrayList<HistoryItem>();

            synchronized (Receipt.DatabaseLock) {
                Cursor itemsCursor = database.query(Receipt.DBItemsTable, Receipt.DBAllItemsColumns, Receipt.DBTargetDBKey + "=" + target, null, null, null,
                        Receipt.DBIndexInReceiptKey);

                while (itemsCursor.moveToNext()) {
                    if (isCancelled()) {
                        itemsCursor.close();
                        database.close();
                        return null;
                    }

                    HistoryItem item = new HistoryItem();

                    item.databaseUID = itemsCursor.getLong(Receipt.DBUIDKeyIndex);
                    item.name = itemsCursor.getString(Receipt.DBNameKeyIndex);
                    item.qty = itemsCursor.getLong(Receipt.DBQtyKeyIndex);
                    item.unitOfMeasurement = itemsCursor.getString(Receipt.DBUnitOfMeasurementKeyIndex);
                    item.price = itemsCursor.getLong(Receipt.DBPriceKeyIndex);


                    Cursor connections = database.query(false, Receipt.DBTagConnectionsTable, Receipt.DBAllTagConnectionColumns, Receipt.DBItemConnectionUIDKey + " = " + item.databaseUID,
                            null, null, null, null, null);

                    while (connections.moveToNext()){
                        if (isCancelled()) {
                            itemsCursor.close();
                            connections.close();
                            database.close();
                            return null;
                        }
                        ItemCollectionFragment.Tag tag = TagStorage.findTagWithUID(connections.getInt(Receipt.DBTagConnectionUIDKeyIndex));
                        if (tag != null) {
                            // Required in order to obtain the correct sorting order
                            TagStorage.addTagToArray(tag, item.tags);
                        }
                    }

                    connections.close();

                    result.items.add(item);
                }

                itemsCursor.close();

                result.details = new long[6];

                Cursor detailCursor =  database.query(Receipt.DBReceiptsTable, new String[]{Receipt.DBPriceKey, Receipt.DBDateKey, Receipt.DBBudgetKey, Receipt.DBItemCountKey, Receipt.DBTaxKey, Receipt.DBReceiptNameKey},
                        Receipt.DBFilenameIdKey + "=" + target, null, null, null, null);

                if (detailCursor.getCount() != 0) {
                    detailCursor.moveToFirst();
                    result.price = detailCursor.getLong(0);
                    result.date = Calendar.getInstance();
                    result.date.setTimeInMillis(detailCursor.getLong(1) * 1000);

                    result.details[DetailCheckoutTime] = detailCursor.getLong(1) * 1000;
                    result.details[DetailItemCount] = detailCursor.getLong(3);
                    result.details[DetailBudget] = detailCursor.getLong(2);
                    result.details[DetailRemainingBudget] = detailCursor.getLong(2) - detailCursor.getLong(0);
                    result.details[DetailTax] = detailCursor.getLong(4);
                    result.details[DetailSubtotal] = new BigDecimal(result.price).movePointLeft(2)
                            .divide(new BigDecimal(10000 + result.details[DetailTax]).movePointLeft(4), RoundingMode.HALF_EVEN).setScale(2, RoundingMode.HALF_EVEN)
                            .movePointRight(2).longValue();

                    result.name = detailCursor.getString(5);
                }

                detailCursor.close();
            }

            if (DEBUG_SLOW_LOADER) {
                try {
                    Thread.sleep(SlowLoaderThreshold);
                } catch (InterruptedException e) {
                    Log.d(TAG, "Sleeping debug thread interrupted.");
                }
            }

            return result;
        }

        public void onCancelled(DataSet result) {
            UIHandler.removeCallbacks(UIRunnable);
            if (artificialTransaction) {
                artificialTransaction = false;
                if (collection != null) {
                    collection.setAnimationsEnabled(false);
                }
                controller.requestCompleteTransaction();
            }
        }

        @Override
        public void onPostExecute(DataSet result) {
            finished = true;
            UIHandler.removeCallbacks(UIRunnable);

            if (collection != null) {
                collection.setAnimationsEnabled(false);
                collection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeTop);
                collection.scrollTo(0, 0);
                collection.setAnchorCondition(null);
            }

            dataSet = result;

            controller.requestBeginNewDataSetTransaction();
            controller.addSectionForViewTypeWithTag(0, "Items").addAllObjects(result.items);
            controller.requestCompleteTransaction();
            // controller handles configuration changes and detached activities on its own

            if (activity != null) {
                setDisplayName();
                setTotal();
            }
        }
    }

    private SearchResolver activeSearch = new SearchResolver() {
        @Override
        public boolean matchesSearch(String string) {
            return false;
        }
    };

    private ViewGroup root;
    private ViewGroup viewer;
    private ViewGroup container;

    private ItemLoaderTask currentLoader;

    private HistoryViewerController controller;
    private CollectionView collection;
    private FrameLayout panelContainer;

    private TextView totalTitle;
    private TextView totalText;

    private TextView headerTitle;

    private HistoryActivity activity;

    private LegacyActionBar actionBar;

    private DataSet dataSet;
    private long currentTarget;

    private ArrayList<HistoryItem> selection = new ArrayList<HistoryItem>();
    private BigDecimal selectionTotal = new BigDecimal(0);

    private TagExpander currentExpander;
    private HistoryItem expanderTarget;

    private LinearLayout panelLayout;
    private boolean detailsUp;

    private boolean active;
    private boolean phoneUI;
    private boolean landscape;

    private boolean tagsInvalidated;

    private boolean artificialTransaction;

    private DisplayMetrics metrics;

    private ArrayList<Animator> animations = new ArrayList<Animator>();
    private ArrayList<Runnable> pendingAnimations = new ArrayList<Runnable>();
    private Handler handler = new Handler();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        controller = new HistoryViewerController();
        controller.addSectionForViewTypeWithTag(0, "Items");

        actionBar = LegacyActionBar.getAttachableLegacyActionBar();
//        actionBar.setSeparatorVisible(true);
//        actionBar.setSeparatorOpacity(0.1f);
//        actionBar.setBackgroundResource(R.color.DashboardBackground);
//        actionBar.setLogoResource(R.drawable.logo_dark);
//        actionBar.setCaretResource(R.drawable.caret_up);

//        actionBar.setLogoResource(R.drawable.back_dark);
//        actionBar.setCaretResource(R.drawable.null_drawable);
        actionBar.setBackMode(LegacyActionBarView.DoneBackMode);
        actionBar.setDoneResource(R.drawable.back_dark_centered);

        actionBar.setOverflowResource(R.drawable.ic_action_overflow);
        actionBar.setTextColor(getResources().getColor(R.color.DashboardText));

        actionBar.addItem(R.id.action_delete, getString(R.string.ItemDelete), R.drawable.ic_action_delete_dark, false, true);
        actionBar.addItem(R.id.action_show_details, getString(R.string.ShowDetails), R.drawable.ic_action_details, false, true);
        actionBar.addItem(R.id.action_share, getString(R.string.MenuShare), R.drawable.ic_action_share_mini_dark, false, true);
//        actionBar.addItem(R.id.action_copy, "[DEV] " + getString(R.string.ItemCopy), R.drawable.ic_action_copy_dark, false, false);
//        actionBar.addItem(R.id.menu_settings, getString(R.string.menu_settings), 0, false, false);
//        actionBar.addItem(R.id.menu_help, getString(R.string.menu_help), 0, false, false);

        actionBar.setOnLegacyActionSeletectedListener(this);

        getActivity().getFragmentManager().beginTransaction().add(actionBar, ActionBarKey).commit();

        setRetainInstance(true);
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        phoneUI = getResources().getConfiguration().smallestScreenWidthDp < 600;
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        metrics = getResources().getDisplayMetrics();

        activity = (HistoryActivity) getActivity();
        root = (ViewGroup) activity.getWindow().getDecorView();

        viewer = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.history_list, root, false);
        container = (ViewGroup) viewer.findViewById(R.id.ScrapWindow);

        totalText = (TextView) viewer.findViewById(R.id.total_sum);
        totalText.setTypeface(Receipt.condensedTypeface());
        totalTitle = (TextView) viewer.findViewById(R.id.text_total);
//        totalTitle.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
//        totalTitle.setPadding((int)(metrics.density * 8) + totalTitle.getPaddingLeft(), totalTitle.getPaddingTop(), totalTitle.getPaddingRight(), totalTitle.getPaddingBottom());
        totalTitle.setTypeface(Receipt.condensedTypeface());
        totalTitle.setEnabled(false);

        headerTitle = (TextView) viewer.findViewById(R.id.ScrapHeaderTitle);
        headerTitle.setTypeface(Receipt.condensedTypeface());

        if (dataSet != null) {
            setDisplayName();
            setTotal();
        }

        collection = (CollectionView) viewer.findViewById(R.id.ScrapCollection);
        collection.setController(controller);

        collection.ensureMinimumSupplyForViewType(10, 0);
        collection.setDeleteAnimationDuration(0);
        collection.setMoveAnimationDuration(200);

        collection.setAnimationsEnabled(false);

        final View TotalSeparator = viewer.findViewById(R.id.TotalSeparator);
        collection.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                if (collection.getVisibility() == View.VISIBLE) {
                    if (collection.getHeight() < collection.getChildAt(0).getHeight()) {
                        TotalSeparator.setVisibility(View.VISIBLE);
                    }
                    else {
                        TotalSeparator.setVisibility(View.INVISIBLE);
                    }
                }
            }
        });

        collection.setOnViewCollectedListener(new CollectionView.OnViewCollectedListener() {
            @Override
            public void onViewCollected(CollectionView collectionView, View view, int viewType) {
                if (viewType == 0) {
                    ((LegacyRippleDrawable) view.getBackground()).flushRipple();
                }
            }
        });

        actionBar.setContainer((ViewGroup) viewer.findViewById(R.id.ScrapWindowActionBar));
        // collection contents are never changed after the initial loading is complete

        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            PopoverDrawable background = new PopoverDrawable(getActivity(), true);
            background.setGravity(PopoverDrawable.GravityCenter);
            background.setShadowRadius(16, 8, true);
            background.setFillColor(getResources().getColor(R.color.DashboardBackground));
            container.setBackground(background);
//            viewer.setPadding(0,0,0,0);
        }

        root.addView(viewer);

        viewer.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                viewer.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                ((ViewGroup.MarginLayoutParams) viewer.getLayoutParams()).topMargin = ((ViewGroup) root.getChildAt(0)).getChildAt(0).getTop();
                if (landscape && !phoneUI) {
                    viewer.getLayoutParams().height = getResources().getDisplayMetrics().heightPixels - 2 * ((ViewGroup) root.getChildAt(0)).getChildAt(0).getTop();
                }
                viewer.requestLayout();
            }
        });

        if (!active) {
            viewer.setVisibility(View.INVISIBLE);
        }
        else {
            activity.getContent().setLayerType(View.LAYER_TYPE_HARDWARE, null);
            if (phoneUI) {
                activity.getContent().setScaleY(0.95f);
                activity.getContent().setScaleX(0.95f);
            }
            activity.getContent().setAlpha(phoneUI ? 0f : 0.4f);
            root.setBackgroundColor(phoneUI ? 0 : 0xFF000000);
        }


        if (expanderTarget != null) {
            collection.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    //noinspection deprecation
                    collection.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                    final View objectView = collection.retainViewForObject(expanderTarget);
                    currentExpander = TagExpander.fromViewInContainerWithTarget((TagView) objectView.findViewById(R.id.ItemTags), (ViewGroup) objectView, expanderTarget);
                    // TODO
                    currentExpander.setOnTagDeletedListener(HistoryViewerFragment.this);
                    currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                        @Override
                        public void onClose() {
                            currentExpander = null;
                            expanderTarget = null;
                            collection.releaseView(objectView);
                        }
                    });
                    currentExpander.expandAnimated(false);
                    currentExpander.restoreStaticContext();
                }
            });
        }

        if (detailsUp) {
            showDetails(false);
        }
    }

    class LoadInsertAnimator implements CollectionView.ReversibleAnimation {

        public void playAnimation(View view, Object object, int viewType) {
            view.setAlpha(0f);
            view.setScaleX(0.99f);
            view.setScaleY(0.99f);

            view.animate().alpha(1f).scaleX(1f).scaleY(1f);
        }
        public void resetState(View view, Object object, int viewType) { }
    }

    public void onPause() {
        super.onPause();

        flushAnimations();

        if (getActivity() != null) {
            if (!getActivity().isChangingConfigurations()) {
                if (currentExpander != null) {
                    currentExpander.destroy(true);
                }
                return;
            }
        }

        if (currentExpander != null) {
            currentExpander.saveStaticContext();
        }
    }

    public void onDetach() {
        super.onDetach();

        activity = null;
        root = null;

        viewer = null;
        container = null;

        totalText = null;
        totalTitle = null;

        headerTitle = null;

        currentExpander = null;

        collection = null;

        panelLayout = null;

    }

    public void flushAnimations() {
        while (pendingAnimations.size() > 0) {
            handler.removeCallbacks(pendingAnimations.get(0));
            pendingAnimations.get(0).run();
        }

        for (Animator animator : animations) {
            animator.end();
        }
    }

    static class ViewHolder {
        TagView tags;
        TextView name;
        TextView qty;
        TextView price;

        View searchHighlight;
    }

    public void setDisplayName() {
        SpannableStringBuilder date = new SpannableStringBuilder();

        if (TextUtils.isEmpty(dataSet.name)) {
            date.append(Integer.toString(dataSet.date.get(Calendar.DATE))).append(" ");
            date.append(dataSet.date.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault()));
            int length = date.length();
            date.append(", ").append(dataSet.date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()));

            date.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.HistoryScrapMonth)), length, date.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
//        date.setSpan(new AbsoluteSizeSpan(24, true), 0, date.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
//        date.setSpan(new Utils.CustomTypefaceSpan(Receipt.condensedTypeface()), 0, date.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        else {
            date.append(dataSet.name);
        }

        headerTitle.setText(date);
//        actionBar.setTitle(date);
//        actionBar.setLogoResource(0);
    }

    public void setTotal() {
        totalText.setText(ReceiptActivity.totalFormattedString(activity, new BigDecimal(dataSet.price).movePointLeft(2)));
    }

    class HistoryViewerController extends CollectionViewController {

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            View view =  inflater.inflate(R.layout.layout_item_window_scrap, container, false);
            ViewHolder holder = prepareViewHolder(view);

            ((DisableableFrameLayout) view.findViewById(R.id.ItemRoot)).setForwardPressedStateEnabled(false);

            //Code setting the correct layout margins
            ViewGroup.MarginLayoutParams workParams = (ViewGroup.MarginLayoutParams) holder.price.getLayoutParams();
            ViewGroup.MarginLayoutParams setParams = (ViewGroup.MarginLayoutParams) holder.qty.getLayoutParams();
            setParams.rightMargin = workParams.rightMargin + workParams.width;
            setParams = (ViewGroup.MarginLayoutParams) view.findViewById(R.id.QtyTouchHelper).getLayoutParams();
            setParams.rightMargin = workParams.rightMargin + workParams.width;

            view.setBackground(new LegacyRippleDrawable(activity));
            holder.tags.setBackground(new LegacyRippleDrawable(activity));

            holder.price.setBackground(null);
            holder.qty.setBackground(null);

//            workParams = (ViewGroup.MarginLayoutParams) holder.qty.getLayoutParams();
//            setParams = (ViewGroup.MarginLayoutParams) holder.name.getLayoutParams();
//            setParams.rightMargin = workParams.rightMargin + workParams.width;
//            workParams = (ViewGroup.MarginLayoutParams) holder.tags.getLayoutParams();
//            setParams.leftMargin = workParams.leftMargin + workParams.width;
//            setParams.leftMargin += holder.tags.getPaddingLeft() + holder.tags.getPaddingRight();
//            workParams.width += holder.tags.getPaddingLeft() + holder.tags.getPaddingRight();

            return view;
        }

        @Override
        public void configureView(View view, Object object, int viewType) {
            ViewHolder holder = (ViewHolder) view.getTag();
            HistoryItem item = (HistoryItem) object;

            ((ViewGroup) view.getParent()).setClipChildren(false);

            holder.name.setText(item.name);

            holder.qty.setText(ReceiptActivity.quantityFormattedString(view.getContext(), item.qty, item.unitOfMeasurement));
            holder.price.setText(ReceiptActivity.longToFormattedString(item.price, Receipt.titleLightSpan()));

            holder.tags.setTags(item.tags);

            if (activeSearch.matchesSearch(item.name)) {
                holder.searchHighlight.setVisibility(View.VISIBLE);
            }
            else {
                holder.searchHighlight.setVisibility(View.INVISIBLE);
            }

            view.setOnClickListener(selectionClickListener);
            view.setOnLongClickListener(selectionLongClickListener);
            if (selection.size() == 0) {
                holder.tags.setOnClickListener(tagClickListener);
                holder.tags.setEnabled(true);
            }
            else {
                holder.tags.setClickable(false);
                holder.tags.setEnabled(false);
            }

            if (view.isSelected() != item.selected && !isRefreshingViews()) {
                ((LegacyRippleDrawable) view.getBackground()).dismissPendingAnimation();
            }
            if (item.selected) {
                view.setSelected(true);
            }
            else {
                view.setSelected(false);
            }
        }
    }

    public ViewHolder prepareViewHolder(View target) {
        ViewHolder holder = new ViewHolder();
        holder.tags = (TagView) target.findViewById(R.id.ItemTags);
        holder.name = (TextView) target.findViewById(R.id.ItemTitle);
        holder.qty = (TextView) target.findViewById(R.id.QtyTitle);
        holder.price = (TextView) target.findViewById(R.id.PriceTitle);

        holder.searchHighlight = target.findViewById(R.id.SearchHighlight);

        target.setTag(holder);
        return holder;
    }

    private View.OnClickListener selectionClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            toggleSelectionForView(view);
        }
    };

    private View.OnLongClickListener selectionLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            toggleSelectionForView(view);
            return true;
        }
    };

    private View.OnClickListener tagClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View view) {
            if (currentExpander != null) {
                currentExpander.compact();
            }

            collection.retainView((View) view.getParent().getParent());
            expanderTarget = (HistoryItem) collection.getObjectForView((ViewGroup) view.getParent().getParent());
            currentExpander = TagExpander.fromViewInContainerWithTarget((TagView) view, (ViewGroup) view.getParent().getParent(), (HistoryItem) collection.getObjectForView((ViewGroup) view.getParent().getParent()));
            currentExpander.setOnTagDeletedListener(HistoryViewerFragment.this);
            currentExpander.expand();
            currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                @Override
                public void onClose() {
                    currentExpander = null;
                    expanderTarget = null;
                    collection.releaseView((View) view.getParent().getParent());
                }
            });
        }
    };


    @Override
    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        if (item.getId() == android.R.id.home) {
            collapse();
        }

        if (item.getId() == R.id.action_share) {
            ReceiptCoder coder = ReceiptCoder.sharedCoder(activity);

//            coder.shareFileFromActivity(coder.createShareableFile(currentTarget), activity);
            coder.shareFileFromAnchorInActivity(coder.createShareableFile(currentTarget), item.getActionBar().obtainAnchorForItemWithID(item.getId()), activity);
        }

        if (item.getId() == R.id.action_delete) {
            confirmDeletion();
        }

        if (item.getId() == R.id.action_show_details) {
            showDetails();
        }
    }

    public boolean handleBackPressed() {
        if (actionBar.handleBackPress()) return true;

        if (detailsUp) {
            dismissDetails();
            return true;
        }

        if (currentExpander != null) {
            currentExpander.compact();
            return true;
        }

        if (active) {
            collapse();
            return true;
        }
        return false;
    }

    public boolean handleMenuPressed() {
        if (active) {
            actionBar.showOverflow();
            return true;
        }
        return false;
    }

    public boolean isActive() {
        return active;
    }

    public void setTarget(long target) {

        actionBar.closeAllContextModesAnimated(false);

        if (currentLoader != null) {
            currentLoader.cancel(false);
        }
        currentLoader = new ItemLoaderTask();
        currentLoader.execute(target);
        active = true;
        currentTarget = target;

        controller.requestBeginNewDataSetTransaction();
        controller.addSectionForViewTypeWithTag(0, "Items");
        collection.setAnimationsEnabled(false);
        controller.requestCompleteTransaction();

        activeSearch = activity.obtainSearchResolver();

        if (detailsUp) {
            dismissDetails(false);
        }
    }

    public void onNavigationComplete() {
        if (artificialTransaction) {
            artificialTransaction = false;
            if (collection != null) {
                collection.setAnimationsEnabled(true);
                collection.setInsertAnimator(new LoadInsertAnimator());
            }

            controller.requestCompleteTransaction();
        }
    }

    public View getViewerView() {
        return viewer;
    }

    public void collapse() {
        active = false;

        if (currentExpander != null) {
            currentExpander.destroy();
        }

        actionBar.destroyAllCustomViews();
        if (panelLayout != null) {
            ScrollView detailScroller = (ScrollView) panelLayout.getChildAt(0);
            detailScroller.setVerticalScrollBarEnabled(false);
            detailScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            detailScroller.setWillNotDraw(true);
        }
        flushAnimations();
        if (activity != null) activity.collapseScrap();

        if (tagsInvalidated) {
            activity.onTagsInvalidated();
            tagsInvalidated = false;
        }

    }

    public void onGlobalAnimationEnded() {
        actionBar.closeAllContextModesAnimated(false);
    }


    // *********** SELECTION ***************

    boolean multipleSelection = false;
    public void toggleSelectionForView(View view) {

        int previousSelectionSize = selection.size();

        if (currentExpander != null) {
            currentExpander.compact();
        }

        if (tagWrapper != null) {
            LegacyActionBar.ContextBarWrapper selectionWrapperTemp = selectionWrapper;
            selectionWrapper = null;
            tagWrapper.dismiss();
            selectionWrapper = selectionWrapperTemp;
        }

        HistoryItem item = (HistoryItem) collection.getObjectForView(view);
        item.selected = !item.selected;

        if (item.selected) {
            selection.add(item);
            selectionTotal = selectionTotal.add(new BigDecimal(item.price).movePointLeft(2).multiply(new BigDecimal(item.qty > 0 ? item.qty : 10000).movePointLeft(4)));
        }
        else {
            selection.remove(item);
            selectionTotal = selectionTotal.subtract(new BigDecimal(item.price).movePointLeft(2).multiply(new BigDecimal(item.qty > 0 ? item.qty : 10000).movePointLeft(4)));
        }

        if (selection.size() > 0) {

            if (selectionWrapper == null) {
                selectionWrapper = actionBar.createContextMode(selectionListener);
                selectionWrapper.addItem(R.id.action_copy, getString(R.string.ItemCopy), R.drawable.ic_action_copy, false, true);
                selectionWrapper.setBackgroundResource(phoneUI ? R.color.SelectionBar : R.drawable.actionbar_selection_round);
                selectionWrapper.setSeparatorOpacity(0.25f);

                selectionWrapper.start();
            }

            if (selection.size() > 1) {
                if (!multipleSelection) {
                    multipleSelection = true;
                    selectionWrapper.addItemToIndex(R.id.action_edit_tags, getString(R.string.ItemEditTags), R.drawable.ic_action_edit_tags, false, true, 1);
                }
            }
            else {
                if (multipleSelection) {
                    multipleSelection = false;
                    selectionWrapper.removeItemWithId(R.id.action_edit_tags);
                }
            }

            selectionWrapper.setTitleAnimated(selection.size() + " selected", selection.size() - previousSelectionSize);
            selectionWrapper.setSubtitle(ReceiptActivity.currentLocale + selectionTotal
                    .add(selectionTotal
                            .multiply(new BigDecimal(dataSet.details[DetailTax])
                                    .movePointLeft(4)))
                    .setScale(2, RoundingMode.HALF_EVEN) + " total");
        }
        else {
            selectionWrapper.dismiss();
        }

        collection.refreshViews();
    }

    public void clearSelection() {
        for (HistoryItem item : selection) {
            item.selected = false;
        }

        selection = new ArrayList<HistoryItem>();
        collection.refreshViews();
    }

    private LegacyActionBar.ContextBarListener selectionListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {

        }

        @Override
        public void onContextBarDismissed() {
            clearSelection();
            selectionWrapper = null;
            selectionTotal = new BigDecimal(0);
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            if (item.getId() == R.id.action_copy) {
                copySelection();
            }
            if (item.getId() == R.id.action_edit_tags) {
                editTagsForSelection();
            }
        }
    };
    private LegacyActionBar.ContextBarWrapper selectionWrapper;

    static class CopySelectionTask extends AsyncTask<long[], Void, Void> {

        @Override
        protected Void doInBackground(long[] ... ids) {
            final SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();

            StringBuilder idList = new StringBuilder();
            idList.append("(");
            for (int i = 0; i < ids[0].length; i++) {
                idList.append(ids[0][i]);
                idList.append(", ");
            }
            idList.deleteCharAt(idList.length() - 2);
            idList.append(")");

            db.execSQL("insert into " + Receipt.DBPendingTable + " (name, qty, unitOfMeasurement, price, _ID) " +
                    "select name, qty, unitOfMeasurement, price, _ID from " + Receipt.DBItemsTable + "" +
                    " where _ID in " + idList.toString());

            db.close();

            return null;
        }

    }

    // *********** SELECTION COPY ***************

    public void copySelection() {

        long ids[] = new long[selection.size()];
        for (int i = 0; i < selection.size(); i++) {
            ids[i] = selection.get(i).databaseUID;
        }

        new CopySelectionTask().execute(ids);

        final FrameLayout root = (FrameLayout)activity.getWindow().getDecorView();
        final View Content = root.getChildAt(0);

        final View BlitzView = new View(activity);
        BlitzView.setBackgroundColor(0xFFFFFFFF);
        BlitzView.setClickable(true);
        BlitzView.setAlpha(0);

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(root.getWidth(), root.getHeight());

        root.addView(BlitzView, params);

        if (phoneUI) {
            Content.setVisibility(View.INVISIBLE);
        }
        else {
            Content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }

        final float ContentAlpha = Content.getAlpha();
        final TimeInterpolator accelerator = new AccelerateInterpolator(1.5f);
        final TimeInterpolator decelerator = new DecelerateInterpolator(1.5f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean deselected = false;
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                float blitzFraction;
                if (fraction < 0.5f) {
                    if (fraction < 0.16f) {
                        blitzFraction = Utils.getIntervalPercentage(fraction, 0f, 0.16f);
                        blitzFraction = accelerator.getInterpolation(blitzFraction);
                        Content.setAlpha(Utils.interpolateValues(blitzFraction, ContentAlpha, 0f));
                    } else {
                        Content.setAlpha(0f);
                        if (!deselected) {
                            deselected = true;
                            if (selectionWrapper != null) selectionWrapper.dismissInstantly();
                        }
                        blitzFraction = Utils.getIntervalPercentage(fraction, 0.16f, 0.5f);
                        blitzFraction = 1 - decelerator.getInterpolation(blitzFraction);
                    }
                    BlitzView.setAlpha(blitzFraction);
                }
                else {
                    blitzFraction = Utils.getIntervalPercentage(fraction, 0.66f, 1f);
                    blitzFraction = decelerator.getInterpolation(blitzFraction);
                    Content.setAlpha(Utils.interpolateValues(blitzFraction, 0f, ContentAlpha));
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                root.removeView(BlitzView);
                if (!phoneUI) Content.setLayerType(View.LAYER_TYPE_NONE, null);
                Content.setVisibility(View.VISIBLE);
            }
        });
        animator.setInterpolator(new LinearInterpolator());
        animator.setDuration(800);
        animator.start();

    }


    // *********** SELECTION TAG EDITING ***************

    class ProxyItem extends ItemCollectionFragment.Item {

        ArrayList<ItemCollectionFragment.Tag> allTags = new ArrayList<ItemCollectionFragment.Tag>();
        ArrayList<Integer> tagCounts = new ArrayList<Integer>();

        ArrayList<HistoryItem> targets;

        int freeTagSlots = 4;

        public void addTagToIndex(ItemCollectionFragment.Tag tag, int index) {
            super.addTagToIndex(tag, index);
            // This tag is no longer uncommon
            allTags.remove(tag);
            freeTagSlots = 4;
            for (HistoryItem item : targets) {
                item.addTag(tag);
                if (freeTagSlots > 4 - item.tags.size()) {
                    freeTagSlots = 4 - item.tags.size();
                }
            }
            if (collection != null) collection.refreshViews();
        }

        public void removeTagAtIndex(int index) {
            ItemCollectionFragment.Tag tag = tags.get(index);
            super.removeTagAtIndex(index);
            freeTagSlots = 4;
            // A color of -1 indicates uncommon tags
            if (tag.color == -1) {
                for (HistoryItem item : targets) {
                    item.removeTags(allTags);
                    if (freeTagSlots > 4 - item.tags.size()) {
                        freeTagSlots = 4 - item.tags.size();
                    }
                }
            }
            else {
                for (HistoryItem item : targets) {
                    item.removeTag(tag);
                    if (freeTagSlots > 4 - item.tags.size()) {
                        freeTagSlots = 4 - item.tags.size();
                    }
                }
            }
            if (collection != null) collection.refreshViews();
        }

        public boolean hasUncommonTags() {
            return allTags.size() > 0;
        }

        public boolean canHaveUncommonTags() {
            return true;
        }

        public boolean canAddTags() {
            return freeTagSlots > 0;
        }

    }

    protected ProxyItem createProxyItem(Context context) {
        ProxyItem proxyItem = new ProxyItem();

        ArrayList<ItemCollectionFragment.Tag> allTags = proxyItem.allTags;
        ArrayList<Integer> tagCounts = proxyItem.tagCounts;

        int index;

        proxyItem.freeTagSlots = 4;
        for (ItemCollectionFragment.Item item : selection) {
            for (ItemCollectionFragment.Tag tag : item.tags) {
                if ((index = allTags.indexOf(tag)) == -1) {
                    allTags.add(tag);
                    tagCounts.add(1);
                }
                else {
                    tagCounts.set(index, tagCounts.get(index) + 1);
                }
            }
            if (proxyItem.freeTagSlots > 4 - item.tags.size()) {
                proxyItem.freeTagSlots = 4 - item.tags.size();
            }
        }

        //Determine which tags are uncommon
        for (int i = 0, tagCountsSize = tagCounts.size(); i < tagCountsSize; i++) {
            int count = tagCounts.get(i);
            if (count == selection.size()) {
                proxyItem.tags.add(allTags.remove(i));
                tagCounts.remove(i);
                i--;
                tagCountsSize--;
            }
        }

        //If there are tags still left in the allTags array it means that there are uncommon tags
        if (allTags.size() != 0) {
            ItemCollectionFragment.Tag uncommonTag = new ItemCollectionFragment.Tag();
            uncommonTag.name = context.getResources().getString(R.string.UncommonTags);
            uncommonTag.color = -1;
            proxyItem.tags.add(0, uncommonTag);
        }

        proxyItem.targets = selection;

        return proxyItem;
    }

    private LegacyActionBar.ContextBarWrapper tagWrapper;
    private TagExpander contextExpander;
    private LegacyActionBar.ContextBarListener tagListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {
        }

        @Override
        public void onContextBarDismissed() {
            if (contextExpander != null) {
                contextExpander.compact();
            }

            if (selectionWrapper != null) {
                selectionWrapper.dismissInstantly();
            }

            tagWrapper = null;
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {}
    };

    public void editTagsForSelection() {

        tagWrapper = actionBar.createContextMode(tagListener);
        tagWrapper.setCustomView(new LegacyActionBar.CustomViewProvider() {
            boolean initial = true;

            @Override
            public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
                TagView proxy = new TagView(container.getContext());
                ItemCollectionFragment.Item proxyItem = createProxyItem(container.getContext());
                proxy.setTags(proxyItem.tags);
//                container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                contextExpander = TagExpander.fromViewInContainerWithProxyTarget(proxy, container, proxyItem);
                contextExpander.setOnTagDeletedListener(HistoryViewerFragment.this);
                contextExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                    @Override
                    public void onClose() {
                        contextExpander = null;
                    }
                });
                if (initial) {
                    contextExpander.expand();
                    initial = false;
                }
                else {
                    contextExpander.expandAnimated(false);
                    final View Container = container;
                    container.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            //noinspection deprecation
                            Container.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                            contextExpander.restoreStaticContext();
                        }
                    });
                }
                return null;
            }

            @Override
            public void onDestroyCustomView(View customView) {
            }
        });

        if (phoneUI) {
            tagWrapper.setBackgroundColor(getResources().getColor(R.color.GradientStart));
        }
        else {
            tagWrapper.setBackgroundResource(R.drawable.actionbar_background_round);
        }
        tagWrapper.setDoneResource(R.drawable.ic_action_done_dark);
        tagWrapper.setTextColor(getResources().getColor(R.color.DashboardText));
        tagWrapper.setSeparatorVisible(false);
//        tagWrapper.setSeparatorOpacity(0.25f);
        tagWrapper.setBackButtonPosition(LegacyActionBarView.BackButtonPositionRight);

        tagWrapper.start();
    }

    // ******************** DATE PICKER ******************

    private final View.OnClickListener DatePickerClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {

            final CalendarPicker Picker = new CalendarPicker();
            Picker.setDate(dataSet.date.getTimeInMillis());

            getActivity().getFragmentManager().beginTransaction().add(Picker, "CalendarPicker").commit();

            Popover datePopover = new Popover(Picker, Popover.anchorWithID(DetailIDs[0]));
            datePopover.setOnDismissListener(new Popover.OnDismissListener() {
                @Override
                public void onDismiss() {
                    Picker.detach();
                }
            });

            datePopover.show(getActivity());
        }
    };

    // ******************** DETAILS **********************

    private LegacyActionBar.ContextBarWrapper detailsBar;
    private LegacyActionBar.ContextBarListener detailsListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {}

        @Override
        public void onContextBarDismissed() {
            if (detailsUp) {
                dismissDetails(true);
            }
            detailsBar = null;
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {}
    };

    public void showDetails() {
        showDetails(true);
    }

    final static boolean USE_VIEWPROXY_ANIMATIONS = true;
    /**
     * The maximum amount of animation stride used in the detail show/hide animation.
     */
    final static int DetailSpread = 100;

    /**
     * The duration of each item's animation used in the detail show/hide animation, expressed in milliseconds.
     */
    final static int DetailAnimationDuration = 350;

    /**
     * The displacement that will be applied to items in the detail show/hide animation, expressed in milliseconds.
     */
    final static int DetailDisplacementDistanceDP = 128;

    /**
     * Creates and displays the details panel and context mode.
     * @param animated Controls whether this change will be animated.
     */
    public void showDetails(boolean animated) {

        flushAnimations();

        detailsUp = true;

        if (detailsBar == null) {
            detailsBar = actionBar.createContextMode(detailsListener);
            detailsBar.setDoneResource(R.drawable.ic_action_done_dark);
            detailsBar.setSeparatorVisible(false);
            detailsBar.setTextColor(getResources().getColor(R.color.DashboardText));

            if (phoneUI) {
                detailsBar.setBackgroundColor(getResources().getColor(R.color.GradientStart));
            }
            else {
                detailsBar.setBackgroundResource(R.drawable.actionbar_background_round);
            }

            detailsBar.start();

            detailsBar.setTitleAnimated(getString(R.string.Details), 1);
        }

        final ScrollView DetailScroller = new ScrollView(getActivity());
        DetailScroller.setClipChildren(false);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.BELOW, R.id.ScrapWindowActionBar);
        params.addRule(RelativeLayout.ABOVE, R.id.ScrapTotal);

        PanelBuilder builder = new PanelBuilder(activity)
                .setTitleSetting(DetailLabels[0], DetailLabelTypes[0], DetailIDs[0]);

        if (!phoneUI) {
            builder.setTitleWidth((int) (144 * metrics.density + 0.5f));
        }

        for (int i = 1; i < DetailCount; i++) {
            builder.addSetting(DetailLabels[i], DetailLabelTypes[i], DetailIDs[i]);
        }

        FrameLayout panel = builder.build();
        panel.setClipChildren(false);

        SpannableStringBuilder date = new SpannableStringBuilder();
        date.append(Integer.toString(dataSet.date.get(Calendar.DATE))).append(" ").append(dataSet.date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()));
        Utils.appendWithSpan(date, " " + dataSet.date.get(Calendar.YEAR), Receipt.textLightSpan());
        ((TextView) panel.findViewById(DetailIDs[0])).setText(date);
        $(panel, "#" + DetailIDs[0]).click(DatePickerClickListener);

        date = new SpannableStringBuilder();
        date.append(Integer.toString(dataSet.date.get(Calendar.HOUR_OF_DAY))).append(":").append(Integer.toString(dataSet.date.get(Calendar.MINUTE)));
        Utils.appendWithSpan(date, ":" + dataSet.date.get(Calendar.SECOND), Receipt.titleLightSpan());
        ((TextView) panel.findViewById(R.id.CheckoutTime)).setText(date);

        ((TextView) panel.findViewById(R.id.ItemCount)).setText(Long.toString(dataSet.details[DetailItemCount]));
        if (dataSet.details[DetailBudget] < Long.MAX_VALUE) {
            ((TextView) panel.findViewById(R.id.BudgetAssigned)).setText(ReceiptActivity.longToFormattedString(dataSet.details[DetailBudget], Receipt.textLightSpan()));
            ((TextView) panel.findViewById(R.id.BudgetRemaining)).setText(ReceiptActivity.longToFormattedString(dataSet.details[DetailRemainingBudget], Receipt.textLightSpan()));
        }
        else {
            ((TextView) panel.findViewById(R.id.BudgetAssigned)).setText(Utils.appendWithSpan(new SpannableStringBuilder(), getString(R.string.BudgetUnlimited), Receipt.textLightSpan()));
            ((TextView) panel.findViewById(R.id.BudgetRemaining)).setText(Utils.appendWithSpan(new SpannableStringBuilder(), getString(R.string.BudgetUnlimited), Receipt.textLightSpan()));
        }
        ((TextView) panel.findViewById(R.id.Tax)).setText(ReceiptActivity.longToFormattedString(dataSet.details[DetailTax], Receipt.textLightSpan()));
        ((TextView) panel.findViewById(R.id.Subtotal)).setText(ReceiptActivity.longToFormattedString(dataSet.details[DetailSubtotal], Receipt.textLightSpan()));

        panelLayout = new LinearLayout(activity);
        panelLayout.setGravity(Gravity.CENTER);

        panelLayout.addView(DetailScroller, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panelLayout.setClipChildren(false);

        final View TotalSeparator = viewer.findViewById(R.id.TotalSeparator);
        final View ActionBarSeparator = viewer.findViewById(R.id.ActionBarSeparator);

        DetailScroller.addView(panel);
        DetailScroller.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                if (DetailScroller.getHeight() >= ((View) DetailScroller.getParent()).getHeight()) {
                    TotalSeparator.setVisibility(View.VISIBLE);
                    ActionBarSeparator.setVisibility(View.VISIBLE);
                } else {
                    TotalSeparator.setVisibility(View.INVISIBLE);
                    ActionBarSeparator.setVisibility(View.INVISIBLE);
                }
            }
        });

        container.addView(panelLayout, params);

        if (animated && !USE_VIEWPROXY_ANIMATIONS) {
            collection.freeze();
            collection.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            collection.buildLayer();

            ((View) headerTitle.getParent()).setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ((View) headerTitle.getParent()).buildLayer();

            DetailScroller.setTranslationY(-96 * metrics.density);
            DetailScroller.setAlpha(0f);
            DetailScroller.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            DetailScroller.setVerticalScrollBarEnabled(false);
            DetailScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            DetailScroller.setWillNotDraw(true);

            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    collection.setTranslationY(Utils.interpolateValues(fraction, 0f, 128 * metrics.density));
                    collection.setAlpha(1 - fraction);

                    ((View) headerTitle.getParent()).setTranslationY(Utils.interpolateValues(fraction, 0f, 128 * metrics.density));
                    ((View) headerTitle.getParent()).setAlpha(1 - fraction);

                    DetailScroller.setAlpha(fraction);
                    DetailScroller.setTranslationY(Utils.interpolateValues(fraction, -96 * metrics.density, 0f));
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);
                    ((View) headerTitle.getParent()).setVisibility(View.INVISIBLE);
                    ((View) headerTitle.getParent()).setLayerType(View.LAYER_TYPE_NONE, null);
                    collection.setVisibility(View.INVISIBLE);
                    collection.thaw();
                    collection.setLayerType(View.LAYER_TYPE_NONE, null);

                    DetailScroller.setLayerType(View.LAYER_TYPE_NONE, null);
                    DetailScroller.setVerticalScrollBarEnabled(true);
                    DetailScroller.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                    DetailScroller.setWillNotDraw(false);

                    if (currentExpander != null) {
                        currentExpander.destroy();
                    }
                }
            });
            animator.setDuration(300);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            pendingAnimations.add(new Runnable() {
                @Override
                public void run() {
                    pendingAnimations.remove(this);

                    animator.start();
                    animations.add(animator);
                }
            });
            handler.post(pendingAnimations.get(pendingAnimations.size() - 1));
        }
        else if (animated && USE_VIEWPROXY_ANIMATIONS) {

            $.bind(activity);

            $.finishQueue("detailsQueue", true);

            ((ViewGroup) collection.getChildAt(0)).setClipChildren(false);
            $ outgoing = $(R.id.ScrapHeader).add(collection.visibleViews()).reverse();

            outgoing.animate()
                        .property($.TranslateY, $.dp(DetailDisplacementDistanceDP), $.Op.Add)
                        .property($.Opacity, 0)
                        .layer(true)
                        .duration(DetailAnimationDuration)
                        .interpolator(new Utils.FrictionInterpolator(1.5f))
                        .stride(DetailSpread / outgoing.length())
                        .complete(new $.AnimationCallback() {
                            @Override
                            public void run($ collection) {
                                HistoryViewerFragment.this.collection.setVisibility(View.INVISIBLE);
                                $(R.id.ScrapHeader).visibility($.Invisible);
                                collection.property($.Opacity, 1).property($.TranslateY, 0);
                            }
                        })
                    .start("detailsQueue");

            final $ Incoming = $(DetailScroller.getChildAt(0)).children();
            Incoming.each(new $.Each() {
                int i = 0;
                int row = Incoming.length();
                int stride = DetailSpread / Incoming.length();

                @Override
                public void run(View view, int index) {
                    $ wrapper = $(view);

                    wrapper.property($.Opacity, 0)
                            .property($.TranslateY, $.dp(-DetailDisplacementDistanceDP), $.Op.Add)
                            .animate()
                                .property($.Opacity, 1)
                                .property($.TranslateY, $.dp(DetailDisplacementDistanceDP), $.Op.Add)
                                .layer(true)
                                .duration(DetailAnimationDuration)
                                .delay(stride * row)
                                .interpolator(new Utils.FrictionInterpolator(1.5f))
                            .start("detailsQueue");

                    i++;
                    if (i == 2) {
                        i = 0;
                        row --;
                    }
                }
            });

            $.unbind();

        }
        else {
            collection.setVisibility(View.INVISIBLE);
            ((View) headerTitle.getParent()).setVisibility(View.INVISIBLE);
        }

    }

    public void dismissDetails() {
        dismissDetails(true);
    }

    /**
     * Dismisses and then destroys the details panel.
     * @param animated Controls whether this change is animated.
     */
    public void dismissDetails(boolean animated) {

        flushAnimations();
        if (USE_VIEWPROXY_ANIMATIONS) $.finishQueue("detailsQueue", true);

        detailsUp = false;

        ScrollView detailScroller = (ScrollView) panelLayout.getChildAt(0);
        detailScroller.setVerticalScrollBarEnabled(false);
        detailScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        detailScroller.setWillNotDraw(true);

        final LinearLayout PanelLayout = panelLayout;
        panelLayout = null;

        ((View) headerTitle.getParent()).setVisibility(View.VISIBLE);
        collection.setVisibility(View.VISIBLE);

        final Runnable CleanupRunnable = new Runnable() {
            @Override
            public void run() {
                viewer.findViewById(R.id.ActionBarSeparator).setVisibility(View.INVISIBLE);

                if (collection.getHeight() < collection.getChildAt(0).getHeight()) {
                    viewer.findViewById(R.id.TotalSeparator).setVisibility(View.VISIBLE);
                }
                else {
                    viewer.findViewById(R.id.TotalSeparator).setVisibility(View.INVISIBLE);
                }

                container.removeView(PanelLayout);
                ((View) headerTitle.getParent()).setAlpha(1f);
                ((View) headerTitle.getParent()).setTranslationY(0f);

                collection.setAlpha(1f);
                collection.setTranslationY(0f);
            }
        };

        if (animated && !USE_VIEWPROXY_ANIMATIONS) {

            collection.freeze();
            collection.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            collection.buildLayer();

            ((View) headerTitle.getParent()).setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ((View) headerTitle.getParent()).buildLayer();

            PanelLayout.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            PanelLayout.buildLayer();

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    collection.setTranslationY(Utils.interpolateValues(fraction, 128 * metrics.density, 0f));
                    collection.setAlpha(fraction);

                    ((View) headerTitle.getParent()).setTranslationY(Utils.interpolateValues(fraction, 128 * metrics.density, 0f));
                    ((View) headerTitle.getParent()).setAlpha(fraction);

                    PanelLayout.setAlpha(1 - fraction);
                    PanelLayout.setTranslationY(Utils.interpolateValues(fraction, 0f, - 96 * metrics.density));
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);
                    ((View) headerTitle.getParent()).setLayerType(View.LAYER_TYPE_NONE, null);
                    collection.thaw();
                    collection.setLayerType(View.LAYER_TYPE_NONE, null);

                    CleanupRunnable.run();
                }
            });
            animator.setDuration(300);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            animations.add(animator);
            animator.start();
        }
        else if (animated && USE_VIEWPROXY_ANIMATIONS) {

            $.bind(activity);

            ((ViewGroup) collection.getChildAt(0)).setClipChildren(false);
            $ outgoing = $(R.id.ScrapHeader).add(collection.visibleViews());

            outgoing.animate()
                        .property($.TranslateY, $.dp(DetailDisplacementDistanceDP), 0)
                        .property($.Opacity, 0, 1)
                        .layer(true)
                        .duration(DetailAnimationDuration)
                        .forcefeed()
                        .interpolator(new Utils.FrictionInterpolator(1.5f))
                        .delay(50)
                    .stride(DetailSpread / outgoing.length())
                        .complete(new $.AnimationCallback() {
                            @Override
                            public void run($ collection) {
//                                HistoryViewerFragment.this.collection.setVisibility(View.INVISIBLE);
//                                $(R.id.ScrapHeader).visibility($.Invisible);
//                                collection.property($.Opacity, 1).property($.TranslateY, 0);

                                CleanupRunnable.run();
                            }
                        })
                    .start("detailsQueue");

            final $ Incoming = $(detailScroller.getChildAt(0)).children().reverse();
            Incoming.each(new $.Each() {
                int i = 0;
                int row = Incoming.length();
                int stride = DetailSpread / Incoming.length();

                @Override
                public void run(View view, int index) {
                    $ wrapper = $(view);

                    wrapper
                            .animate()
                            .property($.Opacity, 0)
                            .property($.TranslateY, $.dp(-DetailDisplacementDistanceDP), $.Op.Add)
                            .layer(true)
                            .duration(DetailAnimationDuration)
                            .delay(stride * row)
                            .interpolator(new Utils.FrictionInterpolator(1.5f))
                        .start("detailsQueue");

                    i++;
                    if (i == 2) {
                        i = 0;
                        row --;
                    }
                }
            });

            $.unbind();

        }
        else {
            CleanupRunnable.run();
        }
    }

    // ******************** DELETE *****************
    private LegacyActionBar.ContextBarWrapper deleteWrapper;
    private LegacyActionBar.ContextBarListener deleteListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {}

        @Override
        public void onContextBarDismissed() {
            deleteWrapper = null;
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            if (item.getId() == R.id.ConfirmOK) {
                deleteCurrentReceipt();
            }
            if (item.getId() == R.id.ConfirmCancel) {
                deleteWrapper.dismiss();
            }
        }
    };

    public void confirmDeletion() {
        deleteWrapper = actionBar.createActionConfirmationContextMode(getString(R.string.ConfirmScrapTitle), getString(R.string.ActionDelete), R.drawable.ic_action_delete, deleteListener);
        deleteWrapper.setBackgroundResource(phoneUI ? android.R.color.holo_orange_dark : R.drawable.actionbar_confirmation_round);
        deleteWrapper.start();
    }

    public long getCurrentTarget() {
        return currentTarget;
    }

    public void deleteCurrentReceipt() {
        active = false;

        if (currentExpander != null) {
            currentExpander.destroy();
        }

        actionBar.destroyAllCustomViews();
        if (panelLayout != null) {
            ScrollView detailScroller = (ScrollView) panelLayout.getChildAt(0);
            detailScroller.setVerticalScrollBarEnabled(false);
            detailScroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
            detailScroller.setWillNotDraw(true);
        }
        flushAnimations();

        if (activity != null) activity.deleteScrap();

        if (tagsInvalidated) {
            activity.onTagsInvalidated();
            tagsInvalidated = false;
        }

    }

    @Override
    public void onTagDeleted(ItemCollectionFragment.Tag tag) {
        activity.onTagDeleted(tag);

        ReceiptActivity.invalidatedTags.add(tag);

        if (dataSet != null) for (HistoryItem item : dataSet.items) {
            item.removeTag(tag);
        }
    }

}
