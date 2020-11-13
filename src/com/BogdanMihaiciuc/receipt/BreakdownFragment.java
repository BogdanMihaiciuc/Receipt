package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.CycleInterpolator;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.PieChartView;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.TagView;
import com.BogdanMihaiciuc.util.TooltipPopover;
import com.BogdanMihaiciuc.util.Utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;

import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;
import static com.BogdanMihaiciuc.receipt.StatsFragment.Precision;

public class BreakdownFragment extends Fragment {

    final static String TAG = BreakdownFragment.class.toString();

    final static boolean DEBUG_SLOW_LOAD = false;
    final static boolean NewLoad = false;
    final static boolean NewIntro = true;

    final static String BreakdownFragmentKey = "breakdownFragment";

    final static Tag UncategorizedTag = new Tag();

    final static long LoadingLeniencyDelay = 400;

    final static long CommonDuration = 500;

    static {
        UncategorizedTag.color = -1;
        UncategorizedTag.name = "Uncategorized"; //TODO
        UncategorizedTag.tagUID = Integer.MIN_VALUE;
    }

    private Runnable loaderRunnable = new Runnable() {
        @Override
        public void run() {
            //TODO ?
        }
    };

    class BreakdownLoader extends AsyncTask {

        private Map<ItemCollectionFragment.Tag, BreakdownItem> pendingData = new IdentityHashMap<ItemCollectionFragment.Tag, BreakdownItem>();
        private long unixDate;
        private Precision precision;

        BreakdownLoader(long unixDate, Precision precision) {
//            new Throwable().printStackTrace();
            this.unixDate = unixDate / 1000;
            this.precision = statsFragment.getPrecisionData();
        }

        protected void onPreExecute() {
            handler.postDelayed(loaderRunnable, LoadingLeniencyDelay);
        }

        @Override
        protected Object doInBackground(Object[] objects) {
            synchronized (Receipt.DatabaseLock) {
                if (DEBUG_SLOW_LOAD) {
                    try {
                        Thread.sleep(2000);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                SQLiteDatabase database = Receipt.DBHelper.getReadableDatabase();

                Cursor receipts = database.query(Receipt.DBReceiptsTable, new String[] {Receipt.DBFilenameIdKey, Receipt.DBTaxKey},
                        precision.getGrouper(Receipt.DBDateKey) + " = " +
                        precision.getGrouper(String.valueOf(unixDate)), null,
                        null, null, null);

                while (receipts.moveToNext()) {
                    if (isCancelled()) {
                        receipts.close();
                        database.close();
                        return null;
                    }

                    long tax = receipts.getInt(1);

                    Cursor items = database.query(Receipt.DBItemsTable, new String[] {Receipt.DBPriceKey, Receipt.DBQtyKey, Receipt.DBItemUIDKey},
                            Receipt.DBTargetDBKey + " = " + receipts.getLong(0), null, null, null, null);

                    while (items.moveToNext()) {
                        if (isCancelled()) {
                            receipts.close();
                            items.close();
                            database.close();
                            return null;
                        }

                        Cursor tagConnections = database.query(Receipt.DBTagConnectionsTable, Receipt.DBAllTagConnectionColumns,
                                Receipt.DBItemConnectionUIDKey + " = " + items.getLong(2), null, null, null, null);

                        if (tagConnections.getCount() > 0) {
                            while (tagConnections.moveToNext()) {
                                if (isCancelled()) {
                                    receipts.close();
                                    items.close();
                                    tagConnections.close();
                                    database.close();
                                    return null;
                                }

                                long qty = items.getLong(1);
                                if (qty == 0) qty = 10000;

                                Tag tag = TagStorage.findTagWithUID(tagConnections.getInt(Receipt.DBTagConnectionUIDKeyIndex));
                                if (tag != null) {
                                    if (pendingData.containsKey(tag)) {
                                        BreakdownItem item = pendingData.get(tag);

                                        if (tax == 0)
                                            item.bigAmount = item.bigAmount.add(new BigDecimal(items.getLong(0)).movePointLeft(2).multiply(new BigDecimal(qty).movePointLeft(4)));
                                        else
                                            item.bigAmount = item.bigAmount.add(
                                                    new BigDecimal(items.getLong(0)).movePointLeft(2)
                                                            .multiply(new BigDecimal(qty).movePointLeft(4))
                                                            .multiply(new BigDecimal(tax + 10000).movePointLeft(4))
                                            );
                                    }
                                    else {
                                        pendingData.put(tag, new BreakdownItem());
                                        BreakdownItem item = pendingData.get(tag);
                                        item.title = tag.name;
                                        item.color = tag.color;
                                        item.tag = tag;

                                        if (tax == 0)
                                            item.bigAmount = item.bigAmount.add(new BigDecimal(items.getLong(0)).movePointLeft(2).multiply(new BigDecimal(qty).movePointLeft(4)));
                                        else
                                            item.bigAmount = item.bigAmount.add(
                                                    new BigDecimal(items.getLong(0)).movePointLeft(2)
                                                            .multiply(new BigDecimal(qty).movePointLeft(4))
                                                            .multiply(new BigDecimal(tax + 10000).movePointLeft(4))
                                            );
                                    }
                                }

                            }
                        }
                        else {

                            long qty = items.getLong(1);
                            if (qty == 0) qty = 10000;

                            Tag tag = UncategorizedTag;
                            if (pendingData.containsKey(tag)) {
                                BreakdownItem item = pendingData.get(tag);

                                if (tax == 0)
                                    item.bigAmount = item.bigAmount.add(new BigDecimal(items.getLong(0)).movePointLeft(2).multiply(new BigDecimal(qty).movePointLeft(4)));
                                else
                                    item.bigAmount = item.bigAmount.add(
                                            new BigDecimal(items.getLong(0)).movePointLeft(2)
                                                    .multiply(new BigDecimal(qty).movePointLeft(4))
                                                    .multiply(new BigDecimal(tax + 10000).movePointLeft(4))
                                    );
                            }
                            else {
                                pendingData.put(tag, new BreakdownItem());
                                BreakdownItem item = pendingData.get(tag);
                                item.title = tag.name;
                                item.color = tag.color;
                                item.tag = tag;

                                if (tax == 0)
                                    item.bigAmount = item.bigAmount.add(new BigDecimal(items.getLong(0)).movePointLeft(2).multiply(new BigDecimal(qty).movePointLeft(4)));
                                else
                                    item.bigAmount = item.bigAmount.add(
                                            new BigDecimal(items.getLong(0)).movePointLeft(2)
                                                    .multiply(new BigDecimal(qty).movePointLeft(4))
                                                    .multiply(new BigDecimal(tax + 10000).movePointLeft(4))
                                    );
                            }

                        }

                        tagConnections.close();

                    }

                    items.close();

                }

                receipts.close();

                database.close();
            }
            return null;
        }

        protected void onPostExecute(Object result) {
            handler.removeCallbacks(loaderRunnable);

            if (currentLoader == this) {
                currentLoader = null;
            }

            items.clear();

            if (pie != null) {
                pie.startTransaction();
                pie.clear();
            }

            controller.requestBeginTransaction();
            controller.getSectionAtIndex(1).clear();
            for (Map.Entry<Tag, BreakdownItem> entry : pendingData.entrySet()) {
                items.add(entry.getValue());
                if (pie != null) pie.addColorWithAmount(entry.getValue().color, entry.getValue().bigAmount.longValue());
            }
            controller.getSectionAtIndex(1).addAllObjects(items);
            controller.getSectionAtIndex(1).sortWithComparator(new Comparator<BreakdownItem>() {
                @Override
                public int compare(BreakdownItem o, BreakdownItem o2) {
                    return -o.bigAmount.compareTo(o2.bigAmount);
                }
            });

            boolean animated = activity != null && activity.getCurrentNavigationIndex() == activity.getStatsNavigationIndex();

            if (pie != null) pie.endTransactionAnimated(animated);

            if (breakdownList != null) {
                if (initial) {
                    initial = false;
                }
                else breakdownList.setAnimationsEnabled(animated);
            }
            controller.requestCompleteTransaction();
            if (breakdownList != null) breakdownList.refreshViews();

            selectedInterval = unixDate * 1000;

            if (activity != null) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(selectedInterval);

                if (phoneUI) ((TextView) ((View) breakdownRoot.getParent()).findViewById(R.id.text_total)).setText(precision.sectionSubtitle.getTitle(activity, c));
                else ((TextView)breakdownRoot.findViewById(R.id.BreakdownTitle)).setText(precision.sectionTitle.getTitle(activity, c));
            }

        }

    }

    private HistoryActivity activity;
    private DisplayMetrics metrics;
    private Resources resources;

    private ViewGroup actionBarRoot;
    private ViewGroup root;

    private RelativeLayout breakdownRoot;
    private View listBackground;
    private CollectionView breakdownList;
    private PieChartView pie;

    private Handler handler = new Handler();

    private BreakdownLoader currentLoader;

    private boolean phoneUI;
    private boolean landscape;

    private boolean initial = true;

    private Precision precision;
    private ArrayList<BreakdownItem> items = new ArrayList<BreakdownItem>();

    private long selectedInterval = Long.MIN_VALUE;

    private StatsFragment statsFragment;

    private ArrayList<Runnable> animationStack;

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);

//        controller.requestBeginTransaction();
        controller.addSectionForViewTypeWithTag(0, null);
        controller.getSectionAtIndex(0).addObject(new Object());
        controller.addSectionForViewTypeWithTag(1, null);
//        controller.requestCompleteTransaction();

        controller.setComparator(new CollectionViewController.ObjectComparator() {
            @Override
            public boolean areObjectsEqual(Object object1, Object object2) {
                if ((object1.getClass() == object2.getClass()) && (object1.getClass() == Object.class)) {
                    return true;
                }
                if (object1.getClass() != object2.getClass()) return false;
                return ((BreakdownItem) object1).color == ((BreakdownItem) object2).color;
            }
        });

    }

    public void onAttach(Activity activity) {
        super.onAttach(activity);
        this.activity = (HistoryActivity) activity;
        metrics = getResources().getDisplayMetrics();
        resources = getResources();

        phoneUI = resources.getConfiguration().smallestScreenWidthDp < 600;
        landscape = resources.getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
//        if (!phoneUI) landscape = !landscape;

        root = (ViewGroup) activity.getWindow().getDecorView();
        actionBarRoot = (ViewGroup) (activity.findViewById(R.id.LegacyActionBar));
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        breakdownRoot = (RelativeLayout) inflater.inflate(R.layout.breakdown, null);
        breakdownRoot.setClickable(true);

        listBackground = breakdownRoot.findViewById(R.id.ListBackground);
        breakdownList = (CollectionView) breakdownRoot.findViewById(R.id.BreakdownList);
        breakdownList.setGravity(Gravity.CENTER);
        breakdownList.setRefreshViewsOnTransactionEnabled(true);
        breakdownList.setController(controller);
        breakdownList.setMoveInterpolator(new Utils.FrictionInterpolator(1.33f));
        breakdownList.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
                if (((ViewGroup) view).getChildAt(0).getHeight() > view.getHeight()) {
                    activity.findViewById(R.id.TotalSeparator).setVisibility(View.VISIBLE);
                }
                else {
                    activity.findViewById(R.id.TotalSeparator).setVisibility(View.INVISIBLE);
                }
            }
        });
        breakdownList.setInsertAnimator(new CollectionView.ReversibleAnimation() {
            @Override
            public void playAnimation(View view, Object object, int viewType) {
                view.setScaleY(1.01f);
                view.setScaleX(1.01f);
                view.setAlpha(0f);
                view.animate().alpha(1f).scaleX(1f).scaleY(1f);
            }

            @Override
            public void resetState(View view, Object object, int viewType) {

            }
        });

        pie = (PieChartView) breakdownRoot.findViewById(R.id.Pie);
//        pie.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//        pie.setClearModeEnabled(true);
        pie.setEraserColor(getResources().getColor(R.color.GradientStart));
        pie.setSlicesSelectable(false);

        pie.startTransaction();
        for (BreakdownItem item : items) {
            pie.addColorWithAmount(item.color, item.bigAmount.longValue());
        }
        pie.endTransactionAnimated(false);

        breakdownRoot.setBackgroundColor(getResources().getColor(R.color.GradientStart));
        ((View) pie.getParent()).setBackgroundColor(0);
        listBackground.setBackgroundColor(0);

        return breakdownRoot;
    }

    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (statsFragment == null) {
            statsFragment = (StatsFragment) activity.getFragmentManager().findFragmentById(R.id.StatsFragment);
        }

        boolean correctTab = activity.getCurrentNavigationIndex() == activity.getStatsNavigationIndex();

        if (!resumed && correctTab) {
            pie.setCompletion(0f);
            pie.setAlpha(0f);

            animationStack = new ArrayList<Runnable>();

            animationStack.add(new Runnable() {
                public void run() {
                    if (pie != null) {
                        final PieChartView Pie = pie;
                        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                            @Override
                            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                                Pie.setCompletion(valueAnimator.getAnimatedFraction());
                                Pie.setAlpha(valueAnimator.getAnimatedFraction());
                            }
                        });
                        animator.setDuration(800);
                        animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
                        animator.start();
                    }
                }
            });
        }

        if (!phoneUI) {
            ((TextView)breakdownRoot.findViewById(R.id.BreakdownTitle)).setTypeface(Receipt.condensedTypeface());
            if (selectedInterval != Long.MIN_VALUE && precision != null) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(selectedInterval);

                ((TextView)breakdownRoot.findViewById(R.id.BreakdownTitle)).setText(precision.sectionTitle.getTitle(activity, c));
            }
        }
        else {
            if (selectedInterval != Long.MIN_VALUE && precision != null) {
                Calendar c = Calendar.getInstance();
                c.setTimeInMillis(selectedInterval);

                ((TextView)((View) breakdownRoot.getParent()).findViewById(R.id.text_total)).setText(precision.sectionSubtitle.getTitle(activity, c));
            }
        }

        if (phoneUI && landscape) {
            breakdownList.setOnScrollListener(new CollectionView.OnScrollListener() {
                @Override
                public void onScroll(CollectionView collectionView, int top, int amount) {
                    pie.setTranslationY(- top);
                }
            });
        }

    }

    final static int ResumeAnimationDelay = 500;
    private boolean resumed;

    @Override
    public void onResume() {
        super.onResume();

        int resumeAnimationDelay = phoneUI ? ResumeAnimationDelay + 100 : ResumeAnimationDelay;

        if (animationStack != null)
            handler.postDelayed( new Runnable() {
                public void run() {
                    resumed = true;

                    // If there were pending animation runnables before the fragment has been resumed
                    // run them now!
                    if (activity == null) {
                        // If detached from activity in the meantime, clear the animation stack
                        animationStack = null;
                        return;
                    }
                    if (animationStack != null) {
                        for (Runnable r : animationStack) {
                            r.run();
                        }
                        animationStack = null;
                    }
                }
            }, resumeAnimationDelay);
    }

    public void onDetach() {
        super.onDetach();
        activity = null;
        resources = null;

        root = null;
        actionBarRoot = null;

        breakdownRoot = null;
        pie = null;
        listBackground = null;
        breakdownList = null;

        animationStack = null;
    }

    public void selectItem(StatsFragment.StatItem item) {
        if (currentLoader != null) {
            currentLoader.cancel(false);
        }
        currentLoader = new BreakdownLoader(item.unixDate * 1000, precision);
        //noinspection unchecked
        currentLoader.execute();
    }

    public void deselect() {
        if (currentLoader != null) {
            currentLoader.cancel(false);
        }

        items.clear();

        if (pie != null) {
            pie.startTransaction();
            pie.clear();
        }

        controller.requestBeginTransaction();
        controller.getSectionAtIndex(1).clear();

        boolean animated = activity.getCurrentNavigationIndex() == activity.getStatsNavigationIndex();

        if (pie != null) {
            pie.endTransactionAnimated(animated);
        }
        else breakdownList.setAnimationsEnabled(animated);
        controller.requestCompleteTransaction();

        if (activity != null) {
            if (phoneUI) ((TextView)((View) breakdownRoot.getParent()).findViewById(R.id.text_total)).setText("");
            else ((TextView)breakdownRoot.findViewById(R.id.BreakdownTitle)).setText("");
        }
    }

    public void onOverlayRemoved(int tagUID) {
        if (activity == null) return;

        BreakdownItem removedTag = null;
        for (BreakdownItem item : items) {
            if (item.tag.tagUID == tagUID) {
                removedTag = item;
                break;
            }
        }

        if (removedTag == null) return;

        final View BreakdownView = breakdownList.getViewForObject(removedTag);

        if (BreakdownView == null) return;

        final TagView Tag = (TagView) BreakdownView.findViewById(R.id.ItemTags);
        breakdownList.retainView(BreakdownView);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                Tag.setPlusOpacity(1 - fraction);
                Tag.setRotation(Utils.interpolateValues(fraction, 45, 0));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Tag.setPlusEnabled(false);
                if (breakdownList != null) {
                    breakdownList.releaseView(BreakdownView);
                }
            }
        });
        animator.setDuration(200);
        animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        animator.start();
    }

    public void onOverlayAdded(int tagUID) {
        if (activity == null) return;

        BreakdownItem removedTag = null;
        for (BreakdownItem item : items) {
            if (item.tag.tagUID == tagUID) {
                removedTag = item;
                break;
            }
        }

        if (removedTag == null) return;

        final View BreakdownView = breakdownList.getViewForObject(removedTag);

        if (BreakdownView == null) return;

        final TagView Tag = ((TagView) BreakdownView.findViewById(R.id.ItemTags));
        Tag.setPlusEnabled(true);
        Tag.setPlusOpacity(0f);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        breakdownList.retainView(BreakdownView);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                Tag.setPlusOpacity(fraction);
                Tag.setRotation(45 * fraction);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                Tag.setPlusEnabled(true);
                if (breakdownList != null) {
                    breakdownList.releaseView(BreakdownView);
                }
            }
        });
        animator.setDuration(200);
        animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        animator.start();
    }

    static class BreakdownItem {
        Tag tag;
        BigDecimal bigAmount = new BigDecimal(0);

        int color;
        String title;

        public String toString() {
            return tag.name;
        }
    }

    class BreakdownCollectionController extends CollectionViewController {

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            if (viewType == 0) {
                View view = new View(container.getContext());
                if (phoneUI && landscape) {
                    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, getResources().getDimensionPixelSize(R.dimen.BreakdownPieSize));
                    view.setLayoutParams(params);
                }
                else {
                    ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
                    view.setLayoutParams(params);
                }
                return view;
            }
            if (viewType == 1) {
                View convertView = inflater.inflate(R.layout.breakdown_item, container, false);
                convertView.setBackground(Utils.getDeselectedColors(activity));
                ((TagView) convertView.findViewById(R.id.ItemTags)).setDashedCircleEnabled(false);
                if (landscape)
                    convertView.findViewById(R.id.PriceTitle).getLayoutParams().width = (int) (100 * metrics.density);
                return convertView;
            }
            return null;
        }

        @Override
        public void configureView(View view, Object object, int viewType) {
            if (viewType == 1) {
                final BreakdownItem Item = (BreakdownItem) object;

                ((TextView) view.findViewById(R.id.ItemTitle)).setText(Item.title);
                ((TextView) view.findViewById(R.id.PriceTitle)).setText(ReceiptActivity.bigDecimalToFormattedString(Item.bigAmount, Receipt.textLightSpan()));
                TagView tag = ((TagView) view.findViewById(R.id.ItemTags));
                tag.setColor(Item.color);
                if (statsFragment.hasOverlay(Item.tag.tagUID)) {
                    tag.setPlusEnabled(true);
                    tag.setRotation(45);
                    tag.setPlusOpacity(1f);
                }
                else {
                    tag.setPlusEnabled(false);
                    tag.setRotation(0);
                    tag.setPlusOpacity(0f);
                }

                final int UID = Item.tag.tagUID;
                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (statsFragment.hasOverlay(UID)) {
                            statsFragment.removeOverlay(UID);
                        }
                        else {
                            if (!statsFragment.canAddOverlays() || UID == UncategorizedTag.tagUID) {
                                view.animate().cancel();
                                view.setTranslationX(0f);

                                view.animate().translationX(metrics.density * 5)
                                        .setDuration(300)
                                        .setInterpolator(new CycleInterpolator(2));

                                return;
                            }
                            statsFragment.addOverlayForTag(UID);
                        }
                    }
                });
                if (phoneUI) {
                    view.setOnLongClickListener(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
//                            Rect rect = new Rect();
//                            v.getGlobalVisibleRect(rect);
//                            Toast toast = Toast.makeText(getActivity(), Item.tag.name, Toast.LENGTH_SHORT);
//                            toast.setGravity(Gravity.TOP|Gravity.LEFT, rect.left, rect.top + rect.height()/2);
//                            toast.show();
                            TooltipPopover popover = new TooltipPopover(Item.tag.name, null, new Popover.AnchorProvider() {
                                @Override
                                public View getAnchor(Popover popover) {
                                    return getCollectionView().getViewForObject(Item);
                                }
                            });
                            if (Item.tag.color != -1) {
                                popover.setBackgroundColor(Item.tag.color);
                                popover.setTextColor(TagStorage.getSuggestedTextColor(Item.tag.color));
                            }
                            else {
                                popover.setBackgroundColor(0xBBFFFFFF);
                                popover.setTextColor(Utils.transparentColor(1f, 0));
                            }
                            popover.requestGravity(Popover.GravityLeftOf);
                            popover.show(getActivity());
                            return true;
                        }
                    });
                }
            }
        }
    }

    private BreakdownCollectionController controller = new BreakdownCollectionController();

}
