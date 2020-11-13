package com.BogdanMihaiciuc.receipt;

import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.util.Log;

import com.BogdanMihaiciuc.receipt.ItemCollectionFragment.Tag;

import java.util.ArrayList;

public class TagStorage {

    final static boolean DEBUG_SAVE = true;

    final static int NoColor = 0;

    final static int MaximumTags = 18;

    final static String LastUsedTagUIDKey = "lastUsedTagUID";

    static interface OnTagsChangedListener {
        public void onTagsChanged();
    }

    private static int lastUsedTagUID = 0;

    static ArrayList<Integer> colors;
    static ArrayList<Tag> tags;
    static ArrayList<OnTagsChangedListener> listeners = new ArrayList<OnTagsChangedListener>();

    static boolean loaded = false;

    // This is necessary for the app to function and must happen at startup
    static void loadTags() {
        if (loaded) return;

        tags = new ArrayList<Tag>();

        synchronized (Receipt.DatabaseLock) {
            SQLiteDatabase db = null;
            try {
                db = Receipt.DBHelper.getReadableDatabase();

                if (db.getVersion() != Receipt.DatabaseVersion) return;

                Cursor databaseTags = db.query(Receipt.DBTagsTable,
                        Receipt.DBAllTagColumns, null, null, null, null, null);

                if (databaseTags.getCount() > 0) {
                    databaseTags.moveToFirst();
                    do {
                        Tag tag = new Tag();
                        tag.color = databaseTags.getInt(Receipt.DBColorKeyIndex);
                        tag.name = databaseTags.getString(Receipt.DBNameKeyIndex);
                        tag.tagUID = databaseTags.getInt(Receipt.DBTagUIDKeyIndex);
                        if (tag.tagUID > lastUsedTagUID) lastUsedTagUID = tag.tagUID;
                        tags.add(tag);
                    } while (databaseTags.moveToNext());
                }
            }
            finally {
                if (db != null) {
                    db.close();
                }

                loaded = true;
            }
        }
    }

    static ArrayList<ItemCollectionFragment.Tag> getDefaultTags(Resources res) {

        getAllAvailableColors(res);

        return tags;
    }

    static Tag findTag(String filter) {
        for (Tag tag : getDefaultTags(null)) {
            if (tag.name.toLowerCase().startsWith(filter)) return tag;
        }

        return null;
    }

    static Tag findExactTag(String filter) {
        for (Tag tag : getDefaultTags(null)) {
            if (tag.name.equalsIgnoreCase(filter)) return tag;
        }

        return null;
    }

    static int getFilteredTags(String filter, ArrayList<Tag> outTags) {
        int exactMatchLocation = -1;
        int i = 0;
        for (Tag tag : getDefaultTags(null)) {
            if (tag.name.toLowerCase().startsWith(filter)) {
                if (tag.name.equalsIgnoreCase(filter)) {
                    exactMatchLocation = i;
                }
                outTags.add(tag);
            }

            i++;
        }

        return exactMatchLocation;
    }

    static void addTag(Tag tag) {
//        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
//        if (lastUsedTagUID == 0) {
//            lastUsedTagUID = sharedPreferences.getInt(LastUsedTagUIDKey, 0);
//        }

        lastUsedTagUID++;
        tag.tagUID = lastUsedTagUID;
        if (DEBUG_SAVE) Log.d("TagStorage", "Saving tag with UID " + lastUsedTagUID);
        tags.add(tag);

//        sharedPreferences.edit().putInt(LastUsedTagUIDKey, lastUsedTagUID).apply();

        final Tag savedTag = tag;
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void ... params) {
                synchronized (Receipt.DatabaseLock) {
                    SQLiteDatabase db = null;
                    try {
                        db = Receipt.DBHelper.getWritableDatabase();

                        ContentValues tagValues = new ContentValues(3);
                        tagValues.put(Receipt.DBUIDKey, savedTag.tagUID);
                        tagValues.put(Receipt.DBNameKey, savedTag.name);
                        tagValues.put(Receipt.DBColorKey, savedTag.color);

                        db.insert(Receipt.DBTagsTable, null, tagValues);
                    }
                    finally {
                        if (db != null) {
                            db.close();
                        }
                    }
                }
                return null;
            }
        }.execute();
    }


    // TODO Only used when importing entire history
    static void addTagToDatabase(Tag savedTag, SQLiteDatabase db) {

        lastUsedTagUID++;
        savedTag.tagUID = lastUsedTagUID;
        if (DEBUG_SAVE) Log.d("TagStorage", "Saving tag with UID " + lastUsedTagUID);
        tags.add(savedTag);

        ContentValues tagValues = new ContentValues(3);
        tagValues.put(Receipt.DBUIDKey, savedTag.tagUID);
        tagValues.put(Receipt.DBNameKey, savedTag.name);
        tagValues.put(Receipt.DBColorKey, savedTag.color);

        db.insert(Receipt.DBTagsTable, null, tagValues);
    }

    static void removeTag(Tag tag) {
        final Tag RemovedTag = tag;
        tags.remove(tag);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void ... params) {
                synchronized (Receipt.DatabaseLock) {
                    SQLiteDatabase db = null;
                    try {
                        db = Receipt.DBHelper.getWritableDatabase();

                        db.execSQL("delete from " + Receipt.DBTagsTable + " where " + Receipt.DBUIDKey + " = " + RemovedTag.tagUID);
                        db.execSQL("delete from " + Receipt.DBTagConnectionsTable + " where " + Receipt.DBTagConnectionUIDKey + " = " + RemovedTag.tagUID);

                    }
                    finally {
                        if (db != null) {
                            db.close();
                        }
                    }
                }
                return null;
            }
        }.execute();
    }

    static Tag resolveTag(int color, String name) {
        for (Tag tag : tags) {
            if (tag.color == color && tag.name.equals(name)) return tag;
        }

        return null;
    }

    static Tag findTagWithUID(int uid) {

        if (tags == null)  {
            Log.e("TagStorage", "Attempting to resolve tag before the tags have been loaded.");
            return null;
        }

        for (Tag tag : tags) {
            if (tag.tagUID == uid) return tag;
        }

        Log.e("TagStorage", "Did not find tag with UID " + uid);

        return null;
    }

    static Tag findTagWithColor(int color) {
        if (color == -1) return null;

        for (Tag tag : tags) {
            if (tag.color == color) return tag;
        }

        return null;
    }

    static int[] colorMapping = new int[] {
            0, 1, 8, 9, 10, 5,
            6, 7, 2, 3, 4, 11,
            12, 13, 14, 15, 16, 17
    };

    static int[] colorStrength = new int[] {
            100000, 10000, 1000, 100, 10, 1,
            200000, 20000, 2000, 200, 20, 2,
            300000, 30000, 3000, 300, 30, 3
    };

    final static int TextColorWhite = 0xFFFFFFFF;
    final static int TextColorBlack = 0x88000000;

    static int[] requiredTextColor = new int[] {
            TextColorWhite, TextColorBlack, TextColorBlack, TextColorBlack, TextColorBlack, TextColorBlack,
            TextColorWhite, TextColorBlack, TextColorWhite, TextColorWhite, TextColorWhite, TextColorBlack,
            TextColorWhite, TextColorWhite, TextColorWhite, TextColorWhite, TextColorWhite, TextColorWhite
    };

    static ArrayList<Integer> getAllAvailableColors(Resources res) {
        if (colors == null) {
            colors = new ArrayList<Integer>(18);

            colors.add(res.getColor(R.color.TagLightRed));
            colors.add(res.getColor(R.color.TagLightOrange));
            colors.add(res.getColor(R.color.TagLightGreen));
            colors.add(res.getColor(R.color.TagLightBlue));
            colors.add(res.getColor(R.color.TagLightPurple));
            colors.add(res.getColor(R.color.TagWhite));

            colors.add(res.getColor(R.color.TagRed));
            colors.add(res.getColor(R.color.TagOrange));
            colors.add(res.getColor(R.color.TagGreen));
            colors.add(res.getColor(R.color.TagBlue));
            colors.add(res.getColor(R.color.TagPurple));
            colors.add(res.getColor(R.color.TagGray));

            colors.add(res.getColor(R.color.TagDarkRed));
            colors.add(res.getColor(R.color.TagDarkOrange));
            colors.add(res.getColor(R.color.TagDarkGreen));
            colors.add(res.getColor(R.color.TagDarkBlue));
            colors.add(res.getColor(R.color.TagDarkPurple));
            colors.add(res.getColor(R.color.TagBlack));
        }

        return colors;
    }

    static int getAllAvailableColorCount() {
        return 18;
    }

    // The color strength is used in sorting
    static int getColorStrength(int color) {
        int colorIndex = colors.indexOf(color);
        if (colorIndex == -1) return 0;
        return colorStrength[colorIndex];
    }

    static int getSuggestedTextColor(int color) {
        int colorIndex = colors.indexOf(color);
        if (colorIndex == -1) return 0;
        return requiredTextColor[colorIndex];
    }

    // This MUST be called after initialization
    static boolean isColorAvailable(int color) {
        for (Tag tag : tags) {
            if (tag.color == color) return false;
        }
        return true;
    }

    static int getNextAvailableColor() {
        boolean available;
        for (int i = 0; i < colorMapping.length; i++) {
            int color = colors.get(colorMapping[i]);
            available = true;
            for (Tag tag : tags) {
                if (tag.color == color) available = false;
            }
            if (available) return color;
        }

        return NoColor;
    }

    static boolean canCreateTags() {
        return tags.size() < MaximumTags;
    }

    static int generateTagUID() {
        return ++lastUsedTagUID;
    }

    static void addTagToArray(Tag tag, ArrayList<Tag> array) {
        if (array.contains(tag)) return;
        int strength = getColorStrength(tag.color);
        int position = -1;
        for (int i = 0; i < array.size(); i++) {
            if (strength < getColorStrength(array.get(i).color)) {
                position = i;
                break;
            }
        }

        if (position == -1) position = array.size();

        array.add(position, tag);
    }

}
