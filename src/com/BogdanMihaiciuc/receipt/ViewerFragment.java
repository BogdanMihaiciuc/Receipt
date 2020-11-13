package com.BogdanMihaiciuc.receipt;

import android.R.interpolator;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.widget.AutoCompleteTextView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.Toast;

import com.BogdanMihaiciuc.receipt.HelpStory.OnCloseListener;
import com.BogdanMihaiciuc.receipt.HelpStory.OnSelectPageListener;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.TagView;
import com.BogdanMihaiciuc.util.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;

import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Item;
import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;
import static com.BogdanMihaiciuc.receipt.ScrapListAdapter.DatabaseProxyItem;

@Deprecated
public class ViewerFragment extends Fragment implements OnMenuItemClickListener, LegacyActionBar.OnLegacyActionSelectedListener, TagExpander.OnTagDeletedListener {
	
	final static boolean DEBUG = false;
	final static boolean DEBUG_DETACH = false;
	final static boolean DEBUG_ROTATION = false;
	final static boolean DEBUG_HELP = false;
	
	final static String CurrentHelpPageKey = "ViewerFragment.currentHelpPage";
	final static String ConfirmationBannerUpKey = "ViewerFragment.confirmationBannerUp";
	final static String SelectionListKey = "ViewerFragment.selectionList";
	final static String IdListKey = "ViewerFragment.idList";
    final static String TagExpanderTargetKey = "ViewerFragment.tagExpander";
    final static String SelectionCountKey = "ViewerFragment.selectionCount";
    final static String ScrapActionBarKey = "ViewerFragment.scrapActionBar";

    final static String ActionModeKey = "ViewerFragment.actionMode";
    final static String ConfirmationKey = "ViewerFragment.confirmation";
    final static String TagWrapperKey = "ViewerFragment.tagWrapper";
	
	final static int NoHelpPage = -1;

    static interface ViewerFragmentListener {
		public void detachView();
	}

    static ViewerFragment currentViewer;

    static LegacyActionBar.ContextBarListener staticActionModeListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {

        }

        @Override
        public void onContextBarDismissed() {
            currentViewer.actionMode = null;

            currentViewer.closeActionMode();
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            if (item.getId() == R.id.action_edit_tags) {
                currentViewer.editTagsForSelection();
            }
        }
    };

    static LegacyActionBar.ContextBarListener staticConfirmationListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {

        }

        @Override
        public void onContextBarDismissed() {
            currentViewer.confirmation = null;
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            if (item.getId() != android.R.id.home) currentViewer.confirmation.dismiss();
            if (item.getId() == R.id.ConfirmOK) currentViewer.deleteCurrentScrap();
        }
    };

	private HistoryActivity activity;
	private View generatedView;
	private ScrapListAdapter adapter;
	private ScrapListAdapter detailAdapter;
	private long currentTarget;
	private long scheduledTarget = -1;
	private long priceForCurrentTarget;
	private boolean dontRemoveViewOnDetach;
	private Calendar dateForCurrentTarget;

    private LegacyActionBar scrapActionBar; // TODO
	
	private ListView scrapList;
	private ListView detailList;
	private View scrapWindow;
    private LegacyActionBar.ContextBarWrapper actionMode;
    private LegacyActionBar.ContextBarWrapper confirmation;
	
	private int currentHelpPage = NoHelpPage;
	private HelpStory helpStory;
	
	private boolean confirmationBannerUp;
	
	private int mode = ScrapListAdapter.ModeScrapContent;
	
	private boolean phoneUI;
	
	private boolean[] selectionList;
	private long[] idList;
	private int selectionCount;
    private long tagExpanderTarget = Long.MIN_VALUE;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		phoneUI = getResources().getConfiguration().smallestScreenWidthDp < 600;

        currentViewer = this;
		
		if (DEBUG_ROTATION) {
			Log.d("ViewerFragment", "onCreate called! mode is : " + mode);
			for (StackTraceElement ste : new Throwable().getStackTrace())
				Log.d("ViewerFragment", ste.toString());
		}
		
		if (savedInstanceState != null) {
			if (DEBUG_HELP) Log.d("ViewerFragment", "Restored state!");
			currentHelpPage = savedInstanceState.getInt(CurrentHelpPageKey, NoHelpPage);
			confirmationBannerUp = savedInstanceState.getBoolean(ConfirmationBannerUpKey, false);
			selectionList = savedInstanceState.getBooleanArray(SelectionListKey);
			idList = savedInstanceState.getLongArray(IdListKey);
			selectionCount = savedInstanceState.getInt(SelectionCountKey);
            tagExpanderTarget = savedInstanceState.getLong(TagExpanderTargetKey);
		}
	}
    
    @Override
    public void onDestroy() {
    	if (helpStory != null) {
    		helpStory.cleanup();
    		helpStory = null;
    	}
    	super.onDestroy();
    }
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
	         Bundle savedInstanceState) {
	    return null;
	}

    public void onDestroyView() {
        super.onDestroyView();
        adapter.onDestroyView();
    }
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		activity = (HistoryActivity)getActivity();
	}
	
	@Override
	public void onAttach(Activity attachedActivity) {
		super.onAttach(attachedActivity);
		activity = (HistoryActivity)attachedActivity;

        scrapActionBar = (LegacyActionBar) getActivity().getFragmentManager().findFragmentByTag(ScrapActionBarKey);
        if (scrapActionBar == null) {
            scrapActionBar = LegacyActionBar.getAttachableLegacyActionBar();
            scrapActionBar.setCaretResource(R.drawable.caret_up);
            scrapActionBar.setLogoResource(R.drawable.logo_dark);
            scrapActionBar.setOverflowResource(R.drawable.ic_action_overflow);
            scrapActionBar.setSeparatorVisible(true);
            scrapActionBar.setSeparatorOpacity(0.25f);

            scrapActionBar.addItem(R.id.action_share, getString(R.string.MenuShare), R.drawable.ic_action_share_mini_dark, false, true);
            scrapActionBar.addItem(R.id.action_show_details, getString(R.string.ShowDetails), R.drawable.ic_action_done_dark, false, true);
            scrapActionBar.addItem(R.id.action_delete, getString(R.string.ItemDelete), R.drawable.ic_action_delete_dark, false, false);
            scrapActionBar.addItem(R.id.action_copy, "[DEV] " + getString(R.string.ItemCopy), R.drawable.ic_action_copy_dark, false, true); //TODO
//            scrapActionBar.addItem(1991, "Show the dropdown NOW!", 0, false, false);
            scrapActionBar.addItem(R.id.menu_settings, getString(R.string.menu_settings), 0, false, false);
            scrapActionBar.addItem(R.id.menu_help, getString(R.string.menu_help), 0, false, false);

            getActivity().getFragmentManager().beginTransaction().add(scrapActionBar, ScrapActionBarKey).commit();
        }
        scrapActionBar.setOnLegacyActionSeletectedListener(this);
        actionMode = scrapActionBar.findContextModeWithTag(ActionModeKey);
        confirmation = scrapActionBar.findContextModeWithTag(ConfirmationKey);
        tagWrapper = scrapActionBar.findContextModeWithTag(TagWrapperKey);

		if (generatedView != null) {
//			scrapList = (ListView)activity.findViewById(R.id.ScrapWindowList);
//			detailList = (ListView)activity.findViewById(R.id.ScrapInfoList);

            ViewGroup actionBar = (ViewGroup) generatedView.findViewById(R.id.ScrapWindowActionBar);
            scrapActionBar.setContainer(actionBar);
		}
		if (scheduledTarget != - 1 && generatedView != null) {
			setTargetScrap(scheduledTarget);
		}
	}
	
	@Override
	public void onDetach() {

        if (adapter != null) {
            adapter.detach();
        }

        // destroy the problematic TagExpander view
        if (generatedView != null) {
            View scroller = generatedView.findViewById(TagExpander.ScrollerID);
            while (scroller != null) {
                ((ViewGroup) scroller.getParent()).removeView(scroller);
                scroller = generatedView.findViewById(TagExpander.ScrollerID);
            }
        }
		
		if (!dontRemoveViewOnDetach) {
            activity.getFragmentManager().beginTransaction().remove(scrapActionBar).commit();
            if (generatedView != null) {
                ViewGroup actionBar = (ViewGroup) generatedView.findViewById(R.id.ScrapWindowActionBar);
                Utils.ViewUtils.cancelAllAnimationsInViewGroup(actionBar);
                Utils.ViewUtils.cancelAllAnimationsInViewGroup(scrapList);
            }
//			activity.detachViewAndCloseCursor(adapter.getCursor(), mode); // TODO
			if (DEBUG_DETACH) Log.d("ViewerFragment", "Asking activity to detach view!");
		}
		else
			if (DEBUG_DETACH) Log.d("ViewerFragment", "View detach skipped!");
		
		final ViewGroup root = ((ViewGroup)activity.getWindow().getDecorView());
		final View content = root.getChildAt(0);
		if (content != null)
			content.setVisibility(View.VISIBLE);
		if (pendingBlitzView != null)
			root.removeView(pendingBlitzView);
		if (pendingScreenshot != null)
			pendingScreenshot = null;

        currentViewer = null;
		
		dontRemoveViewOnDetach = false;
		super.onDetach();
		scheduledTarget = -1;
		
		activity = null;
		generatedView = null;
		scrapList = null;
		detailList = null;
		scrapWindow = null;
		
		helpStory = null;
	}
	
	public void dontRemoveViewOnNextDetach() {
		dontRemoveViewOnDetach = true;
	}
	
	public void removeViewOnNextDetach() {
		dontRemoveViewOnDetach = false;
	}
	
	public void setMode(int mode) {
		this.mode = mode;
		
		if (mode == ScrapListAdapter.ModeScrapContent) {
			scrapList.setVisibility(View.VISIBLE);
			detailList.setVisibility(View.INVISIBLE);
		}
		else {
			detailList.setVisibility(View.VISIBLE);
			scrapList.setVisibility(View.INVISIBLE);
		}
	}
	
	public void toggleMode() {
		
		if (mode != ScrapListAdapter.ModeScrapContent) {
			if (scrapList != null) scrapList.setVisibility(View.VISIBLE);
			if (detailList != null) detailList.setVisibility(View.INVISIBLE);
			mode = ScrapListAdapter.ModeScrapContent;
		}
		else {
			if (detailList != null) detailList.setVisibility(View.VISIBLE);
			if (scrapList != null) scrapList.setVisibility(View.INVISIBLE);
			mode = ScrapListAdapter.ModeScrapInfo;
		}
		
	}
	
	public int getMode() {
		return mode;
	}
	 
	public View generateViewWithInflater(Activity target) {
		// If possible, return the previously created view
		View newView;
		if (generatedView != null)
			newView = generatedView;
		else
			newView = target.getLayoutInflater().inflate(R.layout.history_list, (ViewGroup)target.getWindow().getDecorView(), false);
		final View returnedView = newView;
		
//		scrapList = (ListView)returnedView.findViewById(R.id.ScrapWindowList);
//		detailList = (ListView)returnedView.findViewById(R.id.ScrapInfoList);
		scrapWindow = returnedView.findViewById(R.id.ScrapWindow);
		
		if (mode == ScrapListAdapter.ModeScrapContent) {
			scrapList.setVisibility(View.VISIBLE);
			detailList.setVisibility(View.INVISIBLE);
		}
		else {
			detailList.setVisibility(View.VISIBLE);
			scrapList.setVisibility(View.INVISIBLE);
		}
		
//		returnedView.findViewById(R.id.DeleteButton).setOnClickListener(deleteClickListener);
		generatedView = returnedView;
		TextView totalSum = ((TextView)returnedView.findViewById(R.id.total_sum));
        totalSum.setTypeface(Receipt.condensedLightTypeface());
        ((TextView) returnedView.findViewById(R.id.text_total)).setTypeface(Receipt.condensedTypeface());
		
		if (phoneUI && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
			totalSum.setText(ReceiptActivity.totalFormattedStringWithSpecifiedCutoff(target, 
					new BigDecimal(priceForCurrentTarget).movePointLeft(2), 
					ReceiptActivity.LandscapeInitialCutoff));
		else
			totalSum.setText(ReceiptActivity.totalFormattedStringWithSpecifiedCutoff(target, 
					new BigDecimal(priceForCurrentTarget).movePointLeft(2), 
					ReceiptActivity.PortraitInitialCutoff));
		
		final DisplayMetrics metrics = new DisplayMetrics();
		
		returnedView.setOnTouchListener(new View.OnTouchListener() {
	        @Override
	        public boolean onTouch(View v, MotionEvent event) {
				// This happens if the fragment has been detached, but the view has not been cleaned up completely
				if (activity == null) return false;
				/*if (event.getAction() == MotionEvent.ACTION_UP) {
					boolean result = gestureDetector.onTouchEvent(event);
					if (result == false) {
						onSwipeRight(1, 1);
					}
					return result;
				}*/
	            if (event.getAction() == MotionEvent.ACTION_DOWN){
	                
	            	if (event.getX() < returnedView.findViewById(R.id.ScrapWindow).getLeft() - 40 ||
	            			event.getX() > returnedView.findViewById(R.id.ScrapWindow).getRight() + 40 ||
	            			event.getY() < returnedView.findViewById(R.id.ScrapWindow).getTop() - 40 ||
	            			event.getY() > returnedView.findViewById(R.id.ScrapWindow).getBottom() + 40) {
	            		// If the touch is out of the window's margin of error, dismiss it!
	    				activity.getFragmentManager().popBackStackImmediate();
	    				returnedView.setOnTouchListener(null);
	    	            return true;
	            	}
	            	
	            	// onTouch happens only when the activity is alive and the fragment attached, so safe to use it here
	        		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
	            	returnedView.findViewById(R.id.ScrapWindow).setCameraDistance(metrics.widthPixels * 5);
	            	
	            }
	            return false; //gestureDetector.onTouchEvent(event);
	        }
	    });
		
//		if (confirmationBannerUp)
//			showConfirmationBanner(true);

        ViewGroup actionBar = (ViewGroup) returnedView.findViewById(R.id.ScrapWindowActionBar);
//        actionBar.removeAllViews();
        if (scrapActionBar != null) scrapActionBar.setContainer(actionBar);
		
		return returnedView;
	}
	
	public void notifyLocaleChanged() {
		if (generatedView != null) {
			TextView totalSum = ((TextView)generatedView.findViewById(R.id.total_sum));
			
			if (phoneUI && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
				totalSum.setText(ReceiptActivity.totalFormattedStringWithSpecifiedCutoff(getActivity(), 
						new BigDecimal(priceForCurrentTarget).movePointLeft(2), 
						ReceiptActivity.LandscapeInitialCutoff));
			else
				totalSum.setText(ReceiptActivity.totalFormattedStringWithSpecifiedCutoff(getActivity(), 
						new BigDecimal(priceForCurrentTarget).movePointLeft(2), 
						ReceiptActivity.PortraitInitialCutoff));
			
			if (adapter != null) adapter.notifyDataSetChanged();
			if (detailAdapter != null) detailAdapter.notifyDataSetChanged();
		}
	}
	
	public void setTargetScrap(long target) {
		currentTarget = target;
		if (activity != null && generatedView != null) {
			
//			ListView listView = (ListView)generatedView.findViewById(R.id.ScrapWindowList);
			SQLiteDatabase database = Receipt.DBHelper.getReadableDatabase();
			
			Cursor data = database.query(Receipt.DBItemsTable, Receipt.DBAllItemsColumns, Receipt.DBTargetDBKey + "=" + target, null, null, null, 
							Receipt.DBIndexInReceiptKey);
			if (adapter == null) adapter = new ScrapListAdapter(activity, this);
			if (detailAdapter == null) detailAdapter = new ScrapListAdapter(activity, this);
			adapter.setLists(selectionList, idList);
			adapter.setSelectionCount(selectionCount);
			adapter.setCursor(data, database);
//			adapter.setSearchTerms(activity.getSearchTerms());
            adapter.setTagExpanderTarget(tagExpanderTarget);

			long details[] = new long[7];
			
			Cursor priceFinder =  database.query(Receipt.DBReceiptsTable, new String[]{Receipt.DBPriceKey, Receipt.DBDateKey, Receipt.DBBudgetKey, Receipt.DBItemCountKey, Receipt.DBTaxKey}, 
									Receipt.DBFilenameIdKey + "=" + target, null, null, null, null);
			if (priceFinder.getCount() != 0) {
				priceFinder.moveToFirst();
				priceForCurrentTarget = priceFinder.getLong(0);
				dateForCurrentTarget = Calendar.getInstance();
				dateForCurrentTarget.setTimeInMillis(priceFinder.getLong(1) * 1000);
				
				details[ScrapListAdapter.UnixCheckoutTime] = priceFinder.getLong(1) * 1000;
				details[ScrapListAdapter.ItemTypeCount] = priceFinder.getLong(3);
				details[ScrapListAdapter.AssignedBudget] = priceFinder.getLong(2);
				details[ScrapListAdapter.RemainingBudget] = priceFinder.getLong(2) - priceFinder.getLong(0);
				details[ScrapListAdapter.Tax] = priceFinder.getLong(4);
				details[ScrapListAdapter.Subtotal] = new BigDecimal(priceForCurrentTarget).movePointLeft(2)
						.divide(new BigDecimal(10000 + details[ScrapListAdapter.Tax]).movePointLeft(4), RoundingMode.HALF_EVEN).setScale(2, RoundingMode.HALF_EVEN)
						.movePointRight(2).longValue();
			}
			detailAdapter.setCursor(data, database);
			detailAdapter.setInfoDetails(details);
			//adapter.setInfoDetails(details);
			detailAdapter.setModeAndReloadAfter(ScrapListAdapter.ModeScrapInfo, false);
			
			TextView totalSum = ((TextView)generatedView.findViewById(R.id.total_sum));
			
			if (phoneUI && getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE)
				totalSum.setText(ReceiptActivity.totalFormattedStringWithSpecifiedCutoff(activity, 
						new BigDecimal(priceForCurrentTarget).movePointLeft(2), 
						ReceiptActivity.LandscapeInitialCutoff));
			else
				totalSum.setText(ReceiptActivity.totalFormattedStringWithSpecifiedCutoff(activity, 
						new BigDecimal(priceForCurrentTarget).movePointLeft(2), 
						ReceiptActivity.PortraitInitialCutoff));

			priceFinder.close();
//			listView.setAdapter(adapter);
//			detailList = (ListView)generatedView.findViewById(R.id.ScrapInfoList);
			detailList.setAdapter(detailAdapter);
			
//			if (selectionCount > 0) {
//				showActionMode(true);
//			}
			
			if (DEBUG) Log.d("ViewerFragment", "Set target scrap to " + target);
		}
		else {
			if (DEBUG) Log.d("ViewerFragment", "Couldn't set target because activity is null!");
			scheduledTarget = target;
		}
	}
	

	public void showConfirmationBanner() {
		showConfirmationBanner(false);
	}
	
	final static long ConfirmationBannerAnimationDuration = 250;
	final static long ActionModeAnimationDuration = 150;
	final static long ActionModeScaleDelay = 100;
	
	public void notifySelectionCountChanged() {
        final LegacyActionBar.ContextBarWrapper ActionMode = actionMode;
        actionMode = null;
        if (tagWrapper != null) {
            tagWrapper.dismiss();
        }
        actionMode = ActionMode;

		int tempSelectionCount = adapter.getSelectionCount();
		if (selectionCount > 0 && tempSelectionCount == 0)
			closeActionMode();
		if (selectionCount == 0 && tempSelectionCount > 0) {
			showActionMode(false);
            // To disable tapping on the tags
            adapter.notifyDataSetChanged();
        }
		selectionCount = tempSelectionCount;
		
		if (actionMode != null) {
			actionMode.setTitleAnimated(selectionCount + " selected", 1);

            if (selectionCount > 1) {
                if (actionMode.findItemWithId(R.id.action_edit_tags) == null) {
                    actionMode.addItemToIndex(R.id.action_edit_tags, getString(R.string.ItemEditTags), R.drawable.ic_action_edit_tags, true, true, 0);
                }
            }
            else {
                actionMode.removeItemWithId(R.id.action_edit_tags);
            }
        }
		
	}
	
	public void showActionMode(boolean instantly) {

		if (confirmation != null)
			confirmation.dismiss();

		if (activity == null) return;
		if (generatedView != null) {

            if (actionMode == null) {

                actionMode = scrapActionBar.createContextMode(staticActionModeListener);
                actionMode.setTag(ActionModeKey);
                actionMode.addItem(R.id.action_copy, getString(R.string.ItemCopy), R.drawable.ic_action_copy, true, true);
                actionMode.setBackgroundColor(getResources().getColor(R.color.HeaderCanCheckout));
                actionMode.setSeparatorOpacity(0.25f);

                actionMode.start();

                if (selectionCount > 0) actionMode.setTitleAnimated(selectionCount + " selected", 1);

            }
			
		}
	}
	
	public void closeActionMode() {
		closeActionMode(true);
	}
	
	public void closeActionMode(boolean clearSelection) {

		if (selectionCount > 0) {
			adapter.clearSelection(clearSelection);
			selectionCount = 0;
		}

        if (actionMode != null) {
            actionMode.dismiss();
        }
		
	}

    class ProxyItem extends Item {

        ArrayList<Tag> allTags = new ArrayList<Tag>();
        ArrayList<Integer> tagCounts = new ArrayList<Integer>();

        ArrayList<DatabaseProxyItem> targets;

        int freeTagSlots = 4;

        public void addTagToIndex(Tag tag, int index) {
            super.addTagToIndex(tag, index);
            // This tag is no longer uncommon
            allTags.remove(tag);
            freeTagSlots = 4;
            for (DatabaseProxyItem item : targets) {
                item.addTag(tag);
                if (freeTagSlots > 4 - item.tags.size()) {
                    freeTagSlots = 4 - item.tags.size();
                }
            }
            adapter.notifyDataSetChanged();
        }

        public void removeTagAtIndex(int index) {
            Tag tag = tags.get(index);
            super.removeTagAtIndex(index);
            freeTagSlots = 4;
            // A color of -1 indicates uncommon tags
            if (tag.color == -1) {
                for (DatabaseProxyItem item : targets) {
                    item.removeTags(allTags);
                    if (freeTagSlots > 4 - item.tags.size()) {
                        freeTagSlots = 4 - item.tags.size();
                    }
                }
            }
            else {
                for (DatabaseProxyItem item : targets) {
                    item.removeTag(tag);
                    if (freeTagSlots > 4 - item.tags.size()) {
                        freeTagSlots = 4 - item.tags.size();
                    }
                }
            }
            adapter.notifyDataSetChanged();
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

        ArrayList<Tag> allTags = proxyItem.allTags;
        ArrayList<Integer> tagCounts = proxyItem.tagCounts;
        ArrayList<DatabaseProxyItem> selectionList = adapter.getItemSelectionList();

        int index;

        proxyItem.freeTagSlots = 4;
        for (Item item : selectionList) {
            for (Tag tag : item.tags) {
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
            if (count == selectionList.size()) {
                proxyItem.tags.add(allTags.remove(i));
                tagCounts.remove(i);
                i--;
                tagCountsSize--;
            }
        }

        //If there are tags still left in the allTags array it means that there are uncommon tags
        if (allTags.size() != 0) {
            Tag uncommonTag = new Tag();
            uncommonTag.name = context.getResources().getString(R.string.UncommonTags);
            uncommonTag.color = -1;
            proxyItem.tags.add(0, uncommonTag);
        }

        proxyItem.targets = selectionList;

        return proxyItem;
    }

    private LegacyActionBar.ContextBarWrapper tagWrapper;
    private TagExpander contextExpander;
    private static LegacyActionBar.ContextBarListener tagListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {
        }

        @Override
        public void onContextBarDismissed() {
            if (currentViewer.contextExpander != null) {
                currentViewer.contextExpander.compact();
            }

            if (currentViewer.actionMode != null) {
                currentViewer.actionMode.dismissInstantly();
            }
            else {
            }
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {}
    };

    public void editTagsForSelection() {

        tagWrapper = scrapActionBar.createContextMode(tagListener);
        tagWrapper.setCustomView(new LegacyActionBar.CustomViewProvider() {
            boolean initial = true;

            @Override
            public View onCreateCustomView(LayoutInflater inflater, ViewGroup container) {
                TagView proxy = new TagView(container.getContext());
                Item proxyItem = createProxyItem(container.getContext());
                proxy.setTags(proxyItem.tags);
//                container.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                contextExpander = TagExpander.fromViewInContainerWithProxyTarget(proxy, container, proxyItem);
                contextExpander.setOnTagDeletedListener(ViewerFragment.this);
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
                    container.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
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

        tagWrapper.setBackgroundColor(getResources().getColor(R.color.GradientStart));
        tagWrapper.setDoneResource(R.drawable.ic_action_done_dark);
        tagWrapper.setTextColor(0x88000000);
        tagWrapper.setSeparatorVisible(true);
        tagWrapper.setSeparatorOpacity(0.25f);
        tagWrapper.setBackButtonPosition(LegacyActionBarView.BackButtonPositionRight);
        tagWrapper.setTag(TagWrapperKey);

        tagWrapper.start();
    }

    @Override
    public void onTagDeleted(Tag tag) {

    }
	
	public void showConfirmationBanner(boolean instantly) {
		confirmationBannerUp = true;
		if (activity == null) return;
		if (generatedView != null) {

            if (confirmation == null) {
                confirmation = scrapActionBar.createActionConfirmationContextMode(getString(R.string.ConfirmScrapTitle), getString(R.string.ActionDelete), R.drawable.ic_action_delete, staticConfirmationListener);
                confirmation.setTag(ConfirmationKey);

                confirmation.setBackgroundColor(getResources().getColor(android.R.color.holo_orange_light));
                confirmation.setCaretResource(R.drawable.caret_up);
                confirmation.setTextColor(0x88000000);
                confirmation.setSeparatorOpacity(0.25f);

                confirmation.start();
            }

		}
		
	}
	
	public void closeConfirmationBanner() {
		confirmationBannerUp = false;
		
		if (confirmation != null) {
            confirmation.dismiss();
        }
	}
	
	public void deleteCurrentScrap() {

		if (activity == null) return;
		if (generatedView != null) {
			
			dontRemoveViewOnNextDetach();
            activity.getFragmentManager().beginTransaction().remove(scrapActionBar).commit();
//			activity.deleteCurrentScrap(); // TODO
			
			final View window = generatedView.findViewById(R.id.ScrapWindow);
            Utils.ViewUtils.cancelAllAnimationsInViewGroup((ViewGroup) window.findViewById(R.id.ScrapWindowActionBar));
			
			Rect rct = new Rect();
			window.getGlobalVisibleRect(rct);
			window.setEnabled(false);
//			activity.undimBackground(); // TODO
			
			//Animate the screenshot
            window.setPivotX(rct.left);
            window.setPivotY(rct.bottom);
            window.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            window.buildLayer();
            
            if (phoneUI) {
	            window.animate()
            		.alpha(0f).rotationBy(20).xBy(rct.exactCenterX());
            }
            else {
            	window.animate()
            	.alpha(0f).rotationBy(20).yBy(rct.height()/4);
            }
            window.animate().setDuration(500)
        		.setInterpolator(new AccelerateInterpolator(2))
            	//listener is only ever called from a view, which requires the activity to exit, so it's safe to call getActivity(void)
            	.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationCancel(Animator animation) {
						((ViewGroup)window.getParent()).removeView(window);
					}
				});
            
		}
	}
	
	final static int halfDuration = 250;
	final static int duration = 500;
	
	public void toggleDetails() {
		toggleDetails(1f, halfDuration, false);
	}
	
	public void toggleDetails(final float multiplier, final long duration, final boolean decelerateFirst) {
		
		if (adapter != null && generatedView != null) {
			
			final View window = generatedView.findViewById(R.id.ScrapWindow);
			//adapter.toggleModeAndReloadAfter(false);
			
            window.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            window.buildLayer();
            
    		final DisplayMetrics metrics = new DisplayMetrics();
    		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        	window.setCameraDistance(metrics.widthPixels * 5);
            
            if (phoneUI) {
            	activity.showContents();
            }
            
            if (decelerateFirst) {
            	window.animate()
            		.setInterpolator(AnimationUtils.loadInterpolator(getActivity(), interpolator.linear));
            }
            else {
            	window.animate()
        		.setInterpolator(AnimationUtils.loadInterpolator(getActivity(), interpolator.accelerate_cubic));
            }
            
            if (DEBUG_GESTURE) Log.d("ViewerFragment", "Starting animation with initial rotation: " + window.getRotationY());
            final TimeInterpolator decelerateCubic = AnimationUtils.loadInterpolator(getActivity(), interpolator.decelerate_cubic);
            
        	window.animate()
        		.rotationY(-90 * multiplier)
           		.setDuration(
           				(long)(duration * (
           						90f - Math.abs(window.getRotationY()))
           						/ 90f
           					) 
           		)
            	//listener is only ever called from a view, which requires the activity to exit, so it's safe to call getActivity(void)
            	.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator animation) {
						toggleMode();
						
						window.setRotationY(90 * multiplier);
						window.animate()
			        		.rotationY(0)
			            	.setInterpolator(decelerateCubic)
			           		.setDuration(duration)
			           		.setListener(new AnimatorListener() {
								public void onAnimationStart(Animator animation) { }
								public void onAnimationRepeat(Animator animation) { }
								public void onAnimationEnd(Animator animation) { window.setLayerType(View.LAYER_TYPE_NONE, null); }
								public void onAnimationCancel(Animator animation) {	window.setLayerType(View.LAYER_TYPE_NONE, null); }
							});
					}
					
					@Override
					public void onAnimationCancel(Animator animation) {
						toggleMode();
					}
				});
		}
		
	}

    @Override
    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        if (item.getId() == R.id.action_delete) {
            showConfirmationBanner();
        }
        if (item.getId() == R.id.action_share) {
            // TODO
        }
        if (item.getId() == R.id.action_show_details) {
            toggleDetails();
            if (mode == ScrapListAdapter.ModeScrapInfo) {
                scrapActionBar.findItemWithId(R.id.action_show_details).setName(getString(R.string.ShowDetails));
            }
            else {
                scrapActionBar.findItemWithId(R.id.action_show_details).setName(getString(R.string.ShowItems));
            }
        }
        if (item.getId() == R.id.action_copy) {
            makeAllActive();
        }
        if (item.getId() == 1991) {
            adapter.debugFocus();
            ((AutoCompleteTextView) getActivity().getWindow().getCurrentFocus()).showDropDown();
        }
        if (item.getId() == R.id.menu_help) {
            showHelp(0, false);
        }
        if (item.getId() == R.id.menu_settings) {
            Intent settingsIntent = new Intent(getActivity(), SettingsActivity.class);
            startActivity(settingsIntent);
        }
        if (item.getId() == android.R.id.home) {
            getActivity().getFragmentManager().popBackStackImmediate();
        }
    }

	@Override
	public boolean onMenuItemClick(MenuItem item) {
		switch (item.getItemId()) {
		
		case R.id.menu_details: {
			toggleDetails();
			return true;
		}
		
		case R.id.menu_help: {
			showHelp(0, false);
			return true;
		}
		
		case R.id.menu_make_active: {
			makeAllActive();
			return true;
		}
		
		case R.id.menu_settings: {
        	Intent settingsIntent = new Intent(getActivity(), SettingsActivity.class);
        	startActivity(settingsIntent);
			return true;
		}
		
		case (R.id.menu_timestamp_dump) : {

			SQLiteDatabase database = Receipt.DBHelper.getReadableDatabase();
			Cursor priceFinder =  database.query(Receipt.DBReceiptsTable, new String[]{Receipt.DBPriceKey, Receipt.DBDateKey}, 
									Receipt.DBFilenameIdKey + "=" + currentTarget, null, null, null, null);
			if (priceFinder.getCount() != 0) {
				priceFinder.moveToFirst();
				Calendar d = Calendar.getInstance();
				d.setTimeInMillis(priceFinder.getLong(1) * 1000);
				Toast.makeText(activity, "Timestamp: " + d.get(Calendar.YEAR) + "/" + d.get(Calendar.MONTH) + "/" + d.get(Calendar.DATE) +
										" " + d.get(Calendar.HOUR) + ":" + d.get(Calendar.MINUTE) + ":" + d.get(Calendar.SECOND), Toast.LENGTH_LONG).show();
			}
			
			database.close();
			priceFinder.close();
			
			return true;
		}
		}
		return false;
	}
	
	@Override
	public void onSaveInstanceState(Bundle outState) {
		if (adapter != null) {
			outState.putBooleanArray(SelectionListKey, adapter.getSelectionList());
			outState.putLongArray(IdListKey, adapter.getIdList());
			outState.putInt(SelectionCountKey, adapter.getSelectionCount());
            outState.putLong(TagExpanderTargetKey, adapter.getTagExpanderTarget());
            adapter.saveTagExpanderStaticContext();
		}
		outState.putInt(CurrentHelpPageKey, currentHelpPage);
		outState.putBoolean(ConfirmationBannerUpKey, confirmationBannerUp);
	}
	
	public void restoreHelp() {
		if (DEBUG_HELP) Log.d("ViewerFragment", "Restoring help at page " + currentHelpPage);
		if (currentHelpPage != NoHelpPage && helpStory == null) {
			generatedView.getViewTreeObserver().addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
				@SuppressLint("NewApi")
				@SuppressWarnings("deprecation")
				@Override
				public void onGlobalLayout() {
					if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
						generatedView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
					else
						generatedView.getViewTreeObserver().removeGlobalOnLayoutListener(this);
					showHelp(currentHelpPage, true);
				}
			});
		}
	}
	
	public void showHelp(int page, boolean instant) {
		
		final DisplayMetrics metrics = new DisplayMetrics();
		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		
		int viewPosition[] = new int[2];
		int viewDimensions[] = new int[2];
		generatedView.findViewById(R.id.ScrapWindowActionBar).getLocationOnScreen(viewPosition);
		viewDimensions[0] = generatedView.findViewById(R.id.ScrapWindowActionBar).getWidth();
		viewDimensions[1] = generatedView.findViewById(R.id.ScrapWindowActionBar).getHeight();
		int x = viewPosition[0] + (int)(24 * metrics.density);
		int y = viewPosition[1] + viewDimensions[1] + (int)(24 * metrics.density);
		
		HelpOverlayBuilder page1 = new HelpOverlayBuilder(getActivity(), x + (int)(24 * metrics.density), y + (int)(24 * metrics.density));
		page1.setTitle(getString(R.string.ScrapTapTitle))
			.setExplanation(getString(R.string.ScrapTapDescription))
			.setScale(0.66f);
		
		HelpOverlayBuilder page2 = new HelpOverlayBuilder(getActivity(), generatedView.findViewById(LegacyActionBarView.OverflowID));
		page2.setTitle(getString(R.string.ScrapOverflowTitle))
			.setExplanation(getString(R.string.ScrapOverflowDescription))
			.setScale(0.66f);
		
		helpStory = new HelpStory(activity);
		helpStory.addPages(page1, page2);
		
		final ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
		final View content = root.getChildAt(0);
		
		helpStory.setOnSelectPageListener(new OnSelectPageListener() {
			@Override
			public void onSelectPage(int page) {
				currentHelpPage = page;
			}
		});
		
		helpStory.setOnCloseListener(new OnCloseListener() {
			@Override
			public void onClose(int page) {
				currentHelpPage = NoHelpPage;
				helpStory = null;
				content.animate()
					.alpha(0.4f)
					.setListener(new AnimatorListenerAdapter() {
						public void onAnimationStart(Animator a) {
							content.setVisibility(View.VISIBLE);
							content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
						}
						@Override
						public void onAnimationEnd(Animator a) {
							if (!phoneUI) content.setLayerType(View.LAYER_TYPE_NONE, null);
							generatedView.setLayerType(View.LAYER_TYPE_NONE, null);
						}
					});
			}
		});
		
		generatedView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		if (instant) {
			content.setAlpha(0);
			content.setVisibility(View.INVISIBLE);
		}
		else {
			content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
			content.animate()
				.alpha(0)
				.setStartDelay(0)
				.setListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(Animator a) {
						content.setVisibility(View.INVISIBLE);
						content.setLayerType(View.LAYER_TYPE_NONE, null);
					}
			});
		}
		
		if (instant) {
			helpStory.startStoryWithPageInstantly(page);
		}
		else {
			helpStory.startStoryWithPage(page);
		}
		
		currentHelpPage = page;
		
	}
	
	View pendingBlitzView;
	View pendingScreenshot;
	
	static class MakeAllActiveTask extends AsyncTask<Long, Void, Void> {

		@Override
		protected Void doInBackground(Long... ids) {
			final SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();
			
			db.execSQL("insert into " + Receipt.DBPendingTable + " (name, qty, unitOfMeasurement, price) " +
					"select name, qty, unitOfMeasurement, price from " + Receipt.DBItemsTable + "" +
					" where targetDB=" + ids[0]);
			
			db.close();
			
			return null;
		}
		
	}

	public void makeAllActive() {
		
		new MakeAllActiveTask().execute(currentTarget);
		
		final FrameLayout root = (FrameLayout)activity.getWindow().getDecorView();
		final View content = root.getChildAt(0);
		final View ScrapWindow = activity.findViewById(R.id.ScrapWindow);
		final Rect ScrapRect = new Rect();
		ScrapWindow.getGlobalVisibleRect(ScrapRect);
		
		final View BlitzView = new View(activity);
		pendingBlitzView = BlitzView;
		BlitzView.setBackgroundColor(0xFFFFFFFF);
		BlitzView.setAlpha(0);
		
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(root.getWidth(), root.getHeight());
		
		root.addView(BlitzView, params);
		
		if (phoneUI) {
			content.setVisibility(View.INVISIBLE);
			content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		}
		else
			content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		
		BlitzView.animate()
			.alpha(1)
			.setDuration(50)
			.setInterpolator(new AccelerateInterpolator(2))
			.setListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator a) {
					if (activity != null)
						BlitzView.animate()
							.alpha(0)
							.setDuration(250)
							.setInterpolator(new AccelerateInterpolator(1))
							.setListener(new AnimatorListenerAdapter() {
								public void onAnimationEnd(Animator a) {
									if (activity != null) {
										if (phoneUI) {
											content.setVisibility(View.VISIBLE);
										}
										else {
											content.setVisibility(View.VISIBLE);
											content.animate()
												.alpha(0.4f)
												.setListener(new AnimatorListenerAdapter() {
													public void onAnimationEnd(Animator a) {
														if (activity != null && !phoneUI) {
															content.setLayerType(View.LAYER_TYPE_NONE, null);
														}
													}
												});
										}
									}
									root.removeView(BlitzView);
									pendingBlitzView = null;
								}
							});
					else {
						root.removeView(BlitzView);
						pendingBlitzView = null;
					}
				}
			});
		
		final long contentAnimationDuration = content.animate().getDuration();
		
		if (!phoneUI)
			content.animate()
				.alpha(0)
				.setDuration(50)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationEnd(Animator a) {
						content.animate().setDuration(contentAnimationDuration);
						if (activity != null)
							content.setVisibility(View.INVISIBLE);
					}
				});
	}
	
	static class SelectionData {
		boolean[] selection;
		long[] ids;
		static SelectionData makeData(boolean[] selection, long[] ids) {
			SelectionData data = new SelectionData();
			data.selection = selection;
			data.ids = ids;
			return data;
		}
	}
	
	static class MakeSelectionActiveTask extends AsyncTask<SelectionData, Void, Void> {

		@Override
		protected Void doInBackground(SelectionData... ids) {
			final SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();
			
			StringBuilder idList = new StringBuilder();
			idList.append("(");
			for (int i = 0; i < ids[0].ids.length; i++) {
				if (ids[0].selection[i]) {
					idList.append(ids[0].ids[i]);
					idList.append(", ");
				}
			}
			idList.deleteCharAt(idList.length() - 2);
			idList.append(")");
			
			db.execSQL("insert into " + Receipt.DBPendingTable + " (name, qty, unitOfMeasurement, price) " +
					"select name, qty, unitOfMeasurement, price from " + Receipt.DBItemsTable + "" +
					" where _ID in " + idList.toString());
			
			db.close();
			
			return null;
		}
		
	}

	public void makeSelectionActive() {
		
		new MakeSelectionActiveTask().execute(SelectionData.makeData(adapter.getSelectionList().clone(), adapter.getIdList().clone()));
		
		final FrameLayout root = (FrameLayout)activity.getWindow().getDecorView();
		final View content = root.getChildAt(0);
		final View Window = phoneUI ? generatedView : activity.findViewById(R.id.ScrapWindow);
		final View ScrapWindow = activity.findViewById(R.id.ScrapWindow);
		final Rect ScrapRect = new Rect();
		ScrapWindow.getGlobalVisibleRect(ScrapRect);
		
		final View BlitzView = new View(activity);
		pendingBlitzView = BlitzView;
		BlitzView.setBackgroundColor(0xFFFFFFFF);
		BlitzView.setAlpha(0);
		
		FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(root.getWidth(), root.getHeight());
		
		
//		final ListView list = (ListView)Window.findViewById(R.id.ScrapWindowList);
//		list.getGlobalVisibleRect(ScrapRect);
		
		root.addView(BlitzView, params);
		
		if (phoneUI) {
			content.setVisibility(View.INVISIBLE);
			content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		}
		else
			content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		
		BlitzView.animate()
			.alpha(1)
			.setDuration(50)
			.setInterpolator(new AccelerateInterpolator(2))
			.setListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator a) {
					if (activity != null) {
						adapter.notifyDataSetChanged();
						BlitzView.animate()
							.alpha(0)
							.setDuration(250)
							.setInterpolator(new AccelerateInterpolator(1))
							.setListener(new AnimatorListenerAdapter() {
								public void onAnimationEnd(Animator a) {
									if (activity != null) {
										if (phoneUI) {
											content.setVisibility(View.VISIBLE);
										}
										else {
											content.setVisibility(View.VISIBLE);
											content.animate()
												.alpha(0.4f)
												.setListener(new AnimatorListenerAdapter() {
													public void onAnimationEnd(Animator a) {
														if (activity != null && !phoneUI) {
															content.setLayerType(View.LAYER_TYPE_NONE, null);
														}
													}
												});
										}
									}
									root.removeView(BlitzView);
									pendingBlitzView = null;
								}
							});
					}
					else {
						root.removeView(BlitzView);
						pendingBlitzView = null;
					}
				}
			});
		
		final long contentAnimationDuration = content.animate().getDuration();
		
		if (!phoneUI)
			content.animate()
				.alpha(0)
				.setDuration(50)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationEnd(Animator a) {
						content.animate().setDuration(contentAnimationDuration);
						if (activity != null)
							content.setVisibility(View.INVISIBLE);
					}
				});
	}

    public boolean handleMenuPressed() {
        if (helpStory != null) return true;
        scrapActionBar.showOverflow();
        return true;
    }
	
	public boolean handleBackPressed() {
		if (helpStory != null) {
			helpStory.exitStory();
			return true;
		}
		if (scrapActionBar.handleBackPress()) {
			return true;
		}
		if (pendingScreenshot != null)
			return true;
        if (adapter.handleBackPressed()) {
            return true;
        }
		return false;
	}
	
	public void onScrollRight(float amount) {
    	scrapWindow.setRotationY(scrapWindow.getRotationY() - (amount * (((float)80)/(float)scrapWindow.getWidth())));
	}
	
	final static boolean DEBUG_GESTURE = false;
	
    public void onSwipeRight(float amount, float velocity) {
    	//scrapWindow.setRotationY(scrapWindow.getRotationY() + amount);
    	if (DEBUG_GESTURE) Log.d("ViewerFragment", "Velocity is " + velocity);
    	float rotation = amount * (((float)80)/(float)scrapWindow.getWidth());
    	if (velocity == 0)
    		velocity = 1;
    	
    	if (velocity > 2500) velocity = 2500;
    	if (velocity < -2500) velocity = -2500;
    	
    	if (velocity < -500)
	    	toggleDetails(1, (long)(300 * 1000/Math.abs(velocity)), true);
    	else if (velocity > 500)
	    	toggleDetails(-1, (long)(300 * 1000/Math.abs(velocity)), true);
    	else if (rotation < -20)
	    	toggleDetails(1, (long)(250), false);
    	else if (rotation > 20)
	    	toggleDetails(-1, (long)(250), false);
    	else
	    	scrapWindow.animate()
    		.rotationY(0)
    		.setDuration((long)(100))
    		.setInterpolator(new DecelerateInterpolator());
    	
    }

    public void onSwipeLeft(float amount, float velocity) {
    	onSwipeRight(amount, velocity);
    }

    public void onSwipeTop() {
    }

    public void onSwipeBottom() {
    }
 
	
    @Deprecated
	class GestureListener extends SimpleOnGestureListener {

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }
        
        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX,float distanceY) {
            onScrollRight(distanceX);
            return false;
        }
        
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        	boolean result = false;
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > Math.abs(diffY)) {
                   onSwipeLeft(diffX, velocityX);
                   result = true;
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }
        
    }
	
}