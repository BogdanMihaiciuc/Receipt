package com.BogdanMihaiciuc.receipt;

import android.R.interpolator;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.FragmentManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.ScaleXSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import com.BogdanMihaiciuc.receipt.BackendStorage.AbstractReceipt;
import com.BogdanMihaiciuc.receipt.BackendStorage.ReceiptFileHeader;
import com.BogdanMihaiciuc.receipt.HeaderFragment.CheckoutInformation;
import com.BogdanMihaiciuc.receipt.HelpStory.OnCloseListener;
import com.BogdanMihaiciuc.receipt.HelpStory.OnSelectPageListener;
import com.BogdanMihaiciuc.receipt.ItemCollectionFragment.PartialCheckoutItems;
import com.BogdanMihaiciuc.util.$;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.ShakeDetector;
import com.BogdanMihaiciuc.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Currency;
import java.util.Locale;

import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Item;
import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;

public class ReceiptActivity extends Activity implements OnSelectPageListener, com.BogdanMihaiciuc.util.Utils.HierarchyController,
        LegacyActionBar.OnLegacyActionSelectedListener, LegacyActionBar.ContextModeChangedListener,
        Utils.BackStack, Utils.RippleAnimationStack {
	
	final static boolean DEBUG = false;
	final static boolean DEBUG_CREATE_ORDER = false;
	final static boolean FIRSTRUN = false;
    final static boolean DEBUG_NEW_HIERARCHY = true;
	
	final static String TAG = "ReceiptActivity";
    
	final static String CurrentHelpPageKey = "com.BogdanMihaiciuc.receipt.currentHelpPage";
	final static String LastUsedTaxKey = "lastUsedTax";
	
	final static int NotificationID = 0;
	
	private View screenshotView = null;
	
	//This sets what features have been used
	public static int FeaturesUsed = 0;
	
	final static int FeatureAddItem = 1;
	final static int FeatureCrossOffItem = 2;
	final static int FeatureSetPrice = 4;
	final static int FeatureSetBudget = 8;
	final static int FeatureCheckout = 16;
	final static int FeatureHistory = 32;
	final static int FeatureDiscard = 64;
	final static int FeatureSave = 128;
	final static int FeatureSchedule = 256;
	
	final static int MinimumLandscapeSidebarDP = 600;
	final static int MinimumSidebarDP = 600;
	
	//constants
	public final static BigDecimal UnlimitedBudget = new BigDecimal(-1);
	
	//keys used for bundles
	public final static String PackageNameKey = "com.BogdanMihaiciuc.receipt";
	public final static String ItemsLeftKey = PackageNameKey + "itemsLeft";
	public final static String TotalItemsKey = PackageNameKey + "totalItems";
	public final static String LocaleKey = PackageNameKey + "moneyLocale";
	public final static String TotalKey = PackageNameKey + "total";
	public final static String BudgetKey = PackageNameKey + "budget";
    public final static String OpenIntentHandledKey = PackageNameKey + ".handledIntent";
	final static String EstimatedTotalKey = "estimatedTotal";
	final static String TaxKey = "tax";
    final static String NameKey = "listName";
	
	//keys used for preferences
	final static String LastListFilename = "last_list.receipt";
	final static String ExitedWithActiveListKey = "exitedWithActiveList";
	
	final static String IndicatorFragmentKey = "indicatorFragment";
	final static String BackendFragmentKey = "backendFragment";
	
	final static String BackendTransitionCompleteKey = "backendTransitionComplete";

    class restoreStateRunnable implements Runnable {
		ArrayList<ItemListFragment.Item> items;
		int itemsCrossed, totalItems;
		long total, budget;
		boolean budgetExceeded;
		restoreStateRunnable(ArrayList<ItemListFragment.Item> items,
			int itemsCrossed, int totalItems,
			long total, long budget,
			boolean budgetExceeded) {
				this.items = items;
				this.total = total;
				this.budget = budget;
				this.itemsCrossed = itemsCrossed;
				this.totalItems = totalItems;
				this.budgetExceeded = budgetExceeded;
		}
		public void run() {
			//restoreState(items, itemsCrossed, totalItems, total, budget, budgetExceeded);
		}
	}
	
	class showToastRunnable implements Runnable {
		private String message;
		showToastRunnable(String s) {message = s;}
		public void run() {
			showToast(message);
		}
	}
	
	static String currentLocale = "";
	static String currentTruncatedLocale = "";
	static String currentLocaleSignature = "";
	static boolean resetLocale;
	static boolean reorderItems;

    static ArrayList<Tag> invalidatedTags = new ArrayList<Tag>();
	
//	public interface OverviewFragmentType {
//
//		void showBudgetPopup(View v);
//		//These two events fire up whenever there is need to update the fragment view for the new values
//		void onBudgetChange(BigDecimal newBudget);
//		void onBudgetExceeded();
//		void onBudgetOK();
//		void onTotalChange(BigDecimal newTotal);
//		void delayInit();
//		void initNow();
//		View getView();
//
//		boolean handleBackPressed();
//		void finalizeChangesInstantly();
//
//	}
	
	//Deprecated
	//private ItemsFragment itemsFragment;
//	private ItemListFragment itemListFragment;
    private ItemCollectionFragment itemCollectionFragment;
	private TotalFragment overviewFragment;
	private HeaderFragment headerFragment;
	private IndicatorFragmentNonCompat indicatorFragment;
	private BackendFragment backendFragment;
	
	private ViewGroup root;
    private ViewGroup contentRoot;
    private ViewGroup actionBarRoot;
    private ViewGroup actionBarContainer;
    private View content;
	
	private File currentListHolder = null;
	
	private int itemsCrossed, totalItems;
	boolean budgetExceeded;
	private BigDecimal total = new BigDecimal(0);
	private BigDecimal estimatedTotal = new BigDecimal(0);
	private BigDecimal budget = UnlimitedBudget;	
	private int tax;
    private String name = "";
	
	private int currentHelpPage = -1;
	private HelpStory restoringHelpStory = null;
	private View helperHint = null;
	private boolean hintIsHidden = false;
	private boolean makeHintInstant = false;
	private boolean itemsFragmentWantsToRestore = false;
	private int helpStoryPagesCompleted = 0;
	
	private boolean sidebarMode;
	
	private Rect disabledTouchZone;
    
    private AbstractReceipt pendingReceipt;
	
	final Handler backgroundHandler = new Handler();
	
	private boolean firstRunInstance;
    private boolean restoredInstance;

    private ShakeDetector shakeDetector;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    @SuppressWarnings("deprecation")
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        $.bind(this);

    	if (DEBUG_CREATE_ORDER) Log.d(TAG, "ReceiptActivity.onCreate()");
        super.onCreate(savedInstanceState);
    	if (DEBUG_CREATE_ORDER) Log.d(TAG, "ReceiptActivity.super.onCreate()");

        if (savedInstanceState != null) {
            restoredInstance = true;
        }
        
        SettingsFragment.createCurrencyList();
		
		sidebarMode = getResources().getConfiguration().smallestScreenWidthDp >= MinimumSidebarDP ||
								(getResources().getConfiguration().smallestScreenWidthDp >= MinimumLandscapeSidebarDP 
								&& getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
		
		if (sidebarMode) {
        	getWindow().requestFeature(Window.FEATURE_ACTION_BAR_OVERLAY);
		}
        
        indicatorFragment = (IndicatorFragmentNonCompat) getFragmentManager().findFragmentByTag(IndicatorFragmentKey);
        backendFragment = (BackendFragment) getFragmentManager().findFragmentByTag(BackendFragmentKey);
        if (indicatorFragment == null) {
	        indicatorFragment = new IndicatorFragmentNonCompat();
	        getFragmentManager().beginTransaction().add(indicatorFragment, IndicatorFragmentKey).commit();
        }
        if (backendFragment == null) {
        	backendFragment = new BackendFragment();
        	getFragmentManager().beginTransaction().add(backendFragment, BackendFragmentKey).commit();
        	if (DEBUG_CREATE_ORDER) Log.d(TAG, "BackendFragment added!");
        }

        if (ReceiptFileReceiver.openFileIntent != null) {
            Intent launchIntent = ReceiptFileReceiver.openFileIntent;
            ReceiptFileReceiver.openFileIntent = null;

            if ((Intent.ACTION_VIEW).equals(launchIntent.getAction())) {
                openFile(launchIntent);
            }
        }

        setContentView(R.layout.activity_receipt_legacy_bar);
        LegacyActionBar bar = (LegacyActionBar) getFragmentManager().findFragmentById(R.id.LegacyActionBar);
        if (!bar.isRetainedInstance()) {
//            bar.setLogoResource(R.drawable.back_light);
//            bar.setCaretResource(R.drawable.null_drawable);
            bar.setBackMode(LegacyActionBarView.DoneBackMode);
            if (sidebarMode) {
                bar.setDoneResource(R.drawable.close_mini);
                bar.setBackgroundColor(getResources().getColor(R.color.ReceiptBackground));
            }
            else {
                bar.setDoneResource(R.drawable.back_light_centered);
                bar.setBackgroundColor(getResources().getColor(R.color.ActionBar));

                bar.setSeparatorVisible(true);
                bar.setSeparatorThickness(2);
                bar.setSeparatorOpacity(0.33f);

                bar.setRippleHighlightColors(LegacyRippleDrawable.DefaultLightPressedColor, LegacyRippleDrawable.DefaultLightRippleColor);
            }

            int deleteIcon = R.drawable.ic_action_delete;
            int shareIcon = R.drawable.ic_action_share_mini;
            if (sidebarMode) {
                deleteIcon = R.drawable.ic_action_delete_dark;
                shareIcon = R.drawable.ic_action_share_mini_dark;
            }
            bar.addItem(R.id.menu_discard, getString(R.string.menu_discard), deleteIcon, false, true);
            bar.addItem(R.id.menu_share, getString(R.string.MenuShare), shareIcon, false, true);
//            bar.addItem(8080, "[DEV] FLASH ICON", R.drawable.ic_action_done, false, true);
//            bar.buildItem().setId(555).setTitle("Invert selection").build();
//            bar.addItem(R.id.menu_settings, getString(R.string.menu_settings), 0, false, false);
//            bar.addItem(R.id.menu_help, getString(R.string.menu_help), 0, false, false);
        }
        
        if (Receipt.DBHelper == null) {
        	Receipt.DBHelper = new Receipt.DatabaseHelper(getApplicationContext());
        }
        
        root = (ViewGroup) getWindow().getDecorView();
        root.setBackgroundDrawable(null);
            contentRoot = (ViewGroup) findViewById(R.id.ContentContainer).getParent();
                content = findViewById(R.id.ContentContainer);

        //Fragment initialize
        FragmentManager fragmentManager = getFragmentManager();
        itemCollectionFragment = (ItemCollectionFragment) fragmentManager.findFragmentById(R.id.ItemCollectionFragment);
        headerFragment = (HeaderFragment) fragmentManager.findFragmentById(R.id.headerFragment);
        overviewFragment = (TotalFragment) fragmentManager.findFragmentById(R.id.totalFragment);
        
        if (savedInstanceState != null) {
        	//restore checkout enable state
        	//Restoring state following configuration change 
        	//No need to reload list
        	itemsCrossed = savedInstanceState.getInt(ItemsLeftKey);
        	totalItems = savedInstanceState.getInt(TotalItemsKey);
        	currentLocale = savedInstanceState.getString(LocaleKey);
        	total = new BigDecimal(savedInstanceState.getString(TotalKey));
        	budget = new BigDecimal(savedInstanceState.getString(BudgetKey));
        	estimatedTotal = new BigDecimal(savedInstanceState.getString(EstimatedTotalKey));
        	tax = savedInstanceState.getInt(TaxKey);
        	budgetExceeded = total.compareTo(budget) == 1;
        	budgetExceeded = budget.compareTo(UnlimitedBudget) == 0 ? false : budgetExceeded;
        	currentHelpPage = savedInstanceState.getInt(CurrentHelpPageKey);
            name = savedInstanceState.getString(NameKey, "");
//        	if (totalItems == 0)
//        		if (currentHelpPage == -1)
//        			placeHint(false);
//        		else
//        			placeHint(true);
        	
//        	firstRunFinalized = savedInstanceState.getBoolean(FirstRunFinalizedKey, true);
//        	firstRunIconShown = savedInstanceState.getBoolean(FirstRunIconShownKey, true);
//        	firstRunTextShown = savedInstanceState.getBoolean(FirstRunTextShownKey, true);
//        	firstRunLogoShown = savedInstanceState.getBoolean(FirstRunLogoShownKey, true);
        }
        
        if (currentListHolder == null)
        	currentListHolder = new File(getFilesDir(), LastListFilename);
        
        if (currentHelpPage != - 1) {
        	findViewById(R.id.AddItemButton).getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					findViewById(R.id.AddItemButton).getViewTreeObserver().removeGlobalOnLayoutListener(this);
					addHelpStoryPageCompleted();
					addHelpStoryPageCompleted();
					addHelpStoryPageCompleted();
				}
			});
        }
        
        final DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
		
		sidebarMode = getResources().getConfiguration().smallestScreenWidthDp >= MinimumSidebarDP ||
				(getResources().getConfiguration().smallestScreenWidthDp >= MinimumLandscapeSidebarDP && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);

        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        getLocale(globalPrefs);
        reorderItems = globalPrefs.getBoolean(SettingsFragment.AutoReorderKey, true);
        
//        if (savedInstanceState != null) {
//        	firstRunInstance = savedInstanceState.getBoolean(FirstRunInstanceKey, false);
//        	if (firstRunInstance && !savedInstanceState.getBoolean(FirstRunFinalizedKey, true)) {
//        		runFirst(savedInstanceState);
//        	}
//        }
//        else if (globalPrefs.getBoolean("firstRun", true)) {
//        	runFirst(savedInstanceState);
//        	firstRunInstance = true;
//        }

        TagStorage.loadTags();

        if (getActionBar() != null) {
            getActionBar().hide();
        }

        if (pendingReceipt != null) {
            restoreState(pendingReceipt);
            pendingReceipt = null;
        }
        
    	if (DEBUG_CREATE_ORDER) Log.d(TAG, "ReceiptActivity.onCreate() about to exit!");

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        shakeDetector = new ShakeDetector(new ShakeDetector.OnShakeListener() {
            @Override
            public void onShake() {
                if (backendFragment.getState() == BackendFragment.StateOpenList) {
                    itemCollectionFragment.order();
                }
            }
        });

    }

    public boolean isRestoredInstance() {
        return restoredInstance;
    }

    public LegacyActionBar getLegacyActionBar() {
        return (LegacyActionBar) getFragmentManager().findFragmentById(R.id.LegacyActionBar);
    }

    protected void onNewIntent(Intent intent) {
        if (intent == null) {
            return;
        }
        if ((Intent.ACTION_VIEW).equals(intent.getAction())) {
            openFile(intent);
        }
        if (ReceiptFileReceiver.openFileIntent != null) {
            Intent launchIntent = ReceiptFileReceiver.openFileIntent;
            ReceiptFileReceiver.openFileIntent = null;

            if ((Intent.ACTION_VIEW).equals(launchIntent.getAction())) {
                openFile(launchIntent);
            }
        }
    }

    protected void openFile(Intent intent) {
        backendFragment.openFile(intent, this);
    }

    public View findViewById(int id) {
        if (id == android.R.id.content) {
            return content;
        }
        return super.findViewById(id);
    }

    @Override
    public FrameLayout getRoot() {
        return (FrameLayout) root;
    }

    @Override
    public ViewGroup getContentRoot() {
        ViewParent parent = content.getParent();
        // Go up the view hierarchy until the superParent of all content views is found
        while (true) {
            if (parent.getParent() == root)
                return (ViewGroup) parent;
            parent = parent.getParent();
        }
    }

    @Override
    public ViewGroup getActionBarRoot() {
        return (ViewGroup) findViewById(LegacyActionBarView.ActionBarID).getParent();
    }

    @Override
    public ViewGroup getActionBarContainer() {
        return (ViewGroup) findViewById(LegacyActionBarView.ActionBarID).getParent();
    }

    @Override
    public View getContent() {
        return (((ViewGroup) findViewById(R.id.ContentContainer)).getChildAt(0));
    }

    public BackendFragment getBackendFragment() {
        return backendFragment;
    }
    
    public void showLoadingPlaceholder() {
		headerFragment.delayInit();
		//overviewFragment.delayInit();
    }
    
    public void hideLoadingPlaceholder() {
    	headerFragment.initNow();
    	//overviewFragment.initNow();
    }
    
    public void setDisabledTouchZone(Rect disabledZone) {
    	disabledTouchZone = disabledZone;
    }
    
    public boolean canEnterActionMode() {
    	return disabledTouchZone == null;
    }
    
    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
    	if (disabledTouchZone == null) return super.dispatchTouchEvent(e);
    	else {
    		if (disabledTouchZone.contains((int) e.getRawX(), (int) e.getRawY())) {
    			return false;
    		}
    	}
    	return super.dispatchTouchEvent(e);
    }

	@Override
	public void onActionModeStarted(final ActionMode actionMode) {
//		if (backendFragment.getState() == BackendFragment.StateBackend)
//			backgroundHandler.post( new Runnable() {
//				public void run() {
//					actionMode.finish();
//				}
//			});

        final LegacyActionBar.ContextBarWrapper wrapper;
        if (backendFragment.getState() == BackendFragment.StateBackend && !sidebarMode) {
            wrapper = backendFragment.getActionBar().createActionModeBackedContextMode(actionMode);
        }
        else {
            wrapper = ((LegacyActionBar) getFragmentManager().findFragmentById(R.id.LegacyActionBar)).createActionModeBackedContextMode(actionMode);
        }

        if (wrapper == null)
            findViewById(R.id.LegacyActionBar).setVisibility(View.INVISIBLE);
        else {

            if (Resources.getSystem().getIdentifier("action_mode_bar", "id", "android") != 0) {
                ViewGroup actionModeView = (ViewGroup) findViewById(Resources.getSystem().getIdentifier("action_mode_bar", "id", "android"));
                if (actionModeView != null) {
                    for (int i = 0, size = actionModeView.getChildCount(); i < size; i++) {
                        actionModeView.getChildAt(i).animate().cancel();
                    }
                    ((ViewGroup) actionModeView.getParent()).removeView(actionModeView);
                }
            }

            if (backendFragment.getState() == BackendFragment.StateOpenList)
                findViewById(R.id.LegacyActionBar).bringToFront();
            wrapper.setBackgroundColor(getResources().getColor(R.color.SelectionBar));
            wrapper.setSeparatorVisible(true);
            wrapper.setBackMode(LegacyActionBarView.DoneBackMode);
            backgroundHandler.post(new Runnable() {
                @Override
                public void run() {
                    wrapper.start();
                }
            });
        }

		super.onActionModeStarted(actionMode);
        onContextModeStarted();
	}

    public void onContextModeStarted() {
        headerFragment.setActionModeStarted(true);
    }

    @Override
    public void onContextModeChanged() {

    }

    @Override
	public void onActionModeFinished(ActionMode actionMode) {
        findViewById(R.id.LegacyActionBar).setVisibility(View.VISIBLE);
		super.onActionModeFinished(actionMode);
	}

    public void onContextModeFinished() {
        headerFragment.setActionModeStarted(false);
    }
    
    
    public IndicatorFragmentNonCompat getIndicator() {
    	return indicatorFragment;
    }
    
    @Override 
    protected void onSaveInstanceState(Bundle outState) {
    	super.onSaveInstanceState(outState);
    	
    	outState.putInt(TotalItemsKey, totalItems);
    	outState.putInt(ItemsLeftKey, itemsCrossed);
    	outState.putString(LocaleKey, currentLocale);
    	outState.putString(BudgetKey, budget.toString());
    	outState.putString(TotalKey, total.toString());
    	outState.putString(EstimatedTotalKey, estimatedTotal.toString());
    	outState.putInt(CurrentHelpPageKey, currentHelpPage);
    	outState.putInt(TaxKey, tax);
        outState.putString(NameKey, name);
    	
    	outState.putBoolean(FirstRunIconShownKey, firstRunIconShown);
    	outState.putBoolean(FirstRunLogoShownKey, firstRunLogoShown);
    	outState.putBoolean(FirstRunTextShownKey, firstRunTextShown);
    	outState.putBoolean(FirstRunFinalizedKey, firstRunFinalized);
    	outState.putBoolean(FirstRunInstanceKey, firstRunInstance);
    	
    }
    
    private boolean firstRunIconShown;
    private boolean firstRunLogoShown;
    private boolean firstRunTextShown;
    private boolean firstRunFinalized;
    final static String FirstRunFinalizedKey = "firstRunFinalized";
    final static String FirstRunTextShownKey = "firstRunTextShown";
    final static String FirstRunLogoShownKey = "firstRunLogoShown";
    final static String FirstRunIconShownKey = "firstRunIconShown";
    final static String FirstRunInstanceKey = "firstRunInstance";
    
    public void runFirst(Bundle savedInstanceState) {

        SharedPreferences.Editor globalPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit();
        globalPrefs.putBoolean("firstRun", false).apply();

        if (true) return; // WelcomeFragment now handles first run
        
    	View welcome = getLayoutInflater().inflate(R.layout.layout_welcome, root, true);
    	if (!firstRunLogoShown) welcome.findViewById(R.id.WelcomeLogo).setVisibility(View.INVISIBLE);
    	if (!firstRunTextShown) welcome.findViewById(R.id.WelcomeText).setVisibility(View.INVISIBLE);
    	if (!firstRunIconShown) welcome.findViewById(R.id.WelcomeIcon).setVisibility(View.INVISIBLE);
    }
    
    public void showIcon(final FrameLayout welcome, final DisplayMetrics metrics) {
    	View icon = welcome.findViewById(R.id.WelcomeIcon);
		icon.setAlpha(0);
		icon.setScaleX(0.5f);
		icon.setScaleY(0.5f);
		icon.setVisibility(View.VISIBLE);
		icon.animate()
			.alpha(1).scaleX(1).scaleY(1)
			.setStartDelay(500)
			.setDuration(700)
			.setInterpolator(new OvershootInterpolator(2f))
			.setListener(new AnimatorListenerAdapter(){
				public void onAnimationStart(Animator a) {
					firstRunIconShown = true;
				}
				@Override
				public void onAnimationEnd(Animator a) {
	    			showLogo(welcome, metrics);
				}
			});
    }
    
    public void showLogo(final FrameLayout welcome, final DisplayMetrics metrics) {
    	View logo = welcome.findViewById(R.id.WelcomeLogo);
		logo.setAlpha(0);
		logo.setTranslationY(-50 * metrics.density);
		logo.setVisibility(View.VISIBLE);
		logo.animate()
			.alpha(1).translationY(0)
			.setStartDelay(500)
			.setDuration(500)
			.setInterpolator(new DecelerateInterpolator(2))
			.setListener(new AnimatorListenerAdapter() {
				public void onAnimationStart(Animator a) {
					firstRunLogoShown = true;
				}
				@Override
				public void onAnimationEnd(Animator a) {
					showText(welcome, metrics);
				}
			});
    }
    
    public void showText(final FrameLayout welcome, final DisplayMetrics metrics) {
    	View text = welcome.findViewById(R.id.WelcomeText);
		text.setAlpha(0);
		text.setTranslationY(-50 * metrics.density);
		text.setVisibility(View.VISIBLE);
		text.animate()
			.alpha(1).translationY(0)
			.setDuration(500)
			.setStartDelay(500)
			.setInterpolator(new DecelerateInterpolator(2))
			.setListener(new AnimatorListenerAdapter() {
				public void onAnimationStart(Animator a) {
					firstRunTextShown = true;
				}
				@Override
				public void onAnimationEnd(Animator a) {
					
					finalizeWelcome(welcome, metrics);
					
				}
			});
    }
    
    public void finalizeWelcome(final FrameLayout welcome, final DisplayMetrics metrics) {
    	
		welcome.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        welcome.animate()
        	.yBy(-metrics.heightPixels)
        	.setDuration(700)
			.setStartDelay(500)
        	.setInterpolator(new AccelerateInterpolator(2))
        	.setListener(new AnimatorListenerAdapter(){
				public void onAnimationStart(Animator a) {
					firstRunFinalized = true;
				}
              	  @Override
              	  public void onAnimationEnd(Animator a){
                	root.removeView((View)welcome.getParent());
              	  }
          	});
        
        final View content = root.getChildAt(0);
        root.setBackgroundColor(0xff000000);
        content.setAlpha(0.4f);
        content.setScaleX(0.8f);
        content.setScaleY(0.8f);
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        content.animate()
    	.alpha(1f).scaleX(1).scaleY(1)
    	.setStartDelay(800)
    	.setDuration(500)
    	.setInterpolator(new AccelerateInterpolator(1))
    	.setListener(new AnimatorListenerAdapter(){
            @SuppressWarnings("deprecation")
			@Override
            public void onAnimationEnd(Animator a){
            	//Allow the hint to animate once again
            	makeHintInstant = false;
        		content.setLayerType(View.LAYER_TYPE_NONE, null);
        		root.setBackgroundDrawable(null);
        	  }
    		});

        if (hintIsHidden == false && helperHint != null) {
        	helperHint.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            helperHint.setAlpha(0);
            helperHint.setScaleX(1.3f);
            helperHint.setScaleY(1.3f);
            helperHint.animate()
        		.alpha(1f).scaleX(1).scaleY(1)
            	.setStartDelay(800)
            	.setDuration(500)
            	.setInterpolator(new AccelerateInterpolator(1))
                .setListener(new AnimatorListenerAdapter() {
	              	  @Override
	              	  public void onAnimationEnd(Animator a){
	              		  helperHint.setLayerType(View.LAYER_TYPE_NONE, null);
	              		  helperHint.animate().setStartDelay(0);
	              	  }
				});
        }
    }
    
    public void showFullscreenLoader() {
    	
    }
    
    @Override
    public void onResume() {
    	super.onResume();

        sensorManager.registerListener(shakeDetector, accelerometer, SensorManager.SENSOR_DELAY_UI);

        if (invalidatedTags.size() > 0) {

            for (Tag tag : invalidatedTags) {
                itemCollectionFragment.onTagDeleted(tag);
                backendFragment.onTagDeleted(tag);
            }

            invalidatedTags.clear();
        }

    	if (!firstRunFinalized || FIRSTRUN) {
    		
    		final DisplayMetrics metrics = new DisplayMetrics();
    		getWindowManager().getDefaultDisplay().getMetrics(metrics);
    		
    		final FrameLayout welcome = (FrameLayout) findViewById(R.id.WelcomePanel);
    		if (welcome != null) {
    			
    			if (!firstRunIconShown) showIcon(welcome, metrics);
    			else if (!firstRunLogoShown) showLogo(welcome, metrics);
    			else if (!firstRunTextShown) showText(welcome, metrics);
    			else if (!firstRunFinalized) finalizeWelcome(welcome, metrics);
    			
    		}
    	}
    }

    private ArrayList<Animator> ripples = new ArrayList<Animator>();

    public void flushRipples() {
        while (ripples.size() > 0) {
            ripples.get(0).end();
        }
    }

    public void addRipple(Animator a) {
        ripples.add(a);
    }

    public void removeRipple(Animator a) {
        ripples.remove(a);
    }

    public void stopAnimations() {
        if (itemCollectionFragment != null) {
            itemCollectionFragment.stopAnimations();
        }
        flushRipples();
    }

    public void setLabelAnimationsEnabled(boolean enabled) {
        headerFragment.setLabelAndCheckoutAnimationsEnabled(enabled, enabled);
    }

    public void setLabelAndCheckoutAnimationsEnabled(boolean enabled, boolean checkoutEnabled) {
        headerFragment.setLabelAndCheckoutAnimationsEnabled(enabled, checkoutEnabled);
    }

    public void resumeAnimations() {
        if (itemCollectionFragment != null)
            itemCollectionFragment.resumeAnimations();
    }
    
    public void postWantToRestore() {
    	if (currentHelpPage != -1 && helpStoryPagesCompleted != 3)
    		//helpStoryPagesCompleted is being checked in case we are dealing with a race condition
    		itemsFragmentWantsToRestore = true;
    	else;
    		//itemsFragment.restoreItemsAndSelection(null, null);
    }
    
//    public void placeHint(final boolean hide) {

//    	final Rect ItemsFragmentVisibleRect = new Rect();
    	//((ViewGroup)findViewById(R.id.itemsFragment)).getGlobalVisibleRect(ItemsFragmentVisibleRect);
//    	findViewById(R.id.ItemListFragment).getGlobalVisibleRect(ItemsFragmentVisibleRect);
    	
//    	if (ItemsFragmentVisibleRect.width() == 0) {
    		//((ViewGroup)findViewById(R.id.itemsFragment)).addOnLayoutChangeListener(new OnLayoutChangeListener() {
//    		findViewById(R.id.ItemListFragment).addOnLayoutChangeListener(new OnLayoutChangeListener() {
//				@Override
//				public void onLayoutChange(View v, int left, int top, int right,
//						int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
//					placeHintAfterLayout(hide);
//					v.removeOnLayoutChangeListener(this);
//				}
//			});
//    	}
//    	else
//    		placeHintAfterLayout(hide);
    	
//    }
    
    final static boolean HINT_DISABLER = true;
    
//    @SuppressWarnings("deprecation")
//	public void placeHintAfterLayout(boolean hide) {
//
//    	if (helperHint != null) return;
//    	if (HINT_DISABLER) return;
//
//    	RelativeLayout layout = new RelativeLayout(this);
//    	helperHint = layout;
//
//    	final Rect ItemsFragmentVisibleRect = new Rect();
//    	//((ViewGroup)findViewById(R.id.itemsFragment)).getGlobalVisibleRect(ItemsFragmentVisibleRect);
//    	findViewById(R.id.ItemListFragment).getGlobalVisibleRect(ItemsFragmentVisibleRect);
//
//    	RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ItemsFragmentVisibleRect.width(), ItemsFragmentVisibleRect.height());
//
//    	layout.setLayoutParams(layoutParams);
//
//		DisplayMetrics metrics = new DisplayMetrics();
//        getWindowManager().getDefaultDisplay().getMetrics(metrics);
//    	Rect rct = new Rect();
//    	getWindow().getDecorView().getWindowVisibleDisplayFrame(rct);
//
//        // Initialize device-specific dimensions
//        int buttonBarHeight, titleTopMargin, textMargin;
//        int swdp = getResources().getConfiguration().smallestScreenWidthDp;
//        if (swdp < 600) {
//        	buttonBarHeight = HelpOverlayBuilder.PhoneButtonBarHeight;
//        	titleTopMargin = HelpOverlayBuilder.PhoneTitleTopMargin;
//        	textMargin = HelpOverlayBuilder.PhoneTextMargin;
//        }
//        else {
//        	buttonBarHeight = HelpOverlayBuilder.TabletButtonBarHeight;
//        	titleTopMargin = HelpOverlayBuilder.TabletTextMargin;
//        	textMargin = HelpOverlayBuilder.TabletTextMargin;
//        }
//
//        //Construct the button bar
//        RelativeLayout buttonBar = new RelativeLayout(this);
//        RelativeLayout.LayoutParams buttonBarParams = new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, (int)(buttonBarHeight * metrics.density));
//        buttonBarParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
//        if (swdp >= 600)
//        	if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
//        		buttonBarParams.bottomMargin = HelpOverlayBuilder.TabletTextMargin;
//        	else
//        		buttonBarParams.bottomMargin = HelpOverlayBuilder.TabletTextMargin / 2;
//        buttonBar.setLayoutParams(buttonBarParams);
//
//
//        //Construct and add the cancel button
//        Button cancelButton = new Button(this);
//        cancelButton.setText("Help");
//        RelativeLayout.LayoutParams cancelParams = new RelativeLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
//	    cancelParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
//	    cancelParams.rightMargin = textMargin;
//        cancelButton.setLayoutParams(cancelParams);
//        cancelButton.setBackgroundDrawable(getResources().getDrawable(R.drawable.cling_button_bg));
//        //setBackgroundDrawable clears the padding, so padding must be set after setting the background
//        cancelButton.setPadding(HelpOverlayBuilder.TabletTextMargin, (int)(16 * metrics.density), HelpOverlayBuilder.TabletTextMargin, (int)(16 * metrics.density));
//        cancelButton.setGravity(Gravity.CENTER);
//        cancelButton.setTextColor(getResources().getColor(android.R.color.white));
//        cancelButton.setTextSize(HelpOverlayBuilder.ExplanationTextSize);
//        cancelButton.setId(1024);
//        cancelButton.setOnClickListener(new OnClickListener() {
//        	public void onClick(View v) {
//        		helperHint.findViewById(1024).setEnabled(false);
//        		startStoryMode();
//        	}
//        });
//        buttonBar.addView(cancelButton);
//        layout.addView(buttonBar);
//
//    	//Position parameters
//    	RelativeLayout.LayoutParams titleParams= new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, (int) (48 * metrics.scaledDensity));
//    	titleParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
//
//    	//Set the margin, with whatever extras the previous comparisons may have added
//        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && swdp < 600)
//        	//On phones in landscape, the text ends up being too low
//        	titleParams.topMargin = (int)(textMargin * metrics.density) ;
//        else if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && swdp >= 600)
//        	titleParams.topMargin = (int)(titleTopMargin * metrics.density) ;
//        else
//        	titleParams.topMargin = (int)(2 * titleTopMargin * metrics.density) ;
//    	titleParams.leftMargin = (int)(textMargin * metrics.density);
//    	titleParams.rightMargin = (int)(textMargin * metrics.density);
//
//    	//Actual title view
//    	TextView titleView = new TextView(this);
//    	titleView.setText(R.string.HintTitle);
//    	titleView.setTextColor(0x88000000);
//    	titleView.setTextSize(32);
//    	titleView.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
//    	titleView.setLayoutParams(titleParams);
//    	titleView.setId(1);
//    	titleView.setGravity(Gravity.CENTER);
//
//    	layout.addView(titleView);
//
//		//Position parameters
//    	RelativeLayout.LayoutParams explanationParams= new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
//    	explanationParams.leftMargin = (int)(textMargin * metrics.density);
//    	explanationParams.rightMargin = (int)(textMargin * metrics.density);
//        explanationParams.addRule(RelativeLayout.BELOW, 1);
//        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE && swdp < 600)
//        	//On phones in landscape, the text ends up being too low
//        	explanationParams.topMargin = (int)(2 * HelpOverlayBuilder.PhoneTextMargin * metrics.density);
//        else
//        	explanationParams.topMargin = (int)(5 * HelpOverlayBuilder.PhoneTextMargin * metrics.density);
//    	//Actual explanation view
//    	TextView explanationView = new TextView(this);
//    	explanationView.setText(R.string.HintDescription);
//    	explanationView.setTextColor(0x44000000);
//    	explanationView.setMaxLines(4);
//    	explanationView.setTextSize(20);
//    	explanationView.setLayoutParams(explanationParams);
//    	explanationView.setGravity(Gravity.CENTER);
//
//    	layout.addView(explanationView);
//
//    	root.addView(layout, 1);
//    	layout.setX(ItemsFragmentVisibleRect.left);
//    	layout.setY(ItemsFragmentVisibleRect.top);
//
//    	if (hide) {
//    		layout.setAlpha(0);
//    		layout.setScaleX(0.5f);
//    		layout.setScaleY(0.5f);
//    	}
//
//    	/*if (itemsFragment.isItemBeingAdded()) {
//    		layout.setVisibility(View.GONE);
//    	}*/
//    	if (/*itemListFragment.editorVisible() ||*/ backendFragment.getState() == BackendFragment.StateBackend) {
//    		layout.setVisibility(View.GONE);
//    	}
//
//    }

//    public void hideHint() {
//    	if (helperHint != null)
//    		helperHint.animate()
//    			.alpha(0)
//    			.setDuration(HelpOverlayBuilder.AnimationLength)
//    			.setStartDelay(0)
//    			.setInterpolator(new DecelerateInterpolator(2))
//    			.setListener(new AnimatorListener() {
//    				public void onAnimationStart(Animator a) {}
//    				public void onAnimationRepeat(Animator a) {}
//    				public void onAnimationCancel(Animator a) {
//		    			helperHint.findViewById(1024).setEnabled(true);
//		    			}
//    				public void onAnimationEnd(Animator a) {
//    					if (helperHint != null)
//    						helperHint.setVisibility(View.GONE);
//    				}
//    			});
//    	hintIsHidden = true;
//    }
    
//    public void hideHintInstantly() {
//    	if (helperHint != null) helperHint.setVisibility(View.GONE);
//    	hintIsHidden = true;
//    }
//
//    public void showHint() {
//    	if (helperHint != null) {
//			helperHint.findViewById(1024).setEnabled(true);
//    		if (!makeHintInstant)
//    			helperHint.animate().alpha(1).setDuration(HelpOverlayBuilder.AnimationLength)
//    				.setInterpolator(new AccelerateInterpolator(2))
//					.setStartDelay(0)
//	    			.setListener(new AnimatorListener() {
//	    				public void onAnimationStart(Animator a) {}
//	    				public void onAnimationRepeat(Animator a) {}
//	    				public void onAnimationCancel(Animator a) {}
//	    				public void onAnimationEnd(Animator a) {
//	    					if (helperHint != null)
//	    						helperHint.setVisibility(View.VISIBLE);
//	    				}});
//    		else {
//    			helperHint.setAlpha(1);
//    			helperHint.setVisibility(View.VISIBLE);
//    		}
//    	}
//    	else
//    		placeHint(false);
//    	hintIsHidden=false;
//    }
    
    public void addHelpStoryPageCompleted() {
    	helpStoryPagesCompleted++;
    	if (DEBUG) Log.d("ReceiptActivity", "Restored " + helpStoryPagesCompleted + " help pages!");
    	if (helpStoryPagesCompleted == 3) {
    		helpStoryPagesCompleted = 0;
    		startShowcase(currentHelpPage, false);
    		if (itemsFragmentWantsToRestore);
    			//itemsFragment.restoreItemsAndSelection(null, null);
    		itemsFragmentWantsToRestore = false;
    	}
    }
    
    public void startStoryMode() {
    	if (helperHint != null && !hintIsHidden)
    		helperHint.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	if (helperHint != null && !hintIsHidden) {
    		startShowcase(-1, true, HelpOverlayBuilder.AnimationLength/3);
			helperHint.animate()
				.scaleX(0.75f).scaleY(0.75f).alpha(0)
				.setDuration(HelpOverlayBuilder.AnimationLength/2)
	        	.setStartDelay(0)
				.setInterpolator(AnimationUtils.loadInterpolator(this, interpolator.decelerate_cubic))
				.setListener(new AnimatorListener() {
					public void onAnimationStart(Animator animator) { }
					public void onAnimationCancel(Animator animator) { }
					public void onAnimationRepeat(Animator animator) { }
					public void onAnimationEnd(Animator animator) { 
		        		helperHint.setLayerType(View.LAYER_TYPE_NONE, null);
					}
				});
    	}
    	else {
    		startShowcase(-1, true, 0);
    	}
    }
    public void startShowcase(int step, boolean animate) {
    	startShowcase(step, animate, 0);
    }
    
    public void startShowcase(int step, boolean animate, long delay) {
    	
    	// The showcase items depend upon which features are available
    	// and which features the user has already explored
    	HelpOverlayBuilder helpHint = new HelpOverlayBuilder(this, findViewById(R.id.AddItemButton))
    							.setTitle(getString(R.string.AddTitle))
    							.setExplanation(getString(R.string.AddDescription))
    							.setCanContinue(true)
    							.setMaxExplanationLines(5)
    							.setScale(0.66f);
    	if (totalItems < 1) {
    		helpHint.setExplanation(getString(R.string.AddDescription));
    	}
    	DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
    	int firstItem[] = new int[2];
    	findViewById(R.id.HeaderBackground).getLocationOnScreen(firstItem);
    	HelpOverlayBuilder page2;
    	//Static generation of actionbar items coordinates to alleviate headaches!
        Rect rect = new Rect();
        getWindow().getDecorView().getGlobalVisibleRect(rect);
        int checkoutX, historyX, itemY, crossItemX;
    	// Initialize device-specific dimensions
        int swdp = getResources().getConfiguration().smallestScreenWidthDp;
        boolean isLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        if (swdp < 600) {
        	//phone specific coordinates
        	page2 = new HelpOverlayBuilder(this, findViewById(R.id.total_sum), getString(R.string.BudgetTitle), 
        			getString(R.string.BudgetDescriptionPhone)).setScale(0.66f);
        	crossItemX = (int)(48 * metrics.density);
    		historyX = (int)(84 * metrics.density);
        	if (isLandscape) {
        		checkoutX = (int)(140 * metrics.density);
        		itemY = (int)(45 * metrics.density);
        	}
        	else {
        		checkoutX = (int)(140 * metrics.density);
        		itemY = (int)(49 * metrics.density);
        	}
        	if (ViewConfiguration.get(this).hasPermanentMenuKey()) {
        		//phone with hardware menu key specific coordinates
        		historyX = historyX - (int)(42 * metrics.density);
        		checkoutX = historyX - (int)(42 * metrics.density);
        	}
        }
        else {
        	//tablet specific coordinates
    		page2 = new HelpOverlayBuilder(this, findViewById(R.id.budget_sum), getString(R.string.BudgetTitle), 
    				getString(R.string.BudgetDescriptionTablet))
    				.setScale(0.8f);
        	if (!isLandscape)
        		crossItemX = (int)(48 * metrics.density);
        	else
        		crossItemX = (int)(72 * metrics.density);
    		historyX = (int)(96 * metrics.density);
    		checkoutX = (int)(160 * metrics.density);
    		itemY = (int)(52 * metrics.density);
        }
        historyX = rect.right - historyX;
        checkoutX = rect.right - checkoutX;
        if (total.compareTo(new BigDecimal(0)) == 0 && itemsCrossed != 0)
        	if (swdp >= 600 && isLandscape)
        		crossItemX = rect.right - crossItemX - (int)(320 * metrics.density);
        	else
        		crossItemX = rect.right - crossItemX;
        
        
        if (DEBUG) Log.d("ReceiptActivity", "The screen size is " + rect.right + "x" + rect.bottom);
    	
		if (DEBUG) Log.d("ReceiptActivity", "About to show the coordinates for checkout!");
    	HelpOverlayBuilder page3 = new HelpOverlayBuilder(this, checkoutX, itemY)
			.setTitle(getString(R.string.BackendTitle))
			.setExplanation(getString(R.string.BackendDescription))
			.setScale(0.66f);
    	if (swdp < 360 && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
    		page3.setTitleRequiredLineWidth((int)(120 * metrics.density));
		if (DEBUG) Log.d("ReceiptActivity", "About to show the coordinates for History!");
    	HelpOverlayBuilder page4 = new HelpOverlayBuilder(this, historyX, itemY)
    				.setTitle(getString(R.string.HistoryTitle))
    				.setExplanation(getString(R.string.HistoryDescription))
    				.setScale(0.66f);
    	if (swdp < 360 && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
    		page4.setTitleRequiredLineWidth((int)(120 * metrics.density));
    	final Context context = this;
    	if (totalItems > 0) {
        	HelpOverlayBuilder page1 = new HelpOverlayBuilder(this, crossItemX, firstItem[1] + findViewById(R.id.HeaderBackground).getHeight() + (int)(metrics.density *24))
    			.setScale(0.66f)
        		.setTitle(getString(R.string.CrossOffTitle))
        		.setExplanation(getString(R.string.CrossOffDescriptionWithTap));
        	if (itemsCrossed != 0 && total.compareTo(new BigDecimal(0)) == 0)
        		page1.setExplanation(getString(R.string.CrossOffDescriptionWithoutTap));
        	if (swdp >= 600 && isLandscape)
        		page1.setTitleRequiredLineWidth(320)
					.setExplanationMaxLineWidth(500);
        	if (itemsCrossed > 0) {
        		int pos[] = new int[2];
        		findViewById(R.id.HeaderTitle).getLocationOnScreen(pos);
        		if (swdp < 600) {
        			pos[0] = pos[0] + findViewById(R.id.HeaderTitle).getHeight()/2;
        			pos[1] = pos[1] + findViewById(R.id.HeaderTitle).getHeight()/2;
        		}
        		else {
        			if (isLandscape)
        				pos[0] = pos[0] + 2 * findViewById(R.id.HeaderTitle).getHeight()/5;
        			else
        				pos[0] = pos[0] + findViewById(R.id.HeaderTitle).getHeight()/3;
        			pos[1] = pos[1] + findViewById(R.id.HeaderTitle).getHeight()/2;
        		}
            	HelpOverlayBuilder page5 = new HelpOverlayBuilder(this, pos[0], pos[1])
            				.setTitle(getString(R.string.CheckoutTitle))
            				.setExplanation(getString(R.string.CheckoutDescription))
            				.setScale(0.66f);
        		restoringHelpStory = new HelpStory(this).addPages(helpHint, page1, page2, page5, page3, page4);
        	}
        	else
        		restoringHelpStory = new HelpStory(this).addPages(helpHint, page1, page2, page3, page4);
    	}
    	else
    		restoringHelpStory = new HelpStory(this).addPages(helpHint, page2, page3, page4);
    	restoringHelpStory.setOnSelectPageListener(this)
    			.setOnCloseListener(new OnCloseListener() {
    				public void onClose(int page) {
    					currentHelpPage = -1;
    					restoringHelpStory = null;
    					if (helperHint != null && !hintIsHidden) {
	    	        		helperHint.setLayerType(View.LAYER_TYPE_HARDWARE, null);
	    	        		helperHint.buildLayer();
    						helperHint.findViewById(1024).setEnabled(true);
	    	        		helperHint.animate()
	    	        			.scaleX(1).scaleY(1).alpha(1)
	    	        			.setDuration(HelpOverlayBuilder.AnimationLength)
	    	        			.setInterpolator(AnimationUtils.loadInterpolator(context, interpolator.decelerate_quad))
	    	        			.setListener(new AnimatorListener() {
	    	        				public void onAnimationStart(Animator animator) { }
	    	        				public void onAnimationCancel(Animator animator) {
	    	        	        		helperHint.setLayerType(View.LAYER_TYPE_NONE, null); }
	    	        				public void onAnimationRepeat(Animator animator) { }
	    	        				public void onAnimationEnd(Animator animator) { 
	    	        	        		helperHint.setLayerType(View.LAYER_TYPE_NONE, null);
	    	        				}
    	        			});
    					}
    				}
    			});
    	if (step == -1) {
    		if (totalItems == 0)
    			step = 0;
    		else if (this.itemsCrossed == 0)
    			step = 1;
    		else if (this.total.compareTo(new BigDecimal(0)) == 0)
    			step = 1;
    		else if (this.budget == UnlimitedBudget)
    			step = 2;
    		else
    			step = 3;
    	}
    	((InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0);
    	if (animate)
    		restoringHelpStory.startStoryWithPageDelayed(step, delay);
    	else
    		restoringHelpStory.startStoryWithPageInstantly(step);
    	currentHelpPage = step;
    }
    
    public void closeShowcase() {
    	if (restoringHelpStory != null) {
    		restoringHelpStory.exitStory();
    		restoringHelpStory = null;
    	}
    }

    public void restoreState(AbstractReceipt receipt) {
        restoreStateSilently(receipt, false);
    }
    
    public void restoreStateSilently(AbstractReceipt receipt, boolean silently) {
    	
    	// CrashGuard
    	// in rare cases, this gets called before onCreate; in such cases, the other fragments haven't been saved in their variables yet
    	// so the restoring state is scheduled to be run in onCreate via the pendingReceipt
    	if (headerFragment == null) {
    		pendingReceipt = receipt;
            if (DEBUG) Log.d(TAG, "Delaying state restore because fragments haven't been created yet.");
    		return;
    	}
    	
    	restoreState(receipt.items, receipt.header.itemsCrossed, receipt.items.size(), 
    			receipt.header.total, receipt.header.estimatedTotal, 
    			receipt.header.budget, 
    			receipt.header.budget.compareTo(UnlimitedBudget) == 0 ? false : 
    				receipt.header.total.add(receipt.header.total.multiply(new BigDecimal(receipt.header.tax).movePointLeft(4))).compareTo(receipt.header.budget) == 1,
    			receipt.header.tax, receipt.header.name, silently);
    }
    
    public void restoreState(ArrayList<ItemCollectionFragment.Item> items,
    		int itemsCrossed, int totalItems,
    		BigDecimal total, BigDecimal estimatedTotal, BigDecimal budget,
    		boolean budgetExceeded, int tax, String name, boolean silently) {

		if (DEBUG) Log.d("ReceiptActivity", "Restoring data returned by the background thread.");
		
		this.total = total;
		this.estimatedTotal = estimatedTotal;
		this.budget = budget;
		this.itemsCrossed = itemsCrossed;
		this.totalItems = totalItems;
		this.budgetExceeded = budgetExceeded;
		this.tax = tax;
        this.name = name;
    	//currentLocale ="�";

    	if (DEBUG) Log.d("ReceiptActivity", "headerFragment: " + headerFragment + "; overviewFragment:" + overviewFragment);
    	headerFragment.setInitial(true);
        if (!silently) {
            if (headerFragment != null) headerFragment.initNow();
            if (overviewFragment != null) overviewFragment.initNow();
        }
		
		if (items != null) {
//			itemListFragment.registerDataForRestore(items);
            itemCollectionFragment.registerDataForRestore(items);
		}
    	
    }

    public int getState() {
        return backendFragment.getState();
    }

    public boolean isSidebar() {
        return backendFragment.isSidebar();
    }
    
    public void showToast(String message) {
		Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
    
    public void appendItems(ArrayList<ItemCollectionFragment.Item> items) {
        itemCollectionFragment.addPendingItems(items);
//    	itemListFragment.addPendingItems(items);
    }
    
    public void appendToClipboard(ArrayList<ItemCollectionFragment.Item> items) {
    	headerFragment.appendToClipboard(items);
    }

    public void onTagDeleted(Tag tag) {
        backendFragment.onTagDeleted(tag);
    }
    
    // Stub
    final static boolean DISABLED_NOTIFICATION = true;
    protected void updateNotification() {
    	if (DISABLED_NOTIFICATION) return;
    	if (totalItems - itemsCrossed != 0) {
    		// TODO Finish the notification and make sure it displays correctly
	    	//ArrayList<ItemsFragment.ActionItem> items = itemsFragment.items();
//    		ArrayList<ItemListFragment.Item> items = itemListFragment.items();
            ArrayList<ItemCollectionFragment.Item> items = itemCollectionFragment.getItems();
	    	
	    	DisplayMetrics metrics = new DisplayMetrics();
	    	getWindowManager().getDefaultDisplay().getMetrics(metrics);
	    	
	    	StringBuffer contentText = new StringBuffer();
	    	int maxItems = totalItems - itemsCrossed > 6 ? 6 : totalItems - itemsCrossed;
	    	String ending = totalItems - itemsCrossed > 6 ? "..." : "";
	    	int lastItem = 0;
	    	for (int i = 0; i < maxItems; i++) {
	    		while (items.get(lastItem).crossedOff == true) lastItem++;
				contentText.append(items.get(lastItem).name);
				if (i < maxItems - 1) contentText.append(", ");
				lastItem++;
	    	}
	    	contentText.append(ending);
	    	lastItem = 0;
	    	
	    	BitmapDrawable notificationIcon = (BitmapDrawable)getResources().getDrawable(R.drawable.notification);
	    	Bitmap notificationBitmap = notificationIcon.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
	    	Canvas notificationCanvas = new Canvas(notificationBitmap);
	    	
	    	Paint countPaint = new Paint();
	    	countPaint.setAntiAlias(true);
	    	countPaint.setTextSize(12 * metrics.density);
	    	countPaint.setColor(0);
	    	countPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.MULTIPLY));
	    	countPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
	    	countPaint.setTextAlign(Paint.Align.CENTER);
	    	  
	    	notificationCanvas.drawText(String.valueOf(totalItems - itemsCrossed), 12 * metrics.density, 16 * metrics.density, countPaint);
	    	
	    	NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
		        .setSmallIcon(R.drawable.notification_small)
		        .setLargeIcon(notificationBitmap)
		        .setContentTitle(String.format(getResources().getString(R.string.ItemsLeftTitle), totalItems - itemsCrossed))
		        .setContentText(contentText);
	    	
	    	NotificationCompat.InboxStyle inboxStyle =
	    	        new NotificationCompat.InboxStyle();
	    	// Sets a title for the Inbox style big view
	    	inboxStyle.setBigContentTitle(String.format(getResources().getString(R.string.ItemsLeftTitle), totalItems - itemsCrossed));
	    	// Moves events into the big view
	    	for (int i=0; i < maxItems; i++) {
	    		while (items.get(lastItem).crossedOff == true) lastItem++;
				inboxStyle.addLine(items.get(lastItem).name);
				lastItem++;
	    	}
	    	// Moves the big view style object into the notification object.
	    	builder.setStyle(inboxStyle);
	    	
	    	// Creates an explicit intent for an Activity in your app
	    	Intent resultIntent = new Intent(this, ReceiptActivity.class);
	    	resultIntent.addCategory(Intent.CATEGORY_LAUNCHER);
	    	resultIntent.setAction(Intent.ACTION_MAIN);
	    	// The stack builder object will contain an artificial back stack for the
	    	// started Activity.
	    	// This ensures that navigating backward from the Activity leads out of
	    	// your application to the Home screen.
	    	TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
	    	// Adds the back stack for the Intent (but not the Intent itself)
	    	stackBuilder.addParentStack(ReceiptActivity.class);
	    	// Adds the Intent that starts the Activity to the top of the stack
	    	stackBuilder.addNextIntent(resultIntent);
	    	PendingIntent resultPendingIntent =
	    	        stackBuilder.getPendingIntent(
	    	            0,
	    	            PendingIntent.FLAG_UPDATE_CURRENT
	    	        );
	    	builder.setContentIntent(resultPendingIntent);
	    	//builder.setLights(0x8800aaff, 500, 1500);
	    	NotificationManager mNotificationManager =
	    	    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		    	// mId allows you to update the notification later on.
		    	mNotificationManager.notify(NotificationID, builder.build());
    	}
    }
    
    @Override
    protected void onUserLeaveHint() {
    	updateNotification();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backendFragment.handleOnBackDown();
        }
        return super.onKeyDown(keyCode, event);
    }
    
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            backendFragment.handleOnBackUp();
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (currentHelpPage != -1) return true;
            if (!backendFragment.handleMenuPressed()) {
                ((LegacyActionBar) getFragmentManager().findFragmentById(R.id.LegacyActionBar)).showOverflow();
            }

            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void pushToBackStack(Runnable r) {
        backendFragment.pushToBackStack(r);
    }

    public void popBackStackFrom(Runnable r) {backendFragment.popBackStackFrom(r);}

    public void rewindBackStackFrom(Runnable r) {backendFragment.rewindBackStackFrom(r);}

    public boolean popBackStack() {return backendFragment.popBackStack();}

    public void swipeFromBackStack(Runnable r) {backendFragment.swipeFromBackStack(r);}

    public int backStackSize() {
        return backendFragment.backStackSize();
    }

    public Utils.BackStack persistentBackStack() {
        return backendFragment;
    }
    
    @Override
    public void onBackPressed() {
        // Prevents a stack overflow exception when popping an empty backstack, which calls back to onBackPressed
        if (backendFragment.canPopBackStack()) {
            if (backendFragment.popBackStack()) return;
        }

        if (currentHelpPage != -1) {
        	closeShowcase();
            return;
        }

        if (((LegacyActionBar) getFragmentManager().findFragmentById(R.id.LegacyActionBar)).handleBackPress()) {
            return;
        }

        if (overviewFragment.handleBackPressed()) return;
        if (itemCollectionFragment.handleBackPressed()) return;
        else {
            if (backendFragment.handleBackPressed()) return;
	    	updateNotification();
	    	super.onBackPressed();
        }
    }

    static boolean getLocale(SharedPreferences globalPrefs) {
    	
    	boolean localeUpdated = resetLocale;
    	
    	String tempLocale;
        if (!globalPrefs.getBoolean(SettingsFragment.UseCurrencySymbolKey, true)) {
        	tempLocale = "";
        }
        else {
        	String defaultSymbol;
        	try {
        		defaultSymbol = Locale.getDefault().getDisplayCountry() + " - " + Currency.getInstance(Locale.getDefault()).getSymbol();
        	}
        	catch (IllegalArgumentException e) {
        		defaultSymbol = Locale.getDefault().getDisplayCountry() + " - ";
        	}
    		tempLocale = globalPrefs.getString(SettingsFragment.CurrencySymbolKey, defaultSymbol);
        }
        

        if (!tempLocale.equals(currentLocaleSignature)) {
        	currentLocaleSignature = tempLocale;
        	localeUpdated = true;
        	String[] localeParts = tempLocale.split(" - ");
        	if (localeParts.length > 1)
        		currentLocale = localeParts[1];
        	else
        		currentLocale = "";
        	
        	if (currentLocale.length() > 1)
        		currentTruncatedLocale = "";
        	else
        		currentTruncatedLocale = currentLocale;
        }
        
    	return localeUpdated;
    }
    
    @Override
    public void onStart() {
    	super.onStart();
        
    	NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);
    	notificationManager.cancel(NotificationID);
        
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        boolean tempReorderItems = globalPrefs.getBoolean(SettingsFragment.AutoReorderKey, true);
        
        if (tempReorderItems != reorderItems) {
        	reorderItems = tempReorderItems;
            // TODO
//        	itemListFragment.notifyOrderingRuleChanged();
        	//itemsFragment.notifyOrderingRuleChanged();
        }
        
        if (getLocale(globalPrefs)) {
        	resetLocale = false;
        	//itemsFragment.notifyLocaleChanged();
            // TODO
//        	itemListFragment.notifyLocaleChanged();
            itemCollectionFragment.onLocaleChanged();
            backendFragment.updateBalanceDisplay();
        	overviewFragment.onTotalChange(total);
        	overviewFragment.onBudgetChange(budget);
        }
    	
    	//itemsFragment.loadPendingItems();
//        itemListFragment.loadPendingItems();
        headerFragment.loadPendingItems();
        // TODO
        if (totalItems != itemCollectionFragment.getItems().size()) {
        	Log.e("ReceiptActivity", "Non-matching count; Activity: " + totalItems + "; Fragment: " + itemCollectionFragment.getItems().size()
        							+ "\n will now recalculate totals.");
            if (true) {
                Log.e(TAG, "items in fragment");
                for (Item item : itemCollectionFragment.getItems()) {
                    Log.e(TAG, item.name + "  " + item.qty + item.unitOfMeasurement + "  " + item.price);
                }
                throw new RuntimeException("Unable to restore items.");
            }
        	BigDecimal total = new BigDecimal(0);
        	setTotal(total);
        	BigDecimal estTotal = new BigDecimal(0);
        	setEstimatedTotal(estTotal);
        	totalItems = itemCollectionFragment.getItems().size();
        	itemsCrossed = 0;
        	for (ItemCollectionFragment.Item item : itemCollectionFragment.getItems()) {
        		if (item.price == 0) {
        			fastAddToEstimatedTotal(item.qty, item.estimatedPrice);
        			if (item.crossedOff) fastAddToTotal(item.qty, item.estimatedPrice);
        		}
        		else {
        			fastAddToEstimatedTotal(item.qty, item.price);
        			if (item.crossedOff) fastAddToTotal(item.qty, item.price);
        		}
        		if (item.crossedOff) itemsCrossed++;
        	}
        	addToItemCount(0);
        	addToCrossedOffCount(0);
        	addToEstimatedTotal(0,0);
        	addToTotal(0,0);
        }
    }
    
    public void saveStateInBackground() {
    	Thread t = new Thread() {
    		public void run() {
		        
		        FileOutputStream previousListReader;
				ArrayList<ItemCollectionFragment.Item> data = itemCollectionFragment.getItems();
				try {
					previousListReader = openFileOutput(LastListFilename, Context.MODE_PRIVATE);
					try {
						ObjectOutputStream stream = new ObjectOutputStream(previousListReader);
						int itemCount = data.size();
						stream.writeInt(itemsCrossed);
						stream.writeInt(totalItems);
			        	stream.writeLong(total.movePointRight(4).longValue());
			        	stream.writeLong(budget.movePointRight(4).longValue());
			        	stream.writeBoolean(budgetExceeded);
						stream.writeInt(itemCount);
                        for (ItemCollectionFragment.Item aData : data) {
                            aData.flatten(stream);
                            //stream.writeObject(data.get(i));
                        }
						stream.close();
					}
					catch (IOException e) {
						backgroundHandler.post(new showToastRunnable("Something went wrong, sorry!"));
					}
					catch (Exception e) {
						backgroundHandler.post(new showToastRunnable( "Something went wrong, sorry!"));
					}
				}
				catch (FileNotFoundException exception) {
					backgroundHandler.post(new showToastRunnable("Something went wrong, sorry!"));
					return;
				}
				catch (Exception e) {
					e.printStackTrace();
				}
				if (DEBUG) Log.d("ReceiptActivity/onDestroy-thread", "State saved sucessfuly!");
			}
		};
		t.start();
    }

    @Override
    protected void onStop() {
        if (totalItems != 0) {
        	//saveStateInBackground();
	        SharedPreferences.Editor exitState = getPreferences(Context.MODE_PRIVATE).edit();
	        exitState.putBoolean(ExitedWithActiveListKey, true);
	        exitState.apply();
        }
        else {
	        SharedPreferences.Editor exitState = getPreferences(Context.MODE_PRIVATE).edit();
	        exitState.putBoolean(ExitedWithActiveListKey, false);
	        exitState.apply();
        }
        super.onStop();
    }
    
    @Override
    protected void onDestroy() {
        $.unbind();

    	if (restoringHelpStory != null) {
    		restoringHelpStory.cleanup();
    		restoringHelpStory = null;
    	}
    	super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

    	if (sidebarMode) return super.onCreateOptionsMenu(menu);
    	
        // Inflate the menu; this adds items to the action bar if it is present.
    	if (backendFragment.getState() == BackendFragment.StateOpenList)
    		getMenuInflater().inflate(R.menu.activity_receipt, menu);
    	else
    		getMenuInflater().inflate(R.menu.activity_receipt_backend, menu);
        return true;
    }
    
    final static Paint countPaint;
    
    static {
    	countPaint = new Paint();
    	countPaint.setAntiAlias(true);
    	countPaint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    	countPaint.setTextAlign(Paint.Align.CENTER);
    	countPaint.setColor(0xFFFFFFFF);
    }
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
    	/*
        MenuItem item= menu.findItem(R.id.menu_checkout);
        item.setEnabled(itemsCrossed != 0);
        */
    	
    	if (sidebarMode) return super.onPrepareOptionsMenu(menu);
        
        MenuItem backend = menu.findItem(R.id.menu_backend);
        boolean backendEnabled = backendFragment.getNumberOfActiveLists() > 0;
    	
        final DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);

    	BitmapDrawable backendIcon = (BitmapDrawable)getResources().getDrawable(R.drawable.ic_backend);
    	Bitmap backendBitmap = backendIcon.getBitmap().copy(Bitmap.Config.ARGB_8888, true);
    	Canvas backendCanvas = new Canvas(backendBitmap);
        
    	countPaint.setTextSize(10 * metrics.density);
    	
    	float textX = metrics.density > 1.5f ? 17 * metrics.density : 17 * metrics.density + 0.5f;
    	  
    	backendCanvas.drawText(String.valueOf(backendFragment.getNumberOfActiveLists() > 99 ? ":)" : backendFragment.getNumberOfActiveLists()), 
    			textX, 21 * metrics.density, countPaint);
    	if (!backendEnabled)
    		backendCanvas.drawColor(0x55FFFFFF, PorterDuff.Mode.MULTIPLY);
    	
    	
    	backend.setIcon(new BitmapDrawable(getResources(), backendBitmap));
    	backend.setEnabled(backendEnabled);
    	
    	if (backendFragment.getNumberOfActiveLists() != 1)
    		backend.setTitle(String.format(getString(R.string.MenuBackendMultiple), backendFragment.getNumberOfActiveLists()));
    	else
    		backend.setTitle(String.format(getString(R.string.MenuBackendSingle), backendFragment.getNumberOfActiveLists()));
        
        boolean returnValue = super.onPrepareOptionsMenu(menu);
        return returnValue;
    }
    
    public void animationCleanup(View v) {
    	((ViewGroup)getWindow().getDecorView()).removeView(v);
    	if (DEBUG) Log.d("ReceiptActivity", "Animation callback called.");
    }
    
    public void recreateItemsView() {
    	return;
    	//itemsFragment.recreateView();
    }
    
    static Paint erasePaint = new Paint();
    
    public void onCheckoutPressed() {
    	if (canCheckout()) {
            backendFragment.handleCheckout();
        }
        else {
            halfCheckout();
        }
    }
    
    public void shareThis() {
        ReceiptCoder.sharedCoder(this).shareFileFromAnchorInActivity(getTemporaryFile(), getLegacyActionBar().obtainAnchorForItemWithID(R.id.menu_share), this);
    }
    
    public File getTemporaryFile() {

    	AbstractReceipt receipt = new AbstractReceipt();
    	receipt.header = new ReceiptFileHeader();
    	requestHeader(receipt);
    	receipt.items = getItems();

		return ReceiptCoder.sharedCoder(this).createShareableFile(receipt);
    }

    public void handleDiscard() {
        backendFragment.handleDiscard();
    }
    
	@Override
    public boolean onOptionsItemSelected(MenuItem item) {
		return onOptionsIdSelected(item.getItemId(), item);
    }

    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        onOptionsIdSelected(item.getId(), null);
    }

	@SuppressWarnings("deprecation")
	public boolean onOptionsIdSelected(int id, MenuItem item) {
    	switch (id) {
        case R.id.menu_reset:
        	return true;
        	
        case R.id.menu_share: {
        	shareThis();
        }
        return true;
        	
        case R.id.menu_discard: {
        	
        	itemCollectionFragment.handleDiscard();
            
            }
        	return true;
        	
        case R.id.menu_help: {

            backendFragment.handleHelpPressed();
            
        }
        return true;
        
        case R.id.menu_date_checkout: {
        	showDialog(7076);
        }
        return true;
        
        case R.id.menu_scrap_clear: {
        	showDialog(7075);
        }
        return true;

        case 555: {
            itemCollectionFragment.invertSelection();
            return true;
        }

        case 8080: {
            itemCollectionFragment.flashItem();
            return true;
        }
        
        case R.id.menu_history: {
        	
        	Intent historyIntent = new Intent(this, HistoryActivity.class);
        	startActivity(historyIntent);
        	
        }
        return true;
        
        case R.id.menu_settings: {
        	
//        	Intent settingsIntent = new Intent(this, SettingsActivity.class);
//        	startActivity(settingsIntent);

            backendFragment.showSettings();
        	
        }
        return true;

        case android.R.id.home:
        case R.id.menu_backend:
        	backendFragment.toggleBackendWithActionBarScreenshot(false);
        	return true;
        	
    	case R.id.menu_backup:
    		try {
    		File sd = Environment.getExternalStorageDirectory();
            File data = Environment.getDataDirectory();

            String currentDBPath = Receipt.DBHelper.getReadableDatabase().getPath();
            Receipt.DBHelper.getReadableDatabase().close();
            String backupDBPath = "Receipt Backup.bak";
            File currentDB = new File(currentDBPath);
            File backupDB = new File(sd, backupDBPath);

                FileChannel src = new FileInputStream(currentDB).getChannel();
                FileChannel dst = new FileOutputStream(backupDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
                Toast.makeText(getBaseContext(), backupDB.toString(), Toast.LENGTH_LONG).show();
    		}
    		catch(Exception e) {
    			e.printStackTrace();
    		}
    		
    		return true;
    		
    	case R.id.menu_restore:
    		try {
    		File sd = Environment.getExternalStorageDirectory();
            File data = Environment.getDataDirectory();

            String currentDBPath = Receipt.DBHelper.getReadableDatabase().getPath();
            Receipt.DBHelper.getReadableDatabase().close();
            String backupDBPath = "Receipt Backup.bak";
            File currentDB = new File(currentDBPath);
            File backupDB = new File(sd, backupDBPath);

                FileChannel dst = new FileOutputStream(currentDB).getChannel();
                FileChannel src = new FileInputStream(backupDB).getChannel();
                dst.transferFrom(src, 0, src.size());
                src.close();
                dst.close();
                Toast.makeText(getBaseContext(), backupDB.toString(), Toast.LENGTH_LONG).show();
    		}
    		catch(Exception e) {
    			e.printStackTrace();
    		}
    		return true;

            case 1991:
                Log.d(TAG, "Diagnose before layout: ");
                runDiagnose();
                ((ViewGroup) findViewById(R.id.ItemCollection)).getChildAt(0).requestLayout();
                return true;
        	
        default:
            return super.onOptionsItemSelected(item);
	    }
	}

    private void runDiagnose() {
        ((CollectionView) findViewById(R.id.ItemCollection)).runForEachVisibleView(new CollectionView.ViewRunnable() {
            @Override
            public void runForView(View view, Object object, int viewType) {
                View parent = (View) view.getParent();
                String visibility;
                if (parent.getVisibility() == View.VISIBLE) {
                    visibility = "visible";
                }
                else {
                    visibility = "not visible";
                }
                int width = parent.getWidth();
                if (width == 0) {
                    parent.requestLayout();
                }
                Log.d(TAG, "View for item " + object.toString() + " visibility is " + visibility + ", width is " + width);
            }
        });
    }
	
	@SuppressWarnings("deprecation")
	public void classicDiscard() {

    	//Set up the animation layer
    	final View content = root.findViewById(android.R.id.content);

    	Rect contentRect = new Rect();
    	content.getGlobalVisibleRect(contentRect);
    	
    	//Make hint changes instant to make sure they don't bog down the animation
    	makeHintInstant = true;
    	
    	//Cleanup the previous animations, if they were running
    	if (screenshotView != null) {
    		root.removeView(screenshotView);
    		screenshotView = null;
    	}
    	
    	Rect rct = new Rect();
    	root.getWindowVisibleDisplayFrame(rct);
    	Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
    	Canvas bitmapCanvas = new Canvas(bitmap);
    	bitmapCanvas.clipRect(contentRect);
        root.draw(bitmapCanvas);
    	screenshotView = new View(this);
    	BitmapDrawable background = new BitmapDrawable(getResources(), bitmap);
    	screenshotView.setBackgroundDrawable(background);
    	//dimmerView = new View(this);
    	//dimmerView.setBackgroundColor(getResources().getColor(R.color.Black));
    	screenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	root.addView(screenshotView);

        root.setBackgroundColor(0xff000000);
        content.setAlpha(0.4f);
    	backendFragment.prepareNewReceipt();
    	findViewById(R.id.AddItemButton).setEnabled(false); 
        
        //Animate the screenshot
        screenshotView.setPivotX(0f);
        screenshotView.setPivotY(rct.bottom);
        screenshotView.animate()
        	.alpha(0f).rotationBy(20).xBy(rct.exactCenterX())
        	.setDuration(500)
        	.setInterpolator(new AccelerateInterpolator(1.33f))
        	.setListener(new AnimatorListener(){
        		public void onAnimationStart(Animator a){}
        		public void onAnimationRepeat(Animator a){}
        		public void onAnimationCancel(Animator a){
                  	//Allow the hint to animate once again
                  	makeHintInstant = false;
          		  	if (screenshotView != null) screenshotView.setLayerType(View.LAYER_TYPE_NONE, null);
            		root.removeView(screenshotView);
        			screenshotView = null; //it has been cleaned by animationCleanup(View);
              	  }
              	  @Override
              	  public void onAnimationEnd(Animator a){
                  	//Allow the hint to animate once again
                  	makeHintInstant = false;
          		  	if (screenshotView != null) screenshotView.setLayerType(View.LAYER_TYPE_NONE, null);
            		root.removeView(screenshotView);
        			screenshotView = null; //it has been cleaned by animationCleanup(View);
          	  }
          	});
        
        //Fade the content layer
        //This is MUCH MUCH smoother than setting a black rectangle above it and fading it
        //As there's extra rendering involved when it fades
        content.animate()
	    	.alpha(1f)
	    	.setStartDelay(150)
	    	.setDuration(250)
	    	.setInterpolator(new AccelerateInterpolator(1))
	    	.setListener(new AnimatorListenerAdapter(){
	              	  public void onAnimationEnd(Animator a){
		        		content.setLayerType(View.LAYER_TYPE_NONE, null);
		        		content.setAlpha(1);
		        		root.setBackgroundDrawable(null);
		              	findViewById(R.id.AddItemButton).setEnabled(true);
		              	if (DEBUG) Log.d("ReceiptActivity", "Secondary animation cleanup performed!");
		        	  }
	    	});
	}
            
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case 7076:
            final DatePickerDialog picker = new DatePickerDialog(this, 
                    null,
                    Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DATE));
            picker.setCancelable(true);
            picker.setCanceledOnTouchOutside(true);
            picker.setButton(DialogInterface.BUTTON_POSITIVE, "OK",
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Calendar date = Calendar.getInstance();
                        date.set(Calendar.YEAR, picker.getDatePicker().getYear());
                        date.set(Calendar.MONTH, picker.getDatePicker().getMonth());
                        date.set(Calendar.DATE, picker.getDatePicker().getDayOfMonth());
                        checkout(date.getTimeInMillis()/1000);
                    }
                });
            picker.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", 
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Log.d("Picker", "Cancel!");
                    }
                });
            return picker;
	    case 7075:
	        final DatePickerDialog deletePicker = new DatePickerDialog(this, 
	                null,
	                Calendar.getInstance().get(Calendar.YEAR), Calendar.getInstance().get(Calendar.MONTH), Calendar.getInstance().get(Calendar.DATE));
	        deletePicker.setCancelable(true);
	        deletePicker.setCanceledOnTouchOutside(true);
	        deletePicker.setButton(DialogInterface.BUTTON_POSITIVE, "OK",
	            new DialogInterface.OnClickListener() {
	                @Override
	                public void onClick(DialogInterface dialog, int which) {
	                    Calendar date = Calendar.getInstance();
	                    date.set(Calendar.YEAR, deletePicker.getDatePicker().getYear());
	                    date.set(Calendar.MONTH, deletePicker.getDatePicker().getMonth());
	                    date.set(Calendar.DATE, deletePicker.getDatePicker().getDayOfMonth());
	                    deleteScrap(date.getTimeInMillis()/1000);
	                }
	            });
	        deletePicker.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", 
	            new DialogInterface.OnClickListener() {
	                @Override
	                public void onClick(DialogInterface dialog, int which) {
	                    Log.d("Picker", "Cancel!");
	                }
	            });
	        return deletePicker;
        }
        return null;
    }
    
    public void deleteScrap(long month) {
		SQLiteDatabase database = Receipt.DBHelper.getWritableDatabase();
		Cursor query = database.query(Receipt.DBReceiptsTable, Receipt.DBAllReceiptColumns,  
						"strftime("+StatsFragment.DateFormatMonthPrecision+", "+Receipt.DBDateKey+", 'unixepoch') =" +
								"= strftime("+StatsFragment.DateFormatMonthPrecision+", "+month+", 'unixepoch')", null, null, null, null);

		for (int i = 0; i < query.getCount(); i++) {
			query.moveToPosition(i);
			database.delete(Receipt.DBItemsTable, Receipt.DBTargetDBKey + "==" + query.getLong(Receipt.DBFilenameIdKeyIndex), null);
		}
		query.close();
		database.delete(Receipt.DBReceiptsTable, 
				"strftime("+StatsFragment.DateFormatMonthPrecision+", "+Receipt.DBDateKey+", 'unixepoch') =" +
						"= strftime("+StatsFragment.DateFormatMonthPrecision+", "+month+", 'unixepoch')", null);
		database.close();
    }
    
    @Override
    protected void onPause() {
        sensorManager.unregisterListener(shakeDetector);

        // OnStop is unreliable
    	if (!isChangingConfigurations()) {
    		overviewFragment.finalizeChangesInstantly();
//    		itemListFragment.commitEditorInstantly();
            itemCollectionFragment.finalizeChangesInstantly();
    	}
        backendFragment.onActivityPaused();
    	super.onPause();
    }
    
    //Onclick events
    
    public void showBudgetPopup(View v) {
    	overviewFragment.showBudgetPopup(v);
    }
    
    public void resetBudget(View v) {
    	budget = UnlimitedBudget;
    }
    
    //Onclick for the plus button; stub calling the Fragment's implementation
    public void addNewItemToList(View v) {
//    	itemListFragment.addNewItemToList();
        itemCollectionFragment.addNewItemToList();
//        hideHint();
    }
    
    public void editItemField(View v) {
    	//itemsFragment.editItemField(v);
    }
    
    @Deprecated
    public void showEditDialog() {
    	if (DEBUG) Log.d("ReceiptActivity", "Should create editDialog, but this method is now a stub!");
    }

    public void onFinishEditDialog(BigDecimal inputValue) {
    	setBudget(inputValue);
        
    }
    
    public boolean budgetIsExceeded() {
    	return budgetExceeded;
    }

    public BigDecimal getBalance() {
        if (getBudget().compareTo(UnlimitedBudget) != 0) {
            return getBudget().subtract(getCrossedOffCount() != 0 ? getTotal() : getEstimatedTotal());
        }
        else {
            return UnlimitedBudget;
        }
    }

	public BigDecimal getBudget() {
		return budget;
	}

	public void setBudget(BigDecimal inputValue) {
		if ((inputValue.stripTrailingZeros().compareTo(new BigDecimal(0)) <= 0 || inputValue.movePointRight(2).compareTo(new BigDecimal(Long.MAX_VALUE)) >= 0)
				&& inputValue.compareTo(UnlimitedBudget) != 0) {
			setBudget(UnlimitedBudget);
			return;
		}
		budget = inputValue.setScale(2, RoundingMode.HALF_EVEN);
		overviewFragment.onBudgetChange(inputValue);
		if (getBudget().compareTo(UnlimitedBudget) == 0) {
			if (budgetExceeded) {
				budgetExceeded = false;
	        	headerFragment.onBudgetOK();
			}
			// This fragment may be tracking the estimated budget
        	overviewFragment.onBudgetOK();
			return;
		}
        if (getTotal().compareTo(getBudget()) == 1 && !budgetExceeded) {
        	budgetExceeded = true;
        	headerFragment.onBudgetExceeded();
        	overviewFragment.onBudgetExceeded();
        }
        if (getTotal().compareTo(getBudget()) < 1 && budgetExceeded) {
        	budgetExceeded = false;
        	headerFragment.onBudgetOK();
        	overviewFragment.onBudgetOK();
        }
	}
	
	public void setTax(int tax) {
        backendFragment.removeFromUsedGlobalBudget(getTotal());
		this.tax = tax;
		if (tax < 0) this.tax = 0;
		if (tax > 10000) this.tax = 10000;
		PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putInt(LastUsedTaxKey, this.tax).apply();
		setEstimatedTotal(estimatedTotal);
		setTotal(total);
		itemCollectionFragment.refreshActionMode();
        backendFragment.addToUsedGlobalBudget(getTotal());
	}
	
	public int getTax() {
		return tax;
	}

	public BigDecimal getTotal() {
		if (tax == 0) return total; //faster when there's no tax
		return total.add(total.multiply(new BigDecimal(tax).movePointLeft(4)));
	}
	
	public BigDecimal getSubtotal() {
		return total;
	}

	public void setTotal(BigDecimal total) {
        backendFragment.removeFromUsedGlobalBudget(getTotal());
		this.total = total;
        backendFragment.addToUsedGlobalBudget(getTotal());
		overviewFragment.onTotalChange(total);
		if (getBudget().compareTo(UnlimitedBudget) == 0) {
			if (budgetExceeded) {
				budgetExceeded = false;
	        	headerFragment.onBudgetOK();
	        	overviewFragment.onBudgetOK();
			}
			return;
		}
        if (getTotal().compareTo(getBudget()) == 1 && !budgetExceeded) {
        	budgetExceeded = true;
        	headerFragment.onBudgetExceeded();
        	overviewFragment.onBudgetExceeded();
        }
        if (getTotal().compareTo(getBudget()) < 1 && budgetExceeded) {
        	budgetExceeded = false;
        	headerFragment.onBudgetOK();
        	overviewFragment.onBudgetOK();
        }
	}
	
	@Deprecated
	public void addToTotal(long priceToAdd) {
		setTotal(total.add(new BigDecimal(priceToAdd).movePointLeft(4)));
	}

	public void addToTotal(long qty, long price) {
		if (ItemListFragment.DEBUG_PRICES) Log.d(ItemListFragment.TAG, "Adding " + qty + ":" + price + " to total.");
		if (qty == 0) {
            directAddToTotal(10000, price);
        }
		else {
            directAddToTotal(qty, price);
        }
	}
	
	public void directAddToTotal(long qty, long price) {
//        backendFragment.addToUsedGlobalBudget(
//                new BigDecimal(qty)
//                        .movePointLeft(4)
//                        .multiply(new BigDecimal(price)
//                                .movePointLeft(2))
//        );
		setTotal(total.add(
					new BigDecimal(qty)
					.movePointLeft(4)
					.multiply(new BigDecimal(price)
								.movePointLeft(2))
					)
				);
	}
	
	public void fastAddToTotal(long qty, long price) {
		if (qty == 0) {
			total = total.add(new BigDecimal(price).movePointLeft(2));
        }
		else
			total = total.add(
								new BigDecimal(qty)
								.movePointLeft(4)
								.multiply(new BigDecimal(price)
											.movePointLeft(2))
								);
	}

	public BigDecimal getEstimatedTotal() {
		if (tax == 0) return estimatedTotal; //faster when there's no tax
		return estimatedTotal.add(estimatedTotal.multiply(new BigDecimal(tax).movePointLeft(4)));
	}
	
	public BigDecimal getEstimatedSubtotal() {
		return estimatedTotal;
	}

	public void setEstimatedTotal(BigDecimal estimatedTotal) {
		this.estimatedTotal = estimatedTotal;
		overviewFragment.onTotalChange(estimatedTotal);
	}
	
	public void addToEstimatedTotal(long qty, long price) {
		if (qty == 0)
			directAddToEstimatedTotal(10000, price);
		else
			directAddToEstimatedTotal(qty, price);
	}
	
	public void directAddToEstimatedTotal(long qty, long price) {
		setEstimatedTotal(estimatedTotal.add(
					new BigDecimal(qty)
					.movePointLeft(4)
					.multiply(new BigDecimal(price)
								.movePointLeft(2))
					)
				);
	}
	
	public void fastAddToEstimatedTotal(long qty, long price) {
		if (qty == 0)
			estimatedTotal = estimatedTotal.add(new BigDecimal(price).movePointLeft(2));
		else
			estimatedTotal = estimatedTotal.add(
								new BigDecimal(qty)
								.movePointLeft(4)
								.multiply(new BigDecimal(price)
											.movePointLeft(2))
								);
	}
	
	/*public void removeFromTotal(long priceToAdd) {
		setTotal(total - priceToAdd);
	}*/
	
	public int getCrossedOffCount() {
		return itemsCrossed;
	}
	
	public ArrayList<ItemCollectionFragment.Item> getItems() {
		return itemCollectionFragment.getItems();
	}
	
	public boolean startedList() {
		return (totalItems != 0);
	}
	
	public boolean canCheckout() {
		return ((itemsCrossed == totalItems) && (totalItems != 0));
	}
	
	public int getRemainingItemCount() {
		return totalItems - itemsCrossed;
	}
	
	public void addToItemCount(int itemsToAdd) {
		totalItems += itemsToAdd;
//		if (totalItems==0)
//			showHint();
		headerFragment.onCrossedOffCountChange(itemsCrossed);
		invalidateOptionsMenu();
	}
	
	public void addToCrossedOffCount(int itemsToAdd) {
		itemsCrossed += itemsToAdd;
		headerFragment.onCrossedOffCountChange(itemsCrossed);
		invalidateOptionsMenu();
	}
	

	public void fastAddToTotal(long priceToAdd) {
		total = total.add(new BigDecimal(priceToAdd).movePointLeft(4));
	}
	
	public void fastAddToItemCount(int itemsToAdd) {
		totalItems += itemsToAdd;
	}

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        if (headerFragment != null) {
            headerFragment.onNameChanged(this.name);
        }
    }

    public static CharSequence titleFormattedString(String source) {
        SpannableStringBuilder title = Utils.appendWithSpan(new SpannableStringBuilder(), source, new Utils.CustomTypefaceSpan(Receipt.condensedTypeface()));
        title.setSpan(new AbsoluteSizeSpan(24, true), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return title;
    }
	
	static public String longToDecimalString(long value) {
		
		String displayString = Long.toString(value);
		if (displayString.length() < 3) displayString = "0" + displayString;
		if (displayString.length() < 3) displayString = "0" + displayString;
		return displayString.substring(0, displayString.length() - 2) + "."
				+ displayString.substring(displayString.length() - 2);
		
	}

    public static CharSequence longToFormattedString(long value, Object span) {
        return bigDecimalToFormattedString(new BigDecimal(value).movePointLeft(2), span);
    }

    public static CharSequence bigDecimalToFormattedString(BigDecimal value, Object span) {
        String base = currentTruncatedLocale + value.setScale(2, RoundingMode.HALF_EVEN).toPlainString();

//        Object format = span == null ? Receipt.textLightSpan() : span;
//
//        SpannableStringBuilder formattedTotal = new SpannableStringBuilder(base);
//        if (currentTruncatedLocale.length() > 0) formattedTotal.setSpan(format, 0, currentTruncatedLocale.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        int pointIndex = base.indexOf('.');
//        if (pointIndex != -1) {
//            formattedTotal.setSpan(format, pointIndex, formattedTotal.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//        }
        return base;
    }

	public static CharSequence phoneQuantityFormattedString(Context context, long qty, String measurement) {
		
		if (qty == 0) qty = 10000;
		
		BigDecimal format = new BigDecimal(qty).movePointLeft(4).stripTrailingZeros();
		String tempString = format.toPlainString();
		
		if (tempString.length() == 1) {
			return format.setScale(1, RoundingMode.HALF_UP).toPlainString() + measurement;
		}

		if (tempString.length() >= 4) {
			if (tempString.indexOf('.') == 1)
				return tempString;
			format = format.setScale(1, RoundingMode.DOWN);
			SpannableStringBuilder builder = new SpannableStringBuilder();
			String formatString = format.toPlainString();
			builder.append(formatString);
			builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.implicit_text_colors)), 
					formatString.indexOf('.'), formatString.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
			return builder;
		}
		else {
			return tempString + measurement;
		}
		
	}
	
	public static CharSequence quantityFormattedString(Context context, long qty, String measurement) {
		
		if (qty == 0) qty = 10000;
		
		BigDecimal format = new BigDecimal(qty).movePointLeft(4).stripTrailingZeros();
		String tempString = format.toPlainString();
		Configuration config = context.getResources().getConfiguration();
		if (tempString.length() == 1) {
			return format.setScale(1, RoundingMode.HALF_UP).toPlainString() + measurement;
		}
		if (config.smallestScreenWidthDp >= 600 || config.orientation == Configuration.ORIENTATION_LANDSCAPE) 
			return tempString + measurement;
		if (tempString.length() >= 5) {
			if (tempString.indexOf('.') == 1)
				return tempString;
			format = format.setScale(2, RoundingMode.DOWN);
			SpannableStringBuilder builder = new SpannableStringBuilder();
			String formatString = format.toPlainString();
			builder.append(formatString);
			builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.implicit_text_colors)), 
					formatString.indexOf('.'), formatString.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
			return builder;
		}
		else {
			return tempString + measurement;
		}
		
	}
	
	final static int PortraitInitialCutoff = 8;
	final static int LandscapeInitialCutoff = 16;
	
	public static CharSequence totalFormattedString(Context context, BigDecimal price) {
		
		int initialCutoff;
		
		Configuration config = context.getResources().getConfiguration();
		if (config.smallestScreenWidthDp < 600) {
			if (config.orientation == Configuration.ORIENTATION_PORTRAIT) {
				initialCutoff = PortraitInitialCutoff;
			}
			else {
				initialCutoff = LandscapeInitialCutoff;
			}
		}
		else {
			if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
				initialCutoff = PortraitInitialCutoff;
			}
			else {
				initialCutoff = LandscapeInitialCutoff;
			}
		}
		
		return totalFormattedStringWithSpecifiedCutoff(context, price, initialCutoff);
	}
	
	public static CharSequence totalFormattedStringWithSpecifiedCutoff(Context context, BigDecimal price, int initialCutoff) {
		
		if (ReceiptActivity.currentLocale.length() <= 1)
			initialCutoff++;
		
		String formattedString = price.setScale(2, RoundingMode.HALF_UP).toPlainString();
		int length = formattedString.length();
		if (length <= initialCutoff) {
			if (ReceiptActivity.currentLocale.length() > 2) {
				SpannableStringBuilder builder = new SpannableStringBuilder();
				builder.append(formattedString).append(ReceiptActivity.currentLocale);
                // TODO: Ugly
//				builder.setSpan(new TypefaceSpan("sans-serif-condensed-light"), 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new ScaleXSpan(0.8f), builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new RelativeSizeSpan(0.66f), builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new Utils.CustomTypefaceSpan(Receipt.condensedTypeface())
                        , builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.ItemImplicitValue))
                        , builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				return builder;
			}
			else {
                return currentLocale + formattedString;
//                SpannableStringBuilder formattedTotal = new SpannableStringBuilder();
//                formattedTotal.append(currentLocale).append(formattedString);
////                formattedTotal.setSpan(Receipt.textLightSpan(), 0, currentLocale.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
//                int pointIndex = formattedString.indexOf('.');
//                if (pointIndex != -1) {
//                    formattedTotal.setSpan(Receipt.textLightSpan(), pointIndex + currentLocale.length(), formattedTotal.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
//                }
//                return formattedTotal;
            }
		}
		else if (length > initialCutoff && length <= initialCutoff + 2) {
			return formattedString;
		}
		else if (length > initialCutoff + 2 && length <= initialCutoff + 4) {
			return price.setScale(0, RoundingMode.HALF_UP).toPlainString();
		}
		else if (length > initialCutoff + 4 && length <= initialCutoff + 5) {
			return price.movePointLeft(3).setScale(0, RoundingMode.HALF_UP).toPlainString() + "k";
		}
		else if (length > initialCutoff + 5 && length <= initialCutoff + 8) {
			return price.movePointLeft(6).setScale(0, RoundingMode.HALF_UP).toPlainString() + "m";
		}
		else if (length > initialCutoff + 8 && length <= initialCutoff + 11) {
			return price.movePointLeft(9).setScale(0, RoundingMode.HALF_UP).toPlainString() + "b";
		}
		else if (length > initialCutoff + 11 && length <= initialCutoff + 14) {
			return price.movePointLeft(12).setScale(0, RoundingMode.HALF_UP).toPlainString() + "t";
		}
		else {
			return price.movePointLeft(15).setScale(0, RoundingMode.HALF_UP).toPlainString() + "q";
		}
	}
	
	public static CharSequence shortFormattedTotalWithCutoff(Context context, long price, int initialCutoff) {
		return shortFormattedTotalWithCutoff(context, new BigDecimal(price).movePointLeft(2), initialCutoff);
	}
	
	public static CharSequence shortFormattedTotalWithCutoff(Context context, BigDecimal price, int initialCutoff) {
		
		if (ReceiptActivity.currentLocale.length() <= 1)
			initialCutoff++;
		
		String formattedString = price.setScale(0, RoundingMode.HALF_UP).toPlainString();
		int length = formattedString.length();
		if (length <= initialCutoff) {
			if (ReceiptActivity.currentLocale.length() > 2) {
				SpannableStringBuilder builder = new SpannableStringBuilder();
				builder.append(formattedString).append(ReceiptActivity.currentLocale);
                // TODO: ugly
//				builder.setSpan(new TypefaceSpan("sans-serif-condensed-light"), 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new ScaleXSpan(0.8f), builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new RelativeSizeSpan(0.66f), builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new Utils.CustomTypefaceSpan(Receipt.condensedTypeface())
                        , builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.ItemImplicitValue))
                        , builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				return builder;
			}
			else {
                return currentLocale + formattedString;
//                SpannableStringBuilder formattedTotal = new SpannableStringBuilder(currentLocale + formattedString);
//                formattedTotal.setSpan(Receipt.textLightSpan(), 0, currentLocale.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                return formattedTotal;
            }
		}
		else if (length > initialCutoff && length <= initialCutoff + 2) {
			return formattedString;
		}
		else if (length > initialCutoff + 2 && length <= initialCutoff + 4) {
			return price.movePointLeft(3).setScale(0, RoundingMode.HALF_UP).toPlainString() + "k";
		}
		else if (length > initialCutoff + 4 && length <= initialCutoff + 7) {
			return price.movePointLeft(6).setScale(0, RoundingMode.HALF_UP).toPlainString() + "m";
		}
		else if (length > initialCutoff + 7 && length <= initialCutoff + 10) {
			return price.movePointLeft(9).setScale(0, RoundingMode.HALF_UP).toPlainString() + "b";
		}
		else if (length > initialCutoff + 10 && length <= initialCutoff + 13) {
			return price.movePointLeft(12).setScale(0, RoundingMode.HALF_UP).toPlainString() + "t";
		}
		else {
			return price.movePointLeft(15).setScale(0, RoundingMode.HALF_UP).toPlainString() + "q";
		}
	}
	
	@Deprecated
	static public String longToTruncatedDecimalString(long value) {
		
		String displayString = Long.toString(value);
		if (displayString.length() < 3) displayString = "0" + displayString;
		if (displayString.length() < 3) displayString = "0" + displayString;
		if (DEBUG) Log.d("ReceiptActivity", "Will truncate " + displayString);
		return displayString.substring(0, displayString.length() - 2) + "." 
				+ displayString.substring(displayString.length() - 2, displayString.length() - 1);
		
	}
	
	static public String totalToTruncatedDecimalString(long value) {

		String displayString = Long.toString(value);
		while (displayString.length() < 5) displayString = "0" + displayString;
		if (DEBUG) Log.d("ReceiptActivity", "Will truncate " + displayString);
		String string = displayString.substring(0, displayString.length() - 4) + "." 
				+ displayString.substring(displayString.length() - 4, displayString.length() - 2);
		string.length();
		
		return longToDecimalString(value);
		
	}
	
	public static String longFormattedPrice(BigDecimal value) {
		
		String displayString = value.setScale(2, RoundingMode.HALF_EVEN).toString();
		
		if (displayString.length() > 8) {
			
		}
		
		return displayString;
	}
	
	public void requestHeader(AbstractReceipt receipt) {
		ReceiptFileHeader header = receipt.header;
		header.budget = getBudget();
		header.itemsCrossed = getCrossedOffCount();
		header.total = getSubtotal();
		header.estimatedTotal = getEstimatedSubtotal();
		header.totalItems = totalItems;
		header.tax = tax;
        header.name = name;
	}

	@Override
	public void onSelectPage(int page) {
		currentHelpPage = page;
	}

    public boolean closeEditorAndKeyboard() {
        return closeEditorAndKeyboard(false);
    }

	public boolean closeEditorAndKeyboard(boolean animated) {
		overviewFragment.finalizeChangesInstantly();
//		return itemListFragment.commitEditorInstantly();
        if (animated) {
            itemCollectionFragment.finalizeChanges();
        }
        else {
            itemCollectionFragment.finalizeChangesInstantly();
        }
        return false;
	}

    public void dismissContextModes() {
        itemCollectionFragment.dismissContextModes(true);
    }

    public void dismissContextModes(boolean animated) {
        itemCollectionFragment.dismissContextModes(animated);
    }

    public void dismissBackendContextModes() {
        backendFragment.dismissContextModes();
    }

    public void setListVisible(boolean visible, boolean animated, int delay) {
        headerFragment.setListVisible(visible, animated, delay);
    }
	
	@SuppressWarnings("deprecation")
	public void halfCheckout() {

    	//So far, this may not run while other animations are onscreen
    	//so animation cleanup is not required (for now)
    	//Set up the animation layer
        final boolean SidebarMode = backendFragment.isSidebar();

    	final View Content = SidebarMode ? findViewById(R.id.ActivityContainer) : ((ViewGroup) findViewById(R.id.ContentContainer)).getChildAt(0);

    	Rect contentRect = new Rect();
    	Content.getGlobalVisibleRect(contentRect);
    	
    	//Make hint changes instant to make sure they don't bog down the animation
    	makeHintInstant = true;

//    	Rect rct = new Rect();
//    	root.getWindowVisibleDisplayFrame(rct);
    	Bitmap bitmap = Bitmap.createBitmap(Content.getWidth(), Content.getHeight(), Bitmap.Config.ARGB_8888);
    	Canvas bitmapCanvas = new Canvas(bitmap);
//    	bitmapCanvas.clipRect(contentRect);
        Content.draw(bitmapCanvas);
    	screenshotView = new View(this);
    	BitmapDrawable background = new BitmapDrawable(getResources(), bitmap);
    	screenshotView.setBackgroundDrawable(background);
    	screenshotView.setClickable(true);
    	
    	screenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	Content.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        root.setBackgroundColor(0xff000000);
        Content.setAlpha(0.4f);
    	//Restore the background behind it
    	findViewById(R.id.AddItemButton).setEnabled(false);
    	BigDecimal total = getTotal();
    	BigDecimal budget;
    	if (!budgetExceeded) {
            budget = total;
        }
    	else {
            budget = getBudget();
        }
    	if (budget.compareTo(UnlimitedBudget) == 0 || budget.compareTo(new BigDecimal(Long.MAX_VALUE)) > -1 || getBudget().compareTo(UnlimitedBudget) == 0) {
            budget = UnlimitedBudget;
        }
        // TODO
    	//ArrayList<ActionItem> itemsToCheckout = itemsFragment.deleteCrossedOffItems();
        headerFragment.onPendingPartialCheckout();
    	PartialCheckoutItems itemsToCheckout = itemCollectionFragment.deleteCrossedOffItems();
    	if (getBudget().compareTo(UnlimitedBudget) != 0) {
	        setBudget(getBudget().subtract(total));
	        if (getBudget().compareTo(new BigDecimal(0)) == -1)
	        	setBudget(new BigDecimal(0));
    	}
    	
    	//The total MUST be 0, because all crossed off items are now gone
    	//setTotal(new BigDecimal(0));
    	//checkout(Calendar.getInstance().getTimeInMillis()/1000l, itemsToCheckout, itemsToCheckout.size(), total, budget);
    	headerFragment.setPartialCheckoutDone(CheckoutInformation.make(itemsToCheckout, total, budget, Calendar.getInstance().getTimeInMillis()/1000l, itemsToCheckout.items.size(), tax, name));

        if (isSidebar()) {
            ((ViewGroup) Content.getParent()).addView(screenshotView, new ViewGroup.LayoutParams(Content.getWidth(), Content.getHeight()));
            screenshotView.setX(Content.getX());
        }
        else {
            ((ViewGroup) Content.getParent()).addView(screenshotView);
        }
        
        //Animate the screenshot
        screenshotView.animate()
        	.yBy(- Content.getHeight() / 2)
        	.alpha(0)
        	.setDuration(300)
        	.setInterpolator(new AccelerateInterpolator(1))
        	.setListener(new AnimatorListenerAdapter(){
        		public void onAnimationCancel(Animator a){
              		if (screenshotView != null) screenshotView.setLayerType(View.LAYER_TYPE_NONE, null);
                    ((ViewGroup) Content.getParent()).removeView(screenshotView);
            		screenshotView = null; //it has been cleaned by animationCleanup(View);
              	  }
              	  @Override
              	  public void onAnimationEnd(Animator a){
                      if (screenshotView != null) screenshotView.setLayerType(View.LAYER_TYPE_NONE, null);
                      ((ViewGroup) Content.getParent()).removeView(screenshotView);
                      screenshotView = null; //it has been cleaned by animationCleanup(View);
              	  }
          	});
        
        //Fade the content layer
        //This is MUCH MUCH smoother than setting a black rectangle above it and fading it
        //As there's extra rendering involved when it fades
        Content.animate()
	    	.alpha(1f)
	    	.setStartDelay(50)
	    	.setDuration(250)
	    	.setInterpolator(new AccelerateInterpolator(1))
	    	.setListener(new AnimatorListenerAdapter(){
	        		public void onAnimationCancel(Animator a){
	              		if (screenshotView != null) screenshotView.setLayerType(View.LAYER_TYPE_NONE, null);
                        ((ViewGroup) Content.getParent()).removeView(screenshotView);
	            		screenshotView = null; //it has been cleaned by animationCleanup(View);
	              	}
					@Override
	              	public void onAnimationEnd(Animator a){
		            	//Allow the hint to animate once again
		            	makeHintInstant = false;
		        		Content.setLayerType(View.LAYER_TYPE_NONE, null);
		        		Content.setAlpha(1);
		        		root.setBackgroundDrawable(null);
		              	findViewById(R.id.AddItemButton).setEnabled(true);
	        	  }
	    		});
	}
	

    
    @SuppressWarnings("deprecation")
	public void onUndoPressed(CheckoutInformation pendingCheckout) {
    	//Set up the animation layer
        final boolean SidebarMode = backendFragment.isSidebar();
    	final View Content = SidebarMode ? findViewById(R.id.ActivityContainer) : (View) findViewById(R.id.innerList).getParent();
    	
    	final ViewGroup ContentRoot = SidebarMode ? (ViewGroup) findViewById(R.id.ActivityContainer) : (ViewGroup) Content.getParent();

    	Rect contentRect = new Rect();
    	Content.getGlobalVisibleRect(contentRect);
    	
    	//Make hint changes instant to make sure they don't bog down the animation
    	makeHintInstant = true;
    	Rect rct = new Rect();
    	root.getWindowVisibleDisplayFrame(rct);
    	Bitmap bitmap = Bitmap.createBitmap(ContentRoot.getWidth(), ContentRoot.getHeight(), Bitmap.Config.ARGB_8888);
    	Canvas bitmapCanvas = new Canvas(bitmap);
        ContentRoot.draw(bitmapCanvas);
    	final View screenshotView = new View(this);
    	BitmapDrawable background = new BitmapDrawable(getResources(), bitmap);
    	screenshotView.setBackgroundDrawable(background);
    	screenshotView.setClickable(true);

    	Content.setAlpha(0);
    	
    	screenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	Content.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        if (SidebarMode) {
            root.setBackgroundColor(0xFF000000);
        }
        else {
            ContentRoot.setBackgroundColor(0xff000000);
        }
    	//Restore the background behind it
    	findViewById(R.id.AddItemButton).setEnabled(false);
    	
    	if (getBudget().compareTo(UnlimitedBudget) == 0)
    		setBudget(pendingCheckout.budget);
    	else
    		setBudget(getBudget().add(pendingCheckout.budget));
    	
    	itemCollectionFragment.restoreCrossedOffItems(pendingCheckout.items);
    	
    	//The total MUST be 0, because all crossed off items are now gone
    	//setTotal(new BigDecimal(0));

        if (SidebarMode) {
            root.addView(screenshotView, 0, new ViewGroup.LayoutParams(contentRect.width(), contentRect.height()));
            screenshotView.setX(contentRect.left);
            screenshotView.setY(contentRect.top);
        }
        else {
            ContentRoot.addView(screenshotView, 0);
        }
        
        //Animate the screenshot
    	Content.setY(-rct.bottom / 2);
        Content.animate()
        	.translationY(0)
        	.alpha(1)
        	.setDuration(300)
        	.setInterpolator(new DecelerateInterpolator(1))
        	.setListener(new AnimatorListenerAdapter(){
        		public void onAnimationCancel(Animator a) {
        			Content.setTranslationY(0);
        			Content.setAlpha(1);
                    if (SidebarMode) {
                        root.removeView(screenshotView);
                    }
                    else {
                        ContentRoot.removeView(screenshotView);
                    }
        		}
				@Override
              	public void onAnimationEnd(Animator a){
	            	//Allow the hint to animate once again
	            	makeHintInstant = false;
	        		Content.setLayerType(View.LAYER_TYPE_NONE, null);
                    if (SidebarMode) {
                        root.setBackgroundResource(0);
                    }
                    else {
                        ContentRoot.setBackgroundResource(0);
                    }
	              	findViewById(R.id.AddItemButton).setEnabled(true);
				}
          	});
        
        //Fade the content layer
        //This is MUCH MUCH smoother than setting a black rectangle above it and fading it
        //As there's extra rendering involved when it fades
        screenshotView.animate()
	    	.alpha(0.4f)
	    	.setStartDelay(50)
	    	.setDuration(250)
	    	.setInterpolator(new DecelerateInterpolator(1))
	    	.setListener(new AnimatorListenerAdapter(){
	    		@Override
            	public void onAnimationEnd(Animator a){
                    if (SidebarMode) {
                        root.removeView(screenshotView);
                    }
                    else {
                        ContentRoot.removeView(screenshotView);
                    }
            	}
	    	});
    }
	
	@SuppressWarnings("deprecation")
	public void classicCheckout() {

    	//So far, this may not run while other animations are onscreen
    	//so animation cleanup is not required (for now)
    	//Set up the animation layer
    	final View content = findViewById(android.R.id.content);

    	Rect contentRect = new Rect();
    	content.getGlobalVisibleRect(contentRect);
    	
    	//Make hint changes instant to make sure they don't bog down the animation
    	makeHintInstant = true;
    	Rect rct = new Rect();
    	root.getWindowVisibleDisplayFrame(rct);
    	Bitmap bitmap = Bitmap.createBitmap(root.getWidth(), root.getHeight(), Bitmap.Config.ARGB_8888);
    	Canvas bitmapCanvas = new Canvas(bitmap);
    	bitmapCanvas.clipRect(contentRect);
        root.draw(bitmapCanvas);
    	screenshotView = new View(this);
    	BitmapDrawable background = new BitmapDrawable(getResources(), bitmap);
    	screenshotView.setBackgroundDrawable(background);
    	
    	screenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    	
    	root.addView(screenshotView);
    	//screenshotView.setY(contentRect.top);

        root.setBackgroundColor(0xff000000);
        content.setAlpha(0.4f);
    	checkout(Calendar.getInstance().getTimeInMillis()/1000);
    	backendFragment.prepareNewReceipt();
    	findViewById(R.id.AddItemButton).setEnabled(false);
        
        //Animate the screenshot
        screenshotView.animate()
        	.yBy(-rct.bottom)
        	.setDuration(500)
        	.setInterpolator(new AccelerateInterpolator(1.33f))
        	.setListener(new AnimatorListenerAdapter(){
              	  @Override
              	  public void onAnimationEnd(Animator a){
              		  	if (screenshotView != null) screenshotView.setLayerType(View.LAYER_TYPE_NONE, null);
                		root.removeView(screenshotView);
            			screenshotView = null; //it has been cleaned by animationCleanup(View);
              	  }
          	});
        
        //Fade the content layer
        //This is MUCH MUCH smoother than setting a black rectangle above it and fading it
        //As there's extra rendering involved when it fades
        content.animate()
	    	.alpha(1f)
	    	.setStartDelay(150)
	    	.setDuration(250)
	    	.setInterpolator(new AccelerateInterpolator(1f))
	    	.setListener(new AnimatorListenerAdapter(){
	        		public void onAnimationCancel(Animator a){
	              		if (screenshotView != null) screenshotView.setLayerType(View.LAYER_TYPE_NONE, null);
	                	root.removeView(screenshotView);
	            		screenshotView = null; //it has been cleaned by animationCleanup(View);
	              	}
					@Override
	              	public void onAnimationEnd(Animator a){
		            	//Allow the hint to animate once again
		            	makeHintInstant = false;
		        		content.setLayerType(View.LAYER_TYPE_NONE, null);
		        		content.setAlpha(1);
		        		root.setBackgroundDrawable(null);
		              	findViewById(R.id.AddItemButton).setEnabled(true);
		              	if (DEBUG) Log.d("ReceiptActivity", "Secondary animation cleanup performed!");
	        	  }
	    		});
	}
	
	// **************************************************
	// As of this point, all operations involve databases in some way
	// **************************************************
	
	public void checkout(final long unixDate, final ArrayList<ItemCollectionFragment.Item> dataToCheckout,
			final int itemsToSave, final BigDecimal totalToSave, final BigDecimal budgetToSave, final int tax, final String Name) {
		final int itemsCrossedToSave = itemsToSave;
		final int totalItemsToSave = itemsToSave;
        backendFragment.checkOutBudget(totalToSave);
		new AsyncTask<Void, Void, Void>() {
    		protected Void doInBackground(Void ... input) {
    			synchronized (Receipt.DatabaseLock) {
			        SQLiteDatabase dataBase;
					Receipt.DatabaseHelper helper = Receipt.DBHelper;
					dataBase = helper.getWritableDatabase();
					ContentValues values = new ContentValues();
					
					values.put(Receipt.DBDateKey, unixDate);
					values.put(Receipt.DBItemCrossedCountKey, itemsCrossedToSave);
					values.put(Receipt.DBItemCountKey, totalItemsToSave);
                    values.put(Receipt.DBReceiptNameKey, Name);
	
					BigDecimal totalTried = totalToSave.movePointRight(2).setScale(0, BigDecimal.ROUND_HALF_EVEN);
					if (totalTried.compareTo(new BigDecimal(Long.MAX_VALUE)) == 1) {
						values.put(Receipt.DBBigPriceKey, totalToSave.toString());
					}
					values.put(Receipt.DBPriceKey, totalTried.longValue());
					// The budget may NOT be higher than Long.MAX_VALUE
					// Otherwise it is automatically set to (UnlimitedBudget)
					if (budgetToSave.compareTo(UnlimitedBudget) == 0)
						values.put(Receipt.DBBudgetKey, Long.MAX_VALUE);
					else
						values.put(Receipt.DBBudgetKey, budgetToSave.movePointRight(2).setScale(0, BigDecimal.ROUND_HALF_EVEN).longValue());
					values.put(Receipt.DBTaxKey, tax);
					
					long targetDatabase = dataBase.insert(Receipt.DBReceiptsTable, null,
								values);
					int index = 0;
					ContentValues itemValues = new ContentValues(5);
					for (ItemCollectionFragment.Item item : dataToCheckout) {
						itemValues.put(Receipt.DBNameKey, item.name);
						if (item.price == 0)
							itemValues.put(Receipt.DBPriceKey, item.estimatedPrice);
						else
							itemValues.put(Receipt.DBPriceKey, item.price);
						itemValues.put(Receipt.DBQtyKey, item.qty);
						itemValues.put(Receipt.DBTargetDBKey, targetDatabase);
						itemValues.put(Receipt.DBIndexInReceiptKey, index);
						itemValues.put(Receipt.DBUnitOfMeasurementKey, item.unitOfMeasurement);

                        long itemUID = dataBase.insert(Receipt.DBItemsTable, null, itemValues);
                        for (Tag tag : item.tags) {
                            ContentValues tagConnectionValues = new ContentValues(2);
                            tagConnectionValues.put(Receipt.DBItemConnectionUIDKey, itemUID);
                            tagConnectionValues.put(Receipt.DBTagConnectionUIDKey, tag.tagUID);

                            dataBase.insert(Receipt.DBTagConnectionsTable, null, tagConnectionValues);
                        }

						index++;
					}
					
					dataBase.close();
    			}
				if (DEBUG) Log.d("ReceiptActivity/checkout(long)-thread", "State saved sucessfuly!");
				return null;
			}

            protected void onPostExecute(Void result) {
                if (backendFragment != null) {
                    backendFragment.onHistoryTotalsChanged();
                }
            }

		}.execute();
	}
	
	public void checkout(final long unixDate) {
		//final ArrayList<ItemsFragment.ActionItem> dataToCheckout = itemsFragment.items();
		final ArrayList<ItemCollectionFragment.Item> dataToCheckout = new ArrayList<ItemCollectionFragment.Item>(itemCollectionFragment.getItems());
		final int itemsCrossedToSave = this.itemsCrossed;
		final int totalItemsToSave = this.totalItems;
		final BigDecimal totalToSave = getTotal();
		final BigDecimal budgetToSave = this.budget;
		final int tax = this.tax;
        final String Name = this.name;
        backendFragment.checkOutBudget(totalToSave);
		new AsyncTask<Void, Void, Void>() {
    		protected Void doInBackground(Void ... input) {
    			synchronized(Receipt.DatabaseLock) {
			        SQLiteDatabase dataBase;
					Receipt.DatabaseHelper helper = new Receipt.DatabaseHelper(ReceiptActivity.this);
					dataBase = helper.getWritableDatabase();
					ContentValues values = new ContentValues();
					values.put(Receipt.DBDateKey, unixDate);
					values.put(Receipt.DBItemCrossedCountKey, itemsCrossedToSave);
					values.put(Receipt.DBItemCountKey, totalItemsToSave);
                    values.put(Receipt.DBReceiptNameKey, Name);
	
					BigDecimal totalTried = totalToSave.movePointRight(2).setScale(0, BigDecimal.ROUND_HALF_EVEN);
					if (totalTried.compareTo(new BigDecimal(Long.MAX_VALUE)) == 1) {
						values.put(Receipt.DBBigPriceKey, totalToSave.toString());
					}
					values.put(Receipt.DBPriceKey, totalTried.longValue());
					// The budget may NOT be higher than Long.MAX_VALUE
					// Otherwise it is automatically set to (UnlimitedBudget)
					if (budgetToSave.compareTo(UnlimitedBudget) == 0)
						values.put(Receipt.DBBudgetKey, Long.MAX_VALUE);
					else
						values.put(Receipt.DBBudgetKey, budgetToSave.movePointRight(2).setScale(0, BigDecimal.ROUND_HALF_EVEN).longValue());
					values.put(Receipt.DBTaxKey, tax);
					
					long targetDatabase = dataBase.insert(Receipt.DBReceiptsTable, null,
								values);
					int index = 0;
					ContentValues itemValues = new ContentValues(5);
					for (ItemCollectionFragment.Item item : dataToCheckout) {
						itemValues.put(Receipt.DBNameKey, item.name);
						if (item.price == 0)
							itemValues.put(Receipt.DBPriceKey, item.estimatedPrice);
						else
							itemValues.put(Receipt.DBPriceKey, item.price);
						itemValues.put(Receipt.DBQtyKey, item.qty);
						itemValues.put(Receipt.DBTargetDBKey, targetDatabase);
						itemValues.put(Receipt.DBIndexInReceiptKey, index);
						itemValues.put(Receipt.DBUnitOfMeasurementKey, item.unitOfMeasurement);

						long itemUID = dataBase.insert(Receipt.DBItemsTable, null, itemValues);
                        for (Tag tag : item.tags) {
                            ContentValues tagConnectionValues = new ContentValues(2);
                            tagConnectionValues.put(Receipt.DBItemConnectionUIDKey, itemUID);
                            tagConnectionValues.put(Receipt.DBTagConnectionUIDKey, tag.tagUID);

                            dataBase.insert(Receipt.DBTagConnectionsTable, null, tagConnectionValues);
                        }

						index++;
					}
					
					dataBase.close();
    			}
				if (DEBUG) Log.d("ReceiptActivity/checkout(long)-thread", "State saved sucessfuly!");
				return null;
			}

            protected void onPostExecute(Void result) {
                if (backendFragment != null) {
                    backendFragment.onHistoryTotalsChanged();
                }
            }
		}.execute();
	}


	
    
}
