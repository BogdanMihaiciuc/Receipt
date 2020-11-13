package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Fragment;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.view.ViewCompat;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.ActionMode;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.CycleInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.BogdanMihaiciuc.receipt.IndicatorFragmentNonCompat.Task;
import com.BogdanMihaiciuc.util.TagView;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Locale;

@Deprecated
public class ItemListFragment extends Fragment {
	
	final static boolean DEBUG = true;
	final static boolean DEBUG_EDITOR_POSITION = false;
    final static boolean DEBUG_DELETION = false;
	final static boolean DEBUG_SUGGESTIONS = false;
	final static boolean DEBUG_TITLE_FOCUS = false;
	final static boolean DEBUG_SELECTION = false;
	final static boolean DEBUG_PRICES = false;
	final static boolean DEBUG_SWIPE_DELETE = false;
	final static boolean DEBUG_ANCHORS = false;
    final static boolean DEBUG_HANDLER = false;
    
	final static String TAG = "ItemListFragment";
	
	static class ViewHolder {
		int id;
    	TextView title;
    	TextView qty;
    	TextView price;
    	View strikethrough;
    	
    	View itemRoot;
    	View deleteRoot;
    	
    	Animator animator;
    }
	
	final static int EditorModeAll = 0;
	final static int EditorModeTitle = 1;
	final static int EditorModeQty = 2;
	final static int EditorModePrice = 3;
	
	final static int FocusedTitle = 0;
	final static int FocusedQty = 1;
	final static int FocusedPrice = 2;
	final static int FocusedNone = -1;
	
	final static int InvalidTarget = -1;
	final static int NewItemTarget = 0;

    final static int SetNone = 0; //Denotes an item being added
    final static int SetTitle = 1;
    final static int SetQty = 2;
    final static int SetPrice = 4;
    
    final static int NoAnchor = Integer.MAX_VALUE;
    
	final static int DefaultHeight = -1;
	
	final static long LayoutAnimationDuration = 300;
	
	final static int ReorderDelay = 4000;

    @Deprecated
	static class Item
	{
		
		String name;
		long qty;
		long price;
		
		boolean crossedOff;
		int flags;
		
		long estimatedPrice;
		String unitOfMeasurement;
		
		// These two fields are run-time dependent and not flattened into files
		boolean invalidated;
		boolean selected;
		
		public Item clipboardCopy() {
			Item copy = new Item();
			copy.name = name;
			copy.qty = qty;
			copy.price = price;
			copy.estimatedPrice = estimatedPrice;
			copy.unitOfMeasurement = unitOfMeasurement;
			copy.flags = flags;
			return copy;
		}
		
		public void flatten(ObjectOutputStream os) throws IOException {
			//CrashGuard
			if (name != null)
				os.writeUTF(name);
			else
				os.writeUTF("-");
			
			os.writeLong(qty);
			os.writeLong(price);
			os.writeBoolean(crossedOff);
			os.writeInt(flags);
			os.writeLong(estimatedPrice);

			//CrashGuard
			if (unitOfMeasurement != null)
				os.writeUTF(unitOfMeasurement);
			else
				os.writeUTF("x");
		}
		
		public static Item inflate(ObjectInputStream is) throws IOException {
			Item item = new Item();
				item.name = is.readUTF();
				item.qty = is.readLong();
				item.price = is.readLong();
				item.crossedOff = is.readBoolean();
				item.flags = is.readInt();
				item.estimatedPrice = is.readLong();
				item.unitOfMeasurement = is.readUTF();
			return item;
		}
		
		public CharSequence toMenuString(Context context) {
			SpannableStringBuilder builder = new SpannableStringBuilder();
			builder.append(name);
			builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.implicit_text_colors)), 
					name.length(), name.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
			builder.append(" - ").append(ReceiptActivity.currentTruncatedLocale).append(ReceiptActivity.longToDecimalString(estimatedPrice));
			return builder;
		}
		
	}
	
	final static Item NullItem = new Item();
	
	static {
		NullItem.unitOfMeasurement = "";
		NullItem.name = "";
	}
	
	static class PartialCheckoutItems {
		ArrayList<Item> items;
		ArrayList<Integer> positions;
	}
	
	private ArrayList<Item> items;
	
	private ViewGroup listRoot;
	private DisableableListView list;
	
	private ViewGroup root;
	
	private ViewGroup editor;
	private AutoCompleteTextView titleEditor;
	private EditText qtyEditor;
	private EditText priceEditor;
	private TextView editorTitle;
	private TextView editorQty;
	private TextView editorPrice;
	private View editorStrikethrough;
	private View editorFloatingBackground;
	private View titleCompletionHelper;

    private TagExpander currentExpander;
	
	static class AnimatorHolder {
		Animator animator;
		int target;
	}
	
	private ValueAnimator deleteAnimator;
	private ArrayList<AnimatorHolder> animators = new ArrayList<AnimatorHolder>();
	private ArrayList<Integer> anchors = new ArrayList<Integer>();
	
	private ReceiptActivity activity;
	private final DisplayMetrics metrics = new DisplayMetrics();
	
	private ItemListAdapter adapter = new ItemListAdapter();
	
	private boolean editorVisible;
	private int editorTarget = InvalidTarget;
	private int editorMode;
	private int editorFocus;
	
	private int scrollState;
	
	private int confirmingPosition = ListView.INVALID_POSITION;
	
	private float minimumUnitSwipeDistance;
	
	private BigDecimal selectionTotal = new BigDecimal(0);
	
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		adapter = new ItemListAdapter();
		items = new ArrayList<Item>();
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		
		listRoot = (ViewGroup) inflater.inflate(R.layout.fragment_list_items, container, false);
		list = (DisableableListView) listRoot.findViewById(R.id.ItemList);
		
		editor = (ViewGroup) listRoot.findViewById(R.id.Editor);
		editor.setVisibility(View.INVISIBLE);
		titleEditor = (AutoCompleteTextView) listRoot.findViewById(R.id.ItemTitleEditor);
		qtyEditor = (EditText) listRoot.findViewById(R.id.QtyEditor);
		priceEditor = (EditText) listRoot.findViewById(R.id.PriceEditor);
		
		editorFloatingBackground = listRoot.findViewById(R.id.EditorFloatingBackground);
		titleCompletionHelper = listRoot.findViewById(R.id.TitleCompletionHelper);
		
		titleEditor.addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
				if (!editorVisible) return;
				if (editorTarget == InvalidTarget) return;
				if (!loopForSuggestion(s)) {
					if (editorMode == EditorModeAll) {
						// For new items, estimated price is commited at the end
						items.get(0).estimatedPrice = 0;
						priceEditor.setHint(getString(R.string.PriceEditHint));
					}
					else {
						// If there was previously an estimated price clear it from the estimated total
						// But only if there wasn't an explicit price set
						if (items.get(editorTarget).estimatedPrice != 0 && items.get(editorTarget).price == 0)
							activity.addToEstimatedTotal(items.get(editorTarget).qty, -items.get(editorTarget).estimatedPrice);
						items.get(editorTarget).estimatedPrice = 0;
						priceEditor.setHint(getString(R.string.PriceEditHint));
						cloneViewInEditor(editorTarget, true);
					}
				}
				showSuggestionsForView(titleEditor);
			}
			
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			@Override
			public void afterTextChanged(Editable s) {
			}
		});
		
		editorTitle = (TextView) editor.findViewById(R.id.ItemTitle);
		editorTitle.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				onPositionClicked(editorTarget);
				list.requestFocus();
				((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(editor.getWindowToken(), 0);
			}
		});
		editorPrice = (TextView) editor.findViewById(R.id.PriceTitle);
		editorPrice.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				swapEditorMode(EditorModePrice);
			}
		});
		editorQty = (TextView) editor.findViewById(R.id.QtyTitle);
		editorQty.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				swapEditorMode(EditorModeQty);
			}
		});
		editorStrikethrough = editor.findViewById(R.id.ItemStrikethrough);
		
		qtyEditor.setOnTouchListener(unitSelectorListener);
		
		list.setAdapter(adapter);
		list.setOnScrollListener(editorPositionKeeper);
		list.setEmptyView(inflater.inflate(R.layout.empty_hint, null));
		
		return listRoot;
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		activity = (ReceiptActivity) getActivity();
		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		minimumUnitSwipeDistance = metrics.density * 50;
		
		root = (ViewGroup) activity.getWindow().getDecorView();
		list.setScrollingCacheEnabled(true);
		
		scrollState = OnScrollListener.SCROLL_STATE_IDLE;
		
		if (items.size() == 0) {
			activity.findViewById(R.id.EmptyHint).setVisibility(View.VISIBLE);
		}
		else {
			activity.findViewById(R.id.EmptyHint).setVisibility(View.GONE);
		}
		
		if (editorVisible) {
			editor.setVisibility(View.VISIBLE);
			if (editorFocus != FocusedNone) {
				switch (editorFocus) {
				case FocusedTitle:
					titleEditor.requestFocus();
					break;
				case FocusedPrice:
					priceEditor.requestFocus();
					break;
				case FocusedQty:
					qtyEditor.requestFocus();
					break;
				}
			}
			setEditorMode(editorMode, true);
			// Cheap version of addOnLayoutChangeListener
			editor.addOnLayoutChangeListener(new OnLayoutChangeListener() {
				int layoutPasses = 0;
				public void onLayoutChange(View arg0, int arg1, int arg2,
						int arg3, int arg4, int arg5, int arg6, int arg7,
						int arg8) {
					layoutPasses++;
					if (layoutPasses == 2) 
						editor.removeOnLayoutChangeListener(this);
					else
						return;
					setEditorMode(editorMode, true);
					InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
					if (editorFocus != FocusedNone) 
						if (editorFocus != FocusedNone) {
							switch (editorFocus) {
							case FocusedTitle:
								imm.showSoftInput(titleEditor, InputMethodManager.SHOW_FORCED);
								titleEditor.requestFocus();
								break;
							case FocusedPrice:
								imm.showSoftInput(priceEditor, InputMethodManager.SHOW_FORCED);
								priceEditor.requestFocus();
								break;
							case FocusedQty:
								imm.showSoftInput(qtyEditor, InputMethodManager.SHOW_FORCED);
								qtyEditor.requestFocus();
								break;
							}
						}
				}
			});

			setEditorFocusListener(focusLossListener);
			
			if (editorMode == EditorModeAll) {
				setEditorListener(newItemListener);
				
				qtyEditor.setHint(items.get(0).unitOfMeasurement);
			}
			else {
				setEditorListener(editorListener);
				cloneViewInEditor(editorTarget);
				qtyEditor.setHint(items.get(editorTarget).unitOfMeasurement);
			}
		}
		
		if (selectionList.size() > 0)
			restoreActionMode();
		
		prepareTouchListener();
	}
	
	@Override
	public void onPause() {
		cancelAnchorAnimations();
		if (deleteAnimator != null)
			deleteAnimator.cancel();
		super.onPause();
	}
	
	
	
	@Override
	public void onDetach() {
		super.onDetach();
		activity = null;
		
		if (deleteAnimator != null) deleteAnimator.cancel();
		deleteAnimator = null;
		
		int holderSize = animators.size();
		for (int i = 0; i < holderSize; i++) {
			animators.get(0).animator.cancel();
			removeHolder(animators.get(0));
		}
		
		
		if (editorVisible) {
			editorFocus = FocusedNone;
			if (titleEditor.hasFocus())
				editorFocus = FocusedTitle;
			if (qtyEditor.hasFocus())
				editorFocus = FocusedQty;
			if (priceEditor.hasFocus())
				editorFocus = FocusedPrice;
		}
		
		actionMode = null;
		
		listRoot = null;
		list.setAdapter(null);
		list = null;
		
		titleEditor = null;
		qtyEditor = null;
		priceEditor = null;
		editorFloatingBackground = null;
		titleCompletionHelper = null;
		editorTitle = null;
		editorQty = null;
		editorPrice = null;
		editorStrikethrough = null;
		editor = null;
		
		root = null;
		
		detector = null;
		
	}
	
	public boolean editorVisible() {
		return editorVisible;
	}
	
	public int decode(int position) {
		final int Position = position;
		for (Integer i : anchors) {
			if (Position > i) position--;
		}
		return position;
	}
	
	public int decodeNonDeleted(int position) {
		if (DEBUG_ANCHORS) Log.d(TAG, "TranslateNonDeleted - confirmingPosition: " + confirmingPosition + "\nposition: " + position + "\nanchors: " + anchors);
		final int Position = position;
		boolean passedThroughConfirmingPosition = false;
		for (Integer i : anchors) {
			if (Position > i) 
				if (i == confirmingPosition && !passedThroughConfirmingPosition) {
					passedThroughConfirmingPosition = true;
				}
				else {
					position--;
				}
		}
		if (position >= items.size()) position = items.size() - 1;
		if (position < 0) position = 0;
		return position;
	}
	
	public int encode(int position) {
		final int Position = position;
		for (Integer i : anchors) {
			if (Position >= i) position++;
		}
		return position;
	}
	
	private class ItemListAdapter extends BaseAdapter {
		
		private boolean newZeroView;
		
		@Override
		public int getCount() {
			int size = items.size() + anchors.size();
			if (confirmingPosition != ListView.INVALID_POSITION)
				size -= 1;
			if (deleteAnimator != null)
				size += 1;
			return size;
		}

		@Override
		public Item getItem(int position) {
			if (anchors.contains(position) && position != confirmingPosition) return NullItem;
			if (deleteAnimator != null)
				return items.get(decodeNonDeleted(position - 1 < 0 ? 0 : position - 1));
			return items.get(decodeNonDeleted(position));
		}

		@Override
		public long getItemId(int position) {
			return position;
		}
		
		public void notifyDataSetChanged() {
			super.notifyDataSetChanged();
			if (list == null) return;
            try { // TODO
                if (items.size() == 0) {
                    activity.findViewById(R.id.EmptyHint).setVisibility(View.VISIBLE);
                }
                else {
                    activity.findViewById(R.id.EmptyHint).setVisibility(View.GONE);
                }
            }
            catch (Exception e) {}
		}
		
		public int getViewTypeCount() { return 2; }
		
		public int getItemViewType(int position) {
			if (position == 0 && deleteAnimator != null)
				return 1;
			return 0;
		}
		
		public boolean areAllItemsEnabled() {
			return false;
		}
		
		public boolean isEnabled(int position) {
			return false;
		}
		
		public void setNextViewToZeroHeight() {
			newZeroView = true;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup container) {
			
			View view = convertView;
			ViewHolder holder;
			
			if (newZeroView && position == 0) {
				newZeroView = false;
				view = activity.getLayoutInflater().inflate(R.layout.layout_list_item, container, false);
				LayoutParams params = view.getLayoutParams();
				params.height = 5;
				view.setLayoutParams(params);
				holder = new ViewHolder();
				holder.title = (TextView)view.findViewById(R.id.ItemTitle);
				holder.qty = (TextView)view.findViewById(R.id.QtyTitle);
				holder.price = (TextView)view.findViewById(R.id.PriceTitle);
				holder.strikethrough = view.findViewById(R.id.ItemStrikethrough);
				holder.itemRoot = view.findViewById(R.id.ItemRoot);
				holder.deleteRoot = view.findViewById(R.id.DeleteStrip);
				holder.qty.setOnClickListener(qtyEditListener);
				holder.price.setOnClickListener(priceEditListener);
				view.setTag(holder);
				holder.id = position;
				view.setVisibility(View.INVISIBLE);
				
				view.setOnClickListener(itemClickListener);
				view.setOnLongClickListener(itemLongClickListener);
				view.setOnTouchListener(new DeleteTouchListener());
				
				return view;
			}
			
			if (convertView == null) {
				view = activity.getLayoutInflater().inflate(R.layout.layout_list_item, container, false);
				holder = new ViewHolder();
				holder.title = (TextView)view.findViewById(R.id.ItemTitle);
				holder.qty = (TextView)view.findViewById(R.id.QtyTitle);
				holder.price = (TextView)view.findViewById(R.id.PriceTitle);
				holder.strikethrough = view.findViewById(R.id.ItemStrikethrough);
				
				holder.itemRoot = view.findViewById(R.id.ItemRoot);
				holder.deleteRoot = view.findViewById(R.id.DeleteStrip);
				
				view.setOnClickListener(itemClickListener);
				view.setOnLongClickListener(itemLongClickListener);
				view.setOnTouchListener(new DeleteTouchListener());
				view.setTag(holder);

			}
			else
				holder = (ViewHolder) view.getTag();
			
			if (holder.animator != null) {
				holder.animator.cancel();
				holder.animator = null;
			}
			
			if (anchors.contains(position) && confirmingPosition != ListView.INVALID_POSITION) {
				decorateDeletedView(holder);
			}
			else
				restoreDeletedView(view);
			
			holder.id = decode(position);
			
			Item item = null;
			try {
				item = getItem(position);
			}
			catch (IndexOutOfBoundsException e) {
				if (anchors.contains(Integer.valueOf(position)))
					return view;
				else
					throw e;
			}
			
			if (anchors.contains(position) && confirmingPosition == ListView.INVALID_POSITION) {
				view.getLayoutParams().height = 2;
				view.requestLayout();
			}
			else {
				view.getLayoutParams().height = (int) (48 * metrics.density);
				view.requestLayout();
			}
			
			if (deleteAnimator != null)
				position--;
				
			if (position < 0) position = 0;
			
			if (item.flags == SetNone)
				view.setVisibility(View.INVISIBLE);
			else
				view.setVisibility(View.VISIBLE);
			
			holder.title.setText(item.name);
			if ((item.flags & SetQty) == 0) {
				holder.qty.setText("1.0" + item.unitOfMeasurement);
				holder.qty.setTextColor(getResources().getColor(R.color.implicit_text_colors));
			}
			else {
				holder.qty.setText(ReceiptActivity.quantityFormattedString(activity, item.qty, item.unitOfMeasurement));
				holder.qty.setTextColor(getResources().getColor(android.R.color.black));
			}
			if ((item.flags & SetPrice) == 0) {
				if (item.estimatedPrice == 0)
					holder.price.setText(ReceiptActivity.currentLocale);
				else
					holder.price.setText(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(item.estimatedPrice));
				holder.price.setTextColor(getResources().getColor(R.color.implicit_text_colors));
			}
			else {
				holder.price.setText(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(item.price));
				holder.price.setTextColor(getResources().getColor(android.R.color.black));
			}
			
			if (item.crossedOff) {
				holder.title.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
				holder.qty.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
				holder.price.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
				holder.strikethrough.setVisibility(View.VISIBLE);
			}
			else {
				holder.title.setTextColor(getResources().getColor(android.R.color.black));
				holder.strikethrough.setVisibility(View.GONE);
			}
			
			if (selectionList.size() > 0) {
                view.findViewById(R.id.ItemTags).setOnClickListener(null);
				holder.qty.setOnClickListener(null);
				holder.price.setOnClickListener(null);
				holder.qty.setClickable(false);
				holder.price.setClickable(false);
			}
			else {
                view.findViewById(R.id.ItemTags).setOnClickListener(tagEditListener);
				holder.qty.setOnClickListener(qtyEditListener);
				holder.price.setOnClickListener(priceEditListener);
				holder.qty.setClickable(true);
				holder.price.setClickable(true);
			}
			
			if (item.selected) {
				view.setBackgroundResource(R.drawable.selected_scrap);
			}
			else {
				view.setBackgroundResource(R.drawable.unselected_scrap);
			}
			
			if (editorTarget == position) {
				view.setVisibility(View.INVISIBLE);
			}
			else {
				view.setVisibility(View.VISIBLE);
			}
			
			return view;
		}
		
	}
	
	public ArrayList<Item> discardItems() {
		final ArrayList<Item> ReturnValue = items;
		items = new ArrayList<Item>();
		adapter.notifyDataSetChanged();
		
		if (editorVisible) {
			editorVisible = false;
			editorTarget = InvalidTarget;
			setEditorFocusListener(null);
			editor.setVisibility(View.INVISIBLE);
		}
		
		return ReturnValue;
	}
	
	public PartialCheckoutItems deleteCrossedOffItems() {
		ArrayList<Item> crossedOffItems = new ArrayList<Item>();
		ArrayList<Integer> positions = new ArrayList<Integer>();
		int i = 0;
		for (Item item : items) {
			if (item.crossedOff == true) {
				crossedOffItems.add(item);
				positions.add(i);
				activity.fastAddToTotal(item.qty, -(item.price == 0 ? item.estimatedPrice : item.price));
				activity.fastAddToEstimatedTotal(item.qty, -(item.price == 0 ? item.estimatedPrice : item.price));
			}
			i++;
		}
		items.removeAll(crossedOffItems);
		activity.addToCrossedOffCount(-crossedOffItems.size());
		activity.addToItemCount(-crossedOffItems.size());
		activity.addToTotal(0, 0);
		activity.addToEstimatedTotal(0, 0);
		adapter.notifyDataSetChanged();
		PartialCheckoutItems checkoutItems = new PartialCheckoutItems();
		checkoutItems.items = crossedOffItems;
		checkoutItems.positions = positions;
		return checkoutItems;
	}
	
	public void restoreCrossedOffItems(PartialCheckoutItems checkoutItems) {
		int checkoutSize = checkoutItems.items.size();
		for (int i = 0; i < checkoutSize; i++) {
			Item item = checkoutItems.items.get(i);
			items.add(checkoutItems.positions.get(i), item);
			activity.fastAddToTotal(item.qty, (item.price == 0 ? item.estimatedPrice : item.price));
			activity.fastAddToEstimatedTotal(item.qty, (item.price == 0 ? item.estimatedPrice : item.price));
		}
		activity.addToItemCount(checkoutSize);
		activity.addToCrossedOffCount(checkoutSize);
		activity.addToTotal(0, 0);
		activity.addToEstimatedTotal(0, 0);
		adapter.notifyDataSetChanged();
	}
	
	private OnScrollListener editorPositionKeeper = new OnScrollListener() {
		
		@Override
		public void onScroll(AbsListView list, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
				moveEditor(list, firstVisibleItem, visibleItemCount, totalItemCount);
		}
		@Override
		public void onScrollStateChanged(AbsListView list, int state) {
			scrollState = state;
			
			if (confirmingPosition != ListView.INVALID_POSITION)
				confirmDeletion();
			
		}
		
	};
	
	public void moveEditor(AbsListView list, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		
		if (!editorVisible)
			return;
		
		final int editorTarget = deleteAnimator != null ? encode(this.editorTarget + 1) : encode(this.editorTarget);
		
		if (editorTarget < firstVisibleItem) {
			editor.setY(0);
			editorFloatingBackground.setAlpha(1);
			return;
		}
		
		if (editorTarget > list.getLastVisiblePosition()) {
			editor.setY(listRoot.getHeight() - editor.getHeight());
			editorFloatingBackground.setAlpha(1);
			return;
		}
		
		float scrollDifference = 0f;
		float maxTransparentScroll = 48 * metrics.density;
		int bottomScroll = listRoot.getHeight() - editor.getHeight();
		int editorY = list.getChildAt(editorTarget - firstVisibleItem).getTop() - list.getScrollY();
		if (editorY < 0) {
			scrollDifference = ((float)Math.abs(editorY))/maxTransparentScroll;
			editorY = 0;
		}
		if (editorY > bottomScroll) {
			scrollDifference = ((float)(editorY - bottomScroll))/maxTransparentScroll;
			editorY = bottomScroll;
		}
		if (DEBUG_EDITOR_POSITION) Log.d(TAG, "ScrollDifference is " + scrollDifference + ", bottomScroll is " + bottomScroll + ", maxTransparentScroll is " + maxTransparentScroll);
		editorFloatingBackground.setAlpha(scrollDifference);
		editor.setY(editorY);
		
	}
	
	public void layoutEditor(AbsListView list, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
		
		if (!editorVisible)
			return;

		final int editorTarget = deleteAnimator != null ? encode(this.editorTarget + 1) : encode(this.editorTarget);
		
		if (editorTarget < firstVisibleItem) {
			FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) editor.getLayoutParams();
			params.topMargin = 0;
			editor.setLayoutParams(params);
			editor.setTop(0);
			editorFloatingBackground.setAlpha(1);
			return;
		}
		
		if (editorTarget > list.getLastVisiblePosition()) {
			FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) editor.getLayoutParams();
			params.topMargin = listRoot.getHeight() - editor.getHeight();
			editor.setLayoutParams(params);
			editor.setTop(listRoot.getHeight() - editor.getHeight());
			editorFloatingBackground.setAlpha(1);
			return;
		}
		
		float scrollDifference = 0f;
		float maxTransparentScroll = 48 * metrics.density;
		int bottomScroll = listRoot.getHeight() - editor.getHeight();
		int editorY = list.getChildAt(editorTarget - firstVisibleItem).getTop() - list.getScrollY();
		if (editorY < 0) {
			scrollDifference = ((float)Math.abs(editorY))/maxTransparentScroll;
			editorY = 0;
		}
		if (editorY > bottomScroll) {
			scrollDifference = ((float)(editorY - bottomScroll))/maxTransparentScroll;
			editorY = bottomScroll;
		}
		if (DEBUG_EDITOR_POSITION) Log.d(TAG, "ScrollDifference is " + scrollDifference + ", bottomScroll is " + bottomScroll + ", maxTransparentScroll is " + maxTransparentScroll);
		editorFloatingBackground.setAlpha(scrollDifference);
		FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) editor.getLayoutParams();
		params.setMargins(0, editorY, 0, 0);
		editor.setTop(editorY);
		editor.setLayoutParams(params);
		
		if (DEBUG_EDITOR_POSITION) Log.d(TAG, "TopMargin is: " + editorY + ", position is: " + editor.getTop());
		
	}
	
	private void setEditorMode(int mode) {
		setEditorMode(mode, false);
	}
	
	private void setEditorMode(int mode, boolean layout) {
		editorMode = mode;
		editor.animate().cancel();
		editor.setAlpha(1);
		if (mode == EditorModeAll) {
			titleEditor.setVisibility(View.VISIBLE);
			priceEditor.setVisibility(View.VISIBLE);
			qtyEditor.setVisibility(View.VISIBLE);
			editorTitle.setVisibility(View.INVISIBLE);
			editorPrice.setVisibility(View.INVISIBLE);
			editorQty.setVisibility(View.INVISIBLE);
			editorStrikethrough.setVisibility(View.INVISIBLE);
		}
		else {
			
			int titleMode = View.INVISIBLE;
			int priceMode = View.INVISIBLE;
			int qtyMode = View.INVISIBLE;
			
			if (mode == EditorModePrice) priceMode = View.VISIBLE;
			if (mode == EditorModeQty) qtyMode = View.VISIBLE;
			if (mode == EditorModeTitle) titleMode = View.VISIBLE;
			
			titleEditor.setVisibility(titleMode);
			priceEditor.setVisibility(priceMode);
			qtyEditor.setVisibility(qtyMode);
			editorTitle.setVisibility(titleMode ^ View.INVISIBLE);
			editorPrice.setVisibility(priceMode ^ View.INVISIBLE);
			editorQty.setVisibility(qtyMode ^ View.INVISIBLE);
			
		}
		if (layout)
			layoutEditor(list, list.getFirstVisiblePosition(), list.getLastVisiblePosition() - list.getFirstVisiblePosition(), items.size());
		else
			moveEditor(list, list.getFirstVisiblePosition(), list.getLastVisiblePosition() - list.getFirstVisiblePosition(), items.size());
	}
	
	public void addNewItemToList() {
		
		if (selectionList.size() > 0 && actionMode != null)
			actionMode.finish();
		
		if (confirmingPosition != ListView.INVALID_POSITION)
			confirmDeletion();
		
		boolean zeroIsAnchor = anchors.contains(Integer.valueOf(0));
		
		cancelAnchorAnimations();
		
		if (editorVisible) {
			if (editorMode == EditorModeAll) {
				String title = titleEditor.getText().toString().trim();
				if (title.isEmpty()) {
					titleCompletionHelper.setVisibility(View.VISIBLE);
					titleCompletionHelper.setAlpha(0);
					titleCompletionHelper.animate()
						.alpha(1)
						.setInterpolator(new CycleInterpolator(2))
						.setDuration(300)
						.setListener(new AnimatorListenerAdapter() {
							public void onAnimationEnd(Animator a) {
								if (activity == null) return;
								titleCompletionHelper.setVisibility(View.INVISIBLE);
							}
						});
					return;
				}
				else
					finishAddingItem(true);
			}
			else
				commitChanges(true);
		}
		
		delayHandler.delayIndefinitely();
		
//		activity.hideHint();
		
		editorVisible = true;
		editorTarget = NewItemTarget;
		setEditorMode(EditorModeAll);
		
		Item newItem = new Item();
		newItem.flags = SetNone;
		newItem.unitOfMeasurement = getString(R.string.Count);
		items.add(0, newItem);
		if (list.getFirstVisiblePosition() == 0)
			if (!zeroIsAnchor) adapter.setNextViewToZeroHeight();
		adapter.notifyDataSetChanged();
		
		if (list.getFirstVisiblePosition() == 0) {
			if (deleteAnimator != null) deleteAnimator.cancel();
			list.addOnLayoutChangeListener(new OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View v, int left, int top, int right,
						int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
					list.removeOnLayoutChangeListener(this);
					final View animatedView = list.getChildAt(0);
					ViewCompat.setHasTransientState(animatedView, true);
					ValueAnimator animator = ObjectAnimator.ofInt(5, (int)(48*metrics.density));
					animator.setDuration(LayoutAnimationDuration);
					animator.setInterpolator(new DecelerateInterpolator(2));
					animator.addUpdateListener(new AnimatorUpdateListener() {
						@Override
						public void onAnimationUpdate(ValueAnimator animator) {
							LayoutParams params = animatedView.getLayoutParams();
							params.height = (Integer)animator.getAnimatedValue();
							animatedView.setLayoutParams(params);
						}
					});
					animator.addListener(new AnimatorListenerAdapter() {
						public void onAnimationEnd(Animator a) {
							LayoutParams params = animatedView.getLayoutParams();
							params.height = (int)(48 * metrics.density);
							animatedView.setLayoutParams(params);
							ViewCompat.setHasTransientState(animatedView, false);
							((ViewHolder)animatedView.getTag()).animator = null;
							adapter.notifyDataSetChanged();
						}
					});
					((ViewHolder)animatedView.getTag()).animator = animator;
					animator.start();
				}
			});
		}
		//list.smoothScrollToPosition(0);
		
		editor.setVisibility(View.VISIBLE);
		editor.setAlpha(0);
		editor.setY(-(int)(48 * metrics.density));
		editor.animate()
			.alpha(1)
			.yBy((int)(48 * metrics.density))
			.setDuration(LayoutAnimationDuration)
			.setListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator a) {
					if (activity == null || !editorVisible) return;
					InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
					titleEditor.requestFocus();
					imm.showSoftInput(titleEditor, InputMethodManager.SHOW_FORCED);
				}
			});
		
		setEditorListener(newItemListener);

		setEditorFocusListener(focusLossListener);
		
		titleEditor.setText(null);
		priceEditor.setText(null);
		qtyEditor.setText(null);
		
		qtyEditor.setHint(items.get(0).unitOfMeasurement);
		priceEditor.setHint(getString(R.string.PriceEditHint));
		
	}

	public void finishAddingItem(final boolean retainKeyboard) {

		setEditorFocusListener(null);
		delayHandler.redelay(ReorderDelay);
		
		final Item item = items.get(0);
		String title = titleEditor.getText().toString().trim();
		if (title.isEmpty()) {

			editorTarget = InvalidTarget;
			item.invalidated = true;
			
			if (list.getFirstVisiblePosition() != 0) {
				items.remove(item);
				adapter.notifyDataSetChanged();
				editorTarget -= 1;
				
				editorVisible = false;
				editor.animate()
					.alpha(0)
					.setDuration(LayoutAnimationDuration)
					.setInterpolator(new DecelerateInterpolator(2))
					.setListener(new AnimatorListenerAdapter() {
						public void onAnimationEnd(Animator a) {
							if (activity == null) return;
							if (!editorVisible) {
								if (!retainKeyboard) {
									InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
									imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
								}
								editor.setVisibility(View.INVISIBLE);
							}
						}
					});
			}
			else {
				final View animatedView = list.getChildAt(0);
				ViewCompat.setHasTransientState(animatedView, true);
				
				items.remove(item);
				editorTarget -= 1;
				
				if (deleteAnimator != null) deleteAnimator.cancel();
				
				deleteAnimator = ObjectAnimator.ofInt((int)(48*metrics.density), 4);
				deleteAnimator.setDuration(LayoutAnimationDuration);
				deleteAnimator.setInterpolator(new DecelerateInterpolator(2));
				deleteAnimator.addUpdateListener(new AnimatorUpdateListener() {
					@Override
					public void onAnimationUpdate(ValueAnimator animator) {
						LayoutParams params = animatedView.getLayoutParams();
						params.height = (Integer)animator.getAnimatedValue();
						animatedView.setLayoutParams(params);
					}
				});
				deleteAnimator.addListener(new AnimatorListenerAdapter() {
					public void onAnimationEnd(Animator a) {
						ViewCompat.setHasTransientState(animatedView, false);
						((ViewHolder)animatedView.getTag()).animator = null;
						if (activity != null) adapter.notifyDataSetChanged();
						LayoutParams params = animatedView.getLayoutParams();
						params.height = (int)(48*metrics.density);
						animatedView.setLayoutParams(params); 
						deleteAnimator = null;
					}
				});
				((ViewHolder)animatedView.getTag()).animator = deleteAnimator;
				deleteAnimator.start();
				
				editorVisible = false;
				editor.animate()
					.alpha(0)
					.yBy(-editor.getHeight())
					.setDuration(LayoutAnimationDuration)
					.setInterpolator(new DecelerateInterpolator(2))
					.setListener(new AnimatorListenerAdapter() {
						public void onAnimationEnd(Animator a) {
							if (activity == null) return;
							if (!editorVisible) {
								if (!retainKeyboard) {
									InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
									imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
								}
								editor.setVisibility(View.INVISIBLE);
							}
						}
					});
			}
			
			return;
		}
		item.name = title;
		item.flags |= SetTitle;
		activity.addToItemCount(1);
		
		editorTarget = InvalidTarget;

		editorVisible = false;
		
		if (list.getFirstVisiblePosition() != 0) {
			if (!retainKeyboard) {
				editor.animate()
					.alpha(0)
					.yBy(-editor.getHeight())
					.setDuration(LayoutAnimationDuration)
					.setInterpolator(new DecelerateInterpolator(2))
					.setListener(new AnimatorListenerAdapter() {
						public void onAnimationEnd(Animator a) {
							if (activity == null) return;
							if (!editorVisible) {
								editor.setVisibility(View.INVISIBLE);
								if (!retainKeyboard) {
									InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
									imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
									editor.setVisibility(View.INVISIBLE);
								}
							}
						}
					});
			}
		}
		else if (!retainKeyboard) {
			InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(editor.getWindowToken(), 0);
			editor.setVisibility(View.INVISIBLE);
		}
		
		long qty;
		try {
			qty = new BigDecimal(qtyEditor.getText().toString()).movePointRight(4).longValue();
			if (qty > 9999999) //That is to say 999.9999
				qty = 9999999;
		}
		catch (NumberFormatException exception) {
			qty = 0;
		}
		if (qty != 0)
			item.flags |= SetQty;
		item.qty = qty;
		
		long price;
		try {
			price = new BigDecimal(priceEditor.getText().toString()).movePointRight(2).longValue();
		}
		catch (NumberFormatException exception) {
			price = 0;
		}
		if (price != 0) {
			item.flags |= SetPrice;
			if (PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("autoCross", true)) {
				delayHandler.registerCallback(ReorderRunnable, ReorderDelay);
				item.crossedOff = true;
				activity.addToCrossedOffCount(1);
				activity.addToTotal(qty, price);
			}
		}
		item.price = price;
		
		if (item.price != 0)
			activity.addToEstimatedTotal(item.qty, item.price);
		else
			activity.addToEstimatedTotal(item.qty, item.estimatedPrice);
		
		adapter.notifyDataSetChanged();
		
	}

	private OnEditorActionListener newItemListener = new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView view, int keyCode, KeyEvent event) {
			
			if (keyCode == EditorInfo.IME_ACTION_DONE) {
				//focus change may occur after the done key is pressed
				//this can cause issues, so it's best to remove the listener
				//since this listener can potentially change the focus of either three fields
				//all three should be cleared
				setEditorFocusListener(null);
				
				//If the user double-taps the done button, this causes the finishAddingItem(boolean)
				//method to be called twice, resulting in the first item getting deleted
				//and the number of items going out of sync with the activity
				setEditorListener(null);
				
				finishAddingItem(false);
				return true;
			}
			
			return false;
		}
	};
	
	private OnEditorActionListener editorListener = new OnEditorActionListener() {
		@Override
		public boolean onEditorAction(TextView view, int keyCode, KeyEvent event) {
			
			if (keyCode == EditorInfo.IME_ACTION_DONE) {
				//focus change may occur after the done key is pressed
				//this can cause issues, so it's best to remove the listener
				setEditorFocusListener(null);
				setEditorListener(null);
				
				//hide keyboard
				InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
				
				commitChanges();
				
				return true;
			}
			
			return false;
		}
	};
	
	private OnFocusChangeListener focusLossListener = new OnFocusChangeListener() {
		@Override
		public void onFocusChange(View view, boolean hasFocus) {
			if (hasFocus == false) {
				if (editorMode == EditorModeAll) {
					if (!(titleEditor.hasFocus() || qtyEditor.hasFocus() || priceEditor.hasFocus()))
						finishAddingItem(true);
				}
				else { 
					commitChanges();
				}
			}
			
		}
	};
	
	private OnFocusChangeListener titleFocusLossListener = new OnFocusChangeListener() {
		@Override
		public void onFocusChange(View view, boolean hasFocus) {
			if (hasFocus == false) {
				if (DEBUG_TITLE_FOCUS) Log.d(TAG, "Title has lost focus on item " + editorTarget);
				if (editorMode == EditorModeAll) {
					if (!(titleEditor.hasFocus() || qtyEditor.hasFocus() || priceEditor.hasFocus()))
						finishAddingItem(true);
				}
				else { 
					commitChanges();
				}
			}
			
		}
	};
	
	private void setEditorListener(OnEditorActionListener listener) {
		titleEditor.setOnEditorActionListener(listener);
		priceEditor.setOnEditorActionListener(listener);
		qtyEditor.setOnEditorActionListener(listener);
	}
	
	private void setEditorFocusListener(OnFocusChangeListener listener) {
		
		if (listener == focusLossListener)
			titleEditor.setOnFocusChangeListener(titleFocusLossListener);
		else
			titleEditor.setOnFocusChangeListener(listener);
		priceEditor.setOnFocusChangeListener(listener);
		qtyEditor.setOnFocusChangeListener(listener);
		
	}
	
	private void swapEditorMode(int mode) {
		applyChanges();
		setEditorMode(mode);
		cloneViewInEditor(editorTarget, true);
		if (mode == EditorModePrice) {
			priceEditor.requestFocus();
		}
		if (mode == EditorModeQty) {
			qtyEditor.requestFocus();
		}
		if (mode == EditorModeTitle) {
			titleEditor.requestFocus();
		}
		setEditorFocusListener(focusLossListener);
	}

    private OnClickListener tagEditListener = new OnClickListener() {
        @Override
        public void onClick(View view) {
            final int ConfirmingPosition = confirmingPosition;

            if (confirmingPosition != ListView.INVALID_POSITION) {
                confirmDeletion();
            }

            boolean listViewHack = commitEditor(true) || (deleteAnimator != null && anchors.size() == 0);

            if (currentExpander != null) currentExpander.compact();
            currentExpander = TagExpander.fromViewInContainer((TagView) view, (ViewGroup) view.getParent());
            currentExpander.expand();
            currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                @Override
                public void onClose() {
                    currentExpander = null;
                }
            });
        }
    };
	
	private OnClickListener qtyEditListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			final int ConfirmingPosition = confirmingPosition;
			
			if (confirmingPosition != ListView.INVALID_POSITION) {
				confirmDeletion();
			}
			
			boolean listViewHack = commitEditor(true) || (deleteAnimator != null && anchors.size() == 0);
			
			delayHandler.delayIndefinitely();
			
			View viewParent = ((View)view.getParent().getParent());
			viewParent.setVisibility(View.INVISIBLE);
			
			ViewHolder holder = (ViewHolder)(viewParent).getTag();
			cloneViewInEditor(viewParent);
			
			if (ConfirmingPosition != ListView.INVALID_POSITION && ConfirmingPosition > holder.id)
				listViewHack = false;
			
			if (!listViewHack) {
				if (items.get(holder.id).qty != 0)
					qtyEditor.setText(new BigDecimal(items.get(holder.id).qty).movePointLeft(4).stripTrailingZeros().toPlainString());
				else
					qtyEditor.setText(null);
				qtyEditor.setHint(items.get(holder.id).unitOfMeasurement);
			}
			else {
				if (items.get(holder.id - 1).qty != 0)
					qtyEditor.setText(new BigDecimal(items.get(holder.id - 1).qty).movePointLeft(4).stripTrailingZeros().toPlainString());
				else
					qtyEditor.setText(null);
				qtyEditor.setHint(items.get(holder.id - 1).unitOfMeasurement);
			}
			
			if (DEBUG_SELECTION) Log.d(TAG, "ListViewHack is: " + listViewHack + "; tracking position: " + holder.id);
			
			editorVisible = true;
			editorTarget = holder.id;
			if (DEBUG_SELECTION) Log.d(TAG, "editorTarget is: " + list.getPositionForView(viewParent));
			if (listViewHack)
				editorTarget -= 1;
			editor.setVisibility(View.VISIBLE);
			setEditorMode(EditorModeQty, true);
			viewParent.setVisibility(View.INVISIBLE);
			setEditorListener(editorListener);
			setEditorFocusListener(focusLossListener);
			
			qtyEditor.requestFocus();
			qtyEditor.selectAll();
			InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(qtyEditor, InputMethodManager.SHOW_FORCED);
		}
	};
	
	private OnClickListener priceEditListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			final int ConfirmingPosition = confirmingPosition;
			
			if (confirmingPosition != ListView.INVALID_POSITION)
				confirmDeletion();

			boolean listViewHack = commitEditor(true) || (deleteAnimator != null && anchors.size() == 0);
			
			delayHandler.delayIndefinitely();
			
			View viewParent = ((View)view.getParent().getParent());
			
			ViewHolder holder = (ViewHolder)viewParent.getTag();
			cloneViewInEditor(viewParent);
			
			if (ConfirmingPosition != ListView.INVALID_POSITION && ConfirmingPosition > holder.id)
				listViewHack = false;
			
			if (!listViewHack) {
				if (items.get(holder.id).price != 0)
					priceEditor.setText(ReceiptActivity.longToDecimalString(items.get(holder.id).price));
				else
					priceEditor.setText(null);
				
				if (items.get(holder.id).estimatedPrice != 0)
					priceEditor.setHint(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(items.get(holder.id).estimatedPrice));
				else
					priceEditor.setHint(R.string.PriceEditHint);
				
			}
			else {
				if (items.get(holder.id - 1).price != 0)
					priceEditor.setText(ReceiptActivity.longToDecimalString(items.get(holder.id - 1).price));
				else
					priceEditor.setText(null);
				
				if (items.get(holder.id - 1).estimatedPrice != 0)
					priceEditor.setHint(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(items.get(holder.id - 1).estimatedPrice));
				else
					priceEditor.setHint(R.string.PriceEditHint);
			}
			
			editorVisible = true;
			editorTarget = holder.id;
			if (listViewHack)
				editorTarget -= 1;
			editor.setVisibility(View.VISIBLE);
			setEditorMode(EditorModePrice, true);
			viewParent.setVisibility(View.INVISIBLE);
			setEditorListener(editorListener);
			setEditorFocusListener(focusLossListener);
			
			priceEditor.requestFocus();
			priceEditor.selectAll();
			InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(priceEditor, InputMethodManager.SHOW_FORCED);
		}
	};
	
	private OnClickListener itemClickListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
			
			boolean listViewHack = commitEditor(false) || (deleteAnimator != null && anchors.size() == 0);
			
			InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
			
			ViewHolder holder = (ViewHolder)view.getTag();
			
			int position = listViewHack ? holder.id - 1 : holder.id;
			if (selectionList.size() == 0)
				onPositionClicked(position);
			else
				toggleSelectionForPosition(position);
		}
	};
	
	private void onPositionClicked(int position) {
		
		if (confirmingPosition != ListView.INVALID_POSITION)
			confirmDeletion();
		
		delayHandler.registerCallback(ReorderRunnable, ReorderDelay);

		Item item = items.get(position);
		
		if (DEBUG_PRICES) Log.d(TAG, "ActionItem at " + position + " has qty " + item.qty + ", price " + item.price + " and estimated price " + item.estimatedPrice);
		
		if (item.crossedOff) {
			item.crossedOff = false;
			activity.addToCrossedOffCount(-1);
			if (item.price == 0)
				activity.addToTotal(item.qty, -item.estimatedPrice);
			else
				activity.addToTotal(item.qty, -item.price);
		}
		else {
			item.crossedOff = true;
			activity.addToCrossedOffCount(1);
			if (item.price == 0)
				activity.addToTotal(item.qty, item.estimatedPrice);
			else
				activity.addToTotal(item.qty, item.price);
		}
		
		adapter.notifyDataSetChanged();
		
	}
	
	// The NullOnClickListener eats up click events
	final OnClickListener NullOnClickListener = new OnClickListener() {
		@Override
		public void onClick(View v) {}
	};
	
	private OnLongClickListener itemLongClickListener = new OnLongClickListener() {
		@Override
		public boolean onLongClick(View view) {
			boolean listViewHack = commitEditor(false) || (deleteAnimator != null && anchors.size() == 0);
			
			delayHandler.delayIndefinitely();
			
			InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
			
			ViewHolder holder = (ViewHolder)view.getTag();
			
			if (DEBUG_SELECTION) Log.d(TAG, "ListViewHack is " + listViewHack + "; position is " + holder.id);
			int position = listViewHack ? holder.id - 1 : holder.id;
			if (activity.canEnterActionMode())
				toggleSelectionForPosition(position);
			return true;
		}
	};
	
	private View getPositionIfVisible(int position) {
		
		return list.getChildAt(position - list.getFirstVisiblePosition());
		
	}
	
	private void cloneViewInEditor(int position) {
		cloneViewInEditor(position, false);
	}
	
	private void cloneViewInEditor(int position, boolean manual) {
		
		View view = getPositionIfVisible(position);

		Item item = items.get(position);
		
		// There is no need to set-up the title edit box here, because it can only be accessed
		// during selection via long-press > edit
		
		// *** QTY EDIT BOX ***
		if (item.qty != 0)
			qtyEditor.setText(new BigDecimal(item.qty).movePointLeft(4).stripTrailingZeros().toPlainString());
		else
			qtyEditor.setText(null);
		qtyEditor.setHint(item.unitOfMeasurement);

		// *** PRICE EDIT BOX ***
		if (item.price != 0)
			priceEditor.setText(ReceiptActivity.longToDecimalString(item.price));
		else
			priceEditor.setText(null);
		
		if (item.estimatedPrice != 0)
			priceEditor.setHint(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(item.estimatedPrice));
		else
			priceEditor.setHint(R.string.PriceEditHint);
		
		// *** STATIC TEXTVIEWS ***
		if (view != null && !manual) {
			cloneViewInEditor(view);
		}
		else {
			editorTitle.setText(item.name);
			if ((item.flags & SetQty) == 0) {
				editorQty.setText("1.0" + item.unitOfMeasurement);
				editorQty.setTextColor(getResources().getColor(R.color.implicit_text_colors));
			}
			else {
				editorQty.setText(ReceiptActivity.quantityFormattedString(activity, item.qty, item.unitOfMeasurement));
				editorQty.setTextColor(getResources().getColor(android.R.color.black));
			}
			if ((item.flags & SetPrice) == 0) {
				if (item.estimatedPrice == 0)
					editorPrice.setText(ReceiptActivity.currentLocale);
				else
					editorPrice.setText(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(item.estimatedPrice));
				editorPrice.setTextColor(getResources().getColor(R.color.implicit_text_colors));
			}
			else {
				editorPrice.setText(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(item.price));
				editorPrice.setTextColor(getResources().getColor(android.R.color.black));
			}
			
			if (item.crossedOff) {
				editorTitle.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
				editorQty.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
				editorPrice.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
				editorStrikethrough.setVisibility(View.VISIBLE);
			}
			else {
				editorTitle.setTextColor(getResources().getColor(android.R.color.black));
				editorStrikethrough.setVisibility(View.GONE);
			}
		}
	}
	
	private void cloneViewInEditor(View view) {
		ViewHolder holder = (ViewHolder) view.getTag();
		editorTitle.setText(holder.title.getText());
		editorTitle.setTextColor(holder.title.getTextColors());
		
		editorQty.setText(holder.qty.getText());
		editorQty.setTextColor(holder.qty.getTextColors());
		
		editorPrice.setTextColor(holder.price.getTextColors());
		editorPrice.setText(holder.price.getText());
		
		editorStrikethrough.setVisibility(holder.strikethrough.getVisibility());
	}
	
	public boolean commitEditor() {
		return commitEditor(false);
	}
	
	public boolean commitEditor(boolean retainKeyboard) {

        if (currentExpander != null) {
            currentExpander.compact();
        }
		
		int startingSize = items.size();
		boolean listViewHack = false;
		
		if (editorVisible) {
			if (editorMode == EditorModeAll) {
				finishAddingItem(retainKeyboard);
				if (items.size() != startingSize) listViewHack = true;
			}
			else
				commitChanges(retainKeyboard);
		}
		
		if (DEBUG_SELECTION) Log.d(TAG, "Starting size is: " + startingSize + "; current size is: " + items.size() + "; listViewHack is: " + listViewHack);
		
		delayHandler.redelay(ReorderDelay);
		
		return listViewHack;
		
	}
	
	public boolean commitEditorInstantly() {

        if (currentExpander != null) {
            currentExpander.destroy();
        }
		
		if (confirmingPosition != ListView.INVALID_POSITION)
			confirmDeletionInstantly();
		
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(list.getWindowToken(), 0);
		
		int startingSize = items.size();
		boolean listViewHack = false;
		
		if (editorVisible) {
			if (editorMode == EditorModeAll) {
				final Item item = items.get(0);
				String title = titleEditor.getText().toString().trim();
				if (title.isEmpty() && list.getFirstVisiblePosition() == 0) {
						editorTarget = InvalidTarget;
						setEditorFocusListener(null);
						editor.setVisibility(View.INVISIBLE);
						Animator animator = ((ViewHolder) list.getChildAt(0).getTag()).animator;
						if (animator != null) animator.cancel();
						items.remove(item);
						adapter.notifyDataSetChanged();
						editorTarget -= 1;
						editorVisible = false;
				}
				else
					finishAddingItem(false);
				if (items.size() != startingSize) listViewHack = true;
			}
			else
				commitChanges(false);
		}
		else if (items.size() > 0) {
			if (items.get(0).invalidated = true) {
				if (deleteAnimator != null) deleteAnimator.cancel();
			}
		}
		
		cancelAnchorAnimations();
		
		delayHandler.unregisterCallback();
		InstantReorderRunnable.run();
		
		return listViewHack;
	}
	
	protected void commitChanges() {
		commitChanges(false);
	}
	
	protected void applyChanges() {
		setEditorFocusListener(null);
		
		if (DEBUG_SELECTION) Log.d(TAG, "Applying changes for target: " + editorTarget);
		
		if (editorMode == EditorModeQty) {
			final Item item = items.get(editorTarget);
			final long oldQty = item.qty;
			long qty;
			try {
				qty = new BigDecimal(qtyEditor.getText().toString()).movePointRight(4).longValue();
				if (qty > 9999999) //That is to say, 999.9999
					qty = 9999999;
			}
			catch (NumberFormatException exception) {
				qty = 0;
			}
			if (qty != 0)
				item.flags |= SetQty;
			else
				item.flags &= ~SetQty;
			item.qty = qty;
			
			long qtyDifference = qty - oldQty;
			if (qty == 0) qtyDifference += 10000;
			if (oldQty == 0) qtyDifference -= 10000;
			
			if (DEBUG_PRICES) Log.d(TAG, "Old qty was:" + oldQty + "; new qty is: " + qty + "; difference is: " + qtyDifference);
			
			// These "direct" methods do not set the quantity to one if it was zero
			if (item.crossedOff)  {
				if (item.price == 0)
					activity.directAddToTotal(qtyDifference, item.estimatedPrice);
				else
					activity.directAddToTotal(qtyDifference, item.price);
			}
			if (item.price == 0)
				activity.directAddToEstimatedTotal(qtyDifference, item.estimatedPrice);
			else
				activity.directAddToEstimatedTotal(qtyDifference, item.price);

			adapter.notifyDataSetChanged();
		}
		
		if (editorMode == EditorModePrice) {
			final Item item = items.get(editorTarget);
			final long oldPrice = item.price;
			final boolean wasCrossedOff = item.crossedOff;
			final boolean autoCross = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("autoCross", true);
			long price;
			try {
				price = new BigDecimal(priceEditor.getText().toString()).movePointRight(2).longValue();
			}
			catch (NumberFormatException exception) {
				price = 0;
			}
			if (price != 0) {
				if (autoCross) {
					item.crossedOff = true;
				}
				item.flags |= SetPrice;
			}
			else
				item.flags &= ~SetPrice;
			item.price = price;
			
			long priceDifference = price - oldPrice;

			activity.addToEstimatedTotal(item.qty, priceDifference);
			if (oldPrice == 0 && price != 0)
				activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
			if (item.price == 0 && oldPrice != 0)
				activity.addToEstimatedTotal(item.qty, item.estimatedPrice);
			
			if (wasCrossedOff) {
				activity.addToTotal(item.qty, priceDifference);
				if (oldPrice == 0 && price != 0)
					activity.addToTotal(item.qty, -item.estimatedPrice);
				if (item.price == 0 && oldPrice != 0)
					activity.addToTotal(item.qty, item.estimatedPrice);
			}
			else if (autoCross) {
				if (item.crossedOff) {
					delayHandler.registerCallback(ReorderRunnable, ReorderDelay);
					activity.addToCrossedOffCount(1);
					if (item.price != 0)
						activity.addToTotal(item.qty, item.price);
					else
						activity.addToTotal(item.qty, item.estimatedPrice);
				}
			}

			adapter.notifyDataSetChanged();
		}
		
		if (editorMode == EditorModeTitle) {
			final Item item = items.get(editorTarget);
			
			String newName = titleEditor.getText().toString().trim();
			
			if (!TextUtils.isEmpty(newName))
				item.name = newName;
			
			// TODO dangerous, needs more testing
			// Definitely NOT production quality, better off for now
			/*if (items.get(editorTarget).estimatedPrice == 0 && titleEditor.getAdapter() == null) {
				if (DEBUG_SUGGESTIONS) Log.d(TAG, "Fired off an estimated price finder\n due to a title losing focus.");
				new EstimatedPriceFinder(item).execute();
			}*/

			adapter.notifyDataSetChanged();
		}
	}
	
	protected void commitChanges(boolean retainEditor) {
		setEditorFocusListener(null);

        if (currentExpander != null) {
            currentExpander.compact();
        }
		
		editorVisible = false;
		if (!retainEditor)
			editor.setVisibility(View.INVISIBLE);
		
		applyChanges();
		
		editorTarget = InvalidTarget;
		
		delayHandler.redelay(ReorderDelay);
		
	}
	
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
								setUnitOfMeasurement(item.getTitle().toString());
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
	
	public ArrayList<Item> items() {
		return items;
	}
	
	public void registerDataForRestore(ArrayList<Item> data) {
		items = data;
		adapter.notifyDataSetChanged();
	}
	
	public void setUnitOfMeasurement(String unit) {
		items.get(editorTarget).unitOfMeasurement = unit;
		qtyEditor.setHint(unit);
	}
	
	@Deprecated
	public void loadPendingItems() {
		new LoadPendingItemsAsyncTask().execute();
	}

	@Deprecated
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
				item.flags = SetTitle;
				item.crossedOff = false;
				item.estimatedPrice = pendingItems.getLong(Receipt.DBPriceKeyIndex);
				item.name = pendingItems.getString(Receipt.DBNameKeyIndex);
				item.unitOfMeasurement = pendingItems.getString(Receipt.DBUnitOfMeasurementKeyIndex);
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
			if (indicator != null)
				indicator.stopWorking(task);
			addPendingItems(result);
		}
    	
    }
    
    protected void addPendingItems(final ArrayList<Item> result) {
    	
    	if (result.size() <= 0)
    		return;
//    	else
//    		activity.hideHint();
    	
    	if (actionMode != null)
    		actionMode.finish();
    	
    	if (editorVisible)
    		if (editorMode == EditorModeAll)
    			finishAddingItem(false);
    		else
    			commitChanges(false);
    	
    	for (Item item : result) {
    		activity.addToItemCount(1);

			//Pending items never start off crossed off and their prices are usually estimates
    		item.crossedOff = false;
    		item.selected = false;
    		if (item.price == 0)
    			activity.addToEstimatedTotal(item.qty, item.estimatedPrice);
    		else
    			activity.addToEstimatedTotal(item.qty, item.price);
			
    	}
    	
    	int pos, offset;
    	if ((pos = list.getFirstVisiblePosition()) != 0) {
        	items.addAll(0, result);
        	adapter.notifyDataSetChanged();
    		list.setSelectionFromTop(pos + result.size(), offset = list.getChildAt(0).getTop());
    		list.smoothScrollToPositionFromTop(pos, offset, 300);
    	}
    	else {
    		
    		if (result.size() != 0) {
    			activity.findViewById(R.id.EmptyHint).setVisibility(View.GONE);
    		}
    		
    		delayHandler.redelay(ReorderDelay);
			LayoutInflater inflater = activity.getLayoutInflater();
			int itemsToInflate = result.size();
			float ViewHeight = getResources().getDimensionPixelOffset(R.dimen.DP48) - 1; //48f * metrics.density;
			float startingY = list.getChildAt(0) != null ? list.getChildAt(0).getY() : 0f;
			if (startingY != 0f && items.size() != result.size()) {
				list.smoothScrollToPosition(0);
			}
			float numItems = listRoot.getHeight() / ViewHeight;
			itemsToInflate = Math.min(itemsToInflate, Math.round(numItems + 0.5f));
//			}
//			else {
//				itemsToInflate = Math.min(itemsToInflate, list.getLastVisiblePosition() - list.getFirstVisiblePosition());
//			}
			
			for (int i = 0; i < itemsToInflate; i++) {
				
				final View view = inflater.inflate(R.layout.layout_list_item, listRoot, false);
				listRoot.addView(view);
				view.setY(0 - (itemsToInflate - i) * ViewHeight * metrics.density);
				generateViewHolder(view);
				decorateView(view, result.get(i));
				view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
				view.setAlpha(0);
				
				final AnimatorHolder holder = new AnimatorHolder();
				animators.add(holder);
				
				view.animate()
					.y(i * ViewHeight)
					.alpha(1)
					.setDuration(ReorderLength)
					.setListener(new AnimatorListenerAdapter() {
						public void onAnimationStart(Animator a) {
							holder.animator = a;
						}
						public void onAnimationEnd(Animator a) {
							listRoot.removeView(view);
							animators.remove(holder);
						}
					});
			}
			
			final AnimatorHolder holder = new AnimatorHolder();
			animators.add(holder);
			
			list.setLayerType(View.LAYER_TYPE_HARDWARE, null);
			// This animation MUST finish, or else data becomes corrupted
			list.animate()
				.translationY(itemsToInflate * ViewHeight)
				.setDuration(ReorderLength)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationStart(Animator a) {
						holder.animator = a;
					}
					public void onAnimationEnd(Animator a) {
						if (activity == null) return;
						list.setLayerType(View.LAYER_TYPE_NONE, null);
						list.animate().setListener(null);
				    	items.addAll(0, result);
						adapter.notifyDataSetChanged();
						list.setTranslationY(0);
						animators.remove(holder);
					}
				});
			
    	}
    }
    
    public void notifyLocaleChanged() {
    	adapter.notifyDataSetChanged();
    }
    
    public void addToSelectionTotal(int position) {
    	Item item = items.get(position);
    	if (item.price != 0) {
	    	if (item.qty == 0) {
	    		selectionTotal = selectionTotal.add(new BigDecimal(item.price).movePointLeft(2));
	    	}
	    	else {
	    		selectionTotal = selectionTotal.add(new BigDecimal(item.price).movePointLeft(2).multiply(new BigDecimal(item.qty).movePointLeft(4)));
	    	}
    	}
    	else {
	    	if (item.qty == 0) {
	    		selectionTotal = selectionTotal.add(new BigDecimal(item.estimatedPrice).movePointLeft(2));
	    	}
	    	else {
	    		selectionTotal = selectionTotal.add(new BigDecimal(item.estimatedPrice).movePointLeft(2).multiply(new BigDecimal(item.qty).movePointLeft(4)));
	    	}
    	}
    }
    
    public void subtractFromSelectionTotal(int position) {
    	Item item = items.get(position);
    	if (item.price != 0) {
	    	if (item.qty == 0) {
	    		selectionTotal = selectionTotal.subtract(new BigDecimal(item.price).movePointLeft(2));
	    	}
	    	else {
	    		selectionTotal = selectionTotal.subtract(new BigDecimal(item.price).movePointLeft(2).multiply(new BigDecimal(item.qty).movePointLeft(4)));
	    	}
    	}
    	else {
	    	if (item.qty == 0) {
	    		selectionTotal = selectionTotal.subtract(new BigDecimal(item.estimatedPrice).movePointLeft(2));
	    	}
	    	else {
	    		selectionTotal = selectionTotal.subtract(new BigDecimal(item.estimatedPrice).movePointLeft(2).multiply(new BigDecimal(item.qty).movePointLeft(4)));
	    	}
    	}
    }
    
    public void resetSelectionTotal() {
    	selectionTotal = new BigDecimal(0);
    }
    
    private void restoreActionMode() {
		actionMode = activity.startActionMode(actionModeCallback);
		actionMode.setTitle(selectionList.size() + " selected");
		actionMode.setSubtitle(ReceiptActivity.currentLocale + selectionTotal
				.add(selectionTotal
						.multiply(new BigDecimal(activity.getTax())
							.movePointLeft(4)))
				.setScale(2, RoundingMode.HALF_EVEN) + " total");
    }
    
    public void toggleSelectionForPosition(int position) {

		if (confirmingPosition != ListView.INVALID_POSITION)
			confirmDeletion();
    	
    	if (actionMode == null) {
    		actionMode = activity.startActionMode(actionModeCallback);
    	}
    	
    	if (items.get(position).selected) {
    		if (DEBUG_SELECTION) Log.d(TAG, "Removing from selection position " + position);
    		selectionList.remove(selectionList.indexOf(position));
    		subtractFromSelectionTotal(position);
    	}
    	else {
    		if (DEBUG_SELECTION) Log.d(TAG, "Adding to selection position " + position);
    		selectionList.add(position);
    		addToSelectionTotal(position);
    	}
    	
    	items.get(position).selected = !items.get(position).selected;
    	
    	multipleSelection = selectionList.size() > 1;
    	actionMode.invalidate();
    	
    	if (selectionList.size() > 0) {
			actionMode.setTitle(selectionList.size() + " selected");
			actionMode.setSubtitle(ReceiptActivity.currentLocale + selectionTotal
					.add(selectionTotal
							.multiply(new BigDecimal(activity.getTax())
								.movePointLeft(4)))
					.setScale(2, RoundingMode.HALF_EVEN) + " total");
    	}
    	else {
    		actionMode.finish();
    	}
		
		adapter.notifyDataSetChanged();
    }
    
    public void deselect() {
    	resetSelectionTotal();
    	selectionList.clear();
    	for (Item item : items) {
    		item.selected = false;
    	}
    	adapter.notifyDataSetChanged();
    }
    
	public ArrayList<Item> deleteSelection() {

//		final View ScreenshotView = new View(activity);
//    	final Rect ListRect = new Rect();
//		list.getGlobalVisibleRect(ListRect);
//		Bitmap screenshot = Bitmap.createBitmap(ListRect.width(), ListRect.height(), Bitmap.Config.ARGB_8888);
//		
//		Canvas canvas = new Canvas(screenshot);
		
		final int listEnd = list.getLastVisiblePosition();
		final int listStart = list.getFirstVisiblePosition();
		int counter = 0;
		
//		canvas.translate(0, list.getChildAt(0).getY());
		
		final ArrayList<AnimatorHolder> localHolders = new ArrayList<ItemListFragment.AnimatorHolder>();
		final ArrayList<View> localViewHolders = new ArrayList<View>();
		final ArrayList<Integer> localAnchors = new ArrayList<Integer>();
		final int animationsElapsed[] = new int[1];
		final int localHoldersSize[] = new int[1];
		
		for (int i = 0; i < listStart; i++) {
			if (items.get(i).selected)
				localAnchors.add(i);
		}
		
		final int scrollOffset = localAnchors.size();
		
		for (int i = listStart; i < items.size(); i++) {
			if (items.get(i).selected)
				localAnchors.add(i);
		}
		
		for (int i = listEnd; i >= listStart; i--) {
			if (items.get(i).selected) {
				final View DeletedView = list.getChildAt(i - listStart);//.draw(canvas);
				
				final Integer Anchor = i;
				
				anchors.add(Anchor);
				localAnchors.remove(Anchor);
				
				ViewCompat.setHasTransientState(DeletedView, true);
				final AnimatorHolder holder = new AnimatorHolder();
				holder.target = i;
				
				localHolders.add(holder);
				localViewHolders.add(DeletedView);
				
				DeletedView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
				DeletedView.animate()
					.translationX(DeletedView.getWidth())
					.alpha(0)
					.setDuration(200)
					.setInterpolator(new AccelerateInterpolator())
					.setStartDelay(30 * counter)
					.setListener(new AnimatorListenerAdapter() {
						boolean cancelled;
						public void onAnimationStart(Animator a) {
							holder.animator = a;
						}
						public void onAnimationCancel(Animator a) {
							cancelled = true;
						}
						public void onAnimationEnd(Animator a) {
							if (cancelled) {
								localHolders.remove(holder);
								removeHolder(holder);
								DeletedView.setAlpha(1);
								DeletedView.setTranslationX(0);
								DeletedView.setLayerType(View.LAYER_TYPE_NONE, null);
								ViewCompat.setHasTransientState(DeletedView, false);
								anchors.removeAll(localAnchors);
								adapter.notifyDataSetChanged();
							}
							final int ViewHeight = DeletedView.getHeight();
							ValueAnimator animator = ValueAnimator.ofInt(ViewHeight, 2);
							animator.setDuration(200);
							animator.addUpdateListener(new AnimatorUpdateListener() {
								@Override
								public void onAnimationUpdate(ValueAnimator arg0) {
									DeletedView.getLayoutParams().height = (Integer) arg0.getAnimatedValue();
									DeletedView.requestLayout();
								}
							});
							animator.addListener(new AnimatorListenerAdapter() {
								public void onAnimationEnd(Animator a) {
									animationsElapsed[0]++;
									if (animationsElapsed[0] == localHoldersSize[0]) {
										if (DEBUG_DELETION) Log.d(TAG, "Animations have been finished!\nLocal anchors are " + localAnchors
												+ "\nlocalHoldersSize is " + localHoldersSize[0]);
										anchors.removeAll(localAnchors);
										for (int i = 0; i < animationsElapsed[0]; i++) {
											removeHolder(localHolders.get(i));
											View restoredView = localViewHolders.get(i);
											restoredView.getLayoutParams().height = ViewHeight;
											restoredView.requestLayout();
											restoredView.setAlpha(1);
											restoredView.setTranslationX(0);
											restoredView.setLayerType(View.LAYER_TYPE_NONE, null);
											ViewCompat.setHasTransientState(restoredView, false);
										}
										if (scrollOffset != 0 && scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
											list.smoothScrollToPositionFromTop(listStart - scrollOffset, 0, 0);
										}
										adapter.notifyDataSetChanged();
									}
								}
							});
							holder.animator = animator;
							animator.start();
						}
					})
					.start();
				
				counter++;
				
			}
			//canvas.translate(0, list.getChildAt(i - listStart).getHeight());
		}
		localHoldersSize[0] = localHolders.size();
		anchors.addAll(localAnchors);
		
//		FrameLayout.LayoutParams screenshotParams = new FrameLayout.LayoutParams(ListRect.width(), ListRect.height());
//		
//		ScreenshotView.setBackgroundDrawable(new BitmapDrawable(getResources(), screenshot));
//		ScreenshotView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
//		ScreenshotView.setX(ListRect.left);
//		ScreenshotView.setY(ListRect.top);
//		ScreenshotView.setClickable(true);
//		root.addView(ScreenshotView, screenshotParams);
//		
//		ScreenshotView.animate()
//			.alpha(0).translationX(ListRect.width())
//			.setDuration(200)
//			.setInterpolator(new AccelerateInterpolator())
//			.setListener(new AnimatorListenerAdapter() {
//				public void onAnimationEnd(Animator a) {
//					if (activity == null) return;
//					root.removeView(ScreenshotView);
//				}
//			});
    	
    	if (DEBUG_DELETION) Log.d(TAG, "Selection list has " + selectionList.size() + " items");
    	
    	
    	ArrayList<Item> deletedItems = new ArrayList<Item>();
    	for (Integer i : selectionList) {
        	if (DEBUG_DELETION) Log.d(TAG, "Processing item " + i);
        	
    		activity.addToItemCount(-1);
        	
        	Item item = items.get(i);
        	if (item.price == 0)
        		activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
        	else
        		activity.addToEstimatedTotal(item.qty, -item.price);
        	
    		if (item.crossedOff) {
            	if (item.price == 0)
            		activity.addToTotal(item.qty, -item.estimatedPrice);
            	else
            		activity.addToTotal(item.qty, -item.price);
    			activity.addToCrossedOffCount(-1);
    		}
    		deletedItems.add(items.get(i));
    	}
    	items.removeAll(deletedItems);
    	adapter.notifyDataSetChanged();
    	return deletedItems;
    }
	

    
	public ArrayList<Item> cutSelection() {
		
		final int listEnd = list.getLastVisiblePosition();
		final int listStart = list.getFirstVisiblePosition();
		int counter = 0;
		
		final ArrayList<AnimatorHolder> localHolders = new ArrayList<ItemListFragment.AnimatorHolder>();
		final ArrayList<View> localViewHolders = new ArrayList<View>();
		final ArrayList<Integer> localAnchors = new ArrayList<Integer>();
		final int animationsElapsed[] = new int[1];
		final int localHoldersSize[] = new int[1];
		
		for (int i = 0; i < listStart; i++) {
			if (items.get(i).selected)
				localAnchors.add(i);
		}
		
		final int scrollOffset = localAnchors.size();
		
		for (int i = listStart; i < items.size(); i++) {
			if (items.get(i).selected)
				localAnchors.add(i);
		}
		
		final View Flash = new View(activity);
		Flash.setBackgroundColor(getResources().getColor(android.R.color.white));
		Flash.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		Flash.setAlpha(0);
		Flash.setClickable(true);
		root.addView(Flash);
		Flash.animate()
			.alpha(1)
			.setDuration(50)
			.setInterpolator(new AccelerateInterpolator(2))
			.setListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator a) {
					if (activity == null) return;
					Flash.animate()
						.alpha(0)
						.setDuration(250)
						.setInterpolator(new AccelerateInterpolator(1))
						.setListener(new  AnimatorListenerAdapter() {
							public void onAnimationEnd(Animator a) {
								if (activity == null) return;
								root.removeView(Flash);
							}
						});
				}
			});
		
		for (int i = listEnd; i >= listStart; i--) {
			if (items.get(i).selected) {
				final View DeletedView = list.getChildAt(i - listStart);//.draw(canvas);
				
				final Integer Anchor = i;
				
				anchors.add(Anchor);
				localAnchors.remove(Anchor);
				
				ViewCompat.setHasTransientState(DeletedView, true);
				final AnimatorHolder holder = new AnimatorHolder();
				holder.target = i;
				
				localHolders.add(holder);
				localViewHolders.add(DeletedView);
				
				DeletedView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
				DeletedView.animate()
					.alpha(0)
					.setDuration(200)
					.setInterpolator(new AccelerateInterpolator())
					.setStartDelay(30 * counter)
					.setListener(new AnimatorListenerAdapter() {
						boolean cancelled;
						public void onAnimationStart(Animator a) {
							holder.animator = a;
						}
						public void onAnimationCancel(Animator a) {
							cancelled = true;
						}
						public void onAnimationEnd(Animator a) {
							if (cancelled) {
								localHolders.remove(holder);
								removeHolder(holder);
								DeletedView.setAlpha(1);
								DeletedView.setTranslationX(0);
								DeletedView.setLayerType(View.LAYER_TYPE_NONE, null);
								ViewCompat.setHasTransientState(DeletedView, false);
								anchors.removeAll(localAnchors);
								adapter.notifyDataSetChanged();
							}
							final int ViewHeight = DeletedView.getHeight();
							ValueAnimator animator = ValueAnimator.ofInt(ViewHeight, 2);
							animator.setDuration(200);
							animator.addUpdateListener(new AnimatorUpdateListener() {
								@Override
								public void onAnimationUpdate(ValueAnimator arg0) {
									DeletedView.getLayoutParams().height = (Integer) arg0.getAnimatedValue();
									DeletedView.requestLayout();
								}
							});
							animator.addListener(new AnimatorListenerAdapter() {
								public void onAnimationEnd(Animator a) {
									animationsElapsed[0]++;
									if (animationsElapsed[0] == localHoldersSize[0]) {
										if (DEBUG_DELETION) Log.d(TAG, "Animations have been finished!\nLocal anchors are " + localAnchors
												+ "\nlocalHoldersSize is " + localHoldersSize[0]);
										anchors.removeAll(localAnchors);
										for (int i = 0; i < animationsElapsed[0]; i++) {
											removeHolder(localHolders.get(i));
											View restoredView = localViewHolders.get(i);
											restoredView.getLayoutParams().height = ViewHeight;
											restoredView.requestLayout();
											restoredView.setAlpha(1);
											restoredView.setTranslationX(0);
											restoredView.setLayerType(View.LAYER_TYPE_NONE, null);
											ViewCompat.setHasTransientState(restoredView, false);
										}
										if (scrollOffset != 0 && scrollState == OnScrollListener.SCROLL_STATE_IDLE) {
											list.smoothScrollToPositionFromTop(listStart - scrollOffset, 0, 0);
										}
										adapter.notifyDataSetChanged();
									}
								}
							});
							holder.animator = animator;
							animator.start();
						}
					})
					.start();
				
				counter++;
				
			}
		}
		localHoldersSize[0] = localHolders.size();
		anchors.addAll(localAnchors);
    	
    	if (DEBUG_DELETION) Log.d(TAG, "Selection list has " + selectionList.size() + " items");
    	
    	
    	ArrayList<Item> deletedItems = new ArrayList<Item>();
    	for (Integer i : selectionList) {
        	if (DEBUG_DELETION) Log.d(TAG, "Processing item " + i);
        	
    		activity.addToItemCount(-1);
        	
        	Item item = items.get(i);
        	if (item.price == 0)
        		activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
        	else
        		activity.addToEstimatedTotal(item.qty, -item.price);
        	
    		if (item.crossedOff) {
            	if (item.price == 0)
            		activity.addToTotal(item.qty, -item.estimatedPrice);
            	else
            		activity.addToTotal(item.qty, -item.price);
    			activity.addToCrossedOffCount(-1);
    		}
    		deletedItems.add(items.get(i));
    	}
    	items.removeAll(deletedItems);
    	adapter.notifyDataSetChanged();
//    	activity.appendToClipboard(deletedItems);
    	return deletedItems;
    }
	
	public void flashScreen() {
		final View Flash = new View(activity);
		Flash.setBackgroundColor(getResources().getColor(android.R.color.white));
		Flash.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		Flash.setAlpha(0);
		Flash.setClickable(true);
		root.addView(Flash);
		Flash.animate()
			.alpha(1)
			.setDuration(50)
			.setInterpolator(new AccelerateInterpolator(2))
			.setListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator a) {
					if (activity == null) return;
					Flash.animate()
						.alpha(0)
						.setDuration(250)
						.setInterpolator(new AccelerateInterpolator(1))
						.setListener(new  AnimatorListenerAdapter() {
							public void onAnimationEnd(Animator a) {
								if (activity == null) return;
								root.removeView(Flash);
							}
						});
				}
			});
	}
	
	public void copySelection() {
    	flashScreen();
    	
    	ArrayList<Item> deletedItems = new ArrayList<Item>();
    	for (Integer i : selectionList) {
    		deletedItems.add(items.get(i).clipboardCopy());
    	}
//    	activity.appendToClipboard(deletedItems);
	}
    
    public void editTitleForSelection(int position) {

		boolean listViewHack = commitEditor(true) || (deleteAnimator != null && anchors.size() == 0);
		
		View viewParent = list.getChildAt(position - list.getFirstVisiblePosition());
		viewParent.setVisibility(View.INVISIBLE);
		
		cloneViewInEditor(position, true);
		
		if (!listViewHack) {
			titleEditor.setText(items.get(position).name);
		}
		
		editorVisible = true;
		editorTarget = position;
		editor.setVisibility(View.VISIBLE);
		setEditorMode(EditorModeTitle, true);
		viewParent.setVisibility(View.INVISIBLE);
		setEditorListener(editorListener);
		setEditorFocusListener(focusLossListener);
		
		titleEditor.requestFocus();
		InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(titleEditor, InputMethodManager.SHOW_FORCED);
    }
    
    // **************************** ACTIONMODE RELATED CALLS ******************************

	// selection handlers
	private ArrayList<Integer> selectionList = new ArrayList<Integer>();
	private ActionMode actionMode = null;
	private boolean multipleSelection;
	private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {
		
		private boolean multiMode;
		
	    @Override
	    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
	    	delayHandler.unregisterCallback();
	    	multiMode = false;
	        // Inflate a menu resource providing context menu items
	        MenuInflater inflater = mode.getMenuInflater();
	        inflater.inflate(R.menu.item_selection, menu);
	        return true;
	    }

	    @Override
	    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
	    	if (multiMode != multipleSelection) {
	    		multiMode = multipleSelection;
	    		menu.clear();
		        MenuInflater inflater = mode.getMenuInflater();
		    	if (multipleSelection) {
			        inflater.inflate(R.menu.item_multiple_selection, menu);
			        return true;
		    	}
		    	else {
			        inflater.inflate(R.menu.item_selection, menu);
			        return true;
		    	}
	    	}
	        return false; // Return false if nothing is done
	    }

	    @Override
	    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
	        switch (item.getItemId()) {
	        	case R.id.action_delete:
		        	deleteSelection();
		        	mode.finish();
		        	return true;
	        	case R.id.action_cut:
	        		cutSelection();
	        		mode.finish();
	        		return true;
	        	case R.id.action_copy:
	        		copySelection();
	        		mode.finish();
	        		return true;
	        	case R.id.action_crosoff:
	        		mode.finish();
	        		return true;
	        	case R.id.action_rename:
	        		editTitleForSelection(selectionList.get(0));
	        		mode.finish();
	        		return true;
	            default:
	                return false;
	        }
	    }

	    @Override
	    public void onDestroyActionMode(ActionMode mode) {
	    	if (selectionList.size() != 0) {
	    		deselect();
	    	}
	    	actionMode = null;
	    	delayHandler.registerCallback(ReorderRunnable, ReorderDelay);
	    	if (editorVisible) delayHandler.delayIndefinitely();
	    }
	};
	
	//************************** HISTORY BASED SUGGESTIONS ************************

	
	static class Suggestion {
		String name;
		long price;
		String measurement;
		
		public String toString() {
			return name;
		}
		
		public static Suggestion make(Cursor cursor) {
			Suggestion suggestion = new Suggestion();
			suggestion.name = cursor.getString(0);
			suggestion.price = cursor.getLong(1);
			suggestion.measurement = cursor.getString(2);
			return suggestion;
		}
	}
	
	class FindSuggestionsAsyncTask extends AsyncTask<String, Void, Suggestion[]> {

		@Override
		protected Suggestion[] doInBackground(String... arg0) {
			synchronized (Receipt.DatabaseLock) {
				SQLiteDatabase db = Receipt.DBHelper.getReadableDatabase();
				
				if (isCancelled()) {
					db.close();
					return null;
				}
	
				String text = arg0[0].toLowerCase(Locale.getDefault()) + "%" ;
				Cursor query = db.query(Receipt.DBItemsTable,		 																					//FROM
						new String[] {Receipt.DBNameKey, Receipt.DBPriceKey, Receipt.DBUnitOfMeasurementKey}, 											//SELECT
						"lower(" + Receipt.DBNameKey + ") like ?", 																						//WHERE
						new String[]{text}, 																											//ARGS
						Receipt.DBNameKey, null, "count(" + Receipt.DBNameKey + ") desc",																//GROUPBY, HAVING, ORDERBY
						"5");																															//LIMIT
				
				Suggestion result[] = null;
				
				if (isCancelled()) {
					query.close();
					db.close();
					return null;
				}
				
				int index = 0;
				if (query.getCount() != 0) {
					
					result = new Suggestion[query.getCount()];
					
					while (query.moveToNext()) {
						
						if (isCancelled()) {
							query.close();
							db.close();
							return null;
						}
						
						result[index] = Suggestion.make(query);
						index++;
					}
				}
				
				query.close();
				db.close();
				return result;
			}
		}
		
		@Override
		protected void onPostExecute(Suggestion[] results) {
			preparePopupMenu(results);
		}
		
	}
	
	class EstimatedPriceFinder extends AsyncTask<Void, Void, Suggestion> {
			
		Item item;
		
		EstimatedPriceFinder(Item item) {
			this.item = item;
		}

		@Override
		protected Suggestion doInBackground(Void... arg0) {
			synchronized (Receipt.DatabaseLock) {
				SQLiteDatabase db = Receipt.DBHelper.getReadableDatabase();
				
				if (isCancelled()) {
					db.close();
					return null;
				}
	
				String text = item.name.toLowerCase(Locale.getDefault());
				Cursor query = db.query(Receipt.DBItemsTable,		 																					//FROM
						new String[] {Receipt.DBNameKey, Receipt.DBPriceKey, Receipt.DBUnitOfMeasurementKey}, 											//SELECT
						"lower(" + Receipt.DBNameKey + ") like ?", 																						//WHERE
						new String[]{text}, 																											//ARGS
						Receipt.DBNameKey, null, "count(" + Receipt.DBNameKey + ") desc",																//GROUPBY, HAVING, ORDERBY
						null);				
				
				if (isCancelled()) {
					query.close();
					db.close();
					return null;
				}
				
				Suggestion suggestion = null;
				
				if (query.getCount() != 0) {
					
					query.moveToFirst();
						
					if (isCancelled()) {
						query.close();
						db.close();
						return null;
					}
					
					suggestion = Suggestion.make(query);
				}
				
				query.close();
				db.close();
				return suggestion;
			}
		}
		
		@Override
		protected void onPostExecute(Suggestion result) {
			
			// Only do this of the item's name hasn't changed in the meantime
			if (result != null) {
				if (TextUtils.equals(item.name, result.name)) {
					if (item.price == 0) {
						activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
						activity.addToEstimatedTotal(item.qty, result.price);
						if (item.crossedOff) {
							activity.addToTotal(item.qty, -item.estimatedPrice);
							activity.addToTotal(item.qty, result.price);
						}
					}
					
					item.estimatedPrice = result.price;
					item.unitOfMeasurement = result.measurement;
				}
			}
			
			adapter.notifyDataSetChanged();
		}
		
	}
	
	private FindSuggestionsAsyncTask suggestionsTask;
	
	public void preparePopupMenu(Suggestion[] suggestions) {
		
		if (activity == null) return;
		
		final ArrayAdapter<Suggestion> suggestionsAdapter = new ArrayAdapter<Suggestion>(activity, R.layout.layout_suggestion, R.id.Suggestion);
		if (suggestions != null) suggestionsAdapter.addAll(suggestions);
		titleEditor.setAdapter(suggestionsAdapter);
		loopForSuggestion(titleEditor.getText().toString());
		adapter.notifyDataSetChanged();
		
	}
	
	public boolean loopForSuggestion(CharSequence s) {
		if (titleEditor.getAdapter() != null) {
			@SuppressWarnings("unchecked")
			ArrayAdapter<Suggestion> suggestionsAdapter = (ArrayAdapter<Suggestion>) titleEditor.getAdapter();
			int adapterSize = suggestionsAdapter.getCount();
			for (int i = 0; i < adapterSize; i++) {
				Suggestion suggestion = suggestionsAdapter.getItem(i);
				if (s.toString().trim().equalsIgnoreCase(suggestion.name)) {
					if (editorMode == EditorModeAll) {
						qtyEditor.setHint(suggestion.measurement);
						
						Item item = items.get(0);
						
						item.unitOfMeasurement = suggestion.measurement;
						
						// For new items, estimated price is set and added on completion
						
						if (suggestion.price > 0) {
							priceEditor.setHint(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(suggestion.price));
						}
						else {
							priceEditor.setHint(getString(R.string.PriceEditHint));
						}
						item.estimatedPrice = suggestion.price;
					}
					else {
						
						Item item = items.get(editorTarget);
						
						item.unitOfMeasurement = suggestion.measurement;
						if (item.price == 0) {
							activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
							activity.addToEstimatedTotal(item.qty, suggestion.price);
							if (item.crossedOff) {
								activity.addToTotal(item.qty, -item.estimatedPrice);
								activity.addToTotal(item.qty, suggestion.price);
							}
						}
						
						qtyEditor.setHint(suggestion.measurement);
						item.unitOfMeasurement = suggestion.measurement;
						if (suggestion.price > 0) {
							priceEditor.setHint(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(suggestion.price));
						}
						else {
							priceEditor.setHint(getString(R.string.PriceEditHint));
						}
						item.estimatedPrice = suggestion.price;
						cloneViewInEditor(editorTarget, true);
					}
					return true;
				}
			}
		}
		return false;
	}
	
	public void showSuggestionsForView(TextView view) {
		
		if (DEBUG_SUGGESTIONS) Log.d("ItemsFragment", "Control reached showSuggestionsForView(TextView);");
		
		if (view.getText().toString().isEmpty()) 
			return;
		if (suggestionsTask != null) 
			suggestionsTask.cancel(false);
		suggestionsTask = new FindSuggestionsAsyncTask();
		suggestionsTask.execute(view.getText().toString().trim());
	}
	
	// ******************** DELETE ONTOUCH LISTENER ************************
	


	final static int initialSwipeSteps = 2;
	private float minimumSwipeDistance;
	private float minimumSwipeError;
	private float minimumSwipeSpeed;
	private android.view.GestureDetector detector;
	
	private void prepareTouchListener() {
		minimumSwipeDistance = 2 * metrics.widthPixels/3;
		minimumSwipeSpeed = 1000;
		minimumSwipeError = 10 * metrics.density;
		detector = new android.view.GestureDetector(activity, new GestureDetector());
		detector.setIsLongpressEnabled(false);
	}
	
	
	private void cancelAnchorAnimations() {

		// Stop outstanding animations to prevent errors due to a changing item count
		int animatorSize = animators.size();
		for (int i = 0; i < animatorSize; i++)
			// The animators should be cleared in descending order of their anchors
			animators.get(0).animator.cancel();
		
	}
	
	public boolean isAnimated(int anchor) {
		for (AnimatorHolder holder : animators) {
			if (holder.target == anchor) return true;
		}
		return false;
	}
	
	private AnimatorHolder getHolderForAnchor(int anchor) {
		for (AnimatorHolder holder : animators) {
			if (holder.target == anchor) return holder;
		}
		return null;
	}
	
	private void removeHolder(final AnimatorHolder holder) {
		animators.remove(holder);
		removeAnchor(holder.target);
	}
	
	private void removeAnchor(final int Anchor) {

		anchors.remove(Integer.valueOf(Anchor));
		int anchorSize = anchors.size();
		for (int i = 0; i < anchorSize; i++) {
			if (anchors.get(i) > Anchor) {
				if (anchors.get(i) == confirmingPosition) confirmingPosition--;
				AnimatorHolder holder = getHolderForAnchor(anchors.get(i));
				if (holder != null) holder.target--;
				anchors.set(i, anchors.get(i) - 1);
			}
		}
		
	}
	
	public void confirmDeletion() {
		
		if (confirmingPosition != ListView.INVALID_POSITION) {
			View v = activity.findViewById(R.id.HistoryDate2);
			if (v != null) root.removeView(v);
		}
		else return;
		
		//Finish outstanding animations
		cancelAnchorAnimations();
		
		final int Anchor = confirmingPosition;
		
		final View deletedView = list.getChildAt(confirmingPosition - list.getFirstVisiblePosition());
		if (deletedView != null) {
			Rect deleteRect = new Rect();
			deletedView.getGlobalVisibleRect(deleteRect);

    		activity.addToItemCount(-1);
        	Item item = items.get(decode(confirmingPosition));
        	if (item.price == 0)
        		activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
        	else
        		activity.addToEstimatedTotal(item.qty, -item.price);
        	
    		if (item.crossedOff) {
            	if (item.price == 0)
            		activity.addToTotal(item.qty, -item.estimatedPrice);
            	else
            		activity.addToTotal(item.qty, -item.price);
    			activity.addToCrossedOffCount(-1);
    		}
			items.remove(decode(confirmingPosition));
			
			list.setOnTouchListener(null);
			if (deleteAnimator != null)
				deleteAnimator.cancel();
			final int ViewHeight = deletedView.getHeight();
			
			ViewCompat.setHasTransientState(deletedView, true);
			deletedView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
			
			ValueAnimator animator = ValueAnimator.ofInt(deletedView.getHeight(), 2);
			
			final AnimatorHolder holder = new AnimatorHolder();
			holder.animator = animator;
			holder.target = Anchor;
			
			animator.addUpdateListener(new AnimatorUpdateListener() {
				@Override
				public void onAnimationUpdate(ValueAnimator animation) {
					deletedView.getLayoutParams().height = (Integer) animation.getAnimatedValue();
					deletedView.requestLayout();
				}
			});
			animator.addListener(new AnimatorListenerAdapter() {
				public void onAnimationEnd(Animator a) {
					deletedView.getLayoutParams().height = ViewHeight;
					deletedView.requestLayout();
					
					restoreDeletedView(deletedView);
//					removeAnchor(Anchor);
					//Remove holder also removes the anchor
					removeHolder(holder);

					deletedView.setLayerType(View.LAYER_TYPE_NONE, null);
					ViewCompat.setHasTransientState(deletedView, false);
					adapter.notifyDataSetChanged();
				}
			});
			animator.setDuration(200);
			animator.start();
			animator.setTarget(Anchor);
			int target;
			int animatorsSize = animators.size();
			for (target = 0; target < animatorsSize; target++) {
				if (animators.get(target).target < Anchor)
					break;
			}
			animators.add(target, holder);
			confirmingPosition = ListView.INVALID_POSITION;
			
			delayHandler.redelay(ReorderDelay);
		}
		else {
			confirmDeletionInstantly();
		}
	}
	
	public void confirmDeletionInstantly() {

		activity.addToItemCount(-1);
    	Item item = items.get(decode(confirmingPosition));
    	if (item.price == 0)
    		activity.addToEstimatedTotal(item.qty, -item.estimatedPrice);
    	else
    		activity.addToEstimatedTotal(item.qty, -item.price);
    	
		if (item.crossedOff) {
        	if (item.price == 0)
        		activity.addToTotal(item.qty, -item.estimatedPrice);
        	else
        		activity.addToTotal(item.qty, -item.price);
			activity.addToCrossedOffCount(-1);
		}
		
		list.setOnTouchListener(null);
		items.remove(decode(confirmingPosition));
		adapter.notifyDataSetChanged();
		
		removeAnchor(confirmingPosition);
		confirmingPosition = ListView.INVALID_POSITION;
		
		delayHandler.redelay(ReorderDelay);
		
	}
	
	private OnTouchListener deleteConfirmator = new OnTouchListener() {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			if (confirmingPosition == ListView.INVALID_POSITION) return false;
			Rect r = new Rect();
			list.getGlobalVisibleRect(r);
			if (event.getRawY() > r.top && event.getRawY() < r.bottom && event.getRawX() > r.left && event.getRawX() < r.right) return false;
			
			final View deletedView = list.getChildAt(confirmingPosition - list.getFirstVisiblePosition());
			if (deletedView != null) {
				Rect deleteRect = new Rect();
				deletedView.getGlobalVisibleRect(deleteRect);
				
				boolean inDeleteStrip = event.getRawX() > deleteRect.left && event.getRawX() < deleteRect.right
						&& event.getRawY() > deleteRect.top && event.getRawY() < deleteRect.bottom;
				
				if (!inDeleteStrip) {
					confirmDeletion();
				}
			}
			else {
				confirmDeletion();
			}
			return false;
		}
	};
	
	public void restoreDeletedView(final View deletedView) {

		ViewHolder holder = (ViewHolder) deletedView.getTag();
		
		int count = ((ViewGroup)holder.deleteRoot).getChildCount();
		for (int i = 0; i < count; i++) {
			((ViewGroup)holder.deleteRoot).getChildAt(i).setVisibility(View.GONE);
		}

		deletedView.setOnLongClickListener(itemLongClickListener);
		deletedView.setOnClickListener(itemClickListener);
		deletedView.setClickable(true);
		deletedView.setLongClickable(true);
		deletedView.setPressed(false);
		
		holder.deleteRoot.setVisibility(View.GONE);
		holder.itemRoot.setBackgroundResource(0);
		holder.itemRoot.setAlpha(1);
		holder.itemRoot.setTranslationX(0);
		holder.itemRoot.setVisibility(View.VISIBLE);
		
	}
	
	public void decorateDeletedView(final ViewHolder Holder) {
		int count = ((ViewGroup)Holder.deleteRoot).getChildCount();
		
		if (Holder.itemRoot.getWidth() != 0)
			Holder.itemRoot.setTranslationX(Holder.itemRoot.getWidth());
		else
			Holder.itemRoot.post(new Runnable() {
				@Override
				public void run() {
					Holder.itemRoot.setTranslationX(Holder.itemRoot.getWidth());
				}
			});
		Holder.itemRoot.setAlpha(0);
		Holder.itemRoot.setBackgroundColor(0xFFFFFFFF);
		
		Holder.deleteRoot.setVisibility(View.VISIBLE);
		for (int i = 0; i < count; i++) {
			((ViewGroup)Holder.deleteRoot).getChildAt(i).setVisibility(View.VISIBLE);
		}
		
		final View postView = (View) Holder.itemRoot.getParent();
		
		postView.setOnClickListener(null);
		postView.setOnLongClickListener(null);
		postView.setClickable(false);
		postView.setLongClickable(false);
		postView.setBackgroundResource(0);
		postView.setPressed(false);
		
		Holder.deleteRoot.findViewById(R.id.Undo).setEnabled(true);
		Holder.deleteRoot.findViewById(R.id.Undo).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				
				anchors.remove(Integer.valueOf(confirmingPosition));
				
				confirmingPosition = ListView.INVALID_POSITION;
				
				// To reset ids to their correct values
				adapter.notifyDataSetChanged();
				ViewCompat.setHasTransientState(postView, true);
				
				root.removeView(activity.findViewById(R.id.HistoryDate2));
				//list.setOnTouchListener(null);
				
				Holder.deleteRoot.findViewById(R.id.Undo).setEnabled(false);
				Holder.itemRoot.animate()
					.translationX(0).alpha(1).setDuration(200)
					.setListener(new AnimatorListenerAdapter() {
						public void onAnimationEnd(Animator a) {
							if (activity == null) return;
							Holder.itemRoot.animate().setListener(null);
							ViewCompat.setHasTransientState(postView, false);
							
							postView.setOnLongClickListener(itemLongClickListener);
							postView.setOnClickListener(itemClickListener);
							postView.setClickable(true);
							postView.setLongClickable(true);
							postView.setPressed(false);
							int count = ((ViewGroup)Holder.deleteRoot).getChildCount();
							for (int i = 0; i < count; i++) {
								((ViewGroup)Holder.deleteRoot).getChildAt(i).setVisibility(View.GONE);
							}
							Holder.deleteRoot.setVisibility(View.GONE);
							Holder.itemRoot.setBackgroundResource(0);
							
							// To clear up possible imperfect values
							adapter.notifyDataSetChanged();
						}
					});
				
				delayHandler.redelay(ReorderDelay);
			}
		});
	}
	
	private OnTouchListener nullOnTouchListener = new OnTouchListener() {
		@Override
		public boolean onTouch(View v, MotionEvent event) {
			return true;
		}
	};
	
	class DeleteTouchListener implements OnTouchListener {
		
		public boolean activated;
		public boolean started;
		public boolean ran;
		private int elapsedSteps;
		private float previousX, previousY;
		private float startX;
		private float x, y;
		
		@Override
		public boolean onTouch(View view, MotionEvent event) {
			
			boolean shouldFling = detector.onTouchEvent(event);
			
			if (activity == null) return false;
			
			x = event.getRawX();
			y = event.getRawY();
			
			if (event.getAction() == MotionEvent.ACTION_DOWN) {
				activated = true;
				started = false;
				ran = false;
				previousX = x;
				previousY = y;
				startX = previousX;
				elapsedSteps = 1;
	            if (scrollState != OnScrollListener.SCROLL_STATE_IDLE || selectionList.size() > 0) {
	            	activated = false;
	            }
				return false;
			}
			
			if (elapsedSteps < initialSwipeSteps && event.getAction() != MotionEvent.ACTION_UP) {
				if (Math.abs(y - previousY) > Math.abs(x - previousX) || scrollState != OnScrollListener.SCROLL_STATE_IDLE) {
					activated = false;
				}
				else {
					previousX = x;
					previousY = y;
				}
				elapsedSteps++;
				return false;
			}
			
			if (activated && event.getAction() != MotionEvent.ACTION_UP) {
				if (Math.abs(x - startX) >= minimumSwipeError) {
					started = true;
				}
			}
			
			if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
				
				if (!activated || !started) {
					if (!editorVisible) delayHandler.redelay(ReorderDelay);
					return false;
				}
				
				if (activated && ran) {
					if ((shouldFling || Math.abs(x - startX) > minimumSwipeDistance)
							&& event.getAction() == MotionEvent.ACTION_UP) {
						list.requestDisallowInterceptTouchEvent(false);
						ViewCompat.setHasTransientState(view, true);
	
						final ViewHolder Holder = (ViewHolder) view.getTag();
						
						final View postView = view;
						float distanceRatio = (Holder.itemRoot.getWidth() - Math.abs(Holder.itemRoot.getTranslationX()))/Holder.itemRoot.getWidth();
						if (distanceRatio < 0) distanceRatio = 0.01f;
						float speedRatio;
						if (shouldFling) {
							speedRatio = 2000/speed;
						}
						else {
							speedRatio = 0.5f;
						}
						
						// Block touch events until this animation has completed
						final View TouchBlocker = new View(activity);
						TouchBlocker.setOnTouchListener(nullOnTouchListener);
						root.addView(TouchBlocker);
						
						//Log.d(TAG, "Speed is: " + speed + "; speed ratio is: " + speedRatio);
						
						Holder.itemRoot.animate()
							.translationX(Math.signum(speedRatio) * Holder.itemRoot.getWidth()).alpha(0)
							.setDuration((long)(Math.signum(speedRatio) * 400 * distanceRatio * speedRatio))
							.setInterpolator(new DecelerateInterpolator())
							.setListener(new AnimatorListenerAdapter() {
								public void onAnimationEnd(Animator a) {
									if (activity == null) return;
									Holder.itemRoot.animate().setListener(null);
									ViewCompat.setHasTransientState(postView, false);
									
									//Enable touch events once again
									root.removeView(TouchBlocker);
									
									if (confirmingPosition != ListView.INVALID_POSITION)
										confirmDeletion();
									
									confirmingPosition = encode(Holder.id);
									
									final View helper = new View(activity);
									root.addView(helper);
									helper.setOnTouchListener(deleteConfirmator);
									helper.setId(R.id.HistoryDate2);

									// This view is now an anchor. An anchor has the unique property that all views after it
									// have their ids decremented by one
									anchors.add(encode(Holder.id));
									
									//Update ids post-anchor creation
									adapter.notifyDataSetChanged();
								}
							});
					}
					else {
						delayHandler.redelay(ReorderDelay);
						
						list.requestDisallowInterceptTouchEvent(false);
						ViewCompat.setHasTransientState(view, true);
	
						final ViewHolder Holder = (ViewHolder) view.getTag();
						
						final View postView = view;
						
						Holder.itemRoot.animate()
							.translationX(0).alpha(1).setDuration(200)
							.setListener(new AnimatorListenerAdapter() {
								public void onAnimationEnd(Animator a) {
									if (activity == null) return;
									postView.setOnLongClickListener(itemLongClickListener);
									postView.setOnClickListener(itemClickListener);
									postView.setClickable(true);
									postView.setLongClickable(true);
									postView.setPressed(false);
									postView.setBackgroundResource(R.drawable.list_background_drawable);
									Holder.itemRoot.setBackgroundResource(0);
									Holder.deleteRoot.setVisibility(View.GONE);
									//postView.setEnabled(true);
									ViewCompat.setHasTransientState(postView, false);
								}
							});
					}
					return true;
				}
				
				return false;
			}
			
			if (!activated || !started)
				return false;
			
			if (started && activated && event.getAction() == MotionEvent.ACTION_MOVE) {

				ViewHolder holder = (ViewHolder) view.getTag();
				
				if (!ran) {
					holder.itemRoot.setBackgroundColor(getResources().getColor(android.R.color.white));
					holder.deleteRoot.setVisibility(View.VISIBLE);
					if (editorVisible) {
						commitEditorInstantly();
					}
					delayHandler.delayIndefinitely();
				}
				
				view.setOnClickListener(null);
				view.setOnLongClickListener(null);
				view.setClickable(false);
				view.setLongClickable(false);
				view.setBackgroundResource(0);
				view.setPressed(false);

				ran = true;
				
				list.requestDisallowInterceptTouchEvent(true);
				
	            float alpha = Math.abs(holder.itemRoot.getTranslationX())/minimumSwipeDistance;
	            if (alpha > 1) alpha = 1;
	            alpha = 1 - alpha;
	            if (alpha < 0.1f) alpha = 0.1f;
	            
	            holder.itemRoot.setTranslationX(holder.itemRoot.getTranslationX() + x - previousX);
				holder.itemRoot.setAlpha(alpha);
	            
				previousX = x;
				previousY = y;
				
				return true;
			}
			
			return false;
		}
	};
	
	private float speed;

    class GestureDetector extends SimpleOnGestureListener { 

        @Override 
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) { 
            if(Math.abs(velocityX) > minimumSwipeSpeed) { 
            	if (Math.signum(velocityX) == Math.signum(e2.getX() - e1.getX())) {
            		speed = velocityX;
                    return true;
            	}
            }
            return false; 
        } 

    } 
    
    //******************* REORDERING ***********************
    
    public void notifyOrderingRuleChanged() {
    	if (ReceiptActivity.reorderItems) {
    		delayHandler.registerCallback(ReorderRunnable, ReorderDelay);
    	}
    	else {
    		delayHandler.unregisterCallback();
    	}
    }

	
    final static long ReorderLength = 350;
    
    static class DelayHandler {
    	
    	private Handler handler = new Handler();
    	Runnable callback;
    	
    	public void registerCallback(Runnable r, long delay) {
    		if (DEBUG_HANDLER) Log.d(TAG, "Callback registered!");
    		if (callback != null)
    			handler.removeCallbacks(callback);
    		callback = r;
    		handler.postDelayed(r, delay);
    	}
    	
    	public void unregisterCallback() {
    		if (DEBUG_HANDLER) Log.d(TAG, "Callback unregistered!");
    		if (callback != null) {
    			handler.removeCallbacks(callback);
    			callback = null;
    		}
    	}
    	
    	public void redelay(long delay) {
    		if (DEBUG_HANDLER) Log.d(TAG, "Callback postponed!");
    		if (callback != null) {
    			handler.removeCallbacks(callback);
    			handler.postDelayed(callback, delay);
    		}
    	}
    	
    	public void delayIndefinitely() {
    		if (DEBUG_HANDLER) Log.d(TAG, "Callback postponed indefinitely!");
    		if (callback != null) {
    			handler.removeCallbacks(callback);
    		}
    	}
    	
    	@Deprecated
    	public boolean postDelayed(Runnable r, long delay) {
    		return handler.postDelayed(r, delay);
    	}
    	
    	@Deprecated
    	public void removeCallbacks(Runnable r) {
    		handler.removeCallbacks(r);
    	}
    	
    }
    
	private DelayHandler delayHandler = new DelayHandler();
	
	protected int translate(int value, int translateAmount) {
		return value - translateAmount;
	}
	
	static class AnimatorEndHook {
		int animationCount;
		Runnable callback;
		
		void onAnimationFinished() {
			animationCount--;
			if (animationCount == 0)
				callback.run();
		}
		
	}
	
	//TranslateOut is a more complex animation changing the LayoutParams of an item moving offscreen
	@SuppressWarnings("deprecation")
	protected void translateOut(int position, final AnimatorEndHook hook) {
		final View MovingView = list.getChildAt(position);
		MovingView.setVisibility(View.INVISIBLE);
		ViewCompat.setHasTransientState(MovingView, true);

		final AnimatorHolder holder = new AnimatorHolder();
		animators.add(holder);
		
		final int ViewHeight = MovingView.getHeight();
		final int ViewWidth = MovingView.getWidth();
		
		final View Ghost = new View(activity);
		Bitmap bitmap = Bitmap.createBitmap(ViewWidth, ViewHeight, Bitmap.Config.ARGB_8888);
		MovingView.draw(new Canvas(bitmap));
		Ghost.setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
		
		listRoot.addView(Ghost, ViewWidth, ViewHeight);
		Ghost.setY(MovingView.getTop());
		Ghost.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		
		Ghost.animate()
			.translationYBy(list.getHeight())
			.setDuration(ReorderLength);
		
		//This part takes care of the view auto-translation by shrinking the animated view's real item
		ValueAnimator animator = ValueAnimator.ofInt(ViewHeight, 2);
		animator.setDuration(ReorderLength);
		animator.addUpdateListener(new AnimatorUpdateListener() {
			@Override
			public void onAnimationUpdate(ValueAnimator arg0) {
				MovingView.getLayoutParams().height = (Integer) arg0.getAnimatedValue();
				MovingView.requestLayout();
			}
		});
		animator.addListener(new AnimatorListenerAdapter() {
			boolean cancelled;
			
			public void onAnimationCancel(Animator a) {
				//cancelled = true;
			}
			
			public void onAnimationEnd(Animator a) {
				MovingView.setVisibility(View.VISIBLE);
				MovingView.getLayoutParams().height = ViewHeight;
				MovingView.requestLayout();
				MovingView.setLayerType(View.LAYER_TYPE_NONE, null);
				ViewCompat.setHasTransientState(MovingView, false);
				
				listRoot.removeView(Ghost);
				
				if (!cancelled) removeHolder(holder);
				
				hook.onAnimationFinished();
			}
		});
		holder.animator = animator;
		animator.start();
		
	}
	
	//TranslateTo moves an item by changing its translateY property by (newPosition - oldPosition) * 48dp amount
	private void translateTo(int oldPosition, int newPosition, final AnimatorEndHook hook) {
		final View MovingView = list.getChildAt(oldPosition);
		ViewCompat.setHasTransientState(MovingView, true);
		MovingView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
		
		final int ViewHeight = list.getChildAt(0).getHeight() - 1;
		
		final AnimatorHolder holder = new AnimatorHolder();
		animators.add(holder);
		
		MovingView.animate()
			.translationYBy((newPosition - oldPosition) * ViewHeight)
			.setDuration(ReorderLength)
			.setStartDelay(0)
			.setInterpolator(new AccelerateDecelerateInterpolator())
			.setListener(new AnimatorListenerAdapter() {
				
				boolean cancelled;
				
				public void onAnimationStart(Animator a) {
					holder.animator = a;
				}
				public void onAnimationCancel(Animator a) {
					//cancelled = true;
				}
				public void onAnimationEnd(Animator a) {
					MovingView.animate().setListener(null);
					MovingView.setLayerType(View.LAYER_TYPE_NONE, null);
					ViewCompat.setHasTransientState(MovingView, false);
					MovingView.setTranslationY(0);
					
					if (!cancelled) removeHolder(holder);
					
					hook.onAnimationFinished();
				}
			});
	}
	
	private final Runnable ReorderRunnable = new Runnable() {
		public void run() {
			if (!ReceiptActivity.reorderItems || anchors.size() != 0) return;
			if (items.size() == 0) return;
			
			delayHandler.unregisterCallback();
			
			// Reordering ONLY happens when there have been no interactions for a while
			// Thus, it is guaranteed that there will be no anchors, no pending new items
			// and no pending deletion confirmations
			
			final ArrayList<Item> sortedItems = new ArrayList<Item>(items);
			final ArrayList<Item> items = ItemListFragment.this.items;
			ItemListFragment.this.items = sortedItems;
			
			Collections.sort(sortedItems, new Comparator<Item>() {
				@Override
				public int compare(Item lhs, Item rhs) {
					if (lhs.crossedOff == rhs.crossedOff) return 0;
					if (lhs.crossedOff) return 1;
					else return -1;
				}
			});
			
			final int firstVisibleItem = list.getFirstVisiblePosition();
			final int lastVisibleItem = list.getLastVisiblePosition();
			
			int uncrossedItem = ListView.INVALID_POSITION;
			boolean hasCrossedItems = false;

			boolean lastUncrossedItemSet = false;
			int lastUncrossedItem = ListView.INVALID_POSITION;
			
			final int ViewHeight = list.getChildAt(0).getHeight() - 1;
			
			for (int i = lastVisibleItem; i >= firstVisibleItem; i--) {
				if (items.get(i).crossedOff == false) {
					uncrossedItem = i;
					
					if (!lastUncrossedItemSet) {
						lastUncrossedItemSet = true;
						lastUncrossedItem = i;
					}
				}
				else {
					hasCrossedItems = true;
				}
			}
			
			final int firstUncrossedItem = uncrossedItem;
			
			final int startingOffset = list.getChildAt(0).getTop();
			
			if (firstUncrossedItem == ListView.INVALID_POSITION || !hasCrossedItems) {
//				list.smoothScrollToPositionFromTop(sortedItems.indexOf(items.get(firstVisibleItem)), startingOffset, 0);
				list.setSelectionFromTop(sortedItems.indexOf(items.get(firstVisibleItem)), startingOffset);
				adapter.notifyDataSetChanged();
			}
			else {
				
				//Compute the new positions for each item
				final int positionTranslation = firstUncrossedItem - firstVisibleItem;
				final int firstTranslatedPosition = sortedItems.indexOf(items.get(firstUncrossedItem));
				
				//Stop scrolling and prevent further scrolling until animations have completed
				list.setScrollingEnabled(false);
				list.smoothScrollBy(1, 0);
				
				boolean autoTranslation = false;
//				int translationAmount = 0;
				int newPosition;
				int itemsToInflate = 0;
				final AnimatorEndHook hook = new AnimatorEndHook();
				for (int i = firstVisibleItem; i <= lastVisibleItem; i++) {
					newPosition = firstUncrossedItem + (sortedItems.indexOf(items.get(i)) - firstTranslatedPosition - positionTranslation);
					if (newPosition != i) {
						if (items.get(i).crossedOff) {
							if (newPosition > lastVisibleItem) {
//								if (autoTranslation)
//									translationAmount++;
//								autoTranslation = true;
//								translateOut(i - firstVisibleItem, hook);
								translateTo(i - firstVisibleItem, newPosition - firstVisibleItem, hook);
								hook.animationCount++;
								itemsToInflate++;
							}
							else {
//								translationAmount = 0;
//								autoTranslation = false;
								translateTo(i - firstVisibleItem, newPosition - firstVisibleItem, hook);
								hook.animationCount++;
							}
						}
						else {
							// Uncrossed items may NOT go out of view
							if (autoTranslation) continue;
							else {
								translateTo(i - firstVisibleItem, newPosition - firstVisibleItem, hook);
								hook.animationCount++;
							}
						}
					}
				}
				
				Item lastItem = items.get(lastUncrossedItem);
				int startingPoint = sortedItems.indexOf(lastItem) + 1;
				LayoutInflater inflater = activity.getLayoutInflater();
				
				for (int i = 0; i < itemsToInflate; i++) {
//					newPosition = lastVisibleItem - itemsToInflate + i + 1;
					newPosition = firstUncrossedItem + (startingPoint + i - firstTranslatedPosition - positionTranslation);
					
					final View view = inflater.inflate(R.layout.layout_list_item, listRoot, false);
					listRoot.addView(view);
					view.setY(list.getChildAt(0).getY() + (lastVisibleItem - firstVisibleItem + 2 + i) * 48 * metrics.density);
					generateViewHolder(view);
					decorateView(view, sortedItems.get(startingPoint + i));
					view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
					
					final AnimatorHolder holder = new AnimatorHolder();
					animators.add(holder);
					
					hook.animationCount++;
					
					view.animate()
						.y(list.getChildAt(0).getY() + (newPosition - firstVisibleItem) * ViewHeight)
						.setDuration(ReorderLength)
						.setListener(new AnimatorListenerAdapter() {
							public void onAnimationStart(Animator a) {
								holder.animator = a;
							}
							public void onAnimationEnd(Animator a) {
								listRoot.removeView(view);
								hook.onAnimationFinished();
								animators.remove(holder);
							}
						});
				}
				
				final View TouchBlocker = new View(activity);
				
				hook.callback = new Runnable() {
					@Override
					public void run() {
						if (activity == null) return;
						list.setScrollingEnabled(true);
						adapter.notifyDataSetChanged();
						list.setSelectionFromTop(sortedItems.indexOf(items.get(firstUncrossedItem)), startingOffset);
						listRoot.removeView(TouchBlocker);
//						list.smoothScrollToPositionFromTop(sortedItems.indexOf(items.get(firstUncrossedItem)), startingOffset, 0);
					}
				};
				
				if (hook.animationCount == 0) {
					hook.callback.run();
				}
				else {
					TouchBlocker.setOnTouchListener(nullOnTouchListener);
					listRoot.addView(TouchBlocker);
				}
				
			}
		}
	};
	

	protected final Runnable InstantReorderRunnable = new Runnable() {
		public void run() {
			if (!ReceiptActivity.reorderItems) return;
			Collections.sort(items, new Comparator<Item>() {
				@Override
				public int compare(Item lhs, Item rhs) {
					if (lhs.crossedOff == rhs.crossedOff) return 0;
					if (lhs.crossedOff) return 1;
					else return -1;
				}
			});
			adapter.notifyDataSetChanged();
		}
	};
	
	//*************** AUTO-GENERATION OF EXTRA VIEWS
	//*************** TO PREVENT SLOWDOWNS FROM LAYOUTPARAMS
	//*************** ANIMATIONS
	
	public void generateViewHolder (View view) {

		ViewHolder holder = new ViewHolder();
		holder.title = (TextView)view.findViewById(R.id.ItemTitle);
		holder.qty = (TextView)view.findViewById(R.id.QtyTitle);
		holder.price = (TextView)view.findViewById(R.id.PriceTitle);
		holder.strikethrough = view.findViewById(R.id.ItemStrikethrough);
		holder.itemRoot = view.findViewById(R.id.ItemRoot);
		holder.deleteRoot = view.findViewById(R.id.DeleteStrip);
		view.setTag(holder);
		
	}
	
	public void decorateView (View view, Item item) {
		ViewHolder holder = (ViewHolder) view.getTag();
		
		holder.title.setText(item.name);
		if ((item.flags & SetQty) == 0) {
			holder.qty.setText("1.0" + item.unitOfMeasurement);
			holder.qty.setTextColor(getResources().getColor(R.color.implicit_text_colors));
		}
		else {
			holder.qty.setText(ReceiptActivity.quantityFormattedString(activity, item.qty, item.unitOfMeasurement));
			holder.qty.setTextColor(getResources().getColor(android.R.color.black));
		}
		if ((item.flags & SetPrice) == 0) {
			if (item.estimatedPrice == 0)
				holder.price.setText(ReceiptActivity.currentLocale);
			else
				holder.price.setText(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(item.estimatedPrice));
			holder.price.setTextColor(getResources().getColor(R.color.implicit_text_colors));
		}
		else {
			holder.price.setText(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(item.price));
			holder.price.setTextColor(getResources().getColor(android.R.color.black));
		}
		
		if (item.crossedOff) {
			holder.title.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
			holder.qty.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
			holder.price.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
			holder.strikethrough.setVisibility(View.VISIBLE);
		}
		else {
			holder.title.setTextColor(getResources().getColor(android.R.color.black));
			holder.strikethrough.setVisibility(View.GONE);
		}
	}
	
	//******************* MISC *****************************
	
	public void refreshActionMode() {
		if (selectionList.size() > 0) {
			if (actionMode != null) {
				actionMode.setSubtitle(ReceiptActivity.currentLocale + selectionTotal
						.add(selectionTotal
								.multiply(new BigDecimal(activity.getTax())
									.movePointLeft(4)))
						.setScale(2, RoundingMode.HALF_EVEN) + " total");
			}
		}
	}

}
