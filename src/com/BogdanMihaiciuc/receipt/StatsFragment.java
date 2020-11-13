package com.BogdanMihaiciuc.receipt;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.receipt.SearchFragment.QueryData;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.GraphView;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.MenuPopover;
import com.BogdanMihaiciuc.util.Popover;
import com.BogdanMihaiciuc.util.PrecisionRangeSlider;
import com.BogdanMihaiciuc.util.TagView;
import com.BogdanMihaiciuc.util.TooltipPopover;
import com.BogdanMihaiciuc.util.Utils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

@SuppressWarnings("deprecation")
public class StatsFragment extends Fragment {

    final static String TAG = StatsFragment.class.getName();
	
	final static boolean DEBUG = false;
	static final boolean DEBUG_LONG_OPERATION = false;
    final static boolean DEBUG_OVERLAYS = false;
    final static boolean USE_FAST_CALCULATOR = true;
    final static boolean DEBUG_FAST_CALCULATOR = false;
	static final long LONG_OPERATION_DELAY = 2000;

    // When an item's price * qty are greater then the OverflowMagicValue
    // they will overflow when multiplied by tax
    final static long OverflowMagicValue = 99999980000001L;
	
	static class StatItem {
		// Data related to the statItem
		long total;
		float percentageOfHighest;
		long unixDate;
		StatItem(long total) {
			this.total = total;
		}
		StatItem(long total, long unixDate) {
			this.total = total;
			this.unixDate = unixDate;
		}
	}

	static class StatData {
		long totalOverall;
		long average;
		long highestPrice;
		ArrayList<StatItem> items;
	}
	
	
	// Precision class
	static interface TitleGetter {
		public String getTitle(Calendar date);
	}
	static interface ContextTitleGetter {
		public CharSequence getTitle(Context context, Calendar date);
	}
	static interface ValueGetter {
		public CharSequence getValue(Context context, long value);
	}

    final static int QuarterDivisor = 3;
	
	static class Precision {
		String sqlPrecision;
		String sqlAggregate;
		
		int outerField;
		int outerFieldMultiplier;
		int innerField;
		
		TitleGetter title;
		TitleGetter subtitle;
		TitleGetter verboseTitle;
        ContextTitleGetter spinnerTitle;
		ContextTitleGetter sectionTitle;
        ContextTitleGetter sectionSubtitle;
		
		ValueGetter value;
		
		public boolean areDatesEqual(Calendar date1, Calendar date2) {
			return getComparator(date1) == getComparator(date2);
		}
		
		public int getComparator(Calendar date) {
			if (sqlPrecision.equals(DateFormatWeekPrecision))
				return date.get(Calendar.YEAR) * 100000 + date.get(Calendar.MONTH) * 100 + date.get(Calendar.WEEK_OF_YEAR);
            if (sqlPrecision.equals(DateFormatQuarterPrecision))
                return date.get(Calendar.YEAR) * 10 + ((date.get(Calendar.MONTH)) / QuarterDivisor);
			return date.get(outerField) * outerFieldMultiplier + date.get(innerField);
		}

        public int getComparator(long unixTime) {
            Calendar c = Calendar.getInstance();
            c.setTimeInMillis(unixTime);
            return getComparator(c);
        }

        public String getGrouper(String field) {
            if (sqlPrecision.equals(DateFormatQuarterPrecision)) {
                return "(" +
                        "(cast(strftime('%Y', " + field + ", 'unixepoch', 'localtime') as integer) * 10) + " +
                        "(cast(strftime('%m', " + field + ", 'unixepoch', 'localtime') as integer) + 2) / 3)";
            }
            else {
                return "strftime(" + sqlPrecision + ", " + field + ", 'unixepoch', 'localtime')";
            }
        }
		
		static Precision makePrecision(String precision) {
			return makePrecision(precision, StatsTotal);
		}

        static Precision makeNextPrecision(String precision) {
            if (precision.equals(DateFormatQuarterPrecision) || precision.equals(DateFormatYearPrecision))
                return makePrecision(DateFormatMonthPrecision);
            if (precision.equals(DateFormatMonthPrecision))
                return makePrecision(DateFormatWeekPrecision);
            return makePrecision(DateFormatMonthPrecision);
        }
		
		static Precision makePrecision(String precision, String aggregate) {
			Precision precisionData = new Precision();
			
			precisionData.sqlPrecision = precision;
			precisionData.sqlAggregate = aggregate;
			if (precision.equals(DateFormatYearPrecision)) {
				precisionData.outerField = Calendar.YEAR;
				precisionData.outerFieldMultiplier = 0;
				precisionData.innerField = Calendar.YEAR;
				precisionData.title = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return "'" + String.valueOf(date.get(Calendar.YEAR)).substring(2);
					}
				};
				precisionData.subtitle = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return null;
					}
				};
				precisionData.verboseTitle = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return String.valueOf(date.get(Calendar.YEAR));
					}
				};
				precisionData.sectionTitle = new ContextTitleGetter() {
					@Override
					public String getTitle(Context context, Calendar date) {
						return String.valueOf(date.get(Calendar.YEAR));
					}
				};
                precisionData.spinnerTitle = precisionData.sectionTitle;
                precisionData.sectionSubtitle = precisionData.sectionTitle;
			}
            else if (precision.equals(DateFormatQuarterPrecision)) {
                precisionData.outerField = Calendar.YEAR;
                precisionData.outerFieldMultiplier = 10;
                precisionData.innerField = Calendar.MONTH;
                precisionData.title = new TitleGetter() {
                    @Override
                    public String getTitle(Calendar date) {
                        return String.valueOf((date.get(Calendar.MONTH) + 3) / QuarterDivisor);
                    }
                };
                precisionData.subtitle = new TitleGetter() {
                    @Override
                    public String getTitle(Calendar date) {
                        if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                            return null;
                        }
                        else {
                            return String.valueOf(date.get(Calendar.YEAR));
                        }
                    }
                };
                precisionData.verboseTitle = new TitleGetter() {
                    public String getTitle(Calendar date) {
                        return String.format(Receipt.getStaticContext().getString(R.string.QuarterDisplayCompact), ((date.get(Calendar.MONTH) + 3) / QuarterDivisor), date.get(Calendar.YEAR));
                    }
                };
                precisionData.sectionTitle = new ContextTitleGetter() {
                    public String getTitle(Context context, Calendar date) {
                        if ((date.get(Calendar.MONTH)) / QuarterDivisor == (Calendar.getInstance().get(Calendar.MONTH)) / QuarterDivisor
                                && date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                            return (context.getResources().getString(R.string.HeaderThisQuarter));
                        }
                        else {
                            if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                                return String.format(Receipt.getStaticContext().getString(R.string.QuarterDisplayCurrent), ((date.get(Calendar.MONTH) + 3) / QuarterDivisor));
                            }
                            else {
                                return String.format(Receipt.getStaticContext().getString(R.string.QuarterDisplayFormer), ((date.get(Calendar.MONTH) + 3) / QuarterDivisor), date.get(Calendar.YEAR));
                            }
                        }
                    }
                };
                precisionData.spinnerTitle = precisionData.sectionTitle;
                precisionData.sectionSubtitle = new ContextTitleGetter() {
                    public String getTitle(Context context, Calendar date) {
                        if ((date.get(Calendar.MONTH)) / QuarterDivisor == (Calendar.getInstance().get(Calendar.MONTH)) / QuarterDivisor
                                && date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                            return (context.getResources().getString(R.string.HeaderThisQuarter));
                        }
                        else {
                            if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                                return String.format(Receipt.getStaticContext().getString(R.string.QuarterDisplayCurrent), ((date.get(Calendar.MONTH) + 3) / QuarterDivisor));
                            }
                            else {
                                return String.format(Receipt.getStaticContext().getString(R.string.QuarterDisplayCompact), ((date.get(Calendar.MONTH) + 3) / QuarterDivisor), date.get(Calendar.YEAR));
                            }
                        }
                    }
                };
            }
			else if (precision.equals(DateFormatMonthPrecision)) {
				precisionData.outerField = Calendar.YEAR;
				precisionData.outerFieldMultiplier = 100;
				precisionData.innerField = Calendar.MONTH;
				precisionData.title = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
					}
				};
				precisionData.subtitle = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
                        if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                            return null;
                        }
                        else {
                            return "'" + String.valueOf(date.get(Calendar.YEAR) % 100);
                        }
					}
				};
				precisionData.verboseTitle = new TitleGetter() {
					public String getTitle(Calendar date) {
                        if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                            return date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
                        }
                        else {
                            return date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()) + " " +
                                    date.get(Calendar.YEAR);
                        }
					}
				};
				precisionData.sectionTitle = new ContextTitleGetter() {
					public CharSequence getTitle(Context context, Calendar date) {
                        if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                            return date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault());
                        }
                        else {
                            SpannableStringBuilder text = new SpannableStringBuilder();
                            text.append(date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()));
                            Utils.appendWithSpan(text, " " + date.get(Calendar.YEAR), new ForegroundColorSpan(context.getResources().getColor(R.color.DashboardTitle)));
                            return text;
                        }
					}
				};
                precisionData.spinnerTitle = precisionData.sectionTitle;
                precisionData.sectionSubtitle = new ContextTitleGetter() {
                    public CharSequence getTitle(Context context, Calendar date) {
                        if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                            return date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
                        }
                        else {
                            SpannableStringBuilder text = new SpannableStringBuilder();
                            text.append(date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()));
                            Utils.appendWithSpan(text, " " + date.get(Calendar.YEAR), new ForegroundColorSpan(context.getResources().getColor(R.color.DashboardTitle)));
                            return text;
                        }
                    }
                };
			}
			else if (precision.equals(DateFormatWeekPrecision)) {
				precisionData.outerField = Calendar.YEAR;
				precisionData.outerFieldMultiplier = 1000;
				precisionData.innerField = Calendar.WEEK_OF_YEAR;
				precisionData.title = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return "W" + date.get(Calendar.WEEK_OF_MONTH);
					}
				};
				precisionData.subtitle = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
					}
				};
				precisionData.verboseTitle = new TitleGetter() {
					public String getTitle(Calendar date) {
						return "Week " + date.get(Calendar.WEEK_OF_MONTH) + ", " + 
								date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
					}
				};
                precisionData.spinnerTitle = new ContextTitleGetter() {
                    public String getTitle(Context context, Calendar date) {
                        if (date.get(Calendar.WEEK_OF_YEAR) == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
                            return (context.getResources().getString(R.string.HeaderThisWeek));
                        }
                        else {
                            if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                                return date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) + ", " + "week " + date.get(Calendar.WEEK_OF_MONTH);
                            }
                            else {
                                return date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) + " " + date.get(Calendar.YEAR) + ", " + "week " + date.get(Calendar.WEEK_OF_MONTH);
                            }
                        }
                    }
                };
				precisionData.sectionTitle = new ContextTitleGetter() {
					public String getTitle(Context context, Calendar date) {
						if (date.get(Calendar.WEEK_OF_YEAR) == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
							return (context.getResources().getString(R.string.HeaderThisWeek));
						}
						else {
							if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
								return "Week " + date.get(Calendar.WEEK_OF_MONTH) + ", " + 
										date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault());
							}
							else
								return "Week " + date.get(Calendar.WEEK_OF_MONTH) + ", " + 
										date.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault()) + " " + 
										date.get(Calendar.YEAR);
						}
					}
				};
                precisionData.sectionSubtitle = new ContextTitleGetter() {
                    public String getTitle(Context context, Calendar date) {
                        if (date.get(Calendar.WEEK_OF_YEAR) == Calendar.getInstance().get(Calendar.WEEK_OF_YEAR)) {
                            return (context.getResources().getString(R.string.HeaderThisWeek));
                        }
                        else {
                            if (date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
                                return "Week " + date.get(Calendar.WEEK_OF_MONTH);
                            }
                            else
                                return "Week " + date.get(Calendar.WEEK_OF_MONTH);
                        }
                    }
                };
			}
			else if (precision.equals(DateFormatWeekdayPrecision)) {
				precisionData.outerField = Calendar.YEAR;
				precisionData.outerFieldMultiplier = 0;
				precisionData.innerField = Calendar.DAY_OF_WEEK;
				precisionData.title = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return date.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault());
					}
				};
				precisionData.subtitle = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return null;
					}
				};
				precisionData.verboseTitle = new TitleGetter() {
					public String getTitle(Calendar date) {
						return date.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
					}
				};
				precisionData.sectionTitle = new ContextTitleGetter() {
					public String getTitle(Context context, Calendar date) {
						return date.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale.getDefault());
					}
				};
                precisionData.spinnerTitle = precisionData.sectionTitle;
                precisionData.sectionSubtitle = precisionData.sectionTitle;
			}
			else if (precision.equals(DateFormatHourOfDayPrecision)) {
				precisionData.outerField = Calendar.YEAR;
				precisionData.outerFieldMultiplier = 0;
				precisionData.innerField = Calendar.HOUR_OF_DAY;
				precisionData.title = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return String.valueOf(date.get(Calendar.HOUR_OF_DAY));
					}
				};
				precisionData.subtitle = new TitleGetter() {
					@Override
					public String getTitle(Calendar date) {
						return null;
					}
				};
				precisionData.verboseTitle = new TitleGetter() {
					public String getTitle(Calendar date) {
						return String.valueOf(date.get(Calendar.HOUR_OF_DAY));
					}
				};
				precisionData.sectionTitle = new ContextTitleGetter() {
					public String getTitle(Context context, Calendar date) {
						return String.valueOf(date.get(Calendar.HOUR_OF_DAY));
					}
				};
                precisionData.spinnerTitle = precisionData.sectionTitle;
                precisionData.sectionSubtitle = precisionData.sectionTitle;
			}
			
			if (aggregate.equals(StatsTotal)) {
				precisionData.value = new ValueGetter() {
					public CharSequence getValue(Context context, long value) {
						
						return ReceiptActivity.totalFormattedStringWithSpecifiedCutoff(context, 
								new java.math.BigDecimal(value).movePointLeft(2), 
								ReceiptActivity.PortraitInitialCutoff);
						
//						if (ReceiptActivity.currentLocale.length() > 2) {
//							SpannableStringBuilder builder = new SpannableStringBuilder();
//							builder.append(ReceiptActivity.currentLocale).append(ReceiptActivity.totalToTruncatedDecimalString(value));
//							builder.setSpan(new ForegroundColorSpan(context.getResources().getColor(R.color.crossedoff_text_colors)), 
//									0, ReceiptActivity.currentLocale.length(), 
//									SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
//							builder.setSpan(new RelativeSizeSpan(0.66f), 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
//							return builder;
//						}
//						else
//							return ReceiptActivity.currentLocale + ReceiptActivity.totalToTruncatedDecimalString(value);
					}
				};
			}
			else if (aggregate.equals(StatsCount)) {
				precisionData.value = new ValueGetter() {
					public CharSequence getValue(Context context, long value) {
						return String.valueOf(value);
					}
				};
			}
			
			return precisionData;
		}
	}
	
	// These precisions are continuous
	final static String DateFormatMonthPrecision = "'%Y-%m'";
	final static String DateFormatDatePrecision = "'%Y-%j'";
	final static String DateFormatYearPrecision = "'%Y'";
	final static String DateFormatWeekPrecision = "'%Y-%m-%W'";
    final static String DateFormatQuarterPrecision = "'%Y-%m'  ";
	
	// These precisions are not guaranteed to be continuous;
	final static String DateFormatWeekdayPrecision = "'%w'";
	final static String DateFormatHourOfDayPrecision = "'%H'";
	final static String DateFormatYearmonthPercision = "'%m'";
	
	final static String StatsTotal = "sum";
	final static String StatsAverage = "avg";
	final static String StatsCount = "count";

    final static int MaximumPhoneOverlays = 4;
    final static int MaximumMiniTabletOverlays = 6;
    final static int MaximumTabletOverlays = 12;

    static String[] PrecisionLabels;
	
	private ArrayList<IndicatorFragment.Task> taskStack = new ArrayList<IndicatorFragment.Task>();
	private ArrayList<IndicatorFragment.Task> finishedTaskStack = new ArrayList<IndicatorFragment.Task>();

    class IntervalList {
        ArrayList<Long> months, quarters, years;
    }

    class IntervalFinderTask extends AsyncTask<Void, Void, IntervalList> {

        @Override
        protected IntervalList doInBackground(Void... voids) {
            ArrayList<Long> months = new ArrayList<Long>();
            ArrayList<Long> quarters = new ArrayList<Long>();
            ArrayList<Long> years = new ArrayList<Long>();

            long lastQuarter = Long.MIN_VALUE, lastYear = Long.MIN_VALUE;
            Calendar workCalendar = Calendar.getInstance();

            synchronized (Receipt.DatabaseLock) {
                Precision monthPrecision = Precision.makePrecision(DateFormatMonthPrecision);
                Precision quarterPrecision = Precision.makePrecision(DateFormatQuarterPrecision);
                Precision yearPrecision = Precision.makePrecision(DateFormatYearPrecision);

                SQLiteDatabase database = Receipt.DBHelper.getReadableDatabase();
                Cursor baseIntervals = Receipt.queryDatabase(database)
                        .selectColumns(Receipt.DBDateKey)
                        .fromTable(Receipt.DBReceiptsTable)
                        .groupBy(monthPrecision.getGrouper(Receipt.DBDateKey))
                        .orderBy(Receipt.DBDateKey)
                        .execute();

                while (baseIntervals.moveToNext()) {
                    months.add(baseIntervals.getLong(0));
                    workCalendar.setTimeInMillis(baseIntervals.getLong(0) * 1000);

                    if (lastQuarter != quarterPrecision.getComparator(workCalendar)) {
                        lastQuarter = quarterPrecision.getComparator(workCalendar);
                        quarters.add(baseIntervals.getLong(0));
                    }

                    if (lastYear != yearPrecision.getComparator(workCalendar)) {
                        lastYear = yearPrecision.getComparator(workCalendar);
                        years.add(baseIntervals.getLong(0));
                    }

                }

                baseIntervals.close();
                database.close();
            }

            IntervalList intervals = new IntervalList();
            intervals.months = months;
            intervals.quarters = quarters;
            intervals.years = years;

            return intervals;
        }

        @Override
        protected void onPostExecute(IntervalList result) {
            intervals = result;
        }

    }
	
	class GenerateStatsTask extends AsyncTask <Receipt.DatabaseHelper, Void, StatData> {
		
		private IndicatorFragment.Task task;
		private IndicatorFragment indicator;
		//private String precision;
		private Precision precisionData;
		private long unixTimeStart;
		private long unixTimeEnd;
		//private String statsFunction;
		
		GenerateStatsTask(Precision precision, long rangeStart, long rangeEnd) {
			//this.precision = precision.sqlPrecision;
			unixTimeStart = rangeStart;
			unixTimeEnd = rangeEnd;
			//statsFunction = precision.sqlAggregate;
			precisionData = precision;
		}
		
		@Override
		protected void onPreExecute() {
			task = IndicatorFragment.Task.createTask("Loading", null);
			if (activity != null) {
				indicator = activity.getIndicator();
				indicator.startWorking(task);
			}
			else {
				taskStack.add(task);
			}
		}
		
		@Override
		protected StatData doInBackground(Receipt.DatabaseHelper... arg0) {
			synchronized(Receipt.DatabaseLock) {
				SQLiteDatabase database = arg0[0].getReadableDatabase();
				
				// Sleep for 2 seconds to simulate a long loading time
				if (DEBUG_LONG_OPERATION) {
					try {
						Thread.sleep(LONG_OPERATION_DELAY);
					}
					catch (Throwable e) {}
				}
				
				// List of totals
				ArrayList<StatItem> items = new ArrayList<StatItem>();
				long total = 0;
				long average;
				long highestPrice = 0;
				
				// TODO cache totals to prevent long queries
				Cursor totalList;
				if (unixTimeStart != -1) 
					totalList = database.rawQuery(
						//"select sum(" + Receipt.DBPriceKey + "),  strftime('%s', " + Receipt.DBDateKey + ", 'unixepoch')" + 
						"select " + precisionData.sqlAggregate + "(" + Receipt.DBPriceKey + "), " + Receipt.DBDateKey + 
						" from " + Receipt.DBReceiptsTable +
						" group by " + precisionData.getGrouper(Receipt.DBDateKey) +
						" having " + Receipt.DBDateKey + " between " + unixTimeStart + " and " + unixTimeEnd, 
						null);
				else
					totalList = database.rawQuery(
							//"select sum(" + Receipt.DBPriceKey + "),  strftime('%s', " + Receipt.DBDateKey + ", 'unixepoch')" + 
							"select" + precisionData.sqlAggregate + "(" + Receipt.DBPriceKey + "), " + Receipt.DBDateKey + 
							" from " + Receipt.DBReceiptsTable +
                            " group by " + precisionData.getGrouper(Receipt.DBDateKey),
							null);
				
				if (totalList != null) {
					if (isCancelled()) {
						totalList.close();
						database.close();
						return null;
					}
					
					int resultSize = totalList.getCount();
					for (int i = 0; i < resultSize; i++) {
	
						if (isCancelled()) {
							totalList.close();
							database.close();
							return null;
						}
						
						totalList.moveToPosition(i);
						
						// average is used as a temporary value here since it's actually computed after the loop has completed
						average = totalList.getLong(0);
						items.add(new StatItem(average, totalList.getLong(1)));
						if (average > highestPrice) {
							highestPrice = average;
						}
						
					}
					
					totalList.close();
				}
				
				
				// Overall Average; this includes months not displayed on the graph stats
				average = 0;
				Cursor totalAverage = database.rawQuery("select avg(price) from " +
								"(select " + precisionData.sqlAggregate + "("+Receipt.DBPriceKey+") as price from "+Receipt.DBReceiptsTable+
                                    " group by " + precisionData.getGrouper(Receipt.DBDateKey) + ")",
						null);
				
				if (totalAverage != null) {
	
					if (isCancelled()) {
						totalAverage.close();
						database.close();
						return null;
					}
					
					if (totalAverage.getCount() > 0) {
						totalAverage.moveToFirst();
						average = totalAverage.getLong(0);
					}
					totalAverage.close();
				}
				
				//Because the average can be higher than displayed months
				//if months not displayed have high price values
				if (average > highestPrice) {
					highestPrice = average;
				}
				
				Cursor totalOverall = database.rawQuery(
						"select sum("+Receipt.DBPriceKey+") from "+Receipt.DBReceiptsTable, 
						null);
	
				
				if (totalOverall != null) {
	
					if (isCancelled()) {
						totalOverall.close();
						database.close();
						return null;
					}
					
					if (totalOverall.getCount() > 0) {
						totalOverall.moveToFirst();
						total = totalOverall.getLong(0);
					}
					totalOverall.close();
				}
				database.close();
				
				for (StatItem item : items) {
					item.percentageOfHighest = ((float)item.total)/((float)highestPrice);
				}
				
				StatData result = new StatData();
				result.average = average;
				result.totalOverall = total;
				result.highestPrice = highestPrice;
				result.items = items;
				
				return result;
			}
		}
		
		@Override
		protected void onCancelled() {
			if (activity != null) {
				indicator = activity.getIndicator();
				indicator.stopWorking(task);
			}
			else {
                finishedTaskStack.add(task);
            }
		}
		
		@Override
		protected void onPostExecute(StatData result) {
			if (activity != null) {
				indicator = activity.getIndicator();
				indicator.stopWorking(task);
			}
			else {
                finishedTaskStack.add(task);
            }

			data = result;

            boolean animated = activity != null;
            if (animated) {
                animated = activity.getCurrentNavigationIndex() == activity.getStatsNavigationIndex();
            }

			onCreatedDataSet(animated, false);

		}
		
	}

    static class StatOverlay {
        int tagUID;
        SparseArray<BigDecimal> values;
        OverlayFinderTask pendingThread;
    }

    class OverlayFinderTask extends AsyncTask<Void, Void, StatOverlay> {

//        private int tagUID;
        private Precision precision;

        private StatOverlay overlay;

        public OverlayFinderTask(StatOverlay overlay) {
            this.overlay = overlay;

            precision = precisionData;
        }

//        public OverlayFinderTask(int tagUID) {
//            this.tagUID = tagUID;
//
//            precision = precisionData;
//        }

        @Override
        protected StatOverlay doInBackground(Void[] objects) {
            long benchmarkStart = System.currentTimeMillis();

//            StatOverlay overlay = new StatOverlay();
//            overlay.tagUID = tagUID;
            overlay.values = new SparseArray<BigDecimal>();

            long previousAmount = 0;
            long currentAmount = 0;

            int bigDecimalAllocations = 0;
            int itemsProcessed = 0;

            synchronized (Receipt.DatabaseLock) {
                SQLiteDatabase database = Receipt.DBHelper.getReadableDatabase();

                Cursor receipts = Receipt.queryDatabase(database)
                        .selectColumns(Receipt.DBFilenameIdKey, Receipt.DBTaxKey, Receipt.DBDateKey)
                        .fromTable(Receipt.DBReceiptsTable)
                        .where(Receipt.DBDateKey + " between " +
                            unixTimeStart + " and " +
                                unixTimeEnd) // TODO: correct checking
                        .orderBy(Receipt.DBDateKey)
                        .execute();

                int currentGrouper = Integer.MIN_VALUE;
                Calendar workCalendar = Calendar.getInstance();

                while (receipts.moveToNext()) {
                    if (isCancelled()) {
                        receipts.close();
                        database.close();
                        return null;
                    }

                    long tax = receipts.getInt(1);
                    workCalendar.setTimeInMillis(receipts.getLong(2) * 1000);

                    if (currentGrouper != precision.getComparator(workCalendar)) {
                        if (USE_FAST_CALCULATOR) {
                            if (currentGrouper != Integer.MIN_VALUE) {
                                if (DEBUG_FAST_CALCULATOR) {
                                    Log.d(TAG, "FastCalculator: 3 big decimals allocated!");
                                    bigDecimalAllocations += 3;
                                }
                                overlay.values.put(currentGrouper, overlay.values.get(currentGrouper).add(new BigDecimal(currentAmount).movePointLeft(10)));
                            }
                        }
                        currentGrouper = precision.getComparator(workCalendar);
                        overlay.values.put(currentGrouper, new BigDecimal(0));
                        if (DEBUG_OVERLAYS) Log.d(TAG, "Found grouper: " + currentGrouper);

                        currentAmount = 0;
                        previousAmount = 0;
                    }

                    Cursor tagConnections = database.rawQuery(
                            "select " + Receipt.DBItemsTable + "." + Receipt.DBPriceKey + ", " + Receipt.DBItemsTable + "." + Receipt.DBQtyKey +
                                    " from " + Receipt.DBItemsTable + ", " + Receipt.DBTagConnectionsTable +
                                    " where " + Receipt.DBItemsTable + "." + Receipt.DBItemUIDKey + " = " + Receipt.DBTagConnectionsTable + "." + Receipt.DBItemConnectionUIDKey +
                                    " and " + Receipt.DBTagConnectionsTable + "." + Receipt.DBTagConnectionUIDKey + " = " + overlay.tagUID +
                                    " and " + Receipt.DBItemsTable + "." + Receipt.DBTargetDBKey + " = " + receipts.getLong(0), null

                    );

                    if (tagConnections.getCount() > 0) {

                        while (tagConnections.moveToNext()) {
                            if (isCancelled()) {
                                receipts.close();
                                tagConnections.close();
                                database.close();
                                return null;
                            }

                            if (DEBUG_FAST_CALCULATOR) itemsProcessed++;

                            long qty = tagConnections.getLong(1);
                            long price;
                            if (qty == 0) qty = 10000;
                            if (USE_FAST_CALCULATOR) {
                                price = tagConnections.getLong(0);
                                if (price * qty > OverflowMagicValue || price * qty < 0) {
                                    // An overflow has already occurred or will occur when further multplying with tax
                                    // Use a BigDecimal for this value instead
                                    overlay.values.put(currentGrouper, overlay.values.get(currentGrouper).add(
                                            new BigDecimal(price).multiply(new BigDecimal(qty)).multiply(new BigDecimal(tax + 10000))
                                            .movePointLeft(10)
                                    ));
                                    if (DEBUG_FAST_CALCULATOR) {
                                        bigDecimalAllocations += 4;
                                        Log.d(TAG, "FastCalculator: 4 big decimals allocated to prevent an overflow!");
                                    }
                                }
                                else {
                                    // Otherwise it's safe to keep using a long
                                    currentAmount += tagConnections.getLong(0) * qty * (tax + 10000);
                                    if (currentAmount < previousAmount) {
                                        // decimal places: 2 price, 4 qty, 4 tax
                                        if (DEBUG_FAST_CALCULATOR) {
                                            Log.d(TAG, "FastCalculator: 3 big decimals allocated!");
                                            bigDecimalAllocations += 3;
                                        }
                                        overlay.values.put(currentGrouper, overlay.values.get(currentGrouper).add(new BigDecimal(previousAmount).movePointLeft(10)));
                                        // Overflow can occur if price digits - 2 + qty digits - 4  + 1 >= 9
                                        currentAmount = tagConnections.getLong(0) * qty * (tax + 10000);
                                    }
                                    previousAmount = currentAmount;
                                }
                            }
                            else {
                                if (tax == 0) {
                                    if (DEBUG_FAST_CALCULATOR) {
                                        Log.d(TAG, "LegacyCalculator: 5 big decimals allocated");
                                        bigDecimalAllocations += 5;
                                    }
                                    overlay.values.put(currentGrouper, overlay.values.get(currentGrouper).add(
                                            new BigDecimal(tagConnections.getLong(0)).movePointLeft(2)
                                                    .multiply(new BigDecimal(qty).movePointLeft(4))
                                    ));
                                } else {
                                    if (DEBUG_FAST_CALCULATOR) {
                                        Log.d(TAG, "LegacyCalculator: 7 big decimals allocated");
                                        bigDecimalAllocations += 7;
                                    }
                                    overlay.values.put(currentGrouper, overlay.values.get(currentGrouper).add(
                                            new BigDecimal(tagConnections.getLong(0)).movePointLeft(2)
                                                    .multiply(new BigDecimal(qty).movePointLeft(4))
                                                    .multiply(new BigDecimal(tax + 10000).movePointLeft(4))
                                    ));
                                }
                            }

                        }
                    }

                    tagConnections.close();

                }

                receipts.close();

                database.close();
                if (USE_FAST_CALCULATOR) {
                    if (currentGrouper != Integer.MIN_VALUE) {
                        if (DEBUG_FAST_CALCULATOR) {
                            Log.d(TAG, "FastCalculator: 3 big decimals allocated!");
                            bigDecimalAllocations += 3;
                        }
                        overlay.values.put(currentGrouper, overlay.values.get(currentGrouper).add(new BigDecimal(currentAmount).movePointLeft(10)));
                    }
                }
            }

            if (DEBUG_FAST_CALCULATOR) {
                Log.d(TAG, "Total BigDecimal allocations: " + bigDecimalAllocations);
                Log.d(TAG, "Total items processed: " + itemsProcessed);
                Log.d(TAG, "Total duration: " + (System.currentTimeMillis() - benchmarkStart));
            }

            return null;
        }

        protected void onPostExecute(StatOverlay values) {
            overlay.pendingThread = null;
            addFoundOverlay(overlay);
        }

    }

    private StatData data;
    StatItem selectedItem = null;

//    private ArrayList<ItemCollectionFragment.Tag> overla;
    private ArrayList<StatOverlay> overlays = new ArrayList<StatOverlay>();

    private IntervalList intervals;

    private boolean updating;
    private boolean updateAnimationDone = true;

    private HistoryActivity activity;
    private boolean resumed;

    private boolean phoneUI;
    private boolean landscape;

    private boolean playedIntro = false;
    private boolean rangePanelUp = false;

    private int statsStackMode = GraphStackingDrawable.ModeStandard;

    private BreakdownFragment breakdownFragment;

    //private String precision;
    private Precision precisionData = Precision.makePrecision(DateFormatMonthPrecision);
    private int precisionIndex = 0;
    long unixTimeStart;
    long unixTimeEnd;
    private boolean loadedInitially;

    private GraphView graph;
    private ListenableHorizontalScrollView graphScroller;

    private TextView statsTitle;
    private TagView overlayTags;
    private int maximumOverlays;

    private TagExpander currentExpander;

    private ArrayList<Animator> animations = new ArrayList<Animator>();
    private ArrayList<Runnable> backStack = new ArrayList<Runnable>();
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);

		unixTimeStart = Long.MIN_VALUE/1000;
		unixTimeEnd = Long.MAX_VALUE/1000;

        PrecisionLabels = new String[] {
                getResources().getString(R.string.Weekly),
                getResources().getString(R.string.Monthly),
                getResources().getString(R.string.Quarterly),
                getResources().getString(R.string.Yearly)
        };

        breakdownFragment = (BreakdownFragment) getActivity().getFragmentManager().findFragmentById(R.id.BreakdownFragment);
        if (breakdownFragment == null) {
            breakdownFragment = (BreakdownFragment) getFragmentManager().findFragmentByTag("android:switcher:"+R.id.HistoryPager+":2");
        }
        if (breakdownFragment == null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (getActivity() == null) {
                        handler.post(this);
                        return;
                    }

                    breakdownFragment = (BreakdownFragment) getActivity().getFragmentManager().findFragmentById(R.id.BreakdownFragment);
                    if (breakdownFragment == null) {
                        breakdownFragment = (BreakdownFragment) getFragmentManager().findFragmentByTag("android:switcher:"+R.id.HistoryPager+":2");
                    }

                    if (breakdownFragment == null) {
                        handler.post(this);
                    }
                }
            });
        }

        phoneUI = (getResources().getConfiguration().smallestScreenWidthDp < 600);

        new IntervalFinderTask().execute();
		
	}

	public String currentOverlaysDescription() {
		StringBuilder builder = new StringBuilder();

		boolean first = true;
		for (StatOverlay overlay : overlays) {
			if (!first) {
				builder.append(", ");
			}
			else {
				first = false;
			}

			builder.append(TagStorage.findTagWithUID(overlay.tagUID).name);
		}

		return builder.toString();
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup root, Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_stats, root, false);

        landscape = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;

        graph = (GraphView) view.findViewById(R.id.StatsGraphView);
        graph.setOverlayDisplayMode(GraphView.OverlayModeRegular, false);
        graphScroller = (ListenableHorizontalScrollView) graph.getParent().getParent();

        view.findViewById(R.id.GraphRipple).setBackground(new LegacyRippleDrawable(view.getContext()));
        graph.setRippleView(view.findViewById(R.id.GraphRipple));

        statsTitle = ((TextView) view.findViewById(R.id.StatsTitle));
        statsTitle.setTypeface(Receipt.condensedTypeface());

        view.findViewById(R.id.StatsStackMode).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				graph.setOverlayDisplayMode(1 - graph.getOverlayDispalyMode(), true);
				if (graph.getOverlayDispalyMode() == GraphView.OverlayModeRegular) {
					((GraphStackingDrawable) ((ImageView) view.findViewById(R.id.StatsStackMode)).getDrawable()).setMode(GraphStackingDrawable.ModeStandard, true);
					statsStackMode = GraphStackingDrawable.ModeStandard;
				} else {
					((GraphStackingDrawable) ((ImageView) view.findViewById(R.id.StatsStackMode)).getDrawable()).setMode(GraphStackingDrawable.ModeStacked, true);
					statsStackMode = GraphStackingDrawable.ModeStacked;
				}
			}
		});
		view.findViewById(R.id.StatsStackMode).setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				new TooltipPopover(getString(R.string.StatsStackModeTooltip),
									statsStackMode == GraphStackingDrawable.ModeStacked ? getString(R.string.StatsStackModeStacked) : getString(R.string.StatsStackModeOverlay),
									TooltipPopover.anchorWithID(R.id.StatsStackMode)).show(activity);
				return true;
			}
		});

        if (phoneUI) {
            maximumOverlays = MaximumPhoneOverlays;
        }
        else {
            if (getResources().getConfiguration().smallestScreenWidthDp < 720) {
                maximumOverlays = MaximumMiniTabletOverlays;
            }
            else {
                maximumOverlays = MaximumTabletOverlays;
            }
        }

        overlayTags = (TagView) view.findViewById(R.id.StatsOverlays);
        overlayTags.setMaximumTags(maximumOverlays);
        overlayTags.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(final View ClickedTagView) {
				ClickedTagView.setPressed(false);
				final ViewGroup OverlayList = (ViewGroup) activity.findViewById(R.id.StatsOverlayList);

				final Runnable ExpanderCloser = new Runnable() {
					@Override
					public void run() {
						if (currentExpander != null) {
							currentExpander.compact();
						}
					}
				};
				backStack.add(ExpanderCloser);

				currentExpander = TagExpander.fromViewInContainerWithProxyTarget((TagView) ClickedTagView, (ViewGroup) activity.findViewById(R.id.StatsOverlayList), new OverlayProxy());
				currentExpander.setCanEditTags(false);
				currentExpander.setEnforceHuePositions(false);
				currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
					@Override
					public void onClose() {
						backStack.remove(ExpanderCloser);
						activity.findViewById(R.id.StatsHeader).animate().translationX(0).alpha(1f);
						OverlayList.animate().translationX(ClickedTagView.getLeft() - OverlayList.getLeft())
								.setListener(new AnimatorListenerAdapter() {
									@Override
									public void onAnimationEnd(Animator animation) {
										OverlayList.setVisibility(View.INVISIBLE);
									}
								}).start();
						currentExpander = null;
					}
				});
				currentExpander.expand();
				OverlayList.setTranslationX(ClickedTagView.getLeft() - OverlayList.getLeft());
				OverlayList.setVisibility(View.VISIBLE);
				OverlayList.animate().translationX(0f).setListener(null);
				activity.findViewById(R.id.StatsHeader).animate().translationX(-ClickedTagView.getLeft() + OverlayList.getLeft()).alpha(0.4f);
			}
		});

		overlayTags.setOnLongClickListener(new View.OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				new TooltipPopover(getString(R.string.OverlayTagsTooltip),
						overlays.size() == 0 ? getString(R.string.OverlayTagsTooltipNoTags) : currentOverlaysDescription(),
						TooltipPopover.anchorWithID(overlayTags.getId())).show(activity);

				return true;
			}
		});

        if (landscape) {
            view.findViewById(R.id.SeparatorPortrait).setVisibility(View.INVISIBLE);
        }
        else {
            view.findViewById(R.id.SeparatorLandscape).setVisibility(View.INVISIBLE);
        }

        statsTitle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final ArrayList<CharSequence> Titles = new ArrayList<CharSequence>();
                Titles.add(getString(R.string.Monthly));
                Titles.add(getString(R.string.Quarterly));
                Titles.add(getString(R.string.Yearly));
                MenuPopover popover = (MenuPopover) new MenuPopover(new Popover.AnchorProvider() {
                    @Override
                    public View getAnchor(Popover popover) {
                        return statsTitle;
                    }
                }, Titles).show(getActivity());
                popover.setTitle("Stats Grouping");
                if (DateFormatMonthPrecision.equals(precisionData.sqlPrecision)) {
                    popover.setSelection(0);
                }
                else if (DateFormatQuarterPrecision.equals(precisionData.sqlPrecision)) {
                    popover.setSelection(1);
                }
                else if (DateFormatYearPrecision.equals(precisionData.sqlPrecision)) {
                    popover.setSelection(2);
                }
                popover.setOnMenuItemSelectedListener(new MenuPopover.OnMenuItemSelectedListener() {
                    @Override
                    public void onMenuItemSelected(Object object, int index) {
                        changePrecisionTo(index);
                    }
                });
            }
        });

        statsTitle.setPadding((int) (getResources().getDisplayMetrics().density * 8 + statsTitle.getPaddingLeft()), statsTitle.getPaddingTop(), statsTitle.getPaddingRight(), statsTitle.getPaddingBottom());

        return view;
	}
	
	private DisplayMetrics metrics;
	
	static float maxDistance;
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		this.activity = (HistoryActivity)getActivity();

        try {
            ((TextView) getActivity().findViewById(R.id.text_total)).setTypeface(Receipt.condensedTypeface());
            ((TextView) getActivity().findViewById(R.id.text_total)).setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            getActivity().findViewById(R.id.text_total).setClickable(false);
            ((TextView) getActivity().findViewById(R.id.total_sum)).setTypeface(Receipt.condensedTypeface());
        }
        catch (NullPointerException e) {
            Log.e(TAG, "Unimplemented content.");
        }
		
		metrics = getResources().getDisplayMetrics();
        int swdp = activity.getResources().getConfiguration().smallestScreenWidthDp;
		
		maxDistance = metrics.density * 50;

        graph.setBaseColor(Utils.interpolateColors(0.9f, 0xFFFFFFFF, getResources().getColor(R.color.DashboardTextOpaque)));
        graph.setFillColor(Utils.interpolateColors(0.5f, 0xFFFFFFFF, getResources().getColor(R.color.GradientStart)));
        graph.setSelectorColor(getResources().getColor(R.color.GraphSelector));
        graph.postInvalidate();
//        graph.setFillEnabled(false);

        activity.findViewById(R.id.StatsTitle).setBackground(new LegacyRippleDrawable(activity).setShape(LegacyRippleDrawable.ShapeCircle));
        activity.findViewById(R.id.StatsStackMode).setBackground(new LegacyRippleDrawable(activity).setShape(LegacyRippleDrawable.ShapeCircle));
        activity.findViewById(R.id.StatsOverlays).setBackground(new LegacyRippleDrawable(activity).setShape(LegacyRippleDrawable.ShapeCircle));

        GraphStackingDrawable graphStackingDrawable = new GraphStackingDrawable(activity);
        if (statsStackMode != GraphStackingDrawable.ModeStandard) {
            graphStackingDrawable.setMode(GraphStackingDrawable.ModeStacked, false);
            graph.setOverlayDisplayMode(GraphView.OverlayModeStacked, false);
        }
        ((ImageView) activity.findViewById(R.id.StatsStackMode)).setImageDrawable(graphStackingDrawable);
		
		if (data != null && !updating) {
            updating = true;
		    onCreatedDataSet(false, true);
		}

        ArrayList<ItemCollectionFragment.Tag> tags = new ArrayList<ItemCollectionFragment.Tag>();
        for (StatOverlay overlay : overlays) {
            if (!updating && data != null) displayOverlay(overlay, false);
            tags.add(TagStorage.findTagWithUID(overlay.tagUID));
        }
        overlayTags.setTags(tags);

        if (rangePanelUp) {
            showPanel(false);
        }

        TextView totalTitle = (TextView) getActivity().findViewById(R.id.text_total);

//        totalTitle.setPadding((int)(metrics.density * 8) + totalTitle.getPaddingLeft(), totalTitle.getPaddingTop(), totalTitle.getPaddingRight(), totalTitle.getPaddingBottom());
        totalTitle.setPadding(0, totalTitle.getPaddingTop(), totalTitle.getPaddingRight(), totalTitle.getPaddingBottom());
        ((ViewGroup.MarginLayoutParams) totalTitle.getLayoutParams()).leftMargin = getResources().getDimensionPixelSize(R.dimen.PrimaryKeyline);

        if (selectedItem != null) {

            totalTitle = (TextView) getActivity().findViewById(R.id.total_sum);
            totalTitle.setText(ReceiptActivity.totalFormattedString(activity, new BigDecimal(selectedItem.total).movePointLeft(2)));
        }

        updateTitle();

	}

    protected void updateTitle() {
        if (precisionData == null) return;

        if (DateFormatMonthPrecision.equals(precisionData.sqlPrecision)) {
            ((TextView) activity.findViewById(R.id.StatsTitle)).setText(activity.getText(R.string.MonthlyDisplay));
        }
        else if (DateFormatQuarterPrecision.equals(precisionData.sqlPrecision)) {
            ((TextView) activity.findViewById(R.id.StatsTitle)).setText(activity.getText(R.string.QuarterlyDisplay));
        }
        else if (DateFormatYearPrecision.equals(precisionData.sqlPrecision)) {
            ((TextView) activity.findViewById(R.id.StatsTitle)).setText(activity.getText(R.string.YearlyDisplay));
        }
    }
	
	@Override
	public void onDetach() {
		super.onDetach();
		
		activity = null;

        graph = null;
        graphScroller = null;

        statsTitle = null;

        overlayTags = null;
        currentExpander = null;

	}

    final private Handler handler = new Handler();
    final static int ResumeAnimationDelay = 750;

    @Override
    public void onResume() {
        super.onResume();
        resumed = true;

        if (!playedIntro) {

            if (activity.getCurrentNavigationIndex() != activity.getStatsNavigationIndex()) {

                graphScroller.scrollTo(
                        graph.getWidth() - graphScroller.getWidth() + graphScroller.getPaddingLeft() + graphScroller.getPaddingRight()
                        , 0);

                graphScroller.fullScroll(View.FOCUS_RIGHT);

                playedIntro = true;
                return;
            }

            graph.setCompletion(0f);
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animations.add(animator);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {

                    graph.setCompletion(valueAnimator.getAnimatedFraction());
                    graphScroller.scrollTo((int) (Utils.interpolateValues(valueAnimator.getAnimatedFraction(), 0,
                            graph.getWidth() - graphScroller.getWidth() + graphScroller.getPaddingLeft() + graphScroller.getPaddingRight()))
                            , 0);
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);
                    playedIntro = true;
                }
            });
            animator.setDuration(500);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            animator.setStartDelay(ResumeAnimationDelay);
            animator.start();
        }
    }

    public void freezeScrollers() {
        graphScroller.setWillNotDraw(true);
        ((CollectionView) activity.findViewById(R.id.BreakdownList)).freeze();
    }

    public void thawScrollers() {
        graphScroller.setWillNotDraw(false);
        ((CollectionView) activity.findViewById(R.id.BreakdownList)).thaw();
    }

    public void onPause() {
        super.onPause();

        resumed = false;
        flushAnimations();
    }

    public void flushAnimations() {
        while (animations.size() > 0) {
            animations.get(0).end();
        }
    }

    public void loadInitialDataForPrecision(Precision precision) {
        if (!loadedInitially) {
            loadDataForPrecision(precision);
        }
        loadedInitially = true;

        if (activity != null) {
            updateTitle();
        }
    }

    public void loadDataForPrecision(Precision precision) {
        //this.precision = precision;
        precisionData = precision;
        new GenerateStatsTask(precision, Long.MIN_VALUE/1000, Long.MAX_VALUE/1000).execute(Receipt.DBHelper);
    }

    public void loadDirectQuery(QueryData data, Precision precision) {
        throw new UnsupportedOperationException("StatsFragment no longer supports searching.");
    }

    public void reloadData() {
        new GenerateStatsTask(precisionData, unixTimeStart, unixTimeEnd).execute(Receipt.DBHelper);
    }

    public void update() {
        if (breakdownFragment != null) {
            breakdownFragment.deselect();
        }

        updating = true;
        updateAnimationDone = false;
        new IntervalFinderTask().execute();
        for (StatOverlay overlay : overlays) {
            if (overlay.pendingThread != null) overlay.pendingThread.cancel(false);
            overlay.pendingThread = new OverlayFinderTask(overlay);
            overlay.pendingThread.execute();
        }

        // AsyncTask guarantees that this will finish last
        new GenerateStatsTask(precisionData, unixTimeStart, unixTimeEnd).execute(Receipt.DBHelper);

        if (activity.getCurrentNavigationIndex() != activity.getStatsNavigationIndex()) {
            graph.setCompletion(0f);
            graph.clearOverlays();
            updateAnimationDone = true;
        }
        else {
//            graphScroller.requestDisableInteraction(); TODO

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animations.add(animator);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    graph.setCompletion(1 - fraction);
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);

                    updateAnimationDone = true;
                    if (!updating) {
                        onUpdateComplete();
                    }
                }
            });
            animator.setDuration(200);
            animator.setInterpolator(new AccelerateInterpolator(1.5f));
            animator.start();
        }
    }

    public void onUpdateComplete() {
        onCreatedDataSet();

        graph.clearOverlays();
        for (StatOverlay overlay : overlays) {
            if (overlay.pendingThread == null) {
                displayOverlay(overlay, false);
            }
        }

        if (activity.getStatsNavigationIndex() != activity.getCurrentNavigationIndex()) {
            graph.setCompletion(1);
        }
        else {
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animations.add(animator);

            graphScroller.setScrollX(0);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    graph.setCompletion(fraction);
                    graphScroller.setScrollX((int) (Utils.interpolateValues(fraction, 0, graph.getWidth() - graphScroller.getWidth() - graphScroller.getPaddingLeft())));
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);
                }
            });
            animator.setDuration(300);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            animator.start();
        }
    }
	
	public void onCreatedDataSet() {
		onCreatedDataSet(true, false);
	}
	
	public void onCreatedDataSet(final boolean animated, final boolean Silent) {
        if (activity != null) {

            if (updating && !updateAnimationDone) {
                updating = false;
                return;
            }

            graph.clear();

            for (StatItem item : data.items) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(item.unixDate * 1000);
                String title = precisionData.title.getTitle(calendar).toUpperCase(Locale.ENGLISH);
                graph.addPointWithTag(item, item.total, title);
            }

            graph.setOnSelectionChangedListener(new GraphView.OnSelectionChangedListener() {
                @Override
                public void onSelectionChanged(Object object, int index) {
                    selectedItem = (StatItem) object;
                    breakdownFragment.selectItem((StatItem) object);
                    ((TextView) activity.findViewById(R.id.total_sum)).setText(ReceiptActivity.totalFormattedString(activity, new BigDecimal(((StatItem) object).total).movePointLeft(2)));


                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(selectedItem.unixDate * 1000);
                }
            });

            graphScroller.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    if (activity == null) return;
                    graphScroller.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                    graphScroller.scrollTo(graph.getWidth(), 0);
                }
            });

            if (!updating) {
                if (data.items.size() > 0) {
                    selectedItem = data.items.get(data.items.size() - 1);
                    graph.selectPointWithTag(selectedItem);

                    if (!playedIntro) {
                        graphScroller.scrollTo(graph.getWidth(), 0);
                        graph.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                if (activity == null) return;
                                graph.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                                graphScroller.scrollTo(graph.getWidth(), 0);
                            }
                        });
                    }
                }
            }
            else {
                if (data.items.size() > 0) {
                    if (selectedItem != null) {
                        boolean foundSelectedItem = false;
                        for (StatItem item : data.items) {
                            if (item == selectedItem) {
                                foundSelectedItem = true;
                                break;
                            }
                            if (precisionData.getComparator(item.unixDate * 1000) == precisionData.getComparator(selectedItem.unixDate * 1000)) {
                                foundSelectedItem = true;
                                selectedItem = item;
                                break;
                            }
                        }
                        if (!foundSelectedItem) selectedItem = null;
                    }
                    if (selectedItem == null) {
                        selectedItem = data.items.get(data.items.size() - 1);
                        ((TextView) activity.findViewById(R.id.total_sum)).setText(ReceiptActivity.totalFormattedString(activity, new BigDecimal(selectedItem.total).movePointLeft(2)));
                        graph.selectPointWithTag(selectedItem);
                        breakdownFragment.selectItem(selectedItem);
                    }
                    else {
                        Calendar c = Calendar.getInstance();
                        c.setTimeInMillis(selectedItem.unixDate * 1000);
                        ((TextView) activity.findViewById(R.id.total_sum)).setText(ReceiptActivity.totalFormattedString(activity, new BigDecimal(selectedItem.total).movePointLeft(2)));
                        graph.selectPointWithTagSilently(selectedItem);
                        breakdownFragment.selectItem(selectedItem);
                    }

                    if (!playedIntro) {
                        graphScroller.scrollTo(graph.getWidth(), 0);
                        graph.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                            @Override
                            public void onGlobalLayout() {
                                if (activity == null) return;
                                graph.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                                graphScroller.scrollTo(graph.getWidth(), 0);
                            }
                        });
                    }
                }

                updating = false;
                if (updateAnimationDone && !Silent) {
                    onUpdateComplete();
                }
            }
        }
    }

    public void addOverlayForTag(int tagUID) {
        if (overlays.size() == maximumOverlays) return;
        for (StatOverlay overlay : overlays) {
            if (overlay.tagUID == tagUID) return; // overlay already exists
        }

        if (currentExpander != null) {
            currentExpander.addTag(TagStorage.findTagWithUID(tagUID));
        }
        else {
            findOverlay(tagUID);
        }
    }

    protected void findOverlay(int tagUID) {
        breakdownFragment.onOverlayAdded(tagUID);
        StatOverlay overlay = new StatOverlay();
        overlay.tagUID = tagUID;
        overlay.pendingThread = new OverlayFinderTask(overlay);
        overlay.pendingThread.execute();
        overlays.add(overlay);
        refreshOverlayTagView();
    }

    protected void addFoundOverlay(StatOverlay overlay) {
        boolean animated = true;

        if (activity != null) {

            // Draw the overlay
            displayOverlay(overlay, animated);
        }
    }

    public void removeOverlay(int tagUID) {
        if (currentExpander != null) {
            ItemCollectionFragment.Tag tag = TagStorage.findTagWithUID(tagUID);
            if (tag != null) currentExpander.removeColor(tag.color);
            return;
        }

        for (StatOverlay overlay : overlays) {
            if (overlay.tagUID == tagUID) {
                removeOverlay(overlay, true);
                return;
            }
        }
    }

    public void onTagDeleted(ItemCollectionFragment.Tag tag) {
        for (StatOverlay overlay : overlays) {
            if (overlay.tagUID == tag.tagUID) {
                if (currentExpander != null) currentExpander.removeColor(tag.color);
                else removeOverlay(overlay, false);

                return;
            }
        }

        if (selectedItem != null) {
            breakdownFragment.selectItem(selectedItem);
        }
    }

    protected void removeOverlay(StatOverlay overlay, boolean animated) {
        breakdownFragment.onOverlayRemoved(overlay.tagUID);

        if (overlay.pendingThread != null) overlay.pendingThread.cancel(false);
        overlays.remove(overlay);

        if (activity != null) {
            graph.removeOverlayWithColor(TagStorage.findTagWithUID(overlay.tagUID).color, animated);
            refreshOverlayTagView();
        }
    }

    public boolean hasOverlay(int color) {
        for (StatOverlay overlay : overlays) {
            if (overlay.tagUID == color) return true;
        }

        return false;
    }

    public boolean canAddOverlays() {
        return overlays.size() < maximumOverlays;
    }

    protected void refreshOverlayTagView() {

        if (activity != null) {
            ArrayList<ItemCollectionFragment.Tag> tags = new ArrayList<ItemCollectionFragment.Tag>(overlays.size());
            for (StatOverlay statOverlay : overlays) {
                tags.add(TagStorage.findTagWithUID(statOverlay.tagUID));
            }

            overlayTags.setTags(tags);
        }

    }

    protected void displayOverlay(StatOverlay overlay, boolean animated) {
        GraphView.Overlay graphOverlay = graph.createOverlayWithColor(TagStorage.findTagWithUID(overlay.tagUID).color);
        for (GraphView.Point point : graphOverlay.getPoints()) {
            int pointComparator = precisionData.getComparator(((StatItem) point.getTag()).unixDate * 1000);
            if (DEBUG_OVERLAYS) Log.d(TAG, "Matching point grouper: " + pointComparator);

            if (overlay.values.get(pointComparator) != null) {
                point.setValue(overlay.values.get(pointComparator).movePointRight(2).longValue());
                if (DEBUG_OVERLAYS) Log.d(TAG, "Value is: " + point.getValue());
            }
            else {
                point.setValue(0);
            }
        }

        graphOverlay.show(animated);
    }
	
	public void notifyLocaleChanged() {
		
		// TODO
		
	}

    public boolean handleBackPressed() {
        if (backStack.size() > 0) {
            backStack.get(backStack.size() - 1).run();
            return true;
        }
        if (rangePanelUp) {
            dismissPanel();
            return true;
        }
        return false;
    }

    public StatItem getSelectedItem() {
        return selectedItem;
    }

    public Precision getPrecisionData() {
        return precisionData;
    }

    public StatData getData() {
        return data;
    }

    // ****************** OVERLAY PROXY ***********************

    class OverlayProxy extends ItemCollectionFragment.Item {
        public OverlayProxy() {
            for (StatOverlay overlay : overlays) {
                tags.add(TagStorage.findTagWithUID(overlay.tagUID));
            }
        }

        public void addTagToIndex(ItemCollectionFragment.Tag tag, int index) {
            if (tags.contains(tag)) return;

            super.addTagToIndex(tag, index);
            findOverlay(tag.tagUID);
        }

        public boolean canAddTags() {
            if (phoneUI) return super.canAddTags();
            else {
                if (getResources().getConfiguration().smallestScreenWidthDp < 720) {
                    return tags.size() < MaximumMiniTabletOverlays;
                }
                else {
                    return tags.size() < MaximumTabletOverlays;
                }
            }
        }

        public void removeTagAtIndex(int index) {
            ItemCollectionFragment.Tag tag = tags.get(index);

            for (StatOverlay overlay : overlays) {
                if (overlay.tagUID == tag.tagUID) {
                    removeOverlay(overlay, true);
                    break; // TODO
                }
            }

            super.removeTagAtIndex(index);
        }
    }

    // ****************** TIMERANGE AND PRECISION SELECTOR *********************

    public void changePrecisionTo(int precisionIndex) {
        SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).edit();
        prefs.putInt(SearchFragment.LastPrecisionKey, precisionIndex);
        prefs.apply();

        selectedItem = null;

        if (precisionIndex == 0) {
            precisionData = Precision.makePrecision(DateFormatMonthPrecision);
        }
        else if (precisionIndex == 1) {
            precisionData = Precision.makePrecision(DateFormatQuarterPrecision);
        }
        else {
            precisionData = Precision.makePrecision(DateFormatYearPrecision);
        }

        updateTitle();
        update();
    }

    private int pendingPrecision = -1;

    @Deprecated //Handled by popover menu for now
    public void showPanel(boolean animated) {
        rangePanelUp = true;

        flushAnimations();

        // The StatsRoot is located within a FrameLayout covering the whole content below the legacy action bar
        final ViewGroup StatsRoot = (ViewGroup) activity.findViewById(R.id.StatsFragment);
        final ViewGroup GraphRoot = (ViewGroup) activity.findViewById(R.id.GraphRoot);

        PanelBuilder builder = new PanelBuilder(activity);

        float density = getResources().getDisplayMetrics().density;
        if (phoneUI) {
            builder.setTitleWidth((int) (density * 72));
            builder.setSettingMargin((int) (density * 24));
        }
        else {
            builder.setTitleWidth((int) (density * 144));
            builder.setSettingMargin((int) (density * 48));
        }

        builder.addSetting("Show", PanelBuilder.TypeSlider, R.id.StatsPrecisionSlider);
        builder.addSetting("Range", PanelBuilder.TypeSlider, R.id.StatsRangeSlider);

        final View Panel = builder.build();

        final PrecisionRangeSlider PrecisionSlider = (PrecisionRangeSlider) Panel.findViewById(R.id.StatsPrecisionSlider);
        String uppercasePrecisionLabels[] = new String[PrecisionLabels.length];
        for (int i = 0; i < uppercasePrecisionLabels.length; i++) {
            uppercasePrecisionLabels[i] = PrecisionLabels[i].toUpperCase(Locale.getDefault());
        }
        PrecisionSlider.addLabels(uppercasePrecisionLabels);
        PrecisionSlider.setPopupListener(new PrecisionRangeSlider.PopupListener() {
            @Override
            public CharSequence getPopupLabel(float percent) {
                return PrecisionLabels[PrecisionSlider.getLabelForPercentage(percent)];
            }
        });
        int currentPrecision = 1; // implicit
        if (precisionData != null) {
            if (precisionData.sqlPrecision.equals(DateFormatWeekPrecision)) {
                currentPrecision = 0;
            }
            if (precisionData.sqlPrecision.equals(DateFormatQuarterPrecision)) {
                currentPrecision = 2;
            }
            if (precisionData.sqlPrecision.equals(DateFormatYearPrecision)) {
                currentPrecision = 3;
            }
        }
        PrecisionSlider.setEndPosition(12 + 25 * currentPrecision, false);
        PrecisionSlider.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                PrecisionSlider.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                PrecisionSlider.setEndPosition(PrecisionSlider.getLabelExactCenter(PrecisionSlider.getSelectedLabel()));
            }
        });
        PrecisionSlider.setSelectedLabel(currentPrecision);
        PrecisionSlider.setOnRangeChangeListener(new PrecisionRangeSlider.OnRangeChangedListener() {
            @Override
            public void onRangeChange(float fromRange, float toRange, boolean fromUser) {
                if (fromUser) PrecisionSlider.setSelectedLabel(PrecisionSlider.getLabelForPercentage(toRange));
            }

            @Override
            public void onRangeSelected(float fromRange, float toRange, boolean fromUser) {
                if (fromUser) {
                    PrecisionSlider.setSelectedLabel(PrecisionSlider.getLabelForPercentage(toRange));
                    PrecisionSlider.setEndPosition(PrecisionSlider.getLabelExactCenter(PrecisionSlider.getSelectedLabel()));
                    pendingPrecision = PrecisionSlider.getLabelForPercentage(toRange);
                }
            }
        });

        final PrecisionRangeSlider RangeSlider =  (PrecisionRangeSlider) Panel.findViewById(R.id.StatsRangeSlider);
        RangeSlider.setSliderType(PrecisionRangeSlider.SliderTypeRange);
        final Calendar WorkCalendar = Calendar.getInstance();
        WorkCalendar.setTimeInMillis(intervals.months.get(0) * 1000);
        RangeSlider.addLabels(precisionData.title.getTitle(WorkCalendar).toUpperCase(), "TODAY");
        RangeSlider.setRange(0, intervals.months.size());
        RangeSlider.setEndPosition(Float.MAX_VALUE, false);
        RangeSlider.setMinimumRange(6);
        RangeSlider.setMaximumRange(24);
        RangeSlider.setPopupListener(new PrecisionRangeSlider.PopupListener() {
            @Override
            public CharSequence getPopupLabel(float percent) {
                if (percent > intervals.months.size() - 1) {
                    percent = intervals.months.size() - 1;
                }
                WorkCalendar.setTimeInMillis(intervals.months.get((int) (percent)) * 1000);
                return precisionData.verboseTitle.getTitle(WorkCalendar);
            }
        });

        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(Panel.getLayoutParams().width, Panel.getLayoutParams().height);
        if (landscape) {
            params.height += StatsRoot.findViewById(R.id.StatsHeader).getLayoutParams().height;
            Panel.setPadding(Panel.getPaddingLeft(), Panel.getPaddingTop() + StatsRoot.findViewById(R.id.StatsHeader).getLayoutParams().height,
                    Panel.getPaddingRight(), Panel.getPaddingBottom());

            params.addRule(RelativeLayout.CENTER_IN_PARENT);
        }
        else {
            params.topMargin = StatsRoot.findViewById(R.id.StatsHeader).getLayoutParams().height;

            params.topMargin += ((StatsRoot.getLayoutParams().height - params.topMargin) - Panel.getLayoutParams().height) / 2;
        }

        Panel.setLayoutParams(params);
        Panel.setId(R.id.StatsRangePanel);

        StatsRoot.addView(Panel);

        final float Translation = 96 * density; // is negative

        if (animated) {
            Panel.setAlpha(0f);
            Panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);
            Panel.setTranslationY(- Translation);

            GraphRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animations.add(animator);
            animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator valueAnimator) {
                    float fraction = valueAnimator.getAnimatedFraction();

                    Panel.setAlpha(fraction);
                    GraphRoot.setAlpha(1 - fraction);

                    Panel.setTranslationY(Utils.interpolateValues(fraction, - Translation, 0));
                    GraphRoot.setTranslationY(Translation * fraction);
                }
            });
            animator.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    animations.remove(animation);
                    Panel.setLayerType(View.LAYER_TYPE_NONE, null);
                    GraphRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);

                    GraphRoot.setVisibility(View.INVISIBLE);
                }
            });
            animator.setDuration(250);
            animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
            animator.start();
        }
        else {
            GraphRoot.setVisibility(View.INVISIBLE);
        }
    }

    @Deprecated //Handled by popover menu for now
    public void dismissPanel() {
        rangePanelUp = false;
        flushAnimations();

        if (pendingPrecision != -1) {
            if (pendingPrecision == 0) precisionData = Precision.makePrecision(DateFormatWeekPrecision);
            if (pendingPrecision == 1) precisionData = Precision.makePrecision(DateFormatMonthPrecision);
            if (pendingPrecision == 2) precisionData = Precision.makePrecision(DateFormatQuarterPrecision);
            if (pendingPrecision == 3) precisionData = Precision.makePrecision(DateFormatYearPrecision);
            update();
        }

        // TODO: apply changes

        // The StatsRoot is located within a FrameLayout covering the whole content below the legacy action bar
        final ViewGroup StatsRoot = (ViewGroup) activity.findViewById(R.id.StatsFragment);
        final ViewGroup GraphRoot = (ViewGroup) activity.findViewById(R.id.GraphRoot);
        final ViewGroup Panel = (ViewGroup) activity.findViewById(R.id.StatsRangePanel);

        float density = getResources().getDisplayMetrics().density;

        final float Translation = 96 * density; // is negative

        GraphRoot.setAlpha(0f);
        GraphRoot.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        GraphRoot.setTranslationY(Translation);
        GraphRoot.setVisibility(View.VISIBLE);

        Panel.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animations.add(animator);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                float fraction = valueAnimator.getAnimatedFraction();

                Panel.setAlpha(1 - fraction);
                GraphRoot.setAlpha(fraction);

                Panel.setTranslationY(Utils.interpolateValues(fraction, 0, - Translation));
                GraphRoot.setTranslationY(Translation * (1 - fraction));
            }
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                animations.remove(animation);
                Panel.setLayerType(View.LAYER_TYPE_NONE, null);
                GraphRoot.setLayerType(View.LAYER_TYPE_NONE, null);

                StatsRoot.removeView(Panel);
            }
        });
        animator.setDuration(250);
        animator.setInterpolator(new Utils.FrictionInterpolator(1.5f));
        animator.start();
    }
	
}
