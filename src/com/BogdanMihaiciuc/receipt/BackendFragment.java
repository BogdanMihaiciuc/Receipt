package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.receipt.BackendStorage.AbstractReceipt;
import com.BogdanMihaiciuc.receipt.HelpStory.OnCloseListener;
import com.BogdanMihaiciuc.receipt.HelpStory.OnSelectPageListener;
import com.BogdanMihaiciuc.util.$;
import com.BogdanMihaiciuc.util.BetaUtilities;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.DisableableFrameLayout;
import com.BogdanMihaiciuc.util.DisableableView;
import com.BogdanMihaiciuc.util.EventTouchListener;
import com.BogdanMihaiciuc.util.ExtendedFragment;
import com.BogdanMihaiciuc.util.FloatingActionButton;
import com.BogdanMihaiciuc.util.Glyph;
import com.BogdanMihaiciuc.util.IntentListPopover;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.MessagePopover;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.SettingsPopover;
import com.BogdanMihaiciuc.util.SwipeToDeleteListener;
import com.BogdanMihaiciuc.util.TooltipPopover;
import com.BogdanMihaiciuc.util.Utils;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Item;
import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;
import static com.BogdanMihaiciuc.util.SwipeToDeleteListener.OnMoveListener;

public class BackendFragment extends ExtendedFragment implements BackendStorage.OnLoadProgressListener, LegacyActionBar.OnLegacyActionSelectedListener, Utils.BackStack {

     // *********** BACKSTACK ****************
    private ArrayList<Runnable> backStack = new ArrayList<Runnable>();

    private final Runnable BackDisabler = new Runnable() {
        @Override
        public void run() {
            pushToBackStack(this);
        }
    };

    public void pushToBackStack(Runnable r) {
        backStack.add(r);
    }

    public void disableBackButton() {
        pushToBackStack(BackDisabler);
    }

    public void enableBackButton() {
        for (int i = backStack.size() - 1; i >= 0; i--) {
            if (backStack.get(i) == BackDisabler) {
                backStack.remove(i);
                return;
            }
        }
    }

    public boolean canPopBackStack() {
        return backStack.size() > 0;
    }

    public boolean popBackStack() {

        if (backStack.size() > 0) {
            backStack.remove(backStack.size() - 1).run();
            return true;
        }

        activity.onBackPressed();
        return false;
    }

    @Override
    public Utils.BackStack persistentBackStack() {
        return this;
    }

    public void popBackStackFrom(Runnable r) {

        int insertionPoint = backStack.indexOf(r);
        if (insertionPoint != -1) {
            for (; insertionPoint < backStack.size(); ) {
                backStack.remove(insertionPoint);
            }
        }
    }

    public void rewindBackStackFrom(Runnable r) {

        int insertionPoint = backStack.indexOf(r);
        if (insertionPoint != -1) {
            for (; insertionPoint < backStack.size(); ) {
                backStack.remove(insertionPoint).run();
            }
        }
    }

    public void swipeFromBackStack(Runnable r) {

        backStack.remove(r);
    }

    public int backStackSize() {
        return backStack.size();
    }

    // *********** BACKSTACK ****************

    final static String TAG = "BackendFragment";

    final static boolean DEBUG = false;
    final static boolean DEBUG_ERRANT_TOUCHES = false;
    final static boolean DEBUG_SCRAP_TARGETS = false;
    final static boolean DEBUG_BUDGET = false;
    final static boolean DEBUG_SIDEBAR = true;
    final static boolean USE_ALIGN_FAB = false;
    final static boolean USE_RESET_PHONE_STATE = false;
    final static boolean USE_OLD_ANIMATIONS = false;

    final static String ExitStateKey = "exitState";
    final static int StateNotSet = -1;
    final static int StateOpenList = 0;
    final static int StateBackend = 1;

    final static int ViewTypeScrap = 0;
    final static int ViewTypeDashboardPlaceholder = 1;
    final static int ViewTypeHeader = 2;
    final static int ViewTypeFutureScrap = 3;

    final static String OpenedFileKey = "openedFile";

    final static int BudgetResetTypeMonthly = 0;
    final static int BudgetResetTypeWeekly = 1;
    final static int BudgetResetTypeManually = 2;
    final static int BudgetResetTypeTwicePerMonth = 3;
    final static int MonthlySubtypeOnDate = 0;
    final static int MonthlySubtypeFirstDay = 1;
    final static int MonthlySubtypeLastDay = 2;
    final static int BudgetResetSubtypeInapplicable = -1;

    final static String GlobalBudgetKey = "globalBudget";
    final static String CheckedOutBudgetKey = "checkedOutBudget";
    final static String UsedGlobalBudgetKey = "usedGlobalBudget";
    final static String CustomIncomeKey = "customIncome";
    final static String CarryoverBudgetKey = "carryoverBudget";
    final static String BudgetResetTypeKey = "budgetResetType";
    final static String BudgetResetSubtypeKey = "budgetResetSubtype";
    final static String BudgetResetValueKey = "budgetResetValue";
    final static String LastBudgetResetTimeKey = "lastBudgetResetTime";

    final static String BudgetSetupDoneKey = "budgetSetupDone";

    final static String ActionRefreshBudgetKey = "com.BogdanMihaiciuc.receipt.actionRefreshBudget";
    final static String ActionCreateFromFactoryKey = "com.BogdanMihaiciuc.receipt.actionCreateFromFactory";
    final static String FactoryUIDKey = "backendFragment.factoryUID";
    final static long NoFactory = -1L;

    final static String OpenFilesSectionKey = "openFiles";
    final static String OpenFilesHeaderKey = "openFilesHeader";
    final static String LibrarySectionKey = "library";
    final static String LibraryHeaderKey = "libraryHeader";

    final static String ScrapMetadataKey = "BackendFragment$Scrap";

    final static int WhiteR = 0xF3, WhiteG = 0xF3, WhiteB = 0xF6;
    final static int BlackR = 0x1F, BlackG = 0x20, BlackB = 0x25;
    final static int RDiff = WhiteR - BlackR, GDiff = WhiteG - BlackG, BDiff = WhiteB - BlackB;

    final static float WarningPercentage = 0.25f;
    final static float ErrorPercentage = 0f;
    final static int BalanceStateOK = 0;
    final static int BalanceStateWarning = 1;
    final static int BalanceStateError = 2;

    final static int ShadowAppearDistanceDP = 48;

    // The NullOnTouchListener eats up motion events
    final static OnTouchListener NullOnTouchListener = new OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            return true;
        }
    };

    // The NullOnClickListener eats up click events
    final static OnClickListener NullOnClickListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
        }
    };

    static class TouchZoneDisabler implements OnTouchListener {
        Rect disabledZone;

        TouchZoneDisabler(Rect disabledZone) {
            this.disabledZone = disabledZone;
        }

        @Override
        public boolean onTouch(View view, MotionEvent e) {
            if (disabledZone.contains((int) e.getRawX(),(int) e.getRawY())) {
                if (DEBUG_ERRANT_TOUCHES) Log.d(TAG, "TouchZoneDisabler has disabled an errant touch.");
                return true;
            }
            if (DEBUG_ERRANT_TOUCHES) Log.d(TAG, "TouchZoneDisabler has allowed a non-errant touch through.");
            return false;
        }

    }

    public static interface OnBalanceStateChangeListener {
        public void onBalanceStateChanged(int fromState, int toState);
    }

    private int state = StateNotSet;
    private boolean sidebarMode;
    private int activeList;

    private BackendStorage storage;
    private List<AbstractReceipt> activeLists;

    private ReceiptActivity activity;

    private LegacyActionBar backendActionBar;

    private final DisplayMetrics metrics = new DisplayMetrics();

    private ReceiptReceiver budgetReceiver;
    private ArrayList<ReceiptReceiver> localFactoryReceivers;

    // BACKEND RELATED VIEWS
    private CollectionView backendCollection;
    private ViewGroup backend;
    private ViewGroup root;
    private ViewGroup content;
    private ViewGroup contentRoot;
    /**
     * The container in which the action bar for the open list will be placed.
     */
    private ViewGroup actionBarRoot;
    private int totalBottomPanelHeight;
    private ViewGroup totalBottomPanel;
    private FloatingActionButton scrapFAB;
    /**
     * The open list's action bar view.
     */
    private View actionBar;
    private View backendActionBarView;
    private View actionBarShadow;

    private Utils.ClippedLayout activityContainer;
    private ViewGroup dashboardPanelContainer;
    private ViewGroup dashboardActionBarContainer;

    private ViewGroup emptyContainer;
    private ValueAnimator emptyAnimator;

    private View oobeLayout;

    private int balanceState = BalanceStateOK;
    private boolean dashboardEditorUp;
    private View dashboardEditPanel;

    private View dashboard;
    private ViewGroup dashboardContainer;
    private View dashboardShadow;
    private TextView balanceText;
    private TextView balanceTitle;
    private TextView dayText;
    private FloatingActionButton backendFAB;
    private ValueAnimator actionBarAnimator;
    private ArrayList<OnBalanceStateChangeListener> balanceStateChangeListeners = new ArrayList<OnBalanceStateChangeListener>();

    private boolean phoneUI, landscape;

    private int rows = 2;

    private View confirmator;
    private View confirmingView;
    private boolean confirmatorUp;
    private AbstractReceipt confirmingReceipt;

    private HelpStory story;
    private int currentHelpPage = -1;

    private ArrayList<Runnable> startRunnables = new ArrayList<Runnable>();
    private ArrayList<Runnable> persistentStartRunnables = new ArrayList<Runnable>();

    private ArrayList<OnGlobalLayoutListener> animationLayoutListeners = new ArrayList<OnGlobalLayoutListener>();
    private ArrayList<Animator> animations = new ArrayList<Animator>();
    private ArrayList<View> animatedViews = new ArrayList<View>();
    private ArrayList<Runnable> pendingAnimations = new ArrayList<Runnable>();
    private Handler handler = new Handler();

    private BackendController controller = new BackendController();

    private BigDecimal globalBudget;
    private BigDecimal customIncome;
    private BigDecimal checkedOutBudget;
    private BigDecimal usedGlobalBudget;
//    private BigDecimal carryoverBudget;
//    private long nextBudgetResetTime;
    private int budgetResetType;
    private int budgetResetSubtype;
    private int budgetResetValue;
    private long lastBudgetResetTime;
    private boolean budgetInitialized = false;

    private BigDecimal totalDay = new BigDecimal(0);

    private int shadowAppearDistance;

    private Popover popover;

    private boolean introPlayed;

    private CollectionView.ReversibleAnimation insertAnimationStandard = new CollectionView.ReversibleAnimation() {
        @Override
        public void playAnimation(View view, Object object, int viewType) {
            view.setAlpha(0f);
            view.setScaleY(0.6f);
            view.setScaleX(0.6f);
            view.animate().alpha(1f).scaleX(1f).scaleY(1f);
        }

        @Override
        public void resetState(View view, Object object, int viewType) {
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (ReceiptActivity.DEBUG_CREATE_ORDER) Log.d(TAG, "BackendFragment.onCreate()");
        super.onCreate(savedInstanceState);
        if (ReceiptActivity.DEBUG_CREATE_ORDER) Log.d(TAG, "BackendFragment.super.onCreate()");
        // TODO
        getActivity().getFragmentManager().beginTransaction().add(new WelcomeFragment(), "com.BogdanMihaiciuc.receipt.WelcomeFragment").commit();
        backendActionBar = LegacyActionBar.getAttachableLegacyActionBar();
//        backendActionBar.setBackMode(LegacyActionBarView.DoneBackMode);

        boolean sidebarMode = getResources().getConfiguration().smallestScreenWidthDp >= 600;
        backendActionBar.getWrapper().forcedMinimumItems = 4;

//        backendActionBar.addItem(R.id.menu_new_receipt, getString(R.string.NewList), R.drawable.content_new_dark, false, true);
        if (!sidebarMode) {
            backendActionBar.addItem(R.id.menu_settings, getString(R.string.menu_settings), R.drawable.ic_settings_dark, false, true);
            backendActionBar.addItem(R.id.MenuEditDashboard, getString(R.string.MenuEditDashboard), R.drawable.ic_wallet_dark, false, true);
        }
        else {
            backendActionBar.addItem(R.id.menu_new_receipt, getString(R.string.NewList), R.drawable.content_new_mini_dark, false, true);
        }
        backendActionBar.addItem(R.id.menu_history, getString(R.string.menu_history), R.drawable.ic_history_dark, false, true);
        if (sidebarMode) backendActionBar.addItem(R.id.menu_settings, getString(R.string.menu_settings), R.drawable.ic_settings_dark, false, true);

//        backendActionBar.addItem(5050, "[DEV] CONNECTION TEST", R.drawable.ic_action_done_dark, false, true);

//        backendActionBar.addItem(6060, "[DEV] BETA WARNING", R.drawable.ic_action_done_dark, false, true);

//        backendActionBar.addItem(7070, "DEV - ProxyView animation", R.drawable.ic_add_to_list_dark, false, true);

//        backendActionBar.addItem(7076, "[DEV] Reset Budget", 0, false, false); //TODO
//        backendActionBar.buildItem().setId(7077).setTitle("[DEV] Trigger Budget Update!").setResource(0).setShowAsIcon(false).setTitleVisible(false).build();
//        backendActionBar.addItem(R.id.menu_settin)
//        backendActionBar.addItem(3048, "Fucking clear the lists", 0, false, false);
//        backendActionBar.addItem(R.id.menu_help, getString(R.string.menu_help), 0, false, false);

        backendActionBar.setLogoResource(R.drawable.logo_dark);
        backendActionBar.setOverflowResource(R.drawable.ic_action_overflow);
        backendActionBar.setBackButtonEnabled(false);

        backendActionBar.setOnLegacyActionSeletectedListener(this);

        backendActionBar.setBackgroundColor(getResources().getColor(sidebarMode ? R.color.GradientStart : R.color.DashboardActionBar));
        backendActionBar.setTextColor(getResources().getColor(R.color.DashboardText));

        getActivity().getFragmentManager().beginTransaction().add(backendActionBar, "com.BogdanMihaiciuc.receipt.BackendActionBar").commit();

        setRetainInstance(true);
        setHasOptionsMenu(true);

        storage = BackendStorage.getSharedStorage(getActivity().getApplicationContext());
        storage.addOnLoadProgressListener(this);
        initialize();


        // Check to see if the alarm has already been scheduled
//        Intent budgetRefresher = new Intent(ActionRefreshBudgetKey);
//        PendingIntent intent = PendingIntent.getBroadcast(getActivity().getApplicationContext(), 0, budgetRefresher, PendingIntent.FLAG_NO_CREATE);
//
//        if (intent != null) {
//            AlarmManager am = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
//            am.set(AlarmManager.RTC, nextBudgetResetTime, intent);
//        }
    }

    private void initialize() {

        final ReceiptActivity activity = (ReceiptActivity) getActivity();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);

        final SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext());

        if (state == StateNotSet) {
            state = globalPrefs.getInt(ExitStateKey, StateBackend);
            if (USE_RESET_PHONE_STATE && activity.getResources().getConfiguration().smallestScreenWidthDp < 600) state = StateBackend;
        }

        loadGlobalBudget(globalPrefs);
        refreshBudgetIfNeeded();

        SharedPreferences exitState = activity.getPreferences(Context.MODE_PRIVATE);
        if (!exitState.getBoolean(ReceiptActivity.BackendTransitionCompleteKey, false)) {
            exitState.edit().putBoolean(ReceiptActivity.BackendTransitionCompleteKey, true).apply();
        }
        activeLists = storage.getActiveLists();

        if (activeLists.size() > 0) {
            AbstractReceipt receipt = activeLists.get(0);
            if (receipt == null) receipt = new AbstractReceipt();
            if (receipt.header == null) receipt.header = new BackendStorage.ReceiptFileHeader();
            if (receipt.items == null) receipt.items = new ArrayList<Item>();

            activity.restoreState(receipt);
            activeList = 0;
        }

        controller.addSectionForViewTypeWithTag(ViewTypeDashboardPlaceholder, null).addObject(new Object());
        if (activity.getResources().getConfiguration().smallestScreenWidthDp < 600) {
            controller.addSectionForViewTypeWithTag(ViewTypeHeader, LibraryHeaderKey).addObject(getString(R.string.SourceLibrary));
        }
        controller.addSectionForViewTypeWithTag(ViewTypeScrap, LibrarySectionKey).addAllObjects(activeLists);
        if (state == StateOpenList && activity.getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            controller.findSectionWithTag(LibrarySectionKey).removeObjectAtIndex(0);
        }

//        budgetReceiver = ReceiptReceiver.budgetReceiver(new Runnable() {
//            @Override
//            public void run() {
//                refreshBudget();
//            }
//        });

        onHistoryTotalsChanged();

    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (storage != null)
            storage.removeOnLoadProgressListener(this);
    }

    public void onProgressUpdate() {
        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);
        if (state == StateOpenList && sidebarMode) {
            controller.findSectionWithTag(LibrarySectionKey).removeObjectAtIndex(0);
        }
        if (backendCollection != null) {
            backendCollection.refreshViews();
            boolean animationsEnabled = backendCollection.areAnimationsEnabled();
            backendCollection.setAnimationsEnabled(false);
            controller.requestCompleteTransaction();
            backendCollection.setAnimationsEnabled(animationsEnabled);
        }
        else {
            controller.requestCompleteTransaction();
        }

    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        sidebarMode = getResources().getConfiguration().smallestScreenWidthDp >= ReceiptActivity.MinimumSidebarDP ||
                (getResources().getConfiguration().smallestScreenWidthDp >= ReceiptActivity.MinimumLandscapeSidebarDP
                        && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        if (getResources().getConfiguration().smallestScreenWidthDp >= 600) {
            if (getResources().getConfiguration().screenWidthDp >= 1200) {
                rows = 2;
            }
            else {
                rows = 1;
            }
        }
        else {
            if (getResources().getConfiguration().screenWidthDp >= 560 || getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
                rows = 2;
            }
            else {
                rows = 1;
            }
        }

//        Log.d(TAG, "BackendWidth: " + (getResources().getDimensionPixelSize(R.dimen.BackendWidth) / getResources().getDisplayMetrics().density));

        phoneUI = getResources().getConfiguration().smallestScreenWidthDp < 600;
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        controller.setColumnCountForViewType(rows, ViewTypeScrap);

        // *********** VIEW OBJECTS **************
        activity = (ReceiptActivity) getActivity();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        root = (ViewGroup) activity.getWindow().getDecorView();
        contentRoot = activity.getContentRoot();
        content = (ViewGroup) activity.getContent();
        actionBarRoot = (ViewGroup) activity.getActionBarContainer().getParent();
        totalBottomPanelHeight = (int) (getResources().getDimension(R.dimen.TotalFragmentHeight) + metrics.density * 72 / 2);
        totalBottomPanel = (ViewGroup) activity.findViewById(R.id.TotalBottomPanel);
        scrapFAB = (FloatingActionButton) activity.findViewById(R.id.ScrapFAB);
        actionBar = actionBarRoot.getChildAt(0);

        ((ViewGroup) activity.findViewById(R.id.ContentContainer).getParent()).setClipChildren(false);

        shadowAppearDistance = (int) (ShadowAppearDistanceDP * metrics.density);

        backend = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.backend, content, false);

        emptyContainer = (ViewGroup) backend.findViewById(R.id.EmptyContainer);
        emptyContainer.findViewById(R.id.EmptyText).setVisibility(View.GONE);

        backendCollection = (CollectionView) backend.findViewById(R.id.BackendCollection);
        backendCollection.setMoveWithLayersEnabled(false);
        backendCollection.setContainerResizeDelay(520);
        backendCollection.setInsertAnimator(insertAnimationStandard);
        backendCollection.setMoveInterpolator(new Utils.FrictionInterpolator(1.33f));

        dashboard = backend.findViewById(R.id.Dashboard);
        dashboardContainer = (ViewGroup) backend.findViewById(R.id.DashboardContainer);
        dashboardShadow = backend.findViewById(R.id.DashboardShadow);
        balanceText = (TextView) dashboard.findViewById(R.id.DashboardBalanceText);
        balanceTitle = (TextView) dashboard.findViewById(R.id.DashboardBalanceTitle);
        dayText = (TextView) backend.findViewById(R.id.DashboardPrimaryText);

        if (sidebarMode) {
            activityContainer = (Utils.ClippedLayout) root.findViewById(R.id.ActivityContainer);
            ((TextView) dashboard.findViewById(R.id.DashboardBalanceTitle)).setTypeface(Receipt.condensedTypeface());

            actionBarShadow = backend.findViewById(R.id.BackendActionBarSeparator);

            dashboardPanelContainer = (ViewGroup) root.findViewById(R.id.DashboardPanelContainer);
            dashboardActionBarContainer = (ViewGroup) root.findViewById(R.id.DashboardActionBarContainer);

            dashboardPanelContainer.addView(obtainDashboardEditor());
            FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) dashboardEditPanel.getLayoutParams();
            params.gravity = Gravity.CENTER;
            if (landscape) {
                params.width = (int) (metrics.density * 480 + 0.5f);
            }
            else {
                params.width = (int) (metrics.density * 320 + 0.5f);
            }
            dashboardActionBarContainer.addView(obtainDashboardEditorHeader(LayoutInflater.from(activity), dashboardActionBarContainer));

            dashboardContainer.findViewById(R.id.DashboardRipple).setBackground(new LegacyRippleDrawable(activity));
            dashboardContainer.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (state == StateOpenList && backendCollection.areInteractionsEnabled()) {
                        ((LegacyRippleDrawable) dashboardContainer.findViewById(R.id.DashboardRipple).getBackground()).dismissPendingFlushRequest();
                        flushAnimations();
                        collapseBackend();
                    }
                }
            });


            dashboardActionBarContainer.findViewById(R.id.MenuEditDashboard).setBackground(new LegacyRippleDrawable(activity).setShape(LegacyRippleDrawable.ShapeCircle));
            dashboardActionBarContainer.findViewById(android.R.id.home).setBackground(new LegacyRippleDrawable(activity).setShape(LegacyRippleDrawable.ShapeCircle));

            dashboardActionBarContainer.findViewById(R.id.MenuEditDashboard).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    activateDashboardEditor(true);
                }
            });
            dashboardActionBarContainer.findViewById(android.R.id.home).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    hideDashboardEditor(true);
                }
            });

            dashboardActionBarContainer.findViewById(R.id.MenuEditDashboard).setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new TooltipPopover(getString(R.string.MenuEditDashboard), null, Popover.anchorWithID(R.id.MenuEditDashboard)).show(activity);
                    return true;
                }
            });

//            PopoverDrawable background = new PopoverDrawable(activity, true);
//            background.setGravity(PopoverDrawable.GravityCenter);
//            background.setShadowRadius(16, 16, true);
//            background.setPadding(16, 0, 8, 0);
//            activityContainer.setBackground(background);

//            activity.findViewById(R.id.innerList).setBackground(null);

        }

        if (state != StateBackend && !sidebarMode) backend.setVisibility(View.INVISIBLE);

        // *********** ONSCROLL LISTENER **************
        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
        final boolean Landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        backendCollection.setOnScrollListener(new CollectionView.OnScrollListener() {

            float alpha = 0f;

            View placeholder = null;
//            View header = null;

            @Override
            public void onScroll(CollectionView collectionView, int top, int amount) {
                if (placeholder == null) {
                    placeholder = (View) collectionView.retainViewForObject(controller.getSectionAtIndex(0).getObjectAtIndex(0)).getParent();
                }

//                if (header == null) {
//                    header = (View) collectionView.retainViewForObject(controller.findSectionWithTag(LibraryHeaderKey).getObjectAtIndex(0)).getParent();
//                }

                if (placeholder == null) {
                    top = 0;
                }

                if (dashboardEditorUp) return; // Messes with rotation

                if (top < dashboardContainer.getHeight()) {
                    top = - top;
                    int containerHeight = dashboardContainer.getHeight();
                    int fabTranslation = - containerHeight + (int) (24 * metrics.density);
                    fabTranslation = top < fabTranslation ? fabTranslation : top;

                    alpha = -top/(ShadowAppearDistanceDP * metrics.density);
                    alpha = alpha > 1f ? 1f : alpha;
                    if (!Landscape || sidebarMode) {
                        dashboardContainer.setTranslationY(top);
                        if (!sidebarMode) dashboardShadow.setTranslationY(top);
                        if (USE_ALIGN_FAB) backendFAB.setTranslationY(fabTranslation);
                    }

                    if (sidebarMode) {
                        actionBarShadow.setAlpha(alpha);
                    }
                }
                else {
                    alpha = 1f;

                    if (!Landscape || sidebarMode) {
                        dashboardContainer.setTranslationY(-dashboardContainer.getHeight());
                        if (!sidebarMode) dashboardShadow.setTranslationY(-dashboardContainer.getHeight());
                        if (USE_ALIGN_FAB) backendFAB.setTranslationY(- dashboardContainer.getHeight() + (int) (24 * metrics.density));
                    }

                    if (sidebarMode) {
                        actionBarShadow.setAlpha(alpha);
                    }
                }
            }
        });

        backendCollection.setOnViewCollectedListener(new CollectionView.OnViewCollectedListener() {
            @Override
            public void onViewCollected(CollectionView collectionView, View view, int viewType) {
                if (viewType == ViewTypeScrap) {
                    ((LegacyRippleDrawable) view.findViewById(R.id.ScrapRipple).getBackground()).flushRipple();
                }
            }
        });

        backendActionBarView = backend.findViewById(R.id.BackendActionBar);
        if (sidebarMode) {
            backendActionBar.setSplitZone((ViewGroup) backend.findViewById(R.id.BackendActionBarSplit));
            backendActionBar.setSplitZoneAlignment(LegacyActionBarView.SplitZoneAlignmentCenter);
        }
        backendActionBar.setContainer((ViewGroup) backendActionBarView);

        // *********** LAYOUT ADJUSTMENTS **************
        if (Landscape && getResources().getConfiguration().smallestScreenWidthDp < 600) {
            backendCollection.getLayoutParams().width = (int) ((168 * rows) * metrics.density);
            dashboardContainer.getLayoutParams().width = metrics.widthPixels - backendCollection.getLayoutParams().width;
            dashboardContainer.getLayoutParams().width = metrics.widthPixels - backendCollection.getLayoutParams().width;
        }

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            ((ViewGroup.MarginLayoutParams) (backendFAB = (FloatingActionButton) backend.findViewById(R.id.BackendFAB)).getLayoutParams()).topMargin =
                    getResources().getDimensionPixelSize(R.dimen.ActionBarSize) + getResources().getDimensionPixelSize(R.dimen.DashboardHeight) - (int) (metrics.density * 72) / 2;
        }
        else {
            ((ViewGroup.MarginLayoutParams) (backendFAB = (FloatingActionButton) backend.findViewById(R.id.BackendFAB)).getLayoutParams()).leftMargin =
                    dashboardContainer.getLayoutParams().width - (int) (metrics.density * 72) / 2;
        }
        if (sidebarMode) activity.setListVisible(true, false, 0);

        backendFAB.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                if (dashboardEditorUp) {
                    dashboardEditWrapper.dismiss();
                }
                else {
                    addNewList();
                }
            }
        });
        backendFAB.setBackgroundAndGlyphColors(getResources().getColor(R.color.HeaderCanCheckout), 0xFFFFFFFF, false);
        backendFAB.setGlyph(Glyph.GlyphPlus);
        backendFAB.setTitle(getString(R.string.NewList));

        backendCollection.setController(controller);
        backendCollection.ensureMinimumSupplyForViewType(8, ViewTypeScrap);

        if (contextBar != null) {
            backendCollection.setOverScrollEnabled(false);
        }

        if (sidebarMode) {
            ((ViewGroup) root.findViewById(R.id.BackendContainer)).addView(backend, 0);
        }
        else {
            root.addView(backend, 0);
        }

        if (state == StateBackend) {
            prepareBackend();
        }
        if (sidebarMode) {
            if (state == StateBackend) {
                activityContainer.setVisibility(View.INVISIBLE);
                dashboardPanelContainer.setVisibility(View.VISIBLE);
            }
            else {
                activityContainer.setVisibility(View.VISIBLE);
                dashboardPanelContainer.setVisibility(View.INVISIBLE);
            }
        }

        prepareActionBar(false);

        if (confirmatorUp) {
            showDeleteConfirmator(false);
        }

        if (startRunnables.size() > 0) {
            for (Runnable runnable : startRunnables)
                runnable.run();
            startRunnables.clear();
        }

        if (persistentStartRunnables.size() > 0) {
            for (Runnable runnable : persistentStartRunnables)
                runnable.run();
        }

        if (currentHelpPage != -1)
            backendActionBarView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                @SuppressWarnings("deprecation")
                @Override
                public void onGlobalLayout() {
                    backendActionBarView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    showHelpWithPage(currentHelpPage, true);
                }
            });

        updateBalanceDisplay();

        if (!sidebarMode) {
            root.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                private View content;

                @Override
                public void onGlobalLayout() {
                    if (content == null)
                        content = (View) activity.findViewById(R.id.ContentContainer).getParent();
                    if (((ViewGroup.MarginLayoutParams) backend.getLayoutParams()).topMargin != content.getTop()) {
                        ((ViewGroup.MarginLayoutParams) backend.getLayoutParams()).topMargin = content.getTop();
                        backend.requestLayout();
                    }
                }
            });
        }

//        if (selection.size() != 0)
//            dashboardContainer.setAlpha(0.5f);

        balanceText.setTypeface(Typeface.createFromAsset(activity.getAssets(), "RobotoCondensed-Light.ttf"));

        final View Graphic = emptyContainer.findViewById(R.id.EmptyImage);
        Graphic.setAlpha(0f);

        if (emptyAnimator != null) {
            emptyAnimator.cancel();
        }

        emptyAnimator = ValueAnimator.ofFloat(0f, 1f);
        emptyAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                Graphic.setTranslationY(fraction * Graphic.getHeight() / 2 - Graphic.getHeight() / 4);
                if (fraction < 0.5f) {
                    Graphic.setAlpha(fraction);
                } else {
                    Graphic.setAlpha(1 - fraction);
                }
            }
        });
        emptyAnimator.addListener(new AnimatorListenerAdapter() {
            boolean cancelled;

            public void onAnimationStart(Animator animation) {
                cancelled = false;
                if (emptyContainer != null) {
                    if (!sidebarMode) emptyContainer.setVisibility(View.VISIBLE);
                }
            }

            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            public void onAnimationEnd(Animator animation) {
                if (!cancelled) {
                    animation.start();
                    if (emptyContainer != null) {
                        emptyContainer.setVisibility(View.INVISIBLE);
                    }
                }
            }
        });
        emptyAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        emptyAnimator.setDuration(1000);
        emptyAnimator.setStartDelay(500);
        if (state == BackendFragment.StateBackend && controller.findSectionWithTag(LibrarySectionKey).getSize() == 0) {
            emptyAnimator.start();
        }
        else {
            emptyContainer.setVisibility(View.INVISIBLE);
        }

        if (!sidebarMode) initOverScrollListener(); // TODO sidebar friendly overscrolllistener

        if (dashboardEditorUp) {
            showDashboardEditor(false);
        }
        else {
            hideDashboardEditor(false);
        }

        if (popover != null) {
            if (phoneUI && landscape) {
                popover.requestGravity(Popover.GravityRightOf);
            }
            else {
                popover.requestGravity(Popover.GravityBelow);
            }
        }


        if (!introPlayed) {
            introPlayed = true;

            backend.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                int step = 0;
                @Override
                public void onGlobalLayout() {
                    step++;

                    if (step == 2) backend.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    else return;

                    if (state == StateBackend || sidebarMode) {
                        $(sidebarMode ? R.id.DashboardContainer : -1).add(backendCollection, "." + ScrapMetadataKey)
                                .property($.Y, activity.getWindow().getDecorView().getHeight())
                                .property($.TranslateX, $.dp(-15, activity))
                                .animate()
                                    .property($.TranslateY, 0)
                                    .property($.TranslateX, 0)
                                    .property($.Rotate, -15, 0)
                                    .stride(50)
                                    .duration(500)
                                    .interpolator(new DecelerateInterpolator(1.5f))
                                    .delay(500)
                                .start();

                        backendFAB.hide(false);
                        backendFAB.showDelayed(500);

                        $(sidebarMode ? backendActionBar.getBaseActionBarView().getSplitZone() : backendActionBar.getBaseActionBarView(), "." + LegacyActionBarView.ActionItemMetadataKey).not("#7070")
                                .property($.Y, $.dp(-72, activity))
                                .animate()
                                    .property($.TranslateY, 0)
                                    .stride(50)
                                    .duration(400)
                                    .delay(500)
                                    .interpolator(new DecelerateInterpolator(1.5f))
                                .start();
//                                .animate()
//                                    .property($.TranslateY, $.dp(-8, activity))
//                                    .duration(100)
//                                    .interpolator(new DecelerateInterpolator(1.5f))
//                                .start()
//                                .animate()
//                                    .property($.TranslateY, 0)
//                                    .duration(100)
//                                    .interpolator(new AccelerateInterpolator(1.5f))
//                                .start();

                        $(R.id.view2).property($.ScaleX, 0)
                                .animate()
                                    .property($.ScaleX, 1)
                                    .duration(400)
                                    .delay(700)
                                .start();

                        if (!sidebarMode) {
                            $(R.id.DashboardBalanceTitle).property($.TranslateY, $.dp(48, activity)).property($.Opacity, 0);
                            $(R.id.DashboardBalanceText).property($.TranslateY, $.dp(48, activity)).property($.Opacity, 0);

                            $(R.id.DashboardBalanceText, R.id.DashboardBalanceTitle)
                                    .animate()
                                        .property($.TranslateY, 0)
                                        .property($.Opacity, 1)
                                        .layer(true)
                                        .duration(500)
                                        .stride(100)
                                        .interpolator(new DecelerateInterpolator(1.5f))
                                        .delay(800)
                                    .start();
                        }
                    }

                    if (state == StateOpenList || sidebarMode) {
                        $("." + ItemCollectionFragment.ItemMetadataKey)
                                .property($.Y, root.getWidth() / 2f)
                                .unclip(2)
                                .property($.Opacity, 0)
                                .animate()
                                    .property($.Y, 0)
                                    .property($.Opacity, 1)
                                .delay(500)
                                .stride(20)
                                    .interpolator(new DecelerateInterpolator(1.5f))
                                .layer(true)
                                .start();

                        if (scrapFAB.isVisible()) {
                            scrapFAB.hide(false);
                            scrapFAB.showDelayed(500);
                        }

                        $(activity.getLegacyActionBar().getBaseActionBarView(), "." + LegacyActionBarView.ActionItemMetadataKey).add("." + HeaderFragment.HeaderItemMetadataKey)
                                .property($.Y, $.dp(-72, activity))
                                .animate()
                                    .property($.TranslateY, 0)
                                    .stride(50)
                                    .duration(300)
                                    .delay(500)
                                    .interpolator(new DecelerateInterpolator(1.5f))
                                .start();

                        $(R.id.total_sum, R.id.text_total)
                                .property($.Y, $.dp(56, activity))
                                .animate()
                                    .property($.Y, 0)
                                    .duration(300)
                                    .stride(100)
                                    .interpolator(new DecelerateInterpolator(1.5f))
                                    .delay(500)
                                .start();
                    }
                }
            });
        }


    }

    public void onResume() {
        super.onResume();
//        getActivity().getApplicationContext().registerReceiver(budgetReceiver, new IntentFilter(ActionRefreshBudgetKey));
    }

    public void onPause() {
        super.onPause();

//        getActivity().getApplicationContext().unregisterReceiver(budgetReceiver);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onDetach() {
        super.onDetach();
        flushAnimations();

        activity = null;
        root = null;
        contentRoot = null;
        content = null;
        actionBarRoot = null;
        totalBottomPanel = null;
        scrapFAB = null;
        actionBar = null;

        balanceText = null;
        balanceTitle = null;
        dayText = null;

        backendCollection = null;
        backend = null;
        backendActionBarView = null;

        actionBarShadow = null;

        emptyContainer = null;

        activityContainer = null;
        dashboardPanelContainer = null;
        dashboardActionBarContainer = null;

        dashboard = null;
        dashboardContainer = null;
        dashboardShadow = null;
        dashboardEditPanel = null;

        backendFAB = null;

        confirmator = null;
        confirmingView = null;

        emptyAnimator = null;

        oobeLayout = null;

        if (story != null) {
            story.cleanup();
        }
        story = null;
    }

    public void flushAnimations() {
        $.createGlobalQueue();

        if (backendFAB != null) {
            backendFAB.flushAnimations();
        }

        while (pendingAnimations.size() > 0) {
            handler.removeCallbacks(pendingAnimations.get(0));
            pendingAnimations.get(0).run();
        }

        if (actionBarAnimator != null) {
            actionBarAnimator.end();
            actionBarAnimator = null;
        }

        for (OnGlobalLayoutListener listener : animationLayoutListeners) {
            listener.onGlobalLayout();
        }

        while (animatedViews.size() > 0) {
            animatedViews.get(0).animate().cancel();
        }

        while (animations.size() > 0) {
            animations.get(0).cancel();
        }

        activity.stopAnimations();

        if (emptyAnimator != null) {
            emptyAnimator.cancel();
            emptyAnimator.setCurrentPlayTime(emptyAnimator.getDuration());
        }
    }

    public int getState() {
        return state;
    }

    public boolean isSidebar() {
        return sidebarMode;
    }

    public int getNumberOfActiveLists() {
        if (confirmatorUp)
            return activeLists.size() - 1;
        return activeLists.size();
    }

    public void refreshBudgetIfNeeded() {
        if (getNextBudgetResetTimeFromDate(lastBudgetResetTime) <= System.currentTimeMillis()) {
            refreshBudget();
//
//            lastBudgetResetTime = System.currentTimeMillis();
//            PreferenceManager.getDefaultSharedPreferences(Receipt.getStaticContext()).edit().putLong(LastBudgetResetTimeKey, lastBudgetResetTime).apply();
        }
    }

    public void refreshBudget() {

        final SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(Receipt.getStaticContext());

        BigDecimal usedBudgetPlaceholder = usedGlobalBudget;
        usedGlobalBudget = new BigDecimal("0");
        BigDecimal remainingBudget = getBalance();
        usedGlobalBudget = usedBudgetPlaceholder;

        // Load budget related values
        try {
            globalBudget = new BigDecimal(globalPrefs.getString(GlobalBudgetKey, "0"));
        }
        catch (NumberFormatException e) {
            globalBudget = new BigDecimal("0");
        }

        checkedOutBudget = new BigDecimal("0");

//        carryoverBudget = remainingBudget;
        customIncome = remainingBudget;

        // Make sure the types have been loaded correctly
        budgetResetType = globalPrefs.getInt(BudgetResetTypeKey, BudgetResetTypeManually);
        budgetResetSubtype = globalPrefs.getInt(BudgetResetSubtypeKey, BudgetResetSubtypeInapplicable);
        budgetResetValue = globalPrefs.getInt(BudgetResetValueKey, 0);

        if (DEBUG_BUDGET) {
            Log.d(TAG, "Post-update budget values: \n" +
                    "Budget: " + globalBudget + "\n" +
                    "CheckedOutBudget: " + checkedOutBudget + "\n" +
                    "CustomIncome: " + customIncome + "\n" +
                    "UsedBudget: " + usedGlobalBudget + "\n" +
                    "resulting balance: " + getBalance());
        }

//        nextBudgetResetTime = getNextBudgetResetTime();

//        Calendar calendar = Calendar.getInstance();
//        calendar.setTimeInMillis(nextBudgetResetTime);
//        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");
//        if (DEBUG_BUDGET) Log.d(TAG, "Next reset time is: " + sdf.format(calendar.getTime()));

        final SharedPreferences.Editor globalPrefsEditor = globalPrefs.edit();
        globalPrefsEditor.putString(UsedGlobalBudgetKey, usedGlobalBudget.toString());
        globalPrefsEditor.putString(CheckedOutBudgetKey, checkedOutBudget.toString());
        globalPrefsEditor.putString(CustomIncomeKey, customIncome.toString());

        lastBudgetResetTime = System.currentTimeMillis();
        globalPrefsEditor.putLong(LastBudgetResetTimeKey, lastBudgetResetTime);
        globalPrefsEditor.apply();

        updateBalanceDisplay();

//        Intent budgetRefresher = new Intent(ActionRefreshBudgetKey);
//        PendingIntent intent = PendingIntent.getBroadcast(getActivity().getApplicationContext(), 0, budgetRefresher, PendingIntent.FLAG_UPDATE_CURRENT);
//
//        AlarmManager am = (AlarmManager) getActivity().getSystemService(Context.ALARM_SERVICE);
//        am.set(AlarmManager.RTC, nextBudgetResetTime, intent);

    }

    public long getNextBudgetResetTime() {
        return getNextBudgetResetTimeFromDate(System.currentTimeMillis());
    }

    public long getNextBudgetResetTimeFromDate(long timeInMillis) {
        if (budgetResetValue == 0) {
            return Long.MAX_VALUE;
        }

        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(com.BogdanMihaiciuc.util.Date.dateAtTime(timeInMillis).getNextDate(budgetResetValue));

        if (DEBUG_BUDGET) {
            Calendar now = Calendar.getInstance();
            now.setTimeInMillis(timeInMillis);

            String message = "Reset debug: \n";
            message += "Now: " + now.get(Calendar.YEAR) + " " + now.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) + " " + now.get(Calendar.DATE) + "\n";
            message += "Next: " + next.get(Calendar.YEAR) + " " + next.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) + " " + next.get(Calendar.DATE) + "\n";
            message += "\twith reset date: " + budgetResetValue;
            Log.d(TAG, message);
        }

        return next.getTimeInMillis();
    }

    public BigDecimal getGlobalBudget() {
        return globalBudget;
    }

//    public BigDecimal getCompoundBudget() {
//        return globalBudget.subtract(carryoverBudget);
//    }

    public BigDecimal getBalance() {
        return globalBudget.subtract(usedGlobalBudget).subtract(checkedOutBudget).add(customIncome);
    }

    public void addToGlobalBudget(BigDecimal amount) {
        globalBudget = globalBudget.add(amount);
    }

    public void addToUsedGlobalBudget(BigDecimal amount) {
        if (!budgetInitialized) {
            if (usedGlobalBudget == null) {
                usedGlobalBudget = amount;
            }
            else {
                usedGlobalBudget = usedGlobalBudget.add(amount);
            }

            return;
        }
        usedGlobalBudget = usedGlobalBudget.add(amount);
        updateBalanceDisplay();
    }

    public void removeFromUsedGlobalBudget(BigDecimal amount) {
        if (!budgetInitialized) {
            if (usedGlobalBudget == null) {
                usedGlobalBudget = amount.negate();
            }
            else {
                usedGlobalBudget = usedGlobalBudget.subtract(amount);
            }

            return;
        }
        usedGlobalBudget = usedGlobalBudget.subtract(amount);
        updateBalanceDisplay();
    }

    public void checkOutBudget(BigDecimal amount) {
        // The amount MUST be at least equal to usedGlobalBudget
        usedGlobalBudget = usedGlobalBudget.subtract(amount);
        checkedOutBudget = checkedOutBudget.add(amount);

        updateBalanceDisplay();
    }

    static class HistoryTotalsContainer {
        BigDecimal day;
    }

    public void onHistoryTotalsChanged() {
        new AsyncTask<Void, Void, HistoryTotalsContainer>() {

            @Override
            protected HistoryTotalsContainer doInBackground(Void... voids) {
                HistoryTotalsContainer container = new HistoryTotalsContainer();

                synchronized (Receipt.DatabaseLock) {
                    SQLiteDatabase db = Receipt.DBHelper.getReadableDatabase();
                    Cursor total = db.rawQuery("select sum(" + Receipt.DBPriceKey + ")" +
                            " from " + Receipt.DBReceiptsTable +
                            " where strftime( '%Y-%m-%d', " + Receipt.DBDateKey + ", 'unixepoch', 'localtime') " +
                            "= strftime( '%Y-%m-%d', " + (Calendar.getInstance().getTimeInMillis() / 1000) + ", 'unixepoch', 'localtime')", null);

                    if (total.moveToFirst()) container.day = new BigDecimal(total.getLong(0)).movePointLeft(2);
                    else container.day = new BigDecimal(0);
                    total.close();

                    db.close();

                }
                return container;
            }

            protected void onPostExecute(HistoryTotalsContainer result) {
                totalDay = result.day;

                updateBalanceDisplay();
            }
        }.execute();
    }

    public void addOnBalanceStateChangeListener(OnBalanceStateChangeListener listener) {
        if (!balanceStateChangeListeners.contains(listener))
            balanceStateChangeListeners.add(listener);
    }

    public void removeOnBalanceStateChangeListener(OnBalanceStateChangeListener listener) {
        balanceStateChangeListeners.remove(listener);
    }

    public int getBalanceState() {
        return balanceState;
    }

    protected void updateBalanceDisplay() { updateBalanceDisplay(true); }

    protected void updateBalanceDisplay(boolean animated) {
        if (activity == null) return;

        refreshBudgetIfNeeded();

//        if (!PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean(BudgetSetupDoneKey, false)) {
//            if (oobeLayout == null) {
//                dashboardContainer.addView(oobeLayout = LayoutInflater.from(activity).inflate(R.layout.dashboard_oobe_button, dashboardContainer, false));
//                ((FrameLayout.LayoutParams) oobeLayout.getLayoutParams()).gravity = Gravity.CENTER;
//                ((TextView) ((ViewGroup) oobeLayout).getChildAt(0)).setTypeface(Receipt.condensedTypeface());
//
//                dashboard.setVisibility(View.INVISIBLE);
//            }
//            return;
//        }

        float percentage;
        if (getGlobalBudget().compareTo(new BigDecimal(0)) == 0) {
            percentage = 1f;
        }
        else {
            percentage = getBalance().setScale(2, RoundingMode.DOWN).divide(getGlobalBudget(), RoundingMode.DOWN).floatValue();
        }

        if (getBalance().compareTo(new BigDecimal(0)) == -1) percentage = -1f;

        balanceText.setText(ReceiptActivity.totalFormattedString(activity, getBalance()));
        dayText.setText(ReceiptActivity.totalFormattedString(activity, usedGlobalBudget.add(totalDay)));

        final int CurrentColor = backendActionBar.getBackgroundColor();
        final LegacyActionBar ActivityActionBar = activity.getLegacyActionBar();
        final int CurrentActivityColor = ActivityActionBar.getBackgroundColor();
        final int CurrentState = balanceState;

        if (dashboardEditPanel != null) {
            ((TextView) dashboardEditPanel.findViewById(R.id.DashboardEditBalance)).setText(balanceText.getText());
        }

        if (percentage > 0 /*WarningPercentage*/) {
            backendActionBar.setBackgroundColor(getResources().getColor(sidebarMode ? R.color.GradientStart : R.color.DashboardActionBar));
            if (balanceState == BalanceStateOK) return; // No state change has occured
            balanceText.setTextColor(getResources().getColor(R.color.DashboardText));
            balanceTitle.setText(getString(R.string.Balance));
            setActionBarDarkIcons();
            balanceState = BalanceStateOK;
            backendActionBar.setSeparatorVisible(false);
            backendActionBar.setTextColor(getResources().getColor(R.color.DashboardText));

            ActivityActionBar.setSeparatorVisible(false);

            if (actionBarAnimator != null) actionBarAnimator.cancel();
            actionBarAnimator = ValueAnimator.ofFloat(0f, 1f);
            actionBarAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    backendActionBar.setBackgroundColor(Utils.interpolateColors(valueAnimator.getAnimatedFraction(),
                            CurrentColor,
                            getResources().getColor(sidebarMode ? R.color.GradientStart : R.color.DashboardActionBar)));
                    ActivityActionBar.setBackgroundColor(Utils.interpolateColors(valueAnimator.getAnimatedFraction(),
                            CurrentActivityColor,
                            getResources().getColor(sidebarMode ? R.color.DashboardBackground : R.color.ActionBar)));
                }
            });
            actionBarAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            if (animated) actionBarAnimator.start();
            else actionBarAnimator.end();
        }
//        else if (percentage > ErrorPercentage) {
//            balanceText.setTextColor(getResources().getColor(R.color.DashboardText));
//            if (balanceState == BalanceStateWarning) return; // No state change has occured
////            balanceText.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
////            dashboardContainer.setBackgroundDrawable(getResources().getDrawable(R.drawable.dashboard_warning_background));
////            ((GradientDrawable) dashboardContainer.getBackground()).setDither(true);
////            dashboardContainer.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
//            balanceTitle.setText(getString(R.string.Balance));
//            balanceState = BalanceStateWarning;
//
////            backendActionBar.setBackgroundResource(android.R.color.holo_orange_light);
//            setActionBarDarkIcons();
//            backendActionBar.setSeparatorVisible(true);
//            backendActionBar.setSeparatorOpacity(0.15f);
//
//            ActivityActionBar.setSeparatorVisible(false);
//
//            if (actionBarAnimator != null) actionBarAnimator.cancel();
//            actionBarAnimator = ValueAnimator.ofFloat(0f, 1f);
//            actionBarAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
//                @Override
//                public void onAnimationUpdate(ValueAnimator valueAnimator) {
//                    backendActionBar.setBackgroundColor(Utils.interpolateColors(valueAnimator.getAnimatedFraction(),
//                            CurrentColor,
//                            getResources().getColor(android.R.color.holo_orange_light)));
//                    ActivityActionBar.setBackgroundColor(Utils.interpolateColors(valueAnimator.getAnimatedFraction(),
//                            CurrentActivityColor,
//                            getResources().getColor(R.color.ActionBar)));
//                }
//            });
//            actionBarAnimator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
//            if (animated) actionBarAnimator.start();
//            else actionBarAnimator.end();
//        }
        else {
            balanceText.setTextColor(getResources().getColor(R.color.OverBudget));
//            dashboardContainer.setBackgroundDrawable(getResources().getDrawable(R.drawable.dashboard_error_background));
//            ((GradientDrawable) dashboardContainer.getBackground()).setDither(true);
            if (percentage < 0) {
                balanceTitle.setText(getString(R.string.Debt));
            }
            else {
                balanceTitle.setText(getString(R.string.Balance));
            }
            if (balanceState == BalanceStateError) return; // No state change has occured
            balanceState = BalanceStateError;

//            backendActionBar.setBackgroundResource(android.R.color.holo_red_light);
            setActionBarLightIcons();
            backendActionBar.setSeparatorVisible(true);
            backendActionBar.setSeparatorOpacity(0.15f);

            ActivityActionBar.setSeparatorVisible(true);
            ActivityActionBar.setSeparatorOpacity(0.15f);

            backendActionBar.setTextColor(getResources().getColor(android.R.color.white));

            if (actionBarAnimator != null) actionBarAnimator.cancel();
            actionBarAnimator = ValueAnimator.ofFloat(0f, 1f);
            final ValueAnimator.AnimatorUpdateListener UpdateListener = new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = Math.abs(valueAnimator.getAnimatedFraction());
                    backendActionBar.setBackgroundColor(Utils.interpolateColors(fraction,
                            CurrentColor,
                            getResources().getColor(R.color.ActionBarOverBudget)));
                    ActivityActionBar.setBackgroundColor(Utils.interpolateColors(fraction,
                            CurrentActivityColor,
                            getResources().getColor(R.color.ActionBarOverBudget)));
                }
            };
            actionBarAnimator.addUpdateListener(UpdateListener);
            actionBarAnimator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animation.removeAllListeners();
                    backendActionBar.setBackgroundColor(getResources().getColor(R.color.ActionBarOverBudget));
                    ActivityActionBar.setBackgroundColor(getResources().getColor(R.color.ActionBarOverBudget));
                }
            });
            actionBarAnimator.setInterpolator(new CycleInterpolator(0.75f));
            actionBarAnimator.start();
            if (animated) actionBarAnimator.start();
            else actionBarAnimator.end();
        }

        if (dashboardEditPanel != null) {
            ((TextView) dashboardEditPanel.findViewById(R.id.DashboardEditBalance)).setTextColor(balanceText.getTextColors().getDefaultColor());
        }

        if (balanceState != CurrentState) {
            for (OnBalanceStateChangeListener listener : balanceStateChangeListeners) {
                listener.onBalanceStateChanged(CurrentState, balanceState);
            }
        }

        if (balanceState == BalanceStateError || CurrentState == BalanceStateError)
            backendCollection.refreshViews();
    }

    private LegacyActionBar.ContextBarWrapper dashboardEditWrapper;
    private LegacyActionBar.ContextBarListener dashboardListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {

        }

        @Override
        public void onContextBarDismissed() {
            dismissDashboardEditor();
            dashboardEditWrapper = null;
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {

        }
    };

    public LegacyActionBar getActionBar() {
        return backendActionBar;
    }

    public void loadGlobalBudget(final SharedPreferences globalPrefs) {

        BigDecimal pendingUsedGlobalBudget = null;
        if (usedGlobalBudget != null) pendingUsedGlobalBudget = usedGlobalBudget;

        // Load budget related values
        try {
            globalBudget = new BigDecimal(globalPrefs.getString(GlobalBudgetKey, "0"));
        }
        catch (NumberFormatException e) {
            globalBudget = new BigDecimal("0");
        }
        usedGlobalBudget = new BigDecimal(globalPrefs.getString(UsedGlobalBudgetKey, "0"));
        checkedOutBudget = new BigDecimal(globalPrefs.getString(CheckedOutBudgetKey, "0"));
        customIncome = new BigDecimal(globalPrefs.getString(CustomIncomeKey, "0"));
        lastBudgetResetTime = globalPrefs.getLong(LastBudgetResetTimeKey, System.currentTimeMillis());
        budgetResetType = globalPrefs.getInt(BudgetResetTypeKey, BudgetResetTypeManually);
        budgetResetSubtype = globalPrefs.getInt(BudgetResetSubtypeKey, BudgetResetSubtypeInapplicable);
        budgetResetValue = globalPrefs.getInt(BudgetResetValueKey, 0);

        if (pendingUsedGlobalBudget != null) usedGlobalBudget = usedGlobalBudget.add(pendingUsedGlobalBudget);

        budgetInitialized = true;

    }

    protected void saveGlobalBudget() {
        SharedPreferences.Editor exitState = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).edit();

        if (globalBudget.compareTo(new BigDecimal(0)) == 0) {
            if (budgetResetValue != 0) {
                budgetResetValue = 0;
                exitState.putInt(BudgetResetValueKey, budgetResetValue);
            }
        }

        exitState.putString(GlobalBudgetKey, globalBudget.toString());
        exitState.putString(UsedGlobalBudgetKey, usedGlobalBudget.toString());
        exitState.putString(CheckedOutBudgetKey, checkedOutBudget.toString());
        exitState.putString(CustomIncomeKey, customIncome.toString());
        exitState.apply();
    }

    public void updateReceiptFromActivity(AbstractReceipt receipt) {
        activity.requestHeader(receipt);
        receipt.items = activity.getItems();
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.menu_new_receipt) {
            addNewList();
        }
        if (item.getItemId() == 7032) {
            resetBudget();
        }
        return true;
    }

    public void resetBudget() {
        globalBudget = new BigDecimal("0");
        checkedOutBudget = new BigDecimal("0");
        customIncome = new BigDecimal("0");

        updateBalanceDisplay();
    }

    // This is actually called by the activity's onPause() method
    public void onActivityPaused() {
        if (!activity.isChangingConfigurations()) {
            if (confirmatorUp) finalizeDelete();
            PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).edit().putInt(ExitStateKey, state).apply();
            storage.save(activity);
            storage.saveReceiptAt(0);

            saveGlobalBudget();
        }
    }

    static class ViewHolder {
        TextView title;
        TextView[] lines;
        ProgressBar loader;
        int target;
        AbstractReceipt receipt;
    }

    private SwipeToDeleteListener swipeToDeleteListener = null;

    private class BackendController extends CollectionViewController {

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            if (viewType == ViewTypeScrap) {
                View scrap = inflater.inflate(R.layout.backend_scrap, container, false);

                $.metadata(scrap, ScrapMetadataKey, "");

                prepareViewHolder(scrap);
                LegacyRippleDrawable background = new LegacyRippleDrawable(getActivity());
                background.setShape(LegacyRippleDrawable.ShapeRoundRect);
                background.setSelectedColors(Utils.transparentColor(0.5f, getResources().getColor(android.R.color.holo_blue_light)),
                        Utils.overlayColors(Utils.transparentColor(0.5f, getResources().getColor(android.R.color.holo_blue_light)), LegacyRippleDrawable.DefaultPressedColor));
                background.setNotificationColors(Utils.transparentColor(0.5f, getResources().getColor(android.R.color.holo_orange_dark)),
                        Utils.overlayColors(Utils.transparentColor(0.5f, getResources().getColor(android.R.color.holo_orange_dark)), LegacyRippleDrawable.DefaultPressedColor));
                scrap.findViewById(R.id.ScrapRipple).setBackground(background);




                if (true) {
                    EventTouchListener listener = EventTouchListener.listenerInContext(activity);

                    listener.setDelegate(new EventTouchListener.EventDelegateAdapter() {
                        @Override
                        public void viewDidMove(EventTouchListener listener, View view, float distance) {
                            view.setTranslationX(view.getTranslationX() + distance);

                            float distanceRatio = Math.abs(view.getTranslationX() / getSwipeDistanceThreshold());

                            if (distanceRatio > 1) distanceRatio = 1;
                            distanceRatio = Utils.interpolateValues(distanceRatio, 1, 0.2f);

                            view.setAlpha(distanceRatio);
                        }

                        public boolean viewShouldStartMoving(EventTouchListener listener, View view) {
                            if (selection.size() == 0) {
                                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                backendCollection.requestDisableInteractions();
                                return true;
                            }

                            return false;
                        }

                        @Override
                        public void viewDidBeginSwiping(EventTouchListener listener, final View view, float velocity) {
                            AbstractReceipt target;
                            try {
                                target = (AbstractReceipt) backendCollection.getObjectForView(view);
                            }
                            catch (Exception e) {
                                target = null;
                            }

                            if (target == null) {
                                viewDidCancelSwiping(listener, view);
                                return;
                            }
                            backendCollection.requestEnableInteractions();

                            // The view will continue to move with constant speed
                            if (velocity == 0) {
                                velocity = EventTouchListener.sgn(view.getTranslationX());
                            }

                            float totalDistance =  backendCollection.getWidth() - Math.abs(view.getTranslationX());
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

                            controller.requestBeginTransaction();
                            confirmingReceipt = target;
                            controller.findSectionWithTag(LibrarySectionKey).removeObject(target);
                            if (confirmingReceipt.header != null) { //enable deletion of corrupt receipts
                                usedGlobalBudget = usedGlobalBudget.subtract(confirmingReceipt.header.total.multiply(new BigDecimal(10000 + confirmingReceipt.header.tax).movePointLeft(4)));
                            }
                            updateBalanceDisplay();

                            showDeleteConfirmator(true);

                            final float StartingAlpha = view.getAlpha();
                            final float StartingTranslation = view.getTranslationX();
                            final float Velocity = velocity;
                            final float TotalDistance = totalDistance;
                            final View InnerView = view;

                            backendCollection.setAnimationsEnabled(true);
                            backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
                            backendCollection.setAnchorCondition(null);
                            backendCollection.setDeleteAnimationStride(0);
                            backendCollection.setDeleteAnimationDuration(timeRequired);
                            backendCollection.setDeleteInterpolator(new LinearInterpolator());
//                            backendCollection.setMoveWithLayersEnabled(true);
                            backendCollection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
                                @Override
                                public void playAnimation(View view, Object object, int viewType) {
                                    InnerView.setAlpha(1f);
                                    InnerView.setTranslationX(0f);

                                    InnerView.setLayerType(View.LAYER_TYPE_NONE, null);
                                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                    view.setAlpha(StartingAlpha);
                                    view.setTranslationX(view.getTranslationX() + StartingTranslation);
                                    view.animate().alpha(0f).translationXBy(EventTouchListener.sgn(Velocity) * TotalDistance);
                                }

                                @Override
                                public void resetState(View view, Object object, int viewType) {
                                    view.setAlpha(1f);
                                    view.setLayerType(View.LAYER_TYPE_NONE, null);
                                    backendCollection.setDeleteInterpolator(CollectionView.StandardDeleteInterpolator);
                                }
                            });

                            controller.requestCompleteTransaction();
                        }

                        @Override
                        public void viewDidCancelSwiping(EventTouchListener listener, final View View) {
                            backendCollection.requestEnableInteractions();
                            View.animate().alpha(1f).translationX(0f).withEndAction(new Runnable() {
                                @Override
                                public void run() {
                                    View.setLayerType(android.view.View.LAYER_TYPE_NONE, null);
                                }
                            });
                        }

                        @Override
                        public int getSwipeDistanceThreshold() {
                            return phoneUI ? metrics.widthPixels / 2 : 2 * getResources().getDimensionPixelSize(R.dimen.BackendWidth) / 3;
                        }
                    });

//                    listener.setEnforceTapThreshold(true);

                    scrap.setOnTouchListener(listener);
                }



                return scrap;
            }
            if (viewType == ViewTypeHeader) {
                return inflater.inflate(R.layout.lists_header, container, false);
            }
            if (viewType == ViewTypeDashboardPlaceholder) {
                View placeholder = (landscape && !sidebarMode) ? new View(activity) :
                        new FrameLayout(activity) {
                            public boolean onInterceptTouchEvent(MotionEvent event) {
                                return dashboardContainer.onInterceptTouchEvent(event);
                            }

                            public boolean dispatchTouchEvent(MotionEvent event) {
                                return dashboardContainer.dispatchTouchEvent(event);
                            }

                            public boolean onTouchEvent(MotionEvent event) {
                                return dashboardContainer.onTouchEvent(event);
                            }
                        };

                int height = getResources().getDimensionPixelSize(R.dimen.DashboardHeight);

                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && !sidebarMode) {
                    TypedValue tv = new TypedValue();
                    if (container.getContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
                    {
                        height = TypedValue.complexToDimensionPixelSize(tv.data, metrics);
                    }
                    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, height);
                    placeholder.setLayoutParams(params);
                    return placeholder;
                }

                if (height != 0) {
//                    height += (int)(16 * metrics.density);
                    LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, height);
                    placeholder.setLayoutParams(params);
                }
                else {
                    dashboardContainer.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    dashboardContainer.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                    height = dashboardContainer.getMeasuredHeight();
                    if (height != 0) {
//                        height += (int)(16 * metrics.density);
                        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, height);
                        placeholder.setLayoutParams(params);
                    }
                    else {
                        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT, 400);
                        placeholder.setLayoutParams(params);
                    }
                }
                return placeholder;
            }
            return null;
        }

        @Override
        public void configureView(View view, Object item, int viewType) {
            if (viewType == ViewTypeHeader) {
                if (item.equals(getResources().getString(R.string.SourceLibrary))) {
//                    (view.findViewById(R.id.NewListButton)).setOnClickListener(new OnClickListener() {
//                        @Override
//                        public void onClick(View view) {
//                            addNewList();
//                        }
//                    });
                    int size = activeLists.size() - (confirmatorUp ? 1 : 0);

                    if (size != 1) {
                        ((TextView) view.findViewById(R.id.HeaderTitle)).setText(String.format(getResources().getString(R.string.SourceLibraryText), size));
                    }
                    else {
                        ((TextView) view.findViewById(R.id.HeaderTitle)).setText(getResources().getString(R.string.SourceLibrarySingleText));
                    }
                }
                else {
                    ((TextView) view.findViewById(R.id.HeaderTitle)).setText((String) item);
                }
            }
            if (viewType == ViewTypeScrap) {

                if (false) {
                    if (swipeToDeleteListener == null) {
                        swipeToDeleteListener = new SwipeToDeleteListener(getActivity().getApplicationContext());

                        OnMoveListener moveListener = new OnMoveListener() {

                            float alpha;
                            float deleteDistance = (metrics.widthPixels / rows) / 2f;

                            @Override
                            public void onMove(View view, float distance, boolean initial) {
                                view.setTranslationX(view.getTranslationX() + distance);

                                alpha = Math.max(0.1f, 1 - Math.abs(view.getX() - view.getLeft()) / deleteDistance);
                                view.setAlpha(alpha);

                                if (initial) {
                                    backendCollection.retainView(view);
                                    backendCollection.requestDisableInteractions();

                                    view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                    view.setOnClickListener(null);
                                    view.setOnLongClickListener(null);
                                    view.setClickable(false);
                                    view.setLongClickable(false);
                                    view.setPressed(false);
                                }
                            }
                        };

                        SwipeToDeleteListener.OnReleaseListener releaseListener = new SwipeToDeleteListener.OnReleaseListener() {
                            @Override
                            public void onRelease(final View view) {
                                view.animate()
                                        .translationX(0).alpha(1f)
                                        .setListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                view.animate().setListener(null);

                                                if (backendCollection != null) {
                                                    view.setLayerType(View.LAYER_TYPE_NONE, null);
                                                    view.setOnClickListener(scrapClickListener);
                                                    view.setOnLongClickListener(scrapLongClickListener);
                                                    view.setClickable(true);
                                                    view.setLongClickable(true);
                                                    backendCollection.requestEnableInteractions();
                                                    backendCollection.releaseView(view);
                                                    view.setPressed(false);
                                                }
                                            }
                                        });
                            }
                        };

                        SwipeToDeleteListener.OnDeleteListener deleteListener = new SwipeToDeleteListener.OnDeleteListener() {
                            @Override
                            public void onDelete(final View view, float velocity, float velocityRatio) {

                                float dr = backendCollection.getWidth() - view.getTranslationX();
                                float distanceRatio = dr / view.getWidth();
                                if (distanceRatio < 0) distanceRatio = 0.01f;
                                float timeRatio = Math.signum(velocityRatio) * 300 * distanceRatio * velocityRatio;
                                if (timeRatio > 295f) timeRatio = 295f;

                                view.animate().alpha(0f).translationX(Math.signum(velocityRatio) * backendCollection.getWidth())
                                        .setInterpolator(new DecelerateInterpolator(1.5f))
                                        .setDuration((long) (timeRatio))
                                        .setListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                view.animate().setListener(null);

                                                if (backendCollection != null) {
                                                    view.setLayerType(View.LAYER_TYPE_NONE, null);
                                                    view.setOnClickListener(scrapClickListener);
                                                    view.setOnLongClickListener(scrapLongClickListener);
                                                    view.setClickable(true);
                                                    view.setLongClickable(true);

                                                    view.setPressed(false);
                                                }
                                            }
                                        });


                                backendCollection.requestEnableInteractions();
                                controller.requestBeginTransaction();

                                AbstractReceipt target = (AbstractReceipt) backendCollection.getObjectForView(view);
                                confirmingReceipt = target;
                                controller.findSectionWithTag(LibrarySectionKey).removeObject(target);
                                if (confirmingReceipt.header != null) //enable deletion of corrupt receipts
                                    usedGlobalBudget = usedGlobalBudget.subtract(confirmingReceipt.header.total.multiply(new BigDecimal(10000 + confirmingReceipt.header.tax).movePointLeft(4)));
                                updateBalanceDisplay();

                                showDeleteConfirmator(true);

                                backendCollection.setAnimationsEnabled(true);
                                backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
                                backendCollection.setAnchorCondition(null);
                                backendCollection.setContainerResizeDelay(500);
                                backendCollection.setDeleteAnimationStride(0);
                                backendCollection.setDeleteAnimationDuration(200);
                                backendCollection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
                                    @Override
                                    public void playAnimation(View view, Object object, int viewType) {
                                        view.animate().alpha(0f);
                                    }

                                    @Override
                                    public void resetState(View view, Object object, int viewType) {
                                        view.setAlpha(1f);
                                    }
                                });

                                controller.requestCompleteTransaction();

                            }
                        };

                        SwipeToDeleteListener.EnabledListener enabledListener = new SwipeToDeleteListener.EnabledListener() {
                            @Override
                            public boolean isEnabled() {
                                // handled by setting and removing of the SwipeToDeleteListener
                                return true;
                            }
                        };

                        swipeToDeleteListener.setEnabledListener(enabledListener);
                        swipeToDeleteListener.setOnDeleteListener(deleteListener);
                        swipeToDeleteListener.setOnMoveListener(moveListener);
                        swipeToDeleteListener.setOnReleaseListener(releaseListener);
                        swipeToDeleteListener.setMinimumSwipeDistance((metrics.widthPixels / rows) / 2f);

                    }
                }

                final AbstractReceipt receipt = (AbstractReceipt) item;

                final int target = activeLists.indexOf(receipt);

                decorateView(view, receipt, false, target);
                if (view.isSelected() != receipt.selected) {
                    if (!isRefreshingViews()) {
                        ((LegacyRippleDrawable) view.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingAnimation();
                    }
                    view.setSelected(receipt.selected);
                }
                if (confirmationWrapper != null) {
                    if (!view.isActivated() && !isRefreshingViews()) {
                        ((LegacyRippleDrawable) view.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingAnimation();
                    }
                    view.setActivated(true);
                }
                else {
                    if (view.isActivated() && !isRefreshingViews()) {
                        ((LegacyRippleDrawable) view.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingAnimation();
                    }
                    view.setActivated(false);
                }
                if (view.getParent() != null) {
                    ((ViewGroup) view.getParent()).setClipChildren(false);
                    if (view.getParent().getParent() != null) ((ViewGroup) view.getParent().getParent()).setClipChildren(false);
                }
                view.setOnClickListener(scrapClickListener);
                view.setOnLongClickListener(scrapLongClickListener);
//                if (selection.size() == 0 ) {
//                    view.setOnTouchListener(swipeToDeleteListener);
//                }
//                else {
//                    view.setOnTouchListener(null);
//                }
            }
        }

        @Override
        public void requestCompleteTransaction() {

            if (!sidebarMode) {
                if (backendCollection.getViewForObject(findSectionWithTag(LibraryHeaderKey).getObjectAtIndex(0)) != null) {
                    configureView(backendCollection.getViewForObject(findSectionWithTag(LibraryHeaderKey).getObjectAtIndex(0)),
                            findSectionWithTag(LibraryHeaderKey).getObjectAtIndex(0), ViewTypeHeader);
                }
            }

            if (findSectionWithTag(LibrarySectionKey).getSize() == 0) {
                if (emptyAnimator != null) {
                    emptyAnimator.start();
                    if (!sidebarMode) emptyContainer.setVisibility(View.VISIBLE);
                }
            }
            else {
                if (emptyAnimator != null) {
                    emptyAnimator.cancel();
                    emptyContainer.setVisibility(View.INVISIBLE);
                }
            }

            super.requestCompleteTransaction();
        }
    }

    private OnClickListener scrapClickListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            onReceiptClicked((AbstractReceipt) backendCollection.getObjectForView(view));
        }
    };

    private View.OnLongClickListener scrapLongClickListener = new View.OnLongClickListener() {
        @Override
        public boolean onLongClick(View view) {
            onReceiptLongClicked((AbstractReceipt) backendCollection.getObjectForView(view));
            return true;
        }
    };

    public ViewHolder prepareViewHolder(View view) {
        ViewHolder holder = new ViewHolder();
        holder.title = (TextView) view.findViewById(R.id.ScrapHeader);
        holder.title.setTypeface(Receipt.condensedTypeface());
        holder.lines = new TextView[4];
        holder.lines[0] = (TextView) view.findViewById(R.id.ScrapLine1);
        holder.lines[1] = (TextView) view.findViewById(R.id.ScrapLine2);
        holder.lines[2] = (TextView) view.findViewById(R.id.ScrapLine3);
        holder.lines[3] = (TextView) view.findViewById(R.id.ScrapLine4);
        holder.loader = (ProgressBar) view.findViewById(R.id.ScrapLoader);
        view.setTag(holder);
        return holder;
    }

    public void decorateView(View view, AbstractReceipt receipt, boolean forceLoad, int target) {

        if (!receipt.loadedForDisplay) {
            if (DEBUG) Log.d(TAG, "View could not be decorated because it was not loaded for display!");
            if (forceLoad) {
                try {
                    storage.loadReceipt(receipt, BackendStorage.LoadTypeDisplay);
                }
                catch (IOException e) {
                    // TODO Graceful handle of error
                    e.printStackTrace();
                }
                decorateView(view, receipt, false, target);
            }
            else {
                ViewHolder holder = (ViewHolder) view.getTag();
                holder.title.setText(getString(R.string.TaskLoad));
                for (int i = 0; i < 4; i++) {
                    holder.lines[i].setText("");
                }
                holder.loader.setVisibility(View.VISIBLE);
                holder.title.setTextColor(getResources().getColor(R.color.DashboardText));
                holder.target = target;
                holder.receipt = receipt;
            }
        }
        else {
            ViewHolder holder = (ViewHolder) view.getTag();
            holder.target = target;
            holder.receipt = receipt;
            holder.loader.setVisibility(View.INVISIBLE);
            int itemsLeft = receipt.header.totalItems - receipt.header.itemsCrossed;
            // Old expression
//            boolean budgetExceeded = receipt.header.budget.compareTo(ReceiptActivity.UnlimitedBudget) == 0 ? false :
//                    receipt.header.total.add(receipt.header.total.multiply(new BigDecimal(receipt.header.tax).movePointLeft(4))).compareTo(receipt.header.budget) == 1;
            boolean budgetExceeded = receipt.header.budget.compareTo(ReceiptActivity.UnlimitedBudget) != 0 &&
                    receipt.header.total.add(receipt.header.total.multiply(new BigDecimal(receipt.header.tax).movePointLeft(4))).compareTo(receipt.header.budget) == 1;
            if (TextUtils.isEmpty(receipt.header.name)) {
                if (itemsLeft > 0) {
                    holder.title.setText(String.format(getString(R.string.ItemsLeftMini), itemsLeft));
                }
                else {
                    if (budgetExceeded) {
                        holder.title.setText(getString(R.string.BudgetExceededMini));
                    }
                    else {
                        holder.title.setText(getString(R.string.CanCheckoutMini));
                    }
                    if (receipt.header.totalItems == 0) {
                        holder.title.setText(getString(R.string.NewListMini));
                    }
                }
            }
            else {
                holder.title.setText(receipt.header.name);
            }
            if (budgetExceeded) {
                holder.title.setTextColor(getResources().getColor(R.color.OverBudget));
            }
            else if(itemsLeft == 0 && receipt.header.totalItems > 0) {
                if (balanceState != BalanceStateError)
                    holder.title.setTextColor(getResources().getColor(R.color.HeaderCanCheckout));
                else
                    holder.title.setTextColor(getResources().getColor(R.color.DashboardText));
            }
            else {
                holder.title.setTextColor(getResources().getColor(R.color.DashboardText));
            }
            int lines = receipt.items.size() > 4 ? 4 : receipt.items.size();
            for (int i = 0; i < lines; i++) {
                holder.lines[i].setText(receipt.items.get(i).name);
                if (receipt.items.get(i).crossedOff) {
                    holder.lines[i].setPaintFlags(holder.lines[i].getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
                }
                else {
                    holder.lines[i].setPaintFlags(holder.lines[i].getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
                }
            }
            for (int i = lines; i < 4; i++) {
                holder.lines[i].setText("");
                holder.lines[i].setPaintFlags(holder.lines[i].getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
            }
            if (DEBUG) Log.d(TAG, "View was decorated!");
        }
    }

    public void setActionBarLightIcons() {
        if (sidebarMode) {
            backendActionBar.findItemWithId(R.id.menu_new_receipt).setResource(R.drawable.content_new_mini_light);
        }
        else {
            backendActionBar.findItemWithId(R.id.MenuEditDashboard).setResource(R.drawable.ic_wallet);
        }
        backendActionBar.findItemWithId(R.id.menu_history).setResource(R.drawable.ic_history);
        backendActionBar.findItemWithId(R.id.menu_settings).setResource(R.drawable.ic_settings_light);
        backendActionBar.setOverflowResource(R.drawable.ic_action_overflow_light);
        backendActionBar.setLogoResource(R.drawable.logo);
    }

    public void setActionBarDarkIcons() {
        if (sidebarMode) {
            backendActionBar.findItemWithId(R.id.menu_new_receipt).setResource(R.drawable.content_new_mini_dark);
        }
        else {
            backendActionBar.findItemWithId(R.id.MenuEditDashboard).setResource(R.drawable.ic_wallet_dark);
        }
        backendActionBar.findItemWithId(R.id.menu_history).setResource(R.drawable.ic_history_dark);
        backendActionBar.findItemWithId(R.id.menu_settings).setResource(R.drawable.ic_settings_dark);
        backendActionBar.setOverflowResource(R.drawable.ic_action_overflow);
        backendActionBar.setLogoResource(R.drawable.logo_dark);
    }

    public void prepareBackend() {

        if (sidebarMode) return;

//    	activity.hideHint();
        content.animate().cancel();
        if (content.getVisibility() == View.VISIBLE) {
            content.setPivotX(content.getWidth()/2);
            content.setPivotY(content.getHeight()/2);
        }
        content.setVisibility(View.INVISIBLE);
        content.setAlpha(1);
        content.setScaleX(1);
        content.setScaleY(1);
        content.setRotation(0);
        content.setX(0);
        backend.setVisibility(View.VISIBLE);
//    	root.setBackgroundResource(R.drawable.dashboard_background);
//    	adapter.notifyDataSetChanged();
    }

    public void onReceiptClicked(final AbstractReceipt receipt) {

        if (selection.size() > 0) {
            onReceiptLongClicked(receipt);
            return;
        }

        if (state != StateBackend && !sidebarMode) {
            return;
        }

        if (sidebarMode) {
            activeList = activeLists.indexOf(receipt);
        }

        if (!receipt.fullyLoaded) {
            // No more background loading, for now
            try {
                BackendStorage.DiskLock.lock();
                //noinspection SynchronizeOnNonFinalField
                synchronized (receipt.filename) {
                    try {
                        storage.loadReceipt(receipt);
                    }
                    catch (IOException exception) {
                        storage.replaceReceiptAtWith(activeLists.indexOf(receipt), storage.newReceipt());
                    }
                }
            }
            finally {
                BackendStorage.DiskLock.unlock();
            }

        }

        if (sidebarMode) {

            final int position = activeLists.indexOf(receipt);

            View expandingReceipt = backendCollection.retainViewForObject(receipt);
            AbstractReceipt collapsedReceipt = activeLists.get(0);
            if (state == StateOpenList) {
                activity.closeEditorAndKeyboard();
                updateReceiptFromActivity(activeLists.get(0));
                storage.saveReceiptAt(0);
            }
            storage.movePositionTo(position, 0);

            controller.requestBeginTransaction();

            controller.findSectionWithTag(LibrarySectionKey).clear();
            controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);
            controller.findSectionWithTag(LibrarySectionKey).removeObjectAtIndex(0);
            backendCollection.setAnchorCondition(null);
            backendCollection.setDeleteAnimationDuration(50);
            backendCollection.endAllAnimations();
            controller.requestCompleteTransaction();

            ((LegacyRippleDrawable) expandingReceipt.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingFlushRequest();
            boolean animationsFlushed = collapseCurrentScrap(collapsedReceipt);

            activity.restoreState(receipt);

            expandScrapFromView(expandingReceipt, receipt, !animationsFlushed);

            if (state == StateBackend) {
                hideDashboardPanel(OpenCloseSidebarAnimationDuration);
            }

            state = StateOpenList;

        }
        else {
            showScrapFromView(backendCollection.getViewForObject(receipt), receipt);
        }

    }

    public void onReceiptLongClicked(final AbstractReceipt receipt) {
        receipt.selected = !receipt.selected;

        if (confirmationWrapper != null) {
            confirmationWrapper.dismiss();
        }

        int selectionSizePre = selection.size();

        if (receipt.selected) {
            selection.add(receipt);
            selectionTotal = selectionTotal.add(receipt.header.total.add(receipt.header.total.multiply(new BigDecimal(receipt.header.tax).movePointLeft(4))));

            if (contextBar == null) {
                contextBar = backendActionBar.createContextMode(contextListener);

                contextBar.addItem(R.id.menu_delete, getString(R.string.ItemDelete), R.drawable.ic_action_delete, false, true);
                contextBar.addItem(R.id.menu_share, getString(R.string.MenuShare), R.drawable.ic_action_share_mini, false, true);

                contextBar.start();
                backendCollection.refreshViews();
            }
        }
        else {
            selection.remove(receipt);
            selectionTotal = selectionTotal.subtract(receipt.header.total.add(receipt.header.total.multiply(new BigDecimal(receipt.header.tax).movePointLeft(4))));
        }

        if (selection.size() == 0) {
            contextBar.dismiss();
        }
        else {
            contextBar.setTitleAnimated(selection.size() + " selected", selection.size() - selectionSizePre);
            contextBar.setSubtitle(ReceiptActivity.currentLocale + selectionTotal.setScale(2, RoundingMode.HALF_EVEN) + " total");
        }

        View receiptView = backendCollection.getViewForObject(receipt);
        if (receiptView != null) {

            controller.requestConfigureView(receiptView, receipt, ViewTypeScrap);
        }

    }

    private ArrayList<AbstractReceipt> selection = new ArrayList<AbstractReceipt>();
    private LegacyActionBar.ContextBarWrapper contextBar;
    private BigDecimal selectionTotal = new BigDecimal(0);
    private LegacyActionBar.ContextBarListener contextListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {
//            dashboardContainer.animate().alpha(0.5f).setStartDelay(0L);
            if (activity != null) {
                activity.dismissContextModes();
            }
        }

        public void onContextBarActivated(LegacyActionBar.ContextBarWrapper wrapper) {
            if (backendCollection != null) {
                backendCollection.setOverScrollEnabled(false);
            }
        }

        @Override
        public void onContextBarDismissed() {
            contextBar = null;
            deselect();
            backendCollection.refreshViews();
            if (backendCollection != null) {
                backendCollection.setOverScrollEnabled(true);
            }
//            dashboardContainer.animate().alpha(1f);
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            if (item.getId() == R.id.menu_delete) {
                confirmationWrapper = backendActionBar.createActionConfirmationContextMode(
                        selection.size() > 1 ? getString(R.string.ConfirmSelectionMultiple, selection.size()) : getString(R.string.ConfirmSelectionSingle, 1),
                        getString(R.string.ActionDelete), R.drawable.ic_action_delete, confirmationListener);
                confirmationWrapper.start();
            }
            if (item.getId() == R.id.menu_share) {
                ArrayList<Uri> files = new ArrayList<Uri>();

                // TODO Backgroundize

                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                intent.setType(Receipt.DriveMimeType);

                for (AbstractReceipt receipt : selection) {
                    try {
                        if (!receipt.fullyLoaded) {
                            storage.loadReceipt(receipt);
                        }
                    }
                    catch (IOException e) { /* nothing to do here */ }
                    files.add(Uri.fromFile(ReceiptCoder.sharedCoder(activity).createShareableFile(receipt)));
                }

                intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
//                activity.startActivity(Intent.createChooser(intent, "Share"));

                IntentListPopover popover = new IntentListPopover(contextBar.obtainAnchorForItemWithID(R.id.menu_share), intent);
                popover.getHeader().setTitle(ReceiptActivity.titleFormattedString("Share"));
                popover.show(activity);
                popover.setOnDismissListener(new Popover.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        if (contextBar != null) contextBar.dismiss();
                    }
                });

//                contextBar.dismiss();
            }
        }
    };

    public void dismissContextModes() {
        if (confirmationWrapper != null) {
            confirmationWrapper.dismiss();
        }
        if (selection.size() > 0) {
            deselect();
        }
    }

    private LegacyActionBar.ContextBarWrapper confirmationWrapper;
    private LegacyActionBar.ContextBarListener confirmationListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {
            if (backendCollection != null) {
                backendCollection.runForEachVisibleView(new CollectionView.ViewRunnable() {
                    @Override
                    public void runForView(View view, Object object, int viewType) {
                        view.setActivated(true);
                    }
                });
            }
        }

        @Override
        public void onContextBarDismissed() {
            confirmationWrapper = null;
            if (backendCollection != null) {
                backendCollection.runForEachVisibleView(new CollectionView.ViewRunnable() {
                    @Override
                    public void runForView(View view, Object object, int viewType) {
                        view.setActivated(false);
                    }
                });
            }
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            if (item.getId() == R.id.ConfirmOK) {
                deleteSelection();
                if (contextBar != null) contextBar.dismissInstantly();
            }

            if (item.getId() != android.R.id.home) confirmationWrapper.dismiss();
        }
    };

    public void deleteSelection() {
        for (AbstractReceipt receipt : selection) {
            usedGlobalBudget = usedGlobalBudget.subtract(receipt.header.total.multiply(new BigDecimal(10000 + receipt.header.tax).movePointLeft(4)));
            storage.deleteReceipt(receipt);
        }
        updateBalanceDisplay();
        controller.requestBeginTransaction();

        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists); // TODO
        if (sidebarMode && state == StateOpenList) {
            controller.findSectionWithTag(LibrarySectionKey).removeObjectAtIndex(0);
        }

        backendCollection.setAnimationsEnabled(true);
        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
        backendCollection.setDeleteAnimator(new CollectionView.ReversibleAnimation() {
            @Override
            public void playAnimation(View view, Object object, int viewType) {
                view.animate().alpha(0f).translationXBy(backendCollection.getWidth() / 2f);
            }

            @Override
            public void resetState(View view, Object object, int viewType) {
                view.setAlpha(1f);
            }
        });
        backendCollection.setDeleteAnimationStride(50);
        backendCollection.setDeleteAnimationDuration(200);
        final AtomicInteger count = new AtomicInteger(0);
        backendCollection.runForEachVisibleView(new CollectionView.ViewRunnable() {
            @Override
            public void runForView(View view, Object object, int viewType) {
                if (view.isSelected())
                    count.set(count.get() + 1);
            }
        });
        backendCollection.setContainerResizeDelay(count.get() * 50 + backendCollection.getDeleteAnimationDuration() + backendCollection.getMoveAnimationDuration());

        controller.requestCompleteTransaction();

        if (activeLists.size() > 0) {
            if (!activeLists.get(0).fullyLoaded) {
                try {
                    BackendStorage.DiskLock.lock();
                    storage.loadReceipt(activeLists.get(0));
                }
                catch (IOException e) {
                    e.printStackTrace();
                }
                finally {
                    BackendStorage.DiskLock.unlock();
                }
            }
            activity.restoreState(activeLists.get(0));
        }
    }

    public void deselect() {
        for (AbstractReceipt receipt : selection) {
            receipt.selected = false;
        }

        selection.clear();
        backendCollection.refreshViews();
        selectionTotal = new BigDecimal(0);
        if (contextBar != null) {
            contextBar.dismiss();
        }
    }

    public void onTagDeleted(Tag tag) {
        for (AbstractReceipt receipt : activeLists) {
            if (receipt.fullyLoaded) {

                for (Item item : receipt.items) {
                    item.tags.remove(tag);
                }
            }
        }
    }

    public void handleHelpPressed() {

        if (state == StateBackend)
            showHelpWithPage(0, false);
        else
            activity.startStoryMode();

    }

    protected void showHelpWithPage(int page, boolean instantly) {

        if (true) {
            BetaUtilities.showUnderConstructionFromView(backend);
            return;
        }

        story = new HelpStory(activity);

        HelpOverlayBuilder page1 = new HelpOverlayBuilder(activity, backendActionBarView);
        page1.setTitle(getString(R.string.BackendAddTitle))
                .setExplanation(getString(R.string.BackendAddDescription));
        page1.setScale(0.66f);

        HelpOverlayBuilder page2 = new HelpOverlayBuilder(activity, root.getWidth()/2 + getResources().getDrawable(R.drawable.backend_scrap_gray).getIntrinsicWidth()/4,
                root.getHeight() - getResources().getDrawable(R.drawable.backend_scrap_gray).getIntrinsicHeight()/2);

        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
            if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
                page2.setCanShareButtonSpace(true);
                page2.setHighlightPosition(root.getWidth()/2 + getResources().getDrawable(R.drawable.backend_scrap_gray).getIntrinsicWidth()/4,
                        root.getHeight() - getResources().getDrawable(R.drawable.backend_scrap_gray).getIntrinsicHeight()/4);
            }
            else {
                page2.setHighlightPosition(root.getWidth()/2 + getResources().getDrawable(R.drawable.backend_scrap_gray).getIntrinsicWidth()/4,
                        root.getHeight() - getResources().getDrawable(R.drawable.backend_scrap_gray).getIntrinsicHeight()/2);
            }
        }
        page2.setTitle(getString(R.string.BackendRemoveTitle));
        page2.setExplanation(getString(R.string.BackendRemoveDescription));
        if (getResources().getConfiguration().smallestScreenWidthDp < 600)
            page2.setScale(0.66f);
        else
            page2.setScale(0.85f);
        page2.setImage(HelpOverlayBuilder.ImageFling);
        page2.setTitleRequiredLineWidth((int) (240 * metrics.density));
        page2.setExplanationMaxLineWidth((int) (240 * metrics.density));

        story.addPages(page1, page2);
        story.setOnSelectPageListener(new OnSelectPageListener() {
            @Override
            public void onSelectPage(int page) {
                currentHelpPage = page;
            }
        });
        story.setOnCloseListener(new OnCloseListener() {
            @Override
            public void onClose(int page) {
                currentHelpPage = -1;
                story = null;
            }
        });
        if (instantly)
            story.startStoryWithPageInstantly(page);
        else
            story.startStoryWithPage(page);
        currentHelpPage = page;

    }

    public void sidebarCreateAndOpenList() {
        AbstractReceipt collapsedReceipt = null;
        if (activeLists.size() > 0) {
            collapsedReceipt = activeLists.get(0);
            if (state == StateOpenList) {
                activity.closeEditorAndKeyboard();
                updateReceiptFromActivity(activeLists.get(0));
                storage.saveReceiptAt(0);
            }
        }
        AbstractReceipt receipt = storage.addNewReceiptTo(0);

        controller.requestBeginTransaction();

        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);
        controller.findSectionWithTag(LibrarySectionKey).removeObjectAtIndex(0);
        backendCollection.setAnchorCondition(null);
        backendCollection.setDeleteAnimationDuration(50);
        backendCollection.setInsertAnimator(new CollectionView.ReversibleAnimation() {
            public void playAnimation(View view, Object object, int viewType) {
                view.setAlpha(0f);
                view.animate().alpha(1f);
            }

            public void resetState(View view, Object object, int viewType) {
            }
        });
        backendCollection.endAllAnimations();
        controller.requestCompleteTransaction();
        backendCollection.setInsertAnimator(insertAnimationStandard);

        ((LegacyRippleDrawable) backendActionBar.getBaseActionBarView().findViewWithId(R.id.menu_new_receipt, true).getBackground()).dismissPendingFlushRequest();

        boolean animationsFlushed = collapseCurrentScrap(collapsedReceipt);

        activity.restoreState(receipt);

        createScrap(animationsFlushed);

        if (state == StateBackend) {
            hideDashboardPanel(OpenCloseSidebarAnimationDuration, new DecelerateInterpolator(1.5f));
        }

        state = StateOpenList;
    }

    public void createScrap(boolean animationsFlushed) {
        if (!animationsFlushed) flushAnimations();

        activity.flushRipples();
        activity.setLabelAnimationsEnabled(false);

        final View ActivityContainer = activityContainer;
        ActivityContainer.bringToFront();
        ActivityContainer.setVisibility(View.VISIBLE);

        ActivityContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        root.setBackgroundColor(0xFF000000);
        ActivityContainer.setScaleX(1);
        ActivityContainer.setScaleY(1);
//        ActivityContainer.setY(0);
        ActivityContainer.setTranslationY(root.getHeight() / 6);
        ActivityContainer.animate().cancel();
        ActivityContainer.setPivotX(ActivityContainer.getWidth());
        ActivityContainer.setPivotY(0);
        ActivityContainer.setAlpha(0);
        ActivityContainer.setRotation(-20);
//        ActivityContainer.setTranslationX(0);
        ActivityContainer.setTranslationX(- root.getWidth() / 2);
        animatedViews.add(ActivityContainer);
        ActivityContainer.animate()
                .alpha(1).rotation(0).translationY(0).translationX(0)
                .setDuration(OpenCloseSidebarAnimationDuration)
                .setStartDelay(0)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .setListener(new AnimatorListenerAdapter(){
                    public void onAnimationEnd(Animator a){
                        animatedViews.remove(ActivityContainer);
                        if (activity == null) return;

                        activity.resumeAnimations();
                        root.setBackgroundColor(0);
                        activity.setLabelAnimationsEnabled(true);

//                        activity.setListVisible(true, true, 100);

                        Utils.ViewUtils.resetViewProperties(ActivityContainer);
                    }
                })
                .start();
    }

    @SuppressWarnings("deprecation")
    public void addNewList() {
        if (sidebarMode) {
            sidebarCreateAndOpenList();
            return;
        }
        flushAnimations();

        final View OldActivityView;
        final ViewGroup animationRoot = (ViewGroup) content.getParent();

        root.setBackgroundColor(getResources().getColor(android.R.color.black));

        if (state == StateBackend) {

            AbstractReceipt receipt = storage.addNewReceiptTo(0);
            activity.restoreState(receipt);

            state = StateOpenList;

            backendCollection.freeze();

            controller.requestBeginTransaction();
            controller.findSectionWithTag(LibrarySectionKey).addObjectToIndex(receipt, 0);

            activity.setLabelAnimationsEnabled(false);

            $(backend).layer(View.LAYER_TYPE_HARDWARE).animate()
                        .property($.ScaleX, .95f)
                        .property($.ScaleY, .95f)
                        .property($.Opacity, .4f)
                        .duration(350)
//                        .interpolator(new AccelerateInterpolator(1.5f))
                    .start()
                    .animate()
                        .duration(250)
                        .visibility(View.INVISIBLE)
                    .start();

            $(totalBottomPanel).animate()
                        .property($.TranslateY, $.dp(4, activity))
                        .delay(400)
                        .duration(100)
                        .interpolator(new DecelerateInterpolator(1.5f))
                    .start()
                    .animate()
                        .property($.TranslateY, 0)
                        .duration(100)
                        .interpolator(new AccelerateInterpolator(1.5f))
                    .start();

            $(content).layer(View.LAYER_TYPE_HARDWARE).animate()
                        .property($.Y, - content.getHeight() - backendActionBar.getBaseActionBarView().getActionBarHeight(), 0)
                        .interpolator(new AccelerateInterpolator(1.5f))
                        .duration(400)
                        .visibility(View.VISIBLE)
                    .start()
                    .animate()
                        .property($.Y, -$.dp(16, activity))
                        .duration(100)
                        .interpolator(new DecelerateInterpolator(1.5f))
                    .start()
                    .animate()
                        .property($.Y, 0)
                        .duration(100)
                        .interpolator(new AccelerateInterpolator(1.5f))
                        .complete(new $.AnimationCallback() {
                            @Override
                            public void run($ collection) {
                                collection.resetProperties()
                                        .layer(View.LAYER_TYPE_NONE);

                                $(backend).resetProperties()
                                        .layer(View.LAYER_TYPE_NONE);

                                activity.resumeAnimations();
                                activity.setLabelAnimationsEnabled(false);

                                backendCollection.thaw();
                                backendCollection.scrollTo(0, 0);

                                backendCollection.setAnimationsEnabled(false);
                                backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
                                controller.requestCompleteTransaction();
                            }
                        })
                    .start();

            prepareActionBar(true);

            return;
        }

        // dead code if the state isn't StateBackend: 99% of the cases where this is called

        if (state != StateBackend) {

            activity.closeEditorAndKeyboard();

            OldActivityView = new View(activity);
            Rect contentRect = new Rect();
            content.getGlobalVisibleRect(contentRect);

            updateReceiptFromActivity(activeLists.get(0));
            storage.saveReceiptAt(0);

            Rect rct = new Rect();
            animationRoot.getWindowVisibleDisplayFrame(rct);
            Bitmap bitmap = Bitmap.createBitmap(animationRoot.getWidth(), animationRoot.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(bitmap);
            animationRoot.draw(bitmapCanvas);
            BitmapDrawable background = new BitmapDrawable(getResources(), bitmap);
            OldActivityView.setBackgroundDrawable(background);
            OldActivityView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            animationRoot.addView(OldActivityView);

            OldActivityView.setPivotX(contentRect.right);
            OldActivityView.setPivotY(contentRect.bottom);

            animatedViews.add(OldActivityView);
            OldActivityView.animate()
                    .alpha(0.4f)
                    .setDuration(400)
                    .setInterpolator(new DecelerateInterpolator(0.75f))
                    .setListener(new AnimatorListenerAdapter() {
                        public void onAnimationEnd(Animator a) {
                            animatedViews.remove(OldActivityView);
                            if (activity == null) return;
                            animationRoot.removeView(OldActivityView);
                        }
                    });
        }
        else {
            OldActivityView = null;
        }

        AbstractReceipt receipt = storage.addNewReceiptTo(0);
        activity.restoreState(receipt);

        state = StateOpenList;

        final Rect rct = new Rect();
        backendCollection.getGlobalVisibleRect(rct);
        backend.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        backendCollection.freeze();

        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).addObjectToIndex(receipt, 0);

        final View ContentView = content;
        content.bringToFront();
        (activity.getContentRoot()).bringToFront();
        content.setVisibility(View.VISIBLE);

        ContentView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        activity.setLabelAnimationsEnabled(false);

        content.setScaleX(1);
        content.setScaleY(1);
        content.setY(0);
        ContentView.animate().cancel();
        ContentView.setPivotX(rct.right);
        ContentView.setPivotY(rct.top);
        ContentView.setAlpha(0);
        ContentView.setRotation(-20);
        ContentView.setX(-rct.exactCenterX());
        animatedViews.add(ContentView);
        ContentView.animate()
                .alpha(1).rotation(0).x(0)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .setListener(new AnimatorListenerAdapter(){
                    public void onAnimationStart(Animator a) {
                        if (activity != null) activity.flushRipples();
                    }

                    public void onAnimationCancel(Animator a) {
                        if (activity == null) return;
                        animationRoot.removeView(OldActivityView);
                    }

                    public void onAnimationEnd(Animator a){
                        animatedViews.remove(ContentView);
                        if (activity == null) return;

                        activity.resumeAnimations();
                        activity.setLabelAnimationsEnabled(false);

                        ContentView.setPivotX(ContentView.getWidth()/2);
                        ContentView.setPivotY(ContentView.getHeight()/2);
//                        activity.showHint();
                        backend.setVisibility(View.INVISIBLE);
                        backend.setScaleX(1f);
                        backend.setScaleY(1f);
                        backendCollection.thaw();
                        backendCollection.scrollTo(0, 0);
                        root.setBackgroundColor(0);
                        backend.setLayerType(View.LAYER_TYPE_NONE, null);
                        content.setLayerType(View.LAYER_TYPE_NONE, null);

                        Utils.ViewUtils.resetViewProperties(backend);
                        Utils.ViewUtils.resetViewProperties(content);

                        backendCollection.setAnimationsEnabled(false);
                        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
                        controller.requestCompleteTransaction();

                        if (confirmationWrapper != null) confirmationWrapper.dismissInstantly();
                        if (contextBar != null) contextBar.dismissInstantly();
                    }
                });

        backend.animate().alpha(0.4f).scaleY(0.95f).scaleX(0.95f)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator(1.5f));


        prepareActionBar(true);
    }

    public void openFile(Intent intent, ReceiptActivity callingActivity) {

//        AbstractReceipt receipt = new AbstractReceipt();
//        ObjectInputStream is = null;
//
//        try {
//            is = new ObjectInputStream(callingActivity.getContentResolver().openInputStream(intent.getData()));
//
//            receipt.header = BackendStorage.ReceiptFileHeader.inflate(is);
//            receipt.items = new ArrayList<Item>();
//            for (int i = 0; i < receipt.header.totalItems; i++) {
//                receipt.items.add(Item.inflate(is));
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        } finally {
//            if (is != null) try {
//                is.close();
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//        }

//        String text = "Open file:\n\t";
//        for (Item item : receipt.items) {
//            text += item.name + "\n\t";
//        }
//
//        Toast.makeText(getActivity(), text, Toast.LENGTH_LONG).show();

        ReceiptCoder.FileError error = new ReceiptCoder.FileError();
        AbstractReceipt receipt = ReceiptCoder.sharedCoder(callingActivity).decodeFile(intent, error);

        if (error.code != ReceiptCoder.ErrorNone) {
            new MessagePopover(error.title, error.description).show(callingActivity);
//            Toast.makeText(callingActivity, error.title + "(" + error.code + ")\n" + error.description, Toast.LENGTH_LONG).show();
            return;
        }

        if (receipt == null) {
            // unable to open at all
            return;
        }

        if (activity == null) {
            // App has been launched from the open file intent
            if (storage == null) storage = BackendStorage.getSharedStorage(callingActivity.getApplicationContext());
            storage.importReceiptToIndex(receipt, 0);
            callingActivity.restoreState(receipt);
            addToUsedGlobalBudget(receipt.header.total);
            state = StateOpenList;
            return;
        }

        final int PreState = state;
        state = StateOpenList;

        flushAnimations();

        final View Content = content;
        final ViewGroup Root = root;

        final Rect ContentRect = new Rect();
        content.getGlobalVisibleRect(ContentRect);

        final View ContentScreenshot = (PreState == StateOpenList) ? Utils.ViewUtils.screenshotView(content) : null;
        if (PreState == StateOpenList) {
            Root.addView(ContentScreenshot, 0, new LayoutParams(Content.getWidth(), Content.getHeight()));
            ContentScreenshot.setY(ContentRect.top);

            updateReceiptFromActivity(activeLists.get(0));
            storage.saveReceiptAt(0);
        }

        storage.importReceiptToIndex(receipt, 0);
        activity.restoreState(receipt);
        addToUsedGlobalBudget(receipt.header.total);

        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists); // TODO

        if (backendCollection != null) backendCollection.setAnimationsEnabled(false);
        controller.requestCompleteTransaction();
        backendCollection.refreshViews();

        content.bringToFront();
        (activity.getContentRoot()).bringToFront();

        activity.setDisabledTouchZone(ContentRect);

        Content.setPivotX(ContentRect.right);
        Content.setPivotY(ContentRect.top);
        Content.setAlpha(0);
        Content.setRotation(-20);
        Content.setX(-ContentRect.exactCenterX());

        Root.setBackgroundColor(0xFF000000);
        if (PreState == StateOpenList) {
            ContentScreenshot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        else {
            backend.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        Content.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animations.add(animator);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                Content.setAlpha(fraction);
                Content.setRotation(Utils.interpolateValues(fraction, -20, 0));
                Content.setX(Utils.interpolateValues(fraction, -ContentRect.exactCenterX(), 0));

                if (PreState == StateOpenList) {
                    ContentScreenshot.setAlpha(Utils.interpolateValues(fraction, 1f, 0.4f));
                }
                else {
                    backend.setAlpha(Utils.interpolateValues(fraction, 1f, 0.4f));
                }

                if (activity != null) activity.setDisabledTouchZone(null);
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            public void onAnimationStart(Animator animator) {
                Content.setVisibility(View.VISIBLE);
                if (activity != null) activity.setLabelAnimationsEnabled(false);
                if (PreState == StateBackend) {
                    prepareActionBar(true);
                }
            }
            @Override
            public void onAnimationEnd(Animator animation) {
                animations.remove(animation);
                if (activity != null) activity.setLabelAnimationsEnabled(true);

                if (PreState == StateOpenList) {
                    Root.removeView(ContentScreenshot);
                }
                else {
                    backend.setVisibility(View.INVISIBLE);
                }
                Content.setLayerType(View.LAYER_TYPE_NONE, null);
                Root.setBackgroundColor(0);
            }
        });
        animator.setDuration(400);
        animator.setStartDelay(1000);
        animator.setInterpolator(new DecelerateInterpolator(1.5f));
        animator.start();
    }

    public void sidebarHandleDiscard() {
        // state must be StateOpenList

        activity.closeEditorAndKeyboard();

        controller.requestBeginTransaction();


        usedGlobalBudget = usedGlobalBudget.subtract(activity.getTotal());
        storage.deleteReceiptAt(0);
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);
        backendCollection.setAnchorCondition(null);
        backendCollection.setDeleteAnimationDuration(50);
        backendCollection.setInsertAnimator(new CollectionView.ReversibleAnimation() {
            public void playAnimation(View view, Object object, int viewType) {
                view.setAlpha(0f);
                view.animate().alpha(1f);
            }
            public void resetState(View view, Object object, int viewType) {}
        });
        backendCollection.endAllAnimations();
        controller.requestCompleteTransaction();
        backendCollection.setInsertAnimator(insertAnimationStandard);

        discardScrap();

        if (activeLists.size() > 0) {
            try {
                if (!activeLists.get(0).fullyLoaded)
                    storage.loadReceipt(activeLists.get(0));
            } catch (IOException exception) {
                storage.addNewReceiptTo(0);
            }
            activity.restoreState(activeLists.get(0));
        }
        updateBalanceDisplay(false);

        showDashboardPanel(OpenCloseSidebarAnimationDuration, new AccelerateInterpolator(1.5f));

        state = StateBackend;
    }

    public void discardScrap() {
        flushAnimations();

        activityContainer.setVisibility(View.VISIBLE);
        final View ActivityContainerScreenshot = Utils.ViewUtils.screenshotView(activityContainer);
        root.addView(ActivityContainerScreenshot, new LayoutParams(activityContainer.getWidth(), activityContainer.getHeight()));
        activityContainer.setVisibility(View.INVISIBLE);

        final ViewGroup Root = root;
        final Rect ActivityRect = new Rect();
        activityContainer.getGlobalVisibleRect(ActivityRect);

        ActivityContainerScreenshot.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        activity.setLabelAnimationsEnabled(false);

//        activity.setListVisible(false, false, 0);

        root.setBackgroundColor(0xFF000000);
        ActivityContainerScreenshot.setX(ActivityRect.left);
        ActivityContainerScreenshot.setY(ActivityRect.top);
        ActivityContainerScreenshot.setPivotX(0);
        ActivityContainerScreenshot.setPivotY(activityContainer.getHeight());
        animatedViews.add(ActivityContainerScreenshot);
        ActivityContainerScreenshot.animate()
                .alpha(0f).rotationBy(20).xBy(ActivityRect.width()/2)
                .setDuration(OpenCloseSidebarAnimationDuration)
                .setStartDelay(0)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .setListener(new AnimatorListenerAdapter(){
                    public void onAnimationEnd(Animator a){
                        animatedViews.remove(ActivityContainerScreenshot);
                        if (activity == null) return;

                        activity.resumeAnimations();
                        activity.setLabelAnimationsEnabled(true);
                        Root.setBackgroundColor(0);
                        Root.removeView(ActivityContainerScreenshot);
                    }
                })
                .start();
    }

    @SuppressWarnings("deprecation")
    public void handleDiscard() {

        if (sidebarMode) {
            sidebarHandleDiscard();
            return;
        }

        activity.closeEditorAndKeyboard();

        flushAnimations();

//		if (activeLists.size() == 1) {
//			activity.classicDiscard();
//			activity.invalidateOptionsMenu();
//			return;
//		}

        state = StateBackend;
//		backendList.setScrollingEnabled(true);

        //Set up the animation layer
//    	final View content = root.findViewById(android.R.id.content);

        Rect contentRect = new Rect();
        content.getGlobalVisibleRect(contentRect);

        final Rect rct = new Rect();
        root.getWindowVisibleDisplayFrame(rct);
        Bitmap bitmap = Bitmap.createBitmap(content.getWidth(), content.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas bitmapCanvas = new Canvas(bitmap);
//        bitmapCanvas.clipRect(contentRect);
        content.draw(bitmapCanvas);
        final View ScreenshotView = new View(activity);
        BitmapDrawable background = new BitmapDrawable(getResources(), bitmap);
        ScreenshotView.setBackgroundDrawable(background);
        ScreenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(content.getWidth(), content.getHeight());
        root.addView(ScreenshotView, root.indexOfChild(backend) + 1, params);

        ScreenshotView.setTranslationY(contentRect.top);

        usedGlobalBudget = usedGlobalBudget.subtract(activity.getTotal());

        if (activeLists.size() > 1) {
            try {
                if (!activeLists.get(1).fullyLoaded)
                    storage.loadReceipt(activeLists.get(1));
            } catch (IOException exception) {
                storage.addNewReceiptTo(1);
            }
            activity.restoreStateSilently(activeLists.get(1), true);
            activity.setListVisible(false, true, 0);
        }
        updateBalanceDisplay(false);
        storage.deleteReceiptAt(0);
        backendCollection.setAnimationsEnabled(false);
        activity.setLabelAnimationsEnabled(false);
//    	adapter.notifyDataSetChanged();

        root.setBackgroundColor(getResources().getColor(android.R.color.black));

        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

        backendCollection.setAnimationsEnabled(false);
        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
        controller.requestCompleteTransaction();
//        activity.hideHintInstantly();
        prepareBackend();
        backend.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        backendCollection.freeze();

        ScreenshotView.setPivotX(0f);
        ScreenshotView.setPivotY(contentRect.bottom);
        animatedViews.add(ScreenshotView);
        ScreenshotView.animate()
                .alpha(0f).rotationBy(20).xBy(rct.exactCenterX())
                .setDuration(400)
                .setInterpolator(new AccelerateInterpolator(1f))
                .setListener(new AnimatorListenerAdapter(){
                    @Override
                    public void onAnimationEnd(Animator a){
                        animatedViews.remove(ScreenshotView);
                        ScreenshotView.setBackgroundDrawable(null);
                        if (activity == null) return;
                        activity.setLabelAnimationsEnabled(true);
                        backendCollection.thaw();
                        backend.setLayerType(View.LAYER_TYPE_NONE, null);
                        Utils.ViewUtils.resetViewProperties(backend);
                        root.removeView(ScreenshotView);
                        root.setBackgroundColor(0);
                        backend.setScaleX(1f);
                        backend.setScaleY(1f);
                    }
                });

        backend.setScaleX(0.95f);
        backend.setScaleY(0.95f);
        backend.setAlpha(0.4f);
        backend.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(350)
                .setInterpolator(new AccelerateInterpolator(1.5f));

        prepareActionBar(true);

    }

    public void sidebarHandleCheckout() {

        activity.closeEditorAndKeyboard();

        flushAnimations();

        state = StateBackend;

        activity.checkout(Calendar.getInstance().getTimeInMillis()/1000l);

        checkoutScrap();

        if (activeLists.size() > 1) {
            try {
                if (!activeLists.get(1).fullyLoaded)
                    storage.loadReceipt(activeLists.get(1));
            } catch (IOException exception) {
                storage.addNewReceiptTo(1);
            }
            activity.restoreState(activeLists.get(1));
        }

        storage.deleteReceiptAt(0);

        // The transaction mainly serves to disable interactions
        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
        controller.requestCompleteTransaction();

        showDashboardPanel(OpenCloseSidebarAnimationDuration, new AccelerateInterpolator(1.5f));

    }

    public void checkoutScrap() {
        final View ActivityScreenshot = Utils.ViewUtils.screenshotView(activityContainer);
        final Rect ActivityRect = new Rect();
        activityContainer.getGlobalVisibleRect(ActivityRect);
        activityContainer.setVisibility(View.INVISIBLE);

        final ViewGroup Root = root;
        ActivityScreenshot.setX(ActivityRect.left);
        ActivityScreenshot.setY(ActivityRect.top);
        Root.addView(ActivityScreenshot, new LayoutParams(ActivityRect.width(), ActivityRect.height()));

//        activity.setListVisible(false, false, 0);

        activity.setLabelAnimationsEnabled(false);

        animatedViews.add(ActivityScreenshot);
        Root.setBackgroundColor(0xFF000000);
        ActivityScreenshot.animate()
                .yBy(-Root.getHeight())
                .setDuration(OpenCloseSidebarAnimationDuration)
                .setInterpolator(new AccelerateInterpolator(1.5f))
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        animatedViews.remove(ActivityScreenshot);
                        Root.setBackgroundColor(0);
                        if (activity == null) return;
                        activity.setLabelAnimationsEnabled(true);
                        Root.removeView(ActivityScreenshot);
                    }
                });
    }


    final static boolean USE_NEW_CHECKOUT = true;
    @SuppressWarnings("deprecation")
    public void handleCheckout() {

        if (sidebarMode) {
            sidebarHandleCheckout();
            return;
        }

        activity.closeEditorAndKeyboard();

        flushAnimations();

        // Classic checkout involved creating a new list after the checkout
//        if (activeLists.size() == 1) {
//            activity.classicCheckout();
//            updateReceiptFromActivity(activeLists.get(0));
//            activity.invalidateOptionsMenu();
//            return;
//        }

        state = StateBackend;

        final Rect ContentRect = new Rect();
        content.getGlobalVisibleRect(ContentRect);

        //Make hint changes instant to make sure they don't bog down the animation
        final Rect rct = new Rect();

        final View ScreenshotView = USE_NEW_CHECKOUT ? Utils.ViewUtils.screenshotView(content) : new View(activity);

        if (!USE_NEW_CHECKOUT) {
            root.getWindowVisibleDisplayFrame(rct);
            Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas bitmapCanvas = new Canvas(bitmap);
            bitmapCanvas.clipRect(ContentRect);
            root.draw(bitmapCanvas);
            BitmapDrawable background = new BitmapDrawable(getResources(), bitmap);
            ScreenshotView.setBackgroundDrawable(background);
        }
//        content.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (USE_NEW_CHECKOUT) {
            root.addView(ScreenshotView, root.indexOfChild(backend) + 1, new LayoutParams(ContentRect.width(), ContentRect.height()));
            ScreenshotView.setX(ContentRect.left);
            ScreenshotView.setY(ContentRect.top);
        }
        else {
            root.addView(ScreenshotView);
        }
        //ScreenshotView.setY(ContentRect.top);

//        content.setVisibility(View.INVISIBLE);

        //Restore the background behind it
        activity.findViewById(R.id.AddItemButton).setEnabled(false);
        activity.checkout(Calendar.getInstance().getTimeInMillis() / 1000l);

        if (activeLists.size() > 1) {
            try {
                if (!activeLists.get(1).fullyLoaded)
                    storage.loadReceipt(activeLists.get(1));
            } catch (IOException exception) {
                storage.addNewReceiptTo(1);
            }
            activity.restoreStateSilently(activeLists.get(1), true);
            activity.setListVisible(false, true, 0);
        }

        storage.deleteReceiptAt(0);
//        activity.restoreState(activeLists.get(0));

        activity.setLabelAndCheckoutAnimationsEnabled(false, true);

        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

        backendCollection.setAnimationsEnabled(false);
        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
        controller.requestCompleteTransaction();

//    	activity.hideHintInstantly();
        prepareBackend();

        backendCollection.freeze();

        ScreenshotView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ScreenshotView.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                ScreenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                ScreenshotView.buildLayer();

                backend.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                backend.buildLayer();
                root.setBackgroundColor(0xFF000000);

                //Animate the screenshot
                if (USE_NEW_CHECKOUT) {
                    View historyButton = backendActionBar.getBaseActionBarView().findViewById(R.id.menu_history);
                    Rect historyRect = new Rect();
                    historyButton.getGlobalVisibleRect(historyRect);

                    historyRect.offset(0, -historyRect.height());

                    ValueAnimator animator = ValueAnimator.ofFloat(0f ,1f);

                    final float TargetScaleX = historyButton.getWidth() / ((float) ContentRect.width());
                    final float TargetScaleY = historyButton.getHeight() / ((float) ContentRect.height());

                    final Point StartPoint = new Point(ContentRect.centerX(), ContentRect.centerY());
                    final Point EndPoint = new Point(historyRect.centerX(), historyRect.centerY());

                    final Point StartPointBezier = new Point(StartPoint.x, StartPoint.y + root.getHeight() / 3);
                    final Point EndPointBezier = new Point(EndPoint.x, EndPoint.y + root.getHeight() / 3);

                    final TimeInterpolator Accelerator = new AccelerateInterpolator(1.5f);
                    final TimeInterpolator Decelerator = new DecelerateInterpolator(1.5f);
                    final TimeInterpolator Frictor = new Utils.FrictionInterpolator(1.5f);

                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animation) {
                            float fraction = Frictor.getInterpolation(animation.getAnimatedFraction());

                            Utils.ViewUtils.centerViewOnPoint(ScreenshotView,
                                    Utils.bezierX(fraction, StartPoint, StartPointBezier, EndPoint, EndPointBezier),
                                    Utils.bezierY(fraction, StartPoint, StartPointBezier, EndPoint, EndPointBezier));

                            ScreenshotView.setScaleX(Utils.interpolateValues(fraction, 1f, TargetScaleX));
                            ScreenshotView.setScaleY(Utils.interpolateValues(fraction, 1f, TargetScaleY));

//                    if (fraction < 0.25f) {
//                        fraction = Accelerator.getInterpolation(Utils.getIntervalPercentage(fraction, 0f, 0.25f));
//
//                        ScreenshotView.setScaleX(Utils.interpolateValues(fraction, 1f, 1.2f));
//                        ScreenshotView.setScaleY(Utils.interpolateValues(fraction, 1f, 1.2f));
//                    }
//                    else {
//                        fraction = Decelerator.getInterpolation(Utils.getIntervalPercentage(fraction, 0.25f, 1f));
//
//                        ScreenshotView.setScaleX(Utils.interpolateValues(fraction, 1.2f, TargetScaleX));
//                        ScreenshotView.setScaleY(Utils.interpolateValues(fraction, 1.2f, TargetScaleY));
//                    }

                        }
                    });

                    animator.setInterpolator(new LinearInterpolator());
                    animator.setDuration(500);
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            animations.remove(animation);
                            ScreenshotView.setBackgroundDrawable(null);
                            if (activity == null) return;
                            root.removeView(ScreenshotView);
                            activity.findViewById(R.id.AddItemButton).setEnabled(true);
                            backend.setLayerType(View.LAYER_TYPE_NONE, null);
                            root.setBackgroundColor(0);
                            Utils.ViewUtils.resetViewProperties(backend);
                            activity.setLabelAnimationsEnabled(true);
                            backendCollection.thaw();

                            ((LegacyRippleDrawable) backendActionBar.getBaseActionBarView().findViewById(R.id.menu_history).getBackground())
                                    .flashColor(Utils.transparentColor(0.5f, getResources().getColor(R.color.SelectionBar)));
                        }
                    });

                    animations.add(animator);
                    animator.start();
                }
                else {
                    animatedViews.add(ScreenshotView);
                    ScreenshotView.animate()
                            .yBy(-rct.bottom)
                            .setDuration(400)
                            .setInterpolator(new AccelerateInterpolator(1.5f))
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator a) {
                                    animatedViews.remove(ScreenshotView);
                                    ScreenshotView.setBackgroundDrawable(null);
                                    if (activity == null) return;
                                    root.removeView(ScreenshotView);
                                    activity.findViewById(R.id.AddItemButton).setEnabled(true);
                                    backend.setLayerType(View.LAYER_TYPE_NONE, null);
                                    root.setBackgroundColor(0);
                                    Utils.ViewUtils.resetViewProperties(backend);
                                    activity.setLabelAnimationsEnabled(true);
                                }
                            });
                }

                backend.setAlpha(0.4f);
                backend.setScaleX(0.95f);
                backend.setScaleY(0.95f);
                backend.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(450)
                        .setInterpolator(new AccelerateInterpolator(1.5f));

                prepareActionBar(true);
            }
        });
    }

    public void prepareNewReceipt() {

        if (activeLists.get(0) != null) {
            usedGlobalBudget = usedGlobalBudget.subtract(activeLists.get(0).header.total.multiply(new BigDecimal(10000 + activeLists.get(0).header.tax).movePointLeft(4)));
            updateBalanceDisplay();
        }
        storage.deleteReceiptAt(0);
        AbstractReceipt receipt = storage.addNewReceipt();

        activity.restoreState(receipt);

        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

        backendCollection.setAnimationsEnabled(false);
        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
        controller.requestCompleteTransaction();

        activity.invalidateOptionsMenu();

    }

    @SuppressWarnings("deprecation")
    public void expandReceiptFromNullView(AbstractReceipt receipt) {
        if (state == StateOpenList) return;
        int position = activeLists.indexOf(receipt);

        flushAnimations();

        activity.restoreState(receipt);
        storage.movePositionTo(position, 0);

        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

        state = StateOpenList;

        prepareActionBar(true);

        final ViewGroup animationRoot = (ViewGroup) content.getParent();
        animationRoot.getTag();

        final Rect rct = new Rect();
        backendCollection.getGlobalVisibleRect(rct);
        backend.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        backendCollection.freeze();

        content.animate().cancel();
        content.setVisibility(View.VISIBLE);
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        final View ScreenshotView = content;
        content.bringToFront();
        (activity.getContentRoot()).bringToFront();

        activity.setLabelAnimationsEnabled(false);

        ScreenshotView.setPivotX(rct.width()/2);
        ScreenshotView.setPivotY(rct.height()/2);
        ScreenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ScreenshotView.setAlpha(0);
        ScreenshotView.setScaleX(0.5f);
        ScreenshotView.setScaleY(0.5f);
        ScreenshotView.setY(metrics.heightPixels);
        animatedViews.add(ScreenshotView);
        ScreenshotView.animate()
                .alpha(1).scaleX(1).scaleY(1).y(0)
                .setDuration(350)
                .setInterpolator(new DecelerateInterpolator(1.5f))
                .setListener(new AnimatorListenerAdapter(){
                    public void onAnimationCancel(Animator a) {
                        if (activity == null) return;
                        content.setAlpha(1);
                        content.setScaleX(1);
                        content.setScaleY(1);
                        content.setY(0);
                    }
                    @Override
                    public void onAnimationEnd(Animator a){
                        animatedViews.remove(ScreenshotView);
                        if (activity == null) return;
                        backend.setVisibility(View.INVISIBLE);
                        backendCollection.scrollTo(0, 0);
                        content.setVisibility(View.VISIBLE);
                        root.setBackgroundDrawable(null);
                        backend.setLayerType(View.LAYER_TYPE_NONE, null);
                        content.setLayerType(View.LAYER_TYPE_NONE, null);
                        backendCollection.thaw();

                        activity.setLabelAnimationsEnabled(true);

                        backendCollection.setAnimationsEnabled(false);
                        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeNoScroll);
                        controller.requestCompleteTransaction();
                    }
                });
    }

    public void toggleBackendWithActionBarScreenshot(boolean useActionBarScreenshot) {
        if (state == StateOpenList) {
//			showBackend(500);
            if (sidebarMode) {
                collapseBackend();
            }
            else {
                showBackendWithActionbarScreenshot(useActionBarScreenshot);
                activeList = 0;
            }
        }
        else {
            if (backendCollection.getViewForObject(activeLists.get(0)) != null) {
                showScrapFromView(backendCollection.getViewForObject(activeLists.get(0)), activeLists.get(0));
            }
            else {
                expandReceiptFromNullView(activeLists.get(0));
            }
        }
    }

    public void collapseBackend() {

        AbstractReceipt collapsedReceipt = activeLists.get(0);
        activity.closeEditorAndKeyboard();
        updateReceiptFromActivity(activeLists.get(0));
        storage.saveReceiptAt(0);

        controller.requestBeginTransaction();

        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);
        backendCollection.setAnchorCondition(null);
        backendCollection.setDeleteAnimationDuration(50);
        backendCollection.setInsertAnimator(new CollectionView.ReversibleAnimation() {
            public void playAnimation(View view, Object object, int viewType) {
                view.setAlpha(0f);
                view.animate().alpha(1f);
            }

            public void resetState(View view, Object object, int viewType) {
            }
        });
        final int TransactionScrollingMode = backendCollection.getTransactionScrollingMode();
        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeTop);
        controller.requestCompleteTransaction();
        backendCollection.setInsertAnimator(insertAnimationStandard);

        collapseCurrentScrap(collapsedReceipt);

        backendCollection.setTransactionScrollingMode(TransactionScrollingMode);

        state = StateBackend;
        showDashboardPanel(OpenCloseSidebarAnimationDuration);
    }

    final static long OpenCloseAnimationDuration = 400l;
    final static long OpenCloseSidebarAnimationDuration = 400l;

    public void expandScrapFromView(final View CollectionScrap, AbstractReceipt receipt, boolean flushAnimations) {

        ((LegacyRippleDrawable) CollectionScrap.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingFlushRequest();
        if (flushAnimations) flushAnimations();

        final int position = activeLists.indexOf(receipt);

        final CollectionView BackendCollection = backendCollection;
        BackendCollection.retainView(CollectionScrap);

        final CollectionView ItemCollection = (CollectionView) activity.findViewById(R.id.ItemCollection);
        if (ItemCollection != null) {
            ItemCollection.freeze();
        }

        final ViewGroup Content = activityContainer;

        Content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Content.buildLayer();
        root.setBackgroundColor(0xFF000000);

        final ViewGroup Root = root;
        final Utils.ClippedLayout AnimationRoot = new Utils.ClippedLayout(activity);
        Root.addView(AnimationRoot, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        final View Scrap = activity.getLayoutInflater().inflate(R.layout.backend_scrap, root, false);
        prepareViewHolder(Scrap);
        decorateView(Scrap, activeLists.get(0), false, position);
        AnimationRoot.addView(Scrap, CollectionScrap.getWidth(), CollectionScrap.getHeight());

//        final LegacyRippleDrawable Background = new LegacyRippleDrawable(activity);
//        Background.setShape(LegacyRippleDrawable.ShapeRoundRect);
//        Scrap.findViewById(R.id.ScrapRipple).setBackground(Background);

        Scrap.findViewById(R.id.ScrapRipple).setBackground(((LegacyRippleDrawable) CollectionScrap.findViewById(R.id.ScrapRipple).getBackground()).createDelegateDrawable());
        ((DisableableView) CollectionScrap.findViewById(R.id.ScrapRipple)).suspendDrawing();

        Scrap.setOnTouchListener(Utils.ViewUtils.DisablerTouchListener);

        Scrap.setVisibility(View.VISIBLE);
        Content.setVisibility(View.INVISIBLE);
        CollectionScrap.setVisibility(View.INVISIBLE);

        Scrap.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        activityContainer.bringToFront();
        activity.setLabelAnimationsEnabled(false);
        activity.setListVisible(true, false, 0); // TODO

        CollectionScrap.setTranslationX(0f);
//        ((View) CollectionScrap.getParent()).setTranslationX(0f);

//        BackendCollection.freeze();

        OnGlobalLayoutListener backendCollectionLayoutListener = new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                //public void run() {
                BackendCollection.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                animationLayoutListeners.remove(this);

                // ********** Animation constants **********
                final float ScrapMargin = getResources().getDimension(R.dimen.BackendScrapImagePadding);

                float scrapWidth = getResources().getDimensionPixelSize(R.dimen.BackendScrapSize);
                float scrapHeight = getResources().getDimensionPixelSize(R.dimen.BackendScrapHeight);

                final Rect ContentRect = new Rect();
                Content.getGlobalVisibleRect(ContentRect);
                final Rect ScrapRect = new Rect();

                CollectionScrap.setTranslationX(0f);
//                ((View) CollectionScrap.getParent()).setTranslationX(0f);

                CollectionScrap.getGlobalVisibleRect(ScrapRect);
                final View CollectionScrapParent = (View) CollectionScrap.getParent();

                // Fixing the ScrapRect for scraps which aren't fully visible
                final boolean ScrapFullyVisible = ScrapRect.height() == CollectionScrap.getHeight();
                int multiplier = 1;
                if (!ScrapFullyVisible) {

                    if (CollectionScrapParent.getY() <= backendCollection.getScrollY()) {
                        Log.d(TAG, "ScrapRect needs adjustments due to missing top part: " + ScrapRect +", scrollY is " + backendCollection.getScrollY() + ", y is: " + CollectionScrapParent.getY());
                        // The top part of the scrap is missing
                        ScrapRect.top = (int) (ScrapRect.bottom - scrapHeight);
                        multiplier = -1;
                    }
                    else {
                        Log.d(TAG, "ScrapRect needs adjustments due to missing bottom part: " + ScrapRect +", scrollY is " + backendCollection.getScrollY());
                        // The bottom part of the scrap is missing
                        ScrapRect.bottom = (int) (ScrapRect.top + scrapHeight);
                    }
                }

                Log.d(TAG, "ScrapRect: " + ScrapRect);

                activity.flushRipples();

//                long downTime = SystemClock.uptimeMillis();
//                long eventTime = SystemClock.uptimeMillis() + 50;
//                float x = ScrapRect.left + ((LegacyRippleDrawable) CollectionScrap.findViewById(R.id.ScrapRipple).getBackground()).lastXCoordinate();
//                float y = ScrapRect.top + ((LegacyRippleDrawable) CollectionScrap.findViewById(R.id.ScrapRipple).getBackground()).lastYCoordinate();
//                int metaState = 0;
//                MotionEvent motionEvent = MotionEvent.obtain(
//                        downTime,
//                        eventTime,
//                        MotionEvent.ACTION_DOWN,
//                        x,
//                        y,
//                        metaState
//                );
//
//                Scrap.dispatchTouchEvent(motionEvent);
//                Background.setRippleSource(x - ScrapRect.left, y - ScrapRect.top);

                final float ScrapHeightRatio = ContentRect.height() / (scrapHeight - 2 * ScrapMargin);
                final float ScrapWidthRatio = ContentRect.width() / (scrapWidth - 2 * ScrapMargin);

                final float ContentHeightRatio = (scrapHeight - 2 * ScrapMargin) / ContentRect.height();
                final float ContentWidthRatio = (scrapWidth - 2 * ScrapMargin) / ContentRect.width();

                final Point EndPoint = new Point(ContentRect.centerX(), ContentRect.centerY());
                final Point StartPoint = new Point(ScrapRect.centerX(), (int) (ScrapRect.centerY() + (scrapHeight - ScrapRect.height()) / 2f));

                final Point EndBezier = new Point();
//                EndBezier.x = (int) (ContentRect.exactCenterX() +  2 * Math.abs(ScrapRect.exactCenterX() - ContentRect.exactCenterX()));
//                EndBezier.y = (int) (ContentRect.exactCenterY());
                EndBezier.x = EndPoint.x - Math.abs(StartPoint.x - EndPoint.x) / 3;
                EndBezier.y = EndPoint.y;

                final Point StartBezier = new Point();
//                StartBezier.x = (int) (ScrapRect.exactCenterX() + 2 * Math.abs(ScrapRect.exactCenterX() - ContentRect.exactCenterX()));
//                StartBezier.y = (int)(ScrapRect.exactCenterY());
                StartBezier.x = StartPoint.x;
                StartBezier.y = StartPoint.y - (multiplier * ContentRect.height() / 2);

                // BEZIER IS LIKE:
                /*

                        ---*

                  |
                  |
                  *


                 */

                Content.setCameraDistance(5 * metrics.widthPixels);
                Scrap.setCameraDistance(5 * metrics.widthPixels);

                Content.setPivotX(Content.getWidth() / 2f);
                Content.setPivotY(Content.getHeight() / 2f);
                Utils.ViewUtils.resetViewProperties(Content);

                if (!ScrapFullyVisible) {
                    final Rect CollectionRect = new Rect();
                    backendCollection.getGlobalVisibleRect(CollectionRect);

                    AnimationRoot.addDrawArea(ContentRect);
                    AnimationRoot.addDrawArea(CollectionRect);
                }

                Content.setAlpha(0f);

                // ********** Animations **********

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    TimeInterpolator accelerateInterpolator = new AccelerateInterpolator(1.5f);
                    TimeInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
                    float pointX, pointY;
                    float fraction;

                    boolean resetAnimationRootClips;

                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        fraction = valueAnimator.getAnimatedFraction();
                        if (fraction < 0.5f) {
                            fraction = accelerateInterpolator.getInterpolation(fraction * 2f) / 2f;
                        }
                        else {
                            fraction = decelerateInterpolator.getInterpolation((fraction - 0.5f) * 2f) / 2f + 0.5f;
                        }

                        if (fraction > 0.25f) {
                            Content.setAlpha(1f);
                            Scrap.setAlpha(1 - Utils.getIntervalPercentage(fraction, 0.25f, 1f));
                        }

                        if (ScrapFullyVisible) {
                            pointX = Utils.interpolateValues(fraction, StartPoint.x, EndPoint.x);
                            pointY = Utils.interpolateValues(fraction, StartPoint.y, EndPoint.y);
                        }
                        else {
                            pointX = Utils.bezierX(fraction, StartPoint, StartBezier, EndPoint, EndBezier);
                            pointY = Utils.bezierY(fraction, StartPoint, StartBezier, EndPoint, EndBezier);
                        }

                        Content.setVisibility(View.VISIBLE);
                        Utils.ViewUtils.centerViewOnPoint(Scrap, pointX, pointY);
                        Scrap.setScaleX(Utils.interpolateValues(fraction, 1, ScrapWidthRatio));
                        Scrap.setScaleY(Utils.interpolateValues(fraction, 1, ScrapHeightRatio));

                        Utils.ViewUtils.centerViewOnPoint(Content, pointX, pointY - ContentRect.top);
                        Content.setScaleX(Utils.interpolateValues(fraction, ContentWidthRatio, 1));
                        Content.setScaleY(Utils.interpolateValues(fraction, ContentHeightRatio, 1));

                        if (fraction >= 0.5f) {
                            if (!resetAnimationRootClips) {
                                resetAnimationRootClips = true;
                                AnimationRoot.removeClips();

                                Content.setAlpha(1f);
                            }
                        }
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animations.remove(animation);
                        if (activity == null) return;

                        activity.resumeAnimations();
                        activity.setLabelAnimationsEnabled(true);

                        Content.setLayerType(View.LAYER_TYPE_NONE, null);

                        Utils.ViewUtils.resetViewProperties(Content);

                        activity.setDisabledTouchZone(null);
                        Content.bringToFront();
                        (activity.getContentRoot()).bringToFront();
                        if (ItemCollection != null)
                            ItemCollection.thaw();
                        Root.setBackgroundColor(0);
//                        Root.removeView(Scrap);
                        Root.removeView(AnimationRoot);

//                        activity.setListVisible(true, true, 100);

                        ((DisableableView) CollectionScrap.findViewById(R.id.ScrapRipple)).resumeDrawing();
                        CollectionScrap.setVisibility(View.VISIBLE);
                        BackendCollection.releaseView(CollectionScrap);
                    }
                });
                animator.setInterpolator(new LinearInterpolator());
                animator.setDuration(OpenCloseSidebarAnimationDuration);
                animator.start();
                animations.add(animator);

            }
        };

        BackendCollection.getViewTreeObserver().addOnGlobalLayoutListener(backendCollectionLayoutListener);
        animationLayoutListeners.add(backendCollectionLayoutListener);
    }

    public void showScrapFromView(final View CollectionScrap, AbstractReceipt receipt) {
        if (state == StateOpenList) return;

        ((LegacyRippleDrawable) CollectionScrap.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingFlushRequest();

        flushAnimations();

        final int position = activeLists.indexOf(receipt);

        activity.restoreState(receipt);
        storage.movePositionTo(position, 0);

        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

        final CollectionView BackendCollection = backendCollection;
        BackendCollection.retainView(CollectionScrap);

        final CollectionView ItemCollection = (CollectionView) activity.findViewById(R.id.ItemCollection);
        if (ItemCollection != null) {
            ItemCollection.freeze();
        }

        state = StateOpenList;
        backend.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        backend.buildLayer();
        if (USE_OLD_ANIMATIONS) {
            content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            content.buildLayer();
        }
        prepareActionBar(true);
        actionBarRoot.animate().setStartDelay(100);
        totalBottomPanel.animate().setStartDelay(100);
        backendActionBarView.buildLayer();
        root.setBackgroundColor(0xFF000000);

        activity.setLabelAnimationsEnabled(false);

        final ViewGroup Root = root;
        final View Content = content;

        final Utils.ClippedLayout AnimationRoot = new Utils.ClippedLayout(activity);
        $(AnimationRoot).params(new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        $(backend).after(AnimationRoot);
        //Root.addView(AnimationRoot, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        final View Scrap = activity.getLayoutInflater().inflate(R.layout.backend_scrap, root, false);
        prepareViewHolder(Scrap);
        decorateView(Scrap, activeLists.get(0), false, position);
        AnimationRoot.addView(Scrap, CollectionScrap.getWidth(), CollectionScrap.getHeight());
        Scrap.setClickable(true);
//        final LegacyRippleDrawable Background = new LegacyRippleDrawable(activity);
//        Background.setShape(LegacyRippleDrawable.ShapeRoundRect);
//        Scrap.findViewById(R.id.ScrapRipple).setBackground(Background);

        Scrap.findViewById(R.id.ScrapRipple).setBackground(((LegacyRippleDrawable) CollectionScrap.findViewById(R.id.ScrapRipple).getBackground()).createDelegateDrawable());
        ((DisableableView) CollectionScrap.findViewById(R.id.ScrapRipple)).suspendDrawing();

        Scrap.setVisibility(View.VISIBLE);
        CollectionScrap.setVisibility(View.INVISIBLE);

        Scrap.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        BackendCollection.freeze();

        OnGlobalLayoutListener backendCollectionLayoutListener = new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                //public void run() {
                BackendCollection.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                animationLayoutListeners.remove(this);

                // ********** Animation constants **********
                final float ScrapMargin = getResources().getDimension(R.dimen.BackendScrapImagePadding);

                float scrapWidth = getResources().getDimensionPixelSize(R.dimen.BackendScrapSize);
                float scrapHeight = getResources().getDimensionPixelSize(R.dimen.BackendScrapHeight);

                final Rect ContentRect = new Rect();
                Content.getGlobalVisibleRect(ContentRect);
                final Rect ScrapRect = new Rect();
                CollectionScrap.getGlobalVisibleRect(ScrapRect);

                final View CollectionScrapParent = (View) CollectionScrap.getParent();

//                activity.flushRipples();

                //TODO
                // Only interested if the top part of the scrap is missing; the missing bottom part can continue regularly
                final boolean ScrapFullyVisible = ScrapRect.height() == CollectionScrap.getHeight() || CollectionScrapParent.getY() >= backendCollection.getScrollY();
                if (!ScrapFullyVisible) {
                    ScrapRect.top = (int) (ScrapRect.bottom - scrapHeight);

                    // The animation will only play in the areas below the actionBar
                    final Rect ActionBarRect = new Rect();
                    backendActionBar.getBaseActionBarView().getGlobalVisibleRect(ActionBarRect);

                    AnimationRoot.addDrawArea(new Rect(0, ActionBarRect.bottom, metrics.widthPixels, metrics.heightPixels));
                }

                final float ScrapHeightRatio = ContentRect.height() / (scrapHeight - 2 * ScrapMargin);
                final float ScrapWidthRatio = ContentRect.width() / (scrapWidth - 2 * ScrapMargin);

                final float ContentHeightRatio = (scrapHeight - 2 * ScrapMargin) / ContentRect.height();
                final float ContentWidthRatio = (scrapWidth - 2 * ScrapMargin) / ContentRect.width();

                final Point EndPoint = new Point(ContentRect.centerX(), ContentRect.centerY());
                final Point StartPoint = new Point(ScrapRect.centerX(), (int) (ScrapRect.centerY() + (scrapHeight - ScrapRect.height()) / 2f));

                final Point EndBezier = new Point();
//                EndBezier.x = (int) (ContentRect.exactCenterX() +  2 * Math.abs(ScrapRect.exactCenterX() - ContentRect.exactCenterX()));
//                EndBezier.y = (int) (ContentRect.exactCenterY());
                EndBezier.x = EndPoint.x;// - Math.abs(StartPoint.x - EndPoint.x) / 3;
                EndBezier.y = EndPoint.y + ContentRect.height() / 4;

                final Point StartBezier = new Point();
//                StartBezier.x = (int) (ScrapRect.exactCenterX() + 2 * Math.abs(ScrapRect.exactCenterX() - ContentRect.exactCenterX()));
//                StartBezier.y = (int)(ScrapRect.exactCenterY());
                StartBezier.x = StartPoint.x;
                StartBezier.y = StartPoint.y; // + ContentRect.height() / 3;
                if (USE_OLD_ANIMATIONS) Content.setCameraDistance(5 * metrics.widthPixels);
                Scrap.setCameraDistance(5 * metrics.widthPixels);

                // region view mapping

                $ contentProxy = $(Content);
                $ title = contentProxy.find(R.id.HeaderTitle);
                $ titleIcon = contentProxy.find(R.id.NameEditor);
                $ remaining = contentProxy.find(R.id.HeaderCount, R.id.PasteButton);

                $ items = ItemCollection.visibleViews();
                $ mappedItems = $();
                for (int i = 0; i < Math.min(items.length(), 4); i++) {
                    mappedItems.add(items.get(i));
                }
                items = items.not(mappedItems);

                final TimeInterpolator Friction = new Utils.FrictionInterpolator(1f);
                $ scrapHeader = $(CollectionScrap).find(R.id.ScrapHeader);
                Point titleOffset = scrapHeader.offset();
                titleOffset.x += scrapHeader.layout($.PaddingLeft);
                titleOffset.y -= (title.height() - scrapHeader.height()) / 2 + scrapHeader.layout($.PaddingTop);
                title.parent().clips(false).parent().clips(false).parent().clips(false).parent().clips(false).parent().clips(false);
                title.animate()
                        .cubic(titleOffset, new Point(0, 1), title.offset(), new Point(0, 1), $.OriginTopLeft)
                        .interpolator(Friction)
                        .duration(OpenCloseAnimationDuration)
                        .start("backendQueue");

                titleIcon.animate()
                        .cubic(titleOffset, new Point(0, 1), titleIcon.offset(), new Point(0, 1), $.OriginTopLeft)
                        .property($.Opacity, 0, 1)
                        .layer(true)
                        .interpolator(Friction)
                        .duration(OpenCloseAnimationDuration)
                        .start("backendQueue");

                remaining.each((view, index) -> {
                    Point remainingOffset = new Point(titleOffset);
                    remainingOffset.x += $(Scrap).width() - $.dimen(R.dimen.PrimaryKeyline) - view.getWidth();
                    $(view).animate()
                            .cubic(remainingOffset, new Point(0, 1), $(view).offset(), new Point(0, 1), $.OriginTopLeft)
                            .property($.Opacity, 0, 1)
                            .layer(true)
                            .interpolator(Friction)
                            .duration(OpenCloseAnimationDuration)
                            .start("backendQueue");
                });

                final $ Items = items;
                $(ItemCollection).children().children().clips(false);
                mappedItems.each((view, index) -> {
                    $ scrapItem = $(CollectionScrap).find(".$Tag=ScrapLine" + (index + 1));
                    $ item = $(view).find(R.id.ItemTitle);
                    item.parent().parent().clips(false).parent().clips(false);
                    Point itemOffset = scrapItem.offset();
                    itemOffset.x += scrapItem.layout($.PaddingLeft);
                    itemOffset.y -= (item.height() - scrapItem.height()) / 2 + scrapItem.layout($.PaddingTop);
                    item.animate()
                            .cubic(itemOffset, new Point(0, 1), item.offset(), new Point(0, 1), $.OriginTopLeft)
                            .color($.TextColor, scrapItem.textColor(), item.textColor())
                            .interpolator(Friction)
                            .duration(OpenCloseAnimationDuration)
                            .start("backendQueue");

                    item = $(view);
                    $ itemValues = item.find(R.id.QtyTitle).add(item.find(R.id.PriceTitle));
                    final Point ValuePoint = new Point(itemOffset);
                    ValuePoint.x += $(Scrap).width() - $.dimen(R.dimen.PrimaryKeyline) - $.dp(64);
                    itemValues.each((itemValue, _i) -> $(itemValue).animate()
                            .cubic(ValuePoint, new Point(0, 1), $(itemValue).offset(), new Point(0, 1), $.OriginTopLeft)
//                            .color($.TextColor, 0, $(itemValue).textColor())
                            .property($.Opacity, 0, 1)
                            .layer(true)
                            .interpolator(Friction)
                            .duration(OpenCloseAnimationDuration)
                            .start("backendQueue"));

                    $ tags = item.find(R.id.ItemTags);
                    tags.animate()
                            .cubic(itemOffset, new Point(0, 1), tags.offset(), new Point(0, 1), $.OriginTopLeft)
                            .property($.Opacity, 0, 1)
                            .layer(true)
                            .interpolator(Friction)
                            .duration(OpenCloseAnimationDuration)
                            .start("backendQueue");

                    if (index == mappedItems.length() - 1) {
                        Items.each((itemView, i) -> {
                            $ $item = $(itemView);
                            $item.parent().parent().clips(false).parent().clips(false);
                            $ $itemTitle = $item.find(R.id.ItemTitle);
                            $itemTitle.animate()
                                    .cubic(itemOffset, new Point(1, 1), $itemTitle.offset(), new Point(-1, -1), $.OriginTopLeft)
//                                    .color($.TextColor, 0, $itemTitle.textColor())
                                    .property($.Opacity, 0, 1)
                                    .layer(true)
                                    .interpolator(Friction)
                                    .duration(OpenCloseAnimationDuration)
//                                    .update((views, f) -> {
//                                        Rect r = new Rect();
//                                        views.getGlobalVisibleRect(r);
//                                        Log.e(TAG, "UPDATED: (" + views.getX() + ", " + views.getY() + "); bounds " + r);
//                                    })
                                    .start("backendQueue");

                            $ $itemValues = $item.find(R.id.QtyTitle).add($item.find(R.id.PriceTitle));
                            $itemValues.each((itemValue, _i) -> $(itemValue).animate()
                                    .cubic(ValuePoint, new Point(1, 1), $(itemValue).offset(), new Point(-1, -1), $.OriginTopLeft)
//                                    .color($.TextColor, 0, $(itemValue).textColor())
                                    .property($.Opacity, 0, 1)
                                    .layer(true)
                                    .interpolator(Friction)
                                    .duration(OpenCloseAnimationDuration)
                                    .start("backendQueue"));

                            $ $tags = $item.find(R.id.ItemTags);
                            $tags.animate()
                                    .cubic(itemOffset, new Point(1, 1), $tags.offset(), new Point(-1, -1), $.OriginTopLeft)
                                    .property($.Opacity, 0, 1)
                                    .layer(true)
                                    .interpolator(Friction)
                                    .duration(OpenCloseAnimationDuration)
//                                    .complete(set -> {
//                                        Rect r = new Rect();
//                                        $item.get(0).getGlobalVisibleRect(r);
//                                        Log.d(TAG, "Offset is " + r);
//                                    })
                                    .start("backendQueue");

                        });
                    }
                });

                final Drawable ContentBackground = Content.getBackground();
                contentProxy.find(R.id.HeaderBackground).background(null);
                contentProxy.find(R.id.innerList).background(null);
                contentProxy.visibility($.Visible).background(null);


                // endregion

                if (!USE_OLD_ANIMATIONS) $(Scrap).children().not(R.id.ScrapRipple).detach();

                if (USE_OLD_ANIMATIONS) {
                    Content.setPivotX(Content.getWidth() / 2f);
                    Content.setPivotY(Content.getHeight() / 2f);
                }
                Utils.ViewUtils.resetViewProperties(Content);

                if (USE_OLD_ANIMATIONS) Content.setAlpha(0f);

                // ********** Animations **********

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    boolean flipped = false;
                    TimeInterpolator accelerateInterpolator = new AccelerateInterpolator(1.5f);
                    TimeInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
                    float pointX, pointY;
                    float fraction;

                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        fraction = valueAnimator.getAnimatedFraction();
                        if (fraction < 0.5f) {
                            fraction = accelerateInterpolator.getInterpolation(fraction * 2f) / 2f;
                        } else {
                            fraction = decelerateInterpolator.getInterpolation((fraction - 0.5f) * 2f) / 2f + 0.5f;
                        }

                        if (ScrapFullyVisible) {
                            pointX = Utils.interpolateValues(fraction, StartPoint.x, EndPoint.x);
                            pointY = Utils.interpolateValues(fraction, StartPoint.y, EndPoint.y);
                        }
                        else {
                            pointX = Utils.bezierX(fraction, StartPoint, StartBezier, EndPoint, EndBezier);
                            pointY = Utils.bezierY(fraction, StartPoint, StartBezier, EndPoint, EndBezier);
                        }


                        if (false) {
                            if (fraction < 0.5f) {
                                Utils.ViewUtils.centerViewOnPoint(Scrap, pointX, pointY);

                                Scrap.setRotationY(Utils.interpolateValues(fraction, 0, 180));
                                Scrap.setScaleX(Utils.interpolateValues(fraction, 1, ScrapWidthRatio));
                                Scrap.setScaleY(Utils.interpolateValues(fraction, 1, ScrapHeightRatio));
                            }
                            else {
                                if (!flipped) {
                                    flipped = true;
                                    Content.setVisibility(View.VISIBLE);
                                    Root.removeView(Scrap);
                                }
                                Utils.ViewUtils.centerViewOnPoint(Content, pointX, pointY - ContentRect.top);

                                Content.setRotationY(Utils.interpolateValues(fraction, -180, 0));
                                Content.setScaleX(Utils.interpolateValues(fraction, ContentWidthRatio, 1));
                                Content.setScaleY(Utils.interpolateValues(fraction, ContentHeightRatio, 1));
                            }
                        }
                        else {
                            if (USE_OLD_ANIMATIONS) Content.setVisibility(View.VISIBLE);
                            Utils.ViewUtils.centerViewOnPoint(Scrap, pointX, pointY);
                            Scrap.setScaleX(Utils.interpolateValues(fraction, 1, ScrapWidthRatio));
                            Scrap.setScaleY(Utils.interpolateValues(fraction, 1, ScrapHeightRatio));
//                            Scrap.setAlpha(1 - fraction);

                            if (USE_OLD_ANIMATIONS) {
                                Utils.ViewUtils.centerViewOnPoint(Content, pointX, pointY - ContentRect.top);
                                Content.setScaleX(Utils.interpolateValues(fraction, ContentWidthRatio, 1));
                                Content.setScaleY(Utils.interpolateValues(fraction, ContentHeightRatio, 1));
                            }
                        }

                        if (fraction > 0.25f) {
                            if (USE_OLD_ANIMATIONS) {
                                Content.setAlpha(1f);
                                Scrap.setAlpha(1 - Utils.getIntervalPercentage(fraction, 0.25f, 1f));
                            }
                        }

                        backend.setAlpha(Utils.interpolateValues(fraction, 1f, 0.4f));
                        backend.setScaleY(Utils.interpolateValues(fraction, 1f, 0.95f));
                        backend.setScaleX(Utils.interpolateValues(fraction, 1f, 0.95f));

                        Scrap.setAlpha(1);
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animations.remove(animation);
                        if (activity == null) return;

                        activity.resumeAnimations();
                        activity.setLabelAnimationsEnabled(true);

                        BackendCollection.thaw();
                        backend.setVisibility(View.INVISIBLE);

                        backend.setLayerType(View.LAYER_TYPE_NONE, null);
                        backend.setScaleX(1f);
                        backend.setScaleY(1f);
                        backend.setAlpha(1f);
                        Content.setBackground(ContentBackground);
                        Content.setLayerType(View.LAYER_TYPE_NONE, null);

                        contentProxy.find(R.id.HeaderBackground).backgroundColor($.color(R.color.ReceiptBackground));
                        contentProxy.find(R.id.innerList).backgroundColor($.color(R.color.ReceiptBackground));

                        Utils.ViewUtils.resetViewProperties(Content);

                        activity.setDisabledTouchZone(null);
                        content.bringToFront();
                        contentRoot.setVisibility(View.VISIBLE);
                        (activity.getContentRoot()).bringToFront();
                        if (ItemCollection != null) {
                            ItemCollection.thaw();
                        }
                        Root.setBackgroundColor(0);
                        Root.removeView(AnimationRoot);
                        Root.removeView(Scrap);

                        CollectionScrap.setVisibility(View.VISIBLE);
                        ((DisableableView) CollectionScrap.findViewById(R.id.ScrapRipple)).resumeDrawing();
                        BackendCollection.releaseView(CollectionScrap);

                        controller.requestCompleteTransaction();
                    }
                });
                animator.setInterpolator(new LinearInterpolator());
                animator.setDuration(OpenCloseAnimationDuration);
                animator.start();
                animations.add(animator);

            }
        };

        BackendCollection.getViewTreeObserver().addOnGlobalLayoutListener(backendCollectionLayoutListener);
        animationLayoutListeners.add(backendCollectionLayoutListener);

    }

    public void showDashboardPanel(long duration) {
        showDashboardPanel(duration, new Utils.FrictionInterpolator(1.5f));
    }

    public void showDashboardPanel(long duration, TimeInterpolator interpolator) {
        dashboardPanelContainer.setVisibility(View.VISIBLE);
        dashboardPanelContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        dashboardPanelContainer.buildLayer();
        dashboardPanelContainer.setAlpha(0.4f);

        root.setBackgroundColor(0xFF000000);

        dashboardPanelContainer.animate().alpha(1f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animatedViews.remove(dashboardPanelContainer);

                        root.setBackgroundColor(0);

                        dashboardPanelContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                    }
                })
                .start();

        animatedViews.add(dashboardPanelContainer);
    }

    public void hideDashboardPanel(long duration) {
        hideDashboardPanel(duration, new Utils.FrictionInterpolator(1.5f));
    }

    public void hideDashboardPanel(long duration, TimeInterpolator interpolator) {
        dashboardPanelContainer.setVisibility(View.VISIBLE);
        dashboardPanelContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        dashboardPanelContainer.buildLayer();
        dashboardPanelContainer.setAlpha(1);

        dashboardPanelContainer.animate().alpha(0.4f)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animatedViews.remove(dashboardPanelContainer);

                        dashboardPanelContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                        dashboardPanelContainer.setVisibility(View.INVISIBLE);
                    }
                })
                .start();

        animatedViews.add(dashboardPanelContainer);
    }

    public boolean collapseCurrentScrap(AbstractReceipt collapsedReceipt) {
        return collapseCurrentScrap(false, collapsedReceipt);
    }

    public boolean collapseCurrentScrap(boolean scrolling, AbstractReceipt collapsedReceipt) {
        if (state == StateBackend) return false;

        flushAnimations();

        final CollectionView ItemCollection = (CollectionView) activity.findViewById(R.id.ItemCollection);
        ItemCollection.smoothScrollTo(0, ItemCollection.getScrollY());
        ItemCollection.setVerticalScrollBarEnabled(false);
        ItemCollection.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ItemCollection.setScrollbarFadingEnabled(false);
        ItemCollection.freeze();

        if (scrolling) {
            backendCollection.smoothScrollTo(0, 0);
        }

//        final ViewGroup AnimationRoot = root;


        final Utils.ClippedLayout AnimationRoot = new Utils.ClippedLayout(activity);
        root.addView(AnimationRoot, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);

        final View CollectionScrap = backendCollection.retainViewForObject(collapsedReceipt);
        final View Scrap = activity.getLayoutInflater().inflate(R.layout.backend_scrap, AnimationRoot, false);
        prepareViewHolder(Scrap);
        controller.configureView(Scrap, collapsedReceipt, ViewTypeScrap);
//        final View Scrap = Utils.ViewUtils.screenshotView(CollectionScrap, CollectionScrap.getWidth(), CollectionScrap.getHeight());
//        AnimationRoot.addView(Scrap, 1, new LayoutParams(getResources().getDimensionPixelSize(R.dimen.BackendScrapSize), getResources().getDimensionPixelSize(R.dimen.BackendScrapHeight)));
        AnimationRoot.addView(Scrap, new LayoutParams(getResources().getDimensionPixelSize(R.dimen.BackendScrapSize), getResources().getDimensionPixelSize(R.dimen.BackendScrapHeight)));

        final Rect ActivityRect = new Rect();
        activityContainer.getGlobalVisibleRect(ActivityRect);
        if (DEBUG_SIDEBAR) Log.d(TAG, "Activity container coordinates are: " + ActivityRect);
        final View ScrapScreenshot = Utils.ViewUtils.screenshotView(activityContainer);
        AnimationRoot.addView(ScrapScreenshot, ActivityRect.width(), ActivityRect.height());

        activity.dismissContextModes(false);

        Scrap.setOnTouchListener(Utils.ViewUtils.DisablerTouchListener);

        ScrapScreenshot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Scrap.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        Scrap.setVisibility(View.INVISIBLE);
        CollectionScrap.setVisibility(View.INVISIBLE);

        activityContainer.setVisibility(View.INVISIBLE);

        activity.setLabelAnimationsEnabled(false);

//        activity.setListVisible(false, false, 0);

        OnGlobalLayoutListener scrapGlobalLayoutListener = new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                Scrap.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                animationLayoutListeners.remove(this);

                // ********** Animation constants **********
                final float ScrapMargin = getResources().getDimension(R.dimen.BackendScrapImagePadding);

                float scrapWidth = getResources().getDimensionPixelSize(R.dimen.BackendScrapSize);
                float scrapHeight = getResources().getDimensionPixelSize(R.dimen.BackendScrapHeight);

//                final Rect ActivityRect = new Rect();
//                root.findViewById(R.id.ActivityContainer).getGlobalVisibleRect(ActivityRect);
                final Rect ScrapRect = new Rect();
                CollectionScrap.getGlobalVisibleRect(ScrapRect);

                // If not scrolling, the CollectionScrap's rect's coordinates must be adjusted to account for the CollectionView scroll position
                int[] location = new int[2];
                backendCollection.getLocationOnScreen(location);

                ScrapRect.top = (location[1] + dashboardContainer.getHeight() + 1 - backendCollection.getScrollY()); // HACKY Static positioning
                // if the top is too far up, the animation ends up not making sense, so ensure there is a negative limit to the top value
                if (ScrapRect.top < location[1] - scrapHeight) {
                    ScrapRect.top = (int) (location[1]  - scrapHeight);
                }
                ScrapRect.bottom = (int) (ScrapRect.top + scrapHeight);

                final boolean BezierEnabled = ScrapRect.top < location[1];

                final float ScrapHeightRatio = ActivityRect.height() / (scrapHeight - 2 * ScrapMargin);
                final float ScrapWidthRatio = ActivityRect.width() / (scrapWidth - 2 * ScrapMargin);

                final float ContentHeightRatio = (scrapHeight - 2 * ScrapMargin) / ActivityRect.height();
                final float ContentWidthRatio = (scrapWidth - 2 * ScrapMargin) / ActivityRect.width();

                final Point StartPoint = new Point(ActivityRect.centerX(), ActivityRect.centerY());
                final Point EndPoint = new Point(ScrapRect.centerX(), (int) (ScrapRect.centerY() + (scrapHeight - ScrapRect.height()) / 2f));

                // Identical to startPoint?
//                final Point ContentStartPoint = new Point(ActivityRect.centerX(), ActivityRect.centerY());
//                final Point ContentEndPoint = new Point(ScrapRect.centerX(), (int) ((ScrapRect.centerY() + (scrapHeight - ScrapRect.height()) / 2f)));

                final Point StartBezier = new Point();
//                StartBezier.x = (int) (ActivityRect.exactCenterX() -  2 * Math.abs(ScrapRect.exactCenterX() - ActivityRect.exactCenterX()));
//                StartBezier.y = (int) (ActivityRect.exactCenterY());
                StartBezier.x = StartPoint.x - Math.abs(StartPoint.x - EndPoint.x) / 3;
                StartBezier.y = StartPoint.y;

                final Point EndBezier = new Point();
//                EndBezier.x = (int) (ScrapRect.exactCenterX() - 2 * Math.abs(ScrapRect.exactCenterX() - ActivityRect.exactCenterX()));
//                EndBezier.y = (int)(ScrapRect.exactCenterY());
                EndBezier.x = EndPoint.x;
                EndBezier.y = EndPoint.y + ActivityRect.height() / 2;

                ScrapScreenshot.setCameraDistance(5 * metrics.widthPixels);
                Scrap.setCameraDistance(5 * metrics.widthPixels);
                Scrap.setVisibility(View.VISIBLE);

                // ********** Animations **********

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    TimeInterpolator accelerateInterpolator = new AccelerateInterpolator(1.5f);
                    TimeInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
                    float pointX, pointY;
                    float fraction;

                    boolean resetAnimationRootClips = false;

                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        fraction = valueAnimator.getAnimatedFraction();
                        if (fraction < 0.5f) {
                            fraction = accelerateInterpolator.getInterpolation(fraction * 2f) / 2f;
                        }
                        else {
                            fraction = decelerateInterpolator.getInterpolation((fraction - 0.5f) * 2f) / 2f + 0.5f;
                        }

                        if (fraction > 0.5f) {
                            if (!resetAnimationRootClips) {
                                resetAnimationRootClips = true;

                                if (BezierEnabled) {
                                    final Rect CollectionRect = new Rect();
                                    backendCollection.getGlobalVisibleRect(CollectionRect);
                                    AnimationRoot.addDrawArea(ActivityRect);
                                    AnimationRoot.addDrawArea(CollectionRect);

                                    AnimationRoot.invalidate();

                                    Log.e(TAG, "Animation clips have been set in place!");
                                }
                            }
                        }

                        if (BezierEnabled) {
                            pointX = Utils.bezierX(fraction, StartPoint, StartBezier, EndPoint, EndBezier);
                            pointY = Utils.bezierY(fraction, StartPoint, StartBezier, EndPoint, EndBezier);
                        }
                        else {
                            pointX = Utils.interpolateValues(fraction, StartPoint.x, EndPoint.x);
                            pointY = Utils.interpolateValues(fraction, StartPoint.y, EndPoint.y);
                        }

                        if (fraction > 0.5f) {
                            ScrapScreenshot.setAlpha(2 - 2 * fraction);
                        }
                        if (fraction > 0.25f) {
                            float alpha = Utils.getIntervalPercentage(fraction, 0.25f, 0.75f);
                            Scrap.setAlpha(alpha > 1f ? 1f : alpha);
                        }
//                        Utils.ViewUtils.centerViewOnPoint(ScrapScreenshot,
//                                Utils.interpolateValues(fraction, ContentStartPoint.x, ContentEndPoint.x),
//                                Utils.interpolateValues(fraction, ContentStartPoint.y, ContentEndPoint.y));
                        Utils.ViewUtils.centerViewOnPoint(ScrapScreenshot, pointX, pointY);
                        ScrapScreenshot.setScaleX(Utils.interpolateValues(fraction, 1, ContentWidthRatio));
                        ScrapScreenshot.setScaleY(Utils.interpolateValues(fraction, 1, ContentHeightRatio));
                        Utils.ViewUtils.centerViewOnPoint(Scrap, pointX, pointY);
                        Scrap.setScaleX(Utils.interpolateValues(fraction, ScrapWidthRatio, 1));
                        Scrap.setScaleY(Utils.interpolateValues(fraction, ScrapHeightRatio, 1));

                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animations.remove(animation);
                        if (activity == null) return;

                        activity.setLabelAnimationsEnabled(true);

                        ScrapScreenshot.setLayerType(View.LAYER_TYPE_NONE, null);
                        Scrap.setLayerType(View.LAYER_TYPE_NONE, null);

//                        dashboardContainer.setTranslationY(0f);
//                        dashboardShadow.setTranslationY(0f);

                        AnimationRoot.removeView(Scrap);
                        AnimationRoot.removeView(ScrapScreenshot);

                        activity.setDisabledTouchZone(null);
                        content.bringToFront();
                        contentRoot.setVisibility(View.VISIBLE);
//                        content.setVisibility(View.INVISIBLE);
                        Utils.ViewUtils.resetViewProperties(ScrapScreenshot);
//                        (activity.getContentRoot()).bringToFront();
                        ItemCollection.thaw();

                        CollectionScrap.setVisibility(View.VISIBLE);
                        backendCollection.releaseView(CollectionScrap);
//
                        ItemCollection.setVerticalScrollBarEnabled(true);
                        ItemCollection.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                        ItemCollection.setScrollbarFadingEnabled(true);

                        root.removeView(AnimationRoot);
                    }
                });
                animator.setInterpolator(new LinearInterpolator());
                animator.setDuration(OpenCloseSidebarAnimationDuration);
                animator.start();
                animations.add(animator);

//                Root.setBackgroundColor(0xFF000000);
            }
        };

        Scrap.getViewTreeObserver().addOnGlobalLayoutListener(scrapGlobalLayoutListener);
        animationLayoutListeners.add(scrapGlobalLayoutListener);

        return true;
    }

//    final static boolean NO_ACTIONBAR_SCREENSHOT = false;
    final static boolean USE_SCRAP_TRANSPARENCY = false;

    public void showBackendWithActionbarScreenshot(boolean useActionBarScreenshot) {
        // ********** Internal state **********
        if (state == StateBackend) {
            return;
        }

        final boolean NO_ACTIONBAR_SCREENSHOT = !useActionBarScreenshot; // to guarantee a consistent framerate, an actionbar screenshot is used when this animation runs as a result of the user pressing the back button.

        ((LegacyRippleDrawable) actionBarRoot.findViewById(LegacyActionBarView.BackID).getBackground()).dismissPendingFlushRequest();
        flushAnimations();

        backendCollection.setVerticalScrollBarEnabled(false);
        backendCollection.setOverScrollMode(View.OVER_SCROLL_NEVER);
        backendCollection.setScrollbarFadingEnabled(false);
        backendCollection.freeze();
        final CollectionView ItemCollection = (CollectionView) activity.findViewById(R.id.ItemCollection);
        ItemCollection.smoothScrollTo(0, ItemCollection.getScrollY());
        ItemCollection.setVerticalScrollBarEnabled(false);
        ItemCollection.setOverScrollMode(View.OVER_SCROLL_NEVER);
        ItemCollection.setScrollbarFadingEnabled(false);
        ItemCollection.freeze();

        ((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar)).destroyAllCustomViews();

        activity.closeEditorAndKeyboard();

        state = StateBackend;
        updateReceiptFromActivity(activeLists.get(0));
        storage.saveReceiptAt(0);

        controller.requestBeginTransaction();
        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

        backendCollection.setAnimationsEnabled(false);
        backendCollection.setAnchorCondition(null);
        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeTop);
        controller.requestCompleteTransaction();

        Rect disabledRect = new Rect();
        ((View) content.getParent()).getGlobalVisibleRect(disabledRect);
        //The TouchBlocker eats up errant click and touch events on the animated view
        activity.setDisabledTouchZone(disabledRect);

        int top = backendCollection.getObjectMinimumScrollPosition(activeLists.get(0));

        backendCollection.scrollTo(0, top);

        // ********** Animation setup **********

        final ViewGroup Root = root;
        ((View) content.getParent()).animate().cancel();
        content.animate().cancel();

        // ***** SCRAP ******

//        final View Scrap = activity.getLayoutInflater().inflate(R.layout.backend_scrap, root, false);
//        prepareViewHolder(Scrap);
//        decorateView(Scrap, activeLists.get(0), false, 0);
        final View CollectionScrap = backendCollection.retainViewForObject(activeLists.get(0));
        controller.configureView(CollectionScrap, activeLists.get(0), ViewTypeScrap);
//        controller.configureView(CollectionScrap, activeLists.get(0), ViewTypeScrap);
        final View Scrap = Utils.ViewUtils.screenshotView(CollectionScrap, CollectionScrap.getWidth(), CollectionScrap.getHeight());
        // The scrap is always visible, so there is no need for an animationRoot in this case
        root.addView(Scrap, 1, new LayoutParams(CollectionScrap.getWidth(), CollectionScrap.getHeight()));

        // ***** ACTION BAR ******

        final Rect ActionBarRect = new Rect();
        actionBarRoot.getGlobalVisibleRect(ActionBarRect);

        final View ActionBarScreenshot = NO_ACTIONBAR_SCREENSHOT ? actionBarRoot : new View(getActivity());
        if (!NO_ACTIONBAR_SCREENSHOT) {
            Bitmap bitmap = Bitmap.createBitmap(actionBarRoot.getWidth(), actionBarRoot.getHeight(), Bitmap.Config.ARGB_8888);
            actionBarRoot.draw(new Canvas(bitmap));
            ActionBarScreenshot.setBackgroundDrawable(new BitmapDrawable(bitmap));
            root.addView(ActionBarScreenshot, actionBarRoot.getWidth(), actionBarRoot.getHeight());
            ActionBarScreenshot.setY(ActionBarRect.top);
        }

        // ****** CONTENT & BACKEND ******
        prepareBackend();
        prepareActionBar(false);

        final View Content = content;
        final View Backend = backend;
        final View TotalBottomPanel = totalBottomPanel;
        TotalBottomPanel.setVisibility(View.VISIBLE);
        TotalBottomPanel.setAlpha(1);
        TotalBottomPanel.setTranslationY(0);
        TotalBottomPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (NO_ACTIONBAR_SCREENSHOT) {
            ActionBarScreenshot.setVisibility(View.VISIBLE);
            ActionBarScreenshot.setAlpha(1f);
            ActionBarScreenshot.setTranslationY(0f);
            activity.getLegacyActionBar().getBaseActionBarView().requestDisableInteractions();
        }

        dashboardContainer.setTranslationY(0f);
        dashboardShadow.setTranslationY(0f);

        final boolean Lollipop = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;

        content.setVisibility(View.VISIBLE);
//        Root.setBackgroundColor(0xFF000000);
        Backend.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Backend.buildLayer();
        Content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//        ActionBarScreenshot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        Scrap.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        Scrap.setVisibility(View.INVISIBLE);
        CollectionScrap.setVisibility(View.INVISIBLE);

        activity.setLabelAnimationsEnabled(false);

        OnGlobalLayoutListener scrapGlobalLayoutListener = new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                //noinspection deprecation
                Scrap.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                animationLayoutListeners.remove(this);

                // ********** Animation constants **********
                final float ScrapMargin = getResources().getDimension(R.dimen.BackendScrapImagePadding);

                float scrapWidth = getResources().getDimensionPixelSize(R.dimen.BackendScrapSize);
                float scrapHeight = getResources().getDimensionPixelSize(R.dimen.BackendScrapHeight);

                final Rect ContentRect = new Rect();
                content.getGlobalVisibleRect(ContentRect);
                final Rect ScrapRect = new Rect();
                CollectionScrap.getGlobalVisibleRect(ScrapRect);

                final float ScrapHeightRatio = ContentRect.height() / (scrapHeight - 2 * ScrapMargin);
                final float ScrapWidthRatio = ContentRect.width() / (scrapWidth - 2 * ScrapMargin);

                final float ContentHeightRatio = (scrapHeight - 2 * ScrapMargin) / ContentRect.height();
                final float ContentWidthRatio = (scrapWidth - 2 * ScrapMargin) / ContentRect.width();

                final Point StartPoint = new Point(ContentRect.centerX(), ContentRect.centerY());
                final Point EndPoint = new Point(ScrapRect.centerX(), (int) (ScrapRect.centerY() + (scrapHeight - ScrapRect.height()) / 2f));

                final Point ContentStartPoint = new Point(ContentRect.centerX(), ContentRect.centerY() - ContentRect.top);
                final Point ContentEndPoint = new Point(ScrapRect.centerX(), (int) ((ScrapRect.centerY() + (scrapHeight - ScrapRect.height()) / 2f) - ContentRect.top));

                final Point StartBezier = new Point();
                StartBezier.x = (int) (ContentRect.exactCenterX() -  2 * Math.abs(ScrapRect.exactCenterX() - ContentRect.exactCenterX()));
                StartBezier.y = (int) (ContentRect.exactCenterY());

                final Point EndBezier = new Point();
                EndBezier.x = (int) (ScrapRect.exactCenterX() - 2 * Math.abs(ScrapRect.exactCenterX() - ContentRect.exactCenterX()));
                EndBezier.y = (int)(ScrapRect.exactCenterY());

                Content.setCameraDistance(5 * metrics.widthPixels);
                Scrap.setCameraDistance(5 * metrics.widthPixels);
                Scrap.setVisibility(View.VISIBLE);

                // ********** Animations **********

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animator.setInterpolator(new LinearInterpolator());
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    boolean flipped = false;
                    TimeInterpolator accelerateInterpolator = new AccelerateInterpolator(1.5f);
                    TimeInterpolator decelerateInterpolator = new DecelerateInterpolator(1.5f);
                    float pointX
                            ,
                            pointY;
                    float fraction;

                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {
                        fraction = valueAnimator.getAnimatedFraction();
                        // This is later implemented by FrictionInterpolator class
                        if (fraction < 0.5f) {
                            fraction = accelerateInterpolator.getInterpolation(fraction * 2f) / 2f;
                        } else {
                            fraction = decelerateInterpolator.getInterpolation((fraction - 0.5f) * 2f) / 2f + 0.5f;
                        }

//                        pointX = Utils.bezierX(fraction, StartPoint, StartBezier, EndPoint, EndBezier);
//                        pointY = Utils.bezierY(fraction, StartPoint, StartBezier, EndPoint, EndBezier);

                        pointX = Utils.interpolateValues(fraction, StartPoint.x, EndPoint.x);
                        pointY = Utils.interpolateValues(fraction, StartPoint.y, EndPoint.y);

                        if (false) {
                            if (fraction < 0.5f) {
                                Utils.ViewUtils.centerViewOnPoint(Content, pointX, pointY - (ContentRect.top) * Utils.interpolateValues(fraction, 1, ContentHeightRatio));

                                Content.setRotationY(-180 * fraction);
                                Content.setScaleX(Utils.interpolateValues(fraction, 1, ContentWidthRatio));
                                Content.setScaleY(Utils.interpolateValues(fraction, 1, ContentHeightRatio));
                            } else {
                                if (!flipped) {
                                    flipped = true;
                                    Content.setVisibility(View.INVISIBLE);
                                    Utils.ViewUtils.resetViewProperties(Content);

                                    Scrap.setVisibility(View.VISIBLE);
                                }
                                Utils.ViewUtils.centerViewOnPoint(Scrap, pointX, pointY);

                                Scrap.setRotationY(Utils.interpolateValues(fraction, 180, 0));
                                Scrap.setScaleX(Utils.interpolateValues(fraction, ScrapWidthRatio, 1));
                                Scrap.setScaleY(Utils.interpolateValues(fraction, ScrapHeightRatio, 1));
                            }
                        }
                        else {
                            if (fraction > 0.5f) {
                                Content.setAlpha(2 - 2 * fraction);
                            }
                            if (fraction > 0.25f && USE_SCRAP_TRANSPARENCY) {
                                float alpha = Utils.getIntervalPercentage(fraction, 0.25f, 0.75f);
                                Scrap.setAlpha(alpha > 1f ? 1f : alpha);
                            }
                            Utils.ViewUtils.centerViewOnPoint(Content,
                                    Utils.interpolateValues(fraction, ContentStartPoint.x, ContentEndPoint.x),
                                    Utils.interpolateValues(fraction, ContentStartPoint.y, ContentEndPoint.y));
                            Content.setScaleX(Utils.interpolateValues(fraction, 1, ContentWidthRatio));
                            Content.setScaleY(Utils.interpolateValues(fraction, 1, ContentHeightRatio));
                            Utils.ViewUtils.centerViewOnPoint(Scrap, pointX, pointY);
                            Scrap.setScaleX(Utils.interpolateValues(fraction, ScrapWidthRatio, 1));
                            Scrap.setScaleY(Utils.interpolateValues(fraction, ScrapHeightRatio, 1));
                        }

                        Backend.setAlpha(Utils.interpolateValues(fraction, 0.4f, 1f));
                        Backend.setScaleY(Utils.interpolateValues(fraction, 0.95f, 1f));
                        Backend.setScaleX(Utils.interpolateValues(fraction, 0.95f, 1f));
                    }
                });
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animations.remove(animation);
                        if (activity == null) return;

                        backendCollection.thaw();

                        activity.setLabelAnimationsEnabled(true);

                        Backend.setLayerType(View.LAYER_TYPE_NONE, null);
                        Content.setLayerType(View.LAYER_TYPE_NONE, null);
                        Scrap.setLayerType(View.LAYER_TYPE_NONE, null);

                        dashboardContainer.setTranslationY(0f);
                        dashboardShadow.setTranslationY(0f);

                        root.removeView(Scrap);
                        if (NO_ACTIONBAR_SCREENSHOT) {
                            ActionBarScreenshot.setVisibility(View.INVISIBLE);
                            activity.getLegacyActionBar().getBaseActionBarView().requestEnableInteractions();
                        }
                        else {
                            root.removeView(ActionBarScreenshot);
                        }

                        activity.setDisabledTouchZone(null);
                        content.bringToFront();
                        contentRoot.setVisibility(View.VISIBLE);
                        content.setVisibility(View.INVISIBLE);
                        Utils.ViewUtils.resetViewProperties(Content);
                        (activity.getContentRoot()).bringToFront();
                        ItemCollection.thaw();
                        Root.setBackgroundColor(0);

                        CollectionScrap.setVisibility(View.VISIBLE);
                        backendCollection.releaseView(CollectionScrap);

                        backendCollection.setVerticalScrollBarEnabled(true);
                        backendCollection.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                        backendCollection.setScrollbarFadingEnabled(true);

                        ItemCollection.setVerticalScrollBarEnabled(true);
                        ItemCollection.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                        ItemCollection.setScrollbarFadingEnabled(true);
                    }
                });
                animator.setInterpolator(new LinearInterpolator());
                animator.setDuration(OpenCloseAnimationDuration);
                animator.start();
                animations.add(animator);

                Root.setBackgroundColor(0xFF000000);
//                Backend.setAlpha(0.4f);
//                Backend.animate()
//                        .alpha(1f)
//                        .setDuration(OpenCloseAnimationDuration)
//                        .setInterpolator(new AccelerateInterpolator(1.5f));

                if (NO_ACTIONBAR_SCREENSHOT) {
                    ActionBarScreenshot.animate()
                            .yBy(-ActionBarScreenshot.getHeight())
                            .setDuration(300)
                            .setStartDelay(0)
                            .setInterpolator(new AccelerateInterpolator(1.5f));
                }
                else {
                    ActionBarScreenshot.animate()
                            .yBy(-ActionBarScreenshot.getHeight())
                            .setDuration(300)
                            .setInterpolator(new AccelerateInterpolator(1.5f));
                }

                if (Lollipop || !USE_SCRAP_TRANSPARENCY) {
                    TotalBottomPanel.animate().withLayer();
                }

                TotalBottomPanel.animate()
                        .translationY(totalBottomPanelHeight)
                        .setDuration(300)
                        .setStartDelay(0)
                        .setInterpolator(new AccelerateInterpolator(1.5f))
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                TotalBottomPanel.setVisibility(View.INVISIBLE);
                                TotalBottomPanel.setAlpha(0f);
                                TotalBottomPanel.setLayerType(View.LAYER_TYPE_NONE, null);
                            }
                        });
            }
        };

        Scrap.getViewTreeObserver().addOnGlobalLayoutListener(scrapGlobalLayoutListener);
        animationLayoutListeners.add(scrapGlobalLayoutListener);
    }

    private boolean paused;

    @Override
    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        int id = item.getId();
        if (id == 3048) {
            storage.fuckingClear();
            controller.requestBeginTransaction();

            controller.findSectionWithTag(LibrarySectionKey).clear();

            controller.requestCompleteTransaction();
        }
        if (id == 7070) {
            $ collection = $("." + ScrapMetadataKey + ", ." + LegacyActionBarView.ActionItemMetadataKey + ", #" + R.id.DashboardBalanceText + ", #" + R.id.DashboardBalanceTitle + ", #" +  R.id.view2);

            if (!paused) {
                collection.pause();

                paused = true;
            }
            else {
                collection.resume();

                paused = false;
            }
        }
        if (id == 7076) {
            globalBudget = new BigDecimal(0);
            usedGlobalBudget = new BigDecimal(0);
            checkedOutBudget = new BigDecimal(0);
            customIncome = new BigDecimal(0);
//            carryoverBudget = new BigDecimal(0);
            saveGlobalBudget();
            updateBalanceDisplay();
            return;
        }
        if (id == 7077) {
            refreshBudget();
        }
        // Connection test
        if (id == 5050) {
            new Popover(new BetaConnectionTester(), Popover.anchorWithID(5050)).show(activity).requestGravity(phoneUI ? Popover.GravityBelow : Popover.GravityAbove);
        }
        // Beta Message
        if (id == 6060) {
            SpannableStringBuilder builder = new SpannableStringBuilder("This is a ");
            Utils.appendWithSpan(builder, "very early", new Utils.CustomTypefaceSpan(Utils.MediumTypeface));
            builder.append(" preview of ");
            Utils.appendWithSpan(builder, "RECEIPT v1.3", new Utils.CustomTypefaceSpan(Utils.MediumTypeface));
            builder.append(" and certain features may not work correctly or at all.");
//            Utils.appendWithSpan(builder, "To submit feedback", new Utils.CustomTypefaceSpan(Utils.MediumTypeface));
//            builder.append(", use the Beta Menu, accessible from the main screen toolbar.");
            new MessagePopover("Dragons Ahead!", builder).setOKButtonLabel("Got it!").show(activity);
        }
        if (id == R.id.menu_new_receipt) addNewList();
        else if (id == R.id.menu_history) activity.onOptionsIdSelected(R.id.menu_history, null);
        else if (id == R.id.MenuEditDashboard) showDashboardEditor();
        else activity.onLegacyActionSelected(item);
    }

    public void showSettings() {
        SettingsPopover settings = new SettingsPopover(new Popover.AnchorProvider() {
            @Override
            public View getAnchor(Popover popover) {
                return activity.findViewById(R.id.menu_settings);
            }
        }, PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()));

        // TODO Localize
        settings.addCheckboxSetting("autoReorder", "Auto reorder", "When enabled, crossed-off items will automatically be moved to the bottom of the list").setDefaultValue(true);
        settings.addCheckboxSetting("shakeToSort", "Shake to sort", "When enabled, shaking your device will cause all items to be sorted alphabetically").setDefaultValue(true);
        settings.addCheckboxSetting("playExceededAlert", "Limit exceeded alert", "When enabled, a sound alert will play when going over the list limit").setDefaultValue(false);
        settings.addCheckboxSetting("autoCross", "Auto cross-off", "When enabled, typing in an item's price will cause that item to be automatically crossed-off").setDefaultValue(true);
        settings.addHeaderSetting("currency", "Currency", "");
        settings.addListSetting("currencySymbol", Arrays.asList(Receipt.currencyNameList), Receipt.currencyNames, Receipt.currencySymbols);
        settings.addHeaderSetting("about", "About", "");
        settings.addInfoSetting("version", "Version: 1.3", "");
        settings.addInfoSetting("copyright", "©2014 Mihaiciuc Bogdan", "");
        settings.addHeaderSetting("developer", "Developer", "");
        settings.addCheckboxSetting("alwaysIntro", "Always play intro", "When enabled, the intro animation will always play when the app is launched").setDefaultValue(false);

        settings.getHeader().setTitle(ReceiptActivity.titleFormattedString("Settings"));

        settings.show(activity);
    }

    final static long ActionBarAnimationDuration = 250;

    public void prepareActionBar(boolean animated) {

        if (sidebarMode) return;

        if (state == StateBackend) {

            actionBarRoot.animate().cancel();
            totalBottomPanel.animate().cancel();

            if (animated) {
//    			backendActionBarView.setVisibility(View.VISIBLE);
                actionBarRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                actionBarRoot.animate()
                        .translationY(-actionBar.getHeight())
                        .scaleY(0.99f)
                        .alpha(0)
                        .setDuration(ActionBarAnimationDuration).setInterpolator(new AccelerateInterpolator(1.33f))
                        .setStartDelay(100)
                        .setListener(new AnimatorListenerAdapter() {
                            public void onAnimationEnd(Animator a) {
                                if (actionBarRoot == null) return;
                                actionBarRoot.setLayerType(View.LAYER_TYPE_NONE, null);
                                actionBarRoot.setVisibility(View.INVISIBLE);
                            }
                        });

                scrapFAB.animate().cancel();
                totalBottomPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                totalBottomPanel.animate()
                        .translationY(totalBottomPanelHeight)
                        .alpha(0f)
                        .setDuration(ActionBarAnimationDuration)
                        .setInterpolator(new AccelerateInterpolator(1.33f))
                        .setStartDelay(100)
                        .setListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                if (totalBottomPanel == null) return;
                                totalBottomPanel.setLayerType(View.LAYER_TYPE_NONE, null);
                                totalBottomPanel.setVisibility(View.INVISIBLE);
                            }
                        });

                scrapFAB.setEnabled(false);


            }
            else {
                int actionBarSize;
                TypedValue typeValue = new TypedValue();
                activity.getTheme().resolveAttribute(android.R.attr.actionBarSize, typeValue, true);
                actionBarSize = TypedValue.complexToDimensionPixelSize(typeValue.data,getResources().getDisplayMetrics());

//    			backendActionBarView.setVisibility(View.VISIBLE);
                actionBarRoot.setVisibility(View.INVISIBLE);
                actionBarRoot.setTranslationY(- actionBarSize);
                actionBarRoot.setAlpha(0);

                totalBottomPanel.setVisibility(View.INVISIBLE);
                totalBottomPanel.setTranslationY(totalBottomPanelHeight);
                totalBottomPanel.setAlpha(0f);

                scrapFAB.setEnabled(false);
            }
        }
        else {

            actionBarRoot.animate().cancel();
            totalBottomPanel.animate().cancel();

            scrapFAB.setEnabled(true);

            if (animated) {
                actionBarRoot.setVisibility(View.VISIBLE);
                actionBarRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                actionBarRoot.setScaleY(0.99f);
                if (dashboardContainer.getVisibility() != View.VISIBLE) actionBarRoot.setTranslationY(0);
                actionBarRoot.animate()
                        .translationY(0)
                        .scaleY(1f)
                        .alpha(1)
                        .setDuration(ActionBarAnimationDuration).setInterpolator(new DecelerateInterpolator(1.33f))
                        .setStartDelay(0)
                        .setListener(new AnimatorListenerAdapter() {
                            public void onAnimationEnd(Animator a) {

                                actionBarRoot.setLayerType(View.LAYER_TYPE_NONE, null);
//    						backendActionBarView.setVisibility(View.INVISIBLE);
                            }
                        });

//                scrapFAB.setAlpha(0f);
//                scrapFAB.hide(false);
                activity.setListVisible(false, false, 0);
                totalBottomPanel.setVisibility(View.VISIBLE);
                totalBottomPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                totalBottomPanel.animate()
                        .translationY(0)
                        .scaleY(1f)
                        .alpha(1)
                        .setDuration(ActionBarAnimationDuration).setInterpolator(new DecelerateInterpolator(1.33f))
                        .setStartDelay(0)
                        .setListener(new AnimatorListenerAdapter() {
                            public void onAnimationEnd(Animator a) {

                                totalBottomPanel.setLayerType(View.LAYER_TYPE_NONE, null);

                                activity.setListVisible(true, true, 100);
//                                scrapFAB.showDelayed(100);
//                                scrapFAB.setAlpha(1f);
//                                scrapFAB.setScaleX(0.5f);
//                                scrapFAB.setScaleY(0.5f);
//                                scrapFAB.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(Utils.CompoundInterpolator.frictionOvershootInterpolator()).setStartDelay(100);
//    						backendActionBarView.setVisibility(View.INVISIBLE);
                            }
                        });
            }
            else {

//    			backendActionBarView.setVisibility(View.INVISIBLE);
                actionBarRoot.setVisibility(View.VISIBLE);
                actionBarRoot.setTranslationY(0);
                actionBarRoot.setAlpha(1);

                totalBottomPanel.setVisibility(View.VISIBLE);
                totalBottomPanel.setTranslationY(0);
                totalBottomPanel.setAlpha(1);

                scrapFAB.setAlpha(1f);
                scrapFAB.setScaleY(1f);
                scrapFAB.setScaleX(1f);
            }
        }
        activity.invalidateOptionsMenu();
    }

    public void handleOnBackDown() { }

    public void handleOnBackUp() { }

    public boolean handleMenuPressed() {
        if (state == StateBackend) {
            backendActionBar.showOverflow();
            return true;
        }

        return false;
    }

    public boolean handleBackPressed() {

//		if (budgetPanelUp) {
//			//applyBudgetSettings();
//			return true;
//		}

        if (currentHelpPage != -1 && story != null) {
            story.exitStory();
            return true;
        }

        if (backendActionBar.handleBackPress()) {
            return true;
        }

        if (state == StateBackend && confirmatorUp) {
            finalizeDelete();
            return false;
        }
        if (state == StateOpenList && activeLists.size() > 0) {
//			if (confirmatorUp) finalizeDelete(false);
//			if (backendList.getLastVisiblePosition() < activeLists.size() - 1)
//				expandReceiptFromNullView(0);
//			else
//				expandReceiptFromView(adapter.viewAtPosition(0), 0);
            if (sidebarMode) return false;
            showBackendWithActionbarScreenshot(true);
//            activity.onOptionsIdSelected(R.id.menu_backend, null);
            return true;
        }

        if (state == StateBackend && sidebarMode && dashboardEditorUp) {
            hideDashboardEditor(true);
            return true;
        }

        return false;
    }


    /****************** CONFIRMATOR     *******************/

    private void showDeleteConfirmator(boolean animated) {

        confirmatorUp = true;

        confirmator = activity.getLayoutInflater().inflate(R.layout.delete_overlay, root, false);

        final View undo = confirmator.findViewById(R.id.Undo);
        LegacyRippleDrawable background = new LegacyRippleDrawable(activity, LegacyRippleDrawable.ShapeRoundRect);
        background.setColors(0x00FFFFFF, 0x22FFFFFF);
        background.setRippleColor(0x40FFFFFF);
        undo.setBackground(background);

        root.addView(confirmator);
        undo.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                confirmator.setOnTouchListener(null);
                undo.setOnClickListener(null);
                undo.setClickable(false);
                undoDelete();
            }
        });

        confirmator.animate().setDuration(100);

        if (animated) {
            confirmator.setAlpha(0);
            confirmator.animate()
                    .alpha(1).withLayer();
        }

        confirmator.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    if (event.getY() < ((ViewGroup) confirmator).getChildAt(0).getTop() - metrics.density * 16) {
                        confirmator.setOnTouchListener(null);
                        undo.setOnClickListener(null);
                        undo.setClickable(false);
                        finalizeDelete();
                        return false;
                    }
                }
                return true;
            }
        });

    }

    public void undoDelete() {
        usedGlobalBudget = usedGlobalBudget.add(confirmingReceipt.header.total.multiply(new BigDecimal(10000 + confirmingReceipt.header.tax).movePointLeft(4)));
        updateBalanceDisplay();

        if (confirmatorUp) {
            final View Confirmator = confirmator;
            confirmator.animate().alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator a) {
                    if (activity == null) return;
                    root.removeView(Confirmator);
                }
            });
            confirmator = null;
            confirmatorUp = false;
        }

        activeLists = storage.getActiveLists();
        controller.requestBeginTransaction();

        controller.findSectionWithTag(LibrarySectionKey).clear();
        controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);
        if (state == StateOpenList && sidebarMode) {
            controller.findSectionWithTag(LibrarySectionKey).removeObjectAtIndex(0);
        }

        backendCollection.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
        backendCollection.setAnimationsEnabled(true);
        final AbstractReceipt ConfirmingReceipt = confirmingReceipt;
        backendCollection.setAnchorCondition(new CollectionView.AnchorInspector() {
            @Override
            public boolean isAnchor(Object object, int viewType) {
                return object == ConfirmingReceipt;
            }
        });
        controller.requestCompleteTransaction();

    }

    public void finalizeDelete() {
        finalizeDelete(true);
    }

    public void finalizeDelete(boolean animated) {

        activeLists = storage.getActiveLists();

        if (activeLists.indexOf(confirmingReceipt) == 0 && activeLists.size() > 1) {
            try {
                if (!activeLists.get(1).fullyLoaded)
                    storage.loadReceipt(activeLists.get(1));
            }
            catch (IOException exception) {
                storage.addNewReceiptTo(1);
            }
            activity.restoreState(activeLists.get(1));
        }
        storage.deleteReceipt(confirmingReceipt);
//    	activity.invalidateOptionsMenu();
        //The adapter already shows the correct state
        //adapter.notifyDataSetChanged();
//		activeList = 0;

        if (confirmatorUp) {
            final View Confirmator = confirmator;
            if (animated)
                confirmator.animate().alpha(0).withLayer().setListener(new AnimatorListenerAdapter() {
                    public void onAnimationEnd(Animator a) {
                        if (activity == null) return;
                        root.removeView(Confirmator);
                    }
                });
            else
                root.removeView(confirmator);
            confirmator = null;
            confirmatorUp = false;
        }

        confirmingReceipt = null;
    }

    // Factory methods
    public interface FactoryRunnable {
        public void createFromFactory(long factoryUID);
    }


    // over scroller

    VelocityTracker tracker;

    /**
     * Used to initialize the over scroller after the backend collection view has been created.
     * The overscroller will intercept top-overscroll events and use them to enable the <strong>Create new list</strong> gesture.
     */
    protected void initOverScrollListener() {
        final float ScaledMinimumFlingVelocity = ViewConfiguration.get(activity).getScaledMinimumFlingVelocity();

        backendCollection.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (tracker != null)
                    tracker.addMovement(motionEvent);
                return false;
            }
        });
        backendCollection.setOnOverScrollListener(new CollectionView.OnOverScrollListener() {
            boolean started;
            int lastAmount;

            @Override
            public void onOverScroll(CollectionView collectionView, int amount, int direction) {
                lastAmount = amount;
                if (!started) {
                    started = true;
                    tracker = VelocityTracker.obtain();

                    flushAnimations();

                    activity.setLabelAnimationsEnabled(false);

                    contentRoot.setVisibility(View.VISIBLE);
//                    contentRoot.setClipChildren(false);
                    content.setVisibility(View.VISIBLE);
                    content.bringToFront();
                    actionBarRoot.setVisibility(View.VISIBLE);
                    actionBarRoot.setTranslationY(0f);
                    actionBarRoot.setAlpha(1f);

                    contentRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                    content.setLayerType(View.LAYER_TYPE_NONE, null);
                    backend.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    contentRoot.setTranslationY(- contentRoot.getHeight());

                    backendCollection.setOverScrollMode(View.OVER_SCROLL_NEVER);
                    backendCollection.setVerticalScrollBarEnabled(false);

                    root.setBackgroundColor(0xFF000000);

                    AbstractReceipt receipt = storage.addNewReceiptTo(0);
                    activity.restoreState(receipt);
                }

                contentRoot.setTranslationY(- contentRoot.getHeight() + amount / 2);
                backend.setAlpha(1 - amount / (2f * contentRoot.getHeight()));
            }

            @Override
            public void onOverScrollStopped(CollectionView collectionView) {
                if (activity == null) {
                    // Most likely, screen rotation has occured while the user was over scrolling
                    // Views have been detached from the window and can't be used anymore
                    // Since the activity has already been restored to the new receipt, it should be used
                    // to prevent it from overwriting the last active list
                    state = StateOpenList;
                    return;
                }

                final boolean WasStarted = started;
                started = false;
                float speed = 0;
                if (tracker != null) {
                    tracker.computeCurrentVelocity(1);
                    speed = tracker.getYVelocity();

                    tracker.recycle();
                }

                if (!WasStarted) return;

                // speed is px/ms, scaledminimumflingvelocity is px/s
                if ((speed * 1000 >= ScaledMinimumFlingVelocity || lastAmount > (metrics.heightPixels / 2f))
                        && speed > 0f && lastAmount > 56 * metrics.density) {
                    // This gesture meets the requirements of creating a new list
                    state = StateOpenList;

                    speed = Math.max(2, speed) / metrics.density;

                    final float BackendAlpha = backend.getAlpha();
                    ValueAnimator animator = ValueAnimator.ofFloat(- contentRoot.getHeight(), 0f);
                    animations.add(animator);

                    totalBottomPanel.setAlpha(1f);
//                    scrapFAB.setAlpha(0f);
//                    scrapFAB.hide(false);
                    activity.setListVisible(false, false, 0);
                    final View PanelScreenshot = Utils.ViewUtils.screenshotView(totalBottomPanel);
                    totalBottomPanel.setAlpha(0f);

                    root.addView(PanelScreenshot, root.getWidth(), totalBottomPanelHeight);
                    PanelScreenshot.setTranslationY(root.getHeight());
                    PanelScreenshot.setAlpha(0f);

                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator valueAnimator) {
                            float fraction = valueAnimator.getAnimatedFraction();

                            contentRoot.setTranslationY((Float) valueAnimator.getAnimatedValue());
                            backend.setAlpha(Utils.interpolateValues(fraction, BackendAlpha, 0.4f));
                            backend.setScaleX(Utils.interpolateValues(fraction, 1f, 0.95f));
                            backend.setScaleY(Utils.interpolateValues(fraction, 1f, 0.95f));

                            PanelScreenshot.setTranslationY(Utils.interpolateValues(fraction, root.getHeight(), root.getHeight() - totalBottomPanelHeight));
                            PanelScreenshot.setAlpha(fraction);
                        }
                    });

                    TimeInterpolator interpolator = new AccelerateInterpolator(1.5f);

                    long duration = (long) (1600 / speed);
                    if (duration > 600) duration = 600;
                    animator.setDuration(duration)
                            .setInterpolator(interpolator);

                    animator.start();

                    float fraction = (lastAmount / 2f) / (float) contentRoot.getHeight();
                    long currentPlayTime = (long) (duration * (float)Math.cbrt(fraction));

                    animator.setCurrentPlayTime(currentPlayTime);

                    backendCollection.freeze();
                    animator.addListener(new AnimatorListenerAdapter() {
                        boolean cancelled;
                        public void onAnimationCancel(Animator animation) {
                            cancelled = true;
                        }
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            animations.remove(animation);

                            contentRoot.setLayerType(View.LAYER_TYPE_NONE, null);
                            contentRoot.setTranslationY(0);

                            if (!cancelled) {
                                final View ContentContainer = activity.findViewById(R.id.ContentContainer);

                                animatedViews.add(ContentContainer);

                                PanelScreenshot.animate()
                                        .translationYBy(3 * metrics.density)
                                        .setInterpolator(new Utils.BounceCycleInterpolator(1.5f))
                                        .setStartDelay(0)
                                        .setDuration(300)
                                        .setListener(null);

                                ContentContainer.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                actionBarRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                                ContentContainer.animate()
                                        .translationY(- 0.05f * ContentContainer.getHeight())
                                        .setInterpolator(new Utils.BounceCycleInterpolator(1.5f))
                                        .setStartDelay(0)
                                        .setDuration(300)
                                        .setListener(new AnimatorListenerAdapter() {
                                            @Override
                                            public void onAnimationEnd(Animator animation) {
                                                animatedViews.remove(ContentContainer);
                                                ContentContainer.animate().setListener(null);

                                                //Retain temporary receipt
                                                controller.requestBeginTransaction();
                                                controller.findSectionWithTag(LibrarySectionKey).clear();
                                                controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

                                                backendCollection.setAnimationsEnabled(false);
                                                controller.requestCompleteTransaction();

                                                activity.resumeAnimations();

                                                ContentContainer.setLayerType(View.LAYER_TYPE_NONE, null);
                                                content.setLayerType(View.LAYER_TYPE_NONE, null);
                                                ContentContainer.setTranslationY(0);
                                                contentRoot.setTranslationY(0);
                                                content.setTranslationY(0f);

                                                backend.setVisibility(View.INVISIBLE);
                                                backend.setLayerType(View.LAYER_TYPE_NONE, null);
                                                actionBarRoot.setLayerType(View.LAYER_TYPE_NONE, null);
                                                actionBarRoot.setTranslationY(0f);

                                                actionBar.setY(0);

                                                backendCollection.thaw();

                                                backend.setScaleX(1f);
                                                backend.setScaleY(1f);

                                                root.setBackgroundColor(0x0);

                                                root.removeView(PanelScreenshot);
                                                totalBottomPanel.setAlpha(1f);
                                                totalBottomPanel.setTranslationY(0f);
                                                totalBottomPanel.setVisibility(View.VISIBLE);

                                                activity.setListVisible(true, true, 100);
                                                activity.setLabelAnimationsEnabled(true);
//                                                scrapFAB.showDelayed(100);
//                                                scrapFAB.setAlpha(1f);
//                                                scrapFAB.setEnabled(true);
//                                                scrapFAB.setScaleX(0.5f);
//                                                scrapFAB.setScaleY(0.5f);
//                                                scrapFAB.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).setInterpolator(Utils.CompoundInterpolator.frictionOvershootInterpolator()).setStartDelay(100);
                                            }
                                        });
                            }
                            else {
                                backend.setLayerType(View.LAYER_TYPE_NONE, null);
                                backend.setVisibility(View.INVISIBLE);
                                backend.setScaleX(1f);
                                backend.setScaleY(1f);
                                backendCollection.thaw();
                                root.setBackgroundColor(0x0);

                                //Retain temporary receipt
                                controller.requestBeginTransaction();
                                controller.findSectionWithTag(LibrarySectionKey).clear();
                                controller.findSectionWithTag(LibrarySectionKey).addAllObjects(activeLists);

                                backendCollection.setAnimationsEnabled(false);
                                controller.requestCompleteTransaction();
                            }
                        }
                    });
                }
                else {
                    // Delete temporary receipt
                    // TODO: only removed from activeLists, but backing file not deleted
                    storage.removeReceiptAt(0);

                    if (lastAmount > 0.1 * metrics.heightPixels) {
                        // Likely user has stopped scrolling

                        contentRoot.animate().translationY(-contentRoot.getHeight())
                                .setDuration(200);

                        backend.animate().alpha(1f)
                                .setDuration(200);

                        backendCollection.freeze();
                        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                        animator.setDuration(200);
                        animator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                animations.remove(animation);

                                backend.setLayerType(View.LAYER_TYPE_NONE, null);
                                contentRoot.setLayerType(View.LAYER_TYPE_NONE, null);

                                contentRoot.setTranslationY(0f);
                                content.setVisibility(View.INVISIBLE);
                                actionBarRoot.setVisibility(View.INVISIBLE);

                                root.setBackgroundColor(0x0);

                                actionBarRoot.setTranslationY(-actionBarRoot.getHeight());

                                backendCollection.setVerticalScrollBarEnabled(true);

                                if (activeLists.size() > 0) activity.restoreState(activeLists.get(0));

                                backendCollection.thaw();
                                backendCollection.setOverScrollMode(View.OVER_SCROLL_NEVER);

                                activity.setLabelAnimationsEnabled(true);

                                if (emptyAnimator != null && controller.findSectionWithTag(LibrarySectionKey).getSize() == 0) {
                                    emptyAnimator.start();
                                }
                            }
                        });

                        animator.start();
                        animations.add(animator);

                        ValueAnimator overScrollAnimator = ValueAnimator.ofFloat(0f, 1f);
                        overScrollAnimator.setDuration(400);
                        overScrollAnimator.addListener(new AnimatorListenerAdapter() {
                            @Override
                            public void onAnimationEnd(Animator animation) {
                                animations.remove(animation);
                                if (backendCollection != null) backendCollection.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                            }
                        });
                        overScrollAnimator.start();
                        animations.add(overScrollAnimator);
                    }
                    else {
                        // Likely user has scrolled up past the overscroll

                        contentRoot.setTranslationY(0f);
                        content.setVisibility(View.INVISIBLE);
                        actionBarRoot.setVisibility(View.INVISIBLE);

                        root.setBackgroundColor(0x0);

                        actionBarRoot.setTranslationY(-actionBarRoot.getHeight());

                        backendCollection.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
                        backendCollection.setVerticalScrollBarEnabled(true);
                        if (activeLists.size() > 0) activity.restoreState(activeLists.get(0));

                        contentRoot.setLayerType(View.LAYER_TYPE_NONE, null);
                        backend.setLayerType(View.LAYER_TYPE_NONE, null);
                        backend.setAlpha(1f);

                        if (emptyAnimator != null && controller.findSectionWithTag(LibrarySectionKey).getSize() == 0) {
                            emptyAnimator.start();
                        }
                    }
                }
                tracker = null;
            }
        });
    }

    // **************** DASHBOARD EDITOR ****************

    /**
     * Creates the dashboard editor panel layout.
     * @return The root view of the dashboard editor panel.
     */
    private View obtainDashboardEditor() {
        final boolean Landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        final boolean Phone = getResources().getConfiguration().smallestScreenWidthDp < 600;

        PanelBuilder builder = new PanelBuilder(getActivity());

        if (sidebarMode) {
            builder.setHeaderSetting(getString(R.string.Balance), PanelBuilder.TypeText, R.id.DashboardEditBalance);
        }
        builder.setTitleWidth(Landscape ?
                (Phone ? (int) (144 * metrics.density) : (int) (192 * metrics.density))
                : (Phone ? getResources().getDimensionPixelSize(R.dimen.AlignmentWidth) :  (int) (144 * metrics.density)))
            .setTitleSetting(getString(R.string.Budget), PanelBuilder.TypeEditText, R.id.DashboardEditBudget)
            .addSetting(getString(R.string.Reset), PanelBuilder.TypeButton, R.id.DashboardEditReset)
            .addSetting(getString(R.string.Spent), PanelBuilder.TypeText, R.id.DashboardEditSpent);

        if (!sidebarMode) {
            builder.addSetting(getString(R.string.Balance), PanelBuilder.TypeText, R.id.DashboardEditBalance);
        }

        dashboardEditPanel = builder.build();
        ((ViewGroup) dashboardEditPanel).setClipChildren(false);

        EditText budgetField = (EditText) dashboardEditPanel.findViewById(R.id.DashboardEditBudget);
        budgetField.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        budgetField.setMaxLines(1);
        budgetField.setImeOptions(EditorInfo.IME_ACTION_DONE);
        budgetField.setPadding(budgetField.getPaddingLeft(), 0, budgetField.getPaddingRight(), 0);
        budgetField.setHint(ReceiptActivity.totalFormattedString(activity, new BigDecimal(0)));
        if (globalBudget.compareTo(new BigDecimal(0)) != 0) budgetField.setText(globalBudget.toPlainString());
        budgetField.addTextChangedListener(new Utils.OnTextChangedListener() {
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                BigDecimal newBudget;
                try {
                    newBudget = new BigDecimal(charSequence.toString());
                    newBudget = newBudget.setScale(2, RoundingMode.HALF_EVEN);
                }
                catch (Exception e) {
                    newBudget = new BigDecimal(0);
                }

                globalBudget = newBudget;

                View resetField = dashboardEditPanel.findViewById(R.id.DashboardEditReset);
                if (newBudget.compareTo(new BigDecimal(0)) == 0) {
                    resetField.setEnabled(false);
                    resetField.setAlpha(0.5f);
                }
                else {
                    resetField.setEnabled(true);
                    resetField.setAlpha(1f);
                }

                updateBalanceDisplay();
            }
        });

        TextView resetField = (TextView) dashboardEditPanel.findViewById(R.id.DashboardEditReset);

        final NumberPicker.OnValueChangeListener ValueChangeListener = new NumberPicker.OnValueChangeListener() {
            int suffixResources[] = new int[] {R.string.SuffixST, R.string.SuffixND, R.string.SuffixRD, R.string.SuffixTH};

            @Override
            public void onValueChange(NumberPicker numberPicker, int oldValue, int newValue) {
                TextView resetField = (TextView) dashboardEditPanel.findViewById(R.id.DashboardEditReset);
                if (newValue != 0) {
                    int suffixResource = suffixResources[3];
                    if (newValue / 10 != 1) {
                        if (newValue % 10 < 4 && newValue % 10 > 0) suffixResource = suffixResources[newValue % 10 - 1];
                    }

                    SpannableStringBuilder builder = new SpannableStringBuilder(getString(R.string.ResetDisplay, newValue, getString(suffixResource)));
                    builder.setSpan(new SuperscriptSpan(), builder.length() - 2, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.setSpan(new RelativeSizeSpan(0.66f), builder.length() - 2, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    resetField.setText(builder);
                }
                else {
                    resetField.setText(getString(R.string.ResetTypeManually));
                }

                budgetResetValue = newValue;

            }
        };
        ValueChangeListener.onValueChange(null, 0, budgetResetValue);

        resetField.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(final View view) {
                final ResetSelectorController popoverController = new ResetSelectorController(Receipt.getStaticContext());
                popover = new Popover(
                        popoverController,
//                        new LegacyActionBar.CustomViewListener() {
//                            @Override
//                            public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
//                                final View customView = inflater.inflate(R.layout.dashboard_reset_popover, container, false);
//
//                                ((TextView) customView.findViewById(R.id.ResetTitle)).setTypeface(Receipt.condensedTypeface());
//                                NumberPicker picker = (NumberPicker) customView.findViewById(R.id.DatePicker);
//                                picker.setMaxValue(31);
//                                picker.setMinValue(0);
//                                String displayedValues[] = new String[32];
//                                displayedValues[0] = getString(R.string.ResetTypeManually);
//                                for (int i = 1; i < 32; i++) {
//                                    displayedValues[i] = Integer.toString(i);
//                                }
//                                picker.setDisplayedValues(displayedValues);
//                                picker.setDescendantFocusability(NumberPicker.FOCUS_BLOCK_DESCENDANTS);
//
//                                picker.setOnValueChangedListener(ValueChangeListener);
//                                picker.setValue(budgetResetValue);
//
//                                return customView;
//                            }
//
//                            @Override
//                            public void onDestroyCustomView(View customView) {
//
//                            }
//                        },
                        new Popover.AnchorProvider() {
                            @Override
                            public View getAnchor(Popover popover) {
                                return activity.findViewById(R.id.DashboardEditReset);
                            }
                        });
                popoverController.attach(activity, popover);
//                popover.setShowAsWindowEnabled(true);
                popover.setOnDismissListener(new Popover.OnDismissListener() {
                    @Override
                    public void onDismiss() {
                        PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).edit().putInt(BudgetResetValueKey, budgetResetValue).apply();
                        popoverController.detach();
                    }
                });
                popover.show(getActivity());
            }
        });
        if (getGlobalBudget().compareTo(new BigDecimal(0)) == 0) {
            resetField.setEnabled(false);
            resetField.setAlpha(0.5f);
        }

        TextView balance = (TextView) dashboardEditPanel.findViewById(R.id.DashboardEditBalance);
        ((TextView) dashboardEditPanel.findViewById(R.id.DashboardEditBalance)).setText(balanceText.getText());
        ((TextView) dashboardEditPanel.findViewById(R.id.DashboardEditBalance)).setTextColor(balanceText.getTextColors().getDefaultColor());
        TextView spent = (TextView) dashboardEditPanel.findViewById(R.id.DashboardEditSpent);
        spent.setText(ReceiptActivity.totalFormattedString(activity, usedGlobalBudget.add(checkedOutBudget)));

        return dashboardEditPanel;
    }

    private View obtainDashboardEditorHeader(LayoutInflater inflater, ViewGroup container) {

        View view = inflater.inflate(R.layout.dashboard_add_balance, container, false);

        EditText addToBalanceBox = (EditText) view.findViewById(R.id.AddBox);
        if (addToBalanceBox != null) {
            addToBalanceBox.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            addToBalanceBox.setMaxLines(1);
            addToBalanceBox.setImeOptions(EditorInfo.IME_ACTION_DONE);

            addToBalanceBox.setOnEditorActionListener(new TextView.OnEditorActionListener() {
                @Override
                public boolean onEditorAction(TextView textView, int i, KeyEvent keyEvent) {
                    if (keyEvent == null) {
                        ((ViewGroup) textView.getParent()).findViewById(R.id.AddToBalance).performClick();
                        return true;
                    }
                    if (keyEvent.getAction() == EditorInfo.IME_ACTION_DONE) {
                        ((ViewGroup) textView.getParent()).findViewById(R.id.AddToBalance).performClick();
                    }
                    return false;
                }
            });

            view.findViewById(R.id.AddToBalance).setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    BigDecimal amount;
                    try {
                        amount = new BigDecimal(((TextView) ((ViewGroup) view.getParent()).findViewById(R.id.AddBox)).getText().toString());
                    }
                    catch (NumberFormatException exception) {
                        return;
                    }

                    customIncome = customIncome.add(amount);

                    TextView addBox = ((TextView) ((ViewGroup) view.getParent()).findViewById(R.id.AddBox));
                    addBox.setText("");
                    if (addBox.hasFocus()) {
                        InputMethodManager imm = (InputMethodManager) addBox.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        imm.hideSoftInputFromWindow(addBox.getWindowToken(), 0);
                    }

                    updateBalanceDisplay();
                }
            });
            view.findViewById(R.id.AddToBalance).setBackground(new LegacyRippleDrawable(activity));
        }

        if (sidebarMode) {
            TextView title = (TextView) view.findViewById(R.id.DashboardEditorTitle);
            title.setTypeface(Receipt.condensedTypeface());
        }

        return view;
    }

    public void showDashboardEditor() {
        showDashboardEditor(true);
    }

    /**
     * This changes the dashboard from the regular balance display into the dashboard editor display. PhoneUI only.
     * @param animated Controls whether this change is animated
     */
    public void showDashboardEditor(boolean animated) {
        if (sidebarMode) {
            activateDashboardEditor(animated);
            return;
        }

        if (animated) {
            View editBudgetButton = backendActionBar.getBaseActionBarView().findViewById(R.id.MenuEditDashboard);
            if (editBudgetButton != null) {
                ((LegacyRippleDrawable) editBudgetButton.getBackground()).dismissPendingFlushRequest();
            }
        }

        flushAnimations();

        dashboardEditorUp = true;
        final boolean Landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        final boolean Phone = getResources().getConfiguration().smallestScreenWidthDp < 600;

        obtainDashboardEditor();

//        dashboardEditPanel.setPadding((int) (48 * metrics.density), 0, (int) (48 * metrics.density), 0);
        backendCollection.requestDisableInteractions();

        if (Landscape) {
            enableDashboardEditorLandscape(animated);
        }
        else {
            enableDashboardEditorPortrait(animated);
        }

        backendFAB.setEnabled(false);
        backendFAB.hide(animated);

        if (dashboardEditWrapper == null) {
            dashboardEditWrapper = backendActionBar.createContextMode(dashboardListener);

            dashboardEditWrapper.setSeparatorVisible(false);
            dashboardEditWrapper.setBackButtonEnabled(true);
            dashboardEditWrapper.setBackMode(LegacyActionBarView.DoneBackMode);
            dashboardEditWrapper.setTextColor(getResources().getColor(R.color.DashboardText));
            dashboardEditWrapper.setBackgroundColor(getResources().getColor(R.color.DashboardActionBar));
            dashboardEditWrapper.setDoneResource(R.drawable.ic_action_done_dark);
            dashboardEditWrapper.setDoneSeparatorVisible(false);

            dashboardEditWrapper.setCustomView(new LegacyActionBar.CustomViewProvider() {
                boolean isRotating;

                @Override
                public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
                    View view = obtainDashboardEditorHeader(inflater, container);

                    if (!isRotating) {
                        isRotating = true;
                        view.setScaleY(0f);
                        view.animate().scaleY(1f).setDuration(200);

                        View title = view.findViewById(R.id.DashboardEditorTitle);
                        if (title != null) {
                            title.setTranslationY(48 * metrics.density);
                            title.setAlpha(0f);

                            title.animate().translationY(0f).alpha(1f).setDuration(200);
                        }
                    }
                    return view;
                }

                @Override
                public void onDestroyCustomView(View customView) {

                }
            });

            dashboardEditWrapper.start();
//            dashboardEditWrapper.setTitleAnimated("Edit budget", 1);
        }
        else {
            dashboardEditWrapper.setBackgroundColor(getResources().getColor(R.color.DashboardActionBar));
        }

    }


    /**
     * When set to true, dashboard balance panel animations will not use the new
     * ViewProxy animations, falling back to the classic ValueAnimator animations instead.
     */
    final static boolean USE_OLD_BALANCE_ANIMATIONS = false;

    /**
     * The amount by which fields will be increasingly displaced during the dashboard edit panel show/hide animation, expressed in DP.
     */
    final static int DashboardPanelDisplacementStride = 0;

    /**
     * The stride used for animating fields during the dhasboard edit panel show/hide animation, expressed in milliseconds.
     */
    final static int DashboardPanelStride = 33;

    /**
     * Shows the balance edit fields for non-sidebar mode layouts.
     * @param animated Controls whether this change is animated.
     */
    public void enableDashboardEditorPortrait(boolean animated) {

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dashboardEditPanel.getLayoutParams());
            params.topMargin = getResources().getDimensionPixelSize(R.dimen.DashboardVerticalPadding);
            params.topMargin += (getResources().getDimensionPixelSize(R.dimen.DashboardHeight)
                    - getResources().getDimensionPixelSize(R.dimen.DashboardContentHeight)) / 2;
        dashboardContainer.addView(dashboardEditPanel, params);
        dashboardContainer.setClipChildren(false);

        if (backendCollection.getHeight() == 0) {
            backendCollection.setScrollY(0);
            controller.purgeSavedState();
        }

        final int TotalTranslation = (int) (96 * metrics.density + 0.5f - dashboardContainer.getTranslationY());

//        dashboardContainer.bringToFront();
        if (!animated) {
            dashboardContainer.getLayoutParams().height += (int) (96 * metrics.density + 0.5f);
            backendCollection.setTranslationY(TotalTranslation);
            backendCollection.setAlpha(0.4f);
            backendCollection.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            dashboard.setAlpha(0f);

//            backendFAB.setGlyph(Glyph.GlyphDone, false);
//            backendFAB.setBackgroundAndGlyphColors(0xFFFFFFFF, Utils.transparentColor(0.75f, 0), false);
        }
        else {

            if (USE_OLD_BALANCE_ANIMATIONS) {
                dashboardEditPanel.setAlpha(0f);
            }

            dashboard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            dashboard.buildLayer();
            if (USE_OLD_BALANCE_ANIMATIONS) {
                dashboardEditPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                dashboardEditPanel.buildLayer();
            }
            backendCollection.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            backendCollection.buildLayer();

            int newDimension = dashboardContainer.getLayoutParams().height + (int) (96 * metrics.density + 0.5f);

            final int ContainerDimension = dashboardContainer.getHeight();
            final int NewContainerDimension = newDimension;

            final float StartingTranslation = dashboardContainer.getTranslationY();

            if (!USE_OLD_BALANCE_ANIMATIONS) {
                $.bind(activity);

                ((ViewGroup) dashboardEditPanel).setClipChildren(false);

                $(dashboardEditPanel).children().each(new $.Each() {
                    int i = 0;
                    int row = 4;

                    final TimeInterpolator Friction = new Utils.FrictionInterpolator(1.5f);

                    @Override
                    public void run(View view, int index) {
                        $ field = $(view);

                        field.property($.TranslateY, $.dp(-64 - DashboardPanelDisplacementStride * row), $.Op.Add)
                                .property($.Opacity, 0)
                                .animate()
                                    .property($.Opacity, 0, 1)
                                    .property($.TranslateY, $.dp(64 + DashboardPanelDisplacementStride * row), $.Op.Add)
                                    .layer(true)
                                    .delay(DashboardPanelStride * row)
                                    .duration(300)
                                    .interpolator(Friction)
                                .start("dashboardQueue");

                        // The animation groups fields in pairs of 2 (title + field)
                        i++;
                        if (i == 2) {
                            row--;
                            i = 0;
                        }
                    }
                });

                $.unbind();
            }

//            backendFAB.setGlyph(Glyph.GlyphDone, true);
//            backendFAB.setBackgroundAndGlyphColors(0xFFFFFFFF, Utils.transparentColor(0.75f, 0), true);

            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();
                    dashboard.setTranslationY(Utils.interpolateValues(fraction, 0, 96 * metrics.density));

                    if (USE_OLD_BALANCE_ANIMATIONS)
                        dashboardEditPanel.setTranslationY(Utils.interpolateValues(fraction, -96 * metrics.density, 0));

                    backendCollection.setTranslationY(Utils.interpolateValues(fraction, 0f, TotalTranslation));
                    dashboardContainer.setTranslationY(Utils.interpolateValues(fraction, StartingTranslation, 0));
                    dashboardShadow.setTranslationY(dashboardContainer.getTranslationY());
                    if (USE_ALIGN_FAB)
                        backendFAB.setTranslationY(Utils.interpolateValues(fraction, StartingTranslation, 96 * metrics.density));
//                    backendFAB.setAlpha(Utils.interpolateValues(fraction, 1f, 0f));
//                    backendFAB.setScaleX(Utils.interpolateValues(fraction, 1f, 0.5f));
//                    backendFAB.setScaleY(Utils.interpolateValues(fraction, 1f, 0.5f));
                    dashboardContainer.getLayoutParams().height = (int) Utils.interpolateValues(fraction, ContainerDimension, NewContainerDimension);
                    dashboardContainer.requestLayout();
                    dashboard.setAlpha(1 - fraction);

                    if (USE_OLD_BALANCE_ANIMATIONS) dashboardEditPanel.setAlpha(fraction);

                    backendCollection.setAlpha(Utils.interpolateValues(fraction, 1f, 0.4f));
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);

                    dashboard.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (USE_OLD_BALANCE_ANIMATIONS)
                        dashboardEditPanel.setLayerType(View.LAYER_TYPE_NONE, null);
                    enableBackButton();
                }
            });
            animator.setDuration(300);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    pendingAnimations.remove(this);

                    animator.start();
                    animations.add(animator);
                    disableBackButton();
                }
            };
            pendingAnimations.add(r);
            handler.post(r);
        }
    }

    public void enableDashboardEditorLandscape(boolean animated) {
        //noinspection SuspiciousNameCombination
        final int RequiredWidth = metrics.heightPixels; // This is the same width as in landscape
        final int Width = getResources().getDimensionPixelSize(R.dimen.DashboardLandscapeWidth);

        final int WidthDisplacement = RequiredWidth - Width;

        final View PlaceHolder = new View(activity);
        PlaceHolder.setBackgroundColor(getResources().getColor(R.color.DashboardBackground));

        dashboardContainer.setClipChildren(false);
        backend.setClipChildren(false);

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(WidthDisplacement, LayoutParams.MATCH_PARENT);
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        backend.addView(PlaceHolder, 0, params);

        params = new RelativeLayout.LayoutParams(dashboardEditPanel.getLayoutParams());
        params.width = RequiredWidth;
        params.addRule(RelativeLayout.CENTER_VERTICAL);
        backend.addView(dashboardEditPanel, params);

        final View BackendSeparator = backend.findViewById(R.id.DashboardShadow);

        if (animated) {
            dashboard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            dashboard.buildLayer();
            if (USE_OLD_BALANCE_ANIMATIONS) {
                dashboardEditPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                dashboardEditPanel.buildLayer();
            }
            backendCollection.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            backendCollection.buildLayer();

            if (USE_OLD_BALANCE_ANIMATIONS) dashboardEditPanel.setAlpha(0f);

            PlaceHolder.setTranslationX(- WidthDisplacement);

            if (!USE_OLD_BALANCE_ANIMATIONS) {
                $.bind(activity);

                ((ViewGroup) dashboardEditPanel).setClipChildren(false);

                $(dashboardEditPanel).children().each(new $.Each() {
                    int i = 0;
                    int row = 4;

                    final TimeInterpolator Friction = new Utils.FrictionInterpolator(1.5f);

                    @Override
                    public void run(View view, int index) {
                        $ field = $(view);

                        field.property($.TranslateY, $.dp(-64 - DashboardPanelDisplacementStride * row), $.Op.Add)
                                .property($.Opacity, 0)
                                .animate()
                                .property($.Opacity, 0, 1)
                                .property($.TranslateY, $.dp(64 + DashboardPanelDisplacementStride * row), $.Op.Add)
                                .layer(true)
                                .delay(DashboardPanelStride * row)
                                .duration(300)
                                .interpolator(Friction)
                                .start("dashboardQueue");

                        // The animation groups fields in pairs of 2 (title + field)
                        i++;
                        if (i == 2) {
                            row--;
                            i = 0;
                        }
                    }
                });

                $.unbind();
            }

//            backendFAB.setGlyph(Glyph.GlyphDone, true);
//            backendFAB.setBackgroundAndGlyphColors(0xFFFFFFFF, Utils.transparentColor(0.75f, 0), true);

            final ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                View dashboardAddBalance;
                int location[] = new int[2];

                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();
                    dashboard.setTranslationY(Utils.interpolateValues(fraction, 0, 128 * metrics.density));
                    dashboard.setTranslationX(Utils.interpolateValues(fraction, 0, -WidthDisplacement));
                    if (USE_OLD_BALANCE_ANIMATIONS) dashboardEditPanel.setTranslationY(Utils.interpolateValues(fraction, -96 * metrics.density, 0));
                    backendCollection.setTranslationX(Utils.interpolateValues(fraction, 0f, WidthDisplacement));
                    dashboardContainer.setTranslationX(Utils.interpolateValues(fraction, 0f, WidthDisplacement));
                    PlaceHolder.setTranslationX(Utils.interpolateValues(fraction, -WidthDisplacement, 0f));
                    BackendSeparator.setTranslationX(fraction * WidthDisplacement);
                    if (USE_ALIGN_FAB) backendFAB.setTranslationX(fraction * WidthDisplacement);
//                    backendFAB.setAlpha(Utils.interpolateValues(fraction, 1f, 0f));
//                    backendFAB.setScaleX(Utils.interpolateValues(fraction, 1f, 0.5f));
//                    backendFAB.setScaleY(Utils.interpolateValues(fraction, 1f, 0.5f));
                    dashboard.setAlpha(1 - fraction);
                    if (USE_OLD_BALANCE_ANIMATIONS) dashboardEditPanel.setAlpha(fraction);
                    backendCollection.setAlpha(Utils.interpolateValues(fraction, 1f, 0.4f));

                    if (dashboardAddBalance == null) {
                        dashboardAddBalance = backendActionBarView.findViewById(R.id.DashboardAddBalance);
                    }

                    if (dashboardAddBalance != null) {
                        if (location[0] == 0) {
                            dashboardAddBalance.getLocationOnScreen(location);
                        }
                        dashboardAddBalance.getLayoutParams().width = (- location[0] + RequiredWidth) - (int) ((1 - fraction) * WidthDisplacement);
                        dashboardAddBalance.requestLayout();
                    }
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);

                    dashboard.setLayerType(View.LAYER_TYPE_NONE, null);
                    dashboardEditPanel.setLayerType(View.LAYER_TYPE_NONE, null);
                    enableBackButton();
                }
            });
            animator.setDuration(300);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    pendingAnimations.remove(this);

                    animator.start();
                    animations.add(animator);
                    disableBackButton();
                }
            };
            pendingAnimations.add(r);
            handler.post(r);
        }
        else {
            backendCollection.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            dashboard.setTranslationY(96 * metrics.density);
            dashboard.setTranslationX(-WidthDisplacement);
            backendCollection.setTranslationX(WidthDisplacement);
            dashboardContainer.setTranslationX(WidthDisplacement);
            BackendSeparator.setTranslationX(WidthDisplacement);
            dashboard.setAlpha(0f);
            backendCollection.setAlpha(0.4f);

//            backendFAB.setGlyph(Glyph.GlyphDone, false);
//            backendFAB.setBackgroundAndGlyphColors(0xFFFFFFFF, Utils.transparentColor(0.75f, 0), false);

            // Legacy actionbar view performs init earlier than backend fragment, so this likely exists
            final View DashboardAddBalance = backendActionBarView.findViewById(R.id.DashboardAddBalance);

            if (DashboardAddBalance != null) {
                final int LocationOnScreen[] = new int[2];
                DashboardAddBalance.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        DashboardAddBalance.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                        DashboardAddBalance.getLocationOnScreen(LocationOnScreen);
                        DashboardAddBalance.getLayoutParams().width = - LocationOnScreen[0] + RequiredWidth;
                        DashboardAddBalance.requestLayout();
                    }
                });
            }
        }
    }

    public void dismissDashboardEditor() {
        flushAnimations();
        backendFAB.setEnabled(false);

        dashboardEditorUp = false;

        final boolean Landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        if (Landscape) {
            dismissDashboardEditorLandscape();
        }
        else {
            dismissDashboardEditorPortrait();
        }

//        backendFAB.setAlpha(0f);
//        final ValueAnimator fabAnimator = ValueAnimator.ofFloat(0f, 1f);
//        fabAnimator.setStartDelay(300);
//        fabAnimator.setDuration(400);
//        fabAnimator.setInterpolator(Utils.CompoundInterpolator.frictionOvershootInterpolator());
//        backendFAB.setGlyph(Glyph.GlyphPlus, true);
//        backendFAB.setBackgroundAndGlyphColors(getResources().getColor(R.color.HeaderCanCheckout), 0xFFFFFFFF, true);
//        backendFAB.flashColor(getResources().getColor(R.color.HeaderCanCheckout));
//        fabAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
//            @Override
//            public void onAnimationUpdate(ValueAnimator valueAnimator) {
//                float fraction = (Float) valueAnimator.getAnimatedValue();
////                backendFAB.setAlpha(fraction);
////                backendFAB.setScaleX(Utils.interpolateValues(fraction, 0.5f, 1f));
////                backendFAB.setScaleY(backendFAB.getScaleX());
//            }
//        });
//        fabAnimator.addListener(new AnimatorListenerAdapter() {
//            @Override
//            public void onAnimationEnd(Animator animation) {
//                animations.remove(animation);
//            }
//        });
//        animations.add(fabAnimator);
//        fabAnimator.start();

        backendFAB.setEnabled(true);
        backendFAB.show();

        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(Receipt.getStaticContext());
        if (globalBudget.compareTo(new BigDecimal(0)) == 0) {
            if (budgetResetValue != 0) {
                budgetResetValue = 0;
                globalPrefs.edit().putInt(BudgetResetValueKey, budgetResetValue).apply();
            }
        }
    }

    public void dismissDashboardEditorPortrait() {

        dashboard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        if (USE_OLD_BALANCE_ANIMATIONS) dashboardEditPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        backendCollection.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (USE_OLD_BALANCE_ANIMATIONS) dashboardEditPanel.setAlpha(0f);

        final int ContainerHeight = dashboardContainer.getHeight();
        final int NewContainerHeight = getResources().getDimensionPixelSize(R.dimen.DashboardHeight);

        final int TotalTranslation = (int) (96 * metrics.density + 0.5f + Math.min(backendCollection.getScrollY(), dashboardContainer.getHeight() - 96 * metrics.density));

        if (!USE_OLD_BALANCE_ANIMATIONS) {
            $.bind(activity);

            ((ViewGroup) dashboardEditPanel).setClipChildren(false);

            $(dashboardEditPanel).children().each(new $.Each() {
                int i = 0;
                int row = 1;

                final TimeInterpolator Friction = new Utils.FrictionInterpolator(1.5f);

                @Override
                public void run(View view, int index) {
                    $ field = $(view);

                    field.finish("dashboardQueue")
                            .animate()
                            .property($.Opacity, 0)
                            .property($.TranslateY, $.dp(- 64 - DashboardPanelDisplacementStride * (4 - row)), $.Op.Add)
                            .layer(true)
                            .delay(DashboardPanelStride * row - DashboardPanelStride)
                            .duration(300)
                            .interpolator(Friction)
                            .start("dashboardQueue");

                    // The animation groups fields in pairs of 2 (title + field)
                    i++;
                    if (i == 2) {
                        row++;
                        i = 0;
                    }
                }
            });

            $.unbind();
        }

        final View DashboardEditPanel = dashboardEditPanel;
        dashboardEditPanel = null;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animations.add(animator);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();
                dashboard.setTranslationY(Utils.interpolateValues(fraction, 96 * metrics.density, 0));
                dashboard.setAlpha(fraction);

                if (USE_OLD_BALANCE_ANIMATIONS) DashboardEditPanel.setTranslationY(Utils.interpolateValues(fraction, 0, -96 * metrics.density));
                if (USE_OLD_BALANCE_ANIMATIONS) DashboardEditPanel.setAlpha(1 - fraction);

                backendCollection.setTranslationY(Utils.interpolateValues(fraction, TotalTranslation + 0.5f, 0));
                backendCollection.setAlpha(Utils.interpolateValues(fraction, 0.4f, 1f));

                dashboardContainer.getLayoutParams().height = (int) Utils.interpolateValues(fraction, ContainerHeight, NewContainerHeight);
                dashboardContainer.setTranslationY(Utils.interpolateValues(fraction, 0, -TotalTranslation + 96 * metrics.density));
                dashboardShadow.setTranslationY(dashboardContainer.getTranslationY());
                if (USE_ALIGN_FAB) backendFAB.setTranslationY(Utils.interpolateValues(fraction, 96 * metrics.density, -TotalTranslation + 96 * metrics.density));
                dashboardContainer.requestLayout();
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animations.remove(animation);

                if (emptyAnimator != null && controller.findSectionWithTag(LibrarySectionKey).getSize() == 0) {
                    emptyAnimator.start();
                }

                backendCollection.getOnScrollListener().onScroll(backendCollection, backendCollection.getScrollY(), -1);

                // Perform cleanup
                dashboard.setTranslationY(0);
                dashboard.setAlpha(1);
                backendCollection.setTranslationY(0);
                backendCollection.setAlpha(1);
                dashboardContainer.setTranslationY(-TotalTranslation + 96 * metrics.density);
                if (dashboardContainer.getLayoutParams().height != NewContainerHeight) {
                    dashboardContainer.getLayoutParams().height = NewContainerHeight;
                    dashboardContainer.requestLayout();
                }

                dashboard.setLayerType(View.LAYER_TYPE_NONE, null);
                backendCollection.setLayerType(View.LAYER_TYPE_NONE, null);
                dashboardContainer.removeView(DashboardEditPanel);
                backendCollection.requestEnableInteractions();
                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
                backendFAB.setEnabled(true);
            }
        });
        animator.setStartDelay(100);
        animator.setDuration(300);
        animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        animator.start();
    }

    public void dismissDashboardEditorLandscape() {
        //noinspection SuspiciousNameCombination
        final int RequiredWidth = metrics.heightPixels; // This is the same width as in landscape
        final int Width = getResources().getDimensionPixelSize(R.dimen.DashboardLandscapeWidth);

        final int WidthDisplacement = RequiredWidth - Width;

        final View PlaceHolder = backend.getChildAt(0);

        final View BackendSeparator = backend.findViewById(R.id.DashboardShadow);
        dashboard.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        dashboard.setVisibility(View.VISIBLE);
        if (USE_OLD_BALANCE_ANIMATIONS) dashboardEditPanel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        backendCollection.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (!USE_OLD_BALANCE_ANIMATIONS) {
            $.bind(activity);

            ((ViewGroup) dashboardEditPanel).setClipChildren(false);

            $(dashboardEditPanel).children().each(new $.Each() {
                int i = 0;
                int row = 1;

                final TimeInterpolator Friction = new Utils.FrictionInterpolator(1.5f);

                @Override
                public void run(View view, int index) {
                    $ field = $(view);

                    field.finish("dashboardQueue")
                            .animate()
                            .property($.Opacity, 0)
                            .property($.TranslateY, $.dp(- 64 - DashboardPanelDisplacementStride * (4 - row)), $.Op.Add)
                            .layer(true)
                            .delay(DashboardPanelStride * row - DashboardPanelStride)
                            .duration(300)
                            .interpolator(Friction)
                            .start("dashboardQueue");

                    // The animation groups fields in pairs of 2 (title + field)
                    i++;
                    if (i == 2) {
                        row++;
                        i = 0;
                    }
                }
            });

            $.unbind();
        }

        final View DashboardEditPanel = dashboardEditPanel;
        dashboardEditPanel = null;

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animations.add(animator);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();
                dashboard.setTranslationY(Utils.interpolateValues(fraction, 128 * metrics.density, 0));
                dashboard.setTranslationX(Utils.interpolateValues(fraction, -WidthDisplacement, 0));
                if (USE_OLD_BALANCE_ANIMATIONS) DashboardEditPanel.setTranslationY(Utils.interpolateValues(fraction, 0, -96 * metrics.density));
                backendCollection.setTranslationX(Utils.interpolateValues(fraction, WidthDisplacement, 0));
                dashboardContainer.setTranslationX(Utils.interpolateValues(fraction, WidthDisplacement, 0));
                PlaceHolder.setTranslationX(Utils.interpolateValues(fraction, 0, -WidthDisplacement));
                BackendSeparator.setTranslationX((1 - fraction) * WidthDisplacement);
                if (USE_ALIGN_FAB) backendFAB.setTranslationX((1 - fraction) * WidthDisplacement);
                dashboard.setAlpha(fraction);
                if (USE_OLD_BALANCE_ANIMATIONS) DashboardEditPanel.setAlpha(1 - fraction);
                backendCollection.setAlpha(Utils.interpolateValues(fraction, 0.4f, 1f));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animations.remove(animation);

                if (emptyAnimator != null && controller.findSectionWithTag(LibrarySectionKey).getSize() == 0) {
                    emptyAnimator.start();
                }

                // Perform cleanup
                dashboard.setTranslationY(0);
                dashboard.setTranslationX(0);
                backendCollection.setTranslationX(0);
                dashboardContainer.setTranslationX(0);
                BackendSeparator.setTranslationX(0);
                dashboard.setAlpha(1);
                backendCollection.setAlpha(1);

                dashboard.setLayerType(View.LAYER_TYPE_NONE, null);
                DashboardEditPanel.setLayerType(View.LAYER_TYPE_NONE, null);
                backendCollection.setLayerType(View.LAYER_TYPE_NONE, null);

                backend.removeView(PlaceHolder);
                backend.removeView(DashboardEditPanel);

                backendCollection.requestEnableInteractions();

                InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);

                backendFAB.setEnabled(true);
            }
        });
        animator.setStartDelay(100);
        animator.setDuration(300);
        animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        animator.start();
    }

    /**
     * Hides the edit fields from the dashboard panel, leaving only the current balance visible.
     * Sidebar mode only.
     * @param animated Controls whether this change is animated
     */
    public void hideDashboardEditor(boolean animated) {
        if (!sidebarMode) return;

        final View SearchBox = dashboardActionBarContainer.findViewById(R.id.SearchBoxLayout);
        final View Title = dashboardActionBarContainer.findViewById(R.id.DashboardEditorTitle);
        final View Button = dashboardActionBarContainer.findViewById(R.id.MenuEditDashboard);
        final View Home = dashboardActionBarContainer.findViewById(android.R.id.home);

        if (!animated) {
            SearchBox.setVisibility(View.INVISIBLE);
            Home.setVisibility(View.INVISIBLE);

            Title.setVisibility(View.VISIBLE);
            Button.setVisibility(View.VISIBLE);

            PanelBuilder.headerSettingOfPanel((ViewGroup) dashboardEditPanel).setTranslationY(metrics.density * 48 * 3 / 2);
            PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setVisibility(View.INVISIBLE);

            PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditBudget)).setVisibility(View.INVISIBLE);
            dashboardEditPanel.findViewById(R.id.DashboardEditBudget).setVisibility(View.INVISIBLE);

            PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditReset)).setVisibility(View.INVISIBLE);
            dashboardEditPanel.findViewById(R.id.DashboardEditReset).setVisibility(View.INVISIBLE);

            PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditSpent)).setVisibility(View.INVISIBLE);
            dashboardEditPanel.findViewById(R.id.DashboardEditSpent).setVisibility(View.INVISIBLE);

            return;
        }

        dashboardEditorUp = false;

        //Preparing the animation
        final View SettingRows[] = {
                PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditBudget)),
                dashboardEditPanel.findViewById(R.id.DashboardEditBudget),
                PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditReset)),
                dashboardEditPanel.findViewById(R.id.DashboardEditReset),
                PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditSpent)),
                dashboardEditPanel.findViewById(R.id.DashboardEditSpent)
        };

        for (int i = 0; i < 6; i++) {
            SettingRows[i].setLayerType(View.LAYER_TYPE_HARDWARE, null);
            SettingRows[i].setAlpha(1f);
            SettingRows[i].setVisibility(View.VISIBLE);
        }

        for (int i = 0; i < 3; i++) {
            SettingRows[i * 2].setTranslationY(0f);
            SettingRows[i * 2 + 1].setTranslationY(0f);
        }

        final float HeaderDisplacement = metrics.density * 48 * 3 / 2;

        PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setAlpha(1f);
        PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setVisibility(View.VISIBLE);

        final int SecondaryKeyline = getResources().getDimensionPixelSize(R.dimen.SecondaryKeyline);

        Title.setTranslationX(- SecondaryKeyline);
        Title.setAlpha(0f);

        SearchBox.setScaleY(1);
        Button.setScaleX(1);
        Button.setScaleY(1f);

        Home.setVisibility(View.VISIBLE);
        SearchBox.setVisibility(View.VISIBLE);

        Button.setVisibility(View.VISIBLE);
        Title.setVisibility(View.VISIBLE);

        ((DisableableFrameLayout) dashboardActionBarContainer).requestDisableInteractions();

        final TimeInterpolator Friction = new Utils.FrictionInterpolator(1.5f);

        //Running the animation
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        final ValueAnimator.AnimatorUpdateListener UpdateListener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = Friction.getInterpolation(animation.getAnimatedFraction());
                float compoundDisplacement = metrics.density * 96;
                for (int i = 0; i < 3; i++) {
                    SettingRows[i * 2].setTranslationY(Utils.interpolateValues(fraction, 0f, compoundDisplacement));
                    SettingRows[i * 2 + 1].setTranslationY(Utils.interpolateValues(fraction, 0f, compoundDisplacement));
                    SettingRows[i * 2].setAlpha(1 - fraction);
                    SettingRows[i * 2 + 1].setAlpha(1 - fraction);

                    compoundDisplacement += 48 * metrics.density;
                }

                PanelBuilder.headerSettingOfPanel((ViewGroup) dashboardEditPanel).setTranslationY(Utils.interpolateValues(fraction, 0f, HeaderDisplacement));
                PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setTranslationY(Utils.interpolateValues(fraction, 0f, HeaderDisplacement));
                PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setAlpha(1 - fraction);

                // The actionBar animation runs twice as fast
                fraction = 3f / 2f * animation.getAnimatedFraction();
                if (fraction > 1f) fraction = 1f;
                fraction = Friction.getInterpolation(fraction);

                Button.setScaleX(fraction);
                Button.setScaleY(fraction);
                Button.setAlpha(fraction);

                Title.setTranslationX(Utils.interpolateValues(fraction, - SecondaryKeyline, 0));
                Title.setAlpha(fraction);

                SearchBox.setScaleY(1 - fraction);
                Home.setTranslationX(Utils.interpolateValues(fraction, 0, -SecondaryKeyline));
                Home.setAlpha(1 - fraction);
            }
        };

        animator.addUpdateListener(UpdateListener);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animations.remove(animation);
                if (activity == null) return;

                ((ValueAnimator) animation).setCurrentPlayTime(animation.getDuration());
                UpdateListener.onAnimationUpdate((ValueAnimator) animation);
                ((DisableableFrameLayout) dashboardActionBarContainer).requestEnableInteractions();

                for (int i = 0; i < 6; i++) {
                    SettingRows[i].setLayerType(View.LAYER_TYPE_NONE, null);
                    SettingRows[i].setVisibility(View.INVISIBLE);
                }

                SearchBox.setVisibility(View.INVISIBLE);
                Home.setVisibility(View.INVISIBLE);

                Title.setVisibility(View.VISIBLE);
                Button.setVisibility(View.VISIBLE);
            }
        });
        animator.setDuration(400);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
        animations.add(animator);
    }

    /**
     * Shows the edit fields from the dashboard panel.
     * Sidebar mode only.
     * @param animated Controls whether this change is animated
     */
    public void activateDashboardEditor(final boolean animated) {
        if (!sidebarMode) return;

        dashboardEditorUp = true;

        //Preparing the animation
        final View SettingRows[] = {
                PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditBudget)),
                dashboardEditPanel.findViewById(R.id.DashboardEditBudget),
                PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditReset)),
                dashboardEditPanel.findViewById(R.id.DashboardEditReset),
                PanelBuilder.labelOfSettingFromPanel(dashboardEditPanel.findViewById(R.id.DashboardEditSpent)),
                dashboardEditPanel.findViewById(R.id.DashboardEditSpent)
        };

        final View SearchBox = dashboardActionBarContainer.findViewById(R.id.SearchBoxLayout);
        final View Title = dashboardActionBarContainer.findViewById(R.id.DashboardEditorTitle);
        final View Button = dashboardActionBarContainer.findViewById(R.id.MenuEditDashboard);
        final View Home = dashboardActionBarContainer.findViewById(android.R.id.home);

        if (!animated) {
            for (int i = 0; i < 6; i++) {
                SettingRows[i].setAlpha(1f);
                SettingRows[i].setVisibility(View.VISIBLE);
                SettingRows[i].setTranslationX(0f);
            }

            Home.setVisibility(View.VISIBLE);
            SearchBox.setVisibility(View.VISIBLE);
            Button.setVisibility(View.INVISIBLE);
            Title.setVisibility(View.INVISIBLE);

            PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setAlpha(1f);
            PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setVisibility(View.VISIBLE);
            PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setTranslationY(0f);
            PanelBuilder.headerSettingOfPanel((ViewGroup) dashboardEditPanel).setTranslationY(0f);

            return;
        }

        for (int i = 0; i < 6; i++) {
            SettingRows[i].setLayerType(View.LAYER_TYPE_HARDWARE, null);
            SettingRows[i].setAlpha(0f);
            SettingRows[i].setVisibility(View.VISIBLE);
        }

        float compoundDisplacement = metrics.density * 96;
        for (int i = 0; i < 3; i++) {
            SettingRows[i * 2].setTranslationY(compoundDisplacement);
            SettingRows[i * 2 + 1].setTranslationY(compoundDisplacement);

            compoundDisplacement += 48 * metrics.density;
        }

        final float HeaderDisplacement = metrics.density * 48 * 3 / 2;

        PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setAlpha(0f);
        PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setVisibility(View.VISIBLE);

        final int SecondaryKeyline = getResources().getDimensionPixelSize(R.dimen.SecondaryKeyline);

        Home.setTranslationX(- SecondaryKeyline);
        Home.setAlpha(0f);

        SearchBox.setScaleY(0);

        Home.setVisibility(View.VISIBLE);
        SearchBox.setVisibility(View.VISIBLE);

        ((DisableableFrameLayout) dashboardActionBarContainer).requestDisableInteractions();

        final TimeInterpolator Friction = new Utils.FrictionInterpolator(1.5f);

        //Running the animation
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        final ValueAnimator.AnimatorUpdateListener UpdateListener = new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float fraction = Friction.getInterpolation(animation.getAnimatedFraction());
                float compoundDisplacement = metrics.density * 96;
                for (int i = 0; i < 3; i++) {
                    SettingRows[i * 2].setTranslationY(Utils.interpolateValues(fraction, compoundDisplacement, 0f));
                    SettingRows[i * 2 + 1].setTranslationY(Utils.interpolateValues(fraction, compoundDisplacement, 0f));
                    SettingRows[i * 2].setAlpha(fraction);
                    SettingRows[i * 2 + 1].setAlpha(fraction);

                    compoundDisplacement += 48 * metrics.density;
                }

                PanelBuilder.headerSettingOfPanel((ViewGroup) dashboardEditPanel).setTranslationY(Utils.interpolateValues(fraction, HeaderDisplacement, 0f));
                PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setTranslationY(Utils.interpolateValues(fraction, HeaderDisplacement, 0f));
                PanelBuilder.headerSeparatorOfPanel((ViewGroup) dashboardEditPanel).setAlpha(fraction);

                // The actionBar animation runs twice as fast
                fraction = 3f / 2f * animation.getAnimatedFraction();
                if (fraction > 1f) fraction = 1f;
                fraction = Friction.getInterpolation(fraction);

                Button.setScaleX(1 - fraction);
                Button.setScaleY(1 - fraction);
                Button.setAlpha(1 - fraction);

                Title.setTranslationX(Utils.interpolateValues(fraction, 0, - SecondaryKeyline));
                Title.setAlpha(1 - fraction);

                SearchBox.setScaleY(fraction);
                Home.setTranslationX(Utils.interpolateValues(fraction, -SecondaryKeyline, 0));
                Home.setAlpha(fraction);
            }
        };

        animator.addUpdateListener(UpdateListener);
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animations.remove(animation);
                if (activity == null) return;

                ((ValueAnimator) animation).setCurrentPlayTime(animation.getDuration());
                UpdateListener.onAnimationUpdate((ValueAnimator) animation);
                ((DisableableFrameLayout) dashboardActionBarContainer).requestEnableInteractions();

                for (int i = 0; i < 6; i++) {
                    SettingRows[i].setLayerType(View.LAYER_TYPE_NONE, null);
                }

                Button.setVisibility(View.INVISIBLE);
                Title.setVisibility(View.INVISIBLE);
            }
        });
        animator.setDuration(400);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();
        animations.add(animator);
    }

}
