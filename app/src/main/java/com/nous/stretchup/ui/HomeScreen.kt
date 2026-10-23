package com.nous.stretchup.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nous.stretchup.AppViewModel
import com.nous.stretchup.session.SessionPlanner
import com.nous.stretchup.session.Slot
import java.util.Calendar

/** Formats minutes-from-midnight as a 12-hour clock string, e.g. 450 -> "7:30 am". */
fun formatClock(minutesOfDay: Int): String {
    val h24 = minutesOfDay / 60
    val m = minutesOfDay % 60
    val suffix = if (h24 < 12) "am" else "pm"
    val h = when {
        h24 % 12 == 0 -> 12
        else -> h24 % 12
    }
    return "$h:${m.toString().padStart(2, '0')} $suffix"
}

/** Formats a countdown as m:ss. */
fun formatSeconds(total: Int): String {
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/** Time-of-day word for the subtitle; purely cosmetic. */
private fun greeting(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Morning"
        hour < 17 -> "Afternoon"
        else -> "Evening"
    }
}

/**
 * Which of the two sets the big button starts. Morning before 3pm, evening after, so
 * opening the app at the right time of day does not need a choice.
 */
private fun primarySlot(): Slot {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (hour < 15) Slot.WAKE else Slot.BED
}

/**
 * The home screen: today's five stretches, the start button, and the two reminder
 * rows. Nothing here touches the network — the only outbound interaction the app has
 * is handing an intent to the system settings pages, and only when the user taps.
 */
@Composable
fun HomeScreen(
    vm: AppViewModel,
    notificationsEnabled: Boolean,
    exactAlarmsOk: Boolean,
    onOpenNotificationSettings: () -> Unit,
    onOpenExactAlarmSettings: () -> Unit,
    onPickTime: (initial: Int, onSet: (Int) -> Unit) -> Unit
) {
    val slot = primarySlot()
    val preview = vm.preview(slot)
    val cs = MaterialTheme.colorScheme
    val otherSlot = if (slot == Slot.WAKE) Slot.BED else Slot.WAKE

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 28.dp, bottom = 28.dp)
    ) {
        Text("Stretch", style = MaterialTheme.typography.displayLarge, color = cs.onBackground)
        Text(
            "${greeting()} — five quiet minutes, standing up.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(Modifier.height(22.dp))

        // ---- today's set ----
        Surface(
            color = cs.surface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (slot == Slot.WAKE) "Morning set" else "Evening set",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            "5 stretches · 1 minute each · no mat",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                    Text(
                        "5:00",
                        style = MaterialTheme.typography.headlineMedium,
                        color = cs.primary
                    )
                }

                Spacer(Modifier.height(14.dp))

                preview.forEachIndexed { index, block ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.labelLarge,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.width(22.dp)
                        )
                        Text(
                            block.stretch.name,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            if (block.stretch.twoSided) "30s / side" else "60s",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Button(
                    onClick = { vm.startSession(slot) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = cs.primary)
                ) {
                    Text(
                        if (slot == Slot.WAKE) "Start morning stretch" else "Start evening stretch",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                TextButton(
                    onClick = { vm.startSession(otherSlot) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        if (otherSlot == Slot.WAKE) "or start the morning set" else "or start the evening set",
                        style = MaterialTheme.typography.labelLarge,
                        color = cs.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ---- reminders ----
        Surface(
            color = cs.surface,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                Text("Reminders", style = MaterialTheme.typography.titleLarge)
                Text(
                    "A nudge when you wake up and when you head to bed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )

                Spacer(Modifier.height(10.dp))

                ReminderRow(
                    label = "Wake up",
                    time = formatClock(vm.wakeMinutes),
                    checked = vm.morningOn,
                    onCheckedChange = vm::setMorning,
                    onTimeClick = { onPickTime(vm.wakeMinutes, vm::setWake) }
                )

                HorizontalDivider(color = cs.outline.copy(alpha = 0.6f))

                ReminderRow(
                    label = "Bedtime",
                    time = formatClock(vm.bedMinutes),
                    checked = vm.eveningOn,
                    onCheckedChange = vm::setEvening,
                    onTimeClick = { onPickTime(vm.bedMinutes, vm::setBed) }
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        VideoImportCard(vm = vm, onChanged = { vm.videosChanged() })

        if (!notificationsEnabled) {
            Spacer(Modifier.height(14.dp))
            NoticeCard(
                title = "Notifications are off",
                body = "Reminders can't appear until Android allows them for this app.",
                action = "Turn on",
                onAction = onOpenNotificationSettings
            )
        }

        if (!exactAlarmsOk) {
            Spacer(Modifier.height(14.dp))
            NoticeCard(
                title = "Reminders may run late",
                body = "Android is throttling alarms, so a nudge can arrive a few minutes after the time you set.",
                action = "Allow precise alarms",
                onAction = onOpenExactAlarmSettings
            )
        }

        Spacer(Modifier.height(20.dp))

        Text(
            "Sessions rotate through 12 standing stretches, so tomorrow's set is different from today's." +
                if (vm.sessionsDone > 0) "  ${vm.sessionsDone} session${if (vm.sessionsDone == 1) "" else "s"} so far." else "",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Start,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            "${SessionPlanner.TOTAL_SECONDS / 60} minutes · standing only · works offline",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
    }
}

/** One reminder: its label, its time (tap to change), and an on/off switch. */
@Composable
private fun ReminderRow(
    label: String,
    time: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onTimeClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onTimeClick)
                .padding(vertical = 2.dp)
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            Text(
                time,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = cs.primary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = cs.primary)
        )
    }
}

/** A quiet inline notice with a single action, used for permission problems. */
@Composable
private fun NoticeCard(
    title: String,
    body: String,
    action: String,
    onAction: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = cs.surfaceVariant,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            TextButton(onClick = onAction, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text(action, style = MaterialTheme.typography.labelLarge, color = cs.primary)
            }
        }
    }
}
