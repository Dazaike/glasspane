package com.glasspane.app.ui

import androidx.camera.video.Quality
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Small, in-memory app preferences: haptics on/off and the defaults applied
 * when the camera screen first launches. Intentionally minimal - a
 * future-proofing entry point rather than a full settings screen.
 */
class AppSettingsState {
    var hapticsEnabled by mutableStateOf(true)
    var defaultGridOn by mutableStateOf(false)
    var defaultQuality by mutableStateOf(Quality.FHD)
}

@Composable
fun rememberAppSettingsState(): AppSettingsState = remember { AppSettingsState() }

/** Minimal settings sheet contents: haptics toggle, default grid, default quality. */
@Composable
fun SettingsSheetContent(settings: AppSettingsState, modifier: Modifier = Modifier) {
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    Column(modifier = modifier.padding(16.dp)) {
        Text(text = "Settings", style = MaterialTheme.typography.titleMedium)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Haptic feedback")
            Switch(checked = settings.hapticsEnabled, onCheckedChange = { settings.hapticsEnabled = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Grid on by default")
            Switch(checked = settings.defaultGridOn, onCheckedChange = { settings.defaultGridOn = it })
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Default quality")
            androidx.compose.foundation.layout.Box {
                TextButton(onClick = { qualityMenuExpanded = true }) {
                    Text(qualityLabel(settings.defaultQuality))
                }
                DropdownMenu(expanded = qualityMenuExpanded, onDismissRequest = { qualityMenuExpanded = false }) {
                    listOf(Quality.SD, Quality.HD, Quality.FHD, Quality.UHD).forEach { quality ->
                        DropdownMenuItem(
                            text = { Text(qualityLabel(quality)) },
                            onClick = {
                                settings.defaultQuality = quality
                                qualityMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

fun qualityLabel(quality: Quality): String = when (quality) {
    Quality.SD -> "480p"
    Quality.HD -> "720p"
    Quality.FHD -> "1080p"
    Quality.UHD -> "4K"
    else -> "Auto"
}
