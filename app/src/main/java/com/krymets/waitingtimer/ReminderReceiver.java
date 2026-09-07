package com.krymets.waitingtimer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ReminderReceiver extends BroadcastReceiver {
    public static final String ACTION_REMIND = "com.krymets.waitingtimer.REMIND";
    public static final String ACTION_STILL_WAITING = "com.krymets.waitingtimer.STILL_WAITING";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        if (ACTION_REMIND.equals(intent.getAction())) {
            NotificationHelper.showReminder(context);
        } else if (ACTION_STILL_WAITING.equals(intent.getAction())) {
            NotificationHelper.dismissReminder(context);
        }
    }
}
