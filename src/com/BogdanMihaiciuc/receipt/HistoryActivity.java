package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.ActionBar.Tab;
import android.app.ActionBar.TabListener;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.NavUtils;
import android.support.v4.view.PagerAdapter;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.Toast;

import com.BogdanMihaiciuc.receipt.HelpStory.OnCloseListener;
import com.BogdanMihaiciuc.receipt.HelpStory.OnSelectPageListener;
import com.BogdanMihaiciuc.receipt.SearchFragment.QueryData;
import com.BogdanMihaiciuc.receipt.StatsFragment.Precision;
import com.BogdanMihaiciuc.util.CollectionPopover;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.DisableableView;
import com.BogdanMihaiciuc.util.IntentListPopover;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Locale;

import static com.BogdanMihaiciuc.receipt.BackendStorage.AbstractReceipt;
import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Item;
import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;

public class HistoryActivity extends Activity
        implements TabListener,
        LegacyActionBar.OnLegacyActionSelectedListener, LegacyActionBar.ContextModeChangedListener, LegacyActionBar.OnLegacyNavigationElementSelectedListener,
        Utils.BackStack, TagExpander.OnTagDeletedListener, Utils.RippleAnimationStack {
	
	final static boolean DEBUG = false;
    final static String TAG = HistoryActivity.class.getName();
	
	final static int FragmentCount = 3;
	final static String HistoryGridFragmentKey = "HistoryGridFragment";

    final static String ActiveTabKey = "HistoryActivity.activeTab";
    @Deprecated
    private class HistoryAdapter extends FragmentPagerAdapter {
        public HistoryAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public int getCount() {
            return FragmentCount;
        }

        @Override
        public Fragment getItem(int position) {
            if (position == 0) {
            	return historyGridFragment;
            }
            if (position == 1) {
                return statsFragment;
            }
            return breakdownFragment;
        }
    }

    static class StatAdapter extends PagerAdapter {

        boolean phoneUI;
        boolean landscape;

        StatAdapter(boolean phoneUI, boolean landscape) {
            this.phoneUI = phoneUI;
            this.landscape = landscape;
        }

        @Override
        public int getCount() {
            return phoneUI ? 3 : 2;
        }

        public Object instantiateItem(ViewGroup container, int index) {
            return container.getChildAt(index);
        }

        public void destroyItem(ViewGroup container, int index, Object object) {
            container.removeView((View) object);
        }

        public float getPageWidth(int position) {
            if (phoneUI) {
                if (position == 0) {
                    return landscape ? 0.33f : 0.5f;
                }
                else {
                    return 1f;
                }
            }
            else {
                return 1f;
            }
        }

        @Override
        public boolean isViewFromObject(View view, Object o) {
            return o == view;
        }
    }

	final static String TargetIdKey = "targetId";
	final static String IndicatorFragmentKey = "indicatorFragment";
	final static String SearchFragmentKey = "searchFragment";
    final static String HistorySearchFragmentKey = "historySearchFragment";
    final static String HistoryViewerFragmentKey = "historyViewerFragment";
	
	final static OnTouchListener NullOnTouchListener = new OnTouchListener() {
		public boolean onTouch(View arg0, MotionEvent arg1) {return true;}		
	};
	
	final static String CurrentHelpPageKey = "currentHelpPage";
	final static int NoHelpPage = -1;
	
	private boolean phoneUI;
    private boolean landscape;
	
	private HistoryGridFragment historyGridFragment;
	private StatsFragment statsFragment;
    private BreakdownFragment breakdownFragment;
	private IndicatorFragment indicatorFragment;
    private HistorySearchFragment historySearchFragment;
    private HistoryViewerFragment historyViewerFragment;
    private LegacyActionBar legacyActionBar;
	private DisableableViewPager pager;
    @Deprecated
//    private SearchFragment searchFragment;
	//private View dimmerView = null;
	//private View screenshotView = null;
	private View viewerView;
	private ViewGroup content;
	private ViewGroup root;
	private boolean prepareActionMode = false;
	private DisplayMetrics metrics;
	
	private int historyListScrollMode;
	private Drawable historyListOverscrollDrawable;
	
	private HelpStory story;
	private int currentHelpPage = NoHelpPage;
	
	final static String ViewerModeKey = "viewerMode";

	@SuppressWarnings("deprecation")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		// Load the indicator fragment before setting the content view
		// As fragments declared within the layout may need to use it
        if (savedInstanceState != null) {
    		indicatorFragment = (IndicatorFragment) getFragmentManager().findFragmentByTag(IndicatorFragmentKey);
//    		searchFragment = (SearchFragment) getFragmentManager().findFragmentByTag(SearchFragmentKey);
            historySearchFragment = (HistorySearchFragment) getFragmentManager().findFragmentByTag(HistorySearchFragmentKey);
            historyViewerFragment = (HistoryViewerFragment) getFragmentManager().findFragmentByTag(HistoryViewerFragmentKey);
        }
        else {
        	indicatorFragment = new IndicatorFragment();
//        	searchFragment = new SearchFragment();
            historySearchFragment = new HistorySearchFragment();
            historyViewerFragment = new HistoryViewerFragment();
        	getFragmentManager().beginTransaction()
                    .add(indicatorFragment, IndicatorFragmentKey)
//                    .add(searchFragment, SearchFragmentKey)
                    .add(historySearchFragment, HistorySearchFragmentKey)
                    .add(historyViewerFragment, HistoryViewerFragmentKey).commit();
        }
        
		setContentView(R.layout.activity_history_legacy_bar);
        legacyActionBar = (LegacyActionBar) getFragmentManager().findFragmentById(R.id.LegacyActionBar);
        legacyActionBar.setOnLegacyNavigationElementSelectedListener(this);
        legacyActionBar.setRippleHighlightColors(LegacyRippleDrawable.DefaultLightPressedColor, LegacyRippleDrawable.DefaultLightRippleColor);
		
		metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);
		
        getWindow().getDecorView().setBackgroundDrawable(null);
        phoneUI = (getResources().getConfiguration().smallestScreenWidthDp < 600);
        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        
        root = (ViewGroup)getWindow().getDecorView();
        content = (ViewGroup)root.getChildAt(0);
		
		// Show the Up button in the action bar.
//		getActionBar().setDisplayHomeAsUpEnabled(true);
        //ActionBar initialize
//        ActionBar actionBar = getActionBar();
//        actionBar.setDisplayUseLogoEnabled(true);
//        actionBar.setDisplayShowTitleEnabled(false);

        // Restore last viewer, if it existed
        if (savedInstanceState != null) {
        	currentHelpPage = savedInstanceState.getInt(CurrentHelpPageKey, NoHelpPage);
        	if (currentHelpPage != NoHelpPage) {
        		final ViewTreeObserver observer = root.getViewTreeObserver();
        		observer.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
					@SuppressLint("NewApi")
					@Override
					public void onGlobalLayout() {
		        		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
		        			root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
		        		else
		        			root.getViewTreeObserver().removeGlobalOnLayoutListener(this);
		        		showHelp(currentHelpPage, true);
					}
				});
        	}
		}
        
        //Tabs and pagers are only available on phones
        if (getResources().getConfiguration().smallestScreenWidthDp < 600) {
            historyGridFragment = (HistoryGridFragment)getFragmentManager().findFragmentById(R.id.HistoryGridFragment);
            statsFragment = (StatsFragment)getFragmentManager().findFragmentById(R.id.StatsFragment);
//	        if (savedInstanceState == null) {
//				historyGridFragment = new HistoryGridFragment();
//				statsFragment = new StatsFragment();
//                breakdownFragment = new BreakdownFragment();
//			}
			
			pager = (DisableableViewPager)findViewById(R.id.HistoryPager);
			pager.setAdapter(new StatAdapter(phoneUI, landscape));
			pager.setOnPageChangeListener(new android.support.v4.view.ViewPager.OnPageChangeListener() {
				@Override
				public void onPageScrollStateChanged(int arg0) {
				}
	
				@Override
				public void onPageScrolled(int arg0, float arg1, int arg2) {
				}
				
				@Override
				public void onPageSelected(int page) {
                    if (page > 0) legacyActionBar.setSelectedNavigationIndex(page - 1);
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putInt(ActiveTabKey, page - 1).apply();
//					getActionBar().setSelectedNavigationItem(page);
				}
			});
            pager.setOffscreenPageLimit(2);
			
			// If restoring from an action mode, disable paging
			if (prepareActionMode)
				pager.setPagingEnabled(false);
			
//			if (savedInstanceState != null) {
//				historyGridFragment = (HistoryGridFragment)getFragmentManager().findFragmentByTag("android:switcher:"+R.id.HistoryPager+":0");
//				statsFragment = (StatsFragment)getFragmentManager().findFragmentByTag("android:switcher:"+R.id.HistoryPager+":1");
//                breakdownFragment = (BreakdownFragment) getFragmentManager().findFragmentByTag("android:switcher:"+R.id.HistoryPager+":2");
//			}
//			actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
			// If restoring from an action mode, postpone creating the tabs
//			if (!prepareActionMode) {
//		        actionBar.addTab(actionBar.newTab()
//		                    .setText(getResources().getString(R.string.HistoryTabLabel))
//		                    .setTabListener(this));
//		        actionBar.addTab(actionBar.newTab()
//		                .setText(getResources().getString(R.string.StatsTabLabel))
//		                .setTabListener(this));
//			}


            if (!legacyActionBar.isRetainedInstance()) {
//                legacyActionBar.setLogoResource(R.drawable.logo);
//                legacyActionBar.setCaretResource(R.drawable.null_drawable);
                legacyActionBar.setBackMode(LegacyActionBarView.DoneBackMode);
                legacyActionBar.setDoneResource(R.drawable.back_light_centered);
                legacyActionBar.setBackgroundColor(getResources().getColor(R.color.ActionBar));

                legacyActionBar.setSeparatorVisible(true);
                legacyActionBar.setSeparatorThickness(2);
                legacyActionBar.setSeparatorOpacity(0.33f);

                legacyActionBar.addNavigationElement(0, getString(R.string.HistoryTabLabel), R.drawable.ic_history);
                legacyActionBar.addNavigationElement(0, getString(R.string.StatsTabLabel), R.drawable.ic_stats);
//                legacyActionBar.addNavigationElement(0, getString(R.string.BreakdownTabLabel), R.drawable.ic_breakdown);
                legacyActionBar.setNavigationMode(LegacyActionBarView.InlineTabsNavigationMode);

//                legacyActionBar.addItem(R.id.menu_grouper, getString(R.string.GrouperMenu), R.drawable.ic_grouper, false, true);
                legacyActionBar.addItem(555, "Export Entire History", R.drawable.ic_search, false, false);
                legacyActionBar.addItem(666, "Import Entire History", R.drawable.ic_search, false, false);
//                legacyActionBar.addItem(R.id.menu_search, getString(R.string.SearchMenu), R.drawable.ic_search, false, false);
                legacyActionBar.addItem(R.id.menu_clear_history, getString(R.string.menu_clear_history), 0, false, false);
//                legacyActionBar.addItem(R.id.menu_settings, getString(R.string.menu_settings), 0, false, false);
//                legacyActionBar.addItem(R.id.menu_help, getString(R.string.menu_help), 0, false, false);


                int activeTab = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getInt(ActiveTabKey, 0);
                if (activeTab < 0) activeTab = 0;
                if (activeTab > 1) activeTab = 1;
                legacyActionBar.setSelectedNavigationIndex(activeTab);
                pager.setCurrentItem(phoneUI ? activeTab + 1 : activeTab, false);
            }
        }
        else {

            pager = (DisableableViewPager)findViewById(R.id.HistoryPager);
            pager.setAdapter(new StatAdapter(phoneUI, landscape));
            pager.setOnPageChangeListener(new android.support.v4.view.ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrollStateChanged(int arg0) {
                }

                @Override
                public void onPageScrolled(int arg0, float arg1, int arg2) {
                }

                @Override
                public void onPageSelected(int page) {
                    legacyActionBar.setSelectedNavigationIndex(page);
                    PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).edit().putInt(ActiveTabKey, page).apply();
//					getActionBar().setSelectedNavigationItem(page);
                }
            });
            pager.setOffscreenPageLimit(2);

            // If restoring from an action mode, disable paging
            if (prepareActionMode)
                pager.setPagingEnabled(false);

            if (!legacyActionBar.isRetainedInstance()) {
//                legacyActionBar.setLogoResource(R.drawable.back_light);
//                legacyActionBar.setCaretResource(R.drawable.null_drawable);
                legacyActionBar.setBackMode(LegacyActionBarView.DoneBackMode);
                legacyActionBar.setDoneResource(R.drawable.back_light_centered);
                legacyActionBar.setBackgroundColor(getResources().getColor(R.color.ActionBar));

                legacyActionBar.setSeparatorVisible(true);
                legacyActionBar.setSeparatorThickness(2);
                legacyActionBar.setSeparatorOpacity(0.33f);

                legacyActionBar.setBackgroundColor(getResources().getColor(R.color.ActionBar));

                legacyActionBar.addNavigationElement(0, getString(R.string.HistoryTabLabel), R.drawable.ic_history);
                legacyActionBar.addNavigationElement(0, getString(R.string.StatsTabLabel), R.drawable.ic_stats);
                legacyActionBar.setNavigationMode(LegacyActionBarView.InlineTabsNavigationMode);

//                legacyActionBar.addItem(R.id.menu_grouper, getString(R.string.GrouperMenu), R.drawable.ic_grouper, false, true);
//                legacyActionBar.addItem(R.id.menu_search, getString(R.string.SearchMenu), R.drawable.ic_search, false, true);
//                legacyActionBar.addItem(R.id.menu_breakdown, getString(R.string.BreakdownMenu), R.drawable.ic_breakdown, false, true);
                legacyActionBar.addItem(555, "Export Entire History", R.drawable.ic_search, false, false);
                legacyActionBar.addItem(666, "Import Entire History", R.drawable.ic_search, false, false);
                legacyActionBar.addItem(R.id.menu_clear_history, getString(R.string.menu_clear_history), 0, false, false);
//                legacyActionBar.addItem(R.id.menu_settings, getString(R.string.menu_settings), 0, false, false);
//                legacyActionBar.addItem(R.id.menu_help, getString(R.string.menu_help), 0, false, false);

                int activeTab = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getInt(ActiveTabKey, 0);
                legacyActionBar.setSelectedNavigationIndex(activeTab);
                pager.setCurrentItem(activeTab, false);
            }

        	// tablet specific layout
			historyGridFragment = (HistoryGridFragment)getFragmentManager().findFragmentById(R.id.HistoryGridFragment);
			statsFragment = (StatsFragment)getFragmentManager().findFragmentById(R.id.StatsFragment);
        }

        getActionBar().hide();
        
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		int activePrecision = prefs.getInt(SearchFragment.LastPrecisionKey, 0);
		int activeAggregate = prefs.getInt(SearchFragment.LastAggregateKey, 0);
        try {
            loadInitialDataForPrecision(Precision.makePrecision(SearchFragment.PrecisionKeys[activePrecision], SearchFragment.AggregateKeys[activeAggregate]));
        }
        catch (ArrayIndexOutOfBoundsException exception) {
            SharedPreferences.Editor editor = prefs.edit();

            activePrecision = 0;
            activeAggregate = 0;

            editor.putInt(SearchFragment.LastPrecisionKey, 0);
            editor.putInt(SearchFragment.LastAggregateKey, 0);

            loadInitialDataForPrecision(Precision.makePrecision(SearchFragment.PrecisionKeys[activePrecision], SearchFragment.AggregateKeys[activeAggregate]));

            editor.apply();
        }
        
	}

    public ViewGroup getContent() {
        return content;
    }
	
    @Override
    protected void onDestroy() {
    	if (story != null) {
    		story.cleanup();
    		story = null;
    	}
    	super.onDestroy();
    }

    @Override
    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        onOptionsIdSelected(item.getId());
    }

    static boolean getLocale(SharedPreferences globalPrefs) {
    	
    	boolean localeUpdated = false;
    	
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
        

        if (!tempLocale.equals(ReceiptActivity.currentLocaleSignature)) {
        	ReceiptActivity.currentLocaleSignature = tempLocale;
        	localeUpdated = true;
        	String[] localeParts = tempLocale.split(" - ");
        	if (localeParts.length > 1)
        		ReceiptActivity.currentLocale = localeParts[1];
        	else
        		ReceiptActivity.currentLocale = "";
        	
        	if (ReceiptActivity.currentLocale.length() > 1)
        		ReceiptActivity.currentTruncatedLocale = "";
        	else
        		ReceiptActivity.currentTruncatedLocale = ReceiptActivity.currentLocale;
        }
        
    	return localeUpdated;
    }
	
	public void onStart() {
		super.onStart();
		
        SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        
        if (getLocale(globalPrefs)) {
        	ReceiptActivity.resetLocale = true;
//        	BaseAdapter adapter = (BaseAdapter)((ListView)findViewById(R.id.list)).getAdapter();
//        	if (adapter != null) adapter.notifyDataSetChanged();
            historyGridFragment.notifyLocaleChanged();
        	statsFragment.notifyLocaleChanged();
        }
	}
	
	public void setHistoryListScrollMode(int scrollMode) {
		historyListScrollMode = scrollMode;
	}
	
	public void setHistoryListOverscrollDrawable(Drawable overscrollDrawable) {
		historyListOverscrollDrawable = overscrollDrawable;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putInt(CurrentHelpPageKey, currentHelpPage);
		if (getFragmentManager().findFragmentByTag("ViewerFragment") != null) {
			getFragmentManager().putFragment(outState, "ViewerFragment", getFragmentManager().findFragmentByTag("ViewerFragment"));
			outState.putInt(ViewerModeKey, ((ViewerFragment)getFragmentManager().findFragmentByTag("ViewerFragment")).getMode());
		}
	}
	
	
	protected void showHelp(int page, boolean instant) {
		
        Rect rect = new Rect();
        getWindow().getDecorView().getGlobalVisibleRect(rect);
        int grouperX, searchX, itemY;
        int historyTabX=0, historyTabY=0, statTabX=0;
    	// Initialize device-specific dimensions
        itemY = 0;
        int swdp = getResources().getConfiguration().smallestScreenWidthDp;
        TypedValue tv = new TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
        {
            itemY = TypedValue.complexToDimensionPixelSize(tv.data,getResources().getDisplayMetrics());
        }
        itemY = itemY/2 + (int)(25 * metrics.density);
        boolean isLandscape = (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE);
        if (swdp < 600) {
    		searchX = (int)(84 * metrics.density);
    		grouperX = (int)(searchX + 56 * metrics.density);
        	if (isLandscape) {
        		historyTabY = itemY;
        		historyTabX = (int)(145 * metrics.density);
        		statTabX = (int)(historyTabX + 83 * metrics.density);
        		//itemY = (int)(45 * metrics.density);
        	}
        	else {
        		historyTabY = 3 * itemY - (int)(50 * metrics.density);
        		historyTabX = rect.right/4;
        		statTabX = 3 * rect.right/4;
        		//grouperX = (int)(140 * metrics.density);
        		//itemY = (int)(49 * metrics.density);
        	}
        	if (ViewConfiguration.get(this).hasPermanentMenuKey()) {
        		//phone with hardware menu key specific coordinates
        		searchX = searchX - (int)(42 * metrics.density);
        		grouperX = searchX - (int)(42 * metrics.density);
        	}
        }
        else {
    		searchX = (int)(96 * metrics.density);
    		grouperX = (int)(160 * metrics.density);
    		//itemY = (int)(52 * metrics.density);
        }
        searchX = rect.right - searchX;
        grouperX = rect.right - grouperX;
        
        story = new HelpStory(this);
        		
        if (phoneUI) {
        	if (legacyActionBar.getSelectedNavigationIndex() == 1) {
	        	HelpOverlayBuilder page3 = new HelpOverlayBuilder(this, findViewById(R.id.StatsGraphView));
	    		page3.setTitle(getString(R.string.StatTitle))
	    			.setExplanation(getString(R.string.StatDescriptionPhone))
	    			.setMaxExplanationLines(2)
	    			.setScale(0.66f);
	        	HelpOverlayBuilder page4 = new HelpOverlayBuilder(this, findViewById(R.id.StatsGraphView));
	    		page4.setTitle(getString(R.string.StatZoomTitle))
	    			.setExplanation(getString(R.string.StatZoomDescription))
	    			.setMaxExplanationLines(2)
	    			.setScale(0.66f)
	    			.setImage(HelpOverlayBuilder.ImageZoom);
	    		if (isLandscape) {
	    			page4.setMaxExplanationLines(3);
	    		}
	        	HelpOverlayBuilder page5 = new HelpOverlayBuilder(this, historyTabX, historyTabY);
	    		page5.setTitle(getString(R.string.HistoryTabTitle))
	    			.setExplanation(getString(R.string.HistoryTabDescription))
	    			.setScale(0.66f);
	    		story.addPages(page3, page4, page5);
        	}
        	else {
	        	HelpOverlayBuilder page3 = new HelpOverlayBuilder(this, rect.right/2, 0);
	    		page3.setTitle(getString(R.string.HistoryScrapTitle))
	    			.setExplanation(getString(R.string.HistoryScrapDescription))
	    			.setMaxExplanationLines(2)
	    			.setImage(HelpOverlayBuilder.ImageNone)
	    			.setScale(0.66f);
	        	HelpOverlayBuilder page5 = new HelpOverlayBuilder(this, statTabX, historyTabY);
	    		page5.setTitle(getString(R.string.StatsTabTitle))
	    			.setExplanation(getString(R.string.StatsTabDescription))
	    			.setScale(0.66f);
	    		story.addPages(page3, page5);
        	}
        }
        else {
        	HelpOverlayBuilder page3 = new HelpOverlayBuilder(this, findViewById(R.id.StatsGraphView));
    		page3.setTitle(getString(R.string.StatTitle))
    			.setExplanation(getString(R.string.StatDescriptionTablet))
    			.setScale(1f);
        	HelpOverlayBuilder page4 = new HelpOverlayBuilder(this, findViewById(R.id.StatsGraphView));
    		page4.setTitle(getString(R.string.StatZoomTitle))
    			.setExplanation(getString(R.string.StatZoomDescription))
    			.setScale(1f)
    			.setImage(HelpOverlayBuilder.ImageZoom);
        	HelpOverlayBuilder page5 = new HelpOverlayBuilder(this, findViewById(R.id.History));
    		page5.setTitle(getString(R.string.HistoryScrapTitle))
    			.setExplanation(getString(R.string.HistoryScrapDescription))
    			.setMaxExplanationLines(2)
    			.setImage(HelpOverlayBuilder.ImageNone)
    			.setScale(0.66f);
    		if (isLandscape)
    			page5.setHighlightPosition(page5.getX() - (int)(100 * metrics.density), page5.getY());
    		else
    			page5.setHighlightPosition(page5.getX(), page5.getY() - (int)(100 * metrics.density));
    		story.addPages(page3, page4, page5);
        }
        
        story.setOnSelectPageListener(new OnSelectPageListener() {
			@Override
			public void onSelectPage(int page) {
				currentHelpPage = page;
			}
		});
        
        story.setOnCloseListener(new OnCloseListener() {
			@Override
			public void onClose(int page) {
				currentHelpPage = NoHelpPage;
				story = null;
			}
		});
        
		HelpOverlayBuilder page1 = new HelpOverlayBuilder(this, grouperX, itemY);
		page1.setTitle(getString(R.string.GrouperTitle))
			.setExplanation(getString(R.string.GrouperDescription))
			.setScale(0.66f);
		
		HelpOverlayBuilder page2 = new HelpOverlayBuilder(this, searchX, itemY);
		page2.setTitle(getString(R.string.SearchTitle))
			.setExplanation(getString(R.string.SearchDescription))
			.setScale(0.66f);
		
        story.addPages(page1, page2);
        
        if (instant)
        	story.startStoryWithPageInstantly(page);
        else
        	story.startStoryWithPage(page);
        
        currentHelpPage = page;
	}
	
	@Override
	public void onPause() {
		super.onPause();
		// Cancel animations to finish any possible pending events
		content.animate().cancel();
        flushAnimations();
	}
	
	@Override
	public void onStop() {
		super.onStop();
		if (getFragmentManager().findFragmentByTag("ViewerFragment") != null) {
			((ViewerFragment)getFragmentManager().findFragmentByTag("ViewerFragment")).dontRemoveViewOnNextDetach();
		}
	}
	
	@Override
	public void onRestart() {
		super.onRestart();
		if (getFragmentManager().findFragmentByTag("ViewerFragment") != null) {
			((ViewerFragment)getFragmentManager().findFragmentByTag("ViewerFragment")).removeViewOnNextDetach();
		}
	}

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            if (currentHelpPage != -1) return true;

            if (historyViewerFragment.handleMenuPressed()) {
                return true;
            }
            legacyActionBar.showOverflow();
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    public void pushToBackStack(Runnable r) {
        indicatorFragment.pushToBackStack(r);
    }

    public void popBackStackFrom(Runnable r) {indicatorFragment.popBackStackFrom(r);}

    public void rewindBackStackFrom(Runnable r) {indicatorFragment.rewindBackStackFrom(r);}

    public boolean popBackStack() {
        return indicatorFragment.popBackStack();
    }

    public void swipeFromBackStack(Runnable r) {indicatorFragment.swipeFromBackStack(r);}

    public Utils.BackStack persistentBackStack() {return indicatorFragment;}

    public int backStackSize() {
        return indicatorFragment.backStackSize();
    }
	
	@Override
	public void onBackPressed() {
        // Prevents a stack overflow exception when popping an empty backstack, which calls back to onBackPressed
        if (indicatorFragment.canPopBackStack()) {
            if (indicatorFragment.popBackStack()) return;
        }

        if (historyViewerFragment.handleBackPressed()) return;
        if (getCurrentNavigationIndex() == 0
                || (phoneUI && getCurrentNavigationIndex() == 1)) {
            if (historySearchFragment.handleBackPressed()) return;
        }
        if (getCurrentNavigationIndex() == getStatsNavigationIndex()) {
            if (statsFragment.handleBackPressed()) return;
        }

        if (currentHelpPage != NoHelpPage) {
            story.exitStory();
        }
        else {
            if (!legacyActionBar.handleBackPress()) {
                super.onBackPressed();
            }
        }
	}
	
	public void superOnBackPressed() {
		super.onBackPressed();
	}
	
	public void startWorking(IndicatorFragment.Task task) {
		
	}
	
	public void stopWorking(IndicatorFragment.Task task) {
		
	}
	
//	@Override
//	public boolean onCreateOptionsMenu(Menu menu) {
//		// Inflate the menu; this adds items to the action bar if it is present.
//		getMenuInflater().inflate(R.menu.activity_history, menu);
//		return true;
//	}

//    @Override
//    public boolean onPrepareOptionsMenu(Menu menu) {
//        if (phoneUI) {
//            if (getActionBar().getSelectedNavigationIndex() == 0) {
//                menu.findItem(R.id.menu_search).setVisible(true);
//                menu.findItem(R.id.menu_breakdown).setVisible(false);
//            }
//            else {
//                menu.findItem(R.id.menu_search).setVisible(false);
//                menu.findItem(R.id.menu_breakdown).setVisible(true);
//            }
//        }
//
//        return true;
//    }
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (!onOptionsIdSelected(item.getItemId())) return super.onOptionsItemSelected(item);
        return true;
	}

    private static IntentListPopover obtainSharePopover(HistoryActivity context) {
        return new IntentListPopover(new Popover.AnchorProvider() {
            @Override
            public View getAnchor(Popover popover) {
                return ((HistoryActivity) popover.getActivity()).legacyActionBar.getView();
            }
        }, new Intent());
    }

    public void shareEntireHistory() {
        new AsyncTask<Void, Void, File>() {
//            LegacyActionBar.ContextBarWrapper wrapper;
            IntentListPopover popover;

            protected void onPreExecute() {
//                wrapper = legacyActionBar.createActionConfirmationContextMode("Preparing entire history...", "Something", new LegacyActionBar.ContextBarListener() {
//                    public void onContextBarStarted() {}
//                    public void onContextBarDismissed() {}
//                    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
//                        if (item.getId() == R.id.ConfirmCancel) cancel(false);
//                    }
//                });
//                wrapper.start();

                popover = obtainSharePopover(HistoryActivity.this);
                popover.setAutoGravity(CollectionPopover.AutoGravityCenter);
                popover.setShowAsModalEnabled(true);

                popover.getHeader().setTitle(ReceiptActivity.titleFormattedString("Share History"));

                LegacyActionBar.ContextBarWrapper workingWrapper = popover.getHeader().createContextMode(new LegacyActionBar.ContextBarListener() {
                    public void onContextBarStarted() {
                    }

                    public void onContextBarDismissed() {
                    }

                    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
                        if (item.getId() == R.id.ConfirmCancel) cancel(true);
                    }
                });
                workingWrapper.setMode(LegacyActionBar.ConfirmationActionBar);
                workingWrapper.setBackgroundResource(R.drawable.actionbar_confirmation_round);
                workingWrapper.setTitle(ReceiptActivity.titleFormattedString("Preparing History"));
                workingWrapper.setBackMode(LegacyActionBarView.CaretBackMode);
                workingWrapper.setBackButtonEnabled(false);
                workingWrapper.addItem(R.id.ConfirmCancel, "CANCEL", 0, true, true);

                popover.show(HistoryActivity.this);

                popover.setOnCreatedLayoutListener(new CollectionPopover.OnCreatedLayoutListener() {
                    @Override
                    public void onCreatedLayout(Popover popover, View root) {
                        ((CollectionPopover) popover).getCollectionView().setMoveAnimationDuration(400);
                    }
                });

                workingWrapper.start();
            }

            @Override
            protected File doInBackground(Void... voids) {
                synchronized (Receipt.DatabaseLock) {
                    return getTemporaryEntireHistoryFile();
                }
            }

            @Override
            protected void onCancelled() {
//                wrapper.dismiss();
                popover.dismiss();
            }

            protected void onPostExecute(File file) {
//                wrapper.dismiss();

                Log.e(TAG, "Temporary file is " + file.getAbsolutePath() + " with size " + file.length());

                String mimeType = Receipt.EntireHistoryMimeType;
                Intent intent = new Intent();

                intent.setAction(android.content.Intent.ACTION_SEND);
                intent.setType(mimeType);
                intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
//                startActivity(Intent.createChooser(intent, "Share Entire History"));
                popover.getHeader().getCurrentContextMode().dismiss();
                popover.setIntent(intent, HistoryActivity.this);
            }

        }.execute();

    }

    public static AbstractReceipt convertDatabaseEntryToAbstractReceipt(SQLiteDatabase db, Cursor cursor) {
        AbstractReceipt receipt = new AbstractReceipt();
        receipt.header = new BackendStorage.ReceiptFileHeader();
        receipt.filename = String.valueOf(cursor.getLong(Receipt.DBFilenameIdKeyIndex));

        receipt.header.budget = new BigDecimal(cursor.getLong(Receipt.DBBudgetKeyIndex)).movePointLeft(2);
        receipt.header.total = new BigDecimal("0");
        receipt.header.tax = cursor.getInt(Receipt.DBTaxKeyIndex);
        receipt.header.estimatedTotal = new BigDecimal(cursor.getLong(Receipt.DBPriceKeyIndex)).movePointLeft(2);
        receipt.header.totalItems = cursor.getInt(Receipt.DBItemCountKeyIndex);

        receipt.items = new ArrayList<Item>();

        Cursor items = db.query(Receipt.DBItemsTable, Receipt.DBAllItemsColumns, Receipt.DBTargetDBKey + " = " + cursor.getLong(Receipt.DBFilenameIdKeyIndex),
                null, null, null, null);

        while (items.moveToNext()) {
            receipt.items.add(convertDatabaseEntryToItem(db, items));
        }

        items.close();

        return receipt;
    }

    public static Item convertDatabaseEntryToItem(SQLiteDatabase db, Cursor cursor) {
        Item item = new Item();

        item.name = cursor.getString(Receipt.DBNameKeyIndex);
        item.price = 0;
        item.estimatedPrice = cursor.getLong(Receipt.DBPriceKeyIndex);
        item.qty = cursor.getInt(Receipt.DBQtyKeyIndex);
        item.unitOfMeasurement = cursor.getString(Receipt.DBUnitOfMeasurementKeyIndex);

        if (item.qty == 0) {
            item.flags = ItemCollectionFragment.SetTitle;
        }
        else {
            item.flags = ItemCollectionFragment.SetQty;
        }

        item.tags = new ArrayList<Tag>();
        Cursor tags = db.rawQuery(
                "select t." + Receipt.DBNameKey + ", t." + Receipt.DBColorKey + " " +
                "from " + Receipt.DBTagsTable + " t, " + Receipt.DBTagConnectionsTable + " tc " +
                "where t." + Receipt.DBUIDKey + " = tc." + Receipt.DBTagConnectionUIDKey +
                    " and tc." + Receipt.DBItemConnectionUIDKey + " = " + cursor.getLong(Receipt.DBUIDKeyIndex), null
        );

        while (tags.moveToNext()) {
            Tag tag = new Tag();
            tag.name = tags.getString(0);
            tag.color = tags.getInt(1);
            item.tags.add(tag);
        }

        tags.close();


        return item;
    }

    public File getTemporaryEntireHistoryFile() {

        File cacheDir = getExternalCacheDir();

        String filename = "Receipt";


        try {
            File file = File.createTempFile(filename, ".receiptHistory", cacheDir);
            file.setReadable(true, false);
            filename = file.getAbsolutePath();
            ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));

            os.writeInt(0x00012ECE);

            SQLiteDatabase db = Receipt.DBHelper.getReadableDatabase();
            Cursor receipts = db.query(Receipt.DBReceiptsTable, Receipt.DBAllReceiptColumns, null, null, null, null, null);
            int count = receipts.getCount();
            os.writeInt(count);
            for (int i = 0; i < count; i++) {
                receipts.moveToNext();
                AbstractReceipt receipt = convertDatabaseEntryToAbstractReceipt(db, receipts);

                os.writeLong(receipts.getLong(Receipt.DBDateKeyIndex));
                receipt.header.flatten(os);
                for (int j = 0; j < receipt.header.totalItems; j++) {
                    receipt.items.get(j).flatten(os, Item.CurrentVersionUID, true);
                }
            }

            receipts.close();

            os.close();
            return file;
        }
        catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public void importEntireHistory() {

        try {
            ObjectInputStream is = new ObjectInputStream(new FileInputStream("/sdcard/Receipt.receiptHistory"));
            SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();

            is.readInt();
            int count = is.readInt();

            for (int i = 0; i < count; i++) {
                long date = is.readLong();

                AbstractReceipt receipt = new AbstractReceipt();
                receipt.header = BackendStorage.ReceiptFileHeader.inflate(is);
                receipt.items = new ArrayList<Item>();

                ContentValues values = new ContentValues(6);
                values.put(Receipt.DBDateKey, date);
                values.put(Receipt.DBPriceKey, receipt.header.estimatedTotal.movePointRight(2).longValue());
                values.put(Receipt.DBTaxKey, receipt.header.tax);
                values.put(Receipt.DBItemCountKey, receipt.header.totalItems);
                values.put(Receipt.DBItemCrossedCountKey, receipt.header.totalItems);
                values.put(Receipt.DBBudgetKey, receipt.header.budget.movePointRight(2).longValue());
                long receiptUID = db.insert(Receipt.DBReceiptsTable, null, values);

                for (int j = 0; j < receipt.header.totalItems; j++) {
                    Item item = Item.inflateCreatingMissingTagToDatabase(is, Item.CurrentVersionUID, true, db);

                    values = new ContentValues(5);
                    values.put(Receipt.DBNameKey, item.name);
                    values.put(Receipt.DBQtyKey, item.qty);
                    values.put(Receipt.DBUnitOfMeasurementKey, item.unitOfMeasurement);
                    values.put(Receipt.DBPriceKey, item.estimatedPrice);
                    values.put(Receipt.DBTargetDBKey, receiptUID);
                    values.put(Receipt.DBIndexInReceiptKey, j);

                    long itemUID = db.insert(Receipt.DBItemsTable, null, values);

                    for (Tag tag : item.tags) {
                        values = new ContentValues(2);
                        values.put(Receipt.DBItemConnectionUIDKey, itemUID);
                        values.put(Receipt.DBTagConnectionUIDKey, tag.tagUID);
                        db.insert(Receipt.DBTagConnectionsTable, null, values);
                    }

                }


            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (StreamCorruptedException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        loadDataForPrecision(statsFragment.getPrecisionData());

    }

    public boolean onOptionsIdSelected(int id) {

        switch (id) {
            case R.id.menu_breakdown: {
                return true;
            }

            case R.id.menu_clear_history: {

                if (historyViewerFragment.isActive()) return true;
                historyGridFragment.confirmClearHistory();
                // TODO: Handle
//                searchFragment.showConfirmation();
                return true;
            }
            case android.R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.menu_help: {
                if (!historyViewerFragment.isActive()) showHelp(0, false);
                return true;
            }
            case R.id.menu_settings: {
                Intent settingsIntent = new Intent(this, SettingsActivity.class);
                startActivity(settingsIntent);
                return true;
            }
            case R.id.menu_show_stats: {

                statsFragment.reloadData();

            }
            return true;
            case R.id.menu_toggle_stats: {

                Toast.makeText(this, "Unsupported", Toast.LENGTH_SHORT).show();
                //statsFragment.toggleWeeklyStats();

            }
            return true;
            case R.id.menu_update: {
                statsFragment.update();
                return true;
            }
            case R.id.menu_start_working: {
            }
            return true;
            case R.id.menu_stop_working: {
            }
            case 555: {
                shareEntireHistory();
                return true;
            }
            case 666: {
                importEntireHistory();
            }
            return true;
        }
        return true;
    }
	
	public void clearHistory() {
    	historyGridFragment.clearHistory();

		NavUtils.navigateUpFromSameTask(this);
		
		overridePendingTransition(R.anim.discard_enter_animation, R.anim.discard_animation);
	}
	
	public void loadDataForPrecision(Precision precision) {
        statsFragment.loadDataForPrecision(precision);
		historyGridFragment.loadDataForPrecision(precision.sqlPrecision);
	}
	public void loadInitialDataForPrecision(Precision precision) {
        statsFragment.loadInitialDataForPrecision(precision);
		historyGridFragment.loadInitialDataForPrecision(StatsFragment.DateFormatMonthPrecision, phoneUI);
	}
	public void loadDirectQuery(QueryData history, QueryData stats, Precision precision) {
        statsFragment.loadDirectQuery(stats, precision);
		historyGridFragment.loadDirectQuery(history, precision);
	}

    public void search(HistorySearchFragment.Query query) {
        historyGridFragment.search(query);
    }

    public void clearSearch() {
        historyGridFragment.clearSearch();
    }

    public boolean isSearchActive() {
        return historyGridFragment.isSearchActive();
    }

    public void onCurrentSectionChanged(CollectionView.Section section) {
        historyGridFragment.onCurrentSectionChanged(section);
    }
	
//	public String getSearchTerms() {
//		return searchFragment.getSearchTerms();
//	}

    @Override
    public void onContextModeStarted() {
        onActionModeStarted(null);
    }

    @Override
    public void onContextModeChanged() {

    }

    @Override
    public void onContextModeFinished() {
        onActionModeFinished(null);
    }
	
	@Override
	public void onActionModeStarted(ActionMode actionMode) {
        if (pager == null) {
            prepareActionMode = true;
            return;
        }
        pager.setPagingEnabled(false);
//        searchFragment.startPeeking();
	}
	
	@Override
	public void onActionModeFinished(ActionMode actionMode) {
        pager.setPagingEnabled(true);
//        searchFragment.stopPeeking();
	}

    public void disablePaging() {
        if (pager != null) pager.setPagingEnabled(false);
    }

    public void enablePaging() {
        if (pager != null) pager.setPagingEnabled(true);
    }

	@Override
	public void onTabReselected(Tab arg0, android.app.FragmentTransaction arg1) { }

	@Override
	public void onTabSelected(Tab arg0, android.app.FragmentTransaction arg1) {
//		pager.setCurrentItem(getActionBar().getSelectedNavigationIndex(), true);
//        invalidateOptionsMenu();
	}

    @Override
    public void onLegacyNavigationElementSelected(int index, LegacyActionBar.LegacyNavigationElement element) {
        if (phoneUI) index += 1;
        pager.setCurrentItem(index, true);
//        if (phoneUI) {
//            if (index == 1) {
//                LegacyActionBar.ActionItem item = legacyActionBar.findItemWithId(R.id.menu_search);
//                item.setName(getString(R.string.BreakdownMenu));
//                item.setResource(R.drawable.ic_breakdown);
//                legacyActionBar.replaceItemWithId(R.id.menu_grouper, R.id.menu_breakdown, getString(R.string.BreakdownMenu), R.drawable.ic_breakdown, false, true);
//                legacyActionBar.removeItemWithId(R.id.menu_grouper);
//                legacyActionBar.addItemToIndex(R.id.menu_breakdown, getString(R.string.BreakdownMenu), R.drawable.ic_breakdown, false, true, 1);
//            }
//            else {
//                LegacyActionBar.ActionItem item = legacyActionBar.findItemWithId(R.id.menu_search);
//                item.setName(getString(R.string.SearchMenu));
//                item.setResource(R.drawable.ic_search);
//                legacyActionBar.replaceItemWithId(R.id.menu_breakdown, R.id.menu_grouper, getString(R.string.GrouperMenu), R.drawable.ic_grouper, false, true);
//                legacyActionBar.removeItemWithId(R.id.menu_breakdown);
//                legacyActionBar.addItemToIndex(R.id.menu_grouper, getString(R.string.GrouperMenu), R.drawable.ic_grouper, false, true, 1);
//            }
//        }
    }

    public int getCurrentNavigationIndex() {
        return pager.getCurrentItem();
    }

    public int getStatsNavigationIndex() {
        return phoneUI ? 2 : 1;
    }

	@Override
	public void onTabUnselected(Tab arg0, android.app.FragmentTransaction arg1) { }
	
//	public void confirmSelectionDelete() {
//		searchFragment.showSelectionConfirmation();
//	}
	
	public void deleteSelection() {
		historyGridFragment.deleteSelection();
	}
	
	public void deleteSelection(ArrayList<HistoryGridAdapter.Scrap> selection) {
		historyGridFragment.deleteSelection(selection);
	}
	
	final static int AnimationFlipHalfLength = 200;
	final static int AnimationFlipLength = 400;
	final static int AnimationFadeDelay =100;
	final static int AnimationFadeLength = 400;
	final static int AnimationZoomHalfLength = 200;
	final static int AnimationZoomLength = 400;

    private boolean useCollectionView;
	
	public void restoreList(final ListView list) {
		list.setOverscrollHeader(historyListOverscrollDrawable);
		list.setOverScrollMode(historyListScrollMode);
		list.setVerticalScrollBarEnabled(true);
		list.setFastScrollEnabled(true);
		list.setOnTouchListener(null);
		if (pager != null)
			pager.setPagingEnabled(true);
	}

    public void collapseNavigationPanel() {
        if (pager.getCurrentItem() == 0) {
            pager.setCurrentItem(1, true);
        }
    }

    private ArrayList<Animator> animations = new ArrayList<Animator>();

    protected boolean globalAnimationActive;

    protected void onGlobalAnimationStarted() {
        flushAnimations();

        indicatorFragment.cancelAnimations();

        pager.setPagingEnabled(false);
        pager.setOverScrollMode(View.OVER_SCROLL_NEVER);
        pager.setHorizontalScrollBarEnabled(false);
        pager.freeze();

        statsFragment.freezeScrollers();
//        pager.setScrollbarFadingEnabled(false);

        final CollectionView history = (CollectionView) findViewById(R.id.History);
        history.smoothScrollTo(0, history.getScrollY());
        history.freeze();

        CollectionView sectionCollection = (CollectionView) findViewById(R.id.SectionCollection);
        sectionCollection.smoothScrollTo(0, sectionCollection.getScrollY());
        sectionCollection.freeze();

        historySearchFragment.clearSearchbarFocus();

        globalAnimationActive = true;
    }

    private boolean tagsInvalidated;

    protected void onGlobalAnimationEnded() {
        pager.setPagingEnabled(true);
        pager.setOverScrollMode(View.OVER_SCROLL_IF_CONTENT_SCROLLS);
        pager.thaw();

        statsFragment.thawScrollers();

        final CollectionView history = (CollectionView) findViewById(R.id.History);
        history.thaw();

        CollectionView sectionCollection = (CollectionView) findViewById(R.id.SectionCollection);
        sectionCollection.thaw();

        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(content.getWindowToken(), 0);

        globalAnimationActive = false;

        if (tagsInvalidated) {
            tagsInvalidated = false;
            statsFragment.update();
        }
    }

    public void flushAnimations() {
        for (Animator animator : animations) {
            animator.cancel();
        }

        flushRipples();
    }

    public boolean dispatchTouchEvent(MotionEvent event) {
        if (globalAnimationActive) return false;

        // Popover handles outside touches on its own
        if (getFragmentManager().findFragmentByTag(Popover.PopoverKey) != null) {
            return super.dispatchTouchEvent(event);
        }

        if (historyViewerFragment.isActive()) {
            View viewer = historyViewerFragment.getViewerView();
            if (Utils.isWithinInterval(event.getRawX(), viewer.getX(), viewer.getX() + viewer.getWidth())
                    && Utils.isWithinInterval(event.getRawY(), viewer.getY(), viewer.getY() + viewer.getHeight())) {
                return super.dispatchTouchEvent(event);
            }
            else {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    historyViewerFragment.collapse();
                }
                return false;
            }
        }


        return super.dispatchTouchEvent(event);
    }

    public void showScrapFromView(long targetId, final View Scrap) {

        historyViewerFragment.setTarget(targetId);
        final View ScrapWindow = historyViewerFragment.getViewerView();

        Scrap.setPressed(false);
        final View ScrapScreenshot = getLayoutInflater().inflate(R.layout.history_scrap, root, false);  //Utils.ViewUtils.screenshotView(Scrap, Scrap.getWidth(), Scrap.getHeight());
        historyGridFragment.cloneScrapView(ScrapScreenshot, Scrap, HistoryGridAdapter.ItemTypeScrap);
//        final LegacyRippleDrawable Background = new LegacyRippleDrawable(this);
//        Background.setShape(LegacyRippleDrawable.ShapeRoundRect);
//        ScrapScreenshot.findViewById(R.id.ScrapRipple).setBackground(Background);

        // The simulated touch event might otherwise cause the selection mode to trigger
        ScrapScreenshot.setOnClickListener(null);
        ScrapScreenshot.setOnLongClickListener(null);

        ScrapScreenshot.findViewById(R.id.ScrapRipple).setBackground(((LegacyRippleDrawable) Scrap.findViewById(R.id.ScrapRipple).getBackground()).createDelegateDrawable());
        ((DisableableView) Scrap.findViewById(R.id.ScrapRipple)).suspendDrawing();
        ((LegacyRippleDrawable) Scrap.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingFlushRequest();

        final Rect ScrapRect = new Rect();
        Scrap.getGlobalVisibleRect(ScrapRect);

        Utils.ViewUtils.centerViewOnPoint(ScrapScreenshot, ScrapRect.centerX(), ScrapRect.centerY());

        final Utils.ClippedLayout AnimationRoot = new Utils.ClippedLayout(this);

        root.addView(AnimationRoot, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        AnimationRoot.addView(ScrapScreenshot, new FrameLayout.LayoutParams(Scrap.getWidth(), Scrap.getHeight()));

        final CollectionView Collection = (CollectionView) ScrapWindow.findViewById(R.id.ScrapCollection);
        Collection.freeze();

        onGlobalAnimationStarted();

        ScrapWindow.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                ScrapWindow.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                int scrapSize = getResources().getDimensionPixelSize(R.dimen.HistoryScrapSize);

                ScrapWindow.setVisibility(View.VISIBLE);

                final Rect WindowRect = new Rect();
                ScrapWindow.getGlobalVisibleRect(WindowRect);

//                long downTime = SystemClock.uptimeMillis();
//                long eventTime = SystemClock.uptimeMillis() + 50;
//                float x = ScrapRect.left + ((LegacyRippleDrawable) Scrap.findViewById(R.id.ScrapRipple).getBackground()).lastXCoordinate();
//                float y = ScrapRect.top + ((LegacyRippleDrawable) Scrap.findViewById(R.id.ScrapRipple).getBackground()).lastYCoordinate();
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
//                ScrapScreenshot.dispatchTouchEvent(motionEvent);
//                Background.setRippleSource(x - ScrapRect.left, y - ScrapRect.top);

                // In here, the only interesting scenario happens if the scrap is cut off at the top

                Rect searchBarRect = new Rect();
                View searchBoxLayout = findViewById(R.id.SearchBoxLayout);
                searchBoxLayout.getGlobalVisibleRect(searchBarRect);
                if (searchBarRect.height() > 0) {
                    searchBarRect.top = searchBarRect.bottom - searchBoxLayout.getHeight();
                    Utils.insetRect(searchBarRect, searchBoxLayout.getPaddingLeft(), searchBoxLayout.getPaddingTop(), searchBoxLayout.getPaddingRight(), searchBoxLayout.getPaddingBottom());
                }

                final boolean BezierEnabled = (ScrapRect.height() != Scrap.getHeight() && ScrapRect.top < root.getHeight() / 2) || Rect.intersects(searchBarRect, ScrapRect);
                if (BezierEnabled) {
                    Rect contentRect = new Rect();
                    findViewById(R.id.ContentContainer).getGlobalVisibleRect(contentRect);
                    // Removes the actionbar area
                    AnimationRoot.addDrawArea(contentRect);
//                    if (searchBarRect.height() > 0) {
//                        AnimationRoot.addPunchedArea(searchBarRect);
//                        AnimationRoot.buildClips();
//                    }

                    ScrapRect.top = ScrapRect.bottom - scrapSize;
                }

                final float ScrapPadding = getResources().getDimensionPixelSize(R.dimen.BackendScrapImagePadding);
                final float WindowPadding = phoneUI ? 0 : getResources().getDimensionPixelSize(R.dimen.HistoryListWindowPadding);

                final float WindowWidthRatio = (scrapSize - 2 * ScrapPadding) / (WindowRect.width() - 2 * WindowPadding);
                final float WindowHeightRatio = (scrapSize - 2 * ScrapPadding) / (WindowRect.height() - 2 * WindowPadding);

                final float ScrapWidthRatio = 1f / WindowWidthRatio;
                final float ScrapHeightRatio = 1f / WindowHeightRatio;

                Utils.ViewUtils.centerViewOnPoint(ScrapWindow, ScrapRect.centerX(), ScrapRect.centerY());

                final Point StartPoint = new Point((int) (ScrapRect.centerX() + (scrapSize - ScrapRect.width()) / 2f),  (int) (ScrapRect.centerY() + (scrapSize - ScrapRect.height()) / 2f));
                final Point EndPoint = new Point(WindowRect.centerX(), WindowRect.centerY());

                final Point BezierStartPoint = new Point(StartPoint.x, StartPoint.y + root.getHeight() / 3);
                final Point BezierEndPoint = new Point(EndPoint.x, EndPoint.y);

                root.setBackgroundColor(0xFF000000);
                content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                ScrapScreenshot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                ScrapWindow.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                content.buildLayer();
                ScrapWindow.buildLayer();
                ScrapWindow.setAlpha(0f);

                ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
                animations.add(animator);
                animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                    boolean removedClips;

                    @Override
                    public void onAnimationUpdate(ValueAnimator valueAnimator) {

                        float fraction = valueAnimator.getAnimatedFraction();

                        float x, y;
                        if (BezierEnabled) {
                            x = Utils.bezierX(fraction, StartPoint, BezierStartPoint, EndPoint, BezierEndPoint);
                            y = Utils.bezierY(fraction, StartPoint, BezierStartPoint, EndPoint, BezierEndPoint);
                        }
                        else {
                            x = Utils.interpolateValues(fraction, StartPoint.x, EndPoint.x);
                            y = Utils.interpolateValues(fraction, StartPoint.y, EndPoint.y);
                        }

                        Utils.ViewUtils.centerViewOnPoint(ScrapWindow, x, y);
                        Utils.ViewUtils.centerViewOnPoint(ScrapScreenshot, x, y);

                        ScrapWindow.setScaleY(Utils.interpolateValues(fraction, WindowHeightRatio, 1f));
                        ScrapWindow.setScaleX(Utils.interpolateValues(fraction, WindowWidthRatio, 1f));
                        ScrapScreenshot.setScaleY(Utils.interpolateValues(fraction, 1f, ScrapHeightRatio));
                        ScrapScreenshot.setScaleX(Utils.interpolateValues(fraction, 1f, ScrapWidthRatio));

//                        ScrapWindow.setAlpha(fraction);
//                        if (!phoneUI && fraction > 0.5f) {
//                            ScrapScreenshot.setAlpha(Utils.interpolateValues(2 * fraction - 1f, 1f, 0f));
//                        }
                        content.setAlpha(Utils.interpolateValues(fraction, 1f, 0.4f));

                        if (fraction > 0.25f) {
                            ScrapScreenshot.setAlpha(1 - Utils.getIntervalPercentage(fraction, 0.25f, 1f));
                        }
                        if (fraction > 0.25f) {
                            float alpha = Utils.getIntervalPercentage(fraction, 0.25f, 0.75f);
                            ScrapWindow.setAlpha(alpha > 1f ? 1f : alpha);
                        }

                        if (fraction > 0.5f && !removedClips) {
                            removedClips = true;
                            AnimationRoot.removeClips();
                            AnimationRoot.invalidate();
                        }

                        if (phoneUI) {
                            content.setScaleY(Utils.interpolateValues(fraction, 1f, 0.95f));
                            content.setScaleX(Utils.interpolateValues(fraction, 1f, 0.95f));
                        }
                    }
                });

                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        animations.remove(animation);

                        root.removeView(AnimationRoot);
                        root.removeView(ScrapScreenshot);

                        ScrapWindow.setLayerType(View.LAYER_TYPE_NONE, null);
                        Utils.ViewUtils.resetViewProperties(ScrapWindow);
//                        Utils.ViewUtils.centerViewOnPoint(ScrapWindow, EndPoint.x, EndPoint.y);
                        Collection.thaw();
                        ((DisableableView) Scrap.findViewById(R.id.ScrapRipple)).resumeDrawing();

                        if (phoneUI) {
                            content.setAlpha(0f);
                            root.setBackgroundColor(0);
                        }

                        historyViewerFragment.onNavigationComplete();

                        onGlobalAnimationEnded();
                    }
                });
                animator.setDuration(AnimationFlipLength);
                animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
                animator.start();
            }
        });
    }

    public void collapseScrap() {

        onGlobalAnimationStarted();
        content.setScaleX(1f);
        content.setScaleY(1f);

        final int hiddenLocation = historyGridFragment.getLocationOfOffscreenHiddenScrap();
        final View Scrap = historyGridFragment.getHiddenScrapIfVisible();
        historyGridFragment.setListenersDisabled(false);
        historyGridFragment.resetHiddenScrap();

        final View ScrapWindow = historyViewerFragment.getViewerView();

        final CollectionView Collection = (CollectionView) ScrapWindow.findViewById(R.id.ScrapCollection);
        Collection.freeze();

        if (Scrap != null) {
            int scrapSize = getResources().getDimensionPixelSize(R.dimen.HistoryScrapSize);

            final View ScrapScreenshot = Utils.ViewUtils.screenshotView(Scrap, Scrap.getWidth(), Scrap.getHeight());
            if (!phoneUI) {
                ScrapScreenshot.setAlpha(0f);
            }
            final Rect ScrapRect = new Rect();
            Scrap.getGlobalVisibleRect(ScrapRect);

            Utils.ViewUtils.centerViewOnPoint(ScrapScreenshot, ScrapRect.centerX(), ScrapRect.centerY());

            final Utils.ClippedLayout AnimationRoot = new Utils.ClippedLayout(this);

            root.addView(AnimationRoot, root.indexOfChild(ScrapWindow), new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            AnimationRoot.addView(ScrapScreenshot, new FrameLayout.LayoutParams(Scrap.getWidth(), Scrap.getHeight()));

            final Rect WindowRect = new Rect();
            ScrapWindow.getGlobalVisibleRect(WindowRect);

            final float ScrapPadding = getResources().getDimensionPixelSize(R.dimen.BackendScrapImagePadding);
            final float WindowPadding = phoneUI ? 0 : getResources().getDimensionPixelSize(R.dimen.HistoryListWindowPadding);

            final float WindowWidthRatio = (scrapSize - 2 * ScrapPadding) / (WindowRect.width() - 2 * WindowPadding);
            final float WindowHeightRatio = (scrapSize - 2 * ScrapPadding) / (WindowRect.height() - 2 * WindowPadding);

            final float ScrapWidthRatio = 1f / WindowWidthRatio;
            final float ScrapHeightRatio = 1f / WindowHeightRatio;

            // In here, the only interesting scenario happens if the scrap is cut off at the top
            final Rect SearchBarRect = new Rect();
            final Rect ContentRect = new Rect();
            View searchBoxLayout = findViewById(R.id.SearchBoxLayout);
            searchBoxLayout.getGlobalVisibleRect(SearchBarRect);
            if (SearchBarRect.height() > 0) {
                SearchBarRect.top = SearchBarRect.bottom - searchBoxLayout.getHeight();
                Utils.insetRect(SearchBarRect, searchBoxLayout.getPaddingLeft(), searchBoxLayout.getPaddingTop(), searchBoxLayout.getPaddingRight(), searchBoxLayout.getPaddingBottom());
            }

            final boolean BezierEnabled = (ScrapRect.height() != Scrap.getHeight() && ScrapRect.top < root.getHeight() / 2) || Rect.intersects(SearchBarRect, ScrapRect);
            if (BezierEnabled) {
                findViewById(R.id.ContentContainer).getGlobalVisibleRect(ContentRect);
                ScrapRect.top = ScrapRect.bottom - scrapSize;
            }

            Utils.ViewUtils.centerViewOnPoint(ScrapWindow, ScrapRect.centerX(), ScrapRect.centerY());

            final Point EndPoint = new Point((int) (ScrapRect.centerX() + (scrapSize - ScrapRect.width()) / 2f),  (int) (ScrapRect.centerY() + (scrapSize - ScrapRect.height()) / 2f));
            final Point StartPoint = new Point(WindowRect.centerX(), WindowRect.centerY());

            final Point BezierStartPoint = new Point(StartPoint.x, StartPoint.y);
            final Point BezierEndPoint = new Point(EndPoint.x, EndPoint.y + root.getHeight() / 3);

            ((CollectionView) ScrapWindow.findViewById(R.id.ScrapCollection)).freeze();

            root.setBackgroundColor(0xFF000000);
            content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            content.buildLayer();
            ScrapScreenshot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ScrapScreenshot.buildLayer();
            ScrapWindow.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ScrapWindow.buildLayer();

            if (phoneUI) {
                content.setScaleX(0.95f);
                content.setScaleY(0.95f);
            }

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animations.add(animator);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                boolean removedClips;

                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    float x, y;
                    if (BezierEnabled) {
                        x = Utils.bezierX(fraction, StartPoint, BezierStartPoint, EndPoint, BezierEndPoint);
                        y = Utils.bezierY(fraction, StartPoint, BezierStartPoint, EndPoint, BezierEndPoint);
                    }
                    else {
                        x = Utils.interpolateValues(fraction, StartPoint.x, EndPoint.x);
                        y = Utils.interpolateValues(fraction, StartPoint.y, EndPoint.y);
                    }

                    Utils.ViewUtils.centerViewOnPoint(ScrapWindow, x, y);
                    Utils.ViewUtils.centerViewOnPoint(ScrapScreenshot, x, y);

                    ScrapWindow.setScaleY(Utils.interpolateValues(fraction, 1f, WindowHeightRatio));
                    ScrapWindow.setScaleX(Utils.interpolateValues(fraction, 1f, WindowWidthRatio));
                    ScrapScreenshot.setScaleY(Utils.interpolateValues(fraction, ScrapHeightRatio, 1f));
                    ScrapScreenshot.setScaleX(Utils.interpolateValues(fraction, ScrapWidthRatio, 1f));

                    if (fraction > .5f) {

                        if (!removedClips && BezierEnabled) {
                            removedClips = true;
                            AnimationRoot.addDrawArea(ContentRect);
//                            if (SearchBarRect.height() > 0) {
//                                AnimationRoot.addPunchedArea(SearchBarRect);
//                                AnimationRoot.buildClips();
//                            }
                            AnimationRoot.invalidate();
                        }

                    }

                    // Fast-track the alpha animation
                    float alphaFraction = Utils.constrain(Utils.getIntervalPercentage(fraction, 0, 0.75f), 0, 1);
                    if (alphaFraction > 0.5f) {
                        ScrapWindow.setAlpha(2 - 2 * alphaFraction);
                    }
                    if (alphaFraction > 0.25f && !phoneUI) {
                        float alpha = Utils.getIntervalPercentage(alphaFraction, 0.25f, 0.75f);
                        ScrapScreenshot.setAlpha(alpha > 1f ? 1f : alpha);
                    }

                    content.setAlpha(Utils.interpolateValues(fraction, 0.4f, 1f));
                    if (phoneUI) {
                        content.setScaleY(Utils.interpolateValues(fraction, 0.95f, 1f));
                        content.setScaleX(Utils.interpolateValues(fraction, 0.95f, 1f));
                    }
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);
                    root.removeView(AnimationRoot);
                    root.removeView(ScrapScreenshot);
                    root.setBackgroundColor(0);

                    ScrapWindow.setLayerType(View.LAYER_TYPE_NONE, null);
                    ScrapWindow.setVisibility(View.INVISIBLE);
                    content.setLayerType(View.LAYER_TYPE_NONE, null);
                    Collection.thaw();

                    ((CollectionView) ScrapWindow.findViewById(R.id.ScrapCollection)).thaw();

                    Utils.ViewUtils.resetViewProperties(ScrapWindow);

                    Scrap.setVisibility(View.VISIBLE);

                    onGlobalAnimationEnded();

                    historyViewerFragment.onGlobalAnimationEnded();
                }
            });
            animator.setDuration(AnimationFlipLength);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            animator.start();
        }
        else {
            root.setBackgroundColor(0xFF000000);
            content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            content.buildLayer();
            ScrapWindow.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            ScrapWindow.buildLayer();

            if (phoneUI) {
                content.setScaleX(0.95f);
                content.setScaleY(0.95f);
            }

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animations.add(animator);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    ScrapWindow.setScaleY(Utils.interpolateValues(fraction, 1f, 0.8f));
                    ScrapWindow.setScaleX(Utils.interpolateValues(fraction, 1f, 0.8f));
                    ScrapWindow.setAlpha(1 - fraction);

                    content.setAlpha(Utils.interpolateValues(fraction, 0.4f, 1f));
                    if (phoneUI) {
                        content.setScaleY(Utils.interpolateValues(fraction, 0.95f, 1f));
                        content.setScaleX(Utils.interpolateValues(fraction, 0.95f, 1f));
                    }
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);
                    root.setBackgroundColor(0);

                    ScrapWindow.setLayerType(View.LAYER_TYPE_NONE, null);
                    ScrapWindow.setVisibility(View.INVISIBLE);
                    content.setLayerType(View.LAYER_TYPE_NONE, null);
                    Collection.thaw();

                    Utils.ViewUtils.resetViewProperties(ScrapWindow);

                    onGlobalAnimationEnded();

                    historyViewerFragment.onGlobalAnimationEnded();
                }
            });
            animator.setDuration(AnimationFlipLength);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            animator.start();
        }
    }

    public void deleteScrap() {

        onGlobalAnimationStarted();

        final long UID = historyViewerFragment.getCurrentTarget();

        final View ScrapWindow = historyViewerFragment.getViewerView();

        final CollectionView Collection = (CollectionView) ScrapWindow.findViewById(R.id.ScrapCollection);
        Collection.freeze();
        root.setBackgroundColor(0xFF000000);
        content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        content.buildLayer();
        ScrapWindow.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        ScrapWindow.buildLayer();

        if (phoneUI) {
            content.setScaleX(0.95f);
            content.setScaleY(0.95f);
        }

        ScrapWindow.setPivotX(0f);
        ScrapWindow.setPivotY(ScrapWindow.getHeight());

        historyGridFragment.holdTransaction();

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animations.add(animator);

        final TimeInterpolator Accelerator = new AccelerateInterpolator(1f);
        final TimeInterpolator FasterAccelerator = new AccelerateInterpolator(1.5f);

        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                float fastFraction = FasterAccelerator.getInterpolation(fraction);
                float accelerationFraction = Accelerator.getInterpolation(fraction);

                ScrapWindow.setRotation(Utils.interpolateValues(accelerationFraction, 0f, 20f));
                if (phoneUI) ScrapWindow.setTranslationX(Utils.interpolateValues(accelerationFraction, 0f, ScrapWindow.getWidth() / 2f));
                else ScrapWindow.setTranslationY(Utils.interpolateValues(accelerationFraction, 0f, (48 * metrics.density)));
                ScrapWindow.setAlpha(1 - accelerationFraction);

                content.setAlpha(Utils.interpolateValues(fastFraction, 0.4f, 1f));
                if (phoneUI) {
                    content.setScaleY(Utils.interpolateValues(fastFraction, 0.95f, 1f));
                    content.setScaleX(Utils.interpolateValues(fastFraction, 0.95f, 1f));
                }
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animations.remove(animation);
                root.setBackgroundColor(0);

                ScrapWindow.setLayerType(View.LAYER_TYPE_NONE, null);
                ScrapWindow.setVisibility(View.INVISIBLE);
                content.setLayerType(View.LAYER_TYPE_NONE, null);
                Collection.thaw();

                Utils.ViewUtils.resetViewProperties(ScrapWindow);
                ScrapWindow.setPivotX(ScrapWindow.getWidth() / 2f);
                ScrapWindow.setPivotY(ScrapWindow.getHeight() / 2f);

                onGlobalAnimationEnded();

                historyViewerFragment.onGlobalAnimationEnded();

                historyGridFragment.releaseTransaction();
            }
        });
        animator.setDuration(AnimationFlipLength);
        animator.setInterpolator(new LinearInterpolator());
        animator.start();


        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                synchronized (Receipt.DatabaseLock) {
                    SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();

                    db.delete(Receipt.DBReceiptsTable, Receipt.DBFilenameIdKey + "=" + UID, null);
                    Cursor items = db.query(Receipt.DBItemsTable, new String[] {Receipt.DBItemUIDKey}, Receipt.DBTargetDBKey + " = " + UID,
                            null, null, null, null);
                    while (items.moveToNext()) {
                        db.delete(Receipt.DBTagConnectionsTable, Receipt.DBItemConnectionUIDKey + " = " + items.getLong(0), null);
                    }
                    items.close();
                    db.delete(Receipt.DBItemsTable, Receipt.DBTargetDBKey + "=" + UID, null);
                }
                return null;
            }
        }.execute();
        update();
    }

    public void onTagsInvalidated() {
        //tagsInvalidated = true;
        statsFragment.update();
    }
	
	private boolean animationFinished = false;
	private boolean cleanUpView = false;
	
	public void update() {
		statsFragment.update();
		historyGridFragment.update();
	}
	
	@SuppressWarnings("deprecation")
	public void hideContents() {
		//content.setVisibility(View.INVISIBLE);
		root.setBackgroundDrawable(null);
	}
	
	public void showContents() {
		//content.setVisibility(View.INVISIBLE);
		root.setBackgroundColor(0xff000000);
	}
	
	public void loadHistory(String precision, long dateWithinPrecision) {
		historyGridFragment.loadDataForPrecisionAndDate(precision, dateWithinPrecision);
	}

    public HistoryViewerFragment.SearchResolver obtainSearchResolver() {
        return historyGridFragment.obtainSearchResolver();
    }

    @Override
    public void onTagDeleted(Tag tag) {
        statsFragment.onTagDeleted(tag);
    }


    public IndicatorFragment getIndicator() {
		return indicatorFragment;
	}

    private ArrayList<Animator> ripples = new ArrayList<Animator>();

    public void flushRipples() {
        while (ripples.size() > 0) {
            ripples.get(0).setInterpolator(LegacyRippleDrawable.FlagAnimationCancelled);
            ripples.get(0).end();
        }
    }

    public void addRipple(Animator a) {
        ripples.add(a);
    }

    public void removeRipple(Animator a) {
        ripples.remove(a);
    }

}
