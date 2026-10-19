package com.nous.stretchup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        android.util.Log.i("Stretch", "receiver action=${intent.action} slot=${intent.getStringExtra(Reminders.EXTRA_SLOT)}")
        when (intent.action) {
            Reminders.SNOOZE -> {
                val slot = Reminders.slotOf(intent.getStringExtra(Reminders.EXTRA_SLOT))
                Reminders.snooze(context, slot)
                return
            }
            else -> {
                val slot = Reminders.slotOf(intent.getStringExtra(Reminders.EXTRA_SLOT))
                Reminders.notify(context, slot)
                // AlarmManager one-shots do not repeat: re-arm tomorrow's nudge.
                val prefs = com.nous.stretchup.data.Prefs(context)
                val minutes = if (slot == com.nous.stretchup.session.Slot.WAKE) prefs.wakeMinutes else prefs.bedMinutes
                val enabled = if (slot == com.nous.stretchup.session.Slot.WAKE) prefs.morningOn else prefs.eveningOn
                if (enabled) Reminders.scheduleDaily(context, slot, minutes)
            }
        }
    }
}
