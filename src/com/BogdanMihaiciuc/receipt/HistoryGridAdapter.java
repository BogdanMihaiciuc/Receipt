package com.BogdanMihaiciuc.receipt;

import android.content.res.Configuration;
import android.util.Log;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.BogdanMihaiciuc.receipt.StatsFragment.Precision;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class HistoryGridAdapter extends BaseAdapter {
	
	final static boolean DEBUG = false;
	final static boolean DEBUG_POSITION = false;
	final static boolean DEBUG_HIDDEN_POSITION = false;
	
	// ------------------------
	// Inner classes and interfaces required for the adapter
	// ------------------------
	final static int ItemTypeCount = 3;
	final static int ItemTypeHeader = 0;
	final static int ItemTypeScrap = 1;
    final static int ItemTypeBigHeader = 3;
	final static int ItemTypeStatsFragment= 2;
    final static int ItemTypePlaceholder = 2;
	
	static interface HistoryItem {
		public int getItemType();
	}
	
	static interface HistoryRow extends HistoryItem {
		
	}
	
	static class Header implements HistoryItem, HistoryRow {
		Calendar date;
        Precision precision;
        int subtype;
        int count;
        boolean selected = false;
		public int getItemType() { return ItemTypeHeader; }
		Header() {
			date = Calendar.getInstance();
		}
        Header(Header source) {
            date = source.date;
            precision = source.precision;
            subtype = source.subtype;
        }
		Header(Calendar date) {
			this.date = date;
		}
        public boolean equals(Object object) {
            if (object instanceof Header) {
                if (((Header) object).subtype != subtype) return false;

                if (!precision.sqlPrecision.equals(((Header) object).precision.sqlPrecision)) return false;
                return precision.areDatesEqual(date, ((Header) object).date);
            }
            return false;
        }
        public String toString() {
            return precision.title.getTitle(date);
        }
	}
	
	static class Scrap {
		Calendar date;
		int itemCount;
		long total;
        String name;
		boolean exceededBudget;
		long targetId;
		// These values are runtime based
		boolean selected;
		boolean hidden;
		Scrap() {
			date = Calendar.getInstance();
			selected = false;
			hidden = false;
		}
		Scrap(Calendar date, int itemCount, long total, boolean exceededBudget, long targetId) {
			this.date = date;
			this.itemCount = itemCount;
			this.total = total;
			this.exceededBudget = exceededBudget;
			this.targetId = targetId;
			selected = false;
			hidden = false;
		}

        public boolean equals(Object object) {
            if (object instanceof Scrap)
                return targetId == ((Scrap) object).targetId;
            return false;
        }
        public String toString() {
            DateFormatSymbols dateFormat = new DateFormatSymbols();
            return dateFormat.getShortMonths()[date.get(Calendar.MONTH)] + " " + String.valueOf(date.get(Calendar.DATE)) + "; " + itemCount + " items.";
        }
	}
	
	static class ScrapCluster implements HistoryItem {
		static Scrap nullScrap = new Scrap();
		
		public static int firstIndexOfRowForColumns(int row, int columnCount) {
			return row * columnCount;
		}
		
		public ArrayList<Scrap> cluster;
		
		ScrapCluster(ArrayList<Scrap> cluster) {
			this.cluster = cluster;
		}
		
		public int getItemType() { return ItemTypeScrap; }
        public int size() {return cluster.size();}
		public int rowCountForColumns(int columnCount) {
			return (cluster.size() + columnCount - 1)/columnCount;
		}
		public ScrapRow rowForColumns(int row, int columnCount) {
			if (DEBUG) Log.d("HistoryGridAdapter", "Getting row " + row + " columnCount " + columnCount + " size " + cluster.size());
			if (firstIndexOfRowForColumns(row, columnCount) + columnCount > cluster.size())
				return new ScrapRow(cluster.subList(firstIndexOfRowForColumns(row, columnCount), cluster.size()));
			return new ScrapRow(cluster.subList(firstIndexOfRowForColumns(row, columnCount), firstIndexOfRowForColumns(row, columnCount) + columnCount));
		}
		public Scrap itemAtRowAndColumnForColumns(int row, int column, int columnCount) {
			int computedSize = firstIndexOfRowForColumns(row, columnCount) + column;
			if (computedSize > cluster.size()) {
				return nullScrap;
			}
			else
				return cluster.get(computedSize);
		}
		public Scrap itemAt(int index) {
			return cluster.get(index);
		}
	}
	
	static class ScrapRow implements HistoryItem, HistoryRow {
		public List<Scrap> row;
		ScrapRow(List<Scrap> row) {
			this.row = row;
		}
		public Scrap get(int column) {
			if (column > row.size())
				return ScrapCluster.nullScrap;
			return row.get(column);
		}
		public int rowSize() {
			return row.size();
		}
		@Override
		public int getItemType() {
			return ItemTypeScrap; 
		}
	}
	
	final static boolean includeAuto5ColumnCount = true;
	
	static class HistoryItemArray {
		public Precision precisionData;
		public int hiddenOuterIndex = -1;
		public int hiddenInnerIndex = -1;
		ArrayList<HistoryItem> innerData = new ArrayList<HistoryItem>();
		public int rowCountFor3Columns = 0;
		public int rowCountFor4Columns = 0;
		public int rowCountFor5Columns = 0;
		public int size() { return innerData.size(); }
		public HistoryItem get(int index) { return innerData.get(index); }
		public ScrapCluster clusterOfRowForColumns(int rowIndex, int columnCount) {
			int computedRow = 0;
			int innerRow;
			for (HistoryItem item : innerData) {
				innerRow = 0;
				 if (item.getItemType() == ItemTypeHeader) {
					 // If the requested row is a header, just return it
					 if (rowIndex == computedRow)
						 return null;
					 computedRow += 1;
				 }
				 else {
					 innerRow = computedRow;
					 computedRow += ((ScrapCluster)item).rowCountForColumns(columnCount);
					 // If the requested row is part of a cluster, return the appropriate row from the cluster
					 if (rowIndex >= innerRow && rowIndex < computedRow)
						 return (ScrapCluster)item;
				 }
			}
			return null;
		}
		public HistoryRow rowForColumns(int rowIndex, int columnCount) {
			int computedRow = 0;
			int innerRow;
			for (HistoryItem item : innerData) {
				innerRow = 0;
				 if (item.getItemType() == ItemTypeHeader) {
					 // If the requested row is a header, just return it
					 if (rowIndex == computedRow)
						 return (HistoryRow) item;
					 computedRow += 1;
				 }
				 else {
					 innerRow = computedRow;
					 computedRow += ((ScrapCluster)item).rowCountForColumns(columnCount);
					 // If the requested row is part of a cluster, return the appropriate row from the cluster
					 if (rowIndex >= innerRow && rowIndex < computedRow) {
						 return ((ScrapCluster)item).rowForColumns(rowIndex - innerRow, columnCount);
					 }
				 }
			}
			return (HistoryRow) innerData.get(0);
		}
		public void regenerateRowCountCache() {
			rowCountFor3Columns = 0;
			rowCountFor4Columns = 0;
			rowCountFor5Columns = 0;
			int headerCount = 0;
			for (HistoryItem item : innerData) {
				if (item.getItemType() == ItemTypeHeader)
					headerCount += 1;
				else {
					rowCountFor3Columns += ((ScrapCluster)item).rowCountForColumns(3);
					rowCountFor4Columns += ((ScrapCluster)item).rowCountForColumns(4);
					rowCountFor5Columns += ((ScrapCluster)item).rowCountForColumns(5);
				}
			}
			rowCountFor3Columns += headerCount;
			rowCountFor4Columns += headerCount;
			rowCountFor5Columns += headerCount;
		}
		public int totalRowCountForColumns(int columnCount) {
			if (columnCount == 3) return rowCountFor3Columns;
			if (columnCount == 4) return rowCountFor4Columns;
			if (includeAuto5ColumnCount) if (columnCount == 5) return rowCountFor5Columns;
			int rowCount = 0;
			for (HistoryItem item : innerData) {
				if (item.getItemType() == ItemTypeHeader)
					rowCount += 1;
				else
					rowCount += ((ScrapCluster)item).rowCountForColumns(columnCount);
			}
			return rowCount;
		}
		public boolean add(HistoryItem object) {
			if (object.getItemType() == ItemTypeHeader) {
				rowCountFor3Columns += 1;
				rowCountFor4Columns += 1;
				if (includeAuto5ColumnCount) rowCountFor5Columns += 1;
			}
			else {
				rowCountFor3Columns += ((ScrapCluster)object).rowCountForColumns(3);
				rowCountFor4Columns += ((ScrapCluster)object).rowCountForColumns(4);
				if (includeAuto5ColumnCount) rowCountFor5Columns += ((ScrapCluster)object).rowCountForColumns(5);
			}
			return innerData.add(object);
		}
		public void add(int index, HistoryItem object) {
			if (object.getItemType() == ItemTypeHeader) {
				rowCountFor3Columns += 1;
				rowCountFor4Columns += 1;
				if (includeAuto5ColumnCount) rowCountFor5Columns += 1;
			}
			else {
				rowCountFor3Columns += ((ScrapCluster)object).rowCountForColumns(3);
				rowCountFor4Columns += ((ScrapCluster)object).rowCountForColumns(4);
				if (includeAuto5ColumnCount) rowCountFor5Columns += ((ScrapCluster)object).rowCountForColumns(5);
			}
			innerData.add(index, object);
		}
		public void remove(int index) {
			HistoryItem object = innerData.get(index);
			innerData.remove(index);
			if (object.getItemType() == ItemTypeHeader) {
				rowCountFor3Columns -= 1;
				rowCountFor4Columns -= 1;
				if (includeAuto5ColumnCount) rowCountFor5Columns -= 1;
			}
			else {
				rowCountFor3Columns -= ((ScrapCluster)object).rowCountForColumns(3);
				rowCountFor4Columns -= ((ScrapCluster)object).rowCountForColumns(4);
				if (includeAuto5ColumnCount) rowCountFor5Columns -= ((ScrapCluster)object).rowCountForColumns(5);
			}
		}
		public void remove(HistoryItem object) {
			if (!innerData.contains(object)) return;
			innerData.remove(object);
			if (object.getItemType() == ItemTypeHeader) {
				rowCountFor3Columns -= 1;
				rowCountFor4Columns -= 1;
				if (includeAuto5ColumnCount) rowCountFor5Columns -= 1;
			}
			else {
				rowCountFor3Columns -= ((ScrapCluster)object).rowCountForColumns(3);
				rowCountFor4Columns -= ((ScrapCluster)object).rowCountForColumns(4);
				if (includeAuto5ColumnCount) rowCountFor5Columns -= ((ScrapCluster)object).rowCountForColumns(5);
			}
		}
        public Scrap findScrapWithUID(long uid) {
            for (HistoryItem item : innerData) {
                if (item.getClass() == ScrapCluster.class) {
                    for (Scrap scrap : ((ScrapCluster) item).cluster) {
                        if (scrap.targetId == uid) return scrap;
                    }
                }
            }
            return null;
        }
	}

	// ------------------------
	// Selection and action mode
	// ------------------------

	// selection handlers
	private ArrayList<Scrap> selectionList;
	private ActionMode actionMode = null;
	private boolean multipleSelection;
	private ActionMode.Callback actionModeCallback = new ActionMode.Callback() {

		private boolean multiMode;

	    @Override
	    public boolean onCreateActionMode(ActionMode mode, Menu menu) {
	    	multiMode = false;
	        // Inflate a menu resource providing context menu items
	        MenuInflater inflater = mode.getMenuInflater();
	        inflater.inflate(R.menu.history_selection, menu);
	        return true;
	    }

	    @Override
	    public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
	    	if (multiMode != multipleSelection) {
	    		multiMode = multipleSelection;
	    	}
	        return false; // Return false if nothing is done
	    }

	    @Override
	    public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
	        switch (item.getItemId()) {
	        	case R.id.action_delete: {
//	        		activity.confirmSelectionDelete();
	        	}
	        		return true;
	            default:
	                return false;
	        }
	    }

	    @Override
	    public void onDestroyActionMode(ActionMode mode) {
	    	clearSelection();
	    }
	};

	public void deselect() {
		if (actionMode != null)
			actionMode.finish();
	}

	public void deleteSelection() {
		if (actionMode != null) {
    		activity.deleteSelection(selectionList);
    		actionMode.finish();
		}
	}
	
	// ------------------------
	// The actual adapter
	// ------------------------
	
	static class HistoryItemViewHolder {
		int viewType;
	}
	
	static class HeaderViewHolder extends HistoryItemViewHolder {
		TextView title;
	}
	
	static class ScrapViewHolder {
		TextView date;
		TextView itemCount;
		TextView total;
		RelativeLayout scrapBase;
		
		int currentRow;
		int currentColumn;
	}
	
	static class RowViewHolder extends HistoryItemViewHolder{
		int scrapsUsed;
		ScrapViewHolder holders[];
		RowViewHolder (ScrapViewHolder holders[]) {
			this.holders = holders;
		}
	}
	
	final static int PhonePortraitColumnCount = 3;
	final static int PhoneLandscapeColumnCount = 4;
	final static int GalaxyNexusLandscapeColumnCount = 5;
	final static int TabletPortraitColumnCount = 3;
	final static int TabletLandscapeColumnCount = 4;
    final static int LargeTabletPortraitColumnCount = 4;
    final static int LargeTabletLandscapeColumnCount = 5;
	
	private HistoryItemArray data;
	private HistoryActivity activity;
	private int columnCount;
	private boolean hasExtraItem = false;
	private ListView list;
	private View hiddenScrap = null;
	private int locationOfHiddenItemInHistoryItemArray = -1;
	private int locationOfHiddenItemInScrapCluster = -1;
	private int rowOfHiddenItemInList = -1;
	private int columnOfHiddenItemInList = -1;
	private boolean listenersDisabled = false;
	private long idOfHiddenScrap = -1;
	
	HistoryGridAdapter(HistoryActivity context) {
		this.data = new HistoryItemArray();
		selectionList = new ArrayList<Scrap>();
		attach(context);
	}
	
	HistoryGridAdapter(HistoryActivity context, HistoryItemArray data) {
		this.data = new HistoryItemArray();
		selectionList = new ArrayList<Scrap>();
		attach(context);
		this.data = data;
	}
	
	public void add(HistoryItem item) {
		data.add(item);
	}
	
	public void adaptData(HistoryItemArray newData) {
		data = newData;
		this.locationOfHiddenItemInHistoryItemArray = data.hiddenOuterIndex;
		this.locationOfHiddenItemInScrapCluster = data.hiddenInnerIndex;
		if (locationOfHiddenItemInHistoryItemArray == -1)
			idOfHiddenScrap = -1;
		notifyDataSetChanged();
	}
	
	public boolean isHiddenScrapAvailable() {
		return idOfHiddenScrap != -1;
	}
	
	public void setList(ListView list) {
		this.list = list;
	}
	
	public void detach() {
		hasExtraItem = false;
		list = null;
		activity = null;
		hiddenScrap = null;
		rowOfHiddenItemInList = -1;
		columnOfHiddenItemInList = -1;
	}
	
	public void removeScrapView() {
		ScrapCluster cluster = (ScrapCluster)data.innerData.get(locationOfHiddenItemInHistoryItemArray);
		cluster.cluster.remove(locationOfHiddenItemInScrapCluster);
		hiddenScrap = null;
		locationOfHiddenItemInHistoryItemArray = -1;
		locationOfHiddenItemInScrapCluster = -1;
		rowOfHiddenItemInList = -1;
		columnOfHiddenItemInList = -1;
		data.regenerateRowCountCache();
		notifyDataSetChanged();
	}
	
	// Update the configuration
	public void attach(HistoryActivity context) {
		activity = context;
		if (selectionList.size() > 0) {
			actionMode = activity.startActionMode(actionModeCallback);
			actionMode.setTitle(selectionList.size() + " selected");
		}
		Configuration config = context.getResources().getConfiguration();
		int swdp = config.smallestScreenWidthDp;
		if (swdp < 600) {
			if (config.orientation == Configuration.ORIENTATION_LANDSCAPE) {
				columnCount = PhoneLandscapeColumnCount;
				if (swdp > 359)
					columnCount = GalaxyNexusLandscapeColumnCount;
			}
			else 
				columnCount = PhonePortraitColumnCount;
		}
		else {
			if (config.orientation == Configuration.ORIENTATION_LANDSCAPE)
				columnCount = TabletLandscapeColumnCount;
			else  {
				columnCount = TabletPortraitColumnCount;
				//hasExtraItem = true;
			}
		}
	}
	
	public void setListenersDisabled(boolean disabled) {
		listenersDisabled = disabled;
	}
	
	private OnClickListener scrapClickListener = new OnClickListener (){
		@Override
		public void onClick(View view) {
			if (listenersDisabled) return;
			if (selectionList.size() > 0)
				toggleSelectionForView(view);
			else {

				listenersDisabled = true;
				
				//// Pass the view and target id to the activity to hide and bring up the relevant window
				
				int row = list.getFirstVisiblePosition() + list.indexOfChild((View)view.getParent().getParent());
				int column = ((ViewGroup)view.getParent().getParent()).indexOfChild((View)view.getParent());
				if (DEBUG_POSITION) Log.d("HistoryGridAdapter", "View is at row " + row + " and column " + column);
				
				ScrapRow scrapRow = (ScrapRow) data.rowForColumns(row, columnCount);
				ScrapCluster containingCluster = data.clusterOfRowForColumns(row, columnCount);
				Scrap scrapToShow = scrapRow.get(column);
				// Hide scraps on phones too now!
				scrapToShow.hidden = true;
				
				hiddenScrap = view;
				
				locationOfHiddenItemInScrapCluster = containingCluster.cluster.indexOf(scrapToShow);
				locationOfHiddenItemInHistoryItemArray = data.innerData.indexOf(containingCluster);
				rowOfHiddenItemInList = row;
				columnOfHiddenItemInList = column;
				idOfHiddenScrap = scrapToShow.targetId;
//				activity.showTargetIdFromView(scrapToShow.targetId, view); // TODO
			}
		}
	};
	
	public long idOfHiddenScrap() {
		return idOfHiddenScrap;
	}
	
	public void resetHiddenScrap() {
		idOfHiddenScrap = -1;
	}
	
	public void setCoordinatesOfHiddenScrap(int historyArrayIndex, int scrapClusterIndex) {
		locationOfHiddenItemInHistoryItemArray = historyArrayIndex;
		locationOfHiddenItemInScrapCluster = scrapClusterIndex;
	}
	
	private OnLongClickListener scrapLongClickListener = new OnLongClickListener (){
		@Override
		public boolean onLongClick(View view) {
			if (listenersDisabled) return true;
			toggleSelectionForView(view);
			return true;
		}
	};
	
	public View getHiddenScrapOrHideIfOffscreen(boolean alwaysHide) {
		((ScrapCluster)data.innerData.get(locationOfHiddenItemInHistoryItemArray))
				.cluster.get(locationOfHiddenItemInScrapCluster).hidden = false;
		if (rowOfHiddenItemInList != -1 && columnOfHiddenItemInList != -1) {
			if (list.getFirstVisiblePosition() <= rowOfHiddenItemInList && list.getLastVisiblePosition() >= rowOfHiddenItemInList) {
				if (alwaysHide) hiddenScrap.setVisibility(View.VISIBLE);
				if (DEBUG_HIDDEN_POSITION) if (hiddenScrap == null) {
					Log.d("HistoryGridAdapter", "First visible: " + list.getFirstVisiblePosition() + " item is at but null!!:" + rowOfHiddenItemInList + 
																			" Last visible: " + list.getLastVisiblePosition());
				}
				RowViewHolder holder = (RowViewHolder) list.getChildAt(rowOfHiddenItemInList-list.getFirstVisiblePosition()).getTag();
				return holder.holders[columnOfHiddenItemInList].scrapBase;
				//return hiddenScrap; // This fails on phones, unfortunately
			}
		}
		else {
			notifyDataSetChanged();
			if (DEBUG_HIDDEN_POSITION) Log.d("HistoryGridAdapter", "First visible: " + list.getFirstVisiblePosition() + " item:" + rowOfHiddenItemInList + 
					" Last visible: " + list.getLastVisiblePosition());
		}
		return null;
	}
	
	final static int HiddenViewAboveScreen =  -1;
	final static int HiddenViewBelowScreen = 1;
	
	public int getLocationOfOffscreenHiddenScrap() {
		if (rowOfHiddenItemInList == -1) {
			int dataSize = data.size();
			boolean keepGoing = true;
			for (int i = 0; i < dataSize && keepGoing; i++) {
				if (data.rowForColumns(i, columnCount).getItemType() != ItemTypeHeader) {
					for (Scrap scrap : ((ScrapRow)data.rowForColumns(i, columnCount)).row) {
						if (DEBUG_HIDDEN_POSITION) Log.d("HistoryGridAdapter", "Scrap " + scrap + " hidden is " + scrap.hidden);
						if (scrap.hidden == true) {
							rowOfHiddenItemInList = i;
							keepGoing = false;
							break;
						}
					}
				}
			}
		}
		if (DEBUG_HIDDEN_POSITION) Log.d("HistoryGridAdapter", "First visible: " + list.getFirstVisiblePosition() + " item:" + rowOfHiddenItemInList + 
				" Last visible: " + list.getLastVisiblePosition());
		if (rowOfHiddenItemInList < list.getFirstVisiblePosition()) {
			return HiddenViewAboveScreen;
		}
		return HiddenViewBelowScreen;
	}
	
	public Scrap getScrapOfView(View view) {
		int row = list.getFirstVisiblePosition() + list.indexOfChild((View)view.getParent().getParent());
		int column = ((ViewGroup)view.getParent().getParent()).indexOfChild((View)view.getParent());
		if (DEBUG_POSITION) Log.d("HistoryGridAdapter", "View is at row " + row + " and column " + column);
		ScrapRow scrapRow = (ScrapRow) data.rowForColumns(row, columnCount);
		return scrapRow.get(column);
	}
	
	public void toggleSelectionForView(View view) {
		
		int row = list.getFirstVisiblePosition() + list.indexOfChild((View)view.getParent().getParent());
		int column = ((ViewGroup)view.getParent().getParent()).indexOfChild((View)view.getParent());
		if (DEBUG_POSITION) Log.d("HistoryGridAdapter", "View is at row " + row + " and column " + column);
		ScrapRow scrapRow = (ScrapRow) data.rowForColumns(row, columnCount);
		scrapRow.get(column).selected = !view.isSelected();
		if (!view.isSelected()) {
			if (selectionList.size() == 0 || actionMode == null) {
				actionMode = activity.startActionMode(actionModeCallback);
			}
			selectionList.add(scrapRow.get(column));
		}
		else {
			selectionList.remove(scrapRow.get(column));
			if (selectionList.size() == 0)
				actionMode.finish();
		}
		if (actionMode != null) actionMode.setTitle(selectionList.size() + " selected");
		view.setSelected(!view.isSelected());
		
	}

	public void clearSelection() {
		for (Scrap scrap : selectionList) {
			scrap.selected = false;
		}
		selectionList.clear();
		actionMode = null;
		notifyDataSetChanged();
	}

	@Override
	public int getCount() {
		if (hasExtraItem)
			return data.totalRowCountForColumns(columnCount) + 1;
		return data.totalRowCountForColumns(columnCount);
	}

	@Override
	public Object getItem(int position) {
		return data.rowForColumns(position, columnCount);
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	@Override
	public int getItemViewType(int position) {
		return data.rowForColumns(position, columnCount).getItemType();
	}
	
	@Override
	public int getViewTypeCount() {
		return ItemTypeCount;
	}
	
	@Override
	public boolean isEnabled(int position) {
		return false;
	}

	@Override
	public View getView(int index, View convertView, ViewGroup root) {
		if (DEBUG) Log.d("HistoryGridAdapter", "Data has " + data.size() + " items.");
		if (DEBUG_HIDDEN_POSITION) Log.d("HistoryGridAdapter", "getView(int, View, ViewGroup) called!");
		View returnedView;
		if (convertView != null) {
			returnedView = convertView;
			HistoryItemViewHolder baseHolder = (HistoryItemViewHolder)returnedView.getTag();
			if (baseHolder.viewType == ItemTypeHeader) {
				HeaderViewHolder holder = (HeaderViewHolder)baseHolder;
				Header header = (Header)data.rowForColumns(index, columnCount);
				holder.title.setText(data.precisionData.sectionTitle.getTitle(activity, header.date));
				/*
				if (header.date.get(Calendar.MONTH) == Calendar.getInstance().get(Calendar.MONTH)
						&& header.date.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)) {
					holder.title.setText(activity.getResources().getString(R.string.HeaderThisMonth));
				}
				else {
						DateFormatSymbols dateFormat = new DateFormatSymbols();
						holder.title.setText(dateFormat.getMonths()[header.date.get(Calendar.MONTH)]);
				}*/
			}
			else {
				RowViewHolder holder = (RowViewHolder)baseHolder;
				ScrapViewHolder holders[] = holder.holders;
				ScrapRow row = (ScrapRow)data.rowForColumns(index, columnCount);
				holder.viewType = ItemTypeScrap;
				holder.scrapsUsed = row.rowSize();
				DateFormatSymbols dateFormat = new DateFormatSymbols();
				for (int i = 0; i < holder.scrapsUsed; i++) {
					String dateName = dateFormat.getShortMonths()[row.row.get(i).date.get(Calendar.MONTH)] + " " + String.valueOf(row.row.get(i).date.get(Calendar.DATE));
					holders[i].date.setText(dateName);
					holders[i].itemCount.setText(String.format(activity.getResources().getString(R.string.ScrapItems), row.row.get(i).itemCount));
					holders[i].total.setText(ReceiptActivity.shortFormattedTotalWithCutoff(activity, row.row.get(i).total, 3));
//					if (ReceiptActivity.currentLocale.length() > 1) {
//						SpannableStringBuilder builder = new SpannableStringBuilder();
//						builder.append(String.format(activity.getResources().getString(R.string.ScrapPrice), ReceiptActivity.currentLocale, row.row.get(i).total/100));
//						builder.setSpan(new ForegroundColorSpan(activity.getResources().getColor(R.color.crossedoff_text_colors)), 
//								0, ReceiptActivity.currentLocale.length(), 
//								SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
//						builder.setSpan(new RelativeSizeSpan(0.66f), 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
//						holders[i].total.setText(builder);
//					}
//					else
//						holders[i].total.setText(String.format(activity.getResources().getString(R.string.ScrapPrice), ReceiptActivity.currentLocale, row.row.get(i).total/100));
					if (DEBUG_HIDDEN_POSITION) Log.d("HistoryGridAdapter", "ActionItem at: " + index + ", " + i + " is hidden: " + row.row.get(i).hidden);
					if (row.row.get(i).hidden) {
						if (DEBUG_HIDDEN_POSITION) Log.d("HistoryGridAdapter", "Position of hidden item is: " + index + ", " + i);
						holders[i].scrapBase.setVisibility(View.INVISIBLE);
						rowOfHiddenItemInList = index;
						columnOfHiddenItemInList = i;
						hiddenScrap = holders[i].scrapBase;
					}
					else
						holders[i].scrapBase.setVisibility(View.VISIBLE);
					holders[i].scrapBase.setOnClickListener(scrapClickListener);
					holders[i].scrapBase.setOnLongClickListener(scrapLongClickListener);
					if (DEBUG_POSITION) Log.d("HistoryGridAdapter", "Row " + index + " column " + i + " selected is " + row.row.get(i).selected + ".");
					holders[i].scrapBase.setSelected(row.row.get(i).selected);
					holders[i].currentRow = index;
					holders[i].currentColumn = i;
				}
				for (int i = holder.scrapsUsed; i < columnCount; i++) {
					holders[i].scrapBase.setVisibility(View.GONE);
				}
			}
		}
		else {
			LayoutInflater inflater = activity.getLayoutInflater();
			if (getItemViewType(index) == ItemTypeHeader) {
				returnedView = inflater.inflate(R.layout.history_header, null);
				HeaderViewHolder holder = new HeaderViewHolder();
				holder.viewType = ItemTypeHeader;
				holder.title = (TextView)returnedView.findViewById(R.id.HeaderTitle);
				returnedView.setTag(holder);
				
				Header header = (Header)data.rowForColumns(index, columnCount);
				holder.title.setText(data.precisionData.sectionTitle.getTitle(activity, header.date));
				
			}
			else {
				returnedView = inflater.inflate(R.layout.history_row, null);
				ScrapViewHolder holders[] = new ScrapViewHolder[columnCount];
				for (int i = 0; i < columnCount; i++) holders[i] = new ScrapViewHolder();
				holders[0].date = (TextView)returnedView.findViewById(R.id.HistoryDate1);
				holders[0].itemCount = (TextView)returnedView.findViewById(R.id.HistoryItems1);
				holders[0].total = (TextView)returnedView.findViewById(R.id.HistoryPrice1);
				holders[0].scrapBase = (RelativeLayout)returnedView.findViewById(R.id.HistoryScrap1);
				holders[1].date = (TextView)returnedView.findViewById(R.id.HistoryDate2);
				holders[1].itemCount = (TextView)returnedView.findViewById(R.id.HistoryItems2);
				holders[1].total = (TextView)returnedView.findViewById(R.id.HistoryPrice2);
				holders[1].scrapBase = (RelativeLayout)returnedView.findViewById(R.id.HistoryScrap2);
				holders[2].date = (TextView)returnedView.findViewById(R.id.HistoryDate3);
				holders[2].itemCount = (TextView)returnedView.findViewById(R.id.HistoryItems3);
				holders[2].total = (TextView)returnedView.findViewById(R.id.HistoryPrice3);
				holders[2].scrapBase = (RelativeLayout)returnedView.findViewById(R.id.HistoryScrap3);
				if (columnCount > 3) {
					holders[3].date = (TextView)returnedView.findViewById(R.id.HistoryDate4);
					holders[3].itemCount = (TextView)returnedView.findViewById(R.id.HistoryItems4);
					holders[3].total = (TextView)returnedView.findViewById(R.id.HistoryPrice4);
					holders[3].scrapBase = (RelativeLayout)returnedView.findViewById(R.id.HistoryScrap4);
				}
				if (columnCount > 4) {
					holders[4].date = (TextView)returnedView.findViewById(R.id.HistoryDate5);
					holders[4].itemCount = (TextView)returnedView.findViewById(R.id.HistoryItems5);
					holders[4].total = (TextView)returnedView.findViewById(R.id.HistoryPrice5);
					holders[4].scrapBase = (RelativeLayout)returnedView.findViewById(R.id.HistoryScrap5);
				}
				
				RowViewHolder holder = new RowViewHolder(holders);
				ScrapRow row = (ScrapRow)data.rowForColumns(index, columnCount);
				holder.viewType = ItemTypeScrap;
				holder.scrapsUsed = row.rowSize();
				DateFormatSymbols dateFormat = new DateFormatSymbols();
				for (int i = 0; i < holder.scrapsUsed; i++) {
					String dateName = dateFormat.getShortMonths()[row.row.get(i).date.get(Calendar.MONTH)] + " " + String.valueOf(row.row.get(i).date.get(Calendar.DATE));
					holders[i].date.setText(dateName);
					holders[i].itemCount.setText(String.format(activity.getResources().getString(R.string.ScrapItems), row.row.get(i).itemCount));
					holders[i].total.setText(ReceiptActivity.shortFormattedTotalWithCutoff(activity, row.row.get(i).total, 3));
//					if (ReceiptActivity.currentLocale.length() > 1) {
//						SpannableStringBuilder builder = new SpannableStringBuilder();
//						builder.append(String.format(activity.getResources().getString(R.string.ScrapPrice), ReceiptActivity.currentLocale, row.row.get(i).total/100));
//						builder.setSpan(new ForegroundColorSpan(activity.getResources().getColor(R.color.crossedoff_text_colors)), 
//								0, ReceiptActivity.currentLocale.length(), 
//								SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
//						builder.setSpan(new RelativeSizeSpan(0.66f), 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
//						holders[i].total.setText(builder);
//					}
//					else
//						holders[i].total.setText(String.format(activity.getResources().getString(R.string.ScrapPrice), ReceiptActivity.currentLocale, row.row.get(i).total/100));
					holders[i].scrapBase.setOnClickListener(scrapClickListener);
					holders[i].scrapBase.setOnLongClickListener(scrapLongClickListener);
					holders[i].scrapBase.setTag(holders[i]);
					holders[i].currentRow = index;
					holders[i].currentColumn = i;
					if (DEBUG_HIDDEN_POSITION) Log.d("HistoryGridAdapter", "ActionItem at: " + index + ", " + i + " is hidden: " + row.row.get(i).hidden);
					if (row.row.get(i).hidden) {
						if (DEBUG_HIDDEN_POSITION) Log.d("HistoryGridAdapter", "Position of hidden item is: " + index + ", " + i);
						holders[i].scrapBase.setVisibility(View.INVISIBLE);
						rowOfHiddenItemInList = index;
						columnOfHiddenItemInList = i;
						hiddenScrap = holders[i].scrapBase;
					}
					else
						holders[i].scrapBase.setVisibility(View.VISIBLE);
					if (row.row.get(i).selected)
						holders[i].scrapBase.setSelected(true);
				}
				for (int i = holder.scrapsUsed; i < columnCount; i++) {
					holders[i].scrapBase.setVisibility(View.GONE);
				}
				returnedView.setTag(holder);
			}
		}
		return returnedView;
	}
	
	@Override
	public void notifyDataSetChanged() {
		super.notifyDataSetChanged();
		if (list != null)
			list.smoothScrollBy(1, 1);
	}
	
	public Section makeSection(Header header, int rowPosition) {
		Section section = new Section();
		section.header = header;
		section.rowPosition = rowPosition;
		return section;
	}
	
	class Section {
		Header header;
		int rowPosition;
		public String toString() {
			return data.precisionData.sectionTitle.getTitle(activity, header.date).toString();
		}
	}

	public Object[] getSections() {
		ArrayList<Section> sections = new ArrayList<Section>();
		int dataSize = data.totalRowCountForColumns(columnCount);
		for (int i = 0; i < dataSize; i++) {
			if (data.rowForColumns(i, columnCount).getItemType() == ItemTypeHeader) {
				Section section = makeSection((Header)data.rowForColumns(i, columnCount), i);
				sections.add(section);
			}
		}
		return sections.toArray();
	}

	public int getPositionForSection(int position) {
		if (position >= 0)
			return ((Section)getSections()[position]).rowPosition;
		else
			return 0;
	}

	public int getSectionForPosition(int position) {
		Object[] sections = getSections();
		for (int i = 0; i < sections.length; i++) {
			if (position > ((Section)sections[i]).rowPosition)
				return i - 1;
		}
		return 0;
	}

}
