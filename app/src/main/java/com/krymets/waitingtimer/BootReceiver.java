package com.krymets.waitingtimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AppDb.Session active = AppDb.get(context).getActiveSession();
        if (active != null) {
            NotificationHelper.onSessionStartedOrChanged(context, active);
        }
        WaitingWidgetProvider.updateAll(context);
    }
}
