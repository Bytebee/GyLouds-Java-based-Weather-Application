package com.example.weatherapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra("title");
        String content = intent.getStringExtra("content");

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, "APP_CHANNEL_ID")
                .setSmallIcon(R.drawable.logo)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            notificationManager.notify(1002, builder.build());
        } else {
            Log.e("NotificationReceiver", "Notification permission not granted.");
        }
    }
}
