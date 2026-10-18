package com.qin.f21.toolbox.core.hardware

import android.content.Context
import android.hardware.camera2.CameraManager
import android.os.Build
import android.util.Log

/**
 * Controls the camera flash as a torch.
 *
 * The Qin F21 Pro has a single rear camera with an LED flash, which the platform exposes
 * through [CameraManager.setTorchMode]. If no flash unit is present the caller receives
 * [TorchState.UNAVAILABLE] instead of a silent no-op.
 */
class TorchController(context: Context) {

    enum class TorchState { OFF, ON, UNAVAILABLE }

    private val appContext = context.applicationContext
    private val cameraManager = appContext.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private val capabilities = HardwareCapabilities.get(appContext)
    private var activeCameraId: String? = null

    @Volatile
    private var torchOn = false

    val isAvailable: Boolean get() = capabilities.hasTorch && cameraManager != null

    fun isOn(): Boolean = torchOn

    fun setEnabled(enabled: Boolean): TorchState {
        if (!isAvailable) return TorchState.UNAVAILABLE
        val manager = cameraManager ?: return TorchState.UNAVAILABLE
        return try {
            val id = activeCameraId ?: capabilities.camerasWithFlash.firstOrNull() ?: return TorchState.UNAVAILABLE
            manager.setTorchMode(id, enabled)
            activeCameraId = if (enabled) id else null
            torchOn = enabled
            if (enabled) TorchState.ON else TorchState.OFF
        } catch (e: Exception) {
            Log.w(TAG, "Torch toggle failed", e)
            TorchState.UNAVAILABLE
        }
    }

    companion object {
        private const val TAG = "TorchController"
    }
}

/**
 * Infrared blaster access.
 *
 * Only some Qin models ship an IR emitter; the platform is asked at runtime and the bundled
 * code database is only offered when [android.hardware.ConsumerIrManager.hasIrEmitter] is true.
 */
class InfraredController(context: Context) {

    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService("consumer_ir") as? android.hardware.ConsumerIrManager

    val isAvailable: Boolean get() = try {
        manager?.hasIrEmitter() == true
    } catch (e: Exception) {
        false
    }

    /** The carrier frequency range the emitter of this device supports. */
    val carrierFrequencyRange: IntRange
        get() = try {
            val ranges = manager?.carrierFrequencies
            if (ranges.isNullOrEmpty()) {
                0..0
            } else {
                ranges.minOf { it.minFrequency }..ranges.maxOf { it.maxFrequency }
            }
        } catch (e: Exception) {
            0..0
        }

    fun transmit(pattern: IntArray, carrierFrequency: Int = 38000): Boolean {
        val ir = manager ?: return false
        if (!isAvailable) return false
        return try {
            ir.transmit(carrierFrequency, pattern)
            true
        } catch (e: Exception) {
            Log.w(TAG, "IR transmit failed", e)
            false
        }
    }

    companion object {
        private const val TAG = "InfraredController"
    }
}
