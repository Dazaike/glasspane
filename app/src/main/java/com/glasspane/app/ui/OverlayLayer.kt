package com.glasspane.app.ui

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext

/**
 * Renders every layer in [layersState] (in order, bottom to top) full-bleed
 * over the camera preview, each alpha-blended and color-adjusted per its own
 * [OverlayLayerState]. Must be composed above (i.e. after, in z-order) the
 * `AndroidView` hosting the CameraX `PreviewView` for the alpha blend to be
 * visible. Purely Compose-drawn: never attached to any CameraX capture
 * surface, so overlays are automatically excluded from recorded video.
 */
@Composable
fun OverlayLayersCanvas(layersState: OverlayLayersState, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    Box(modifier = modifier.fillMaxSize()) {
        layersState.layers.forEach { layer ->
            key(layer.id) {
                LaunchedEffect(layer.imageUri) {
                    val uri = layer.imageUri
                    layer.imageBitmap = if (uri == null) {
                        null
                    } else {
                        runCatching {
                            context.contentResolver.openInputStream(uri)?.use { stream ->
                                BitmapFactory.decodeStream(stream)?.asImageBitmap()
                            }
                        }.getOrNull()
                    }
                }

                val bitmap = layer.imageBitmap
                if (bitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(layer.id) {
                                detectTapGestures {
                                    layersState.setActiveLayer(layer.id)
                                }
                            }
                            .pointerInput(layer.id, layer.locked) {
                                detectTransformGestures { _, pan, zoom, rotationDelta ->
                                    if (!layer.locked) {
                                        layer.offsetX += pan.x
                                        layer.offsetY += pan.y
                                        layer.scale = (layer.scale * zoom).coerceIn(0.25f, 6f)
                                        layer.rotation += rotationDelta
                                    }
                                }
                            }
                    ) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Overlay image",
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = layer.scale * (if (layer.flipHorizontal) -1f else 1f),
                                    scaleY = layer.scale * (if (layer.flipVertical) -1f else 1f),
                                    rotationZ = layer.rotation,
                                    translationX = layer.offsetX,
                                    translationY = layer.offsetY
                                ),
                            contentScale = ContentScale.Fit,
                            alpha = layer.opacity,
                            colorFilter = ColorFilter.colorMatrix(
                                colorMatrixFor(layer.brightness, layer.contrast, layer.saturation)
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * Builds a combined brightness/contrast/saturation [ColorMatrix].
 * [brightness] is -1f..1f (0 neutral), [contrast] and [saturation] are 0f..2f
 * (1f neutral); values map onto standard scale+translate color matrices.
 */
fun colorMatrixFor(brightness: Float, contrast: Float, saturation: Float): ColorMatrix {
    val saturationMatrix = ColorMatrix().apply { setToSaturation(saturation) }
    val translate = (1f - contrast) * 127.5f + brightness * 255f
    val contrastMatrix = ColorMatrix(
        floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f
        )
    )
    saturationMatrix.timesAssign(contrastMatrix)
    return saturationMatrix
}

/** Launcher hook for picking a single image via the system Photo Picker. */
@Composable
fun rememberImagePickerLauncher(onPicked: (Uri) -> Unit) =
    rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            onPicked(uri)
        }
    }

fun launchImagePicker(launcher: ActivityResultLauncher<PickVisualMediaRequest>) {
    launcher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
    )
}
