package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Rect;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.AbsoluteSizeSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import com.BogdanMihaiciuc.receipt.IndicatorFragmentNonCompat.Task;
import com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Item;
import com.BogdanMihaiciuc.receipt.ItemCollectionFragment.PartialCheckoutItems;
import com.BogdanMihaiciuc.util.CollectionEventDelegate;
import com.BogdanMihaiciuc.util.CollectionPopover;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.EventTouchListener;
import com.BogdanMihaiciuc.util.ExtendedFragment;
import com.BogdanMihaiciuc.util.FloatingActionButton;
import com.BogdanMihaiciuc.util.Glyph;
import com.BogdanMihaiciuc.util.GlyphDrawable;
import com.BogdanMihaiciuc.util.LabelDrawable;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyActionBarView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.LegacySnackbar;
import com.BogdanMihaiciuc.util.ListenableEditText;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.TooltipPopover;
import com.BogdanMihaiciuc.util.Utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

public class HeaderFragment extends ExtendedFragment implements BackendFragment.OnBalanceStateChangeListener, LegacyActionBar.OnLegacyActionSelectedListener {

    final static String TAG = HeaderFragment.class.getName();

	public final static String HeaderItemMetadataKey = "HeaderFragment$HeaderItem";

	final static float PaddingPhone = 8;
	final static float PaddingTablet = 16;
	final static float PaddingTabletLandscape = 20;
	
	final static TimeInterpolator Accelerate = new AccelerateInterpolator(1.5f);
	final static TimeInterpolator Decelerate = new DecelerateInterpolator(1.5f);
	
	final static int PasteAll = Integer.MAX_VALUE;
	final static int ClearClipboard = Integer.MAX_VALUE - 1;

    static class CheckoutInformation {
		PartialCheckoutItems items;
		BigDecimal total;
		BigDecimal budget;
		long date;
		int itemCount;
		int tax;
        String name;
		
		public void commit(ReceiptActivity activity) {
			activity.checkout(date, items.items, itemCount, total, budget, tax, name);
		}
		
		private CheckoutInformation() {}
		
		static CheckoutInformation make(PartialCheckoutItems items, BigDecimal total, BigDecimal budget, long date, int itemCount, int tax, String name) {
			CheckoutInformation info = new CheckoutInformation();
				info.items = items;
				info.total = total;
				info.budget = budget;
				info.date = date;
				info.itemCount = itemCount;
				info.tax = tax;
                info.name = name;
			return info;
		}
	}
	
	static ArrayList<Item> clipboard = new ArrayList<Item>();
	private ArrayList<Item> scheduledAppendToClipboard = null;
	
	private boolean pasteVisible;
	
	private ReceiptActivity activity;
	private ViewGroup root;
	
	private TextView title;
	private View background;
//    private ImageView checkoutButton;

    private View titleEditButton;
    private ListenableEditText titleEditor;
    private View titleEditDone;
    private View titleEditIcon;

    private FloatingActionButton checkoutFAB;
	private TextView header;
    private View checkoutTouchZone;
	private ImageView pasteButton;
    private TextView addButton;

    private TextView count;
    private GlyphDrawable countDoneDrawable;

    private int backgroundColor;
    private int glyphColor;

    private int headerBackgroundColor = 0x00FFFFFF;
	
	//SidebarMode exclusive views
    //SidebarMode is dead
//	private boolean sidebarMode;
//	private ImageView overflowButton;
//	private ImageView shareButton;
//	private ImageView historyButton; //This is technically outside the HeaderFragment, but it's still managed by it
	
	private View content;

	private View contentRoot;
	
	private View undoHelper;
	
	private DisplayMetrics metrics;

    private int balanceState;
	
	private float translationX;
	private float minimumUnitSwipeDistance;
	private boolean initial = true;
	
	private boolean loading = false;
	
	private boolean trackingUndo = false;
	private CheckoutInformation pendingCheckout;
	
	private PopupMenu clipboardPopup;
    private CollectionPopover popover;

    private CheckoutStateManager checkoutStateManager = new CheckoutStateManager();

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		ViewGroup returned = (ViewGroup) inflater.inflate(R.layout.fragment_header, container, false);

        View addButton = returned.findViewById(R.id.AddItemButton);
        View addFeedback = new View(inflater.getContext());
        addFeedback.setLayoutParams(addButton.getLayoutParams());
        addFeedback.setId(R.id.AddFeedbackView);
        addFeedback.setBackgroundColor(getResources().getColor(android.R.color.holo_blue_light));
        addFeedback.setVisibility(View.INVISIBLE);
        ((ViewGroup) returned.getChildAt(0)).addView(addFeedback, 0);

        return returned;
	}
	
	private OnClickListener checkoutListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			activity.onCheckoutPressed();
		}
	};
	
	private OnClickListener undoListener = new OnClickListener() {
		@Override
		public void onClick(View v) {
			activity.onUndoPressed(pendingCheckout);
			trackingUndo = false;
			pendingCheckout = null;
            checkoutFAB.setGlyph(Glyph.GlyphDone);
            checkoutFAB.setTitle(getString(R.string.CheckoutTitle));
			reinit();
			root.removeView(undoHelper);
			undoHelper = null;
            checkoutStateManager.setUndoVisible(false, true);
		}
	};
	
	private OnTouchListener checkoutConfirmator = new OnTouchListener() {
		@Override
		public boolean onTouch(View arg0, MotionEvent event) {
			if (activity == null) return false;
			
			Rect r = new Rect();
			checkoutFAB.getGlobalVisibleRect(r);
			if (event.getRawX() > r.right || event.getRawX() < r.left || event.getRawY() < r.top || event.getRawY() > r.bottom) {
				trackingUndo = false;
				pendingCheckout.commit((ReceiptActivity) arg0.getContext());
				pendingCheckout = null;
                checkoutFAB.setGlyph(Glyph.GlyphDone);
                checkoutFAB.setTitle(getString(R.string.CheckoutTitle));
				reinit();
				root.removeView(arg0);
				undoHelper = null;
                checkoutStateManager.setUndoVisible(false, true);
				return false;
			}
			Rect outerBounds = new Rect(r);
			outerBounds.inset((int) (- 16 * metrics.density), (int) (- 16 * metrics.density));
			
			// ERROR OF MARGIN
			if (outerBounds.contains((int) event.getRawX(), (int) event.getRawY()))
				if (!r.contains((int) event.getRawX(), (int) event.getRawY()))
					return true;
			
			return false;
		}
		
	};

	private OnLongClickListener pasteTooltip = new OnLongClickListener() {
		@Override
		public boolean onLongClick(View v) {

			new TooltipPopover(pasteButton.getResources().getString(R.string.PasteHint),
					clipboard.size() == 1 ? getString(R.string.PasteHintDescriptionSingle) : getString(R.string.PasteHintDescriptionMultiple, clipboard.size()),
					new Popover.AnchorProvider() {
						@Override
						public View getAnchor(Popover popover) {
							return pasteButton;
						}
					}).show(getActivity());

//					Rect rect = new Rect();
//					v.getGlobalVisibleRect(rect);
//					Toast toast = Toast.makeText(getActivity(), R.string.PasteHint, Toast.LENGTH_SHORT);
//					toast.setGravity(Gravity.TOP|Gravity.LEFT, rect.left, rect.top + rect.height()/2);
//					toast.show();
			return true;
		}
	};
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		activity = (ReceiptActivity) getActivity();

        metrics = getResources().getDisplayMetrics();

		title = (TextView) activity.findViewById(R.id.HeaderTitle);
        titleEditButton = activity.findViewById(R.id.HeaderTitleEditButton);
        titleEditor = (ListenableEditText) activity.findViewById(R.id.HeaderTitleEditor);
        titleEditDone = activity.findViewById(R.id.HeaderTitleDone);
        titleEditIcon = activity.findViewById(R.id.NameEditor);
		addButton = (TextView) activity.findViewById(R.id.AddItemButton);
		pasteButton = (ImageView) activity.findViewById(R.id.PasteButton);
//        checkoutButton = (ImageView) activity.findViewById(R.id.CheckoutButton);
        checkoutFAB = (FloatingActionButton) activity.findViewById(R.id.ScrapFAB);
        checkoutFAB.setTitle(getString(R.string.CheckoutTitle));
        checkoutTouchZone = checkoutFAB;
        count = (TextView) activity.findViewById(R.id.HeaderCount);
		header = title;
		header.animate().setDuration(200);
		background = activity.findViewById(R.id.HeaderBackground);
		contentRoot = activity.findViewById(android.R.id.content);
		content = ((ViewGroup)activity.findViewById(android.R.id.content)).getChildAt(0);

		$(title, pasteButton, count).metadata(HeaderItemMetadataKey, "");

        ((ViewGroup) titleEditButton.getParent()).setClipChildren(false);
        titleEditButton.setBackground(new LegacyRippleDrawable(activity).setShape(LegacyRippleDrawable.ShapeCircle));
        titleEditDone.setBackground(new LegacyRippleDrawable(activity, LegacyRippleDrawable.ShapeRoundRect));
        title.setTypeface(Receipt.condensedTypeface());

        titleEditor.setTypeface(Receipt.condensedTypeface());
        titleEditor.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.HeaderTextSize));
        titleEditor.setTextColor(getResources().getColor(R.color.DashboardText));
        titleEditor.setHintTextColor(getResources().getColor(R.color.DashboardTitle));
        titleEditor.setPadding(titleEditor.getPaddingLeft() - (int) (4 * metrics.density + 0.5f), 0, titleEditor.getPaddingRight(), 0);

        titleEditor.addTextChangedListener(titleEditorListener);
        titleEditor.setOnKeyPreImeListener(titleKeyPreImeListener);
        titleEditor.setOnFocusChangeListener(titleFocusListener);
        titleEditor.setOnEditorActionListener(titleEditorActionListener);

        count.setBackground(new LabelDrawable(activity));
        countDoneDrawable = new com.BogdanMihaiciuc.util.GlyphDrawable(activity, Glyph.GlyphDone).setIntrinsicSize((int) (metrics.density * 16 + 0.5f));
        countDoneDrawable.setColor(0xFFFFFFFF);
		
		root = (ViewGroup) activity.getWindow().getDecorView();

		minimumUnitSwipeDistance = getResources().getDimensionPixelSize(R.dimen.ActionBarSize);
		
		if (clipboard.size() == 0) {
			pasteButton.setVisibility(View.INVISIBLE);
			pasteVisible = false;
		}
		else {
			pasteButton.setVisibility(View.VISIBLE);
			pasteVisible = true;
		}
		
		float padding;
		Configuration config = getResources().getConfiguration();
		if (config.smallestScreenWidthDp < 600) {
			padding = PaddingPhone ;//- 2 * metrics.density;
		}
		else {
			if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
				padding = PaddingTabletLandscape;
			}
			else {
				padding = PaddingTablet;
			}
		}
		translationX = - getResources().getDimensionPixelSize(R.dimen.SecondaryKeyline);
		
		if (config.smallestScreenWidthDp < 600 || config.orientation != Configuration.ORIENTATION_LANDSCAPE)
			addButton.setOnLongClickListener(new OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					Rect rect = new Rect();
					v.getGlobalVisibleRect(rect);
					Toast toast = Toast.makeText(getActivity(), R.string.AddButtonHint, Toast.LENGTH_SHORT);
					toast.setGravity(Gravity.TOP|Gravity.LEFT, rect.left, rect.top + rect.height()/2);
					toast.show();
					return true;
				}
			});

		pasteButton.setOnLongClickListener(pasteTooltip);

        addButton.setOnTouchListener(advancedAddListener);
		
		pasteButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                pasteAll();
            }
        });

		pasteButton.setOnTouchListener(advancedPasteListener);
		
		checkoutTouchZone.setOnClickListener(checkoutListener);
		
//		checkoutTouchZone.setOnLongClickListener(new OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View v) {
//                Rect rect = new Rect();
//                v.getGlobalVisibleRect(rect);
//                Toast toast = Toast.makeText(getActivity(), R.string.menu_checkout, Toast.LENGTH_SHORT);
//                toast.setGravity(Gravity.TOP | Gravity.LEFT, rect.left, rect.top + rect.height() / 2);
//                toast.show();
//                return true;
//            }
//        });

        titleEditButton.setOnClickListener(titleEditButtonListener);
        titleEditDone.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                dismissTitleEditor();
            }
        });

        activity.getBackendFragment().addOnBalanceStateChangeListener(this);
        balanceState = activity.getBackendFragment().getBalanceState();
		
		if (loading) {
			onLoadingStarted();
			//header.setAlpha(0.40f);
		}
		else {
            checkoutFAB.setBackgroundAndGlyphColors(this.backgroundColor, this.glyphColor, false);
            int color = backgroundColor;
            if (backgroundColor == 0xFFFFFFFF) {
                color = 0x44000000;
            }
            ((LabelDrawable) count.getBackground()).setColor(color, labelAnimationsEnabled);

			reinit();
		}
		
		if (trackingUndo) {
			undoHelper = new View(activity);
			undoHelper.setOnTouchListener(checkoutConfirmator);
			root.addView(undoHelper);
			setUndoMode();
		}
		
		if (scheduledAppendToClipboard != null) {
            appendToClipboard(scheduledAppendToClipboard);
        }
		
	}
	
	public void onPause() {
		if (clipboardPopup != null) {
            clipboardPopup.dismiss();
        }

		if (!activity.isChangingConfigurations() && trackingUndo) {
			trackingUndo = false;
			pendingCheckout.commit((ReceiptActivity) getActivity());
			pendingCheckout = null;
            checkoutFAB.setGlyph(Glyph.GlyphDone);
            checkoutFAB.setTitle(getString(R.string.CheckoutTitle));
			reinit();
			root.removeView(undoHelper);
			undoHelper = null;
            checkoutStateManager.setUndoVisible(false, true);
		}

		super.onPause();
	}
	
	
	@Override
	public void onDetach() {

		activity = null;
		
		title = null;
        titleEditButton = null;
        titleEditor = null;
        titleEditDone = null;
        titleEditIcon = null;
		background = null;
		addButton = null;
//        checkoutButton = null;
        checkoutFAB = null;
        checkoutTouchZone = null;
		header = null;
		pasteButton = null;
        count = null;
        countDoneDrawable = null;
		
//		shareButton = null;
//		overflowButton = null;
		
		content = null;
		contentRoot = null;
		
		root = null;
		undoHelper = null;
		
		super.onDetach();

	}
	
	public void initNow() {

		loading = false;
		
		if (activity != null) {
            title.setText(activity.getName());
			addButton.setEnabled(true);
			if (pasteVisible) pasteButton.setEnabled(true);
			reinit();
		}
		
	}
	
	public void delayInit() {
		loading = true;
		if (activity != null) onLoadingStarted();
	}
	
	public void setInitial(boolean initial) {
		this.initial = initial;
	}
	
	public void recheckCheckout() {
        if (checkoutTouchZone != null) {
            checkoutTouchZone.setOnClickListener(checkoutListener);
//            checkoutTouchZone.setOnLongClickListener(new OnLongClickListener() {
//                @Override
//                public boolean onLongClick(View v) {
//                    Rect rect = new Rect();
//                    v.getGlobalVisibleRect(rect);
//                    Toast toast = Toast.makeText(getActivity(), R.string.menu_checkout, Toast.LENGTH_SHORT);
//                    toast.setGravity(Gravity.TOP | Gravity.LEFT, rect.left, rect.top + rect.height() / 2);
//                    toast.show();
//                    return true;
//                }
//            });
        }
		if (activity.getCrossedOffCount() > 0) {
			enableCheckout();
		}
		else {
			disableCheckout();
		}
	}
	
	public void enableCheckout() {
        if (((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar)).getCurrentContextMode() != null) {
            return;
        }

        checkoutStateManager.setCheckoutEnabled(true, true);
//        checkoutFAB.setEnabled(true);

		checkoutTouchZone.setEnabled(true);
//		if (content.getAlpha() > 0.9f && contentRoot.getAlpha() > 0.9f && !initial) {
//            checkoutButton.animate().translationX(0).alpha(1f).setInterpolator(Decelerate);
//        }
//		else {
//            checkoutButton.setTranslationX(0);
//            checkoutButton.setAlpha(1f);
//        }
	}
	
	public void disableCheckout() {

        checkoutStateManager.setCheckoutEnabled(false, true);

		checkoutTouchZone.setEnabled(false);
//		if (content.getAlpha() > 0.9f && contentRoot.getAlpha() > 0.9f && !initial) {
//            checkoutButton.animate().translationX(translationX).alpha(0f).setInterpolator(Accelerate);
//        }
//		else {
//            checkoutButton.setTranslationX(translationX);
//            checkoutButton.setAlpha(0f);
//        }
	}
	
	public void setActionModeStarted(boolean started) {
		if (started) {
			disableCheckout();
		}
		else {
			recheckCheckout();
		}
	}

    public void onPendingPartialCheckout() {
        checkoutStateManager.setUndoVisible(true, true);
    }
	
	public void setPartialCheckoutDone(CheckoutInformation pendingCheckout) {
		trackingUndo = true;
		this.pendingCheckout = pendingCheckout;
		undoHelper = new View(activity);
		undoHelper.setOnTouchListener(checkoutConfirmator);
		root.addView(undoHelper);
		setUndoMode();
	}
	
	public void setUndoMode() {
        checkoutStateManager.setUndoVisible(true, true);

        checkoutFAB.setGlyph(Glyph.GlyphUndo);
        checkoutFAB.setTitle(getString(R.string.UndoCheckoutHint));
        this.backgroundColor = Utils.transparentColor(0.75f, 0);
        this.glyphColor = 0xFFFFFFFF;
        checkoutFAB.setBackgroundAndGlyphColors(Utils.transparentColor(0.75f, 0), 0xFFFFFFFF, true);

        checkoutTouchZone.setOnClickListener(undoListener);
//        checkoutTouchZone.setOnLongClickListener(new OnLongClickListener() {
//            @Override
//            public boolean onLongClick(View v) {
//                Rect rect = new Rect();
//                v.getGlobalVisibleRect(rect);
//                Toast toast = Toast.makeText(getActivity(), R.string.UndoCheckoutHint, Toast.LENGTH_SHORT);
//                toast.setGravity(Gravity.TOP | Gravity.LEFT, rect.left, rect.top + rect.height() / 2);
//                toast.show();
//                return true;
//            }
//        });
        enableCheckout();
//        checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.undo_arrow_dark));
//		header.setCompoundDrawablesWithIntrinsicBounds(R.drawable.undo_arrow_dark, 0, 0, 0);
	}
	
	public void onLoadingStarted() {
		addButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.content_new_dark, 0, 0, 0);
		addButton.setTextColor(getResources().getColor(R.color.large_price));
		addButton.setEnabled(false);
		
		header.setTextColor(getResources().getColor(R.color.DashboardText));
//		checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_done_dark));

		pasteButton.setImageDrawable(getResources().getDrawable(R.drawable.paste_dark));
		if (pasteVisible) pasteButton.setEnabled(false);
		
//		if (sidebarMode) {
//			overflowButton.setImageResource(R.drawable.ic_action_overflow);
//			shareButton.setImageResource(R.drawable.ic_action_share_dark);
//		}
		
//		title.setTextColor(getResources().getColor(R.color.DashboardText));
		background.setBackgroundColor(headerBackgroundColor);
		title.setText(getResources().getString(R.string.LoadingTitle));
		
		checkoutTouchZone.setEnabled(false);
//		checkoutButton.setTranslationX(translationX);
//        checkoutButton.setAlpha(0f);
	}

    @Override
    public void onBalanceStateChanged(int fromState, int toState) {
        balanceState = toState;
        if (fromState == BackendFragment.BalanceStateError || toState == BackendFragment.BalanceStateError) {
            reinit();
        }
    }
	
	public void reinit() {
        if (activity == null) return; // not attached yet

        int backgroundColor = 0xFFFFFFFF;
        int glyphColor = Utils.transparentColor(0.75f, 0);

		Resources resources = getResources(); //cached response
		boolean budgetExceeded = activity.budgetIsExceeded();
        addButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.content_new_dark, 0, 0, 0);
        addButton.setTextColor(getResources().getColor(R.color.large_price));
        header.setTextColor(getResources().getColor(R.color.DashboardText));
        if (getResources() == null || pasteButton == null) {
            Log.e(ReceiptActivity.TAG, "Unexpected NULL paste button.");
            return;
        }
//        checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_done_dark));
//        pasteButton.setImageDrawable(getResources().getDrawable(R.drawable.paste_dark));
//        title.setTextColor(resources.getColor(R.color.DashboardText));
        background.setBackgroundColor(headerBackgroundColor);

        if (TextUtils.isEmpty(activity.getName())) {
            title.setText(getString(R.string.NewList));
        }
        else {
            title.setText(activity.getName());
        }
        titleEditor.setText(activity.getName());
		
//		if (sidebarMode) {
//			overflowButton.setImageResource(R.drawable.ic_action_overflow_light);
//			shareButton.setImageResource(R.drawable.ic_action_share);
//		}
		
		if (activity.canCheckout()) {
			if (!budgetExceeded) {
//                title.setText(resources.getString(R.string.CanCheckoutTitle));
                count.setText("");
                count.setCompoundDrawablesWithIntrinsicBounds(countDoneDrawable, null, null, null);

                if (balanceState != BackendFragment.BalanceStateError) {
//                    title.setTextColor(resources.getColor(R.color.HeaderCanCheckout));
//                    checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_done_blue));
                    backgroundColor = getResources().getColor(R.color.HeaderCanCheckout);
                    glyphColor = 0xFFFFFFFF;
                } // else it retains the black colors set above
			}
			else {
//				title.setText(resources.getString(R.string.BudgetExceededTitle));
//				title.setTextColor(getResources().getColor(R.color.HeaderOverBudget));

                count.setText("");
                count.setCompoundDrawablesWithIntrinsicBounds(countDoneDrawable, null, null, null);

//                checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_done_red));
                addButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.content_new_red, 0, 0, 0);
                backgroundColor = getResources().getColor(R.color.HeaderOverBudget);
                glyphColor = 0xFFFFFFFF;
			}
		}
		else if (activity.startedList()) {
//			title.setText(String.format(resources.getString(R.string.ItemsLeftTitle), activity.getRemainingItemCount()));
            count.setText("" + activity.getRemainingItemCount());
            count.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

			if (!budgetExceeded) {
//                title.setTextColor(resources.getColor(R.color.DashboardText));
//                checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_done_dark));
//				if (sidebarMode) {
//					overflowButton.setImageResource(R.drawable.ic_action_overflow);
//					shareButton.setImageResource(R.drawable.ic_action_share_dark);
//				}
			}
			else {
//				title.setTextColor(getResources().getColor(R.color.HeaderOverBudget));
//                checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_done_red));
                addButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.content_new_red, 0, 0, 0);
                backgroundColor = getResources().getColor(R.color.HeaderOverBudget);
                glyphColor = 0xFFFFFFFF;
			}
		}
		else {
//			title.setText(resources.getString(R.string.NewListTitle));
            count.setText("0");
            count.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);

			if (!budgetExceeded) {
//				title.setTextColor(resources.getColor(R.color.DashboardText));
//                checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_done_dark));
                addButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.content_new_dark, 0, 0, 0);
			}
			else {
//				title.setTextColor(getResources().getColor(R.color.HeaderOverBudget));
			}
		}
		
		if (!trackingUndo) {
            recheckCheckout();
            if (this.backgroundColor != backgroundColor || this.glyphColor != glyphColor) {
                postColorChange(backgroundColor, glyphColor);
            }
        }
		else {
            setUndoMode();
        }
		
		initial = false;
		
	}


    private boolean labelAnimationsEnabled = true;

    public void setLabelAndCheckoutAnimationsEnabled(boolean enabled, boolean checkoutEnabled) {
        labelAnimationsEnabled = enabled;
        if (count != null) {
            ((LabelDrawable) count.getBackground()).setAnimationsEnabled(enabled);
        }

        if (!checkoutEnabled) {
            if (checkoutFAB != null) {
                checkoutFAB.flushAnimations();
            }
        }
    }

    private final Runnable ColorChangeRunnable = new Runnable() {
        @Override
        public void run() {
            if (checkoutFAB != null) {
                checkoutFAB.setBackgroundAndGlyphColors(backgroundColor, glyphColor, labelAnimationsEnabled);
            }
            if (count != null) {
                int color = backgroundColor;
                if (backgroundColor == 0xFFFFFFFF) {
                    color = 0x44000000;
                }
                ((LabelDrawable) count.getBackground()).setColor(color, labelAnimationsEnabled);
            }
        }
    };

    public void postColorChange(final int backgroundColor, final int glyphColor) {
        this.backgroundColor = backgroundColor;
        this.glyphColor = glyphColor;

        handler.removeCallbacks(ColorChangeRunnable);
        handler.post(ColorChangeRunnable);
    }
	
	public void onCrossedOffCountChange(int newCount) {
		reinit();
	}
	
	final static int NotificationPauseDelay = 1000;
	boolean canPlayNotification = true;
	
	private Handler handler = new Handler();
	
	private Runnable pauseRunnable = new Runnable() {
		public void run() {
			canPlayNotification = true;
		}
	};
	
	private Runnable notificationPlayer = new Runnable() {
		public void run() {
			if (getActivity() == null) {
				handler.postDelayed(this, NotificationPauseDelay);
				return;
			}
			SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
			if (globalPrefs.getBoolean(SettingsFragment.PlayExceededAleryKey, false)) {
				try {
			        Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
			        Ringtone notificationSound = RingtoneManager.getRingtone(getActivity().getApplicationContext(), notification);
			        notificationSound.play();
			    } 
				catch (Exception e) {
					
				}
			}
		}
	};
	
	public void onBudgetExceeded() {
		
		if (canPlayNotification) {
			notificationPlayer.run();
			canPlayNotification = false;
			handler.postDelayed(pauseRunnable, NotificationPauseDelay);
		}

		addButton.setCompoundDrawablesWithIntrinsicBounds(R.drawable.content_new_red, 0, 0, 0);
		addButton.setTextColor(getResources().getColor(R.color.crossedoff_text_colors));
//        checkoutButton.setImageDrawable(getResources().getDrawable(R.drawable.ic_action_done_red));
        pasteButton.setImageDrawable(getResources().getDrawable(R.drawable.paste_dark));
//		if (activity.canCheckout()) title.setText(getResources().getString(R.string.BudgetExceededTitle));
//		background.setBackgroundColor(getResources().getColor(R.color.HeaderOverBudget));
//		title.setTextColor(getResources().getColor(R.color.HeaderOverBudget));
        postColorChange(getResources().getColor(R.color.HeaderOverBudget), 0xFFFFFFFF);
        checkoutFAB.flashColor(getResources().getColor(R.color.HeaderOverBudget));
		
//		if (sidebarMode) {
//			overflowButton.setImageResource(R.drawable.ic_action_overflow_light);
//			shareButton.setImageResource(R.drawable.ic_action_share);
//		}
		
	}
	
	public void onBudgetOK() {
		
		reinit();
		
	}
	
	public void appendToClipboard(ArrayList<Item> items) {
		if (activity == null) {
			scheduledAppendToClipboard = items;
			return;
		}
		scheduledAppendToClipboard = null;
		if (!PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean("continuousClipboard", true)) {
			clipboard.clear();
		}
		clipboard.addAll(items);
		if (!pasteVisible)
			showClipboard();
	}
	
	public void showClipboard() {
		if (pasteVisible) return;
		
		pasteVisible = true;
		
		pasteButton.setEnabled(true);
		pasteButton.setOnTouchListener(advancedPasteListener);
		
		pasteButton.setTranslationX(50 * metrics.density);
		pasteButton.setAlpha(0f);
		pasteButton.setVisibility(View.VISIBLE);
		
		pasteButton.animate()
			.alpha(1).translationX(0)
			.setDuration(200)
			.setInterpolator(Decelerate)
			.setListener(null);
		
	}
	
	public void pasteAll() {
		activity.appendItems(clipboard);
		clearClipboard();
	}
	
	public void clearClipboard() {
		clipboard = new ArrayList<Item>();
		
		pasteVisible = false;
		
		pasteButton.setEnabled(false);
		pasteButton.setOnTouchListener(null);
		
		pasteButton.animate()
			.alpha(0).translationX(50 * metrics.density)
			.setDuration(200)
			.setInterpolator(Accelerate)
			.setListener(new AnimatorListenerAdapter() {
                public void onAnimationEnd(Animator a) {
                    if (activity == null) return;
                    pasteButton.setVisibility(View.INVISIBLE);
                }
            });
	}
	

	private OnTouchListener advancedPasteListener = new OnTouchListener() {
		
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
				feedbackView = ((ViewGroup)view.getParent()).findViewById(R.id.PasteFeedbackView);
				feedbackView.setAlpha(0);
				feedbackView.setVisibility(View.VISIBLE);
				view.getParent().requestDisallowInterceptTouchEvent(true);
				return false;
			}
			if (event.getAction() == MotionEvent.ACTION_MOVE && 
					(event.getX() - startX > 10 * metrics.density ||
							event.getY() - startY > 10 * metrics.density)) {
				view.setPressed(false);
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
					feedbackView.setVisibility(View.INVISIBLE);
					feedbackView.setTranslationY(0);
					feedbackView = null;

                    final int Width = getResources().getConfiguration().smallestScreenWidthDp < 600 ?
                            (int) (264 * metrics.density + 0.5f) : (int) (360 * metrics.density + 0.5f);
                    CollectionViewController controller = new CollectionViewController() {

						private final Runnable SelectionBackStackEntry = new Runnable() {
							@Override
							public void run() {
								if (selectionWrapper != null) selectionWrapper.dismiss();
							}
						};

						private TreeMap<Integer, Item> pendingItems = new TreeMap<Integer, Item>();
						private ArrayList<Item> selection = new ArrayList<Item>();
						private LegacyActionBar.ContextBarWrapper selectionWrapper;
						LegacyActionBar.ContextBarListener selectionListener = new LegacyActionBar.ContextBarListener() {
							@Override
							public void onContextBarStarted() {
								((Utils.BackStack) getActivity()).persistentBackStack().pushToBackStack(SelectionBackStackEntry);
							}

							@Override
							public void onContextBarDismissed() {
								selectionWrapper = null;

								deselect();

								((Utils.BackStack) getActivity()).persistentBackStack().swipeFromBackStack(SelectionBackStackEntry);
							}

							@Override
							public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
								if (item.getId() == R.id.menu_delete) {
									deleteSelection();

									if (selectionWrapper != null) selectionWrapper.dismiss();
								}
							}
						};

						public void deselect() {
							selection.clear();

							getCollectionView().refreshViews();

							if (selectionWrapper != null) selectionWrapper.dismiss();
						}

						public void deleteSelection() {
							requestBeginTransaction();

							for (Item item : selection) {
								pendingItems.put(getSectionAtIndex(0).indexOfObject(item), item);
							}

							// this must be run twice in order to preserve the item indexes while building the pendingItems
							for (Item item : selection) {
								getSectionAtIndex(0).removeObject(item);
							}

							requestCompleteTransaction();

							LegacySnackbar.showSnackbarWithMessage(getString(R.string.DeleteOverlayTitle), new LegacySnackbar.SnackbarListener() {
								@Override
								public void onActionConfirmed(LegacySnackbar snackbar) {
									pendingItems.clear();
								}

								@Override
								public void onActionUndone(LegacySnackbar snackbar) {
									requestBeginTransaction();

									for (Map.Entry<Integer, Item> entry : pendingItems.entrySet()) {
										Item item = entry.getValue();

										getSectionAtIndex(0).addObjectToIndex(item, entry.getKey());
									}

									requestCompleteTransaction();

									pendingItems.clear();
								}
							}, getActivity());
						}

						public void onItemLongClicked(View view) {
							Item item = (Item) getObjectForView(view);



							int oldSize = selection.size();

							if (selection.contains(item)) {
								selection.remove(item);
								view.setSelected(false);
							}
							else {
								selection.add(item);
								view.setSelected(true);
							}

							Context context = getCollectionView().getContext();

							if (selectionWrapper == null) {
								selectionWrapper = popover.getHeader().createContextMode(selectionListener);

								selectionWrapper.addItem(R.id.PasteAll, context.getString(R.string.PasteSelection), R.drawable.ic_add_to_list, false, true);
								selectionWrapper.addItem(R.id.menu_delete, context.getString(R.string.DeleteLabel), R.drawable.ic_action_delete, false, true);

								selectionWrapper.start();
							}

							if (selection.size() == 0 && selectionWrapper != null) {
								selectionWrapper.dismiss();
							}

							if (selectionWrapper != null) selectionWrapper.setTitleAnimated(
									Utils.appendWithSpan(
											new SpannableStringBuilder(), context.getString(R.string.SelectionTitle, selection.size()), new AbsoluteSizeSpan(20, true)
									), selection.size() - oldSize
							);
						}

						final int RootViewUID = LegacyActionBarView.generateViewId();
						final int TitleViewUID = LegacyActionBarView.generateViewId();
						final int PriceViewUID = LegacyActionBarView.generateViewId();

                        @Override
                        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
                            Utils.DPTranslator dp = new Utils.DPTranslator(container.getResources().getDisplayMetrics().density);
                            int height = dp.get(48);

                            FrameLayout layout = new FrameLayout(container.getContext(), null, android.R.attr.borderlessButtonStyle);
                            layout.setBackground(Utils.getDeselectedColors(activity));
                            layout.setLayoutParams(new ViewGroup.LayoutParams(Width, height));
							layout.setId(RootViewUID);
                            TextView title = new TextView(container.getContext());
                            title.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.TextSize));
                            title.setTextColor(getResources().getColor(R.color.DashboardText));
                            title.setGravity(Gravity.CENTER | Gravity.LEFT);
                            title.setPadding(getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline), 0, 0, 0);
                            title.setId(TitleViewUID);
                            layout.addView(title, new WindowManager.LayoutParams(Width, height));
                            TextView price = new TextView(container.getContext());
                            price.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.TextSize));
                            price.setTextColor(getResources().getColor(R.color.DashboardTitle));
                            price.setGravity(Gravity.CENTER | Gravity.RIGHT);
                            price.setPadding(getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline), 0, dp.get(16), 0);
                            price.setId(PriceViewUID);
                            layout.addView(price, new WindowManager.LayoutParams(Width, height));

							EventTouchListener listener = EventTouchListener.listenerInContext(inflater.getContext());
							CollectionEventDelegate delegate = new CollectionEventDelegate(this) {

								@Override
								public boolean viewCanStartMoving(EventTouchListener listener, View view, Object object) {
									return selection.size() == 0;
								}

								@Override
								public void viewDidStartSwiping(EventTouchListener listener, View view, Object object, int viewType) {

								}

								@Override
								public void objectDidConfirmDeletion(EventTouchListener listener, Object object) {
									clipboard.remove(object);
								}

								@Override
								public CharSequence getDeletedLabelForObject(EventTouchListener listener, View view, Object object, int viewType) {
									return view.getResources().getString(R.string.DeleteOverlayTitle);
								}

								@Override
								public void objectDidUndoDeletion(EventTouchListener listener, Object object) {

								}
							};
							listener.setDelegate(delegate);

							((LegacyRippleDrawable) layout.getBackground()).setForwardListener(listener);

                            return layout;
                        }

						@Override
						protected void onAttachedToCollectionView(CollectionView collectionView) {
							collectionView.setLegacyRippleDrawableIDs(RootViewUID);
						}

						@Override
                        public void configureView(View view, final Object object, int viewType) {
                            view.setTag(object);
                            view.setOnClickListener(new OnClickListener() {
								@Override
								public void onClick(View view) {
									if (selection.size() > 0) {
										onItemLongClicked(view);
									}
									else {
										requestBeginTransaction();
										getSectionAtIndex(0).removeObject(object);
										getCollectionView().setDeleteInterpolator(CollectionView.StandardDeleteInterpolator);
										getCollectionView().setDeleteAnimator(getCollectionView().StandardDeleteAnimator);
										getCollectionView().setDeleteAnimationDuration(200);
										requestCompleteTransaction();
										clipboardPopupListener.onClick(view);
									}
								}
							});
							view.setOnLongClickListener(new OnLongClickListener() {
								@Override
								public boolean onLongClick(View v) {
									onItemLongClicked(v);
									return true;
								}
							});
                            Item item  = (Item) object;
                            ((TextView) view.findViewById(TitleViewUID)).setText(item.name);
                            if (item.price > 0) {
                                ((TextView) view.findViewById(PriceViewUID)).setText(ReceiptActivity.longToFormattedString(item.price, null));
                            }
                            else {
                                ((TextView) view.findViewById(PriceViewUID)).setText(ReceiptActivity.longToFormattedString(item.estimatedPrice, null));
                            }

							if (view.isSelected() != selection.contains(item)) {
								if (!isRefreshingViews()) {
									((LegacyRippleDrawable) view.getBackground()).dismissPendingAnimation();
								}
								view.setSelected(selection.contains(item));
							}
                        }
                    };
                    controller.addSection();
                    for (Item item : clipboard) {
                        controller.getSectionAtIndex(0).addObject(item);
                    }
                    popover = new CollectionPopover(new Popover.AnchorProvider() {
                        @Override
                        public View getAnchor(Popover popover) {
                            return pasteButton;
                        }
                    }, controller);
                    popover.setOnDismissListener(new Popover.OnDismissListener() {
                        @Override
                        public void onDismiss() {
                            popover = null;
                        }
                    });
                    SpannableStringBuilder title = Utils.appendWithSpan(new SpannableStringBuilder(), getString(R.string.ItemsInClipboard), new AbsoluteSizeSpan(24, true));
                    title.setSpan(new Utils.CustomTypefaceSpan(Receipt.condensedTypeface()), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    popover.getHeader().setTitle(title);
                    popover.getHeader().buildItem().setId(R.id.PasteAll).setResource(R.drawable.ic_add_to_list_dark).setTitle(getString(R.string.PasteAll))
                            .setShowAsIcon(true).build();
                    popover.getHeader().buildItem().setId(R.id.menu_delete).setResource(R.drawable.ic_action_delete_dark).setTitle(getString(R.string.ClearClipboard))
                            .setShowAsIcon(true).build();
                    popover.getHeader().setOnLegacyActionSeletectedListener(HeaderFragment.this);
                    popover.setWidth(Width);
                    popover.setOnCreatedLayoutListener(CollectionPopover.FastAnimationLayoutListener);
                    popover.show(activity);
				    return true;
				}
			}
			if (event.getAction() == MotionEvent.ACTION_UP && event.getPointerCount() == 1) {
				if (feedbackView == null)
					return false;
				feedbackView.setAlpha(1);
				feedbackView.setTranslationY(0);
				feedbackView.setVisibility(View.INVISIBLE);
				feedbackView = null;
				view.getParent().requestDisallowInterceptTouchEvent(false);
				return false;
			}
            return !fired;
        }
		
	};


    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
        if (item.getId() == R.id.PasteAll) {
            pasteAll();
            if (popover != null) popover.dismiss();
        }
        if (item.getId() == R.id.menu_delete) {
            clearClipboard();
            if (popover != null) popover.dismiss();
        }
        if (item.getId() == R.id.AddAll) {
            addAllCommonlyUsed();
            if (popover != null) popover.dismiss();
        }
    }



    private OnTouchListener advancedAddListener = new OnTouchListener() {

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
                feedbackView = ((ViewGroup)view.getParent()).findViewById(R.id.AddFeedbackView);
                feedbackView.setAlpha(0);
                feedbackView.setVisibility(View.VISIBLE);
                view.getParent().requestDisallowInterceptTouchEvent(true);
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_MOVE &&
                    (event.getX() - startX > 10 * metrics.density ||
                            event.getY() - startY > 10 * metrics.density)) {
                view.setPressed(false);
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
                    final int Width = getResources().getConfiguration().smallestScreenWidthDp < 600 ?
                            (int) (264 * metrics.density + 0.5f) : (int) (360 * metrics.density + 0.5f);
                    CollectionViewController controller = new CollectionViewController() {
                        @Override
                        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
                            Utils.DPTranslator dp = new Utils.DPTranslator(container.getResources().getDisplayMetrics().density);
                            int height = dp.get(48);

                            FrameLayout layout = new FrameLayout(container.getContext(), null, android.R.attr.borderlessButtonStyle);
                                layout.setLayoutParams(new ViewGroup.LayoutParams(Width, height));
                                layout.setBackground(Utils.getDeselectedColors(activity));
                            TextView title = new TextView(container.getContext());
                                title.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.TextSize));
                                title.setTextColor(getResources().getColor(R.color.DashboardText));
                                title.setGravity(Gravity.CENTER | Gravity.LEFT);
                                title.setPadding(getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline), 0, 0, 0);
                                title.setId(1);
                            layout.addView(title, new WindowManager.LayoutParams(Width, height));
                            TextView price = new TextView(container.getContext());
                                price.setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen.TextSize));
                                price.setTextColor(getResources().getColor(R.color.DashboardTitle));
                                price.setGravity(Gravity.CENTER | Gravity.RIGHT);
                                price.setPadding(getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline), 0, getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline), 0);
                                price.setId(2);
                            layout.addView(price, new WindowManager.LayoutParams(Width, height));
                            return layout;
                        }

                        @Override
                        public void configureView(View view, Object item, int viewType) {
                            view.setTag(item);
                            view.setOnClickListener(commonlyUsedPopupListener);
                            ((TextView) view.findViewById(1)).setText(((ItemCollectionFragment.Suggestion) item).name);
                            ((TextView) view.findViewById(2)).setText(ReceiptActivity.longToFormattedString(((ItemCollectionFragment.Suggestion) item).price, null));
                        }
                    };
                    int commonlyUsedSize = ItemCollectionFragment.commonlyUsedItems.size();
                    controller.addSection();
                    for (int i = 0; i < commonlyUsedSize; i++) {
                        controller.getSectionAtIndex(0).addObject(ItemCollectionFragment.commonlyUsedItems.get(i).suggestion);
                    }
                    popover = new CollectionPopover(new Popover.AnchorProvider() {
                        @Override
                        public View getAnchor(Popover popover) {
                            return addButton;
                        }
                    }, controller);
                    popover.setOnDismissListener(new Popover.OnDismissListener() {
                        @Override
                        public void onDismiss() {
                            popover = null;
                        }
                    });
                    SpannableStringBuilder title = Utils.appendWithSpan(new SpannableStringBuilder(), getString(R.string.CommonlyUsedItems), new AbsoluteSizeSpan(24, true));
                    title.setSpan(new Utils.CustomTypefaceSpan(Receipt.condensedTypeface()), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    popover.getHeader().setTitle(title);
                    popover.getHeader().buildItem().setId(R.id.AddAll).setResource(R.drawable.ic_add_to_list_dark).setTitle(getString(R.string.AddAll))
                            .setShowAsIcon(true).build();
                    popover.getHeader().setOnLegacyActionSeletectedListener(HeaderFragment.this);
                    popover.setWidth(Width);
                    popover.show(activity);
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
                view.getParent().requestDisallowInterceptTouchEvent(false);
                return false;
            }
            if (!fired)
                return true;
            return false;
        }

    };
	
	final OnClickListener clipboardPopupListener = new OnClickListener() {
		@Override
		public void onClick(View view) {
//			if (item.getItemId() == PasteAll) {
//				pasteAll();
//			}
//			else if (item.getItemId() == ClearClipboard) {
//				clearClipboard();
//			}
//			else {
            ArrayList<Item> wrapper = new ArrayList<Item>(1);
            wrapper.add((Item) view.getTag());
            activity.appendItems(wrapper);
            clipboard.remove(view.getTag());
            if (clipboard.size() == 0) {
                clearClipboard();
                popover.dismiss();
            }
//			}
//			return false;
		}
		
	};

    private Utils.OnTextChangedListener titleEditorListener = new Utils.OnTextChangedListener() {
        public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            activity.setName(charSequence.toString().trim());
        }
    };

    private View.OnFocusChangeListener titleFocusListener = new View.OnFocusChangeListener() {
        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus) {
                dismissTitleEditor();
            }
        }
    };

    private TextView.OnEditorActionListener titleEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (event == null || event.getAction() == EditorInfo.IME_ACTION_DONE) {
                dismissTitleEditor();
            }
            return false;
        }
    };

    private ListenableEditText.OnKeyPreImeListener titleKeyPreImeListener = new ListenableEditText.OnKeyPreImeListener() {
        @Override
        public boolean onKeyPreIme(int keyCode, KeyEvent event) {
            if (event.getAction() == KeyEvent.ACTION_UP && keyCode == KeyEvent.KEYCODE_BACK) {
                dismissTitleEditor();
            }
            return false;
        }
    };

    private OnClickListener titleEditButtonListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            showTitleEditor();
        }
    };

    public void onNameChanged(String newName) {
        title.setText(TextUtils.isEmpty(newName) ? getString(R.string.NewList) : newName);
    }

    public void showTitleEditor() {
        activity.closeEditorAndKeyboard(true);

        titleEditor.setVisibility(View.VISIBLE);
        title.setVisibility(View.INVISIBLE);

        titleEditor.setText(activity.getName());
        titleEditor.requestFocus();

        titleEditButton.setOnClickListener(null);

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.showSoftInput(titleEditor, InputMethodManager.SHOW_FORCED);

        titleEditIcon.animate().translationX(-getResources().getDimensionPixelSize(R.dimen.SecondaryKeyline)).alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                titleEditIcon.setVisibility(View.INVISIBLE);
            }
        });

        titleEditDone.setAlpha(0f);
        titleEditDone.setTranslationX(-getResources().getDimensionPixelSize(R.dimen.SecondaryKeyline));
        titleEditDone.setVisibility(View.VISIBLE);
        titleEditDone.animate().translationX(0f).alpha(1f).setDuration(200);
    }

    public void dismissTitleEditor() {
        titleEditor.setVisibility(View.INVISIBLE);
        title.setVisibility(View.VISIBLE);

        titleEditButton.setOnClickListener(titleEditButtonListener);

        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(titleEditor.getWindowToken(), 0);

        titleEditDone.animate().translationX(-getResources().getDimensionPixelSize(R.dimen.SecondaryKeyline)).alpha(0f).setDuration(200).withEndAction(new Runnable() {
            @Override
            public void run() {
                titleEditDone.setVisibility(View.INVISIBLE);
            }
        });

        titleEditIcon.setAlpha(0f);
        titleEditIcon.setTranslationX(-getResources().getDimensionPixelSize(R.dimen.SecondaryKeyline));
        titleEditIcon.setVisibility(View.VISIBLE);
        titleEditIcon.animate().translationX(0f).alpha(1f).setDuration(200);
    }

    public void setListVisible(boolean visible, boolean animated, int delay) {
        checkoutStateManager.setListVisible(visible, animated, delay);
    }

    private class CheckoutStateManager {
        boolean listVisible;
        boolean checkoutEnabled;
        boolean undoVisible;

        public void updateState(boolean animated, long delay) {
            if (listVisible && (checkoutEnabled || undoVisible)) {
                checkoutFAB.showDelayed(delay, animated);
            }
            else {
                checkoutFAB.hide(animated);
            }
        }

        public void setUndoVisible(boolean visible, boolean animated) {
            undoVisible = visible;
            updateState(animated, 0);
        }

        public void setListVisible(boolean visible, boolean animated, int delay) {
            listVisible = visible;
            updateState(animated, delay);
        }

        public void setCheckoutEnabled(boolean enabled, boolean animated) {
            checkoutEnabled = enabled;
            updateState(animated, 0);
        }
    }

    final OnClickListener commonlyUsedPopupListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            ArrayList<Item> wrapper = new ArrayList<Item>(1);
            wrapper.add(((ItemCollectionFragment.Suggestion) v.getTag()).toItem());
            activity.appendItems(wrapper);
            if (popover != null) {
                popover.dismiss();
            }
        }

    };

    public void addAllCommonlyUsed() {
        ArrayList<Item> wrapper = new ArrayList<Item>(1);
        for (ItemCollectionFragment.CommonlyUsedItem commonlyUsedItem : ItemCollectionFragment.commonlyUsedItems) {
            wrapper.add(commonlyUsedItem.suggestion.toItem());
        }
        activity.appendItems(wrapper);
        if (popover != null) {
            popover.dismiss();
        }
    }
	
	public void loadPendingItems() {
		new LoadPendingItemsAsyncTask().execute();
	}

    class LoadPendingItemsAsyncTask extends AsyncTask<Void, Void, ArrayList<Item>> {
    	
    	private Task task;
    	private IndicatorFragmentNonCompat indicator;
    	
    	protected void onPreExecute() {
    		task = Task.createTask("Loading items", null);
    	}

		@Override
		protected ArrayList<Item> doInBackground(Void... params) {
            synchronized (Receipt.DatabaseLock) {
                SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();

                Cursor pendingItems = db.query(Receipt.DBPendingTable, Receipt.DBAllPendingItemsColumns,
                        null, null, null, null, null);

                ArrayList<Item> newItems = new ArrayList<Item>();

                if (pendingItems.getCount() > 0)
                    publishProgress();

                while (pendingItems.moveToNext()) {
                    Item item = new Item();
                    item.flags = ItemListFragment.SetTitle;
                    item.crossedOff = false;
                    item.estimatedPrice = pendingItems.getLong(Receipt.DBPriceKeyIndex);
                    item.name = pendingItems.getString(Receipt.DBNameKeyIndex);
                    item.unitOfMeasurement = pendingItems.getString(Receipt.DBUnitOfMeasurementKeyIndex);
                    item.qty = 0;

                    long uid = pendingItems.getLong(Receipt.DBPendingItemUIDKeyIndex);
                    Cursor tagFinder = db.query(Receipt.DBTagConnectionsTable, Receipt.DBAllTagConnectionColumns,
                            Receipt.DBItemConnectionUIDKey + " = " + uid, null, null, null, null);
                    while (tagFinder.moveToNext()) {
                        item.addTag(TagStorage.findTagWithUID(tagFinder.getInt(Receipt.DBTagConnectionUIDKeyIndex)));
                    }

                    newItems.add(item);
                }

                db.execSQL("delete from " + Receipt.DBPendingTable);

                db.close();
                return newItems;
            }
		}
		
		@Override
		protected void onProgressUpdate(Void ... progress) {

    		if (activity != null) {
    			indicator = activity.getIndicator();
    			indicator.startWorking(task);
    		}
    		
		}
		
		@Override
		protected void onPostExecute(ArrayList<Item> result) {
			if (indicator != null)
				indicator.stopWorking(task);
			if (result.size() > 0) {
				appendToClipboard(result);
			}
		}
    	
    }
	
}
