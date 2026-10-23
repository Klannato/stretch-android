package com.nous.stretchup

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.nous.stretchup.data.Prefs
import com.nous.stretchup.model.Stretch
import com.nous.stretchup.model.StretchLibrary
import com.nous.stretchup.session.Block
import com.nous.stretchup.session.SessionPlanner
import com.nous.stretchup.session.Slot

/**
 * Holds the settings and the live session state for the whole activity.
 *
 * It lives in a ViewModel for two reasons: rotating the device must not restart a
 * five-minute stretch, and the settings screen and the session screen both need the
 * same AlarmManager side effects when a time changes.
 *
 * All persisted values go through [Prefs], which is synchronous — reminders are read
 * from a BroadcastReceiver that has no lifecycle in which to collect a Flow.
 */
class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = Prefs(app)

    var library by mutableStateOf<List<Stretch>>(emptyList())
        private set

    var wakeMinutes by mutableStateOf(prefs.wakeMinutes)
        private set
    var bedMinutes by mutableStateOf(prefs.bedMinutes)
        private set
    var morningOn by mutableStateOf(prefs.morningOn)
        private set
    var eveningOn by mutableStateOf(prefs.eveningOn)
        private set
    var sessionsDone by mutableStateOf(prefs.sessionsDone)
        private set

    /**
     * Bumped when imported footage changes. The session screen keys on it so a clip
     * imported while a session is paused is picked up on the next block.
     */
    var videosVersion by mutableStateOf(0)
        private set

    fun videosChanged() {
        videosVersion += 1
    }

    // Session state
    var plan by mutableStateOf<List<Block>>(emptyList())
        private set
    var slot by mutableStateOf(Slot.WAKE)
        private set
    var elapsed by mutableStateOf(0f)
        private set
    var running by mutableStateOf(false)
        private set
    var inSession by mutableStateOf(false)
        private set
    var finished by mutableStateOf(false)
        private set

    init {
        library = StretchLibrary.load(app).map { StretchLibrary.sanitizePoses(it) }
    }

    // ---- settings ----
    // Each setter writes through to prefs and re-arms the alarms immediately, so the
    // change takes effect without needing an app restart.

    fun setWake(minutes: Int) {
        wakeMinutes = minutes
        prefs.wakeMinutes = minutes
        Reminders.scheduleAll(getApplication())
    }

    fun setBed(minutes: Int) {
        bedMinutes = minutes
        prefs.bedMinutes = minutes
        Reminders.scheduleAll(getApplication())
    }

    fun setMorning(on: Boolean) {
        morningOn = on
        prefs.morningOn = on
        Reminders.scheduleAll(getApplication())
    }

    fun setEvening(on: Boolean) {
        eveningOn = on
        prefs.eveningOn = on
        Reminders.scheduleAll(getApplication())
    }

    // ---- session ----

    /**
     * The five stretches that would play now, without consuming the rotation cursor.
     * Used by the home screen so the list it shows matches the session it starts.
     */
    fun preview(slot: Slot): List<Block> = SessionPlanner.plan(library, prefs.cursor, slot).first

    /**
     * Builds a fresh five-block plan and advances the rotation cursor, so the next
     * session opens on a different stretch.
     */
    fun startSession(target: Slot) {
        val (blocks, nextCursor) = SessionPlanner.plan(library, prefs.cursor, target)
        if (blocks.isEmpty()) return
        plan = blocks
        prefs.cursor = nextCursor
        slot = target
        elapsed = 0f
        finished = false
        running = true
        inSession = true
    }

    fun endSession() {
        inSession = false
        running = false
        elapsed = 0f
        finished = false
    }

    fun togglePause() {
        if (finished) return
        running = !running
    }

    /**
     * Advances the session clock. Deliberately takes a delta rather than reading the
     * wall clock: pausing is then just "stop calling tick", and a block boundary can
     * never be skipped because a frame took too long.
     */
    fun tick(delta: Float) {
        if (!running || finished) return
        val next = elapsed + delta
        if (next >= SessionPlanner.TOTAL_SECONDS) {
            elapsed = SessionPlanner.TOTAL_SECONDS.toFloat()
            running = false
            finished = true
            sessionsDone += 1
            prefs.sessionsDone = sessionsDone
        } else {
            elapsed = next
        }
    }

    /** Jumps to the start of the next block, finishing the session if this was the last. */
    fun skipBlock() {
        val current = SessionPlanner.blockIndexFor(elapsed)
        val nextStart = (current + 1) * SessionPlanner.BLOCK_SECONDS
        if (nextStart >= SessionPlanner.TOTAL_SECONDS) {
            elapsed = SessionPlanner.TOTAL_SECONDS.toFloat()
            running = false
            finished = true
            sessionsDone += 1
            prefs.sessionsDone = sessionsDone
        } else {
            elapsed = nextStart.toFloat()
        }
    }

    /** Jumps to the start of the previous block; no-op on the first block. */
    fun backBlock() {
        val current = SessionPlanner.blockIndexFor(elapsed)
        elapsed = ((current - 1).coerceAtLeast(0)) * SessionPlanner.BLOCK_SECONDS.toFloat()
        finished = false
    }
}
