package com.nous.stretchup.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nous.stretchup.AppViewModel
import com.nous.stretchup.model.poseAt
import androidx.compose.ui.platform.LocalContext
import com.nous.stretchup.session.SessionPlanner
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * The session screen: one stretch per 60-second block, with the figure, the cue, the
 * countdown and the reference loop.
 *
 * The clock is driven here rather than in the ViewModel because it has to stop when
 * the screen leaves composition; the ViewModel only owns the elapsed value.
 */
@Composable
fun SessionScreen(vm: AppViewModel) {
    val haptics = LocalHapticFeedback.current
    val blockIndex = SessionPlanner.blockIndexFor(vm.elapsed)
    val block = vm.plan.getOrNull(blockIndex) ?: return
    val into = SessionPlanner.secondsIntoBlock(vm.elapsed)
    val cs = MaterialTheme.colorScheme

    // One timer drives the session clock; 100 ms is smooth enough for a countdown.
    LaunchedEffect(vm.running) {
        while (vm.running) {
            kotlinx.coroutines.delay(100)
            vm.tick(0.1f)
        }
    }

    LaunchedEffect(blockIndex, vm.running) {
        if (vm.running) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    if (vm.finished) {
        Summary(vm)
        return
    }

    // The breathing animation is a 7-second cosine cycle, so the pose eases in and out
    // instead of snapping between keyframes. Frozen while paused so the figure holds.
    val cycle = if (vm.running) {
        val transition = rememberInfiniteTransition(label = "breath")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(7000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "breathValue"
        )
        value
    } else {
        0.2f
    }

    val pose = block.stretch.poseAt(cycle)
    val bounds = remember(block.stretch.id) { figureBounds(block.stretch) }
    // Two-sided stretches play the same pose flipped for the second half, which is what
    // makes "30 seconds a side" work without a second set of drawings.
    val mirrored = SessionPlanner.mirrorActive(block, into)
    val switching = SessionPlanner.switchingSide(block, into)
    val blockRemaining = ceil(SessionPlanner.BLOCK_SECONDS - into).toInt().coerceIn(0, SessionPlanner.BLOCK_SECONDS)
    val totalRemaining = ceil(SessionPlanner.TOTAL_SECONDS - vm.elapsed).toInt().coerceAtLeast(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .padding(top = 16.dp, bottom = 24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(SessionPlanner.BLOCKS) { i ->
                    val filled = i < blockIndex
                    val current = i == blockIndex
                    Box(
                        Modifier
                            .size(if (current) 9.dp else 7.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    filled -> cs.primary
                                    current -> cs.primary.copy(alpha = 0.45f)
                                    else -> cs.outline
                                }
                            )
                    )
                }
            }
            TextButton(onClick = { vm.endSession() }) {
                Text("Close", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(10.dp))

        val context = LocalContext.current
        val available = remember { context.assets.list("ref")?.toSet() ?: emptySet() }
        val hasReference = "${block.stretch.id}.gif" in available || "${block.stretch.id}.webp" in available

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(26.dp))
                .background(cs.surface)
        ) {
            Row(Modifier.fillMaxSize()) {
                StickFigure(
                    pose = pose,
                    bounds = bounds,
                    mirrored = mirrored,
                    showWall = block.stretch.props == "wall",
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(10.dp)
                )
                if (hasReference) {
                    Column(
                        modifier = Modifier
                            .width(126.dp)
                            .align(Alignment.Top)
                            .padding(top = 10.dp, end = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        ReferenceLoop(
                            stretchId = block.stretch.id,
                            contentDescription = "Reference animation for ${block.stretch.name}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.75f)
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Text(
                            "reference",
                            style = MaterialTheme.typography.labelSmall,
                            color = cs.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            if (switching) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(14.dp))
                        .background(cs.primary)
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                ) {
                    Text(
                        "Switch sides",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onPrimary
                    )
                }
            }

            if (block.twoSided) {
                Text(
                    if (mirrored) "Second side" else "First side",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Text(
            block.stretch.name,
            style = MaterialTheme.typography.headlineMedium,
            color = cs.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            block.stretch.cue,
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                formatSeconds(blockRemaining),
                style = MaterialTheme.typography.displayLarge,
                color = cs.onBackground
            )
            Text(
                "  ${formatSeconds(totalRemaining)} left in the set",
                style = MaterialTheme.typography.labelLarge,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = { vm.backBlock() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Back", style = MaterialTheme.typography.labelLarge, color = cs.onBackground)
            }
            Button(
                onClick = { vm.togglePause() },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
            ) {
                Text(
                    if (vm.running) "Pause" else "Resume",
                    style = MaterialTheme.typography.titleMedium
                )
            }
            OutlinedButton(
                onClick = { vm.skipBlock() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(52.dp)
            ) {
                Text("Next", style = MaterialTheme.typography.labelLarge, color = cs.onBackground)
            }
        }
    }
}

/** End-of-session recap: what was done, and the one button left to press. */
@Composable
private fun Summary(vm: AppViewModel) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("Five minutes, done.", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            if (vm.slot == com.nous.stretchup.session.Slot.WAKE) "You're awake. Go have the morning."
            else "Loose enough to sleep. Lights off.",
            style = MaterialTheme.typography.bodyLarge,
            color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(22.dp))
        vm.plan.forEachIndexed { i, b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    "${i + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = cs.primary,
                    modifier = Modifier.size(width = 24.dp, height = 22.dp)
                )
                Text(b.stretch.name, style = MaterialTheme.typography.bodyMedium)
            }
        }
        Spacer(Modifier.height(26.dp))
        Button(
            onClick = { vm.endSession() },
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            Text("Done", style = MaterialTheme.typography.titleMedium)
        }
    }
}
