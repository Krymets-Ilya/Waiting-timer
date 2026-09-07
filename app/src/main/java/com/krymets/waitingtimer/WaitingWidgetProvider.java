package com.krymets.waitingtimer;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

public class WaitingWidgetProvider extends AppWidgetProvider {
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int id : appWidgetIds) updateOne(context, appWidgetManager, id);
    }

    public static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName component = new ComponentName(context, WaitingWidgetProvider.class);
        int[] ids = manager.getAppWidgetIds(component);
        for (int id : ids) updateOne(context, manager, id);
    }

    private static void updateOne(Context context, AppWidgetManager manager, int id) {
        AppDb db = AppDb.get(context);
        AppDb.Session active = db.getActiveSession();
        RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_waiting);

        Intent openIntent = new Intent(context, MainActivity.class);
        PendingIntent open = PendingIntent.getActivity(context, 301, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        rv.setOnClickPendingIntent(R.id.widget_title, open);

        if (active == null) {
            rv.setTextViewText(R.id.widget_title, "WAITING / WASTING");
            rv.setViewVisibility(R.id.widget_object, View.GONE);
            rv.setViewVisibility(R.id.widget_chronometer, View.GONE);
            rv.setViewVisibility(R.id.widget_summary, View.VISIBLE);
            rv.setTextViewText(R.id.widget_summary, "Сегодня " + AppDb.formatDuration(db.todayTotal()));
            rv.setTextViewText(R.id.widget_action, "START");
            Intent start = new Intent(context, WaitActionReceiver.class).setAction(WaitActionReceiver.ACTION_START);
            PendingIntent pi = PendingIntent.getBroadcast(context, 302, start,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.widget_action, pi);
        } else {
            rv.setTextViewText(R.id.widget_title, "ЖДУ");
            rv.setViewVisibility(R.id.widget_object, View.VISIBLE);
            rv.setTextViewText(R.id.widget_object, active.objectName != null ? active.objectName : "Ожидание");
            rv.setViewVisibility(R.id.widget_chronometer, View.VISIBLE);
            long base = SystemClock.elapsedRealtime() - Math.max(0, System.currentTimeMillis() - active.startedAt);
            rv.setChronometer(R.id.widget_chronometer, base, null, true);
            rv.setViewVisibility(R.id.widget_summary, View.GONE);
            rv.setTextViewText(R.id.widget_action, "STOP");
            Intent stop = new Intent(context, WaitActionReceiver.class).setAction(WaitActionReceiver.ACTION_STOP);
            PendingIntent pi = PendingIntent.getBroadcast(context, 303, stop,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            rv.setOnClickPendingIntent(R.id.widget_action, pi);
        }
        manager.updateAppWidget(id, rv);
    }
}
