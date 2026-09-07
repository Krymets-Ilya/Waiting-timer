package com.krymets.waitingtimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WaitActionReceiver extends BroadcastReceiver {
    public static final String ACTION_START = "com.krymets.waitingtimer.START";
    public static final String ACTION_STOP = "com.krymets.waitingtimer.STOP";
    public static final String ACTION_DATA_CHANGED = "com.krymets.waitingtimer.DATA_CHANGED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        AppDb db = AppDb.get(context);
        if (ACTION_START.equals(intent.getAction())) {
            db.startSession();
            AppDb.Session active = db.getActiveSession();
            NotificationHelper.onSessionStartedOrChanged(context, active);
        } else if (ACTION_STOP.equals(intent.getAction())) {
            db.stopActive();
            NotificationHelper.clearAll(context);
        } else {
            return;
        }
        WaitingWidgetProvider.updateAll(context);
        context.sendBroadcast(new Intent(ACTION_DATA_CHANGED).setPackage(context.getPackageName()));
    }
}
