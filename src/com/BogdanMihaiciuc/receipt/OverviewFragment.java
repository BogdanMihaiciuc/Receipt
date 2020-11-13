package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import java.math.BigDecimal;
import java.math.RoundingMode;

//import com.BogdanMihaiciuc.receipt.ReceiptActivity.OverviewFragmentType;

@Deprecated
public class OverviewFragment extends Fragment implements OnEditorActionListener, OnMenuItemClickListener, OnFocusChangeListener {
	
	final static int SetBudget = 1;
	final static int SetTax = 2;
	
	private ReceiptActivity activity; //parent activity, which is cached, since it rarely changes
	
	private TextView budgetText;
	private EditText budgetEditor;
	private TextView totalSum;
	private TextView title;
	
	private ViewGroup totalRoot;
	
	private boolean loading;
	private float density;
	
	private boolean showingTax;
	private boolean hadFocus;
	private View taxRoot;
	
	private PopupMenu popup;
	
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		
		return inflater.inflate(R.layout.fragment_overview, container, false);
		
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		activity = (ReceiptActivity) getActivity();
		
		totalSum = (TextView) activity.findViewById(R.id.total_sum);
		budgetText = (TextView) activity.findViewById(R.id.budget_sum);
		budgetEditor = (EditText) activity.findViewById(R.id.budget_edit);
		title = (TextView) activity.findViewById(R.id.text_total);
		
		budgetEditor.setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (activity == null) return;
				hadFocus = hasFocus;
				if (!hasFocus) {
					activity.getWindow().setSoftInputMode(
						       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
					finishEditingBudget();
				}
				else {
					activity.getWindow().setSoftInputMode(
						       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
				}
			}
		});
		
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT)
			totalRoot = (ViewGroup) getView();
		else
			totalRoot = (ViewGroup) activity.findViewById(R.id.TotalRoot);
		
		DisplayMetrics metrics = new DisplayMetrics();
		activity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
		density = metrics.density;
		
		budgetEditor.setMinWidth((int) (100 * density));
 		
		if (!loading) {
			reinit();
		}
		
		if (showingTax) {
			showingTax = false;
			showTaxEditor(false, hadFocus);
		}
		
	}
	
	public void onDetach() {
		activity = null;
		totalSum = null;
		budgetText = null;
		budgetEditor = null;
		totalRoot = null;
		title = null;
		
		taxRoot = null;
		
		super.onDetach();
	}
	
	public void delayInit() {
		loading = true;
	}
	
	public void initNow() {
		
		if (activity == null)
			loading = false;
		reinit();
		
	}
	
	public void reinit() {
		
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

		/*if (ReceiptActivity.currentLocale.length() > 2) {
			SpannableStringBuilder builder = new SpannableStringBuilder();
			builder.append(ReceiptActivity.currentLocale).append(total.setScale(2, RoundingMode.HALF_EVEN).toString());
			builder.setSpan(new RelativeSizeSpan(0.66f), 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
			totalSum.setText( builder );
		}
		else
			totalSum.setText(ReceiptActivity.currentLocale 
					+ total.setScale(2, RoundingMode.HALF_EVEN).toString());*/
		
		totalSum.setText(ReceiptActivity.totalFormattedString(activity, total));
		
		if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) == 0) {
			budgetText.setText(getResources().getString(R.string.BudgetUnlimited));
			budgetText.setTextColor(getResources().getColorStateList(R.color.unlimited_budget_colors));
		}
		else {
			/*if (ReceiptActivity.currentLocale.length() > 2) {
				SpannableStringBuilder builder = new SpannableStringBuilder();
				builder.append(ReceiptActivity.currentLocale).append(activity.getBudget().setScale(2, RoundingMode.HALF_EVEN).toString());
				builder.setSpan(new RelativeSizeSpan(0.66f), 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				budgetText.setText( builder );
			}
			else
				budgetText.setText(ReceiptActivity.currentLocale 
						+ activity.getBudget().setScale(2, RoundingMode.HALF_EVEN).toString());*/
			budgetText.setText(ReceiptActivity.totalFormattedString(activity, activity.getBudget()));
		}
		if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) != 0 && activity.getBudget().compareTo(total) < 0) {
			int overBudget = getResources().getColor(R.color.OverBudget);
			totalSum.setTextColor(overBudget);
			budgetText.setTextColor(overBudget);
		}
		else {
			onBudgetOK();
		}
		
	}
	

	
	public void showTaxEditor(final boolean animated, final boolean requestFocus) {
		if (showingTax) return;
		showingTax = true;
		
		taxRoot = activity.getLayoutInflater().inflate(R.layout.footer_tax, totalRoot, false);
		totalRoot.addView(taxRoot);
		taxRoot.setClickable(true);

		
		if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
			taxRoot.findViewById(R.id.FooterTitle).setPadding((int)(18 * density), 0, (int)(18 * density), 0);
			RelativeLayout.LayoutParams params = (LayoutParams) taxRoot.getLayoutParams();
			params.addRule(RelativeLayout.ALIGN_PARENT_TOP);
			params.height = (int)(65 * density);
			taxRoot.setLayoutParams(params);
			
			final EditText input = (EditText) taxRoot.findViewById(R.id.FooterInput);
			input.addOnLayoutChangeListener(new OnLayoutChangeListener() {
				@Override
				public void onLayoutChange(View arg0, int arg1, int arg2, int arg3,
						int arg4, int arg5, int arg6, int arg7, int arg8) {
					input.removeOnLayoutChangeListener(this);
					input.setMaxWidth(input.getRight() - ((View)input.getParent()).findViewById(R.id.FooterTitle).getRight());
				}
			});
			
		}
		else {
			RelativeLayout.LayoutParams params = (LayoutParams) taxRoot.getLayoutParams();
			params.addRule(RelativeLayout.ALIGN_TOP, R.id.text_budget);
			params.addRule(RelativeLayout.ALIGN_BOTTOM, R.id.budget_sum);
			taxRoot.setLayoutParams(params);
		}
		
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
	

	public void showBudgetPopup(View v) {
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
	    }
	    popupMenu.add(Menu.NONE, SetBudget, Menu.NONE, R.string.SetBudget);
	    popup.show();
	}
	
	public boolean onMenuItemClick(MenuItem item) {
		//The action depends on which type of menu has been shown
	    switch (item.getItemId()) {
	        case SetBudget:
	        	this.showBudgetEdit();
	            return true;
	        case SetTax:
	        	this.showTaxEditor(true, true);
	        default:
	            return false;
	    }
	}
	
	public void showBudgetEdit() {
		
		if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) != 0) {
			budgetEditor.setText(activity.getBudget().setScale(2, RoundingMode.HALF_EVEN).toString());
		}
		else {
			budgetEditor.setText("0");
		}
		
		budgetText.setVisibility(View.GONE);
		budgetEditor.setVisibility(View.VISIBLE);
		
		budgetEditor.requestFocus();

		activity.getWindow().setSoftInputMode(
			       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
		
		//This brings up the soft keyboard
		 InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
         imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
         
		
		budgetEditor.setOnEditorActionListener(this);
		//budgetEditor.setOnFocusChangeListener(this);
	}

	public void onBudgetChange(BigDecimal newBudget) {
		
		reinit();
		
	}

	public void onTotalChange(BigDecimal newTotal) {
		
		reinit();
		
	}

	public void onBudgetExceeded() {
		
		if (activity == null)
			return;
		
		TextView totalSum = (TextView) activity.findViewById(R.id.total_sum),
				totalBudget = (TextView) activity.findViewById(R.id.budget_sum);
		int overBudget = getResources().getColor(R.color.OverBudget);
		totalSum.setTextColor(overBudget);
		totalBudget.setTextColor(overBudget);
		
	}

	public void onBudgetOK() {
		
		if (activity == null)
			return;
		
		TextView totalSum = (TextView) activity.findViewById(R.id.total_sum),
				totalBudget = (TextView) activity.findViewById(R.id.budget_sum);
		int okBudget = getResources().getColor(R.color.large_price);
		totalSum.setTextColor(okBudget);
		if (activity.getBudget().compareTo(ReceiptActivity.UnlimitedBudget) != 0) totalBudget.setTextColor(okBudget);

	}
	
	@Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (EditorInfo.IME_ACTION_DONE == actionId) {
            finishEditingBudget();
            return true;
        }
        return false;
    }
	
	private void finishEditingBudget() {
		
		if (activity == null)
			return;
		
		//restore the adjust mode to panning
				activity.getWindow().setSoftInputMode(
					       WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN | WindowManager.LayoutParams.SOFT_INPUT_STATE_UNCHANGED);
		
		// Cleanup UI and Events
		budgetEditor.setOnEditorActionListener(null);
		//budgetEditor.setOnFocusChangeListener(null);
		InputMethodManager imm = (InputMethodManager)activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(budgetEditor.getWindowToken(), 0);
		
		budgetText.setVisibility(View.VISIBLE);
		budgetEditor.setVisibility(View.GONE);
		
		// Return input text to activity
        String enteredText = budgetEditor.getText().toString();
        if (enteredText.isEmpty()) {
        	activity.onFinishEditDialog(ReceiptActivity.UnlimitedBudget);
        	return;
        }
        BigDecimal result;
        try {
        	result = new BigDecimal(enteredText);
        } catch (Exception nfe) {
        	//This can occur if the user types in special characters like "-" or ","
        	result = ReceiptActivity.UnlimitedBudget;
        }
        	
        
        activity.onFinishEditDialog(result);
	}

	@Override
	public void onFocusChange(View editText, boolean hasFocus) {
		if (hasFocus == false) {
			finishEditingBudget();
		}
	}
	
	public boolean handleBackPressed() {
		if (budgetEditor.getVisibility() == View.VISIBLE) {
			finishEditingBudget();
			return true;
		}
		if (showingTax) {
			dismissTaxEditor(true);
			return true;
		}
		return false;
	}
	
	public void finalizeChangesInstantly() {
		if (budgetEditor.getVisibility() == View.VISIBLE) {
			finishEditingBudget();
		}
		if (showingTax) {
			dismissTaxEditor(false);
		}
	}

}
