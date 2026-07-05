package com.glasspane.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.glasspane.app.camera.TorchCapability
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * Compact torch control: a single toggle button, with a large filled-bar
 * vertical strength control shown above it while the torch is on and the
 * hardware reports `FLASH_INFO_STRENGTH_MAXIMUM_LEVEL > 1` (API 33+).
 *
 * The bar is a custom control (not a shrunk Material [androidx.compose.material3.Slider],
 * whose track/thumb stay tiny regardless of container size) so the whole
 * width/height of its box is a real drag/tap surface with a big visible fill.
 * It auto-hides ~2s after the last interaction so it doesn't stay
 * permanently on-screen while the torch is simply left on; tapping/dragging
 * it (or re-toggling the torch) brings it back. The whole control should be
 * omitted by the caller when the bound camera has no flash unit.
 */
@Composable
fun TorchControls(
    capability: TorchCapability,
    isOn: Boolean,
    strengthLevel: Int,
    onToggle: (Boolean) -> Unit,
    onStrengthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val containerColor by animateColorAsState(
        targetValue = if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "torchButtonColor"
    )

    // A single effect keyed on (isOn, strengthLevel): any change -- torch
    // toggled on, or the strength dragged/tapped to a new value -- restarts
    // this coroutine (cancelling any pending hide), shows the bar, and
    // schedules it to hide again after 2s of no further change. Keeping this
    // as one effect (rather than a separate tick counter + a second
    // launched effect racing it) avoids ordering bugs between two coroutines.
    var sliderVisible by remember { mutableStateOf(false) }
    LaunchedEffect(isOn, strengthLevel) {
        if (isOn) {
            sliderVisible = true
            delay(2000)
            sliderVisible = false
        } else {
            sliderVisible = false
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        AnimatedVisibility(visible = isOn && capability.supportsVariableStrength && sliderVisible) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .height(220.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .pointerInput(capability.maxStrengthLevel) {
                        detectTapGestures { offset ->
                            onStrengthChange(levelForOffset(offset.y, size.height, capability.maxStrengthLevel))
                        }
                    }
                    .pointerInput(capability.maxStrengthLevel) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            onStrengthChange(levelForOffset(change.position.y, size.height, capability.maxStrengthLevel))
                        }
                    },
                contentAlignment = Alignment.BottomCenter
            ) {
                val fraction = if (capability.maxStrengthLevel > 1) {
                    (strengthLevel - 1f) / (capability.maxStrengthLevel - 1f).toFloat()
                } else {
                    1f
                }.coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(fraction)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        FloatingActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                sliderVisible = true
                onToggle(!isOn)
            },
            containerColor = containerColor,
            modifier = Modifier.padding(top = 8.dp)
        ) {
            Icon(
                imageVector = if (isOn) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                contentDescription = "Toggle torch"
            )
        }
    }
}

private fun levelForOffset(y: Float, heightPx: Int, maxLevel: Int): Int {
    if (heightPx <= 0 || maxLevel <= 1) return maxLevel.coerceAtLeast(1)
    val fraction = 1f - (y / heightPx).coerceIn(0f, 1f)
    return (1 + fraction * (maxLevel - 1)).roundToInt().coerceIn(1, maxLevel)
}
