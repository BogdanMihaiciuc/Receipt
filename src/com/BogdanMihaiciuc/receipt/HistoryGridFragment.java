package com.BogdanMihaiciuc.receipt;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.receipt.HistoryGridAdapter.Scrap;
import com.BogdanMihaiciuc.receipt.IndicatorFragment.Task;
import com.BogdanMihaiciuc.receipt.SearchFragment.QueryData;
import com.BogdanMihaiciuc.receipt.StatsFragment.Precision;
import com.BogdanMihaiciuc.util.CollectionView;
import com.BogdanMihaiciuc.util.CollectionViewController;
import com.BogdanMihaiciuc.util.IntentListPopover;
import com.BogdanMihaiciuc.util.LegacyActionBar;
import com.BogdanMihaiciuc.util.LegacyRippleDrawable;
import com.BogdanMihaiciuc.util.Popover;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

public class HistoryGridFragment extends Fragment {

    private final static String TAG = HistoryGridFragment.class.toString();
	
	final static boolean DEBUG_GENERATEDATABASE = false;
	final static boolean DEBUG_RELOAD = false;
    final static Object PlaceholderObject = new Object();
    
	private SQLiteDatabase storedLists;
	
	public Cursor getHistory() {
        return storedLists.query(Receipt.DBReceiptsTable, Receipt.DBAllReceiptColumns, null, null, null, null, null);
    }
	
	public Cursor findScrap(long targetId) throws SQLException {
										//Unique, Select in, select columns, having
        Cursor cursor = storedLists.query(true, Receipt.DBReceiptsTable, 
        		Receipt.DBAllReceiptColumns, Receipt.DBFilenameIdKey + "=" + targetId, null, null, null, null, null);
        if (cursor != null) {
            cursor.moveToFirst();
        }
        return cursor;

    }
	
	public boolean deleteScrap(long targetId) {

	    return storedLists.delete(Receipt.DBReceiptsTable, Receipt.DBFilenameIdKey + "=" + targetId, null) > 0;
	    
	}
	
	public void removeScrapView() {
		gridAdapter.removeScrapView();
	}
	
	private class DataSetForMatchingDateLoader extends AsyncTask <Receipt.DatabaseHelper, Void, HistoryGridAdapter.HistoryItemArray> {
		
		private IndicatorFragment.Task task;
		private IndicatorFragment indicator;
		private long unixTimeContainingDate;
		private long unixStartTime;
		private long unixEndTime;
		private String dateFormat;
		private boolean singleInstance;
		private Precision precisionData;
		long hiddenId = -1;
		
		DataSetForMatchingDateLoader(long unixTimeContainingDate, String dateFormat) {
            // TODO
            new Throwable().printStackTrace();
			singleInstance = true;
			this. unixTimeContainingDate = unixTimeContainingDate;
			if (dateFormat == null) {
				//Default to loading items in the matching month
				this.dateFormat = StatsFragment.DateFormatMonthPrecision;
			}
			else {
				this.dateFormat = dateFormat;
			}
			precisionData = Precision.makePrecision(dateFormat);
		}
		
		DataSetForMatchingDateLoader(long startTime, long endTime, String dateFormat) {
			singleInstance = false;
			this.unixStartTime = startTime;
			this.unixEndTime = endTime;
			if (dateFormat == null) {
				//Default to loading items in the matching month
				this.dateFormat = StatsFragment.DateFormatMonthPrecision;
			}
			else {
				this.dateFormat = dateFormat;
			}
			precisionData = Precision.makePrecision(dateFormat);
		}
		
		@Override
		protected void onPreExecute() {
			//activity.startWorking();
			activeSearch = false;
			task = IndicatorFragment.Task.createTask("Loading", null);
			if (activity != null) {
				indicator = activity.getIndicator();
				indicator.startWorking(task);
			}
			else {
				taskStack.add(task);
			}
        	if (gridAdapter != null) {
        		if (singleInstance) gridAdapter.deselect();
        		gridAdapter.setListenersDisabled(true);
        		hiddenId = gridAdapter.idOfHiddenScrap();
        	}
            if (singleInstance) clearSelection();
		}

		@Override
		protected HistoryGridAdapter.HistoryItemArray doInBackground(Receipt.DatabaseHelper... databaseHelper) {
			HistoryGridAdapter.HistoryItemArray result = new HistoryGridAdapter.HistoryItemArray();

//			if (phoneUI)
				result.precisionData = Precision.makePrecision(dateFormat);
//			else
//				result.precisionData = Precision.makeNextPrecision(dateFormat);

            Precision precision = Precision.makePrecision(dateFormat);
            Precision nextPrecision = Precision.makeNextPrecision(dateFormat);
			
			synchronized(Receipt.DatabaseLock) {
				SQLiteDatabase storedLists = databaseHelper[0].getReadableDatabase();
				if (HistoryActivity.DEBUG) Log.d("HistoryGridFragment", "AsyncTask started!");
				//Column orderL FilenameIdKey, DateKey, PriceKey, ItemCountKey
				//First cursor is current month
				Cursor queryResult;
				
				if (singleInstance)
					queryResult = storedLists.query(true, Receipt.DBReceiptsTable, Receipt.DBAllReceiptColumns, 
			        		precision.getGrouper(Receipt.DBDateKey) + " =" +
			        		"= " + precision.getGrouper("'" + (unixTimeContainingDate/1000) + "'"),
			        		null, null, null, Receipt.DBDateKey + " DESC", null);
				else {
					if (precisionData.sqlPrecision != StatsFragment.DateFormatWeekdayPrecision) {
                        queryResult = storedLists.query(true, Receipt.DBReceiptsTable, Receipt.DBAllReceiptColumns,
                                Receipt.DBDateKey + " between " + unixStartTime + " and " + unixEndTime,
                                null, null, null, Receipt.DBDateKey + " DESC", null);
                    }
					else {
                        queryResult = storedLists.query(true, Receipt.DBReceiptsTable, Receipt.DBAllReceiptColumns,
                                Receipt.DBDateKey + " between " + unixStartTime + " and " + unixEndTime,
                                null, null, null,
                                "strftime(" + precisionData.sqlPrecision + ", " + Receipt.DBDateKey + ", 'unixepoch', 'localtime'), " +
                                        Receipt.DBDateKey + " DESC", null);
                    }
				}
				
				long lastDate = Long.MAX_VALUE;
				long currentDate;
				Calendar workCalendar = Calendar.getInstance();
				long grouper;

                long bigGrouper;
				
				int historyItemIndex = -1;
				int scrapClusterIndex = 0;
				
		        if (queryResult != null && queryResult.getCount() != 0) {
		        	if (isCancelled()) {
		    	        queryResult.close();
		    	        storedLists.close();
		        		return null;
		        	}

                    HistoryGridAdapter.Header bigHeader;
		        	HistoryGridAdapter.Header header = null;
	        		HistoryGridAdapter.ScrapCluster cluster = null;
		        	while (queryResult.moveToNext()) {
			        	if (isCancelled()) {
			    	        queryResult.close();
			    	        storedLists.close();
			        		return null;
			        	}
			        	
			        	currentDate = 1000l * queryResult.getLong(1);
		        		workCalendar.setTimeInMillis(lastDate);
		        		grouper = result.precisionData.getComparator(workCalendar);
                        bigGrouper = precision.getComparator(workCalendar);
		        		workCalendar.setTimeInMillis(currentDate);
		        		
		        		boolean areDifferent;
		        		areDifferent = grouper != result.precisionData.getComparator(workCalendar);
		        		
		        		if (areDifferent) {
		        			// we reached a different group, add a new header and commit the pending group
		        			if (cluster != null) result.add(cluster);
                            // on tablets, big headers limit large sections and regular headers limit mini sections
                            if (bigGrouper != precision.getComparator(workCalendar) && !phoneUI) {
                                bigHeader = new HistoryGridAdapter.Header(workCalendar);
                                bigHeader.precision = precision;
                                bigHeader.subtype = HistoryGridAdapter.ItemTypeBigHeader;
                                result.add(bigHeader);
                            }
                            if (header != null && cluster != null) {
                                header.count = cluster.size();
                            }
		    	        	header = new HistoryGridAdapter.Header(workCalendar);
                            if (!phoneUI) {
                                header.precision = nextPrecision;
                            }
                            else {
                                header.precision = result.precisionData;
                            }
                            header.subtype = HistoryGridAdapter.ItemTypeHeader;
		    	        	workCalendar = Calendar.getInstance();
		    	        	result.add(header);
		            		cluster = new HistoryGridAdapter.ScrapCluster(new ArrayList<HistoryGridAdapter.Scrap>());
		            		// There are two new items; one Header and one ScrapCluster
		            		historyItemIndex += 2;
		            		scrapClusterIndex = 0;
		        		}
		        		
		        		if ((precisionData.sqlPrecision == StatsFragment.DateFormatWeekdayPrecision
		        				|| precisionData.sqlPrecision == StatsFragment.DateFormatHourOfDayPrecision) && cluster == null) {
	
		    	        	header = new HistoryGridAdapter.Header(workCalendar);
                            header.precision = result.precisionData;
		    	        	workCalendar = Calendar.getInstance();
		    	        	result.add(header);
		            		cluster = new HistoryGridAdapter.ScrapCluster(new ArrayList<HistoryGridAdapter.Scrap>());
		            		// There are two new items; one Header and one ScrapCluster
		            		historyItemIndex += 2;
		            		scrapClusterIndex = 0;
		            		
		        		}
			        	
		        		HistoryGridAdapter.Scrap scrap = new HistoryGridAdapter.Scrap();
		        		scrap.targetId = queryResult.getLong(0);
		        		scrap.date = Calendar.getInstance();
		        		scrap.date.setTimeInMillis(currentDate);
		        		scrap.total = queryResult.getLong(2);
		        		scrap.itemCount = queryResult.getInt(3);
                        scrap.name = queryResult.getString(Receipt.DBReceiptNameKeyIndex);
		        		cluster.cluster.add(scrap);
		        		lastDate = currentDate;
		        		
		        		if (scrap.targetId == hiddenId) {
		        			result.hiddenInnerIndex = scrapClusterIndex;
		        			result.hiddenOuterIndex = historyItemIndex;
		        			scrap.hidden = true;
		        		}
		        		scrapClusterIndex++;

                        if (header != null && cluster != null) {
                            header.count = cluster.size();
                        }
		        	}
		        	result.add(cluster);
		        }
		        queryResult.close();
		        storedLists.close();
		        
				if (HistoryActivity.DEBUG) Log.d("HistoryGridFragment", "DataSetForMatchingDateLoader - AsyncTask finished with " + result.size() + " items!");
			}
			
			return result;
		}
		
		@Override
	    protected void onPostExecute(HistoryGridAdapter.HistoryItemArray result) {
            searchData = null;
            activeSearch = false;

			if (activity != null) {
				indicator = activity.getIndicator();
				indicator.stopWorking(task);
			}
			else
				finishedTaskStack.add(task);
			
	        onCreatedDataSet(result);
        	if (gridAdapter != null) {
        		gridAdapter.setListenersDisabled(false);
        	}
			//activity.stopWorking();
	    }
		
	}
	private class DirectQueryLoader extends AsyncTask <Receipt.DatabaseHelper, Void, HistoryGridAdapter.HistoryItemArray> {
		
		private IndicatorFragment.Task task;
		private IndicatorFragment indicator;
		HistorySearchFragment.Query data;
		Precision precision;
		long hiddenId = -1;

        @Deprecated
		DirectQueryLoader(QueryData data, Precision precision) {
//			this.data = data;
			this.precision = precision;
		}

        DirectQueryLoader(HistorySearchFragment.Query query) {
            this.data = query;
            precision = Precision.makePrecision(StatsFragment.DateFormatMonthPrecision);
        }
		
		@Override
		protected void onPreExecute() {
			//activity.startWorking();
			task = IndicatorFragment.Task.createTask("Loading", null);
			if (activity != null) {
				indicator = activity.getIndicator();
				indicator.startWorking(task);
			}
			else {
				taskStack.add(task);
			}
        	if (gridAdapter != null) {
        		gridAdapter.deselect();
        		gridAdapter.setListenersDisabled(true);
        		hiddenId = gridAdapter.idOfHiddenScrap();
        	}
		}

		@Override
		protected HistoryGridAdapter.HistoryItemArray doInBackground(Receipt.DatabaseHelper... databaseHelper) {
			HistoryGridAdapter.HistoryItemArray result = new HistoryGridAdapter.HistoryItemArray();
			
			result.precisionData = precision;
			
			synchronized (Receipt.DatabaseLock) {
				SQLiteDatabase storedLists = databaseHelper[0].getReadableDatabase();
				if (HistoryActivity.DEBUG) Log.d("HistoryGridFragment", "AsyncTask started!");
				//Column orderL FilenameIdKey, DateKey, PriceKey, ItemCountKey
				//First cursor is current month
				Cursor queryResult = storedLists.rawQuery(data.rawQuery, data.args.toArray(new String[data.args.size()]));
				
				long lastDate = Long.MAX_VALUE;
				long currentDate;
				Calendar workCalendar = Calendar.getInstance();
				long grouper;

                long bigGrouper;
				
				int historyItemIndex = -1;
				int scrapClusterIndex = 0;
				
		        if (queryResult != null && queryResult.getCount() != 0) {
		        	if (isCancelled()) {
		    	        queryResult.close();
		    	        storedLists.close();
		        		return null;
		        	}

                    HistoryGridAdapter.Header bigHeader;
                    HistoryGridAdapter.Header header = null;
                    HistoryGridAdapter.ScrapCluster cluster = null;
		        	while (queryResult.moveToNext()) {
			        	if (isCancelled()) {
			    	        queryResult.close();
			    	        storedLists.close();
			        		return null;
			        	}
			        	
			        	currentDate = 1000l * queryResult.getLong(1);
		        		workCalendar.setTimeInMillis(lastDate);
		        		grouper = precision.getComparator(workCalendar);
                        bigGrouper = precision.getComparator(workCalendar);
		        		workCalendar.setTimeInMillis(currentDate);

		        		
		        		boolean areDifferent;
		        		areDifferent = grouper != precision.getComparator(workCalendar);
	
		        		if (areDifferent) {
		        			// we reached a different group, add a new header and commit the pending group
		        			if (cluster != null) result.add(cluster);
                            // on tablets, big headers limit large sections and regular headers limit mini sections
                            if (bigGrouper != precision.getComparator(workCalendar) && !phoneUI) {
                                bigHeader = new HistoryGridAdapter.Header(workCalendar);
                                bigHeader.precision = precision;
                                bigHeader.subtype = HistoryGridAdapter.ItemTypeBigHeader;
                                result.add(bigHeader);
                            }
                            if (header != null && cluster != null) {
                                header.count = cluster.size();
                            }
		    	        	header = new HistoryGridAdapter.Header(workCalendar);
                            header.precision = result.precisionData;
                            header.subtype = HistoryGridAdapter.ItemTypeHeader;
                            workCalendar = Calendar.getInstance();
		    	        	result.add(header);
		            		cluster = new HistoryGridAdapter.ScrapCluster(new ArrayList<HistoryGridAdapter.Scrap>());
		            		// There are two new items; one Header and one ScrapCluster
		            		historyItemIndex += 2;
		            		scrapClusterIndex = 0;
		        		}
		        		
		        		if ((precision.sqlPrecision == StatsFragment.DateFormatWeekdayPrecision
		        				|| precision.sqlPrecision == StatsFragment.DateFormatHourOfDayPrecision) && cluster == null) {
	
		    	        	header = new HistoryGridAdapter.Header(workCalendar);
                            header.precision = result.precisionData;
                            header.subtype = HistoryGridAdapter.ItemTypeHeader;
		    	        	workCalendar = Calendar.getInstance();
		    	        	result.add(header);
		            		cluster = new HistoryGridAdapter.ScrapCluster(new ArrayList<HistoryGridAdapter.Scrap>());
		            		// There are two new items; one Header and one ScrapCluster
		            		historyItemIndex += 2;
		            		scrapClusterIndex = 0;
		            		
		        		}
			        	
		        		HistoryGridAdapter.Scrap scrap = new HistoryGridAdapter.Scrap();
		        		scrap.targetId = queryResult.getLong(0);
		        		scrap.date = Calendar.getInstance();
		        		scrap.date.setTimeInMillis(currentDate);
		        		scrap.total = queryResult.getLong(2);
		        		scrap.itemCount = queryResult.getInt(3);
		        		cluster.cluster.add(scrap);
		        		lastDate = currentDate;
		        		
		        		if (scrap.targetId == hiddenId) {
		        			result.hiddenInnerIndex = scrapClusterIndex;
		        			result.hiddenOuterIndex = historyItemIndex;
		        			scrap.hidden = true;
		        		}
		        		scrapClusterIndex++;

                        if (header != null && cluster != null) {
                            header.count = cluster.size();
                        }
		        	}
		        	result.add(cluster);
		        }
		        queryResult.close();
		        storedLists.close();
		        
				if (HistoryActivity.DEBUG) Log.d("HistoryGridFragment", "DataSetForMatchingDateLoader - AsyncTask finished with " + result.size() + " items!");
			}
			
			return result;
		}
		
		@Override
	    protected void onPostExecute(HistoryGridAdapter.HistoryItemArray result) {
			if (activity != null) {
				indicator = activity.getIndicator();
				indicator.stopWorking(task);
			}
			else
				finishedTaskStack.add(task);
			
	        onCreatedDataSet(result);
        	if (gridAdapter != null) {
        		gridAdapter.setListenersDisabled(false);
        	}
			//activity.stopWorking();
	    }
		
	}
	
	private HistoryActivity activity;
	private HistoryGridAdapter gridAdapter;
	private boolean phoneUI;
    private boolean useCollectionView;
    private long idOfHiddenScrap;

    private int columnCount;
    private CollectionView collectionView;

    private CollectionView sectionCollection;
    private SectionController sectionController;

    private DisplayMetrics metrics;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
        phoneUI = (getResources().getConfiguration().smallestScreenWidthDp < 600);
        setRetainInstance(true);

        useCollectionView = true;
        controller.setComparator(new CollectionViewController.ObjectComparator() {
            @Override
            public boolean areObjectsEqual(Object object1, Object object2) {
                if (activeSearch && !phoneUI) {
                    if (object1 instanceof HistoryGridAdapter.Header) {
                        if (((HistoryGridAdapter.Header) object1).subtype == HistoryGridAdapter.ItemTypeHeader) return false;
                    }
                    if (object2 instanceof HistoryGridAdapter.Header) {
                        if (((HistoryGridAdapter.Header) object2).subtype == HistoryGridAdapter.ItemTypeHeader) return false;
                    }
                }
                return object1.equals(object2);
            }
        });

        sectionController = new SectionController();
        sectionController.setComparator(new CollectionViewController.ObjectComparator() {
            @Override
            public boolean areObjectsEqual(Object object1, Object object2) {
                // The section collection only holds header objects
                HistoryGridAdapter.Header header1 = (HistoryGridAdapter.Header) object1;
                HistoryGridAdapter.Header header2 = (HistoryGridAdapter.Header) object2;
                if (header1.subtype == Integer.MIN_VALUE && header2.subtype == Integer.MIN_VALUE) {
                    return header1.date.get(Calendar.YEAR) == header2.date.get(Calendar.YEAR);
                }
                if (header1.subtype != Integer.MIN_VALUE && header2.subtype != Integer.MIN_VALUE) {
                    return header1.date.get(Calendar.YEAR) * 100 + header1.date.get(Calendar.MONTH)
                            == header2.date.get(Calendar.YEAR) * 100 + header2.date.get(Calendar.MONTH);
                }
                return false;
            }
        });

        /*
        if (phoneUI)
        	// On phones show months from the start of time to the end of time
        	new DataSetForMatchingDateLoader(Long.MIN_VALUE, Long.MAX_VALUE, DateFormatMonthPrecision).execute(Receipt.DBHelper);
        	*/
	}
	
	public void update() {
		if (DEBUG_RELOAD) Log.d("HistoryGridFragment", "About to reload!");
		if (activeSearch) new DirectQueryLoader(searchData).execute(Receipt.DBHelper);
		else new DataSetForMatchingDateLoader(Long.MIN_VALUE, Long.MAX_VALUE, StatsFragment.DateFormatMonthPrecision).execute(Receipt.DBHelper);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_history, container, false);
		
	}
	
	@Override
	public void onAttach(Activity activity) {
		super.onAttach(activity);
		this.activity = (HistoryActivity) activity;
		if (Receipt.DBHelper == null) {
        	Receipt.DBHelper = new Receipt.DatabaseHelper(activity.getApplicationContext());
        }
		storedLists = Receipt.DBHelper.getWritableDatabase();
			// new DataSetForMatchingDateLoader(Calendar.getInstance().getTimeInMillis(), null).execute(Receipt.DBHelper);
		if (gridAdapter != null)  {
			gridAdapter.attach(this.activity);
		}
		
	}

    public void confirmClearHistory() {
        ((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar)).createActionConfirmationContextMode(
                getString(R.string.ConfirmWholeHistory), getString(R.string.DeleteLabel), R.drawable.ic_action_delete, new LegacyActionBar.ContextBarListener() {
                    @Override
                    public void onContextBarStarted() {
                    }

                    @Override
                    public void onContextBarDismissed() {
                    }

                    @Override
                    public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
                        if (item.getId() == R.id.ConfirmOK) {
                            activity.clearHistory();
                        } else {
                            ((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar)).handleBackPress();
                        }
                    }
                }).start();
    }
	
	public void clearHistory() {
		new AsyncTask<Void, Void, Void>() {
			public Void doInBackground(Void ... input) {
				synchronized(Receipt.DatabaseLock) {
					SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();
					db.execSQL("DROP TABLE IF EXISTS " + Receipt.DBReceiptsTable + ";");
					db = Receipt.DBHelper.getWritableDatabase();
					db.execSQL(Receipt.CreateReceiptDatabase);
//					db = Receipt.DBHelper.getWritableDatabase();
					db.execSQL("DROP TABLE IF EXISTS " + Receipt.DBItemsTable + ";");
//					db = Receipt.DBHelper.getWritableDatabase();
					db.execSQL(Receipt.CreateItemsDatabase);
                    db.execSQL("DROP TABLE IF EXISTS " + Receipt.DBTagConnectionsTable + ";");
                    db.execSQL(Receipt.CreateTagConnectionsDatabase);
					db.close();
				}
				return null;
			}
		}.execute();/*
		HistoryGridAdapter.HistoryItemArray cleanData = new HistoryGridAdapter.HistoryItemArray();
		gridAdapter.adaptData(cleanData);*/
	}
	
	private ArrayList<IndicatorFragment.Task> taskStack = new ArrayList<IndicatorFragment.Task>();
	private ArrayList<IndicatorFragment.Task> finishedTaskStack = new ArrayList<IndicatorFragment.Task>();

    private CollectionView.ReversibleAnimation deleteAnimatorStandard = new CollectionView.ReversibleAnimation() {
        @Override
        public void playAnimation(View view, Object object, int viewType) {
            if (viewType == HistoryGridAdapter.ItemTypeHeader) {
                view.animate().xBy(10 * getResources().getDisplayMetrics().density).alpha(0f).scaleX(0.99f);

            } else {
                view.animate().alpha(0);
            }
        }

        @Override
        public void resetState(View view, Object object, int viewType) {
            if (viewType == HistoryGridAdapter.ItemTypeHeader) {
                view.setX(view.getX() - 10 * getResources().getDisplayMetrics().density);
                view.setAlpha(1f);
                view.setScaleX(1f);
            } else {
                view.setAlpha(1);
            }
        }
    };

    private CollectionView.ReversibleAnimation insertAnimationStandard = new CollectionView.ReversibleAnimation() {
        @Override
        public void playAnimation(View view, Object object, int viewType) {
            if (viewType == HistoryGridAdapter.ItemTypeHeader || viewType == HistoryGridAdapter.ItemTypeBigHeader) {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                view.setX(view.getX() - 10 * getResources().getDisplayMetrics().density);
                view.setScaleX(0.99f);
                if (view.getY() + view.getHeight() > collectionView.getScrollY() && view.getY() < collectionView.getY() + collectionView.getHeight()) {
                    ((ViewGroup) view).getChildAt(0).setAlpha(0f);
                    ((ViewGroup) view).getChildAt(0).animate().alpha(1f).start();
                }
                view.animate().xBy(10 * getResources().getDisplayMetrics().density).scaleX(1f);
            } else {
                view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                view.buildLayer();
                view.setAlpha(0f);
                view.setScaleY(0.6f);
                view.setScaleX(0.6f);
                view.animate().alpha(1f).scaleX(1f).scaleY(1f);
            }
        }

        @Override
        public void resetState(View view, Object object, int viewType) {
        }
    };

    private CollectionView.ReversibleAnimation sectionInsertAnimation = new CollectionView.ReversibleAnimation() {
        @Override
        public void playAnimation(View view, Object object, int viewType) {
            view.setAlpha(0f);
            view.animate().alpha(1f);
        }

        @Override
        public void resetState(View view, Object object, int viewType) {}
    };
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

        collectionView = (CollectionView) activity.findViewById(R.id.History);

        metrics = activity.getResources().getDisplayMetrics();

//        int columnCount;
//
//        Configuration config = getResources().getConfiguration();
//        int swdp = config.smallestScreenWidthDp;
//        if (swdp < 600) {
//            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                columnCount = HistoryGridAdapter.PhoneLandscapeColumnCount;
//                if (swdp > 359)
//                    columnCount = HistoryGridAdapter.GalaxyNexusLandscapeColumnCount;
//            }
//            else
//                columnCount = HistoryGridAdapter.PhonePortraitColumnCount;
//        }
//        else if (swdp < 720) {
//            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
//                columnCount = HistoryGridAdapter.TabletLandscapeColumnCount;
//            else  {
//                columnCount = HistoryGridAdapter.TabletPortraitColumnCount;
//            }
//        }
//        else {
//            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
//                columnCount = HistoryGridAdapter.LargeTabletLandscapeColumnCount;
//            else  {
//                columnCount = HistoryGridAdapter.LargeTabletPortraitColumnCount;
//            }
//        }

        final View History = collectionView;
        History.setVisibility(View.VISIBLE);
        collectionView.setDeleteAnimator(deleteAnimatorStandard);
        collectionView.setInsertAnimator(insertAnimationStandard);
        History.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (History.getHeight() > 0) {
                    //noinspection deprecation
                    History.getViewTreeObserver().removeGlobalOnLayoutListener(this);

                    int usableWidth = collectionView.getWidth() - collectionView.getPaddingLeft() - collectionView.getPaddingRight();
                    int scrapWidth = getResources().getDimensionPixelSize(R.dimen.HistoryScrapSize);
                    columnCount = usableWidth / scrapWidth;
                    controller.setColumnCountForViewType(columnCount, HistoryGridAdapter.ItemTypeScrap);
                    collectionView.setController(controller);

                    if (idOfHiddenScrap != -1) {
                        Scrap scrap = new Scrap();
                        scrap.targetId = idOfHiddenScrap;
                        View scrapView = controller.retainViewForObject(scrap);
                        if (scrapView != null)
                            scrapView.setVisibility(View.INVISIBLE);
                    }

                    final View SearchBox = activity.findViewById(R.id.SearchBoxLayout);
                    if (SearchBox.getWidth() + ((ViewGroup.MarginLayoutParams) SearchBox.getLayoutParams()).leftMargin * 2 >
                            ((View) SearchBox.getParent()).getWidth()) {
                        SearchBox.getLayoutParams().width = ViewGroup.LayoutParams.MATCH_PARENT;
                        SearchBox.requestLayout();
                    }
                }
            }
        });

        collectionView.setOnViewCollectedListener(new CollectionView.OnViewCollectedListener() {
            @Override
            public void onViewCollected(CollectionView collectionView, View view, int viewType) {
                if (viewType == HistoryGridAdapter.ItemTypeScrap) {
                    ((LegacyRippleDrawable) view.findViewById(R.id.ScrapRipple).getBackground()).flushRipple();
                }
            }
        });

        sectionCollection = (CollectionView) activity.findViewById(R.id.SectionCollection);
        sectionCollection.setInsertAnimator(sectionInsertAnimation);
        sectionCollection.setController(sectionController);
		
		if (pendingResult != null) {
			onCreatedDataSet(pendingResult);
			pendingResult = null;
		}
		
		if (taskStack.size() > 0) {
			IndicatorFragment indicator = activity.getIndicator();
			for (IndicatorFragment.Task task : taskStack) {
				indicator.startWorking(task);
				taskStack.remove(task);
			}
		}
		
		if (finishedTaskStack.size() > 0) {
			IndicatorFragment indicator = activity.getIndicator();
			for (IndicatorFragment.Task task : finishedTaskStack) {
				indicator.stopWorking(task);
				finishedTaskStack.remove(task);
			}
		}

        if (selectionList.size() > 0) {
            activity.disablePaging();
        }

	}

    @Override
    public void onDetach() {
        super.onDetach();
        storedLists.close();
        storedLists = null;
        activity = null;
        collectionView = null;
        sectionCollection = null;
        if (gridAdapter != null) gridAdapter.detach();
    }

    private CollectionViewController controller = new CollectionViewController() {
        @Override
        public View createEmptyView(ViewGroup container, LayoutInflater inflater) {
            if (firstDataSet) {
                return null;
            }

            if (activeSearch) {
                View v = inflater.inflate(R.layout.layout_empty, container, false);
                v.findViewById(R.id.EmptyImage).setVisibility(View.GONE);
                ((TextView) v.findViewById(R.id.EmptyText)).setText(getString(R.string.NoResults));
                ((TextView) v.findViewById(R.id.EmptyText)).setTypeface(Receipt.condensedTypeface());
                return v;
            }

            View v = inflater.inflate(R.layout.layout_empty, container, false);
            v.findViewById(R.id.EmptyImage).setVisibility(View.GONE);
            ((TextView) v.findViewById(R.id.EmptyText)).setTypeface(Receipt.condensedTypeface());
            return v;
        }

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            if (viewType == HistoryGridAdapter.ItemTypePlaceholder) {
                View returnedView = new View(container.getContext());TypedValue tv = new TypedValue();
                int height;
                if (container.getContext().getTheme().resolveAttribute(android.R.attr.actionBarSize, tv, true))
                {
                    height = TypedValue.complexToDimensionPixelSize(tv.data, container.getResources().getDisplayMetrics());
                }
                else {
                    height = (int) (48 * container.getResources().getDisplayMetrics().density);
                }

                if (height < container.getResources().getDimensionPixelSize(R.dimen.DP48)) {
                    height = container.getResources().getDimensionPixelSize(R.dimen.DP48);
                }
                height = height - (int) (8 * container.getResources().getDisplayMetrics().density);
                height = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 1 : height;
                returnedView.setLayoutParams(new FrameLayout.LayoutParams(0, height));
                return returnedView;
            }
            if (viewType == HistoryGridAdapter.ItemTypeHeader) {
                View returnedView = inflater.inflate(R.layout.history_header, container, false);
                HistoryGridAdapter.HeaderViewHolder holder = new HistoryGridAdapter.HeaderViewHolder();
                holder.title = (TextView)returnedView.findViewById(R.id.HeaderTitle);
                returnedView.setTag(holder);
                return returnedView;
            }
            else if (viewType == HistoryGridAdapter.ItemTypeBigHeader) {
                View returnedView = inflater.inflate(R.layout.history_big_header, container, false);
                HistoryGridAdapter.HeaderViewHolder holder = new HistoryGridAdapter.HeaderViewHolder();
                holder.title = (TextView)returnedView.findViewById(R.id.HeaderTitle);
                holder.title.setTypeface(Receipt.condensedTypeface());
                returnedView.setTag(holder);
                return returnedView;
            }
            else {
                View returnedView = inflater.inflate(R.layout.history_scrap, container, false);
                HistoryGridAdapter.ScrapViewHolder holder = new HistoryGridAdapter.ScrapViewHolder();
                holder.date = (TextView)returnedView.findViewById(R.id.HistoryDate);

                holder.date.setTypeface(Receipt.condensedTypeface());
                holder.date.setTextColor(getResources().getColor(R.color.DashboardSecondaryText));
//                holder.date.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
//                holder.date.setTextSize(20);

                holder.total = (TextView)returnedView.findViewById(R.id.HistoryPrice);
                holder.total.setTypeface(Receipt.condensedTypeface());
//                holder.scrapBase = (RelativeLayout)returnedView.findViewById(R.id.HistoryScrap1);
                returnedView.setTag(holder);
                LegacyRippleDrawable background = new LegacyRippleDrawable(getActivity());
                background.setShape(LegacyRippleDrawable.ShapeRoundRect);
                background.setSelectedColors(com.BogdanMihaiciuc.util.Utils.transparentColor(0.5f, getResources().getColor(android.R.color.holo_blue_light)),
                        com.BogdanMihaiciuc.util.Utils.overlayColors(com.BogdanMihaiciuc.util.Utils.transparentColor(0.5f, getResources().getColor(android.R.color.holo_blue_light)), LegacyRippleDrawable.DefaultPressedColor));
                background.setNotificationColors(com.BogdanMihaiciuc.util.Utils.transparentColor(0.5f, getResources().getColor(android.R.color.holo_orange_dark)),
                        com.BogdanMihaiciuc.util.Utils.overlayColors(com.BogdanMihaiciuc.util.Utils.transparentColor(0.5f, getResources().getColor(android.R.color.holo_orange_dark)), LegacyRippleDrawable.DefaultPressedColor));
                returnedView.findViewById(R.id.ScrapRipple).setBackground(background);
                return returnedView;
            }
        }

        @Override
        public void configureView(View view, Object item, int viewType) {
            if (viewType == HistoryGridAdapter.ItemTypeHeader) {
                HistoryGridAdapter.HeaderViewHolder holder = (HistoryGridAdapter.HeaderViewHolder) view.getTag();
                HistoryGridAdapter.Header header = (HistoryGridAdapter.Header) item;
                if (phoneUI) {
                    holder.title.setText(precision.sectionTitle.getTitle(activity, header.date));
                }
                else {
                    if (header.count == 1) {
                        holder.title.setText(getString(R.string.SourceLibrarySingleText));
                    }
                    else {
                        holder.title.setText(String.format(getString(R.string.SourceLibraryText), header.count));
                    }
                }
            }
            else if (viewType == HistoryGridAdapter.ItemTypePlaceholder) {}
            else if (viewType == HistoryGridAdapter.ItemTypeBigHeader) {
                HistoryGridAdapter.HeaderViewHolder holder = (HistoryGridAdapter.HeaderViewHolder) view.getTag();
                HistoryGridAdapter.Header header = (HistoryGridAdapter.Header) item;
                SpannableStringBuilder date = new SpannableStringBuilder();

                date.append(header.date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault())).append(" ");

                if (Calendar.getInstance().get(Calendar.YEAR) != header.date.get(Calendar.YEAR)) {
                    com.BogdanMihaiciuc.util.Utils.appendWithSpan(date, "'" + Integer.toString(header.date.get(Calendar.YEAR) % 100),
                            new ForegroundColorSpan(getResources().getColor(R.color.DashboardTitle)));
                }
                holder.title.setText(date);
            }
            else {
                final Scrap scrap = (Scrap) item;
                SpannableStringBuilder date = new SpannableStringBuilder();
                date.append(String.valueOf(scrap.date.get(Calendar.DATE)));
//                date.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.HistoryScrapDate)), 0, date.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                int start = date.length();
                date.append(" ").append(scrap.date.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, Locale.getDefault()));
                int end = date.length();
                date.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.HistoryScrapMonth)), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                date.setSpan(new TypefaceSpan("sans-serif"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                date.setSpan(new com.BogdanMihaiciuc.util.Utils.CustomTypefaceSpan(Receipt.condensedLightTypeface()), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
//                date.setSpan(new RelativeSizeSpan(0.8f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

                HistoryGridAdapter.ScrapViewHolder holder = (HistoryGridAdapter.ScrapViewHolder) view.getTag();
                holder.date.setText(date);
                holder.total.setText(ReceiptActivity.shortFormattedTotalWithCutoff(activity, scrap.total, 3));

                view.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (selectionList.size() > 0) {
                            view.setSelected(scrap.selected = !scrap.selected);
                            onSelectionChangedForScrap(scrap);
                            return;
                        }
                        view.setVisibility(View.INVISIBLE);
                        controller.retainView(view);
                        idOfHiddenScrap = scrap.targetId;
                        activity.showScrapFromView(scrap.targetId, view);
                    }
                });

                view.setOnLongClickListener(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View view) {
                        view.setSelected(scrap.selected = !scrap.selected);
                        onSelectionChangedForScrap(scrap);
                        return true;
                    }
                });

                if (view.isSelected() != scrap.selected) {
                    if (!isRefreshingViews()) {
                        ((LegacyRippleDrawable) view.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingAnimation();
                    }
                    view.setSelected(scrap.selected);
                }

                boolean activated = (deleteConfirmator != null);
                if (view.isActivated() != activated) {
                    if (!isRefreshingViews()) {
                        ((LegacyRippleDrawable) view.findViewById(R.id.ScrapRipple).getBackground()).dismissPendingAnimation();
                    }
                    view.setActivated(activated);
                }
            }
        }
    };

    public void cloneScrapView(View view, View object, int viewType) {
        HistoryGridAdapter.ScrapViewHolder holder = new HistoryGridAdapter.ScrapViewHolder();
        holder.date = (TextView)view.findViewById(R.id.HistoryDate);

        holder.date.setTypeface(Receipt.condensedTypeface());
        holder.date.setTextColor(getResources().getColor(R.color.DashboardSecondaryText));
//                holder.date.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
//                holder.date.setTextSize(20);

        holder.total = (TextView)view.findViewById(R.id.HistoryPrice);
        holder.total.setTypeface(Receipt.condensedTypeface());
//                holder.scrapBase = (RelativeLayout)returnedView.findViewById(R.id.HistoryScrap1);
        view.setTag(holder);
        controller.configureView(view, collectionView.getObjectForView(object), viewType);
    }
	
	private HistoryGridAdapter.HistoryItemArray pendingResult;

    boolean firstDataSet = true;
	
	public void onCreatedDataSet(final HistoryGridAdapter.HistoryItemArray result) {
		if (activity == null) {
			pendingResult = result;
			return;
		}

        // Reconstruct the selection by matching scraps from the new dataset with those in the selection list
        if (selectionList.size() > 0) {
            ArrayList<Scrap> oldSelection = new ArrayList<Scrap>(selectionList);
            Scrap workScrap;

            selectionList.clear();
            selectionTotal = new BigDecimal(0);
            for (Scrap scrap : oldSelection) {
                workScrap = result.findScrapWithUID(scrap.targetId);
                if (workScrap != null) {
                    selectionList.add(workScrap);
                    selectionTotal = selectionTotal.add(new BigDecimal(workScrap.total).movePointLeft(2));
                    workScrap.selected = true;
                }
            }

            if (contextBar != null) {
                contextBar.setTitleAnimated(selectionList.size() + " selected", selectionList.size() - oldSelection.size() < 0 ? -1 : 1);
                contextBar.setSubtitle(ReceiptActivity.currentLocale + selectionTotal + " total");
            }

            if (selectionList.size() == 0 && contextBar != null) {
                contextBar.dismiss();
            }

        }

        // Precision is now fixed at month for phones, week for tablets
        this.precision = result.precisionData;
//        int columnCount;
//
//        Configuration config = getResources().getConfiguration();
//        int swdp = config.smallestScreenWidthDp;
//        if (swdp < 600) {
//            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
//                columnCount = HistoryGridAdapter.PhoneLandscapeColumnCount;
//                if (swdp > 359)
//                    columnCount = HistoryGridAdapter.GalaxyNexusLandscapeColumnCount;
//            }
//            else
//                columnCount = HistoryGridAdapter.PhonePortraitColumnCount;
//        }
//        else if (swdp < 720) {
//            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
//                columnCount = HistoryGridAdapter.TabletLandscapeColumnCount;
//            else  {
//                columnCount = HistoryGridAdapter.TabletPortraitColumnCount;
//            }
//        }
//        else {
//            if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
//                columnCount = HistoryGridAdapter.LargeTabletLandscapeColumnCount;
//            else  {
//                columnCount = HistoryGridAdapter.LargeTabletPortraitColumnCount;
//            }
//        }

        // This ends all in progress animations and closes any internal locks on the controller
//            collectionView.setAnimationsEnabled(false);
//            collectionView.setAnimationsEnabled(true);

        controller.requestBeginTransaction();
        sectionController.requestBeginNewDataSetTransaction();

        controller.clear();
        if (result.size() > 0) {
            controller.addSectionForViewTypeWithTag(HistoryGridAdapter.ItemTypePlaceholder, null);
            controller.getSectionAtIndex(0).addObject(PlaceholderObject);
        }
        int lastItemType = Integer.MIN_VALUE;
        CollectionView.Section lastSection = null;

        int lastYear = Integer.MIN_VALUE;
        CollectionView.Section lastSectionSection = null;
        for (HistoryGridAdapter.HistoryItem item : result.innerData) {
            if (item.getItemType() != lastItemType) {
                lastItemType = item.getItemType();
                if (lastItemType == HistoryGridAdapter.ItemTypeHeader) {
                    lastItemType = ((HistoryGridAdapter.Header) item).subtype;
                }
                lastSection = controller.addSectionForViewTypeWithTag(lastItemType, null);
                if (lastItemType == HistoryGridAdapter.ItemTypeScrap) {
                    lastSection.setColumnCount(columnCount);
                }
                else {
                    lastSection.setColumnCount(1);
                }
            }

            if (item.getItemType() == HistoryGridAdapter.ItemTypeScrap) {
                lastSection.addAllObjects(((HistoryGridAdapter.ScrapCluster) item).cluster);
            }
            else {
                lastSection.addObject(item);
                HistoryGridAdapter.Header header = (HistoryGridAdapter.Header) item;
                if (lastYear != header.date.get(Calendar.YEAR)) {
                    lastYear = header.date.get(Calendar.YEAR);
                    lastSectionSection = sectionController.addSectionForViewTypeWithTag(ViewTypeSectionHeader, null);
                    // Changing the header subtype to ensure the comparator sees the two objects as different
                    int subtype = header.subtype;
                    header.subtype = Integer.MIN_VALUE;
                    lastSectionSection.addObject(new HistoryGridAdapter.Header(header));
                    header.subtype = subtype;

                    lastSectionSection = sectionController.addSectionForViewTypeWithTag(ViewTypeSection, null);
                }

                // Phones get all the regular headers as sections, tablets get the big headers as sections
                // When a search is active, tablets behave the same as phones
                if (phoneUI && lastSection.getViewType() == HistoryGridAdapter.ItemTypeHeader) {
                    lastSectionSection.addObject(item);
                }
                if (!phoneUI && lastSection.getViewType() == HistoryGridAdapter.ItemTypeBigHeader) {
                    lastSectionSection.addObject(item);
                }
            }
        }

        if (firstDataSet ||
                (phoneUI && ((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar)).getSelectedNavigationIndex() != 0)) {
            collectionView.setAnimationsEnabled(false);

            sectionCollection.setAnimationsEnabled(false);
        }
        else {
            if (collectionView.getScrollY() < 48 * getResources().getDisplayMetrics().density) {
                collectionView.setTransactionScrollingMode(CollectionView.TransactionScrollingModeTop);
            }
            else {
                collectionView.setTransactionScrollingMode(CollectionView.TransactionScrollingModeAnchor);
            }
            if (!phoneUI) {
                boolean customAnimationCondition = collectionView.getTag() != null ? (activeSearch || (Boolean) collectionView.getTag()) : activeSearch;
                if (customAnimationCondition) {
                    collectionView.setInsertAnimator(insertAnimationStandard);
                    collectionView.setDeleteAnimator(deleteAnimatorStandard);
                }
                else {
//                        collectionView.setInsertAnimator(insertAnimatorNav);
//                        collectionView.setDeleteAnimator(deleteAnimatorNav);
                }
            }
        }

        controller.requestCompleteTransaction();
        collectionView.setAnimationsEnabled(true);

        sectionController.requestCompleteTransaction();
        sectionCollection.setAnimationsEnabled(true);

        if (firstDataSet) {
            firstDataSet = false;
            collectionView.ensureMinimumSupplyForViewType(10, HistoryGridAdapter.ItemTypeScrap);
            collectionView.ensureMinimumSupplyForViewType(4, HistoryGridAdapter.ItemTypeHeader);
        }

        if (activeSearch) collectionView.setTag(true);
        else collectionView.setTag(false);

        if (idOfHiddenScrap != -1) {
            Scrap scrap = new Scrap();
            scrap.targetId = idOfHiddenScrap;
            View scrapView = controller.retainViewForObject(scrap);
            if (scrapView != null)
                scrapView.setVisibility(View.INVISIBLE);
        }
	}
	
	public View getHiddenScrapIfVisible() {
        if (useCollectionView) {
            Scrap scrap = new Scrap();
            scrap.targetId = idOfHiddenScrap;
            return controller.getViewForObject(scrap);
        }
		return gridAdapter.getHiddenScrapOrHideIfOffscreen(false);
	}

	public boolean getHiddenScrapAvailability() {
        if (useCollectionView) {
            Scrap scrap = new Scrap();
            scrap.targetId = idOfHiddenScrap;
            return controller.getViewForObject(scrap) != null;
        }
        else return gridAdapter.isHiddenScrapAvailable();
	}
	
	public void resetHiddenScrap() {
        if (useCollectionView) {
            Scrap scrap = new Scrap();
            scrap.targetId = idOfHiddenScrap;
            View view;
            if ((view = controller.getViewForObject(scrap)) != null) {
                controller.releaseView(view);
            }
            idOfHiddenScrap = -1;
        }
        else {
            if (gridAdapter != null)
                gridAdapter.resetHiddenScrap();
        }
	}

    public void holdTransaction() {
        controller.holdTransaction();
    }

    public void releaseTransaction() {
        controller.releaseTransaction();
    }
	
	public int getLocationOfOffscreenHiddenScrap() {
        if (useCollectionView)
            return 1;
		return gridAdapter.getLocationOfOffscreenHiddenScrap();
	}
	
	public void setListenersDisabled(boolean disabled) {
        if (!useCollectionView)
		    gridAdapter.setListenersDisabled(disabled);
	}
	

	@SuppressWarnings("unchecked")
	public void deleteSelection(final ArrayList<HistoryGridAdapter.Scrap> selection) {
		new AsyncTask<ArrayList<HistoryGridAdapter.Scrap>, Void, Void>() {
			private Task task;
			private IndicatorFragment indicator;
			protected void onPreExecute() {
				if (gridAdapter != null)
					gridAdapter.setListenersDisabled(true);
				task = IndicatorFragment.Task.createTask("Deleting", null);
				indicator = activity.getIndicator();
				indicator.startWorking(task);
			}
			@Override
			protected Void doInBackground(ArrayList<HistoryGridAdapter.Scrap> ... params) {
				ArrayList<HistoryGridAdapter.Scrap> selection = params[0];
				synchronized(Receipt.DatabaseLock) {
					SQLiteDatabase db = Receipt.DBHelper.getWritableDatabase();
					for (HistoryGridAdapter.Scrap scrap : selection) {
					    db.delete(Receipt.DBReceiptsTable, Receipt.DBFilenameIdKey + "=" + scrap.targetId, null);
                        Cursor items = db.query(Receipt.DBItemsTable, new String[] {Receipt.DBItemUIDKey}, Receipt.DBTargetDBKey + " = " + scrap.targetId,
                                null, null, null, null);
                        while (items.moveToNext()) {
                            db.delete(Receipt.DBTagConnectionsTable, Receipt.DBItemConnectionUIDKey + " = " + items.getLong(0), null);
                        }
                        items.close();
					    db.delete(Receipt.DBItemsTable, Receipt.DBTargetDBKey + "=" + scrap.targetId, null);
					}
					db.close();
				}
				return null;
			}
			protected void onPostExecute(Void result) {
				if (gridAdapter != null)
					gridAdapter.setListenersDisabled(false);
				if (activity != null)
					activity.update();
				indicator.stopWorking(task);
			}
		}.execute((ArrayList<Scrap>) selection.clone());
	}
	
	private boolean loadedInitially;
	private boolean activeSearch;
	private HistorySearchFragment.Query searchData;
	private Precision precision;

    @Deprecated
	public void loadDirectQuery(QueryData data, Precision precision) {
        if (true) throw new RuntimeException("Deprecated method: loadDirectQuery()");
//		searchData = data;
		activeSearch = true;
		this.precision = precision;
		new DirectQueryLoader(data, precision).execute(Receipt.DBHelper);
	}

    public void search(HistorySearchFragment.Query query) {
        activeSearch = true;
        searchData = query;
        new DirectQueryLoader(query).execute(Receipt.DBHelper);
    }

    public void clearSearch() {
        activeSearch = false;
        searchData = null;
        loadDataForPrecision(StatsFragment.DateFormatMonthPrecision);
    }
	
	public void loadInitialDataForPrecision(String precision, boolean phoneUI) {
		if (!loadedInitially) {
			this.phoneUI = phoneUI;
			loadDataForPrecision(precision);
		}
		loadedInitially = true;
	}
	
	public void loadDataForPrecisionAndDate(String precision, long unixDate) {
		new DataSetForMatchingDateLoader(unixDate, precision).execute(Receipt.DBHelper);
	}
	
	public void loadDataForPrecision(String precision) {
        if (gridAdapter != null) {
            gridAdapter.setListenersDisabled(true);
        }
        new DataSetForMatchingDateLoader(Long.MIN_VALUE, Long.MAX_VALUE, precision).execute(Receipt.DBHelper);
	}

    // *************** SECTION COLLECTION ***************
    final static int ViewTypeSection = 0;
    final static int ViewTypeSectionHeader = 1;
    private class SectionController extends CollectionViewController {

        @Override
        public View createView(int viewType, ViewGroup container, LayoutInflater inflater) {
            if (viewType == ViewTypeSection) {

                ViewGroup header = (ViewGroup) inflater.inflate(R.layout.history_header, container, false);
                TextView view = (TextView) header.getChildAt(0);
                view.setTextColor(getResources().getColor(R.color.DashboardText));
                header.setBackgroundDrawable(new LegacyRippleDrawable(activity));
                view.setAllCaps(false);

                int paddingMultiplier = phoneUI ? 1 : 2;

                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    header.setPadding((int) (paddingMultiplier * 32 * metrics.density + 0.5f), 0, 0, 0);
                }
                else {
                    header.setPadding((int) (paddingMultiplier * 16 * metrics.density + 0.5f), 0, 0, 0);
                }

                header.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Object header = sectionCollection.getObjectForView(view);

                        // The sectionCollection object is the same instance as the collectionView header object
                        View mainHeader = collectionView.retainViewForObject(header);
                        int requiredScroll = (int) ((View) mainHeader.getParent()).getY();
                        int currentScroll = collectionView.getScrollY();

                        // when scroll direction is up, need to account for the searchbar popping up, potrait only
                        if (requiredScroll < currentScroll && getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE) {
                            requiredScroll -= activity.findViewById(R.id.SearchBoxLayout).getHeight();
                        }
                        collectionView.smoothScrollTo(0, requiredScroll);

                        // On phones, also collapse the navigation panel
                        if (phoneUI) {
                            activity.collapseNavigationPanel();
                        }
                    }
                });

                header.getLayoutParams().height = getResources().getDimensionPixelSize(R.dimen.GenericHeaderHeight);

                return header;
            }
            else {
                ViewGroup header = (ViewGroup) inflater.inflate(R.layout.history_header, container, false);
                TextView view = (TextView) header.getChildAt(0);
                view.setTextColor(getResources().getColor(R.color.DashboardTitle));

                int paddingMultiplier = phoneUI ? 1 : 2;
//                paddingMultiplier += getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE ? 1 : 0;

                header.getLayoutParams().height = (int) (72 * metrics.density + 0.5f);

                if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    header.setPadding((int) (paddingMultiplier * 32 * metrics.density + 0.5f), header.getPaddingTop() + (int) (16 * metrics.density), 0, header.getPaddingBottom());
                }
                else {
                    header.setPadding((int) (paddingMultiplier * 16 * metrics.density + 0.5f), header.getPaddingTop() + (int) (16 * metrics.density), 0, header.getPaddingBottom());
                }

                return header;
            }
        }

        @Override
        public void configureView(View view, Object item, int viewType) {
            HistoryGridAdapter.Header header = (HistoryGridAdapter.Header) item;
            TextView headerView = (TextView) view.findViewById(R.id.HeaderTitle);

            if (viewType == ViewTypeSectionHeader) {
                headerView.setText(Integer.toString(header.date.get(Calendar.YEAR)));
            }
            else {
                headerView.setText(header.date.getDisplayName(Calendar.MONTH, Calendar.LONG, Locale.getDefault()));
                view.setSelected(header.selected);
                if (view.isSelected()) {
                    headerView.setTextColor(getResources().getColor(R.color.DashboardText));
                }
                else {
                    headerView.setTextColor(getResources().getColor(R.color.ItemStrikethroughText));
                }
            }
        }
    }

    public boolean isSearchActive() {
        return activeSearch;
    }

    private CollectionView.Section currentSection;

    public void onCurrentSectionChanged(CollectionView.Section section) {
        if (currentSection != null) {
            ((HistoryGridAdapter.Header) currentSection.getObjectAtIndex(0)).selected = false;
        }
        currentSection = section;
        if (currentSection != null) {
            ((HistoryGridAdapter.Header) currentSection.getObjectAtIndex(0)).selected = true;
        }

        if (sectionCollection != null && currentSection != null && !phoneUI) {
            View view = sectionCollection.getViewForObject(currentSection.getObjectAtIndex(0));
            if (view == null) {
                sectionCollection.smoothScrollToObject(currentSection.getObjectAtIndex(0));
            }

            sectionCollection.refreshViews();
        }
    }

    private HistoryViewerFragment.SearchResolver searchResolver = new HistoryViewerFragment.SearchResolver() {
        @Override
        public boolean matchesSearch(String string) {
            if (searchData == null) {
                if (HistorySearchFragment.DEBUG_SEARCHRESOLVER) Log.d(TAG, "The searchData is NULL!");
                return false;
            }
            else {
                if (HistorySearchFragment.DEBUG_SEARCHRESOLVER) Log.d(TAG, "Resolving against search resolver!");
                return searchData.matchesSearch(string);
            }
        }
    };

    public HistoryViewerFragment.SearchResolver obtainSearchResolver() {
        return searchResolver;
    }

    // ------------------------
    // Selection and action mode
    // ------------------------

    // selection handlers
    private ArrayList<Scrap> selectionList = new ArrayList<Scrap>();
//    private ActionMode actionMode = null;
    private BigDecimal selectionTotal = new BigDecimal(0);
    private LegacyActionBar.ContextBarWrapper contextBar;
    private LegacyActionBar.ContextBarListener contextBarListener = new LegacyActionBar.ContextBarListener() {
        @Override
        public void onContextBarStarted() {

        }

        @Override
        public void onContextBarDismissed() {
            contextBar = null;
            clearSelection();
        }

        @Override
        public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
            switch (item.getId()) {
                case R.id.action_delete: {
                    confirmSelectionDelete();
                    return;
                }
                case R.id.action_share: {
                    Intent intent = new Intent();
                    intent.setAction(Intent.ACTION_SEND_MULTIPLE);
                    intent.setType(Receipt.DriveMimeType);

                    ArrayList<Uri> files = new ArrayList<Uri>();

                    for (Scrap scrap : selectionList) {
                        files.add(Uri.fromFile(ReceiptCoder.sharedCoder(activity).createShareableFile(scrap.targetId)));
                    }

                    intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM, files);
//                    activity.startActivity(Intent.createChooser(intent, "Share"));

                    IntentListPopover popover = new IntentListPopover(contextBar.obtainAnchorForItemWithID(R.id.action_share), intent);
                    popover.getHeader().setTitle(ReceiptActivity.titleFormattedString("Share"));
                    popover.show(activity);
                    popover.setOnDismissListener(new Popover.OnDismissListener() {
                        @Override
                        public void onDismiss() {
                            if (contextBar != null) contextBar.dismiss();
                        }
                    });

//                    contextBar.dismiss();
                    return;
                }
            }
        }
    };

    LegacyActionBar.ContextBarWrapper deleteConfirmator;
    private void confirmSelectionDelete() {
        deleteConfirmator = ((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar))
                .createActionConfirmationContextMode(
                        selectionList.size() > 1 ? getString(R.string.ConfirmSelectionMultiple, selectionList.size()) : getString(R.string.ConfirmSelectionSingle, 1),
                        getString(R.string.ActionDelete), R.drawable.ic_action_delete, new LegacyActionBar.ContextBarListener() {
                            @Override
                            public void onContextBarStarted() {
                                if (collectionView != null) {
                                    collectionView.runForEachVisibleView(new CollectionView.ViewRunnable() {
                                        @Override
                                        public void runForView(View view, Object object, int viewType) {
                                            view.setActivated(true);
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onContextBarDismissed() {
                                deleteConfirmator = null;
                                if (collectionView != null) {
                                    collectionView.runForEachVisibleView(new CollectionView.ViewRunnable() {
                                        @Override
                                        public void runForView(View view, Object object, int viewType) {
                                            view.setActivated(false);
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onLegacyActionSelected(LegacyActionBar.ActionItem item) {
                                if (item.getId() == R.id.ConfirmCancel) {
                                    if (deleteConfirmator != null) deleteConfirmator.dismiss();
                                }
                                if (item.getId() == R.id.ConfirmOK) {
                                    deleteSelection();
                                    if (contextBar != null) contextBar.dismissInstantly();
                                    deleteConfirmator.dismiss();
                                }
                            }
                        });

        deleteConfirmator.start();
    }

    public void deselect() {
        if (contextBar != null)
            contextBar.dismiss();
    }

    public void deleteSelection() {
        if (contextBar != null) {
            deleteSelection(selectionList);
            contextBar.dismiss();
        }
    }

    public void clearSelection() {
        if (contextBar != null) {
            contextBar.dismiss(); // this will call clearSelection again
            return;
        }

        for (Scrap scrap : selectionList) {
            scrap.selected = false;
        }
        selectionList.clear();
        selectionTotal = new BigDecimal(0);

        collectionView.refreshViews();
    }

    public void notifyLocaleChanged() {
        if (collectionView != null) {
            collectionView.refreshViews();
        }
    }

    protected void onSelectionChangedForScrap(Scrap scrap) {

        if (deleteConfirmator != null) {
            deleteConfirmator.dismiss();
        }

        int sizePre = selectionList.size();

        if (scrap.selected) {
            selectionList.add(scrap);
            selectionTotal = selectionTotal.add(new BigDecimal(scrap.total).movePointLeft(2));
            if (contextBar == null) {
                contextBar = ((LegacyActionBar) activity.getFragmentManager().findFragmentById(R.id.LegacyActionBar)).createContextMode(contextBarListener);

                contextBar.addItem(R.id.action_delete, getString(R.string.ItemDelete), R.drawable.ic_action_delete, false, true);
                contextBar.addItem(R.id.action_share, getString(R.string.MenuShare), R.drawable.ic_action_share_mini, false, true);

                contextBar.start();
            }
        }
        else {
            selectionList.remove(scrap);
            selectionTotal = selectionTotal.subtract(new BigDecimal(scrap.total).movePointLeft(2));
            if (contextBar != null && selectionList.size() == 0) {
                contextBar.dismiss();
            }
        }

        if (contextBar != null) {
            contextBar.setTitleAnimated(selectionList.size() + " selected", selectionList.size() - sizePre);
            contextBar.setSubtitle(ReceiptActivity.currentLocale + selectionTotal + " total");
        }
    }
	
}
