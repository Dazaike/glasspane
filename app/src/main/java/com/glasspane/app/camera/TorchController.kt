package com.glasspane.app.camera

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraControl
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.CaptureRequestOptions
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.core.content.ContextCompat

private const val TAG = "TorchController"

/**
 * Describes the torch hardware capability of the currently bound camera.
 *
 * [maxStrengthLevel] mirrors `CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL`.
 * A value of 1 (or unavailable, pre-API 33) means the device only supports a plain
 * on/off torch; anything greater than 1 means the UI should expose a strength slider.
 */
data class TorchCapability(
    val supportsVariableStrength: Boolean,
    val maxStrengthLevel: Int
)

/**
 * Wraps torch on/off and (where supported) variable-strength control for a bound
 * [Camera]. Variable strength requires driving the Camera2 `FLASH_STRENGTH_LEVEL`
 * capture request key via [Camera2CameraControl] while CameraX still owns the
 * camera session -- the camera must never be opened directly via [CameraManager]
 * in parallel, as that would conflict with CameraX's session.
 */
@OptIn(markerClass = [ExperimentalCamera2Interop::class])
class TorchController(
    private val context: Context,
    private val camera: Camera
) {
    private val cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val cameraId: String = Camera2CameraInfo.from(camera.cameraInfo).cameraId

    private var torchCallback: CameraManager.TorchCallback? = null
    private var currentStrengthLevel: Int = 1

    /**
     * Reads torch hardware capability from Camera2 characteristics.
     *
     * `extractCameraCharacteristics` is flagged `RestrictedApi` by lint (it is
     * scoped to the androidx.camera library group) but is the documented,
     * intended way for app code to read [CameraCharacteristics] for a bound
     * CameraX camera; suppressed per CameraX's own Camera2Interop guidance.
     */
    @SuppressLint("RestrictedApi")
    fun queryCapability(): TorchCapability {
        val characteristics = Camera2CameraInfo.extractCameraCharacteristics(camera.cameraInfo)
        val maxLevel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            characteristics.get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL) ?: 1
        } else {
            1
        }
        return TorchCapability(supportsVariableStrength = maxLevel > 1, maxStrengthLevel = maxLevel)
    }

    /** Simple on/off control, used on hardware/API levels without strength support. */
    fun setTorchEnabled(enabled: Boolean) {
        camera.cameraControl.enableTorch(enabled)
    }

    /**
     * Sets torch to ON at the given [level] (1..maxStrengthLevel) using the Camera2
     * `FLASH_STRENGTH_LEVEL` capture request key via [Camera2CameraControl]. Only
     * meaningful on API 33+ hardware reporting `maxStrengthLevel > 1`.
     */
    @Suppress("NewApi")
    fun setTorchStrength(level: Int) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            setTorchEnabled(level > 0)
            return
        }
        currentStrengthLevel = level
        val camera2Control = Camera2CameraControl.from(camera.cameraControl)
        val options = CaptureRequestOptions.Builder()
            .setCaptureRequestOption(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
            .setCaptureRequestOption(CaptureRequest.FLASH_STRENGTH_LEVEL, level)
            .build()
        camera2Control.setCaptureRequestOptions(options)
    }

    /** Turns the torch fully off, clearing any Camera2 strength override. */
    fun turnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val camera2Control = Camera2CameraControl.from(camera.cameraControl)
            camera2Control.setCaptureRequestOptions(CaptureRequestOptions.Builder().build())
        }
        camera.cameraControl.enableTorch(false)
    }

    /**
     * Registers a [CameraManager.TorchCallback] so external torch state changes
     * (e.g. another app, or the strength level reported by the driver) are
     * reflected back into the UI via [onTorchStateChanged].
     */
    fun registerTorchCallback(onTorchStateChanged: (isOn: Boolean, strengthLevel: Int) -> Unit) {
        unregisterTorchCallback()
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                if (cameraId == this@TorchController.cameraId) {
                    onTorchStateChanged(enabled, if (enabled) currentStrengthLevel else 0)
                }
            }

            override fun onTorchModeUnavailable(cameraId: String) {
                if (cameraId == this@TorchController.cameraId) {
                    Log.w(TAG, "Torch unavailable for camera $cameraId")
                }
            }

            override fun onTorchStrengthLevelChanged(cameraId: String, newStrengthLevel: Int) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    cameraId == this@TorchController.cameraId
                ) {
                    currentStrengthLevel = newStrengthLevel
                    onTorchStateChanged(true, newStrengthLevel)
                }
            }
        }
        torchCallback = callback
        cameraManager.registerTorchCallback(callback, Handler(Looper.getMainLooper()))
    }

    fun unregisterTorchCallback() {
        torchCallback?.let { cameraManager.unregisterTorchCallback(it) }
        torchCallback = null
    }
}
