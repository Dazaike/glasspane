package com.glasspane.app.ui

import android.hardware.camera2.CameraCharacteristics
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ZoomState
import androidx.camera.video.Quality
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BlurOn
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Observer
import com.glasspane.app.camera.CameraXController
import com.glasspane.app.camera.LensOption
import com.glasspane.app.camera.LensRepository
import com.glasspane.app.camera.TorchCapability
import com.glasspane.app.camera.TorchController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Top-level camera UI: hosts a [PreviewView] (in
 * [PreviewView.ImplementationMode.COMPATIBLE] so the Compose overlay can
 * alpha-blend above it), the multi-layer overlay canvas, grid, lens/zoom
 * controls, torch, and recording controls. All Compose-drawn UI here is
 * layered above the preview surface only -- it is never attached to the
 * CameraX capture pipeline, so recorded video excludes it by construction.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    val cameraController = remember { CameraXController(context) }
    val settings = rememberAppSettingsState()

    val realHaptics = LocalHapticFeedback.current
    val effectiveHaptics = remember(settings.hapticsEnabled, realHaptics) {
        if (settings.hapticsEnabled) {
            realHaptics
        } else {
            object : HapticFeedback {
                override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) = Unit
            }
        }
    }

    var torchController by remember { mutableStateOf<TorchController?>(null) }
    var torchCapability by remember {
        mutableStateOf(TorchCapability(supportsVariableStrength = false, maxStrengthLevel = 1))
    }
    var torchOn by remember { mutableStateOf(false) }
    var torchStrength by remember { mutableIntStateOf(1) }
    var hasFlashUnit by remember { mutableStateOf(false) }

    var isRecording by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var elapsedSeconds by remember { mutableLongStateOf(0L) }

    var lenses by remember { mutableStateOf<List<LensOption>>(emptyList()) }
    var currentFacing by remember { mutableIntStateOf(CameraCharacteristics.LENS_FACING_BACK) }
    var selectedLensId by remember { mutableStateOf<String?>(null) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoomRatio by remember { mutableFloatStateOf(1f) }
    var maxZoomRatio by remember { mutableFloatStateOf(1f) }
    var linearZoom by remember { mutableFloatStateOf(0f) }

    var cameraControlsExpanded by remember { mutableStateOf(false) }
    var displayOptionsExpanded by remember { mutableStateOf(false) }
    var gridEnabled by remember { mutableStateOf(false) }
    var currentQuality by remember { mutableStateOf(Quality.FHD) }
    var qualityMenuExpanded by remember { mutableStateOf(false) }

    var showOverlaysSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    val overlaysSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    val layersState = remember { OverlayLayersState() }
    val imagePicker = rememberImagePickerLauncher { uri -> layersState.addLayer(uri) }

    LaunchedEffect(Unit) {
        gridEnabled = settings.defaultGridOn
        currentQuality = settings.defaultQuality
    }

    fun onCameraBound(camera: Camera) {
        boundCamera = camera
        hasFlashUnit = camera.cameraInfo.hasFlashUnit()
        val controller = TorchController(context, camera)
        torchController = controller
        torchCapability = controller.queryCapability()
        controller.registerTorchCallback { isOn, level ->
            torchOn = isOn
            if (level > 0) torchStrength = level
        }
        cameraController.getCameraProvider()?.let { provider ->
            lenses = LensRepository.listLenses(provider)
            if (selectedLensId == null) {
                selectedLensId = lenses.firstOrNull { it.facing == currentFacing }?.cameraId
            }
        }
    }

    DisposableEffect(boundCamera) {
        val camera = boundCamera
        val observer = Observer<ZoomState> { state ->
            zoomRatio = state.zoomRatio
            minZoomRatio = state.minZoomRatio
            maxZoomRatio = state.maxZoomRatio
            linearZoom = state.linearZoom
        }
        camera?.cameraInfo?.zoomState?.observeForever(observer)
        onDispose {
            camera?.cameraInfo?.zoomState?.removeObserver(observer)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            torchController?.unregisterTorchCallback()
            cameraController.unbind()
        }
    }

    LaunchedEffect(isRecording, isPaused) {
        if (isRecording) {
            var accumulated = 0L
            var segmentStart = System.currentTimeMillis()
            while (isRecording) {
                if (!isPaused) {
                    elapsedSeconds = accumulated + (System.currentTimeMillis() - segmentStart) / 1000
                } else {
                    accumulated = elapsedSeconds
                }
                delay(500)
                if (isPaused) {
                    segmentStart = System.currentTimeMillis()
                    accumulated = elapsedSeconds
                }
            }
        } else {
            elapsedSeconds = 0L
        }
    }

    fun switchFacing() {
        val targetFacing = if (currentFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        val defaultSelector = if (targetFacing == CameraCharacteristics.LENS_FACING_BACK) {
            CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            CameraSelector.DEFAULT_FRONT_CAMERA
        }
        currentFacing = targetFacing
        selectedLensId = lenses.firstOrNull { it.facing == targetFacing && it.zoomLabel == "1x" }?.cameraId
            ?: lenses.firstOrNull { it.facing == targetFacing }?.cameraId
        effectiveHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        cameraController.switchTo(defaultSelector)
    }

    fun selectLens(option: LensOption) {
        selectedLensId = option.cameraId
        effectiveHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
        cameraController.switchTo(option.cameraSelector)
    }

    CompositionLocalProvider(LocalHapticFeedback provides effectiveHaptics) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        cameraController.startCamera(lifecycleOwner, this) { boundCamera ->
                            onCameraBound(boundCamera)
                        }
                    }
                }
            )

            OverlayLayersCanvas(layersState = layersState, modifier = Modifier.fillMaxSize())

            if (gridEnabled) {
                GridOverlay(modifier = Modifier.fillMaxSize())
            }

            // Top-left cluster: a single button; expands into 3 controls below
            // it (switch camera, lens chips, zoom slider).
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 24.dp)
                    .animateContentSize()
            ) {
                FloatingActionButton(
                    onClick = {
                        cameraControlsExpanded = !cameraControlsExpanded
                        effectiveHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Cameraswitch, contentDescription = "Camera controls")
                }
                AnimatedVisibility(visible = cameraControlsExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        // 1: switch front/back camera
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { switchFacing() }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Cameraswitch,
                                contentDescription = "Switch front/back camera",
                                tint = Color.White
                            )
                            Text(
                                text = "Switch camera",
                                color = Color.White,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        // 2: lens chips
                        val lensesForFacing = lenses.filter { it.facing == currentFacing }
                        AnimatedVisibility(visible = lensesForFacing.size > 1) {
                            LazyRow(modifier = Modifier.padding(top = 12.dp)) {
                                items(lensesForFacing, key = { it.cameraId }) { option ->
                                    FilterChip(
                                        modifier = Modifier.padding(end = 4.dp),
                                        selected = option.cameraId == selectedLensId,
                                        onClick = { selectLens(option) },
                                        label = { Text(option.zoomLabel) }
                                    )
                                }
                            }
                        }
                        // 3: zoom slider
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(text = "${"%.1f".format(zoomRatio)}x", color = Color.White)
                            Slider(
                                value = linearZoom,
                                onValueChange = { value ->
                                    linearZoom = value
                                    boundCamera?.cameraControl?.setLinearZoom(value)
                                },
                                valueRange = 0f..1f,
                                modifier = Modifier.width(200.dp)
                            )
                        }
                    }
                }
            }

            // Top-right cluster: a single button; expands into 3 controls below
            // it (grid toggle, quality picker, settings).
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 24.dp, end = 16.dp)
                    .animateContentSize(),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = {
                        displayOptionsExpanded = !displayOptionsExpanded
                        effectiveHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                ) {
                    Icon(imageVector = Icons.Filled.Tune, contentDescription = "Display options")
                }
                AnimatedVisibility(visible = displayOptionsExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        horizontalAlignment = Alignment.End
                    ) {
                        // 1: grid toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable {
                                gridEnabled = !gridEnabled
                                effectiveHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                        ) {
                            Text(text = "Grid", color = Color.White, modifier = Modifier.padding(end = 8.dp))
                            Icon(
                                imageVector = Icons.Filled.GridOn,
                                contentDescription = "Toggle grid",
                                tint = if (gridEnabled) MaterialTheme.colorScheme.primary else Color.White
                            )
                        }
                        // 2: quality picker
                        Box(modifier = Modifier.padding(top = 12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { if (!isRecording) qualityMenuExpanded = true }
                            ) {
                                Text(text = "Quality", color = Color.White, modifier = Modifier.padding(end = 8.dp))
                                Text(text = qualityLabel(currentQuality), color = Color.White)
                            }
                            DropdownMenu(
                                expanded = qualityMenuExpanded,
                                onDismissRequest = { qualityMenuExpanded = false }
                            ) {
                                listOf(Quality.SD, Quality.HD, Quality.FHD, Quality.UHD).forEach { quality ->
                                    DropdownMenuItem(
                                        text = { Text(qualityLabel(quality)) },
                                        onClick = {
                                            currentQuality = quality
                                            qualityMenuExpanded = false
                                            cameraController.setQuality(quality)
                                        }
                                    )
                                }
                            }
                        }
                        // 3: settings
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .padding(top = 12.dp)
                                .clickable { showSettingsSheet = true }
                        ) {
                            Text(text = "Settings", color = Color.White, modifier = Modifier.padding(end = 8.dp))
                            Icon(imageVector = Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White)
                        }
                    }
                }
            }

            // Bottom-left cluster: overlays FAB + global visibility toggles.
            // Start padding matches the top-left cluster's so both FABs share
            // the same left edge.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, top = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FloatingActionButton(onClick = { showOverlaysSheet = true }) {
                    Icon(imageVector = Icons.Filled.Layers, contentDescription = "Overlays")
                }
                AnimatedVisibility(visible = layersState.layers.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallFloatingActionButton(onClick = {
                            layersState.toggleVisibilityInstant()
                            effectiveHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }) {
                            Icon(
                                imageVector = if (layersState.visibilityState == OverlayVisibility.VISIBLE) {
                                    Icons.Filled.Visibility
                                } else {
                                    Icons.Filled.VisibilityOff
                                },
                                contentDescription = "Toggle overlay visibility"
                            )
                        }
                        SmallFloatingActionButton(onClick = {
                            coroutineScope.launch { layersState.toggleVisibilityFade() }
                            effectiveHaptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }) {
                            Icon(imageVector = Icons.Filled.BlurOn, contentDescription = "Fade overlay visibility")
                        }
                    }
                }
            }

            // Bottom-right cluster: compact torch button above the record FAB.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, end = 16.dp),
                horizontalAlignment = Alignment.End
            ) {
                if (hasFlashUnit) {
                    torchController?.let { controller ->
                        TorchControls(
                            capability = torchCapability,
                            isOn = torchOn,
                            strengthLevel = torchStrength,
                            onToggle = { enabled ->
                                torchOn = enabled
                                if (enabled) {
                                    if (torchCapability.supportsVariableStrength) {
                                        controller.setTorchStrength(torchStrength)
                                    } else {
                                        controller.setTorchEnabled(true)
                                    }
                                } else {
                                    controller.turnOff()
                                }
                            },
                            onStrengthChange = { level ->
                                torchStrength = level
                                controller.setTorchStrength(level)
                            }
                        )
                    }
                }

                if (hasFlashUnit) {
                    Spacer(modifier = Modifier.height(16.dp))
                }

                RecordingControls(
                    isRecording = isRecording,
                    isPaused = isPaused,
                    elapsedSeconds = elapsedSeconds,
                    onStartStop = {
                        if (isRecording) {
                            cameraController.stopRecording()
                        } else {
                            cameraController.startRecording { event ->
                                when (event) {
                                    is VideoRecordEvent.Start -> {
                                        isRecording = true
                                        isPaused = false
                                    }
                                    is VideoRecordEvent.Pause -> isPaused = true
                                    is VideoRecordEvent.Resume -> isPaused = false
                                    is VideoRecordEvent.Finalize -> {
                                        isRecording = false
                                        isPaused = false
                                        val message = if (event.hasError()) {
                                            "Recording failed: ${event.error}"
                                        } else {
                                            "Saved to ${event.outputResults.outputUri}"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                                    }
                                    else -> Unit
                                }
                            }
                        }
                    },
                    onPauseResume = {
                        if (isPaused) {
                            cameraController.resumeRecording()
                        } else {
                            cameraController.pauseRecording()
                        }
                    }
                )
            }
        }
    }

    if (showOverlaysSheet) {
        ModalBottomSheet(
            onDismissRequest = { showOverlaysSheet = false },
            sheetState = overlaysSheetState
        ) {
            OverlayLayersSheetContent(
                layersState = layersState,
                onAddLayerRequested = { launchImagePicker(imagePicker) },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = settingsSheetState
        ) {
            SettingsSheetContent(settings = settings, modifier = Modifier.fillMaxWidth())
        }
    }
}