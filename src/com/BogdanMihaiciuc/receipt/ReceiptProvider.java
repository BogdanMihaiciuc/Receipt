package com.BogdanMihaiciuc.receipt;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.text.TextUtils;

import com.BogdanMihaiciuc.receipt.Receipt.DatabaseHelper;

import java.util.Calendar;

import static com.BogdanMihaiciuc.receipt.Receipt.Authority;
import static com.BogdanMihaiciuc.receipt.Receipt.DBBudgetKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBDateKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBFilenameIdKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBHelper;
import static com.BogdanMihaiciuc.receipt.Receipt.DBIndexInReceiptKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBItemCountKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBItemCrossedCountKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBItemsTable;
import static com.BogdanMihaiciuc.receipt.Receipt.DBNameKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBPriceKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBQtyKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBReceiptsTable;
import static com.BogdanMihaiciuc.receipt.Receipt.DBTargetDBKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DBUnitOfMeasurementKey;
import static com.BogdanMihaiciuc.receipt.Receipt.DriveMimeType;
import static com.BogdanMihaiciuc.receipt.Receipt.ItemsIdKey;
import static com.BogdanMihaiciuc.receipt.Receipt.ItemsKey;
import static com.BogdanMihaiciuc.receipt.Receipt.ReceiptsIdKey;
import static com.BogdanMihaiciuc.receipt.Receipt.ReceiptsKey;

public class ReceiptProvider extends ContentProvider {
	
	final static UriMatcher URIMatcher;
	
	static {
		
		URIMatcher = new UriMatcher(UriMatcher.NO_MATCH);
		URIMatcher.addURI(Authority, "receipts", ReceiptsKey);
		URIMatcher.addURI(Authority, "receipts/#", ReceiptsIdKey);
		URIMatcher.addURI(Authority, "items", ItemsKey);
		URIMatcher.addURI(Authority, "items/#", ItemsIdKey);
		
	}
	
	@Override
	public boolean onCreate() {
		if (DBHelper == null)
			DBHelper = new DatabaseHelper(getContext().getApplicationContext());
		
		return true;
	}
	
	@Override
	public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
		
		SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
		String sort;
		
		switch (URIMatcher.match(uri)) {
		case ReceiptsIdKey: {
			builder.appendWhere(DBFilenameIdKey + " = " + uri.getLastPathSegment());
		}
		case ReceiptsKey: {
			builder.setTables(DBReceiptsTable);
			sort = DBDateKey + " desc";
			break;
		}
		case ItemsIdKey: {
			builder.appendWhere(DBTargetDBKey + " = " + uri.getLastPathSegment());
		}
		case ItemsKey: {
			builder.setTables(DBItemsTable);
			sort = null;
			break;
		}
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}
		
		if (!TextUtils.isEmpty(sortOrder))
			sort = sortOrder;
		
		SQLiteDatabase db = DBHelper.getReadableDatabase();
		
		Cursor cursor = builder.query(db, projection, selection, selectionArgs, null, null, sort);
		cursor.setNotificationUri(getContext().getContentResolver(), uri);
		
		return cursor;
		
	}
	
	@Override
	public String getType(Uri uri) {
		return DriveMimeType;
	}

	@Override
	public int delete(Uri uri, String where, String[] selectionArgs) {
		
		final SQLiteDatabase db = DBHelper.getWritableDatabase();
		String selection = "";
		String table = null;
		
		switch (URIMatcher.match(uri)) {
		case ReceiptsIdKey:
			selection = DBFilenameIdKey + " = " + uri.getLastPathSegment();
		case ReceiptsKey: 
			table = DBReceiptsTable;
			break;
		case ItemsIdKey:
			selection = DBTargetDBKey + " = " + uri.getLastPathSegment();
		case ItemsKey: 
			table = DBItemsTable;
			break;
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}
		
		if (TextUtils.isEmpty(selection))
			selection = where;
		else
			selection = "(" + selection + ") and (" + where + ")";
		
		switch (URIMatcher.match(uri)) {
		case ReceiptsIdKey:
		case ReceiptsKey: 
			Cursor deletedReceipts;
			if (TextUtils.isEmpty(selection))
				deletedReceipts = db.rawQuery("select " + DBFilenameIdKey + " from " + DBReceiptsTable, null);
			else
				deletedReceipts = db.rawQuery("select " + DBFilenameIdKey + " from " + DBReceiptsTable
					+ " where " + selection, selectionArgs);
			
			while (deletedReceipts.moveToNext())
				db.execSQL("delete from " + DBItemsTable + " where " + DBTargetDBKey + " = " + deletedReceipts.getLong(0));
			deletedReceipts.close();
			break;
		default:break;
		}
		
		int rows;
		
		if (TextUtils.isEmpty(selection))
			rows = db.delete(table, "1", null);
		else
			rows = db.delete(table, selection, selectionArgs);
		
		getContext().getContentResolver().notifyChange(uri, null);
		return rows;
		
	}

	@Override
	public int update(Uri uri, ContentValues values, String selection,
			String[] args) {
		
		switch (URIMatcher.match(uri)) {
		case (ReceiptsKey):
		case ReceiptsIdKey:
			return updateReceipts(uri, values, selection, args);
		case ItemsKey:
		case ItemsIdKey:
			return updateItems(uri, values, selection, args);
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}
	}
	
	public int updateReceipts(Uri uri, ContentValues values, String selection, String[] args) {
		
		String where = null;
		
		if (URIMatcher.match(uri) == ReceiptsIdKey) {
			where = "(" + DBFilenameIdKey + " = " + uri.getLastPathSegment() + ")";
		}
		
		if (where != null)
			where += " and (" + selection + ")";
		else
			where = selection;
		
		SQLiteDatabase db = DBHelper.getWritableDatabase();
		int count = db.update(Receipt.DBReceiptsTable, values, where, args);
		
		getContext().getContentResolver().notifyChange(uri, null);
	
		return count;
	}
	
	public int updateItems(Uri uri, ContentValues values, String selection, String[] args) {
		
		String where = null;
		
		if (URIMatcher.match(uri) == ItemsIdKey) {
			where = "(" + DBTargetDBKey + " = " + uri.getLastPathSegment() + ")";
		}
		
		if (where != null)
			where += " and (" + selection + ")";
		else
			where = selection;
		
		SQLiteDatabase db = DBHelper.getWritableDatabase();
		int count = db.update(Receipt.DBItemsTable, values, where, args);
		
		getContext().getContentResolver().notifyChange(uri, null);
	
		return count;
	}

	@Override
	public Uri insert(Uri uri, ContentValues values) {
		
		switch (URIMatcher.match(uri)) {
		case (ReceiptsKey):
		case ReceiptsIdKey:
			return insertIntoReceipts(uri, values);
		case ItemsKey:
		case ItemsIdKey:
			return insertIntoItems(uri, values);
		default:
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}
		
	}
	
	public Uri insertIntoReceipts(Uri uri, ContentValues values) {
		
		if (!values.containsKey(DBDateKey))
			values.put(DBDateKey, (long)Calendar.getInstance().getTimeInMillis()/1000);
		if (!values.containsKey(DBPriceKey))
			values.put(DBPriceKey, (long)0);
		if (!values.containsKey(DBBudgetKey))
			values.put(DBBudgetKey, (long)0);
		if (!values.containsKey(DBItemCountKey))
			values.put(DBItemCountKey, (long)0);
		if (!values.containsKey(DBItemCrossedCountKey))
			values.put(DBItemCrossedCountKey, (long)0);
		
		SQLiteDatabase db = DBHelper.getWritableDatabase();
		long id = db.insert(Receipt.DBReceiptsTable, null,
					values);

		getContext().getContentResolver().notifyChange(ContentUris.withAppendedId(uri, id), null);
		
		return ContentUris.withAppendedId(uri, id);
		
	}
	
	public Uri insertIntoItems(Uri uri, ContentValues values) {
		
		if (!values.containsKey(DBNameKey))
			throw new IllegalArgumentException("Can't insert items without a name.");
		if (!values.containsKey(DBPriceKey))
			values.put(DBPriceKey, (long)0);
		if (!values.containsKey(DBQtyKey))
			values.put(DBQtyKey, (long)0);
		if (!values.containsKey(DBTargetDBKey))
			throw new IllegalArgumentException("Can't insert items without a target receipt.");
		if (!values.containsKey(DBIndexInReceiptKey))
			values.put(DBIndexInReceiptKey, (long)0);
		if (!values.containsKey(DBUnitOfMeasurementKey))
			values.put(DBUnitOfMeasurementKey, "x");
		
		SQLiteDatabase db = DBHelper.getWritableDatabase();
		long id = db.insert(Receipt.DBItemsTable, null,
					values);

		getContext().getContentResolver().notifyChange(ContentUris.withAppendedId(uri, id), null);
		
		return ContentUris.withAppendedId(uri, id);
	}
	
}
