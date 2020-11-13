package com.BogdanMihaiciuc.receipt;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import android.util.Log;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

// This class handles all non-UI related storage for the Backend
public class BackendStorage {

    // The DiskLock is used to serialize save/open operations
    // The lock MUST be fair, to ensure that saves always occur BEFORE opens and that
    // saves are commited in the order they were made
    final static Lock DiskLock = new ReentrantLock(true);

    final static String TAG = "BackendStorage";
    final static boolean DEBUG = false;
    final static boolean DEBUG_JUNK_FILES = true;
    final static boolean DEBUG_SAVE = true;
    final static boolean DEBUG_SLOW_SAVE = false;
    final static int SLOW_SAVE_LENGTH = 500;

    final static long MinimumPossibleFilenameId = Long.MIN_VALUE;

    final static String SharedPreferencesFilename = "BackendStorage";

    final static String FilenamePrefix = "receipt_";
    final static String ActiveListsFilename = "receipt_active_lists";
    final static String LastUsedFilenameIdKey = "lastUsedFilenameId";

    final static int LoadTypeFull = 0;
    final static int LoadTypeDisplay = 1;

    static interface OnLoadProgressListener {
        public void onProgressUpdate();
    }

    static class AbstractReceipt {
        String filename;
        ReceiptFileHeader header;
        ArrayList<ItemCollectionFragment.Item> items;

        // Runtime information
        boolean loadedForDisplay;
        boolean fullyLoaded;
        boolean selected;
    }

    static class ReceiptFileHeader {
        long fileVersion = 7;

        int itemsCrossed;
        int totalItems;
        BigDecimal total;
        BigDecimal estimatedTotal;
        BigDecimal budget = ReceiptActivity.UnlimitedBudget;
        int tax;

        String name;

        long startingVersion;

        public ReceiptFileHeader() {
            total = new BigDecimal(0);
            estimatedTotal = new BigDecimal(0);
            budget = ReceiptActivity.UnlimitedBudget;
        }

        public void flatten(ObjectOutputStream os) throws IOException {
            os.writeLong(fileVersion);
            os.writeInt(itemsCrossed);
            os.writeInt(totalItems);
            os.writeUTF(total.toString());
            os.writeUTF(estimatedTotal.toString());
            os.writeUTF(budget.toString());
            os.writeInt(tax);
            os.writeUTF(name != null ? name : "");
        }

        public static ReceiptFileHeader inflate(ObjectInputStream is) throws IOException {
            ReceiptFileHeader header = new ReceiptFileHeader();
            header.fileVersion = is.readLong();
            header.itemsCrossed = is.readInt();
            header.totalItems = is.readInt();

            header.startingVersion = header.fileVersion;

            // AT THIS POINT, THE FILE VERSION IS AT LEAST 3
            if (header.fileVersion == 3) {
                header.total = new BigDecimal(is.readLong()).movePointLeft(4);
                header.estimatedTotal = new BigDecimal(-1);
                long budget = is.readLong();
                if (budget == Long.MAX_VALUE)
                    header.budget = ReceiptActivity.UnlimitedBudget;
                else
                    header.budget = new BigDecimal(budget).movePointLeft(4);
                header.fileVersion = 4;
            }
            else {
                header.total = new BigDecimal(is.readUTF());
                header.estimatedTotal = new BigDecimal(is.readUTF());
                header.budget = new BigDecimal(is.readUTF());
            }

            // AT THIS POINT, THE FILE VERSION IS AT LEAST 4
            if (header.fileVersion == 4) {
                header.tax = 0;
                header.fileVersion = 5;
            }
            else {
                header.tax = is.readInt();
            }

            // AT THIS POINT, THE FILE VERSION IS AT LEAST 5
            // upgrading to version 6 is done automatically on save; items are loaded based on the starting version, if applicable
            if (header.fileVersion == 5) header.fileVersion = 6;

            // AT THIS POINT, THE FILE VERSION IS AT LEAST 6
            if (header.fileVersion == 6) {
                header.name = "";
                header.fileVersion = 7;
            }
            else {
                header.name = is.readUTF();
            }

            return header;
        }
    }

    private static BackendStorage sharedStorage;
    private long lastUsedFilenameId;

    private ArrayList<AbstractReceipt> activeLists = new ArrayList<AbstractReceipt>();
    private ArrayList<OnLoadProgressListener> listeners = new ArrayList<OnLoadProgressListener>();

    static BackendStorage getSharedStorage(Context ApplicationContext) {
        if (sharedStorage == null) {
            sharedStorage = new BackendStorage(ApplicationContext);
            sharedStorage.initialize();
        }
        return sharedStorage;
    }

    private Context context;

    private BackendStorage(Context context) {
        this.context = context;
    }

    public void addOnLoadProgressListener(OnLoadProgressListener listener) {
        listeners.add(listener);
    }

    public void removeOnLoadProgressListener(OnLoadProgressListener listener) {
        listeners.remove(listener);
    }

    public List<AbstractReceipt> getActiveLists() {
        return Collections.unmodifiableList(activeLists);
    }

    public AbstractReceipt newReceipt() {
        AbstractReceipt receipt =new AbstractReceipt();
        lastUsedFilenameId += 1;
        PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(LastUsedFilenameIdKey, lastUsedFilenameId).apply();
        receipt.filename = FilenamePrefix + lastUsedFilenameId;
        receipt.fullyLoaded = true;
        receipt.header = new ReceiptFileHeader();
        receipt.header.tax = PreferenceManager.getDefaultSharedPreferences(context).getInt(ReceiptActivity.LastUsedTaxKey, 0);
        receipt.items = new ArrayList<ItemCollectionFragment.Item>();
        receipt.loadedForDisplay = true;
        new ReceiptSaver(receipt).execute(context);
        return receipt;
    }

    public String createFilename() {
        lastUsedFilenameId += 1;
        PreferenceManager.getDefaultSharedPreferences(context).edit().putLong(LastUsedFilenameIdKey, lastUsedFilenameId).apply();
        return FilenamePrefix + lastUsedFilenameId;
    }

    public void importReceiptToIndex(AbstractReceipt receipt, int index) {

        receipt.loadedForDisplay = true;
        receipt.fullyLoaded = true;
        receipt.filename = createFilename();

        activeLists.add(index, receipt);
        saveReceiptAt(index);
    }

    public AbstractReceipt addNewReceipt() {
        AbstractReceipt receipt = newReceipt();
        addReceipt(receipt);
        return receipt;
    }

    public AbstractReceipt addNewReceiptTo(int position) {
        AbstractReceipt receipt = newReceipt();
        addReceiptTo(receipt, position);
        return receipt;
    }

    public void addReceipt(AbstractReceipt receipt) {
        activeLists.add(receipt);
    }

    public void addReceiptTo(AbstractReceipt receipt, int position) {
        activeLists.add(position, receipt);
    }

    public void movePositionTo(int originalPosition, int targetPosition) {
        try {
            activeLists.add(targetPosition, activeLists.remove(originalPosition));
        }
        catch (ArrayIndexOutOfBoundsException e) {
            Log.d(TAG, "ArrayIndexOutOfBoundsException has occured during movePositionTo(original: " + originalPosition + ", target: " + targetPosition + ")!");
            for (int i = 0; i < activeLists.size(); i++) {
                String display = "Receipt " + activeLists.get(i).filename + ": ";
                for (int j = 0; j < activeLists.get(i).items.size(); j++) {
                    display += activeLists.get(i).items.get(j).name;
                }
                Log.d(TAG, display);
            }

            throw e;
        }
    }

    public void replaceReceiptAtWith(int position, AbstractReceipt newReceipt) {
        removeReceiptAt(position);
        addReceiptTo(newReceipt, position);
    }

    public void removeReceipt(AbstractReceipt receipt) {
        activeLists.remove(receipt);
    }

    public AbstractReceipt removeReceiptAt(int position) {
        return activeLists.remove(position);
    }

    public void deleteReceipt(final AbstractReceipt receipt) {
        removeReceipt(receipt);
        new Thread() {
            public void run() {
                synchronized(receipt.filename) { context.deleteFile(receipt.filename); }
            }
        }.run();
    }

    public void deleteReceiptAt(int position) {
        final AbstractReceipt deletedReceipt = removeReceiptAt(position);
        new Thread() {
            public void run() {
                synchronized(deletedReceipt.filename) { context.deleteFile(deletedReceipt.filename); }
            }
        }.run();
    }

    public void loadReceipt(AbstractReceipt receipt) throws IOException{
        loadReceipt(receipt, LoadTypeFull);
    }

    public void loadReceipt(AbstractReceipt receipt, int loadType) throws IOException {
        if (loadType == LoadTypeFull)
            fullyLoadReceipt(receipt);
        else
            loadReceiptForDisplay(receipt);
    }

    public void saveReceiptAt(int position) {
        if (activeLists.size() != 0) //If it was not fully loaded, it wasn't changed so there's no need to tamper with its file
            if (activeLists.get(position).fullyLoaded) new ReceiptSaver(activeLists.get(position)).execute(context);
    }

    public void save(ReceiptActivity activity) {
        if (activeLists.size() > 0) {
            if (activeLists.get(0).header == null)
                activeLists.get(0).header = new ReceiptFileHeader();
            updateReceiptFromActivity(activeLists.get(0), activity);
            PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).edit()
                    .putLong(LastUsedFilenameIdKey, lastUsedFilenameId).apply();
        }
        new SaveStateTask().execute(TaskHelper.make(context, activeLists));
    }

    private void initialize() {

        final SharedPreferences globalPrefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());

        try {
            lastUsedFilenameId = globalPrefs.getLong(LastUsedFilenameIdKey, MinimumPossibleFilenameId);
        }
        catch (ClassCastException e) {
            lastUsedFilenameId = Long.MIN_VALUE + 100;
        }

        SharedPreferences exitState = context.getSharedPreferences(SharedPreferencesFilename, Context.MODE_PRIVATE);
        // TODO check for backendTransitionCompleteKey
        try {
            DiskLock.lock();
            try {
                ObjectInputStream is = new ObjectInputStream(context.getApplicationContext().openFileInput(ActiveListsFilename));
                int items = is.readInt();
                AbstractReceipt receipt;
                for (int i = 0; i < items; i++) {
                    receipt = new AbstractReceipt();
                    receipt.filename = is.readUTF();
                    activeLists.add(receipt);
                    if (i == 0) {
                        try {
                            fullyLoadReceipt(receipt);
                        }
                        catch (IOException e) {
                            Log.e(TAG, "Receipt " + receipt.filename + " was corrupted and could not be read!");
                            activeLists.remove(receipt);
                            i--;
                            items--;
                        }
                    }
                    new DisplayLoaderTask().execute(context.getApplicationContext());
                }
                if (items == 0) {
                    // TODO Might be necesarry to start app with an existing list
                    if (globalPrefs.getBoolean(ReceiptActivity.BackendTransitionCompleteKey, false)) {
//                        receipt = newReceipt();
//                        activeLists.add(receipt);
                    }
                    else {
                        globalPrefs.edit().putBoolean(ReceiptActivity.BackendTransitionCompleteKey, true).apply();
                    }
                }
                is.close();
            }
            catch (EOFException e) {
                if (DEBUG) Log.d(TAG, "Running one-time init due to unexpected EOF!\nActiveLists size is " + activeLists.size());
            }
            catch (FileNotFoundException e) {
                if (DEBUG) Log.d(TAG, "Running one-time init due to FileNotFound!");
            }
            catch (IOException e) {
                e.printStackTrace();
                AbstractReceipt receipt = newReceipt();
                activeLists.add(0, receipt);
            }
        }
        finally {
            DiskLock.unlock();
        }

        // The cleanupGatherTask lists all the receipt files in a background thread
        // then switches to the ui thread to match up found files to active lists
        // finally, it removes those files which were not found in the active lists
        new CleanupGatherTask().execute();

    }

    private void oneTimeInit(ReceiptActivity activity) {
        //This is a first ever instance of the BackendFragment; create the active lists file
        try {
            ObjectOutputStream os = new ObjectOutputStream(activity.getApplicationContext().openFileOutput(ActiveListsFilename, Context.MODE_PRIVATE));

            AbstractReceipt receipt = new AbstractReceipt();
            lastUsedFilenameId += 1;
            PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).edit().putLong(LastUsedFilenameIdKey, lastUsedFilenameId).apply();
            receipt.filename = FilenamePrefix + lastUsedFilenameId;

            os.writeInt(1);
            os.writeUTF(receipt.filename);

            os.close();

            receipt.header = new ReceiptFileHeader();
            activity.requestHeader(receipt);
            activeLists.add(receipt);
            if (DEBUG) Log.d(TAG, "After one-time init, activeLists size is " + activeLists.size());
        }
        catch (IOException ioE) {
            ioE.printStackTrace();
        }
    }

    private void fullyLoadReceipt(AbstractReceipt receipt) throws IOException {
        if (receipt.fullyLoaded) return;
        ObjectInputStream ois = null;

        if (receipt.filename.contains("/")) {
            Log.e(TAG, "File was not saved correctly; it contains a file separator");

            ois = new ObjectInputStream(new FileInputStream(receipt.filename));
            receipt.filename = createFilename();

            return;
        }
        else {
            try {
                ois = new ObjectInputStream(context.openFileInput(receipt.filename));
            }
            catch (Exception e) {
                receipt.header = new ReceiptFileHeader();
                receipt.items = new ArrayList<ItemCollectionFragment.Item>();
                receipt.filename = createFilename();
                return;
            }
        }

        receipt.header = ReceiptFileHeader.inflate(ois);
        receipt.items = new ArrayList<ItemCollectionFragment.Item>();
        for (int j = 0; j < receipt.header.totalItems; j++) {
            receipt.items.add(ItemCollectionFragment.Item.inflate(ois, receipt.header.startingVersion));
        }
        receipt.header.totalItems = receipt.items.size();
        receipt.loadedForDisplay = true;
        receipt.fullyLoaded = true;
        ois.close();
    }

    private void loadReceiptForDisplay(AbstractReceipt receipt) throws IOException {
        if (receipt.fullyLoaded || receipt.loadedForDisplay) return;
        ObjectInputStream ois = receipt.filename.contains("/") ? new ObjectInputStream(new FileInputStream(receipt.filename))
                                    : new ObjectInputStream(context.openFileInput(receipt.filename));
        receipt.header = ReceiptFileHeader.inflate(ois);
        receipt.items = new ArrayList<ItemCollectionFragment.Item>();
        int itemsToLoad = receipt.header.totalItems > 4 ? 4 : receipt.header.totalItems;

        // If the receipt was upgraded, upgrade the items by loading them all
        if (receipt.header.startingVersion != receipt.header.fileVersion) itemsToLoad = receipt.header.totalItems;

        for (int j = 0; j < itemsToLoad; j++) {
            receipt.items.add(ItemCollectionFragment.Item.inflate(ois, receipt.header.startingVersion));
        }
        receipt.loadedForDisplay = true;
        if (receipt.header.startingVersion != receipt.header.fileVersion) receipt.fullyLoaded = true;
        ois.close();
    }

    private void updateReceiptFromActivity(AbstractReceipt receipt, ReceiptActivity activity) {
        activity.requestHeader(receipt);
        receipt.items = activity.getItems();
    }

    static class TaskHelper {
        Context context;
        ArrayList<AbstractReceipt> activeLists;
        static TaskHelper make(Context context, ArrayList<AbstractReceipt> list) {
            TaskHelper t = new TaskHelper();
            t.context = context;
            t.activeLists = new ArrayList<AbstractReceipt>(list);
            return t;
        }
    }

    class SaveStateTask extends AsyncTask<TaskHelper, Void, Void> {

        @Override
        protected Void doInBackground(TaskHelper... arg0) {
            try {
                DiskLock.lock();
                if (DEBUG_SLOW_SAVE) {
                    try {
                        Thread.sleep(SLOW_SAVE_LENGTH);
                    }
                    catch (InterruptedException e) {
                        Log.e(TAG, "Sleeping debug thread was interrupted!");
                        e.printStackTrace();
                    }
                }
                if (DEBUG) Log.d(TAG, "Saving state; ActiveLists size is " + activeLists.size());
                Context context = arg0[0].context;
                ArrayList<AbstractReceipt> activeLists = arg0[0].activeLists;
                try {
                    ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(ActiveListsFilename, Context.MODE_PRIVATE));
                    os.writeInt(activeLists.size());
                    int size = activeLists.size();
                    for (int i = 0; i < size; i++) {
                        os.writeUTF(activeLists.get(i).filename);
                        if (DEBUG_SAVE) {
                            AbstractReceipt receipt = activeLists.get(i);
                            if (DEBUG) Log.d(TAG, "Save scrap from backend. Scrap is : " + (receipt.header.totalItems - receipt.header.itemsCrossed));
                        }
                    }
                    os.close();
                    if (DEBUG) Log.d(TAG, "ActiveLists info was saved successfully!");
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

                if (DEBUG) Log.d(TAG, "State saved successfully!");
            }
            finally {
                DiskLock.unlock();
            }
            return null;
        }

    }

    static class DisplayHelper {
        AbstractReceipt receipt;
        int position;
        static DisplayHelper make(AbstractReceipt receipt, int position) {
            DisplayHelper helper = new DisplayHelper();
            helper.receipt = receipt;
            helper.position = position;
            return helper;
        }
    }

    class DisplayLoaderTask extends AsyncTask <Context, DisplayHelper, Void> {

        ArrayList<AbstractReceipt> lists;

        protected void onPreExecute() {
            lists = new ArrayList<AbstractReceipt>(activeLists);
        }

        @Override
        protected Void doInBackground(Context ... arg0) {
            int i = -1;
            for (AbstractReceipt receipt : lists) {
                try {
                    DiskLock.lock();
                    if (DEBUG_SLOW_SAVE) {
                        try {
                            Thread.sleep(SLOW_SAVE_LENGTH);
                        }
                        catch (InterruptedException e) {
                            Log.e(TAG, "Sleeping debug thread was interrupted!");
                            e.printStackTrace();
                        }
                    }
                    i++;
                    if (receipt.fullyLoaded || receipt.loadedForDisplay) continue;
                    try {
                        ObjectInputStream is = receipt.filename.contains("/") ? new ObjectInputStream(new FileInputStream(receipt.filename)) :
                                new ObjectInputStream(arg0[0].openFileInput(receipt.filename));
                        receipt.header = ReceiptFileHeader.inflate(is);
                        receipt.items = new ArrayList<ItemCollectionFragment.Item>();
                        int itemsToLoad = receipt.header.totalItems > 4 ? 4 : receipt.header.totalItems;

                        // If the receipt was upgraded, upgrade the items by loading them all
                        if (receipt.header.startingVersion != receipt.header.fileVersion) itemsToLoad = receipt.header.totalItems;

                        for (int j = 0; j < itemsToLoad; j++) {
                            receipt.items.add(ItemCollectionFragment.Item.inflate(is, receipt.header.startingVersion));
                        }
                        receipt.loadedForDisplay = true;
                        if (receipt.header.startingVersion != receipt.header.fileVersion) receipt.fullyLoaded = true;

                        if (receipt.header.totalItems <= 4) receipt.fullyLoaded = true;

                        publishProgress(DisplayHelper.make(receipt, i));
                        is.close();
                    }
                    catch (Exception e) {
                        receipt.header = new ReceiptFileHeader();
                        e.printStackTrace();
                    }
                }
                finally {
                    DiskLock.unlock();
                }
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(DisplayHelper ... progress) {
            if (activeLists.get(progress[0].position).fullyLoaded || activeLists.get(progress[0].position).loadedForDisplay) {
                for (OnLoadProgressListener onLoadProgressListener : listeners) {
                    onLoadProgressListener.onProgressUpdate();
                }
            }
            else {
                activeLists.set(progress[0].position, progress[0].receipt);

                for (OnLoadProgressListener onLoadProgressListener : listeners) {
                    onLoadProgressListener.onProgressUpdate();
                }
            }
        }

        @Override
        protected void onPostExecute(Void result) {
        }

    }

    class ReceiptSaver extends AsyncTask<Context, Void, Void> {

        AbstractReceipt receipt;
        ReceiptSaver(AbstractReceipt receipt) {
            this.receipt = receipt;
        }

        @Override
        protected Void doInBackground(Context...contexts) {
            if (!receipt.fullyLoaded) return null; //Guard against bad save requests

            try {
                DiskLock.lock(); //Might not be needed TODO test
                if (DEBUG_SLOW_SAVE) {
                    try {
                        Thread.sleep(SLOW_SAVE_LENGTH);
                    }
                    catch (InterruptedException e) {
                        Log.e(TAG, "Sleeping debug thread was interrupted!");
                        e.printStackTrace();
                    }
                }

                Context context = contexts[0];
                synchronized(receipt.filename) {
                    try {
                        ObjectOutputStream os = new ObjectOutputStream(context.openFileOutput(receipt.filename, Context.MODE_PRIVATE));
                        receipt.header.flatten(os);
                        for (int i = 0; i < receipt.header.totalItems; i++) {
                            receipt.items.get(i).flatten(os, receipt.header.fileVersion);
                        }
                        os.close();
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
            finally {
                DiskLock.unlock();
            }
            return null;
        }
    }

    private class CleanupGatherTask extends AsyncTask<Void, Void, ArrayList<String>> {

        @Override
        protected ArrayList<String> doInBackground(Void[] objects) {
            File fileDir = context.getFilesDir();
            FilenameFilter filter = new FilenameFilter() {
                @Override
                public boolean accept(File file, String s) {
                    return s.startsWith(FilenamePrefix) && !s.equals(ActiveListsFilename);
                }
            };

            return new ArrayList<String>(Arrays.asList(fileDir.list(filter)));
        }

        protected void onPostExecute(ArrayList<String> filenames) {
            if (filenames == null) return;
            //noinspection unchecked
            for (int i = 0; i < filenames.size(); i++) {
                String filename = filenames.get(i);
                for (AbstractReceipt receipt : activeLists) {
                    if (filename.equals(receipt.filename)) {
                        filenames.remove(filename);
                        i--;
                    }
                }
            }

            if (filenames.size() > 0) {
                Log.i(TAG, "About to delete " + filenames.size() + " junk files.");
                //noinspection unchecked
                new CleanupTask().execute(filenames);
            }
            else if (DEBUG_JUNK_FILES) Log.d(TAG, "No junk files were found.");
        }

    }

    public void fuckingClear() {
        activeLists.clear();
    }

    private class CleanupTask extends AsyncTask<ArrayList<String>, Void, Void> {

        @Override
        protected Void doInBackground(ArrayList<String>[] filenames) {
            for (String filename : filenames[0]) {
                if (DEBUG_JUNK_FILES) {
                    Log.d(TAG, "Deleting junk file: " + filename);
                }
                context.deleteFile(filename);
            }
            return null;
        }
    }


}
