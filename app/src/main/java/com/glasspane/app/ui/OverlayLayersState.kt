package com.glasspane.app.ui

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

/** Global visibility mode applied to every overlay layer at once. */
enum class OverlayVisibility { VISIBLE, HIDDEN, FADED }

/**
 * A single overlay image layer: its source image, opacity, pan/zoom/rotation
 * transform, flip flags, brightness/contrast/saturation color adjustments, and
 * a lock flag that freezes only the transform gestures (pan/zoom/rotate).
 *
 * Lives purely in the Compose layer: it is rendered above
 * [androidx.camera.view.PreviewView] and is never attached to any CameraX
 * capture surface, so it is automatically excluded from recorded video.
 */
class OverlayLayerState(val id: String = UUID.randomUUID().toString(), imageUri: Uri? = null) {
    var imageUri by mutableStateOf(imageUri)
    var imageBitmap by mutableStateOf<ImageBitmap?>(null)
    var opacity by mutableFloatStateOf(0.5f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
    var scale by mutableFloatStateOf(1f)
    var rotation by mutableFloatStateOf(0f)
    var flipHorizontal by mutableStateOf(false)
    var flipVertical by mutableStateOf(false)

    /** -1f..1f, 0f is neutral. */
    var brightness by mutableFloatStateOf(0f)

    /** 0f..2f, 1f is neutral. */
    var contrast by mutableFloatStateOf(1f)

    /** 0f..2f, 1f is neutral (0 = grayscale, 2 = oversaturated). */
    var saturation by mutableFloatStateOf(1f)

    var locked by mutableStateOf(false)

    fun resetTransform() {
        offsetX = 0f
        offsetY = 0f
        scale = 1f
        rotation = 0f
    }

    fun resetColorAdjustments() {
        brightness = 0f
        contrast = 1f
        saturation = 1f
    }

    val isTransformed: Boolean
        get() = offsetX != 0f || offsetY != 0f || scale != 1f || rotation != 0f
}

/**
 * Owns the ordered list of [OverlayLayerState] layers, which one is "active"
 * (bound to the settings sheet), and the global visibility toggle state shared
 * across all layers.
 */
class OverlayLayersState {
    val layers: SnapshotStateList<OverlayLayerState> = mutableStateListOf()
    var activeLayerId by mutableStateOf<String?>(null)
        private set
    var visibilityState by mutableStateOf(OverlayVisibility.VISIBLE)
        private set

    private var preHideOpacities: Map<String, Float> = emptyMap()

    val activeLayer: OverlayLayerState?
        get() = layers.firstOrNull { it.id == activeLayerId }

    fun setActiveLayer(id: String) {
        if (layers.any { it.id == id }) {
            activeLayerId = id
        }
    }

    fun addLayer(uri: Uri?): OverlayLayerState {
        val layer = OverlayLayerState(imageUri = uri)
        layers.add(layer)
        activeLayerId = layer.id
        return layer
    }

    fun removeLayer(id: String) {
        val removedIndex = layers.indexOfFirst { it.id == id }
        if (removedIndex < 0) return
        layers.removeAt(removedIndex)
        if (activeLayerId == id) {
            activeLayerId = layers.getOrNull(removedIndex.coerceAtMost(layers.lastIndex))?.id
                ?: layers.lastOrNull()?.id
        }
    }

    fun reorder(from: Int, to: Int) {
        if (from !in layers.indices || to !in layers.indices || from == to) return
        val item = layers.removeAt(from)
        layers.add(to, item)
    }

    /** Instantly hides (or restores) every layer's opacity, no animation. */
    fun toggleVisibilityInstant() {
        if (layers.isEmpty()) return
        when (visibilityState) {
            OverlayVisibility.VISIBLE -> {
                preHideOpacities = layers.associate { it.id to it.opacity }
                layers.forEach { it.opacity = 0f }
                visibilityState = OverlayVisibility.HIDDEN
            }
            OverlayVisibility.HIDDEN, OverlayVisibility.FADED -> {
                layers.forEach { layer -> layer.opacity = preHideOpacities[layer.id] ?: layer.opacity }
                visibilityState = OverlayVisibility.VISIBLE
            }
        }
    }

    /** Gradually fades every layer's opacity to/from its pre-hide value. */
    suspend fun toggleVisibilityFade(durationMs: Int = 1500) {
        if (layers.isEmpty()) return
        when (visibilityState) {
            OverlayVisibility.VISIBLE -> {
                preHideOpacities = layers.associate { it.id to it.opacity }
                visibilityState = OverlayVisibility.FADED
                animateAllTo(layers.associate { it.id to 0f }, durationMs)
            }
            OverlayVisibility.HIDDEN, OverlayVisibility.FADED -> {
                visibilityState = OverlayVisibility.VISIBLE
                animateAllTo(layers.associate { it.id to (preHideOpacities[it.id] ?: it.opacity) }, durationMs)
            }
        }
    }

    private suspend fun animateAllTo(targets: Map<String, Float>, durationMs: Int) = coroutineScope {
        layers.forEach { layer ->
            val target = targets[layer.id] ?: return@forEach
            launch {
                val animatable = Animatable(layer.opacity)
                animatable.animateTo(target, tween(durationMs)) {
                    layer.opacity = value
                }
            }
        }
    }
}
