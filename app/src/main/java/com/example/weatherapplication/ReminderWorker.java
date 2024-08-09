package com.example.weatherapplication;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public class ReminderWorker extends Worker {

    private static final String TAG = "ReminderWorker";

    public ReminderWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        sendReminderNotification("GyLouds Reminder", "Don't lose your streak by visiting the app everyday!");
        return Result.success();
    }

    private void sendReminderNotification(String title, String content) {
        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(getApplicationContext());

        if (notificationManager.areNotificationsEnabled()) {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext(), "APP_CHANNEL_ID")
                    .setSmallIcon(R.drawable.logo)
                    .setContentTitle(title)
                    .setContentText(content)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT);

            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        } else {
            Log.e(TAG, "Notification permission not granted.");
        }
    }
}
