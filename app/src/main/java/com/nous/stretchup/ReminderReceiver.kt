package com.nous.stretchup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.nous.stretchup.data.Prefs
import com.nous.stretchup.session.Slot

/**
 * Runs when an alarm fires (or when the notification's snooze action is used).
 *
 * Not exported: this receiver only ever answers PendingIntents created by this app,
 * so another app cannot make it post notifications.
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val slot = Reminders.slotOf(intent.getStringExtra(Reminders.EXTRA_SLOT))

        if (intent.action == Reminders.SNOOZE) {
            // One-shot alarm ten minutes out; nothing else to reschedule.
            Reminders.snooze(context, slot)
            return
        }

        Reminders.notify(context, slot)

        // AlarmManager one-shots do not repeat, so every firing has to re-arm the next
        // day's nudge. Read the times synchronously here: a receiver has no lifecycle in
        // which to collect a Flow.
        val prefs = Prefs(context)
        val minutes = if (slot == Slot.WAKE) prefs.wakeMinutes else prefs.bedMinutes
        val enabled = if (slot == Slot.WAKE) prefs.morningOn else prefs.eveningOn
        if (enabled) Reminders.scheduleDaily(context, slot, minutes)
    }
}
