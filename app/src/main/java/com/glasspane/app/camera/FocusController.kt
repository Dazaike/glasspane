package com.glasspane.app.camera

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera

/**
 * Describes the manual-focus hardware capability of the currently bound camera.
 *
 * [minimumFocusDistance] mirrors `CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE`,
 * in diopters (0f = focus at infinity; the hardware's reported value is the closest
 * focus distance). [supportsManualFocus] additionally requires the Camera2
 * `CONTROL_AF_MODE_OFF` mode to be available, since manual focus is driven by
 * disabling autofocus and setting `LENS_FOCUS_DISTANCE` directly.
 */
data class FocusCapability(
    val supportsManualFocus: Boolean,
    val minimumFocusDistance: Float
)

/**
 * Wraps manual-focus control for a bound [Camera] using the Camera2
 * `LENS_FOCUS_DISTANCE` capture request key via [Camera2CameraControl] while
 * CameraX still owns the camera session.
 */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
class FocusController(private val camera: Camera) {

    /**
     * Reads manual-focus hardware capability from Camera2 characteristics.
     *
     * `extractCameraCharacteristics` is flagged `RestrictedApi` by lint (it is
     * scoped to the androidx.camera library group) but is the documented,
     * intended way for app code to read [CameraCharacteristics] for a bound
     * CameraX camera; suppressed per CameraX's own Camera2Interop guidance.
     */
    @SuppressLint("RestrictedApi")
    fun queryCapability(): FocusCapability {
        val characteristics = Camera2CameraInfo.extractCameraCharacteristics(camera.cameraInfo)
        val minimumFocusDistance =
            characteristics.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
        val afModes =
            characteristics.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES) ?: IntArray(0)
        val supportsManualFocus =
            minimumFocusDistance > 0f && afModes.contains(CaptureRequest.CONTROL_AF_MODE_OFF)
        return FocusCapability(
            supportsManualFocus = supportsManualFocus,
            minimumFocusDistance = minimumFocusDistance
        )
    }

    /** Locks focus at [distance] diopters, disabling autofocus. */
    fun setManualFocus(distance: Float) {
        val camera2Control = Camera2CameraControl.from(camera.cameraControl)
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
            .setCaptureRequestOption(CaptureRequest.LENS_FOCUS_DISTANCE, distance)
            .build()
        camera2Control.addCaptureRequestOptions(options)
    }

    /** Restores continuous autofocus. */
    fun setAutoFocus() {
        val camera2Control = Camera2CameraControl.from(camera.cameraControl)
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            .build()
        camera2Control.addCaptureRequestOptions(options)
    }
}
