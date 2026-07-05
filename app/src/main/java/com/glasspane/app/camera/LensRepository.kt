package com.glasspane.app.camera

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import kotlin.math.roundToInt

/**
 * One selectable physical lens: which facing it belongs to, a human-readable
 * zoom label relative to that facing's default ("1x") lens, and a
 * [CameraSelector] that pins CameraX to this specific camera id.
 */
data class LensOption(
    val cameraId: String,
    val facing: Int,
    val zoomLabel: String,
    val zoomFactor: Float,
    val cameraSelector: CameraSelector
)

/**
 * Enumerates the device's front/back cameras via CameraX + Camera2 interop and
 * labels every back-facing lens relative to the default ("1x") back lens by
 * comparing focal lengths, so a real ultra-wide physical lens naturally shows
 * up labeled `~0.5x` on devices that expose one as a separate CameraX camera.
 */
object LensRepository {

    private data class RawLens(val cameraId: String, val facing: Int, val focalMm: Float?)

    @OptIn(markerClass = [ExperimentalCamera2Interop::class])
    @SuppressLint("RestrictedApi")
    fun listLenses(cameraProvider: ProcessCameraProvider): List<LensOption> {
        val infos: List<CameraInfo> = cameraProvider.availableCameraInfos

        val raw = infos.mapNotNull { info ->
            runCatching {
                val characteristics = Camera2CameraInfo.extractCameraCharacteristics(info)
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING) ?: return@mapNotNull null
                val focalLengths = characteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                val cameraId = Camera2CameraInfo.from(info).cameraId
                RawLens(cameraId = cameraId, facing = facing, focalMm = focalLengths?.minOrNull())
            }.getOrNull()
        }

        fun defaultCameraIdFor(facing: Int): String? {
            val selector = if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            return runCatching {
                selector.filter(infos).firstOrNull()?.let { Camera2CameraInfo.from(it).cameraId }
            }.getOrNull()
        }

        val result = mutableListOf<LensOption>()
        for (facing in listOf(CameraCharacteristics.LENS_FACING_BACK, CameraCharacteristics.LENS_FACING_FRONT)) {
            val group = raw.filter { it.facing == facing }
            if (group.isEmpty()) continue

            val baselineId = defaultCameraIdFor(facing) ?: group.first().cameraId
            val baseline = group.firstOrNull { it.cameraId == baselineId } ?: group.first()
            val baselineFocal = baseline.focalMm

            group.sortedBy { it.cameraId }.forEach { candidate ->
                val label = when {
                    candidate.cameraId == baseline.cameraId -> "1x"
                    baselineFocal != null && candidate.focalMm != null && candidate.focalMm > 0f ->
                        formatZoomLabel(baselineFocal / candidate.focalMm)
                    else -> null
                } ?: return@forEach

                val zoomFactor = if (candidate.cameraId == baseline.cameraId || baselineFocal == null || candidate.focalMm == null) {
                    1f
                } else {
                    baselineFocal / candidate.focalMm
                }

                val candidateId = candidate.cameraId
                val selector = CameraSelector.Builder()
                    .addCameraFilter { camInfos ->
                        camInfos.filter { Camera2CameraInfo.from(it).cameraId == candidateId }
                    }
                    .build()

                result.add(
                    LensOption(
                        cameraId = candidateId,
                        facing = facing,
                        zoomLabel = label,
                        zoomFactor = zoomFactor,
                        cameraSelector = selector
                    )
                )
            }
        }
        return result
    }

    private fun formatZoomLabel(ratio: Float): String {
        val rounded = (ratio * 100).roundToInt() / 100f
        return if (rounded == rounded.toInt().toFloat()) {
            "${rounded.toInt()}x"
        } else {
            "${rounded}x"
        }
    }
}
