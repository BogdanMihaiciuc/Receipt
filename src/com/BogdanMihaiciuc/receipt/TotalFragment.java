package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLayoutChangeListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.BogdanMihaiciuc.util.$;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class TotalFragment extends Fragment implements OnMenuItemClickListener {
	
	final static int SetBudget = 1;
	final static int SetTax = 2;
	
	private ReceiptActivity activity;
	float density;
	private DisplayMetrics metrics;
	
	private boolean loading;
	private TextView totalSum;
	private TextView title;
	
	private ViewGroup totalRoot;
	
	private boolean showingBudget;
	private View budgetRoot;
	
	private boolean showingTax;
	private boolean hadFocus;
	private View taxRoot;
	
	private PopupMenu popup;
	
	private boolean showingPanel;
	private ViewGroup panel;
	private View panelTouchHandler;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		
		View fragment = inflater.inflate(R.layout.fragment_total, container, false);

        fragment.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                showBudgetPopup(view);
            }
        });

        return fragment;
		
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		activity = (ReceiptActivity) getActivity();
		totalSum = (TextView) activity.findViewById(R.id.total_sum);
        totalSum.setTypeface(Receipt.condensedTypeface());
		title = (TextView) activity.findViewById(R.id.text_total);
        title.setTypeface(Receipt.condensedTypeface());
		totalRoot = (ViewGroup) getView();
		
		metrics = new DisplayMetrics();
		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		density = metrics.density;

//        ((ViewGroup)activity.findViewById(R.id.ItemCollection)).getChildAt(0).addOnLayoutChangeListener(new OnLayoutChangeListener() {
//            @Override
//            public void onLayoutChange(View view, int i, int i2, int i3, int i4, int i5, int i6, int i7, int i8) {
//                if (activity.findViewById(R.id.ItemCollection).getHeight() <
//                        ((ViewGroup)activity.findViewById(R.id.ItemCollection)).getChildAt(0).getHeight()) {
//                    activity.findViewById(R.id.TotalSeparator).setVisibility(View.VISIBLE);
//                }
//                else {
//                    activity.findViewById(R.id.TotalSeparator).setVisibility(View.INVISIBLE);
//                }
//            }
//        });

//        totalSum.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View view) {
//
//                SpannableStringBuilder text = new SpannableStringBuilder();
////                text.append(getString(R.string.total));
////                text.append('\n');
////                text.setSpan(new AbsoluteSizeSpan(24, true), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//
//                text.append(activity.getCrossedOffCount() != 0 ?
//                        ReceiptActivity.currentTruncatedLocale + activity.getTotal().setScale(2, RoundingMode.HALF_EVEN).toPlainString()
//                        : ReceiptActivity.currentTruncatedLocale + activity.getEstimatedTotal().setScale(2, RoundingMode.HALF_EVEN).toPlainString());
//
////                if (activity.getTax() > 0) {
////                    text.append('\n');
////                    text.append(getString(R.string.Subtotal));
////                    text.setSpan(new AbsoluteSizeSpan(24, true), text.length() - getString(R.string.Subtotal).length(), text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
////                    text.append('\n');
////
////                    text.append(activity.getCrossedOffCount() != 0 ?
////                            ReceiptActivity.currentTruncatedLocale + activity.getSubtotal().setScale(2, RoundingMode.HALF_EVEN).toPlainString()
////                            : ReceiptActivity.currentTruncatedLocale + activity.getEstimatedSubtotal().setScale(2, RoundingMode.HALF_EVEN).toPlainString());
////                }
//
//                Receipt.headsUpFromView(view, text);
//            }
//        });
		
		if (!loading) {
			
			BigDecimal total;
			if (activity.getCrossedOffCount() > 0) {
				total = activity.getTotal();
				title.setText(R.string.total);
			}
			else {
				total = activity.getEstimatedTotal();
				title.setText(R.string.EstimatedTotal);
			}

			totalSum.setText(ReceiptActivity.totalFormattedString(activity, total));
			
			if (activity.budgetIsExceeded())
				totalSum.setTextColor(getResources().getColor(R.color.OverBudget));
			
			onTotalChange(null);
		}
		
		if (showingTax) {
			showingTax = false;
			showTaxEditor(false, hadFocus);
		}
		
		if (showingBudget) {
			showingBudget = false;
			showBudgetEditor(false, hadFocus);
		}

        if (showingPanel) {
            showPanel(true);
        }

        if (activity.isSidebar()) {
            getView().setBackgroundColor(0xFFFFFFFF);
        }
		
	}
	
	
	public void onPause() {
		if (popup != null)
			popup.dismiss();
		popup = null;
		
		super.onPause();
	}
	
	
	public void onDetach() {
		
		activity = null;
		totalSum = null;
		title = null;
		totalRoot = null;
		
		taxRoot = null;
		budgetRoot = null;
		
		super.onDetach();
	}
	
	public void delayInit() {
		loading = true;
	}
	
	public void initNow() {
		
		if (activity == null)
			loading = false;
		else{
			
			BigDecimal total;
			if (activity.getCrossedOffCount() > 0) {
				total = activity.getTotal();
				title.setText(R.string.total);
			}
			else {
				total = activity.getEstimatedTotal();
				title.setText(R.string.EstimatedTotal);
			}
			
			totalSum.setText(ReceiptActivity.totalFormattedString(activity, total));
			
			if (activity.budgetIsExceeded()) {
                totalSum.setTextColor(getResources().getColor(R.color.OverBudget));
                if (panel != null) {
                    ((TextView) panel.findViewById(R.id.TotalText)).setTextColor(getResources().getColor(R.color.OverBudget));
                }
            }
			else {
				onBudgetOK();
			}

			onTotalChange(null);
			
		}
		
	}
	
	public void showTaxEditor(final boolean animated, final boolean requestFocus) {
		if (showingTax) return;
		showingTax = true;
		
		taxRoot = activity.getLayoutInflater().inflate(R.layout.footer_tax, totalRoot, false);
		totalRoot.addView(taxRoot);
		taxRoot.setClickable(true);
		
		((TextView) taxRoot.findViewById(R.id.FooterTitle)).setText(R.string.Tax);

		taxRoot.findViewById(R.id.FooterInput).setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (activity == null) return;
				hadFocus = hasFocus;
				if (!hasFocus) {
					activity.getWindow().setSoftInputMode(
						       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
				}
				else {
					activity.getWindow().setSoftInputMode(
						       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
				}
			}
		});
		
		((EditText) taxRoot.findViewById(R.id.FooterInput)).setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					dismissTaxEditor(true);
					return true;
				}
				return false;
			}
		});
		
		final EditText input = (EditText) taxRoot.findViewById(R.id.FooterInput);
		input.addOnLayoutChangeListener(new OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View arg0, int arg1, int arg2, int arg3,
					int arg4, int arg5, int arg6, int arg7, int arg8) {
				input.removeOnLayoutChangeListener(this);
				input.setMaxWidth(input.getRight() - ((View)input.getParent()).findViewById(R.id.FooterTitle).getRight());
			}
		});
		
		if (activity.getTax() != 0)
			((EditText) taxRoot.findViewById(R.id.FooterInput))
				.setText(new BigDecimal(activity.getTax()).movePointLeft(2).stripTrailingZeros().toPlainString());
		else
			((EditText) taxRoot.findViewById(R.id.FooterInput))
				.setText("");
		
		if (animated) {
			final View TaxRoot = taxRoot;
			taxRoot.setTranslationX(100 * density);
			taxRoot.setAlpha(0);
			taxRoot.animate()
				.translationX(0).alpha(1)
				.setInterpolator(new DecelerateInterpolator(2f))
				.setDuration(250)
				.setListener(new AnimatorListenerAdapter() {
					boolean cancelled;
					public void onAnimationCancel(Animator a) {
						cancelled = true;
					}
					public void onAnimationEnd(Animator a) {
						if (activity == null) return;
						if (requestFocus && !cancelled) {
							TaxRoot.findViewById(R.id.FooterInput).requestFocus();
							((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
								.showSoftInput(TaxRoot.findViewById(R.id.FooterInput), InputMethodManager.SHOW_FORCED);
						}
					}
				});
		}
		else {
			if (requestFocus) {
				taxRoot.findViewById(R.id.FooterInput).requestFocus();
				((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
					.showSoftInput(taxRoot.findViewById(R.id.FooterInput), InputMethodManager.SHOW_FORCED);
			}
		}
		
		taxRoot.findViewById(R.id.FooterTitle).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dismissTaxEditor(true);
			}
		});
		
	}
	
	public void dismissTaxEditor(boolean animated) {
		
		int tax;
		try {
			tax = new BigDecimal(((EditText) taxRoot.findViewById(R.id.FooterInput)).getText().toString().trim()).movePointRight(2).intValue();
		}
		catch (NumberFormatException e) {
			tax = 0;
		}
		
		activity.setTax(tax);
		
		showingTax = false;
		taxRoot.animate().cancel();
		
		taxRoot.findViewById(R.id.FooterTitle).setEnabled(false);
		taxRoot.findViewById(R.id.FooterInput).setEnabled(false);
		
		final View TaxRoot = taxRoot;
		taxRoot = null;
		
		((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(TaxRoot.getWindowToken(), 0);
		
		if (animated) {
			if (activity.getTax() != tax) {
				ValueAnimator animator = ValueAnimator.ofInt(0xFF, 0x44);
				animator.setDuration(400);
				animator.setInterpolator(new AccelerateInterpolator(2f));
				animator.addUpdateListener(new AnimatorUpdateListener() {
					@Override
					public void onAnimationUpdate(ValueAnimator animation) {
						int color = 0xFFFF0000 | (Integer)animation.getAnimatedValue() | ((Integer)animation.getAnimatedValue() << 8);
						TaxRoot.setBackgroundColor(color);
					}
				});
				animator.start();
			}
			TaxRoot.animate()
				.translationX(100 * density).alpha(0)
				.setInterpolator(new AccelerateInterpolator(2f))
				.setDuration(250)
				.setStartDelay(200)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationEnd(Animator a) {
						if (activity == null) return;
						totalRoot.removeView(TaxRoot);
					}
				});
		}
		else {
			totalRoot.removeView(TaxRoot);
		}
	}
	
	public void showBudgetEditor(final boolean animated, final boolean requestFocus) {
		if (showingBudget) return;
		showingBudget = true;
		
		budgetRoot = activity.getLayoutInflater().inflate(R.layout.footer_tax, totalRoot, false);
		totalRoot.addView(budgetRoot);
		budgetRoot.setClickable(true);
		
		((TextView) budgetRoot.findViewById(R.id.FooterTitle)).setText(R.string.budget);

		budgetRoot.findViewById(R.id.FooterInput).setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (activity == null) return;
				hadFocus = hasFocus;
				if (!hasFocus) {
					activity.getWindow().setSoftInputMode(
						       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
				}
				else {
					activity.getWindow().setSoftInputMode(
						       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
				}
			}
		});
		
		((EditText) budgetRoot.findViewById(R.id.FooterInput)).setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
				if (actionId == EditorInfo.IME_ACTION_DONE) {
					dismissBudgetEditor(true);
					return true;
				}
				return false;
			}
		});
		
		final EditText input = (EditText) budgetRoot.findViewById(R.id.FooterInput);
		input.addOnLayoutChangeListener(new OnLayoutChangeListener() {
			@Override
			public void onLayoutChange(View arg0, int arg1, int arg2, int arg3,
					int arg4, int arg5, int arg6, int arg7, int arg8) {
				input.removeOnLayoutChangeListener(this);
				input.setMaxWidth(input.getRight() - ((View)input.getParent()).findViewById(R.id.FooterTitle).getRight());
			}
		});
		
		if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) != 0)
			((EditText) budgetRoot.findViewById(R.id.FooterInput))
				.setText(activity.getBudget().setScale(2, RoundingMode.HALF_EVEN).stripTrailingZeros().toPlainString());
		else
			((EditText) budgetRoot.findViewById(R.id.FooterInput))
				.setText("");
		
		if (animated) {
			final View BudgetRoot = budgetRoot;
			budgetRoot.setTranslationX(100 * density);
			budgetRoot.setAlpha(0);
			budgetRoot.animate()
				.translationX(0).alpha(1)
				.setInterpolator(new DecelerateInterpolator(2f))
				.setDuration(250)
				.setListener(new AnimatorListenerAdapter() {
					boolean cancelled;
					public void onAnimationCancel(Animator a) {
						cancelled = true;
					}
					public void onAnimationEnd(Animator a) {
						if (activity == null) return;
						if (requestFocus && !cancelled) {
							BudgetRoot.findViewById(R.id.FooterInput).requestFocus();
							((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
								.showSoftInput(BudgetRoot.findViewById(R.id.FooterInput), InputMethodManager.SHOW_FORCED);
						}
					}
				});
		}
		else {
			if (requestFocus) {
				budgetRoot.findViewById(R.id.FooterInput).requestFocus();
				((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE))
					.showSoftInput(budgetRoot.findViewById(R.id.FooterInput), InputMethodManager.SHOW_FORCED);
			}
		}
		
		budgetRoot.findViewById(R.id.FooterTitle).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				dismissBudgetEditor(true);
			}
		});
		
	}
	
	public void dismissBudgetEditor(boolean animated) {
		
		BigDecimal budget;
		try {
			budget = new BigDecimal(((EditText) budgetRoot.findViewById(R.id.FooterInput)).getText().toString().trim());
		}
		catch (NumberFormatException e) {
			budget = new BigDecimal(0);
		}
		
		activity.setBudget(budget);
		
		showingBudget = false;
		budgetRoot.animate().cancel();
		
		budgetRoot.findViewById(R.id.FooterTitle).setEnabled(false);
		budgetRoot.findViewById(R.id.FooterInput).setEnabled(false);
		
		final View BudgetRoot = budgetRoot;
		budgetRoot = null;
		
		((InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(BudgetRoot.getWindowToken(), 0);
		
		final float density = this.density;
		
		if (animated) {
			if (activity.getBudget().compareTo(budget) != 0 && budget.compareTo(new BigDecimal(0)) != 0) {
				ValueAnimator animator = ValueAnimator.ofInt(0xFF, 0x44);
				animator.setDuration(400);
				animator.setInterpolator(new AccelerateInterpolator(2f));
				animator.addUpdateListener(new AnimatorUpdateListener() {
					@Override
					public void onAnimationUpdate(ValueAnimator animation) {
						int color = 0xFFFF0000 | (Integer)animation.getAnimatedValue() | ((Integer)animation.getAnimatedValue() << 8);
						BudgetRoot.setBackgroundColor(color);
					}
				});
				animator.start();
			}
			BudgetRoot.animate()
				.translationX(100 * density).alpha(0)
				.setInterpolator(new AccelerateInterpolator(2f))
				.setDuration(250)
				.setStartDelay(200)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationEnd(Animator a) {
						if (activity == null) return;
						totalRoot.removeView(BudgetRoot);
					}
				});
		}
		else {
			totalRoot.removeView(BudgetRoot);
		}
	}
	
	final boolean DEBUG_PANEL = true;
	
	//Popup menu on phones
	public void showBudgetPopup(View v) {
		
		showPanel();
		if (DEBUG_PANEL) return;
		
	    popup = new PopupMenu(activity, v);
	    popup.setOnMenuItemClickListener(this);
	    Menu popupMenu = popup.getMenu();
	    
	    //The menu items depend on whether the budget and/or tax have
	    //already been set
	    if (activity.getTax() != 0)
	    	popupMenu.add(String.format(getString(R.string.SubtotalDisplay), activity.getSubtotal().setScale(2, RoundingMode.HALF_EVEN).toString())).setEnabled(false);
	    
	    if (activity.getTax() == 0) {
	    	popupMenu.add(Menu.NONE, SetTax, Menu.NONE, R.string.SetTax);
	    }
	    else {
	    	popupMenu.add(Menu.NONE, SetTax, Menu.NONE, String.format(getString(R.string.TaxDisplay), 
	    			new BigDecimal(activity.getTax()).movePointLeft(2).stripTrailingZeros().toPlainString()));
	    }
	    
	    if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) != 0) {
	    	popupMenu.add(String.format(getString(R.string.RemainingBudgetDisplay), 
	    			activity.getBudget().subtract(activity.getTotal()).setScale(2, RoundingMode.HALF_EVEN).toString()))
	    			.setEnabled(false);
	    	popupMenu.add(Menu.NONE, SetBudget, Menu.NONE, String.format(getString(R.string.BudgetDisplay), 
	    			activity.getBudget().setScale(2, RoundingMode.HALF_EVEN).toString()));
	    }
	    else {
	    	popupMenu.add(Menu.NONE, SetBudget, Menu.NONE, R.string.SetBudget);
	    }
	    popup.show();
	}
	
	public boolean onMenuItemClick(MenuItem item) {
	    
	    popup = null;
	    
		//The action depends on which type of menu has been shown
	    switch (item.getItemId()) {
	        case SetBudget:
	        	showBudgetEditor(true, true);
	            return true;
	        case SetTax:
	        	showTaxEditor(true, true);
	        	return true;
	        default:
	            return false;
	    }
	}

	public void onBudgetChange(BigDecimal newBudget) {
		onTotalChange(null);
	}

	public void onTotalChange(BigDecimal newTotal) {
		
		if (activity == null)
			return;
		
		BigDecimal total;
		if (activity.getCrossedOffCount() > 0) {
			total = activity.getTotal();
			title.setText(R.string.total);
		}
		else {
			total = activity.getEstimatedTotal();
			title.setText(R.string.EstimatedTotal);
		}

		totalSum.setText(ReceiptActivity.totalFormattedString(activity, total));

		if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) == 0) return;

		if (total.compareTo(activity.getBudget()) == 1) {
			onBudgetExceeded();
		}
		else {
			onBudgetOK();
		}
		
	}
	

    // PANEL FUNCTIONALITY

    private Handler delayHandler = new Handler();
    final static int TypingUpdateDelay = 150;

    protected void updateBudget() {
        if (!showingPanel) return;

        BigDecimal budget;
        try {
            budget = new BigDecimal(((EditText) panel.findViewById(R.id.BudgetEditor)).getText().toString().trim());
        }
        catch (NumberFormatException e) {
            budget = new BigDecimal(0);
        }

        BigDecimal previousBudget = activity.getBudget();
        if (previousBudget.compareTo(budget) != 0) {
            activity.setBudget(budget);
            updatePanelBalance();
        }
    }

    final Runnable updateBudgetRunnable = new Runnable() {
        @Override
        public void run() {
            updateBudget();
        }
    };

    protected void updateTax() {
        if (!showingPanel) return;

        int tax;
        try {
            tax = new BigDecimal(((EditText) panel.findViewById(R.id.TaxEditor)).getText().toString().trim()).movePointRight(2).intValue();
        }
        catch (NumberFormatException e) {
            tax = 0;
        }

        if (tax != activity.getTax()) {
            activity.setTax(tax);
            updatePanelTotal();
        }
    }

    final Runnable updateTaxRunnable = new Runnable() {
        @Override
        public void run() {
            updateTax();
        }
    };

    // PANEL UI

    final static long PanelAnimationDuration = 400;
    final static long PanelAnimationStepping = 25;
    final static float PanelAnimationAcceleration = 1.5f;
    final static TimeInterpolator PanelAnimationInterpolator = new Utils.FrictionInterpolator(PanelAnimationAcceleration);

    protected void updatePanelBalance() {
        if (!showingPanel) return;

        if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) != 0) {
            ((TextView) panel.findViewById(R.id.BalanceText))
                    .setText(ReceiptActivity.totalFormattedStringWithSpecifiedCutoff(
                            activity,
                            activity.getBalance(),
                            ReceiptActivity.LandscapeInitialCutoff));
        }
        else {
            SpannableStringBuilder unlimited = new SpannableStringBuilder();
            unlimited.append(getString(R.string.BudgetUnlimited));
            unlimited.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.light_text_gray)), 0, unlimited.length(), SpannableStringBuilder.SPAN_INCLUSIVE_INCLUSIVE);
            ((TextView) panel.findViewById(R.id.BalanceText)).setText(unlimited);
        }
    }

    protected void updatePanelBudget() {
        if (!showingPanel) return;

        if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) == 0) {
            ((TextView) panel.findViewById(R.id.BudgetEditor)).setText("");
        }
        else {
            ((TextView) panel.findViewById(R.id.BudgetEditor)).setText(activity.getBudget().setScale(2, RoundingMode.HALF_EVEN).toPlainString());
        }
    }

    protected void updatePanelTax() {
        if (!showingPanel) return;

        if (activity.getTax() == 0) {
            ((TextView) panel.findViewById(R.id.TaxEditor)).setText("");
        }
        else {
            ((TextView) panel.findViewById(R.id.TaxEditor)).setText(new BigDecimal(activity.getTax()).movePointLeft(2).stripTrailingZeros().toPlainString());
        }
    }

    protected void updatePanelTotal() {
        if (!showingPanel) return;

        ((TextView) panel.findViewById(R.id.TotalText)).setText(totalSum.getText());
    }
    
    protected void showPanel() {
    	showPanel(false);
    }
    
    protected void showPanel(final boolean instant) {

    	final ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
    	final View content = activity.getContentRoot();
    	showingPanel = true;
    	
    	panelTouchHandler = new View(activity);
    	panelTouchHandler.setOnTouchListener(new OnTouchListener() {
			@Override
			public boolean onTouch(View arg0, MotionEvent event) {
				if (event.getAction() == MotionEvent.ACTION_DOWN && (event.getRawY() < panel.getY() || event.getRawX() > panel.getRight() || event.getRawX() < panel.getLeft())) {
					panelTouchHandler.setOnTouchListener(null);
					applyBudgetSettings();
				}
				return true;
			}
		});
    	root.addView(panelTouchHandler);
    	
    	panel = (ViewGroup) activity.getLayoutInflater().inflate(R.layout.layout_panel_total, root, false);
    	root.addView(panel);
        panel.setVisibility(View.INVISIBLE);

    	final CollectionView itemList = (CollectionView) activity.findViewById(R.id.ItemCollection);
    	final View InnerList = activity.findViewById(R.id.innerList);
        final TextView balanceTitle = (TextView) panel.findViewById(R.id.TotalTitle);
        final TextView balanceText = (TextView) panel.findViewById(R.id.TotalText);

        balanceTitle.setTypeface(Receipt.condensedTypeface());
        balanceTitle.setTextColor(title.getTextColors().getDefaultColor());
        balanceText.setTypeface(Receipt.condensedTypeface());
        balanceText.setTextColor(totalSum.getTextColors().getDefaultColor());

        balanceText.setText(totalSum.getText());
        balanceTitle.setText(title.getText());

        balanceText.setEnabled(false);
//        balanceText.setOnClickListener(new OnClickListener() {
//            @Override
//            public void onClick(View view) {
//                Receipt.headsUpFromView(view,
//                        activity.getCrossedOffCount() != 0 ?
//                                ReceiptActivity.currentTruncatedLocale + activity.getTotal().setScale(2, RoundingMode.HALF_EVEN).toPlainString()
//                                : ReceiptActivity.currentTruncatedLocale + activity.getEstimatedTotal().setScale(2, RoundingMode.HALF_EVEN).toPlainString());
//            }
//        });

        updatePanelBalance();
        updatePanelBudget();
        updatePanelTax();

        EditText budgetEditor = (EditText) panel.findViewById(R.id.BudgetEditor);
        budgetEditor.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    delayHandler.removeCallbacks(updateBudgetRunnable);
                    updateBudget();
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });
        budgetEditor.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    delayHandler.removeCallbacks(updateBudgetRunnable);
                    updateBudget();
                }
                else {
                    view.requestFocus();
                }
            }
        });
        budgetEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {}
            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                delayHandler.removeCallbacks(updateBudgetRunnable);
                delayHandler.postDelayed(updateBudgetRunnable, TypingUpdateDelay);
            }
            @Override
            public void afterTextChanged(Editable editable) {}
        });

        EditText taxEditor = (EditText) panel.findViewById(R.id.TaxEditor);
        taxEditor.setOnEditorActionListener(new OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    delayHandler.removeCallbacks(updateTaxRunnable);
                    updateTax();
                    InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
                    return true;
                }
                return false;
            }
        });
        taxEditor.setOnFocusChangeListener(new OnFocusChangeListener() {
            @Override
            public void onFocusChange(View view, boolean hasFocus) {
                if (!hasFocus) {
                    delayHandler.removeCallbacks(updateTaxRunnable);
                    updateTax();
                }
                else {
                    view.requestFocus();
                }
            }
        });
        taxEditor.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i2, int i3) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i2, int i3) {
                delayHandler.postDelayed(updateTaxRunnable, TypingUpdateDelay);
            }

            @Override
            public void afterTextChanged(Editable editable) {
            }
        });

        panel.getLayoutParams().width = InnerList.getWidth();
        if (activity.isSidebar()) {
//            View activityContainer = activity.findViewById(R.id.ActivityContainer);
//            Rect r = new Rect();
//            activityContainer.getGlobalVisibleRect(r);

            panel.setX(activity.findViewById(R.id.ActivityContainer).getLeft());
        }
        else {
            panel.setX(InnerList.getX());
        }
        balanceText.setX(totalSum.getX() + totalRoot.getX());
        balanceTitle.setX(totalRoot.getX() + 1 * metrics.density);
        panel.findViewById(R.id.MenuGraphic).setX(totalRoot.getX());

        activity.stopAnimations();
    	
    	panel.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {

            @Override
            public void onGlobalLayout() {

//                if (DEBUG_PANEL) Log.d("TotalFragment", "panel width is" + panel.getWidth());

                if (panel.getLayoutParams().width == 0) {
                    if (InnerList.getWidth() > 0) {
                        panel.getLayoutParams().width = InnerList.getWidth();
                        panel.requestLayout();
                        if (activity.isSidebar()) {
                            panel.setX(activity.findViewById(R.id.ActivityContainer).getLeft());
                        }
                        else {
                            panel.setX(InnerList.getX());
                        }
                        balanceText.setX(totalSum.getX());
                    }
                    return;
                }

                if (panel.getHeight() > 0 && root.getHeight() > 0 && InnerList.getWidth() > 0) {
                    //noinspection deprecation
                    panel.getViewTreeObserver().removeGlobalOnLayoutListener(this);
                    panel.setVisibility(View.VISIBLE);
                }
                else {
                    return;
                }

                panel.setY(root.getHeight() - balanceTitle.getHeight());

                final float AlignmentWidth = panel.findViewById(R.id.AlignmentPlaceholder).getWidth();
                if (!instant) itemList.freeze();

                if (!instant) {
                    panel.findViewById(R.id.MenuGraphic).animate()
                            .x(-panel.findViewById(R.id.MenuGraphic).getWidth())
                            .setDuration(PanelAnimationDuration)
                            .setInterpolator(PanelAnimationInterpolator);
                }
                else {
                    panel.findViewById(R.id.MenuGraphic).setTranslationX(- panel.findViewById(R.id.MenuGraphic).getWidth());
                }

                content.animate().cancel();

                content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                content.buildLayer();

				root.setBackgroundColor(getResources().getColor(R.color.Black));

                if (!instant) {
                    content.animate()
                            .alpha(0.4f)
                            .setDuration(PanelAnimationDuration)
                            .setInterpolator(PanelAnimationInterpolator)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationStart(Animator a) {
    //                                content.setBackgroundColor(getResources().getColor(R.color.ActionBar));
                                }

                                @Override
                                public void onAnimationEnd(Animator a) {
//                                    content.setLayerType(View.LAYER_TYPE_NONE, null);
                                    itemList.thaw();
                                }
                            });
                }
                else {
                    root.setBackgroundColor(getResources().getColor(R.color.Black));
                    content.setAlpha(0.4f);
                }


                if (!instant) {
                    panel.animate()
                            .y(root.getHeight() - panel.getHeight())
                            .setDuration(PanelAnimationDuration)
                            .setInterpolator(PanelAnimationInterpolator);
                }
                else {
                    panel.setY(root.getHeight() - panel.getHeight());
                }

                if (!instant) {
                    balanceTitle.animate()
                            .x(AlignmentWidth - balanceTitle.getWidth())
                            .setDuration(PanelAnimationDuration)
                            .setInterpolator(PanelAnimationInterpolator);
                }
                else {
                    balanceTitle.setX(AlignmentWidth - balanceTitle.getWidth());
                }

                if (!instant) {
                    balanceText.animate()
                            .x(AlignmentWidth + 48 * metrics.density)
                            .setDuration(PanelAnimationDuration)
                            .setInterpolator(PanelAnimationInterpolator);
                }
                else {
                    balanceText.setX(AlignmentWidth + 48 * metrics.density);
                }

                View tmp = panel.findViewById(R.id.BalanceTitle);
                panelTitleShow(balanceTitle, tmp, AlignmentWidth, instant, 1);
                tmp = panel.findViewById(R.id.BudgetTitle);
                panelTitleShow(balanceTitle, tmp, AlignmentWidth, instant, 2);
                tmp = panel.findViewById(R.id.TaxTitle);
                panelTitleShow(balanceTitle, tmp, AlignmentWidth, instant, 3);

                tmp = panel.findViewById(R.id.BalanceText);
                panelControlShow(balanceText, tmp, AlignmentWidth, instant, 1);
                tmp = panel.findViewById(R.id.BudgetEditor);
                panelControlShow(balanceText, tmp, AlignmentWidth, instant, 2);
                tmp = panel.findViewById(R.id.TaxEditor);
                panelControlShow(balanceText, tmp, AlignmentWidth, instant, 3);
            }
        });
    	
    }
    
    protected void panelTitleShow(View balanceTitle, View tmp, float AlignmentWidth, boolean instant, int startDelay) {
        if (!instant) {
            tmp.setX(balanceTitle.getX());
			tmp.setTranslationY($.dp(48 + 24 * startDelay, activity));
			tmp.setAlpha(0f);
            tmp.animate()
					.x(AlignmentWidth - tmp.getWidth())
					.translationY(0)
					.alpha(1f)
					.withLayer()
					.setDuration(PanelAnimationDuration)
					.setStartDelay(startDelay * PanelAnimationStepping)
					.setInterpolator(PanelAnimationInterpolator);
        }
        else {
            tmp.setX(AlignmentWidth - tmp.getWidth());
        }
    }
    
    protected void panelControlShow(View balanceText, View tmp, float AlignmentWidth, boolean instant, int startDelay) {
        if (!instant) {
			tmp.setX(balanceText.getX());
			tmp.setTranslationY($.dp(48 + 24 * startDelay, activity));
			tmp.setAlpha(0f);
			tmp.animate()
					.x(AlignmentWidth + 48 * metrics.density)
					.translationY(0)
					.alpha(1f)
					.withLayer()
					.setDuration(instant ? 0 : PanelAnimationDuration)
					.setStartDelay(startDelay * PanelAnimationStepping)
					.setInterpolator(PanelAnimationInterpolator);
        }
        else {
            tmp.setX(AlignmentWidth + 48 * metrics.density);
        }
    }
    
    protected void applyBudgetSettings() {
    	closeBudgetSettings();
    }
    
    protected void closeBudgetSettings() {
    	showingPanel = false;

        activity.stopAnimations();

        // Just in case
        activity.findViewById(R.id.ItemCollection).requestFocus();
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(panel.getWindowToken(), 0);
    	
    	final ViewGroup root = (ViewGroup) activity.getWindow().getDecorView();
    	root.removeView(panelTouchHandler);
    	panelTouchHandler = null;
    	
    	TextView balanceTitleBackend = title;
    	TextView balanceTextBackend = totalSum;
    	
    	final View content = activity.getContentRoot();
    	final View Panel = panel;
    	
    	TextView balanceTitle = (TextView) Panel.findViewById(R.id.TotalTitle);
    	TextView balanceText = (TextView) Panel.findViewById(R.id.TotalText);

        Panel.findViewById(R.id.MenuGraphic).animate()
                .x(totalRoot.getX())
                .setDuration(PanelAnimationDuration)
                .setInterpolator(PanelAnimationInterpolator);
    	
    	content.animate().cancel();
    	content.animate()
    		.alpha(1)
    		.setDuration(PanelAnimationDuration)
    		.setInterpolator(PanelAnimationInterpolator)
    		.setListener(new AnimatorListenerAdapter() {
    			@Override
    			public void onAnimationStart(Animator a) {
    		    	content.setLayerType(View.LAYER_TYPE_HARDWARE, null);
    		    	root.setBackgroundColor(getResources().getColor(R.color.Black));
    			}
    			@Override
    			public void onAnimationEnd(Animator a) {
    				content.setLayerType(View.LAYER_TYPE_NONE, null);
    		    	root.setBackgroundColor(0);
    			}
			});
    	
    	Panel.animate().cancel();
    	Panel.animate()
                .y(root.getHeight() - balanceTitleBackend.getHeight())
                .setDuration(PanelAnimationDuration)
                .setInterpolator(PanelAnimationInterpolator)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator a) {
                        root.removeView(Panel);
                        if (activity != null)
                            activity.resumeAnimations();
                    }
                });
    	
    	panel = null;
    	
    	balanceTitle.animate()
    		.x(totalRoot.getX() + balanceTitleBackend.getLeft() + 1 * metrics.density)
    		.setDuration(PanelAnimationDuration)
    		.setInterpolator(PanelAnimationInterpolator);
    	
    	balanceText.animate()
    		.x(totalRoot.getX() + balanceTextBackend.getLeft())
    		.setDuration(PanelAnimationDuration)
    		.setInterpolator(PanelAnimationInterpolator);
    	

    	View tmp = Panel.findViewById(R.id.BalanceTitle);
    	panelTitleHide(balanceTitleBackend, tmp, false, 0);
    	tmp = Panel.findViewById(R.id.BudgetTitle);
    	panelTitleHide(balanceTitleBackend, tmp, false, 0);
    	tmp = Panel.findViewById(R.id.TaxTitle);
    	panelTitleHide(balanceTitleBackend, tmp, false, 0);
    	
    	tmp = Panel.findViewById(R.id.BalanceText);
    	panelControlHide(balanceTextBackend, tmp, false, 0);
    	tmp = Panel.findViewById(R.id.BudgetEditor);
    	panelControlHide(balanceTextBackend, tmp, false, 0);
    	tmp = Panel.findViewById(R.id.TaxEditor);
    	panelControlHide(balanceTextBackend, tmp, false, 0);
    	
    }
    
    protected void panelTitleHide(View balanceTitleBackend, View tmp, boolean instant, int startDelay) {
    	tmp.animate()
    		.x(balanceTitleBackend.getX())
			.translationY($.dp(48 + 24 * startDelay, activity))
				.setDuration(instant ? 0 : PanelAnimationDuration)
    		.setStartDelay(startDelay * PanelAnimationStepping)
    		.setInterpolator(PanelAnimationInterpolator);
    }
    
    protected void panelControlHide(View balanceTextBackend, View tmp, boolean instant, int startDelay) {
    	tmp.animate()
    		.x(balanceTextBackend.getX())
				.translationY($.dp(48 + 24 * startDelay, activity))
				.setDuration(instant ? 0 : PanelAnimationDuration)
    		.setStartDelay(startDelay * PanelAnimationStepping)
    		.setInterpolator(PanelAnimationInterpolator);
    }

	public void onBudgetExceeded() {
		
		TextView totalSum = (TextView) activity.findViewById(R.id.total_sum);
		totalSum.setTextColor(getResources().getColor(R.color.OverBudget));

        if (panel != null) {
            ((TextView) panel.findViewById(R.id.TotalText)).setTextColor(getResources().getColor(R.color.OverBudget));
        }
		
	}

	public void onBudgetOK() {
		
		TextView totalSum = (TextView) activity.findViewById(R.id.total_sum);
		totalSum.setTextColor(getResources().getColor(R.color.DashboardText));

        if (panel != null) {
            ((TextView) panel.findViewById(R.id.TotalText)).setTextColor(getResources().getColor(R.color.DashboardText));
        }

	}
	
	public boolean handleBackPressed() {
		if (showingPanel) {
			closeBudgetSettings();
			return true;
		}
		if (showingBudget) {
			dismissBudgetEditor(true);
			return true;
		}
		if (showingTax) {
			dismissTaxEditor(true);
			return true;
		}
		return false;
	}
	
	public void finalizeChangesInstantly() {
		if (showingBudget) {
			dismissBudgetEditor(false);
			}
		if (showingTax) {
			dismissTaxEditor(false);
		}
	}

}
