package com.BogdanMihaiciuc.receipt;

import android.R.interpolator;
import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.LayoutTransition;
import android.app.Fragment;
import android.app.Service;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.BogdanMihaiciuc.receipt.IndicatorFragmentNonCompat.Task;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Locale;

@Deprecated
public class ItemsFragment extends Fragment {
	
	static String[] UnitsOfMeasurement = null;
	
	static class ViewHolder {
		int id;
    	TextView title;
    	TextView qtyTitle;
    	TextView priceTitle;
    	EditText titleEdit;
    	EditText qtyEdit;
    	EditText priceEdit;
    	View strikethrough;
    }
	
	private ViewGroup root;
	private boolean itemBeingAdded;
	private String itemBeingAddedUnit;
	private ViewHolder itemBeingAddedHolder;
	private ReceiptActivity activity;
	private ArrayList<Item> items;
	private DisplayMetrics metrics = new DisplayMetrics();
	
	//a list of possible attributes an item may have set
    //these may be combined binary for multiple attributes
    public final static int SetNone = 0;
    public final static int SetTitle = 1;
    public final static int SetQty = 2;
    public final static int SetPrice = 4;
	
	static class Item implements Serializable
	{
		
		private static final long serialVersionUID = 5393203670222014523L;
		
		String name;
		long qty;
		long price;
		
		boolean crossedOff;
		int controlFlags;
		
		long estimatedPrice;
		String measurementUnit;
		
	}
	
		public static  ItemListFragment.Item convert(Item i) {
			ItemListFragment.Item item = new ItemListFragment.Item();
			item.name = i.name;
			item.qty = i.qty * 100;
			item.price = i.price;
			item.crossedOff = i.crossedOff;
			item.flags = i.controlFlags;
			item.estimatedPrice = i.estimatedPrice;
			item.unitOfMeasurement = i.measurementUnit;
			return item;
		}
	
	
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_items, container, false);
		
	}
	
	public void onCreate (Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		selectionList = new ArrayList<Integer>();
		items = new ArrayList<Item>();
		activity = null;
		setRetainInstance(true);
		
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		activity = (ReceiptActivity) getActivity();
		root = (ViewGroup) activity.findViewById(R.id.ItemList);
		
		if (registeredRestoreData == null) {
			activity.postWantToRestore();
		}
		else {
			restoreItemsAndSelection(registeredRestoreData, null);
			registeredRestoreData = null;
		}
		
		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		minimumUnitSwipeDistance = metrics.density * 40;
		
		
	}
	
	public boolean isItemBeingAdded() {
		return itemBeingAdded;
	}
	

	public void restoreNewItemToList() {
		
//		activity.hideHint();
		
		View newItemView = activity.getLayoutInflater().inflate(R.layout.layout_item, null);
		//sets the id to a new int
		//This id is unique, as all other children have the id lower than the item count
		newItemView.setId(items.size());
		newItemView.setEnabled(false); //to disable clicking
		root.addView(newItemView, 0);
		
		//initialize the viewholder to minimize findviewbyid calls later
		final ViewHolder holder = new ViewHolder();
		holder.id = items.size();
		holder.title = (TextView)newItemView.findViewById(R.id.ItemTitle);
		holder.qtyTitle = (TextView)newItemView.findViewById(R.id.QtyTitle);
		holder.priceTitle = (TextView)newItemView.findViewById(R.id.PriceTitle);
		holder.titleEdit = (EditText)newItemView.findViewById(R.id.ItemTitleEditor);
		holder.qtyEdit = (EditText)newItemView.findViewById(R.id.QtyEditor);
		holder.priceEdit = (EditText)newItemView.findViewById(R.id.PriceEditor);
		holder.strikethrough = (View)newItemView.findViewById(R.id.ItemStrikethrough);
		
		itemBeingAddedHolder = holder;
		
		//initialize the view for editing
		newItemView.setTag(holder);
		holder.title.setVisibility(View.INVISIBLE);
		holder.titleEdit.setVisibility(View.VISIBLE);
		holder.qtyTitle.setVisibility(View.INVISIBLE);
		holder.priceTitle.setVisibility(View.INVISIBLE);
		holder.qtyEdit.setVisibility(View.VISIBLE);
		holder.priceEdit.setVisibility(View.VISIBLE);
		
		//set up listeners
		holder.titleEdit.setOnEditorActionListener(newItemListener);
		holder.titleEdit.setOnFocusChangeListener(focusLossListener);
		holder.titleEdit.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				showSuggestionsForView(holder.titleEdit);
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		holder.qtyEdit.setOnEditorActionListener(newItemListener);
		holder.qtyEdit.setOnFocusChangeListener(focusLossListener);
		holder.qtyEdit.setOnTouchListener(unitSelectorListener);
		holder.priceEdit.setOnEditorActionListener(newItemListener);
		holder.priceEdit.setOnFocusChangeListener(focusLossListener);
		
		switch (itemBeingAddedData.focus) {
		case 0:
			holder.titleEdit.requestFocus();
			break;
		case 1:
			holder.qtyEdit.requestFocus();
			break;
		case 2:
			holder.priceEdit.requestFocus();
			break;
		}

		holder.priceEdit.post(new Runnable() {
			public void run() {
				holder.titleEdit.setText(itemBeingAddedData.title);
				holder.qtyEdit.setText(itemBeingAddedData.qty);
				holder.qtyEdit.setHint(itemBeingAddedData.measurement);
				holder.priceEdit.setText(itemBeingAddedData.price);
			}
		});
    }
	
	@Override
	public void onDetach() {
		super.onDetach();
		//These will cause problems if allowed to run after rotation
		animationDelayHandler.removeCallbacks(this.unwindRunnableStack);
		this.crossAnimationRunnableStack.clear();
		this.uncrossAnimationRunnableStack.clear();
		//Cleanup context references
		root = null;
		activity = null;
		itemBeingAddedHolder = null;
		if (suggestionsTask != null) suggestionsTask.cancel(false);
	}
	
	private ArrayList<AnimationRunnable> crossAnimationRunnableStack = new ArrayList<AnimationRunnable>();
	private ArrayList<AnimationRunnable> uncrossAnimationRunnableStack = new ArrayList<AnimationRunnable>();
	private Runnable unwindRunnableStack = new Runnable() {
		@Override
		public void run() {
			for (AnimationRunnable runnable : crossAnimationRunnableStack) {
				runnable.run();
			}
			for (AnimationRunnable runnable : uncrossAnimationRunnableStack) {
				runnable.run();
			}
			// To enforce the correct positions! Hacky and possibly slow but it works.
			for (AnimationRunnable runnable : crossAnimationRunnableStack) {
				runnable.run();
			}
			crossAnimationRunnableStack.clear();
			uncrossAnimationRunnableStack.clear();
		}
	};
	
	public void addRunnableInstanceToStackForView(View view, AnimationRunnable runnable, int index) {
		
		if (!ReceiptActivity.reorderItems)
			return;
		
		//Ultimately, each view will animate just once, so if there was already a pending animation for this view
		//clear it
		if (runnable.animationType() == AnimationUncross) {
			int size = crossAnimationRunnableStack.size();
			for (int i = 0; i < size; i++) {
				if (crossAnimationRunnableStack.get(i).getView() == view) {
					crossAnimationRunnableStack.remove(i);
					break;
				}
			}
			size = uncrossAnimationRunnableStack.size();
			if (size == 0)
				uncrossAnimationRunnableStack.add(size, runnable);
			else {
				int i;
				for (i = 0; i < size; i++) {
					if (index > uncrossAnimationRunnableStack.get(i).getId())
						break;
				}
				uncrossAnimationRunnableStack.add(i, runnable);
			}
		}
		else {
			int size = uncrossAnimationRunnableStack.size();
			for (int i = 0; i < size; i++) {
				if (uncrossAnimationRunnableStack.get(i).getView() == view) {
					uncrossAnimationRunnableStack.remove(i);
					break;
				}
			}
			size = crossAnimationRunnableStack.size();
			if (size == 0)
				crossAnimationRunnableStack.add(size, runnable);
			else {
				int i;
				for (i = 0; i < size; i++) {
					if (index < crossAnimationRunnableStack.get(i).getId())
						break;
				}
				crossAnimationRunnableStack.add(i, runnable);
			}
		}
	}
	
	private final Handler animationDelayHandler = new Handler();
	
	interface AnimationRunnable extends Runnable {
		public View getView();
		public int animationType();
		public int getId();
	}
	
	final static int AnimationCross = 0;
	final static int AnimationUncross = 1;
	final static boolean ANIMATIONDEBUG = false;
	
	// TODO Fully capture state to prevent bad positioning
	// This runnable must be run on the UI thread
	class AnimateUncrossRunnable implements AnimationRunnable {
		public View view;
		DisplayMetrics metrics;
		int itemsSize, crossedOffCount;
		AnimateUncrossRunnable(View view, DisplayMetrics metrics, int itemsSize, int crossedOffCount) {
			this.view = view;
			this.metrics = metrics;
			this.itemsSize = itemsSize;
			this.crossedOffCount = crossedOffCount;
		}
		public View getView() {
			return view;
		}
		public int animationType() {
			return AnimationUncross;
		}
		public int getId() {
			return ((ViewHolder)view.getTag()).id;
		}
		@Override
		public void run() {
			ViewHolder holder = (ViewHolder) view.getTag();
			
			//Animate the restored view into its original location
    		//if possible
			int size = items.size();
			int location = 0;
			int currentId;
			//loop from the start, since it's likely to get results faster!
			if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Dispatched uncross runnable for id " + holder.id);
			if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Looking to place id " + holder.id);
			while (true) {
				//as a rule of thumb, uncrossed item location may not be larger than (size - id - 1)
				if (location == itemsSize - holder.id - 1) {
					break;
				}
				currentId = root.getChildAt(location).getId();
				if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Testing position:  " + location + "; Found id: " + currentId);
				if (items.get(currentId).crossedOff == true) {
					if (ANIMATIONDEBUG) Log.d("ItemsFragment", "This location is CROSSED OFF; placing here!");
					break;
				}
				else
					if (currentId <= holder.id) {
						if (ANIMATIONDEBUG) Log.d("ItemsFragment", "This location's id is smaller than our id; placing here!");
						break;
					}
				if (location == size-1) break; //for safety, might not be necessary
				location++;
			}
			if (ANIMATIONDEBUG) if (location==size-1) Log.d("ItemsFragment", "Couldn't find a suitable place until now. Is nothing crossed off?");
			
			if (location != root.indexOfChild(view)) {
				//If location stays the same, there's no reason to jump through hoops
	            root.setClipChildren(false);
				LayoutTransition transition = root.getLayoutTransition();
				Animator addItemAnimator = transition.getAnimator(LayoutTransition.APPEARING);
				transition.setAnimator(LayoutTransition.APPEARING, null);
				root.setLayoutTransition(null);
	    		root.removeView(view);
	    		view.setAlpha(0);
				root.setLayoutTransition(transition);
	    		root.addView(view, location);
				transition.setAnimator(LayoutTransition.APPEARING, addItemAnimator);
				root.setLayoutTransition(transition);
				if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Uncrossing " + holder.title.getText().toString() + " to location " + location);
	    		view.animate()
	    			.y(location * metrics.density * 48)
	    			.setInterpolator(AnimationUtils.loadInterpolator(activity, interpolator.decelerate_cubic))
	    			.setListener(new AnimatorListener() {
			    				public void onAnimationStart(Animator a) {
			    		    		view.setAlpha(1);
			    				}
			    				public void onAnimationRepeat(Animator a) {}
			    				public void onAnimationCancel(Animator a) {}
			    				public void onAnimationEnd(Animator a) {
			    					view.setTranslationY(0);
			    				}
			    			});
			}
		}
	}
	
	// This runnable must be run on the UI thread
	class AnimateCrossRunnable implements AnimationRunnable {
		public View view;
		DisplayMetrics metrics;
		int itemsSize, crossedOffCount;
		AnimateCrossRunnable(View view, DisplayMetrics metrics, int itemsSize, int crossedOffCount) {
			this.view = view;
			this.metrics = metrics;
			this.itemsSize = itemsSize;
			this.crossedOffCount = crossedOffCount;
		}
		public View getView() {
			return view;
		}
		public int animationType() {
			return AnimationCross;
		}
		public int getId() {
			return ((ViewHolder)view.getTag()).id;
		}
		@Override
		public void run() {
			ViewHolder holder = (ViewHolder) view.getTag();
			 //Animate the crossed off view into the bottom
    		//trying to fit its index within the crossed list

			//previous method fails
			//Just determine the new location based on the current position and state
			if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Dispatched cross runnable for id " + holder.id);
			int location = itemsSize - 1;
			int currentId;
			boolean foundItem = false;
			
			//Loop from behind, since it's likely to get results faster!
			//Runnables are now guaranteed to run ascending id order (descending location order)
			if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Looking to place id " + holder.id);
			if (!foundItem) {
				location = itemsSize - 1;
				while (true) {
					//as a rule of thumb, crossed off item location may not be smaller than (size - id - 1)
					if (location == itemsSize - holder.id - 1) {
						break;
					}
					currentId = root.getChildAt(location).getId();
					if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Testing position:  " + location + "; Found id: " + currentId);
					if (items.get(currentId).crossedOff == false) {
						if (ANIMATIONDEBUG) Log.d("ItemsFragment", "This location is NOT crossed off; placing here!");
						break;
					}
					else
						if (currentId >= holder.id) {
							if (ANIMATIONDEBUG) Log.d("ItemsFragment", "This location's id is bigger than our id; placing here!");
							break;
						}
					if (location == 0) break; //for safety, might not be necessary
					location--;
				}
			}
			if (ANIMATIONDEBUG) if (location==0) Log.d("ItemsFragment", "Couldn't find a suitable place until now. Is everything crossed off?");
			if (location != root.indexOfChild(view)) {
				//If location stays the same, there's no reason to jump through hoops
	            root.setClipChildren(false);
				final LayoutTransition transition = root.getLayoutTransition();
				Animator addItemAnimator = transition.getAnimator(LayoutTransition.APPEARING);
				transition.setAnimator(LayoutTransition.APPEARING, null);
				
				root.setLayoutTransition(null);
	    		root.removeView(view);
	    		view.setAlpha(0);
				root.setLayoutTransition(transition);
	    		root.addView(view, location);
				transition.setAnimator(LayoutTransition.APPEARING, addItemAnimator);
				root.setLayoutTransition(transition);
				if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Crossing " + holder.title.getText().toString() + " to location " + location);
				if (foundItem)
					view.animate().y((location+1) * metrics.density * 48);
				else
					view.animate().y(location * metrics.density * 48);
	    		view.animate().setInterpolator(AnimationUtils.loadInterpolator(activity, interpolator.decelerate_cubic))
	    			.setListener(new AnimatorListener() {
			    				public void onAnimationStart(Animator a) {
			    		    		view.setAlpha(1);
			    		    	}
			    				public void onAnimationRepeat(Animator a) {}
			    				public void onAnimationCancel(Animator a) {}
			    				public void onAnimationEnd(Animator a) {
			    					view.setTranslationY(0);
			    				}
			    			});
			}
		}
	}
	
	class AnimateResetRunnable implements AnimationRunnable {
		public View view;
		DisplayMetrics metrics;
		int itemsSize, crossedOffCount;
		AnimateResetRunnable(View view, DisplayMetrics metrics, int itemsSize, int crossedOffCount) {
			this.view = view;
			this.metrics = metrics;
			this.itemsSize = itemsSize;
			this.crossedOffCount = crossedOffCount;
		}
		public View getView() {
			return view;
		}
		public int animationType() {
			return AnimationUncross;
		}
		public int getId() {
			return ((ViewHolder)view.getTag()).id;
		}
		@Override
		public void run() {
			ViewHolder holder = (ViewHolder) view.getTag();
			
			//Animate the restored view into its original location
    		//if possible
			int size = items.size();
			int location = items.size() - holder.id - 1;
			
			if (ANIMATIONDEBUG) if (location==size-1) Log.d("ItemsFragment", "Couldn't find a suitable place until now. Is nothing crossed off?");
			
			if (location != root.indexOfChild(view)) {
				//If location stays the same, there's no reason to jump through hoops
	            root.setClipChildren(false);
				LayoutTransition transition = root.getLayoutTransition();
				Animator addItemAnimator = transition.getAnimator(LayoutTransition.APPEARING);
				transition.setAnimator(LayoutTransition.APPEARING, null);
				root.setLayoutTransition(null);
	    		root.removeView(view);
	    		view.setAlpha(0);
				root.setLayoutTransition(transition);
	    		root.addView(view, location);
				transition.setAnimator(LayoutTransition.APPEARING, addItemAnimator);
				root.setLayoutTransition(transition);
				if (ANIMATIONDEBUG) Log.d("ItemsFragment", "Uncrossing " + holder.title.getText().toString() + " to location " + location);
	    		view.animate()
	    			.y(location * metrics.density * 48)
	    			.setInterpolator(AnimationUtils.loadInterpolator(activity, interpolator.decelerate_cubic))
	    			.setListener(new AnimatorListener() {
			    				public void onAnimationStart(Animator a) {
			    		    		view.setAlpha(1);
			    				}
			    				public void onAnimationRepeat(Animator a) {}
			    				public void onAnimationCancel(Animator a) {}
			    				public void onAnimationEnd(Animator a) {
			    					view.setTranslationY(0);
			    				}
			    			});
			}
		}
	}
	
	// selection handlers
	private ArrayList<Integer> selectionList;
	private ActionMode actionMode = null;
	private boolean multipleSelection;
	private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
		
		private boolean multiMode;
		private boolean selectedTitle = false;
		
	    // Called when the action mode is created; startActionMode() was called
	    @Override
	    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
	    	multiMode = false;
	        // Inflate a menu resource providing context menu items
	        MenuInflater inflater = mode.getMenuInflater();
	        inflater.inflate(R.menu.item_selection, menu);
	        return true;
	    }

	    // Called each time the action mode is shown. Always called after onCreateActionMode, but
	    // may be called multiple times if the mode is invalidated.
	    @Override
	    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
	    	if (multiMode != multipleSelection) {
	    		multiMode = multipleSelection;
	    		menu.clear();
		        MenuInflater inflater = mode.getMenuInflater();
		    	if (multipleSelection) {
			    	// Inflate a menu resource providing context menu items
			        inflater.inflate(R.menu.item_multiple_selection, menu);
			        return true;
		    	}
		    	else {
		    		// Inflate a menu resource providing context menu items
			        inflater.inflate(R.menu.item_selection, menu);
			        return true;
		    	}
	    	}
	        return false; // Return false if nothing is done
	    }

	    // Called when the user selects a contextual menu item
	    @Override
	    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
	        switch (item.getItemId()) {
	        	case R.id.action_delete:
		    		if (uncrossAnimationRunnableStack.size() != 0 || crossAnimationRunnableStack.size() != 0)
		    			unwindRunnableStack.run();
		        	deleteSelection();
		        	mode.finish();
		        	return true;
	        	case R.id.action_crosoff:
	        		crossOffSelection();
	        		mode.finish();
	        		return true;
	        	case R.id.action_rename:
	        		editTitleForSelection();
	        		selectedTitle = true;
	        		mode.finish();
	        		return true;
//	        	case R.id.action_select_all:
//	        		selectAll();
	            default:
	                return false;
	        }
	    }

	    // Called when the user exits the action mode
	    @Override
	    public void onDestroyActionMode(ActionMode mode) {
	    	if (selectionList.size() != 0) {
	    		clearSelection();
	    	}
	    	if (!selectedTitle)
	    		if (uncrossAnimationRunnableStack.size() != 0 || crossAnimationRunnableStack.size() != 0)
	    			unwindRunnableStack.run();
	    	selectedTitle = false;
	    	actionMode = null;
	    }
	};
	
	//Listeners

	private OnClickListener itemClickListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			activity.findViewById(R.id.innerList).requestFocus();
			InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
			if (actionMode == null) 
				toggleCrossedOffForView(view, 2000);
			else
				toggleSelectionForView(view);
		}
	};
	
	private OnLongClickListener itemSelectListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(View view) {
			activity.findViewById(R.id.innerList).requestFocus();
			InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
			toggleSelectionForView(view);
			return true;
		}
	};
	
	private OnEditorActionListener newItemListener = new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView view, int keyCode, KeyEvent event) {
			
			if (keyCode == EditorInfo.IME_ACTION_DONE) {
				//focus change may occur after the done key is pressed
				//this can cause issues, so it's best to remove the listener
				//since this listener can potentially change the focus of either three fields
				//all three should be cleared
				ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
				holder.titleEdit.setOnFocusChangeListener(null);
				holder.priceEdit.setOnFocusChangeListener(null);
				holder.qtyEdit.setOnFocusChangeListener(null);
				
				//hide keyboard
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
				
				finishAddingItem(true);
				return true;
			}
			
			return false;
		}
	}, 
	
	editItemListener = new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView view, int keyCode, KeyEvent event) {
			
			if (keyCode == EditorInfo.IME_ACTION_DONE) {
				//focus change may occur after the done key is pressed
				//this can cause issues, so it's best to remove the listener
				view.setOnFocusChangeListener(null);
				
				//hide keyboard
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
				
				ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
				String newTitle = view.getText().toString().trim();
				if (!newTitle.isEmpty()) {
					items.get(holder.id).name = newTitle;
					holder.title.setText(newTitle);
				}
				holder.titleEdit.setVisibility(View.INVISIBLE);
				holder.title.setVisibility(View.VISIBLE);
				// reschedule pending animations
				animationDelayHandler.removeCallbacks(unwindRunnableStack);
				animationDelayHandler.postDelayed(unwindRunnableStack, 4000);
				return true;
			}
			
			return false;
		}
	}, 
	
	editQtyListener = new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView view, int keyCode, KeyEvent event) {
			
			if (keyCode == EditorInfo.IME_ACTION_DONE) {
				//focus change may occur after the done key is pressed
				//this can cause issues, so it's best to remove the listener
				view.setOnFocusChangeListener(null);
				
				//hide keyboard
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
				
				changeQtyForView(view);
				return true;
			}
			
			return false;
		}
	}, 
	
	editPriceListener = new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView view, int keyCode, KeyEvent event) {
			
			if (keyCode == EditorInfo.IME_ACTION_DONE) {
				//focus change may occur after the done key is pressed
				//this can cause issues, so it's best to remove the listener
				view.setOnFocusChangeListener(null);
				
				//hide keyboard
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
				
				changePriceForView(view);
				
				return true;
			}
			
			return false;
		}
	};
	
	private OnFocusChangeListener focusLossListener = new OnFocusChangeListener() {
		@Override
		public void onFocusChange(View view, boolean hasFocus) {
			if (hasFocus == false) {
				ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
				if (holder.titleEdit.hasFocus() | holder.qtyEdit.hasFocus() | holder.priceEdit.hasFocus()) {
					if (holder.id != items.size()) { //not dealing with a new item so commit changes
						if (view == holder.qtyEdit) changeQtyForView((TextView) view);
						if (view == holder.priceEdit) changePriceForView((TextView) view);
						if (view == holder.titleEdit) changeTitleForView((TextView) view);
					}
				}
				else { //Focus is out of this item, so commit changes
					if (holder.id == items.size()) { //if dealing with a new item, the commit call is different
						if (ReceiptActivity.DEBUG) Log.d("ItemsFragment", "Focus loss when a new item is being added.");
						finishAddingItem(true);
						return;
					}
					if (view == holder.qtyEdit) changeQtyForView((TextView) view);
					if (view == holder.priceEdit) changePriceForView((TextView) view);
					if (view == holder.titleEdit) changeTitleForView((TextView) view);
				}
			}
			
		}
	};
	
	private OnClickListener fieldEditListener = new OnClickListener() {
		public void onClick (View view) {
			if (actionMode == null) {
				editItemField(view);
				animationDelayHandler.removeCallbacks(unwindRunnableStack);
			}
			else {
				toggleSelectionForView((View)view.getParent());
			}
		}
	};
	
	private float minimumUnitSwipeDistance;
	private OnTouchListener unitSelectorListener = new OnTouchListener() {
		
		float startX, startY;
		boolean fired;
		View feedbackView;
		
		@Override
		public boolean onTouch(final View view, MotionEvent event) {
			if (event.getPointerCount() > 1) {
				view.getParent().getParent().requestDisallowInterceptTouchEvent(false);
				return false;
			}
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				startX = event.getX();
				startY = event.getY();
				fired = false;
				feedbackView = ((ViewGroup)view.getParent()).findViewById(R.id.QtyTouchHelper);
				feedbackView.setAlpha(0);
				feedbackView.setVisibility(View.VISIBLE);
				view.getParent().getParent().requestDisallowInterceptTouchEvent(true);
				return false;
			}
			if (!fired) {
				float alpha;
				alpha = (event.getY() - startY)/minimumUnitSwipeDistance;
				alpha = alpha < 0 ? 0 : alpha;
				feedbackView.setAlpha(alpha);
				feedbackView.setY(- (1-alpha) * feedbackView.getHeight());
			}
			if (event.getY() - startY > minimumUnitSwipeDistance) {
				if (Math.abs(event.getY() - startY) > Math.abs(event.getX() - startX) && !fired) {
					fired = true;
					feedbackView.setAlpha(1);
					feedbackView.setVisibility(View.GONE);
					feedbackView.setTranslationY(0);
					feedbackView = null;
				    PopupMenu popup = new PopupMenu(activity, view);
				    popup.inflate(R.menu.unit_of_measurement);
				    popup.setOnMenuItemClickListener(new OnMenuItemClickListener() {
						@Override
						public boolean onMenuItemClick(MenuItem item) {
							switch(item.getItemId()) {
							case R.id.UnitCount:
							case R.id.UnitGram:
							case R.id.UnitKilogram:
							case R.id.UnitLitre:
							case R.id.UnitOunce:
							case R.id.UnitPound:
								setUnitOfMeasurement((TextView)view, item.getTitle().toString());
								return true;
							default:
								return false;
							}
						}
					});
				    popup.show();
				    return true;
				}
			}
			if (event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() == 1) {
				if (feedbackView == null)
					return false;
				feedbackView.setAlpha(1);
				feedbackView.setTranslationY(0);
				feedbackView.setVisibility(View.GONE);
				feedbackView = null;
				view.getParent().getParent().requestDisallowInterceptTouchEvent(false);
				return false;
			}
			if (!fired)
				return true;
			return false;
		}
		
	};
	
	public void setUnitOfMeasurement(TextView view, String unit) {
		ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
		view.setHint(unit);
		view.requestFocus();
		try {
			Item data = items.get(holder.id);
			data.measurementUnit = unit;
		}
		catch (IndexOutOfBoundsException e) {
			itemBeingAddedUnit = unit;
		}
	}
	
	private ArrayList<Item> registeredRestoreData = null;
	//this schedules the data to be restored
	//the next time onActivityCreated() is called
	public void registerDataForRestore(ArrayList<Item> dataToRestore) {
		//however, if the activity has already been created
		//it's OK to call now
		if (activity == null)
			registeredRestoreData = dataToRestore;
		else
			restoreItemsAndSelection(dataToRestore, null);
	}
	
	// used to restore state on rotation or relaunch
	// this should not be called after items have been added
	public void restoreItemsAndSelection(ArrayList<Item> itemToRestore, ArrayList<Integer> selectionToRestore) {
		if (itemToRestore != null) items = itemToRestore;
		if (selectionToRestore != null) selectionList = selectionToRestore;
		
		//While restoring, temporarily disable the transitions
		LayoutTransition transition = root.getLayoutTransition();
		root.setLayoutTransition(null);
		
		if (items.size() != 0) { 
			//there are items to be restored
			View newItemView;
			int itemsToRestore = items.size();
			Item currentItem;
			int crossedOffCount = 0;
			//restore the item views and their states
			for (int i = 0; i < itemsToRestore; i++) {
				currentItem = items.get(i);
				LayoutInflater inflater = activity.getLayoutInflater();
				//re-add each previous item
				newItemView = inflater.inflate(R.layout.layout_item, null);
				//if the item is crossed off, add it to the end of the list!
				if (currentItem.crossedOff) {
					root.addView(newItemView, root.getChildCount() - crossedOffCount);
					crossedOffCount++;
				}
				else
					//else add it to the top
					root.addView(newItemView, 0);
				newItemView.setId(i);
				if (ReceiptActivity.DEBUG) Log.d("ItemsFragment", "Restored item " + i);
				final ViewHolder holder = new ViewHolder();
				holder.id = i;
				holder.title = (TextView)newItemView.findViewById(R.id.ItemTitle);
				holder.qtyTitle = (TextView)newItemView.findViewById(R.id.QtyTitle);
				holder.priceTitle = (TextView)newItemView.findViewById(R.id.PriceTitle);
				holder.titleEdit = (EditText)newItemView.findViewById(R.id.ItemTitleEditor);
				holder.titleEdit.addTextChangedListener(new TextWatcher() {
					@Override
					public void onTextChanged(CharSequence s, int start, int before, int count) {
						showSuggestionsForView(holder.titleEdit);
					}
					
					@Override
					public void beforeTextChanged(CharSequence s, int start, int count,
							int after) {
					}
					@Override
					public void afterTextChanged(Editable s) {
					}
				});
				holder.qtyEdit = (EditText)newItemView.findViewById(R.id.QtyEditor);
				holder.qtyEdit.setOnTouchListener(unitSelectorListener);
				holder.qtyEdit.setHint(currentItem.measurementUnit);
				holder.priceEdit = (EditText)newItemView.findViewById(R.id.PriceEditor);
				holder.strikethrough = (View)newItemView.findViewById(R.id.ItemStrikethrough);
				newItemView.setTag(holder);
				
				newItemView.setOnClickListener(itemClickListener);
				newItemView.setOnLongClickListener(itemSelectListener);
				holder.titleEdit.setVisibility(View.INVISIBLE);
				holder.title.setVisibility(View.VISIBLE);
				holder.title.setText(currentItem.name);
				holder.titleEdit.setText(currentItem.name);
				
				//qty
		    	if (currentItem.qty == 0) {
		    		holder.qtyTitle.setText("1.0" + currentItem.measurementUnit);
		    		holder.qtyEdit.setText("");
		    	}
		    	else {
		    		holder.qtyTitle.setText(ReceiptActivity.longToTruncatedDecimalString(currentItem.qty) + currentItem.measurementUnit);
		    		holder.qtyEdit.setText(ReceiptActivity.longToDecimalString(currentItem.qty));
		    	}
				holder.qtyEdit.setVisibility(View.INVISIBLE);
				holder.qtyTitle.setVisibility(View.VISIBLE);
				
				//price
		    	if (currentItem.price == 0) {
		    		if (ReceiptActivity.currentLocale != "")
		    			holder.priceTitle.setText(ReceiptActivity.currentLocale);
		    		else
		    			holder.priceTitle.setText("0");
		    		holder.priceEdit.setText("");
		    	}
		    	else {
		    		holder.priceTitle.setText(ReceiptActivity.currentTruncatedLocale 
		    				+ ReceiptActivity.longToDecimalString(currentItem.price));
		    		holder.priceEdit.setText(ReceiptActivity.longToDecimalString(currentItem.price));
		    	}
		    	holder.priceEdit.setVisibility(View.INVISIBLE);
		    	holder.priceTitle.setVisibility(View.VISIBLE);
		    	
		    	//flags and colors
		    	if (currentItem.crossedOff) {
		    		holder.title.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
		    		holder.qtyTitle.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
		    		holder.priceTitle.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
		    		holder.strikethrough.setVisibility(View.VISIBLE);
		    	}
		    	else {
		    		if ((currentItem.controlFlags & SetQty) == 0) {
			    		holder.qtyTitle.setTextColor(getResources().getColor(R.color.implicit_text_colors));
			    	}
			    	if ((currentItem.controlFlags & SetPrice) == 0) {
			    		holder.priceTitle.setTextColor(getResources().getColor(R.color.implicit_text_colors));
			    	}
		    	}
		    	
		    	holder.qtyTitle.setOnClickListener(fieldEditListener);
		    	holder.priceTitle.setOnClickListener(fieldEditListener);
				
			}
			
			if (selectionList != null) if (selectionList.size() != 0) {
				//There was a selection previously
				//we should restore that as well
				actionMode = activity.startActionMode(actionModeCallback);
				actionMode.setTitle(selectionList.size() + " selected");
				actionMode.setSubtitle(ReceiptActivity.currentLocale + ReceiptActivity.totalToTruncatedDecimalString(getSelectionTotal()) + " total");
				if (selectionList.size() > 1) {
					actionMode.invalidate();
				}
				for (Integer i : selectionList) {
					root.findViewById(i).setSelected(true);
				}
			}
		}

		//restoring is done, restore animations to the root view
		root.setLayoutTransition(transition);
		
		if (itemBeingAdded)
			restoreNewItemToList();
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
	                                ContextMenuInfo menuInfo) {
	    super.onCreateContextMenu(menu, v, menuInfo);
	    MenuInflater inflater = activity.getMenuInflater();
	    inflater.inflate(R.menu.item_selection, menu);
	}
	
	public void editTitleForSelection() {
		
		int id = selectionList.get(0);
		View itemView = root.findViewById(id);
		ViewHolder holder = (ViewHolder)itemView.getTag();
		holder.title.setVisibility(View.INVISIBLE);
		holder.titleEdit.setVisibility(View.VISIBLE);
		holder.titleEdit.setText(items.get(id).name);
		holder.titleEdit.setOnEditorActionListener(editItemListener);
		holder.titleEdit.setOnFocusChangeListener(focusLossListener);
		InputMethodManager manager = (InputMethodManager)activity.getSystemService(Service.INPUT_METHOD_SERVICE);
		manager.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
		holder.titleEdit.requestFocus();
		
	}
	
	public void changeTitleForView(View view) {

		ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
		String newTitle = holder.titleEdit.getText().toString().trim();
		if (!newTitle.isEmpty()) {
			items.get(holder.id).name = newTitle;
			holder.title.setText(newTitle);
		}
		holder.titleEdit.setVisibility(View.INVISIBLE);
		holder.title.setVisibility(View.VISIBLE);
		// reschedule pending animations
		animationDelayHandler.removeCallbacks(unwindRunnableStack);
		animationDelayHandler.postDelayed(unwindRunnableStack, 4000);
		
	}
	
	public void changeQtyForView(TextView view) {
		ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
		long newQty, qtyDifference;
		Item data = items.get(holder.id);
		try {
			newQty = new BigDecimal(view.getText().toString()).movePointRight(2).longValue();
		}
		catch (NumberFormatException exception) {
			newQty = 0;
		}
		qtyDifference = newQty - data.qty;
		if (newQty == 0) qtyDifference += 100;
		if (data.qty == 0) qtyDifference -= 100;
		if (newQty == 0) {
    		data.qty = 0;
    		holder.qtyTitle.setText("1.0" + data.measurementUnit);
    		//remove SetQty from controlFlags
    		data.controlFlags &= (~SetQty);
    		//if the item has been crossed off, the color should
    		//stay grey
    		if (!data.crossedOff) {
    			holder.qtyTitle.setTextColor(getResources().getColor(R.color.implicit_text_colors));
    		}
    	}
    	else {
    		data.qty = newQty;
    		holder.qtyTitle.setText(ReceiptActivity.longToTruncatedDecimalString(newQty) + data.measurementUnit);
    		//add SetQty to controlFlags
    		data.controlFlags |= SetQty;
    		if (!data.crossedOff) {
    			holder.qtyTitle.setTextColor(getResources().getColor(R.color.ItemTitle));
    		}
    	}
		if (data.crossedOff) activity.addToTotal(qtyDifference * data.price);
		holder.qtyEdit.setVisibility(View.INVISIBLE);
		holder.qtyTitle.setVisibility(View.VISIBLE);
		// reschedule pending animations
		animationDelayHandler.removeCallbacks(unwindRunnableStack);
		animationDelayHandler.postDelayed(unwindRunnableStack, 4000);
	}
	
	public void changePriceForView(TextView view) {
		ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
		long newPrice;
		long priceDifference;
		Item data = items.get(holder.id);
		boolean alreadyCrossedOff = data.crossedOff;
		try {
			newPrice = new BigDecimal(view.getText().toString()).movePointRight(2).longValue();
		}
		catch (NumberFormatException exception) {
			newPrice = 0;
		}
		priceDifference = newPrice - data.price;
		if (newPrice == 0) {
    		data.price = 0;
    		if (ReceiptActivity.currentLocale != "")
    			holder.priceTitle.setText(ReceiptActivity.currentLocale);
    		else
    			holder.priceTitle.setText("0");
    		//remove SetPrice from controlFlags
    		data.controlFlags &= (~SetPrice);
    		//if the item has been crossed off, the color should
    		//stay grey
    		if (!data.crossedOff) {
    			holder.priceTitle.setTextColor(getResources().getColor(R.color.implicit_text_colors));
    		}
    	}
    	else {
    		data.price = newPrice;
    		holder.priceTitle.setText(ReceiptActivity.currentTruncatedLocale 
    				+ ReceiptActivity.longToDecimalString(newPrice));
    		//add SetPrice from controlFlags
    		data.controlFlags |= SetPrice;
    		//when entering a new price, the item is crossed off
    		if (alreadyCrossedOff == false) 
    			toggleCrossedOffForView((View)view.getParent(), 4000);
    		/*activity.addToCrossedOffCount(1);
    		data.crossedOff = true;
    		holder.title.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
    		holder.qtyTitle.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
    		holder.priceTitle.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
    		holder.strikethrough.setVisibility(View.VISIBLE); */
    	}
		if (alreadyCrossedOff) {
			if (data.qty == 0) 
				activity.addToTotal(100 * priceDifference);
			else
				activity.addToTotal(data.qty * priceDifference);
		} /*
		else {
			if (data.qty == 0) 
				activity.addToTotal(100 * data.price);
			else
				activity.addToTotal(data.qty * data.price);
		} */
		if (alreadyCrossedOff || newPrice == 0) {
			// reschedule pending animations
			animationDelayHandler.removeCallbacks(unwindRunnableStack);
			animationDelayHandler.postDelayed(unwindRunnableStack, 4000);
		}
		holder.priceEdit.setVisibility(View.INVISIBLE);
		holder.priceTitle.setVisibility(View.VISIBLE);
	}
	
	public void toggleCrossedOffForView(final View view, int animationDelay) {
		
		ViewHolder holder = (ViewHolder)view.getTag();
		Item item = items.get(holder.id);
		
		DisplayMetrics metrics = new DisplayMetrics();
        activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
        
		if (item.crossedOff) {
			activity.addToCrossedOffCount(-1);
    		if (item.qty == 0)
    			activity.addToTotal(-100 * item.price);
    		else
    			activity.addToTotal(-item.qty * item.price);
			holder.title.setTextColor(getResources().getColor(R.color.set_text_colors));
			if ((item.controlFlags & SetQty) == 0) {
	    		holder.qtyTitle.setTextColor(getResources().getColor(R.color.implicit_text_colors));
	    	}
			else {
	    		holder.qtyTitle.setTextColor(getResources().getColor(R.color.set_text_colors));
				
			}
	    	if ((item.controlFlags & SetPrice) == 0) {
	    		holder.priceTitle.setTextColor(getResources().getColor(R.color.implicit_text_colors));
	    	}
	    	else {
	    		holder.priceTitle.setTextColor(getResources().getColor(R.color.set_text_colors));
	    	}
	    	item.crossedOff = false;
	    	holder.strikethrough.setVisibility(View.GONE);
	    	
	    	//Animate the restored view into its original location after a while
	    	//This removes redundant changes from the same view
	    	addRunnableInstanceToStackForView(view, new AnimateUncrossRunnable(view, metrics, items.size(), activity.getCrossedOffCount()), holder.id);
	    	animationDelayHandler.removeCallbacks(unwindRunnableStack);
	    	if (animationDelay > 0)
	    		animationDelayHandler.postDelayed(unwindRunnableStack, animationDelay);
	    	else
	    		unwindRunnableStack.run();
		}
		else {
			activity.addToCrossedOffCount(1);
    		if (item.qty == 0) 
    			activity.addToTotal(100 * item.price);
    		else
    			activity.addToTotal(item.qty * item.price);
    		holder.title.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
    		holder.qtyTitle.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
    		holder.priceTitle.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
    		holder.strikethrough.setVisibility(View.VISIBLE);
    		item.crossedOff = true;
            
    		//Animate the crossed off view into the bottom, after a while
	    	//This removes redundant changes from the same view
	    	addRunnableInstanceToStackForView(view, new AnimateCrossRunnable(view, metrics, items.size(), activity.getCrossedOffCount()), holder.id);
	    	animationDelayHandler.removeCallbacks(unwindRunnableStack);
	    	if (animationDelay > 0)
	    		animationDelayHandler.postDelayed(unwindRunnableStack, animationDelay);
	    	else
	    		unwindRunnableStack.run();
		}
		
	}
	
	public void toggleSelectionForView(View view) {
		
		int id = view.getId();
		if (ReceiptActivity.DEBUG) Log.d("ItemsFragment", "Selected item " + id);
		if (selectionList.contains(id)) {
			selectionList.remove((Integer)id);
			view.setSelected(false);
		}
		else {
			selectionList.add(id);
			view.setSelected(true);
		}
		
		if (actionMode == null) {
			//delay the animations until actionmode is done
	    	animationDelayHandler.removeCallbacks(unwindRunnableStack);
			multipleSelection = false;
			activity.findViewById(R.id.innerList).requestFocus();
			InputMethodManager manager = (InputMethodManager)activity.getSystemService(Service.INPUT_METHOD_SERVICE);
			manager.hideSoftInputFromWindow(view.getWindowToken(), 0);
			actionMode = activity.startActionMode(actionModeCallback);
		}
		if (selectionList.size() > 1 && !multipleSelection) {
			multipleSelection = true;
			actionMode.invalidate();
		}
		if (selectionList.size() == 1 && multipleSelection) {
			multipleSelection = false;
			actionMode.invalidate();
		}
		actionMode.setTitle(selectionList.size() + " selected");
		actionMode.setSubtitle(ReceiptActivity.currentLocale + ReceiptActivity.totalToTruncatedDecimalString(getSelectionTotal()) + " total");
		if (selectionList.size() == 0) {
			actionMode.finish();
			actionMode = null;
		}
		
	}
	
	public long getSelectionTotal() {
		long total = 0;
		
		for (int position : selectionList) {
			if (items.get(position).price != 0)
				if (items.get(position).qty == 0)
					total += items.get(position).price * 100;
				else
					total += items.get(position).price * items.get(position).qty;
		}
		
		return total;
	}
	
	public void clearSelection() {
		for (Integer index : selectionList) {
			root.findViewById(index).setSelected(false);
			actionMode = null;
		}
		selectionList.clear();
	}
	
	public void selectAll() {
		int size = items.size();
		for (int i = 0; i < size; i++) {
			root.findViewById(i).setSelected(true);
			if (!selectionList.contains(i))
				selectionList.add(i);
		}
		if (size > 1)
			multipleSelection = true;
		// This can only get called from the action mode, so it can't be null
		actionMode.setTitle(selectionList.size() + " selected");
		actionMode.setSubtitle(ReceiptActivity.currentLocale + ReceiptActivity.totalToTruncatedDecimalString(getSelectionTotal()) + " total");
		actionMode.invalidate();
	}
	
	private class ItemBeingAddedData {
		CharSequence title;
		CharSequence qty;
		CharSequence price;
		String measurement;
		int focus;
	} 
	private ItemBeingAddedData itemBeingAddedData;
	
	@Override
	public void onStop() {
		
		if (itemBeingAdded) {
			itemBeingAddedHolder.titleEdit.setOnFocusChangeListener(null);
			itemBeingAddedHolder.qtyEdit.setOnFocusChangeListener(null);
			itemBeingAddedHolder.priceEdit.setOnFocusChangeListener(null);
			itemBeingAddedData = new ItemBeingAddedData();
			itemBeingAddedData.title = itemBeingAddedHolder.titleEdit.getText();
			itemBeingAddedData.qty = itemBeingAddedHolder.qtyEdit.getText();
			itemBeingAddedData.price = itemBeingAddedHolder.priceEdit.getText();
			itemBeingAddedData.measurement = itemBeingAddedUnit;
			if (itemBeingAddedHolder.titleEdit.hasFocus())
				itemBeingAddedData.focus = 0;
			if (itemBeingAddedHolder.qtyEdit.hasFocus())
				itemBeingAddedData.focus = 1;
			if (itemBeingAddedHolder.priceEdit.hasFocus())
				itemBeingAddedData.focus = 2;
		}
		
		super.onStop();
	}
	
	public void addNewItemToList(View v) {
		
		if (actionMode != null) {
			actionMode.finish();
			actionMode = null;
		}
		
		View lastEditor = null;
//		activity.hideHint();
		
		if (itemBeingAdded) { //finish editing the current item before adding a new one
			//clear the listeners, since they may cause trouble
			itemBeingAddedHolder.titleEdit.setOnFocusChangeListener(null);
			itemBeingAddedHolder.qtyEdit.setOnFocusChangeListener(null);
			itemBeingAddedHolder.priceEdit.setOnFocusChangeListener(null);
			lastEditor = finishAddingItem(false);
		}
		itemBeingAdded = true;
		
		// commit pending animations since the item count will change
		// but do it after adding the previous item, since it can potentially schedule its own animations
    	animationDelayHandler.removeCallbacks(unwindRunnableStack);
    	unwindRunnableStack.run();
		
    	itemBeingAddedUnit = getString(R.string.Count);
		View newItemView = activity.getLayoutInflater().inflate(R.layout.layout_item, null);
		//sets the id to a new int
		//This id is unique, as all other children have the id lower than the item count
		newItemView.setId(items.size());
		newItemView.setEnabled(false); //to disable clicking
		root.addView(newItemView, 0);
		
		//initialize the viewholder to minimize findviewbyid calls later
		final ViewHolder holder = new ViewHolder();
		holder.id = items.size();
		holder.title = (TextView)newItemView.findViewById(R.id.ItemTitle);
		holder.qtyTitle = (TextView)newItemView.findViewById(R.id.QtyTitle);
		holder.priceTitle = (TextView)newItemView.findViewById(R.id.PriceTitle);
		holder.titleEdit = (EditText)newItemView.findViewById(R.id.ItemTitleEditor);
		holder.qtyEdit = (EditText)newItemView.findViewById(R.id.QtyEditor);
		holder.priceEdit = (EditText)newItemView.findViewById(R.id.PriceEditor);
		holder.strikethrough = (View)newItemView.findViewById(R.id.ItemStrikethrough);
		
		itemBeingAddedHolder = holder;
		
		//initialize the view for editing
		newItemView.setTag(holder);
		holder.title.setVisibility(View.INVISIBLE);
		holder.titleEdit.setVisibility(View.VISIBLE);
		holder.qtyTitle.setVisibility(View.INVISIBLE);
		holder.priceTitle.setVisibility(View.INVISIBLE);
		holder.qtyEdit.setVisibility(View.VISIBLE);
		holder.priceEdit.setVisibility(View.VISIBLE);
		
		//set up listeners
		holder.titleEdit.setOnEditorActionListener(newItemListener);
		holder.titleEdit.setOnFocusChangeListener(focusLossListener);
		holder.titleEdit.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				showSuggestionsForView(holder.titleEdit);
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		holder.qtyEdit.setOnEditorActionListener(newItemListener);
		holder.qtyEdit.setOnFocusChangeListener(focusLossListener);
		holder.qtyEdit.setOnTouchListener(unitSelectorListener);
		holder.priceEdit.setOnEditorActionListener(newItemListener);
		holder.priceEdit.setOnFocusChangeListener(focusLossListener);
		
		//This brings up the soft keyboard
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		holder.titleEdit.requestFocus();
		if (lastEditor != null) 
			lastEditor.setVisibility(View.INVISIBLE);
		else
			imm.showSoftInput(holder.titleEdit, 0); 
		
		
    }
	
	public View finishAddingItem(boolean removeFocus) {
		
		itemBeingAdded = false;
		itemBeingAddedHolder = null;
		//View itemView = root.findViewById(items.size());
		View itemView = root.getChildAt(0);
		itemView.setId(items.size());
		ViewHolder holder= (ViewHolder)itemView.getTag();
		
		String title = holder.titleEdit.getText().toString().trim();
		
		if (title.isEmpty()) {
			root.removeView(itemView);
			if (items.size() == 0 && removeFocus);
//				activity.showHint();
			return null;
		}
		
		itemView.setOnClickListener(itemClickListener);
		itemView.setOnLongClickListener(itemSelectListener);
		
		Item newItemData = new Item();
		
		//title
		newItemData.name = title;
		newItemData.price = 0l;
		newItemData.qty = 0l;
		newItemData.crossedOff = false;
		newItemData.controlFlags = SetTitle;
		newItemData.measurementUnit = itemBeingAddedUnit;
		holder.title.setVisibility(View.VISIBLE);
		holder.title.setText(title);
		
    	holder.qtyTitle.setOnClickListener(fieldEditListener);
    	holder.priceTitle.setOnClickListener(fieldEditListener);
    	
    	activity.addToItemCount(1);
		itemView.setEnabled(true); //to enable clicking 
    	
		items.add(newItemData);
		if (removeFocus) {
			holder.titleEdit.setVisibility(View.INVISIBLE);
		}
		this.changeQtyForView(holder.qtyEdit);
		this.changePriceForView(holder.priceEdit);
    	return holder.titleEdit;
    	
	}
	
	public void editItemField(View view) {
		
		ViewHolder holder = (ViewHolder)((View)view.getParent()).getTag();
		if (ReceiptActivity.DEBUG) Log.d("ItemsFragment", "EditField for id " + holder.id + " with price " + items.get(holder.id).price
				+ " and qty " + items.get(holder.id).qty);
		EditText editText;
		
		view.setVisibility(View.INVISIBLE);
		
		if (view == holder.qtyTitle) {
			editText = holder.qtyEdit;
			long qty = items.get(holder.id).qty;
			if (qty != 0)
				editText.setText(ReceiptActivity.longToDecimalString(qty));
			else
				editText.setText("");
			editText.setOnEditorActionListener(editQtyListener);
		}
		else {
			editText = holder.priceEdit;
			long price = items.get(holder.id).price;
			if (price != 0)
				editText.setText(ReceiptActivity.longToDecimalString(price));
			else
				editText.setText("");
			editText.setOnEditorActionListener(editPriceListener);
		}

		editText.setOnFocusChangeListener(focusLossListener);
		editText.setVisibility(View.VISIBLE);
		
		//This brings up the soft keyboard
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		editText.requestFocus();
		imm.showSoftInput(editText, 0);
		
		editText.requestFocus();
		
	}
	
	public void clear() {
		
		if (root == null) {
			//Bad call, time to exit!
			//This can happen due to animation callbacks not syncing correctly
			if (ReceiptActivity.DEBUG) Log.d("ItemsActivity", "Trapped bad call to recreateView()!");
			return;
		}
		

		//The animations will cause a crash if allowed to run after the list has been detached
		animationDelayHandler.removeCallbacks(this.unwindRunnableStack);
		this.crossAnimationRunnableStack.clear();
		this.uncrossAnimationRunnableStack.clear();
		
		if (actionMode != null) actionMode.finish();
		
		activity.findViewById(R.id.innerList).requestFocus();
		
		//A new backing list is made, rather than immediately clearing the old one
		//as it can  still be used for saving by a background thread
		items = new ArrayList<Item>();
		selectionList.clear();
		
		//RemoveAllViews can be slow since control has to loop on all items
		//remove each one and then update the UI
		//root.removeAllViews();
		
		//This optimization removes the whole fragment root tree to speed things up
		//since there is no batch animation involved, and the whole tree is removed at once
		//But this requires a subsequent call to recreateView()
		ViewGroup rootParent = (ViewGroup)root.getParent();
		//This used to be findViewById(R.id.itemsFragment);
		rootParent.removeViewAt(0);
		root = null;
	}
	
	public void recreateView() {
		
		//This regenerates the fragment view and should only be used after a call to clear()
		//Since inflating an xml is hard work, this should ideally be called when there aren't
		//any animations running or they might get janky
		
		if (root != null) {
			//Bad call, time to exit!
			//This can happen if the user rotates the screen
			//after clear() is called, but before recreateView() gets called
			if (ReceiptActivity.DEBUG) Log.d("ItemsActivity", "Trapped bad call to recreateView()!");
			return;
		}
		
//		activity.getLayoutInflater().
//				inflate(R.layout.fragment_items, (ViewGroup)activity.findViewById(R.id.itemsFragment), true);
//		root = (ViewGroup) activity.findViewById(R.id.ItemList);
	}
	
	public ArrayList<Item> items() {
		return items;
	}
	
	public void deleteSelection() {
		
		for (int i : selectionList) {
			//First remove the view associated with the removed object
			root.removeView(root.findViewById(i));
			Item item = items.get(i);
			if (item.crossedOff) {
				if (item.qty == 0) {
					activity.addToTotal(-item.price * 100);
				}
				else {
					activity.addToTotal(-item.price * item.qty);
				}
				activity.addToCrossedOffCount(-1);
			}
		}
		//Reconstruct the view indexes based on their previous ids
		//so they remain in sync with the data array
		//Their position in the list may no longer correspond with their id
		int totalItems = root.getChildCount();
		int itemsLeft = 0;
		int lastIdFound = 0;
		View currentView = null;
		ViewHolder holder = null;
		while (itemsLeft < totalItems) {
			currentView = null;
			while (currentView == null) {
				currentView = root.findViewById(lastIdFound);
				lastIdFound++;
				//if (ReceiptActivity.DEBUG) Log.d("ItemsFragment", "Incremented lastIdFound to " + lastIdFound);
			}
			holder = (ViewHolder)currentView.getTag();
			currentView.setId(itemsLeft);
			holder.id = itemsLeft;
			if (ReceiptActivity.DEBUG) Log.d("ItemsFragment", "Reassigned view at id " + (lastIdFound - 1) + " to id " + itemsLeft);
			//view = root.getChildAt(totalItems - itemsLeft);
			//if (ReceiptActivity.DEBUG) Log.d("ItemsFragment", "Reassigned view from id " + view.getId() + " to id " + (itemsLeft - 1));
			//view.setId(itemsLeft - 1);
			//((ViewHolder)view.getTag()).id = (itemsLeft-1);
			itemsLeft++;
		}
		//remove the data associated with the deleted items
		ArrayList<Item> itemsToRemove = new ArrayList<Item>();
		int selectionListSize = selectionList.size();
		for (int i = 0; i < selectionListSize; i++) {
			itemsToRemove.add(items.get(selectionList.get(i)));
		}
		items.removeAll(itemsToRemove);
		//selected items no longer exist
		activity.addToItemCount(-selectionList.size());
		selectionList.clear();
		
	}
	

	public ArrayList<Item> deleteCrossedOffItems() {
		
		animationDelayHandler.removeCallbacks(unwindRunnableStack);
		crossAnimationRunnableStack.clear();
		uncrossAnimationRunnableStack.clear();
		
		LayoutTransition transition = root.getLayoutTransition();
		root.setLayoutTransition(null);

		ArrayList<Item> itemsToRemove = new ArrayList<Item>();
		
		for (Item item : items) {
			//First remove the view associated with the removed object
			if (item.crossedOff) {
				if (item.qty == 0) {
					activity.fastAddToTotal(-item.price * 100);
				}
				else {
					activity.fastAddToTotal(-item.price * item.qty);
				}
				activity.fastAddToItemCount(-1);
				root.removeView(root.findViewById(items.indexOf(item)));
				itemsToRemove.add(item);
			}
		}
		
		root.setLayoutTransition(transition);
		
		//Reconstruct the view indexes based on their previous ids
		//so they remain in sync with the data array
		//Their position in the list may no longer correspond with their id
		int totalItems = root.getChildCount();
		int itemsProcessed = 0;
		int lastIdFound = 0;
		View currentView = null;
		ViewHolder holder = null;
		while (itemsProcessed < totalItems) {
			currentView = null;
			while (currentView == null) {
				currentView = root.findViewById(lastIdFound);
				lastIdFound++;
			}
			holder = (ViewHolder)currentView.getTag();
			currentView.setId(itemsProcessed);
			holder.id = itemsProcessed;
			itemsProcessed++;
		}
		//remove the data associated with the deleted items
		
		items.removeAll(itemsToRemove);
		
		activity.addToItemCount(0);
		activity.addToTotal(0);
		activity.addToCrossedOffCount(-activity.getCrossedOffCount());
		
		return itemsToRemove;
		
	}
	
	public void crossOffSelection() {
		
		Item item;
		for (int i : selectionList) {
			item = items.get(i);
			if (item.crossedOff == false) {
				toggleCrossedOffForView(root.findViewById(i), 2000);
			}
		}
	}
	
	class FindSuggestionsAsyncTask extends AsyncTask<String, Void, String[]> {
		
		View anchor;
		
		FindSuggestionsAsyncTask(View anchor) {
			this.anchor = anchor;
		}

		@Override
		protected String[] doInBackground(String... arg0) {
			SQLiteDatabase db = Receipt.DBHelper.getReadableDatabase();
			
			if (isCancelled()) {
				db.close();
				return null;
			}

			String text = arg0[0].toLowerCase(Locale.getDefault()) + "%" ;
			Cursor query = db.rawQuery("select " + Receipt.DBNameKey + ", count(*) as cnt" +
										" from " + Receipt.DBItemsTable + 
										" group by " + Receipt.DBNameKey +
										" having lower(" + Receipt.DBNameKey + ") like ?" +
										" order by cnt desc" +
										" limit 5", new String[]{text});
			
			String result[] = null;
			
			if (isCancelled()) {
				query.close();
				db.close();
				return null;
			}
			
			int index = 0;
			if (query.getCount() != 0) {
				
				result = new String[query.getCount()];
				
				while (query.moveToNext()) {
					
					if (isCancelled()) {
						query.close();
						db.close();
						return null;
					}
					
					result[index] = query.getString(0);
					index++;
				}
			}
			
			query.close();
			db.close();
			return result;
		}
		
		@Override
		protected void onPostExecute(String[] results) {
			preparePopupMenu(results, anchor);
		}
		
	}
	
	private FindSuggestionsAsyncTask suggestionsTask;
	final static boolean DEBUG_SUGGESTIONS = false;
	
	public void preparePopupMenu(String[] suggestions, View anchor) {
		
		if (DEBUG_SUGGESTIONS) Log.d("ItemsFragment", "Control reached preparePopupMenu(String[], View);");
		
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, R.layout.layout_suggestion, R.id.Suggestion);
		if (suggestions != null) adapter.addAll(suggestions);
		((AutoCompleteTextView)anchor).setAdapter(adapter);
		adapter.notifyDataSetChanged();
		
	}
	
	public void showSuggestionsForView(TextView view) {
		
		if (DEBUG_SUGGESTIONS) Log.d("ItemsFragment", "Control reached showSuggestionsForView(TextView);");
		
		if (view.getText().toString().isEmpty()) return;
		if (suggestionsTask != null) suggestionsTask.cancel(false);
		suggestionsTask = new FindSuggestionsAsyncTask(view);
		suggestionsTask.execute(view.getText().toString());
	}
	
	public void notifyLocaleChanged() {
		final int ViewCount = root.getChildCount();
		for (int i = 0; i < ViewCount; i++) {
			ViewHolder holder = ((ViewHolder)root.getChildAt(i).getTag());
			if (items.get(holder.id).price != 0)
				holder.priceTitle.setText(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(items.get(holder.id).price));
			else
	    		if (ReceiptActivity.currentLocale != "")
	    			holder.priceTitle.setText(ReceiptActivity.currentLocale);
	    		else
	    			holder.priceTitle.setText("0");
		}
	}
	
	public void notifyOrderingRuleChanged() {
		
		if (itemBeingAdded) {
			itemBeingAddedHolder.titleEdit.setOnFocusChangeListener(null);
			itemBeingAddedHolder.qtyEdit.setOnFocusChangeListener(null);
			itemBeingAddedHolder.priceEdit.setOnFocusChangeListener(null);
			LayoutTransition transition = root.getLayoutTransition();
			root.setLayoutTransition(null);
			finishAddingItem(true);
			root.setLayoutTransition(transition);
		}
		
		final int ViewCount = root.getChildCount();
		animationDelayHandler.removeCallbacks(unwindRunnableStack);
		if (ReceiptActivity.reorderItems)
			for (int i = ViewCount - 1; i >= 0; i--) {
				ViewHolder holder = ((ViewHolder)root.getChildAt(i).getTag());
				if (items.get(holder.id).crossedOff) {
			    	new AnimateCrossRunnable((View)holder.strikethrough.getParent(), metrics, items.size(), activity.getCrossedOffCount()).run();
				}
			}
		else
			for (int i = 0; i < ViewCount; i++) {
				ViewHolder holder = ((ViewHolder)root.getChildAt(i).getTag());
				if (items.get(holder.id).crossedOff) {
			    	new AnimateResetRunnable((View)holder.strikethrough.getParent(), metrics, items.size(), activity.getCrossedOffCount()).run();
				}
			}
	}

	public void loadPendingItems() {
		new LoadPendingItemsAsyncTask().execute();
	}
	
	protected void addPendingItems(ArrayList<Item> itemsToAdd, Task task) {
		
		if (activity != null) {
			LayoutTransition transition = root.getLayoutTransition();
			root.setLayoutTransition(null);
			
			animationDelayHandler.removeCallbacks(unwindRunnableStack);
			unwindRunnableStack.run();
			
			int position;
			
			if (itemBeingAdded) {
				position = 1;
				root.getChildAt(0).setId(items.size() + itemsToAdd.size());
				((ViewHolder)root.getChildAt(0).getTag()).id = items.size() + itemsToAdd.size();
			}
			else {
				position = 0;
			}
			
			if (itemsToAdd.size() != 0) { 
				
//				activity.hideHint();
				
				//there are items to be restored
				View newItemView;
				int itemsToRestore = itemsToAdd.size();
				Item currentItem;
				
				activity.addToItemCount(itemsToRestore);
				
				int currentItemCount = items.size();
				
				//restore the item views and their states
				for (int i = 0; i < itemsToRestore; i++) {
					currentItem = itemsToAdd.get(i);
					items.add(currentItem);
					LayoutInflater inflater = activity.getLayoutInflater();
					//re-add each previous item
					newItemView = inflater.inflate(R.layout.layout_item, null);
					
					
					newItemView.setId(currentItemCount + i);
					final ViewHolder holder = new ViewHolder();
					holder.id = currentItemCount + i;
					holder.title = (TextView)newItemView.findViewById(R.id.ItemTitle);
					holder.qtyTitle = (TextView)newItemView.findViewById(R.id.QtyTitle);
					holder.priceTitle = (TextView)newItemView.findViewById(R.id.PriceTitle);
					holder.titleEdit = (EditText)newItemView.findViewById(R.id.ItemTitleEditor);
					holder.titleEdit.addTextChangedListener(new TextWatcher() {
						@Override
						public void onTextChanged(CharSequence s, int start, int before, int count) {
							showSuggestionsForView(holder.titleEdit);
						}
						
						@Override
						public void beforeTextChanged(CharSequence s, int start, int count,
								int after) {
						}
						@Override
						public void afterTextChanged(Editable s) {
						}
					});
					holder.qtyEdit = (EditText)newItemView.findViewById(R.id.QtyEditor);
					holder.qtyEdit.setOnTouchListener(unitSelectorListener);
					holder.qtyEdit.setHint(currentItem.measurementUnit);
					holder.priceEdit = (EditText)newItemView.findViewById(R.id.PriceEditor);
					holder.strikethrough = (View)newItemView.findViewById(R.id.ItemStrikethrough);
					newItemView.setTag(holder);
					
					newItemView.setOnClickListener(itemClickListener);
					newItemView.setOnLongClickListener(itemSelectListener);
					holder.titleEdit.setVisibility(View.INVISIBLE);
					holder.title.setVisibility(View.VISIBLE);
					holder.title.setText(currentItem.name);
					holder.titleEdit.setText(currentItem.name);
		    		holder.qtyTitle.setText("1.0" + currentItem.measurementUnit);
		    		holder.qtyEdit.setText("");
					holder.qtyEdit.setVisibility(View.INVISIBLE);
					holder.qtyTitle.setVisibility(View.VISIBLE);
					
					//price
			    	if (currentItem.price == 0) {
			    		if (ReceiptActivity.currentLocale != "")
			    			holder.priceTitle.setText(ReceiptActivity.currentLocale);
			    		else
			    			holder.priceTitle.setText("0");
			    		holder.priceEdit.setText("");
			    	}
			    	else {
			    		holder.priceTitle.setText(ReceiptActivity.currentTruncatedLocale 
			    				+ ReceiptActivity.longToDecimalString(currentItem.price));
			    		holder.priceEdit.setText(ReceiptActivity.longToDecimalString(currentItem.price));
			    	}
			    	holder.priceEdit.setVisibility(View.INVISIBLE);
			    	holder.priceTitle.setVisibility(View.VISIBLE);
				    		holder.qtyTitle.setTextColor(getResources().getColor(R.color.implicit_text_colors));
				    		
			    	if ((currentItem.controlFlags & SetPrice) == 0) {
			    		holder.priceTitle.setTextColor(getResources().getColor(R.color.implicit_text_colors));
			    	}
			    	
			    	holder.qtyTitle.setOnClickListener(fieldEditListener);
			    	holder.priceTitle.setOnClickListener(fieldEditListener);
			    	
					root.addView(newItemView, position);
					
				}
				
				activity.getIndicator().stopWorking(task);
			}
			
			root.setLayoutTransition(transition);
		}
		else {
			items.addAll(itemsToAdd);
		}
	}
    
    class LoadPendingItemsAsyncTask extends AsyncTask<Void, Void, ArrayList<Item>> {
    	
    	private Task task;
    	private IndicatorFragmentNonCompat indicator;
    	
    	protected void onPreExecute() {
    		task = Task.createTask("Loading items", null);
    	}

		@Override
		protected ArrayList<Item> doInBackground(Void... params) {
			SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();
			
			Cursor pendingItems = db.query(Receipt.DBPendingTable, Receipt.DBAllPendingItemsColumns, 
					null, null, null, null, null);
			
			ArrayList<Item> newItems = new ArrayList<Item>();
			
			if (pendingItems.getCount() > 0)
				publishProgress();
			
			while (pendingItems.moveToNext()) {
				Item item = new Item();
				item.controlFlags = SetTitle;
				item.crossedOff = false;
				item.price = pendingItems.getLong(Receipt.DBPriceKeyIndex);
				if (item.price > 0) item.controlFlags |= SetPrice;
				item.name = pendingItems.getString(Receipt.DBNameKeyIndex);
				item.measurementUnit = pendingItems.getString(Receipt.DBUnitOfMeasurementKeyIndex);
				item.qty = 0;
				newItems.add(item);
			}
			
			db.execSQL("delete from " + Receipt.DBPendingTable);
			
			db.close();
			return newItems;
		}
		
		@Override
		protected void onProgressUpdate(Void ... progress) {

    		if (activity != null) {
    			indicator = activity.getIndicator();
    			indicator.startWorkingInstantly(task);
    		}
    		
		}
		
		@Override
		protected void onPostExecute(ArrayList<Item> result) {
			addPendingItems(result, task);
		}
    	
    }
    
}
