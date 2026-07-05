package com.glasspane.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Record start/stop control, an elapsed-time readout, and (while recording) a
 * secondary pause/resume control. Recording/pause state and duration are
 * owned by the caller (see `CameraScreen`); this composable is purely
 * presentational.
 */
@Composable
fun RecordingControls(
    isRecording: Boolean,
    isPaused: Boolean,
    elapsedSeconds: Long,
    onStartStop: () -> Unit,
    onPauseResume: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val recordColor by animateColorAsState(
        targetValue = if (isRecording) Color.Red else MaterialTheme.colorScheme.primary,
        label = "recordButtonColor"
    )

    // No end/right padding here: the caller (CameraScreen) right-aligns this
    // row against the torch button above it, so the record FAB's right edge
    // must exactly match the torch FAB's right edge (both flush, uninset).
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (isRecording) {
            Text(
                text = formatElapsed(elapsedSeconds),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .padding(end = 12.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
        AnimatedVisibility(visible = isRecording) {
            SmallFloatingActionButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onPauseResume()
                },
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                    contentDescription = if (isPaused) "Resume recording" else "Pause recording"
                )
            }
        }
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onStartStop()
            },
            containerColor = recordColor,
            modifier = Modifier.clip(CircleShape)
        ) {
            Icon(
                imageVector = if (isRecording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                contentDescription = if (isRecording) "Stop recording" else "Start recording",
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

private fun formatElapsed(totalSeconds: Long): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
