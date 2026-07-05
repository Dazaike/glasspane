package com.glasspane.app.camera

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.text.SimpleDateFormat
import java.util.Locale

private const val TAG = "CameraXController"

/**
 * Owns the CameraX [ProcessCameraProvider] binding lifecycle for Glasspane.
 *
 * Binds [Preview] (rendered into a caller-supplied [PreviewView]) together with a
 * [VideoCapture] use case backed by [Recorder] to the same [Camera] session. The
 * Compose overlay/torch/recording UI is drawn separately on top of the
 * [PreviewView] in the Compose layer and is never fed into this pipeline, so the
 * recorded video naturally excludes the overlay image.
 *
 * [lifecycleOwner]/[previewView] are cached after the first successful bind so
 * that later lens ([switchTo]) or quality ([setQuality]) changes can rebind
 * without any new picker/view plumbing from the caller.
 */
class CameraXController(private val context: Context) {

    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null

    private var lifecycleOwner: LifecycleOwner? = null
    private var previewView: PreviewView? = null
    private var onBoundCallback: ((Camera) -> Unit)? = null

    private var currentSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    private var currentQuality: Quality = Quality.FHD

    var camera: Camera? = null
        private set

    /**
     * Requests the [ProcessCameraProvider] and binds preview + video capture to
     * [lifecycleOwner] once it is available. Safe to call multiple times (e.g. on
     * configuration change); CameraX unbinds previous use cases before rebinding.
     * [onBound] is invoked on the main thread once the camera is bound.
     */
    fun startCamera(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        onBound: (Camera) -> Unit
    ) {
        this.lifecycleOwner = lifecycleOwner
        this.previewView = previewView
        this.onBoundCallback = onBound
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener(
            {
                val provider = providerFuture.get()
                cameraProvider = provider
                bind(provider)
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    /** Exposes the provider so callers (e.g. lens enumeration) can inspect available cameras. */
    fun getCameraProvider(): ProcessCameraProvider? = cameraProvider

    private fun bind(provider: ProcessCameraProvider) {
        val lifecycleOwner = this.lifecycleOwner ?: return
        val previewView = this.previewView ?: return

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }

        val qualitySelector = QualitySelector.fromOrderedList(
            listOf(currentQuality, Quality.FHD, Quality.HD, Quality.SD).distinct(),
            FallbackStrategy.higherQualityOrLowerThan(currentQuality)
        )
        val newRecorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
        val newVideoCapture = VideoCapture.withOutput(newRecorder)

        provider.unbindAll()
        val boundCamera = provider.bindToLifecycle(
            lifecycleOwner,
            currentSelector,
            preview,
            newVideoCapture
        )
        camera = boundCamera
        videoCapture = newVideoCapture
        onBoundCallback?.invoke(boundCamera)
    }

    /**
     * Switches to a different camera/lens (front/back toggle or a specific
     * [LensOption.cameraSelector]) and rebinds. No-ops while a recording is
     * active, since [VideoCapture] must not be swapped mid-recording.
     */
    fun switchTo(selector: CameraSelector) {
        if (isRecording()) {
            Log.w(TAG, "switchTo ignored while recording is active")
            return
        }
        currentSelector = selector
        cameraProvider?.let { bind(it) }
    }

    /**
     * Changes the recorder's target [Quality] and rebinds. No-ops while a
     * recording is active.
     */
    fun setQuality(quality: Quality) {
        if (isRecording()) {
            Log.w(TAG, "setQuality ignored while recording is active")
            return
        }
        currentQuality = quality
        cameraProvider?.let { bind(it) }
    }

    fun currentQualitySetting(): Quality = currentQuality

    /**
     * Starts recording video-only output to MediaStore. [onEvent] receives
     * [VideoRecordEvent] updates (Start/Status/Pause/Resume/Finalize) so the UI
     * can reflect recording state and duration.
     */
    fun startRecording(onEvent: (VideoRecordEvent) -> Unit) {
        val videoCapture = this.videoCapture ?: run {
            Log.w(TAG, "startRecording called before camera bound")
            return
        }
        if (activeRecording != null) {
            Log.w(TAG, "startRecording called while a recording is already active")
            return
        }

        val name = "Glasspane_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis()) +
            ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Glasspane")
            }
        }
        val outputOptions = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
            .setContentValues(contentValues)
            .build()

        activeRecording = videoCapture.output
            .prepareRecording(context, outputOptions)
            // Video-only: withAudioEnabled() is intentionally never called.
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event is VideoRecordEvent.Finalize) {
                    activeRecording = null
                    if (event.hasError()) {
                        Log.e(TAG, "Video recording finalized with error: ${event.error}")
                    }
                }
                onEvent(event)
            }
    }

    fun stopRecording() {
        activeRecording?.stop()
        activeRecording = null
    }

    /** Pauses the active recording in place; a no-op if nothing is recording. */
    fun pauseRecording() {
        activeRecording?.pause()
    }

    /** Resumes a previously paused recording; a no-op if nothing is recording. */
    fun resumeRecording() {
        activeRecording?.resume()
    }

    fun isRecording(): Boolean = activeRecording != null

    fun unbind() {
        stopRecording()
        cameraProvider?.unbindAll()
        camera = null
    }
}
