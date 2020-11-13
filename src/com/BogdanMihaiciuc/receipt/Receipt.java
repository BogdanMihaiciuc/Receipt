package com.BogdanMihaiciuc.receipt;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Typeface;
import android.os.Handler;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public class Receipt extends Application {

    final static String TAG = Receipt.class.getName();
	
	final static Object DatabaseLock = new Object();
	
	protected final static boolean USES_SYNC = false;
	
	// This class has constant and app-wide useful methods
	
	final static String DrivePrefix = "Receipt - ";
	
	final static String DBDateKey = "date";
	final static String DBItemCountKey = "itemCount";
	final static String DBItemCrossedCountKey = "itemCrossedCount";
	final static String DBPriceKey = "price";
	final static String DBBigPriceKey = "bigPrice";
	final static String DBBudgetKey = "budget";
	final static String DBTaxKey = "tax";
    final static String DBReceiptNameKey = "name";
	final static String DBFilenameIdKey = "targetId";
	final static String DBAccountNameKey = "accountName";
	final static String DBDriveFilenameIdKey = "driveFilenameId";
	final static String DBAllReceiptColumns[] = new String[]{DBFilenameIdKey, DBDateKey, DBPriceKey, DBItemCountKey, DBBudgetKey, DBBigPriceKey, DBTaxKey, DBReceiptNameKey};
	final static int DBFilenameIdKeyIndex = 0;
	final static int DBDateKeyIndex = 1;
	final static int DBItemCountKeyIndex = 3;
	final static int DBBudgetKeyIndex = 4;
	final static int DBBigPriceKeyIndex = 5;
    final static int DBTaxKeyIndex = 6;
    final static int DBReceiptNameKeyIndex = 7;
	final static int DBAccountNameKeyIndex = 5;
	final static int DBDriveFilenameIdKeyIndex = 6;
	
	final static String CreateReceiptDatabase =
	        "create table receipts (targetId integer primary key autoincrement, " + 
	        "date text not null, " +
	        "price integer not null, " +
	        "bigPrice text, " +
	        "tax integer not null, " +
	        "itemCount integer not null, " +
	        "itemCrossedCount integer not null, " +
	        "budget integer not null, " +
            "name text);";
	private static final String DatabaseName = "data";
    static final String DBReceiptsTable = "receipts";
	
	final static String DBNameKey = "name";
	final static String DBQtyKey = "qty";
	final static String DBUnitOfMeasurementKey = "unitOfMeasurement";
	final static String DBTargetDBKey = "targetDB";
	final static String DBIndexInReceiptKey = "targetIndex";
    final static String DBItemUIDKey = "_ID";
	final static String DBAllItemsColumns[] = {DBNameKey, DBQtyKey, DBPriceKey, DBUnitOfMeasurementKey, DBTargetDBKey, DBIndexInReceiptKey, DBItemUIDKey};
	final static int DBNameKeyIndex = 0;
	final static int DBQtyKeyIndex = 1;
	final static int DBPriceKeyIndex = 2;
	final static int DBUnitOfMeasurementKeyIndex = 3;
	final static int DBTargetDBKeyIndex = 4;
	final static int DBIndexInReceiptKeyIndex = 5;
	final static int DBUIDKeyIndex = 6;
	
    static final String DBItemsTable = "receiptItems";
	final static String DriveMimeType = "application/x-receipt";
    final static String EntireHistoryMimeType = "application/x-receipt-history";
	
	final static String CreateItemsDatabase =
	        "create table receiptItems (_ID integer primary key, " +
	        "name text not null, " +
	        "qty integer not null, " +
	        "unitOfMeasurement text not null, " +
	        "price integer not null, " +
	        "targetDB integer not null, " +
	        "targetIndex integer not null);";

    final static String DBColorKey = "color";
    final static String DBUIDKey = "UID";
    final static String DBAllTagColumns[] = {DBNameKey, DBColorKey, DBUIDKey};
    final static int DBColorKeyIndex = 1;
    final static int DBTagUIDKeyIndex = 2;

    final static String DBTagsTable = "tags";

    final static String CreateTagsDatabase =
            "create table " + DBTagsTable + " (" + DBUIDKey + " integer primary key, " +
                    DBNameKey + " text not null, " +
                    DBColorKey + " integer not null);";

    final static String DBItemConnectionUIDKey = "itemUID";
    final static String DBTagConnectionUIDKey = "tagUID";
    final static String DBAllTagConnectionColumns[] = {DBItemConnectionUIDKey, DBTagConnectionUIDKey};
    final static int DBItemConnectionKeyIndex = 0;
    final static int DBTagConnectionUIDKeyIndex = 1;

    final static String DBTagConnectionsTable = "tagConnections";

    final static String CreateTagConnectionsDatabase =
            "create table " + DBTagConnectionsTable + " (" + DBItemConnectionUIDKey + " integer not null, " +
                    DBTagConnectionUIDKey + " integer not null, " +
                    "primary key (" + DBItemConnectionUIDKey + ", " + DBTagConnectionUIDKey + "));";
	
	final static String DBPendingTable = "pendingItems";
	final static String DBAllPendingItemsColumns[] = {DBNameKey, DBQtyKey, DBPriceKey, DBUnitOfMeasurementKey, DBItemUIDKey};
	final static String CreatePendingDatabase = 
	        "create table pendingItems (_ID integer not null, " +
	        "name text not null, " +
	        "qty integer not null, " +
	        "unitOfMeasurement text not null, " +
	        "price integer not null);";
    final static int DBPendingItemUIDKeyIndex = DBUnitOfMeasurementKeyIndex + 1;

    // TODO Integrate
    final static String DBIncomeTable = "income";
    final static String DBIncomeUIDKey = "incomeUID";
    final static String DBIncomeAmountKey = "incomeAmount";
    final static String DBIncomeDateKey = "incomeDate";
    final static String DBAllIncomeColumns[] = {DBIncomeUIDKey, DBIncomeAmountKey, DBIncomeDateKey};
    final static String CreateIncomeDatabase =
            "create table " + DBIncomeTable + " (" + DBIncomeUIDKey + " integer primary key, " +
                    DBIncomeAmountKey + " text not null, " +
                    DBIncomeDateKey + " text not null);";

	
	final static String DBLocalChangesTable = "localChanges";
	final static String DBAllLocalChangesColumns[] = {DBDriveFilenameIdKey, DBAccountNameKey};
	final static int DBChangedDriveFilenameIdKeyIndex = 0;
	final static int DBChangedAccountNameKeyIndex = 1;
	final static String CreateLocalChangesTable =
			"create table localChanges ( _ID integer primary key, " +
			DBDriveFilenameIdKey + " text not null, " +
			DBAccountNameKey + " text not null);";
	
	final static String Authority = "com.BogdanMihaiciuc.receipt.provider";
	
	final static int ReceiptsKey = 0;
	final static int ReceiptsIdKey = 2;
	final static int ItemsKey = 1;
	final static int ItemsIdKey = 3;
	
    static final int DatabaseVersion = 1506;
    
    public static class DatabaseHelper extends SQLiteOpenHelper {

        DatabaseHelper(Context context) {
            super(context, DatabaseName, null, DatabaseVersion);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        	db.execSQL(CreateReceiptDatabase);
            db.execSQL(CreateItemsDatabase);
            db.execSQL(CreatePendingDatabase);
            db.execSQL(CreateTagsDatabase);
            db.execSQL(CreateTagConnectionsDatabase);
        }

        @SuppressWarnings("unused")
		@Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            synchronized (DatabaseLock) {
                Log.i(TAG, "Receipt is upgrading the database from version " + oldVersion + " to "
                        + newVersion + ".");

                if (oldVersion < 1200) {
                    db.beginTransaction();
                    try {
                        db.execSQL("create table backup as select * from " + DBItemsTable);
                        db.execSQL("drop table if exists " + DBItemsTable);
                        db.execSQL(CreateItemsDatabase);
                        db.execSQL("insert into " + DBItemsTable + " (_ID, name, qty, price, targetDB, targetIndex, unitOfMeasurement) " +
                                "select _ID, name, qty, price, targetDB, targetIndex, 'x' as unitOfMeasurement from backup");
                        db.execSQL("update " + DBItemsTable + " set unitOfMeasurement='x'");
                        db.execSQL("drop table if exists backup");
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {

                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1300) {
                    db.beginTransaction();
                    try {
                        db.execSQL(CreatePendingDatabase);
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {

                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1402 && USES_SYNC) {

                    db.beginTransaction();
                    try {
                        db.execSQL("create table backup as select * from " + DBReceiptsTable);
                        Log.d("Receipt", "1");
                        db.execSQL("drop table if exists " + DBReceiptsTable);
                        Log.d("Receipt", "1");
                        db.execSQL(CreateReceiptDatabase);
                        Log.d("Receipt", "1");
                        db.rawQuery("select accountName from receipts", null);
                        Log.d("Receipt", "1");
                        db.execSQL("insert into " + DBReceiptsTable + " (targetId, date, price, itemCount, itemCrossedCount, budget, accountName, driveFilenameId) " +
                                "select targetId, date, price, itemCount, itemCrossedCount, budget, null, null from backup");
                        Log.d("Receipt", "1");
                        db.execSQL("drop table if exists backup");
                        Log.d("Receipt", "1");
                        db.execSQL("drop table if exists " + DBLocalChangesTable);
                        db.execSQL(CreateLocalChangesTable);
                        Log.d("Receipt", "1");
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {

                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1450) {

                    db.beginTransaction();
                    try {
                        db.execSQL("create table backup as select * from " + DBReceiptsTable);
                        db.execSQL("drop table if exists " + DBReceiptsTable);
                        db.execSQL(CreateReceiptDatabase);
                        db.execSQL("insert into " + DBReceiptsTable + " (targetId, date, price, itemCount, itemCrossedCount, budget, bigPrice, tax) " +
                                "select targetId, date, price/100, itemCount, itemCrossedCount, budget/100, null, 0 from backup");
                        db.execSQL("drop table if exists backup");
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {

                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1455) {

                    db.beginTransaction();
                    try {
                        db.execSQL("create table backup as select * from " + DBItemsTable);
                        db.execSQL("drop table if exists " + DBItemsTable);
                        db.execSQL(CreateItemsDatabase);
                        db.execSQL("insert into " + DBItemsTable + " (_ID, name, qty, unitOfMeasurement, price, targetDB, targetIndex) " +
                                "select _ID, name, qty * 100, unitOfMeasurement, price, targetDB, targetIndex from backup");
                        db.execSQL("drop table if exists backup");
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {

                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1456) {

                    db.beginTransaction();
                    try {
                        db.execSQL("update " + DBReceiptsTable + " set " + DBBudgetKey + " = " + Long.MAX_VALUE + " where " + DBBudgetKey + " = " + (Long.MAX_VALUE/100));
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {

                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1502) {
                    db.beginTransaction();
                    try {
                        db.execSQL(CreateTagsDatabase);
                        db.execSQL(CreateTagConnectionsDatabase);

                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {

                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1504) {
                    db.beginTransaction();
                    try {
                        db.execSQL("drop table if exists " + DBTagConnectionsTable);
                        db.execSQL(CreateTagConnectionsDatabase);
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {
                        Log.e("", "The following error has caused the update process to terminate before it could complete.");
                        e.printStackTrace();
                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1505) {
                    db.beginTransaction();
                    try {
                        db.execSQL("drop table if exists " + DBPendingTable);
                        db.execSQL(CreatePendingDatabase);
                        db.execSQL(CreateIncomeDatabase);
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {
                        Log.e("", "The following error has caused the update process to terminate before it could complete.");
                        e.printStackTrace();
                    }
                    finally {
                        db.endTransaction();
                    }
                }

                if (oldVersion < 1506) {
                    db.beginTransaction();
                    try {
                        db.execSQL("alter table " + DBReceiptsTable + " add " + DBReceiptNameKey + " text");
                        db.setTransactionSuccessful();
                    }
                    catch (Exception e) {
                        Log.e("", "The following error has caused the update process to terminate before it could complete.");
                        e.printStackTrace();
                    }
                    finally {
                        db.endTransaction();
                    }
                }
            }
        }
    }
	
	static DatabaseHelper DBHelper = null;
    final static Handler handler = new Handler();

    public static void headsUpFromView(final View fromView, final CharSequence message) {
        final Activity activity = (Activity) fromView.getContext();
        HeadsUpFragment headsUp = new HeadsUpFragment(fromView, message);
        activity.getFragmentManager().beginTransaction().add(headsUp, "HeadsUpFragment" + fromView.getId()).commit();
    }

    public static class SQLSimpleQueryBuilder {
        String table;
        String columns[];
        String selection;
        String args[];
        String grouper;
        String having;
        String order;
        String limit;
        boolean distinct;

        SQLiteDatabase database;

        private SQLSimpleQueryBuilder(SQLiteDatabase database) {
            this.database = database;
        }

        public SQLSimpleQueryBuilder fromTable(String table) {
            this.table = table;
            return this;
        }

        public SQLSimpleQueryBuilder selectColumns(String ... columns) {
            this.columns = columns;
            return this;
        }

        public SQLSimpleQueryBuilder where(String selection) {
            this.selection = selection;
            return this;
        }

        public SQLSimpleQueryBuilder where(String selection, String args[]) {
            this.selection = selection;
            this.args = args;
            return this;
        }

        public SQLSimpleQueryBuilder withArgs(String ... args) {
            this.args = args;
            return this;
        }

        public SQLSimpleQueryBuilder groupBy(String grouper) {
            this.grouper = grouper;
            return this;
        }

        public SQLSimpleQueryBuilder having(String having) {
            this.having = having;
            return this;
        }

        public SQLSimpleQueryBuilder orderBy(String order) {
            this.order = order;
            return this;
        }

        public SQLSimpleQueryBuilder limit(String limit) {
            this.limit = limit;
            return this;
        }

        public SQLSimpleQueryBuilder setDistinct(boolean distinct) {
            this.distinct = distinct;
            return this;
        }

        public Cursor execute() {
            if (table == null || columns == null) throw new UnsupportedOperationException("Query with null table or columns");
            if (!distinct && limit == null) {
                return database.query(table, columns, selection, args, grouper, having, order);
            }
            return database.query(distinct, table, columns, selection, args, grouper, having, order, limit);
        }

    }

    // *** as per: http://stackoverflow.com/questions/2711858/is-it-possible-to-set-font-for-entire-application

    public static void setDefaultFont(Context context,
                                      String staticTypefaceFieldName, String fontAssetName) {
        final Typeface regular = Typeface.createFromAsset(context.getAssets(),
                fontAssetName);
        replaceFont(staticTypefaceFieldName, regular);
    }

    protected static void replaceFont(String staticTypefaceFieldName,
                                      final Typeface newTypeface) {
        try {
            final Field staticField = Typeface.class
                    .getDeclaredField(staticTypefaceFieldName);
            staticField.setAccessible(true);
            staticField.set(null, newTypeface);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        }
    }

    // ***

    static Receipt application;

    public static Context getStaticContext() {
        return application;
    }

    private static Typeface defaultTypeface, condensed, condensedBold, condensedLight, medium;
    private static ForegroundColorSpan textLightSpan, titleLightSpan, hintLightSpan;

    public static Typeface defaultTypeface() {
        return defaultTypeface;
    }

    public static Typeface condensedTypeface() {
        return condensed;
    }

    public static Typeface condensedLightTypeface() {
        return condensedLight;
    }

    public static Typeface condensedBoldTypeface() {
        return condensedBold;
    }

    public static Typeface mediumTypeface() { return medium; }

    public static ForegroundColorSpan textLightSpan() { return textLightSpan; }

    public static ForegroundColorSpan titleLightSpan() { return titleLightSpan; }

    public static ForegroundColorSpan hintLightSpan() { return hintLightSpan; }

    public static SQLSimpleQueryBuilder queryDatabase(SQLiteDatabase database) {
        return new SQLSimpleQueryBuilder(database);
    }

    // ** as per: https://gist.github.com/artem-zinnatullin/7749076

    public static void overrideFont(Context context, String defaultFontNameToOverride, String customFontFileNameInAssets) {
        try {
            final Typeface customFontTypeface = Typeface.createFromAsset(context.getAssets(), customFontFileNameInAssets);

            final Field defaultFontTypefaceField = Typeface.class.getDeclaredField(defaultFontNameToOverride);
            defaultFontTypefaceField.setAccessible(true);
            defaultFontTypefaceField.set(null, customFontTypeface);
        } catch (Exception e) {
            Log.e(TAG, "Can not set custom font " + customFontFileNameInAssets + " instead of " + defaultFontNameToOverride);
        }
    }

    // **

    static String[] currencyLocaleList;
    static String[] currencyNameList;
    static ArrayList<String> currencySymbols = new ArrayList<String>();
    static ArrayList<String> currencyNames = new ArrayList<String>();
    static String defaultLocale;

    static void createCurrencyList() {

        if (currencyLocaleList != null && currencyNameList != null) return;

        Locale[] locales = Locale.getAvailableLocales();
        Locale defaultLocale = Locale.getDefault();
        Currency currency;

        TreeMap<String, String[]> currencies = new TreeMap<String, String[]>();

        for (Locale locale : locales) {
            try {
                currency = Currency.getInstance(locale);
                String country = locale.getDisplayCountry(defaultLocale);
                CharSequence symbol = currency.getSymbol(defaultLocale);
                if (country.trim().length()>0) {
                    currencies.put(locale.getDisplayCountry(defaultLocale) + " - " + symbol, new String[] {symbol.toString(), locale.getDisplayCountry(defaultLocale)});
                }
            }
            catch (IllegalArgumentException e) {}
        }

        final String[][] result = new String[2][currencies.size()];
        currencySymbols.ensureCapacity(currencies.size());
        currencyNames.ensureCapacity(currencies.size());

        final Iterator<?> iter = currencies.entrySet().iterator();

        int ii = 0;
        while(iter.hasNext()){
            final Map.Entry<?, ?> mapping = (Map.Entry<?, ?>) iter.next();

            result[0][ii] = (String) mapping.getKey();
            result[1][ii] = ((String[]) mapping.getValue())[0];

            currencySymbols.add(((String[]) mapping.getValue())[0]);
            currencyNames.add(((String[]) mapping.getValue())[1]);

            ii++;
        }

        currencyLocaleList = result[1];
        currencyNameList = result[0];


        try {
            Receipt.defaultLocale = defaultLocale.getDisplayCountry(defaultLocale) + " - " + Currency.getInstance(defaultLocale).getSymbol(defaultLocale);
        }
        catch (IllegalArgumentException e) {}

    }

    @Override
    public void onCreate() {
        super.onCreate();

        overrideFont(getApplicationContext(), "SANS_SERIF", "Roboto-Regular.ttf");

        application = this;

        if (DBHelper == null)
            DBHelper = new DatabaseHelper(this);

        if (!TagStorage.loaded) {
            TagStorage.loadTags();
            TagStorage.getAllAvailableColors(getResources());
        }

        defaultTypeface = Typeface.SANS_SERIF;
        condensed = Typeface.createFromAsset(getAssets(), "RobotoCondensed-Regular.ttf");
        condensedLight = Typeface.createFromAsset(getAssets(), "RobotoCondensed-Light.ttf");
        condensedBold = Typeface.createFromAsset(getAssets(), "RobotoCondensed-Bold.ttf");
        medium = Typeface.createFromAsset(getAssets(), "Roboto-Medium.ttf");

        textLightSpan = new ForegroundColorSpan(getResources().getColor(R.color.DashboardTitle));
        titleLightSpan = new ForegroundColorSpan(getResources().getColor(R.color.DashboardSubtitle));
        hintLightSpan = new ForegroundColorSpan(getResources().getColor(R.color.DashboardSubtitle));

        createCurrencyList();

        ItemCollectionFragment.findMostUsedItems(this);
    }

}
