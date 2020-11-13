package com.BogdanMihaiciuc.receipt;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.concurrent.locks.ReentrantLock;

public class Backend {

    final static ReentrantLock BackendLock = new ReentrantLock(true);

    // This class has constant and backend useful methods
    final static String BEDatabaseName = "ActiveLists";

    final static String BEItemCountKey = "itemCount";
    final static String BEItemCrossedCountKey = "itemCrossedCount";
    final static String BEPriceKey = "price";
    final static String BEBudgetKey = "budget";
    final static String BETaxKey = "tax";
    final static String BEReceiptUIDKey = "_ID";
    @SuppressWarnings("unused")
    final static String BEReceiptInternetUIDKey = "InternetUID";
    final static String BEAllReceiptColumns[] = new String[]{BEReceiptUIDKey, BEItemCountKey, BEItemCrossedCountKey, BEPriceKey, BETaxKey, BEBudgetKey};
    final static int BEReceiptUIDIndex = 0;
    final static int BEItemCountIndex = 1;
    final static int BEItemCrossedCountIndex = 2;
    final static int BEPriceIndex = 3;
    final static int BETaxIndex = 4;

    final static String BECreateReceiptDatabase =
            "create table receipts (_ID integer primary key, " +
                    "price text not null, " +
                    "tax integer not null, " +
                    "itemCount integer not null, " +
                    "itemCrossedCount integer not null, " +
                    "budget text not null);";
    static final String BEReceiptsTable = "receipts";

    final static String BENameKey = "name";
    final static String BEQtyKey = "qty";
    final static String BEUnitOfMeasurementKey = "unitOfMeasurement";
    final static String BEReceiptKey = "receipt";
    final static String BEIndexKey = "index";
    final static String BEImplicitPriceKey = "implicitPrice";
    final static String BECheckedKey = "checked";
    final static String BEItemUIDKey = "_ID";
    @SuppressWarnings("unused")
    final static String BEItemInternetUIDKey = "InternetUID";
    final static String BEAllItemsColumns[] = {BEItemUIDKey, BEIndexKey, BENameKey, BEPriceKey, BEImplicitPriceKey, BEQtyKey, BEUnitOfMeasurementKey, BECheckedKey, BEReceiptKey};
    final static int BEItemUIDIndex = 0;
    final static int BEIndexIndex = 1;
    final static int BENameIndex = 2;
    final static int BEImplicitPriceIndex = 4;
    final static int BEQtyIndex = 5;
    final static int BEUnitOfMeasurementIndex = 6;
    final static int BECheckedIndex = 7;
    final static int BEReceiptIndex = 8;

    static final String BEItemsTable = "items";

    final static String BECreateItemsDatabase =
            "create table items (_ID integer primary key, " +
                    "name text not null, " +
                    "qty integer not null, " +
                    "unitOfMeasurement text not null, " +
                    "price integer not null, " +
                    "implicitPrice integer not null, " +
                    "receipt integer not null, " +
                    "index integer not null, " +
                    "checked integer not null);";

    static final int BEDatabaseVersion = 1000;

    public static class BEDatabaseHelper extends SQLiteOpenHelper {

        BEDatabaseHelper(Context context) {
            super(context, BEDatabaseName, null, BEDatabaseVersion);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(BECreateReceiptDatabase);
            db.execSQL(BECreateItemsDatabase);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.d("Receipt", "Receipt is upgrading database from " + oldVersion + " to "
                    + newVersion + ".");
        }
    }

    static BEDatabaseHelper BEHelper = null;

}
