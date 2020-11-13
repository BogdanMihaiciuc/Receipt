package com.BogdanMihaiciuc.receipt;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepeatDateStorage {

    final static String RepeatDateKey = "com.BogdanMihaiciuc.receipt.repeatDate";

    final static int MaximumValues = 9;
    final static int BitsPerValue = 6;

    final static long ValueMask = 0x000000000000001FL;
    final static long TypeMask = 0x0000000000000020L;
    final static int TypeShift = 5;

    static class RepeatDate {
        int type;
        int value;

        static RepeatDate dateOfTypeWithValue(int type, int value) {
            RepeatDate date = new RepeatDate();

            date.type = type;
            date.value = value;

            return date;
        }

        static RepeatDate dateWithBits(long bits) {
            return dateWithBitsAtPosition(bits, 0);
        }

        static RepeatDate dateWithBitsAtPosition(long bits, int position) {
            long shiftedBits = bits >>> position;

            return dateOfTypeWithValue((int) ((shiftedBits & TypeMask) >>> TypeShift), (int) (shiftedBits & ValueMask));
        }

        public long bits() {
            return (type << TypeShift) | value;
        }
    }

    private static RepeatDateStorage sharedStorage;
    private static Context applicationContext;

    private long repeatDatesBits;
    private ArrayList<RepeatDate> repeatDates;

    private RepeatDateStorage(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        repeatDatesBits = prefs.getLong(RepeatDateKey, 0);

        repeatDates = unpackRepeatDatesBits();
    }

    /**
     * Retrieves the default shared repeat date storage associated with the current process.
     * @param context The application context from which to get the repeat dates. May be null if the storage has already been initialized
     * @return The shared storage
     * @throws java.lang.NullPointerException if context is null and the shared storage has not already been initialized
     */
    public static RepeatDateStorage sharedStorage(Context context) {
        if (context != null) {
            context = context.getApplicationContext();
            applicationContext = context;
        }

        if (sharedStorage == null) {
            sharedStorage = new RepeatDateStorage(context);
        }

        return sharedStorage;
    }

    public static long bitRepresentationOfRepeatDate(int type, int value) {
        return (type << TypeShift) | value;
    }

    private ArrayList<RepeatDate> unpackRepeatDatesBits() {
        ArrayList<RepeatDate> dates = new ArrayList<RepeatDate>();

        int shift = 0;
        for (int i = 0; i < MaximumValues; i++) {
            RepeatDate date = RepeatDate.dateWithBitsAtPosition(repeatDatesBits, shift);

            if (date.value == 0) {
                break;
            }

            dates.add(date);
            shift += BitsPerValue;
        }

        return dates;
    }

    private long packRepeatDates() {
        long bits = 0L;

        int shift = 0;
        for (RepeatDate date : repeatDates) {
            bits |= date.bits() << shift;

            shift += BitsPerValue;
        }

        return bits;
    }

    public List<RepeatDate> getRepeatDates() {
        return Collections.unmodifiableList(repeatDates);
    }

    public RepeatDate addRepeatDate(int type, int value) {
        RepeatDate date = RepeatDate.dateOfTypeWithValue(type, value);
        repeatDates.add(date);
        packAndSaveRepeatDates();

        return date;
    }

    public void removeRepeatDate(RepeatDate date) {
        repeatDates.remove(date);
        packAndSaveRepeatDates();
    }

    private void packAndSaveRepeatDates() {
        repeatDatesBits = packRepeatDates();
        SharedPreferences.Editor prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext).edit();
        prefs.putLong(RepeatDateKey, repeatDatesBits);
        prefs.apply();
    }

    public boolean canAddRepeatDates() {
        return repeatDates.size() < MaximumValues;
    }

}
