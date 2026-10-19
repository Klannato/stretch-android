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

    fun preview(slot: Slot): List<Block> = SessionPlanner.plan(library, prefs.cursor, slot).first

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

    fun backBlock() {
        val current = SessionPlanner.blockIndexFor(elapsed)
        elapsed = ((current - 1).coerceAtLeast(0)) * SessionPlanner.BLOCK_SECONDS.toFloat()
        finished = false
    }
}
