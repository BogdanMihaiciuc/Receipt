package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

import com.BogdanMihaiciuc.receipt.StatsFragment.Precision;
import com.BogdanMihaiciuc.util.LegacyActionBar;

import java.util.ArrayList;
import java.util.Locale;

@Deprecated
public class SearchFragment extends Fragment {
	
	final static boolean DEBUG_SEARCH = false;

    final static int SpinnerID = 1991;
	
	final static String LastPrecisionKey = "lastPrecision";
	final static String LastAggregateKey = "_lastAggregate";
	
	static String[] PrecisionLabels;
//	static String[] PrecisionKeys = {StatsFragment.DateFormatYearPrecision, StatsFragment.DateFormatQuarterPrecision, StatsFragment.DateFormatMonthPrecision,
//									StatsFragment.DateFormatWeekPrecision}; //, StatsFragment.DateFormatWeekdayPrecision, StatsFragment.DateFormatHourOfDayPrecision};
    static String[] PrecisionKeys = {StatsFragment.DateFormatMonthPrecision, StatsFragment.DateFormatQuarterPrecision, StatsFragment.DateFormatYearPrecision};
	
	static String[] AggregateLabels;
	static String[] AggregateKeys = {StatsFragment.StatsTotal, StatsFragment.StatsCount};
	
	//Number and date comparison conditions
	final static int Equals = 0;
	final static int GreaterThan = 1;
	final static int LessThan = 2;
	final static int AtLeast = 3;
	final static int AtMost = 4;
	
	static String[] NumericOperators = {" = ", " > ", " < ", " >= ", " <= "};
	
	//String comparison conditions
	final static int Contains = 0;
	final static int Is = 1;
	final static int StartsWith = 2;
	final static int EndsWith = 3;
	
	static String[] StringOperandPrefix = {"%", "", "", "%"};
	static String[] StringOperandSuffix = {"%", "", "%", ""};
	
	//Merge
	final static int And = 0;
	final static int Or = 1;
	
	final static int NumberArgument = 0;
	final static int CalendarArgument = 1;
	final static int StringArgument = 2;
	
	final static int ReceiptTarget = 0;
	final static int ItemTarget = 1;
	
	static class Condition {
		long number;
		Precision precision;
		String text;
		String field;
		String table;
		
		int argumentType;
		int operator;
		int targetType;
		
		public String getSQL() {
			return getSQLBuffer().toString();
		}
		
		public StringBuffer getSQLBuffer() {
			StringBuffer buffer = new StringBuffer();

			if (argumentType == StringArgument) {
				buffer.append("lower(");
			}
			buffer.append(table).append('.').append(field);
			if (argumentType == StringArgument) {
				buffer.append(") like ");
				buffer.append('?');
			}
			else {
				buffer.append(NumericOperators[operator]).append(number);
			}
			
			return buffer;
		}
		
		public String getText() {
			return StringOperandPrefix[operator] + text + StringOperandSuffix[operator];
		}
		
		public String getSecondaryText() {
			return StringOperandPrefix[Contains] + " " + text + StringOperandSuffix[Contains];
		}
		
	}
	
	static class QueryData {
		
		public String rawQuery;
		public String[] args;
		
		private QueryData() {}
		
		static QueryData createStatQueryData(ArrayList<Condition> conditions, Precision precision) {
			QueryData data = new QueryData();
			
			ArrayList<String> selectionArgs = new ArrayList<String>();
			StringBuilder queryBuffer = new StringBuilder();
			queryBuffer.append("select " + precision.sqlAggregate + "(" + Receipt.DBPriceKey + "), " + Receipt.DBDateKey +
                    "\n from " + Receipt.DBReceiptsTable +
                    "\n where " + Receipt.DBFilenameIdKey +
                    " in (select " + Receipt.DBReceiptsTable + "." + Receipt.DBFilenameIdKey +
                    "\n\t from " + Receipt.DBReceiptsTable + ", " + Receipt.DBItemsTable +
                    "\n\t where " + Receipt.DBReceiptsTable + "." + Receipt.DBFilenameIdKey + " = " + Receipt.DBItemsTable + "." + Receipt.DBTargetDBKey);
			
			if (conditions.size() > 0) {
				queryBuffer.append(" and ");
				int size = conditions.size();
				Condition condition;
				for (int i = 0; i < size; i++) {
					condition = conditions.get(i);
					if (condition.argumentType == StringArgument) queryBuffer.append("(");
					queryBuffer.append(condition.getSQLBuffer());
					if (condition.argumentType == StringArgument) {
						queryBuffer.append(" or ").append(condition.getSQLBuffer()).append(")");
					}
					if (i != size - 1) {
						queryBuffer.append(" and ");
					}
					if (condition.argumentType == StringArgument) {
						selectionArgs.add(condition.getText());
						selectionArgs.add(condition.getSecondaryText());
					}
				}
			}

            if (StatsFragment.DateFormatQuarterPrecision.equals(precision.sqlPrecision)) {
                queryBuffer.append(")\n  group by " + precision.getGrouper(Receipt.DBDateKey));
            }
            else {
			    queryBuffer.append(")\n group by strftime(" + precision.sqlPrecision + ", " + Receipt.DBDateKey + ", 'unixepoch', 'localtime')");
            }
			
			data.rawQuery = queryBuffer.toString();
			data.args = selectionArgs.toArray(new String[selectionArgs.size()]);
			
			if (DEBUG_SEARCH) Log.d("SearchFragment", "Generated stats query " + data.rawQuery + " with arguments " + data.args[0] + " " + data.args[1]);
			
			return data;
		}
		
		static QueryData createHistoryQueryData(ArrayList<Condition> conditions, Precision precision) {
			QueryData data = new QueryData();
			
			ArrayList<String> selectionArgs = new ArrayList<String>();
			StringBuilder queryBuffer = new StringBuilder();
			queryBuffer.append("select ");
			for (int i = 0; i < Receipt.DBAllReceiptColumns.length; i++) {
				queryBuffer.append(Receipt.DBReceiptsTable + "." + Receipt.DBAllReceiptColumns[i]);
				if (i != Receipt.DBAllReceiptColumns.length - 1)
					queryBuffer.append(", ");
			}
			queryBuffer.append(" from " + Receipt.DBReceiptsTable +
					"\n where "  + Receipt.DBFilenameIdKey + 
					"\n in (select " + Receipt.DBReceiptsTable + "." + Receipt.DBFilenameIdKey +
						"\n\t from " + Receipt.DBReceiptsTable + ", " + Receipt.DBItemsTable +
						"\n\t where " + Receipt.DBReceiptsTable + "." + Receipt.DBFilenameIdKey + " = " + Receipt.DBItemsTable + "." + Receipt.DBTargetDBKey );
			
			if (conditions.size() > 0) {
				queryBuffer.append(" and ");
				int size = conditions.size();
				Condition condition;
				for (int i = 0; i < size; i++) {
					condition = conditions.get(i);
					if (condition.argumentType == StringArgument) queryBuffer.append("(");
					queryBuffer.append(condition.getSQLBuffer());
					if (condition.argumentType == StringArgument) {
						queryBuffer.append(" or ").append(condition.getSQLBuffer()).append(")");
					}
					if (i != size - 1) {
						queryBuffer.append(" and ");
					}
					if (condition.argumentType == StringArgument) {
						selectionArgs.add(condition.getText());
						selectionArgs.add(condition.getSecondaryText());
					}
				}
			}
			
			queryBuffer.append(")" +
					"\n order by " + Receipt.DBDateKey + " desc");
			
			data.rawQuery = queryBuffer.toString();
			data.args = selectionArgs.toArray(new String[selectionArgs.size()]);
			
			if (DEBUG_SEARCH) Log.d("SearchFragment", "Generated history query " + data.rawQuery + " with arguments " + data.args[0] + " " + data.args[1]);
			
			return data;
		}
		
	}
	
	static interface ActivityRunnable {
		void run(Activity activity);
	}
	
	private boolean restoreGrouper;
	private boolean showingSearch;
	private boolean restoreConfirmation;
	private boolean peeking;
	private boolean showingSelectionConfirmation;
	
	private HistoryActivity activity;
	private FrameLayout root;	
	
	@SuppressWarnings("unused")
	private int statusBarSize;
	private DisplayMetrics metrics;
	
	private View confirmation;
	private View selectionConfirmation;
	private ViewGroup search;
	
	//Grouper
	private View grouper;
	private int activePrecision;
	private int activeAggregate;
	private String activeSearch;
	
	private Handler handler;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
		setHasOptionsMenu(true);
		metrics = new DisplayMetrics();
		
		PrecisionLabels = new String[] {
				getResources().getString(R.string.Yearly),
                getResources().getString(R.string.Quarterly),
				getResources().getString(R.string.Monthly),
				getResources().getString(R.string.Weekly)//,
//				getResources().getString(R.string.Weekday),
//				getResources().getString(R.string.HourOfDay)
			};
		AggregateLabels = new String[] {
				getResources().getString(R.string.PriceStats),
				getResources().getString(R.string.CountStats)
			};
		activePrecision = 1;
		activeAggregate = 0;
		
		handler = new Handler();
		
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		activity = ((HistoryActivity)getActivity());
		root = (FrameLayout)getActivity().getWindow().getDecorView();
		root = (FrameLayout)((ViewGroup)root.getChildAt(0)).getChildAt(0);

        root = (FrameLayout) activity.findViewById(R.id.LegacyActionBar);

		getActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);
		
		int resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android");
		if (resourceId > 0) {
		    statusBarSize = getResources().getDimensionPixelSize(resourceId);
		}
		else {
			statusBarSize = 0;
		}
		
		if (restoreGrouper) {
			showGrouper();
		}
		
		if (restoreConfirmation) {
			showConfirmation();
		}
		
		if (showingSelectionConfirmation)
			showSelectionConfirmation();
		
		if (showingSearch)
			showSearch();
		
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
		activePrecision = prefs.getInt(LastPrecisionKey, 2);
        if (activePrecision >= PrecisionKeys.length) {
            activePrecision = PrecisionKeys.length - 1;
        }
//		activeAggregate = prefs.getInt(LastAggregateKey, 0);
        activeAggregate = 0;
        LegacyActionBar legacyActionBar = (LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar);
        if (legacyActionBar.findItemWithId(SpinnerID) == null && false) {
            LegacyActionBar.SpinnerItem item = legacyActionBar.addSpinnerToIndex(SpinnerID, 0);
            item.addObjects(PrecisionLabels);
            item.setSelection(activePrecision);
            item.setListener(new LegacyActionBar.LegacySpinnerListener() {
                @Override
                public void onItemSelected(int spinnerID, int index, Object object) {
                    if (activePrecision != index) {
                        activePrecision = index;
                        activity.loadDataForPrecision(Precision.makePrecision(PrecisionKeys[activePrecision], AggregateKeys[activeAggregate]));

                        SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).edit();
                        prefs.putInt(LastPrecisionKey, activePrecision);
                        prefs.apply();

                    }
                }

                @Override
                public void onNothingSelected(int spinnerId) {}
            });
        }
	}
	
	@Override
	public void onDetach() {
		super.onDetach();
		confirmation = null;
		grouper = null;
		root = null;
	}
	
	public boolean onBackPressed() {
		if (confirmation != null) {
			hideConfirmation();
            return true;
        }
		else if (grouper != null) {
			hideGrouper();
            return true;
        }
		else if (search != null) {
			hideSearch();
            return true;
        }
		else
			return false;
	}
	
	public void showConfirmation() {

		LayoutInflater inflater = getActivity().getLayoutInflater();
		
		confirmation = inflater.inflate(R.layout.layout_actionbar_generic_confirmation, root, false);
		
		root.addView(confirmation);
		if (!restoreConfirmation) {
			confirmation.setTranslationX(100 * metrics.density);
			confirmation.setAlpha(0);
			confirmation.animate()
				.alpha(1).translationX(0)
				.setDuration(250)
				.setInterpolator(new DecelerateInterpolator(2f));
			restoreConfirmation = true;
		}
		
		confirmation.findViewById(R.id.ConfirmBack).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				hideConfirmation();
			}
		});
		
		confirmation.findViewById(R.id.ConfirmCancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				hideConfirmation();
			}
		});
		
		confirmation.findViewById(R.id.ConfirmOK).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				v.setOnClickListener(null);
				activity.clearHistory();
			}
		});
		
	}
	
	public void hideConfirmation() {
		if (!restoreConfirmation) return;
		restoreConfirmation = false;
		
		final View ClosingConfirmation = confirmation;
		
		confirmation.animate()
			.alpha(0).translationX(100 * metrics.density)
			.setDuration(250)
			.setInterpolator(new AccelerateInterpolator(2f))
			.setListener(new AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {}
				@Override
				public void onAnimationRepeat(Animator animation) {}
				@Override
				public void onAnimationEnd(Animator animation) {
					if (root != null)
						root.removeView(ClosingConfirmation);
				}
				@Override
				public void onAnimationCancel(Animator animation) {}
			});
		confirmation = null;
	}
	
	public void showGrouper() {
		LayoutInflater inflater = getActivity().getLayoutInflater();
		
		grouper = inflater.inflate(R.layout.layout_actionbar_grouper, root, false);
		grouper.setClickable(true);
		
		root.addView(grouper);
		if (!restoreGrouper) {
			grouper.setTranslationX(100 * metrics.density);
			grouper.setAlpha(0);
			grouper.animate()
				.alpha(1).translationX(0)
				.setDuration(250)
				.setInterpolator(new DecelerateInterpolator(2f));
			restoreGrouper = true;
		}
		Spinner groups = (Spinner)grouper.findViewById(R.id.GrouperSpinner);
		groups.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.layout_spinner_item, PrecisionLabels) {


			public TextView getView(int position, View convertView, ViewGroup parent) {
				TextView v = (TextView) super.getView(position, convertView, parent);
				v.setTextSize(12);
				v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
				v.setAllCaps(true);
				return v;
			}
			
			public TextView getDropDownView(int position, View convertView, ViewGroup parent) {
				TextView v = (TextView) super.getView(position, convertView, parent);
				if (position == activePrecision)
					v.setBackgroundResource(R.drawable.selected_scrap);
				else
					v.setBackgroundResource(R.drawable.unselected_scrap);
				return v;
			}
			
		});
		groups.setSelection(activePrecision);
		groups.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> spinner, View item,
					int position, long id) {
				if (activePrecision != position) {
					activePrecision = position;
					activity.loadDataForPrecision(Precision.makePrecision(PrecisionKeys[activePrecision], AggregateKeys[activeAggregate]));
					
					SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).edit();
					prefs.putInt(LastPrecisionKey, activePrecision);
					prefs.apply();
					
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {}
		});
		Spinner types = (Spinner)grouper.findViewById(R.id.GrouperTypeSpinner);
		types.setAdapter(new ArrayAdapter<String>(getActivity(), R.layout.layout_spinner_item, AggregateLabels) {


			public TextView getView(int position, View convertView, ViewGroup parent) {
				TextView v = (TextView) super.getView(position, convertView, parent);
				v.setTextSize(12);
				v.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
				v.setAllCaps(true);
				return v;
			}
			
			public TextView getDropDownView(int position, View convertView, ViewGroup parent) {
				TextView v = (TextView) super.getView(position, convertView, parent);
				if (position == activeAggregate)
					v.setBackgroundResource(R.drawable.selected_scrap);
				else
					v.setBackgroundResource(R.drawable.unselected_scrap);
				return v;
			}
			
		});
		types.setSelection(activeAggregate);
		types.setOnItemSelectedListener(new OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> spinner, View item,
					int position, long id) {
				if (activeAggregate != position) {
					activeAggregate = position;
					activity.loadDataForPrecision(Precision.makePrecision(PrecisionKeys[activePrecision], AggregateKeys[activeAggregate]));
					
					SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).edit();
					prefs.putInt(LastAggregateKey, activeAggregate);
					prefs.apply();
					
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0) {}
		});
		grouper.findViewById(R.id.GrouperBack).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				hideGrouper();
			}
		});
		
		if (peeking) {
			grouper.setAlpha(0);
			grouper.setVisibility(View.GONE);
		}
	}
	
	public void hideGrouper() {
		restoreGrouper = false;
		final View closingGrouper = grouper;
		grouper.findViewById(R.id.GrouperBack).setOnClickListener(null);
		grouper.animate()
			.alpha(0).translationX(100 * metrics.density)
			.setDuration(250)
			.setInterpolator(new AccelerateInterpolator(2f))
			.setListener(new AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {}
				@Override
				public void onAnimationRepeat(Animator animation) {}
				@Override
				public void onAnimationEnd(Animator animation) {
					if (root != null)
						root.removeView(closingGrouper);
				}
				@Override
				public void onAnimationCancel(Animator animation) {}
			});
		grouper = null;
	}
	
	final static long SearchDelay = 500;
	final private Runnable ProcessSearchRunnable = new Runnable() {
		ArrayList<Condition> conditionList = new ArrayList<Condition>();
		public void run() {
			if (activeSearch != null) {
				Condition itemCondition = new Condition();
				itemCondition.argumentType = StringArgument;
				itemCondition.field = Receipt.DBNameKey;
				itemCondition.table = Receipt.DBItemsTable;
				itemCondition.text = activeSearch.toLowerCase(Locale.getDefault());
				itemCondition.operator = StartsWith;
				
				conditionList.clear();
				conditionList.add(itemCondition);
				
				Precision precision = Precision.makePrecision(PrecisionKeys[activePrecision], AggregateKeys[activeAggregate]);
				
				QueryData historyData = QueryData.createHistoryQueryData(conditionList, precision);
				QueryData statsData = QueryData.createStatQueryData(conditionList, precision);
				
				if (activity != null)
					activity.loadDirectQuery(historyData, statsData, precision);
			}
		}
	};
	
	public void showSearch() {
		LayoutInflater inflater = getActivity().getLayoutInflater();
		
		search = (ViewGroup) inflater.inflate(R.layout.layout_actionbar_searcher, root, false);
		search.setClickable(true);
		
		root.addView(search);
		if (!showingSearch) {
			search.setTranslationX(100 * metrics.density);
			search.setAlpha(0);
			search.animate()
				.alpha(1).translationX(0)
				.setDuration(250)
				.setInterpolator(new DecelerateInterpolator(2f));
			showingSearch = true;
		}

		((EditText)search.getChildAt(1)).setText(activeSearch);
		((EditText)search.getChildAt(1)).addTextChangedListener(new TextWatcher() {
			@Override
			public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
				String newTerms = arg0.toString().trim();
				if (activeSearch==null || !activeSearch.equalsIgnoreCase(newTerms)) {
					activeSearch = newTerms.toLowerCase(Locale.getDefault());
					handler.removeCallbacks(ProcessSearchRunnable);
					handler.postDelayed(ProcessSearchRunnable, SearchDelay);
				}
			}
			
			@Override
			public void beforeTextChanged(CharSequence arg0, int arg1, int arg2,
					int arg3) {}
			
			@Override
			public void afterTextChanged(Editable arg0) {}
		});
		((EditText)search.getChildAt(1)).setOnEditorActionListener(new OnEditorActionListener() {
			@Override
			public boolean onEditorAction(TextView arg0, int arg1, KeyEvent arg2) {
				if (arg1 == EditorInfo.IME_ACTION_SEARCH) {
					InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
					imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
				}
				return false;
			}
		});
		search.findViewById(R.id.SearchBack).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				hideSearch();
			}
		});
		
		if (peeking) {
			search.setAlpha(0);
			search.setVisibility(View.GONE);
		}

		search.getChildAt(1).requestFocus();
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(search.getChildAt(1), InputMethodManager.SHOW_FORCED);
	}
	
	public void hideSearch() {
		handler.removeCallbacks(ProcessSearchRunnable);
		activity.loadDataForPrecision(Precision.makePrecision(PrecisionKeys[activePrecision], AggregateKeys[activeAggregate]));
		InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(search.getWindowToken(), 0);
		showingSearch = false;
		activeSearch = "";
		final View ClosingSearch = search;
		search.findViewById(R.id.SearchBack).setOnClickListener(null);
		search.animate()
			.alpha(0).translationX(100 * metrics.density)
			.setDuration(250)
			.setInterpolator(new AccelerateInterpolator(2f))
			.setListener(new AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {}
				@Override
				public void onAnimationRepeat(Animator animation) {}
				@Override
				public void onAnimationEnd(Animator animation) {
					if (root != null)
						root.removeView(ClosingSearch);
				}
				@Override
				public void onAnimationCancel(Animator animation) {}
			});
		search = null;
	}

    // peeking is now deprecated
	public void startPeeking() {
        if (true) return;
		peeking = true;
		if (restoreConfirmation && confirmation != null)
			hideConfirmation();
		if (restoreGrouper && grouper != null)
			grouper.animate()
				.alpha(0)
				.setDuration(150)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationEnd(Animator a) {
						if (grouper != null) grouper.setVisibility(View.INVISIBLE);
					}
				});
		if (showingSearch && search != null)
			search.animate()
				.alpha(0)
				.setDuration(150)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationEnd(Animator a) {
						if (search != null) search.setVisibility(View.INVISIBLE);
					}
				});
	}
	
	public void stopPeeking() {
        if (true) return;
		peeking = false;
		if (restoreGrouper)
			grouper.animate()
				.alpha(1)
				.setDuration(150)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationStart(Animator a) {
						if (grouper != null) grouper.setVisibility(View.VISIBLE);
					}
				});
		if (showingSearch) {
			search.animate()
				.alpha(1)
				.setDuration(150)
				.setListener(new AnimatorListenerAdapter() {
					public void onAnimationStart(Animator a) {
						if (search != null) search.setVisibility(View.VISIBLE);
					}
				});
		}
		if (showingSelectionConfirmation)
			hideSelectionConfirmation();
	}
	
	public void showSelectionConfirmation() {

		LayoutInflater inflater = getActivity().getLayoutInflater();
		
		selectionConfirmation = inflater.inflate(R.layout.layout_actionbar_generic_confirmation, root, false);
		
		root.addView(selectionConfirmation);
		if (!showingSelectionConfirmation) {
			selectionConfirmation.setTranslationX(100 * metrics.density);
			selectionConfirmation.setAlpha(0);
			selectionConfirmation.animate()
				.alpha(1).translationX(0)
				.setDuration(250)
				.setInterpolator(new DecelerateInterpolator(2f));
			showingSelectionConfirmation = true;
		}
		
		((TextView)selectionConfirmation.findViewById(R.id.ConfirmBack)).setText(R.string.ConfirmSelection);
		
		selectionConfirmation.findViewById(R.id.ConfirmBack).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				hideSelectionConfirmation();
			}
		});
		
		selectionConfirmation.findViewById(R.id.ConfirmCancel).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				hideSelectionConfirmation();
			}
		});
		
		selectionConfirmation.findViewById(R.id.ConfirmOK).setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				v.setOnClickListener(null);
				hideSelectionConfirmation();
				activity.deleteSelection();
			}
		});
		
	}
	
	public void hideSelectionConfirmation() {
		if (!showingSelectionConfirmation) return;
		showingSelectionConfirmation = false;
		
		final View ClosingSelectionConfirmation = selectionConfirmation;
		
		selectionConfirmation.animate()
			.alpha(0).translationX(100 * metrics.density)
			.setDuration(250)
			.setInterpolator(new AccelerateInterpolator(2f))
			.setListener(new AnimatorListener() {
				@Override
				public void onAnimationStart(Animator animation) {}
				@Override
				public void onAnimationRepeat(Animator animation) {}
				@Override
				public void onAnimationEnd(Animator animation) {
					if (root != null)
						root.removeView(ClosingSelectionConfirmation);
				}
				@Override
				public void onAnimationCancel(Animator animation) {}
			});
		selectionConfirmation = null;
	}
	
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.search_fragment, menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		return onOptionsIdSelected(item.getItemId());
	}

    public boolean onOptionsIdSelected(int id) {
        switch(id) {
            case R.id.menu_grouper: {
                showGrouper();
                return true;
            }
            case R.id.menu_search: {
                showSearch();
                return true;
            }
        }
        return false;
    }
	
	@SuppressLint("DefaultLocale")
	public String getSearchTerms() {
		if (showingSearch)
			if (activeSearch != null)
				if (!activeSearch.trim().isEmpty())
					return activeSearch.trim().toLowerCase(Locale.getDefault());
		return null;
	}
	
}
