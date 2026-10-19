package com.nous.stretchup.data

import android.content.Context

/** Small synchronous store: reminders must be readable from a BroadcastReceiver. */
class Prefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("stretch", Context.MODE_PRIVATE)

    var wakeMinutes: Int
        get() = sp.getInt(KEY_WAKE, 7 * 60)
        set(value) { sp.edit().putInt(KEY_WAKE, value).apply() }

    var bedMinutes: Int
        get() = sp.getInt(KEY_BED, 22 * 60 + 30)
        set(value) { sp.edit().putInt(KEY_BED, value).apply() }

    var morningOn: Boolean
        get() = sp.getBoolean(KEY_MORNING, true)
        set(value) { sp.edit().putBoolean(KEY_MORNING, value).apply() }

    var eveningOn: Boolean
        get() = sp.getBoolean(KEY_EVENING, true)
        set(value) { sp.edit().putBoolean(KEY_EVENING, value).apply() }

    /** Rotation pointer: which stretch the next session starts from. */
    var cursor: Int
        get() = sp.getInt(KEY_CURSOR, 0)
        set(value) { sp.edit().putInt(KEY_CURSOR, value).apply() }

    var sessionsDone: Int
        get() = sp.getInt(KEY_DONE, 0)
        set(value) { sp.edit().putInt(KEY_DONE, value).apply() }

    var askedNotifications: Boolean
        get() = sp.getBoolean(KEY_ASKED_NOTIF, false)
        set(value) { sp.edit().putBoolean(KEY_ASKED_NOTIF, value).apply() }

    companion object {
        private const val KEY_WAKE = "wake_minutes"
        private const val KEY_BED = "bed_minutes"
        private const val KEY_MORNING = "morning_on"
        private const val KEY_EVENING = "evening_on"
        private const val KEY_CURSOR = "cursor"
        private const val KEY_DONE = "sessions_done"
        private const val KEY_ASKED_NOTIF = "asked_notifications"
    }
}
