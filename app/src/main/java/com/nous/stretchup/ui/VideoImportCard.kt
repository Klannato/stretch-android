package com.nous.stretchup.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.nous.stretchup.AppViewModel
import com.nous.stretchup.data.VideoLibrary

/**
 * Import real footage and map it onto stretches.
 *
 * Files named after a stretch (`side-bend.mp4`, `side-bend_2.mov`, `side-bend-angle1.mp4`)
 * are assigned automatically; anything else lands in a queue where it can be pointed at
 * the right stretch by hand. Once a stretch has footage, the session screen plays it
 * instead of the drawn figure and the illustration.
 */
@Composable
fun VideoImportCard(vm: AppViewModel, onChanged: () -> Unit) {
    val context = LocalContext.current
    val library = remember { VideoLibrary(context) }
    var videos by remember { mutableStateOf(library.videos()) }
    var pending by remember { mutableStateOf(library.pending()) }
    val cs = MaterialTheme.colorScheme

    fun refresh() {
        videos = library.videos()
        pending = library.pending()
        onChanged()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { uri ->
            // Keep read access across reboots; the app never copies the file itself.
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val name = VideoLibrary.displayName(context, uri.toString())
            val id = VideoLibrary.stretchIdForFileName(name, vm.library.map { it.id })
            if (id != null) library.add(id, uri.toString()) else library.addPending(listOf(uri.toString()))
        }
        refresh()
    }

    Surface(color = cs.surface, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Text("Your own footage", style = MaterialTheme.typography.titleLarge)
            Text(
                "Import clips of a person doing the stretches. Once a stretch has footage, the app plays it instead of the drawings.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = { picker.launch(arrayOf("video/*")) },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = cs.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Text("Import videos", style = MaterialTheme.typography.titleMedium)
            }

            val assigned = videos.filterValues { it.isNotEmpty() }
            if (assigned.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = cs.outline.copy(alpha = 0.6f))
                assigned.forEach { (id, list) ->
                    val stretchName = vm.library.firstOrNull { it.id == id }?.name ?: id
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(stretchName, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (list.size == 1) "1 angle" else "${list.size} angles",
                                style = MaterialTheme.typography.labelSmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                        TextButton(onClick = { library.clear(id); refresh() }) {
                            Text("Remove", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            if (pending.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = cs.outline.copy(alpha = 0.6f))
                Text(
                    "${pending.size} clip${if (pending.size == 1) "" else "s"} to assign",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 10.dp)
                )
                pending.forEach { uri ->
                    PendingRow(
                        fileName = VideoLibrary.displayName(context, uri),
                        stretches = vm.library.map { it.name to it.id },
                        onAssign = { id -> library.resolvePending(uri, id); refresh() }
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingRow(
    fileName: String,
    stretches: List<Pair<String, String>>,
    onAssign: (String) -> Unit
) {
    val cs = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            fileName,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            maxLines = 1
        )
        Box {
            TextButton(onClick = { open = true }) {
                Text("Choose stretch", style = MaterialTheme.typography.labelLarge, color = cs.primary)
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                stretches.forEach { (name, id) ->
                    DropdownMenuItem(
                        text = { Text(name, style = MaterialTheme.typography.bodyMedium) },
                        onClick = { open = false; onAssign(id) }
                    )
                }
            }
        }
    }
}
