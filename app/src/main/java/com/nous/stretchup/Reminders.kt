package com.nous.stretchup

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nous.stretchup.data.Prefs
import com.nous.stretchup.session.Slot
import java.util.Calendar

/**
 * Everything to do with the two daily nudges: scheduling them with AlarmManager,
 * building the notification, and keeping the alarm alive across reboots.
 *
 * One alarm per slot, each rescheduled by [scheduleDaily] after it fires. No network
 * access is involved at any point — the notification is entirely local.
 */
object Reminders {

    const val CHANNEL_ID = "stretch_reminders"
    const val EXTRA_SLOT = "slot"
    const val EXTRA_AUTOSTART = "autostart"
    private const val SNOOZE_MINUTES = 10

    fun slotOf(name: String?): Slot = if (name == Slot.BED.name) Slot.BED else Slot.WAKE

    private fun requestCode(slot: Slot, snooze: Boolean = false): Int =
        when (slot) {
            Slot.WAKE -> if (snooze) 2001 else 1001
            Slot.BED -> if (snooze) 2002 else 1002
        }

    private fun alarmIntent(context: Context, slot: Slot, snooze: Boolean): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = if (snooze) REMIND else REMIND
            putExtra(EXTRA_SLOT, slot.name)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(slot, snooze),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    const val REMIND = "com.nous.stretchup.REMIND"
    const val SNOOZE = "com.nous.stretchup.SNOOZE"

    /**
     * Android 12+ can withhold exact alarms. When it has, every schedule call silently
     * degrades to an inexact window, so the home screen surfaces this to offer the
     * settings page rather than letting nudges arrive late with no explanation.
     */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(AlarmManager::class.java) ?: return false
        return am.canScheduleExactAlarms()
    }

    /** Re-arm both daily reminders from stored settings. Safe to call any time. */
    fun scheduleAll(context: Context) {
        val prefs = Prefs(context)
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(alarmIntent(context, Slot.WAKE, false))
        am.cancel(alarmIntent(context, Slot.BED, false))
        if (prefs.morningOn) scheduleDaily(context, Slot.WAKE, prefs.wakeMinutes)
        if (prefs.eveningOn) scheduleDaily(context, Slot.BED, prefs.bedMinutes)
    }

    fun scheduleDaily(context: Context, slot: Slot, minutesOfDay: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = nextOccurrence(minutesOfDay)
        val pi = alarmIntent(context, slot, false)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            // Inexact fallback: Android may drift this by a few minutes.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun snooze(context: Context, slot: Slot) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val trigger = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        val pi = alarmIntent(context, slot, snooze = true)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    /**
     * The next time-of-day occurrence, as an epoch millis. Always in the future: if the
     * time has already passed today, this returns tomorrow's.
     */
    fun nextOccurrence(minutesOfDay: Int): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, minutesOfDay / 60)
            set(Calendar.MINUTE, minutesOfDay % 60)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1)
        return target.timeInMillis
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Stretch reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Morning and bedtime stretch nudges"
            enableVibration(true)
            setSound(null, null)
        }
        nm.createNotificationChannel(channel)
    }

    fun notify(context: Context, slot: Slot) {
        ensureChannel(context)
        val prefs = Prefs(context)
        if (!prefs.let { if (slot == Slot.WAKE) it.morningOn else it.eveningOn }) return

        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_SLOT, slot.name)
            putExtra(EXTRA_AUTOSTART, true)
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            requestCode(slot),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            requestCode(slot, snooze = true) + 100,
            Intent(context, ReminderReceiver::class.java).apply {
                action = SNOOZE
                putExtra(EXTRA_SLOT, slot.name)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = when (slot) {
            Slot.WAKE -> "Good morning — five minutes to loosen up"
            Slot.BED -> "Wind down — five minutes before bed"
        }
        val body = when (slot) {
            Slot.WAKE -> "Stand up and shake the night out. Five stretches, standing."
            Slot.BED -> "Five gentle standing stretches, then straight to bed."
        }

        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_stretch)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .addAction(0, "Start 5 min", contentIntent)
            .addAction(0, "Snooze $SNOOZE_MINUTES min", snoozeIntent)
            .build()

        runCatching {
            // Silently no-op when the user has switched notifications off: posting would
            // throw on Android 13+, and the reminder is not worth a crash.
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                NotificationManagerCompat.from(context).notify(requestCode(slot), n)
            }
        }
    }
}
