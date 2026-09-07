package com.krymets.waitingtimer;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.SystemClock;

public final class NotificationHelper {
    public static final String CHANNEL_ID = "waiting_active";
    public static final int ONGOING_ID = 101;
    public static final int REMINDER_ID = 102;
    public static final long REMINDER_AFTER_MS = 60 * 60 * 1000L;

    private NotificationHelper() {}

    public static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "Активное ожидание", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Таймер ожидания и напоминание");
            channel.setShowBadge(false);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public static boolean canNotify(Context context) {
        return Build.VERSION.SDK_INT < 33 ||
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    public static void onSessionStartedOrChanged(Context context, AppDb.Session session) {
        ensureChannel(context);
        showOngoing(context, session);
        scheduleReminder(context, session);
    }

    public static void showOngoing(Context context, AppDb.Session session) {
        if (session == null || !canNotify(context)) return;
        Intent open = new Intent(context, MainActivity.class);
        PendingIntent content = PendingIntent.getActivity(context, 200, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(context, WaitActionReceiver.class).setAction(WaitActionReceiver.ACTION_STOP);
        PendingIntent stop = PendingIntent.getBroadcast(context, 201, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String line = session.objectName != null ? session.objectName : "Вы ждёте";
        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Waiting")
                .setContentText(line)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setWhen(session.startedAt)
                .setUsesChronometer(true)
                .addAction(new Notification.Action.Builder(null, "Дождался", stop).build());
        context.getSystemService(NotificationManager.class).notify(ONGOING_ID, builder.build());
    }

    public static void scheduleReminder(Context context, AppDb.Session session) {
        if (session == null) return;
        cancelReminderAlarm(context);
        long elapsedAlready = AppDb.activeDuration(session);
        long delay = Math.max(1000L, REMINDER_AFTER_MS - elapsedAlready);
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + delay, reminderPendingIntent(context));
    }

    public static void showReminder(Context context) {
        AppDb.Session active = AppDb.get(context).getActiveSession();
        if (active == null || AppDb.activeDuration(active) < REMINDER_AFTER_MS - 5000L || !canNotify(context)) return;

        Intent yesIntent = new Intent(context, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_STILL_WAITING);
        PendingIntent yes = PendingIntent.getBroadcast(context, 211, yesIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent stopIntent = new Intent(context, WaitActionReceiver.class).setAction(WaitActionReceiver.ACTION_STOP);
        PendingIntent stop = PendingIntent.getBroadcast(context, 212, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(context, CHANNEL_ID)
                : new Notification.Builder(context);
        builder.setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Всё ещё ждёте?")
                .setContentText("Таймер уже идёт больше часа")
                .setAutoCancel(true)
                .addAction(new Notification.Action.Builder(null, "Да", yes).build())
                .addAction(new Notification.Action.Builder(null, "Дождался", stop).build());
        context.getSystemService(NotificationManager.class).notify(REMINDER_ID, builder.build());
    }

    public static void clearAll(Context context) {
        NotificationManager nm = context.getSystemService(NotificationManager.class);
        nm.cancel(ONGOING_ID);
        nm.cancel(REMINDER_ID);
        cancelReminderAlarm(context);
    }

    public static void dismissReminder(Context context) {
        context.getSystemService(NotificationManager.class).cancel(REMINDER_ID);
    }

    private static PendingIntent reminderPendingIntent(Context context) {
        Intent intent = new Intent(context, ReminderReceiver.class).setAction(ReminderReceiver.ACTION_REMIND);
        return PendingIntent.getBroadcast(context, 210, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelReminderAlarm(Context context) {
        AlarmManager alarm = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarm.cancel(reminderPendingIntent(context));
    }
}
