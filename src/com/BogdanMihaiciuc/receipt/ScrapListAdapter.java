package com.BogdanMihaiciuc.receipt;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.support.v4.util.LongSparseArray;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.TypefaceSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.BogdanMihaiciuc.util.TagView;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Item;
import static com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;

@Deprecated
public class ScrapListAdapter extends BaseAdapter implements TagExpander.OnTagDeletedListener {

    final static String TAG = ScrapListAdapter.class.toString();
	
	final static boolean DEBUG = false;
    final static boolean DEBUG_COMPATIBILITY = false;

    static class ItemViewHolder {
		TextView title;
		TextView qty;
		TextView price;
        TagView tags;
		long id;

        boolean hasExpandedTags;
	}

    static class DisplayItem {
        String title;
        long qty;
        long price;
        ArrayList<ItemCollectionFragment.Tag> tags = new ArrayList<ItemCollectionFragment.Tag>();
    }
	
	static class InfoViewHolder {
		TextView title;
		TextView detail;
	}
		
	private boolean[] selectionList;
	private long[] idList;
	private int selectionCount;
	
	final static int ScrapDetailCount = 7;
	
	final static int UnixCheckoutTime = 0;
	final static int ItemTypeCount = 1;
	final static int TotalItemCount = 2;
	final static int AssignedBudget = 3;
	final static int RemainingBudget = 4;
	final static int Tax = 5;
	final static int Subtotal = 6;

    final static int ModeScrapContent = 0;
    final static int ModeScrapInfo = 1;
    final static int ModeCount = 2;
	
	final static int InfoTitles[] = {R.string.CheckoutDate, R.string.CheckoutTime, R.string.ItemCount, R.string.BudgetAssigned, R.string.BudgetRemaining, R.string.Tax, R.string.Subtotal};
	private long[] details;
	
	private String searchTerms;
	private Cursor queryResult;
    private LongSparseArray<ArrayList<Tag>> tags;
	private Activity activity;
	private ViewerFragment fragment;

    private TagExpander currentExpander;

    private long tagExpanderTarget = Long.MIN_VALUE;
	
	private int mode = 0;
	
	public ScrapListAdapter(Activity context, ViewerFragment fragment) {
		activity = context;
		this.fragment = fragment;
	}
	
	public void setSearchTerms(String terms) {
		searchTerms = terms;
	}
	
	public void toggleModeAndReloadAfter(boolean reload) {
		if (mode == ModeScrapContent)
			setModeAndReloadAfter(ModeScrapInfo, reload);
		else
			setModeAndReloadAfter(ModeScrapContent, reload);
	}
	
	public void setModeAndReloadAfter(int mode, boolean reload) {
		this.mode = mode;
		if (reload) notifyDataSetChanged();
	}
	
	public void setInfoDetails(long[] details) {
		if (ViewerFragment.DEBUG) Log.d("ScrapListAdapter", "Set info details!");
		this.details = details;
		if (queryResult != null) {
			if (queryResult.getCount() != 0) {
				queryResult.moveToPosition(-1);
				details[TotalItemCount] = 0;
				while (queryResult.moveToNext()) {
					long qty = queryResult.getLong(Receipt.DBQtyKeyIndex);
					if (qty == 0)
						qty = 100;
					details[TotalItemCount] += qty;
				}
			}
		}
	}
	
	public long[] getInfoDetails() {
		return details;
	}
	
	public boolean[] getSelectionList() {
		return selectionList;
	}
	
	public long[] getIdList() {
		return idList;
	}
	
	public int getSelectionCount() {
		return selectionCount;
	}
	
	public void clearSelection(boolean notify) {
		selectionCount = 0;
		selectionList = new boolean[selectionList.length];
		if (notify) notifyDataSetChanged();
	}
	
	public void setLists(boolean[] selectionList, long[] idList) {
		this.selectionList = selectionList;
		this.idList = idList;
	}
	
	public void setSelectionCount(int selectionCount) {
		this.selectionCount = selectionCount;
	}

    public long getTagExpanderTarget() {
        return tagExpanderTarget;
    }

    public void setTagExpanderTarget(long tagExpanderTarget) {
        this.tagExpanderTarget = tagExpanderTarget;
    }

    public void saveTagExpanderStaticContext() {
        if (currentExpander != null) {
            currentExpander.saveStaticContext();
        }
    }

    public void onDestroyView() {
        if (currentExpander != null) {
            currentExpander.onDestroyView();
        }
    }

    public boolean handleBackPressed() {
        if (tagExpanderTarget >= 0 && currentExpander == null) {
            tagExpanderTarget = Long.MIN_VALUE;
            return true;
        }
        if (currentExpander != null) {
            currentExpander.compact();
            return true;
        }
        return false;
    }
	
	public void setCursor(Cursor cursor, SQLiteDatabase db) {
		if (queryResult != null) queryResult.close();
		queryResult = cursor;

        // Load the tags for each item in a sparsearray
        tags = new LongSparseArray<ArrayList<ItemCollectionFragment.Tag>>();
        if (cursor.getCount() > 0) {
            cursor.moveToFirst();
            do {
                ArrayList<Tag> itemTags = new ArrayList<Tag>();
                tags.put(cursor.getLong(Receipt.DBUIDKeyIndex), itemTags);

                Cursor connections = null;
                try {
                    connections = db.query(false, Receipt.DBTagConnectionsTable, Receipt.DBAllTagConnectionColumns, Receipt.DBItemConnectionUIDKey + " = " + cursor.getLong(Receipt.DBUIDKeyIndex),
                            null, null, null, null, null);

                    if (connections.getCount() > 0) {
                        connections.moveToFirst();
                        do {
                            Tag tag = TagStorage.findTagWithUID(connections.getInt(Receipt.DBTagConnectionUIDKeyIndex));
                            if (tag != null) {
                                // Required in order to obtain the correct sorting order
                                TagStorage.addTagToArray(tag, itemTags);
//                                itemTags.add(tag);
                            }
                        } while (connections.moveToNext());
                    }

                }
                catch (SQLiteException e) {
                    e.printStackTrace();
                }
                finally {
                    if (connections != null)
                        connections.close();
                }

            } while (cursor.moveToNext());
        }
		
		if (selectionList == null) {
			selectionList = new boolean[cursor.getCount()];
			selectionCount = 0;
		}
		if (idList == null) {
			idList = new long[cursor.getCount()];
			cursor.move(-1);
			int i = 0;
			while (cursor.moveToNext()) {
				idList[i] = cursor.getLong(Receipt.DBUIDKeyIndex);
				i++;
			}
		}
		
		notifyDataSetChanged();
	}
	
	public Cursor getCursor() {
		return queryResult;
	}
	
	public void releaseCursor() {
		if (queryResult != null) queryResult.close();
		queryResult = null;
	}
	
	@Override
	public int getCount() {
		if (DEBUG) Log.d("ScrapListAdapter", "Cursor has " + queryResult.getCount() + " items.");
		if (mode == ModeScrapContent)
			return queryResult.getCount();
		else
			return ScrapDetailCount;
	}

	@Override
	public Object getItem(int arg0) {
		return null;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}
	
	@Override
	public int getViewTypeCount() {
		return ModeCount;
	}
	
	@Override
	public int getItemViewType(int position) {
		return mode;
	}

	@Override
	public View getView(final int position, View convertView, ViewGroup root) {
		if (mode == ModeScrapContent)
			return getContentView(position, convertView, root);
		else
			return getInfoView(position, convertView, root);
	}
	
	@Override
	public boolean areAllItemsEnabled() {
		return mode == ModeScrapContent;
	}
	
	@Override
	public boolean isEnabled(int position) {
		return mode == ModeScrapContent;
	}

    public static class DatabaseProxyItem extends Item {
        long targetUID;

        protected void addTagToDatabase(final Tag tag) {
            new Thread() {
                public void run() {
                    synchronized (Receipt.DatabaseLock) {
                        SQLiteDatabase database = Receipt.DBHelper.getWritableDatabase();
                        ContentValues values = new ContentValues(2);
                        values.put(Receipt.DBTagConnectionUIDKey, tag.tagUID);
                        values.put(Receipt.DBItemConnectionUIDKey, targetUID);
                        database.insert(Receipt.DBTagConnectionsTable, null, values);
                    }
                }
            }.start();
        }

        public void addTagToIndex(final Tag tag, final int index) {
            super.addTagToIndex(tag, index);
            addTagToDatabase(tag);
            // No need to notify the adapter; the TagExpander takes care of updating the relevant view
        }

        public void addTag(final Tag tag) {
            super.addTag(tag);
            // The super implementation will ultimately call addTagToIndex;
//            addTagToDatabase(tag);
        }

        public void removeTagAtIndex(int index) {
            final Tag tag = tags.get(index);
            super.removeTagAtIndex(index);
            new Thread() {
                public void run() {
                    synchronized (Receipt.DatabaseLock) {
                        SQLiteDatabase database = Receipt.DBHelper.getWritableDatabase();
                        database.delete(Receipt.DBTagConnectionsTable,
                                Receipt.DBItemConnectionUIDKey + " = " + targetUID + " and " +
                                        Receipt.DBTagConnectionUIDKey + " = " + tag.tagUID,
                                null);
                    }
                }
            }.start();

            // No need to notify the adapter; the TagExpander takes care of updating the relevant view
        }

        public void removeTag(Tag tag) {
            int index = tags.indexOf(tag);
            if (index != -1) {
                removeTagAtIndex(index);
            }
        }

        public void removeTags(final ArrayList<Tag> tags) {
            this.tags.removeAll(tags);
            new Thread() {
                public void run() {
                    synchronized (Receipt.DatabaseLock) {
                        SQLiteDatabase database = Receipt.DBHelper.getWritableDatabase();
                        for (Tag tag : tags) {
                            database.delete(Receipt.DBTagConnectionsTable,
                                    Receipt.DBItemConnectionUIDKey + " = " + targetUID + " and " +
                                            Receipt.DBTagConnectionUIDKey + " = " + tag.tagUID,
                                    null);
                        }
                    }
                }
            }.start();
        }

    }

    protected void debugFocus() {
        if (activity.getCurrentFocus() != currentExpander.getAddTextView()) {
            Log.d(TAG, "The current focus is different from the expander's addTextView!!!!1!!1");
        }
    }

    public ArrayList<DatabaseProxyItem> getItemSelectionList() {
        int i = 0;
        ArrayList<DatabaseProxyItem> itemSelectionList = new ArrayList<DatabaseProxyItem>();
        for (boolean selected : selectionList) {
            Cursor cursor = getCursor();
            if (selected) {
                DatabaseProxyItem item = new DatabaseProxyItem();
                cursor.moveToPosition(i);
                item.targetUID = cursor.getLong(Receipt.DBUIDKeyIndex);
                item.tags = tags.get(item.targetUID);
                if (item.tags == null) {
                    item.tags = new ArrayList<Tag>();
                    Log.w(TAG, "Tags are null for item UID: " + idList[i]);
                }
                itemSelectionList.add(item);
            }
            i++;
        }
        return itemSelectionList;
    }

    public void detach() {
        if (currentExpander != null) {
            currentExpander.destroyView();
        }
    }
	
	@SuppressLint("DefaultLocale")
	public View getContentView(final int position, View convertView, ViewGroup root) {
		View returnedView;
		ItemViewHolder holder;
		if (convertView != null) {
			returnedView = convertView;
			holder = (ItemViewHolder)returnedView.getTag();
		}
		else {
			LayoutInflater inflater  = activity.getLayoutInflater();
			returnedView = inflater.inflate(R.layout.layout_item_window_scrap, root, false);
			holder = new ItemViewHolder();
			holder.title = (TextView)returnedView.findViewById(R.id.ItemTitle);
			holder.price = (TextView)returnedView.findViewById(R.id.PriceTitle);
			holder.qty = (TextView)returnedView.findViewById(R.id.QtyTitle);
            holder.tags = (TagView)returnedView.findViewById(R.id.ItemTags);
			returnedView.setTag(holder);

            //Code setting the correct layout margins
            ViewGroup.MarginLayoutParams workParams = (ViewGroup.MarginLayoutParams) holder.price.getLayoutParams();
            ViewGroup.MarginLayoutParams setParams = (ViewGroup.MarginLayoutParams) holder.qty.getLayoutParams();
            setParams.rightMargin = workParams.rightMargin + workParams.width;
            setParams = (ViewGroup.MarginLayoutParams) returnedView.findViewById(R.id.QtyTouchHelper).getLayoutParams();
            setParams.rightMargin = workParams.rightMargin + workParams.width;

            workParams = (ViewGroup.MarginLayoutParams) holder.qty.getLayoutParams();
            setParams = (ViewGroup.MarginLayoutParams) holder.title.getLayoutParams();
            setParams.rightMargin = workParams.rightMargin + workParams.width;
            workParams = (ViewGroup.MarginLayoutParams) holder.tags.getLayoutParams();
            setParams.leftMargin = workParams.leftMargin + workParams.width;
            setParams.leftMargin += holder.tags.getPaddingLeft() + holder.tags.getPaddingRight();
            workParams.width += holder.tags.getPaddingLeft() + holder.tags.getPaddingRight();

		}
		queryResult.moveToPosition(position);
		holder.id = queryResult.getLong(Receipt.DBTargetDBKeyIndex);
		holder.title.setText(queryResult.getString(Receipt.DBNameKeyIndex));
		holder.price.setText(ReceiptActivity.currentTruncatedLocale + ReceiptActivity.longToDecimalString(queryResult.getLong(Receipt.DBPriceKeyIndex)));
        holder.tags.setTags(tags.get(queryResult.getLong(Receipt.DBUIDKeyIndex)));
		long qty = queryResult.getLong(Receipt.DBQtyKeyIndex);
		if (qty == 0)
			qty = 10000;
		holder.qty.setText(ReceiptActivity.phoneQuantityFormattedString(activity, qty,  queryResult.getString(Receipt.DBUnitOfMeasurementKeyIndex)));
		
		returnedView.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
                if (tagExpanderTarget >= 0 && currentExpander == null) {
                    tagExpanderTarget = Long.MIN_VALUE;
                }
                if (currentExpander != null) {
                    currentExpander.compact();
                }
				selectionList[position] = !selectionList[position];
				
				if (selectionList[position]) {
					v.setSelected(true);
					v.setBackgroundResource(R.drawable.selected_scrap);
					selectionCount++;
				}
				else {
					v.setSelected(false);
					v.setBackgroundResource(R.drawable.unselected_scrap);
					selectionCount--;
				}

				fragment.notifySelectionCountChanged();
			}
		});

        final long UID = queryResult.getLong(Receipt.DBUIDKeyIndex);

        //noinspection PointlessBooleanExpression
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !DEBUG_COMPATIBILITY) {
            if (tagExpanderTarget == UID && !holder.hasExpandedTags) {
                if (currentExpander != null) {
                    currentExpander.destroy();
                }

                holder.hasExpandedTags = true;

                DatabaseProxyItem target = new DatabaseProxyItem();
                target.targetUID = UID;
                target.tags = tags.get(UID);
                currentExpander = TagExpander.fromViewInListViewContainerWithTarget(holder.tags, (ViewGroup) returnedView, target);
                currentExpander.setOnTagDeletedListener(ScrapListAdapter.this);
                currentExpander.expandAnimated(false);
                currentExpander.restoreStaticContext();
                final ItemViewHolder Holder = holder;
                currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                    @Override
                    public void onClose() {
                        currentExpander = null;
                        tagExpanderTarget = Long.MIN_VALUE;
                        Holder.hasExpandedTags = false;
                    }
                });
            }
        }

        if (selectionCount == 0) {
            holder.tags.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    //noinspection PointlessBooleanExpression
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN && !DEBUG_COMPATIBILITY) {
                        if (currentExpander != null) {
                            currentExpander.compact();
                        }

                        final ItemViewHolder holder = (ItemViewHolder) ((View) view.getParent().getParent()).getTag();
                        holder.hasExpandedTags = true;

                        DatabaseProxyItem target = new DatabaseProxyItem();
                        target.targetUID = UID;
                        target.tags = tags.get(UID);
                        tagExpanderTarget = UID;
                        currentExpander = TagExpander.fromViewInListViewContainerWithTarget((TagView) view, (ViewGroup) view.getParent(), target);
                        currentExpander.setOnTagDeletedListener(ScrapListAdapter.this);
                        currentExpander.expand();
                        currentExpander.setOnCloseListener(new TagExpander.OnCloseListener() {
                            @Override
                            public void onClose() {
                                currentExpander = null;
                                tagExpanderTarget = Long.MIN_VALUE;
                                holder.hasExpandedTags = false;
                            }
                        });
                    }
                    else {
                        // Running on ICS, where the precious setHasTransientState is missing
                        ((View) view.getParent().getParent()).performClick();
                        fragment.editTagsForSelection();
                    }
                }
            });
        }
        else {
            holder.tags.setOnClickListener(null);
            holder.tags.setClickable(false);
        }
		
		if (selectionList[position]) {
			returnedView.setSelected(true);
			returnedView.setBackgroundResource(R.drawable.selected_scrap);
		}
		else {
			returnedView.setSelected(false);
			returnedView.setBackgroundResource(R.drawable.unselected_scrap);
		}
		
		if (searchTerms != null)
			if (holder.title.getText().toString().toLowerCase(Locale.getDefault()).startsWith(searchTerms) ||
					(!searchTerms.isEmpty() && holder.title.getText().toString().toLowerCase(Locale.getDefault()).contains(" " + searchTerms)))
				returnedView.findViewById(R.id.SearchHighlight).setVisibility(View.VISIBLE);
			else
				returnedView.findViewById(R.id.SearchHighlight).setVisibility(View.GONE);
				
		
		returnedView.setId(position);
		return returnedView;
	}
	
	public View getInfoView(int position, View convertView, ViewGroup root) {
		View returnedView;
		InfoViewHolder holder;
		if (convertView != null) {
			returnedView = convertView;
			holder = (InfoViewHolder)returnedView.getTag();
		}
		else {
			LayoutInflater inflater  = activity.getLayoutInflater();
			returnedView = inflater.inflate(R.layout.layout_scrap_info_item, root, false);
			holder = new InfoViewHolder();
			holder.title = (TextView)returnedView.findViewById(R.id.ItemTitle);
			holder.detail = (TextView)returnedView.findViewById(R.id.PriceTitle);
			returnedView.setTag(holder);
			returnedView.setEnabled(false);
		}
		queryResult.moveToPosition(position);
		holder.title.setText(InfoTitles[position]);
		switch (position) {
			case UnixCheckoutTime: {
				Calendar time = Calendar.getInstance();
				time.setTimeInMillis(details[0]);
				holder.detail.setText(time.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.getDefault())
						+ " " + time.get(Calendar.DATE)
						+ ", " + time.get(Calendar.YEAR));
				break;
			}
			case ItemTypeCount: {
				Calendar time = Calendar.getInstance();
				time.setTimeInMillis(details[0]);
				holder.detail.setText(time.get(Calendar.HOUR_OF_DAY)
						+ ":" + ( time.get(Calendar.MINUTE) < 10 ? ("0" + time.get(Calendar.MINUTE)) : time.get(Calendar.MINUTE))
						+ ":" + ( time.get(Calendar.SECOND) < 10 ? ("0" + time.get(Calendar.SECOND)) : time.get(Calendar.SECOND)));
				break;
			}
			case TotalItemCount: {
				holder.detail.setText(String.valueOf(details[position - 1]));
				break;
			}
			case AssignedBudget:
			case RemainingBudget: {
				if (details[AssignedBudget] == Long.MAX_VALUE)
					holder.detail.setText(R.string.BudgetUnlimited);
				else {
					SpannableStringBuilder builder = new SpannableStringBuilder();
					builder.append(ReceiptActivity.currentLocale);
					if (ReceiptActivity.currentLocale.length() > 1) {
						builder.setSpan(new RelativeSizeSpan(0.95f), 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
						builder.setSpan(new TypefaceSpan("sans-serif-condensed")
								, 0, ReceiptActivity.currentLocale.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					}
					builder.setSpan(new ForegroundColorSpan(activity.getResources().getColor(R.color.unlimited_budget_colors)), 0, builder.length(),
							SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					builder.append(ReceiptActivity.totalToTruncatedDecimalString(details[position]));
					holder.detail.setText(builder);
				}
				break;
			}
			case Tax: {
				if (details[Tax] == 0)
					holder.detail.setText("0%");
				else
					holder.detail.setText(new BigDecimal(details[Tax]).movePointLeft(2).stripTrailingZeros().toPlainString() + "%");
				break;
			}
			case Subtotal: {
				SpannableStringBuilder builder = new SpannableStringBuilder();
				if (ReceiptActivity.currentLocale.length() > 1) {
                    builder.append(ReceiptActivity.totalToTruncatedDecimalString(details[position]));
                    builder.append(ReceiptActivity.currentLocale);
					builder.setSpan(new RelativeSizeSpan(0.95f), builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
					builder.setSpan(new TypefaceSpan("sans-serif-light")
							, builder.length() - ReceiptActivity.currentLocale.length(), builder.length(), SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.setSpan(new ForegroundColorSpan(activity.getResources().getColor(R.color.unlimited_budget_colors)), builder.length() - ReceiptActivity.currentLocale.length(), builder.length(),
                            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
				}
                else {
                    builder.append(ReceiptActivity.currentLocale);
                    builder.setSpan(new ForegroundColorSpan(activity.getResources().getColor(R.color.unlimited_budget_colors)), 0, builder.length(),
                            SpannableStringBuilder.SPAN_EXCLUSIVE_EXCLUSIVE);
                    builder.append(ReceiptActivity.totalToTruncatedDecimalString(details[position]));
                }
				holder.detail.setText(builder);
				break;
			}
			default: break;
		}
		return returnedView;
	}

    @Override
    public void onTagDeleted(Tag tag) {
        Toast.makeText(activity, "UI unimplemented!", Toast.LENGTH_SHORT).show();
    }

}
