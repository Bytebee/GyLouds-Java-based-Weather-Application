package com.example.weatherapplication;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class StreakManager {

    private static final String PREFS_NAME = "AppPrefs";
    private static final String LAST_OPEN_DATE = "LastOpenDate";
    private static final String STREAK_COUNT = "StreakCount";

    public static void checkAndUpdateStreak(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        String todayDate = dateFormat.format(new Date());

        String lastOpenDate = prefs.getString(LAST_OPEN_DATE, "");
        int currentStreak = prefs.getInt(STREAK_COUNT, 0);

        if (!todayDate.equals(lastOpenDate)) {
            Calendar yesterday = Calendar.getInstance();
            yesterday.add(Calendar.DAY_OF_YEAR, -1);
            String yesterdayDate = dateFormat.format(yesterday.getTime());

            if (lastOpenDate.equals(yesterdayDate)) {
                currentStreak++;
            } else {
                currentStreak = 1;
            }

            editor.putString(LAST_OPEN_DATE, todayDate);
            editor.putInt(STREAK_COUNT, currentStreak);
            editor.apply();
        }
    }

    public static int getCurrentStreak(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getInt(STREAK_COUNT, 0);
    }
}
