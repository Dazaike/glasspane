package com.glasspane.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/**
 * Contents of the "Overlays" bottom sheet: a horizontal tray of layer
 * thumbnails (tap = select active, "x" = delete, trailing "+" = add) plus a
 * settings section bound to [OverlayLayersState.activeLayer].
 */
@Composable
fun OverlayLayersSheetContent(
    layersState: OverlayLayersState,
    onAddLayerRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current

    Column(modifier = modifier.padding(bottom = 24.dp)) {
        Text(
            text = "Overlay layers",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(layersState.layers, key = { it.id }) { layer ->
                LayerThumbnail(
                    layer = layer,
                    isActive = layer.id == layersState.activeLayerId,
                    onSelect = {
                        layersState.setActiveLayer(layer.id)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                    onDelete = {
                        layersState.removeLayer(layer.id)
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                )
            }
            item {
                AddLayerTile(onClick = onAddLayerRequested)
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        val activeLayer = layersState.activeLayer
        if (activeLayer == null) {
            Text(
                text = "Add an overlay image to edit its settings.",
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LayerSettings(layer = activeLayer)
        }
    }
}

@Composable
private fun LayerThumbnail(
    layer: OverlayLayerState,
    isActive: Boolean,
    onSelect: () -> Unit,
    onDelete: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary else Color.Gray,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() }
    ) {
        val bitmap = layer.imageBitmap
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Layer thumbnail",
                modifier = Modifier.fillMaxWidth().height(72.dp),
                contentScale = ContentScale.Crop
            )
        }
        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Delete layer",
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun AddLayerTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(imageVector = Icons.Filled.Add, contentDescription = "Add layer")
    }
}

private val opacityPresets = listOf(0.25f, 0.5f, 0.75f, 1f)

@Composable
private fun LayerSettings(layer: OverlayLayerState) {
    val haptics = LocalHapticFeedback.current
    var adjustExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        Text(text = "Opacity: ${(layer.opacity * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = layer.opacity, onValueChange = { layer.opacity = it }, valueRange = 0f..1f)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            opacityPresets.forEach { preset ->
                FilterChip(
                    selected = layer.opacity == preset,
                    onClick = { layer.opacity = preset },
                    label = { Text("${(preset * 100).toInt()}%") }
                )
            }
        }

        Row(
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(onClick = { layer.flipHorizontal = !layer.flipHorizontal }) {
                Icon(
                    imageVector = Icons.Filled.Flip,
                    contentDescription = "Flip horizontal",
                    tint = if (layer.flipHorizontal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = { layer.flipVertical = !layer.flipVertical }) {
                Icon(
                    imageVector = Icons.Filled.Flip,
                    contentDescription = "Flip vertical",
                    tint = if (layer.flipVertical) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.rotate(90f)
                )
            }
            IconButton(onClick = {
                layer.locked = !layer.locked
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            }) {
                Icon(
                    imageVector = if (layer.locked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = if (layer.locked) "Unlock layer" else "Lock layer"
                )
            }
            if (layer.isTransformed) {
                IconButton(onClick = { layer.resetTransform() }) {
                    Icon(imageVector = Icons.Filled.Refresh, contentDescription = "Reset transform")
                }
            }
            IconButton(onClick = { adjustExpanded = !adjustExpanded }) {
                Icon(
                    imageVector = if (adjustExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = "Toggle color adjustments"
                )
            }
            Text("Adjust", style = MaterialTheme.typography.labelLarge)
        }

        AnimatedVisibility(visible = adjustExpanded) {
            Column {
                Text("Brightness", style = MaterialTheme.typography.labelMedium)
                Slider(value = layer.brightness, onValueChange = { layer.brightness = it }, valueRange = -1f..1f)
                Text("Contrast", style = MaterialTheme.typography.labelMedium)
                Slider(value = layer.contrast, onValueChange = { layer.contrast = it }, valueRange = 0f..2f)
                Text("Saturation", style = MaterialTheme.typography.labelMedium)
                Slider(value = layer.saturation, onValueChange = { layer.saturation = it }, valueRange = 0f..2f)
            }
        }
    }
}
