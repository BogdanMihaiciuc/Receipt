package com.BogdanMihaiciuc.receipt;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Log;
import android.util.SparseArray;

import com.BogdanMihaiciuc.util.*;

import java.io.EOFException;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import static com.BogdanMihaiciuc.receipt.BackendStorage.AbstractReceipt;

public class ReceiptCoder {

    final static String TAG = ReceiptCoder.class.getName();

    final static boolean DEBUG_VERBOSE_DECODE = false;

    public final static int CurrentVersion = 1;
    public final static int CurrentRevision = 1;

    public final static int ErrorNone = 0;
    public final static int ErrorUnknownFile = 1;
    public final static int ErrorIOException = 2;
    public final static int ErrorEarlyEOF = 3;
    public final static int ErrorNewerVersion = 4;
    public final static int ErrorNotFound = 5;

    private static String ErrorTitle;
    private static SparseArray<String> ErrorDescriptions = new SparseArray<String>();

    public static class FileError {
        int code;
        String title;
        String description;
    }

    public static class SharedHeader {
        final static byte[] Code = {'R', 'C', 'P', 'T'};
        public int version;
        public int revision;

        private SharedHeader() {
            version = CurrentVersion;
            revision = CurrentRevision;
        }

        public void flatten(ObjectOutputStream stream) throws IOException {

            stream.writeByte(Code[0]);
            stream.writeByte(Code[1]);
            stream.writeByte(Code[2]);
            stream.writeByte(Code[3]);

            stream.writeInt(version);
            stream.writeInt(revision);

        }

        private static boolean isReceipt(byte code[]) {
            for (int i = 0; i < 4; i++) {
                if (code[i] != Code[i]) return false;
            }

            return true;
        }

        public static SharedHeader inflate(ObjectInputStream stream, FileError error) {
            byte[] code = new byte[4];

            try {
                stream.read(code);
            }
            catch (IOException e) {
                setError(error, ErrorIOException);
                return null;
            }

            if (!isReceipt(code)) {
                setError(error, ErrorUnknownFile);
                return null;
            }

            SharedHeader header = new SharedHeader();
            try {
                header.version = stream.readInt();
                header.revision = stream.readInt();
            }
            catch (IOException e) {
                setError(error, ErrorIOException);
                return null;
            }

            if (header.version > CurrentRevision) {
                setError(error, ErrorNewerVersion);
                return null;
            }

            return header;
        }

    }

    private static void clearError(FileError target) {
        target.code = ErrorNone;
    }

    private static void setError(FileError target, int code) {
        if (target == null) return;

        target.code = code;
        target.description = ErrorDescriptions.get(code);
        target.title = ErrorTitle;
    }

    private static void copyErrorTo(FileError source, FileError target) {
        if (target == null) return;

        target.code = source.code;
        target.description = source.description;
        target.title = source.title;
    }

    private static ReceiptCoder sharedCoder;

    private Context context;

    public static ReceiptCoder sharedCoder(Context context) {
        if (sharedCoder == null) {
            sharedCoder = new ReceiptCoder(context.getApplicationContext());
        }

        return sharedCoder;
    }

    private ReceiptCoder(Context context) {
        this.context = context;

        ErrorDescriptions.put(ErrorUnknownFile, context.getString(R.string.FileErrorDescription, context.getString(R.string.ErrorUnknownFile)));
        ErrorDescriptions.put(ErrorIOException, context.getString(R.string.FileErrorDescription, context.getString(R.string.ErrorIOException)));
        ErrorDescriptions.put(ErrorEarlyEOF, context.getString(R.string.FileErrorDescription, context.getString(R.string.ErrorEarlyEOF)));
        ErrorDescriptions.put(ErrorNewerVersion, context.getString(R.string.FileErrorDescription, context.getString(R.string.ErrorNewerVersion)));
        ErrorDescriptions.put(ErrorNotFound, context.getString(R.string.FileErrorDescription, context.getString(R.string.ErrorNotFound)));

        ErrorTitle = context.getString(R.string.FileErrorTitle);
    }

    public File createShareableFile(AbstractReceipt receipt) {
        File cacheDir = context.getExternalCacheDir();

        final String PreviousFilename = receipt.filename;

        receipt.filename = "Receipt-";


        try {
            File file = File.createTempFile(receipt.filename, ".receipt", cacheDir);
            file.setReadable(true, false);
            receipt.filename = file.getAbsolutePath();
            ObjectOutputStream os = new ObjectOutputStream(new FileOutputStream(file));
            new SharedHeader().flatten(os);
            receipt.header.flatten(os);

            if (receipt.header.totalItems != receipt.items.size()) {
                if (DEBUG_VERBOSE_DECODE) Log.e(TAG, "Mismatch between header(" + receipt.header.totalItems + ") and array(" + receipt.items.size() + ")");
            }
            if (DEBUG_VERBOSE_DECODE) Log.d(TAG, "Flattened header.");

            for (int i = 0; i < receipt.header.totalItems; i++) {
                receipt.items.get(i).flatten(os, ItemCollectionFragment.Item.CurrentVersionUID, true);
                if (DEBUG_VERBOSE_DECODE) Log.d(TAG, "Flattened item: " + receipt.items.get(i).name);
            }
            os.close();
            return file;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        finally {
            receipt.filename = PreviousFilename;
        }

        return null;
    }

    public File createShareableFile(long databaseUID) {
        AbstractReceipt receipt;
        synchronized (Receipt.DatabaseLock) {
            SQLiteDatabase database = Receipt.DBHelper.getReadableDatabase();

            Cursor result = Receipt.queryDatabase(database)
                                   .selectColumns(Receipt.DBAllReceiptColumns)
                                   .fromTable(Receipt.DBReceiptsTable)
                                   .where(Receipt.DBFilenameIdKey + " = " + databaseUID)
                                   .execute();

            if (result.getCount() == 0) {
                Log.e(TAG, "Bad UID; can't share!");
            }
            else {
                result.moveToFirst();
            }

            receipt = HistoryActivity.convertDatabaseEntryToAbstractReceipt(database, result);
        }

        return createShareableFile(receipt);
    }

//    public void shareFileFromActivity(File file, Activity activity) {
//        String mimeType = Receipt.DriveMimeType;
//        Intent intent = new Intent();
//
//        intent.setAction(android.content.Intent.ACTION_SEND);
//        intent.setType(mimeType);
//        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
//        activity.startActivity(Intent.createChooser(intent, "Share"));
//    }

    public void shareFileFromAnchorInActivity(File file, Popover.AnchorProvider anchor, Activity activity) {

        String mimeType = Receipt.DriveMimeType;
        Intent intent = new Intent();

        intent.setAction(android.content.Intent.ACTION_SEND);
        intent.setType(mimeType);
        intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        IntentListPopover popover = new IntentListPopover(anchor, intent);
        popover.getHeader().setTitle(ReceiptActivity.titleFormattedString("Share"));
        popover.show(activity);

    }

    public AbstractReceipt decodeFile(Intent intent, FileError error) {
        clearError(error);

        ObjectInputStream is;
        try {
            is = new ObjectInputStream(context.getContentResolver().openInputStream(intent.getData()));
        }
        catch (FileNotFoundException e) {
            setError(error, ErrorNotFound);
            return null;
        }
        catch (IOException e) {
            setError(error, ErrorIOException);
            return null;
        }

        FileError headerError = new FileError();
        clearError(headerError);
        SharedHeader.inflate(is, headerError);
        if (headerError.code != ErrorNone) {
            Log.e(TAG, "Unable to decode file; unrecognized header.");
            copyErrorTo(headerError, error);
            return null;
        }

        AbstractReceipt receipt = new AbstractReceipt();

        try {
            receipt.header = BackendStorage.ReceiptFileHeader.inflate(is);
        }
        catch (IOException e) {
            setError(error, ErrorIOException);
            return null;
        }

        if (DEBUG_VERBOSE_DECODE) {
            Log.d(TAG, "Decoded header version: " + receipt.header.startingVersion + "; brought to version: " + receipt.header.fileVersion);
            Log.d(TAG, "Items: " + receipt.header.totalItems + ", totalling: " + receipt.header.total);
        }

        receipt.items = new ArrayList<ItemCollectionFragment.Item>();

        try {
            for (int i = 0; i < receipt.header.totalItems; i++) {
                receipt.items.add(ItemCollectionFragment.Item.inflateFromExternalSource(is, receipt.header.startingVersion));

                if (DEBUG_VERBOSE_DECODE) {
                    Log.d(TAG, "Decoded item: " + receipt.items.get(i).name);
                }
            }
        }
        catch (EOFException e) {

            if (DEBUG_VERBOSE_DECODE) {
                Log.e(TAG, "EOF exception, unable to continue.");
            }
            setError(error, ErrorEarlyEOF);
        }
        catch (IOException e) {

            if (DEBUG_VERBOSE_DECODE) {
                Log.e(TAG, "IO exception, unable to continue.");
            }
            setError(error, ErrorIOException);
        }

        return receipt;
    }

}
