package com.nous.stretchup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager forgets everything across a reboot or an app update. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.scheduleAll(context)
    }
}
