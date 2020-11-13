package com.BogdanMihaiciuc.util;

import java.util.Calendar;

public class Date {

    final static String TAG = Date.class.getName();

    final static int DaysInMonth[] = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};

    private int date;
    private int month;
    private int year;

    Calendar workCalendar;

    private Date() {
        workCalendar = Calendar.getInstance();
        date = workCalendar.get(Calendar.DATE);
        month = workCalendar.get(Calendar.MONTH);
        year = workCalendar.get(Calendar.YEAR);
    }

    private Date(long time) {
        workCalendar = Calendar.getInstance();
        workCalendar.setTimeInMillis(time);
        date = workCalendar.get(Calendar.DATE);
        month = workCalendar.get(Calendar.MONTH);
        year = workCalendar.get(Calendar.YEAR);
    }

    public static Date currentDate() {
        return new Date();
    }

    public static Date dateAtTime(long time) {
        return new Date(time);
    }

    public long getNextDate(int nextDate) {
        if (date < nextDate) {
            if (nextDate > getNumbersOfDaysInMonth()) {
                nextDate = getNumbersOfDaysInMonth();
            }

            workCalendar.clear();
            workCalendar.set(Calendar.YEAR, year);
            workCalendar.set(Calendar.MONTH, month);
            workCalendar.set(Calendar.DATE, nextDate);
        }
        else {
            month++;
            if (month > 11) {
                month = 1;
                year++;
            }
            if (nextDate > getNumbersOfDaysInMonth()) {
                nextDate = getNumbersOfDaysInMonth();
            }

            workCalendar.clear();
            workCalendar.set(Calendar.YEAR, year);
            workCalendar.set(Calendar.MONTH, month);
            workCalendar.set(Calendar.DATE, nextDate);
        }

        return workCalendar.getTimeInMillis();
    }

    public int getNumbersOfDaysInMonth() {
        if (month == 1) {
            return year % 4 == 0 ? 29 : 28;
        }
        return DaysInMonth[month];
    }

}
